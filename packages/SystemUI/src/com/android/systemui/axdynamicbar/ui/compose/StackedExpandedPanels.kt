package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
import android.os.Trace
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.ExpandedMaxWidth
import com.android.systemui.axdynamicbar.shared.IslandActions
import com.android.systemui.axdynamicbar.shared.LocalBlurAlpha
import com.android.systemui.axdynamicbar.shared.LocalStackOwnsContentClick
import com.android.systemui.axdynamicbar.shared.LocalStatusBarPaintedCardIsTop
import com.android.systemui.axdynamicbar.shared.LocalStatusBarPaintedCardOwnerKey
import com.android.systemui.axdynamicbar.shared.LocalStatusBarCardNaturalMeasureContextToken
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackCardNaturalSizeReporter
import com.android.systemui.axdynamicbar.shared.statusBarStackEventOwnerKey
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragPhaseReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragSettlingReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragYReporter
import com.android.systemui.axdynamicbar.shared.StackExpandedCardSwipeStartDp
import com.android.systemui.axdynamicbar.shared.StackExpandedCommitThresholdDp
import com.android.systemui.axdynamicbar.shared.StackExpandedDeepRubberStartDp
import com.android.systemui.axdynamicbar.shared.StackExpandedDragPhase
import com.android.systemui.axdynamicbar.shared.StackExpandedLeaveAlphaMs
import com.android.systemui.axdynamicbar.shared.StackExpandedResetSpringStiffness
import com.android.systemui.axdynamicbar.shared.StackExpandedScaleDepth0
import com.android.systemui.axdynamicbar.shared.StackExpandedScaleDepth1
import com.android.systemui.axdynamicbar.shared.StackExpandedScaleDepth2Legacy
import com.android.systemui.axdynamicbar.shared.StackExpandedScaleSpringDamping
import com.android.systemui.axdynamicbar.shared.StackExpandedScaleSpringStiffness
import com.android.systemui.axdynamicbar.shared.StackExpandedSettleSpringDamping
import com.android.systemui.axdynamicbar.shared.StackExpandedSettleSpringStiffness
import com.android.systemui.axdynamicbar.shared.StackExpandedTopAlphaC1x
import com.android.systemui.axdynamicbar.shared.StackExpandedTopAlphaC1y
import com.android.systemui.axdynamicbar.shared.StackExpandedTopAlphaC2x
import com.android.systemui.axdynamicbar.shared.StackExpandedTopAlphaC2y
import com.android.systemui.axdynamicbar.shared.ShapeCard
import com.android.systemui.axdynamicbar.shared.SpaceLg
import com.android.systemui.axdynamicbar.shared.SpaceXxs
import com.android.systemui.axdynamicbar.shared.StatusBarCardNaturalCacheEntry
import com.android.systemui.axdynamicbar.shared.canOpenIslandEventApp
import com.android.systemui.axdynamicbar.shared.openIslandEventAndCollapse
import com.android.systemui.axdynamicbar.shared.stackExpandedApplyDeepRubber
import com.android.systemui.axdynamicbar.shared.stackExpandedDragPhase
import com.android.systemui.axdynamicbar.shared.stackExpandedCommitLateralSeedsPx
import com.android.systemui.axdynamicbar.shared.stackExpandedIncomingSurfaceTranslationYPx
import com.android.systemui.axdynamicbar.shared.stackExpandedLayerContentOffsetXPx
import com.android.systemui.axdynamicbar.shared.stackExpandedLayerZIndex
import com.android.systemui.axdynamicbar.shared.stackExpandedOutgoingLeaveTranslationYPx
import com.android.systemui.axdynamicbar.shared.stackExpandedPhaseOnPointerRelease
import com.android.systemui.axdynamicbar.shared.stackExpandedReleaseIsCancelEnd
import com.android.systemui.axdynamicbar.shared.stackExpandedSettleFinallyActions
import com.android.systemui.axdynamicbar.shared.stackExpandedShouldCancelSettleOnGesturesDisabled
import com.android.systemui.axdynamicbar.shared.statusBarCardNaturalPublishAfterLayout
import com.android.systemui.axdynamicbar.shared.statusBarStackShouldCommitOnRelease
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** So lop stack hien thi toi da (top + duoi). Layer >=2 alpha=0 luc rest -- giu hit-test 11/11. */
private const val MAX_VISIBLE_LAYERS = 3

/** Max keo xuong ~= 48% chieu cao man (tran; khong dung lam commit gate). */
private const val MAX_DRAG_SCREEN_FRACTION = 0.48f

