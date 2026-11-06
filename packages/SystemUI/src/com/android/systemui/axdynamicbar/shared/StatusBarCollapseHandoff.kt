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

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Collapse content/glass handoff — mot session authoritative
 * (prepare → collapse → transfer → cleanup).
 *
 * Samsung runtime (G991N): prepare real INVISIBLE + dummy LIVE Chronometer;
 * mot callback main-thread show real roi hide CardStack; cleanup window sau.
 * Chi minh hoa kien truc — KHONG copy timer 600/650ms product.
 *
 * Cam: ca hai FG hidden; complementary dual-window lam "proof" blur;
 * claim drawWithContent/OnDraw = SurfaceFlinger PRESENT;
 * claim shared Compose snapshot = atomic SF screen swap.
 */

/** Phase lifecycle collapse handoff. */
enum class StatusBarCollapseHandoffPhase {
    /** Khong co session — idle chip / expand. */
    Idle,
    /** Bat dau collapse: real chuan bi layout (invisible), session armed. */
    Prepare,
    /** Morph / hold: dummy LIVE so huu FG+glass; real alpha0 nhung compose. */
    Collapse,
    /** Real attach/layout ready — van dummy visible cho den transfer. */
    Ready,
    /** Mot state change: real owns FG+glass; dummy retired. */
    Transfer,
    /** Overlay host cleanup sau transfer. */
    Cleanup,
}

/** Ai ve foreground content (Row/Circle) o endpoint. */
enum class StatusBarCollapseFgOwner {
    None,
    Dummy,
    Real,
}

/** Ai so huu glass/blur visible (mot owner — khong complementary dual). */
enum class StatusBarCollapseGlassOwner {
    None,
    Overlay,
    Native,
}

/**
 * Snapshot session public — Chip/Panel doc, chi controller ghi.
 */
data class StatusBarCollapseHandoffSnapshot(
    val phase: StatusBarCollapseHandoffPhase = StatusBarCollapseHandoffPhase.Idle,
    /** Tang moi lan beginPrepare — reject stale callback. */
    val sessionGeneration: Int = 0,
    val layoutGeneration: Int = -1,
    val expectedOwnerId: String? = null,
    /** Real attach + layout>0 (khong = SF present). */
    val realPrepared: Boolean = false,
    /** Da goi transfer (show real → retire dummy). */
    val transferred: Boolean = false,
    /** Cleanup overlay da xac nhan. */
    val cleanupDone: Boolean = false,
)

/**
 * Ownership FG/glass + liveness — derive tu phase (1 state change → flip ca cap).
 *
 * [dummyFgAlpha]/[realFgAlpha]: 0 hoac full follow — KHONG partial live FG.
 * [dummyLiveValue]: true suot Prepare/Collapse/Ready (Chronometer/media live).
 * [freezeMotion]: tat marquee/entry; KHONG khoa ticker khi liveValue.
 *
 * nativeGlassOn=false trong Prepare/Collapse/Ready: overlay so huu glass visible;
 * Chip van giu islandBlur/axBlurBackground enabled (alpha surface 0) de warm engine —
 * KHONG ve 2 full backgrounds. Outer FG alpha0 != SF PRESENT blur ready.
 */
data class StatusBarCollapseHandoffOwnership(
    val fgOwner: StatusBarCollapseFgOwner,
    val glassOwner: StatusBarCollapseGlassOwner,
    val dummyFgAlpha: Float,
    val realFgAlpha: Float,
    val overlayGlassOn: Boolean,
    val nativeGlassOn: Boolean,
    val freezeMotion: Boolean,
    val dummyLiveValue: Boolean,
    val realLiveValue: Boolean,
)

/**
 * Derive ownership tu phase. Idle + !suppressed → Real native (chip binh thuong).
 * Idle + suppressed ngoai session → Dummy (morph chua beginPrepare).
 */
