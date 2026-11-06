package com.android.systemui.axdynamicbar.shared

/** Tracks continuous inactivity independently for each media session. */
internal class StatusBarMediaTimeout {
    private val inactiveSince = mutableMapOf<String, Long>()

    fun update(playback: Map<String, Boolean>, now: Long) {
        inactiveSince.keys.retainAll(playback.keys)
        for ((id, playing) in playback) {
            if (playing) inactiveSince.remove(id)
            else inactiveSince.putIfAbsent(id, now)
        }
    }

    fun expiredIds(now: Long): Set<String> =
        inactiveSince.filterValues { now - it >= TIMEOUT_MS }.keys.toSet()

    fun nextDelayMs(now: Long): Long? =
        inactiveSince.values
            .map { TIMEOUT_MS - (now - it) }
            .filter { it > 0L }
            .minOrNull()

    companion object {
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }
}
