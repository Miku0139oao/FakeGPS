package com.github.fakegps.ui

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import tiger.radio.loggerlibrary.Logger

/**
 * Button that keeps invoking clicks while it stays pressed.
 *
 * <p>Every step gives a short haptic tick, so a press that lands on the panel
 * background (which starts a drag instead) is immediately distinguishable from
 * a real arrow press.</p>
 */
class JoyStickButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs) {

    private var isPressDown = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (isPressDown && isAttachedToWindow && isEnabled) {
                Logger.d(TAG, "invoke click")
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                performClick()
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                isPressDown = true
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                postDelayed(repeatRunnable, LONG_PRESS_DELAY_MS)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(repeatRunnable)
                isPressDown = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        // Announced to accessibility services; the repeat runnable also calls
        // this for every step, so taps reach TalkBack users too.
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeatRunnable)
        isPressDown = false
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val TAG = "JoyStickButton"
        private const val LONG_PRESS_DELAY_MS = 450L
        private const val REPEAT_INTERVAL_MS = 150L
    }
}
