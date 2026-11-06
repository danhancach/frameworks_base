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

import kotlin.math.roundToInt

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

// --- STATUS BAR height lock (tach keyguard: khong bounce pillH luc owner switch) ---

/**
 * STATUS BAR — H dich morph khi dang/da expand.
 * - Dung owner + da khoa → H khoa.
 * - Owner doi: uu tien [cachedOwnerNaturalHPx] (do luc the do la under/top) —
 *   KHONG bridge lastExpanded cua owner cu (tranh top nho bi H under cao).
 * - Chua cache owner moi → GIU [lastExpandedHPx] (khong bounce pillH mid-switch).
 * - Chua tung co envelope → 0 (caller moi fallback pillH: chi first expand).
 */
fun statusBarMorphTargetHeightPx(
    locked: LockedTargetHeight,
    owner: String?,
    lastExpandedHPx: Int,
    cachedOwnerNaturalHPx: Int = 0,
): Int {
    val matched = effectiveLockedTargetHeightPx(locked, owner)
    if (matched > 0) return matched
    if (cachedOwnerNaturalHPx > 0) return cachedOwnerNaturalHPx
    if (lastExpandedHPx > 0) return lastExpandedHPx
    return 0
}

/**
 * STATUS BAR — cap nhat lock tu natural measure (wrapContentHeight unbounded).
 * - Ignore measured <=0 / <= pillH (transient wrap / feedback).
 * - Owner doi: retarget ngay sang measured moi.
 * - Cung owner: follow natural H (RemoteViews/media/timer/stack doi H) — khong khoa mai.
 * - measured == current → no-op (tranh recompose vong blur/graphicsLayer).
 */
fun resolveStatusBarLockedTargetHeight(
    current: LockedTargetHeight,
    owner: String,
    measuredHPx: Int,
    pillHPx: Int,
): LockedTargetHeight {
    if (measuredHPx <= 0 || measuredHPx <= pillHPx) return current
    if (current.owner == owner && current.heightPx == measuredHPx) return current
    return LockedTargetHeight(heightPx = measuredHPx, owner = owner)
}

// --- Stack expanded: 4 loai H (task-031) — KHONG gop lam mot ---

/**
 * Bon loai chieu cao stack expanded STATUS BAR.
 *
 * 1. [cardNaturalHPx] — child card measured theo event.id (full, truoc draw-clip peek)
 * 2. [paintedTopHPx] — shared glass / painted NEW TOP only
 * 3. [stackHostEnvelopeHPx] — Box.wrapContentHeight max(layers)+pads
 * 4. [touchInsetsEnvelopeHPx] — touch / insets host (co the >= painted)
 */
data class StatusBarStackHeightKinds(
    val cardNaturalHPx: Int = 0,
    val paintedTopHPx: Int = 0,
    val stackHostEnvelopeHPx: Int = 0,
    val touchInsetsEnvelopeHPx: Int = 0,
)

/**
 * Host-lock H tu card natural + pads stack (SpaceXxs + EXPANDED_BOTTOM_PAD).
 * Don vi khop lockNaturalSize (envelope co pads).
 */
fun statusBarStackHostHeightFromCardNaturalPx(
    cardNaturalHPx: Int,
    topPadPx: Int,
    bottomPadPx: Int,
): Int {
    if (cardNaturalHPx <= 0) return 0
    return cardNaturalHPx + topPadPx.coerceAtLeast(0) + bottomPadPx.coerceAtLeast(0)
}

/**
 * Chon H khoa natural cho TOP khi stack wrapContent = max(top, under)+pads.
 *
 * - Co [topCardNaturalHPx] > pill → top+pads (KHONG dung under/max envelope).
 * - Chua do top (first expand) → fallback [stackHostEnvelopeHPx].
 * Do under natural chi de peek/reveal — cam ap min-H under len top.
 */
fun statusBarLockHeightPreferTopCardNaturalPx(
    topCardNaturalHPx: Int,
    stackHostEnvelopeHPx: Int,
    topPadPx: Int,
    bottomPadPx: Int,
    pillHPx: Int,
): Int {
    val topHost =
        statusBarStackHostHeightFromCardNaturalPx(topCardNaturalHPx, topPadPx, bottomPadPx)
    if (topHost > pillHPx) return topHost
    if (stackHostEnvelopeHPx > pillHPx) return stackHostEnvelopeHPx
    return 0
}

/**
 * Rest swap (expanded at-rest / owner doi): follow live lock H.
 * Frozen morph run: giu frozen — uy quyen [statusBarMorphRunEndpointPx].
 */
fun statusBarExpandedRestOrFrozenHeightPx(
    restLiveLockHPx: Int,
    frozenEndpointHPx: Int,
    pillHPx: Int,
    morphTransitionRunning: Boolean,
    morphExpandedAtRest: Boolean,
): Int =
    statusBarMorphRunEndpointPx(
        frozenEndpointPx = frozenEndpointHPx,
        liveTargetPx = restLiveLockHPx,
        pillPx = pillHPx,
        morphTransitionRunning = morphTransitionRunning,
        morphExpandedAtRest = morphExpandedAtRest,
    )

/**
 * Owner key STATUS BAR tu event.id — khop contentOwnerKey ExpandedPanel.
 */
fun statusBarStackEventOwnerKey(eventId: String): String = "event:$eventId"

/**
 * Measure-context anh huong natural height (task-032/033).
 * [availableWidthPx] = envelope host width (KHONG so truc tiep = card W do padding);
 * [densityDpi] bat rotation/density; [fontScale] bat H doi khi text scale.
 * Token reject stale + validate lookup — KHONG re-label size cu bang effect.
 */
data class StatusBarCardNaturalMeasureContext(
    val availableWidthPx: Int,
    val densityDpi: Int,
    val fontScale: Float = 1f,
) {
    val token: Int
        get() =
            statusBarCardNaturalMeasureContextToken(
                availableWidthPx = availableWidthPx,
                densityDpi = densityDpi,
                fontScale = fontScale,
            )
}

/** Token on dinh: envelopeW + densityDpi + fontScale (milli). */
fun statusBarCardNaturalMeasureContextToken(
    availableWidthPx: Int,
    densityDpi: Int,
    fontScale: Float = 1f,
): Int {
    var h = 17
    h = 31 * h + availableWidthPx.coerceAtLeast(0)
    h = 31 * h + densityDpi
    // milli tranh float noise; cover H doi khi fontScale doi ma W/dpi giu.
    h = 31 * h + (fontScale * 1000f).toInt()
    return h
}

/**
 * Cache entry natural: H + measured W + context token luc layout/measure.
 * Lookup chi hop le khi token == active context.
 */
data class StatusBarCardNaturalCacheEntry(
    val heightPx: Int,
    val measuredWidthPx: Int,
    val contextToken: Int,
)

/**
 * Observation layer sau place/measure — provenance = context luc layout.
 * Map remember qua context van giu ban ghi cu; active lookup can token khop.
 */
data class StatusBarCardNaturalLayerObservation(
    val widthPx: Int,
    val heightPx: Int,
    val measuredContextToken: Int,
)

/**
 * Cap nhat cache natural theo event identity + measure context (task-032).
 * - h <=0 → bo qua (khong xoa bang measure transient).
 * - reportContextToken != active → REJECT (late report old width/config).
 * - Cung key + context: follow H moi (RemoteViews/content).
 * - Tra key da ghi; null neu bo qua.
 */
fun statusBarPutCardNaturalSizeCache(
    cache: MutableMap<String, StatusBarCardNaturalCacheEntry>,
    eventId: String,
    widthPx: Int,
    heightPx: Int,
    reportContextToken: Int,
    activeContextToken: Int,
): String? {
    if (eventId.isEmpty() || heightPx <= 0) return null
    if (reportContextToken != activeContextToken) return null
    val key = statusBarStackEventOwnerKey(eventId)
    val next =
        StatusBarCardNaturalCacheEntry(
            heightPx = heightPx,
            measuredWidthPx = widthPx.coerceAtLeast(0),
            contextToken = reportContextToken,
        )
    if (cache[key] != next) cache[key] = next
    return key
}

/**
 * Lookup natural H — chi khi entry dung active context token.
 * Stale width/density/fontScale (token khac) → 0 (cho layout moi / bridge).
 */
fun statusBarLookupCardNaturalHeightPx(
    cache: Map<String, StatusBarCardNaturalCacheEntry>,
    ownerKey: String,
    activeContextToken: Int,
): Int {
    if (ownerKey.isEmpty() || activeContextToken == 0) return 0
    val entry = cache[ownerKey] ?: return 0
    if (entry.contextToken != activeContextToken) return 0
    return entry.heightPx.coerceAtLeast(0)
}

/** Lookup natural W — cung token policy voi height (task-037 rest rect). */
fun statusBarLookupCardNaturalWidthPx(
    cache: Map<String, StatusBarCardNaturalCacheEntry>,
    ownerKey: String,
    activeContextToken: Int,
): Int {
    if (ownerKey.isEmpty() || activeContextToken == 0) return 0
    val entry = cache[ownerKey] ?: return 0
    if (entry.contextToken != activeContextToken) return 0
    return entry.measuredWidthPx.coerceAtLeast(0)
}

/**
 * Observation con song qua context — chi dung khi da measure trong active.
 * ContextB chua place → null (KHONG promote H tu contextA).
 */
fun statusBarCardNaturalObservationForActiveContext(
    observation: StatusBarCardNaturalLayerObservation?,
    activeContextToken: Int,
): StatusBarCardNaturalLayerObservation? {
    if (observation == null || activeContextToken == 0) return null
    if (observation.heightPx <= 0) return null
    if (observation.measuredContextToken != activeContextToken) return null
    return observation
}

/**
 * Producer sau onPlaced/onSizeChanged (task-033): bind size vao layoutContextToken.
 * - h<=0 / token=0 → skip
 * - entry == previous → null (khong write moi frame)
 * - ke ca W/H bang previous nhung token moi (layout context moi) → publish
 * KHONG copy raw map cu gan token moi bang effect.
 */
fun statusBarCardNaturalPublishAfterLayout(
    previousPublished: StatusBarCardNaturalCacheEntry?,
    widthPx: Int,
    heightPx: Int,
    layoutContextToken: Int,
): StatusBarCardNaturalCacheEntry? {
    if (heightPx <= 0 || layoutContextToken == 0) return null
    val next =
        StatusBarCardNaturalCacheEntry(
            heightPx = heightPx,
            measuredWidthPx = widthPx.coerceAtLeast(0),
            contextToken = layoutContextToken,
        )
    return if (previousPublished != next) next else null
}

/**
 * Xoa entry khong con live hoac sai active context.
 */
fun statusBarPruneCardNaturalCache(
    cache: MutableMap<String, StatusBarCardNaturalCacheEntry>,
    liveOwnerKeys: Collection<String>,
    activeContextToken: Int,
) {
    if (cache.isEmpty()) return
    val live = liveOwnerKeys.toHashSet()
    val stale =
        cache.keys.filter { key ->
            key !in live || cache[key]?.contextToken != activeContextToken
        }
    stale.forEach { cache.remove(it) }
}

/**
 * H khoa tu natural / first-expand envelope — KHONG permanently lock max
 * under khi dang bridge doi natural (owner swap / context republish).
 *
 * - Co top natural → top+pads.
 * - Chua natural + dang co bridge/lock → 0 (caller giu continuity, cho republish).
 * - First expand (chua bridge) → cho phep stack envelope.
 */
fun statusBarLockHeightFromNaturalOrBridgePx(
    topCardNaturalHPx: Int,
    stackHostEnvelopeHPx: Int,
    topPadPx: Int,
    bottomPadPx: Int,
    pillHPx: Int,
    hasBridgeOrLockedHeight: Boolean,
): Int {
    val topHost =
        statusBarStackHostHeightFromCardNaturalPx(topCardNaturalHPx, topPadPx, bottomPadPx)
    if (topHost > pillHPx) return topHost
    if (hasBridgeOrLockedHeight) return 0
    if (stackHostEnvelopeHPx > pillHPx) return stackHostEnvelopeHPx
    return 0
}

// --- Content alpha (keyguard + statusbar) — clamp 0..1, tach khoi overshoot scale ---

/**
 * Nhip pill chrome: mo/dong khong doi xung; [progress] da clamp 0..1.
 * Mo: pill rut som. Dong: pill tro lai o nua sau.
 */
internal fun morphPillContentAlpha(progress: Float, closing: Boolean): Float =
    if (closing) {
        ((0.55f - progress) / 0.55f).coerceIn(0f, 1f)
    } else {
        (1f - progress / 0.35f).coerceIn(0f, 1f)
    }

/**
 * Nhip noi dung expanded: gate theo progress local — KHONG copy nguong 239dp Samsung.
 * Mo: hien khi khung ~progress 0.4+. Dong: tat nhanh o ~12% dau (progress cao → thap).
 */
internal fun morphExpandContentAlpha(progress: Float, closing: Boolean): Float =
    if (closing) {
        ((progress - 0.88f) / 0.12f).coerceIn(0f, 1f)
    } else {
        ((progress - 0.4f) / 0.4f).coerceIn(0f, 1f)
    }

/**
 * Shell/temp chrome trong morph STATUS BAR — bu [morphExpandContentAlpha] (keyguard).
 * StatusBar: dung [statusBarMorphShellGlassAlpha] track (StatusBarMorphTracks).
 */
internal fun morphTempShellAlpha(expandContentAlpha: Float): Float =
    (1f - expandContentAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)

// statusBarMorphShellGlassAlpha / ShellTintProgress / CardSurfaceBlurFactor
// → SSOT StatusBarMorphTracks.kt (color + glass doc lap geom).

