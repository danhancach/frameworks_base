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

import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Lifecycle Clock timer/stopwatch khi notification vang tam (owner FG).
 *
 * MEASURED (runtime APK 959104f4…): Clock enter → notif remove → event clear →
 * ownerHidden flip → APP_SHOW nham; Home → post lai ~1274ms. Reason cu the
 * CHUA log runtime — khong claim da chung minh REASON_APP_CANCEL.
 *
 * POLICY (source):
 * - KEEP_OWNER_GAP: owner FG + app-cancel (hoac reason unknown) → giu id/timebase/pin.
 * - DROP_ENDED: dismiss/user/system/death/ban/... hoac app-cancel khi khong owner FG.
 * - Absolute per-key/per-generation deadline (max gap); leave-grace khong keo qua cap.
 * - DROP thang KEEP tren duplicate Scrim+NotifCollection; KEEP trung khong reset deadline.
 * - Same-key post: xem [classifyDeskClockPosted] — moi path kiem gap truoc early-return.
 *
 * P1 STOP/RESET: APP_CANCEL(+ALL)+owner FG trung voi stop/reset that trong Clock.
 * Secondary (status/actions/metric end) khong tin cay tai thoi diem removal — SBN
 * da mat; chua do runtime update-truoc-cancel. → UNPROVABLE tu source signals.
 * Tradeoff: giu bao thu bounded; residual ghost ≤ OWNER_GAP_MAX (con FG) hoac
 * LEAVE_REPOST_GRACE (sau Home). KHONG claim stop/reset PASS.
 */

/** Reason chua truyen (Scrim path cu) — heuristic FG; gioi han da document. */
internal const val DESK_CLOCK_REASON_UNKNOWN = -1

/**
 * Tran giu khi van owner FG ma notif khong ve (stop/reset trong app).
 * Absolute tu moc first-removed — khong vo han qua FG flip-flop.
 */
internal const val DESK_CLOCK_OWNER_GAP_MAX_MS = 45_000L

/**
 * Sau roi owner FG: cho Clock post lai truoc khi drop.
 * Do runtime ~1274ms; buffer thiet bi cham. Bi cap boi absolute deadline.
 */
internal const val DESK_CLOCK_LEAVE_REPOST_GRACE_MS = 3_500L

internal enum class DeskClockNotifRemovalDecision {
    /** Giu event — owner transient notif absence. */
    KEEP_OWNER_GAP,
    /** Ket thuc that — clear event. */
    DROP_ENDED,
}

/**
 * Quyet dinh KEEP/DROP cho timer|stopwatch removal.
 * SSOT production + test — khong helper-only emitter gia.
 */
internal fun deskClockNotifRemovalDecision(
    reason: Int,
    ownerInForeground: Boolean,
): DeskClockNotifRemovalDecision {
    when (reason) {
        NotificationListenerService.REASON_CANCEL,
        NotificationListenerService.REASON_CANCEL_ALL,
        NotificationListenerService.REASON_CLICK,
        NotificationListenerService.REASON_LISTENER_CANCEL,
        NotificationListenerService.REASON_LISTENER_CANCEL_ALL,
        NotificationListenerService.REASON_PACKAGE_BANNED,
        NotificationListenerService.REASON_USER_STOPPED,
        NotificationListenerService.REASON_PACKAGE_CHANGED,
        NotificationListenerService.REASON_CLEAR_DATA,
        NotificationListenerService.REASON_CHANNEL_REMOVED,
        NotificationListenerService.REASON_PACKAGE_SUSPENDED,
        NotificationListenerService.REASON_PROFILE_TURNED_OFF,
        NotificationListenerService.REASON_ERROR,
        NotificationListenerService.REASON_ASSISTANT_CANCEL,
        NotificationListenerService.REASON_SNOOZED,
        NotificationListenerService.REASON_TIMEOUT,
        NotificationListenerService.REASON_CHANNEL_BANNED,
        NotificationListenerService.REASON_LOCKDOWN,
        NotificationListenerService.REASON_BUNDLE_DISMISSED,
        NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED,
        NotificationListenerService.REASON_GROUP_OPTIMIZATION,
        NotificationListenerService.REASON_UNAUTOBUNDLED,
        -> return DeskClockNotifRemovalDecision.DROP_ENDED
    }

    if (
        reason == NotificationListenerService.REASON_APP_CANCEL ||
            reason == NotificationListenerService.REASON_APP_CANCEL_ALL
    ) {
        return if (ownerInForeground) {
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP
        } else {
            DeskClockNotifRemovalDecision.DROP_ENDED
        }
    }

    // Unknown / reason=0: chi KEEP khi owner FG (heuristic; khong chung minh reason).
    if (reason == DESK_CLOCK_REASON_UNKNOWN || reason == 0) {
        return if (ownerInForeground) {
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP
        } else {
            DeskClockNotifRemovalDecision.DROP_ENDED
        }
    }

    return DeskClockNotifRemovalDecision.DROP_ENDED
}

