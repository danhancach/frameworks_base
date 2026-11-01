package com.android.systemui.axdynamicbar.model

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.drawable.Drawable
import android.net.Uri
import android.service.notification.StatusBarNotification
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel

enum class RecordingState {
    RECORDING,
    PAUSED,
    SAVED,
}

const val NOTIFICATION_DECAY_MS = 5000L
const val NOTIFICATION_FRESH_PRIORITY = 75
const val NOTIFICATION_STALE_PRIORITY = 15
const val MEDIA_PLAYING_PRIORITY = 70
const val MEDIA_PAUSED_PRIORITY = 20

/**
 * [suppressOnDismiss] makes the interactor hide a dismissed event for as long as its source keeps
 * publishing the same id. Only for events whose dismissal cannot touch the source (AospChip).
 *
 * Do not enable it for an event whose dismissal clears its source: ids are reused, and the
 * suppression is released only by a source emission without that id. The clear itself emits that,
 * so the suppression either does nothing or, if that emission is missed, hides every later
 * occurrence for good. Sources that need "stay hidden until it ends" remember the dismissal
 * themselves (see chargingDismissed).
 */
data class EventBehavior(
    val autoDismissMs: Long? = null,
    val suppressOnDismiss: Boolean = false,
    val autoShowsIsland: Boolean = true,
)

sealed class IslandEvent(open val priority: Int, val id: String) : Comparable<IslandEvent> {

    open val behavior: EventBehavior
        get() = DEFAULT_BEHAVIOR

    open fun withoutDrawables(): IslandEvent = this

    data class AospChip(
        val active: OngoingActivityChipModel.Active,
    ) : IslandEvent(priority = priorityForAospChipKey(active.key), id = "aosp_${active.key}") {
        // Dismissal cannot clear an AOSP chip, so it is hidden by id until the chip goes away.
        override val behavior = EventBehavior(suppressOnDismiss = true)
    }

    data class AudioRecording(
        val appName: String = "",
        val state: RecordingState = RecordingState.RECORDING,
        val startTimeMs: Long = System.currentTimeMillis(),
        val actions: List<NotificationAction> = emptyList(),
        val appIcon: Drawable? = null,
        val pausedDurationMs: Long = 0L,
    ) : IslandEvent(priority = 84, id = "audio_recording") {
        override fun withoutDrawables() = copy(appIcon = null)
    }

    data class Media(
        val mediaKey: String = "",
        val track: String,
        val artist: String,
        val isPlaying: Boolean,
        val albumArt: Drawable? = null,
        val progress: Float = 0f,
        val duration: Long = 0L,
        val position: Long = 0L,
        val playbackSpeed: Float = 1f,
        val positionUpdateTime: Long = 0L,
        val outputDeviceName: String = "",
        val customActions: List<MediaCustomAction> = emptyList(),
        val appIcon: Drawable? = null,
        val packageName: String = "",
    ) : IslandEvent(priority = MEDIA_PLAYING_PRIORITY, id = "media_" + mediaKey) {
        override val priority: Int
            get() = if (isPlaying) MEDIA_PLAYING_PRIORITY else MEDIA_PAUSED_PRIORITY
        override fun withoutDrawables() = copy(albumArt = null, appIcon = null)
    }

    data class Bluetooth(
        val deviceName: String,
        val batteryLevel: Int = -1,
        val isConnected: Boolean = true,
        val address: String = "",
        val deviceIcon: Drawable? = null,
        val deviceTypeLabel: String = "",
        val deviceImage: Drawable? = null,
        val leftBatteryLevel: Int? = null,
        val rightBatteryLevel: Int? = null,
        val caseBatteryLevel: Int? = null,
    ) : IslandEvent(priority = 60, id = "bluetooth") {
        // deviceImage stays in the comparison. It arrives after the event is first published, and
        // drawable-only updates are otherwise dropped before they reach the bar.
        override fun withoutDrawables() = copy(deviceIcon = null)

        fun batterySummary(): String? {
            val left = leftBatteryLevel
            val right = rightBatteryLevel
            return when {
                left != null && right != null -> "$left% · $right%"
                left != null -> "$left%"
                right != null -> "$right%"
                batteryLevel >= 0 -> "$batteryLevel%"
                else -> null
            }
        }
    }

    data class Hotspot(val numDevices: Int) : IslandEvent(priority = 55, id = "hotspot")

