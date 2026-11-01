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

/**
 * Geometry morph pill→panel keyguard (pure, khong Compose).
 *
 * Mot host dung chung Event (Clock/Media) + Battery: ONE spring [p] → sx/sy +
 * offset top-left (transformOrigin 0,0) + requiredSize(target). Chi leaf content khac.
 *
 * p ≥ 0: sx/sy = lerp(pill/target, 1, pRaw) — trung Event raw (ke ca overshoot p>1).
 * p < 0: envelope CHUAN HOA theo pill (khong lerp am theo targetH — tranh squash H600):
 *   visualW/H = pillW/H * breathW/H; sx/sy = visual/target.
 *   breath doc lap panel H; amplitude >> breath cu ~1.15%.
 * Corner: pCorner ∈ [0,1]. Khong blanket clamp 0..1 / BounceGain / raw p<0.
 *
 * Offset neo day (shared): top = B − targetH·sy, left blend tam pill→man.
 * Voi p∈[0,1] + visualW=lerp(pillW,targetW,p): trung Event lerp(originLeft,targetLeft,p).
 * p>1: overshoot 3 canh (trai/phai/tren), bottom = B.
 *
 * Compromise (ref §5B + Clock raw ~10–15%H / 3–7%W @ p=-0.046, audit 3–5%):
 *   @ p=-0.046 → ~11.5%H / ~5.1%W; floor 15%H / 7%W — doc lap H∈{152…600}.
 */
internal data class KeyguardMorphGeom(
    /** Scale X sau lerp (+ breathW khi p<0). */
    val sx: Float,
    /** Scale Y sau lerp (+ breathH khi p<0). */
    val sy: Float,
    /**
     * Progress offset ngang (blend pill→expanded). p≥0 = raw; p<0 = 0 (neo pill).
     * Truc Y luon neo day qua [morphOffsetTopLeft] — khong dung pOffset cho Y.
     */
    val pOffset: Float,
    /** Progress corner (luon trong [0,1]). */
    val pCorner: Float,
)

/** San scale tranh chia 0 / graphicsLayer vo hieu. */
internal const val MorphScaleFloor = 0.01f

/**
 * Breath undershoot chuan hoa theo pill (anisotropic).
 *
 * Truoc (28ce8de): gain=0.25 floor=0.96 → ~1.15%@p=-0.046 (yeu vs Clock raw).
 * Sau: gainH/W map ~11.5%H / ~5%W @ p=-0.046; floor 15%H / 7%W.
 * Khong restore lerp(pill,target,p) am — Battery H600 se squash.
 */
internal const val MorphBreathGainW = 1.1f
internal const val MorphBreathGainH = 2.5f
internal const val MorphBreathFloorW = 0.93f
internal const val MorphBreathFloorH = 0.85f

private fun morphLerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Tinh sx/sy/pOffset/pCorner — dimension-aware, dung chung moi morph host.
 *
 * @param pRaw spring raw (co the <0 / >1)
 */
internal fun computeKeyguardMorphGeom(
    pRaw: Float,
    pillWPx: Float,
    pillHPx: Float,
    targetWPx: Float,
    targetHPx: Float,
): KeyguardMorphGeom {
    val safePillW = pillWPx.coerceAtLeast(1f)
    val safePillH = pillHPx.coerceAtLeast(1f)
    val safeTargetW = targetWPx.coerceAtLeast(1f)
    val safeTargetH = targetHPx.coerceAtLeast(1f)
    if (pRaw < 0f) {
        // Envelope pill-relative: visual = pill * breath; doc lap targetH.
        val breathW = (1f + pRaw * MorphBreathGainW).coerceIn(MorphBreathFloorW, 1f)
        val breathH = (1f + pRaw * MorphBreathGainH).coerceIn(MorphBreathFloorH, 1f)
        return KeyguardMorphGeom(
            sx = ((safePillW / safeTargetW) * breathW).coerceAtLeast(MorphScaleFloor),
            sy = ((safePillH / safeTargetH) * breathH).coerceAtLeast(MorphScaleFloor),
            pOffset = 0f,
            pCorner = 0f,
        )
    }
    // p≥0: raw lerp (overshoot p>1 giu nguyen — trung ban accepted).
    val sx =
        morphLerp(safePillW / safeTargetW, 1f, pRaw).coerceAtLeast(MorphScaleFloor)
    val sy =
        morphLerp(safePillH / safeTargetH, 1f, pRaw).coerceAtLeast(MorphScaleFloor)
    return KeyguardMorphGeom(
        sx = sx,
        sy = sy,
        pOffset = pRaw,
        pCorner = pRaw.coerceIn(0f, 1f),
    )
}

