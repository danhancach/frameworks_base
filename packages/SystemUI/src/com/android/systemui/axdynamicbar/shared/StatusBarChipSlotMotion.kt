/*
 * Copyright 2025-2026 AxionOS
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

import android.app.PendingIntent
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.StatusBarNicSlotReflowSuspend
import com.android.systemui.statusbar.phone.NotificationIconContainer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Neighbor tracks STATUS BAR (G991N One UI7 ChipAnimationController) — DOC LAP card morph.
 *
 * Tracks:
 * - Clock nudge: two-phase spring (khong share finish voi slot)
 * - Slot/NIC width reflow: CARD vs APP interpolator rieng, 500ms
 * - Pill app show: alpha + scale spring (khong Material 0.8)
 *
 * Geometry card 500/600 [StatusBarMorphTracks] KHONG doi o day.
 *
 * Evidence: samsung-neighbors-app-visibility-runtime-20261103.md
 * APK SHA 4a221c9c… G991N — khong nham F741N.
 */

/** Huong / nguyen nhan neighbor motion — mot trigger / controller. */
enum class StatusBarNeighborTrigger {
    /** Mo the Dynamic Bar — slot reclaim 1→0. */
    CARD_OPEN,
    /** Dong the — slot restore 0→1. */
    CARD_CLOSE,
    /** Roi owner app — pill hien lai. */
    APP_SHOW,
    /** Vao owner app — pill an (GONE + slot). */
    APP_HIDE,
}

/**
 * Request neighbor motion tu ownership decision (Expansion) — Chip KHONG infer
 * trigger tu destination boolean (tranh APP_SHOW → CARD_CLOSE khi FG clear truoc reclaim).
 *
 * [sequence] monotonic; [previous*] la snapshot truoc mutate.
 */
data class StatusBarNeighborMotionRequest(
    val sequence: Long,
    val trigger: StatusBarNeighborTrigger,
    val reclaiming: Boolean,
    val previousReclaiming: Boolean,
    val previousOwnerAppFgHidden: Boolean,
    val previousPillSuppressed: Boolean,
    val ownerAppFgHidden: Boolean,
    val pillSuppressed: Boolean,
)

/**
 * Trigger khi owner FG hide flag doi (production decision) — KHONG dung
 * [statusBarNeighborTriggerOf] voi destination boolean sau khi flag da clear.
 */
internal fun statusBarNeighborTriggerForOwnerFgChange(hiddenNow: Boolean): StatusBarNeighborTrigger =
    if (hiddenNow) StatusBarNeighborTrigger.APP_HIDE else StatusBarNeighborTrigger.APP_SHOW

/** CARD morph slot reclaim/restore — tach APP. */
internal fun statusBarNeighborTriggerForCardSlot(reclaiming: Boolean): StatusBarNeighborTrigger =
    if (reclaiming) StatusBarNeighborTrigger.CARD_OPEN else StatusBarNeighborTrigger.CARD_CLOSE

/**
 * Co nen emit request khi doi slot reclaim / cung reclaim nhung doi trigger cause.
 * Tranh duplicate tu 2 effect doc lap; khong mat same-reclaiming cause change.
 *
 * Layout seed / sync-without-request KHONG qua day — dung
 * [StatusBarNeighborMotionEmitter.syncChipSlotReclaimed].
 */
internal fun statusBarNeighborMotionShouldEmit(
    previousReclaiming: Boolean,
    nextReclaiming: Boolean,
    previousTrigger: StatusBarNeighborTrigger?,
    nextTrigger: StatusBarNeighborTrigger,
): Boolean {
    if (previousReclaiming != nextReclaiming) return true
    // Cung reclaim: chi emit khi cause/trigger doi (vd. APP_HIDE → CARD_OPEN).
    return previousTrigger != null && previousTrigger != nextTrigger
}

/**
 * Production emitter — Expansion delegate; test goi truc tiep setOwnerAppFgHidden
 * (true→false → APP_SHOW) ma khong infer triggerOf(false,true) gia.
 */
internal class StatusBarNeighborMotionEmitter {
    private val seq = java.util.concurrent.atomic.AtomicLong(0L)
    private var lastEmittedTrigger: StatusBarNeighborTrigger? = null

    var chipSlotReclaimed: Boolean = false
        private set
    var ownerAppFgHidden: Boolean = false
        private set
    var pillSuppressed: Boolean = false
        private set

    var latestRequest: StatusBarNeighborMotionRequest? = null
        private set

    fun setPillSuppressed(suppressed: Boolean) {
        pillSuppressed = suppressed
    }

    /**
     * Dong bo reclaim flag KHONG emit neighbor request.
     * Dung cho expand() layout seed va notifyCollapseSettled (collapse da emit).
     */
    fun syncChipSlotReclaimed(reclaiming: Boolean) {
        chipSlotReclaimed = reclaiming
    }

    fun applyChipSlotReclaimed(
        reclaiming: Boolean,
        trigger: StatusBarNeighborTrigger,
        previousOwnerAppFgHidden: Boolean = ownerAppFgHidden,
        previousPillSuppressed: Boolean = pillSuppressed,
    ): StatusBarNeighborMotionRequest? {
        val prevReclaim = chipSlotReclaimed
        val shouldEmit =
            statusBarNeighborMotionShouldEmit(
                previousReclaiming = prevReclaim,
                nextReclaiming = reclaiming,
                previousTrigger = lastEmittedTrigger,
                nextTrigger = trigger,
            )
        chipSlotReclaimed = reclaiming
        if (!shouldEmit) return null
        val req =
            StatusBarNeighborMotionRequest(
                sequence = seq.incrementAndGet(),
                trigger = trigger,
                reclaiming = reclaiming,
                previousReclaiming = prevReclaim,
                previousOwnerAppFgHidden = previousOwnerAppFgHidden,
                previousPillSuppressed = previousPillSuppressed,
                ownerAppFgHidden = ownerAppFgHidden,
                pillSuppressed = pillSuppressed,
            )
        lastEmittedTrigger = trigger
        latestRequest = req
        return req
    }

    /**
     * Production owner FG path — trigger tu huong doi, khong doc destination boolean
     * sau khi flag da clear (P1A).
     */
    fun setOwnerAppFgHidden(hidden: Boolean): StatusBarNeighborMotionRequest? {
        if (ownerAppFgHidden == hidden) return null
        val prevFg = ownerAppFgHidden
        val prevSup = pillSuppressed
        val trigger = statusBarNeighborTriggerForOwnerFgChange(hidden)
        ownerAppFgHidden = hidden
        if (pillSuppressed) return null
        return applyChipSlotReclaimed(
            reclaiming = hidden,
            trigger = trigger,
            previousOwnerAppFgHidden = prevFg,
            previousPillSuppressed = prevSup,
        )
    }
}

/** Slot ChangeBounds duration — Samsung 500 ca CARD va APP (log ~506–510). */
internal const val StatusBarChipSlotDurationMs = 500

/** Legacy alias — CARD/APP deu 500; morph collapse van 600 rieng. */
internal const val StatusBarChipSlotHideMs = StatusBarChipSlotDurationMs

/** Legacy alias — KHONG con = MorphCollapseMs 600. */
internal const val StatusBarChipSlotShowMs = StatusBarChipSlotDurationMs

/**
 * CARD OPEN (expand/reclaim): ChipAnimationController CARD_TRANSITION_EXPAND
 * = CardStackView.expandInterpolator (0.34, 1.45, 0.64, 1) G991N.
 */
internal val StatusBarChipSlotCardExpandEasing: Easing =
    CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)

/**
 * CARD CLOSE (collapse/show): CARD_TRANSITION_COLLAPSE
 * = collapseInterpolator (0.34, 1.16, 0.64, 1).
 */
internal val StatusBarChipSlotCardCollapseEasing: Easing =
    CubicBezierEasing(0.34f, 1.16f, 0.64f, 1f)

/**
 * APP SHOW/HIDE slot: CHANGE_BOUND_INTERPOLATOR (0.33, 1, 0.68, 1).
 * Cung AlPHA_INTERPOLATOR pill app-show.
 */
internal val StatusBarChipSlotAppEasing: Easing =
    CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** View PathInterpolator cung he so Compose easing. */
internal val StatusBarChipSlotAppPathInterpolator =
    PathInterpolator(0.33f, 1f, 0.68f, 1f)

