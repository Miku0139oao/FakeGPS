package com.github.fakegps.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.fakegps.DbUtils;
import com.github.fakegps.FakeGpsUtils;
import com.github.fakegps.JoyStickManager;
import com.github.fakegps.model.LocPoint;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tencent.fakegps.R;

public class MainActivity extends BaseBookmarkActivity implements View.OnClickListener {
    private final double LAT_DEFAULT = 37.802406;
    private final double LON_DEFAULT = -122.401779;

    private static final int REQUEST_CODE_OVERLAY = 2001;
    private static final int REQUEST_CODE_LOCATION = 2002;
    private static final int REQUEST_CODE_NOTIFICATION = 2003;
    private static final int REQUEST_CODE_MOCK_APP = 2004;
    private static final int REQUEST_CODE_MAP = 2005;
    private static final int REQUEST_CODE_APP_SETTINGS = 2006;

    private static final String KEY_PENDING_LAT = "pending_lat";
    private static final String KEY_PENDING_LON = "pending_lon";
    private static final String KEY_PENDING_STEP = "pending_step";

    private EditText mLocEditText;
    private EditText mMoveStepEditText;
    private Button mBtnStart;
    private Button mBtnSetNew;
    private TextView mStatusView;
    private TextView mStatusHint;
    private TextView mCoordsView;

    // Pending start params - saved while requesting permissions
    private LocPoint mPendingStartPoint;
    private double mPendingStartStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        UiUtils.enableEdgeToEdge(this, R.id.root);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
        tintMenuIcon(toolbar.getMenu().findItem(R.id.action_settings));

        mStatusView = findViewById(R.id.tv_status);
        mStatusHint = findViewById(R.id.tv_status_hint);
        mCoordsView = findViewById(R.id.tv_current_coords);

        //location input
        mLocEditText = (EditText) findViewById(R.id.inputLoc);
        LocPoint currentLocPoint = JoyStickManager.get().getCurrentLocPoint();
        if (currentLocPoint != null) {
            mLocEditText.setText(currentLocPoint.toString());
        } else {
            String lastLocPoint = DbUtils.getLastLocPoint(this);
            if (!TextUtils.isEmpty(lastLocPoint)) {
                mLocEditText.setText(lastLocPoint);
            } else {
                mLocEditText.setText(new LocPoint(LAT_DEFAULT, LON_DEFAULT).toString());
            }
        }

        mLocEditText.setSelection(mLocEditText.getText().length());

        //each move step delta
        mMoveStepEditText = (EditText) findViewById(R.id.inputStep);
        double currentMoveStep = JoyStickManager.get().getMoveStep();
        mMoveStepEditText.setText(
                java.math.BigDecimal.valueOf(currentMoveStep).stripTrailingZeros().toPlainString());

        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnStart.setOnClickListener(this);
        mBtnSetNew = (Button) findViewById(R.id.btn_set_loc);
        mBtnSetNew.setOnClickListener(this);
        findViewById(R.id.btn_pick_map).setOnClickListener(this);
        findViewById(R.id.btn_copy).setOnClickListener(this);
        findViewById(R.id.btn_save_current).setOnClickListener(this);

        initBookmarkList((RecyclerView) findViewById(R.id.list_bookmark));

