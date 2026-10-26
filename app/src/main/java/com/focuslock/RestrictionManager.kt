package com.focuslock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

class RestrictionManager(private val ctx: Context, private val store: Store) {

    private fun boot(): Int = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** Remaining restriction time; 0 if none. Removes expired records. Re-bases after reboot. */
    fun remainingMs(pkg: String): Long {
        val r = store.getRestriction(pkg) ?: return 0
        val ev = r.evaluate(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot())
        if (ev.remainingMs <= 0) { store.removeRestriction(pkg); return 0 }
        ev.rebased?.let { store.putRestriction(pkg, it) }
        return ev.remainingMs
    }

    fun isRestricted(pkg: String) = remainingMs(pkg) > 0

    /** Starts a restriction ONLY if none is active. An active one is never reset or extended. */
    fun startIfNone(pkg: String, durationMs: Long) {
        if (isRestricted(pkg)) return
        store.putRestriction(pkg, Restriction.create(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(), durationMs))
    }

    fun pruneExpired() { store.restrictedPackages().forEach { remainingMs(it) } }
}
