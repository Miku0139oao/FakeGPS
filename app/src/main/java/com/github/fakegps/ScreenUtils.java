package com.github.fakegps;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Created by tiger on 7/22/16.
 */
public class ScreenUtils {

    public static int getStatusBarHeight(Context context) {
        int resourceId = context.getResources()
                .getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return context.getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }


    public static int getScreenWidth(Context context) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return context.getResources().getDisplayMetrics().widthPixels;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.getCurrentWindowMetrics().getBounds().width();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return context.getResources().getDisplayMetrics().heightPixels;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.getCurrentWindowMetrics().getBounds().height();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }

}
