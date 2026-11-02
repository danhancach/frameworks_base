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
import com.android.systemui.axdynamicbar.ui.compose.statusBarCollapsePresentationParity
import com.android.systemui.axdynamicbar.ui.compose.statusBarHandbackReadyForUnsuppress
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationGenerationValid
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationMatchesOwner
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationOf
import com.google.common.truth.Truth.assertThat
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
        assertThat(statusBarMorphDummyContentAlpha(0f, closing = false)).isEqualTo(1f)
        // Con full den handoff start; fade trong [0.35, 0.55].
        assertThat(statusBarMorphDummyContentAlpha(0.35f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphDummyContentAlpha(0.55f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.45f, closing = false)).isWithin(0.01f).of(0.5f)
        assertThat(statusBarMorphDummyContentAlpha(0.2f, closing = false)).isEqualTo(1f)
    }

    @Test
    fun dummyContentAlpha_collapse_returnsBeforePillHandoff() {
        // Dong: progress cao → dummy thap; ve pill (progress↓) → dummy len truoc khi card blank xong.
        assertThat(statusBarMorphDummyContentAlpha(1f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0f, closing = true)).isEqualTo(1f)
        assertThat(statusBarMorphDummyContentAlpha(0.55f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphDummyContentAlpha(0.35f, closing = true)).isEqualTo(1f)
        // Mid handoff close: dummy dang len khi card dang xuong.
        val midDummy = statusBarMorphDummyContentAlpha(0.45f, closing = true)
        val midCard = statusBarMorphCardContentAlpha(0.45f, closing = true)
        assertThat(midDummy).isWithin(0.01f).of(0.5f)
        assertThat(midCard).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun contentHandoff_crossfadeNoEmptyMiddle() {
        // Mid handoff open: ca hai >0, complementary ~1 — khong gap shell-only.
        val dummy = statusBarMorphDummyContentAlpha(0.45f, closing = false)
        val card = statusBarMorphCardContentAlpha(0.45f, closing = false)
        assertThat(dummy).isWithin(0.01f).of(0.5f)
        assertThat(card).isWithin(0.01f).of(0.5f)
        assertThat(dummy + card).isWithin(0.02f).of(1f)
        assertThat(statusBarMorphContentHandoffCoherent(dummy, card, shellAlpha = 1f)).isTrue()
        // Quet toan bo open/close: khong empty middle.
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = false)).isTrue()
        assertThat(statusBarMorphContentHandoffNoEmptyGap(closing = true)).isTrue()
        // Shell-only (ca hai content=0) KHONG coherent — khong tautology shell>0.
        assertThat(statusBarMorphContentHandoffCoherent(0f, 0f, shellAlpha = 1f)).isFalse()
        assertThat(statusBarMorphContentHandoffCoherent(0f, 0f, shellAlpha = 0f)).isFalse()
    }

    @Test
    fun cardContentAlpha_p0Zero_restFull() {
        assertThat(statusBarMorphCardContentAlpha(0f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(1f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphCardContentAlpha(0.35f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.55f, closing = false)).isEqualTo(1f)
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
        // Open p=0.2: dummy full, card 0 (chua handoff).
        assertThat(statusBarMorphDummyContentAlpha(0.2f, false)).isEqualTo(1f)
        assertThat(statusBarMorphCardContentAlpha(0.2f, false)).isEqualTo(0f)
        // Open p=0.7: sau End — dummy 0, card full.
        assertThat(statusBarMorphDummyContentAlpha(0.7f, false)).isEqualTo(0f)
        assertThat(statusBarMorphCardContentAlpha(0.7f, false)).isEqualTo(1f)
        // Close p=0.95: card full, dummy 0.
        assertThat(statusBarMorphCardContentAlpha(0.95f, true)).isEqualTo(1f)
        assertThat(statusBarMorphDummyContentAlpha(0.95f, true)).isEqualTo(0f)
        // Close p=0.2: dummy full, card 0 (da handoff ve pill).
        assertThat(statusBarMorphDummyContentAlpha(0.2f, true)).isEqualTo(1f)
        assertThat(statusBarMorphCardContentAlpha(0.2f, true)).isEqualTo(0f)
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
    fun shellGlassAlpha_open_fullBeforeHandoff_zeroAtEnd() {
        // Cua so content van 0.35–0.55; glass quadratic roi nhanh hon card linear.
        assertThat(statusBarMorphShellGlassAlpha(0f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.35f, closing = false)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0.55f, closing = false)).isEqualTo(0f)
        assertThat(statusBarMorphShellGlassAlpha(1f, closing = false)).isEqualTo(0f)
        val midGlass = statusBarMorphShellGlassAlpha(0.45f, closing = false)
        val midCard = statusBarMorphCardContentAlpha(0.45f, closing = false)
        // mid t=0.5 → glass=(0.5)^2=0.25 < card 0.5 — bot double blur.
        assertThat(midGlass).isWithin(0.01f).of(0.25f)
        assertThat(midCard).isWithin(0.01f).of(0.5f)
        assertThat(midGlass).isLessThan(midCard)
    }

    @Test
    fun shellGlassAlpha_close_risesTowardPill() {
        assertThat(statusBarMorphShellGlassAlpha(1f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphShellGlassAlpha(0.55f, closing = true)).isEqualTo(0f)
        assertThat(statusBarMorphShellGlassAlpha(0.35f, closing = true)).isEqualTo(1f)
        assertThat(statusBarMorphShellGlassAlpha(0f, closing = true)).isEqualTo(1f)
        val mid = statusBarMorphShellGlassAlpha(0.45f, closing = true)
        assertThat(mid).isWithin(0.01f).of(0.25f)
    }

    @Test
    fun shellTintProgress_smoothstep_midBelowLinear() {
        // smoothstep(0.5)=0.5; soft ends — dau/cuoi saturates.
        assertThat(statusBarMorphShellTintProgress(0f)).isEqualTo(0f)
        assertThat(statusBarMorphShellTintProgress(1f)).isEqualTo(1f)
        assertThat(statusBarMorphShellTintProgress(0.5f)).isWithin(0.01f).of(0.5f)
        // Early: smoothstep < linear (bot nhay mau som).
        assertThat(statusBarMorphShellTintProgress(0.25f)).isLessThan(0.25f)
    }

    @Test
    fun cardSurfaceBlurFactor_suppressesWhileShellStrong() {
        assertThat(statusBarMorphCardSurfaceBlurFactor(0f, 1f)).isEqualTo(0f)
        assertThat(statusBarMorphCardSurfaceBlurFactor(1f, 0f)).isEqualTo(1f)
        assertThat(statusBarMorphCardSurfaceBlurFactor(0.5f, 0.25f))
            .isWithin(0.01f)
            .of(0.75f)
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
        // Khong doi 0.35–0.55; complementary content van no-gap.
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
        // Closing @p0: dummy content alpha 1; * chrome 0 sau ack = khong double paint dai.
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
        // Timeout path rieng khi OnDraw khong toi.
        val c2 = CollapseHandbackHoldController()
        val s2 = c2.beginSettleHandback(51, ownerId = "x")
        assertThat(c2.acknowledgeLiveDrawnTimeout(s2.holdGeneration)).isTrue()
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
        // Timeout path + reopen (new settle gen).
        val c3 = CollapseHandbackHoldController()
        val s3 = c3.beginSettleHandback(92, ownerId = "a")
        assertThat(c3.acknowledgeLiveDrawnTimeout(s3.holdGeneration)).isTrue()
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
}
