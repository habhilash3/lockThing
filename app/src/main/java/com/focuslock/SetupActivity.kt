package com.focuslock

import android.app.Activity
import android.os.Bundle

/** First-run assistant + diagnostics. Verifiable items show OK/NEEDS ATTENTION; MIUI-only items are marked "cannot be verified". */
class SetupActivity : Activity() {
    private class Item(val title: String, val desc: String, val state: Int, val action: String?, val go: (() -> Unit)?)
    private val OK = 0
    private val TODO = 1
    private val UNK = 2

    override fun onCreate(b: Bundle?) { super.onCreate(b); title = "Setup & diagnostics" }
    override fun onResume() { super.onResume(); build() }

    private fun build() {
        val (sv, col) = screen()
        col.add(label("Setup & diagnostics", 22f, true))
        col.add(label("These settings improve reliability on MIUI 12.5 but do not guarantee it. Menu names differ between builds; each button tries the MIUI screen and falls back to app info. Nothing is enabled automatically - you switch each one on yourself.", 13f, false, 0xFF555555.toInt()), 6)

        val a11yOn = Permissions.accessibilityEnabled(this)
        val items = listOf(
            Item("1. Accessibility service (REQUIRED)",
                "Detects which app comes to the foreground so FocusLock can count continuous use and show the break screen. It reads no screen content. Find FocusLock under Accessibility > Downloaded apps and switch it on." +
                    (if (a11yOn && !Permissions.accessibilityRunning()) "\nEnabled but NOT running: switch it off and on again." else ""),
                if (a11yOn && Permissions.accessibilityRunning()) OK else TODO, "Open Accessibility") { MiuiSetupGuide.openAccessibility(this) },
            Item("2. Usage access (recommended)",
                "Lets FocusLock double-check the foreground app, resume after a restart and verify the limit trigger.",
                if (Permissions.usageAccess(this)) OK else TODO, "Open Usage access") { MiuiSetupGuide.openUsageAccess(this) },
            Item("3. Display over other apps (recommended)",
                "Android lets apps with this permission start the break screen from the background.",
                if (Permissions.overlay(this)) OK else TODO, "Open overlay setting") { MiuiSetupGuide.openOverlay(this) },
            Item("4. MIUI: 'Display pop-up windows while running in the background'",
                "MIUI blocks background activity launches unless this is on. Without it FocusLock can only push you to the home screen instead of showing the break screen.",
                UNK, "Open MIUI permissions") { MiuiSetupGuide.openPopupPermission(this) },
            Item("5. MIUI: Autostart",
                "Helps FocusLock come back after a reboot or being stopped. Apps cannot read this setting.",
                UNK, "Open Autostart") { MiuiSetupGuide.openAutostart(this) },
            Item("6. MIUI: Battery saver = No restrictions",
                "Stops MIUI's battery manager from killing the service in the background. Apps cannot read this setting.",
                UNK, "Open battery saver") { MiuiSetupGuide.openBatterySaver(this) },
            Item("7. Android battery optimization",
                "Standard Android Doze exemption (separate from MIUI's battery saver). Choose 'All apps' > FocusLock > Don't optimize.",
                if (Permissions.batteryOptimizationIgnored(this)) OK else TODO, "Open battery optimization") { MiuiSetupGuide.openBatteryOptimization(this) },
            Item("8. Notifications",
                "Used only for the 'protection is OFF' warning if the accessibility service stops.",
                if (Permissions.notifications(this)) OK else TODO, "Open notification settings") { MiuiSetupGuide.openNotifications(this) },
            Item("9. Lock FocusLock in Recents (manual)",
                "Open Recents, long-press (or pull down) the FocusLock card and tap the lock icon so 'Clear all' does not kill it. Apps cannot read this.",
                UNK, null, null)
        )
        for (item in items) {
            val color = when (item.state) { OK -> 0xFF2E7D32; TODO -> 0xFFC62828; else -> 0xFFEF6C00 }.toInt()
            val status = when (item.state) { OK -> "OK"; TODO -> "NEEDS ATTENTION"; else -> "CANNOT BE VERIFIED - check manually" }
            col.add(label(item.title, 16f, true), 18)
            col.add(label(status, 13f, true, color), 2)
            col.add(label(item.desc, 13f, false, 0xFF444444.toInt()), 2)
            val go = item.go
            if (go != null) col.add(button(item.action ?: "Open") { go() }, 4)
        }
        col.add(label("Known limits: force-stopping FocusLock, switching off its accessibility service or uninstalling it ends enforcement (Android does not let ordinary apps prevent that). Saved restrictions are kept and resume enforcing once the service runs again.", 12f, false, 0xFF777777.toInt()), 24)
        col.add(button("Done") { finish() }, 12)
        setContentView(sv)
    }
}
