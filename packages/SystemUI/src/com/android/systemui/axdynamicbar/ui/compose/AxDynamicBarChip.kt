package com.android.systemui.axdynamicbar.ui.compose

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.animation.Expandable as SystemUiExpandable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.decideOwnerBoundGlassRemeasure
import com.android.systemui.axdynamicbar.shared.expandOnlyEventIdFor
import com.android.systemui.axdynamicbar.shared.ShapeXl
import com.android.systemui.axdynamicbar.shared.statusBarChipCenterUpdateAllowed
import com.android.systemui.axdynamicbar.shared.statusBarChipOuterOccupiedWidthPx
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackPhase5StaticRestoreTimeoutMs
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackDrawCredible
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPhase5MapWaitOutcome
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPhase5ShouldReleaseStatic
import com.android.systemui.axdynamicbar.shared.statusBarOwnerBoundGlassStoreAllowed
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.axdynamicbar.ui.AxDynamicBarStatusBarExpansion
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Cho 1 OnDraw tren View StatusBar — gan "frame da vao display list" hon withFrameNanos
 * (task-043/044/045 cross-window handback).
 *
 * @return false neu detach / VTO dead — KHONG coi la draw (CHECK#3). Residual: OnDraw
 * ≠ pixel da present tren panel; invalidate root co the ve truoc child pill compose.
 * Cleanup VTO: attached → view.post; detached → remove sync (post co the khong chay).
 * Detach mid-wait → resume false + clean listener (khong treo / phantom ready).
 */
private suspend fun awaitViewNextDraw(view: View): Boolean {
    if (!view.isAttachedToWindow) return false
    val vto = view.viewTreeObserver
    if (!vto.isAlive) return false
    return suspendCancellableCoroutine { cont ->
        var completed = false
        fun resumeOnce(value: Boolean) {
            if (completed) return
            completed = true
            if (cont.isActive) cont.resume(value)
        }
        lateinit var listener: ViewTreeObserver.OnDrawListener
        lateinit var attachListener: View.OnAttachStateChangeListener
        fun removeOnDrawNow() {
            val obs = view.viewTreeObserver
            if (obs.isAlive) {
                try {
                    obs.removeOnDrawListener(listener)
                } catch (_: IllegalStateException) {
                    // ignore
                }
            }
        }
        fun removeAttachNow() {
            try {
                view.removeOnAttachStateChangeListener(attachListener)
            } catch (_: IllegalStateException) {
                // ignore
            }
        }
        // Attached: post (an toan sau onDraw). Detached: sync — post co the khong bao gio chay.
        fun removeListenersReliable() {
            if (view.isAttachedToWindow) {
                view.post {
                    removeOnDrawNow()
                    removeAttachNow()
                }
            } else {
                removeOnDrawNow()
                removeAttachNow()
            }
        }
        listener =
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    view.post {
                        removeOnDrawNow()
                        removeAttachNow()
                        resumeOnce(true)
                    }
                }
            }
        attachListener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    removeListenersReliable()
                    resumeOnce(false)
                }
            }
        vto.addOnDrawListener(listener)
        view.addOnAttachStateChangeListener(attachListener)
        cont.invokeOnCancellation { removeListenersReliable() }
        view.invalidate()
    }
}

/**
 * Phase5 shared path: early StateFlow value HOAC collect — mot cho duy nhat.
 * Abort → release ngay. Hide confirm → 1 OnDraw ∥ ≤100ms roi release (gen-safe).
 */
private suspend fun awaitCollapseHandbackCompleteForGen(
    expansion: AxDynamicBarStatusBarExpansion,
    currentLayoutGen: Int,
): AxDynamicBarStatusBarExpansion.CollapseHandbackCompleteSignal {
    val early = expansion.collapseHandbackComplete.value
    if (early != null && (early.abort || early.layoutGeneration == currentLayoutGen)) {
        return early
    }
    return expansion.collapseHandbackComplete.first { sig ->
        sig != null && (sig.abort || sig.layoutGeneration == currentLayoutGen)
    }!!
}

/** Reflow width slot Ax chip — ngan, chi luc expand/collapse (khong idle infinite). */
private const val ChipSlotReflowMs = 280

private val ChipShape = ShapeXl

/**
 * Glass LayoutCoordinates gan branchKey+ownerId nguyen tu (task-038).
 * Cam remasure bang currentEntry.id khi cache van la outgoing A.
 */
