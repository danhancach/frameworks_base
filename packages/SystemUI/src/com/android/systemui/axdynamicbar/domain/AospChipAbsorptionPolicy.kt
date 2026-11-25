/*
 * Copyright 2026 crDroid Android Project
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

package com.android.systemui.axdynamicbar.domain

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SysUISingleton
class AospChipAbsorptionPolicy @Inject constructor() {

    private val chronometerChipKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    companion object {
        private val CLOCK_PACKAGES = setOf("com.google.android.deskclock", "com.android.deskclock")
    }

    /**
     * System chip → island AospChip; chronometer/Clock notif → chi absorb de lay key
     * (dedup PromotedOngoing), khong map AospChip.
     */
    fun isAbsorbed(chip: OngoingActivityChipModel.Active): Boolean =
        isSystemChip(chip) || isChronometerNotificationChip(chip) || isClockChip(chip)

    /** True for chips created by NotifChipsViewModel (promoted ongoing notifications). */
    fun isNotificationChip(chip: OngoingActivityChipModel.Active): Boolean =
        chip.notificationKey != null &&
            chip.key == chip.notificationKey &&
            !chip.isScreenShareNotification

    /** Call / share / record / cast — chip duoc phep thanh IslandEvent.AospChip. */
    fun isSystemChip(chip: OngoingActivityChipModel.Active): Boolean {
        val key = chip.key
        return key.startsWith("callChip-") ||
            key == "ShareToApp" ||
            key == "ScreenRecord" ||
            key == "CastToOtherDevice"
    }

    /** Forget keys whose chips no longer exist. Call once per chip-list emission. */
    fun prune(presentKeys: Set<String>) {
        chronometerChipKeys.retainAll(presentKeys)
    }

    /**
     * DeskClock package — bat ke content type (truoc khi Timer content sticky).
     * Khac [isChronometerNotificationChip]: theo package, khong theo Content.Timer.
     */
    private fun isClockChip(chip: OngoingActivityChipModel.Active): Boolean =
        chip.managingPackageName in CLOCK_PACKAGES

    /**
     * Notif chip co Content.Timer (sticky key) — moi app dung MetricStyle chronometer,
     * khong chi Clock. Ghi key de dedup; khong emit AospChip.
     */
    private fun isChronometerNotificationChip(chip: OngoingActivityChipModel.Active): Boolean {
        if (!isNotificationChip(chip)) return false
        if (chip.content is OngoingActivityChipModel.Content.Timer) {
            chronometerChipKeys.add(chip.key)
        }
        return chip.key in chronometerChipKeys
    }
}