fun statusBarCollapseHandoffOwnershipOf(
    phase: StatusBarCollapseHandoffPhase,
    pillSuppressed: Boolean,
    pillFollowAlpha: Float = 1f,
): StatusBarCollapseHandoffOwnership {
    val follow = pillFollowAlpha.coerceIn(0f, 1f)
    return when (phase) {
        StatusBarCollapseHandoffPhase.Idle ->
            if (pillSuppressed) {
                // Morph dang chay, session chua beginSettle — van Dummy LIVE + overlay glass.
                StatusBarCollapseHandoffOwnership(
                    fgOwner = StatusBarCollapseFgOwner.Dummy,
                    glassOwner = StatusBarCollapseGlassOwner.Overlay,
                    dummyFgAlpha = follow,
                    realFgAlpha = 0f,
                    overlayGlassOn = true,
                    nativeGlassOn = false,
                    freezeMotion = true,
                    dummyLiveValue = true,
                    realLiveValue = true,
                )
            } else {
                StatusBarCollapseHandoffOwnership(
                    fgOwner = StatusBarCollapseFgOwner.Real,
                    glassOwner = StatusBarCollapseGlassOwner.Native,
                    dummyFgAlpha = 0f,
                    realFgAlpha = 1f,
                    overlayGlassOn = false,
                    nativeGlassOn = true,
                    freezeMotion = false,
                    dummyLiveValue = false,
                    realLiveValue = true,
                )
            }
        StatusBarCollapseHandoffPhase.Prepare,
        StatusBarCollapseHandoffPhase.Collapse,
        StatusBarCollapseHandoffPhase.Ready ->
            StatusBarCollapseHandoffOwnership(
                fgOwner = StatusBarCollapseFgOwner.Dummy,
                glassOwner = StatusBarCollapseGlassOwner.Overlay,
                // Dummy giu FG den Transfer — ke ca sau unsuppress composition.
                dummyFgAlpha = follow,
                realFgAlpha = 0f,
                overlayGlassOn = true,
                nativeGlassOn = false,
                freezeMotion = true,
                dummyLiveValue = true,
                // Real prepared invisible: live data ready, chua ve FG.
                realLiveValue = true,
            )
        StatusBarCollapseHandoffPhase.Transfer,
        StatusBarCollapseHandoffPhase.Cleanup ->
            StatusBarCollapseHandoffOwnership(
                fgOwner = StatusBarCollapseFgOwner.Real,
                glassOwner = StatusBarCollapseGlassOwner.Native,
                dummyFgAlpha = 0f,
                realFgAlpha = 1f,
                overlayGlassOn = false,
                nativeGlassOn = true,
                freezeMotion = false,
                dummyLiveValue = false,
                realLiveValue = true,
            )
    }
}

/** Ca hai FG an — bug handoff. */
fun statusBarCollapseHandoffBothFgHidden(
    dummyFgAlpha: Float,
    realFgAlpha: Float,
): Boolean = dummyFgAlpha <= 0.01f && realFgAlpha <= 0.01f

/** Ownership hop le: dung 1 FG owner visible (hoac Idle none khi suppressed). */
fun statusBarCollapseHandoffFgOwnershipSafe(
    ownership: StatusBarCollapseHandoffOwnership,
): Boolean {
    if (statusBarCollapseHandoffBothFgHidden(ownership.dummyFgAlpha, ownership.realFgAlpha)) {
        return false
    }
    // Partial live FG cam.
    if (ownership.realFgAlpha > 0.01f && ownership.realFgAlpha < 0.99f) return false
    return when (ownership.fgOwner) {
        StatusBarCollapseFgOwner.Dummy ->
            ownership.dummyFgAlpha > 0.01f && ownership.realFgAlpha <= 0.01f
        StatusBarCollapseFgOwner.Real ->
            ownership.realFgAlpha > 0.01f && ownership.dummyFgAlpha <= 0.01f
        StatusBarCollapseFgOwner.None ->
            ownership.dummyFgAlpha <= 0.01f && ownership.realFgAlpha <= 0.01f
    }
}

/** Mot glass owner visible — khong complementary dual full. */
fun statusBarCollapseHandoffGlassOwnershipSafe(
    ownership: StatusBarCollapseHandoffOwnership,
): Boolean =
    when (ownership.glassOwner) {
        StatusBarCollapseGlassOwner.Overlay ->
            ownership.overlayGlassOn && !ownership.nativeGlassOn
        StatusBarCollapseGlassOwner.Native ->
            ownership.nativeGlassOn && !ownership.overlayGlassOn
        StatusBarCollapseGlassOwner.None ->
            !ownership.overlayGlassOn && !ownership.nativeGlassOn
    }

