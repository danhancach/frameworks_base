/*
 * Copyright (C) 2026 Evolution X
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

package com.android.systemui.statusbar.notification.collection.coordinator

import com.android.systemui.axdynamicbar.domain.AxDynamicBarSettings
import com.android.systemui.axdynamicbar.domain.ClockChronometerNotif
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.listbuilder.pluggable.NotifFilter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * An thẻ notif Clock timer/stopwatch khoi shade/QS (ke ca lockscreen)
 * khi Dynamic Bar bat va chronometer (timer/stopwatch) dang kich hoat —
 * pill AxDB thay the. Khong an dong ho goc status bar / QS header.
 */
@CoordinatorScope
class AxDynamicBarChronometerCoordinator
@Inject
constructor(
    private val axDynamicBarSettings: AxDynamicBarSettings,
    @Application private val applicationScope: CoroutineScope,
) : Coordinator {

    @Volatile private var hideTimer = false
    @Volatile private var hideStopwatch = false

    private val filter =
        object : NotifFilter(TAG) {
            override fun shouldFilterOut(entry: NotificationEntry, now: Long): Boolean {
                if (!hideTimer && !hideStopwatch) return false
                return ClockChronometerNotif.shouldHideFromShade(
                    entry.sbn,
                    hideTimer = hideTimer,
                    hideStopwatch = hideStopwatch,
                )
            }
        }

    override fun attach(pipeline: NotifPipeline) {
        refreshHideFlags(
            axDynamicBarSettings.isEnabled.value,
            axDynamicBarSettings.disabledEventTypes.value,
        )
        pipeline.addPreGroupFilter(filter)
        applicationScope.launch {
            combine(
                axDynamicBarSettings.isEnabled,
                axDynamicBarSettings.disabledEventTypes,
            ) { enabled, disabled ->
                enabled to disabled
            }.collect { (enabled, disabled) ->
                val changed = refreshHideFlags(enabled, disabled)
                if (changed) filter.invalidateList("axdb-chronometer-settings")
            }
        }
    }

    /** @return true neu hideTimer/hideStopwatch doi. */
    private fun refreshHideFlags(enabled: Boolean, disabled: Set<String>): Boolean {
        val nextTimer = enabled && "timer" !in disabled
        val nextStopwatch = enabled && "stopwatch" !in disabled
        if (nextTimer == hideTimer && nextStopwatch == hideStopwatch) return false
        hideTimer = nextTimer
        hideStopwatch = nextStopwatch
        return true
    }

    companion object {
        private const val TAG = "AxDynamicBarChronometer"
    }
}
