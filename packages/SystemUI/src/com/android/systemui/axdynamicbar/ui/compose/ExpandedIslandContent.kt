@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import com.android.systemui.axdynamicbar.shared.islandClickIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import kotlinx.coroutines.delay
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.res.R

private val EXPANDED_BOTTOM_PAD = 50.dp

@Composable
fun ExpandedIslandContent(
    events: List<IslandEvent>,
    interactor: IslandActions,
    onCollapse: () -> Unit,
    expandedFilter: String? = null,
    /**
     * Chronometer: id dang mo luc expand (multi-timer). Stack van dung ALL non-notif events
     * de doi media↔timer; commit cap nhat qua [onExpandOnlyEventIdChange].
     */
    expandOnlyEventId: String? = null,
    pinnedEventId: String? = null,
    /** Cap nhat expand-only khi stack commit (null cho media / non-chronometer). */
    onExpandOnlyEventIdChange: ((String?) -> Unit)? = null,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    if (events.isEmpty()) return

    // Status-bar expand (khong filter): stack Samsung — toan bo chip non-notif.
    if (expandedFilter == null) {
        val stackEvents =
            remember(events, pinnedEventId) { buildStackOrder(events, pinnedEventId) }
        LaunchedEffect(stackEvents) {
            if (stackEvents.isEmpty()) {
                delay(200)
                onCollapse()
            }
        }
        if (stackEvents.isEmpty()) return

        // Pin + dong bo expandOnlyEventId (timer→media = null, media→timer = id).
        val stackInteractor =
            remember(interactor, events, onExpandOnlyEventIdChange) {
                object : IslandActions by interactor {
                    override fun pinEventById(eventId: String) {
                        interactor.pinEventById(eventId)
                        val next = events.find { it.id == eventId }
                        onExpandOnlyEventIdChange?.invoke(next?.let { expandOnlyEventIdFor(it) })
                    }
                }
            }
        // expandOnlyEventId chi anh huong pin luc mo; stack order = pinned + cycle.
        StackedExpandedPanels(
            events = events,
            pinnedEventId = pinnedEventId ?: expandOnlyEventId,
            interactor = stackInteractor,
            hapticsViewModelFactory = hapticsViewModelFactory,
        )
        return
    }

    val filteredEvents =
        remember(events, expandedFilter, pinnedEventId) {
            val typed = events.filter { EVENT_TYPE_IDS[it::class.java] == expandedFilter }
            val pinned = typed.find { it.id == pinnedEventId }
            if (pinned != null) {
                listOf(pinned) + typed.filter { it.id != pinned.id }
            } else {
                typed
            }
        }

    val notifIds =
        remember(filteredEvents) {
            filteredEvents.filterIsInstance<IslandEvent.Notification>().map { it.id }
        }
    LaunchedEffect(notifIds) { notifIds.forEach { interactor.onNotificationInteraction(it) } }
    DisposableEffect(notifIds) {
        onDispose { notifIds.forEach { interactor.onNotificationInteractionEnd(it) } }
    }

    LaunchedEffect(filteredEvents) {
        if (filteredEvents.isEmpty()) {
            delay(200)
            onCollapse()
        }
    }

    if (filteredEvents.isEmpty()) return

    val isNotificationFilter = expandedFilter == "notification"
    val context = LocalContext.current
    val notifGroups =
        remember(filteredEvents, isNotificationFilter) {
            if (!isNotificationFilter) return@remember emptyList()
            filteredEvents
                .filterIsInstance<IslandEvent.Notification>()
                .groupBy { it.sbn.packageName }
                .values
                .toList()
        }

    val expandedGroups = remember(expandedFilter) { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier =
            Modifier.widthIn(max = ExpandedMaxWidth)
                .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
        contentPadding =
            PaddingValues(start = SpaceLg, end = SpaceLg, top = SpaceXxs, bottom = EXPANDED_BOTTOM_PAD),
    ) {
        if (isNotificationFilter && notifGroups.isNotEmpty()) {
            notifGroups.forEach { group ->
                if (group.size == 1) {
                    val event = group.first()
                    item(key = event.id) {
                        MagneticSwipeToDismiss(
                            onDismiss = { interactor.dismissEvent(event) },
                            modifier = Modifier.animateItem(),
                        ) {
                            CompositionLocalProvider(LocalStackOwnsContentClick provides true) {
                                PrimaryCard(
                                    onClick = {
                                        openEventContentAndCollapse(
                                            context,
                                            event.sbn.notification?.contentIntent,
                                            interactor,
                                            event.sbn.packageName.orEmpty(),
                                        )
                                    },
                                ) {
                                    NotificationExpanded(event, interactor)
                                }
                            }
                        }
                    }
                } else {
                    val pkg = group.first().sbn.packageName
                    val isExpanded = expandedGroups[pkg] == true
                    item(key = "group_$pkg") {
                        MagneticSwipeToDismiss(
                            onDismiss = { group.forEach { interactor.dismissEvent(it) } },
                            modifier = Modifier.animateItem(),
                        ) {
                            PrimaryCard {
                                NotificationGroupCard(
                                    notifications = group,
                                    isExpanded = isExpanded,
                                    onToggleExpand = { expandedGroups[pkg] = !isExpanded },
                                    interactor = interactor,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredEvents, key = { it.id }) { event ->
                MagneticSwipeToDismiss(
                    onDismiss = { interactor.dismissEvent(event) },
                    modifier = Modifier.animateItem(),
                ) {
                    when (event) {
                        is IslandEvent.Media -> MediaCard(event, interactor)
                        // Chronometer & Promoted ongoing: khung QS — khong PrimaryCard SpaceXxl.
                        is IslandEvent.Timer ->
                            ChronometerQsCard { TimerExpanded(event, interactor) }
                        is IslandEvent.Stopwatch ->
                            ChronometerQsCard { StopwatchExpanded(event, interactor) }
                        is IslandEvent.PromotedOngoing ->
                            ChronometerQsCard { PromotedOngoingExpanded(event, interactor) }
                        is IslandEvent.Notification -> {
                            CompositionLocalProvider(LocalStackOwnsContentClick provides true) {
                                PrimaryCard(
                                    onClick = {
                                        openEventContentAndCollapse(
                                            context,
                                            event.sbn.notification?.contentIntent,
                                            interactor,
                                            event.sbn.packageName.orEmpty(),
                                        )
                                    },
                                ) {
                                    NotificationExpanded(event, interactor)
                                }
                            }
                        }
                        is IslandEvent.Call -> {
                            CompositionLocalProvider(LocalStackOwnsContentClick provides true) {
                                PrimaryCard(
                                    onClick = {
                                        openEventContentAndCollapse(
                                            context,
                                            event.sbn.notification?.contentIntent,
                                            interactor,
                                            event.sbn.packageName.orEmpty(),
                                        )
                                    },
                                ) {
                                    CallExpanded(event, interactor)
                                }
                            }
                        }
                        else ->
                            PrimaryCard {
                                val motionScheme = MaterialTheme.motionScheme
                                AnimatedContent(
                                    targetState = event,
                                    transitionSpec = {
                                        ((fadeIn(motionScheme.defaultEffectsSpec()) +
                                            scaleIn(
                                                initialScale = 0.95f,
                                                animationSpec = motionScheme.defaultSpatialSpec(),
                                            )) togetherWith
                                            (fadeOut(motionScheme.fastEffectsSpec()) +
                                                scaleOut(
                                                    targetScale = 0.95f,
                                                    animationSpec = motionScheme.fastSpatialSpec(),
                                                ))).using(sizeTransform = null)
                                    },
                                    contentKey = { it::class.simpleName + it.id },
                                    label = "expanded_card",
                                ) { animatedEvent ->
                                    ExpandedEventContent(
                                        animatedEvent,
                                        interactor,
                                        hapticsViewModelFactory,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExpandedEventContent(
    event: IslandEvent,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    when (event) {
        is IslandEvent.AudioRecording -> AudioRecordingExpanded(event, interactor)
        is IslandEvent.PromotedOngoing -> PromotedOngoingExpanded(event, interactor)
        is IslandEvent.Sports -> SportsExpanded(event, interactor)
        is IslandEvent.NowPlaying -> NowPlayingExpanded(event, interactor)
        // Unreachable in practice — Media is routed straight to MediaCard above, which draws its
        // own frosted surface — but the `when` must stay exhaustive.
        is IslandEvent.Media -> MediaCard(event, interactor)
        is IslandEvent.Bluetooth -> BluetoothExpanded(event, interactor)
        is IslandEvent.Hotspot -> HotspotExpanded(event)
        is IslandEvent.Charging -> ChargingExpanded(event)
        is IslandEvent.Alarm -> AlarmExpanded(event, interactor)
        is IslandEvent.Timer -> TimerExpanded(event, interactor)
        is IslandEvent.Stopwatch -> StopwatchExpanded(event, interactor)
        is IslandEvent.RingerMode -> RingerModeExpanded(event, interactor)
        is IslandEvent.Vpn -> VpnExpanded(event)
        is IslandEvent.Clipboard -> ClipboardExpanded(event, interactor)
        is IslandEvent.Call -> CallExpanded(event, interactor)
        is IslandEvent.Notification -> NotificationExpanded(event, interactor)
        is IslandEvent.AppSwitch -> AppHistoryExpanded(event, interactor)
        is IslandEvent.Torch -> TorchExpanded(event, interactor, hapticsViewModelFactory)
        is IslandEvent.BiometricUnlock -> BiometricUnlockExpanded(event)
        is IslandEvent.KeyguardIndication -> {}
        is IslandEvent.AospChip -> {}
    }
}

@Composable
internal fun BiometricUnlockExpanded(event: IslandEvent.BiometricUnlock) {
    ExpandedCardLayout(
        accentColor = IslandTone.POSITIVE.tint,
        icon = { Icon(Icons.Filled.Check, null, tint = IslandTone.POSITIVE.tint, modifier = Modifier.size(16.dp)) },
        title = {
            Text(stringResource(R.string.ax_dynamic_bar_device_unlocked), color = OnCardText, style = MaterialTheme.typography.titleMedium)
            Text(event.sourceName, color = SubtleGray, style = MaterialTheme.typography.labelMedium)
        },
    )
}

@Composable
internal fun CallExpanded(event: IslandEvent.Call, interactor: IslandActions) {
    val context = LocalContext.current
    val isIncoming = event.callType.contains("incoming", ignoreCase = true)
    val tone = if (isIncoming) IslandTone.POSITIVE else IslandTone.ACCENT
    val accent = tone.tint
    val declineTint = IslandTone.ALERT.tint
    val answerTint = IslandTone.POSITIVE.tint

    val callClickMod =
        if (LocalStackOwnsContentClick.current) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = islandClickIndication(),
            ) {
                openEventContentAndCollapse(
                    context,
                    event.sbn.notification?.contentIntent,
                    interactor,
                    event.sbn.packageName.orEmpty(),
                )
            }
        }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(callClickMod),
        verticalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        // Plain row: the card behind it is already the surface, a tinted panel inside it was a
        // second surface stacked on the glass.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            // Caller photo or app icon
            val photo = event.callerPhoto ?: event.appIcon
            if (photo != null) {
                Image(
                    bitmap = photo.toScaledBitmap(SizeCompactIcon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SizeCompactIcon)
                        .clip(CircleShape),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXxs),
            ) {
                Text(
                    if (isIncoming) stringResource(R.string.ax_dynamic_bar_incoming_call)
                    else stringResource(R.string.ax_dynamic_bar_call),
                    color = SubtleGray,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    event.callerName ?: event.number ?: "",
                    color = OnCardText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Call timer for active calls
            if (!isIncoming && event.callStartTimeMs > 0L) {
                CallElapsedTimer(event.callStartTimeMs, accent)
            }
        }

        // Number subtitle if caller name is present
        if (event.callerName != null && event.number != null) {
            Text(
                event.number,
                color = SubtleGray,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Action buttons
        if (event.actions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpaceLg),
            ) {
                event.actions.forEach { action ->
                    val isDestructive = action.label.toString().lowercase().let {
                        it.contains("decline") || it.contains("reject") || it.contains("hang up")
                    }
                    val isAnswer = action.label.toString().lowercase().let {
                        it.contains("answer") || it.contains("accept")
                    }
                    val tint = when {
                        isDestructive -> declineTint
                        isAnswer -> answerTint
                        else -> accent
                    }
                    ExpressivePillButton(
                        label = action.label.toString(),
                        contentColor = tint,
                        backgroundColor = tint.copy(alpha = AlphaIconBg),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            try { action.action.actionIntent?.sendWithBal(context) }
                            catch (_: Exception) {}
                            interactor.collapseIsland()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CallElapsedTimer(startTimeMs: Long, color: Color) {
    var elapsedMs by remember(startTimeMs) {
        mutableLongStateOf((System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L))
    }
    LaunchedEffect(startTimeMs) {
        while (true) {
            delay(1000)
            elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
        }
    }
    Text(
        formatElapsedTime(elapsedMs),
        color = color,
        style = TsCounter,
    )
}

/**
 * The frosted card every expanded tile sits on. The blur backdrop is the surface itself: it falls
 * back to the opaque [CardBg] wherever the platform will not blur, so no separate background is
 * painted here.
 */
@Composable
internal fun PrimaryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Morph host keyguard da co mot glass — khong boc islandBlur card long.
    val morphHostFrame = LocalKeyguardMorphHostFrame.current
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .islandCardSurface(morphHostFrame = morphHostFrame, onClick = onClick)
                .then(modifier),
    ) {
        Box(modifier = Modifier.padding(SpaceXxl)) { content() }
    }
}