/**
 * Real prepared? Attach + layout>0 — KHONG dung OnDraw/drawWithContent lam PRESENT.
 * Alpha0 van co the layout (tranh circular visibility→ready).
 * Null ownerId → false (khong mark expected cu prepared).
 */
fun statusBarCollapseHandoffRealPrepared(
    attachedToWindow: Boolean,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    chipExists: Boolean,
    ownerId: String?,
    expectedOwnerId: String?,
): Boolean {
    if (!attachedToWindow) return false
    if (!chipExists) return false
    if (layoutWidthPx <= 0 || layoutHeightPx <= 0) return false
    if (ownerId.isNullOrEmpty()) return false
    if (expectedOwnerId != null && ownerId != expectedOwnerId) return false
    return true
}

/** Dummy live trong Prepare/Collapse/Ready — freezeMotion tach. */
fun statusBarCollapseHandoffDummyLiveValue(
    phase: StatusBarCollapseHandoffPhase,
): Boolean =
    phase == StatusBarCollapseHandoffPhase.Prepare ||
        phase == StatusBarCollapseHandoffPhase.Collapse ||
        phase == StatusBarCollapseHandoffPhase.Ready

fun statusBarCollapseHandoffFreezeMotion(
    phase: StatusBarCollapseHandoffPhase,
): Boolean =
    phase == StatusBarCollapseHandoffPhase.Prepare ||
        phase == StatusBarCollapseHandoffPhase.Collapse ||
        phase == StatusBarCollapseHandoffPhase.Ready

/**
 * Facade cleanup production — guard session + layout; stale = total no-op.
 * Expansion/Panel goi qua day; test stale newer-session dung helper nay.
 */
fun statusBarCollapseHandoffTryCompleteCleanup(
    ctrl: StatusBarCollapseHandoffController,
    sessionGen: Int,
    layoutGen: Int,
): Boolean {
    if (!ctrl.matchesSession(sessionGen)) return false
    if (layoutGen != ctrl.layoutGeneration) return false
    if (!ctrl.beginCleanup(sessionGen)) return false
    return ctrl.complete(sessionGen)
}

/** Live selected-owner (alert uu tien) — tach khoi morph/frozen presentation. */
fun statusBarCollapseHandoffLiveSelectedOwnerId(
    alertEventId: String?,
    primaryEventId: String?,
): String? = alertEventId ?: primaryEventId

/**
 * Owner-end authoritative: chip mat / chi con AospChip / empty.
 * KHONG coi expectedOwner null thoang qua la owner-end.
 */
fun statusBarCollapseHandoffOwnerEndedAuthoritative(
    chipMissing: Boolean,
    onlyAospOrEmpty: Boolean,
): Boolean = chipMissing || onlyAospOrEmpty

/**
 * Record layout do duoc theo session — validate lai, khong echo snap.layoutGeneration.
 * width/height <=0 hoac !attached = chua ready / revoked.
 */
data class StatusBarCollapseHandoffMeasuredLayout(
    val ownerId: String?,
    val sessionGeneration: Int,
    val widthPx: Int,
    val heightPx: Int,
    val attached: Boolean,
)

fun statusBarCollapseHandoffMeasuredLayoutValidForSession(
    measured: StatusBarCollapseHandoffMeasuredLayout?,
    expectedOwnerId: String?,
    sessionGeneration: Int,
): Boolean {
    if (measured == null) return false
    if (measured.sessionGeneration != sessionGeneration) return false
    if (!measured.attached) return false
    if (measured.widthPx <= 0 || measured.heightPx <= 0) return false
    if (measured.ownerId.isNullOrEmpty()) return false
    if (expectedOwnerId != null && measured.ownerId != expectedOwnerId) return false
    return true
}

/**
 * Session/owner doi: doc lai tu LayoutCoordinates hien co neu van attach+dung owner.
 * Khong accept prior-owner / size0 / detached. Khong can remeasure neu coords hop le.
 */
