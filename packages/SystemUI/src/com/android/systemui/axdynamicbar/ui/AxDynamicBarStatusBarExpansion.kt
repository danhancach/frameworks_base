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

import com.android.systemui.axdynamicbar.domain.AxDynamicBarInteractor
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.StatusBarPillFrameTracker
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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
     * Reclaim width slot Ax chip (khong GONE ComposeView clock/date) sau overlay
     * handoff — notif icons fill cho trong. Collapse: false TRUOC morph ve dest.
     */
    private val _chipSlotReclaimed = MutableStateFlow(false)
    val chipSlotReclaimed: StateFlow<Boolean> = _chipSlotReclaimed.asStateFlow()

    private val _collapseSettled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseSettled: SharedFlow<Unit> = _collapseSettled.asSharedFlow()

    /**
     * Chip StatusBar bao live pill da ve sau unsuppress (task-043/044 handback).
     * StateFlow giu latest — Panel doc som (truoc collect) khong mat early ack (FAIL#2).
     * Stale gen/owner reject o [CollapseHandbackHoldController.acknowledgeLiveDrawn].
     */
    data class CollapseHandbackDrawnSignal(
        val ownerId: String?,
        val layoutGeneration: Int,
    )

    /**
     * Overlay chrome da an / host sap release — Chip moi release staticMode (phase5).
     * [abort]=true: collapseImmediate / reverse — chip bo keepStatic ngay.
     */
    data class CollapseHandbackCompleteSignal(
        val holdGeneration: Int,
        val layoutGeneration: Int,
        val abort: Boolean = false,
    )

    private val _collapseHandbackDrawn =
        MutableStateFlow<CollapseHandbackDrawnSignal?>(null)
    val collapseHandbackDrawn: StateFlow<CollapseHandbackDrawnSignal?> =
        _collapseHandbackDrawn.asStateFlow()

    private val _collapseHandbackComplete =
        MutableStateFlow<CollapseHandbackCompleteSignal?>(null)
    val collapseHandbackComplete: StateFlow<CollapseHandbackCompleteSignal?> =
        _collapseHandbackComplete.asStateFlow()

    /** Goi tu AxDynamicBarChip sau OnDraw credible (hoac no-owner). */
    fun reportCollapseHandbackLiveDrawn(ownerId: String?, layoutGeneration: Int) {
        _collapseHandbackDrawn.value =
            CollapseHandbackDrawnSignal(
                ownerId = ownerId,
                layoutGeneration = layoutGeneration,
            )
    }

    /** Panel: chrome=0 + hide confirm — Chip phase5 release static. */
    fun reportCollapseHandbackOverlayHidden(holdGeneration: Int, layoutGeneration: Int) {
        _collapseHandbackComplete.value =
            CollapseHandbackCompleteSignal(
                holdGeneration = holdGeneration,
                layoutGeneration = layoutGeneration,
                abort = false,
            )
    }

    /** Xoa latch drawn/complete — settle moi / expand / abort. */
    fun clearCollapseHandbackSignals() {
        _collapseHandbackDrawn.value = null
        _collapseHandbackComplete.value = null
    }

    /** collapseImmediate / reverse: ep Chip bo keepStatic, khong stuck staticMode. */
    fun abortCollapseHandbackStatic() {
        _collapseHandbackDrawn.value = null
        _collapseHandbackComplete.value =
            CollapseHandbackCompleteSignal(
                holdGeneration = -1,
                layoutGeneration = _layoutGeneration.get(),
                abort = true,
            )
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

    private fun publishMorphFrame() {
        val m = pillFrames.morphFrame
        _capturedPillFrame.value = PillFrame(m.left, m.top, m.width, m.height)
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
        val state = interactor.uiState.value
        val preferred = _expandOnlyEventId.value ?: state.topEvent?.id
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
        // Slot da restore o collapse(); giu false. Pill VISIBLE @settle.
        _chipSlotReclaimed.value = false
        _pillSuppressed.value = false
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
            .map { state ->
                state.events.isEmpty() || state.events.all { it is IslandEvent.AospChip }
            }
            .distinctUntilChanged()
            .onEach { shouldCollapse ->
                if (shouldCollapse) collapse()
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
        _chipSlotReclaimed.value = false
        // Khong suppress ngay — doi overlay ve shell p≈0 aligned roi
        // [confirmPillSuppressed] (tranh blank 1 frame / double lech bounds).
        _expandOnlyEventId.value = onlyEventId
        _intent.value = true
    }

    /**
     * An pill that sau khi overlay temp shell da khop bounds pill @p≈0.
     * Chi khi intent con expand — khong suppress som luc collapse.
     * Sau handoff: reclaim width slot → notif icons reflow.
     */
    fun confirmPillSuppressed() {
        if (!_intent.value) return
        _pillSuppressed.value = true
        _chipSlotReclaimed.value = true
    }

    fun collapse() {
        // Safeguard: sync frozen → CURRENT selection TRUOC khi ha intent / clear
        // expandOnly (immediate collapse / SNAP_LEAVE sau commit van dung B).
        ensureFrozenMatchesCurrentSelection()
        // task-048: refresh chrono HIEN TAI @collapse-start (cung owner) — dummy
        // khong giu 4m11s stale roi jump 4m16s luc handback. Khong rebuild owner.
        refreshFrozenPillChronometer()
        // Restore layout slot TRUOC overlay morph ve dest (Samsung INVISIBLE reclaim).
        // KHONG clear frozen / pillSuppressed o day — giu den notifyCollapseSettled.
        _chipSlotReclaimed.value = false
        _intent.value = false
        _expandOnlyEventId.value = null
        // Ticker ~1Hz sau khi intent=false — chi luc dummy collapse (khong expanded idle).
        startCollapseChronometerRefresh()
        // Giu pillSuppressed + capturedPillFrame + frozen den notifyCollapseSettled.
    }

    /** Roi keyguard / bat buoc: tat suppress ngay, khong cho morph SB. */
    private fun collapseImmediate() {
        stopCollapseChronometerRefresh()
        _intent.value = false
        _expandOnlyEventId.value = null
        _chipSlotReclaimed.value = false
        _pillSuppressed.value = false
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
        _expandOnlyEventId.value = selected?.let { expandOnlyEventIdFor(it) }
        syncFrozenPillPresentationToEventId(eventId)
    }

    fun toggle(onlyEventId: String? = null) {
        if (_intent.value) collapse() else expand(onlyEventId)
    }
}
