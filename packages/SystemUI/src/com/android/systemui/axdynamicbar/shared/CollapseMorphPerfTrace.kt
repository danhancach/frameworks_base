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

import android.os.SystemClock
import android.os.Trace
import android.util.Log

/**
 * Trace ASYNC collapse morph — bat tu **closing that** (khong tu tap).
 *
 * ## Hai span (do vs lifecycle)
 *
 * - [AxDbCollapseMorphGeometryClosingTrace] — **do drop dang thu nho**: closing actual →
 *   transition settle (`current/target` false, `!running`). Khong end chi vi progress≈0.01
 *   khi transition van chay. Handoff/hold **sau** settle khong nam trong span nay.
 * - [AxDbCollapseMorphClosingTrace] — lifecycle: closing → `!pillSuppressed` / hide /
 *   cancel khop gen / [forceCancel]. Co the gom hold/handoff sau geometry settle.
 *
 * ## Marker / moc
 *
 * - [AxDbCollapseMorphEndpointTrace] — instant khi geometry settle
 * - [AxDbCollapseMorphHandoffTrace] — instant khi handoff Ready / hold bat dau
 * - `AxDbCollapse/cancel:<reason>` / `AxDbCollapse/retarget` — sync Trace **luon** ghi
 *   (Perfetto thay duoc khi Log.DEBUG tat); khong chi [Log.d]
 *
 * ## Gating (trung thuc)
 *
 * - Async Trace begin/end **luon** ghi (A/B khong can rebuild / bat log).
 * - [Log.d] chi khi [axDbCollapsePerfEnabled] (`log.tag.AxDbCollapse` ≥ DEBUG).
 * - Khong log moi frame bottom/progress.
 *
 * Perfetto: loc slice `AxDbCollapse/`.
 */

internal const val AxDbCollapsePerfTag = "AxDbCollapse"
/** Lifecycle span (co the keo qua hold/handoff). */
internal const val AxDbCollapseMorphClosingTrace = "AxDbCollapse/morphClosing"
/** Geometry-only: dang thu nho → transition settle. */
internal const val AxDbCollapseMorphGeometryClosingTrace = "AxDbCollapse/morphGeometryClosing"
internal const val AxDbCollapseMorphEndpointTrace = "AxDbCollapse/morphEndpoint"
internal const val AxDbCollapseMorphHandoffTrace = "AxDbCollapse/morphHandoff"

internal fun axDbCollapsePerfEnabled(): Boolean =
    Log.isLoggable(AxDbCollapsePerfTag, Log.DEBUG)

/**
 * Session/generation-safe async Trace cho 1 lan collapse morph.
 *
 * - [onCancel]: chi dong khi [layoutGeneration] == active; stale → no-op toan bo.
 * - Lifecycle can dong vo dieu kien (hide/reopen) → [forceCancel].
 * - Retarget gen moi: dong span cu + marker `retarget`, mo span moi.
 */
internal class CollapseMorphPerfTrace {
    private var activeGeneration: Int = -1
    private var activeCookie: Int = 0
    private var lifecycleStarted = false
    private var geometryStarted = false
    private var endpointMarked = false
    private var handoffMarked = false

    val isActive: Boolean
        get() = lifecycleStarted && activeGeneration >= 0

    val isGeometryActive: Boolean
        get() = geometryStarted && activeGeneration >= 0

    val activeGenerationOrNeg1: Int
        get() = activeGeneration

    /**
     * Bat dau closing that (morphClosing lan dau / pillSuppressed collapse).
     * [layoutGeneration] = cookie; retarget cung gen → no-op; gen moi → cancel cu + marker.
     * Mo ca lifecycle + geometry span.
     */
    fun onClosingStarted(layoutGeneration: Int) {
        if (lifecycleStarted && activeGeneration == layoutGeneration) return
        if (lifecycleStarted) {
            emitSyncMarker("AxDbCollapse/retarget")
            endLifecycleAndGeometry("retarget", activeGeneration)
        }
        lifecycleStarted = true
        geometryStarted = true
        endpointMarked = false
        handoffMarked = false
        activeGeneration = layoutGeneration
        activeCookie = layoutGeneration and 0x7fffffff
        Trace.beginAsyncSection(AxDbCollapseMorphClosingTrace, activeCookie)
        Trace.beginAsyncSection(AxDbCollapseMorphGeometryClosingTrace, activeCookie)
        if (axDbCollapsePerfEnabled()) {
            Log.d(
                AxDbCollapsePerfTag,
                "CLOSING_START up=${SystemClock.uptimeMillis()} gen=$layoutGeneration",
            )
        }
    }

