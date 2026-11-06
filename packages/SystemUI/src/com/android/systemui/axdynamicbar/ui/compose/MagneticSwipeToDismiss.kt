package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
import com.android.systemui.axdynamicbar.shared.LocalBlurAlpha
import com.android.systemui.axdynamicbar.shared.stackExpandedContentTranslationYPx

/**
 * A child (e.g. media seekbar) can set this to `true` to prevent
 * [MagneticSwipeToDismiss] from claiming horizontal drags.
 * Set it on ACTION_DOWN so the lock is active before slop detection.
 */
val LocalDismissSwipeLock = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

private val DETACH_THRESHOLD = 72.dp
private const val MAGNETIC_PULL = 0.5f
private val DISMISS_VELOCITY = 500.dp
private const val SNAP_STIFFNESS = 550f
private const val SNAP_DAMPING = 0.6f
private const val FLING_STIFFNESS = 300f
private const val VELOCITY_SMOOTHING = 0.4f
// Tha sau khi dung tay: dung distance, khong dung mau fling cu.
private const val VELOCITY_IDLE_TIMEOUT_MS = 100L

/**
 * Bias doc khi stack / swipe-down: can |dx| > |dy| * bias moi khoa ngang
 * (uu tien doc, giam khoa nham khi tay lech nhe).
 */
private const val VERTICAL_AXIS_BIAS = 1.45f

/** Stack slop = touchSlop * he so — cao hon de tranh tap le xuong. */
private const val STACK_SLOP_MULT = 3.0f

/** Truc: 0=chua khoa, 1=ngang (dismiss), 2=doc len (dismiss), 3=doc xuong (stack), 4=doc xuong (expand card). */
private const val AXIS_NONE = 0
private const val AXIS_HORIZONTAL = 1
private const val AXIS_UP = 2
private const val AXIS_STACK = 3
private const val AXIS_DOWN = 4

/** Tran keo xuong (1 the / stack) — cung ti le man hinh. */
internal const val MAX_NOTIFICATION_DRAG_SCREEN_FRACTION = 0.60f
private const val DOWNWARD_RESISTANCE_START_FRACTION = 0.75f

/** Chan cuoi elastic: lien tuc, cang gan max cang nang. */
private fun resistedDownwardOffset(dragPx: Float, maxDragPx: Float): Float {
    if (maxDragPx <= 0f) return 0f
    val drag = dragPx.coerceAtLeast(0f)
    val start = maxDragPx * DOWNWARD_RESISTANCE_START_FRACTION
    if (drag <= start) return drag
    val remaining = maxDragPx - start
    val excess = drag - start
    return (start + remaining * (excess / (remaining + excess))).coerceAtMost(maxDragPx)
}

private fun downwardDragOffset(rawY: Float, detachPx: Float, maxDragPx: Float): Float {
    val pulled = rawY * MAGNETIC_PULL
    val offset = if (pulled < detachPx) pulled else detachPx + rawY - detachPx / MAGNETIC_PULL
    return resistedDownwardOffset(offset, maxDragPx)
}

