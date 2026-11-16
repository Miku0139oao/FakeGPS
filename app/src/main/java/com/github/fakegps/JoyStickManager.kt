package com.github.fakegps

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.github.fakegps.model.LocPoint
import com.github.fakegps.ui.FlyToActivity
import com.github.fakegps.ui.JoyStickView
import com.github.fakegps.ui.MainActivity
import com.github.fakegps.ui.MapPickerActivity
import com.github.fakegps.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    // Owned by this process-wide singleton; used for short overlay-side writes.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var locationThread: LocationThread? = null
    private var joyStickView: JoyStickView? = null
    private var joyStickLocaleTag: String? = null

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
            Toast.makeText(localizedContext(), R.string.mock_app_not_selected, Toast.LENGTH_LONG)
                .show()
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

    /**
     * Re-applies the overlay visibility after the floating-panel setting
     * changes. The panel can also be turned off because Android blocks some
     * permission dialogs while another app draws over the screen.
     */
    fun refreshOverlay() {
        updateOverlayVisibility()
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
        if (!SettingsStore.showJoystick(appContext)) {
            hideJoyStick()
            return
        }
        val localeTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (joyStickView != null && joyStickLocaleTag != localeTag) {
            // The overlay is inflated from the application context, which does
            // not inherit the per-app locale. Recreate it when the language
            // changes so its labels follow the app language.
            hideJoyStick()
            joyStickView = null
        }
        val view = joyStickView ?: JoyStickView(localizedContext()).also {
            it.setJoyStickPresenter(this)
            joyStickView = it
            joyStickLocaleTag = localeTag
        }
        if (!view.isShowing) {
            view.addToWindow()
        }
    }

    private fun localizedContext(): Context = AppLocale.context(appContext)

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
            Toast.makeText(localizedContext(), R.string.stop_fly, Toast.LENGTH_SHORT).show()
        } else {
            FlyToActivity.startPage(appContext)
        }
    }

    override fun onBookmarkLocationClick() {
        Logger.d(TAG, "onBookmarkLocationClick")
        val locPoint = currentLocPoint
        if (locPoint == null) {
            Toast.makeText(localizedContext(), R.string.service_not_started, Toast.LENGTH_SHORT)
                .show()
            return
        }
        // Bookmark straight from the overlay: opening a naming screen would
        // pull the user out of the app they are testing the location in.
        ioScope.launch {
            val result = BookmarkRepository.savePoint(locPoint, BookmarkNames.autoName(locPoint))
            val message = when (result) {
                BookmarkRepository.DUPLICATE -> R.string.bookmark_exists
                -1L -> R.string.bookmark_save_failed
                else -> R.string.bookmark_saved
            }
            Toast.makeText(localizedContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCopyLocationClick() {
        Logger.d(TAG, "onCopyLocationClick")
        val locPoint = currentLocPoint
        if (locPoint != null) {
            FakeGpsUtils.copyToClipboard(appContext, locPoint.toString())
            val context = localizedContext()
            Toast.makeText(
                context,
                context.getString(R.string.location_copied) + "\n" + locPoint,
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
