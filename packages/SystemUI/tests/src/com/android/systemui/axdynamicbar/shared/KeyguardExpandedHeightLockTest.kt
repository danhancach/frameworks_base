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
class KeyguardExpandedHeightLockTest : SysuiTestCase() {

    @Test
    fun ownerKey_batteryBeatsEvent() {
        assertThat(keyguardExpandedContentOwnerKey(true, "timer_x")).isEqualTo("battery")
        assertThat(keyguardExpandedContentOwnerKey(false, "timer_x"))
            .isEqualTo("event:timer_x")
        assertThat(keyguardExpandedContentOwnerKey(false, null)).isNull()
    }

    @Test
    fun resolve_ownerSwitch_retargetsMeasuredHeight() {
        val clock = LockedTargetHeight(heightPx = 500, owner = "event:timer_1")
        val next =
            resolveLockedTargetHeight(
                current = clock,
                owner = "battery",
                measuredHPx = 820,
            )
        assertThat(next.heightPx).isEqualTo(820)
        assertThat(next.owner).isEqualTo("battery")
    }

    @Test
    fun resolve_sameOwner_keepsLocked_notMax() {
        val locked = LockedTargetHeight(heightPx = 500, owner = "event:timer_1")
        // Tick / RV remeasure taller — khong max(old,new) vo dieu kien.
        val taller =
            resolveLockedTargetHeight(locked, owner = "event:timer_1", measuredHPx = 560)
        assertThat(taller).isEqualTo(locked)
        val shorter =
            resolveLockedTargetHeight(locked, owner = "event:timer_1", measuredHPx = 420)
        assertThat(shorter).isEqualTo(locked)
    }

    @Test
    fun effectiveLocked_ignoresStaleOwner() {
        val locked = LockedTargetHeight(heightPx = 500, owner = "event:timer_1")
        assertThat(effectiveLockedTargetHeightPx(locked, "event:timer_1")).isEqualTo(500)
        assertThat(effectiveLockedTargetHeightPx(locked, "battery")).isEqualTo(0)
        assertThat(effectiveLockedTargetHeightPx(locked, null)).isEqualTo(0)
    }

    @Test
    fun resolve_tallerThenShorter_ownerRoundTrip() {
        var cur = LockedTargetHeight()
        cur = resolveLockedTargetHeight(cur, "event:sw", 600)
        assertThat(cur.heightPx).isEqualTo(600)
        cur = resolveLockedTargetHeight(cur, "battery", 820)
        assertThat(cur.heightPx).isEqualTo(820)
        cur = resolveLockedTargetHeight(cur, "event:media", 480)
        assertThat(cur.heightPx).isEqualTo(480)
        // Lap lai Battery sau Event — retarget lai, khong giu H media.
        cur = resolveLockedTargetHeight(cur, "battery", 810)
        assertThat(cur.heightPx).isEqualTo(810)
        assertThat(cur.owner).isEqualTo("battery")
    }

    // --- STATUS BAR retarget (task-008): bridge envelope, follow natural ---

    @Test
    fun statusBar_ownerSwitch_bridgesLastExpanded_notPill() {
        val locked = LockedTargetHeight(heightPx = 500, owner = "event:media")
        // Owner doi — matched=0 nhung bridge lastExpanded, khong 0/pill.
        assertThat(statusBarMorphTargetHeightPx(locked, "event:timer_1", lastExpandedHPx = 500))
            .isEqualTo(500)
        assertThat(statusBarMorphTargetHeightPx(locked, "event:timer_1", lastExpandedHPx = 0))
            .isEqualTo(0)
    }

    /** task-031: cache natural owner moi thang bridge lastExpanded (top nho != under cao). */
    @Test
    fun statusBar_ownerSwitch_prefersCachedOwnerNatural_overLastExpanded() {
        val locked = LockedTargetHeight(heightPx = 300, owner = "event:under_tall")
        assertThat(
                statusBarMorphTargetHeightPx(
                    locked,
                    owner = "event:top_short",
                    lastExpandedHPx = 300,
                    cachedOwnerNaturalHPx = 100,
                )
            )
            .isEqualTo(100)
        // Khong cache → van bridge (tranh pill bounce mid-switch).
        assertThat(
                statusBarMorphTargetHeightPx(
                    locked,
                    owner = "event:top_short",
                    lastExpandedHPx = 300,
                    cachedOwnerNaturalHPx = 0,
                )
            )
            .isEqualTo(300)
    }

    @Test
    fun statusBar_sameOwner_followsNaturalHeight() {
        var cur = LockedTargetHeight(heightPx = 500, owner = "event:timer_1")
        cur = resolveStatusBarLockedTargetHeight(cur, "event:timer_1", measuredHPx = 560, pillHPx = 60)
        assertThat(cur.heightPx).isEqualTo(560)
        cur = resolveStatusBarLockedTargetHeight(cur, "event:timer_1", measuredHPx = 420, pillHPx = 60)
        assertThat(cur.heightPx).isEqualTo(420)
    }

    @Test
    fun statusBar_resolve_ignoresPillAndZero_andNoopEqual() {
        val locked = LockedTargetHeight(heightPx = 500, owner = "event:media")
        assertThat(
            resolveStatusBarLockedTargetHeight(locked, "event:media", measuredHPx = 0, pillHPx = 60)
        ).isEqualTo(locked)
        assertThat(
            resolveStatusBarLockedTargetHeight(locked, "event:media", measuredHPx = 60, pillHPx = 60)
        ).isEqualTo(locked)
        assertThat(
            resolveStatusBarLockedTargetHeight(locked, "event:media", measuredHPx = 500, pillHPx = 60)
        ).isEqualTo(locked)
    }

    @Test
    fun statusBar_ownerSwitch_retargetsWhenMeasured() {
        var cur = LockedTargetHeight(heightPx = 500, owner = "event:media")
        cur =
            resolveStatusBarLockedTargetHeight(
                cur,
                owner = "event:timer_1",
                measuredHPx = 480,
                pillHPx = 60,
            )
        assertThat(cur.heightPx).isEqualTo(480)
        assertThat(cur.owner).isEqualTo("event:timer_1")
        assertThat(statusBarMorphTargetHeightPx(cur, "event:timer_1", lastExpandedHPx = 500))
            .isEqualTo(480)
    }
}
