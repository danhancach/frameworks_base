@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
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
import com.android.systemui.axdynamicbar.shared.ExpandedMaxWidth
import com.android.systemui.axdynamicbar.shared.LocalBlurAlpha
import com.android.systemui.axdynamicbar.shared.LockedTargetHeight
import com.android.systemui.axdynamicbar.shared.MorphScaleFloor
import com.android.systemui.axdynamicbar.shared.chipAccentColorFor
import com.android.systemui.axdynamicbar.shared.chipContentColorFor
import com.android.systemui.axdynamicbar.shared.computeKeyguardMorphGeom
import com.android.systemui.axdynamicbar.shared.islandBlur
import com.android.systemui.axdynamicbar.shared.morphLocalCornerRadii
import com.android.systemui.axdynamicbar.shared.resolveStatusBarLockedTargetHeight
import com.android.systemui.axdynamicbar.shared.statusBarMorphAtomicEndpointSnapshot
import com.android.systemui.axdynamicbar.shared.statusBarMorphCardContentAlpha
import com.android.systemui.axdynamicbar.shared.statusBarMorphCardSurfaceBlurFactor
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleChromeAlphas
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleRingVisualDiameterPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCircleRingVisualTopLeftPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphCollapseOverlayChromeFactor
import com.android.systemui.axdynamicbar.shared.statusBarMorphDummyContentAlpha
import com.android.systemui.axdynamicbar.shared.statusBarMorphEndpointReady
import com.android.systemui.axdynamicbar.shared.statusBarMorphHostOriginFromVisualRectPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphInverseContentScale
import com.android.systemui.axdynamicbar.shared.statusBarMorphLerpVisualRectPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphPillOriginInHostPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphPillShellAlignedRect
import com.android.systemui.axdynamicbar.shared.statusBarMorphRunEndpointPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphShellGlassAlpha
import com.android.systemui.axdynamicbar.shared.statusBarMorphShellTintProgress
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldConfirmPillSuppressed
import com.android.systemui.axdynamicbar.shared.statusBarMorphShouldTargetExpanded
import com.android.systemui.axdynamicbar.shared.statusBarMorphStageTimeoutEndpointPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphTargetHeightPx
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedBottomPadDp
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedSidePadDp
import com.android.systemui.axdynamicbar.shared.StatusBarExpandedTopPadDp
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualShellSizePx
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualTargetHPx
import com.android.systemui.axdynamicbar.shared.statusBarMorphVisualTargetWPx
import com.android.systemui.axdynamicbar.shared.statusBarOverlayHideDelayMs
import com.android.systemui.axdynamicbar.shared.statusBarShouldUpdateMorphEndpoint
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

