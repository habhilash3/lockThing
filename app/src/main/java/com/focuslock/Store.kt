package com.focuslock

import android.content.Context

/** All persistence (SharedPreferences, on-device only). Restrictions use commit() so they survive an immediate process kill. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("focuslock", Context.MODE_PRIVATE)

    var limitMin: Int
        get() = sp.getInt("limit_min", 5)
        set(v) { sp.edit().putInt("limit_min", v).apply() }
    var blockMin: Int
        get() = sp.getInt("block_min", 120)
        set(v) { sp.edit().putInt("block_min", v).apply() }
    var warnOnDisable: Boolean
        get() = sp.getBoolean("warn_disable", true)
        set(v) { sp.edit().putBoolean("warn_disable", v).apply() }
    var setupShown: Boolean
        get() = sp.getBoolean("setup_shown", false)
        set(v) { sp.edit().putBoolean("setup_shown", v).apply() }

    fun selected(): Set<String> = HashSet(sp.getStringSet("selected", emptySet()) ?: emptySet())
    fun isSelected(pkg: String) = selected().contains(pkg)
    fun setSelected(pkg: String, on: Boolean) {
        val s = selected().toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp.edit().putStringSet("selected", s).commit()
    }

    fun getRestriction(pkg: String): Restriction? = sp.getString("r:$pkg", null)?.let { Restriction.parse(it) }
    fun putRestriction(pkg: String, r: Restriction) { sp.edit().putString("r:$pkg", r.serialize()).commit() }
    fun removeRestriction(pkg: String) { sp.edit().remove("r:$pkg").commit() }
    fun restrictedPackages(): List<String> = sp.all.keys.filter { it.startsWith("r:") }.map { it.substring(2) }

    // Usage session, so a restarted service can resume it if usage events prove it was continuous
    fun saveSession(pkg: String, startWall: Long) { sp.edit().putString("s_pkg", pkg).putLong("s_start", startWall).apply() }
    fun clearSession() { sp.edit().remove("s_pkg").remove("s_start").apply() }
    fun loadSession(): Pair<String, Long>? {
        val p = sp.getString("s_pkg", null) ?: return null
        return p to sp.getLong("s_start", 0L)
    }
}
