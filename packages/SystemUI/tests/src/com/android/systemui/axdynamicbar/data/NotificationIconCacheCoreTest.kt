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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIconCacheCoreTest {

    data class FakeSbn(
        val key: String,
        val iconId: String,
        val isOnlyAlertOnce: Boolean = false,
    )

    data class FakeDrawable(val id: String)

    private fun fakeLoader(source: FakeSbn): FakeDrawable = FakeDrawable(source.iconId)

    @Test
    fun postFirst_thenCollectionUpdate_updatesAuthoritativeAndRevisionMonotonically() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val key = "com.test.app|1|tag|1000"

        // 1. Post den truoc voi icon1
        val sbn1 = FakeSbn(key, "icon1")
        val (d1, rev1) = cache.ingestPostedSource(key, sbn1, ::fakeLoader)
        assertEquals("icon1", d1?.id)
        assertTrue(rev1 > 0L)
        assertEquals(rev1, cache.cachedSlot(key)?.revision)
        assertEquals("icon1", cache.cachedSlot(key)?.drawable?.id)

        // 2. Collection update den sau voi icon2
        val sbn2 = FakeSbn(key, "icon2")
        cache.onCollectionUpdated(key, sbn2)

        // Slot cu bi invalidate de tranh dung lai icon1
        assertNull(cache.cachedSlot(key))
        val rev2 = cache.revision(key)
        assertTrue("rev2 phai lon hon rev1", rev2 > rev1)

        // 3. Resolve snapshot hien tai phai ra icon2 voi revision rev2
        val resolved = cache.resolveCurrent(key, null, ::fakeLoader)
        assertNotNull(resolved)
        val (d2, revResolved) = resolved!!
        assertEquals("icon2", d2?.id)
        assertEquals(rev2, revResolved)
        assertEquals(rev2, cache.cachedSlot(key)?.revision)
        assertEquals("icon2", cache.cachedSlot(key)?.drawable?.id)
    }

    @Test
    fun collectionFirst_thenPost_updatesAuthoritativeAndRevisionMonotonically() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val key = "com.test.app|2|tag|1000"

        // 1. Collection update den truoc voi icon2
        val sbn2 = FakeSbn(key, "icon2")
        cache.onCollectionUpdated(key, sbn2)
        assertEquals(sbn2, cache.authoritativeSource(key))

        // 2. Ingest post den sau voi icon2
        val (d2, revPost) = cache.ingestPostedSource(key, sbn2, ::fakeLoader)
        assertEquals("icon2", d2?.id)
        assertTrue(revPost > 0L)
        assertEquals("icon2", cache.cachedSlot(key)?.drawable?.id)

        // 3. loadByKey tra ve dung icon2 voi revPost
        val byKey = cache.loadByKey(key, ::fakeLoader)
        assertNotNull(byKey)
        assertEquals("icon2", byKey!!.first?.id)
        assertEquals(revPost, byKey.second)
    }

    @Test
    fun onlyAlertOnce_staleHeldEvent_resolvesAuthoritativeSnapshotWithoutPoisoningCache() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val key = "com.test.clock|timer|0"

        // T0: Post ban dau tao event voi sbnOld (iconOld)
        val sbnOld = FakeSbn(key, "clock_icon_old")
        val (dOld, revOld) = cache.ingestPostedSource(key, sbnOld, ::fakeLoader)
        assertEquals("clock_icon_old", dOld?.id)

        // Gia su event trong UI giu snapshot sbnOld
        val heldEventSbnSnapshot = sbnOld

        // T1: App update FLAG_ONLY_ALERT_ONCE voi iconNew
        val sbnNew = FakeSbn(key, "clock_icon_new", isOnlyAlertOnce = true)
        cache.onCollectionUpdated(key, sbnNew)
        val revNew = cache.revision(key)
        assertTrue(revNew > revOld)

        // T2: Invalidation tick xay ra; refresh consumer goi resolveCurrent voi fallback la sbnOld da luu trong event
        // Loader PHAI uu tien authoritative source sbnNew, TUYET DOI khong load lai sbnOld vao slot
        val refreshed = cache.resolveCurrent(key, heldEventSbnSnapshot, ::fakeLoader)
        assertNotNull(refreshed)
        val (dRefreshed, revRefreshed) = refreshed!!

        assertEquals("clock_icon_new", dRefreshed?.id)
        assertEquals(revNew, revRefreshed)

        // Cache slot khong bi poison: loadByKey van lay duoc iconNew
        val slot = cache.cachedSlot(key)
        assertNotNull(slot)
        assertEquals("clock_icon_new", slot!!.drawable?.id)
        assertEquals(revNew, slot.revision)

        val loadedByKey = cache.loadByKey(key, ::fakeLoader)
        assertEquals("clock_icon_new", loadedByKey?.first?.id)
        assertEquals(revNew, loadedByKey?.second)
    }

    @Test
    fun removal_cleansUpAuthoritativeAndSlots_doesNotReloadOrTrackStaleSource() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val key = "com.test.remove|1"

        val sbn = FakeSbn(key, "icon_rem")
        cache.ingestPostedSource(key, sbn, ::fakeLoader)
        assertTrue(cache.isTracked(key))
        assertNotNull(cache.cachedSlot(key))
        assertNotNull(cache.authoritativeSource(key))

        // Collection xoa entry
        val removed = cache.onCollectionRemoved(key)
        assertTrue(removed)

        assertFalse(cache.isTracked(key))
        assertNull(cache.cachedSlot(key))
        assertNull(cache.authoritativeSource(key))
        assertEquals(0L, cache.revision(key))

        // Goi resolveCurrent tren stale event khong duoc reload hay re-track
        val resolvedAfterRemove = cache.resolveCurrent(key, sbn, ::fakeLoader)
        assertNull("Khong duoc re-track hay reload source da bi go", resolvedAfterRemove)
        assertFalse("Key van phai la untracked", cache.isTracked(key))
        assertNull("Slot khong duoc tao lai", cache.cachedSlot(key))
    }

    @Test
    fun configBump_invalidatesSlots_bumpsMonotonicRevisionsAcrossAllLiveKeys() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val k1 = "app1"
        val k2 = "app2"

        val (_, r1_0) = cache.ingestPostedSource(k1, FakeSbn(k1, "i1"), ::fakeLoader)
        val (_, r2_0) = cache.ingestPostedSource(k2, FakeSbn(k2, "i2"), ::fakeLoader)

        assertNotNull(cache.cachedSlot(k1))
        assertNotNull(cache.cachedSlot(k2))

        cache.bumpConfig()

        // Slots da bi clear de yeu cau re-draw theo theme/density moi
        assertNull(cache.cachedSlot(k1))
        assertNull(cache.cachedSlot(k2))

        val r1_1 = cache.revision(k1)
        val r2_1 = cache.revision(k2)

        assertNotEquals(r1_0, r1_1)
        assertNotEquals(r2_0, r2_1)
        assertTrue(r1_1 > r1_0)
        assertTrue(r2_1 > r2_0)

        // Resolve lai se nap cache voi revision moi
        val (d1New, r1Resolved) = cache.resolveCurrent(k1, null, ::fakeLoader)!!
        assertEquals("i1", d1New?.id)
        assertEquals(r1_1, r1Resolved)
        assertEquals(r1_1, cache.cachedSlot(k1)?.revision)
    }

    @Test
    fun bootstrapFromLiveCollection_sourcePredatesListener() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val existingEntry = FakeSbn("existing", "liveIcon")
        cache.onCollectionUpdated("existing", existingEntry)
        val snapshot = cache.resolveCurrent("existing", null, ::fakeLoader)!!
        assertEquals("liveIcon", snapshot.first?.id)
        assertTrue(snapshot.second > 0L)
    }

    @Test
    fun presentNullSnapshot_invalidatesOldIconAndCachesFailure() {
        val cache = NotificationIconCacheCore<FakeSbn, FakeDrawable>()
        val before = cache.ingestPostedSource("key", FakeSbn("key", "valid"), ::fakeLoader)
        cache.onCollectionUpdated("key", FakeSbn("key", "unavailable"))
        val after = cache.resolveCurrent("key", null) { null }!!
        assertNull(after.first)
        assertTrue(after.second > before.second)
        assertNull(cache.resolveCurrent("key", null, ::fakeLoader)!!.first)
    }
}
