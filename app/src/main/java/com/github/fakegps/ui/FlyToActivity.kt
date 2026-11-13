package com.github.fakegps.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.fakegps.DbUtils
import com.github.fakegps.FakeGpsUtils
import com.github.fakegps.JoyStickManager
import com.github.fakegps.model.LocPoint
import com.tencent.fakegps.R
import com.tencent.fakegps.databinding.ActivityFlyBinding
import kotlinx.coroutines.launch

class FlyToActivity : BaseBookmarkActivity(), View.OnClickListener {

    private lateinit var binding: ActivityFlyBinding
    private var wasFlying = false

    private val mapFromLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleMapResult(result, binding.inputFromLoc)
    }

    private val mapToLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleMapResult(result, binding.inputLoc)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        UiUtils.enableEdgeToEdge(this, R.id.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // From location input.
        val currentLocPoint = JoyStickManager.get().currentLocPoint
        if (currentLocPoint != null) {
            binding.inputFromLoc.setText(currentLocPoint.toString())
        } else {
            val lastLocPoint = DbUtils.getLastLocPoint(this)
            if (!TextUtils.isEmpty(lastLocPoint)) {
                binding.inputFromLoc.setText(lastLocPoint)
            } else {
                binding.inputFromLoc.setText(LocPoint(LAT_DEFAULT, LON_DEFAULT).toString())
            }
        }

        binding.inputFlyTime.setText(FLY_TIME_DEFAULT.toString())

        binding.btnFly.setOnClickListener(this)
        binding.btnFromMap.setOnClickListener(this)
        binding.btnToMap.setOnClickListener(this)
        updateBtn()

        initBookmarkList(binding.listBookmark)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                JoyStickManager.get().status.collect { state ->
                    val flying = state.isFlying
                    updateBtn()
                    if (wasFlying && !flying) {
                        Toast.makeText(this@FlyToActivity, R.string.fly_complete, Toast.LENGTH_SHORT)
                            .show()
                    }
                    wasFlying = flying
                }
            }
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_from_map -> openMapFor(binding.inputFromLoc, mapFromLauncher)
            R.id.btn_to_map -> openMapFor(binding.inputLoc, mapToLauncher)
            R.id.btn_fly -> onFlyClicked()
        }
    }

    private fun onFlyClicked() {
        if (!JoyStickManager.get().isStarted) {
            Toast.makeText(this, R.string.start_first, Toast.LENGTH_SHORT).show()
            return
        }

        if (JoyStickManager.get().isFlyMode) {
            wasFlying = false
            JoyStickManager.get().stopFlyMode()
        } else {
            val flyTime = FakeGpsUtils.getIntValueFromInput(this, binding.inputFlyTime)
            val fromPoint = FakeGpsUtils.getLocPointFromInput(this, binding.inputFromLoc)
            val toPoint = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
            if (fromPoint == null || toPoint == null) {
                Toast.makeText(this, R.string.input_invalid, Toast.LENGTH_SHORT).show()
            } else if (flyTime <= 0) {
                Toast.makeText(this, R.string.fly_time_invalid, Toast.LENGTH_SHORT).show()
            } else {
                // Jump to the "From" location first, then fly to "To".
                JoyStickManager.get().jumpToLocation(fromPoint)
                JoyStickManager.get().flyToLocation(toPoint, flyTime)
                wasFlying = true
            }
        }
        updateBtn()
    }

    override fun onStart() {
        super.onStart()
        wasFlying = JoyStickManager.get().isFlyMode
        updateBtn()
    }

    override fun onResume() {
        super.onResume()
        updateBtn()
    }

    private fun openMapFor(field: EditText, launcher: ActivityResultLauncher<Intent>) {
        val point = FakeGpsUtils.getLocPointFromInput(this, field)
            ?: JoyStickManager.get().currentLocPoint
        launcher.launch(MapPickerActivity.createIntent(this, point))
    }

    private fun handleMapResult(result: ActivityResult, target: EditText) {
        if (result.resultCode != RESULT_OK) {
            return
        }
        val data = result.data ?: return
        val latitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
        val longitude = data.getDoubleExtra(MapPickerActivity.EXTRA_LON, Double.NaN)
        if (FakeGpsUtils.isValidLatitude(latitude) &&
            FakeGpsUtils.isValidLongitude(longitude)
        ) {
            target.setText(LocPoint(latitude, longitude).toString())
            target.setSelection(target.text?.length ?: 0)
        }
    }

    override fun onBookmarkSelected(locPoint: LocPoint?) {
        binding.inputLoc.setText(locPoint?.toString() ?: "")
    }

    private fun updateBtn() {
        val started = JoyStickManager.get().isStarted
        binding.btnFly.isEnabled = started
        binding.btnFly.setText(
            if (JoyStickManager.get().isFlyMode) R.string.btn_fly_stop else R.string.btn_fly_start
        )
    }

    companion object {
        private const val LAT_DEFAULT = 23.151637
        private const val LON_DEFAULT = 113.344721
        private const val FLY_TIME_DEFAULT = 60

        @JvmStatic
        fun startPage(context: Context) {
            val intent = Intent(context, FlyToActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
