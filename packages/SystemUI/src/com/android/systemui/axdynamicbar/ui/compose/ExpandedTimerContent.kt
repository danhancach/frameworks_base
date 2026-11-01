/*
 * Copyright 2025-2026 AxionOS
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

package com.android.systemui.axdynamicbar.ui.compose

import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.res.R

/**
 * Expand timer: RemoteViews goc Clock (MetricStyle / actions notif).
 * Khong dung ChronometerExpandedShell / synthetic PAUSE-START.
 */
@Composable
internal fun TimerExpanded(
    event: IslandEvent.Timer,
    interactor: IslandActions,
) {
    ChronometerRemoteViewsExpanded(
        sbn = event.sbn,
        fallbackLabel = event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_timer) },
        fallbackSubtitle =
            run {
                val remainingMs = event.displayedRemainingMs()
                val hasTime =
                    event.endTimeMs > 0L ||
                        event.pausedRemainingMs > 0L ||
                        event.endElapsedRealtimeMs > 0L
                when {
                    hasTime -> formatCountdownLong(remainingMs)
                    event.isPaused -> stringResource(R.string.ax_dynamic_bar_paused)
                    else -> stringResource(R.string.ax_dynamic_bar_running)
                }
            },
        contentIntent = event.contentIntent,
        packageName = event.packageName,
        interactor = interactor,
    )
}

/**
 * Expand stopwatch: RemoteViews goc Clock — khong Compose lap ledger / shell custom.
 */
@Composable
internal fun StopwatchExpanded(event: IslandEvent.Stopwatch, interactor: IslandActions) {
    ChronometerRemoteViewsExpanded(
        sbn = event.sbn,
        fallbackLabel = event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_stopwatch) },
        fallbackSubtitle = formatStopwatch(event.displayedElapsedMs()),
        contentIntent = event.contentIntent,
        packageName = event.packageName,
        interactor = interactor,
    )
}

/**
 * Host SbnContentView theo khung QS (MetricStyle promoted + max height shade).
 * Padding / maxWidth = notification_shade_content_margin — khong PrimaryCard SpaceXxl.
 */
@Composable
internal fun ChronometerRemoteViewsExpanded(
    sbn: StatusBarNotification?,
    fallbackLabel: String,
    fallbackSubtitle: String,
    contentIntent: PendingIntent?,
    packageName: String,
    interactor: IslandActions,
) {
    val fallback: @Composable () -> Unit = {
        ChronometerRemoteViewsFallback(
            label = fallbackLabel,
            subtitle = fallbackSubtitle,
        )
    }
    val shadeMargin = dimensionResource(R.dimen.notification_shade_content_margin_horizontal)
    // Max height = cung dimen ExpandableNotificationRow dung cho promoted ongoing trong shade.
    val maxH = dimensionResource(R.dimen.notification_max_height_for_promoted_ongoing)
    val screenDp = LocalConfiguration.current.screenWidthDp.dp
    // Be rong noi dung QS: man hinh - 2*margin shade (giong notification stack).
    val qsContentMax = (screenDp - shadeMargin * 2).coerceAtLeast(280.dp)
    // Morph host da co mot glass — chi RemoteViews, khong islandBlur card thu hai.
    val morphHostFrame = LocalKeyguardMorphHostFrame.current
    Box(
        modifier =
            Modifier
                .widthIn(max = qsContentMax)
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = maxH)
                .islandCardSurface(
                    morphHostFrame = morphHostFrame,
                    onClick = {
                        interactor.openChronometerApp(contentIntent, packageName)
                        interactor.collapseIsland()
                    },
                ),
    ) {
        if (sbn != null) {
            SbnContentView(
                sbn,
                fallback = fallback,
                qsMetricFrame = true,
                preserveRemoteViewRipple = true,
            )
        } else {
            fallback()
        }
    }
}

/** The chronometer expand tu boc card QS — StackedPanels khong boc PrimaryCard lan nua. */
@Composable
internal fun ChronometerQsCard(content: @Composable () -> Unit) {
    // ChronometerRemoteViewsExpanded tu ve khung; wrapper chi de slot API giong PrimaryCard.
    content()
}

/** Fallback nhe khi sbn null / inflate RemoteViews fail — khong dung shell chronometer day du. */
@Composable
private fun ChronometerRemoteViewsFallback(
    label: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(SpaceXxl),
        verticalArrangement = Arrangement.spacedBy(SpaceXxs),
    ) {
        Text(
            label,
            color = OnCardText,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = SubtleGray,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RowScope.CompactTimerRow(event: IslandEvent.Timer) {
    val remainingMs =
        rememberDisplayedChronometerMs(
            identityKey =
                listOf(
                    event.endTimeMs,
                    event.isPaused,
                    event.pausedRemainingMs,
                    event.endElapsedRealtimeMs,
                ),
            ticking = !event.isPaused,
            tickMs = 500L,
            isCountdown = true,
            compute = { event.displayedRemainingMs() },
        )
    val hasTime =
        event.endTimeMs > 0L || event.pausedRemainingMs > 0L || event.endElapsedRealtimeMs > 0L
    val style = eventStyleFor(event)
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(style.accent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(18.dp)) }
    }
    Spacer(modifier = Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        if (event.label.isNotEmpty())
            Text(
                event.label,
                color = SubtleGray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        if (hasTime) {
            Text(
                formatCountdownLong(remainingMs),
                color = if (event.isPaused) SubtleGray else style.accent,
                style = TsMono.copy(fontSize = 13.sp),
            )
        } else {
            Text(stringResource(if (event.isPaused) R.string.ax_dynamic_bar_paused else R.string.ax_dynamic_bar_running), color = style.accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun RowScope.CompactStopwatchRow(event: IslandEvent.Stopwatch) {
    val elapsedMs =
        rememberDisplayedChronometerMs(
            identityKey =
                listOf(
                    event.startTimeMs,
                    event.startElapsedRealtimeMs,
                    event.isRunning,
                    event.pausedElapsedMs,
                ),
            ticking = event.isRunning,
            tickMs = 200L,
            compute = { event.displayedElapsedMs() },
        )
    val style = eventStyleFor(event)
    Box(
        modifier =
            Modifier.size(SizeCompactIcon).clip(ShapeCompact).background(style.accent.copy(alpha = AlphaIconBg)),
        contentAlignment = Alignment.Center,
    ) {
        if (event.isRunning) PulsingDot(color = style.accent, size = 18.dp)
        else style.icon?.let { Icon(it, null, tint = style.accent, modifier = Modifier.size(18.dp)) }
    }
    Spacer(modifier = Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            event.label.ifEmpty { stringResource(style.labelRes) },
            color = SubtleGray,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatStopwatch(elapsedMs),
            color = if (event.isRunning) style.accent else SubtleGray,
            style = TsMono.copy(fontSize = 13.sp),
        )
    }
}
