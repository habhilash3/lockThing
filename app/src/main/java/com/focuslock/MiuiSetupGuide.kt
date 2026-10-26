package com.focuslock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** MIUI screens move between builds and some are not exported, so every launch is try/catch with an app-details fallback. */
object MiuiSetupGuide {
    private fun details(a: Activity) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}"))

    private fun launch(a: Activity, manual: String, vararg intents: Intent) {
        for (i in intents) { try { a.startActivity(i); return } catch (_: Exception) {} }
        try { a.startActivity(details(a)) } catch (_: Exception) {}
        Toast.makeText(a, manual, Toast.LENGTH_LONG).show()
    }

    fun openAutostart(a: Activity) = launch(a,
        "Open Security > Permissions > Autostart and enable FocusLock.",
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))

    fun openPopupPermission(a: Activity) = launch(a,
        "Settings > Apps > Manage apps > FocusLock > Other permissions > enable 'Display pop-up windows while running in the background'.",
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", a.packageName))

    fun openBatterySaver(a: Activity) = launch(a,
        "Settings > Apps > Manage apps > FocusLock > Battery saver > choose 'No restrictions'.",
        Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", a.packageName).putExtra("package_label", "FocusLock"))

    fun openAccessibility(a: Activity) { try { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {} }

    fun openUsageAccess(a: Activity) = launch(a, "Settings > Privacy > Special permissions > Usage access > FocusLock.",
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${a.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openOverlay(a: Activity) = launch(a, "Settings > Apps > Manage apps > FocusLock > Other permissions > Display over other apps.",
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))

    fun openBatteryOptimization(a: Activity) = launch(a, "Settings > Battery > App battery saver > FocusLock > No restrictions.",
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openNotifications(a: Activity) = launch(a, "Settings > Notifications > FocusLock > allow.",
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
}