fun statusBarCollapseHandoffResolveLayoutAfterSessionChange(
    coordsAttached: Boolean,
    coordsOwnerId: String?,
    coordsWidthPx: Int,
    coordsHeightPx: Int,
    expectedOwnerId: String?,
    sessionGeneration: Int,
): StatusBarCollapseHandoffMeasuredLayout {
    val ownerOk =
        !coordsOwnerId.isNullOrEmpty() &&
            (expectedOwnerId == null || coordsOwnerId == expectedOwnerId)
    val ok =
        coordsAttached &&
            ownerOk &&
            coordsWidthPx > 0 &&
            coordsHeightPx > 0
    return StatusBarCollapseHandoffMeasuredLayout(
        ownerId = coordsOwnerId,
        sessionGeneration = sessionGeneration,
        widthPx = if (ok) coordsWidthPx else 0,
        heightPx = if (ok) coordsHeightPx else 0,
        attached = coordsAttached,
    )
}

/**
 * Ly do dispose coords — session-only KHONG clear LayoutCoordinates con attach.
 * Subtree dispose / host detach moi clear (P1A).
 */
enum class StatusBarCollapseHandoffCoordsDisposeReason {
    SessionOnlyRollover,
    SelectedSubtreeDispose,
    HostDetach,
}

fun statusBarCollapseHandoffClearPillCoordsOnDispose(
    reason: StatusBarCollapseHandoffCoordsDisposeReason,
): Boolean =
    when (reason) {
        StatusBarCollapseHandoffCoordsDisposeReason.SessionOnlyRollover -> false
        StatusBarCollapseHandoffCoordsDisposeReason.SelectedSubtreeDispose,
        StatusBarCollapseHandoffCoordsDisposeReason.HostDetach -> true
    }

/**
 * Outgoing AnimatedContent branch dispose: chi clear khi coords van thuoc branch do.
 * A dispose sau B placed (coordsOwner=B) → false — khong xoa B (P1A).
 */
fun statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords(
    disposedBranchOwnerId: String,
    currentCoordsOwnerId: String?,
): Boolean =
    !currentCoordsOwnerId.isNullOrEmpty() && currentCoordsOwnerId == disposedBranchOwnerId

/**
 * Production transfer readiness — bat buoc record + current owner + layoutGen.
 * Khong null-owner fallback rec.ownerId; khong rec==null legacy success (P1B).
 * Live size/attach uu tien arg; thieu arg → dung record (detach sync da revoke).
 */
fun statusBarCollapseHandoffResolveTransferReadiness(
    readinessRecord: StatusBarCollapseHandoffMeasuredLayout?,
    currentSelectedOwnerId: String?,
    currentLayoutGen: Int?,
    snapLayoutGen: Int,
    expectedOwnerId: String?,
    sessionGeneration: Int,
    layoutWidthPx: Int? = null,
    layoutHeightPx: Int? = null,
    attached: Boolean? = null,
): StatusBarCollapseHandoffMeasuredLayout? {
    if (readinessRecord == null) return null
    if (currentSelectedOwnerId.isNullOrEmpty()) return null
    if (currentLayoutGen == null || currentLayoutGen != snapLayoutGen) return null
    if (
        !statusBarCollapseHandoffMeasuredLayoutValidForSession(
            measured = readinessRecord,
            expectedOwnerId = expectedOwnerId,
            sessionGeneration = sessionGeneration,
        )
    ) {
        return null
    }
    if (currentSelectedOwnerId != readinessRecord.ownerId) return null
    if (expectedOwnerId != null && currentSelectedOwnerId != expectedOwnerId) return null
    val w = layoutWidthPx ?: readinessRecord.widthPx
    val h = layoutHeightPx ?: readinessRecord.heightPx
    val att = attached ?: readinessRecord.attached
    if (!att || w <= 0 || h <= 0) return null
    return StatusBarCollapseHandoffMeasuredLayout(
        ownerId = currentSelectedOwnerId,
        sessionGeneration = sessionGeneration,
        widthPx = w,
        heightPx = h,
        attached = att,
    )
}

/**
 * Transfer inputs: session Ready + realPrepared + live owner/layout/readiness hop le.
 * Stale gen/layout/owner → false (no-op).
 */
