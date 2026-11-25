/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.axdynamicbar.domain

import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.DESK_CLOCK_LEAVE_REPOST_GRACE_MS
import com.android.systemui.axdynamicbar.shared.DeskClockSelectedTab
import com.android.systemui.axdynamicbar.shared.isDeskClockPackage
import com.android.systemui.axdynamicbar.shared.resolveDeskClockExitPinnedIndex

/**
 * Trang thai grace-window cho Clock repost sau khi nguoi dung roi ung dung Clock.
 */
internal data class DeskClockExitGrace(
    val tab: DeskClockSelectedTab? = null,
    val deadlineMs: Long = 0L,
)

/**
 * Ket qua chon event pin va trang thai grace-window thoat Clock cho AxDynamicBarInteractor.
 */
internal data class IslandEventSelectionResult(
    val pinnedIndex: Int,
    val shouldResetManuallyHidden: Boolean,
    val nextDeskClockExitGrace: DeskClockExitGrace,
)

/**
 * Policy thuan xac dinh pin index, reset an thu cong va grace-window thoat Clock.
 *
 * Dung lai bang tra cuu [previousById] (giu ngu nghia first-match) cho novelty, Torch refresh,
 * Media doi track/artist va loc event non-Clock. Giu nguyen thu tu uu tien:
 * hasNewEvents -> hasSignificantChange -> userInitiatedRefresh -> prevPinnedId -> clamp index.
 */
internal fun resolveIslandEventSelection(
    previousEvents: List<IslandEvent>,
    previousPinnedIndex: Int,
    events: List<IslandEvent>,
    previousForegroundPackage: String? = null,
    foregroundPackage: String? = null,
    onKeyguard: Boolean = false,
    nowElapsedMs: Long = 0L,
    lastDeskClockTab: DeskClockSelectedTab? = null,
    pendingDeskClockExit: DeskClockExitGrace = DeskClockExitGrace(),
): IslandEventSelectionResult {
    // Giu phan tu dau tien cho moi id (tuong duong firstOrNull khi co id trung).
    val previousById = HashMap<String, IslandEvent>(previousEvents.size)
    for (event in previousEvents) {
        previousById.putIfAbsent(event.id, event)
    }

    val firstNewEventIndex = events.indexOfFirst { it.id !in previousById }
    val hasNewEvents = firstNewEventIndex >= 0

    val userInitiatedRefresh =
        events.any { event ->
            event is IslandEvent.Torch &&
                previousById[event.id]?.let { it != event } == true
        }

    val firstSignificantChangeIndex =
        if (hasNewEvents) {
            -1
        } else {
            events.indexOfFirst { event ->
                val old = previousById[event.id] ?: return@indexOfFirst false
                event is IslandEvent.Media &&
                    old is IslandEvent.Media &&
                    (event.track != old.track || event.artist != old.artist)
            }
        }
    val hasSignificantChange = firstSignificantChangeIndex >= 0

    val prevPinnedId = previousEvents.getOrNull(previousPinnedIndex)?.id
    val basePinnedIndex =
        when {
            hasNewEvents -> firstNewEventIndex
            hasSignificantChange -> firstSignificantChangeIndex
            userInitiatedRefresh -> 0
            else ->
                resolvePinnedIndexByIdOrFallback(
                    pinnedId = prevPinnedId,
                    events = events,
                    previousPinnedIndex = previousPinnedIndex,
                )
        }

    val wasInDeskClock = isDeskClockPackage(previousForegroundPackage)
    val nowInDeskClock = isDeskClockPackage(foregroundPackage)
    val leavingDeskClock = wasInDeskClock && !nowInDeskClock && !onKeyguard
    var nextExitTab = pendingDeskClockExit.tab
    var nextExitDeadlineMs = pendingDeskClockExit.deadlineMs
    if (nowInDeskClock) {
        nextExitTab = null
        nextExitDeadlineMs = 0L
    } else if (leavingDeskClock) {
        val exitTab =
            lastDeskClockTab?.takeIf {
                it == DeskClockSelectedTab.TIMERS || it == DeskClockSelectedTab.STOPWATCH
            }
        nextExitTab = exitTab
        nextExitDeadlineMs =
            if (exitTab != null) nowElapsedMs + DESK_CLOCK_LEAVE_REPOST_GRACE_MS else 0L
    } else if (nextExitTab != null && nowElapsedMs > nextExitDeadlineMs) {
        nextExitTab = null
        nextExitDeadlineMs = 0L
    }

    val hasNewNonClockEvent =
        hasNewEvents &&
            events.any { event ->
                event.id !in previousById &&
                    event !is IslandEvent.Timer &&
                    event !is IslandEvent.Stopwatch
            }

    val pinnedIndex =
        when {
            hasNewNonClockEvent || userInitiatedRefresh || hasSignificantChange -> {
                nextExitTab = null
                nextExitDeadlineMs = 0L
                basePinnedIndex
            }
            leavingDeskClock || (nextExitTab != null && hasNewEvents) ->
                // Thoat Clock: chon pill theo tab cuoi; thieu/tre thi giu basePinnedIndex.
                resolveDeskClockExitPinnedIndex(
                    events = events,
                    selectedTab = nextExitTab,
                    fallbackIndex = basePinnedIndex,
                )
            else -> basePinnedIndex
        }

    val shouldReset = hasNewEvents || userInitiatedRefresh || hasSignificantChange
    return IslandEventSelectionResult(
        pinnedIndex = pinnedIndex,
        shouldResetManuallyHidden = shouldReset,
        nextDeskClockExitGrace =
            DeskClockExitGrace(
                tab = nextExitTab,
                deadlineMs = nextExitDeadlineMs,
            ),
    )
}

/**
 * Tim lai vi tri cua [pinnedId] trong [events] (first-match); neu khong con thi clamp [previousPinnedIndex].
 */
internal fun resolvePinnedIndexByIdOrFallback(
    pinnedId: String?,
    events: List<IslandEvent>,
    previousPinnedIndex: Int,
): Int {
    if (pinnedId != null) {
        val idx = events.indexOfFirst { it.id == pinnedId }
        if (idx >= 0) return idx
    }
    return previousPinnedIndex.coerceAtMost((events.size - 1).coerceAtLeast(0))
}
