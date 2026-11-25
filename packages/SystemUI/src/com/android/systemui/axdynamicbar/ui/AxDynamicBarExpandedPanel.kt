package com.android.systemui.axdynamicbar.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.compose.theme.PlatformTheme
import com.android.systemui.axdynamicbar.shared.CollapseMorphPerfTrace
import com.android.systemui.axdynamicbar.shared.TouchableInsetsRefreshCoalesce
import com.android.systemui.axdynamicbar.shared.invalidateForInternalInsets
import com.android.systemui.axdynamicbar.shared.statusBarOverlayHideDelayMs
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
    val enabled: Boolean,
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
    private var initialized = false
    private var overlayView: ComposeView? = null
    private var panelLifecycleOwner: PanelLifecycleOwner? = null
    private var hideOverlayJob: Job? = null
    /** Trang thai hien thi hien tai cua ComposeView giu am (tranh add/removeView moi lan mo). */
    private var overlayShown = false
    private val overlayStatusBarTopPx = mutableIntStateOf(0)
    private val overlayHasCutout = mutableStateOf(false)
    private var overlayHideCleanup: (() -> Unit)? = null

    private var replyCaptureActive = false
    private var explicitFocusRequested = false
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
            if (!overlayShown || v.visibility != View.VISIBLE) {
                info.setTouchableInsets(
                    ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                )
                info.touchableRegion.setEmpty()
                return@OnComputeInternalInsetsListener
            }
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
                // Dang fade-out mo app -> rong touchableRegion de touch xuyen xuong app ngay.
                viewModel.statusBarExpansion.isFadingOutCard.value -> {
                    info.setTouchableInsets(
                        ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                    )
                    info.touchableRegion.setEmpty()
                }
                // Compact alert dang mo: chi vung bounds the de pass-through app ben duoi.
                viewModel.isAlertExpanded.value && alertCompactMode -> {
                    if (v.width <= 0 || v.height <= 0) return@OnComputeInternalInsetsListener
                    info.setTouchableInsets(
                        ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
                    )
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
        if (!overlayShown) return
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
        if (initialized) return
        initialized = true

        viewModel.interactor.onCollapseRequested = { viewModel.statusBarExpansion.collapse() }
        viewModel.interactor.onFadeOutForAppLaunchRequested = { pkg ->
            viewModel.statusBarExpansion.fadeOutForAppLaunch(pkg)
        }
        viewModel.interactor.onFocusableRequested = { focusable -> setOverlayFocusable(focusable) }
        viewModel.interactor.onAlertReplyCaptureRequested = { enabled -> setReplyCapture(enabled) }

        viewModel.statusBarExpansion.isFadingOutCard
            .onEach { fading ->
                if (fading) {
                    setOverlayFocusable(false)
                    ensureMainThread { insetsCoalesce.flushNow() }
                }
            }
            .launchIn(applicationScope)

        // pillSuppressed: giu overlay suot collapse morph 600ms sau khi isExpanded=false.
        // Alert exit: delay 400ms (khong dung 50ms toan cuc — cat AnimatedVisibility exit).
        var previousHadAlert = false
        combine(
                viewModel.isExpanded,
                viewModel.statusBarExpansion.pillSuppressed,
                viewModel.interactor.uiState.map { it.notificationAlert != null }.distinctUntilChanged(),
                viewModel.isOnKeyguard,
                viewModel.interactor.settings.isEnabled,
            ) { expanded, pillSuppressed, hasAlert, onKeyguard, enabled ->
                OverlayNeedSnapshot(expanded, pillSuppressed, hasAlert, onKeyguard, enabled)
            }
            .distinctUntilChanged()
            .onEach { snap ->
                val needed =
                    snap.enabled &&
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
                if (overlayShown && viewModel.isAlertExpanded.value) {
                    updateOverlay(viewModel.isExpanded.value)
                } else {
                    ensureMainThread { overlayView?.requestLayout() }
                }
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
        val windowMetrics = windowManager.currentWindowMetrics
        val statusBarTop = windowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars())
            .top
        val hasCutout = windowMetrics.windowInsets
            .getInsets(WindowInsets.Type.displayCutout())
            .top > 0
        overlayStatusBarTopPx.intValue = statusBarTop
        overlayHasCutout.value = hasCutout

        val isCurrentlyExpanded = viewModel.isExpanded.value
        val pillSuppressed = viewModel.statusBarExpansion.pillSuppressed.value
        val hasAlert = viewModel.interactor.uiState.value.notificationAlert != null
        alertOverlayActive = hasAlert
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            if (explicitFocusRequested || replyCaptureActive) 0
            else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        // MATCH_PARENT khi expand/morph/alert: translationY stack khong bi WRAP_CONTENT cat.
        val overlayHeight =
            if (isCurrentlyExpanded || pillSuppressed || hasAlert) {
                WindowManager.LayoutParams.MATCH_PARENT
            } else {
                WindowManager.LayoutParams.WRAP_CONTENT
            }

        // Tai su dung ComposeView + PlatformTheme da giu am (xoa spike 45-67ms luc mo).
        val existing = overlayView
        if (existing != null) {
            if (overlayShown) return
            val params = existing.layoutParams as? WindowManager.LayoutParams
            if (params != null) {
                params.flags = flags
                params.height = overlayHeight
                params.fitInsetsTypes = 0
                params.setTrustedOverlay()
                windowManager.updateViewLayout(existing, params)
            }
            overlayShown = true
            panelLifecycleOwner?.apply {
                handleLifecycleEvent(Lifecycle.Event.ON_START)
                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }
            existing.visibility = View.VISIBLE
            updateTouchableRegion(existing, isCurrentlyExpanded)
            return
        }

        val lifecycleOwner = PanelLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val view = ComposeView(context).apply {
            // translationY stack keo xuong khong bi View clip.
            clipChildren = false
            clipToPadding = false
            setContent {
                PlatformTheme {
                    OverlayContent(
                        viewModel,
                        overlayStatusBarTopPx.intValue,
                        overlayHasCutout.value,
                        onExpandedContentBottomPx = ::reportExpandedContentBottom,
                        onAlertContentBoundsPx = ::reportAlertContentBounds,
                        collapsePerfTrace = collapsePerfTrace,
                        onRegisterHideCleanup = { overlayHideCleanup = it },
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
                            viewModel.isAlertExpanded.value ->
                                if (!alertCompactMode) viewModel.interactor.dismissNotificationAlertFromOutside()
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
                    if (overlayShown) {
                        updateTouchableRegion(v, viewModel.isExpanded.value)
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
        )
        view.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // Chi cap nhat touchable region khi cua so dang hien va bounds thuc su doi.
            if (overlayShown && (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom)) {
                updateTouchableRegion(v, viewModel.isExpanded.value)
            }
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

        overlayShown = true
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
        replyCaptureActive = false
        explicitFocusRequested = false
        alertOverlayActive = false
        alertCompactMode = true
        imeVisible = false
        imeHeight = 0
        expandedContentBottomPx = 0
        alertContentBounds.setEmpty()
        val view = overlayView ?: return
        if (!overlayShown) return
        overlayShown = false
        // Xoa sach state morph/glass/alert dong bo truoc khi dung lifecycle va an View.GONE
        // (khong giu BlurEngine / Cat::High khi idle — bao toan ARR 60Hz).
        overlayHideCleanup?.invoke()
        view.visibility = View.GONE
        (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            params.flags =
                params.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(view, params)
        }
        panelLifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    private var shrinkRunnable: Runnable? = null

    private fun updateOverlay(expanded: Boolean) = ensureMainThread {
        shrinkRunnable?.let { mainHandler.removeCallbacks(it) }
        shrinkRunnable = null
        if (!overlayShown) return@ensureMainThread
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        val morphing = viewModel.statusBarExpansion.pillSuppressed.value
        val focusable = explicitFocusRequested || replyCaptureActive
        if (expanded || morphing) {
            params.flags =
                if (focusable) {
                    params.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                } else {
                    (params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            // MATCH_PARENT: graphicsLayer.translationY khong mo rong WRAP_CONTENT.
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.fitInsetsTypes = 0
            params.setTrustedOverlay()
            windowManager.updateViewLayout(view, params)
            updateTouchableRegion(view, true)
        } else {
            if (alertOverlayActive) {
                params.flags =
                    if (focusable) {
                        params.flags and
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() and
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    } else {
                        (params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) and
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    }
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
                    if (!overlayShown) return@Runnable
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
     * hideOverlay an cua so. EXIT_ANIM_DURATION 600 chi morph WRAP.
     */
    private fun syncAlertOverlayHeight(hasAlert: Boolean) {
        if (!overlayShown) return
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val focusable = explicitFocusRequested || replyCaptureActive
        if (viewModel.isExpanded.value ||
            replyCaptureActive ||
            viewModel.statusBarExpansion.pillSuppressed.value
        ) {
            params.flags =
                if (focusable) {
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
            if (params.height != WindowManager.LayoutParams.MATCH_PARENT) {
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.fitInsetsTypes = 0
            }
            windowManager.updateViewLayout(view, params)
            return
        }
        if (hasAlert) {
            params.flags =
                if (focusable) {
                    params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
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
        if (!overlayShown) return@ensureMainThread
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
        explicitFocusRequested = focusable
        if (!overlayShown) return@ensureMainThread
        val view = overlayView ?: return@ensureMainThread
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return@ensureMainThread
        if (focusable || replyCaptureActive) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(view, params)
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