/** Density mac dinh Sony pdx237. */
internal const val StatusBarChipSlotDefaultDensityDpi = 450f

internal const val StatusBarClockNudgeDp = 2f

/** Spring phase1: toi -2dp (LTR). */
internal const val StatusBarClockNudgeSpring1Stiffness = 400f
internal const val StatusBarClockNudgeSpring1Damping = 0.85f

/** Spring phase2: ve 0. */
internal const val StatusBarClockNudgeSpring2Stiffness = 100f
internal const val StatusBarClockNudgeSpring2Damping = 0.45f

/**
 * Compose Animatable Float mac dinh visibilityThreshold=0.01 — khac
 * DynamicAnimation.TRANSLATION_X MIN_VISIBLE_CHANGE_PIXELS=1f → duration spring lech.
 * Adopt pixel-space termination giong classic View path.
 * API: androidx.dynamicanimation DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS (=1f).
 * DA valueThreshold = minVisible * 0.75; Compose FloatSpringSpec dung visibilityThreshold
 * displacement settle (khong nhan 0.75) — van 1px spatial, khong force 366ms.
 */
internal const val StatusBarClockNudgePixelVisibilityThreshold = 1f

/** Pill app-show scale spring (ChipAnimationController.animateSpring). */
internal const val StatusBarPillAppShowScaleStiffness = 75f
internal const val StatusBarPillAppShowScaleDamping = 0.808f

/** Pill app-show alpha duration (= slot 500). */
internal const val StatusBarPillAppShowAlphaMs = StatusBarChipSlotDurationMs

/**
 * Legacy — one-shot push fraction / gain4 / 2dp cap / shared-finish.
 * Giu hang so de test cu compile fail ro; production khong dung.
 */
@Deprecated("task-007: two-phase spring — khong one-shot push fraction")
internal const val StatusBarClockNudgePushFraction = 0.45f

@Deprecated("task-007: bo 2dp cap / gain4 — curve overshoot duoc phep")
internal const val StatusBarChipSlotOvershootCapDp = 2f

@Deprecated("task-007: removed from layout map")
internal const val StatusBarChipSlotExpandReboundGain = 2.5f

@Deprecated("task-007: removed")
internal const val StatusBarChipSlotExpandReboundMax = 0.04f

@Deprecated("task-007: remove collapse gain4")
internal const val StatusBarChipSlotCollapseOvershootGain = 4f

@Deprecated("task-007: remove soft max / shared-finish coupling")
internal const val StatusBarChipSlotCollapseOvershootMax = 0.04f

@Deprecated("task-007")
internal const val StatusBarChipSlotFractionMax = 1.04f

@Deprecated("Dung StatusBarChipSlotExpandReboundGain")
internal const val StatusBarChipSlotCollapseReboundGain = 2.5f

@Deprecated("Dung StatusBarChipSlotExpandReboundMax")
internal const val StatusBarChipSlotCollapseReboundMax = 0.04f

/** Legacy geometry easing alias — CARD collapse path. */
@Deprecated("Dung StatusBarChipSlotCardExpand/CollapseEasing theo trigger")
internal val StatusBarChipSlotReflowEasing: Easing = StatusBarChipSlotCardCollapseEasing

/** Nudge clock? CARD open/close + APP show; APP hide = khong. */
internal fun statusBarClockNudgeEligible(trigger: StatusBarNeighborTrigger): Boolean =
    when (trigger) {
        StatusBarNeighborTrigger.CARD_OPEN,
        StatusBarNeighborTrigger.CARD_CLOSE,
        StatusBarNeighborTrigger.APP_SHOW,
        -> true
        StatusBarNeighborTrigger.APP_HIDE -> false
    }

/** reclaiming=true → CARD_OPEN / APP_HIDE (slot 1→0); false → show (0→1). */
internal fun statusBarNeighborTriggerOf(
    reclaiming: Boolean,
    appForeground: Boolean,
): StatusBarNeighborTrigger =
    when {
        appForeground && reclaiming -> StatusBarNeighborTrigger.APP_HIDE
        appForeground && !reclaiming -> StatusBarNeighborTrigger.APP_SHOW
        reclaiming -> StatusBarNeighborTrigger.CARD_OPEN
        else -> StatusBarNeighborTrigger.CARD_CLOSE
    }

internal fun statusBarChipSlotDurationMs(
    trigger: StatusBarNeighborTrigger = StatusBarNeighborTrigger.CARD_CLOSE,
): Int = StatusBarChipSlotDurationMs

/** Legacy API: reclaiming → hide/show cung 500. */
internal fun statusBarChipSlotDurationMs(reclaiming: Boolean): Int = StatusBarChipSlotDurationMs

internal fun statusBarChipSlotEasingFor(trigger: StatusBarNeighborTrigger): Easing =
    when (trigger) {
        StatusBarNeighborTrigger.CARD_OPEN -> StatusBarChipSlotCardExpandEasing
        StatusBarNeighborTrigger.CARD_CLOSE -> StatusBarChipSlotCardCollapseEasing
        StatusBarNeighborTrigger.APP_SHOW,
        StatusBarNeighborTrigger.APP_HIDE,
        -> StatusBarChipSlotAppEasing
    }

/** Dich slot: reclaim → 0; restore/show → 1. */
internal fun statusBarChipSlotSegmentTarget(reclaiming: Boolean): Float =
    if (reclaiming) 0f else 1f

/**
 * Segment continuity: fraction = start + (target - start) * easing(t).
 * [start] = rendered fraction hien tai (overshoot >1 / midflight OK).
 * [target] = 0 hoac 1. Duration van 500ms (t 0→1); tradeoff: full easing span
 * tu start bat ky — khong inverse curve / approximate nearest t.
 */
internal fun statusBarChipSlotSegmentFractionAt(
    start: Float,
    target: Float,
    t: Float,
    trigger: StatusBarNeighborTrigger,
): Float {
    if (!start.isFinite() || !target.isFinite()) return 0f
    val tt = t.coerceIn(0f, 1f)
    val eased = statusBarChipSlotEasingFor(trigger).transform(tt)
    return start + (target - start) * eased
}

/**
 * Rendered fraction cho continuity — floor 0 (raw am khong paint), giu overshoot >1.
 * KHONG dung raw am lam start segment.
 */
internal fun statusBarChipSlotRenderedFraction(rawOrLayout: Float): Float =
    statusBarChipSlotWidthFractionForLayout(rawOrLayout)

/**
 * Start khi natural width doi: giu pixel width cu / natural moi (rounding px).
 * Cung natural → [currentRenderedFraction] (da floor).
 */
internal fun statusBarChipSlotSegmentStartForNaturalWidthChange(
    currentRenderedFraction: Float,
    previousNaturalPx: Float,
    nextNaturalPx: Float,
): Float {
    val rendered = statusBarChipSlotRenderedFraction(currentRenderedFraction)
    if (!(previousNaturalPx > 0f) || !previousNaturalPx.isFinite()) return rendered
    if (!(nextNaturalPx > 0f) || !nextNaturalPx.isFinite()) return rendered
    if (previousNaturalPx == nextNaturalPx) return rendered
    return (rendered * previousNaturalPx) / nextNaturalPx
}

/**
 * Endpoint wrapper: reclaim start=1→0; show start=0→1.
 * Tuong duong segment khi start dung endpoint (MorphGeometryTest / legacy).
 */
internal fun statusBarChipSlotRawFractionAt(
    reclaiming: Boolean,
    t: Float,
    trigger: StatusBarNeighborTrigger =
        if (reclaiming) StatusBarNeighborTrigger.CARD_OPEN
        else StatusBarNeighborTrigger.CARD_CLOSE,
): Float {
    val start = if (reclaiming) 1f else 0f
    return statusBarChipSlotSegmentFractionAt(
        start = start,
        target = statusBarChipSlotSegmentTarget(reclaiming),
        t = t,
        trigger = trigger,
    )
}

/**
 * @deprecated task-009: curve inversion / nearest-t — dung segment start+(target-start)*easing.
 * Giu stub cho test migrate; production Chip khong goi.
 */
