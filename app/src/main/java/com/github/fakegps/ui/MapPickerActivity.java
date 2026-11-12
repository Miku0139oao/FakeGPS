package com.github.fakegps.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.fakegps.model.LocPoint;
import com.tencent.fakegps.R;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.Marker;

import java.util.Locale;

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
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.map_picker_title);
        }

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
                updateSelectedCoordinates(marker.getPosition());
            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                updateSelectedCoordinates(marker.getPosition());
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

        updateSelectedText();
        updateNetworkHint();
    }

    private void updateNetworkHint() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager == null
                ? null
                : connectivityManager.getActiveNetworkInfo();
        boolean online = networkInfo != null && networkInfo.isConnected();
        if (!online) {
            mHintView.setText(R.string.map_offline);
        }
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }
}
