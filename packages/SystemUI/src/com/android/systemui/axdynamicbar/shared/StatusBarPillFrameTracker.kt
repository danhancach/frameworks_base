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
 * Rect glass StatusBar (px, screen). width/height <=0 = chua capture.
 *
 * [lastObserved]: moi lan pill glass report (idle / remasure).
 * [morphFrame]: freeze source luc expand; update dest luc collapse suppressed.
 * Clear CHI qua [invalidate] (unmount / keyguard / config) — KHONG clear sau settle.
 */
internal data class StatusBarPillFrame(
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
) {
    val isValid: Boolean
        get() = width > 0 && height > 0
}

/**
 * Lifecycle rect pill StatusBar — tach lastObserved vs morph freeze.
 * Pure/state: test duoc khong can Interactor / Compose.
 *
 * morphFrame + morphOwnerId la cap atomic — CHI doi khi morphFrame doi
 * (seed / collapse retarget / settle / invalidate / capture ghi morph).
 * Cam relabel frozen geometry bang lastObservedOwnerId moi.
 */
internal class StatusBarPillFrameTracker(
    private val maxWidthPx: Int = 400,
    private val maxHeightPx: Int = 120,
) {
    var lastObserved: StatusBarPillFrame = StatusBarPillFrame()
        private set
    var morphFrame: StatusBarPillFrame = StatusBarPillFrame()
        private set
    /** Owner id lan capture thanh cong gan nhat — doi owner → chap nhan natural W moi. */
    var lastObservedOwnerId: String? = null
        private set
    /** Owner cua [morphFrame] — publish/same-owner guard dung cap nay, khong lastObserved. */
    var morphOwnerId: String? = null
        private set

    fun capture(
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        intentExpanded: Boolean,
        pillSuppressed: Boolean,
        /** Width slot Ax chip dang reclaim (icon reflow) — KHONG ghi lastObserved/morph. */
        pillSlotReclaimed: Boolean = false,
        /** Island/alert owner cua glass dang report — null = khong doi owner gate. */
        ownerId: String? = null,
    ) {
        if (width <= 0 || height <= 0) return
        if (width > maxWidthPx || height > maxHeightPx) return
        val prevOwner = lastObservedOwnerId
        val ownerChanged =
            ownerId != null && prevOwner != null && ownerId != prevOwner
        // Cam overwrite freeze 025 khi child shrink / slot reclaim (cung owner).
        if (
            !statusBarPillFrameCaptureAllowed(
                pillSlotReclaimed = pillSlotReclaimed,
                reportedWidth = width,
                lastObservedWidth = lastObserved.width,
                ownerChanged = ownerChanged,
            )
        ) {
            return
        }
        val next = StatusBarPillFrame(left, top, width, height)
        lastObserved = next
        if (ownerId != null) {
            lastObservedOwnerId = ownerId
        }
        val observedOwner = lastObservedOwnerId

        val cur = morphFrame
        // Expand: freeze start rect+owner — khong ghi de / relabel mid-flight.
        if (cur.isValid && intentExpanded) {
            return
        }
        // Collapse + slot an: remasure dest (placeholder co the shift).
        if (cur.isValid && pillSuppressed && !intentExpanded) {
            setMorphOwnedFrame(next, observedOwner)
            return
        }
        // Suppress + van intent (cho shell align): giu rect+owner goc.
        if (cur.isValid && pillSuppressed) {
            return
        }
        setMorphOwnedFrame(next, observedOwner)
    }

    /** Truoc moi expand: seed morph tu lastObserved (ke ca khi OGP khong re-fire). */
    fun seedMorphFromObserved() {
        val obs = lastObserved
        if (obs.isValid) {
            setMorphOwnedFrame(obs, lastObservedOwnerId)
        }
    }

    /**
     * Collapse morph settle — giu rect hop le cho lan mo tiep.
     * Dong bo morph ve lastObserved neu con; KHONG invalidate.
     */
    fun notifyCollapseSettled() {
        val obs = lastObserved
        if (obs.isValid) {
            setMorphOwnedFrame(obs, lastObservedOwnerId)
        }
    }

    /** Unmount / keyguard / rotation-config / destroyed owner. */
    fun invalidate() {
        lastObserved = StatusBarPillFrame()
        lastObservedOwnerId = null
        setMorphOwnedFrame(StatusBarPillFrame(), null)
    }

    /** Ghi morphFrame + morphOwnerId cung luc — publish atomic. */
    private fun setMorphOwnedFrame(frame: StatusBarPillFrame, ownerId: String?) {
        morphFrame = frame
        morphOwnerId = ownerId
    }

    /**
     * Commit truc tiep morphFrame + morphOwnerId tu ket qua probe layout thuc te cua B.
     * Chi chap nhan khi frame hop le va ownerId khong rong.
     */
    fun setMorphFrameForCollapse(frame: StatusBarPillFrame, ownerId: String) {
        if (!frame.isValid || ownerId.isEmpty()) return
        setMorphOwnedFrame(frame, ownerId)
    }
}