@Deprecated("task-009: segment continuity — khong inverse curve")
internal fun statusBarChipSlotLinearTNearRawFraction(
    reclaiming: Boolean,
    targetRaw: Float,
    trigger: StatusBarNeighborTrigger,
    samples: Int = 48,
): Float {
    if (!targetRaw.isFinite()) return 0f
    var bestT = 0f
    var bestErr = Float.POSITIVE_INFINITY
    val n = samples.coerceAtLeast(8)
    for (i in 0..n) {
        val t = i.toFloat() / n.toFloat()
        val raw = statusBarChipSlotRawFractionAt(reclaiming, t, trigger)
        val err = abs(raw - targetRaw)
        if (err < bestErr) {
            bestErr = err
            bestT = t
        }
    }
    var lo = (bestT - 1f / n).coerceAtLeast(0f)
    var hi = (bestT + 1f / n).coerceAtMost(1f)
    repeat(12) {
        val mid = (lo + hi) * 0.5f
        val rawLo = statusBarChipSlotRawFractionAt(reclaiming, lo, trigger)
        val rawHi = statusBarChipSlotRawFractionAt(reclaiming, hi, trigger)
        val rawMid = statusBarChipSlotRawFractionAt(reclaiming, mid, trigger)
        val errMid = abs(rawMid - targetRaw)
        if (errMid < bestErr) {
            bestErr = errMid
            bestT = mid
        }
        if (abs(rawLo - targetRaw) <= abs(rawHi - targetRaw)) {
            hi = mid
        } else {
            lo = mid
        }
    }
    return bestT.coerceIn(0f, 1f)
}

/**
 * Layout width fraction — nonnegative (Compose width khong am).
 * Cho phep overshoot >1 theo curve; clamp floor 0.
 * KHONG gain4 / 2dp cap (task-007 remove).
 */
internal fun statusBarChipSlotWidthFractionForLayout(
    rawFraction: Float,
    slotWidthPx: Float = Float.POSITIVE_INFINITY,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): Float {
    // slotWidthPx / densityDpi giu signature call-site; khong cap theo 2dp.
    if (!rawFraction.isFinite()) return 0f
    return rawFraction.coerceAtLeast(0f)
}

/** true khi raw am — layout clamp 0. */
internal fun statusBarChipSlotCollapseInRebound(rawFraction: Float): Boolean = rawFraction < 0f

/** true khi raw>1 (overshoot show). */
internal fun statusBarChipSlotCollapseInOvershoot(rawFraction: Float): Boolean = rawFraction > 1f

/**
 * Overshoot delta px khi layout>1. EXPAND reclaim: 0 neu raw layout<=1 sau clamp.
 * Khong con cap 2dp.
 */
internal fun statusBarChipSlotOvershootDeltaPx(
    reclaiming: Boolean,
    t: Float,
    slotWidthPx: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
    trigger: StatusBarNeighborTrigger =
        if (reclaiming) StatusBarNeighborTrigger.CARD_OPEN
        else StatusBarNeighborTrigger.CARD_CLOSE,
): Float {
    val raw = statusBarChipSlotRawFractionAt(reclaiming, t, trigger)
    val layout = statusBarChipSlotWidthFractionForLayout(raw, slotWidthPx, densityDpi)
    return if (layout > 1f) (layout - 1f) * slotWidthPx.coerceAtLeast(0f) else 0f
}

@Deprecated("task-007: bo 2dp cap")
internal fun statusBarChipSlotOvershootCapFraction(
    slotWidthPx: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): Float {
    if (!(slotWidthPx > 0f) || !slotWidthPx.isFinite()) return Float.POSITIVE_INFINITY
    val dpi = densityDpi.coerceAtLeast(1f)
    return (StatusBarClockNudgeDp * dpi / 160f / slotWidthPx).coerceAtLeast(0f)
}

@Deprecated("task-007: clock spring doc lap — khong adaptive NIC remaining")
internal fun statusBarClockNudgeReturnRemaining(u: Float): Float {
    val x = u.coerceIn(0f, 1f)
    return (1f - x * x * (2f - x)).coerceIn(0f, 1f)
}

@Deprecated("task-007: removed adaptive coupling")
internal fun statusBarChipSlotNicRemainingPx(
    reclaiming: Boolean,
    linearT: Float,
    slotWidthPx: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): Float {
    val t = linearT.coerceIn(0f, 1f)
    val w = slotWidthPx.coerceAtLeast(1f)
    val raw = statusBarChipSlotRawFractionAt(reclaiming, t)
    val layout = statusBarChipSlotWidthFractionForLayout(raw, slotWidthPx, densityDpi)
    val nicRest = if (reclaiming) 0f else 1f
    return abs(layout - nicRest) * w
}

/**
 * @deprecated One-shot sample — chi con cho test legacy migrate.
 * Production dung [StatusBarClockNudgeController] spring 2 pha.
 */
@Deprecated("task-007: two-phase spring controller")
internal fun statusBarClockNudgeTranslationXAt(
    t: Float,
    amplitudePx: Float,
    nicRemainingPx: Float = Float.NaN,
): Float {
    // Approximate envelope for migrated tests — NOT production driver.
    val tt = t.coerceIn(0f, 1f)
    val pushEnd = 0.45f
    val target = -amplitudePx
    if (tt <= pushEnd) {
        val u = (tt / pushEnd).coerceIn(0f, 1f)
        val s = u * u * (3f - 2f * u)
        return target * s
    }
    val u = ((tt - pushEnd) / (1f - pushEnd)).coerceIn(0f, 1f)
    return target * (1f - u)
}

internal fun statusBarClockNudgeAmplitudePx(
    density: android.util.DisplayMetrics,
    layoutDirection: Int,
): Float {
    var dx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            StatusBarClockNudgeDp,
            density,
        )
    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        dx = -dx
    }
    return dx
}

internal fun statusBarClockShouldNudge(clock: View?): Boolean {
    if (clock == null) return false
    if (!clock.isAttachedToWindow) return false
    if (clock.visibility != View.VISIBLE) return false
    if (clock.width <= 0 && clock.measuredWidth <= 0) return false
    return true
}

internal fun findStatusBarNotificationIconContainer(from: View): NotificationIconContainer? {
    val root = from.rootView ?: from
    return root.findViewById(R.id.notificationIcons)
}

internal fun findStatusBarClockView(from: View): View? {
    val root = from.rootView ?: from
    return root.findViewById(R.id.clock)
}

// --- Owner foreground visibility (Media / Timer / Stopwatch) ---

/**
 * Home-type / launcher / SystemUI → clear FG (pill restore).
 * Dung chung AppTrackingIslandManager + test — khong poll.
 * @param topActivityType WindowConfiguration.ACTIVITY_TYPE_* (HOME=2)
 * Display filter: [statusBarAppFgDisplayEligible] — caller bat buoc.
 */
internal fun statusBarOwnerForegroundIsHomeOrSystemUi(
    topActivityType: Int,
    packageName: String?,
    launcherPackage: String?,
): Boolean {
    // ACTIVITY_TYPE_HOME = 2 (WindowConfiguration) — tranh import framework o test helper.
    if (topActivityType == 2) return true
    val pkg = packageName ?: return false
    if (launcherPackage != null && pkg == launcherPackage) return true
    return pkg == "com.android.systemui" ||
        pkg == "com.android.launcher3" ||
        pkg == "com.google.android.apps.nexuslauncher"
}

/** Display.DEFAULT_DISPLAY — secondary / INVALID khong doi pill statusbar chinh. */
internal const val StatusBarAppFgDefaultDisplayId = 0

/** Chi default display; reject secondary (>0) va invalid/unassigned (<0). */
internal fun statusBarAppFgDisplayEligible(displayId: Int): Boolean =
    displayId == StatusBarAppFgDefaultDisplayId

/**
 * FG ownership vs recorded switch/history — tach dedupe (review010 blocker1).
 * [foregroundPkg] = pill owner; [lastRecordedSwitchPkg] = pkg da ghi recent/switch.
 */
internal data class AppTrackingFgOwnerState(
    val foregroundPkg: String?,
    val lastRecordedSwitchPkg: String?,
)

/** Input task callback — khong can RunningTaskInfo trong test. */
internal data class AppTrackingFgTaskInput(
    val displayId: Int,
    val topActivityType: Int,
    val packageName: String?,
    val taskId: Int,
    val recordAppSwitch: Boolean,
)

internal enum class AppTrackingFgApplyKind {
    SKIP_DISPLAY,
    CLEAR_HOME_OR_SYSTEM_UI,
    SKIP_NO_PACKAGE,
    OWNER_UNCHANGED,
    UPDATE_OWNER,
}

