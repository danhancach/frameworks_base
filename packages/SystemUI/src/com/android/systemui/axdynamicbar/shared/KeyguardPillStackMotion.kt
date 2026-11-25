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

import kotlin.math.abs

// Hang so vat ly 1:1 tu Samsung AODService NowBarCardStackView + aod.S9 + presentation.a.

/** So the toi da trong stack lockscreen tuong tac (NowBarCardStackView.getStackMaxSize). */
internal const val KeyguardStackMaxCards = 3

/** Do sau toi da trong stack (0..2). */
internal const val KeyguardStackMaxDepth = KeyguardStackMaxCards - 1

/** Scale nghi theo depth (NowBarCardStackView.s: 1.0, 0.92, 0.8). */
internal val KeyguardStackRestScales = floatArrayOf(1.0f, 0.92f, 0.8f)

/**
 * Offset Y nghi (dp) theo depth voi pivot tam (0.5, 0.5).
 * Giam nua do lo thuc cho pill 58dp ngan hon Samsung: 0 / ~2.68dp / ~5.20dp.
 */
internal val KeyguardStackRestYDp = floatArrayOf(0f, 5f, 11f)

/** Thoi luong under tho ra don dieu khi the mo rong bat dau dong (ms, khong spring nay). */
internal const val KeyguardStackUnderRevealDurationMs = 320

/** Khoang dy_offset vuot gioi han keo len (now_bar_card_stack_view_dy_offset). */
internal const val KeyguardStackDyOffsetDp = 56f

/** Nguong Manhattan bat dau keo (NowBarCardStackView.onInterceptTouchEvent: 16dp). */
internal const val KeyguardStackDragSlopDp = 16f

/** He so theo truc X luc keo theo depth 0..2 (NowBarCardStackView L1104-1118). */
internal val KeyguardStackFollowX = floatArrayOf(0.1f, 0.08f, 0.06f)

/** He so theo truc Y luc keo theo depth 0..2 (NowBarCardStackView L1131-1148). */
internal val KeyguardStackFollowY = floatArrayOf(0.9f, 0.1f, 0.05f)

/** He so nen mem cho the top khi keo vuot yLimit (NowBarCardStackView L1143). */
internal const val KeyguardStackTopOverLimitFactor = 0.1f

/** Nguong van toc Y commit xoay stack (px/s, NowBarCardStackView L1212). */
internal const val KeyguardStackCommitVelocityYPx = 3000f

/** Press-down scale the top luc ACTION_DOWN (NowBarCardStackView L944, aod.S9.e). */
internal const val KeyguardStackPressScale = 0.98f
internal const val KeyguardStackPressSpringStiffness = 150f
internal const val KeyguardStackPressSpringDamping = 0.57f

/** Spring snap-back / resetItemPosition (NowBarCardStackView L873-876). */
internal const val KeyguardStackResetSpringStiffness = 200f
internal const val KeyguardStackResetSpringDamping = 0.78f

/** Spring commit swapTopItemToBottomByTouch (NowBarCardStackView L1274-1277). */
internal const val KeyguardStackCommitScaleSpringStiffness = 200f
internal const val KeyguardStackCommitScaleSpringDamping = 0.6f
internal const val KeyguardStackCommitXySpringStiffness = 250f
internal const val KeyguardStackCommitXySpringDamping = 1.0f

/** Thoi luong an noi dung the duoi depth>0 (presentation.a.p: 150ms, S9.b). */
internal const val KeyguardStackContentHideDurationMs = 150

/** Kiem tra nguong Manhattan > slopPx (NowBarCardStackView L970). */
internal fun keyguardStackExceedsDragSlop(
    dxPx: Float,
    dyPx: Float,
    slopPx: Float,
): Boolean = abs(dxPx) + abs(dyPx) > slopPx

/** Gioi han keo len yLimit am (NowBarCardStackView L1129: -(pillH + dyOffset)). */
internal fun keyguardStackYLimitPx(
    pillHeightPx: Float,
    dyOffsetPx: Float,
): Float = -(pillHeightPx + dyOffsetPx)

/** Tinh translationX luc keo cho 1 the theo depth va pose goc baseTxPx. */
internal fun keyguardStackDraggedX(
    depth: Int,
    baseTxPx: Float,
    deltaXPx: Float,
): Float {
    val factor = KeyguardStackFollowX.getOrElse(depth) { 0.1f }
    return baseTxPx + factor * deltaXPx
}

/**
 * Tinh translationY luc keo cho 1 the theo depth va pose goc baseTyPx.
 * Ap dung thong nhat cho ca 1 the, battery don va stack 2..3 the (L1130-1150).
 */
internal fun keyguardStackDraggedY(
    depth: Int,
    baseTyPx: Float,
    deltaYPx: Float,
    yLimitPx: Float,
): Float {
    val offset =
        when (depth) {
            0 ->
                if (deltaYPx < yLimitPx) {
                    (deltaYPx - yLimitPx) * KeyguardStackTopOverLimitFactor +
                        KeyguardStackFollowY[0] * yLimitPx
                } else {
                    KeyguardStackFollowY[0] * deltaYPx
                }
            1 -> KeyguardStackFollowY[1] * deltaYPx
            2 -> KeyguardStackFollowY[2] * deltaYPx
            else -> KeyguardStackFollowY[0] * deltaYPx
        }
    return baseTyPx + offset
}

/**
 * Dieu kien commit xoay stack luc nha tay (NowBarCardStackView L1210-1212):
 * |vy| > 3000 (doc lap goc) HOAC (-topTransY > pillHeight && |dy/dx| > 1).
 */
internal fun keyguardStackShouldCommitReorder(
    visibleCardCount: Int,
    velocityYPxPerSec: Float,
    topTranslationYPx: Float,
    pillHeightPx: Float,
    dragDeltaXPx: Float,
    dragDeltaYPx: Float,
): Boolean {
    if (visibleCardCount <= 1) return false
    if (abs(velocityYPxPerSec) > KeyguardStackCommitVelocityYPx) return true
    val steepEnough =
        if (dragDeltaXPx == 0f) {
            dragDeltaYPx != 0f
        } else {
            abs(dragDeltaYPx / dragDeltaXPx) > 1f
        }
    return (-topTranslationYPx) > pillHeightPx && steepEnough
}

/** Chuyen doi depth khi dua the top xuong day stack (0 -> last, d -> d-1). */
internal fun keyguardStackRemappedDepth(
    oldDepth: Int,
    visibleCount: Int,
): Int {
    if (visibleCount <= 1) return 0
    return if (oldDepth == 0) visibleCount - 1 else (oldDepth - 1).coerceAtLeast(0)
}

/** Xoay danh sach id hien thi: dua phan tu dau xuong cuoi. */
internal fun keyguardStackRotateTopToBack(visibleIds: List<String>): List<String> {
    if (visibleIds.size <= 1) return visibleIds
    return visibleIds.drop(1) + visibleIds.first()
}

/** Z-index theo depth: depth 0 cao nhat (NowBarCardStackView.f). */
internal fun keyguardStackZIndex(
    depth: Int,
    visibleCount: Int,
): Float = (visibleCount - 1 - depth).coerceAtLeast(0).toFloat()

/** Scale nghi theo depth (fallback 1.0f). */
internal fun keyguardStackRestScale(depth: Int): Float =
    KeyguardStackRestScales.getOrElse(depth) { 1.0f }

/** Offset Y nghi (dp) theo depth (fallback 0f). */
internal fun keyguardStackRestYDp(depth: Int): Float =
    KeyguardStackRestYDp.getOrElse(depth) { 0f }