    /**
     * Transition settle (current/target false, !running) — end **geometry** span + moc
     * endpoint. Lifecycle van mo qua hold/handoff. Khong goi khi chi progress≈0.01 ma
     * transition van running.
     */
    fun onGeometrySettled(layoutGeneration: Int) {
        if (!lifecycleStarted || activeGeneration != layoutGeneration) return
        endGeometryOnly("settle", layoutGeneration)
        onEndpointReached(layoutGeneration)
    }

    /** Geom morph ve endpoint — moc rieng (goi tu [onGeometrySettled] hoac tay). */
    fun onEndpointReached(layoutGeneration: Int) {
        if (!lifecycleStarted || activeGeneration != layoutGeneration) return
        if (endpointMarked) return
        endpointMarked = true
        Trace.beginAsyncSection(AxDbCollapseMorphEndpointTrace, activeCookie)
        Trace.endAsyncSection(AxDbCollapseMorphEndpointTrace, activeCookie)
        if (axDbCollapsePerfEnabled()) {
            Log.d(
                AxDbCollapsePerfTag,
                "ENDPOINT up=${SystemClock.uptimeMillis()} gen=$layoutGeneration",
            )
        }
    }

    /** Handoff Ready / hold bat dau — moc rieng vs endpoint (ngoai geometry span). */
    fun onHandoffReady(layoutGeneration: Int) {
        if (!lifecycleStarted || activeGeneration != layoutGeneration) return
        if (handoffMarked) return
        handoffMarked = true
        Trace.beginAsyncSection(AxDbCollapseMorphHandoffTrace, activeCookie)
        Trace.endAsyncSection(AxDbCollapseMorphHandoffTrace, activeCookie)
        if (axDbCollapsePerfEnabled()) {
            Log.d(
                AxDbCollapsePerfTag,
                "HANDOFF up=${SystemClock.uptimeMillis()} gen=$layoutGeneration",
            )
        }
    }

    /** Ket thuc lifecycle binh thuong sau handoff / overlay hide path (!pillSuppressed). */
    fun onClosingEnded(layoutGeneration: Int) {
        if (!lifecycleStarted) return
        if (activeGeneration != layoutGeneration) return
        endLifecycleAndGeometry("end", layoutGeneration)
    }

    /**
     * Cancel theo gen: **chi** dong khi [layoutGeneration] khop active.
     * Stale gen → no-op (khong end span moi). Marker Trace luon ghi khi thuc su cancel.
     */
    fun onCancel(layoutGeneration: Int, reason: String) {
        if (!lifecycleStarted) return
        if (activeGeneration != layoutGeneration) return
        val bounded = boundReason(reason)
        emitSyncMarker("AxDbCollapse/cancel:$bounded")
        endLifecycleAndGeometry("cancel:$bounded", layoutGeneration)
    }

    /**
     * Detach / hide / reopen isExpanded — force close vo dieu kien session dang active.
     * Dung khi lifecycle can dong ma gen co the khong khop (tranh orphan span).
     */
    fun forceCancel(reason: String) {
        if (!lifecycleStarted) return
        val bounded = boundReason(reason)
        emitSyncMarker("AxDbCollapse/cancel:$bounded")
        endLifecycleAndGeometry("force:$bounded", activeGeneration)
    }

    private fun endGeometryOnly(reason: String, generation: Int) {
        if (!geometryStarted) return
        Trace.endAsyncSection(AxDbCollapseMorphGeometryClosingTrace, activeCookie)
        geometryStarted = false
        if (axDbCollapsePerfEnabled()) {
            Log.d(
                AxDbCollapsePerfTag,
                "GEOMETRY_END up=${SystemClock.uptimeMillis()} gen=$generation reason=$reason",
            )
        }
    }

    private fun endLifecycleAndGeometry(reason: String, generation: Int) {
        if (!lifecycleStarted) return
        if (geometryStarted) {
            Trace.endAsyncSection(AxDbCollapseMorphGeometryClosingTrace, activeCookie)
            geometryStarted = false
        }
        Trace.endAsyncSection(AxDbCollapseMorphClosingTrace, activeCookie)
        if (axDbCollapsePerfEnabled()) {
            Log.d(
                AxDbCollapsePerfTag,
                "CLOSING_END up=${SystemClock.uptimeMillis()} gen=$generation reason=$reason",
            )
        }
        lifecycleStarted = false
        activeGeneration = -1
        activeCookie = 0
        endpointMarked = false
        handoffMarked = false
    }

    /** Sync marker Perfetto — khong phu thuoc Log.DEBUG. */
    private fun emitSyncMarker(sectionName: String) {
        val name = sectionName.take(Trace.MAX_SECTION_NAME_LEN)
        Trace.beginSection(name)
        Trace.endSection()
    }

    private fun boundReason(reason: String): String {
        // Gioi han de section name + prefix khong qua Trace limit.
        return reason.take(48).replace('\n', '_').replace(' ', '_')
    }
}
