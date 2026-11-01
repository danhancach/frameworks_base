package com.android.systemui.axdynamicbar.domain

import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.provider.Settings.Global
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.EVENT_TYPE_IDS
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.GlobalSettings
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

@SysUISingleton
class AxDynamicBarSettings @Inject constructor(
    @Main private val mainHandler: Handler,
    private val secureSettings: SecureSettings,
    private val context: android.content.Context,
    private val globalSettings: GlobalSettings,
) {
    companion object {
        const val KEY_ENABLED = "ax_dynamic_bar_enabled"
        const val KEY_EVENTS = "ax_dynamic_bar_events"
        const val KEY_KEYGUARD_ENABLED = "ax_dynamic_bar_keyguard_enabled"
        const val KEY_KEYGUARD_MUSIC_PILL_ENABLED = "ax_dynamic_bar_keyguard_music_pill"
        const val KEY_KEYGUARD_BATTERY_CHIP_MODE = "ax_dynamic_bar_keyguard_battery_chip_mode"
        const val KEY_COMPACT_NOTIFICATIONS = "ax_dynamic_bar_compact_notifications"
        const val KEY_CHIP_STYLE = "ax_dynamic_bar_chip_style"

        // Mac dinh ROM = thiet lap pdx237 (Secure settings tren may)
        const val DEFAULT_ENABLED = 1
        const val DEFAULT_KEYGUARD_ENABLED = 1
        const val DEFAULT_KEYGUARD_MUSIC_PILL = 1
        const val DEFAULT_KEYGUARD_BATTERY_CHIP_MODE = 2 // Always show
        const val DEFAULT_COMPACT_NOTIFICATIONS = 1
        const val DEFAULT_CHIP_STYLE = 0
        // JSON danh sach event bi tat (khop ax_dynamic_bar_events)
        const val DEFAULT_DISABLED_EVENTS_JSON =
            """["biometric_unlock","app_switch","torch","clipboard","vpn","ringer","hotspot","bluetooth","charging","sports"]"""

        /** null = chua ghi Secure → mac dinh ROM; "" = user bat het event. */
        fun parseDisabledEventsJson(json: String?): Set<String> {
            if (json == null) {
                return try {
                    val arr = JSONArray(DEFAULT_DISABLED_EVENTS_JSON)
                    (0 until arr.length()).mapNotNull { arr.optString(it) }.toSet()
                } catch (_: Exception) {
                    emptySet()
                }
            }
            if (json.isBlank()) return emptySet()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { arr.optString(it) }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }

    private val contentResolver = context.contentResolver

    private val _isEnabled = MutableStateFlow(DEFAULT_ENABLED == 1)
    @get:JvmName("getIsEnabled") val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isKeyguardEnabled = MutableStateFlow(DEFAULT_KEYGUARD_ENABLED == 1)
    val isKeyguardEnabled: StateFlow<Boolean> = _isKeyguardEnabled.asStateFlow()

    private val _isKeyguardMusicPillEnabled = MutableStateFlow(DEFAULT_KEYGUARD_MUSIC_PILL == 1)
    val isKeyguardMusicPillEnabled: StateFlow<Boolean> = _isKeyguardMusicPillEnabled.asStateFlow()

    private val _keyguardBatteryChipMode = MutableStateFlow(DEFAULT_KEYGUARD_BATTERY_CHIP_MODE)
    val keyguardBatteryChipMode: StateFlow<Int> = _keyguardBatteryChipMode.asStateFlow()

    private val _useWaveformSeekBar = MutableStateFlow(false)
    val useWaveformSeekBar: StateFlow<Boolean> = _useWaveformSeekBar.asStateFlow()
    
    private val _compactNotifications = MutableStateFlow(DEFAULT_COMPACT_NOTIFICATIONS == 1)
    val compactNotifications: StateFlow<Boolean> = _compactNotifications.asStateFlow()

    private val _chipStyle = MutableStateFlow(DEFAULT_CHIP_STYLE)
    val chipStyle: StateFlow<Int> = _chipStyle.asStateFlow()

    private val _disabledEventTypes =
        MutableStateFlow(parseDisabledEventsJson(null))
    val disabledEventTypes: StateFlow<Set<String>> = _disabledEventTypes.asStateFlow()

    init {
        refresh()
    }

    private val settingsObserver =
        object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        refresh()
        secureSettings.registerContentObserverForUserSync(
            KEY_ENABLED,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_EVENTS,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_KEYGUARD_ENABLED,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_KEYGUARD_MUSIC_PILL_ENABLED,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_KEYGUARD_BATTERY_CHIP_MODE,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_CHIP_STYLE,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.MEDIA_WAVEFORM_SEEKBAR),
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
        secureSettings.registerContentObserverForUserSync(
            KEY_COMPACT_NOTIFICATIONS,
            false,
            settingsObserver,
            UserHandle.USER_ALL,
        )
    }

    fun destroy() {
        if (!initialized) return
        initialized = false
        secureSettings.getContentResolver().unregisterContentObserver(settingsObserver)
        contentResolver.unregisterContentObserver(settingsObserver)
        globalSettings.getContentResolver().unregisterContentObserver(settingsObserver)
    }

    private fun refresh() {
        _isEnabled.value =
            secureSettings.getIntForUser(
                KEY_ENABLED, DEFAULT_ENABLED, UserHandle.USER_CURRENT) == 1
        _isKeyguardEnabled.value =
            secureSettings.getIntForUser(
                KEY_KEYGUARD_ENABLED, DEFAULT_KEYGUARD_ENABLED, UserHandle.USER_CURRENT) == 1
        _keyguardBatteryChipMode.value =
            secureSettings.getIntForUser(
                KEY_KEYGUARD_BATTERY_CHIP_MODE,
                DEFAULT_KEYGUARD_BATTERY_CHIP_MODE,
                UserHandle.USER_CURRENT,
            )
        _isKeyguardMusicPillEnabled.value =
            secureSettings.getIntForUser(
                KEY_KEYGUARD_MUSIC_PILL_ENABLED,
                DEFAULT_KEYGUARD_MUSIC_PILL,
                UserHandle.USER_CURRENT,
            ) == 1
        _chipStyle.value =
            secureSettings.getIntForUser(
                KEY_CHIP_STYLE, DEFAULT_CHIP_STYLE, UserHandle.USER_CURRENT)
        _useWaveformSeekBar.value =
            Settings.System.getIntForUser(
                contentResolver, Settings.System.MEDIA_WAVEFORM_SEEKBAR, 0, UserHandle.USER_CURRENT,) == 1
        _compactNotifications.value =
            secureSettings.getIntForUser(
                KEY_COMPACT_NOTIFICATIONS,
                DEFAULT_COMPACT_NOTIFICATIONS,
                UserHandle.USER_CURRENT,
            ) == 1

        _disabledEventTypes.value =
            parseDisabledEventsJson(
                secureSettings.getStringForUser(KEY_EVENTS, UserHandle.USER_CURRENT)
            )
    }

    fun isEventEnabled(event: IslandEvent): Boolean {
        // AospChip dung key AOSP — map sang id toggle Evolver (screen_recording/casting/call)
        val typeId =
            when (event) {
                is IslandEvent.AospChip ->
                    when (val key = event.active.key) {
                        "ScreenRecord" -> "screen_recording"
                        "CastToOtherDevice",
                        "ShareToApp" -> "casting"
                        else ->
                            if (key.startsWith("callChip-")) "call" else "promoted_ongoing"
                    }
                else -> EVENT_TYPE_IDS[event::class.java] ?: return true
            }
        return typeId !in _disabledEventTypes.value
    }

    fun isKeyguardBiometricUnlockEventsActive(): Boolean =
        _isEnabled.value && _isKeyguardEnabled.value &&
            "biometric_unlock" !in _disabledEventTypes.value

    fun isNotificationEventsActive(): Boolean =
        _isEnabled.value && "notification" !in _disabledEventTypes.value
}
