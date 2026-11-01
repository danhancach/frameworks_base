@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui.compose

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.RecordingState
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.delay

/**
 * true khi host morph (Layout + KeyguardIslandBlurBg) da la khung —
 * panel/chronometer/pill bo blur/clip rieng (mot be mat kinh).
 */
internal val LocalKeyguardMorphHostFrame = staticCompositionLocalOf { false }

@Composable
internal fun KeyguardExpandedContent(
    event: IslandEvent,
    allEvents: List<IslandEvent>,
    interactor: IslandActions,
    onCollapse: () -> Unit,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    onAvoidFalsing: () -> Unit = {},
    /** Popup neo tu pill lower-region — khong fillMaxSize (tranh panel lech tren lock icon). */
    asPillAnchoredPopup: Boolean = false,
) {
    if (event is IslandEvent.KeyguardIndication ||
        event is IslandEvent.AppSwitch ||
        event is IslandEvent.AospChip
    ) {
        LaunchedEffect(Unit) { onCollapse() }
        return
    }

    val view = LocalView.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val rootModifier =
        if (asPillAnchoredPopup) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxSize()
        }
    CompositionLocalProvider(LocalKeyguardMorphHostFrame provides asPillAnchoredPopup) {
        Box(
            modifier = rootModifier
                .pointerInteropFilter {
                    // Chi avoidFalsing — khong disallow som (nut/slider uu tien).
                    if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                        onAvoidFalsing()
                    }
                    false
                }
                .pointerInput(onCollapse, touchSlop, asPillAnchoredPopup) {
                    awaitEachGesture {
                        // Final: sau control con. Khong return som khi DOWN consumed —
                        // than Media (islandCardSurface) van keo xuong collapse sau nguong.
                        var downEvent = awaitPointerEvent(PointerEventPass.Final)
                        while (downEvent.changes.none { it.changedToDownIgnoreConsumed() }) {
                            downEvent = awaitPointerEvent(PointerEventPass.Final)
                        }
                        val down = downEvent.changes.first { it.changedToDownIgnoreConsumed() }
                        val downPosition = down.position
                        var draggingDown = false
                        // true den UP/CANCEL — khong reset khi MOVE khong con consumed.
                        var childTookOver = false
                        try {
                            while (true) {
                                // Dang keo collapse: Initial de chiem tu clickable than.
                                val pass =
                                    if (draggingDown) PointerEventPass.Initial
                                    else PointerEventPass.Final
                                val event = awaitPointerEvent(pass)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                val dy = change.position.y - downPosition.y
                                val dx = change.position.x - downPosition.x
                                val pastSlop =
                                    kotlin.math.abs(dx) > touchSlop ||
                                        kotlin.math.abs(dy) > touchSlop
                                val verticalCollapseIntent =
                                    dy > touchSlop &&
                                        kotlin.math.abs(dy) >
                                            kotlin.math.abs(dx) * 1.2f
                                if (
                                    !draggingDown &&
                                    !childTookOver &&
                                    verticalCollapseIntent
                                ) {
                                    draggingDown = true
                                    change.consume()
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                } else if (
                                    !draggingDown &&
                                    !childTookOver &&
                                    change.isConsumed &&
                                    pastSlop
                                ) {
                                    childTookOver = true
                                }
                                if (childTookOver && !draggingDown) {
                                    if (!change.pressed) break
                                    continue
                                }
                                if (draggingDown) change.consume()
                                if (!change.pressed) {
                                    if (draggingDown && dy > touchSlop) {
                                        change.consume()
                                        onCollapse()
                                    } else if (
                                        !asPillAnchoredPopup &&
                                        !change.isConsumed &&
                                        dx * dx + dy * dy <= touchSlop * touchSlop
                                    ) {
                                        // Tap ngoai panel (full-screen host).
                                        change.consume()
                                        onCollapse()
                                    }
                                    break
                                }
                            }
                        } finally {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            when (event) {
                // Giong Timer: khong boc KeyguardPanelSurface — RemoteViews clock / MediaCard
                // tu xu ly hit; PanelSurface clickable long tranh Surface(onClick) cua nut.
                is IslandEvent.Media -> MediaCard(event, interactor)
                is IslandEvent.Timer -> KeyguardTimerPanel(event, interactor, onCollapse)
                is IslandEvent.Stopwatch -> KeyguardStopwatchPanel(event, interactor, onCollapse)
                is IslandEvent.AudioRecording -> KeyguardAudioRecordingPanel(event, interactor)
                else -> KeyguardGenericPanel(event, interactor, hapticsViewModelFactory)
            }
        }
    }
}

/**
 * Be mat kinh keyguard (blur/opaque CardBg). Clickable ro: nuot tap trong panel
 * de handler tap-ngoai-collapse chi chay ngoai the.
 *
 * Khi [LocalKeyguardMorphHostFrame]: host morph da co blur+corner — chi noi dung, khong the rieng.
 */
@Composable
private fun KeyguardPanelSurface(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    val morphHostFrame = LocalKeyguardMorphHostFrame.current
    val surfaceMod =
        if (morphHostFrame) {
            Modifier
                .fillMaxWidth()
                .then(modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        } else {
            Modifier
                .widthIn(max = ExpandedMaxWidth)
                .then(modifier)
                .padding(horizontal = SpaceSection)
                .clip(ShapeXl)
                .islandBlur(RadiusXl, CardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
        }
    Box(modifier = surfaceMod) {
        content()
    }
}

@Composable
private fun TonalBanner(
    colors: IslandColorScheme,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeLg)
            .background(colors.tonal)
            .padding(horizontal = SpaceXxl, vertical = SpaceLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceMd),
    ) {
        content()
    }
}

@Composable
private fun KeyguardTimerPanel(
    event: IslandEvent.Timer,
    interactor: IslandActions,
    onCollapse: () -> Unit,
) {
    // RemoteViews goc Clock — khong Compose countdown / synthetic Pause.
    val remainingMs = event.displayedRemainingMs()
    val hasTime =
        event.endTimeMs > 0L || event.pausedRemainingMs > 0L || event.endElapsedRealtimeMs > 0L
    val subtitle =
        when {
            hasTime -> formatCountdownLong(remainingMs)
            event.isPaused -> stringResource(R.string.ax_dynamic_bar_paused)
            else -> stringResource(R.string.ax_dynamic_bar_running)
        }
    // ChronometerRemoteViewsExpanded da boc khung QS — khong them KeyguardPanelSurface padding lon.
    ChronometerRemoteViewsExpanded(
        sbn = event.sbn,
        fallbackLabel = event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_timer) },
        fallbackSubtitle = subtitle,
        contentIntent = event.contentIntent,
        packageName = event.packageName,
        interactor = interactor,
    )
}

