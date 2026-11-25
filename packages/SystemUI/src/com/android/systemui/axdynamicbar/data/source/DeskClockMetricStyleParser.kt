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

package com.android.systemui.axdynamicbar.data.source

import android.os.Bundle

internal const val EXTRA_ANDROID_METRICS = "android.metrics"
private const val METRIC_TYPE_TIME_DIFFERENCE = 1

/**
 * Chronometer trong android.metrics (TYPE_TIME_DIFFERENCE).
 * countDown=true -> wallAnchorMs = end (timer); false -> wallAnchorMs = start (stopwatch).
 */
internal data class ParsedChronometerMetric(
    val label: String,
    val countDown: Boolean,
    val wallAnchorMs: Long,
    val isPaused: Boolean,
    /** pausedDuration goc (ms): timer = remaining; stopwatch = elapsed - dong bang UI. */
    val frozenDurationMs: Long = 0L,
    /** zeroElapsedRealtime goc (timer = end ER; stopwatch = start ER). 0 = khong co. */
    val elapsedAnchorMs: Long = 0L,
)

/**
 * Google Clock MetricStyle: android.metrics TYPE_TIME_DIFFERENCE (chronometer).
 * countDown=true (timer): wallAnchor = end. countDown=false (stopwatch): wallAnchor = start.
 * Thu tu: pausedDuration -> zeroTime -> zeroElapsedRealtime.
 */
internal fun parseChronometerMetricsFromExtras(
    extras: Bundle,
    nowWall: Long,
    nowElapsed: Long,
): List<ParsedChronometerMetric> {
    val bundles =
        try {
            extras.getParcelableArrayList(EXTRA_ANDROID_METRICS, Bundle::class.java)
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            extras.getParcelableArrayList<Bundle>(EXTRA_ANDROID_METRICS)
        } ?: return emptyList()

    val result = mutableListOf<ParsedChronometerMetric>()
    for (metricBundle in bundles) {
        val value = metricBundle?.getBundle("value") ?: continue
        if (value.getInt("_type", -1) != METRIC_TYPE_TIME_DIFFERENCE) continue

        val countDown = value.getBoolean("countDown", false)
        val metricLabel = metricBundle.getCharSequence("label")?.toString().orEmpty()
        var wallAnchorMs = 0L
        var isPaused = false
        var frozenDurationMs = 0L
        var elapsedAnchorMs = 0L

        if (value.containsKey("pausedDuration")) {
            val duration = value.getLong("pausedDuration")
            if (countDown) {
                // Timer paused: remaining dong bang (Chronometer.Paused)
                if (duration > 0L) {
                    wallAnchorMs = nowWall + duration
                    isPaused = true
                    frozenDurationMs = duration
                }
            } else {
                // Stopwatch paused: elapsed dong bang
                if (duration >= 0L) {
                    wallAnchorMs = nowWall - duration
                    isPaused = true
                    frozenDurationMs = duration
                }
            }
        }
        if (wallAnchorMs <= 0L && value.containsKey("zeroTime")) {
            val zero = value.getLong("zeroTime")
            if (zero > 0L) wallAnchorMs = zero
        }
        if (!isPaused && value.containsKey("zeroElapsedRealtime")) {
            val zeroER = value.getLong("zeroElapsedRealtime")
            elapsedAnchorMs = zeroER
            if (countDown) {
                // Timer: zeroER = thoi diem het gio (elapsedRealtime)
                val remaining = zeroER - nowElapsed
                if (remaining > 0L || wallAnchorMs <= 0L) {
                    wallAnchorMs = nowWall + remaining.coerceAtLeast(0L)
                }
            } else {
                // Stopwatch: zeroER = moc bat dau (co the am sau reboot)
                val elapsed = (nowElapsed - zeroER).coerceAtLeast(0L)
                if (wallAnchorMs <= 0L) wallAnchorMs = nowWall - elapsed
            }
        }
        if (wallAnchorMs > 0L || frozenDurationMs > 0L || elapsedAnchorMs != 0L) {
            result.add(
                ParsedChronometerMetric(
                    metricLabel,
                    countDown,
                    wallAnchorMs,
                    isPaused,
                    frozenDurationMs,
                    elapsedAnchorMs,
                ),
            )
        }
    }
    return result
}

/** Remaining dong nhat (pausedDuration hoac end-now) - dung de khop id khi reorder. */
internal fun metricRemainingMs(
    metric: ParsedChronometerMetric,
    nowWall: Long,
    nowElapsed: Long,
): Long {
    if (metric.isPaused) return metric.frozenDurationMs.coerceAtLeast(0L)
    if (metric.elapsedAnchorMs > 0L) {
        return (metric.elapsedAnchorMs - nowElapsed).coerceAtLeast(0L)
    }
    if (metric.wallAnchorMs > 0L) {
        return (metric.wallAnchorMs - nowWall).coerceAtLeast(0L)
    }
    return 0L
}
