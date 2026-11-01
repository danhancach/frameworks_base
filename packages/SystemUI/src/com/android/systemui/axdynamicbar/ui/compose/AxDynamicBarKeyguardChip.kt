@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset
import com.android.axion.blur.AxBlurSurfaceDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.Expandable
import com.android.compose.animation.ExpandableController
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.animation.Expandable as SystemUiExpandable
import com.android.systemui.blur.AxSystemFrost
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.model.RecordingState
import com.android.systemui.axdynamicbar.shared.*
import com.android.systemui.axdynamicbar.ui.AxDynamicBarChipViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private val ChipHeight = 36.dp
private val ChipShape = ShapeChip
private val ChipIconSize = ChipHeight - SpaceLg
/**
 * Lockscreen bottom pill SSOT = media (cung hang quick affordance 48dp,
 * Row CenterVertically trong LockscreenLowerRegion).
 * Timer/stopwatch Clock dung cung envelope — khong StatusBar 20dp.
 */
private val MusicChipHeight = 58.dp
private val MusicChipMinWidth = 130.dp
private val MusicChipMaxWidth = 180.dp
private val MusicActionSize = MusicChipHeight - 16.dp
private val MusicActionIconSize = MusicChipHeight - 26.dp
private val MusicPlayPauseSize = MusicChipHeight - 24.dp
private val MusicPlayPauseIconSize = MusicChipHeight - 34.dp
private val ActionSize = SpacePanel
private val ActionIconSize = SizeBadge
private val MultiLineChipHeight = 48.dp
/** Chu so chronometer tren keyguard (lon hon CompactChronometer StatusBar). */
private val KeyguardChronometerTextSize = 16.sp

// Stack Samsung Now Bar (port AxDynamicBarKeyguardChip.port.kt) — khong half-measure liveDragY.
/** dy_offset stack Samsung now_bar_card_stack_view_dy_offset. */
private val KeyguardStackDyOffset = 56.dp
// Rest scale / Y — chi top + under ke (depth 0..1); depth>=2 khong compose.
// Peek under = 1/2 Samsung (8dp → 4dp).
private val StackRestScale = floatArrayOf(1f, 0.92f)
private val StackRestYDp = floatArrayOf(0f, 4f)
private const val StackMaxDepth = 1
private val StackFollowX = floatArrayOf(0.1f, 0.08f)
private const val StackCommitVelocityY = 3000f
/** Keyguard stack (AOD NowBarCardStackView): vi tri + scale cung 200 / 0.78. */
private const val StackSnapDamping = 0.78f
private const val StackSnapStiffness = 200f
private const val StackCommitScaleDamping = 0.78f
private const val StackCommitScaleStiffness = 200f
private const val StackCommitXyDamping = 0.78f
private const val StackCommitXyStiffness = 200f
/** Morph khung keyguard (presentation.a): spring 200 / 0.70. */
private const val KeyguardMorphDamping = 0.70f
private const val KeyguardMorphStiffness = 200f
/**
 * Fallback H Battery truoc khi lockContentHeightPx do xong.
 * Card pin cao hon Media/Clock mac dinh 224.dp — khoa som de sy on dinh.
 */
private val KeyguardBatteryExpandedHeightDefault = 300.dp
/**
 * Battery compact overlay: chi hien khi visual H gan pill (tranh stretch
 * noi dung pill tren khung expanded). visualH/pillH = sy*targetH/pillH.
 */
private const val BatteryCompactNearPillMax = 1.12f

/** Follow ΔY theo depth Samsung NowBarCardStackView (Y_limit am). */
private fun stackFollowY(depth: Int, deltaY: Float, yLimit: Float): Float =
    when (depth) {
        0 ->
            if (deltaY < yLimit) {
                (deltaY - yLimit) * 0.1f + 0.9f * yLimit
            } else {
                0.9f * deltaY
            }
        1 -> 0.1f * deltaY
        else -> 0f
    }

private fun stackFollowX(depth: Int, deltaX: Float): Float =
    StackFollowX.getOrElse(depth) { 0f } * deltaX

/** Trang thai visual 1 the trong stack (drag / reorder). */
private data class StackCardMotion(
    val event: IslandEvent,
    val depth: Int,
    val tx: Float,
    val ty: Float,
    val scale: Float,
)

/** Z remap: top cao nhat. */
private fun stackZIndex(depth: Int): Float = (3 - depth).toFloat()

// Morph expand Samsung presentation.a (port) — pill → panel.
private val KeyguardPillCorner = MusicChipHeight / 2
private val KeyguardExpandedHeightDefault = 224.dp
/**
 * Khoang cach day man → canh duoi pill (2520 - 2447 ≈ 73px ≈ 26.dp).
 * Morph + pill stack CUNG neo BottomCenter + offset nay — day on dinh, khong day len.
 */
private val KeyguardPillBottomOffset = 26.dp
private val NowBarTransYEasing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t
private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = (a.value + (b.value - a.value) * t).dp

/**
 * true = the dang chon nhuong ownership nen sang KeyguardSharedGlass (morph host).
 * The khac trong stack van giu GlassChip rieng — khong cat/mat nen khi keo.
 */
private val LocalKeyguardYieldGlassToMorph = staticCompositionLocalOf { false }

/** Clip elliptical (rxPx, ryPx) — bu host scale sx≠sy; scalar khi rx==ry. */
private fun morphCapsuleShape(rxPx: Float, ryPx: Float) =
    GenericShape { size, _ ->
        val rx = rxPx.coerceIn(0f, size.width * 0.5f)
        val ry = ryPx.coerceIn(0f, size.height * 0.5f)
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(rx, ry),
            ),
        )
    }

/**
 * Nen chung — blur+clip CHI lop nen (khong clickable); content (stack) KHONG clip.
 * Cham vung trong the: khung noi dung / absorber o call-site; backdrop dong khi cham ngoai.
 * Dung qua movableContentOf de giu BlurEngine khi chuyen collapsed ↔ morph.
 */
@Composable
private fun KeyguardSharedGlass(
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    cornerRadiusY: Dp = cornerRadius,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val rxPx = with(density) { cornerRadius.toPx() }
    val ryPx = with(density) { cornerRadiusY.toPx() }
    val capsuleShape = remember(rxPx, ryPx) { morphCapsuleShape(rxPx, ryPx) }
    // Fallback/tint SSOT AxSystemFrost — cung affordance/notif; giu movableContentOf/sharedGlass.
    val frostFallback = Color(AxSystemFrost.fallbackColor(context))
    val frostTint = Color(AxSystemFrost.tintColor(context))
    Box(modifier = modifier) {
        // Chi blur/tint/highlight + hinh dang — khong xu ly cham.
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(capsuleShape)
                    .islandBlur(
                        cornerRadius = cornerRadius,
                        cornerRadiusY = cornerRadiusY,
                        fallbackColor = frostFallback,
                        tint = frostTint,
                        highlightWidth = AxBlurSurfaceDefaults.HighlightWidth,
                        // Sample the wallpaper, without replaying keyguard's dimming scrims.
                        includeViewBackdrop = false,
                    ),
        )
        content()
    }
}

/**
 * Khoa cham + a11y khi nhanh morph da an (khong chi alpha).
 * Overlay nuot pointer; clearAndSetSemantics an khoi accessibility tree.
 */
@Composable
private fun KeyguardMorphBranchGate(
    interactive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier.then(
                if (interactive) Modifier
                else Modifier.clearAndSetSemantics {},
            ),
    ) {
        content()
        if (!interactive) {
            // Nam tren noi dung — chan cham; khong de nhanh an nhan nut.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .pointerInteropFilter { true },
            )
        }
    }
}

/**
 * Keyguard chips: glass tren wallpaper (khong accent pill). Tone event o icon/progress/action.
 * Khi [LocalKeyguardYieldGlassToMorph]: bo blur — the top nhuong nen cho khung morph.
 */