/**
 * Ket qua reducer production — AppTrackingIslandManager apply + test listener order.
 * [shouldRecordSwitch] co the true ke ca khi OWNER_UNCHANGED (reconcile B roi front B).
 */
internal data class AppTrackingFgApplyResult(
    val kind: AppTrackingFgApplyKind,
    val nextState: AppTrackingFgOwnerState,
    val shouldRecordSwitch: Boolean,
    val leavingPkgForSwitch: String?,
)

/**
 * Production FG reducer: ownership dedupe ≠ recorded-switch dedupe.
 * Reconcile (record=false) chi doi foregroundPkg; front (record=true) ghi history
 * khi pkg != lastRecordedSwitchPkg — ke ca ownership da dung (stack truoc MovedToFront).
 */
internal fun statusBarAppFgApplyTask(
    state: AppTrackingFgOwnerState,
    task: AppTrackingFgTaskInput,
    launcherPackage: String?,
    emitAppSwitch: Boolean,
    hasLauncherIntent: Boolean,
): AppTrackingFgApplyResult {
    if (!statusBarAppFgDisplayEligible(task.displayId)) {
        return AppTrackingFgApplyResult(
            kind = AppTrackingFgApplyKind.SKIP_DISPLAY,
            nextState = state,
            shouldRecordSwitch = false,
            leavingPkgForSwitch = null,
        )
    }
    if (
        statusBarOwnerForegroundIsHomeOrSystemUi(
            topActivityType = task.topActivityType,
            packageName = task.packageName,
            launcherPackage = launcherPackage,
        )
    ) {
        return AppTrackingFgApplyResult(
            kind = AppTrackingFgApplyKind.CLEAR_HOME_OR_SYSTEM_UI,
            nextState =
                AppTrackingFgOwnerState(
                    foregroundPkg = null,
                    lastRecordedSwitchPkg = state.lastRecordedSwitchPkg,
                ),
            shouldRecordSwitch = false,
            leavingPkgForSwitch = null,
        )
    }
    val pkg =
        task.packageName
            ?: return AppTrackingFgApplyResult(
                kind = AppTrackingFgApplyKind.SKIP_NO_PACKAGE,
                nextState = state,
                shouldRecordSwitch = false,
                leavingPkgForSwitch = null,
            )
    val ownerChanged = pkg != state.foregroundPkg
    val stateWithOwner =
        AppTrackingFgOwnerState(
            foregroundPkg = pkg,
            lastRecordedSwitchPkg = state.lastRecordedSwitchPkg,
        )
    if (!task.recordAppSwitch) {
        return if (ownerChanged) {
            AppTrackingFgApplyResult(
                kind = AppTrackingFgApplyKind.UPDATE_OWNER,
                nextState = stateWithOwner,
                shouldRecordSwitch = false,
                leavingPkgForSwitch = null,
            )
        } else {
            AppTrackingFgApplyResult(
                kind = AppTrackingFgApplyKind.OWNER_UNCHANGED,
                nextState = state,
                shouldRecordSwitch = false,
                leavingPkgForSwitch = null,
            )
        }
    }
    // MovedToFront: ghi switch khi chua record pkg (tach khoi FG ownership dedupe).
    val shouldRecord =
        emitAppSwitch && hasLauncherIntent && pkg != state.lastRecordedSwitchPkg
    if (!shouldRecord) {
        return if (ownerChanged) {
            AppTrackingFgApplyResult(
                kind = AppTrackingFgApplyKind.UPDATE_OWNER,
                nextState = stateWithOwner,
                shouldRecordSwitch = false,
                leavingPkgForSwitch = null,
            )
        } else {
            AppTrackingFgApplyResult(
                kind = AppTrackingFgApplyKind.OWNER_UNCHANGED,
                nextState = state,
                shouldRecordSwitch = false,
                leavingPkgForSwitch = null,
            )
        }
    }
    val leaving = state.lastRecordedSwitchPkg
    val next =
        AppTrackingFgOwnerState(foregroundPkg = pkg, lastRecordedSwitchPkg = pkg)
    return AppTrackingFgApplyResult(
        kind =
            if (ownerChanged) AppTrackingFgApplyKind.UPDATE_OWNER
            else AppTrackingFgApplyKind.OWNER_UNCHANGED,
        nextState = next,
        shouldRecordSwitch = true,
        leavingPkgForSwitch = leaving,
    )
}

/**
 * Package owner de so FG — packageName + contentIntent destination/creator.
 * KHONG send intent.
 */
internal fun islandEventOwnerPackages(event: IslandEvent): Set<String> {
    val out = linkedSetOf<String>()
    when (event) {
        is IslandEvent.Media -> {
            if (event.packageName.isNotEmpty()) out.add(event.packageName)
        }
        is IslandEvent.Timer -> {
            if (event.packageName.isNotEmpty()) out.add(event.packageName)
            addPendingIntentOwnerPackages(event.contentIntent, out)
        }
        is IslandEvent.Stopwatch -> {
            if (event.packageName.isNotEmpty()) out.add(event.packageName)
            addPendingIntentOwnerPackages(event.contentIntent, out)
        }
        else -> Unit
    }
    return out
}

private fun addPendingIntentOwnerPackages(pi: PendingIntent?, out: MutableSet<String>) {
    if (pi == null) return
    try {
        val creator = pi.creatorPackage
        if (!creator.isNullOrEmpty()) out.add(creator)
    } catch (_: RuntimeException) {
        // creator co the throw tren mot so build — bo qua.
    }
    try {
        val intent = pi.intent
        val pkg = intent?.`package`
        if (!pkg.isNullOrEmpty()) out.add(pkg)
        val compPkg = intent?.component?.packageName
        if (!compPkg.isNullOrEmpty()) out.add(compPkg)
    } catch (_: RuntimeException) {
        // getIntent co the bi block — khong send; chi doc metadata.
    }
}

/**
 * An pill statusbar khi FG == owner (Media/Timer/Stopwatch).
 * Unknown/empty owner → khong an. Keyguard: caller tu loc (!onKeyguard).
 * Khong an moi event chi vi ANY app FG.
 */
internal fun islandEventShouldHideForForeground(
    event: IslandEvent,
    foregroundPackage: String?,
): Boolean {
    if (foregroundPackage.isNullOrEmpty()) return false
    val owners = islandEventOwnerPackages(event)
    if (owners.isEmpty()) return false
    return foregroundPackage in owners
}

/** Event type duoc FG-hide policy (khong Broad EVERY event). */
internal fun islandEventSupportsForegroundHide(event: IslandEvent): Boolean =
    event is IslandEvent.Media ||
        event is IslandEvent.Timer ||
        event is IslandEvent.Stopwatch

// --- NIC gate ---

/**
 * Khoa animation noi bo NIC trong luc slot reflow.
 * SoT: desired && !suspended — [StatusBarNicSlotReflowSuspend].
 */
internal class StatusBarNicAnimationGate {
    private var suspended = false
    private var nic: NotificationIconContainer? = null

    fun suspendFrom(host: View) {
        if (suspended) return
        val container = findStatusBarNotificationIconContainer(host) ?: return
        nic = container
        StatusBarNicSlotReflowSuspend.setSuspended(container, true)
        suspended = true
    }

    fun restore() {
        if (!suspended) return
        try {
            StatusBarNicSlotReflowSuspend.setSuspended(nic, false)
        } finally {
            suspended = false
            nic = null
        }
    }
}

/**
 * Two-phase spring clock nudge tren View classic.
 * Generation token: stale end listener khong reset nudge moi / double nudge.
 * DynamicAnimation ton trong animator duration scale (framework).
 * Modern Compose clock: [animateStatusBarClockNudgeSprings] + expansion publish —
 * KHONG hai writer tren cung mot clock.
 */
internal class StatusBarClockNudgeController {
    private var generation = 0
    private var spring1: SpringAnimation? = null
    private var spring2: SpringAnimation? = null
    private var target: View? = null

    fun cancelAndReset() {
        generation++
        spring1?.cancel()
        spring2?.cancel()
        spring1 = null
        spring2 = null
        val v = target
        target = null
        if (v != null) {
            v.translationX = 0f
        }
    }

