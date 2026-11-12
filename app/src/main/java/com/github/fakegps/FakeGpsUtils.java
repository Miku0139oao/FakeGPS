package com.github.fakegps;

import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.Process;
import android.widget.EditText;

import com.github.fakegps.model.LocPoint;
import com.tencent.fakegps.BuildConfig;

import tiger.radio.loggerlibrary.Logger;

/**
 * Created by tiger on 7/23/16.
 */
public final class FakeGpsUtils {
    private static final String TAG = "FakeGpsUtils";

    private FakeGpsUtils() {
    }

    public static void copyToClipboard(Context context, String content) {
        ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cmb != null) {
            ClipData clip = ClipData.newPlainText("FakeGPS Location", content.trim());
            if (Build.VERSION.SDK_INT >= 33) {
                PersistableBundle extras = new PersistableBundle();
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
                clip.getDescription().setExtras(extras);
            }
            cmb.setPrimaryClip(clip);
        }
    }

    /**
     * Android 6.0+ requires the user to select this app in Developer options.
     * On Android 5.x the ACCESS_MOCK_LOCATION permission is granted at install time.
     */
    public static boolean isMockLocationAppSelected(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        try {
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Logger.e(TAG, "Unable to check mock location app op", e);
            return false;
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static boolean isValidLatitude(double latitude) {
        return isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0;
    }

    public static boolean isValidLongitude(double longitude) {
        return isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0;
    }

    public static LocPoint getLocPointFromInput(Context context, EditText editText) {
        String text = editText.getText().toString();
        LocPoint point = CoordinateParser.parse(text);
        if (point == null) {
            if (BuildConfig.DEBUG) {
                Logger.e(TAG, "Unable to parse location input: " + text, null);
            } else {
                Logger.e(TAG, "Unable to parse location input", null);
            }
        }
        return point;
    }

    public static boolean isValidMoveStep(double step) {
        return isFinite(step) && step > 0;
    }

    public static double getMoveStepFromInput(Context context, EditText editText) {
        double step = 0;
        String stepStr = editText.getText().toString().trim();
        try {
            double value = Double.parseDouble(stepStr);
            if (isValidMoveStep(value)) {
                step = value;
            }
        } catch (NumberFormatException e) {
            Logger.e(TAG, "Parse move step error!", e);
        }

        return step;
    }

    public static int getIntValueFromInput(Context context, EditText editText) {
        int value = 0;
        String stepStr = editText.getText().toString().trim();
        try {
            value = Integer.parseInt(stepStr);
        } catch (NumberFormatException e) {
            Logger.e(TAG, "Parse integer input error!", e);
        }

        return value;
    }

}
