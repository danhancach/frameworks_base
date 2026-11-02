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

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.AlphaGlass
import com.android.systemui.axdynamicbar.shared.AlphaIconBg
import com.android.systemui.axdynamicbar.shared.AlphaSecondary
import com.android.systemui.axdynamicbar.shared.AlphaTertiary
import com.android.systemui.axdynamicbar.shared.ChipHighlightWidth
import com.android.systemui.axdynamicbar.shared.ChipTier
import com.android.systemui.axdynamicbar.shared.PillPrimary
import com.android.systemui.axdynamicbar.shared.ShapeXs
import com.android.systemui.axdynamicbar.shared.SpaceMd
import com.android.systemui.axdynamicbar.shared.SpaceSm
import com.android.systemui.axdynamicbar.shared.SpaceXs
import com.android.systemui.axdynamicbar.shared.TsBadge
import com.android.systemui.axdynamicbar.shared.chipAccentColorFor
import com.android.systemui.axdynamicbar.shared.chipContentColorFor
import com.android.systemui.axdynamicbar.shared.chipCornerRadius
import com.android.systemui.axdynamicbar.shared.chipHeight
import com.android.systemui.axdynamicbar.shared.chipProgressFor
import com.android.systemui.axdynamicbar.shared.islandBlur
import com.android.systemui.axdynamicbar.shared.mediaTrackSwitchKey
import com.android.systemui.axdynamicbar.shared.mediaTrackSwitchTransition
import com.android.systemui.axdynamicbar.shared.statusBarChipUsesCircleStyle
import com.android.systemui.axdynamicbar.shared.textKeyFor
import com.android.systemui.axdynamicbar.shared.tierFor
import com.android.systemui.axdynamicbar.shared.toScaledBitmap
import com.android.systemui.res.R

/**
 * Snapshot presentation StatusBar pill — freeze luc expand / overlay enter.
 * Cung mapping cho pill that + overlay dummy (khong bitmap / icon gia).
 *
 * RemoteViews capsule: model AxDynamicBar KHONG co mExpandedChipView Samsung —
 * [supportsRemoteViewsCapsule]=false; dung PillEventIcon/Text.
 */
data class StatusBarPillPresentation(
    val event: IslandEvent,
    val isAlert: Boolean,
    val chipStyle: Int,
    val progress: Float?,
    val ownerId: String,
    val generation: Int,
    /** Chronometer ms freeze @p0 — null neu khong phai timer/stopwatch. */
    val frozenChronometerMs: Long? = null,
    /** Chi keyguard path — StatusBar overlay luon null. */
    val carrierName: String? = null,
) {
    val useCircleStyle: Boolean
        get() = statusBarChipUsesCircleStyle(chipStyle, event, isAlert = isAlert)

    val supportsRemoteViewsCapsule: Boolean
        get() = false
}

/** Tao snapshot tu event hien thi + style — progress/chronometer freeze theo open. */
fun statusBarPillPresentationOf(
    event: IslandEvent,
    isAlert: Boolean,
    chipStyle: Int,
    generation: Int,
    carrierName: String? = null,
    progressOverride: Float? = null,
    frozenChronometerMs: Long? = null,
): StatusBarPillPresentation {
    val useCircle = statusBarChipUsesCircleStyle(chipStyle, event, isAlert = isAlert)
    val progress =
        progressOverride
            ?: chipProgressFor(event, includeMediaProgress = useCircle)
    val chrono =
        frozenChronometerMs
            ?: when (event) {
                is IslandEvent.Timer -> event.displayedRemainingMs()
                is IslandEvent.Stopwatch -> event.displayedElapsedMs()
                else -> null
            }
    return StatusBarPillPresentation(
        event = event,
        isAlert = isAlert,
        chipStyle = chipStyle,
        progress = progress,
        ownerId = event.id,
        generation = generation,
        frozenChronometerMs = chrono,
        carrierName = carrierName,
    )
}

/** So khop presentation @p0 vs pill that (pure test / gate). */
fun statusBarPillPresentationMatchesOwner(
    snapshot: StatusBarPillPresentation?,
    liveEventId: String?,
    generation: Int,
): Boolean {
    if (snapshot == null || liveEventId == null) return false
    return snapshot.ownerId == liveEventId && snapshot.generation == generation
}

/**
 * Snapshot con hop le trong generation hien tai (cancel/reopen midflight).
 * generation khac → stale; khong dung cho dummy moi.
 */
fun statusBarPillPresentationGenerationValid(
    snapshot: StatusBarPillPresentation?,
    currentGeneration: Int,
): Boolean = snapshot != null && snapshot.generation == currentGeneration

