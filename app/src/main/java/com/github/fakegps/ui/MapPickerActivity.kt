package com.github.fakegps.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import com.github.fakegps.AddressSearch
import com.github.fakegps.FakeGpsUtils
import com.github.fakegps.model.LocPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tencent.fakegps.R
import com.tencent.fakegps.databinding.ActivityMapPickerBinding
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Visual location picker backed by OpenStreetMap tiles (osmdroid).
 * No API key, no Google Play Services, and no root are required.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var mapView: MapView
    private lateinit var marker: Marker

    private var latitude = DEFAULT_LAT
    private var longitude = DEFAULT_LON
    private var lastDragTextUpdateMs = 0L
    private var searchJob: Job? = null

    @Suppress("DEPRECATION") // osmdroid still exposes this toggling API.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.enableEdgeToEdge(this, R.id.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val incoming = intent
        var lat = incoming.getDoubleExtra(EXTRA_LAT, DEFAULT_LAT)
        var lon = incoming.getDoubleExtra(EXTRA_LON, DEFAULT_LON)
        if (savedInstanceState != null) {
            lat = savedInstanceState.getDouble(EXTRA_LAT, lat)
            lon = savedInstanceState.getDouble(EXTRA_LON, lon)
        }
        if (lat.isNaN() || lat < -90 || lat > 90) {
            lat = DEFAULT_LAT
        }
        if (lon.isNaN() || lon < -180 || lon > 180) {
            lon = DEFAULT_LON
        }
        latitude = lat
        longitude = lon

        binding.btnSearch.setOnClickListener { performSearch() }
        binding.inputSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        binding.btnZoomIn.setOnClickListener { mapView.controller.zoomIn() }
        binding.btnZoomOut.setOnClickListener { mapView.controller.zoomOut() }
        binding.btnCopyCoords.setOnClickListener { copySelectedCoordinates() }
        binding.btnOpenExternal.setOnClickListener { openInExternalMap() }

        binding.tvOsmAttribution.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://www.openstreetmap.org/copyright".toUri()
                    )
                )
            } catch (ignored: Exception) {
                // No browser installed.
            }
        }

        mapView = binding.map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)
        mapView.setTilesScaledToDpi(true)
        mapView.controller.setZoom(DEFAULT_ZOOM)
        applyNightMapFilter()

        val start = GeoPoint(latitude, longitude)
        mapView.controller.setCenter(start)

        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                selectPoint(point)
                return true
            }

            override fun longPressHelper(point: GeoPoint): Boolean {
                mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                selectPoint(point)
                return true
            }
        })
        mapView.overlays.add(eventsOverlay)

        marker = Marker(mapView)
        marker.position = start
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.setDraggable(true)
        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
            }

            override fun onMarkerDrag(marker: Marker) {
                val now = SystemClock.uptimeMillis()
                if (now - lastDragTextUpdateMs >= 100) {
                    lastDragTextUpdateMs = now
                    updateSelectedCoordinates(marker.position)
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                updateSelectedCoordinates(marker.position)
                announceSelection()
            }
        })
        mapView.overlays.add(marker)
        mapView.invalidate()

        binding.btnUse.setOnClickListener {
            val result = Intent()
            result.putExtra(EXTRA_LAT, latitude)
            result.putExtra(EXTRA_LON, longitude)
            setResult(RESULT_OK, result)
            finish()
        }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnRecenter.setOnClickListener {
            mapView.controller.animateTo(marker.position)
        }

        updateSelectedText()
        updateNetworkHint()
    }

    private fun updateNetworkHint() {
        binding.tvMapHint.setText(
            if (isNetworkAvailable()) R.string.map_hint else R.string.map_offline
        )
    }

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDouble(EXTRA_LAT, latitude)
        outState.putDouble(EXTRA_LON, longitude)
    }

    private fun selectPoint(point: GeoPoint) {
        marker.position = point
        updateSelectedCoordinates(point)
        mapView.invalidate()
        announceSelection()
    }

    @Suppress("DEPRECATION") // Kept for API 21 compatibility.
    private fun announceSelection() {
        binding.tvSelected.announceForAccessibility(binding.tvSelected.text)
    }

    private fun performSearch() {
        val query = binding.inputSearch.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            binding.searchLayout.error = getString(R.string.search_empty)
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, R.string.search_failed, Toast.LENGTH_LONG).show()
            return
        }
        binding.searchLayout.error = null
        setSearchBusy(true)
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val results = AddressSearch.search(applicationContext, query)
            if (isFinishing || isDestroyed) {
                return@launch
            }
            setSearchBusy(false)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showSearchResults(results)
            }
        }
    }

    private fun setSearchBusy(busy: Boolean) {
        binding.btnSearch.isEnabled = !busy
        binding.inputSearch.isEnabled = !busy
        binding.searchLayout.helperText =
            if (busy) getString(R.string.searching) else null
    }

    private fun applyNightMapFilter() {
        val tiles = mapView.overlayManager.tilesOverlay ?: return
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            val matrix = ColorMatrix()
            matrix.setSaturation(0.35f)
            val scale = ColorMatrix()
            scale.setScale(0.62f, 0.62f, 0.62f, 1.0f)
            matrix.postConcat(scale)
            tiles.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            tiles.setColorFilter(null)
        }
    }

    private fun copySelectedCoordinates() {
        val point = LocPoint(latitude, longitude)
        FakeGpsUtils.copyToClipboard(this, point.toString())
        Toast.makeText(
            this,
            getString(R.string.location_copied) + "\n" + point,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openInExternalMap() {
        val uri = String.format(
            Locale.US,
            "geo:%.6f,%.6f?q=%.6f,%.6f",
            latitude, longitude, latitude, longitude
        )
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.search_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchResults(results: List<AddressSearch.Result>) {
        if (results.isEmpty()) {
            Toast.makeText(this, R.string.search_no_results, Toast.LENGTH_LONG).show()
            return
        }
        val labels = results.map { it.label }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_results_title)
            .setItems(labels) { _, which ->
                val result = results[which]
                val point = GeoPoint(result.latitude, result.longitude)
                mapView.controller.animateTo(point)
                mapView.controller.setZoom(16.0)
                selectPoint(point)
                hideKeyboard()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.inputSearch.windowToken, 0)
        binding.inputSearch.clearFocus()
    }

    override fun onDestroy() {
        searchJob?.cancel()
        mapView.onDetach()
        super.onDestroy()
    }

    private fun updateSelectedCoordinates(point: GeoPoint) {
        latitude = point.latitude
        longitude = point.longitude
        updateSelectedText()
    }

    private fun updateSelectedText() {
        val coordinates = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        binding.tvSelected.text = getString(R.string.map_selected, coordinates)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        applyNightMapFilter()
        updateNetworkHint()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"

        private const val DEFAULT_LAT = 37.802406
        private const val DEFAULT_LON = -122.401779
        private const val DEFAULT_ZOOM = 15.0

        @JvmStatic
        fun createIntent(context: Context, point: LocPoint?): Intent {
            val lat = point?.latitude ?: DEFAULT_LAT
            val lon = point?.longitude ?: DEFAULT_LON
            return Intent(context, MapPickerActivity::class.java).apply {
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LON, lon)
            }
        }
    }
}