/**
 * Origin pill trong host overlay — KHONG (maxW-pillW)/2 khi thieu capture
 * (tranh morph tu giua man khi OGP chua re-fire).
 *
 * Luu y: khi thieu capture tra (0, topPad) — CHI an toan cho expand wait.
 * Collapse / owner-ended CAM dung fabricated nay (ghost pill duoi clock) —
 * xem [statusBarMorphShouldTerminalDismissNoDestination].
 */
internal fun statusBarMorphPillOriginInHostPx(
    capturedLeft: Int,
    capturedTop: Int,
    capturedWidth: Int,
    capturedHeight: Int,
    hostLocX: Int,
    hostLocY: Int,
    topPadPx: Int,
): Pair<Int, Int> {
    val left = if (capturedWidth > 0) capturedLeft - hostLocX else 0
    val top =
        if (capturedHeight > 0) {
            capturedTop - hostLocY
        } else {
            topPadPx.coerceAtLeast(0)
        }
    return left to top
}

/**
 * Terminal dismiss: khong con destination pill hop le (owner ended).
 * Khong morph/handback toi fabricated (0, topPad) / 56dp; clear overlay ngay.
 */
internal fun statusBarMorphShouldTerminalDismissNoDestination(
    ownerEndedAuthoritative: Boolean,
): Boolean = ownerEndedAuthoritative

/**
 * Destination owner cho collapse morph/handback — uu tien live selected,
 * khong stale frozen owner da dismiss. Owner-ended → null (terminal).
 */
internal fun statusBarMorphCollapseDestinationOwnerId(
    ownerEndedAuthoritative: Boolean,
    liveSelectedOwnerId: String?,
    frozenOwnerId: String?,
): String? {
    if (ownerEndedAuthoritative) return null
    return liveSelectedOwnerId?.takeIf { it.isNotEmpty() }
        ?: frozenOwnerId?.takeIf { it.isNotEmpty() }
}

/**
 * Collapse visual abort (khong mount host ve fabricated 0,topPad):
 * - owner-ended (terminal empty)
 * - collapsing + thieu capture (ke ca con live dest ID) — live ID ≠ measured rect
 * Co frozen geometry same-owner → [hasFrozenGeometryForLiveOwner]=true cho phep
 * neo bang frozen, khong fabricate.
 */
internal fun statusBarMorphShouldAbortCollapseVisual(
    ownerEndedAuthoritative: Boolean,
    hasValidPillCapture: Boolean,
    collapsing: Boolean,
    hasLiveDestinationOwner: Boolean,
    hasFrozenGeometryForLiveOwner: Boolean = false,
): Boolean {
    if (ownerEndedAuthoritative) return true
    if (!collapsing) return false
    if (hasValidPillCapture) return false
    if (hasFrozenGeometryForLiveOwner && hasLiveDestinationOwner) return false
    // Thieu capture + khong frozen same-owner — abort (cam 0,topPad).
    return true
}

/**
 * Capture chi hop le cho collapse khi owner match destination live.
 * Stale frame owner da dismiss → false (khong neo morph toi geometry cu).
 */
internal fun statusBarMorphCaptureMatchesDestination(
    hasValidCapture: Boolean,
    captureOwnerId: String?,
    destinationOwnerId: String?,
): Boolean {
    if (!hasValidCapture) return false
    if (destinationOwnerId.isNullOrEmpty()) return false
    if (captureOwnerId.isNullOrEmpty()) return false
    return captureOwnerId == destinationOwnerId
}

/**
 * First-frame handoff: chi an pill that khi co rect that + shell XY/WH aligned,
 * hoac morph da chay tu origin that (tranh 2-pill). KHONG suppress tren center fallback.
 * Suppress + slot reclaim dong thoi (khong prelude) — Expansion.confirmPillSuppressed.
 */
internal fun statusBarMorphShouldConfirmPillSuppressed(
    hasValidPillRect: Boolean,
    shellAlignedRect: Boolean,
    morphRunning: Boolean,
    morphProgress: Float,
): Boolean {
    if (!hasValidPillRect) return false
    if (shellAlignedRect) return true
    return morphRunning || morphProgress > 0.02f
}