/** Samsung Now Bar OA root — chi motion; khong copy size. */
private val StatusBarMorphEasing = CubicBezierEasing(0.4f, 1.22f, 0.47f, 0.99f)
private const val StatusBarExpandMs = 500
private const val StatusBarCollapseMs = 600
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

    /** Cap nhat day the tu Compose — requestLayout de InternalInsets tinh lai. */
    private fun reportExpandedContentBottom(bottomPx: Int) {
        if (bottomPx == expandedContentBottomPx) return
        expandedContentBottomPx = bottomPx
        ensureMainThread {
            overlayView?.requestLayout()
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
        ensureMainThread {
            overlayView?.requestLayout()
        }
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
                    if (replyCaptureActive) v.requestLayout() // recompute touchable region
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
     * (set full man truoc day da chan home). Goi requestLayout de insets cap nhat.
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
    val morphProgress by
        morphTransition.animateFloat(
            transitionSpec = {
                if (targetState) {
                    tween(StatusBarExpandMs, easing = StatusBarMorphEasing)
                } else {
                    tween(StatusBarCollapseMs, easing = StatusBarMorphEasing)
                }
            },
            label = "sb_morph_progress",
        ) { expanded -> if (expanded) 1f else 0f }

    var hadMorphExpand by remember { mutableStateOf(false) }
    // H dich khoa theo content owner — KHONG fallback 220.dp (tranh first-frame pop).
    var lockedTargetHeight by remember { mutableStateOf(LockedTargetHeight()) }
    // W dich do natural (StackedExpandedPanels envelope); 0 = chua do.
    var lockedTargetWidthPx by remember { mutableStateOf(0) }
    // Envelope H cuoi (expanded) — bridge khi owner switch; reset chi luc collapse settle.
    var lastExpandedEnvelopeHPx by remember { mutableStateOf(0) }
    // Endpoint H/W freeze cho MOT morph run — tranh retarget mid (F48).
    var morphRunTargetHPx by remember { mutableStateOf(0) }
    var morphRunTargetWPx by remember { mutableStateOf(0) }
    // Sau first composition/layout moi drive target expand.
    var morphFirstFramePassed by remember { mutableStateOf(false) }
    var morphAllowFallbackRect by remember { mutableStateOf(false) }

    val morphClosing =
        !morphTransition.targetState &&
            (morphTransition.currentState || abs(morphProgress) > 0.01f)
    val morphProgressClamped = morphProgress.coerceIn(0f, 1f)
    // Card alpha StatusBar: cung cua so handoff voi dummy (khong morphExpandContentAlpha keyguard).
    val expandContentAlpha =
        statusBarMorphCardContentAlpha(morphProgressClamped, morphClosing)
    // Shell glass ease (quadratic) — bot double blur vs PrimaryCard; p1 = 0 native.
    val morphShellAlpha =
        statusBarMorphShellGlassAlpha(morphProgressClamped, morphClosing)
    // Dummy content di cung overlay; crossfade overlapping/adjacent [0.35..0.55].
    val dummyContentAlpha =
        statusBarMorphDummyContentAlpha(morphProgressClamped, morphClosing)
    val isMorphing = abs(morphProgress) > 0.01f
    val morphRunning = morphTransition.isRunning
    val morphExpandedAtRest =
        morphTransition.currentState &&
            morphTransition.targetState &&
            !morphRunning &&
            abs(morphProgress - 1f) <= 0.01f
    // Hold overlay host sau settle: dummy chrome=1 den live OnDraw ack (task-043).
    // Controller tach timer khoi settle effect (pillSuppressed key cancel delay — bug 028).
    val collapseHandbackCtrl = remember { CollapseHandbackHoldController() }
    var collapseHandbackHold by remember { mutableStateOf(false) }
    var collapseHandbackTimerGen by remember { mutableIntStateOf(0) }
    var collapseLiveDrawnAcked by remember { mutableStateOf(false) }
    val collapseChromeFactor =
        statusBarMorphCollapseOverlayChromeFactor(
            collapseHandbackHold = collapseHandbackHold,
            pillSuppressed = pillSuppressed,
            liveDrawnAcked = collapseLiveDrawnAcked,
        )
    val showMorphHost =
        pillSuppressed || isExpanded || isMorphing || collapseHandbackHold
    val hasValidPillRect = capturedPill.width > 0 && capturedPill.height > 0
    val expandCornerTarget = dimensionResource(R.dimen.notification_corner_radius)
    // Presentation freeze (expand) — fallback live chip neu chua freeze (edge).
    val morphPresentation =
        frozenPresentation
            ?: chipState?.let { st ->
                val display = st.notificationAlert ?: st.event
                statusBarPillPresentationOf(
                    event = display,
                    isAlert = st.notificationAlert != null,
                    chipStyle = chipStyle,
                    generation = layoutGen,
                    carrierName = null,
                )
            }
    // Shell p=0: Row = accent glass; Circle = RING+icon (cung useCircleStyle chip).
    val pillEvent = morphPresentation?.event
    val pillAccent: Color = pillEvent?.let { chipAccentColorFor(it) } ?: CardBg
    val pillContentColor: Color =
        pillEvent?.let { chipContentColorFor(it) } ?: Color.White
    val useCircleStyle = morphPresentation?.useCircleStyle == true
    // Progress freeze trong snapshot — tranh morph tu chay media ring rieng.
    val circleProgressFrozen =
        if (useCircleStyle) morphPresentation?.progress else null

    // Owner on dinh: expand-only id hoac pinned event — doi → invalidate H khoa.
    val contentOwnerKey =
        expandOnlyEventId?.let { "event:$it" }
            ?: chipState?.event?.id?.let { "event:$it" }
            ?: "stack"

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

    // Settle exactly-once @p~0: bat hold + notify (sync). KHONG delay o day —
    // notify → pillSuppressed=false cancel effect; chrome van 1 den live ack (043).
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
            viewModel.statusBarExpansion.abortCollapseHandbackStatic()
            return@LaunchedEffect
        }
        val morphAtRest =
            !morphTransition.currentState &&
                !morphTransition.targetState &&
                !morphRunning &&
                abs(morphProgress) <= 0.001f
        if (pillSuppressed && morphAtRest) {
            val ownerId =
                morphPresentation?.ownerId
                    ?: frozenPresentation?.ownerId
                    ?: chipState?.let { st -> (st.notificationAlert ?: st.event).id }
            val start =
                collapseHandbackCtrl.beginSettleHandback(
                    layoutGen = layoutGen,
                    ownerId = ownerId,
                )
            if (!start.shouldNotifySettle) return@LaunchedEffect
            // Phase1-2: dummy chrome=1; clear latch cu; unsuppress real static — doi OnDraw ack.
            viewModel.statusBarExpansion.clearCollapseHandbackSignals()
            collapseHandbackHold = true
            collapseLiveDrawnAcked = false
            collapseHandbackTimerGen = start.holdGeneration
            viewModel.statusBarExpansion.notifyCollapseSettled(layoutGen)
            hadMorphExpand = false
            lockedTargetHeight = LockedTargetHeight()
            lockedTargetWidthPx = 0
            lastExpandedEnvelopeHPx = 0
            morphRunTargetHPx = 0
            morphRunTargetWPx = 0
            onExpandedContentBottomPx(0)
        }
    }

    // Ack + timeout + release host — key holdGeneration / isExpanded, KHONG pillSuppressed.
    // 044: StateFlow latch + apply early truoc collect (khong drop signal-before-subscribe).
    // Stale clear: clearHoldIfGeneration(gen) no-op khi gen da doi (settle moi / reverse).
    LaunchedEffect(collapseHandbackTimerGen, isExpanded) {
        if (collapseHandbackTimerGen == 0) return@LaunchedEffect
        if (isExpanded) {
            collapseHandbackCtrl.onExpandedOrReverse()
            collapseHandbackHold = false
            collapseLiveDrawnAcked = false
            viewModel.statusBarExpansion.abortCollapseHandbackStatic()
            return@LaunchedEffect
        }
        val gen = collapseHandbackTimerGen
        val settledLayoutGen = collapseHandbackCtrl.expectedLayoutGen
        fun applyDrawn(sig: AxDynamicBarStatusBarExpansion.CollapseHandbackDrawnSignal?): Boolean {
            if (sig == null) return false
            return collapseHandbackCtrl.acknowledgeLiveDrawn(
                holdGeneration = gen,
                layoutGen = sig.layoutGeneration,
                ownerId = sig.ownerId,
            )
        }
        // Early signal (Chip emit truoc collector) — doc StateFlow.value truoc collect.
        if (applyDrawn(viewModel.statusBarExpansion.collapseHandbackDrawn.value)) {
            collapseLiveDrawnAcked = true
        }
        val ackJob =
            launch {
                viewModel.statusBarExpansion.collapseHandbackDrawn.collect { sig ->
                    if (applyDrawn(sig)) {
                        collapseLiveDrawnAcked = true
                    }
                }
            }
        try {
            // Doi OnDraw ack; timeout = hide delay cu (50ms) — khong stuck overlay.
            withTimeoutOrNull(statusBarOverlayHideDelayMs(alertExitGrace = false)) {
                snapshotFlow { collapseLiveDrawnAcked }.first { it }
            }
            if (!collapseLiveDrawnAcked) {
                if (collapseHandbackCtrl.acknowledgeLiveDrawnTimeout(gen)) {
                    collapseLiveDrawnAcked = true
                }
            }
            // Phase4: chrome=0; 1 frame commit; confirm hide → Chip phase5; roi release host.
            withFrameNanos { }
            collapseHandbackCtrl.confirmOverlayHidden(gen)
            // Luon signal Chip (ke ca confirm no-op stale) — tranh stuck staticMode.
            viewModel.statusBarExpansion.reportCollapseHandbackOverlayHidden(
                holdGeneration = gen,
                layoutGeneration = settledLayoutGen,
            )
            withFrameNanos { }
            if (collapseHandbackCtrl.clearHoldIfGeneration(gen)) {
                collapseHandbackHold = false
                collapseLiveDrawnAcked = false
            }
        } finally {
            ackJob.cancel()
        }
    }

    LaunchedEffect(showNotif) {
        notifVisible.targetState = showNotif
        if (!showNotif) onAlertContentBoundsPx(0, 0, 0, 0)
    }

    LaunchedEffect(chipState) {
        val filtered = chipState?.allEvents?.filter { it !is IslandEvent.AospChip }
        if (filtered.isNullOrEmpty() && isExpanded) {
            viewModel.statusBarExpansion.collapse()
        }
    }

    val origin = TransformOrigin(0.5f, 0f)
    val motionScheme = MaterialTheme.motionScheme
    val localView = LocalView.current
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
                    // Owner switch: bridge lastExpanded — KHONG bounce pillH khi van expanded.
                    val liveMorphTargetHPx =
                        statusBarMorphTargetHeightPx(
                            lockedTargetHeight,
                            contentOwnerKey,
                            lastExpandedEnvelopeHPx,
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
                    val sidePadPx =
                        with(density) { StatusBarExpandedSidePadDp.dp.roundToPx() }
                    val topInsetPx =
                        with(density) { StatusBarExpandedTopPadDp.dp.roundToPx() }
                    val bottomPadPx =
                        with(density) { StatusBarExpandedBottomPadDp.dp.roundToPx() }
                    val morphVisualTargetHPx =
                        statusBarMorphVisualTargetHPx(
                            layoutTargetHPx = layoutTargetHPx,
                            topPadPx = topInsetPx,
                            bottomPadPx = bottomPadPx,
                            pillHPx = pillHPx,
                        )
                    val morphVisualTargetWPx =
                        statusBarMorphVisualTargetWPx(
                            layoutTargetWPx = layoutTargetWPx,
                            sidePadPx = sidePadPx,
                            pillWPx = pillWPx,
                        )
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
                    val targetWDp = with(density) { layoutTargetWPx.toDp() }
                    val targetHDp = with(density) { layoutTargetHPx.toDp() }
                    val morphVisualTargetWDp =
                        with(density) { morphVisualTargetWPx.toDp() }
                    val morphVisualTargetHDp =
                        with(density) { morphVisualTargetHPx.toDp() }
                    // Corner pill (chipCorner = H/2) → card — shell p=0 khop glass.
                    val pillCornerPx = pillHPx * 0.5f
                    val cardCornerPx = with(density) { expandCornerTarget.toPx() }
                    val visualCornerPx =
                        pillCornerPx + (cardCornerPx - pillCornerPx) * geom.pCorner
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
                    // Tint shell: smoothstep ve CardBg — bot abrupt vs PrimaryCard.
                    val shellLerp = geom.pCorner.coerceIn(0f, 1f)
                    val tintProgress = statusBarMorphShellTintProgress(shellLerp)
                    val shellFallback = lerp(pillAccent, CardBg, tintProgress)
                    val shellTint =
                        lerp(
                            pillAccent.copy(alpha = AlphaGlass),
                            CardBg.copy(alpha = AlphaGlass),
                            tintProgress,
                        )
                    // Chrome alpha: shell glass ease * collapse handback factor.
                    // KHONG * expandedBlurAlpha (ramp=0 blank sau suppress).
                    val shellChrome =
                        (morphShellAlpha * collapseChromeFactor).coerceIn(0f, 1f)
                    val (ringChromeAlpha, glassShellAlpha) =
                        statusBarMorphCircleChromeAlphas(
                            morphShellAlpha = shellChrome,
                            shellLerp = shellLerp,
                            useCircleStyle = useCircleStyle,
                        )
                    val dummyPaintAlpha =
                        (dummyContentAlpha * collapseChromeFactor).coerceIn(0f, 1f)
                    val cardBlurFactor =
                        statusBarMorphCardSurfaceBlurFactor(
                            cardContentAlpha = expandContentAlpha,
                            shellGlassAlpha = glassShellAlpha,
                        )
                    val (visualShellWPx, visualShellHPx) =
                        statusBarMorphVisualShellSizePx(
                            targetWPx = morphVisualTargetWPx.toFloat(),
                            targetHPx = morphVisualTargetHPx.toFloat(),
                            sx = sx,
                            sy = sy,
                        )
                    // Ring ve VUONG visual (grow isotropic = min(shellW,shellH)); sibling
                    // NGOAI graphicsLayer scale — p0 = 28×28, khong ellipse targetW×targetH.
                    val ringDiameterPx =
                        statusBarMorphCircleRingVisualDiameterPx(
                            visualShellWPx,
                            visualShellHPx,
                        )
                    val ringDiameterDp = with(density) { ringDiameterPx.toDp() }

                    fun lockNaturalSize(wPx: Int, hPx: Int) {
                        // Chi khoa khi natural H > pill — wrapContent unbounded, khong fillMaxSize.
                        // Guard zero/pill transient + feedback cycle (resolve no-op neu ==).
                        if (hPx <= pillHPx) return
                        // requiredWidth(envelope): W do phai ~ envelope (tru owner retarget).
                        if (wPx < envelopeWPx - 2 && wPx < targetWPx - 2) return
                        val next =
                            resolveStatusBarLockedTargetHeight(
                                lockedTargetHeight,
                                contentOwnerKey,
                                hPx,
                                pillHPx,
                            )
                        if (next != lockedTargetHeight) {
                            lockedTargetHeight = next
                            // Owner moi / follow H: chot W theo phep do natural.
                            if (wPx > 0) lockedTargetWidthPx = wPx
                        } else if (lockedTargetWidthPx == 0 && wPx > 0) {
                            lockedTargetWidthPx = wPx
                        }
                        // Bridge envelope cho owner switch sau nay; giu qua interrupted reopen.
                        if (next.heightPx > pillHPx) {
                            lastExpandedEnvelopeHPx = next.heightPx
                        }
                        // Freeze H+W atomic 1 lan / morph run TRUOC khi bat motion; at-rest follow.
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

                    // Visual rect SCREEN: pill → card painted; host bu nested inset*scale.
                    // Thieu capture: neo 0/topPad — KHONG (maxW-pillW)/2 (reopen center bug).
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
                                // Card painted top = SB inset + SpaceXxs (khong top=0 host).
                                targetVisualTopPx = topPadPx + topInsetPx,
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

                    // Circle ring: sibling NGOAI host scale — neo CUNG visualRect (isotropic).
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
                    // Host morph: scale anisotropic + glass shell (Row / mid circle) + content.
                    // Fully expanded: shellAlpha=0 — PrimaryCard/Media/Timer blur goc, khong
                    // LocalKeyguardMorphHostFrame / CardBg vinh vien / double blur.
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
                                    val pos = coords.positionInWindow()
                                    val bottom =
                                        (pos.y + coords.size.height * sy)
                                            .toInt()
                                            .coerceAtLeast(0)
                                    onBottom.value(bottom)
                                },
                    ) {
                        // Temp glass + content-bearing dummy: inner inset + morphVisual W/H.
                        // Host van requiredSize(layout); pad duoi/trai/phai transparent touch.
                        // Ring circle = sibling ngoai — Row dummy nam TRONG shell, inverse scale.
                        if (glassShellAlpha > 0.01f ||
                            (!useCircleStyle && dummyPaintAlpha > 0.01f)
                        ) {
                            val pillWDp = with(density) { pillWPx.toDp() }
                            val pillHDp = with(density) { pillHPx.toDp() }
                            val (invSx, invSy) = statusBarMorphInverseContentScale(sx, sy)
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
                                        ),
                            ) {
                                if (glassShellAlpha > 0.01f) {
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
                                                    alpha = glassShellAlpha,
                                                    highlightWidth = ChipHighlightWidth,
                                                ),
                                    )
                                }
                                // Row dummy: noi dung giong pill that, bay trong shell.
                                // Inverse sx/sy — text/icon khong stretch anisotropic.
                                // Circle: StatusBarMorphCircleRingChrome sibling (task-013).
                                if (
                                    !useCircleStyle &&
                                        dummyPaintAlpha > 0.01f &&
                                        morphPresentation != null
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopStart)
                                                .graphicsLayer {
                                                    alpha = dummyPaintAlpha
                                                    scaleX = invSx
                                                    scaleY = invSy
                                                    transformOrigin =
                                                        TransformOrigin(0f, 0f)
                                                }
                                                .requiredSize(
                                                    width = pillWDp,
                                                    height = pillHDp,
                                                ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        StatusBarPillBody(
                                            presentation = morphPresentation,
                                            includeBlur = false,
                                            includeProgress = true,
                                            staticMode = true,
                                            blurAlpha = 1f,
                                        )
                                    }
                                }
                            }
                        }
                        // Measure unbounded TRUOC alpha — graphicsLayer alpha khong chan measure.
                        // Nested LocalBlurAlpha: giam blur card khi shell glass con (no double).
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
                            ) {
                                // LocalKeyguardMorphHostFrame = false (mac dinh):
                                // PrimaryCard/Media/Timer blur native @p1 (shell=0).
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
