package com.github.fakegps.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.github.fakegps.BookmarkNames
import com.github.fakegps.BookmarkRepository
import com.github.fakegps.BackgroundExecution
import com.github.fakegps.CoordinateParser
import com.github.fakegps.DbUtils
import com.github.fakegps.FakeGpsUtils
import com.github.fakegps.JoyStickManager
import com.github.fakegps.SettingsStore
import com.github.fakegps.model.LocPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.github.fakegps.R
import com.github.fakegps.databinding.ActivityMainBinding
import java.math.BigDecimal
import kotlinx.coroutines.launch

class MainActivity : BaseBookmarkActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding

    // Pending start params - saved while requesting permissions.
    private var pendingStartPoint: LocPoint? = null
    private var pendingStartStep = 0.0

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkPermissionsAndStart()
        } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            showLocationSettingsDialog()
        } else {
            Toast.makeText(
                this, R.string.location_permission_required, Toast.LENGTH_LONG
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // A denied notification only hides the foreground notification.
        checkPermissionsAndStart()
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(
                this, R.string.overlay_permission_missing, Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Both the mock-app picker and the app details screen re-check the state.
        checkPermissionsAndStart()
    }

    private val mapLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleMapResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.enableEdgeToEdge(this, R.id.root)

        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.action_settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            } else {
                false
            }
        }
        tintMenuIcon(binding.toolbar.menu.findItem(R.id.action_settings))

        // Prefer the live session, then the location last started, then where
        // the phone actually is, and only then the built-in sample coordinate.
        // This keeps the app from silently offering a random city on every
        // launch once the user has used it.
        val initialPoint = JoyStickManager.get().currentLocPoint
            ?: CoordinateParser.parse(DbUtils.getLastLocPoint(this))
            ?: deviceLastKnownPoint()
            ?: LocPoint(LAT_DEFAULT, LON_DEFAULT)
        binding.inputLoc.setText(initialPoint.toString())
        binding.inputLoc.setSelection(binding.inputLoc.text?.length ?: 0)

        val currentMoveStep = JoyStickManager.get().moveStep
        binding.inputStep.setText(
            BigDecimal.valueOf(currentMoveStep).stripTrailingZeros().toPlainString()
        )

        binding.btnStart.setOnClickListener(this)
        binding.btnSetLoc.setOnClickListener(this)
        binding.btnPickMap.setOnClickListener(this)
        binding.btnCopy.setOnClickListener(this)
        binding.btnSaveCurrent.setOnClickListener(this)
        binding.btnStepMinus.setOnClickListener { adjustMoveStep(-1.0) }
        binding.btnStepPlus.setOnClickListener { adjustMoveStep(1.0) }

        initBookmarkList(binding.listBookmark)

        if (savedInstanceState != null &&
            savedInstanceState.containsKey(KEY_PENDING_LAT) &&
            savedInstanceState.containsKey(KEY_PENDING_LON)
        ) {
            val latitude = savedInstanceState.getDouble(KEY_PENDING_LAT)
            val longitude = savedInstanceState.getDouble(KEY_PENDING_LON)
            val step = savedInstanceState.getDouble(KEY_PENDING_STEP)
            if (FakeGpsUtils.isValidLatitude(latitude) &&
                FakeGpsUtils.isValidLongitude(longitude) &&
                FakeGpsUtils.isValidMoveStep(step)
            ) {
                pendingStartPoint = LocPoint(latitude, longitude)
                pendingStartStep = step
                checkPermissionsAndStart()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateStatus()
        updateButtons()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingStartPoint?.let {
            outState.putDouble(KEY_PENDING_LAT, it.latitude)
            outState.putDouble(KEY_PENDING_LON, it.longitude)
            outState.putDouble(KEY_PENDING_STEP, pendingStartStep)
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_start -> onStartStopClicked()
            R.id.btn_set_loc -> onSetLocationClicked()
            R.id.btn_pick_map -> {
                val point = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
                    ?: JoyStickManager.get().currentLocPoint
                mapLauncher.launch(MapPickerActivity.createIntent(this, point))
            }
            R.id.btn_copy -> {
                val point = currentOrEnteredPoint()
                if (point != null) {
                    FakeGpsUtils.copyToClipboard(this, point.toString())
                    Toast.makeText(
                        this,
                        getString(R.string.location_copied) + "\n" + point,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            R.id.btn_save_current -> saveCurrentLocation()
        }
    }

    private fun onStartStopClicked() {
        if (JoyStickManager.get().isStarted) {
            JoyStickManager.get().currentLocPoint?.let {
                DbUtils.saveLastLocPoint(this, it)
            }
            JoyStickManager.get().stop()
            // Stay on the screen so the user can adjust and start again
            // instead of being dropped back into whatever app was underneath.
            updateStatus()
            updateButtons()
            return
        }

        val step = FakeGpsUtils.getMoveStepFromInput(this, binding.inputStep)
        val point = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
        if (point == null) {
            Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        if (!FakeGpsUtils.isValidMoveStep(step)) {
            Toast.makeText(this, R.string.move_step_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        pendingStartPoint = point
        pendingStartStep = step
        // Remember what the user actually started, so reopening the app later
        // offers this location instead of falling back to the built-in default.
        DbUtils.saveLastLocPoint(this, point)
        checkPermissionsAndStart()
    }

    private fun onSetLocationClicked() {
        if (!JoyStickManager.get().isStarted) {
            Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show()
            return
        }
        val step = FakeGpsUtils.getMoveStepFromInput(this, binding.inputStep)
        val point = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
        if (step > 0 && point != null) {
            JoyStickManager.get().setMoveStep(step)
            JoyStickManager.get().jumpToLocation(point)
            DbUtils.saveLastLocPoint(this, point)
            updateStatus()
        } else {
            Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleMapResult(result: ActivityResult) {
        if (result.resultCode != RESULT_OK) {
            return
        }
        val data = result.data ?: return
        val latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
        val longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN)
        if (FakeGpsUtils.isValidLatitude(latitude) &&
            FakeGpsUtils.isValidLongitude(longitude)
        ) {
            binding.inputLoc.setText(LocPoint(latitude, longitude).toString())
            binding.inputLoc.setSelection(binding.inputLoc.text?.length ?: 0)
            updateStatus()
        }
    }

    private fun tintMenuIcon(item: MenuItem?) {
        item ?: return
        var icon = item.icon ?: return
        icon = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTint(
            icon, ContextCompat.getColor(this, R.color.brand_on_surface)
        )
        item.icon = icon
    }

    private fun currentOrEnteredPoint(): LocPoint? =
        JoyStickManager.get().currentLocPoint
            ?: FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)

    /**
     * Saves the current location with one tap. The coordinate-derived name
     * keeps every row distinct; the row menu can rename it afterwards.
     */
    private fun saveCurrentLocation() {
        val point = currentOrEnteredPoint()
        if (point == null) {
            Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = BookmarkRepository.savePoint(point, BookmarkNames.autoName(point))
            val message = when (result) {
                BookmarkRepository.DUPLICATE -> R.string.bookmark_exists
                -1L -> R.string.bookmark_save_failed
                else -> R.string.bookmark_saved
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Where the phone really is, used as the first-launch default so a new
     * install does not start the mock in the sample location abroad.
     */
    private fun deviceLastKnownPoint(): LocPoint? {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager =
            getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: Location? = null
        for (provider in manager.getProviders(true)) {
            val location = try {
                manager.getLastKnownLocation(provider)
            } catch (ignored: SecurityException) {
                null
            } ?: continue
            if (best == null || location.time > best.time) {
                best = location
            }
        }
        return best?.let { LocPoint(it.latitude, it.longitude) }
    }

    private fun updateStatus() {
        val started = JoyStickManager.get().isStarted
        binding.tvStatus.setText(
            if (started) R.string.status_active else R.string.status_inactive
        )
        binding.tvStatusHint.visibility = if (started) View.GONE else View.VISIBLE
        val point = currentOrEnteredPoint()
        binding.tvCurrentCoords.text = point?.toString() ?: getString(R.string.value_placeholder)

        val container = ContextCompat.getColor(
            this, if (started) R.color.brand_primary_container else R.color.surface_container
        )
        val content = ContextCompat.getColor(
            this, if (started) R.color.brand_on_primary_container else R.color.brand_on_surface
        )
        binding.cardStatus.setCardBackgroundColor(container)
        binding.cardStatus.strokeColor = ContextCompat.getColor(
            this, if (started) R.color.brand_primary_container else R.color.surface_stroke
        )
        binding.tvStatus.setTextColor(content)
        binding.tvStatusHint.setTextColor(content)
        binding.tvCurrentCoords.setTextColor(content)
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this, if (started) R.color.status_dot_active else R.color.status_dot_inactive
            )
        )
    }

    /** Steps the move-step field by [delta] meters without ever reversing direction. */
    private fun adjustMoveStep(delta: Double) {
        val current = FakeGpsUtils.getMoveStepFromInput(this, binding.inputStep)
        val parsed = if (FakeGpsUtils.isValidMoveStep(current)) current else DEFAULT_MOVE_STEP
        // Decimal arithmetic: adding Doubles writes artifacts such as
        // "0.19999999999999996" into the field when stepping down from 1.2.
        val base = BigDecimal.valueOf(parsed)
        val step = BigDecimal.valueOf(delta)
        val next = if (delta < 0) {
            // Clamp only against the edge the press moves toward, and never
            // past the value already typed: minus must never increase 0.5 m.
            base.add(step).max(BigDecimal.valueOf(MIN_MOVE_STEP).min(base))
        } else {
            base.add(step).min(BigDecimal.valueOf(MAX_MOVE_STEP).max(base))
        }
        binding.inputStep.setText(next.stripTrailingZeros().toPlainString())
        binding.inputStep.setSelection(binding.inputStep.text?.length ?: 0)
    }

    private fun updateButtons() {
        binding.btnStart.setText(
            if (JoyStickManager.get().isStarted) R.string.btn_stop else R.string.btn_start
        )
        binding.btnSetLoc.isEnabled = JoyStickManager.get().isStarted
    }

    /**
     * Checks all required permissions in order:
     * 1. Location permission (required for foreground service with location type)
     * 2. Notification permission (Android 13+)
     * 3. Overlay permission (for floating joystick)
     * 4. Mock location app selection
     * Then starts the service if all granted.
     */
    private fun checkPermissionsAndStart() {
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        // The overlay permission is only needed while the floating panel is
        // enabled; with it off, requiring the permission would block Start for
        // users who deliberately avoid overlays.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            SettingsStore.showJoystick(this) &&
            !Settings.canDrawOverlays(this)
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.overlay_permission_title)
                .setMessage(R.string.overlay_permission_message)
                .setPositiveButton(R.string.go_to_settings) { _, _ ->
                    overlayLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:$packageName".toUri()
                        )
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        if (!FakeGpsUtils.isMockLocationAppSelected(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.mock_app_required_title)
                .setMessage(R.string.mock_app_required_message)
                .setPositiveButton(R.string.go_to_settings) { _, _ ->
                    try {
                        settingsLauncher.launch(
                            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        )
                    } catch (e: Exception) {
                        settingsLauncher.launch(Intent(Settings.ACTION_SETTINGS))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        // Some vendors freeze apps that are not on their background allowlist a
        // few seconds after the last activity stops. With the floating panel off
        // there is nothing keeping the process visible, so the mock would stop
        // and apps would fall back to the real location. Point this out once.
        if (BackgroundExecution.needsBackgroundGuidance() &&
            !SettingsStore.showJoystick(this) &&
            !SettingsStore.backgroundHintShown(this)
        ) {
            SettingsStore.setBackgroundHintShown(this, true)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.background_dialog_title)
                .setMessage(R.string.background_dialog_message)
                .setPositiveButton(R.string.background_open_settings) { _, _ ->
                    BackgroundExecution.openBackgroundSettings(this)
                    doStart()
                }
                .setNegativeButton(R.string.background_later) { _, _ -> doStart() }
                .setCancelable(false)
                .show()
            return
        }

        doStart()
    }

    private fun doStart() {
        pendingStartPoint?.let { point ->
            JoyStickManager.get().setMoveStep(pendingStartStep)
            JoyStickManager.get().start(point)
            pendingStartPoint = null
            finish()
        }
    }

    private fun showLocationSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_permission_required)
            .setMessage(R.string.location_permission_settings_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                settingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:$packageName".toUri()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBookmarkSelected(locPoint: LocPoint?) {
        binding.inputLoc.setText(locPoint?.toString() ?: "")
        if (locPoint != null) {
            if (JoyStickManager.get().isStarted) {
                JoyStickManager.get().jumpToLocation(locPoint)
                DbUtils.saveLastLocPoint(this, locPoint)
                Toast.makeText(this, R.string.location_applied, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show()
            }
        }
        updateStatus()
    }

    companion object {
        private const val LAT_DEFAULT = 37.802406
        private const val LON_DEFAULT = -122.401779

        private const val DEFAULT_MOVE_STEP = 2.0
        private const val MIN_MOVE_STEP = 0.1
        private const val MAX_MOVE_STEP = 10000.0

        private const val KEY_PENDING_LAT = "pending_lat"
        private const val KEY_PENDING_LON = "pending_lon"
        private const val KEY_PENDING_STEP = "pending_step"

        @JvmStatic
        fun startPage(context: android.content.Context) {
            val intent = Intent(context, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }
    }
}
