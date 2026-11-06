@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Trace
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.res.R
import kotlin.math.abs
import kotlin.math.roundToInt

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.compose.theme.PlatformTheme
import com.android.systemui.shared.recents.utilities.Utilities
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.AlphaGlass
import com.android.systemui.axdynamicbar.shared.CardBg
import com.android.systemui.axdynamicbar.shared.ChipHighlightWidth
import com.android.systemui.axdynamicbar.shared.ChipTierPassiveHeight
import com.android.systemui.axdynamicbar.shared.CollapseHandbackHoldController
import com.android.systemui.axdynamicbar.shared.CollapseMorphPerfTrace
import com.android.systemui.axdynamicbar.shared.ExpandedMaxWidth
import com.android.systemui.axdynamicbar.shared.LocalBlurAlpha
import com.android.systemui.axdynamicbar.shared.LocalStatusBarOpenGlassAwaitDestinationDraw
import com.android.systemui.axdynamicbar.shared.LocalStatusBarOpenGlassDestinationDrawReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarOpenGlassDestinationSessionGen
import com.android.systemui.axdynamicbar.shared.LocalStatusBarPaintedCardBoundsReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarCardNaturalMeasureContextToken
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackCardNaturalSizeReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarYieldGlassToMorph
import com.android.systemui.axdynamicbar.shared.StackOpenGlassTransferController
import com.android.systemui.axdynamicbar.shared.StackOpenGlassTransferPhase
import com.android.systemui.axdynamicbar.shared.StatusBarOpenGlassDestinationDrawReport
import com.android.systemui.axdynamicbar.shared.axDbOpenGlassTrace
import com.android.systemui.axdynamicbar.shared.stackOpenGlassAwaitDestinationDraw
import com.android.systemui.axdynamicbar.shared.stackOpenGlassEffectiveTransferPhase
import com.android.systemui.axdynamicbar.shared.stackOpenGlassLocalBlurAlphaFactor
import com.android.systemui.axdynamicbar.shared.stackOpenGlassMergeHandoff
import com.android.systemui.axdynamicbar.shared.stackOpenGlassShouldAbandonWaitingForStackGesture
import com.android.systemui.axdynamicbar.shared.stackOpenGlassShouldCancelTransfer
import com.android.systemui.axdynamicbar.shared.LockedTargetHeight
import com.android.systemui.axdynamicbar.shared.MorphScaleFloor
import com.android.systemui.axdynamicbar.shared.StatusBarCardNaturalCacheEntry
import com.android.systemui.axdynamicbar.shared.StatusBarPaintedCardReportPx
import com.android.systemui.axdynamicbar.shared.TouchableInsetsRefreshCoalesce
import com.android.systemui.axdynamicbar.shared.chipAccentColorFor
import com.android.systemui.axdynamicbar.shared.chipContentColorFor
import com.android.systemui.axdynamicbar.shared.computeKeyguardMorphGeom
import com.android.systemui.axdynamicbar.shared.invalidateForInternalInsets
import com.android.systemui.axdynamicbar.shared.islandBlur
import com.android.systemui.axdynamicbar.shared.morphLocalCornerRadii
import com.android.systemui.axdynamicbar.shared.resolveStatusBarLockedTargetHeight
import com.android.systemui.axdynamicbar.shared.statusBarCardNaturalMeasureContextToken
import com.android.systemui.axdynamicbar.shared.statusBarLockHeightFromNaturalOrBridgePx
import com.android.systemui.axdynamicbar.shared.statusBarLookupCardNaturalHeightPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphAtomicEndpointSnapshot
import com.android.systemui.axdynamicbar.shared.statusBarPruneCardNaturalCache
import com.android.systemui.axdynamicbar.shared.statusBarPutCardNaturalSizeCache
import com.android.systemui.axdynamicbar.shared.statusBarStackEventOwnerKey
import com.android.systemui.axdynamicbar.shared.statusBarStackHostHeightFromCardNaturalPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleChromeAlphas
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleRingVisualDiameterPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleRingVisualTopLeftPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCollapseOverlayChromeFactor
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffAfterRecoveryAction
import com.android.systemui.axdynamicbar.shared.StatusBarCollapseHandoffPhase
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffAfterRecoveryTimeout
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffAwaitPreparedReactive
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffLiveSelectedOwnerId
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffLocalHoldReleaseAllowed
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffOwnerEndedAuthoritative
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffOwnershipOf
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffOverlayGlassAlpha
import com.android.systemui.axdynamicbar.shared.statusBarCollapseHandoffDummyFgAlpha
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragPhaseReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragSettlingReporter
import com.android.systemui.axdynamicbar.shared.LocalStatusBarStackDragYReporter
import com.android.systemui.axdynamicbar.shared.StackExpandedDragPhase
import com.android.systemui.axdynamicbar.shared.StatusBarHandbackReadinessFailsafeMs
import com.android.systemui.axdynamicbar.shared.StatusBarMorphContinuityOwner
import com.android.systemui.axdynamicbar.shared.statusBarAcceptPaintedCardReport
import com.android.systemui.axdynamicbar.shared.statusBarMorphFrozenPaintedWritePolicy
import com.android.systemui.axdynamicbar.shared.statusBarMorphFrozenRebindFromSelectedOnLeaveRest
import com.android.systemui.axdynamicbar.shared.StatusBarMorphFrozenPaintedWrite
import com.android.systemui.axdynamicbar.shared.StatusBarOwnedPaintedRectPx
import com.android.systemui.axdynamicbar.shared.StatusBarPaintedRectProvenance
import com.android.systemui.axdynamicbar.shared.statusBarMorphPaintedLrtbUpdateAllowed
import com.android.systemui.axdynamicbar.shared.statusBarMorphSharedGlassDragTranslationYPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphDummyContentLocalOffsetPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphDummyLayerScale
import com.android.systemui.axdynamicbar.shared.statusBarMorphEndpointReady
import com.android.systemui.axdynamicbar.shared.statusBarMorphGeometryCornerProgress
import com.android.systemui.axdynamicbar.shared.statusBarMorphHostOriginFromVisualRectPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphLerpVisualRectPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphExpandedTargetHostPlacementPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphPaintedBoundsUndoHostScale
import com.android.systemui.axdynamicbar.shared.statusBarMorphPaintedDestFromLocalInTargetHost
import com.android.systemui.axdynamicbar.shared.statusBarMorphPaintedDestReadyForFreeze
import com.android.systemui.axdynamicbar.shared.statusBarMorphPanelWidthGatePx
import com.android.systemui.axdynamicbar.shared.statusBarMorphPersistentSharedGlassAlpha
import com.android.systemui.axdynamicbar.shared.statusBarMorphPillOriginInHostPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCaptureMatchesDestination
import com.android.systemui.axdynamicbar.shared.statusBarMorphCollapseDestinationOwnerId
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldAbortCollapseVisual
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldTerminalDismissNoDestination
import com.android.systemui.axdynamicbar.shared.statusBarMorphPillShellAlignedRect
import com.android.systemui.axdynamicbar.shared.statusBarMorphResolveSideInsetPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphResolveVisualTargetHPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphResolveVisualTargetWPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphRunEndpointPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphShellFallbackArgb
import com.android.systemui.axdynamicbar.shared.statusBarMorphShellTintArgb
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldConfirmPillSuppressed
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldTargetExpanded
import com.android.systemui.axdynamicbar.shared.statusBarMorphStageTimeoutEndpointPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphTargetHeightPx
import com.android.systemui.axdynamicbar.shared.statusBarOwnedPaintedRectOrNull
import com.android.systemui.axdynamicbar.shared.statusBarResolveMeasuredSelectedDestination
import com.android.systemui.axdynamicbar.shared.statusBarSelectShellPaintedRect
import com.android.systemui.axdynamicbar.shared.stackExpandedGlassHandoffDecision
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedBottomPadDp
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedSidePadDp
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedTopPadDp
import com.android.systemui.axdynamicbar.shared.StatusBarMorphCollapseMs
import com.android.systemui.axdynamicbar.shared.StatusBarMorphExpandMs
import com.android.systemui.axdynamicbar.shared.StatusBarMorphMotionEasing
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualShellSizePx
import com.android.systemui.axdynamicbar.shared.statusBarOverlayHideDelayMs
import com.android.systemui.axdynamicbar.shared.statusBarShouldUpdateMorphEndpoint
import com.android.systemui.blur.AxSystemFrost
import com.android.systemui.axdynamicbar.ui.compose.ExpandedIslandContent
import com.android.systemui.axdynamicbar.ui.compose.StackedAlertPanels
import com.android.systemui.axdynamicbar.ui.compose.StatusBarMorphCircleRingChrome
import com.android.systemui.axdynamicbar.ui.compose.StatusBarPillBody
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationOf
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.view.WindowInsets

/** Pill morph — SSOT 500/600 F741N; NIC/clock hide dong bo. */
private const val StatusBarExpandMs = StatusBarMorphExpandMs
/** Collapse morph = 600 F741N (= slot show). */
private const val StatusBarCollapseMs = StatusBarMorphCollapseMs
private val StatusBarPillWidthFallback = 56.dp
/** Cho natural measure (~400ms@60Hz); timeout dung last envelope — khong start H=pill. */
private const val MorphEndpointStageFrames = 24
/** Tran cho them (~800ms tong) neu RemoteViews tre; het van khong empty-start. */
private const val MorphEndpointHardTimeoutFrames = 48

/**
 * Overlay lifecycle tach alert vs morph:
 * - Alert exit: grace 400ms (AnimatedVisibility exit khong bi cat).
 * - Morph collapse settled + pill handoff: chi +50ms roi remove.
 * EXIT_ANIM_DURATION 600 chi WRAP sau morph — khong dung cho alert shrink.
 */
private const val EXIT_ANIM_DURATION = 600L

private data class OverlayNeedSnapshot(
    val expanded: Boolean,
    val pillSuppressed: Boolean,
    val hasAlert: Boolean,
    val onKeyguard: Boolean,
)