/**
 * Co duoc phep sync frozen luc stack commit / collapse safeguard khong.
 * Chi khi dang expand + da freeze (khong doi snapshot luc pill→card lan dau).
 */
fun shouldSyncFrozenPillPresentation(
    intentExpanded: Boolean,
    frozen: StatusBarPillPresentation?,
): Boolean = intentExpanded && frozen != null

/**
 * Chon event cho frozen morph sau CONFIRMED stack pin / collapse safeguard.
 *
 * Thu tu: [preferredEventId] (chronometer expand-only) → [topEvent] (da pin sync) →
 * first event hop le. Khong dung index cu cua snapshot. Id khong con trong [events]
 * → bo preferred, fallback top (tranh ve stale A).
 */
fun resolveFrozenPillEventForSelection(
    events: List<IslandEvent>,
    preferredEventId: String?,
    topEvent: IslandEvent?,
): IslandEvent? {
    if (events.isEmpty()) return null
    preferredEventId?.let { id ->
        events.firstOrNull { it.id == id }?.let { return it }
    }
    topEvent?.let { top ->
        events.firstOrNull { it.id == top.id }?.let { return it }
    }
    return events.firstOrNull { it !is IslandEvent.AospChip } ?: events.firstOrNull()
}

/** Frozen can doi owner khi selected khac snapshot (A→B). Cung owner → giu. */
fun frozenPillOwnerNeedsUpdate(
    frozen: StatusBarPillPresentation?,
    selectedEvent: IslandEvent?,
): Boolean {
    if (frozen == null || selectedEvent == null) return false
    return frozen.ownerId != selectedEvent.id
}

/**
 * Snapshot freeze timer/stopwatch — co frozenChronometerMs de dummy collapse refresh.
 * Media/alert/khac → false (khong dung path chrono).
 */
fun frozenPillHasDynamicChronometer(frozen: StatusBarPillPresentation?): Boolean {
    if (frozen == null || frozen.isAlert) return false
    if (frozen.frozenChronometerMs == null) return false
    return when (frozen.event) {
        is IslandEvent.Timer,
        is IslandEvent.Stopwatch -> true
        else -> false
    }
}

/** Dang chay → can tick ~1Hz luc collapse; paused → chi refresh 1 lan @collapse-start. */
fun frozenPillChronometerIsTicking(event: IslandEvent): Boolean =
    when (event) {
        is IslandEvent.Timer -> !event.isPaused
        is IslandEvent.Stopwatch -> event.isRunning
        else -> false
    }

/**
 * SSOT ms hien thi — cung cong thuc live pill
 * ([IslandEvent.Timer.displayedRemainingMs] / [IslandEvent.Stopwatch.displayedElapsedMs]).
 */
fun displayedChronometerMsOf(
    event: IslandEvent,
    nowWallMs: Long = System.currentTimeMillis(),
    nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
): Long? =
    when (event) {
        is IslandEvent.Timer -> event.displayedRemainingMs(nowWallMs, nowElapsedMs)
        is IslandEvent.Stopwatch -> event.displayedElapsedMs(nowWallMs, nowElapsedMs)
        else -> null
    }

/**
 * Refresh CHI [StatusBarPillPresentation.frozenChronometerMs] luc COLLAPSE.
 * Giu owner / style / progress / generation / isAlert / event identity — khong rebuild
 * owner (tranh card jump / AnimatedContent key churn).
 *
 * [liveEvent]: event cung owner tu uiState (anchor ER / pause moi). Null hoac id khac
 * → fallback [frozen.event] (owner gone) hoac null neu mismatch bat buoc reject.
 * [requireSameOwner]: true (default) → live id khac ownerId → null (stale ignore).
 */
fun refreshFrozenPillChronometerPresentation(
    frozen: StatusBarPillPresentation,
    liveEvent: IslandEvent? = null,
    nowWallMs: Long = System.currentTimeMillis(),
    nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
    requireSameOwner: Boolean = true,
): StatusBarPillPresentation? {
    if (!frozenPillHasDynamicChronometer(frozen)) return null
    val source =
        when {
            liveEvent == null -> frozen.event
            liveEvent.id == frozen.ownerId -> liveEvent
            requireSameOwner -> return null
            else -> frozen.event
        }
    val ms = displayedChronometerMsOf(source, nowWallMs, nowElapsedMs) ?: return null
    if (ms == frozen.frozenChronometerMs) return frozen
    return frozen.copy(frozenChronometerMs = ms)
}

