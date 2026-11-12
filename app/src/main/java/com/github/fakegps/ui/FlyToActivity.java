package com.github.fakegps.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.github.fakegps.DbUtils;
import com.github.fakegps.FakeGpsUtils;
import com.github.fakegps.JoyStickManager;
import com.github.fakegps.model.LocPoint;
import com.tencent.fakegps.R;

public class FlyToActivity extends BaseBookmarkActivity
        implements View.OnClickListener, JoyStickManager.FlyStateListener {
    private final double LAT_DEFAULT = 23.151637;
    private final double LON_DEFAULT = 113.344721;

    private final int FLY_TIME_DEFAULT = 60;

    private static final int REQUEST_CODE_MAP_FROM = 3001;
    private static final int REQUEST_CODE_MAP_TO = 3002;

    private EditText mFromLocEditText;
    private EditText mLocEditText;
    private EditText mFlyTimeEditText;
    private Button mBtnStart;
    private boolean mWasFlying;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fly);
        UiUtils.enableEdgeToEdge(this, R.id.root);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.label_fly);
        }

        // From location input
        mFromLocEditText = (EditText) findViewById(R.id.inputFromLoc);
        LocPoint currentLocPoint = JoyStickManager.get().getCurrentLocPoint();
        if (currentLocPoint != null) {
            mFromLocEditText.setText(currentLocPoint.toString());
        } else {
            String lastLocPoint = DbUtils.getLastLocPoint(this);
            if (!TextUtils.isEmpty(lastLocPoint)) {
                mFromLocEditText.setText(lastLocPoint);
            } else {
                mFromLocEditText.setText(new LocPoint(LAT_DEFAULT, LON_DEFAULT).toString());
            }
        }

        // To location input
        mLocEditText = (EditText) findViewById(R.id.inputLoc);
        mLocEditText.setHint(R.string.loc_input_hint);

        // Fly time input
        mFlyTimeEditText = (EditText) findViewById(R.id.inputFlyTime);
        mFlyTimeEditText.setText(String.valueOf(FLY_TIME_DEFAULT));

        mBtnStart = (Button) findViewById(R.id.btn_fly);
        mBtnStart.setOnClickListener(this);
        findViewById(R.id.btn_from_map).setOnClickListener(this);
        findViewById(R.id.btn_to_map).setOnClickListener(this);
        updateBtn();

        ListView listView = (ListView) findViewById(R.id.list_bookmark);
        initBookmarkList(listView);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_from_map) {
            openMapFor(mFromLocEditText, REQUEST_CODE_MAP_FROM);
            return;
        }
        if (id == R.id.btn_to_map) {
            openMapFor(mLocEditText, REQUEST_CODE_MAP_TO);
            return;
        }
        if (id != R.id.btn_fly) {
            return;
        }
        if (!JoyStickManager.get().isStarted()) {
            Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show();
            return;
        }

        if (JoyStickManager.get().isFlyMode()) {
            mWasFlying = false;
            JoyStickManager.get().stopFlyMode();
        } else {
            int flyTime = FakeGpsUtils.getIntValueFromInput(this, mFlyTimeEditText);
            LocPoint fromPoint = FakeGpsUtils.getLocPointFromInput(this, mFromLocEditText);
            LocPoint toPoint = FakeGpsUtils.getLocPointFromInput(this, mLocEditText);
            if (fromPoint == null || toPoint == null) {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show();
            } else if (flyTime <= 0) {
                Toast.makeText(this, R.string.fly_time_invalid, Toast.LENGTH_SHORT).show();
            } else {
                // Jump to the "From" location first, then fly to "To"
                JoyStickManager.get().jumpToLocation(fromPoint);
                JoyStickManager.get().flyToLocation(toPoint, flyTime);
                mWasFlying = true;
            }
        }
        updateBtn();
    }

    @Override
    protected void onStart() {
        super.onStart();
        JoyStickManager.get().setFlyStateListener(this);
        mWasFlying = JoyStickManager.get().isFlyMode();
        updateBtn();
    }

    @Override
    protected void onStop() {
        JoyStickManager.get().setFlyStateListener(null);
        super.onStop();
    }

    @Override
    public void onFlyStateChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean flying = JoyStickManager.get().isFlyMode();
                updateBtn();
                if (mWasFlying && !flying) {
                    Toast.makeText(FlyToActivity.this, R.string.fly_complete,
                            Toast.LENGTH_SHORT).show();
                }
                mWasFlying = flying;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBtn();
    }

    private void openMapFor(EditText field, int requestCode) {
        LocPoint point = FakeGpsUtils.getLocPointFromInput(this, field);
        if (point == null) {
            point = JoyStickManager.get().getCurrentLocPoint();
        }
        MapPickerActivity.startForResult(this, requestCode, point);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        EditText target;
        if (requestCode == REQUEST_CODE_MAP_FROM) {
            target = mFromLocEditText;
        } else if (requestCode == REQUEST_CODE_MAP_TO) {
            target = mLocEditText;
        } else {
            return;
        }
        double latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN);
        double longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN);
        if (FakeGpsUtils.isValidLatitude(latitude) && FakeGpsUtils.isValidLongitude(longitude)) {
            target.setText(new LocPoint(latitude, longitude).toString());
            target.setSelection(target.getText().length());
        }
    }

    @Override
    protected void onBookmarkSelected(LocPoint locPoint) {
        mLocEditText.setText(locPoint != null ? locPoint.toString() : "");
    }

    private void updateBtn() {
        boolean started = JoyStickManager.get().isStarted();
        mBtnStart.setEnabled(started);
        if (JoyStickManager.get().isFlyMode()) {
            mBtnStart.setText(R.string.btn_fly_stop);
        } else {
            mBtnStart.setText(R.string.btn_fly_start);
        }
    }

    public static void startPage(Context context) {
        Intent intent = new Intent(context, FlyToActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
