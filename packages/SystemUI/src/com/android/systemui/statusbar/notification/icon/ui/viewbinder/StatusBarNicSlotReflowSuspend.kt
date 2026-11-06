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

package com.android.systemui.statusbar.notification.icon.ui.viewbinder

import androidx.annotation.VisibleForTesting
import com.android.systemui.statusbar.phone.NotificationIconContainer

/**
 * SoT animationsEnabled cho NIC **status bar** khi AxDynamicBar slot reflow.
 *
 * AOSP: binder ghi [NotificationIconContainer.setAnimationsEnabled] theo Flow
 * `animationsEnabled` (shadeTouchable && !keyguard) — co the FALSE.
 * Gate Ax chi AND suspend: effective = desired && !suspended.
 *
 * - [applyDesired]: binder status-bar path moi emission.
 * - [setSuspended]: gate reclaim/restore; restore KHONG hardcode true.
 * - desired == null (chua co emission): suspend → force false; unsuspend → khong dung.
 * Chi status-bar bind path; AOD/shelf khong dung object nay.
 */
object StatusBarNicSlotReflowSuspend {
    private val lock = Any()

    @Volatile private var suspended: Boolean = false

    /** null = chua biet desired AOSP (chua bind / chua emission). */
    @Volatile private var lastDesired: Boolean? = null

    fun isSuspended(): Boolean = suspended

    fun lastDesiredOrNull(): Boolean? = lastDesired

    /**
     * Binder status-bar: ghi desired roi apply effective.
     * Khi suspended, mid-flight desired=true van giu NIC animations OFF.
     */
    fun applyDesired(view: NotificationIconContainer, desired: Boolean) {
        synchronized(lock) {
            lastDesired = desired
            applyLocked(view)
        }
    }

    /**
     * Gate slot reflow: bat/tat suspend roi re-apply tren [view] (co the null = empty NIC).
     * Restore (value=false) dung lastDesired — khong default true.
     */
    fun setSuspended(view: NotificationIconContainer?, value: Boolean) {
        synchronized(lock) {
            suspended = value
            if (view != null) {
                applyLocked(view)
            }
        }
    }

    private fun applyLocked(view: NotificationIconContainer) {
        val effective = statusBarNicEffectiveAnimationsEnabled(lastDesired, suspended)
        if (effective != null) {
            view.setAnimationsEnabled(effective)
        }
    }

    @VisibleForTesting
    fun resetForTest() {
        synchronized(lock) {
            suspended = false
            lastDesired = null
        }
    }
}

/**
 * Tinh gia tri setAnimationsEnabled.
 * null = khong goi setter (unknown desired + khong suspended).
 */
fun statusBarNicEffectiveAnimationsEnabled(
    desired: Boolean?,
    suspended: Boolean,
): Boolean? {
    return when {
        desired != null -> desired && !suspended
        suspended -> false
        else -> null
    }
}