    /** Classic View clock only — modern path khong goi. */
    fun start(clock: View, amplitudePx: Float) {
        if (!statusBarClockShouldNudge(clock)) return
        if (amplitudePx == 0f) return
        cancelAndReset()
        val gen = generation
        target = clock
        val s1 =
            SpringAnimation(clock, DynamicAnimation.TRANSLATION_X).apply {
                spring =
                    SpringForce()
                        .setStiffness(StatusBarClockNudgeSpring1Stiffness)
                        .setDampingRatio(StatusBarClockNudgeSpring1Damping)
                addEndListener { _, canceled, _, _ ->
                    if (gen != generation) return@addEndListener
                    if (canceled) return@addEndListener
                    val s2 =
                        SpringAnimation(clock, DynamicAnimation.TRANSLATION_X).apply {
                            spring =
                                SpringForce(0f)
                                    .setStiffness(StatusBarClockNudgeSpring2Stiffness)
                                    .setDampingRatio(StatusBarClockNudgeSpring2Damping)
                            addEndListener { _, _, _, _ ->
                                if (gen != generation) return@addEndListener
                                if (target === clock) {
                                    clock.translationX = 0f
                                }
                                if (generation == gen) {
                                    spring1 = null
                                    spring2 = null
                                }
                            }
                        }
                    spring2 = s2
                    s2.animateToFinalPosition(0f)
                }
            }
        spring1 = s1
        s1.animateToFinalPosition(-amplitudePx)
    }
}

/**
 * Compose-path two-phase spring khi khong co View clock (modern).
 * Cancel coroutine → snap 0. Generation qua [nudgeEpoch] tranh stale.
 */
/**
 * Compose-path two-phase spring. [onFrame] publish tx (expansion) moi frame.
 * Cancel / epoch doi → snap 0 neu van dung epoch.
 */
internal suspend fun Animatable<Float, AnimationVector1D>.animateStatusBarClockNudgeSprings(
    amplitudePx: Float,
    nudgeEpoch: Int,
    currentEpoch: () -> Int,
    onFrame: (Float) -> Unit = {},
) {
    if (amplitudePx == 0f) {
        snapTo(0f)
        onFrame(0f)
        return
    }
    // Pixel-space settle = DynamicAnimation.TRANSLATION_X (1px), khong 0.01 default.
    val pixelSpring1 =
        spring(
            dampingRatio = StatusBarClockNudgeSpring1Damping,
            stiffness = StatusBarClockNudgeSpring1Stiffness,
            visibilityThreshold = StatusBarClockNudgePixelVisibilityThreshold,
        )
    val pixelSpring2 =
        spring(
            dampingRatio = StatusBarClockNudgeSpring2Damping,
            stiffness = StatusBarClockNudgeSpring2Stiffness,
            visibilityThreshold = StatusBarClockNudgePixelVisibilityThreshold,
        )
    try {
        animateTo(
            targetValue = -amplitudePx,
            animationSpec = pixelSpring1,
        ) {
            if (currentEpoch() == nudgeEpoch) onFrame(value)
        }
        if (currentEpoch() != nudgeEpoch) return
        animateTo(
            targetValue = 0f,
            animationSpec = pixelSpring2,
        ) {
            if (currentEpoch() == nudgeEpoch) onFrame(value)
        }
    } finally {
        if (currentEpoch() == nudgeEpoch) {
            snapTo(0f)
            onFrame(0f)
        }
    }
}

/**
 * Orchestration slot vs clock: settleSlot + NIC restore khi SLOT xong/cancel —
 * clock completion DOC LAP (khong doi / khong share finish).
 */
internal fun statusBarNeighborSlotOwnsSettle(
    slotRunGeneration: Int,
    currentGeneration: Int,
): Boolean = slotRunGeneration == currentGeneration

/**
 * Snapshot APP visual start — so floats + gen; KHONG du atomic slot+visual.
 * Production commit: [StatusBarAppPillTransitionMachine.commitStart].
 */
internal data class StatusBarAppPillVisualPlan(
    val generation: Long,
    val trigger: StatusBarNeighborTrigger,
    val startAlpha: Float,
    val startScale: Float,
)

/**
 * Tao plan tu trigger + rendered α/scale hien tai.
 * KHONG force snap0 mid-show — reversal continuity.
 */
internal fun statusBarAppPillVisualPlanOf(
    generation: Long,
    trigger: StatusBarNeighborTrigger,
    currentAlpha: Float,
    currentScale: Float,
): StatusBarAppPillVisualPlan {
    val a = currentAlpha.coerceIn(0f, 1f)
    val s = currentScale.coerceIn(0f, 1f)
    return when (trigger) {
        StatusBarNeighborTrigger.APP_HIDE ->
            StatusBarAppPillVisualPlan(
                generation = generation,
                trigger = trigger,
                startAlpha = 0f,
                startScale = 0f,
            )
        StatusBarNeighborTrigger.APP_SHOW ->
            StatusBarAppPillVisualPlan(
                generation = generation,
                trigger = trigger,
                startAlpha = a,
                startScale = s,
            )
        StatusBarNeighborTrigger.CARD_OPEN,
        StatusBarNeighborTrigger.CARD_CLOSE ->
            StatusBarAppPillVisualPlan(
                generation = generation,
                trigger = trigger,
                startAlpha = a,
                startScale = s,
            )
    }
}

/**
 * Production start seed — SSOT Chip + tests (task-012).
 * Co [renderedFrame]: slot/α/scale/natural tu frame user thay (khong Animatable stale
 * trong suspend gap sau commit). Khong frame (initial/morph clear): fallback backing.
 * Natural doi: bao toan occupied px qua [statusBarChipSlotSegmentStartForNaturalWidthChange]
 * dung naturalPx cua frame (hoac backing) — khong natural doc lap stale.
 */
internal data class StatusBarAppPillStartSeed(
    val slotStartFraction: Float,
    val startAlpha: Float,
    val startScale: Float,
    /** Natural px nguon cho bao toan occupied (frame hoac backing). */
    val previousNaturalPx: Float,
    val fromRenderedFrame: Boolean,
    val visualPlan: StatusBarAppPillVisualPlan?,
)

/**
 * Selector/reducer start — Chip goi truoc commitStart; test cung path.
 * @param renderedFrame machine/layout SSOT (nullable sau clear/morph)
 * @param backing* Animatable / segment state — chi dung khi frame absent
 * @param nextNaturalPx natural layout sap commit
 * @param nextTrigger trigger generation moi
 */
internal fun statusBarAppPillStartSeedOf(
    renderedFrame: StatusBarAppPillRenderedFrame?,
    backingSlotStart: Float,
    backingReclaiming: Boolean,
    backingLinearT: Float,
    backingTrigger: StatusBarNeighborTrigger,
    backingAlpha: Float,
    backingScale: Float,
    backingNaturalPx: Float,
    nextNaturalPx: Float,
    nextTrigger: StatusBarNeighborTrigger,
    generation: Long,
): StatusBarAppPillStartSeed {
    val fromFrame = renderedFrame != null
    val currentRendered: Float
    val currentAlpha: Float
    val currentScale: Float
    val prevNatural: Float
    if (renderedFrame != null) {
        // Layout/graphicsLayer da consume frame.slotFraction (nonnegative).
        currentRendered = statusBarChipSlotRenderedFraction(renderedFrame.slotFraction)
        currentAlpha = renderedFrame.alpha
        currentScale = renderedFrame.scale
        prevNatural =
            if (renderedFrame.naturalPx > 0f && renderedFrame.naturalPx.isFinite()) {
                renderedFrame.naturalPx
            } else {
                nextNaturalPx
            }
    } else {
        val currentRaw =
            statusBarChipSlotSegmentFractionAt(
                start = backingSlotStart,
                target = statusBarChipSlotSegmentTarget(backingReclaiming),
                t = backingLinearT,
                trigger = backingTrigger,
            )
        currentRendered = statusBarChipSlotRenderedFraction(currentRaw)
        currentAlpha = backingAlpha
        currentScale = backingScale
        prevNatural =
            if (backingNaturalPx > 0f && backingNaturalPx.isFinite()) {
                backingNaturalPx
            } else {
                nextNaturalPx
            }
    }
    val startFraction =
        statusBarChipSlotSegmentStartForNaturalWidthChange(
            currentRenderedFraction = currentRendered,
            previousNaturalPx = prevNatural,
            nextNaturalPx = nextNaturalPx,
        )
    val appPlan =
        if (statusBarNeighborTriggerIsApp(nextTrigger)) {
            statusBarAppPillVisualPlanOf(
                generation = generation,
                trigger = nextTrigger,
                currentAlpha = currentAlpha,
                currentScale = currentScale,
            )
        } else {
            null
        }
    return StatusBarAppPillStartSeed(
        slotStartFraction = startFraction,
        startAlpha = appPlan?.startAlpha ?: currentAlpha.coerceIn(0f, 1f),
        startScale = appPlan?.startScale ?: currentScale.coerceIn(0f, 1f),
        previousNaturalPx = prevNatural,
        fromRenderedFrame = fromFrame,
        visualPlan = appPlan,
    )
}