/**
 * Samsung cardSwipeStartDp -- release ngang SB (strict > 126dp).
 */
internal val HORIZONTAL_DISMISS_THRESHOLD = StackExpandedCardSwipeStartDp

/**
 * Chi under ke (layer 1) hien ro 100% khi progress ~25% max
 * (underReveal = progress / UNDER_REVEAL_AT). Layer >= 2 luon an.
 * Moc theo maxDrag (tran keo), khong phai 85dp commit.
 */
private const val UNDER_REVEAL_AT = 0.25f

/**
 * Cao su under (layer 1): giu thap de peek clip lo the duoi (Live Notification).
 * Samsung under 0.10*dy -- UNPROVEN parity quang hoc; khong doi khi chua do runtime.
 */
private const val RUBBER_LAYER1 = 0.04f
private const val RUBBER_LAYER2 = 0.02f

// The duoi an hoan toan khi nghi, khong hien thi mo khi the khong cung kich thuoc
private const val UNDER_REST_ALPHA = 0f

private val STACK_BASE_Y = listOf(0.dp, 12.dp, 22.dp)
/** depth0/1 Samsung; depth2 legacy 0.90 (0.84 unrendered -- khong port). */
private val STACK_SCALE =
    listOf(StackExpandedScaleDepth0, StackExpandedScaleDepth1, StackExpandedScaleDepth2Legacy)
/** The duoi luon do bong de tao chieu sau stack phan tang. */
private val STACK_SHADOW = listOf(0.dp, 8.dp, 12.dp)

internal val EXPANDED_BOTTOM_PAD = 50.dp

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Commit leave: XY spring 100/0.78. */
private val StackCommitXySpring =
    spring<Float>(
        dampingRatio = StackExpandedSettleSpringDamping,
        stiffness = StackExpandedSettleSpringStiffness,
    )

/** Cancel/reset: XY spring 200/0.78. */
private val StackResetXySpring =
    spring<Float>(
        dampingRatio = StackExpandedSettleSpringDamping,
        stiffness = StackExpandedResetSpringStiffness,
    )

/** Commit leave scale: spring 200/0.8131728 -- doc lap XY. */
private val StackLeaveScaleSpring =
    spring<Float>(
        dampingRatio = StackExpandedScaleSpringDamping,
        stiffness = StackExpandedScaleSpringStiffness,
    )

/**
 * topAlphaInterpolator (0.33, 0.2, 0.68, 1).
 * UNPROVEN target: Samsung fade baseColorView; AxDB van lerp graphicsLayer.alpha
 * outgoing (khong doi blur CardLocal ownership). Reset scale L908-918 chua port.
 */
private val StackLeaveTopAlphaEasing =
    CubicBezierEasing(
        StackExpandedTopAlphaC1x,
        StackExpandedTopAlphaC1y,
        StackExpandedTopAlphaC2x,
        StackExpandedTopAlphaC2y,
    )

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

/** Rang buoc keo-tha stack dung chung cho card event va alert. */
internal class StackCardDragBindings(
    val isInteractiveTop: Boolean,
    val stackDragKey: Any,
    val onStackDragStart: (() -> Unit)?,
    val onStackDragXY: ((dx: Float, y: Float) -> Unit)?,
    val onStackDragEnd: ((y: Float, vel: Float) -> Unit)?,
    val maxStackDragPx: Float,
    val contentOffsetX: () -> Float,
    val contentOffsetY: () -> Float,
)

@Composable
internal fun StackedExpandedPanels(
    events: List<IslandEvent>,
    pinnedEventId: String?,
    interactor: IslandActions,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    stackGesturesEnabled: Boolean,
) {
    // Pin local ngay khi commit -- khong cho ViewModel flow tre 1 frame.
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

    SamsungCardStackContainer(
        liveStack = liveStack,
        keyOf = { it.id },
        ownerIdOf = { it.id },
        stackGesturesEnabled = stackGesturesEnabled,
        traceTag = "AxDbStack",
        onCommitPin = { nextId ->
            localPinnedId = nextId
            interactor.pinEventById(nextId)
        },
    ) { event, bindings ->
        // Mot cay noi dung on dinh theo key(event.id): luon boc MagneticSwipeToDismiss + StackPanelCard.
        MagneticSwipeToDismiss(
            onDismiss = { interactor.dismissEventFromExpandedSwipe(event) },
            // The Media chi cho vuot ngang de an ngay khi dang tam dung; dang phat thi khoa ngang.
            allowHorizontalDismiss = event !is IslandEvent.Media || !event.isPlaying,
            // Ngang SB: detach + release strict >126dp (khong half/vel).
            horizontalDetachThreshold = HORIZONTAL_DISMISS_THRESHOLD,
            horizontalReleaseStrictThreshold = HORIZONTAL_DISMISS_THRESHOLD,
            cancelOnConsumedRelease = true,
            gesturesEnabled = bindings.isInteractiveTop && stackGesturesEnabled,
            stackDragKey = bindings.stackDragKey,
            onStackDragStart = bindings.onStackDragStart,
            onStackDragXY = bindings.onStackDragXY,
            onStackDragEnd = bindings.onStackDragEnd,
            maxStackDragPx = bindings.maxStackDragPx,
            elasticStackDrag = false,
            tiltEnabled = false,
            contentOffsetX = bindings.contentOffsetX,
            contentOffsetY = bindings.contentOffsetY,
        ) {
            StackPanelCard(event, interactor, hapticsViewModelFactory)
        }
    }
}

