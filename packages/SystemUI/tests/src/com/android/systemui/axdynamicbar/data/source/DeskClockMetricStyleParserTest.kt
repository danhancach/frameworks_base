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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskClockMetricStyleParserTest {

    private fun metricsExtras(vararg metricBundles: Bundle): Bundle =
        Bundle().apply {
            putParcelableArrayList(EXTRA_ANDROID_METRICS, ArrayList(metricBundles.asList()))
        }

    private fun metricBundle(label: CharSequence? = null, value: Bundle? = null): Bundle =
        Bundle().apply {
            if (label != null) putCharSequence("label", label)
            if (value != null) putBundle("value", value)
        }

    @Test
    fun missingExtrasOrNonTimeDifferenceMetric_returnsEmpty() {
        assertTrue(parseChronometerMetricsFromExtras(Bundle(), 1_700_000_000_000L, 10_000L).isEmpty())

        val nonChrono =
            Bundle().apply {
                putInt("_type", 2)
                putBoolean("countDown", true)
                putLong("pausedDuration", 5_000L)
                putLong("zeroTime", 1_700_000_000_000L)
                putLong("zeroElapsedRealtime", 50_000L)
            }
        val extras = metricsExtras(metricBundle(label = "Steps", value = nonChrono))
        assertTrue(parseChronometerMetricsFromExtras(extras, 1_700_000_000_000L, 10_000L).isEmpty())
    }

    @Test
    fun pausedTimer_freezesRemainingAndSkipsIgnoredZeroTimeAndElapsedKeys() {
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L
        val value =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
                putLong("pausedDuration", 45_000L)
                // Gia tri loi kieu o cac khoa bi nhanh paused bo qua: parser khong duoc doc som.
                putString("zeroTime", "malformed_zero_time")
                putString("zeroElapsedRealtime", "malformed_zero_er")
            }
        val parsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Tea", value = value)),
                nowWall = nowWall,
                nowElapsed = nowElapsed,
            )
        assertEquals(1, parsed.size)
        val metric = parsed[0]
        assertEquals("Tea", metric.label)
        assertTrue(metric.countDown)
        assertTrue(metric.isPaused)
        assertEquals(45_000L, metric.frozenDurationMs)
        assertEquals(nowWall + 45_000L, metric.wallAnchorMs)
        assertEquals(0L, metric.elapsedAnchorMs)
        assertEquals(45_000L, metricRemainingMs(metric, nowWall + 10_000L, nowElapsed + 10_000L))
    }

    @Test
    fun pausedTimer_zeroDurationFallsThroughToRunningAnchor() {
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L
        val value =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
                putLong("pausedDuration", 0L)
                putLong("zeroElapsedRealtime", nowElapsed + 30_000L)
            }
        val parsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Timer", value = value)),
                nowWall = nowWall,
                nowElapsed = nowElapsed,
            )
        assertEquals(1, parsed.size)
        val metric = parsed[0]
        assertFalse(metric.isPaused)
        assertEquals(nowElapsed + 30_000L, metric.elapsedAnchorMs)
        assertEquals(nowWall + 30_000L, metric.wallAnchorMs)
        assertEquals(20_000L, metricRemainingMs(metric, nowWall + 10_000L, nowElapsed + 10_000L))
    }

    @Test
    fun pausedStopwatch_acceptsZeroAndPositiveFrozenElapsedWithoutReadingIgnoredKeys() {
        val nowWall = 1_700_000_000_000L
        val zeroValue =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", false)
                putLong("pausedDuration", 0L)
                putString("zeroTime", "malformed_zero_time")
                putString("zeroElapsedRealtime", "malformed_zero_er")
            }
        val zeroParsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Stopwatch", value = zeroValue)),
                nowWall = nowWall,
                nowElapsed = 5_000L,
            )
        assertEquals(1, zeroParsed.size)
        val zeroPaused = zeroParsed[0]
        assertFalse(zeroPaused.countDown)
        assertTrue(zeroPaused.isPaused)
        assertEquals(0L, zeroPaused.frozenDurationMs)
        assertEquals(nowWall, zeroPaused.wallAnchorMs)
        assertEquals(0L, zeroPaused.elapsedAnchorMs)

        val positiveValue =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", false)
                putLong("pausedDuration", 12_345L)
            }
        val positiveParsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Stopwatch", value = positiveValue)),
                nowWall = nowWall,
                nowElapsed = 5_000L,
            )
        assertEquals(1, positiveParsed.size)
        val runningPaused = positiveParsed[0]
        assertTrue(runningPaused.isPaused)
        assertEquals(12_345L, runningPaused.frozenDurationMs)
        assertEquals(nowWall - 12_345L, runningPaused.wallAnchorMs)
    }

    @Test
    fun runningStopwatch_preservesNegativeZeroElapsedRealtimeAfterReboot() {
        val nowWall = 1_700_000_100_000L
        val nowElapsed = 2_000L
        val zeroEr = -8_000L // elapsed = 10_000ms > uptime sau reboot
        val value =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", false)
                putLong("zeroElapsedRealtime", zeroEr)
            }
        val parsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Stopwatch", value = value)),
                nowWall = nowWall,
                nowElapsed = nowElapsed,
            )
        assertEquals(1, parsed.size)
        val metric = parsed[0]
        assertFalse(metric.countDown)
        assertFalse(metric.isPaused)
        assertEquals(zeroEr, metric.elapsedAnchorMs)
        assertEquals(nowWall - 10_000L, metric.wallAnchorMs)
    }

    @Test
    fun runningTimer_zeroElapsedRealtimeOverridesZeroTimeWhenRemainingPositive() {
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L
        val value =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
                putLong("zeroTime", nowWall + 120_000L)
                putLong("zeroElapsedRealtime", nowElapsed + 60_000L)
            }
        val parsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Pasta", value = value)),
                nowWall = nowWall,
                nowElapsed = nowElapsed,
            )
        assertEquals(1, parsed.size)
        val metric = parsed[0]
        assertEquals(nowElapsed + 60_000L, metric.elapsedAnchorMs)
        assertEquals(nowWall + 60_000L, metric.wallAnchorMs)
    }

    @Test
    fun runningStopwatch_keepsZeroTimeWhenPresentAlongsideZeroElapsedRealtime() {
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L
        val value =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", false)
                putLong("zeroTime", nowWall - 90_000L)
                putLong("zeroElapsedRealtime", nowElapsed - 60_000L)
            }
        val parsed =
            parseChronometerMetricsFromExtras(
                extras = metricsExtras(metricBundle(label = "Stopwatch", value = value)),
                nowWall = nowWall,
                nowElapsed = nowElapsed,
            )
        assertEquals(1, parsed.size)
        val metric = parsed[0]
        assertEquals(nowElapsed - 60_000L, metric.elapsedAnchorMs)
        assertEquals(nowWall - 90_000L, metric.wallAnchorMs)
    }

    @Test
    fun multiMetricBundle_preservesOrderAndSkipsInvalidEntries() {
        val nowWall = 1_700_000_000_000L
        val nowElapsed = 50_000L
        val timerA =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
                putLong("zeroElapsedRealtime", nowElapsed + 15_000L)
            }
        val emptyChrono =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
            }
        val timerB =
            Bundle().apply {
                putInt("_type", 1)
                putBoolean("countDown", true)
                putLong("pausedDuration", 20_000L)
            }
        val extras =
            metricsExtras(
                metricBundle(label = "A", value = timerA),
                metricBundle(label = "MissingValue", value = null),
                metricBundle(label = "Empty", value = emptyChrono),
                metricBundle(label = "B", value = timerB),
            )
        val parsed = parseChronometerMetricsFromExtras(extras, nowWall, nowElapsed)
        assertEquals(listOf("A", "B"), parsed.map { it.label })
        assertFalse(parsed[0].isPaused)
        assertTrue(parsed[1].isPaused)
    }
}
