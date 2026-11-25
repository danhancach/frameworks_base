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

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.statusbar.policy.ConfigurationController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Load notification.smallIcon cho Dynamic Bar.
 *
 * ingestPostedSbn(sbn): nguon authoritative tu NotificationListener/Scrim post —
 * luon load tu SBN do, bump revision (khong phu thuoc ordering collection).
 */
@SysUISingleton
class NotificationSmallIconLoader
@Inject
constructor(
    @Application private val context: Context,
    private val notifCollection: CommonNotifCollection,
    configurationController: ConfigurationController,
) {
    private val core = NotificationIconCacheCore<StatusBarNotification, Drawable>()
    private val _invalidationTick = MutableStateFlow(0L)
    val invalidationTick: StateFlow<Long> = _invalidationTick.asStateFlow()

    private val notifListener =
        object : NotifCollectionListener {
            override fun onEntryAdded(entry: NotificationEntry) {
                core.onCollectionUpdated(entry.key, entry.sbn)
                tick()
            }

            override fun onEntryUpdated(entry: NotificationEntry) {
                core.onCollectionUpdated(entry.key, entry.sbn)
                tick()
            }

            override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
                if (core.onCollectionRemoved(entry.key)) {
                    tick()
                }
            }
        }

    private val configListener =
        object : ConfigurationController.ConfigurationListener {
            override fun onConfigChanged(newConfig: Configuration) = onConfigurationInvalidated()

            override fun onDensityOrFontScaleChanged() = onConfigurationInvalidated()

            override fun onThemeChanged() = onConfigurationInvalidated()

            override fun onUiModeChanged() = onConfigurationInvalidated()
        }

    init {
        notifCollection.addCollectionListener(notifListener)
        configurationController.addCallback(configListener)
    }

    /**
     * Authoritative refresh tu SBN vua post (Scrim/NMS).
     * Bump revision + load tu payload nay — dung ca khi collection chua/bumped truoc.
     */
    fun ingestPostedSbn(sbn: StatusBarNotification): Pair<Drawable?, Long> {
        val result = core.ingestPostedSource(sbn.key, sbn) { loadFresh(it) }
        tick()
        return result
    }

    /**
     * Resolve icon snapshot hien tai doc lap voi SBN snapshot cu cua event.
     * Uu tien nguon authoritative da luu (ingestPostedSbn hoac collection callbacks).
     * Neu key khong con ton tai trong collection va authoritative source, tra ve null.
     */
    fun resolveCurrentSnapshot(key: String): Pair<Drawable?, Long>? {
        // Entry co the co truoc listener: bootstrap tu collection live, khong tu SBN cu cua event.
        if (core.authoritativeSource(key) == null) {
            val entry = notifCollection.getEntry(key) ?: return null
            core.onCollectionUpdated(key, entry.sbn)
        }
        return core.resolveCurrent(key, null) { loadFresh(it) }
    }

    fun loadByKey(notificationKey: String): Drawable? =
        resolveCurrentSnapshot(notificationKey)?.first

    fun revisionByKey(notificationKey: String): Long = core.revision(notificationKey)

    private fun onConfigurationInvalidated() {
        core.bumpConfig()
        tick()
    }

    private fun tick() {
        _invalidationTick.value = _invalidationTick.value + 1L
    }

    private fun loadFresh(sbn: StatusBarNotification): Drawable? {
        val notification = sbn.notification ?: return null
        val icon = notification.smallIcon ?: return null
        return loadIcon(icon, sbn.user, notification.iconLevel)
    }

    private fun loadIcon(icon: Icon, user: UserHandle, iconLevel: Int): Drawable? {
        var userId = user.identifier
        if (userId == UserHandle.USER_ALL) {
            userId = UserHandle.USER_SYSTEM
        }
        val raw =
            try {
                icon.loadDrawableAsUser(context, userId)
            } catch (_: Exception) {
                null
            } ?: return null

        val resolved =
            if (raw is AdaptiveIconDrawable) {
                val mono = raw.monochrome
                if (mono != null) {
                    mono.constantState?.newDrawable(context.resources)?.mutate() ?: mono.mutate()
                } else {
                    raw.constantState?.newDrawable(context.resources)?.mutate() ?: raw.mutate()
                }
            } else {
                raw.constantState?.newDrawable(context.resources)?.mutate() ?: raw.mutate()
            }

        resolved.level = iconLevel
        return resolved
    }
}
