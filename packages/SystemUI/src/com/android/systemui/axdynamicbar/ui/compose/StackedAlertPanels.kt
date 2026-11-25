package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.alertStackOrder
import com.android.systemui.axdynamicbar.shared.IslandActions

/**
 * Stack cho notification alert dong bo physics + track glass morph chuan Samsung
 * [SamsungCardStackContainer] (85dp commit, 126dp ngang, spring XY/scale, 600ms topAlpha).
 */
@Composable
internal fun StackedAlertPanels(
    alerts: List<IslandEvent.Notification>,
    pinnedAlertKey: String?,
    interactor: IslandActions,
    onDismissTop: () -> Unit,
    onSwipeDismissTop: () -> Unit = onDismissTop,
    onAlertCardBoundsPx: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
    stackGesturesEnabled: Boolean = true,
    initiallyCompact: Boolean = false,
) {
    var localPinnedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pinnedAlertKey) {
        if (localPinnedKey != null && pinnedAlertKey == localPinnedKey) {
            localPinnedKey = null
        }
    }
    val effectivePinnedKey = localPinnedKey ?: pinnedAlertKey
    val liveStack =
        remember(alerts, effectivePinnedKey) { alertStackOrder(alerts, effectivePinnedKey) }
    if (liveStack.isEmpty()) return

    var promotedExpandedKeys by remember { mutableStateOf(emptySet<String>()) }
    val stackKeys = remember(liveStack) { liveStack.map { it.sbn.key } }
    LaunchedEffect(stackKeys) {
        if (stackKeys.size >= 2) {
            promotedExpandedKeys = promotedExpandedKeys + stackKeys.drop(1)
        } else if (stackKeys.isEmpty()) {
            promotedExpandedKeys = emptySet()
        }
    }

    SamsungCardStackContainer(
        liveStack = liveStack,
        keyOf = { it.sbn.key },
        ownerIdOf = { it.id },
        stackGesturesEnabled = stackGesturesEnabled,
        traceTag = "AxDbAlertStack",
        onCommitPin = { nextKey ->
            localPinnedKey = nextKey
            interactor.pinNotificationAlertByKey(nextKey)
        },
        onTopCardBoundsPx = onAlertCardBoundsPx,
    ) { alert, bindings ->
        val startCompact =
            remember(alert.sbn.key) {
                initiallyCompact &&
                    liveStack.size == 1 &&
                    alert.sbn.key !in promotedExpandedKeys
            }
        NotificationAlertCard(
            notification = alert,
            interactor = interactor,
            onDismiss = onDismissTop,
            onSwipeDismiss = onSwipeDismissTop,
            initiallyCompact = startCompact,
            forceExpanded = liveStack.size > 1 || alert.sbn.key in promotedExpandedKeys,
            interactive = bindings.isInteractiveTop,
            reportExpandedChanged = bindings.isInteractiveTop,
            enableSwipeGestures = bindings.isInteractiveTop && stackGesturesEnabled,
            horizontalDismissThreshold = HORIZONTAL_DISMISS_THRESHOLD,
            cancelOnConsumedRelease = true,
            tiltEnabled = false,
            stackDragKey = bindings.stackDragKey,
            onStackDragStart = bindings.onStackDragStart,
            onStackDragXY = bindings.onStackDragXY,
            onStackDragEnd = bindings.onStackDragEnd,
            maxStackDragPx = bindings.maxStackDragPx,
            contentOffsetX = bindings.contentOffsetX,
            contentOffsetY = bindings.contentOffsetY,
        )
    }
}