private data class OwnerBoundGlassCoords(
    val branchKey: String,
    val ownerId: String,
    val coords: LayoutCoordinates,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxDynamicBarChip(
    viewModel: AxDynamicBarChipViewModel,
    modifier: Modifier = Modifier,
    ignoreKeyguard: Boolean = false,
) {
    val state by viewModel.chipState.collectAsStateWithLifecycle()
    val isOnKeyguard by viewModel.isOnKeyguard.collectAsStateWithLifecycle()
    val keyguardCarrier by viewModel.keyguardCarrierText.collectAsStateWithLifecycle()
    val chipStyle by viewModel.chipStyle.collectAsStateWithLifecycle()
    val pillSuppressed by
        viewModel.statusBarExpansion.pillSuppressed.collectAsStateWithLifecycle()
    val chipSlotReclaimed by
        viewModel.statusBarExpansion.chipSlotReclaimed.collectAsStateWithLifecycle()
    val isExpanded by
        viewModel.statusBarExpansion.isExpanded.collectAsStateWithLifecycle()
    val localView = LocalView.current
    val windowLocOnScreen = remember { IntArray(2) }
    // Glass owner-bound: coords + branchKey/ownerId nguyen tu — khong relabel A→B.
    var lastGlass by remember { mutableStateOf<OwnerBoundGlassCoords?>(null) }

    var toggleCount by remember { mutableIntStateOf(0) }

    val carrierName =
        if (isOnKeyguard && ignoreKeyguard) keyguardCarrier.takeIf { it.isNotBlank() } else null
    val configuration = LocalConfiguration.current
    val screenWidthPx =
        with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val configEpoch =
        Triple(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
        )

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val expandableController =
        rememberExpandableController(color = Color.Transparent, shape = ChipShape)
    var currentExpandable by remember { mutableStateOf<SystemUiExpandable?>(null) }

    val motionScheme = MaterialTheme.motionScheme
    // Chip co mat (event + khong keyguard) — tach khoi morph suppress.
    // CAM gan chipExists=false khi suppress (destroy composition → flicker).
    val chipExists = state != null && (ignoreKeyguard || !isOnKeyguard)
    // Morph handoff: alpha=0 GIU composition; slot reclaim RIENG sau overlay handoff
    // (width→0 → NIC sibling reflow). Khong GONE ca ComposeView (clock/date chung).
    // AnimatedVisibility chi theo chipExists (event lifecycle) — KHONG size-transform
    // theo suppress (tranh exit/enter destroy cay luc morph).

    // Width fraction: 1 idle; 0 khi reclaim sau handoff; restore o collapse() truoc dest.
    val slotWidthFraction by
        animateFloatAsState(
            targetValue = if (chipSlotReclaimed) 0f else 1f,
            animationSpec = tween(ChipSlotReflowMs, easing = FastOutSlowInEasing),
            label = "ax_chip_slot_reflow",
        )

    // Handback 044: giu staticMode tu suppress den SAU overlay hide (phase5) — khong flip
    // ngay OnDraw (FAIL#1: marquee/ticker/AnimatedContent @ first visible frame).
    // Cam arm keepStatic tu pillSuppressed=false startup — chi khi suppress that.
    var handbackKeepStatic by remember { mutableStateOf(false) }
    LaunchedEffect(pillSuppressed) {
        if (pillSuppressed) {
            handbackKeepStatic = true
        }
    }
    // Phase3: sau unsuppress — OnDraw credible + report ack; GIU static (khong release).
    LaunchedEffect(
        pillSuppressed,
        chipExists,
        handbackKeepStatic,
        state?.let { (it.notificationAlert ?: it.event).id },
    ) {
        if (pillSuppressed) return@LaunchedEffect
        // Startup / idle: khong handback session → khong emit ack gia.
        if (!handbackKeepStatic) return@LaunchedEffect
        val gen = viewModel.statusBarExpansion.layoutGeneration
        val ownerId = state?.let { (it.notificationAlert ?: it.event).id }
        if (!chipExists || ownerId == null) {
            // No-owner: bao overlay timeout/ack null; release static (khong pill).
            viewModel.statusBarExpansion.reportCollapseHandbackLiveDrawn(
                ownerId = null,
                layoutGeneration = gen,
            )
            handbackKeepStatic = false
            return@LaunchedEffect
        }
        val ownerBefore = ownerId
        val drew = awaitViewNextDraw(localView)
        val ownerAfter = state?.let { (it.notificationAlert ?: it.event).id }
        val credible =
            drew &&
                statusBarCollapseHandbackDrawCredible(
                    attachedToWindow = localView.isAttachedToWindow,
                    viewTreeObserverAlive = localView.viewTreeObserver.isAlive,
                    chipExists = chipExists,
                    ownerId = ownerAfter,
                ) &&
                ownerAfter == ownerBefore
        if (!credible) {
            // Khong phantom ready — Panel timeout failsafe; giu static den complete/abort.
            return@LaunchedEffect
        }
        viewModel.statusBarExpansion.reportCollapseHandbackLiveDrawn(
            ownerId = ownerAfter,
            layoutGeneration = gen,
        )
        // Phase5 release o effect complete — KHONG flip static o day.
    }

    // Phase5 (045): sau overlay hide confirm / abort — shared early+collect path.
    // 1 OnDraw credible ∥ bounded ≤100ms (CHI restore; khong conflate phase3 ACK 50ms).
    // Draw false / detach / timeout → van release static (tranh frozen ticker).
    // Stale gen → lap doi signal moi; abort / reverse → release ngay. Idle khong arm static.
    LaunchedEffect(handbackKeepStatic, pillSuppressed) {
        if (!handbackKeepStatic || pillSuppressed) return@LaunchedEffect
        val expansion = viewModel.statusBarExpansion
        while (handbackKeepStatic) {
            val gen = expansion.layoutGeneration
            val sig = awaitCollapseHandbackCompleteForGen(expansion, gen)
            if (sig.abort) {
                handbackKeepStatic = false
                return@LaunchedEffect
            }
            // Hide confirm: bounded wait — khong treo OnDraw vo han (FAIL 044→045).
            val boundedDrew =
                withTimeoutOrNull(StatusBarHandbackPhase5StaticRestoreTimeoutMs) {
                    awaitViewNextDraw(localView)
                }
            val outcome = statusBarCollapseHandbackPhase5MapWaitOutcome(boundedDrew)
            // Abort mid-wait (reverse/expand) — nha ngay, khong doi OnDraw.
            if (expansion.collapseHandbackComplete.value?.abort == true) {
                handbackKeepStatic = false
                return@LaunchedEffect
            }
            val currentGen = expansion.layoutGeneration
            if (
                statusBarCollapseHandbackPhase5ShouldReleaseStatic(
                    abort = false,
                    overlayHideConfirmed = true,
                    signalLayoutGen = sig.layoutGeneration,
                    currentLayoutGen = currentGen,
                    drawWaitOutcome = outcome,
                )
            ) {
                handbackKeepStatic = false
                return@LaunchedEffect
            }
            // Stale gen trong luc wait — khong lock; lap cho complete gen moi.
            if (currentGen != gen) continue
            // Anti-stuck belt (khong the dat neu helper dung) — luon release sau confirm.
            handbackKeepStatic = false
            return@LaunchedEffect
        }
    }

    // Unmount / mat pill: invalidate stale rect (khong dung lai sau khi pill bien mat).
    LaunchedEffect(chipExists) {
        if (!chipExists) {
            viewModel.statusBarExpansion.clearCapturedPillFrame()
            lastGlass = null
            viewModel.statusBarExpansion.reportCollapseHandbackLiveDrawn(
                ownerId = null,
                layoutGeneration = viewModel.statusBarExpansion.layoutGeneration,
            )
            handbackKeepStatic = false
        }
    }
    // Rotation / config: invalidate (bo qua lan compose dau).
    var prevConfigEpoch by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    LaunchedEffect(configEpoch) {
        val prev = prevConfigEpoch
        prevConfigEpoch = configEpoch
        if (prev != null && prev != configEpoch) {
            viewModel.statusBarExpansion.clearCapturedPillFrame()
            lastGlass = null
        }
    }

    AnimatedVisibility(
        visible = chipExists,
        enter =
            fadeIn(motionScheme.defaultEffectsSpec()) +
                scaleIn(initialScale = 0.8f, animationSpec = motionScheme.defaultSpatialSpec()),
        exit =
            fadeOut(motionScheme.fastEffectsSpec()) +
                scaleOut(targetScale = 0.8f, animationSpec = motionScheme.fastSpatialSpec()),
        modifier =
            modifier
                // Outer layout NGOAI padding/widthIn — fraction=0 report width=0 that
                // (min=25dp chi constraint CHILD; NIC sibling nhan cho trong WRAP chip).
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val w =
                        statusBarChipOuterOccupiedWidthPx(
                            naturalWidthPx = placeable.width,
                            slotWidthFraction = slotWidthFraction,
                        )
                    layout(w, placeable.height) {
                        if (w > 0) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                }
                .padding(start = 4.dp, end = 2.dp)
                .widthIn(min = 25.dp, max = 90.dp)
                .graphicsLayer { alpha = if (pillSuppressed) 0f else 1f }
                .then(
                    if (pillSuppressed) {
                        // An khoi a11y / hit — van giu cho composition + remasure.
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier.pointerInput(viewModel) {
                            awaitEachGesture {
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)

                                val startX = down.position.x
                                val startY = down.position.y
                                var dragging = false
                                var totalDx = 0f
                                var decided = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {

                                        if (dragging) {
                                            change.consume()
                                            if (totalDx > 0) viewModel.cyclePrev()
                                            else viewModel.cycleNext()
                                        } else if (!decided) {

                                            change.consume()
                                            if (viewModel.chipTapClosesPanel(down.uptimeMillis)) {
                                                viewModel.statusBarExpansion.collapse()
                                            } else {
                                                val current = state?.event
                                                if (current is IslandEvent.AospChip) {
                                                    val expandable = currentExpandable
                                                    if (
                                                        expandable == null ||
                                                            !viewModel.handleAospChipTap(
                                                                current,
                                                                expandable,
                                                            )
                                                    ) {
                                                        viewModel.statusBarExpansion.expand()
                                                    }
                                                } else {
                                                    // Timer/Stopwatch: chi mo dung 1 event.id.
                                                    viewModel.statusBarExpansion.expand(
                                                        current?.let { expandOnlyEventIdFor(it) },
                                                    )
                                                }
                                            }
                                        }

                                        break
                                    }
                                    val dx = change.position.x - startX
                                    val dy = change.position.y - startY
                                    if (
                                        !decided &&
                                            (abs(dx) > touchSlop || abs(dy) > touchSlop)
                                    ) {
                                        if (abs(dx) >= abs(dy)) {

                                            decided = true
                                            dragging = true
                                            totalDx = dx
                                            change.consume()
                                        } else {

                                            decided = true
                                            break
                                        }
                                    } else if (dragging) {
                                        totalDx = dx
                                        change.consume()
                                    }
                                }
                            }
                        }
                    },
                ),
        // Capture glass that (Row/Circle), KHONG dat o day — AV + fillMaxHeight = SB H.
    ) {
        val visibilityBlurAlpha by
            transition.animateFloat(
                transitionSpec = { motionScheme.fastEffectsSpec() },
                label = "chip_blur_visibility",
            ) {
                if (it == EnterExitState.Visible) 1f else 0f
            }
        // Chi capture khi pill VISIBLE — tranh enter/exit / overlay gia.
        // Khi suppressed van capture (slot chua reclaim / dang restore) de remasure dest.
        val pillCaptureEnabled =
            transition.currentState == EnterExitState.Visible

        // Screen px: boundsInWindow + root locationOnScreen (SB window != overlay).
        // capturePillFrame + centerX: gate khi reclaim / unplaced / width teo (025 origin).
        // Caller DA gate store/remasure owner-bound — khong nhan ownerId lech branch.
        fun captureVisibleGlass(
            coords: LayoutCoordinates,
            branchKey: String,
            ownerId: String,
            updateCache: Boolean,
        ) {
            if (updateCache) {
                lastGlass =
                    OwnerBoundGlassCoords(
                        branchKey = branchKey,
                        ownerId = ownerId,
                        coords = coords,
                    )
            }
            val bounds = coords.boundsInWindow()
            val w = bounds.width.roundToInt()
            val h = bounds.height.roundToInt()
            val centerAllowed =
                statusBarChipCenterUpdateAllowed(
                    pillSlotReclaimed = chipSlotReclaimed,
                    slotWidthFraction = slotWidthFraction,
                    reportedWidthPx = w,
                )
            if (centerAllowed && screenWidthPx > 0f) {
                val centerX = (bounds.left + bounds.right) / 2f
                viewModel.updateChipCenterX(centerX / screenWidthPx)
            }
            if (!pillCaptureEnabled || !centerAllowed) return
            localView.rootView.getLocationOnScreen(windowLocOnScreen)
            if (w > 0 && h > 0) {
                viewModel.statusBarExpansion.capturePillFrame(
                    left = (bounds.left + windowLocOnScreen[0]).roundToInt(),
                    top = (bounds.top + windowLocOnScreen[1]).roundToInt(),
                    width = w,
                    height = h,
                    ownerId = ownerId,
                )
            }
        }

        state?.let { chipState ->
            val displayEvent = chipState.notificationAlert ?: chipState.event
            val isAlert = chipState.notificationAlert != null
            val currentEntry = StatusBarChipEventEntry(displayEvent, isAlert)
            val suppressedLatest by rememberUpdatedState(pillSuppressed)
            val targetKeyLatest by rememberUpdatedState(currentEntry.key)

            // Sync remasure khi intent/suppress/slot/owner doi ma OGP im.
            // Chi khi cache.branchKey EXACT == target — khong fabricate B tu coords A.
            LaunchedEffect(
                isExpanded,
                pillSuppressed,
                chipSlotReclaimed,
                slotWidthFraction,
                pillCaptureEnabled,
                currentEntry.key,
            ) {
                val cached = lastGlass
                val decision =
                    decideOwnerBoundGlassRemeasure(
                        cachedBranchKey = cached?.branchKey,
                        cachedOwnerId = cached?.ownerId,
                        targetBranchKey = currentEntry.key,
                        coordsAttached = cached?.coords?.isAttached == true,
                        pillCaptureEnabled = pillCaptureEnabled,
                        slotWidthFraction = slotWidthFraction,
                    )
                if (decision.allow && cached != null && decision.ownerId != null) {
                    captureVisibleGlass(
                        coords = cached.coords,
                        branchKey = cached.branchKey,
                        ownerId = decision.ownerId,
                        updateCache = false,
                    )
                }
                // else: B chua place / stale A — defer, giu morph rect hop le cu.
            }

            Expandable(
                controller = expandableController,
                onClick = null,
                defaultMinSize = false,
            ) { expandable ->
                currentExpandable = expandable
                // targetState = ENTRY (event+isAlert); contentKey = id on dinh.
                // Branch render ENTRY do — KHONG outer displayEvent (bug overlap B/B).
                // Suppressed: None transition — snap B an, khong crossfade/remount @unsuppress.
                AnimatedContent(
                    targetState = currentEntry,
                    transitionSpec = {
                        val transform =
                            if (suppressedLatest) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (
                                    fadeIn(motionScheme.defaultEffectsSpec()) +
                                        scaleIn(
                                            initialScale = 0.92f,
                                            animationSpec = motionScheme.defaultSpatialSpec(),
                                        )
                                    ) togetherWith (
                                    fadeOut(motionScheme.fastEffectsSpec()) +
                                        scaleOut(
                                            targetScale = 0.92f,
                                            animationSpec = motionScheme.fastSpatialSpec(),
                                        )
                                    )
                            }
                        transform.using(sizeTransform = null)
                    },
                    contentKey = { it.key },
                    label = "chip_event",
                ) { entry ->
                    val contentBlurAlpha by
                        transition.animateFloat(
                            transitionSpec = { motionScheme.fastEffectsSpec() },
                            label = "chip_blur_content",
                        ) {
                            if (it == EnterExitState.Visible) 1f else 0f
                        }
                    // SSOT visual: StatusBarPillBody — dung ENTRY branch, khong outer.
                    val presentation =
                        statusBarChipEntryPresentation(
                            entry = entry,
                            chipStyle = chipStyle,
                            generation = viewModel.statusBarExpansion.layoutGeneration,
                            carrierName = carrierName,
                        )
                    StatusBarPillBody(
                        presentation = presentation,
                        includeBlur = true,
                        includeProgress = true,
                        // Suppressed + handback keep-static: khong remount media @unsuppress.
                        staticMode = suppressedLatest || handbackKeepStatic,
                        blurAlpha = visibilityBlurAlpha * contentBlurAlpha,
                        modifier =
                            Modifier
                                .squishAnimation(toggleCount)
                                .onGloballyPositioned { coords ->
                                    // Chi TARGET branch ghi cache + capture; outgoing khong overwrite.
                                    if (
                                        statusBarOwnerBoundGlassStoreAllowed(
                                            branchKey = entry.key,
                                            targetKey = targetKeyLatest,
                                        )
                                    ) {
                                        captureVisibleGlass(
                                            coords = coords,
                                            branchKey = entry.key,
                                            ownerId = entry.event.id,
                                            updateCache = true,
                                        )
                                    }
                                },
                    )
                }
            }
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
                            animationSpec =
                                keyframes {
                                    durationMillis = 400
                                    1.06f at 120 using FastOutSlowInEasing
                                    0.97f at 260
                                    1f at 400
                                },
                        )
                    }
                    launch {
                        scaleY.animateTo(
                            targetValue = 1f,
                            animationSpec =
                                keyframes {
                                    durationMillis = 400
                                    0.95f at 120 using FastOutSlowInEasing
                                    1.03f at 260
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
