package com.android.systemui.axdynamicbar.shared

import android.app.PendingIntent
import android.net.Uri
import com.android.systemui.axdynamicbar.model.IslandEvent

val EVENT_TYPE_IDS: Map<Class<out IslandEvent>, String> =
    mapOf(
        IslandEvent.AudioRecording::class.java to "audio_recording",
        IslandEvent.PromotedOngoing::class.java to "promoted_ongoing",
        IslandEvent.Sports::class.java to "sports",
        IslandEvent.Media::class.java to "media",
        IslandEvent.Call::class.java to "call",
        IslandEvent.Bluetooth::class.java to "bluetooth",
        IslandEvent.Hotspot::class.java to "hotspot",
        IslandEvent.Charging::class.java to "charging",
        IslandEvent.Alarm::class.java to "alarm",
        IslandEvent.Timer::class.java to "timer",
        IslandEvent.Stopwatch::class.java to "stopwatch",
        IslandEvent.RingerMode::class.java to "ringer",
        IslandEvent.Vpn::class.java to "vpn",
        IslandEvent.Clipboard::class.java to "clipboard",
        IslandEvent.Notification::class.java to "notification",
        IslandEvent.AppSwitch::class.java to "app_switch",
        IslandEvent.Torch::class.java to "torch",
        IslandEvent.BiometricUnlock::class.java to "biometric_unlock",
        IslandEvent.AospChip::class.java to "aosp_chip",
    )

/**
 * Chronometer (Timer/Stopwatch, ke ca tung multi-timer): expand chi dung 1 event.id.
 * Event khac → null (stack nhu cu).
 */
fun expandOnlyEventIdFor(event: IslandEvent): String? =
    when (event) {
        is IslandEvent.Timer, is IslandEvent.Stopwatch -> event.id
        else -> null
    }

interface IslandActions {
    val onFocusableRequested: ((Boolean) -> Unit)?
        get() = null
    fun collapseIsland()
    fun dismissEvent(event: IslandEvent)
    /**
     * Vuot ngang the stack: an dung su kien do, roi thu overlay neu con the stack.
     * Mac dinh chi dismiss (caller chua override).
     */
    fun dismissEventFromExpandedSwipe(event: IslandEvent) {
        dismissEvent(event)
    }
    /** Pin event theo id (stack expand doi panel). Mac dinh no-op. */
    fun pinEventById(eventId: String) {}
    /** Pin alert theo sbn.key (stack overlay doi the). Mac dinh no-op. */
    fun pinNotificationAlertByKey(key: String) {}
    fun togglePlayPause(mediaKey: String)
    fun skipNext(mediaKey: String)
    fun skipPrev(mediaKey: String)
    fun seekTo(mediaKey: String, position: Long)
    fun sendCustomAction(mediaKey: String, action: String)
    fun openMediaOutputSwitcher(mediaKey: String)
    fun openMediaApp(mediaKey: String)
    /** Mo Clock tu contentIntent / packageName (tap vung noi dung expand). */
    fun openChronometerApp(contentIntent: PendingIntent?, packageName: String)
    /**
     * Pause/resume timer Clock. Uu tien [actionIntent]/cache PI; fallback sendBroadcast
     * (AMS cho SystemUI → TimerReceiver).
     */
    fun sendDeskClockTimerAction(
        packageName: String,
        timerId: Int,
        pause: Boolean,
        actionIntent: PendingIntent? = null,
    ) {}
    fun disconnectBluetooth(address: String)
    fun setRingerMode(mode: Int)
    fun toggleTorch()
    fun setTorchLevel(level: Int)
    fun setTorchLevelTemporary(level: Int)
    fun copyToClipboard(text: String)
    fun copyUriToClipboard(uri: Uri)
    fun openUrl(url: String)
    fun removeClipboardItem(id: Long)
    fun switchToApp(taskId: Int)
    fun killApp(taskId: Int)
    fun onNotificationInteraction(eventId: String)
    fun onNotificationInteractionEnd(eventId: String)
    fun onNotificationAlertInteractionStart()
    fun onNotificationAlertInteractionEnd()
    /**
     * Alert scene Compact vs mo rong (Collapsed/Expanded card).
     * Compact: timeout + vuot len; mo rong: chi dismiss chu dong.
     */
    fun onNotificationAlertCompactChanged(compact: Boolean) {}
    fun onAlertReplyActiveChanged(active: Boolean) {}
    fun launchNotificationDismissingKeyguard(event: IslandEvent.Notification)
}
