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

import android.os.Trace
import android.view.Choreographer
import android.view.View

/**
 * Gom cap nhat touchable InternalInsets: dirty ≤1 lan/frame, flush endpoint, huy theo gen.
 *
 * Framework (ViewRootImpl):
 * - [View.requestLayout] → mLayoutRequested=true + scheduleTraversals → measure/layout.
 * - [ViewRootImpl.invalidate] (~2870) → scheduleTraversals **khong** set mLayoutRequested.
 * - [ViewRootImpl.performTraversals] dispatchOnComputeInternalInsets (~4725) khi co listener,
 *   doc lap khoi nhanh measure/layout (chi khi mLayoutRequested).
 * - [ViewRootImpl.setTouchableRegion] (~13892) van requestLayout — khong dung lam shortcut.
 *
 * Dung [View.invalidate] de listener InternalInsets chay lai ma khong ep content remeasure.
 *
 * ## Generation + Choreographer (main-thread)
 *
 * Choreographer frame callback chay tren looper thread da post (thuong main).
 * [Choreographer.removeFrameCallback] chi huy callback **chua** chay; neu callback dang
 * trong hang doi cung frame / da detach identity, stale van co the toi.
 *
 * Vi vay:
 * - Moi lan schedule tao [Choreographer.FrameCallback] **gan gen + postSequence** luc post
 *   (khong dung 1 lambda doc `state.generation` luc chay — do khong validate stale).
 * - Cung gen sau [cancelPendingOnly]/[flushNow] van co the post lai: can **sequence**
 *   rieng moi lan post; stale callback cung gen khong duoc clear pending/framePosted moi.
 * - Stale callback **no-op toan bo**: khong clear `framePosted` / pending cua callback moi.
 * - [TouchableInsetsCoalesceState.onFrame] / [TouchableInsetsCoalesceState.flush] kiem tra
 *   gen **truoc** moi mutation pending.
 */

/** Ten Trace dem schedule vs dispatch (Perfetto slice sync ngan). */
internal const val AxDbInsetsScheduleTrace = "AxDbCollapse/insetsSchedule"
internal const val AxDbInsetsDispatchTrace = "AxDbCollapse/insetsDispatch"

/**
 * Seam post/remove frame callback — production dung Choreographer; test dung fake.
 * Khong dung `fun interface`: can **hai** abstract member (post + remove).
 */
internal interface FrameCallbackScheduler {
    fun postFrameCallback(callback: Choreographer.FrameCallback)

    fun removeFrameCallback(callback: Choreographer.FrameCallback)
}

/** Scheduler mac dinh — uy quyen [Choreographer]. */
internal class ChoreographerFrameScheduler(
    private val choreographer: Choreographer = Choreographer.getInstance(),
) : FrameCallbackScheduler {
    override fun postFrameCallback(callback: Choreographer.FrameCallback) {
        choreographer.postFrameCallback(callback)
    }

    override fun removeFrameCallback(callback: Choreographer.FrameCallback) {
        choreographer.removeFrameCallback(callback)
    }
}

/**
 * State may thuan — test coalesce / flush / cancel / stale gen khong can Choreographer.
 */
internal class TouchableInsetsCoalesceState {
    var generation: Long = 0L
        private set
    var pending: Boolean = false
        private set
    var scheduleRequests: Int = 0
        private set
    var dispatchCount: Int = 0
        private set

    /**
     * Danh dau dirty. @return true neu caller can postFrameCallback (lan dau trong frame).
     * Stale [gen] → no-op.
     */
    fun markDirty(gen: Long): Boolean {
        if (gen != generation) return false
        scheduleRequests++
        if (pending) return false
        pending = true
        return true
    }

    /**
     * Frame callback: chi mutate khi [gen] khop; stale → no-op (khong xoa pending moi).
     * @return true neu nen invalidate.
     */
    fun onFrame(gen: Long): Boolean {
        if (gen != generation) return false
        if (!pending) return false
        pending = false
        dispatchCount++
        return true
    }

    /**
     * Flush ngay (endpoint / dismiss): stale [gen] → no-op toan bo (khong xoa pending moi).
     * @return true neu invalidate.
     */
    fun flush(gen: Long): Boolean {
        if (gen != generation) return false
        pending = false
        dispatchCount++
        return true
    }

    /** Huy callback pending; khong bump gen. */
    fun cancelPending() {
        pending = false
    }

    /** Session doi / detach — bump gen + clear pending (stale frame no-op). */
    fun bumpGeneration(): Long {
        pending = false
        generation += 1L
        return generation
    }

    fun resetCounters() {
        scheduleRequests = 0
        dispatchCount = 0
    }
}