/**
 * Collapse handback chrome overlay (task-043/044/046).
 *
 * Truoc 043: hold && !suppressed → 0 ngay — overlay CONTENT α=0 tren window khac
 * luc real pill moi unsuppress → race both-hidden (A→A lan A→B).
 *
 * Sau 043: giu chrome=1 den [liveDrawnAcked] (chip OnDraw / timeout failsafe).
 * Sau 044: staticMode release SAU overlay hide (phase5); early-ack StateFlow latch.
 * Sau 045: phase5 OnDraw wait bounded (≤100ms) — khong lock static vinh vien.
 * Sau 046: chrome van 0/1 (ACK gate); material overlap dung complementary layer
 * (khong 2×AlphaGlass=.9216 trong REVEALED_AWAITING_DRAW).
 */
internal fun statusBarMorphCollapseOverlayChromeFactor(
    collapseHandbackHold: Boolean,
    pillSuppressed: Boolean,
    liveDrawnAcked: Boolean = false,
): Float {
    if (!collapseHandbackHold) return 1f
    if (pillSuppressed) return 1f
    // hold + unsuppress: giu GLASS den khi live ack (khong α0 truoc real ve).
    // Task-053: dummy FG tach — xem [statusBarMorphCollapseOverlayDummyFgAlpha].
    if (!liveDrawnAcked) return 1f
    return 0f
}

/**
 * Legacy dummy FG alpha (pre task-001).
 *
 * SUPERSEDED: dung [statusBarCollapseHandoffDummyFgAlpha] / ownership phase.
 * Unsuppress KHONG duoc an dummy ngay (both-hidden voi real alpha0) —
 * dummy giu den Transfer. Helper nay giu signature test cu; production Panel
 * khong goi path !pillSuppressed → 0 nua.
 */
internal fun statusBarMorphCollapseOverlayDummyFgAlpha(
    pillFollowAlpha: Float,
    collapseChromeFactor: Float,
    pillSuppressed: Boolean,
    forwardAwaitSuppress: Boolean,
): Float {
    if (forwardAwaitSuppress) return 0f
    if (!pillSuppressed) return 0f
    return (pillFollowAlpha * collapseChromeFactor).coerceIn(0f, 1f)
}

/**
 * Task-053 policy: freezeMotion co the true trong khi liveValue true.
 * Chung minh decouple — handbackKeepStatic khong khoa content.
 */
internal fun statusBarCollapseHandbackContentLiveWhileMotionFrozen(
    pillSuppressed: Boolean,
    handbackKeepStatic: Boolean,
): Boolean {
    val freezeMotion = pillSuppressed || handbackKeepStatic
    val liveValue = !pillSuppressed
    return liveValue && freezeMotion
}

/**
 * Task-053: snapshot diag (owner/gen/suppress/keepStatic/freeze/live/dummyFg).
 * Log khi doi trang thai — khong moi frame.
 */
data class StatusBarHandbackLivenessDiag(
    val ownerId: String?,
    val layoutGeneration: Int,
    val pillSuppressed: Boolean,
    val handbackKeepStatic: Boolean,
    val freezeMotion: Boolean,
    val liveValue: Boolean,
    val dummyFgAlpha: Float,
    val overlayChromeFactor: Float,
)

internal fun statusBarHandbackLivenessDiagOf(
    ownerId: String?,
    layoutGeneration: Int,
    pillSuppressed: Boolean,
    handbackKeepStatic: Boolean,
    collapseHandbackHold: Boolean,
    liveDrawnAcked: Boolean,
    pillFollowAlpha: Float = 1f,
): StatusBarHandbackLivenessDiag {
    val chrome =
        statusBarMorphCollapseOverlayChromeFactor(
            collapseHandbackHold = collapseHandbackHold,
            pillSuppressed = pillSuppressed,
            liveDrawnAcked = liveDrawnAcked,
        )
    return StatusBarHandbackLivenessDiag(
        ownerId = ownerId,
        layoutGeneration = layoutGeneration,
        pillSuppressed = pillSuppressed,
        handbackKeepStatic = handbackKeepStatic,
        freezeMotion = pillSuppressed || handbackKeepStatic,
        liveValue = !pillSuppressed,
        dummyFgAlpha =
            statusBarMorphCollapseOverlayDummyFgAlpha(
                pillFollowAlpha = pillFollowAlpha,
                collapseChromeFactor = chrome,
                pillSuppressed = pillSuppressed,
                forwardAwaitSuppress = false,
            ),
        overlayChromeFactor = chrome,
    )
}

/**
 * Layer α staged (<0.1) luc await-draw — OnDraw traverse pill nonzero, khong full 1.
 * Task-046: tranh true×.72 + overlay×.72 = .9216 flash.
 */
internal const val StatusBarHandbackStagedTrueLayerAlpha = 0.05f

/**
 * So frame ramp staged→1 sau credible OnDraw (frame-driven, khong sleep).
 * ±1 SF frame OK; khong timer dai.
 */
internal const val StatusBarHandbackHandoverFrameSteps = 3

/**
 * Timeout primary (cung hide delay 50ms) het → force-reveal + doi readiness that.
 * KHONG fabricate ACK khi α=0.
 */
internal const val StatusBarHandbackReadinessFailsafeMs = 200L

/**
 * So lan retry pill-layer draw/ready (drawWithContent / bounds) truoc recovery.
 * Root OnDraw khong reliable — retry bounded, khong return som roi treo Panel.
 */
internal const val StatusBarHandbackPillLayerReadyMaxAttempts = 8

/**
 * Timeout moi lan await pill-layer draw (ms) — invalidate + doi signal.
 */
internal const val StatusBarHandbackPillLayerDrawAttemptMs = 48L

/**
 * Panel stall recovery: sau 2 failsafe van !acked — doi Chip force-full bounded.
 * CAM snapshotFlow.first vo han (task-048 stuck dummy @p0).
 */
internal const val StatusBarHandbackStallRecoveryMs = 400L

/**
 * Overlay complementary layer y khi true layer = x:
 * y = (1−x) / (1 − materialAlpha·x)  → effective material ≈ materialAlpha.
 *
 * x=0 → y=1; x=1 → y=0; x=staged(.05) → y≈0.985 (khong both-full .72+.72).
 */
internal fun statusBarMorphHandoffComplementaryOverlayLayer(
    trueLayerFraction: Float,
    materialAlpha: Float = AlphaGlass,
): Float {
    val x = trueLayerFraction.coerceIn(0f, 1f)
    val a = materialAlpha.coerceIn(0f, 1f)
    val denom = 1f - a * x
    if (denom <= 1e-6f) return 0f
    return ((1f - x) / denom).coerceIn(0f, 1f)
}

/**
 * True-layer dua vao complementary overlay (Panel).
 *
 * ForceFull REQUEST co the muon α=1 de paint pill, nhung [drawCredible]=false
 * KHONG duoc derive x=1→y=0 (blank flash). Giu staged → overlay material ≈AlphaGlass.
 * COMMIT ownership (credible) moi dung published layer that.
 */
internal fun statusBarMorphHandoffOverlayTrueLayerForProgress(
    publishedTrueLayer: Float,
    drawCredible: Boolean,
    revealRequested: Boolean,
): Float {
    if (!revealRequested) return 0f
    if (!drawCredible) return StatusBarHandbackStagedTrueLayerAlpha
    return publishedTrueLayer.coerceIn(0f, 1f)
}

/**
 * Material overlay proxy luc await-draw (!credible) — single glass ≈AlphaGlass.
 */
internal fun statusBarMorphHandoffAwaitDrawOverlayMaterialAlpha(
    materialAlpha: Float = AlphaGlass,
): Float {
    val y =
        statusBarMorphHandoffComplementaryOverlayLayer(
            trueLayerFraction = StatusBarHandbackStagedTrueLayerAlpha,
            materialAlpha = materialAlpha,
        )
    return statusBarMorphMaterialTintAlpha(y, materialAlpha)
}

/**
 * True layer fraction theo progress handoff shared (0=overlay owns, 1=true owns).
 *
 * - progress<=0 / chua reveal → 0
 * - await-draw (stagedUntilReady) → [StatusBarHandbackStagedTrueLayerAlpha]
 * - handover: lerp staged→1 theo progress (progress map 0..1 sau ready)
 */
internal fun statusBarMorphHandoffTrueLayerFromProgress(
    progress: Float,
    revealed: Boolean,
    stagedUntilReady: Boolean,
): Float {
    if (!revealed) return 0f
    val p = progress.coerceIn(0f, 1f)
    if (stagedUntilReady) return StatusBarHandbackStagedTrueLayerAlpha
    if (p <= 0f) return StatusBarHandbackStagedTrueLayerAlpha
    // progress 0 → staged; 1 → full.
    val staged = StatusBarHandbackStagedTrueLayerAlpha
    return (staged + (1f - staged) * p).coerceIn(0f, 1f)
}

/**
 * Task-054: FG visibility (outer graphicsLayer) — CHI full suppress / removal.
 *
 * - suppressed → 0
 * - reverse hold chua reveal → 0
 * - unsuppress + revealed → 1 (LIVE timer/title/icon/progress) — KHONG nhan staged .05
 *
 * Glass staged/ramp dung [statusBarMorphHandoffNativeGlassFraction] (blurAlpha).
 * [trueLayerFraction] giu signature call-site cu; KHONG anh huong FG.
 */
@Suppress("UNUSED_PARAMETER")
internal fun statusBarMorphHandoffTruePillAlpha(
    pillSuppressed: Boolean,
    handbackGlassRevealed: Boolean,
    trueLayerFraction: Float = 1f,
): Float {
    if (pillSuppressed) return 0f
    if (!handbackGlassRevealed) return 0f
    return 1f
}

/**
 * Task-054: native islandBlur / Circle soft-fill fraction (KHONG dim FG).
 *
 * - suppressed / !reveal → 0
 * - unsuppress await → staged (.05); handover ramp→1; ACK → 1
 * Overlay complementary match **fraction nay**, khong theo FG α.
 */
internal fun statusBarMorphHandoffNativeGlassFraction(
    pillSuppressed: Boolean,
    handbackGlassRevealed: Boolean,
    trueLayerFraction: Float,
): Float {
    if (pillSuppressed) return 0f
    if (!handbackGlassRevealed) return 0f
    return trueLayerFraction.coerceIn(0f, 1f)
}

/**
 * Overlay shared glass α gate.
 *
 * True owns ([!pillSuppressed] && ![collapseHandbackHold]) → 0 ke ca
 * [isExpanded]=false / isMorphing van mount host (tranh latent double-glass).
 * Hold + unsuppress + !acked:
 * - chua reveal → persistent full (true=0)
 * - revealed → complementary(trueLayer) × persistent (tranh 2×AlphaGlass)
 * Hold / suppressed khac: [persistentShared] × chrome.
 */
@Suppress("UNUSED_PARAMETER")
internal fun statusBarMorphHandoffOverlayGlassAlpha(
    isExpanded: Boolean,
    pillSuppressed: Boolean,
    collapseHandbackHold: Boolean,
    persistentSharedGlassAlpha: Float,
    handbackGlassRevealed: Boolean = true,
    trueLayerFraction: Float = 1f,
    liveDrawnAcked: Boolean = false,
    materialAlpha: Float = AlphaGlass,
): Float {
    // True pill owns — overlay glass off (host co the con mount / isMorphing).
    // [isExpanded] giu signature call-site (forward/idle); gate !suppress&&!hold du.
    if (!pillSuppressed && !collapseHandbackHold) return 0f
    val base = persistentSharedGlassAlpha.coerceIn(0f, 1f)
    // Hold + unsuppress + chua ACK: complementary theo true layer (shared progress).
    if (collapseHandbackHold && !pillSuppressed && !liveDrawnAcked) {
        if (!handbackGlassRevealed) return base
        val y =
            statusBarMorphHandoffComplementaryOverlayLayer(
                trueLayerFraction = trueLayerFraction,
                materialAlpha = materialAlpha,
            )
        return (base * y).coerceIn(0f, 1f)
    }
    return base
}

/**
 * SRC_OVER effective α khi 2 lop (cung material) — proxy flash overlap.
 * 1 owner: max(bottom,top)≈A; 2×A → A+A·(1−A) sang hon.
 * Luu y: A=1 tautology (1+1·0=1) — dung [AlphaGlass] (.72) de do brightness that.
 */
internal fun statusBarMorphSrcOverEffectiveAlpha(bottom: Float, top: Float): Float {
    val b = bottom.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    return t + b * (1f - t)
}

/**
 * Material tint α sau layer gate — [layerAlpha]×[materialAlpha] (AlphaGlass=.72 @p0).
 */
internal fun statusBarMorphMaterialTintAlpha(
    layerAlpha: Float,
    materialAlpha: Float = AlphaGlass,
): Float = (layerAlpha.coerceIn(0f, 1f) * materialAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)

/**
 * SRC_OVER 2 lop material tint (vd. 2× AlphaGlass → .9216 > .72).
 */
internal fun statusBarMorphMaterialSrcOverEffective(
    bottomLayerAlpha: Float,
    topLayerAlpha: Float,
    materialAlpha: Float = AlphaGlass,
): Float =
    statusBarMorphSrcOverEffectiveAlpha(
        bottom = statusBarMorphMaterialTintAlpha(bottomLayerAlpha, materialAlpha),
        top = statusBarMorphMaterialTintAlpha(topLayerAlpha, materialAlpha),
    )

/**
 * Bang handoff: FG α, native glass fraction, overlay α, material — forward/reverse.
 * Task-054: [truePillAlpha]=FG; [nativeGlassFraction]=blur/soft-fill; overlay complementary
 * theo native glass (khong theo FG).
 */
