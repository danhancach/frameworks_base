package com.android.systemui.axdynamicbar.model

data class IslandUiState(
    val events: List<IslandEvent> = emptyList(),
    val islandState: IslandState = IslandState.HIDDEN,
    val pinnedEventIndex: Int = 0,
    val manuallyHidden: Boolean = false,
    val forceVisible: Boolean = false,
    /** Hang doi alert overlay; top = [pinnedAlertIndex]. */
    val notificationAlerts: List<IslandEvent.Notification> = emptyList(),
    val pinnedAlertIndex: Int = 0,
    /**
     * Event id bi an pill vi owner app foreground (Media/Timer/Stopwatch).
     * Van giu trong [events] — suppression != dismiss / owner-end.
     */
    val foregroundSuppressedIds: Set<String> = emptySet(),
    /** Retained runs waiting for a notification repost; keep their status-bar composition. */
    val pendingConfirmationIds: Set<String> = emptySet(),
    /**
     * Package FG that (AppTracking) — null = Home/SystemUI/khong app.
     * Diag dung token hash; khong log raw o UI chip neu khong can.
     */
    val foregroundPackage: String? = null,
) {

    /** Top alert dang xem (tuong thich call-site cu). */
    val notificationAlert: IslandEvent.Notification?
        get() = notificationAlerts.getOrNull(pinnedAlertIndex) ?: notificationAlerts.firstOrNull()

    /** Visual suppression is independent of retained event identity and user pin. */
    val visuallySuppressedIds: Set<String>
        get() = foregroundSuppressedIds + pendingConfirmationIds

    /** Events eligible for presentation after owner and confirmation suppression. */
    val visibleEvents: List<IslandEvent>
        get() = events.filter { it.id !in visuallySuppressedIds }

    val shouldShow: Boolean
        get() =
            notificationAlert != null ||
                (islandState == IslandState.CHIP && visibleEvents.isNotEmpty())

    /**
     * Co underlying event (ke ca FG-hide) — composition co the giu de APP_HIDE/SHOW.
     * Khac [shouldShow] (pill visible).
     */
    val hasUnderlyingChipEvents: Boolean
        get() =
            notificationAlert != null ||
                (islandState == IslandState.CHIP && events.isNotEmpty())

    val isChip: Boolean
        get() = islandState == IslandState.CHIP

    /** Top hien thi: pinned neu khong FG-suppress; else first visible. */
    val topEvent: IslandEvent?
        get() {
            val pinned = events.getOrNull(pinnedEventIndex)
            if (pinned != null && pinned.id !in visuallySuppressedIds) return pinned
            return visibleEvents.firstOrNull()
                ?: events.getOrNull(pinnedEventIndex)
                ?: events.firstOrNull()
        }

    /**
     * Index UI dang chon (top visible) — DOC LAP [pinnedEventIndex] persistent.
     * Khi pinned FG-suppress: co the khac pin user; Home restore pin khong can ghi de.
     */
    val visibleSelectedIndex: Int
        get() {
            val top = topEvent ?: return pinnedEventIndex.coerceAtLeast(0)
            val idx = events.indexOfFirst { it.id == top.id }
            return if (idx >= 0) idx else pinnedEventIndex.coerceAtLeast(0)
        }

    val activeEvents: List<IslandEvent>
        get() = events.filter { it !is IslandEvent.Notification }
}

/**
 * Thu tu stack alert: pinned truoc, roi xoay vong theo hang doi.
 */
fun alertStackOrder(
    alerts: List<IslandEvent.Notification>,
    pinnedAlertKey: String?,
): List<IslandEvent.Notification> {
    if (alerts.isEmpty()) return emptyList()
    val startIdx =
        pinnedAlertKey
            ?.let { key -> alerts.indexOfFirst { it.sbn.key == key } }
            ?.takeIf { it >= 0 }
            ?: 0
    return List(alerts.size) { i -> alerts[(startIdx + i) % alerts.size] }
}
