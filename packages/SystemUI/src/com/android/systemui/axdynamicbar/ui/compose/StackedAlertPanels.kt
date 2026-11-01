package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.alertStackOrder
import com.android.systemui.axdynamicbar.shared.ExpandedMaxWidth
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.shared.ShapeCard
import com.android.systemui.axdynamicbar.shared.SpaceLg
import com.android.systemui.axdynamicbar.shared.SpaceXxs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** So lop stack alert hien thi toi da (top + duoi). */
private const val MAX_VISIBLE_LAYERS = 3

/** Commit khi keo qua ~28% max drag (giu fraction × max moi). */
private const val COMMIT_DRAG_FRACTION = 0.28f

/** Under ke lo 100% alpha/scale tai ~25% max drag. */
private const val UNDER_REVEAL_AT = 0.25f

private const val RUBBER_LAYER1 = 0.04f
private const val RUBBER_LAYER2 = 0.02f

private const val SNAP_CANCEL_MS = 520
private const val SNAP_LEAVE_MS = 560

private const val UNDER_REST_ALPHA = 0f

private val STACK_BASE_Y = listOf(0.dp, 12.dp, 22.dp)
private val STACK_SCALE = listOf(1f, 0.95f, 0.90f)

private val EXPANDED_BOTTOM_PAD = 50.dp
private val COMMIT_VELOCITY = 700.dp

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
private fun BoxScope.AlertStackDimScrim(
    alpha: Float,
    consumeTouches: Boolean = false,
    touchHeight: () -> Float = { Float.POSITIVE_INFINITY },
) {
    val currentTouchHeight by rememberUpdatedState(touchHeight)
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.01f && !consumeTouches) return
    Box(
        modifier =
            Modifier.matchParentSize()
                .background(
                    Color.Black.copy(alpha = if (consumeTouches) a.coerceAtLeast(0.01f) else a),
                    ShapeCard,
                )
                .then(
                    if (consumeTouches) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Chi vung peek touchable — khong nuot tap phan an.
                                if (down.position.y !in 0f..currentTouchHeight()) {
                                    return@awaitEachGesture
                                }
                                down.consume()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    e.changes.forEach { it.consume() }
                                    if (e.changes.all { !it.pressed }) break
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
    )
}

/**
 * Stack rieng cho notification alert — physics giong [StackedExpandedPanels], content =
 * [NotificationAlertCard] (khong dung buildStackOrder / event island).
 */
