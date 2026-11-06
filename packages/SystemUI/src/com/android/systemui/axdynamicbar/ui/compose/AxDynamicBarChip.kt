package com.android.systemui.axdynamicbar.ui.compose

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.unit.Constraints
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.animation.Expandable as SystemUiExpandable
import androidx.compose.ui.unit.dp
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.StatusBarAppPillMotionDiag
import com.android.systemui.axdynamicbar.shared.StatusBarChipNativeOccupiedBridge
import com.android.systemui.axdynamicbar.shared.StatusBarClockNudgeController
import com.android.systemui.axdynamicbar.shared.StatusBarClockNudgePixelVisibilityThreshold
import com.android.systemui.axdynamicbar.shared.StatusBarNeighborTrigger
import com.android.systemui.axdynamicbar.shared.StatusBarNicAnimationGate
import com.android.systemui.axdynamicbar.shared.animateStatusBarClockNudgeSprings
import com.android.systemui.axdynamicbar.shared.animateStatusBarPillAppShowAlpha
import com.android.systemui.axdynamicbar.shared.animateStatusBarPillAppShowScale
import com.android.systemui.axdynamicbar.shared.decideOwnerBoundGlassRemeasure
import com.android.systemui.axdynamicbar.shared.expandOnlyEventIdFor
import com.android.systemui.axdynamicbar.shared.findStatusBarClockView
import com.android.systemui.axdynamicbar.shared.findStatusBarNotificationIconContainer
import com.android.systemui.axdynamicbar.shared.ShapeXl
import com.android.systemui.axdynamicbar.shared.StatusBarAppPillTransitionMachine
import com.android.systemui.axdynamicbar.shared.snapStatusBarPillAppHide
import com.android.systemui.axdynamicbar.shared.snapStatusBarPillAppVisible
import com.android.systemui.axdynamicbar.shared.statusBarAppPillEventKindOf
import com.android.systemui.axdynamicbar.shared.statusBarAppPillFgPkgToken
import com.android.systemui.axdynamicbar.shared.statusBarAppPillStartSeedOf
import com.android.systemui.axdynamicbar.shared.statusBarChipCenterUpdateAllowed
import com.android.systemui.axdynamicbar.shared.statusBarChipOccupiedLayoutContract
import com.android.systemui.axdynamicbar.shared.statusBarChipOccupiedWidthForNativeParent
import com.android.systemui.axdynamicbar.shared.statusBarChipSlotDurationMs
import com.android.systemui.axdynamicbar.shared.statusBarChipSlotSegmentFractionAt
import com.android.systemui.axdynamicbar.shared.statusBarChipSlotSegmentTarget
import com.android.systemui.axdynamicbar.shared.statusBarChipSlotWidthFractionForLayout
import com.android.systemui.axdynamicbar.shared.statusBarClockNudgeAmplitudePx
import com.android.systemui.axdynamicbar.shared.statusBarClockNudgeEligible
import com.android.systemui.axdynamicbar.shared.statusBarClockShouldNudge
import com.android.systemui.axdynamicbar.shared.statusBarNeighborTriggerIsApp
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackHandoverFrameSteps
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackPhase5StaticRestoreTimeoutMs
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackPillLayerDrawAttemptMs
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackPillLayerReadyMaxAttempts
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackStagedTrueLayerAlpha
import com.android.systemui.axdynamicbar.shared.statusBarHandbackLivenessDiagOf
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackAckAfterVisibleLive
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackDrawCredible
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPhase5MapWaitOutcome
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPhase5ShouldReleaseStatic
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackForceRevealMatchesSession
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPillLayerDrawPostMayIncrementEpoch
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPillLayerReady
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackPillLayerReadyAfterRetries
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackStallRecoveryForceFullMayAck
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandbackStallRecoveryMayForceFull
import com.android.systemui.axdynamicbar.shared.statusBarMorphHandoffTrueLayerFromProgress
import com.android.systemui.axdynamicbar.shared.statusBarMorphHandoffNativeGlassFraction
import com.android.systemui.axdynamicbar.shared.statusBarMorphHandoffTruePillAlpha
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffPhase
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffNativeBlurPrepEnabled
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffOwnershipOf
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffClearPillCoordsOnDispose
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffCoordsDisposeReason
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffMeasuredLayoutValidForSession
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffRealPrepared
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffResolveLayoutAfterSessionChange
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffTruePillAlpha
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffNativeGlassFraction
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffMeasuredLayout
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseProbePhase
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseProbeSnapshot
import com.android.systemui.axdynamicbar.shared.StatusBarPillFrame
import com.android.systemui.axdynamicbar.shared.statusBarOwnerBoundGlassStoreAllowed
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.axdynamicbar.ui.AxDynamicBarStatusBarExpansion
import com.android.systemui.clock.ClockModernization
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

private val ChipShape = ShapeXl

