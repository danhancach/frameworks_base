package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.android.systemui.axdynamicbar.shared.islandClickIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.media.controls.ui.drawable.SquigglyProgress
import com.android.systemui.media.controls.ui.view.WaveformSeekBar
import com.android.systemui.res.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val AlbumArtSize = 64.dp
private val PlayPauseSize = 48.dp
private val ControlButtonSize = 40.dp
private val ControlIconSize = 22.dp
private val SeekBarHeight = 24.dp

/**
 * The expanded media tile: art, track and the primary transport on one row, with a slim seek row
 * underneath. It draws its own frosted surface (it is routed around [PrimaryCard]).
 *
 * Keyguard morph host ([LocalKeyguardMorphHostFrame]): bo blur rieng — mot be mat kinh;
 * van clip ShapeCard khi co ripple click (islandCardSurface).
 * Custom actions (shuffle, like, …) khong nam o day; chi transport chinh.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaCard(event: IslandEvent.Media, interactor: IslandActions) {
    val colors = rememberMediaColors(event)
    val accent = colors.accent
    val useWaveform = if (interactor is com.android.systemui.axdynamicbar.domain.AxDynamicBarInteractor) {
        interactor.mediaUseWaveform.collectAsStateWithLifecycle().value
    } else {
        false
    }
    val motionScheme = MaterialTheme.motionScheme

    // Morph host: bo blur rieng (mot glass). Click toan panel mo app; transport/output
    // giu clickable con (thang hit-test). KHONG boc clickable long quanh tung nut.
    val morphHostFrame = LocalKeyguardMorphHostFrame.current
    Box(
        modifier =
            Modifier.fillMaxWidth().islandCardSurface(
                morphHostFrame = morphHostFrame,
                onClick = {
                    interactor.openMediaApp(event.mediaKey)
                    interactor.collapseIsland()
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SpaceXxl),
            verticalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            // Khong spacedBy: tieu de sat cover (trai) va sat phim control (phai); fade lam mem mep.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Art + tieu de hien thi thong tin; click mo app do the cha MediaCard xu ly toan panel.
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover + meta chung 1 transition — doi bai dong bo.
                    AnimatedContent(
                        targetState = event,
                        transitionSpec = { mediaTrackSwitchTransition(motionScheme) },
                        contentKey = { mediaTrackSwitchKey(it) },
                        label = "media_card_header",
                        modifier = Modifier.weight(1f),
                    ) { media ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val art = media.albumArt
                            if (art != null) {
                                Image(
                                    bitmap = art.toSquareScaledBitmap(AlbumArtSize),
                                    contentDescription = null,
                                    modifier = Modifier.size(AlbumArtSize).clip(ShapeLg),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(AlbumArtSize)
                                        .clip(ShapeLg)
                                        .background(accent.copy(alpha = AlphaFaint)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        null,
                                        tint = accent,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            // Samsung: TextView MARQUEE + horizontalFadingEdge native.
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(SpaceXxs),
                            ) {
                                val titleStyle = MaterialTheme.typography.titleSmall
                                val artistStyle = MaterialTheme.typography.bodySmall
                                NativeMarqueeText(
                                    text = media.track.ifEmpty {
                                        stringResource(R.string.ax_dynamic_bar_now_playing)
                                    },
                                    color = OnCardText,
                                    textSize = titleStyle.fontSize,
                                    fontWeight = titleStyle.fontWeight?.weight ?: 500,
                                    fadeLength = SpacePanel,
                                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (media.artist.isNotEmpty()) {
                                    NativeMarqueeText(
                                        text = media.artist,
                                        color = OnCardSecondary,
                                        textSize = artistStyle.fontSize,
                                        fontWeight = artistStyle.fontWeight?.weight ?: 400,
                                        fadeLength = SpacePanel,
                                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                // Cung UI homescreen (Compose icon + squish). Morph: them hit-layer AndroidView
                // trong suot (Compose clickable bi backdrop cancel).
                MediaTransport(event, interactor, accent, morphHitLayer = morphHostFrame)
            }

            if (event.duration > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpaceSm),
                ) {
                    MediaSeekBar(
                        event,
                        interactor,
                        accent,
                        Modifier.weight(1f),
                        useWaveform,
                        nativeTouch = morphHostFrame,
                    )
                    OutputSwitcher(event, interactor, accent, morphHitLayer = morphHostFrame)
                }
            } else {
                // Khong duration (live/podcast): van hien output switcher.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        event.outputDeviceName,
                        color = SubtleGray,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    OutputSwitcher(event, interactor, accent, morphHitLayer = morphHostFrame)
                }
            }
        }
    }
}

/**
 * Transport chung homescreen + keyguard.
 * [morphHitLayer]: ve Compose (icon Material + squish) + AndroidView hit trong suot (View.onClick).
 */