@Composable
internal fun MagneticSwipeToDismiss(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowUpDismiss: Boolean = false,
    /** Vuot xuong tren the (vd. Alert Card) de mo rong hoac mo panel. */
    onSwipeDown: (() -> Unit)? = null,
    /** Cho keo xuong roi snap ve (khong action khi tha). */
    allowDownDrag: Boolean = false,
    gesturesEnabled: Boolean = true,
    /**
     * Vuot ngang dismiss island. false = khong claim ngang (vd. alert compact).
     * Media homescreen: true; SeekBar dung LocalDismissSwipeLock khi scrub.
     */
    allowHorizontalDismiss: Boolean = true,
    /** Keo xuong doi panel stack; null = tat (chi dismiss ngang/len). */
    onStackDrag: ((dragY: Float) -> Unit)? = null,
    /** Goi khi vua khoa truc stack — huy settle anim dang chay. */
    onStackDragStart: (() -> Unit)? = null,
    onStackDragEnd: ((dragY: Float, velocityY: Float) -> Unit)? = null,
    maxStackDragPx: Float = 0f,
    /**
     * Stack (alert + island media/timer/…): chan cuoi elastic
     * (resistedDownwardOffset, bat dau ~75% maxStackDragPx).
     */
    elasticStackDrag: Boolean = false,
    /**
     * Key restart pointerInput sau pin/switch (vd. top event id)
     * — tranh callback stale va stackDragY lech.
     */
    stackDragKey: Any? = null,
    /**
     * Dich visual noi dung (vd. stack dragY) — CHI tren content ben trong
     * pointerInput. Neu dich ca ancestor chua pointer → feedback toa do local
     * (change.position - down) → rung. Pattern: StackedExpandedPanels
     * (contentOffsetY, khong boc translationY tren ancestor pointer).
     *
     * Lambda doc LIVE trong graphicsLayer (vd. { dragY }) — tranh Float param
     * tre 1 frame so voi shared glass / state pointer.
     */
    contentOffsetY: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val viewConfig = LocalViewConfiguration.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val maxDownDragPx = with(density) {
        (screenHeightDp * MAX_NOTIFICATION_DRAG_SCREEN_FRACTION).dp.toPx()
    }

    val detachPx = with(density) { DETACH_THRESHOLD.toPx() }
    val dismissVelPx = with(density) { DISMISS_VELOCITY.toPx() }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    // Offset live sync trong luc keo — tranh scope.launch { snapTo } tung frame (rung).
    var liveOffsetX by remember { mutableFloatStateOf(0f) }
    var liveOffsetY by remember { mutableFloatStateOf(0f) }
    var liveDragging by remember { mutableStateOf(false) }
    var detached by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val swipeLock = remember { mutableStateOf(false) }
    val enableStack = onStackDrag != null && maxStackDragPx > 0f
    val enableDown = allowDownDrag || onSwipeDown != null
    // Callback moi nhat — tranh pointerInput giu closure cu (pin/commit fail).
    val onStackDragStartState = rememberUpdatedState(onStackDragStart)
    val onStackDragState = rememberUpdatedState(onStackDrag)
    val onStackDragEndState = rememberUpdatedState(onStackDragEnd)
    val onSwipeDownState = rememberUpdatedState(onSwipeDown)

    if (dismissed) return

    // Fade blur backdrop theo dismiss offset (giu hanh vi frosted card HEAD).
    val absOff =
        maxOf(
            abs(if (liveDragging) liveOffsetX else offsetX.value),
            abs(if (liveDragging) liveOffsetY else offsetY.value),
        )
    val progress = (absOff / (detachPx * 4f)).coerceIn(0f, 1f)
    val contentAlpha = 1f - progress * 0.5f
    val blurAlpha = LocalBlurAlpha.current * contentAlpha

    CompositionLocalProvider(
        LocalDismissSwipeLock provides swipeLock,
        LocalBlurAlpha provides blurAlpha,
    ) {
        // pointerInput NGOAI graphicsLayer — tranh feedback toa do khi dich visual
        // (stack dragY / dismiss offsetX-Y khong duoc nam tren ancestor cua pointerInput).
        Box(
            modifier =
                modifier.pointerInput(
                    allowUpDismiss,
                    allowHorizontalDismiss,
                    enableStack,
                    enableDown,
                    maxStackDragPx,
                    elasticStackDrag,
                    maxDownDragPx,
                    stackDragKey,
                    gesturesEnabled,
                ) {
                    if (!gesturesEnabled) return@pointerInput
                    val touchSlop = viewConfig.touchSlop

                    awaitEachGesture {
                        val down: PointerInputChange
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val d = event.changes.firstOrNull {
                                it.changedToDownIgnoreConsumed()
                            }
                            if (d != null) { down = d; break }
                        }

                        var axis = AXIS_NONE
                        var velocity = 0f
                        var prevTime = 0L
                        // Moi gesture moi: stack keo bat dau tu 0.
                        var stackDragY = 0f
                        var released = false
                        detached = false
                        liveDragging = false
                        liveOffsetX = 0f
                        liveOffsetY = 0f

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    released = true
                                    // Dung tay: khong coi la fling (mau vel cu).
                                    val releaseVelocity =
                                        if (prevTime > 0L &&
                                            change.uptimeMillis - prevTime <= VELOCITY_IDLE_TIMEOUT_MS
                                        ) velocity else 0f
                                    if (axis != AXIS_NONE) change.consume()
                                    if (axis == AXIS_NONE) break

                                    if (axis == AXIS_STACK) {
                                        // Goi sync — settle Job nam o StackedExpandedPanels.
                                        // Khong dung offset Magnetic — 1 nguon Y = dragY stack.
                                        liveDragging = false
                                        liveOffsetX = 0f
                                        liveOffsetY = 0f
                                        onStackDragEndState.value?.invoke(stackDragY, releaseVelocity)
                                        break
                                    }

                                    if (axis == AXIS_DOWN) {
                                        val off = liveOffsetY
                                        val vel = releaseVelocity
                                        scope.launch {
                                            offsetY.snapTo(off)
                                            liveDragging = false
                                            val shouldExpand =
                                                onSwipeDownState.value != null &&
                                                    (vel >= dismissVelPx * 0.7f ||
                                                        (detached && off > detachPx * 0.5f))
                                            if (shouldExpand) {
                                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                offsetY.animateTo(
                                                    0f,
                                                    spring(dampingRatio = 0.8f, stiffness = FLING_STIFFNESS),
                                                )
                                                onSwipeDownState.value?.invoke()
                                            } else {
                                                if (detached && onSwipeDownState.value != null) {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate
                                                    )
                                                }
                                                offsetY.animateTo(0f, spring(SNAP_DAMPING, SNAP_STIFFNESS))
                                            }
                                            detached = false
                                        }
                                        break
                                    }

                                    val isUp = axis == AXIS_UP
                                    val off = if (isUp) liveOffsetY else liveOffsetX
                                    val vel = releaseVelocity
                                    scope.launch {
                                        if (isUp) offsetY.snapTo(off) else offsetX.snapTo(off)
                                        liveDragging = false
                                        val shouldDismiss =
                                            abs(vel) >= dismissVelPx ||
                                                (detached && abs(off) > detachPx * 0.5f)
                                        if (shouldDismiss) {
                                            val target = when (axis) {
                                                AXIS_UP -> -size.height * 1.5f
                                                else -> {
                                                    val dir = if (abs(vel) > 100f) sign(vel) else sign(off)
                                                    dir * size.width * 1.5f
                                                }
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                            val anim = if (isUp) offsetY else offsetX
                                            anim.animateTo(
                                                target,
                                                spring(dampingRatio = 1f, stiffness = FLING_STIFFNESS),
                                            )
                                            dismissed = true
                                            onDismiss()
                                        } else {
                                            if (detached) {
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.GestureThresholdActivate
                                                )
                                            }
                                            val anim = if (isUp) offsetY else offsetX
                                            anim.animateTo(0f, spring(SNAP_DAMPING, SNAP_STIFFNESS))
                                            detached = false
                                        }
                                    }
                                    break
                                }

                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y

                                if (axis == AXIS_NONE) {
                                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                                        // Bias doc khi stack / swipe-down — khong doi truc sau khi khoa.
                                        val preferVertical = enableStack || enableDown
                                        val wouldBeHorizontal =
                                            if (preferVertical) {
                                                abs(dx) > abs(dy) * VERTICAL_AXIS_BIAS
                                            } else {
                                                abs(dx) >= abs(dy)
                                            }
                                        // Media / seek / UMO carousel: khong claim ngang
                                        // (break ngay, khong consume) — View carousel nhan swipe
                                        if (wouldBeHorizontal &&
                                            (!allowHorizontalDismiss || swipeLock.value)
                                        ) {
                                            break
                                        }
                                        // Stack can nguong cao hon touchSlop — tranh tap
                                        // le xuong bi nham thanh keo panel (nuot clickable).
                                        val stackSlop = touchSlop * STACK_SLOP_MULT
                                        when {
                                            wouldBeHorizontal -> axis = AXIS_HORIZONTAL
                                            enableStack && dy > stackSlop -> {
                                                axis = AXIS_STACK
                                                // 1 nguon Y = dragY stack — khong bat live Magnetic.
                                                liveDragging = false
                                                liveOffsetX = 0f
                                                liveOffsetY = 0f
                                                onStackDragStartState.value?.invoke()
                                            }
                                            enableDown && dy > touchSlop * 1.5f -> {
                                                axis = AXIS_DOWN
                                                liveDragging = true
                                                liveOffsetY = 0f
                                            }
                                            // Doc xuong nhung chua du stackSlop: cho them
                                            (enableStack || enableDown) && dy > 0 -> continue
                                            allowUpDismiss && dy < 0 -> {
                                                axis = AXIS_UP
                                                liveDragging = true
                                                liveOffsetY = 0f
                                            }
                                            // Doc xuong khong stack / doc len khong allow → bo
                                            else -> break
                                        }
                                        if (axis == AXIS_HORIZONTAL) {
                                            liveDragging = true
                                            liveOffsetX = 0f
                                        }
                                    } else {
                                        continue
                                    }
                                }

                                change.consume()

                                val delta = change.position - change.previousPosition
                                val now = change.uptimeMillis
                                if (prevTime > 0L) {
                                    val dt = (now - prevTime).coerceAtLeast(1) / 1000f
                                    val instantVel =
                                        when (axis) {
                                            AXIS_STACK, AXIS_UP, AXIS_DOWN -> delta.y / dt
                                            else -> delta.x / dt
                                        }
                                    velocity =
                                        velocity * (1f - VELOCITY_SMOOTHING) +
                                            instantVel * VELOCITY_SMOOTHING
                                }
                                prevTime = now

                                if (axis == AXIS_STACK) {
                                    // Theo vi tri tu diem down (on dinh hon cong delta).
                                    // Chi goi onStackDrag — khong snap offset Magnetic (1 nguon Y).
                                    liveOffsetX = 0f
                                    liveOffsetY = 0f
                                    stackDragY = if (elasticStackDrag) {
                                        resistedDownwardOffset(dy, maxStackDragPx)
                                    } else {
                                        dy.coerceIn(0f, maxStackDragPx)
                                    }
                                    onStackDragState.value?.invoke(stackDragY)
                                    continue
                                }

                                if (axis == AXIS_DOWN) {
                                    // Vi tri tuyet doi tu down (dy) — khong cong delta (het tich luy).
                                    val rawY = dy.coerceAtLeast(0f)
                                    if (!detached && rawY * MAGNETIC_PULL >= detachPx) {
                                        detached = true
                                        if (onSwipeDownState.value != null) {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.GestureThresholdActivate
                                            )
                                        }
                                    }
                                    liveOffsetY = downwardDragOffset(rawY, detachPx, maxDownDragPx)
                                    continue
                                }

                                val dragAmount =
                                    if (axis == AXIS_UP) delta.y else delta.x
                                val current =
                                    if (axis == AXIS_UP) liveOffsetY else liveOffsetX

                                if (!detached) {
                                    val target = current + dragAmount * MAGNETIC_PULL
                                    if (abs(target) >= detachPx) {
                                        detached = true
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate
                                        )
                                        val next = current + dragAmount
                                        if (axis == AXIS_UP) {
                                            liveOffsetY = next
                                        } else {
                                            liveOffsetX = next
                                        }
                                    } else {
                                        if (axis == AXIS_UP) {
                                            liveOffsetY = target
                                        } else {
                                            liveOffsetX = target
                                        }
                                    }
                                } else {
                                    val next = current + dragAmount
                                    if (axis == AXIS_UP) {
                                        liveOffsetY = next
                                    } else {
                                        liveOffsetX = next
                                    }
                                }
                            }
                        } finally {
                            if (!released && axis != AXIS_NONE) {
                                // CANCEL / mat pointer / restart pointerInput: snap ve 0, khong commit.
                                liveOffsetX = 0f
                                liveOffsetY = 0f
                                liveDragging = false
                                detached = false
                                if (axis == AXIS_STACK) {
                                    onStackDragEndState.value?.invoke(0f, 0f)
                                }
                                scope.launch {
                                    offsetX.snapTo(0f)
                                    offsetY.snapTo(0f)
                                }
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        clip = false
                        // Doc Snapshot trong draw — khong recompose layout moi frame.
                        // AXIS_STACK: magnetic vy=0; stack keo = contentOffsetY (duoi pointer).
                        val vx = if (liveDragging) liveOffsetX else offsetX.value
                        val vy = if (liveDragging) liveOffsetY else offsetY.value
                        val absOff = maxOf(abs(vx), abs(vy))
                        val progress = (absOff / (detachPx * 4f)).coerceIn(0f, 1f)
                        translationX = vx
                        // Doc LIVE — cung frame pointer/dragY (khong capture Float stale).
                        translationY =
                            stackExpandedContentTranslationYPx(
                                magneticVyPx = vy,
                                contentOffsetYPx = contentOffsetY(),
                            )
                        alpha = 1f - progress * 0.5f
                        scaleX = 1f - progress * 0.04f
                        scaleY = 1f - progress * 0.04f
                        rotationZ = (vx / detachPx).coerceIn(-1f, 1f) * 2f
                    }
            ) {
                content()
            }
        }
    }
}
