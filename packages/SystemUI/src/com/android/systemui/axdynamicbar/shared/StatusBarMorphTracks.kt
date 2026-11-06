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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SSOT 4 track STATUS BAR pill↔panel (F741N One UI9 CardStackView / colorTransition).
 *
 * (1) Geometry — shell ChangeBounds-like, PathInterpolator overshoot, 500/600.
 * (2) Dummy pill CONTENT — spring scale + alpha 100ms open / 200ms close; KHONG scale shell.
 * (3) Panel CONTENT — width-gate + remap; collapse early hide.
 * (4) Color — ArgbEvaluator-like progress; Decelerate 500 open / collapseColor 600 close.
 *
 * Blur/shell glass hold doc lap color tint. linearT = elapsed/duration (KHONG geom eased).
 */

// --- Durations (F741N VERIFIED) ---

/** Expand geometry + NIC hide + color open. */
internal const val StatusBarMorphExpandMs = 500

/** Collapse geometry + NIC show + color close. */
internal const val StatusBarMorphCollapseMs = 600

/** Dummy alpha fade EXPAND (CardStackView VA 100L). */
internal const val StatusBarMorphDummyOpenFadeMs = 100

/** Dummy alpha fade COLLAPSE (ViewPropertyAnimator 200L). */
internal const val StatusBarMorphDummyCloseFadeMs = 200

/**
 * Panel collapse early hide budget — Samsung enter: INVISIBLE ngay.
 * Soft-fade toi da ~50ms neu can; mac dinh alpha=0 khi closing.
 */
internal const val StatusBarMorphPanelCloseHideMs = 50

/**
 * Exclusive pill↔panel gap: ca hai α≈0 (glass van cover) truoc content kia len.
 * Chuan 50ms trong khoang user 40–70ms.
 */
internal const val StatusBarMorphContentExclusiveGapMs = 50f

/** Nguong coi content alpha "co" — khong ca hai > eps cung luc. */
internal const val StatusBarMorphContentExclusiveEpsilon = 0.01f

/** Samsung contents width gate 239dp — ADAPT min(gate, 0.9*target) cho short card. */
internal const val StatusBarMorphPanelWidthGateDp = 239f

/**
 * Soft handoff shell↔native [start, 1]: open release / close take.
 * KHONG hard step .999.
 */
internal const val StatusBarMorphShellHandoffStart = 0.97f

/**
 * Min SRC_OVER backplate coverage: shell + parentContentα × nativeBlurBg.
 * Frost AlphaGlass < 1 → day la proxy alpha lop, khong claim pixel-opaque.
 */
internal const val StatusBarMorphMinBackplateCoverage = 0.95f

/**
 * Shell >= nguong → uu tien 1-owner (tat native blur neu shell tu cover).
 * Bang [StatusBarMorphMinBackplateCoverage] — coverage-driven, khong con .15 gap.
 */
internal const val StatusBarMorphShellOwnsBlurAbove = StatusBarMorphMinBackplateCoverage

/** Dummy spring SCALE peak (EXPAND). */
internal const val StatusBarMorphDummyScalePeak = 2f

/** SpringForce stiffness (DynamicAnimation mass=1). */
internal const val StatusBarMorphDummySpringStiffness = 200f

/** SpringForce dampingRatio. */
internal const val StatusBarMorphDummySpringDamping = 0.8131728f

// --- Easing ---

/**
 * expandRoot / collapseRoot — PathInterpolator(0.4, 1.22, 0.47, 0.99).
 * Overshoot y>1; caller clamp corner / left neo (statusBarMorphLerpVisualRectPx).
 */
internal val StatusBarMorphGeometryEasing: Easing =
    CubicBezierEasing(0.4f, 1.22f, 0.47f, 0.99f)

/** Alias API cu — geometry track. */
internal val StatusBarMorphMotionEasing: Easing = StatusBarMorphGeometryEasing

/**
 * collapseColorInterpolator — PathInterpolator(0.13, 1.01, 0.36, 0.98).
 * F741N colorTransition CLOSE = 600ms (KHONG 650 OneUI7).
 */
internal val StatusBarMorphColorCloseEasing: Easing =
    CubicBezierEasing(0.13f, 1.01f, 0.36f, 0.98f)

/** Android DecelerateInterpolator(factor=1): 1-(1-t)^2. */
internal fun statusBarMorphDecelerateFraction(t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    val v = 1f - u
    return 1f - v * v
}

internal fun statusBarMorphDurationMs(closing: Boolean): Int =
    if (closing) StatusBarMorphCollapseMs else StatusBarMorphExpandMs

/**
 * expandedFraction tu updateTransition (1=expanded, 0=collapsed).
 * OPEN: elapsed = p * 500. CLOSE: elapsed = (1-p) * 600 (p giam 1→0).
 */
internal fun statusBarMorphElapsedMs(expandedFraction: Float, closing: Boolean): Float {
    val p = expandedFraction.coerceIn(0f, 1f)
    return if (closing) {
        (1f - p) * StatusBarMorphCollapseMs.toFloat()
    } else {
        p * StatusBarMorphExpandMs.toFloat()
    }
}

// =============================================================================
// TRACK 1 — Geometry (shell)
// =============================================================================

/**
 * Geom progress sau PathInterpolator; overshoot co the >1 / <0.
 * Corner dung [statusBarMorphGeometryCornerProgress]; left neo visualTargetW.
 */
internal fun statusBarMorphGeometryProgress(easedFraction: Float): Float = easedFraction

/** Corner / pCorner clamp — tranh blank khi overshoot. */
internal fun statusBarMorphGeometryCornerProgress(easedFraction: Float): Float =
    easedFraction.coerceIn(0f, 1f)

// =============================================================================
// TRACK 2 — Dummy pill CONTENT (scale + alpha)
// =============================================================================

internal data class StatusBarMorphDummyTrack(
    /** Uniform scale CONTENT (sau inverse host sx/sy). */
    val scale: Float,
    /** Dummy chrome alpha. */
    val alpha: Float,
)

/**
 * Underdamped spring x(t) mass=1 → [to] (Android SpringAnimation closed form).
 * tSeconds >= 0; v0=0.
 */
internal fun statusBarMorphSpringScaleAt(
    elapsedMs: Float,
    from: Float,
    to: Float,
    stiffness: Float = StatusBarMorphDummySpringStiffness,
    dampingRatio: Float = StatusBarMorphDummySpringDamping,
): Float {
    val t = (elapsedMs.coerceAtLeast(0f) / 1000f)
    if (t <= 0f) return from
    val k = stiffness.coerceAtLeast(1e-3f)
    val zeta = dampingRatio.coerceIn(0.01f, 0.999f)
    val w0 = sqrt(k)
    val x0 = from - to
    if (kotlin.math.abs(x0) < 1e-6f) return to
    val wd = w0 * sqrt(1f - zeta * zeta)
    val decay = exp(-zeta * w0 * t)
    val cosTerm = cos(wd * t)
    val sinTerm = sin(wd * t)
    // v0=0 → coeff sin = (zeta*w0*x0)/wd
    val x = to + decay * (x0 * cosTerm + (zeta * w0 * x0) / wd * sinTerm)
    return x.coerceAtLeast(0.01f)
}

/**
 * Dummy CONTENT track theo elapsed ms — DOC LAP geometry eased.
 * OPEN: scale 1→~2 spring + alpha 1→0 trong 100ms dau.
 * CLOSE: alpha 0→1 trong 200ms som (khong doi p<=.28); scale →1 spring.
 */