/**
 * P1: co the chung minh stop/reset vs owner-gap transient bang signal san co?
 * false = fundamentally unprovable (residual ghost bounded).
 */
internal fun deskClockStopResetDistinguishableFromOwnerGap(): Boolean = false

/**
 * Visual swipe-hide stopwatch (clearStopwatch) — khong stop Clock that.
 * Gate stateful: so sanh post voi [latestObserved] (cap nhat khi REJECT),
 * khong chi ORIGINAL dismiss — tranh pause→resume bi NEW_RUN sai vi startER doi.
 * KHONG so |startER vs pausedElapsed|; KHONG NEW_RUN chi vi startER khac / nguong 2s/5s.
 */
/** Sai so displayed cho pause/resume continuity (tick skew). */
internal const val DESK_CLOCK_STOPWATCH_CONTINUITY_EPS_MS = 2_500L
/** displayed ~0 sau pause lon → reset+start (khong resume). */
internal const val DESK_CLOCK_STOPWATCH_FRESH_START_MAX_MS = 1_500L
/** paused/displayed toi thieu de coi fresh-start la reset that. */
internal const val DESK_CLOCK_STOPWATCH_PRIOR_ELAPSED_MIN_MS = 3_000L

/**
 * Identity run stopwatch — running: startER; paused: pausedElapsed;
 * displayedElapsed SSOT (cung don vi ms).
 */
internal data class DeskClockStopwatchRunIdentity(
    val notificationKey: String,
    val isRunning: Boolean,
    val startElapsedRealtimeMs: Long,
    val pausedElapsedMs: Long,
    val displayedElapsedMs: Long,
)

/**
 * Gate visual-dismiss — rejected posts cap nhat latest continuation.
 */
internal data class DeskClockStopwatchVisualDismissGate(
    val latestObserved: DeskClockStopwatchRunIdentity,
    val awaitOwnerRevisit: Boolean = true,
    val ownerRevisitArmed: Boolean = false,
) {
    val notificationKey: String
        get() = latestObserved.notificationKey
}

internal enum class DeskClockStopwatchRunRelation {
    ALLOW_NO_DISMISS_GATE,
    ALLOW_DIFFERENT_KEY,
    ALLOW_NEW_RUN,
    REJECT_SAME_RUN,
}

internal fun deskClockStopwatchRunIdentity(
    notificationKey: String,
    isRunning: Boolean,
    startElapsedRealtimeMs: Long,
    pausedElapsedMs: Long,
    displayedElapsedMs: Long,
): DeskClockStopwatchRunIdentity =
    DeskClockStopwatchRunIdentity(
        notificationKey = notificationKey,
        isRunning = isRunning,
        startElapsedRealtimeMs = if (isRunning) startElapsedRealtimeMs else 0L,
        pausedElapsedMs = if (isRunning) 0L else pausedElapsedMs.coerceAtLeast(0L),
        displayedElapsedMs = displayedElapsedMs.coerceAtLeast(0L),
    )

internal fun deskClockStopwatchVisualDismissGateOf(
    identity: DeskClockStopwatchRunIdentity,
): DeskClockStopwatchVisualDismissGate =
    DeskClockStopwatchVisualDismissGate(
        latestObserved = identity,
        awaitOwnerRevisit = true,
        ownerRevisitArmed = false,
    )

/**
 * Phan loai post vs LATEST continuation — production + test SSOT.
 *
 * Resume sau pause: latest=paused → post running + displayed ~ pausedElapsed → SAME.
 * Reset that: latest paused/running lon → post displayed ~0 → NEW_RUN.
 * Ca hai running + startER khac: KHONG tu dong NEW_RUN; chi NEW_RUN khi displayed jump-back.
 * Metrics zero/UNKNOWN: conservative SAME (khong resurrect).
 */