@SysUISingleton
class AxDynamicBarExpandedPanel
@Inject
constructor(
    @Application private val context: Context,
    private val windowManager: WindowManager,
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainHandler: Handler,
    private val viewModel: AxDynamicBarChipViewModel,
) {
    private var overlayView: ComposeView? = null
    private var panelLifecycleOwner: PanelLifecycleOwner? = null
    private var hideOverlayJob: Job? = null

    private var replyCaptureActive = false
    /** Alert dang hien (khong expanded island) — can MATCH_PARENT de AXIS_DOWN khong bi cat. */
    private var alertOverlayActive = false
    /** Compact alert: touchable chi the; false = soft-max + tap ngoai dismiss. */
    private var alertCompactMode = true
    private var imeVisible = false
    private var imeHeight = 0
    /**
     * Day the expand (px, window coords) — touchable chi toi day (+ slack stack)
     * de phan duoi man van cham home duoc (MATCH_PARENT chi de ve, khong nuot touch).
     */
    private var expandedContentBottomPx = 0
    /** Bounds the alert (window px) — Compact dung rect that; Expanded dung day + soft-max. */
    private val alertContentBounds = Rect()

    /**
     * Coalesce touchable insets: invalidate ≤1/frame (khong requestLayout content).
     * ViewRootImpl.invalidate scheduleTraversals; dispatchOnComputeInternalInsets van chay.
     */
    private val insetsCoalesce =
        TouchableInsetsRefreshCoalesce(
            onInvalidate = { overlayView?.invalidateForInternalInsets() },
        )

    /** Async Trace closing that → end/handoff/cancel (cookie=layoutGen). */
    private val collapsePerfTrace = CollapseMorphPerfTrace()

    private val touchableInsetsListener =
        ViewTreeObserver.OnComputeInternalInsetsListener { info ->
            val v = overlayView ?: return@OnComputeInternalInsetsListener
            when {
                replyCaptureActive -> {
                    val touchableBottom = when {
                        !imeVisible -> v.height
                        imeHeight > 0 -> v.height - imeHeight
                        else -> v.height / 2 // IME height unknown: never cover the keyboard
                    }
                    info.setTouchableInsets(
                        ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                    )
                    info.touchableRegion.set(0, 0, v.width, touchableBottom.coerceAtLeast(0))
                }
                // Expanded / dang morph collapse: chi vung the (+ slack keo).
                viewModel.isExpanded.value || viewModel.statusBarExpansion.pillSuppressed.value -> {
                    if (v.width <= 0 || v.height <= 0) return@OnComputeInternalInsetsListener
                    info.setTouchableInsets(
                        ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                    )
                    info.touchableRegion.set(
                        0,
                        0,
                        v.width,
                        contentTouchableBottom(expandedContentBottomPx, v.height),
                    )
                }
                // Alert: Compact = dung bounds the; Expanded = soft-max full width nhu island.
                alertOverlayActive -> {
                    if (v.width <= 0 || v.height <= 0) return@OnComputeInternalInsetsListener
                    info.setTouchableInsets(
                        ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                    )
                    if (alertCompactMode) {
                        val b = alertContentBounds
                        if (b.isEmpty) {
                            val fallback = (v.height * 0.08f).toInt().coerceAtLeast(1)
                            info.touchableRegion.set(0, 0, v.width, fallback)
                        } else {
                            val slack = (v.height * 0.01f).toInt().coerceAtLeast(1)
                            info.touchableRegion.set(
                                b.left.coerceAtLeast(0),
                                b.top.coerceAtLeast(0),
                                b.right.coerceAtMost(v.width),
                                (b.bottom + slack).coerceAtMost(v.height),
                            )
                        }
                    } else {
                        info.touchableRegion.set(
                            0,
                            0,
                            v.width,
                            contentTouchableBottom(alertContentBounds.bottom, v.height),
                        )
                    }
                }
                else -> return@OnComputeInternalInsetsListener
            }
        }

    /**
     * Day touchable: day the (window Y) + slack.
     * Soft-max ~22% man — ComposeView MATCH_PARENT hay phinh measure; the thuc te
     * ~statusBar+card. Gesture stack so huu touch sau DOWN nen khong can maxDrag.
     */
    private fun contentTouchableBottom(contentBottomPx: Int, windowHeight: Int): Int {
        val softMax = (windowHeight * 0.22f).toInt().coerceAtLeast(1)
        val clamped =
            when {
                contentBottomPx <= 0 -> (windowHeight * 0.14f).toInt()
                contentBottomPx > softMax -> softMax
                else -> contentBottomPx
            }
        val slack = (windowHeight * 0.025f).toInt().coerceAtLeast(1)
        return (clamped + slack).coerceIn(1, softMax + slack)
    }

    /**
     * Cap nhat day the tu Compose — ghi bottom ngay; schedule invalidate (khong requestLayout)
     * de InternalInsets chay lai. Endpoint bottom<=0 → flushNow.
     */
    private fun reportExpandedContentBottom(bottomPx: Int) {
        if (bottomPx == expandedContentBottomPx) return
        expandedContentBottomPx = bottomPx
        ensureMainThread {
            if (bottomPx <= 0) {
                insetsCoalesce.flushNow()
            } else {
                insetsCoalesce.schedule()
            }
        }
    }

    private fun reportAlertContentBounds(left: Int, top: Int, right: Int, bottom: Int) {
        if (
            alertContentBounds.left == left &&
                alertContentBounds.top == top &&
                alertContentBounds.right == right &&
                alertContentBounds.bottom == bottom
        ) {
            return
        }
        alertContentBounds.set(left, top, right, bottom)
        // Ngoai pham vi collapse-bottom: giu requestLayout (chua A/B chung minh invalidate du).
        ensureMainThread { overlayView?.requestLayout() }
    }

    fun init() {
        viewModel.interactor.onCollapseRequested = { viewModel.statusBarExpansion.collapse() }
        viewModel.interactor.onFocusableRequested = { focusable -> setOverlayFocusable(focusable) }
        viewModel.interactor.onAlertReplyCaptureRequested = { enabled -> setReplyCapture(enabled) }

        // pillSuppressed: giu overlay suot collapse morph 600ms sau khi isExpanded=false.
        // Alert exit: delay 400ms (khong dung 50ms toan cuc — cat AnimatedVisibility exit).
        var previousHadAlert = false
        combine(
                viewModel.isExpanded,
                viewModel.statusBarExpansion.pillSuppressed,
                viewModel.interactor.uiState.map { it.notificationAlert != null },
                viewModel.isOnKeyguard,
            ) { expanded, pillSuppressed, hasAlert, onKeyguard ->
                OverlayNeedSnapshot(expanded, pillSuppressed, hasAlert, onKeyguard)
            }
            .onEach { snap ->
                val needed =
                    !snap.onKeyguard &&
                        (snap.expanded || snap.pillSuppressed || snap.hasAlert)
                if (needed) {
                    hideOverlayJob?.cancel()
                    hideOverlayJob = null
                    showOverlay()
                } else {
                    // Alert vua mat + khong morph: grace exit. Morph settle: 50ms handoff.
                    val alertExitGrace =
                        previousHadAlert &&
                            !snap.hasAlert &&
                            !snap.expanded &&
                            !snap.pillSuppressed
                    val delayMs = statusBarOverlayHideDelayMs(alertExitGrace)
                    hideOverlayJob?.cancel()
                    hideOverlayJob =
                        applicationScope.launch {
                            delay(delayMs)
                            hideOverlay()
                        }
                }
                previousHadAlert = snap.hasAlert
            }
            .launchIn(applicationScope)

        viewModel.isExpanded
            .onEach { expanded ->
                updateOverlay(expanded)
            }
            .launchIn(applicationScope)

        // Alert-only: MATCH_PARENT + touchable region (tranh WRAP_CONTENT cat the keo xuong).
        viewModel.interactor.uiState
            .map { it.notificationAlert != null }
            .onEach { hasAlert ->
                alertOverlayActive = hasAlert
                if (!hasAlert) {
                    alertContentBounds.setEmpty()
                    alertCompactMode = true
                }
                ensureMainThread {
                    syncAlertOverlayHeight(hasAlert)
                    overlayView?.requestLayout()
                }
            }
            .launchIn(applicationScope)

        viewModel.interactor.alertCompactMode
            .onEach { compact ->
                alertCompactMode = compact
                ensureMainThread { overlayView?.requestLayout() }
            }
            .launchIn(applicationScope)
    }

    private fun ensureMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action()
        else mainHandler.post(action)
    }

    private fun showOverlay() {
        ensureMainThread { showOverlayInternal() }
    }

    private fun showOverlayInternal() {
        if (overlayView != null) return

        val lifecycleOwner = PanelLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val windowMetrics = windowManager.currentWindowMetrics
        val statusBarTop = windowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars())
            .top
        val hasCutout = windowMetrics.windowInsets
            .getInsets(WindowInsets.Type.displayCutout())
            .top > 0

        val view = ComposeView(context).apply {
            // translationY stack keo xuong khong bi View clip.
            clipChildren = false
            clipToPadding = false
            setContent {
                PlatformTheme {
                    OverlayContent(
                        viewModel,
                        statusBarTop,
                        hasCutout,
                        onExpandedContentBottomPx = ::reportExpandedContentBottom,
                        onAlertContentBoundsPx = ::reportAlertContentBounds,
                        collapsePerfTrace = collapsePerfTrace,
                    )
                }
            }
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_OUTSIDE -> {
                        // Tap ngoai touchable (home) → an expand / alert mo rong.
                        // Compact alert: khong dismiss (touch pass-through xuong app).
                        // WATCH_OUTSIDE_TOUCH van nhan ACTION_OUTSIDE khi touch toi cua so duoi.
                        when {
                            viewModel.isExpanded.value ->
                                viewModel.collapseFromOutsideTouch(event.downTime)
                            alertOverlayActive && !alertCompactMode ->
                                viewModel.interactor.dismissNotificationAlertFromOutside()
                        }
                        true
                    }
                    MotionEvent.ACTION_DOWN ->
                        if (replyCaptureActive) {
                            viewModel.interactor.onOverlayTouchOutsideCard()
                            true
                        } else false
                    else -> false
                }
            }
            setOnApplyWindowInsetsListener { v, insets ->
                val visible = insets.isVisible(WindowInsets.Type.ime())
                val height = insets.getInsets(WindowInsets.Type.ime()).bottom
                if (visible != imeVisible || height != imeHeight) {
                    imeVisible = visible
                    imeHeight = height
                    // Reply/IME: ngoai pham vi collapse-bottom — giu requestLayout cu.
                    if (replyCaptureActive) v.requestLayout()
                }
                viewModel.interactor.onOverlayImeVisibilityChanged(visible)
                v.onApplyWindowInsets(insets)
            }
            viewTreeObserver.addOnComputeInternalInsetsListener(touchableInsetsListener)
        }

        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setViewTreeOnBackPressedDispatcherOwner(lifecycleOwner)
        panelLifecycleOwner = lifecycleOwner

        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.findOnBackInvokedDispatcher()?.let { dispatcher ->
                        lifecycleOwner.onBackPressedDispatcher.setOnBackInvokedDispatcher(
                            dispatcher
                        )
                    }
                    updateTouchableRegion(v, viewModel.isExpanded.value)
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
        )
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            updateTouchableRegion(v, viewModel.isExpanded.value)
        }

        val isCurrentlyExpanded = viewModel.isExpanded.value
        val pillSuppressed = viewModel.statusBarExpansion.pillSuppressed.value
        val hasAlert = viewModel.interactor.uiState.value.notificationAlert != null
        alertOverlayActive = hasAlert
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            if (isCurrentlyExpanded || pillSuppressed || hasAlert) 0
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        // MATCH_PARENT khi expand/morph/alert: translationY stack khong bi WRAP_CONTENT cat.
        val overlayHeight =
            if (isCurrentlyExpanded || pillSuppressed || hasAlert) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayHeight,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                flags,
                PixelFormat.TRANSLUCENT,
            )
        params.gravity = Gravity.TOP or Gravity.FILL_HORIZONTAL
        params.title = "AxDynamicBarExpanded"
        params.fitInsetsTypes = 0
        // MATCH_PARENT + alpha=1 neu khong trusted → InputDispatcher drop touch home
        // ("Untrusted touch due to occlusion by SystemUI") du touchableRegion hep.
        params.setTrustedOverlay()

        windowManager.addView(view, params)
        overlayView = view
    }

    /**
     * Chi dung InternalInsets (touchableInsetsListener) — khong setTouchableRegion full man
     * (set full man truoc day da chan home). Attach/layout/structural: giu requestLayout.
     * Chi collapse-bottom dung invalidate coalesce (reportExpandedContentBottom).
     */
    private fun updateTouchableRegion(view: View, expanded: Boolean) {
        view.requestLayout()
    }

    private fun hideOverlay() {
        ensureMainThread { hideOverlayInternal() }
    }

    private fun hideOverlayInternal() {
        shrinkRunnable?.let { mainHandler.removeCallbacks(it) }
        shrinkRunnable = null
        insetsCoalesce.cancelAndBumpGeneration()
        collapsePerfTrace.forceCancel("hideOverlay")
        overlayView?.let { view ->
            panelLifecycleOwner?.apply {
                handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            windowManager.removeViewImmediate(view)
        }
        overlayView = null
        panelLifecycleOwner = null
        replyCaptureActive = false
        alertOverlayActive = false
        alertCompactMode = true
        imeVisible = false
        imeHeight = 0
        expandedContentBottomPx = 0
        alertContentBounds.setEmpty()
    }

    private var shrinkRunnable: Runnable? = null

    private fun updateOverlay(expanded: Boolean) = ensureMainThread {
        shrinkRunnable?.let { mainHandler.removeCallbacks(it) }
        shrinkRunnable = null
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        val morphing = viewModel.statusBarExpansion.pillSuppressed.value
        if (expanded || morphing) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            // MATCH_PARENT: graphicsLayer.translationY khong mo rong WRAP_CONTENT.
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.fitInsetsTypes = 0
            params.setTrustedOverlay()
            windowManager.updateViewLayout(view, params)
            updateTouchableRegion(view, true)
        } else {
            if (alertOverlayActive) {
                params.flags =
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.fitInsetsTypes = 0
                windowManager.updateViewLayout(view, params)
                updateTouchableRegion(view, false)
                view.requestLayout()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager.updateViewLayout(view, params)
                updateTouchableRegion(view, false)
                val runnable = Runnable {
                    val v = overlayView ?: return@Runnable
                    if (alertOverlayActive ||
                        replyCaptureActive ||
                        viewModel.statusBarExpansion.pillSuppressed.value
                    ) {
                        return@Runnable
                    }
                    val p = v.layoutParams as? WindowManager.LayoutParams ?: return@Runnable
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                    windowManager.updateViewLayout(v, p)
                }
                shrinkRunnable = runnable
                mainHandler.postDelayed(runnable, EXIT_ANIM_DURATION)
            }
        }
    }

    /**
     * Alert hien: MATCH_PARENT (khong cat the keo).
     * Alert an: KHONG WRAP ngay — de AnimatedVisibility exit + grace 400ms;
     * hideOverlay remove cua so. EXIT_ANIM_DURATION 600 chi morph WRAP.
     */
    private fun syncAlertOverlayHeight(hasAlert: Boolean) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (viewModel.isExpanded.value ||
            replyCaptureActive ||
            viewModel.statusBarExpansion.pillSuppressed.value
        ) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.fitInsetsTypes = 0
            }
            windowManager.updateViewLayout(view, params)
            return
        }
        if (hasAlert) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.fitInsetsTypes = 0
            if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
                params.height = WindowManager.LayoutParams.MATCH_PARENT
            }
            windowManager.updateViewLayout(view, params)
            view.requestLayout()
        }
        // hasAlert=false: giu height hien tai den hideOverlay (khong WRAP cat exit).
    }

    private fun setReplyCapture(enabled: Boolean) = ensureMainThread {
        if (replyCaptureActive == enabled) return@ensureMainThread
        replyCaptureActive = enabled
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        // pillSuppressed: dang morph collapse 600ms — khong WRAP (cat geom).
        val keepMatch =
            enabled ||
                viewModel.isExpanded.value ||
                alertOverlayActive ||
                viewModel.statusBarExpansion.pillSuppressed.value
        params.height =
            if (keepMatch) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }
        if (keepMatch) {
            params.fitInsetsTypes = 0
        }
        windowManager.updateViewLayout(view, params)
        view.requestLayout()
    }

    private fun setOverlayFocusable(focusable: Boolean) = ensureMainThread {
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(view, params)
    }
}

