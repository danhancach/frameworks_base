@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui

import android.os.Trace
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.res.R
import kotlin.math.abs
import kotlin.math.roundToInt

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
import com.android.systemui.axdynamicbar.shared.stackExpandedContentTreeBlurAlpha
import com.android.systemui.axdynamicbar.shared.stackOpenGlassAwaitDestinationDraw
import com.android.systemui.axdynamicbar.shared.stackOpenGlassEffectiveTransferPhase
import com.android.systemui.axdynamicbar.shared.stackOpenGlassLocalBlurAlphaFactor
import com.android.systemui.axdynamicbar.shared.stackOpenGlassMergeHandoff
import com.android.systemui.axdynamicbar.shared.stackOpenGlassShouldAbandonWaitingForStackGesture
import com.android.systemui.axdynamicbar.shared.stackOpenGlassShouldCancelTransfer
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldResetSizingState
import com.android.systemui.axdynamicbar.shared.LockedTargetHeight
import com.android.systemui.axdynamicbar.shared.MorphScaleFloor
import com.android.systemui.axdynamicbar.shared.StatusBarCardNaturalCacheEntry
import com.android.systemui.axdynamicbar.shared.StatusBarPaintedCardReportPx
import com.android.systemui.axdynamicbar.shared.chipAccentColorFor
import com.android.systemui.axdynamicbar.shared.chipContentColorFor
import com.android.systemui.axdynamicbar.shared.computeKeyguardMorphGeom
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
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualCornerPx
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
import com.android.systemui.axdynamicbar.shared.statusBarMorphRunDurationMs
import com.android.systemui.axdynamicbar.shared.statusBarMorphSampleAtRunFraction
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
import com.android.systemui.axdynamicbar.shared.StatusBarFadeOutCardMs
import com.android.systemui.axdynamicbar.shared.StatusBarMorphRunFrame
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualShellSizePx
import com.android.systemui.axdynamicbar.shared.statusBarOverlayHideDelayMs
import com.android.systemui.axdynamicbar.shared.statusBarShouldUpdateMorphEndpoint
import com.android.systemui.blur.AxSystemFrost
import com.android.systemui.axdynamicbar.ui.compose.ExpandedIslandContent
import com.android.systemui.axdynamicbar.ui.compose.StackedAlertPanels
import com.android.systemui.axdynamicbar.ui.compose.StatusBarMorphCircleRingChrome
import com.android.systemui.axdynamicbar.ui.compose.StatusBarPillBody
import com.android.systemui.axdynamicbar.ui.compose.statusBarPillPresentationOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val StatusBarPillWidthFallback = 56.dp
/** Cho natural measure (~400ms@60Hz); timeout dung last envelope — khong start H=pill. */
private const val MorphEndpointStageFrames = 24
/** Tran cho them (~800ms tong) neu RemoteViews tre; het van khong empty-start. */
private const val MorphEndpointHardTimeoutFrames = 48