internal fun deskClockStopwatchClassifyRunRelation(
    latest: DeskClockStopwatchRunIdentity?,
    post: DeskClockStopwatchRunIdentity,
): DeskClockStopwatchRunRelation {
    if (latest == null || latest.notificationKey.isEmpty()) {
        return DeskClockStopwatchRunRelation.ALLOW_NO_DISMISS_GATE
    }
    if (post.notificationKey != latest.notificationKey) {
        return DeskClockStopwatchRunRelation.ALLOW_DIFFERENT_KEY
    }
    // Zero/unknown metrics — conservative reject resurrect.
    if (deskClockStopwatchIdentityLooksUnknown(post)) {
        return DeskClockStopwatchRunRelation.REJECT_SAME_RUN
    }
    if (deskClockStopwatchFreshStartAfterPriorRun(latest, post)) {
        return DeskClockStopwatchRunRelation.ALLOW_NEW_RUN
    }
    if (latest.isRunning && post.isRunning) {
        // startER khac khong du de NEW_RUN (resume co the nhin nhu running vs running
        // neu latest chua cap nhat — caller phai update latest tren REJECT).
        return DeskClockStopwatchRunRelation.REJECT_SAME_RUN
    }
    if (!latest.isRunning && !post.isRunning) {
        return DeskClockStopwatchRunRelation.REJECT_SAME_RUN
    }
    // running↔paused: pause/resume continuity.
    if (!latest.isRunning && post.isRunning) {
        val continuous =
            kotlin.math.abs(post.displayedElapsedMs - latest.pausedElapsedMs) <=
                DESK_CLOCK_STOPWATCH_CONTINUITY_EPS_MS
        return if (continuous) {
            DeskClockStopwatchRunRelation.REJECT_SAME_RUN
        } else if (
            latest.pausedElapsedMs >= DESK_CLOCK_STOPWATCH_PRIOR_ELAPSED_MIN_MS &&
                post.displayedElapsedMs <= DESK_CLOCK_STOPWATCH_FRESH_START_MAX_MS
        ) {
            DeskClockStopwatchRunRelation.ALLOW_NEW_RUN
        } else {
            // Ambiguous — conservative SAME (owner revisit van restore).
            DeskClockStopwatchRunRelation.REJECT_SAME_RUN
        }
    }
    // running → paused
    return DeskClockStopwatchRunRelation.REJECT_SAME_RUN
}

internal fun deskClockStopwatchIdentityLooksUnknown(
    identity: DeskClockStopwatchRunIdentity,
): Boolean {
    if (identity.notificationKey.isEmpty()) return true
    if (identity.isRunning) {
        return identity.startElapsedRealtimeMs == 0L && identity.displayedElapsedMs == 0L
    }
    return identity.pausedElapsedMs == 0L && identity.displayedElapsedMs == 0L
}

internal fun deskClockStopwatchFreshStartAfterPriorRun(
    latest: DeskClockStopwatchRunIdentity,
    post: DeskClockStopwatchRunIdentity,
): Boolean {
    val prior =
        if (latest.isRunning) latest.displayedElapsedMs else latest.pausedElapsedMs
    if (prior < DESK_CLOCK_STOPWATCH_PRIOR_ELAPSED_MIN_MS) return false
    if (post.displayedElapsedMs > DESK_CLOCK_STOPWATCH_FRESH_START_MAX_MS) return false
    // Jump-back ro: prior lon → post ~0 (reset/start), ke ca cung startER hiem.
    return post.displayedElapsedMs + DESK_CLOCK_STOPWATCH_CONTINUITY_EPS_MS < prior
}

/**
 * Ap dung 1 post vao gate — SSOT production manager + stateful tests.
 * REJECT → cap nhat latestObserved; NEW_RUN/DIFFERENT_KEY → clear hoac replace.
 */
internal data class DeskClockStopwatchGateApplyResult(
    val relation: DeskClockStopwatchRunRelation,
    val nextGate: DeskClockStopwatchVisualDismissGate?,
    val allowEvent: Boolean,
)

