package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.ExpandedMaxWidth
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.shared.LocalStackOwnsContentClick
import com.android.systemui.axdynamicbar.shared.ShapeCard
import com.android.systemui.axdynamicbar.shared.SpaceLg
import com.android.systemui.axdynamicbar.shared.SpaceXxs
import com.android.systemui.axdynamicbar.shared.openEventContent
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** So lop stack hien thi toi da (top + duoi). */
private const val MAX_VISIBLE_LAYERS = 3

/** Max keo xuong ≈ 48% chieu cao man (gan giua). */
private const val MAX_DRAG_SCREEN_FRACTION = 0.48f

/** Commit khi keo qua ~28% max drag (de doi panel on dinh). */
private const val COMMIT_DRAG_FRACTION = 0.28f

/**
 * Chi under ke (layer 1) hien ro 100% khi progress ~25% max
 * (underReveal = progress / UNDER_REVEAL_AT). Layer >= 2 luon an.
 */
private const val UNDER_REVEAL_AT = 0.25f

/**
 * Cao su gan 0 (chi layer 1): the duoi GIU cho — top keo xuong lo khoang tren
 * (Samsung Live Notification). Rubber lon lam the duoi bi keo xuong theo, khong lo.
 */
private const val RUBBER_LAYER1 = 0.04f
private const val RUBBER_LAYER2 = 0.02f

/** Cancel: chay len cham hon (khong phap). */
private const val SNAP_CANCEL_MS = 520

/**
 * Commit: panel cu CHAY LEN nest under + mo (z duoi top);
 * top moi settle topSettleY (under nest -> rest) cung duration — khong teleport.
 */
private const val SNAP_LEAVE_MS = 560

// The duoi an hoan toan khi nghi, khong hien thi mo khi the khong cung kich thuoc
private const val UNDER_REST_ALPHA = 0f

private val STACK_BASE_Y = listOf(0.dp, 12.dp, 22.dp)
private val STACK_SCALE = listOf(1f, 0.95f, 0.90f)
/** The duoi luon do bong de tao chieu sau stack phan tang. */
private val STACK_SHADOW = listOf(0.dp, 8.dp, 12.dp)

private val EXPANDED_BOTTOM_PAD = 50.dp
private val COMMIT_VELOCITY = 700.dp

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Thu tu stack: pinned truoc, roi cac event con lai theo thu tu cycle chip
 * (events quay vong tu pin, bo notification).
 */