@Composable
internal fun StackedAlertPanels(
    alerts: List<IslandEvent.Notification>,
    pinnedAlertKey: String?,
    interactor: IslandActions,
    onDismissTop: () -> Unit,
    initiallyCompact: Boolean = false,
) {
    var localPinnedKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pinnedAlertKey) {
        if (localPinnedKey != null && pinnedAlertKey == localPinnedKey) {
            localPinnedKey = null
        }
    }
    val effectivePinnedKey = localPinnedKey ?: pinnedAlertKey
    val liveStack =
        remember(alerts, effectivePinnedKey) { alertStackOrder(alerts, effectivePinnedKey) }
    if (liveStack.isEmpty()) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Cung fraction island stack (48% man); commit van ~28% max drag.
    val maxDragPx = with(density) {
        (config.screenHeightDp * 0.48f).dp.toPx()
    }
    val commitThresholdPx = maxDragPx * COMMIT_DRAG_FRACTION
    val commitVelPx = with(density) { COMMIT_VELOCITY.toPx() }
    val leaveNestYPx = with(density) { STACK_BASE_Y[1].toPx() }

    var dragY by remember { mutableFloatStateOf(0f) }
    var underReveal by remember { mutableFloatStateOf(0f) }
    /** Chieu cao clip peek cua under (layer 1); 0 = an. */
    var settleUnderPeekPx by remember { mutableFloatStateOf(0f) }
    /** Full height under da do — dung tinh peek + expand-on-commit. */
    val fullHeights = remember { mutableStateMapOf<String, Int>() }
    val underKey = liveStack.getOrNull(1)?.sbn?.key
    val underFullHeightPx = underKey?.let { fullHeights[it] } ?: 0
    var topSettleY by remember { mutableFloatStateOf(0f) }
    var topSettleScale by remember { mutableFloatStateOf(1f) }
    var topSettleAlpha by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settlingTopKey by remember { mutableStateOf<String?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var leavingKey by remember { mutableStateOf<String?>(null) }
    var leaveProgress by remember { mutableFloatStateOf(0f) }
    var leaveStartY by remember { mutableFloatStateOf(0f) }
    /**
     * Sau commit: clip top moi tu peekHold → full (song song leave).
     * < 0 = khong clip top (rest / xong expand).
     */
    var commitTopPeekPx by remember { mutableFloatStateOf(-1f) }

    val topKey = liveStack.first().sbn.key
    // Keo xuong luon bat (1 the → snap cancel); pin doi the chi khi >= 2.
    val canCommitStackSwitch = liveStack.size >= 2
    // Key tung la under: khi len top van Expanded (khong ve Compact setting).
    var promotedExpandedKeys by remember { mutableStateOf(emptySet<String>()) }
    val stackKeys = remember(liveStack) { liveStack.map { it.sbn.key } }
    LaunchedEffect(stackKeys) {
        if (stackKeys.size >= 2) {
            promotedExpandedKeys = promotedExpandedKeys + stackKeys.drop(1)
        } else if (stackKeys.isEmpty()) {
            promotedExpandedKeys = emptySet()
        }
    }

    LaunchedEffect(topKey, underKey) {
        // Append under luc dang keo / settle: khong reset offset.
        if (isDragging) return@LaunchedEffect
        if (isSettling && topKey == settlingTopKey) return@LaunchedEffect
        if (settleJob?.isActive == true) return@LaunchedEffect
        // Remove / pin ngoai: khong de the moi o offset keo cu.
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        leavingKey = null
        leaveProgress = 0f
        dragY = 0f
        underReveal = 0f
        settleUnderPeekPx = 0f
        topSettleY = 0f
        topSettleScale = 1f
        topSettleAlpha = 1f
        commitTopPeekPx = -1f
    }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        settlingTopKey = null
        leavingKey = null
        leaveProgress = 0f
        topSettleY = 0f
        topSettleScale = 1f
        topSettleAlpha = 1f
        settleUnderPeekPx = 0f
        commitTopPeekPx = -1f
    }

    fun revealFromDragY(y: Float): Float =
        ((y / maxDragPx).coerceIn(0f, 1f) / UNDER_REVEAL_AT).coerceIn(0f, 1f)

    fun peekFromDrag(y: Float, reveal: Float): Float {
        if (y <= 0f) return 0f
        return underPeekHeightPx(
            topY = y,
            topHeight = (fullHeights[topKey] ?: 0).toFloat(),
            underY = leaveNestYPx * reveal + y * RUBBER_LAYER1,
            underHeight = underFullHeightPx.toFloat(),
            underScale = lerp(STACK_SCALE[1], 1f, reveal),
        )
    }

    // Drag: tinh lai peek theo height do (ke ca khi dung tay).
    // Settle: giu peek luc tha (hold-cap), animate tu do — khong tinh lai geometry.
    val underPeekPx = if (isSettling) settleUnderPeekPx else peekFromDrag(dragY, underReveal)

    Box(
        modifier =
            Modifier.widthIn(max = ExpandedMaxWidth)
                .wrapContentHeight()
                // clip=false — top keo xuong khong bi cat som.
                .graphicsLayer { clip = false }
                .padding(
                    PaddingValues(
                        start = SpaceLg,
                        end = SpaceLg,
                        top = SpaceXxs,
                        bottom = EXPANDED_BOTTOM_PAD,
                    )
                ),
        contentAlignment = Alignment.TopCenter,
    ) {
        val visibleCount = minOf(liveStack.size, MAX_VISIBLE_LAYERS)
        // >=4 alert: the leave co the ra ngoai slice — van compose den het anim.
        val visibleAlerts = liveStack.take(visibleCount).toMutableList()
        liveStack
            .firstOrNull { it.sbn.key == leavingKey }
            ?.let { leaving ->
                if (visibleAlerts.none { it.sbn.key == leaving.sbn.key }) visibleAlerts.add(leaving)
            }
        for (alert in visibleAlerts.asReversed()) {
            val layer = liveStack.indexOfFirst { it.sbn.key == alert.sbn.key }
            val styleLayer = layer.coerceAtMost(MAX_VISIBLE_LAYERS - 1)
            key(alert.sbn.key) {
                val isTop = layer == 0
                val isLeaving = alert.sbn.key == leavingKey
                val baseYPx = with(density) { STACK_BASE_Y[styleLayer].toPx() }
                val rubber =
                    when (layer) {
                        0 -> 1f
                        1 -> RUBBER_LAYER1
                        else -> RUBBER_LAYER2
                    }
                val approach =
                    if (layer == 1 && !isLeaving) {
                        underReveal
                    } else {
                        0f
                    }
                // A: under (1) peek luc drag; top clip khi commit expand peekHold→full.
                val useUnderPeekClip = layer == 1 && !isLeaving && canCommitStackSwitch
                val useCommitTopPeek = isTop && !isLeaving && commitTopPeekPx >= 0f
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .zIndex(
                                if (isLeaving) {
                                    (visibleCount - 1).toFloat() - 0.5f
                                } else {
                                    (visibleCount - layer).toFloat()
                                }
                            )
                            .graphicsLayer {
                                // Clip tren peeker (underPeekClip), khong clip o day.
                                clip = false
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                if (isLeaving) {
                                    val t = leaveProgress
                                    translationY = lerp(leaveStartY, leaveNestYPx, t)
                                    val s = lerp(1f, STACK_SCALE[1], t)
                                    scaleX = s
                                    scaleY = s
                                    alpha = lerp(1f, 0f, t)
                                } else {
                                    // Top: base + topSettleY; keo tay = stackVisualDragY → contentOffsetY.
                                    // Under: rubber nhe + underPeekClip — khong boc translationY=dragY.
                                    translationY =
                                        when {
                                            isTop -> baseYPx + topSettleY
                                            layer == 1 -> baseYPx * approach + dragY * rubber
                                            else -> baseYPx * approach
                                        }
                                    val s =
                                        if (isTop) topSettleScale
                                        else
                                            STACK_SCALE[styleLayer] +
                                                (1f - STACK_SCALE[styleLayer]) * approach
                                    scaleX = s
                                    scaleY = s
                                    alpha =
                                        when {
                                            isTop -> topSettleAlpha
                                            // Layer >= 2: luon an.
                                            layer >= 2 -> UNDER_REST_ALPHA
                                            // Under: alpha theo reveal; peek clip lo phan tren.
                                            else -> lerp(UNDER_REST_ALPHA, 1f, approach)
                                        }
                                }
                            }
                ) {
                    val acceptsInput = isTop && !isLeaving
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .then(
                                    when {
                                        useCommitTopPeek ->
                                            Modifier.underPeekClip { commitTopPeekPx }
                                        useUnderPeekClip -> Modifier.underPeekClip { underPeekPx }
                                        !isTop && !isLeaving -> Modifier.underPeekClip { 0f }
                                        else -> Modifier
                                    }
                                ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val startCompact =
                            remember(alert.sbn.key) {
                                initiallyCompact &&
                                    liveStack.size == 1 &&
                                    alert.sbn.key !in promotedExpandedKeys
                            }
                        NotificationAlertCard(
                            notification = alert,
                            interactor = interactor,
                            onDismiss = onDismissTop,
                            initiallyCompact = startCompact,
                            forceExpanded =
                                liveStack.size > 1 || alert.sbn.key in promotedExpandedKeys,
                            interactive = isTop && !isLeaving,
                            reportExpandedChanged = isTop && !isLeaving,
                            enableSwipeGestures = isTop && !isLeaving,
                            // Khong khoa gesture khi isSettling: DOWN moi cancelSettle
                            // (doi the lien tuc).
                            enableStackDrag = true,
                            stackDragKey = topKey to underKey,
                            stackVisualDragY = if (isTop && !isLeaving) dragY else 0f,
                            onStackDragStart = {
                                isDragging = true
                                cancelSettle()
                            },
                            onStackDrag = { y ->
                                dragY = y
                                underReveal = revealFromDragY(y)
                            },
                            onStackDragEnd = { y, vel ->
                                isDragging = false
                                val shouldCommit =
                                    canCommitStackSwitch &&
                                        (y >= commitThresholdPx || vel >= commitVelPx)
                                val nextKey =
                                    if (shouldCommit) {
                                        liveStack.getOrNull(1)?.sbn?.key
                                    } else {
                                        null
                                    }
                                val peekAtRelease = underPeekPx
                                cancelSettle()
                                settleUnderPeekPx = peekAtRelease
                                isSettling = true
                                settlingTopKey = nextKey ?: topKey
                                settleJob =
                                    scope.launch {
                                        try {
                                            val start = y.coerceAtLeast(dragY)
                                            val revealStart =
                                                underReveal.coerceAtLeast(
                                                    revealFromDragY(start)
                                                )
                                            if (nextKey != null) {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                val outgoingKey = liveStack[0].sbn.key
                                                leaveStartY = start
                                                leaveProgress = 0f
                                                leavingKey = outgoingKey
                                                val incomingStartY =
                                                    leaveNestYPx * revealStart +
                                                        start * RUBBER_LAYER1
                                                topSettleY = incomingStartY
                                                val incomingScale =
                                                    lerp(STACK_SCALE[1], 1f, revealStart)
                                                topSettleScale = incomingScale
                                                topSettleAlpha = revealStart
                                                dragY = 0f
                                                underReveal = 0f
                                                // Giu peekHold — animate → full (khong
                                                // set 0: 0 = an clip / top unclipped flash).
                                                val peekHold = peekAtRelease
                                                settleUnderPeekPx = 0f
                                                val fullH =
                                                    underFullHeightPx
                                                        .toFloat()
                                                        .coerceAtLeast(peekHold)
                                                commitTopPeekPx = peekHold
                                                localPinnedKey = nextKey
                                                interactor.pinNotificationAlertByKey(nextKey)
                                                animate(
                                                    initialValue = 0f,
                                                    targetValue = 1f,
                                                    animationSpec =
                                                        tween(
                                                            durationMillis = SNAP_LEAVE_MS,
                                                            easing = FastOutSlowInEasing,
                                                        ),
                                                ) { value, _ ->
                                                    leaveProgress = value
                                                    topSettleScale =
                                                        lerp(incomingScale, 1f, value)
                                                    topSettleAlpha =
                                                        lerp(revealStart, 1f, value)
                                                    topSettleY =
                                                        lerp(incomingStartY, 0f, value)
                                                    commitTopPeekPx =
                                                        lerp(peekHold, fullH, value)
                                                }
                                                leavingKey = null
                                                leaveProgress = 0f
                                                topSettleY = 0f
                                                topSettleScale = 1f
                                                topSettleAlpha = 1f
                                                commitTopPeekPx = -1f
                                            } else {
                                                if (start <= 1f) {
                                                    dragY = 0f
                                                    underReveal = 0f
                                                    settleUnderPeekPx = 0f
                                                    return@launch
                                                }
                                                dragY = start
                                                underReveal = revealStart
                                                val peekStart = peekAtRelease
                                                settleUnderPeekPx = peekStart
                                                animate(
                                                    initialValue = start,
                                                    targetValue = 0f,
                                                    animationSpec =
                                                        tween(
                                                            durationMillis = SNAP_CANCEL_MS,
                                                            easing = FastOutSlowInEasing,
                                                        ),
                                                ) { value, _ ->
                                                    dragY = value
                                                    val t =
                                                        ((start - value) / start).coerceIn(
                                                            0f,
                                                            1f,
                                                        )
                                                    underReveal = lerp(revealStart, 0f, t)
                                                    settleUnderPeekPx = lerp(peekStart, 0f, t)
                                                }
                                            }
                                        } finally {
                                            isSettling = false
                                        }
                                    }
                            },
                            maxStackDragPx = maxDragPx,
                            modifier =
                                Modifier.onSizeChanged { size ->
                                    if (size.height > 0) fullHeights[alert.sbn.key] = size.height
                                },
                        )
                        if (!acceptsInput) {
                            AlertStackDimScrim(
                                0f,
                                consumeTouches = true,
                                touchHeight = {
                                    when {
                                        isLeaving -> Float.POSITIVE_INFINITY
                                        useUnderPeekClip -> underPeekPx
                                        else -> 0f
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
