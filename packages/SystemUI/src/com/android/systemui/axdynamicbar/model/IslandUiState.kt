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
) {

    /** Top alert dang xem (tuong thich call-site cu). */
    val notificationAlert: IslandEvent.Notification?
        get() = notificationAlerts.getOrNull(pinnedAlertIndex) ?: notificationAlerts.firstOrNull()

    val shouldShow: Boolean
        get() = islandState == IslandState.CHIP || notificationAlert != null

    val isChip: Boolean
        get() = islandState == IslandState.CHIP

    val topEvent: IslandEvent?
        get() = events.getOrNull(pinnedEventIndex) ?: events.firstOrNull()

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