internal data class StatusBarMorphHandoffAlphaSample(
    /** FG visibility (outer layer) — 0 suppress / 1 unsuppress revealed. */
    val truePillAlpha: Float,
    val overlayGlassAlpha: Float,
    val effectiveSrcOver: Float,
    /** native×AlphaGlass (mac dinh). */
    val trueMaterialAlpha: Float = 0f,
    /** overlay×AlphaGlass. */
    val overlayMaterialAlpha: Float = 0f,
    /** SRC_OVER material — do brightness that (khong dung layer α=1 tautology). */
    val effectiveMaterialSrcOver: Float = 0f,
    /** Task-054: islandBlur / Circle soft-fill fraction (staged/ramp). */
    val nativeGlassFraction: Float = 1f,
)

internal fun statusBarMorphHandoffAlphaSample(
    pillSuppressed: Boolean,
    handbackGlassRevealed: Boolean,
    isExpanded: Boolean,
    collapseHandbackHold: Boolean,
    liveDrawnAcked: Boolean,
    sharedGlassOwnerPresent: Boolean = true,
    materialAlpha: Float = AlphaGlass,
    /** Native glass sau reveal; await-draw staged (.05), handover ramp→1. */
    trueLayerFraction: Float = 1f,
): StatusBarMorphHandoffAlphaSample {
    val chrome =
        statusBarMorphCollapseOverlayChromeFactor(
            collapseHandbackHold = collapseHandbackHold,
            pillSuppressed = pillSuppressed,
            liveDrawnAcked = liveDrawnAcked,
        )
    val persistent =
        statusBarMorphPersistentSharedGlassAlpha(
            sharedGlassOwnerPresent = sharedGlassOwnerPresent,
            collapseChromeFactor = chrome,
        )
    val fgA =
        statusBarMorphHandoffTruePillAlpha(
            pillSuppressed = pillSuppressed,
            handbackGlassRevealed = handbackGlassRevealed,
            trueLayerFraction = trueLayerFraction,
        )
    val glassX =
        statusBarMorphHandoffNativeGlassFraction(
            pillSuppressed = pillSuppressed,
            handbackGlassRevealed = handbackGlassRevealed,
            trueLayerFraction = trueLayerFraction,
        )
    val overlay =
        statusBarMorphHandoffOverlayGlassAlpha(
            isExpanded = isExpanded,
            pillSuppressed = pillSuppressed,
            collapseHandbackHold = collapseHandbackHold,
            persistentSharedGlassAlpha = persistent,
            handbackGlassRevealed = handbackGlassRevealed,
            trueLayerFraction = glassX,
            liveDrawnAcked = liveDrawnAcked,
            materialAlpha = materialAlpha,
        )
    val tMat = statusBarMorphMaterialTintAlpha(glassX, materialAlpha)
    val oMat = statusBarMorphMaterialTintAlpha(overlay, materialAlpha)
    return StatusBarMorphHandoffAlphaSample(
        truePillAlpha = fgA,
        overlayGlassAlpha = overlay,
        effectiveSrcOver = statusBarMorphSrcOverEffectiveAlpha(glassX, overlay),
        trueMaterialAlpha = tMat,
        overlayMaterialAlpha = oMat,
        effectiveMaterialSrcOver = statusBarMorphSrcOverEffectiveAlpha(tMat, oMat),
        nativeGlassFraction = glassX,
    )
}

/**
 * Phase3 handback (task-043/046):
 * HOLD_HIDDEN → REVEALED_AWAITING_DRAW (true staged α>0, complementary overlay)
 * → HANDOVER (ramp complementary) → ACKED (overlay drop).
 * Root OnDraw luc HOLD_HIDDEN = false-positive (pill invisible).
 */
internal enum class HandbackVisibleRevealPhase {
    HOLD_HIDDEN,
    REVEALED_AWAITING_DRAW,
    HANDOVER,
    ACKED,
}

internal fun statusBarCollapseHandbackVisibleRevealPhase(
    handbackGlassRevealed: Boolean,
    liveDrawnAcked: Boolean,
    trueLayerFraction: Float = 1f,
    drawCredible: Boolean = false,
): HandbackVisibleRevealPhase =
    when {
        liveDrawnAcked -> HandbackVisibleRevealPhase.ACKED
        !handbackGlassRevealed -> HandbackVisibleRevealPhase.HOLD_HIDDEN
        drawCredible &&
            trueLayerFraction > StatusBarHandbackStagedTrueLayerAlpha + 0.001f ->
            HandbackVisibleRevealPhase.HANDOVER
        else -> HandbackVisibleRevealPhase.REVEALED_AWAITING_DRAW
    }

/**
 * Chi ACK overlay drop khi true pill α>0 + draw credible (khong ACK tu root OnDraw α=0).
 * Staged α (.05) du de OnDraw credible — khong can full 1 truoc ACK path bat dau ramp.
 */
internal fun statusBarCollapseHandbackAckAfterVisibleLive(
    truePillAlpha: Float,
    handbackGlassRevealed: Boolean,
    drawCredible: Boolean,
): Boolean =
    truePillAlpha > 0.01f && handbackGlassRevealed && drawCredible

/**
 * Root OnDraw chung minh visible pill glass? Chi khi da reveal (α>0).
 * α=0 → OnDraw root van fire (SB chrome khac) — KHONG credible.
 * Staged (.05) van prove (≠ fabricated readiness flag).
 */
internal fun statusBarCollapseHandbackRootDrawProvesVisiblePill(
    handbackGlassRevealed: Boolean,
    truePillAlpha: Float,
): Boolean = handbackGlassRevealed && truePillAlpha > 0.01f

/**
 * True khi ca hai surface an — bug handback (overlay chrome 0 + live chua ve).
 * Pure gate / test; khong thay sleep.
 */
internal fun statusBarCollapseHandbackBothHidden(
    overlayGlassOrChrome: Float,
    livePillAlpha: Float,
): Boolean = overlayGlassOrChrome <= 0.01f && livePillAlpha <= 0.01f

/**
 * Both-full material flash: 2× layer≈1 → SrcOver material .9216.
 * Staged+complementary KHONG trigger.
 */
internal fun statusBarCollapseHandbackBrightDoubleMaterial(
    trueLayer: Float,
    overlayLayer: Float,
    materialAlpha: Float = AlphaGlass,
    epsilonAboveSingle: Float = 0.05f,
): Boolean {
    val eff =
        statusBarMorphMaterialSrcOverEffective(trueLayer, overlayLayer, materialAlpha)
    return eff > materialAlpha + epsilonAboveSingle
}

/**
 * Ordering an toan sau unsuppress: chrome phai con 1 neu chua liveDrawnAcked.
 */
internal fun statusBarCollapseHandbackOrderingSafe(
    pillSuppressed: Boolean,
    overlayChromeFactor: Float,
    liveDrawnAcked: Boolean,
): Boolean {
    if (pillSuppressed) return overlayChromeFactor >= 0.99f
    if (!liveDrawnAcked) return overlayChromeFactor >= 0.99f
    return overlayChromeFactor <= 0.01f
}

/**
 * Nguong trueLayer ≈1 de ACK/drop overlay (task-045).
 * Staged .05 / mid-ramp .36 → CAM — complementary con giu.
 */
internal const val StatusBarHandbackTrueLayerAckMin = 0.99f

/**
 * Timeout / failsafe duoc phep drop overlay?
 *
 * - Live owner: CHI trueLayer≈1 VA drawCredible OnDraw (owner/gen khop o caller)
 * - Staged .05 / mid-ramp / !credible → CAM (khong alpha hole chrome0)
 * - No-owner that (!liveOwnerExpected || !chipExists) → OK unstick
 * - Cam fabricate readiness (fake no-owner / fake α=1)
 */
internal fun statusBarCollapseHandbackTimeoutMayDropOverlay(
    handbackGlassRevealed: Boolean,
    truePillAlpha: Float,
    liveOwnerExpected: Boolean,
    chipExists: Boolean = true,
    drawCredible: Boolean = false,
): Boolean {
    if (!liveOwnerExpected || !chipExists) return true
    if (!handbackGlassRevealed) return false
    if (!drawCredible) return false
    if (truePillAlpha < StatusBarHandbackTrueLayerAckMin) return false
    return true
}

/**
 * Phan biet request-reveal vs α>0 vs credible OnDraw (≠ SF present).
 * ACK chi khi fraction≈1 + credible + gen/owner khop.
 */
internal data class HandbackReadinessSnapshot(
    val revealRequested: Boolean,
    val trueLayerFraction: Float,
    val drawCredible: Boolean,
    val ownerId: String?,
    val holdGeneration: Int,
    val layoutGeneration: Int = -1,
)

internal fun statusBarCollapseHandbackReadinessAllowsAck(
    snap: HandbackReadinessSnapshot,
    expectedHoldGeneration: Int,
    expectedOwnerId: String?,
    expectedLayoutGeneration: Int = -1,
): Boolean {
    if (snap.holdGeneration != expectedHoldGeneration) return false
    if (
        expectedLayoutGeneration >= 0 &&
            snap.layoutGeneration >= 0 &&
            snap.layoutGeneration != expectedLayoutGeneration
    ) {
        return false
    }
    if (!snap.revealRequested) return false
    if (snap.trueLayerFraction < StatusBarHandbackTrueLayerAckMin) return false
    if (!snap.drawCredible) return false
    if (
        snap.ownerId != null &&
            expectedOwnerId != null &&
            snap.ownerId != expectedOwnerId
    ) {
        return false
    }
    return true
}

/**
 * Progress Chip→Panel SSOT: reject stale holdGen / layoutGen / owner.
 * KHONG dung holdGeneration=-1.
 */
internal fun statusBarCollapseHandbackProgressMatchesSession(
    progressHoldGeneration: Int,
    progressLayoutGeneration: Int,
    progressOwnerId: String?,
    expectedHoldGeneration: Int,
    expectedLayoutGeneration: Int,
    expectedOwnerId: String?,
): Boolean {
    if (progressHoldGeneration < 0) return false
    if (progressHoldGeneration != expectedHoldGeneration) return false
    if (progressLayoutGeneration != expectedLayoutGeneration) return false
    if (
        expectedOwnerId != null &&
            progressOwnerId != null &&
            progressOwnerId != expectedOwnerId
    ) {
        return false
    }
    return true
}

/**
 * Forward suppress toggle: cam both-full / both-hidden (cung gate reverse).
 * trueOwns → overlay0; overlayOwns → true0; crossfade complementary OK.
 * Task-054: both-hidden theo FG; bright-double theo [nativeGlassFraction] (khong FG=1).
 */
internal fun statusBarMorphHandoffForwardSingleOwnerSafe(
    truePillAlpha: Float,
    overlayGlassAlpha: Float,
    materialAlpha: Float = AlphaGlass,
    nativeGlassFraction: Float = truePillAlpha,
): Boolean {
    if (statusBarCollapseHandbackBothHidden(overlayGlassAlpha, truePillAlpha)) {
        return false
    }
    if (
        statusBarCollapseHandbackBrightDoubleMaterial(
            trueLayer = nativeGlassFraction,
            overlayLayer = overlayGlassAlpha,
            materialAlpha = materialAlpha,
        )
    ) {
        return false
    }
    return true
}

/**
 * OnDraw credible? VTO dead / detach / khong pill / null owner → KHONG coi la draw
 * (task-044 CHECK#3). Residual: OnDraw ≠ pixel da present tren panel.
 */
internal fun statusBarCollapseHandbackDrawCredible(
    attachedToWindow: Boolean,
    viewTreeObserverAlive: Boolean,
    chipExists: Boolean,
    ownerId: String?,
): Boolean {
    if (!attachedToWindow) return false
    if (!viewTreeObserverAlive) return false
    if (!chipExists) return false
    if (ownerId.isNullOrEmpty()) return false
    return true
}

/**
 * Phase5: staticMode chi duoc release sau overlay hide confirm (khong flip ngay OnDraw).
 */
internal fun statusBarCollapseHandbackStaticReleaseAllowed(
    overlayHideConfirmed: Boolean,
): Boolean = overlayHideConfirmed

/**
 * Sau unsuppress, static phai con hold neu chua hide confirm — tranh marquee/ticker flash.
 */
internal fun statusBarCollapseHandbackStaticModeHeldUntilHide(
    handbackKeepStatic: Boolean,
    pillSuppressed: Boolean,
    overlayHideConfirmed: Boolean,
): Boolean {
    if (pillSuppressed) return handbackKeepStatic
    if (!overlayHideConfirmed) return handbackKeepStatic
    return true
}

/**
 * Phase5 bounded wait OnDraw sau hide confirm (task-045).
 * CHI restore static→live — KHONG dung thay phase3 ACK timeout 50ms.
 */
internal const val StatusBarHandbackPhase5StaticRestoreTimeoutMs = 100L

/**
 * Ket qua await OnDraw bounded (phase5) — moi nhánh deu ket thuc wait (khong treo).
 * null [boundedDrew] = timeout; true = OnDraw; false = detach / VTO dead / no dispatch.
 */
internal enum class HandbackPhase5DrawWaitOutcome {
    DREW,
    TIMEOUT,
    DETACHED_OR_DEAD,
}

internal fun statusBarCollapseHandbackPhase5MapWaitOutcome(
    boundedDrew: Boolean?,
): HandbackPhase5DrawWaitOutcome =
    when (boundedDrew) {
        true -> HandbackPhase5DrawWaitOutcome.DREW
        false -> HandbackPhase5DrawWaitOutcome.DETACHED_OR_DEAD
        null -> HandbackPhase5DrawWaitOutcome.TIMEOUT
    }

/**
 * Phase5: sau hide confirm / abort — co nen nha handbackKeepStatic?
 *
 * - abort → true ngay (khong doi OnDraw)
 * - chua confirm → false (giu static; khong release som hon 044)
 * - stale layout gen → false (doi signal dung gen / effect moi)
 * - confirm + wait xong (drew / timeout / detach) → true (tranh frozen ticker)
 */