        if (savedInstanceState != null
                && savedInstanceState.containsKey(KEY_PENDING_LAT)
                && savedInstanceState.containsKey(KEY_PENDING_LON)) {
            double latitude = savedInstanceState.getDouble(KEY_PENDING_LAT);
            double longitude = savedInstanceState.getDouble(KEY_PENDING_LON);
            double step = savedInstanceState.getDouble(KEY_PENDING_STEP);
            if (FakeGpsUtils.isValidLatitude(latitude)
                    && FakeGpsUtils.isValidLongitude(longitude)
                    && FakeGpsUtils.isValidMoveStep(step)) {
                mPendingStartPoint = new LocPoint(latitude, longitude);
                mPendingStartStep = step;
                checkPermissionsAndStart();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateStatus();
        updateButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateButtons();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPendingStartPoint != null) {
            outState.putDouble(KEY_PENDING_LAT, mPendingStartPoint.getLatitude());
            outState.putDouble(KEY_PENDING_LON, mPendingStartPoint.getLongitude());
            outState.putDouble(KEY_PENDING_STEP, mPendingStartStep);
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_start) {
            if (JoyStickManager.get().isStarted()) {
                LocPoint currentLocPoint = JoyStickManager.get().getCurrentLocPoint();
                if (currentLocPoint != null) {
                    DbUtils.saveLastLocPoint(this, currentLocPoint);
                }
                JoyStickManager.get().stop();
                finish();
                return;
            }

            double step = FakeGpsUtils.getMoveStepFromInput(this, mMoveStepEditText);
            LocPoint point = FakeGpsUtils.getLocPointFromInput(this, mLocEditText);
            if (point == null) {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!FakeGpsUtils.isValidMoveStep(step)) {
                Toast.makeText(this, R.string.move_step_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            mPendingStartPoint = point;
            mPendingStartStep = step;
            checkPermissionsAndStart();
        } else if (id == R.id.btn_set_loc) {
            if (!JoyStickManager.get().isStarted()) {
                Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show();
                return;
            }
            double step = FakeGpsUtils.getMoveStepFromInput(this, mMoveStepEditText);
            LocPoint point = FakeGpsUtils.getLocPointFromInput(this, mLocEditText);
            if (step > 0 && point != null) {
                JoyStickManager.get().setMoveStep(step);
                JoyStickManager.get().jumpToLocation(point);
                updateStatus();
            } else {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.btn_pick_map) {
            LocPoint point = FakeGpsUtils.getLocPointFromInput(this, mLocEditText);
            if (point == null) {
                point = JoyStickManager.get().getCurrentLocPoint();
            }
            MapPickerActivity.startForResult(this, REQUEST_CODE_MAP, point);
        } else if (id == R.id.btn_copy) {
            LocPoint point = currentOrEnteredPoint();
            if (point != null) {
                FakeGpsUtils.copyToClipboard(this, point.toString());
                Toast.makeText(this, getString(R.string.location_copied) + "\n" + point,
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.btn_save_current) {
            LocPoint point = currentOrEnteredPoint();
            if (point != null) {
                BookmarkActivity.startPage(this, getString(R.string.bookmark), point);
            } else {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void tintMenuIcon(MenuItem item) {
        if (item == null) {
            return;
        }
        Drawable icon = item.getIcon();
        if (icon != null) {
            icon = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(icon,
                    ContextCompat.getColor(this, R.color.brand_on_surface));
            item.setIcon(icon);
        }
    }

    private LocPoint currentOrEnteredPoint() {
        LocPoint point = JoyStickManager.get().getCurrentLocPoint();
        if (point == null) {
            point = FakeGpsUtils.getLocPointFromInput(this, mLocEditText);
        }
        return point;
    }

    private void updateStatus() {
        boolean started = JoyStickManager.get().isStarted();
        mStatusView.setText(started ? R.string.status_active : R.string.status_inactive);
        mStatusHint.setVisibility(started ? View.GONE : View.VISIBLE);
        LocPoint point = currentOrEnteredPoint();
        mCoordsView.setText(point != null ? point.toString() : "—");
    }

    private void updateButtons() {
        mBtnStart.setText(JoyStickManager.get().isStarted()
                ? R.string.btn_stop : R.string.btn_start);
        mBtnSetNew.setEnabled(JoyStickManager.get().isStarted());
    }

    /**
     * Checks all required permissions in order:
     * 1. Location permission (required for foreground service with location type)
     * 2. Notification permission (Android 13+)
     * 3. Overlay permission (for floating joystick)
     * 4. Mock location app selection
     * Then starts the service if all granted.
     */
    private void checkPermissionsAndStart() {
        // Step 1: Check location permission. COARSE is enough for the location
        // foreground service type; mock injection itself is gated by the
        // Developer-options app-op.
        boolean coarseGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!coarseGranted) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_LOCATION);
            return;
        }

        // Step 2: Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATION);
                return;
            }
        }

        // Step 3: Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.overlay_permission_title)
                        .setMessage(R.string.overlay_permission_message)
                        .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return;
            }
        }

        // Step 4: Check that the user picked FakeGPS as the mock location app.
        // Without this every provider call fails silently.
        if (!FakeGpsUtils.isMockLocationAppSelected(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.mock_app_required_title)
                    .setMessage(R.string.mock_app_required_message)
                    .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                        try {
                            startActivityForResult(
                                    new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                    REQUEST_CODE_MOCK_APP);
                        } catch (Exception e) {
                            startActivityForResult(
                                    new Intent(Settings.ACTION_SETTINGS),
                                    REQUEST_CODE_MOCK_APP);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        doStart();
    }

    private void doStart() {
        if (mPendingStartPoint != null) {
            JoyStickManager.get().setMoveStep(mPendingStartStep);
            JoyStickManager.get().start(mPendingStartPoint);
            mPendingStartPoint = null;
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION) {
            boolean coarseGranted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (coarseGranted) {
                checkPermissionsAndStart();
            } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showLocationSettingsDialog();
            } else {
                Toast.makeText(this, R.string.location_permission_required, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CODE_NOTIFICATION) {
            checkPermissionsAndStart();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                checkPermissionsAndStart();
            } else {
                Toast.makeText(this, R.string.overlay_permission_missing, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_MOCK_APP
                || requestCode == REQUEST_CODE_APP_SETTINGS) {
            checkPermissionsAndStart();
        } else if (requestCode == REQUEST_CODE_MAP && resultCode == RESULT_OK && data != null) {
            double latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN);
            double longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN);
            if (FakeGpsUtils.isValidLatitude(latitude) && FakeGpsUtils.isValidLongitude(longitude)) {
                mLocEditText.setText(new LocPoint(latitude, longitude).toString());
                mLocEditText.setSelection(mLocEditText.getText().length());
                updateStatus();
            }
        }
    }

    @Override
    protected void onBookmarkSelected(LocPoint locPoint) {
        mLocEditText.setText(locPoint != null ? locPoint.toString() : "");
        updateStatus();
    }

    private void showLocationSettingsDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_permission_required)
                .setMessage(R.string.location_permission_settings_message)
                .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_APP_SETTINGS);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void startPage(android.content.Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }
}