/**
 * Offset top-left host morph: neo day (bottom==B moi p) + tam ngang.
 *
 * pOffset∈[0,1] blend origin→expanded; pOffset≥1 giu tam man (overshoot 3 canh).
 * visualW/H = target·sx/sy — khop Event lerp top-left khi p∈[0,1] + pill dong tam.
 */
internal fun morphOffsetTopLeft(
    sx: Float,
    sy: Float,
    pillWPx: Float,
    targetWPx: Float,
    targetHPx: Float,
    pOffset: Float,
    originLeftPx: Float,
    anchorBottomPx: Float,
    maxWidthPx: Float,
): Pair<Float, Float> {
    val visualW = targetWPx * sx
    val visualH = targetHPx * sy
    val top = anchorBottomPx - visualH
    val left0 = originLeftPx + (pillWPx - visualW) * 0.5f
    val left1 = (maxWidthPx - visualW) * 0.5f
    val left =
        when {
            pOffset >= 1f -> left1
            pOffset <= 0f -> left0
            else -> morphLerp(left0, left1, pOffset)
        }
    return left to top
}

/**
 * Local corner (pre host scale) — bu anisotropic sx≠sy.
 *
 * Truoc (sai): local = visualCorner/sy → sau scale
 *   visualRy = visualCorner, visualRx = visualCorner*(sx/sy)  (ellipse, H lon → rx/ry≈2–3).
 *
 * Sau: localRx = visualR/sx, localRy = visualR/sy → visual rx = ry = visualR.
 * visualR = min(visualCorner, halfVisualH) — capsule theo pill physical (p≈0: halfVisualH=pillH/2).
 * p=1 (sx=sy=1): local = visualCorner — expanded card khong doi.
 *
 * Squash tap (squishAnimation) doc lap H panel; khong dung BounceGain/q.
 */
internal data class MorphLocalCornerPx(
    val localRxPx: Float,
    val localRyPx: Float,
)

internal fun morphLocalCornerRadii(
    visualCornerPx: Float,
    sx: Float,
    sy: Float,
    targetHPx: Float,
    targetWPx: Float = Float.POSITIVE_INFINITY,
): MorphLocalCornerPx {
    val safeSx = sx.coerceAtLeast(MorphScaleFloor)
    val safeSy = sy.coerceAtLeast(MorphScaleFloor)
    val visualH = (targetHPx * safeSy).coerceAtLeast(0f)
    val halfVisualH = visualH * 0.5f
    val visualR =
        minOf(visualCornerPx.coerceAtLeast(0f), halfVisualH).coerceAtLeast(0f)
    // Cap local trong nua canh host — tranh Path/blur vo le; doc lap bien do H.
    val halfLocalW = (targetWPx * 0.5f).coerceAtLeast(visualR)
    val halfLocalH = (targetHPx * 0.5f).coerceAtLeast(visualR)
    return MorphLocalCornerPx(
        localRxPx = (visualR / safeSx).coerceIn(0f, halfLocalW),
        localRyPx = (visualR / safeSy).coerceIn(0f, halfLocalH),
    )
}

// --- Expanded target-H ownership (Clock Reset→Battery in-host, khong collapse) ---

/** Owner on dinh cho H khoa — id event, KHONG timer remaining / % pin. */
fun keyguardExpandedContentOwnerKey(
    batterySelected: Boolean,
    eventId: String?,
): String? =
    when {
        batterySelected -> "battery"
        !eventId.isNullOrEmpty() -> "event:$eventId"
        else -> null
    }

/** H + owner dang khoa cho mot luot expanded (clear khi collapse settle). */
data class LockedTargetHeight(
    val heightPx: Int = 0,
    val owner: String? = null,
)

/**
 * Resolve H khoa theo content owner.
 * - Owner doi / chua khoa → retarget = measuredH (invalidate H cu).
 * - Cung owner da khoa → giu (khong max(old,new), khong follow tick).
 */
fun resolveLockedTargetHeight(
    current: LockedTargetHeight,
    owner: String,
    measuredHPx: Int,
): LockedTargetHeight {
    if (measuredHPx <= 0) return current
    return if (current.owner != owner || current.heightPx <= 0) {
        LockedTargetHeight(heightPx = measuredHPx, owner = owner)
    } else {
        current
    }
}

/** Chi dung H khoa khi dung owner — owner doi → 0 (fallback remembered/default). */
fun effectiveLockedTargetHeightPx(
    locked: LockedTargetHeight,
    owner: String?,
): Int =
    if (owner != null && locked.owner == owner && locked.heightPx > 0) {
        locked.heightPx
    } else {
        0
    }
