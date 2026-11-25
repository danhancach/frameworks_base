/*
 * Copyright (C) 2024-2026 Evolution X
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

package com.android.systemui.media.controls.util

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.Dumpable
import com.android.systemui.axdynamicbar.domain.AxDynamicBarSettings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import com.android.systemui.util.asIndenting
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Dieu phoi hien thi trinh phat da phuong tien (QS media) tren man hinh khoa.
 * Khi bat pill media (Dynamic Bar phat lai da phuong tien tren keyguard),
 * QS media ngoai man hinh khoa se bi tat de tranh trung lap.
 */
@SysUISingleton
class LockscreenMediaHelper
@Inject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val secureSettings: SecureSettings,
    private val axDynamicBarSettings: AxDynamicBarSettings,
    dumpManager: DumpManager,
) : Dumpable {

    // Lang nghe setting hien thi media tren man hinh khoa cua he thong
    private val mediaControlsOnLockscreenSettingFlow: Flow<Boolean> =
        secureSettings
            .observerFlow(UserHandle.USER_ALL, Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN)
            .onStart { emit(Unit) }
            .map {
                secureSettings.getBoolForUser(
                    Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                    true,
                    UserHandle.USER_CURRENT,
                )
            }
            .distinctUntilChanged()

    // Dynamic Bar pill media duoc coi la bat tren keyguard khi:
    // Dynamic Bar bat, keyguard bat va su kien media khong bi tat
    private val isPillMediaEnabledFlow: Flow<Boolean> =
        combine(
            axDynamicBarSettings.isEnabled,
            axDynamicBarSettings.isKeyguardEnabled,
            axDynamicBarSettings.disabledEventTypes,
        ) { enabled, keyguardEnabled, disabledTypes ->
            enabled && keyguardEnabled && "media" !in disabledTypes
        }.distinctUntilChanged()

    val isMediaAllowedOnLockscreen: StateFlow<Boolean> =
        combine(
            mediaControlsOnLockscreenSettingFlow,
            isPillMediaEnabledFlow,
        ) { userSettingAllowed, pillMediaActive ->
            // Tat qs media ngoai man hinh khoa khi bat pill media
            userSettingAllowed && !pillMediaActive
        }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = computeIsMediaAllowedOnLockscreenSync(),
            )

    init {
        dumpManager.registerDumpable(TAG, this)
    }

    fun isMediaAllowedOnLockscreen(): Boolean = isMediaAllowedOnLockscreen.value

    private fun computeIsMediaAllowedOnLockscreenSync(): Boolean {
        val userSettingAllowed =
            secureSettings.getBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                true,
                UserHandle.USER_CURRENT,
            )
        val pillMediaActive =
            axDynamicBarSettings.isEnabled.value &&
                axDynamicBarSettings.isKeyguardEnabled.value &&
                "media" !in axDynamicBarSettings.disabledEventTypes.value
        return userSettingAllowed && !pillMediaActive
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.asIndenting().run {
            println("LockscreenMediaHelper")
            val userSettingAllowed =
                secureSettings.getBoolForUser(
                    Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                    true,
                    UserHandle.USER_CURRENT,
                )
            val pillMediaActive =
                axDynamicBarSettings.isEnabled.value &&
                    axDynamicBarSettings.isKeyguardEnabled.value &&
                    "media" !in axDynamicBarSettings.disabledEventTypes.value
            println("userSettingAllowed: $userSettingAllowed")
            println("pillMediaActive: $pillMediaActive")
            println("isMediaAllowedOnLockscreen: ${isMediaAllowedOnLockscreen.value}")
        }
    }

    companion object {
        private const val TAG = "LockscreenMediaHelper"
    }
}
