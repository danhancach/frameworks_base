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

import java.util.concurrent.ConcurrentHashMap

/**
 * Core cache va state model cho smallIcon cua thong bao.
 * Doc lap voi Android framework de dam bao thuan khiet, de dang unit-test host,
 * tranh cache poisoning khi SBN cu duoc chuyen vao.
 */
class NotificationIconCacheCore<TSource, TDrawable>(
    private val tracker: KeyRevisionTracker = KeyRevisionTracker(),
) {
    data class Slot<TDrawable>(val revision: Long, val drawable: TDrawable?)

    private val slots = ConcurrentHashMap<String, Slot<TDrawable>>()
    private val authoritativeSources = ConcurrentHashMap<String, TSource>()

    /**
     * Nhan nguon authoritative tu post (Scrim/NMS).
     * Luon cap nhat nguon moi nhat, bump revision va cache drawable moi.
     */
    fun ingestPostedSource(
        key: String,
        source: TSource,
        loader: (TSource) -> TDrawable?,
    ): Pair<TDrawable?, Long> {
        authoritativeSources[key] = source
        // bumpKey luon cap revision moi — khong can ensureTracked truoc.
        val rev = tracker.bumpKey(key)
        val drawable = loader(source)
        slots[key] = Slot(rev, drawable)
        return drawable to rev
    }

    /**
     * Callback khi collection them hoac cap nhat entry.
     * Luu nguon authoritative moi; neu key dang duoc track, bump revision va invalidate slot.
     */
    fun onCollectionUpdated(key: String, source: TSource) {
        authoritativeSources[key] = source
        if (tracker.isTracked(key)) {
            tracker.bumpKey(key)
            slots.remove(key)
        }
    }

    /**
     * Callback khi collection go bo entry.
     * Xoa authoritative source, xoa khoi tracker va cache slot.
     */
    fun onCollectionRemoved(key: String): Boolean {
        authoritativeSources.remove(key)
        slots.remove(key)
        return tracker.removeKey(key)
    }

    /**
     * Resolve icon snapshot hien tai doc lap voi SBN snapshot cu cua event.
     * Uu tien nguon authoritative da luu, tranh cache poisoning.
     * Neu key khong con tracked va khong co nguon authoritative, khong reload hay re-track.
     */
    fun resolveCurrent(
        key: String,
        fallbackSource: TSource?,
        loader: (TSource) -> TDrawable?,
    ): Pair<TDrawable?, Long>? {
        if (!tracker.isTracked(key) && !authoritativeSources.containsKey(key)) {
            return null
        }
        val targetSource = authoritativeSources[key] ?: fallbackSource ?: return null
        val rev = tracker.ensureTracked(key)
        val cached = slots[key]
        if (cached != null && cached.revision == rev) {
            return cached.drawable to rev
        }
        val drawable = loader(targetSource)
        slots[key] = Slot(rev, drawable)
        return drawable to rev
    }

    /**
     * Load icon theo key tu nguon authoritative.
     */
    fun loadByKey(
        key: String,
        loader: (TSource) -> TDrawable?,
    ): Pair<TDrawable?, Long>? {
        val source = authoritativeSources[key] ?: return null
        return resolveCurrent(key, source, loader)
    }

    fun isTracked(key: String): Boolean = tracker.isTracked(key)

    fun revision(key: String): Long = tracker.revision(key)

    fun bumpConfig() {
        tracker.bumpConfig()
        slots.clear()
    }

    fun cachedSlot(key: String): Slot<TDrawable>? = slots[key]

    fun authoritativeSource(key: String): TSource? = authoritativeSources[key]
}