    data class Charging(
        val level: Int,
        val isWireless: Boolean,
        val isCharging: Boolean = true,
        val isPowerSave: Boolean = false,
        val timeRemaining: String? = null,
    ) : IslandEvent(priority = 50, id = "charging") {
        override val behavior = EventBehavior(autoDismissMs = 3000L)
    }

    data class RingerMode(val mode: Int, val label: String) :
        IslandEvent(priority = 40, id = "ringer_mode") {
        override val behavior = EventBehavior(autoDismissMs = 2500L)
    }

    data class BiometricUnlock(val sourceType: Int = 0, val sourceName: String = "Fingerprint") :
        IslandEvent(priority = 80, id = "biometric_unlock") {
        override val behavior = EventBehavior(autoDismissMs = 2000L)
    }

    data class Torch(val level: Int = -1, val maxLevel: Int = -1) :
        IslandEvent(priority = 97, id = "torch") {
        val supportsLevel: Boolean
            get() = level >= 0 && maxLevel > 1
    }

    data class Vpn(val isBranded: Boolean = false, val isValidated: Boolean = false) :
        IslandEvent(priority = 35, id = "vpn")

    data class ClipboardItem(
        val id: Long,
        val preview: String,
        val label: String = "",
        val isUrl: Boolean = false,
        val isImage: Boolean = false,
        val imageUri: Uri? = null,
        val timestamp: Long = System.currentTimeMillis(),
    )

    data class Clipboard(
        val label: String = "",
        val preview: String = "",
        val isUrl: Boolean = false,
        val isImage: Boolean = false,
        val imageUri: Uri? = null,
        val items: List<ClipboardItem> = emptyList(),
    ) : IslandEvent(priority = 25, id = "clipboard")

    data class PromotedOngoing(
        val shortText: String = "",
        val title: String = "",
        val text: String = "",
        val appName: String = "",
        val appIcon: Drawable? = null,
        val sbn: StatusBarNotification,
        val actions: List<NotificationAction> = emptyList(),
        val progress: Float = -1f,
        val isIndeterminate: Boolean = false,
    ) : IslandEvent(priority = 72, id = "promoted_${sbn.key}") {
        override fun withoutDrawables() = copy(appIcon = null)
    }

    data class Sports(
        val team1Name: String,
        val team2Name: String,
        val score1: String,
        val score2: String,
        val team1Icon: Drawable? = null,
        val team2Icon: Drawable? = null,
        val status: GameStatus = GameStatus.LIVE,
        val statusDetail: String = "",
        val league: String = "",
        val commentary: String = "",
        val key: String,
        val sbn: StatusBarNotification? = null,
        val appIcon: Drawable? = null,
    ) : IslandEvent(priority = 73, id = "sports_$key") {
        override fun withoutDrawables() = copy(team1Icon = null, team2Icon = null, appIcon = null)
    }

    enum class GameStatus { PRE_GAME, LIVE, HALFTIME, FINAL }

    data class NowPlaying(
        val songTitle: String,
        val artist: String,
        val key: String,
        val sbn: StatusBarNotification? = null,
        val appIcon: Drawable? = null,
        /** Album cover when available (notif largeIcon or metadata fallback). */
        val albumArt: Drawable? = null,
        val actions: List<NotificationAction> = emptyList(),
    ) : IslandEvent(priority = 42, id = "now_playing") {
        override fun withoutDrawables() = copy(appIcon = null, albumArt = null)
    }