internal fun deskClockStopwatchApplyPostToVisualDismissGate(
    gate: DeskClockStopwatchVisualDismissGate?,
    post: DeskClockStopwatchRunIdentity,
): DeskClockStopwatchGateApplyResult {
    if (gate == null) {
        return DeskClockStopwatchGateApplyResult(
            relation = DeskClockStopwatchRunRelation.ALLOW_NO_DISMISS_GATE,
            nextGate = null,
            allowEvent = true,
        )
    }
    val relation = deskClockStopwatchClassifyRunRelation(gate.latestObserved, post)
    return when (relation) {
        DeskClockStopwatchRunRelation.ALLOW_NO_DISMISS_GATE ->
            DeskClockStopwatchGateApplyResult(relation, null, allowEvent = true)
        DeskClockStopwatchRunRelation.ALLOW_DIFFERENT_KEY ->
            // Clear gate cu — khong stale owner-revisit rebind old key.
            DeskClockStopwatchGateApplyResult(
                relation = relation,
                nextGate = null,
                allowEvent = true,
            )
        DeskClockStopwatchRunRelation.ALLOW_NEW_RUN ->
            DeskClockStopwatchGateApplyResult(
                relation = relation,
                nextGate = null,
                allowEvent = true,
            )
        DeskClockStopwatchRunRelation.REJECT_SAME_RUN ->
            DeskClockStopwatchGateApplyResult(
                relation = relation,
                nextGate = gate.copy(latestObserved = post),
                allowEvent = false,
            )
    }
}

/** Hieu ung FG khi dang visual-dismiss stopwatch (await revisit). */
internal enum class DeskClockStopwatchVisualDismissFgEffect {
    NONE,
    /** Vao owner Clock — arm, chua restore. */
    ARM_OWNER_REVISIT,
    /** Roi owner sau khi da arm → clear gate + rebind. */
    RESTORE_AFTER_LEAVE,
}

/**
 * FG doi khi pill stopwatch dang visual-dismiss (event null, dismissedKey set).
 * Chi restore sau chuoi vao Clock → roi Clock/Home — khong moi tick post.
 */
internal fun deskClockStopwatchVisualDismissOnForegroundChanged(
    dismissedKey: String?,
    awaitOwnerRevisit: Boolean,
    ownerRevisitArmed: Boolean,
    foregroundPackage: String?,
    ownerPackages: Set<String>,
): DeskClockStopwatchVisualDismissFgEffect {
    if (dismissedKey.isNullOrEmpty() || !awaitOwnerRevisit) {
        return DeskClockStopwatchVisualDismissFgEffect.NONE
    }
    val inOwner =
        !foregroundPackage.isNullOrEmpty() && foregroundPackage in ownerPackages
    return when {
        inOwner -> DeskClockStopwatchVisualDismissFgEffect.ARM_OWNER_REVISIT
        ownerRevisitArmed -> DeskClockStopwatchVisualDismissFgEffect.RESTORE_AFTER_LEAVE
        else -> DeskClockStopwatchVisualDismissFgEffect.NONE
    }
}

/** Kind Clock dang track / vua classify tu post. */
internal enum class DeskClockNotifKind {
    NONE,
    STOPWATCH,
    TIMER,
}

/**
 * Quyet dinh sau khi Clock SBN post — SSOT production + test.
 *
 * Classification table:
 * - validated matching rebind → [HANDLE_VALIDATED] (clearOnValidatedMatchingPost trong handle*)
 * - same-key cross-type STOPWATCH↔TIMER → [CROSS_TYPE_DROP_THEN_HANDLE]
 * - same-key nonmatching / disabled / unclassifiable-with-gap → [RECONCILE_DROP_RETAINED]
 * - FLAG_GROUP_SUMMARY other key (khong gap) → [IGNORE] (khong drop unrelated)
 * - FLAG_GROUP_SUMMARY same key as gap → [RECONCILE_DROP_RETAINED] (khong ghost)
 * - extras null + has gap → [RECONCILE_DROP_RETAINED] (conservative Drop retained)
 * - extras null + no gap → [IGNORE]
 * - no old gap + enabled typed → [HANDLE_VALIDATED] (create path cu)
 * - no old gap + disabled/untyped → [IGNORE]
 */
internal enum class DeskClockPostedDisposition {
    HANDLE_VALIDATED,
    CROSS_TYPE_DROP_THEN_HANDLE,
    RECONCILE_DROP_RETAINED,
    IGNORE,
}