@Composable
private fun GlassChip(
    height: Dp,
    blurAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val yieldToMorph = LocalKeyguardYieldGlassToMorph.current
    val context = LocalContext.current
    val frostFallback = Color(AxSystemFrost.fallbackColor(context))
    val frostTint = Color(AxSystemFrost.tintColor(context))
    Box(
        // ShapeChip is a 50 % pill; the blur needs the matching radius explicitly.
        // SSOT AxSystemFrost — khong tint mau rieng; yieldToMorph nhuong nen cho sharedGlass.
        modifier =
            if (yieldToMorph) {
                modifier
            } else {
                modifier.islandBlur(
                    cornerRadius = height / 2,
                    fallbackColor = frostFallback,
                    tint = frostTint,
                    alpha = blurAlpha,
                    highlightWidth = AxBlurSurfaceDefaults.HighlightWidth,
                    includeViewBackdrop = false,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Nhip noi dung AOD/keyguard — mo/dong KHONG doi xung; alpha luon clamp 0..1
 * (tach khoi spring overshoot neu co).
 *
 * Mo: pill rut som; expand hien khi khung du cho (progress ~ rong).
 * Dong: expand tat nhanh (~12% dau progress, ~50ms/400ms); pill tro lai sau.
 */
private fun morphPillContentAlpha(progress: Float, closing: Boolean): Float =
    if (closing) {
        // Pill tro lai o nua sau luc dong.
        ((0.55f - progress) / 0.55f).coerceIn(0f, 1f)
    } else {
        (1f - progress / 0.35f).coerceIn(0f, 1f)
    }

private fun morphExpandContentAlpha(progress: Float, closing: Boolean): Float =
    if (closing) {
        // Thu hoi noi dung chi tiet nhanh — het alpha khi progress con ~0.88.
        ((progress - 0.88f) / 0.12f).coerceIn(0f, 1f)
    } else {
        // Chi hien khi khung du lon (~ progress 0.4+); khong copy nguong 239dp Samsung.
        ((progress - 0.4f) / 0.4f).coerceIn(0f, 1f)
    }

@Composable
private fun rememberChargingParts(batteryString: String): List<String> {
    return remember(batteryString) {
        batteryString.split("\n", limit = 3).map { it.trim() }.filter { it.isNotEmpty() }
    }
}

@Composable
fun AxDynamicBarKeyguardChip(
    viewModel: AxDynamicBarChipViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.chipState.collectAsStateWithLifecycle()
    val isOnKeyguard by viewModel.isOnKeyguard.collectAsStateWithLifecycle()
    val isEnabled by viewModel.isEnabled.collectAsStateWithLifecycle()
    val isKeyguardEnabled by viewModel.isKeyguardEnabled.collectAsStateWithLifecycle()
    val isKeyguardMusicPillEnabled by viewModel.isKeyguardMusicPillEnabled.collectAsStateWithLifecycle()
    val keyguardBatteryChipMode by viewModel.keyguardBatteryChipMode.collectAsStateWithLifecycle()
    val batteryInfo by viewModel.keyguardBatteryInfo.collectAsStateWithLifecycle()
    val isKeyguardExpanded by viewModel.isKeyguardExpanded.collectAsStateWithLifecycle()
    // ChipSection applyExpandedLp xong moi set — morph can fillMaxSize (tranh clip WRAP pill).
    val isLpExpanded by viewModel.keyguardExpansion.isLpExpanded.collectAsStateWithLifecycle()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    val currentView = LocalView.current
    val batteryString by viewModel.batteryString.collectAsStateWithLifecycle()
    val motionScheme = MaterialTheme.motionScheme
    val expandableController =
        rememberExpandableController(color = Color.Transparent, shape = ChipShape)

    val suppressKeyguardIndication = isOnKeyguard && isEnabled && isKeyguardEnabled
    LaunchedEffect(suppressKeyguardIndication) {
        viewModel.setKeyguardIndicationSuppressed(suppressKeyguardIndication)
    }
    DisposableEffect(Unit) { onDispose { viewModel.setKeyguardIndicationSuppressed(false) } }

    val chipState = state
    val rawEvent = chipState?.notificationAlert ?: chipState?.event
    val displayEvent: IslandEvent? =
        when {
            !isKeyguardMusicPillEnabled && rawEvent is IslandEvent.Media -> {
                chipState?.allEvents?.firstOrNull { it !is IslandEvent.Media }
            }
            else -> rawEvent
        }
    val cycleEvents = remember(chipState?.allEvents, isKeyguardMusicPillEnabled) {
        val all = chipState?.allEvents ?: emptyList()
        if (isKeyguardMusicPillEnabled) all else all.filter { it !is IslandEvent.Media }
    }
    val targetEvent = displayEvent ?: chipState?.event
    // Stage1: battery "selected" khi khong co event island + mode cho phep (1=sac, 2=always).
    // Khong PillSwipeStack BatteryItem — multi-event stack = Stage2.
    val batteryPillActive =
        keyguardBatteryChipMode > 0 &&
            (keyguardBatteryChipMode != 1 || batteryInfo.isCharging)
    val batterySelected = batteryPillActive && targetEvent == null

    // Chi morph khi host da MATCH (isLpExpanded) — neu morph som → clip 58dp, chi thay nut RemoteViews.
    val morphTarget = isKeyguardExpanded && isLpExpanded
    val morphTransition = updateTransition(
        targetState = morphTarget,
        label = "kg_morph_transition",
    )
    var expandedContentHeightDp by remember { mutableStateOf(KeyguardExpandedHeightDefault) }
    // Chieu cao dich CO DINH theo content owner (tranh fallback 224.dp khi alpha thap).
    // Owner doi in-host (Clock Reset→Battery) → invalidate; collapse settle moi clear.
    var lockedTargetHeight by remember { mutableStateOf(LockedTargetHeight()) }
    var pillWidthDp by remember { mutableStateOf(MusicChipMaxWidth) }
    // Chi notify collapseSettled sau khi da tung expand — tranh WRAP som luc init.
    var hadMorphExpand by remember { mutableStateOf(false) }

    // Mot spring duy nhat dieu phoi ca khoi (scale + corner + nay) — tranh hai spring lech nhip.
    // Rebound p<0: envelope pill-relative trong computeKeyguardMorphGeom (het dummy pulse).
    val morphProgress by morphTransition.animateFloat(
        transitionSpec = {
            spring(
                dampingRatio = KeyguardMorphDamping,
                stiffness = KeyguardMorphStiffness,
            )
        },
        label = "kg_morph_progress",
    ) { expanded -> if (expanded) 1f else 0f }

    val expandCornerTarget = dimensionResource(R.dimen.notification_corner_radius)

    // closing = dang ve pill (target collapsed) — nhip alpha mo/dong tach biet.
    // abs(progress): van closing khi undershoot p<0.
    val morphClosing =
        !morphTransition.targetState &&
            (morphTransition.currentState || abs(morphProgress) > 0.01f)
    // Alpha clamp 0..1 — tach khoi overshoot spring cua scale.
    val morphProgressClamped = morphProgress.coerceIn(0f, 1f)
    val pillContentAlpha = morphPillContentAlpha(morphProgressClamped, morphClosing)
    val expandContentAlpha =
        morphExpandContentAlpha(morphProgressClamped, morphClosing)

    // abs: giu MATCH khi spring undershoot p<0 (khong coi p am la "het morph").
    val isMorphing = abs(morphProgress) > 0.01f
    val morphRunning = morphTransition.isRunning
    val capturedPill by viewModel.keyguardExpansion.capturedPillFrame.collectAsStateWithLifecycle()
    // Host da MATCH (hoac sap MATCH) nhung isLpExpanded chua true: giu pill o capturedPillFrame
    // trong nhanh morph (progress=0). Tranh nhanh collapsed BottomCenter can xuong day man (~76px).
    val handoffHold =
        isKeyguardExpanded &&
            !isLpExpanded &&
            capturedPill.width > 0 &&
            capturedPill.height > 0
    // fillMaxSize khi morph/handoff; sau collapse settle ve nhanh WRAP ngay ca khi
    // View chua finishCollapsedLayout — tranh fillMaxSize khoa host o 2520px.
    val showHostFill =
        (isLpExpanded && (isKeyguardExpanded || hadMorphExpand)) ||
            isMorphing || handoffHold
    val chipOnKeyguard = isOnKeyguard && isEnabled && isKeyguardEnabled
    val pillVisible = chipOnKeyguard && pillContentAlpha > 0.01f
    val gesturesEnabled = !isKeyguardExpanded && pillVisible

    // Under peek: rest = 1; nua sau collapse tho dan; expand/handoff/expanded = 0.
    val underRevealAlpha = when {
        isKeyguardExpanded || morphTarget || handoffHold -> 0f
        !showHostFill -> 1f
        else -> ((0.6f - morphProgressClamped) / 0.6f).coerceIn(0f, 1f)
    }
    LaunchedEffect(morphTarget) {
        if (morphTarget) {
            hadMorphExpand = true
            // Keep the measured endpoint when reopening during collapse; reset only when settled.
        }
    }

    // Doi transition idle + progress collapsed moi WRAP.
    LaunchedEffect(
        morphTransition.currentState,
        morphTransition.targetState,
        morphRunning,
        morphProgress,
        hadMorphExpand,
    ) {
        if (hadMorphExpand &&
            !morphTransition.currentState &&
            !morphTransition.targetState &&
            !morphRunning &&
            abs(morphProgress) <= 0.001f
        ) {
            viewModel.keyguardExpansion.notifyCollapseSettled()
            hadMorphExpand = false
            lockedTargetHeight = LockedTargetHeight()
        }
    }

    // Khoa H theo content owner khi do o dung targetW — state kick recomposition.
    fun lockContentHeightPx(
        hPx: Int,
        measuredWPx: Int,
        targetWPx: Int,
        pillHPx: Int,
        owner: String,
    ) {
        if (hPx <= pillHPx) return
        // Chi chap nhan phep do o chieu rong dich (requiredWidth), khong theo animW.
        if (measuredWPx < targetWPx - 2) return
        val next = resolveLockedTargetHeight(lockedTargetHeight, owner, hPx)
        if (next != lockedTargetHeight) {
            lockedTargetHeight = next
        }
        val hDp = with(density) { hPx.toDp() }
        if (hDp != expandedContentHeightDp) {
            expandedContentHeightDp = hDp
        }
    }

    // Mot composition nen: movable giua collapsed WRAP va morph host (giu BlurEngine).
    // movableContentOf toi da 4 tham so — gop corner X/Y thanh Pair.
    val sharedGlass = remember {
        movableContentOf {
                corners: Pair<Dp, Dp>,
                glassMod: Modifier,
                content: @Composable BoxScope.() -> Unit,
            ->
            KeyguardSharedGlass(
                cornerRadius = corners.first,
                cornerRadiusY = corners.second,
                modifier = glassMod,
                content = content,
            )
        }
    }

    // Mot nhanh tuong tac: expand thang khi alpha cao; pill khi expand khong active.
    val expandInteractive = expandContentAlpha > 0.5f
    val pillInteractive = pillContentAlpha > 0.5f && !expandInteractive

    CompositionLocalProvider(
        LocalKeyguardSuppressRipple provides true,
        LocalIndication provides KeyguardNoRippleIndication,
    ) {
    Box(
        modifier = if (showHostFill) modifier.fillMaxSize() else modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Host morph: Event (Clock/Media) HOAC Battery khi targetEvent=null — cung spring/geometry.
        if (showHostFill && (targetEvent != null || batterySelected)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxWPx = constraints.maxWidth
                val maxHPx = constraints.maxHeight
                // Media/clock/khac: cung cong thuc morph width (ExpandedMaxWidth).
                val targetExpandedWidth =
                    (maxWidth - SpaceLg * 2).coerceAtMost(ExpandedMaxWidth)
                val targetWPx = with(density) { targetExpandedWidth.roundToPx() }
                val bottomPadPx = with(density) { KeyguardPillBottomOffset.roundToPx() }
                // Owner on dinh (event.id / battery) — doi owner → invalidate H khoa cu.
                val contentOwnerKey =
                    keyguardExpandedContentOwnerKey(
                        batterySelected = batterySelected,
                        eventId = targetEvent?.id,
                    )
                val effectiveLockedHPx =
                    effectiveLockedTargetHeightPx(lockedTargetHeight, contentOwnerKey)
                // Battery: fallback 300.dp truoc lock; Event: 224.dp.
                // Owner doi: KHONG dung expandedContentHeightDp cua owner cu (Clock H
                // khi sang Battery) — fallback theo owner moi roi measure retarget.
                val rememberedHPx =
                    with(density) {
                        when {
                            effectiveLockedHPx > 0 -> expandedContentHeightDp.roundToPx()
                            batterySelected ->
                                KeyguardBatteryExpandedHeightDefault.roundToPx()
                            lockedTargetHeight.owner == contentOwnerKey ->
                                expandedContentHeightDp.roundToPx()
                            else -> KeyguardExpandedHeightDefault.roundToPx()
                        }
                    }
                // Doc hostLoc trong offset/placement — khong o composition (WRAP→MATCH
                // getLocationInWindow cu → captured-host ≈ (0,0) → pill loe goc tren trai).
                val hostLoc = remember { IntArray(2) }

                val pillWPx =
                    when {
                        capturedPill.width > 0 -> capturedPill.width
                        else -> with(density) { pillWidthDp.roundToPx() }
                    }.coerceIn(
                        with(density) { MusicChipMinWidth.roundToPx() },
                        with(density) { MusicChipMaxWidth.roundToPx() },
                    )
                val pillHPx =
                    when {
                        capturedPill.height > 0 -> capturedPill.height
                        else -> with(density) { MusicChipHeight.roundToPx() }
                    }.coerceAtLeast(with(density) { MusicChipHeight.roundToPx() })
                val targetHPx =
                    (if (effectiveLockedHPx > 0) effectiveLockedHPx else rememberedHPx)
                        .coerceAtLeast(pillHPx)
                // Mot geom dung chung Event+Battery (scale host); chi leaf content khac.
                val p = morphProgress
                val geom =
                    computeKeyguardMorphGeom(
                        pRaw = p,
                        pillWPx = pillWPx.toFloat(),
                        pillHPx = pillHPx.toFloat(),
                        targetWPx = targetWPx.toFloat(),
                        targetHPx = targetHPx.toFloat(),
                    )
                // Mot parent transform (het dummy pulse) — rebound = geom p<0.
                val sx = geom.sx.coerceAtLeast(MorphScaleFloor)
                val sy = geom.sy.coerceAtLeast(MorphScaleFloor)
                // visualH/pillH — gate compact Battery (gan pill → khong stretch).
                val visualOverPill =
                    (sy * targetHPx.toFloat() / pillHPx.toFloat()).coerceAtLeast(0f)
                val batteryCompactVisible =
                    batterySelected &&
                        pillContentAlpha > 0.01f &&
                        visualOverPill <= BatteryCompactNearPillMax
                // Goc pill theo host SAU layout cung luot place (dong bo ban giao MATCH).
                // Neo day: top = B − targetH·sy (overshoot 3 canh, bottom on dinh ±2px).
                fun animOffsetForProgress(): IntOffset {
                    currentView.getLocationInWindow(hostLoc)
                    val originLeft =
                        if (capturedPill.width > 0) {
                            capturedPill.left - hostLoc[0]
                        } else {
                            (maxWPx - pillWPx) / 2
                        }
                    val originTop =
                        if (capturedPill.height > 0) {
                            capturedPill.top - hostLoc[1]
                        } else {
                            maxHPx - bottomPadPx - pillHPx
                        }
                    val anchorBottom = (originTop + pillHPx).toFloat()
                    val (left, top) =
                        morphOffsetTopLeft(
                            sx = sx,
                            sy = sy,
                            pillWPx = pillWPx.toFloat(),
                            targetWPx = targetWPx.toFloat(),
                            targetHPx = targetHPx.toFloat(),
                            pOffset = geom.pOffset,
                            originLeftPx = originLeft.toFloat(),
                            anchorBottomPx = anchorBottom,
                            maxWidthPx = maxWPx.toFloat(),
                        )
                    return IntOffset(left.roundToInt(), top.roundToInt())
                }
                // Corner: bu sx/sy elliptical (khong /sy scalar → rx/ry≈sx/sy).
                val visualCornerPx =
                    with(density) {
                        lerpDp(KeyguardPillCorner, expandCornerTarget, geom.pCorner).toPx()
                    }
                val localCorners =
                    morphLocalCornerRadii(
                        visualCornerPx = visualCornerPx,
                        sx = sx,
                        sy = sy,
                        targetHPx = targetHPx.toFloat(),
                        targetWPx = targetWPx.toFloat(),
                    )
                val localCornerXDp = with(density) { localCorners.localRxPx.toDp() }
                val localCornerYDp = with(density) { localCorners.localRyPx.toDp() }
                val targetWDp = with(density) { targetWPx.toDp() }
                val targetHDp = with(density) { targetHPx.toDp() }

                AnimatedVisibility(
                    visible = morphTarget,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(180)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInteropFilter {
                                if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                                    viewModel.avoidFalsingGesture()
                                }
                                false
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.keyguardExpansion.collapse() },
                            ),
                    )
                }

                // Draw the under-card BEFORE the morph surface, not inside the compact-content
                // overlay. Its background/content must stay behind the selected card's glass.
                // Battery-only (targetEvent=null): khong under peek.
                if (targetEvent != null &&
                    chipOnKeyguard &&
                    underRevealAlpha > 0f &&
                    cycleEvents.size > 1
                ) {
                    val selectedIndex = cycleEvents.indexOfFirst { it.id == targetEvent.id }
                    if (selectedIndex >= 0) {
                        val underEvent = cycleEvents[(selectedIndex + 1) % cycleEvents.size]
                        val underController = rememberExpandableController(
                            color = Color.Transparent,
                            shape = ChipShape,
                        )
                        val underPeekPx =
                            with(density) { StackRestYDp[1].dp.toPx() }
                        Box(
                            modifier = Modifier
                                .offset {
                                    val anim = animOffsetForProgress()
                                    val visualBottom = anim.y + targetHPx * sy
                                    val underLeft =
                                        anim.x + (targetWPx * sx - pillWPx) / 2f
                                    val underTop =
                                        visualBottom - pillHPx +
                                            underPeekPx * underRevealAlpha
                                    IntOffset(
                                        underLeft.roundToInt(),
                                        underTop.roundToInt(),
                                    )
                                }
                                .requiredSize(
                                    width = with(density) { pillWPx.toDp() },
                                    height = with(density) { pillHPx.toDp() },
                                )
                                .zIndex(1f)
                                .graphicsLayer {
                                    alpha = underRevealAlpha
                                    // Follow the final spring undershoot without exposing the
                                    // under-card's top or sides beyond the selected card.
                                    scaleX = StackRestScale[1] *
                                        (sx * targetWPx / pillWPx).coerceAtMost(1f)
                                    scaleY = StackRestScale[1] *
                                        (sy * targetHPx / pillHPx).coerceAtMost(1f)
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            KeyguardPillEventChip(
                                event = underEvent,
                                viewModel = viewModel,
                                expandableController = underController,
                                batteryString = batteryString,
                                blurAlpha = underRevealAlpha,
                                rowClickEnabled = false,
                                actionsEnabled = false,
                            )
                        }
                    }
                }

                // Host scale o target size — Event + Battery chung (ONE parent transform).
                val morphGlassMod =
                    Modifier
                        .offset { animOffsetForProgress() }
                        .graphicsLayer {
                            scaleX = sx
                            scaleY = sy
                            transformOrigin = TransformOrigin(0f, 0f)
                            clip = false
                        }
                        .requiredSize(width = targetWDp, height = targetHDp)
                sharedGlass(
                    localCornerXDp to localCornerYDp,
                    morphGlassMod
                        .zIndex(2f)
                        // Chi avoidFalsing — khong awaitFirstDown/disallow o host
                        // (se huy onClick Media / hit Clock RV). Collapse: KeyguardExpandedContent.
                        .pointerInteropFilter {
                            if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                                viewModel.avoidFalsingGesture()
                            }
                            false
                        },
                ) {
                    // Absorber phia sau noi dung: nuot cham trong the, khong xuong backdrop.
                    // Khong boc clickable quanh AndroidView (chan Clock RV / Media hit).
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {},
                                ),
                    )
                    val showPillBranch =
                        batteryCompactVisible ||
                            (!batterySelected && pillVisible && displayEvent != null)
                    // Composition co dinh (pill roi expand) — doi zIndex, khong if/else
                    // doi thu tu (tranh dispose/recreate AndroidView/RemoteViews giua morph).
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (expandInteractive) 0f else 1f),
                    ) {
                        if (showPillBranch) {
                            if (batteryCompactVisible) {
                                KeyguardMorphBranchGate(
                                    interactive = pillInteractive,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                val baseX =
                                                    targetWPx.toFloat() / pillWPx.toFloat()
                                                val baseY =
                                                    targetHPx.toFloat() / pillHPx.toFloat()
                                                alpha = pillContentAlpha
                                                scaleX = baseX
                                                scaleY = baseY
                                                transformOrigin = TransformOrigin.Center
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier.requiredSize(
                                                    width = with(density) { pillWPx.toDp() },
                                                    height = with(density) { pillHPx.toDp() },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CompositionLocalProvider(
                                                LocalKeyguardYieldGlassToMorph provides true,
                                            ) {
                                                KeyguardBatteryPillContent(
                                                    info = batteryInfo,
                                                    keyguardBatteryChipMode =
                                                        keyguardBatteryChipMode,
                                                    batteryString = batteryString,
                                                    contentAlpha = 1f,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (!batterySelected && pillVisible && displayEvent != null) {
                                KeyguardMorphBranchGate(
                                    interactive = pillInteractive,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = pillContentAlpha
                                                scaleX =
                                                    targetWPx.toFloat() / pillWPx.toFloat()
                                                scaleY =
                                                    targetHPx.toFloat() / pillHPx.toFloat()
                                                transformOrigin = TransformOrigin.Center
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        KeyguardPillSwipeStack(
                                            displayEvent = displayEvent,
                                            cycleEvents = listOf(displayEvent),
                                            viewModel = viewModel,
                                            touchSlop = touchSlop,
                                            chipBlurAlpha = pillContentAlpha,
                                            expandableController = expandableController,
                                            batteryString = batteryString,
                                            gesturesEnabled =
                                                gesturesEnabled && pillInteractive,
                                            yieldGlassOnlyTop = true,
                                            underRevealAlpha = 0f,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (expandInteractive) 1f else 0f),
                    ) {
                        KeyguardMorphBranchGate(
                            interactive = expandInteractive,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = expandContentAlpha },
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredWidth(targetWDp)
                                        .wrapContentHeight(
                                            align = Alignment.Bottom,
                                            unbounded = true,
                                        )
                                        .onSizeChanged { sz ->
                                            val owner = contentOwnerKey ?: return@onSizeChanged
                                            lockContentHeightPx(
                                                hPx = sz.height,
                                                measuredWPx = sz.width,
                                                targetWPx = targetWPx,
                                                pillHPx = pillHPx,
                                                owner = owner,
                                            )
                                        },
                                ) {
                                    if (batterySelected) {
                                        KeyguardBatteryExpandedCard(
                                            info = batteryInfo,
                                            batteryString = batteryString,
                                        )
                                    } else if (targetEvent != null) {
                                        KeyguardExpandedContent(
                                            event = targetEvent,
                                            allEvents =
                                                chipState?.allEvents ?: listOf(targetEvent),
                                            interactor = viewModel.interactor,
                                            onCollapse = {
                                                viewModel.keyguardExpansion.collapse()
                                            },
                                            onAvoidFalsing = {
                                                viewModel.avoidFalsingGesture()
                                            },
                                            hapticsViewModelFactory =
                                                viewModel.interactor
                                                    .sliderHapticsViewModelFactory,
                                            asPillAnchoredPopup = true,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (chipOnKeyguard) {
            // Collapsed: nen movable NAM TRONG lop the dang chon (cung tx/ty/scale).
            // Mo panel → sharedGlass chuyen sang morph host (call-site kia).
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(durationMillis = 200, delayMillis = 300)) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec =
                            tween(durationMillis = 200, delayMillis = 300),
                    ),
                exit = fadeOut(motionScheme.fastEffectsSpec()) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = motionScheme.fastSpatialSpec(),
                    ),
            ) {
                Box(
                    modifier = Modifier.onSizeChanged { sz ->
                        if (sz.width > 0) {
                            pillWidthDp = with(density) { sz.width.toDp() }
                        }
                    },
                ) {
                    if (displayEvent != null) {
                        KeyguardPillSwipeStack(
                            displayEvent = displayEvent,
                            cycleEvents = cycleEvents,
                            viewModel = viewModel,
                            touchSlop = touchSlop,
                            chipBlurAlpha = 1f,
                            expandableController = expandableController,
                            batteryString = batteryString,
                            gesturesEnabled = true,
                            yieldGlassOnlyTop = false,
                            // Nen di theo the top khi keo; under van GlassChip rieng.
                            sharedTopGlass = {
                                sharedGlass(
                                    KeyguardPillCorner to KeyguardPillCorner,
                                    Modifier.matchParentSize(),
                                    {},
                                )
                            },
                            underRevealAlpha = underRevealAlpha,
                        )
                    } else if (batteryPillActive) {
                        // Battery-only: rubber-band drag (mutableFloat — snapTo khong goi trong
                        // AwaitPointerEventScope / restricted suspending).
                        // Huy snapJob cu truoc keo/snap moi — tranh coroutine cu ghi de dragX/Y.
                        // isPillDragging giu true den het snap (khong clear o finally release).
                        var batteryDragX by remember { mutableFloatStateOf(0f) }
                        var batteryDragY by remember { mutableFloatStateOf(0f) }
                        val batteryDragScope = rememberCoroutineScope()
                        var batterySnapJob by remember { mutableStateOf<Job?>(null) }
                        DisposableEffect(Unit) {
                            onDispose {
                                batterySnapJob?.cancel()
                                batterySnapJob = null
                                viewModel.keyguardExpansion.setPillDragging(false)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = batteryDragX
                                    translationY = batteryDragY
                                }
                                .pointerInput(touchSlop) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = true)
                                        var dragging = false
                                        try {
                                            while (true) {
                                                val change = awaitPointerEvent().changes
                                                    .firstOrNull { it.id == down.id } ?: break
                                                if (!change.pressed) break
                                                val dy = change.position.y - down.position.y
                                                val dx = change.position.x - down.position.x
                                                if (!dragging && abs(dy) > touchSlop &&
                                                    abs(dy) > abs(dx)
                                                ) {
                                                    dragging = true
                                                    // Cat snap-back lan 1 neu van chay.
                                                    batterySnapJob?.cancel()
                                                    batterySnapJob = null
                                                    viewModel.keyguardExpansion.setPillDragging(true)
                                                    currentView.parent
                                                        ?.requestDisallowInterceptTouchEvent(true)
                                                }
                                                if (dragging) {
                                                    change.consume()
                                                    batteryDragY =
                                                        if (dy < 0) dy * 0.35f else dy * 0.25f
                                                    batteryDragX = dx * 0.1f
                                                }
                                            }
                                        } finally {
                                            if (dragging) {
                                                val snapX = batteryDragX
                                                val snapY = batteryDragY
                                                batterySnapJob?.cancel()
                                                batterySnapJob = batteryDragScope.launch {
                                                    // X/Y song song; cancel giua chung khong clear flag.
                                                    coroutineScope {
                                                        launch {
                                                            animate(
                                                                initialValue = snapX,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        StackSnapDamping,
                                                                    stiffness =
                                                                        StackSnapStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                batteryDragX = value
                                                            }
                                                        }
                                                        launch {
                                                            animate(
                                                                initialValue = snapY,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        StackSnapDamping,
                                                                    stiffness =
                                                                        StackSnapStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                batteryDragY = value
                                                            }
                                                        }
                                                    }
                                                    batteryDragX = 0f
                                                    batteryDragY = 0f
                                                    viewModel.keyguardExpansion
                                                        .setPillDragging(false)
                                                    currentView.parent
                                                        ?.requestDisallowInterceptTouchEvent(
                                                            false,
                                                        )
                                                }
                                            }
                                        }
                                    }
                                },
                        ) {
                            sharedGlass(
                                KeyguardPillCorner to KeyguardPillCorner,
                                Modifier,
                            ) {
                                Box(
                                    modifier =
                                        Modifier.pointerInteropFilter {
                                                if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                                                    viewModel.avoidFalsingGesture()
                                                }
                                                false
                                            }
                                            .clickable(
                                                interactionSource =
                                                    remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    viewModel.keyguardExpansion.expand()
                                                },
                                            ),
                                ) {
                                    CompositionLocalProvider(
                                        LocalKeyguardYieldGlassToMorph provides true,
                                    ) {
                                        KeyguardBatteryPillContent(
                                            info = batteryInfo,
                                            keyguardBatteryChipMode = keyguardBatteryChipMode,
                                            batteryString = batteryString,
                                            contentAlpha = 1f,
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
    } // CompositionLocalProvider (suppress ripple lockscreen)
}

/**
 * Stack Samsung Now Bar: under peek + rawDrag follow + spring settle/reorder.
 * Gesture Main + requireUnconsumed (nut play/pause nhan tap); haptic CHI luc commit.
 * Khong AnimatedContent cycle (gay rung khi giu).
 */
@Composable
private fun KeyguardPillSwipeStack(
    displayEvent: IslandEvent,
    cycleEvents: List<IslandEvent>,
    viewModel: AxDynamicBarChipViewModel,
    touchSlop: Float,
    chipBlurAlpha: Float,
    expandableController: ExpandableController,
    batteryString: String,
    gesturesEnabled: Boolean = true,
    /** Morph host da co sharedGlass: chi depth0 bo GlassChip; under giu nen. */
    yieldGlassOnlyTop: Boolean = false,
    /**
     * Collapsed: dat nen movable TRONG lop graphicsLayer cua the dang chon
     * (cung translation/scale). Null khi morph host so huu nen.
     */
    sharedTopGlass: (@Composable BoxScope.() -> Unit)? = null,
    /** Alpha the duoi (0..1) — fade sau khi khung ve pill; morph = 0. */
    underRevealAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    val currentView = LocalView.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var isSnappingBack by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var rawDragX by remember { mutableFloatStateOf(0f) }
    var rawDragY by remember { mutableFloatStateOf(0f) }
    var motionCards by remember { mutableStateOf<List<StackCardMotion>>(emptyList()) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val currentEvents by rememberUpdatedState(cycleEvents)
    val currentTop by rememberUpdatedState(displayEvent)

    val pillHeightPx = with(density) { MusicChipHeight.toPx() }
    val yLimitPx = -(pillHeightPx + with(density) { KeyguardStackDyOffset.toPx() })
    val restYPx = remember(density) {
        FloatArray(StackRestYDp.size) { d ->
            with(density) { StackRestYDp[d].dp.toPx() }
        }
    }
    val underController = rememberExpandableController(
        color = Color.Transparent,
        shape = ChipShape,
    )

    val hasStack = cycleEvents.size >= 2
    val displayCards: List<StackCardMotion> =
        if (reordering && motionCards.isNotEmpty()) {
            motionCards
        } else if (hasStack) {
            val items = cycleEvents
            val idx = items.indexOfFirst { it.id == displayEvent.id }
                .let { if (it >= 0) it else 0 }
            val maxDepth = min(StackMaxDepth, items.size - 1)
            val dragX = if (isDragging || isSnappingBack) rawDragX else 0f
            val dragY = if (isDragging || isSnappingBack) rawDragY else 0f
            (0..maxDepth).map { depth ->
                StackCardMotion(
                    event = items[(idx + depth) % items.size],
                    depth = depth,
                    tx = stackFollowX(depth, dragX),
                    ty = restYPx[depth] + stackFollowY(depth, dragY, yLimitPx),
                    scale = StackRestScale[depth],
                )
            }
        } else {
            // Mot event van can lop top di chuyen duoc de rubber-band drag.
            listOf(
                StackCardMotion(
                    event = displayEvent,
                    depth = 0,
                    tx = if (isDragging || isSnappingBack) rawDragX else 0f,
                    ty = if (isDragging || isSnappingBack) rawDragY else 0f,
                    scale = 1f,
                )
            )
        }

    // Dispose: cat settleJob + ha flag — tranh Job cu ghi de sau leave composition.
    DisposableEffect(viewModel.keyguardExpansion) {
        onDispose {
            settleJob?.cancel()
            settleJob = null
            viewModel.keyguardExpansion.setPillDragging(false)
        }
    }

    // Lang nghe o cha de nut action native van nam trong hit-test.
    val stackGestureModifier =
        if (gesturesEnabled) {
            Modifier
                .pointerInput(displayEvent.id, cycleEvents.size, touchSlop) {
                    awaitEachGesture {
                        // Main + requireUnconsumed: nut pill consume truoc; keo stack khi khong phai nut.
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val pointerId = down.id
                        viewModel.avoidFalsingGesture()
                        currentView.parent?.requestDisallowInterceptTouchEvent(true)
                        val startY = down.position.y
                        val startX = down.position.x
                        var dragStarted = false
                        var accumulatedDy = 0f
                        var accumulatedDx = 0f
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPointerInputChange(down)
                        if (reordering || isSnappingBack) {
                            settleJob?.cancel()
                            settleJob = null
                            reordering = false
                            isSnappingBack = false
                            motionCards = emptyList()
                            rawDragX = 0f
                            rawDragY = 0f
                        }
                        var committedReorder = false
                        // startSnapBack: guard isSnappingBack — finally abort khong double-start.
                        // Flag dragging chi ha sau animate xong (hoac dispose/expand).
                        fun startSnapBack() {
                            if (committedReorder || isSnappingBack) return
                            isDragging = false
                            val snapY = rawDragY
                            val snapX = rawDragX
                            isSnappingBack = true
                            settleJob?.cancel()
                            settleJob = coroutineScope.launch {
                                // Song song X/Y + join (tranh nested launch; khong dung
                                // coroutineScope {} vi ten trung rememberCoroutineScope).
                                val snapYJob = launch {
                                    animate(
                                        initialValue = snapY,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = StackSnapDamping,
                                            stiffness = StackSnapStiffness,
                                        ),
                                    ) { value, _ -> rawDragY = value }
                                }
                                val snapXJob = launch {
                                    animate(
                                        initialValue = snapX,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = StackSnapDamping,
                                            stiffness = StackSnapStiffness,
                                        ),
                                    ) { value, _ -> rawDragX = value }
                                }
                                snapYJob.join()
                                snapXJob.join()
                                rawDragY = 0f
                                rawDragX = 0f
                                isSnappingBack = false
                                motionCards = emptyList()
                                viewModel.keyguardExpansion.setPillDragging(false)
                                currentView.parent
                                    ?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        try {
                            while (true) {
                                val pointerEvent = awaitPointerEvent()
                                val change =
                                    pointerEvent.changes.firstOrNull {
                                        it.id == pointerId
                                    } ?: continue
                                val dy = change.position.y - startY
                                val dx = change.position.x - startX
                                if (!dragStarted) {
                                    if (abs(dy) > touchSlop || abs(dx) > touchSlop) {
                                        // Chi bat dau keo doc khi truc Y chu dao.
                                        if (abs(dy) > abs(dx)) {
                                            dragStarted = true
                                            isDragging = true
                                            viewModel.keyguardExpansion.setPillDragging(true)
                                            isSnappingBack = false
                                            settleJob?.cancel()
                                            settleJob = null
                                        }
                                    }
                                }
                                if (dragStarted) {
                                    // Chi consume khi da xac dinh keo stack.
                                    change.consume()
                                    velocityTracker.addPointerInputChange(change)
                                    accumulatedDy = dy
                                    accumulatedDx = dx
                                    val items = currentEvents
                                    if (items.size <= 1) {
                                        // Rubber-band 1 card — khong rung khi giu
                                        rawDragY =
                                            if (dy < 0) dy * 0.35f else dy * 0.25f
                                        rawDragX = dx * 0.1f
                                    } else {
                                        rawDragY = dy
                                        rawDragX = dx
                                    }
                                }
                                if (!change.pressed) {
                                    if (dragStarted) {
                                        change.consume()
                                        isDragging = false
                                        val liveItems = currentEvents
                                        val liveCurrent = currentTop
                                        val velocity =
                                            velocityTracker.calculateVelocity()
                                        // Chi dung van toc dung truc (doc) de commit.
                                        val axisVertical =
                                            abs(velocity.y) >= abs(velocity.x)
                                        val vY =
                                            if (axisVertical) abs(velocity.y) else 0f
                                        val topTransY =
                                            stackFollowY(0, rawDragY, yLimitPx)
                                        val steepEnough =
                                            abs(accumulatedDx) < 0.001f ||
                                                abs(accumulatedDy / accumulatedDx) > 1f
                                        val shouldCommit =
                                            liveItems.size >= 2 &&
                                                steepEnough &&
                                                (
                                                    vY > StackCommitVelocityY ||
                                                        -topTransY > pillHeightPx
                                                    )
                                        if (shouldCommit) {
                                            // Haptic CHI luc commit cycle
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            val liveIdx =
                                                liveItems.indexOfFirst {
                                                    it.id == liveCurrent.id
                                                }.let { if (it >= 0) it else 0 }
                                            val maxDepth =
                                                min(StackMaxDepth, liveItems.size - 1)
                                            val count = maxDepth + 1
                                            val snapshot = (0..maxDepth).map { d ->
                                                StackCardMotion(
                                                    event = liveItems[
                                                        (liveIdx + d) % liveItems.size
                                                    ],
                                                    depth = d,
                                                    tx = stackFollowX(d, rawDragX),
                                                    ty = restYPx[d] +
                                                        stackFollowY(
                                                            d,
                                                            rawDragY,
                                                            yLimitPx,
                                                        ),
                                                    scale = StackRestScale[d],
                                                )
                                            }
                                            val remapped = snapshot.map { card ->
                                                val newDepth =
                                                    (card.depth - 1 + count) % count
                                                card.copy(depth = newDepth)
                                            }
                                            rawDragY = 0f
                                            rawDragX = 0f
                                            reordering = true
                                            committedReorder = true
                                            motionCards = remapped
                                            val targetEvent =
                                                remapped.first { it.depth == 0 }.event
                                            viewModel.pinEventById(targetEvent.id)

                                            settleJob?.cancel()
                                            settleJob = coroutineScope.launch {
                                                val jobs = remapped.map { card ->
                                                    launch {
                                                        val startTx = card.tx
                                                        val startTy = card.ty
                                                        val startScale = card.scale
                                                        val destTy = restYPx[card.depth]
                                                        val destScale =
                                                            StackRestScale[card.depth]
                                                        val scaleJob = launch {
                                                            animate(
                                                                initialValue = startScale,
                                                                targetValue = destScale,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        StackCommitScaleDamping,
                                                                    stiffness =
                                                                        StackCommitScaleStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                motionCards =
                                                                    motionCards.map {
                                                                        if (it.depth == card.depth) {
                                                                            it.copy(scale = value)
                                                                        } else it
                                                                    }
                                                            }
                                                        }
                                                        val xJob = launch {
                                                            animate(
                                                                initialValue = startTx,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        StackCommitXyDamping,
                                                                    stiffness =
                                                                        StackCommitXyStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                motionCards =
                                                                    motionCards.map {
                                                                        if (it.depth == card.depth) {
                                                                            it.copy(tx = value)
                                                                        } else it
                                                                    }
                                                            }
                                                        }
                                                        val yJob = launch {
                                                            animate(
                                                                initialValue = startTy,
                                                                targetValue = destTy,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        StackCommitXyDamping,
                                                                    stiffness =
                                                                        StackCommitXyStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                motionCards =
                                                                    motionCards.map {
                                                                        if (it.depth == card.depth) {
                                                                            it.copy(ty = value)
                                                                        } else it
                                                                    }
                                                            }
                                                        }
                                                        scaleJob.join()
                                                        xJob.join()
                                                        yJob.join()
                                                    }
                                                }
                                                jobs.forEach { it.join() }
                                                reordering = false
                                                motionCards = emptyList()
                                                viewModel.keyguardExpansion
                                                    .setPillDragging(false)
                                                currentView.parent
                                                    ?.requestDisallowInterceptTouchEvent(
                                                        false,
                                                    )
                                            }
                                        } else {
                                            // Snap-back — khong haptic, khong commit.
                                            startSnapBack()
                                        }
                                    } else if (
                                        dx * dx + dy * dy <= touchSlop * touchSlop
                                    ) {
                                        // Nut da consume DOWN (requireUnconsumed); con lai = toggle panel.
                                        change.consume()
                                        viewModel.keyguardExpansion.toggle(
                                            expandOnlyEventIdFor(currentTop),
                                        )
                                    }
                                    break
                                }
                            }
                        } finally {
                            // Abort giua drag (chua release path): snap 1 lan.
                            // Khong clear isPillDragging o day neu dang snap/reorder.
                            if (dragStarted && isDragging) {
                                startSnapBack()
                            } else if (!isSnappingBack && !reordering) {
                                viewModel.keyguardExpansion.setPillDragging(false)
                            }
                            if (!committedReorder && !isSnappingBack && !reordering) {
                                currentView.parent
                                    ?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                    }
                }
        } else {
            Modifier
        }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .graphicsLayer { clip = false }
            .then(stackGestureModifier)
            .pointerInteropFilter {
                when (it.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        viewModel.avoidFalsingGesture()
                        currentView.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!reordering && !isSnappingBack) {
                            currentView.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                false
            },
    ) {
        if (displayCards.isNotEmpty()) {
            val sorted = displayCards.sortedByDescending { it.depth }
            for (card in sorted) {
                // Giu subtree theo event.id — doi depth/tx/scale, khong recreate RemoteViews.
                key(card.event.id) {
                    val controller =
                        if (card.depth == 0) expandableController else underController
                    Box(
                        modifier = Modifier
                            .zIndex(stackZIndex(card.depth))
                            .graphicsLayer {
                                clip = false
                                translationX = card.tx
                                translationY = card.ty
                                scaleX = card.scale
                                scaleY = card.scale
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                alpha =
                                    if (card.depth > 0) {
                                        underRevealAlpha.coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }
                            },
                    ) {
                        // Chi chuyen lop nen khi doi depth; giu subtree cover theo event.id.
                        // Tranh AnimatedContent khoi tao lai luc nha tay commit the moi.
                        if (sharedTopGlass != null && card.depth == 0) {
                            sharedTopGlass()
                        }
                        CompositionLocalProvider(
                            LocalKeyguardYieldGlassToMorph provides
                                ((sharedTopGlass != null || yieldGlassOnlyTop) && card.depth == 0),
                        ) {
                            KeyguardPillEventChip(
                                event = card.event,
                                viewModel = viewModel,
                                expandableController = controller,
                                batteryString = batteryString,
                                blurAlpha = chipBlurAlpha,
                                rowClickEnabled = false,
                                actionsEnabled = card.depth == 0 && gesturesEnabled,
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.zIndex(3f)) {
                if (sharedTopGlass != null) {
                    sharedTopGlass()
                }
                CompositionLocalProvider(
                    LocalKeyguardYieldGlassToMorph provides
                        (sharedTopGlass != null || yieldGlassOnlyTop),
                ) {
                    KeyguardPillEventChip(
                        event = displayEvent,
                        viewModel = viewModel,
                        expandableController = expandableController,
                        batteryString = batteryString,
                        blurAlpha = chipBlurAlpha,
                        rowClickEnabled = false,
                        actionsEnabled = gesturesEnabled,
                    )
                }
            }
        }


    }
}

@Composable
private fun KeyguardPillEventChip(
    event: IslandEvent,
    viewModel: AxDynamicBarChipViewModel,
    expandableController: ExpandableController,
    batteryString: String,
    blurAlpha: Float,
    rowClickEnabled: Boolean,
    actionsEnabled: Boolean = true,
) {
    val motionScheme = MaterialTheme.motionScheme
    val accent by animateColorAsState(
        accentColorFor(event),
        motionScheme.fastEffectsSpec(),
        label = "kg_accent",
    )
    val contentColor by animateColorAsState(
        OnCardText,
        motionScheme.fastEffectsSpec(),
        label = "kg_content",
    )
    val rawProgress = chipProgressFor(event)
    val progressTarget = rawProgress ?: 0f
    val progressAnim = remember { Animatable(progressTarget) }
    val progressSpec = motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(progressTarget) {
        if (abs(progressTarget - progressAnim.value) > 0.05f) {
            progressAnim.animateTo(progressTarget, progressSpec)
        } else {
            progressAnim.snapTo(progressTarget)
        }
    }
    val progress = if (rawProgress != null) progressAnim.value else null

    Expandable(
        controller = expandableController,
        onClick = null,
        defaultMinSize = false,
    ) { expandable ->
        KeyguardChipBody(
            event = event,
            accent = accent,
            contentColor = contentColor,
            progress = progress,
            viewModel = viewModel,
            batteryString = batteryString,
            aospChipExpandable = expandable,
            blurAlpha = blurAlpha,
            rowClickEnabled = rowClickEnabled,
            actionsEnabled = actionsEnabled,
        )
    }
}

@Composable
private fun KeyguardChipBody(
    event: IslandEvent,
    accent: Color,
    contentColor: Color,
    progress: Float?,
    viewModel: AxDynamicBarChipViewModel,
    batteryString: String = "",
    aospChipExpandable: SystemUiExpandable,
    blurAlpha: Float,
    rowClickEnabled: Boolean = true,
    actionsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme
    var toggleCount by remember { mutableIntStateOf(0) }
    val isMedia = event is IslandEvent.Media
    val isChronometer =
        event is IslandEvent.Timer || event is IslandEvent.Stopwatch
    // Media + Clock chronometer: cung envelope Now Bar (58 x min 130).
    val useMediaEnvelope = isMedia || isChronometer
    val parts = rememberChargingParts(batteryString)
    val isMultiLineCharging = event is IslandEvent.Charging && parts.size >= 2
    val dynamicHeight = when {
        useMediaEnvelope -> MusicChipHeight
        isMultiLineCharging -> MultiLineChipHeight
        else -> ChipHeight
    }
    val dynamicMinWidth = if (useMediaEnvelope) MusicChipMinWidth else 48.dp
    val washColor = accent.copy(alpha = AlphaIconBg)

    GlassChip(height = dynamicHeight, blurAlpha = blurAlpha) {
        Row(
            modifier = Modifier
                .height(dynamicHeight)
                .widthIn(min = dynamicMinWidth, max = MusicChipMaxWidth)
                .clip(ChipShape)
                .squishAnimation(toggleCount)
                .animateContentSize(motionScheme.defaultSpatialSpec())
                .then(
                    if (progress != null) {
                        // Faint content-toned track; the progress itself keeps the event tone.
                        val trackColor = contentColor.copy(alpha = AlphaTrack)
                        val barColor = accent
                        Modifier.drawWithContent {
                            drawContent()
                            val barH = SizeStrokeWidth.toPx()
                            val y = size.height - barH
                            drawRect(trackColor, Offset(0f, y), Size(size.width, barH))
                            drawRect(barColor, Offset(0f, y), Size(size.width * progress, barH))
                        }
                    } else Modifier
                )
                .pointerInteropFilter {
                    // Chi avoidFalsing — khong disallow o day (de nut con / swipe stack xu ly).
                    if (it.actionMasked == MotionEvent.ACTION_DOWN) {
                        viewModel.avoidFalsingGesture()
                    }
                    false
                }
                .then(
                    if (rowClickEnabled) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            when (event) {
                                is IslandEvent.Notification ->
                                    viewModel.launchNotificationFromKeyguard(event)

                                is IslandEvent.AospChip -> {
                                    viewModel.handleAospChipTap(event, aospChipExpandable)
                                }

                                is IslandEvent.KeyguardIndication,
                                is IslandEvent.AppSwitch -> { }
                                else -> {
                                    val wasExpanded = viewModel.keyguardExpansion.isExpanded.value
                                    viewModel.keyguardExpansion.toggle(expandOnlyEventIdFor(event))
                                    if (!wasExpanded) toggleCount++
                                }
                            }
                        }
                    } else Modifier
                )
                .padding(start = if (useMediaEnvelope) SpaceMd else SpaceSm, end = SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (event is IslandEvent.Media) {
                // Cover + tieu de chung 1 transition; fading edge thay Spacer — chu rong + bien mat muot.
                AnimatedContent(
                    targetState = event,
                    transitionSpec = {
                        mediaTrackSwitchTransition(motionScheme).using(SizeTransform(clip = false))
                    },
                    contentKey = { mediaTrackSwitchKey(it) },
                    label = "kg_media",
                    modifier = Modifier.weight(1f),
                ) { media ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val art = media.albumArt
                        if (art != null) {
                            Image(
                                bitmap = art.toSquareScaledBitmap(MusicActionSize),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(MusicActionSize)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            PillEventIcon(media, tint = accent, animated = false)
                        }
                        // Samsung: TextView MARQUEE + fading edge native (khong Compose Offscreen).
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            NativeMarqueeText(
                                text = media.track.ifEmpty {
                                    stringResource(R.string.ax_dynamic_bar_music)
                                },
                                color = contentColor,
                                textSize = 13.sp,
                                fontWeight = PillPrimary.fontWeight?.weight ?: 500,
                                fadeLength = SpaceMd,
                                gravity = android.view.Gravity.CENTER_HORIZONTAL or
                                    android.view.Gravity.CENTER_VERTICAL,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (media.artist.isNotBlank()) {
                                NativeMarqueeText(
                                    text = media.artist,
                                    color = contentColor.copy(alpha = AlphaSecondary),
                                    textSize = 12.sp,
                                    fontWeight = 400,
                                    fadeLength = SpaceMd,
                                    gravity = android.view.Gravity.CENTER_HORIZONTAL or
                                        android.view.Gravity.CENTER_VERTICAL,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(MusicPlayPauseSize)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = AlphaAccent))
                            .clickable(
                                enabled = actionsEnabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.togglePlayPause(event.mediaKey) },
                            ),
                ) {
                    Icon(
                        if (event.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            if (event.isPlaying) R.string.ax_dynamic_bar_pause
                            else R.string.ax_dynamic_bar_play,
                        ),
                        tint = accent,
                        modifier = Modifier.size(MusicPlayPauseIconSize),
                    )
                }
            } else if (isChronometer) {
                // Dong bo khung media: icon lon + so + nut play/pause cung size Music*.
                val chronoStyle = eventStyleFor(event)
                Box(
                    modifier = Modifier.size(MusicActionSize),
                    contentAlignment = Alignment.Center,
                ) {
                    chronoStyle.icon?.let { vector ->
                        Icon(
                            vector,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(MusicActionIconSize),
                        )
                    } ?: PillEventIcon(event, tint = accent, animated = false)
                }
                Spacer(Modifier.width(SpaceXs))
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 110.dp).fillMaxWidth(),
                ) {
                    KeyguardChronometerReadout(event, contentColor)
                }
                val chronoActions = actionsFor(event, context)
                if (chronoActions.isNotEmpty()) {
                    Spacer(Modifier.width(SpaceXs))
                    chronoActions.forEach { action ->
                        Spacer(Modifier.width(SpaceXxs))
                        ActionButton(
                            enabled = actionsEnabled,
                            icon = action.icon,
                            color = accent,
                            bgColor = accent.copy(alpha = AlphaAccent),
                            onClick = { action.perform(viewModel, event, context) },
                            size = MusicPlayPauseSize,
                            iconSize = MusicPlayPauseIconSize,
                        )
                    }
                }
            } else if (event is IslandEvent.Sports && event.team2Name.isNotEmpty()) {
                SportsChipTeamBadge(event.team1Name, event.team1Icon, contentColor)
                Spacer(Modifier.width(SpaceXs))
                Text(
                    if (event.score1.isNotEmpty()) "${event.score1} - ${event.score2}"
                        else stringResource(R.string.ax_dynamic_bar_sports_vs),
                    style = PillPrimary,
                    color = contentColor,
                    maxLines = 1,
                )
                Spacer(Modifier.width(SpaceXs))
                SportsChipTeamBadge(event.team2Name, event.team2Icon, contentColor)
            } else {
                AnimatedContent(
                    targetState = event,
                    transitionSpec = {
                        (fadeIn(motionScheme.defaultEffectsSpec()) +
                            scaleIn(initialScale = 0.85f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
                            (fadeOut(motionScheme.fastEffectsSpec()) +
                                scaleOut(targetScale = 0.85f, animationSpec = motionScheme.fastSpatialSpec())) using
                            SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() })
                    },
                    contentKey = { iconKeyFor(it) },
                    label = "kg_chip_icon",
                ) { ev ->
                    PillEventIcon(ev, tint = accent, animated = false)
                }
                Spacer(Modifier.width(SpaceXs))
                AnimatedContent(
                    targetState = event,
                    transitionSpec = {
                        (fadeIn(motionScheme.defaultEffectsSpec()) +
                            scaleIn(initialScale = 0.85f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
                            (fadeOut(motionScheme.fastEffectsSpec()) +
                                scaleOut(targetScale = 0.85f, animationSpec = motionScheme.fastSpatialSpec())) using
                            SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> motionScheme.defaultSpatialSpec() })
                    },
                    contentKey = { textKeyFor(it) },
                    label = "kg_chip_text",
                    modifier = Modifier.weight(1f, fill = false),
                ) { ev ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeyguardPrimaryText(ev, contentColor, Modifier.weight(1f, fill = false).widthIn(max = 110.dp), batteryString)
                        val secondary = secondaryTextFor(ev)
                        if (secondary != null) {
                            Text(
                                " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = AlphaTertiary),
                            )
                            Text(
                                secondary,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = AlphaSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 60.dp).basicMarquee(iterations = 1),
                            )
                        }
                    }
                }
            }

            // Media / chronometer: action da nam trong nhanh rieng (MusicPlayPauseSize).
            if (event !is IslandEvent.Media && !isChronometer) {
                val actionsCtx = LocalContext.current
                val actions = actionsFor(event, actionsCtx)
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.width(SpaceXs))
                    actions.forEach { action ->
                        Spacer(Modifier.width(SpaceXxs))
                        ActionButton(
                            enabled = actionsEnabled,
                            icon = action.icon,
                            color = accent,
                            bgColor = washColor,
                            onClick = { action.perform(viewModel, event, context) },
                            size = ActionSize,
                            iconSize = ActionIconSize,
                        )
                    }
                }
            }
        }
    }
}

/** Chronometer lockscreen: Text mono lon (khong CompactChronometer StatusBar 12sp/14dp). */
@Composable
private fun KeyguardChronometerReadout(event: IslandEvent, color: Color) {
    when (event) {
        is IslandEvent.Timer -> {
            val hasTime =
                event.endTimeMs > 0L ||
                    event.pausedRemainingMs > 0L ||
                    event.endElapsedRealtimeMs > 0L
            if (!hasTime) {
                Text(
                    event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_timer) },
                    style = PillPrimary.copy(fontSize = 13.sp),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                return
            }
            val remainingMs =
                rememberDisplayedChronometerMs(
                    identityKey =
                        listOf(
                            event.endTimeMs,
                            event.isPaused,
                            event.pausedRemainingMs,
                            event.endElapsedRealtimeMs,
                        ),
                    ticking = !event.isPaused,
                    tickMs = 1000L,
                    isCountdown = true,
                    compute = { event.displayedRemainingMs() },
                )
            Text(
                formatCountdownCompact(remainingMs),
                style =
                    PillMono.copy(
                        fontSize = KeyguardChronometerTextSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                color = if (event.isPaused) color.copy(alpha = AlphaSecondary) else color,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is IslandEvent.Stopwatch -> {
            val elapsedMs =
                rememberDisplayedChronometerMs(
                    identityKey =
                        listOf(
                            event.startTimeMs,
                            event.startElapsedRealtimeMs,
                            event.isRunning,
                            event.pausedElapsedMs,
                        ),
                    ticking = event.isRunning,
                    tickMs = 1000L,
                    compute = { event.displayedElapsedMs() },
                )
            Text(
                formatStopwatchCompact(elapsedMs),
                style =
                    PillMono.copy(
                        fontSize = KeyguardChronometerTextSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                color = if (event.isRunning) color else color.copy(alpha = AlphaSecondary),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> Unit
    }
}

@Composable
private fun KeyguardPrimaryText(event: IslandEvent, color: Color, modifier: Modifier, batteryString: String = "") {
    when (event) {
        is IslandEvent.AudioRecording -> when (event.state) {
            RecordingState.RECORDING -> ElapsedTimeText(
                event.startTimeMs, color, modifier, event.pausedDurationMs,
            )
            RecordingState.PAUSED -> MarqueeText(stringResource(R.string.ax_dynamic_bar_paused), color, modifier)
            RecordingState.SAVED -> MarqueeText(stringResource(R.string.ax_dynamic_bar_saved), color, modifier)
        }
        is IslandEvent.Media -> MarqueeText(event.track, color, modifier)
        // Chronometer: KeyguardChronometerReadout trong nhanh useMediaEnvelope.
        is IslandEvent.Timer,
        is IslandEvent.Stopwatch -> Unit
        is IslandEvent.Notification -> MarqueeText(event.title ?: event.appName, color, modifier)
        is IslandEvent.Charging -> {
            val parts = rememberChargingParts(batteryString)
            if (parts.size >= 2) {
                Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
                    Text(
                        parts[0],
                        style = PillPrimary,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        parts[1],
                        style = TsCaption,
                        color = color.copy(alpha = AlphaSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                MarqueeText(if (parts.isNotEmpty()) parts[0] else "${event.level}%", color, modifier)
            }
        }
        is IslandEvent.Bluetooth -> MarqueeText(event.deviceName.take(12), color, modifier)
        is IslandEvent.Hotspot -> {
            val hotspotLabel = stringResource(R.string.ax_dynamic_bar_hotspot)
            MarqueeText(
                if (event.numDevices > 0) "$hotspotLabel · ${event.numDevices}" else hotspotLabel,
                color, modifier,
            )
        }
        is IslandEvent.Alarm -> MarqueeText(event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_alarm) }, color, modifier)
        is IslandEvent.Call -> {
            if (event.callStartTimeMs > 0) PillEventText(event, modifier, overrideColor = color)
            else MarqueeText(event.callType ?: stringResource(R.string.ax_dynamic_bar_call), color, modifier)
        }
        is IslandEvent.Torch -> MarqueeText(
            if (event.supportsLevel) "${(event.level.toFloat() / event.maxLevel * 100).toInt()}%"
            else stringResource(R.string.ax_dynamic_bar_flashlight),
            color, modifier,
        )
        is IslandEvent.RingerMode -> MarqueeText(event.label, color, modifier)
        is IslandEvent.Vpn -> MarqueeText(stringResource(R.string.ax_dynamic_bar_vpn_active), color, modifier)
        is IslandEvent.Clipboard -> MarqueeText(
            event.preview.ifEmpty { stringResource(R.string.ax_dynamic_bar_copied) }, color, modifier,
        )
        is IslandEvent.BiometricUnlock -> MarqueeText(stringResource(R.string.ax_dynamic_bar_unlocked), color, modifier)
        is IslandEvent.AppSwitch -> MarqueeText(stringResource(R.string.ax_dynamic_bar_recents), color, modifier)
        is IslandEvent.PromotedOngoing -> MarqueeText(
            event.shortText.ifEmpty { event.title.ifEmpty { event.appName } }, color, modifier,
        )
        is IslandEvent.Sports -> MarqueeText(
            "${event.score1}-${event.score2}", color, modifier,
        )
        is IslandEvent.NowPlaying -> MarqueeText(
            "${event.songTitle} · ${event.artist}".trimEnd(' ', '·', ' '), color, modifier,
        )
        is IslandEvent.KeyguardIndication -> MarqueeText(event.text, color, modifier)
        is IslandEvent.AospChip -> PillEventText(event, modifier, overrideColor = color)
    }
}

@Composable
private fun secondaryTextFor(event: IslandEvent): String? = when (event) {
    is IslandEvent.Media -> event.artist.takeIf { it.isNotBlank() }
    is IslandEvent.AudioRecording -> event.appName.takeIf { it.isNotBlank() }
    is IslandEvent.Timer,
    is IslandEvent.Stopwatch,
    is IslandEvent.Hotspot,
    is IslandEvent.Vpn,
    is IslandEvent.AppSwitch -> null
    is IslandEvent.Charging -> event.timeRemaining
    is IslandEvent.Bluetooth -> event.batterySummary()
    is IslandEvent.Alarm -> {
        if (event.triggerTimeMs > 0) {
            val cal = Calendar.getInstance().apply { timeInMillis = event.triggerTimeMs }
            val triggerTime = "%d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            triggerTime.takeIfDistinctFrom(event.label)
        } else null
    }
    is IslandEvent.Call -> event.callerName
    is IslandEvent.BiometricUnlock -> event.sourceName
    is IslandEvent.Notification ->
        event.text
            ?.takeIf { it.isNotBlank() }
            ?.takeIfDistinctFrom(event.title, event.appName)
            ?.take(30)
    is IslandEvent.PromotedOngoing ->
        event.text
            .takeIf { it.isNotBlank() }
            ?.takeIfDistinctFrom(event.shortText, event.title, event.appName)
            ?.take(20)
    is IslandEvent.Sports -> "${event.team1Name} ${stringResource(R.string.ax_dynamic_bar_sports_vs)} ${event.team2Name}"
    is IslandEvent.KeyguardIndication -> when (event.indicationType) {
        IslandEvent.KeyguardIndication.IndicationType.BIOMETRIC -> stringResource(R.string.ax_dynamic_bar_biometric)
        IslandEvent.KeyguardIndication.IndicationType.TRUST -> stringResource(R.string.ax_dynamic_bar_trust)
        IslandEvent.KeyguardIndication.IndicationType.ALIGNMENT -> stringResource(R.string.ax_dynamic_bar_alignment)
        else -> null
    }
    else -> null
}

private fun String.takeIfDistinctFrom(vararg others: String?): String? {
    val value = trim()
    if (value.isEmpty()) return null
    return value.takeIf { candidate ->
        others.none { other -> candidate.equals(other?.trim(), ignoreCase = true) }
    }
}

@Composable
private fun SportsChipTeamBadge(name: String, icon: Drawable?, contentColor: Color) {
    if (icon != null) {
        Image(
            bitmap = icon.toScaledBitmap(ChipIconSize),
            contentDescription = name,
            modifier = Modifier.size(ChipIconSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(ChipIconSize).clip(CircleShape)
                .background(contentColor.copy(alpha = AlphaIconBg)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(2).uppercase(),
                style = TsBadge,
                color = contentColor,
            )
        }
    }
}

private enum class ActionIcon { PLAY, PAUSE, STOP, SKIP_PREV, SKIP_NEXT }

private data class ChipAction(
    val icon: ActionIcon,
    val perform: (AxDynamicBarChipViewModel, IslandEvent, Context) -> Unit,
)

private fun actionsFor(event: IslandEvent, context: Context): List<ChipAction> = when (event) {
    is IslandEvent.Media -> listOf(
        ChipAction(ActionIcon.SKIP_PREV) { vm, _, _ -> vm.skipPrev(event.mediaKey) },
        ChipAction(if (event.isPlaying) ActionIcon.PAUSE else ActionIcon.PLAY) { vm, _, _ ->
            vm.togglePlayPause(event.mediaKey)
        },
        ChipAction(ActionIcon.SKIP_NEXT) { vm, _, _ -> vm.skipNext(event.mediaKey) },
    )
    is IslandEvent.AudioRecording -> {
        val classified = event.actions.map { it to it.action.classify(context, it.action.actionIntent?.creatorPackage ?: context.packageName) }
        val pauseResume = classified.firstOrNull { (_, k) -> k == NotificationActionType.PAUSE || k == NotificationActionType.RESUME }?.first
        val stop = classified.firstOrNull { (_, k) -> k == NotificationActionType.STOP || k == NotificationActionType.DELETE }?.first
        listOfNotNull(
            pauseResume?.let { action ->
                ChipAction(
                    if (event.state == RecordingState.RECORDING) ActionIcon.PAUSE else ActionIcon.PLAY,
                ) { _, _, ctx -> action.action.actionIntent?.sendWithBal(ctx) }
            },
            stop?.let { action ->
                ChipAction(ActionIcon.STOP) { _, _, ctx ->
                    action.action.actionIntent?.sendWithBal(ctx)
                }
            },
        )
    }
    is IslandEvent.Timer -> {
        val toggleAction = event.actions.firstOrNull()
        listOfNotNull(
            toggleAction?.let { action ->
                ChipAction(
                    if (event.isPaused) ActionIcon.PLAY else ActionIcon.PAUSE,
                ) { _, _, ctx -> action.action.actionIntent?.sendWithBal(ctx) }
            },
        )
    }
    is IslandEvent.Stopwatch -> {
        val toggleAction = event.actions.firstOrNull()
        listOfNotNull(
            toggleAction?.let { action ->
                ChipAction(
                    if (event.isRunning) ActionIcon.PAUSE else ActionIcon.PLAY,
                ) { _, _, ctx -> action.action.actionIntent?.sendWithBal(ctx) }
            },
        )
    }
    is IslandEvent.Torch -> listOf(
        ChipAction(ActionIcon.STOP) { vm, event, _ -> vm.dismissEvent(event) },
    )
    else -> emptyList()
}

@Composable
private fun ActionButton(
    icon: ActionIcon,
    color: Color,
    bgColor: Color,
    onClick: () -> Unit,
    size: Dp = ActionSize,
    iconSize: Dp = ActionIconSize,
    enabled: Boolean = true,
) {
    val imageVector = when (icon) {
        ActionIcon.PLAY -> Icons.Filled.PlayArrow
        ActionIcon.PAUSE -> Icons.Filled.Pause
        ActionIcon.STOP -> Icons.Filled.Stop
        ActionIcon.SKIP_PREV -> Icons.Filled.SkipPrevious
        ActionIcon.SKIP_NEXT -> Icons.Filled.SkipNext
    }
    // clickable + indication null — tranh Surface Material ripple noi bo.
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector,
            contentDescription = icon.name,
            tint = color,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun MarqueeText(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color,
        style = PillPrimary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.widthIn(max = 120.dp).basicMarquee(iterations = 1),
    )
}

@Composable
private fun ElapsedTimeText(
    startTimeMs: Long,
    color: Color,
    modifier: Modifier,
    pausedDurationMs: Long = 0L,
) {
    var elapsedMs by remember(startTimeMs, pausedDurationMs) {
        mutableLongStateOf(
            (System.currentTimeMillis() - startTimeMs - pausedDurationMs).coerceAtLeast(0L)
        )
    }
    LaunchedEffect(startTimeMs, pausedDurationMs) {
        while (true) {
            delay(1000)
            elapsedMs = (System.currentTimeMillis() - startTimeMs - pausedDurationMs)
                .coerceAtLeast(0L)
        }
    }
    Text(formatCountdownLong(elapsedMs), color = color, style = PillMono, modifier = modifier)
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
                            animationSpec = keyframes {
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
                            animationSpec = keyframes {
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
