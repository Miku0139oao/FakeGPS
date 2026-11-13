package com.github.fakegps

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Display-size helpers for the floating joystick.
 */
object ScreenUtils {

    @JvmStatic
    fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    @JvmStatic
    fun getScreenWidth(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return context.resources.displayMetrics.widthPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.bounds.width()
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
        return metrics.widthPixels
    }

    @JvmStatic
    fun getScreenHeight(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return context.resources.displayMetrics.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.bounds.height()
        }
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
        return metrics.heightPixels
    }
}
