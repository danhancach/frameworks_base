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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationStateReducerTest {

    data class TestNotification(
        val key: String,
        val title: String,
        val text: String,
        val appIcon: String,
        val iconRevision: Long,
        val createdAt: Long,
    )

    data class TestAlert(
        val key: String,
        val title: String,
        val text: String,
        val appIcon: String,
        val iconRevision: Long,
        val createdAt: Long,
        val actions: List<String> = listOf("Reply", "Archive"),
    )

    @Test
    fun twoNotifications_orderA_B_refreshB_keepsOrderA_B() {
        val notifA = TestNotification(
            key = "keyA",
            title = "Title A",
            text = "Text A",
            appIcon = "iconA_v1",
            iconRevision = 1L,
            createdAt = 1000L,
        )
        val notifB = TestNotification(
            key = "keyB",
            title = "Title B",
            text = "Text B",
            appIcon = "iconB_v1",
            iconRevision = 1L,
            createdAt = 2000L,
        )
        val initialList = listOf(notifA, notifB)
        assertEquals("keyA", initialList[0].key)
        assertEquals("keyB", initialList[1].key)

        // Refresh icon cho notifB
        val refreshedList = NotificationStateReducer.refreshListInPlace(
            list = initialList,
            key = "keyB",
            keySelector = { it.key },
            updater = { it.copy(appIcon = "iconB_v2", iconRevision = 2L) }
        )

        // Thu tu phai giu nguyen [keyA, keyB], khong bi dao hoac dua len dau
        assertEquals(2, refreshedList.size)
        assertEquals("keyA", refreshedList[0].key)
        assertEquals("iconA_v1", refreshedList[0].appIcon)

        assertEquals("keyB", refreshedList[1].key)
        assertEquals("iconB_v2", refreshedList[1].appIcon)
        assertEquals(2L, refreshedList[1].iconRevision)
        assertEquals("Title B", refreshedList[1].title)
        assertEquals("Text B", refreshedList[1].text)
        assertEquals(2000L, refreshedList[1].createdAt)
    }

    @Test
    fun dismissB_thenRefreshB_doesNotResurrectB() {
        val notifA = TestNotification("keyA", "A", "A text", "iconA", 1L, 1000L)
        val notifB = TestNotification("keyB", "B", "B text", "iconB", 1L, 2000L)
        val initialList = listOf(notifA, notifB)

        // Dismiss B
        val listAfterDismiss = NotificationStateReducer.dismiss(
            list = initialList,
            id = "keyB",
            idSelector = { it.key }
        )
        assertEquals(1, listAfterDismiss.size)
        assertEquals("keyA", listAfterDismiss[0].key)

        // Refresh B khi da dismiss: khong duoc hoi sinh hoac them dummy event vao list
        val listAfterRefresh = NotificationStateReducer.refreshListInPlace(
            list = listAfterDismiss,
            key = "keyB",
            keySelector = { it.key },
            updater = { it.copy(appIcon = "iconB_resurrect?", iconRevision = 99L) }
        )

        assertEquals(1, listAfterRefresh.size)
        assertEquals("keyA", listAfterRefresh[0].key)
    }

    @Test
    fun alertClosed_refreshNotification_doesNotReopenAlert() {
        val closedAlert: TestAlert? = null

        // Khi alert da dong, refresh khong duoc tao hoac mo lai alert
        val result = NotificationStateReducer.refreshAlert(
            alert = closedAlert,
            key = "keyA",
            keySelector = { it.key },
            updater = { it.copy(appIcon = "newIcon", iconRevision = 2L) }
        )

        assertNull("Alert phai tiep tuc la null", result)
    }

    @Test
    fun alertOpenForB_refreshB_updatesIconAndRevisionOnly() {
        val openAlert = TestAlert(
            key = "keyB",
            title = "Message from Bob",
            text = "Dinner at 7?",
            appIcon = "iconB_v1",
            iconRevision = 1L,
            createdAt = 123456L,
            actions = listOf("Reply", "Archive"),
        )

        // Refresh cho B: chi cap nhat appIcon va iconRevision
        val updatedAlert = NotificationStateReducer.refreshAlert(
            alert = openAlert,
            key = "keyB",
            keySelector = { it.key },
            updater = { it.copy(appIcon = "iconB_v2", iconRevision = 2L) }
        )

        assertNotNull(updatedAlert)
        assertEquals("iconB_v2", updatedAlert!!.appIcon)
        assertEquals(2L, updatedAlert.iconRevision)
        // Cac metadata khac phai duoc bao toan tuyet doi
        assertEquals("Message from Bob", updatedAlert.title)
        assertEquals("Dinner at 7?", updatedAlert.text)
        assertEquals(123456L, updatedAlert.createdAt)
        assertEquals(listOf("Reply", "Archive"), updatedAlert.actions)
    }

    @Test
    fun alertOpenForA_refreshB_alertForAUntouched() {
        val openAlertForA = TestAlert(
            key = "keyA",
            title = "Alert A",
            text = "Text A",
            appIcon = "iconA_v1",
            iconRevision = 1L,
            createdAt = 500L,
        )

        // Refresh cho B den khi alert dang mo la A
        val result = NotificationStateReducer.refreshAlert(
            alert = openAlertForA,
            key = "keyB",
            keySelector = { it.key },
            updater = { it.copy(appIcon = "iconB_v2", iconRevision = 2L) }
        )

        assertNotNull(result)
        assertEquals("keyA", result!!.key)
        assertEquals("iconA_v1", result.appIcon)
        assertEquals(1L, result.iconRevision)
    }
}