@Composable
internal fun OverlayContent(
    viewModel: AxDynamicBarChipViewModel,
    statusBarHeightPx: Int,
    hasCutout: Boolean,
    onExpandedContentBottomPx: (Int) -> Unit,
    onAlertContentBoundsPx: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    collapsePerfTrace: CollapseMorphPerfTrace,
    onRegisterHideCleanup: ((() -> Unit)?) -> Unit = {},
) {
    val density = LocalDensity.current
    val isLargeScreen = Utilities.isLargeScreen(LocalContext.current)

    val largeScreenExtra = if (isLargeScreen) 4.dp else 0.dp
    val topPad = if (hasCutout) largeScreenExtra
        else with(density) { statusBarHeightPx.toDp() } + largeScreenExtra
    val chipState by viewModel.chipState.collectAsStateWithLifecycle()
    val chipStyle by viewModel.chipStyle.collectAsStateWithLifecycle()
    val isExpanded by viewModel.isExpanded.collectAsStateWithLifecycle()
    val isAlertExpanded by viewModel.isAlertExpanded.collectAsStateWithLifecycle()
    val expandOnlyEventId by
        viewModel.statusBarExpansion.expandOnlyEventId.collectAsStateWithLifecycle()
    val uiState by viewModel.interactor.uiState.collectAsStateWithLifecycle()
    val isOnKeyguard by viewModel.isOnKeyguard.collectAsStateWithLifecycle()
    val notifAlerts = uiState.notificationAlerts
    val pinnedAlertKey = uiState.notificationAlert?.sbn?.key
    val compactNotifs by viewModel.interactor.settings.compactNotifications.collectAsStateWithLifecycle()
    val alertCompact by viewModel.interactor.alertCompactMode.collectAsStateWithLifecycle()
    val isAlertOverlay = isAlertExpanded && notifAlerts.isNotEmpty()

    val pillSuppressed by
        viewModel.statusBarExpansion.pillSuppressed.collectAsStateWithLifecycle()
    val isFadingOutCard by
        viewModel.statusBarExpansion.isFadingOutCard.collectAsStateWithLifecycle()
    val capturedPill by
        viewModel.statusBarExpansion.capturedPillFrame.collectAsStateWithLifecycle()
    val capturedPillOwnerId by
        viewModel.statusBarExpansion.capturedPillOwnerId.collectAsStateWithLifecycle()
    val frozenPresentation by
        viewModel.statusBarExpansion.frozenPillPresentation.collectAsStateWithLifecycle()
    val layoutGen = viewModel.statusBarExpansion.layoutGeneration

    // Mot dong ho publish ca hai track. KHONG doc morphFrameState.value trong composition
    // goc cua OverlayContent (tranh recompose 120Hz toan cay); chi doc trong derivedStateOf
    // boolean va graphicsLayer / leaf glass.
    var morphTargetExpanded by remember { mutableStateOf(false) }
    var morphSettledExpanded by remember { mutableStateOf(false) }
    val morphFrameState = remember { mutableStateOf(StatusBarMorphRunFrame(0f, 0f)) }
    // Samsung OngoingCardController.fadeOutCard(): chi fade alpha 1->0 trong 100ms, khong morph.
    val cardFadeOutAlpha = remember { Animatable(1f) }
    LaunchedEffect(isFadingOutCard, isExpanded, layoutGen) {
        if (isFadingOutCard) {
            cardFadeOutAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = StatusBarFadeOutCardMs, easing = LinearEasing),
            )
            // Snap morph ve 0 truoc teardown de khong chay morph collapse 600ms sau khi an.
            morphTargetExpanded = false
            morphSettledExpanded = false
            morphFrameState.value = StatusBarMorphRunFrame(0f, 0f)
            onExpandedContentBottomPx(0)
            viewModel.statusBarExpansion.finishFadeOutCard(layoutGen)
        } else if (isExpanded) {
            cardFadeOutAlpha.snapTo(1f)
        }
    }
    LaunchedEffect(morphTargetExpanded) {
        val targetExpanded = morphTargetExpanded
        val start = morphFrameState.value
        val durationMs =
            statusBarMorphRunDurationMs(
                startGeometry = start.geometry,
                startLinear = start.linear,
                targetExpanded = targetExpanded,
            )
        if (durationMs > 0) {
            animate(0f, 1f, animationSpec = tween(durationMs, easing = LinearEasing)) {
                fraction, _ ->
                morphFrameState.value =
                    statusBarMorphSampleAtRunFraction(
                        runFraction = fraction,
                        startGeometry = start.geometry,
                        startLinear = start.linear,
                        targetExpanded = targetExpanded,
                    )
            }
        }
        val endpoint = if (targetExpanded) 1f else 0f
        morphFrameState.value = StatusBarMorphRunFrame(endpoint, endpoint)
        morphSettledExpanded = targetExpanded
    }

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
    // Top-card painted W/H — chi selected top (isTop&&!isLeaving) report.
    val paintedCardWState = remember { mutableIntStateOf(0) }
    val paintedCardHState = remember { mutableIntStateOf(0) }
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

    // Boolean tho tu derivedStateOf — chi trigger recompose tai bien start/settle,
    // KHONG recompose tung frame 120Hz.
    val isMorphing by remember {
        derivedStateOf { abs(morphFrameState.value.geometry) > 0.01f }
    }
    // A reversed opening can have settled=false and target=false while still on screen.
    val morphRunning by remember {
        derivedStateOf {
            val f = morphFrameState.value
            val endpoint = if (morphTargetExpanded) 1f else 0f
            morphSettledExpanded != morphTargetExpanded ||
                f.geometry != endpoint ||
                f.linear != endpoint
        }
    }
    val morphClosing = !morphTargetExpanded && morphRunning
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
    // Geometry settle: settled/target false + !running — KHONG end chi vi progress≈0.01.
    val morphGeometrySettled =
        !morphSettledExpanded &&
            !morphTargetExpanded &&
            !morphRunning
    val morphExpandedAtRest by remember {
        derivedStateOf {
            val f = morphFrameState.value
            morphSettledExpanded &&
                morphTargetExpanded &&
                !morphRunning &&
                abs(f.geometry - 1f) <= 0.01f
        }
    }
    val morphAtRestCollapsed by remember {
        derivedStateOf {
            val f = morphFrameState.value
            !morphSettledExpanded &&
                !morphTargetExpanded &&
                !morphRunning &&
                abs(f.geometry) <= 0.001f
        }
    }
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
    // A stack gesture can take over the outstanding glass ACK after the opening morph ends.
    var openingGlassTakenByStack by remember(isExpanded, layoutGen) { mutableStateOf(false) }
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
    // Holder thuong (khong Snapshot) cho host scale/origin + targetHost — tranh write
    // Compose state trong composition / onGloballyPositioned moi frame.
    val hostGeomHolder = remember { MorphHostGeometryHolder() }
    // Sync callback cho paintedCardBoundsReporter doc frame moi nhat truoc draw phase.
    val syncHostGeomBeforePainted = remember { arrayOfNulls<() -> Unit>(1) }
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
            report@{ report ->
                val (running, atRest) = paintedFreezeGate.value
                // Dang morph va da co morphFrozen hop le: bo qua report tu cay dang scale
                // de tranh float jitter gay recompose + layout lan 2 moi frame.
                if (running && !atRest && hostGeomHolder.morphFrozenValid) {
                    return@report
                }
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
                    // Dam bao hostGeomHolder.sx/sy/hostLeft/Top da dong bo cho frame hien tai
                    // ke ca khi onGloballyPositioned chay truoc draw phase (rest / CardLocal).
                    syncHostGeomBeforePainted[0]?.invoke()
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
                            hostWindowLeftPx =
                                hostGeomHolder.overlayRootWinX + hostGeomHolder.hostLeftPx,
                            hostWindowTopPx =
                                hostGeomHolder.overlayRootWinY + hostGeomHolder.hostTopPx,
                            hostSx = hostGeomHolder.sx,
                            hostSy = hostGeomHolder.sy,
                            ownerKey = reportOwner,
                            contextToken =
                                if (report.contextToken != 0) {
                                    report.contextToken
                                } else {
                                    activeCtx
                                },
                        )
                    if (w > 0 && paintedCardWState.intValue != w) {
                        paintedCardWState.intValue = w
                    }
                    if (h > 0 && paintedCardHState.intValue != h) {
                        paintedCardHState.intValue = h
                    }
                    // Drag/settle: KHONG poison LRTB; skip dest khi |dragY|>eps.
                    val allowLrtb =
                        statusBarMorphPaintedLrtbUpdateAllowed(
                            dragYPx = stackDragYState.floatValue,
                            settling = stackDragSettlingState.value,
                        )
                    if (allowLrtb) {
                        val placementReady = hostGeomHolder.targetHostPlacementReady
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
                                    targetHostLeftPx = hostGeomHolder.targetHostLeftPx,
                                    targetHostTopPx = hostGeomHolder.targetHostTopPx,
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
                                    if (!ownedRectNearlyEqual(livePaintedOwnedRect, owned)) {
                                        livePaintedOwnedRect = owned
                                    }
                                    // Rest: selected-rest; KHONG ghi morph-frozen.
                                    if (atRest && !ownedRectNearlyEqual(selectedRestOwnedRect, owned)) {
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
                                        hostGeomHolder.morphFrozenValid =
                                            owned.isValidAbovePill(1, 1)
                                    }
                                }
                            }
                        }
                    } else if (w > 0 && h > 0 && atRest) {
                        // Settle skip LRTB: cap nhat W/H atomic cung owner.
                        val prev = selectedRestOwnedRect
                        if (prev != null && prev.ownerKey == reportOwner) {
                            val nextRest =
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
                            if (!ownedRectNearlyEqual(prev, nextRest)) {
                                selectedRestOwnedRect = nextRest
                            }
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
    val ownerEndedForMorphHost = isChipOwnerEndedAuthoritative(hostChipState)
    val liveDestOwnerForMorphHost = liveSelectedOwnerIdOf(hostChipState)
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

    // Owner on dinh: alert / expand-only id hoac pinned event -- doi -> invalidate H khoa.
    val contentOwnerKey =
        if (isAlertOverlay) {
            uiState.notificationAlert?.id?.let { statusBarStackEventOwnerKey(it) } ?: "alert_stack"
        } else {
            expandOnlyEventId?.let { "event:$it" }
                ?: chipState?.event?.id?.let { "event:$it" }
                ?: "stack"
        }
    // Sync holder truoc painted report (stale owner reject).
    activePaintedOwnerKeyHolder[0] = contentOwnerKey
    // Lookup natural theo active measure-context (task-032) — KHONG clear async orientation.

    // Drive target SAU first frame (+ uu tien source rect) — first/subsequent/reverse OK.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        morphFirstFramePassed = true
    }

    fun resetMorphSizingState() {
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
        morphFrozenOwnedRect = null
        selectedRestOwnedRect = null
        livePaintedOwnedRect = null
        wasMorphExpandedAtRest = false
        lastPaintedRectProvenance = StatusBarPaintedRectProvenance.None
        hostGeomHolder.reset()
    }

    // Chi reset kich thuoc morph khi host da unmount — khong reset luc collapseHandbackHold
    // de tranh targetHPx ve pillHPx lam sy nhay len 1.0 trong khi sx van bang be rong pill.
    LaunchedEffect(showMorphHost) {
        if (statusBarMorphShouldResetSizingState(showMorphHost = showMorphHost)) {
            resetMorphSizingState()
        }
    }

    // Dang ky cleanup dong bo cho warm-reuse (Goi 2): xoa sach state morph/glass/alert
    // ngay truoc khi overlay chuyen View.GONE + ON_STOP, tranh ro trang thai sang lan mo sau.
    SideEffect {
        onRegisterHideCleanup {
            morphTargetExpanded = false
            morphSettledExpanded = false
            morphFrameState.value = StatusBarMorphRunFrame(0f, 0f)
            morphAllowFallbackRect = false
            collapseHandbackCtrl.onExpandedOrReverse()
            collapseHandbackHold = false
            collapseLiveDrawnAcked = false
            collapseHandbackTimerGen = 0
            stackDragYState.floatValue = 0f
            stackDragSettlingState.value = false
            stackDragPhaseState.value = StackExpandedDragPhase.Idle
            openGlassCtrl.cancel("hideOverlay")
            openGlassPhase = openGlassCtrl.phase
            openGlassDestAcked = openGlassCtrl.destinationFirstDrawAcked
            openGlassSessionGen = openGlassCtrl.sessionGen
            resetMorphSizingState()
        }
    }

    // Premasure: shell giu pill, do natural unbounded; timeout chi freeze last envelope.
    // CAM set allowFallbackWithoutEndpoint rong (start H=pill → snap late F48).
    LaunchedEffect(isExpanded, hasValidPillRect, morphFirstFramePassed, layoutGen) {
        if (isFadingOutCard) return@LaunchedEffect
        if (!isExpanded) {
            morphAllowFallbackRect = false
            morphTargetExpanded = false
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
        if (!isExpanded || isFadingOutCard) return@LaunchedEffect
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
        morphTargetExpanded =
            statusBarMorphShouldTargetExpanded(
                intentExpanded = true,
                firstCompositionPassed = morphFirstFramePassed,
                hasValidSourceRect = hasValidPillRect,
                allowFallbackWithoutRect = morphAllowFallbackRect,
                hasMorphEndpoint = hasEndpoint,
                allowFallbackWithoutEndpoint = false,
            )
    }

    // Collapse START → beginPrepare; morphAtRestCollapsed → mark Ready (cung session, khong reset).
    // Reverse/expand → cancel. KHONG key theo morphProgress (tranh cancel/restart 120Hz).
    LaunchedEffect(
        morphSettledExpanded,
        morphTargetExpanded,
        morphRunning,
        morphAtRestCollapsed,
        isExpanded,
        pillSuppressed,
        layoutGen,
    ) {
        if (isFadingOutCard) return@LaunchedEffect
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
        val ownerEndedNow = isChipOwnerEndedAuthoritative(currentChipState)
        if (statusBarMorphShouldTerminalDismissNoDestination(ownerEndedNow) || abortCollapseVisual) {
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
                liveSelectedOwnerId = liveSelectedOwnerIdOf(currentChipState),
                frozenOwnerId = frozenPresentation?.ownerId,
            )
        val morphAtRest = morphAtRestCollapsed
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
        // Giu kich thuoc dich den khi host unmount (LaunchedEffect(showMorphHost)) — tranh sy=1.0.
        onExpandedContentBottomPx(0)
    }

    // Live selected-owner / owner-end — rememberUpdatedState + snapshotFlow (B2).
    // Tach khoi morphPresentation/frozen (co the prefer frozen khi live lech).
    // chipState la delegated property — capture local moi composition (khong remember {})
    // de smart-cast / cung snapshot cho owner + owner-ended.
    val chipStateNow = chipState
    val liveSelectedOwnerState = rememberUpdatedState(liveSelectedOwnerIdOf(chipStateNow))
    val ownerEndedState = rememberUpdatedState(isChipOwnerEndedAuthoritative(chipStateNow))
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

    LaunchedEffect(isAlertOverlay) {
        if (!isAlertOverlay) onAlertContentBoundsPx(0, 0, 0, 0)
    }

    LaunchedEffect(chipState, isAlertExpanded) {
        val currentChipState = chipState
        if (isAlertExpanded) {
            if (currentChipState?.notificationAlert == null && isExpanded) {
                viewModel.statusBarExpansion.collapseTerminalNoDestination()
            }
            return@LaunchedEffect
        }
        val filtered = currentChipState?.allEvents?.filter { it !is IslandEvent.AospChip }
        if (filtered.isNullOrEmpty() && isExpanded) {
            // Terminal no-destination — khong ordinary morph toi fabricated origin.
            viewModel.statusBarExpansion.collapseTerminalNoDestination()
        }
    }

    val motionScheme = MaterialTheme.motionScheme
    val localView = LocalView.current
    openGlassHostViewHolder[0] = localView
    openGlassLiveExpandedHolder[0] = isExpanded && morphExpandedAtRest
    val hostLocOnScreen = remember { IntArray(2) }

    // Blur per-card (PrimaryCard/Media/Timer/Alert) — giu dang State de KHONG doc trong
    // OverlayContent khi yieldNativeGlass=true (tranh 18 lan recompose luc open morph).
    val expandedBlurAlphaState = animateFloatAsState(
        targetValue = if (showMorphHost) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "expanded_blur_alpha",
    )

    // Remember cac reporter/callback cho staticCompositionLocalOf de ExpandedIslandContent
    // khong recompose vo ich khi OverlayContent doi state o bien.
    val isExpandedLatest = rememberUpdatedState(isExpanded)
    val stackDragYReporter = remember { { y: Float -> stackDragYState.floatValue = y } }
    val stackDragSettlingReporter = remember { { s: Boolean -> stackDragSettlingState.value = s } }
    val stackDragPhaseReporter =
        remember {
            { phase: StackExpandedDragPhase ->
                stackDragPhaseState.value = phase
                if (
                    isExpandedLatest.value && morphExpandedAtRestState.value &&
                        phase == StackExpandedDragPhase.Held &&
                        openGlassCtrl.phase != StackOpenGlassTransferPhase.Complete
                ) {
                    openingGlassTakenByStack = true
                }
            }
        }
    val onCollapseExpanded = remember(viewModel) { { viewModel.statusBarExpansion.collapse() } }
    val onExpandOnlyIdChange =
        remember(viewModel) { { id: String? -> viewModel.statusBarExpansion.setExpandOnlyEventId(id) } }
    val onConfirmedStackId =
        remember(viewModel) {
            { eventId: String -> viewModel.statusBarExpansion.onConfirmedStackSelection(eventId) }
        }

    if (showMorphHost) {
        val onBottom = rememberUpdatedState(onExpandedContentBottomPx)
        val outsideDismissModifier =
            if (!isFadingOutCard && !(isAlertOverlay && alertCompact)) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (isAlertOverlay) {
                            viewModel.interactor.dismissNotificationAlertFromOutside()
                        } else {
                            viewModel.statusBarExpansion.collapse()
                        }
                    },
                )
            } else {
                Modifier
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = false
                        alpha = cardFadeOutAlpha.value
                    }
                    .then(outsideDismissModifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        hostGeomHolder.overlayRootWinX = pos.x
                        hostGeomHolder.overlayRootWinY = pos.y
                    },
            ) {
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
                            hostGeomHolder.morphFrozenValid = rebind.isValidAbovePill(1, 1)
                        }
                    } else {
                        hostGeomHolder.morphFrozenValid =
                            morphFrozenOwnedRect?.isValidAbovePill(1, 1) == true
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
                val (targetHostLeftPx, targetHostTopPx) =
                    statusBarMorphExpandedTargetHostPlacementPx(
                        maxWidthPx = maxWPx.toFloat(),
                        layoutTargetWPx = layoutTargetWPx.toFloat(),
                        topPadPx = topPadPx,
                    )
                hostGeomHolder.targetHostLeftPx = targetHostLeftPx
                hostGeomHolder.targetHostTopPx = targetHostTopPx
                // Ready khi envelope/endpoint > pill — defer freeze neu chua.
                hostGeomHolder.targetHostPlacementReady = layoutTargetWPx > pillWPx
                val targetWDp = with(density) { layoutTargetWPx.toDp() }
                val targetHDp = with(density) { layoutTargetHPx.toDp() }
                val morphVisualTargetWDp =
                    with(density) { morphVisualTargetWPx.toDp() }
                val morphVisualTargetHDp =
                    with(density) { morphVisualTargetHPx.toDp() }
                // Float dpi tu Density — dung chung cho bo goc Samsung 36dp va width gate.
                val morphMotionDensityDpi = density.density * 160f
                val cardCornerPx = with(density) { expandCornerTarget.toPx() }
                val widthGatePx =
                    statusBarMorphPanelWidthGatePx(
                        densityDpi = morphMotionDensityDpi,
                        visualTargetWPx = morphVisualTargetWPx.toFloat(),
                    )
                // Endpoint = AxSystemFrost resource alpha — KHONG ep AlphaGlass 0.72.
                val frostCtx = localView.context
                val frostFallback = Color(AxSystemFrost.fallbackColor(frostCtx))
                val frostTint = Color(AxSystemFrost.tintColor(frostCtx))
                // Blur-on: surfaceBrightTint; blur-off: materialColorSurfaceBright.
                val panelEndpointFallback = frostFallback
                val panelEndpointTint =
                    if (AxSystemFrost.isFrostActive(frostCtx)) frostTint else frostFallback
                // Pill source = live StatusBarPillVisual (accent×AlphaGlass).
                val pillSourceTint = pillAccent.copy(alpha = AlphaGlass)
                // Task-034: 1 persistent shared glass owner (p0→p1 rest→close→handback).
                // Ownership glass chi phu thuoc phase + pillSuppressed (khong phu thuoc pillFollowAlpha).
                val sharedGlassOwnerPresent = true
                val handoffOwnGlass =
                    statusBarCollapseHandoffOwnershipOf(
                        phase = collapseHandoffSnap.phase,
                        pillSuppressed = pillSuppressed,
                        pillFollowAlpha = 1f,
                    )
                val persistentGlass =
                    statusBarMorphPersistentSharedGlassAlpha(
                        sharedGlassOwnerPresent = sharedGlassOwnerPresent,
                        collapseChromeFactor = collapseChromeFactor,
                    )
                val paintMorphGlassAlphaRaw =
                    statusBarCollapseHandoffOverlayGlassAlpha(
                        ownership = handoffOwnGlass,
                        persistentSharedGlassAlpha = persistentGlass,
                    )
                // task-041: expanded stack luon CardLocal (moi phase); SharedPlate
                // chi pill↔expanded morph. Khong Idle yield / warm-floor / observe.
                // task-043: open at-rest GIU shared den local first-draw (merge barrier).
                val stackDragPhaseLive = stackDragPhaseState.value
                val stackOwnsOpeningGlass = isExpanded && openingGlassTakenByStack
                val canTransferOpeningGlass = morphExpandedAtRest && !stackOwnsOpeningGlass
                val glassHandoffBase =
                    stackExpandedGlassHandoffDecision(
                        morphExpandedAtRest = morphExpandedAtRest || stackOwnsOpeningGlass,
                        phase = stackDragPhaseLive,
                        sharedGlassOwnerPresent = sharedGlassOwnerPresent,
                        basePaintAlpha = paintMorphGlassAlphaRaw,
                    )
                // Begin/retarget/cancel/abandon open transfer — SideEffect.
                // Continuity pending (rect chua valid) ≠ ready — khong begin 0-rect.
                // Held/settle khi chua ACK → abandon CardLocal (khong parked shared).
                SideEffect {
                    openGlassLiveExpandedHolder[0] = isExpanded && canTransferOpeningGlass
                    if (
                        stackOwnsOpeningGlass ||
                            stackOpenGlassShouldCancelTransfer(
                                morphExpandedAtRest = canTransferOpeningGlass,
                                isExpandedIntent = isExpanded,
                                transferPhase = openGlassCtrl.phase,
                            )
                    ) {
                        openGlassCtrl.cancel(
                            when {
                                stackOwnsOpeningGlass -> "stackGesture"
                                !isExpanded -> "collapse"
                                else -> "leaveRest"
                            },
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
                            canTransferOpeningGlass &&
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
                        morphExpandedAtRest = canTransferOpeningGlass,
                        recordedPhase = openGlassPhase,
                        destinationFirstDrawAcked = openGlassDestAcked,
                    )
                val glassHandoff =
                    stackOpenGlassMergeHandoff(
                        base = glassHandoffBase,
                        morphExpandedAtRest = canTransferOpeningGlass,
                        transferPhase = openGlassPhaseEffective,
                        destinationFirstDrawAcked = openGlassDestAcked,
                        basePaintAlpha = paintMorphGlassAlphaRaw,
                    )
                val useCardLocalGlass = glassHandoff.useCardLocalGlass
                val paintMorphGlassAlpha = glassHandoff.sharedPlateAlpha
                val keepSharedBlurSubtree = glassHandoff.keepSharedBlurSubtree
                // Await draw chi khi sessionGen>0 (sau begin) — tranh report gen0.
                val openAwaitDestDraw =
                    canTransferOpeningGlass && openGlassSessionGen > 0 &&
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
                val cardBlurFactor = openLocalBlurFactor

                // Gom tham so tinh trong suot 1 luot morph; danh gia 1 lan/frame trong derivedStateOf
                // va chi doc trong graphicsLayer / leaf glass (KHONG recompose OverlayContent 120Hz).
                val morphStaticParams =
                    MorphStaticParams(
                        maxWPx = maxWPx,
                        topPadPx = topPadPx,
                        pillWPx = pillWPx,
                        pillHPx = pillHPx,
                        capturedLeft = capturedPill.left,
                        capturedTop = capturedPill.top,
                        capturedWidth = capturedPill.width,
                        capturedHeight = capturedPill.height,
                        morphVisualTargetWPx = morphVisualTargetWPx,
                        morphVisualTargetHPx = morphVisualTargetHPx,
                        sidePadPx = sidePadPx,
                        topInsetPx = topInsetPx,
                        selectedPaintedLeftPx = selectedPaintedDest?.leftPx ?: Float.NaN,
                        selectedPaintedTopPx = selectedPaintedDest?.topPx ?: Float.NaN,
                        cardCornerPx = cardCornerPx,
                        morphMotionDensityDpi = morphMotionDensityDpi,
                        widthGatePx = widthGatePx,
                        morphRunning = morphRunning,
                        morphClosing = morphClosing,
                        pillAccent = pillAccent,
                        pillSourceTint = pillSourceTint,
                        panelEndpointFallback = panelEndpointFallback,
                        panelEndpointTint = panelEndpointTint,
                        useCircleStyle = useCircleStyle,
                        collapseChromeFactor = collapseChromeFactor,
                        handoffPhase = collapseHandoffSnap.phase,
                        pillSuppressed = pillSuppressed,
                        stackOwnsOpeningGlass = stackOwnsOpeningGlass,
                        forwardAwaitSuppress = forwardAwaitSuppress,
                    )
                val morphStaticParamsState = rememberUpdatedState(morphStaticParams)
                val localViewState = rememberUpdatedState(localView)
                val computedMorphFrameState =
                    remember {
                        derivedStateOf {
                            evaluateMorphFrame(
                                frame = morphFrameState.value,
                                params = morphStaticParamsState.value,
                                localView = localViewState.value,
                                hostLocOnScreen = hostLocOnScreen,
                                morphContinuity = morphContinuity,
                                hostGeomHolder = hostGeomHolder,
                            )
                        }
                    }
                syncHostGeomBeforePainted[0] = { computedMorphFrameState.value }

                // Cap nhat touchable bottom chi tai cac moc chuyen trang thai (mo / dong / settle),
                // KHONG goi reportExpandedContentBottom moi frame luc collapse morph.
                LaunchedEffect(
                    isExpanded,
                    pillSuppressed,
                    isFadingOutCard,
                    targetHostTopPx,
                    layoutTargetHPx,
                    capturedPill.top,
                    pillHPx,
                    statusBarHeightPx,
                ) {
                    if (isFadingOutCard) return@LaunchedEffect
                    when {
                        isExpanded -> {
                            val bottom = (targetHostTopPx + layoutTargetHPx).roundToInt().coerceAtLeast(0)
                            onBottom.value(bottom)
                        }
                        pillSuppressed -> {
                            localView.rootView.getLocationOnScreen(hostLocOnScreen)
                            val (_, pillTopHost) =
                                statusBarMorphPillOriginInHostPx(
                                    capturedLeft = capturedPill.left,
                                    capturedTop = capturedPill.top,
                                    capturedWidth = capturedPill.width,
                                    capturedHeight = capturedPill.height,
                                    hostLocX = hostLocOnScreen[0],
                                    hostLocY = hostLocOnScreen[1],
                                    topPadPx = topPadPx.roundToInt(),
                                )
                            val collapseBottom =
                                (pillTopHost + pillHPx).coerceAtLeast(statusBarHeightPx).coerceAtLeast(0)
                            onBottom.value(collapseBottom)
                        }
                        else -> onBottom.value(0)
                    }
                }

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
                    remember(isAlertOverlay, notifAlerts, chipState?.allEvents, expandOnlyEventId) {
                        buildSet {
                            if (isAlertOverlay) {
                                notifAlerts.forEach { add(statusBarStackEventOwnerKey(it.id)) }
                            } else {
                                chipState?.allEvents?.forEach { ev ->
                                    if (ev !is IslandEvent.AospChip) {
                                        add(statusBarStackEventOwnerKey(ev.id))
                                    }
                                }
                                expandOnlyEventId?.let { add(statusBarStackEventOwnerKey(it)) }
                            }
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

                // First-frame handoff: pill that chi an khi shell XY+WH khop @p≈0.
                // Khong key theo morphProgress/visualShellW de khong cancel/relaunch 120Hz.
                LaunchedEffect(
                    isExpanded,
                    pillSuppressed,
                    hasValidPillRect,
                    pillWPx,
                    pillHPx,
                    capturedPill.left,
                    capturedPill.top,
                ) {
                    if (!isExpanded || pillSuppressed || !hasValidPillRect) return@LaunchedEffect
                    withFrameNanos { }
                    snapshotFlow { computedMorphFrameState.value to morphRunning }
                        .first { (f, running) ->
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
                            val aligned =
                                statusBarMorphPillShellAlignedRect(
                                    pillLeftPx = pillLeftHost.toFloat(),
                                    pillTopPx = pillTopHost.toFloat(),
                                    pillWPx = pillWPx.toFloat(),
                                    pillHPx = pillHPx.toFloat(),
                                    visualLeftPx = f.visualLeftPx,
                                    visualTopPx = f.visualTopPx,
                                    visualWPx = f.visualShellWPx,
                                    visualHPx = f.visualShellHPx,
                                    progress = f.progress,
                                )
                            val confirm =
                                statusBarMorphShouldConfirmPillSuppressed(
                                    hasValidPillRect = true,
                                    shellAlignedRect = aligned,
                                    morphRunning = running,
                                    morphProgress = f.progress,
                                )
                            if (confirm) {
                                viewModel.statusBarExpansion.confirmPillSuppressed()
                            }
                            confirm
                        }
                }

                // Circle pill chrome (vien+icon): tach sang leaf composable, khong recompose OverlayContent.
                if (useCircleStyle && pillEvent != null && (morphRunning || !morphExpandedAtRest || collapseHandbackHold)) {
                    MorphCircleRingChromeHost(
                        event = pillEvent,
                        accent = pillAccent,
                        contentColor = pillContentColor,
                        progress = circleProgressFrozen,
                        computedMorphFrameState = computedMorphFrameState,
                    )
                }
                // Host morph: dich chuyen + scale hoan toan trong graphicsLayer (draw phase),
                // bo .offset va per-frame onGloballyPositioned de cat 100% onLayout/onMeasure 120Hz.
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                val f = computedMorphFrameState.value
                                translationX = f.hostLeftPx
                                translationY = f.hostTopPx
                                scaleX = f.sx
                                scaleY = f.sy
                                transformOrigin = TransformOrigin(0f, 0f)
                                clip = false
                            }
                            .requiredSize(width = targetWDp, height = targetHDp),
                ) {
                    // Persistent shared glass + Row pill FG (cung host).
                    // Host van requiredSize(layout); Circle ring = sibling ngoai.
                    val pillWDp = with(density) { pillWPx.toDp() }
                    val pillHDp = with(density) { pillHPx.toDp() }
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
                        // Shared morph glass tach sang leaf 1-node (khong recompose cay expanded).
                        if (keepSharedBlurSubtree) {
                            MorphSharedGlassPlate(
                                computedMorphFrameState = computedMorphFrameState,
                                paintMorphGlassAlpha = paintMorphGlassAlpha,
                                cardFadeOutAlpha = cardFadeOutAlpha,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        // Row pill foreground: giu mounted suot morph (tranh spike mount/measure giua chung),
                        // dich + scale + alpha hoan toan trong graphicsLayer.
                        if (
                            !useCircleStyle &&
                                morphPresentation != null &&
                                (morphRunning || !morphExpandedAtRest || collapseHandbackHold)
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(AbsoluteAlignment.TopLeft)
                                        .graphicsLayer {
                                            val f = computedMorphFrameState.value
                                            translationX = f.dummyLocalOffsetXPx
                                            translationY = f.dummyLocalOffsetYPx
                                            scaleX = f.dummySx
                                            scaleY = f.dummySy
                                            transformOrigin =
                                                TransformOrigin(0f, 0f)
                                            alpha = f.pillChromeAlpha
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
                    // Measure unbounded TRUOC alpha — graphicsLayer alpha doc luc draw (0 recompose).
                    val yieldNativeGlass = glassHandoff.yieldNativeGlassToShared
                    val expandedTreeBlurAlpha =
                        if (yieldNativeGlass) {
                            0f
                        } else {
                            stackExpandedContentTreeBlurAlpha(
                                expandedBlurAlpha =
                                    expandedBlurAlphaState.value * cardFadeOutAlpha.value,
                                openLocalBlurFactor = cardBlurFactor,
                                yieldNativeGlassToShared = false,
                            )
                        }
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
                                .graphicsLayer {
                                    alpha = computedMorphFrameState.value.expandContentAlpha
                                },
                    ) {
                        CompositionLocalProvider(
                            LocalBlurAlpha provides expandedTreeBlurAlpha,
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
                            LocalStatusBarStackDragYReporter provides stackDragYReporter,
                            LocalStatusBarStackDragSettlingReporter provides
                                stackDragSettlingReporter,
                            LocalStatusBarStackDragPhaseReporter provides
                                stackDragPhaseReporter,
                        ) {
                            // Idle p1 shell=0 -> yield false -> PrimaryCard/Media/Alert frost native.
                            val onAlertBounds = rememberUpdatedState(onAlertContentBoundsPx)
                            if (isAlertOverlay) {
                                StackedAlertPanels(
                                    alerts = notifAlerts,
                                    pinnedAlertKey = pinnedAlertKey,
                                    interactor = viewModel.interactor,
                                    onDismissTop = {
                                        viewModel.interactor.dismissNotificationAlert()
                                    },
                                    onSwipeDismissTop = {
                                        viewModel.interactor.dismissNotificationAlertImmediate()
                                    },
                                    onAlertCardBoundsPx = { left, top, right, bottom ->
                                        onAlertBounds.value(left, top, right, bottom)
                                    },
                                    stackGesturesEnabled =
                                        isExpanded && morphExpandedAtRest && !isFadingOutCard,
                                    initiallyCompact = compactNotifs,
                                )
                            } else {
                                chipState?.let { state ->
                                    val filtered =
                                        remember(state.allEvents) {
                                            state.allEvents.filter {
                                                it !is IslandEvent.AospChip
                                            }
                                        }
                                    if (filtered.isEmpty()) return@let
                                    ExpandedIslandContent(
                                        events = filtered,
                                        interactor = viewModel.interactor,
                                        stackGesturesEnabled =
                                            isExpanded && morphExpandedAtRest && !isFadingOutCard,
                                        onCollapse = onCollapseExpanded,
                                        expandOnlyEventId = expandOnlyEventId,
                                        pinnedEventId = state.event.id,
                                        onExpandOnlyEventIdChange = onExpandOnlyIdChange,
                                        // Commit stack: sync expandOnly + frozen -> B
                                        // (event.id, khong index cu). Atomic sau pin.
                                        onConfirmedStackEventId = onConfirmedStackId,
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
}

private fun isChipOwnerEndedAuthoritative(state: AxDynamicBarChipState?): Boolean =
    statusBarCollapseHandoffOwnerEndedAuthoritative(
        chipMissing = state == null,
        onlyAospOrEmpty =
            state == null ||
                (state.notificationAlert == null &&
                    (state.allEvents.isEmpty() ||
                        state.allEvents.all { it is IslandEvent.AospChip })),
    )

private fun liveSelectedOwnerIdOf(state: AxDynamicBarChipState?): String? =
    statusBarCollapseHandoffLiveSelectedOwnerId(
        alertEventId = state?.notificationAlert?.id,
        primaryEventId = state?.event?.id,
    )

/** Holder thuong (khong Snapshot) giu geometry host/target cho paintedCardBoundsReporter. */
private class MorphHostGeometryHolder {
    var sx: Float = 1f
    var sy: Float = 1f
    var overlayRootWinX: Float = 0f
    var overlayRootWinY: Float = 0f
    var hostLeftPx: Float = 0f
    var hostTopPx: Float = 0f
    var targetHostLeftPx: Float = 0f
    var targetHostTopPx: Float = 0f
    var targetHostPlacementReady: Boolean = false
    var morphFrozenValid: Boolean = false

    fun reset() {
        sx = 1f
        sy = 1f
        hostLeftPx = 0f
        hostTopPx = 0f
        targetHostLeftPx = 0f
        targetHostTopPx = 0f
        targetHostPlacementReady = false
        morphFrozenValid = false
    }
}

private fun ownedRectNearlyEqual(
    a: StatusBarOwnedPaintedRectPx?,
    b: StatusBarOwnedPaintedRectPx?,
): Boolean {
    if (a === b) return true
    if (a == null || b == null) return false
    return a.ownerKey == b.ownerKey &&
        a.contextToken == b.contextToken &&
        abs(a.leftPx - b.leftPx) <= 0.25f &&
        abs(a.topPx - b.topPx) <= 0.25f &&
        abs(a.rightPx - b.rightPx) <= 0.25f &&
        abs(a.bottomPx - b.bottomPx) <= 0.25f
}

/** Tham so on dinh trong suot 1 luot morph (chi doi o moc start/settle/owner). */
private data class MorphStaticParams(
    val maxWPx: Int,
    val topPadPx: Float,
    val pillWPx: Int,
    val pillHPx: Int,
    val capturedLeft: Int,
    val capturedTop: Int,
    val capturedWidth: Int,
    val capturedHeight: Int,
    val morphVisualTargetWPx: Int,
    val morphVisualTargetHPx: Int,
    val sidePadPx: Int,
    val topInsetPx: Int,
    val selectedPaintedLeftPx: Float,
    val selectedPaintedTopPx: Float,
    val cardCornerPx: Float,
    val morphMotionDensityDpi: Float,
    val widthGatePx: Float,
    val morphRunning: Boolean,
    val morphClosing: Boolean,
    val pillAccent: Color,
    val pillSourceTint: Color,
    val panelEndpointFallback: Color,
    val panelEndpointTint: Color,
    val useCircleStyle: Boolean,
    val collapseChromeFactor: Float,
    val handoffPhase: StatusBarCollapseHandoffPhase,
    val pillSuppressed: Boolean,
    val stackOwnsOpeningGlass: Boolean,
    val forwardAwaitSuppress: Boolean,
)

/** Ket qua tinh toan 1 lan/frame — dung chung cho graphicsLayer va leaf glass. */
private data class MorphComputedFrame(
    val progress: Float,
    val sx: Float,
    val sy: Float,
    val hostLeftPx: Float,
    val hostTopPx: Float,
    val visualLeftPx: Float,
    val visualTopPx: Float,
    val visualShellWPx: Float,
    val visualShellHPx: Float,
    val shellLocalRxPx: Float,
    val shellLocalRyPx: Float,
    val expandContentAlpha: Float,
    val dummyLocalOffsetXPx: Float,
    val dummyLocalOffsetYPx: Float,
    val dummySx: Float,
    val dummySy: Float,
    val dummyContentScale: Float,
    val pillChromeAlpha: Float,
    val ringChromeAlpha: Float,
    val ringDiameterPx: Float,
    val shellFallback: Color,
    val shellTint: Color,
)

private fun evaluateMorphFrame(
    frame: StatusBarMorphRunFrame,
    params: MorphStaticParams,
    localView: View,
    hostLocOnScreen: IntArray,
    morphContinuity: StatusBarMorphContinuityOwner,
    hostGeomHolder: MorphHostGeometryHolder,
): MorphComputedFrame {
    val morphProgress = frame.geometry
    val morphLinearClamped = frame.linear.coerceIn(0f, 1f)
    val geom =
        computeKeyguardMorphGeom(
            pRaw = morphProgress,
            pillWPx = params.pillWPx.toFloat(),
            pillHPx = params.pillHPx.toFloat(),
            targetWPx = params.morphVisualTargetWPx.toFloat(),
            targetHPx = params.morphVisualTargetHPx.toFloat(),
        )
    val sx = geom.sx.coerceAtLeast(MorphScaleFloor)
    val sy = geom.sy.coerceAtLeast(MorphScaleFloor)
    val cornerP = statusBarMorphGeometryCornerProgress(geom.pCorner)
    val visualCornerPx =
        statusBarMorphVisualCornerPx(
            expandedLinearT = morphLinearClamped,
            cardCornerPx = params.cardCornerPx,
            densityDpi = params.morphMotionDensityDpi,
        )
    val shellCorners =
        morphLocalCornerRadii(
            visualCornerPx = visualCornerPx,
            sx = sx,
            sy = sy,
            targetHPx = params.morphVisualTargetHPx.toFloat(),
            targetWPx = params.morphVisualTargetWPx.toFloat(),
        )
    val (visualShellWPx, visualShellHPx) =
        statusBarMorphVisualShellSizePx(
            targetWPx = params.morphVisualTargetWPx.toFloat(),
            targetHPx = params.morphVisualTargetHPx.toFloat(),
            sx = sx,
            sy = sy,
        )
    if (!params.morphRunning && morphLinearClamped <= 0.01f && !params.morphClosing) {
        morphContinuity.reset()
    }
    val morphVisual =
        morphContinuity.evaluate(
            expandedFraction = morphLinearClamped,
            closing = params.morphClosing,
            measuredWidthPx = visualShellWPx,
            widthGatePx = params.widthGatePx,
            visualTargetWPx = params.morphVisualTargetWPx.toFloat(),
            densityDpi = params.morphMotionDensityDpi,
        )
    val shellFallback =
        statusBarMorphShellFallbackArgb(
            pillAccent = params.pillAccent,
            panelEndpointFallback = params.panelEndpointFallback,
            tintProgress = morphVisual.colorT,
        )
    val shellTint =
        statusBarMorphShellTintArgb(
            pillSourceTint = params.pillSourceTint,
            panelEndpointTint = params.panelEndpointTint,
            tintProgress = morphVisual.colorT,
        )
    val shellChrome = (morphVisual.shellGlass * params.collapseChromeFactor).coerceIn(0f, 1f)
    val (ringChromeRaw, _) =
        statusBarMorphCircleChromeAlphas(
            morphShellAlpha = shellChrome,
            shellLerp = cornerP,
            useCircleStyle = params.useCircleStyle,
        )
    val handoffOwn =
        statusBarCollapseHandoffOwnershipOf(
            phase = params.handoffPhase,
            pillSuppressed = params.pillSuppressed,
            pillFollowAlpha = morphVisual.dummyAlpha,
        )
    val pillChromeAlpha =
        if (params.stackOwnsOpeningGlass || params.forwardAwaitSuppress) 0f
        else statusBarCollapseHandoffDummyFgAlpha(handoffOwn)
    val ringOwn =
        statusBarCollapseHandoffOwnershipOf(
            phase = params.handoffPhase,
            pillSuppressed = params.pillSuppressed,
            pillFollowAlpha = ringChromeRaw,
        )
    val ringChromeAlpha =
        if (params.stackOwnsOpeningGlass || params.forwardAwaitSuppress) 0f
        else statusBarCollapseHandoffDummyFgAlpha(ringOwn)
    val ringDiameterPx =
        statusBarMorphCircleRingVisualDiameterPx(
            visualShellWPx,
            visualShellHPx,
        )
    localView.rootView.getLocationOnScreen(hostLocOnScreen)
    val (originLeft, originTop) =
        statusBarMorphPillOriginInHostPx(
            capturedLeft = params.capturedLeft,
            capturedTop = params.capturedTop,
            capturedWidth = params.capturedWidth,
            capturedHeight = params.capturedHeight,
            hostLocX = hostLocOnScreen[0],
            hostLocY = hostLocOnScreen[1],
            topPadPx = params.topPadPx.roundToInt(),
        )
    val vr =
        statusBarMorphLerpVisualRectPx(
            pOffset = if (morphProgress.isFinite()) morphProgress else 0f,
            pillLeftPx = originLeft.toFloat(),
            pillTopPx = originTop.toFloat(),
            pillWPx = params.pillWPx.toFloat(),
            pillHPx = params.pillHPx.toFloat(),
            visualTargetWPx = params.morphVisualTargetWPx.toFloat(),
            visualTargetHPx = params.morphVisualTargetHPx.toFloat(),
            sx = sx,
            sy = sy,
            maxWidthPx = params.maxWPx.toFloat(),
            targetVisualTopPx = params.topPadPx + params.topInsetPx,
            paintedTargetLeftPx = params.selectedPaintedLeftPx,
            paintedTargetTopPx = params.selectedPaintedTopPx,
        )
    val (hostLeftPx, hostTopPx) =
        statusBarMorphHostOriginFromVisualRectPx(
            visualLeftPx = vr.leftPx,
            visualTopPx = vr.topPx,
            insetLeftPx = params.sidePadPx.toFloat(),
            insetTopPx = params.topInsetPx.toFloat(),
            sx = sx,
            sy = sy,
        )
    // Dong bo holder thuong cho paintedCardBoundsReporter undo host scale.
    hostGeomHolder.sx = sx
    hostGeomHolder.sy = sy
    hostGeomHolder.hostLeftPx = hostLeftPx
    hostGeomHolder.hostTopPx = hostTopPx
    val (dummySx, dummySy) =
        statusBarMorphDummyLayerScale(
            hostSx = sx,
            hostSy = sy,
            dummyScale = morphVisual.dummyScale,
        )
    val dummyLocalOff =
        statusBarMorphDummyContentLocalOffsetPx(
            pillWPx = params.pillWPx.toFloat(),
            pillHPx = params.pillHPx.toFloat(),
            localSx = dummySx,
            localSy = dummySy,
            visualTargetWPx = params.morphVisualTargetWPx.toFloat(),
            visualTargetHPx = params.morphVisualTargetHPx.toFloat(),
        )
    return MorphComputedFrame(
        progress = morphProgress,
        sx = sx,
        sy = sy,
        hostLeftPx = hostLeftPx,
        hostTopPx = hostTopPx,
        visualLeftPx = vr.leftPx,
        visualTopPx = vr.topPx,
        visualShellWPx = visualShellWPx,
        visualShellHPx = visualShellHPx,
        shellLocalRxPx = shellCorners.localRxPx,
        shellLocalRyPx = shellCorners.localRyPx,
        expandContentAlpha = morphVisual.panelAlpha,
        dummyLocalOffsetXPx = dummyLocalOff.xPx,
        dummyLocalOffsetYPx = dummyLocalOff.yPx,
        dummySx = dummySx,
        dummySy = dummySy,
        dummyContentScale = morphVisual.dummyScale,
        pillChromeAlpha = pillChromeAlpha,
        ringChromeAlpha = ringChromeAlpha,
        ringDiameterPx = ringDiameterPx,
        shellFallback = shellFallback,
        shellTint = shellTint,
    )
}

/** Leaf 1-node cap nhat bo goc va mau tint cho shared glass (khong recompose cay expanded). */
@Composable
private fun MorphSharedGlassPlate(
    computedMorphFrameState: State<MorphComputedFrame>,
    paintMorphGlassAlpha: Float,
    cardFadeOutAlpha: Animatable<Float, *>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val f = computedMorphFrameState.value
    val rxDp = with(density) { f.shellLocalRxPx.toDp() }.coerceAtLeast(1.dp)
    val ryDp = with(density) { f.shellLocalRyPx.toDp() }.coerceAtLeast(1.dp)
    Box(
        modifier =
            modifier.islandBlur(
                cornerRadius = rxDp,
                cornerRadiusY = ryDp,
                fallbackColor = f.shellFallback,
                tint = f.shellTint,
                alpha = paintMorphGlassAlpha * cardFadeOutAlpha.value,
                highlightWidth = ChipHighlightWidth,
            ),
    )
}

/** Leaf circle chrome tach khoi OverlayContent — chi chay khi useCircleStyle=true. */
@Composable
private fun MorphCircleRingChromeHost(
    event: IslandEvent,
    accent: Color,
    contentColor: Color,
    progress: Float?,
    computedMorphFrameState: State<MorphComputedFrame>,
) {
    val density = LocalDensity.current
    val f = computedMorphFrameState.value
    if (f.ringChromeAlpha <= 0.01f) return
    val ringDiameterDp = with(density) { f.ringDiameterPx.toDp() }
    val (l, t) =
        statusBarMorphCircleRingVisualTopLeftPx(
            shellLeftPx = f.visualLeftPx,
            shellTopPx = f.visualTopPx,
            visualWPx = f.visualShellWPx,
            visualHPx = f.visualShellHPx,
            diameterPx = f.ringDiameterPx,
        )
    StatusBarMorphCircleRingChrome(
        event = event,
        accent = accent,
        contentColor = contentColor,
        progress = progress,
        alpha = f.ringChromeAlpha,
        iconScale = f.dummyContentScale,
        modifier =
            Modifier
                .graphicsLayer {
                    translationX = l
                    translationY = t
                }
                .requiredSize(ringDiameterDp),
    )
}