/**
 * Wrapper Choreographer: schedule ≤1 invalidate/frame; flush final; cancel khi detach.
 *
 * Posted callback **gan gen + postSequence** luc [schedule]. Cung generation sau
 * [cancelPendingOnly]/[flushNow] van post lai — stale callback cu phai no-op theo
 * sequence (khong chi gen). Khong dung mutable generation luc chay callback nhu bang
 * chung an toan; khong bump session gen moi frame.
 */
internal class TouchableInsetsRefreshCoalesce(
    private val scheduler: FrameCallbackScheduler = ChoreographerFrameScheduler(),
    private val onInvalidate: () -> Unit,
    private val traceEnabled: () -> Boolean = { true },
) {
    constructor(
        choreographer: Choreographer,
        onInvalidate: () -> Unit,
        traceEnabled: () -> Boolean = { true },
    ) : this(ChoreographerFrameScheduler(choreographer), onInvalidate, traceEnabled)

    private val state = TouchableInsetsCoalesceState()
    private var framePosted = false
    /** Gen da bind voi [postedCallback]; -1 = khong co callback dang treo. */
    private var postedGeneration: Long = -1L
    /**
     * Identity moi lan post (tang dan). remove/flush vo hieu hoa token cu bang cach
     * reset [postedSequence] = -1; schedule moi gan sequence moi (co the cung gen).
     */
    private var nextPostSequence: Long = 0L
    private var postedSequence: Long = -1L
    private var postedCallback: Choreographer.FrameCallback? = null

    val generation: Long
        get() = state.generation

    val scheduleRequests: Int
        get() = state.scheduleRequests

    val dispatchCount: Int
        get() = state.dispatchCount

    /** Test/observability: co frame callback dang treo. */
    val hasPostedFrameCallback: Boolean
        get() = framePosted

    /** Test/observability: gen gan voi callback dang treo (-1 neu khong). */
    val postedFrameGeneration: Long
        get() = postedGeneration

    /** Test/observability: sequence gan voi callback dang treo (-1 neu khong). */
    val postedFrameSequence: Long
        get() = postedSequence

    /** Goi sau khi da ghi bottom/bounds — gom invalidate 1 lan/frame. */
    fun schedule() {
        val gen = state.generation
        if (!state.markDirty(gen)) return
        if (traceEnabled()) {
            Trace.beginSection(AxDbInsetsScheduleTrace)
            Trace.endSection()
        }
        if (framePosted) return
        framePosted = true
        postedGeneration = gen
        val seq = nextPostSequence++
        postedSequence = seq
        // Capture gen+seq luc post — khong doc mutable posted* luc callback chay.
        val cb = Choreographer.FrameCallback { runPostedFrame(gen, seq) }
        postedCallback = cb
        scheduler.postFrameCallback(cb)
    }

    /**
     * Chay callback da post voi [boundGen]+[boundSequence].
     * Stale (sequence/gen khong khop active post) → no-op; khong clear framePosted/pending
     * cua schedule moi (ke ca cung generation).
     */
    internal fun runPostedFrame(boundGen: Long, boundSequence: Long) {
        if (
            !framePosted ||
                postedSequence != boundSequence ||
                postedGeneration != boundGen
        ) {
            // Stale: callback cu toi sau remove/flush/reschedule — khong dung state moi.
            return
        }
        framePosted = false
        postedGeneration = -1L
        postedSequence = -1L
        postedCallback = null
        if (state.onFrame(boundGen)) {
            dispatchInvalidate()
        }
    }

    /** Endpoint (bottom=0) / can insets ngay — invalidate + huy pending. */
    fun flushNow() {
        removeFrameCallback(/* clearPending= */ true)
        val gen = state.generation
        if (state.flush(gen)) {
            dispatchInvalidate()
        }
    }

    /** Detach / hide overlay / session moi. */
    fun cancelAndBumpGeneration() {
        removeFrameCallback(/* clearPending= */ true)
        state.bumpGeneration()
    }

    /** Chi huy pending, giu gen (vd. reverse mid-frame roi schedule lai). */
    fun cancelPendingOnly() {
        removeFrameCallback(/* clearPending= */ true)
        state.cancelPending()
    }

    fun resetCounters() {
        state.resetCounters()
    }

    private fun removeFrameCallback(clearPending: Boolean) {
        if (framePosted) {
            postedCallback?.let { scheduler.removeFrameCallback(it) }
            framePosted = false
            postedGeneration = -1L
            // Vo hieu hoa token cu — cung gen schedule sau khong nham identity.
            postedSequence = -1L
            postedCallback = null
        }
        if (clearPending) {
            state.cancelPending()
        }
    }

    private fun dispatchInvalidate() {
        if (traceEnabled()) {
            Trace.beginSection(AxDbInsetsDispatchTrace)
            try {
                onInvalidate()
            } finally {
                Trace.endSection()
            }
        } else {
            onInvalidate()
        }
    }
}

/** invalidate View — khong requestLayout (xem KDoc class). */
internal fun View.invalidateForInternalInsets() {
    invalidate()
}
