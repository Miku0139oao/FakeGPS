package com.github.fakegps.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import tiger.radio.loggerlibrary.Logger

/**
 * Button that keeps invoking clicks while it stays pressed.
 */
class JoyStickButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs) {

    private var isPressDown = false

    private val longPressDetectorRunnable = object : Runnable {
        override fun run() {
            if (isPressDown && isAttachedToWindow && isEnabled) {
                Logger.d(TAG, "invoke click")
                performClick()
                postDelayed(this, 500)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                isPressDown = true
                postDelayed(longPressDetectorRunnable, 1000)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressDetectorRunnable)
                isPressDown = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressDetectorRunnable)
        isPressDown = false
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val TAG = "JoyStickButton"
    }
}
