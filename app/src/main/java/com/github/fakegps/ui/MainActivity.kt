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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import androidx.core.widget.doAfterTextChanged
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

    // The coordinate field is only overwritten while it holds no unsaved edit,
    // so moving from the floating panel never clobbers something half-typed.
    private var inputDirty = false
    private var updatingInputProgrammatically = false

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
        setInputPoint(initialPoint)

        val currentMoveStep = JoyStickManager.get().moveStep
        binding.inputStep.setText(
            BigDecimal.valueOf(currentMoveStep).stripTrailingZeros().toPlainString()
        )

        binding.btnStart.setOnClickListener(this)
        binding.btnSetLoc.setOnClickListener(this)
        // Typing a coordinate and pressing the keyboard's Done key applies it
        // straight away while the simulation is running; no stop/start needed.
        binding.inputLoc.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE &&
                JoyStickManager.get().isStarted
            ) {
                onSetLocationClicked()
                true
            } else {
                false
            }
        }
        binding.inputLoc.doAfterTextChanged {
            if (!updatingInputProgrammatically) {
                inputDirty = true
            }
        }
        binding.inputStep.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE &&
                JoyStickManager.get().isStarted
            ) {
                applyMoveStepFromInput()
                true
            } else {
                false
            }
        }
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
        // The floating panel can move the simulation while this screen is in the
        // back stack. Follow it, but never overwrite a half-typed coordinate.
        if (JoyStickManager.get().isStarted && !inputDirty) {
            JoyStickManager.get().currentLocPoint?.let { setInputPoint(it) }
        }
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

    /**
     * Applies the entered coordinate and move step without stopping the
     * simulation. The engine keeps running and simply jumps, which is what the
     * map picker, bookmarks and the floating panel already do.
     */
    private fun onSetLocationClicked() {
        if (!JoyStickManager.get().isStarted) {
            Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show()
            return
        }
        val point = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
        if (point == null) {
            Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        applyPoint(point)
    }

    /** Jumps the running simulation to [point] and reports it. */
    private fun applyPoint(point: LocPoint) {
        // A bad step must not block applying the coordinate, but the field must
        // not keep a value the engine is silently ignoring either.
        val step = FakeGpsUtils.getMoveStepFromInput(this, binding.inputStep)
        if (FakeGpsUtils.isValidMoveStep(step)) {
            JoyStickManager.get().setMoveStep(step)
        } else {
            binding.inputStep.setText(
                BigDecimal.valueOf(JoyStickManager.get().moveStep)
                    .stripTrailingZeros().toPlainString()
            )
            binding.inputStep.setSelection(binding.inputStep.text?.length ?: 0)
        }
        JoyStickManager.get().jumpToLocation(point)
        DbUtils.saveLastLocPoint(this, point)
        setInputPoint(point)
        hideKeyboard()
        Toast.makeText(this, R.string.location_applied, Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun setInputPoint(point: LocPoint) = setInputText(point.toString())

    /** Writes the field without counting it as an unsaved user edit. */
    private fun setInputText(text: String) {
        updatingInputProgrammatically = true
        binding.inputLoc.setText(text)
        binding.inputLoc.setSelection(binding.inputLoc.text?.length ?: 0)
        updatingInputProgrammatically = false
        inputDirty = false
    }

    /** Pushes the step typed in the field to the running engine. */
    private fun applyMoveStepFromInput() {
        val step = FakeGpsUtils.getMoveStepFromInput(this, binding.inputStep)
        if (!FakeGpsUtils.isValidMoveStep(step)) {
            Toast.makeText(this, R.string.move_step_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        JoyStickManager.get().setMoveStep(step)
        hideKeyboard()
        updateStatus()
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(binding.inputLoc.windowToken, 0)
        binding.inputLoc.clearFocus()
        binding.inputStep.clearFocus()
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
            val point = LocPoint(latitude, longitude)
            if (JoyStickManager.get().isStarted) {
                // Choosing on the map while the simulation runs should move it
                // immediately instead of quietly filling the text field.
                applyPoint(point)
            } else {
                setInputPoint(point)
                updateStatus()
            }
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
        // The +/- buttons are part of the live controls: while the simulation
        // runs, the floating panel's arrows must move by the new distance
        // immediately instead of waiting for a stop/start.
        if (JoyStickManager.get().isStarted) {
            JoyStickManager.get().setMoveStep(next.toDouble())
        }
    }

    private fun updateButtons() {
        val started = JoyStickManager.get().isStarted
        binding.btnStart.setText(
            if (started) R.string.btn_stop else R.string.btn_start
        )
        // While running, the secondary button applies the entered coordinate to
        // the live session; the label says so instead of implying a restart.
        binding.btnSetLoc.setText(
            if (started) R.string.use_this_location else R.string.btn_set_new_loc
        )
        binding.btnSetLoc.isEnabled = started
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
        when {
            locPoint == null -> setInputText("")
            JoyStickManager.get().isStarted -> {
                // Same path as the map picker: a bookmark applies at once,
                // including any move step typed in the field.
                applyPoint(locPoint)
            }
            else -> {
                setInputPoint(locPoint)
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
