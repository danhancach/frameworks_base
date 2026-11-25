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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardPillStackMotionTest : SysuiTestCase() {

    @Test
    fun dragSlop_usesStrictManhattan16dp() {
        val slopPx = 45f // ~16dp @ 450dpi
        assertThat(keyguardStackExceedsDragSlop(20f, 25f, slopPx)).isFalse()
        assertThat(keyguardStackExceedsDragSlop(25f, -20.5f, slopPx)).isTrue()
        // Keo ngang thuan van vuot slop (khong khoa doc).
        assertThat(keyguardStackExceedsDragSlop(46f, 0f, slopPx)).isTrue()
    }

    @Test
    fun draggedX_matchesSamsungDepthFactorsAndBasePose() {
        assertThat(keyguardStackDraggedX(depth = 0, baseTxPx = 5f, deltaXPx = 100f))
            .isWithin(0.001f).of(15f)
        assertThat(keyguardStackDraggedX(depth = 1, baseTxPx = 2f, deltaXPx = 100f))
            .isWithin(0.001f).of(10f)
        assertThat(keyguardStackDraggedX(depth = 2, baseTxPx = 0f, deltaXPx = 100f))
            .isWithin(0.001f).of(6f)
    }

    @Test
    fun draggedY_matchesSamsungDepth0LinearAndOverLimitCompression() {
        val yLimit = keyguardStackYLimitPx(pillHeightPx = 163f, dyOffsetPx = 157f)
        assertThat(yLimit).isWithin(0.001f).of(-320f)

        // Trong gioi han: 0.9 * dy (ca keo len va keo xuong).
        assertThat(keyguardStackDraggedY(0, baseTyPx = 0f, deltaYPx = -200f, yLimitPx = yLimit))
            .isWithin(0.001f).of(-180f)
        assertThat(keyguardStackDraggedY(0, baseTyPx = 0f, deltaYPx = 100f, yLimitPx = yLimit))
            .isWithin(0.001f).of(90f)

        // Vuot yLimit (-320): (-420 - (-320)) * 0.1 + 0.9 * (-320) = -10 + (-288) = -298.
        assertThat(keyguardStackDraggedY(0, baseTyPx = 0f, deltaYPx = -420f, yLimitPx = yLimit))
            .isWithin(0.001f).of(-298f)

        // Depth 1 & 2 giu baseTy + 0.1 / 0.05 * dy.
        assertThat(keyguardStackDraggedY(1, baseTyPx = 22f, deltaYPx = -200f, yLimitPx = yLimit))
            .isWithin(0.001f).of(2f)
        assertThat(keyguardStackDraggedY(2, baseTyPx = 45f, deltaYPx = -200f, yLimitPx = yLimit))
            .isWithin(0.001f).of(35f)
    }

    @Test
    fun commitReorder_highVelocityCommitsRegardlessOfAngle() {
        // |vy| > 3000 commit ngay ca khi keo cheo (|dy/dx| < 1).
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 2,
                velocityYPxPerSec = -3200f,
                topTranslationYPx = -20f,
                pillHeightPx = 160f,
                dragDeltaXPx = 200f,
                dragDeltaYPx = -50f,
            )
        ).isTrue()

        // Vuot xuong nhanh van commit (Samsung Math.abs(yVelocity) > 3000).
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 3,
                velocityYPxPerSec = 3100f,
                topTranslationYPx = 40f,
                pillHeightPx = 160f,
                dragDeltaXPx = 100f,
                dragDeltaYPx = 30f,
            )
        ).isTrue()
    }

    @Test
    fun commitReorder_slowDragRequiresUpwardDistanceAndSteepAngle() {
        // Du khoang cach va doc (dx=0 -> steep=true).
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 2,
                velocityYPxPerSec = -200f,
                topTranslationYPx = -165f,
                pillHeightPx = 160f,
                dragDeltaXPx = 0f,
                dragDeltaYPx = -185f,
            )
        ).isTrue()

        // Du khoang cach nhung goc ngang qua (|dy/dx| <= 1) -> khong commit.
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 2,
                velocityYPxPerSec = -200f,
                topTranslationYPx = -165f,
                pillHeightPx = 160f,
                dragDeltaXPx = 200f,
                dragDeltaYPx = -185f,
            )
        ).isFalse()

        // Keo xuong cham -> khong commit.
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 2,
                velocityYPxPerSec = 200f,
                topTranslationYPx = 200f,
                pillHeightPx = 160f,
                dragDeltaXPx = 0f,
                dragDeltaYPx = 220f,
            )
        ).isFalse()

        // Chi 1 the -> khong bao gio commit.
        assertThat(
            keyguardStackShouldCommitReorder(
                visibleCardCount = 1,
                velocityYPxPerSec = -5000f,
                topTranslationYPx = -300f,
                pillHeightPx = 160f,
                dragDeltaXPx = 0f,
                dragDeltaYPx = -350f,
            )
        ).isFalse()
    }

    @Test
    fun rotationAndDepthMapping_threeAndTwoCardStacks() {
        assertThat(keyguardStackRotateTopToBack(listOf("a", "b", "c")))
            .containsExactly("b", "c", "a").inOrder()
        assertThat(keyguardStackRemappedDepth(0, 3)).isEqualTo(2)
        assertThat(keyguardStackRemappedDepth(1, 3)).isEqualTo(0)
        assertThat(keyguardStackRemappedDepth(2, 3)).isEqualTo(1)

        assertThat(keyguardStackRemappedDepth(0, 2)).isEqualTo(1)
        assertThat(keyguardStackRemappedDepth(1, 2)).isEqualTo(0)

        assertThat(keyguardStackZIndex(0, 3)).isEqualTo(2f)
        assertThat(keyguardStackZIndex(1, 3)).isEqualTo(1f)
        assertThat(keyguardStackZIndex(2, 3)).isEqualTo(0f)

        assertThat(keyguardStackRestScale(0)).isEqualTo(1.0f)
        assertThat(keyguardStackRestScale(1)).isEqualTo(0.92f)
        assertThat(keyguardStackRestScale(2)).isEqualTo(0.8f)

        assertThat(keyguardStackRestYDp(0)).isEqualTo(0f)
        assertThat(keyguardStackRestYDp(1)).isEqualTo(5f)
        assertThat(keyguardStackRestYDp(2)).isEqualTo(11f)
        assertThat(KeyguardStackUnderRevealDurationMs).isEqualTo(320)
    }

    @Test
    fun springConstants_matchSamsungNowBarCardStackView() {
        assertThat(KeyguardStackPressScale).isEqualTo(0.98f)
        assertThat(KeyguardStackPressSpringStiffness).isEqualTo(150f)
        assertThat(KeyguardStackPressSpringDamping).isEqualTo(0.57f)
        assertThat(KeyguardStackResetSpringStiffness).isEqualTo(200f)
        assertThat(KeyguardStackResetSpringDamping).isEqualTo(0.78f)
        assertThat(KeyguardStackCommitScaleSpringStiffness).isEqualTo(200f)
        assertThat(KeyguardStackCommitScaleSpringDamping).isEqualTo(0.6f)
        assertThat(KeyguardStackCommitXySpringStiffness).isEqualTo(250f)
        assertThat(KeyguardStackCommitXySpringDamping).isEqualTo(1.0f)
    }
}