internal fun statusBarMorphDummyTrack(
    elapsedMs: Float,
    closing: Boolean,
): StatusBarMorphDummyTrack {
    val ms = elapsedMs.coerceAtLeast(0f)
    return if (closing) {
        val alpha = (ms / StatusBarMorphDummyCloseFadeMs.toFloat()).coerceIn(0f, 1f)
        val scale =
            statusBarMorphSpringScaleAt(
                elapsedMs = ms,
                from = StatusBarMorphDummyScalePeak,
                to = 1f,
            )
        StatusBarMorphDummyTrack(scale = scale, alpha = alpha)
    } else {
        val alpha =
            (1f - ms / StatusBarMorphDummyOpenFadeMs.toFloat()).coerceIn(0f, 1f)
        val scale =
            statusBarMorphSpringScaleAt(
                elapsedMs = ms,
                from = 1f,
                to = StatusBarMorphDummyScalePeak,
            )
        StatusBarMorphDummyTrack(scale = scale, alpha = alpha)
    }
}

/**
 * Compat: expandedFraction (1=open, 0=closed) → dummy alpha.
 * Call site: morphLinearT tu transition — KHONG geom eased overshoot.
 */
internal fun statusBarMorphDummyContentAlpha(progress: Float, closing: Boolean): Float {
    val ms = statusBarMorphElapsedMs(progress, closing)
    return statusBarMorphDummyTrack(ms, closing).alpha
}

/** expandedFraction → dummy uniform scale. */
internal fun statusBarMorphDummyContentScale(progress: Float, closing: Boolean): Float {
    val ms = statusBarMorphElapsedMs(progress, closing)
    return statusBarMorphDummyTrack(ms, closing).scale
}

/**
 * Local scale CONTENT trong host anisotropic — man hinh ISOTROPIC theo shell growth.
 *
 * Visual: screenUniform = min(growX, growY) — KHONG nhan dummyScale spring (1→2).
 * Spring track van noi bo (debug/snapshot); nhan spring → content zoom nhanh/lon hon shell.
 * local = (screenUniform/sx, screenUniform/sy); screen W/H ≤ shell moi frame; isotropic.
 *
 * [dummyScale] giu signature call-site / spring track — KHONG multiply vao visual.
 * [pillWPx]/[visualTarget*]: bat buoc de grow dung; default 1f = min(sx,sy).
 */
internal fun statusBarMorphDummyLayerScale(
    hostSx: Float,
    hostSy: Float,
    @Suppress("UNUSED_PARAMETER") dummyScale: Float,
    pillWPx: Float = 1f,
    pillHPx: Float = 1f,
    visualTargetWPx: Float = pillWPx,
    visualTargetHPx: Float = pillHPx,
): Pair<Float, Float> {
    val sx = hostSx.coerceAtLeast(1e-4f)
    val sy = hostSy.coerceAtLeast(1e-4f)
    val growX =
        (visualTargetWPx.coerceAtLeast(1f) / pillWPx.coerceAtLeast(1f)) * sx
    val growY =
        (visualTargetHPx.coerceAtLeast(1f) / pillHPx.coerceAtLeast(1f)) * sy
    // Chi shell growth — khong * spring dummyScale.
    val screenUniform = minOf(growX, growY)
    return (screenUniform / sx) to (screenUniform / sy)
}

/**
 * Alpha dummy CONTENT — CLOSE giu track 200ms; OPEN remap theo geometry shell.
 * User visual override: phai thay content di theo spatial travel (khong bien mat @100ms).
 */
