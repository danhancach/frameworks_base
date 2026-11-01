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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.pipeline.battery.domain.interactor.BatteryInteractor
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class AxDynamicBarKeyguardExpansion
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    private val interactor: AxDynamicBarInteractor,
    private val batteryInteractor: BatteryInteractor,
) {
    private val _intent = MutableStateFlow(false)
    // Chronometer: chi mo dung 1 event.id. null = stack nhu cu.
    private val _expandOnlyEventId = MutableStateFlow<String?>(null)

    val expandOnlyEventId: StateFlow<String?> = _expandOnlyEventId

    /**
     * True khi keo pill collapsed hoac dang snap-back/reorder settle.
     * Clear o cuoi animate / dispose / expand / roi keyguard — khong clear luc tha tay.
     */
    private val _isPillDragging = MutableStateFlow(false)
    val isPillDragging: StateFlow<Boolean> = _isPillDragging

    fun setPillDragging(dragging: Boolean) {
        _isPillDragging.value = dragging
    }

    // Event island hoac chip pin (mode1=charging, mode2=always). Event eligibility giu nguyen.
    private val hasChip =
        combine(
            interactor.uiState,
            interactor.settings.keyguardBatteryChipMode,
            batteryInteractor.isCharging,
        ) { uiState, battMode, charging ->
            val hasEvent = uiState.shouldShow && uiState.topEvent != null
            val batteryEligible = battMode == 2 || (battMode == 1 && charging)
            hasEvent || batteryEligible
        }.distinctUntilChanged()

    val canShow: StateFlow<Boolean> = run {
        val contextAndDoze =
            combine(
                interactor.isOnKeyguard,
                interactor.isDozing,
                interactor.dozeAmount.map { it > 0f }.distinctUntilChanged(),
                interactor.qsExpansion.map { it > 0f }.distinctUntilChanged(),
                interactor.isPanelExpanded,
            ) { onKg, dozing, dozeAmt, qs, panelExp ->
                onKg && !dozing && !dozeAmt && !qs && !panelExp
            }
        val shadeAndBouncer =
            combine(
                interactor.isBouncerShowing,
                interactor.legacyShadeExpansion.map { it >= 0.95f }.distinctUntilChanged(),
                hasChip,
            ) { bouncer, shadeFull, chip ->
                !bouncer && shadeFull && chip
            }
        combine(contextAndDoze, shadeAndBouncer) { a, b -> a && b }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, false)
    }

    val isExpanded: StateFlow<Boolean> =
        combine(_intent, canShow) { intent, show -> intent && show }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Lazily, false)

    private val _collapseSettled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val collapseSettled: SharedFlow<Unit> = _collapseSettled.asSharedFlow()

    /** ChipSection da doi LP sang expanded (MATCH) — AxComposeView.isExpandedProvider. */
    private val _isLpExpanded = MutableStateFlow(false)
    val isLpExpanded: StateFlow<Boolean> = _isLpExpanded

    /**
     * Rect pill (px, window) ghi TRUOC applyExpandedLp — morph noi suy tu day.
     * width/height <=0 = chua capture.
     */
    data class PillFrame(
        val left: Int = 0,
        val top: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
    )

    private val _capturedPillFrame = MutableStateFlow(PillFrame())
    val capturedPillFrame: StateFlow<PillFrame> = _capturedPillFrame

    /**
     * Tang khi expand — callback collapse/WRAP cu so sanh generation roi bo qua,
     * tranh reset/xoa rect cua luot mo moi.
     */
    private val _layoutGeneration = AtomicInteger(0)
    val layoutGeneration: Int
        get() = _layoutGeneration.get()

    /**
     * Gioi han hinh hoc pill (px, ~450dpi): host MATCH (~1080x2520) khong duoc ghi la pill.
     * MusicChip MaxW 180dp / H 58dp — cho du phong stack/peek.
     */
    private val maxPlausiblePillWidthPx = 700
    private val maxPlausiblePillHeightPx = 400

    fun capturePillFrame(left: Int, top: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        // Tu choi capture host con MATCH (fullscreen) lam kich thuoc pill.
        if (width > maxPlausiblePillWidthPx || height > maxPlausiblePillHeightPx) return
        // Dao chieu giua morph: giu rect pill goc — khong capture lai host dang MATCH.
        val cur = _capturedPillFrame.value
        if (cur.width > 0 && cur.height > 0 && (_isLpExpanded.value || _intent.value)) {
            return
        }
        _capturedPillFrame.value = PillFrame(left, top, width, height)
    }

    fun clearCapturedPillFrame() {
        _capturedPillFrame.value = PillFrame()
    }

    fun setLpExpanded(expanded: Boolean) {
        _isLpExpanded.value = expanded
        // Khong xoa rect o day — chi clear sau WRAP layout that (finishCollapsedLayout).
    }

    /**
     * Snapshot generation cho arm WRAP listener; chi finish khi generation con khop.
     */
    fun beginCollapsedLayoutPass(): Int = _layoutGeneration.get()

    /**
     * Host da layout WRAP that → moi ha isLpExpanded + xoa rect.
     * Bo qua neu da expand lai (generation doi / intent true).
     */
    fun finishCollapsedLayout(expectedGeneration: Int) {
        if (_layoutGeneration.get() != expectedGeneration) return
        if (_intent.value) return
        _isLpExpanded.value = false
        clearCapturedPillFrame()
    }

    fun notifyCollapseSettled() {
        _collapseSettled.tryEmit(Unit)
    }

    init {
        interactor.isOnKeyguard
            .onEach {
                if (!it) {
                    setPillDragging(false)
                    collapse()
                }
            }
            .launchIn(applicationScope)
    }

    fun expand(onlyEventId: String? = null) {
        setPillDragging(false)
        // Incoming-call / event van expand nhu cu; battery-only khi mode>0.
        // Mode1 charging gate: hasChip/canShow + pill chi goi expand khi dang hien.
        val hasTopEvent = interactor.uiState.value.topEvent != null
        val hasBattery = interactor.settings.keyguardBatteryChipMode.value > 0
        if (!hasTopEvent && !hasBattery) return
        _layoutGeneration.incrementAndGet()
        _expandOnlyEventId.value = onlyEventId
        _intent.value = true
    }

    fun collapse() {
        _intent.value = false
        _expandOnlyEventId.value = null
    }

    fun toggle(onlyEventId: String? = null) {
        if (_intent.value) collapse() else expand(onlyEventId)
    }
}
