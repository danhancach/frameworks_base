@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

private val ChipShape = ShapeChip
/**
 * Lockscreen bottom pill SSOT = Now Bar (cung hang quick affordance 48dp,
 * Row CenterVertically trong LockscreenLowerRegion). Dong bo moi event.
 */
private val MusicChipHeight = 58.dp
private val MusicChipMinWidth = 130.dp
private val MusicChipMaxWidth = 180.dp
private val MusicActionSize = MusicChipHeight - 16.dp
private val MusicActionIconSize = MusicChipHeight - 26.dp
private val MusicPlayPauseSize = MusicChipHeight - 24.dp
private val MusicPlayPauseIconSize = MusicChipHeight - 34.dp
private val SportsTeamBadgeSize = 24.dp
/** Chu so chronometer tren keyguard (lon hon CompactChronometer StatusBar). */
private val KeyguardChronometerTextSize = 16.sp

// Stack Samsung Now Bar — hang so va cong thuc SSOT nam o KeyguardPillStackMotion.kt.
/** Easing an noi dung the duoi depth>0 (aod.S9.b: 0.17, 0.17, 0.4, 1.0). */
private val KeyguardStackContentHideEasing = CubicBezierEasing(0.17f, 0.17f, 0.4f, 1.0f)
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

/**
 * Trang thai visual 1 the trong stack doc truc tiep trong graphicsLayer (0 recompose/frame).
 * Dung mutableFloatStateOf de cap nhat dong bo ngay trong AwaitPointerEventScope.
 */
private class KeyguardCardVisualState(
    initialTx: Float = 0f,
    initialTy: Float = 0f,
    initialScale: Float = 1f,
    initialContentAlpha: Float = 1f,
) {
    var tx by mutableFloatStateOf(initialTx)
    var ty by mutableFloatStateOf(initialTy)
    var scale by mutableFloatStateOf(initialScale)
    var contentAlpha by mutableFloatStateOf(initialContentAlpha)
}

// Morph expand Samsung presentation.a (port) — pill → panel.
private val KeyguardPillCorner = MusicChipHeight / 2
private val KeyguardExpandedHeightDefault = 224.dp
/**
 * Khoang cach day man -> canh duoi pill (2520 - 2447 ~= 73px ~= 26.dp).
 * Morph + pill stack CUNG neo BottomCenter + offset nay -- day on dinh, khong day len.
 */
private val KeyguardPillBottomOffset = 26.dp
private val NowBarTransYEasing = CubicBezierEasing(0.22f, 0.25f, 0f, 1f)

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

