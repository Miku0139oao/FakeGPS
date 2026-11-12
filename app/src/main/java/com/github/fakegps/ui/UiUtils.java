package com.github.fakegps.ui;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge helpers for targetSdk 35+.
 */
public final class UiUtils {

    private UiUtils() {
    }

    public static void enableEdgeToEdge(Activity activity, int rootId) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }
        final int paddingLeft = root.getPaddingLeft();
        final int paddingTop = root.getPaddingTop();
        final int paddingRight = root.getPaddingRight();
        final int paddingBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    paddingLeft + bars.left,
                    paddingTop + bars.top,
                    paddingRight + bars.right,
                    paddingBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);

        // The window background behind the system bars is light.
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), root);
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
    }
}