    data class Timer(
        val label: String = "",
        val endTimeMs: Long = 0L,
        val originalDurationMs: Long = 0L,
        val appIcon: Drawable? = null,
        val isPaused: Boolean = false,
        /**
         * Khi paused: remaining dong bang (ms). Khong tinh endTimeMs - now — moi lan
         * recomposition/doi pill se bi tru thoi gian wall-clock da troi.
         */
        val pausedRemainingMs: Long = 0L,
        /**
         * Moc het gio theo elapsedRealtime khi dang chay (0 = dung endTimeMs wall).
         * Giong Chronometer.Running (QS chip) — tranh lech khi wall-clock nhay.
         */
        val endElapsedRealtimeMs: Long = 0L,
        val actions: List<NotificationAction> = emptyList(),
        /** Key notification — moi timer mot id rieng (ho tro nhieu dong ho). */
        val notificationKey: String = "",
        /** Mo Clock khi tap noi dung panel (khong phai nut action). */
        val contentIntent: PendingIntent? = null,
        val packageName: String = "",
        val sbn: StatusBarNotification? = null,
        /**
         * Id timer trong Google/AOSP Clock (`TIMER_ID`). Dung de tong hop PAUSE/START
         * khi notif nhom chi con action "Dat lai ca hai…" (khong co nut tung timer).
         * -1 = chua biet.
         */
        val deskClockTimerId: Int = -1,
        /**
         * PendingIntent do Clock tao (PAUSE/START + TIMER_ID). Uu tien PI nay;
         * fallback sendBroadcast can AMS BroadcastSkipPolicy (TimerReceiver exported=false).
         */
        val pausePendingIntent: PendingIntent? = null,
        val startPendingIntent: PendingIntent? = null,
    ) : IslandEvent(
        priority = 45,
        id = if (notificationKey.isNotEmpty()) "timer_$notificationKey" else "timer",
    ) {
        // Ongoing countdown — khong autoDismiss (chip phai con khi timer chay)
        override val behavior = EventBehavior(autoDismissMs = null)
        override fun withoutDrawables() = copy(appIcon = null)

        /**
         * Remaining hien thi (SSOT). Paused: chi pausedRemainingMs — khong ± now.
         * Dang chay: uu tien endElapsedRealtimeMs, fallback endTimeMs wall.
         */
        fun displayedRemainingMs(
            nowWallMs: Long = System.currentTimeMillis(),
            nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
        ): Long =
            when {
                isPaused -> pausedRemainingMs.coerceAtLeast(0L)
                endElapsedRealtimeMs > 0L ->
                    (endElapsedRealtimeMs - nowElapsedMs).coerceAtLeast(0L)
                endTimeMs > 0L -> (endTimeMs - nowWallMs).coerceAtLeast(0L)
                else -> 0L
            }
    }

    data class Stopwatch(
        val label: String = "",
        val startTimeMs: Long = System.currentTimeMillis(),
        /** Moc bat dau elapsedRealtime khi dang chay (0 = dung startTimeMs wall). */
        val startElapsedRealtimeMs: Long = 0L,
        val isRunning: Boolean = true,
        /**
         * Khi paused: elapsed dong bang (ms). Khong tinh now - start — doi pill se cong
         * them thoi gian wall-clock da troi.
         */
        val pausedElapsedMs: Long = 0L,
        /**
         * Cac moc vong (lap) theo elapsed tuyet doi (ms tu luc start).
         * Parse tu EXTRA_TEXT_LINES / BigText cua Clock — rong neu khong co.
         */
        val laps: List<Long> = emptyList(),
        val appIcon: Drawable? = null,
        val actions: List<NotificationAction> = emptyList(),
        val contentIntent: PendingIntent? = null,
        val packageName: String = "",
        val notificationKey: String = "",
        val sbn: StatusBarNotification? = null,
    ) : IslandEvent(priority = 44, id = "stopwatch") {
        // Ongoing stopwatch — khong autoDismiss
        override val behavior = EventBehavior(autoDismissMs = null)
        override fun withoutDrawables() = copy(appIcon = null)

        /**
         * Elapsed hien thi (SSOT) — cung cong thuc Chronometer.Running/Paused (QS chip).
         * Paused: chi pausedElapsedMs. Dang chay: ER base neu co; Instant wall → doi sang ER
         * (elapsedRealtime + (zeroWall - nowWall)) de khong lech khi wall-clock nhay.
         */
        fun displayedElapsedMs(
            nowWallMs: Long = System.currentTimeMillis(),
            nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
        ): Long =
            when {
                !isRunning -> pausedElapsedMs.coerceAtLeast(0L)
                startElapsedRealtimeMs != 0L ->
                    (nowElapsedMs - startElapsedRealtimeMs).coerceAtLeast(0L)
                startTimeMs > 0L -> {
                    // EventTime.ClockTime.asElapsedRealtime
                    val zeroEr = nowElapsedMs + (startTimeMs - nowWallMs)
                    (nowElapsedMs - zeroEr).coerceAtLeast(0L)
                }
                else -> 0L
            }
    }

    data class Alarm(
        val label: String = "",
        val triggerTimeMs: Long = 0L,
        val isRinging: Boolean = false,
        val appIcon: Drawable? = null,
    ) : IslandEvent(priority = 48, id = "alarm") {
        override val behavior = EventBehavior(autoDismissMs = 5000L)
        override fun withoutDrawables() = copy(appIcon = null)
    }