/** locationInWindow [left, top] — NIC.left local co the =0 khi parent dich. */
private fun viewLocationInWindow(view: View?, out: IntArray): Pair<Int, Int> {
    if (view == null) return -1 to -1
    view.getLocationInWindow(out)
    return out[0] to out[1]
}

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
    val diagLocInWindow = remember { IntArray(2) }
    // Glass owner-bound: coords + branchKey/ownerId nguyen tu — khong relabel A→B.
    var lastGlass by remember { mutableStateOf<OwnerBoundGlassCoords?>(null) }
    val fgPkgTokenLatest by
        rememberUpdatedState(statusBarAppPillFgPkgToken(state?.foregroundPackage))
    val eventKindLatest by rememberUpdatedState(statusBarAppPillEventKindOf(state?.event))
    val suppressedCountLatest by
        rememberUpdatedState(state?.visuallySuppressedCount ?: 0)
    val ownerAppHiddenLatest by rememberUpdatedState(state?.ownerAppVisualHidden == true)

    var toggleCount by remember { mutableIntStateOf(0) }

    val carrierName =
        if (isOnKeyguard && ignoreKeyguard) keyguardCarrier.takeIf { it.isNotBlank() } else null
    val configuration = LocalConfiguration.current
    val screenWidthPx =
        with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val configEpoch = configuration.hashCode()

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val expandableController =
        rememberExpandableController(color = Color.Transparent, shape = ChipShape)
    var currentExpandable by remember { mutableStateOf<SystemUiExpandable?>(null) }

    val motionScheme = MaterialTheme.motionScheme
    // Chip co mat (event + khong keyguard) — tach khoi morph suppress.
    // CAM gan chipExists=false khi suppress (destroy composition → flicker).
    val chipExists = state != null && (ignoreKeyguard || !isOnKeyguard)
    // Morph handoff: alpha=0 GIU composition; slot reclaim khi pill suppress
    // (width→0 → NIC sibling reflow). Khong GONE ComposeView (clock/date).
    // AnimatedVisibility chi theo chipExists (event lifecycle) — KHONG size-transform
    // theo suppress (tranh exit/enter destroy cay luc morph).

    // Slot: Animatable linearT DOC LAP clock spring. Outer pill width (khong ComposeView).
    // Segment: start+(target-start)*easing — arbitrary start (overshoot/midflight).
    val slotLinearTAnim = remember { Animatable(1f) }
    var slotSegmentStart by remember { mutableFloatStateOf(1f) }
    var slotMotionReclaiming by remember { mutableStateOf(chipSlotReclaimed) }
    var slotNeighborTrigger by remember {
        mutableStateOf(StatusBarNeighborTrigger.CARD_CLOSE)
    }
    var slotSegmentNaturalPx by remember { mutableFloatStateOf(0f) }
    val nicAnimGate = remember { StatusBarNicAnimationGate() }
    val clockNudge = remember { StatusBarClockNudgeController() }
    // Pixel settle = DynamicAnimation.TRANSLATION_X (1f), khong default 0.01.
    val composeClockNudge =
        remember { Animatable(0f, visibilityThreshold = StatusBarClockNudgePixelVisibilityThreshold) }
    var composeNudgeEpoch by remember { mutableIntStateOf(0) }
    // Clock scope DOC LAP LaunchedEffect slot — khong wait children / cancel theo slot end.
    val clockMotionScope = rememberCoroutineScope()
    var composeNudgeJob by remember { mutableStateOf<Job?>(null) }
    var slotMotionArmed by remember { mutableStateOf(false) }
    var outerPillSlotWidthPx by remember { mutableIntStateOf(0) }
    // Ngan sach NATURAL unanimated tu parent widthIn(chipsMaxWidth) — Int.MAX_VALUE=unbounded.
    var chipNaturalBudgetMaxPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    // Commit machine: first-frame slot+visual coherent truoc suspend snap Animatable.
    val appPillTransition = remember { StatusBarAppPillTransitionMachine() }
    var renderedPillFrame by remember {
        mutableStateOf(appPillTransition.rendered)
    }
    // Native bridge: occupied → requestLayout host/parent; CAM set host width = pill.
    val occupiedBridge = remember { StatusBarChipNativeOccupiedBridge() }
    val appPillDiag = remember { StatusBarAppPillMotionDiag() }
    var lastMeasuredChipOccupiedPx by remember { mutableIntStateOf(-1) }
    val slotDensity = LocalDensity.current
    val expansion = viewModel.statusBarExpansion
    val neighborMotionReq by expansion.neighborMotionRequest.collectAsStateWithLifecycle()
    val slotDensityDpi = slotDensity.density * 160f
    val ownerAppHidden = ownerAppHiddenLatest
    // Pill app-show alpha/scale — chi APP_SHOW; khong Material 0.8 / handoff.
    val appPillAlpha = remember { Animatable(1f) }
    val appPillScale = remember { Animatable(1f) }
    var appVisualEpoch by remember { mutableIntStateOf(0) }
    var prevOwnerAppHidden by remember { mutableStateOf<Boolean?>(null) }
    val fallbackSlotW =
        with(slotDensity) { 48.dp.toPx() }
    // Natural on dinh (cache) — khong = occupied dang teo.
    val slotWPxForLayout =
        outerPillSlotWidthPx.toFloat().coerceAtLeast(fallbackSlotW)
    DisposableEffect(localView) {
        occupiedBridge.attach(localView)
        onDispose { occupiedBridge.detach() }
    }
    val frameForLayout = renderedPillFrame
    val slotWidthFraction =
        statusBarChipSlotWidthFractionForLayout(
            if (frameForLayout != null) {
                frameForLayout.slotFraction
            } else {
                statusBarChipSlotSegmentFractionAt(
                    start = slotSegmentStart,
                    target = statusBarChipSlotSegmentTarget(slotMotionReclaiming),
                    t = slotLinearTAnim.value,
                    trigger = slotNeighborTrigger,
                )
            },
            slotWPxForLayout,
            slotDensityDpi,
        )

    val probeSnapshot by expansion.collapseProbeCtrl.snapshot.collectAsStateWithLifecycle()
    val probePlacement =
        remember { arrayOfNulls<StatusBarCollapseProbeSnapshot>(1) }
    val probeNodePresent = remember { booleanArrayOf(false) }
    fun probeContextToken(): Long =
        (localView.resources.configuration.hashCode().toLong() shl 32) xor
            chipNaturalBudgetMaxPx.toLong()

    // Block only this window's draw while real native end-layout geometry is probed.
    // The listener survives phase changes; callbacks read the controller directly.
    DisposableEffect(localView) {
        val observer = localView.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            val controller = expansion.collapseProbeCtrl
            val request = controller.snapshot.value
            if (request.phase == StatusBarCollapseProbePhase.Idle) {
                true
            } else {
                val parent = localView.parent as? View
                val stable = !localView.isLayoutRequested &&
                    parent?.isLayoutRequested != true && !localView.rootView.isLayoutRequested
                if (!request.cancelled &&
                    (!expansion.isProbeDestinationLive() || chipNaturalBudgetMaxPx <= 0)
                ) {
                    controller.cancelForRestore(closeAfterRestore = request.closeAfterRestore)
                    localView.requestLayout()
                    parent?.requestLayout()
                    false
                } else if (request.phase == StatusBarCollapseProbePhase.Measuring) {
                    val placed = probePlacement[0]
                    val cached = lastGlass
                    val live = viewModel.chipState.value
                    val expectedKey = live?.let {
                        StatusBarChipEventEntry(
                            it.notificationAlert ?: it.event,
                            it.notificationAlert != null,
                        ).key
                    }
                    if (stable && placed != null && cached != null &&
                        placed.requestGeneration == request.requestGeneration &&
                        placed.phase == StatusBarCollapseProbePhase.Measuring &&
                        cached.ownerId == request.targetOwnerId &&
                        cached.branchKey == expectedKey && cached.coords.isAttached
                    ) {
                        val bounds = cached.coords.boundsInWindow()
                        localView.rootView.getLocationOnScreen(windowLocOnScreen)
                        val frame = StatusBarPillFrame(
                            left = (bounds.left + windowLocOnScreen[0]).roundToInt(),
                            top = (bounds.top + windowLocOnScreen[1]).roundToInt(),
                            width = bounds.width.roundToInt(),
                            height = bounds.height.roundToInt(),
                        )
                        // A clipped or unplaced branch cannot certify a full destination.
                        if (frame.width == cached.coords.size.width &&
                            frame.height == cached.coords.size.height &&
                            controller.recordMeasuredLayout(
                                request.requestGeneration, expansion.layoutGeneration,
                                cached.ownerId, frame, probeContextToken(),
                            )
                        ) {
                            localView.requestLayout()
                            parent?.requestLayout()
                        }
                    }
                    if (controller.phase == StatusBarCollapseProbePhase.Measuring &&
                        controller.measurementExpired()
                    ) {
                        controller.cancelForRestore(closeAfterRestore = true)
                        localView.requestLayout()
                        parent?.requestLayout()
                    }
                    false
                } else {
                    // A removed chip has no placement callback: the stable native tree
                    // itself certifies that the temporary occupied width is gone.
                    if (stable && !probeNodePresent[0]) {
                        controller.acknowledgePlacement(
                            request.requestGeneration, StatusBarCollapseProbePhase.Restoring,
                        )
                    }
                    stable && expansion.finishProbeCollapse(
                        request.requestGeneration, probeContextToken(),
                    )
                }
            }
        }
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!v.viewTreeObserver.isAlive) return
                v.viewTreeObserver.removeOnPreDrawListener(listener)
                v.viewTreeObserver.addOnPreDrawListener(listener)
            }
            override fun onViewDetachedFromWindow(v: View) {
                if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnPreDrawListener(listener)
                expansion.abandonDetachedProbe()
            }
        }
        if (observer.isAlive) observer.addOnPreDrawListener(listener)
        localView.addOnAttachStateChangeListener(attachListener)
        onDispose {
            localView.removeOnAttachStateChangeListener(attachListener)
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            val current = localView.viewTreeObserver
            if (current.isAlive) current.removeOnPreDrawListener(listener)
            expansion.abandonDetachedProbe()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            composeNudgeJob?.cancel()
            composeNudgeJob = null
            nicAnimGate.restore()
            clockNudge.cancelAndReset()
            expansion.settleClockNudge()
            expansion.settleSlotMotion(reclaimed = false)
        }
    }

    // Dong bo FG owner → expansion slot APP (khong morph suppress).
    LaunchedEffect(ownerAppHidden) {
        expansion.setOwnerAppFgHidden(ownerAppHidden)
    }

    // Morph / seed visual ONLY — APP α/scale di cung neighborMotionReq generation (task-010).
    var wasPillSuppressed by remember { mutableStateOf(false) }
    LaunchedEffect(pillSuppressed, ownerAppHidden) {
        if (pillSuppressed) {
            // Morph owns visibility — snap visible layers (handoff dummy/real).
            // Clear commit frame: Animatable la SSOT trong morph (khong stale α).
            appPillTransition.clear()
            renderedPillFrame = null
            snapStatusBarPillAppVisible(appPillAlpha, appPillScale)
            wasPillSuppressed = true
            prevOwnerAppHidden = ownerAppHidden
            return@LaunchedEffect
        }
        if (prevOwnerAppHidden == null) {
            // Seed lan dau — khong APP anim.
            appPillTransition.clear()
            renderedPillFrame = null
            if (ownerAppHidden) {
                snapStatusBarPillAppHide(appPillAlpha, appPillScale)
            } else {
                snapStatusBarPillAppVisible(appPillAlpha, appPillScale)
            }
            prevOwnerAppHidden = ownerAppHidden
            wasPillSuppressed = false
            return@LaunchedEffect
        }
        if (wasPillSuppressed) {
            wasPillSuppressed = false
            // Sau morph: FG van hide → snap hide; else visible (CARD path owns slot).
            appPillTransition.clear()
            renderedPillFrame = null
            if (ownerAppHidden) {
                snapStatusBarPillAppHide(appPillAlpha, appPillScale)
            } else {
                snapStatusBarPillAppVisible(appPillAlpha, appPillScale)
            }
        }
        prevOwnerAppHidden = ownerAppHidden
    }

    // Arm slot idle lan dau (chua co request) — khong anim.
    LaunchedEffect(Unit) {
        if (slotMotionArmed) return@LaunchedEffect
        slotMotionArmed = true
        slotMotionReclaiming = chipSlotReclaimed
        slotNeighborTrigger = StatusBarNeighborTrigger.CARD_CLOSE
        // Idle: start da o dich (reclaim→0 / show→1), t=1.
        slotSegmentStart = if (chipSlotReclaimed) 0f else 1f
        slotSegmentNaturalPx = slotWPxForLayout
        slotLinearTAnim.snapTo(1f)
        expansion.settleSlotMotion(reclaimed = chipSlotReclaimed)
    }

    // Slot reflow + NIC: consume explicit request (P1A). Clock DOC LAP (P1C).
    // Segment continuity (task-009): start=rendered, t 0→1, khong inverse curve.
    // task-010: APP_SHOW/HIDE — commit α/scale + slot CUNG generation (atomic start).
    LaunchedEffect(neighborMotionReq?.sequence) {
        val req = neighborMotionReq ?: return@LaunchedEffect
        if (!slotMotionArmed) {
            slotMotionArmed = true
            slotMotionReclaiming = req.reclaiming
            slotNeighborTrigger = req.trigger
            slotSegmentStart =
                if (req.reclaiming) 0f else 1f
            slotSegmentNaturalPx = slotWPxForLayout
            slotLinearTAnim.snapTo(1f)
            expansion.settleSlotMotion(reclaimed = req.reclaiming)
            return@LaunchedEffect
        }
        val reclaiming = req.reclaiming
        val trigger = req.trigger
        val target = statusBarChipSlotSegmentTarget(reclaiming)
        // sequence = gen: cancel cu khong settle/restore de dam len run moi.
        val runSeq = req.sequence
        val classicClock =
            if (!ClockModernization.isEnabled) findStatusBarClockView(localView) else null
        val amp =
            when {
                classicClock != null && statusBarClockShouldNudge(classicClock) ->
                    statusBarClockNudgeAmplitudePx(
                        classicClock.resources.displayMetrics,
                        classicClock.layoutDirection,
                    )
                ClockModernization.isEnabled -> {
                    val dm = localView.resources.displayMetrics
                    statusBarClockNudgeAmplitudePx(dm, localView.layoutDirection)
                }
                else -> 0f
            }
        // Start seed SSOT: uu tien renderedPillFrame (user thay); fallback backing
        // khi frame absent (initial/morph clear). Tranh seed tu Animatable stale
        // trong suspend gap sau commit (task-012).
        val startSeed =
            statusBarAppPillStartSeedOf(
                renderedFrame = renderedPillFrame,
                backingSlotStart = slotSegmentStart,
                backingReclaiming = slotMotionReclaiming,
                backingLinearT = slotLinearTAnim.value,
                backingTrigger = slotNeighborTrigger,
                backingAlpha = appPillAlpha.value,
                backingScale = appPillScale.value,
                backingNaturalPx = slotSegmentNaturalPx,
                nextNaturalPx = slotWPxForLayout,
                nextTrigger = trigger,
                generation = runSeq,
            )
        val startFraction = startSeed.slotStartFraction
        val appPlan = startSeed.visualPlan
        val startAlpha = startSeed.startAlpha
        val startScale = startSeed.startScale
        var appScaleJob: Job? = null
        try {
            nicAnimGate.suspendFrom(localView)
            clockNudge.cancelAndReset()
            composeNudgeJob?.cancel()
            composeNudgeJob = null
            composeNudgeEpoch++
            val nudgeEpoch = composeNudgeEpoch
            expansion.settleClockNudge()
            // Commit immutable first-frame TRUOC moi snapTo suspend (Animatable mutex).
            val committed =
                appPillTransition.commitStart(
                    generation = runSeq,
                    trigger = trigger,
                    slotStartFraction = startFraction,
                    reclaiming = reclaiming,
                    alpha = startAlpha,
                    scale = startScale,
                    naturalPx = slotWPxForLayout,
                )
            renderedPillFrame = committed
            slotSegmentStart = committed.slotStartFraction
            slotMotionReclaiming = committed.reclaiming
            slotNeighborTrigger = committed.trigger
            slotSegmentNaturalPx = committed.naturalPx
            appVisualEpoch = committed.generation.toInt()
            val occupied0 =
                statusBarChipOccupiedWidthForNativeParent(
                    naturalWidthPx = slotWPxForLayout.toInt(),
                    slotWidthFraction =
                        statusBarChipSlotWidthFractionForLayout(
                            committed.slotFraction,
                            slotWPxForLayout,
                            slotDensityDpi,
                        ),
                    densityDpi = slotDensityDpi,
                )
            expansion.publishSlotMotion(
                0f,
                reclaiming,
                slotWidthPx = slotWPxForLayout,
                densityDpi = slotDensityDpi,
                trigger = trigger,
            )
            occupiedBridge.publishDesiredOccupiedPx(occupied0)
            if (statusBarNeighborTriggerIsApp(trigger)) {
                // fg = AppTracking that; eventKind tach (khong nham chronometer=fgPkg).
                appPillDiag.logStart(
                    generation = runSeq,
                    trigger = trigger,
                    ownerHidden = ownerAppHidden,
                    reclaiming = reclaiming,
                    slotStartFraction = committed.slotStartFraction,
                    naturalPx = slotWPxForLayout.toInt(),
                    occupiedDesiredPx = occupied0,
                    alpha = committed.alpha,
                    scale = committed.scale,
                    fgToken = fgPkgTokenLatest,
                    fgSource = "AppTracking.foregroundPackage",
                    eventKind = eventKindLatest,
                    displayId = localView.display?.displayId ?: -1,
                    suppressedCount = suppressedCountLatest,
                    naturalBudgetMaxPx = chipNaturalBudgetMaxPx,
                )
            }
            // Sync Animatable SAU commit — gap suspend van doc renderedPillFrame coherent.
            if (appPlan != null) {
                if (appPlan.trigger == StatusBarNeighborTrigger.APP_HIDE) {
                    snapStatusBarPillAppHide(appPillAlpha, appPillScale)
                } else {
                    // APP_SHOW: start tu plan (0 sau hide; partial khi reversal).
                    appPillAlpha.snapTo(appPlan.startAlpha)
                    appPillScale.snapTo(appPlan.startScale)
                }
            }
            slotLinearTAnim.snapTo(0f)
            // APP_SHOW α/scale — cung generation; duration rieng; cancel theo seq.
            if (appPlan?.trigger == StatusBarNeighborTrigger.APP_SHOW) {
                val planGen = appPlan.generation
                appScaleJob =
                    launch {
                        try {
                            animateStatusBarPillAppShowScale(
                                appPillScale,
                                startScale = appPlan.startScale,
                            )
                        } catch (_: CancellationException) {
                            // Alpha completion / reentry cancel.
                        }
                    }
                launch {
                    try {
                        animateStatusBarPillAppShowAlpha(
                            appPillAlpha,
                            startAlpha = appPlan.startAlpha,
                        )
                        if (expansion.neighborMotionRequest.value?.sequence == planGen) {
                            appScaleJob?.cancel()
                            appPillScale.snapTo(1f)
                            appPillAlpha.snapTo(1f)
                            appPillTransition
                                .advance(planGen, alpha = 1f, scale = 1f)
                                ?.let { renderedPillFrame = it }
                        }
                    } catch (e: CancellationException) {
                        appScaleJob?.cancel()
                        // Khong snap1 — hide/partial moi co the dang chay.
                        throw e
                    }
                }
            }
            // Clock: rememberCoroutineScope — KHONG child cua slot effect (P1C).
            // settleSlot/NIC restore khi SLOT xong; clock own completion.
            if (statusBarClockNudgeEligible(trigger) && amp != 0f) {
                if (classicClock != null) {
                    clockNudge.start(classicClock, amp)
                } else if (ClockModernization.isEnabled) {
                    composeNudgeJob?.cancel()
                    composeNudgeJob =
                        clockMotionScope.launch {
                            try {
                                composeClockNudge.animateStatusBarClockNudgeSprings(
                                    amplitudePx = amp,
                                    nudgeEpoch = nudgeEpoch,
                                    currentEpoch = { composeNudgeEpoch },
                                    onFrame = { tx ->
                                        expansion.publishClockNudgeTranslationX(tx)
                                    },
                                )
                            } finally {
                                if (composeNudgeEpoch == nudgeEpoch) {
                                    expansion.settleClockNudge()
                                }
                            }
                        }
                }
            }
            // Slot only — khong doi clock. Duration 500ms; easing tren segment.
            slotLinearTAnim.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = statusBarChipSlotDurationMs(trigger),
                        easing = LinearEasing,
                    ),
            ) {
                appPillTransition
                    .advance(
                        generation = runSeq,
                        linearT = value,
                        alpha = appPillAlpha.value,
                        scale = appPillScale.value,
                    )
                    ?.let { renderedPillFrame = it }
                val frac =
                    statusBarChipSlotSegmentFractionAt(
                        start = startFraction,
                        target = target,
                        t = value,
                        trigger = trigger,
                    )
                val occupied =
                    statusBarChipOccupiedWidthForNativeParent(
                        naturalWidthPx = slotWPxForLayout.toInt(),
                        slotWidthFraction =
                            statusBarChipSlotWidthFractionForLayout(
                                frac,
                                slotWPxForLayout,
                                slotDensityDpi,
                            ),
                        densityDpi = slotDensityDpi,
                    )
                expansion.publishSlotMotion(
                    value,
                    reclaiming,
                    slotWidthPx = slotWPxForLayout,
                    densityDpi = slotDensityDpi,
                    trigger = trigger,
                )
                occupiedBridge.publishDesiredOccupiedPx(occupied)
                if (statusBarNeighborTriggerIsApp(trigger)) {
                    val nic = findStatusBarNotificationIconContainer(localView)
                    val (hostWinL, hostWinT) = viewLocationInWindow(localView, diagLocInWindow)
                    val (nicWinL, nicWinT) = viewLocationInWindow(nic, diagLocInWindow)
                    val chipMeasured = lastMeasuredChipOccupiedPx
                    appPillDiag.logSample(
                        generation = runSeq,
                        trigger = trigger,
                        ownerHidden = ownerAppHidden,
                        linearT = value,
                        slotFraction = frac,
                        naturalPx = slotWPxForLayout.toInt(),
                        occupiedDesiredPx = occupied,
                        occupiedMeasuredChipPx = chipMeasured,
                        alpha = appPillAlpha.value,
                        scale = appPillScale.value,
                        hostLeft = localView.left,
                        hostWidth = localView.width,
                        nicLeft = nic?.left ?: -1,
                        nicWidth = nic?.width ?: -1,
                        fgToken = fgPkgTokenLatest,
                        eventKind = eventKindLatest,
                        hostWinL = hostWinL,
                        hostWinT = hostWinT,
                        nicWinL = nicWinL,
                        nicWinT = nicWinT,
                        nicParentW = (nic?.parent as? View)?.width ?: -1,
                        layoutPhase =
                            if (chipMeasured < 0) "preLayout" else "postLayout",
                    )
                }
            }
            if (expansion.neighborMotionRequest.value?.sequence == runSeq) {
                // Settle: snap start=target de t=1 on dinh (tranh stale start).
                slotSegmentStart = target
                slotLinearTAnim.snapTo(1f)
                appPillTransition.settleSlot(runSeq)?.let { renderedPillFrame = it }
                expansion.settleSlotMotion(reclaimed = reclaiming)
                val occupiedSettle =
                    statusBarChipOccupiedWidthForNativeParent(
                        naturalWidthPx = slotWPxForLayout.toInt(),
                        slotWidthFraction =
                            statusBarChipSlotWidthFractionForLayout(
                                if (reclaiming) 0f else 1f,
                                slotWPxForLayout,
                                slotDensityDpi,
                            ),
                        densityDpi = slotDensityDpi,
                    )
                occupiedBridge.publishDesiredOccupiedPx(occupiedSettle)
                if (statusBarNeighborTriggerIsApp(trigger)) {
                    val nic = findStatusBarNotificationIconContainer(localView)
                    val (hostWinL, _) = viewLocationInWindow(localView, diagLocInWindow)
                    val (nicWinL, _) = viewLocationInWindow(nic, diagLocInWindow)
                    appPillDiag.logEnd(
                        generation = runSeq,
                        trigger = trigger,
                        occupiedDesiredPx = occupiedSettle,
                        occupiedMeasuredHostPx = localView.width,
                        nicLeft = nic?.left ?: -1,
                        nicWidth = nic?.width ?: -1,
                        nicWinL = nicWinL,
                        hostWinL = hostWinL,
                    )
                }
            }
        } catch (e: CancellationException) {
            appScaleJob?.cancel()
            if (statusBarNeighborTriggerIsApp(trigger)) {
                appPillDiag.logCancel(runSeq, trigger)
            }
            if (expansion.neighborMotionRequest.value?.sequence == runSeq) {
                slotMotionReclaiming = reclaiming
                expansion.settleSlotMotion(reclaimed = reclaiming)
            }
            throw e
        } finally {
            // Chi restore NIC neu van la run hien tai — cu khong reset moi.
            if (expansion.neighborMotionRequest.value?.sequence == runSeq) {
                nicAnimGate.restore()
            }
        }
    }

    // Handoff lifecycle: doc snapshot authoritative — KHONG multi-writer fraction/reveal.
    // freezeMotion trong Prepare/Collapse/Ready; liveValue true (real) sau unsuppress.
    val handoffSnap by
        viewModel.statusBarExpansion.collapseHandoffSnapshot.collectAsStateWithLifecycle()
    val handoffOwn =
        statusBarCollapseHandoffOwnershipOf(
            phase = handoffSnap.phase,
            pillSuppressed = pillSuppressed,
        )
    var pillLayerLayoutW by remember { mutableIntStateOf(0) }
    var pillLayerLayoutH by remember { mutableIntStateOf(0) }
    // LayoutCoordinates + owner gan placement — revalidate session khong can remeasure mu.
    var pillLayerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pillLayerCoordsOwnerId by remember { mutableStateOf<String?>(null) }
    // Attach la effect key — detach revoke prepared (khong chi snapshot isAttached).
    var pillAttached by remember { mutableStateOf(localView.isAttachedToWindow) }
    val handbackGeometryOwnerId = state?.let { (it.notificationAlert ?: it.event).id }
    // Live session/layout cho detach sync revoke (P1B) — khong capture stale trong listener.
    val handoffSessionGenLatest by rememberUpdatedState(handoffSnap.sessionGeneration)
    val handoffLayoutGenLatest by rememberUpdatedState(handoffSnap.layoutGeneration)
    DisposableEffect(localView) {
        val listener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    pillAttached = true
                }

                override fun onViewDetachedFromWindow(v: View) {
                    pillAttached = false
                    // Host detach: clear coords + revoke record NGAY (P1A/P1B).
                    if (
                        statusBarCollapseHandoffClearPillCoordsOnDispose(
                            StatusBarCollapseHandoffCoordsDisposeReason.HostDetach
                        )
                    ) {
                        pillLayerCoords = null
                        pillLayerCoordsOwnerId = null
                        pillLayerLayoutW = 0
                        pillLayerLayoutH = 0
                    }
                    viewModel.statusBarExpansion.reportCollapseHandoffRealPrepared(
                        sessionGeneration = handoffSessionGenLatest,
                        layoutGeneration = handoffLayoutGenLatest,
                        ownerId = null,
                        prepared = false,
                        layoutWidthPx = 0,
                        layoutHeightPx = 0,
                        attached = false,
                    )
                }
            }
        localView.addOnAttachStateChangeListener(listener)
        pillAttached = localView.isAttachedToWindow
        onDispose { localView.removeOnAttachStateChangeListener(listener) }
    }
    // Owner/session doi: doc lai tu coords neu van attach+dung owner; else size0 (B3).
    // KHONG chi zero roi doi OGP — session-only/alpha0 co the khong place lai.
    LaunchedEffect(handbackGeometryOwnerId, handoffSnap.sessionGeneration, pillAttached) {
        val coords = pillLayerCoords
        val resolved =
            statusBarCollapseHandoffResolveLayoutAfterSessionChange(
                coordsAttached = pillAttached && coords?.isAttached == true,
                coordsOwnerId = pillLayerCoordsOwnerId,
                coordsWidthPx = coords?.size?.width ?: 0,
                coordsHeightPx = coords?.size?.height ?: 0,
                expectedOwnerId = handbackGeometryOwnerId,
                sessionGeneration = handoffSnap.sessionGeneration,
            )
        pillLayerLayoutW = resolved.widthPx
        pillLayerLayoutH = resolved.heightPx
    }
    // Session-only rollover: CHI revoke readiness session cu — KHONG clear coords (P1A).
    // Coords clear nam o selected AnimatedContent branch dispose / host detach.
    DisposableEffect(handbackGeometryOwnerId, handoffSnap.sessionGeneration) {
        val disposedSessionGen = handoffSnap.sessionGeneration
        val disposedLayoutGen = handoffSnap.layoutGeneration
        onDispose {
            // P1A: SessionOnlyRollover → KHONG clear pillLayerCoords (clearCoords=false).
            // Chi revoke readiness session cu; LaunchedEffect resolve lai tu coords.
            viewModel.statusBarExpansion.reportCollapseHandoffRealPrepared(
                sessionGeneration = disposedSessionGen,
                layoutGeneration = disposedLayoutGen,
                ownerId = null,
                prepared = false,
                layoutWidthPx = 0,
                layoutHeightPx = 0,
                attached = false,
            )
        }
    }
    // Report prepared/revoke tu collapse START (ke ca pillSuppressed) — khong OnDraw=PRESENT.
    // layoutGeneration = expansion ACTUAL luc report (khong echo handoffSnap.layoutGeneration).
    LaunchedEffect(
        handoffSnap.sessionGeneration,
        handoffSnap.phase,
        handoffSnap.expectedOwnerId,
        pillAttached,
        chipExists,
        pillLayerLayoutW,
        pillLayerLayoutH,
        handbackGeometryOwnerId,
    ) {
        when (handoffSnap.phase) {
            StatusBarCollapseHandoffPhase.Prepare,
            StatusBarCollapseHandoffPhase.Collapse,
            StatusBarCollapseHandoffPhase.Ready -> Unit
            else -> return@LaunchedEffect
        }
        val actualLayoutGen = viewModel.statusBarExpansion.layoutGeneration
        val measured =
            StatusBarCollapseHandoffMeasuredLayout(
                ownerId = handbackGeometryOwnerId,
                sessionGeneration = handoffSnap.sessionGeneration,
                widthPx = pillLayerLayoutW,
                heightPx = pillLayerLayoutH,
                attached = pillAttached,
            )
        val measuredOk =
            statusBarCollapseHandoffMeasuredLayoutValidForSession(
                measured = measured,
                expectedOwnerId = handoffSnap.expectedOwnerId,
                sessionGeneration = handoffSnap.sessionGeneration,
            )
        // Null owner / chip mat / size0 / detach / stale measured: revoke.
        val prepared =
            measuredOk &&
                statusBarCollapseHandoffRealPrepared(
                    attachedToWindow = pillAttached,
                    layoutWidthPx = pillLayerLayoutW,
                    layoutHeightPx = pillLayerLayoutH,
                    chipExists = chipExists,
                    ownerId = handbackGeometryOwnerId,
                    expectedOwnerId = handoffSnap.expectedOwnerId,
                )
        viewModel.statusBarExpansion.reportCollapseHandoffRealPrepared(
            sessionGeneration = handoffSnap.sessionGeneration,
            layoutGeneration = actualLayoutGen,
            ownerId = handbackGeometryOwnerId,
            prepared = prepared,
            layoutWidthPx = pillLayerLayoutW,
            layoutHeightPx = pillLayerLayoutH,
            attached = pillAttached,
        )
    }
    // Diag nhe khi doi phase.
    LaunchedEffect(handoffSnap.phase, pillSuppressed, handbackGeometryOwnerId) {
        if (!android.util.Log.isLoggable("AxDbHandback", android.util.Log.DEBUG)) {
            return@LaunchedEffect
        }
        android.util.Log.d(
            "AxDbHandback",
            "handoff phase=${handoffSnap.phase} sess=${handoffSnap.sessionGeneration} " +
                "sup=$pillSuppressed owner=$handbackGeometryOwnerId " +
                "prep=${handoffSnap.realPrepared} xfer=${handoffSnap.transferred} " +
                "dummyFg=${handoffOwn.dummyFgAlpha} realFg=${handoffOwn.realFgAlpha} " +
                "glass=${handoffOwn.glassOwner}",
        )
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
        }
    }
    // Rotation / config: invalidate (bo qua lan compose dau).
    var prevConfigEpoch by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(configEpoch) {
        val prev = prevConfigEpoch
        prevConfigEpoch = configEpoch
        if (prev != null && prev != configEpoch) {
            viewModel.statusBarExpansion.clearCapturedPillFrame()
            lastGlass = null
        }
    }

    // Event lifecycle Material enter/exit — KHONG layer len APP FG Samsung track.
    // ownerAppHidden: composition van visible; α/scale/slot tu appPill* (khong Material 0.8).
    AnimatedVisibility(
        visible = chipExists,
        enter =
            if (ownerAppHidden) {
                EnterTransition.None
            } else {
                fadeIn(motionScheme.defaultEffectsSpec()) +
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = motionScheme.defaultSpatialSpec(),
                    )
            },
        exit =
            fadeOut(motionScheme.fastEffectsSpec()) +
                scaleOut(targetScale = 0.8f, animationSpec = motionScheme.fastSpatialSpec()),
        modifier =
            modifier
                // Outer layout NGOAI padding/widthIn — fraction=0 report width=0 that
                // (min=25dp chi constraint CHILD; NIC sibling nhan cho trong WRAP chip).
                // task-016: natural trong ngan sach caller (chipsMaxWidth) — KHONG Infinity
                // bypass; KHONG max=occupied animate (cam feedback).
                .layout { measurable, constraints ->
                    val budgetMax =
                        if (constraints.hasBoundedWidth) {
                            constraints.maxWidth.coerceAtLeast(0)
                        } else {
                            Int.MAX_VALUE
                        }
                    chipNaturalBudgetMaxPx = budgetMax
                    val measureMax =
                        if (budgetMax == Int.MAX_VALUE) {
                            Constraints.Infinity
                        } else {
                            budgetMax
                        }
                    val placeable =
                        measurable.measure(
                            constraints.copy(minWidth = 0, maxWidth = measureMax),
                        )
                    val layoutProbe = probeSnapshot
                    val isProbeMeasuring =
                        layoutProbe.phase == StatusBarCollapseProbePhase.Measuring
                    val effectiveSlotFraction =
                        if (isProbeMeasuring) 1f else slotWidthFraction
                    val contract =
                        statusBarChipOccupiedLayoutContract(
                            cachedNaturalPx = outerPillSlotWidthPx,
                            measuredUnconstrainedPx = placeable.width,
                            slotWidthFraction = effectiveSlotFraction,
                            densityDpi = density * 160f,
                            naturalBudgetMaxPx = budgetMax,
                        )
                    // Budget 0: clamp natural/cache ve 0 (an slot hop le).
                    outerPillSlotWidthPx = contract.stableNaturalPx
                    val w = contract.desiredOccupiedPx
                    // Bridge: desired occupied → native remeasure (khong set host=pill).
                    occupiedBridge.publishDesiredOccupiedPx(w)
                    layout(w, placeable.height) {
                        if (w > 0) placeable.placeRelative(0, 0)
                        if (layoutProbe.phase != StatusBarCollapseProbePhase.Idle) {
                            probePlacement[0] = layoutProbe
                            expansion.collapseProbeCtrl.acknowledgePlacement(
                                layoutProbe.requestGeneration, layoutProbe.phase,
                            )
                        }
                    }
                }
                .onGloballyPositioned { coords ->
                    // occChip = outer occupied boundary (modifier nay ngoai widthIn/graphicsLayer).
                    val measuredW = coords.size.width
                    lastMeasuredChipOccupiedPx = measuredW
                    occupiedBridge.noteHostMeasuredWidth(localView.width)
                    if (appPillDiag.enabled() && statusBarNeighborTriggerIsApp(slotNeighborTrigger)) {
                        val frame = renderedPillFrame
                        val nic = findStatusBarNotificationIconContainer(localView)
                        val (hostWinL, hostWinT) =
                            viewLocationInWindow(localView, diagLocInWindow)
                        val (nicWinL, nicWinT) = viewLocationInWindow(nic, diagLocInWindow)
                        appPillDiag.logSample(
                            generation = frame?.generation ?: -1L,
                            trigger = slotNeighborTrigger,
                            ownerHidden = ownerAppHidden,
                            linearT = frame?.linearT ?: slotLinearTAnim.value,
                            slotFraction = slotWidthFraction,
                            naturalPx = outerPillSlotWidthPx,
                            occupiedDesiredPx = occupiedBridge.desiredOccupiedPx,
                            occupiedMeasuredChipPx = measuredW,
                            alpha = frame?.alpha ?: appPillAlpha.value,
                            scale = frame?.scale ?: appPillScale.value,
                            hostLeft = localView.left,
                            hostWidth = localView.width,
                            nicLeft = nic?.left ?: -1,
                            nicWidth = nic?.width ?: -1,
                            fgToken = fgPkgTokenLatest,
                            eventKind = eventKindLatest,
                            hostWinL = hostWinL,
                            hostWinT = hostWinT,
                            nicWinL = nicWinL,
                            nicWinT = nicWinT,
                            nicParentW = (nic?.parent as? View)?.width ?: -1,
                            layoutPhase = "postLayout",
                        )
                    }
                }
                .padding(start = 4.dp, end = 2.dp)
                .widthIn(min = 25.dp, max = 90.dp)
                // Handoff FG α × app-show α/scale — CUNG layer (glass+content).
                // clip+Center: GIA THUYET early paint (chua trace prove) — khong claim fixed.
                // KHONG translationY thuan — Samsung centerY on dinh (~50), chua do Y rieng.
                .graphicsLayer {
                    val handoffA = statusBarCollapseHandoffTruePillAlpha(handoffOwn)
                    val frame = renderedPillFrame
                    val pillA = frame?.alpha ?: appPillAlpha.value
                    val pillS = frame?.scale ?: appPillScale.value
                    alpha = handoffA * pillA
                    scaleX = pillS
                    scaleY = pillS
                    transformOrigin = TransformOrigin.Center
                    clip = true
                }
                .then(
                    if (
                        pillSuppressed ||
                            handoffOwn.realFgAlpha <= 0.01f ||
                            ownerAppHidden ||
                            appPillAlpha.value <= 0.01f
                    ) {
                        // An khoi a11y / hit — van giu composition + remasure.
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
        DisposableEffect(Unit) {
            probeNodePresent[0] = true
            onDispose { probeNodePresent[0] = false }
        }
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
            val currentEntryLatest by rememberUpdatedState(currentEntry)
            val targetKeyLatest by rememberUpdatedState(currentEntry.key)

            // FAIL A3: owner doi → revalidate tu coords (khong blind zero mat placement hien co).
            LaunchedEffect(currentEntry.key, handoffSnap.sessionGeneration) {
                val coords = pillLayerCoords
                val resolved =
                    statusBarCollapseHandoffResolveLayoutAfterSessionChange(
                        coordsAttached = pillAttached && coords?.isAttached == true,
                        coordsOwnerId = pillLayerCoordsOwnerId,
                        coordsWidthPx = coords?.size?.width ?: 0,
                        coordsHeightPx = coords?.size?.height ?: 0,
                        expectedOwnerId = currentEntry.event.id,
                        sessionGeneration = handoffSnap.sessionGeneration,
                    )
                pillLayerLayoutW = resolved.widthPx
                pillLayerLayoutH = resolved.heightPx
            }

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
                // Task-053: selected cung key → bind outerCurrent live (timer/media tick).
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
                    // Selected subtree dispose: clear coords CHI khi van thuoc branch nay.
                    // Outgoing A sau B placed (coordsOwner=B) → khong xoa B (P1A).
                    DisposableEffect(entry.key) {
                        val branchOwnerId = entry.event.id
                        onDispose {
                            if (
                                statusBarCollapseHandoffClearPillCoordsOnDispose(
                                    StatusBarCollapseHandoffCoordsDisposeReason
                                        .SelectedSubtreeDispose
                                ) &&
                                    statusBarCollapseHandoffOutgoingBranchDisposeClearsCoords(
                                        disposedBranchOwnerId = branchOwnerId,
                                        currentCoordsOwnerId = pillLayerCoordsOwnerId,
                                    )
                            ) {
                                pillLayerCoords = null
                                pillLayerCoordsOwnerId = null
                                pillLayerLayoutW = 0
                                pillLayerLayoutH = 0
                                // Doc live session/layout luc dispose — khong capture stale.
                                viewModel.statusBarExpansion.reportCollapseHandoffRealPrepared(
                                    sessionGeneration = handoffSessionGenLatest,
                                    layoutGeneration = handoffLayoutGenLatest,
                                    ownerId = null,
                                    prepared = false,
                                    layoutWidthPx = 0,
                                    layoutHeightPx = 0,
                                    attached = false,
                                )
                            }
                        }
                    }
                    // Selected: outer live event; outgoing: giu branch snapshot (khong leak B).
                    val branchEntry =
                        statusBarChipSelectedLiveEntry(
                            branch = entry,
                            outerCurrent = currentEntryLatest,
                        )
                    val presentation =
                        statusBarChipEntryPresentation(
                            entry = branchEntry,
                            chipStyle = chipStyle,
                            generation = viewModel.statusBarExpansion.layoutGeneration,
                            carrierName = carrierName,
                        )
                    // freezeMotion vs liveValue — phase SSOT (ticker tiep khi alpha0 prepare).
                    // Prepare/Collapse/Ready: liveValue theo handoff (ke ca pillSuppressed).
                    val preparingReal =
                        statusBarCollapseHandoffNativeBlurPrepEnabled(handoffSnap.phase)
                    val freezeMotion =
                        handoffOwn.freezeMotion || (suppressedLatest && !preparingReal)
                    val liveValue =
                        if (preparingReal) {
                            handoffOwn.realLiveValue
                        } else if (suppressedLatest) {
                            false
                        } else {
                            handoffOwn.realLiveValue
                        }
                    // nativeGlassOn=false: blurAlpha ve=0 nhung includeBlur van true (warm engine).
                    // Khong ve 2 full backgrounds — overlay so huu glass visible.
                    val nativeGlass = statusBarCollapseHandoffNativeGlassFraction(handoffOwn)
                    StatusBarPillBody(
                        presentation = presentation,
                        includeBlur = true,
                        includeProgress = true,
                        freezeMotion = freezeMotion,
                        liveValue = liveValue,
                        blurAlpha = visibilityBlurAlpha * contentBlurAlpha * nativeGlass,
                        onPillLayerDrawn = null,
                        modifier =
                            Modifier
                                .squishAnimation(toggleCount)
                                .onGloballyPositioned { coords ->
                                    // FAIL A3: chi selected branch ghi size; outgoing khong poison.
                                    if (
                                        !statusBarOwnerBoundGlassStoreAllowed(
                                            branchKey = entry.key,
                                            targetKey = targetKeyLatest,
                                        )
                                    ) {
                                        return@onGloballyPositioned
                                    }
                                    val w = coords.size.width
                                    val h = coords.size.height
                                    // Luu coords + owner — session revalidate khong can OGP moi.
                                    pillLayerCoords = coords
                                    pillLayerCoordsOwnerId = entry.event.id
                                    // Size0 revoke — khong giu stale positive.
                                    pillLayerLayoutW = w
                                    pillLayerLayoutH = h
                                    captureVisibleGlass(
                                        coords = coords,
                                        branchKey = entry.key,
                                        ownerId = entry.event.id,
                                        updateCache = true,
                                    )
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
