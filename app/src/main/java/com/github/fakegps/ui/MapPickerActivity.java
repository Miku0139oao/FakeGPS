package com.github.fakegps.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.fakegps.AddressSearch;
import com.github.fakegps.model.LocPoint;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tencent.fakegps.R;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visual location picker backed by OpenStreetMap tiles (osmdroid).
 * No API key, no Google Play Services, and no root are required.
 */
public class MapPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LAT = "extra_lat";
    public static final String EXTRA_LON = "extra_lon";

    private static final double DEFAULT_LAT = 37.802406;
    private static final double DEFAULT_LON = -122.401779;
    private static final double DEFAULT_ZOOM = 15.0;

    private MapView mMapView;
    private Marker mMarker;
    private TextView mSelectedView;
    private TextView mHintView;

    private double mLatitude = DEFAULT_LAT;
    private double mLongitude = DEFAULT_LON;
    private long mLastDragTextUpdateMs = 0;

    private TextInputLayout mSearchLayout;
    private TextInputEditText mSearchInput;
    private final ExecutorService mSearchExecutor = Executors.newSingleThreadExecutor();
    private int mSearchRequestId = 0;

    public static void startForResult(Activity activity, int requestCode, LocPoint point) {
        double lat = point != null ? point.getLatitude() : DEFAULT_LAT;
        double lon = point != null ? point.getLongitude() : DEFAULT_LON;
        Intent intent = new Intent(activity, MapPickerActivity.class);
        intent.putExtra(EXTRA_LAT, lat);
        intent.putExtra(EXTRA_LON, lon);
        activity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map_picker);
        UiUtils.enableEdgeToEdge(this, R.id.root);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        Intent intent = getIntent();
        double lat = intent.getDoubleExtra(EXTRA_LAT, DEFAULT_LAT);
        double lon = intent.getDoubleExtra(EXTRA_LON, DEFAULT_LON);
        if (savedInstanceState != null) {
            lat = savedInstanceState.getDouble(EXTRA_LAT, lat);
            lon = savedInstanceState.getDouble(EXTRA_LON, lon);
        }
        if (Double.isNaN(lat) || lat < -90 || lat > 90) {
            lat = DEFAULT_LAT;
        }
        if (Double.isNaN(lon) || lon < -180 || lon > 180) {
            lon = DEFAULT_LON;
        }
        mLatitude = lat;
        mLongitude = lon;

        mSelectedView = (TextView) findViewById(R.id.tv_selected);
        mHintView = (TextView) findViewById(R.id.tv_map_hint);

        mSearchLayout = findViewById(R.id.searchLayout);
        mSearchInput = findViewById(R.id.inputSearch);
        mSearchLayout.setEndIconOnClickListener(view -> performSearch());
        mSearchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        TextView attribution = (TextView) findViewById(R.id.tv_osm_attribution);
        attribution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.openstreetmap.org/copyright")));
                } catch (Exception ignored) {
                    // No browser installed.
                }
            }
        });

        mMapView = (MapView) findViewById(R.id.map);
        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.getController().setZoom(DEFAULT_ZOOM);

        GeoPoint start = new GeoPoint(mLatitude, mLongitude);
        mMapView.getController().setCenter(start);

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                selectPoint(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                selectPoint(point);
                return true;
            }
        });
        mMapView.getOverlays().add(eventsOverlay);

        mMarker = new Marker(mMapView);
        mMarker.setPosition(start);
        mMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mMarker.setDraggable(true);
        mMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
            }

            @Override
            public void onMarkerDrag(Marker marker) {
                long now = SystemClock.uptimeMillis();
                if (now - mLastDragTextUpdateMs >= 100) {
                    mLastDragTextUpdateMs = now;
                    updateSelectedCoordinates(marker.getPosition());
                }
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updateSelectedCoordinates(marker.getPosition());
                announceSelection();
            }
        });
        mMapView.getOverlays().add(mMarker);
        mMapView.invalidate();

        findViewById(R.id.btn_use).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent result = new Intent();
                result.putExtra(EXTRA_LAT, mLatitude);
                result.putExtra(EXTRA_LON, mLongitude);
                setResult(RESULT_OK, result);
                finish();
            }
        });
        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        findViewById(R.id.btn_recenter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mMarker != null) {
                    mMapView.getController().animateTo(mMarker.getPosition());
                }
            }
        });

        updateSelectedText();
        updateNetworkHint();
    }

    private void updateNetworkHint() {
        mHintView.setText(isNetworkAvailable() ? R.string.map_hint : R.string.map_offline);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble(EXTRA_LAT, mLatitude);
        outState.putDouble(EXTRA_LON, mLongitude);
    }

    private void selectPoint(GeoPoint point) {
        mMarker.setPosition(point);
        updateSelectedCoordinates(point);
        mMapView.invalidate();
        announceSelection();
    }

    private void announceSelection() {
        if (mSelectedView != null) {
            mSelectedView.announceForAccessibility(mSelectedView.getText());
        }
    }

    private void performSearch() {
        String query = mSearchInput.getText() == null
                ? "" : mSearchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query)) {
            mSearchLayout.setError(getString(R.string.search_empty));
            return;
        }
        mSearchLayout.setError(null);
        mSearchLayout.setHelperText(getString(R.string.searching));
        final int requestId = ++mSearchRequestId;
        mSearchExecutor.execute(() -> {
            List<AddressSearch.Result> results = AddressSearch.search(this, query);
            runOnUiThread(() -> {
                if (requestId != mSearchRequestId || isFinishing()) {
                    return;
                }
                mSearchLayout.setHelperText(null);
                showSearchResults(results);
            });
        });
    }

    private void showSearchResults(List<AddressSearch.Result> results) {
        if (results == null || results.isEmpty()) {
            Toast.makeText(this, R.string.search_no_results, Toast.LENGTH_LONG).show();
            return;
        }
        final String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            labels[i] = results.get(i).label;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_results_title)
                .setItems(labels, (dialog, which) -> {
                    AddressSearch.Result result = results.get(which);
                    GeoPoint point = new GeoPoint(result.latitude, result.longitude);
                    mMapView.getController().animateTo(point);
                    mMapView.getController().setZoom(16.0);
                    selectPoint(point);
                    hideKeyboard();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
        mSearchInput.clearFocus();
    }

    @Override
    protected void onDestroy() {
        mSearchExecutor.shutdownNow();
        super.onDestroy();
    }

    private void updateSelectedCoordinates(GeoPoint point) {
        mLatitude = point.getLatitude();
        mLongitude = point.getLongitude();
        updateSelectedText();
    }

    private void updateSelectedText() {
        String coordinates = String.format(Locale.US, "%.6f, %.6f", mLatitude, mLongitude);
        mSelectedView.setText(getString(R.string.map_selected, coordinates));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
        updateNetworkHint();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }
}
