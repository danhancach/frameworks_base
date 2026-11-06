package com.android.systemui.axdynamicbar.data.source

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.Intent
import com.android.systemui.res.R
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.RecordingState
import com.android.systemui.axdynamicbar.shared.DESK_CLOCK_REASON_UNKNOWN
import com.android.systemui.axdynamicbar.shared.DeskClockNotifKind
import com.android.systemui.axdynamicbar.shared.DeskClockNotifRemovalDecision
import com.android.systemui.axdynamicbar.shared.DeskClockOwnerGapEffect
import com.android.systemui.axdynamicbar.shared.DeskClockOwnerGapRetention
import com.android.systemui.axdynamicbar.shared.DeskClockPostedDisposition
import com.android.systemui.axdynamicbar.shared.DeskClockStopwatchRunRelation
import com.android.systemui.axdynamicbar.shared.DeskClockStopwatchVisualDismissFgEffect
import com.android.systemui.axdynamicbar.shared.DeskClockStopwatchVisualDismissGate
import com.android.systemui.axdynamicbar.shared.classifyDeskClockPosted
import com.android.systemui.axdynamicbar.shared.deskClockEventToken
import com.android.systemui.axdynamicbar.shared.deskClockNotifRemovalDecision
import com.android.systemui.axdynamicbar.shared.deskClockStopwatchApplyPostToVisualDismissGate
import com.android.systemui.axdynamicbar.shared.deskClockStopwatchRunIdentity
import com.android.systemui.axdynamicbar.shared.deskClockStopwatchVisualDismissGateOf
import com.android.systemui.axdynamicbar.shared.deskClockStopwatchVisualDismissOnForegroundChanged
import com.android.systemui.axdynamicbar.shared.islandEventOwnerPackages
import com.android.systemui.axdynamicbar.shared.statusBarAppPillLogClockNotif
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.util.ScrimUtils
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.withContext