/**
 * Phan loai Clock post vs tracked same-key gap.
 * Dung boi NotificationIslandManager.onNotificationPosted — khong test-only copy.
 *
 * @param hasTrackedSameKeyGap true neu retention/event dang giu cung key
 * @param previousTrackedKind kind dang retained (NONE neu chi gap record / chua biet)
 * @param isGroupSummary FLAG_GROUP_SUMMARY — summary other key khong phai proof ended
 * @param extrasAvailable false → khong classify type; gap → Drop retained
 * @param classifiedKind STOPWATCH/TIMER/NONE sau parse channel/actions/metrics
 * @param typeEnabled false khi disabledTypes chan stopwatch|timer
 */
internal fun classifyDeskClockPosted(
    hasTrackedSameKeyGap: Boolean,
    previousTrackedKind: DeskClockNotifKind,
    isGroupSummary: Boolean,
    extrasAvailable: Boolean,
    classifiedKind: DeskClockNotifKind,
    typeEnabled: Boolean,
): DeskClockPostedDisposition {
    // Summary: cung key gap → reconcile; key khac → ignore (khong drop event khac).
    if (isGroupSummary) {
        return if (hasTrackedSameKeyGap) {
            DeskClockPostedDisposition.RECONCILE_DROP_RETAINED
        } else {
            DeskClockPostedDisposition.IGNORE
        }
    }
    // Extras null: khong classify — prefer Drop retained khi co gap (documented).
    if (!extrasAvailable) {
        return if (hasTrackedSameKeyGap) {
            DeskClockPostedDisposition.RECONCILE_DROP_RETAINED
        } else {
            DeskClockPostedDisposition.IGNORE
        }
    }
    if (classifiedKind == DeskClockNotifKind.NONE) {
        return if (hasTrackedSameKeyGap) {
            DeskClockPostedDisposition.RECONCILE_DROP_RETAINED
        } else {
            DeskClockPostedDisposition.IGNORE
        }
    }
    if (!typeEnabled) {
        return if (hasTrackedSameKeyGap) {
            DeskClockPostedDisposition.RECONCILE_DROP_RETAINED
        } else {
            DeskClockPostedDisposition.IGNORE
        }
    }
    if (!hasTrackedSameKeyGap) {
        return DeskClockPostedDisposition.HANDLE_VALIDATED
    }
    // Same-key cross-type: drop kind cu roi rebind kind moi.
    if (
        previousTrackedKind != DeskClockNotifKind.NONE &&
            previousTrackedKind != classifiedKind
    ) {
        return DeskClockPostedDisposition.CROSS_TYPE_DROP_THEN_HANDLE
    }
    return DeskClockPostedDisposition.HANDLE_VALIDATED
}

/** Token ngan cho key/id — khong dump raw notification text. */
internal fun deskClockEventToken(keyOrId: String?): String {
    if (keyOrId.isNullOrEmpty()) return "-"
    return "ev:" + Integer.toHexString(keyOrId.hashCode())
}

internal fun deskClockReasonLabel(reason: Int): String =
    when (reason) {
        DESK_CLOCK_REASON_UNKNOWN -> "UNKNOWN"
        NotificationListenerService.REASON_CLICK -> "CLICK"
        NotificationListenerService.REASON_CANCEL -> "CANCEL"
        NotificationListenerService.REASON_CANCEL_ALL -> "CANCEL_ALL"
        NotificationListenerService.REASON_APP_CANCEL -> "APP_CANCEL"
        NotificationListenerService.REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL"
        NotificationListenerService.REASON_USER_STOPPED -> "USER_STOPPED"
        NotificationListenerService.REASON_PACKAGE_CHANGED -> "PACKAGE_CHANGED"
        NotificationListenerService.REASON_LISTENER_CANCEL -> "LISTENER_CANCEL"
        NotificationListenerService.REASON_ERROR -> "ERROR"
        else -> "R$reason"
    }

/**
 * Diag gated (AxDbAppPill DEBUG) — post/remove Clock + keep/drop.
 * Off mac dinh; bat/tat cung thu tuc app-pill.
 */
