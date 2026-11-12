package com.github.fakegps.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageButton;

import tiger.radio.loggerlibrary.Logger;

/**
 * Button can keep invoke click event when keep pressed
 * Created by tiger on 7/23/16.
 */
public class JoyStickButton extends AppCompatImageButton {

    private static final String TAG = "JoyStickButton";

    private boolean mIsPressDown = false;

    public JoyStickButton(Context context) {
        super(context);
    }

    public JoyStickButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mIsPressDown = true;
                postDelayed(mLongPressDetectorRunnable, 1000);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(mLongPressDetectorRunnable);
                mIsPressDown = false;
                break;

        }

        return super.onTouchEvent(event);

    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(mLongPressDetectorRunnable);
        mIsPressDown = false;
        super.onDetachedFromWindow();
    }


    private Runnable mLongPressDetectorRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsPressDown && isAttachedToWindow() && isEnabled()) {
                Logger.d(TAG, "invoke click");
                performClick();
                postDelayed(this, 500);
            }
        }
    };
}