@Composable
private fun OverlayContent(
    viewModel: AxDynamicBarChipViewModel,
    statusBarHeightPx: Int,
    hasCutout: Boolean,
    onExpandedContentBottomPx: (Int) -> Unit,
    onAlertContentBoundsPx: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    collapsePerfTrace: CollapseMorphPerfTrace,
) {
    val density = LocalDensity.current
    val isLargeScreen = Utilities.isLargeScreen(LocalContext.current)

    val largeScreenExtra = if (isLargeScreen) 4.dp else 0.dp
    val topPad = if (hasCutout) largeScreenExtra
        else with(density) { statusBarHeightPx.toDp() } + largeScreenExtra
    val chipState by viewModel.chipState.collectAsStateWithLifecycle()
    val chipStyle by viewModel.chipStyle.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isExpanded.collectAsStateWithLifecycle()
    val expandOnlyEventId by
        viewModel.statusBarExpansion.expandOnlyEventId.collectAsStateWithLifecycle()
    val uiState by viewModel.interactor.uiState.collectAsStateWithLifecycle()
    val isOnKeyguard by viewModel.isOnKeyguard.collectAsStateWithLifecycle()
    val notifAlerts = uiState.notificationAlerts
    val pinnedAlertKey = uiState.notificationAlert?.sbn?.key
    val compactNotifs by viewModel.interactor.settings.compactNotifications.collectAsStateWithLifecycle()
    val alertCompact by viewModel.interactor.alertCompactMode.collectAsStateWithLifecycle()

    val lastAlerts = remember { mutableStateOf<List<IslandEvent.Notification>>(emptyList()) }
    val lastPinnedAlertKey = remember { mutableStateOf<String?>(null) }
    if (notifAlerts.isNotEmpty()) {
        lastAlerts.value = notifAlerts
        lastPinnedAlertKey.value = pinnedAlertKey
    }

    val pillSuppressed by
        viewModel.statusBarExpansion.pillSuppressed.collectAsStateWithLifecycle()
    val capturedPill by
        viewModel.statusBarExpansion.capturedPillFrame.collectAsStateWithLifecycle()
    val capturedPillOwnerId by
        viewModel.statusBarExpansion.capturedPillOwnerId.collectAsStateWithLifecycle()
    val frozenPresentation by
        viewModel.statusBarExpansion.frozenPillPresentation.collectAsStateWithLifecycle()
    val layoutGen = viewModel.statusBarExpansion.layoutGeneration

    val showNotif = !isExpanded && !pillSuppressed && notifAlerts.isNotEmpty()
    val notifVisible = remember { MutableTransitionState(false) }

    // Explicit initial collapsed — tranh updateTransition(isExpanded=true) CURRENT=TARGET
    // → progress=1 snap (first open khong animate).
    val morphTransitionState = remember { MutableTransitionState(false) }
    val morphTransition =
        updateTransition(morphTransitionState, label = "sb_morph_transition")
    // Track 1 geometry: PathInterpolator overshoot (clamp corner / left neo rieng).
    val morphProgress by
        morphTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(StatusBarExpandMs, easing = StatusBarMorphMotionEasing)
                } else {
                    tween(StatusBarCollapseMs, easing = StatusBarMorphMotionEasing)
                }
            },
            label = "sb_morph_progress",
        ) { expanded -> if (expanded) 1f else 0f }
    // linearT: track 2/3/4 (dummy/panel/color) — DOC LAP geom eased; reverse an toan.
    val morphLinearT by
        morphTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(StatusBarExpandMs, easing = LinearEasing)
                } else {
                    tween(StatusBarCollapseMs, easing = LinearEasing)
                }
            },
            label = "sb_morph_linear_t",
        ) { expanded -> if (expanded) 1f else 0f }

    var hadMorphExpand by remember { mutableStateOf(false) }
    // H dich khoa theo content owner — KHONG fallback 220.dp (tranh first-frame pop).
    var lockedTargetHeight by remember { mutableStateOf(LockedTargetHeight()) }
    // W dich do natural (StackedExpandedPanels envelope); 0 = chua do.
    var lockedTargetWidthPx by remember { mutableStateOf(0) }
    // Envelope H cuoi (expanded) — bridge khi owner switch; reset chi luc collapse settle.
    // task-031: chi ghi tu TOP natural (+pads), KHONG max(under).
    var lastExpandedEnvelopeHPx by remember { mutableStateOf(0) }
    // Cache natural CARD (khong pads) theo owner + measure-context token (task-032).
    val cardNaturalByOwner =
        remember { mutableStateMapOf<String, StatusBarCardNaturalCacheEntry>() }
    // Active context token — holder thuong (khong Snapshot) sync truoc layout/report.
    val activeNaturalMeasureContextToken = remember { intArrayOf(0) }
    // Active painted owner — reporter reject stale sai owner (task-037).
    val activePaintedOwnerKeyHolder = remember { arrayOf("") }
    // Stack host envelope lan do cuoi (max layers+pads) — touch/host; khong khoa top.
    var lastStackHostEnvelopeHPx by remember { mutableIntStateOf(0) }
    var lastStackHostEnvelopeWPx by remember { mutableIntStateOf(0) }
    // Endpoint H/W freeze cho MOT morph run — tranh retarget mid (F48).
    var morphRunTargetHPx by remember { mutableStateOf(0) }
    var morphRunTargetWPx by remember { mutableStateOf(0) }
    // Top-card painted LRTB + W/H — chi selected top (isTop&&!isLeaving) report.
    val paintedCardWState = remember { mutableIntStateOf(0) }
    val paintedCardHState = remember { mutableIntStateOf(0) }
    val paintedCardLeftState = remember { mutableFloatStateOf(0f) }
    val paintedCardTopState = remember { mutableFloatStateOf(0f) }
    val paintedCardRightState = remember { mutableFloatStateOf(0f) }
    val paintedCardBottomState = remember { mutableFloatStateOf(0f) }
    // Freeze painted trong MOT morph run — tranh retarget/snap right-edge mid-flight.
    val frozenPaintedWState = remember { mutableIntStateOf(0) }
    val frozenPaintedHState = remember { mutableIntStateOf(0) }
    val frozenPaintedLeftState = remember { mutableFloatStateOf(0f) }
    val frozenPaintedTopState = remember { mutableFloatStateOf(0f) }
    val frozenPaintedRightState = remember { mutableFloatStateOf(0f) }
    val frozenPaintedBottomState = remember { mutableFloatStateOf(0f) }
    // task-037/038: owned atomic rect — morph-frozen vs selected-rest tach biet.
    var morphFrozenOwnedRect by remember { mutableStateOf<StatusBarOwnedPaintedRectPx?>(null) }
    var selectedRestOwnedRect by remember { mutableStateOf<StatusBarOwnedPaintedRectPx?>(null) }
    var livePaintedOwnedRect by remember { mutableStateOf<StatusBarOwnedPaintedRectPx?>(null) }
    var wasMorphExpandedAtRest by remember { mutableStateOf(false) }
    var lastPaintedRectProvenance by remember {
        mutableStateOf(StatusBarPaintedRectProvenance.None)
    }
    val paintedCardWPx = paintedCardWState.intValue
    val paintedCardHPx = paintedCardHState.intValue
    // Sau first composition/layout moi drive target expand.
    var morphFirstFramePassed by remember { mutableStateOf(false) }
    var morphAllowFallbackRect by remember { mutableStateOf(false) }
    // Continuity + width-gate latch (remember owner — KHONG SideEffect state loop).
    val morphContinuity = remember { StatusBarMorphContinuityOwner() }

    val morphClosing =
        !morphTransition.targetState &&
            (morphTransition.currentState || abs(morphProgress) > 0.01f)
    // Trace: reverse/reopen truoc start; chi start khi closing that && !isExpanded.
    LaunchedEffect(morphClosing, layoutGen, isExpanded) {
        if (isExpanded) {
            // Reopen/reverse: dong session active vo dieu kien (gen co the doi).
            if (collapsePerfTrace.isActive) {
                collapsePerfTrace.forceCancel("reverse")
            }
            return@LaunchedEffect
        }
        if (morphClosing) {
            collapsePerfTrace.onClosingStarted(layoutGen)
        }
    }
    val morphProgressClamped = morphProgress.coerceIn(0f, 1f)
    val morphLinearClamped = morphLinearT.coerceIn(0f, 1f)
    // Dummy/shell/panel/color danh gia sau khi co visualShellW (trong host); placeholder
    // truoc layout dung evaluate voi width=0 (panel 0, gate chua cross).
    val isMorphing = abs(morphProgress) > 0.01f
    val morphRunning = morphTransition.isRunning
    // Geometry settle: current/target false + !running — KHONG end chi vi progress≈0.01.
    val morphGeometrySettled =
        !morphTransition.currentState &&
            !morphTransition.targetState &&
            !morphRunning
    val morphExpandedAtRest =
        morphTransition.currentState &&
            morphTransition.targetState &&
            !morphRunning &&
            abs(morphProgress - 1f) <= 0.01f
    // Freeze painted qua reporter — gate theo morphRunning + local+targetHost.
    val paintedFreezeGate = rememberUpdatedState(morphRunning to morphExpandedAtRest)
    // SSOT dragY/phase tu StackedExpandedPanels — glass owner theo phase (task-048/027).
    // FAIL B1: doc floatValue/settling/phase live — CAM rememberUpdatedState (lag 1 frame).
    val stackDragYState = remember { mutableFloatStateOf(0f) }
    val stackDragSettlingState = remember { mutableStateOf(false) }
    val stackDragPhaseState = remember { mutableStateOf(StackExpandedDragPhase.Idle) }
    // morphExpandedAtRest cho graphicsLayer doc luc draw (khong capture composition stale).
    val morphExpandedAtRestState = rememberUpdatedState(morphExpandedAtRest)
    // task-045: open morph→CardLocal — GIU shared den dest surface ACK (native|fallback).
    val openGlassCtrl = remember { StackOpenGlassTransferController() }
    var openGlassPhase by remember {
        mutableStateOf(StackOpenGlassTransferPhase.Idle)
    }
    var openGlassDestAcked by remember { mutableStateOf(false) }
    var openGlassSessionGen by remember { mutableIntStateOf(0) }
    // View + live intent cho post-after-draw (024/039: stale gen no-op).
    val openGlassHostViewHolder = remember { arrayOfNulls<View>(1) }
    val openGlassLiveExpandedHolder = remember { booleanArrayOf(true) }
    val openGlassDestDrawReporter =
        remember<(StatusBarOpenGlassDestinationDrawReport) -> Unit> {
            { report ->
                val gen = report.sessionGen
                val applyAck = Runnable {
                    // Stale post sau reverse/session/owner/ctx doi — full no-op.
                    if (openGlassCtrl.sessionGen != gen) {
                        axDbOpenGlassTrace(
                            "dest-drop-post",
                            "stale-gen cb=$gen live=${openGlassCtrl.sessionGen}",
                        )
                        return@Runnable
                    }
                    val liveReq = openGlassCtrl.request
                    if (
                        liveReq == null ||
                            liveReq.ownerKey != report.ownerKey ||
                            liveReq.contextToken != report.contextToken
                    ) {
                        axDbOpenGlassTrace(
                            "dest-drop-post",
                            "owner/ctx cb=${report.ownerKey}/${report.contextToken} " +
                                "req=${liveReq?.ownerKey}/${liveReq?.contextToken}",
                        )
                        return@Runnable
                    }
                    if (
                        openGlassCtrl.onDestinationSurfaceDrawn(
                            callbackSessionGen = report.sessionGen,
                            callbackOwnerKey = report.ownerKey,
                            callbackContextToken = report.contextToken,
                            callbackLayoutWidthPx = report.layoutWidthPx,
                            callbackLayoutHeightPx = report.layoutHeightPx,
                            paintKind = report.paintKind,
                            blurSurfaceAlpha = report.blurSurfaceAlpha,
                            liveExpandedIntent = openGlassLiveExpandedHolder[0],
                            legacyContentOnly = false,
                        )
                    ) {
                        openGlassPhase = openGlassCtrl.phase
                        openGlassDestAcked = openGlassCtrl.destinationFirstDrawAcked
                        openGlassSessionGen = openGlassCtrl.sessionGen
                    }
                }
                val host = openGlassHostViewHolder[0]
                if (host != null) {
                    host.post(applyAck)
                } else {
                    applyAck.run()
                }
            }
        }
    // Host MOVING (graphicsLayer) — chi de undo → LOCAL; KHONG lam destination.
    val hostSxForPainted = remember { mutableFloatStateOf(1f) }
    val hostSyForPainted = remember { mutableFloatStateOf(1f) }
    val hostWinLeftForPainted = remember { mutableFloatStateOf(0f) }
    val hostWinTopForPainted = remember { mutableFloatStateOf(0f) }
    // TARGET host @ rest (layout constraints) — doc lap moving window.
    val targetHostLeftForPainted = remember { mutableFloatStateOf(0f) }
    val targetHostTopForPainted = remember { mutableFloatStateOf(0f) }
    val targetHostPlacementReady = remember { mutableStateOf(false) }
    // Stack layer natural W/H + contextToken — reject stale; lock o BoxWithConstraints.
    val stackCardNaturalReporter =
        remember<(String, Int, Int, Int) -> Unit> {
            { eventId, widthPx, heightPx, reportToken ->
                statusBarPutCardNaturalSizeCache(
                    cache = cardNaturalByOwner,
                    eventId = eventId,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    reportContextToken = reportToken,
                    activeContextToken = activeNaturalMeasureContextToken[0],
                )
            }
        }
    val paintedCardBoundsReporter =
        remember<(StatusBarPaintedCardReportPx) -> Unit> {
            { report ->
                val (running, atRest) = paintedFreezeGate.value
                val activeOwner = activePaintedOwnerKeyHolder[0]
                val activeCtx = activeNaturalMeasureContextToken[0]
                val reportOwner =
                    report.ownerKey.ifEmpty { activeOwner }
                // Under / sai owner / sai context — khong overwrite (task-037).
                val acceptReport =
                    statusBarAcceptPaintedCardReport(
                        reportOwnerKey = reportOwner,
                        reportContextToken = report.contextToken,
                        activeOwnerKey = activeOwner,
                        activeContextToken = activeCtx,
                        isSelectedTop = true,
                    )
                if (acceptReport) {
                    // Layout W/H (pre-scale node) — SSOT morph visual size.
                    val w = report.layoutWidth
                    val h = report.layoutHeight
                    // Undo → LOCAL trong host (khong + hostWindow origin).
                    val hostLocal =
                        statusBarMorphPaintedBoundsUndoHostScale(
                            windowLeftPx = report.windowLeftPx,
                            windowTopPx = report.windowTopPx,
                            windowRightPx = report.windowRightPx,
                            windowBottomPx = report.windowBottomPx,
                            hostWindowLeftPx = hostWinLeftForPainted.floatValue,
                            hostWindowTopPx = hostWinTopForPainted.floatValue,
                            hostSx = hostSxForPainted.floatValue,
                            hostSy = hostSyForPainted.floatValue,
                            ownerKey = reportOwner,
                            contextToken =
                                if (report.contextToken != 0) {
                                    report.contextToken
                                } else {
                                    activeCtx
                                },
                        )
                    if (w > 0) {
                        paintedCardWState.intValue = w
                    }
                    if (h > 0) {
                        paintedCardHState.intValue = h
                    }
                    // Drag/settle: KHONG poison LRTB; skip dest khi |dragY|>eps.
                    val allowLrtb =
                        statusBarMorphPaintedLrtbUpdateAllowed(
                            dragYPx = stackDragYState.floatValue,
                            settling = stackDragSettlingState.value,
                        )
                    if (allowLrtb) {
                        val placementReady = targetHostPlacementReady.value
                        val destReady =
                            statusBarMorphPaintedDestReadyForFreeze(
                                targetHostPlacementReady = placementReady,
                                localLeftPx = hostLocal.windowLeftPx,
                                localTopPx = hostLocal.windowTopPx,
                                localRightPx = hostLocal.windowRightPx,
                                localBottomPx = hostLocal.windowBottomPx,
                            )
                        if (destReady) {
                            val dest =
                                statusBarMorphPaintedDestFromLocalInTargetHost(
                                    targetHostLeftPx = targetHostLeftForPainted.floatValue,
                                    targetHostTopPx = targetHostTopForPainted.floatValue,
                                    localLeftPx = hostLocal.windowLeftPx,
                                    localTopPx = hostLocal.windowTopPx,
                                    localRightPx = hostLocal.windowRightPx,
                                    localBottomPx = hostLocal.windowBottomPx,
                                )
                            if (dest != null) {
                                val left = dest.leftPx
                                val top = dest.topPx
                                val right = dest.leftPx + dest.widthPx
                                val bottom = dest.topPx + dest.heightPx
                                paintedCardLeftState.floatValue = left
                                paintedCardTopState.floatValue = top
                                paintedCardRightState.floatValue = right
                                paintedCardBottomState.floatValue = bottom
                                val owned =
                                    statusBarOwnedPaintedRectOrNull(
                                        ownerKey = reportOwner,
                                        contextToken = hostLocal.contextToken,
                                        leftPx = left,
                                        topPx = top,
                                        rightPx = right,
                                        bottomPx = bottom,
                                    )
                                if (owned != null) {
                                    livePaintedOwnedRect = owned
                                    // Rest: selected-rest; KHONG ghi morph-frozen.
                                    if (atRest) {
                                        selectedRestOwnedRect = owned
                                    }
                                    val frozenValid =
                                        morphFrozenOwnedRect?.isValidAbovePill(1, 1) == true
                                    val writePolicy =
                                        statusBarMorphFrozenPaintedWritePolicy(
                                            morphTransitionRunning = running,
                                            morphExpandedAtRest = atRest,
                                            morphFrozenValid = frozenValid,
                                        )
                                    if (
                                        writePolicy ==
                                            StatusBarMorphFrozenPaintedWrite.Capture
                                    ) {
                                        morphFrozenOwnedRect = owned
                                        frozenPaintedWState.intValue = owned.widthInt
                                        frozenPaintedHState.intValue = owned.heightInt
                                        frozenPaintedLeftState.floatValue = left
                                        frozenPaintedTopState.floatValue = top
                                        frozenPaintedRightState.floatValue = right
                                        frozenPaintedBottomState.floatValue = bottom
                                    }
                                }
                            }
                        }
                    } else if (w > 0 && h > 0 && atRest) {
                        // Settle skip LRTB: cap nhat W/H atomic cung owner.
                        val prev = selectedRestOwnedRect
                        if (prev != null && prev.ownerKey == reportOwner) {
                            selectedRestOwnedRect =
                                prev.copy(
                                    contextToken =
                                        if (hostLocal.contextToken != 0) {
                                            hostLocal.contextToken
                                        } else {
                                            prev.contextToken
                                        },
                                    rightPx = prev.leftPx + w.toFloat(),
                                    bottomPx = prev.topPx + h.toFloat(),
                                )
                        }
                    }
                }
                // Chua ready / drag / reject: defer; KHONG freeze moving host.
            }
        }
    // Hold overlay host sau settle: dummy chrome=1 den live OnDraw ack (task-043).
    // Controller tach timer khoi settle effect (pillSuppressed key cancel delay — bug 028).
    val collapseHandbackCtrl = remember { CollapseHandbackHoldController() }
    var collapseHandbackHold by remember { mutableStateOf(false) }
    var collapseHandbackTimerGen by remember { mutableIntStateOf(0) }
    var collapseLiveDrawnAcked by remember { mutableStateOf(false) }
    // Shared progress Chip (staged/complementary) — runtime feed, khong chi test.
    val collapseHandbackProgress by
        viewModel.statusBarExpansion.collapseHandbackProgress.collectAsStateWithLifecycle()
    val collapseHandoffSnap by
        viewModel.statusBarExpansion.collapseHandoffSnapshot.collectAsStateWithLifecycle()
    val collapseChromeFactor =
        statusBarMorphCollapseOverlayChromeFactor(
            collapseHandbackHold = collapseHandbackHold,
            pillSuppressed = pillSuppressed,
            liveDrawnAcked = collapseLiveDrawnAcked,
        )
    val hasValidPillRect = capturedPill.width > 0 && capturedPill.height > 0
    // Terminal / missing same-owner geometry: khong mount host ve fabricated (0, topPad).
    val hostChipState = chipState
    val ownerEndedForMorphHost =
        statusBarCollapseHandoffOwnerEndedAuthoritative(
            chipMissing = hostChipState == null,
            onlyAospOrEmpty =
                hostChipState == null ||
                    hostChipState.allEvents.isEmpty() ||
                    hostChipState.allEvents.all { it is IslandEvent.AospChip },
        )
    val liveDestOwnerForMorphHost =
        statusBarCollapseHandoffLiveSelectedOwnerId(
            alertEventId = hostChipState?.notificationAlert?.id,
            primaryEventId = hostChipState?.event?.id,
        )
    val collapseDestOwnerId =
        statusBarMorphCollapseDestinationOwnerId(
            ownerEndedAuthoritative = ownerEndedForMorphHost,
            liveSelectedOwnerId = liveDestOwnerForMorphHost,
            frozenOwnerId = frozenPresentation?.ownerId,
        )
    // Expand van dung hasValidPillRect; collapse chi capture same-owner.
    val captureMatchesDest =
        statusBarMorphCaptureMatchesDestination(
            hasValidCapture = hasValidPillRect,
            captureOwnerId = capturedPillOwnerId,
            destinationOwnerId = collapseDestOwnerId,
        )
    val collapseCaptureOk =
        if (isExpanded) hasValidPillRect else captureMatchesDest
    val terminalNoDest =
        statusBarMorphShouldTerminalDismissNoDestination(ownerEndedForMorphHost)
    // Frozen same-owner: production dung collapseCaptureOk (capture owner == dest).
    // Morph A + dest B → abort (cam reuse rect A / fabricated 0,topPad); cho B do.
    val abortCollapseVisual =
        statusBarMorphShouldAbortCollapseVisual(
            ownerEndedAuthoritative = ownerEndedForMorphHost,
            hasValidPillCapture = collapseCaptureOk,
            collapsing = !isExpanded,
            hasLiveDestinationOwner = !collapseDestOwnerId.isNullOrEmpty(),
            hasFrozenGeometryForLiveOwner = collapseCaptureOk,
        )
    val showMorphHost =
        !terminalNoDest &&
            !abortCollapseVisual &&
            (pillSuppressed || isExpanded || isMorphing || collapseHandbackHold)
    val expandCornerTarget = dimensionResource(R.dimen.notification_corner_radius)
    // Presentation freeze (expand) — fallback live chip neu chua freeze (edge).
    // Cung owner/timebase: uu tien live chip event (timer/media title) — khong snapshot stale.
    // Geometry/style van tu frozen khi co; content event live same id.
    val morphPresentation =
        run {
            val frozen = frozenPresentation
            val liveSt = chipState
            val liveDisplay = liveSt?.let { it.notificationAlert ?: it.event }
            when {
                frozen != null &&
                    liveSt != null &&
                    liveDisplay != null &&
                    liveDisplay.id == frozen.ownerId ->
                    statusBarPillPresentationOf(
                        event = liveDisplay,
                        isAlert = liveSt.notificationAlert != null,
                        chipStyle = chipStyle,
                        generation = frozen.generation,
                        carrierName = null,
                    )
                frozen != null -> frozen
                liveSt != null -> {
                    val display = liveSt.notificationAlert ?: liveSt.event
                    statusBarPillPresentationOf(
                        event = display,
                        isAlert = liveSt.notificationAlert != null,
                        chipStyle = chipStyle,
                        generation = layoutGen,
                        carrierName = null,
                    )
                }
                else -> null
            }
        }
    // Shell p=0: Row = accent glass; Circle = RING+icon (cung useCircleStyle chip).
    val pillEvent = morphPresentation?.event
    val pillAccent: Color = pillEvent?.let { chipAccentColorFor(it) } ?: CardBg
    val pillContentColor: Color =
        pillEvent?.let { chipContentColorFor(it) } ?: Color.White
    val useCircleStyle = morphPresentation?.useCircleStyle == true
    // Progress freeze trong snapshot — tranh morph tu chay media ring rieng.
    // Row/Circle cung ownership: progress/ring live trong Collapse (khong freeze vo han).
    val circleProgressFrozen =
        if (useCircleStyle) morphPresentation?.progress else null

    // Owner on dinh: expand-only id hoac pinned event — doi → invalidate H khoa.
    val contentOwnerKey =
        expandOnlyEventId?.let { "event:$it" }
            ?: chipState?.event?.id?.let { "event:$it" }
            ?: "stack"
    // Sync holder truoc painted report (stale owner reject).
    activePaintedOwnerKeyHolder[0] = contentOwnerKey
    // Lookup natural theo active measure-context (task-032) — KHONG clear async orientation.

    // Drive target SAU first frame (+ uu tien source rect) — first/subsequent/reverse OK.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        morphFirstFramePassed = true
    }
    // Premasure: shell giu pill, do natural unbounded; timeout chi freeze last envelope.
    // CAM set allowFallbackWithoutEndpoint rong (start H=pill → snap late F48).
    LaunchedEffect(isExpanded, hasValidPillRect, morphFirstFramePassed, layoutGen) {
        if (!isExpanded) {
            morphAllowFallbackRect = false
            morphTransitionState.targetState = false
            if (!pillSuppressed) onExpandedContentBottomPx(0)
            return@LaunchedEffect
        }
        hadMorphExpand = true
        // Cho capture that — KHONG bat center-fallback sau 1 frame (reopen OGP im
        // se mat rect neu settle clear; giu wait thay vi morph tu giua man).
        if (!(morphFirstFramePassed && hasValidPillRect)) {
            morphAllowFallbackRect = false
            return@LaunchedEffect
        }
        // Stage 1: cho natural measure (RemoteViews).
        repeat(MorphEndpointStageFrames) { withFrameNanos { } }
        val pillHApprox = capturedPill.height.coerceAtLeast(1)
        val afterStage =
            statusBarMorphStageTimeoutEndpointPx(
                measuredOrFrozenPx = morphRunTargetHPx,
                lastExpandedEnvelopePx = lastExpandedEnvelopeHPx,
                pillPx = pillHApprox,
                timedOut = true,
            )
        if (
            !statusBarMorphEndpointReady(morphRunTargetHPx, pillHApprox) &&
                statusBarMorphEndpointReady(afterStage, pillHApprox)
        ) {
            // Timeout: freeze last envelope atomic (W = locked; 0 → envelope luc compose).
            val snap =
                statusBarMorphAtomicEndpointSnapshot(
                    heightPx = afterStage,
                    widthPx = lockedTargetWidthPx,
                    pillHPx = pillHApprox,
                    pillWPx = capturedPill.width.coerceAtLeast(1),
                    fallbackWidthPx = lockedTargetWidthPx,
                )
            if (snap != null) {
                morphRunTargetHPx = snap.first
                morphRunTargetWPx = snap.second
            } else {
                morphRunTargetHPx = afterStage
            }
        }
        // Stage 2: van thieu → cho them; het hard timeout van KHONG empty-start.
        if (!statusBarMorphEndpointReady(morphRunTargetHPx, pillHApprox)) {
            repeat(MorphEndpointHardTimeoutFrames - MorphEndpointStageFrames) {
                withFrameNanos { }
            }
            val afterHard =
                statusBarMorphStageTimeoutEndpointPx(
                    measuredOrFrozenPx = morphRunTargetHPx,
                    lastExpandedEnvelopePx = lastExpandedEnvelopeHPx,
                    pillPx = pillHApprox,
                    timedOut = true,
                )
            if (statusBarMorphEndpointReady(afterHard, pillHApprox)) {
                morphRunTargetHPx = afterHard
            }
            // Con 0: giu shell pill + pillSuppressed den khi lockNaturalSize freeze that.
        }
    }
    // Chi drive expand khi morphRunTarget da freeze (H > pill) — khong dung locked thieu freeze.
    // Pure morph: bat START ngay khi endpoint gates pass — KHONG prelude/landing/spring.
    LaunchedEffect(
        isExpanded,
        hasValidPillRect,
        morphFirstFramePassed,
        morphAllowFallbackRect,
        morphRunTargetHPx,
        lockedTargetHeight.heightPx,
        lockedTargetWidthPx,
        layoutGen,
    ) {
        if (!isExpanded) return@LaunchedEffect
        val pillHGate = capturedPill.height.coerceAtLeast(1)
        // Locked natural san ma chua freeze → chot atomic truoc khi bat motion.
        if (
            !statusBarMorphEndpointReady(morphRunTargetHPx, pillHGate) &&
                statusBarMorphEndpointReady(lockedTargetHeight.heightPx, pillHGate)
        ) {
            val snap =
                statusBarMorphAtomicEndpointSnapshot(
                    heightPx = lockedTargetHeight.heightPx,
                    widthPx = lockedTargetWidthPx,
                    pillHPx = pillHGate,
                    pillWPx = capturedPill.width.coerceAtLeast(1),
                    fallbackWidthPx = lockedTargetWidthPx,
                )
            if (snap != null) {
                morphRunTargetHPx = snap.first
                morphRunTargetWPx = snap.second
            } else {
                morphRunTargetHPx = lockedTargetHeight.heightPx
            }
        }
        val hasEndpoint = statusBarMorphEndpointReady(morphRunTargetHPx, pillHGate)
        morphTransitionState.targetState =
            statusBarMorphShouldTargetExpanded(
                intentExpanded = true,
                firstCompositionPassed = morphFirstFramePassed,
                hasValidSourceRect = hasValidPillRect,
                allowFallbackWithoutRect = morphAllowFallbackRect,
                hasMorphEndpoint = hasEndpoint,
                allowFallbackWithoutEndpoint = false,
            )
    }

    // Collapse START → beginPrepare; morphAtRest → mark Ready (cung session, khong reset).
    // Reverse/expand → cancel. Khong clearCollapseHandbackSignals sau khi da begin (tranh cancel).
    LaunchedEffect(
        morphTransition.currentState,
        morphTransition.targetState,
        morphRunning,
        morphProgress,
        isExpanded,
        pillSuppressed,
        layoutGen,
    ) {
        if (isExpanded) {
            collapseHandbackCtrl.onExpandedOrReverse()
            collapseHandbackHold = false
            collapseLiveDrawnAcked = false
            collapseHandbackTimerGen = 0
            // Dong lifecycle dang active (tranh orphan khi layoutGen doi).
            if (collapsePerfTrace.isActive) {
                collapsePerfTrace.forceCancel("expandedOrReverse")
            }
            viewModel.statusBarExpansion.abortCollapseHandbackStatic()
            return@LaunchedEffect
        }
        // Collapse: snap dragY ve 0 (khong giu glass offset sau morph).
        stackDragYState.floatValue = 0f
        stackDragSettlingState.value = false
        stackDragPhaseState.value = StackExpandedDragPhase.Idle
        if (!pillSuppressed) {
            collapsePerfTrace.onClosingEnded(layoutGen)
            return@LaunchedEffect
        }
        // Destination owner: live uu tien; khong stale frozen owner da dismiss.
        // Snapshot delegated chipState — smart-cast collectAsState khong tin cay.
        val currentChipState = chipState
        val ownerEndedNow =
            statusBarCollapseHandoffOwnerEndedAuthoritative(
                chipMissing = currentChipState == null,
                onlyAospOrEmpty =
                    currentChipState == null ||
                        currentChipState.allEvents.isEmpty() ||
                        currentChipState.allEvents.all { it is IslandEvent.AospChip },
            )
        if (statusBarMorphShouldTerminalDismissNoDestination(ownerEndedNow)) {
            collapseHandbackCtrl.onExpandedOrReverse()
            collapseHandbackHold = false
            collapseLiveDrawnAcked = false
            collapseHandbackTimerGen = 0
            viewModel.statusBarExpansion.collapseTerminalNoDestination()
            return@LaunchedEffect
        }
        val ownerId =
            statusBarMorphCollapseDestinationOwnerId(
                ownerEndedAuthoritative = false,
                liveSelectedOwnerId =
                    statusBarCollapseHandoffLiveSelectedOwnerId(
                        alertEventId = currentChipState?.notificationAlert?.id,
                        primaryEventId = currentChipState?.event?.id,
                    ),
                frozenOwnerId = frozenPresentation?.ownerId,
            )
        val morphAtRest =
            !morphTransition.currentState &&
                !morphTransition.targetState &&
                !morphRunning &&
                abs(morphProgress) <= 0.001f
        // Geometry span: end khi transition settle (khong doi progress-only threshold).
        if (morphGeometrySettled && collapsePerfTrace.isGeometryActive) {
            collapsePerfTrace.onGeometrySettled(layoutGen)
        }
        // START: bat session som (real prepare khi con visually hidden + data live).
        // Chua arm transfer timer — morph ~600ms; arm @Ready tranh timeout som.
        if (!morphAtRest) {
            viewModel.statusBarExpansion.beginCollapseHandoffSession(
                layoutGeneration = layoutGen,
                expectedOwnerId = ownerId,
            )
            return@LaunchedEffect
        }
        // SETTLE: cung session → Ready; unsuppress composition (real alpha0 den Transfer).
        val start =
            collapseHandbackCtrl.beginSettleHandback(
                layoutGen = layoutGen,
                ownerId = ownerId,
            )
        if (!start.shouldNotifySettle) return@LaunchedEffect
        // Endpoint da danh trong onGeometrySettled; handoff moc rieng sau geometry.
        if (!collapsePerfTrace.isGeometryActive) {
            // Dam bao endpoint marker neu geometry da end som / duplicate settle.
            collapsePerfTrace.onEndpointReached(layoutGen)
        }
        val sessionGen =
            viewModel.statusBarExpansion.beginCollapseHandoffSession(
                layoutGeneration = layoutGen,
                expectedOwnerId = ownerId,
            )
        // START co the begin voi owner null — retarget selected-current @settle.
        if (ownerId != null) {
            viewModel.statusBarExpansion.retargetCollapseHandoffOwner(sessionGen, ownerId)
        }
        viewModel.statusBarExpansion.markCollapseHandoffSettled(sessionGen)
        collapsePerfTrace.onHandoffReady(layoutGen)
        collapseHandbackHold = true
        collapseLiveDrawnAcked = false
        collapseHandbackTimerGen = sessionGen
        viewModel.statusBarExpansion.notifyCollapseSettled(layoutGen)
        hadMorphExpand = false
        lockedTargetHeight = LockedTargetHeight()
        lockedTargetWidthPx = 0
        lastExpandedEnvelopeHPx = 0
        lastStackHostEnvelopeHPx = 0
        lastStackHostEnvelopeWPx = 0
        cardNaturalByOwner.clear()
        activeNaturalMeasureContextToken[0] = 0
        morphRunTargetHPx = 0
        morphRunTargetWPx = 0
        paintedCardWState.intValue = 0
        paintedCardHState.intValue = 0
        paintedCardLeftState.floatValue = 0f
        paintedCardTopState.floatValue = 0f
        paintedCardRightState.floatValue = 0f
        paintedCardBottomState.floatValue = 0f
        frozenPaintedWState.intValue = 0
        frozenPaintedHState.intValue = 0
        frozenPaintedLeftState.floatValue = 0f
        frozenPaintedTopState.floatValue = 0f
        frozenPaintedRightState.floatValue = 0f
        frozenPaintedBottomState.floatValue = 0f
        morphFrozenOwnedRect = null
        selectedRestOwnedRect = null
        livePaintedOwnedRect = null
        wasMorphExpandedAtRest = false
        lastPaintedRectProvenance = StatusBarPaintedRectProvenance.None
        targetHostPlacementReady.value = false
        onExpandedContentBottomPx(0)
    }

    // Live selected-owner / owner-end — rememberUpdatedState + snapshotFlow (B2).
    // Tach khoi morphPresentation/frozen (co the prefer frozen khi live lech).
    // chipState la delegated property — capture local moi composition (khong remember {})
    // de smart-cast / cung snapshot cho owner + owner-ended.
    val chipStateNow = chipState
    val liveSelectedOwnerState =
        rememberUpdatedState(
            statusBarCollapseHandoffLiveSelectedOwnerId(
                alertEventId = chipStateNow?.notificationAlert?.id,
                primaryEventId = chipStateNow?.event?.id,
            )
        )
    val ownerEndedState =
        rememberUpdatedState(
            statusBarCollapseHandoffOwnerEndedAuthoritative(
                chipMissing = chipStateNow == null,
                onlyAospOrEmpty =
                    chipStateNow == null ||
                        chipStateNow.allEvents.isEmpty() ||
                        chipStateNow.allEvents.all { it is IslandEvent.AospChip },
            )
        )
    val layoutGenState = rememberUpdatedState(layoutGen)
    val handbackTimerGenState = rememberUpdatedState(collapseHandbackTimerGen)

    // Mot cancellable coordinator: observe StateFlow reactive den transfer / abort / session doi.
    // Soft+recovery timeout KHONG die — delayed prepared (1s/5s) van resume (B1).
    // Key sessionGen / isExpanded — KHONG pillSuppressed (tranh cancel mid-hold).
    LaunchedEffect(collapseHandbackTimerGen, isExpanded) {
        if (collapseHandbackTimerGen == 0) return@LaunchedEffect
        if (isExpanded) {
            collapseHandbackCtrl.onExpandedOrReverse()
            collapseHandbackHold = false
            collapseLiveDrawnAcked = false
            viewModel.statusBarExpansion.abortCollapseHandbackStatic()
            return@LaunchedEffect
        }
        val sessionGen = collapseHandbackTimerGen
        val expansion = viewModel.statusBarExpansion
        val handoffFlow = expansion.collapseHandoffSnapshot
        val hideDelay = statusBarOverlayHideDelayMs(alertExitGrace = false)

        fun clearLocalHoldsIfSameSession() {
            if (
                statusBarCollapseHandoffLocalHoldReleaseAllowed(
                    activeSessionGen = handbackTimerGenState.value,
                    completingSessionGen = sessionGen,
                )
            ) {
                collapseHandbackCtrl.onExpandedOrReverse()
                collapseHandbackHold = false
                collapseLiveDrawnAcked = false
            }
        }

        suspend fun retargetLiveOwnerIfNeeded() {
            val snap = handoffFlow.value
            if (snap.sessionGeneration != sessionGen) return
            if (ownerEndedState.value) return
            val liveOwner = liveSelectedOwnerState.value
            if (liveOwner != null && liveOwner != snap.expectedOwnerId) {
                expansion.retargetCollapseHandoffOwner(sessionGen, liveOwner)
            }
        }

        // Doi settle Ready (endpoint-ready) — chua Ready: giu LIVE dummy, khong transfer som.
        withTimeoutOrNull(hideDelay + StatusBarHandbackReadinessFailsafeMs) {
            handoffFlow.first { snap ->
                snap.sessionGeneration != sessionGen ||
                    snap.phase == StatusBarCollapseHandoffPhase.Idle ||
                    (snap.sessionGeneration == sessionGen &&
                        snap.phase == StatusBarCollapseHandoffPhase.Ready)
            }
        }
        var snap = handoffFlow.value
        if (snap.sessionGeneration != sessionGen ||
            snap.phase == StatusBarCollapseHandoffPhase.Idle
        ) {
            clearLocalHoldsIfSameSession()
            return@LaunchedEffect
        }
        if (snap.phase != StatusBarCollapseHandoffPhase.Ready) {
            return@LaunchedEffect
        }

        // Soft window → recovery retarget → KEEP_OBSERVING den prepared (B1).
        var prepared =
            snap.realPrepared ||
                statusBarCollapseHandoffAwaitPreparedReactive(
                    handoffFlow = handoffFlow,
                    sessionGen = sessionGen,
                    softTimeoutMs = hideDelay,
                    recoveryTimeoutMs = 0L,
                    afterRecoveryKeepObserving = false,
                )
        snap = handoffFlow.value
        if (snap.sessionGeneration != sessionGen ||
            snap.phase == StatusBarCollapseHandoffPhase.Idle
        ) {
            clearLocalHoldsIfSameSession()
            return@LaunchedEffect
        }
        if (!prepared && !snap.realPrepared) {
            retargetLiveOwnerIfNeeded()
            prepared =
                statusBarCollapseHandoffAwaitPreparedReactive(
                    handoffFlow = handoffFlow,
                    sessionGen = sessionGen,
                    softTimeoutMs = StatusBarHandbackReadinessFailsafeMs,
                    recoveryTimeoutMs = 0L,
                    afterRecoveryKeepObserving = false,
                )
            snap = handoffFlow.value
            if (snap.sessionGeneration != sessionGen ||
                snap.phase == StatusBarCollapseHandoffPhase.Idle
            ) {
                clearLocalHoldsIfSameSession()
                return@LaunchedEffect
            }
        }
        if (!prepared && !snap.realPrepared) {
            // Sau recovery: KHONG return — observe mai + owner signals (B1/B2).
            while (true) {
                snap = handoffFlow.value
                if (snap.sessionGeneration != sessionGen ||
                    snap.phase == StatusBarCollapseHandoffPhase.Idle
                ) {
                    clearLocalHoldsIfSameSession()
                    return@LaunchedEffect
                }
                val after =
                    statusBarCollapseHandoffAfterRecoveryTimeout(
                        prepared = snap.realPrepared,
                        ownerEndedAuthoritative = ownerEndedState.value,
                    )
                when (after) {
                    StatusBarCollapseHandoffAfterRecoveryAction.AbortClearHold -> {
                        expansion.abortCollapseHandbackStatic()
                        clearLocalHoldsIfSameSession()
                        return@LaunchedEffect
                    }
                    StatusBarCollapseHandoffAfterRecoveryAction.TransferAttempt -> {
                        prepared = true
                        break
                    }
                    StatusBarCollapseHandoffAfterRecoveryAction.KeepObserving -> {
                        retargetLiveOwnerIfNeeded()
                        // Chot baseline SAU retarget — tranh wake lien tuc (busy loop).
                        snap = handoffFlow.value
                        val beforeOwner = liveSelectedOwnerState.value
                        val beforeEnded = ownerEndedState.value
                        val beforeSnap = snap
                        // Race: StateFlow emit HOAC live owner/end (snapshotFlow) — khong poll.
                        combine(
                                handoffFlow,
                                snapshotFlow {
                                    liveSelectedOwnerState.value to ownerEndedState.value
                                },
                            ) { s, oe ->
                                Triple(s, oe.first, oe.second)
                            }
                            .first { (s, o, e) ->
                                s != beforeSnap || o != beforeOwner || e != beforeEnded
                            }
                    }
                }
            }
        }

        snap = handoffFlow.value
        if (snap.sessionGeneration != sessionGen ||
            snap.phase == StatusBarCollapseHandoffPhase.Idle
        ) {
            clearLocalHoldsIfSameSession()
            return@LaunchedEffect
        }
        if (ownerEndedState.value) {
            expansion.abortCollapseHandbackStatic()
            clearLocalHoldsIfSameSession()
            return@LaunchedEffect
        }
        retargetLiveOwnerIfNeeded()
        snap = handoffFlow.value

        if (!snap.transferred) {
            if (snap.realPrepared || prepared) {
                // P1B: live owner + layoutGen + readiness record (size/attach).
                // Expansion lay size/attach tu record sync; detach revoke ngay → reject.
                val liveOwner = liveSelectedOwnerState.value
                val ok =
                    expansion.transferCollapseHandoff(
                        sessionGeneration = sessionGen,
                        currentSelectedOwnerId = liveOwner,
                        currentLayoutGen = layoutGenState.value,
                        chipExists = !ownerEndedState.value,
                    )
                if (!ok) {
                    // Stale live inputs — giu dummy, observe tiep (khong fabricate).
                    while (true) {
                        val before = handoffFlow.value
                        val beforeOwner = liveSelectedOwnerState.value
                        val beforeEnded = ownerEndedState.value
                        combine(
                                handoffFlow,
                                snapshotFlow {
                                    liveSelectedOwnerState.value to ownerEndedState.value
                                },
                            ) { s, oe ->
                                Triple(s, oe.first, oe.second)
                            }
                            .first { (s, o, e) ->
                                s != before || o != beforeOwner || e != beforeEnded
                            }
                        if (ownerEndedState.value) {
                            expansion.abortCollapseHandbackStatic()
                            clearLocalHoldsIfSameSession()
                            return@LaunchedEffect
                        }
                        retargetLiveOwnerIfNeeded()
                        val now = handoffFlow.value
                        if (now.sessionGeneration != sessionGen ||
                            now.phase == StatusBarCollapseHandoffPhase.Idle
                        ) {
                            clearLocalHoldsIfSameSession()
                            return@LaunchedEffect
                        }
                        if (now.realPrepared) {
                            val retry =
                                expansion.transferCollapseHandoff(
                                    sessionGeneration = sessionGen,
                                    currentSelectedOwnerId = liveSelectedOwnerState.value,
                                    currentLayoutGen = layoutGenState.value,
                                    chipExists = !ownerEndedState.value,
                                )
                            if (retry) break
                        }
                    }
                }
            } else {
                // Khong the toi day neu KeepObserving dung — phong thu.
                return@LaunchedEffect
            }
        }
        snap = handoffFlow.value
        if (snap.sessionGeneration != sessionGen || !snap.transferred) return@LaunchedEffect
        collapseLiveDrawnAcked = true
        withFrameNanos { }
        // Cleanup chi khi van cung session (B4 — stale khong clear hold moi).
        if (
            statusBarCollapseHandoffLocalHoldReleaseAllowed(
                activeSessionGen = handbackTimerGenState.value,
                completingSessionGen = sessionGen,
            )
        ) {
            expansion.reportCollapseHandbackOverlayHidden(
                holdGeneration = sessionGen,
                layoutGeneration = snap.layoutGeneration,
            )
            withFrameNanos { }
            clearLocalHoldsIfSameSession()
        }
    }

    LaunchedEffect(showNotif) {
        notifVisible.targetState = showNotif
        if (!showNotif) onAlertContentBoundsPx(0, 0, 0, 0)
    }

    LaunchedEffect(chipState) {
        val currentChipState = chipState
        val filtered = currentChipState?.allEvents?.filter { it !is IslandEvent.AospChip }
        if (filtered.isNullOrEmpty() && isExpanded) {
            // Terminal no-destination — khong ordinary morph toi fabricated origin.
            viewModel.statusBarExpansion.collapseTerminalNoDestination()
        }
    }

    val origin = TransformOrigin(0.5f, 0f)
    val motionScheme = MaterialTheme.motionScheme
    val localView = LocalView.current
    openGlassHostViewHolder[0] = localView
    openGlassLiveExpandedHolder[0] = isExpanded && morphExpandedAtRest
    val hostLocOnScreen = remember { IntArray(2) }

    // Blur per-card (PrimaryCard/Media/Timer) — khong host glass uniform.
    val expandedBlurAlpha by animateFloatAsState(
        targetValue = if (showMorphHost) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "expanded_blur_alpha",
    )
    val alertBlurAlpha by animateFloatAsState(
        targetValue = if (showNotif) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "alert_blur_alpha",
    )

    CompositionLocalProvider(LocalBlurAlpha provides expandedBlurAlpha) {
        if (showMorphHost) {
            val onBottom = rememberUpdatedState(onExpandedContentBottomPx)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.statusBarExpansion.collapse() },
                        ),
                contentAlignment = Alignment.TopCenter,
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val maxWPx = constraints.maxWidth
                    // Envelope = StackedExpandedPanels widthIn(max=ExpandedMaxWidth).
                    // SpaceLg*2 nam BEN TRONG stack — host van full envelope; shell tru inset.
                    val envelopeWDp = maxWidth.coerceAtMost(ExpandedMaxWidth)
                    val envelopeWPx = with(density) { envelopeWDp.roundToPx() }
                    // Int dpi config — tach ten khoi Float morphMotionDensityDpi (cung scope).
                    val naturalMeasureDensityDpi = LocalConfiguration.current.densityDpi
                    // Measure-context: envelopeW + density + fontScale (H co the doi).
                    // Card W != envelope (padding) — validate bang actual measurement.
                    val measureCtxToken =
                        statusBarCardNaturalMeasureContextToken(
                            availableWidthPx = envelopeWPx,
                            densityDpi = naturalMeasureDensityDpi,
                            fontScale = density.fontScale,
                        )
                    // Sync truoc layout — SideEffect muon: place bi reject token cu.
                    activeNaturalMeasureContextToken[0] = measureCtxToken
                    val topPadPx = with(density) { topPad.toPx() }
                    val pillWPx =
                        when {
                            capturedPill.width > 0 -> capturedPill.width
                            else -> with(density) { StatusBarPillWidthFallback.roundToPx() }
                        }
                    // Fallback an toan = ChipTierPassiveHeight — KHONG gia statusBarH=79.
                    val pillHPx =
                        when {
                            capturedPill.height > 0 -> capturedPill.height
                            else -> with(density) { ChipTierPassiveHeight.roundToPx() }
                        }
                    // Natural owner hien tai — chi hop le dung active measure context.
                    val cachedOwnerCardNaturalHPx =
                        statusBarLookupCardNaturalHeightPx(
                            cache = cardNaturalByOwner,
                            ownerKey = contentOwnerKey,
                            activeContextToken = measureCtxToken,
                        )
                    // Owner switch: cache natural owner moi truoc — KHONG bridge H under cu.
                    val cachedOwnerHostHPx =
                        statusBarStackHostHeightFromCardNaturalPx(
                            cardNaturalHPx = cachedOwnerCardNaturalHPx,
                            topPadPx = with(density) { StatusBarExpandedTopPadDp.dp.roundToPx() },
                            bottomPadPx =
                                with(density) { StatusBarExpandedBottomPadDp.dp.roundToPx() },
                        )
                    val liveMorphTargetHPx =
                        statusBarMorphTargetHeightPx(
                            lockedTargetHeight,
                            contentOwnerKey,
                            lastExpandedEnvelopeHPx,
                            cachedOwnerNaturalHPx = cachedOwnerHostHPx,
                        )
                    // Freeze endpoint 1 morph run; stage=pill khi chua do; at-rest follow.
                    val morphTargetHPx =
                        statusBarMorphRunEndpointPx(
                            frozenEndpointPx = morphRunTargetHPx,
                            liveTargetPx = liveMorphTargetHPx,
                            pillPx = pillHPx,
                            morphTransitionRunning = morphRunning,
                            morphExpandedAtRest = morphExpandedAtRest,
                        )
                    // Chua tung measure: giu targetH = pillH (sx/sy~1) — khong 220.dp pop.
                    val targetHPx =
                        (if (morphTargetHPx > 0) morphTargetHPx else pillHPx)
                            .coerceAtLeast(pillHPx)
                    // W khoa chi khi dung owner; owner doi → envelope (khong stale W).
                    val ownerMatched =
                        lockedTargetHeight.owner == contentOwnerKey &&
                            lockedTargetHeight.heightPx > 0
                    val liveTargetWPx =
                        when {
                            ownerMatched && lockedTargetWidthPx > 0 -> lockedTargetWidthPx
                            else -> envelopeWPx
                        }.coerceAtLeast(pillWPx)
                    val morphTargetWPx =
                        statusBarMorphRunEndpointPx(
                            frozenEndpointPx = morphRunTargetWPx,
                            liveTargetPx = liveTargetWPx,
                            pillPx = pillWPx,
                            morphTransitionRunning = morphRunning,
                            morphExpandedAtRest = morphExpandedAtRest,
                        )
                    val targetWPx =
                        (if (morphTargetWPx > 0) morphTargetWPx else liveTargetWPx)
                            .coerceAtLeast(pillWPx)
                    // (A) layout envelope = measure + padding (touch/host requiredSize).
                    // (B) morphVisual = card painted: −SideLg*2 / −TopXxs / −bottom50.
                    val layoutTargetHPx = targetHPx
                    val layoutTargetWPx = targetWPx
                    val sidePadFallbackPx =
                        with(density) { StatusBarExpandedSidePadDp.dp.roundToPx() }
                    val topInsetPx =
                        with(density) { StatusBarExpandedTopPadDp.dp.roundToPx() }
                    val bottomPadPx =
                        with(density) { StatusBarExpandedBottomPadDp.dp.roundToPx() }
                    // Natural wrapper cache van dung bridge H/W layout (khac painted card).
                    // Cam seed vao selectedRest / open-glass (task-010 flash 990→1012→990).
                    // SSOT measured dest: selected-rest exact → live-painted exact.
                    // First atRest (selectedRest store null) dung live painted — khong natural.
                    val measuredSelectedDest =
                        statusBarResolveMeasuredSelectedDestination(
                            selectedRestOwned = selectedRestOwnedRect,
                            livePaintedOwned = livePaintedOwnedRect,
                            selectedOwnerKey = contentOwnerKey,
                            activeContextToken = measureCtxToken,
                            pillWPx = pillWPx,
                            pillHPx = pillHPx,
                        )
                    val selectedRestValidForRebind = measuredSelectedDest.isMeasuredReady
                    val measuredExactRest = measuredSelectedDest.measuredExact
                    // Dong sau swap: morphFrozen hieu dung = measured exact (rebind SideEffect).
                    val leaveRestRebind =
                        statusBarMorphFrozenRebindFromSelectedOnLeaveRest(
                            wasExpandedAtRest = wasMorphExpandedAtRest,
                            morphExpandedAtRest = morphExpandedAtRest,
                            selectedRestValidForOwner = selectedRestValidForRebind,
                        )
                    val effectiveMorphFrozen =
                        if (leaveRestRebind) measuredExactRest else morphFrozenOwnedRect
                    SideEffect {
                        // Cam seed natural vao selectedRestOwnedRect (doi lot exact).
                        // Chi rebind freeze tu measuredExact (painted rest/live).
                        if (leaveRestRebind) {
                            val rebind = measuredExactRest
                            if (rebind != null && selectedRestValidForRebind) {
                                morphFrozenOwnedRect = rebind
                                frozenPaintedWState.intValue = rebind.widthInt
                                frozenPaintedHState.intValue = rebind.heightInt
                                frozenPaintedLeftState.floatValue = rebind.leftPx
                                frozenPaintedTopState.floatValue = rebind.topPx
                                frozenPaintedRightState.floatValue = rebind.rightPx
                                frozenPaintedBottomState.floatValue = rebind.bottomPx
                            }
                        }
                        wasMorphExpandedAtRest = morphExpandedAtRest
                    }
                    // Selector: store rest + live painted that — KHONG truyen natural.
                    val shellPaintedChoice =
                        statusBarSelectShellPaintedRect(
                            morphTransitionRunning = morphRunning,
                            morphExpandedAtRest = morphExpandedAtRest,
                            selectedOwnerKey = contentOwnerKey,
                            morphFrozen = effectiveMorphFrozen,
                            selectedRest = selectedRestOwnedRect,
                            livePainted = livePaintedOwnedRect,
                            pillWPx = pillWPx,
                            pillHPx = pillHPx,
                            activeContextToken = measureCtxToken,
                        )
                    SideEffect {
                        if (shellPaintedChoice.provenance != lastPaintedRectProvenance) {
                            lastPaintedRectProvenance = shellPaintedChoice.provenance
                            if (android.util.Log.isLoggable("AxDbMorph", android.util.Log.DEBUG)) {
                                android.util.Log.d(
                                    "AxDbMorph",
                                    "paintedRect prov=${shellPaintedChoice.provenance}" +
                                        " owner=${shellPaintedChoice.rect?.ownerKey}" +
                                        " ${shellPaintedChoice.rect?.widthInt}x" +
                                        "${shellPaintedChoice.rect?.heightInt}",
                                )
                            }
                            Trace.instant(
                                Trace.TRACE_TAG_VIEW,
                                "AxDbMorph/painted:${shellPaintedChoice.provenance}",
                            )
                        }
                    }
                    val selectedPaintedDest = shellPaintedChoice.rect
                    val paintedWForMorph =
                        when {
                            selectedPaintedDest != null -> selectedPaintedDest.widthInt
                            paintedCardWPx > pillWPx -> paintedCardWPx
                            else -> 0
                        }
                    val paintedHForMorph =
                        when {
                            selectedPaintedDest != null -> selectedPaintedDest.heightInt
                            paintedCardHPx > pillHPx -> paintedCardHPx
                            else -> 0
                        }
                    // Painted top-card thang SidePad 12dp (right-edge ≤1px).
                    val morphVisualTargetHPx =
                        statusBarMorphResolveVisualTargetHPx(
                            layoutTargetHPx = layoutTargetHPx,
                            topPadPx = topInsetPx,
                            bottomPadPx = bottomPadPx,
                            pillHPx = pillHPx,
                            paintedHPx = paintedHForMorph,
                        )
                    val morphVisualTargetWPx =
                        statusBarMorphResolveVisualTargetWPx(
                            layoutTargetWPx = layoutTargetWPx,
                            sidePadPx = sidePadFallbackPx,
                            pillWPx = pillWPx,
                            paintedWPx = paintedWForMorph,
                        )
                    // Inset trai: tu selected painted LEFT; else (layout−paintedW)/2.
                    val sidePadPx =
                        if (
                            selectedPaintedDest != null && layoutTargetWPx > paintedWForMorph
                        ) {
                            val hostLeftGuess =
                                (maxWPx - layoutTargetWPx) * 0.5f
                            (selectedPaintedDest.leftPx - hostLeftGuess)
                                .roundToInt()
                                .coerceIn(0, (layoutTargetWPx - paintedWForMorph).coerceAtLeast(0))
                        } else {
                            statusBarMorphResolveSideInsetPx(
                                layoutTargetWPx = layoutTargetWPx,
                                paintedWPx = paintedWForMorph,
                                fallbackSidePadPx = sidePadFallbackPx,
                            )
                        }
                    val geom =
                        computeKeyguardMorphGeom(
                            pRaw = morphProgress,
                            pillWPx = pillWPx.toFloat(),
                            pillHPx = pillHPx.toFloat(),
                            targetWPx = morphVisualTargetWPx.toFloat(),
                            targetHPx = morphVisualTargetHPx.toFloat(),
                        )
                    val sx = geom.sx.coerceAtLeast(MorphScaleFloor)
                    val sy = geom.sy.coerceAtLeast(MorphScaleFloor)
                    // Moving host sx/sy — chi undo LOCAL; targetHost tu layout rieng.
                    hostSxForPainted.floatValue = sx
                    hostSyForPainted.floatValue = sy
                    val (targetHostLeftPx, targetHostTopPx) =
                        statusBarMorphExpandedTargetHostPlacementPx(
                            maxWidthPx = maxWPx.toFloat(),
                            layoutTargetWPx = layoutTargetWPx.toFloat(),
                            topPadPx = topPadPx,
                        )
                    targetHostLeftForPainted.floatValue = targetHostLeftPx
                    targetHostTopForPainted.floatValue = targetHostTopPx
                    // Ready khi envelope/endpoint > pill — defer freeze neu chua.
                    targetHostPlacementReady.value = layoutTargetWPx > pillWPx
                    val targetWDp = with(density) { layoutTargetWPx.toDp() }
                    val targetHDp = with(density) { layoutTargetHPx.toDp() }
                    val morphVisualTargetWDp =
                        with(density) { morphVisualTargetWPx.toDp() }
                    val morphVisualTargetHDp =
                        with(density) { morphVisualTargetHPx.toDp() }
                    // Corner: clamp overshoot PathInterpolator (track 1).
                    val pillCornerPx = pillHPx * 0.5f
                    val cardCornerPx = with(density) { expandCornerTarget.toPx() }
                    val cornerP = statusBarMorphGeometryCornerProgress(geom.pCorner)
                    val visualCornerPx =
                        pillCornerPx + (cardCornerPx - pillCornerPx) * cornerP
                    val shellCorners =
                        morphLocalCornerRadii(
                            visualCornerPx = visualCornerPx,
                            sx = sx,
                            sy = sy,
                            targetHPx = morphVisualTargetHPx.toFloat(),
                            targetWPx = morphVisualTargetWPx.toFloat(),
                        )
                    val shellRxDp = with(density) { shellCorners.localRxPx.toDp() }
                    val shellRyDp = with(density) { shellCorners.localRyPx.toDp() }
                    val (visualShellWPx, visualShellHPx) =
                        statusBarMorphVisualShellSizePx(
                            targetWPx = morphVisualTargetWPx.toFloat(),
                            targetHPx = morphVisualTargetHPx.toFloat(),
                            sx = sx,
                            sy = sy,
                        )
                    // Track 2/3/4 + reverse continuity (owner sync, khong SideEffect latch).
                    // Float dpi tu Density — tach ten khoi naturalMeasureDensityDpi (Int).
                    val morphMotionDensityDpi = density.density * 160f
                    val widthGatePx =
                        statusBarMorphPanelWidthGatePx(
                            densityDpi = morphMotionDensityDpi,
                            visualTargetWPx = morphVisualTargetWPx.toFloat(),
                        )
                    // Settle collapsed: reset owner cho run sau (tranh latch/orphan cu).
                    if (!morphRunning && morphLinearClamped <= 0.01f && !morphClosing) {
                        morphContinuity.reset()
                    }
                    val morphVisual =
                        morphContinuity.evaluate(
                            expandedFraction = morphLinearClamped,
                            closing = morphClosing,
                            measuredWidthPx = visualShellWPx,
                            widthGatePx = widthGatePx,
                            visualTargetWPx = morphVisualTargetWPx.toFloat(),
                            densityDpi = morphMotionDensityDpi,
                        )
                    val expandContentAlpha = morphVisual.panelAlpha
                    val dummyContentAlpha = morphVisual.dummyAlpha
                    val dummyContentScale = morphVisual.dummyScale
                    val morphShellAlpha = morphVisual.shellGlass
                    // Track 4 color: ARGB exact (Decelerate500 / collapseColor600).
                    // Endpoint = AxSystemFrost resource alpha — KHONG ep AlphaGlass 0.72.
                    val tintProgress = morphVisual.colorT
                    val frostCtx = localView.context
                    val frostFallback = Color(AxSystemFrost.fallbackColor(frostCtx))
                    val frostTint = Color(AxSystemFrost.tintColor(frostCtx))
                    // Blur-on: surfaceBrightTint; blur-off: materialColorSurfaceBright.
                    val panelEndpointFallback = frostFallback
                    val panelEndpointTint =
                        if (AxSystemFrost.isFrostActive(frostCtx)) frostTint else frostFallback
                    // Pill source = live StatusBarPillVisual (accent×AlphaGlass).
                    val pillSourceTint = pillAccent.copy(alpha = AlphaGlass)
                    val shellFallback =
                        statusBarMorphShellFallbackArgb(
                            pillAccent = pillAccent,
                            panelEndpointFallback = panelEndpointFallback,
                            tintProgress = tintProgress,
                        )
                    val shellTint =
                        statusBarMorphShellTintArgb(
                            pillSourceTint = pillSourceTint,
                            panelEndpointTint = panelEndpointTint,
                            tintProgress = tintProgress,
                        )
                    // Exclusive pill↔panel (ContinuityOwner): pill→0, gap glass, panel↑.
                    // KHONG geo follow prolong — tranh overlap panel sau widthGate.
                    val pillFollowAlpha = dummyContentAlpha
                    // Circle ring vs glass: dung cornerP (khong color t).
                    val shellLerp = cornerP
                    // Chrome alpha: shell glass * handback; blur hold doc lap tint.
                    val shellChrome =
                        (morphShellAlpha * collapseChromeFactor).coerceIn(0f, 1f)
                    val (ringChromeRaw, _) =
                        statusBarMorphCircleChromeAlphas(
                            morphShellAlpha = shellChrome,
                            shellLerp = shellLerp,
                            useCircleStyle = useCircleStyle,
                        )
                    // Task-034: 1 persistent shared glass owner (p0→p1 rest→close→handback).
                    // KHONG fade shell α→0 @p1 (destroy BlurEngine / 2-blur handoff).
                    val sharedGlassOwnerPresent = true
                    // Gate: true owns (!suppress && !hold) → overlay=0 ke ca isMorphing mount.
                    // Hold + chua ACK: complementary theo Chip progress (046, khong 2×.72).
                    // 045: chi doc progress khop holdGen/layout/owner — stale → giu overlay.
                    // Ownership SSOT tu handoff phase — 1 FG + 1 glass owner.
                    val handoffOwn =
                        statusBarCollapseHandoffOwnershipOf(
                            phase = collapseHandoffSnap.phase,
                            pillSuppressed = pillSuppressed,
                            pillFollowAlpha = pillFollowAlpha,
                        )
                    val persistentGlass =
                        statusBarMorphPersistentSharedGlassAlpha(
                            sharedGlassOwnerPresent = sharedGlassOwnerPresent,
                            collapseChromeFactor = collapseChromeFactor,
                        )
                    val paintMorphGlassAlphaRaw =
                        statusBarCollapseHandoffOverlayGlassAlpha(
                            ownership = handoffOwn,
                            persistentSharedGlassAlpha = persistentGlass,
                        )
                    // task-041: expanded stack luon CardLocal (moi phase); SharedPlate
                    // chi pill↔expanded morph. Khong Idle yield / warm-floor / observe.
                    // task-043: open at-rest GIU shared den local first-draw (merge barrier).
                    val stackDragPhaseLive = stackDragPhaseState.value
                    val glassHandoffBase =
                        stackExpandedGlassHandoffDecision(
                            morphExpandedAtRest = morphExpandedAtRest,
                            phase = stackDragPhaseLive,
                            sharedGlassOwnerPresent = sharedGlassOwnerPresent,
                            basePaintAlpha = paintMorphGlassAlphaRaw,
                        )
                    // Begin/retarget/cancel/abandon open transfer — SideEffect.
                    // Continuity pending (rect chua valid) ≠ ready — khong begin 0-rect.
                    // Held/settle khi chua ACK → abandon CardLocal (khong parked shared).
                    SideEffect {
                        openGlassLiveExpandedHolder[0] = isExpanded && morphExpandedAtRest
                        if (
                            stackOpenGlassShouldCancelTransfer(
                                morphExpandedAtRest = morphExpandedAtRest,
                                isExpandedIntent = isExpanded,
                                transferPhase = openGlassCtrl.phase,
                            )
                        ) {
                            openGlassCtrl.cancel(
                                if (!isExpanded) "collapse" else "leaveRest",
                            )
                            openGlassPhase = openGlassCtrl.phase
                            openGlassDestAcked = openGlassCtrl.destinationFirstDrawAcked
                            openGlassSessionGen = openGlassCtrl.sessionGen
                        } else if (
                            stackOpenGlassShouldAbandonWaitingForStackGesture(
                                transferPhase = openGlassCtrl.phase,
                                destinationFirstDrawAcked =
                                    openGlassCtrl.destinationFirstDrawAcked,
                                stackDragPhase = stackDragPhaseLive,
                            )
                        ) {
                            if (openGlassCtrl.abandonWaitingToCardLocal("stackGesture")) {
                                openGlassPhase = openGlassCtrl.phase
                                openGlassDestAcked = openGlassCtrl.destinationFirstDrawAcked
                                openGlassSessionGen = openGlassCtrl.sessionGen
                            }
                        } else if (
                            // Chi begin @ Idle — Held/settle khong tao Waiting shared cover.
                            stackDragPhaseLive == StackExpandedDragPhase.Idle &&
                                morphExpandedAtRest &&
                                isExpanded &&
                                contentOwnerKey.isNotEmpty() &&
                                selectedRestValidForRebind
                        ) {
                            // Cung measuredExact voi shell — khong natural wrapper.
                            val rest = measuredExactRest
                            if (rest != null) {
                                val begun =
                                    openGlassCtrl.retargetOrBeginWaitingDestination(
                                        ownerKey = contentOwnerKey,
                                        contextToken = measureCtxToken,
                                        leftPx = rest.leftPx,
                                        topPx = rest.topPx,
                                        rightPx = rest.rightPx,
                                        bottomPx = rest.bottomPx,
                                    )
                                if (begun != null) {
                                    openGlassPhase = openGlassCtrl.phase
                                    openGlassDestAcked =
                                        openGlassCtrl.destinationFirstDrawAcked
                                    openGlassSessionGen = openGlassCtrl.sessionGen
                                    // Listener attach SAU begin (sessionGen>0) — surface co
                                    // the da ve frame truoc khong reporter. Invalidate host
                                    // de redraw local blur → destination ACK (drop shared).
                                    openGlassHostViewHolder[0]?.invalidate()
                                }
                            }
                        }
                    }
                    // Effective phase: frame Idle@rest chua SideEffect begin van GIU shared.
                    val openGlassPhaseEffective =
                        stackOpenGlassEffectiveTransferPhase(
                            morphExpandedAtRest = morphExpandedAtRest,
                            recordedPhase = openGlassPhase,
                            destinationFirstDrawAcked = openGlassDestAcked,
                        )
                    val glassHandoff =
                        stackOpenGlassMergeHandoff(
                            base = glassHandoffBase,
                            morphExpandedAtRest = morphExpandedAtRest,
                            transferPhase = openGlassPhaseEffective,
                            destinationFirstDrawAcked = openGlassDestAcked,
                            basePaintAlpha = paintMorphGlassAlphaRaw,
                        )
                    val useCardLocalGlass = glassHandoff.useCardLocalGlass
                    val paintMorphGlassAlpha = glassHandoff.sharedPlateAlpha
                    val keepSharedBlurSubtree = glassHandoff.keepSharedBlurSubtree
                    // Await draw chi khi sessionGen>0 (sau begin) — tranh report gen0.
                    val openAwaitDestDraw =
                        openGlassSessionGen > 0 &&
                            stackOpenGlassAwaitDestinationDraw(
                                transferPhase = openGlassPhaseEffective,
                                destinationFirstDrawAcked = openGlassDestAcked,
                            )
                    val openLocalBlurFactor =
                        stackOpenGlassLocalBlurAlphaFactor(
                            transferPhase = openGlassPhaseEffective,
                            destinationFirstDrawAcked = openGlassDestAcked,
                        )
                    // Draw-time: CardLocal → ty=0; SharedPlate (morph) → dragY.
                    // OpenWaiting: local dang attach — ty theo CardLocal (0).
                    val useCardLocalGlassState = rememberUpdatedState(useCardLocalGlass)
                    val forwardAwaitSuppress =
                        paintMorphGlassAlpha <= 0.01f && isExpanded && !pillSuppressed
                    // Dummy FG theo phase (giu den Transfer) — Circle ring cung policy.
                    val pillChromeAlpha =
                        if (forwardAwaitSuppress) 0f
                        else statusBarCollapseHandoffDummyFgAlpha(handoffOwn)
                    val ringOwn =
                        statusBarCollapseHandoffOwnershipOf(
                            phase = collapseHandoffSnap.phase,
                            pillSuppressed = pillSuppressed,
                            pillFollowAlpha = ringChromeRaw,
                        )
                    val ringChromeAlpha =
                        if (forwardAwaitSuppress) 0f
                        else statusBarCollapseHandoffDummyFgAlpha(ringOwn)
                    // Owner present: top FG-only via Local; under giu blur (LocalBlurAlpha~1).
                    // OpenWaiting (045): factor 1 — dest surface (native|fallback) truoc drop.
                    val cardBlurFactor = openLocalBlurFactor
                    // Ring ve VUONG visual (grow isotropic = min(shellW,shellH)); sibling
                    // NGOAI graphicsLayer scale — p0 = 28×28, khong ellipse targetW×targetH.
                    val ringDiameterPx =
                        statusBarMorphCircleRingVisualDiameterPx(
                            visualShellWPx,
                            visualShellHPx,
                        )
                    val ringDiameterDp = with(density) { ringDiameterPx.toDp() }

                    fun lockNaturalSize(wPx: Int, hPx: Int) {
                        // task-023: bo geometry freeze moi cua 022 (chi morphClosing&&endpoint>pill,
                        // khong audit config/owner) — giu fix hep insets coalesce.
                        // task-031/033: hPx stack = max(layers)+pads. Khoa TOP bang natural
                        // dung context; khong permanently lock max under khi cho place moi.
                        if (hPx <= pillHPx) return
                        // requiredWidth(envelope): W do phai ~ envelope (tru owner retarget).
                        if (wPx < envelopeWPx - 2 && wPx < targetWPx - 2) return
                        if (wPx > 0) lastStackHostEnvelopeWPx = wPx
                        lastStackHostEnvelopeHPx = hPx
                        val topCardNatural =
                            statusBarLookupCardNaturalHeightPx(
                                cache = cardNaturalByOwner,
                                ownerKey = contentOwnerKey,
                                activeContextToken = measureCtxToken,
                            )
                        val hasBridgeOrLocked =
                            lastExpandedEnvelopeHPx > pillHPx ||
                                lockedTargetHeight.heightPx > pillHPx
                        val measuredForLock =
                            statusBarLockHeightFromNaturalOrBridgePx(
                                topCardNaturalHPx = topCardNatural,
                                stackHostEnvelopeHPx = hPx,
                                topPadPx = topInsetPx,
                                bottomPadPx = bottomPadPx,
                                pillHPx = pillHPx,
                                hasBridgeOrLockedHeight = hasBridgeOrLocked,
                            )
                        if (measuredForLock <= pillHPx) return
                        val next =
                            resolveStatusBarLockedTargetHeight(
                                lockedTargetHeight,
                                contentOwnerKey,
                                measuredForLock,
                                pillHPx,
                            )
                        if (next != lockedTargetHeight) {
                            lockedTargetHeight = next
                            // Owner moi / follow H: chot W theo phep do natural.
                            if (wPx > 0) lockedTargetWidthPx = wPx
                        } else if (lockedTargetWidthPx == 0 && wPx > 0) {
                            lockedTargetWidthPx = wPx
                        }
                        // Bridge = TOP natural host H (khong max under) cho reopen/timeout.
                        if (next.heightPx > pillHPx) {
                            lastExpandedEnvelopeHPx = next.heightPx
                        }
                        // Freeze H+W atomic 1 lan / morph run TRUOC khi bat motion; at-rest follow.
                        // Rest swap follow live; frozen morph khong retarget (helper SSOT).
                        if (
                            statusBarShouldUpdateMorphEndpoint(
                                frozenEndpointPx = morphRunTargetHPx,
                                measuredPx = next.heightPx,
                                pillPx = pillHPx,
                                morphTransitionRunning = morphRunning && !morphExpandedAtRest,
                            )
                        ) {
                            val snap =
                                statusBarMorphAtomicEndpointSnapshot(
                                    heightPx = next.heightPx,
                                    widthPx = if (wPx > 0) wPx else lockedTargetWidthPx,
                                    pillHPx = pillHPx,
                                    pillWPx = pillWPx,
                                    fallbackWidthPx = envelopeWPx,
                                )
                            if (snap != null) {
                                morphRunTargetHPx = snap.first
                                morphRunTargetWPx = snap.second
                            } else {
                                morphRunTargetHPx = next.heightPx
                                if (wPx > 0) morphRunTargetWPx = wPx
                            }
                        }
                    }

                    // Owner/cache/envelope/context doi ma place chua den → retarget lock.
                    // resolve no-op khi bang → khong loop. Natural chi tu onPlaced layer.
                    LaunchedEffect(
                        contentOwnerKey,
                        cachedOwnerCardNaturalHPx,
                        measureCtxToken,
                        lastStackHostEnvelopeHPx,
                        lastStackHostEnvelopeWPx,
                    ) {
                        val envH = lastStackHostEnvelopeHPx
                        val envW = lastStackHostEnvelopeWPx
                        if (envH > pillHPx && envW > 0) {
                            lockNaturalSize(envW, envH)
                        }
                    }

                    // Prune stale owner / sai context — live stack keys.
                    val liveNaturalOwnerKeys =
                        remember(chipState?.allEvents, expandOnlyEventId) {
                            buildSet {
                                chipState?.allEvents?.forEach { ev ->
                                    if (ev !is IslandEvent.AospChip) {
                                        add(statusBarStackEventOwnerKey(ev.id))
                                    }
                                }
                                expandOnlyEventId?.let { add(statusBarStackEventOwnerKey(it)) }
                            }
                        }
                    SideEffect {
                        if (measureCtxToken != 0 && liveNaturalOwnerKeys.isNotEmpty()) {
                            statusBarPruneCardNaturalCache(
                                cache = cardNaturalByOwner,
                                liveOwnerKeys = liveNaturalOwnerKeys,
                                activeContextToken = measureCtxToken,
                            )
                        }
                    }

                    // Visual rect SCREEN: pill → frozen painted LRTB (task-034).
                    // Thieu painted: neo center/topPad; KHONG (maxW-pillW)/2 khi thieu pill.
                    fun currentVisualRect() =
                        run {
                            localView.rootView.getLocationOnScreen(hostLocOnScreen)
                            val (originLeft, originTop) =
                                statusBarMorphPillOriginInHostPx(
                                    capturedLeft = capturedPill.left,
                                    capturedTop = capturedPill.top,
                                    capturedWidth = capturedPill.width,
                                    capturedHeight = capturedPill.height,
                                    hostLocX = hostLocOnScreen[0],
                                    hostLocY = hostLocOnScreen[1],
                                    topPadPx = topPadPx.roundToInt(),
                                )
                            // Selector painted (frozen morph vs selected rest) → dich p=1.
                            val paintedDest = selectedPaintedDest
                            statusBarMorphLerpVisualRectPx(
                                pOffset = geom.pOffset,
                                pillLeftPx = originLeft.toFloat(),
                                pillTopPx = originTop.toFloat(),
                                pillWPx = pillWPx.toFloat(),
                                pillHPx = pillHPx.toFloat(),
                                visualTargetWPx = morphVisualTargetWPx.toFloat(),
                                visualTargetHPx = morphVisualTargetHPx.toFloat(),
                                sx = sx,
                                sy = sy,
                                maxWidthPx = maxWPx.toFloat(),
                                // Fallback top khi chua painted = SB inset + SpaceXxs.
                                targetVisualTopPx = topPadPx + topInsetPx,
                                paintedTargetLeftPx = paintedDest?.leftPx ?: Float.NaN,
                                paintedTargetTopPx = paintedDest?.topPx ?: Float.NaN,
                            )
                        }

                    fun animOffset(): IntOffset {
                        val vr = currentVisualRect()
                        val (left, top) =
                            statusBarMorphHostOriginFromVisualRectPx(
                                visualLeftPx = vr.leftPx,
                                visualTopPx = vr.topPx,
                                insetLeftPx = sidePadPx.toFloat(),
                                insetTopPx = topInsetPx.toFloat(),
                                sx = sx,
                                sy = sy,
                            )
                        return IntOffset(left.roundToInt(), top.roundToInt())
                    }

                    // First-frame handoff: pill that chi an khi shell XY+WH khop @p≈0.
                    // Co rect that + morph dang chay → suppress (tranh 2-pill); KHONG
                    // suppress khi thieu capture (center gia).
                    LaunchedEffect(
                        isExpanded,
                        pillSuppressed,
                        hasValidPillRect,
                        visualShellWPx,
                        visualShellHPx,
                        morphProgress,
                        morphRunning,
                        pillWPx,
                        pillHPx,
                        capturedPill.left,
                        capturedPill.top,
                    ) {
                        if (!isExpanded || pillSuppressed) return@LaunchedEffect
                        if (!hasValidPillRect) return@LaunchedEffect
                        withFrameNanos { }
                        localView.rootView.getLocationOnScreen(hostLocOnScreen)
                        val (pillLeftHost, pillTopHost) =
                            statusBarMorphPillOriginInHostPx(
                                capturedLeft = capturedPill.left,
                                capturedTop = capturedPill.top,
                                capturedWidth = capturedPill.width,
                                capturedHeight = capturedPill.height,
                                hostLocX = hostLocOnScreen[0],
                                hostLocY = hostLocOnScreen[1],
                                topPadPx = topPadPx.roundToInt(),
                            )
                        val vrGate = currentVisualRect()
                        val aligned =
                            statusBarMorphPillShellAlignedRect(
                                pillLeftPx = pillLeftHost.toFloat(),
                                pillTopPx = pillTopHost.toFloat(),
                                pillWPx = pillWPx.toFloat(),
                                pillHPx = pillHPx.toFloat(),
                                visualLeftPx = vrGate.leftPx,
                                visualTopPx = vrGate.topPx,
                                visualWPx = visualShellWPx,
                                visualHPx = visualShellHPx,
                                progress = morphProgress,
                            )
                        if (
                            statusBarMorphShouldConfirmPillSuppressed(
                                hasValidPillRect = true,
                                shellAlignedRect = aligned,
                                morphRunning = morphRunning,
                                morphProgress = morphProgress,
                            )
                        ) {
                            viewModel.statusBarExpansion.confirmPillSuppressed()
                        }
                    }

                    // Circle pill chrome (vien+icon): sibling NGOAI host — morph only, no bounce.
                    if (useCircleStyle && ringChromeAlpha > 0.01f && pillEvent != null) {
                        StatusBarMorphCircleRingChrome(
                            event = pillEvent,
                            accent = pillAccent,
                            contentColor = pillContentColor,
                            progress = circleProgressFrozen,
                            alpha = ringChromeAlpha,
                            modifier =
                                Modifier
                                    .offset {
                                        val vr = currentVisualRect()
                                        val (l, t) =
                                            statusBarMorphCircleRingVisualTopLeftPx(
                                                shellLeftPx = vr.leftPx,
                                                shellTopPx = vr.topPx,
                                                visualWPx = vr.widthPx,
                                                visualHPx = vr.heightPx,
                                                diameterPx = ringDiameterPx,
                                            )
                                        IntOffset(l.roundToInt(), t.roundToInt())
                                    }
                                    .requiredSize(ringDiameterDp),
                        )
                    }
                    // Host morph: scale anisotropic + 1 persistent shared glass + content.
                    // Task-034: glass GIU @p1 rest (AxSystemFrost) — top FG-only; under blur rieng.
                    // Khong movableContentOf cross-window (pill SB ≠ overlay); 1-owner trong host.
                    // Semantics goc: fillMaxWidth + wrapContentHeight (+ stack bottom 50.dp).
                    Box(
                        modifier =
                            Modifier
                                .offset { animOffset() }
                                .graphicsLayer {
                                    scaleX = sx
                                    scaleY = sy
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    clip = false
                                }
                                .requiredSize(width = targetWDp, height = targetHDp)
                                .onGloballyPositioned { coords ->
                                    // PERF: skip state write khi bounds khong doi (collapse jank
                                    // attribution PENDING — Trace sync cho lan do GPT tiep).
                                    Trace.beginSection("AxDbCollapse/hostOgp")
                                    try {
                                        val pos = coords.positionInWindow()
                                        if (hostWinLeftForPainted.floatValue != pos.x) {
                                            hostWinLeftForPainted.floatValue = pos.x
                                        }
                                        if (hostWinTopForPainted.floatValue != pos.y) {
                                            hostWinTopForPainted.floatValue = pos.y
                                        }
                                        val bottom =
                                            (pos.y + coords.size.height * sy)
                                                .toInt()
                                                .coerceAtLeast(0)
                                        onBottom.value(bottom)
                                    } finally {
                                        Trace.endSection()
                                    }
                                },
                    ) {
                        // Persistent shared glass + Row pill FG (cung host).
                        // Host van requiredSize(layout); Circle ring = sibling ngoai.
                        val pillWDp = with(density) { pillWPx.toDp() }
                        val pillHDp = with(density) { pillHPx.toDp() }
                        // Track 2: content FOLLOW shell (uniform); khong inv huy growth.
                        val (dummySx, dummySy) =
                            statusBarMorphDummyLayerScale(
                                hostSx = sx,
                                hostSy = sy,
                                dummyScale = dummyContentScale,
                                pillWPx = pillWPx.toFloat(),
                                pillHPx = pillHPx.toFloat(),
                                visualTargetWPx = morphVisualTargetWPx.toFloat(),
                                visualTargetHPx = morphVisualTargetHPx.toFloat(),
                            )
                        // Canh giua FG trong shell — absoluteOffset (khong mirror RTL).
                        val dummyLocalOff =
                            statusBarMorphDummyContentLocalOffsetPx(
                                pillWPx = pillWPx.toFloat(),
                                pillHPx = pillHPx.toFloat(),
                                localSx = dummySx,
                                localSy = dummySy,
                                visualTargetWPx = morphVisualTargetWPx.toFloat(),
                                visualTargetHPx = morphVisualTargetHPx.toFloat(),
                            )
                        // dragY SSOT: dich CA shell+clip — doc LIVE luc draw (FAIL B1).
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .offset {
                                        IntOffset(sidePadPx, topInsetPx)
                                    }
                                    .requiredSize(
                                        width = morphVisualTargetWDp,
                                        height = morphVisualTargetHDp,
                                    )
                                    .graphicsLayer {
                                        clip = true
                                        // CardLocal → ty=0; SharedPlate (morph) → dragY.
                                        val liveDragY = stackDragYState.floatValue
                                        val atRest = morphExpandedAtRestState.value
                                        translationY =
                                            if (useCardLocalGlassState.value) {
                                                0f
                                            } else {
                                                statusBarMorphSharedGlassDragTranslationYPx(
                                                    dragYPx = liveDragY,
                                                    morphExpandedAtRest = atRest,
                                                )
                                            }
                                    },
                        ) {
                            // Shared morph glass CHI khi pill↔expanded (keepSharedBlurSubtree).
                            // Expanded stack CardLocal: khong compose — khong parked SF region.
                            if (keepSharedBlurSubtree) {
                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .islandBlur(
                                                cornerRadius =
                                                    shellRxDp.coerceAtLeast(1.dp),
                                                cornerRadiusY =
                                                    shellRyDp.coerceAtLeast(1.dp),
                                                fallbackColor = shellFallback,
                                                tint = shellTint,
                                                alpha = paintMorphGlassAlpha,
                                                highlightWidth = ChipHighlightWidth,
                                            ),
                                )
                            }
                            // Row pill chrome FG-only — cung host glass (isotropic follow).
                            // Circle: StatusBarMorphCircleRingChrome sibling.
                            if (
                                !useCircleStyle &&
                                    pillChromeAlpha > 0.01f &&
                                    morphPresentation != null
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .absoluteOffset {
                                                IntOffset(
                                                    dummyLocalOff.xPx.roundToInt(),
                                                    dummyLocalOff.yPx.roundToInt(),
                                                )
                                            }
                                            .graphicsLayer {
                                                scaleX = dummySx
                                                scaleY = dummySy
                                                transformOrigin =
                                                    TransformOrigin(0f, 0f)
                                                alpha = pillChromeAlpha
                                            }
                                            .requiredSize(
                                                width = pillWDp,
                                                height = pillHDp,
                                            ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    // Dummy LIVE (Samsung Chronometer) — freezeMotion tach liveValue.
                                    StatusBarPillBody(
                                        presentation = morphPresentation,
                                        includeBlur = false,
                                        includeProgress = true,
                                        staticMode = false,
                                        freezeMotion = true,
                                        liveValue = true,
                                        blurAlpha = 1f,
                                    )
                                }
                            }
                        }
                        // Measure unbounded TRUOC alpha — graphicsLayer alpha khong chan measure.
                        // Yield shared chi khi morph SharedPlate — stack rest luon false.
                        val yieldNativeGlass = glassHandoff.yieldNativeGlassToShared
                        Box(
                            modifier =
                                Modifier
                                    .requiredWidth(targetWDp)
                                    .wrapContentHeight(
                                        align = Alignment.Top,
                                        unbounded = true,
                                    )
                                    .onSizeChanged { size ->
                                        lockNaturalSize(size.width, size.height)
                                    }
                                    .graphicsLayer { alpha = expandContentAlpha },
                        ) {
                            CompositionLocalProvider(
                                LocalBlurAlpha provides
                                    (expandedBlurAlpha * cardBlurFactor)
                                        .coerceIn(0f, 1f),
                                LocalStatusBarYieldGlassToMorph provides yieldNativeGlass,
                                LocalStatusBarOpenGlassAwaitDestinationDraw provides
                                    openAwaitDestDraw,
                                LocalStatusBarOpenGlassDestinationSessionGen provides
                                    openGlassSessionGen,
                                LocalStatusBarOpenGlassDestinationDrawReporter provides
                                    if (openAwaitDestDraw) openGlassDestDrawReporter else null,
                                LocalStatusBarPaintedCardBoundsReporter provides
                                    paintedCardBoundsReporter,
                                LocalStatusBarCardNaturalMeasureContextToken provides
                                    measureCtxToken,
                                LocalStatusBarStackCardNaturalSizeReporter provides
                                    stackCardNaturalReporter,
                                LocalStatusBarStackDragYReporter provides
                                    { y -> stackDragYState.floatValue = y },
                                LocalStatusBarStackDragSettlingReporter provides
                                    { s -> stackDragSettlingState.value = s },
                                LocalStatusBarStackDragPhaseReporter provides
                                    { p -> stackDragPhaseState.value = p },
                            ) {
                                // Idle p1 shell=0 → yield false → PrimaryCard/Media frost native.
                                chipState?.let { state ->
                                    val filtered =
                                        state.allEvents.filter {
                                            it !is IslandEvent.AospChip
                                        }
                                    if (filtered.isEmpty()) return@let
                                    ExpandedIslandContent(
                                        events = filtered,
                                        interactor = viewModel.interactor,
                                        onCollapse = {
                                            viewModel.statusBarExpansion.collapse()
                                        },
                                        expandOnlyEventId = expandOnlyEventId,
                                        pinnedEventId = state.event.id,
                                        onExpandOnlyEventIdChange = { id ->
                                            viewModel.statusBarExpansion
                                                .setExpandOnlyEventId(id)
                                        },
                                        // Commit stack: sync expandOnly + frozen → B
                                        // (event.id, khong index cu). Atomic sau pin.
                                        onConfirmedStackEventId = { eventId ->
                                            viewModel.statusBarExpansion
                                                .onConfirmedStackSelection(eventId)
                                        },
                                        hapticsViewModelFactory =
                                            viewModel.interactor
                                                .sliderHapticsViewModelFactory,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalBlurAlpha provides alertBlurAlpha) {
        AnimatedVisibility(
            visibleState = notifVisible,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(
                animationSpec = motionScheme.defaultSpatialSpec(),
                initialScale = 0.4f,
                transformOrigin = origin,
            ),
            exit = fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(
                animationSpec = motionScheme.fastSpatialSpec(),
                targetScale = 0.4f,
                transformOrigin = origin,
            ),
        ) {
            val onAlertBounds = rememberUpdatedState(onAlertContentBoundsPx)
            // Expanded: fillMaxSize + clickable ngoai the → dismiss (giong island).
            // Compact: wrap — touchable dung bounds the, tap ngoai pass-through.
            Box(
                modifier =
                    if (alertCompact) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    viewModel.interactor.dismissNotificationAlertFromOutside()
                                },
                            )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = topPad)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                val left = pos.x.toInt().coerceAtLeast(0)
                                val top = pos.y.toInt().coerceAtLeast(0)
                                val right = (pos.x + coords.size.width).toInt()
                                val bottom = (pos.y + coords.size.height).toInt()
                                onAlertBounds.value(left, top, right, bottom)
                            },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val alerts = lastAlerts.value
                    if (alerts.isNotEmpty()) {
                        // StackAlert tu padding ngang; Compact/Expanded policy theo top.
                        StackedAlertPanels(
                            alerts = alerts,
                            pinnedAlertKey = lastPinnedAlertKey.value,
                            interactor = viewModel.interactor,
                            onDismissTop = {
                                viewModel.interactor.dismissNotificationAlert()
                            },
                            initiallyCompact = compactNotifs,
                        )
                    }
                }
            }
        }
    }
}

private class PanelLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val backDispatcher = OnBackPressedDispatcher()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = backDispatcher

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