fun statusBarCollapseHandoffTransferInputsValid(
    snap: StatusBarCollapseHandoffSnapshot,
    sessionGen: Int,
    currentSelectedOwnerId: String?,
    currentLayoutGen: Int,
    layoutWidthPx: Int,
    layoutHeightPx: Int,
    attached: Boolean,
    chipExists: Boolean,
): Boolean {
    if (snap.sessionGeneration != sessionGen) return false
    if (snap.phase != StatusBarCollapseHandoffPhase.Ready) return false
    if (!snap.realPrepared) return false
    if (currentLayoutGen != snap.layoutGeneration) return false
    return statusBarCollapseHandoffRealPrepared(
        attachedToWindow = attached,
        layoutWidthPx = layoutWidthPx,
        layoutHeightPx = layoutHeightPx,
        chipExists = chipExists,
        ownerId = currentSelectedOwnerId,
        expectedOwnerId = snap.expectedOwnerId,
    )
}

/** Local panel hold chi clear khi van cung session (stale cleanup khong pha hold moi). */
fun statusBarCollapseHandoffLocalHoldReleaseAllowed(
    activeSessionGen: Int,
    completingSessionGen: Int,
): Boolean = activeSessionGen != 0 && activeSessionGen == completingSessionGen

/**
 * Sau soft+recovery timeout ma chua prepared: KEEP_OBSERVING (B1) — khong DIE_EXIT.
 * prepared → TRANSFER_ATTEMPT; owner-end authoritative → ABORT_CLEAR_HOLD.
 */
enum class StatusBarCollapseHandoffAfterRecoveryAction {
    TransferAttempt,
    AbortClearHold,
    KeepObserving,
}

fun statusBarCollapseHandoffAfterRecoveryTimeout(
    prepared: Boolean,
    ownerEndedAuthoritative: Boolean,
): StatusBarCollapseHandoffAfterRecoveryAction =
    when {
        ownerEndedAuthoritative -> StatusBarCollapseHandoffAfterRecoveryAction.AbortClearHold
        prepared -> StatusBarCollapseHandoffAfterRecoveryAction.TransferAttempt
        else -> StatusBarCollapseHandoffAfterRecoveryAction.KeepObserving
    }

/**
 * Doi realPrepared tren StateFlow: soft → recovery → (optional) observe mai den emit.
 * afterRecoveryKeepObserving=true = B1 fix (delayed ready 1s/5s van transfer).
 */
suspend fun statusBarCollapseHandoffAwaitPreparedReactive(
    handoffFlow: StateFlow<StatusBarCollapseHandoffSnapshot>,
    sessionGen: Int,
    softTimeoutMs: Long,
    recoveryTimeoutMs: Long,
    afterRecoveryKeepObserving: Boolean = true,
): Boolean {
    suspend fun waitOnce(timeoutMs: Long?): Boolean {
        val block: suspend () -> StatusBarCollapseHandoffSnapshot =
            {
                handoffFlow.first { snap ->
                    snap.sessionGeneration != sessionGen ||
                        snap.phase == StatusBarCollapseHandoffPhase.Idle ||
                        (snap.sessionGeneration == sessionGen && snap.realPrepared)
                }
            }
        val hit =
            if (timeoutMs == null) {
                block()
            } else {
                withTimeoutOrNull(timeoutMs) { block() }
            }
        return hit != null &&
            hit.sessionGeneration == sessionGen &&
            hit.phase != StatusBarCollapseHandoffPhase.Idle &&
            hit.realPrepared
    }
    if (waitOnce(softTimeoutMs)) return true
    if (waitOnce(recoveryTimeoutMs)) return true
    if (!afterRecoveryKeepObserving) return false
    return waitOnce(null)
}

/**
 * Controller authoritative — CHI noi ghi phase/session.
 * Timeout KHONG fabricate prepared / transfer unprepared real.
 */
class StatusBarCollapseHandoffController {
    var snapshot: StatusBarCollapseHandoffSnapshot = StatusBarCollapseHandoffSnapshot()
        private set

    /**
     * Readiness record gan voi reportRealPrepared — transfer validate lai.
     * width/height < 0 = chua ghi (legacy test path); >=0 bat buoc >0 khi prepared.
     */
    var readinessRecord: StatusBarCollapseHandoffMeasuredLayout? = null
        private set

