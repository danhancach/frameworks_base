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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandEventSelectionPolicyTest {

    private companion object {
        private const val GOOGLE_CLOCK_PKG = "com.google.android.deskclock"
        private const val AOSP_CLOCK_PKG = "com.android.deskclock"
        private const val LAUNCHER_PKG = "com.android.launcher3"
    }

    private fun torch(level: Int): IslandEvent.Torch =
        IslandEvent.Torch(level = level, maxLevel = 5)

    private fun media(key: String = "1", track: String = "A", artist: String = "X"): IslandEvent.Media =
        IslandEvent.Media(mediaKey = key, track = track, artist = artist, isPlaying = true)

    private fun timer(key: String, isPaused: Boolean): IslandEvent.Timer =
        IslandEvent.Timer(label = key, notificationKey = key, isPaused = isPaused)

    private fun stopwatch(): IslandEvent.Stopwatch =
        IslandEvent.Stopwatch(label = "Stopwatch", startTimeMs = 1_000L, isRunning = true)

    @Test
    fun precedence_newEventBeatsMediaChangeAndTorchRefresh() {
        val prev = listOf(torch(1), media(track = "A", artist = "X"))
        val next = listOf(torch(2), media(track = "B", artist = "X"), IslandEvent.Hotspot(1))
        val res =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 1,
                events = next,
            )
        assertEquals(2, res.pinnedIndex)
        assertTrue(res.shouldResetManuallyHidden)
    }

    @Test
    fun precedence_mediaChangeBeatsTorchRefresh_andTorchResetsToZero() {
        val prev = listOf(torch(1), media(track = "A", artist = "X"))
        val mediaChanged = listOf(torch(2), media(track = "A", artist = "Y"))
        val resMedia =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 0,
                events = mediaChanged,
            )
        assertEquals(1, resMedia.pinnedIndex)
        assertTrue(resMedia.shouldResetManuallyHidden)

        val torchOnly = listOf(torch(2), media(track = "A", artist = "X"))
        val resTorch =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 1,
                events = torchOnly,
            )
        assertEquals(0, resTorch.pinnedIndex)
        assertTrue(resTorch.shouldResetManuallyHidden)
    }

    @Test
    fun duplicatePreviousId_preservesFirstMatchSemantics() {
        // Neu list cu co 2 phan tu cung id, lookup phai so sanh voi phan tu dau (firstOrNull).
        val hotspot = IslandEvent.Hotspot(1)
        val prev =
            listOf(
                hotspot,
                media(key = "dup", track = "First", artist = "A"),
                media(key = "dup", track = "Second", artist = "A"),
            )
        val sameAsFirst = listOf(hotspot, media(key = "dup", track = "First", artist = "A"))
        val resSame =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 0,
                events = sameAsFirst,
            )
        assertEquals(0, resSame.pinnedIndex)
        assertFalse(resSame.shouldResetManuallyHidden)

        val sameAsSecond = listOf(hotspot, media(key = "dup", track = "Second", artist = "A"))
        val resDiff =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 0,
                events = sameAsSecond,
            )
        assertEquals(1, resDiff.pinnedIndex)
        assertTrue(resDiff.shouldResetManuallyHidden)
    }

    @Test
    fun fallback_preservesPinnedIdentityAcrossReorderAndClampsOnRemoval() {
        val hotspot = IslandEvent.Hotspot(1)
        val vpn = IslandEvent.Vpn()
        val charging = IslandEvent.Charging(level = 80, isWireless = false)
        val prev = listOf(hotspot, vpn, charging)

        // Giu pin id "vpn" du vi tri thay doi (ke ca khi dang bi FG suppress ben ngoai).
        val reordered = listOf(charging, hotspot, vpn)
        val resReorder =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 1,
                events = reordered,
            )
        assertEquals(2, resReorder.pinnedIndex)
        assertFalse(resReorder.shouldResetManuallyHidden)

        // Pin id bi xoa -> clamp previousPinnedIndex (2 -> lastIndex=0).
        val shrunk = listOf(hotspot)
        val resShrunk =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 2,
                events = shrunk,
            )
        assertEquals(0, resShrunk.pinnedIndex)
        assertFalse(resShrunk.shouldResetManuallyHidden)

        // Danh sach rong -> clamp ve 0.
        val emptyRes =
            resolveIslandEventSelection(
                previousEvents = prev,
                previousPinnedIndex = 2,
                events = emptyList(),
            )
        assertEquals(0, emptyRes.pinnedIndex)
        assertFalse(emptyRes.shouldResetManuallyHidden)
    }

    @Test
    fun deskClockExitTab_andGraceWindowSemanticsPreserved() {
        val mediaEv = media(key = "1", track = "Song", artist = "Art")
        val pausedTimer = timer(key = "t1", isPaused = true)
        val runningTimer = timer(key = "t2", isPaused = false)
        val sw = stopwatch()

        // 1) Roi Clock voi tab TIMERS: chon running timer, arm grace = now + graceMs.
        val leaveRes =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv, pausedTimer, runningTimer, sw),
                previousPinnedIndex = 0,
                events = listOf(mediaEv, pausedTimer, runningTimer, sw),
                previousForegroundPackage = GOOGLE_CLOCK_PKG,
                foregroundPackage = LAUNCHER_PKG,
                onKeyguard = false,
                nowElapsedMs = 10_000L,
                lastDeskClockTab = DeskClockSelectedTab.TIMERS,
            )
        assertEquals(2, leaveRes.pinnedIndex)
        assertEquals(DeskClockSelectedTab.TIMERS, leaveRes.nextDeskClockExitGrace.tab)
        assertEquals(
            10_000L + DESK_CLOCK_LEAVE_REPOST_GRACE_MS,
            leaveRes.nextDeskClockExitGrace.deadlineMs,
        )

        // 2) Repost Clock tai dung moc deadline (now == deadline van hop le) + Media doi track
        // cung luc: hasNewEvents=true -> hasSignificantChange=false, van giu grace chon TIMERS.
        val deadline = leaveRes.nextDeskClockExitGrace.deadlineMs
        val repostAtDeadline =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv),
                previousPinnedIndex = 0,
                events = listOf(media(key = "1", track = "Song2", artist = "Art"), sw, pausedTimer),
                previousForegroundPackage = LAUNCHER_PKG,
                foregroundPackage = LAUNCHER_PKG,
                nowElapsedMs = deadline,
                pendingDeskClockExit = leaveRes.nextDeskClockExitGrace,
            )
        assertEquals(2, repostAtDeadline.pinnedIndex)
        assertTrue(repostAtDeadline.shouldResetManuallyHidden)
        assertEquals(DeskClockSelectedTab.TIMERS, repostAtDeadline.nextDeskClockExitGrace.tab)
        assertEquals(deadline, repostAtDeadline.nextDeskClockExitGrace.deadlineMs)

        // 3) Qua deadline (now > deadline): het han grace, dung basePinnedIndex (first new event).
        val afterDeadline =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv),
                previousPinnedIndex = 0,
                events = listOf(mediaEv, sw, pausedTimer),
                previousForegroundPackage = LAUNCHER_PKG,
                foregroundPackage = LAUNCHER_PKG,
                nowElapsedMs = deadline + 1L,
                pendingDeskClockExit = leaveRes.nextDeskClockExitGrace,
            )
        assertEquals(1, afterDeadline.pinnedIndex)
        assertNull(afterDeadline.nextDeskClockExitGrace.tab)
        assertEquals(0L, afterDeadline.nextDeskClockExitGrace.deadlineMs)

        // 4) Co event non-Clock moi trong grace window: xoa grace va pin event moi.
        val nonClockNew =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv, pausedTimer),
                previousPinnedIndex = 0,
                events = listOf(mediaEv, pausedTimer, IslandEvent.Hotspot(2)),
                previousForegroundPackage = LAUNCHER_PKG,
                foregroundPackage = LAUNCHER_PKG,
                nowElapsedMs = 11_000L,
                pendingDeskClockExit = leaveRes.nextDeskClockExitGrace,
            )
        assertEquals(2, nonClockNew.pinnedIndex)
        assertNull(nonClockNew.nextDeskClockExitGrace.tab)
        assertEquals(0L, nonClockNew.nextDeskClockExitGrace.deadlineMs)

        // 5) Media doi track khong co event moi trong grace window: xoa grace va pin Media.
        val mediaChangeClearsGrace =
            resolveIslandEventSelection(
                previousEvents = listOf(pausedTimer, mediaEv),
                previousPinnedIndex = 0,
                events = listOf(pausedTimer, media(key = "1", track = "Next", artist = "Art")),
                previousForegroundPackage = LAUNCHER_PKG,
                foregroundPackage = LAUNCHER_PKG,
                nowElapsedMs = 11_000L,
                pendingDeskClockExit = leaveRes.nextDeskClockExitGrace,
            )
        assertEquals(1, mediaChangeClearsGrace.pinnedIndex)
        assertNull(mediaChangeClearsGrace.nextDeskClockExitGrace.tab)
        assertEquals(0L, mediaChangeClearsGrace.nextDeskClockExitGrace.deadlineMs)

        // 6) Roi Clock len keyguard: khong arm grace tab.
        val leaveToKeyguard =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv, runningTimer),
                previousPinnedIndex = 0,
                events = listOf(mediaEv, runningTimer),
                previousForegroundPackage = GOOGLE_CLOCK_PKG,
                foregroundPackage = LAUNCHER_PKG,
                onKeyguard = true,
                nowElapsedMs = 10_000L,
                lastDeskClockTab = DeskClockSelectedTab.TIMERS,
            )
        assertEquals(0, leaveToKeyguard.pinnedIndex)
        assertNull(leaveToKeyguard.nextDeskClockExitGrace.tab)
        assertEquals(0L, leaveToKeyguard.nextDeskClockExitGrace.deadlineMs)

        // 7) Quay lai AOSP Clock: xoa grace dang cho.
        val reenterClock =
            resolveIslandEventSelection(
                previousEvents = listOf(mediaEv, runningTimer),
                previousPinnedIndex = 1,
                events = listOf(mediaEv, runningTimer),
                previousForegroundPackage = LAUNCHER_PKG,
                foregroundPackage = AOSP_CLOCK_PKG,
                nowElapsedMs = 11_000L,
                pendingDeskClockExit = leaveRes.nextDeskClockExitGrace,
            )
        assertEquals(1, reenterClock.pinnedIndex)
        assertNull(reenterClock.nextDeskClockExitGrace.tab)
        assertEquals(0L, reenterClock.nextDeskClockExitGrace.deadlineMs)
    }
}