internal fun statusBarCollapseHandbackPhase5ShouldReleaseStatic(
    abort: Boolean,
    overlayHideConfirmed: Boolean,
    signalLayoutGen: Int,
    currentLayoutGen: Int,
    drawWaitOutcome: HandbackPhase5DrawWaitOutcome?,
): Boolean {
    if (abort) return true
    if (!overlayHideConfirmed) return false
    if (signalLayoutGen != currentLayoutGen) return false
    // Confirm + gen khop: bat ky outcome wait (ke ca null chua wait) — chi release khi wait xong.
    return drawWaitOutcome != null
}

/** Phase5 timeout phai tach phase3 ACK 50ms — khong conflate. */
internal fun statusBarCollapseHandbackPhase5TimeoutDistinctFromPhase3Ack(
    phase5TimeoutMs: Long,
    phase3AckTimeoutMs: Long = statusBarOverlayHideDelayMs(alertExitGrace = false),
): Boolean = phase5TimeoutMs != phase3AckTimeoutMs && phase5TimeoutMs in 1L..100L

/**
 * Pill-layer draw/ready (task-048) — thay root OnDraw.
 * attached + size>0 + layerDrawn (drawWithContent) + true α>0.
 */
internal fun statusBarCollapseHandbackPillLayerReady(
    attachedToWindow: Boolean,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    layerDrawn: Boolean,
    truePillAlpha: Float,
): Boolean {
    if (!attachedToWindow) return false
    if (layoutWidthPx <= 0 || layoutHeightPx <= 0) return false
    if (!layerDrawn) return false
    if (truePillAlpha <= 0.01f) return false
    return true
}

/**
 * Het retry pill-layer: van can own-layer draw (FAIL D task-050).
 * Attached+bounds+staged THIEU [layerDrawn] → false (khong mark credible / drop overlay).
 * Anti-stuck: ForceFull / post-stall doi own-layer callback that.
 */
internal fun statusBarCollapseHandbackPillLayerReadyAfterRetries(
    attemptsUsed: Int,
    maxAttempts: Int = StatusBarHandbackPillLayerReadyMaxAttempts,
    attachedToWindow: Boolean,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    truePillAlpha: Float,
    layerDrawn: Boolean = false,
): Boolean {
    if (attemptsUsed < maxAttempts) return false
    if (!attachedToWindow) return false
    if (layoutWidthPx <= 0 || layoutHeightPx <= 0) return false
    if (truePillAlpha <= 0.01f) return false
    // FAIL D: cam ready chi vi attached — bat buoc own-layer callback.
    if (!layerDrawn) return false
    return true
}

/**
 * Cap phat requestId monotonic cho ForceReveal — moi retry doc lap
 * (StateFlow null-then-same se conflate; SharedFlow+id thi khong).
 */
internal fun statusBarCollapseHandbackNextForceRevealRequestId(
    sequence: java.util.concurrent.atomic.AtomicLong,
): Long = sequence.incrementAndGet()

/** 3+ requestId tang dan, khong trung — chung minh retry observable. */
internal fun statusBarCollapseHandbackForceRevealRequestIdsIndependent(
    requestIds: LongArray,
): Boolean {
    if (requestIds.size < 3) return false
    for (i in 1 until requestIds.size) {
        if (requestIds[i] <= requestIds[i - 1]) return false
    }
    return requestIds.toSet().size == requestIds.size
}

/** ForceReveal khop session hold/layout — stale gen reject. */
internal fun statusBarCollapseHandbackForceRevealMatchesSession(
    requestHoldGeneration: Int,
    requestLayoutGeneration: Int,
    sessionHoldGeneration: Int,
    sessionLayoutGeneration: Int,
): Boolean =
    requestHoldGeneration == sessionHoldGeneration &&
        requestLayoutGeneration == sessionLayoutGeneration

/**
 * onPillLayerDrawn post: revalidate gen/owner/size/α truoc ++epoch (FAIL C).
 * Stale hold/layout/owner / size0 / α0 / detach session → false (khong ACK).
 */
internal fun statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch(
    keepStatic: Boolean,
    pillSuppressed: Boolean,
    sessionHoldGeneration: Int?,
    sessionLayoutGeneration: Int?,
    capturedHoldGeneration: Int,
    capturedLayoutGeneration: Int,
    currentLayoutGeneration: Int,
    expectedOwnerId: String?,
    drawnOwnerId: String,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    trueLayerFraction: Float,
): Boolean {
    if (!keepStatic || pillSuppressed) return false
    if (sessionHoldGeneration == null || sessionLayoutGeneration == null) return false
    if (sessionHoldGeneration != capturedHoldGeneration) return false
    if (sessionLayoutGeneration != capturedLayoutGeneration) return false
    if (sessionLayoutGeneration != currentLayoutGeneration) return false
    if (expectedOwnerId != null && drawnOwnerId != expectedOwnerId) return false
    if (layoutWidthPx <= 0 || layoutHeightPx <= 0) return false
    if (trueLayerFraction <= 0.01f) return false
    return true
}

/**
 * Panel: sau failsafe van !acked → co duoc goi stall recovery (force-full Chip)?
 * Live owner + chua ACK → true (bounded); da ACK / gen stale → false.
 */
internal fun statusBarCollapseHandbackShouldRequestStallRecovery(
    liveDrawnAcked: Boolean,
    expectedOwnerPresent: Boolean,
    holdStillActive: Boolean,
): Boolean = holdStillActive && !liveDrawnAcked && expectedOwnerPresent

/**
 * Stall recovery: du dieu kien ACK sau ForceFull REQUEST α=1?
 * No-owner → true (release). Size0 / detach / null owner+chip → false (KHONG ACK).
 * Chi REQUEST α khong dung helper nay — doi pill-layer size>0 + draw.
 */
internal fun statusBarCollapseHandbackStallRecoveryMayForceFull(
    attachedToWindow: Boolean,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    ownerId: String?,
    chipExists: Boolean,
): Boolean {
    if (!chipExists) return true // no-owner path — release
    if (ownerId.isNullOrEmpty()) return true
    if (!attachedToWindow) return false
    // FAIL A1: size0 / unmeasured → KHONG ACK (root attach != pill paint).
    if (layoutWidthPx <= 0 || layoutHeightPx <= 0) return false
    return true
}

/**
 * ForceFull ACK gate day du: MayForceFull + pill-layer ready + owner khop + credible.
 * Root attach / cache size thieu layerDrawn → false.
 */
internal fun statusBarCollapseHandbackStallRecoveryForceFullMayAck(
    mayForceFull: Boolean,
    pillLayerReady: Boolean,
    drawCredible: Boolean,
    ownerMatchesSession: Boolean,
): Boolean {
    if (!mayForceFull) return false
    if (!pillLayerReady) return false
    if (!drawCredible) return false
    if (!ownerMatchesSession) return false
    return true
}

/** Post-stall: chu ky observe reactive (cancellable; KHONG first vo han). */
internal const val StatusBarHandbackPostStallObservePeriodMs = 48L

/** Post-stall: so lan retry force-full + observe truoc khi chi doi drawn signal. */
internal const val StatusBarHandbackPostStallMaxForceRetries = 10

/**
 * Con doi reactive recovery? Owner live + hold + chua ACK → true.
 * No-owner / da ACK / hold off → false (cho phep release / thoat).
 */
internal fun statusBarCollapseHandbackShouldContinuePostStallObserve(
    liveDrawnAcked: Boolean,
    expectedOwnerPresent: Boolean,
    holdStillActive: Boolean,
): Boolean = holdStillActive && !liveDrawnAcked && expectedOwnerPresent
/**
 * Shared glass translationY theo SSOT dragY — chi expanded-at-rest.
 * Morph/settle: 0 (khong fake shift).
 */
internal fun statusBarMorphSharedGlassDragTranslationYPx(
    dragYPx: Float,
    morphExpandedAtRest: Boolean,
): Float {
    if (!morphExpandedAtRest) return 0f
    return dragYPx
}

/**
 * |screenY_fg − screenY_glass| sau cung dragY (rigid) — ky vong ≤1px.
 */
internal fun statusBarMorphFgGlassScreenYDeltaPx(
    fgScreenYPx: Float,
    glassScreenYPx: Float,
): Float = kotlin.math.abs(fgScreenYPx - glassScreenYPx)

/**
 * Painted LRTB: khi |dragY|>eps → KHONG update frozen (tranh poison / jitter feedback).
 */
internal fun statusBarMorphPaintedLrtbUpdateAllowed(
    dragYPx: Float,
    settling: Boolean = false,
    dragEpsilonPx: Float = 0.5f,
): Boolean {
    if (settling) return false
    return kotlin.math.abs(dragYPx) <= dragEpsilonPx
}

/** LRTB window sau baseline-compensate dragY (pure test / reporter). */
internal data class StatusBarMorphLrtbPx(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
)

/**
 * Baseline-compensate window bounds neu reporter van fire luc drag.
 * window* da gom graphicsLayer translationY = dragY → tru de ve rest geometry.
 */
internal fun statusBarMorphPaintedBoundsBaselineCompensateDrag(
    windowLeftPx: Float,
    windowTopPx: Float,
    windowRightPx: Float,
    windowBottomPx: Float,
    dragYPx: Float,
): StatusBarMorphLrtbPx =
    StatusBarMorphLrtbPx(
        leftPx = windowLeftPx,
        topPx = windowTopPx - dragYPx,
        rightPx = windowRightPx,
        bottomPx = windowBottomPx - dragYPx,
    )

/**
 * Latch latest drawn signal (StateFlow-like) — giu early ack truoc Panel subscribe.
 * Apply qua [collapseHandbackApplyDrawnSignal]; stale gen/owner bi controller reject.
 */
internal class CollapseHandbackDrawnHold {
    var latestOwnerId: String? = null
        private set
    var latestLayoutGen: Int = -1
        private set
    var hasSignal: Boolean = false
        private set

    fun report(ownerId: String?, layoutGeneration: Int) {
        latestOwnerId = ownerId
        latestLayoutGen = layoutGeneration
        hasSignal = true
    }

    fun clear() {
        latestOwnerId = null
        latestLayoutGen = -1
        hasSignal = false
    }
}

/** Apply signal (early hoac sau subscribe) vao controller — reject stale. */
internal fun collapseHandbackApplyDrawnSignal(
    ctrl: CollapseHandbackHoldController,
    holdGeneration: Int,
    ownerId: String?,
    layoutGeneration: Int,
): Boolean =
    ctrl.acknowledgeLiveDrawn(
        holdGeneration = holdGeneration,
        layoutGen = layoutGeneration,
        ownerId = ownerId,
    )

/**
 * State machine hold sau settle — tach khoi LaunchedEffect co key pillSuppressed.
 *
 * Bug 028: effect set hold=true → notifyCollapseSettled → pillSuppressed=false →
 * recomposition cancel effect TRUOC delay(50) → hold stuck true.
 *
 * Task-043: them [liveDrawnAcked] — chrome overlay chi ve 0 sau ack (hoac timeout).
 * Task-044: [overlayHideConfirmed] tach phase5 static release; early-ack latch.
 * Task-045: phase5 await OnDraw + timeout ≤100ms gen-safe; detach cleanup VTO.
 *
 * Compose: (1) settle hold + notify; (2) chip OnDraw ack (static con); (3) chrome 0;
 * (4) confirm hide + signal chip; (5) chip 1 OnDraw credible ∥ ≤100ms → release static.
 */
internal class CollapseHandbackHoldController {
    /** Hold dang bat (showMorphHost; chrome 1 den khi liveDrawnAcked). */
    var holdActive: Boolean = false
        private set

    /** Tang moi lan bat hold — timer/ack cu khong clear gen moi. */
    var holdGeneration: Int = 0
        private set

    /** Layout gen da settle exactly-once (tranh double notify). */
    var lastSettledLayoutGen: Int = -1
        private set

    /** Live pill StatusBar da ve (OnDraw / timeout) — cho phep chrome → 0. */
    var liveDrawnAcked: Boolean = false
        private set

    /** Overlay chrome da ve 0 — cho phep chip release staticMode (phase5). */
    var overlayHideConfirmed: Boolean = false
        private set

    /** Owner ky vong luc settle (parity B); null = khong rang buoc owner. */
    var expectedOwnerId: String? = null
        private set

    /** layoutGeneration ky vong luc settle. */
    var expectedLayoutGen: Int = -1
        private set

    data class SettleStart(
        val shouldNotifySettle: Boolean,
        val holdGeneration: Int,
    )

    /**
     * Morph @p~0 + pill van suppressed → bat hold + cho phep notify 1 lan / layoutGen.
     * Goi TRUOC notifyCollapseSettled; khong delay trong cung coroutine key pillSuppressed.
     */
    fun beginSettleHandback(layoutGen: Int, ownerId: String? = null): SettleStart {
        if (lastSettledLayoutGen == layoutGen) {
            return SettleStart(shouldNotifySettle = false, holdGeneration = holdGeneration)
        }
        lastSettledLayoutGen = layoutGen
        holdActive = true
        holdGeneration += 1
        liveDrawnAcked = false
        overlayHideConfirmed = false
        expectedOwnerId = ownerId
        expectedLayoutGen = layoutGen
        return SettleStart(shouldNotifySettle = true, holdGeneration = holdGeneration)
    }

    /**
     * Chip bao live da draw (owner/generation-safe).
     * [ownerId]=null = no-pill / unmount — van ack de khong stuck overlay.
     * Owner lech expected (non-null) → ignore (khong ack gen sai).
     */
    fun acknowledgeLiveDrawn(
        holdGeneration: Int,
        layoutGen: Int,
        ownerId: String?,
    ): Boolean {
        if (!holdActive) return false
        if (holdGeneration != this.holdGeneration) return false
        if (layoutGen != expectedLayoutGen) return false
        if (
            ownerId != null &&
                expectedOwnerId != null &&
                ownerId != expectedOwnerId
        ) {
            return false
        }
        liveDrawnAcked = true
        return true
    }

