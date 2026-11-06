package com.android.systemui.axdynamicbar.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusBarMediaTimeoutTest {
    private val timeout = StatusBarMediaTimeout()
    private val tenMinutes = StatusBarMediaTimeout.TIMEOUT_MS

    @Test
    fun pausedMediaExpiresAtTenMinutes() {
        timeout.update(mapOf("a" to false), 100L)
        assertEquals(emptySet<String>(), timeout.expiredIds(100L + tenMinutes - 1L))
        assertEquals(setOf("a"), timeout.expiredIds(100L + tenMinutes))
        assertNull(timeout.nextDelayMs(100L + tenMinutes))
    }

    @Test
    fun repeatedUpdatesDoNotRestartTimeout() {
        timeout.update(mapOf("a" to false), 0L)
        timeout.update(mapOf("a" to false), tenMinutes - 1L)
        assertEquals(1L, timeout.nextDelayMs(tenMinutes - 1L))
        assertEquals(setOf("a"), timeout.expiredIds(tenMinutes))
    }

    @Test
    fun playingMediaNeverExpires() {
        timeout.update(mapOf("a" to true), 0L)
        assertEquals(emptySet<String>(), timeout.expiredIds(tenMinutes * 2))
        assertNull(timeout.nextDelayMs(tenMinutes * 2))
    }

    @Test
    fun resumeRestoresExpiredMediaAndNextPauseStartsNewTimeout() {
        timeout.update(mapOf("a" to false), 0L)
        assertEquals(setOf("a"), timeout.expiredIds(tenMinutes))
        timeout.update(mapOf("a" to true), tenMinutes)
        assertEquals(emptySet<String>(), timeout.expiredIds(tenMinutes))
        timeout.update(mapOf("a" to false), tenMinutes + 100L)
        assertEquals(emptySet<String>(), timeout.expiredIds(tenMinutes * 2))
        assertEquals(setOf("a"), timeout.expiredIds(tenMinutes * 2 + 100L))
    }

    @Test
    fun sessionsExpireIndependently() {
        timeout.update(mapOf("a" to false, "b" to true), 0L)
        timeout.update(mapOf("a" to false, "b" to false), 100L)
        assertEquals(setOf("a"), timeout.expiredIds(tenMinutes))
        assertEquals(100L, timeout.nextDelayMs(tenMinutes))
        assertEquals(setOf("a", "b"), timeout.expiredIds(tenMinutes + 100L))
    }

    @Test
    fun removedSessionDoesNotCarryTimeoutIntoNewSession() {
        timeout.update(mapOf("a" to false), 0L)
        timeout.update(emptyMap(), 100L)
        timeout.update(mapOf("a" to false), tenMinutes)
        assertEquals(emptySet<String>(), timeout.expiredIds(tenMinutes))
        assertEquals(tenMinutes, timeout.nextDelayMs(tenMinutes))
    }

    @Test
    fun elapsedTimeIncludesSleep() {
        timeout.update(mapOf("a" to false), 0L)
        timeout.update(mapOf("a" to false), tenMinutes * 3)
        assertEquals(setOf("a"), timeout.expiredIds(tenMinutes * 3))
        assertNull(timeout.nextDelayMs(tenMinutes * 3))
    }
}