internal fun buildStackOrder(
    events: List<IslandEvent>,
    pinnedEventId: String?,
): List<IslandEvent> {
    if (events.isEmpty()) return emptyList()
    val startIdx =
        pinnedEventId
            ?.let { id -> events.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
    val cycled = List(events.size) { i -> events[(startIdx + i) % events.size] }
    return cycled.filter { it !is IslandEvent.Notification }
}

/** Scrim mo the duoi; consumeTouches chan tap xuyen xuong the duoi. */
@Composable
private fun BoxScope.StackDimScrim(
    alpha: Float,
    consumeTouches: Boolean = false,
    /** Chi vung peek touchable — khong nuot tap phan an (clip). */
    touchHeight: () -> Float = { Float.POSITIVE_INFINITY },
) {
    val currentTouchHeight by rememberUpdatedState(touchHeight)
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.01f && !consumeTouches) return
    // Quan trong: khong chen Box matchParentSize khi khong can consume (touchHeight<=0).
    // Neu van ve scrim trong suot + pointerInput, hit-test dung o scrim → the/nut Pause
    // khong nhan tap (roi bubble len fillMaxSize collapse).
    val peekH = if (consumeTouches) currentTouchHeight() else Float.POSITIVE_INFINITY
    if (a <= 0.01f && consumeTouches && peekH <= 0f) return
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

@Composable
internal fun StackedExpandedPanels(
    events: List<IslandEvent>,
    pinnedEventId: String?,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    // Pin local ngay khi commit — khong cho ViewModel flow tre 1 frame.
    var localPinnedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pinnedEventId) {
        if (localPinnedId != null && pinnedEventId == localPinnedId) {
            localPinnedId = null
        }
    }
    val effectivePinnedId = localPinnedId ?: pinnedEventId
    val liveStack =
        remember(events, effectivePinnedId) { buildStackOrder(events, effectivePinnedId) }
    if (liveStack.isEmpty()) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val maxDragPx =
        with(density) { (config.screenHeightDp * MAX_DRAG_SCREEN_FRACTION).dp.toPx() }
    val commitThresholdPx = maxDragPx * COMMIT_DRAG_FRACTION
    val commitVelPx = with(density) { COMMIT_VELOCITY.toPx() }
    val leaveNestYPx = with(density) { STACK_BASE_Y[1].toPx() }

    var dragY by remember { mutableFloatStateOf(0f) }
    // Chi under ke (layer 1): 0 = an, 1 = ro 100%. Tach khoi dragY de cancel fade ca hanh trinh.
    var underReveal by remember { mutableFloatStateOf(0f) }
    /** Chieu cao clip peek cua under (layer 1); 0 = an. */
    var settleUnderPeekPx by remember { mutableFloatStateOf(0f) }
    /** Full height theo event.id — dung tinh peek + expand-on-commit. */
    val fullHeights = remember { mutableStateMapOf<String, Int>() }
    val underId = liveStack.getOrNull(1)?.id
    val underFullHeightPx = underId?.let { fullHeights[it] } ?: 0
    // Offset Y top ngoai dragY — settle under→rest sau commit (khong teleport).
    var topSettleY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settlingTopId by remember { mutableStateOf<String?>(null) }
    // Mot settle Job duy nhat — tranh gesture 2 danh nhau voi anim 1.
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Panel cu sau pin: z thap hon top moi — di len + mo, khong chan drag top moi.
    var leavingId by remember { mutableStateOf<String?>(null) }
    var leaveProgress by remember { mutableFloatStateOf(0f) }
    var leaveStartY by remember { mutableFloatStateOf(0f) }
    /**
     * Sau commit: clip top moi tu peekHold → full (song song leave).
     * < 0 = khong clip top (rest / xong expand).
     */
    var commitTopPeekPx by remember { mutableFloatStateOf(-1f) }

    val topId = liveStack.first().id
    // Keo xuong luon bat (1 the → snap cancel); pin doi the chi khi >= 2.
    // Truoc day enableStackDrag = size>=2 → 1 pill bi khoa keo giu.
    val canCommitStackSwitch = liveStack.size >= 2

    // Reset drag khi top/under doi ngoai settle (vd. pin tu chip).
    LaunchedEffect(topId, underId) {
        if (isDragging) return@LaunchedEffect
        if (isSettling && topId == settlingTopId) return@LaunchedEffect
        if (settleJob?.isActive == true) return@LaunchedEffect
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        leavingId = null
        leaveProgress = 0f
        dragY = 0f
        underReveal = 0f
        settleUnderPeekPx = 0f
        topSettleY = 0f
        commitTopPeekPx = -1f
    }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        settlingTopId = null
        leavingId = null
        leaveProgress = 0f
        topSettleY = 0f
        settleUnderPeekPx = 0f
        commitTopPeekPx = -1f
    }

    fun revealFromDragY(y: Float): Float =
        ((y / maxDragPx).coerceIn(0f, 1f) / UNDER_REVEAL_AT).coerceIn(0f, 1f)

    fun peekFromDrag(y: Float, reveal: Float): Float {
        if (y <= 0f) return 0f
        return underPeekHeightPx(
            topY = y,
            topHeight = (fullHeights[topId] ?: 0).toFloat(),
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
                // translationY khong mo rong layout — khong clip the keo xuong.
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
        // Ve tu duoi len (layer xa nhat truoc) de z-order dung.
        // key(event.id) + cung nhanh MagneticSwipe→StackPanelCard: pin/swap chi doi
        // zIndex/graphicsLayer/gesture — khong dispose AndroidView/blur.
        for (layer in (visibleCount - 1) downTo 0) {
            val event = liveStack[layer]
            key(event.id) {
                val isTop = layer == 0
                // Leave anim gan theo id — con dung khi pin chua recompose xong.
                val isLeaving = event.id == leavingId
                val baseYPx = with(density) { STACK_BASE_Y[layer].toPx() }
                val rubber =
                    when (layer) {
                        0 -> 1f
                        1 -> RUBBER_LAYER1
                        else -> RUBBER_LAYER2
                    }
                // Chi under ke (layer 1) dung underReveal; layer >= 2 luon an.
                val approach =
                    if (layer == 1 && !isLeaving) {
                        underReveal
                    } else {
                        0f
                    }
                // Under (1) peek luc drag; top clip khi commit expand peekHold→full.
                val useUnderPeekClip = layer == 1 && !isLeaving && canCommitStackSwitch
                val useCommitTopPeek = isTop && !isLeaving && commitTopPeekPx >= 0f
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // Leaving DUOI top moi — chay len nest, khong de len panel chinh.
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
                                    // Tu cho tha tay keo len vi tri cu (under nest) + mo dan.
                                    val t = leaveProgress
                                    translationY = lerp(leaveStartY, leaveNestYPx, t)
                                    val s = lerp(1f, STACK_SCALE[1], t)
                                    scaleX = s
                                    scaleY = s
                                    // Mo song song hanh trinh len — het alpha khi gan nest.
                                    alpha = lerp(1f, 0f, t)
                                } else {
                                    // Top: base + topSettleY; keo tay = contentOffsetY (Magnetic).
                                    // Under: rubber nhe + underPeekClip — khong boc them translationY=dragY.
                                    translationY =
                                        when {
                                            isTop -> baseYPx + topSettleY
                                            layer == 1 -> baseYPx * approach + dragY * rubber
                                            else -> baseYPx * approach
                                        }
                                    val s =
                                        STACK_SCALE[layer] +
                                            (1f - STACK_SCALE[layer]) * approach
                                    scaleX = s
                                    scaleY = s
                                    alpha =
                                        when {
                                            isTop -> 1f
                                            // Layer >= 2: luon an.
                                            layer >= 2 -> UNDER_REST_ALPHA
                                            else -> lerp(UNDER_REST_ALPHA, 1f, approach)
                                        }
                                }
                            }
                            .then(
                                if (layer >= 1 && !isLeaving && approach > 0f) {
                                    Modifier.shadow(
                                        STACK_SHADOW[layer] * approach,
                                        ShapeCard,
                                        clip = false,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                ) {
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
                                )
                                .onSizeChanged { size ->
                                    if (size.height > 0) fullHeights[event.id] = size.height
                                },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        // Mot cay noi dung on dinh theo key(event.id): luon boc
                        // MagneticSwipeToDismiss + StackPanelCard. Doi top/under chi
                        // bat/tat gesture + offset — khong if/else doi nhanh (recreate
                        // AndroidView RemoteViews + islandBlur → nhay nut / lo home).
                        val isInteractiveTop = isTop && !isLeaving
                        MagneticSwipeToDismiss(
                            onDismiss = { interactor.dismissEvent(event) },
                            // Media: vuot ngang = carousel UMO doi session, khong dismiss
                            allowHorizontalDismiss = event !is IslandEvent.Media,
                            gesturesEnabled = isInteractiveTop,
                            stackDragKey =
                                if (isInteractiveTop) topId to underId else event.id,
                            // Khong khoa gesture khi isSettling: DOWN moi cancelSettle
                            // de doi the lien tuc (tranh cho ~560ms SNAP_LEAVE).
                            // 1 the: van keo/giu duoc; tha → snap cancel (khong pin).
                            onStackDragStart =
                                if (isInteractiveTop) {
                                    {
                                        isDragging = true
                                        // Huy leave/peek anim dang chay — keo tiep ngay.
                                        cancelSettle()
                                    }
                                } else {
                                    null
                                },
                            onStackDrag =
                                if (isInteractiveTop) {
                                    { y ->
                                        dragY = y
                                        underReveal = revealFromDragY(y)
                                    }
                                } else {
                                    null
                                },
                            onStackDragEnd =
                                if (isInteractiveTop) {
                                    { y, vel ->
                                        isDragging = false
                                        val shouldCommit =
                                            y >= commitThresholdPx || vel >= commitVelPx
                                        val nextId =
                                            if (shouldCommit && canCommitStackSwitch) {
                                                liveStack[1].id
                                            } else {
                                                null
                                            }
                                        val peekAtRelease = underPeekPx
                                        cancelSettle()
                                        settleUnderPeekPx = peekAtRelease
                                        isSettling = true
                                        settlingTopId = nextId ?: topId
                                        settleJob =
                                            scope.launch {
                                                try {
                                                    val start = y.coerceAtLeast(dragY)
                                                    val revealStart =
                                                        underReveal.coerceAtLeast(
                                                            revealFromDragY(start)
                                                        )
                                                    if (nextId != null) {
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                        // Pin + chip doi ngay; settle chay nen.
                                                        val outgoingId = liveStack[0].id
                                                        leaveStartY = start
                                                        leaveProgress = 0f
                                                        leavingId = outgoingId
                                                        // Vi tri visual under ke truoc pin →
                                                        // settle top moi ve rest.
                                                        val incomingStartY =
                                                            leaveNestYPx * revealStart +
                                                                start * RUBBER_LAYER1
                                                        topSettleY = incomingStartY
                                                        dragY = 0f
                                                        underReveal = 0f
                                                        // Giu peekHold — animate → full (khong
                                                        // set 0: 0 = an clip / top unclipped
                                                        // flash).
                                                        val peekHold = peekAtRelease
                                                        settleUnderPeekPx = 0f
                                                        val fullH =
                                                            underFullHeightPx
                                                                .toFloat()
                                                                .coerceAtLeast(peekHold)
                                                        commitTopPeekPx = peekHold
                                                        localPinnedId = nextId
                                                        interactor.pinEventById(nextId)
                                                        // Panel cu leave nest + top moi settle +
                                                        // peekHold→full (song song).
                                                        animate(
                                                            initialValue = 0f,
                                                            targetValue = 1f,
                                                            animationSpec =
                                                                tween(
                                                                    durationMillis =
                                                                        SNAP_LEAVE_MS,
                                                                    easing =
                                                                        FastOutSlowInEasing,
                                                                ),
                                                        ) { value, _ ->
                                                            leaveProgress = value
                                                            topSettleY =
                                                                lerp(incomingStartY, 0f, value)
                                                            commitTopPeekPx =
                                                                lerp(peekHold, fullH, value)
                                                        }
                                                        leavingId = null
                                                        leaveProgress = 0f
                                                        topSettleY = 0f
                                                        // Xong expand: bo clip top.
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
                                                        // Top snap dragY→0; under fade + peek→0
                                                        animate(
                                                            initialValue = start,
                                                            targetValue = 0f,
                                                            animationSpec =
                                                                tween(
                                                                    durationMillis =
                                                                        SNAP_CANCEL_MS,
                                                                    easing =
                                                                        FastOutSlowInEasing,
                                                                ),
                                                        ) { value, _ ->
                                                            dragY = value
                                                            val t =
                                                                ((start - value) / start)
                                                                    .coerceIn(0f, 1f)
                                                            underReveal =
                                                                lerp(revealStart, 0f, t)
                                                            settleUnderPeekPx =
                                                                lerp(peekStart, 0f, t)
                                                        }
                                                    }
                                                } finally {
                                                    isSettling = false
                                                }
                                            }
                                    }
                                } else {
                                    null
                                },
                            maxStackDragPx = if (isInteractiveTop) maxDragPx else 0f,
                            // Cung elastic alert: resistedDownwardOffset @ 75% max
                            elasticStackDrag = true,
                            // Dich visual duoi pointer — chi top interactive.
                            contentOffsetY = if (isInteractiveTop) dragY else 0f,
                        ) {
                            StackPanelCard(event, interactor, hapticsViewModelFactory)
                        }
                        // Scrim alpha 0 — chi consume tap (vung peek); giong alert stack.
                        if (!isInteractiveTop) {
                            StackDimScrim(
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

/**
 * Tap vung content/card (khong phai nut action) → mo app tuong ung.
 * Tai su dung openEventContent / openMediaApp / launchNotification.
 */
private fun openStackedEvent(
    context: Context,
    event: IslandEvent,
    interactor: IslandActions,
) {
    when (event) {
        is IslandEvent.Timer ->
            openEventContent(context, event.contentIntent, event.packageName)
        is IslandEvent.Stopwatch ->
            openEventContent(context, event.contentIntent, event.packageName)
        is IslandEvent.NowPlaying ->
            openEventContent(
                context,
                event.sbn?.notification?.contentIntent,
                event.sbn?.packageName.orEmpty(),
            )
        is IslandEvent.Media -> interactor.openMediaApp(event.mediaKey)
        is IslandEvent.Notification ->
            interactor.launchNotificationDismissingKeyguard(event)
        is IslandEvent.PromotedOngoing ->
            openEventContent(
                context,
                event.sbn.notification?.contentIntent,
                event.sbn.packageName.orEmpty(),
            )
        is IslandEvent.Sports ->
            openEventContent(
                context,
                event.sbn?.notification?.contentIntent,
                event.sbn?.packageName.orEmpty(),
            )
        is IslandEvent.AospChip -> {
            when (val b = event.active.clickBehavior) {
                is OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification -> b.onClick()
                is OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification -> b.onClick()
                is OngoingActivityChipModel.ClickBehavior.None -> {}
                else -> {}
            }
        }
        else -> return
    }
    interactor.collapseIsland()
}

@Composable
private fun StackPanelCard(
    event: IslandEvent,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
) {
    val context = LocalContext.current
    // Media / chronometer / ongoing tu xu ly tap noi dung (khong PrimaryCard outer click).
    // LocalStackOwnsContentClick: tat onContentClick trong ExpandedCardLayout (tranh double-open).
    // PrimaryCard onClick: padding / vung khong phai nut → mo app; ActionChip van thang hit-test.
    CompositionLocalProvider(LocalStackOwnsContentClick provides true) {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (event) {
                is IslandEvent.Media -> MediaCard(event, interactor)
                // Chronometer & Promoted ongoing: khung QS — khong PrimaryCard SpaceXxl.
                is IslandEvent.Timer -> ChronometerQsCard { TimerExpanded(event, interactor) }
                is IslandEvent.Stopwatch ->
                    ChronometerQsCard { StopwatchExpanded(event, interactor) }
                is IslandEvent.PromotedOngoing ->
                    ChronometerQsCard { PromotedOngoingExpanded(event, interactor) }
                else ->
                    // Khong AnimatedContent — doi panel bang stack swap; crossfade gay nhay.
                    PrimaryCard(onClick = { openStackedEvent(context, event, interactor) }) {
                        ExpandedEventContent(event, interactor, hapticsViewModelFactory)
                    }
            }
        }
    }
}