    data class AppSwitch(
        val recentApps: List<RecentApp> = emptyList(),
        val previousApp: RecentApp? = null,
    ) : IslandEvent(priority = 10, id = "app_switch") {
        override val behavior = EventBehavior(autoShowsIsland = false)
        override fun withoutDrawables() = copy(
            recentApps = recentApps.map { it.copy(appIcon = null) },
            previousApp = previousApp?.copy(appIcon = null),
        )
    }

    data class KeyguardIndication(
        val text: String,
        val indicationType: IndicationType,
    ) : IslandEvent(priority = 5, id = "kg_indication_${indicationType.name}") {
        override val behavior: EventBehavior
            get() = when (indicationType) {
                IndicationType.BIOMETRIC -> EventBehavior(autoDismissMs = 3500L)
                IndicationType.TRANSIENT -> EventBehavior(autoDismissMs = 3500L)
                IndicationType.TRUST -> DEFAULT_BEHAVIOR
                IndicationType.DISCLOSURE -> DEFAULT_BEHAVIOR
                IndicationType.OWNER_INFO -> DEFAULT_BEHAVIOR
                IndicationType.ALIGNMENT -> EventBehavior(autoDismissMs = 5000L)
                IndicationType.PERSISTENT_UNLOCK -> DEFAULT_BEHAVIOR
            }

        enum class IndicationType {
            BIOMETRIC,
            TRANSIENT,
            TRUST,
            DISCLOSURE,
            OWNER_INFO,
            ALIGNMENT,
            PERSISTENT_UNLOCK,
        }
    }

    data class RecentApp(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable? = null,
        val taskId: Int = -1,
        val lastActiveTime: Long = System.currentTimeMillis(),
    )

    data class NotificationAction(val label: CharSequence, val action: Notification.Action)

    data class ReplyAction(
        val label: CharSequence,
        val action: Notification.Action,
        val remoteInput: RemoteInput,
    )

    data class MediaCustomAction(
        val label: String,
        val action: String,
        val icon: Drawable? = null,
    )

    data class Call(
        val sbn: StatusBarNotification,
        val callerName: String? = null,
        val number: String? = null,
        val appIcon: Drawable? = null,
        val callerPhoto: Drawable? = null,
        val callType: String = "Phone:incoming",
        val callStartTimeMs: Long = System.currentTimeMillis(),
        val actions: List<NotificationAction> = emptyList(),
    ) : IslandEvent(priority = 100, id = "call_${sbn.key}") {
        override fun withoutDrawables() = copy(appIcon = null, callerPhoto = null)
    }

    data class Notification(
        val sbn: StatusBarNotification,
        val title: String? = null,
        val text: String? = null,
        val appIcon: Drawable? = null,
        val appName: String = "",
        val progress: Int = -1,
        val progressMax: Int = 100,
        val isProgressIndeterminate: Boolean = false,
        val actions: List<NotificationAction> = emptyList(),
        val replyAction: ReplyAction? = null,
        val isConversation: Boolean = false,
        val isGroupConversation: Boolean = false,
        val conversationTitle: String? = null,
        val senderIcon: Drawable? = null,
        val senderName: String? = null,
        val groupKey: String? = null,
        val isGroupSummary: Boolean = false,
        val notificationImage: Drawable? = null,
        val callStartTimeMs: Long = 0L,
        val createdAt: Long = System.currentTimeMillis(),
    ) : IslandEvent(priority = NOTIFICATION_STALE_PRIORITY, id = "notification_${sbn.key}") {
        override val priority: Int
            get() =
                if (System.currentTimeMillis() - createdAt < NOTIFICATION_DECAY_MS)
                    NOTIFICATION_FRESH_PRIORITY
                else NOTIFICATION_STALE_PRIORITY
    }

    companion object {
        internal val DEFAULT_BEHAVIOR = EventBehavior()

        val ONGOING_TYPES: Set<Class<out IslandEvent>> =
            setOf(
                AudioRecording::class.java,
                PromotedOngoing::class.java,
                Sports::class.java,
                Media::class.java,
                Call::class.java,
                Timer::class.java,
                Stopwatch::class.java,
                AospChip::class.java,
            )
    }

    val isOngoing: Boolean
        get() = this::class.java in ONGOING_TYPES

    override fun compareTo(other: IslandEvent): Int = other.priority - this.priority
}

enum class IslandState {
    HIDDEN,
    CHIP,
}

internal fun priorityForAospChipKey(key: String): Int = when {
    key.startsWith("callChip-") -> 88
    key == "ScreenRecord" -> 90
    key == "ShareToApp" -> 86
    key == "CastToOtherDevice" -> 82
    else -> 70
}
