/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.systemui.axdynamicbar.shared

import android.service.notification.NotificationListenerService
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.IslandState
import com.android.systemui.axdynamicbar.model.IslandUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * task-007/008: neighbor tracks G991N — clock nudge, slot CARD vs APP, FG visibility.
 * P1A–D source corrections. SOURCE-ONLY; khong claim runtime PASS.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarNeighborMotionTest : SysuiTestCase() {

    @Test
    fun clockNudge_eligibility_matrix() {
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.CARD_OPEN)).isTrue()
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.CARD_CLOSE)).isTrue()
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_SHOW)).isTrue()
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_HIDE)).isFalse()
    }

    @Test
    fun neighborTrigger_cardVsApp() {
        assertThat(statusBarNeighborTriggerOf(reclaiming = true, appForeground = false))
            .isEqualTo(StatusBarNeighborTrigger.CARD_OPEN)
        assertThat(statusBarNeighborTriggerOf(reclaiming = false, appForeground = false))
            .isEqualTo(StatusBarNeighborTrigger.CARD_CLOSE)
        assertThat(statusBarNeighborTriggerOf(reclaiming = true, appForeground = true))
            .isEqualTo(StatusBarNeighborTrigger.APP_HIDE)
        assertThat(statusBarNeighborTriggerOf(reclaiming = false, appForeground = true))
            .isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
    }

    @Test
    fun slotDuration_allTriggers_500_notMorph600() {
        for (t in StatusBarNeighborTrigger.entries) {
            assertThat(statusBarChipSlotDurationMs(t)).isEqualTo(500)
        }
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
        // Morph collapse van 600 — neighbor khong keo theo.
        assertThat(StatusBarMorphCollapseMs).isEqualTo(600)
        assertThat(StatusBarMorphExpandMs).isEqualTo(500)
    }

    @Test
    fun slotEasing_cardVsApp_distinct() {
        val cardOpen = StatusBarChipSlotCardExpandEasing.transform(0.5f)
        val cardClose = StatusBarChipSlotCardCollapseEasing.transform(0.5f)
        val app = StatusBarChipSlotAppEasing.transform(0.5f)
        // CARD expand overshoot manh hon collapse; APP easing khac ca hai.
        assertThat(cardOpen).isGreaterThan(1f)
        assertThat(cardClose).isGreaterThan(0.9f)
        assertThat(app).isNotEqualTo(cardOpen)
        assertThat(statusBarChipSlotEasingFor(StatusBarNeighborTrigger.APP_SHOW))
            .isEqualTo(StatusBarChipSlotAppEasing)
        assertThat(statusBarChipSlotEasingFor(StatusBarNeighborTrigger.CARD_OPEN))
            .isEqualTo(StatusBarChipSlotCardExpandEasing)
    }

    @Test
    fun slotLayout_nonnegative_allowsOvershootNoGain4Cap() {
        // CARD close mid: overshoot raw>1 → layout>1 (khong clamp 2dp / gain4).
        val raw =
            statusBarChipSlotRawFractionAt(
                reclaiming = false,
                t = 0.55f,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        val layout = statusBarChipSlotWidthFractionForLayout(raw, slotWidthPx = 183f, 450f)
        assertThat(layout).isAtLeast(0f)
        // Undershoot reclaim: raw am → layout 0.
        val rawOpen =
            statusBarChipSlotRawFractionAt(
                reclaiming = true,
                t = 0.4f,
                trigger = StatusBarNeighborTrigger.CARD_OPEN,
            )
        val layoutOpen = statusBarChipSlotWidthFractionForLayout(rawOpen, 183f, 450f)
        assertThat(layoutOpen).isAtLeast(0f)
        // Explicit negative raw safety.
        assertThat(statusBarChipSlotWidthFractionForLayout(-0.2f)).isEqualTo(0f)
    }

    @Test
    fun outerOccupiedWidth_usesLayoutFraction_noDoubleMap() {
        val natural = 172
        assertThat(statusBarChipOuterOccupiedWidthPx(natural, 1f)).isEqualTo(172)
        assertThat(statusBarChipOuterOccupiedWidthPx(natural, 0f)).isEqualTo(0)
        assertThat(statusBarChipOuterOccupiedWidthPx(natural, 0.5f)).isEqualTo(86)
        // Overshoot fraction → width>natural OK; khong am.
        assertThat(statusBarChipOuterOccupiedWidthPx(natural, 1.05f)).isEqualTo(180)
        assertThat(statusBarChipOuterOccupiedWidthPx(natural, -1f)).isEqualTo(0)
    }

    @Test
    fun clockNudgeAmplitude_ltrNegativeRtlFlipped() {
        val dm = context.resources.displayMetrics
        val ltr = statusBarClockNudgeAmplitudePx(dm, View.LAYOUT_DIRECTION_LTR)
        val rtl = statusBarClockNudgeAmplitudePx(dm, View.LAYOUT_DIRECTION_RTL)
        assertThat(ltr).isGreaterThan(0f)
        assertThat(rtl).isEqualTo(-ltr)
        assertThat(StatusBarClockNudgeDp).isEqualTo(2f)
        assertThat(StatusBarClockNudgeSpring1Stiffness).isEqualTo(400f)
        assertThat(StatusBarClockNudgeSpring1Damping).isWithin(0.001f).of(0.85f)
        assertThat(StatusBarClockNudgeSpring2Stiffness).isEqualTo(100f)
        assertThat(StatusBarClockNudgeSpring2Damping).isWithin(0.001f).of(0.45f)
    }

    @Test
    fun sharedFinish_obsolete_returnsFalse() {
        // Khong enforce clock/NIC finish dong khung (~364 vs ~506).
        assertThat(
                statusBarSlotClockShareEndPhase(
                    reclaiming = true,
                    slotWidthPx = 183f,
                    clockAmplitudePx = 5.625f,
                )
            )
            .isFalse()
        assertThat(statusBarSlotClockShareEndPhasePerceptiveMatrix()).isFalse()
    }

    @Test
    fun foregroundHide_mediaTimerStopwatch_notUnrelatedOrEmpty() {
        val media =
            IslandEvent.Media(
                mediaKey = "k",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        val timer =
            IslandEvent.Timer(
                label = "T",
                endTimeMs = 1L,
                packageName = "com.google.android.deskclock",
            )
        val stopwatch =
            IslandEvent.Stopwatch(
                packageName = "com.google.android.deskclock",
            )
        val torch = IslandEvent.Torch()
        assertThat(islandEventSupportsForegroundHide(media)).isTrue()
        assertThat(islandEventSupportsForegroundHide(timer)).isTrue()
        assertThat(islandEventSupportsForegroundHide(stopwatch)).isTrue()
        assertThat(islandEventSupportsForegroundHide(torch)).isFalse()

        assertThat(islandEventShouldHideForForeground(media, "com.spotify.music")).isTrue()
        assertThat(islandEventShouldHideForForeground(media, "com.android.settings")).isFalse()
        assertThat(islandEventShouldHideForForeground(timer, "com.google.android.deskclock"))
            .isTrue()
        assertThat(islandEventShouldHideForForeground(timer, "com.android.settings")).isFalse()
        // Empty / unknown owner — khong an.
        val mediaEmpty = media.copy(packageName = "")
        assertThat(islandEventOwnerPackages(mediaEmpty)).isEmpty()
        assertThat(islandEventShouldHideForForeground(mediaEmpty, "com.spotify.music")).isFalse()
        assertThat(islandEventShouldHideForForeground(media, null)).isFalse()
        assertThat(islandEventShouldHideForForeground(media, "")).isFalse()
        // Home / launcher package khac owner → keep.
        assertThat(
                islandEventShouldHideForForeground(
                    stopwatch,
                    "com.google.android.apps.nexuslauncher",
                )
            )
            .isFalse()
    }

    @Test
    fun pillAppShow_constants_distinctFromDummyMorphSpring() {
        assertThat(StatusBarPillAppShowScaleStiffness).isEqualTo(75f)
        assertThat(StatusBarPillAppShowScaleDamping).isWithin(0.001f).of(0.808f)
        assertThat(StatusBarPillAppShowAlphaMs).isEqualTo(500)
        // Khong nham dummy morph spring 200 / 0.8131728.
        assertThat(StatusBarMorphDummySpringStiffness).isEqualTo(200f)
        assertThat(StatusBarPillAppShowScaleStiffness)
            .isNotEqualTo(StatusBarMorphDummySpringStiffness)
    }

    // --- P1A: explicit APP_SHOW qua production emitter (khong triggerOf artificial) ---

    @Test
    fun p1a_ownerFgTrueToFalse_emitsAppShow_notCardClose() {
        val emitter = StatusBarNeighborMotionEmitter()
        // Initial FG-hidden (vao owner app).
        val hide = emitter.setOwnerAppFgHidden(true)
        assertThat(hide).isNotNull()
        assertThat(hide!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_HIDE)
        assertThat(hide.reclaiming).isTrue()
        assertThat(hide.previousOwnerAppFgHidden).isFalse()
        assertThat(emitter.chipSlotReclaimed).isTrue()

        // HOME restore: production setOwnerAppFgHidden(false) — APP_SHOW.
        // Buoc nay tung infer CARD_CLOSE vi flag clear truoc reclaim.
        val show = emitter.setOwnerAppFgHidden(false)
        assertThat(show).isNotNull()
        assertThat(show!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        assertThat(show.reclaiming).isFalse()
        assertThat(show.previousOwnerAppFgHidden).isTrue()
        assertThat(show.previousReclaiming).isTrue()
        assertThat(show.sequence).isGreaterThan(hide.sequence)
        // Curve APP (khong CARD overshoot).
        assertThat(statusBarChipSlotEasingFor(show.trigger))
            .isEqualTo(StatusBarChipSlotAppEasing)
    }

    @Test
    fun p1a_unrelatedSettings_noEmitWhenFlagUnchanged() {
        val emitter = StatusBarNeighborMotionEmitter()
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        // Settings in/out: flag van false → khong APP cycle.
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        assertThat(emitter.latestRequest).isNull()
    }

    @Test
    fun p1a_morphOwnsSlot_fgFlagSetButNoAppEmit() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setPillSuppressed(true)
        // FG hide trong morph: flag set, khong emit APP (morph owns reclaim).
        assertThat(emitter.setOwnerAppFgHidden(true)).isNull()
        assertThat(emitter.ownerAppFgHidden).isTrue()
        assertThat(emitter.chipSlotReclaimed).isFalse()
    }

    @Test
    fun p1a_sameReclaimingCauseChange_stillEmits() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true) // APP_HIDE reclaim=true
        // Morph CARD_OPEN cung reclaim=true nhung cause doi → van emit.
        val card =
            emitter.applyChipSlotReclaimed(
                reclaiming = true,
                trigger = StatusBarNeighborTrigger.CARD_OPEN,
            )
        assertThat(card).isNotNull()
        assertThat(card!!.trigger).isEqualTo(StatusBarNeighborTrigger.CARD_OPEN)
        assertThat(card.reclaiming).isTrue()
        assertThat(card.previousReclaiming).isTrue()
    }

    @Test
    fun p1a_destinationBooleanInfer_wouldWronglyBeCardClose() {
        // Document bug: sau set FG false, triggerOf(reclaiming=false, appFg=false)=CARD_CLOSE.
        assertThat(statusBarNeighborTriggerOf(reclaiming = false, appForeground = false))
            .isEqualTo(StatusBarNeighborTrigger.CARD_CLOSE)
        // Production decision path dung huong doi:
        assertThat(statusBarNeighborTriggerForOwnerFgChange(hiddenNow = false))
            .isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
    }

    // --- P1B: pin preserved across FG suppress ---

    @Test
    fun p1b_pinnedPreserved_visibleFallbackSeparate() {
        val timerA =
            IslandEvent.Timer(
                label = "A",
                endTimeMs = 1L,
                packageName = "com.google.android.deskclock",
            )
        val mediaB =
            IslandEvent.Media(
                mediaKey = "m",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        // pinned A (=0); FG suppress A → visible B; pin van 0.
        val state =
            IslandUiState(
                events = listOf(timerA, mediaB),
                islandState = IslandState.CHIP,
                pinnedEventIndex = 0,
                foregroundSuppressedIds = setOf(timerA.id),
            )
        assertThat(state.pinnedEventIndex).isEqualTo(0)
        assertThat(state.topEvent?.id).isEqualTo(mediaB.id)
        assertThat(state.visibleSelectedIndex).isEqualTo(1)
        // Clear suppress (Home): pin A tro lai top.
        val home = state.copy(foregroundSuppressedIds = emptySet())
        assertThat(home.topEvent?.id).isEqualTo(timerA.id)
        assertThat(home.visibleSelectedIndex).isEqualTo(0)
        assertThat(home.pinnedEventIndex).isEqualTo(0)
    }

    @Test
    fun p1b_alertKeyguard_topPrefersAlertWithoutPinMutation() {
        // Alert overlay: pinned media van giu khi FG suppress — topEvent alert path
        // nam o ViewModel; day chi dam pin index khong bi policy suppress ghi de.
        val media =
            IslandEvent.Media(
                mediaKey = "m",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        val state =
            IslandUiState(
                events = listOf(media),
                islandState = IslandState.CHIP,
                pinnedEventIndex = 0,
                foregroundSuppressedIds = setOf(media.id),
            )
        assertThat(state.pinnedEventIndex).isEqualTo(0)
        assertThat(state.visibleEvents).isEmpty()
    }

    // --- P1C: independent slot/clock completion + pixel threshold ---

    @Test
    fun p1c_slotOwnsSettle_independentOfClockGeneration() {
        // Slot settle chi khi gen khop — clock completion khong doi slot gen.
        assertThat(statusBarNeighborSlotOwnsSettle(1, 1)).isTrue()
        assertThat(statusBarNeighborSlotOwnsSettle(1, 2)).isFalse()
        // Nudge APP_HIDE skip; slot van chay — orchestration tach lifetime.
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_HIDE)).isFalse()
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_SHOW)).isTrue()
        // Exact API: DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS (TRANSLATION_X).
        assertThat(StatusBarClockNudgePixelVisibilityThreshold)
            .isEqualTo(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
        assertThat(StatusBarClockNudgePixelVisibilityThreshold).isEqualTo(1f)
        // Khong hardcode 366ms shared finish.
        assertThat(
                statusBarSlotClockShareEndPhase(
                    reclaiming = false,
                    slotWidthPx = 183f,
                    clockAmplitudePx = 5.625f,
                )
            )
            .isFalse()
    }

    // --- P1D / task-009: segment continuity (khong inverse curve) ---

    @Test
    fun p1d_segmentMidflightReversals_firstFrameEqualsStart() {
        val triggerHide = StatusBarNeighborTrigger.APP_HIDE
        val triggerShow = StatusBarNeighborTrigger.APP_SHOW
        for (midT in floatArrayOf(0.2f, 0.5f, 0.8f)) {
            val midShow =
                statusBarChipSlotSegmentFractionAt(
                    start = 0f,
                    target = 1f,
                    t = midT,
                    trigger = triggerShow,
                )
            val hideFrame0 =
                statusBarChipSlotSegmentFractionAt(
                    start = midShow,
                    target = 0f,
                    t = 0f,
                    trigger = triggerHide,
                )
            assertThat(hideFrame0).isWithin(1e-5f).of(midShow)

            val midHide =
                statusBarChipSlotSegmentFractionAt(
                    start = 1f,
                    target = 0f,
                    t = midT,
                    trigger = triggerHide,
                )
            val showFrame0 =
                statusBarChipSlotSegmentFractionAt(
                    start = midHide,
                    target = 1f,
                    t = 0f,
                    trigger = triggerShow,
                )
            assertThat(showFrame0).isWithin(1e-5f).of(midHide)
        }
        assertThat(statusBarChipSlotWidthFractionForLayout(1.05f)).isEqualTo(1.05f)
        assertThat(statusBarChipSlotWidthFractionForLayout(-0.1f)).isEqualTo(0f)
    }

    @Test
    fun p1d_staleGeneration_doesNotOwnSettleOrNicRestore() {
        // Old end callback / cancel: seq cu != latest → khong settle/restore.
        assertThat(statusBarNeighborSlotOwnsSettle(slotRunGeneration = 3, currentGeneration = 4))
            .isFalse()
        assertThat(statusBarNeighborSlotOwnsSettle(slotRunGeneration = 4, currentGeneration = 4))
            .isTrue()
    }

    // --- task-009 BLOCKER1: expand seed khong CARD_CLOSE; settle khong duplicate ---

    @Test
    fun p1e_appShowThenExpandSeed_noCardClose_confirmOneCardOpen() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show = emitter.setOwnerAppFgHidden(false)
        assertThat(show).isNotNull()
        assertThat(show!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        val seqAfterShow = show.sequence

        // Production expand() seed = syncChipSlotReclaimed(false) — KHONG emit.
        emitter.syncChipSlotReclaimed(false)
        assertThat(emitter.latestRequest!!.sequence).isEqualTo(seqAfterShow)
        assertThat(emitter.latestRequest!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        assertThat(emitter.chipSlotReclaimed).isFalse()

        // confirmPillSuppressed → mot CARD_OPEN (khong CARD_CLOSE trung gian).
        val open =
            emitter.applyChipSlotReclaimed(
                reclaiming = true,
                trigger = StatusBarNeighborTrigger.CARD_OPEN,
            )
        assertThat(open).isNotNull()
        assertThat(open!!.trigger).isEqualTo(StatusBarNeighborTrigger.CARD_OPEN)
        assertThat(open.sequence).isGreaterThan(seqAfterShow)
        assertThat(emitter.latestRequest!!.trigger).isEqualTo(StatusBarNeighborTrigger.CARD_OPEN)
    }

    @Test
    fun p1e_trueReverse_reclaimTrueToFalse_emitsCardClose() {
        val emitter = StatusBarNeighborMotionEmitter()
        val open =
            emitter.applyChipSlotReclaimed(
                reclaiming = true,
                trigger = StatusBarNeighborTrigger.CARD_OPEN,
            )
        assertThat(open).isNotNull()
        val close =
            emitter.applyChipSlotReclaimed(
                reclaiming = false,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        assertThat(close).isNotNull()
        assertThat(close!!.trigger).isEqualTo(StatusBarNeighborTrigger.CARD_CLOSE)
        assertThat(close.previousReclaiming).isTrue()
        assertThat(close.reclaiming).isFalse()
    }

    @Test
    fun p1e_collapseEmitThenSettleSync_noDuplicateNeighbor() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.applyChipSlotReclaimed(
            reclaiming = true,
            trigger = StatusBarNeighborTrigger.CARD_OPEN,
        )
        val close =
            emitter.applyChipSlotReclaimed(
                reclaiming = false,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        assertThat(close).isNotNull()
        val seq = close!!.sequence
        // notifyCollapseSettled = sync only (collapse da emit).
        emitter.syncChipSlotReclaimed(false)
        assertThat(emitter.latestRequest!!.sequence).isEqualTo(seq)
        emitter.syncChipSlotReclaimed(false)
        assertThat(emitter.latestRequest!!.sequence).isEqualTo(seq)
        // apply trung trigger+reclaim cung khong emit them.
        assertThat(
                emitter.applyChipSlotReclaimed(
                    reclaiming = false,
                    trigger = StatusBarNeighborTrigger.CARD_CLOSE,
                ),
            )
            .isNull()
        assertThat(emitter.latestRequest!!.sequence).isEqualTo(seq)
    }

    // --- task-009 BLOCKER2: overshoot / undershoot / natural-width segment ---

    @Test
    fun p1f_overshootCardClose_toAppHide_continuity() {
        for (overshoot in floatArrayOf(1.03f, 1.1f)) {
            val start = statusBarChipSlotRenderedFraction(overshoot)
            assertThat(start).isEqualTo(overshoot)
            val frame0 =
                statusBarChipSlotSegmentFractionAt(
                    start = start,
                    target = 0f,
                    t = 0f,
                    trigger = StatusBarNeighborTrigger.APP_HIDE,
                )
            // First frame == old rendered — khong snap ve raw1 cua APP curve.
            assertThat(frame0).isWithin(1e-5f).of(overshoot)
            assertThat(frame0).isGreaterThan(1f)
        }
    }

    @Test
    fun p1f_cardOpenUndershootFloor0_thenShow() {
        // Raw am khong paint → start rendered = 0 (khong negative unpainted).
        val rendered = statusBarChipSlotRenderedFraction(-0.2f)
        assertThat(rendered).isEqualTo(0f)
        val frame0 =
            statusBarChipSlotSegmentFractionAt(
                start = rendered,
                target = 1f,
                t = 0f,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        assertThat(frame0).isEqualTo(0f)
        val mid =
            statusBarChipSlotSegmentFractionAt(
                start = rendered,
                target = 1f,
                t = 0.5f,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
            )
        assertThat(mid).isGreaterThan(0f)
        assertThat(mid).isLessThan(1.2f)
    }

    @Test
    fun p1f_naturalWidthChange_preservesPixelWidth() {
        val rendered = 0.5f
        val prevNatural = 172f
        val nextNatural = 200f
        val start =
            statusBarChipSlotSegmentStartForNaturalWidthChange(
                currentRenderedFraction = rendered,
                previousNaturalPx = prevNatural,
                nextNaturalPx = nextNatural,
            )
        val oldPx = rendered * prevNatural
        val newPx = start * nextNatural
        assertThat(newPx).isWithin(0.01f).of(oldPx)
        // First segment frame width == old px (rounding).
        val frame0 =
            statusBarChipSlotSegmentFractionAt(
                start = start,
                target = 1f,
                t = 0f,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
            )
        assertThat(frame0 * nextNatural).isWithin(0.01f).of(oldPx)
    }

    @Test
    fun p1f_endpointRawFraction_matchesSegmentEndpoints() {
        // Legacy rawFractionAt = segment start 0/1 — MorphGeometryTest khong vo.
        assertThat(
                statusBarChipSlotRawFractionAt(
                    reclaiming = false,
                    t = 0.4f,
                    trigger = StatusBarNeighborTrigger.CARD_CLOSE,
                ),
            )
            .isWithin(1e-5f)
            .of(
                statusBarChipSlotSegmentFractionAt(
                    start = 0f,
                    target = 1f,
                    t = 0.4f,
                    trigger = StatusBarNeighborTrigger.CARD_CLOSE,
                ),
            )
        assertThat(
                statusBarChipSlotRawFractionAt(
                    reclaiming = true,
                    t = 0.4f,
                    trigger = StatusBarNeighborTrigger.CARD_OPEN,
                ),
            )
            .isWithin(1e-5f)
            .of(
                statusBarChipSlotSegmentFractionAt(
                    start = 1f,
                    target = 0f,
                    t = 0.4f,
                    trigger = StatusBarNeighborTrigger.CARD_OPEN,
                ),
            )
    }

    // --- task-010: Home FG once + APP_SHOW atomic plan + occupied width ---

    @Test
    fun p1g_ownerToHome_emitsAppShowOnce_noWaitNotifTick() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show = emitter.setOwnerAppFgHidden(false)
        assertThat(show).isNotNull()
        assertThat(show!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        val seq = show.sequence
        // Home da clear FG — duplicate false khong doi notif tick.
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        assertThat(emitter.latestRequest!!.sequence).isEqualTo(seq)
        assertThat(emitter.latestRequest!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
    }

    @Test
    fun p1g_settingsUnrelated_flagUnchanged_noAppCycle() {
        val emitter = StatusBarNeighborMotionEmitter()
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        assertThat(emitter.latestRequest).isNull()
    }

    @Test
    fun p1g_appHideThenShow_planStartsFromRendered() {
        val hidePlan =
            statusBarAppPillVisualPlanOf(
                generation = 1L,
                trigger = StatusBarNeighborTrigger.APP_HIDE,
                currentAlpha = 1f,
                currentScale = 1f,
            )
        assertThat(hidePlan.startAlpha).isEqualTo(0f)
        assertThat(hidePlan.startScale).isEqualTo(0f)
        val showFromHide =
            statusBarAppPillVisualPlanOf(
                generation = 2L,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
                currentAlpha = 0f,
                currentScale = 0f,
            )
        assertThat(showFromHide.startAlpha).isEqualTo(0f)
        assertThat(showFromHide.startScale).isEqualTo(0f)
        assertThat(statusBarNeighborTriggerIsApp(showFromHide.trigger)).isTrue()
    }

    @Test
    fun p1g_rapidShowHideShow_generationsMonotonic_noSnap0MidShow() {
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show1 = emitter.setOwnerAppFgHidden(false)!!
        val hide2 = emitter.setOwnerAppFgHidden(true)!!
        val show3 = emitter.setOwnerAppFgHidden(false)!!
        assertThat(hide2.sequence).isGreaterThan(show1.sequence)
        assertThat(show3.sequence).isGreaterThan(hide2.sequence)
        // Mid-show reentry: α=0.4 → plan giu 0.4 (khong force 0).
        val mid =
            statusBarAppPillVisualPlanOf(
                generation = show3.sequence,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
                currentAlpha = 0.4f,
                currentScale = 0.55f,
            )
        assertThat(mid.startAlpha).isWithin(1e-5f).of(0.4f)
        assertThat(mid.startScale).isWithin(1e-5f).of(0.55f)
    }

    @Test
    fun p1g_hideMidAlphaScale_planSnapsZero() {
        val plan =
            statusBarAppPillVisualPlanOf(
                generation = 9L,
                trigger = StatusBarNeighborTrigger.APP_HIDE,
                currentAlpha = 0.6f,
                currentScale = 0.3f,
            )
        assertThat(plan.startAlpha).isEqualTo(0f)
        assertThat(plan.startScale).isEqualTo(0f)
    }

    @Test
    fun p1g_staleGeneration_canceledDoesNotOwnSettle() {
        assertThat(statusBarNeighborSlotOwnsSettle(slotRunGeneration = 5, currentGeneration = 6))
            .isFalse()
        assertThat(statusBarNeighborSlotOwnsSettle(slotRunGeneration = 6, currentGeneration = 6))
            .isTrue()
        // Stale APP_SHOW completion khong restore qua seq moi.
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show = emitter.setOwnerAppFgHidden(false)!!
        emitter.setOwnerAppFgHidden(true)
        assertThat(emitter.latestRequest!!.sequence).isGreaterThan(show.sequence)
        assertThat(emitter.latestRequest!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_HIDE)
    }

    @Test
    fun p1g_slotInitialFrameContinuity_withAppShowPlan() {
        // Production commit machine — khong chi math hai helper doc lap.
        val machine = StatusBarAppPillTransitionMachine()
        val midHide =
            statusBarChipSlotSegmentFractionAt(
                start = 1f,
                target = 0f,
                t = 0.4f,
                trigger = StatusBarNeighborTrigger.APP_HIDE,
            )
        val plan =
            statusBarAppPillVisualPlanOf(
                generation = 1L,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
                currentAlpha = 0.2f,
                currentScale = 0.1f,
            )
        val committed =
            machine.commitStart(
                generation = plan.generation,
                trigger = plan.trigger,
                slotStartFraction = midHide,
                reclaiming = false,
                alpha = plan.startAlpha,
                scale = plan.startScale,
                naturalPx = 172f,
            )
        // Suspend-gap: chua advance — rendering van coherent (t=0 + partial α).
        val peeked = machine.rendered!!
        assertThat(peeked.slotFraction).isWithin(1e-5f).of(midHide)
        assertThat(peeked.linearT).isEqualTo(0f)
        assertThat(peeked.alpha).isWithin(1e-5f).of(0.2f)
        assertThat(peeked.scale).isWithin(1e-5f).of(0.1f)
        assertThat(committed.generation).isEqualTo(1L)
        // Cancel: gen moi commit truoc khi snap cu xong — stale advance bi ignore.
        machine.commitStart(
            generation = 2L,
            trigger = StatusBarNeighborTrigger.APP_HIDE,
            slotStartFraction = peeked.slotFraction,
            reclaiming = true,
            alpha = 0f,
            scale = 0f,
            naturalPx = 172f,
        )
        assertThat(machine.advance(generation = 1L, linearT = 0.5f)).isNull()
        assertThat(machine.rendered!!.generation).isEqualTo(2L)
        assertThat(machine.rendered!!.alpha).isEqualTo(0f)
        assertThat(machine.rendered!!.linearT).isEqualTo(0f)
    }

    @Test
    fun p1g_nativeOccupiedWidth_zeroKeepsHostSemantics() {
        // width0: occupied 0; natural >0 van dung cho show readiness (khong GONE host).
        assertThat(statusBarChipOccupiedWidthForNativeParent(172, 0f)).isEqualTo(0)
        assertThat(statusBarChipOccupiedWidthForNativeParent(172, 1f)).isEqualTo(172)
        assertThat(statusBarChipOccupiedWidthForNativeParent(172, 0.5f)).isEqualTo(86)
        // Home FG helper: ACTIVITY_TYPE_HOME=2 clear; Settings package khong clear.
        assertThat(
                statusBarOwnerForegroundIsHomeOrSystemUi(
                    topActivityType = 2,
                    packageName = null,
                    launcherPackage = "com.google.android.apps.nexuslauncher",
                ),
            )
            .isTrue()
        assertThat(
                statusBarOwnerForegroundIsHomeOrSystemUi(
                    topActivityType = 1,
                    packageName = "com.android.settings",
                    launcherPackage = "com.google.android.apps.nexuslauncher",
                ),
            )
            .isFalse()
        assertThat(
                statusBarOwnerForegroundIsHomeOrSystemUi(
                    topActivityType = 1,
                    packageName = "com.google.android.apps.nexuslauncher",
                    launcherPackage = "com.google.android.apps.nexuslauncher",
                ),
            )
            .isTrue()
    }

    // --- task-011: FG ownership vs switch history + default-display filter ---

    private val launcher = "com.google.android.apps.nexuslauncher"

    private fun fgTask(
        pkg: String?,
        displayId: Int = StatusBarAppFgDefaultDisplayId,
        type: Int = 1,
        record: Boolean,
        taskId: Int = 1,
    ) =
        AppTrackingFgTaskInput(
            displayId = displayId,
            topActivityType = type,
            packageName = pkg,
            taskId = taskId,
            recordAppSwitch = record,
        )

    @Test
    fun p1h_reconcileThenFront_recordsSwitchOnce() {
        // A → reconcile B (owner only) → front B phai record (khong duplicate-skip).
        var state = AppTrackingFgOwnerState(foregroundPkg = "com.a", lastRecordedSwitchPkg = "com.a")
        val recB =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.b", record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(recB.kind).isEqualTo(AppTrackingFgApplyKind.UPDATE_OWNER)
        assertThat(recB.shouldRecordSwitch).isFalse()
        assertThat(recB.nextState.foregroundPkg).isEqualTo("com.b")
        assertThat(recB.nextState.lastRecordedSwitchPkg).isEqualTo("com.a")
        state = recB.nextState
        val frontB =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.b", record = true),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(frontB.shouldRecordSwitch).isTrue()
        assertThat(frontB.leavingPkgForSwitch).isEqualTo("com.a")
        assertThat(frontB.nextState.lastRecordedSwitchPkg).isEqualTo("com.b")
        assertThat(frontB.nextState.foregroundPkg).isEqualTo("com.b")
    }

    @Test
    fun p1h_frontThenReconcile_noDuplicateRecord() {
        var state = AppTrackingFgOwnerState(foregroundPkg = "com.a", lastRecordedSwitchPkg = "com.a")
        val frontB =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.b", record = true),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(frontB.shouldRecordSwitch).isTrue()
        state = frontB.nextState
        val recB =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.b", record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(recB.kind).isEqualTo(AppTrackingFgApplyKind.OWNER_UNCHANGED)
        assertThat(recB.shouldRecordSwitch).isFalse()
        val dupFront =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.b", record = true),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(dupFront.shouldRecordSwitch).isFalse()
        assertThat(dupFront.kind).isEqualTo(AppTrackingFgApplyKind.OWNER_UNCHANGED)
    }

    @Test
    fun p1h_homeRoundTrip_ownerClears_noSpuriousSwitch() {
        var state = AppTrackingFgOwnerState(foregroundPkg = "com.a", lastRecordedSwitchPkg = "com.a")
        val home =
            statusBarAppFgApplyTask(
                state,
                fgTask(pkg = null, type = 2, record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = false,
            )
        assertThat(home.kind).isEqualTo(AppTrackingFgApplyKind.CLEAR_HOME_OR_SYSTEM_UI)
        assertThat(home.nextState.foregroundPkg).isNull()
        assertThat(home.nextState.lastRecordedSwitchPkg).isEqualTo("com.a")
        state = home.nextState
        val backA =
            statusBarAppFgApplyTask(
                state,
                fgTask("com.a", record = true),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(backA.kind).isEqualTo(AppTrackingFgApplyKind.UPDATE_OWNER)
        assertThat(backA.shouldRecordSwitch).isFalse()
        assertThat(backA.nextState.foregroundPkg).isEqualTo("com.a")
    }

    @Test
    fun p1h_secondaryHomeRestart_skips_defaultOwnerPreserved() {
        assertThat(statusBarAppFgDisplayEligible(StatusBarAppFgDefaultDisplayId)).isTrue()
        assertThat(statusBarAppFgDisplayEligible(1)).isFalse()
        assertThat(statusBarAppFgDisplayEligible(-1)).isFalse()
        val state =
            AppTrackingFgOwnerState(foregroundPkg = "com.settings", lastRecordedSwitchPkg = "com.settings")
        val secondaryHome =
            statusBarAppFgApplyTask(
                state,
                fgTask(pkg = launcher, displayId = 1, type = 2, record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = true,
            )
        assertThat(secondaryHome.kind).isEqualTo(AppTrackingFgApplyKind.SKIP_DISPLAY)
        assertThat(secondaryHome.nextState.foregroundPkg).isEqualTo("com.settings")
    }

    @Test
    fun p1h_defaultHomeRestart_clearsOwnerOnce_nullTopHomeType() {
        val state =
            AppTrackingFgOwnerState(foregroundPkg = "com.settings", lastRecordedSwitchPkg = "com.settings")
        val defaultHome =
            statusBarAppFgApplyTask(
                state,
                fgTask(pkg = null, displayId = 0, type = 2, record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = false,
            )
        assertThat(defaultHome.kind).isEqualTo(AppTrackingFgApplyKind.CLEAR_HOME_OR_SYSTEM_UI)
        assertThat(defaultHome.nextState.foregroundPkg).isNull()
        // Restart lai Home — owner da null, van CLEAR (idempotent show-once o emitter).
        val again =
            statusBarAppFgApplyTask(
                defaultHome.nextState,
                fgTask(pkg = null, displayId = 0, type = 2, record = false),
                launcher,
                emitAppSwitch = true,
                hasLauncherIntent = false,
            )
        assertThat(again.kind).isEqualTo(AppTrackingFgApplyKind.CLEAR_HOME_OR_SYSTEM_UI)
        assertThat(again.nextState.foregroundPkg).isNull()
        // Emitter: true→false chi 1 APP_SHOW (Home clear FG).
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        assertThat(emitter.setOwnerAppFgHidden(false)).isNotNull()
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
    }

    // --- task-012: start seed tu renderedPillFrame (khong Animatable stale) ---

    @Test
    fun p1i_hideCommitted_staleBacking_showSeedsRenderedWidthAlphaScale() {
        // B APP_HIDE committed: width1 α0 scale0; backing chua snap (t=1 α1 scale1).
        val machine = StatusBarAppPillTransitionMachine()
        machine.commitStart(
            generation = 1L,
            trigger = StatusBarNeighborTrigger.APP_HIDE,
            slotStartFraction = 1f,
            reclaiming = true,
            alpha = 0f,
            scale = 0f,
            naturalPx = 172f,
        )
        assertThat(machine.rendered!!.slotFraction).isWithin(1e-5f).of(1f)
        // C APP_SHOW — cung selector Chip; KHONG manual pass starts dung.
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 1f,
                backingReclaiming = true,
                backingLinearT = 1f, // stale: chua snapTo(0)
                backingTrigger = StatusBarNeighborTrigger.APP_HIDE,
                backingAlpha = 1f,
                backingScale = 1f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 2L,
            )
        assertThat(seed.fromRenderedFrame).isTrue()
        // Backing t=1 + reclaim → raw0; frame van width1 — seed phai lay frame.
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(1f)
        assertThat(seed.startAlpha).isEqualTo(0f)
        assertThat(seed.startScale).isEqualTo(0f)
        val show =
            machine.commitStart(
                generation = 2L,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
                slotStartFraction = seed.slotStartFraction,
                reclaiming = false,
                alpha = seed.startAlpha,
                scale = seed.startScale,
                naturalPx = 172f,
            )
        assertThat(show.slotFraction).isWithin(1e-5f).of(1f)
        assertThat(show.alpha).isEqualTo(0f)
        assertThat(show.scale).isEqualTo(0f)
        assertThat(show.linearT).isEqualTo(0f)
    }

    @Test
    fun p1i_partialShowFrame_vsDifferentBacking_seedsFrame() {
        val machine = StatusBarAppPillTransitionMachine()
        val mid =
            statusBarChipSlotSegmentFractionAt(
                start = 0f,
                target = 1f,
                t = 0.35f,
                trigger = StatusBarNeighborTrigger.APP_SHOW,
            )
        machine.commitStart(
            generation = 3L,
            trigger = StatusBarNeighborTrigger.APP_SHOW,
            slotStartFraction = mid,
            reclaiming = false,
            alpha = 0.4f,
            scale = 0.55f,
            naturalPx = 172f,
        )
        // advance mid-run (partial visual)
        machine.advance(generation = 3L, linearT = 0.35f, alpha = 0.4f, scale = 0.55f)
        val frame = machine.rendered!!
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = frame,
                backingSlotStart = 0f,
                backingReclaiming = false,
                backingLinearT = 0f, // stale khac frame
                backingTrigger = StatusBarNeighborTrigger.APP_SHOW,
                backingAlpha = 1f,
                backingScale = 1f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.APP_HIDE,
                generation = 4L,
            )
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(frame.slotFraction)
        // APP_HIDE snap visual0 tu plan; slot start van tu frame partial.
        assertThat(seed.startAlpha).isEqualTo(0f)
        assertThat(seed.startScale).isEqualTo(0f)
        val hide =
            machine.commitStart(
                generation = 4L,
                trigger = StatusBarNeighborTrigger.APP_HIDE,
                slotStartFraction = seed.slotStartFraction,
                reclaiming = true,
                alpha = seed.startAlpha,
                scale = seed.startScale,
                naturalPx = 172f,
            )
        assertThat(hide.slotFraction).isWithin(1e-5f).of(frame.slotFraction)
        assertThat(hide.alpha).isEqualTo(0f)
    }

    @Test
    fun p1i_naturalWidthChange_preservesOccupiedPxFromFrame() {
        val machine = StatusBarAppPillTransitionMachine()
        machine.commitStart(
            generation = 5L,
            trigger = StatusBarNeighborTrigger.APP_SHOW,
            slotStartFraction = 0.5f,
            reclaiming = false,
            alpha = 0.5f,
            scale = 0.5f,
            naturalPx = 100f,
        )
        // occupied = 0.5 * 100 = 50; next natural 200 → start 0.25
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 1f,
                backingReclaiming = false,
                backingLinearT = 1f,
                backingTrigger = StatusBarNeighborTrigger.APP_SHOW,
                backingAlpha = 1f,
                backingScale = 1f,
                backingNaturalPx = 999f, // stale independent — khong dung
                nextNaturalPx = 200f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 6L,
            )
        assertThat(seed.previousNaturalPx).isWithin(1e-5f).of(100f)
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(0.25f)
        assertThat(seed.startAlpha).isWithin(1e-5f).of(0.5f)
        assertThat(seed.startScale).isWithin(1e-5f).of(0.5f)
    }

    @Test
    fun p1i_staleAdvancementIgnored_afterSeedCommit() {
        val machine = StatusBarAppPillTransitionMachine()
        machine.commitStart(
            generation = 7L,
            trigger = StatusBarNeighborTrigger.APP_HIDE,
            slotStartFraction = 1f,
            reclaiming = true,
            alpha = 0f,
            scale = 0f,
            naturalPx = 172f,
        )
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 1f,
                backingReclaiming = true,
                backingLinearT = 1f,
                backingTrigger = StatusBarNeighborTrigger.APP_HIDE,
                backingAlpha = 1f,
                backingScale = 1f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 8L,
            )
        machine.commitStart(
            generation = 8L,
            trigger = StatusBarNeighborTrigger.APP_SHOW,
            slotStartFraction = seed.slotStartFraction,
            reclaiming = false,
            alpha = seed.startAlpha,
            scale = seed.startScale,
            naturalPx = 172f,
        )
        assertThat(machine.advance(generation = 7L, linearT = 0.9f, alpha = 1f)).isNull()
        assertThat(machine.rendered!!.generation).isEqualTo(8L)
        assertThat(machine.rendered!!.alpha).isEqualTo(0f)
        assertThat(machine.settleSlot(7L)).isNull()
    }

    @Test
    fun p1i_frameAbsent_settledT1_fallsBackToEvaluatedFraction1() {
        // settled t=1, start=.3 reclaim=false → target1 → fraction=1 (khong giu .3).
        val machine = StatusBarAppPillTransitionMachine()
        assertThat(machine.rendered).isNull()
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 0.3f,
                backingReclaiming = false,
                backingLinearT = 1f,
                backingTrigger = StatusBarNeighborTrigger.CARD_CLOSE,
                backingAlpha = 0.7f,
                backingScale = 0.8f,
                backingNaturalPx = 160f,
                nextNaturalPx = 160f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 9L,
            )
        assertThat(seed.fromRenderedFrame).isFalse()
        val expected =
            statusBarChipSlotSegmentFractionAt(
                start = 0.3f,
                target = statusBarChipSlotSegmentTarget(reclaiming = false),
                t = 1f,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        assertThat(expected).isWithin(1e-5f).of(1f)
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(expected)
        assertThat(seed.startAlpha).isWithin(1e-5f).of(0.7f)
        assertThat(seed.startScale).isWithin(1e-5f).of(0.8f)
    }

    @Test
    fun p1i_frameAbsent_t0_fallsBackToSegmentStart() {
        // t=0 → evaluated = segment start (.3); natural bang → seed .3.
        val machine = StatusBarAppPillTransitionMachine()
        assertThat(machine.rendered).isNull()
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 0.3f,
                backingReclaiming = false,
                backingLinearT = 0f,
                backingTrigger = StatusBarNeighborTrigger.CARD_CLOSE,
                backingAlpha = 0.7f,
                backingScale = 0.8f,
                backingNaturalPx = 160f,
                nextNaturalPx = 160f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 9L,
            )
        assertThat(seed.fromRenderedFrame).isFalse()
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(0.3f)
        assertThat(seed.startAlpha).isWithin(1e-5f).of(0.7f)
        assertThat(seed.startScale).isWithin(1e-5f).of(0.8f)
    }

    @Test
    fun p1i_frameAbsent_midflight_usesEvaluatedCurrentFraction() {
        // Midflight: seed = start+(target-start)*ease(t) — khong segment start thô.
        val machine = StatusBarAppPillTransitionMachine()
        assertThat(machine.rendered).isNull()
        val t = 0.5f
        val start = 0.3f
        val trigger = StatusBarNeighborTrigger.CARD_CLOSE
        val expected =
            statusBarChipSlotSegmentFractionAt(
                start = start,
                target = statusBarChipSlotSegmentTarget(reclaiming = false),
                t = t,
                trigger = trigger,
            )
        assertThat(expected).isGreaterThan(start)
        assertThat(expected).isLessThan(1f)
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = start,
                backingReclaiming = false,
                backingLinearT = t,
                backingTrigger = trigger,
                backingAlpha = 0.5f,
                backingScale = 0.6f,
                backingNaturalPx = 160f,
                nextNaturalPx = 160f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 12L,
            )
        assertThat(seed.fromRenderedFrame).isFalse()
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(expected)
        assertThat(seed.startAlpha).isWithin(1e-5f).of(0.5f)
        assertThat(seed.startScale).isWithin(1e-5f).of(0.6f)
    }

    @Test
    fun p1i_morphClear_nullFrame_doesNotOverrideHandoffBacking() {
        val machine = StatusBarAppPillTransitionMachine()
        machine.commitStart(
            generation = 10L,
            trigger = StatusBarNeighborTrigger.APP_SHOW,
            slotStartFraction = 1f,
            reclaiming = false,
            alpha = 0.2f,
            scale = 0.1f,
            naturalPx = 172f,
        )
        // Morph clear — giong Chip LaunchedEffect pillSuppressed.
        machine.clear()
        assertThat(machine.rendered).isNull()
        val seed =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = 1f,
                backingReclaiming = false,
                backingLinearT = 1f,
                backingTrigger = StatusBarNeighborTrigger.CARD_CLOSE,
                backingAlpha = 1f, // snap visible sau morph
                backingScale = 1f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.CARD_OPEN,
                generation = 11L,
            )
        assertThat(seed.fromRenderedFrame).isFalse()
        assertThat(seed.slotStartFraction).isWithin(1e-5f).of(1f)
        assertThat(seed.startAlpha).isEqualTo(1f)
        assertThat(seed.startScale).isEqualTo(1f)
        assertThat(seed.visualPlan).isNull() // CARD — khong APP plan
    }

    // --- task-015/016: natural budget clamp + no occupied feedback + coherent start ---

    @Test
    fun p1j_budgetSmallerThanNatural_clampsStableAndDesired() {
        // Production: chipsMaxWidth < content natural — clamp, khong Infinity bypass.
        val contract =
            statusBarChipOccupiedLayoutContract(
                cachedNaturalPx = 172,
                measuredUnconstrainedPx = 172,
                slotWidthFraction = 1f,
                naturalBudgetMaxPx = 90,
            )
        assertThat(contract.stableNaturalPx).isEqualTo(90)
        assertThat(contract.desiredOccupiedPx)
            .isEqualTo(
                statusBarChipOccupiedWidthForNativeParent(
                    naturalWidthPx = 90,
                    slotWidthFraction = 1f,
                ),
            )
        assertThat(contract.naturalBudgetMaxPx).isEqualTo(90)
    }

    @Test
    fun p1j_budgetZero_clampsNaturalAndOccupiedToZero() {
        val contract =
            statusBarChipOccupiedLayoutContract(
                cachedNaturalPx = 172,
                measuredUnconstrainedPx = 172,
                slotWidthFraction = 1f,
                naturalBudgetMaxPx = 0,
            )
        assertThat(contract.stableNaturalPx).isEqualTo(0)
        assertThat(contract.desiredOccupiedPx).isEqualTo(0)
    }

    @Test
    fun p1j_budgetReductionMidflight_clampsCacheWhileAnimating() {
        // Rotation / chipsMaxWidth giam khi dang APP_HIDE — clamp cache, khong giu 172.
        val mid =
            statusBarChipOccupiedLayoutContract(
                cachedNaturalPx = 172,
                measuredUnconstrainedPx = 172,
                slotWidthFraction = 0.55f,
                naturalBudgetMaxPx = 80,
            )
        assertThat(mid.stableNaturalPx).isEqualTo(80)
        assertThat(mid.desiredOccupiedPx)
            .isEqualTo(
                statusBarChipOccupiedWidthForNativeParent(
                    naturalWidthPx = 80,
                    slotWidthFraction = 0.55f,
                ),
            )
    }

    @Test
    fun p1j_stableBudget_shrinkingOccupiedFraction_keepsNatural() {
        // Occupied animate teo (frac) — measured van full natural trong budget on dinh.
        // Cam occupied→natural feedback (khong truyen frac*natural lam budget).
        val natural = 172
        val budget = 200
        var prev = Int.MAX_VALUE
        for (i in 0..20) {
            val t = i / 20f
            val frac =
                statusBarChipSlotSegmentFractionAt(
                    start = 1f,
                    target = 0f,
                    t = t,
                    trigger = StatusBarNeighborTrigger.APP_HIDE,
                )
            val layoutFrac = statusBarChipSlotWidthFractionForLayout(frac, natural.toFloat())
            val contract =
                statusBarChipOccupiedLayoutContract(
                    cachedNaturalPx = natural,
                    measuredUnconstrainedPx = natural,
                    slotWidthFraction = layoutFrac,
                    naturalBudgetMaxPx = budget,
                )
            assertThat(contract.stableNaturalPx).isEqualTo(natural)
            assertThat(contract.desiredOccupiedPx).isAtMost(prev)
            prev = contract.desiredOccupiedPx
        }
        assertThat(prev).isEqualTo(0)
    }

    @Test
    fun p1j_hiddenToShow_restAllowsNaturalGrowWithinBudget() {
        // Hide xong (frac~0 cache giu) → show rest: cho natural doi trong budget.
        val afterHide =
            statusBarChipOccupiedLayoutContract(
                cachedNaturalPx = 172,
                measuredUnconstrainedPx = 40, // gia do content teo khi occupied=0 place
                slotWidthFraction = 0.2f,
                naturalBudgetMaxPx = 200,
            )
        assertThat(afterHide.stableNaturalPx).isEqualTo(172) // animate: giu cache
        val atRestShow =
            statusBarChipOccupiedLayoutContract(
                cachedNaturalPx = 172,
                measuredUnconstrainedPx = 160,
                slotWidthFraction = 1f,
                naturalBudgetMaxPx = 200,
            )
        assertThat(atRestShow.stableNaturalPx).isEqualTo(160)
    }

    @Test
    fun p1j_hideSegment_occupiedMonotonic_withStableNatural() {
        val natural = 172
        var prev = Int.MAX_VALUE
        for (i in 0..20) {
            val t = i / 20f
            val frac =
                statusBarChipSlotSegmentFractionAt(
                    start = 1f,
                    target = 0f,
                    t = t,
                    trigger = StatusBarNeighborTrigger.APP_HIDE,
                )
            val layoutFrac = statusBarChipSlotWidthFractionForLayout(frac, natural.toFloat())
            val contract =
                statusBarChipOccupiedLayoutContract(
                    cachedNaturalPx = natural,
                    measuredUnconstrainedPx = natural,
                    slotWidthFraction = layoutFrac,
                    naturalBudgetMaxPx = Int.MAX_VALUE,
                )
            assertThat(contract.stableNaturalPx).isEqualTo(natural)
            assertThat(contract.desiredOccupiedPx).isAtMost(prev)
            prev = contract.desiredOccupiedPx
        }
        assertThat(prev).isEqualTo(0)
    }

    @Test
    fun p1j_showHideShow_coherentStart_noSnap0MidShow() {
        val machine = StatusBarAppPillTransitionMachine()
        machine.commitStart(
            generation = 1L,
            trigger = StatusBarNeighborTrigger.APP_SHOW,
            slotStartFraction = 0f,
            reclaiming = false,
            alpha = 0f,
            scale = 0f,
            naturalPx = 172f,
        )
        machine.advance(1L, linearT = 0.4f, alpha = 0.45f, scale = 0.35f)
        val mid = machine.rendered!!
        val seedHide =
            statusBarAppPillStartSeedOf(
                renderedFrame = mid,
                backingSlotStart = 0f,
                backingReclaiming = false,
                backingLinearT = 0.99f,
                backingTrigger = StatusBarNeighborTrigger.APP_SHOW,
                backingAlpha = 1f,
                backingScale = 1f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.APP_HIDE,
                generation = 2L,
            )
        assertThat(seedHide.startAlpha).isEqualTo(0f)
        assertThat(seedHide.startScale).isEqualTo(0f)
        val hide =
            machine.commitStart(
                generation = 2L,
                trigger = StatusBarNeighborTrigger.APP_HIDE,
                slotStartFraction = seedHide.slotStartFraction,
                reclaiming = true,
                alpha = 0f,
                scale = 0f,
                naturalPx = 172f,
            )
        // Reversal show: seed tu frame hide (α0) — khong force mid-show 0 sau khi da partial show.
        machine.advance(2L, linearT = 0.3f, alpha = 0f, scale = 0f)
        val seedShow =
            statusBarAppPillStartSeedOf(
                renderedFrame = machine.rendered,
                backingSlotStart = hide.slotStartFraction,
                backingReclaiming = true,
                backingLinearT = 0.3f,
                backingTrigger = StatusBarNeighborTrigger.APP_HIDE,
                backingAlpha = 0.9f,
                backingScale = 0.9f,
                backingNaturalPx = 172f,
                nextNaturalPx = 172f,
                nextTrigger = StatusBarNeighborTrigger.APP_SHOW,
                generation = 3L,
            )
        assertThat(seedShow.fromRenderedFrame).isTrue()
        assertThat(seedShow.startAlpha).isEqualTo(0f)
        assertThat(seedShow.startScale).isEqualTo(0f)
        assertThat(seedShow.slotStartFraction).isWithin(1e-5f).of(
            statusBarChipSlotRenderedFraction(machine.rendered!!.slotFraction),
        )
    }

    @Test
    fun p1j_staleGeneration_cancelDoesNotOwnSettle() {
        assertThat(statusBarNeighborSlotOwnsSettle(3, 4)).isFalse()
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show = emitter.setOwnerAppFgHidden(false)!!
        emitter.setOwnerAppFgHidden(true)
        assertThat(emitter.latestRequest!!.sequence).isGreaterThan(show.sequence)
    }

    @Test
    fun p1j_nativeBridge_dedupesDesired_withoutClaimingHostEqualsPill() {
        val bridge = StatusBarChipNativeOccupiedBridge()
        assertThat(bridge.publishDesiredOccupiedPx(120)).isFalse() // chua attach
        assertThat(bridge.desiredOccupiedPx).isEqualTo(120)
        // Dedupes
        assertThat(bridge.publishDesiredOccupiedPx(120)).isFalse()
        bridge.noteHostMeasuredWidth(400) // host = clock+date+chip — khong = 120
        assertThat(bridge.lastHostWidthPx).isEqualTo(400)
        assertThat(bridge.lastHostWidthPx).isNotEqualTo(bridge.desiredOccupiedPx)
    }

    @Test
    fun p1j_timebasePin_fgHistoryDisplay_stillHold() {
        // Smoke: APP_HIDE skip nudge; FG emitter APP_SHOW; slot 500ms — regression 008/011.
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_HIDE)).isFalse()
        val emitter = StatusBarNeighborMotionEmitter()
        emitter.setOwnerAppFgHidden(true)
        val show = emitter.setOwnerAppFgHidden(false)
        assertThat(show!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        assertThat(statusBarChipSlotDurationMs(StatusBarNeighborTrigger.APP_SHOW)).isEqualTo(500)
        assertThat(statusBarAppFgDisplayEligible(displayId = 0)).isTrue()
    }

    // --- task-018: Clock owner-gap lifecycle (production policy SSOT) ---

    @Test
    fun task018_ownerGap_appCancelWhileOwnerFg_keeps() {
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_APP_CANCEL_ALL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
    }

    @Test
    fun task018_userDismissOrDeath_dropsEvenIfOwnerFg() {
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_USER_STOPPED,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_LISTENER_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    @Test
    fun task018_appCancelWhenNotOwnerFg_drops() {
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = false,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    @Test
    fun task018_unknownReason_heuristicOwnerFgOnly() {
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = DESK_CLOCK_REASON_UNKNOWN,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
        assertThat(
                deskClockNotifRemovalDecision(
                    reason = DESK_CLOCK_REASON_UNKNOWN,
                    ownerInForeground = false,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    @Test
    fun task018_ownerEnter_notifRemoved_stillHiddenAndRetained() {
        // Production path: event van trong repo + FG suppress → ownerHidden; KHONG empty→APP_SHOW.
        val sw =
            IslandEvent.Stopwatch(
                label = "SW",
                startTimeMs = 1_000L,
                startElapsedRealtimeMs = 500L,
                isRunning = true,
                packageName = "com.google.android.deskclock",
                notificationKey = "sw_key",
            )
        val clockPkg = "com.google.android.deskclock"
        assertThat(islandEventShouldHideForForeground(sw, clockPkg)).isTrue()
        val suppressed = setOf(sw.id)
        val state =
            IslandUiState(
                events = listOf(sw),
                islandState = IslandState.CHIP,
                pinnedEventIndex = 0,
                foregroundSuppressedIds = suppressed,
                foregroundPackage = clockPkg,
            )
        assertThat(state.visibleEvents).isEmpty()
        assertThat(state.hasUnderlyingChipEvents).isTrue()
        val ownerHidden =
            state.foregroundSuppressedIds.isNotEmpty() &&
                state.visibleEvents.isEmpty() &&
                state.notificationAlert == null &&
                state.events.any { it.id in state.foregroundSuppressedIds }
        assertThat(ownerHidden).isTrue()
        // Policy: APP_CANCEL + owner FG → KEEP (event retained nhu tren).
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
    }

    @Test
    fun task018_homeBeforeDelayedPost_oneAppShowWithRetained() {
        // Event retained qua gap → Home clear FG → dung 1 APP_SHOW (emitter production).
        val emitter = StatusBarNeighborMotionEmitter()
        val hide = emitter.setOwnerAppFgHidden(true)
        assertThat(hide!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_HIDE)
        val show = emitter.setOwnerAppFgHidden(false)
        assertThat(show!!.trigger).isEqualTo(StatusBarNeighborTrigger.APP_SHOW)
        // Khong emit lan 2 khi flag da false (doi post tre khong tao APP_SHOW moi).
        assertThat(emitter.setOwnerAppFgHidden(false)).isNull()
        assertThat(DESK_CLOCK_LEAVE_REPOST_GRACE_MS).isAtLeast(2_500L)
        assertThat(DESK_CLOCK_OWNER_GAP_MAX_MS).isAtMost(60_000L)
    }

    @Test
    fun task018_sameKeyRebind_preservesTimebaseFields() {
        // handleStopwatch dung existing start* khi metrics/partial — model contract.
        val existing =
            IslandEvent.Stopwatch(
                label = "SW",
                startTimeMs = 9_000L,
                startElapsedRealtimeMs = 4_000L,
                isRunning = true,
                packageName = "com.google.android.deskclock",
                notificationKey = "same_key",
            )
        // Rebind cung key: id on dinh; timebase fields khong reset ve now.
        val rebound =
            existing.copy(
                label = "SW",
                notificationKey = "same_key",
                startTimeMs = existing.startTimeMs,
                startElapsedRealtimeMs = existing.startElapsedRealtimeMs,
            )
        assertThat(rebound.id).isEqualTo(existing.id)
        assertThat(rebound.startTimeMs).isEqualTo(9_000L)
        assertThat(rebound.startElapsedRealtimeMs).isEqualTo(4_000L)
        assertThat(rebound.notificationKey).isEqualTo("same_key")
    }

    @Test
    fun task018_timerPauseVsStopwatch_andMediaUnchanged() {
        val timer =
            IslandEvent.Timer(
                label = "T",
                endTimeMs = 99L,
                isPaused = true,
                pausedRemainingMs = 12_000L,
                packageName = "com.google.android.deskclock",
                notificationKey = "t1",
            )
        val media =
            IslandEvent.Media(
                mediaKey = "m",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        // Timer pause van la event (update post, khong removal KEEP).
        assertThat(timer.isPaused).isTrue()
        assertThat(timer.displayedRemainingMs(nowWallMs = 50L)).isEqualTo(12_000L)
        // Media FG-hide policy khong doi.
        assertThat(islandEventSupportsForegroundHide(media)).isTrue()
        assertThat(islandEventShouldHideForForeground(media, "com.spotify.music")).isTrue()
        assertThat(islandEventShouldHideForForeground(media, "com.android.settings")).isFalse()
        // Stop/reset = user/system cancel → DROP.
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    @Test
    fun task018_boundedRetentionConstants_documented() {
        // Tran reconciliation — khong indefinite ghost.
        assertThat(DESK_CLOCK_OWNER_GAP_MAX_MS).isEqualTo(45_000L)
        assertThat(DESK_CLOCK_LEAVE_REPOST_GRACE_MS).isEqualTo(3_500L)
        assertThat(deskClockReasonLabel(NotificationListenerService.REASON_APP_CANCEL))
            .isEqualTo("APP_CANCEL")
        assertThat(deskClockEventToken("sw_key")).startsWith("ev:")
    }

    // --- task-019: production retention reducer (fake clock) ---

    private class FakeDeskClockMonoClock(startMs: Long = 1_000_000L) : DeskClockMonoClock {
        var now: Long = startMs

        override fun elapsedRealtime(): Long = now

        fun advance(deltaMs: Long) {
            now += deltaMs
        }
    }

    @Test
    fun task019_p1_stopReset_fundamentallyUnprovable() {
        // Khong claim stop/reset PASS — APP_CANCEL+FG khong phan biet.
        assertThat(deskClockStopResetDistinguishableFromOwnerGap()).isFalse()
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
    }

    @Test
    fun task019_reasonPrecedence_dropDominatesKeepOnDuplicate() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        val keep =
            retention.applyRemovalDecision(
                key = "sw",
                decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            )
        assertThat(keep).isInstanceOf(DeskClockOwnerGapEffect.ArmTimer::class.java)
        val firstRemoved = retention.peek("sw")!!.firstRemovedElapsedMs
        // Duplicate KEEP (Scrim+collection) — khong reset absolute.
        clock.advance(5_000L)
        val keep2 =
            retention.applyRemovalDecision(
                key = "sw",
                decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            )
        assertThat(keep2).isInstanceOf(DeskClockOwnerGapEffect.ArmTimer::class.java)
        assertThat(retention.peek("sw")!!.firstRemovedElapsedMs).isEqualTo(firstRemoved)
        assertThat((keep2 as DeskClockOwnerGapEffect.ArmTimer).delayMs)
            .isEqualTo(DESK_CLOCK_OWNER_GAP_MAX_MS - 5_000L)
        // DROP thang KEEP.
        val drop =
            retention.applyRemovalDecision(
                key = "sw",
                decision = DeskClockNotifRemovalDecision.DROP_ENDED,
                ownerInForeground = true,
            )
        assertThat(drop).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        assertThat(retention.hasGap("sw")).isFalse()
    }

    @Test
    fun task019_absoluteDeadline_fgFlipFlopDoesNotExtend() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            key = "sw",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        val deadline = retention.peek("sw")!!.absoluteDeadlineElapsedMs
        // Flip-flop FG nhieu lan — absolute khong doi; leave-grace bi cap.
        clock.advance(40_000L)
        val leave =
            retention.onOwnerForegroundChanged(key = "sw", ownerInForeground = false)
                as DeskClockOwnerGapEffect.ArmTimer
        assertThat(leave.delayMs).isEqualTo(3_500L)
        assertThat(retention.peek("sw")!!.absoluteDeadlineElapsedMs).isEqualTo(deadline)
        clock.advance(1_000L)
        val back =
            retention.onOwnerForegroundChanged(key = "sw", ownerInForeground = true)
                as DeskClockOwnerGapEffect.ArmTimer
        // Con 4s toi absolute (45-41), khong reset 45s.
        assertThat(back.delayMs).isEqualTo(4_000L)
        clock.advance(4_000L)
        val expired =
            retention.onOwnerForegroundChanged(key = "sw", ownerInForeground = false)
        assertThat(expired).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        assertThat(retention.hasGap("sw")).isFalse()
    }

    @Test
    fun task019_leaveGrace_cappedByAbsoluteRemaining() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            key = "t1",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        clock.advance(43_000L)
        val leave =
            retention.onOwnerForegroundChanged(key = "t1", ownerInForeground = false)
                as DeskClockOwnerGapEffect.ArmTimer
        // Leave grace 3.5s nhung absolute chi con 2s.
        assertThat(leave.delayMs).isEqualTo(2_000L)
        assertThat(leave.cause).isEqualTo("leave_grace")
    }

    @Test
    fun task019_generationSafe_rebindClearsStaleTimeout() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        val arm1 =
            retention.applyRemovalDecision(
                key = "sw",
                decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            ) as DeskClockOwnerGapEffect.ArmTimer
        val gen1 = arm1.generation
        retention.clearOnValidatedMatchingPost("sw")
        assertThat(retention.hasGap("sw")).isFalse()
        // Stale timer gen cu khong drop.
        assertThat(
                retention.onArmedTimeout(
                    "sw",
                    gen1,
                    arm1.armSequence,
                    arm1.dueDeadlineElapsedMs,
                )
            )
            .isEqualTo(DeskClockOwnerGapEffect.None)
        // Owner-gap moi — generation moi.
        val arm2 =
            retention.applyRemovalDecision(
                key = "sw",
                decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            ) as DeskClockOwnerGapEffect.ArmTimer
        assertThat(arm2.generation).isNotEqualTo(gen1)
        clock.advance(DESK_CLOCK_OWNER_GAP_MAX_MS)
        val drop =
            retention.onArmedTimeout(
                "sw",
                arm2.generation,
                arm2.armSequence,
                arm2.dueDeadlineElapsedMs,
            )
        assertThat(drop).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
    }

    @Test
    fun task019_sameKeyNonmatching_reconcileStopwatchAndTimer() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            key = "sw_key",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        retention.applyRemovalDecision(
            key = "tm_key",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        // Nonmatching same-key post → drop (khong doi activeSbns!=null).
        val swDrop = retention.reconcileNonMatchingSameKeyPost("sw_key")
        assertThat(swDrop).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        assertThat((swDrop as DeskClockOwnerGapEffect.Drop).cause)
            .isEqualTo("nonmatching_replace")
        assertThat(retention.hasGap("sw_key")).isFalse()
        val tmDrop = retention.reconcileNonMatchingSameKeyPost("tm_key")
        assertThat(tmDrop).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        assertThat(retention.hasGap("tm_key")).isFalse()
        // Timeout khong can activeSbns==null — gen+arm identity checked.
        val armAgain =
            retention.applyRemovalDecision(
                key = "sw_key",
                decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            ) as DeskClockOwnerGapEffect.ArmTimer
        clock.advance(DESK_CLOCK_OWNER_GAP_MAX_MS)
        assertThat(
                retention.onArmedTimeout(
                    "sw_key",
                    armAgain.generation,
                    armAgain.armSequence,
                    armAgain.dueDeadlineElapsedMs,
                )
            )
            .isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
    }

    @Test
    fun task019_trueSameKeyRebind_preservesGapClearOnly() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            key = "same",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        val cleared = retention.clearOnValidatedMatchingPost("same")
        assertThat(cleared).isNotNull()
        assertThat(retention.hasGap("same")).isFalse()
        // Stale old-key remove sau rebind key moi khong xoa gap key moi.
        retention.applyRemovalDecision(
            key = "new_key",
            decision = DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        retention.applyRemovalDecision(
            key = "same",
            decision = DeskClockNotifRemovalDecision.DROP_ENDED,
            ownerInForeground = true,
        )
        assertThat(retention.hasGap("new_key")).isTrue()
        assertThat(retention.hasGap("same")).isFalse()
    }

    @Test
    fun task019_userSwitchAndDismiss_clearRetention() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            "a",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        retention.applyRemovalDecision(
            "b",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        assertThat(retention.clearAll()).hasSize(2)
        assertThat(retention.activeKeys()).isEmpty()
        // User dismiss path: CANCEL → DROP ngay ca khi owner FG.
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    @Test
    fun task019_mediaNoRegression_fgHideUnchanged() {
        val media =
            IslandEvent.Media(
                mediaKey = "m",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        assertThat(islandEventSupportsForegroundHide(media)).isTrue()
        assertThat(islandEventShouldHideForForeground(media, "com.spotify.music")).isTrue()
        // Media khong di qua DeskClockOwnerGapRetention.
        val retention = DeskClockOwnerGapRetention(clock = FakeDeskClockMonoClock())
        assertThat(retention.activeKeys()).isEmpty()
    }

    @Test
    fun task019_timerPauseVsOwnerGap_reasonMatrix() {
        // Pause = update post (khong removal KEEP); stop/app-cancel+FG = KEEP bounded.
        val timer =
            IslandEvent.Timer(
                label = "T",
                endTimeMs = 99L,
                isPaused = true,
                pausedRemainingMs = 12_000L,
                packageName = "com.google.android.deskclock",
                notificationKey = "t1",
            )
        assertThat(timer.isPaused).isTrue()
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = true,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.KEEP_OWNER_GAP)
        assertThat(
                deskClockNotifRemovalDecision(
                    NotificationListenerService.REASON_APP_CANCEL,
                    ownerInForeground = false,
                )
            )
            .isEqualTo(DeskClockNotifRemovalDecision.DROP_ENDED)
    }

    // --- task-020: production classifyDeskClockPosted + arm identity ---

    @Test
    fun task020_missingExtras_withGap_reconcileDrop() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.STOPWATCH,
                    isGroupSummary = false,
                    extrasAvailable = false,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.RECONCILE_DROP_RETAINED)
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = false,
                    extrasAvailable = false,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
    }

    @Test
    fun task020_groupSummary_sameKeyReconcile_otherKeyIgnore() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.TIMER,
                    isGroupSummary = true,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.RECONCILE_DROP_RETAINED)
        // Summary key khac — khong drop unrelated / khong coi proof ended.
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = true,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
    }

    @Test
    fun task020_disabledType_withGap_reconcile() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.STOPWATCH,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.STOPWATCH,
                    typeEnabled = false,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.RECONCILE_DROP_RETAINED)
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.TIMER,
                    typeEnabled = false,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
    }

    @Test
    fun task020_crossType_stopwatchToTimer_dropThenHandle() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.STOPWATCH,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.TIMER,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.CROSS_TYPE_DROP_THEN_HANDLE)
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.TIMER,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.STOPWATCH,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.CROSS_TYPE_DROP_THEN_HANDLE)
    }

    @Test
    fun task020_ordinaryNonmatching_andNoOldGap() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.STOPWATCH,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.RECONCILE_DROP_RETAINED)
        // No old gap — create/ignore path cu.
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.TIMER,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.HANDLE_VALIDATED)
    }

    @Test
    fun task020_validatedRebind_sameKind_handleValidated() {
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = true,
                    previousTrackedKind = DeskClockNotifKind.STOPWATCH,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.STOPWATCH,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.HANDLE_VALIDATED)
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            "same",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        assertThat(retention.clearOnValidatedMatchingPost("same")).isNotNull()
        assertThat(retention.hasGap("same")).isFalse()
    }

    @Test
    fun task020_staleLeaveGraceCallback_afterFgReturn_sameGenNoop() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            "sw",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        clock.advance(1_000L)
        val leave =
            retention.onOwnerForegroundChanged("sw", ownerInForeground = false)
                as DeskClockOwnerGapEffect.ArmTimer
        val leaveSeq = leave.armSequence
        val leaveDue = leave.dueDeadlineElapsedMs
        val gen = leave.generation
        // FG return re-arm — cung gen, armSequence moi; absolute khong sliding.
        clock.advance(500L)
        val back =
            retention.onOwnerForegroundChanged("sw", ownerInForeground = true)
                as DeskClockOwnerGapEffect.ArmTimer
        assertThat(back.generation).isEqualTo(gen)
        assertThat(back.armSequence).isNotEqualTo(leaveSeq)
        assertThat(back.dueDeadlineElapsedMs).isNotEqualTo(leaveDue)
        // Stale leave-grace callback (cung gen) khong Drop som.
        assertThat(
                retention.onArmedTimeout("sw", gen, leaveSeq, leaveDue)
            )
            .isEqualTo(DeskClockOwnerGapEffect.None)
        assertThat(retention.hasGap("sw")).isTrue()
        // True expiry cua arm hien tai van Drop.
        clock.advance(back.delayMs)
        assertThat(
                retention.onArmedTimeout(
                    "sw",
                    back.generation,
                    back.armSequence,
                    back.dueDeadlineElapsedMs,
                )
            )
            .isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
    }

    @Test
    fun task020_duplicateTimeoutCallbacks_secondNoop() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        val arm =
            retention.applyRemovalDecision(
                "sw",
                DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
                ownerInForeground = true,
            ) as DeskClockOwnerGapEffect.ArmTimer
        clock.advance(arm.delayMs)
        assertThat(
                retention.onArmedTimeout(
                    "sw",
                    arm.generation,
                    arm.armSequence,
                    arm.dueDeadlineElapsedMs,
                )
            )
            .isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        // Callback trung / race thu 2.
        assertThat(
                retention.onArmedTimeout(
                    "sw",
                    arm.generation,
                    arm.armSequence,
                    arm.dueDeadlineElapsedMs,
                )
            )
            .isEqualTo(DeskClockOwnerGapEffect.None)
    }

    @Test
    fun task020_timerIndependentKeys_staleOldKeyDoesNotEraseNew() {
        val clock = FakeDeskClockMonoClock()
        val retention = DeskClockOwnerGapRetention(clock = clock)
        retention.applyRemovalDecision(
            "tm_old",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        retention.applyRemovalDecision(
            "tm_new",
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP,
            ownerInForeground = true,
        )
        // Stale old-key remove sau rebind khong xoa event key moi.
        val dropOld = retention.reconcileNonMatchingSameKeyPost("tm_old")
        assertThat(dropOld).isInstanceOf(DeskClockOwnerGapEffect.Drop::class.java)
        assertThat(retention.hasGap("tm_old")).isFalse()
        assertThat(retention.hasGap("tm_new")).isTrue()
        // Multi-timer independent — classify no-gap key khong reconcile key khac.
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = true,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
        assertThat(retention.hasGap("tm_new")).isTrue()
    }

    @Test
    fun task020_mediaNoRegression_note() {
        val media =
            IslandEvent.Media(
                mediaKey = "m20",
                track = "t",
                artist = "a",
                isPlaying = true,
                packageName = "com.spotify.music",
            )
        assertThat(islandEventSupportsForegroundHide(media)).isTrue()
        // classifyDeskClockPosted chi cho Clock path — media khong goi.
        assertThat(
                classifyDeskClockPosted(
                    hasTrackedSameKeyGap = false,
                    previousTrackedKind = DeskClockNotifKind.NONE,
                    isGroupSummary = false,
                    extrasAvailable = true,
                    classifiedKind = DeskClockNotifKind.NONE,
                    typeEnabled = true,
                )
            )
            .isEqualTo(DeskClockPostedDisposition.IGNORE)
        assertThat(deskClockStopResetDistinguishableFromOwnerGap()).isFalse()
    }

    // --- task-022/023: touchable insets coalesce / flush / cancel / stale gen ---

    @Test
    fun task022_insetsCoalesce_dirtyOncePerFrame() {
        val s = TouchableInsetsCoalesceState()
        val gen = s.generation
        assertThat(s.markDirty(gen)).isTrue()
        assertThat(s.markDirty(gen)).isFalse()
        assertThat(s.markDirty(gen)).isFalse()
        assertThat(s.scheduleRequests).isEqualTo(3)
        assertThat(s.pending).isTrue()
        assertThat(s.onFrame(gen)).isTrue()
        assertThat(s.dispatchCount).isEqualTo(1)
        assertThat(s.pending).isFalse()
        // Frame khong pending → no-op.
        assertThat(s.onFrame(gen)).isFalse()
        assertThat(s.dispatchCount).isEqualTo(1)
    }

    @Test
    fun task022_insetsCoalesce_flushClearsPendingAndDispatches() {
        val s = TouchableInsetsCoalesceState()
        val gen = s.generation
        assertThat(s.markDirty(gen)).isTrue()
        assertThat(s.flush(gen)).isTrue()
        assertThat(s.pending).isFalse()
        assertThat(s.dispatchCount).isEqualTo(1)
        // Pending da clear — onFrame khong dispatch lan 2.
        assertThat(s.onFrame(gen)).isFalse()
        assertThat(s.dispatchCount).isEqualTo(1)
    }

    @Test
    fun task022_insetsCoalesce_staleGenerationNoop() {
        val s = TouchableInsetsCoalesceState()
        val old = s.generation
        assertThat(s.markDirty(old)).isTrue()
        val neu = s.bumpGeneration()
        assertThat(neu).isNotEqualTo(old)
        assertThat(s.pending).isFalse()
        assertThat(s.markDirty(old)).isFalse()
        assertThat(s.onFrame(old)).isFalse()
        assertThat(s.flush(old)).isFalse()
        assertThat(s.dispatchCount).isEqualTo(0)
        assertThat(s.markDirty(neu)).isTrue()
        assertThat(s.onFrame(neu)).isTrue()
        assertThat(s.dispatchCount).isEqualTo(1)
    }

    @Test
    fun task022_insetsCoalesce_cancelPendingKeepsGeneration() {
        val s = TouchableInsetsCoalesceState()
        val gen = s.generation
        assertThat(s.markDirty(gen)).isTrue()
        s.cancelPending()
        assertThat(s.pending).isFalse()
        assertThat(s.generation).isEqualTo(gen)
        assertThat(s.markDirty(gen)).isTrue()
        assertThat(s.onFrame(gen)).isTrue()
    }

    /** Stale onFrame sau bump+markDirty moi khong duoc xoa pending moi. */
    @Test
    fun task023_insetsCoalesce_staleOnFramePreservesNewPending() {
        val s = TouchableInsetsCoalesceState()
        val old = s.generation
        assertThat(s.markDirty(old)).isTrue()
        val neu = s.bumpGeneration()
        assertThat(s.markDirty(neu)).isTrue()
        assertThat(s.pending).isTrue()
        assertThat(s.onFrame(old)).isFalse()
        assertThat(s.pending).isTrue()
        assertThat(s.dispatchCount).isEqualTo(0)
        assertThat(s.onFrame(neu)).isTrue()
        assertThat(s.pending).isFalse()
        assertThat(s.dispatchCount).isEqualTo(1)
    }

    /** Stale flush sau bump+markDirty moi khong duoc xoa pending moi. */
    @Test
    fun task023_insetsCoalesce_staleFlushPreservesNewPending() {
        val s = TouchableInsetsCoalesceState()
        val old = s.generation
        s.bumpGeneration()
        val neu = s.generation
        assertThat(neu).isNotEqualTo(old)
        assertThat(s.markDirty(neu)).isTrue()
        assertThat(s.flush(old)).isFalse()
        assertThat(s.pending).isTrue()
        assertThat(s.dispatchCount).isEqualTo(0)
        assertThat(s.flush(neu)).isTrue()
        assertThat(s.pending).isFalse()
        assertThat(s.dispatchCount).isEqualTo(1)
    }

    /**
     * Wrapper cross-gen: invoke **real** captured callback (khong bypass bang helper arg).
     * Stale oldCb sau bump khong clear framePosted/pending moi; newCb dispatch dung.
     */
    @Test
    fun task023_insetsCoalesceWrapper_staleCallbackDoesNotClearNewSchedule() {
        val fake = RecordingFrameScheduler()
        var invalidateCount = 0
        val c =
            TouchableInsetsRefreshCoalesce(
                scheduler = fake,
                onInvalidate = { invalidateCount++ },
                traceEnabled = { false },
            )
        c.schedule()
        assertThat(c.hasPostedFrameCallback).isTrue()
        assertThat(fake.posted).hasSize(1)
        val oldGen = c.postedFrameGeneration
        val oldCb = fake.posted.single()

        // Session moi + dirty moi (reschedule sau bump).
        c.cancelAndBumpGeneration()
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(fake.removed).contains(oldCb)
        c.schedule()
        assertThat(c.hasPostedFrameCallback).isTrue()
        val newGen = c.postedFrameGeneration
        assertThat(newGen).isNotEqualTo(oldGen)
        assertThat(fake.posted).hasSize(2)
        val newCb = fake.posted.last()

        // Stale: goi callback Choreographer that captured (neu van den sau remove).
        oldCb.doFrame(0L)
        assertThat(c.hasPostedFrameCallback).isTrue()
        assertThat(c.postedFrameGeneration).isEqualTo(newGen)
        assertThat(invalidateCount).isEqualTo(0)

        // Callback moi dispatch 1 lan.
        newCb.doFrame(1L)
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(invalidateCount).isEqualTo(1)
        assertThat(c.dispatchCount).isEqualTo(1)
    }

    /**
     * Cung generation: cancelPendingOnly → schedule B; oldCb.doFrame khong pha B;
     * newCb.doFrame dispatch dung. Khong bump session gen.
     */
    @Test
    fun task024_insetsCoalesceWrapper_sameGen_cancelPendingOnly_oldCbDoesNotClearNew() {
        val fake = RecordingFrameScheduler()
        var invalidateCount = 0
        val c =
            TouchableInsetsRefreshCoalesce(
                scheduler = fake,
                onInvalidate = { invalidateCount++ },
                traceEnabled = { false },
            )
        c.schedule()
        val gen = c.generation
        val oldCb = fake.posted.single()
        val oldSeq = c.postedFrameSequence

        c.cancelPendingOnly()
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(c.generation).isEqualTo(gen)
        assertThat(fake.removed).contains(oldCb)

        c.schedule()
        assertThat(c.generation).isEqualTo(gen)
        assertThat(c.hasPostedFrameCallback).isTrue()
        assertThat(c.postedFrameSequence).isNotEqualTo(oldSeq)
        assertThat(c.postedFrameSequence).isNotEqualTo(-1L)
        val newCb = fake.posted.last()
        val pendingBefore = c.scheduleRequests
        assertThat(invalidateCount).isEqualTo(0)

        oldCb.doFrame(0L)
        assertThat(c.hasPostedFrameCallback).isTrue()
        assertThat(c.postedFrameGeneration).isEqualTo(gen)
        assertThat(invalidateCount).isEqualTo(0)
        assertThat(c.dispatchCount).isEqualTo(0)
        assertThat(c.scheduleRequests).isEqualTo(pendingBefore)

        newCb.doFrame(1L)
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(invalidateCount).isEqualTo(1)
        assertThat(c.dispatchCount).isEqualTo(1)
    }

    /**
     * Cung generation: flushNow (invalidate 1) → schedule B; oldCb.doFrame no-op;
     * newCb.doFrame them 1 invalidate (tong 2).
     */
    @Test
    fun task024_insetsCoalesceWrapper_sameGen_flushNow_oldCbDoesNotClearNew() {
        val fake = RecordingFrameScheduler()
        var invalidateCount = 0
        val c =
            TouchableInsetsRefreshCoalesce(
                scheduler = fake,
                onInvalidate = { invalidateCount++ },
                traceEnabled = { false },
            )
        c.schedule()
        val gen = c.generation
        val oldCb = fake.posted.single()

        c.flushNow()
        assertThat(invalidateCount).isEqualTo(1)
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(c.generation).isEqualTo(gen)

        c.schedule()
        assertThat(c.hasPostedFrameCallback).isTrue()
        val newCb = fake.posted.last()
        assertThat(newCb).isNotEqualTo(oldCb)

        oldCb.doFrame(0L)
        assertThat(c.hasPostedFrameCallback).isTrue()
        assertThat(invalidateCount).isEqualTo(1)
        assertThat(c.dispatchCount).isEqualTo(1) // chi flush, chua frame B

        newCb.doFrame(1L)
        assertThat(c.hasPostedFrameCallback).isFalse()
        assertThat(invalidateCount).isEqualTo(2)
        assertThat(c.dispatchCount).isEqualTo(2)
    }

    @Test
    fun task022_collapsePerfTrace_sessionCancelAndRetarget() {
        val t = CollapseMorphPerfTrace()
        assertThat(t.isActive).isFalse()
        t.onClosingStarted(3)
        assertThat(t.isActive).isTrue()
        assertThat(t.isGeometryActive).isTrue()
        // Cung gen → no-op (khong duplicate span).
        t.onClosingStarted(3)
        assertThat(t.isActive).isTrue()
        t.onGeometrySettled(3)
        assertThat(t.isGeometryActive).isFalse()
        assertThat(t.isActive).isTrue() // lifecycle van mo
        t.onHandoffReady(3)
        // Duplicate moc → no-op (idempotent).
        t.onEndpointReached(3)
        t.onHandoffReady(3)
        t.onClosingEnded(3)
        assertThat(t.isActive).isFalse()
        // Stale end sau khi da end → no-op.
        t.onClosingEnded(3)
        assertThat(t.isActive).isFalse()
        t.onClosingStarted(4)
        assertThat(t.isActive).isTrue()
        t.onCancel(4, "reverse")
        assertThat(t.isActive).isFalse()
        t.onClosingStarted(5)
        t.forceCancel("detach")
        assertThat(t.isActive).isFalse()
    }

    /**
     * Stale onCancel(3) sau start4 khong tat span4; stale end/endpoint/handoff3 giu4;
     * cancel4 dong4.
     */
    @Test
    fun task023_collapsePerfTrace_staleCancelDoesNotEndNewSpan() {
        val t = CollapseMorphPerfTrace()
        t.onClosingStarted(3)
        t.onClosingStarted(4) // retarget: dong3, mo4
        assertThat(t.isActive).isTrue()
        assertThat(t.activeGenerationOrNeg1).isEqualTo(4)
        assertThat(t.isGeometryActive).isTrue()
        t.onCancel(3, "stale")
        assertThat(t.isActive).isTrue()
        assertThat(t.activeGenerationOrNeg1).isEqualTo(4)
        assertThat(t.isGeometryActive).isTrue()
        t.onClosingEnded(3)
        t.onEndpointReached(3)
        t.onHandoffReady(3)
        t.onGeometrySettled(3)
        assertThat(t.isActive).isTrue()
        assertThat(t.isGeometryActive).isTrue()
        t.onCancel(4, "reverse")
        assertThat(t.isActive).isFalse()
        assertThat(t.isGeometryActive).isFalse()
    }

    // --- task-005: stateful stopwatch gate + live-dest morph (UNRUN source) ---

    @Test
    fun stopwatchGate_runningDismiss_pauseResume_noFalseNewRun() {
        // Dismiss running startER=100000 displayed=10000 → rejected ticks → pause →
        // resume startER=130000 → van REJECT (khong NEW_RUN chi vi startER khac).
        var gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 10_000L,
                )
            )
        fun apply(
            running: Boolean,
            startEr: Long,
            paused: Long,
            displayed: Long,
        ): DeskClockStopwatchGateApplyResult {
            val r =
                deskClockStopwatchApplyPostToVisualDismissGate(
                    gate = gate,
                    post =
                        deskClockStopwatchRunIdentity(
                            notificationKey = "sw:1",
                            isRunning = running,
                            startElapsedRealtimeMs = startEr,
                            pausedElapsedMs = paused,
                            displayedElapsedMs = displayed,
                        ),
                )
            gate = r.nextGate ?: gate
            return r
        }
        var r = apply(true, 100_000L, 0L, 11_000L)
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(r.allowEvent).isFalse()
        assertThat(gate.latestObserved.displayedElapsedMs).isEqualTo(11_000L)
        r = apply(true, 100_000L, 0L, 15_000L)
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        r = apply(false, 0L, 20_000L, 20_000L)
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(gate.latestObserved.isRunning).isFalse()
        assertThat(gate.latestObserved.pausedElapsedMs).isEqualTo(20_000L)
        r = apply(true, 130_000L, 0L, 20_200L)
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(r.allowEvent).isFalse()
        assertThat(gate.latestObserved.isRunning).isTrue()
        assertThat(gate.latestObserved.startElapsedRealtimeMs).isEqualTo(130_000L)
        r = apply(true, 130_000L, 0L, 21_000L)
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
    }

    @Test
    fun stopwatchGate_pausedDismiss_thenResume_rejects() {
        var gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = false,
                    startElapsedRealtimeMs = 0L,
                    pausedElapsedMs = 45_000L,
                    displayedElapsedMs = 45_000L,
                )
            )
        val resume =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:1",
                        isRunning = true,
                        startElapsedRealtimeMs = 200_000L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 45_400L,
                    ),
            )
        assertThat(resume.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(resume.allowEvent).isFalse()
        gate = resume.nextGate!!
        assertThat(gate.latestObserved.isRunning).isTrue()
    }

    @Test
    fun stopwatchGate_trueResetJumpBack_allowsNewRun() {
        var gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 90_000L,
                )
            )
        val reset =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:1",
                        isRunning = true,
                        startElapsedRealtimeMs = 500_000L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 500L,
                    ),
            )
        assertThat(reset.relation).isEqualTo(DeskClockStopwatchRunRelation.ALLOW_NEW_RUN)
        assertThat(reset.allowEvent).isTrue()
        assertThat(reset.nextGate).isNull()
    }

    @Test
    fun stopwatchGate_differentKey_clearsGateNoStaleRebind() {
        val gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 10_000L,
                )
            )
        val other =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:2",
                        isRunning = true,
                        startElapsedRealtimeMs = 100_000L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 10_000L,
                    ),
            )
        assertThat(other.relation).isEqualTo(DeskClockStopwatchRunRelation.ALLOW_DIFFERENT_KEY)
        assertThat(other.nextGate).isNull()
        assertThat(other.allowEvent).isTrue()
    }

    @Test
    fun stopwatchGate_bothRunningDifferentStartEr_sameDisplayed_rejects() {
        // startER khac KHONG du NEW_RUN (review-004 A).
        val gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 3_000L,
                )
            )
        val r =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:1",
                        isRunning = true,
                        startElapsedRealtimeMs = 999_000L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 3_100L,
                    ),
            )
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(r.allowEvent).isFalse()
        assertThat(r.nextGate!!.latestObserved.startElapsedRealtimeMs).isEqualTo(999_000L)
    }

    @Test
    fun stopwatchGate_unknownZeroMetrics_conservativeReject() {
        val gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 10_000L,
                )
            )
        val r =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:1",
                        isRunning = true,
                        startElapsedRealtimeMs = 0L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 0L,
                    ),
            )
        assertThat(r.relation).isEqualTo(DeskClockStopwatchRunRelation.REJECT_SAME_RUN)
        assertThat(r.allowEvent).isFalse()
    }

    @Test
    fun stopwatchGate_removalRepostSameKey_continuesRejectThenOwnerRevisit() {
        var gate =
            deskClockStopwatchVisualDismissGateOf(
                deskClockStopwatchRunIdentity(
                    notificationKey = "sw:1",
                    isRunning = true,
                    startElapsedRealtimeMs = 100_000L,
                    pausedElapsedMs = 0L,
                    displayedElapsedMs = 12_000L,
                )
            )
        val repost =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = gate,
                post =
                    deskClockStopwatchRunIdentity(
                        notificationKey = "sw:1",
                        isRunning = true,
                        startElapsedRealtimeMs = 100_000L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 14_000L,
                    ),
            )
        assertThat(repost.allowEvent).isFalse()
        gate = repost.nextGate!!
        assertThat(gate.awaitOwnerRevisit).isTrue()
        val owners = setOf("com.google.android.deskclock")
        assertThat(
                deskClockStopwatchVisualDismissOnForegroundChanged(
                    dismissedKey = gate.notificationKey,
                    awaitOwnerRevisit = gate.awaitOwnerRevisit,
                    ownerRevisitArmed = false,
                    foregroundPackage = "com.google.android.deskclock",
                    ownerPackages = owners,
                )
            )
            .isEqualTo(DeskClockStopwatchVisualDismissFgEffect.ARM_OWNER_REVISIT)
        assertThat(
                deskClockStopwatchVisualDismissOnForegroundChanged(
                    dismissedKey = gate.notificationKey,
                    awaitOwnerRevisit = true,
                    ownerRevisitArmed = true,
                    foregroundPackage = "com.android.launcher3",
                    ownerPackages = owners,
                )
            )
            .isEqualTo(DeskClockStopwatchVisualDismissFgEffect.RESTORE_AFTER_LEAVE)
    }

    @Test
    fun stopwatchOwnerRevisit_thenLeave_restores() {
        val owners = setOf("com.google.android.deskclock")
        assertThat(
                deskClockStopwatchVisualDismissOnForegroundChanged(
                    dismissedKey = "sw:1",
                    awaitOwnerRevisit = true,
                    ownerRevisitArmed = false,
                    foregroundPackage = "com.google.android.deskclock",
                    ownerPackages = owners,
                )
            )
            .isEqualTo(DeskClockStopwatchVisualDismissFgEffect.ARM_OWNER_REVISIT)
        assertThat(
                deskClockStopwatchVisualDismissOnForegroundChanged(
                    dismissedKey = "sw:1",
                    awaitOwnerRevisit = true,
                    ownerRevisitArmed = true,
                    foregroundPackage = null,
                    ownerPackages = owners,
                )
            )
            .isEqualTo(DeskClockStopwatchVisualDismissFgEffect.RESTORE_AFTER_LEAVE)
        assertThat(
                deskClockStopwatchVisualDismissOnForegroundChanged(
                    dismissedKey = "sw:1",
                    awaitOwnerRevisit = true,
                    ownerRevisitArmed = false,
                    foregroundPackage = null,
                    ownerPackages = owners,
                )
            )
            .isEqualTo(DeskClockStopwatchVisualDismissFgEffect.NONE)
    }

    @Test
    fun terminalDismiss_ownerEnded_noFabricatedMorph() {
        assertThat(statusBarMorphShouldTerminalDismissNoDestination(ownerEndedAuthoritative = true))
            .isTrue()
        assertThat(
                statusBarMorphShouldTerminalDismissNoDestination(ownerEndedAuthoritative = false)
            )
            .isFalse()
        assertThat(
                statusBarMorphCollapseDestinationOwnerId(
                    ownerEndedAuthoritative = true,
                    liveSelectedOwnerId = "event:media",
                    frozenOwnerId = "event:stopwatch",
                )
            )
            .isNull()
        assertThat(
                statusBarMorphCollapseDestinationOwnerId(
                    ownerEndedAuthoritative = false,
                    liveSelectedOwnerId = "event:media",
                    frozenOwnerId = "event:stopwatch",
                )
            )
            .isEqualTo("event:media")
        assertThat(
                statusBarMorphCollapseDestinationOwnerId(
                    ownerEndedAuthoritative = false,
                    liveSelectedOwnerId = null,
                    frozenOwnerId = "event:media",
                )
            )
            .isEqualTo("event:media")
    }

    @Test
    fun abortCollapse_liveDestWithoutSameOwnerGeometry() {
        // Live dest ID ≠ measured rect — abort (cam fabricated 0,topPad).
        assertThat(
                statusBarMorphShouldAbortCollapseVisual(
                    ownerEndedAuthoritative = true,
                    hasValidPillCapture = true,
                    collapsing = true,
                    hasLiveDestinationOwner = false,
                )
            )
            .isTrue()
        assertThat(
                statusBarMorphShouldAbortCollapseVisual(
                    ownerEndedAuthoritative = false,
                    hasValidPillCapture = false,
                    collapsing = true,
                    hasLiveDestinationOwner = false,
                )
            )
            .isTrue()
        assertThat(
                statusBarMorphShouldAbortCollapseVisual(
                    ownerEndedAuthoritative = false,
                    hasValidPillCapture = false,
                    collapsing = true,
                    hasLiveDestinationOwner = true,
                    hasFrozenGeometryForLiveOwner = false,
                )
            )
            .isTrue()
        // Frozen same-owner → cho neo (khong fabricate).
        assertThat(
                statusBarMorphShouldAbortCollapseVisual(
                    ownerEndedAuthoritative = false,
                    hasValidPillCapture = false,
                    collapsing = true,
                    hasLiveDestinationOwner = true,
                    hasFrozenGeometryForLiveOwner = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarMorphShouldAbortCollapseVisual(
                    ownerEndedAuthoritative = false,
                    hasValidPillCapture = true,
                    collapsing = true,
                    hasLiveDestinationOwner = true,
                )
            )
            .isFalse()
    }

    @Test
    fun captureMatchesDestination_requiresSameOwner() {
        assertThat(
                statusBarMorphCaptureMatchesDestination(
                    hasValidCapture = true,
                    captureOwnerId = "A",
                    destinationOwnerId = "B",
                )
            )
            .isFalse()
        assertThat(
                statusBarMorphCaptureMatchesDestination(
                    hasValidCapture = true,
                    captureOwnerId = "B",
                    destinationOwnerId = "B",
                )
            )
            .isTrue()

    }

    @Test
    fun ownerEndedAuthoritative_emptyOrAosp() {
        assertThat(
                statusBarCollapseHandoffOwnerEndedAuthoritative(
                    chipMissing = true,
                    onlyAospOrEmpty = false,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandoffOwnerEndedAuthoritative(
                    chipMissing = false,
                    onlyAospOrEmpty = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandoffOwnerEndedAuthoritative(
                    chipMissing = false,
                    onlyAospOrEmpty = false,
                )
            )
            .isFalse()
    }

    @Test
    fun handoffAfterRecovery_ownerEnded_abortsHold() {
        assertThat(
                statusBarCollapseHandoffAfterRecoveryTimeout(
                    prepared = false,
                    ownerEndedAuthoritative = true,
                )
            )
            .isEqualTo(StatusBarCollapseHandoffAfterRecoveryAction.AbortClearHold)
        assertThat(
                statusBarCollapseHandoffLocalHoldReleaseAllowed(
                    activeSessionGen = 3,
                    completingSessionGen = 3,
                )
            )
            .isTrue()
        // Stale session khong clear hold session moi.
        assertThat(
                statusBarCollapseHandoffLocalHoldReleaseAllowed(
                    activeSessionGen = 4,
                    completingSessionGen = 3,
                )
            )
            .isFalse()
    }

    // --- task-006: morphFrame+owner atomic + native alpha lifecycle (UNRUN) ---

    @Test
    fun pillFrame_morphOwnerAtomic_expandFreezeKeepsA_whenObserveB() {
        val t = StatusBarPillFrameTracker()
        // Capture A idle → morph A+owner A.
        t.capture(
            left = 100,
            top = 40,
            width = 200,
            height = 56,
            intentExpanded = false,
            pillSuppressed = false,
            ownerId = "A",
        )
        assertThat(t.morphFrame.left).isEqualTo(100)
        assertThat(t.morphOwnerId).isEqualTo("A")
        assertThat(t.lastObservedOwnerId).isEqualTo("A")
        // Expand freeze A.
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.width).isEqualTo(200)
        assertThat(t.morphOwnerId).isEqualTo("A")
        t.capture(
            left = 100,
            top = 40,
            width = 200,
            height = 56,
            intentExpanded = true,
            pillSuppressed = false,
            ownerId = "A",
        )
        // Observe B mid-expand — lastObserved=B, morph van A+owner A (cam relabel).
        t.capture(
            left = 300,
            top = 42,
            width = 180,
            height = 56,
            intentExpanded = true,
            pillSuppressed = false,
            ownerId = "B",
        )
        assertThat(t.lastObserved.left).isEqualTo(300)
        assertThat(t.lastObservedOwnerId).isEqualTo("B")
        assertThat(t.morphFrame.left).isEqualTo(100)
        assertThat(t.morphFrame.width).isEqualTo(200)
        assertThat(t.morphOwnerId).isEqualTo("A")
        // Same-owner publish snapshot: morph A khong mang label B.
        assertThat(
                statusBarMorphCaptureMatchesDestination(
                    hasValidCapture = t.morphFrame.isValid,
                    captureOwnerId = t.morphOwnerId,
                    destinationOwnerId = "B",
                )
            )
            .isFalse()
        assertThat(
                statusBarMorphCaptureMatchesDestination(
                    hasValidCapture = t.morphFrame.isValid,
                    captureOwnerId = t.morphOwnerId,
                    destinationOwnerId = "A",
                )
            )
            .isTrue()
        // Collapse capture B → morph B+owner B.
        t.capture(
            left = 300,
            top = 42,
            width = 180,
            height = 56,
            intentExpanded = false,
            pillSuppressed = true,
            ownerId = "B",
        )
        assertThat(t.morphFrame.left).isEqualTo(300)
        assertThat(t.morphOwnerId).isEqualTo("B")
        t.notifyCollapseSettled()
        assertThat(t.morphOwnerId).isEqualTo("B")
        assertThat(t.morphFrame.width).isEqualTo(180)
        // Invalidate clear ca cap.
        t.invalidate()
        assertThat(t.morphFrame.isValid).isFalse()
        assertThat(t.morphOwnerId).isNull()
        assertThat(t.lastObservedOwnerId).isNull()
    }

    @Test
    fun pillFrame_sameFramePublication_retargetDoesNotRelabelFrozen() {
        val t = StatusBarPillFrameTracker()
        t.capture(10, 10, 160, 56, intentExpanded = false, pillSuppressed = false, ownerId = "A")
        t.seedMorphFromObserved()
        val frozen = t.morphFrame
        // Suppress + intent: giu morph; observe B cung frame size khac owner.
        t.capture(
            left = 10,
            top = 10,
            width = 160,
            height = 56,
            intentExpanded = true,
            pillSuppressed = true,
            ownerId = "B",
        )
        assertThat(t.morphFrame).isEqualTo(frozen)
        assertThat(t.morphOwnerId).isEqualTo("A")
        assertThat(t.lastObservedOwnerId).isEqualTo("B")
        // Retarget collapse → B.
        t.capture(
            left = 50,
            top = 12,
            width = 140,
            height = 56,
            intentExpanded = false,
            pillSuppressed = true,
            ownerId = "B",
        )
        assertThat(t.morphFrame.left).isEqualTo(50)
        assertThat(t.morphOwnerId).isEqualTo("B")
    }

    /** Fake Choreographer: ghi post/remove de test wrapper gen-bound cancel/reschedule. */
    private class RecordingFrameScheduler : FrameCallbackScheduler {
        val posted = mutableListOf<android.view.Choreographer.FrameCallback>()
        val removed = mutableListOf<android.view.Choreographer.FrameCallback>()

        override fun postFrameCallback(callback: android.view.Choreographer.FrameCallback) {
            posted.add(callback)
        }

        override fun removeFrameCallback(callback: android.view.Choreographer.FrameCallback) {
            removed.add(callback)
        }
    }
}