/**
 * Immutable first-frame slot + visual — rendering doc 1 object.
 * Tranh slotStart/trigger moi + linearT Animatable cu khi snapTo suspend.
 */
internal data class StatusBarAppPillRenderedFrame(
    val generation: Long,
    val trigger: StatusBarNeighborTrigger,
    val slotStartFraction: Float,
    val reclaiming: Boolean,
    val linearT: Float,
    val alpha: Float,
    val scale: Float,
    val naturalPx: Float,
) {
    val slotFraction: Float
        get() =
            statusBarChipSlotSegmentFractionAt(
                start = slotStartFraction,
                target = statusBarChipSlotSegmentTarget(reclaiming),
                t = linearT,
                trigger = trigger,
            )
}

/**
 * Production commit machine — Chip layout/graphicsLayer consume [rendered].
 * commitStart() khong suspend; Animatable.snapTo sync SAU (gap van coherent).
 * Khong Snapshot.withMutableSnapshot; khong claim comment/same LaunchedEffect = atomic.
 */
internal class StatusBarAppPillTransitionMachine {
    var rendered: StatusBarAppPillRenderedFrame? = null
        private set

    fun clear() {
        rendered = null
    }

    /** Commit start coherent (t=0 + α/scale plan) — atomic assign. */
    fun commitStart(
        generation: Long,
        trigger: StatusBarNeighborTrigger,
        slotStartFraction: Float,
        reclaiming: Boolean,
        alpha: Float,
        scale: Float,
        naturalPx: Float,
    ): StatusBarAppPillRenderedFrame {
        val frame =
            StatusBarAppPillRenderedFrame(
                generation = generation,
                trigger = trigger,
                slotStartFraction = slotStartFraction,
                reclaiming = reclaiming,
                linearT = 0f,
                alpha = alpha.coerceIn(0f, 1f),
                scale = scale.coerceIn(0f, 1f),
                naturalPx = naturalPx,
            )
        rendered = frame
        return frame
    }

    /**
     * Cap nhat frame dang chay. Stale generation → ignore (cancel/reentry guard).
     * @return frame moi hoac null neu stale/clear.
     */
    fun advance(
        generation: Long,
        linearT: Float? = null,
        alpha: Float? = null,
        scale: Float? = null,
    ): StatusBarAppPillRenderedFrame? {
        val cur = rendered ?: return null
        if (cur.generation != generation) return null
        val next =
            cur.copy(
                linearT = linearT?.coerceIn(0f, 1f) ?: cur.linearT,
                alpha = alpha?.coerceIn(0f, 1f) ?: cur.alpha,
                scale = scale?.coerceIn(0f, 1f) ?: cur.scale,
            )
        rendered = next
        return next
    }

    /** Settle slot t=1 + start=target khi van dung generation. */
    fun settleSlot(generation: Long): StatusBarAppPillRenderedFrame? {
        val cur = rendered ?: return null
        if (cur.generation != generation) return null
        val target = statusBarChipSlotSegmentTarget(cur.reclaiming)
        val next = cur.copy(slotStartFraction = target, linearT = 1f)
        rendered = next
        return next
    }
}

/** APP_* trigger? — slot+α/scale/clock chung generation. */
internal fun statusBarNeighborTriggerIsApp(trigger: StatusBarNeighborTrigger): Boolean =
    trigger == StatusBarNeighborTrigger.APP_SHOW ||
        trigger == StatusBarNeighborTrigger.APP_HIDE

/**
 * Occupied width chip (outer) → ComposeView WRAP_CONTENT → LinearLayout sibling NIC.
 * width0: khong GONE host (clock/date chung cay); parent van remeasure.
 */
internal fun statusBarChipOccupiedWidthForNativeParent(
    naturalWidthPx: Int,
    slotWidthFraction: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): Int =
    statusBarChipOuterOccupiedWidthPx(
        naturalWidthPx = naturalWidthPx,
        slotWidthFraction = slotWidthFraction,
        densityDpi = densityDpi,
    )

/**
 * Ngan sach NATURAL hop le (unanimated) — chipsMaxWidth / cutout / app-handle tu caller.
 * Int.MAX_VALUE = khong chan (parent unbounded). 0 = an toan clamp ve 0.
 * CAM truyen occupied dang animate (tranh feedback hide jerk).
 */
internal fun statusBarChipClampToNaturalBudgetPx(px: Int, naturalBudgetMaxPx: Int): Int {
    val v = px.coerceAtLeast(0)
    if (naturalBudgetMaxPx == Int.MAX_VALUE) return v
    return v.coerceAtMost(naturalBudgetMaxPx.coerceAtLeast(0))
}

/**
 * Natural ON DINH cho occupied — task-015/016.
 *
 * Do natural trong ngan sach caller (KHONG Constraints.Infinity bypass chipsMaxWidth;
 * KHONG max=occupied animate). Helper chan feedback khi measured teo vi occupied.
 *
 * Policy:
 * - clamp measured/cache theo [naturalBudgetMaxPx] (ke ca budget 0 / giam midflight)
 * - cache<=0 → nhan measured>0 (lan dau)
 * - measured >= cache → nhan (noi dung lon hon)
 * - measured < cache + slotFraction~rest (≥0.98) → nhan (idle, noi dung thuc su doi)
 * - measured < cache + dang animate fraction → GIU cache (cam occupied→natural feedback)
 */
internal fun statusBarChipStableNaturalWidthPx(
    cachedNaturalPx: Int,
    measuredUnconstrainedPx: Int,
    slotWidthFraction: Float,
    naturalBudgetMaxPx: Int = Int.MAX_VALUE,
): Int {
    val measured =
        statusBarChipClampToNaturalBudgetPx(measuredUnconstrainedPx, naturalBudgetMaxPx)
    val cached = statusBarChipClampToNaturalBudgetPx(cachedNaturalPx, naturalBudgetMaxPx)
    if (measured <= 0) return cached
    if (cached <= 0) return measured
    if (measured >= cached) return measured
    val atRest = slotWidthFraction.isFinite() && slotWidthFraction >= 0.98f
    return if (atRest) measured else cached
}

/**
 * Contract layout occupied: natural on dinh × fraction → desired width.
 * [naturalBudgetMaxPx]: ngan sach unanimated tu production (chipsMaxWidth) — test cung dung.
 * Cam truyen occupied animate thay budget (feedback → occupied non-monotonic).
 */
internal data class StatusBarChipOccupiedLayoutContract(
    val stableNaturalPx: Int,
    val desiredOccupiedPx: Int,
    val usedMeasuredAsNatural: Boolean,
    val naturalBudgetMaxPx: Int,
)

internal fun statusBarChipOccupiedLayoutContract(
    cachedNaturalPx: Int,
    measuredUnconstrainedPx: Int,
    slotWidthFraction: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
    naturalBudgetMaxPx: Int = Int.MAX_VALUE,
): StatusBarChipOccupiedLayoutContract {
    val measuredClamped =
        statusBarChipClampToNaturalBudgetPx(measuredUnconstrainedPx, naturalBudgetMaxPx)
    val stable =
        statusBarChipStableNaturalWidthPx(
            cachedNaturalPx = cachedNaturalPx,
            measuredUnconstrainedPx = measuredClamped,
            slotWidthFraction = slotWidthFraction,
            naturalBudgetMaxPx = naturalBudgetMaxPx,
        )
    val desired =
        statusBarChipOccupiedWidthForNativeParent(
            naturalWidthPx = stable,
            slotWidthFraction = slotWidthFraction,
            densityDpi = densityDpi,
        )
    return StatusBarChipOccupiedLayoutContract(
        stableNaturalPx = stable,
        desiredOccupiedPx = desired,
        usedMeasuredAsNatural = stable == measuredClamped && measuredClamped > 0,
        naturalBudgetMaxPx = naturalBudgetMaxPx,
    )
}

