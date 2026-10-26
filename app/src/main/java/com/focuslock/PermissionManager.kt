package com.focuslock

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** Reports only what an ordinary app can truly check. MIUI-specific toggles are NOT inspectable and never reported as verified. */
object Permissions {
    fun accessibilityEnabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, FocusAccessibilityService::class.java)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun accessibilityRunning() = FocusAccessibilityService.instance != null

    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT && ctx.checkSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED)
    }

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)
    fun notifications(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun batteryOptimizationIgnored(ctx: Context) =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Problems that make blocking unreliable right now. */
    fun issues(ctx: Context): List<String> {
        val l = ArrayList<String>()
        if (!accessibilityEnabled(ctx)) l += "Accessibility service is OFF - blocking is NOT enforced."
        else if (!accessibilityRunning()) l += "Accessibility service is enabled but not running (MIUI may have stopped it). Turn it off and on again in Accessibility settings."
        if (!usageAccess(ctx)) l += "Usage access not granted - recovery and validation are limited."
        return l
    }
}
