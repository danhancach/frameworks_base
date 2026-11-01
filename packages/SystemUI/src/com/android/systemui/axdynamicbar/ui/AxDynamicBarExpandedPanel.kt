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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState

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
import com.android.systemui.axdynamicbar.shared.LocalBlurAlpha
import com.android.systemui.axdynamicbar.ui.compose.ExpandedIslandContent
import com.android.systemui.axdynamicbar.ui.compose.StackedAlertPanels
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import android.view.WindowInsets

private const val EXIT_ANIM_DURATION = 300L

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
                // Expanded island: chi vung the (+ slack keo) — phan duoi pass-through home.
                viewModel.isExpanded.value -> {
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

        val needsOverlay =
            combine(
                viewModel.isExpanded,
                viewModel.interactor.uiState.map { it.notificationAlert },
                viewModel.isOnKeyguard,
            ) { expanded, alert, onKeyguard ->
                !onKeyguard && (expanded || alert != null)
            }

        needsOverlay
            .onEach { needed ->
                if (needed) {
                    hideOverlayJob?.cancel()
                    hideOverlayJob = null
                    showOverlay()
                } else {
                    hideOverlayJob?.cancel()
                    hideOverlayJob =
                        applicationScope.launch {
                            delay(400)
                            hideOverlay()
                        }
                }
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
        val hasAlert = viewModel.interactor.uiState.value.notificationAlert != null
        alertOverlayActive = hasAlert
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            if (isCurrentlyExpanded || hasAlert) 0
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        // MATCH_PARENT khi expand/alert: translationY stack khong bi WRAP_CONTENT cat ~1/4 man.
        val overlayHeight =
            if (isCurrentlyExpanded || hasAlert) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
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
        if (expanded) {
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
                    if (alertOverlayActive || replyCaptureActive) return@Runnable
                    val p = v.layoutParams as? WindowManager.LayoutParams ?: return@Runnable
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                    windowManager.updateViewLayout(v, p)
                }
                shrinkRunnable = runnable
                mainHandler.postDelayed(runnable, EXIT_ANIM_DURATION)
            }
        }
    }

    /** Alert hien/an: doi height MATCH_PARENT ↔ WRAP_CONTENT (khong cat the keo). */
    private fun syncAlertOverlayHeight(hasAlert: Boolean) {
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (viewModel.isExpanded.value || replyCaptureActive) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.fitInsetsTypes = 0
            }
            windowManager.updateViewLayout(view, params)
            return
        }
        val want =
            if (hasAlert) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT
        if (hasAlert) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            params.fitInsetsTypes = 0
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (params.height != want) {
            params.height = want
        }
        windowManager.updateViewLayout(view, params)
        view.requestLayout()
    }

    private fun setReplyCapture(enabled: Boolean) = ensureMainThread {
        if (replyCaptureActive == enabled) return@ensureMainThread
        replyCaptureActive = enabled
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        params.height =
            when {
                enabled || viewModel.isExpanded.value || alertOverlayActive ->
                    WindowManager.LayoutParams.MATCH_PARENT
                else -> WindowManager.LayoutParams.WRAP_CONTENT
            }
        if (enabled || viewModel.isExpanded.value || alertOverlayActive) {
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

    val expandedVisible = remember { MutableTransitionState(false) }
    val showNotif = !isExpanded && notifAlerts.isNotEmpty()
    val notifVisible = remember { MutableTransitionState(false) }

    LaunchedEffect(isExpanded) {
        expandedVisible.targetState = isExpanded
        if (!isExpanded) onExpandedContentBottomPx(0)
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

    val expandedBlurAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "expanded_blur_alpha",
    )
    val alertBlurAlpha by animateFloatAsState(
        targetValue = if (showNotif) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "alert_blur_alpha",
    )

    CompositionLocalProvider(LocalBlurAlpha provides expandedBlurAlpha) {
        AnimatedVisibility(
            visibleState = expandedVisible,
            // Wrap content — khong fillMaxSize (tranh hit-test / measure day full man).
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
            val onBottom = rememberUpdatedState(onExpandedContentBottomPx)
            // fillMaxSize: ve stack + tap vung trong touchable nhung ngoai the → collapse.
            // Tap duoi soft-max (home) di qua WM; ACTION_OUTSIDE cung collapse.
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
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = topPad)
                            .onGloballyPositioned { coords ->
                                val bottom =
                                    (coords.positionInWindow().y + coords.size.height)
                                        .toInt()
                                        .coerceAtLeast(0)
                                onBottom.value(bottom)
                            },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    chipState?.let { state ->
                        val filtered = state.allEvents.filter { it !is IslandEvent.AospChip }
                        if (filtered.isEmpty()) return@let
                        // Khong boc clickable ca the — de nut Pause/Play nhan tap;
                        // tap ngoai the van collapse qua Box fillMaxSize o tren.
                        ExpandedIslandContent(
                            events = filtered,
                            interactor = viewModel.interactor,
                            onCollapse = { viewModel.statusBarExpansion.collapse() },
                            expandOnlyEventId = expandOnlyEventId,
                            pinnedEventId = state.event.id,
                            onExpandOnlyEventIdChange = { id ->
                                viewModel.statusBarExpansion.setExpandOnlyEventId(id)
                            },
                            hapticsViewModelFactory =
                                viewModel.interactor.sliderHapticsViewModelFactory,
                        )
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
