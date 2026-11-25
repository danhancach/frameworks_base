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

package com.android.systemui.axdynamicbar.domain

/**
 * Reducer xu ly state transition cho notification list va open alert.
 * Dam bao:
 * 1. Refresh icon tai cho khong coalesce, reorder hoac reinsert notification da dismiss.
 * 2. Alert dang mo chi copy icon va revision tai cho; khong mo lai khi da dong;
 *    khong reset createdAt, timeout, sbn hoac actions.
 */
object NotificationStateReducer {

    /**
     * Cap nhat icon tai cho cho notification trong danh sach.
     * Neu key ton tai tai vi tri idx, thay the dung phan tu tai idx (giu nguyen thu tu).
     * Neu key khong ton tai (vi du da bi dismiss), tra ve danh sach nguyen ven,
     * khong them moi hoac hoi sinh dummy event.
     */
    fun <T> refreshListInPlace(
        list: List<T>,
        key: String,
        keySelector: (T) -> String,
        updater: (T) -> T,
    ): List<T> {
        val idx = list.indexOfFirst { keySelector(it) == key }
        if (idx < 0) return list
        val updated = updater(list[idx])
        val copy = list.toMutableList()
        copy[idx] = updated
        return copy
    }

    /**
     * Cap nhat icon cho alert dang mo.
     * Neu alert == null (da dong hoac chua mo), tra ve null ma khong mo lai alert.
     * Neu alert khac key, giu nguyen alert hien tai.
     * Neu dung key, goi updater de cap nhat icon/revision ma khong lam mat cac metadata khac.
     */
    fun <TAlert> refreshAlert(
        alert: TAlert?,
        key: String,
        keySelector: (TAlert) -> String,
        updater: (TAlert) -> TAlert,
    ): TAlert? {
        if (alert == null) return null
        if (keySelector(alert) != key) return alert
        return updater(alert)
    }

    /**
     * Coalesce event moi hoac content update: xoa phan tu cu neu co va them vao dau danh sach.
     */
    fun <T> coalesce(
        list: List<T>,
        event: T,
        idSelector: (T) -> String,
    ): List<T> {
        val next = list.filterNot { idSelector(it) == idSelector(event) }.toMutableList()
        next.add(0, event)
        return next
    }

    /**
     * Dismiss event: xoa phan tu khoi danh sach theo id.
     */
    fun <T> dismiss(
        list: List<T>,
        id: String,
        idSelector: (T) -> String,
    ): List<T> {
        return list.filterNot { idSelector(it) == id }
    }
}