internal fun statusBarAppPillLogClockNotif(
    cause: String,
    reason: Int,
    decision: String,
    ownerAssoc: Boolean,
    eventKind: String,
    eventToken: String,
    timebaseAnchor: Long,
    seq: Long = -1L,
) {
    if (!statusBarAppPillDiagEnabled()) return
    Log.d(
        StatusBarAppPillMotionDiagTag,
        "CLOCK_NOTIF up=${SystemClock.uptimeMillis()} cause=$cause " +
            "reason=${deskClockReasonLabel(reason)} decision=$decision " +
            "ownerAssoc=$ownerAssoc eventKind=$eventKind ev=$eventToken " +
            "tb=$timebaseAnchor seq=$seq",
    )
}

/** Dong ho monotonic — production SystemClock; test inject fake. */
internal fun interface DeskClockMonoClock {
    fun elapsedRealtime(): Long
}

internal enum class DeskClockOwnerGapMode {
    IN_OWNER_FG,
    LEAVE_GRACE,
}

/** Ban ghi retention per-key — absolute deadline + generation + arm identity. */
internal data class DeskClockOwnerGapRecord(
    val key: String,
    val generation: Long,
    val firstRemovedElapsedMs: Long,
    val absoluteDeadlineElapsedMs: Long,
    val mode: DeskClockOwnerGapMode,
    /** Tang moi lan ArmTimer — stale leave-grace sau FG re-arm khong Drop som. */
    val armSequence: Long = 0L,
    /** due = now+delay luc arm; callback phai khop. */
    val dueDeadlineElapsedMs: Long = 0L,
)

/** Hieu ung reducer → manager arm job / drop event. */
internal sealed class DeskClockOwnerGapEffect {
    data class ArmTimer(
        val key: String,
        val generation: Long,
        val delayMs: Long,
        val cause: String,
        val armSequence: Long,
        val dueDeadlineElapsedMs: Long,
    ) : DeskClockOwnerGapEffect()

    data class Drop(
        val key: String,
        val generation: Long,
        val cause: String,
    ) : DeskClockOwnerGapEffect()

    data object None : DeskClockOwnerGapEffect()
}

/**
 * Production retention SSOT dung boi NotificationIslandManager.
 * Absolute first-removed deadline; leave-grace bi cap; generation-safe timeout;
 * DROP thang KEEP; KEEP trung khong reset firstRemoved.
 */
