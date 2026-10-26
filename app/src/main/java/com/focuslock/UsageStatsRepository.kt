package com.focuslock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Supplementary source (events can arrive with delay, so NOT the primary detector).
 * Used for seeding after service (re)start / unlock, and validating the limit trigger.
 */
class UsageStatsRepository(private val ctx: Context) {
    data class Fg(val pkg: String, val timeMs: Long)

    private val usm get() = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun foreground(lookbackMs: Long = 3 * 3600_000L): Fg? = try {
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - lookbackMs, now)
        val e = UsageEvents.Event()
        var cur: String? = null; var curCls: String? = null; var t = 0L
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> { cur = e.packageName; curCls = e.className; t = e.timeStamp }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (e.packageName == cur && e.className == curCls) { cur = null; curCls = null }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { cur = null; curCls = null }
            }
        }
        cur?.let { Fg(it, t) }
    } catch (e: Exception) { null }   // permission missing / OEM quirk: callers treat null as "unknown"

    /** True if, since [sinceWall], no other non-ignored app resumed and the screen never went off. */
    fun continuousUse(pkg: String, sinceWall: Long, ignore: (String) -> Boolean): Boolean = try {
        val ev = usm.queryEvents(sinceWall, System.currentTimeMillis())
        val e = UsageEvents.Event()
        var ok = true
        while (ev != null && ev.hasNextEvent() && ok) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) ok = false
            else if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.packageName != pkg && !ignore(e.packageName)) ok = false
        }
        ok
    } catch (e: Exception) { false }
}
