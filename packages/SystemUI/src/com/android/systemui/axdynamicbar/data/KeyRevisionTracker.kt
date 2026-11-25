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
import java.util.concurrent.atomic.AtomicLong

/**
 * Cap revision monotonic theo tien trinh cho tung key va config.
 * Khong dung packed bit xor, khong reset sequence khi removeKey de tranh reuse revision.
 */
class KeyRevisionTracker {
    private val sequence = AtomicLong(0L)
    private val keyRevisions = ConcurrentHashMap<String, Long>()

    /**
     * Dam bao key duoc track voi mot revision monotonic moi neu chua co.
     * Tra ve revision hien tai hoac moi cap.
     */
    fun ensureTracked(key: String): Long {
        return keyRevisions.computeIfAbsent(key) { sequence.incrementAndGet() }
    }

    fun isTracked(key: String): Boolean = keyRevisions.containsKey(key)

    /**
     * Tang revision khi co post hoac update authoritative.
     * Luon cap mot gia tri monotonic moi cho key.
     */
    fun bumpKey(key: String): Long {
        val next = sequence.incrementAndGet()
        keyRevisions[key] = next
        return next
    }

    /**
     * Xoa key khoi live tracking, giai phong bo nho.
     * Khong reset sequence tien trinh de khi key duoc repost se nhan revision moi lon hon.
     */
    fun removeKey(key: String): Boolean {
        return keyRevisions.remove(key) != null
    }

    /**
     * Invalidate toan bo key dang track khi config/theme thay doi.
     * Cap revision moi cho tat ca key dang live.
     */
    fun bumpConfig(): Long {
        val cfgRev = sequence.incrementAndGet()
        for (key in keyRevisions.keys) {
            keyRevisions[key] = sequence.incrementAndGet()
        }
        return cfgRev
    }

    /**
     * Lay revision hien tai cua key. Neu key khong duoc track, tra ve 0L.
     */
    fun revision(key: String): Long {
        return keyRevisions[key] ?: 0L
    }

    /**
     * Kiem tra revision co khop phien ban hien hanh cua key hay khong.
     * Tra ve false neu key da bi xoa hoac da duoc cap phien ban moi hon.
     */
    fun isCurrent(key: String, revision: Long): Boolean {
        return revision > 0L && keyRevisions[key] == revision
    }

    /**
     * Xoa toan bo key dang track khi dung nghe; giu nguyen sequence monotonic.
     */
    fun clear() {
        keyRevisions.clear()
    }
}