// morphPillContentAlpha / morphExpandContentAlpha — shared KeyguardMorphGeometry.kt

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

    // Under peek: an ngay khi mo (snap 0); tho ra don dieu tu tu ngay khi bat dau dong (khong bam spring nay).
    val underShouldHide = isKeyguardExpanded || morphTarget || handoffHold
    val underRevealAlpha by animateFloatAsState(
        targetValue = if (underShouldHide) 0f else 1f,
        animationSpec =
            if (underShouldHide) {
                snap()
            } else {
                tween(
                    durationMillis = KeyguardStackUnderRevealDurationMs,
                    easing = NowBarTransYEasing,
                )
            },
        label = "kg_under_reveal",
    )
    LaunchedEffect(morphTarget) {
        if (morphTarget) {
            hadMorphExpand = true
            // Giu endpoint da do khi mo lai giua collapse; chi reset khi settled.
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
                // Giu under gan san khi showHostFill (blurAlpha=0 luc mo -> hideDrawable, khong flash).
                // CAM graphicsLayer.alpha < 1 boc islandBlur (offscreen layer chan Mode.SRC gay nen trang).
                if (targetEvent != null &&
                    chipOnKeyguard &&
                    cycleEvents.size > 1
                ) {
                    val selectedIndex = cycleEvents.indexOfFirst { it.id == targetEvent.id }
                    if (selectedIndex >= 0) {
                        val maxUnderDepth = min(KeyguardStackMaxDepth, cycleEvents.size - 1)
                        val underController = rememberExpandableController(
                            color = Color.Transparent,
                            shape = ChipShape,
                        )
                        // Ve tu sau ra truoc (depth 2 -> depth 1), neo tinh theo pill nghi, khong bam spring rebound.
                        for (depth in maxUnderDepth downTo 1) {
                            val underEvent = cycleEvents[(selectedIndex + depth) % cycleEvents.size]
                            val underPeekPx =
                                with(density) { keyguardStackRestYDp(depth).dp.toPx() }
                            val underRestScale = keyguardStackRestScale(depth)
                            key(underEvent.id) {
                                Box(
                                    modifier = Modifier
                                        .offset {
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
                                            val underTop =
                                                originTop + underPeekPx * underRevealAlpha
                                            IntOffset(
                                                originLeft,
                                                underTop.roundToInt(),
                                            )
                                        }
                                        .requiredSize(
                                            width = with(density) { pillWPx.toDp() },
                                            height = with(density) { pillHPx.toDp() },
                                        )
                                        .zIndex((KeyguardStackMaxDepth - depth).toFloat())
                                        .graphicsLayer {
                                            scaleX = underRestScale
                                            scaleY = underRestScale
                                            transformOrigin = TransformOrigin.Center
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
                                        contentAlpha = { 0f },
                                    )
                                }
                            }
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
                        // Battery-only: dung dung vat ly depth 0 Samsung (press 0.98, Manhattan 16dp,
                        // follow 0.9y/0.1x + nen 0.1 qua yLimit, snap-back 0.78/200).
                        val batteryVisual = remember { KeyguardCardVisualState() }
                        val batteryDragScope = rememberCoroutineScope()
                        var batterySnapJob by remember { mutableStateOf<Job?>(null) }
                        var batteryPressJob by remember { mutableStateOf<Job?>(null) }
                        val batterySlopPx = with(density) { KeyguardStackDragSlopDp.dp.toPx() }
                        val batteryYLimitPx =
                            keyguardStackYLimitPx(
                                pillHeightPx = with(density) { MusicChipHeight.toPx() },
                                dyOffsetPx = with(density) { KeyguardStackDyOffsetDp.dp.toPx() },
                            )
                        DisposableEffect(Unit) {
                            onDispose {
                                batterySnapJob?.cancel()
                                batterySnapJob = null
                                batteryPressJob?.cancel()
                                batteryPressJob = null
                                viewModel.keyguardExpansion.setPillDragging(false)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    clip = false
                                    translationX = batteryVisual.tx
                                    translationY = batteryVisual.ty
                                    scaleX = batteryVisual.scale
                                    scaleY = batteryVisual.scale
                                    transformOrigin = TransformOrigin.Center
                                }
                                .pointerInput(batterySlopPx, batteryYLimitPx) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = true)
                                        viewModel.avoidFalsingGesture()
                                        var dragging = false
                                        val downX = down.position.x
                                        val downY = down.position.y
                                        var dragOriginX = downX
                                        var dragOriginY = downY
                                        var baseX = batteryVisual.tx
                                        var baseY = batteryVisual.ty
                                        var lastPos = down.position
                                        var releasedNormally = false
                                        // Press-down scale 0.98f (aod.S9.e: 150 / 0.57).
                                        batteryPressJob?.cancel()
                                        batteryPressJob = batteryDragScope.launch {
                                            animate(
                                                initialValue = batteryVisual.scale,
                                                targetValue = KeyguardStackPressScale,
                                                animationSpec = spring(
                                                    dampingRatio =
                                                        KeyguardStackPressSpringDamping,
                                                    stiffness =
                                                        KeyguardStackPressSpringStiffness,
                                                ),
                                            ) { value, _ -> batteryVisual.scale = value }
                                        }
                                        try {
                                            while (true) {
                                                val change = awaitPointerEvent().changes
                                                    .firstOrNull { it.id == down.id } ?: break
                                                lastPos = change.position
                                                if (!change.pressed) {
                                                    releasedNormally = true
                                                    break
                                                }
                                                val dxFromDown = change.position.x - downX
                                                val dyFromDown = change.position.y - downY
                                                if (!dragging &&
                                                    keyguardStackExceedsDragSlop(
                                                        dxFromDown,
                                                        dyFromDown,
                                                        batterySlopPx,
                                                    )
                                                ) {
                                                    dragging = true
                                                    batterySnapJob?.cancel()
                                                    batterySnapJob = null
                                                    // Reset goc ngay tai diem vuot 16dp (khong giat).
                                                    dragOriginX = change.position.x
                                                    dragOriginY = change.position.y
                                                    baseX = batteryVisual.tx
                                                    baseY = batteryVisual.ty
                                                    viewModel.keyguardExpansion.setPillDragging(true)
                                                    currentView.parent
                                                        ?.requestDisallowInterceptTouchEvent(true)
                                                }
                                                if (dragging) {
                                                    change.consume()
                                                    val dragDx = change.position.x - dragOriginX
                                                    val dragDy = change.position.y - dragOriginY
                                                    batteryVisual.tx =
                                                        keyguardStackDraggedX(0, baseX, dragDx)
                                                    batteryVisual.ty =
                                                        keyguardStackDraggedY(
                                                            0,
                                                            baseY,
                                                            dragDy,
                                                            batteryYLimitPx,
                                                        )
                                                }
                                            }
                                        } finally {
                                            if (dragging) {
                                                batteryPressJob?.cancel()
                                                batteryPressJob = null
                                                val snapX = batteryVisual.tx
                                                val snapY = batteryVisual.ty
                                                val snapScale = batteryVisual.scale
                                                batterySnapJob?.cancel()
                                                batterySnapJob = batteryDragScope.launch {
                                                    coroutineScope {
                                                        launch {
                                                            animate(
                                                                initialValue = snapX,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackResetSpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackResetSpringStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                batteryVisual.tx = value
                                                            }
                                                        }
                                                        launch {
                                                            animate(
                                                                initialValue = snapY,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackResetSpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackResetSpringStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                batteryVisual.ty = value
                                                            }
                                                        }
                                                        launch {
                                                            animate(
                                                                initialValue = snapScale,
                                                                targetValue = 1f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackResetSpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackResetSpringStiffness,
                                                                ),
                                                            ) { value, _ ->
                                                                batteryVisual.scale = value
                                                            }
                                                        }
                                                    }
                                                    batteryVisual.tx = 0f
                                                    batteryVisual.ty = 0f
                                                    batteryVisual.scale = 1f
                                                    viewModel.keyguardExpansion
                                                        .setPillDragging(false)
                                                    currentView.parent
                                                        ?.requestDisallowInterceptTouchEvent(
                                                            false,
                                                        )
                                                }
                                            } else {
                                                // Nha tay khong keo: phuc hoi scale 1.0f (S9.e).
                                                batteryPressJob?.cancel()
                                                batteryPressJob = batteryDragScope.launch {
                                                    animate(
                                                        initialValue = batteryVisual.scale,
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio =
                                                                KeyguardStackPressSpringDamping,
                                                            stiffness =
                                                                KeyguardStackPressSpringStiffness,
                                                        ),
                                                    ) { value, _ -> batteryVisual.scale = value }
                                                }
                                                val inside =
                                                    lastPos.x in 0f..size.width.toFloat() &&
                                                        lastPos.y in 0f..size.height.toFloat()
                                                if (releasedNormally && inside) {
                                                    viewModel.keyguardExpansion.expand()
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
                                        },
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
 * Stack Samsung Now Bar 1:1 (NowBarCardStackView + aod.S9 + presentation.a):
 * - Toi da 3 tang (1.0 / 0.92 / 0.8), offset (0 / 8 / 16dp), pivot tam (0.5, 0.5).
 * - Press-down scale 0.98f (150 / 0.57); Manhattan 16dp + reset goc tai diem vuot slop.
 * - Keo thong nhat 1..3 the theo depth; commit |vy|>3000 || (-ty>pillH && |dy/dx|>1).
 * - Commit spring Scale 0.6/200 + XY 1.0/250; reset spring 0.78/200.
 * - Content the duoi an ve 0 (150ms S9.b) luc nghi, bat ngay 1 luc cham/keo.
 * - Hoan pinEventById + doi sharedTopGlass toi sau settle (tranh restart pointerInput & nhay blur).
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
     * Collapsed: dat nen movable TRONG lop graphicsLayer cua the dang so huu glass
     * (cung translation/scale). Null khi morph host so huu nen.
     */
    sharedTopGlass: (@Composable BoxScope.() -> Unit)? = null,
    /** Alpha the duoi (0..1) — fade sau khi khung ve pill; morph = 0. */
    underRevealAlpha: Float = 1f,
) {
    val density = LocalDensity.current
    val currentView = LocalView.current
    val haptic = LocalHapticFeedback.current
    val stackScope = rememberCoroutineScope()

    val cardStates = remember { mutableMapOf<String, KeyguardCardVisualState>() }
    var activeOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDragging by remember { mutableStateOf(false) }
    var isSnappingBack by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var isPressScaling by remember { mutableStateOf(false) }
    var isFadingContent by remember { mutableStateOf(false) }
    var pendingCommitTargetId by remember { mutableStateOf<String?>(null) }
    var awaitingPinEventId by remember { mutableStateOf<String?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var pressScaleJob by remember { mutableStateOf<Job?>(null) }
    var contentAlphaJob by remember { mutableStateOf<Job?>(null) }
    var measuredPillHeightPx by remember { mutableFloatStateOf(0f) }

    val fallbackPillHeightPx = with(density) { MusicChipHeight.toPx() }
    val pillHeightPx =
        if (measuredPillHeightPx > 0f) measuredPillHeightPx else fallbackPillHeightPx
    val dyOffsetPx = with(density) { KeyguardStackDyOffsetDp.dp.toPx() }
    val yLimitPx = keyguardStackYLimitPx(pillHeightPx, dyOffsetPx)
    val dragSlopPx = with(density) { KeyguardStackDragSlopDp.dp.toPx() }
    val restYPx = remember(density) {
        FloatArray(KeyguardStackMaxCards) { d ->
            with(density) { keyguardStackRestYDp(d).dp.toPx() }
        }
    }
    val underController = rememberExpandableController(
        color = Color.Transparent,
        shape = ChipShape,
    )

    // Danh sach toi da 3 the tu trang thai live (bat dau tu displayEvent).
    val liveVisibleEvents: List<IslandEvent> = remember(displayEvent, cycleEvents) {
        if (cycleEvents.isEmpty()) {
            listOf(displayEvent)
        } else {
            val idx = cycleEvents.indexOfFirst { it.id == displayEvent.id }
                .let { if (it >= 0) it else 0 }
            val count = min(KeyguardStackMaxCards, cycleEvents.size)
            (0 until count).map { d -> cycleEvents[(idx + d) % cycleEvents.size] }
                .distinctBy { it.id }
        }
    }

    // Ha cho pin ngay khi displayEvent da cap nhat dung id vua commit (khong nhay 1 frame).
    LaunchedEffect(displayEvent.id, cycleEvents) {
        val waiting = awaitingPinEventId
        if (waiting != null &&
            (displayEvent.id == waiting || cycleEvents.none { it.id == waiting })
        ) {
            awaitingPinEventId = null
            if (!isDragging && !isSnappingBack && !reordering) {
                activeOrderIds = emptyList()
            }
        }
    }

    val keepActiveOrder =
        (isDragging || isSnappingBack || reordering || awaitingPinEventId != null) &&
            activeOrderIds.isNotEmpty()
    val visibleEvents: List<IslandEvent> =
        if (keepActiveOrder) {
            val eventById = buildMap {
                put(displayEvent.id, displayEvent)
                for (e in cycleEvents) put(e.id, e)
            }
            val mapped = activeOrderIds.mapNotNull { eventById[it] }
            if (mapped.isNotEmpty()) mapped else liveVisibleEvents
        } else {
            liveVisibleEvents
        }

    fun stateFor(id: String, depth: Int): KeyguardCardVisualState =
        cardStates.getOrPut(id) {
            KeyguardCardVisualState(
                initialTx = 0f,
                initialTy = restYPx.getOrElse(depth) { 0f },
                initialScale = keyguardStackRestScale(depth),
                initialContentAlpha = if (depth == 0) 1f else 0f,
            )
        }

    // Dong bo trang thai nghi & don state thua khi idle.
    val visibleIdsSnapshot = visibleEvents.map { it.id }
    LaunchedEffect(visibleIdsSnapshot, isDragging, isSnappingBack, reordering) {
        if (!isDragging && !isSnappingBack && !reordering) {
            val liveSet = visibleIdsSnapshot.toSet()
            visibleIdsSnapshot.forEachIndexed { depth, id ->
                val st = stateFor(id, depth)
                st.tx = 0f
                st.ty = restYPx.getOrElse(depth) { 0f }
                if (!isPressScaling) {
                    st.scale = keyguardStackRestScale(depth)
                }
                if (!isFadingContent) {
                    st.contentAlpha = if (depth == 0) 1f else 0f
                }
            }
            cardStates.keys.retainAll(liveSet)
        }
    }

    // Khi tat gesture (bat dau expand/an): huy moi animation & tra ve trang thai nghi.
    LaunchedEffect(gesturesEnabled) {
        if (!gesturesEnabled) {
            settleJob?.cancel()
            settleJob = null
            pressScaleJob?.cancel()
            pressScaleJob = null
            contentAlphaJob?.cancel()
            contentAlphaJob = null
            isDragging = false
            isSnappingBack = false
            reordering = false
            isPressScaling = false
            isFadingContent = false
            pendingCommitTargetId = null
            awaitingPinEventId = null
            activeOrderIds = emptyList()
            viewModel.keyguardExpansion.setPillDragging(false)
            liveVisibleEvents.forEachIndexed { depth, ev ->
                val st = stateFor(ev.id, depth)
                st.tx = 0f
                st.ty = restYPx.getOrElse(depth) { 0f }
                st.scale = keyguardStackRestScale(depth)
                st.contentAlpha = if (depth == 0) 1f else 0f
            }
        }
    }

    val currentVisibleEvents by rememberUpdatedState(visibleEvents)
    val currentPillHeightPx by rememberUpdatedState(pillHeightPx)
    val currentYLimitPx by rememberUpdatedState(yLimitPx)
    val currentDragSlopPx by rememberUpdatedState(dragSlopPx)

    // Dispose: cat moi job + ha flag — tranh Job cu ghi de sau leave composition.
    DisposableEffect(viewModel.keyguardExpansion) {
        onDispose {
            settleJob?.cancel()
            settleJob = null
            pressScaleJob?.cancel()
            pressScaleJob = null
            contentAlphaJob?.cancel()
            contentAlphaJob = null
            viewModel.keyguardExpansion.setPillDragging(false)
        }
    }

    // Hien noi dung tat ca the ngay khi cham/keo (NowBarCardStackView.p / showContents).
    fun showAllCardContents(ids: List<String>) {
        contentAlphaJob?.cancel()
        contentAlphaJob = null
        isFadingContent = false
        ids.forEachIndexed { depth, id ->
            stateFor(id, depth).contentAlpha = 1f
        }
    }

    // An noi dung the duoi depth>0 trong 150ms S9.b, giu top=1f (presentation.a.p).
    fun applyDepthContentVisibility(ids: List<String>) {
        contentAlphaJob?.cancel()
        isFadingContent = true
        contentAlphaJob = stackScope.launch {
            coroutineScope {
                ids.forEachIndexed { depth, id ->
                    val st = stateFor(id, depth)
                    if (depth == 0) {
                        st.contentAlpha = 1f
                    } else {
                        launch {
                            animate(
                                initialValue = st.contentAlpha,
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = KeyguardStackContentHideDurationMs,
                                    easing = KeyguardStackContentHideEasing,
                                ),
                            ) { value, _ -> st.contentAlpha = value }
                        }
                    }
                }
            }
            isFadingContent = false
        }
    }

    // Spring scale press-down (0.98f) hoac phuc hoi (1.0f) theo aod.S9.e (150 / 0.57).
    fun animateTopPressScale(topId: String, targetScale: Float) {
        pressScaleJob?.cancel()
        isPressScaling = true
        val st = stateFor(topId, 0)
        pressScaleJob = stackScope.launch {
            animate(
                initialValue = st.scale,
                targetValue = targetScale,
                animationSpec = spring(
                    dampingRatio = KeyguardStackPressSpringDamping,
                    stiffness = KeyguardStackPressSpringStiffness,
                ),
            ) { value, _ -> st.scale = value }
            st.scale = targetScale
            isPressScaling = false
        }
    }

    // Lang nghe o cha voi key on dinh (khong restart pointerInput khi displayEvent doi).
    val stackGestureModifier =
        if (gesturesEnabled) {
            Modifier
                .pointerInput(viewModel) {
                    awaitEachGesture {
                        // Main + requireUnconsumed: nut pill consume truoc; keo stack khi khong phai nut.
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val pointerId = down.id
                        viewModel.avoidFalsingGesture()
                        currentView.parent?.requestDisallowInterceptTouchEvent(true)

                        // Neu dang reorder/snap-back: huy spring, flush pin da commit va giu pose live.
                        val wasReordering = reordering
                        val interruptedMotion = reordering || isSnappingBack
                        if (interruptedMotion) {
                            settleJob?.cancel()
                            settleJob = null
                            val flushId = pendingCommitTargetId
                            pendingCommitTargetId = null
                            if (wasReordering && flushId != null) {
                                awaitingPinEventId = flushId
                                viewModel.pinEventById(flushId)
                            }
                            reordering = false
                            isSnappingBack = false
                        }

                        val startOrder = currentVisibleEvents.map { it.id }
                        if (startOrder.isEmpty()) return@awaitEachGesture
                        activeOrderIds = startOrder

                        // Hien content cac the duoi va ep press scale 0.98f len top (NowBarCardStackView L944, 1043).
                        showAllCardContents(startOrder)
                        if (!wasReordering) {
                            animateTopPressScale(startOrder.first(), KeyguardStackPressScale)
                        }

                        val downX = down.position.x
                        val downY = down.position.y
                        var dragOriginX = downX
                        var dragOriginY = downY
                        var dragStarted = false
                        var dragDx = 0f
                        var dragDy = 0f
                        var lastPos = down.position
                        var basePoses: Map<String, Offset> = emptyMap()
                        var velocityTracker = VelocityTracker()
                        velocityTracker.addPointerInputChange(down)
                        var committedReorder = false
                        var releasedNormally = false

                        // Snap-back resetItemPosition (NowBarCardStackView L847-878): 0.78 / 200 ca XY & Scale.
                        fun startSnapBack(order: List<String>) {
                            if (committedReorder || isSnappingBack) return
                            isDragging = false
                            pressScaleJob?.cancel()
                            pressScaleJob = null
                            isPressScaling = false
                            isSnappingBack = true
                            applyDepthContentVisibility(order)
                            settleJob?.cancel()
                            settleJob = stackScope.launch {
                                coroutineScope {
                                    order.forEachIndexed { depth, id ->
                                        val st = stateFor(id, depth)
                                        val destTy = restYPx.getOrElse(depth) { 0f }
                                        val destScale = keyguardStackRestScale(depth)
                                        launch {
                                            animate(
                                                initialValue = st.tx,
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = KeyguardStackResetSpringDamping,
                                                    stiffness = KeyguardStackResetSpringStiffness,
                                                ),
                                            ) { value, _ -> st.tx = value }
                                        }
                                        launch {
                                            animate(
                                                initialValue = st.ty,
                                                targetValue = destTy,
                                                animationSpec = spring(
                                                    dampingRatio = KeyguardStackResetSpringDamping,
                                                    stiffness = KeyguardStackResetSpringStiffness,
                                                ),
                                            ) { value, _ -> st.ty = value }
                                        }
                                        launch {
                                            animate(
                                                initialValue = st.scale,
                                                targetValue = destScale,
                                                animationSpec = spring(
                                                    dampingRatio = KeyguardStackResetSpringDamping,
                                                    stiffness = KeyguardStackResetSpringStiffness,
                                                ),
                                            ) { value, _ -> st.scale = value }
                                        }
                                    }
                                }
                                order.forEachIndexed { depth, id ->
                                    val st = stateFor(id, depth)
                                    st.tx = 0f
                                    st.ty = restYPx.getOrElse(depth) { 0f }
                                    st.scale = keyguardStackRestScale(depth)
                                }
                                isSnappingBack = false
                                if (awaitingPinEventId == null) {
                                    activeOrderIds = emptyList()
                                }
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
                                lastPos = change.position
                                val liveOrder =
                                    activeOrderIds.ifEmpty { currentVisibleEvents.map { it.id } }
                                val dxFromDown = change.position.x - downX
                                val dyFromDown = change.position.y - downY

                                if (!dragStarted &&
                                    change.pressed &&
                                    keyguardStackExceedsDragSlop(
                                        dxFromDown,
                                        dyFromDown,
                                        currentDragSlopPx,
                                    )
                                ) {
                                    // Bat dau keo (NowBarCardStackView.r): reset goc + velocityTracker, giu pose live.
                                    dragStarted = true
                                    isDragging = true
                                    isSnappingBack = false
                                    settleJob?.cancel()
                                    settleJob = null
                                    viewModel.keyguardExpansion.setPillDragging(true)
                                    dragOriginX = change.position.x
                                    dragOriginY = change.position.y
                                    basePoses = liveOrder.mapIndexed { depth, id ->
                                        val st = stateFor(id, depth)
                                        id to Offset(st.tx, st.ty)
                                    }.toMap()
                                    velocityTracker = VelocityTracker()
                                    velocityTracker.addPointerInputChange(change)
                                    showAllCardContents(liveOrder)
                                }

                                if (dragStarted && change.pressed) {
                                    change.consume()
                                    velocityTracker.addPointerInputChange(change)
                                    dragDx = change.position.x - dragOriginX
                                    dragDy = change.position.y - dragOriginY
                                    val yLimit = currentYLimitPx
                                    liveOrder.forEachIndexed { depth, id ->
                                        val st = stateFor(id, depth)
                                        val base = basePoses[id]
                                            ?: Offset(0f, restYPx.getOrElse(depth) { 0f })
                                        st.tx = keyguardStackDraggedX(depth, base.x, dragDx)
                                        st.ty =
                                            keyguardStackDraggedY(depth, base.y, dragDy, yLimit)
                                    }
                                }

                                if (!change.pressed) {
                                    releasedNormally = true
                                    if (dragStarted) {
                                        change.consume()
                                        isDragging = false
                                        val velocity = velocityTracker.calculateVelocity()
                                        val topId = liveOrder.firstOrNull()
                                        val topTransY =
                                            if (topId != null) stateFor(topId, 0).ty else 0f
                                        val shouldCommit =
                                            keyguardStackShouldCommitReorder(
                                                visibleCardCount = liveOrder.size,
                                                velocityYPxPerSec = velocity.y,
                                                topTranslationYPx = topTransY,
                                                pillHeightPx = currentPillHeightPx,
                                                dragDeltaXPx = dragDx,
                                                dragDeltaYPx = dragDy,
                                            )
                                        if (shouldCommit) {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            pressScaleJob?.cancel()
                                            pressScaleJob = null
                                            isPressScaling = false

                                            val rotatedOrder =
                                                keyguardStackRotateTopToBack(liveOrder)
                                            val newTopId = rotatedOrder.first()
                                            activeOrderIds = rotatedOrder
                                            reordering = true
                                            committedReorder = true
                                            pendingCommitTargetId = newTopId
                                            applyDepthContentVisibility(rotatedOrder)

                                            // Commit reorder (NowBarCardStackView L1274-1277):
                                            // Scale 0.6/200, XY 1.0/250.
                                            settleJob?.cancel()
                                            settleJob = stackScope.launch {
                                                coroutineScope {
                                                    rotatedOrder.forEachIndexed { newDepth, id ->
                                                        val st = stateFor(id, newDepth)
                                                        val destTy =
                                                            restYPx.getOrElse(newDepth) { 0f }
                                                        val destScale =
                                                            keyguardStackRestScale(newDepth)
                                                        launch {
                                                            animate(
                                                                initialValue = st.scale,
                                                                targetValue = destScale,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackCommitScaleSpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackCommitScaleSpringStiffness,
                                                                ),
                                                            ) { value, _ -> st.scale = value }
                                                        }
                                                        launch {
                                                            animate(
                                                                initialValue = st.tx,
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackCommitXySpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackCommitXySpringStiffness,
                                                                ),
                                                            ) { value, _ -> st.tx = value }
                                                        }
                                                        launch {
                                                            animate(
                                                                initialValue = st.ty,
                                                                targetValue = destTy,
                                                                animationSpec = spring(
                                                                    dampingRatio =
                                                                        KeyguardStackCommitXySpringDamping,
                                                                    stiffness =
                                                                        KeyguardStackCommitXySpringStiffness,
                                                                ),
                                                            ) { value, _ -> st.ty = value }
                                                        }
                                                    }
                                                }
                                                rotatedOrder.forEachIndexed { newDepth, id ->
                                                    val st = stateFor(id, newDepth)
                                                    st.tx = 0f
                                                    st.ty = restYPx.getOrElse(newDepth) { 0f }
                                                    st.scale = keyguardStackRestScale(newDepth)
                                                }
                                                val commitId = pendingCommitTargetId
                                                pendingCommitTargetId = null
                                                reordering = false
                                                if (commitId != null) {
                                                    awaitingPinEventId = commitId
                                                    viewModel.pinEventById(commitId)
                                                } else {
                                                    activeOrderIds = emptyList()
                                                }
                                                viewModel.keyguardExpansion
                                                    .setPillDragging(false)
                                                currentView.parent
                                                    ?.requestDisallowInterceptTouchEvent(false)
                                            }
                                        } else {
                                            startSnapBack(liveOrder)
                                        }
                                    } else {
                                        // Tap (<16dp Manhattan): phuc hoi scale 1.0f + an content the duoi.
                                        val inside =
                                            lastPos.x in 0f..size.width.toFloat() &&
                                                lastPos.y in 0f..size.height.toFloat()
                                        if (inside) {
                                            val topId = liveOrder.firstOrNull()
                                            if (topId != null) {
                                                animateTopPressScale(topId, 1f)
                                            }
                                            applyDepthContentVisibility(liveOrder)
                                            change.consume()
                                            val tapEvent =
                                                currentVisibleEvents.firstOrNull() ?: displayEvent
                                            viewModel.keyguardExpansion.toggle(
                                                expandOnlyEventIdFor(tapEvent),
                                            )
                                        } else if (interruptedMotion) {
                                            // Ngat settle nhung tha tay ngoai bien: snap ve nghi.
                                            startSnapBack(liveOrder)
                                        } else {
                                            val topId = liveOrder.firstOrNull()
                                            if (topId != null) {
                                                animateTopPressScale(topId, 1f)
                                            }
                                            applyDepthContentVisibility(liveOrder)
                                        }
                                    }
                                    break
                                }
                            }
                        } finally {
                            val liveOrder =
                                activeOrderIds.ifEmpty { currentVisibleEvents.map { it.id } }
                            if ((dragStarted && isDragging) || (!releasedNormally && interruptedMotion)) {
                                startSnapBack(liveOrder)
                            } else if (!releasedNormally && !committedReorder && !isSnappingBack && !reordering) {
                                // Cancel truoc khi bat dau keo: tra scale + contentAlpha ve nghi.
                                val topId = liveOrder.firstOrNull()
                                if (topId != null) {
                                    animateTopPressScale(topId, 1f)
                                }
                                applyDepthContentVisibility(liveOrder)
                                if (awaitingPinEventId == null) {
                                    activeOrderIds = emptyList()
                                }
                                viewModel.keyguardExpansion.setPillDragging(false)
                            } else if (!isSnappingBack && !reordering) {
                                if (awaitingPinEventId == null) {
                                    activeOrderIds = emptyList()
                                }
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

    // Giu sharedTopGlass gan theo displayEvent.id — chi chuyen khi settle xong (khong nhay blur giua bay).
    val glassOwnerEventId = displayEvent.id
    val visibleCount = visibleEvents.size

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
        if (visibleEvents.isNotEmpty()) {
            for (depth in (visibleCount - 1) downTo 0) {
                val event = visibleEvents[depth]
                val cardState = stateFor(event.id, depth)
                val isSharedGlassOwner = event.id == glassOwnerEventId
                // Giu subtree theo event.id — doi depth/tx/ty/scale trong graphicsLayer, khong recompose.
                key(event.id) {
                    val controller =
                        if (depth == 0) expandableController else underController
                    Box(
                        modifier = Modifier
                            .zIndex(keyguardStackZIndex(depth, visibleCount))
                            .then(
                                if (depth == 0) {
                                    Modifier.onSizeChanged { sz ->
                                        if (sz.height > 0) {
                                            measuredPillHeightPx = sz.height.toFloat()
                                        }
                                    }
                                } else Modifier
                            )
                            .graphicsLayer {
                                clip = false
                                translationX = cardState.tx
                                translationY = cardState.ty
                                scaleX = cardState.scale
                                scaleY = cardState.scale
                                transformOrigin = TransformOrigin.Center
                            },
                    ) {
                        if (sharedTopGlass != null && isSharedGlassOwner) {
                            sharedTopGlass()
                        }
                        val layerReveal =
                            if (depth > 0) underRevealAlpha.coerceIn(0f, 1f) else 1f
                        CompositionLocalProvider(
                            LocalKeyguardYieldGlassToMorph provides
                                ((sharedTopGlass != null && isSharedGlassOwner) ||
                                    (yieldGlassOnlyTop && depth == 0)),
                        ) {
                            KeyguardPillEventChip(
                                event = event,
                                viewModel = viewModel,
                                expandableController = controller,
                                batteryString = batteryString,
                                blurAlpha = chipBlurAlpha * layerReveal,
                                rowClickEnabled = false,
                                actionsEnabled = depth == 0 && gesturesEnabled && !reordering,
                                contentAlpha = { cardState.contentAlpha * layerReveal },
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
    contentAlpha: () -> Float = { 1f },
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
            contentAlpha = contentAlpha,
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
    contentAlpha: () -> Float = { 1f },
) {
    val context = LocalContext.current
    val motionScheme = MaterialTheme.motionScheme
    val isChronometer =
        event is IslandEvent.Timer ||
            event is IslandEvent.Stopwatch ||
            (event is IslandEvent.AudioRecording && event.state != RecordingState.SAVED) ||
            (event is IslandEvent.Call && event.connected && event.callStartTimeMs > 0L)
    val washColor = accent.copy(alpha = AlphaIconBg)

    // Dong bo toan bo pill Now Bar Samsung (58dp x [130..180]dp) — khop morph host, khong animateContentSize.
    GlassChip(height = MusicChipHeight, blurAlpha = blurAlpha) {
        Row(
            modifier = Modifier
                .height(MusicChipHeight)
                .widthIn(min = MusicChipMinWidth, max = MusicChipMaxWidth)
                .clip(ChipShape)
                .graphicsLayer { alpha = contentAlpha().coerceIn(0f, 1f) }
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
                                    viewModel.keyguardExpansion.toggle(expandOnlyEventIdFor(event))
                                }
                            }
                        }
                    } else Modifier
                )
                .padding(horizontal = SpaceMd),
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
                // Dong bo khung Now Bar cho Clock / AudioRecording / Call co dong ho.
                KeyguardChronometerLeadingIcon(event, accent, washColor)
                Spacer(Modifier.width(SpaceXs))
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f, fill = false).widthIn(max = 110.dp).fillMaxWidth(),
                ) {
                    KeyguardChronometerReadout(event, contentColor)
                    val sub = secondaryTextFor(event)
                    if (sub != null && event !is IslandEvent.Timer && event !is IslandEvent.Stopwatch) {
                        NativeMarqueeText(
                            text = sub,
                            color = contentColor.copy(alpha = AlphaSecondary),
                            textSize = 11.sp,
                            fontWeight = 400,
                            fadeLength = SpaceMd,
                            gravity = android.view.Gravity.CENTER_HORIZONTAL or
                                android.view.Gravity.CENTER_VERTICAL,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SportsChipTeamBadge(event.team1Name, event.team1Icon, contentColor)
                    Spacer(Modifier.width(SpaceSm))
                    Text(
                        if (event.score1.isNotEmpty()) "${event.score1} - ${event.score2}"
                        else stringResource(R.string.ax_dynamic_bar_sports_vs),
                        style = PillPrimary.copy(fontSize = 15.sp),
                        color = contentColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(SpaceSm))
                    SportsChipTeamBadge(event.team2Name, event.team2Icon, contentColor)
                }
            } else if (event is IslandEvent.NowPlaying) {
                AnimatedContent(
                    targetState = event,
                    transitionSpec = {
                        mediaTrackSwitchTransition(motionScheme).using(SizeTransform(clip = false))
                    },
                    contentKey = { "${it.key}|${it.songTitle}|${it.artist}" },
                    label = "kg_now_playing",
                    modifier = Modifier.weight(1f),
                ) { np ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val art = np.albumArt
                        if (art != null) {
                            Image(
                                bitmap = art.toSquareScaledBitmap(MusicActionSize),
                                contentDescription = null,
                                modifier = Modifier.size(MusicActionSize).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            KeyguardEventLeadingBadge(np, accent, washColor)
                        }
                        Spacer(Modifier.width(SpaceXs))
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f),
                        ) {
                            NativeMarqueeText(
                                text = np.songTitle.ifEmpty {
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
                            if (np.artist.isNotBlank()) {
                                NativeMarqueeText(
                                    text = np.artist,
                                    color = contentColor.copy(alpha = AlphaSecondary),
                                    textSize = 11.5.sp,
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
            } else {
                // Now Bar thong nhat: badge trai + 2 dong NativeMarqueeText can giua + action lon 34dp (khong dual AC/bounce).
                KeyguardEventLeadingBadge(event, accent, washColor)
                Spacer(Modifier.width(SpaceXs))
                KeyguardNowBarTextColumn(
                    event = event,
                    contentColor = contentColor,
                    batteryString = batteryString,
                    modifier = Modifier.weight(1f),
                )
                val actions = actionsFor(event, context)
                if (actions.isNotEmpty()) {
                    Spacer(Modifier.width(SpaceXs))
                    actions.forEach { action ->
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
            }
        }
    }
}

@Composable
private fun KeyguardEventLeadingBadge(
    event: IslandEvent,
    accent: Color,
    washColor: Color,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(washColor),
        contentAlignment = Alignment.Center,
    ) {
        PillEventIcon(event, tint = accent, animated = false)
    }
}

@Composable
private fun KeyguardChronometerLeadingIcon(
    event: IslandEvent,
    accent: Color,
    washColor: Color,
) {
    when (event) {
        is IslandEvent.Call -> {
            val photo = event.callerPhoto
            if (photo != null) {
                Image(
                    bitmap = photo.toSquareScaledBitmap(MusicActionSize),
                    contentDescription = null,
                    modifier = Modifier.size(MusicActionSize).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                KeyguardEventLeadingBadge(event, accent, washColor)
            }
        }
        is IslandEvent.AudioRecording -> KeyguardEventLeadingBadge(event, accent, washColor)
        else -> {
            val chronoSmallIcon =
                when (event) {
                    is IslandEvent.Timer -> event.appIcon
                    is IslandEvent.Stopwatch -> event.appIcon
                    else -> null
                }
            val chronoStyle = eventStyleFor(event)
            Box(
                modifier = Modifier.size(MusicActionSize),
                contentAlignment = Alignment.Center,
            ) {
                if (chronoSmallIcon != null) {
                    SmallIconImage(
                        drawable = chronoSmallIcon,
                        size = MusicActionIconSize,
                        tint = accent,
                    )
                } else {
                    chronoStyle.icon?.let { vector ->
                        Icon(
                            vector,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(MusicActionIconSize),
                        )
                    } ?: PillEventIcon(event, tint = accent, animated = false)
                }
            }
        }
    }
}

@Composable
private fun KeyguardNowBarTextColumn(
    event: IslandEvent,
    contentColor: Color,
    batteryString: String,
    modifier: Modifier = Modifier,
) {
    val parts = rememberChargingParts(batteryString)
    val primaryText =
        if (event is IslandEvent.Charging && parts.size >= 2) {
            parts[0]
        } else {
            keyguardPrimaryLabelFor(event, batteryString)
        }
    val secondaryText =
        if (event is IslandEvent.Charging && parts.size >= 2) {
            parts[1]
        } else {
            secondaryTextFor(event)
        }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (event is IslandEvent.AospChip && primaryText == null) {
            PillEventText(event, Modifier.weight(1f, fill = false), overrideColor = contentColor)
        } else {
            NativeMarqueeText(
                text = primaryText.orEmpty(),
                color = contentColor,
                textSize = 13.sp,
                fontWeight = PillPrimary.fontWeight?.weight ?: 500,
                fadeLength = SpaceMd,
                gravity = android.view.Gravity.CENTER_HORIZONTAL or
                    android.view.Gravity.CENTER_VERTICAL,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!secondaryText.isNullOrBlank()) {
            NativeMarqueeText(
                text = secondaryText,
                color = contentColor.copy(alpha = AlphaSecondary),
                textSize = 11.5.sp,
                fontWeight = 400,
                fadeLength = SpaceMd,
                gravity = android.view.Gravity.CENTER_HORIZONTAL or
                    android.view.Gravity.CENTER_VERTICAL,
                modifier = Modifier.fillMaxWidth(),
            )
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
                NativeMarqueeText(
                    text = event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_timer) },
                    color = color,
                    textSize = 13.sp,
                    fontWeight = PillPrimary.fontWeight?.weight ?: 500,
                    fadeLength = SpaceMd,
                    gravity = android.view.Gravity.CENTER_HORIZONTAL or
                        android.view.Gravity.CENTER_VERTICAL,
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
        is IslandEvent.AudioRecording -> {
            val elapsedMs =
                rememberLiveOrFrozenChronometerMs(
                    identityKey = listOf(event.startTimeMs, event.state, event.pausedDurationMs),
                    ticking = event.state == RecordingState.RECORDING,
                    compute = { audioRecordingElapsedMs(event) },
                )
            Text(
                formatStopwatchCompact(elapsedMs),
                style =
                    PillMono.copy(
                        fontSize = KeyguardChronometerTextSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                color =
                    if (event.state == RecordingState.RECORDING) color
                    else color.copy(alpha = AlphaSecondary),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is IslandEvent.Call -> {
            val elapsedMs =
                rememberLiveOrFrozenChronometerMs(
                    identityKey = listOf(event.id, event.callStartTimeMs, event.connected),
                    ticking = event.connected && event.callStartTimeMs > 0L,
                    compute = { callElapsedMs(event.callStartTimeMs) },
                )
            Text(
                formatStopwatchCompact(elapsedMs),
                style =
                    PillMono.copy(
                        fontSize = KeyguardChronometerTextSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                color = color,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> Unit
    }
}

@Composable
private fun keyguardPrimaryLabelFor(
    event: IslandEvent,
    batteryString: String = "",
): String? = when (event) {
    is IslandEvent.AudioRecording -> when (event.state) {
        RecordingState.RECORDING -> stringResource(R.string.ax_dynamic_bar_recording)
        RecordingState.PAUSED -> stringResource(R.string.ax_dynamic_bar_paused)
        RecordingState.SAVED -> stringResource(R.string.ax_dynamic_bar_saved)
    }
    is IslandEvent.Media -> event.track.ifEmpty { stringResource(R.string.ax_dynamic_bar_music) }
    is IslandEvent.Timer,
    is IslandEvent.Stopwatch -> null
    is IslandEvent.Notification -> event.title ?: event.appName
    is IslandEvent.Charging -> {
        val parts = rememberChargingParts(batteryString)
        if (parts.isNotEmpty()) parts[0] else "${event.level}%"
    }
    is IslandEvent.Bluetooth -> event.deviceName.take(12)
    is IslandEvent.Hotspot -> {
        val hotspotLabel = stringResource(R.string.ax_dynamic_bar_hotspot)
        if (event.numDevices > 0) "$hotspotLabel · ${event.numDevices}" else hotspotLabel
    }
    is IslandEvent.Alarm -> event.label.ifEmpty { stringResource(R.string.ax_dynamic_bar_alarm) }
    is IslandEvent.Call -> {
        if (event.callType == "Phone:incoming") stringResource(R.string.ax_dynamic_bar_incoming_call)
        else stringResource(R.string.ax_dynamic_bar_call)
    }
    is IslandEvent.Torch ->
        if (event.supportsLevel) "${(event.level.toFloat() / event.maxLevel * 100).toInt()}%"
        else stringResource(R.string.ax_dynamic_bar_flashlight)
    is IslandEvent.RingerMode -> event.label
    is IslandEvent.Vpn -> stringResource(R.string.ax_dynamic_bar_vpn_active)
    is IslandEvent.Clipboard -> event.preview.ifEmpty { stringResource(R.string.ax_dynamic_bar_copied) }
    is IslandEvent.BiometricUnlock -> stringResource(R.string.ax_dynamic_bar_unlocked)
    is IslandEvent.AppSwitch -> stringResource(R.string.ax_dynamic_bar_recents)
    is IslandEvent.PromotedOngoing ->
        event.shortText.ifEmpty { event.title.ifEmpty { event.appName } }
    is IslandEvent.Sports -> "${event.score1}-${event.score2}"
    is IslandEvent.NowPlaying ->
        "${event.songTitle} · ${event.artist}".trimEnd(' ', '·', ' ')
    is IslandEvent.KeyguardIndication -> event.text
    is IslandEvent.AospChip -> null
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
            bitmap = icon.toScaledBitmap(SportsTeamBadgeSize),
            contentDescription = name,
            modifier = Modifier.size(SportsTeamBadgeSize).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier.size(SportsTeamBadgeSize).clip(CircleShape)
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
    size: Dp = MusicPlayPauseSize,
    iconSize: Dp = MusicPlayPauseIconSize,
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