/**
 * Bridge occupied → native LinearLayout sibling NIC.
 * CHI requestLayout host/parent — CAM set LayoutParams.width = pill
 * (ComposeView chung clock/date).
 */
internal class StatusBarChipNativeOccupiedBridge {
    var desiredOccupiedPx: Int = 0
        private set
    var lastHostWidthPx: Int = -1
        private set
    private var host: View? = null
    private var lastRequested: Int = -1

    fun attach(host: View) {
        this.host = host
    }

    fun detach() {
        host = null
        lastRequested = -1
    }

    fun noteHostMeasuredWidth(widthPx: Int) {
        lastHostWidthPx = widthPx
    }

    /**
     * @return true neu da enqueue/request layout (desired doi).
     */
    fun publishDesiredOccupiedPx(px: Int): Boolean {
        val next = px.coerceAtLeast(0)
        desiredOccupiedPx = next
        if (next == lastRequested) return false
        lastRequested = next
        val v = host ?: return false
        val parent = v.parent as? View
        // Khong set v.layoutParams.width = next — host != pill-only.
        if (!v.isInLayout) {
            v.requestLayout()
            parent?.requestLayout()
        } else {
            v.post {
                v.requestLayout()
                parent?.requestLayout()
            }
        }
        return true
    }
}

/** Tag adb: `adb -s <serial> shell setprop log.tag.AxDbAppPill DEBUG` */
internal const val StatusBarAppPillMotionDiagTag = "AxDbAppPill"

private const val StatusBarAppPillDiagMinIntervalMs = 33L // ~30Hz
private const val StatusBarAppPillDiagWindowMs = 500L
private const val StatusBarAppPillDiagTraceName = "AxDbAppPill"

/**
 * Rut gon package cho log — khong dump raw. Mapping: `pkg:<hex32 hashCode unsigned>`.
 * null/empty → null (in log thanh "-").
 */
internal fun statusBarAppPillFgPkgToken(packageName: String?): String? {
    if (packageName.isNullOrEmpty()) return null
    return "pkg:" + Integer.toHexString(packageName.hashCode())
}

/** Loai event pill (chronometer/media/…) — KHONG gan nhan la FG package. */
internal fun statusBarAppPillEventKindOf(event: IslandEvent?): String =
    when (event) {
        is IslandEvent.Stopwatch,
        is IslandEvent.Timer -> "chronometer"
        is IslandEvent.Media -> "media"
        null -> "-"
        else -> event::class.simpleName ?: "event"
    }

/** True khi log.tag.AxDbAppPill=DEBUG — dung chung AppTracking / VM / Chip. */
internal fun statusBarAppPillDiagEnabled(): Boolean =
    Log.isLoggable(StatusBarAppPillMotionDiagTag, Log.DEBUG)

/**
 * Log FG ownership thap volume (AppTracking) — cung tag/uptime de stage delay Home.
 * Khong private content; package = [statusBarAppPillFgPkgToken].
 */
internal fun statusBarAppPillLogFgOwner(
    cause: String,
    kind: String,
    taskId: Int,
    displayId: Int,
    packageName: String?,
    seq: Long = -1L,
) {
    if (!statusBarAppPillDiagEnabled()) return
    Log.d(
        StatusBarAppPillMotionDiagTag,
        "FG_OWNER up=${SystemClock.uptimeMillis()} cause=$cause kind=$kind " +
            "task=$taskId display=$displayId fg=${statusBarAppPillFgPkgToken(packageName) ?: "-"} " +
            "seq=$seq",
    )
}

/**
 * Log emit/commit ownership UI (Interactor/VM) — correlatable voi FG_OWNER + START.
 */
internal fun statusBarAppPillLogUiOwner(
    cause: String,
    ownerHidden: Boolean,
    suppressedCount: Int,
    fgToken: String?,
    eventKind: String,
    seq: Long = -1L,
) {
    if (!statusBarAppPillDiagEnabled()) return
    Log.d(
        StatusBarAppPillMotionDiagTag,
        "UI_OWNER up=${SystemClock.uptimeMillis()} cause=$cause ownerHidden=$ownerHidden " +
            "suppressedN=$suppressedCount fg=${fgToken ?: "-"} eventKind=$eventKind seq=$seq",
    )
}

/**
 * Diagnostics gated — off mac dinh (Log.isLoggable).
 * Phan biet desired occupied (Compose slot) vs measured native host/NIC.
 * Trace ASYNC theo generation cookie — KHONG beginSection dong bo span coroutine/frame.
 * end/cancel dung 1 lan / gen; stale gen khong dong run moi; DEBUG off mid-run van end async.
 */
internal class StatusBarAppPillMotionDiag {
    private var windowStartUptimeMs: Long = 0L
    private var lastSampleUptimeMs: Long = 0L
    private var lastSignature: String = ""
    /** Generation dang giu async Trace; -1 = khong active. */
    private var activeTraceGeneration: Long = -1L
    private var activeTraceCookie: Int = 0

    fun enabled(): Boolean = statusBarAppPillDiagEnabled()

    fun logStart(
        generation: Long,
        trigger: StatusBarNeighborTrigger,
        ownerHidden: Boolean,
        reclaiming: Boolean,
        slotStartFraction: Float,
        naturalPx: Int,
        occupiedDesiredPx: Int,
        alpha: Float,
        scale: Float,
        /** Token FG that (AppTracking) — khong nham eventKind. */
        fgToken: String?,
        fgSource: String,
        /** chronometer/media/… — tach khoi fg. */
        eventKind: String,
        displayId: Int,
        suppressedCount: Int = -1,
        naturalBudgetMaxPx: Int = Int.MAX_VALUE,
    ) {
        // Dong gen cu truoc (cancel/stale) — ke ca khi DEBUG vua tat mid-run.
        endAsyncTraceIfActive(expectedGeneration = null)
        if (!enabled()) return
        val now = SystemClock.uptimeMillis()
        windowStartUptimeMs = now
        lastSampleUptimeMs = now
        lastSignature = ""
        val budget =
            if (naturalBudgetMaxPx == Int.MAX_VALUE) "inf" else naturalBudgetMaxPx.toString()
        val msg =
            "START up=$now gen=$generation cause=$trigger ownerHidden=$ownerHidden " +
                "reclaim=$reclaiming startFrac=${fmt(slotStartFraction)} natural=$naturalPx " +
                "budget=$budget occDesire=$occupiedDesiredPx a=${fmt(alpha)} s=${fmt(scale)} " +
                "fg=${fgToken ?: "-"} src=$fgSource eventKind=$eventKind " +
                "suppressedN=$suppressedCount display=$displayId"
        Log.d(StatusBarAppPillMotionDiagTag, msg)
        // Async Trace: cookie = gen thap 31-bit; khong span sync thread-local.
        val cookie = (generation and 0x7fffffffL).toInt()
        Trace.beginAsyncSection(StatusBarAppPillDiagTraceName, cookie)
        activeTraceGeneration = generation
        activeTraceCookie = cookie
    }

    fun logEnd(
        generation: Long,
        trigger: StatusBarNeighborTrigger,
        occupiedDesiredPx: Int,
        occupiedMeasuredHostPx: Int,
        nicLeft: Int,
        nicWidth: Int,
        nicWinL: Int = -1,
        hostWinL: Int = -1,
    ) {
        val now = SystemClock.uptimeMillis()
        if (enabled()) {
            Log.d(
                StatusBarAppPillMotionDiagTag,
                "END up=$now gen=$generation cause=$trigger occDesire=$occupiedDesiredPx " +
                    "hostW=$occupiedMeasuredHostPx nicL=$nicLeft nicW=$nicWidth " +
                    "nicWinL=$nicWinL hostWinL=$hostWinL",
            )
        }
        endAsyncTraceIfActive(expectedGeneration = generation)
        windowStartUptimeMs = 0L
    }

    /** Cancel / stale — dong async Trace dung gen (stale khong dong gen moi). */
    fun logCancel(generation: Long, trigger: StatusBarNeighborTrigger) {
        if (enabled()) {
            Log.d(
                StatusBarAppPillMotionDiagTag,
                "CANCEL up=${SystemClock.uptimeMillis()} gen=$generation cause=$trigger",
            )
        }
        endAsyncTraceIfActive(expectedGeneration = generation)
        windowStartUptimeMs = 0L
    }