@Composable
private fun KeyguardStopwatchPanel(
    event: IslandEvent.Stopwatch,
    interactor: IslandActions,
    onCollapse: () -> Unit,
) {
    ChronometerRemoteViewsExpanded(
        sbn = event.sbn,
        fallbackLabel = event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_stopwatch) },
        fallbackSubtitle = formatStopwatch(event.displayedElapsedMs()),
        contentIntent = event.contentIntent,
        packageName = event.packageName,
        interactor = interactor,
    )
}

@Composable
private fun KeyguardAudioRecordingPanel(event: IslandEvent.AudioRecording, interactor: IslandActions) {
    val context = LocalContext.current
    val colors = rememberIslandColors(event)
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(event.startTimeMs, event.state, event.pausedDurationMs) {
        if (event.state == RecordingState.RECORDING) {
            while (true) {
                elapsedMs = (System.currentTimeMillis() - event.startTimeMs - event.pausedDurationMs)
                    .coerceAtLeast(0L)
                delay(1000)
            }
        } else {
            elapsedMs = (System.currentTimeMillis() - event.startTimeMs - event.pausedDurationMs)
                .coerceAtLeast(0L)
        }
    }

    val isRecording = event.state == RecordingState.RECORDING

    KeyguardPanelSurface { Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SpaceSection),
        verticalArrangement = Arrangement.spacedBy(SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TonalBanner(colors) {
            if (isRecording) PulsingDot(color = colors.accent, size = SpaceMd)
            Icon(Icons.Filled.Mic, null, tint = colors.accent, modifier = Modifier.size(SizeIconSm))
            Text(
                when (event.state) {
                    RecordingState.RECORDING -> stringResource(R.string.ax_dynamic_bar_recording)
                    RecordingState.PAUSED -> stringResource(R.string.ax_dynamic_bar_paused)
                    RecordingState.SAVED -> stringResource(R.string.ax_dynamic_bar_saved)
                }.uppercase(),
                color = colors.accent,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Text(
            formatElapsedTime(elapsedMs),
            color = OnCardText,
            style = MaterialTheme.typography.displayLarge,
        )

        if (isRecording) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth().clip(ShapeChip),
                color = colors.accent,
                trackColor = colors.accent.copy(alpha = AlphaFaint),
            )
        } else {
            LinearWavyProgressIndicator(
                progress = { 0f },
                modifier = Modifier.fillMaxWidth().clip(ShapeChip),
                color = colors.accent.copy(alpha = AlphaDisabled),
                trackColor = colors.accent.copy(alpha = AlphaFaint),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpaceLg),
        ) {
            val classifiedActions = remember(event.actions, context) {
                event.actions.map { action ->
                    val pkg = action.action.actionIntent?.creatorPackage ?: context.packageName
                    action to action.action.classify(context, pkg)
                }
            }
            val pauseResume = classifiedActions.firstOrNull { (_, kind) ->
                kind == NotificationActionType.PAUSE || kind == NotificationActionType.RESUME
            }?.first
            val stop = classifiedActions.firstOrNull { (_, kind) ->
                kind == NotificationActionType.STOP || kind == NotificationActionType.DELETE
            }?.first
            if (pauseResume != null) {
                ExpressivePillButton(
                    label = if (isRecording) stringResource(R.string.ax_dynamic_bar_pause) else stringResource(R.string.ax_dynamic_bar_resume),
                    icon = if (isRecording) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentColor = colors.onAccent,
                    backgroundColor = colors.accent,
                    modifier = Modifier.weight(1f),
                    onClick = { pauseResume.action.actionIntent?.sendWithBal(context) },
                )
            }
            ExpressivePillButton(
                label = stringResource(R.string.ax_dynamic_bar_stop),
                icon = Icons.Filled.Stop,
                contentColor = colors.accent,
                backgroundColor = colors.tonal,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (stop != null) {
                        stop.action.actionIntent?.sendWithBal(context)
                        interactor.collapseIsland()
                    } else {
                        interactor.dismissEvent(event)
                    }
                },
            )
        }
    }
}
}

@Composable
private fun KeyguardGenericPanel(
    event: IslandEvent,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    val colors = rememberIslandColors(event)
    KeyguardPanelSurface { Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SpaceSection),
        verticalArrangement = Arrangement.spacedBy(SpaceXxl),
    ) {
        ExpandedEventContent(event, interactor, hapticsViewModelFactory)

        ExpressivePillButton(
            label = stringResource(R.string.ax_dynamic_bar_dismiss),
            contentColor = colors.accent,
            backgroundColor = colors.accent.copy(alpha = AlphaIconBg),
            modifier = Modifier.fillMaxWidth(),
            onClick = { interactor.dismissEvent(event) },
        )
    }
}
}

