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

package com.android.systemui.axdynamicbar.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.axdynamicbar.domain.AxDynamicBarInteractor
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffController
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffMeasuredLayout
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffPhase
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffSnapshot
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseProbeController
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseProbePhase
import com.android.systemui.axdynamicbar.shared.StatusBarPillFrame
import com.android.systemui.axdynamicbar.shared.StatusBarNeighborMotionEmitter
import com.android.systemui.axdynamicbar.shared.StatusBarNeighborMotionRequest
import com.android.systemui.axdynamicbar.shared.StatusBarNeighborTrigger
import com.android.systemui.axdynamicbar.shared.StatusBarPillFrameTracker
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffTryCompleteCleanup
import com.android.systemui.axdynamicbar.shared.statusBarNeighborTriggerForCardSlot
import com.android.systemui.axdynamicbar.shared.statusBarNeighborTriggerOf
import com.android.systemui.axdynamicbar.shared.expandOnlyEventIdFor
import com.android.systemui.axdynamicbar.ui.compose.StatusBarPillPresentation
import com.android.systemui.axdynamicbar.ui.compose.collapseChronometerTickDelayMs
import com.android.systemui.axdynamicbar.ui.compose.frozenPillChronometerIsTicking
import com.android.systemui.axdynamicbar.ui.compose.frozenPillHasDynamicChronometer
import com.android.systemui.axdynamicbar.ui.compose.frozenPillOwnerNeedsUpdate
import com.android.systemui.axdynamicbar.ui.compose.refreshFrozenPillChronometerPresentation
import com.android.systemui.axdynamicbar.ui.compose.resolveFrozenPillEventForSelection
import com.android.systemui.axdynamicbar.ui.compose.shouldSyncFrozenPillPresentation
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationOf
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SysUISingleton
class AxDynamicBarStatusBarExpansion
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val interactor: AxDynamicBarInteractor,
) {
    private val _intent = MutableStateFlow(false)
    // Chronometer: chi mo dung 1 event.id (multi-timer tach chip). null = stack nhu cu.
    private val _expandOnlyEventId = MutableStateFlow<String?>(null)
    // Confirmed card ID bridges the asynchronous pinned-top flow (including media -> media).
    private var confirmedExpandedOwnerId: String? = null

    val isExpanded: StateFlow<Boolean> =
        combine(_intent, interactor.isOnKeyguard) { intent, onKg -> intent && !onKg }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Lazily, false)

    val expandOnlyEventId: StateFlow<String?> = _expandOnlyEventId

    /**
     * An pill StatusBar tu luc expand den khi morph collapse settle — overlay giu
     * suot morph 600ms sau [isExpanded]=false; cleanup overlay +50ms sau handoff
     * (alert exit grace 400ms tach biet — xem ExpandedPanel).
     */
    private val _pillSuppressed = MutableStateFlow(false)
    val pillSuppressed: StateFlow<Boolean> = _pillSuppressed.asStateFlow()

    /**
     * Reclaim width slot Ax chip (khong GONE ComposeView clock/date) khi pill
     * suppress (morph handoff) — notif icons fill cho trong. Collapse: false
     * TRUOC morph ve dest.
     */
    private val _chipSlotReclaimed = MutableStateFlow(false)
    val chipSlotReclaimed: StateFlow<Boolean> = _chipSlotReclaimed.asStateFlow()

    /**
     * SSOT linear t∈[0,1] SLOT only (Snapshot — KHONG StateFlow/frame spam).
     * Clock nudge DOC LAP (spring 2 pha) — khong sample chung t.
     */
    var slotMotionLinearT by mutableFloatStateOf(1f)
        private set

    /** Huong run dang publish (true=EXPAND reclaim). */
    var slotMotionReclaiming by mutableStateOf(false)
        private set

    /** true khi dang tween slot. */
    var slotMotionRunning by mutableStateOf(false)
        private set

    /**
     * Outer pill occupied slot width (px) — telemetry slot natural; KHONG = host width.
     * NIC reflow thuc te: Chip layout modifier width + localView.requestLayout (khong doc Int nay).
     */
    var slotMotionSlotWidthPx by mutableFloatStateOf(0f)
        private set

    /** DensityDpi luc publish. */
    var slotMotionDensityDpi by mutableFloatStateOf(450f)
        private set

    /**
     * Neighbor trigger dang chay — CARD vs APP interpolator / nudge eligibility.
     */
    var slotNeighborTrigger by mutableStateOf(StatusBarNeighborTrigger.CARD_CLOSE)
        private set

    /**
     * Compose modern clock translationX — writer duy nhat tu chip nudge springs.
     * Classic View clock dung StatusBarClockNudgeController (khong dual-write).
     */
    var clockNudgeTranslationX by mutableFloatStateOf(0f)
        private set

    /** true khi spring nudge dang chay (Compose clock). */
    var clockNudgeRunning by mutableStateOf(false)
        private set

    /** Owner-app FG dang an pill — slot APP_HIDE/SHOW (khac morph CARD). */
    private val _ownerAppFgHidden = MutableStateFlow(false)
    val ownerAppFgHidden: StateFlow<Boolean> = _ownerAppFgHidden.asStateFlow()

    /**
     * Explicit neighbor motion request (monotonic seq + previous state).
     * Chip collect — KHONG infer APP_SHOW tu destination boolean sau khi FG cleared.
     * SSOT decision: [neighborMotionEmitter] (testable production path).
     */
    private val neighborMotionEmitter = StatusBarNeighborMotionEmitter()
    private val _neighborMotionRequest =
        MutableStateFlow<StatusBarNeighborMotionRequest?>(null)
    val neighborMotionRequest: StateFlow<StatusBarNeighborMotionRequest?> =
        _neighborMotionRequest.asStateFlow()

    /** Goi moi frame tu AxDynamicBarChip slot driver (telemetry T/trigger — khong publish host width). */
    fun publishSlotMotion(
        linearT: Float,
        reclaiming: Boolean,
        slotWidthPx: Float = slotMotionSlotWidthPx,
        densityDpi: Float = slotMotionDensityDpi,
        trigger: StatusBarNeighborTrigger =
            _neighborMotionRequest.value?.trigger
                ?: statusBarNeighborTriggerOf(
                    reclaiming,
                    appForeground = _ownerAppFgHidden.value && !_pillSuppressed.value,
                ),
    ) {
        slotMotionLinearT = linearT.coerceIn(0f, 1f)
        slotMotionReclaiming = reclaiming
        slotNeighborTrigger = trigger
        if (slotWidthPx > 0f && slotWidthPx.isFinite()) {
            slotMotionSlotWidthPx = slotWidthPx
        }
        if (densityDpi > 0f && densityDpi.isFinite()) {
            slotMotionDensityDpi = densityDpi
        }
        slotMotionRunning = true
    }

    /** Ket thuc / snap idle — t=1. */
    fun settleSlotMotion(reclaimed: Boolean) {
        slotMotionLinearT = 1f
        slotMotionReclaiming = reclaimed
        slotMotionRunning = false
    }

    fun publishClockNudgeTranslationX(tx: Float) {
        clockNudgeTranslationX = tx
        clockNudgeRunning = true
    }

    fun settleClockNudge() {
        clockNudgeTranslationX = 0f
        clockNudgeRunning = false
    }

    /** Dong bo StateFlow slot/FG tu emitter sau moi decision. */
    private fun publishEmitterState(req: StatusBarNeighborMotionRequest?) {
        _ownerAppFgHidden.value = neighborMotionEmitter.ownerAppFgHidden
        _chipSlotReclaimed.value = neighborMotionEmitter.chipSlotReclaimed
        if (req != null) {
            _neighborMotionRequest.value = req
        }
    }

    /**
     * Ap slot reclaim + emit request tai ownership decision (mot writer / emitter).
     */
    private fun applyChipSlotReclaimed(
        reclaiming: Boolean,
        trigger: StatusBarNeighborTrigger,
        previousOwnerAppFgHidden: Boolean = neighborMotionEmitter.ownerAppFgHidden,
        previousPillSuppressed: Boolean = neighborMotionEmitter.pillSuppressed,
    ): StatusBarNeighborMotionRequest? {
        neighborMotionEmitter.setPillSuppressed(_pillSuppressed.value)
        val req =
            neighborMotionEmitter.applyChipSlotReclaimed(
                reclaiming = reclaiming,
                trigger = trigger,
                previousOwnerAppFgHidden = previousOwnerAppFgHidden,
                previousPillSuppressed = previousPillSuppressed,
            )
        publishEmitterState(req)
        return req
    }

    /**
     * Dong bo reclaim KHONG emit request — expand seed / collapse settled
     * (motion da emit o collapse start).
     */
    private fun syncChipSlotReclaimed(reclaiming: Boolean) {
        neighborMotionEmitter.setPillSuppressed(_pillSuppressed.value)
        neighborMotionEmitter.syncChipSlotReclaimed(reclaiming)
        _chipSlotReclaimed.value = neighborMotionEmitter.chipSlotReclaimed
        _ownerAppFgHidden.value = neighborMotionEmitter.ownerAppFgHidden
    }

    /**
     * Dong bo FG owner hide → slot reclaim APP (khong doi morph pillSuppressed).
     * Emit APP_SHOW/HIDE theo huong doi — KHONG infer sau khi flag destination da clear.
     * Khi morph dang suppress: morph ownership slot; FG flag van set.
     *
     * @return request APP_* neu emit (test production path); null neu no-op / morph owns.
     */
    fun setOwnerAppFgHidden(hidden: Boolean): StatusBarNeighborMotionRequest? {
        neighborMotionEmitter.setPillSuppressed(_pillSuppressed.value)
        val req = neighborMotionEmitter.setOwnerAppFgHidden(hidden)
        // Luon sync FG flag (ke ca khi morph owns slot — emit null).
        _ownerAppFgHidden.value = neighborMotionEmitter.ownerAppFgHidden
        if (req != null) {
            _chipSlotReclaimed.value = neighborMotionEmitter.chipSlotReclaimed
            _neighborMotionRequest.value = req
        } else if (!_pillSuppressed.value) {
            // no-op duplicate: van sync reclaim tu emitter
            _chipSlotReclaimed.value = neighborMotionEmitter.chipSlotReclaimed
        }
        return req
    }

    private val _collapseSettled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseSettled: SharedFlow<Unit> = _collapseSettled.asSharedFlow()

    /**
     * Lifecycle collapse handoff (chat-1103212515 task-001).
     * Mot controller authoritative — Chip/Panel doc [collapseHandoffSnapshot].
     * Legacy drawn/progress/forceReveal giu stub bridge (khong staged ramp).
     */
    private val collapseHandoffCtrl = StatusBarCollapseHandoffController()
    internal val collapseProbeCtrl = StatusBarCollapseProbeController()
    private val _collapseHandoffSnapshot =
        MutableStateFlow(StatusBarCollapseHandoffSnapshot())
    val collapseHandoffSnapshot: StateFlow<StatusBarCollapseHandoffSnapshot> =
        _collapseHandoffSnapshot.asStateFlow()

    private fun publishHandoffSnapshot() {
        _collapseHandoffSnapshot.value = collapseHandoffCtrl.snapshot
    }

    /**
     * Collapse START — bat Prepare/Collapse session (morph con chay).
     * Cung layoutGen → no-op exactly-once; settle sau chi mark Ready (khong reset).
     */
    fun beginCollapseHandoffSession(layoutGeneration: Int, expectedOwnerId: String?): Int {
        val started = collapseHandoffCtrl.beginPrepare(layoutGeneration, expectedOwnerId)
        if (started) {
            collapseHandoffCtrl.enterCollapse(collapseHandoffCtrl.sessionGeneration)
            _collapseHandbackSession.value =
                CollapseHandbackSession(
                    holdGeneration = collapseHandoffCtrl.sessionGeneration,
                    layoutGeneration = layoutGeneration,
                    expectedOwnerId = expectedOwnerId,
                )
        }
        publishHandoffSnapshot()
        return collapseHandoffCtrl.sessionGeneration
    }

    /** Morph @rest — Ready (cung session; dummy van owns den transfer). */
    fun markCollapseHandoffSettled(sessionGeneration: Int): Boolean {
        val ok = collapseHandoffCtrl.markCollapseSettled(sessionGeneration)
        publishHandoffSnapshot()
        return ok
    }

    /**
     * Chip: real attach+layout ready / revoke (khong = SF PRESENT).
     * prepared=false revoke; null owner + prepared=true reject.
     * layoutWidth/Height/attached luu readiness record — transfer revalidate.
     */
    fun reportCollapseHandoffRealPrepared(
        sessionGeneration: Int,
        layoutGeneration: Int,
        ownerId: String?,
        prepared: Boolean,
        layoutWidthPx: Int = -1,
        layoutHeightPx: Int = -1,
        attached: Boolean = true,
    ): Boolean {
        val ok =
            collapseHandoffCtrl.reportRealPrepared(
                sessionGen = sessionGeneration,
                layoutGen = layoutGeneration,
                ownerId = ownerId,
                prepared = prepared,
                layoutWidthPx = layoutWidthPx,
                layoutHeightPx = layoutHeightPx,
                attached = attached,
            )
        publishHandoffSnapshot()
        if (ok && prepared) {
            _collapseHandbackDrawn.value =
                CollapseHandbackDrawnSignal(ownerId, layoutGeneration)
        }
        return ok
    }

    /** Current-owner retarget mid-flight — clear prepared, doi report lai. */
    fun retargetCollapseHandoffOwner(
        sessionGeneration: Int,
        ownerId: String?,
    ): Boolean {
        val ok = collapseHandoffCtrl.retargetExpectedOwner(sessionGeneration, ownerId)
        if (ok) {
            val session = _collapseHandbackSession.value
            if (session != null && session.holdGeneration == sessionGeneration) {
                _collapseHandbackSession.value = session.copy(expectedOwnerId = ownerId)
            }
            publishHandoffSnapshot()
        }
        return ok
    }

    /**
     * Readiness record hien tai (Chip report) — Panel transfer revalidate.
     * Detach sync revoke → null; khong dung state Compose tre.
     */
    fun collapseHandoffReadinessRecord(): StatusBarCollapseHandoffMeasuredLayout? =
        collapseHandoffCtrl.readinessRecord

    /**
     * Transfer: show real roi retire dummy.
     * CHI Ready + realPrepared that — [allowUnprepared] bi ignore (khong fabricate).
     * Bat buoc measured record + live owner/layoutGen; size/attach tu arg hoac record.
     * Null owner / missing record → reject (P1B); stale = no-op.
     */
    @Suppress("UNUSED_PARAMETER")
    fun transferCollapseHandoff(
        sessionGeneration: Int,
        allowUnprepared: Boolean = false,
        currentSelectedOwnerId: String? = null,
        currentLayoutGen: Int? = null,
        layoutWidthPx: Int? = null,
        layoutHeightPx: Int? = null,
        attached: Boolean? = null,
        chipExists: Boolean = true,
    ): Boolean {
        // Thieu size/attach live → dung record sync (detach da revoke ngay).
        val rec = collapseHandoffCtrl.readinessRecord
        val w = layoutWidthPx ?: rec?.widthPx
        val h = layoutHeightPx ?: rec?.heightPx
        val att = attached ?: rec?.attached
        val ok =
            collapseHandoffCtrl.transfer(
                sessionGen = sessionGeneration,
                allowUnprepared = false,
                currentSelectedOwnerId = currentSelectedOwnerId,
                currentLayoutGen = currentLayoutGen,
                layoutWidthPx = w,
                layoutHeightPx = h,
                attached = att,
                chipExists = chipExists,
            )
        publishHandoffSnapshot()
        return ok
    }

    /**
     * Overlay hide → Cleanup → Idle.
     * Guard session + layout; beginCleanup fail / stale = total no-op (khong mutate legacy).
     */
    fun completeCollapseHandoffCleanup(
        sessionGeneration: Int,
        layoutGeneration: Int = _layoutGeneration.get(),
    ): Boolean {
        val ok =
            statusBarCollapseHandoffTryCompleteCleanup(
                ctrl = collapseHandoffCtrl,
                sessionGen = sessionGeneration,
                layoutGen = layoutGeneration,
            )
        if (!ok) {
            // Stale / lifecycle sai — khong publish/clear session moi hon.
            return false
        }
        _collapseHandbackComplete.value =
            CollapseHandbackCompleteSignal(
                holdGeneration = sessionGeneration,
                layoutGeneration = layoutGeneration,
                abort = false,
            )
        _collapseHandbackSession.value = null
        _collapseHandbackProgress.value = null
        publishHandoffSnapshot()
        return true
    }

    fun cancelCollapseHandoff() {
        collapseHandoffCtrl.cancel()
        _collapseHandbackDrawn.value = null
        _collapseHandbackProgress.value = null
        _collapseHandbackSession.value = null
        _collapseHandbackComplete.value =
            CollapseHandbackCompleteSignal(
                holdGeneration = -1,
                layoutGeneration = _layoutGeneration.get(),
                abort = true,
            )
        publishHandoffSnapshot()
    }

    // --- Legacy bridge (signature cu; uy quyen controller moi) ---

    data class CollapseHandbackDrawnSignal(
        val ownerId: String?,
        val layoutGeneration: Int,
    )

    data class CollapseHandbackCompleteSignal(
        val holdGeneration: Int,
        val layoutGeneration: Int,
        val abort: Boolean = false,
    )

    /** Legacy staged complementary — transfer atomic thay the. */
    data class CollapseHandbackProgress(
        val holdGeneration: Int,
        val layoutGeneration: Int,
        val ownerId: String?,
        val revealRequested: Boolean,
        val trueLayerFraction: Float,
        val drawCredible: Boolean,
        val handoverProgress: Float = 0f,
    )

    /** Legacy force-reveal stub — blind transfer bypass DISABLED (task-002 B3). */
    data class CollapseHandbackForceReveal(
        val requestId: Long,
        val holdGeneration: Int,
        val layoutGeneration: Int,
        val forceFullRecovery: Boolean = false,
    )

    data class CollapseHandbackSession(
        val holdGeneration: Int,
        val layoutGeneration: Int,
        val expectedOwnerId: String?,
    )

    private val _collapseHandbackDrawn =
        MutableStateFlow<CollapseHandbackDrawnSignal?>(null)
    val collapseHandbackDrawn: StateFlow<CollapseHandbackDrawnSignal?> =
        _collapseHandbackDrawn.asStateFlow()

    private val _collapseHandbackComplete =
        MutableStateFlow<CollapseHandbackCompleteSignal?>(null)
    val collapseHandbackComplete: StateFlow<CollapseHandbackCompleteSignal?> =
        _collapseHandbackComplete.asStateFlow()

    private val _collapseHandbackProgress =
        MutableStateFlow<CollapseHandbackProgress?>(null)
    val collapseHandbackProgress: StateFlow<CollapseHandbackProgress?> =
        _collapseHandbackProgress.asStateFlow()

    private val forceRevealRequestSeq = AtomicLong(0)
    private val _collapseHandbackForceReveal =
        MutableSharedFlow<CollapseHandbackForceReveal>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val collapseHandbackForceReveal: SharedFlow<CollapseHandbackForceReveal> =
        _collapseHandbackForceReveal.asSharedFlow()

    private val _collapseHandbackSession =
        MutableStateFlow<CollapseHandbackSession?>(null)
    val collapseHandbackSession: StateFlow<CollapseHandbackSession?> =
        _collapseHandbackSession.asStateFlow()

    fun reportCollapseHandbackLiveDrawn(ownerId: String?, layoutGeneration: Int) {
        val snap = collapseHandoffCtrl.snapshot
        if (snap.phase != StatusBarCollapseHandoffPhase.Idle) {
            // Null owner = revoke (chip unmount), khong mark prepared.
            reportCollapseHandoffRealPrepared(
                sessionGeneration = snap.sessionGeneration,
                layoutGeneration = layoutGeneration,
                ownerId = ownerId,
                prepared = ownerId != null,
            )
        } else {
            _collapseHandbackDrawn.value =
                CollapseHandbackDrawnSignal(ownerId, layoutGeneration)
        }
    }

    fun reportCollapseHandbackProgress(progress: CollapseHandbackProgress) {
        if (progress.holdGeneration < 0) return
        val session = _collapseHandbackSession.value ?: return
        if (progress.holdGeneration != session.holdGeneration) return
        if (progress.layoutGeneration != session.layoutGeneration) return
        _collapseHandbackProgress.value = progress
        if (progress.drawCredible && progress.trueLayerFraction >= 0.99f) {
            reportCollapseHandoffRealPrepared(
                sessionGeneration = progress.holdGeneration,
                layoutGeneration = progress.layoutGeneration,
                ownerId = progress.ownerId,
                prepared = true,
            )
        }
    }

    fun requestCollapseHandbackForceReveal(
        holdGeneration: Int,
        layoutGeneration: Int,
        forceFullRecovery: Boolean = false,
    ): Long {
        // Blind transfer(allowUnprepared) DISABLED — giu LIVE dummy, khong fabricate prepared.
        // Listener legacy van nhan emit; recovery that = retarget + doi prepared / owner-end cancel.
        val requestId = forceRevealRequestSeq.incrementAndGet()
        _collapseHandbackForceReveal.tryEmit(
            CollapseHandbackForceReveal(
                requestId = requestId,
                holdGeneration = holdGeneration,
                layoutGeneration = layoutGeneration,
                forceFullRecovery = forceFullRecovery,
            )
        )
        return requestId
    }

    fun beginCollapseHandbackSession(
        holdGeneration: Int,
        layoutGeneration: Int,
        expectedOwnerId: String?,
    ) {
        if (collapseHandoffCtrl.snapshot.phase == StatusBarCollapseHandoffPhase.Idle) {
            beginCollapseHandoffSession(layoutGeneration, expectedOwnerId)
        } else {
            _collapseHandbackSession.value =
                CollapseHandbackSession(
                    holdGeneration = collapseHandoffCtrl.sessionGeneration,
                    layoutGeneration = layoutGeneration,
                    expectedOwnerId = expectedOwnerId,
                )
        }
    }

    fun reportCollapseHandbackOverlayHidden(holdGeneration: Int, layoutGeneration: Int) {
        // Guard ca session + layout — stale layoutGen = no-op.
        completeCollapseHandoffCleanup(
            sessionGeneration = holdGeneration,
            layoutGeneration = layoutGeneration,
        )
    }

    fun clearCollapseHandbackSignals() {
        _collapseHandbackDrawn.value = null
        _collapseHandbackComplete.value = null
        _collapseHandbackProgress.value = null
        _collapseHandbackSession.value = null
        // Reset controller neu con hold sot — tranh beginPrepare no-op cung layoutGen.
        if (collapseHandoffCtrl.phase != StatusBarCollapseHandoffPhase.Idle) {
            collapseHandoffCtrl.cancel()
            publishHandoffSnapshot()
        }
    }

    fun abortCollapseHandbackStatic() {
        cancelCollapseHandoff()
    }

    /**
     * Rect pill (px, screen) ghi TRUOC / khi collapsed — morph noi suy tu day.
     * width/height <=0 = chua capture.
     *
     * Lifecycle: giu qua collapse settle khi pill con mounted; chi
     * [clearCapturedPillFrame] khi invalidate that (unmount / keyguard / config).
     */
    data class PillFrame(
        val left: Int = 0,
        val top: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
    )

    private val pillFrames = StatusBarPillFrameTracker()
    private val _capturedPillFrame = MutableStateFlow(PillFrame())
    val capturedPillFrame: StateFlow<PillFrame> = _capturedPillFrame.asStateFlow()
    /** Owner id cua morph/capture frame — collapse retarget same-owner. */
    private val _capturedPillOwnerId = MutableStateFlow<String?>(null)
    val capturedPillOwnerId: StateFlow<String?> = _capturedPillOwnerId.asStateFlow()

    private fun publishMorphFrame() {
        // Cap atomic morphFrame+morphOwnerId — khong relabel bang lastObservedOwnerId.
        val m = pillFrames.morphFrame
        _capturedPillFrame.value = PillFrame(m.left, m.top, m.width, m.height)
        _capturedPillOwnerId.value = pillFrames.morphOwnerId
    }

    /**
     * Presentation freeze luc expand — overlay dummy dung snapshot, khong nhay event moi.
     * Clear khi collapse settle / collapseImmediate.
     * Collapse: refresh CHI frozenChronometerMs (task-048) — giu owner/style/gen.
     */
    private val _frozenPillPresentation = MutableStateFlow<StatusBarPillPresentation?>(null)
    val frozenPillPresentation: StateFlow<StatusBarPillPresentation?> =
        _frozenPillPresentation.asStateFlow()

    /** Ticker ~1Hz chi luc collapse + chrono dang chay — huy @settle/expand/immediate. */
    private var collapseChronometerJob: Job? = null

    /**
     * Tang khi expand — finishCollapsed / clear cu so sanh generation roi bo qua,
     * tranh xoa rect cua luot mo moi (reverse mid-flight).
     */
    private val _layoutGeneration = AtomicInteger(0)
    val layoutGeneration: Int
        get() = _layoutGeneration.get()

    fun capturePillFrame(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        ownerId: String? = null,
    ) {
        pillFrames.capture(
            left = left,
            top = top,
            width = width,
            height = height,
            intentExpanded = _intent.value,
            pillSuppressed = _pillSuppressed.value,
            pillSlotReclaimed = _chipSlotReclaimed.value,
            ownerId = ownerId,
        )
        publishMorphFrame()
    }

    /** Invalidate that — unmount / keyguard / rotation-config / destroyed owner. */
    fun clearCapturedPillFrame() {
        collapseProbeCtrl.cancelForRestore(closeAfterRestore = false)
        pillFrames.invalidate()
        publishMorphFrame()
    }

    fun clearFrozenPillPresentation() {
        stopCollapseChronometerRefresh()
        _frozenPillPresentation.value = null
    }

    /**
     * Refresh frozenChronometerMs theo live event cung owner (elapsedRealtime SSOT).
     * Khong doi owner/style/progress/generation. Stale gen/owner → no-op.
     */
    fun refreshFrozenPillChronometer(
        nowWallMs: Long = System.currentTimeMillis(),
        nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
    ) {
        val frozen = _frozenPillPresentation.value ?: return
        if (!frozenPillHasDynamicChronometer(frozen)) return
        val gen = frozen.generation
        val owner = frozen.ownerId
        val live = interactor.uiState.value.events.firstOrNull { it.id == owner }
        val next =
            refreshFrozenPillChronometerPresentation(
                frozen = frozen,
                liveEvent = live,
                nowWallMs = nowWallMs,
                nowElapsedMs = nowElapsedMs,
                requireSameOwner = true,
            )
                ?: return
        val cur = _frozenPillPresentation.value ?: return
        if (cur.generation != gen || cur.ownerId != owner) return
        if (next.frozenChronometerMs == cur.frozenChronometerMs) return
        _frozenPillPresentation.value = next
    }

    private fun stopCollapseChronometerRefresh() {
        collapseChronometerJob?.cancel()
        collapseChronometerJob = null
    }

    /**
     * Sau refresh @collapse-start: tick toi moc giay tiep roi ~1Hz den khi clear frozen.
     * Khong ticker luc expanded idle. Paused → khong job.
     */
    private fun startCollapseChronometerRefresh() {
        stopCollapseChronometerRefresh()
        val frozen = _frozenPillPresentation.value ?: return
        if (!frozenPillHasDynamicChronometer(frozen)) return
        val live =
            interactor.uiState.value.events.firstOrNull { it.id == frozen.ownerId }
                ?: frozen.event
        if (!frozenPillChronometerIsTicking(live)) return
        val gen = frozen.generation
        val owner = frozen.ownerId
        val countdown = live is IslandEvent.Timer
        collapseChronometerJob =
            applicationScope.launch {
                val firstMs = frozen.frozenChronometerMs ?: return@launch
                var delayMs = collapseChronometerTickDelayMs(firstMs, countdown)
                while (isActive) {
                    delay(delayMs)
                    val cur = _frozenPillPresentation.value
                    if (cur == null || cur.generation != gen || cur.ownerId != owner) break
                    if (_intent.value) break
                    refreshFrozenPillChronometer()
                    val after = _frozenPillPresentation.value?.frozenChronometerMs ?: break
                    // Countdown het gio: dung tick (delay<=0 da an toan 1000ms; khong spin).
                    if (countdown && after <= 0L) break
                    delayMs = collapseChronometerTickDelayMs(after, countdown)
                }
            }
    }

    /**
     * Dong bo frozen morph/dummy → event dang chon (stack commit CONFIRMED).
     *
     * Goi SAU [AxDynamicBarInteractor.pinEventById] (uiState.topEvent da B).
     * Khong tang [layoutGeneration]; giu freeze den [notifyCollapseSettled].
     * Id khong hop le / event bien mat → fallback topEvent; khong owner → no-op
     * (khong clear mid-flight → tranh 1-frame live nhay).
     */
    fun syncFrozenPillPresentationToEventId(eventId: String?) {
        applyFrozenPillSelectionSync(preferredEventId = eventId)
    }

    /**
     * Collapse / SNAP_LEAVE safeguard: resolve CURRENT pinned top,
     * khong clear presentation luc bat dau collapse.
     * Frozen alert (isAlert) giu nguyen — khong sync sang island top.
     */
    private fun ensureFrozenMatchesCurrentSelection() {
        val frozen = _frozenPillPresentation.value ?: return
        if (frozen.isAlert) return
        val preferred = liveCollapseOwnerId()
        applyFrozenPillSelectionSync(preferredEventId = preferred)
    }

    private fun applyFrozenPillSelectionSync(preferredEventId: String?) {
        val frozen = _frozenPillPresentation.value
        if (!shouldSyncFrozenPillPresentation(intentExpanded = _intent.value, frozen = frozen)) {
            return
        }
        val state = interactor.uiState.value
        // Stack island: khong dung alert khi sync theo event pin (alert path rieng).
        val display =
            resolveFrozenPillEventForSelection(
                events = state.events.filter { it !is IslandEvent.AospChip },
                preferredEventId = preferredEventId,
                topEvent = state.topEvent,
            )
                ?: return
        if (!frozenPillOwnerNeedsUpdate(frozen, display)) return
        val chipStyle = interactor.settings.chipStyle.value
        val gen = frozen?.generation ?: _layoutGeneration.get()
        _frozenPillPresentation.value =
            statusBarPillPresentationOf(
                event = display,
                isAlert = false,
                chipStyle = chipStyle,
                generation = gen,
                carrierName = null,
            )
    }

    fun beginCollapsedLayoutPass(): Int = _layoutGeneration.get()

    /**
     * Morph collapse da ve pill (progress~0, transition idle) → hien lai chip.
     * GIU captured rect (OGP co the khong re-fire khi chi doi alpha).
     * Bo qua neu generation doi / da expand lai.
     */
    fun notifyCollapseSettled(expectedGeneration: Int = _layoutGeneration.get()) {
        if (_layoutGeneration.get() != expectedGeneration) return
        if (_intent.value) return
        // Neighbor motion da emit o collapse(); sync reclaim, KHONG duplicate request.
        val reclaim = _ownerAppFgHidden.value
        syncChipSlotReclaimed(reclaim)
        _pillSuppressed.value = false
        neighborMotionEmitter.setPillSuppressed(false)
        pillFrames.notifyCollapseSettled()
        publishMorphFrame()
        // Clear frozen 1 lan sau handback — khong clear o collapse().
        clearFrozenPillPresentation()
        _collapseSettled.tryEmit(Unit)
    }

    init {
        interactor.isOnKeyguard
            .onEach { if (it) collapseImmediate() }
            .launchIn(applicationScope)

        interactor.uiState
            .onEach { state ->
                val pending = confirmedExpandedOwnerId
                if (pending != null &&
                    (state.topEvent?.id == pending || state.events.none { it.id == pending })
                ) confirmedExpandedOwnerId = null
            }
            .map { state ->
                state.events.isEmpty() || state.events.all { it is IslandEvent.AospChip }
            }
            .distinctUntilChanged()
            .onEach { shouldCollapse ->
                // Terminal: khong morph toi fabricated (0, topPad) sau clear capture.
                if (shouldCollapse) collapseTerminalNoDestination()
            }
            .launchIn(applicationScope)

        combine(
            interactor.qsExpansion.map { it > 0f }.distinctUntilChanged(),
            interactor.legacyShadeExpansion.map { it > 0f }.distinctUntilChanged(),
            interactor.isPanelExpanded,
        ) { qs, shade, panel -> qs || shade || panel }
            .distinctUntilChanged()
            .onEach { if (it) collapse() }
            .launchIn(applicationScope)
    }

    fun expand(onlyEventId: String? = null) {
        collapseProbeCtrl.cancelForRestore(closeAfterRestore = false)
        val state = interactor.uiState.value
        if (state.events.isEmpty() || state.events.all { it is IslandEvent.AospChip }) return
        stopCollapseChronometerRefresh()
        clearCollapseHandbackSignals()
        val gen = _layoutGeneration.incrementAndGet()
        // Freeze = dung event pill dang render (alert / onlyEventId / pinned top).
        val display =
            state.notificationAlert
                ?: onlyEventId?.let { id -> state.events.firstOrNull { it.id == id } }
                ?: state.topEvent
        confirmedExpandedOwnerId = null
        val chipStyle = interactor.settings.chipStyle.value
        if (display != null) {
            _frozenPillPresentation.value =
                statusBarPillPresentationOf(
                    event = display,
                    isAlert = state.notificationAlert != null,
                    chipStyle = chipStyle,
                    generation = gen,
                    carrierName = null,
                )
        }
        // Seed morph tu lastObserved — moi lan mo dung bbox that ke ca khi OGP im.
        pillFrames.seedMorphFromObserved()
        publishMorphFrame()
        // Slot reclaim chi SAU confirmPillSuppressed (overlay handoff).
        // expand() seed layout reclaim=false — KHONG emit (tranh APP_SHOW→CARD_CLOSE).
        syncChipSlotReclaimed(reclaiming = false)
        // Khong suppress ngay — doi overlay ve shell p≈0 aligned roi
        // [confirmPillSuppressed] (tranh blank 1 frame / double lech bounds).
        _expandOnlyEventId.value = onlyEventId
        _intent.value = true
    }

    /**
     * An pill that sau khi overlay temp shell da khop bounds pill @p≈0.
     * Dong thoi reclaim width slot → notif icons / clock reflow (500ms hide).
     * Chi khi intent con expand — khong suppress som luc collapse.
     */
    fun confirmPillSuppressed() {
        if (!_intent.value) return
        _pillSuppressed.value = true
        neighborMotionEmitter.setPillSuppressed(true)
        applyChipSlotReclaimed(
            reclaiming = true,
            trigger = statusBarNeighborTriggerForCardSlot(reclaiming = true),
        )
    }

    fun collapse() {
        // Repeated close guard: neu probe dang chay cho cung session thi khong restart probe
        if (collapseProbeCtrl.phase != StatusBarCollapseProbePhase.Idle) {
            return
        }
        // Safeguard: sync frozen → CURRENT selection TRUOC khi ha intent / clear
        // expandOnly (immediate collapse / SNAP_LEAVE sau commit van dung B).
        ensureFrozenMatchesCurrentSelection()
        // task-048: refresh chrono HIEN TAI @collapse-start (cung owner) — dummy
        // khong giu 4m11s stale roi jump 4m16s luc handback. Khong rebuild owner.
        refreshFrozenPillChronometer()

        val selectedOwner = liveCollapseOwnerId()
        val isSuppressedExpanded = _intent.value && _pillSuppressed.value
        val morphOwner = pillFrames.morphOwnerId
        val hasValidMorph = pillFrames.morphFrame.isValid
        val needsProbe =
            isSuppressedExpanded && !_ownerAppFgHidden.value &&
                !selectedOwner.isNullOrEmpty() &&
                (!hasValidMorph || morphOwner != selectedOwner)

        if (needsProbe && selectedOwner != null) {
            val requested =
                collapseProbeCtrl.requestProbe(
                    targetOwnerId = selectedOwner,
                    layoutGeneration = layoutGeneration,
                )
            if (requested) {
                // Phase 1: Giu _intent=true va giu slot reclaimed; cho probe layout chay.
                // Morph va slot animation chua chay o phase nay.
                return
            }
        }

        executeStandardCollapse()
    }

    private fun executeStandardCollapse() {
        // Restore layout slot TRUOC overlay morph ve dest (Samsung INVISIBLE reclaim).
        // Neu owner FG dang an pill → giu reclaim (APP_HIDE), khong force show slot.
        val reclaim = _ownerAppFgHidden.value
        val trigger =
            if (reclaim) StatusBarNeighborTrigger.APP_HIDE
            else statusBarNeighborTriggerForCardSlot(reclaiming = false)
        applyChipSlotReclaimed(reclaiming = reclaim, trigger = trigger)
        _intent.value = false
        _expandOnlyEventId.value = null
        // Ticker ~1Hz sau khi intent=false — chi luc dummy collapse (khong expanded idle).
        startCollapseChronometerRefresh()
        // Giu pillSuppressed + capturedPillFrame + frozen den notifyCollapseSettled.
    }

    internal fun liveCollapseOwnerId(): String? {
        val state = interactor.uiState.value
        val confirmed = confirmedExpandedOwnerId?.takeIf { id ->
            _intent.value && state.events.any { it.id == id && it !is IslandEvent.AospChip }
        }
        return state.notificationAlert?.id
            ?: _expandOnlyEventId.value
            ?: confirmed
            ?: state.topEvent?.id
    }

    internal fun isProbeDestinationLive(): Boolean {
        val request = collapseProbeCtrl.snapshot.value
        return _intent.value && !_ownerAppFgHidden.value &&
            request.layoutGeneration == layoutGeneration &&
            request.targetOwnerId == liveCollapseOwnerId()
    }

    internal fun finishProbeCollapse(generation: Long, contextToken: Long): Boolean {
        val result = collapseProbeCtrl.finishRestored(
            generation, layoutGeneration, liveCollapseOwnerId(), contextToken,
        ) ?: return false
        if (!result.closeAfterRestore || !_intent.value) return true
        val frame = result.frame
        val owner = result.ownerId
        if (frame != null && owner != null) {
            pillFrames.setMorphFrameForCollapse(frame, owner)
            publishMorphFrame()
        }
        executeStandardCollapse()
        return true
    }

    internal fun abandonDetachedProbe() {
        val request = collapseProbeCtrl.snapshot.value
        collapseProbeCtrl.abort()
        if (request.phase != StatusBarCollapseProbePhase.Idle &&
            request.closeAfterRestore && _intent.value
        ) executeStandardCollapse()
    }

    /**
     * Last-event / empty / all-Aosp swipe-hide: ket thuc visual outgoing ngay.
     * Khong morph/handback toi fabricated origin (0, topPad) sau chipExists clear
     * capture — tranh ghost pill duoi left status clock.
     */
    fun collapseTerminalNoDestination() {
        collapseImmediate()
    }

    /** Roi keyguard / bat buoc / terminal no-destination: tat suppress ngay. */
    private fun collapseImmediate() {
        collapseProbeCtrl.cancelForRestore(closeAfterRestore = false)
        stopCollapseChronometerRefresh()
        _intent.value = false
        _expandOnlyEventId.value = null
        val reclaim = _ownerAppFgHidden.value
        val trigger =
            if (reclaim) StatusBarNeighborTrigger.APP_HIDE
            else statusBarNeighborTriggerForCardSlot(reclaiming = false)
        applyChipSlotReclaimed(reclaiming = reclaim, trigger = trigger)
        _pillSuppressed.value = false
        neighborMotionEmitter.setPillSuppressed(false)
        abortCollapseHandbackStatic()
        clearCapturedPillFrame()
        clearFrozenPillPresentation()
    }

    /**
     * Cap nhat expand-only khi stack commit doi chip (timer → media = null, media → timer = id).
     * Dong thoi sync frozen morph/dummy → event vua pin (atomic voi pin, truoc collapse).
     * Khong doi _intent — panel dang mo. Drag tentative (commit false) khong goi.
     */
    fun setExpandOnlyEventId(onlyEventId: String?) {
        _expandOnlyEventId.value = onlyEventId
        // preferred = expand-only id (chrono); media null → resolve dung topEvent sau pin.
        syncFrozenPillPresentationToEventId(onlyEventId)
    }

    /**
     * Confirmed stack selection theo event.id that (media↔media null→null van sync).
     * ExpandedIslandContent goi sau pinEventById voi id the moi.
     */
    fun onConfirmedStackSelection(eventId: String) {
        val state = interactor.uiState.value
        val selected = state.events.firstOrNull { it.id == eventId }
        confirmedExpandedOwnerId = selected?.id?.takeIf { state.topEvent?.id != it }
        _expandOnlyEventId.value = selected?.let { expandOnlyEventIdFor(it) }
        syncFrozenPillPresentationToEventId(eventId)
    }

    fun toggle(onlyEventId: String? = null) {
        if (_intent.value) collapse() else expand(onlyEventId)
    }
}
