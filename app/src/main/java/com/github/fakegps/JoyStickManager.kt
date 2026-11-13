package com.github.fakegps

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.github.fakegps.model.LocPoint
import com.github.fakegps.ui.BookmarkActivity
import com.github.fakegps.ui.FlyToActivity
import com.github.fakegps.ui.JoyStickView
import com.github.fakegps.ui.MainActivity
import com.github.fakegps.ui.MapPickerActivity
import com.tencent.fakegps.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tiger.radio.loggerlibrary.Logger

/**
 * Status exposed to lifecycle-aware UI. Only start/fly state is published; the
 * coordinate card keeps its existing lifecycle-driven refresh behavior.
 */
data class MovementStatus(
    val isStarted: Boolean,
    val isFlying: Boolean
)

/**
 * Process-wide movement controller and joystick presenter.
 *
 * <p>The controller is main-thread affine for service and overlay operations;
 * the engine thread only reads movement state and publishes status.</p>
 */
object JoyStickManager : IJoyStickPresenter {

    private const val TAG = "JoyStickManager"

    private lateinit var appContext: Context
    private val movementState = MovementState { SystemClock.elapsedRealtime() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var locationThread: LocationThread? = null
    private var joyStickView: JoyStickView? = null

    // Incremented while any of our Activities is started. The overlay is only
    // shown over other apps, never on top of our own screens.
    private var appActivitiesStarted = 0

    @Volatile
    private var started = false

    private val _status = MutableStateFlow(MovementStatus(isStarted = false, isFlying = false))
    val status: StateFlow<MovementStatus> = _status.asStateFlow()

    private val engineListener = LocationThread.Listener {
        mainHandler.post {
            stop()
            Toast.makeText(appContext, R.string.mock_app_not_selected, Toast.LENGTH_LONG).show()
        }
    }

    @JvmStatic
    fun get(): JoyStickManager = this

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val currentLocPoint: LocPoint?
        get() = movementState.currentPoint()

    val moveStep: Double
        get() = movementState.moveStep()

    val isStarted: Boolean
        get() = started

    val isFlyMode: Boolean
        get() = movementState.isFlyMode()

    @Synchronized
    fun start(locPoint: LocPoint) {
        movementState.start(locPoint)
        ensureEngine()
        locationThread?.requestStart()

        // Start foreground service for Android 8.0+.
        startForegroundService()

        started = true
        updateOverlayVisibility()
        publishStatus()
    }

    @Synchronized
    fun stop() {
        locationThread?.requestStop()

        // Stop foreground service.
        stopForegroundService()

        hideJoyStick()
        movementState.resetFly()
        started = false
        publishStatus()
    }

    /** Called from Application.ActivityLifecycleCallbacks on the main thread. */
    fun onAppActivityStarted() {
        appActivitiesStarted++
        updateOverlayVisibility()
    }

    /** Called from Application.ActivityLifecycleCallbacks on the main thread. */
    fun onAppActivityStopped() {
        appActivitiesStarted = (appActivitiesStarted - 1).coerceAtLeast(0)
        updateOverlayVisibility()
    }

    private fun updateOverlayVisibility() {
        if (started && appActivitiesStarted == 0) {
            showJoyStick()
        } else {
            hideJoyStick()
        }
    }

    private fun ensureEngine() {
        if (locationThread != null) {
            return
        }
        val engine = LocationThread(appContext, engineListener)
        engine.startThread()
        locationThread = engine
    }

    private fun startForegroundService() {
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, FakeLocationService::class.java)
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            appContext.stopService(Intent(appContext, FakeLocationService::class.java))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to stop foreground service", e)
        }
    }

    fun showJoyStick() {
        val view = joyStickView ?: JoyStickView(appContext).also {
            it.setJoyStickPresenter(this)
            joyStickView = it
        }
        if (!view.isShowing) {
            view.addToWindow()
        }
    }

    fun hideJoyStick() {
        val view = joyStickView
        if (view != null && view.isShowing) {
            view.removeFromWindow()
        }
    }

    /**
     * Returns a new point every call. When a flight is active the point is the
     * linear interpolation between the snapshot origin and the target, based on
     * real elapsed time rather than the number of ticks.
     */
    fun getUpdateLocPoint(): LocPoint? {
        val tick = movementState.tick() ?: return null
        if (tick.flyCompleted) {
            publishStatus()
        }
        return tick.point
    }

    fun jumpToLocation(location: LocPoint) {
        movementState.jumpTo(location)
        publishStatus()
    }

    fun flyToLocation(location: LocPoint, flyTimeSeconds: Int) {
        movementState.flyTo(location, flyTimeSeconds)
        if (flyTimeSeconds > 0) {
            publishStatus()
        }
    }

    fun stopFlyMode() {
        movementState.stopFly()
        publishStatus()
    }

    fun setMoveStep(moveStep: Double) {
        movementState.setMoveStep(moveStep)
    }

    private fun publishStatus() {
        _status.value = MovementStatus(
            isStarted = started,
            isFlying = movementState.isFlyMode()
        )
    }

    override fun onSetLocationClick() {
        Logger.d(TAG, "onSetLocationClick")
        MainActivity.startPage(appContext)
    }

    override fun onMapClick() {
        Logger.d(TAG, "onMapClick")
        MapPickerActivity.startForOverlay(appContext, currentLocPoint)
    }

    override fun onFlyClick() {
        Logger.d(TAG, "onFlyClick")
        if (movementState.isFlyMode()) {
            stopFlyMode()
            Toast.makeText(appContext, R.string.stop_fly, Toast.LENGTH_SHORT).show()
        } else {
            FlyToActivity.startPage(appContext)
        }
    }

    override fun onBookmarkLocationClick() {
        Logger.d(TAG, "onBookmarkLocationClick")
        val locPoint = currentLocPoint
        if (locPoint != null) {
            BookmarkActivity.startPage(appContext, appContext.getString(R.string.bookmark), locPoint)
            Toast.makeText(
                appContext,
                appContext.getString(R.string.bookmark_save_hint) + "\n" + locPoint,
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(appContext, R.string.service_not_started, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCopyLocationClick() {
        Logger.d(TAG, "onCopyLocationClick")
        val locPoint = currentLocPoint
        if (locPoint != null) {
            FakeGpsUtils.copyToClipboard(appContext, locPoint.toString())
            Toast.makeText(
                appContext,
                appContext.getString(R.string.location_copied) + "\n" + locPoint,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onArrowUpClick() {
        Logger.d(TAG, "onArrowUpClick")
        movementState.arrowUp()
    }

    override fun onArrowDownClick() {
        Logger.d(TAG, "onArrowDownClick")
        movementState.arrowDown()
    }

    override fun onArrowLeftClick() {
        Logger.d(TAG, "onArrowLeftClick")
        movementState.arrowLeft()
    }

    override fun onArrowRightClick() {
        Logger.d(TAG, "onArrowRightClick")
        movementState.arrowRight()
    }
}
