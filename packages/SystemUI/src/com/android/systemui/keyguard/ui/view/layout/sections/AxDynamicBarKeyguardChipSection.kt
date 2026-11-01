package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.axion.compose.host.AxComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.axdynamicbar.ui.compose.AxDynamicBarKeyguardChip
import com.android.systemui.classifier.FalsingCollector
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.media.MediaViewController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shared.R as sharedR
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.util.WallpaperDepthUtils
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val CHIP_ABOVE_LOCK_MARGIN_DP = 12f
private const val UNSET = -1

private fun extraBottomMarginPx(context: Context): Int =
    context.resources.getDimensionPixelSize(R.dimen.ax_dynamic_bar_keyguard_chip_extra_bottom_margin)

private fun Float.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

/**
 * Keyguard Dynamic Bar section — expand LP + an sibling theo archive stock-expand-stack.
 */
class AxDynamicBarKeyguardChipSection
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val viewModel: AxDynamicBarChipViewModel,
    private val indicationController: KeyguardIndicationController,
    private val clockInteractor: KeyguardClockInteractor,
    private val falsingCollector: FalsingCollector,
) : KeyguardSection() {

    private val chipViewId = R.id.ax_dynamic_bar_keyguard_chip
    private var bindHandle: DisposableHandle? = null
    private var expansionHandle: DisposableHandle? = null

    /** alpha + visibility truoc khi an (restore luc collapse). */
    private val originalViewsState =
        Collections.synchronizedMap(WeakHashMap<View, Pair<Float, Int>>())

    private val excludedClockDateViewIds = setOf(
        chipViewId,
        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL,
        ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE,
        sharedR.id.date_smartspace_view,
        R.id.burn_in_layer,
        R.id.burn_in_layer_empty_view,
        R.id.nssl_placeholder,
        R.id.nssl_placeholder_barrier_bottom,
    )

    private fun wireTouchGuards(composeView: AxComposeView) {
        composeView.onDispatchTouchDown = {
            falsingCollector.avoidGesture()
            composeView.requestDisallowInterceptTouchEvent(true)
        }
        composeView.isExpandedProvider = {
            viewModel.isKeyguardExpanded.value || viewModel.keyguardExpansion.isLpExpanded.value
        }
        // Collapsed WRAP: null → claim moi touch tren view; expanded bo qua bounds.
        composeView.touchBoundsProvider = { null }
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        val composeView = AxComposeView(context).apply {
            id = chipViewId
            clipChildren = false
            clipToPadding = false
            wireTouchGuards(this)
        }
        constraintLayout.clipChildren = false
        constraintLayout.clipToPadding = false
        constraintLayout.addView(composeView)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        val composeView: AxComposeView = constraintLayout.requireViewById(chipViewId)
        composeView.clipChildren = false
        composeView.clipToPadding = false
        wireTouchGuards(composeView)
        constraintLayout.clipChildren = false
        constraintLayout.clipToPadding = false

        if (viewModel.isEnabled.value && viewModel.isKeyguardEnabled.value &&
            viewModel.isOnKeyguard.value
        ) {
            indicationController.setSuppressIndication(true)
        }

        composeView.setContent {
            PlatformTheme {
                AxDynamicBarKeyguardChip(viewModel = viewModel)
            }
        }

        bindHandle = composeView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                combine(
                    viewModel.isOnKeyguard,
                    viewModel.isEnabled,
                    viewModel.isKeyguardEnabled,
                ) { onKg, enabled, kgEnabled ->
                    onKg && enabled && kgEnabled
                }.collect { indicationController.setSuppressIndication(it) }
            }
        }

        expansionHandle = composeView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                val scope = this
                scope.launch {
                    viewModel.keyguardExpansion.collapseSettled.collect {
                        if (viewModel.keyguardExpansion.isExpanded.value) return@collect
                        composeView.elevation = 0f
                        constraintLayout.elevation = 0f
                        WallpaperDepthUtils.get()?.setDynamicBarExpanded(false)
                        MediaViewController.getOrNull()?.setExpandedMusicOpen(false)
                        // Giu isLpExpanded + captured rect den khi WRAP layout that —
                        // tranh mo lai sat settle chup host MATCH lam pill (khung keo dai).
                        val gen = viewModel.keyguardExpansion.beginCollapsedLayoutPass()
                        applyCollapsedLp(composeView, viewModel.isLowUdfps.value)
                        armCollapsedHostLayout(constraintLayout, composeView, gen)
                    }
                }
                scope.launch {
                    viewModel.isKeyguardExpanded.collectLatest { expanded ->
                        if (expanded) {
                            clockInteractor.clockSize.collect { size ->
                                if (size != ClockSize.SMALL) {
                                    clockInteractor.setClockSize(ClockSize.SMALL)
                                }
                            }
                        }
                    }
                }
                // Restore siblings as collapse starts; only shrink the host at collapseSettled.
                scope.launch {
                    viewModel.isKeyguardExpanded.collectLatest { expanded ->
                        if (expanded) {
                            onExpandedStateChanged(constraintLayout, composeView)
                        } else {
                            setOtherKeyguardElementsHidden(constraintLayout, false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Thu tu: an sibling → capture pill rect → applyExpandedLp PARENT →
     * setLpExpanded khi host layout MATCH xong (onSizeChanged / layout), khong post mu.
     */
    private fun onExpandedStateChanged(
        constraintLayout: ConstraintLayout,
        composeView: View,
    ) {
        WallpaperDepthUtils.get()?.setDynamicBarExpanded(true)
        // MediaCard morph — khong bat album cover full man (mediaScrim).
        MediaViewController.getOrNull()?.setExpandedMusicOpen(false)
        setOtherKeyguardElementsHidden(constraintLayout, true)
        TransitionManager.endTransitions(constraintLayout)
        composeView.bringToFront()
        composeView.elevation = 40f
        constraintLayout.elevation = 0f
        // Ghi nhan rect pill that TRUOC khi doi host LP (WRAP → MATCH).
        // Bo qua neu host con MATCH (~fullscreen) — capturePillFrame cung tu choi size vo ly.
        val hostW = composeView.width
        val hostH = composeView.height
        val parentH = constraintLayout.height
        val hostLooksFilled =
            (parentH > 0 && hostH >= (parentH * 0.5f).toInt()) ||
                hostW > 700 ||
                hostH > 400
        if (!hostLooksFilled) {
            val loc = IntArray(2)
            composeView.getLocationInWindow(loc)
            viewModel.keyguardExpansion.capturePillFrame(
                loc[0],
                loc[1],
                hostW,
                hostH,
            )
        }
        applyExpandedLp(composeView)
        armLpExpandedAfterHostLayout(constraintLayout, composeView)
    }

    /**
     * Sau applyCollapsedLp: doi host layout WRAP that roi moi finishCollapsedLayout
     * (ha isLpExpanded + xoa rect). Generation/intent chan callback cu khi da mo lai.
     */
    private fun armCollapsedHostLayout(
        constraintLayout: ConstraintLayout,
        composeView: View,
        expectedGeneration: Int,
    ) {
        fun isHostCollapsed(): Boolean {
            val w = composeView.width
            val h = composeView.height
            if (w <= 0 || h <= 0) return false
            val pw = constraintLayout.width
            val ph = constraintLayout.height
            if (pw > 0 && ph > 0) {
                return w < (pw * 0.5f).toInt() && h < (ph * 0.35f).toInt()
            }
            val cap = viewModel.keyguardExpansion.capturedPillFrame.value
            if (cap.width > 0 && cap.height > 0) {
                return w <= cap.width + 24 && h <= cap.height + 24
            }
            return w < 700 && h < 400
        }
        fun tryFinish() {
            if (viewModel.keyguardExpansion.layoutGeneration != expectedGeneration) return
            if (viewModel.keyguardExpansion.isExpanded.value) return
            if (!isHostCollapsed()) return
            viewModel.keyguardExpansion.finishCollapsedLayout(expectedGeneration)
        }
        val listener =
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    if (viewModel.keyguardExpansion.layoutGeneration != expectedGeneration ||
                        viewModel.keyguardExpansion.isExpanded.value
                    ) {
                        composeView.removeOnLayoutChangeListener(this)
                        return
                    }
                    if (!isHostCollapsed()) return
                    composeView.removeOnLayoutChangeListener(this)
                    tryFinish()
                }
            }
        composeView.addOnLayoutChangeListener(listener)
        composeView.requestLayout()
        if (isHostCollapsed()) {
            composeView.removeOnLayoutChangeListener(listener)
            tryFinish()
        }
    }

    /** setLpExpanded chi khi host da layout MATCH — khong post mu, khong nham WRAP pill. */
    private fun armLpExpandedAfterHostLayout(
        constraintLayout: ConstraintLayout,
        composeView: View,
    ) {
        fun isHostFilled(): Boolean {
            val w = composeView.width
            val h = composeView.height
            if (w <= 0 || h <= 0) return false
            val pw = constraintLayout.width
            val ph = constraintLayout.height
            if (pw > 0 && ph > 0) {
                return w >= (pw * 0.85f).toInt() && h >= (ph * 0.85f).toInt()
            }
            // Parent chua do: phai lon hon rect pill da capture (tranh WRAP → setLp som).
            val cap = viewModel.keyguardExpansion.capturedPillFrame.value
            val minW = if (cap.width > 0) cap.width * 2 else 400
            val minH = if (cap.height > 0) cap.height * 2 else 200
            return w >= minW && h >= minH
        }
        val listener =
            object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int,
                ) {
                    if (!isHostFilled()) return
                    composeView.removeOnLayoutChangeListener(this)
                    if (viewModel.isKeyguardExpanded.value) {
                        viewModel.keyguardExpansion.setLpExpanded(true)
                    }
                }
            }
        composeView.addOnLayoutChangeListener(listener)
        if (isHostFilled()) {
            composeView.removeOnLayoutChangeListener(listener)
            if (viewModel.isKeyguardExpanded.value) {
                viewModel.keyguardExpansion.setLpExpanded(true)
            }
        }
    }

    private fun collectActiveClockViews(): Set<View> {
        val clock = clockInteractor.currentClock.value ?: return emptySet()
        val result = LinkedHashSet<View>()
        result.addAll(clock.smallClock.layout.views)
        result.addAll(clock.largeClock.layout.views)
        result.add(clock.smallClock.view)
        result.add(clock.largeClock.view)
        return result
    }

    private fun shouldKeepVisibleOnExpand(child: View, activeClockViews: Set<View>): Boolean {
        if (child is Barrier || child is Guideline) return true
        val id = child.id
        if (id in excludedClockDateViewIds) return true
        if (child in activeClockViews) return true
        if (activeClockViews.any { root -> root === child || isViewUnder(child, root) }) {
            return true
        }
        val entryName =
            runCatching { child.resources.getResourceEntryName(id) }.getOrNull()?.lowercase()
                ?: ""
        return entryName.contains("clock") ||
            entryName.contains("date") ||
            entryName.contains("burn_in") ||
            entryName.contains("nssl_placeholder") ||
            entryName.contains("barrier") ||
            entryName.contains("guideline")
    }

    private fun isViewUnder(child: View, root: View): Boolean {
        if (root !is ViewGroup) return false
        var p: android.view.ViewParent? = child.parent
        while (p != null) {
            if (p === root) return true
            p = p.parent
        }
        return false
    }

    /**
     * An moi sibling VISIBLE tren keyguard root (start/end_button, device_entry, …).
     * Giu chip + clock theo shouldKeepVisibleOnExpand. Restore luc collapse.
     */
    private fun setOtherKeyguardElementsHidden(
        constraintLayout: ConstraintLayout,
        hidden: Boolean,
    ) {
        val activeClockViews = if (hidden) collectActiveClockViews() else emptySet()
        for (i in 0 until constraintLayout.childCount) {
            val child = constraintLayout.getChildAt(i)
            if (shouldKeepVisibleOnExpand(child, activeClockViews)) continue

            if (hidden) {
                if (child.visibility != View.VISIBLE) continue
                originalViewsState.putIfAbsent(child, Pair(child.alpha, child.visibility))
                child.animate().cancel()
                child.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        if (viewModel.isKeyguardExpanded.value) {
                            child.visibility = View.INVISIBLE
                        }
                    }
                    .start()
            } else {
                // Keep the baseline while restoring: another expand may cancel this animation.
                // Removing it now would capture an intermediate alpha on the next expand.
                val savedState = originalViewsState[child] ?: continue
                child.animate().cancel()
                child.visibility = savedState.second
                child.animate()
                    .alpha(savedState.first)
                    .setDuration(250)
                    .withEndAction {
                        if (!viewModel.isKeyguardExpanded.value) {
                            originalViewsState.remove(child)
                        }
                    }
                    .start()
            }
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val expanded = viewModel.isKeyguardExpanded.value
        val lowUdfps = viewModel.isLowUdfps.value
        val chipAboveLockPx = CHIP_ABOVE_LOCK_MARGIN_DP.dpToPx(context)
        val extraBottomPx = extraBottomMarginPx(context)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        constraintSet.apply {
            when {
                expanded -> {
                    // Archive: MATCH toan PARENT (khong neo start_button).
                    constrainWidth(chipViewId, ConstraintSet.MATCH_CONSTRAINT)
                    constrainHeight(chipViewId, ConstraintSet.MATCH_CONSTRAINT)
                    connect(chipViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(
                        chipViewId,
                        ConstraintSet.BOTTOM,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.BOTTOM,
                    )
                    connect(
                        chipViewId,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                    )
                    connect(chipViewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                }
                lowUdfps -> {
                    constrainWidth(chipViewId, wrap)
                    constrainHeight(chipViewId, wrap)
                    connect(
                        chipViewId,
                        ConstraintSet.BOTTOM,
                        R.id.device_entry_icon_view,
                        ConstraintSet.TOP,
                        chipAboveLockPx + extraBottomPx,
                    )
                    connect(
                        chipViewId,
                        ConstraintSet.START,
                        ConstraintSet.PARENT_ID,
                        ConstraintSet.START,
                    )
                    connect(chipViewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                }
                else -> {
                    constrainWidth(chipViewId, wrap)
                    constrainHeight(chipViewId, wrap)
                    connect(chipViewId, ConstraintSet.TOP, R.id.start_button, ConstraintSet.TOP)
                    connect(
                        chipViewId,
                        ConstraintSet.BOTTOM,
                        R.id.start_button,
                        ConstraintSet.BOTTOM,
                    )
                    setVerticalBias(chipViewId, 0.5f)
                    connect(chipViewId, ConstraintSet.START, R.id.start_button, ConstraintSet.END)
                    connect(chipViewId, ConstraintSet.END, R.id.end_button, ConstraintSet.START)
                }
            }
        }
    }

    /** Archive: width/height 0 + 4 canh PARENT — host full keyguard root. */
    private fun applyExpandedLp(composeView: View) {
        val lp = composeView.layoutParams as ConstraintLayout.LayoutParams
        lp.width = 0
        lp.height = 0
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        lp.topMargin = 0
        lp.bottomMargin = 0
        lp.topToBottom = UNSET
        lp.bottomToTop = UNSET
        lp.startToEnd = UNSET
        lp.endToStart = UNSET
        composeView.layoutParams = lp
        composeView.requestLayout()
    }

    private fun applyCollapsedLp(composeView: View, lowUdfps: Boolean) {
        val lp = composeView.layoutParams as ConstraintLayout.LayoutParams
        val extraBottomPx = extraBottomMarginPx(context)
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.topMargin = 0
        lp.topToTop = UNSET
        lp.topToBottom = UNSET
        if (lowUdfps) {
            lp.bottomToTop = R.id.device_entry_icon_view
            lp.bottomToBottom = UNSET
            lp.bottomMargin = CHIP_ABOVE_LOCK_MARGIN_DP.dpToPx(context) + extraBottomPx
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToEnd = UNSET
            lp.endToStart = UNSET
        } else {
            lp.topToTop = R.id.start_button
            lp.bottomToBottom = R.id.start_button
            lp.bottomToTop = UNSET
            lp.topMargin = 0
            lp.bottomMargin = 0
            lp.verticalBias = 0.5f
            lp.startToEnd = R.id.start_button
            lp.endToStart = R.id.end_button
            lp.startToStart = UNSET
            lp.endToEnd = UNSET
        }
        composeView.layoutParams = lp
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        TransitionManager.endTransitions(constraintLayout)
        setOtherKeyguardElementsHidden(constraintLayout, false)
        originalViewsState.clear()
        WallpaperDepthUtils.get()?.setDynamicBarExpanded(false)
        MediaViewController.getOrNull()?.setExpandedMusicOpen(false)
        expansionHandle?.dispose()
        expansionHandle = null
        bindHandle?.dispose()
        bindHandle = null
        indicationController.setSuppressIndication(false)
        constraintLayout.removeView(chipViewId)
    }
}
