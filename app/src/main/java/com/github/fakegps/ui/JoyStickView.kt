package com.github.fakegps.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.github.fakegps.IJoyStickPresenter
import com.github.fakegps.ScreenUtils
import com.tencent.fakegps.R

/**
 * Floating joystick overlay.
 *
 * <p>Constructed with the application context and attached through
 * [WindowManager]; all window operations must stay on the main thread.</p>
 */
class JoyStickView(context: Context) : FrameLayout(context) {

    private var viewWidth = 0
    private var viewHeight = 0
    // Touch point x position according to the screen.
    private var xInScreen = 0f
    // Touch point y position according to the screen.
    private var yInScreen = 0f
    // Touch point x position according to the view.
    private var xInView = 0f
    // Touch point y position according to the view.
    private var yInView = 0f

    var isShowing: Boolean = false
        private set

    private var joyStickPresenter: IJoyStickPresenter? = null

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windowLayoutParams = WindowManager.LayoutParams()

    private val onClickListener = OnClickListener { view ->
        val presenter = joyStickPresenter ?: return@OnClickListener
        when (view.id) {
            R.id.btn_up -> presenter.onArrowUpClick()
            R.id.btn_down -> presenter.onArrowDownClick()
            R.id.btn_left -> presenter.onArrowLeftClick()
            R.id.btn_right -> presenter.onArrowRightClick()
            R.id.btn_set_loc -> presenter.onSetLocationClick()
            R.id.btn_fly_to -> presenter.onFlyClick()
            R.id.btn_bookmark -> presenter.onBookmarkLocationClick()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.joystick_layout, this)

        statusBarHeight = ScreenUtils.getStatusBarHeight(context)
        viewWidth = context.resources.getDimensionPixelSize(R.dimen.joystick_width)
        viewHeight = context.resources.getDimensionPixelSize(R.dimen.joystick_height)

        // Use TYPE_APPLICATION_OVERLAY for Android O (26) and above.
        windowLayoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        windowLayoutParams.format = PixelFormat.RGBA_8888
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowLayoutParams.gravity = Gravity.LEFT or Gravity.TOP

        // The real width and height is needed, or the view cannot be shown.
        windowLayoutParams.width = viewWidth
        windowLayoutParams.height = viewHeight
        windowLayoutParams.x = maxOf(0, ScreenUtils.getScreenWidth(context) - viewWidth)
        windowLayoutParams.y = ScreenUtils.getScreenHeight(context) / 2

        findViewById<View>(R.id.btn_set_loc).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_fly_to).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_bookmark).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_bookmark).setOnLongClickListener {
            joyStickPresenter?.onCopyLocationClick()
            true
        }

        findViewById<View>(R.id.btn_up).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_left).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_right).setOnClickListener(onClickListener)
        findViewById<View>(R.id.btn_down).setOnClickListener(onClickListener)
    }

    fun addToWindow() {
        try {
            windowManager.addView(this, windowLayoutParams)
            isShowing = true
        } catch (e: Exception) {
            // Overlay permission can be revoked between the check and this call.
            isShowing = false
        }
    }

    fun removeFromWindow() {
        try {
            if (isAttachedToWindow) {
                windowManager.removeView(this)
            }
        } catch (e: Exception) {
            // The system may already have detached the window.
        } finally {
            isShowing = false
        }
    }

    fun setJoyStickPresenter(presenter: IJoyStickPresenter?) {
        joyStickPresenter = presenter
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                xInView = event.x
                yInView = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                xInScreen = event.rawX
                yInScreen = event.rawY - statusBarHeight
                updateViewPosition()
            }
        }
        return true
    }

    private fun updateViewPosition() {
        windowLayoutParams.x = (xInScreen - xInView).toInt()
        windowLayoutParams.y = (yInScreen - yInView).toInt()
        windowManager.updateViewLayout(this, windowLayoutParams)
    }

    private companion object {
        private var statusBarHeight = 0
    }
}