    /**
     * Timeout failsafe — CHI ack khi [statusBarCollapseHandbackTimeoutMayDropOverlay].
     * Live owner: true≈1 + drawCredible; staged/mid-ramp → reject.
     * No-owner that → mayDrop; CAM fabricate fake no-owner / fake α=1.
     */
    fun acknowledgeLiveDrawnTimeout(
        holdGeneration: Int,
        handbackGlassRevealed: Boolean = true,
        truePillAlpha: Float = 1f,
        liveOwnerExpected: Boolean = true,
        chipExists: Boolean = true,
        drawCredible: Boolean = false,
    ): Boolean {
        if (!holdActive) return false
        if (holdGeneration != this.holdGeneration) return false
        if (
            !statusBarCollapseHandbackTimeoutMayDropOverlay(
                handbackGlassRevealed = handbackGlassRevealed,
                truePillAlpha = truePillAlpha,
                liveOwnerExpected = liveOwnerExpected,
                chipExists = chipExists,
                drawCredible = drawCredible,
            )
        ) {
            return false
        }
        liveDrawnAcked = true
        return true
    }

    /**
     * Phase4 xong: chrome da 0 (acked). Chip moi duoc phep phase5 release static.
     * Goi TRUOC [clearHoldIfGeneration] de con khop holdGeneration.
     */
    fun confirmOverlayHidden(holdGeneration: Int): Boolean {
        if (holdGeneration != this.holdGeneration) return false
        if (!liveDrawnAcked) return false
        overlayHideConfirmed = true
        return true
    }

    /** Rapid reverse / expand — clear hold ngay, cho phep settle gen sau. */
    fun onExpandedOrReverse() {
        holdActive = false
        lastSettledLayoutGen = -1
        liveDrawnAcked = false
        overlayHideConfirmed = false
        expectedOwnerId = null
        expectedLayoutGen = -1
    }

    /**
     * Sau ack/timeout + hide confirm: clear chi khi dung [expectedHoldGen].
     * @return true neu da clear holdActive.
     */
    fun clearHoldIfGeneration(expectedHoldGen: Int): Boolean {
        if (expectedHoldGen != holdGeneration) return false
        if (!holdActive) return false
        holdActive = false
        liveDrawnAcked = false
        overlayHideConfirmed = false
        expectedOwnerId = null
        expectedLayoutGen = -1
        return true
    }
}

/**
 * Outer occupied width sau reclaim — layout NGOAI widthIn/padding.
 * [slotWidthFraction] da la layout fraction (>=0, co the >1 overshoot).
 * KHONG map lai raw (tranh double-amplify). Compose width nonnegative.
 */
internal fun statusBarChipOuterOccupiedWidthPx(
    naturalWidthPx: Int,
    slotWidthFraction: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): Int {
    if (naturalWidthPx <= 0) return 0
    if (!slotWidthFraction.isFinite()) return 0
    // densityDpi giu signature; floor 0 — Compose khong am width.
    val f = slotWidthFraction.coerceAtLeast(0f)
    return (naturalWidthPx * f).roundToInt().coerceAtLeast(0)
}

/**
 * Gate updateChipCenterX + frame capture — cam khi slot reclaim / unplaced / dang teo.
 * Bao ve task025 origin (khong day center gia khi child khong place).
 */
internal fun statusBarChipCenterUpdateAllowed(
    pillSlotReclaimed: Boolean,
    slotWidthFraction: Float,
    reportedWidthPx: Int,
): Boolean {
    if (pillSlotReclaimed) return false
    if (reportedWidthPx <= 0) return false
    if (slotWidthFraction < 0.98f) return false
    return true
}

/**
 * Cho phep ghi StatusBarPillFrameTracker? Cam khi slot reclaim / width teo cung owner.
 * Owner doi (A→B) + slot da restore (caller: fraction>=0.98 / !reclaim): chap nhan
 * natural W ke ca B < half A — tranh morph endpoint A-size roi snap B (task-037).
 * Giu freeze 025 — khong overwrite lastObserved bang child shrink cung owner.
 */
internal fun statusBarPillFrameCaptureAllowed(
    pillSlotReclaimed: Boolean,
    reportedWidth: Int,
    lastObservedWidth: Int,
    ownerChanged: Boolean = false,
): Boolean {
    if (pillSlotReclaimed) return false
    if (reportedWidth <= 0) return false
    // Owner moi @full restore: natural size hop le (ngan hon / dai hon A).
    if (ownerChanged) return true
    if (
        lastObservedWidth > 0 &&
            reportedWidth * 2 < lastObservedWidth
    ) {
        return false
    }
    return true
}

/**
 * Chi capture geom tu branch target/settled — khong outgoing AnimatedContent
 * (tranh stale W/H cua A khi dang switch B).
 */
internal fun statusBarPillFrameCaptureFromTransitionBranchAllowed(
    branchKey: String,
    targetKey: String,
): Boolean = branchKey == targetKey

/**
 * Quyet dinh remasure tu cache glass owner-bound (task-038).
 * [allow]=false → defer; KHONG fabricate owner B tren coords A.
 */
internal data class OwnerBoundGlassRemeasureDecision(
    val allow: Boolean,
    val ownerId: String?,
)

/**
 * Chi reuse cache khi branchKey EXACT match target + coords con attached + slot san sang.
 * Key mismatch (A cache vs target B) → defer, giu morph rect cu.
 */
internal fun decideOwnerBoundGlassRemeasure(
    cachedBranchKey: String?,
    cachedOwnerId: String?,
    targetBranchKey: String,
    coordsAttached: Boolean,
    pillCaptureEnabled: Boolean,
    slotWidthFraction: Float,
): OwnerBoundGlassRemeasureDecision {
    if (!pillCaptureEnabled || !coordsAttached) {
        return OwnerBoundGlassRemeasureDecision(allow = false, ownerId = null)
    }
    if (cachedBranchKey == null || cachedOwnerId == null) {
        return OwnerBoundGlassRemeasureDecision(allow = false, ownerId = null)
    }
    if (cachedBranchKey != targetBranchKey) {
        return OwnerBoundGlassRemeasureDecision(allow = false, ownerId = null)
    }
    if (slotWidthFraction < 0.98f) {
        return OwnerBoundGlassRemeasureDecision(allow = false, ownerId = null)
    }
    return OwnerBoundGlassRemeasureDecision(allow = true, ownerId = cachedOwnerId)
}

/**
 * Store glass cache tu OGP — chi TARGET branch; outgoing khong overwrite.
 * Alias gate branch (cung dieu kien key).
 */
internal fun statusBarOwnerBoundGlassStoreAllowed(
    branchKey: String,
    targetKey: String,
): Boolean = statusBarPillFrameCaptureFromTransitionBranchAllowed(branchKey, targetKey)

/**
 * Cache owner-bound testable (khong LayoutCoordinates) — branchKey+ownerId nguyen tu.
 * Mo phong OGP store / LE remasure / invalidate unmount-config.
 */
internal class OwnerBoundGlassCacheState {
    var branchKey: String? = null
        private set
    var ownerId: String? = null
        private set
    var attached: Boolean = false
        private set

    fun clear() {
        branchKey = null
        ownerId = null
        attached = false
    }

    /** Outgoing (branch != target) → false, cache khong doi. */
    fun tryStoreFromBranch(
        branchKey: String,
        targetKey: String,
        ownerId: String,
        attached: Boolean = true,
    ): Boolean {
        if (!attached) return false
        if (!statusBarOwnerBoundGlassStoreAllowed(branchKey, targetKey)) return false
        this.branchKey = branchKey
        this.ownerId = ownerId
        this.attached = true
        return true
    }

    fun markDetached() {
        attached = false
    }

    /**
     * Owner id hop le de remasure — null = defer (khong relabel stale cache thanh target moi).
     */
    fun remasureOwnerIdOrNull(
        targetBranchKey: String,
        pillCaptureEnabled: Boolean,
        slotWidthFraction: Float,
    ): String? {
        val d =
            decideOwnerBoundGlassRemeasure(
                cachedBranchKey = branchKey,
                cachedOwnerId = ownerId,
                targetBranchKey = targetBranchKey,
                coordsAttached = attached,
                pillCaptureEnabled = pillCaptureEnabled,
                slotWidthFraction = slotWidthFraction,
            )
        return if (d.allow) d.ownerId else null
    }
}

/**
 * Bu anisotropic host sx≠sy: child scaleX=1/sx, scaleY=1/sy → man hinh isotropic = pill size.
 * Dummy CONTENT visual: [statusBarMorphDummyLayerScale] theo shell growth — KHONG * spring dummyScale.
 */
internal fun statusBarMorphInverseContentScale(sx: Float, sy: Float): Pair<Float, Float> {
    val safeSx = sx.coerceAtLeast(1e-4f)
    val safeSy = sy.coerceAtLeast(1e-4f)
    return (1f / safeSx) to (1f / safeSy)
}

// Dummy/card alpha + handoff windows → StatusBarMorphTracks.kt (track 2/3, elapsed ms).

// Pill chrome: CHI morph Bezier 500/600 + inverse scale — KHONG spring/prelude/landing.
// (task-015: bo elastic 006/007/010/011; morph/handback ACK giu nguyen.)

/**
 * Row morph glass alpha — MOT owner blur = shell (khong nhan (1-chrome)).
 * Truoc: g*(1-c) → glass tat khi pill opaque du geom lon → nhay mau/blur @handoff.
 * Sau: pass-through shell; pill chrome FG-only (includeBlur=false trong morph).
 * [pillChromeAlpha] giu signature / complementary test — khong giam glass.
 */
internal fun statusBarMorphRowMorphGlassAlpha(
    morphGlassAlpha: Float,
    pillChromeAlpha: Float,
): Float {
    // chrome khong tru glass (tranh gap/flash); param giu API.
    if (pillChromeAlpha < 0f) return 0f
    return morphGlassAlpha.coerceIn(0f, 1f)
}

/**
 * Continuity handoff F741N: dummy+card HOAC shell glass (content gap Samsung OK).
 * Shell-only mid (dummy fade xong, panel chua width-gate) KHONG coi la fail.
 */
internal fun statusBarMorphContentHandoffCoherent(
    dummyAlpha: Float,
    cardAlpha: Float,
    shellAlpha: Float = 0f,
): Boolean {
    if (shellAlpha < 0f) return false
    val d = dummyAlpha.coerceIn(0f, 1f)
    val c = cardAlpha.coerceIn(0f, 1f)
    val s = shellAlpha.coerceIn(0f, 1f)
    if (d + c >= 0.95f) return true
    if (s >= 0.95f) return true
    return d > 0.05f || c > 0.05f
}

/**
 * Quet linearT: moi moc phai cover bang dummy+card HOAC shell glass.
 * 1-owner: shell luon co luc flight (track 4 glass).
 */
internal fun statusBarMorphContentHandoffNoEmptyGap(
    closing: Boolean,
    samples: Int = 21,
): Boolean {
    if (samples < 2) return false
    for (i in 0 until samples) {
        val p = i / (samples - 1).toFloat()
        val dummy = statusBarMorphDummyContentAlpha(p, closing)
        val card = statusBarMorphCardContentAlpha(p, closing)
        val shell = statusBarMorphShellGlassAlpha(p, closing)
        if (!statusBarMorphContentHandoffCoherent(dummy, card, shell)) return false
    }
    return true
}

// --- STATUS BAR morph visual rect (tach envelope layout vs card painted) ---

/**
 * Khop [EXPANDED_BOTTOM_PAD] ExpandedIslandContent / StackedExpandedPanels (50.dp).
 * Layout measure van gom pad (touch); morph glass / sx/sy dung visual = layout − insets.
 */
internal const val StatusBarExpandedBottomPadDp = 50

/** Khop [SpaceLg] start/end StackedExpandedPanels / ExpandedIslandContent (12.dp). */
internal const val StatusBarExpandedSidePadDp = 12

/** Khop [SpaceXxs] top StackedExpandedPanels / ExpandedIslandContent (2.dp). */
internal const val StatusBarExpandedTopPadDp = 2

/**
 * morphVisualTargetW = layoutTargetW − 2*sidePad, coerce ≥ pillW.
 * Envelope (widthIn max) van dung measure/host; shell chi ve card painted ngang.
 * UU TIEN [statusBarMorphResolveVisualTargetWPx] khi co measured painted.
 */
internal fun statusBarMorphVisualTargetWPx(
    layoutTargetWPx: Int,
    sidePadPx: Int,
    pillWPx: Int,
): Int {
    val safePill = pillWPx.coerceAtLeast(1)
    val visual = layoutTargetWPx - 2 * sidePadPx.coerceAtLeast(0)
    return visual.coerceAtLeast(safePill)
}

/**
 * Chon visual W: measured painted top-card (honest) thang side-pad estimate.
 * [paintedWPx] <=0 → fallback strip sidePad (co the lech right-edge).
 */
internal fun statusBarMorphResolveVisualTargetWPx(
    layoutTargetWPx: Int,
    sidePadPx: Int,
    pillWPx: Int,
    paintedWPx: Int,
): Int {
    val safePill = pillWPx.coerceAtLeast(1)
    if (paintedWPx > safePill) {
        return paintedWPx.coerceAtMost(layoutTargetWPx.coerceAtLeast(safePill))
    }
    return statusBarMorphVisualTargetWPx(layoutTargetWPx, sidePadPx, pillWPx)
}

/**
 * Side inset thuc te tu layout vs painted — thay SidePad 12dp khi da do.
 * Khong am; neu chua do → sidePad fallback.
 */