    val phase: StatusBarCollapseHandoffPhase
        get() = snapshot.phase

    val sessionGeneration: Int
        get() = snapshot.sessionGeneration

    val layoutGeneration: Int
        get() = snapshot.layoutGeneration

    val expectedOwnerId: String?
        get() = snapshot.expectedOwnerId

    val realPrepared: Boolean
        get() = snapshot.realPrepared

    val transferred: Boolean
        get() = snapshot.transferred

    /** Hold overlay host? Prepare..Transfer (truoc Cleanup xong). */
    val holdActive: Boolean
        get() =
            when (snapshot.phase) {
                StatusBarCollapseHandoffPhase.Prepare,
                StatusBarCollapseHandoffPhase.Collapse,
                StatusBarCollapseHandoffPhase.Ready,
                StatusBarCollapseHandoffPhase.Transfer -> true
                StatusBarCollapseHandoffPhase.Cleanup -> !snapshot.cleanupDone
                StatusBarCollapseHandoffPhase.Idle -> false
            }

    private fun clearReadinessRecord() {
        readinessRecord = null
    }

    /**
     * Bat session moi (collapse START — morph con chay).
     * Cung layoutGen da begin → no-op (exactly-once).
     */
    fun beginPrepare(layoutGen: Int, ownerId: String?): Boolean {
        if (
            snapshot.layoutGeneration == layoutGen &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Idle &&
                !snapshot.cleanupDone
        ) {
            return false
        }
        clearReadinessRecord()
        snapshot =
            StatusBarCollapseHandoffSnapshot(
                phase = StatusBarCollapseHandoffPhase.Prepare,
                sessionGeneration = snapshot.sessionGeneration + 1,
                layoutGeneration = layoutGen,
                expectedOwnerId = ownerId,
                realPrepared = false,
                transferred = false,
                cleanupDone = false,
            )
        return true
    }

