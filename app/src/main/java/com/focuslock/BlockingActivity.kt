package com.focuslock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date

/** No bypass button. Leaving it (back/home/recents) goes home; reopening the app triggers it again. */
class BlockingActivity : Activity() {
    companion object {
        const val EXTRA_PKG = "pkg"
        fun launch(ctx: Context, pkg: String) {
            try {
                ctx.startActivity(Intent(ctx, BlockingActivity::class.java).putExtra(EXTRA_PKG, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            } catch (_: Exception) {}
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var rm: RestrictionManager
    private var pkg = ""
    private lateinit var timeView: TextView
    private lateinit var untilView: TextView
    private lateinit var nameView: TextView
    private lateinit var homeBtn: Button
    private val tick = object : Runnable { override fun run() { update(); handler.postDelayed(this, 1000) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        rm = RestrictionManager(this, Store(this))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFF101820.toInt()); setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        col.add(label("TIME TO TAKE A BREAK", 24f, true, Color.WHITE).apply { gravity = Gravity.CENTER })
        nameView = label("", 18f, true, 0xFFFFB74D.toInt()).apply { gravity = Gravity.CENTER }; col.add(nameView, 20)
        col.add(label("You've reached your continuous usage limit.\nThis application is temporarily blocked.", 16f, false, 0xFFDDDDDD.toInt()).apply { gravity = Gravity.CENTER }, 16)
        timeView = label("", 38f, true, Color.WHITE).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
        col.add(timeView, 24)
        untilView = label("", 14f, false, 0xFF9E9E9E.toInt()).apply { gravity = Gravity.CENTER }; col.add(untilView, 4)
        col.add(label("Use this time for something that matters to you.", 16f, false, 0xFFDDDDDD.toInt()).apply { gravity = Gravity.CENTER }, 24)
        homeBtn = button("Go to home screen") { goHome() }; col.add(homeBtn, 32)
        setContentView(col)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); intent = i; pkg = i.getStringExtra(EXTRA_PKG) ?: pkg; update() }
    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onStop() { super.onStop(); finishAndRemoveTask() }   // never linger in the back stack

    @Deprecated("Back goes home, never to the blocked app")
    override fun onBackPressed() { goHome() }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }

    private fun update() {
        val name = Apps.label(packageManager, pkg)
        nameView.text = name
        val rem = rm.remainingMs(pkg)   // always the real persisted remaining time, never a restarted countdown
        if (rem > 0) {
            timeView.text = "Time remaining\n" + formatHms(rem)
            untilView.text = "Available again around " + SimpleDateFormat("HH:mm").format(Date(System.currentTimeMillis() + rem))
        } else {
            timeView.text = "Restriction ended"
            untilView.text = "You can open $name again."
            homeBtn.text = "Close"
        }
    }
}
