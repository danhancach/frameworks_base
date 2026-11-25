package com.android.systemui.axdynamicbar.domain

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings.Global
import android.util.Log
import com.android.systemui.axdynamicbar.data.IslandEventRepository
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.IslandState
import com.android.systemui.axdynamicbar.model.IslandUiState
import com.android.systemui.axdynamicbar.model.RecordingState
import com.android.systemui.axdynamicbar.shared.DESK_CLOCK_TAB_ACTION
import com.android.systemui.axdynamicbar.shared.DESK_CLOCK_TAB_EXTRA_PACKAGE
import com.android.systemui.axdynamicbar.shared.DESK_CLOCK_TAB_EXTRA_SELECTED_TAB
import com.android.systemui.axdynamicbar.shared.DeskClockSelectedTab
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.shared.StatusBarMediaTimeout
import com.android.systemui.axdynamicbar.shared.deskClockSelectedTabFromOrdinal
import com.android.systemui.axdynamicbar.shared.isDeskClockPackage
import com.android.systemui.axdynamicbar.shared.islandEventShouldHideForForeground
import com.android.systemui.axdynamicbar.shared.islandEventSupportsForegroundHide
import com.android.systemui.axdynamicbar.shared.sendWithBal
import com.android.systemui.axdynamicbar.shared.statusBarAppPillEventKindOf
import com.android.systemui.axdynamicbar.shared.statusBarAppPillFgPkgToken
import com.android.systemui.axdynamicbar.shared.statusBarAppPillLogUiOwner
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.statusbar.StatusBarState
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.policy.ZenModeController
import com.android.systemui.util.settings.GlobalSettings
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SysUISingleton
class AxDynamicBarInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    private val repository: IslandEventRepository,
    val settings: AxDynamicBarSettings,
    private val statusBarStateController: StatusBarStateController,
    private val keyguardStateController: KeyguardStateController,
    val sliderHapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val activityStarter: ActivityStarter,
    private val indicationController: KeyguardIndicationController,
    private val shadeInteractor: ShadeInteractor,
    private val shadeRepository: ShadeRepository,
    private val zenModeController: ZenModeController,
    private val globalSettings: GlobalSettings,
    private val audioManager: AudioManager,
) : IslandActions {
    private val _uiState = MutableStateFlow(IslandUiState())
    val uiState: StateFlow<IslandUiState> = _uiState.asStateFlow()

    private val autoDismissJobs = ConcurrentHashMap<String, Job>()
    @Volatile private var notifAlertJob: Job? = null
    @Volatile private var alertHeld = false
    @Volatile private var alertReplyActive = false
    @Volatile private var overlayImeVisible = false
    @Volatile private var lastOutsideDismissElapsedMs = 0L
    @Volatile private var pendingMorphDismissAlertKey: String? = null
    /** Tab cuoi cua Google/AOSP Clock nhan tu SharedPreferencesImpl. */
    @Volatile private var lastDeskClockTab: DeskClockSelectedTab? = null
    @Volatile private var pendingDeskClockExitTab: DeskClockSelectedTab? = null
    @Volatile private var pendingDeskClockExitDeadlineMs = 0L

    private val deskClockTabReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != DESK_CLOCK_TAB_ACTION) return
                val pkg = intent.getStringExtra(DESK_CLOCK_TAB_EXTRA_PACKAGE)
                if (!isDeskClockPackage(pkg)) return
                val tabOrdinal = intent.getIntExtra(DESK_CLOCK_TAB_EXTRA_SELECTED_TAB, -1)
                if (tabOrdinal < 0) return
                lastDeskClockTab = deskClockSelectedTabFromOrdinal(tabOrdinal)
            }
        }

    private val dismissedEventIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val statusBarMediaTimeout = StatusBarMediaTimeout()
    private val statusBarMediaTimeoutLock = Any()
    private val expiredStatusBarMediaIds = MutableStateFlow<Set<String>>(emptySet())

    override var onFocusableRequested: ((Boolean) -> Unit)? = null

    var onCollapseRequested: (() -> Unit)? = null

    var onKeyguardCollapseRequested: (() -> Unit)? = null

    var onFadeOutForAppLaunchRequested: ((String?) -> Unit)? = null

    var onAlertMorphCollapseRequested: (() -> Unit)? = null

    var onAlertMorphImmediateDismissRequested: (() -> Unit)? = null

    var onAlertReplyCaptureRequested: ((Boolean) -> Unit)? = null

    /** Compact: timeout + touchable dung the; Expanded: soft-max + tap ngoai dismiss. */
    private val _alertCompactMode = MutableStateFlow(true)
    val alertCompactMode: StateFlow<Boolean> = _alertCompactMode.asStateFlow()

    private var isInitialized = false

    private val _isPanelExpanded = MutableStateFlow(false)
    
    val isPanelExpanded: StateFlow<Boolean> = _isPanelExpanded.asStateFlow()
    
    val qsExpansion: StateFlow<Float> = shadeInteractor.qsExpansion
    
    val legacyShadeExpansion: StateFlow<Float> = shadeRepository.legacyShadeExpansion
    val mediaUseWaveform: StateFlow<Boolean> = settings.useWaveformSeekBar
    private val _isOnKeyguard = MutableStateFlow(false)
    val isOnKeyguard: StateFlow<Boolean> = _isOnKeyguard.asStateFlow()
    private val _isKeyguardFadingAway = MutableStateFlow(false)
    val isKeyguardFadingAway: StateFlow<Boolean> = _isKeyguardFadingAway.asStateFlow()
    private val _isBouncerShowing = MutableStateFlow(false)
    val isBouncerShowing: StateFlow<Boolean> = _isBouncerShowing.asStateFlow()
    private val _isDozing = MutableStateFlow(statusBarStateController.isDozing)
    val isDozing: StateFlow<Boolean> = _isDozing.asStateFlow()
    private val _dozeAmount = MutableStateFlow(0f)
    
    val dozeAmount: StateFlow<Float> = _dozeAmount.asStateFlow()
    @Volatile private var isDreaming = false

    private val statusBlocking: Boolean
        get() = _isDozing.value || isDreaming

    companion object {
        private const val TAG = "AxDynamicBarInteractor"
        private const val NOTIF_ALERT_DURATION_MS = 5500L
        /** Tran tap ngoai / ACTION_OUTSIDE — tranh 1 gesture nuot ca stack. */
        private const val OUTSIDE_DISMISS_DEBOUNCE_MS = 480L

        private fun IslandEvent.Notification.isActiveCall(): Boolean {
            val extras = sbn.notification?.extras ?: return false
            return extras.containsKey(Notification.EXTRA_ANSWER_INTENT) ||
                extras.containsKey(Notification.EXTRA_DECLINE_INTENT) ||
                extras.containsKey(Notification.EXTRA_HANG_UP_INTENT)
        }
    }

    fun init() {
        if (isInitialized) return
        isInitialized = true

        settings.init()
        try {
            context.registerReceiverAsUser(
                deskClockTabReceiver,
                UserHandle.ALL,
                IntentFilter(DESK_CLOCK_TAB_ACTION),
                /* broadcastPermission = */ null,
                /* scheduler = */ null,
                Context.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Khong dang ky duoc receiver tab Clock", e)
        }

        repository.system.onChargingStarted = { scheduleAutoDismiss(it) }
        repository.system.onRingerChanged = { scheduleAutoDismiss(it) }
        repository.system.onClipboardCopied = { scheduleAutoDismiss(it) }

        repository.notification.activeMediaPackageProvider = { repository.media.activeMediaPackages }
        // Clock owner-gap: so FG de KEEP/DROP + leave-grace.
        repository.notification.foregroundPackageProvider = {
            repository.appTracking.foregroundPackage.value
        }
        repository.notification.onAlarmEvent = {
            scheduleAutoDismiss(it, if (it.isRinging) 30_000L else 5_000L)
        }

        repository.biometric.onBiometricUnlock = { scheduleAutoDismiss(it) }

        applicationScope.launch {
            repository.notification.audioRecordingEvent.collect { event ->
                if (event?.state == RecordingState.SAVED) {
                    scheduleAutoDismiss(event, 5_000L)
                }
            }
        }

        applicationScope.launch {
            repository.notification.notificationFlow.collect { notification ->
                // The manager is shared with the dynamic island, which can have it listening
                // while the bar is off. The bar's own notification events are then not wanted,
                // and the heads up is not suppressed, so showing the card would overlap it.
                if (!settings.isNotificationEventsActive()) return@collect
                if (!repository.notification.coalesceNotification(notification)) return@collect
                showNotificationAlert(notification)
            }
        }

        applicationScope.launch {
            repository.notification.notificationIconRefreshFlow.collect { refresh ->
                if (!settings.isNotificationEventsActive()) return@collect
                // Chi cap nhat icon alert dang mo tai cho neu trung key.
                // KHONG goi coalesceNotification de tranh reorder list hoac hoi sinh notification da dismiss.
                refreshOpenNotificationAlertIcon(refresh.key, refresh.icon, refresh.iconRevision)
            }
        }

        applicationScope.launch {
            repository.notification.notificationRemovedFlow.collect { key ->
                removeNotificationAlertByKey(key)
            }
        }

        applicationScope.launch {
            combine(
                _uiState.map { state ->
                    state.shouldShow &&
                        state.events.any { it is IslandEvent.Media && it.isPlaying && it.duration > 0L }
                },
                _isPanelExpanded,
                qsExpansion.map { it > 0f },
            ) { mediaActive, panelExpanded, qsOpen ->
                mediaActive && !panelExpanded && !qsOpen
            }.distinctUntilChanged().collect { needsPolling ->
                if (needsPolling) repository.media.startProgressPolling()
                else repository.media.stopProgressPolling()
            }
        }

        _isOnKeyguard.value = statusBarStateController.state == StatusBarState.KEYGUARD
        _isKeyguardFadingAway.value = keyguardStateController.isKeyguardFadingAway
        _isBouncerShowing.value = keyguardStateController.isPrimaryBouncerShowing

        keyguardStateController.addCallback(
            object : KeyguardStateController.Callback {
                override fun onKeyguardFadingAwayChanged() {
                    _isKeyguardFadingAway.value = keyguardStateController.isKeyguardFadingAway
                }

                override fun onPrimaryBouncerShowingChanged() {
                    _isBouncerShowing.value = keyguardStateController.isPrimaryBouncerShowing
                }
            }
        )

        statusBarStateController.addCallback(
            object : StatusBarStateController.StateListener {
                override fun onExpandedChanged(isExpanded: Boolean) {
                    onPanelExpandedChanged(isExpanded)
                }

                override fun onStateChanged(newState: Int) {
                    _isOnKeyguard.value = newState == StatusBarState.KEYGUARD
                    updateChipVisibility()
                }

                override fun onDozingChanged(dozing: Boolean) {
                    _isDozing.value = dozing
                    updateChipVisibility()
                }

                override fun onDozeAmountChanged(linear: Float, eased: Float) {
                    _dozeAmount.value = linear
                }

                override fun onDreamingChanged(dreaming: Boolean) {
                    isDreaming = dreaming
                    updateChipVisibility()
                }
            }
        )

        indicationController.addIndicationListener { type, text ->
            val indicationType = mapIndicationType(type) ?: return@addIndicationListener
            if (indicationType == IslandEvent.KeyguardIndication.IndicationType.BIOMETRIC &&
                !settings.isKeyguardBiometricUnlockEventsActive()) {
                return@addIndicationListener
            }
            if (text != null && text.isNotEmpty()) {
                val event = IslandEvent.KeyguardIndication(
                    text = text.toString(),
                    indicationType = indicationType,
                )
                repository.updateIndicationEvent(event)
                scheduleAutoDismiss(event)
            } else {
                repository.clearIndicationEvent(indicationType)
            }
        }

        applicationScope.launch {
            settings.isEnabled.collect { enabled ->
                if (enabled) repository.startListening()
                else {
                    repository.stopListening()
                    dismissAllNotificationAlertsImmediate(notifyExpansion = false)
                    repository.media.stopProgressPolling()
                    autoDismissJobs.values.forEach { it.cancel() }
                    autoDismissJobs.clear()
                    dismissedEventIds.clear()
                    synchronized(statusBarMediaTimeoutLock) {
                        statusBarMediaTimeout.clear()
                    }
                    expiredStatusBarMediaIds.value = emptySet()
                    pendingDeskClockExitTab = null
                    pendingDeskClockExitDeadlineMs = 0L
                    repository.clearAllIndicationEvents()
                    _uiState.value = IslandUiState()
                }
            }
        }

        applicationScope.launch {
            settings.disabledEventTypes.collect {
                repository.refreshListeners()
            }
        }

        applicationScope.launch {
            combine(
                repository.media.mediaEvents.map { events ->
                    events.associate { it.id to it.isPlaying }
                }.distinctUntilChanged(),
                // Kiem tra lai thoi gian tam dung khi khoa man hinh/doze (ke ca luc ngu).
                _isOnKeyguard,
                _isDozing,
            ) { playback, _, _ -> playback }.collectLatest { playback ->
                synchronized(statusBarMediaTimeoutLock) {
                    statusBarMediaTimeout.update(playback, SystemClock.elapsedRealtime())
                }
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val (expired, remaining) =
                        synchronized(statusBarMediaTimeoutLock) {
                            statusBarMediaTimeout.expiredIds(now) to
                                statusBarMediaTimeout.nextDelayMs(now)
                        }
                    expiredStatusBarMediaIds.value = expired
                    val delayMs = remaining ?: break
                    delay(delayMs)
                }
            }
        }

        applicationScope.launch {
            combine(
                repository.events,
                settings.disabledEventTypes,
                _isOnKeyguard,
                repository.appTracking.foregroundPackage,
                expiredStatusBarMediaIds,
            ) { raw, _, kg, foregroundPkg, expiredMedia ->
                Triple(raw.filter { settings.isEventEnabled(it) }, kg, foregroundPkg) to expiredMedia
            }.collect { (input, expiredMedia) ->
                val (rawEvents, onKeyguard, foregroundPkg) = input
                if (!settings.isEnabled.value) return@collect
                // Cap nhat reconciliation owner-gap Clock truoc khi tinh suppress/UI.
                repository.notification.onForegroundPackageChanged(foregroundPkg)
                dismissedEventIds.removeAll { id -> rawEvents.none { it.id == id } }
                // Home may precede the combined event update. Consult both the snapshot
                // and source, but retain the event so APP_HIDE/SHOW keeps its composition.
                val liveAwaitingTimerIds =
                    repository.notification.timerEvents.value
                        .asSequence()
                        .filter { it.awaitingOwnerRepost }
                        .map { it.id }
                        .toSet()
                val pendingConfirmationIds = buildSet {
                    for (event in rawEvents) {
                        when (event) {
                            is IslandEvent.Stopwatch ->
                                if (event.awaitingOwnerRepost ||
                                    repository.notification.stopwatchEvent.value
                                        ?.awaitingOwnerRepost == true
                                ) {
                                    add(event.id)
                                }
                            is IslandEvent.Timer ->
                                if (event.awaitingOwnerRepost || event.id in liveAwaitingTimerIds) {
                                    add(event.id)
                                }
                            else -> Unit
                        }
                    }
                }
                // Giu day du event (timer/media timebase + pin); FG-hide = suppress id
                // — khong remove khoi repo / khong coi la dismiss hay owner-end.
                val events = rawEvents.filter { e ->
                    e.id !in dismissedEventIds &&
                        // Keyguard has its own presentation; never expose an unconfirmed run.
                        !(onKeyguard && e.id in pendingConfirmationIds) &&
                        // Dong bo an media tam dung qua han / bi vuot ngang tren ca status bar va keyguard; giu trong QS.
                        !(e is IslandEvent.Media && !e.isPlaying && e.id in expiredMedia) &&
                        !(onKeyguard && e is IslandEvent.Notification) &&
                        !(onKeyguard && e is IslandEvent.Charging) &&
                        !(onKeyguard && e is IslandEvent.AppSwitch) &&
                        !(onKeyguard && e is IslandEvent.Clipboard) &&
                        !(!onKeyguard && e is IslandEvent.KeyguardIndication)
                }
                val fgSuppressedIds =
                    if (onKeyguard || foregroundPkg.isNullOrEmpty()) {
                        emptySet()
                    } else {
                        events
                            .asSequence()
                            .filter {
                                islandEventSupportsForegroundHide(it) &&
                                    islandEventShouldHideForForeground(it, foregroundPkg)
                            }
                            .map { it.id }
                            .toSet()
                    }

                val current = _uiState.value

                val newState =
                    when {
                        events.isEmpty() -> IslandState.HIDDEN
                        statusBlocking -> IslandState.HIDDEN
                        else -> IslandState.CHIP
                    }

                val selection =
                    resolveIslandEventSelection(
                        previousEvents = current.events,
                        previousPinnedIndex = current.pinnedEventIndex,
                        events = events,
                        previousForegroundPackage = current.foregroundPackage,
                        foregroundPackage = foregroundPkg,
                        onKeyguard = onKeyguard,
                        nowElapsedMs = SystemClock.elapsedRealtime(),
                        lastDeskClockTab = lastDeskClockTab,
                        pendingDeskClockExit =
                            DeskClockExitGrace(
                                tab = pendingDeskClockExitTab,
                                deadlineMs = pendingDeskClockExitDeadlineMs,
                            ),
                    )
                pendingDeskClockExitTab = selection.nextDeskClockExitGrace.tab
                pendingDeskClockExitDeadlineMs = selection.nextDeskClockExitGrace.deadlineMs
                // P1B: GIU pinnedEventIndex qua FG suppress tam — khong doi pin user.
                // Visible fallback = IslandUiState.topEvent (loc suppressed); Home van A.
                val pinnedIndex = selection.pinnedIndex
                val shouldReset = selection.shouldResetManuallyHidden

                val next =
                    IslandUiState(
                        events = events,
                        islandState = newState,
                        pinnedEventIndex = pinnedIndex,
                        manuallyHidden = if (shouldReset) false else current.manuallyHidden,
                        forceVisible = false,
                        notificationAlerts = current.notificationAlerts,
                        pinnedAlertIndex = current.pinnedAlertIndex,
                        foregroundSuppressedIds = fgSuppressedIds,
                        pendingConfirmationIds =
                            pendingConfirmationIds.intersect(events.map { it.id }.toSet()),
                        foregroundPackage = foregroundPkg,
                    )
                // Diag delay Home: emit sequence khi FG ownership / suppress doi.
                if (
                    current.foregroundPackage != next.foregroundPackage ||
                        current.foregroundSuppressedIds != next.foregroundSuppressedIds ||
                        current.pendingConfirmationIds != next.pendingConfirmationIds
                ) {
                    val ownerHidden =
                        next.visuallySuppressedIds.isNotEmpty() &&
                            next.visibleEvents.isEmpty() &&
                            next.notificationAlert == null &&
                            next.events.any { it.id in next.visuallySuppressedIds }
                    statusBarAppPillLogUiOwner(
                        cause = "interactor.emit",
                        ownerHidden = ownerHidden,
                        suppressedCount = next.visuallySuppressedIds.size,
                        fgToken = statusBarAppPillFgPkgToken(next.foregroundPackage),
                        eventKind = statusBarAppPillEventKindOf(next.topEvent),
                    )
                }
                _uiState.value = next

            }
        }
    }

    fun cycleNext() {
        pendingDeskClockExitTab = null
        pendingDeskClockExitDeadlineMs = 0L
        val current = _uiState.value
        if (current.events.size <= 1) return
        val next = (current.pinnedEventIndex + 1) % current.events.size
        _uiState.value = current.copy(pinnedEventIndex = next)
    }

    fun cyclePrev() {
        pendingDeskClockExitTab = null
        pendingDeskClockExitDeadlineMs = 0L
        val current = _uiState.value
        if (current.events.size <= 1) return
        val prev = (current.pinnedEventIndex - 1 + current.events.size) % current.events.size
        _uiState.value = current.copy(pinnedEventIndex = prev)
    }

    fun pinEventAt(index: Int) {
        pendingDeskClockExitTab = null
        pendingDeskClockExitDeadlineMs = 0L
        val current = _uiState.value
        if (index < 0 || index >= current.events.size) return
        _uiState.value = current.copy(pinnedEventIndex = index)
    }

    override fun pinEventById(eventId: String) {
        val idx = _uiState.value.events.indexOfFirst { it.id == eventId }
        if (idx >= 0) pinEventAt(idx)
    }

    override fun pinNotificationAlertByKey(key: String) {
        val current = _uiState.value
        val idx = current.notificationAlerts.indexOfFirst { it.sbn.key == key }
        if (idx < 0) return
        _uiState.value = current.copy(pinnedAlertIndex = idx)
        // Doi the stack → top moi Expanded, khong timeout.
        _alertCompactMode.value = false
        notifAlertJob?.cancel()
        notifAlertJob = null
    }

    override fun dismissEvent(event: IslandEvent) {
        autoDismissJobs[event.id]?.cancel()
        autoDismissJobs.remove(event.id)
        if (event.behavior.suppressOnDismiss) {
            dismissedEventIds.add(event.id)
        }

        val current = _uiState.value
        val updatedEvents = current.events.filter { it.id != event.id }
        val newIndex =
            current.pinnedEventIndex.coerceAtMost((updatedEvents.size - 1).coerceAtLeast(0))

        _uiState.value =
            current.copy(
                events = updatedEvents,
                pinnedEventIndex = newIndex,
                islandState =
                    if (updatedEvents.isEmpty()) IslandState.HIDDEN else current.islandState,
            )

        when (event) {
            is IslandEvent.AudioRecording ->
                if (event.state == RecordingState.SAVED) {
                    repository.notification.clearAudioRecording()
                } else {
                    repository.notification.dismissAudioRecording()
                }
            is IslandEvent.Sports -> {
                repository.smartspace.clearSportsEvent(event.key, event.team1Name, event.team2Name)
                repository.notification.clearSportsEvent(event.key, event.team1Name, event.team2Name)
            }
            is IslandEvent.NowPlaying -> {
                repository.smartspace.dismissNowPlaying()
                repository.notification.clearNowPlaying()
            }
            is IslandEvent.PromotedOngoing ->
                repository.notification.clearPromotedOngoing(event.sbn.key)
            is IslandEvent.Media -> repository.media.clear(event.mediaKey)
            is IslandEvent.Bluetooth -> repository.connectivity.clearBluetooth()
            is IslandEvent.Hotspot -> repository.connectivity.clearHotspot()
            is IslandEvent.Charging -> repository.system.clearCharging()
            is IslandEvent.Alarm -> repository.notification.clearAlarm()
            is IslandEvent.Call -> repository.notification.clearCall(event.sbn.key)
            is IslandEvent.Timer ->
                repository.notification.clearTimer(event.notificationKey)
            is IslandEvent.Stopwatch -> repository.notification.clearStopwatch()
            is IslandEvent.RingerMode -> repository.system.clearRinger()
            is IslandEvent.Vpn -> repository.connectivity.clearVpn()
            is IslandEvent.Clipboard -> repository.system.clearClipboard()
            is IslandEvent.Notification -> repository.notification.dismissNotification(event)
            is IslandEvent.AppSwitch -> repository.appTracking.clear()
            is IslandEvent.Torch -> {
                if (repository.torch.isEnabled) {
                    repository.torch.toggleTorch()
                }
                repository.torch.clear()
            }
            is IslandEvent.BiometricUnlock -> repository.biometric.clear()
            is IslandEvent.KeyguardIndication -> repository.clearIndicationEvent(event.indicationType)
            is IslandEvent.AospChip -> {}
        }
    }

    override fun dismissEventFromExpandedSwipe(event: IslandEvent) {
        if (event is IslandEvent.Media) {
            val liveMedia =
                repository.media.mediaEvents.value.firstOrNull { it.id == event.id }
            // Chi an ngay khi media dang tam dung; giu phien trong QS, dong bo an sang keyguard.
            if (event.isPlaying || liveMedia?.isPlaying == true) {
                collapseIsland()
                return
            }
            hidePausedMediaFromSwipe(event)
        } else {
            dismissEvent(event)
        }
        // Stack mo rong = event khong notif / khong AospChip. Con the trong stack
        // thi thu overlay. Rong hoac chi Aosp: flow terminal / LaunchedEffect xu ly.
        val stackRemains =
            _uiState.value.events.any {
                it !is IslandEvent.Notification && it !is IslandEvent.AospChip
            }
        if (stackRemains) collapseIsland()
    }

    private fun hidePausedMediaFromSwipe(event: IslandEvent.Media) {
        val now = SystemClock.elapsedRealtime()
        val expired =
            synchronized(statusBarMediaTimeoutLock) {
                statusBarMediaTimeout.expireNow(event.id, now)
                statusBarMediaTimeout.expiredIds(now)
            }
        expiredStatusBarMediaIds.value = expired

        val current = _uiState.value
        val updatedEvents = current.events.filter { it.id != event.id }
        val newIndex =
            current.pinnedEventIndex.coerceAtMost((updatedEvents.size - 1).coerceAtLeast(0))
        _uiState.value =
            current.copy(
                events = updatedEvents,
                pinnedEventIndex = newIndex,
                islandState =
                    if (updatedEvents.isEmpty()) IslandState.HIDDEN else current.islandState,
            )
    }

    fun getTopEvent(): IslandEvent? = _uiState.value.topEvent

    override fun collapseIsland() {
        if (_isOnKeyguard.value) {
            onKeyguardCollapseRequested?.invoke() ?: onCollapseRequested?.invoke()
        } else {
            onCollapseRequested?.invoke()
        }
    }

    override fun collapseIslandForAppLaunch(launchingPackage: String?) {
        if (_isOnKeyguard.value) {
            onKeyguardCollapseRequested?.invoke() ?: onCollapseRequested?.invoke()
        } else {
            onFadeOutForAppLaunchRequested?.invoke(launchingPackage)
                ?: onCollapseRequested?.invoke()
        }
    }

    override fun onNotificationInteraction(eventId: String) {
        autoDismissJobs[eventId]?.cancel()
        autoDismissJobs.remove(eventId)
    }

    override fun onNotificationInteractionEnd(eventId: String) {
        val event = _uiState.value.events.find { it.id == eventId } ?: return
        scheduleAutoDismiss(event)
    }

    override fun onNotificationAlertInteractionStart() {
        alertHeld = true
        notifAlertJob?.cancel()
        notifAlertJob = null
    }

    override fun onNotificationAlertInteractionEnd() {
        alertHeld = false
        // Chi Compact moi auto-timeout sau khi tha tay.
        scheduleCompactAlertTimeout()
    }

    override fun onNotificationAlertCompactChanged(compact: Boolean) {
        if (_alertCompactMode.value == compact) return
        _alertCompactMode.value = compact
        if (!compact) {
            // Mo rong: huy timeout — chi an khi user dismiss.
            notifAlertJob?.cancel()
            notifAlertJob = null
            return
        }
        // Quay Compact: schedule lai neu dang hien alert.
        if (alertHeld) return
        scheduleCompactAlertTimeout()
    }

    private fun scheduleCompactAlertTimeout() {
        val alert = _uiState.value.notificationAlert ?: return
        if (alert.isActiveCall() || !_alertCompactMode.value) return
        notifAlertJob?.cancel()
        notifAlertJob =
            applicationScope.launch {
                delay(NOTIF_ALERT_DURATION_MS)
                dismissNotificationAlert()
            }
    }

    override fun onAlertReplyActiveChanged(active: Boolean) {
        val newActive = active && _uiState.value.notificationAlert != null
        if (newActive == alertReplyActive) return
        alertReplyActive = newActive
        onAlertReplyCaptureRequested?.invoke(newActive)
    }

    fun onOverlayTouchOutsideCard() {
        if (alertReplyActive) abandonAlertReply()
    }

    fun onOverlayImeVisibilityChanged(visible: Boolean) {
        val wasVisible = overlayImeVisible
        overlayImeVisible = visible
        if (wasVisible && !visible && alertReplyActive) abandonAlertReply()
    }

    private fun abandonAlertReply() {
        if (_uiState.value.notificationAlert == null) {
            alertReplyActive = false
            onAlertReplyCaptureRequested?.invoke(false)
            return
        }
        dismissNotificationAlert()
    }

    /**
     * Tap / ACTION_OUTSIDE ngoai the — chi an **top** 1 lan / debounce.
     * Tranh 1 cu cham (clickable + OUTSIDE hoac region doi) nuot ca hang doi.
     */
    fun dismissNotificationAlertFromOutside() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOutsideDismissElapsedMs < OUTSIDE_DISMISS_DEBOUNCE_MS) return
        lastOutsideDismissElapsedMs = now
        dismissNotificationAlert()
    }

    internal fun hasPendingAlertMorphDismiss(eventId: String): Boolean {
        val key = pendingMorphDismissAlertKey ?: return false
        return eventId == "notification_$key"
    }

    private fun clearAlertReplyAndHold() {
        alertHeld = false
        if (alertReplyActive) {
            alertReplyActive = false
            onFocusableRequested?.invoke(false)
            onAlertReplyCaptureRequested?.invoke(false)
        }
        notifAlertJob?.cancel()
        notifAlertJob = null
    }

    private fun removeTopAlertFromState(): Boolean {
        clearAlertReplyAndHold()
        val current = _uiState.value
        val alerts = current.notificationAlerts
        if (alerts.isEmpty()) {
            _alertCompactMode.value = true
            return false
        }
        val pinned = current.pinnedAlertIndex.coerceIn(0, alerts.lastIndex)
        val remaining = alerts.toMutableList().also { it.removeAt(pinned) }
        if (remaining.isEmpty()) {
            _alertCompactMode.value = true
            _uiState.value =
                current.copy(notificationAlerts = emptyList(), pinnedAlertIndex = 0)
            return false
        }
        // Promote under -> top Expanded, khong ep timeout.
        val newPinned = pinned.coerceAtMost(remaining.lastIndex)
        _alertCompactMode.value = false
        _uiState.value =
            current.copy(notificationAlerts = remaining, pinnedAlertIndex = newPinned)
        return true
    }

    fun dismissNotificationAlert() {
        val current = _uiState.value
        val alerts = current.notificationAlerts
        if (alerts.isEmpty()) {
            pendingMorphDismissAlertKey = null
            _alertCompactMode.value = true
            return
        }
        if (alerts.size > 1) {
            pendingMorphDismissAlertKey = null
            removeTopAlertFromState()
            return
        }
        val canMorphClose =
            onAlertMorphCollapseRequested != null &&
                !_isOnKeyguard.value &&
                !_isPanelExpanded.value &&
                qsExpansion.value <= 0f &&
                legacyShadeExpansion.value <= 0f &&
                settings.isEnabled.value
        if (!canMorphClose) {
            dismissNotificationAlertImmediate(notifyExpansion = true)
            return
        }
        clearAlertReplyAndHold()
        pendingMorphDismissAlertKey = alerts.first().sbn.key
        onAlertMorphCollapseRequested?.invoke()
    }

    fun dismissNotificationAlertImmediate(notifyExpansion: Boolean = true) {
        pendingMorphDismissAlertKey = null
        val hasRemaining = removeTopAlertFromState()
        if (!hasRemaining && notifyExpansion) {
            onAlertMorphImmediateDismissRequested?.invoke()
        }
    }

    fun dismissAllNotificationAlertsImmediate(notifyExpansion: Boolean = true) {
        pendingMorphDismissAlertKey = null
        clearAlertReplyAndHold()
        _alertCompactMode.value = true
        val current = _uiState.value
        if (current.notificationAlerts.isNotEmpty()) {
            _uiState.value =
                current.copy(notificationAlerts = emptyList(), pinnedAlertIndex = 0)
        }
        if (notifyExpansion) {
            onAlertMorphImmediateDismissRequested?.invoke()
        }
    }

    fun finalizePendingMorphAlertDismiss() {
        val key = pendingMorphDismissAlertKey ?: return
        pendingMorphDismissAlertKey = null
        removeNotificationAlertByKey(key, keepRemainingExpanded = false)
    }

    override fun launchAlertAndFadeOut(launchingPackage: String?) {
        val current = _uiState.value
        val alerts = current.notificationAlerts
        if (alerts.isEmpty()) return
        val pinned = current.pinnedAlertIndex.coerceIn(0, alerts.lastIndex)
        val launched = alerts[pinned]
        clearAlertReplyAndHold()
        pendingMorphDismissAlertKey = launched.sbn.key
        val fadeHandler = onFadeOutForAppLaunchRequested
        if (fadeHandler != null && !_isOnKeyguard.value && !_isPanelExpanded.value) {
            fadeHandler.invoke(launchingPackage)
        } else {
            finalizePendingMorphAlertDismiss()
        }
    }

    /** Xoa dung key trong hang doi; neu xoa top -> promote under. */
    private fun removeNotificationAlertByKey(
        key: String,
        keepRemainingExpanded: Boolean = true,
    ) {
        if (pendingMorphDismissAlertKey == key) {
            pendingMorphDismissAlertKey = null
        }
        val current = _uiState.value
        val alerts = current.notificationAlerts
        val idx = alerts.indexOfFirst { it.sbn.key == key }
        if (idx < 0) return
        val wasTop = idx == current.pinnedAlertIndex.coerceIn(0, alerts.lastIndex)
        val remaining = alerts.filter { it.sbn.key != key }
        if (remaining.isEmpty()) {
            clearAlertReplyAndHold()
            _alertCompactMode.value = true
            _uiState.value =
                current.copy(notificationAlerts = emptyList(), pinnedAlertIndex = 0)
            return
        }
        var newPinned = current.pinnedAlertIndex
        when {
            idx < newPinned -> newPinned--
            idx == newPinned -> newPinned = newPinned.coerceAtMost(remaining.lastIndex)
        }
        val resolvedPinned = newPinned.coerceIn(0, remaining.lastIndex)
        _uiState.value =
            current.copy(
                notificationAlerts = remaining,
                pinnedAlertIndex = resolvedPinned,
            )
        if (wasTop) {
            clearAlertReplyAndHold()
            val promoted = remaining[resolvedPinned]
            _alertCompactMode.value =
                if (keepRemainingExpanded) {
                    false
                } else {
                    settings.compactNotifications.value && !promoted.isActiveCall()
                }
        }
    }

    private fun updateAlertInQueue(
        current: IslandUiState,
        index: Int,
        notification: IslandEvent.Notification,
    ) {
        val updated = current.notificationAlerts.toMutableList()
        updated[index] = notification
        _uiState.value = current.copy(notificationAlerts = updated)
    }

    private fun shouldSuppressForDndOrRinger(notification: IslandEvent.Notification): Boolean {
        if (notification.isActiveCall()) return false
        val category = notification.sbn.notification?.category
        if (category == Notification.CATEGORY_CALL || category == Notification.CATEGORY_ALARM) return false
        val zenMode = zenModeController.zen
        if (zenMode == Global.ZEN_MODE_NO_INTERRUPTIONS ||
            zenMode == Global.ZEN_MODE_ALARMS) return true
        val ringerMode = audioManager.ringerMode
        return ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    /** Cap nhat icon alert dang mo tai cho trong stack; giu nguyen thu tu va metadata. */
    private fun refreshOpenNotificationAlertIcon(key: String, icon: Drawable?, iconRevision: Long) {
        val current = _uiState.value
        val alerts = current.notificationAlerts
        if (alerts.isEmpty()) return
        val updatedAlerts = NotificationStateReducer.refreshListInPlace(
            list = alerts,
            key = key,
            keySelector = { it.sbn.key },
            updater = {
                it.copy(
                    appIcon = icon,
                    iconRevision = iconRevision,
                )
            },
        )
        if (updatedAlerts !== alerts) {
            _uiState.value = current.copy(notificationAlerts = updatedAlerts)
        }
    }

    private fun showNotificationAlert(
        notification: IslandEvent.Notification,
    ) {
        if (!settings.isEnabled.value || _isPanelExpanded.value || statusBlocking || _isOnKeyguard.value) return
        if (!repository.notification.isNotificationPostCurrent(notification)) return
        if (shouldSuppressForDndOrRinger(notification)) return
        val dismissingKey = pendingMorphDismissAlertKey
        if (dismissingKey != null) {
            pendingMorphDismissAlertKey = null
            if (notification.sbn.key != dismissingKey) {
                val before = _uiState.value
                val cleaned = before.notificationAlerts.filter { it.sbn.key != dismissingKey }
                if (cleaned.size != before.notificationAlerts.size) {
                    _uiState.value =
                        before.copy(notificationAlerts = cleaned, pinnedAlertIndex = 0)
                }
            }
        }
        val current = _uiState.value
        val alerts = current.notificationAlerts
        val pinnedIdx =
            if (alerts.isEmpty()) 0
            else current.pinnedAlertIndex.coerceIn(0, alerts.lastIndex)
        val topAlert = alerts.getOrNull(pinnedIdx)
        if (topAlert != null &&
            topAlert.isActiveCall() &&
            !notification.isActiveCall()
        ) return

        val sameKeyIdx = alerts.indexOfFirst { it.sbn.key == notification.sbn.key }
        val hasProgress = notification.progress >= 0 || notification.isProgressIndeterminate
        val isExpandedTop = topAlert != null && !_alertCompactMode.value

        // Same-key: cap nhat dung entry, khong nhay de entry khac.
        if (sameKeyIdx >= 0) {
            updateAlertInQueue(current, sameKeyIdx, notification)
            if (alertHeld) return
            if (hasProgress) return
            if (sameKeyIdx == pinnedIdx && _alertCompactMode.value) {
                scheduleCompactAlertTimeout()
            }
            return
        }

        if (alertHeld && topAlert != null) {
            // Compact + dang giu: bo qua notif khac key. Expanded: append under.
            if (isExpandedTop) {
                _uiState.value =
                    current.copy(notificationAlerts = alerts + notification)
            }
            return
        }

        // Call active: len top, giu cac alert khac under.
        if (notification.isActiveCall()) {
            notifAlertJob?.cancel()
            notifAlertJob = null
            _alertCompactMode.value = false
            _uiState.value =
                current.copy(
                    notificationAlerts = listOf(notification) + alerts,
                    pinnedAlertIndex = 0,
                )
            return
        }

        // Expanded + notif moi → append under, khong thay top, khong timeout.
        if (isExpandedTop) {
            notifAlertJob?.cancel()
            notifAlertJob = null
            _uiState.value = current.copy(notificationAlerts = alerts + notification)
            return
        }

        // Compact / chua co alert: notif moi = top (HUN), restart timeout.
        notifAlertJob?.cancel()
        notifAlertJob = null
        _alertCompactMode.value =
            settings.compactNotifications.value && !notification.isActiveCall()
        _uiState.value =
            current.copy(
                notificationAlerts = listOf(notification),
                pinnedAlertIndex = 0,
            )

        if (hasProgress) return
        scheduleCompactAlertTimeout()
    }

    override fun togglePlayPause(mediaKey: String) = repository.media.togglePlayPause(mediaKey)

    override fun skipNext(mediaKey: String) = repository.media.skipNext(mediaKey)

    override fun skipPrev(mediaKey: String) = repository.media.skipPrev(mediaKey)

    override fun sendCustomAction(mediaKey: String, action: String) = repository.media.sendCustomAction(mediaKey, action)

    override fun openMediaOutputSwitcher(mediaKey: String) = repository.media.openMediaOutputSwitcher(mediaKey)

    override fun disconnectBluetooth(address: String) = repository.connectivity.disconnectBluetooth(address)

    override fun openUrl(url: String) = repository.system.openUrl(url)

    override fun openMediaApp(mediaKey: String) = repository.media.openMediaApp(mediaKey)

    override fun openChronometerApp(contentIntent: PendingIntent?, packageName: String) {
        if (contentIntent != null) {
            try {
                activityStarter.postStartActivityDismissingKeyguard(contentIntent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open chronometer contentIntent", e)
            }
        }
        if (packageName.isEmpty()) return
        val intent =
            context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activityStarter.postStartActivityDismissingKeyguard(intent, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open chronometer package=$packageName", e)
        }
    }

    override fun sendDeskClockTimerAction(
        packageName: String,
        timerId: Int,
        pause: Boolean,
        actionIntent: PendingIntent?,
    ) {
        // 1) PI Clock tao (notif 1-timer / cache) — tin cay nhat.
        val cachedPi =
            actionIntent
                ?: repository.notification.getDeskClockTogglePendingIntent(timerId, pause)
        if (cachedPi != null) {
            try {
                cachedPi.sendWithBal(context)
                Log.i(TAG, "sendDeskClockTimerAction via PI pause=$pause id=$timerId")
                return
            } catch (e: Exception) {
                Log.w(TAG, "sendDeskClockTimerAction PI failed", e)
            }
        }
        if (packageName.isEmpty() || timerId < 0) {
            Log.w(TAG, "sendDeskClockTimerAction skip pkg=$packageName id=$timerId")
            return
        }
        val action =
            if (pause) "com.android.deskclock.action.PAUSE_TIMER"
            else "com.android.deskclock.action.START_TIMER"
        // 2) sendBroadcast — AMS BroadcastSkipPolicy cho SystemUI → TimerReceiver.
        try {
            val intent =
                Intent(action).apply {
                    setClassName(packageName, "com.android.deskclock.timer.TimerReceiver")
                    putExtra("com.android.deskclock.extra.TIMER_ID", timerId)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
            context.sendBroadcast(intent)
            Log.i(TAG, "sendDeskClockTimerAction via sendBroadcast pause=$pause id=$timerId")
        } catch (e: Exception) {
            Log.w(TAG, "sendDeskClockTimerAction broadcast failed", e)
        }
    }

    override fun seekTo(mediaKey: String, position: Long) = repository.media.seekTo(mediaKey, position)

    override fun setRingerMode(mode: Int) = repository.system.setRingerMode(mode)

    override fun toggleTorch() = repository.torch.toggleTorch()

    override fun launchNotificationDismissingKeyguard(event: IslandEvent.Notification) {
        val intent = event.sbn.notification?.contentIntent ?: return
        activityStarter.startPendingIntentDismissingKeyguard(intent)
    }

    override fun setTorchLevel(level: Int) = repository.torch.setLevel(level)

    override fun setTorchLevelTemporary(level: Int) = repository.torch.setLevelTemporary(level)

    override fun copyToClipboard(text: String) = repository.system.copyToClipboard(text)

    override fun copyUriToClipboard(uri: Uri) = repository.system.copyUriToClipboard(uri)

    override fun removeClipboardItem(id: Long) = repository.system.removeClipboardItem(id)

    override fun switchToApp(taskId: Int) = repository.appTracking.switchToApp(taskId)

    override fun killApp(taskId: Int) = repository.appTracking.killApp(taskId)

    fun onPanelExpandedChanged(expanded: Boolean) {
        _isPanelExpanded.value = expanded
        if (expanded) dismissAllNotificationAlertsImmediate(notifyExpansion = true)
        // QS/shade blocks alerts and expanded cards, not the persistent status-bar pill.
        // Keep its composition and occupied slot across panel open/close.
    }

    private fun updateChipVisibility() {
        val current = _uiState.value
        val shouldHide = statusBlocking
        if (shouldHide && current.islandState == IslandState.CHIP) {
            _uiState.value = current.copy(islandState = IslandState.HIDDEN)
        } else if (!shouldHide && current.events.isNotEmpty() && current.islandState == IslandState.HIDDEN && !current.manuallyHidden) {
            _uiState.value = current.copy(islandState = IslandState.CHIP)
        }
    }

    private fun scheduleAutoDismiss(event: IslandEvent, delayOverride: Long? = null) {
        if (!settings.isEnabled.value) return
        val ms = delayOverride ?: event.behavior.autoDismissMs ?: return
        val eventId = event.id
        autoDismissJobs[eventId]?.cancel()
        autoDismissJobs[eventId] =
            applicationScope.launch {
                delay(ms)
                val current = _uiState.value.events.find { it.id == eventId } ?: event
                dismissEvent(current)
            }
    }

    private fun mapIndicationType(type: Int): IslandEvent.KeyguardIndication.IndicationType? =
        when (type) {
            KeyguardIndicationController.AX_TYPE_BIOMETRIC ->
                IslandEvent.KeyguardIndication.IndicationType.BIOMETRIC
            KeyguardIndicationController.AX_TYPE_TRANSIENT ->
                IslandEvent.KeyguardIndication.IndicationType.TRANSIENT
            KeyguardIndicationController.AX_TYPE_TRUST ->
                IslandEvent.KeyguardIndication.IndicationType.TRUST
            KeyguardIndicationController.AX_TYPE_DISCLOSURE ->
                IslandEvent.KeyguardIndication.IndicationType.DISCLOSURE
            KeyguardIndicationController.AX_TYPE_OWNER_INFO ->
                IslandEvent.KeyguardIndication.IndicationType.OWNER_INFO
            KeyguardIndicationController.AX_TYPE_ALIGNMENT ->
                IslandEvent.KeyguardIndication.IndicationType.ALIGNMENT
            KeyguardIndicationController.AX_TYPE_PERSISTENT_UNLOCK ->
                IslandEvent.KeyguardIndication.IndicationType.PERSISTENT_UNLOCK
            else -> null
        }
}