@SysUISingleton
class NotificationIslandManager
@Inject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    private val notifCollectionLazy: dagger.Lazy<CommonNotifCollection>,
) {
    companion object {
        private const val TAG = "NotificationIslandManager"

        private const val RECORDER_REMOVAL_GRACE_MS = 3_000L

        private val CLOCK_PACKAGES = setOf("com.google.android.deskclock", "com.android.deskclock")
        private val ALARM_PACKAGES = setOf("com.google.android.deskclock", "com.android.deskclock")

        private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
        private val SPORTS_PACKAGES = setOf(
            "com.espn.score_center",
            "com.fivemobile.thescore",
            "com.yahoo.mobile.client.android.sportacular",
            "com.sofascore.app",
            "com.fotmob",
            "com.foxsports.android",
            "com.cbs.sports.android",
            "com.bleacherreport.android.teamstream",
            "com.nbaimd.gametime.nba2011",
            "com.nfl.fantasy.core",
            "com.mlb.atbat",
            "com.nhl.gc1112.free",
            "livescore.livesportsmedia.com",
            "com.flashscore.en",
            "com.365scores.android",
            "lunosoftware.sportsalerts",
            "com.onefootball.brasil",
        )

        private val SCORE_PATTERN =
            Regex("""(.+?)\s+(\d+)\s*[-–—:]\s*(\d+)\s+(.+)""")
        private val VS_PATTERN =
            Regex("""(.+?)\s+(?:vs\.?|v\.?|at)\s+(.+)""", RegexOption.IGNORE_CASE)
        /** mm:ss hoac h:mm:ss trong text / sortKey timer notification. */
        private val TIMER_REMAINING_PATTERN =
            Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?::(\d{2}))?(?![\d:])""")
        /** Google Clock MetricStyle sortKey: ⏳H:MM:SS / ⏳M:SS remaining. */
        private val SORTKEY_REMAINING_PATTERN =
            Regex("""[⏳⏱🕐]?(\d{1,2}):(\d{2})(?::(\d{2}))?""")
        /** Lap stopwatch: H:MM:SS(.d)? / M:SS(.dd)? trong TEXT_LINES. */
        private val STOPWATCH_LAP_PATTERN =
            Regex("""(?<![\d:])(\d{1,2}):(\d{2})(?::(\d{2}))?(?:[.,](\d{1,3}))?(?![\d:])""")
        private const val EXTRA_TIMER_ID = "com.android.deskclock.extra.TIMER_ID"
        private const val EXTRA_TIMER_IDS = "com.android.deskclock.extra.TIMER_IDS"
        /** clock-app://…/timer/4/view */
        private val TIMER_URI_ID_PATTERN = Regex("""/timer/(\d+)(?:/|$)""")
        /** groupKey …|g:4 hoac group g:4 — khong bat so uid/prefix. */
        private val TIMER_GROUP_ID_PATTERN = Regex("""(?:\|g:|^g:)(\d+)$""")
        /** Prefs: TIMER_ID da thay + remaining gan nhat (survive SystemUI restart). */
        private const val PREFS_DESKCLOCK = "axdb_deskclock_timers"
        private const val PREFS_KNOWN_IDS = "known_ids"
        private const val PREFS_REMAINING_PREFIX = "rem_"
        /** Sai so khop remaining khi pause/resume / tick. */
        private const val TIMER_REMAINING_MATCH_MS = 3_000L

        private const val NOW_PLAYING_PACKAGE = "com.google.android.as"
        private const val NOW_PLAYING_CHANNEL = "ambientmusic"
        /** 26Q1 standalone Pixel Now Playing app. */
        private const val NOW_PLAYING_APP_PACKAGE = "com.google.android.apps.pixel.nowplaying"
        /** ASI often omits largeIcon on non-Pixel; resolve cover by title+artist. */
        private const val ITUNES_SEARCH_URL =
            "https://itunes.apple.com/search?term=%s&media=music&entity=song&limit=1"
        private const val ART_HTTP_CONNECT_MS = 4000
        private const val ART_HTTP_READ_MS = 6000

        private val RECORDER_PACKAGES =
            setOf("com.google.android.apps.recorder", "com.android.soundrecorder")

        private val SUPPRESSED_PACKAGES: Set<String> = buildSet {
            addAll(CLOCK_PACKAGES)
            addAll(ALARM_PACKAGES)
            addAll(RECORDER_PACKAGES)
        }
    }

    private val _timerEvents = MutableStateFlow<List<IslandEvent.Timer>>(emptyList())
    val timerEvents: StateFlow<List<IslandEvent.Timer>> = _timerEvents.asStateFlow()

    private val _stopwatchEvent = MutableStateFlow<IslandEvent.Stopwatch?>(null)
    val stopwatchEvent: StateFlow<IslandEvent.Stopwatch?> = _stopwatchEvent.asStateFlow()

    private val _alarmEvent = MutableStateFlow<IslandEvent.Alarm?>(null)
    val alarmEvent: StateFlow<IslandEvent.Alarm?> = _alarmEvent.asStateFlow()

    private val _notificationEvents = MutableStateFlow<List<IslandEvent.Notification>>(emptyList())
    val notificationEvents: StateFlow<List<IslandEvent.Notification>> =
        _notificationEvents.asStateFlow()

    private val _audioRecordingEvent = MutableStateFlow<IslandEvent.AudioRecording?>(null)
    val audioRecordingEvent: StateFlow<IslandEvent.AudioRecording?> =
        _audioRecordingEvent.asStateFlow()

    private val _promotedOngoingEvents =
        MutableStateFlow<List<IslandEvent.PromotedOngoing>>(emptyList())
    val promotedOngoingEvents: StateFlow<List<IslandEvent.PromotedOngoing>> =
        _promotedOngoingEvents.asStateFlow()

    private val _sportsEvents = MutableStateFlow<List<IslandEvent.Sports>>(emptyList())
    val sportsEvents: StateFlow<List<IslandEvent.Sports>> = _sportsEvents.asStateFlow()

    private val _nowPlayingEvent = MutableStateFlow<IslandEvent.NowPlaying?>(null)
    val nowPlayingEvent: StateFlow<IslandEvent.NowPlaying?> = _nowPlayingEvent.asStateFlow()

    private val _callEvents = MutableStateFlow<List<IslandEvent.Call>>(emptyList())
    val callEvents: StateFlow<List<IslandEvent.Call>> = _callEvents.asStateFlow()

    @Volatile var disabledTypes: Set<String> = emptySet()

    private var recorderPackage: String? = null

    private var recorderNotifKey: String? = null

    private var pauseStartMs: Long = 0L

    private var accumulatedPauseMs: Long = 0L

    private var recorderGraceJob: Job? = null
    private var recording: IslandEvent.AudioRecording? = null
    private var recordingDismissed = false
    /**
     * Visual swipe-hide stopwatch (khong stop Clock that). Stateful gate —
     * rejected posts cap nhat latestObserved (pause/resume continuation).
     */
    private var stopwatchVisualDismissGate: DeskClockStopwatchVisualDismissGate? = null
    private var stopwatchDismissedPackage: String? = null
    private val stopwatchDismissedKey: String?
        get() = stopwatchVisualDismissGate?.notificationKey
    private val stopwatchAwaitOwnerRevisit: Boolean
        get() = stopwatchVisualDismissGate?.awaitOwnerRevisit == true
    private var stopwatchOwnerRevisitArmed: Boolean
        get() = stopwatchVisualDismissGate?.ownerRevisitArmed == true
        set(value) {
            val g = stopwatchVisualDismissGate ?: return
            stopwatchVisualDismissGate = g.copy(ownerRevisitArmed = value)
        }
    private val dismissedPromotedKeys = mutableSetOf<String>()
    private val dismissedSportsKeys = mutableSetOf<String>()
    private var alarmNotificationKey: String? = null

    val notificationFlow = MutableSharedFlow<IslandEvent.Notification>(extraBufferCapacity = 16)
    val notificationRemovedFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    var activeMediaPackageProvider: (() -> Set<String>)? = null

    /**
     * FG package hien tai (AppTracking) — dung policy owner-gap Clock.
     * Interactor gan khi init; null = coi nhu khong owner FG.
     */
    var foregroundPackageProvider: (() -> String?)? = null

    private val seenSignatures = mutableMapOf<String, Long>()

    /**
     * Retention SSOT — absolute deadline + generation (test inject clock qua
     * DeskClockOwnerGapRetention rieng; production dung instance nay).
     */
    private val deskClockOwnerGapRetention = DeskClockOwnerGapRetention()

    /** Stopwatch dang giu qua owner-gap (notif vang tam). */
    private var stopwatchOwnerGapKey: String? = null
    private var stopwatchGapJob: Job? = null
    /** Timer keys dang giu qua owner-gap. */
    private val timerOwnerGapKeys = ConcurrentHashMap.newKeySet<String>()
    private val timerGapJobs = ConcurrentHashMap<String, Job>()

    var onTimerEvent: ((IslandEvent.Timer) -> Unit)? = null
    var onAlarmEvent: ((IslandEvent.Alarm) -> Unit)? = null
    var onNotificationPosted: ((IslandEvent.Notification) -> Unit)? = null

    @Volatile private var listening = false
    /** Job het han theo tung notification key. */
    private val timerJobs = mutableMapOf<String, Job>()
    /** originalDurationMs theo key — giu khi update cung timer. */
    private val timerOriginalDurations = mutableMapOf<String, Long>()
    /** TIMER_ID da thay theo notification key (giu khi doi 1→nhieu timer). */
    private val deskClockTimerIdsByNotif = mutableMapOf<String, LinkedHashSet<Int>>()
    /**
     * Remaining ms gan nhat theo TIMER_ID — khop metric khi thu tu metrics doi
     * (critical / dang chay len dau) sau Pause→Resume.
     */
    private val deskClockLastRemainingById = ConcurrentHashMap<Int, Long>()
    /**
     * PendingIntent PAUSE/START do Clock tao theo TIMER_ID — giu khi notif doi sang multi
     * (chi con RESET nhom). SystemUI khong tu tao PI cho receiver exported=false.
     */
    private val deskClockPausePiById = ConcurrentHashMap<Int, android.app.PendingIntent>()
    private val deskClockStartPiById = ConcurrentHashMap<Int, android.app.PendingIntent>()
    private var stopwatchNotificationKey: String? = null
    private val deskClockPrefs by lazy {
        context.getSharedPreferences(PREFS_DESKCLOCK, Context.MODE_PRIVATE)
    }

    /** Bo nho dem SBN theo notification key de the mo rong co the inflate layout goc. */
    private val activeSbns = ConcurrentHashMap<String, StatusBarNotification>()
    private val _activeNotifications = MutableStateFlow<Map<String, StatusBarNotification>>(emptyMap())
    val activeNotifications: StateFlow<Map<String, StatusBarNotification>> = _activeNotifications.asStateFlow()

    fun getSbn(key: String): StatusBarNotification? {
        return try {
            notifCollectionLazy.get().getEntry(key)?.sbn ?: activeSbns[key]
        } catch (_: Exception) {
            activeSbns[key]
        }
    }

    private val scrimListener =
        object : ScrimUtils.ScrimEventListener {
            override fun onNotificationRemoved(sbn: StatusBarNotification) {
                handleNotificationRemoved(sbn, DESK_CLOCK_REASON_UNKNOWN)
            }

            override fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
                handleNotificationRemoved(sbn, reason)
            }

            override fun onUserChanged() {
                // User switch — drop moi owner-gap retained (khong ghost cross-user).
                dropAllDeskClockOwnerGaps(cause = "user_changed")
            }

            override fun onNotificationPosted(sbn: StatusBarNotification) {
                activeSbns[sbn.key] = sbn
                _activeNotifications.update { it + (sbn.key to sbn) }
                val pkg = sbn.packageName
                // pkg null: chi reconcile neu dang track gap cung key (khong biet CLOCK).
                if (pkg == null) {
                    if (hasTrackedDeskClockSameKeyGap(sbn.key)) {
                        reconcileDeskClockNonMatchingPost(sbn.key)
                    }
                    return
                }

                if (pkg in CLOCK_PACKAGES) {
                    val clockFlags = sbn.notification?.flags ?: 0
                    val isGroupSummary =
                        clockFlags and Notification.FLAG_GROUP_SUMMARY != 0
                    val extras = sbn.notification?.extras
                    val hasGap = hasTrackedDeskClockSameKeyGap(sbn.key)
                    val prevKind = trackedDeskClockKindForKey(sbn.key)

                    // Summary / extras null: classify truoc early-return (P3).
                    if (isGroupSummary || extras == null) {
                        val early =
                            classifyDeskClockPosted(
                                hasTrackedSameKeyGap = hasGap,
                                previousTrackedKind = prevKind,
                                isGroupSummary = isGroupSummary,
                                extrasAvailable = extras != null,
                                classifiedKind = DeskClockNotifKind.NONE,
                                typeEnabled = true,
                            )
                        if (early == DeskClockPostedDisposition.RECONCILE_DROP_RETAINED) {
                            reconcileDeskClockNonMatchingPost(sbn.key)
                        }
                        return
                    }

                    val channelId = sbn.notification?.channelId?.lowercase() ?: ""
                    val actionLabels =
                        sbn.notification?.actions?.map { it.title?.toString()?.lowercase() ?: "" }
                            ?: emptyList()
                    val actionIntents =
                        sbn.notification?.actions?.map { actionIntentString(it) ?: "" }
                            ?: emptyList()
                    val hasStopwatchIntent = actionIntents.any {
                        DeskClockActions.isStopwatchAction(it)
                    }
                    val hasTimerIntent = actionIntents.any {
                        DeskClockActions.isTimerAction(it)
                    }
                    // EN + VI: Lap / Vong — PendingIntent action thuong null tren Clock moi
                    val hasLap =
                        actionLabels.any {
                            it.contains("lap") || it.contains("vòng") || it.contains("vong")
                        }
                    val isCountDown = extras.getBoolean("android.chronometerCountDown", false)

                    val isStopwatch =
                        hasStopwatchIntent || channelId.contains("stopwatch") || hasLap
                    val isTimer =
                        !isStopwatch &&
                            (hasTimerIntent ||
                                channelId.contains("timer") ||
                                channelId.contains("firing") ||
                                isCountDown ||
                                actionLabels.any { it.contains("+1") || it.contains("add") })

                    val classifiedKind =
                        when {
                            isStopwatch -> DeskClockNotifKind.STOPWATCH
                            isTimer -> DeskClockNotifKind.TIMER
                            else -> DeskClockNotifKind.NONE
                        }
                    val typeEnabled =
                        when (classifiedKind) {
                            DeskClockNotifKind.STOPWATCH -> "stopwatch" !in disabledTypes
                            DeskClockNotifKind.TIMER -> "timer" !in disabledTypes
                            DeskClockNotifKind.NONE -> true
                        }
                    val disposition =
                        classifyDeskClockPosted(
                            hasTrackedSameKeyGap = hasGap,
                            previousTrackedKind = prevKind,
                            isGroupSummary = false,
                            extrasAvailable = true,
                            classifiedKind = classifiedKind,
                            typeEnabled = typeEnabled,
                        )
                    when (disposition) {
                        DeskClockPostedDisposition.HANDLE_VALIDATED -> {
                            when (classifiedKind) {
                                DeskClockNotifKind.STOPWATCH ->
                                    handleStopwatch(sbn, extras, actionLabels, actionIntents)
                                DeskClockNotifKind.TIMER ->
                                    handleTimer(sbn, extras, actionLabels, actionIntents)
                                DeskClockNotifKind.NONE -> Unit
                            }
                            if (classifiedKind != DeskClockNotifKind.NONE) return
                        }
                        DeskClockPostedDisposition.CROSS_TYPE_DROP_THEN_HANDLE -> {
                            // Drop kind cu + clear retention truoc rebind kind moi.
                            dropTrackedDeskClockKindBeforeCrossType(sbn.key, prevKind)
                            when (classifiedKind) {
                                DeskClockNotifKind.STOPWATCH ->
                                    handleStopwatch(sbn, extras, actionLabels, actionIntents)
                                DeskClockNotifKind.TIMER ->
                                    handleTimer(sbn, extras, actionLabels, actionIntents)
                                DeskClockNotifKind.NONE -> Unit
                            }
                            if (classifiedKind != DeskClockNotifKind.NONE) return
                        }
                        DeskClockPostedDisposition.RECONCILE_DROP_RETAINED -> {
                            reconcileDeskClockNonMatchingPost(sbn.key)
                            if (classifiedKind != DeskClockNotifKind.NONE) return
                        }
                        DeskClockPostedDisposition.IGNORE -> {
                            if (classifiedKind != DeskClockNotifKind.NONE) return
                        }
                    }
                }

                val extras = sbn.notification?.extras ?: return

                val isAlarmCategory = sbn.notification?.category == Notification.CATEGORY_ALARM
                if ((isAlarmCategory || pkg in ALARM_PACKAGES) && sbn.isOngoing) {
                    if ("alarm" !in disabledTypes) handleAlarm(sbn, extras)
                    return
                }

                if (sbn.notification?.category == Notification.CATEGORY_CALL) {
                    val isCallStyle =
                        extras.containsKey(Notification.EXTRA_ANSWER_INTENT) ||
                            extras.containsKey(Notification.EXTRA_DECLINE_INTENT) ||
                            extras.containsKey(Notification.EXTRA_HANG_UP_INTENT)
                    if (isCallStyle) {
                        if ("call" !in disabledTypes) handleCallNotification(sbn, extras)
                        return
                    }
                }

                val allActions = sbn.notification?.actions ?: emptyArray()
                val isMedia = sbn.notification?.category == Notification.CATEGORY_TRANSPORT ||
                    sbn.notification?.extras?.containsKey(
                        Notification.EXTRA_MEDIA_SESSION
                    ) == true
                if (sbn.isOngoing && !isMedia && "audio_recording" !in disabledTypes) {
                    val actionIntentMap = allActions.associateWith { actionIntentString(it) }
                    val iconResMap = allActions.associateWith { actionIconResName(it, pkg)?.lowercase() }
                    val recActions = allActions.filter { a ->
                        val intent = actionIntentMap[a]
                        val icon = iconResMap[a] ?: ""
                        when {
                            intent == "STOP" || intent == "PAUSE" || intent == "RESUME" -> true
                            icon.matchesMaterial(MaterialIconSet.Stop) -> true
                            icon.matchesMaterial(MaterialIconSet.Pause) -> true
                            icon.matchesMaterial(MaterialIconSet.Play) -> true
                            else -> {
                                val lbl = a.title?.toString()?.lowercase() ?: ""
                                lbl.contains("stop") || lbl.contains("pause") || lbl.contains("resume")
                            }
                        }
                    }
                    val hasStop = recActions.any { a ->
                        actionIntentMap[a] == "STOP" ||
                            (iconResMap[a] ?: "").matchesMaterial(MaterialIconSet.Stop) ||
                            (a.title?.toString()?.lowercase() ?: "").contains("stop")
                    }
                    if (hasStop && recActions.size >= 2) {
                        val hasResumeAction = recActions.any { actionIntentMap[it] == "RESUME" }
                        val hasPauseAction = recActions.any { actionIntentMap[it] == "PAUSE" }
                        val isPaused = when {
                            hasResumeAction -> true
                            hasPauseAction -> false
                            else -> recActions.any { a ->
                                (iconResMap[a] ?: "").matchesMaterial(MaterialIconSet.Play) ||
                                    (a.title?.toString()?.lowercase() ?: "").contains("resume")
                            }
                        }
                        val notifActions =
                            recActions.mapNotNull { a ->
                                a.title?.let {
                                    IslandEvent.NotificationAction(label = it, action = a)
                                }
                            }
                        val appName = resolveAppName(pkg)
                        val existing = recording
                        val now = System.currentTimeMillis()
                        val parsedElapsedMs = parseRecorderElapsedMs(extras)

                        val startTime = when {
                            parsedElapsedMs != null -> now - parsedElapsedMs
                            existing != null && existing.state != RecordingState.SAVED &&
                                recorderNotifKey == sbn.key -> existing.startTimeMs
                            sbn.notification?.extras?.getBoolean("android.showChronometer") == true ->
                                sbn.notification.`when`
                            else -> now
                        }

                        accumulatedPauseMs = 0L
                        pauseStartMs = 0L

                        recorderGraceJob?.cancel()
                        recorderGraceJob = null
                        recorderPackage = pkg
                        recorderNotifKey = sbn.key
                        publishRecording(
                            IslandEvent.AudioRecording(
                                appName = appName,
                                state =
                                    if (isPaused) RecordingState.PAUSED
                                    else RecordingState.RECORDING,
                                startTimeMs = startTime,
                                actions = notifActions,
                                pausedDurationMs = 0L,
                            )
                        )
                        return
                    }
                }

                if (
                    pkg == recorderPackage && recording != null && !sbn.isOngoing
                ) {
                    val notifActions =
                        allActions.mapNotNull { a ->
                            a.title?.let { IslandEvent.NotificationAction(label = it, action = a) }
                        }
                    val title = extras.getString("android.title") ?: ""
                    val existing = recording ?: return
                    recorderGraceJob?.cancel()
                    recorderGraceJob = null
                    recorderNotifKey = sbn.key
                    if (existing.state != RecordingState.SAVED) recordingDismissed = false
                    publishRecording(
                        existing.copy(
                            appName = title.ifEmpty { existing.appName },
                            state = RecordingState.SAVED,
                            actions = notifActions,
                        )
                    )
                    return
                }

                if (pkg == NOW_PLAYING_PACKAGE || pkg == NOW_PLAYING_APP_PACKAGE) {
                    val channel = sbn.notification?.channelId ?: ""
                    // ASI uses ambientmusic channel; standalone NP app may use other channels.
                    if (pkg == NOW_PLAYING_APP_PACKAGE
                            || channel.contains(NOW_PLAYING_CHANNEL)
                            || channel.contains("now_playing", ignoreCase = true)) {
                        if ("now_playing" !in disabledTypes) handleNowPlaying(sbn, extras)
                        return
                    }
                }

                if (pkg in SUPPRESSED_PACKAGES) return

                if ("sports" !in disabledTypes) {
                    if (pkg == GOOGLE_PACKAGE) {
                        val groupKey = sbn.groupKey ?: ""
                        val isSportsGroup = groupKey.contains("::sports", ignoreCase = true)
                        if (isSportsGroup && handleSportsScore(sbn, extras, forceCapture = true)) return
                    }
                    if (pkg in SPORTS_PACKAGES && handleSportsScore(sbn, extras, forceCapture = true)) return
                }

                if (sbn.isOngoing && isPromotable(sbn, extras)) {
                    if ("promoted_ongoing" !in disabledTypes) handlePromotedOngoing(sbn, extras, pkg)
                    return
                }

                val indeterminate = extras.getBoolean("android.progressIndeterminate", false)
                val progressRaw = extras.getInt("android.progress", -1)
                val progressMax = extras.getInt("android.progressMax", 0)
                val hasProgress =
                    indeterminate ||
                        (extras.containsKey("android.progress") &&
                            progressMax > 0 &&
                            progressRaw >= 0)

                if (sbn.isOngoing && hasProgress) {
                    if ("promoted_ongoing" !in disabledTypes) handlePromotedOngoing(sbn, extras, pkg)
                    return
                }
                if (!sbn.isOngoing) {
                    dismissedPromotedKeys.remove(sbn.key)
                    _promotedOngoingEvents.value =
                        _promotedOngoingEvents.value.filter { it.sbn.key != sbn.key }
                }
                if (sbn.isOngoing) return
                if ("notification" in disabledTypes) return
                val category = sbn.notification?.category
                if (category == Notification.CATEGORY_TRANSPORT) return
                if (category == Notification.CATEGORY_SERVICE && !hasProgress) return
                if (sbn.packageName in activeMediaPackageProvider?.invoke().orEmpty()) return

                val notifFlags = sbn.notification?.flags ?: 0
                if (notifFlags and Notification.FLAG_GROUP_SUMMARY != 0) return
                val groupKey = if (sbn.isGroup) sbn.groupKey else null

                val title = extras.getString("android.title")
                val text =
                    extras.getCharSequence("android.bigText")?.toString()?.takeIf {
                        it.isNotEmpty()
                    } ?: extras.getString("android.text")

                val notif = sbn.notification
                val isMessagingStyle =
                    notif != null && notif.isStyle(Notification.MessagingStyle::class.java)

                val lastMessage: Notification.MessagingStyle.Message? =
                    if (isMessagingStyle) {
                        notif?.extras
                            ?.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { Notification.MessagingStyle.Message.getMessagesFromBundleArray(it) }
                            ?.maxByOrNull { it.timestamp }
                    } else null

                val signature: Long =
                    if (lastMessage != null) lastMessage.timestamp
                    else contentSignature(sbn, extras)
                val previous = seenSignatures.put(sbn.key, signature)

                if (previous == signature) return

                if (previous != null &&
                    (notif?.flags ?: 0) and Notification.FLAG_ONLY_ALERT_ONCE != 0
                ) return

                if (lastMessage != null && isFromSelf(lastMessage, extras)) return

                val icon =
                    try {
                        context.packageManager.getApplicationIcon(pkg)
                    } catch (_: Exception) {
                        null
                    }
                val appName =
                    try {
                        context.packageManager
                            .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                            .toString()
                    } catch (_: Exception) {
                        pkg
                    }

                val allNotifActions = sbn.notification?.actions ?: emptyArray()

                // App-provided buttons (Mark as read, Copy code, Call, ...). Contextual actions are
                // system-generated smart suggestions, not the app's own buttons.
                val actions =
                    allNotifActions
                        .filter { a ->
                            a.remoteInputs.isNullOrEmpty() && a.actionIntent != null && !a.isContextual
                        }
                        .take(3)
                        .mapNotNull { a ->
                            a.title?.let { IslandEvent.NotificationAction(label = it, action = a) }
                        }

                val replyAction =
                    allNotifActions
                        .firstOrNull { a ->
                            !a.remoteInputs.isNullOrEmpty() && a.actionIntent != null
                        }
                        ?.let { a ->
                            val remoteInput = a.remoteInputs!!.first()
                            IslandEvent.ReplyAction(
                                label = a.title ?: remoteInput.label ?: "Reply",
                                action = a,
                                remoteInput = remoteInput,
                            )
                        }

                var senderIcon: Drawable? = null
                var senderName: String? = null
                var latestMessageText: String? = null
                var isConversation = false
                var isGroupConversation = false
                var conversationTitle: String? = null
                if (
                    notif != null &&
                        isMessagingStyle &&
                        notif.extras != null
                ) {
                    isConversation = true
                    isGroupConversation = notif.extras.getBoolean(
                        Notification.EXTRA_IS_GROUP_CONVERSATION, false
                    )
                    conversationTitle = notif.extras.getCharSequence(
                        Notification.EXTRA_CONVERSATION_TITLE
                    )?.toString()?.takeIf { it.isNotEmpty() }
                    val messagesArray =
                        notif.extras.getParcelableArray(
                            Notification.EXTRA_MESSAGES,
                            Parcelable::class.java,
                        )
                    if (messagesArray != null && messagesArray.isNotEmpty()) {
                        val messages =
                            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                                messagesArray
                            )
                        val lastMessage = messages.maxByOrNull { it.timestamp }
                        val sender = lastMessage?.senderPerson
                        senderName = sender?.name?.toString()
                        latestMessageText = lastMessage?.text?.toString()
                        senderIcon =
                            try {
                                sender?.icon?.loadDrawable(context)
                            } catch (_: Exception) {
                                null
                            }
                    }

                    if (senderIcon == null) {
                        senderIcon =
                            try {
                                notif.extras
                                    .getParcelable(
                                        Notification.EXTRA_CONVERSATION_ICON,
                                        Icon::class.java,
                                    )
                                    ?.loadDrawable(context)
                            } catch (_: Exception) {
                                null
                            }
                    }

                    if (senderIcon == null) {
                        senderIcon =
                            try {
                                notif.getLargeIcon()?.loadDrawable(context)
                            } catch (_: Exception) {
                                null
                            }
                    }
                }

                val notificationImage = extractNotificationImage(extras, sbn)

                val event =
                    IslandEvent.Notification(
                        sbn = sbn,
                        title = title,
                        text = latestMessageText ?: text,
                        appIcon = icon,
                        appName = appName,
                        progress = if (hasProgress && !indeterminate) progressRaw else -1,
                        progressMax = progressMax.coerceAtLeast(1),
                        isProgressIndeterminate = indeterminate,
                        actions = actions,
                        replyAction = replyAction,
                        isConversation = isConversation,
                        isGroupConversation = isGroupConversation,
                        conversationTitle = conversationTitle,
                        senderIcon = senderIcon,
                        senderName = senderName,
                        groupKey = groupKey,
                        notificationImage = notificationImage,
                    )
                applicationScope.launch { notificationFlow.emit(event) }
                onNotificationPosted?.invoke(event)
            }
        }

    private val notifCollectionListener =
        object : NotifCollectionListener {
            override fun onEntryAdded(entry: NotificationEntry) {
                entry.sbn?.let { scrimListener.onNotificationPosted(it) }
            }

            override fun onEntryUpdated(entry: NotificationEntry) {
                entry.sbn?.let { scrimListener.onNotificationPosted(it) }
            }

            override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
                // Collection da co reason — khong drop qua Scrim path khong reason.
                entry.sbn?.let { handleNotificationRemoved(it, reason) }
            }
        }

    /**
     * FG doi (Interactor) — doi max-gap <-> leave-grace cho event dang KEEP.
     * Absolute deadline khong gia han khi flip-flop; chi re-arm phan con lai.
     * Home + event retained → APP_SHOW ngay (khong doi Clock post).
     * Visual-dismiss stopwatch: owner-gap chi khi retained nonnull; restore sau
     * chuoi vao Clock → roi (Home) — khong resurrect moi tick.
     */
    fun onForegroundPackageChanged(foregroundPackage: String?) {
        val sw = _stopwatchEvent.value
        val swKey = stopwatchOwnerGapKey
        if (swKey != null && sw != null) {
            val inOwner =
                !foregroundPackage.isNullOrEmpty() &&
                    foregroundPackage in islandEventOwnerPackages(sw)
            applyDeskClockOwnerGapEffect(
                deskClockOwnerGapRetention.onOwnerForegroundChanged(swKey, inOwner),
                kind = DeskClockGapKind.STOPWATCH,
            )
        } else {
            // Event null sau swipe-hide — owner-gap khong chay; visual-dismiss restore.
            applyStopwatchVisualDismissForeground(foregroundPackage)
        }
        for (key in timerOwnerGapKeys.toList()) {
            val timer =
                _timerEvents.value.firstOrNull {
                    isTimerKeyForNotification(it.notificationKey, key)
                }
            if (timer == null) {
                clearTimerOwnerGap(key)
                continue
            }
            val inOwner =
                !foregroundPackage.isNullOrEmpty() &&
                    foregroundPackage in islandEventOwnerPackages(timer)
            applyDeskClockOwnerGapEffect(
                deskClockOwnerGapRetention.onOwnerForegroundChanged(key, inOwner),
                kind = DeskClockGapKind.TIMER,
            )
        }
    }

    private fun stopwatchVisualDismissOwnerPackages(): Set<String> {
        val pkg = stopwatchDismissedPackage
        return if (!pkg.isNullOrEmpty()) {
            CLOCK_PACKAGES + pkg
        } else {
            CLOCK_PACKAGES
        }
    }

    private fun applyStopwatchVisualDismissForeground(foregroundPackage: String?) {
        when (
            deskClockStopwatchVisualDismissOnForegroundChanged(
                dismissedKey = stopwatchDismissedKey,
                awaitOwnerRevisit = stopwatchAwaitOwnerRevisit,
                ownerRevisitArmed = stopwatchOwnerRevisitArmed,
                foregroundPackage = foregroundPackage,
                ownerPackages = stopwatchVisualDismissOwnerPackages(),
            )
        ) {
            DeskClockStopwatchVisualDismissFgEffect.NONE -> Unit
            DeskClockStopwatchVisualDismissFgEffect.ARM_OWNER_REVISIT -> {
                stopwatchOwnerRevisitArmed = true
            }
            DeskClockStopwatchVisualDismissFgEffect.RESTORE_AFTER_LEAVE -> {
                val key = stopwatchDismissedKey
                clearStopwatchVisualDismissGate()
                if (key != null) {
                    rebindStopwatchFromActiveNotification(key)
                }
            }
        }
    }

    private fun clearStopwatchVisualDismissGate() {
        stopwatchVisualDismissGate = null
        stopwatchDismissedPackage = null
    }

    /** Rebind stopwatch tu SBN ongoing sau deliberate owner revisit / new-run gate clear. */
    private fun rebindStopwatchFromActiveNotification(key: String) {
        val sbn = getSbn(key) ?: return
        val extras = sbn.notification?.extras ?: return
        val actionLabels =
            sbn.notification?.actions?.map { it.title?.toString()?.lowercase() ?: "" }
                ?: emptyList()
        val actionIntents =
            sbn.notification?.actions?.map { actionIntentString(it) ?: "" } ?: emptyList()
        handleStopwatch(sbn, extras, actionLabels, actionIntents)
    }

    private fun currentForegroundPackage(): String? =
        try {
            foregroundPackageProvider?.invoke()
        } catch (_: RuntimeException) {
            null
        }

    private fun handleNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
        activeSbns.remove(sbn.key)
        _activeNotifications.update { it - sbn.key }
        seenSignatures.remove(sbn.key)

        val fg = currentForegroundPackage()

        if (_timerEvents.value.any { isTimerKeyForNotification(it.notificationKey, sbn.key) }) {
            handleTimerNotificationRemoved(sbn, reason, fg)
        }
        if (sbn.key == stopwatchNotificationKey || sbn.key == stopwatchOwnerGapKey) {
            handleStopwatchNotificationRemoved(sbn, reason, fg)
        }

        // Media / non-clock: giu hanh vi cu — clear ngay (khong owner-gap Clock).
        dismissedPromotedKeys.remove(sbn.key)
        dismissedSportsKeys.remove(sbn.key)

        if (sbn.key == recorderNotifKey) {
            recorderNotifKey = null
            val currentState = recording?.state
            if (currentState == RecordingState.SAVED) {
                clearAudioRecording()
            } else if (currentState != null) {
                recorderGraceJob?.cancel()
                recorderGraceJob =
                    applicationScope.launch {
                        delay(RECORDER_REMOVAL_GRACE_MS)
                        if (recorderNotifKey == null &&
                            recording?.state != RecordingState.SAVED
                        ) {
                            clearAudioRecording()
                        }
                    }
            }
        }

        if (sbn.key == alarmNotificationKey) {
            alarmNotificationKey = null
            _alarmEvent.value = null
        }

        _callEvents.value = _callEvents.value.filter { it.sbn.key != sbn.key }

        _promotedOngoingEvents.value =
            _promotedOngoingEvents.value.filter { it.sbn.key != sbn.key }

        _sportsEvents.value = _sportsEvents.value.filter { it.key != sbn.key }

        if (_nowPlayingEvent.value?.key == sbn.key) {
            _nowPlayingEvent.value = null
        }

        _notificationEvents.value =
            _notificationEvents.value.filter { it.sbn.key != sbn.key }

        notificationRemovedFlow.tryEmit(sbn.key)
    }

    private fun handleStopwatchNotificationRemoved(
        sbn: StatusBarNotification,
        reason: Int,
        foregroundPackage: String?,
    ) {
        val existing = _stopwatchEvent.value
        val owners = existing?.let { islandEventOwnerPackages(it) }.orEmpty()
        val ownerAssoc =
            !foregroundPackage.isNullOrEmpty() && foregroundPackage in owners
        val decision = deskClockNotifRemovalDecision(reason, ownerAssoc)
        val tb =
            existing?.let {
                if (it.isRunning) it.startElapsedRealtimeMs else it.pausedElapsedMs
            } ?: 0L
        statusBarAppPillLogClockNotif(
            cause = "remove",
            reason = reason,
            decision =
                if (decision == DeskClockNotifRemovalDecision.KEEP_OWNER_GAP) "keep"
                else "drop",
            ownerAssoc = ownerAssoc,
            eventKind = "stopwatch",
            eventToken = deskClockEventToken(sbn.key),
            timebaseAnchor = tb,
        )
        // DROP thang KEEP tren duplicate Scrim+collection; KEEP trung khong reset deadline.
        val effect =
            deskClockOwnerGapRetention.applyRemovalDecision(
                key = sbn.key,
                decision = decision,
                ownerInForeground = ownerAssoc,
            )
        when (decision) {
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP -> {
                // Retain run data, but require a repost before presenting it again.
                stopwatchOwnerGapKey = sbn.key
                // Key van tro toi event retained (post tre cung key rebind).
                stopwatchNotificationKey = sbn.key
                val current = _stopwatchEvent.value
                if (current != null && !current.awaitingOwnerRepost) {
                    _stopwatchEvent.value = current.copy(awaitingOwnerRepost = true)
                }
                applyDeskClockOwnerGapEffect(effect, DeskClockGapKind.STOPWATCH)
            }
            DeskClockNotifRemovalDecision.DROP_ENDED -> {
                // Chi drop neu key van so huu event hien tai (stale old-key khong xoa moi).
                val currentKey =
                    _stopwatchEvent.value?.notificationKey ?: stopwatchOwnerGapKey
                if (currentKey == null || currentKey == sbn.key) {
                    dropStopwatchEvent(cause = "removal")
                } else {
                    deskClockOwnerGapRetention.clearKey(sbn.key)
                }
            }
        }
    }

    private fun handleTimerNotificationRemoved(
        sbn: StatusBarNotification,
        reason: Int,
        foregroundPackage: String?,
    ) {
        val matched =
            _timerEvents.value.filter { isTimerKeyForNotification(it.notificationKey, sbn.key) }
        if (matched.isEmpty()) return
        val owners = matched.flatMap { islandEventOwnerPackages(it) }.toSet()
        val ownerAssoc =
            !foregroundPackage.isNullOrEmpty() && foregroundPackage in owners
        val decision = deskClockNotifRemovalDecision(reason, ownerAssoc)
        val tb = matched.firstOrNull()?.endElapsedRealtimeMs ?: 0L
        statusBarAppPillLogClockNotif(
            cause = "remove",
            reason = reason,
            decision =
                if (decision == DeskClockNotifRemovalDecision.KEEP_OWNER_GAP) "keep"
                else "drop",
            ownerAssoc = ownerAssoc,
            eventKind = "timer",
            eventToken = deskClockEventToken(sbn.key),
            timebaseAnchor = tb,
        )
        val effect =
            deskClockOwnerGapRetention.applyRemovalDecision(
                key = sbn.key,
                decision = decision,
                ownerInForeground = ownerAssoc,
            )
        when (decision) {
            DeskClockNotifRemovalDecision.KEEP_OWNER_GAP -> {
                timerOwnerGapKeys.add(sbn.key)
                applyDeskClockOwnerGapEffect(effect, DeskClockGapKind.TIMER)
            }
            DeskClockNotifRemovalDecision.DROP_ENDED -> {
                clearTimerOwnerGap(sbn.key)
                removeTimersForNotificationKey(sbn.key)
            }
        }
    }

    private enum class DeskClockGapKind {
        STOPWATCH,
        TIMER,
    }

    private fun applyDeskClockOwnerGapEffect(
        effect: DeskClockOwnerGapEffect,
        kind: DeskClockGapKind,
    ) {
        when (effect) {
            is DeskClockOwnerGapEffect.None -> Unit
            is DeskClockOwnerGapEffect.Drop -> {
                when (kind) {
                    DeskClockGapKind.STOPWATCH -> dropStopwatchEvent(cause = effect.cause)
                    DeskClockGapKind.TIMER -> {
                        clearTimerOwnerGap(effect.key)
                        removeTimersForNotificationKey(effect.key)
                        statusBarAppPillLogClockNotif(
                            cause = "drop:${effect.cause}",
                            reason = DESK_CLOCK_REASON_UNKNOWN,
                            decision = "drop",
                            ownerAssoc = false,
                            eventKind = "timer",
                            eventToken = deskClockEventToken(effect.key),
                            timebaseAnchor = 0L,
                        )
                    }
                }
            }
            is DeskClockOwnerGapEffect.ArmTimer -> armDeskClockOwnerGapJob(effect, kind)
        }
    }

    private fun armDeskClockOwnerGapJob(
        arm: DeskClockOwnerGapEffect.ArmTimer,
        kind: DeskClockGapKind,
    ) {
        when (kind) {
            DeskClockGapKind.STOPWATCH -> {
                cancelStopwatchGapJobs()
                stopwatchOwnerGapKey = arm.key
                stopwatchGapJob =
                    applicationScope.launch {
                        delay(arm.delayMs)
                        val drop =
                            deskClockOwnerGapRetention.onArmedTimeout(
                                key = arm.key,
                                generation = arm.generation,
                                armSequence = arm.armSequence,
                                dueDeadlineElapsedMs = arm.dueDeadlineElapsedMs,
                            )
                        if (drop is DeskClockOwnerGapEffect.Drop) {
                            dropStopwatchEvent(cause = arm.cause)
                        }
                    }
            }
            DeskClockGapKind.TIMER -> {
                timerGapJobs.remove(arm.key)?.cancel()
                timerOwnerGapKeys.add(arm.key)
                timerGapJobs[arm.key] =
                    applicationScope.launch {
                        delay(arm.delayMs)
                        val drop =
                            deskClockOwnerGapRetention.onArmedTimeout(
                                key = arm.key,
                                generation = arm.generation,
                                armSequence = arm.armSequence,
                                dueDeadlineElapsedMs = arm.dueDeadlineElapsedMs,
                            )
                        if (drop is DeskClockOwnerGapEffect.Drop) {
                            clearTimerOwnerGap(arm.key)
                            removeTimersForNotificationKey(arm.key)
                            statusBarAppPillLogClockNotif(
                                cause = "drop:${arm.cause}",
                                reason = DESK_CLOCK_REASON_UNKNOWN,
                                decision = "drop",
                                ownerAssoc = false,
                                eventKind = "timer",
                                eventToken = deskClockEventToken(arm.key),
                                timebaseAnchor = 0L,
                            )
                        }
                    }
            }
        }
    }

    /** Co retention/event dang track cung notification key. */
    private fun hasTrackedDeskClockSameKeyGap(sbnKey: String): Boolean {
        if (deskClockOwnerGapRetention.hasGap(sbnKey)) return true
        if (stopwatchOwnerGapKey == sbnKey) return true
        if (_stopwatchEvent.value?.notificationKey == sbnKey) return true
        if (sbnKey in timerOwnerGapKeys) return true
        return _timerEvents.value.any { isTimerKeyForNotification(it.notificationKey, sbnKey) }
    }

    /** Kind dang retained cho key — STOPWATCH/TIMER/NONE. */
    private fun trackedDeskClockKindForKey(sbnKey: String): DeskClockNotifKind {
        val sw =
            stopwatchOwnerGapKey == sbnKey ||
                _stopwatchEvent.value?.notificationKey == sbnKey
        val tm =
            sbnKey in timerOwnerGapKeys ||
                _timerEvents.value.any { isTimerKeyForNotification(it.notificationKey, sbnKey) }
        return when {
            sw && !tm -> DeskClockNotifKind.STOPWATCH
            tm && !sw -> DeskClockNotifKind.TIMER
            sw && tm -> DeskClockNotifKind.STOPWATCH // hiem — uu tien SW roi cross-type drop
            else -> DeskClockNotifKind.NONE
        }
    }

    /** Cross-type: drop kind cu + clear retention truoc handle kind moi. */
    private fun dropTrackedDeskClockKindBeforeCrossType(
        sbnKey: String,
        prevKind: DeskClockNotifKind,
    ) {
        deskClockOwnerGapRetention.reconcileNonMatchingSameKeyPost(sbnKey)
        when (prevKind) {
            DeskClockNotifKind.STOPWATCH -> dropStopwatchEvent(cause = "cross_type_switch")
            DeskClockNotifKind.TIMER -> {
                clearTimerOwnerGap(sbnKey)
                removeTimersForNotificationKey(sbnKey)
                statusBarAppPillLogClockNotif(
                    cause = "drop:cross_type_switch",
                    reason = DESK_CLOCK_REASON_UNKNOWN,
                    decision = "drop",
                    ownerAssoc = false,
                    eventKind = "timer",
                    eventToken = deskClockEventToken(sbnKey),
                    timebaseAnchor = 0L,
                )
            }
            DeskClockNotifKind.NONE -> {
                // Gap record khong gan kind — chi clear retention.
                deskClockOwnerGapRetention.clearKey(sbnKey)
            }
        }
    }

    private fun reconcileDeskClockNonMatchingPost(sbnKey: String) {
        val wasStopwatchGap =
            stopwatchOwnerGapKey == sbnKey ||
                _stopwatchEvent.value?.notificationKey == sbnKey
        val wasTimerGap =
            sbnKey in timerOwnerGapKeys ||
                _timerEvents.value.any { isTimerKeyForNotification(it.notificationKey, sbnKey) }
        if (!wasStopwatchGap &&
            !wasTimerGap &&
            !deskClockOwnerGapRetention.hasGap(sbnKey)
        ) {
            return
        }
        val effect = deskClockOwnerGapRetention.reconcileNonMatchingSameKeyPost(sbnKey)
        if (effect !is DeskClockOwnerGapEffect.Drop) {
            // Van can drop UI neu chi co event ma retention da mat.
            if (wasStopwatchGap) dropStopwatchEvent(cause = "nonmatching_replace")
            if (wasTimerGap) {
                clearTimerOwnerGap(sbnKey)
                removeTimersForNotificationKey(sbnKey)
            }
            return
        }
        if (wasStopwatchGap) {
            applyDeskClockOwnerGapEffect(effect, DeskClockGapKind.STOPWATCH)
        }
        if (wasTimerGap) {
            applyDeskClockOwnerGapEffect(effect, DeskClockGapKind.TIMER)
        }
        // Retention gap ma chua gan SW/TM flag — effect Drop da xoa record.
    }

    private fun dropStopwatchEvent(cause: String) {
        cancelStopwatchGapJobs()
        val gapKey = stopwatchOwnerGapKey
        if (gapKey != null) deskClockOwnerGapRetention.clearKey(gapKey)
        stopwatchOwnerGapKey = null
        stopwatchNotificationKey = null
        clearStopwatchVisualDismissGate()
        if (_stopwatchEvent.value != null) {
            statusBarAppPillLogClockNotif(
                cause = "drop:$cause",
                reason = DESK_CLOCK_REASON_UNKNOWN,
                decision = "drop",
                ownerAssoc = false,
                eventKind = "stopwatch",
                eventToken = deskClockEventToken(_stopwatchEvent.value?.notificationKey),
                timebaseAnchor = _stopwatchEvent.value?.startElapsedRealtimeMs ?: 0L,
            )
        }
        _stopwatchEvent.value = null
    }

    private fun dropAllDeskClockOwnerGaps(cause: String) {
        deskClockOwnerGapRetention.clearAll()
        if (stopwatchOwnerGapKey != null || _stopwatchEvent.value != null) {
            dropStopwatchEvent(cause = cause)
        }
        for (key in timerOwnerGapKeys.toList()) {
            clearTimerOwnerGap(key)
            removeTimersForNotificationKey(key)
        }
        timerOwnerGapKeys.clear()
    }

    private fun cancelStopwatchGapJobs() {
        stopwatchGapJob?.cancel()
        stopwatchGapJob = null
    }

    private fun clearTimerOwnerGap(notificationKey: String) {
        timerOwnerGapKeys.remove(notificationKey)
        timerGapJobs.remove(notificationKey)?.cancel()
        deskClockOwnerGapRetention.clearKey(notificationKey)
    }

    private fun markStopwatchGapClearedOnPost(sbnKey: String, timebaseAnchor: Long) {
        val oldGap = stopwatchOwnerGapKey
        if (oldGap != null || deskClockOwnerGapRetention.hasGap(sbnKey)) {
            statusBarAppPillLogClockNotif(
                cause = "post",
                reason = DESK_CLOCK_REASON_UNKNOWN,
                decision = "rebind",
                ownerAssoc =
                    currentForegroundPackage()?.let { fg ->
                        _stopwatchEvent.value?.let { fg in islandEventOwnerPackages(it) }
                    } == true,
                eventKind = "stopwatch",
                eventToken = deskClockEventToken(sbnKey),
                timebaseAnchor = timebaseAnchor,
            )
        }
        // Valid matching rebind: clear old + new key; stale timer gen cu khong drop.
        if (oldGap != null) deskClockOwnerGapRetention.clearOnValidatedMatchingPost(oldGap)
        if (oldGap != sbnKey) deskClockOwnerGapRetention.clearOnValidatedMatchingPost(sbnKey)
        cancelStopwatchGapJobs()
        stopwatchOwnerGapKey = null
    }

    private fun markTimerGapClearedOnPost(sbnKey: String, timebaseAnchor: Long) {
        if (sbnKey in timerOwnerGapKeys || deskClockOwnerGapRetention.hasGap(sbnKey)) {
            statusBarAppPillLogClockNotif(
                cause = "post",
                reason = DESK_CLOCK_REASON_UNKNOWN,
                decision = "rebind",
                ownerAssoc =
                    currentForegroundPackage()?.let { fg ->
                        _timerEvents.value
                            .firstOrNull { isTimerKeyForNotification(it.notificationKey, sbnKey) }
                            ?.let { fg in islandEventOwnerPackages(it) }
                    } == true,
                eventKind = "timer",
                eventToken = deskClockEventToken(sbnKey),
                timebaseAnchor = timebaseAnchor,
            )
        }
        deskClockOwnerGapRetention.clearOnValidatedMatchingPost(sbnKey)
        clearTimerOwnerGap(sbnKey)
    }

    fun startListening() {
        if (listening) return
        listening = true
        ScrimUtils.get().addListener(scrimListener)
        applicationScope.launch(Dispatchers.Main) {
            try {
                val collection = notifCollectionLazy.get()
                collection.addCollectionListener(notifCollectionListener)
                collection.allNotifs.forEach { entry ->
                    entry.sbn?.let { scrimListener.onNotificationPosted(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loi khi dang ky collection listener hoac replay thong bao", e)
            }
        }
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        ScrimUtils.get().removeListener(scrimListener)
        applicationScope.launch(Dispatchers.Main) {
            try {
                notifCollectionLazy.get().removeCollectionListener(notifCollectionListener)
            } catch (e: Exception) {
                Log.w(TAG, "Loi khi huy collection listener", e)
            }
        }
        seenSignatures.clear()
        activeSbns.clear()
        _activeNotifications.value = emptyMap()
        timerJobs.values.forEach { it.cancel() }
        timerJobs.clear()
        timerOriginalDurations.clear()
        timerGapJobs.values.forEach { it.cancel() }
        timerGapJobs.clear()
        timerOwnerGapKeys.clear()
        deskClockOwnerGapRetention.clearAll()
        _timerEvents.value = emptyList()
        cancelStopwatchGapJobs()
        stopwatchOwnerGapKey = null
        stopwatchNotificationKey = null
        clearStopwatchVisualDismissGate()
        _stopwatchEvent.value = null
        dismissedPromotedKeys.clear()
        dismissedSportsKeys.clear()
        _alarmEvent.value = null
        _callEvents.value = emptyList()
        _notificationEvents.value = emptyList()
        _promotedOngoingEvents.value = emptyList()
        _sportsEvents.value = emptyList()
        _nowPlayingEvent.value = null
        alarmNotificationKey = null
        clearAudioRecording()
    }

    fun clearAudioRecording() {
        recorderGraceJob?.cancel()
        recorderGraceJob = null
        recording = null
        recordingDismissed = false
        _audioRecordingEvent.value = null
        recorderPackage = null
        recorderNotifKey = null
        pauseStartMs = 0L
        accumulatedPauseMs = 0L
    }

    fun dismissNotification(event: IslandEvent.Notification) {
        _notificationEvents.value = _notificationEvents.value.filter { it.id != event.id }
    }

    fun coalesceNotification(event: IslandEvent.Notification) {
        val current = _notificationEvents.value.toMutableList()
        current.removeAll { it.id == event.id }
        current.add(0, event)
        _notificationEvents.value = current
    }

    fun dismissAudioRecording() {
        if (recording == null) return
        recordingDismissed = true
        _audioRecordingEvent.value = null
    }

    private fun publishRecording(event: IslandEvent.AudioRecording) {
        recording = event
        _audioRecordingEvent.value = if (recordingDismissed) null else event
    }

    fun clearTimer(notificationKey: String? = null) {
        if (notificationKey.isNullOrEmpty()) {
            timerJobs.values.forEach { it.cancel() }
            timerJobs.clear()
            timerOriginalDurations.clear()
            timerGapJobs.values.forEach { it.cancel() }
            timerGapJobs.clear()
            val gapKeys = timerOwnerGapKeys.toList()
            timerOwnerGapKeys.clear()
            gapKeys.forEach { deskClockOwnerGapRetention.clearKey(it) }
            _timerEvents.value = emptyList()
        } else {
            clearTimerOwnerGap(notificationKey)
            removeTimersForNotificationKey(notificationKey)
        }
    }

    private fun isTimerKeyForNotification(timerKey: String, notificationKey: String): Boolean =
        timerKey == notificationKey || timerKey.startsWith("$notificationKey#")

    private fun cancelTimerJobsForNotificationKey(
        notificationKey: String,
        clearDurations: Boolean = true,
    ) {
        timerJobs.keys
            .filter { isTimerKeyForNotification(it, notificationKey) }
            .forEach { k ->
                timerJobs.remove(k)?.cancel()
                if (clearDurations) timerOriginalDurations.remove(k)
            }
    }

    private fun removeTimersForNotificationKey(
        notificationKey: String,
        clearDurations: Boolean = true,
    ) {
        cancelTimerJobsForNotificationKey(notificationKey, clearDurations)
        _timerEvents.value =
            _timerEvents.value.filter { !isTimerKeyForNotification(it.notificationKey, notificationKey) }
    }

    private fun removeTimer(notificationKey: String) {
        timerJobs.remove(notificationKey)?.cancel()
        timerOriginalDurations.remove(notificationKey)
        _timerEvents.value = _timerEvents.value.filter { it.notificationKey != notificationKey }
    }

    fun clearStopwatch() {
        // Visual swipe-hide pill — KHONG stop stopwatch that / KHONG owner-gap.
        // Stateful gate: rejected posts cap nhat latest (pause/resume).
        val existing = _stopwatchEvent.value
        val key =
            existing?.notificationKey?.takeIf { it.isNotEmpty() }
                ?: stopwatchNotificationKey
                ?: stopwatchOwnerGapKey
        stopwatchVisualDismissGate =
            if (key != null && existing != null) {
                deskClockStopwatchVisualDismissGateOf(
                    deskClockStopwatchRunIdentity(
                        notificationKey = key,
                        isRunning = existing.isRunning,
                        startElapsedRealtimeMs = existing.startElapsedRealtimeMs,
                        pausedElapsedMs = existing.pausedElapsedMs,
                        displayedElapsedMs = existing.displayedElapsedMs(),
                    ),
                )
            } else if (key != null) {
                deskClockStopwatchVisualDismissGateOf(
                    deskClockStopwatchRunIdentity(
                        notificationKey = key,
                        isRunning = false,
                        startElapsedRealtimeMs = 0L,
                        pausedElapsedMs = 0L,
                        displayedElapsedMs = 0L,
                    ),
                )
            } else {
                null
            }
        stopwatchDismissedPackage =
            existing?.packageName?.takeIf { it.isNotEmpty() }
        cancelStopwatchGapJobs()
        stopwatchOwnerGapKey?.let { deskClockOwnerGapRetention.clearKey(it) }
        stopwatchOwnerGapKey = null
        if (key != null) {
            stopwatchNotificationKey = key
        }
        _stopwatchEvent.value = null
    }

    fun clearAlarm() {
        _alarmEvent.value = null
    }

    fun clearCall(key: String) {
        _callEvents.value = _callEvents.value.filter { it.sbn.key != key }
    }

    /**
     * Chronometer trong android.metrics (TYPE_TIME_DIFFERENCE).
     * countDown=true → wallAnchorMs = end (timer); false → wallAnchorMs = start (stopwatch).
     */
    private data class ParsedChronometerMetric(
        val label: String,
        val countDown: Boolean,
        val wallAnchorMs: Long,
        val isPaused: Boolean,
        /** pausedDuration goc (ms): timer = remaining; stopwatch = elapsed — dong bang UI. */
        val frozenDurationMs: Long = 0L,
        /** zeroElapsedRealtime goc (timer = end ER; stopwatch = start ER). 0 = khong co. */
        val elapsedAnchorMs: Long = 0L,
    )

    /** Doc title/text an toan — Clock dung SpannableString, getString() tra null + log Bundle. */
    private fun extrasCharSequence(extras: Bundle, key: String): String? =
        extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }

    private fun handleTimer(
        sbn: StatusBarNotification,
        extras: Bundle,
        actionLabels: List<String> = emptyList(),
        actionIntents: List<String> = emptyList(),
    ) {
        val defaultLabel =
            extrasCharSequence(extras, Notification.EXTRA_TITLE)
                ?: context.getString(R.string.ax_dynamic_bar_timer)
        val icon =
            try {
                sbn.notification.smallIcon?.loadDrawable(context)
                    ?: context.packageManager.getApplicationIcon(sbn.packageName)
            } catch (_: Exception) {
                null
            }
        val notifPaused = detectTimerPausedFromActions(actionLabels, actionIntents)
        val rawActions = extractNotificationActions(sbn)
        // Cache PI PAUSE/START truoc khi loc (multi mat action tung timer).
        cacheDeskClockTogglePendingIntents(rawActions)
        // Bo action nhom ("Dat lai ca hai…") — moi the chi 1 timer, khong hien nut cho ca nhom.
        val actions = rawActions.filterNot { isSharedMultiTimerAction(it.label) }
        val key = sbn.key
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val metrics =
            parseChronometerMetricsFromExtras(extras, nowWall, nowElapsed).filter { it.countDown }
        // Giu endTime/duration cu truoc khi cap nhat (tranh nhap nhay bo nho).
        val existing =
            _timerEvents.value.find { isTimerKeyForNotification(it.notificationKey, key) }

        cancelTimerJobsForNotificationKey(key, clearDurations = false)

        val contentIntent = sbn.notification?.contentIntent
        val packageName = sbn.packageName.orEmpty()
        val currentList = _timerEvents.value
        val firstIdx = currentList.indexOfFirst { isTimerKeyForNotification(it.notificationKey, key) }
        val remaining = currentList.filter { !isTimerKeyForNotification(it.notificationKey, key) }
        val extractedIds = extractDeskClockTimerIds(sbn)
        val idCache = deskClockTimerIdsByNotif.getOrPut(key) { linkedSetOf() }
        extractedIds.forEach { idCache.add(it) }
        rememberDeskClockTimerIds(extractedIds)
        currentList
            .filter { isTimerKeyForNotification(it.notificationKey, key) }
            .map { it.deskClockTimerId }
            .filter { it >= 0 }
            .forEach { idCache.add(it) }
        // Nap rem_* tu prefs cho fingerprint (khong do known_ids vao pool idCache).
        loadPersistedDeskClockRemainings()
        val criticalIndex =
            extras.getInt("android.metrics.criticalIndex", 0).let { raw ->
                if (metrics.isEmpty()) 0 else raw.coerceIn(0, metrics.lastIndex)
            }

        if (metrics.isNotEmpty()) {
            val useSplitKeys = metrics.size > 1
            // Multi: groupKey "g:4" khong phai TIMER_ID — chi lay id tu URI/extra.
            val idsForResolve =
                if (metrics.size > 1) {
                    extractedIds.filter { it >= 0 }.distinct()
                } else {
                    extractedIds
                }
            val existingForKey =
                currentList.filter { isTimerKeyForNotification(it.notificationKey, key) }
            // Seed remaining tu event hien co (truoc khi remap).
            existingForKey.forEach { ev ->
                val id = ev.deskClockTimerId
                if (id < 0) return@forEach
                val rem = eventRemainingMs(ev, nowWall)
                if (rem > 0L) deskClockLastRemainingById[id] = rem
            }
            val assignedIds =
                assignDeskClockTimerIds(
                    metrics = metrics,
                    criticalIndex = criticalIndex,
                    extractedIds = idsForResolve,
                    cachedIds = idCache.toList(),
                    existingForKey = existingForKey,
                    notifKey = key,
                    useSplitKeys = useSplitKeys,
                    nowWall = nowWall,
                    nowElapsed = nowElapsed,
                )
            assignedIds.filter { it >= 0 }.forEach { idCache.add(it) }
            rememberDeskClockTimerIds(assignedIds)
            pruneDeskClockRemainings(assignedIds)
            val newEvents =
                metrics.mapIndexed { index, metric ->
                    val timerKey = if (useSplitKeys) "$key#$index" else key
                    val deskClockTimerId = assignedIds.getOrElse(index) { -1 }
                    val rem = metricRemainingMs(metric, nowWall, nowElapsed)
                    if (deskClockTimerId >= 0 && rem > 0L) {
                        deskClockLastRemainingById[deskClockTimerId] = rem
                        persistDeskClockRemaining(deskClockTimerId, rem)
                    }
                    upsertTimerEvent(
                        timerKey = timerKey,
                        label = metric.label.ifEmpty { defaultLabel },
                        endTimeMs = metric.wallAnchorMs,
                        isPaused = metric.isPaused || notifPaused,
                        pausedRemainingMs = metric.frozenDurationMs,
                        endElapsedRealtimeMs =
                            if (metric.isPaused || notifPaused) 0L else metric.elapsedAnchorMs,
                        appIcon = icon,
                        actions = actions,
                        contentIntent = contentIntent,
                        packageName = packageName,
                        sbn = sbn,
                        deskClockTimerId = deskClockTimerId,
                        pausePendingIntent = deskClockPausePiById[deskClockTimerId],
                        startPendingIntent = deskClockStartPiById[deskClockTimerId],
                    )
                }
            val insertAt = if (firstIdx >= 0) firstIdx.coerceAtMost(remaining.size) else 0
            _timerEvents.value = remaining.take(insertAt) + newEvents + remaining.drop(insertAt)
            markTimerGapClearedOnPost(
                sbnKey = key,
                timebaseAnchor = newEvents.firstOrNull()?.endElapsedRealtimeMs ?: 0L,
            )
            newEvents.forEach {
                Log.i(
                    TAG,
                    "handleTimer metrics label=${it.label} endTimeMs=${it.endTimeMs} " +
                        "paused=${it.isPaused} timerId=${it.deskClockTimerId}",
                )
                onTimerEvent?.invoke(it)
            }
            return
        }

        // Fallback: notification khong co android.metrics (DeskClock cu / app khac)
        var endTimeMs = resolveTimerEndTimeMs(sbn, extras)
        if (endTimeMs <= 0L && existing != null && existing.endTimeMs > nowWall) {
            endTimeMs = existing.endTimeMs
        }
        val fallbackId =
            extractedIds.firstOrNull()
                ?: existing?.deskClockTimerId?.takeIf { it >= 0 }
                ?: -1
        if (fallbackId >= 0) idCache.add(fallbackId)
        val event =
            upsertTimerEvent(
                timerKey = key,
                label = defaultLabel,
                endTimeMs = endTimeMs,
                isPaused = notifPaused,
                appIcon = icon,
                actions = actions,
                contentIntent = contentIntent,
                packageName = packageName,
                sbn = sbn,
                deskClockTimerId = fallbackId,
                pausePendingIntent = deskClockPausePiById[fallbackId],
                startPendingIntent = deskClockStartPiById[fallbackId],
            )
        val insertAt = if (firstIdx >= 0) firstIdx.coerceAtMost(remaining.size) else 0
        _timerEvents.value = remaining.take(insertAt) + listOf(event) + remaining.drop(insertAt)
        markTimerGapClearedOnPost(
            sbnKey = key,
            timebaseAnchor = event.endElapsedRealtimeMs,
        )
        Log.i(
            TAG,
            "handleTimer fallback label=${event.label} endTimeMs=${event.endTimeMs} paused=${event.isPaused}",
        )
        onTimerEvent?.invoke(event)
    }

    /**
     * Google Clock MetricStyle: android.metrics TYPE_TIME_DIFFERENCE (chronometer).
     * countDown=true (timer): wallAnchor = end. countDown=false (stopwatch): wallAnchor = start.
     * Thu tu: pausedDuration → zeroTime → zeroElapsedRealtime.
     */
    private fun parseChronometerMetricsFromExtras(
        extras: Bundle,
        nowWall: Long,
        nowElapsed: Long,
    ): List<ParsedChronometerMetric> {
        val bundles =
            try {
                extras.getParcelableArrayList("android.metrics", Bundle::class.java)
            } catch (_: Exception) {
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList<Bundle>("android.metrics")
            } ?: return emptyList()

        val result = mutableListOf<ParsedChronometerMetric>()
        for (metricBundle in bundles) {
            val value = metricBundle?.getBundle("value") ?: continue
            if (value.getInt("_type", -1) != 1) continue

            val countDown = value.getBoolean("countDown", false)
            val metricLabel = metricBundle.getCharSequence("label")?.toString().orEmpty()
            var wallAnchorMs = 0L
            var isPaused = false
            var frozenDurationMs = 0L
            var elapsedAnchorMs = 0L

            if (value.containsKey("pausedDuration")) {
                val duration = value.getLong("pausedDuration")
                if (countDown) {
                    // Timer paused: remaining dong bang (Chronometer.Paused)
                    if (duration > 0L) {
                        wallAnchorMs = nowWall + duration
                        isPaused = true
                        frozenDurationMs = duration
                    }
                } else {
                    // Stopwatch paused: elapsed dong bang
                    if (duration >= 0L) {
                        wallAnchorMs = nowWall - duration
                        isPaused = true
                        frozenDurationMs = duration
                    }
                }
            }
            if (wallAnchorMs <= 0L && value.containsKey("zeroTime")) {
                val zero = value.getLong("zeroTime")
                if (zero > 0L) wallAnchorMs = zero
            }
            if (!isPaused && value.containsKey("zeroElapsedRealtime")) {
                val zeroER = value.getLong("zeroElapsedRealtime")
                elapsedAnchorMs = zeroER
                if (countDown) {
                    // Timer: zeroER = thoi diem het gio (elapsedRealtime)
                    val remaining = zeroER - nowElapsed
                    if (remaining > 0L || wallAnchorMs <= 0L) {
                        wallAnchorMs = nowWall + remaining.coerceAtLeast(0L)
                    }
                } else {
                    // Stopwatch: zeroER = moc bat dau (co the am sau reboot)
                    val elapsed = (nowElapsed - zeroER).coerceAtLeast(0L)
                    if (wallAnchorMs <= 0L) wallAnchorMs = nowWall - elapsed
                }
            }
            if (wallAnchorMs > 0L || frozenDurationMs > 0L || elapsedAnchorMs != 0L) {
                result.add(
                    ParsedChronometerMetric(
                        metricLabel,
                        countDown,
                        wallAnchorMs,
                        isPaused,
                        frozenDurationMs,
                        elapsedAnchorMs,
                    ),
                )
            }
        }
        return result
    }

    private fun detectTimerPausedFromActions(
        actionLabels: List<String>,
        actionIntents: List<String>,
    ): Boolean {
        val hasPauseIntent = actionIntents.any { DeskClockActions.matches(it, "PAUSE_TIMER") }
        val hasStartIntent = actionIntents.any { DeskClockActions.matches(it, "START_TIMER") }
        val hasAddMinuteIntent = actionIntents.any { DeskClockActions.matches(it, "ADD_MINUTE_TIMER") }
        return when {
            hasStartIntent -> true
            hasPauseIntent || hasAddMinuteIntent -> false
            actionLabels.any {
                it.contains("+1") || it.contains("add") || it.contains("+ 1")
            } -> false
            else ->
                actionLabels.any {
                    it.contains("resume") ||
                        (it.contains("play") && !it.contains("display")) ||
                        it.contains("tiếp") ||
                        it.contains("tiep") ||
                        (it.contains("start") &&
                            !it.contains("stop") &&
                            !it.contains("reset") &&
                            !it.contains("đặt") &&
                            !it.contains("dat"))
                }
        }
    }

    private fun upsertTimerEvent(
        timerKey: String,
        label: String,
        endTimeMs: Long,
        isPaused: Boolean,
        pausedRemainingMs: Long = 0L,
        endElapsedRealtimeMs: Long = 0L,
        appIcon: Drawable?,
        actions: List<IslandEvent.NotificationAction>,
        contentIntent: android.app.PendingIntent? = null,
        packageName: String = "",
        sbn: StatusBarNotification? = null,
        deskClockTimerId: Int = -1,
        pausePendingIntent: android.app.PendingIntent? = null,
        startPendingIntent: android.app.PendingIntent? = null,
    ): IslandEvent.Timer {
        val existing = _timerEvents.value.find { it.notificationKey == timerKey }
        val nowMs = System.currentTimeMillis()
        val nowEr = SystemClock.elapsedRealtime()
        val originalDurationMs =
            when {
                existing != null && existing.originalDurationMs > 0L -> existing.originalDurationMs
                timerOriginalDurations[timerKey] != null -> timerOriginalDurations[timerKey]!!
                endTimeMs > 0L ->
                    (endTimeMs - nowMs).coerceAtLeast(1000L).also {
                        timerOriginalDurations[timerKey] = it
                    }
                else -> timerOriginalDurations[timerKey] ?: 0L
            }
        if (originalDurationMs > 0L) {
            timerOriginalDurations[timerKey] = originalDurationMs
        }

        // Dong bang remaining khi paused — tranh UI tru wall-clock khi doi pill / recomposition.
        val frozenRemaining =
            when {
                !isPaused -> 0L
                pausedRemainingMs > 0L -> pausedRemainingMs
                existing?.isPaused == true && existing.pausedRemainingMs > 0L ->
                    existing.pausedRemainingMs
                endElapsedRealtimeMs > 0L -> (endElapsedRealtimeMs - nowEr).coerceAtLeast(0L)
                endTimeMs > 0L -> (endTimeMs - nowMs).coerceAtLeast(0L)
                else -> 0L
            }
        val endEr = if (isPaused) 0L else endElapsedRealtimeMs

        timerJobs.remove(timerKey)?.cancel()
        if (!isPaused) {
            val remainingMs =
                when {
                    endEr > 0L -> endEr - nowEr
                    endTimeMs > 0L -> endTimeMs - nowMs
                    else -> 0L
                }
            if (remainingMs > 0L) {
                timerJobs[timerKey] =
                    applicationScope.launch {
                        delay((remainingMs + 3_000L).coerceAtLeast(3_000L))
                        removeTimer(timerKey)
                    }
            }
        }

        val resolvedId =
            when {
                deskClockTimerId >= 0 -> deskClockTimerId
                existing?.deskClockTimerId != null && existing.deskClockTimerId >= 0 ->
                    existing.deskClockTimerId
                else -> -1
            }
        val pausePi =
            pausePendingIntent
                ?: (if (resolvedId >= 0) deskClockPausePiById[resolvedId] else null)
                ?: existing?.pausePendingIntent
        val startPi =
            startPendingIntent
                ?: (if (resolvedId >= 0) deskClockStartPiById[resolvedId] else null)
                ?: existing?.startPendingIntent
        return IslandEvent.Timer(
            label = label,
            endTimeMs = endTimeMs,
            originalDurationMs = originalDurationMs,
            appIcon = appIcon,
            isPaused = isPaused,
            pausedRemainingMs = frozenRemaining,
            endElapsedRealtimeMs = endEr,
            actions = actions,
            notificationKey = timerKey,
            contentIntent = contentIntent,
            packageName = packageName,
            sbn = sbn,
            deskClockTimerId = resolvedId,
            pausePendingIntent = pausePi,
            startPendingIntent = startPi,
        )
    }

    /** Luu PI PAUSE/START + TIMER_ID tu action notif (truoc khi loc action nhom). */
    private fun cacheDeskClockTogglePendingIntents(
        rawActions: List<IslandEvent.NotificationAction>,
    ) {
        for (na in rawActions) {
            val pi = na.action.actionIntent ?: continue
            val intent =
                try {
                    pi.intent
                } catch (_: Exception) {
                    null
                } ?: continue
            val id =
                try {
                    intent.getIntExtra(EXTRA_TIMER_ID, -1)
                } catch (_: Exception) {
                    -1
                }
            if (id < 0) continue
            rememberDeskClockTimerIds(listOf(id))
            val act = intent.action.orEmpty()
            when {
                DeskClockActions.matches(act, "PAUSE_TIMER") -> deskClockPausePiById[id] = pi
                DeskClockActions.matches(act, "START_TIMER") -> deskClockStartPiById[id] = pi
            }
        }
    }

    /**
     * PI Clock tao (tu notif 1-timer) — dung khi the multi khong con action Pause/Play.
     * Khong xoa khi notif doi sang multi; chi mat khi SystemUI chet.
     */
    fun getDeskClockTogglePendingIntent(timerId: Int, pause: Boolean): android.app.PendingIntent? {
        if (timerId < 0) return null
        return if (pause) deskClockPausePiById[timerId] else deskClockStartPiById[timerId]
    }

    /**
     * Lay TIMER_ID / TIMER_IDS tu contentIntent (URI clock-app://…/timer/N), action, groupKey.
     * Multi-timer Clock chi gui RESET_UNEXPIRED — can id de tong hop PAUSE/START.
     */
    private fun extractDeskClockTimerIds(sbn: StatusBarNotification): List<Int> {
        val ids = linkedSetOf<Int>()
        fun absorb(intent: Intent?) {
            if (intent == null) return
            try {
                intent.getIntArrayExtra(EXTRA_TIMER_IDS)?.forEach { if (it >= 0) ids.add(it) }
            } catch (_: Exception) {}
            try {
                val one = intent.getIntExtra(EXTRA_TIMER_ID, -1)
                if (one >= 0) ids.add(one)
            } catch (_: Exception) {}
            try {
                val uri = intent.data?.toString().orEmpty()
                TIMER_URI_ID_PATTERN.findAll(uri).forEach { m ->
                    m.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 }?.let { ids.add(it) }
                }
            } catch (_: Exception) {}
        }
        try {
            absorb(sbn.notification?.contentIntent?.intent)
        } catch (_: Exception) {}
        try {
            absorb(sbn.notification?.deleteIntent?.intent)
        } catch (_: Exception) {}
        sbn.notification?.actions?.forEach { action ->
            try {
                absorb(action.actionIntent?.intent)
            } catch (_: Exception) {}
        }
        // Chi dung group/g:N khi 1 metric — multi Clock dung group "4" chung, khong phai TIMER_ID.
        val metricCount =
            try {
                sbn.notification
                    ?.extras
                    ?.getParcelableArrayList<Bundle>("android.metrics")
                    ?.size
                    ?: 0
            } catch (_: Exception) {
                0
            }
        if (metricCount <= 1) {
            val group = sbn.notification?.group.orEmpty()
            group.toIntOrNull()?.takeIf { it >= 0 }?.let { ids.add(it) }
            listOf(group, sbn.groupKey.orEmpty()).forEach { g ->
                TIMER_GROUP_ID_PATTERN.findAll(g).forEach { m ->
                    m.groupValues
                        .getOrNull(1)
                        ?.toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?.let { ids.add(it) }
                }
            }
        }
        return ids.toList()
    }

    /** Remaining dong nhat (pausedDuration hoac end−now) — dung de khop id khi reorder. */
    private fun metricRemainingMs(
        metric: ParsedChronometerMetric,
        nowWall: Long,
        nowElapsed: Long,
    ): Long {
        if (metric.isPaused) return metric.frozenDurationMs.coerceAtLeast(0L)
        if (metric.elapsedAnchorMs > 0L) {
            return (metric.elapsedAnchorMs - nowElapsed).coerceAtLeast(0L)
        }
        if (metric.wallAnchorMs > 0L) {
            return (metric.wallAnchorMs - nowWall).coerceAtLeast(0L)
        }
        return 0L
    }

    private fun eventRemainingMs(ev: IslandEvent.Timer, nowWall: Long): Long {
        if (ev.isPaused) return ev.pausedRemainingMs.coerceAtLeast(0L)
        if (ev.endElapsedRealtimeMs > 0L) {
            return (ev.endElapsedRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
        if (ev.endTimeMs > 0L) return (ev.endTimeMs - nowWall).coerceAtLeast(0L)
        return 0L
    }

    private fun loadPersistedDeskClockIds(): List<Int> {
        val raw = deskClockPrefs.getString(PREFS_KNOWN_IDS, "").orEmpty()
        if (raw.isEmpty()) return emptyList()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id >= 0 } }
    }

    /** Chi nap rem_* — dung cho fingerprint; khong dua id lich su vao pool gan. */
    private fun loadPersistedDeskClockRemainings() {
        val all = deskClockPrefs.all ?: return
        for ((key, value) in all) {
            if (!key.startsWith(PREFS_REMAINING_PREFIX)) continue
            val id = key.removePrefix(PREFS_REMAINING_PREFIX).toIntOrNull() ?: continue
            val rem = (value as? Long) ?: continue
            if (id >= 0 && rem > 0L) deskClockLastRemainingById.putIfAbsent(id, rem)
        }
    }

    private fun rememberDeskClockTimerIds(ids: Iterable<Int>) {
        val valid = ids.filter { it >= 0 }.distinct()
        if (valid.isEmpty()) return
        // Giu biet id da thay (debug / fingerprint nap rem) — pool gan khong dung list nay.
        val merged = (loadPersistedDeskClockIds() + valid).distinct()
        deskClockPrefs.edit().putString(PREFS_KNOWN_IDS, merged.joinToString(",")).apply()
        for (id in valid) {
            if (deskClockLastRemainingById.containsKey(id)) continue
            val rem = deskClockPrefs.getLong(PREFS_REMAINING_PREFIX + id, -1L)
            if (rem > 0L) deskClockLastRemainingById[id] = rem
        }
    }

    private fun persistDeskClockRemaining(timerId: Int, remainingMs: Long) {
        if (timerId < 0 || remainingMs <= 0L) return
        deskClockPrefs.edit().putLong(PREFS_REMAINING_PREFIX + timerId, remainingMs).apply()
    }

    /** Sau gan xong: xoa rem_* khong con dung (tranh id timer cu khop nham). */
    private fun pruneDeskClockRemainings(activeIds: Collection<Int>) {
        val keep = activeIds.filter { it >= 0 }.toSet()
        val edit = deskClockPrefs.edit()
        var changed = false
        for (id in deskClockLastRemainingById.keys.toList()) {
            if (id in keep) continue
            deskClockLastRemainingById.remove(id)
            edit.remove(PREFS_REMAINING_PREFIX + id)
            changed = true
        }
        if (keep.isNotEmpty()) {
            edit.putString(PREFS_KNOWN_IDS, keep.sorted().joinToString(","))
            changed = true
        }
        if (changed) edit.apply()
    }

    /**
     * Gan TIMER_ID duy nhat cho tung metric.
     * Khop theo remaining ms (khong so endTime vs pausedDuration) — tranh Resume chi 1
     * timer khi metrics doi thu tu / ca hai cung label "Da tam dung".
     * Khong sticky theo slot #index khi multi (thu tu metric khong on dinh).
     */
    private fun assignDeskClockTimerIds(
        metrics: List<ParsedChronometerMetric>,
        criticalIndex: Int,
        extractedIds: List<Int>,
        cachedIds: List<Int>,
        existingForKey: List<IslandEvent.Timer>,
        notifKey: String,
        useSplitKeys: Boolean,
        nowWall: Long,
        nowElapsed: Long,
    ): List<Int> {
        val n = metrics.size
        if (n == 0) return emptyList()
        val out = MutableList(n) { -1 }
        val taken = linkedSetOf<Int>()

        fun claim(index: Int, id: Int): Boolean {
            if (id < 0 || id in taken || out[index] >= 0) return false
            out[index] = id
            taken.add(id)
            return true
        }

        fun remainingClose(a: Long, b: Long): Boolean {
            if (a <= 0L || b <= 0L) return false
            return kotlin.math.abs(a - b) <= TIMER_REMAINING_MATCH_MS
        }

        // 1) Khop remaining vs event hien co (on dinh qua pause/resume + doi label).
        for (index in 0 until n) {
            val rem = metricRemainingMs(metrics[index], nowWall, nowElapsed)
            if (rem <= 0L) continue
            val match =
                existingForKey.firstOrNull { ev ->
                    val eid = ev.deskClockTimerId
                    if (eid < 0 || eid in taken) return@firstOrNull false
                    remainingClose(eventRemainingMs(ev, nowWall), rem)
                }
            if (match != null) claim(index, match.deskClockTimerId)
        }

        // 2) Khop remaining vs ban do id→remaining (kem prefs sau restart).
        for (index in 0 until n) {
            if (out[index] >= 0) continue
            val rem = metricRemainingMs(metrics[index], nowWall, nowElapsed)
            if (rem <= 0L) continue
            val matchId =
                deskClockLastRemainingById.entries
                    .firstOrNull { (id, last) -> id !in taken && remainingClose(last, rem) }
                    ?.key
            if (matchId != null) claim(index, matchId)
        }

        // 3) URI critical + pool id cua notif/event hien tai — KHONG do known_ids lich su
        // (id timer da xoa bi gan nham → Resume sai 1 cai).
        val pool =
            (extractedIds.asSequence() +
                    cachedIds.asSequence() +
                    existingForKey.asSequence().map { it.deskClockTimerId } +
                    deskClockTimerIdsByNotif[notifKey].orEmpty().asSequence())
                .filter { it >= 0 }
                .distinct()
                .toList()
        val criticalId = extractedIds.firstOrNull() ?: -1
        if (criticalId >= 0) claim(criticalIndex.coerceIn(0, n - 1), criticalId)
        for (index in 0 until n) {
            if (out[index] >= 0) continue
            val next = pool.firstOrNull { it !in taken } ?: -1
            claim(index, next)
        }

        // 4) Fallback cu (1 timer / thieu cache notif) — van khong dung prefs rong.
        for (index in 0 until n) {
            if (out[index] >= 0) continue
            val resolved =
                resolveDeskClockTimerId(
                    index = index,
                    criticalIndex = criticalIndex,
                    metricsSize = n,
                    extractedIds = extractedIds,
                    cachedIds = cachedIds,
                )
            claim(index, resolved)
        }

        // 5) Sticky slot chi khi 1 metric (multi: thu tu doi theo critical — nguy hiem).
        if (!useSplitKeys) {
            for (index in 0 until n) {
                if (out[index] >= 0) continue
                val prev =
                    existingForKey
                        .firstOrNull { it.notificationKey == notifKey }
                        ?.deskClockTimerId
                        ?.takeIf { it >= 0 }
                        ?: continue
                claim(index, prev)
            }
        }
        return out
    }

    /**
     * Gan id cho tung metric: critical → URI/id dau; con lai → cache (id da thay khi 1 timer).
     * Neu TIMER_IDS du so metric → map theo chi so.
     */
    private fun resolveDeskClockTimerId(
        index: Int,
        criticalIndex: Int,
        metricsSize: Int,
        extractedIds: List<Int>,
        cachedIds: List<Int>,
    ): Int {
        if (extractedIds.size >= metricsSize && metricsSize > 0) {
            return extractedIds.getOrElse(index) { -1 }
        }
        val criticalId = extractedIds.firstOrNull() ?: -1
        if (index == criticalIndex && criticalId >= 0) return criticalId
        if (metricsSize <= 1) return criticalId
        val packageWide =
            deskClockTimerIdsByNotif.values.asSequence().flatten().filter { it >= 0 }.distinct()
        val others =
            (cachedIds.asSequence() + packageWide)
                .filter { it >= 0 && it != criticalId }
                .distinct()
                .toList()
        val otherSlots = (0 until metricsSize).filter { it != criticalIndex }
        val slot = otherSlots.indexOf(index)
        if (slot >= 0) return others.getOrElse(slot) { -1 }
        return -1
    }

    /**
     * Lay moc ket thuc timer (wall-clock ms).
     * Thu lan luot: Metric.TimeDifference, notification.when (+ chronometer),
     * Chronometer RemoteViews, parse mm:ss tu text.
     */
    private fun resolveTimerEndTimeMs(sbn: StatusBarNotification, extras: Bundle): Long {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        endTimeFromTimerMetric(extras, nowWall, nowElapsed)?.let { return it }

        val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val whenMs = sbn.notification.`when`
        if (whenMs > 0L) {
            wallClockEndFromWhen(whenMs, showChrono, countDown, nowWall, nowElapsed)?.let {
                return it
            }
        }

        val chronoBase = extractChronometerBase(sbn)
        if (chronoBase > 0L) {
            val remaining = chronoBase - nowElapsed
            if (remaining > 0L) return nowWall + remaining
        }

        parseTimerRemainingMs(sbn, extras)?.let { remaining ->
            if (remaining > 0L) return nowWall + remaining
        }

        return 0L
    }

    /** Fallback single endtime — tai su dung parse multi-metric countdown. */
    private fun endTimeFromTimerMetric(
        extras: Bundle,
        nowWall: Long,
        nowElapsed: Long,
    ): Long? =
        parseChronometerMetricsFromExtras(extras, nowWall, nowElapsed)
            .firstOrNull { it.countDown }
            ?.wallAnchorMs

    /**
     * Chuyen notification.when thanh wall-clock end.
     * AOSP: when = wall-clock end. Mot so app luu sai theo elapsedRealtime.
     */
    private fun wallClockEndFromWhen(
        whenMs: Long,
        showChrono: Boolean,
        countDown: Boolean,
        nowWall: Long,
        nowElapsed: Long,
    ): Long? {
        val looksLikeWallClock = whenMs > 1_000_000_000_000L // ~2001-09-09
        if (looksLikeWallClock) {
            if (countDown || showChrono) {
                // Cho phep tre vai giay (lam tron DeskClock + SECOND_IN_MILLIS)
                if (whenMs > nowWall - 5_000L) return whenMs.coerceAtLeast(nowWall)
            }
            if (whenMs > nowWall) return whenMs
            return null
        }

        // when mang hinh elapsedRealtime (sai hop dong nhung gap thuc te)
        if (showChrono || countDown) {
            val remaining = whenMs - nowElapsed
            if (remaining > 0L) return nowWall + remaining
        }
        return null
    }

    /** Parse con lai mm:ss / h:mm:ss tu shortCriticalText / sortKey / text / title. */
    private fun parseTimerRemainingMs(sbn: StatusBarNotification, extras: Bundle): Long? {
        val shortCritical =
            try {
                sbn.notification?.shortCriticalText?.toString()
            } catch (_: Exception) {
                extras.getCharSequence(Notification.EXTRA_SHORT_CRITICAL_TEXT)?.toString()
                    ?: extras.getCharSequence("android.shortCriticalText")?.toString()
            }
        // MetricStyle embedding remaining trong sortKey (⏳0:01:46) khi extras metrics thieu
        val sortKey = sbn.notification?.sortKey
        val candidates =
            listOfNotNull(
                shortCritical,
                sortKey,
                extrasCharSequence(extras, Notification.EXTRA_TEXT),
                extrasCharSequence(extras, Notification.EXTRA_TITLE),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            )
        for (text in candidates) {
            parseElapsedTimeToMs(text)?.let { return it }
            parseSortKeyRemainingMs(text)?.let { return it }
        }
        return null
    }

    private fun parseElapsedTimeToMs(text: String): Long? {
        val match = TIMER_REMAINING_PATTERN.find(text) ?: return null
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[2].toLongOrNull() ?: return null
        val c = match.groupValues[3].toLongOrNull()
        val secs = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
        if (secs <= 0L) return null
        return secs * 1000L
    }

    /**
     * Doc danh sach vong stopwatch tu EXTRA_TEXT_LINES / BigText / EXTRA_TEXT.
     * Giu laps cu neu notif lan nay khong gui lai lines (tranh mat khi refresh).
     */
    private fun parseStopwatchLapsMs(extras: Bundle, previous: List<Long>): List<Long> {
        val lines = mutableListOf<String>()
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach {
            val s = it?.toString()?.trim().orEmpty()
            if (s.isNotEmpty()) lines += s
        }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.lines()?.forEach {
            val s = it.trim()
            if (s.isNotEmpty()) lines += s
        }
        extrasCharSequence(extras, Notification.EXTRA_TEXT)?.lines()?.forEach {
            val s = it.trim()
            if (s.isNotEmpty()) lines += s
        }
        if (lines.isEmpty()) return previous
        val parsed =
            lines.mapNotNull { line ->
                // Bo qua dong chi la nhan "Lap"/"Vong" khong so.
                parseElapsedTimeToMs(line)
                    ?: STOPWATCH_LAP_PATTERN.find(line)?.let { m ->
                        val a = m.groupValues[1].toLongOrNull() ?: return@let null
                        val b = m.groupValues[2].toLongOrNull() ?: return@let null
                        val c = m.groupValues[3].toLongOrNull()
                        val frac = m.groupValues[4]
                        val secs = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
                        val fracMs =
                            if (frac.isNotEmpty()) {
                                frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                            } else {
                                0L
                            }
                        ((secs * 1000L) + fracMs).takeIf { it > 0L }
                    }
            }
                .distinct()
                .sorted()
        return if (parsed.isNotEmpty()) parsed else previous
    }

    /** Parse remaining tu MetricStyle sortKey (uu tien chuoi sau emoji dong ho). */
    private fun parseSortKeyRemainingMs(text: String): Long? {
        if (!text.contains("⏳") && !text.contains("⏱") && !text.contains("🕐")) {
            // Van thu pattern neu chuoi chi la H:MM:SS / M:SS
            if (!text.any { it.isDigit() }) return null
        }
        val match = SORTKEY_REMAINING_PATTERN.find(text) ?: return null
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[2].toLongOrNull() ?: return null
        val c = match.groupValues[3].toLongOrNull()
        val secs = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
        if (secs <= 0L) return null
        return secs * 1000L
    }

    /** Nhan dien stopwatch dang chay / tam dung tu label action (EN + VI). */
    private fun detectStopwatchRunningFromLabels(actionLabels: List<String>): Boolean? {
        val running =
            actionLabels.any { label ->
                label.contains("pause") ||
                    label.contains("tạm") ||
                    label.contains("tam dung") ||
                    label.contains("lap") ||
                    label.contains("vòng") ||
                    label.contains("vong")
            }
        val paused =
            actionLabels.any { label ->
                label.contains("resume") ||
                    label.contains("tiếp") ||
                    label.contains("tiep")
            }
        return when {
            running -> true
            paused -> false
            else -> null
        }
    }

    private fun handleStopwatch(
        sbn: StatusBarNotification,
        extras: Bundle,
        actionLabels: List<String> = emptyList(),
        actionIntents: List<String> = emptyList(),
    ) {
        val label = extrasCharSequence(extras, Notification.EXTRA_TITLE) ?: ""
        val icon =
            try {
                sbn.notification.smallIcon?.loadDrawable(context)
                    ?: context.packageManager.getApplicationIcon(sbn.packageName)
            } catch (_: Exception) {
                null
            }

        val hasPauseIntent = actionIntents.any { DeskClockActions.matches(it, "PAUSE_STOPWATCH") }
        val hasStartIntent = actionIntents.any { DeskClockActions.matches(it, "START_STOPWATCH") }
        val hasLapIntent = actionIntents.any { DeskClockActions.matches(it, "LAP_STOPWATCH") }
        val labelRunning = detectStopwatchRunningFromLabels(actionLabels)
        var isRunning =
            when {
                hasPauseIntent || hasLapIntent -> true
                hasStartIntent -> false
                labelRunning != null -> labelRunning
                else -> true // ongoing stopwatch notif: mac dinh dang chay khi khong ro action
            }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val existing = _stopwatchEvent.value
        var startTimeMs = existing?.startTimeMs ?: nowWall
        var startElapsedRealtimeMs = 0L
        var pausedElapsedMs = 0L
        var resolvedStart = false

        // Uu tien android.metrics (countDown=false): when bi Clock refresh ≈ now khi dong app.
        // Metric thang action labels — tranh action START/PAUSE nham → dong bang sai khi
        // Clock van chay (pill lech / tut hau so voi app).
        val stopwatchMetric =
            parseChronometerMetricsFromExtras(extras, nowWall, nowElapsed).firstOrNull {
                !it.countDown
            }
        if (stopwatchMetric != null) {
            if (stopwatchMetric.isPaused) {
                isRunning = false
                pausedElapsedMs = stopwatchMetric.frozenDurationMs
                startElapsedRealtimeMs = 0L
                startTimeMs =
                    if (stopwatchMetric.wallAnchorMs > 0L) stopwatchMetric.wallAnchorMs
                    else nowWall - pausedElapsedMs.coerceAtLeast(0L)
            } else {
                isRunning = true
                pausedElapsedMs = 0L
                startElapsedRealtimeMs = stopwatchMetric.elapsedAnchorMs
                startTimeMs =
                    if (stopwatchMetric.wallAnchorMs > 0L) {
                        stopwatchMetric.wallAnchorMs
                    } else if (startElapsedRealtimeMs != 0L) {
                        nowWall - (nowElapsed - startElapsedRealtimeMs).coerceAtLeast(0L)
                    } else {
                        existing?.startTimeMs ?: nowWall
                    }
            }
            resolvedStart = true
        }

        if (!resolvedStart) {
            val whenMs = sbn.notification.`when`
            val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
            if (whenMs > 1_000_000_000_000L) {
                // Wall-clock start — DeskClock cu / app khong metrics
                startTimeMs = whenMs
                startElapsedRealtimeMs = 0L
                resolvedStart = true
            } else if (showChrono && whenMs > 0L) {
                // whenMs = chronometer base (elapsedRealtime)
                val elapsed = nowElapsed - whenMs
                if (elapsed >= 0L) {
                    startTimeMs = nowWall - elapsed
                    startElapsedRealtimeMs = whenMs
                    resolvedStart = true
                }
            }
        }
        if (!resolvedStart) {
            val chronoBase = extractChronometerBase(sbn)
            if (chronoBase != 0L) {
                // RemoteViews Chronometer.base = elapsedRealtime (co the am)
                val elapsed = (nowElapsed - chronoBase).coerceAtLeast(0L)
                startTimeMs = nowWall - elapsed
                startElapsedRealtimeMs = chronoBase
                resolvedStart = true
            }
        }

        // Dong bang elapsed khi paused (giu cu neu notif khong gui lai pausedDuration)
        if (!isRunning) {
            pausedElapsedMs =
                when {
                    pausedElapsedMs > 0L -> pausedElapsedMs
                    existing != null && !existing.isRunning && existing.pausedElapsedMs > 0L ->
                        existing.pausedElapsedMs
                    startElapsedRealtimeMs != 0L ->
                        (nowElapsed - startElapsedRealtimeMs).coerceAtLeast(0L)
                    startTimeMs > 0L -> (nowWall - startTimeMs).coerceAtLeast(0L)
                    else -> 0L
                }
            startElapsedRealtimeMs = 0L
        }

        val actions = extractNotificationActions(sbn)
        val laps = parseStopwatchLapsMs(extras, existing?.laps.orEmpty())
        val event =
            IslandEvent.Stopwatch(
                label = label,
                startTimeMs = startTimeMs,
                startElapsedRealtimeMs = if (isRunning) startElapsedRealtimeMs else 0L,
                isRunning = isRunning,
                pausedElapsedMs = if (isRunning) 0L else pausedElapsedMs,
                laps = laps,
                appIcon = icon,
                actions = actions,
                contentIntent = sbn.notification?.contentIntent,
                packageName = sbn.packageName.orEmpty(),
                notificationKey = sbn.key,
                sbn = sbn,
                awaitingOwnerRepost = false,
            )
        stopwatchNotificationKey = sbn.key
        val postIdentity =
            deskClockStopwatchRunIdentity(
                notificationKey = sbn.key,
                isRunning = event.isRunning,
                startElapsedRealtimeMs = event.startElapsedRealtimeMs,
                pausedElapsedMs = event.pausedElapsedMs,
                displayedElapsedMs = event.displayedElapsedMs(),
            )
        val apply =
            deskClockStopwatchApplyPostToVisualDismissGate(
                gate = stopwatchVisualDismissGate,
                post = postIdentity,
            )
        stopwatchVisualDismissGate = apply.nextGate
        if (apply.allowEvent &&
            (apply.relation == DeskClockStopwatchRunRelation.ALLOW_NEW_RUN ||
                apply.relation == DeskClockStopwatchRunRelation.ALLOW_DIFFERENT_KEY)
        ) {
            stopwatchDismissedPackage = null
        }
        if (apply.allowEvent) {
            _stopwatchEvent.value = event
            // Cung key/replacement sau owner-gap: rebind, khong restart timebase (existing).
            markStopwatchGapClearedOnPost(
                sbnKey = sbn.key,
                timebaseAnchor =
                    if (event.isRunning) event.startElapsedRealtimeMs else event.pausedElapsedMs,
            )
            Log.i(
                TAG,
                "handleStopwatch label=${event.label} isRunning=${event.isRunning} " +
                    "startTimeMs=${event.startTimeMs} startER=${event.startElapsedRealtimeMs} " +
                    "pausedElapsed=${event.pausedElapsedMs} " +
                    "displayed=${event.displayedElapsedMs()} fromMetrics=${stopwatchMetric != null}",
            )
        }
    }

    private fun extractChronometerBase(sbn: StatusBarNotification): Long {
        try {
            val rv = sbn.notification.contentView ?: sbn.notification.bigContentView ?: return 0L
            val pkgCtx = context.createPackageContext(sbn.packageName, Context.CONTEXT_RESTRICTED)
            val container = FrameLayout(context)
            val inflated = rv.apply(pkgCtx, container) ?: return 0L
            return findChronometer(inflated)?.base ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract chronometer base from ${sbn.packageName}", e)
            return 0L
        }
    }

    private fun findChronometer(view: View): Chronometer? {
        if (view is Chronometer) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findChronometer(view.getChildAt(i))?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun extractNotificationActions(
        sbn: StatusBarNotification
    ): List<IslandEvent.NotificationAction> {
        return sbn.notification
            ?.actions
            ?.filter { a -> a.remoteInputs.isNullOrEmpty() && a.actionIntent != null }
            ?.take(3)
            ?.mapNotNull { a ->
                a.title?.let { IslandEvent.NotificationAction(label = it, action = a) }
            } ?: emptyList()
    }

    /**
     * Action ap dung ca nhom timer (Clock: "Dat lai ca hai…", "Reset both…").
     * Khong dua vao the expand 1-timer.
     */
    private fun isSharedMultiTimerAction(label: CharSequence?): Boolean {
        val t = label?.toString()?.lowercase().orEmpty()
        if (t.isEmpty()) return false
        if (t.contains("cả hai") || t.contains("ca hai")) return true
        if (t.contains("both")) return true
        if (t.contains("all timers") || t.contains("tất cả") || t.contains("tat ca")) return true
        // "hai/nhieu dong ho" trong cung nhan nut reset/pause nhom.
        if ((t.contains("hai ") || t.contains("nhiều") || t.contains("nhieu")) &&
            (t.contains("đồng hồ") || t.contains("dong ho") || t.contains("timer"))
        ) {
            return true
        }
        return false
    }

    private fun handleAlarm(sbn: StatusBarNotification, extras: Bundle) {
        val label =
            extrasCharSequence(extras, Notification.EXTRA_TITLE)
                ?: context.getString(R.string.ax_dynamic_bar_alarm)
        val isRinging = sbn.notification.category == Notification.CATEGORY_ALARM
        val icon =
            try {
                context.packageManager.getApplicationIcon(sbn.packageName)
            } catch (_: Exception) {
                null
            }
        val event =
            IslandEvent.Alarm(
                label = label,
                triggerTimeMs = sbn.notification.`when`,
                isRinging = isRinging,
                appIcon = icon,
            )
        alarmNotificationKey = sbn.key
        _alarmEvent.value = event
        onAlarmEvent?.invoke(event)
    }

    private fun resolveAppName(packageName: String): String =
        try {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            ""
        }

    private fun handleCallNotification(sbn: StatusBarNotification, extras: Bundle) {
        val callerName = extras.getString("android.title")
        val number = extras.getString("android.text")
        val callerPhoto =
            try {
                sbn.notification?.getLargeIcon()?.loadDrawable(context)
            } catch (_: Exception) {
                null
            }

        val allActions = sbn.notification?.actions ?: emptyArray()
        val actions =
            allActions
                .filter { a -> a.remoteInputs.isNullOrEmpty() && a.actionIntent != null }
                .take(3)
                .mapNotNull { a ->
                    a.title?.let { IslandEvent.NotificationAction(label = it, action = a) }
                }

        val androidCallType = extras.getInt(
            Notification.EXTRA_CALL_TYPE,
            Notification.CallStyle.CALL_TYPE_UNKNOWN,
        )
        val callType =
            if (androidCallType == Notification.CallStyle.CALL_TYPE_INCOMING)
                "Phone:incoming"
            else "Phone:active"

        val icon =
            try {
                context.packageManager.getApplicationIcon(sbn.packageName)
            } catch (_: Exception) {
                null
            }

        val callWhen = sbn.notification?.`when` ?: 0L
        val callStart = if (callWhen > 0L) callWhen else System.currentTimeMillis()

        val event =
            IslandEvent.Call(
                sbn = sbn,
                callerName = callerName,
                number = number,
                appIcon = icon,
                callerPhoto = callerPhoto,
                callType = callType,
                callStartTimeMs = callStart,
                actions = actions,
            )

        val current = _callEvents.value.toMutableList()
        current.removeAll { it.sbn.key == sbn.key }
        current.add(0, event)
        _callEvents.value = current
    }

    private fun actionIconResName(action: Notification.Action, pkg: String): String? {
        val icon = action.getIcon() ?: return null
        val resId = try { icon.resId } catch (_: Exception) { 0 }
        if (resId == 0) return null
        return try {
            context.packageManager.getResourcesForApplication(pkg).getResourceEntryName(resId)
        } catch (_: Exception) {
            null
        }
    }

    private fun actionIntentString(action: Notification.Action): String? {
        val pi = action.actionIntent ?: return null
        return try {
            pi.intent?.action
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRecorderElapsedMs(extras: Bundle): Long? {
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return null
        val match = Regex("""(\d+):(\d+)(?::(\d+))?""").find(text) ?: return null
        val a = match.groupValues[1].toLongOrNull() ?: return null
        val b = match.groupValues[2].toLongOrNull() ?: return null
        val c = match.groupValues[3].toLongOrNull()
        val secs = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
        return secs * 1000L
    }

    private enum class MaterialIconSet(val patterns: List<String>) {
        Play(listOf("play_arrow", "media_play", "play_circle", "play")),
        Pause(listOf("pause_circle", "media_pause", "pause")),
        Stop(listOf("stop_circle", "media_stop", "stop")),
    }

    private object DeskClockActions {
        private val PREFIXES =
            listOf(
                "com.android.deskclock.action.",
                "com.google.android.deskclock.action.",
            )

        fun matches(action: String, name: String): Boolean =
            PREFIXES.any { action == it + name } || action.endsWith(".action.$name")

        fun isTimerAction(action: String): Boolean =
            matches(action, "START_TIMER") ||
                matches(action, "PAUSE_TIMER") ||
                matches(action, "RESET_TIMER") ||
                matches(action, "ADD_MINUTE_TIMER") ||
                matches(action, "SHOW_TIMER")

        fun isStopwatchAction(action: String): Boolean =
            matches(action, "START_STOPWATCH") ||
                matches(action, "PAUSE_STOPWATCH") ||
                matches(action, "RESET_STOPWATCH") ||
                matches(action, "LAP_STOPWATCH") ||
                matches(action, "SHOW_STOPWATCH")
    }

    private fun String.matchesMaterial(set: MaterialIconSet): Boolean =
        set.patterns.any { this.contains(it) }

    private fun isPromotable(sbn: StatusBarNotification, extras: Bundle): Boolean {
        val notification = sbn.notification ?: return false

        if (notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0) return true

        return notification.hasPromotableCharacteristics()
    }

    private fun handlePromotedOngoing(sbn: StatusBarNotification, extras: Bundle, pkg: String) {
        if (sbn.key in dismissedPromotedKeys) return
        val shortCritical =
            try {
                sbn.notification?.shortCriticalText?.toString() ?: ""
            } catch (_: Exception) {
                extras.getString("android.shortCriticalText") ?: ""
            }
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val appName = resolveAppName(pkg)
        val icon = loadNotificationIcon(sbn, pkg)

        val actions = extractNotificationActions(sbn)

        val progressRaw = extras.getInt("android.progress", -1)
        val progressMax = extras.getInt("android.progressMax", 0)
        val indeterminate = extras.getBoolean("android.progressIndeterminate", false)
        val progress =
            if (progressRaw >= 0 && progressMax > 0)
                (progressRaw.toFloat() / progressMax.toFloat()).coerceIn(0f, 1f)
            else -1f

        val event =
            IslandEvent.PromotedOngoing(
                shortText = shortCritical,
                title = title,
                text = text,
                appName = appName,
                appIcon = icon,
                sbn = sbn,
                actions = actions,
                progress = progress,
                isIndeterminate = indeterminate,
            )

        val current = _promotedOngoingEvents.value.toMutableList()
        current.removeAll { it.sbn.key == sbn.key }
        current.add(0, event)
        _promotedOngoingEvents.value = current
    }

    /** Hides the ongoing notification until it is removed or stops being ongoing. */
    fun clearPromotedOngoing(key: String) {
        dismissedPromotedKeys.add(key)
        _promotedOngoingEvents.value = _promotedOngoingEvents.value.filter { it.sbn.key != key }
    }

    fun clearSportsEvent(key: String, team1: String? = null, team2: String? = null) {
        val matching =
            _sportsEvents.value.filter { e ->
                e.key == key ||
                    (team1 != null && team2 != null &&
                        e.team1Name.equals(team1, ignoreCase = true) &&
                        e.team2Name.equals(team2, ignoreCase = true))
            }
        matching.forEach { dismissedSportsKeys.add(it.key) }
        _sportsEvents.value = _sportsEvents.value.filter { e -> matching.none { it.key == e.key } }
    }

    fun clearNowPlaying() {
        _nowPlayingEvent.value = null
    }

    private fun handleNowPlaying(sbn: StatusBarNotification, extras: Bundle) {
        val title = extras.getCharSequence("android.title")?.toString() ?: return
        val byMatch = Regex("""(.+?)\s+by\s+(.+)""", RegexOption.IGNORE_CASE).find(title)
        val dashParts = if (byMatch == null) title.split(" - ", " – ", limit = 2) else null
        val songTitle: String
        val artist: String
        when {
            byMatch != null -> {
                songTitle = byMatch.groupValues[1].trim()
                artist = byMatch.groupValues[2].trim()
            }
            dashParts != null && dashParts.size == 2 -> {
                songTitle = dashParts[0].trim()
                artist = dashParts[1].trim()
            }
            else -> {
                songTitle = title
                artist = ""
            }
        }

        val allActions = sbn.notification?.actions ?: emptyArray()
        val notifActions = allActions.mapNotNull { a ->
            a.title?.let { IslandEvent.NotificationAction(label = it, action = a) }
        }
        val appIcon = loadNotificationIcon(sbn, sbn.packageName)
        // Prefer existing art on same-song re-post so we don't flash back to the note.
        val previous = _nowPlayingEvent.value
        val sameSong = previous != null
            && previous.songTitle == songTitle
            && previous.artist == artist
        val largeIcon = try {
            sbn.notification?.getLargeIcon()?.loadDrawable(context)
        } catch (_: Exception) {
            null
        }
        val albumArt = largeIcon
            ?: if (sameSong) previous?.albumArt else null

        _nowPlayingEvent.value = IslandEvent.NowPlaying(
            songTitle = songTitle,
            artist = artist,
            key = sbn.key,
            sbn = sbn,
            appIcon = appIcon,
            albumArt = albumArt,
            actions = notifActions,
        )

        if (albumArt == null && songTitle.isNotBlank()) {
            val searchTerm = listOf(songTitle, artist).filter { it.isNotBlank() }.joinToString(" ")
            val eventKey = sbn.key
            applicationScope.launch {
                val art = loadItunesAlbumArt(searchTerm) ?: return@launch
                val current = _nowPlayingEvent.value
                if (current != null
                    && current.key == eventKey
                    && current.songTitle == songTitle
                    && current.artist == artist
                    && current.albumArt == null) {
                    _nowPlayingEvent.value = current.copy(albumArt = art)
                    Log.i(TAG, "Now Playing album art applied via iTunes for $songTitle")
                }
            }
        }
    }

    /**
     * Lightweight cover lookup when ASI omits ALBUM_ART_URI / largeIcon on non-Pixel ports.
     */
    private suspend fun loadItunesAlbumArt(term: String): Drawable? = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext null
        try {
            val encoded = URLEncoder.encode(term, StandardCharsets.UTF_8.name())
            val searchUrl = ITUNES_SEARCH_URL.format(encoded)
            val json = httpGetString(searchUrl) ?: return@withContext null
            val results = JSONObject(json).optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            val artwork = results.getJSONObject(0).optString("artworkUrl100")
                .ifBlank { return@withContext null }
                .replace("100x100bb", "300x300bb")
            val bytes = httpGetBytes(artwork) ?: return@withContext null
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext null
            BitmapDrawable(context.resources, bmp)
        } catch (e: Exception) {
            Log.w(TAG, "iTunes album art failed for '$term'", e)
            null
        }
    }

    private fun httpGetString(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = ART_HTTP_CONNECT_MS
                readTimeout = ART_HTTP_READ_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RisingOS-DynamicBar-NowPlaying")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpGetBytes(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = ART_HTTP_CONNECT_MS
                readTimeout = ART_HTTP_READ_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RisingOS-DynamicBar-NowPlaying")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun handleSportsScore(
        sbn: StatusBarNotification,
        extras: Bundle,
        forceCapture: Boolean = false,
    ): Boolean {
        val title = extras.getCharSequence("android.title")?.toString() ?: return false
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        val allFields = listOf(title.trim(), text.trim())
        val scoreMatch = allFields.firstNotNullOfOrNull { SCORE_PATTERN.find(it) }

        var team1Name: String
        var team2Name: String
        var score1 = ""
        var score2 = ""

        if (scoreMatch != null) {
            team1Name = scoreMatch.groupValues[1].trim()
            score1 = scoreMatch.groupValues[2]
            score2 = scoreMatch.groupValues[3]
            team2Name = scoreMatch.groupValues[4].trim()
                .replace(Regex("""\s*[·•|].*"""), "")
        } else {
            val combined = "$title $text"
            val vsMatch = VS_PATTERN.find(combined)
            if (vsMatch != null) {
                team1Name = vsMatch.groupValues[1].trim()
                    .replace(Regex("""^.*[·•|]\s*"""), "")
                team2Name = vsMatch.groupValues[2].trim()
                    .replace(Regex("""\s*[·•|].*$"""), "")
            } else if (forceCapture) {
                val parts = text.split("·", "•", "|", " - ", " – ")
                    .map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    team1Name = parts[0]
                    team2Name = parts[1]
                } else {
                    team1Name = text.ifEmpty { title }
                    team2Name = ""
                }
            } else {
                return false
            }
        }

        val isOngoing = sbn.isOngoing
        val status = when {
            score1.isNotEmpty() && !isOngoing -> IslandEvent.GameStatus.FINAL
            score1.isNotEmpty() && isOngoing -> IslandEvent.GameStatus.LIVE
            isOngoing -> IslandEvent.GameStatus.PRE_GAME
            else -> IslandEvent.GameStatus.FINAL
        }

        var statusDetail = ""
        if (score1.isNotEmpty()) {
            val shortCritical = try {
                sbn.notification?.shortCriticalText?.toString() ?: ""
            } catch (_: Exception) {
                extras.getString("android.shortCriticalText") ?: ""
            }
            statusDetail = shortCritical
                .replace(score1, "").replace(score2, "")
                .replace("-", "").replace("–", "").replace(":", "")
                .trim()
        }

        val allText = "$title · $text"
        val league = allText.split("·", "•", "|")
            .map { it.trim() }
            .firstOrNull { part ->
                part.length in 2..30 &&
                    !part.any { it.isDigit() } &&
                    part != team1Name && part != team2Name
            } ?: ""

        val commentary = extras.getCharSequence("android.bigText")?.toString()
            ?: extras.getString("android.subText") ?: ""

        val appIcon = loadNotificationIcon(sbn, sbn.packageName)

        val event = IslandEvent.Sports(
            team1Name = team1Name,
            team2Name = team2Name,
            score1 = score1,
            score2 = score2,
            status = status,
            statusDetail = statusDetail,
            league = league,
            commentary = commentary,
            key = sbn.key,
            sbn = sbn,
            appIcon = appIcon,
        )

        if (sbn.key in dismissedSportsKeys) return true

        val current = _sportsEvents.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        current.add(0, event)
        _sportsEvents.value = current
        return true
    }

    private fun extractNotificationImage(extras: Bundle, sbn: StatusBarNotification): Drawable? {
        try {
            extras.getParcelable(Notification.EXTRA_PICTURE_ICON, Icon::class.java)
                ?.loadDrawable(context)?.let { return it }
        } catch (_: Exception) {}

        try {
            extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
                ?.let { return BitmapDrawable(context.resources, it) }
        } catch (_: Exception) {}

        if (!sbn.notification.isStyle(Notification.MessagingStyle::class.java)) {
            try {
                sbn.notification?.getLargeIcon()?.loadDrawable(context)?.let { return it }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun loadNotificationIcon(sbn: StatusBarNotification, pkg: String): Drawable? {
        return try {
            sbn.notification?.smallIcon?.loadDrawable(context)
        } catch (_: Exception) {
            null
        }
            ?: try {
                context.packageManager.getApplicationIcon(pkg)
            } catch (_: Exception) {
                null
            }
    }

    private fun contentSignature(sbn: StatusBarNotification, extras: Bundle): Long {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val lastLine = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.lastOrNull()?.toString()
        return 31L * sbn.notification.`when` + Objects.hash(title, text, lastLine)
    }

    private fun isFromSelf(msg: Notification.MessagingStyle.Message, extras: Bundle): Boolean {
        val sender = msg.senderPerson ?: return true   // null sender == the device user
        val self = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        return self != null && (
            (self.key != null && self.key == sender.key) ||
                (self.key == null && self.name != null && self.name == sender.name)
        )
    }
}
