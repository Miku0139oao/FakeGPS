package com.github.fakegps.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge helpers for targetSdk 35+.
 */
object UiUtils {

    @JvmStatic
    fun enableEdgeToEdge(activity: Activity, rootId: Int) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val root = activity.findViewById<View>(rootId) ?: return

        val paddingLeft = root.paddingLeft
        val paddingTop = root.paddingTop
        val paddingRight = root.paddingRight
        val paddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, ime.bottom)
            view.setPadding(
                paddingLeft + bars.left,
                paddingTop + bars.top,
                paddingRight + bars.right,
                paddingBottom + bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        // Match the system bar icon color to the current theme.
        val nightMode = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        val lightBars = nightMode != Configuration.UI_MODE_NIGHT_YES
        val controller = WindowCompat.getInsetsController(activity.window, root)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }
}