@Composable
private fun MediaTransport(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    morphHitLayer: Boolean,
) {
    val tonalBg = accent.copy(alpha = AlphaSubtle)
    var prevToggle by remember { mutableIntStateOf(0) }
    var playToggle by remember { mutableIntStateOf(0) }
    var nextToggle by remember { mutableIntStateOf(0) }
    val onPrev = rememberUpdatedState {
        interactor.skipPrev(event.mediaKey)
        prevToggle++
    }
    val onPlay = rememberUpdatedState {
        interactor.togglePlayPause(event.mediaKey)
        playToggle++
    }
    val onNext = rememberUpdatedState {
        interactor.skipNext(event.mediaKey)
        nextToggle++
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceXs),
        ) {
            TransportButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.ax_dynamic_bar_previous),
                tint = accent,
                bg = tonalBg,
                size = ControlButtonSize,
                toggleCount = prevToggle,
                // Morph: click do hit-layer; StatusBar: click Compose.
                onClick = if (morphHitLayer) null else ({ onPrev.value.invoke() }),
            )
            TransportButton(
                icon = if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (event.isPlaying) R.string.ax_dynamic_bar_pause
                    else R.string.ax_dynamic_bar_play
                ),
                tint = accent,
                bg = accent.copy(alpha = AlphaAccent),
                size = PlayPauseSize,
                toggleCount = playToggle,
                onClick = if (morphHitLayer) null else ({ onPlay.value.invoke() }),
            )
            TransportButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.ax_dynamic_bar_next),
                tint = accent,
                bg = tonalBg,
                size = ControlButtonSize,
                toggleCount = nextToggle,
                onClick = if (morphHitLayer) null else ({ onNext.value.invoke() }),
            )
        }
        if (morphHitLayer) {
            val density = LocalDensity.current
            val controlPx = with(density) { ControlButtonSize.roundToPx() }
            val playPx = with(density) { PlayPauseSize.roundToPx() }
            val gapPx = with(density) { SpaceXs.roundToPx() }
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { ctx ->
                    LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(transparentHitSlot(ctx, controlPx))
                        addView(
                            transparentHitSlot(ctx, playPx).also {
                                (it.layoutParams as LinearLayout.LayoutParams).marginStart = gapPx
                                (it.layoutParams as LinearLayout.LayoutParams).marginEnd = gapPx
                            },
                        )
                        addView(transparentHitSlot(ctx, controlPx))
                    }
                },
                update = { row ->
                    row.getChildAt(0).setOnClickListener { onPrev.value.invoke() }
                    row.getChildAt(1).setOnClickListener { onPlay.value.invoke() }
                    row.getChildAt(2).setOnClickListener { onNext.value.invoke() }
                },
            )
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    bg: Color,
    size: Dp,
    toggleCount: Int,
    onClick: (() -> Unit)?,
) {
    val mod = Modifier
        .size(size)
        .squishAnimation(toggleCount)
        .clip(CircleShape)
        .background(bg)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(tint),
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
    Box(contentAlignment = Alignment.Center, modifier = mod) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(ControlIconSize))
    }
}

@Composable
private fun OutputSwitcher(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    morphHitLayer: Boolean,
) {
    val label = event.outputDeviceName.ifBlank {
        stringResource(R.string.ax_dynamic_bar_output)
    }
    var toggleCount by remember { mutableIntStateOf(0) }
    val onClick = rememberUpdatedState {
        interactor.openMediaOutputSwitcher(event.mediaKey)
        interactor.collapseIsland()
        toggleCount++
    }
    Box(modifier = Modifier.size(32.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .squishAnimation(toggleCount)
                .clip(CircleShape)
                .background(accent.copy(alpha = AlphaIconBg))
                .then(
                    if (morphHitLayer) Modifier
                    else Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = islandClickIndication(accent),
                        onClick = { onClick.value.invoke() },
                    ),
                ),
        ) {
            Icon(
                Icons.Filled.VolumeUp,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(SizeIconSm),
            )
        }
        if (morphHitLayer) {
            val density = LocalDensity.current
            val sizePx = with(density) { 32.dp.roundToPx() }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> transparentHitSlot(ctx, sizePx) },
                update = { v -> v.setOnClickListener { onClick.value.invoke() } },
            )
        }
    }
}

