package com.github.fakegps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Helpers for keeping the mock engine alive while the app is in the background.
 *
 * <p>Stock Android lets a foreground service run indefinitely, but most vendors
 * freeze background apps anyway. Each has its own allowlist screen, and on some
 * of them (vivo/iQOO in particular) the process is frozen a few seconds after
 * the last activity stops, which makes every app fall back to the real
 * location. The methods below open the vendor screen when it is known and fall
 * back to the system battery-optimization dialog otherwise.</p>
 */
object BackgroundExecution {

    enum class Vendor { VIVO, XIAOMI, OPPO, HUAWEI, SAMSUNG, MEIZU, OTHER }

    @JvmStatic
    fun vendor(): Vendor {
        val name = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            name.contains("vivo") || name.contains("iqoo") -> Vendor.VIVO
            name.contains("xiaomi") || name.contains("redmi") || name.contains("poco") ->
                Vendor.XIAOMI
            name.contains("oppo") || name.contains("realme") || name.contains("oneplus") ->
                Vendor.OPPO
            name.contains("huawei") || name.contains("honor") -> Vendor.HUAWEI
            name.contains("samsung") -> Vendor.SAMSUNG
            name.contains("meizu") -> Vendor.MEIZU
            else -> Vendor.OTHER
        }
    }

    /**
     * Vendors known to freeze background apps even when a location foreground
     * service is running. Other devices only need the stock battery setting.
     */
    @JvmStatic
    fun needsBackgroundGuidance(): Boolean = vendor() != Vendor.OTHER

    @JvmStatic
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the best background-execution screen this device offers: the
     * vendor allowlist when known, else the stock battery-optimization dialog,
     * else the app details page. Always returns after trying every fallback.
     */
    @JvmStatic
    fun openBackgroundSettings(context: Context) {
        val opened = when (vendor()) {
            Vendor.VIVO -> openVivo(context)
            Vendor.XIAOMI -> openXiaomi(context)
            Vendor.OPPO -> openOppo(context)
            Vendor.HUAWEI -> openHuawei(context)
            Vendor.SAMSUNG -> openSamsung(context)
            Vendor.MEIZU, Vendor.OTHER -> false
        }
        if (!opened) {
            requestIgnoreBatteryOptimizations(context)
        }
    }

    @JvmStatic
    fun openVivoBackgroundSettings(context: Context): Boolean = openVivo(context)

    /** vivo/iQOO background power page, then the autostart page. */
    private fun openVivo(context: Context): Boolean =
        startComponent(context, "com.iqoo.powersaving", "com.iqoo.powersaving.BackgroundHighUsageActivity") ||
            startComponent(
                context,
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )

    /** MIUI/HyperOS per-app battery policy, then autostart management. */
    private fun openXiaomi(context: Context): Boolean {
        val powerKeeper = Intent().setComponent(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
        )
        powerKeeper.putExtra("package_name", context.packageName)
        powerKeeper.putExtra(
            "package_label",
            context.applicationInfo.loadLabel(context.packageManager).toString()
        )
        if (start(context, powerKeeper)) {
            return true
        }
        return startComponent(
            context,
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
    }

    /** ColorOS/OxygenOS autostart list, then the battery usage model page. */
    private fun openOppo(context: Context): Boolean {
        val components = arrayOf(
            "com.coloros.safecenter" to
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to
                "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.oppoguardelf" to
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            "com.oplus.battery" to
                "com.oplus.powermanager.fuelgaue.PowerControlActivity"
        )
        for ((packageName, className) in components) {
            if (startComponent(context, packageName, className)) {
                return true
            }
        }
        return false
    }

    /** EMUI/MagicOS startup manager. */
    private fun openHuawei(context: Context): Boolean {
        val components = arrayOf(
            "com.huawei.systemmanager" to
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.hihonor.systemmanager" to
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.hihonor.systemmanager" to
                "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity"
        )
        for ((packageName, className) in components) {
            if (startComponent(context, packageName, className)) {
                return true
            }
        }
        return false
    }

    /** Samsung Device care → Battery. */
    private fun openSamsung(context: Context): Boolean {
        val components = arrayOf(
            "com.samsung.android.lool" to
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.samsung.android.sm" to
                "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        for ((packageName, className) in components) {
            if (startComponent(context, packageName, className)) {
                return true
            }
        }
        return false
    }

    /**
     * Opens the system "allow background activity" dialog, falling back to the
     * app details page when the OEM does not provide it.
     */
    @JvmStatic
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.packageName)
            )
            if (start(context, intent)) {
                return
            }
        }
        openAppDetails(context)
    }

    @JvmStatic
    fun openAppDetails(context: Context) {
        start(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + context.packageName)
            )
        )
    }

    private fun startComponent(context: Context, packageName: String, className: String): Boolean {
        val intent = Intent().setComponent(ComponentName(packageName, className))
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (ignored: Exception) {
            false
        }
    }
}
