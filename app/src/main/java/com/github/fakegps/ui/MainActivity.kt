package com.github.fakegps.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.net.toUri
import com.github.fakegps.DbUtils
import com.github.fakegps.FakeGpsUtils
import com.github.fakegps.JoyStickManager
import com.github.fakegps.model.LocPoint
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tencent.fakegps.R
import com.tencent.fakegps.databinding.ActivityMainBinding
import java.math.BigDecimal

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

        val currentLocPoint = JoyStickManager.get().currentLocPoint
        if (currentLocPoint != null) {
            binding.inputLoc.setText(currentLocPoint.toString())
        } else {
            val lastLocPoint = DbUtils.getLastLocPoint(this)
            if (!TextUtils.isEmpty(lastLocPoint)) {
                binding.inputLoc.setText(lastLocPoint)
            } else {
                binding.inputLoc.setText(LocPoint(LAT_DEFAULT, LON_DEFAULT).toString())
            }
        }
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
            R.id.btn_save_current -> {
                val point = currentOrEnteredPoint()
                if (point != null) {
                    BookmarkActivity.startPage(this, getString(R.string.bookmark), point)
                } else {
                    Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onStartStopClicked() {
        if (JoyStickManager.get().isStarted) {
            JoyStickManager.get().currentLocPoint?.let {
                DbUtils.saveLastLocPoint(this, it)
            }
            JoyStickManager.get().stop()
            finish()
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

    private fun updateStatus() {
        val started = JoyStickManager.get().isStarted
        binding.tvStatus.setText(
            if (started) R.string.status_active else R.string.status_inactive
        )
        binding.tvStatusHint.visibility = if (started) View.GONE else View.VISIBLE
        val point = currentOrEnteredPoint()
        binding.tvCurrentCoords.text = point?.toString() ?: "—"
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
        updateStatus()
    }

    companion object {
        private const val LAT_DEFAULT = 37.802406
        private const val LON_DEFAULT = -122.401779

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