/**
 * Delay den moc doi chu so compact tiep (countdown floor; stopwatch round *.5).
 * Countdown: formatCountdownCompact = floor(ms/1000) — doi chu so vua SAU moc
 * integer-second (r+1ms). Vi du 251400→401ms→250999; 251000→1ms; 250999→1000ms.
 * ms<=0: 1000ms an toan — khong 1ms busy-loop khi het gio.
 * Stopwatch: giu half-second round rieng (500ms edge → 1ms). Khong poll 120Hz.
 */
fun collapseChronometerTickDelayMs(chronoMs: Long, countdown: Boolean): Long {
    if (chronoMs <= 0L) return 1000L
    return if (countdown) {
        val r = chronoMs % 1000L
        (r + 1L).coerceIn(1L, 1000L)
    } else {
        val r = chronoMs % 1000L
        val toHalf = if (r <= 500L) 500L - r else 1500L - r
        toHalf.coerceIn(1L, 1000L)
    }
}

/** Key chip_event khong gom chronometer — refresh ms khong churn AnimatedContent. */
fun statusBarChipEventDisplayKeyStableAcrossChronoTick(
    before: StatusBarPillPresentation,
    after: StatusBarPillPresentation,
): Boolean =
    statusBarChipEventDisplayKey(before.event, before.isAlert) ==
        statusBarChipEventDisplayKey(after.event, after.isAlert) &&
        before.ownerId == after.ownerId &&
        before.generation == after.generation &&
        before.chipStyle == after.chipStyle &&
        before.progress == after.progress

/**
 * Dummy overlay vs pill that cung owner truoc unsuppress (task-034).
 * generation khong bat buoc khop live chip (chip dung layoutGen hien tai).
 */
fun statusBarCollapsePresentationParity(
    frozen: StatusBarPillPresentation?,
    liveEventId: String?,
): Boolean {
    if (frozen == null || liveEventId == null) return false
    return frozen.ownerId == liveEventId
}

/**
 * Entry on dinh cho AnimatedContent(label=chip_event).
 * Key = id + isAlert — timer/media tick KHONG doi key → khong restart transition.
 */
data class StatusBarChipEventEntry(
    val event: IslandEvent,
    val isAlert: Boolean,
) {
    val key: String
        get() = statusBarChipEventDisplayKey(event, isAlert)
}

/** Key chip_event: alert:id hoac id — khong gom chronometer tick / progress. */
fun statusBarChipEventDisplayKey(event: IslandEvent, isAlert: Boolean): String =
    if (isAlert) "alert:${event.id}" else event.id

/**
 * Presentation cho DUNG branch AnimatedContent ([entry]), khong outer current.
 * Outgoing A phai ve A du outer da B.
 */
fun statusBarChipEntryPresentation(
    entry: StatusBarChipEventEntry,
    chipStyle: Int,
    generation: Int,
    carrierName: String? = null,
): StatusBarPillPresentation =
    statusBarPillPresentationOf(
        event = entry.event,
        isAlert = entry.isAlert,
        chipStyle = chipStyle,
        generation = generation,
        carrierName = carrierName,
    )

/**
 * True neu branch dang ve outer current thay vi entry (bug closure task-037).
 * Outgoing A + outer B + rendered B → leak.
 */
fun statusBarChipBranchRendersOuterClosure(
    branchEntry: StatusBarChipEventEntry,
    outerCurrent: StatusBarChipEventEntry,
    renderedOwnerId: String,
): Boolean {
    if (branchEntry.key == outerCurrent.key) return false
    return renderedOwnerId == outerCurrent.event.id &&
        renderedOwnerId != branchEntry.event.id
}

/**
 * Handoff @p~0: frozen B == live B va live content da settle (khong crossfade dang chay).
 * Task-043: dung truoc unsuppress; chrome overlay chi ve 0 sau liveDrawnAcked (controller).
 */
fun statusBarHandbackReadyForUnsuppress(
    frozen: StatusBarPillPresentation?,
    liveEventId: String?,
    liveContentSettled: Boolean,
): Boolean {
    if (!statusBarCollapsePresentationParity(frozen, liveEventId)) return false
    return liveContentSettled
}

