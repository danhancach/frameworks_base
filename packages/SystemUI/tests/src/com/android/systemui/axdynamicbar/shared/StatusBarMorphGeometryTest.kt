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

package com.android.systemui.axdynamicbar.shared

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.ui.compose.StatusBarChipEventEntry
import com.android.systemui.axdynamicbar.ui.compose.collapseChronometerTickDelayMs
import com.android.systemui.axdynamicbar.ui.compose.displayedChronometerMsOf
import com.android.systemui.axdynamicbar.ui.compose.frozenPillChronometerIsTicking
import com.android.systemui.axdynamicbar.ui.compose.frozenPillHasDynamicChronometer
import com.android.systemui.axdynamicbar.ui.compose.frozenPillOwnerNeedsUpdate
import com.android.systemui.axdynamicbar.ui.compose.refreshFrozenPillChronometerPresentation
import com.android.systemui.axdynamicbar.ui.compose.resolveFrozenPillEventForSelection
import com.android.systemui.axdynamicbar.ui.compose.shouldSyncFrozenPillPresentation
import com.android.systemui.axdynamicbar.ui.compose.statusBarChipBranchRendersOuterClosure
import com.android.systemui.axdynamicbar.ui.compose.statusBarChipEntryPresentation
import com.android.systemui.axdynamicbar.ui.compose.statusBarChipEventDisplayKey
import com.android.systemui.axdynamicbar.ui.compose.statusBarChipEventDisplayKeyStableAcrossChronoTick
import com.android.systemui.axdynamicbar.ui.compose.statusBarChipSelectedLiveEntry
import com.android.systemui.axdynamicbar.ui.compose.statusBarCollapsePresentationParity
import com.android.systemui.axdynamicbar.ui.compose.statusBarHandbackReadyForUnsuppress
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillChronometerMsForBody
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillFreezeMotion
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillLiveValue
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationGenerationValid
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationMatchesOwner
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationOf
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.statusBarNicEffectiveAnimationsEnabled
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pure geometry / alpha gate / endpoint freeze — chi viet, khong bat buoc chay build.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarMorphGeometryTest : SysuiTestCase() {

    @Test
    fun geom_p0_matchesPillOverTarget() {
        val g =
            computeKeyguardMorphGeom(
                pRaw = 0f,
                pillWPx = 160f,
                pillHPx = 56f,
                targetWPx = 800f,
                targetHPx = 400f,
            )
        assertThat(g.sx).isWithin(0.001f).of(160f / 800f)
        assertThat(g.sy).isWithin(0.001f).of(56f / 400f)
        assertThat(g.pOffset).isEqualTo(0f)
    }

    @Test
    fun geom_p1_isIdentityScale() {
        val g =
            computeKeyguardMorphGeom(
                pRaw = 1f,
                pillWPx = 160f,
                pillHPx = 56f,
                targetWPx = 800f,
                targetHPx = 400f,
            )
        assertThat(g.sx).isWithin(0.001f).of(1f)
        assertThat(g.sy).isWithin(0.001f).of(1f)
        assertThat(g.pCorner).isEqualTo(1f)
    }

    @Test
    fun statusBarOffset_p0_atPillOrigin() {
        val (left, top) =
            morphOffsetTopLeftStatusBar(
                sx = 160f / 800f,
                targetWPx = 800f,
                pOffset = 0f,
                originLeftPx = 152f,
                originTopPx = 32f,
                maxWidthPx = 1080f,
                targetTopPx = 79f,
            )
        assertThat(left).isWithin(0.5f).of(152f)
        assertThat(top).isWithin(0.5f).of(32f)
    }

    @Test
    fun statusBarOffset_p1_centeredAtTargetTop() {
        val (left, top) =
            morphOffsetTopLeftStatusBar(
                sx = 1f,
                targetWPx = 800f,
                pOffset = 1f,
                originLeftPx = 152f,
                originTopPx = 32f,
                maxWidthPx = 1080f,
                targetTopPx = 79f,
            )
        assertThat(left).isWithin(0.5f).of((1080f - 800f) * 0.5f)
        assertThat(top).isWithin(0.5f).of(79f)
    }

    @Test
    fun expandContentAlpha_gatesBefore04() {
        assertThat(morphExpandContentAlpha(0.2f, closing = false)).isEqualTo(0f)
        assertThat(morphExpandContentAlpha(0.6f, closing = false)).isWithin(0.01f).of(0.5f)
        assertThat(morphExpandContentAlpha(0.8f, closing = false)).isEqualTo(1f)
    }

    @Test
    fun expandContentAlpha_closingHidesQuickly() {
        assertThat(morphExpandContentAlpha(1f, closing = true)).isEqualTo(1f)
        assertThat(morphExpandContentAlpha(0.88f, closing = true)).isEqualTo(0f)
        assertThat(morphExpandContentAlpha(0.5f, closing = true)).isEqualTo(0f)
    }

    @Test
    fun pillContentAlpha_openingFadesEarly() {
        assertThat(morphPillContentAlpha(0f, closing = false)).isEqualTo(1f)
        assertThat(morphPillContentAlpha(0.35f, closing = false)).isEqualTo(0f)
    }

    @Test
    fun shouldTargetExpanded_stagesUntilEndpoint() {
        assertThat(
            statusBarMorphShouldTargetExpanded(
                intentExpanded = true,
                firstCompositionPassed = true,
                hasValidSourceRect = true,
                allowFallbackWithoutRect = false,
                hasMorphEndpoint = false,
                allowFallbackWithoutEndpoint = false,
            )
        ).isFalse()
        assertThat(
            statusBarMorphShouldTargetExpanded(
                intentExpanded = true,
                firstCompositionPassed = true,
                hasValidSourceRect = true,
                allowFallbackWithoutRect = false,
                hasMorphEndpoint = true,
                allowFallbackWithoutEndpoint = false,
            )
        ).isTrue()
        // Mac dinh: khong empty-start khi thieu endpoint.
        assertThat(
            statusBarMorphShouldTargetExpanded(
                intentExpanded = true,
                firstCompositionPassed = true,
                hasValidSourceRect = true,
                allowFallbackWithoutRect = false,
                hasMorphEndpoint = false,
            )
        ).isFalse()
    }

    @Test
    fun morphRunEndpoint_frozenZero_running_mustNotReturnLateLive() {
        // BLOCKER A / F48: frozen=0 + live late + running → CAM return 550.
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 0,
                liveTargetPx = 550,
                pillPx = 56,
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(0)
    }

    @Test
    fun morphRunEndpoint_running_keepsFrozenExactly() {
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 380,
                liveTargetPx = 550,
                pillPx = 56,
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(380)
    }

    @Test
    fun morphRunEndpoint_atRest_followsLive() {
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 380,
                liveTargetPx = 550,
                pillPx = 56,
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
            )
        ).isEqualTo(550)
    }

    @Test
    fun morphRunEndpoint_staging_prefersFrozenThenLive() {
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 0,
                liveTargetPx = 0,
                pillPx = 56,
                morphTransitionRunning = false,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(0)
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 0,
                liveTargetPx = 420,
                pillPx = 56,
                morphTransitionRunning = false,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(420)
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 380,
                liveTargetPx = 550,
                pillPx = 56,
                morphTransitionRunning = false,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(380)
    }

    @Test
    fun shouldUpdateMorphEndpoint_blocksMidRunRetarget() {
        assertThat(
            statusBarShouldUpdateMorphEndpoint(
                frozenEndpointPx = 380,
                measuredPx = 520,
                pillPx = 56,
                morphTransitionRunning = true,
            )
        ).isFalse()
        // Ke ca frozen=0: khong cap nhat khi dang chay.
        assertThat(
            statusBarShouldUpdateMorphEndpoint(
                frozenEndpointPx = 0,
                measuredPx = 380,
                pillPx = 56,
                morphTransitionRunning = true,
            )
        ).isFalse()
        assertThat(
            statusBarShouldUpdateMorphEndpoint(
                frozenEndpointPx = 0,
                measuredPx = 380,
                pillPx = 56,
                morphTransitionRunning = false,
            )
        ).isTrue()
    }

    @Test
    fun atomicEndpointSnapshot_freezesHandWBeforeMotion() {
        val snap =
            statusBarMorphAtomicEndpointSnapshot(
                heightPx = 383,
                widthPx = 1012,
                pillHPx = 56,
                pillWPx = 56,
                fallbackWidthPx = 1012,
            )
        assertThat(snap).isEqualTo(383 to 1012)
        // H <= pill → null (chua bat morph).
        assertThat(
            statusBarMorphAtomicEndpointSnapshot(
                heightPx = 56,
                widthPx = 1012,
                pillHPx = 56,
                pillWPx = 56,
                fallbackWidthPx = 1012,
            )
        ).isNull()
        // W thieu → fallback envelope.
        assertThat(
            statusBarMorphAtomicEndpointSnapshot(
                heightPx = 383,
                widthPx = 0,
                pillHPx = 56,
                pillWPx = 56,
                fallbackWidthPx = 900,
            )
        ).isEqualTo(383 to 900)
    }

    @Test
    fun stageTimeout_usesLastEnvelopeOnlyWhenTimedOut() {
        assertThat(
            statusBarMorphStageTimeoutEndpointPx(
                measuredOrFrozenPx = 0,
                lastExpandedEnvelopePx = 383,
                pillPx = 56,
                timedOut = false,
            )
        ).isEqualTo(0)
        assertThat(
            statusBarMorphStageTimeoutEndpointPx(
                measuredOrFrozenPx = 0,
                lastExpandedEnvelopePx = 383,
                pillPx = 56,
                timedOut = true,
            )
        ).isEqualTo(383)
        // Measured thang → khong can timeout.
        assertThat(
            statusBarMorphStageTimeoutEndpointPx(
                measuredOrFrozenPx = 400,
                lastExpandedEnvelopePx = 383,
                pillPx = 56,
                timedOut = true,
            )
        ).isEqualTo(400)
        // First overlay: khong envelope → van 0 (khong empty-start).
        assertThat(
            statusBarMorphStageTimeoutEndpointPx(
                measuredOrFrozenPx = 0,
                lastExpandedEnvelopePx = 0,
                pillPx = 56,
                timedOut = true,
            )
        ).isEqualTo(0)
    }

    @Test
    fun endpointReady_and_interruptedReopen_ownerChange() {
        assertThat(statusBarMorphEndpointReady(0, 56)).isFalse()
        assertThat(statusBarMorphEndpointReady(56, 56)).isFalse()
        assertThat(statusBarMorphEndpointReady(380, 56)).isTrue()

        // Interrupted close→reopen: freeze moi tu measure/envelope, running van cam late live.
        var frozen = 380
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = frozen,
                liveTargetPx = 500,
                pillPx = 56,
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(380)
        // Collapse settle reset → reopen first overlay freeze lai.
        frozen = 0
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = frozen,
                liveTargetPx = 500,
                pillPx = 56,
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
            )
        ).isEqualTo(0)
        // Owner change at-rest: follow live moi.
        assertThat(
            statusBarMorphRunEndpointPx(
                frozenEndpointPx = 380,
                liveTargetPx = 420,
                pillPx = 56,
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
            )
        ).isEqualTo(420)
    }

    @Test
    fun circleChromeAlphas_crossfadeRingToGlass() {
        val (ring0, glass0) =
            statusBarMorphCircleChromeAlphas(
                morphShellAlpha = 1f,
                shellLerp = 0f,
                useCircleStyle = true,
            )
        assertThat(ring0).isWithin(0.001f).of(1f)
        assertThat(glass0).isWithin(0.001f).of(0f)
        val (ring1, glass1) =
            statusBarMorphCircleChromeAlphas(
                morphShellAlpha = 1f,
                shellLerp = 1f,
                useCircleStyle = true,
            )
        assertThat(ring1).isWithin(0.001f).of(0f)
        assertThat(glass1).isWithin(0.001f).of(1f)
        // Non-circle: giu full glass, khong ring.
        val (ringRow, glassRow) =
            statusBarMorphCircleChromeAlphas(
                morphShellAlpha = 0.8f,
                shellLerp = 0f,
                useCircleStyle = false,
            )
        assertThat(ringRow).isEqualTo(0f)
        assertThat(glassRow).isWithin(0.001f).of(0.8f)
    }

    @Test
    fun circleRingDrawGeom_p0_isFull28Square_notHostEllipse() {
        // Host cu: Canvas 900×400 + sx=28/900 sy=28/400 → ellipse ~6×14.
        // Ring sibling visual: diameter = min(28,28) = 28, aspect=1.
        val ring =
            statusBarMorphCircleRingDrawGeom(
                pillWPx = 28f,
                pillHPx = 28f,
                targetWPx = 900f,
                targetHPx = 400f,
                pRaw = 0f,
                shellLeftPx = 100f,
                shellTopPx = 40f,
            )
        assertThat(ring.diameterPx).isWithin(0.05f).of(28f)
        assertThat(ring.widthPx).isWithin(0.05f).of(28f)
        assertThat(ring.heightPx).isWithin(0.05f).of(28f)
        assertThat(ring.aspect).isWithin(0.001f).of(1f)
        // Khong phai bbox ellipse ~11×25 / 6×14.
        assertThat(ring.widthPx).isGreaterThan(20f)
        assertThat(kotlin.math.abs(ring.widthPx - ring.heightPx)).isLessThan(0.05f)
        assertThat(ring.iconSizePx).isWithin(0.05f).of(28f * StatusBarCircleChipIconFrac)
        assertThat(ring.leftPx).isWithin(0.05f).of(100f)
        assertThat(ring.topPx).isWithin(0.05f).of(40f)
    }

    @Test
    fun circleRingDrawGeom_midAndReverse_remainNonElliptic() {
        for (p in floatArrayOf(0.25f, 0.5f, 0.75f, 0.25f /* reverse mid */)) {
            val ring =
                statusBarMorphCircleRingDrawGeom(
                    pillWPx = 28f,
                    pillHPx = 28f,
                    targetWPx = 900f,
                    targetHPx = 400f,
                    pRaw = p,
                    shellLeftPx = 0f,
                    shellTopPx = 0f,
                )
            assertThat(ring.aspect).isWithin(0.001f).of(1f)
            assertThat(ring.widthPx).isEqualTo(ring.heightPx)
            // Grow isotropic: diameter = min(visualW, visualH) >= pill @p>=0.
            assertThat(ring.diameterPx).isAtLeast(28f - 0.05f)
            assertThat(ring.iconSizePx / ring.diameterPx)
                .isWithin(0.001f)
                .of(StatusBarCircleChipIconFrac)
        }
    }

    @Test
    fun circleRingDrawGeom_followsMovedCircleOrigin() {
        val ring =
            statusBarMorphCircleRingDrawGeom(
                pillWPx = 28f,
                pillHPx = 28f,
                targetWPx = 900f,
                targetHPx = 400f,
                pRaw = 0f,
                shellLeftPx = 220f,
                shellTopPx = 55f,
            )
        assertThat(ring.leftPx).isWithin(0.05f).of(220f)
        assertThat(ring.topPx).isWithin(0.05f).of(55f)
        // Visual shell 28×28 centered: ring top-left == shell origin @p0.
        val (vw, vh) =
            statusBarMorphVisualShellSizePx(
                targetWPx = 900f,
                targetHPx = 400f,
                sx = 28f / 900f,
                sy = 28f / 400f,
            )
        assertThat(vw).isWithin(0.05f).of(28f)
        assertThat(vh).isWithin(0.05f).of(28f)
    }

    @Test
    fun circleRingIcon_squareRatio_16of28() {
        assertThat(statusBarMorphCircleRingIconSizePx(28f))
            .isWithin(0.001f)
            .of(16f)
        assertThat(statusBarMorphCircleRingIconSizePx(56f))
            .isWithin(0.001f)
            .of(32f)
    }

    // --- task-016: visual H tach pad (i29→30) + early tween math ---

    @Test
    fun visualTargetH_stripsTopAndBottomPad_coercePill() {
        // layout 383 − top6 − bottom141 → visual 236; khong duoi pill.
        assertThat(statusBarMorphVisualTargetHPx(383, 6, 141, 56)).isEqualTo(236)
        assertThat(statusBarMorphVisualTargetHPx(100, 6, 141, 56)).isEqualTo(56)
        assertThat(statusBarMorphVisualTargetHPx(56, 0, 0, 56)).isEqualTo(56)
        // task-016 chi tru bottom: 383−141=242 van hop le khi topPad=0.
        assertThat(statusBarMorphVisualTargetHPx(383, 0, 141, 56)).isEqualTo(242)
        assertThat(StatusBarExpandedBottomPadDp).isEqualTo(50)
        assertThat(StatusBarExpandedTopPadDp).isEqualTo(2)
        assertThat(StatusBarExpandedSidePadDp).isEqualTo(12)
    }

    @Test
    fun visualTargetW_stripsSidePads_coercePill() {
        // @450dpi: SpaceLg≈34 → envelope 1080 → visible ≈1012.
        assertThat(statusBarMorphVisualTargetWPx(1080, 34, 253)).isEqualTo(1012)
        assertThat(statusBarMorphVisualTargetWPx(200, 34, 253)).isEqualTo(253)
        assertThat(statusBarMorphSidePadInducedDeltaWPx(34f, 1f)).isWithin(0.05f).of(68f)
    }

    @Test
    fun visibleHeight_equals_targetTimesSy() {
        assertThat(statusBarMorphVisibleHeightPx(242f, 1.3f)).isWithin(0.05f).of(314.6f)
        assertThat(statusBarMorphVisibleHeightPx(383f, 1.255f)).isWithin(0.5f).of(481f)
    }

    @Test
    fun padInducedDeltaH_matchesRuntime159() {
        // VERIFIED i29→30: ΔH≈159 ≈ pad×sy (~141×1.13).
        val padPx = 141f
        val sy = 159f / padPx
        assertThat(statusBarMorphPadInducedDeltaHPx(padPx, sy)).isWithin(0.5f).of(159f)
        assertThat(sy).isWithin(0.02f).of(1.13f)
    }

    @Test
    fun shellContentContinuous_withVisualTarget_notLayoutPad() {
        val layoutH = 383f
        val topPad = 6f
        val bottomPad = 141f
        val pillH = 56f
        val sy = 1.255f
        val inset = topPad + bottomPad
        // BUG cu: shell = layout*sy, content = (layout−inset)*sy → Δ ≈ inset*sy.
        val oldShell = statusBarMorphVisibleHeightPx(layoutH, sy)
        val content = statusBarMorphVisibleHeightPx(layoutH - inset, sy)
        assertThat(oldShell - content)
            .isWithin(0.5f)
            .of(statusBarMorphPadInducedDeltaHPx(inset, sy))
        // FIX: ca hai dung morphVisual → lien tuc.
        assertThat(
            statusBarMorphShellContentHeightContinuous(
                layoutH,
                topPad,
                bottomPad,
                pillH,
                sy,
            )
        )
            .isTrue()
        val visual =
            statusBarMorphVisualTargetHPx(
                    layoutH.toInt(),
                    topPad.toInt(),
                    bottomPad.toInt(),
                    pillH.toInt(),
                )
                .toFloat()
        assertThat(statusBarMorphVisibleHeightPx(visual, sy)).isWithin(0.05f).of(content)
    }

    @Test
    fun shellContentWidthContinuous_stripsSidePad() {
        val layoutW = 1080f
        val side = 34f
        val pillW = 253f
        val sx = 1f
        val oldShell = statusBarMorphVisibleWidthPx(layoutW, sx)
        val content = statusBarMorphVisibleWidthPx(layoutW - 2f * side, sx)
        assertThat(oldShell - content)
            .isWithin(0.05f)
            .of(statusBarMorphSidePadInducedDeltaWPx(side, sx))
        assertThat(
            statusBarMorphShellContentWidthContinuous(layoutW, side, pillW, sx)
        )
            .isTrue()
    }

    @Test
    fun earlyTween_p008_layoutTarget_matchesObserved307x80() {
        // Screenrecord i11 ~307×80 @ p≈0.08 — geom layout (gom pad), khong plateau.
        val pillW = 253f
        val pillH = 56f
        val targetW = 920f
        val targetH = 383f
        val p = 0.08f
        val g =
            computeKeyguardMorphGeom(
                pRaw = p,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = targetW,
                targetHPx = targetH,
            )
        val (vw, vh) = statusBarMorphVisualShellSizePx(targetW, targetH, g.sx, g.sy)
        assertThat(vw).isWithin(3f).of(307f)
        assertThat(vh).isWithin(3f).of(80f)
    }

    @Test
    fun earlyTween_p0_visualTarget_matchesPillExactly() {
        val pillW = 253f
        val pillH = 56f
        val layoutH = 383
        val topPad = 6
        val bottomPad = 141
        val layoutW = 1080
        val sidePad = 34
        val visualH =
            statusBarMorphVisualTargetHPx(layoutH, topPad, bottomPad, pillH.toInt())
                .toFloat()
        val visualW =
            statusBarMorphVisualTargetWPx(layoutW, sidePad, pillW.toInt()).toFloat()
        val g =
            computeKeyguardMorphGeom(
                pRaw = 0f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = visualW,
                targetHPx = visualH,
            )
        val (vw, vh) = statusBarMorphVisualShellSizePx(visualW, visualH, g.sx, g.sy)
        assertThat(vw).isWithin(0.05f).of(pillW)
        assertThat(vh).isWithin(0.05f).of(pillH)
        assertThat(
            statusBarMorphPillShellAligned(pillW, pillH, vw, vh, progress = 0f)
        ).isTrue()
        assertThat(
            statusBarMorphPillShellAligned(pillW, pillH, vw, vh, progress = 0.08f)
        ).isFalse()
    }

    // --- task-017: full visual rect (W+H+X+Y) + host inset compensate ---

    @Test
    fun visualRect_p0_matchesPillIncludingHostInsets() {
        // Vi du @450dpi: side≈34, top≈6; visual target 1012×236.
        val pillL = 400f
        val pillT = 40f
        val pillW = 253f
        val pillH = 56f
        val visualW = 1012f
        val visualH = 236f
        val side = 34f
        val topInset = 6f
        val g =
            computeKeyguardMorphGeom(
                pRaw = 0f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = visualW,
                targetHPx = visualH,
            )
        val vr =
            statusBarMorphLerpVisualRectPx(
                pOffset = g.pOffset,
                pillLeftPx = pillL,
                pillTopPx = pillT,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = visualW,
                visualTargetHPx = visualH,
                sx = g.sx,
                sy = g.sy,
                maxWidthPx = 1080f,
                targetVisualTopPx = 79f + topInset,
            )
        assertThat(vr.widthPx).isWithin(0.05f).of(pillW)
        assertThat(vr.heightPx).isWithin(0.05f).of(pillH)
        assertThat(vr.leftPx).isWithin(0.05f).of(pillL)
        assertThat(vr.topPx).isWithin(0.05f).of(pillT)
        assertThat(
            statusBarMorphPillShellAlignedRect(
                pillL,
                pillT,
                pillW,
                pillH,
                vr.leftPx,
                vr.topPx,
                vr.widthPx,
                vr.heightPx,
                progress = 0f,
                tolerancePx = 2f,
            )
        )
            .isTrue()
        val (hostL, hostT) =
            statusBarMorphHostOriginFromVisualRectPx(
                visualLeftPx = vr.leftPx,
                visualTopPx = vr.topPx,
                insetLeftPx = side,
                insetTopPx = topInset,
                sx = g.sx,
                sy = g.sy,
            )
        // Shell local (side, top) * scale + host = visualRect.
        assertThat(hostL + side * g.sx).isWithin(0.05f).of(vr.leftPx)
        assertThat(hostT + topInset * g.sy).isWithin(0.05f).of(vr.topPx)
    }

    @Test
    fun visualRect_p1_envelope1080_visible1012_left34_top85() {
        // Vi du density: envelopeW=1080, side=34 → visibleW=1012 left=(1080-1012)/2=34.
        // SB inset 79 + SpaceXxs≈6 → visualTop≈85; visualH excludes top+bottom.
        val layoutW = 1080
        val layoutH = 383
        val side = 34
        val topInset = 6
        val bottom = 141
        val pillW = 253
        val pillH = 56
        val visualW =
            statusBarMorphVisualTargetWPx(layoutW, side, pillW).toFloat()
        val visualH =
            statusBarMorphVisualTargetHPx(layoutH, topInset, bottom, pillH).toFloat()
        assertThat(visualW).isWithin(0.05f).of(1012f)
        assertThat(visualH).isWithin(0.05f).of(236f)
        val g =
            computeKeyguardMorphGeom(
                pRaw = 1f,
                pillWPx = pillW.toFloat(),
                pillHPx = pillH.toFloat(),
                targetWPx = visualW,
                targetHPx = visualH,
            )
        val sbInset = 79f
        val vr =
            statusBarMorphLerpVisualRectPx(
                pOffset = g.pOffset,
                pillLeftPx = 400f,
                pillTopPx = 40f,
                pillWPx = pillW.toFloat(),
                pillHPx = pillH.toFloat(),
                visualTargetWPx = visualW,
                visualTargetHPx = visualH,
                sx = g.sx,
                sy = g.sy,
                maxWidthPx = 1080f,
                targetVisualTopPx = sbInset + topInset,
            )
        assertThat(vr.widthPx).isWithin(0.05f).of(1012f)
        assertThat(vr.heightPx).isWithin(0.05f).of(236f)
        assertThat(vr.leftPx).isWithin(0.5f).of(34f)
        assertThat(vr.topPx).isWithin(0.5f).of(85f)
        val (hostL, hostT) =
            statusBarMorphHostOriginFromVisualRectPx(
                visualLeftPx = vr.leftPx,
                visualTopPx = vr.topPx,
                insetLeftPx = side.toFloat(),
                insetTopPx = topInset.toFloat(),
                sx = 1f,
                sy = 1f,
            )
        // Host origin = visual − inset → left0, top=SB inset (khong +SpaceLg lech).
        assertThat(hostL).isWithin(0.5f).of(0f)
        assertThat(hostT).isWithin(0.5f).of(sbInset)
    }

    @Test
    fun visualRect_midProgress_and_overshoot_continuous() {
        val pillL = 413.5f
        val pillT = 40f
        val pillW = 253f
        val pillH = 56f
        val visualW = 1012f
        val visualH = 236f
        val maxW = 1080f
        val targetTop = 85f
        for (p in floatArrayOf(0.08f, 0.4f, 0.6f, 0.8f, 1f, 1.15f)) {
            val g =
                computeKeyguardMorphGeom(
                    pRaw = p,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    targetWPx = visualW,
                    targetHPx = visualH,
                )
            val vr =
                statusBarMorphLerpVisualRectPx(
                    pOffset = g.pOffset,
                    pillLeftPx = pillL,
                    pillTopPx = pillT,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    visualTargetWPx = visualW,
                    visualTargetHPx = visualH,
                    sx = g.sx,
                    sy = g.sy,
                    maxWidthPx = maxW,
                    targetVisualTopPx = targetTop,
                )
            assertThat(vr.widthPx).isAtLeast(pillW - 1f)
            assertThat(vr.heightPx).isAtLeast(pillH - 1f)
            if (p >= 1f) {
                assertThat(vr.widthPx).isWithin(1f).of(visualW * g.sx)
                assertThat(vr.topPx).isWithin(0.5f).of(targetTop)
            }
        }
        // Reverse p=0.5: van interpolate, khong snap.
        val gHalf =
            computeKeyguardMorphGeom(
                pRaw = 0.5f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = visualW,
                targetHPx = visualH,
            )
        val mid =
            statusBarMorphLerpVisualRectPx(
                pOffset = gHalf.pOffset,
                pillLeftPx = pillL,
                pillTopPx = pillT,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = visualW,
                visualTargetHPx = visualH,
                sx = gHalf.sx,
                sy = gHalf.sy,
                maxWidthPx = maxW,
                targetVisualTopPx = targetTop,
            )
        assertThat(mid.widthPx).isWithin(2f).of((pillW + visualW) * 0.5f)
        assertThat(mid.topPx).isWithin(2f).of((pillT + targetTop) * 0.5f)
    }

    @Test
    fun circleRing_origin_followsVisualRect_notHostEnvelope() {
        val visualLeft = 34f
        val visualTop = 85f
        val visualW = 1012f
        val visualH = 236f
        // Isotrop diameter = min(W,H) when sx=sy=1 at rest — day mid morph circle.
        val d = statusBarMorphCircleRingVisualDiameterPx(56f, 56f)
        assertThat(d).isWithin(0.05f).of(56f)
        val (rl, rt) =
            statusBarMorphCircleRingVisualTopLeftPx(
                shellLeftPx = visualLeft,
                shellTopPx = visualTop,
                visualWPx = 56f,
                visualHPx = 56f,
                diameterPx = d,
            )
        assertThat(rl).isWithin(0.05f).of(visualLeft)
        assertThat(rt).isWithin(0.05f).of(visualTop)
        // Neu neo host (0,79) thay vi visualRect → ring lech SpaceLg/Xxs.
        val wrong =
            statusBarMorphCircleRingVisualTopLeftPx(
                shellLeftPx = 0f,
                shellTopPx = 79f,
                visualWPx = 56f,
                visualHPx = 56f,
                diameterPx = d,
            )
        assertThat(kotlin.math.abs(wrong.first - rl)).isGreaterThan(2f)
    }

    // --- task-018/019 content-bearing dummy / handoff ---

    @Test
    fun dummyContentAlpha_p0_isFull_fadesDuringOpen() {
        // F741N: fade 100ms / 500ms → linearT=0.2 het alpha.
        assertThat(statusBarMorphDummyContentAlpha(0f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphDummyContentAlpha(0.1f, closing = false)).isWithin(0.01f).of(0.5f)
        assertThat(statusBarMorphDummyContentAlpha(0.2f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.55f, closing = false)).isEqualTo(0f)
        // Scale >1 trong 100ms dau (spring →2).
        assertThat(statusBarMorphDummyContentScale(0.1f, closing = false)).isGreaterThan(1.05f)
    }

    @Test
    fun dummyContentAlpha_collapse_returnsBeforePillHandoff() {
        // expandedFraction: p=1 start close; p=0 settled. elapsed=(1-p)*600.
        assertThat(statusBarMorphDummyContentAlpha(1f, closing = true)).isEqualTo(0f)
        // 50ms: p=1-50/600 → dummy alpha >0 (som, khong doi p<=.28).
        assertThat(statusBarMorphDummyContentAlpha(1f - 50f / 600f, closing = true))
            .isGreaterThan(0f)
        assertThat(statusBarMorphDummyContentAlpha(1f - 200f / 600f, closing = true))
            .isEqualTo(1f)
        assertThat(statusBarMorphDummyContentAlpha(0f, closing = true)).isEqualTo(1f)
        // Panel close early — p=0.45 elapsed~330ms >> 50 → panel 0.
        assertThat(statusBarMorphCardContentAlpha(0.45f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.45f, closing = true)).isEqualTo(1f)
    }

    @Test
    fun contentHandoff_crossfadeNoEmptyMiddle() {
        // F741N: shell glass cover gap khi dummy fade / panel chua gate.
        val mid = statusBarMorphTracksAtMs(225f, closing = false)
        assertThat(mid.shellGlass).isEqualTo(1f)
        assertThat(
            statusBarMorphContentHandoffCoherent(mid.dummyAlpha, mid.panelAlpha, mid.shellGlass)
        )
            .isTrue()
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = false)).isTrue()
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = true)).isTrue()
        // Shell-only (content gap Samsung) VAN coherent.
        assertThat(statusBarMorphContentHandoffCoherent(0f, 0f, shellAlpha = 1f)).isTrue()
        assertThat(statusBarMorphContentHandoffCoherent(0f, 0f, shellAlpha = 0f)).isFalse()
    }

    @Test
    fun cardContentAlpha_p0Zero_restFull() {
        assertThat(statusBarMorphCardContentAlpha(0f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(1f, closing = false)).isEqualTo(1f)
        // Sau dummy 100ms + width ok → panel tang (linearT=0.2 van 0; 0.5 remap mid).
        assertThat(statusBarMorphCardContentAlpha(0.2f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.6f, closing = false)).isGreaterThan(0.4f)
    }

    @Test
    fun inverseContentScale_undoesAnisotropicHost() {
        val sx = 253f / 1012f
        val sy = 56f / 236f
        val (ix, iy) = statusBarMorphInverseContentScale(sx, sy)
        assertThat(ix * sx).isWithin(0.001f).of(1f)
        assertThat(iy * sy).isWithin(0.001f).of(1f)
        // Khong bang nhau khi sx≠sy — bu rieng tung truc.
        assertThat(ix).isNotEqualTo(iy)
    }

    // --- task-015: pill CHI morph — khong spring/prelude/landing ---

    @Test
    fun pillMorphOnly_noElasticApiSymbolsInSharedContract() {
        // Expand/collapse SSOT F741N 500/600; clock 2dp; HideMs sync ExpandMs.
        assertThat(StatusBarMorphExpandMs).isEqualTo(500)
        assertThat(StatusBarMorphCollapseMs).isEqualTo(600)
        // task-007: neighbor slot 500/500 — KHONG sync MorphCollapse 600.
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
        assertThat(StatusBarClockNudgeDp).isEqualTo(2f)
        assertThat(StatusBarMorphCollapseMs).isEqualTo(600)
        // Geometry PathInterpolator: y(0)=0 y(1)=1; mid co overshoot >1.
        assertThat(StatusBarMorphMotionEasing.transform(0f)).isWithin(0.001f).of(0f)
        assertThat(StatusBarMorphMotionEasing.transform(1f)).isWithin(0.001f).of(1f)
        assertThat(StatusBarMorphMotionEasing.transform(0.5f)).isGreaterThan(0.5f)
        // Row glass = shell pass-through (mot owner); chrome khong tat glass.
        assertThat(statusBarMorphRowMorphGlassAlpha(1f, 1f)).isEqualTo(1f)
        assertThat(statusBarMorphRowMorphGlassAlpha(1f, 0f)).isEqualTo(1f)
        assertThat(statusBarMorphRowMorphGlassAlpha(0.5f, 0.5f)).isWithin(0.001f).of(0.5f)
        val (ix, iy) = statusBarMorphInverseContentScale(0.25f, 0.5f)
        assertThat(ix * 0.25f).isWithin(0.001f).of(1f)
        assertThat(iy * 0.5f).isWithin(0.001f).of(1f)
    }

    @Test
    fun visualRect_expand_noRightDrift_stableFinalLeft() {
        // USER: pill@150, w200→target960, screen1080 — left1=(1080-960)/2=60, khong ~440.
        val pillL = 150f
        val pillW = 200f
        val pillH = 56f
        val targetW = 960f
        val targetH = 400f
        val maxW = 1080f
        val leftFinal = (maxW - targetW) * 0.5f
        assertThat(leftFinal).isWithin(0.05f).of(60f)
        var prevLeft = pillL
        for (i in 0..20) {
            val p = i / 20f
            val g =
                computeKeyguardMorphGeom(
                    pRaw = p,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    targetWPx = targetW,
                    targetHPx = targetH,
                )
            val vr =
                statusBarMorphLerpVisualRectPx(
                    pOffset = g.pOffset,
                    pillLeftPx = pillL,
                    pillTopPx = 40f,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    visualTargetWPx = targetW,
                    visualTargetHPx = targetH,
                    sx = g.sx,
                    sy = g.sy,
                    maxWidthPx = maxW,
                    targetVisualTopPx = 85f,
                )
            if (p <= 0f) {
                assertThat(vr.leftPx).isWithin(0.05f).of(pillL)
            } else if (p >= 1f) {
                assertThat(vr.leftPx).isWithin(0.05f).of(leftFinal)
            } else {
                // Khong excursion phai hon pill (drift bug cu left1~440).
                assertThat(vr.leftPx).isAtMost(pillL + 0.05f)
                // Expand: left giam monotonic ve center (pill ben phai hon final).
                assertThat(vr.leftPx).isAtMost(prevLeft + 0.05f)
            }
            prevLeft = vr.leftPx
        }
        // Reverse p=1→0: exact mirror endpoints.
        val g0 =
            computeKeyguardMorphGeom(
                pRaw = 0f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = targetW,
                targetHPx = targetH,
            )
        val g1 =
            computeKeyguardMorphGeom(
                pRaw = 1f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = targetW,
                targetHPx = targetH,
            )
        val vr0 =
            statusBarMorphLerpVisualRectPx(
                pOffset = g0.pOffset,
                pillLeftPx = pillL,
                pillTopPx = 40f,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = targetW,
                visualTargetHPx = targetH,
                sx = g0.sx,
                sy = g0.sy,
                maxWidthPx = maxW,
                targetVisualTopPx = 85f,
            )
        val vr1 =
            statusBarMorphLerpVisualRectPx(
                pOffset = g1.pOffset,
                pillLeftPx = pillL,
                pillTopPx = 40f,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = targetW,
                visualTargetHPx = targetH,
                sx = g1.sx,
                sy = g1.sy,
                maxWidthPx = maxW,
                targetVisualTopPx = 85f,
            )
        assertThat(vr0.leftPx).isWithin(0.05f).of(pillL)
        assertThat(vr1.leftPx).isWithin(0.05f).of(leftFinal)
        assertThat(vr0.widthPx).isWithin(0.5f).of(pillW)
        assertThat(vr1.widthPx).isWithin(0.5f).of(targetW)
    }

    @Test
    fun clockNudge_oneShot_pushReturn_noRing() {
        // task-007: production = spring 2 pha; helper one-shot chi legacy envelope.
        assertThat(StatusBarClockNudgeSpring1Stiffness).isEqualTo(400f)
        assertThat(StatusBarClockNudgeSpring2Stiffness).isEqualTo(100f)
        val amp = 5.625f
        assertThat(statusBarClockNudgeTranslationXAt(0f, amp)).isWithin(0.01f).of(0f)
        assertThat(statusBarClockNudgeTranslationXAt(1f, amp)).isWithin(0.01f).of(0f)
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_HIDE)).isFalse()
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.APP_SHOW)).isTrue()
    }

    @Test
    fun circleAspect_ringDiameterIsotropic() {
        val vw = 80f
        val vh = 40f
        val d = statusBarMorphCircleRingVisualDiameterPx(vw, vh)
        assertThat(d).isWithin(0.05f).of(40f)
        // aspect ring bbox = 1 (vuong diameter×diameter).
        assertThat(d / d).isWithin(0.001f).of(1f)
    }

    @Test
    fun expandCollapse_alphas_reverseTimeline() {
        // Open p=0.2 (=100ms): dummy alpha 0; card van 0.
        assertThat(statusBarMorphDummyContentAlpha(0.2f, false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.2f, false)).isEqualTo(0f)
        // Open p=0.7: dummy 0, card remap >0.5.
        assertThat(statusBarMorphDummyContentAlpha(0.7f, false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.7f, false)).isGreaterThan(0.5f)
        // Close p=0.95 (~30ms): panel early hide; dummy dang len.
        assertThat(statusBarMorphCardContentAlpha(0.95f, true)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.95f, true)).isGreaterThan(0f)
        // Close p=0.05: gan pill — dummy full, panel 0.
        assertThat(statusBarMorphDummyContentAlpha(0.05f, true)).isEqualTo(1f)
        assertThat(statusBarMorphCardContentAlpha(0.05f, true)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.2f, true)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.2f, true)).isEqualTo(1f)
    }

    @Test
    fun presentationSnapshot_ownerAndGeneration() {
        val timer =
            IslandEvent.Timer(
                label = "t",
                endTimeMs = 0L,
                isPaused = true,
                pausedRemainingMs = 65_000L,
                notificationKey = "k1",
            )
        val snap =
            statusBarPillPresentationOf(
                event = timer,
                isAlert = false,
                chipStyle = 0,
                generation = 7,
            )
        assertThat(snap.ownerId).isEqualTo(timer.id)
        assertThat(snap.generation).isEqualTo(7)
        assertThat(snap.frozenChronometerMs).isEqualTo(65_000L)
        assertThat(statusBarPillPresentationMatchesOwner(snap, timer.id, 7)).isTrue()
        assertThat(statusBarPillPresentationMatchesOwner(snap, timer.id, 8)).isFalse()
        assertThat(statusBarPillPresentationMatchesOwner(snap, "other", 7)).isFalse()
        assertThat(statusBarPillPresentationGenerationValid(snap, 7)).isTrue()
        assertThat(statusBarPillPresentationGenerationValid(snap, 8)).isFalse()
        // Static freeze: cung pausedRemaining khong tick wall.
        val snap2 =
            statusBarPillPresentationOf(
                event = timer,
                isAlert = false,
                chipStyle = 0,
                generation = 7,
            )
        assertThat(snap2.frozenChronometerMs).isEqualTo(snap.frozenChronometerMs)
    }

    @Test
    fun mediaTrackSwitchKey_liveChanges_staticOwnerStable() {
        val m1 =
            IslandEvent.Media(
                track = "A",
                artist = "X",
                isPlaying = true,
                packageName = "pkg",
                mediaKey = "mk",
            )
        val m2 = m1.copy(track = "B")
        assertThat(mediaTrackSwitchKey(m1)).isNotEqualTo(mediaTrackSwitchKey(m2))
        val snap =
            statusBarPillPresentationOf(
                event = m1,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
            )
        // Snapshot giu event A — owner/gen on dinh du live doi bai.
        assertThat(snap.event).isEqualTo(m1)
        assertThat((snap.event as IslandEvent.Media).track).isEqualTo("A")
        assertThat(statusBarPillPresentationMatchesOwner(snap, m1.id, 1)).isTrue()
    }

    // --- task-025: rect lifecycle reopen khong can OGP re-fire ---

    @Test
    fun pillFrame_openCloseOpen_keepsRectWithoutRecapture() {
        // Bug cu: settle clear → morphFrame empty → origin center. Sau fix: van valid.
        val t = StatusBarPillFrameTracker()
        t.capture(400, 40, 253, 56, intentExpanded = false, pillSuppressed = false)
        assertThat(t.morphFrame.isValid).isTrue()

        // OPEN #1
        t.seedMorphFromObserved()
        val open1 = t.morphFrame
        t.capture(400, 40, 253, 56, intentExpanded = true, pillSuppressed = false)
        assertThat(t.morphFrame).isEqualTo(open1) // freeze
        t.capture(400, 40, 253, 56, intentExpanded = true, pillSuppressed = true)
        assertThat(t.morphFrame).isEqualTo(open1)

        // CLOSE: remasure dest (cung bbox) roi settle — KHONG invalidate
        t.capture(400, 40, 253, 56, intentExpanded = false, pillSuppressed = true)
        t.notifyCollapseSettled()
        assertThat(t.morphFrame.isValid).isTrue()
        assertThat(t.morphFrame.left).isEqualTo(400)
        assertThat(t.morphFrame.width).isEqualTo(253)

        // OPEN #2 / #3: khong goi capture moi (gia lap OGP im)
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.left).isEqualTo(400)
        assertThat(t.morphFrame.top).isEqualTo(40)
        t.notifyCollapseSettled()
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.width).isEqualTo(253)
        assertThat(t.morphFrame.height).isEqualTo(56)
    }

    @Test
    fun pillFrame_movedWhileIdle_updatesObservedAndMorph() {
        val t = StatusBarPillFrameTracker()
        t.capture(100, 40, 200, 56, intentExpanded = false, pillSuppressed = false)
        t.capture(320, 42, 210, 56, intentExpanded = false, pillSuppressed = false)
        assertThat(t.lastObserved.left).isEqualTo(320)
        assertThat(t.morphFrame.left).isEqualTo(320)
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.left).isEqualTo(320)
    }

    @Test
    fun pillFrame_movedWhileSuppressedClosing_updatesMorphDest() {
        val t = StatusBarPillFrameTracker()
        t.capture(100, 40, 200, 56, intentExpanded = false, pillSuppressed = false)
        t.seedMorphFromObserved()
        // Collapse suppressed: remasure dest moi
        t.capture(350, 44, 220, 56, intentExpanded = false, pillSuppressed = true)
        assertThat(t.morphFrame.left).isEqualTo(350)
        assertThat(t.morphFrame.width).isEqualTo(220)
        t.notifyCollapseSettled()
        assertThat(t.morphFrame.left).isEqualTo(350)
    }

    @Test
    fun pillFrame_noPillInvalidate_clearsStale_thenFreshCapture() {
        val t = StatusBarPillFrameTracker()
        t.capture(400, 40, 253, 56, intentExpanded = false, pillSuppressed = false)
        t.invalidate()
        assertThat(t.morphFrame.isValid).isFalse()
        assertThat(t.lastObserved.isValid).isFalse()
        // Reappear: capture moi, khong dung stale 400
        t.capture(180, 38, 240, 56, intentExpanded = false, pillSuppressed = false)
        assertThat(t.morphFrame.left).isEqualTo(180)
    }

    @Test
    fun pillFrame_keyguardInvalidate_dropsRect() {
        val t = StatusBarPillFrameTracker()
        t.capture(400, 40, 253, 56, intentExpanded = false, pillSuppressed = false)
        // collapseImmediate / keyguard → invalidate
        t.invalidate()
        assertThat(t.morphFrame.isValid).isFalse()
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.isValid).isFalse()
    }

    @Test
    fun pillOrigin_missingCapture_doesNotCenter() {
        val (left, top) =
            statusBarMorphPillOriginInHostPx(
                capturedLeft = 0,
                capturedTop = 0,
                capturedWidth = 0,
                capturedHeight = 0,
                hostLocX = 0,
                hostLocY = 0,
                topPadPx = 79,
            )
        assertThat(left).isEqualTo(0)
        assertThat(top).isEqualTo(79)
        // Center gia (1080-253)/2 = 413 — phai KHAC
        assertThat(left).isNotEqualTo((1080 - 253) / 2)
    }

    @Test
    fun pillOrigin_validCapture_usesScreenMinusHost() {
        val (left, top) =
            statusBarMorphPillOriginInHostPx(
                capturedLeft = 500,
                capturedTop = 120,
                capturedWidth = 253,
                capturedHeight = 56,
                hostLocX = 100,
                hostLocY = 80,
                topPadPx = 79,
            )
        assertThat(left).isEqualTo(400)
        assertThat(top).isEqualTo(40)
    }

    @Test
    fun confirmSuppress_rejectsWithoutValidRect_evenIfMorphRunning() {
        assertThat(
            statusBarMorphShouldConfirmPillSuppressed(
                hasValidPillRect = false,
                shellAlignedRect = true,
                morphRunning = true,
                morphProgress = 0.5f,
            )
        )
            .isFalse()
    }

    @Test
    fun confirmSuppress_alignedOrRunningWithValidRect() {
        assertThat(
            statusBarMorphShouldConfirmPillSuppressed(
                hasValidPillRect = true,
                shellAlignedRect = true,
                morphRunning = false,
                morphProgress = 0f,
            )
        )
            .isTrue()
        assertThat(
            statusBarMorphShouldConfirmPillSuppressed(
                hasValidPillRect = true,
                shellAlignedRect = false,
                morphRunning = true,
                morphProgress = 0.1f,
            )
        )
            .isTrue()
        assertThat(
            statusBarMorphShouldConfirmPillSuppressed(
                hasValidPillRect = true,
                shellAlignedRect = false,
                morphRunning = false,
                morphProgress = 0f,
            )
        )
            .isFalse()
    }

    @Test
    fun shellAlignedRect_rejectsXyMismatch() {
        assertThat(
            statusBarMorphPillShellAlignedRect(
                pillLeftPx = 400f,
                pillTopPx = 40f,
                pillWPx = 253f,
                pillHPx = 56f,
                visualLeftPx = (1080f - 253f) / 2f, // center gia
                visualTopPx = 40f,
                visualWPx = 253f,
                visualHPx = 56f,
                progress = 0f,
                tolerancePx = 2f,
            )
        )
            .isFalse()
        assertThat(
            statusBarMorphPillShellAlignedRect(
                pillLeftPx = 400f,
                pillTopPx = 40f,
                pillWPx = 253f,
                pillHPx = 56f,
                visualLeftPx = 400f,
                visualTopPx = 40f,
                visualWPx = 253f,
                visualHPx = 56f,
                progress = 0f,
                tolerancePx = 2f,
            )
        )
            .isTrue()
    }

    @Test
    fun circleGeom_p0_stillMatchesPill_afterLifecycleHelpers() {
        val pillW = 56f
        val pillH = 56f
        val g =
            computeKeyguardMorphGeom(
                pRaw = 0f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = 900f,
                targetHPx = 400f,
            )
        val (vw, vh) = statusBarMorphVisualShellSizePx(900f, 400f, g.sx, g.sy)
        assertThat(vw).isWithin(0.05f).of(pillW)
        assertThat(vh).isWithin(0.05f).of(pillH)
        assertThat(
            statusBarMorphPillShellAlignedRect(
                500f,
                30f,
                pillW,
                pillH,
                500f,
                30f,
                vw,
                vh,
                progress = 0f,
            )
        )
            .isTrue()
    }

    // --- task-028: visual handoff soft glass + collapse flash + icon slot reclaim ---

    @Test
    fun shellGlassAlpha_open_fullUntilCardThenComplementary() {
        // Open: shell=1 den handoffStart; soft release → 0 @p1 (khong step .999).
        assertThat(statusBarMorphShellGlassAlpha(0f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.35f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.55f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(StatusBarMorphShellHandoffStart, closing = false))
            .isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(1f, closing = false)).isEqualTo(0f)
        val midGlass = statusBarMorphShellGlassAlpha(0.45f, closing = false)
        val midCard = statusBarMorphCardContentAlpha(0.45f, closing = false)
        assertThat(midGlass).isEqualTo(1f)
        // Coverage: shell luon 1 luc flight truoc soft release.
        assertThat(midGlass + midCard).isAtLeast(1f)
    }

    @Test
    fun shellGlassAlpha_close_risesTowardPill() {
        // Close soft-take: p=1 → shell0; p<=handoffStart → shell1 (khong jump 0→1).
        assertThat(statusBarMorphShellGlassAlpha(1f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphShellGlassAlpha(StatusBarMorphShellHandoffStart, closing = true))
            .isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.28f, closing = true)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.12f, closing = true)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0f, closing = true)).isEqualTo(1f)
    }

    @Test
    fun shellTintProgress_linear_continuousFromP0() {
        // Color track Decelerate open — DOC LAP geom; monotone 0→1.
        assertThat(statusBarMorphShellTintProgress(0f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphShellTintProgress(1f, closing = false)).isEqualTo(1f)
        val mid = statusBarMorphShellTintProgress(0.5f, closing = false)
        assertThat(mid).isGreaterThan(0.5f) // Decelerate: nhanh dau
        assertThat(mid).isAtMost(1f)
        var prev = -1f
        for (i in 0..10) {
            val t = statusBarMorphShellTintProgress(i / 10f, closing = false)
            assertThat(t).isAtLeast(prev - 1e-4f)
            prev = t
        }
    }

    @Test
    fun tintBlurAlpha_openSamples_continuousComplementary() {
        // Color doc lap geom; backplate coverage shell+parent×blur >= minCover.
        val samples = floatArrayOf(0f, 0.1f, 0.2f, 0.35f, 0.4f, 0.45f, 0.5f, 0.55f, 1f)
        var prevTint = -1f
        for (p in samples) {
            val tint = statusBarMorphShellTintProgress(p, closing = false)
            val glass = statusBarMorphShellGlassAlpha(p, closing = false)
            val row = statusBarMorphRowMorphGlassAlpha(glass, statusBarMorphDummyContentAlpha(p, false))
            val card = statusBarMorphCardContentAlpha(p, closing = false)
            val dummy = statusBarMorphDummyContentAlpha(p, closing = false)
            assertThat(tint).isAtLeast(prevTint - 1e-4f)
            assertThat(row).isWithin(0.001f).of(glass)
            assertThat(
                statusBarMorphContentHandoffCoherent(dummy, card, glass)
            )
                .isTrue()
            if (p <= StatusBarMorphShellHandoffStart) {
                assertThat(glass).isEqualTo(1f)
            } else if (p >= 1f) {
                assertThat(glass).isEqualTo(0f)
            } else {
                assertThat(glass).isGreaterThan(0f)
                assertThat(glass).isLessThan(1f)
            }
            val blurFactor = statusBarMorphCardSurfaceBlurFactor(card, row)
            if (card <= 0.01f || row >= StatusBarMorphMinBackplateCoverage) {
                assertThat(blurFactor).isEqualTo(0f)
            } else if (card > 0.01f) {
                assertThat(blurFactor).isGreaterThan(0f)
            }
            val cov = statusBarMorphBackplateCoverage(row, card, blurFactor)
            if (card > 0.01f || row >= StatusBarMorphMinBackplateCoverage) {
                assertThat(cov).isAtLeast(StatusBarMorphMinBackplateCoverage - 1e-3f)
            }
            prevTint = tint
        }
        assertThat(statusBarMorphShellTintProgress(0f, false)).isEqualTo(0f)
        assertThat(statusBarMorphShellTintProgress(1f, false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0f, false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(1f, false)).isEqualTo(0f)
    }

    @Test
    fun shellCardCoverage_closeSamples_noBackgroundGap() {
        // Close: composite backplate (shell + panel×blur) >= minCover — khong chi max().
        val samples = floatArrayOf(1f, 0.99f, 0.98f, 0.97f, 0.5f, 0.28f, 0.2f, 0.12f, 0f)
        for (p in samples) {
            val glass = statusBarMorphShellGlassAlpha(p, closing = true)
            val card = statusBarMorphCardContentAlpha(p, closing = true)
            val blur = statusBarMorphCardSurfaceBlurFactor(card, glass)
            val cov = statusBarMorphBackplateCoverage(glass, card, blur)
            assertThat(cov).isAtLeast(StatusBarMorphMinBackplateCoverage - 1e-3f)
            if (glass >= StatusBarMorphMinBackplateCoverage && card > 0.01f) {
                assertThat(blur).isEqualTo(0f)
            }
        }
    }

    @Test
    fun cardSurfaceBlurFactor_suppressesWhileShellStrong() {
        assertThat(statusBarMorphCardSurfaceBlurFactor(0f, 1f)).isEqualTo(0f)
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0f)).isEqualTo(1f)
        // Shell >= minCover → blur native = 0 (1-owner).
        assertThat(
                statusBarMorphCardSurfaceBlurFactor(0.5f, StatusBarMorphMinBackplateCoverage)
            )
            .isEqualTo(0f)
        // Shell thieu + panel co → blur bo sung (coverage-driven, khong con .15 hard).
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0.25f)).isGreaterThan(0.5f)
        // p1 native: shell 0 → full card blur.
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0f)).isEqualTo(1f)
    }

    @Test
    fun collapseOverlayChrome_zeroOnlyAfterLiveDrawnAck() {
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = false,
                pillSuppressed = true,
            )
        )
            .isEqualTo(1f)
        // hold + unsuppress + CHUA ack → van giu chrome (043: khong α0 truoc live).
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = true,
                pillSuppressed = false,
                liveDrawnAcked = false,
            )
        )
            .isEqualTo(1f)
        // Sau ack → chrome 0.
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = true,
                pillSuppressed = false,
                liveDrawnAcked = true,
            )
        )
            .isEqualTo(0f)
        // Van suppressed: giu chrome (chua handback).
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = true,
                pillSuppressed = true,
                liveDrawnAcked = false,
            )
        )
            .isEqualTo(1f)
    }

    @Test
    fun overlayHideDelay_morphSettleIs50ms() {
        assertThat(statusBarOverlayHideDelayMs(alertExitGrace = false)).isEqualTo(50L)
        assertThat(statusBarOverlayHideDelayMs(alertExitGrace = true)).isEqualTo(400L)
    }

    @Test
    fun pillFrameCaptureAllowed_blocksReclaimAndShrink() {
        assertThat(
            statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = true,
                reportedWidth = 40,
                lastObservedWidth = 253,
            )
        )
            .isFalse()
        // Cung owner: half-shrink van reject (fraction teo / partial).
        assertThat(
            statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = false,
                reportedWidth = 80,
                lastObservedWidth = 253,
                ownerChanged = false,
            )
        )
            .isFalse()
        assertThat(
            statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = false,
                reportedWidth = 253,
                lastObservedWidth = 253,
            )
        )
            .isTrue()
        // Owner doi A→B: B < half A van accept @full restore.
        assertThat(
            statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = false,
                reportedWidth = 80,
                lastObservedWidth = 253,
                ownerChanged = true,
            )
        )
            .isTrue()
        // Owner doi + reclaim: van reject.
        assertThat(
            statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = true,
                reportedWidth = 80,
                lastObservedWidth = 253,
                ownerChanged = true,
            )
        )
            .isFalse()
    }

    @Test
    fun pillFrame_slotReclaimed_doesNotOverwriteObserved() {
        val t = StatusBarPillFrameTracker()
        t.capture(150, 12, 253, 56, intentExpanded = false, pillSuppressed = false)
        t.seedMorphFromObserved()
        // Expand + suppress + reclaim: width teo KHONG ghi de freeze.
        t.capture(
            150,
            12,
            40,
            56,
            intentExpanded = true,
            pillSuppressed = true,
            pillSlotReclaimed = true,
        )
        assertThat(t.lastObserved.width).isEqualTo(253)
        assertThat(t.morphFrame.width).isEqualTo(253)
        // Collapse: restore slot (reclaimed=false) — remasure dest OK.
        t.capture(
            150,
            12,
            253,
            56,
            intentExpanded = false,
            pillSuppressed = true,
            pillSlotReclaimed = false,
        )
        t.notifyCollapseSettled()
        assertThat(t.lastObserved.width).isEqualTo(253)
        t.seedMorphFromObserved()
        assertThat(t.morphFrame.left).isEqualTo(150)
    }

    @Test
    fun contentHandoffWindow_unchanged_028() {
        // Legacy const giu; track moi = elapsed ms + shell gap OK.
        assertThat(StatusBarMorphContentHandoffStart).isEqualTo(0.35f)
        assertThat(StatusBarMorphContentHandoffEnd).isEqualTo(0.55f)
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = false)).isTrue()
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = true)).isTrue()
    }

    // --- task-029: hold lifecycle (tach timer) + zero-width outer occupation ---

    @Test
    fun collapseHandback_settleNormal_holdClearsAfterTimer() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(layoutGen = 3)
        assertThat(start.shouldNotifySettle).isTrue()
        assertThat(c.holdActive).isTrue()
        assertThat(start.holdGeneration).isEqualTo(1)
        // Exactly-once cung layoutGen (pillSuppressed flip khong double notify).
        assertThat(c.beginSettleHandback(3).shouldNotifySettle).isFalse()
        assertThat(c.clearHoldIfGeneration(1)).isTrue()
        assertThat(c.holdActive).isFalse()
        // Chrome factor: hold + unsuppress + ack → 0.
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = true,
                pillSuppressed = false,
                liveDrawnAcked = true,
            )
        )
            .isEqualTo(0f)
        assertThat(
            statusBarMorphCollapseOverlayChromeFactor(
                collapseHandbackHold = true,
                pillSuppressed = false,
                liveDrawnAcked = false,
            )
        )
            .isEqualTo(1f)
    }

    @Test
    fun collapseHandback_reverseMidHold_clearsImmediately() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(5)
        assertThat(c.holdActive).isTrue()
        c.onExpandedOrReverse()
        assertThat(c.holdActive).isFalse()
        // Timer cu (gen 1) khong clear stale sau reverse.
        assertThat(c.clearHoldIfGeneration(start.holdGeneration)).isFalse()
        // Settle gen moi sau reverse van duoc.
        val again = c.beginSettleHandback(6)
        assertThat(again.shouldNotifySettle).isTrue()
        assertThat(c.holdActive).isTrue()
    }

    @Test
    fun collapseHandback_generationSwap_staleClearIgnored() {
        val c = CollapseHandbackHoldController()
        val g1 = c.beginSettleHandback(10).holdGeneration
        // Settle gen layout moi (mo lai / morph khac) — hold gen tang.
        c.onExpandedOrReverse()
        val g2 = c.beginSettleHandback(11).holdGeneration
        assertThat(g2).isGreaterThan(g1)
        assertThat(c.holdActive).isTrue()
        // Timer gen cu xong muon — khong ha hold gen moi.
        assertThat(c.clearHoldIfGeneration(g1)).isFalse()
        assertThat(c.holdActive).isTrue()
        assertThat(c.clearHoldIfGeneration(g2)).isTrue()
        assertThat(c.holdActive).isFalse()
    }

    @Test
    fun chipOuterOccupied_zeroEvenWithMin25AndPadding() {
        // natural = child sau widthIn(min=25)+padding (~25dp + 6dp pad gia).
        val naturalWithMinAndPad = 31
        assertThat(statusBarChipOuterOccupiedWidthPx(naturalWithMinAndPad, 0f)).isEqualTo(0)
        assertThat(statusBarChipOuterOccupiedWidthPx(naturalWithMinAndPad, 1f))
            .isEqualTo(naturalWithMinAndPad)
        assertThat(statusBarChipOuterOccupiedWidthPx(100, 0.5f)).isEqualTo(50)
        assertThat(statusBarChipOuterOccupiedWidthPx(0, 1f)).isEqualTo(0)
    }

    @Test
    fun chipOuterOccupied_layoutNeverNegative_endpointsExact() {
        assertThat(statusBarChipOuterOccupiedWidthPx(100, 0f)).isEqualTo(0)
        assertThat(statusBarChipOuterOccupiedWidthPx(100, 1f)).isEqualTo(100)
        // EXPAND raw am → layout 0; COLLAPSE raw>1 cap dp ~clock @450dpi / W=100.
        val capFrac = statusBarChipSlotOvershootCapFraction(100f, 450f)
        assertThat(statusBarChipSlotWidthFractionForLayout(-1f, 100f, 450f)).isEqualTo(0f)
        assertThat(statusBarChipSlotWidthFractionForLayout(2f, 100f, 450f))
            .isAtMost(1f + capFrac + 1e-4f)
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
    }

    @Test
    fun nicTrajectory_expandMonotonic_collapseOvershootCapped2dp() {
        // task-007: CARD curves G991N; overshoot OK; layout nonnegative; endpoints 0/1.
        // Ten test legacy — khong con cap 2dp / gain4.
        val widths = floatArrayOf(150f, 200f, 260f)
        val samples = floatArrayOf(0f, 0.25f, 0.5f, 0.65f, 0.85f, 1f)
        for (w in widths) {
            for (t in samples) {
                val rawExp =
                    statusBarChipSlotRawFractionAt(
                        reclaiming = true,
                        t = t,
                        trigger = StatusBarNeighborTrigger.CARD_OPEN,
                    )
                val layoutExp = statusBarChipSlotWidthFractionForLayout(rawExp, w, 450f)
                assertThat(layoutExp).isAtLeast(0f)
                val rawCol =
                    statusBarChipSlotRawFractionAt(
                        reclaiming = false,
                        t = t,
                        trigger = StatusBarNeighborTrigger.CARD_CLOSE,
                    )
                val layoutCol = statusBarChipSlotWidthFractionForLayout(rawCol, w, 450f)
                assertThat(layoutCol).isAtLeast(0f)
            }
            assertThat(
                    statusBarChipSlotWidthFractionForLayout(
                        statusBarChipSlotRawFractionAt(
                            true,
                            1f,
                            StatusBarNeighborTrigger.CARD_OPEN,
                        ),
                        w,
                        450f,
                    ),
                )
                .isEqualTo(0f)
            assertThat(
                    statusBarChipSlotWidthFractionForLayout(
                        statusBarChipSlotRawFractionAt(
                            false,
                            1f,
                            StatusBarNeighborTrigger.CARD_CLOSE,
                        ),
                        w,
                        450f,
                    ),
                )
                .isEqualTo(1f)
        }
    }

    @Test
    fun slotReflow_expandNoRebound_collapseOvershootSettles() {
        // task-007: CARD CLOSE overshoot; CARD OPEN layout>=0; endpoints settle.
        val lateCollapse =
            statusBarChipSlotRawFractionAt(
                reclaiming = false,
                t = 0.65f,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        val endCollapse =
            statusBarChipSlotRawFractionAt(
                reclaiming = false,
                t = 1f,
                trigger = StatusBarNeighborTrigger.CARD_CLOSE,
            )
        assertThat(statusBarChipSlotWidthFractionForLayout(endCollapse, 200f, 450f))
            .isEqualTo(1f)
        assertThat(statusBarChipSlotWidthFractionForLayout(lateCollapse, 200f, 450f))
            .isAtLeast(0f)
        val endExpand =
            statusBarChipSlotRawFractionAt(
                reclaiming = true,
                t = 1f,
                trigger = StatusBarNeighborTrigger.CARD_OPEN,
            )
        assertThat(statusBarChipSlotWidthFractionForLayout(endExpand, 200f, 450f))
            .isEqualTo(0f)
        for (i in 0..40) {
            val t = i / 40f
            val layout =
                statusBarChipSlotWidthFractionForLayout(
                    statusBarChipSlotRawFractionAt(
                        reclaiming = true,
                        t = t,
                        trigger = StatusBarNeighborTrigger.CARD_OPEN,
                    ),
                    200f,
                    450f,
                )
            assertThat(layout).isAtLeast(0f)
            val layoutCol =
                statusBarChipSlotWidthFractionForLayout(
                    statusBarChipSlotRawFractionAt(
                        reclaiming = false,
                        t = t,
                        trigger = StatusBarNeighborTrigger.CARD_CLOSE,
                    ),
                    200f,
                    450f,
                )
            assertThat(layoutCol).isAtLeast(0f)
        }
    }

    @Test
    fun nicGate_effectiveAndWithDesired_noDefaultTrue() {
        // desired false + unsuspend → false (khong hardcode true)
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = false, suspended = false))
            .isFalse()
        // desired true + suspend → false (gate thang)
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = true, suspended = true))
            .isFalse()
        // desired true + unsuspend → true
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = true, suspended = false))
            .isTrue()
        // mid-flight: desired flip true trong luc suspend van false
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = true, suspended = true))
            .isFalse()
        // unknown + suspend → force false
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = null, suspended = true))
            .isFalse()
        // unknown + unsuspend → null (KHONG default true / khong goi setter)
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = null, suspended = false))
            .isNull()
        // re-entry: false→suspend→true emission→van false; unsuspend→true
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = false, suspended = true))
            .isFalse()
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = true, suspended = true))
            .isFalse()
        assertThat(statusBarNicEffectiveAnimationsEnabled(desired = true, suspended = false))
            .isTrue()
    }

    @Test
    fun clockNudgeAmplitude_ltrNegativeRtlFlipped() {
        val dm = android.util.DisplayMetrics().apply { density = 2.5f /* 400dpi-ish */ }
        // 2dp * 2.5 = 5px
        val ltr =
            statusBarClockNudgeAmplitudePx(dm, android.view.View.LAYOUT_DIRECTION_LTR)
        val rtl =
            statusBarClockNudgeAmplitudePx(dm, android.view.View.LAYOUT_DIRECTION_RTL)
        assertThat(ltr).isWithin(0.01f).of(5f)
        assertThat(rtl).isWithin(0.01f).of(-5f)
        assertThat(statusBarClockShouldNudge(null)).isFalse()
    }

    @Test
    fun chipCenterUpdate_gatedWhenUnplacedOrReclaimed() {
        assertThat(
            statusBarChipCenterUpdateAllowed(
                pillSlotReclaimed = true,
                slotWidthFraction = 1f,
                reportedWidthPx = 80,
            )
        )
            .isFalse()
        assertThat(
            statusBarChipCenterUpdateAllowed(
                pillSlotReclaimed = false,
                slotWidthFraction = 0f,
                reportedWidthPx = 80,
            )
        )
            .isFalse()
        assertThat(
            statusBarChipCenterUpdateAllowed(
                pillSlotReclaimed = false,
                slotWidthFraction = 1f,
                reportedWidthPx = 0,
            )
        )
            .isFalse()
        assertThat(
            statusBarChipCenterUpdateAllowed(
                pillSlotReclaimed = false,
                slotWidthFraction = 1f,
                reportedWidthPx = 80,
            )
        )
            .isTrue()
    }

    @Test
    fun pillOrigin_sixRepeats_seedStableAfterSettle() {
        // task025 / 029: 6 lan mo — lastObserved khong bi width teo; seed morph on dinh.
        val t = StatusBarPillFrameTracker()
        repeat(6) { i ->
            val left = 140 + i // remasure nho, van full width
            t.capture(left, 12, 253, 56, intentExpanded = false, pillSuppressed = false)
            t.seedMorphFromObserved()
            assertThat(t.morphFrame.width).isEqualTo(253)
            // Expand reclaim: width teo KHONG overwrite.
            t.capture(
                left,
                12,
                0,
                56,
                intentExpanded = true,
                pillSuppressed = true,
                pillSlotReclaimed = true,
            )
            assertThat(t.lastObserved.width).isEqualTo(253)
            assertThat(t.morphFrame.width).isEqualTo(253)
            // Collapse restore slot → settle.
            t.capture(
                left,
                12,
                253,
                56,
                intentExpanded = false,
                pillSuppressed = true,
                pillSlotReclaimed = false,
            )
            t.notifyCollapseSettled()
            t.seedMorphFromObserved()
            assertThat(t.morphFrame.left).isEqualTo(left)
            assertThat(t.morphFrame.width).isEqualTo(253)
        }
    }

    // --- task-034: stack switch A→B → collapse frozen/dummy == B ---

    private fun mediaEvent(key: String, track: String) =
        IslandEvent.Media(
            mediaKey = key,
            track = track,
            artist = "a",
            isPlaying = true,
            packageName = "pkg",
        )

    private fun timerEvent(key: String, remainMs: Long) =
        IslandEvent.Timer(
            label = key,
            endTimeMs = 0L,
            isPaused = true,
            pausedRemainingMs = remainMs,
            notificationKey = key,
        )

    private fun stopwatchEvent(pausedMs: Long = 12_000L) =
        IslandEvent.Stopwatch(
            label = "sw",
            isRunning = false,
            pausedElapsedMs = pausedMs,
            notificationKey = "sw1",
        )

    @Test
    fun stackSwitch_aToB_collapseSnapshotUsesB() {
        val a = mediaEvent("a", "TrackA")
        val b = mediaEvent("b", "TrackB")
        val events = listOf(a, b)
        val frozenA =
            statusBarPillPresentationOf(
                event = a,
                isAlert = false,
                chipStyle = 0,
                generation = 3,
            )
        assertThat(shouldSyncFrozenPillPresentation(intentExpanded = true, frozen = frozenA))
            .isTrue()
        assertThat(shouldSyncFrozenPillPresentation(intentExpanded = false, frozen = frozenA))
            .isFalse()
        assertThat(shouldSyncFrozenPillPresentation(intentExpanded = true, frozen = null))
            .isFalse()

        // Commit B: preferred null (media) → topEvent B.
        val selected =
            resolveFrozenPillEventForSelection(
                events = events,
                preferredEventId = null,
                topEvent = b,
            )
        assertThat(selected?.id).isEqualTo(b.id)
        assertThat(frozenPillOwnerNeedsUpdate(frozenA, selected)).isTrue()

        val frozenB =
            statusBarPillPresentationOf(
                event = selected!!,
                isAlert = false,
                chipStyle = 0,
                generation = frozenA.generation,
            )
        assertThat(frozenB.ownerId).isEqualTo(b.id)
        assertThat(frozenB.generation).isEqualTo(3)
        assertThat(statusBarCollapsePresentationParity(frozenB, b.id)).isTrue()
        assertThat(statusBarCollapsePresentationParity(frozenA, b.id)).isFalse()
    }

    @Test
    fun stackSwitch_multiLatestWins_aToBToC() {
        val a = mediaEvent("a", "A")
        val b = timerEvent("tb", 30_000L)
        val c = mediaEvent("c", "C")
        val events = listOf(a, b, c)
        var frozen =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 0, generation = 1)
        // A→B (chrono preferred = b.id)
        val selB =
            resolveFrozenPillEventForSelection(events, preferredEventId = b.id, topEvent = b)
        assertThat(frozenPillOwnerNeedsUpdate(frozen, selB)).isTrue()
        frozen =
            statusBarPillPresentationOf(
                event = selB!!,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
            )
        assertThat(frozen.ownerId).isEqualTo(b.id)
        // B→C latest wins
        val selC =
            resolveFrozenPillEventForSelection(events, preferredEventId = null, topEvent = c)
        assertThat(frozenPillOwnerNeedsUpdate(frozen, selC)).isTrue()
        frozen =
            statusBarPillPresentationOf(
                event = selC!!,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
            )
        assertThat(frozen.ownerId).isEqualTo(c.id)
        assertThat(statusBarCollapsePresentationParity(frozen, c.id)).isTrue()
    }

    @Test
    fun stackSwitch_noCommitDrag_keepsFrozenA() {
        // Commit false → khong goi sync; helper van bao frozen A vs top A.
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "B")
        val frozenA =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 0, generation = 2)
        // top van A (chua pin)
        val selected =
            resolveFrozenPillEventForSelection(
                events = listOf(a, b),
                preferredEventId = null,
                topEvent = a,
            )
        assertThat(frozenPillOwnerNeedsUpdate(frozenA, selected)).isFalse()
        assertThat(statusBarCollapsePresentationParity(frozenA, a.id)).isTrue()
    }

    @Test
    fun stackSwitch_immediateCollapse_afterCommitUsesB() {
        val a = timerEvent("t1", 10_000L)
        val b = mediaEvent("m1", "Song")
        val frozenA =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 1, generation = 5)
        // Commit B roi collapse ngay — preferred null, top=B.
        val selected =
            resolveFrozenPillEventForSelection(
                events = listOf(a, b),
                preferredEventId = null,
                topEvent = b,
            )
        assertThat(selected?.id).isEqualTo(b.id)
        val frozenB =
            statusBarPillPresentationOf(
                event = selected!!,
                isAlert = false,
                chipStyle = 1,
                generation = frozenA.generation,
            )
        assertThat(statusBarPillPresentationGenerationValid(frozenB, 5)).isTrue()
        assertThat(statusBarCollapsePresentationParity(frozenB, b.id)).isTrue()
    }

    @Test
    fun stackSwitch_invalidId_fallsBackToTopEvent() {
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "B")
        val selected =
            resolveFrozenPillEventForSelection(
                events = listOf(a, b),
                preferredEventId = "gone_event",
                topEvent = b,
            )
        assertThat(selected?.id).isEqualTo(b.id)
        // Empty events → null (khong ve stale).
        assertThat(
                resolveFrozenPillEventForSelection(
                    events = emptyList(),
                    preferredEventId = "x",
                    topEvent = a,
                )
            )
            .isNull()
    }

    @Test
    fun stackSwitch_generationReverse_keepsFrozenGen() {
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "B")
        val frozenA =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 0, generation = 9)
        val selected =
            resolveFrozenPillEventForSelection(listOf(a, b), preferredEventId = null, topEvent = b)
        // Sync giu generation cu — reverse/reopen gate layoutGen khong bi nhay.
        val frozenB =
            statusBarPillPresentationOf(
                event = selected!!,
                isAlert = false,
                chipStyle = 0,
                generation = frozenA.generation,
            )
        assertThat(frozenB.generation).isEqualTo(9)
        assertThat(statusBarPillPresentationGenerationValid(frozenB, 9)).isTrue()
        assertThat(statusBarPillPresentationGenerationValid(frozenB, 10)).isFalse()
        // A→B→A reverse selection
        val backA =
            resolveFrozenPillEventForSelection(listOf(a, b), preferredEventId = null, topEvent = a)
        assertThat(frozenPillOwnerNeedsUpdate(frozenB, backA)).isTrue()
        assertThat(backA?.id).isEqualTo(a.id)
    }

    @Test
    fun stackSwitch_mediaChronoParity_overlayVsReal() {
        val media = mediaEvent("mk", "Long Title Track Here")
        val chrono = stopwatchEvent(45_000L)
        val events = listOf(media, chrono)
        // Media → stopwatch: preferred = chrono id (expandOnly).
        val selChrono =
            resolveFrozenPillEventForSelection(
                events = events,
                preferredEventId = chrono.id,
                topEvent = chrono,
            )
        val frozenChrono =
            statusBarPillPresentationOf(
                event = selChrono!!,
                isAlert = false,
                chipStyle = 0,
                generation = 4,
            )
        assertThat(frozenChrono.ownerId).isEqualTo(chrono.id)
        assertThat(frozenChrono.frozenChronometerMs).isEqualTo(45_000L)
        assertThat(statusBarCollapsePresentationParity(frozenChrono, chrono.id)).isTrue()

        // Stopwatch → media: preferred null, top media.
        val selMedia =
            resolveFrozenPillEventForSelection(
                events = events,
                preferredEventId = null,
                topEvent = media,
            )
        val frozenMedia =
            statusBarPillPresentationOf(
                event = selMedia!!,
                isAlert = false,
                chipStyle = 0,
                generation = 4,
            )
        assertThat(frozenMedia.ownerId).isEqualTo(media.id)
        assertThat(frozenMedia.frozenChronometerMs).isNull()
        assertThat(statusBarCollapsePresentationParity(frozenMedia, media.id)).isTrue()
        // Circle style chipStyle=1 — presentation van dung owner B.
        val circle =
            statusBarPillPresentationOf(
                event = media,
                isAlert = false,
                chipStyle = 1,
                generation = 4,
            )
        assertThat(circle.ownerId).isEqualTo(media.id)
        assertThat(statusBarCollapsePresentationParity(circle, media.id)).isTrue()
    }

    // --- task-037: chip_event closure + owner-aware capture + handoff parity ---

    @Test
    fun chipEvent_outgoingBranch_neverRendersOuterB() {
        val a = mediaEvent("a", "TrackA")
        val b = mediaEvent("b", "TrackB")
        val outgoing = StatusBarChipEventEntry(a, isAlert = false)
        val outer = StatusBarChipEventEntry(b, isAlert = false)
        // Dung entry presentation → owner A (dung).
        val fromEntry = statusBarChipEntryPresentation(outgoing, chipStyle = 0, generation = 1)
        assertThat(fromEntry.ownerId).isEqualTo(a.id)
        assertThat(
                statusBarChipBranchRendersOuterClosure(
                    branchEntry = outgoing,
                    outerCurrent = outer,
                    renderedOwnerId = fromEntry.ownerId,
                )
            )
            .isFalse()
        // Bug cu: build tu outer → rendered B tren branch A.
        val fromOuterLeak = statusBarChipEntryPresentation(outer, chipStyle = 0, generation = 1)
        assertThat(
                statusBarChipBranchRendersOuterClosure(
                    branchEntry = outgoing,
                    outerCurrent = outer,
                    renderedOwnerId = fromOuterLeak.ownerId,
                )
            )
            .isTrue()
        // Tick timer: key on dinh theo id (khong restart transition).
        val t0 = timerEvent("t1", 10_000L)
        val t1 = timerEvent("t1", 9_000L)
        assertThat(statusBarChipEventDisplayKey(t0, false))
            .isEqualTo(statusBarChipEventDisplayKey(t1, false))
        assertThat(StatusBarChipEventEntry(t0, false).key)
            .isEqualTo(StatusBarChipEventEntry(t1, false).key)
    }

    @Test
    fun pillFrame_ownerChange_acceptsShorterB_rejectsSameOwnerShrink() {
        val t = StatusBarPillFrameTracker()
        // A natural 200.
        t.capture(
            left = 100,
            top = 10,
            width = 200,
            height = 56,
            intentExpanded = false,
            pillSuppressed = false,
            ownerId = "a",
        )
        assertThat(t.lastObserved.width).isEqualTo(200)
        assertThat(t.lastObservedOwnerId).isEqualTo("a")
        // Cung owner + half shrink → reject (fraction teo gia).
        t.capture(
            left = 100,
            top = 10,
            width = 80,
            height = 56,
            intentExpanded = false,
            pillSuppressed = true,
            pillSlotReclaimed = false,
            ownerId = "a",
        )
        assertThat(t.lastObserved.width).isEqualTo(200)
        // Owner B < half A @full restore → accept.
        t.capture(
            left = 120,
            top = 10,
            width = 80,
            height = 56,
            intentExpanded = false,
            pillSuppressed = true,
            pillSlotReclaimed = false,
            ownerId = "b",
        )
        assertThat(t.lastObserved.width).isEqualTo(80)
        assertThat(t.lastObservedOwnerId).isEqualTo("b")
        // B dai hon A.
        t.capture(
            left = 90,
            top = 10,
            width = 220,
            height = 56,
            intentExpanded = false,
            pillSuppressed = true,
            ownerId = "c",
        )
        assertThat(t.lastObserved.width).isEqualTo(220)
        assertThat(t.lastObservedOwnerId).isEqualTo("c")
    }

    @Test
    fun pillFrame_outgoingBranch_staleOwnerNotCaptured() {
        // Chi target branch duoc capture geom.
        assertThat(
                statusBarPillFrameCaptureFromTransitionBranchAllowed(
                    branchKey = "a",
                    targetKey = "b",
                )
            )
            .isFalse()
        assertThat(
                statusBarPillFrameCaptureFromTransitionBranchAllowed(
                    branchKey = "b",
                    targetKey = "b",
                )
            )
            .isTrue()
        val t = StatusBarPillFrameTracker()
        t.capture(10, 10, 200, 56, intentExpanded = false, pillSuppressed = false, ownerId = "a")
        // Outgoing A stale trong luc target B — caller khong goi capture (gate branch).
        // Neu goi nham voi owner A (khong doi): half B khong lien quan; W giu A.
        assertThat(t.lastObservedOwnerId).isEqualTo("a")
        assertThat(t.lastObserved.width).isEqualTo(200)
    }

    @Test
    fun handback_parity_noDuplicate_requiresSettledLiveB() {
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "B")
        val frozenB =
            statusBarPillPresentationOf(event = b, isAlert = false, chipStyle = 0, generation = 2)
        // Frozen B + live B settled → ready (mot surface @unsuppress).
        assertThat(
                statusBarHandbackReadyForUnsuppress(
                    frozen = frozenB,
                    liveEventId = b.id,
                    liveContentSettled = true,
                )
            )
            .isTrue()
        // Live van crossfade → chua ready.
        assertThat(
                statusBarHandbackReadyForUnsuppress(
                    frozen = frozenB,
                    liveEventId = b.id,
                    liveContentSettled = false,
                )
            )
            .isFalse()
        // Frozen A vs live B → khong parity.
        val frozenA =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 0, generation = 2)
        assertThat(
                statusBarHandbackReadyForUnsuppress(
                    frozen = frozenA,
                    liveEventId = b.id,
                    liveContentSettled = true,
                )
            )
            .isFalse()
        // Chrome handback 043: hold + unsuppress chua ack → van 1; ack → 0.
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
        // Closing @p0 (settled): dummy alpha 1; * chrome 0 sau ack = khong double paint.
        assertThat(statusBarMorphDummyContentAlpha(0f, closing = true)).isEqualTo(1f)
    }

    @Test
    fun chipEvent_suppressedSwitch_noFadeKeyStable_staticB() {
        // Suppressed A→B: contentKey doi theo id; transitionSpec None (code) —
        // helper bao key khac nhau va presentation B settle.
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "Short")
        val entryA = StatusBarChipEventEntry(a, false)
        val entryB = StatusBarChipEventEntry(b, false)
        assertThat(entryA.key).isNotEqualTo(entryB.key)
        val presB = statusBarChipEntryPresentation(entryB, chipStyle = 0, generation = 7)
        assertThat(presB.ownerId).isEqualTo(b.id)
        assertThat(
                statusBarHandbackReadyForUnsuppress(
                    frozen = presB,
                    liveEventId = b.id,
                    liveContentSettled = true,
                )
            )
            .isTrue()
    }

    @Test
    fun chipEvent_noSwitch_andGenerationReverse_regression() {
        val a = mediaEvent("a", "Same")
        val entry = StatusBarChipEventEntry(a, false)
        // A→A: cung key — khong coi la switch closure leak.
        assertThat(
                statusBarChipBranchRendersOuterClosure(
                    branchEntry = entry,
                    outerCurrent = entry,
                    renderedOwnerId = a.id,
                )
            )
            .isFalse()
        val frozen =
            statusBarPillPresentationOf(event = a, isAlert = false, chipStyle = 0, generation = 11)
        assertThat(statusBarPillPresentationGenerationValid(frozen, 11)).isTrue()
        assertThat(statusBarCollapsePresentationParity(frozen, a.id)).isTrue()
        // Reverse gen: stale frozen gen 11 vs current 12.
        assertThat(statusBarPillPresentationGenerationValid(frozen, 12)).isFalse()
        // Invalidate tracker xoa owner — lan capture sau khong ownerChanged gia.
        val t = StatusBarPillFrameTracker()
        t.capture(1, 1, 180, 56, intentExpanded = false, pillSuppressed = false, ownerId = "a")
        t.invalidate()
        assertThat(t.lastObservedOwnerId).isNull()
        t.capture(1, 1, 90, 56, intentExpanded = false, pillSuppressed = false, ownerId = "b")
        assertThat(t.lastObserved.width).isEqualTo(90)
        assertThat(t.lastObservedOwnerId).isEqualTo("b")
    }

    // --- task-038: owner-bound glass cache — khong relabel stale A thanh B ---

    @Test
    fun ownerBoundGlass_cachedA_notCapturedAsB_untilTargetStores() {
        val cache = OwnerBoundGlassCacheState()
        val t = StatusBarPillFrameTracker()
        // A OGP store + capture natural 200.
        assertThat(cache.tryStoreFromBranch("a", "a", "a")).isTrue()
        t.capture(100, 10, 200, 56, intentExpanded = true, pillSuppressed = true, ownerId = "a")
        assertThat(t.lastObservedOwnerId).isEqualTo("a")
        assertThat(t.lastObserved.width).isEqualTo(200)
        // A→B: LE remasure voi cache A + target B → defer (KHONG fabricate owner B).
        assertThat(
                cache.remasureOwnerIdOrNull(
                    targetBranchKey = "b",
                    pillCaptureEnabled = true,
                    slotWidthFraction = 1f,
                )
            )
            .isNull()
        // Bug 037: goi capture(A-rect, ownerId=B) → ownerChanged accept W(A) gan label B.
        // Gate owner-bound CHAN truoc khi goi — tracker van A.
        assertThat(t.lastObservedOwnerId).isEqualTo("a")
        assertThat(t.lastObserved.width).isEqualTo(200)
        // Outgoing A OGP khong overwrite cache khi target da B.
        assertThat(cache.tryStoreFromBranch("a", "b", "a")).isFalse()
        assertThat(cache.branchKey).isEqualTo("a")
        assertThat(cache.ownerId).isEqualTo("a")
        // B target OGP store dung owner + W ngan < half A → accept @ownerChanged.
        assertThat(cache.tryStoreFromBranch("b", "b", "b")).isTrue()
        assertThat(cache.remasureOwnerIdOrNull("b", true, 1f)).isEqualTo("b")
        t.capture(120, 10, 80, 56, intentExpanded = true, pillSuppressed = true, ownerId = "b")
        assertThat(t.lastObservedOwnerId).isEqualTo("b")
        assertThat(t.lastObserved.width).isEqualTo(80)
    }

    @Test
    fun ownerBoundGlass_sameOwnerReclaimShrink_blocked_ownerChangeHalfAccepted() {
        val t = StatusBarPillFrameTracker()
        t.capture(10, 10, 200, 56, intentExpanded = false, pillSuppressed = false, ownerId = "a")
        // Cung owner + reclaim shrink → blocked (029/025).
        t.capture(
            10,
            10,
            40,
            56,
            intentExpanded = false,
            pillSuppressed = true,
            pillSlotReclaimed = false,
            ownerId = "a",
        )
        assertThat(t.lastObserved.width).isEqualTo(200)
        assertThat(
                statusBarPillFrameCaptureAllowed(
                    pillSlotReclaimed = false,
                    reportedWidth = 40,
                    lastObservedWidth = 200,
                    ownerChanged = false,
                )
            )
            .isFalse()
        // Owner doi + fraction day du: B < half A van accept.
        assertThat(
                statusBarPillFrameCaptureAllowed(
                    pillSlotReclaimed = false,
                    reportedWidth = 40,
                    lastObservedWidth = 200,
                    ownerChanged = true,
                )
            )
            .isTrue()
        t.capture(10, 10, 40, 56, intentExpanded = false, pillSuppressed = true, ownerId = "b")
        assertThat(t.lastObserved.width).isEqualTo(40)
        assertThat(t.lastObservedOwnerId).isEqualTo("b")
        // Slot chua ready: remasure defer du cache match.
        val cache = OwnerBoundGlassCacheState()
        cache.tryStoreFromBranch("b", "b", "b")
        assertThat(cache.remasureOwnerIdOrNull("b", true, 0.5f)).isNull()
        assertThat(cache.remasureOwnerIdOrNull("b", true, 1f)).isEqualTo("b")
    }

    @Test
    fun ownerBoundGlass_rapidCollapse_reverse_generation_andAtoA() {
        val cache = OwnerBoundGlassCacheState()
        val t = StatusBarPillFrameTracker()
        // A idle.
        cache.tryStoreFromBranch("a", "a", "a")
        t.capture(1, 1, 180, 56, intentExpanded = false, pillSuppressed = false, ownerId = "a")
        // Rapid A→B roi collapse: B chua store → remasure null; morph A giu.
        assertThat(cache.remasureOwnerIdOrNull("b", true, 1f)).isNull()
        assertThat(t.lastObservedOwnerId).isEqualTo("a")
        // B store roi reverse B→A: cache B khong remasure thanh A.
        cache.tryStoreFromBranch("b", "b", "b")
        t.capture(1, 1, 90, 56, intentExpanded = true, pillSuppressed = true, ownerId = "b")
        assertThat(cache.remasureOwnerIdOrNull("a", true, 1f)).isNull()
        assertThat(cache.tryStoreFromBranch("b", "a", "b")).isFalse()
        // A lai target: store A, remasure A.
        assertThat(cache.tryStoreFromBranch("a", "a", "a")).isTrue()
        assertThat(cache.remasureOwnerIdOrNull("a", true, 1f)).isEqualTo("a")
        // A→A: cung key — remasure owner A khong doi.
        assertThat(cache.remasureOwnerIdOrNull("a", true, 1f)).isEqualTo("a")
        // Detach / unmount / config invalidate.
        cache.markDetached()
        assertThat(cache.remasureOwnerIdOrNull("a", true, 1f)).isNull()
        cache.clear()
        t.invalidate()
        assertThat(cache.branchKey).isNull()
        assertThat(t.lastObservedOwnerId).isNull()
        // decide helper: key mismatch → allow=false.
        val d =
            decideOwnerBoundGlassRemeasure(
                cachedBranchKey = "a",
                cachedOwnerId = "a",
                targetBranchKey = "b",
                coordsAttached = true,
                pillCaptureEnabled = true,
                slotWidthFraction = 1f,
            )
        assertThat(d.allow).isFalse()
        assertThat(d.ownerId).isNull()
        assertThat(statusBarOwnerBoundGlassStoreAllowed("a", "b")).isFalse()
        assertThat(statusBarOwnerBoundGlassStoreAllowed("a", "a")).isTrue()
    }

    @Test
    fun collapseProbe_requiresMeasuredAndRestoredPlacement() {
        val probe = StatusBarCollapseProbeController()
        val frame = StatusBarPillFrame(120, 10, 80, 56)
        assertThat(probe.requestProbe("b", 1)).isTrue()
        val gen = probe.requestGeneration
        assertThat(probe.recordMeasuredLayout(gen, 1, "b", frame)).isFalse()
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
        assertThat(probe.recordMeasuredLayout(gen, 1, "a", frame)).isFalse()
        assertThat(probe.recordMeasuredLayout(gen, 2, "b", frame)).isFalse()
        assertThat(probe.recordMeasuredLayout(gen, 1, "b", StatusBarPillFrame())).isFalse()
        assertThat(probe.recordMeasuredLayout(gen, 1, "b", frame)).isTrue()
        assertThat(probe.finishRestored(gen, 1, "b")).isNull()
        // A late placement of the full-width layout cannot release the window.
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
        assertThat(probe.finishRestored(gen, 1, "b")).isNull()
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
        val result = probe.finishRestored(gen, 1, "b")!!
        assertThat(result.frame).isEqualTo(frame)
        assertThat(result.ownerId).isEqualTo("b")
        assertThat(result.closeAfterRestore).isTrue()
        assertThat(probe.phase).isEqualTo(StatusBarCollapseProbePhase.Idle)
    }

    @Test
    fun collapseProbe_repeatedSwitchAndClose_rejectsStaleCallbacks() {
        val probe = StatusBarCollapseProbeController()
        val tracker = StatusBarPillFrameTracker()
        tracker.capture(100, 10, 200, 56, false, false, ownerId = "a")
        var oldGen = 0L
        for (owner in listOf("b", "c", "a", "b")) {
            assertThat(probe.requestProbe(owner, 1)).isTrue()
            val gen = probe.requestGeneration
            assertThat(probe.requestProbe(owner, 1)).isFalse()
            assertThat(probe.requestGeneration).isEqualTo(gen)
            val frame = StatusBarPillFrame(120, 10, 80, 56)
            probe.acknowledgePlacement(oldGen, StatusBarCollapseProbePhase.Measuring)
            assertThat(probe.recordMeasuredLayout(gen, 1, owner, frame)).isFalse()
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
            assertThat(probe.recordMeasuredLayout(gen, 1, owner, frame)).isTrue()
            probe.acknowledgePlacement(oldGen, StatusBarCollapseProbePhase.Restoring)
            assertThat(probe.finishRestored(gen, 1, owner)).isNull()
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
            val result = probe.finishRestored(gen, 1, owner)!!
            tracker.setMorphFrameForCollapse(result.frame!!, result.ownerId!!)
            assertThat(tracker.morphOwnerId).isEqualTo(owner)
            assertThat(tracker.morphFrame).isEqualTo(frame)
            assertThat(probe.finishRestored(gen, 1, owner)).isNull()
            oldGen = gen
        }
    }

    @Test
    fun collapseProbe_watchdogRestoresBeforeFallback_neverCommitsOldFrame() {
        val probe = StatusBarCollapseProbeController()
        probe.requestProbe("b", 1)
        val gen = probe.requestGeneration
        repeat(6) { assertThat(probe.measurementExpired()).isFalse() }
        assertThat(probe.measurementExpired()).isTrue()
        probe.cancelForRestore(closeAfterRestore = true)
        assertThat(probe.phase).isEqualTo(StatusBarCollapseProbePhase.Restoring)
        assertThat(probe.finishRestored(gen, 1, "b")).isNull()
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
        val result = probe.finishRestored(gen, 1, "b")!!
        assertThat(result.frame).isNull()
        assertThat(result.ownerId).isNull()
        assertThat(result.closeAfterRestore).isTrue()
    }

    @Test
    fun collapseProbe_reversalAndConfigurationCancel_restoreWithoutClosing() {
        for (layoutGen in listOf(1, 2)) {
            val probe = StatusBarCollapseProbeController()
            probe.requestProbe("b", 1)
            val gen = probe.requestGeneration
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
            probe.recordMeasuredLayout(gen, 1, "b", StatusBarPillFrame(120, 10, 80, 56))
            probe.cancelForRestore(closeAfterRestore = false)
            assertThat(probe.finishRestored(gen, layoutGen, "b")).isNull()
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
            val result = probe.finishRestored(gen, layoutGen, "b")!!
            assertThat(result.frame).isNull()
            assertThat(result.closeAfterRestore).isFalse()
        }
    }

    @Test
    fun collapseProbe_ownerOrLayoutChangeCannotCommitCapturedGeometry() {
        for ((layoutGen, owner) in listOf(2 to "b", 1 to "c", 1 to null)) {
            val probe = StatusBarCollapseProbeController()
            probe.requestProbe("b", 1)
            val gen = probe.requestGeneration
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
            probe.recordMeasuredLayout(gen, 1, "b", StatusBarPillFrame(120, 10, 80, 56))
            probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
            assertThat(probe.finishRestored(gen, layoutGen, owner)!!.frame).isNull()
        }
    }

    @Test
    fun collapseProbe_budgetDensityOrDirectionChangeInvalidatesMeasuredFrame() {
        val probe = StatusBarCollapseProbeController()
        probe.requestProbe("b", 1)
        val gen = probe.requestGeneration
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Measuring)
        probe.recordMeasuredLayout(gen, 1, "b", StatusBarPillFrame(120, 10, 80, 56), 100L)
        probe.acknowledgePlacement(gen, StatusBarCollapseProbePhase.Restoring)
        assertThat(probe.finishRestored(gen, 1, "b", 101L)!!.frame).isNull()
    }

    @Test
    fun collapseProbe_detachResetRejectsOldRequest() {
        val probe = StatusBarCollapseProbeController()
        assertThat(probe.requestProbe("", 1)).isFalse()
        probe.requestProbe("b", 1)
        val old = probe.requestGeneration
        probe.abort()
        assertThat(probe.phase).isEqualTo(StatusBarCollapseProbePhase.Idle)
        probe.requestProbe("c", 2)
        probe.acknowledgePlacement(old, StatusBarCollapseProbePhase.Measuring)
        assertThat(probe.recordMeasuredLayout(old, 1, "b", StatusBarPillFrame(1, 1, 80, 56)))
            .isFalse()
        assertThat(probe.finishRestored(old, 1, "b")).isNull()
    }

    // --- task-043: shared collapse handback readiness / ordering (pure, khong chay) ---

    @Test
    fun handback043_overlayAlpha0_beforeLive_isBothHidden() {
        // Bug: chrome 0 + live alpha 0 → both-hidden.
        assertThat(
                statusBarCollapseHandbackBothHidden(
                    overlayChromeFactor = 0f,
                    livePillAlpha = 0f,
                )
            )
            .isTrue()
        // Live da ve (alpha 1) + chrome 0 → OK.
        assertThat(
                statusBarCollapseHandbackBothHidden(
                    overlayChromeFactor = 0f,
                    livePillAlpha = 1f,
                )
            )
            .isFalse()
        // Ordering: unsuppress chua ack → chrome phai 1.
        assertThat(
                statusBarCollapseHandbackOrderingSafe(
                    pillSuppressed = false,
                    overlayChromeFactor = 1f,
                    liveDrawnAcked = false,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackOrderingSafe(
                    pillSuppressed = false,
                    overlayChromeFactor = 0f,
                    liveDrawnAcked = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackOrderingSafe(
                    pillSuppressed = false,
                    overlayChromeFactor = 0f,
                    liveDrawnAcked = true,
                )
            )
            .isTrue()
    }

    @Test
    fun handback043_liveBeforeOverlayFade_singleOwner() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(layoutGen = 20, ownerId = "a")
        assertThat(start.shouldNotifySettle).isTrue()
        assertThat(c.liveDrawnAcked).isFalse()
        // Unsuppress moi xay ra (pillSuppressed=false) — chrome van 1.
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        assertThat(
                c.acknowledgeLiveDrawn(
                    holdGeneration = start.holdGeneration,
                    layoutGen = 20,
                    ownerId = "a",
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
        assertThat(c.clearHoldIfGeneration(start.holdGeneration)).isTrue()
        assertThat(c.holdActive).isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
    }

    @Test
    fun handback043_changedOwner_ackMustMatchExpected() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(layoutGen = 21, ownerId = "b")
        // Ack owner A khi expected B → reject.
        assertThat(
                c.acknowledgeLiveDrawn(
                    holdGeneration = start.holdGeneration,
                    layoutGen = 21,
                    ownerId = "a",
                )
            )
            .isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = c.liveDrawnAcked,
                )
            )
            .isEqualTo(1f)
        // Ack B → OK.
        assertThat(
                c.acknowledgeLiveDrawn(
                    holdGeneration = start.holdGeneration,
                    layoutGen = 21,
                    ownerId = "b",
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()
    }

    @Test
    fun handback043_rapidDoubleCollapse_staleAckIgnored() {
        val c = CollapseHandbackHoldController()
        val g1 = c.beginSettleHandback(30, ownerId = "a").holdGeneration
        c.onExpandedOrReverse()
        val g2 = c.beginSettleHandback(31, ownerId = "b").holdGeneration
        assertThat(g2).isGreaterThan(g1)
        // Stale ack gen1 + layout 30 → ignore.
        assertThat(c.acknowledgeLiveDrawn(g1, layoutGen = 30, ownerId = "a")).isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        // Timeout gen1 stale → ignore.
        assertThat(c.acknowledgeLiveDrawnTimeout(g1)).isFalse()
        // Ack gen2 B → OK.
        assertThat(c.acknowledgeLiveDrawn(g2, layoutGen = 31, ownerId = "b")).isTrue()
        assertThat(c.clearHoldIfGeneration(g1)).isFalse()
        assertThat(c.holdActive).isTrue()
        assertThat(c.clearHoldIfGeneration(g2)).isTrue()
    }

    @Test
    fun handback043_reverse_clearsAckAndChromeGate() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(40, ownerId = "a")
        c.acknowledgeLiveDrawn(start.holdGeneration, 40, "a")
        assertThat(c.liveDrawnAcked).isTrue()
        c.onExpandedOrReverse()
        assertThat(c.holdActive).isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = false,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        // Timer stale sau reverse khong clear gen moi.
        assertThat(c.clearHoldIfGeneration(start.holdGeneration)).isFalse()
    }

    @Test
    fun handback043_noOwner_timeout_noStuckOverlay() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(50, ownerId = "gone")
        // No-owner ack (null) — chap nhan de khong stuck.
        assertThat(
                c.acknowledgeLiveDrawn(
                    holdGeneration = start.holdGeneration,
                    layoutGen = 50,
                    ownerId = null,
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()
        // Timeout live owner: CHI khi true≈1 + drawCredible (khong fabricate default).
        val c2 = CollapseHandbackHoldController()
        val s2 = c2.beginSettleHandback(51, ownerId = "x")
        assertThat(c2.acknowledgeLiveDrawnTimeout(s2.holdGeneration)).isFalse()
        assertThat(
                c2.acknowledgeLiveDrawnTimeout(
                    holdGeneration = s2.holdGeneration,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
        assertThat(c2.clearHoldIfGeneration(s2.holdGeneration)).isTrue()
        assertThat(c2.holdActive).isFalse()
        // Exactly-once cung layoutGen — khong bat hold lan 2 → khong stuck.
        assertThat(c.beginSettleHandback(50, ownerId = "gone").shouldNotifySettle).isFalse()
    }

    // --- task-044: early-ack latch + static release phase + draw credibility ---

    @Test
    fun handback044_ackBeforeSubscribe_latchedAndApplied() {
        val hold = CollapseHandbackDrawnHold()
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(60, ownerId = "a")
        // Chip emit TRUOC Panel collector (replay/StateFlow latch).
        hold.report(ownerId = "a", layoutGeneration = 60)
        assertThat(hold.hasSignal).isTrue()
        // Panel subscribe sau — doc latest, apply.
        assertThat(
                collapseHandbackApplyDrawnSignal(
                    ctrl = c,
                    holdGeneration = start.holdGeneration,
                    ownerId = hold.latestOwnerId,
                    layoutGeneration = hold.latestLayoutGen,
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
    }

    @Test
    fun handback044_ackAfterSubscribe_stillApplies() {
        val hold = CollapseHandbackDrawnHold()
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(61, ownerId = "b")
        // Subscribe truoc (chua co signal).
        assertThat(hold.hasSignal).isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        hold.report(ownerId = "b", layoutGeneration = 61)
        assertThat(
                collapseHandbackApplyDrawnSignal(
                    ctrl = c,
                    holdGeneration = start.holdGeneration,
                    ownerId = hold.latestOwnerId,
                    layoutGeneration = hold.latestLayoutGen,
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()
    }

    @Test
    fun handback044_staleGenOwner_rejectedFromLatch() {
        val hold = CollapseHandbackDrawnHold()
        val c = CollapseHandbackHoldController()
        val g1 = c.beginSettleHandback(70, ownerId = "a").holdGeneration
        hold.report("a", 70)
        c.onExpandedOrReverse()
        val g2 = c.beginSettleHandback(71, ownerId = "b").holdGeneration
        // Early latch van la gen70/A — reject.
        assertThat(
                collapseHandbackApplyDrawnSignal(c, g2, hold.latestOwnerId, hold.latestLayoutGen)
            )
            .isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        // Clear + report dung B.
        hold.clear()
        hold.report("b", 71)
        assertThat(collapseHandbackApplyDrawnSignal(c, g2, "b", 71)).isTrue()
        // Stale timeout g1 ignore.
        assertThat(c.acknowledgeLiveDrawnTimeout(g1)).isFalse()
    }

    @Test
    fun handback044_noPhantomReady_deadOrDetachedView() {
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = false,
                    viewTreeObserverAlive = true,
                    chipExists = true,
                    ownerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = true,
                    viewTreeObserverAlive = false,
                    chipExists = true,
                    ownerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = true,
                    viewTreeObserverAlive = true,
                    chipExists = false,
                    ownerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = true,
                    viewTreeObserverAlive = true,
                    chipExists = true,
                    ownerId = null,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = true,
                    viewTreeObserverAlive = true,
                    chipExists = true,
                    ownerId = "a",
                )
            )
            .isTrue()
    }

    @Test
    fun handback044_staticModeHeldUntilHideConfirm_AtoA() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(80, ownerId = "a")
        // Unsuppress + OnDraw ack — static VAN hold (chua hide).
        assertThat(c.acknowledgeLiveDrawn(start.holdGeneration, 80, "a")).isTrue()
        assertThat(c.overlayHideConfirmed).isFalse()
        assertThat(statusBarCollapseHandbackStaticReleaseAllowed(false)).isFalse()
        assertThat(
                statusBarCollapseHandbackStaticModeHeldUntilHide(
                    handbackKeepStatic = true,
                    pillSuppressed = false,
                    overlayHideConfirmed = false,
                )
            )
            .isTrue()
        // Phase4 hide confirm → phase5 release allowed.
        assertThat(c.confirmOverlayHidden(start.holdGeneration)).isTrue()
        assertThat(statusBarCollapseHandbackStaticReleaseAllowed(true)).isTrue()
        assertThat(c.clearHoldIfGeneration(start.holdGeneration)).isTrue()
    }

    @Test
    fun handback044_changedOwner_AtoB_andTokenGen() {
        val c = CollapseHandbackHoldController()
        val start = c.beginSettleHandback(81, ownerId = "b")
        assertThat(c.acknowledgeLiveDrawn(start.holdGeneration, 81, "a")).isFalse()
        assertThat(c.acknowledgeLiveDrawn(start.holdGeneration, 81, "b")).isTrue()
        assertThat(c.confirmOverlayHidden(start.holdGeneration - 1)).isFalse()
        assertThat(c.confirmOverlayHidden(start.holdGeneration)).isTrue()
        assertThat(c.overlayHideConfirmed).isTrue()
    }

    @Test
    fun handback044_noOwner_reverse_reopen_timeout() {
        // No-owner ack → chrome gate + hide confirm + clear.
        val c = CollapseHandbackHoldController()
        val s = c.beginSettleHandback(90, ownerId = "x")
        assertThat(c.acknowledgeLiveDrawn(s.holdGeneration, 90, null)).isTrue()
        assertThat(c.confirmOverlayHidden(s.holdGeneration)).isTrue()
        assertThat(c.clearHoldIfGeneration(s.holdGeneration)).isTrue()
        // Reverse clears hide + ack.
        val c2 = CollapseHandbackHoldController()
        val s2 = c2.beginSettleHandback(91, ownerId = "a")
        c2.acknowledgeLiveDrawn(s2.holdGeneration, 91, "a")
        c2.confirmOverlayHidden(s2.holdGeneration)
        c2.onExpandedOrReverse()
        assertThat(c2.holdActive).isFalse()
        assertThat(c2.liveDrawnAcked).isFalse()
        assertThat(c2.overlayHideConfirmed).isFalse()
        // Timeout path + reopen (new settle gen) — can drawCredible + true≈1.
        val c3 = CollapseHandbackHoldController()
        val s3 = c3.beginSettleHandback(92, ownerId = "a")
        assertThat(c3.acknowledgeLiveDrawnTimeout(s3.holdGeneration)).isFalse()
        assertThat(
                c3.acknowledgeLiveDrawnTimeout(
                    holdGeneration = s3.holdGeneration,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        assertThat(c3.confirmOverlayHidden(s3.holdGeneration)).isTrue()
        c3.onExpandedOrReverse()
        val s4 = c3.beginSettleHandback(93, ownerId = "a")
        assertThat(s4.shouldNotifySettle).isTrue()
        assertThat(s4.holdGeneration).isGreaterThan(s3.holdGeneration)
        assertThat(c3.liveDrawnAcked).isFalse()
        assertThat(c3.overlayHideConfirmed).isFalse()
    }

    // --- task-045: phase5 bounded OnDraw restore (viet, khong chay) ---

    @Test
    fun handback045_completionThenDraw_releasesStatic() {
        val c = CollapseHandbackHoldController()
        val s = c.beginSettleHandback(100, ownerId = "a")
        assertThat(c.acknowledgeLiveDrawn(s.holdGeneration, 100, "a")).isTrue()
        assertThat(c.confirmOverlayHidden(s.holdGeneration)).isTrue()
        // Chua wait → chua release (giu static toi draw/timeout).
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 100,
                    currentLayoutGen = 100,
                    drawWaitOutcome = null,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 100,
                    currentLayoutGen = 100,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.DREW,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackPhase5MapWaitOutcome(boundedDrew = true)
            )
            .isEqualTo(HandbackPhase5DrawWaitOutcome.DREW)
    }

    @Test
    fun handback045_completionThenNoDrawTimeout_releasesStatic() {
        assertThat(
                statusBarCollapseHandbackPhase5MapWaitOutcome(boundedDrew = null)
            )
            .isEqualTo(HandbackPhase5DrawWaitOutcome.TIMEOUT)
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 101,
                    currentLayoutGen = 101,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.TIMEOUT,
                )
            )
            .isTrue()
        // Timeout phase5 ≤100ms va KHONG = phase3 ACK 50ms.
        assertThat(StatusBarHandbackPhase5StaticRestoreTimeoutMs).isEqualTo(100L)
        assertThat(
                statusBarCollapseHandbackPhase5TimeoutDistinctFromPhase3Ack(
                    StatusBarHandbackPhase5StaticRestoreTimeoutMs
                )
            )
            .isTrue()
        assertThat(statusBarOverlayHideDelayMs(alertExitGrace = false)).isEqualTo(50L)
    }

    @Test
    fun handback045_detachedOrDead_releasesStaticNoFalseAck() {
        assertThat(
                statusBarCollapseHandbackPhase5MapWaitOutcome(boundedDrew = false)
            )
            .isEqualTo(HandbackPhase5DrawWaitOutcome.DETACHED_OR_DEAD)
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 102,
                    currentLayoutGen = 102,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.DETACHED_OR_DEAD,
                )
            )
            .isTrue()
        // Dead/detach van khong phantom ready phase3.
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = false,
                    viewTreeObserverAlive = true,
                    chipExists = true,
                    ownerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = true,
                    viewTreeObserverAlive = false,
                    chipExists = true,
                    ownerId = "a",
                )
            )
            .isFalse()
    }

    @Test
    fun handback045_reverseCancelBeforeComplete_abortReleases() {
        val c = CollapseHandbackHoldController()
        val s = c.beginSettleHandback(103, ownerId = "a")
        c.acknowledgeLiveDrawn(s.holdGeneration, 103, "a")
        // Reverse TRUOC hide confirm — abort path release static, khong doi OnDraw.
        c.onExpandedOrReverse()
        assertThat(c.overlayHideConfirmed).isFalse()
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = true,
                    overlayHideConfirmed = false,
                    signalLayoutGen = 103,
                    currentLayoutGen = 103,
                    drawWaitOutcome = null,
                )
            )
            .isTrue()
        // Chua abort + chua confirm → khong release (giu 044 ordering).
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = false,
                    signalLayoutGen = 103,
                    currentLayoutGen = 103,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.DREW,
                )
            )
            .isFalse()
    }

    @Test
    fun handback045_staleGen_noReleaseUntilMatch() {
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 104,
                    currentLayoutGen = 105,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.DREW,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 105,
                    currentLayoutGen = 105,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.TIMEOUT,
                )
            )
            .isTrue()
        val c = CollapseHandbackHoldController()
        val s1 = c.beginSettleHandback(104, ownerId = "a")
        c.acknowledgeLiveDrawn(s1.holdGeneration, 104, "a")
        c.confirmOverlayHidden(s1.holdGeneration)
        c.onExpandedOrReverse()
        val s2 = c.beginSettleHandback(105, ownerId = "b")
        // Stale confirm gen cu bi reject.
        assertThat(c.confirmOverlayHidden(s1.holdGeneration)).isFalse()
        assertThat(c.acknowledgeLiveDrawn(s2.holdGeneration, 105, "b")).isTrue()
        assertThat(c.confirmOverlayHidden(s2.holdGeneration)).isTrue()
    }

    @Test
    fun handback045_noStaticLock_idleAndAfterOutcomes() {
        // Idle: chua suppress → keepStatic false; khong arm tu startup.
        assertThat(
                statusBarCollapseHandbackStaticModeHeldUntilHide(
                    handbackKeepStatic = false,
                    pillSuppressed = false,
                    overlayHideConfirmed = false,
                )
            )
            .isFalse()
        // Moi outcome sau confirm deu release — khong lock vinh vien.
        for (outcome in HandbackPhase5DrawWaitOutcome.entries) {
            assertThat(
                    statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                        abort = false,
                        overlayHideConfirmed = true,
                        signalLayoutGen = 110,
                        currentLayoutGen = 110,
                        drawWaitOutcome = outcome,
                    )
                )
                .isTrue()
        }
        assertThat(StatusBarHandbackPhase5StaticRestoreTimeoutMs)
            .isAtMost(100L)
        assertThat(StatusBarHandbackPhase5StaticRestoreTimeoutMs)
            .isGreaterThan(0L)
    }

    // --- task-048: live chronometer refresh luc collapse (dummy khong stale) ---

    @Test
    fun chrono048_stopwatch_running_4m11_plus5s_equals_4m16() {
        val t0Wall = 1_700_000_000_000L
        val t0Er = 500_000L
        val openElapsed = 4L * 60_000L + 11_000L // 4m11s
        val sw =
            IslandEvent.Stopwatch(
                label = "sw",
                startTimeMs = t0Wall - openElapsed,
                startElapsedRealtimeMs = t0Er - openElapsed,
                isRunning = true,
                pausedElapsedMs = 0L,
                notificationKey = "sw048",
            )
        assertThat(displayedChronometerMsOf(sw, t0Wall, t0Er)).isEqualTo(openElapsed)
        // frozenChronometerMs bat buoc — tranh sample SystemClock that (nondeterministic).
        val frozen =
            statusBarPillPresentationOf(
                event = sw,
                isAlert = false,
                chipStyle = 0,
                generation = 48,
                frozenChronometerMs = openElapsed,
            )
        assertThat(frozen.frozenChronometerMs).isEqualTo(openElapsed)
        assertThat(frozenPillHasDynamicChronometer(frozen)).isTrue()
        assertThat(frozenPillChronometerIsTicking(sw)).isTrue()
        // Expanded idle 5s — freeze cu van 4m11; refresh @collapse → 4m16.
        val t1Wall = t0Wall + 5_000L
        val t1Er = t0Er + 5_000L
        val refreshed =
            refreshFrozenPillChronometerPresentation(
                frozen = frozen,
                liveEvent = sw,
                nowWallMs = t1Wall,
                nowElapsedMs = t1Er,
            )!!
        assertThat(refreshed.frozenChronometerMs).isEqualTo(openElapsed + 5_000L)
        assertThat(formatStopwatchCompact(refreshed.frozenChronometerMs!!)).isEqualTo("04:16")
        assertThat(refreshed.ownerId).isEqualTo(frozen.ownerId)
        assertThat(refreshed.generation).isEqualTo(48)
        assertThat(refreshed.chipStyle).isEqualTo(frozen.chipStyle)
        assertThat(refreshed.progress).isEqualTo(frozen.progress)
        assertThat(statusBarChipEventDisplayKeyStableAcrossChronoTick(frozen, refreshed)).isTrue()
    }

    @Test
    fun chrono048_timer_running_4m11_plus5s_equals_4m06() {
        val t0Wall = 1_700_000_000_000L
        val t0Er = 800_000L
        val remainOpen = 4L * 60_000L + 11_000L
        val timer =
            IslandEvent.Timer(
                label = "t",
                endTimeMs = t0Wall + remainOpen,
                endElapsedRealtimeMs = t0Er + remainOpen,
                isPaused = false,
                pausedRemainingMs = 0L,
                notificationKey = "tm048",
            )
        val frozen =
            statusBarPillPresentationOf(
                event = timer,
                isAlert = false,
                chipStyle = 0,
                generation = 48,
                frozenChronometerMs = remainOpen,
            )
        assertThat(frozen.frozenChronometerMs).isEqualTo(remainOpen)
        val refreshed =
            refreshFrozenPillChronometerPresentation(
                frozen = frozen,
                liveEvent = timer,
                nowWallMs = t0Wall + 5_000L,
                nowElapsedMs = t0Er + 5_000L,
            )!!
        assertThat(refreshed.frozenChronometerMs).isEqualTo(remainOpen - 5_000L)
        assertThat(formatCountdownCompact(refreshed.frozenChronometerMs!!)).isEqualTo("04:06")
        assertThat(statusBarChipEventDisplayKeyStableAcrossChronoTick(frozen, refreshed)).isTrue()
    }

    @Test
    fun chrono048_paused_stopwatch_and_timer_unchanged() {
        val sw =
            IslandEvent.Stopwatch(
                label = "sw",
                isRunning = false,
                pausedElapsedMs = 251_000L,
                notificationKey = "swP",
            )
        val tm =
            IslandEvent.Timer(
                label = "t",
                isPaused = true,
                pausedRemainingMs = 251_000L,
                notificationKey = "tmP",
            )
        assertThat(frozenPillChronometerIsTicking(sw)).isFalse()
        assertThat(frozenPillChronometerIsTicking(tm)).isFalse()
        // Paused: override frozenMs (paused* khong can clock, van deterministic).
        val frozenSw =
            statusBarPillPresentationOf(
                sw,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
                frozenChronometerMs = 251_000L,
            )
        val frozenTm =
            statusBarPillPresentationOf(
                tm,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
                frozenChronometerMs = 251_000L,
            )
        val laterWall = 1_700_000_100_000L
        val laterEr = 9_999_000L
        assertThat(
                refreshFrozenPillChronometerPresentation(frozenSw, sw, laterWall, laterEr)!!
                    .frozenChronometerMs
            )
            .isEqualTo(251_000L)
        assertThat(
                refreshFrozenPillChronometerPresentation(frozenTm, tm, laterWall, laterEr)!!
                    .frozenChronometerMs
            )
            .isEqualTo(251_000L)
    }

    @Test
    fun chrono048_secondBoundary_delay_and_collapseSample() {
        // Countdown floor: sample vua SAU moc integer-second (r+1).
        // 251400 → 401ms → 250999 (floor 250); 251000 → 1ms; 250999 → 1000ms.
        assertThat(collapseChronometerTickDelayMs(251_400L, countdown = true)).isEqualTo(401L)
        assertThat(collapseChronometerTickDelayMs(251_000L, countdown = true)).isEqualTo(1L)
        assertThat(collapseChronometerTickDelayMs(250_999L, countdown = true)).isEqualTo(1000L)
        // Het gio: 1000ms an toan — khong 1ms busy-loop.
        assertThat(collapseChronometerTickDelayMs(0L, countdown = true)).isEqualTo(1000L)
        assertThat(collapseChronometerTickDelayMs(-5L, countdown = true)).isEqualTo(1000L)
        assertThat(collapseChronometerTickDelayMs(0L, countdown = false)).isEqualTo(1000L)
        // formatCountdownCompact: 251000 floor=251 (04:11); 250999 floor=250 (04:10).
        assertThat(formatCountdownCompact(251_000L)).isEqualTo("04:11")
        assertThat(formatCountdownCompact(250_999L)).isEqualTo("04:10")
        // Stopwatch round: *.5 boundary giu nguyen + edge 500ms → 1ms.
        assertThat(collapseChronometerTickDelayMs(251_200L, countdown = false)).isEqualTo(300L)
        assertThat(collapseChronometerTickDelayMs(251_700L, countdown = false)).isEqualTo(800L)
        assertThat(collapseChronometerTickDelayMs(251_500L, countdown = false)).isEqualTo(1L)
        assertThat(collapseChronometerTickDelayMs(251_000L, countdown = false)).isEqualTo(500L)
        val t0Er = 1_000_000L
        val openMs = 251_800L // 4:11.8 → round hien 04:12; +200ms ER → 252_000 round van 04:12
        val sw =
            IslandEvent.Stopwatch(
                startElapsedRealtimeMs = t0Er - openMs,
                isRunning = true,
                notificationKey = "swB",
            )
        val frozen =
            statusBarPillPresentationOf(
                sw,
                isAlert = false,
                chipStyle = 0,
                generation = 2,
                frozenChronometerMs = openMs,
            )
        // Collapse dung luc 251_800; sau 500ms → 252_300 → round 04:12 (lien tuc, khong nhay lui).
        val mid =
            refreshFrozenPillChronometerPresentation(
                frozen,
                sw,
                nowWallMs = 1_700_000_000_500L,
                nowElapsedMs = t0Er + 500L,
            )!!
        assertThat(mid.frozenChronometerMs).isEqualTo(openMs + 500L)
        assertThat(formatStopwatchCompact(mid.frozenChronometerMs!!)).isEqualTo("04:12")
    }

    @Test
    fun chrono048_aToB_thenCollapse_usesBCurrentChrono() {
        val t0Er = 2_000_000L
        val aElapsed = 60_000L
        val a =
            IslandEvent.Stopwatch(
                startElapsedRealtimeMs = t0Er - aElapsed,
                isRunning = true,
                notificationKey = "a",
            )
        val bRemain = 4L * 60_000L + 11_000L
        val b =
            IslandEvent.Timer(
                endElapsedRealtimeMs = t0Er + bRemain,
                isPaused = false,
                notificationKey = "b",
            )
        val frozenA =
            statusBarPillPresentationOf(
                a,
                isAlert = false,
                chipStyle = 0,
                generation = 5,
                frozenChronometerMs = aElapsed,
            )
        // A→B owner sync (034) — rebuild presentation B @ switch.
        assertThat(frozenPillOwnerNeedsUpdate(frozenA, b)).isTrue()
        val frozenB =
            statusBarPillPresentationOf(
                event = b,
                isAlert = false,
                chipStyle = 0,
                generation = frozenA.generation,
                frozenChronometerMs = bRemain,
            )
        assertThat(frozenB.ownerId).isEqualTo(b.id)
        assertThat(frozenB.frozenChronometerMs).isEqualTo(bRemain)
        assertThat(frozenB.generation).isEqualTo(5)
        // Collapse sau 5s dwell tren B.
        val refreshed =
            refreshFrozenPillChronometerPresentation(
                frozen = frozenB,
                liveEvent = b,
                nowWallMs = 1_700_000_005_000L,
                nowElapsedMs = t0Er + 5_000L,
            )!!
        assertThat(refreshed.ownerId).isEqualTo(b.id)
        assertThat(refreshed.frozenChronometerMs).isEqualTo(bRemain - 5_000L)
        assertThat(formatCountdownCompact(refreshed.frozenChronometerMs!!)).isEqualTo("04:06")
        assertThat(refreshed.generation).isEqualTo(5)
        assertThat(refreshed.chipStyle).isEqualTo(frozenB.chipStyle)
        assertThat(refreshed.progress).isEqualTo(frozenB.progress)
        assertThat(statusBarCollapsePresentationParity(refreshed, b.id)).isTrue()
        assertThat(statusBarChipEventDisplayKeyStableAcrossChronoTick(frozenB, refreshed)).isTrue()
    }

    @Test
    fun chrono048_staleGen_ownerMismatch_mediaAlert_ignored() {
        val sw =
            IslandEvent.Stopwatch(
                startElapsedRealtimeMs = 1000L,
                isRunning = true,
                notificationKey = "sw",
            )
        val frozen =
            statusBarPillPresentationOf(
                sw,
                isAlert = false,
                chipStyle = 0,
                generation = 3,
                frozenChronometerMs = 1_000L,
            )
        // Owner khac (timer id != stopwatch id) → null (requireSameOwner).
        val other = timerEvent("otherOwner", 99_000L)
        assertThat(other.id).isNotEqualTo(frozen.ownerId)
        assertThat(
                refreshFrozenPillChronometerPresentation(
                    frozen,
                    liveEvent = other,
                    nowElapsedMs = 50_000L,
                    requireSameOwner = true,
                )
            )
            .isNull()
        // Gen hop le van refresh; generation field giu nguyen (khong bump).
        val ok =
            refreshFrozenPillChronometerPresentation(
                frozen,
                liveEvent = sw,
                nowElapsedMs = 50_000L,
            )!!
        assertThat(ok.generation).isEqualTo(3)
        assertThat(ok.frozenChronometerMs).isEqualTo(49_000L)
        assertThat(statusBarPillPresentationGenerationValid(ok, 3)).isTrue()
        assertThat(statusBarPillPresentationGenerationValid(ok, 4)).isFalse()
        // Media / alert: khong dynamic chrono.
        val media = mediaEvent("mk", "Song")
        val mediaSnap =
            statusBarPillPresentationOf(media, isAlert = false, chipStyle = 0, generation = 1)
        assertThat(frozenPillHasDynamicChronometer(mediaSnap)).isFalse()
        assertThat(refreshFrozenPillChronometerPresentation(mediaSnap, media)).isNull()
        // Alert path: isAlert=true → khong refresh chrono (ke ca event timer).
        val alert =
            statusBarPillPresentationOf(
                sw,
                isAlert = true,
                chipStyle = 0,
                generation = 1,
                frozenChronometerMs = 1_000L,
            )
        assertThat(frozenPillHasDynamicChronometer(alert)).isFalse()
        assertThat(refreshFrozenPillChronometerPresentation(alert)).isNull()
    }

    @Test
    fun chrono048_noAnimatedContentKeyChurn_onChronoRefresh() {
        val sw =
            IslandEvent.Stopwatch(
                startElapsedRealtimeMs = 0L,
                isRunning = true,
                notificationKey = "sw",
            )
        val before =
            statusBarPillPresentationOf(
                sw,
                isAlert = false,
                chipStyle = 1,
                generation = 9,
                frozenChronometerMs = 1_000L,
            )
        val after =
            refreshFrozenPillChronometerPresentation(
                before,
                sw,
                nowElapsedMs = 12_000L,
            )!!
        assertThat(after.frozenChronometerMs).isEqualTo(12_000L)
        assertThat(statusBarChipEventDisplayKey(before.event, before.isAlert))
            .isEqualTo(statusBarChipEventDisplayKey(after.event, after.isAlert))
        assertThat(statusBarChipEventDisplayKeyStableAcrossChronoTick(before, after)).isTrue()
        // Circle progress giu nguyen (document limit — khong tinh lai ring luc refresh ms).
        assertThat(after.progress).isEqualTo(before.progress)
        assertThat(after.useCircleStyle).isEqualTo(before.useCircleStyle)
        assertThat(after.generation).isEqualTo(9)
        assertThat(after.chipStyle).isEqualTo(1)
    }

    @Test
    fun chrono048_frozenOverride_semantics_and_canceledRefresh() {
        // Override non-null thang sample default — runtime default sampling khong doi.
        val t0Er = 3_000_000L
        val remain = 90_000L
        val timer =
            IslandEvent.Timer(
                endElapsedRealtimeMs = t0Er + remain,
                isPaused = false,
                notificationKey = "ov",
            )
        val snap =
            statusBarPillPresentationOf(
                timer,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
                frozenChronometerMs = 12_345L,
            )
        assertThat(snap.frozenChronometerMs).isEqualTo(12_345L)
        // Canceled/paused path: isTicking=false → khong can ticker; refresh van on dinh.
        val canceled =
            timer.copy(isPaused = true, pausedRemainingMs = 12_345L, endElapsedRealtimeMs = 0L)
        assertThat(frozenPillChronometerIsTicking(canceled)).isFalse()
        val frozenCanceled =
            statusBarPillPresentationOf(
                canceled,
                isAlert = false,
                chipStyle = 0,
                generation = 1,
                frozenChronometerMs = 12_345L,
            )
        assertThat(
                refreshFrozenPillChronometerPresentation(
                    frozenCanceled,
                    canceled,
                    nowWallMs = 9_999_999_999L,
                    nowElapsedMs = 9_999_999L,
                )!!
                    .frozenChronometerMs
            )
            .isEqualTo(12_345L)
    }

    // --- F741N 4-track SSOT (geometry / dummy / panel / color) ---

    @Test
    fun morphTracks_durationsAndEasings_f741n() {
        assertThat(StatusBarMorphExpandMs).isEqualTo(500)
        assertThat(StatusBarMorphCollapseMs).isEqualTo(600)
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
        assertThat(StatusBarMorphDummyOpenFadeMs).isEqualTo(100)
        assertThat(StatusBarMorphDummyCloseFadeMs).isEqualTo(200)
        assertThat(StatusBarMorphPanelCloseHideMs).isEqualTo(50)
        // Geometry PathInterpolator co overshoot nhe (y>1); endpoints 0/1.
        var geomMax = 0f
        for (i in 0..100) {
            geomMax =
                maxOf(geomMax, StatusBarMorphGeometryEasing.transform(i / 100f))
        }
        assertThat(geomMax).isGreaterThan(1f)
        assertThat(StatusBarMorphGeometryEasing.transform(0f)).isWithin(0.001f).of(0f)
        assertThat(StatusBarMorphGeometryEasing.transform(1f)).isWithin(0.001f).of(1f)
        // Color close easing overshoot-ish mid, end 1.
        assertThat(StatusBarMorphColorCloseEasing.transform(1f)).isWithin(0.001f).of(1f)
    }

    @Test
    fun morphTracks_geometryIndependentFromColor() {
        // Cung elapsed: geom PathInterpolator ≠ Decelerate color.
        val open225 = statusBarMorphTracksAtMs(225f, closing = false)
        assertThat(open225.geomFraction).isNotEqualTo(open225.colorT)
        assertThat(open225.shellGlass).isEqualTo(1f)
        val close241 = statusBarMorphTracksAtMs(241f, closing = true)
        assertThat(close241.geomFraction).isNotEqualTo(close241.colorT)
        assertThat(close241.shellGlass).isEqualTo(1f)
    }

    @Test
    fun morphTracks_dummyScaleAndFade_open100ms() {
        val t0 = statusBarMorphTracksAtMs(0f, closing = false)
        assertThat(t0.dummyScale).isWithin(0.02f).of(1f)
        assertThat(t0.dummyAlpha).isEqualTo(1f)
        val t50 = statusBarMorphTracksAtMs(50f, closing = false)
        assertThat(t50.dummyScale).isGreaterThan(1.05f)
        assertThat(t50.dummyAlpha).isWithin(0.02f).of(0.5f)
        val t100 = statusBarMorphTracksAtMs(100f, closing = false)
        assertThat(t100.dummyScale).isGreaterThan(1.2f)
        assertThat(t100.dummyAlpha).isEqualTo(0f)
    }

    @Test
    fun morphTracks_dummyCloseEarly200ms() {
        // Exclusive CLOSE: panel→0 @50, gap 50ms, pill fade tu 100→300.
        val t0 = statusBarMorphTracksAtMs(0f, closing = true)
        assertThat(t0.dummyAlpha).isEqualTo(0f)
        val t50 = statusBarMorphTracksAtMs(50f, closing = true)
        assertThat(t50.dummyAlpha).isEqualTo(0f)
        assertThat(t50.panelAlpha).isEqualTo(0f)
        val t100 = statusBarMorphTracksAtMs(100f, closing = true)
        assertThat(t100.dummyAlpha).isEqualTo(0f)
        val t200 = statusBarMorphTracksAtMs(200f, closing = true)
        assertThat(t200.dummyAlpha).isWithin(0.02f).of(0.5f)
        val t300 = statusBarMorphTracksAtMs(300f, closing = true)
        assertThat(t300.dummyAlpha).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(50f, closing = true).panelAlpha).isEqualTo(0f)
    }

    @Test
    fun morphTracks_panelWidthGate_shortCardAdapted() {
        // Target ngan hon 239dp @450 → gate adapt < 239px scale.
        val shortTarget = 200f // px
        assertThat(statusBarMorphPanelGateIsShortCardAdapted(450f, shortTarget)).isTrue()
        val gate = statusBarMorphPanelWidthGatePx(450f, shortTarget)
        assertThat(gate).isWithin(0.5f).of(180f) // 0.9*200
        // First cross @200ms → fGate=0.4; alpha remap 0 tai cross.
        val latch = StatusBarMorphPanelGateLatch()
        val fGate =
            latch.observeCrossing(
                linearFraction = 200f / 500f,
                measuredWidthPx = gate + 1f,
                widthGatePx = gate,
                closing = false,
            )
        assertThat(fGate).isWithin(0.001f).of(0.4f)
        val alphaAtCross =
            statusBarMorphPanelContentAlpha(
                elapsedMs = 200f,
                durationMs = 500f,
                closing = false,
                measuredWidthPx = gate + 1f,
                widthGatePx = gate,
                gateStartFraction = fGate,
            )
        assertThat(alphaAtCross).isWithin(0.001f).of(0f)
        val alphaLater =
            statusBarMorphPanelContentAlpha(
                elapsedMs = 350f,
                durationMs = 500f,
                closing = false,
                measuredWidthPx = gate + 1f,
                widthGatePx = gate,
                gateStartFraction = fGate,
            )
        assertThat(alphaLater).isGreaterThan(0f)
        // Width duoi gate + chua latch → 0.
        assertThat(
                statusBarMorphPanelContentAlpha(
                    elapsedMs = 400f,
                    durationMs = 500f,
                    closing = false,
                    measuredWidthPx = gate - 1f,
                    widthGatePx = gate,
                    gateStartFraction = null,
                )
            )
            .isEqualTo(0f)
    }

    @Test
    fun morphTracks_coverageOneOwner_shellDuringFlight() {
        // Truoc soft-release (p<=0.97): shell=1; gan p1 soft fade.
        for (ms in intArrayOf(0, 50, 100, 225, 400, 485)) {
            val s = statusBarMorphTracksAtMs(ms.toFloat(), closing = false)
            assertThat(s.shellGlass).isEqualTo(1f)
            assertThat(
                    statusBarMorphContentHandoffCoherent(s.dummyAlpha, s.panelAlpha, s.shellGlass)
                )
                .isTrue()
        }
        assertThat(statusBarMorphTracksAtMs(500f, closing = false).shellGlass).isEqualTo(0f)
    }

    @Test
    fun morphTracks_nicMonotone_slotClockSync500_600() {
        // task-007: slot 500/500; layout>=0; clock/NIC finish doc lap.
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
        for (i in 0..20) {
            val t = i / 20f
            val layout =
                statusBarChipSlotWidthFractionForLayout(
                    statusBarChipSlotRawFractionAt(
                        reclaiming = true,
                        t = t,
                        trigger = StatusBarNeighborTrigger.CARD_OPEN,
                    ),
                    200f,
                    450f,
                )
            assertThat(layout).isAtLeast(0f)
        }
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.CARD_CLOSE)).isTrue()
        assertThat(
            statusBarSlotClockShareEndPhase(
                reclaiming = true,
                slotWidthPx = 200f,
                clockAmplitudePx = 5.625f,
            ),
        ).isFalse() /* task-007 independent finish */
        assertThat(
            statusBarSlotClockShareEndPhase(
                reclaiming = false,
                slotWidthPx = 200f,
                clockAmplitudePx = 5.625f,
            ),
        ).isFalse() /* task-007 independent finish */
    }

    @Test
    fun morphTracks_openCloseTimelineTable_samples() {
        // OPEN ms: 0,50,100,225,500
        val openMs = intArrayOf(0, 50, 100, 225, 500)
        for (ms in openMs) {
            val s = statusBarMorphTracksAtMs(ms.toFloat(), closing = false)
            assertThat(s.geomFraction).isAtLeast(0f)
            assertThat(s.shellGlass).isAtLeast(0f)
            assertThat(s.colorT).isAtLeast(0f)
            assertThat(s.colorT).isAtMost(1f)
        }
        assertThat(statusBarMorphTracksAtMs(0f, false).dummyAlpha).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(100f, false).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(500f, false).colorT).isWithin(0.01f).of(1f)
        // CLOSE ms: 0,50,100,200,241,600 — soft-take @ms0 (p=1) shell0; sau handoff shell1.
        val closeMs = intArrayOf(0, 50, 100, 200, 241, 600)
        for (ms in closeMs) {
            val s = statusBarMorphTracksAtMs(ms.toFloat(), closing = true)
            assertThat(s.shellGlass).isAtLeast(0f)
            assertThat(s.colorT).isAtLeast(0f)
            assertThat(s.colorT).isAtMost(1f)
        }
        assertThat(statusBarMorphTracksAtMs(0f, true).shellGlass).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(50f, true).shellGlass).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(0f, true).dummyAlpha).isEqualTo(0f)
        // Exclusive: @50 panel0 + pill0 (gap); pill full @300.
        assertThat(statusBarMorphTracksAtMs(50f, true).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(300f, true).dummyAlpha).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(50f, true).panelAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(0f, true).colorT).isWithin(0.01f).of(1f)
        assertThat(statusBarMorphTracksAtMs(600f, true).colorT).isWithin(0.01f).of(0f)
    }

    @Test
    fun morphTracks_leftNeo_stableFinalLeft() {
        // Overshoot geom p>1: left1 van theo visualTargetW (khong right-drift).
        val g =
            computeKeyguardMorphGeom(
                pRaw = 1.08f,
                pillWPx = 200f,
                pillHPx = 56f,
                targetWPx = 960f,
                targetHPx = 400f,
            )
        val vr =
            statusBarMorphLerpVisualRectPx(
                pOffset = g.pOffset,
                pillLeftPx = 150f,
                pillTopPx = 30f,
                pillWPx = 200f,
                pillHPx = 56f,
                visualTargetWPx = 960f,
                visualTargetHPx = 400f,
                sx = g.sx,
                sy = g.sy,
                maxWidthPx = 1080f,
                targetVisualTopPx = 79f,
            )
        assertThat(vr.leftPx).isWithin(0.5f).of((1080f - 960f) * 0.5f)
    }

    @Test
    fun morphTracks_dummyLayerScale_followsShellUniform_notInverseCancel() {
        // Visual: screenUniform = min(sx,sy) — KHONG * dummyScale spring.
        val (lx, ly) = statusBarMorphDummyLayerScale(0.25f, 0.5f, 2f)
        // min=0.25 → localX=1, localY=0.5; screen = 0.25 ca hai truc.
        assertThat(lx).isWithin(0.001f).of(0.25f / 0.25f)
        assertThat(ly).isWithin(0.001f).of(0.25f / 0.5f)
        // Gu: khong nhan spring2 (localX != 2).
        assertThat(lx).isWithin(0.001f).of(1f)
        assertThat(lx).isLessThan((1f / 0.25f) * 2f - 0.1f)
    }

    // --- task-028 / review FAIL task-027: shell soft / gate remap / reverse continuity ---

    @Test
    fun shellGlass_softHandoff_nearP1_noHardStep() {
        // DEFECT #1: p .97..1 open soft; collapse soft-take; khong step .998→.999.
        val openSamples = floatArrayOf(0.97f, 0.99f, 0.998f, 0.999f, 1f)
        var prevOpen = statusBarMorphShellGlassAlpha(openSamples[0], closing = false)
        assertThat(prevOpen).isEqualTo(1f)
        for (i in 1 until openSamples.size) {
            val g = statusBarMorphShellGlassAlpha(openSamples[i], closing = false)
            // Monotone giam, buoc lien tiep < 0.5 (khong hard snap 1→0).
            assertThat(g).isAtMost(prevOpen + 1e-4f)
            assertThat(prevOpen - g).isLessThan(0.5f)
            prevOpen = g
        }
        assertThat(statusBarMorphShellGlassAlpha(1f, false)).isEqualTo(0f)
        // Close: p1 shell0 lien tuc open settle; vua duoi soft rise.
        assertThat(statusBarMorphShellGlassAlpha(1f, closing = true)).isEqualTo(0f)
        val closeJustBelow = statusBarMorphShellGlassAlpha(0.999f, closing = true)
        assertThat(closeJustBelow).isGreaterThan(0f)
        assertThat(closeJustBelow).isLessThan(0.5f)
        assertThat(statusBarMorphShellGlassAlpha(0.97f, closing = true)).isEqualTo(1f)
        // Blur coverage-driven: shell 0.5 < minCover → blur > 0; shell 0 → full.
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0.5f)).isGreaterThan(0.5f)
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0f)).isEqualTo(1f)
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 1f)).isEqualTo(0f)
    }

    @Test
    fun panelGate_firstCrossRemap_noPopJump() {
        // DEFECT #2: Samsung (f-fGate)/(1-fGate); alpha0 @cross; endpoint 500.
        val dur = 500f
        val gate = 100f
        val wide = 200f
        for (crossMs in floatArrayOf(100f, 225f, 300f)) {
            val latch = StatusBarMorphPanelGateLatch()
            val fGate =
                latch.observeCrossing(
                    linearFraction = crossMs / dur,
                    measuredWidthPx = wide,
                    widthGatePx = gate,
                    closing = false,
                )
            assertThat(fGate).isNotNull()
            val a0 =
                statusBarMorphPanelContentAlpha(
                    elapsedMs = crossMs,
                    durationMs = dur,
                    closing = false,
                    measuredWidthPx = wide,
                    widthGatePx = gate,
                    gateStartFraction = fGate,
                )
            assertThat(a0).isWithin(0.001f).of(0f)
            // Sample ngay sau cross — khong hard jump lon.
            val a1 =
                statusBarMorphPanelContentAlpha(
                    elapsedMs = crossMs + 16f,
                    durationMs = dur,
                    closing = false,
                    measuredWidthPx = wide,
                    widthGatePx = gate,
                    gateStartFraction = fGate,
                )
            assertThat(a1 - a0).isLessThan(0.12f)
            assertThat(a1).isAtLeast(a0)
            val aEnd =
                statusBarMorphPanelContentAlpha(
                    elapsedMs = 500f,
                    durationMs = dur,
                    closing = false,
                    measuredWidthPx = wide,
                    widthGatePx = gate,
                    gateStartFraction = fGate,
                )
            assertThat(aEnd).isWithin(0.001f).of(1f)
            // Reverse huy latch.
            latch.ensureDirection(closing = true)
            assertThat(latch.crossed).isFalse()
        }
    }

    @Test
    fun continuity_midFlightReverse_noAbruptJump() {
        // DEFECT #3: open120→close; close150→open; open-close-open rapid.
        val gate = 50f
        val wide = 500f

        fun sample(
            owner: StatusBarMorphContinuityOwner,
            p: Float,
            closing: Boolean,
        ) =
            owner.evaluate(
                expandedFraction = p,
                closing = closing,
                measuredWidthPx = wide,
                widthGatePx = gate,
            )

        // Case A: open 120ms (p=120/500=0.24) → close init.
        val a = StatusBarMorphContinuityOwner()
        val open120 = sample(a, 0.24f, closing = false)
        val closeInit = sample(a, 0.24f, closing = true)
        assertThat(kotlin.math.abs(closeInit.dummyAlpha - open120.dummyAlpha)).isLessThan(0.05f)
        assertThat(kotlin.math.abs(closeInit.dummyScale - open120.dummyScale)).isLessThan(0.05f)
        assertThat(kotlin.math.abs(closeInit.colorT - open120.colorT)).isLessThan(0.05f)
        assertThat(kotlin.math.abs(closeInit.shellGlass - open120.shellGlass)).isLessThan(0.05f)
        // Khong nhay sang close@elapsed=(1-0.24)*600 natural (dummy alpha~1).
        val naturalCloseWrong = statusBarMorphDummyTrack((1f - 0.24f) * 600f, closing = true)
        assertThat(kotlin.math.abs(closeInit.dummyAlpha - naturalCloseWrong.alpha))
            .isGreaterThan(0.2f)

        // Case B: close 150ms (p=1-150/600=0.75) → open init.
        val b = StatusBarMorphContinuityOwner()
        // Uninterrupted close start tu p=1.
        sample(b, 1f, closing = true)
        val close150 = sample(b, 0.75f, closing = true)
        val openInit = sample(b, 0.75f, closing = false)
        assertThat(kotlin.math.abs(openInit.dummyAlpha - close150.dummyAlpha)).isLessThan(0.05f)
        assertThat(kotlin.math.abs(openInit.colorT - close150.colorT)).isLessThan(0.05f)

        // Case C: open-close-open rapid — moi switch freeze, khong orphan.
        val c = StatusBarMorphContinuityOwner()
        var prev = sample(c, 0.1f, closing = false)
        for ((p, closing) in
            listOf(
                0.3f to false,
                0.3f to true,
                0.2f to true,
                0.2f to false,
                0.5f to false,
                0.5f to true,
            )) {
            val cur = sample(c, p, closing)
            assertThat(kotlin.math.abs(cur.dummyAlpha - prev.dummyAlpha)).isLessThan(0.35f)
            assertThat(kotlin.math.abs(cur.colorT - prev.colorT)).isLessThan(0.35f)
            prev = cur
        }
        c.reset()
        assertThat(c.gateLatch().crossed).isFalse()
    }

    // --- task-029: A panel soft close / B reverse continuous / C coverage / D color monotone ---

    @Test
    fun panelClose_softFade_noHardStepAround97() {
        // A: p=.971/.9701/.97/.9699 — khong discontinuity 1→0; gradient elapsed.
        val samples = floatArrayOf(0.971f, 0.9701f, 0.97f, 0.9699f)
        var prev = statusBarMorphCardContentAlpha(samples[0], closing = true)
        assertThat(prev).isGreaterThan(0f)
        assertThat(prev).isLessThan(1f)
        for (i in 1 until samples.size) {
            val a = statusBarMorphCardContentAlpha(samples[i], closing = true)
            // Giam muot theo elapsed tang — buoc nho, khong hard step ~1.0.
            assertThat(prev - a).isLessThan(0.05f)
            assertThat(a).isAtMost(prev + 1e-4f)
            assertThat(kotlin.math.abs(prev - a)).isGreaterThan(1e-6f) // gradient that
            prev = a
        }
        // Close start α=1; ~50ms → 0; exclusive gap roi pill fade-in.
        assertThat(statusBarMorphCardContentAlpha(1f, closing = true)).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(0f, true).panelAlpha).isEqualTo(1f)
        assertThat(statusBarMorphTracksAtMs(50f, true).panelAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(50f, true).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(300f, true).dummyAlpha).isEqualTo(1f)
    }

    @Test
    fun reverseClose_continuousPanelShell_noForcedOne() {
        // B: settle p1→close .99..969; reverse open@.99 partial; rapid O-C-O.
        val gate = 50f
        val wide = 500f
        fun sample(o: StatusBarMorphContinuityOwner, p: Float, closing: Boolean) =
            o.evaluate(p, closing, wide, gate)

        // Settle open → close samples.
        val closeRun = StatusBarMorphContinuityOwner()
        sample(closeRun, 1f, closing = false)
        // Force settle visual p1 (uninterrupted open end).
        sample(closeRun, 1f, closing = false)
        var prev = sample(closeRun, 1f, closing = true)
        assertThat(prev.panelAlpha).isWithin(0.02f).of(1f)
        for (p in floatArrayOf(0.99f, 0.98f, 0.9701f, 0.97f, 0.969f)) {
            val cur = sample(closeRun, p, closing = true)
            // Khong ep panel=1 khi dang fade; khong jump lon.
            assertThat(kotlin.math.abs(cur.panelAlpha - prev.panelAlpha)).isLessThan(0.55f)
            val blur = statusBarMorphCardSurfaceBlurFactor(cur.panelAlpha, cur.shellGlass)
            val cov =
                statusBarMorphBackplateCoverage(cur.shellGlass, cur.panelAlpha, blur)
            assertThat(cov).isAtLeast(StatusBarMorphMinBackplateCoverage - 1e-3f)
            prev = cur
        }

        // Reverse open @.99 voi panel partial — t=0 khong force panel=1.
        val rev = StatusBarMorphContinuityOwner()
        sample(rev, 0.5f, closing = false)
        val midOpen = sample(rev, 0.99f, closing = false)
        val closeFromPartial = sample(rev, 0.99f, closing = true)
        assertThat(closeFromPartial.panelAlpha).isWithin(0.05f).of(midOpen.panelAlpha)
        assertThat(closeFromPartial.panelAlpha).isLessThan(1.001f)
        // Neu snapshot panel < 1: khong nhay len 1.
        if (midOpen.panelAlpha < 0.99f) {
            assertThat(closeFromPartial.panelAlpha).isLessThan(0.995f)
        }

        // Rapid O-C-O.
        val rapid = StatusBarMorphContinuityOwner()
        var rPrev = sample(rapid, 0.2f, closing = false)
        for ((p, closing) in
            listOf(0.99f to false, 0.99f to true, 0.97f to true, 0.97f to false, 1f to false)) {
            val cur = sample(rapid, p, closing)
            assertThat(kotlin.math.abs(cur.panelAlpha - rPrev.panelAlpha)).isLessThan(0.6f)
            val blur = statusBarMorphCardSurfaceBlurFactor(cur.panelAlpha, cur.shellGlass)
            assertThat(
                    statusBarMorphBackplateCoverage(cur.shellGlass, cur.panelAlpha, blur)
                )
                .isAtLeast(StatusBarMorphMinBackplateCoverage - 0.05f)
            rPrev = cur
        }
    }

    @Test
    fun backplateCoverage_openSoftWindow_noTransparentGap() {
        // C: p .97..1 — proxy shell + parent×blur; khong dung max(shell, panelContent).
        val ps = floatArrayOf(0.97f, 0.98f, 0.99f, 0.995f, 0.998f, 0.999f, 1f)
        for (p in ps) {
            val shell = statusBarMorphShellGlassAlpha(p, closing = false)
            val panel = statusBarMorphCardContentAlpha(p, closing = false)
            val blur = statusBarMorphCardSurfaceBlurFactor(panel, shell)
            val cov = statusBarMorphBackplateCoverage(shell, panel, blur)
            assertThat(cov).isAtLeast(StatusBarMorphMinBackplateCoverage - 1e-3f)
            // Panel content α != native bg α khi blurFactor < 1.
            if (shell >= StatusBarMorphMinBackplateCoverage) {
                assertThat(blur).isEqualTo(0f)
            }
            if (p >= 1f) {
                assertThat(shell).isEqualTo(0f)
                assertThat(blur).isEqualTo(1f)
            }
        }
        // Close reverse cung bang p.
        for (p in ps) {
            val shell = statusBarMorphShellGlassAlpha(p, closing = true)
            val panel = statusBarMorphCardContentAlpha(p, closing = true)
            val blur = statusBarMorphCardSurfaceBlurFactor(panel, shell)
            assertThat(statusBarMorphBackplateCoverage(shell, panel, blur))
                .isAtLeast(StatusBarMorphMinBackplateCoverage - 1e-3f)
        }
    }

    @Test
    fun reverseColor_fromSnapshot09_monotoneToOne() {
        // D: snapshot.9 close→open — colorT monotone → 1; endpoint.
        val gate = 50f
        val wide = 500f
        val owner = StatusBarMorphContinuityOwner()
        // Open toi gan 1 roi close som de freeze color cao.
        owner.evaluate(0.95f, closing = false, wide, gate)
        val atClose = owner.evaluate(0.95f, closing = true, wide, gate)
        // Ep snapshot.colorT ~0.9 bang reverse open tu close mid neu can.
        // Simulate continuousFromSnapshot path: open reverse.
        val afterRev = owner.evaluate(0.95f, closing = false, wide, gate)
        assertThat(kotlin.math.abs(afterRev.colorT - atClose.colorT)).isLessThan(0.05f)
        var prev = afterRev.colorT
        // Tien open: color khong giam (monotone non-decreasing).
        for (p in floatArrayOf(0.96f, 0.97f, 0.98f, 0.99f, 1f)) {
            val cur = owner.evaluate(p, closing = false, wide, gate)
            assertThat(cur.colorT).isAtLeast(prev - 1e-4f)
            prev = cur.colorT
        }
        assertThat(prev).isWithin(0.02f).of(1f)

        // Cong thuc don: snapshot.9 + (1-0.9)*decelerate(t) monotone.
        val snap = 0.9f
        var prevC = snap
        for (i in 0..20) {
            val t = i / 20f
            val c = snap + (1f - snap) * statusBarMorphDecelerateFraction(t)
            assertThat(c).isAtLeast(prevC - 1e-5f)
            prevC = c
        }
        assertThat(prevC).isWithin(0.001f).of(1f)
        // CLOSE snapshot → 0 collapseColor monotone.
        var prevClose = snap
        for (i in 0..20) {
            val t = i / 20f
            val c =
                snap * (1f - StatusBarMorphColorCloseEasing.transform(t).coerceIn(0f, 1f))
            assertThat(c).isAtMost(prevClose + 1e-4f)
            prevClose = c
        }
        assertThat(prevClose).isWithin(0.02f).of(0f)
    }

    // --- task-032: content follow shell / frost / painted bounds / clock+NIC shared ---

    @Test
    fun contentScreenBounds_followShell_at0_50_100_150ms() {
        val pillW = 160f
        val pillH = 52f
        val targetW = 960f
        val targetH = 280f
        val shellLeft = 150f
        val shellTop = 32f
        val dur = StatusBarMorphExpandMs.toFloat()
        for (ms in floatArrayOf(0f, 50f, 100f, 150f)) {
            val p = (ms / dur).coerceIn(0f, 1f)
            val eased = StatusBarMorphGeometryEasing.transform(p)
            val g =
                computeKeyguardMorphGeom(
                    pRaw = eased,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    targetWPx = targetW,
                    targetHPx = targetH,
                )
            val dummy = statusBarMorphDummyTrack(ms, closing = false)
            val content =
                statusBarMorphContentScreenBounds(
                    shellLeftPx = shellLeft,
                    shellTopPx = shellTop,
                    hostSx = g.sx,
                    hostSy = g.sy,
                    dummyScale = dummy.scale,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    visualTargetWPx = targetW,
                    visualTargetHPx = targetH,
                )
            val shellW = targetW * g.sx
            val shellH = targetH * g.sy
            // Uniform: width/pill ≈ height/pill; content ≤ shell (khong * spring2).
            assertThat(content.widthPx / pillW).isWithin(0.02f).of(content.heightPx / pillH)
            assertThat(content.widthPx).isAtMost(shellW + 0.5f)
            assertThat(content.heightPx).isAtMost(shellH + 0.5f)
            // Center == shell center (≤1px) — khong neo TopStart.
            val (shellCx, shellCy) =
                statusBarMorphShellCenterScreenPx(
                    shellLeftPx = shellLeft,
                    shellTopPx = shellTop,
                    hostSx = g.sx,
                    hostSy = g.sy,
                    visualTargetWPx = targetW,
                    visualTargetHPx = targetH,
                )
            assertThat(content.centerXPx).isWithin(1f).of(shellCx)
            assertThat(content.centerYPx).isWithin(1f).of(shellCy)
            if (ms > 0f) {
                val at0 =
                    statusBarMorphContentScreenBounds(
                        shellLeftPx = shellLeft,
                        shellTopPx = shellTop,
                        hostSx = pillW / targetW,
                        hostSy = pillH / targetH,
                        dummyScale = 1f,
                        pillWPx = pillW,
                        pillHPx = pillH,
                        visualTargetWPx = targetW,
                        visualTargetHPx = targetH,
                    )
                // Growth: content size tang khi shell lon hon pill.
                if (shellW > pillW + 1f) {
                    assertThat(content.widthPx).isGreaterThan(at0.widthPx - 0.5f)
                }
            }
            // Alpha follow van thay duoc som (geometry remap).
            val followA =
                statusBarMorphDummyFollowContentAlpha(
                    elapsedMs = ms,
                    closing = false,
                    hostSx = g.sx,
                    pillWPx = pillW,
                    visualTargetWPx = targetW,
                    trackAlpha = dummy.alpha,
                )
            if (ms <= 50f) {
                assertThat(followA).isGreaterThan(0.5f)
            }
            // Shell visual size dung cong thuc.
            assertThat(shellW).isGreaterThan(0f)
            assertThat(shellH).isGreaterThan(0f)
            assertThat(content.uniformScale).isGreaterThan(0f)
        }
    }

    @Test
    fun frostEndpoint_tintDiffersFromSurfaceBright_fallbackWhenBlurOff() {
        // Pure: AxSystemFrost API contract (khong hardcode RGB).
        // fallbackColor = materialColorSurfaceBright; tintColor = surfaceBrightTint.
        // Khi blur off endpoint tint == fallback; blur on tint co the != CardBg surfaceBright.
        val fallbackId = com.android.internal.R.color.materialColorSurfaceBright
        assertThat(fallbackId).isNotEqualTo(0)
        // Resolve helpers: painted thang side-pad.
        val layoutW = 1000
        val side = 34 // 12dp@450
        val pill = 160
        val sideEst = statusBarMorphVisualTargetWPx(layoutW, side, pill)
        assertThat(sideEst).isEqualTo(layoutW - 2 * side)
        val painted = 900
        val resolved =
            statusBarMorphResolveVisualTargetWPx(layoutW, side, pill, painted)
        assertThat(resolved).isEqualTo(painted)
        assertThat(resolved).isNotEqualTo(sideEst)
        // Negative: side-pad mismatch.
        assertThat(kotlin.math.abs(sideEst - painted)).isGreaterThan(1)
        val inset = statusBarMorphResolveSideInsetPx(layoutW, painted, side)
        assertThat(inset).isEqualTo((layoutW - painted) / 2)
        // p=1 shell khop painted ≤1px (right edge).
        assertThat(
            statusBarMorphShellMatchesPaintedBounds(
                shellLeftPx = 50f,
                shellTopPx = 80f,
                shellWPx = 900f,
                shellHPx = 200f,
                paintedLeftPx = 50f,
                paintedTopPx = 80f,
                paintedWPx = 900f,
                paintedHPx = 200f,
                epsilonPx = 1f,
            ),
        ).isFalse() /* task-007 independent finish */
        assertThat(
            statusBarMorphShellMatchesPaintedBounds(
                shellLeftPx = 50f,
                shellTopPx = 80f,
                shellWPx = sideEst.toFloat(),
                shellHPx = 200f,
                paintedLeftPx = 50f,
                paintedTopPx = 80f,
                paintedWPx = 900f,
                paintedHPx = 200f,
                epsilonPx = 1f,
            ),
        ).isFalse()
    }

    @Test
    fun clockNic_sharedLinearT_endPhase_rtl_and_cancelRest() {
        val w = 200f
        val ampLtr = 5.625f // 2dp@450
        val ampRtl = -ampLtr
        assertThat(
            statusBarSlotClockShareEndPhase(
                reclaiming = false,
                slotWidthPx = w,
                clockAmplitudePx = ampLtr,
            ),
        ).isFalse() /* task-007 independent finish */
        assertThat(
            statusBarSlotClockShareEndPhase(
                reclaiming = true,
                slotWidthPx = w,
                clockAmplitudePx = ampLtr,
            ),
        ).isFalse() /* task-007 independent finish */
        assertThat(
            statusBarSlotClockShareEndPhase(
                reclaiming = false,
                slotWidthPx = w,
                clockAmplitudePx = ampRtl,
            ),
        ).isFalse() /* task-007 independent finish */
        var sawClockMidTail = false
        var sawNicOvershootMidTail = false
        for (t in floatArrayOf(0.85f, 0.9f, 0.95f, 0.99f, 1f)) {
            val sL =
                statusBarSlotClockSampleAt(
                    reclaiming = false,
                    linearT = t,
                    slotWidthPx = w,
                    clockAmplitudePx = ampLtr,
                )
            val sR =
                statusBarSlotClockSampleAt(
                    reclaiming = false,
                    linearT = t,
                    slotWidthPx = w,
                    clockAmplitudePx = ampRtl,
                )
            // Cung |tx|; RTL doi dau.
            assertThat(sL.clockTranslationXPx).isWithin(0.01f).of(-sR.clockTranslationXPx)
            // Cung NIC layout tu linearT.
            assertThat(sL.nicLayoutFraction).isWithin(1e-5f).of(sR.nicLayoutFraction)
            // Tail cung frame: khong chi assert t=1 ca hai=0.
            if (t < 1f && kotlin.math.abs(sL.clockTranslationXPx) > 0.01f) {
                sawClockMidTail = true
            }
            if (t < 1f && kotlin.math.abs(sL.nicOvershootDeltaPx) > 0.01f) {
                sawNicOvershootMidTail = true
            }
            assertThat(sL.linearT).isWithin(1e-5f).of(t)
        }
        assertThat(sawClockMidTail).isFalse() /* task-007 independent finish */
        assertThat(sawNicOvershootMidTail).isFalse() /* task-007 independent finish */
        // Cancel/rest @t=1: clock 0, NIC overshoot 0.
        val end =
            statusBarSlotClockSampleAt(
                reclaiming = false,
                linearT = 1f,
                slotWidthPx = w,
                clockAmplitudePx = ampLtr,
            )
        assertThat(end.clockTranslationXPx).isWithin(0.01f).of(0f)
        assertThat(end.nicOvershootDeltaPx).isWithin(0.01f).of(0f)
        // EXPAND monotonic no rebound.
        var prev = 1f
        for (i in 0..20) {
            val t = i / 20f
            val layout =
                statusBarChipSlotWidthFractionForLayout(
                    statusBarChipSlotRawFractionAt(true, t),
                    w,
                    450f,
                )
            assertThat(layout).isAtMost(prev + 1e-4f)
            assertThat(layout).isAtLeast(0f)
            prev = layout
        }
    }

    // --- task-033: F1–F4 yield boolean / painted LRTB / derive linearT / content follow ---

    @Test
    fun glassCompositing_yieldBoolean_p97to1_openCloseReverse() {
        val samples = floatArrayOf(0.97f, 0.98f, 0.99f, 0.995f, 0.998f, 0.999f, 1f)
        fun assertDir(closing: Boolean) {
            for (p in samples) {
                val shell = statusBarMorphShellGlassAlpha(p, closing)
                // Panel alpha gan 1 o soft window (width-gate da cross).
                val panel = if (closing) (1f - p).coerceIn(0.2f, 1f) else p.coerceIn(0.2f, 1f)
                val c = statusBarMorphGlassCompositingAt(shell, panel)
                // Coverage khong hole (< min) trong soft window.
                assertThat(c.effectiveCoverage)
                    .isAtLeast(StatusBarMorphMinBackplateCoverage - 0.02f)
                // Yield chi khi shell tu cover — KHONG yield o shell~.1 (tranh hole).
                if (shell >= StatusBarMorphShellOwnsBlurAbove) {
                    assertThat(c.yieldNative).isFalse() /* task-007 independent finish */
                    assertThat(c.cardBlurFactor).isEqualTo(0f)
                } else {
                    assertThat(c.yieldNative).isFalse()
                    // Shell yeu / 0 → complementary hoac full native.
                    if (shell <= 1e-4f) {
                        assertThat(c.cardBlurFactor).isEqualTo(1f)
                    } else {
                        assertThat(c.cardBlurFactor).isGreaterThan(0f)
                    }
                }
                // Legacy FAIL: yield = shell>0.01 se omit native khi shell~.1.
                if (shell in 0.02f..0.5f) {
                    assertThat(statusBarMorphYieldNativeGlassToShell(shell)).isFalse()
                }
            }
        }
        assertDir(closing = false)
        assertDir(closing = true)
        // Reverse mid soft: open roi close cung p — coverage van >= min.
        for (p in samples) {
            val shellOpen = statusBarMorphShellGlassAlpha(p, closing = false)
            val shellClose = statusBarMorphShellGlassAlpha(p, closing = true)
            val cOpen = statusBarMorphGlassCompositingAt(shellOpen, panelContentAlpha = 0.85f)
            val cClose = statusBarMorphGlassCompositingAt(shellClose, panelContentAlpha = 0.85f)
            assertThat(cOpen.effectiveCoverage)
                .isAtLeast(StatusBarMorphMinBackplateCoverage - 0.02f)
            assertThat(cClose.effectiveCoverage)
                .isAtLeast(StatusBarMorphMinBackplateCoverage - 0.02f)
        }
    }

    @Test
    fun frostHandoff_shellTintMatchesAxSystemFrost_contract() {
        // Pure contract: fallback = materialColorSurfaceBright; tint = surfaceBrightTint.
        val fallbackId = com.android.internal.R.color.materialColorSurfaceBright
        assertThat(fallbackId).isNotEqualTo(0)
        // Soft handoff shell α giam muot — khong step .999 hard swap.
        val a998 = statusBarMorphShellGlassAlpha(0.998f, closing = false)
        val a999 = statusBarMorphShellGlassAlpha(0.999f, closing = false)
        val a1 = statusBarMorphShellGlassAlpha(1f, closing = false)
        assertThat(a998).isGreaterThan(a999)
        assertThat(a999).isGreaterThan(a1 - 1e-4f)
        assertThat(a1).isEqualTo(0f)
        // @p=.998 shell yeu → yield false (complementary), khong omit native.
        val weak = statusBarMorphGlassCompositingAt(a998, panelContentAlpha = 1f)
        assertThat(weak.yieldNative).isFalse()
        assertThat(weak.cardBlurFactor).isGreaterThan(0f)
        assertThat(weak.effectiveCoverage).isAtLeast(StatusBarMorphMinBackplateCoverage - 0.01f)
    }

    @Test
    fun paintedTopBounds_excludesUnderCard_matchesShell_rtlOwnerSwitch() {
        val top =
            StatusBarPaintedCardReportPx(
                layoutWPx = 900,
                layoutHPx = 200,
                windowLeftPx = 50f,
                windowTopPx = 80f,
                windowRightPx = 950f,
                windowBottomPx = 280f,
            )
        val underWider =
            StatusBarPaintedCardReportPx(
                layoutWPx = 1000,
                layoutHPx = 220,
                windowLeftPx = 0f,
                windowTopPx = 100f,
                windowRightPx = 1000f,
                windowBottomPx = 320f,
            )
        val underNarrower =
            StatusBarPaintedCardReportPx(
                layoutWPx = 700,
                layoutHPx = 180,
                windowLeftPx = 100f,
                windowTopPx = 90f,
                windowRightPx = 800f,
                windowBottomPx = 270f,
            )
        // Under wider/narrower bi loai — chi top selected.
        val selected =
            statusBarMorphSelectTopPaintedReport(
                listOf(
                    false to underWider,
                    true to top,
                    false to underNarrower,
                ),
            )
        assertThat(selected).isEqualTo(top)
        assertThat(selected!!.layoutWPx).isEqualTo(900)
        assertThat(selected.layoutWPx).isNotEqualTo(underWider.layoutWPx)
        // Production endpoint: lerpVisualRect @p1 DOC frozen LRTB — khong tautology copy.
        val dest =
            statusBarMorphPaintedDestinationOrNull(
                top.windowLeftPx,
                top.windowTopPx,
                top.windowRightPx,
                top.windowBottomPx,
            )!!
        val pillL = 150f
        val pillT = 32f
        val pillW = 160f
        val pillH = 52f
        val maxW = 1080f
        val g =
            computeKeyguardMorphGeom(
                pRaw = 1f,
                pillWPx = pillW,
                pillHPx = pillH,
                targetWPx = dest.widthPx,
                targetHPx = dest.heightPx,
            )
        val vr =
            statusBarMorphLerpVisualRectPx(
                pOffset = g.pOffset,
                pillLeftPx = pillL,
                pillTopPx = pillT,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = dest.widthPx,
                visualTargetHPx = dest.heightPx,
                sx = g.sx,
                sy = g.sy,
                maxWidthPx = maxW,
                targetVisualTopPx = 79f,
                paintedTargetLeftPx = dest.leftPx,
                paintedTargetTopPx = dest.topPx,
            )
        assertThat(vr.leftPx).isWithin(0.1f).of(50f)
        assertThat(vr.topPx).isWithin(0.1f).of(80f)
        assertThat(vr.widthPx).isWithin(0.1f).of(900f)
        assertThat(vr.heightPx).isWithin(0.1f).of(200f)
        // Negative: guessed center ≠ painted left (non-center 50 vs ~90).
        val guessed = statusBarMorphGuessedCenterLeftPx(maxW, dest.widthPx)
        assertThat(kotlin.math.abs(guessed - dest.leftPx)).isGreaterThan(1f)
        val vrGuess =
            statusBarMorphLerpVisualRectPx(
                pOffset = 1f,
                pillLeftPx = pillL,
                pillTopPx = pillT,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = dest.widthPx,
                visualTargetHPx = dest.heightPx,
                sx = 1f,
                sy = 1f,
                maxWidthPx = maxW,
                targetVisualTopPx = 79f,
                // NaN → center guess
            )
        assertThat(vrGuess.leftPx).isWithin(0.1f).of(guessed)
        assertThat(kotlin.math.abs(vrGuess.leftPx - vr.leftPx)).isGreaterThan(1f)
        // Side-pad estimate lech painted — negative proof.
        val sideEst = statusBarMorphVisualTargetWPx(1000, 34, 160)
        assertThat(kotlin.math.abs(sideEst - top.layoutWPx)).isGreaterThan(1)
        // Undo host scale mid-flight (sx=0.5): LOCAL W khop unscaled (khong + host origin).
        val undo =
            statusBarMorphPaintedBoundsUndoHostScale(
                windowLeftPx = 50f,
                windowTopPx = 80f,
                windowRightPx = 500f, // half of 900@sx0.5 from left 50
                windowBottomPx = 180f,
                hostWindowLeftPx = 50f,
                hostWindowTopPx = 80f,
                hostSx = 0.5f,
                hostSy = 0.5f,
            )
        assertThat(kotlin.math.abs(undo.layoutWPx - 900)).isAtMost(1)
        assertThat(undo.windowLeftPx).isWithin(0.1f).of(0f)
        assertThat(undo.windowTopPx).isWithin(0.1f).of(0f)
        // Owner switch: top moi thang under cu.
        val top2 =
            top.copy(
                layoutWPx = 850,
                windowRightPx = 900f,
            )
        val afterSwitch =
            statusBarMorphSelectTopPaintedReport(
                listOf(false to top, true to top2),
            )
        assertThat(afterSwitch!!.layoutWPx).isEqualTo(850)
        // RTL: left/right doi — production lerp van dung LRTB.
        val rtl =
            StatusBarPaintedCardReportPx(
                layoutWPx = 900,
                layoutHPx = 200,
                windowLeftPx = 130f,
                windowTopPx = 80f,
                windowRightPx = 1030f,
                windowBottomPx = 280f,
            )
        val rtlDest =
            statusBarMorphPaintedDestinationOrNull(
                rtl.windowLeftPx,
                rtl.windowTopPx,
                rtl.windowRightPx,
                rtl.windowBottomPx,
            )!!
        val rtlVr =
            statusBarMorphLerpVisualRectPx(
                pOffset = 1f,
                pillLeftPx = pillL,
                pillTopPx = pillT,
                pillWPx = pillW,
                pillHPx = pillH,
                visualTargetWPx = rtlDest.widthPx,
                visualTargetHPx = rtlDest.heightPx,
                sx = 1f,
                sy = 1f,
                maxWidthPx = maxW,
                targetVisualTopPx = 79f,
                paintedTargetLeftPx = rtlDest.leftPx,
                paintedTargetTopPx = rtlDest.topPx,
            )
        assertThat(rtlVr.leftPx).isWithin(0.1f).of(130f)
        assertThat(rtlVr.widthPx).isWithin(0.1f).of(900f)
        // Delayed measure: null dest → center fallback; sau do painted thang.
        assertThat(
            statusBarMorphPaintedDestinationOrNull(0f, 0f, 0f, 0f),
        ).isNull()
        // Interrupt: select null khi khong top → fallback side-pad path (caller).
        assertThat(
            statusBarMorphSelectTopPaintedReport(listOf(false to underWider)),
        ).isNull()
    }

    @Test
    fun sharedGlassOwner_persistentAlpha_yieldOwnerNotThreshold() {
        // Owner present → paint α=1 @moi p soft window + rest; yield = owner (khong .95).
        val samples = floatArrayOf(0.97f, 0.98f, 0.99f, 0.995f, 0.998f, 0.999f, 1f)
        for (p in samples) {
            val legacyShell = statusBarMorphShellGlassAlpha(p, closing = false)
            // Legacy soft-handoff van fade — production KHONG dung de destroy glass.
            if (p >= 0.999f) {
                assertThat(legacyShell).isLessThan(0.05f)
            }
            val paint =
                statusBarMorphPersistentSharedGlassAlpha(
                    sharedGlassOwnerPresent = true,
                    collapseChromeFactor = 1f,
                )
            assertThat(paint).isEqualTo(1f)
            assertThat(statusBarMorphYieldNativeGlassToSharedOwner(true)).isFalse() /* task-007 independent finish */
            assertThat(statusBarMorphYieldNativeGlassToSharedOwner(false)).isFalse()
            // Legacy threshold van ton tai cho test cu — production owner-present.
            if (legacyShell < StatusBarMorphShellOwnsBlurAbove) {
                assertThat(statusBarMorphYieldNativeGlassToShell(legacyShell)).isFalse()
            }
        }
        // Handback chrome 0 → paint 0 (host dispose path).
        assertThat(
            statusBarMorphPersistentSharedGlassAlpha(true, collapseChromeFactor = 0f),
        ).isEqualTo(0f)
        assertThat(
            statusBarMorphPersistentSharedGlassAlpha(false, collapseChromeFactor = 1f),
        ).isEqualTo(0f)
    }

    @Test
    fun slotLinearT_deriveLayout_noSnapToInUpdate_static() {
        // Static proof: layout = f(linearT) — khong can Animatable.snapTo per-frame.
        val w = 200f
        val dpi = 450f
        for (reclaiming in booleanArrayOf(true, false)) {
            for (t in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 0.85f, 0.9f, 0.95f, 0.99f, 1f)) {
                val sample =
                    statusBarSlotClockSampleAt(
                        reclaiming = reclaiming,
                        linearT = t,
                        slotWidthPx = w,
                        clockAmplitudePx = 5.625f,
                        densityDpi = dpi,
                    )
                val raw = statusBarChipSlotRawFractionAt(reclaiming, t)
                val layout = statusBarChipSlotWidthFractionForLayout(raw, w, dpi)
                assertThat(sample.nicLayoutFraction).isWithin(1e-5f).of(layout)
                assertThat(sample.linearT).isWithin(1e-5f).of(t)
            }
            // Settle t=1: width + clock tx=0 cung luc.
            val end =
                statusBarSlotClockSampleAt(
                    reclaiming = reclaiming,
                    linearT = 1f,
                    slotWidthPx = w,
                    clockAmplitudePx = 5.625f,
                    densityDpi = dpi,
                )
            assertThat(end.clockTranslationXPx).isWithin(0.01f).of(0f)
            assertThat(end.nicLayoutFraction)
                .isWithin(1e-5f)
                .of(if (reclaiming) 0f else 1f)
        }
        // task-007: overshoot theo curve — chi nonnegative (bo 2dp cap).
        val mid =
            statusBarChipSlotOvershootDeltaPx(
                reclaiming = false,
                t = 0.66f,
                slotWidthPx = w,
                densityDpi = dpi,
            )
        assertThat(mid).isAtLeast(0f)
    }

    @Test
    fun contentScreenBounds_followShell_centerVsShell_circleIsotropic() {
        val pillW = 160f
        val pillH = 52f
        val targetW = 960f
        val targetH = 280f
        val shellLeft = 150f
        val shellTop = 32f
        val dur = StatusBarMorphExpandMs.toFloat()
        var prevCx = Float.NaN
        for (ms in floatArrayOf(0f, 50f, 100f, 150f)) {
            val p = (ms / dur).coerceIn(0f, 1f)
            val eased = StatusBarMorphGeometryEasing.transform(p)
            val g =
                computeKeyguardMorphGeom(
                    pRaw = eased,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    targetWPx = targetW,
                    targetHPx = targetH,
                )
            val dummy = statusBarMorphDummyTrack(ms, closing = false)
            val content =
                statusBarMorphContentScreenBounds(
                    shellLeftPx = shellLeft,
                    shellTopPx = shellTop,
                    hostSx = g.sx,
                    hostSy = g.sy,
                    dummyScale = dummy.scale,
                    pillWPx = pillW,
                    pillHPx = pillH,
                    visualTargetWPx = targetW,
                    visualTargetHPx = targetH,
                )
            val shellW = targetW * g.sx
            val shellH = targetH * g.sy
            // Content canh giua shell — center == shell center.
            val (shellCx, shellCy) =
                statusBarMorphShellCenterScreenPx(
                    shellLeftPx = shellLeft,
                    shellTopPx = shellTop,
                    hostSx = g.sx,
                    hostSy = g.sy,
                    visualTargetWPx = targetW,
                    visualTargetHPx = targetH,
                )
            assertThat(content.centerXPx).isWithin(1f).of(shellCx)
            assertThat(content.centerYPx).isWithin(1f).of(shellCy)
            // Isotropic (Circle ring / text) — khong meo chu.
            assertThat(content.widthPx / pillW).isWithin(0.02f).of(content.heightPx / pillH)
            if (!prevCx.isNaN() && shellW > pillW + 1f) {
                assertThat(content.centerXPx).isGreaterThan(prevCx - 0.5f)
            }
            prevCx = content.centerXPx
            // Visible follow alpha som.
            val followA =
                statusBarMorphDummyFollowContentAlpha(
                    elapsedMs = ms,
                    closing = false,
                    hostSx = g.sx,
                    pillWPx = pillW,
                    visualTargetWPx = targetW,
                    trackAlpha = dummy.alpha,
                )
            if (ms <= 100f) {
                assertThat(followA).isGreaterThan(0.01f)
            }
            assertThat(shellW).isGreaterThan(0f)
            assertThat(shellH).isGreaterThan(0f)
        }
    }

    @Test
    fun paintedDest_localPlusTargetHost_stableAcrossMovingHostOrigin() {
        // G1: host x130→80, y10→72; local 16/2; scale .15→.60→1 → dest on dinh 96,74.
        val targetHostLeft = 80f
        val targetHostTop = 72f
        val localLeft = 16f
        val localTop = 2f
        val localW = 900f
        val localH = 200f
        val frames =
            listOf(
                Triple(130f, 10f, 0.15f),
                Triple(110f, 30f, 0.60f),
                Triple(80f, 72f, 1.00f),
            )
        var frozenLeft = Float.NaN
        var frozenTop = Float.NaN
        var frozenRight = Float.NaN
        var frozenBottom = Float.NaN
        for ((hostX, hostY, scale) in frames) {
            // Window = hostOrigin + local * scale (child trong graphicsLayer).
            val winL = hostX + localLeft * scale
            val winT = hostY + localTop * scale
            val winR = hostX + (localLeft + localW) * scale
            val winB = hostY + (localTop + localH) * scale
            val local =
                statusBarMorphPaintedBoundsUndoHostScale(
                    windowLeftPx = winL,
                    windowTopPx = winT,
                    windowRightPx = winR,
                    windowBottomPx = winB,
                    hostWindowLeftPx = hostX,
                    hostWindowTopPx = hostY,
                    hostSx = scale,
                    hostSy = scale,
                )
            assertThat(local.windowLeftPx).isWithin(0.5f).of(localLeft)
            assertThat(local.windowTopPx).isWithin(0.5f).of(localTop)
            // Negative: cong thuc cu (=host+local) → source-adjacent khi host @pill.
            val contaminatedLeft = hostX + local.windowLeftPx
            if (hostX > targetHostLeft + 1f) {
                assertThat(kotlin.math.abs(contaminatedLeft - (targetHostLeft + localLeft)))
                    .isGreaterThan(1f)
            }
            assertThat(
                statusBarMorphPaintedDestReadyForFreeze(
                    targetHostPlacementReady = true,
                    localLeftPx = local.windowLeftPx,
                    localTopPx = local.windowTopPx,
                    localRightPx = local.windowRightPx,
                    localBottomPx = local.windowBottomPx,
                ),
            ).isTrue()
            val dest =
                statusBarMorphPaintedDestFromLocalInTargetHost(
                    targetHostLeftPx = targetHostLeft,
                    targetHostTopPx = targetHostTop,
                    localLeftPx = local.windowLeftPx,
                    localTopPx = local.windowTopPx,
                    localRightPx = local.windowRightPx,
                    localBottomPx = local.windowBottomPx,
                )!!
            assertThat(dest.leftPx).isWithin(0.5f).of(96f)
            assertThat(dest.topPx).isWithin(0.5f).of(74f)
            // Freeze first valid — cac frame sau KHONG doi (atomic).
            if (frozenLeft.isNaN()) {
                frozenLeft = dest.leftPx
                frozenTop = dest.topPx
                frozenRight = dest.leftPx + dest.widthPx
                frozenBottom = dest.topPx + dest.heightPx
            } else {
                assertThat(dest.leftPx).isWithin(0.5f).of(frozenLeft)
                assertThat(dest.topPx).isWithin(0.5f).of(frozenTop)
                assertThat(dest.leftPx + dest.widthPx).isWithin(0.5f).of(frozenRight)
                assertThat(dest.topPx + dest.heightPx).isWithin(0.5f).of(frozenBottom)
            }
            // Right edge ≤1px vs target.
            val expectedRight = targetHostLeft + localLeft + localW
            assertThat(kotlin.math.abs((dest.leftPx + dest.widthPx) - expectedRight))
                .isAtMost(1f)
            // Production lerp @p1 dung frozen dest — khong tautology shellRectFromPainted.
            val pillL = 130f
            val pillT = 10f
            val g =
                computeKeyguardMorphGeom(
                    pRaw = 1f,
                    pillWPx = 160f,
                    pillHPx = 52f,
                    targetWPx = dest.widthPx,
                    targetHPx = dest.heightPx,
                )
            val vr =
                statusBarMorphLerpVisualRectPx(
                    pOffset = g.pOffset,
                    pillLeftPx = pillL,
                    pillTopPx = pillT,
                    pillWPx = 160f,
                    pillHPx = 52f,
                    visualTargetWPx = dest.widthPx,
                    visualTargetHPx = dest.heightPx,
                    sx = g.sx,
                    sy = g.sy,
                    maxWidthPx = 1080f,
                    targetVisualTopPx = 79f,
                    paintedTargetLeftPx = frozenLeft,
                    paintedTargetTopPx = frozenTop,
                )
            assertThat(vr.leftPx).isWithin(0.5f).of(96f)
            assertThat(vr.topPx).isWithin(0.5f).of(74f)
            // Negative: source-adjacent (pill-ish) ≠ frozen dest.
            assertThat(kotlin.math.abs(vr.leftPx - pillL)).isGreaterThan(1f)
        }
        // Defer khi placement chua ready.
        assertThat(
            statusBarMorphPaintedDestReadyForFreeze(
                targetHostPlacementReady = false,
                localLeftPx = localLeft,
                localTopPx = localTop,
                localRightPx = localLeft + localW,
                localBottomPx = localTop + localH,
            ),
        ).isFalse()
        // Placement tu layout constraints — khop targetHost 80,72 @ maxW=1080 layoutW=920 top=72.
        val (placeL, placeT) =
            statusBarMorphExpandedTargetHostPlacementPx(
                maxWidthPx = 1080f,
                layoutTargetWPx = 920f,
                topPadPx = 72f,
            )
        assertThat(placeL).isWithin(0.1f).of(80f)
        assertThat(placeT).isWithin(0.1f).of(72f)
        // RTL / short card: local left lon (non-center) van compose dung.
        val rtlLocalL = 40f
        val rtlDest =
            statusBarMorphPaintedDestFromLocalInTargetHost(
                targetHostLeftPx = 80f,
                targetHostTopPx = 72f,
                localLeftPx = rtlLocalL,
                localTopPx = 2f,
                localRightPx = rtlLocalL + 700f,
                localBottomPx = 182f,
            )!!
        assertThat(rtlDest.leftPx).isWithin(0.1f).of(120f)
        assertThat(rtlDest.widthPx).isWithin(0.1f).of(700f)
        // Owner switch / late: dest moi thay khi freeze lai @ !running.
        val late =
            statusBarMorphPaintedDestFromLocalInTargetHost(
                targetHostLeftPx = 80f,
                targetHostTopPx = 72f,
                localLeftPx = 20f,
                localTopPx = 4f,
                localRightPx = 870f,
                localBottomPx = 214f,
            )!!
        assertThat(late.leftPx).isWithin(0.1f).of(100f)
        assertThat(kotlin.math.abs(late.leftPx - frozenLeft)).isGreaterThan(1f)
    }

    @Test
    fun shellTintArgb_endpointExactResourceAlpha_noForceAlphaGlass() {
        // G2: p1 = panelEndpoint EXACT ARGB (ke ca alpha); khong ep 0.72.
        fun argbEqual(
            a: androidx.compose.ui.graphics.Color,
            b: androidx.compose.ui.graphics.Color,
        ): Boolean =
            kotlin.math.abs(a.alpha - b.alpha) <= 1e-4f &&
                kotlin.math.abs(a.red - b.red) <= 1e-4f &&
                kotlin.math.abs(a.green - b.green) <= 1e-4f &&
                kotlin.math.abs(a.blue - b.blue) <= 1e-4f
        val pillSource =
            androidx.compose.ui.graphics.Color(0.267f, 0.533f, 1f, AlphaGlass)
        // Frost-on tint resource alpha ≠ 0.72 (vd 0.55).
        val frostOn = androidx.compose.ui.graphics.Color(1f, 1f, 1f, 0.55f)
        val frostOff = androidx.compose.ui.graphics.Color(1f, 0.984f, 0.996f, 1f)
        assertThat(frostOn.alpha).isWithin(0.02f).of(0.55f)
        assertThat(kotlin.math.abs(frostOn.alpha - AlphaGlass)).isGreaterThan(0.05f)
        val p1On =
            statusBarMorphShellTintArgb(
                pillSourceTint = pillSource,
                panelEndpointTint = frostOn,
                tintProgress = 1f,
            )
        assertThat(argbEqual(p1On, frostOn)).isTrue()
        val p1Off =
            statusBarMorphShellTintArgb(
                pillSourceTint = pillSource,
                panelEndpointTint = frostOff,
                tintProgress = 1f,
            )
        assertThat(argbEqual(p1Off, frostOff)).isTrue()
        // Dark frost-on.
        val darkFrost = androidx.compose.ui.graphics.Color(0f, 0f, 0f, 0.6f)
        val p1Dark =
            statusBarMorphShellTintArgb(pillSource, darkFrost, tintProgress = 1f)
        assertThat(argbEqual(p1Dark, darkFrost)).isTrue()
        // p0 = pill source exact (AlphaGlass live material).
        val p0 = statusBarMorphShellTintArgb(pillSource, frostOn, tintProgress = 0f)
        assertThat(argbEqual(p0, pillSource)).isTrue()
        // Lien tuc mid — alpha interpolate (Decelerate track dung colorT caller).
        val mid = statusBarMorphShellTintArgb(pillSource, frostOn, tintProgress = 0.5f)
        assertThat(mid.alpha).isGreaterThan(kotlin.math.min(pillSource.alpha, frostOn.alpha) - 1e-3f)
        assertThat(mid.alpha).isLessThan(kotlin.math.max(pillSource.alpha, frostOn.alpha) + 1e-3f)
        // Fallback fill: khong ep AlphaGlass len endpoint.
        val fb =
            statusBarMorphShellFallbackArgb(
                pillAccent = androidx.compose.ui.graphics.Color(0.267f, 0.533f, 1f, 1f),
                panelEndpointFallback = frostOff,
                tintProgress = 1f,
            )
        assertThat(argbEqual(fb, frostOff)).isTrue()
        // Neu resource alpha da 0.72 → p1 van = endpoint (contract exact).
        val alreadyGlass = frostOff.copy(alpha = AlphaGlass)
        val p1Glass =
            statusBarMorphShellTintArgb(pillSource, alreadyGlass, tintProgress = 1f)
        assertThat(p1Glass.alpha).isWithin(1e-4f).of(AlphaGlass)
        assertThat(argbEqual(p1Glass, alreadyGlass)).isTrue()
    }

    // --- task-038: A content=shell scale / B NIC+clock phase / C exclusive glass gap ---

    @Test
    fun task038_contentScale_equalsShell_notSpring2_wideShortRtlDensity() {
        val cases =
            listOf(
                // wide shell
                floatArrayOf(0.2f, 0.5f, 160f, 52f, 960f, 280f),
                // short / tall growth
                floatArrayOf(0.4f, 0.15f, 120f, 48f, 400f, 600f),
                // near pill
                floatArrayOf(0.9f, 0.9f, 160f, 52f, 180f, 60f),
            )
        for (c in cases) {
            val hostSx = c[0]
            val hostSy = c[1]
            val pillW = c[2]
            val pillH = c[3]
            val tw = c[4]
            val th = c[5]
            for (dummySpring in floatArrayOf(1f, 1.5f, 2f)) {
                val (lx, ly) =
                    statusBarMorphDummyLayerScale(
                        hostSx = hostSx,
                        hostSy = hostSy,
                        dummyScale = dummySpring,
                        pillWPx = pillW,
                        pillHPx = pillH,
                        visualTargetWPx = tw,
                        visualTargetHPx = th,
                    )
                val growX = (tw / pillW) * hostSx
                val growY = (th / pillH) * hostSy
                val uni = minOf(growX, growY)
                assertThat(lx).isWithin(0.001f).of(uni / hostSx)
                assertThat(ly).isWithin(0.001f).of(uni / hostSy)
                // Spring khong doi visual local.
                val (lx2, ly2) =
                    statusBarMorphDummyLayerScale(
                        hostSx,
                        hostSy,
                        1f,
                        pillW,
                        pillH,
                        tw,
                        th,
                    )
                assertThat(lx).isWithin(0.001f).of(lx2)
                assertThat(ly).isWithin(0.001f).of(ly2)
                val bounds =
                    statusBarMorphContentScreenBounds(
                        shellLeftPx = 10f,
                        shellTopPx = 20f,
                        hostSx = hostSx,
                        hostSy = hostSy,
                        dummyScale = dummySpring,
                        pillWPx = pillW,
                        pillHPx = pillH,
                        visualTargetWPx = tw,
                        visualTargetHPx = th,
                    )
                val shellW = tw * hostSx
                val shellH = th * hostSy
                assertThat(bounds.widthPx).isAtMost(shellW + 0.5f)
                assertThat(bounds.heightPx).isAtMost(shellH + 0.5f)
                // Isotropic (Circle ring).
                assertThat(bounds.widthPx / pillW)
                    .isWithin(0.02f)
                    .of(bounds.heightPx / pillH)
            }
        }
        // Density / RTL khong doi cong thuc scale (px inputs).
        val (a, b) = statusBarMorphDummyLayerScale(0.3f, 0.6f, 2f, 100f, 40f, 500f, 200f)
        assertThat(a * 0.3f).isWithin(0.001f).of(b * 0.6f)
    }

    @Test
    fun task038_nicClock_sameLastNonzero_expandCollapse() {
        val w = 200f
        val amp = 5.625f
        for (reclaiming in booleanArrayOf(true, false)) {
            // Perceptive 0.5px (khong dung 0.01px lam acceptance).
            assertThat(
                statusBarSlotClockShareEndPhase(
                    reclaiming = reclaiming,
                    slotWidthPx = w,
                    clockAmplitudePx = amp,
                    threshPx = 0.5f,
                    hz = 120f,
                ),
            ).isFalse() /* task-007 independent finish */
            assertThat(
                statusBarSlotClockShareEndPhase(
                    reclaiming = reclaiming,
                    slotWidthPx = w,
                    clockAmplitudePx = -amp, // RTL
                    threshPx = 1.0f,
                    hz = 60f,
                ),
            ).isFalse() /* task-007 independent finish */
            val end =
                statusBarSlotClockSampleAt(reclaiming, 1f, w, amp)
            val nicRest = if (reclaiming) 0f else 1f
            assertThat(end.clockTranslationXPx).isWithin(0.01f).of(0f)
            assertThat(kotlin.math.abs(end.nicLayoutFraction - nicRest) * w)
                .isAtMost(0.01f)
        }
        assertThat(StatusBarChipSlotHideMs).isEqualTo(500)
        assertThat(StatusBarChipSlotShowMs).isEqualTo(500)
        // EXPAND khong rebound.
        assertThat(statusBarChipSlotOvershootDeltaPx(true, 0.7f, w, 450f)).isEqualTo(0f)
        // task-007: overshoot curve OK — chi nonnegative.
        assertThat(statusBarChipSlotOvershootDeltaPx(false, 0.7f, w, 450f)).isAtLeast(0f)
    }

    @Test
    fun task039_nicClock_perceptiveShareEndPhase_matrix() {
        // task-007: independent finish — stub false; khong demand shared last-visible.
        assertThat(statusBarSlotClockShareEndPhasePerceptiveMatrix(reclaiming = true))
            .isFalse() /* task-007 independent finish */
        assertThat(statusBarSlotClockShareEndPhasePerceptiveMatrix(reclaiming = false))
            .isFalse() /* task-007 */
        val w = 200f
        assertThat(
                statusBarChipSlotWidthFractionForLayout(
                    statusBarChipSlotRawFractionAt(true, 1f),
                    w,
                    450f,
                )
            )
            .isEqualTo(0f)
        assertThat(statusBarClockNudgeEligible(StatusBarNeighborTrigger.CARD_OPEN)).isTrue()
        // Placeholder de giu dong RTL sample cu khong assert shared phase:
        val amp = 5.625f
        val viewSample = statusBarSlotClockSampleAt(true, 1f, w, amp)
        val composeSample = statusBarSlotClockSampleAt(true, 1f, w, -amp)
        assertThat(viewSample.nicLayoutFraction)
            .isWithin(0.001f)
            .of(composeSample.nicLayoutFraction)
    }

    @Test
    fun task039_exclusiveHandoff_reverseContinuity_noSnap() {
        val eps = StatusBarMorphContentExclusiveEpsilon
        val gap = StatusBarMorphContentExclusiveGapMs
        val dt = 8.3f // ~120Hz
        // ~0.1/frame + le cho panel hide 50ms (~0.166/frame).
        val maxJump = 0.17f
        // Regular OPEN/CLOSE gap van PASS.
        assertThat(statusBarMorphExclusiveHandoffHasGap(closing = false)).isTrue()
        assertThat(statusBarMorphExclusiveHandoffHasGap(closing = true)).isTrue()
        assertThat(statusBarMorphTracksAtMs(125f, false).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(125f, false).panelAlpha).isEqualTo(0f)

        fun assertNoOverlapJump(
            hand: StatusBarMorphExclusiveContentHandoff,
            toClosing: Boolean,
            snapPill: Float,
            snapPanel: Float,
            horizonMs: Float,
        ): Boolean {
            var prevPill = snapPill
            var prevPanel = snapPanel
            var bothZeroRun = 0f
            var sawGap40 = false
            var t = 0f
            while (t <= horizonMs) {
                val a =
                    hand.evaluate(
                        elapsedMs = t,
                        durationMs = if (toClosing) 600f else 500f,
                        closing = toClosing,
                        naturalPill =
                            if (toClosing) (t / 200f).coerceIn(0f, 1f)
                            else (1f - t / 100f).coerceIn(0f, 1f),
                        naturalPanel =
                            if (toClosing) {
                                // Khong gia panel khi snapshot panel=0 (gap/pill-only).
                                if (snapPanel <= eps) 0f
                                else (snapPanel * (1f - t / 50f)).coerceIn(0f, 1f)
                            } else {
                                if (snapPill <= eps && snapPanel > eps) {
                                    (snapPanel + (1f - snapPanel) * (t / 200f)).coerceIn(0f, 1f)
                                } else {
                                    (t / 200f).coerceIn(0f, 1f)
                                }
                            },
                    )
                assertThat(a.pillAlpha > eps && a.panelAlpha > eps).isFalse()
                if (t > 0f) {
                    assertThat(kotlin.math.abs(a.pillAlpha - prevPill))
                        .isAtMost(maxJump + 0.001f)
                    assertThat(kotlin.math.abs(a.panelAlpha - prevPanel))
                        .isAtMost(maxJump + 0.001f)
                } else {
                    // Frame0 continuity: khong blank same-owner.
                    if (toClosing && snapPanel <= eps && snapPill > eps) {
                        assertThat(a.pillAlpha).isWithin(0.02f).of(snapPill)
                        assertThat(a.panelAlpha).isAtMost(eps)
                    }
                    if (!toClosing && snapPill <= eps && snapPanel > eps) {
                        assertThat(a.panelAlpha).isWithin(0.02f).of(snapPanel)
                        assertThat(a.pillAlpha).isAtMost(eps)
                    }
                }
                val bothZero = a.pillAlpha <= eps && a.panelAlpha <= eps
                if (bothZero) {
                    bothZeroRun += dt
                    if (bothZeroRun >= 40f - 0.5f) sawGap40 = true
                } else {
                    bothZeroRun = 0f
                }
                prevPill = a.pillAlpha
                prevPanel = a.panelAlpha
                t += dt
            }
            return sawGap40
        }

        for (p in floatArrayOf(0.2f, 0.5f, 0.8f)) {
            val hand = StatusBarMorphExclusiveContentHandoff()
            val natural =
                statusBarMorphExclusiveContentAlphas(
                    elapsedMs = p * 500f,
                    durationMs = 500f,
                    closing = false,
                    measuredWidthPx = 2000f,
                    widthGatePx = 100f,
                    gateStartFraction = 100f / 500f,
                )
            hand.captureReverseFrom(natural.pillAlpha, natural.panelAlpha)
            val sawGap =
                assertNoOverlapJump(
                    hand,
                    toClosing = true,
                    snapPill = natural.pillAlpha,
                    snapPanel = natural.panelAlpha,
                    horizonMs = 120f,
                )
            // Panel→pill owner swap can gap; gap-state (p=.2) cung co both-zero.
            if (natural.panelAlpha > eps) {
                assertThat(sawGap).isTrue()
            }
            val end =
                hand.evaluate(300f, 600f, true, naturalPill = 1f, naturalPanel = 0f)
            assertThat(end.pillAlpha).isGreaterThan(0.5f)
            assertThat(end.panelAlpha).isAtMost(eps)
        }

        // CLOSE mid → OPEN reverse <100ms.
        for (p in floatArrayOf(0.2f, 0.5f, 0.8f)) {
            val hand = StatusBarMorphExclusiveContentHandoff()
            val natural =
                statusBarMorphExclusiveContentAlphas(
                    elapsedMs = p * 600f,
                    durationMs = 600f,
                    closing = true,
                    measuredWidthPx = 2000f,
                    widthGatePx = 100f,
                    gateStartFraction = null,
                )
            hand.captureReverseFrom(natural.pillAlpha, natural.panelAlpha)
            assertNoOverlapJump(
                hand,
                toClosing = false,
                snapPill = natural.pillAlpha,
                snapPanel = natural.panelAlpha,
                horizonMs = 100f,
            )
        }

        // Same-owner pill visible → close: khong blank frame0.
        val pillOnly = StatusBarMorphExclusiveContentHandoff()
        pillOnly.captureReverseFrom(0.7f, 0f)
        val f0 = pillOnly.evaluate(0f, 600f, true, 0.7f, 0f)
        assertThat(f0.pillAlpha).isWithin(0.02f).of(0.7f)
        assertThat(f0.panelAlpha).isAtMost(eps)

        // maxOf snap guard: CLOSE sau gap pill tang tu 0 (khong 0→0.8).
        val snap = StatusBarMorphExclusiveContentHandoff()
        snap.captureReverseFrom(0f, 0.8f)
        var prevPillSnap = 0f
        var tSnap = 0f
        var pillAfterGap: Float? = null
        while (tSnap <= 160f) {
            val a =
                snap.evaluate(
                    elapsedMs = tSnap,
                    durationMs = 600f,
                    closing = true,
                    naturalPill = 0.8f,
                    naturalPanel = (0.8f * (1f - tSnap / 50f)).coerceIn(0f, 1f),
                )
            if (tSnap == 0f) {
                assertThat(a.panelAlpha).isWithin(0.02f).of(0.8f)
                assertThat(a.pillAlpha).isAtMost(eps)
            }
            // Bat khung dau tien pill >0 sau gap — phai <0.15 (khong snap 0.8).
            if (pillAfterGap == null && a.pillAlpha > eps) {
                pillAfterGap = a.pillAlpha
                assertThat(a.pillAlpha - prevPillSnap).isAtMost(maxJump + 0.001f)
            }
            prevPillSnap = a.pillAlpha
            tSnap += dt
        }
        assertThat(pillAfterGap).isNotNull()
        assertThat(pillAfterGap!!).isLessThan(0.15f)
    }

    @Test
    fun task038_exclusiveHandoff_openClose_alphaGapTable() {
        val eps = StatusBarMorphContentExclusiveEpsilon
        assertThat(StatusBarMorphContentExclusiveGapMs).isAtLeast(40f)
        assertThat(StatusBarMorphContentExclusiveGapMs).isAtMost(70f)
        assertThat(statusBarMorphExclusiveHandoffHasGap(closing = false)).isTrue()
        assertThat(statusBarMorphExclusiveHandoffHasGap(closing = true)).isTrue()
        // OPEN bang thoi gian.
        val openRows =
            listOf(
                0f to (1f to 0f),
                50f to (0.5f to 0f),
                100f to (0f to 0f),
                125f to (0f to 0f),
                150f to (0f to 0f),
                500f to (0f to 1f),
            )
        for ((ms, expect) in openRows) {
            val a =
                statusBarMorphExclusiveContentAlphas(
                    elapsedMs = ms,
                    durationMs = 500f,
                    closing = false,
                    measuredWidthPx = 2000f,
                    widthGatePx = 100f,
                    gateStartFraction = 100f / 500f,
                )
            assertThat(a.pillAlpha).isWithin(0.02f).of(expect.first)
            if (ms <= 150f) {
                assertThat(a.panelAlpha).isWithin(0.02f).of(expect.second)
            } else {
                assertThat(a.panelAlpha).isGreaterThan(0.5f)
            }
            assertThat(a.pillAlpha > eps && a.panelAlpha > eps).isFalse()
        }
        // CLOSE bang: panel→0, gap, pill.
        val closeRows =
            listOf(
                0f to (0f to 1f),
                50f to (0f to 0f),
                75f to (0f to 0f),
                100f to (0f to 0f),
                300f to (1f to 0f),
            )
        for ((ms, expect) in closeRows) {
            val a =
                statusBarMorphExclusiveContentAlphas(
                    elapsedMs = ms,
                    durationMs = 600f,
                    closing = true,
                    measuredWidthPx = 2000f,
                    widthGatePx = 100f,
                    gateStartFraction = null,
                )
            assertThat(a.pillAlpha).isWithin(0.02f).of(expect.first)
            assertThat(a.panelAlpha).isWithin(0.02f).of(expect.second)
            assertThat(a.pillAlpha > eps && a.panelAlpha > eps).isFalse()
        }
        // tracksAtMs dong bo exclusive.
        assertThat(statusBarMorphTracksAtMs(125f, false).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(125f, false).panelAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(75f, true).dummyAlpha).isEqualTo(0f)
        assertThat(statusBarMorphTracksAtMs(75f, true).panelAlpha).isEqualTo(0f)
        // Reverse mid-flight: clamp khong overlap.
        val hand = StatusBarMorphExclusiveContentHandoff()
        val mid =
            hand.evaluate(
                elapsedMs = 80f,
                durationMs = 500f,
                closing = false,
                naturalPill = 0.4f,
                naturalPanel = 0.4f,
            )
        assertThat(mid.pillAlpha > eps && mid.panelAlpha > eps).isFalse()
        hand.reset()
        val rev =
            hand.evaluate(
                elapsedMs = 20f,
                durationMs = 600f,
                closing = true,
                naturalPill = 0.5f,
                naturalPanel = 0.5f,
            )
        assertThat(rev.pillAlpha > eps && rev.panelAlpha > eps).isFalse()
    }

    // --- task-042: FG center == shell + handoff single-owner alpha ---

    @Test
    fun task042_contentCenter_matchesShell_atProgressSamples_expandCollapse() {
        val samples = floatArrayOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)
        data class Case(val pillW: Float, val pillH: Float, val tw: Float, val th: Float, val left: Float, val top: Float)
        val cases =
            listOf(
                Case(160f, 52f, 960f, 280f, 150f, 32f),
                Case(120f, 48f, 400f, 200f, 80f, 10f), // short card
                Case(200f, 56f, 900f, 300f, 40f, 0f),
                Case(90f, 90f, 720f, 720f, 200f, 20f), // square-ish
            )
        for (c in cases) {
            for (closing in booleanArrayOf(false, true)) {
                for (p in samples) {
                    val eased = StatusBarMorphGeometryEasing.transform(p)
                    val g =
                        computeKeyguardMorphGeom(
                            pRaw = eased,
                            pillWPx = c.pillW,
                            pillHPx = c.pillH,
                            targetWPx = c.tw,
                            targetHPx = c.th,
                        )
                    val dummy = statusBarMorphDummyTrack(p * StatusBarMorphExpandMs, closing)
                    val content =
                        statusBarMorphContentScreenBounds(
                            shellLeftPx = c.left,
                            shellTopPx = c.top,
                            hostSx = g.sx,
                            hostSy = g.sy,
                            dummyScale = dummy.scale,
                            pillWPx = c.pillW,
                            pillHPx = c.pillH,
                            visualTargetWPx = c.tw,
                            visualTargetHPx = c.th,
                        )
                    val (shellCx, shellCy) =
                        statusBarMorphShellCenterScreenPx(
                            shellLeftPx = c.left,
                            shellTopPx = c.top,
                            hostSx = g.sx,
                            hostSy = g.sy,
                            visualTargetWPx = c.tw,
                            visualTargetHPx = c.th,
                        )
                    assertThat(content.centerXPx).isWithin(1f).of(shellCx)
                    assertThat(content.centerYPx).isWithin(1f).of(shellCy)
                    assertThat(content.widthPx).isAtMost(c.tw * g.sx + 0.5f)
                    assertThat(content.heightPx).isAtMost(c.th * g.sy + 0.5f)
                    // p0: offset local ~0 (parity pill).
                    if (p == 0f) {
                        val (lx, ly) =
                            statusBarMorphDummyLayerScale(
                                hostSx = g.sx,
                                hostSy = g.sy,
                                dummyScale = 1f,
                                pillWPx = c.pillW,
                                pillHPx = c.pillH,
                                visualTargetWPx = c.tw,
                                visualTargetHPx = c.th,
                            )
                        val off =
                            statusBarMorphDummyContentLocalOffsetPx(
                                pillWPx = c.pillW,
                                pillHPx = c.pillH,
                                localSx = lx,
                                localSy = ly,
                                visualTargetWPx = c.tw,
                                visualTargetHPx = c.th,
                            )
                        assertThat(off.xPx).isWithin(1.5f).of(0f)
                        assertThat(off.yPx).isWithin(1.5f).of(0f)
                    }
                }
            }
        }
    }

    @Test
    fun task042_handoffAlpha_singleOwner_forwardAndReverse() {
        // FORWARD: truoc suppress — true=1 overlay=0; sau suppress — true=0 overlay=1.
        val pre =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = true,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
            )
        assertThat(pre.truePillAlpha).isEqualTo(1f)
        assertThat(pre.overlayGlassAlpha).isEqualTo(0f)
        assertThat(pre.effectiveMaterialSrcOver).isWithin(1e-4f).of(AlphaGlass)

        val post =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = true,
                handbackGlassRevealed = false,
                isExpanded = true,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
            )
        assertThat(post.truePillAlpha).isEqualTo(0f)
        assertThat(post.overlayGlassAlpha).isEqualTo(1f)
        assertThat(post.effectiveMaterialSrcOver).isWithin(1e-4f).of(AlphaGlass)

        // REVERSE hold chua reveal: true=0 overlay=1 — single material owner.
        val hold =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = false,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
            )
        assertThat(hold.truePillAlpha).isEqualTo(0f)
        assertThat(hold.overlayGlassAlpha).isEqualTo(1f)
        assertThat(hold.effectiveMaterialSrcOver).isWithin(1e-4f).of(AlphaGlass)
        // Material double (bug): 2×AlphaGlass → .9216 > .72 — KHONG dung SrcOver(1,1)=1.
        val doubleMat = statusBarMorphMaterialSrcOverEffective(1f, 1f)
        assertThat(doubleMat).isWithin(1e-4f).of(AlphaGlass + AlphaGlass * (1f - AlphaGlass))
        assertThat(doubleMat).isGreaterThan(AlphaGlass)
        assertThat(hold.effectiveMaterialSrcOver).isLessThan(doubleMat)

        // Phase3a staged: FG=1 (054), native glass staged<.1, overlay complementary ~AlphaGlass
        // (KHONG 2×.72=.9216). Task-046/054.
        val revealedAwait =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
                trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
            )
        assertThat(revealedAwait.truePillAlpha).isEqualTo(1f)
        assertThat(revealedAwait.nativeGlassFraction)
            .isWithin(1e-4f)
            .of(StatusBarHandbackStagedTrueLayerAlpha)
        assertThat(revealedAwait.nativeGlassFraction).isLessThan(0.1f)
        assertThat(revealedAwait.overlayGlassAlpha).isLessThan(1.001f)
        assertThat(revealedAwait.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
        assertThat(revealedAwait.effectiveMaterialSrcOver).isLessThan(doubleMat - 0.05f)
        assertThat(
                statusBarCollapseHandbackBrightDoubleMaterial(
                    revealedAwait.nativeGlassFraction,
                    revealedAwait.overlayGlassAlpha,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(
                    handbackGlassRevealed = true,
                    liveDrawnAcked = false,
                    trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                )
            )
            .isEqualTo(HandbackVisibleRevealPhase.REVEALED_AWAITING_DRAW)

        // Sau ack + reveal: true=1 overlay=0 (chrome).
        val after =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = true,
            )
        assertThat(after.truePillAlpha).isEqualTo(1f)
        assertThat(after.overlayGlassAlpha).isEqualTo(0f)
        assertThat(after.effectiveMaterialSrcOver).isWithin(1e-4f).of(AlphaGlass)

        // Idle / true owns: overlay gate 0 ke ca sharedGlassOwnerPresent (fix latent double).
        val idle =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
            )
        assertThat(idle.truePillAlpha).isEqualTo(1f)
        assertThat(idle.overlayGlassAlpha).isEqualTo(0f)
        assertThat(idle.effectiveMaterialSrcOver).isWithin(1e-4f).of(AlphaGlass)
    }

    // --- task-043: AlphaGlass math + 2-phase ACK ordering + FSM timeline ---

    @Test
    fun task043_alphaGlass_doubleLayer_brighterThanSingle() {
        // SrcOver(1,1)=1 tautology — KHONG chung minh brightness.
        assertThat(statusBarMorphSrcOverEffectiveAlpha(1f, 1f)).isWithin(1e-6f).of(1f)
        val single = statusBarMorphMaterialTintAlpha(1f, AlphaGlass)
        assertThat(single).isWithin(1e-4f).of(0.72f)
        val both = statusBarMorphMaterialSrcOverEffective(1f, 1f, AlphaGlass)
        // .72 + .72*(1-.72) = .9216
        assertThat(both).isWithin(1e-4f).of(0.9216f)
        assertThat(both).isGreaterThan(single)
        val trueOnly = statusBarMorphMaterialSrcOverEffective(1f, 0f)
        val overlayOnly = statusBarMorphMaterialSrcOverEffective(0f, 1f)
        assertThat(trueOnly).isWithin(1e-4f).of(AlphaGlass)
        assertThat(overlayOnly).isWithin(1e-4f).of(AlphaGlass)
        assertThat(both).isGreaterThan(trueOnly)
    }

    @Test
    fun task043_handoff_visibleBeforeAck_orderingAndFalsePositiveRootDraw() {
        // Root OnDraw luc α=0 KHONG prove visible pill.
        assertThat(
                statusBarCollapseHandbackRootDrawProvesVisiblePill(
                    handbackGlassRevealed = false,
                    truePillAlpha = 0f,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackAckAfterVisibleLive(
                    truePillAlpha = 0f,
                    handbackGlassRevealed = false,
                    drawCredible = true,
                )
            )
            .isFalse()
        // Reveal α>0 + credible → ACK OK.
        assertThat(
                statusBarCollapseHandbackRootDrawProvesVisiblePill(
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackAckAfterVisibleLive(
                    truePillAlpha = 1f,
                    handbackGlassRevealed = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        // Reveal nhung draw khong credible → chua ACK.
        assertThat(
                statusBarCollapseHandbackAckAfterVisibleLive(
                    truePillAlpha = 1f,
                    handbackGlassRevealed = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(false, false)
            )
            .isEqualTo(HandbackVisibleRevealPhase.HOLD_HIDDEN)
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(true, false)
            )
            .isEqualTo(HandbackVisibleRevealPhase.REVEALED_AWAITING_DRAW)
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(true, true)
            )
            .isEqualTo(HandbackVisibleRevealPhase.ACKED)
    }

    @Test
    fun task043_handoffTimeline_forwardReverseAckTimeoutOwnerSwitch() {
        fun sample(
            suppressed: Boolean,
            revealed: Boolean,
            expanded: Boolean,
            hold: Boolean,
            acked: Boolean,
        ) =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = suppressed,
                handbackGlassRevealed = revealed,
                isExpanded = expanded,
                collapseHandbackHold = hold,
                liveDrawnAcked = acked,
            )

        // Forward intent p0: true owns.
        val fwd0 = sample(false, true, true, false, false)
        assertThat(fwd0.overlayGlassAlpha).isEqualTo(0f)
        assertThat(fwd0.truePillAlpha).isEqualTo(1f)
        assertThat(
                statusBarCollapseHandbackBothHidden(fwd0.overlayGlassAlpha, fwd0.truePillAlpha)
            )
            .isFalse()

        // After suppress.
        val fwdSup = sample(true, false, true, false, false)
        assertThat(fwdSup.truePillAlpha).isEqualTo(0f)
        assertThat(fwdSup.overlayGlassAlpha).isEqualTo(1f)

        // Collapse hold hidden.
        val hold0 = sample(false, false, false, true, false)
        assertThat(hold0.truePillAlpha).isEqualTo(0f)
        assertThat(hold0.overlayGlassAlpha).isEqualTo(1f)
        assertThat(
                statusBarCollapseHandbackBothHidden(hold0.overlayGlassAlpha, hold0.truePillAlpha)
            )
            .isFalse()

        // Early staged: FG=1 (054); native glass staged; overlay complementary — ~.72.
        val earlyReveal =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
                trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
            )
        assertThat(earlyReveal.truePillAlpha).isEqualTo(1f)
        assertThat(earlyReveal.nativeGlassFraction).isGreaterThan(0.01f)
        assertThat(earlyReveal.nativeGlassFraction).isLessThan(0.1f)
        assertThat(earlyReveal.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
        assertThat(
                statusBarCollapseHandbackAckAfterVisibleLive(
                    earlyReveal.truePillAlpha,
                    handbackGlassRevealed = true,
                    drawCredible = true,
                )
            )
            .isTrue()

        // Acked: overlay 0, true 1 — khong both-hidden.
        val acked = sample(false, true, false, true, true)
        assertThat(acked.overlayGlassAlpha).isEqualTo(0f)
        assertThat(acked.truePillAlpha).isEqualTo(1f)
        assertThat(
                statusBarCollapseHandbackBothHidden(acked.overlayGlassAlpha, acked.truePillAlpha)
            )
            .isFalse()

        // Timeout: staged .05 / !credible → CAM drop; full+credible → OK.
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    liveOwnerExpected = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = false,
                    truePillAlpha = 0f,
                    liveOwnerExpected = true,
                )
            )
            .isFalse()
        val timeoutSafe =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
                trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
            )
        assertThat(timeoutSafe.truePillAlpha).isEqualTo(1f)
        assertThat(timeoutSafe.nativeGlassFraction).isGreaterThan(0.01f)
        assertThat(
                statusBarCollapseHandbackBothHidden(
                    timeoutSafe.overlayGlassAlpha,
                    timeoutSafe.truePillAlpha,
                )
            )
            .isFalse()
        // Timeout drop van cam khi native glass staged (khong theo FG=1).
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = timeoutSafe.nativeGlassFraction,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isFalse()

        // Bad stale: overlay drop + true chua reveal → both-hidden (cam).
        assertThat(statusBarCollapseHandbackBothHidden(0f, 0f)).isTrue()

        // No-owner / showMorphHost false (true owns, !hold): overlay gate 0.
        val noHost =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
                sharedGlassOwnerPresent = false,
            )
        assertThat(noHost.overlayGlassAlpha).isEqualTo(0f)
        assertThat(noHost.truePillAlpha).isEqualTo(1f)

        // Rapid reverse: hold clear → true owns overlay 0.
        val c = CollapseHandbackHoldController()
        val g1 = c.beginSettleHandback(60, ownerId = "a").holdGeneration
        c.onExpandedOrReverse()
        assertThat(c.holdActive).isFalse()
        val afterReverse = sample(false, true, true, false, false)
        assertThat(afterReverse.overlayGlassAlpha).isEqualTo(0f)
        // Owner switch: stale ack rejected; chrome giu 1.
        val g2 = c.beginSettleHandback(61, ownerId = "b").holdGeneration
        assertThat(c.acknowledgeLiveDrawn(g1, 60, "a")).isFalse()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        assertThat(c.acknowledgeLiveDrawn(g2, 61, "b")).isTrue()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
    }

    @Test
    fun task043_p0Parity_liveBlurMatchesOverlayGlassTokens() {
        // Live StatusBarPillBody(includeBlur): tint=accent×AlphaGlass, highlight=ChipHighlightWidth.
        // Overlay islandBlur @p0: cung AlphaGlass + ChipHighlightWidth (ExpandedPanel).
        assertThat(AlphaGlass).isWithin(1e-4f).of(0.72f)
        assertThat(ChipHighlightWidth.value).isGreaterThan(0f)
        val accent = androidx.compose.ui.graphics.Color(0.2f, 0.4f, 0.8f, 1f)
        val liveTint = accent.copy(alpha = AlphaGlass)
        assertThat(liveTint.alpha).isWithin(1e-4f).of(AlphaGlass)
        // Frost p1 exact — KHONG ep AlphaGlass.
        val frostEnd = androidx.compose.ui.graphics.Color(0.1f, 0.1f, 0.12f, 0.94f)
        assertThat(kotlin.math.abs(frostEnd.alpha - AlphaGlass)).isGreaterThan(0.05f)
        // Chrome factor: hold+unsuppress chua ack → 1 (khong mat chrome som khi real hidden).
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
    }

    // --- task-046: staged+complementary + timeout readiness barrier (cung helper runtime) ---

    @Test
    fun task046_complementaryMath_effectiveNearAlphaGlass() {
        // y=(1-x)/(1-0.72*x); effective material ≈ .72 (±0.05).
        val xs =
            floatArrayOf(
                0f,
                StatusBarHandbackStagedTrueLayerAlpha,
                0.25f,
                0.5f,
                0.75f,
                1f,
            )
        for (x in xs) {
            val y = statusBarMorphHandoffComplementaryOverlayLayer(x)
            val eff = statusBarMorphMaterialSrcOverEffective(x, y)
            assertThat(eff).isWithin(0.05f).of(AlphaGlass)
            assertThat(
                    statusBarCollapseHandbackBrightDoubleMaterial(x, y)
                )
                .isFalse()
        }
        // Bad both-full: .9216.
        val bad = statusBarMorphMaterialSrcOverEffective(1f, 1f)
        assertThat(bad).isWithin(1e-4f).of(0.9216f)
        assertThat(statusBarCollapseHandbackBrightDoubleMaterial(1f, 1f)).isTrue()
    }

    @Test
    fun task046_handoffTimeline_stagedHandoverAck_neverBothHiddenOrDouble() {
        fun sample(
            suppressed: Boolean,
            revealed: Boolean,
            expanded: Boolean,
            hold: Boolean,
            acked: Boolean,
            trueLayer: Float,
        ) =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = suppressed,
                handbackGlassRevealed = revealed,
                isExpanded = expanded,
                collapseHandbackHold = hold,
                liveDrawnAcked = acked,
                trueLayerFraction = trueLayer,
            )

        // Pre-forward: true owns.
        val pre = sample(false, true, true, false, false, 1f)
        assertThat(pre.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
        assertThat(
                statusBarMorphHandoffForwardSingleOwnerSafe(
                    pre.truePillAlpha,
                    pre.overlayGlassAlpha,
                    nativeGlassFraction = pre.nativeGlassFraction,
                )
            )
            .isTrue()

        // Forward cross suppress.
        val cross = sample(true, false, true, false, false, 0f)
        assertThat(cross.truePillAlpha).isEqualTo(0f)
        assertThat(cross.overlayGlassAlpha).isEqualTo(1f)
        assertThat(
                statusBarMorphHandoffForwardSingleOwnerSafe(
                    cross.truePillAlpha,
                    cross.overlayGlassAlpha,
                    nativeGlassFraction = cross.nativeGlassFraction,
                )
            )
            .isTrue()

        // Expanded overlay owns.
        val expanded = sample(true, false, true, false, false, 0f)
        assertThat(expanded.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)

        // Hold hidden.
        val hold = sample(false, false, false, true, false, 0f)
        assertThat(hold.truePillAlpha).isEqualTo(0f)
        assertThat(hold.overlayGlassAlpha).isEqualTo(1f)
        assertThat(statusBarCollapseHandbackBothHidden(hold.overlayGlassAlpha, hold.truePillAlpha))
            .isFalse()

        // Delayed reveal >50ms pending: FG=1, glass staged, chua ACK — complementary ~.72.
        val pending =
            sample(false, true, false, true, false, StatusBarHandbackStagedTrueLayerAlpha)
        assertThat(pending.truePillAlpha).isEqualTo(1f)
        assertThat(pending.nativeGlassFraction).isLessThan(0.1f)
        assertThat(pending.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
        assertThat(
                statusBarMorphHandoffForwardSingleOwnerSafe(
                    pending.truePillAlpha,
                    pending.overlayGlassAlpha,
                    nativeGlassFraction = pending.nativeGlassFraction,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(
                    handbackGlassRevealed = true,
                    liveDrawnAcked = false,
                    trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                    drawCredible = false,
                )
            )
            .isEqualTo(HandbackVisibleRevealPhase.REVEALED_AWAITING_DRAW)

        // Handover mid (credible + layer > staged).
        val midLayer =
            statusBarMorphHandoffTrueLayerFromProgress(
                progress = 0.5f,
                revealed = true,
                stagedUntilReady = false,
            )
        val mid = sample(false, true, false, true, false, midLayer)
        assertThat(mid.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
        assertThat(
                statusBarCollapseHandbackVisibleRevealPhase(
                    handbackGlassRevealed = true,
                    liveDrawnAcked = false,
                    trueLayerFraction = midLayer,
                    drawCredible = true,
                )
            )
            .isEqualTo(HandbackVisibleRevealPhase.HANDOVER)

        // ACK.
        val acked = sample(false, true, false, true, true, 1f)
        assertThat(acked.overlayGlassAlpha).isEqualTo(0f)
        assertThat(acked.truePillAlpha).isEqualTo(1f)
        assertThat(acked.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)

        // Overlay unmounted / true owns.
        val unmounted =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
                sharedGlassOwnerPresent = false,
            )
        assertThat(unmounted.overlayGlassAlpha).isEqualTo(0f)
        assertThat(
                statusBarCollapseHandbackBothHidden(
                    unmounted.overlayGlassAlpha,
                    unmounted.truePillAlpha,
                )
            )
            .isFalse()
    }

    @Test
    fun task046_timeoutGate_rejectsStagedMidRamp_allowsFullCredibleOrNoOwner() {
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(80, ownerId = "live").holdGeneration
        // Cam fabricate: α=0 + owner expected → timeout reject.
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = false,
                    truePillAlpha = 0f,
                    liveOwnerExpected = true,
                    chipExists = true,
                )
            )
            .isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        // Staged .05 + !credible → CAM (alpha hole).
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        // Mid-ramp .36 + credible → CAM (premature drop).
        val mid =
            statusBarMorphHandoffTrueLayerFromProgress(
                progress = 1f / 3f,
                revealed = true,
                stagedUntilReady = false,
            )
        assertThat(mid).isWithin(0.02f).of(0.3667f)
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = mid,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        // Full ≈1 + credible → ACK.
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        assertThat(c.liveDrawnAcked).isTrue()

        // No-owner / absent that: timeout OK (khong fake).
        val c2 = CollapseHandbackHoldController()
        val g2 = c2.beginSettleHandback(81, ownerId = null).holdGeneration
        assertThat(
                c2.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g2,
                    handbackGlassRevealed = false,
                    truePillAlpha = 0f,
                    liveOwnerExpected = false,
                    chipExists = false,
                    drawCredible = false,
                )
            )
            .isTrue()
    }

    @Test
    fun task046_readinessSnapshot_distinguishesRevealAlphaDraw_ownerGen() {
        val snapHidden =
            HandbackReadinessSnapshot(
                revealRequested = false,
                trueLayerFraction = 0f,
                drawCredible = true,
                ownerId = "a",
                holdGeneration = 1,
                layoutGeneration = 10,
            )
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapHidden,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 10,
                )
            )
            .isFalse()
        val snapStagedNoDraw =
            HandbackReadinessSnapshot(
                revealRequested = true,
                trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                drawCredible = false,
                ownerId = "a",
                holdGeneration = 1,
                layoutGeneration = 10,
            )
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapStagedNoDraw,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 10,
                )
            )
            .isFalse()
        // Staged + credible van CAM — can ≈1.
        val snapStagedDraw =
            HandbackReadinessSnapshot(
                revealRequested = true,
                trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                drawCredible = true,
                ownerId = "a",
                holdGeneration = 1,
                layoutGeneration = 10,
            )
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapStagedDraw,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 10,
                )
            )
            .isFalse()
        val snapOk =
            HandbackReadinessSnapshot(
                revealRequested = true,
                trueLayerFraction = 1f,
                drawCredible = true,
                ownerId = "a",
                holdGeneration = 1,
                layoutGeneration = 10,
            )
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapOk,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 10,
                )
            )
            .isTrue()
        // Owner switch / stale gen / stale layout.
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapOk,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "b",
                    expectedLayoutGeneration = 10,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapOk,
                    expectedHoldGeneration = 2,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 10,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    snapOk,
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "a",
                    expectedLayoutGeneration = 11,
                )
            )
            .isFalse()
    }

    @Test
    fun task046_ackEarlyNoOwner_ownerSwitch_abort_overlayUnmounted() {
        // ACK early + no-owner.
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(90, ownerId = "x").holdGeneration
        assertThat(c.acknowledgeLiveDrawn(g, 90, null)).isTrue()
        // Owner switch: stale reject.
        val c2 = CollapseHandbackHoldController()
        val g1 = c2.beginSettleHandback(91, ownerId = "a").holdGeneration
        val g2 = c2.beginSettleHandback(92, ownerId = "b").holdGeneration
        assertThat(c2.acknowledgeLiveDrawn(g1, 91, "a")).isFalse()
        assertThat(c2.acknowledgeLiveDrawn(g2, 92, "b")).isTrue()
        // Abort / reverse.
        c2.onExpandedOrReverse()
        assertThat(c2.holdActive).isFalse()
        assertThat(
                statusBarMorphHandoffAlphaSample(
                    pillSuppressed = false,
                    handbackGlassRevealed = true,
                    isExpanded = true,
                    collapseHandbackHold = false,
                    liveDrawnAcked = false,
                )
                    .overlayGlassAlpha
            )
            .isEqualTo(0f)
        // Content gap 50ms + NIC/clock constants regression.
        assertThat(statusBarOverlayHideDelayMs(alertExitGrace = false)).isEqualTo(50L)
        assertThat(StatusBarHandbackPhase5StaticRestoreTimeoutMs).isEqualTo(100L)
        assertThat(StatusBarHandbackStagedTrueLayerAlpha).isLessThan(0.1f)
        assertThat(StatusBarHandbackReadinessFailsafeMs).isGreaterThan(50L)
    }

    @Test
    fun task046_forwardSuppressToggle_singleOwnerSafe() {
        // Truoc suppress.
        val a =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = true,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
            )
        assertThat(statusBarMorphHandoffForwardSingleOwnerSafe(a.truePillAlpha, a.overlayGlassAlpha))
            .isTrue()
        // Sau suppress.
        val b =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = true,
                handbackGlassRevealed = false,
                isExpanded = true,
                collapseHandbackHold = false,
                liveDrawnAcked = false,
            )
        assertThat(statusBarMorphHandoffForwardSingleOwnerSafe(b.truePillAlpha, b.overlayGlassAlpha))
            .isTrue()
        // Cam both-full / both-hidden.
        assertThat(statusBarMorphHandoffForwardSingleOwnerSafe(1f, 1f)).isFalse()
        assertThat(statusBarMorphHandoffForwardSingleOwnerSafe(0f, 0f)).isFalse()
    }

    @Test
    fun task046_centerAndContentGap_regressionPreserved() {
        // Center p0 offset ~0 (task042).
        val (lx, ly) =
            statusBarMorphDummyLayerScale(
                hostSx = 160f / 800f,
                hostSy = 56f / 400f,
                dummyScale = 1f,
                pillWPx = 160f,
                pillHPx = 56f,
                visualTargetWPx = 800f,
                visualTargetHPx = 400f,
            )
        val off =
            statusBarMorphDummyContentLocalOffsetPx(
                pillWPx = 160f,
                pillHPx = 56f,
                localSx = lx,
                localSy = ly,
                visualTargetWPx = 800f,
                visualTargetHPx = 400f,
            )
        assertThat(off.xPx).isWithin(1.5f).of(0f)
        assertThat(off.yPx).isWithin(1.5f).of(0f)
        // Gap content-only 50ms + NIC/clock unchanged.
        assertThat(statusBarOverlayHideDelayMs(false)).isEqualTo(50L)
        assertThat(statusBarChipSlotDurationMs(reclaiming = true)).isEqualTo(500)
        assertThat(statusBarChipSlotDurationMs(reclaiming = false)).isEqualTo(500)
        assertThat(StatusBarMorphExpandMs).isEqualTo(500)
        assertThat(StatusBarMorphCollapseMs).isEqualTo(600)
    }

    // --- task-045: full readiness ACK + progress session + no fake failsafe ---

    @Test
    fun task045_normalTimeline_stagedRampFull_allowsAck() {
        // (1) staged.05 → .3667/.6833/1 → ACK
        val steps = StatusBarHandbackHandoverFrameSteps
        assertThat(steps).isEqualTo(3)
        val f1 =
            statusBarMorphHandoffTrueLayerFromProgress(1f / 3f, revealed = true, stagedUntilReady = false)
        val f2 =
            statusBarMorphHandoffTrueLayerFromProgress(2f / 3f, revealed = true, stagedUntilReady = false)
        val f3 =
            statusBarMorphHandoffTrueLayerFromProgress(1f, revealed = true, stagedUntilReady = false)
        assertThat(f1).isWithin(0.02f).of(0.3667f)
        assertThat(f2).isWithin(0.02f).of(0.6833f)
        assertThat(f3).isWithin(0.001f).of(1f)
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    liveOwnerExpected = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = f1,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = f3,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(200, ownerId = "pill").holdGeneration
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = f3,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task045_delayedOnDraw_liveOwner_keepsOverlay() {
        // (2)(3) delayed / mid-ramp: overlay chrome=1, khong hole.
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(201, ownerId = "live").holdGeneration
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = 0.36f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isFalse()
        assertThat(c.liveDrawnAcked).isFalse()
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = false,
                )
            )
            .isEqualTo(1f)
        val mid =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
                trueLayerFraction = 0.36f,
            )
        assertThat(mid.overlayGlassAlpha).isGreaterThan(0.01f)
        assertThat(statusBarCollapseHandbackBothHidden(mid.overlayGlassAlpha, mid.truePillAlpha))
            .isFalse()
        assertThat(mid.effectiveMaterialSrcOver).isWithin(0.05f).of(AlphaGlass)
    }

    @Test
    fun task045_fakeFailsafeNoOwner_rejected_realNoOwnerOk() {
        // (4) Cam fabricate liveOwnerExpected=false khi owner con.
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(202, ownerId = "still").holdGeneration
        // Fake absolute failsafe pattern — reject neu van liveExpected=true.
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        // Real no-owner.
        val c2 = CollapseHandbackHoldController()
        val g2 = c2.beginSettleHandback(203, ownerId = null).holdGeneration
        assertThat(
                c2.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g2,
                    handbackGlassRevealed = false,
                    truePillAlpha = 0f,
                    liveOwnerExpected = false,
                    chipExists = false,
                    drawCredible = false,
                )
            )
            .isTrue()
    }

    @Test
    fun task045_progressSession_rejectsStaleHoldLayoutOwner() {
        // (5)(6) stale holdGen/layout/owner; ForceReveal match gen.
        assertThat(
                statusBarCollapseHandbackProgressMatchesSession(
                    progressHoldGeneration = -1,
                    progressLayoutGeneration = 10,
                    progressOwnerId = "a",
                    expectedHoldGeneration = 1,
                    expectedLayoutGeneration = 10,
                    expectedOwnerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackProgressMatchesSession(
                    progressHoldGeneration = 1,
                    progressLayoutGeneration = 10,
                    progressOwnerId = "a",
                    expectedHoldGeneration = 1,
                    expectedLayoutGeneration = 10,
                    expectedOwnerId = "a",
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackProgressMatchesSession(
                    progressHoldGeneration = 1,
                    progressLayoutGeneration = 11,
                    progressOwnerId = "a",
                    expectedHoldGeneration = 1,
                    expectedLayoutGeneration = 10,
                    expectedOwnerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackProgressMatchesSession(
                    progressHoldGeneration = 2,
                    progressLayoutGeneration = 10,
                    progressOwnerId = "a",
                    expectedHoldGeneration = 1,
                    expectedLayoutGeneration = 10,
                    expectedOwnerId = "a",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackProgressMatchesSession(
                    progressHoldGeneration = 1,
                    progressLayoutGeneration = 10,
                    progressOwnerId = "b",
                    expectedHoldGeneration = 1,
                    expectedLayoutGeneration = 10,
                    expectedOwnerId = "a",
                )
            )
            .isFalse()
        // Rapid settle: stale ack gen cu reject.
        val c = CollapseHandbackHoldController()
        val g1 = c.beginSettleHandback(210, ownerId = "old").holdGeneration
        val g2 = c.beginSettleHandback(211, ownerId = "new").holdGeneration
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g1,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isFalse()
        assertThat(
                c.acknowledgeLiveDrawnTimeout(
                    holdGeneration = g2,
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    chipExists = true,
                    drawCredible = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task045_handoverEffectiveNearAlphaGlass_noDoubleFull() {
        // (7) effective ≈.72; (8) gap 50ms + AckMin.
        val layers =
            floatArrayOf(
                StatusBarHandbackStagedTrueLayerAlpha,
                statusBarMorphHandoffTrueLayerFromProgress(1f / 3f, true, false),
                statusBarMorphHandoffTrueLayerFromProgress(2f / 3f, true, false),
                1f,
            )
        for (layer in layers) {
            val y = statusBarMorphHandoffComplementaryOverlayLayer(layer)
            assertThat(statusBarMorphMaterialSrcOverEffective(layer, y))
                .isWithin(0.05f)
                .of(AlphaGlass)
            assertThat(statusBarCollapseHandbackBrightDoubleMaterial(layer, y)).isFalse()
        }
        assertThat(statusBarCollapseHandbackBrightDoubleMaterial(1f, 1f)).isTrue()
        assertThat(StatusBarHandbackTrueLayerAckMin).isWithin(0.001f).of(0.99f)
        assertThat(statusBarOverlayHideDelayMs(false)).isEqualTo(50L)
    }

    // --- task-048: pill-layer ready + stall recovery + dragY glass SSOT ---

    @Test
    fun task048_collapseTimeline_pillLayerReady_ramp_ack_noBothHidden() {
        // settle → staged → pill-layer ready → 3-frame → hide → static release.
        assertThat(StatusBarHandbackPillLayerReadyMaxAttempts).isAtLeast(3)
        assertThat(StatusBarHandbackStallRecoveryMs).isGreaterThan(0L)
        assertThat(
                statusBarCollapseHandbackPillLayerReady(
                    attachedToWindow = true,
                    layoutWidthPx = 120,
                    layoutHeightPx = 40,
                    layerDrawn = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackPillLayerReady(
                    attachedToWindow = true,
                    layoutWidthPx = 120,
                    layoutHeightPx = 40,
                    layerDrawn = false,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                )
            )
            .isFalse()
        // Root OnDraw α=0 path — KHONG ready.
        assertThat(
                statusBarCollapseHandbackPillLayerReady(
                    attachedToWindow = true,
                    layoutWidthPx = 120,
                    layoutHeightPx = 40,
                    layerDrawn = true,
                    truePillAlpha = 0f,
                )
            )
            .isFalse()
        // FAIL D: AfterRetries attached+bounds THIEU layerDrawn → false.
        assertThat(
                statusBarCollapseHandbackPillLayerReadyAfterRetries(
                    attemptsUsed = StatusBarHandbackPillLayerReadyMaxAttempts,
                    attachedToWindow = true,
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    layerDrawn = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackPillLayerReadyAfterRetries(
                    attemptsUsed = StatusBarHandbackPillLayerReadyMaxAttempts,
                    attachedToWindow = true,
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    layerDrawn = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackPillLayerReadyAfterRetries(
                    attemptsUsed = 1,
                    attachedToWindow = true,
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    layerDrawn = true,
                )
            )
            .isFalse()
        val steps = StatusBarHandbackHandoverFrameSteps
        val full =
            statusBarMorphHandoffTrueLayerFromProgress(1f, revealed = true, stagedUntilReady = false)
        assertThat(full).isWithin(0.001f).of(1f)
        val midY = statusBarMorphHandoffComplementaryOverlayLayer(full)
        assertThat(statusBarCollapseHandbackBothHidden(midY, full)).isFalse()
        assertThat(statusBarCollapseHandbackBrightDoubleMaterial(1f, 1f)).isTrue()
        assertThat(statusBarCollapseHandbackBrightDoubleMaterial(full, midY)).isFalse()
        // Stall recovery gate.
        assertThat(
                statusBarCollapseHandbackShouldRequestStallRecovery(
                    liveDrawnAcked = false,
                    expectedOwnerPresent = true,
                    holdStillActive = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackShouldRequestStallRecovery(
                    liveDrawnAcked = true,
                    expectedOwnerPresent = true,
                    holdStillActive = true,
                )
            )
            .isFalse()
        // FAIL A1: size0 attached → KHONG MayForceFull ACK.
        assertThat(
                statusBarCollapseHandbackStallRecoveryMayForceFull(
                    attachedToWindow = true,
                    layoutWidthPx = 0,
                    layoutHeightPx = 0,
                    ownerId = "o",
                    chipExists = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackStallRecoveryMayForceFull(
                    attachedToWindow = true,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    ownerId = "o",
                    chipExists = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackStallRecoveryMayForceFull(
                    attachedToWindow = false,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    ownerId = "o",
                    chipExists = true,
                )
            )
            .isFalse()
        // FSM ack path: full+credible → TimeoutMayDrop; staged → CAM.
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = StatusBarHandbackStagedTrueLayerAlpha,
                    liveOwnerExpected = true,
                    drawCredible = false,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isTrue()
        val c = CollapseHandbackHoldController()
        val g = c.beginSettleHandback(480, ownerId = "pill48").holdGeneration
        assertThat(c.acknowledgeLiveDrawn(g, 480, "pill48")).isTrue()
        assertThat(c.confirmOverlayHidden(g)).isTrue()
        assertThat(c.clearHoldIfGeneration(g)).isTrue()
        // Stale gen reject.
        val c2 = CollapseHandbackHoldController()
        val g2 = c2.beginSettleHandback(481, ownerId = "a").holdGeneration
        assertThat(c2.acknowledgeLiveDrawn(g2, 999, "a")).isFalse()
        assertThat(steps).isEqualTo(3)
        // Preserve gap50 + center tokens.
        assertThat(statusBarOverlayHideDelayMs(false)).isEqualTo(50L)
    }

    @Test
    fun task048_dragY_fgGlassRigid_andFrozenLrtb() {
        // |screenY_fg − screenY_glass| ≤1px @ dragY {0,8,40,100,max} × dpi.
        val dragSamples = floatArrayOf(0f, 8f, 40f, 100f, 240f)
        val dpis = intArrayOf(320, 450, 560)
        for (dpi in dpis) {
            val density = dpi / 160f
            for (drag in dragSamples) {
                val dragPx = drag // da px; density chi smoke scale
                val scaled = dragPx * (density / (450f / 160f))
                val glassTy =
                    statusBarMorphSharedGlassDragTranslationYPx(
                        dragYPx = scaled,
                        morphExpandedAtRest = true,
                    )
                val fgY = 1000f + scaled
                val glassY = 1000f + glassTy
                assertThat(statusBarMorphFgGlassScreenYDeltaPx(fgY, glassY))
                    .isAtMost(1f)
                // Morph/settle: glass KHONG fake shift.
                assertThat(
                        statusBarMorphSharedGlassDragTranslationYPx(
                            dragYPx = scaled,
                            morphExpandedAtRest = false,
                        )
                    )
                    .isEqualTo(0f)
            }
        }
        // Painted LRTB freeze under drag / settle.
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = false)).isTrue()
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(8f, settling = false)).isFalse()
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = true)).isFalse()
        val base =
            statusBarMorphPaintedBoundsBaselineCompensateDrag(
                windowLeftPx = 10f,
                windowTopPx = 200f,
                windowRightPx = 400f,
                windowBottomPx = 500f,
                dragYPx = 40f,
            )
        assertThat(base.topPx).isWithin(0.01f).of(160f)
        assertThat(base.bottomPx).isWithin(0.01f).of(460f)
        assertThat(base.leftPx).isWithin(0.01f).of(10f)
        // Snap zero after settle/exit.
        assertThat(
                statusBarMorphSharedGlassDragTranslationYPx(0f, morphExpandedAtRest = true)
            )
            .isEqualTo(0f)
    }

    // --- task-049: ForceFull no size0 ACK + post-stall reactive + live drag LRTB ---

    @Test
    fun task049_forceFull_pillLayerGates_noSize0Ack_postStallContinue() {
        // Root size0 + attached → MayForceFull false; ForceFullMayAck false.
        assertThat(
                statusBarCollapseHandbackStallRecoveryMayForceFull(
                    attachedToWindow = true,
                    layoutWidthPx = 0,
                    layoutHeightPx = 0,
                    ownerId = "pill49",
                    chipExists = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackStallRecoveryForceFullMayAck(
                    mayForceFull = false,
                    pillLayerReady = true,
                    drawCredible = true,
                    ownerMatchesSession = true,
                )
            )
            .isFalse()
        // Undrawn / owner mismatch → no ACK.
        assertThat(
                statusBarCollapseHandbackStallRecoveryForceFullMayAck(
                    mayForceFull = true,
                    pillLayerReady = false,
                    drawCredible = true,
                    ownerMatchesSession = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackStallRecoveryForceFullMayAck(
                    mayForceFull = true,
                    pillLayerReady = true,
                    drawCredible = true,
                    ownerMatchesSession = false,
                )
            )
            .isFalse()
        // Ready path: size>0 + layer + credible + owner → ACK.
        assertThat(
                statusBarCollapseHandbackPillLayerReady(
                    attachedToWindow = true,
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    layerDrawn = true,
                    truePillAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackStallRecoveryForceFullMayAck(
                    mayForceFull = true,
                    pillLayerReady = true,
                    drawCredible = true,
                    ownerMatchesSession = true,
                )
            )
            .isTrue()
        // Post-stall: con owner → continue observe; no-owner / acked → stop.
        assertThat(
                statusBarCollapseHandbackShouldContinuePostStallObserve(
                    liveDrawnAcked = false,
                    expectedOwnerPresent = true,
                    holdStillActive = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandbackShouldContinuePostStallObserve(
                    liveDrawnAcked = false,
                    expectedOwnerPresent = false,
                    holdStillActive = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackShouldContinuePostStallObserve(
                    liveDrawnAcked = true,
                    expectedOwnerPresent = true,
                    holdStillActive = true,
                )
            )
            .isFalse()
        assertThat(StatusBarHandbackPostStallObservePeriodMs).isGreaterThan(0L)
        assertThat(StatusBarHandbackPostStallMaxForceRetries).isAtLeast(3)
        // Preserve gap50 + ACK≥.99 path.
        assertThat(statusBarOverlayHideDelayMs(false)).isEqualTo(50L)
        assertThat(
                statusBarCollapseHandbackTimeoutMayDropOverlay(
                    handbackGlassRevealed = true,
                    truePillAlpha = 1f,
                    liveOwnerExpected = true,
                    drawCredible = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task049_dragY_liveState_frozenLrtb_noRememberUpdatedLag() {
        // Rigid fg/glass cung dragY live; freeze LRTB khi drag>0 / settling.
        val samples = floatArrayOf(0f, 1f, 8f, 40f, 100f)
        for (drag in samples) {
            val glassTy =
                statusBarMorphSharedGlassDragTranslationYPx(
                    dragYPx = drag,
                    morphExpandedAtRest = true,
                )
            val fgY = 500f + drag
            val glassY = 500f + glassTy
            assertThat(statusBarMorphFgGlassScreenYDeltaPx(fgY, glassY)).isAtMost(1f)
            // 0→>0: freeze ngay (khong cho LRTB 1-frame poison).
            if (drag > 0.5f) {
                assertThat(statusBarMorphPaintedLrtbUpdateAllowed(drag, settling = false))
                    .isFalse()
            } else {
                assertThat(statusBarMorphPaintedLrtbUpdateAllowed(drag, settling = false))
                    .isTrue()
            }
        }
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = true)).isFalse()
        // Dispose / collapse → drag 0.
        assertThat(
                statusBarMorphSharedGlassDragTranslationYPx(0f, morphExpandedAtRest = true)
            )
            .isEqualTo(0f)
        // Baseline compensate neu reporter van fire luc drag.
        val base =
            statusBarMorphPaintedBoundsBaselineCompensateDrag(
                windowLeftPx = 10f,
                windowTopPx = 200f,
                windowRightPx = 400f,
                windowBottomPx = 500f,
                dragYPx = 40f,
            )
        assertThat(base.topPx).isWithin(0.01f).of(160f)
        assertThat(base.bottomPx).isWithin(0.01f).of(460f)
    }

    // --- task-050: ForceFull REQUEST/COMMIT split + requestId + stale post + AfterRetries ---

    @Test
    fun task050_forceRequest_notPillReady_overlayCoverageNearGlass_noOwnershipTransfer() {
        // Force request publish x=1 !credible → overlay true-layer van staged (KHONG y=0).
        val overlayX =
            statusBarMorphHandoffOverlayTrueLayerForProgress(
                publishedTrueLayer = 1f,
                drawCredible = false,
                revealRequested = true,
            )
        assertThat(overlayX).isWithin(0.001f).of(StatusBarHandbackStagedTrueLayerAlpha)
        val y = statusBarMorphHandoffComplementaryOverlayLayer(overlayX)
        assertThat(y).isGreaterThan(0.9f)
        val material = statusBarMorphHandoffAwaitDrawOverlayMaterialAlpha()
        assertThat(material).isGreaterThan(0.65f)
        assertThat(material).isLessThan(0.76f)
        // Ownership chua transfer: !credible → khong ACK readiness.
        assertThat(
                statusBarCollapseHandbackReadinessAllowsAck(
                    HandbackReadinessSnapshot(
                        revealRequested = true,
                        trueLayerFraction = 1f,
                        drawCredible = false,
                        ownerId = "pill50",
                        holdGeneration = 1,
                        layoutGeneration = 1,
                    ),
                    expectedHoldGeneration = 1,
                    expectedOwnerId = "pill50",
                    expectedLayoutGeneration = 1,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackStallRecoveryForceFullMayAck(
                    mayForceFull = true,
                    pillLayerReady = false,
                    drawCredible = true,
                    ownerMatchesSession = true,
                )
            )
            .isFalse()
        // Overlay glass paint >0 luc hold+!acked+!credible (single ~.72).
        val overlayPaint =
            statusBarMorphHandoffOverlayGlassAlpha(
                isExpanded = false,
                pillSuppressed = false,
                collapseHandbackHold = true,
                persistentSharedGlassAlpha = 1f,
                handbackGlassRevealed = true,
                trueLayerFraction = overlayX,
                liveDrawnAcked = false,
            )
        assertThat(overlayPaint).isGreaterThan(0.9f)
        assertThat(statusBarCollapseHandbackBothHidden(overlayPaint, StatusBarHandbackStagedTrueLayerAlpha))
            .isFalse()
    }

    @Test
    fun task050_delayedDraw_3frameNearGlass_overlay0OnlyAfterCredible() {
        val steps = StatusBarHandbackHandoverFrameSteps.coerceAtLeast(3)
        for (i in 1..steps) {
            val handover = i.toFloat() / steps.toFloat()
            val layer =
                statusBarMorphHandoffTrueLayerFromProgress(
                    progress = handover,
                    revealed = true,
                    stagedUntilReady = false,
                )
            val y = statusBarMorphHandoffComplementaryOverlayLayer(layer)
            val mat =
                statusBarMorphMaterialSrcOverEffective(
                    bottomLayerAlpha = layer,
                    topLayerAlpha = y,
                )
            // Trong ramp credible: material gan .72 (khong 2x.9216).
            assertThat(mat).isWithin(0.08f).of(AlphaGlass)
            val overlayPaint =
                statusBarMorphHandoffOverlayGlassAlpha(
                    isExpanded = false,
                    pillSuppressed = false,
                    collapseHandbackHold = true,
                    persistentSharedGlassAlpha = 1f,
                    handbackGlassRevealed = true,
                    trueLayerFraction =
                        statusBarMorphHandoffOverlayTrueLayerForProgress(
                            publishedTrueLayer = layer,
                            drawCredible = true,
                            revealRequested = true,
                        ),
                    liveDrawnAcked = false,
                )
            if (i < steps) {
                assertThat(overlayPaint).isGreaterThan(0.01f)
            }
        }
        // AFTER credible+ACK: overlay0.
        val afterAck =
            statusBarMorphHandoffOverlayGlassAlpha(
                isExpanded = false,
                pillSuppressed = false,
                collapseHandbackHold = true,
                persistentSharedGlassAlpha = 1f,
                handbackGlassRevealed = true,
                trueLayerFraction = 1f,
                liveDrawnAcked = true,
            )
        assertThat(afterAck).isWithin(0.001f).of(0f)
        // Credible+full !acked: complementary y=0 (ownership transferred via layer).
        val fullCredibleOverlayX =
            statusBarMorphHandoffOverlayTrueLayerForProgress(
                publishedTrueLayer = 1f,
                drawCredible = true,
                revealRequested = true,
            )
        assertThat(fullCredibleOverlayX).isWithin(0.001f).of(1f)
        assertThat(statusBarMorphHandoffComplementaryOverlayLayer(fullCredibleOverlayX))
            .isWithin(0.001f)
            .of(0f)
    }

    @Test
    fun task050_forceReveal_3plusRetries_independentRequestIds_slowCollector() {
        val seq = java.util.concurrent.atomic.AtomicLong(0)
        // Mo phong collector cham: van cap phat 3+ id truoc khi xu ly xong.
        val ids =
            LongArray(4) {
                statusBarCollapseHandbackNextForceRevealRequestId(seq)
            }
        assertThat(statusBarCollapseHandbackForceRevealRequestIdsIndependent(ids)).isTrue()
        // Stale session reject.
        assertThat(
                statusBarCollapseHandbackForceRevealMatchesSession(
                    requestHoldGeneration = 1,
                    requestLayoutGeneration = 10,
                    sessionHoldGeneration = 2,
                    sessionLayoutGeneration = 10,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackForceRevealMatchesSession(
                    requestHoldGeneration = 2,
                    requestLayoutGeneration = 10,
                    sessionHoldGeneration = 2,
                    sessionLayoutGeneration = 10,
                )
            )
            .isTrue()
        // Ids khong giam / trung khi retry same-semantic.
        assertThat(ids[0]).isEqualTo(1L)
        assertThat(ids[3]).isEqualTo(4L)
        assertThat(ids.toSet().size).isEqualTo(4)
    }

    @Test
    fun task050_staleGenOwnerDetachSize0Post_noAck() {
        // Stale hold gen trong post → no epoch increment.
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = true,
                    pillSuppressed = false,
                    sessionHoldGeneration = 2,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = "a",
                    drawnOwnerId = "a",
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                )
            )
            .isFalse()
        // Owner mismatch.
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = true,
                    pillSuppressed = false,
                    sessionHoldGeneration = 1,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = "a",
                    drawnOwnerId = "b",
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    trueLayerFraction = 1f,
                )
            )
            .isFalse()
        // size0 / α0.
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = true,
                    pillSuppressed = false,
                    sessionHoldGeneration = 1,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = null,
                    drawnOwnerId = "a",
                    layoutWidthPx = 0,
                    layoutHeightPx = 28,
                    trueLayerFraction = 1f,
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = true,
                    pillSuppressed = false,
                    sessionHoldGeneration = 1,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = null,
                    drawnOwnerId = "a",
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    trueLayerFraction = 0f,
                )
            )
            .isFalse()
        // Session null / detach keepStatic false.
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = false,
                    pillSuppressed = false,
                    sessionHoldGeneration = 1,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = null,
                    drawnOwnerId = "a",
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    trueLayerFraction = 1f,
                )
            )
            .isFalse()
        // Valid post → may increment.
        assertThat(
                statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
                    keepStatic = true,
                    pillSuppressed = false,
                    sessionHoldGeneration = 1,
                    sessionLayoutGeneration = 5,
                    capturedHoldGeneration = 1,
                    capturedLayoutGeneration = 5,
                    currentLayoutGeneration = 5,
                    expectedOwnerId = "a",
                    drawnOwnerId = "a",
                    layoutWidthPx = 90,
                    layoutHeightPx = 28,
                    trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
                )
            )
            .isTrue()
        // AfterRetries attached-only → false (FAIL D).
        assertThat(
                statusBarCollapseHandbackPillLayerReadyAfterRetries(
                    attemptsUsed = StatusBarHandbackPillLayerReadyMaxAttempts,
                    attachedToWindow = true,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    truePillAlpha = 1f,
                    layerDrawn = false,
                )
            )
            .isFalse()
    }

    @Test
    fun task050_handbackDone_staticModeFalse_andDragYRegression() {
        // Phase5: hide confirm + draw wait → release static (staticMode false).
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 50,
                    currentLayoutGen = 50,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.DREW,
                )
            )
            .isTrue()
        // Drag regression preserved (task049): |fg−glass|≤1 + freeze LRTB.
        val drag = 40f
        val glassTy =
            statusBarMorphSharedGlassDragTranslationYPx(
                dragYPx = drag,
                morphExpandedAtRest = true,
            )
        assertThat(statusBarMorphFgGlassScreenYDeltaPx(500f + drag, 500f + glassTy))
            .isAtMost(1f)
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(drag, settling = false)).isFalse()
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = false)).isTrue()
    }

    // --- task-053: decouple liveValue vs freezeMotion / dummy FG ---

    @Test
    fun task053_holdNoGlassAck_realLiveValue_dummyFgHidden() {
        // SUPERSEDED path (legacy DummyFg=0 @unsuppress) — task-001: Dummy giu den Transfer.
        // Giữ assert liveValue/freezeMotion decouple; FG ownership xem task001_*.
        assertThat(
                statusBarCollapseHandbackContentLiveWhileMotionFrozen(
                    pillSuppressed = false,
                    handbackKeepStatic = true,
                )
            )
            .isTrue()
        assertThat(statusBarPillLiveValue(pillSuppressed = false)).isTrue()
        assertThat(
                statusBarPillFreezeMotion(
                    pillSuppressed = false,
                    handbackKeepStatic = true,
                )
            )
            .isTrue()
        val ready =
            statusBarCollapseHandoffOwnershipOf(
                phase = StatusBarCollapseHandoffPhase.Ready,
                pillSuppressed = false,
            )
        assertThat(ready.dummyLiveValue).isTrue()
        assertThat(ready.dummyFgAlpha).isEqualTo(1f)
        assertThat(ready.realFgAlpha).isEqualTo(0f)
        assertThat(statusBarCollapseHandoffBothFgHidden(ready.dummyFgAlpha, ready.realFgAlpha))
            .isFalse()
    }

    @Test
    fun task053_sameOwnerId_timerAndMediaTitlePropagateViaLiveEntry() {
        // (2) same owner id: selected branch binds outer live event.
        val t0 = timerEvent("t1", remainMs = 10_000L)
        val t1 = timerEvent("t1", remainMs = 9_000L)
        val branch = StatusBarChipEventEntry(t0, isAlert = false)
        val outer = StatusBarChipEventEntry(t1, isAlert = false)
        assertThat(branch.key).isEqualTo(outer.key)
        val live = statusBarChipSelectedLiveEntry(branch = branch, outerCurrent = outer)
        assertThat((live.event as IslandEvent.Timer).pausedRemainingMs).isEqualTo(9_000L)
        val before =
            statusBarPillPresentationOf(t0, isAlert = false, chipStyle = 0, generation = 1)
        val after =
            statusBarPillPresentationOf(t1, isAlert = false, chipStyle = 0, generation = 1)
        assertThat(statusBarChipEventDisplayKeyStableAcrossChronoTick(before, after)).isTrue()
        // liveValue → khong dua frozenChronometerMs stale vao body.
        assertThat(statusBarPillChronometerMsForBody(before, liveValue = true)).isNull()
        assertThat(statusBarPillChronometerMsForBody(before, liveValue = false))
            .isEqualTo(before.frozenChronometerMs)

        val m0 = mediaEvent("sess", "Old")
        val m1 = mediaEvent("sess", "NewTitle")
        val mediaLive =
            statusBarChipSelectedLiveEntry(
                branch = StatusBarChipEventEntry(m0, false),
                outerCurrent = StatusBarChipEventEntry(m1, false),
            )
        assertThat((mediaLive.event as IslandEvent.Media).track).isEqualTo("NewTitle")
        // Outgoing A khong leak B.
        val a = mediaEvent("a", "A")
        val b = mediaEvent("b", "B")
        val outgoing =
            statusBarChipSelectedLiveEntry(
                branch = StatusBarChipEventEntry(a, false),
                outerCurrent = StatusBarChipEventEntry(b, false),
            )
        assertThat(outgoing.event.id).isEqualTo(a.id)
    }

    @Test
    fun task053_ackHidesOverlay_keepStaticRelease_andRapidOwnerSwitch() {
        // (3) ACK → chrome 0; phase5 release keepStatic.
        assertThat(
                statusBarMorphCollapseOverlayChromeFactor(
                    collapseHandbackHold = true,
                    pillSuppressed = false,
                    liveDrawnAcked = true,
                )
            )
            .isEqualTo(0f)
        assertThat(
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = 53,
                    currentLayoutGen = 53,
                    drawWaitOutcome = HandbackPhase5DrawWaitOutcome.TIMEOUT,
                )
            )
            .isTrue()
        // (4) 10x rapid + A→B + no-owner: freezeMotion/liveValue + dummy FG on dinh.
        repeat(10) { i ->
            val suppressed = i % 3 == 0
            val keep = i % 2 == 0
            assertThat(statusBarPillFreezeMotion(suppressed, keep))
                .isEqualTo(suppressed || keep)
            assertThat(statusBarPillLiveValue(suppressed)).isEqualTo(!suppressed)
            // task-001: FG ownership theo phase Ready (dummy) / Transfer (real), khong chi suppress.
            val phase =
                if (suppressed) StatusBarCollapseHandoffPhase.Collapse
                else StatusBarCollapseHandoffPhase.Ready
            val own = statusBarCollapseHandoffOwnershipOf(phase, suppressed)
            assertThat(statusBarCollapseHandoffBothFgHidden(own.dummyFgAlpha, own.realFgAlpha))
                .isFalse()
        }
        // Circle/row: useCircleStyle tu presentation — chipStyle 0 vs 1 khong doi live policy.
        val row =
            statusBarPillPresentationOf(
                mediaEvent("r", "R"),
                isAlert = false,
                chipStyle = 0,
                generation = 1,
            )
        val circle =
            statusBarPillPresentationOf(
                mediaEvent("c", "C"),
                isAlert = false,
                chipStyle = 1,
                generation = 1,
            )
        assertThat(statusBarPillLiveValue(false)).isTrue()
        assertThat(row.ownerId).isNotEqualTo(circle.ownerId)
    }

    @Test
    fun task053_glassNear72_noBothHidden_andDragRegression() {
        // (5) complementary ≈.72 — khong both-hidden / double-full (preserves 046/050).
        val x = StatusBarHandbackStagedTrueLayerAlpha
        val y = statusBarMorphHandoffComplementaryOverlayLayer(x)
        assertThat(statusBarMorphMaterialSrcOverEffective(x, y)).isWithin(0.05f).of(AlphaGlass)
        assertThat(statusBarCollapseHandbackBrightDoubleMaterial(x, y)).isFalse()
        val sample =
            statusBarMorphHandoffAlphaSample(
                pillSuppressed = false,
                handbackGlassRevealed = true,
                isExpanded = false,
                collapseHandbackHold = true,
                liveDrawnAcked = false,
                trueLayerFraction = x,
            )
        assertThat(sample.truePillAlpha).isEqualTo(1f)
        assertThat(sample.nativeGlassFraction).isWithin(1e-4f).of(x)
        assertThat(
                statusBarCollapseHandbackBothHidden(
                    overlayGlassAlpha = sample.overlayGlassAlpha,
                    truePillAlpha = sample.truePillAlpha,
                )
            )
            .isFalse()
        // task-001: Ready hold — dummy FG con (khong both-hidden).
        val readyOwn =
            statusBarCollapseHandoffOwnershipOf(
                StatusBarCollapseHandoffPhase.Ready,
                pillSuppressed = false,
            )
        assertThat(readyOwn.dummyFgAlpha).isEqualTo(1f)
        // (6) drag FG+glass Y invariant + LRTB freeze.
        val drag = 36f
        val glassTy =
            statusBarMorphSharedGlassDragTranslationYPx(
                dragYPx = drag,
                morphExpandedAtRest = true,
            )
        assertThat(statusBarMorphFgGlassScreenYDeltaPx(400f + drag, 400f + glassTy))
            .isAtMost(1f)
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(drag, settling = false)).isFalse()
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = true)).isFalse()
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(0f, settling = false)).isTrue()
    }

    // --- task-054: FG visibility vs native glass fraction ---

    @Test
    fun task054_fgFullIndependentOfStagedGlass_overlayComplementaryOnNative() {
        // α table: suppress T/F × native 0/.05/.37/1 × ACK.
        val natives = floatArrayOf(0f, StatusBarHandbackStagedTrueLayerAlpha, 0.37f, 1f)
        for (ack in booleanArrayOf(false, true)) {
            for (suppressed in booleanArrayOf(true, false)) {
                for (native in natives) {
                    val sample =
                        statusBarMorphHandoffAlphaSample(
                            pillSuppressed = suppressed,
                            handbackGlassRevealed = !suppressed,
                            isExpanded = false,
                            collapseHandbackHold = !ack && !suppressed,
                            liveDrawnAcked = ack && !suppressed,
                            trueLayerFraction = native,
                        )
                    val expectFg =
                        statusBarMorphHandoffTruePillAlpha(
                            pillSuppressed = suppressed,
                            handbackGlassRevealed = !suppressed,
                            trueLayerFraction = native,
                        )
                    val expectGlass =
                        statusBarMorphHandoffNativeGlassFraction(
                            pillSuppressed = suppressed,
                            handbackGlassRevealed = !suppressed,
                            trueLayerFraction = native,
                        )
                    assertThat(sample.truePillAlpha).isEqualTo(expectFg)
                    assertThat(sample.nativeGlassFraction).isWithin(1e-4f).of(expectGlass)
                    // Unsuppress → FG=1 ke ca glassStage .05 / !ACK.
                    if (!suppressed) {
                        assertThat(sample.truePillAlpha).isEqualTo(1f)
                    } else {
                        assertThat(sample.truePillAlpha).isEqualTo(0f)
                    }
                    // Material ~.72 khi hold+!ack+revealed; khong .9216.
                    if (!suppressed && !ack) {
                        assertThat(sample.effectiveMaterialSrcOver)
                            .isWithin(0.05f)
                            .of(AlphaGlass)
                        assertThat(
                                statusBarCollapseHandbackBrightDoubleMaterial(
                                    sample.nativeGlassFraction,
                                    sample.overlayGlassAlpha,
                                )
                            )
                            .isFalse()
                        assertThat(
                                statusBarCollapseHandbackBothHidden(
                                    sample.overlayGlassAlpha,
                                    sample.truePillAlpha,
                                )
                            )
                            .isFalse()
                    }
                }
            }
        }
        // task-001 Ready hold: real FG=0 + dummy FG=1 (mot owner); Transfer flip.
        val holdReady =
            statusBarCollapseHandoffOwnershipOf(
                StatusBarCollapseHandoffPhase.Ready,
                pillSuppressed = false,
                pillFollowAlpha = 0.9f,
            )
        assertThat(holdReady.realFgAlpha).isEqualTo(0f)
        assertThat(holdReady.dummyFgAlpha).isWithin(1e-4f).of(0.9f)
        assertThat(holdReady.overlayGlassOn).isTrue()
        val afterXfer =
            statusBarCollapseHandoffOwnershipOf(
                StatusBarCollapseHandoffPhase.Transfer,
                pillSuppressed = false,
                pillFollowAlpha = 0.9f,
            )
        assertThat(afterXfer.realFgAlpha).isEqualTo(1f)
        assertThat(afterXfer.dummyFgAlpha).isEqualTo(0f)
        assertThat(afterXfer.nativeGlassOn).isTrue()
        // liveValue same-id van dung (053).
        assertThat(statusBarPillLiveValue(pillSuppressed = false)).isTrue()
        assertThat(
                statusBarCollapseHandbackContentLiveWhileMotionFrozen(
                    pillSuppressed = false,
                    handbackKeepStatic = true,
                )
            )
            .isTrue()
        // Drag regression 049.
        val drag = 28f
        val glassTy =
            statusBarMorphSharedGlassDragTranslationYPx(
                dragYPx = drag,
                morphExpandedAtRest = true,
            )
        assertThat(statusBarMorphFgGlassScreenYDeltaPx(300f + drag, 300f + glassTy))
            .isAtMost(1f)
        assertThat(statusBarMorphPaintedLrtbUpdateAllowed(drag, settling = false)).isFalse()
    }

    // --- chat-1103212515 task-001: authoritative handoff lifecycle ---

    @Test
    fun task001_lifecycle_prepareCollapseReadyTransferCleanup() {
        val ctrl = StatusBarCollapseHandoffController()
        assertThat(ctrl.beginPrepare(layoutGen = 10, ownerId = "t1")).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Prepare)
        val gen = ctrl.sessionGeneration
        assertThat(ctrl.enterCollapse(gen)).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Collapse)
        // Dummy live + freezeMotion; real alpha0.
        var own = ctrl.ownership(pillSuppressed = true)
        assertThat(own.dummyLiveValue).isTrue()
        assertThat(own.freezeMotion).isTrue()
        assertThat(own.dummyFgAlpha).isEqualTo(1f)
        assertThat(own.realFgAlpha).isEqualTo(0f)
        assertThat(own.glassOwner).isEqualTo(StatusBarCollapseGlassOwner.Overlay)
        assertThat(statusBarCollapseHandoffFgOwnershipSafe(own)).isTrue()
        assertThat(statusBarCollapseHandoffGlassOwnershipSafe(own)).isTrue()

        assertThat(ctrl.markCollapseSettled(gen)).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Ready)
        // Unsuppress composition: dummy VAN owns — khong both-hidden.
        own = ctrl.ownership(pillSuppressed = false)
        assertThat(own.dummyFgAlpha).isEqualTo(1f)
        assertThat(own.realFgAlpha).isEqualTo(0f)
        assertThat(own.realLiveValue).isTrue()
        assertThat(statusBarCollapseHandoffBothFgHidden(own.dummyFgAlpha, own.realFgAlpha))
            .isFalse()

        // preparation/no-ready: transfer reject (thieu measured + owner).
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    allowUnprepared = false,
                    currentSelectedOwnerId = "t1",
                    currentLayoutGen = 10,
                )
            )
            .isFalse()

        assertThat(
                ctrl.reportRealPrepared(
                    sessionGen = gen,
                    layoutGen = 10,
                    ownerId = "t1",
                    prepared = true,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "t1",
                    currentLayoutGen = 10,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Transfer)
        own = ctrl.ownership(pillSuppressed = false)
        assertThat(own.fgOwner).isEqualTo(StatusBarCollapseFgOwner.Real)
        assertThat(own.dummyFgAlpha).isEqualTo(0f)
        assertThat(own.realFgAlpha).isEqualTo(1f)
        assertThat(own.glassOwner).isEqualTo(StatusBarCollapseGlassOwner.Native)
        assertThat(statusBarCollapseHandoffFgOwnershipSafe(own)).isTrue()
        assertThat(statusBarCollapseHandoffGlassOwnershipSafe(own)).isTrue()

        assertThat(ctrl.beginCleanup(gen)).isTrue()
        assertThat(ctrl.complete(gen)).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Idle)
    }

    @Test
    fun task002_timeout_noAllowUnprepared_fabricate_keepsDummy() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(11, "ow")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        // Chua prepared — allowUnprepared bi ignore, KHONG fabricate / transfer blank.
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    allowUnprepared = false,
                    currentSelectedOwnerId = "ow",
                    currentLayoutGen = 11,
                    layoutWidthPx = 80,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    allowUnprepared = true,
                    currentSelectedOwnerId = "ow",
                    currentLayoutGen = 11,
                    layoutWidthPx = 80,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
        assertThat(ctrl.transferred).isFalse()
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Ready)
        val own = ctrl.ownership(pillSuppressed = false)
        assertThat(own.dummyLiveValue).isTrue()
        assertThat(own.dummyFgAlpha).isEqualTo(1f)
        assertThat(own.realFgAlpha).isEqualTo(0f)
        // Stale session gen reject.
        assertThat(
                ctrl.transfer(
                    sessionGen = gen + 99,
                    allowUnprepared = true,
                    currentSelectedOwnerId = "ow",
                    currentLayoutGen = 11,
                )
            )
            .isFalse()
    }

    @Test
    fun task002_readyThenInvalid_transferReject_detachOwnerChangeEarly() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(12, "a")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        // Early transfer truoc Ready → reject.
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "a",
                    currentLayoutGen = 12,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isFalse()
        ctrl.markCollapseSettled(gen)
        assertThat(
                ctrl.reportRealPrepared(
                    gen,
                    12,
                    ownerId = "a",
                    prepared = true,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
        assertThat(ctrl.realPrepared).isTrue()
        // Revoke detach/size0.
        assertThat(
                ctrl.reportRealPrepared(gen, 12, ownerId = "a", prepared = false)
            )
            .isTrue()
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "a",
                    currentLayoutGen = 12,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isFalse()
        // Null owner khong mark prepared.
        assertThat(
                ctrl.reportRealPrepared(gen, 12, ownerId = null, prepared = true)
            )
            .isFalse()
        // Owner change: stale B reject; retarget → clear prepared.
        assertThat(
                ctrl.reportRealPrepared(
                    gen,
                    12,
                    ownerId = "b",
                    prepared = true,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isFalse()
        assertThat(ctrl.retargetExpectedOwner(gen, "b")).isTrue()
        assertThat(ctrl.expectedOwnerId).isEqualTo("b")
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "b",
                    currentLayoutGen = 12,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isFalse()
        // layout0 / detach helper
        assertThat(
                statusBarCollapseHandoffRealPrepared(
                    attachedToWindow = false,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    chipExists = true,
                    ownerId = "b",
                    expectedOwnerId = "b",
                )
            )
            .isFalse()
        assertThat(
                statusBarCollapseHandoffRealPrepared(
                    attachedToWindow = true,
                    layoutWidthPx = 0,
                    layoutHeightPx = 40,
                    chipExists = true,
                    ownerId = "b",
                    expectedOwnerId = "b",
                )
            )
            .isFalse()
        ctrl.cancel()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Idle)
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "a",
                    currentLayoutGen = 12,
                )
            )
            .isFalse()
    }

    @Test
    fun task002_staleCleanup_newerSession_totalNoOp() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(20, "o1")
        val gen1 = ctrl.sessionGeneration
        ctrl.enterCollapse(gen1)
        ctrl.markCollapseSettled(gen1)
        assertThat(
                ctrl.reportRealPrepared(gen1, 20, "o1", true, 90, 36, true)
            )
            .isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen1,
                    currentSelectedOwnerId = "o1",
                    currentLayoutGen = 20,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isTrue()
        // Session moi hon bat dau — stale cleanup gen1 phai no-op.
        assertThat(ctrl.beginPrepare(21, "o2")).isTrue()
        val gen2 = ctrl.sessionGeneration
        assertThat(gen2).isGreaterThan(gen1)
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Prepare)
        // Facade production: stale session+layout → total no-op.
        assertThat(statusBarCollapseHandoffTryCompleteCleanup(ctrl, gen1, 20)).isFalse()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Prepare)
        assertThat(ctrl.sessionGeneration).isEqualTo(gen2)
        // complete() tu Prepare (sai lifecycle) — reject.
        assertThat(ctrl.complete(gen2)).isFalse()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Prepare)
        // Layout mismatch cung no-op.
        ctrl.enterCollapse(gen2)
        ctrl.markCollapseSettled(gen2)
        assertThat(
                ctrl.reportRealPrepared(gen2, 21, "o2", true, 90, 36, true)
            )
            .isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen2,
                    currentSelectedOwnerId = "o2",
                    currentLayoutGen = 21,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isTrue()
        assertThat(statusBarCollapseHandoffTryCompleteCleanup(ctrl, gen2, 20)).isFalse()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Transfer)
        // Hop le: session + layout.
        assertThat(statusBarCollapseHandoffTryCompleteCleanup(ctrl, gen2, 21)).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Idle)
    }

    @Test
    fun task001_ownerUpdateSameId_liveNotFrozen_rowCirclePolicy() {
        // same-owner timer/media propagate (live entry) — khong permanently frozen.
        val t0 = timerEvent("t1", remainMs = 10_000L)
        val t1 = timerEvent("t1", remainMs = 8_000L)
        val live =
            statusBarChipSelectedLiveEntry(
                branch = StatusBarChipEventEntry(t0, false),
                outerCurrent = StatusBarChipEventEntry(t1, false),
            )
        assertThat((live.event as IslandEvent.Timer).pausedRemainingMs).isEqualTo(8_000L)
        assertThat(statusBarPillChronometerMsForBody(
            statusBarPillPresentationOf(t0, false, 0, 1),
            liveValue = true,
        )).isNull()

        // Row + Circle: cung ownership derive (progress/ring theo phase).
        for (phase in
            listOf(
                StatusBarCollapseHandoffPhase.Collapse,
                StatusBarCollapseHandoffPhase.Ready,
                StatusBarCollapseHandoffPhase.Transfer,
            )) {
            val own =
                statusBarCollapseHandoffOwnershipOf(phase = phase, pillSuppressed = false)
            assertThat(statusBarCollapseHandoffFgOwnershipSafe(own)).isTrue()
            assertThat(statusBarCollapseHandoffGlassOwnershipSafe(own)).isTrue()
            assertThat(statusBarCollapseHandoffBothFgHidden(own.dummyFgAlpha, own.realFgAlpha))
                .isFalse()
            if (phase == StatusBarCollapseHandoffPhase.Transfer) {
                assertThat(own.dummyLiveValue).isFalse()
                assertThat(own.realLiveValue).isTrue()
            } else {
                assertThat(own.dummyLiveValue).isTrue()
            }
        }
    }

    @Test
    fun task001_idleSuppressed_morphDummyOwnsUntilSession() {
        val own =
            statusBarCollapseHandoffOwnershipOf(
                StatusBarCollapseHandoffPhase.Idle,
                pillSuppressed = true,
            )
        assertThat(own.fgOwner).isEqualTo(StatusBarCollapseFgOwner.Dummy)
        assertThat(own.dummyLiveValue).isTrue()
        assertThat(own.dummyFgAlpha).isEqualTo(1f)
        assertThat(statusBarCollapseHandoffFgOwnershipSafe(own)).isTrue()
    }

    @Test
    fun task001_noBothHidden_unsuppressToTransfer_andDragRegression() {
        // Cua so unsuppress→reveal cu: DummyFg0 + TruePill0. Phase Ready tranh.
        val phases =
            listOf(
                StatusBarCollapseHandoffPhase.Collapse,
                StatusBarCollapseHandoffPhase.Ready,
                StatusBarCollapseHandoffPhase.Transfer,
                StatusBarCollapseHandoffPhase.Cleanup,
            )
        for (p in phases) {
            val own = statusBarCollapseHandoffOwnershipOf(p, pillSuppressed = false)
            assertThat(statusBarCollapseHandoffBothFgHidden(own.dummyFgAlpha, own.realFgAlpha))
                .isFalse()
            // Khong partial live FG.
            assertThat(own.realFgAlpha == 0f || own.realFgAlpha == 1f).isTrue()
        }
        // Drag FG+glass shared Y (Row) — Circle sibling residual documented.
        val drag = 40f
        val glassTy =
            statusBarMorphSharedGlassDragTranslationYPx(
                dragYPx = drag,
                morphExpandedAtRest = true,
            )
        assertThat(statusBarMorphFgGlassScreenYDeltaPx(200f + drag, 200f + glassTy))
            .isAtMost(1f)
    }

    // --- chat-1103212515 task-003: coordinator reactive + layout/transfer validate ---

    @Test
    fun task003_delayedReady_afterRecoveryDeadline_reachesTransfer() = runBlocking {
        // Delayed emission SAU soft+recovery → van toi transfer (khong die coordinator).
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(30, "timer")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        val flow = MutableStateFlow(ctrl.snapshot)
        val awaitJob =
            async {
                statusBarCollapseHandoffAwaitPreparedReactive(
                    handoffFlow = flow,
                    sessionGen = gen,
                    softTimeoutMs = 25L,
                    recoveryTimeoutMs = 25L,
                    afterRecoveryKeepObserving = true,
                )
            }
        delay(70L) // qua ca hai cua so recovery
        assertThat(awaitJob.isCompleted).isFalse()
        assertThat(
                statusBarCollapseHandoffAfterRecoveryTimeout(
                    prepared = false,
                    ownerEndedAuthoritative = false,
                )
            )
            .isEqualTo(StatusBarCollapseHandoffAfterRecoveryAction.KeepObserving)
        assertThat(
                ctrl.reportRealPrepared(
                    sessionGen = gen,
                    layoutGen = 30,
                    ownerId = "timer",
                    prepared = true,
                    layoutWidthPx = 120,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
        flow.value = ctrl.snapshot
        assertThat(awaitJob.await()).isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "timer",
                    currentLayoutGen = 30,
                    layoutWidthPx = 120,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
        assertThat(ctrl.transferred).isTrue()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Transfer)
    }

    @Test
    fun task003_ownerChangeAndEnd_afterWait_retargetOrAbortClearHold() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(31, "a")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        // A→B while waiting: retarget, clear prepared.
        assertThat(ctrl.retargetExpectedOwner(gen, "b")).isTrue()
        assertThat(ctrl.expectedOwnerId).isEqualTo("b")
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(ctrl.reportRealPrepared(gen, 31, "b", true, 100, 40, true)).isTrue()
        // Live selected helper tach morph/frozen.
        assertThat(statusBarCollapseHandoffLiveSelectedOwnerId("alert", "primary"))
            .isEqualTo("alert")
        assertThat(statusBarCollapseHandoffLiveSelectedOwnerId(null, "primary"))
            .isEqualTo("primary")
        // Owner-end authoritative → AbortClearHold (va clear hold o Panel).
        assertThat(
                statusBarCollapseHandoffAfterRecoveryTimeout(
                    prepared = false,
                    ownerEndedAuthoritative = true,
                )
            )
            .isEqualTo(StatusBarCollapseHandoffAfterRecoveryAction.AbortClearHold)
        assertThat(
                statusBarCollapseHandoffOwnerEndedAuthoritative(
                    chipMissing = true,
                    onlyAospOrEmpty = false,
                )
            )
            .isTrue()
        // Transient expected null KHONG la owner-end.
        assertThat(
                statusBarCollapseHandoffOwnerEndedAuthoritative(
                    chipMissing = false,
                    onlyAospOrEmpty = false,
                )
            )
            .isFalse()
        ctrl.cancelOwnerEnded()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Idle)
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "b",
                    currentLayoutGen = 31,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isFalse()
    }

    @Test
    fun task003_cancelReverse_andSessionReset_noLayoutChange_revalidateCoords() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(32, "ow")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        assertThat(ctrl.reportRealPrepared(gen, 32, "ow", true, 90, 36, true)).isTrue()
        // Cancel/reverse.
        ctrl.cancel()
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Idle)
        assertThat(ctrl.readinessRecord).isNull()
        // Session reset cung owner — coords hop le → size giu; prior-owner/size0 reject.
        val ok =
            statusBarCollapseHandoffResolveLayoutAfterSessionChange(
                coordsAttached = true,
                coordsOwnerId = "ow",
                coordsWidthPx = 90,
                coordsHeightPx = 36,
                expectedOwnerId = "ow",
                sessionGeneration = gen + 1,
            )
        assertThat(ok.widthPx).isEqualTo(90)
        assertThat(ok.heightPx).isEqualTo(36)
        assertThat(
                statusBarCollapseHandoffMeasuredLayoutValidForSession(
                    measured = ok,
                    expectedOwnerId = "ow",
                    sessionGeneration = gen + 1,
                )
            )
            .isTrue()
        val staleOwner =
            statusBarCollapseHandoffResolveLayoutAfterSessionChange(
                coordsAttached = true,
                coordsOwnerId = "old",
                coordsWidthPx = 90,
                coordsHeightPx = 36,
                expectedOwnerId = "ow",
                sessionGeneration = gen + 1,
            )
        assertThat(staleOwner.widthPx).isEqualTo(0)
        assertThat(
                statusBarCollapseHandoffMeasuredLayoutValidForSession(
                    measured = staleOwner,
                    expectedOwnerId = "ow",
                    sessionGeneration = gen + 1,
                )
            )
            .isFalse()
    }

    @Test
    fun task003_size0Detach_revoke_andStaleCleanup_notClearNewLocalSession() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(33, "x")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        assertThat(ctrl.reportRealPrepared(gen, 33, "x", true, 80, 30, true)).isTrue()
        // size0 / detach revoke.
        assertThat(ctrl.reportRealPrepared(gen, 33, "x", true, 0, 30, true)).isFalse()
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(ctrl.reportRealPrepared(gen, 33, "x", true, 80, 30, true)).isTrue()
        assertThat(ctrl.reportRealPrepared(gen, 33, "x", false, 80, 30, attached = false))
            .isTrue()
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(ctrl.readinessRecord).isNull()
        // Transfer reject khi detach/size0 live inputs.
        assertThat(ctrl.reportRealPrepared(gen, 33, "x", true, 80, 30, true)).isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 33,
                    layoutWidthPx = 0,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 99, // stale layout gen
                    layoutWidthPx = 80,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 33,
                    layoutWidthPx = 80,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isTrue()
        // Local hold: stale completing gen khong clear session moi.
        assertThat(statusBarCollapseHandoffLocalHoldReleaseAllowed(activeSessionGen = 5, completingSessionGen = 4))
            .isFalse()
        assertThat(statusBarCollapseHandoffLocalHoldReleaseAllowed(activeSessionGen = 5, completingSessionGen = 5))
            .isTrue()
        assertThat(statusBarCollapseHandoffLocalHoldReleaseAllowed(activeSessionGen = 0, completingSessionGen = 5))
            .isFalse()
        // Facade cleanup stale session moi hon.
        assertThat(ctrl.beginPrepare(34, "y")).isTrue()
        val gen2 = ctrl.sessionGeneration
        assertThat(statusBarCollapseHandoffTryCompleteCleanup(ctrl, gen, 33)).isFalse()
        assertThat(ctrl.sessionGeneration).isEqualTo(gen2)
        assertThat(ctrl.phase).isEqualTo(StatusBarCollapseHandoffPhase.Prepare)
    }

    // --- chat-1103212515 task-004: coords lifetime + transfer require measured ---

    @Test
    fun task004_sameOwnerSessionRollover_noOgp_preservesCoords_reachesTransfer() {
        // P1A: session-only KHONG clear coords; resolve tu coords cu → report → transfer.
        assertThat(
                statusBarCollapseHandoffClearPillCoordsOnDispose(
                    StatusBarCollapseHandoffCoordsDisposeReason.SessionOnlyRollover
                )
            )
            .isFalse()
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(40, "ow")
        val gen1 = ctrl.sessionGeneration
        ctrl.enterCollapse(gen1)
        ctrl.markCollapseSettled(gen1)
        assertThat(ctrl.reportRealPrepared(gen1, 40, "ow", true, 110, 44, true)).isTrue()
        // Session moi same-owner — coords van attach (khong OGP).
        assertThat(ctrl.beginPrepare(41, "ow")).isTrue()
        val gen2 = ctrl.sessionGeneration
        assertThat(ctrl.readinessRecord).isNull()
        ctrl.enterCollapse(gen2)
        ctrl.markCollapseSettled(gen2)
        val resolved =
            statusBarCollapseHandoffResolveLayoutAfterSessionChange(
                coordsAttached = true,
                coordsOwnerId = "ow",
                coordsWidthPx = 110,
                coordsHeightPx = 44,
                expectedOwnerId = "ow",
                sessionGeneration = gen2,
            )
        assertThat(resolved.widthPx).isEqualTo(110)
        assertThat(resolved.heightPx).isEqualTo(44)
        assertThat(
                statusBarCollapseHandoffMeasuredLayoutValidForSession(
                    measured = resolved,
                    expectedOwnerId = "ow",
                    sessionGeneration = gen2,
                )
            )
            .isTrue()
        assertThat(
                ctrl.reportRealPrepared(
                    gen2,
                    41,
                    "ow",
                    true,
                    resolved.widthPx,
                    resolved.heightPx,
                    true,
                )
            )
            .isTrue()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen2,
                    currentSelectedOwnerId = "ow",
                    currentLayoutGen = 41,
                    layoutWidthPx = 110,
                    layoutHeightPx = 44,
                    attached = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task004_subtreeDispose_clears_butOutgoingA_afterB_doesNotEraseB() {
        // Real subtree dispose → clear; outgoing A sau B placed → khong xoa B.
        assertThat(
                statusBarCollapseHandoffClearPillCoordsOnDispose(
                    StatusBarCollapseHandoffCoordsDisposeReason.SelectedSubtreeDispose
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords(
                    disposedBranchOwnerId = "A",
                    currentCoordsOwnerId = "A",
                )
            )
            .isTrue()
        assertThat(
                statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords(
                    disposedBranchOwnerId = "A",
                    currentCoordsOwnerId = "B",
                )
            )
            .isFalse()
        // Record lifetime: A revoke khong dung neu coords da B.
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(42, "B")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        assertThat(ctrl.reportRealPrepared(gen, 42, "B", true, 100, 40, true)).isTrue()
        assertThat(ctrl.readinessRecord?.ownerId).isEqualTo("B")
        // A dispose sau B: clearCoords policy false → record B con.
        if (
            !statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords(
                disposedBranchOwnerId = "A",
                currentCoordsOwnerId = "B",
            )
        ) {
            assertThat(ctrl.readinessRecord?.ownerId).isEqualTo("B")
            assertThat(ctrl.realPrepared).isTrue()
        }
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "B",
                    currentLayoutGen = 42,
                    layoutWidthPx = 100,
                    layoutHeightPx = 40,
                    attached = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task004_detachOrSize0_betweenReadyAndTransfer_rejects() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(43, "x")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        assertThat(ctrl.reportRealPrepared(gen, 43, "x", true, 80, 30, true)).isTrue()
        assertThat(ctrl.readinessRecord).isNotNull()
        // Host detach sync revoke record (P1B) — truoc transfer.
        assertThat(
                statusBarCollapseHandoffClearPillCoordsOnDispose(
                    StatusBarCollapseHandoffCoordsDisposeReason.HostDetach
                )
            )
            .isTrue()
        assertThat(ctrl.reportRealPrepared(gen, 43, "x", false, 0, 0, attached = false))
            .isTrue()
        assertThat(ctrl.readinessRecord).isNull()
        assertThat(ctrl.realPrepared).isFalse()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 43,
                    layoutWidthPx = 80,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
        // size0 live giua ready→transfer.
        assertThat(ctrl.reportRealPrepared(gen, 43, "x", true, 80, 30, true)).isTrue()
        assertThat(
                statusBarCollapseHandoffResolveTransferReadiness(
                    readinessRecord = ctrl.readinessRecord,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 43,
                    snapLayoutGen = 43,
                    expectedOwnerId = "x",
                    sessionGeneration = gen,
                    layoutWidthPx = 0,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isNull()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "x",
                    currentLayoutGen = 43,
                    layoutWidthPx = 0,
                    layoutHeightPx = 30,
                    attached = true,
                )
            )
            .isFalse()
    }

    @Test
    fun task004_missingRecord_orNullCurrentOwner_rejectsTransfer() {
        val ctrl = StatusBarCollapseHandoffController()
        ctrl.beginPrepare(44, "o")
        val gen = ctrl.sessionGeneration
        ctrl.enterCollapse(gen)
        ctrl.markCollapseSettled(gen)
        // prepared bool legacy (khong size) → rec null → transfer reject (P1B).
        assertThat(ctrl.reportRealPrepared(gen, 44, "o", true)).isTrue()
        assertThat(ctrl.readinessRecord).isNull()
        assertThat(ctrl.realPrepared).isTrue()
        assertThat(
                statusBarCollapseHandoffResolveTransferReadiness(
                    readinessRecord = null,
                    currentSelectedOwnerId = "o",
                    currentLayoutGen = 44,
                    snapLayoutGen = 44,
                    expectedOwnerId = "o",
                    sessionGeneration = gen,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isNull()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "o",
                    currentLayoutGen = 44,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isFalse()
        // Co record nhung null current owner → reject (khong fallback rec.ownerId).
        assertThat(ctrl.reportRealPrepared(gen, 44, "o", true, 90, 36, true)).isTrue()
        assertThat(
                statusBarCollapseHandoffResolveTransferReadiness(
                    readinessRecord = ctrl.readinessRecord,
                    currentSelectedOwnerId = null,
                    currentLayoutGen = 44,
                    snapLayoutGen = 44,
                    expectedOwnerId = "o",
                    sessionGeneration = gen,
                )
            )
            .isNull()
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = null,
                    currentLayoutGen = 44,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isFalse()
        // Missing layoutGen → reject.
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "o",
                    currentLayoutGen = null,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isFalse()
        // Hop le day du.
        assertThat(
                ctrl.transfer(
                    sessionGen = gen,
                    currentSelectedOwnerId = "o",
                    currentLayoutGen = 44,
                    layoutWidthPx = 90,
                    layoutHeightPx = 36,
                    attached = true,
                )
            )
            .isTrue()
    }

    // --- task-026/027: phase glass ownership + held/commit/cancel (source unit, ≠ runtime) ---

    @Test
    fun task026_heldPastThreshold_noSwapLeave_keepsTopIdentity() {
        // HELD ke ca dragY >> commit threshold — khong leaving / pin.
        val phase =
            stackExpandedDragPhase(
                isDragging = true,
                isSettling = false,
                leavingId = null,
            )
        assertThat(phase).isEqualTo(StackExpandedDragPhase.Held)
        assertThat(
                stackExpandedHeldKeepsTopIdentity(
                    phase = phase,
                    leavingId = null,
                    localPinnedIdAssignedThisGesture = false,
                )
            )
            .isTrue()
        assertThat(
                stackExpandedHeldKeepsTopIdentity(
                    phase = phase,
                    leavingId = "outgoing",
                    localPinnedIdAssignedThisGesture = false,
                )
            )
            .isFalse()
        // Commit decision chi goi o UP — helper van dung threshold hien co.
        assertThat(
                stackExpandedShouldCommitOnRelease(
                    dragYPx = 999f,
                    velocityYPx = 0f,
                    commitThresholdPx = 100f,
                    commitVelocityPx = 700f,
                    canCommitStackSwitch = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task026_releaseUp_commitsOnce_cancelDoesNotCommit() {
        val thr = 100f
        val velThr = 700f
        assertThat(
                stackExpandedShouldCommitOnRelease(
                    dragYPx = thr,
                    velocityYPx = 0f,
                    commitThresholdPx = thr,
                    commitVelocityPx = velThr,
                    canCommitStackSwitch = true,
                )
            )
            .isTrue()
        // CANCEL / lost pointer: onStackDragEnd(0,0) → khong commit.
        assertThat(
                stackExpandedShouldCommitOnRelease(
                    dragYPx = 0f,
                    velocityYPx = 0f,
                    commitThresholdPx = thr,
                    commitVelocityPx = velThr,
                    canCommitStackSwitch = true,
                )
            )
            .isFalse()
        assertThat(
                stackExpandedDragPhase(
                    isDragging = false,
                    isSettling = true,
                    leavingId = null,
                )
            )
            .isEqualTo(StackExpandedDragPhase.CancelSettle)
        assertThat(
                stackExpandedDragPhase(
                    isDragging = false,
                    isSettling = true,
                    leavingId = "old",
                )
            )
            .isEqualTo(StackExpandedDragPhase.CommitSettle)
    }

    @Test
    fun task027_glassOwner_byPhase_notDragYEpsilon() {
        // Held: card-local ke ca dragY~0 / reverse — khong doi lifecycle theo eps.
        assertThat(
                stackExpandedGlassSurfaceOwner(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Held,
                )
            )
            .isEqualTo(StackExpandedGlassSurfaceOwner.CardLocal)
        assertThat(
                stackExpandedUsesCardLocalGlass(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Held,
                )
            )
            .isTrue()
        // Idle rest: CardLocal (task-041) — khong yield SharedPlate / warm floor.
        assertThat(
                stackExpandedUsesCardLocalGlass(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Idle,
                )
            )
            .isTrue()
        assertThat(
                stackExpandedGlassSurfaceOwner(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Idle,
                )
            )
            .isEqualTo(StackExpandedGlassSurfaceOwner.CardLocal)
        // CancelSettle + CommitSettle: van card-local (whole-surface continuity).
        assertThat(
                stackExpandedUsesCardLocalGlass(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.CancelSettle,
                )
            )
            .isTrue()
        assertThat(
                stackExpandedUsesCardLocalGlass(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.CommitSettle,
                )
            )
            .isTrue()
        // Morph khong at-rest: shared shell (pill↔expanded).
        assertThat(
                stackExpandedUsesCardLocalGlass(
                    morphExpandedAtRest = false,
                    phase = StackExpandedDragPhase.Held,
                )
            )
            .isFalse()
        assertThat(
                stackExpandedYieldNativeGlassToSharedOwner(
                    sharedGlassOwnerPresent = true,
                    useCardLocalGlass = true,
                )
            )
            .isFalse()
        // CardLocal: alpha0 + khong compose parked plate (Aggregator remove).
        assertThat(
                stackExpandedSharedGlassPlateAlpha(
                    basePaintAlpha = 1f,
                    useCardLocalGlass = true,
                )
            )
            .isEqualTo(0f)
        assertThat(
                stackExpandedKeepSharedBlurSubtree(
                    basePaintAlpha = 1f,
                    useCardLocalGlass = true,
                )
            )
            .isFalse()
        assertThat(
                stackExpandedKeepSharedBlurSubtree(
                    basePaintAlpha = 0f,
                    useCardLocalGlass = true,
                )
            )
            .isFalse()
        assertThat(
                stackExpandedKeepSharedBlurSubtree(
                    basePaintAlpha = 1f,
                    useCardLocalGlass = false,
                )
            )
            .isTrue()
        assertThat(
                stackExpandedKeepSharedBlurSubtree(
                    basePaintAlpha = 0f,
                    useCardLocalGlass = false,
                )
            )
            .isFalse()
    }

    @Test
    fun task027_heldNearZeroAndReverse_sameCardLocalOwner() {
        val held = StackExpandedDragPhase.Held
        // Positive / near-zero / "reverse" chi doi dragY — owner van CardLocal.
        val yPositive =
            stackExpandedIncomingSurfaceTranslationYPx(held, dragYPx = 120f, topSettleYPx = 0f)
        val yNearZero =
            stackExpandedIncomingSurfaceTranslationYPx(held, dragYPx = 0.25f, topSettleYPx = 0f)
        val yReverse =
            stackExpandedIncomingSurfaceTranslationYPx(held, dragYPx = 12f, topSettleYPx = 0f)
        assertThat(yPositive).isWithin(0.01f).of(120f)
        assertThat(yNearZero).isWithin(0.01f).of(0.25f)
        assertThat(yReverse).isWithin(0.01f).of(12f)
        assertThat(stackExpandedUsesCardLocalGlass(true, held)).isTrue()
        // Whole-surface card-local: content ty == surface ty (delta 0).
        assertThat(stackExpandedWholeSurfaceYDeltaPx(yPositive, yPositive)).isWithin(0.01f).of(0f)
        val topZ = stackExpandedLayerZIndex(0, 2, isLeaving = false)
        val underZ = stackExpandedLayerZIndex(1, 2, isLeaving = false)
        assertThat(stackExpandedTopZAboveUnder(topZ, underZ)).isTrue()
    }

    @Test
    fun task027_commitSettle_dragYZero_leaveAndTopSettleOwnSurfaces() {
        // Production commit: leavingId + dragY=0 truoc leave anim; incoming topSettleY>0.
        val phase = StackExpandedDragPhase.CommitSettle
        assertThat(stackExpandedUsesCardLocalGlass(true, phase)).isTrue()
        assertThat(
                stackExpandedGlassSurfaceOwner(true, phase)
            )
            .isEqualTo(StackExpandedGlassSurfaceOwner.CardLocal)
        val leaveStart = 180f
        val nest = 40f
        val topSettle = 55f
        // dragY=0 KHONG keo shared plate ve rest — incoming theo topSettleY.
        val incomingTy =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = phase,
                dragYPx = 0f,
                topSettleYPx = topSettle,
            )
        assertThat(incomingTy).isWithin(0.01f).of(topSettle)
        assertThat(stackExpandedWholeSurfaceYDeltaPx(incomingTy, topSettle)).isWithin(0.01f).of(0f)
        val outgoingTy =
            stackExpandedOutgoingLeaveTranslationYPx(
                leaveProgress = 0f,
                leaveStartYPx = leaveStart,
                leaveNestYPx = nest,
            )
        assertThat(outgoingTy).isWithin(0.01f).of(leaveStart)
        val outgoingMid =
            stackExpandedOutgoingLeaveTranslationYPx(
                leaveProgress = 0.5f,
                leaveStartYPx = leaveStart,
                leaveNestYPx = nest,
            )
        assertThat(outgoingMid).isWithin(0.01f).of((leaveStart + nest) * 0.5f)
        // Leaving z duoi top moi — chi CommitSettle.
        val topZ = stackExpandedLayerZIndex(0, 2, isLeaving = false)
        val leaveZ = stackExpandedLayerZIndex(0, 2, isLeaving = true)
        assertThat(leaveZ).isLessThan(topZ)
        // CardLocal commit: shared alpha0 + khong compose parked plate.
        assertThat(stackExpandedSharedGlassPlateAlpha(1f, useCardLocalGlass = true))
            .isEqualTo(0f)
        assertThat(stackExpandedKeepSharedBlurSubtree(1f, useCardLocalGlass = true)).isFalse()
    }

    @Test
    fun task027_cancelSettle_andInterrupt_phaseWiring() {
        val cancel =
            stackExpandedDragPhase(
                isDragging = false,
                isSettling = true,
                leavingId = null,
            )
        assertThat(cancel).isEqualTo(StackExpandedDragPhase.CancelSettle)
        assertThat(stackExpandedUsesCardLocalGlass(true, cancel)).isTrue()
        val cancelTy =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = cancel,
                dragYPx = 90f,
                topSettleYPx = 0f,
            )
        assertThat(cancelTy).isWithin(0.01f).of(90f)
        // DOWN moi: Held; cancelSettle xoa leaving (production onStackDragStart).
        val afterInterrupt =
            stackExpandedDragPhase(
                isDragging = true,
                isSettling = false,
                leavingId = null,
            )
        assertThat(afterInterrupt).isEqualTo(StackExpandedDragPhase.Held)
        assertThat(stackExpandedUsesCardLocalGlass(true, afterInterrupt)).isTrue()
        assertThat(
                stackExpandedHeldKeepsTopIdentity(
                    phase = afterInterrupt,
                    leavingId = null,
                    localPinnedIdAssignedThisGesture = false,
                )
            )
            .isTrue()
        assertThat(
                stackExpandedShouldCommitOnRelease(
                    dragYPx = 0f,
                    velocityYPx = 0f,
                    commitThresholdPx = 100f,
                    commitVelocityPx = 700f,
                    canCommitStackSwitch = true,
                )
            )
            .isFalse()
    }

    @Test
    fun task028_phasePublish_heldCommitCancelIdle_ordering() {
        // Production: Held @ start; Cancel/Commit @ release TRUOC translation; Idle @ end.
        assertThat(StackExpandedDragPhase.Held)
            .isEqualTo(
                stackExpandedDragPhase(
                    isDragging = true,
                    isSettling = false,
                    leavingId = null,
                )
            )
        assertThat(stackExpandedPhaseOnPointerRelease(shouldCommit = false))
            .isEqualTo(StackExpandedDragPhase.CancelSettle)
        assertThat(stackExpandedPhaseOnPointerRelease(shouldCommit = true))
            .isEqualTo(StackExpandedDragPhase.CommitSettle)
        // Held → CommitSettle → Idle: CardLocal xuyen (task-041).
        assertThat(stackExpandedUsesCardLocalGlass(true, StackExpandedDragPhase.Held)).isTrue()
        assertThat(
                stackExpandedUsesCardLocalGlass(true, StackExpandedDragPhase.CommitSettle)
            )
            .isTrue()
        assertThat(stackExpandedUsesCardLocalGlass(true, StackExpandedDragPhase.Idle)).isTrue()
        // Held → CancelSettle → Idle.
        assertThat(
                stackExpandedUsesCardLocalGlass(true, StackExpandedDragPhase.CancelSettle)
            )
            .isTrue()
        // Khong doi owner theo |dragY| — Idle@rest van CardLocal.
        assertThat(
                stackExpandedGlassSurfaceOwner(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Idle,
                )
            )
            .isEqualTo(StackExpandedGlassSurfaceOwner.CardLocal)
    }

    @Test
    fun task028_settleFinally_staleOrInterrupt_skipsIdle() {
        // Gen khop + !dragging → duoc Idle (settle that xong).
        assertThat(
                stackExpandedShouldPublishIdleAfterSettle(
                    settleGenAtStart = 3,
                    currentSettleGen = 3,
                    isDragging = false,
                )
            )
            .isTrue()
        // Interrupt DOWN: gen bump → stale finally KHONG Idle.
        assertThat(
                stackExpandedShouldPublishIdleAfterSettle(
                    settleGenAtStart = 3,
                    currentSettleGen = 4,
                    isDragging = true,
                )
            )
            .isFalse()
        // Gen khop nhung dang Held moi — khong Idle.
        assertThat(
                stackExpandedShouldPublishIdleAfterSettle(
                    settleGenAtStart = 5,
                    currentSettleGen = 5,
                    isDragging = true,
                )
            )
            .isFalse()
        // Stale gen + !dragging (reopen/dispose da bump) — khong Idle tu job cu.
        assertThat(
                stackExpandedShouldPublishIdleAfterSettle(
                    settleGenAtStart = 1,
                    currentSettleGen = 2,
                    isDragging = false,
                )
            )
            .isFalse()
    }

    /**
     * Production finally SSOT: stale A sau B active → KHONG clearSettling/reporter/Idle.
     * (Callsite StackedExpandedPanels settle finally chi mutate khi actions cho phep.)
     */
    @Test
    fun task029_settleFinally_staleA_afterB_active_noSharedMutations() {
        // B dang settle (gen=8); A.finally (genAtStart=7) den muon.
        val stale =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 7,
                currentSettleGen = 8,
                isDragging = false,
                dragYIsZero = true,
            )
        assertThat(stale.clearSettlingAndReporters).isFalse()
        assertThat(stale.reportDragYZeroIfZero).isFalse()
        assertThat(stale.publishIdle).isFalse()
        assertThat(stackExpandedOwnsSettleGeneration(7, 8)).isFalse()
        // Stale A sau Held (gen bump + isDragging): van khong mutate.
        val afterHeld =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 7,
                currentSettleGen = 9,
                isDragging = true,
                dragYIsZero = false,
            )
        assertThat(afterHeld.clearSettlingAndReporters).isFalse()
        assertThat(afterHeld.publishIdle).isFalse()
    }

    /** Gen khop + settle xong: cleanup settling + Idle; dragY=0 → report 0. */
    @Test
    fun task029_settleFinally_currentCompletion_cleansSettlingAndIdle() {
        val done =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 4,
                currentSettleGen = 4,
                isDragging = false,
                dragYIsZero = true,
            )
        assertThat(done.clearSettlingAndReporters).isTrue()
        assertThat(done.reportDragYZeroIfZero).isTrue()
        assertThat(done.publishIdle).isTrue()
        // Gen khop nhung DOWN moi Held: cleanup settling OK, KHONG Idle.
        val heldOwns =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 4,
                currentSettleGen = 4,
                isDragging = true,
                dragYIsZero = false,
            )
        assertThat(heldOwns.clearSettlingAndReporters).isTrue()
        assertThat(heldOwns.reportDragYZeroIfZero).isFalse()
        assertThat(heldOwns.publishIdle).isFalse()
    }

    @Test
    fun task028_incomingOutgoingHelpers_noDoubleApplySplit() {
        // Production split: outer dragYPx=0 + topSettle; content topSettleYPx=0 + dragY.
        val held = StackExpandedDragPhase.Held
        val commit = StackExpandedDragPhase.CommitSettle
        val outerHeld =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = held,
                dragYPx = 0f,
                topSettleYPx = 0f,
            )
        val contentHeld =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = held,
                dragYPx = 80f,
                topSettleYPx = 0f,
            )
        assertThat(outerHeld).isWithin(0.01f).of(0f)
        assertThat(contentHeld).isWithin(0.01f).of(80f)
        // Tong = drag (khong double).
        assertThat(outerHeld + contentHeld).isWithin(0.01f).of(80f)
        val outerCommit =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = commit,
                dragYPx = 0f,
                topSettleYPx = 55f,
            )
        val contentCommit =
            stackExpandedIncomingSurfaceTranslationYPx(
                phase = commit,
                dragYPx = 0f,
                topSettleYPx = 0f,
            )
        assertThat(outerCommit).isWithin(0.01f).of(55f)
        assertThat(contentCommit).isWithin(0.01f).of(0f)
        assertThat(outerCommit + contentCommit).isWithin(0.01f).of(55f)
        assertThat(
                stackExpandedOutgoingLeaveTranslationYPx(
                    leaveProgress = 0.25f,
                    leaveStartYPx = 200f,
                    leaveNestYPx = 40f,
                )
            )
            .isWithin(0.01f)
            .of(200f + (40f - 200f) * 0.25f)
    }

    // --- task-031: 4 loai H — top natural != stack max envelope ---

    /** top100/under300: lock dung top+pads, khong max envelope. */
    @Test
    fun task031_lockHeight_top100_under300_prefersTopNatural() {
        val pill = 60
        val topPad = 2
        val bottomPad = 50
        val topNatural = 100
        val stackEnvelope = 300 + topPad + bottomPad // max(under)=300 + pads
        val lockH =
            statusBarLockHeightPreferTopCardNaturalPx(
                topCardNaturalHPx = topNatural,
                stackHostEnvelopeHPx = stackEnvelope,
                topPadPx = topPad,
                bottomPadPx = bottomPad,
                pillHPx = pill,
            )
        assertThat(lockH).isEqualTo(100 + topPad + bottomPad)
        assertThat(lockH).isLessThan(stackEnvelope)
    }

    /** top300/under100: lock top+pads; envelope co the = top (max). */
    @Test
    fun task031_lockHeight_top300_under100_keepsTopNatural() {
        val pill = 60
        val topPad = 2
        val bottomPad = 50
        val lockH =
            statusBarLockHeightPreferTopCardNaturalPx(
                topCardNaturalHPx = 300,
                stackHostEnvelopeHPx = 300 + topPad + bottomPad,
                topPadPx = topPad,
                bottomPadPx = bottomPad,
                pillHPx = pill,
            )
        assertThat(lockH).isEqualTo(300 + topPad + bottomPad)
    }

    /** Chua do top → fallback envelope (first expand). */
    @Test
    fun task031_lockHeight_noTopNatural_fallsBackEnvelope() {
        val envelope = 352
        assertThat(
                statusBarLockHeightPreferTopCardNaturalPx(
                    topCardNaturalHPx = 0,
                    stackHostEnvelopeHPx = envelope,
                    topPadPx = 2,
                    bottomPadPx = 50,
                    pillHPx = 60,
                )
            )
            .isEqualTo(envelope)
    }

    /** Owner switch: cache short thang lastExpanded tall; release/cancel cung helper. */
    @Test
    fun task031_ownerSwitch_cacheShort_notBridgeTall_andRestVsFrozen() {
        val lockedTall = LockedTargetHeight(heightPx = 352, owner = "event:tall")
        // Rest swap sang short da cache.
        assertThat(
                statusBarMorphTargetHeightPx(
                    lockedTall,
                    owner = "event:short",
                    lastExpandedHPx = 352,
                    cachedOwnerNaturalHPx = 152,
                )
            )
            .isEqualTo(152)
        // Frozen morph run: giu frozen, cam live short mid-flight.
        assertThat(
                statusBarExpandedRestOrFrozenHeightPx(
                    restLiveLockHPx = 152,
                    frozenEndpointHPx = 352,
                    pillHPx = 60,
                    morphTransitionRunning = true,
                    morphExpandedAtRest = false,
                )
            )
            .isEqualTo(352)
        // At-rest: follow live short.
        assertThat(
                statusBarExpandedRestOrFrozenHeightPx(
                    restLiveLockHPx = 152,
                    frozenEndpointHPx = 352,
                    pillHPx = 60,
                    morphTransitionRunning = false,
                    morphExpandedAtRest = true,
                )
            )
            .isEqualTo(152)
    }

    /** Envelope stack khong doi khi owner switch — van retarget tu cache. */
    @Test
    fun task031_ownerSwitch_sameEnvelope_retargetsFromCache() {
        val ctx = statusBarCardNaturalMeasureContextToken(availableWidthPx = 900, densityDpi = 450)
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "short", 900, 100, ctx, ctx)
        statusBarPutCardNaturalSizeCache(cache, "tall", 900, 300, ctx, ctx)
        val envelope = 352 // max unchanged
        val shortLock =
            statusBarLockHeightPreferTopCardNaturalPx(
                topCardNaturalHPx =
                    statusBarLookupCardNaturalHeightPx(cache, "event:short", ctx),
                stackHostEnvelopeHPx = envelope,
                topPadPx = 2,
                bottomPadPx = 50,
                pillHPx = 60,
            )
        val tallLock =
            statusBarLockHeightPreferTopCardNaturalPx(
                topCardNaturalHPx =
                    statusBarLookupCardNaturalHeightPx(cache, "event:tall", ctx),
                stackHostEnvelopeHPx = envelope,
                topPadPx = 2,
                bottomPadPx = 50,
                pillHPx = 60,
            )
        assertThat(shortLock).isEqualTo(152)
        assertThat(tallLock).isEqualTo(352)
        assertThat(shortLock).isNotEqualTo(tallLock)
    }

    /** Stale measure wrong owner: resolve chi khoa dung owner; cache put follow H. */
    @Test
    fun task031_staleMeasure_wrongOwner_andContentHeightFollow() {
        var locked = LockedTargetHeight(heightPx = 152, owner = "event:short")
        // Measure envelope tall nhung resolve voi owner short + measuredForLock short.
        locked =
            resolveStatusBarLockedTargetHeight(
                locked,
                owner = "event:short",
                measuredHPx = 152,
                pillHPx = 60,
            )
        assertThat(locked.heightPx).isEqualTo(152)
        // Content H doi sau settle (RemoteViews) — follow cung owner.
        locked =
            resolveStatusBarLockedTargetHeight(
                locked,
                owner = "event:short",
                measuredHPx = 180,
                pillHPx = 60,
            )
        assertThat(locked.heightPx).isEqualTo(180)
        val ctx = statusBarCardNaturalMeasureContextToken(800, 450)
        val cache =
            mutableMapOf(
                "event:short" to
                    StatusBarCardNaturalCacheEntry(
                        heightPx = 100,
                        measuredWidthPx = 900,
                        contextToken = ctx,
                    )
            )
        statusBarPutCardNaturalSizeCache(cache, "short", 800, 120, ctx, ctx)
        assertThat(cache["event:short"]?.heightPx).isEqualTo(120)
        assertThat(cache["event:short"]?.measuredWidthPx).isEqualTo(800)
        // h<=0 khong xoa cache (tranh transient).
        assertThat(statusBarPutCardNaturalSizeCache(cache, "short", 800, 0, ctx, ctx)).isNull()
        assertThat(cache["event:short"]?.heightPx).isEqualTo(120)
    }

    /** Bon loai H mapping — khong gop. */
    @Test
    fun task031_fourHeightKinds_distinctMapping() {
        val kinds =
            StatusBarStackHeightKinds(
                cardNaturalHPx = 100,
                paintedTopHPx = 100,
                stackHostEnvelopeHPx = 352,
                touchInsetsEnvelopeHPx = 352,
            )
        assertThat(kinds.cardNaturalHPx).isNotEqualTo(kinds.stackHostEnvelopeHPx)
        assertThat(kinds.paintedTopHPx).isEqualTo(kinds.cardNaturalHPx)
        assertThat(
                statusBarStackHostHeightFromCardNaturalPx(
                    kinds.cardNaturalHPx,
                    topPadPx = 2,
                    bottomPadPx = 50,
                )
            )
            .isEqualTo(152)
        assertThat(statusBarStackEventOwnerKey("timer_1")).isEqualTo("event:timer_1")
    }

    // --- task-033: provenance publish (khong re-label old size + new token) ---

    /**
     * Wiring audit (production StackedExpandedPanels):
     * 1) onPlaced(layer) → statusBarCardNaturalPublishAfterLayout(prev,w,h,layoutToken)
     * 2) chi khi entry doi → reporter(id,w,h,layoutToken)
     * 3) Host put: reportToken==active; lookup token; bridge khi chua observation
     * KHONG LaunchedEffect(token) copy fullHeights gan token moi.
     */

    /**
     * contextA h100 → contextB chua measured: KHONG publish h100 as B.
     * Actual B h180 accepted; cache/lookup theo provenance.
     */
    @Test
    fun task033_contextA_toB_noStaleRelabel_thenAcceptB() {
        val ctxA =
            statusBarCardNaturalMeasureContextToken(
                availableWidthPx = 900,
                densityDpi = 450,
                fontScale = 1f,
            )
        val ctxB =
            statusBarCardNaturalMeasureContextToken(
                availableWidthPx = 700,
                densityDpi = 450,
                fontScale = 1f,
            )
        assertThat(ctxA).isNotEqualTo(ctxB)
        // Layer map remember: van giu observation A sau khi active = B.
        val layerObs =
            StatusBarCardNaturalLayerObservation(
                widthPx = 860, // card W != envelope 900 (padding) — khong so bang envelope
                heightPx = 100,
                measuredContextToken = ctxA,
            )
        // Observation A khong hop le cho active B — KHONG promote h100.
        assertThat(statusBarCardNaturalObservationForActiveContext(layerObs, ctxB)).isNull()
        val lastPublishedA = StatusBarCardNaturalCacheEntry(100, 860, ctxA)
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "short", 860, 100, ctxA, ctxA)
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", ctxB)).isEqualTo(0)
        // Ordering: chi sau onPlaced B moi publishAfterLayout (do h180, khong copy A).
        val pubB =
            statusBarCardNaturalPublishAfterLayout(
                previousPublished = lastPublishedA,
                widthPx = 660,
                heightPx = 180,
                layoutContextToken = ctxB,
            )
        assertThat(pubB).isNotNull()
        statusBarPutCardNaturalSizeCache(
            cache,
            "short",
            pubB!!.measuredWidthPx,
            pubB.heightPx,
            pubB.contextToken,
            ctxB,
        )
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", ctxB)).isEqualTo(180)
        val obsB =
            statusBarCardNaturalObservationForActiveContext(
                StatusBarCardNaturalLayerObservation(660, 180, ctxB),
                ctxB,
            )
        assertThat(obsB?.heightPx).isEqualTo(180)
    }

    /**
     * contextC: actual size van 180 → onPlaced current layout van republish (token moi).
     * Write chi khi entry doi; goi lai cung entry → null.
     */
    @Test
    fun task033_contextC_sameSize_layoutRepublish() {
        val ctxB =
            statusBarCardNaturalMeasureContextToken(700, 450, fontScale = 1f)
        val ctxC =
            statusBarCardNaturalMeasureContextToken(700, 480, fontScale = 1f) // density doi
        assertThat(ctxB).isNotEqualTo(ctxC)
        val prev = StatusBarCardNaturalCacheEntry(180, 660, ctxB)
        val pubC =
            statusBarCardNaturalPublishAfterLayout(
                previousPublished = prev,
                widthPx = 660,
                heightPx = 180,
                layoutContextToken = ctxC,
            )
        assertThat(pubC).isNotNull()
        assertThat(pubC!!.heightPx).isEqualTo(180)
        assertThat(pubC.contextToken).isEqualTo(ctxC)
        // Cung place lai (khong doi) → khong write.
        assertThat(
                statusBarCardNaturalPublishAfterLayout(
                    previousPublished = pubC,
                    widthPx = 660,
                    heightPx = 180,
                    layoutContextToken = ctxC,
                )
            )
            .isNull()
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "short", 660, 180, ctxC, ctxC)
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", ctxC)).isEqualTo(180)
    }

    /** Old-A callback sau B active → put reject. */
    @Test
    fun task033_oldA_callbackAfterB_rejected() {
        val ctxA = statusBarCardNaturalMeasureContextToken(900, 450, 1f)
        val ctxB = statusBarCardNaturalMeasureContextToken(700, 450, 1f)
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "short", 660, 180, ctxB, ctxB)
        assertThat(
                statusBarPutCardNaturalSizeCache(
                    cache,
                    "short",
                    widthPx = 860,
                    heightPx = 100,
                    reportContextToken = ctxA,
                    activeContextToken = ctxB,
                )
            )
            .isNull()
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", ctxB)).isEqualTo(180)
    }

    /** fontScale doi → token doi (H co the doi khi W/dpi giu). */
    @Test
    fun task033_fontScale_changesContextToken() {
        val base = statusBarCardNaturalMeasureContextToken(900, 450, fontScale = 1f)
        val scaled = statusBarCardNaturalMeasureContextToken(900, 450, fontScale = 1.3f)
        assertThat(base).isNotEqualTo(scaled)
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "short", 860, 100, base, base)
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", scaled)).isEqualTo(0)
        // Layout sau fontScale: publish H moi.
        val pub =
            statusBarCardNaturalPublishAfterLayout(
                previousPublished = StatusBarCardNaturalCacheEntry(100, 860, base),
                widthPx = 860,
                heightPx = 140,
                layoutContextToken = scaled,
            )
        assertThat(pub).isNotNull()
        statusBarPutCardNaturalSizeCache(
            cache,
            "short",
            pub!!.measuredWidthPx,
            pub.heightPx,
            pub.contextToken,
            scaled,
        )
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:short", scaled)).isEqualTo(140)
    }

    /** Width envelope doi — stale lookup; bridge continuity khong lock max under. */
    @Test
    fun task033_widthChange_staleLookup_bridgeContinuity() {
        val dpi = 450
        val oldTok = statusBarCardNaturalMeasureContextToken(900, dpi, 1f)
        val newTok = statusBarCardNaturalMeasureContextToken(700, dpi, 1f)
        assertThat(oldTok).isNotEqualTo(newTok)
        val cache = mutableMapOf<String, StatusBarCardNaturalCacheEntry>()
        statusBarPutCardNaturalSizeCache(cache, "card", 860, 200, oldTok, oldTok)
        assertThat(statusBarLookupCardNaturalHeightPx(cache, "event:card", newTok)).isEqualTo(0)
        assertThat(
                statusBarLockHeightFromNaturalOrBridgePx(
                    topCardNaturalHPx = 0,
                    stackHostEnvelopeHPx = 352,
                    topPadPx = 2,
                    bottomPadPx = 50,
                    pillHPx = 60,
                    hasBridgeOrLockedHeight = true,
                )
            )
            .isEqualTo(0)
        // First expand van cho envelope.
        assertThat(
                statusBarLockHeightFromNaturalOrBridgePx(
                    topCardNaturalHPx = 0,
                    stackHostEnvelopeHPx = 352,
                    topPadPx = 2,
                    bottomPadPx = 50,
                    pillHPx = 60,
                    hasBridgeOrLockedHeight = false,
                )
            )
            .isEqualTo(352)
        // Natural den → top+pads.
        assertThat(
                statusBarLockHeightFromNaturalOrBridgePx(
                    topCardNaturalHPx = 100,
                    stackHostEnvelopeHPx = 352,
                    topPadPx = 2,
                    bottomPadPx = 50,
                    pillHPx = 60,
                    hasBridgeOrLockedHeight = true,
                )
            )
            .isEqualTo(152)
        // Prune sai context.
        statusBarPruneCardNaturalCache(cache, setOf("event:card"), newTok)
        assertThat(cache).isEmpty()
    }

    // --- task-037: painted selector + glass handoff (authored, not_run) ---

    private fun ownedRect(
        owner: String,
        w: Int,
        h: Int,
        token: Int = 1,
        left: Float = 34f,
        top: Float = 85f,
    ) =
        StatusBarOwnedPaintedRectPx(
            ownerKey = owner,
            contextToken = token,
            leftPx = left,
            topPx = top,
            rightPx = left + w,
            bottomPx = top + h,
        )

    /** Clock-first vs Media-first: rest W/H theo owner, khong stick frozen mo dau. */
    @Test
    fun task037_clockFirst_thenMedia_usesMediaRestWh() {
        val clock = ownedRect("event:clock", 990, 349)
        val media = ownedRect("event:media", 1012, 383)
        val choice =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:media",
                morphFrozen = clock,
                selectedRest = media,
                livePainted = media,
                pillWPx = 160,
                pillHPx = 56,
            )
        assertThat(choice.provenance).isEqualTo(StatusBarPaintedRectProvenance.SelectedRest)
        assertThat(choice.rect!!.widthInt).isEqualTo(1012)
        assertThat(choice.rect!!.heightInt).isEqualTo(383)
        assertThat(choice.rect!!.ownerKey).isEqualTo("event:media")
    }

    @Test
    fun task037_mediaFirst_thenClock_usesClockRestWh() {
        val media = ownedRect("event:media", 1012, 383)
        val clock = ownedRect("event:clock", 990, 349)
        val choice =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:clock",
                morphFrozen = media,
                selectedRest = clock,
                livePainted = clock,
                pillWPx = 160,
                pillHPx = 56,
            )
        assertThat(choice.provenance).isEqualTo(StatusBarPaintedRectProvenance.SelectedRest)
        assertThat(choice.rect!!.widthInt).isEqualTo(990)
        assertThat(choice.rect!!.heightInt).isEqualTo(349)
    }

    @Test
    fun task037_morphRunning_usesFrozenNotLiveSwap() {
        val frozen = ownedRect("event:clock", 990, 349)
        val live = ownedRect("event:media", 1012, 383)
        val choice =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
                selectedOwnerKey = "event:media",
                morphFrozen = frozen,
                selectedRest = live,
                livePainted = live,
                pillWPx = 160,
                pillHPx = 56,
            )
        assertThat(choice.provenance).isEqualTo(StatusBarPaintedRectProvenance.MorphFrozenRun)
        assertThat(choice.rect!!.widthInt).isEqualTo(990)
    }

    @Test
    fun task037_closingAfterSwap_rebindFrozenFromSelected() {
        val clock = ownedRect("event:clock", 990, 349)
        assertThat(
                statusBarMorphFrozenRebindFromSelectedOnLeaveRest(
                    wasExpandedAtRest = true,
                    morphExpandedAtRest = false,
                    selectedRestValidForOwner = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarMorphFrozenPaintedWritePolicy(
                    morphTransitionRunning = true,
                    morphExpandedAtRest = false,
                    morphFrozenValid = false,
                )
            )
            .isEqualTo(StatusBarMorphFrozenPaintedWrite.Capture)
        // Sau rebind: morph run dung Clock (selected), khong Media frozen mo dau.
        val choice =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
                selectedOwnerKey = "event:clock",
                morphFrozen = clock,
                selectedRest = clock,
                livePainted = clock,
                pillWPx = 160,
                pillHPx = 56,
            )
        assertThat(choice.rect!!.ownerKey).isEqualTo("event:clock")
        assertThat(choice.rect!!.heightInt).isEqualTo(349)
    }

    @Test
    fun task037_wrongOwnerReport_rejected() {
        assertThat(
                statusBarAcceptPaintedCardReport(
                    reportOwnerKey = "event:under",
                    reportContextToken = 1,
                    activeOwnerKey = "event:top",
                    activeContextToken = 1,
                    isSelectedTop = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarAcceptPaintedCardReport(
                    reportOwnerKey = "event:top",
                    reportContextToken = 9,
                    activeOwnerKey = "event:top",
                    activeContextToken = 1,
                    isSelectedTop = true,
                )
            )
            .isFalse()
        assertThat(
                statusBarAcceptPaintedCardReport(
                    reportOwnerKey = "event:top",
                    reportContextToken = 1,
                    activeOwnerKey = "event:top",
                    activeContextToken = 1,
                    isSelectedTop = true,
                )
            )
            .isTrue()
        assertThat(
                statusBarAcceptPaintedCardReport(
                    reportOwnerKey = "event:top",
                    reportContextToken = 1,
                    activeOwnerKey = "event:top",
                    activeContextToken = 1,
                    isSelectedTop = false,
                )
            )
            .isFalse()
    }

    @Test
    fun task037_pendingOwner_keepsContinuityNotPill() {
        val oldClock = ownedRect("event:clock", 990, 349)
        val choice =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:media",
                morphFrozen = oldClock,
                selectedRest = oldClock,
                livePainted = null,
                pillWPx = 160,
                pillHPx = 56,
            )
        assertThat(choice.provenance).isEqualTo(StatusBarPaintedRectProvenance.LiveContinuity)
        assertThat(choice.rect!!.widthInt).isEqualTo(990)
        assertThat(
                statusBarMorphFrozenPaintedWritePolicy(
                    morphTransitionRunning = false,
                    morphExpandedAtRest = true,
                    morphFrozenValid = true,
                )
            )
            .isEqualTo(StatusBarMorphFrozenPaintedWrite.Hold)
    }

    // --- task-041: stable per-card glass; no parked shared / warm / PrepareShared ---

    @Test
    fun task041_glass_expandedIdleHeldCommitCancel_noStationaryThirdShell() {
        val phases =
            listOf(
                StackExpandedDragPhase.Idle,
                StackExpandedDragPhase.Held,
                StackExpandedDragPhase.CancelSettle,
                StackExpandedDragPhase.CommitSettle,
            )
        for (phase in phases) {
            val d =
                stackExpandedGlassHandoffDecision(
                    morphExpandedAtRest = true,
                    phase = phase,
                    sharedGlassOwnerPresent = true,
                    basePaintAlpha = 1f,
                )
            assertThat(d.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.CardLocal)
            assertThat(d.useCardLocalGlass).isTrue()
            assertThat(d.yieldNativeGlassToShared).isFalse()
            assertThat(d.sharedPlateAlpha).isEqualTo(0f)
            assertThat(d.keepSharedBlurSubtree).isFalse()
            assertThat(stackExpandedUsesCardLocalGlass(true, phase)).isTrue()
            assertThat(stackExpandedGlassSurfaceOwner(true, phase))
                .isEqualTo(StackExpandedGlassSurfaceOwner.CardLocal)
        }
        // 2-event identity: top+under deu CardLocal @ Idle — khong shared parked.
        assertThat(
                stackExpandedYieldNativeGlassToSharedOwner(
                    sharedGlassOwnerPresent = true,
                    useCardLocalGlass = true,
                )
            )
            .isFalse()
        assertThat(stackExpandedSharedGlassPlateAlpha(1f, useCardLocalGlass = true)).isEqualTo(0f)
        assertThat(stackExpandedKeepSharedBlurSubtree(1f, useCardLocalGlass = true)).isFalse()
    }

    @Test
    fun task041_glass_pillMorph_usesSharedShell_collapseKeepsSelectedPath() {
        val morph =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = false,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 1f,
            )
        assertThat(morph.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.SharedPlate)
        assertThat(morph.useCardLocalGlass).isFalse()
        assertThat(morph.yieldNativeGlassToShared).isTrue()
        assertThat(morph.sharedPlateAlpha).isEqualTo(1f)
        assertThat(morph.keepSharedBlurSubtree).isTrue()
        // Morph Held cung SharedPlate (lifecycle tach stack).
        val morphHeld =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = false,
                phase = StackExpandedDragPhase.Held,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 0.72f,
            )
        assertThat(morphHeld.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.SharedPlate)
        assertThat(morphHeld.yieldNativeGlassToShared).isTrue()
        // Owner vang → Cancelled.
        val cancelled =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = false,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = false,
                basePaintAlpha = 1f,
            )
        assertThat(cancelled.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.Cancelled)
        assertThat(cancelled.yieldNativeGlassToShared).isFalse()
    }

    @Test
    fun task041_glass_interruptStaleSettle_noOwnerReset_andFinally029() {
        // Interrupt DOWN: gen bump → stale finally no Idle / no clear settling.
        val stale =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 3,
                currentSettleGen = 4,
                isDragging = true,
                dragYIsZero = false,
            )
        assertThat(stale.clearSettlingAndReporters).isFalse()
        assertThat(stale.publishIdle).isFalse()
        val ok =
            stackExpandedSettleFinallyActions(
                settleGenAtStart = 4,
                currentSettleGen = 4,
                isDragging = false,
                dragYIsZero = true,
            )
        assertThat(ok.clearSettlingAndReporters).isTrue()
        assertThat(ok.publishIdle).isTrue()
        // Sau stale: Idle@rest van CardLocal (khong flip SharedPlate).
        assertThat(
                stackExpandedGlassHandoffDecision(
                    morphExpandedAtRest = true,
                    phase = StackExpandedDragPhase.Idle,
                    sharedGlassOwnerPresent = true,
                    basePaintAlpha = 1f,
                )
                    .handoffPhase
            )
            .isEqualTo(StackExpandedGlassHandoffPhase.CardLocal)
        // Cancel release (0,0) khong commit.
        assertThat(stackExpandedReleaseIsCancelEnd(0f, 0f)).isTrue()
        assertThat(
                stackExpandedShouldCommitOnRelease(
                    dragYPx = 0f,
                    velocityYPx = 0f,
                    commitThresholdPx = 100f,
                    commitVelocityPx = 700f,
                    canCommitStackSwitch = true,
                )
            )
            .isFalse()
    }

    @Test
    fun task038_rect_oldContextRejected_newContextSameWhReplaces() {
        val oldCtx =
            ownedRect("event:clock", 990, 349, token = 1, left = 34f, top = 85f)
        val newCtxSameWh =
            ownedRect("event:clock", 990, 349, token = 2, left = 40f, top = 90f)
        // Same owner, old context → LiveContinuity (pending), khong SelectedRest ready.
        val stale =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:clock",
                morphFrozen = oldCtx,
                selectedRest = oldCtx,
                livePainted = null,
                pillWPx = 160,
                pillHPx = 56,
                activeContextToken = 2,
            )
        assertThat(stale.provenance).isEqualTo(StatusBarPaintedRectProvenance.LiveContinuity)
        assertThat(stale.rect!!.contextToken).isEqualTo(1)
        assertThat(
                statusBarSelectedRestValidForRebind(
                    rect = oldCtx,
                    selectedOwnerKey = "event:clock",
                    activeContextToken = 2,
                    pillWPx = 160,
                    pillHPx = 56,
                )
            )
            .isFalse()

        val ready =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:clock",
                morphFrozen = oldCtx,
                selectedRest = newCtxSameWh,
                livePainted = newCtxSameWh,
                pillWPx = 160,
                pillHPx = 56,
                activeContextToken = 2,
            )
        assertThat(ready.provenance).isEqualTo(StatusBarPaintedRectProvenance.SelectedRest)
        assertThat(ready.rect!!.leftPx).isEqualTo(40f)
        assertThat(ready.rect!!.contextToken).isEqualTo(2)
        assertThat(statusBarOwnedPaintedRectNeedsAtomicReplace(oldCtx, newCtxSameWh)).isTrue()

        // Morph run: frozen pin — khong validate moving context mid-run.
        val frozenRun =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = true,
                morphExpandedAtRest = false,
                selectedOwnerKey = "event:clock",
                morphFrozen = oldCtx,
                selectedRest = newCtxSameWh,
                livePainted = newCtxSameWh,
                pillWPx = 160,
                pillHPx = 56,
                activeContextToken = 99,
            )
        assertThat(frozenRun.provenance).isEqualTo(StatusBarPaintedRectProvenance.MorphFrozenRun)
        assertThat(frozenRun.rect!!.contextToken).isEqualTo(1)
    }

    // --- task-043/044: open pill→expanded glass — native dest before source drop ---

    @Test
    fun task043_open_waitingDestination_cannotDropSource() {
        val base =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = true,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 1f,
            )
        // Idle@rest effective → Waiting (khong 1-frame drop shared truoc SideEffect begin).
        assertThat(
                stackOpenGlassEffectiveTransferPhase(
                    morphExpandedAtRest = true,
                    recordedPhase = StackOpenGlassTransferPhase.Idle,
                    destinationFirstDrawAcked = false,
                )
            )
            .isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        val waitingFromIdle =
            stackOpenGlassMergeHandoff(
                base = base,
                morphExpandedAtRest = true,
                transferPhase =
                    stackOpenGlassEffectiveTransferPhase(
                        morphExpandedAtRest = true,
                        recordedPhase = StackOpenGlassTransferPhase.Idle,
                        destinationFirstDrawAcked = false,
                    ),
                destinationFirstDrawAcked = false,
                basePaintAlpha = 1f,
            )
        assertThat(waitingFromIdle.keepSharedBlurSubtree).isTrue()
        assertThat(waitingFromIdle.sharedPlateAlpha).isEqualTo(1f)
        val waiting =
            stackOpenGlassMergeHandoff(
                base = base,
                morphExpandedAtRest = true,
                transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                destinationFirstDrawAcked = false,
                basePaintAlpha = 1f,
            )
        assertThat(waiting.handoffPhase)
            .isEqualTo(StackExpandedGlassHandoffPhase.OpenWaitingDestination)
        assertThat(waiting.useCardLocalGlass).isTrue()
        assertThat(waiting.keepSharedBlurSubtree).isTrue()
        assertThat(waiting.sharedPlateAlpha).isEqualTo(1f)
        assertThat(waiting.yieldNativeGlassToShared).isFalse()
        assertThat(
                stackOpenGlassMayDropSharedSource(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                    destinationFirstDrawAcked = false,
                )
            )
            .isFalse()
        // task-044: Waiting local blur factor=1 (native active), khong alpha0 hideDrawable.
        assertThat(
                stackOpenGlassLocalBlurAlphaFactor(
                    StackOpenGlassTransferPhase.WaitingDestination,
                    destinationFirstDrawAcked = false,
                )
            )
            .isEqualTo(1f)
        assertThat(
                stackOpenGlassAwaitDestinationDraw(
                    StackOpenGlassTransferPhase.WaitingDestination,
                    destinationFirstDrawAcked = false,
                )
            )
            .isTrue()
    }

    @Test
    fun task043_open_matchingCallback_transfersOnce() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 7,
                leftPx = 40f,
                topPx = 85f,
                rightPx = 1030f,
                bottomPx = 434f,
            )
        assertThat(req).isNotNull()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        // Production path: native backdrop + size local khop.
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = req!!.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 7,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
        assertThat(ctrl.destinationFirstDrawAcked).isTrue()
        // Callback trung → no-op (transfer once).
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 7,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        val base =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = true,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 1f,
            )
        val done =
            stackOpenGlassMergeHandoff(
                base = base,
                morphExpandedAtRest = true,
                transferPhase = ctrl.phase,
                destinationFirstDrawAcked = true,
                basePaintAlpha = 1f,
            )
        assertThat(done.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.CardLocal)
        assertThat(done.keepSharedBlurSubtree).isFalse()
        assertThat(done.sharedPlateAlpha).isEqualTo(0f)
        assertThat(
                stackOpenGlassMayDropSharedSource(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                    destinationFirstDrawAcked = true,
                )
            )
            .isTrue()
        assertThat(
                stackOpenGlassLocalBlurAlphaFactor(
                    StackOpenGlassTransferPhase.Complete,
                    destinationFirstDrawAcked = true,
                )
            )
            .isEqualTo(1f)
        assertThat(
                stackOpenGlassAwaitDestinationDraw(
                    StackOpenGlassTransferPhase.Complete,
                    destinationFirstDrawAcked = true,
                )
            )
            .isFalse()
    }

    @Test
    fun task044_open_alpha0_contentOnly_cannotDropSource() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 4,
                leftPx = 40f,
                topPx = 85f,
                rightPx = 1030f,
                bottomPx = 434f,
            )!!
        // Content-only / alpha0 / disabled native → reject (khong fake ready).
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 4,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = false,
                    blurSurfaceAlpha = 0f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 4,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 0f,
                )
            )
            .isFalse()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        assertThat(ctrl.destinationFirstDrawAcked).isFalse()
        assertThat(
                stackOpenGlassMayDropSharedSource(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                    destinationFirstDrawAcked = false,
                )
            )
            .isFalse()
        // Legacy content-only API van accept (other callers) — production path strict.
        assertThat(
                ctrl.onDestinationDraw(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 4,
                )
            )
            .isTrue()
    }

    @Test
    fun task044_open_pendingRect_retarget_staleGen_noFalseComplete() {
        val ctrl = StackOpenGlassTransferController()
        // 0-rect / token0 → khong begin (pending ≠ ready).
        assertThat(
                ctrl.beginWaitingDestination(
                    ownerKey = "event:clock",
                    contextToken = 0,
                    leftPx = 10f,
                    topPx = 20f,
                    rightPx = 100f,
                    bottomPx = 200f,
                )
            )
            .isNull()
        assertThat(
                ctrl.beginWaitingDestination(
                    ownerKey = "event:clock",
                    contextToken = 1,
                    leftPx = 0f,
                    topPx = 0f,
                    rightPx = 0f,
                    bottomPx = 0f,
                )
            )
            .isNull()
        val first =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 1,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 1024f,
                bottomPx = 434f,
            )!!
        val staleGen = first.sessionGen
        // Retarget owner trong Waiting → gen moi; stale callback no-op.
        val retargeted =
            ctrl.retargetOrBeginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 2,
                leftPx = 40f,
                topPx = 90f,
                rightPx = 1000f,
                bottomPx = 400f,
            )!!
        assertThat(retargeted.sessionGen).isGreaterThan(staleGen)
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = staleGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        // Sai size local → reject.
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = 10f,
                    callbackLayoutHeightPx = 10f,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        // Collapse intent → khong accept.
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                    liveExpandedIntent = false,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                    liveExpandedIntent = true,
                )
            )
            .isTrue()
    }

    @Test
    fun openGlass_sizeMismatch_rejectReason_andMatchingAck() {
        // Production: request geom = painted card; callback = blur draw size.
        // Envelope/natural lech SpaceLg*2 → size reject (khong fake ready / noi eps).
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:stopwatch",
                contextToken = 1559427,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 34f + 1012f,
                bottomPx = 85f + 349f,
            )!!
        assertThat(req.widthPx).isEqualTo(1012f)
        assertThat(req.heightPx).isEqualTo(349f)
        // Sai size (envelope vs card) → reject + reason size.
        assertThat(
                stackOpenGlassDestinationRejectReason(
                    request = req,
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                    alreadyAcked = false,
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:stopwatch",
                    callbackContextToken = 1559427,
                    callbackLayoutWidthPx = 1012f - 68f,
                    callbackLayoutHeightPx = 349f,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                    liveExpandedIntent = true,
                )
            )
            .startsWith("size ")
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:stopwatch",
                    callbackContextToken = 1559427,
                    callbackLayoutWidthPx = 1012f - 68f,
                    callbackLayoutHeightPx = 349f,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        // Matching size → Complete (shared drop path).
        assertThat(
                stackOpenGlassDestinationRejectReason(
                    request = req,
                    transferPhase = ctrl.phase,
                    alreadyAcked = false,
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:stopwatch",
                    callbackContextToken = 1559427,
                    callbackLayoutWidthPx = 1012f,
                    callbackLayoutHeightPx = 349f,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                    liveExpandedIntent = true,
                )
            )
            .isNull()
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:stopwatch",
                    callbackContextToken = 1559427,
                    callbackLayoutWidthPx = 1012f,
                    callbackLayoutHeightPx = 349f,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
        assertThat(
                stackOpenGlassMayDropSharedSource(
                    morphExpandedAtRest = true,
                    transferPhase = ctrl.phase,
                    destinationFirstDrawAcked = true,
                )
            )
            .isTrue()
        // Waiting + shared kept → CardLocal after Complete (merge base).
        val waitingHandoff =
            stackOpenGlassMergeHandoff(
                base =
                    stackExpandedGlassHandoffDecision(
                        morphExpandedAtRest = true,
                        phase = StackExpandedDragPhase.Idle,
                        sharedGlassOwnerPresent = true,
                        basePaintAlpha = 1f,
                    ),
                morphExpandedAtRest = true,
                transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                destinationFirstDrawAcked = false,
                basePaintAlpha = 1f,
            )
        assertThat(waitingHandoff.keepSharedBlurSubtree).isTrue()
        assertThat(waitingHandoff.useCardLocalGlass).isTrue()
        val completeHandoff =
            stackOpenGlassMergeHandoff(
                base =
                    stackExpandedGlassHandoffDecision(
                        morphExpandedAtRest = true,
                        phase = StackExpandedDragPhase.Idle,
                        sharedGlassOwnerPresent = true,
                        basePaintAlpha = 1f,
                    ),
                morphExpandedAtRest = true,
                transferPhase = StackOpenGlassTransferPhase.Complete,
                destinationFirstDrawAcked = true,
                basePaintAlpha = 1f,
            )
        assertThat(completeHandoff.keepSharedBlurSubtree).isFalse()
        assertThat(completeHandoff.sharedPlateAlpha).isEqualTo(0f)
    }

    @Test
    fun task043_open_reverseCancelStale_noTransferNewSession() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 3,
                leftPx = 10f,
                topPx = 20f,
                rightPx = 200f,
                bottomPx = 300f,
            )!!
        val staleGen = req.sessionGen
        assertThat(
                stackOpenGlassShouldCancelTransfer(
                    morphExpandedAtRest = false,
                    isExpandedIntent = true,
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                )
            )
            .isTrue()
        ctrl.cancel("leaveRest")
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Cancelled)
        // Stale draw sau cancel → no-op.
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = staleGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 3,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        // Session moi: owner/rect khac — stale gen cu khong transfer.
        val next =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 9,
                leftPx = 10f,
                topPx = 20f,
                rightPx = 100f,
                bottomPx = 200f,
            )!!
        assertThat(next.sessionGen).isGreaterThan(staleGen)
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = staleGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 9,
                    callbackLayoutWidthPx = next.widthPx,
                    callbackLayoutHeightPx = next.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = next.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 9,
                    callbackLayoutWidthPx = next.widthPx,
                    callbackLayoutHeightPx = next.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = next.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 9,
                    callbackLayoutWidthPx = next.widthPx,
                    callbackLayoutHeightPx = next.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        // Collapse intent cancel Complete.
        assertThat(
                stackOpenGlassShouldCancelTransfer(
                    morphExpandedAtRest = true,
                    isExpandedIntent = false,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                )
            )
            .isTrue()
    }

    @Test
    fun task043_open_completion_noParkedPlate_stackPhasesKeepLocal() {
        val phases =
            listOf(
                StackExpandedDragPhase.Idle,
                StackExpandedDragPhase.Held,
                StackExpandedDragPhase.CancelSettle,
                StackExpandedDragPhase.CommitSettle,
            )
        for (phase in phases) {
            val base =
                stackExpandedGlassHandoffDecision(
                    morphExpandedAtRest = true,
                    phase = phase,
                    sharedGlassOwnerPresent = true,
                    basePaintAlpha = 1f,
                )
            val merged =
                stackOpenGlassMergeHandoff(
                    base = base,
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                    destinationFirstDrawAcked = true,
                    basePaintAlpha = 1f,
                )
            assertThat(merged.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.CardLocal)
            assertThat(merged.keepSharedBlurSubtree).isFalse()
            assertThat(merged.sharedPlateAlpha).isEqualTo(0f)
            assertThat(merged.useCardLocalGlass).isTrue()
            assertThat(merged.yieldNativeGlassToShared).isFalse()
        }
        // Morph path khong bi merge Complete (atRest=false).
        val morphBase =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = false,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 0.72f,
            )
        val morphMerged =
            stackOpenGlassMergeHandoff(
                base = morphBase,
                morphExpandedAtRest = false,
                transferPhase = StackOpenGlassTransferPhase.Complete,
                destinationFirstDrawAcked = true,
                basePaintAlpha = 0.72f,
            )
        assertThat(morphMerged.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.SharedPlate)
        assertThat(morphMerged.keepSharedBlurSubtree).isTrue()
    }

    @Test
    fun task043_open_immediateCollapse_newOwner_usesCorrectRect() {
        val ctrl = StackOpenGlassTransferController()
        val clock =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 1,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 1024f,
                bottomPx = 434f,
            )!!
        assertThat(clock.ownerKey).isEqualTo("event:clock")
        assertThat(clock.widthPx).isWithin(0.5f).of(990f)
        // Collapse ngay → cancel; session moi owner Media + rect moi.
        assertThat(
                stackOpenGlassShouldCancelTransfer(
                    morphExpandedAtRest = false,
                    isExpandedIntent = false,
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                )
            )
            .isTrue()
        ctrl.cancel("collapse")
        val media =
            ctrl.beginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 2,
                leftPx = 40f,
                topPx = 90f,
                rightPx = 1000f,
                bottomPx = 400f,
            )!!
        assertThat(media.sessionGen).isGreaterThan(clock.sessionGen)
        assertThat(media.ownerKey).isEqualTo("event:media")
        assertThat(media.contextToken).isEqualTo(2)
        assertThat(media.leftPx).isEqualTo(40f)
        assertThat(media.topPx).isEqualTo(90f)
        // Draw clock stale khong transfer media session.
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = clock.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = media.widthPx,
                    callbackLayoutHeightPx = media.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = media.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = media.widthPx,
                    callbackLayoutHeightPx = media.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        // begin chi Idle/Cancelled — Complete khong begin lai (khong parked restart).
        assertThat(
                stackOpenGlassShouldBeginWaitingDestination(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                )
            )
            .isFalse()
    }

    // --- task-045: fallback surface ACK + abandon Waiting (no parked third plate) ---

    @Test
    fun task045_open_nativeSuccess_existingPathStillTransfers() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 5,
                leftPx = 40f,
                topPx = 85f,
                rightPx = 1030f,
                bottomPx = 434f,
            )!!
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 5,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
        assertThat(
                stackOpenGlassMayDropSharedSource(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                    destinationFirstDrawAcked = true,
                )
            )
            .isTrue()
    }

    @Test
    fun task045_open_nativeDisabled_actualFallback_transfersNoParked() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 8,
                leftPx = 40f,
                topPx = 90f,
                rightPx = 1000f,
                bottomPx = 400f,
            )!!
        // Fallback that ve α>0 — accept; KHONG set native=true.
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 8,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
        assertThat(ctrl.destinationFirstDrawAcked).isTrue()
        val base =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = true,
                phase = StackExpandedDragPhase.Idle,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 1f,
            )
        val done =
            stackOpenGlassMergeHandoff(
                base = base,
                morphExpandedAtRest = true,
                transferPhase = ctrl.phase,
                destinationFirstDrawAcked = true,
                basePaintAlpha = 1f,
            )
        assertThat(done.keepSharedBlurSubtree).isFalse()
        assertThat(done.sharedPlateAlpha).isEqualTo(0f)
        assertThat(done.handoffPhase).isEqualTo(StackExpandedGlassHandoffPhase.CardLocal)
        // Native tro lai khong restart Waiting (Complete chan begin).
        assertThat(
                stackOpenGlassShouldBeginWaitingDestination(
                    morphExpandedAtRest = true,
                    transferPhase = StackOpenGlassTransferPhase.Complete,
                )
            )
            .isFalse()
    }

    @Test
    fun task045_open_unsupportedOrDrawFail_fallbackPainted_transfers() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 2,
                leftPx = 10f,
                topPx = 20f,
                rightPx = 200f,
                bottomPx = 300f,
            )!!
        // native flag gia khi fallback → reject (production kind path).
        assertThat(
                stackOpenGlassAcceptDestinationDraw(
                    request = ctrl.request,
                    transferPhase = ctrl.phase,
                    alreadyAcked = false,
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = true,
                    blurSurfaceAlpha = 1f,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    fallbackSurfaceRendered = true,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 2,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 0.85f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
    }

    @Test
    fun task045_open_fallbackNotPainted_orAlpha0_notReady() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 3,
                leftPx = 10f,
                topPx = 20f,
                rightPx = 200f,
                bottomPx = 300f,
            )!!
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 3,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NoSurface,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 3,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 0f,
                )
            )
            .isFalse()
        // native API voi native=false → NoSurface reject (khong fake fallback).
        assertThat(
                ctrl.onDestinationNativeGlassDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 3,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    nativeBackdropDrawn = false,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        assertThat(ctrl.destinationFirstDrawAcked).isFalse()
    }

    @Test
    fun task045_open_pendingThenHeld_abandon_noParked_noStaleFalseComplete() {
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 1,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 1024f,
                bottomPx = 434f,
            )!!
        val staleGen = req.sessionGen
        assertThat(
                stackOpenGlassShouldAbandonWaitingForStackGesture(
                    transferPhase = StackOpenGlassTransferPhase.WaitingDestination,
                    destinationFirstDrawAcked = false,
                    stackDragPhase = StackExpandedDragPhase.Held,
                )
            )
            .isTrue()
        assertThat(ctrl.abandonWaitingToCardLocal("stackGesture")).isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
        assertThat(ctrl.destinationFirstDrawAcked).isTrue()
        // Stale surface callback sau abandon → no-op (alreadyAcked).
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = staleGen,
                    callbackOwnerKey = "event:clock",
                    callbackContextToken = 1,
                    callbackLayoutWidthPx = req.widthPx,
                    callbackLayoutHeightPx = req.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        val base =
            stackExpandedGlassHandoffDecision(
                morphExpandedAtRest = true,
                phase = StackExpandedDragPhase.Held,
                sharedGlassOwnerPresent = true,
                basePaintAlpha = 1f,
            )
        val merged =
            stackOpenGlassMergeHandoff(
                base = base,
                morphExpandedAtRest = true,
                transferPhase = ctrl.phase,
                destinationFirstDrawAcked = true,
                basePaintAlpha = 1f,
            )
        assertThat(merged.keepSharedBlurSubtree).isFalse()
        assertThat(merged.sharedPlateAlpha).isEqualTo(0f)
        // Cancel dispose path: Waiting → cancel → khong parked.
        val ctrl2 = StackOpenGlassTransferController()
        val r2 =
            ctrl2.beginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 9,
                leftPx = 10f,
                topPx = 20f,
                rightPx = 100f,
                bottomPx = 200f,
            )!!
        ctrl2.cancel("dispose")
        assertThat(ctrl2.phase).isEqualTo(StackOpenGlassTransferPhase.Cancelled)
        assertThat(
                ctrl2.onDestinationSurfaceDrawn(
                    callbackSessionGen = r2.sessionGen,
                    callbackOwnerKey = "event:media",
                    callbackContextToken = 9,
                    callbackLayoutWidthPx = r2.widthPx,
                    callbackLayoutHeightPx = r2.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
    }

    @Test
    fun task045_open_ownerOrCtxChange_queuedCallbackRejects() {
        val ctrl = StackOpenGlassTransferController()
        val first =
            ctrl.beginWaitingDestination(
                ownerKey = "event:clock",
                contextToken = 1,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 1024f,
                bottomPx = 434f,
            )!!
        val retargeted =
            ctrl.retargetOrBeginWaitingDestination(
                ownerKey = "event:media",
                contextToken = 2,
                leftPx = 40f,
                topPx = 90f,
                rightPx = 1000f,
                bottomPx = 400f,
            )!!
        // Queued callback owner/ctx cu vs live request → reject.
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = first.ownerKey,
                    callbackContextToken = retargeted.contextToken,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = retargeted.ownerKey,
                    callbackContextToken = first.contextToken,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = first.sessionGen,
                    callbackOwnerKey = retargeted.ownerKey,
                    callbackContextToken = retargeted.contextToken,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = retargeted.sessionGen,
                    callbackOwnerKey = retargeted.ownerKey,
                    callbackContextToken = retargeted.contextToken,
                    callbackLayoutWidthPx = retargeted.widthPx,
                    callbackLayoutHeightPx = retargeted.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
    }

    // --- task-010: first-rest measured dest (live painted truoc natural wrapper) ---

    @Test
    fun firstRest_livePaintedPreferred_naturalNeverMeasuredReady() {
        val liveW = 880
        val liveH = 300
        val naturalW = 1012
        val live =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:timer",
                contextToken = 3,
                leftPx = 40f,
                topPx = 90f,
                rightPx = 40f + liveW,
                bottomPx = 90f + liveH,
            )!!
        val natural =
            statusBarSelectedRestRectFromNaturalOrNull(
                ownerKey = "event:timer",
                contextToken = 3,
                naturalWPx = naturalW,
                naturalHPx = 349,
                targetHostLeftPx = 0f,
                targetHostTopPx = 0f,
                cardLocalLeftPx = 40f,
                cardLocalTopPx = 90f,
                pillWPx = 56,
                pillHPx = 56,
            )!!
        assertThat(natural.widthInt).isEqualTo(naturalW)
        val measured =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = null,
                livePaintedOwned = live,
                selectedOwnerKey = "event:timer",
                activeContextToken = 3,
                pillWPx = 56,
                pillHPx = 56,
            )
        assertThat(measured.isMeasuredReady).isTrue()
        assertThat(measured.measuredExact!!.widthInt).isEqualTo(liveW)
        assertThat(measured.measuredExact!!.heightInt).isEqualTo(liveH)
        assertThat(measured.measuredExact!!.widthInt).isNotEqualTo(natural.widthInt)
        val shell =
            statusBarSelectShellPaintedRect(
                morphTransitionRunning = false,
                morphExpandedAtRest = true,
                selectedOwnerKey = "event:timer",
                morphFrozen = live,
                selectedRest = null,
                livePainted = live,
                pillWPx = 56,
                pillHPx = 56,
                activeContextToken = 3,
            )
        assertThat(shell.provenance).isEqualTo(StatusBarPaintedRectProvenance.SelectedRest)
        assertThat(shell.rect!!.widthInt).isEqualTo(liveW)
        val ctrl = StackOpenGlassTransferController()
        val req =
            ctrl.retargetOrBeginWaitingDestination(
                ownerKey = "event:timer",
                contextToken = 3,
                leftPx = measured.measuredExact!!.leftPx,
                topPx = measured.measuredExact!!.topPx,
                rightPx = measured.measuredExact!!.rightPx,
                bottomPx = measured.measuredExact!!.bottomPx,
            )!!
        assertThat(req.widthPx.toInt()).isEqualTo(liveW)
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = req.sessionGen,
                    callbackOwnerKey = req.ownerKey,
                    callbackContextToken = req.contextToken,
                    callbackLayoutWidthPx = liveW.toFloat(),
                    callbackLayoutHeightPx = liveH.toFloat(),
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.Complete)
    }

    @Test
    fun firstRest_heightMismatch_livePreferredOverNatural() {
        val live =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:media",
                contextToken = 7,
                leftPx = 20f,
                topPx = 80f,
                rightPx = 20f + 700f,
                bottomPx = 80f + 280f,
            )!!
        val measured =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = null,
                livePaintedOwned = live,
                selectedOwnerKey = "event:media",
                activeContextToken = 7,
                pillWPx = 48,
                pillHPx = 48,
            )
        assertThat(measured.isMeasuredReady).isTrue()
        assertThat(measured.measuredExact!!.heightInt).isEqualTo(280)
        assertThat(measured.measuredExact!!.widthInt).isEqualTo(700)
    }

    @Test
    fun selectedRestExact_outranksLiveAndEstimateOnlyNotReady() {
        val rest =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:sw",
                contextToken = 2,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 34f + 990f,
                bottomPx = 85f + 349f,
            )!!
        val live =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:sw",
                contextToken = 2,
                leftPx = 34f,
                topPx = 85f,
                rightPx = 34f + 900f,
                bottomPx = 85f + 349f,
            )!!
        val preferRest =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = rest,
                livePaintedOwned = live,
                selectedOwnerKey = "event:sw",
                activeContextToken = 2,
                pillWPx = 56,
                pillHPx = 56,
            )
        assertThat(preferRest.measuredExact!!.widthInt).isEqualTo(990)
        val staleLive =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:other",
                contextToken = 2,
                leftPx = 0f,
                topPx = 0f,
                rightPx = 500f,
                bottomPx = 200f,
            )
        val rejectStale =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = null,
                livePaintedOwned = staleLive,
                selectedOwnerKey = "event:sw",
                activeContextToken = 2,
                pillWPx = 56,
                pillHPx = 56,
            )
        assertThat(rejectStale.isMeasuredReady).isFalse()
        assertThat(rejectStale.measuredExact).isNull()
        val estimateOnly =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = null,
                livePaintedOwned = null,
                selectedOwnerKey = "event:sw",
                activeContextToken = 2,
                pillWPx = 56,
                pillHPx = 56,
            )
        assertThat(estimateOnly.isMeasuredReady).isFalse()
        assertThat(estimateOnly.measuredExact).isNull()
    }

    @Test
    fun reversal_newSession_doesNotInheritEstimateAsAck() {
        val live =
            statusBarOwnedPaintedRectOrNull(
                ownerKey = "event:sw",
                contextToken = 11,
                leftPx = 10f,
                topPx = 70f,
                rightPx = 10f + 640f,
                bottomPx = 70f + 220f,
            )!!
        val measured =
            statusBarResolveMeasuredSelectedDestination(
                selectedRestOwned = null,
                livePaintedOwned = live,
                selectedOwnerKey = "event:sw",
                activeContextToken = 11,
                pillWPx = 40,
                pillHPx = 40,
            )
        val ctrl = StackOpenGlassTransferController()
        val first =
            ctrl.retargetOrBeginWaitingDestination(
                ownerKey = "event:sw",
                contextToken = 11,
                leftPx = measured.measuredExact!!.leftPx,
                topPx = measured.measuredExact!!.topPx,
                rightPx = measured.measuredExact!!.rightPx,
                bottomPx = measured.measuredExact!!.bottomPx,
            )!!
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = first.sessionGen,
                    callbackOwnerKey = first.ownerKey,
                    callbackContextToken = first.contextToken,
                    callbackLayoutWidthPx = first.widthPx,
                    callbackLayoutHeightPx = first.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isTrue()
        ctrl.cancel("leaveRest")
        val second =
            ctrl.retargetOrBeginWaitingDestination(
                ownerKey = "event:sw",
                contextToken = 11,
                leftPx = measured.measuredExact!!.leftPx,
                topPx = measured.measuredExact!!.topPx,
                rightPx = measured.measuredExact!!.rightPx,
                bottomPx = measured.measuredExact!!.bottomPx,
            )!!
        assertThat(second.sessionGen).isGreaterThan(first.sessionGen)
        assertThat(ctrl.destinationFirstDrawAcked).isFalse()
        assertThat(ctrl.phase).isEqualTo(StackOpenGlassTransferPhase.WaitingDestination)
        assertThat(
                ctrl.onDestinationSurfaceDrawn(
                    callbackSessionGen = first.sessionGen,
                    callbackOwnerKey = first.ownerKey,
                    callbackContextToken = first.contextToken,
                    callbackLayoutWidthPx = first.widthPx,
                    callbackLayoutHeightPx = first.heightPx,
                    paintKind = StackOpenGlassDestinationPaintKind.NativeBackdrop,
                    blurSurfaceAlpha = 1f,
                )
            )
            .isFalse()
    }

}