    /**
     * @param expectedGeneration null = force close gen dang active; non-null chi close neu khop.
     */
    private fun endAsyncTraceIfActive(expectedGeneration: Long?) {
        val gen = activeTraceGeneration
        if (gen < 0L) return
        if (expectedGeneration != null && expectedGeneration != gen) return
        val cookie = activeTraceCookie
        activeTraceGeneration = -1L
        activeTraceCookie = 0
        Trace.endAsyncSection(StatusBarAppPillDiagTraceName, cookie)
    }

    /**
     * Sample throttle: <=30Hz trong 500ms sau start; sau window chi khi signature doi.
     * hostWin va nicWin la toa do locationInWindow; nicParentW + layoutPhase de doc toa do.
     */
    fun logSample(
        generation: Long,
        trigger: StatusBarNeighborTrigger,
        ownerHidden: Boolean,
        linearT: Float,
        slotFraction: Float,
        naturalPx: Int,
        occupiedDesiredPx: Int,
        occupiedMeasuredChipPx: Int,
        alpha: Float,
        scale: Float,
        hostLeft: Int,
        hostWidth: Int,
        nicLeft: Int,
        nicWidth: Int,
        fgToken: String?,
        eventKind: String = "-",
        hostWinL: Int = -1,
        hostWinT: Int = -1,
        nicWinL: Int = -1,
        nicWinT: Int = -1,
        nicParentW: Int = -1,
        /** preLayout | postLayout — sample truoc layout phai gan nhan. */
        layoutPhase: String = "postLayout",
        force: Boolean = false,
    ) {
        if (!enabled()) return
        val now = SystemClock.uptimeMillis()
        val sig =
            "$generation:${occupiedDesiredPx}:${occupiedMeasuredChipPx}:" +
                "${fmt(slotFraction)}:${fmt(alpha)}:${fmt(scale)}:$hostWidth:$nicWinL:$layoutPhase"
        val inWindow =
            windowStartUptimeMs > 0L && (now - windowStartUptimeMs) <= StatusBarAppPillDiagWindowMs
        val due =
            force ||
                (inWindow && (now - lastSampleUptimeMs) >= StatusBarAppPillDiagMinIntervalMs) ||
                (!inWindow && sig != lastSignature &&
                    (now - lastSampleUptimeMs) >= StatusBarAppPillDiagMinIntervalMs)
        if (!due) return
        lastSampleUptimeMs = now
        lastSignature = sig
        Log.d(
            StatusBarAppPillMotionDiagTag,
            "SAMPLE up=$now gen=$generation cause=$trigger ownerHidden=$ownerHidden " +
                "t=${fmt(linearT)} frac=${fmt(slotFraction)} natural=$naturalPx " +
                "occDesire=$occupiedDesiredPx occChip=$occupiedMeasuredChipPx " +
                "a=${fmt(alpha)} s=${fmt(scale)} hostL=$hostLeft hostW=$hostWidth " +
                "hostWinL=$hostWinL hostWinT=$hostWinT nicL=$nicLeft nicW=$nicWidth " +
                "nicWinL=$nicWinL nicWinT=$nicWinT nicParentW=$nicParentW " +
                "phase=$layoutPhase fg=${fgToken ?: "-"} eventKind=$eventKind",
        )
    }

    private fun fmt(v: Float): String =
        if (!v.isFinite()) "NaN" else ((v * 1000f).roundToInt() / 1000f).toString()
}

/** Pill app-show alpha →1 — PathInterpolator(.33,1,.68,1), 500ms. Start tu plan (khong force 0). */
internal suspend fun animateStatusBarPillAppShowAlpha(
    alpha: Animatable<Float, AnimationVector1D>,
    startAlpha: Float = alpha.value,
) {
    val start = startAlpha.coerceIn(0f, 1f)
    if (alpha.value != start) {
        alpha.snapTo(start)
    }
    alpha.animateTo(
        targetValue = 1f,
        animationSpec =
            tween(
                durationMillis = StatusBarPillAppShowAlphaMs,
                easing = StatusBarChipSlotAppEasing,
            ),
    )
}

/** Pill app-show scale spring — start tu plan; caller cancel + snap 1 khi alpha xong. */
internal suspend fun animateStatusBarPillAppShowScale(
    scale: Animatable<Float, AnimationVector1D>,
    startScale: Float = scale.value,
) {
    val start = startScale.coerceIn(0f, 1f)
    if (scale.value != start) {
        scale.snapTo(start)
    }
    scale.animateTo(
        targetValue = 1f,
        animationSpec =
            spring(
                dampingRatio = StatusBarPillAppShowScaleDamping,
                stiffness = StatusBarPillAppShowScaleStiffness,
            ),
    )
}

/** Snap pill visual an (APP_HIDE) — khong VisibilityTransition 200ms. */
internal suspend fun snapStatusBarPillAppHide(
    alpha: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
) {
    alpha.stop()
    scale.stop()
    alpha.snapTo(0f)
    scale.snapTo(0f)
}

/** Rest visible (handoff / unrelated) — khong app-show anim. */
internal suspend fun snapStatusBarPillAppVisible(
    alpha: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
) {
    alpha.stop()
    scale.stop()
    alpha.snapTo(1f)
    scale.snapTo(1f)
}

// --- Legacy shared-finish helpers (obsolete) — stub false / deprecate ---

@Deprecated("task-007: khong enforce identical clock/NIC finish")
internal data class StatusBarSlotClockSample(
    val linearT: Float,
    val nicLayoutFraction: Float,
    val clockTranslationXPx: Float,
    val nicOvershootDeltaPx: Float,
)

@Deprecated("task-007")
internal fun statusBarSlotClockSampleAt(
    reclaiming: Boolean,
    linearT: Float,
    slotWidthPx: Float,
    clockAmplitudePx: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
): StatusBarSlotClockSample {
    val t = linearT.coerceIn(0f, 1f)
    val raw = statusBarChipSlotRawFractionAt(reclaiming, t)
    val layout = statusBarChipSlotWidthFractionForLayout(raw, slotWidthPx, densityDpi)
    return StatusBarSlotClockSample(
        linearT = t,
        nicLayoutFraction = layout,
        clockTranslationXPx =
            statusBarClockNudgeTranslationXAt(t, clockAmplitudePx),
        nicOvershootDeltaPx =
            statusBarChipSlotOvershootDeltaPx(reclaiming, t, slotWidthPx, densityDpi),
    )
}

@Deprecated("task-007: independent finish")
internal fun statusBarSlotClockLastVisibleT(
    reclaiming: Boolean,
    slotWidthPx: Float,
    clockAmplitudePx: Float,
    densityDpi: Float,
    threshPx: Float,
    durationMs: Float,
    hz: Float,
): Pair<Float, Float> = -1f to -1f

@Deprecated("task-007: always false — do not demand shared finish")
internal fun statusBarSlotClockShareEndPhase(
    reclaiming: Boolean,
    slotWidthPx: Float,
    clockAmplitudePx: Float,
    densityDpi: Float = StatusBarChipSlotDefaultDensityDpi,
    phaseTs: FloatArray = floatArrayOf(),
    threshPx: Float = 0.5f,
    durationMs: Float = StatusBarChipSlotDurationMs.toFloat(),
    hz: Float = 120f,
): Boolean = false

@Deprecated("task-007")
internal fun statusBarSlotClockShareEndPhasePerceptiveMatrix(
    reclaiming: Boolean = true,
    slotWidthsPx: FloatArray = floatArrayOf(),
    densityDpis: FloatArray = floatArrayOf(),
    thresholdsPx: FloatArray = floatArrayOf(),
    hzList: FloatArray = floatArrayOf(),
): Boolean = false

@Deprecated("STATUS BAR: dung StatusBarClockNudgeController / springs")
internal suspend fun Animatable<Float, AnimationVector1D>.animateStatusBarClockNudge(
    amplitudePx: Float,
    durationMs: Int = StatusBarChipSlotShowMs,
) {
    animateStatusBarClockNudgeSprings(
        amplitudePx = amplitudePx,
        nudgeEpoch = 0,
        currentEpoch = { 0 },
    )
}
