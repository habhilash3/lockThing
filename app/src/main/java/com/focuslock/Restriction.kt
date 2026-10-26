package com.focuslock

/**
 * Pure (Android-free) restriction record + timing maths, so it can be unit-tested.
 *
 * Within one boot, remaining time is measured with elapsedRealtime (monotonic, counts deep sleep,
 * immune to the user changing the clock). After a reboot the monotonic base is gone, so we fall back
 * to the saved wall-clock expiry (capped at the original duration) and re-base on the new boot.
 */
data class Restriction(
    val startWall: Long,
    val endWall: Long,
    val endElapsed: Long,
    val boot: Int,
    val durationMs: Long
) {
    data class Evaluation(val remainingMs: Long, val rebased: Restriction?)

    fun serialize() = "$startWall|$endWall|$endElapsed|$boot|$durationMs"

    fun evaluate(nowWall: Long, nowElapsed: Long, currentBoot: Int): Evaluation {
        if (currentBoot != -1 && currentBoot == boot) {
            return Evaluation(minOf(endElapsed - nowElapsed, durationMs), null)
        }
        val rem = minOf(endWall - nowWall, durationMs)
        if (rem <= 0) return Evaluation(rem, null)
        val rebased = if (currentBoot != -1)
            copy(endWall = nowWall + rem, endElapsed = nowElapsed + rem, boot = currentBoot) else null
        return Evaluation(rem, rebased)
    }

    companion object {
        fun create(nowWall: Long, nowElapsed: Long, boot: Int, durationMs: Long) =
            Restriction(nowWall, nowWall + durationMs, nowElapsed + durationMs, boot, durationMs)

        fun parse(s: String): Restriction? {
            val p = s.split('|')
            if (p.size != 5) return null
            return try {
                Restriction(p[0].toLong(), p[1].toLong(), p[2].toLong(), p[3].toInt(), p[4].toLong())
            } catch (e: NumberFormatException) { null }
        }
    }
}

fun formatHms(ms: Long): String {
    val s = (maxOf(ms, 0L) + 999) / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
