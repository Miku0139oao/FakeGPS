package com.github.fakegps;

import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.widget.EditText;

import com.github.fakegps.model.LocPoint;

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
            cmb.setPrimaryClip(ClipData.newPlainText("FakeGPS Location", content.trim()));
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
        LocPoint point = null;
        String text = editText.getText().toString().replace("(", "").replace(")", "");
        String[] split = text.split(",");
        if (split.length == 2) {
            try {
                double lat = Double.parseDouble(split[0].trim());
                double lon = Double.parseDouble(split[1].trim());
                if (isValidLatitude(lat) && isValidLongitude(lon)) {
                    point = new LocPoint(lat, lon);
                } else {
                    Logger.e(TAG, "Location out of range: " + lat + ", " + lon, null);
                }
            } catch (NumberFormatException e) {
                Logger.e(TAG, "Parse loc point error!", e);
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