internal fun statusBarMorphResolveSideInsetPx(
    layoutTargetWPx: Int,
    paintedWPx: Int,
    fallbackSidePadPx: Int,
): Int {
    if (paintedWPx > 0 && layoutTargetWPx > paintedWPx) {
        return ((layoutTargetWPx - paintedWPx) / 2).coerceAtLeast(0)
    }
    return fallbackSidePadPx.coerceAtLeast(0)
}

/**
 * Visual H: painted thang strip top+bottom pad khi measured.
 */
internal fun statusBarMorphResolveVisualTargetHPx(
    layoutTargetHPx: Int,
    topPadPx: Int,
    bottomPadPx: Int,
    pillHPx: Int,
    paintedHPx: Int,
): Int {
    val safePill = pillHPx.coerceAtLeast(1)
    if (paintedHPx > safePill) {
        return paintedHPx.coerceAtMost(layoutTargetHPx.coerceAtLeast(safePill))
    }
    return statusBarMorphVisualTargetHPx(layoutTargetHPx, topPadPx, bottomPadPx, pillHPx)
}

/**
 * p=1 shell vs native painted — |Δ| ≤ epsilonPx moi canh (right edge quan trong).
 */
internal fun statusBarMorphShellMatchesPaintedBounds(
    shellLeftPx: Float,
    shellTopPx: Float,
    shellWPx: Float,
    shellHPx: Float,
    paintedLeftPx: Float,
    paintedTopPx: Float,
    paintedWPx: Float,
    paintedHPx: Float,
    epsilonPx: Float = 1f,
): Boolean {
    val eps = epsilonPx.coerceAtLeast(0f)
    if (kotlin.math.abs(shellLeftPx - paintedLeftPx) > eps) return false
    if (kotlin.math.abs(shellTopPx - paintedTopPx) > eps) return false
    if (kotlin.math.abs(shellWPx - paintedWPx) > eps) return false
    if (kotlin.math.abs(shellHPx - paintedHPx) > eps) return false
    val shellRight = shellLeftPx + shellWPx
    val paintedRight = paintedLeftPx + paintedWPx
    return kotlin.math.abs(shellRight - paintedRight) <= eps
}

/**
 * Undo host sx/sy → card LOCAL trong host (KHONG + hostWindow origin).
 *
 * Truoc (sai): left = hostWindowLeft + (windowLeft-hostWindowLeft)/sx
 * → WINDOW ABSOLUTE nhiem translation host dang animate (pill→expanded).
 * Freeze luc host @pill → dest ~source-adjacent → right-edge jump.
 *
 * Sau: localLeft = (windowLeft-hostWindowLeft)/sx — on dinh moi frame host.
 * window*Px trong [StatusBarPaintedCardReportPx] = LOCAL (khong screen).
 * Dest screen = [statusBarMorphPaintedDestFromLocalInTargetHost].
 */
internal fun statusBarMorphPaintedBoundsUndoHostScale(
    windowLeftPx: Float,
    windowTopPx: Float,
    windowRightPx: Float,
    windowBottomPx: Float,
    hostWindowLeftPx: Float,
    hostWindowTopPx: Float,
    hostSx: Float,
    hostSy: Float,
    ownerKey: String = "",
    contextToken: Int = 0,
): StatusBarPaintedCardReportPx {
    val sx = hostSx.coerceAtLeast(1e-4f)
    val sy = hostSy.coerceAtLeast(1e-4f)
    val left = (windowLeftPx - hostWindowLeftPx) / sx
    val top = (windowTopPx - hostWindowTopPx) / sy
    val right = (windowRightPx - hostWindowLeftPx) / sx
    val bottom = (windowBottomPx - hostWindowTopPx) / sy
    val w = (right - left).coerceAtLeast(0f)
    val h = (bottom - top).coerceAtLeast(0f)
    return StatusBarPaintedCardReportPx(
        layoutWPx = w.roundToInt().coerceAtLeast(0),
        layoutHPx = h.roundToInt().coerceAtLeast(0),
        windowLeftPx = left,
        windowTopPx = top,
        windowRightPx = right,
        windowBottomPx = bottom,
        ownerKey = ownerKey,
        contextToken = contextToken,
    )
}

/**
 * TARGET host physical @ rest day du — tu layout constraints, KHONG moving window.
 * Host requiredSize(layoutW×H) can TopCenter: left=(maxW−layoutW)/2, top=statusBar topPad.
 */
internal fun statusBarMorphExpandedTargetHostPlacementPx(
    maxWidthPx: Float,
    layoutTargetWPx: Float,
    topPadPx: Float,
): Pair<Float, Float> {
    val layoutW = layoutTargetWPx.coerceAtLeast(0f)
    val maxW = maxWidthPx.coerceAtLeast(layoutW)
    return ((maxW - layoutW) * 0.5f) to topPadPx.coerceAtLeast(0f)
}

/**
 * Production endpoint assembly: dest SCREEN = targetHostPlacement + localCardBounds.
 * Inject vao test — KHONG tautology shellRectFromPainted.
 * null neu W/H local chua hop le.
 */
internal fun statusBarMorphPaintedDestFromLocalInTargetHost(
    targetHostLeftPx: Float,
    targetHostTopPx: Float,
    localLeftPx: Float,
    localTopPx: Float,
    localRightPx: Float,
    localBottomPx: Float,
): StatusBarMorphVisualRectPx? =
    statusBarMorphPaintedDestinationOrNull(
        paintedLeftPx = targetHostLeftPx + localLeftPx,
        paintedTopPx = targetHostTopPx + localTopPx,
        paintedRightPx = targetHostLeftPx + localRightPx,
        paintedBottomPx = targetHostTopPx + localBottomPx,
    )

/**
 * Freeze painted LRTB atomic: chi khi placement target san + local W/H > 1.
 * Fallback: caller giu dest cu / NaN center — KHONG overwrite bang moving host.
 */
internal fun statusBarMorphPaintedDestReadyForFreeze(
    targetHostPlacementReady: Boolean,
    localLeftPx: Float,
    localTopPx: Float,
    localRightPx: Float,
    localBottomPx: Float,
): Boolean {
    if (!targetHostPlacementReady) return false
    val w = localRightPx - localLeftPx
    val h = localBottomPx - localTopPx
    return w > 1f && h > 1f && localLeftPx.isFinite() && localTopPx.isFinite()
}

/**
 * Chon painted bounds the top (isTop && !isLeaving) — under bi loai.
 * [candidates]: Pair(isSelectedTop, report); null neu khong co top.
 */
internal fun statusBarMorphSelectTopPaintedReport(
    candidates: List<Pair<Boolean, StatusBarPaintedCardReportPx>>,
): StatusBarPaintedCardReportPx? =
    candidates.firstOrNull { it.first }?.second

/**
 * Shell p=1 rect tu painted LRTB host-local (khong tautology shellW==shellW).
 */
internal fun statusBarMorphShellRectFromPaintedHostLocal(
    paintedLeftPx: Float,
    paintedTopPx: Float,
    paintedRightPx: Float,
    paintedBottomPx: Float,
): FloatArray =
    floatArrayOf(
        paintedLeftPx,
        paintedTopPx,
        (paintedRightPx - paintedLeftPx).coerceAtLeast(0f),
        (paintedBottomPx - paintedTopPx).coerceAtLeast(0f),
    )

/**
 * morphVisualTargetH = layoutTargetH − topPad − bottomPad, coerce ≥ pillH.
 * Khong xoa pad layout — chi bo inset khoi target scale/shell.
 */
internal fun statusBarMorphVisualTargetHPx(
    layoutTargetHPx: Int,
    topPadPx: Int,
    bottomPadPx: Int,
    pillHPx: Int,
): Int {
    val safePill = pillHPx.coerceAtLeast(1)
    val visual =
        layoutTargetHPx - topPadPx.coerceAtLeast(0) - bottomPadPx.coerceAtLeast(0)
    return visual.coerceAtLeast(safePill)
}

/** visualH = targetH * sy — cung cong thuc shell va per-card khi target = morphVisual. */
internal fun statusBarMorphVisibleHeightPx(targetHPx: Float, sy: Float): Float =
    targetHPx.coerceAtLeast(1f) * sy.coerceAtLeast(0f)

/** visualW = targetW * sx. */
internal fun statusBarMorphVisibleWidthPx(targetWPx: Float, sx: Float): Float =
    targetWPx.coerceAtLeast(1f) * sx.coerceAtLeast(0f)

/**
 * ΔH khi shell scale theo layoutH (gom pad) roi tat → con content (layout−insets)*sy.
 * Ky vong ≈ (top+bottom)Pad * sy (flicker i29→30 ~159px @ bottom~141, sy~1.13).
 */
internal fun statusBarMorphPadInducedDeltaHPx(bottomPadPx: Float, sy: Float): Float =
    bottomPadPx.coerceAtLeast(0f) * sy.coerceAtLeast(0f)

/**
 * ΔW khi shell dung envelopeW roi handoff → card = envelope − 2*sidePad.
 * Ky vong ≈ 2*sidePad * sx (flash hep @450dpi side~34 → ~68px @ sx=1).
 */
internal fun statusBarMorphSidePadInducedDeltaWPx(sidePadPx: Float, sx: Float): Float =
    2f * sidePadPx.coerceAtLeast(0f) * sx.coerceAtLeast(0f)

/**
 * Sau fix: shellVisible va contentVisible cung morphVisualTarget * s → lien tuc.
 * [epsilonPx] dung cho pure test / gate.
 */
internal fun statusBarMorphShellContentHeightContinuous(
    layoutTargetHPx: Float,
    topPadPx: Float,
    bottomPadPx: Float,
    pillHPx: Float,
    sy: Float,
    epsilonPx: Float = 1f,
): Boolean {
    val visualH =
        statusBarMorphVisualTargetHPx(
                layoutTargetHPx = layoutTargetHPx.roundToInt(),
                topPadPx = topPadPx.roundToInt(),
                bottomPadPx = bottomPadPx.roundToInt(),
                pillHPx = pillHPx.roundToInt(),
            )
            .toFloat()
    val shellH = statusBarMorphVisibleHeightPx(visualH, sy)
    val contentH = statusBarMorphVisibleHeightPx(visualH, sy)
    return kotlin.math.abs(shellH - contentH) <= epsilonPx
}

/**
 * Sau fix: shell W = visualTargetW * sx = (layoutW − 2*side) * sx — khop card ngang.
 */
internal fun statusBarMorphShellContentWidthContinuous(
    layoutTargetWPx: Float,
    sidePadPx: Float,
    pillWPx: Float,
    sx: Float,
    epsilonPx: Float = 1f,
): Boolean {
    val visualW =
        statusBarMorphVisualTargetWPx(
                layoutTargetWPx = layoutTargetWPx.roundToInt(),
                sidePadPx = sidePadPx.roundToInt(),
                pillWPx = pillWPx.roundToInt(),
            )
            .toFloat()
    val shellW = statusBarMorphVisibleWidthPx(visualW, sx)
    val contentW = statusBarMorphVisibleWidthPx(visualW, sx)
    return kotlin.math.abs(shellW - contentW) <= epsilonPx
}

/**
 * Visible card bbox tren SCREEN (sau host scale): interpolate pill → card painted.
 * width/height = visualTarget * sx/sy. p=0 == pill; p=1 painted LRTB hoac center fallback.
 */
internal data class StatusBarMorphVisualRectPx(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
)

/**
 * Dich p=1 tu frozen painted LRTB (production) — null neu chua do.
 * Caller: currentVisualRect / test endpoint ≠ tautology self-copy.
 */
internal fun statusBarMorphPaintedDestinationOrNull(
    paintedLeftPx: Float,
    paintedTopPx: Float,
    paintedRightPx: Float,
    paintedBottomPx: Float,
): StatusBarMorphVisualRectPx? {
    val w = paintedRightPx - paintedLeftPx
    val h = paintedBottomPx - paintedTopPx
    if (!(w > 1f) || !(h > 1f)) return null
    if (!paintedLeftPx.isFinite() || !paintedTopPx.isFinite()) return null
    return StatusBarMorphVisualRectPx(
        leftPx = paintedLeftPx,
        topPx = paintedTopPx,
        widthPx = w,
        heightPx = h,
    )
}

/**
 * Guess center left (maxW − visualW)/2 — dung khi CHUA painted; negative test vs LRTB that.
 */
internal fun statusBarMorphGuessedCenterLeftPx(
    maxWidthPx: Float,
    visualTargetWPx: Float,
): Float = (maxWidthPx - visualTargetWPx) * 0.5f

internal fun statusBarMorphLerpVisualRectPx(
    pOffset: Float,
    pillLeftPx: Float,
    pillTopPx: Float,
    pillWPx: Float,
    pillHPx: Float,
    visualTargetWPx: Float,
    visualTargetHPx: Float,
    sx: Float,
    sy: Float,
    maxWidthPx: Float,
    targetVisualTopPx: Float,
    /**
     * Dich LEFT painted (host-relative). NaN = fallback center (maxW−visualTargetW)/2.
     * Task-034: KHONG center khi da freeze LRTB — tranh right-edge jump.
     */
    paintedTargetLeftPx: Float = Float.NaN,
    /** Dich TOP painted; NaN = [targetVisualTopPx]. */
    paintedTargetTopPx: Float = Float.NaN,
): StatusBarMorphVisualRectPx {
    val vw = statusBarMorphVisibleWidthPx(visualTargetWPx, sx)
    val vh = statusBarMorphVisibleHeightPx(visualTargetHPx, sy)
    val left0 = pillLeftPx
    val top0 = pillTopPx
    // Neo left dich: painted L truoc; khong painted → center on dinh theo visualTargetW (p=1).
    // Truoc: early p vw~pill → left1~center man → drift; task-033 ghi LRTB nhung khong doc.
    val left1 =
        if (paintedTargetLeftPx.isFinite()) {
            paintedTargetLeftPx
        } else {
            statusBarMorphGuessedCenterLeftPx(maxWidthPx, visualTargetWPx)
        }
    val top1 =
        if (paintedTargetTopPx.isFinite()) {
            paintedTargetTopPx
        } else {
            targetVisualTopPx
        }
    val left =
        when {
            pOffset >= 1f -> left1
            pOffset <= 0f -> left0
            else -> morphLerp(left0, left1, pOffset)
        }
    val top =
        when {
            pOffset >= 1f -> top1
            pOffset <= 0f -> top0
            else -> morphLerp(top0, top1, pOffset)
        }
    return StatusBarMorphVisualRectPx(
        leftPx = left,
        topPx = top,
        widthPx = vw,
        heightPx = vh,
    )
}

