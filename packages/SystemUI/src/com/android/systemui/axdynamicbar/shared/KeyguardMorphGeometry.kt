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
 * - Owner doi / chua do owner moi → GIU [lastExpandedHPx] (khong bounce pillH).
 * - Chua tung co envelope → 0 (caller moi fallback pillH: chi first expand).
 */
fun statusBarMorphTargetHeightPx(
    locked: LockedTargetHeight,
    owner: String?,
    lastExpandedHPx: Int,
): Int {
    val matched = effectiveLockedTargetHeightPx(locked, owner)
    if (matched > 0) return matched
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
 * Shell/temp chrome trong morph STATUS BAR — bu [morphExpandContentAlpha].
 * Mo: full khi content alpha~0 (tranh blank 0→0.4); tat dan khi per-card len.
 * Dong: shell tro lai truoc khi pill handoff. Fully expanded → 0 (khong CardBg vinh vien).
 *
 * StatusBar glass that: uu tien [statusBarMorphShellGlassAlpha] (ease, tranh double blur).
 */
internal fun morphTempShellAlpha(expandContentAlpha: Float): Float =
    (1f - expandContentAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)

/**
 * Shell GLASS alpha StatusBar — tach khoi content handoff linear.
 * Open: full den Start; quadratic ve 0 trong [Start, End] (roi nhanh hon card)
 * de PrimaryCard blur khong chong frost shell. p>=End / p1 → 0 (card native).
 * Close: shell len lai (u^2) khi ve pill — card blur tat truoc, khong flash tint.
 * KHONG doi cua so content [0.35, 0.55].
 */
internal fun statusBarMorphShellGlassAlpha(progress: Float, closing: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = StatusBarMorphContentHandoffStart
    val end = StatusBarMorphContentHandoffEnd
    val span = (end - start).coerceAtLeast(1e-4f)
    return if (closing) {
        when {
            p >= end -> 0f
            p <= start -> 1f
            else -> {
                val u = ((end - p) / span).coerceIn(0f, 1f)
                (u * u).coerceIn(0f, 1f)
            }
        }
    } else {
        when {
            p <= start -> 1f
            p >= end -> 0f
            else -> {
                val t = ((p - start) / span).coerceIn(0f, 1f)
                val remain = 1f - t
                (remain * remain).coerceIn(0f, 1f)
            }
        }
    }
}

/**
 * Tint/fallback shell → CardBg: smoothstep tren pCorner — bot nhay mau abrupt.
 */
internal fun statusBarMorphShellTintProgress(shellLerp: Float): Float {
    val t = shellLerp.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * He so blur nested PrimaryCard/Media khi shell glass con — tranh double backdrop.
 * p1 / shell=0 → 1 (card native). Shell manh → giam blur card.
 */
internal fun statusBarMorphCardSurfaceBlurFactor(
    cardContentAlpha: Float,
    shellGlassAlpha: Float,
): Float {
    if (cardContentAlpha <= 0.01f) return 0f
    return (1f - shellGlassAlpha.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}

/**
 * Collapse handback chrome overlay (task-043/044).
 *
 * Truoc 043: hold && !suppressed → 0 ngay — overlay CONTENT α=0 tren window khac
 * luc real pill moi unsuppress → race both-hidden (A→A lan A→B).
 *
 * Sau 043: giu chrome=1 den [liveDrawnAcked] (chip OnDraw / timeout failsafe).
 * Sau 044: staticMode release SAU overlay hide (phase5); early-ack StateFlow latch.
 * Sau 045: phase5 OnDraw wait bounded (≤100ms) — khong lock static vinh vien.
 */
internal fun statusBarMorphCollapseOverlayChromeFactor(
    collapseHandbackHold: Boolean,
    pillSuppressed: Boolean,
    liveDrawnAcked: Boolean = false,
): Float {
    if (!collapseHandbackHold) return 1f
    if (pillSuppressed) return 1f
    // hold + unsuppress: giu dummy den khi live ack (khong α0 truoc real ve).
    if (!liveDrawnAcked) return 1f
    return 0f
}

/**
 * True khi ca hai surface an — bug handback (overlay chrome 0 + live chua ve).
 * Pure gate / test; khong thay sleep.
 */
internal fun statusBarCollapseHandbackBothHidden(
    overlayChromeFactor: Float,
    livePillAlpha: Float,
): Boolean = overlayChromeFactor <= 0.01f && livePillAlpha <= 0.01f

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

    /** Timeout / interrupt failsafe — chrome duoc ve 0, khong stuck host. */
    fun acknowledgeLiveDrawnTimeout(holdGeneration: Int): Boolean {
        if (!holdActive) return false
        if (holdGeneration != this.holdGeneration) return false
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
 * naturalWidthPx = measure child (min 25 + pad); fraction=0 → 0 that (NIC reflow).
 */
internal fun statusBarChipOuterOccupiedWidthPx(
    naturalWidthPx: Int,
    slotWidthFraction: Float,
): Int {
    if (naturalWidthPx <= 0) return 0
    val f = slotWidthFraction.coerceIn(0f, 1f)
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
 * Cua so handoff noi dung STATUS BAR (progress 0..1) — geometry-relative, KHONG 239dp.
 * Dummy di cung overlay den [Start], roi crossfade overlapping/adjacent voi card den [End].
 */
internal const val StatusBarMorphContentHandoffStart = 0.35f
internal const val StatusBarMorphContentHandoffEnd = 0.55f

/**
 * Dummy pill CONTENT alpha trong overlay STATUS BAR.
 * Open: 1 @p0..Start, crossfade → 0 @End; Close: 0 @End..1, crossfade → 1 @Start..0.
 * Khong alias [morphPillContentAlpha] (fade som → gap empty truoc card).
 */
internal fun statusBarMorphDummyContentAlpha(progress: Float, closing: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = StatusBarMorphContentHandoffStart
    val end = StatusBarMorphContentHandoffEnd
    val span = (end - start).coerceAtLeast(1e-4f)
    return if (closing) {
        when {
            p >= end -> 0f
            p <= start -> 1f
            else -> ((end - p) / span).coerceIn(0f, 1f)
        }
    } else {
        when {
            p <= start -> 1f
            p >= end -> 0f
            else -> ((end - p) / span).coerceIn(0f, 1f)
        }
    }
}

/**
 * Card expanded CONTENT alpha — StatusBar morph (tach keyguard [morphExpandContentAlpha]).
 * Open/Close cung cua so [Start, End] voi dummy → complementary, khong empty middle.
 */
internal fun statusBarMorphCardContentAlpha(progress: Float, closing: Boolean): Float {
    val p = progress.coerceIn(0f, 1f)
    val start = StatusBarMorphContentHandoffStart
    val end = StatusBarMorphContentHandoffEnd
    val span = (end - start).coerceAtLeast(1e-4f)
    return if (closing) {
        when {
            p >= end -> 1f
            p <= start -> 0f
            else -> ((p - start) / span).coerceIn(0f, 1f)
        }
    } else {
        when {
            p <= start -> 0f
            p >= end -> 1f
            else -> ((p - start) / span).coerceIn(0f, 1f)
        }
    }
}

/**
 * Bu anisotropic host sx≠sy: child scaleX=1/sx, scaleY=1/sy → man hinh isotropic = pill size.
 */
internal fun statusBarMorphInverseContentScale(sx: Float, sy: Float): Pair<Float, Float> {
    val safeSx = sx.coerceAtLeast(1e-4f)
    val safeSy = sy.coerceAtLeast(1e-4f)
    return (1f / safeSx) to (1f / safeSy)
}

/**
 * Continuity handoff: dummy+card phai cover (sum >= nguong) — KHONG tautology shell>0→true.
 * [shellAlpha] giu signature cu; KHONG bypass gap (shell-only ≠ content handoff).
 */
internal fun statusBarMorphContentHandoffCoherent(
    dummyAlpha: Float,
    cardAlpha: Float,
    shellAlpha: Float = 0f,
): Boolean {
    // shellAlpha khong tham gia — tranh tautology shell>0 → true (task-018 sai).
    if (shellAlpha < 0f) return false
    val d = dummyAlpha.coerceIn(0f, 1f)
    val c = cardAlpha.coerceIn(0f, 1f)
    // Complementary / overlapping: luon co it nhat ~1 kenh noi dung.
    if (d + c >= 0.95f) return true
    // Adjacent edge: mot kenh con >0.
    return d > 0.05f || c > 0.05f
}

/** Quet progress: khong co diem nao dummy+card deu ~0 (empty middle). */
internal fun statusBarMorphContentHandoffNoEmptyGap(
    closing: Boolean,
    samples: Int = 21,
): Boolean {
    if (samples < 2) return false
    for (i in 0 until samples) {
        val p = i / (samples - 1).toFloat()
        val dummy = statusBarMorphDummyContentAlpha(p, closing)
        val card = statusBarMorphCardContentAlpha(p, closing)
        if (dummy + card < 0.05f) return false
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
 * width/height = visualTarget * sx/sy. p=0 == pill; p=1 centered + [targetVisualTopPx].
 */
internal data class StatusBarMorphVisualRectPx(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
)

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
): StatusBarMorphVisualRectPx {
    val vw = statusBarMorphVisibleWidthPx(visualTargetWPx, sx)
    val vh = statusBarMorphVisibleHeightPx(visualTargetHPx, sy)
    val left0 = pillLeftPx
    val top0 = pillTopPx
    val left1 = (maxWidthPx - vw) * 0.5f
    val top1 = targetVisualTopPx
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
    val visualW = targetWPx * sx.coerceAtLeast(MorphScaleFloor)
    val left0 = originLeftPx
    val top0 = originTopPx
    val left1 = (maxWidthPx - visualW) * 0.5f
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
