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

package com.android.systemui.axdynamicbar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyRevisionTrackerTest {

    @Test
    fun bumpKey_changesRevision_monotonic() {
        val t = KeyRevisionTracker()
        val r0 = t.ensureTracked("k1")
        val r1 = t.bumpKey("k1")
        val r2 = t.bumpKey("k1")
        assertTrue("r1 phai lon hon r0", r1 > r0)
        assertTrue("r2 phai lon hon r1", r2 > r1)
        assertEquals(r2, t.revision("k1"))
    }

    @Test
    fun sameUriContentChange_simulatedByBump_updatesRevision() {
        val t = KeyRevisionTracker()
        val before = t.ensureTracked("clock")
        // Post hoac update authoritative luon bump revision theo sequence
        val after = t.bumpKey("clock")
        assertNotEquals(before, after)
        assertTrue(after > before)
    }

    @Test
    fun removeThenRepost_doesNotReuseRevisions_acrossMultipleCycles() {
        val t = KeyRevisionTracker()
        val seenRevisions = mutableSetOf<Long>()

        for (cycle in 1..5) {
            val trackedRev = t.ensureTracked("cycle_key")
            assertTrue("Revision phai moi chua tung thay", seenRevisions.add(trackedRev))

            val bumpedRev1 = t.bumpKey("cycle_key")
            assertTrue("Revision phai moi chua tung thay", seenRevisions.add(bumpedRev1))

            val bumpedRev2 = t.bumpKey("cycle_key")
            assertTrue("Revision phai moi chua tung thay", seenRevisions.add(bumpedRev2))

            assertTrue("removeKey phai tra ve true", t.removeKey("cycle_key"))
            assertEquals("Revision sau remove phai la 0", 0L, t.revision("cycle_key"))
            assertFalse("Khong con tracked", t.isTracked("cycle_key"))
        }

        // 5 chu ky x 3 revisions = 15 revisions rieng biet
        assertEquals(15, seenRevisions.size)
    }

    @Test
    fun configBump_changesAllTrackedRevisions_monotonic() {
        val t = KeyRevisionTracker()
        val ra0 = t.ensureTracked("a")
        val rb0 = t.ensureTracked("b")

        val cfg = t.bumpConfig()
        val ra1 = t.revision("a")
        val rb1 = t.revision("b")

        assertNotEquals(ra0, ra1)
        assertNotEquals(rb0, rb1)
        assertTrue(ra1 > ra0)
        assertTrue(rb1 > rb0)
        assertTrue(ra1 > cfg || rb1 > cfg)
    }

    @Test
    fun collectionThenPost_ordering_monotonic() {
        val t = KeyRevisionTracker()
        t.ensureTracked("k")
        val afterCollection = t.bumpKey("k")
        val afterPost = t.bumpKey("k")
        assertTrue(afterPost > afterCollection)

        val t2 = KeyRevisionTracker()
        t2.ensureTracked("k2")
        val postFirst = t2.bumpKey("k2")
        val collectionSecond = t2.bumpKey("k2")
        assertTrue(collectionSecond > postFirst)
    }

    @Test
    fun isCurrent_rejectsRemovedAndStaleSameKeyTokens() {
        val tracker = KeyRevisionTracker()

        // Token 0L hoac chua track luon khong hop le
        assertFalse(tracker.isCurrent("k1", 0L))

        val v1 = tracker.bumpKey("k1")
        assertTrue(tracker.isCurrent("k1", v1))

        // Remove nhanh: event v1 den muon phai bi tu choi
        tracker.removeKey("k1")
        assertFalse(tracker.isCurrent("k1", v1))

        // Dang lai cung key: chi v2 hop le, v1 van bi tu choi
        val v2 = tracker.bumpKey("k1")
        assertTrue(v2 > v1)
        assertFalse(tracker.isCurrent("k1", v1))
        assertTrue(tracker.isCurrent("k1", v2))

        // Dung nghe (clear): huy toan bo key nhung giu sequence monotonic
        tracker.clear()
        assertFalse(tracker.isCurrent("k1", v2))
        val v3 = tracker.bumpKey("k1")
        assertTrue(v3 > v2)
        assertFalse(tracker.isCurrent("k1", v2))
        assertTrue(tracker.isCurrent("k1", v3))
    }
}