/**
 * Host origin = visualRect.topLeft − (insetLeft*sx, insetTop*sy).
 * Shell local (insetLeft, insetTop) sau scale → SCREEN = visualRect (khong lech +SpaceLg).
 */
internal fun statusBarMorphHostOriginFromVisualRectPx(
    visualLeftPx: Float,
    visualTopPx: Float,
    insetLeftPx: Float,
    insetTopPx: Float,
    sx: Float,
    sy: Float,
): Pair<Float, Float> {
    val safeSx = sx.coerceAtLeast(MorphScaleFloor)
    val safeSy = sy.coerceAtLeast(MorphScaleFloor)
    return (visualLeftPx - insetLeftPx.coerceAtLeast(0f) * safeSx) to
        (visualTopPx - insetTopPx.coerceAtLeast(0f) * safeSy)
}

/**
 * Frame dau: shell visual rect ≈ pill (p≈0) — W/H/X/Y.
 * [tolerancePx] mac dinh 2px (acceptance task-017).
 */
internal fun statusBarMorphPillShellAlignedRect(
    pillLeftPx: Float,
    pillTopPx: Float,
    pillWPx: Float,
    pillHPx: Float,
    visualLeftPx: Float,
    visualTopPx: Float,
    visualWPx: Float,
    visualHPx: Float,
    progress: Float,
    tolerancePx: Float = 2f,
): Boolean {
    if (progress > 0.02f) return false
    return kotlin.math.abs(visualWPx - pillWPx) <= tolerancePx &&
        kotlin.math.abs(visualHPx - pillHPx) <= tolerancePx &&
        kotlin.math.abs(visualLeftPx - pillLeftPx) <= tolerancePx &&
        kotlin.math.abs(visualTopPx - pillTopPx) <= tolerancePx
}

/**
 * Frame dau: shell visual ≈ pill (p≈0) — moi an pill that (tranh blank/double).
 * Khong timer plateau; chi so sanh bounds + progress.
 */
internal fun statusBarMorphPillShellAligned(
    pillWPx: Float,
    pillHPx: Float,
    visualShellWPx: Float,
    visualShellHPx: Float,
    progress: Float,
    tolerancePx: Float = 4f,
): Boolean {
    if (progress > 0.02f) return false
    return kotlin.math.abs(visualShellWPx - pillWPx) <= tolerancePx &&
        kotlin.math.abs(visualShellHPx - pillHPx) <= tolerancePx
}

/**
 * Dieu target expand cua MutableTransitionState STATUS BAR (pure).
 * current luon bat dau false; chi target=true sau first composition + source rect hop le
 * (hoac fallback timeout) — tranh updateTransition(isExpanded=true) snap progress=1.
 *
 * Endpoint natural (task-012): stage target=false den khi co H/W dich that
 * (hoac timeout freeze last envelope). [allowFallbackWithoutEndpoint] mac dinh false —
 * KHONG bat morph voi targetH=pill roi snap late live (F48).
 */
internal fun statusBarMorphShouldTargetExpanded(
    intentExpanded: Boolean,
    firstCompositionPassed: Boolean,
    hasValidSourceRect: Boolean,
    allowFallbackWithoutRect: Boolean,
    hasMorphEndpoint: Boolean = true,
    allowFallbackWithoutEndpoint: Boolean = false,
): Boolean {
    if (!intentExpanded || !firstCompositionPassed) return false
    if (!(hasValidSourceRect || allowFallbackWithoutRect)) return false
    return hasMorphEndpoint || allowFallbackWithoutEndpoint
}

/**
 * H/W dung trong computeKeyguardMorphGeom cho MOT luot morph STATUS BAR.
 *
 * Invariant (task-012): khi [morphTransitionRunning], endpoint KHONG BAO GIO switch
 * sang late [liveTargetPx] — ke ca [frozenEndpointPx]=0 (tranh F48 khi stage miss).
 * - Expanded at-rest: follow live (owner/content doi H).
 * - Staging (!running && !atRest): prefer frozen, else live de freeze truoc drive.
 * - Running + frozen thieu: 0 → caller giu pill / khong coi la endpoint hop le.
 */
internal fun statusBarMorphRunEndpointPx(
    frozenEndpointPx: Int,
    liveTargetPx: Int,
    pillPx: Int,
    morphTransitionRunning: Boolean,
    morphExpandedAtRest: Boolean,
): Int {
    val live = if (liveTargetPx > pillPx) liveTargetPx else 0
    if (morphExpandedAtRest && live > 0) return live
    // Morph dang chay: chi frozen — CAM return live (frozen=0 van cam).
    if (morphTransitionRunning) {
        return if (frozenEndpointPx > pillPx) frozenEndpointPx else 0
    }
    if (frozenEndpointPx > pillPx) return frozenEndpointPx
    if (live > 0) return live
    return 0
}

/** Endpoint natural hop le de BAT morph (khong dung pill lam dich). */
internal fun statusBarMorphEndpointReady(endpointPx: Int, pillPx: Int): Boolean =
    endpointPx > pillPx

/**
 * Timeout stage: uu tien measured/frozen; het gio moi dung last envelope cung owner.
 * Tra 0 = tiep tuc cho measure — KHONG start morph target=pill.
 */
internal fun statusBarMorphStageTimeoutEndpointPx(
    measuredOrFrozenPx: Int,
    lastExpandedEnvelopePx: Int,
    pillPx: Int,
    timedOut: Boolean,
): Int {
    if (measuredOrFrozenPx > pillPx) return measuredOrFrozenPx
    if (timedOut && lastExpandedEnvelopePx > pillPx) return lastExpandedEnvelopePx
    return 0
}

/**
 * Snapshot H+W atomic truoc khi bat motion — null neu H chua > pill.
 * [widthPx] thieu → dung [fallbackWidthPx] (envelope) de tranh xScale lech.
 */
internal fun statusBarMorphAtomicEndpointSnapshot(
    heightPx: Int,
    widthPx: Int,
    pillHPx: Int,
    pillWPx: Int,
    fallbackWidthPx: Int,
): Pair<Int, Int>? {
    if (heightPx <= pillHPx) return null
    val w =
        when {
            widthPx > pillWPx -> widthPx
            fallbackWidthPx > pillWPx -> fallbackWidthPx
            else -> 0
        }
    if (w <= 0) return null
    return heightPx to w
}

/**
 * Circle-style temp chrome: ring vs glass theo shellLerp (pCorner).
 * Non-circle: ring=0, glass=full shell alpha (giu Row accent-glass).
 *
 * Caller truyen morphShellAlpha THUAN — KHONG nhan expandedBlurAlpha ramp
 * (tranh blank 1+ frame khi pillSuppressed da an pill that).
 */
internal fun statusBarMorphCircleChromeAlphas(
    morphShellAlpha: Float,
    shellLerp: Float,
    useCircleStyle: Boolean,
): Pair<Float, Float> {
    val shell = morphShellAlpha.coerceIn(0f, 1f)
    val lerp = shellLerp.coerceIn(0f, 1f)
    return if (useCircleStyle) {
        (shell * (1f - lerp)) to (shell * lerp)
    } else {
        0f to shell
    }
}

/** Ty le icon / diameter CircleChip REST (16dp / 28dp). */
internal const val StatusBarCircleChipIconFrac = 16f / 28f

/**
 * Kich thuoc shell SAU scale anisotropic: visualW = targetW*sx, visualH = targetH*sy.
 * p=0 circle 28×28 / target 900×400 → sx=28/900, sy=28/400 → visual 28×28 (vuong).
 */
internal fun statusBarMorphVisualShellSizePx(
    targetWPx: Float,
    targetHPx: Float,
    sx: Float,
    sy: Float,
): Pair<Float, Float> =
    (targetWPx.coerceAtLeast(1f) * sx.coerceAtLeast(0f)) to
        (targetHPx.coerceAtLeast(1f) * sy.coerceAtLeast(0f))

/**
 * Duong kinh ring ve tren SCREEN (vuong isotrop) — min(visualW, visualH).
 * Doc lap host sx≠sy: Canvas KHONG nam trong graphicsLayer scale anisotropic.
 */
internal fun statusBarMorphCircleRingVisualDiameterPx(
    visualWPx: Float,
    visualHPx: Float,
): Float = minOf(visualWPx, visualHPx).coerceAtLeast(1f)

/** Top-left square ring centered tren visual shell bounds. */
internal fun statusBarMorphCircleRingVisualTopLeftPx(
    shellLeftPx: Float,
    shellTopPx: Float,
    visualWPx: Float,
    visualHPx: Float,
    diameterPx: Float,
): Pair<Float, Float> {
    val d = diameterPx.coerceAtLeast(1f)
    return (shellLeftPx + (visualWPx - d) * 0.5f) to
        (shellTopPx + (visualHPx - d) * 0.5f)
}

/** Icon apparent size = diameter * 16/28 — khong meo aspect. */
internal fun statusBarMorphCircleRingIconSizePx(diameterPx: Float): Float =
    diameterPx.coerceAtLeast(1f) * StatusBarCircleChipIconFrac

/**
 * Geometry ve ring circle tren SCREEN (sibling ngoai host scale).
 * width==height==diameter; aspect luon 1 — khong ellipse khi sx≠sy.
 */
internal data class StatusBarMorphCircleRingDrawGeom(
    val leftPx: Float,
    val topPx: Float,
    val diameterPx: Float,
    val iconSizePx: Float,
) {
    val widthPx: Float
        get() = diameterPx
    val heightPx: Float
        get() = diameterPx
    /** 1 = vuong; <1 = ellipse ngang (KHONG dung cho ring dung). */
    val aspect: Float
        get() = if (heightPx <= 0f) 1f else widthPx / heightPx
}

/**
 * Tinh bbox ring visual tu pill/target/p + shell top-left (overlay coords).
 * p=0 + circle 28×28/target bat ky: diameter≈28, aspect=1.
 */
internal fun statusBarMorphCircleRingDrawGeom(
    pillWPx: Float,
    pillHPx: Float,
    targetWPx: Float,
    targetHPx: Float,
    pRaw: Float,
    shellLeftPx: Float,
    shellTopPx: Float,
): StatusBarMorphCircleRingDrawGeom {
    val g =
        computeKeyguardMorphGeom(
            pRaw = pRaw,
            pillWPx = pillWPx,
            pillHPx = pillHPx,
            targetWPx = targetWPx,
            targetHPx = targetHPx,
        )
    val (vw, vh) =
        statusBarMorphVisualShellSizePx(
            targetWPx = targetWPx,
            targetHPx = targetHPx,
            sx = g.sx,
            sy = g.sy,
        )
    val d = statusBarMorphCircleRingVisualDiameterPx(vw, vh)
    val (left, top) =
        statusBarMorphCircleRingVisualTopLeftPx(
            shellLeftPx = shellLeftPx,
            shellTopPx = shellTopPx,
            visualWPx = vw,
            visualHPx = vh,
            diameterPx = d,
        )
    return StatusBarMorphCircleRingDrawGeom(
        leftPx = left,
        topPx = top,
        diameterPx = d,
        iconSizePx = statusBarMorphCircleRingIconSizePx(d),
    )
}

/**
 * Co freeze / cap nhat frozen endpoint sau natural measure?
 * - Stage (chua chay morph): cho freeze lan dau khi measured > pill.
 * - Morph dang chay: KHONG doi (ke ca frozen=0) — tranh mid-jump F48.
 * - Expanded at-rest (caller morphTransitionRunning=false): follow.
 */
internal fun statusBarShouldUpdateMorphEndpoint(
    frozenEndpointPx: Int,
    measuredPx: Int,
    pillPx: Int,
    morphTransitionRunning: Boolean,
): Boolean {
    if (measuredPx <= pillPx) return false
    if (morphTransitionRunning) return false
    return true
}

/** Grace go overlay: alert exit 400ms; morph settle + pill handoff chi 50ms. */
internal fun statusBarOverlayHideDelayMs(alertExitGrace: Boolean): Long =
    if (alertExitGrace) 400L else 50L

/**
 * Offset top-left host morph STATUS BAR (neo tren, pivot top-left giong Samsung OA).
 *
 * p=0: dung origin pill (screen→overlay da quy doi). p=1: can giua ngang + [targetTopPx].
 * Khong dung morphOffsetTopLeft (neo day keyguard).
 */
internal fun morphOffsetTopLeftStatusBar(
    sx: Float,
    targetWPx: Float,
    pOffset: Float,
    originLeftPx: Float,
    originTopPx: Float,
    maxWidthPx: Float,
    targetTopPx: Float,
): Pair<Float, Float> {
    // sx giu signature (host scale); left dich ON DINH theo targetW — khong (maxW-vw(p))/2.
    val left0 = originLeftPx
    val top0 = originTopPx
    val left1 = (maxWidthPx - targetWPx) * 0.5f
    val top1 = targetTopPx
    val left =
        when {
            pOffset >= 1f -> left1
            pOffset <= 0f -> left0
            else -> morphLerp(left0, left1, pOffset)
        }
    val top =
        when {
            pOffset >= 1f -> top1
            pOffset <= 0f -> top0
            else -> morphLerp(top0, top1, pOffset)
        }
    return left to top
}