/** Container stack Samsung dung chung cho ca island event va notification alert. */
@Composable
internal fun <T> SamsungCardStackContainer(
    liveStack: List<T>,
    keyOf: (T) -> String,
    ownerIdOf: (T) -> String,
    stackGesturesEnabled: Boolean,
    traceTag: String,
    onCommitPin: (String) -> Unit,
    onTopCardBoundsPx: ((left: Int, top: Int, right: Int, bottom: Int) -> Unit)? = null,
    cardContent: @Composable (item: T, bindings: StackCardDragBindings) -> Unit,
) {
    if (liveStack.isEmpty()) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val maxDragPx =
        with(density) { (config.screenHeightDp * MAX_DRAG_SCREEN_FRACTION).dp.toPx() }
    // Samsung: child.getY() > 85dp -- dragYPx tu rest; khong 28% maxDrag / velocity.
    val commitThresholdPx =
        with(density) { StackExpandedCommitThresholdDp.dp.toPx() }
    val deepRubberStartPx =
        with(density) { StackExpandedDeepRubberStartDp.dp.toPx() }
    val leaveNestYPx = with(density) { STACK_BASE_Y[1].toPx() }

    var dragY by remember { mutableFloatStateOf(0f) }
    /** Finger dx raw (AXIS_STACK) -- factor 0.10/0.05 held; commit dung seed tach. */
    var heldFingerDx by remember { mutableFloatStateOf(0f) }
    /** Outgoing painted X luc commit -- settle ve 0 cung leaveProgress. */
    var leaveStartX by remember { mutableFloatStateOf(0f) }
    /** Incoming (top moi) painted X luc commit -- settle ve 0 cung XY spring. */
    var topSettleX by remember { mutableFloatStateOf(0f) }
    // Bao cao SSOT dragY / settling / phase len morph shared glass.
    val stackDragYReporter = LocalStatusBarStackDragYReporter.current
    val stackDragSettlingReporter = LocalStatusBarStackDragSettlingReporter.current
    val stackDragPhaseReporter = LocalStatusBarStackDragPhaseReporter.current
    // Chi under ke (layer 1): 0 = an, 1 = ro 100%. Tach khoi dragY de cancel fade ca hanh trinh.
    var underReveal by remember { mutableFloatStateOf(0f) }
    /** Chieu cao clip peek cua under (layer 1); 0 = an. */
    var settleUnderPeekPx by remember { mutableFloatStateOf(0f) }
    /** Full height theo key -- peek local; co the stale nhe khi doi context. */
    val fullHeights = remember { mutableStateMapOf<String, Int>() }
    // Host publish dedupe -- HashMap (khong snapshot); chi write khi entry doi.
    val lastHostNaturalPublish =
        remember { mutableMapOf<String, StatusBarCardNaturalCacheEntry>() }
    val cardNaturalSizeReporter = LocalStatusBarStackCardNaturalSizeReporter.current
    val measureContextToken = LocalStatusBarCardNaturalMeasureContextToken.current
    val topKey = keyOf(liveStack.first())
    val underKey = liveStack.getOrNull(1)?.let(keyOf)
    val underFullHeightPx = underKey?.let { fullHeights[it] } ?: 0
    // Offset Y top ngoai dragY -- settle under -> rest sau commit (khong teleport).
    var topSettleY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var settlingTopKey by remember { mutableStateOf<String?>(null) }
    // Mot settle Job duy nhat -- tranh gesture 2 danh nhau voi anim 1.
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Gen settle -- finally stale/interrupt khong Idle de ghi de Held moi.
    var settleGen by remember { mutableIntStateOf(0) }
    // Panel cu sau pin: z thap hon top moi -- di len + mo, khong chan drag top moi.
    var leavingKey by remember { mutableStateOf<String?>(null) }
    /** XY leave progress (giu vuot bien lo xo, khong clamp translation). */
    var leaveProgress by remember { mutableFloatStateOf(0f) }
    /** Scale giu vuot bien lo xo; alpha clamp [0,1] khi ve. */
    var leaveScaleProgress by remember { mutableFloatStateOf(0f) }
    var leaveAlphaProgress by remember { mutableFloatStateOf(0f) }
    var leaveStartY by remember { mutableFloatStateOf(0f) }
    /**
     * Sau commit: clip top moi tu peekHold -> full (song song leave).
     * < 0 = khong clip top (rest / xong expand).
     */
    var commitTopPeekPx by remember { mutableFloatStateOf(-1f) }

    // Keo xuong luon bat (1 the -> snap cancel); pin doi the chi khi >= 2.
    val canCommitStackSwitch = liveStack.size >= 2

    // Phase ownership sync cung frame dragY -- KHONG SideEffect sole source.
    fun publishStackDragPhase(phase: StackExpandedDragPhase) {
        stackDragPhaseReporter?.invoke(phase)
        if (Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
            Trace.instant(Trace.TRACE_TAG_VIEW, "$traceTag/phaseSync:$phase")
        }
    }

    // Reset drag khi top/under doi ngoai settle (vd. pin tu chip).
    LaunchedEffect(topKey, underKey) {
        if (isDragging) return@LaunchedEffect
        if (isSettling && topKey == settlingTopKey) return@LaunchedEffect
        if (settleJob?.isActive == true) return@LaunchedEffect
        settleGen++
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        leavingKey = null
        leaveProgress = 0f
        leaveScaleProgress = 0f
        leaveAlphaProgress = 0f
        dragY = 0f
        heldFingerDx = 0f
        leaveStartX = 0f
        topSettleX = 0f
        stackDragYReporter?.invoke(0f)
        stackDragSettlingReporter?.invoke(false)
        publishStackDragPhase(StackExpandedDragPhase.Idle)
        underReveal = 0f
        settleUnderPeekPx = 0f
        topSettleY = 0f
        commitTopPeekPx = -1f
    }

    fun cancelSettle() {
        // Invalidate ownership TRUOC cancel -- stale Job finally no-op (khong xoa settle B).
        settleGen++
        settleJob?.cancel()
        settleJob = null
        isSettling = false
        settlingTopKey = null
        leavingKey = null
        leaveProgress = 0f
        leaveScaleProgress = 0f
        leaveAlphaProgress = 0f
        topSettleY = 0f
        settleUnderPeekPx = 0f
        commitTopPeekPx = -1f
        leaveStartX = 0f
        topSettleX = 0f
        // Cancel settle -> bao settling=false ngay; onStackDragStart se Held sau cancelSettle.
        stackDragSettlingReporter?.invoke(false)
    }

    // Dong panel / roi trang thai nghi: huy settle va drag dang do de the isLeaving / under
    // khong tiep tuc dich chuyen hay giu blur suot collapse morph.
    LaunchedEffect(stackGesturesEnabled) {
        if (
            stackExpandedShouldCancelSettleOnGesturesDisabled(
                stackGesturesEnabled = stackGesturesEnabled,
                isSettling = isSettling,
                leavingId = leavingKey,
                isDragging = isDragging,
                dragYPx = dragY,
                underReveal = underReveal,
            )
        ) {
            cancelSettle()
            isDragging = false
            dragY = 0f
            heldFingerDx = 0f
            underReveal = 0f
            stackDragYReporter?.invoke(0f)
            publishStackDragPhase(StackExpandedDragPhase.Idle)
        }
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

    val underPeekPx = if (isSettling) settleUnderPeekPx else peekFromDrag(dragY, underReveal)

    // Phase derived -- chi Trace/audit; ownership SSOT = publishStackDragPhase sync.
    val dragPhase = stackExpandedDragPhase(isDragging, isSettling, leavingKey)
    val lastTracedPhaseRef = remember { object { var value = dragPhase } }
    SideEffect {
        if (dragPhase != lastTracedPhaseRef.value) {
            lastTracedPhaseRef.value = dragPhase
            if (Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
                Trace.instant(Trace.TRACE_TAG_VIEW, "$traceTag/phaseAudit:$dragPhase")
            }
        }
    }

    // Sync pointer + settle animate frame goi reporter truc tiep; dispose -> 0 / Idle.
    DisposableEffect(stackDragYReporter, stackDragSettlingReporter, stackDragPhaseReporter) {
        onDispose {
            settleGen++
            settleJob?.cancel()
            settleJob = null
            stackDragYReporter?.invoke(0f)
            stackDragSettlingReporter?.invoke(false)
            stackDragPhaseReporter?.invoke(StackExpandedDragPhase.Idle)
        }
    }

    Box(
        modifier =
            Modifier.widthIn(max = ExpandedMaxWidth)
                .wrapContentHeight()
                // translationY khong mo rong layout -- khong clip the keo xuong.
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
        // >=4 item: the leave co the ra ngoai slice -- van compose den het anim.
        val visibleItems = liveStack.take(visibleCount).toMutableList()
        liveStack
            .firstOrNull { keyOf(it) == leavingKey }
            ?.let { leaving ->
                if (visibleItems.none { keyOf(it) == keyOf(leaving) }) visibleItems.add(leaving)
            }
        for (item in visibleItems.asReversed()) {
            val itemKey = keyOf(item)
            val ownerId = ownerIdOf(item)
            val rawLayer = liveStack.indexOfFirst { keyOf(it) == itemKey }
            if (rawLayer < 0) continue
            val styleLayer = rawLayer.coerceAtMost(MAX_VISIBLE_LAYERS - 1)
            key(itemKey) {
                val isTop = rawLayer == 0
                val isLeaving = itemKey == leavingKey
                val baseYPx = with(density) { STACK_BASE_Y[styleLayer].toPx() }
                val rubber =
                    when (rawLayer) {
                        0 -> 1f
                        1 -> RUBBER_LAYER1
                        else -> RUBBER_LAYER2
                    }
                val approach =
                    if (rawLayer == 1 && !isLeaving) {
                        underReveal
                    } else {
                        0f
                    }
                val useUnderPeekClip = rawLayer == 1 && !isLeaving && canCommitStackSwitch
                val useCommitTopPeek = isTop && !isLeaving && commitTopPeekPx >= 0f
                val cardOffsetX = {
                    stackExpandedLayerContentOffsetXPx(
                        layer = rawLayer,
                        isLeaving = isLeaving,
                        leaveStartXPx = leaveStartX,
                        leaveProgress = leaveProgress,
                        isIncomingSettling = isTop && !isLeaving && isSettling && leavingKey != null,
                        incomingSettleXPx = topSettleX,
                        heldFingerDxPx = heldFingerDx,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .zIndex(
                                stackExpandedLayerZIndex(
                                    layer = rawLayer,
                                    visibleCount = visibleItems.size,
                                    isLeaving = isLeaving,
                                )
                            )
                            .graphicsLayer {
                                clip = false
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                if (isLeaving) {
                                    translationY =
                                        stackExpandedOutgoingLeaveTranslationYPx(
                                            leaveProgress = leaveProgress,
                                            leaveStartYPx = leaveStartY,
                                            leaveNestYPx = leaveNestYPx,
                                        )
                                    val s = lerp(1f, STACK_SCALE[1], leaveScaleProgress)
                                        .coerceAtLeast(0.01f)
                                    scaleX = s
                                    scaleY = s
                                    alpha = lerp(1f, 0f, leaveAlphaProgress.coerceIn(0f, 1f))
                                } else {
                                    translationY =
                                        when {
                                            isTop ->
                                                baseYPx +
                                                    stackExpandedIncomingSurfaceTranslationYPx(
                                                        phase = dragPhase,
                                                        dragYPx = 0f,
                                                        topSettleYPx = topSettleY,
                                                    )
                                            rawLayer == 1 -> baseYPx * approach + dragY * rubber
                                            else -> baseYPx * approach
                                        }
                                    val s =
                                        STACK_SCALE[styleLayer] +
                                            (1f - STACK_SCALE[styleLayer]) * approach
                                    scaleX = s
                                    scaleY = s
                                    alpha =
                                        when {
                                            isTop -> 1f
                                            rawLayer >= 2 -> UNDER_REST_ALPHA
                                            else -> lerp(UNDER_REST_ALPHA, 1f, approach)
                                        }
                                }
                            }
                            .then(
                                if (rawLayer >= 1 && !isLeaving && approach > 0f) {
                                    Modifier.shadow(
                                        STACK_SHADOW[styleLayer] * approach,
                                        ShapeCard,
                                        clip = false,
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                ) {
                    val isInteractiveTop = isTop && !isLeaving
                    val underBlurFactor =
                        if (isTop || isLeaving) 1f else approach.coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .then(
                                    when {
                                        useCommitTopPeek ->
                                            Modifier.underPeekClip(cardOffsetX) { commitTopPeekPx }
                                        useUnderPeekClip ->
                                            Modifier.underPeekClip(cardOffsetX) { underPeekPx }
                                        !isTop && !isLeaving -> Modifier.underPeekClip { 0f }
                                        else -> Modifier
                                    }
                                )
                                .onPlaced { coordinates ->
                                    val w = coordinates.size.width
                                    val h = coordinates.size.height
                                    if (h <= 0) return@onPlaced
                                    if (fullHeights[itemKey] != h) {
                                        fullHeights[itemKey] = h
                                    }
                                    val published =
                                        statusBarCardNaturalPublishAfterLayout(
                                            previousPublished = lastHostNaturalPublish[ownerId],
                                            widthPx = w,
                                            heightPx = h,
                                            layoutContextToken = measureContextToken,
                                        )
                                    if (published != null) {
                                        lastHostNaturalPublish[ownerId] = published
                                        cardNaturalSizeReporter?.invoke(
                                            ownerId,
                                            published.measuredWidthPx,
                                            published.heightPx,
                                            published.contextToken,
                                        )
                                    }
                                }
                                .then(
                                    if (isInteractiveTop && onTopCardBoundsPx != null) {
                                        Modifier.onGloballyPositioned { coords ->
                                            val b = coords.boundsInWindow()
                                            if (b.width > 0f && b.height > 0f) {
                                                onTopCardBoundsPx(
                                                    b.left.toInt().coerceAtLeast(0),
                                                    b.top.toInt().coerceAtLeast(0),
                                                    b.right.toInt(),
                                                    b.bottom.toInt(),
                                                )
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val bindings =
                            StackCardDragBindings(
                                isInteractiveTop = isInteractiveTop,
                                stackDragKey = if (isInteractiveTop) topKey to underKey else itemKey,
                                onStackDragStart =
                                    if (isInteractiveTop) {
                                        {
                                            if (isSettling || leavingKey != null) {
                                                cancelSettle()
                                            }
                                            isDragging = true
                                            dragY = 0f
                                            heldFingerDx = 0f
                                            leaveStartX = 0f
                                            topSettleX = 0f
                                            stackDragYReporter?.invoke(0f)
                                            underReveal = 0f
                                            publishStackDragPhase(StackExpandedDragPhase.Held)
                                        }
                                    } else {
                                        null
                                    },
                                onStackDragXY =
                                    if (isInteractiveTop) {
                                        { dx, y ->
                                            heldFingerDx = dx
                                            val rubbered =
                                                stackExpandedApplyDeepRubber(
                                                    dragPx = y.coerceIn(0f, maxDragPx),
                                                    deepStartPx = deepRubberStartPx,
                                                )
                                            dragY = rubbered
                                            stackDragYReporter?.invoke(rubbered)
                                            underReveal = revealFromDragY(rubbered)
                                        }
                                    } else {
                                        null
                                    },
                                onStackDragEnd =
                                    if (isInteractiveTop) {
                                        { y, vel ->
                                            isDragging = false
                                            val paintedY = dragY
                                            val isCancel = stackExpandedReleaseIsCancelEnd(y, vel)
                                            val shouldCommit =
                                                statusBarStackShouldCommitOnRelease(
                                                    paintedDragYPx = paintedY,
                                                    commitThresholdPx = commitThresholdPx,
                                                    canCommitStackSwitch = canCommitStackSwitch,
                                                    isCancelOrLostPointer = isCancel,
                                                )
                                            publishStackDragPhase(
                                                stackExpandedPhaseOnPointerRelease(shouldCommit)
                                            )
                                            val nextKey =
                                                if (shouldCommit) {
                                                    keyOf(liveStack[1])
                                                } else {
                                                    null
                                                }
                                            val peekAtRelease = underPeekPx
                                            cancelSettle()
                                            settleUnderPeekPx = peekAtRelease
                                            isSettling = true
                                            stackDragSettlingReporter?.invoke(true)
                                            settlingTopKey = nextKey ?: topKey
                                            val mySettleGen = ++settleGen
                                            settleJob =
                                                scope.launch {
                                                    try {
                                                        val start = paintedY
                                                        val paintedDx = heldFingerDx
                                                        val revealStart =
                                                            underReveal.coerceAtLeast(
                                                                revealFromDragY(start)
                                                            )
                                                        if (nextKey != null) {
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            val outgoingKey = keyOf(liveStack[0])
                                                            leaveStartY = start
                                                            leaveProgress = 0f
                                                            leaveScaleProgress = 0f
                                                            leaveAlphaProgress = 0f
                                                            leavingKey = outgoingKey
                                                            if (Trace.isTagEnabled(Trace.TRACE_TAG_VIEW)) {
                                                                Trace.instant(
                                                                    Trace.TRACE_TAG_VIEW,
                                                                    "$traceTag/commitLeave",
                                                                )
                                                            }
                                                            val incomingStartY =
                                                                leaveNestYPx * revealStart +
                                                                    start * RUBBER_LAYER1
                                                            val xSeeds =
                                                                stackExpandedCommitLateralSeedsPx(
                                                                    paintedDx
                                                                )
                                                            leaveStartX = xSeeds.outgoingPaintedXPx
                                                            topSettleX = xSeeds.incomingPaintedXPx
                                                            topSettleY = incomingStartY
                                                            dragY = 0f
                                                            heldFingerDx = 0f
                                                            stackDragYReporter?.invoke(0f)
                                                            underReveal = 0f
                                                            val peekHold = peekAtRelease
                                                            settleUnderPeekPx = 0f
                                                            val fullH =
                                                                underFullHeightPx
                                                                    .toFloat()
                                                                    .coerceAtLeast(peekHold)
                                                            commitTopPeekPx = peekHold
                                                            onCommitPin(nextKey)
                                                            coroutineScope {
                                                                launch {
                                                                    animate(
                                                                        initialValue = 0f,
                                                                        targetValue = 1f,
                                                                        animationSpec =
                                                                            StackCommitXySpring,
                                                                    ) { value, _ ->
                                                                        leaveProgress = value
                                                                        topSettleY =
                                                                            lerp(
                                                                                incomingStartY,
                                                                                0f,
                                                                                value,
                                                                            )
                                                                        topSettleX =
                                                                            lerp(
                                                                                xSeeds
                                                                                    .incomingPaintedXPx,
                                                                                0f,
                                                                                value,
                                                                            )
                                                                        val peekT =
                                                                            value.coerceIn(0f, 1f)
                                                                        commitTopPeekPx =
                                                                            lerp(
                                                                                peekHold,
                                                                                fullH,
                                                                                peekT,
                                                                            )
                                                                    }
                                                                }
                                                                launch {
                                                                    animate(
                                                                        initialValue = 0f,
                                                                        targetValue = 1f,
                                                                        animationSpec =
                                                                            StackLeaveScaleSpring,
                                                                    ) { value, _ ->
                                                                        leaveScaleProgress = value
                                                                    }
                                                                }
                                                                launch {
                                                                    animate(
                                                                        initialValue = 0f,
                                                                        targetValue = 1f,
                                                                        animationSpec =
                                                                            tween(
                                                                                durationMillis =
                                                                                    StackExpandedLeaveAlphaMs,
                                                                                easing =
                                                                                    StackLeaveTopAlphaEasing,
                                                                            ),
                                                                    ) { value, _ ->
                                                                        leaveAlphaProgress = value
                                                                    }
                                                                }
                                                            }
                                                            leavingKey = null
                                                            leaveProgress = 0f
                                                            leaveScaleProgress = 0f
                                                            leaveAlphaProgress = 0f
                                                            topSettleY = 0f
                                                            topSettleX = 0f
                                                            leaveStartX = 0f
                                                            commitTopPeekPx = -1f
                                                        } else {
                                                            if (start <= 1f && kotlin.math.abs(paintedDx) <= 1f) {
                                                                dragY = 0f
                                                                heldFingerDx = 0f
                                                                stackDragYReporter?.invoke(0f)
                                                                underReveal = 0f
                                                                settleUnderPeekPx = 0f
                                                                return@launch
                                                            }
                                                            dragY = start
                                                            heldFingerDx = paintedDx
                                                            stackDragYReporter?.invoke(start)
                                                            underReveal = revealStart
                                                            val peekStart = peekAtRelease
                                                            settleUnderPeekPx = peekStart
                                                            coroutineScope {
                                                                if (start > 1f) {
                                                                    launch {
                                                                        animate(
                                                                            initialValue = start,
                                                                            targetValue = 0f,
                                                                            animationSpec =
                                                                                StackResetXySpring,
                                                                        ) { value, _ ->
                                                                            dragY = value
                                                                            stackDragYReporter
                                                                                ?.invoke(value)
                                                                            val t =
                                                                                ((start - value) /
                                                                                        start)
                                                                                    .coerceIn(
                                                                                        0f,
                                                                                        1f,
                                                                                    )
                                                                            underReveal =
                                                                                lerp(
                                                                                    revealStart,
                                                                                    0f,
                                                                                    t,
                                                                                )
                                                                            settleUnderPeekPx =
                                                                                lerp(
                                                                                    peekStart,
                                                                                    0f,
                                                                                    t,
                                                                                )
                                                                        }
                                                                    }
                                                                } else {
                                                                    dragY = 0f
                                                                    stackDragYReporter?.invoke(0f)
                                                                    underReveal = 0f
                                                                    settleUnderPeekPx = 0f
                                                                }
                                                                if (kotlin.math.abs(paintedDx) > 1f) {
                                                                    launch {
                                                                        animate(
                                                                            initialValue =
                                                                                paintedDx,
                                                                            targetValue = 0f,
                                                                            animationSpec =
                                                                                StackResetXySpring,
                                                                        ) { value, _ ->
                                                                            heldFingerDx = value
                                                                        }
                                                                    }
                                                                } else {
                                                                    heldFingerDx = 0f
                                                                }
                                                            }
                                                        }
                                                    } finally {
                                                        val finallyActions =
                                                            stackExpandedSettleFinallyActions(
                                                                settleGenAtStart = mySettleGen,
                                                                currentSettleGen = settleGen,
                                                                isDragging = isDragging,
                                                                dragYIsZero = dragY == 0f,
                                                            )
                                                        if (finallyActions.clearSettlingAndReporters) {
                                                            isSettling = false
                                                            stackDragSettlingReporter?.invoke(false)
                                                            if (finallyActions.reportDragYZeroIfZero) {
                                                                stackDragYReporter?.invoke(0f)
                                                            }
                                                        }
                                                        if (finallyActions.publishIdle) {
                                                            publishStackDragPhase(
                                                                StackExpandedDragPhase.Idle
                                                            )
                                                        }
                                                    }
                                                }
                                        }
                                    } else {
                                        null
                                    },
                                maxStackDragPx = if (isInteractiveTop) maxDragPx else 0f,
                                contentOffsetX = cardOffsetX,
                                contentOffsetY = {
                                    if (!isInteractiveTop) {
                                        0f
                                    } else {
                                        stackExpandedIncomingSurfaceTranslationYPx(
                                            phase =
                                                stackExpandedDragPhase(
                                                    isDragging = isDragging,
                                                    isSettling = isSettling,
                                                    leavingId = leavingKey,
                                                ),
                                            dragYPx = dragY,
                                            topSettleYPx = 0f,
                                        )
                                    }
                                },
                            )
                        CompositionLocalProvider(
                            LocalBlurAlpha provides
                                (LocalBlurAlpha.current * underBlurFactor).coerceIn(0f, 1f),
                            LocalStatusBarPaintedCardIsTop provides isInteractiveTop,
                            LocalStatusBarPaintedCardOwnerKey provides
                                statusBarStackEventOwnerKey(ownerId),
                        ) {
                            cardContent(item, bindings)
                        }
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
/** Mo ung dung tu stack panel roi fade-out 100ms. */
private fun openStackedEvent(
    context: Context,
    event: IslandEvent,
    interactor: IslandActions,
) {
    if (event is IslandEvent.AospChip) {
        when (val b = event.active.clickBehavior) {
            is OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification -> b.onClick()
            is OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification -> b.onClick()
            is OngoingActivityChipModel.ClickBehavior.None -> {}
            else -> {}
        }
        interactor.collapseIslandForAppLaunch(null)
        return
    }
    openIslandEventAndCollapse(context, event, interactor)
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
    // PrimaryCard onClick: chi gan cho event co app chu; ActionChip van thang hit-test.
    val primaryClick =
        if (canOpenIslandEventApp(event)) {
            { openStackedEvent(context, event, interactor) }
        } else {
            null
        }
    CompositionLocalProvider(LocalStackOwnsContentClick provides true) {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (event) {
                is IslandEvent.Media -> MediaCard(event, interactor)
                // Chronometer & Promoted ongoing: khung QS — khong PrimaryCard SpaceXxl.
                is IslandEvent.Timer -> ChronometerQsCard { TimerExpanded(event, interactor) }
                is IslandEvent.Stopwatch ->
                    ChronometerQsCard { StopwatchExpanded(event, interactor) }
                is IslandEvent.PromotedOngoing -> PromotedOngoingExpanded(event, interactor)
                else ->
                    // Khong AnimatedContent — doi panel bang stack swap; crossfade gay nhay.
                    PrimaryCard(onClick = primaryClick) {
                        ExpandedEventContent(event, interactor, hapticsViewModelFactory)
                    }
            }
        }
    }
}