    /** Morph dang chay / @rest suppressed — dummy owns. */
    fun enterCollapse(sessionGen: Int): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (
            snapshot.phase != StatusBarCollapseHandoffPhase.Prepare &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Collapse
        ) {
            return false
        }
        snapshot = snapshot.copy(phase = StatusBarCollapseHandoffPhase.Collapse)
        return true
    }

    /**
     * Morph @rest + unsuppress composition — van Dummy owns den transfer.
     * Chi Prepare/Collapse → Ready. KHONG clear/reset session.
     */
    fun markCollapseSettled(sessionGen: Int): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (
            snapshot.phase != StatusBarCollapseHandoffPhase.Prepare &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Collapse
        ) {
            return false
        }
        snapshot = snapshot.copy(phase = StatusBarCollapseHandoffPhase.Ready)
        return true
    }

    /**
     * Chip bao real prepared / revoke. Stale gen/owner → reject.
     * prepared=false revoke readiness (detach/size0/owner doi).
     * Null owner + prepared=true → reject (khong mark expected cu).
     * layoutWidth/Height <0 = khong ghi size (legacy); >=0 luu record de transfer validate.
     */
    fun reportRealPrepared(
        sessionGen: Int,
        layoutGen: Int,
        ownerId: String?,
        prepared: Boolean,
        layoutWidthPx: Int = -1,
        layoutHeightPx: Int = -1,
        attached: Boolean = true,
    ): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (layoutGen != snapshot.layoutGeneration) return false
        if (
            snapshot.phase != StatusBarCollapseHandoffPhase.Prepare &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Collapse &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Ready
        ) {
            return false
        }
        if (prepared) {
            // Null owner khong duoc mark prepared cho expected cu.
            if (ownerId.isNullOrEmpty()) return false
            if (
                snapshot.expectedOwnerId != null &&
                    ownerId != snapshot.expectedOwnerId
            ) {
                return false
            }
            // Size0 / detach cung owner — reject prepared (revocable).
            if (layoutWidthPx == 0 || layoutHeightPx == 0 || !attached) {
                clearReadinessRecord()
                snapshot = snapshot.copy(realPrepared = false)
                return false
            }
            if (layoutWidthPx > 0 && layoutHeightPx > 0) {
                readinessRecord =
                    StatusBarCollapseHandoffMeasuredLayout(
                        ownerId = ownerId,
                        sessionGeneration = sessionGen,
                        widthPx = layoutWidthPx,
                        heightPx = layoutHeightPx,
                        attached = attached,
                    )
            }
            snapshot =
                snapshot.copy(
                    realPrepared = true,
                    phase =
                        if (snapshot.phase == StatusBarCollapseHandoffPhase.Ready) {
                            StatusBarCollapseHandoffPhase.Ready
                        } else {
                            StatusBarCollapseHandoffPhase.Collapse
                        },
                )
            return true
        }
        // Revoke: detach / size0 / owner mat — giu phase, clear prepared.
        clearReadinessRecord()
        snapshot = snapshot.copy(realPrepared = false)
        return true
    }

    /**
     * Retarget expected owner khi current-owner doi mid-flight (cung session/layout).
     * Different owner = clear prepared (doi Chip report lai).
     * expected null → ownerId (settle fill): giu prepared neu da report.
     */
    fun retargetExpectedOwner(sessionGen: Int, ownerId: String?): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (
            snapshot.phase != StatusBarCollapseHandoffPhase.Prepare &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Collapse &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Ready
        ) {
            return false
        }
        if (ownerId == snapshot.expectedOwnerId) return true
        val fillNullExpected =
            snapshot.expectedOwnerId == null && ownerId != null && snapshot.realPrepared
        val differentOwner =
            snapshot.expectedOwnerId != null &&
                ownerId != null &&
                ownerId != snapshot.expectedOwnerId
        if (differentOwner || ownerId == null) {
            clearReadinessRecord()
        }
        snapshot =
            snapshot.copy(
                expectedOwnerId = ownerId,
                realPrepared =
                    when {
                        fillNullExpected -> true
                        differentOwner -> false
                        ownerId == null -> false
                        else -> false
                    },
            )
        return true
    }

    /**
     * Transfer ownership: show real roi retire dummy.
     * CHI khi Ready + realPrepared that. KHONG fabricate prepared.
     * [allowUnprepared] bi ignore (legacy signature) — khong bypass blank.
     * Production bat buoc readiness record + currentSelectedOwnerId + layoutGen (P1B).
     * Khong null-owner fallback; khong rec==null legacy bool-only success.
     */
    @Suppress("UNUSED_PARAMETER")
    fun transfer(
        sessionGen: Int,
        allowUnprepared: Boolean = false,
        currentSelectedOwnerId: String? = null,
        currentLayoutGen: Int? = null,
        layoutWidthPx: Int? = null,
        layoutHeightPx: Int? = null,
        attached: Boolean? = null,
        chipExists: Boolean = true,
    ): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (snapshot.transferred) return true
        // Chi sau collapse settle (Ready) — khong transfer som tu Prepare/Collapse.
        if (snapshot.phase != StatusBarCollapseHandoffPhase.Ready) return false
        if (!snapshot.realPrepared) return false
        val layoutGen = currentLayoutGen ?: return false
        val measured =
            statusBarCollapseHandoffResolveTransferReadiness(
                readinessRecord = readinessRecord,
                currentSelectedOwnerId = currentSelectedOwnerId,
                currentLayoutGen = layoutGen,
                snapLayoutGen = snapshot.layoutGeneration,
                expectedOwnerId = snapshot.expectedOwnerId,
                sessionGeneration = sessionGen,
                layoutWidthPx = layoutWidthPx,
                layoutHeightPx = layoutHeightPx,
                attached = attached,
            ) ?: return false
        if (
            !statusBarCollapseHandoffTransferInputsValid(
                snap = snapshot,
                sessionGen = sessionGen,
                currentSelectedOwnerId = measured.ownerId,
                currentLayoutGen = layoutGen,
                layoutWidthPx = measured.widthPx,
                layoutHeightPx = measured.heightPx,
                attached = measured.attached,
                chipExists = chipExists,
            )
        ) {
            return false
        }
        snapshot =
            snapshot.copy(
                phase = StatusBarCollapseHandoffPhase.Transfer,
                transferred = true,
            )
        return true
    }

    /** Overlay hide confirm → Cleanup. */
    fun beginCleanup(sessionGen: Int): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (
            snapshot.phase != StatusBarCollapseHandoffPhase.Transfer &&
                snapshot.phase != StatusBarCollapseHandoffPhase.Cleanup
        ) {
            return false
        }
        snapshot = snapshot.copy(phase = StatusBarCollapseHandoffPhase.Cleanup)
        return true
    }

    /**
     * Cleanup xong → Idle.
     * Chi tu Cleanup (sau beginCleanup hop le) — khong Prepare→Idle chi bang gen.
     */
    fun complete(sessionGen: Int): Boolean {
        if (!matchesSession(sessionGen)) return false
        if (snapshot.phase != StatusBarCollapseHandoffPhase.Cleanup) return false
        clearReadinessRecord()
        snapshot =
            StatusBarCollapseHandoffSnapshot(
                phase = StatusBarCollapseHandoffPhase.Idle,
                sessionGeneration = snapshot.sessionGeneration,
                layoutGeneration = -1,
                expectedOwnerId = null,
                realPrepared = false,
                transferred = false,
                cleanupDone = true,
            )
        return true
    }

    /** Reverse / expand / abort — ve Idle, tang gen de reject stale. */
    fun cancel() {
        clearReadinessRecord()
        snapshot =
            StatusBarCollapseHandoffSnapshot(
                phase = StatusBarCollapseHandoffPhase.Idle,
                sessionGeneration = snapshot.sessionGeneration + 1,
                layoutGeneration = -1,
                expectedOwnerId = null,
                realPrepared = false,
                transferred = false,
                cleanupDone = true,
            )
    }

    /**
     * Owner end hop le (chip bien mat / events empty) — cancel session, tang gen.
     * Khac transient missing (retarget + doi prepared lai).
     */
    fun cancelOwnerEnded() {
        cancel()
    }

    fun matchesSession(sessionGen: Int): Boolean =
        sessionGen == snapshot.sessionGeneration &&
            snapshot.phase != StatusBarCollapseHandoffPhase.Idle

    fun ownership(pillSuppressed: Boolean, pillFollowAlpha: Float = 1f) =
        statusBarCollapseHandoffOwnershipOf(
            phase = snapshot.phase,
            pillSuppressed = pillSuppressed,
            pillFollowAlpha = pillFollowAlpha,
        )
}

