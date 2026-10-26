package com.focuslock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var rm: RestrictionManager
    private val rows = ArrayList<Pair<String, TextView>>()
    private val ticker = object : Runnable { override fun run() { refreshStatus(); handler.postDelayed(this, 1000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        store = Store(this); rm = RestrictionManager(this, store)
        if (!store.setupShown) { store.setupShown = true; startActivity(Intent(this, SetupActivity::class.java)) }
    }

    override fun onResume() { super.onResume(); rm.pruneExpired(); build(); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun build() {
        val (sv, col) = screen(); rows.clear()
        col.add(label("FocusLock", 26f, true))
        val issues = Permissions.issues(this)
        if (issues.isEmpty()) {
            col.add(label("● Monitoring active", 15f, true, 0xFF2E7D32.toInt()), 4)
        } else {
            col.add(label("⚠ Blocking is NOT reliably enforced", 15f, true, 0xFFC62828.toInt()), 4)
            issues.forEach { col.add(label("• $it", 13f, false, 0xFFC62828.toInt()), 2) }
            col.add(button("Fix in setup") { startActivity(Intent(this, SetupActivity::class.java)) }, 6)
        }
        col.add(label("Limit: ${store.limitMin} min continuous use → blocked for ${store.blockMin} min", 13f, false, 0xFF555555.toInt()), 8)

        col.add(label("Selected apps", 18f, true), 20)
        val pm = packageManager
        val pkgs = (store.selected() + store.restrictedPackages()).sortedBy { Apps.label(pm, it).lowercase() }
        if (pkgs.isEmpty()) col.add(label("No apps selected. Tap 'Manage apps'. Nothing is selected automatically.", 14f, false, 0xFF777777.toInt()), 6)
        for (pkg in pkgs) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(ImageView(this).apply { setImageDrawable(Apps.icon(pm, pkg)) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val txt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            txt.addView(label(Apps.label(pm, pkg), 16f, true))
            val st = label("", 13f); txt.addView(st)
            row.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            col.add(row, 10); rows += pkg to st
        }
        col.add(button("Manage apps") { startActivity(Intent(this, AppSelectionActivity::class.java)) }, 20)
        col.add(button("Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }, 4)
        col.add(button("Setup & diagnostics") { startActivity(Intent(this, SetupActivity::class.java)) }, 4)
        setContentView(sv)
        refreshStatus()
    }

    private fun refreshStatus() {
        for ((pkg, tv) in rows) {
            val rem = rm.remainingMs(pkg)
            if (rem > 0) {
                tv.text = "BLOCKED · ${formatHms(rem)} left (until ${SimpleDateFormat("HH:mm").format(Date(System.currentTimeMillis() + rem))})"
                tv.setTextColor(0xFFC62828.toInt())
            } else {
                tv.text = "Monitoring"; tv.setTextColor(Color.GRAY)
            }
        }
    }
}