/**
 * Than pill Row/Circle — SSOT visual cho StatusBar chip + overlay dummy.
 *
 * LIVE ([staticMode]=false): animateColor/Dp + AnimatedContent media/icon/text/alert
 * giong AxDynamicBarChip HEAD (truoc extract 018).
 * SNAPSHOT/DUMMY ([staticMode]=true): cung layout/body, khong motion/ticker/marquee;
 * chronometer dung [StatusBarPillPresentation.frozenChronometerMs].
 *
 * [includeBlur]: islandBlur + clip (pill that). Overlay shell tu ve glass → false.
 * [includeProgress]: thanh progress day (Row) — dummy overlay van ve neu snapshot co.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusBarPillBody(
    presentation: StatusBarPillPresentation,
    modifier: Modifier = Modifier,
    includeBlur: Boolean = true,
    includeProgress: Boolean = true,
    staticMode: Boolean = false,
    blurAlpha: Float = 1f,
) {
    val event = presentation.event
    val motionScheme = MaterialTheme.motionScheme
    val accentTarget = chipAccentColorFor(event)
    val contentTarget = chipContentColorFor(event)
    // Hook luon goi (Compose); staticMode chi chon target truc tiep — khong animate dummy.
    val accentAnim by
        animateColorAsState(
            accentTarget,
            motionScheme.fastEffectsSpec(),
            label = "accent",
        )
    val contentAnim by
        animateColorAsState(
            contentTarget,
            motionScheme.fastEffectsSpec(),
            label = "content",
        )
    val accent = if (staticMode) accentTarget else accentAnim
    val contentColor = if (staticMode) contentTarget else contentAnim
    val progress = presentation.progress
    val chipTextMaxWidth = dimensionResource(R.dimen.ongoing_activity_chip_max_text_width)

    if (presentation.useCircleStyle) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // Capture / squish dat tren CircleChip glass that — khong cha fillMaxHeight.
            CircleChip(
                event = event,
                accent = accent,
                contentColor = contentColor,
                progress = progress,
                modifier = modifier,
            )
        }
        return
    }

    val tier = if (presentation.isAlert) ChipTier.ACTIVE else tierFor(event)
    val heightAnim by
        animateDpAsState(
            tier.chipHeight,
            motionScheme.defaultSpatialSpec(),
            label = "chip_height",
        )
    val cornerAnim by
        animateDpAsState(
            tier.chipCornerRadius,
            motionScheme.defaultSpatialSpec(),
            label = "chip_corner",
        )
    val chipHeightDp: Dp = if (staticMode) tier.chipHeight else heightAnim
    val chipCorner: Dp = if (staticMode) tier.chipCornerRadius else cornerAnim
    val isChronometerEvent = event is IslandEvent.Timer || event is IslandEvent.Stopwatch

    Box(
        modifier = Modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                modifier
                    .height(chipHeightDp)
                    .widthIn(max = if (isChronometerEvent) 108.dp else 100.dp)
                    .then(
                        if (includeBlur) {
                            Modifier.clip(RoundedCornerShape(chipCorner))
                                .islandBlur(
                                    cornerRadius = chipCorner,
                                    fallbackColor = accent,
                                    tint = accent.copy(alpha = AlphaGlass),
                                    alpha = blurAlpha,
                                    highlightWidth = ChipHighlightWidth,
                                )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (includeProgress && progress != null) {
                            val trackColor = lerp(accent, contentColor, 0.2f)
                            val fillColor = lerp(accent, contentColor, 0.6f)
                            Modifier.drawWithContent {
                                drawContent()
                                val barH = 2.dp.toPx()
                                val y = size.height - barH
                                drawRect(
                                    trackColor,
                                    topLeft = Offset(0f, y),
                                    size = Size(size.width, barH),
                                )
                                drawRect(
                                    fillColor,
                                    topLeft = Offset(0f, y),
                                    size = Size(size.width * progress, barH),
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = SpaceSm, end = SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBarPillRowContent(
                presentation = presentation,
                contentColor = contentColor,
                chipTextMaxWidth = chipTextMaxWidth,
                staticMode = staticMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.StatusBarPillRowContent(
    presentation: StatusBarPillPresentation,
    contentColor: Color,
    chipTextMaxWidth: Dp,
    staticMode: Boolean,
) {
    val event = presentation.event
    val carrierName = presentation.carrierName
    val isAlert = presentation.isAlert
    val isChronometerEvent = event is IslandEvent.Timer || event is IslandEvent.Stopwatch
    val motionScheme = MaterialTheme.motionScheme

    if (carrierName != null) {
        Text(
            text = carrierName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = AlphaSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 56.dp),
        )
        Text(
            text = " · ",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = AlphaTertiary),
        )
    }
    when {
        isAlert && event is IslandEvent.Notification -> {
            val notif = event
            if (staticMode) {
                StatusBarAlertRow(notif, contentColor, chipTextMaxWidth, marquee = false)
            } else {
                AnimatedContent(
                    targetState = notif.sbn.key,
                    transitionSpec = {
                        (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                                fadeOut(motionScheme.fastEffectsSpec()))
                            .using(sizeTransform = null)
                    },
                    label = "alert_content",
                ) {
                    StatusBarAlertRow(notif, contentColor, chipTextMaxWidth, marquee = true)
                }
            }
        }
        event is IslandEvent.Sports && event.team2Name.isNotEmpty() -> {
            StatusBarSportsTeamBadge(event.team1Name, event.team1Icon, contentColor)
            Spacer(Modifier.width(SpaceXs))
            Text(
                if (event.score1.isNotEmpty()) "${event.score1} - ${event.score2}"
                else stringResource(R.string.ax_dynamic_bar_sports_vs),
                style = PillPrimary,
                color = contentColor,
                maxLines = 1,
            )
            Spacer(Modifier.width(SpaceXs))
            StatusBarSportsTeamBadge(event.team2Name, event.team2Icon, contentColor)
        }
        event is IslandEvent.Media -> {
            // Mot cay AnimatedContent — staticMode chi None + static text (043).
            // Idle track switch van mediaTrackSwitchTransition khi !staticMode.
            AnimatedContent(
                targetState = event,
                transitionSpec = {
                    if (staticMode) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        mediaTrackSwitchTransition(motionScheme).using(sizeTransform = null)
                    }
                },
                contentKey = { mediaTrackSwitchKey(it) },
                label = "chip_media",
                modifier =
                    Modifier.weight(1f, fill = false)
                        .widthIn(max = chipTextMaxWidth + 20.dp),
            ) { media ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillEventIcon(media, tint = contentColor, animated = false)
                    PillEventText(
                        media,
                        Modifier.widthIn(max = chipTextMaxWidth),
                        overrideColor = contentColor,
                        static = staticMode,
                        frozenChronometerMs = presentation.frozenChronometerMs,
                    )
                }
            }
        }
        else -> {
            // Icon/text: cung cay — static None; idle van fade khi !staticMode (043).
            AnimatedContent(
                targetState = statusBarChipIconKey(event),
                transitionSpec = {
                    if (staticMode) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                                fadeOut(motionScheme.fastEffectsSpec()))
                            .using(sizeTransform = null)
                    }
                },
                label = "chip_icon",
            ) {
                PillEventIcon(event, tint = contentColor, animated = false)
            }
            Spacer(Modifier.width(SpaceXs))
            if (isChronometerEvent) {
                // Chronometer compact: Canvas khoa width — khong weight/remeasure Row
                PillEventText(
                    event,
                    Modifier,
                    overrideColor = contentColor,
                    static = staticMode,
                    frozenChronometerMs = presentation.frozenChronometerMs,
                )
            } else {
                AnimatedContent(
                    targetState = textKeyFor(event),
                    transitionSpec = {
                        if (staticMode) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                                    fadeOut(motionScheme.fastEffectsSpec()))
                                .using(sizeTransform = null)
                        }
                    },
                    label = "chip_text",
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .widthIn(max = chipTextMaxWidth),
                ) {
                    PillEventText(
                        event,
                        Modifier.widthIn(max = chipTextMaxWidth),
                        overrideColor = contentColor,
                        static = staticMode,
                        frozenChronometerMs = presentation.frozenChronometerMs,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBarAlertRow(
    notif: IslandEvent.Notification,
    contentColor: Color,
    chipTextMaxWidth: Dp,
    marquee: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        notif.appIcon?.let { icon ->
            Image(
                bitmap = icon.toScaledBitmap(16.dp),
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(ShapeXs),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(SpaceXs))
        }
        Text(
            text = notif.appName ?: "",
            style = PillPrimary,
            color = contentColor,
            maxLines = 1,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier =
                Modifier.widthIn(max = chipTextMaxWidth).then(
                    if (marquee) Modifier.basicMarquee(iterations = 1) else Modifier,
                ),
        )
    }
}

/** Key icon chip — khop AxDynamicBarChip HEAD (truoc extract 018). */
internal fun statusBarChipIconKey(event: IslandEvent): Any =
    when (event) {
        is IslandEvent.AppSwitch -> {
            val app = event.previousApp ?: event.recentApps.firstOrNull()
            app?.taskId ?: app?.packageName ?: event.id
        }
        is IslandEvent.Media -> mediaTrackSwitchKey(event)
        is IslandEvent.Notification -> event.sbn.key
        else -> event.id
    }

@Composable
internal fun StatusBarSportsTeamBadge(name: String, icon: Drawable?, contentColor: Color) {
    val badgeSize = 16.dp
    if (icon != null) {
        Image(
            bitmap = icon.toScaledBitmap(badgeSize),
            contentDescription = name,
            modifier = Modifier.size(badgeSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier =
                Modifier.size(badgeSize)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                style = TsBadge,
                color = contentColor,
            )
        }
    }
}