internal fun statusBarMorphDummyFollowContentAlpha(
    elapsedMs: Float,
    closing: Boolean,
    hostSx: Float,
    pillWPx: Float,
    visualTargetWPx: Float,
    trackAlpha: Float,
): Float {
    if (closing) return trackAlpha.coerceIn(0f, 1f)
    val sx0 =
        (pillWPx.coerceAtLeast(1f) / visualTargetWPx.coerceAtLeast(1f)).coerceIn(1e-4f, 1f)
    val growth =
        ((hostSx.coerceAtLeast(0f) - sx0) / (1f - sx0).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
    // Full den ~20% growth; fade het @55% — thay vi alpha0 @100ms Samsung raw.
    val geo =
        when {
            growth <= 0.20f -> 1f
            growth >= 0.55f -> 0f
            else -> 1f - (growth - 0.20f) / 0.35f
        }
    return maxOf(trackAlpha.coerceIn(0f, 1f), geo).coerceIn(0f, 1f)
}

/**
 * Offset local FG trong shell (TopStart + TransformOrigin 0,0) de tam visible = tam shell.
 *
 * offset = ((targetW − pillW·localSx)/2, (targetH − pillH·localSy)/2)
 * KHONG cong sidePad/topInset (parent shell da offset). p0 → (0,0) parity pill.
 */
internal data class StatusBarMorphDummyContentLocalOffset(
    val xPx: Float,
    val yPx: Float,
)

internal fun statusBarMorphDummyContentLocalOffsetPx(
    pillWPx: Float,
    pillHPx: Float,
    localSx: Float,
    localSy: Float,
    visualTargetWPx: Float,
    visualTargetHPx: Float,
): StatusBarMorphDummyContentLocalOffset {
    val scaledW = pillWPx.coerceAtLeast(0f) * localSx.coerceAtLeast(0f)
    val scaledH = pillHPx.coerceAtLeast(0f) * localSy.coerceAtLeast(0f)
    return StatusBarMorphDummyContentLocalOffset(
        xPx = (visualTargetWPx - scaledW) * 0.5f,
        yPx = (visualTargetHPx - scaledH) * 0.5f,
    )
}

/**
 * Screen bounds CONTENT (FG canh giua shell) — test center + uniform scale.
 * screenW/H = pill * screenUniform; center = SHELL center (khong neo TopStart).
 */
internal data class StatusBarMorphContentScreenBounds(
    val centerXPx: Float,
    val centerYPx: Float,
    val widthPx: Float,
    val heightPx: Float,
    val uniformScale: Float,
)

internal fun statusBarMorphContentScreenBounds(
    shellLeftPx: Float,
    shellTopPx: Float,
    hostSx: Float,
    hostSy: Float,
    dummyScale: Float,
    pillWPx: Float,
    pillHPx: Float,
    visualTargetWPx: Float,
    visualTargetHPx: Float,
): StatusBarMorphContentScreenBounds {
    val (localSx, localSy) =
        statusBarMorphDummyLayerScale(
            hostSx = hostSx,
            hostSy = hostSy,
            dummyScale = dummyScale,
            pillWPx = pillWPx,
            pillHPx = pillHPx,
            visualTargetWPx = visualTargetWPx,
            visualTargetHPx = visualTargetHPx,
        )
    val sx = hostSx.coerceAtLeast(1e-4f)
    val sy = hostSy.coerceAtLeast(1e-4f)
    val off =
        statusBarMorphDummyContentLocalOffsetPx(
            pillWPx = pillWPx,
            pillHPx = pillHPx,
            localSx = localSx,
            localSy = localSy,
            visualTargetWPx = visualTargetWPx,
            visualTargetHPx = visualTargetHPx,
        )
    // Man hinh: (localOffset + halfScaledLocal) * hostS = half visualTarget * hostS.
    val w = pillWPx.coerceAtLeast(0f) * localSx * sx
    val h = pillHPx.coerceAtLeast(0f) * localSy * sy
    // Uniform = min(grow) — khong * dummyScale spring.
    val uni =
        if (pillWPx > 1e-3f) w / pillWPx
        else
            minOf(
                (visualTargetWPx / pillWPx.coerceAtLeast(1f)) * sx,
                (visualTargetHPx / pillHPx.coerceAtLeast(1f)) * sy,
            )
    // FG offset canh giua: center = shellLeft + (off + halfLocalScaled) * hostS
    // == shell center (khong neo TopStart shellLeft+w/2).
    val contentCx = shellLeftPx + (off.xPx + pillWPx.coerceAtLeast(0f) * localSx * 0.5f) * sx
    val contentCy = shellTopPx + (off.yPx + pillHPx.coerceAtLeast(0f) * localSy * 0.5f) * sy
    return StatusBarMorphContentScreenBounds(
        centerXPx = contentCx,
        centerYPx = contentCy,
        widthPx = w,
        heightPx = h,
        uniformScale = uni,
    )
}

/** Tam shell man hinh — doi chieu test center FG (≤1px). */
internal fun statusBarMorphShellCenterScreenPx(
    shellLeftPx: Float,
    shellTopPx: Float,
    hostSx: Float,
    hostSy: Float,
    visualTargetWPx: Float,
    visualTargetHPx: Float,
): Pair<Float, Float> {
    val sx = hostSx.coerceAtLeast(1e-4f)
    val sy = hostSy.coerceAtLeast(1e-4f)
    return (shellLeftPx + visualTargetWPx * sx * 0.5f) to
        (shellTopPx + visualTargetHPx * sy * 0.5f)
}

// =============================================================================
// Exclusive pill ↔ panel content handoff (OPEN/CLOSE gap, glass cover)
// =============================================================================

/** Pill + panel alpha sau exclusive handoff (khong overlap ca hai > eps). */
internal data class StatusBarMorphExclusiveContentAlphas(
    val pillAlpha: Float,
    val panelAlpha: Float,
)

/**
 * Pure OPEN/CLOSE exclusive handoff theo elapsed + gate.
 *
 * OPEN: pill fade (track 100ms) → gap ≥40ms ca hai ~0 → panel remap sau max(gate, pillZero+gap).
 * CLOSE: panel soft-hide → gap → pill fade-in (delay DummyCloseFade).
 * Geom geo 20–55% co the overlap panel neu khong gate; day ep sequential.
 * Reverse: dung [StatusBarMorphExclusiveContentHandoff] latch — pure nay = uninterrupted.
 */
internal fun statusBarMorphExclusiveContentAlphas(
    elapsedMs: Float,
    durationMs: Float,
    closing: Boolean,
    @Suppress("UNUSED_PARAMETER") measuredWidthPx: Float,
    @Suppress("UNUSED_PARAMETER") widthGatePx: Float,
    gateStartFraction: Float?,
): StatusBarMorphExclusiveContentAlphas {
    val ms = elapsedMs.coerceAtLeast(0f)
    val dur = durationMs.coerceAtLeast(1f)
    val gap = StatusBarMorphContentExclusiveGapMs.coerceIn(40f, 70f)
    if (closing) {
        val panelHideT =
            (ms / StatusBarMorphPanelCloseHideMs.toFloat()).coerceIn(0f, 1f)
        val panel = (1f - panelHideT).coerceIn(0f, 1f)
        val pillStartMs = StatusBarMorphPanelCloseHideMs.toFloat() + gap
        val pill =
            if (ms <= pillStartMs) {
                0f
            } else {
                ((ms - pillStartMs) / StatusBarMorphDummyCloseFadeMs.toFloat())
                    .coerceIn(0f, 1f)
            }
        return StatusBarMorphExclusiveContentAlphas(pillAlpha = pill, panelAlpha = panel)
    }
    // OPEN: pill track fade → gap → panel (sau gate).
    val pillEndMs = StatusBarMorphDummyOpenFadeMs.toFloat()
    val pill = (1f - ms / pillEndMs).coerceIn(0f, 1f)
    // Chua latch gate → panel 0 (widthGate chi dung luc observeCrossing).
    if (gateStartFraction == null) {
        return StatusBarMorphExclusiveContentAlphas(pillAlpha = pill, panelAlpha = 0f)
    }
    val gateMs = gateStartFraction.coerceIn(0f, 0.99f) * dur
    val panelStartMs = maxOf(gateMs, pillEndMs + gap)
    val panel =
        if (ms < panelStartMs) {
            0f
        } else {
            val denom = (dur - panelStartMs).coerceAtLeast(0.01f)
            ((ms - panelStartMs) / denom).coerceIn(0f, 1f)
        }
    return StatusBarMorphExclusiveContentAlphas(pillAlpha = pill, panelAlpha = panel)
}

/**
 * Controller exclusive — latch pill/panel zero elapsed; reverse mid-flight khong overlap.
 * Pure [statusBarMorphExclusiveContentAlphas] cho uninterrupted; day them geo latch.
 *
 * Reverse: giu snapshot alpha (khong reset→0); interpolate lien tuc; gap ≥40ms khi doi owner;
 * neu chi 1 content visible va reverse khong can swap owner ngay → khong blank frame0.
 * CLOSE pill sau gap: fade tu 0 (KHONG maxOf(fade,pillN) snap 0→0.8).
 */
internal class StatusBarMorphExclusiveContentHandoff {
    private var directionClosing: Boolean? = null
    private var pillZeroElapsedMs: Float? = null
    private var panelZeroElapsedMs: Float? = null
    /** Snapshot alpha luc doi huong (continuity). */
    private var reversePill0: Float = 0f
    private var reversePanel0: Float = 0f
    private var hasReverseSnapshot: Boolean = false
    /** CLOSE/OPEN: dang o nhánh 1-owner (khong ep gap blank). */
    private var sameOwnerFastPath: Boolean = false
    private var lastPill: Float = 0f
    private var lastPanel: Float = 0f

    fun reset() {
        directionClosing = null
        pillZeroElapsedMs = null
        panelZeroElapsedMs = null
        reversePill0 = 0f
        reversePanel0 = 0f
        hasReverseSnapshot = false
        sameOwnerFastPath = false
        lastPill = 0f
        lastPanel = 0f
    }

    /**
     * Chuan bi reverse tu visual dang hien — giu alpha, khong reset ve 0.
     * Goi truoc evaluate segment moi (elapsed thuong 0).
     */
    fun captureReverseFrom(pillAlpha: Float, panelAlpha: Float) {
        reversePill0 = pillAlpha.coerceIn(0f, 1f)
        reversePanel0 = panelAlpha.coerceIn(0f, 1f)
        lastPill = reversePill0
        lastPanel = reversePanel0
        hasReverseSnapshot = true
        pillZeroElapsedMs = null
        panelZeroElapsedMs = null
        sameOwnerFastPath = false
        // directionClosing clear — evaluate se set + chon fast-path.
        directionClosing = null
    }

    /**
     * [naturalPill]/[naturalPanel] = follow/geo + gate remap truoc exclusive.
     * Tra pair khong ca hai > [StatusBarMorphContentExclusiveEpsilon].
     */
    fun evaluate(
        elapsedMs: Float,
        durationMs: Float,
        closing: Boolean,
        naturalPill: Float,
        naturalPanel: Float,
    ): StatusBarMorphExclusiveContentAlphas {
        val ms = elapsedMs.coerceAtLeast(0f)
        val gap = StatusBarMorphContentExclusiveGapMs.coerceIn(40f, 70f)
        val eps = StatusBarMorphContentExclusiveEpsilon
        val pillN = naturalPill.coerceIn(0f, 1f)
        val panelN = naturalPanel.coerceIn(0f, 1f)

        if (directionClosing == null) {
            directionClosing = closing
            if (hasReverseSnapshot) {
                // Chon fast-path neu reverse khong can swap owner ngay.
                sameOwnerFastPath =
                    if (closing) {
                        // Ve pill: panel da ~0 va pill dang/con visible.
                        reversePanel0 <= eps && reversePill0 > eps
                    } else {
                        // Ve panel: pill da ~0 va panel dang visible.
                        reversePill0 <= eps && reversePanel0 > eps
                    }
            }
        } else if (directionClosing != closing) {
            // Mid-flight reverse trong cung controller: chup last visual.
            captureReverseFrom(lastPill, lastPanel)
            directionClosing = closing
            sameOwnerFastPath =
                if (closing) {
                    reversePanel0 <= eps && reversePill0 > eps
                } else {
                    reversePill0 <= eps && reversePanel0 > eps
                }
        }

        val out =
            if (closing) {
                evaluateClosing(ms, gap, eps, pillN, panelN)
            } else {
                evaluateOpening(ms, gap, eps, pillN, panelN)
            }
        lastPill = out.pillAlpha
        lastPanel = out.panelAlpha
        return out
    }

    private fun evaluateClosing(
        ms: Float,
        gap: Float,
        eps: Float,
        pillN: Float,
        panelN: Float,
    ): StatusBarMorphExclusiveContentAlphas {
        if (sameOwnerFastPath && hasReverseSnapshot) {
            // Pill dang hien, panel=0: pill → 1, khong blank gap.
            val fadeT =
                (ms / StatusBarMorphDummyCloseFadeMs.toFloat()).coerceIn(0f, 1f)
            val pill =
                (reversePill0 + (1f - reversePill0) * fadeT).coerceIn(0f, 1f)
            return statusBarMorphExclusiveClamp(pill, 0f)
        }
        // Panel ha truoc (tu snapshot neu reverse, else natural).
        val panel =
            if (hasReverseSnapshot && reversePanel0 > eps) {
                val hideT =
                    (ms / StatusBarMorphPanelCloseHideMs.toFloat()).coerceIn(0f, 1f)
                (reversePanel0 * (1f - hideT)).coerceIn(0f, 1f)
            } else {
                panelN
            }
        if (panel <= eps && panelZeroElapsedMs == null) {
            panelZeroElapsedMs = ms
        }
        val zeroAt = panelZeroElapsedMs
        val pill =
            when {
                zeroAt == null -> 0f
                ms < zeroAt + gap -> 0f
                else -> {
                    // Fade tu 0 — KHONG maxOf(fade,pillN) (tranh snap 0→0.8).
                    ((ms - zeroAt - gap) / StatusBarMorphDummyCloseFadeMs.toFloat())
                        .coerceIn(0f, 1f)
                }
            }
        return statusBarMorphExclusiveClamp(pill, panel)
    }

    private fun evaluateOpening(
        ms: Float,
        gap: Float,
        eps: Float,
        pillN: Float,
        panelN: Float,
    ): StatusBarMorphExclusiveContentAlphas {
        if (sameOwnerFastPath && hasReverseSnapshot) {
            // Panel dang hien, pill=0: panel → 1, khong blank.
            val t = (ms / 200f).coerceIn(0f, 1f)
            val panel =
                (reversePanel0 + (1f - reversePanel0) * t).coerceIn(0f, 1f)
            return statusBarMorphExclusiveClamp(0f, panel)
        }
        // Pill ha truoc (tu snapshot neu reverse).
        val pill =
            if (hasReverseSnapshot && reversePill0 > eps) {
                val fadeT =
                    (ms / StatusBarMorphDummyOpenFadeMs.toFloat()).coerceIn(0f, 1f)
                (reversePill0 * (1f - fadeT)).coerceIn(0f, 1f)
            } else if (hasReverseSnapshot && reversePill0 <= eps) {
                0f
            } else {
                pillN
            }
        if (pill <= eps && pillZeroElapsedMs == null) {
            pillZeroElapsedMs = ms
        }
        val zeroAt = pillZeroElapsedMs
        val panel =
            when {
                zeroAt == null -> 0f
                ms < zeroAt + gap -> 0f
                // Natural panel (caller lerp lien tuc) — khong maxOf snap.
                else -> panelN
            }
        return statusBarMorphExclusiveClamp(pill, panel)
    }
}

/** Ep khong overlap; uu tien huong: OPEN giu pill / CLOSE giu panel khi xung dot. */
internal fun statusBarMorphExclusiveClamp(
    pillAlpha: Float,
    panelAlpha: Float,
    preferPill: Boolean = false,
): StatusBarMorphExclusiveContentAlphas {
    val eps = StatusBarMorphContentExclusiveEpsilon
    var pill = pillAlpha.coerceIn(0f, 1f)
    var panel = panelAlpha.coerceIn(0f, 1f)
    if (pill > eps && panel > eps) {
        if (preferPill) panel = 0f else pill = 0f
    }
    return StatusBarMorphExclusiveContentAlphas(pillAlpha = pill, panelAlpha = panel)
}

/**
 * Quet OPEN/CLOSE: khong frame nao ca hai > eps; co doan gap ≥40ms ca hai ≤eps (standard gate).
 */
internal fun statusBarMorphExclusiveHandoffHasGap(
    closing: Boolean,
    durationMs: Float = statusBarMorphDurationMs(closing).toFloat(),
    gateStartFraction: Float? =
        (StatusBarMorphDummyOpenFadeMs.toFloat() / statusBarMorphDurationMs(false).toFloat())
            .coerceIn(0f, 0.99f),
    sampleDtMs: Float = 5f,
): Boolean {
    val dur = durationMs.coerceAtLeast(1f)
    val gapNeed = 40f
    val eps = StatusBarMorphContentExclusiveEpsilon
    var bothZeroRun = 0f
    var sawGap = false
    var prevBothZero = false
    var t = 0f
    while (t <= dur + 0.01f) {
        val a =
            statusBarMorphExclusiveContentAlphas(
                elapsedMs = t,
                durationMs = dur,
                closing = closing,
                measuredWidthPx = 2000f,
                widthGatePx = 100f,
                gateStartFraction = if (closing) null else gateStartFraction,
            )
        if (a.pillAlpha > eps && a.panelAlpha > eps) return false
        val bothZero = a.pillAlpha <= eps && a.panelAlpha <= eps
        if (bothZero) {
            bothZeroRun = if (prevBothZero) bothZeroRun + sampleDtMs else sampleDtMs
            if (bothZeroRun >= gapNeed - 0.5f) sawGap = true
        } else {
            bothZeroRun = 0f
        }
        prevBothZero = bothZero
        t += sampleDtMs
    }
    // Rest: OPEN panel full / CLOSE pill full.
    val end =
        statusBarMorphExclusiveContentAlphas(
            elapsedMs = dur,
            durationMs = dur,
            closing = closing,
            measuredWidthPx = 2000f,
            widthGatePx = 100f,
            gateStartFraction = if (closing) null else gateStartFraction,
        )
    if (closing) {
        if (end.pillAlpha < 1f - eps) return false
        if (end.panelAlpha > eps) return false
    } else {
        if (end.panelAlpha < 1f - eps) return false
        if (end.pillAlpha > eps) return false
    }
    return sawGap
}

// =============================================================================
// TRACK 3 — Panel CONTENT (width-gate + early close)
// =============================================================================

/**
 * Gate px: min(239dp→px, 0.9*visualTargetW) — short card van mo khi du cho.
 */
internal fun statusBarMorphPanelWidthGatePx(
    densityDpi: Float,
    visualTargetWPx: Float,
    gateDp: Float = StatusBarMorphPanelWidthGateDp,
): Float {
    val dpi = densityDpi.coerceAtLeast(1f)
    val gate239 = gateDp * dpi / 160f
    val shortAdapt = visualTargetWPx.coerceAtLeast(1f) * 0.9f
    return minOf(gate239, shortAdapt).coerceAtLeast(1f)
}

/**
 * Latch FIRST width-gate crossing (CardStackView L665-687).
 * [gateStartFraction] = linear animator fraction tai lan cross dau; remap (f-fGate)/(1-fGate).
 * Reset moi run / doi chieu — KHONG Compose State (tranh SideEffect loop).
 */
internal class StatusBarMorphPanelGateLatch {
    /** Da qua gate 1 lan trong run hien tai. */
    var crossed: Boolean = false
        private set
    /** Fraction linear [0,0.99] snapshot luc first cross. */
    var gateStartFraction: Float = 0f
        private set
    /** Huong run: true=close; null=chua init. */
    private var directionClosing: Boolean? = null

    /** Sync huong — close/reverse huy latch cu. */
    fun ensureDirection(closing: Boolean) {
        if (directionClosing != closing) {
            reset()
            directionClosing = closing
        }
    }

    fun reset() {
        crossed = false
        gateStartFraction = 0f
    }

    /**
     * Cap nhat latch neu FIRST cross; tra gateStartFraction khi da latched, else null.
     * [linearFraction] = elapsed/duration (Samsung animatedValue 0..1).
     */
    fun observeCrossing(
        linearFraction: Float,
        measuredWidthPx: Float,
        widthGatePx: Float,
        closing: Boolean,
    ): Float? {
        ensureDirection(closing)
        if (closing) {
            reset()
            directionClosing = true
            return null
        }
        if (!crossed && measuredWidthPx > widthGatePx) {
            crossed = true
            // Samsung: min(floatValue, 0.99) — tranh denom~0.
            gateStartFraction = linearFraction.coerceIn(0f, 0.99f)
        }
        return if (crossed) gateStartFraction else null
    }
}

/**
 * Panel alpha — DOC LAP tint/color.
 * OPEN: Samsung remap (f - fGate) / (1 - fGate); alpha0 dung luc FIRST cross.
 * CLOSE: soft fade 1→0 trong [StatusBarMorphPanelCloseHideMs] (elapsed) — KHONG hard step @.97.
 * Dummy fade-in 200ms doc lap. Shell soft-take cover nen.
 *
 * [gateStartFraction]: null = chua cross; latched value tu [StatusBarMorphPanelGateLatch].
 * [expandedFraction]: giu API; close dung elapsed, khong threshold p.
 */
internal fun statusBarMorphPanelContentAlpha(
    elapsedMs: Float,
    durationMs: Float,
    closing: Boolean,
    measuredWidthPx: Float,
    widthGatePx: Float,
    gateStartFraction: Float? = null,
    @Suppress("UNUSED_PARAMETER") expandedFraction: Float? = null,
): Float {
    val ms = elapsedMs.coerceAtLeast(0f)
    val dur = durationMs.coerceAtLeast(1f)
    if (closing) {
        // Soft early hide: α=1 @close start → 0 trong ~50ms; gradient qua p=.97.
        val hideT =
            (ms / StatusBarMorphPanelCloseHideMs.toFloat()).coerceIn(0f, 1f)
        return (1f - hideT).coerceIn(0f, 1f)
    }
    val fGate = gateStartFraction ?: return 0f
    // Van can width>gate HOAC da latched (short-card / interrupt giu latch).
    if (measuredWidthPx <= widthGatePx && gateStartFraction == null) return 0f
    val f = (ms / dur).coerceIn(0f, 1f)
    val denom = (1f - fGate).coerceAtLeast(0.01f)
    return ((f - fGate) / denom).coerceIn(0f, 1f)
}

/**
 * Compat expandedFraction → panel alpha. [visualWidthPx]/[visualTargetWPx] cho gate adapt.
 * Mac dinh density 450 (pdx237).
 *
 * [gateStartFraction]: null → coi nhu cross ngay @dummy fade (compat test cu / infinite width).
 * Truyen latch.observeCrossing(...) tu Compose de dung Samsung first-cross.
 */
internal fun statusBarMorphCardContentAlpha(
    progress: Float,
    closing: Boolean,
    visualWidthPx: Float = Float.POSITIVE_INFINITY,
    visualTargetWPx: Float = Float.POSITIVE_INFINITY,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
    gateStartFraction: Float? = Float.NaN,
    gateLatched: Boolean = false,
): Float {
    val dur = statusBarMorphDurationMs(closing).toFloat()
    val ms = statusBarMorphElapsedMs(progress, closing)
    val gate =
        statusBarMorphPanelWidthGatePx(
            densityDpi = densityDpi,
            visualTargetWPx =
                if (visualTargetWPx.isFinite()) visualTargetWPx else visualWidthPx,
        )
    val w =
        if (visualWidthPx.isFinite()) visualWidthPx
        else Float.POSITIVE_INFINITY
    // NaN sentinel = auto: latched/infinite → fGate = dummyOpen/dur; else null.
    val fGate: Float? =
        when {
            gateStartFraction != null && !gateStartFraction.isNaN() -> gateStartFraction
            gateLatched || w > gate || !w.isFinite() ->
                (StatusBarMorphDummyOpenFadeMs.toFloat() / dur).coerceIn(0f, 0.99f)
            else -> null
        }
    return statusBarMorphPanelContentAlpha(
        elapsedMs = ms,
        durationMs = dur,
        closing = closing,
        measuredWidthPx = w,
        widthGatePx = gate,
        gateStartFraction = fGate,
        expandedFraction = progress,
    )
}

/** True khi short-card gate < 239dp px (adapt). */
internal fun statusBarMorphPanelGateIsShortCardAdapted(
    densityDpi: Float,
    visualTargetWPx: Float,
): Boolean {
    val dpi = densityDpi.coerceAtLeast(1f)
    val gate239 = StatusBarMorphPanelWidthGateDp * dpi / 160f
    return statusBarMorphPanelWidthGatePx(dpi, visualTargetWPx) < gate239 - 0.5f
}

// =============================================================================
// TRACK 4 — Color (ARGB lerp progress) + shell glass / blur factor
// =============================================================================

/**
 * Color t∈[0,1] DOC LAP geom.pCorner / cardContentAlpha.
 * t = trong [pillAccent→CardBg] (caller lerp 1 lan).
 * OPEN: Decelerate 500 → t 0→1. CLOSE: collapseColor 600 → t 1→0
 * (1 - eased) de giu cung truc lerp; KHONG 650 OneUI7.
 */
internal fun statusBarMorphColorProgress(elapsedMs: Float, closing: Boolean): Float {
    val dur = statusBarMorphDurationMs(closing).toFloat().coerceAtLeast(1f)
    val t = (elapsedMs.coerceAtLeast(0f) / dur).coerceIn(0f, 1f)
    return if (closing) {
        (1f - StatusBarMorphColorCloseEasing.transform(t)).coerceIn(0f, 1f)
    } else {
        statusBarMorphDecelerateFraction(t)
    }
}

/** Compat expandedFraction → color progress. */
internal fun statusBarMorphShellTintProgress(
    expandedFraction: Float,
    closing: Boolean = false,
): Float {
    val ms = statusBarMorphElapsedMs(expandedFraction, closing)
    return statusBarMorphColorProgress(ms, closing)
}

/**
 * Track 4 tint ARGB: lerp full channels (ke ca alpha) — KHONG ep AlphaGlass len endpoint.
 *
 * [pillSourceTint] = live pill material (StatusBarPillVisual: accent×AlphaGlass).
 * [panelEndpointTint] = AxSystemFrost.tintColor / fallbackColor EXACT resource ARGB
 * (islandBlur tint=null → surfaceBrightTint, khong copy alpha 0.72).
 * p1 → endpoint alpha/resource giong native; blur-off dung fallback.
 */
internal fun statusBarMorphShellTintArgb(
    pillSourceTint: Color,
    panelEndpointTint: Color,
    tintProgress: Float,
): Color = lerp(pillSourceTint, panelEndpointTint, tintProgress.coerceIn(0f, 1f))

/**
 * Fallback fill (blur-off / axBlur fallbackColor): pillAccent → frost fallback EXACT.
 * Khong ep AlphaGlass.
 */
internal fun statusBarMorphShellFallbackArgb(
    pillAccent: Color,
    panelEndpointFallback: Color,
    tintProgress: Float,
): Color = lerp(pillAccent, panelEndpointFallback, tintProgress.coerceIn(0f, 1f))

/**
 * Shell glass hold — DOC LAP color tint.
 * Soft handoff [StatusBarMorphShellHandoffStart, 1]: open 1→0 / close 0→1 muot.
 * Color tint (track 4) doc lap — 1 surface pillAccent→CardBg lien tuc.
 */
internal fun statusBarMorphShellGlassAlpha(progress: Float, closing: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = StatusBarMorphShellHandoffStart
    val span = (1f - start).coerceAtLeast(1e-6f)
    return if (closing) {
        // Soft-take ease-out: len nhanh khoi 0 (cover som), p=1 → 0, p<=start → 1.
        if (p <= start) {
            1f
        } else {
            val u = ((1f - p) / span).coerceIn(0f, 1f)
            // 1-(1-u)^3 — nhanh dau, muot, khong step.
            val inv = 1f - u
            (1f - inv * inv * inv).coerceIn(0f, 1f)
        }
    } else {
        // Soft-release ease-in: giu 1 lau, roi ha muot → 0 @p1.
        if (p <= start) {
            1f
        } else {
            val u = ((p - start) / span).coerceIn(0f, 1f)
            // 1-u^3 — ha cham luc moi roi nhanh gan p1.
            (1f - u * u * u).coerceIn(0f, 1f)
        }
    }
}

/**
 * Native card blur factor (LocalBlurAlpha = expandedBlur × factor).
 * Safe mode coverage-driven: shell >= minCover → factor 0 (1-owner).
 * Shell thieu → bat native blur toi thieu de SRC_OVER cover >= minCover.
 * Media/remote heterogeneous: co the double frost ngan trong soft window — tranh lo trong suot.
 */
internal fun statusBarMorphCardSurfaceBlurFactor(
    cardContentAlpha: Float,
    shellGlassAlpha: Float,
    minCoverage: Float = StatusBarMorphMinBackplateCoverage,
): Float {
    if (cardContentAlpha <= 0.01f) return 0f
    val shell = shellGlassAlpha.coerceIn(0f, 1f)
    val parent = cardContentAlpha.coerceIn(0f, 1f)
    val minC = minCoverage.coerceIn(0.5f, 1f)
    // Shell tat → full native blur (settle p1 / close start shell0).
    if (shell <= 1e-4f) return 1f
    // Shell tu cover → tat native blur (khong double).
    if (shell >= minC) return 0f
    // 1-(1-shell)*(1-parent*blur) >= minC
    // parent*blur >= 1 - (1-minC)/(1-shell)
    val gap = (1f - shell).coerceAtLeast(1e-6f)
    val needNative = (1f - (1f - minC) / gap).coerceIn(0f, 1f)
    if (needNative <= 1e-6f) return 0f
    return (needNative / parent).coerceIn(0f, 1f)
}

/**
 * Legacy soft-handoff: yield native khi shell >= minOwns (.95).
 * Task-034 production: dung [statusBarMorphYieldNativeGlassToSharedOwner] (owner-present).
 * Giu API cho test coverage / reverse-compat.
 */
internal fun statusBarMorphYieldNativeGlassToShell(
    shellGlassAlpha: Float,
    minShellOwns: Float = StatusBarMorphShellOwnsBlurAbove,
): Boolean {
    val shell = shellGlassAlpha.coerceIn(0f, 1f)
    val owns = minShellOwns.coerceIn(0.5f, 1f)
    return shell >= owns
}

/**
 * Task-034: yield native TOP card khi shared glass owner present.
 * KHONG threshold .95 / shell alpha — 1 surface p0→p1 rest→close→handback.
 */
internal fun statusBarMorphYieldNativeGlassToSharedOwner(
    sharedGlassOwnerPresent: Boolean,
): Boolean = sharedGlassOwnerPresent

/**
 * Persistent shared glass α: 1 khi owner present (× handback chrome).
 * KHONG fade 1→0 @p1 (tranh destroy BackgroundBlurDrawable / 2-blur handoff).
 */
internal fun statusBarMorphPersistentSharedGlassAlpha(
    sharedGlassOwnerPresent: Boolean,
    collapseChromeFactor: Float = 1f,
): Float =
    if (sharedGlassOwnerPresent) {
        collapseChromeFactor.coerceIn(0f, 1f)
    } else {
        0f
    }

/**
 * Snapshot compositing: shell α + yield boolean + effective SRC_OVER coverage.
 * Test p=.97..1 open/close/reverse — khong proxy-only.
 */
internal data class StatusBarMorphGlassCompositing(
    val shellGlass: Float,
    val yieldNative: Boolean,
    val cardBlurFactor: Float,
    val effectiveCoverage: Float,
)

internal fun statusBarMorphGlassCompositingAt(
    shellGlassAlpha: Float,
    panelContentAlpha: Float,
    expandedBlurAlpha: Float = 1f,
    minCoverage: Float = StatusBarMorphMinBackplateCoverage,
): StatusBarMorphGlassCompositing {
    val shell = shellGlassAlpha.coerceIn(0f, 1f)
    val panel = panelContentAlpha.coerceIn(0f, 1f)
    val yield = statusBarMorphYieldNativeGlassToShell(shell, minCoverage)
    // Yield → FG-only (factor 0); else complementary / full native.
    val factor =
        if (yield) {
            0f
        } else {
            statusBarMorphCardSurfaceBlurFactor(panel, shell, minCoverage)
        }
    val coverage =
        statusBarMorphBackplateCoverage(
            shellGlass = shell,
            panelContentAlpha = panel,
            cardBlurFactor = factor,
            expandedBlurAlpha = expandedBlurAlpha,
        )
    return StatusBarMorphGlassCompositing(
        shellGlass = shell,
        yieldNative = yield,
        cardBlurFactor = factor,
        effectiveCoverage = coverage,
    )
}

/**
 * Proxy backplate coverage (SRC_OVER):
 * shellGlass + parentContentα × expandedBlur × cardBlurFactor.
 * Khop ve: shell islandBlur(α=paintMorphGlass) + content Box(α=panel) × LocalBlurAlpha.
 */
internal fun statusBarMorphBackplateCoverage(
    shellGlass: Float,
    panelContentAlpha: Float,
    cardBlurFactor: Float,
    expandedBlurAlpha: Float = 1f,
): Float {
    val shell = shellGlass.coerceIn(0f, 1f)
    val nativeBg =
        (panelContentAlpha.coerceIn(0f, 1f) *
                expandedBlurAlpha.coerceIn(0f, 1f) *
                cardBlurFactor.coerceIn(0f, 1f))
            .coerceIn(0f, 1f)
    return (1f - (1f - shell) * (1f - nativeBg)).coerceIn(0f, 1f)
}

/** Shell toi thieu de SRC_OVER(shell, panel×blur1) >= minCover. */
internal fun statusBarMorphMinShellForCoverage(
    panelContentAlpha: Float,
    minCoverage: Float = StatusBarMorphMinBackplateCoverage,
): Float {
    val panel = panelContentAlpha.coerceIn(0f, 1f)
    val minC = minCoverage.coerceIn(0.5f, 1f)
    if (panel >= minC) return 0f
    val gap = (1f - panel).coerceAtLeast(1e-6f)
    return (1f - (1f - minC) / gap).coerceIn(0f, 1f)
}

// =============================================================================
// Snapshot 4 track (test / debug table)
// =============================================================================

internal data class StatusBarMorphTracksSnapshot(
    val geomFraction: Float,
    val dummyScale: Float,
    val dummyAlpha: Float,
    val panelAlpha: Float,
    val colorT: Float,
    val shellGlass: Float,
)

/**
 * Visual snapshot de freeze khi doi chieu (DEFECT #3).
 * Geom PathInterpolator van do Compose morphProgress — khong nam trong snapshot nay.
 */
internal data class StatusBarMorphVisualSnapshot(
    val dummyScale: Float,
    val dummyAlpha: Float,
    val panelAlpha: Float,
    val colorT: Float,
    val shellGlass: Float,
)

/** Elapsed segment khi reverse: Compose tween full dur tu fractionAtStart → dich. */
internal fun statusBarMorphSegmentElapsedMs(
    expandedFraction: Float,
    closing: Boolean,
    fractionAtSegmentStart: Float,
): Float {
    val p = expandedFraction.coerceIn(0f, 1f)
    val start = fractionAtSegmentStart.coerceIn(0f, 1f)
    val dur = statusBarMorphDurationMs(closing).toFloat()
    return if (closing) {
        val span = start.coerceAtLeast(1e-6f)
        (((span - p) / span) * dur).coerceAtLeast(0f)
    } else {
        val span = (1f - start).coerceAtLeast(1e-6f)
        (((p - start) / span) * dur).coerceAtLeast(0f)
    }
}

/**
 * Continuity owner — freeze snapshot luc doi chieu; segment elapsed tu 0;
 * noi suy tu gia tri hien tai → target moi (Sony safety, khong claim Samsung interrupt).
 *
 * Giu trong remember{} Compose — mutate sync, KHONG MutableState (recomposition-safe).
 */
internal class StatusBarMorphContinuityOwner {
    private var directionClosing: Boolean? = null
    private var fractionAtSegmentStart: Float = 0f
    private var from: StatusBarMorphVisualSnapshot? = null
    private var last: StatusBarMorphVisualSnapshot =
        StatusBarMorphVisualSnapshot(
            dummyScale = 1f,
            dummyAlpha = 1f,
            panelAlpha = 0f,
            colorT = 0f,
            shellGlass = 1f,
        )
    private val gateLatch = StatusBarMorphPanelGateLatch()
    private val exclusiveHandoff = StatusBarMorphExclusiveContentHandoff()

    fun gateLatch(): StatusBarMorphPanelGateLatch = gateLatch

    /** Huy latch / segment (collapse settle / owner moi). */
    fun reset() {
        directionClosing = null
        fractionAtSegmentStart = 0f
        from = null
        gateLatch.reset()
        exclusiveHandoff.reset()
        last =
            StatusBarMorphVisualSnapshot(
                dummyScale = 1f,
                dummyAlpha = 1f,
                panelAlpha = 0f,
                colorT = 0f,
                shellGlass = 1f,
            )
    }

    /**
     * Danh gia 4 track content (dummy/panel/color/shell) theo expandedFraction linear.
     * Geom KHONG qua day — caller van dung morphProgress eased.
     */
    fun evaluate(
        expandedFraction: Float,
        closing: Boolean,
        measuredWidthPx: Float,
        widthGatePx: Float,
        visualTargetWPx: Float = measuredWidthPx,
        densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
    ): StatusBarMorphVisualSnapshot {
        val p = expandedFraction.coerceIn(0f, 1f)
        val dirChanged = directionClosing != null && directionClosing != closing
        if (directionClosing == null) {
            // Run moi: snapshot endpoint dung huong (uninterrupted).
            directionClosing = closing
            fractionAtSegmentStart = if (closing) 1f else 0f
            from = null
            gateLatch.ensureDirection(closing)
            last =
                if (closing) {
                    StatusBarMorphVisualSnapshot(
                        dummyScale = StatusBarMorphDummyScalePeak,
                        dummyAlpha = 0f,
                        panelAlpha = 1f,
                        colorT = 1f,
                        shellGlass = 0f,
                    )
                } else {
                    StatusBarMorphVisualSnapshot(
                        dummyScale = 1f,
                        dummyAlpha = 1f,
                        panelAlpha = 0f,
                        colorT = 0f,
                        shellGlass = 1f,
                    )
                }
        } else if (dirChanged) {
            // Mid-flight reverse: freeze last visual; segment moi elapsed=0.
            // Continuity handoff: giu alpha snapshot — KHONG reset→evaluate blank.
            from = last
            fractionAtSegmentStart = p
            directionClosing = closing
            gateLatch.ensureDirection(closing)
            exclusiveHandoff.captureReverseFrom(last.dummyAlpha, last.panelAlpha)
        }

        val segMs = statusBarMorphSegmentElapsedMs(p, closing, fractionAtSegmentStart)
        val dur = statusBarMorphDurationMs(closing).toFloat()
        val snap = from
        val out =
            if (snap == null) {
                // Uninterrupted: track chuan theo segment elapsed (= absolute khi start 0/1).
                uninterruptedVisual(
                    segmentElapsedMs = segMs,
                    closing = closing,
                    expandedFraction = p,
                    measuredWidthPx = measuredWidthPx,
                    widthGatePx = widthGatePx,
                    visualTargetWPx = visualTargetWPx,
                    densityDpi = densityDpi,
                )
            } else {
                continuousFromSnapshot(
                    snapshot = snap,
                    segmentElapsedMs = segMs,
                    closing = closing,
                    expandedFraction = p,
                    measuredWidthPx = measuredWidthPx,
                    widthGatePx = widthGatePx,
                )
            }
        last = out
        // Het segment: co the clear from de frame sau dung uninterrupted — giu from
        // den het flight tranh snap ve natural mid-values.
        return out
    }

    private fun uninterruptedVisual(
        segmentElapsedMs: Float,
        closing: Boolean,
        expandedFraction: Float,
        measuredWidthPx: Float,
        widthGatePx: Float,
        visualTargetWPx: Float,
        densityDpi: Float,
    ): StatusBarMorphVisualSnapshot {
        val dur = statusBarMorphDurationMs(closing).toFloat()
        val dummy = statusBarMorphDummyTrack(segmentElapsedMs, closing)
        val linearF = (segmentElapsedMs / dur).coerceIn(0f, 1f)
        val fGate =
            gateLatch.observeCrossing(
                linearFraction = linearF,
                measuredWidthPx = measuredWidthPx,
                widthGatePx = widthGatePx,
                closing = closing,
            )
        // Exclusive pill↔panel (gap glass); spring scale van doc lap.
        val exclusive =
            statusBarMorphExclusiveContentAlphas(
                elapsedMs = segmentElapsedMs,
                durationMs = dur,
                closing = closing,
                measuredWidthPx = measuredWidthPx,
                widthGatePx = widthGatePx,
                gateStartFraction = fGate,
            )
        return StatusBarMorphVisualSnapshot(
            dummyScale = dummy.scale,
            dummyAlpha = exclusive.pillAlpha,
            panelAlpha = exclusive.panelAlpha,
            colorT = statusBarMorphColorProgress(segmentElapsedMs, closing),
            shellGlass = statusBarMorphShellGlassAlpha(expandedFraction, closing),
        )
    }

    private fun continuousFromSnapshot(
        snapshot: StatusBarMorphVisualSnapshot,
        segmentElapsedMs: Float,
        closing: Boolean,
        expandedFraction: Float,
        measuredWidthPx: Float,
        widthGatePx: Float,
    ): StatusBarMorphVisualSnapshot {
        val dur = statusBarMorphDurationMs(closing).toFloat().coerceAtLeast(1f)
        val t = (segmentElapsedMs / dur).coerceIn(0f, 1f)
        return if (closing) {
            val dummyAlphaLerped =
                lerpFloat(
                    snapshot.dummyAlpha,
                    1f,
                    (segmentElapsedMs / StatusBarMorphDummyCloseFadeMs.toFloat())
                        .coerceIn(0f, 1f),
                )
            val dummyScale =
                statusBarMorphSpringScaleAt(
                    elapsedMs = segmentElapsedMs,
                    from = snapshot.dummyScale,
                    to = 1f,
                )
            // Panel: lien tuc tu snapshot → 0 (hide ~50ms). KHONG maxOf(...,1) @p>.97.
            val panelHide =
                (segmentElapsedMs / StatusBarMorphPanelCloseHideMs.toFloat()).coerceIn(0f, 1f)
            val panelLerped = lerpFloat(snapshot.panelAlpha, 0f, panelHide)
            val exclusive =
                exclusiveHandoff.evaluate(
                    elapsedMs = segmentElapsedMs,
                    durationMs = dur,
                    closing = true,
                    naturalPill = dummyAlphaLerped,
                    naturalPanel = panelLerped,
                )
            val shellNatural = statusBarMorphShellGlassAlpha(expandedFraction, closing = true)
            // Shell: lerp snapshot→natural; floor coverage neu panel dang ha (tranh lo ~18ms).
            val shellLerped = lerpFloat(snapshot.shellGlass, shellNatural, t)
            val shell =
                maxOf(shellLerped, statusBarMorphMinShellForCoverage(exclusive.panelAlpha))
                    .coerceIn(0f, 1f)
            // Color: snapshot → 0 theo collapseColor (monotone).
            val colorT =
                snapshot.colorT *
                    (1f - StatusBarMorphColorCloseEasing.transform(t).coerceIn(0f, 1f))
            StatusBarMorphVisualSnapshot(
                dummyScale = dummyScale,
                dummyAlpha = exclusive.pillAlpha,
                panelAlpha = exclusive.panelAlpha,
                colorT = colorT.coerceIn(0f, 1f),
                shellGlass = shell,
            )
        } else {
            val dummyAlpha =
                lerpFloat(
                    snapshot.dummyAlpha,
                    0f,
                    (segmentElapsedMs / StatusBarMorphDummyOpenFadeMs.toFloat())
                        .coerceIn(0f, 1f),
                )
            val dummyScale =
                statusBarMorphSpringScaleAt(
                    elapsedMs = segmentElapsedMs,
                    from = snapshot.dummyScale,
                    to = StatusBarMorphDummyScalePeak,
                )
            val linearF = (segmentElapsedMs / dur).coerceIn(0f, 1f)
            val fGate =
                gateLatch.observeCrossing(
                    linearFraction = linearF,
                    measuredWidthPx = measuredWidthPx,
                    widthGatePx = widthGatePx,
                    closing = false,
                )
            val panelNatural =
                statusBarMorphPanelContentAlpha(
                    elapsedMs = segmentElapsedMs,
                    durationMs = dur,
                    closing = false,
                    measuredWidthPx = measuredWidthPx,
                    widthGatePx = widthGatePx,
                    gateStartFraction = fGate,
                    expandedFraction = expandedFraction,
                )
            // Tu snapshot.panel → natural; alpha0 neu natural 0 (chua cross).
            val panelLerped =
                if (fGate == null && panelNatural <= 0f) {
                    lerpFloat(snapshot.panelAlpha, 0f, t)
                } else {
                    lerpFloat(snapshot.panelAlpha, panelNatural, t)
                }
            // Exclusive: khong overlap mid-flight reverse.
            val exclusive =
                exclusiveHandoff.evaluate(
                    elapsedMs = segmentElapsedMs,
                    durationMs = dur,
                    closing = false,
                    naturalPill = dummyAlpha,
                    naturalPanel = panelLerped,
                )
            // Color OPEN: snapshot + (1-snapshot)*decelerate(t) — monotone → 1 (khong bounce).
            val colorT =
                snapshot.colorT +
                    (1f - snapshot.colorT) * statusBarMorphDecelerateFraction(t)
            val shellNatural = statusBarMorphShellGlassAlpha(expandedFraction, closing = false)
            val shellLerped =
                lerpFloat(snapshot.shellGlass, shellNatural, t).coerceIn(0f, 1f)
            val shell =
                maxOf(shellLerped, statusBarMorphMinShellForCoverage(exclusive.panelAlpha))
                    .coerceIn(0f, 1f)
            StatusBarMorphVisualSnapshot(
                dummyScale = dummyScale,
                dummyAlpha = exclusive.pillAlpha,
                panelAlpha = exclusive.panelAlpha,
                colorT = colorT.coerceIn(0f, 1f),
                shellGlass = shell,
            )
        }
    }
}

private fun lerpFloat(a: Float, b: Float, t: Float): Float {
    val u = t.coerceIn(0f, 1f)
    return a + (b - a) * u
}

/**
 * Sample 4 track tai moc elapsed ms (tu luc bat dau open/close).
 * expandedFraction = elapsed/dur (open) hoac 1-elapsed/dur (close).
 * Panel open: [visualWidthPx] mac dinh >> gate — first-cross @ dummy fade (compat).
 */
internal fun statusBarMorphTracksAtMs(
    elapsedMs: Float,
    closing: Boolean,
    visualWidthPx: Float = 2000f,
    visualTargetWPx: Float = 2000f,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): StatusBarMorphTracksSnapshot {
    val dur = statusBarMorphDurationMs(closing).toFloat()
    val motionT = (elapsedMs / dur).coerceIn(0f, 1f)
    val expandedFraction = if (closing) 1f - motionT else motionT
    val geom = StatusBarMorphGeometryEasing.transform(motionT).let { eased ->
        // Geom progress theo huong motion; expanded amount = eased open / 1-eased close.
        if (closing) 1f - eased else eased
    }
    val dummy = statusBarMorphDummyTrack(elapsedMs, closing)
    val gate =
        statusBarMorphPanelWidthGatePx(densityDpi, visualTargetWPx)
    // Compat infinite-wide: fGate = dummyOpen/dur; exclusive gap sau pill zero.
    val fGate =
        if (visualWidthPx > gate) {
            (StatusBarMorphDummyOpenFadeMs.toFloat() / dur).coerceIn(0f, 0.99f)
        } else {
            null
        }
    val exclusive =
        statusBarMorphExclusiveContentAlphas(
            elapsedMs = elapsedMs,
            durationMs = dur,
            closing = closing,
            measuredWidthPx = visualWidthPx,
            widthGatePx = gate,
            gateStartFraction = fGate,
        )
    val color = statusBarMorphColorProgress(elapsedMs, closing)
    val shell = statusBarMorphShellGlassAlpha(expandedFraction, closing)
    return StatusBarMorphTracksSnapshot(
        geomFraction = geom,
        dummyScale = dummy.scale,
        dummyAlpha = exclusive.pillAlpha,
        panelAlpha = exclusive.panelAlpha,
        colorT = color,
        shellGlass = shell,
    )
}

// Legacy handoff windows — KHONG dung track moi; giu hang so tranh break import test cu.
@Deprecated("F741N: dummy/panel theo elapsed ms, khong cua so 0.35/0.55")
internal const val StatusBarMorphContentHandoffStart = 0.35f

@Deprecated("F741N: dummy/panel theo elapsed ms")
internal const val StatusBarMorphContentHandoffEnd = 0.55f

@Deprecated("F741N close dummy som 200ms — khong 0.12")
internal const val StatusBarMorphCloseHandoffStart = 0.12f

@Deprecated("F741N close panel early hide — khong 0.28")
internal const val StatusBarMorphCloseHandoffEnd = 0.28f
