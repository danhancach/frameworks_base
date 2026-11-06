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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class StatusBarCollapseProbePhase {
    Idle,
    Measuring,
    Restoring,
}

private const val MaxProbeMeasurementDraws = 6

internal data class StatusBarCollapseProbeSnapshot(
    val phase: StatusBarCollapseProbePhase = StatusBarCollapseProbePhase.Idle,
    val requestGeneration: Long = 0,
    val targetOwnerId: String? = null,
    val layoutGeneration: Int = 0,
    val cancelled: Boolean = false,
    val closeAfterRestore: Boolean = true,
)

internal data class StatusBarCollapseProbeResult(
    val frame: StatusBarPillFrame?,
    val ownerId: String?,
    val closeAfterRestore: Boolean,
)

/** Capture final layout, restore the rendered layout, then release both animations. */
internal class StatusBarCollapseProbeController {
    private var nextGeneration = 0L
    private val state = MutableStateFlow(StatusBarCollapseProbeSnapshot())
    val snapshot = state.asStateFlow()
    val phase get() = state.value.phase
    val requestGeneration get() = state.value.requestGeneration
    private var measuredPlacement = false
    private var restoredPlacement = false
    private var skippedDraws = 0
    private var frame: StatusBarPillFrame? = null
    private var measuredContextToken = 0L

    fun requestProbe(targetOwnerId: String, layoutGeneration: Int): Boolean {
        if (targetOwnerId.isEmpty() || phase != StatusBarCollapseProbePhase.Idle) return false
        measuredPlacement = false
        restoredPlacement = false
        skippedDraws = 0
        frame = null
        state.value = StatusBarCollapseProbeSnapshot(
            phase = StatusBarCollapseProbePhase.Measuring,
            requestGeneration = ++nextGeneration,
            targetOwnerId = targetOwnerId,
            layoutGeneration = layoutGeneration,
        )
        return true
    }

    fun acknowledgePlacement(generation: Long, placedPhase: StatusBarCollapseProbePhase) {
        val current = state.value
        if (current.requestGeneration != generation || current.phase != placedPhase) return
        when (placedPhase) {
            StatusBarCollapseProbePhase.Measuring -> measuredPlacement = true
            StatusBarCollapseProbePhase.Restoring -> restoredPlacement = true
            StatusBarCollapseProbePhase.Idle -> Unit
        }
    }

    fun recordMeasuredLayout(
        generation: Long,
        layoutGeneration: Int,
        ownerId: String,
        frame: StatusBarPillFrame,
        contextToken: Long = 0L,
    ): Boolean {
        val current = state.value
        if (current.phase != StatusBarCollapseProbePhase.Measuring ||
            current.requestGeneration != generation || current.layoutGeneration != layoutGeneration ||
            current.targetOwnerId != ownerId || !measuredPlacement || !frame.isValid
        ) return false
        this.frame = frame
        measuredContextToken = contextToken
        restoredPlacement = false
        state.value = current.copy(phase = StatusBarCollapseProbePhase.Restoring)
        return true
    }

    /** A watchdog abandons measurement only; it never permits drawing probe geometry. */
    fun measurementExpired(): Boolean = ++skippedDraws > MaxProbeMeasurementDraws

    fun cancelForRestore(closeAfterRestore: Boolean) {
        if (phase == StatusBarCollapseProbePhase.Idle) return
        frame = null
        val current = state.value
        if (current.phase != StatusBarCollapseProbePhase.Restoring) restoredPlacement = false
        state.value = current.copy(
            phase = StatusBarCollapseProbePhase.Restoring,
            cancelled = true,
            closeAfterRestore = closeAfterRestore,
        )
    }

    fun finishRestored(
        generation: Long,
        layoutGeneration: Int,
        liveSelectedOwnerId: String?,
        contextToken: Long = 0L,
    ): StatusBarCollapseProbeResult? {
        val current = state.value
        if (current.phase != StatusBarCollapseProbePhase.Restoring ||
            current.requestGeneration != generation || !restoredPlacement
        ) return null
        val valid = !current.cancelled && measuredContextToken == contextToken &&
            current.layoutGeneration == layoutGeneration &&
            current.targetOwnerId == liveSelectedOwnerId && frame?.isValid == true
        val result = StatusBarCollapseProbeResult(
            frame = if (valid) frame else null,
            ownerId = if (valid) current.targetOwnerId else null,
            closeAfterRestore = current.closeAfterRestore,
        )
        abort()
        return result
    }

    /** Only for detached/disposed hosts where no temporary layout can be painted. */
    fun abort() {
        frame = null
        measuredPlacement = false
        restoredPlacement = false
        skippedDraws = 0
        measuredContextToken = 0L
        state.value = StatusBarCollapseProbeSnapshot()
    }
}