// --- task-037: painted morph-frozen vs selected-rest (atomic owner+LRTB) ---

/**
 * Rect painted gan event owner + measure-context — W/H/LRTB atomic.
 * Tach snapshot MOT morph run khoi selected rest / stack swap.
 */
data class StatusBarOwnedPaintedRectPx(
    val ownerKey: String,
    val contextToken: Int,
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
) {
    val widthPx: Float
        get() = (rightPx - leftPx).coerceAtLeast(0f)
    val heightPx: Float
        get() = (bottomPx - topPx).coerceAtLeast(0f)
    val widthInt: Int
        get() = widthPx.roundToInt()
    val heightInt: Int
        get() = heightPx.roundToInt()

    fun isValidAbovePill(pillWPx: Int, pillHPx: Int): Boolean =
        widthInt > pillWPx.coerceAtLeast(1) && heightInt > pillHPx.coerceAtLeast(1)
}

/** Provenance shell visual — DEBUG/trace; khong claim runtime PASS. */
enum class StatusBarPaintedRectProvenance {
    MorphFrozenRun,
    SelectedRest,
    LiveContinuity,
    None,
}

data class StatusBarShellPaintedRectChoice(
    val rect: StatusBarOwnedPaintedRectPx?,
    val provenance: StatusBarPaintedRectProvenance,
)

/**
 * Reporter stale: under / sai owner / sai context → reject (khong overwrite).
 * [reportContextToken]=0 → bo qua check token (legacy).
 */
fun statusBarAcceptPaintedCardReport(
    reportOwnerKey: String,
    reportContextToken: Int,
    activeOwnerKey: String,
    activeContextToken: Int,
    isSelectedTop: Boolean,
): Boolean {
    if (!isSelectedTop) return false
    if (reportOwnerKey.isEmpty() || activeOwnerKey.isEmpty()) return false
    if (reportOwnerKey != activeOwnerKey) return false
    if (
        reportContextToken != 0 &&
            activeContextToken != 0 &&
            reportContextToken != activeContextToken
    ) {
        return false
    }
    return true
}

/**
 * Co duoc ghi morph-frozen painted?
 * - Expanded at-rest: KHONG (tranh stick the mo dau).
 * - Morph running: chi Capture khi frozen chua valid (fill lan dau / reopen).
 * - Staging (!running && !atRest): Capture (freeze truoc drive).
 */
enum class StatusBarMorphFrozenPaintedWrite {
    Hold,
    Capture,
}

fun statusBarMorphFrozenPaintedWritePolicy(
    morphTransitionRunning: Boolean,
    morphExpandedAtRest: Boolean,
    morphFrozenValid: Boolean,
): StatusBarMorphFrozenPaintedWrite {
    if (morphExpandedAtRest) return StatusBarMorphFrozenPaintedWrite.Hold
    if (morphTransitionRunning) {
        return if (morphFrozenValid) {
            StatusBarMorphFrozenPaintedWrite.Hold
        } else {
            StatusBarMorphFrozenPaintedWrite.Capture
        }
    }
    return StatusBarMorphFrozenPaintedWrite.Capture
}

/**
 * Roi at-rest vao morph (dong sau swap): rebind frozen tu selected hien tai.
 * true → copy selectedRest → morphFrozen (atomic), khong giu frozen mo dau.
 * [selectedRestValidForOwner] PHAI kem active context (xem
 * [statusBarSelectedRestValidForRebind]) — continuity pending KHONG rebind.
 */
fun statusBarMorphFrozenRebindFromSelectedOnLeaveRest(
    wasExpandedAtRest: Boolean,
    morphExpandedAtRest: Boolean,
    selectedRestValidForOwner: Boolean,
): Boolean = wasExpandedAtRest && !morphExpandedAtRest && selectedRestValidForOwner

/**
 * contextToken khop active? Token 0 (legacy) → bo qua check.
 */
fun statusBarOwnedPaintedRectMatchesActiveContext(
    rect: StatusBarOwnedPaintedRectPx?,
    activeContextToken: Int,
): Boolean {
    if (rect == null) return false
    if (activeContextToken == 0 || rect.contextToken == 0) return true
    return rect.contextToken == activeContextToken
}

/**
 * Exact selected-rest/live: owner + active context + W/H > pill.
 * Sai context → null (caller dung continuity, khong mark ready).
 */
fun statusBarOwnedPaintedRectExactForOwner(
    rect: StatusBarOwnedPaintedRectPx?,
    selectedOwnerKey: String,
    activeContextToken: Int,
    pillWPx: Int,
    pillHPx: Int,
): StatusBarOwnedPaintedRectPx? =
    rect?.takeIf {
        it.ownerKey == selectedOwnerKey &&
            it.isValidAbovePill(pillWPx, pillHPx) &&
            statusBarOwnedPaintedRectMatchesActiveContext(it, activeContextToken)
    }

/**
 * Destination do that cho frame vua mo xong va open-glass.
 *
 * Thu tu: selected-rest exact → live-painted exact; chua do thi KHONG ready.
 * Natural/wrapper estimate **khong** truyen vao day — cam doi lot measuredExact
 * (flash shared 990→1012→990 khi seed natural vao selectedRest store).
 */
data class StatusBarMeasuredSelectedDestination(
    /** Surface do that (rest store hoac live painted) — shell/openGlass/ACK. */
    val measuredExact: StatusBarOwnedPaintedRectPx?,
) {
    /** Chi ready khi co surface do that khop owner va context. */
    val isMeasuredReady: Boolean
        get() = measuredExact != null
}

/**
 * SSOT OverlayContent: chon dest measured truoc moi natural estimate.
 * Natural chi dung premeasure/layout ngoai ham nay — khong write selectedRest.
 */
fun statusBarResolveMeasuredSelectedDestination(
    selectedRestOwned: StatusBarOwnedPaintedRectPx?,
    livePaintedOwned: StatusBarOwnedPaintedRectPx?,
    selectedOwnerKey: String,
    activeContextToken: Int,
    pillWPx: Int,
    pillHPx: Int,
): StatusBarMeasuredSelectedDestination {
    val measuredExact =
        statusBarOwnedPaintedRectExactForOwner(
            rect = selectedRestOwned,
            selectedOwnerKey = selectedOwnerKey,
            activeContextToken = activeContextToken,
            pillWPx = pillWPx,
            pillHPx = pillHPx,
        ) ?: statusBarOwnedPaintedRectExactForOwner(
            rect = livePaintedOwned,
            selectedOwnerKey = selectedOwnerKey,
            activeContextToken = activeContextToken,
            pillWPx = pillWPx,
            pillHPx = pillHPx,
        )
    return StatusBarMeasuredSelectedDestination(measuredExact)
}

/**
 * Rebind frozen khi leave rest — chi exact owner+context (khong freeze continuity).
 */
fun statusBarSelectedRestValidForRebind(
    rect: StatusBarOwnedPaintedRectPx?,
    selectedOwnerKey: String,
    activeContextToken: Int,
    pillWPx: Int,
    pillHPx: Int,
): Boolean =
    statusBarOwnedPaintedRectExactForOwner(
        rect = rect,
        selectedOwnerKey = selectedOwnerKey,
        activeContextToken = activeContextToken,
        pillWPx = pillWPx,
        pillHPx = pillHPx,
    ) != null

/**
 * Cap nhat atomic record khi doi owner/context/LRTB (khong chi W/H).
 */
fun statusBarOwnedPaintedRectNeedsAtomicReplace(
    current: StatusBarOwnedPaintedRectPx?,
    candidate: StatusBarOwnedPaintedRectPx,
): Boolean {
    if (current == null) return true
    return current.ownerKey != candidate.ownerKey ||
        current.contextToken != candidate.contextToken ||
        current.leftPx != candidate.leftPx ||
        current.topPx != candidate.topPx ||
        current.rightPx != candidate.rightPx ||
        current.bottomPx != candidate.bottomPx
}

/**
 * Chon rect shell visual: morph-frozen CHI khi morph run/staging;
 * expanded rest → selected owner+context (hoac continuity pending), KHONG frozen mo dau.
 *
 * [activeContextToken]: SelectedRest/exact live PHAI khop; sai context → LiveContinuity
 * (giu W/H tam, KHONG mark ready / KHONG freeze nhu dest moi).
 * MorphFrozenRun: pin snapshot theo rules cu — KHONG validate moving context mid-run
 * (tranh zero/pill snap).
 */
fun statusBarSelectShellPaintedRect(
    morphTransitionRunning: Boolean,
    morphExpandedAtRest: Boolean,
    selectedOwnerKey: String,
    morphFrozen: StatusBarOwnedPaintedRectPx?,
    selectedRest: StatusBarOwnedPaintedRectPx?,
    livePainted: StatusBarOwnedPaintedRectPx?,
    pillWPx: Int,
    pillHPx: Int,
    activeContextToken: Int = 0,
): StatusBarShellPaintedRectChoice {
    fun valid(r: StatusBarOwnedPaintedRectPx?): StatusBarOwnedPaintedRectPx? =
        r?.takeIf { it.isValidAbovePill(pillWPx, pillHPx) }

    val frozen = valid(morphFrozen)
    val rest = valid(selectedRest)
    val live = valid(livePainted)
    val restExact =
        statusBarOwnedPaintedRectExactForOwner(
            rest,
            selectedOwnerKey,
            activeContextToken,
            pillWPx,
            pillHPx,
        )
    val liveExact =
        statusBarOwnedPaintedRectExactForOwner(
            live,
            selectedOwnerKey,
            activeContextToken,
            pillWPx,
            pillHPx,
        )

    if (morphExpandedAtRest) {
        when {
            restExact != null ->
                return StatusBarShellPaintedRectChoice(
                    restExact,
                    StatusBarPaintedRectProvenance.SelectedRest,
                )
            liveExact != null ->
                return StatusBarShellPaintedRectChoice(
                    liveExact,
                    StatusBarPaintedRectProvenance.SelectedRest,
                )
            // Pending: owner moi / context moi chua measure — continuity, khong pill.
            rest != null ->
                return StatusBarShellPaintedRectChoice(
                    rest,
                    StatusBarPaintedRectProvenance.LiveContinuity,
                )
            live != null ->
                return StatusBarShellPaintedRectChoice(
                    live,
                    StatusBarPaintedRectProvenance.LiveContinuity,
                )
            else ->
                return StatusBarShellPaintedRectChoice(
                    null,
                    StatusBarPaintedRectProvenance.None,
                )
        }
    }
    if (morphTransitionRunning) {
        // Pin frozen — khong doi activeContext mid-run.
        return if (frozen != null) {
            StatusBarShellPaintedRectChoice(
                frozen,
                StatusBarPaintedRectProvenance.MorphFrozenRun,
            )
        } else {
            StatusBarShellPaintedRectChoice(null, StatusBarPaintedRectProvenance.None)
        }
    }
    // Staging: prefer frozen (pin), else exact selected/live, else continuity.
    when {
        frozen != null ->
            return StatusBarShellPaintedRectChoice(
                frozen,
                StatusBarPaintedRectProvenance.MorphFrozenRun,
            )
        restExact != null ->
            return StatusBarShellPaintedRectChoice(
                restExact,
                StatusBarPaintedRectProvenance.SelectedRest,
            )
        rest != null ->
            return StatusBarShellPaintedRectChoice(
                rest,
                StatusBarPaintedRectProvenance.LiveContinuity,
            )
        liveExact != null ->
            return StatusBarShellPaintedRectChoice(
                liveExact,
                StatusBarPaintedRectProvenance.SelectedRest,
            )
        live != null ->
            return StatusBarShellPaintedRectChoice(
                live,
                StatusBarPaintedRectProvenance.LiveContinuity,
            )
        else ->
            return StatusBarShellPaintedRectChoice(
                null,
                StatusBarPaintedRectProvenance.None,
            )
    }
}

/** Tao owned rect tu LRTB atomic — null neu W/H khong hop le. */
fun statusBarOwnedPaintedRectOrNull(
    ownerKey: String,
    contextToken: Int,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
): StatusBarOwnedPaintedRectPx? {
    if (ownerKey.isEmpty()) return null
    val dest =
        statusBarMorphPaintedDestinationOrNull(leftPx, topPx, rightPx, bottomPx) ?: return null
    return StatusBarOwnedPaintedRectPx(
        ownerKey = ownerKey,
        contextToken = contextToken,
        leftPx = dest.leftPx,
        topPx = dest.topPx,
        rightPx = dest.leftPx + dest.widthPx,
        bottomPx = dest.topPx + dest.heightPx,
    )
}

/**
 * Rest selected rect tu natural W/H (cache 031-033) + target host — khi painted
 * con bi shell cu constrain. LRTB = host + card-local origin.
 */
fun statusBarSelectedRestRectFromNaturalOrNull(
    ownerKey: String,
    contextToken: Int,
    naturalWPx: Int,
    naturalHPx: Int,
    targetHostLeftPx: Float,
    targetHostTopPx: Float,
    cardLocalLeftPx: Float,
    cardLocalTopPx: Float,
    pillWPx: Int,
    pillHPx: Int,
): StatusBarOwnedPaintedRectPx? {
    if (ownerKey.isEmpty()) return null
    if (naturalWPx <= pillWPx || naturalHPx <= pillHPx) return null
    val left = targetHostLeftPx + cardLocalLeftPx
    val top = targetHostTopPx + cardLocalTopPx
    return statusBarOwnedPaintedRectOrNull(
        ownerKey = ownerKey,
        contextToken = contextToken,
        leftPx = left,
        topPx = top,
        rightPx = left + naturalWPx.toFloat(),
        bottomPx = top + naturalHPx.toFloat(),
    )
}
