package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Event-driven: reacts only to TYPE_WINDOW_STATE_CHANGED. No polling, no wake locks.
 * One Handler timer exists only while a selected app is in the foreground.
 *
 * Session rules
 *  - Starts when a selected, unrestricted app becomes foreground.
 *  - Ends (timer resets) when any other non-ignored app / home / FocusLock UI comes up, or the screen turns off.
 *  - System UI (notification shade), IME windows and the "android" package are ignored (they don't count as leaving).
 *  - After unlock or service (re)start the foreground app is re-seeded from UsageStatsManager.
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: FocusAccessibilityService? = null
        private val IGNORED = setOf("com.android.systemui", "android")
        private const val CH = "focuslock_warn"
        private const val NOTIF_ID = 1
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: Store
    private lateinit var restrictions: RestrictionManager
    private lateinit var usage: UsageStatsRepository
    private lateinit var power: PowerManager

    private var ime: Set<String> = emptySet()
    private var lastPkg: String? = null
    private var sessionPkg: String? = null
    private var sessionStartWall = 0L
    private var sessionStartElapsed = 0L
    private var receiverRegistered = false
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L

    private val limitRunnable = Runnable { onLimitReached() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { endSession(true); lastPkg = null }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    refreshIme()
                    handler.postDelayed({ reseed() }, 600)   // keyguard dismissal emits no window event for the app underneath
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = Store(this)
        restrictions = RestrictionManager(this, store)
        usage = UsageStatsRepository(this)
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        instance = this
        refreshIme()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        })
        receiverRegistered = true
        restrictions.pruneExpired()
        handler.postDelayed({ reseed() }, 300)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) { lastPkg = pkg; endSession(true); return }
        if (ignored(pkg)) return
        handleForeground(pkg, false)
    }

    override fun onInterrupt() { handler.removeCallbacks(limitRunnable) }

    override fun onUnbind(intent: Intent?): Boolean { teardown(true); return super.onUnbind(intent) }
    override fun onDestroy() { teardown(false); super.onDestroy() }

    private fun teardown(warn: Boolean) {
        handler.removeCallbacksAndMessages(null)   // persisted session intentionally kept so it can resume
        if (receiverRegistered) { try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}; receiverRegistered = false }
        val wasUp = instance === this
        instance = null
        if (warn && wasUp) warnDisabled()
    }

    private fun ignored(pkg: String) = pkg in IGNORED || pkg in ime

    private fun refreshIme() {
        ime = try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) { emptySet() }
    }

    private fun limitMs() = store.limitMin * 60_000L
    private fun blockMs() = store.blockMin * 60_000L

    private fun reseed() {
        if (!power.isInteractive) return
        val fg = usage.foreground() ?: return
        if (fg.pkg == packageName || ignored(fg.pkg)) return
        handleForeground(fg.pkg, true)
    }

    private fun handleForeground(pkg: String, resume: Boolean) {
        lastPkg = pkg
        // Restriction check first; it does NOT depend on the app still being "selected".
        if (restrictions.isRestricted(pkg)) { endSession(true); block(pkg); return }
        if (!store.isSelected(pkg)) { endSession(true); return }
        if (sessionPkg == pkg) return          // same session (dialogs / activities inside the app)
        endSession(true)
        startSession(pkg, resume)
    }

    private fun startSession(pkg: String, resume: Boolean) {
        val nowWall = System.currentTimeMillis()
        var startWall = nowWall
        if (resume) {
            val saved = store.loadSession()
            if (saved != null && saved.first == pkg && saved.second in 1..nowWall &&
                usage.continuousUse(pkg, saved.second, ::ignored)) startWall = saved.second
        }
        val used = nowWall - startWall
        sessionPkg = pkg
        sessionStartWall = startWall
        sessionStartElapsed = SystemClock.elapsedRealtime() - used
        store.saveSession(pkg, startWall)
        handler.postDelayed(limitRunnable, maxOf(0L, limitMs() - used))
    }

    private fun endSession(clearPersisted: Boolean) {
        handler.removeCallbacks(limitRunnable)
        sessionPkg = null
        if (clearPersisted) store.clearSession()
    }

    private fun onLimitReached() {
        val pkg = sessionPkg ?: return
        if (!power.isInteractive) { endSession(true); return }
        val used = SystemClock.elapsedRealtime() - sessionStartElapsed
        if (used < limitMs() - 1500) { handler.postDelayed(limitRunnable, limitMs() - used); return }  // uptime drift
        // Cross-check: if usage events show a different app resumed after the session began, a switch was missed.
        val fg = usage.foreground()
        if (fg != null && fg.pkg != pkg && fg.pkg != packageName && !ignored(fg.pkg) && fg.timeMs > sessionStartWall) {
            endSession(true); handleForeground(fg.pkg, false); return
        }
        restrictions.startIfNone(pkg, blockMs())   // persisted immediately; never resets an active one
        endSession(true)
        block(pkg)
    }

    private fun block(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockPkg && now - lastBlockAt < 400) return
        lastBlockPkg = pkg; lastBlockAt = now
        BlockingActivity.launch(this, pkg)
        // Fallback: if the block screen never became foreground (e.g. MIUI denied the background start), push the app away.
        handler.postDelayed({ if (lastPkg == pkg) performGlobalAction(GLOBAL_ACTION_HOME) }, 1200)
    }

    private fun warnDisabled() {
        try {
            if (!store.warnOnDisable) return
            if (store.selected().isEmpty() && store.restrictedPackages().isEmpty()) return
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Protection warnings", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(this, 0, Intent(this, SetupActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            nm.notify(NOTIF_ID, Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("FocusLock protection is OFF")
                .setContentText("Accessibility service stopped. App blocking is not enforced. Tap to fix.")
                .setContentIntent(pi).setAutoCancel(true).build())
        } catch (_: Exception) {}
    }
}