/** Slot hit trong suot — View.onClick (parity Clock RemoteViews), khong ve icon / ripple. */
private fun transparentHitSlot(ctx: Context, sizePx: Int): android.view.View {
    return android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        isClickable = true
        isFocusable = true
        // Khong dung selectableItemBackground — tranh ripple tren lockscreen morph.
        background = null
        foreground = null
    }
}

@Composable
private fun MediaSeekBar(
    event: IslandEvent.Media,
    interactor: IslandActions,
    accent: Color,
    modifier: Modifier = Modifier,
    useWaveform: Boolean = false,
    /** Keyguard morph: SeekBar.isEnabled + listener (Compose tap bi backdrop collapse). */
    nativeTouch: Boolean = false,
) {
    val mediaProgress = rememberMediaProgress(event)
    val isPlaying = event.isPlaying
    val durationMs = event.duration
    val positionMs = mediaProgress.positionMs
    val serverFraction = mediaProgress.progress
    val speed = event.playbackSpeed.takeIf { it > 0f } ?: 1f

    // Identity theo QS mediaKey + metadata — tranh hai pill cung bai/goi nham seek anim.
    val seekIdentity = "${event.mediaKey}|${event.track}|${event.artist}|${event.packageName}"

    var isScrubbing by remember { mutableStateOf(false) }
    var displayFraction by remember { mutableFloatStateOf(serverFraction) }

    val interactorRef = rememberUpdatedState(interactor)
    val durationRef = rememberUpdatedState(durationMs)
    val swipeLock = LocalDismissSwipeLock.current

    LaunchedEffect(seekIdentity, positionMs, durationMs, isPlaying, speed) {
        if (isScrubbing) return@LaunchedEffect
        displayFraction = serverFraction
        if (!isPlaying || durationMs <= 0L) return@LaunchedEffect
        val startProgressMs = positionMs
        val startNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (isScrubbing) break
            val elapsedMs = (frameNanos - startNanos) / 1_000_000L
            val interpolated =
                ((startProgressMs + elapsedMs * speed) / durationMs).coerceIn(0f, 1f)
            displayFraction = interpolated
            if (interpolated >= 1f) break
        }
    }

    val displayMs = (displayFraction * durationMs).toLong()
    val accentArgb = accent.toArgb()
    val trackAlphaArgb = accent.copy(alpha = AlphaSubtle).toArgb()

    // Times flank the bar on one line instead of taking a row of their own above it.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm),
    ) {
        Text(
            formatElapsedTime(displayMs),
            color = SubtleGray,
            style = PillMono,
        )

        val barModifier =
            if (nativeTouch) {
                // Touch do SeekBar View — khong Compose pointerInput.
                Modifier.weight(1f).height(SeekBarHeight)
            } else {
                Modifier
                    .weight(1f)
                    .height(SeekBarHeight)
                    .pointerInput(swipeLock) {
                        awaitEachGesture {
                            awaitPointerEvent()
                            swipeLock.value = true
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                            } finally {
                                swipeLock.value = false
                            }
                        }
                    }
                    .pointerInput("tap") {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            displayFraction = fraction
                            interactorRef.value.seekTo(event.mediaKey, (fraction * durationRef.value).toLong())
                        }
                    }
                    .pointerInput("drag") {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isScrubbing = true
                                displayFraction =
                                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                interactorRef.value.seekTo(
                                    event.mediaKey,
                                    (displayFraction * durationRef.value).toLong(),
                                )
                                isScrubbing = false
                            },
                            onDragCancel = { isScrubbing = false },
                            onHorizontalDrag = { change, _ ->
                                displayFraction =
                                    (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                change.consume()
                            },
                        )
                    }
            }

        Box(
            modifier = barModifier,
            contentAlignment = Alignment.Center,
        ) {
            if (useWaveform) {
                AndroidView(
                    factory = { context ->
                        WaveformSeekBar(context).apply {
                            max = 10_000
                            setWaveformColor(accentArgb)
                            setThumbColor(accentArgb)
                            isEnabled = nativeTouch
                            if (nativeTouch) {
                                wireNativeSeekTouch(
                                    seekBar = this,
                                    onScrubbing = { scrubbing ->
                                        isScrubbing = scrubbing
                                        swipeLock.value = scrubbing
                                    },
                                    onFraction = { fraction, commit ->
                                        displayFraction = fraction
                                        if (commit) {
                                            interactorRef.value.seekTo(
                                                event.mediaKey,
                                                (fraction * durationRef.value).toLong(),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                    update = { bar ->
                        if (!isScrubbing) {
                            val target = (displayFraction * 10_000f).toInt().coerceIn(0, 10_000)
                            if (bar.progress != target) bar.progress = target
                        }
                        when {
                            isPlaying && !bar.isPlaying -> bar.startWaveAnimation()
                            !isPlaying && bar.isPlaying -> bar.stopWaveAnimation()
                        }
                        if (nativeTouch && !bar.isEnabled) bar.isEnabled = true
                    },
                    modifier = Modifier.fillMaxWidth().height(SeekBarHeight),
                )
            } else {
                AndroidView(
                    factory = { context ->
                        SeekBar(context).apply {
                            max = 10_000
                            splitTrack = false
                            setPadding(0, 0, 0, 0)
                            isEnabled = nativeTouch

                            thumb = createSeekBarThumb(context, accentArgb)
                            thumbOffset = thumb.intrinsicWidth / 2

                            val layer = (progressDrawable?.mutate() as? LayerDrawable)
                            if (layer != null) {
                                layer.findDrawableByLayerId(android.R.id.background)
                                    ?.mutate()?.setTint(trackAlphaArgb)
                                layer.findDrawableByLayerId(android.R.id.secondaryProgress)
                                    ?.mutate()?.setTint(
                                        com.android.internal.graphics.ColorUtils
                                            .setAlphaComponent(accentArgb, 60)
                                    )
                                val squiggle = SquigglyProgress().apply {
                                    waveLength = context.resources.getDimensionPixelSize(
                                        R.dimen.qs_media_seekbar_progress_wavelength
                                    ).toFloat()
                                    lineAmplitude = context.resources.getDimensionPixelSize(
                                        R.dimen.qs_media_seekbar_progress_amplitude
                                    ).toFloat()
                                    phaseSpeed = context.resources.getDimensionPixelSize(
                                        R.dimen.qs_media_seekbar_progress_phase
                                    ).toFloat()
                                    strokeWidth = context.resources.getDimensionPixelSize(
                                        R.dimen.qs_media_seekbar_progress_stroke_width
                                    ).toFloat()
                                    setTint(accentArgb)
                                    drawRemainingLine = false
                                    transitionEnabled = false
                                    animate = false
                                }
                                layer.setDrawableByLayerId(android.R.id.progress, squiggle)
                                progressDrawable = layer
                            }
                            if (nativeTouch) {
                                wireNativeSeekTouch(
                                    seekBar = this,
                                    onScrubbing = { scrubbing ->
                                        isScrubbing = scrubbing
                                        swipeLock.value = scrubbing
                                    },
                                    onFraction = { fraction, commit ->
                                        displayFraction = fraction
                                        if (commit) {
                                            interactorRef.value.seekTo(
                                                event.mediaKey,
                                                (fraction * durationRef.value).toLong(),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    },
                    update = { bar ->
                        if (!isScrubbing) {
                            val target = (displayFraction * 10_000f).toInt().coerceIn(0, 10_000)
                            bar.progress = target
                        }

                        (bar.thumb as? GradientDrawable)?.setColor(accentArgb)

                        val alpha = if (isPlaying) 255 else (255 * 0.55f).toInt()
                        bar.thumb?.alpha = alpha

                        val layer = bar.progressDrawable as? LayerDrawable

                        layer?.findDrawableByLayerId(android.R.id.background)
                            ?.setTint(trackAlphaArgb)
                        layer?.findDrawableByLayerId(android.R.id.secondaryProgress)
                            ?.setTint(
                                com.android.internal.graphics.ColorUtils
                                    .setAlphaComponent(accentArgb, 60)
                            )

                        val squiggle = layer
                            ?.findDrawableByLayerId(android.R.id.progress) as? SquigglyProgress
                        squiggle?.apply {
                            setTint(accentArgb)
                            setAlpha(alpha)
                            animate = isPlaying && !isScrubbing
                        }
                        layer?.alpha = alpha
                        if (nativeTouch && !bar.isEnabled) bar.isEnabled = true
                    },
                    modifier = Modifier.fillMaxWidth().height(SeekBarHeight),
                )
            }
        }

        Text(
            formatElapsedTime(durationMs),
            color = SubtleGray,
            style = PillMono,
        )
    }
}

/**
 * Tap + drag tren SeekBar View — consume touch, khong lo backdrop collapse.
 * [onFraction] commit=true khi tha tay / tap xong.
 */
private fun wireNativeSeekTouch(
    seekBar: SeekBar,
    onScrubbing: (Boolean) -> Unit,
    onFraction: (fraction: Float, commit: Boolean) -> Unit,
) {
    seekBar.setOnSeekBarChangeListener(
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                onFraction((progress / 10_000f).coerceIn(0f, 1f), false)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                onScrubbing(true)
                seekBar.parent?.requestDisallowInterceptTouchEvent(true)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                onFraction((seekBar.progress / 10_000f).coerceIn(0f, 1f), true)
                onScrubbing(false)
                seekBar.parent?.requestDisallowInterceptTouchEvent(false)
            }
        },
    )
    seekBar.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL,
            ->
                v.parent?.requestDisallowInterceptTouchEvent(false)
        }
        // false: de SeekBar xu ly tap/drag (setProgress fromUser).
        false
    }
}

/**
 * Creates a pill-shaped thumb drawable for the seekbar.
 */
private fun createSeekBarThumb(context: android.content.Context, tintColor: Int): GradientDrawable {
    val wPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)
    val hPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)
    val radiusPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    )
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setSize(wPx, hPx)
        cornerRadius = radiusPx
        setColor(tintColor)
    }
}

@Composable
internal fun RowScope.CompactMediaRow(
    event: IslandEvent.Media,
    interactor: IslandActions,
) {
    val accent = rememberMediaColors(event).accent
    event.albumArt?.let {
        Image(
            bitmap = it.toSquareScaledBitmap(SizeCompactIcon),
            null,
            modifier = Modifier.size(SizeCompactIcon).clip(ShapeCompact),
            contentScale = ContentScale.Crop,
        )
    } ?: run {
        Box(
            modifier = Modifier.size(SizeCompactIcon)
                .clip(ShapeCompact)
                .background(accent.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(20.dp))
        }
    }
    Spacer(Modifier.width(SpaceLg))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
        Text(
            event.track.ifEmpty { stringResource(R.string.ax_dynamic_bar_music) },
            color = OnCardText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (event.artist.isNotEmpty())
            Text(
                event.artist,
                color = SubtleGray,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
    }
    Spacer(Modifier.width(SpaceMd))
    Surface(
        onClick = { interactor.togglePlayPause(event.mediaKey) },
        shape = CircleShape,
        color = accent.copy(alpha = AlphaAccent),
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Modifier.squishAnimation(toggleCount: Int): Modifier {
    val scaleX = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val scaleY = remember { Animatable(1f, visibilityThreshold = 0.01f) }
    val currentToggleCount by rememberUpdatedState(toggleCount)
    LaunchedEffect(Unit) {
        snapshotFlow { currentToggleCount }
            .drop(1)
            .collectLatest {
                scaleX.snapTo(1f)
                scaleY.snapTo(1f)
                coroutineScope {
                    launch {
                        scaleX.animateTo(
                            targetValue = 1f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                1.066f at 120 using FastOutSlowInEasing
                                0.967f at 260
                                1f at 400
                            },
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = 1f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                0.945f at 120 using FastOutSlowInEasing
                                1.033f at 260
                                1f at 400
                            },
                        )
                    }
                }
            }
    }
    return this.graphicsLayer {
        this.scaleX = scaleX.value
        this.scaleY = scaleY.value
    }
}
