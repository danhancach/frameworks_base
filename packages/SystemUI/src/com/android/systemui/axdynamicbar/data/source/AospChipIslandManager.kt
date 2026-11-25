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

package com.android.systemui.axdynamicbar.data.source

import com.android.systemui.axdynamicbar.data.NotificationSmallIconLoader
import com.android.systemui.axdynamicbar.domain.AospChipAbsorptionPolicy
import com.android.systemui.axdynamicbar.domain.AxDynamicBarChipsRefiner
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the island absorbed from AOSP chips, plus what other sources must now stay quiet about. */
data class AospChipSnapshot(
    /** Chips to show in the island (system only — call/share/record/cast). */
    val events: List<IslandEvent.AospChip> = emptyList(),
    /**
     * Keys of absorbed chronometer/Clock notification chips — chi de dedup PromotedOngoing;
     * khong wrap thanh AospChip (pill = IslandEvent.Timer/Stopwatch).
     */
    val notificationKeys: Set<String> = emptySet(),
    /** Packages owning absorbed notification chips (hidden ones included). */
    val packages: Set<String> = emptySet(),
)

@SysUISingleton
class AospChipIslandManager @Inject constructor(
    private val refiner: AxDynamicBarChipsRefiner,
    private val policy: AospChipAbsorptionPolicy,
    private val smallIconLoader: NotificationSmallIconLoader,
) {
    val snapshot: Flow<AospChipSnapshot> =
        combine(refiner.chipsFlow, smallIconLoader.invalidationTick) { model, _ ->
            val all = model.active
            policy.prune(all.mapTo(HashSet()) { it.key })

            val absorbed = all.filter { policy.isAbsorbed(it) }
            // Chronometer/Clock notif: ghi key/package, khong emit AospChip.
            val notifChips = absorbed.filter { policy.isNotificationChip(it) }

            AospChipSnapshot(
                events =
                    absorbed
                        .filter { policy.isSystemChip(it) }
                        .map { active -> toAospChipEvent(active) },
                notificationKeys = notifChips.mapNotNullTo(HashSet()) { it.notificationKey },
                packages = notifChips.mapNotNullTo(HashSet()) { it.managingPackageName },
            )
        }
            .distinctUntilChanged { old, new ->
                old.notificationKeys == new.notificationKeys &&
                    old.packages == new.packages &&
                    old.events.size == new.events.size &&
                    old.events.indices.all { i ->
                        old.events[i].withoutDrawables() == new.events[i].withoutDrawables()
                    }
            }

    /** Kept for existing callers. */
    val aospChipEvents: Flow<List<IslandEvent.AospChip>> =
        snapshot.map { it.events }.distinctUntilChanged { old, new ->
            old.size == new.size &&
                old.indices.all { i -> old[i].withoutDrawables() == new[i].withoutDrawables() }
        }

    private fun toAospChipEvent(
        active: OngoingActivityChipModel.Active,
    ): IslandEvent.AospChip {
        val key =
            when (val icon = active.icon) {
                is OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon ->
                    icon.notificationKey
                else -> active.notificationKey
            }
        val smallIcon = key?.let { smallIconLoader.loadByKey(it) }
        val revision = key?.let { smallIconLoader.revisionByKey(it) } ?: 0L
        return IslandEvent.AospChip(
            active = active,
            smallIcon = smallIcon,
            iconRevision = revision,
        )
    }
}