/**
 * Bridge legacy hold chrome: overlay glass on khi Dummy owns; off sau Transfer.
 * Thay complementary staged ramp.
 */
fun statusBarCollapseHandoffOverlayGlassAlpha(
    ownership: StatusBarCollapseHandoffOwnership,
    persistentSharedGlassAlpha: Float,
): Float =
    if (ownership.overlayGlassOn) {
        persistentSharedGlassAlpha.coerceIn(0f, 1f)
    } else {
        0f
    }

fun statusBarCollapseHandoffNativeGlassFraction(
    ownership: StatusBarCollapseHandoffOwnership,
): Float = if (ownership.nativeGlassOn) 1f else 0f

/**
 * Blur surface prep: islandBlur/axBlurBackground van enabled trong Prepare/Collapse/Ready
 * (alpha ve = 0) — warm BlurEngine + frame track; KHONG bat nativeGlassOn visible
 * (tranh 2 full backgrounds). Outer FG alpha0 van khong chung minh SF PRESENT.
 */
fun statusBarCollapseHandoffNativeBlurPrepEnabled(
    phase: StatusBarCollapseHandoffPhase,
): Boolean =
    phase == StatusBarCollapseHandoffPhase.Prepare ||
        phase == StatusBarCollapseHandoffPhase.Collapse ||
        phase == StatusBarCollapseHandoffPhase.Ready ||
        phase == StatusBarCollapseHandoffPhase.Transfer ||
        phase == StatusBarCollapseHandoffPhase.Cleanup

fun statusBarCollapseHandoffTruePillAlpha(
    ownership: StatusBarCollapseHandoffOwnership,
): Float = ownership.realFgAlpha

fun statusBarCollapseHandoffDummyFgAlpha(
    ownership: StatusBarCollapseHandoffOwnership,
): Float = ownership.dummyFgAlpha
