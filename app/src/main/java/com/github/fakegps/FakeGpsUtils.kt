package com.github.fakegps

import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.Process
import android.widget.EditText
import com.github.fakegps.model.LocPoint
import com.github.fakegps.BuildConfig
import tiger.radio.loggerlibrary.Logger

/**
 * Shared parsing, validation, clipboard and mock-location helpers.
 */
object FakeGpsUtils {

    private const val TAG = "FakeGpsUtils"

    @JvmStatic
    fun copyToClipboard(context: Context, content: String) {
        val cmb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cmb != null) {
            val clip = ClipData.newPlainText("FakeGPS Location", content.trim())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val extras = PersistableBundle()
                extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                clip.description.extras = extras
            }
            cmb.setPrimaryClip(clip)
        }
    }

    /**
     * Android 6.0+ requires the user to select this app in Developer options.
     * On Android 5.x the ACCESS_MOCK_LOCATION permission is granted at install time.
     */
    @JvmStatic
    fun isMockLocationAppSelected(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        return try {
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Logger.e(TAG, "Unable to check mock location app op", e)
            false
        }
    }

    private fun isFinite(value: Double): Boolean =
        !value.isNaN() && !value.isInfinite()

    @JvmStatic
    fun isValidLatitude(latitude: Double): Boolean =
        isFinite(latitude) && latitude >= -90.0 && latitude <= 90.0

    @JvmStatic
    fun isValidLongitude(longitude: Double): Boolean =
        isFinite(longitude) && longitude >= -180.0 && longitude <= 180.0

    @JvmStatic
    fun getLocPointFromInput(context: Context, editText: EditText): LocPoint? =
        parseLocation(editText.text.toString())

    @JvmStatic
    fun parseLocation(text: String?): LocPoint? {
        val point = CoordinateParser.parse(text)
        if (point == null) {
            if (BuildConfig.DEBUG) {
                Logger.e(TAG, "Unable to parse location input: $text", null)
            } else {
                Logger.e(TAG, "Unable to parse location input", null)
            }
        }
        return point
    }

    @JvmStatic
    fun isValidMoveStep(step: Double): Boolean =
        isFinite(step) && step > 0

    @JvmStatic
    fun getMoveStepFromInput(context: Context, editText: EditText): Double =
        parseMoveStep(editText.text.toString())

    @JvmStatic
    fun parseMoveStep(text: String): Double =
        try {
            val value = text.trim().toDouble()
            if (isValidMoveStep(value)) value else 0.0
        } catch (e: NumberFormatException) {
            Logger.e(TAG, "Parse move step error!", e)
            0.0
        }

    @JvmStatic
    fun getIntValueFromInput(context: Context, editText: EditText): Int =
        parseIntValue(editText.text.toString())

    @JvmStatic
    fun parseIntValue(text: String): Int =
        try {
            text.trim().toInt()
        } catch (e: NumberFormatException) {
            Logger.e(TAG, "Parse integer input error!", e)
            0
        }
}