internal class DeskClockOwnerGapRetention(
    private val clock: DeskClockMonoClock =
        DeskClockMonoClock { SystemClock.elapsedRealtime() },
    private val maxGapMs: Long = DESK_CLOCK_OWNER_GAP_MAX_MS,
    private val leaveGraceMs: Long = DESK_CLOCK_LEAVE_REPOST_GRACE_MS,
) {
    private var nextGeneration = 1L
    private val records = linkedMapOf<String, DeskClockOwnerGapRecord>()

    fun peek(key: String): DeskClockOwnerGapRecord? = records[key]

    fun hasGap(key: String): Boolean = key in records

    fun activeKeys(): Set<String> = records.keys.toSet()

    /**
     * Ap dung KEEP/DROP. DROP thang (xoa record); KEEP moi gan deadline;
     * KEEP trung chi doi mode / re-arm phan con lai — khong gia han absolute.
     */
    fun applyRemovalDecision(
        key: String,
        decision: DeskClockNotifRemovalDecision,
        ownerInForeground: Boolean,
    ): DeskClockOwnerGapEffect {
        when (decision) {
            DeskClockNotifRemovalDecision.DROP_ENDED -> {
                val prev = records.remove(key)
                return DeskClockOwnerGapEffect.Drop(
                    key = key,
                    generation = prev?.generation ?: 0L,
                    cause = "removal_drop",
                )
            }
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP -> {
                val mode =
                    if (ownerInForeground) {
                        DeskClockOwnerGapMode.IN_OWNER_FG
                    } else {
                        DeskClockOwnerGapMode.LEAVE_GRACE
                    }
                val existing = records[key]
                if (existing != null) {
                    // Duplicate KEEP (Scrim+collection): giu firstRemoved/deadline.
                    val updated = existing.copy(mode = mode)
                    records[key] = updated
                    return armFromRecord(updated)
                }
                val now = clock.elapsedRealtime()
                val gen = nextGeneration++
                val record =
                    DeskClockOwnerGapRecord(
                        key = key,
                        generation = gen,
                        firstRemovedElapsedMs = now,
                        absoluteDeadlineElapsedMs = now + maxGapMs,
                        mode = mode,
                    )
                records[key] = record
                return armFromRecord(record)
            }
        }
    }

    /** FG owner doi — re-arm theo mode; delay khong vuot absolute con lai. */
    fun onOwnerForegroundChanged(
        key: String,
        ownerInForeground: Boolean,
    ): DeskClockOwnerGapEffect {
        val existing = records[key] ?: return DeskClockOwnerGapEffect.None
        val mode =
            if (ownerInForeground) {
                DeskClockOwnerGapMode.IN_OWNER_FG
            } else {
                DeskClockOwnerGapMode.LEAVE_GRACE
            }
        if (existing.mode == mode) return DeskClockOwnerGapEffect.None
        val updated = existing.copy(mode = mode)
        records[key] = updated
        return armFromRecord(updated)
    }

    /** Valid matching Clock event post — clear; timeout gen cu khong drop gen moi. */
    fun clearOnValidatedMatchingPost(key: String): Long? {
        return records.remove(key)?.generation
    }

    /** Same-key post khong match Stopwatch/Timer — drop stale retained. */
    fun reconcileNonMatchingSameKeyPost(key: String): DeskClockOwnerGapEffect {
        val prev = records.remove(key) ?: return DeskClockOwnerGapEffect.None
        return DeskClockOwnerGapEffect.Drop(
            key = key,
            generation = prev.generation,
            cause = "nonmatching_replace",
        )
    }

    /**
     * Timeout da arm: chi drop khi generation + armSequence (+ due) con khop.
     * Stale leave-grace sau FG re-arm (cung gen, seq moi) → no-op.
     * Stale old-gen sau rebind → no-op. True expiry → Drop.
     * Cap tuyet doi — khong phu thuoc activeSbns==null; khong sliding-window.
     */
    fun onArmedTimeout(
        key: String,
        generation: Long,
        armSequence: Long,
        dueDeadlineElapsedMs: Long = -1L,
    ): DeskClockOwnerGapEffect {
        val existing = records[key] ?: return DeskClockOwnerGapEffect.None
        if (existing.generation != generation) return DeskClockOwnerGapEffect.None
        if (existing.armSequence != armSequence) return DeskClockOwnerGapEffect.None
        if (
            dueDeadlineElapsedMs >= 0L &&
                existing.dueDeadlineElapsedMs != dueDeadlineElapsedMs
        ) {
            return DeskClockOwnerGapEffect.None
        }
        records.remove(key)
        return DeskClockOwnerGapEffect.Drop(
            key = key,
            generation = generation,
            cause = "absolute_deadline",
        )
    }

    fun clearKey(key: String) {
        records.remove(key)
    }

    fun clearAll(): List<DeskClockOwnerGapRecord> {
        val out = records.values.toList()
        records.clear()
        return out
    }

    private fun armFromRecord(record: DeskClockOwnerGapRecord): DeskClockOwnerGapEffect {
        val now = clock.elapsedRealtime()
        val remainingAbsolute =
            (record.absoluteDeadlineElapsedMs - now).coerceAtLeast(0L)
        if (remainingAbsolute == 0L) {
            records.remove(record.key)
            return DeskClockOwnerGapEffect.Drop(
                key = record.key,
                generation = record.generation,
                cause = "absolute_deadline",
            )
        }
        val delay =
            when (record.mode) {
                DeskClockOwnerGapMode.IN_OWNER_FG -> remainingAbsolute
                DeskClockOwnerGapMode.LEAVE_GRACE ->
                    minOf(leaveGraceMs, remainingAbsolute)
            }
        val cause =
            when (record.mode) {
                DeskClockOwnerGapMode.IN_OWNER_FG -> "gap_max"
                DeskClockOwnerGapMode.LEAVE_GRACE -> "leave_grace"
            }
        // Re-arm FG flip: giu absolute, tang armSequence — khong sliding max-gap.
        val seq = record.armSequence + 1L
        val due = now + delay
        val armed =
            record.copy(
                armSequence = seq,
                dueDeadlineElapsedMs = due,
            )
        records[record.key] = armed
        return DeskClockOwnerGapEffect.ArmTimer(
            key = armed.key,
            generation = armed.generation,
            delayMs = delay,
            cause = cause,
            armSequence = seq,
            dueDeadlineElapsedMs = due,
        )
    }
}
