@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.shared

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.min
import com.android.axion.blur.AxBlurSurfaceDefaults
import com.android.axion.blur.AxBlurSurfacePaintKind
import com.android.axion.blur.axBlurBackground
import com.android.systemui.blur.AxSystemFrost
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.res.R

internal val SpaceXxs = 2.dp
internal val SpaceXs = 4.dp
internal val SpaceSm = 6.dp
internal val SpaceMd = 8.dp
internal val SpaceLg = 12.dp
internal val SpaceXl = 14.dp
internal val SpaceXxl = 16.dp
internal val SpaceSection = 20.dp
internal val SpacePanel = 24.dp
internal val SpacePanelLarge = 28.dp

internal val RadiusXs = 8.dp
internal val RadiusSm = 12.dp
internal val RadiusLg = 24.dp
internal val RadiusXl = 32.dp
internal val RadiusCard = 28.dp

internal val ShapeXs = RoundedCornerShape(RadiusXs)
internal val ShapeSm = RoundedCornerShape(RadiusSm)
internal val ShapeLg = RoundedCornerShape(RadiusLg)
internal val ShapeXl = RoundedCornerShape(RadiusXl)
internal val ShapeCard = RoundedCornerShape(RadiusCard)

internal val ShapeIconLarge = RoundedCornerShape(20.dp)
internal val ShapeIconMedium = RoundedCornerShape(16.dp)
internal val ShapeAlbum = RoundedCornerShape(16.dp)
internal val ShapeChip = RoundedCornerShape(percent = 50)
internal val ShapeCompact = RoundedCornerShape(12.dp)

internal val SizeBadge = 14.dp
internal val SizeIconSm = 20.dp
internal val SizeIconMd = 28.dp
internal val SizeButton = 48.dp
internal val SizeButtonLg = 48.dp
internal val SizeButtonXl = 64.dp
internal val SizeAlbumSm = 52.dp
internal val SizeAlbumLg = 200.dp
internal val SizeSeekHeight = 16.dp
internal val SizeProgressHeight = 8.dp
internal val SizeStrokeWidth = 2.dp
internal val SizeStrokeThin = 1.5f
internal val SizeCompactIcon = 40.dp
internal val SizeActionHeight = 48.dp

/** The leading icon slot in an expanded tile: a small rounded badge, not a column of its own. */
internal val SizeIconBadge = 28.dp

/** A compact tile action — circular and icon-only, small enough to sit in the header row. */
internal val SizeActionIcon = 36.dp

internal val ExpandedMaxWidth = 420.dp

internal const val AlphaSecondary = 0.7f
internal const val AlphaTertiary = 0.5f
internal const val AlphaHint = 0.4f
internal const val AlphaDisabled = 0.3f
internal const val AlphaSubtle = 0.15f
internal const val AlphaFaint = 0.1f
internal const val AlphaBorder = 0.08f
internal const val AlphaStatusChip = 0.14f
internal const val AlphaIconBg = 0.16f
internal const val AlphaTrack = 0.25f

/**
 * A wash strong enough to read as a filled control without going solid — the primary of two
 * accent washes that sit side by side (play/pause next to skip).
 */
internal const val AlphaAccent = 0.30f

/**
 * Opacity of a chip's coloured frost (the tint passed to [islandBlur]) and of solid tiles inside
 * a glass card: enough to carry colour and keep a status-bar label legible, thin enough to frost.
 */
internal const val AlphaGlass = 0.72f

/**
 * Opacity of every [islandBlur] surface below this point in the tree.
 *
 * The cross-window blur is drawn by a platform drawable, which a Compose `graphicsLayer { alpha }`
 * or an `AnimatedVisibility` fade above it does not reach. Containers that fade (the expanded
 * panel, the alert card, a card being swiped away) publish their alpha here instead; nested
 * providers multiply into the parent value rather than replace it.
 */
internal val LocalBlurAlpha = compositionLocalOf { 1f }

/**
 * STATUS BAR morph: shared glass owner present (morph host).
 * Tach LocalKeyguardYieldGlassToMorph — khong dam keyguard/AOD.
 * islandCardSurface: yield=true → TOP selected FG-only; under giu blur.
 * Task-041: expanded stack yield=false (moi card giu islandBlur local);
 * chi pill↔expanded morph moi yield shared shell.
 */
internal val LocalStatusBarYieldGlassToMorph = staticCompositionLocalOf { false }

/**
 * Open transfer (task-043/045): top card dang await destination surface.
 * true → AxBlurSurface destination listener (native HOAC fallback ve that);
 * false sau Complete. KHONG content-only / alpha0; KHONG fake native khi fallback.
 */
internal val LocalStatusBarOpenGlassAwaitDestinationDraw = staticCompositionLocalOf { false }

/** Session gen active cho destination draw report (0 = khong await). */
internal val LocalStatusBarOpenGlassDestinationSessionGen = staticCompositionLocalOf { 0 }

/**
 * Callback sau surface local ve that (NativeBackdrop | FallbackSurfaceRendered, α>0).
 * layout W/H = local size (khong window transformed). KHONG GPU/SF ACK.
 */
internal data class StatusBarOpenGlassDestinationDrawReport(
    val sessionGen: Int,
    val ownerKey: String,
    val contextToken: Int,
    val layoutWidthPx: Float = 0f,
    val layoutHeightPx: Float = 0f,
    val nativeBackdropDrawn: Boolean = false,
    val blurSurfaceAlpha: Float = 0f,
    val paintKind: StackOpenGlassDestinationPaintKind =
        if (nativeBackdropDrawn) {
            StackOpenGlassDestinationPaintKind.NativeBackdrop
        } else {
            StackOpenGlassDestinationPaintKind.NoSurface
        },
    val fallbackSurfaceRendered: Boolean = false,
)

internal val LocalStatusBarOpenGlassDestinationDrawReporter =
    staticCompositionLocalOf<((StatusBarOpenGlassDestinationDrawReport) -> Unit)?> { null }

/**
 * true = the selected top (isTop && !isLeaving) — chi the nay report painted bounds.
 * Under-card KHONG overwrite morph target.
 */
internal val LocalStatusBarPaintedCardIsTop = staticCompositionLocalOf { false }

/**
 * event:id owner cua layer dang report painted — reject stale sai owner (task-037).
 * Rong = legacy (host tu bind contentOwnerKey).
 */
internal val LocalStatusBarPaintedCardOwnerKey = staticCompositionLocalOf { "" }

/**
 * Painted top-card bounds (layout W/H + window LRTB) — freeze morph target.
 * null = khong do (fallback side-pad).
 * [ownerKey]/[contextToken] atomic voi LRTB (task-037).
 */
internal data class StatusBarPaintedCardReportPx(
    val layoutWPx: Int,
    val layoutHPx: Int,
    val windowLeftPx: Float,
    val windowTopPx: Float,
    val windowRightPx: Float,
    val windowBottomPx: Float,
    val ownerKey: String = "",
    val contextToken: Int = 0,
) {
    val layoutWidth: Int
        get() = layoutWPx.coerceAtLeast(0)
    val layoutHeight: Int
        get() = layoutHPx.coerceAtLeast(0)
    val windowWidthPx: Float
        get() = (windowRightPx - windowLeftPx).coerceAtLeast(0f)
    val windowHeightPx: Float
        get() = (windowBottomPx - windowTopPx).coerceAtLeast(0f)
}

internal val LocalStatusBarPaintedCardBoundsReporter =
    staticCompositionLocalOf<((StatusBarPaintedCardReportPx) -> Unit)?> { null }

/**
 * SSOT dragY stack (Expanded at-rest) — StackedExpandedPanels bao cao len morph host.
 * Shared glass dich cung frame; KHONG translate pointerInput ancestor.
 */
internal val LocalStatusBarStackDragYReporter =
    staticCompositionLocalOf<((dragYPx: Float) -> Unit)?> { null }

/** Bao cao settle/commit anim — morph host freeze painted LRTB. */
internal val LocalStatusBarStackDragSettlingReporter =
    staticCompositionLocalOf<((settling: Boolean) -> Unit)?> { null }

/**
 * Bao cao [StackExpandedDragPhase] — glass ownership theo phase (khong |dragY| eps).
 *
 * Ownership SSOT: StackedExpandedPanels publish DONG BO tai gesture lifecycle
 * (onStackDragStart Held / release Cancel|CommitSettle / settle-end Idle) cung
 * frame voi dragY — KHONG lay SideEffect lam sole source.
 * Morph host: yield/plate alpha = composition doc state; graphicsLayer phase =
 * draw-time. Khong claim sync chi vi ten Live; khong doi phase theo |dragY|.
 */
internal val LocalStatusBarStackDragPhaseReporter =
    staticCompositionLocalOf<((phase: StackExpandedDragPhase) -> Unit)?> { null }

/**
 * Measure-context token (envelopeW + densityDpi + fontScale) — producer bind
 * trong onPlaced sau layout current (task-033). 0 = chua co constraints.
 */
internal val LocalStatusBarCardNaturalMeasureContextToken =
    staticCompositionLocalOf { 0 }

/**
 * Bao cao natural W/H + contextToken theo event.id (layer onPlaced full —
 * underPeekClip chi draw). Morph host cache theo identity+context; lock TOP
 * dung natural the nay, KHONG max stack envelope.
 * (task-031/033: tach card natural vs host envelope vs painted vs touch).
 */
internal val LocalStatusBarStackCardNaturalSizeReporter =
    staticCompositionLocalOf<
        ((eventId: String, widthPx: Int, heightPx: Int, contextToken: Int) -> Unit)?
    > { null }

/**
 * Legacy size-only reporter — map sang bounds (LRTB=0) neu call-site cu.
 * UU TIEN [LocalStatusBarPaintedCardBoundsReporter].
 */
@Deprecated("Dung LocalStatusBarPaintedCardBoundsReporter (full LRTB)")
internal val LocalStatusBarPaintedCardSizeReporter =
    staticCompositionLocalOf<((widthPx: Int, heightPx: Int) -> Unit)?> { null }

/** Rim width for the small glass pills; the 1 dp card rim would read as an outline at chip size. */
internal val ChipHighlightWidth = 0.5.dp

/**
 * Frosted glass for one island surface, drawn behind the content of the node it's applied to.
 * Apply it after the surface's `clip` and before padding, so it covers the full surface.
 *
 * [cornerRadius] must match the shape the surface clips to. [fallbackColor] is painted instead
 * wherever the platform won't blur (blur disabled, battery saver, unsupported device). [tint] is
 * the frost laid over the blur: null takes the system frost; chips pass their own colour so the
 * frost *is* the chip's colour instead of a second fill stacked on top of it. The rim highlight
 * replaces a separate border.
 */
@Composable
internal fun Modifier.islandBlur(
    cornerRadius: Dp,
    fallbackColor: Color,
    tint: Color? = null,
    alpha: Float = LocalBlurAlpha.current,
    highlightWidth: Dp = AxBlurSurfaceDefaults.HighlightWidth,
    includeViewBackdrop: Boolean = true,
    /** Ban kinh Y elliptical; mac dinh = [cornerRadius] (scalar). */
    cornerRadiusY: Dp = cornerRadius,
    /** Open-glass legacy: fire sau native backdrop (khong alpha_zero). */
    onNativeBackdropDrawn: ((widthPx: Int, heightPx: Int, surfaceAlpha: Float) -> Unit)? =
        null,
    /**
     * Open-glass production: native HOAC fallback ve that (α>0).
     * Mac dinh null — surface thuong khong callback moi frame.
     */
    onDestinationSurfaceDrawn:
        ((
            widthPx: Int,
            heightPx: Int,
            surfaceAlpha: Float,
            kind: AxBlurSurfacePaintKind,
        ) -> Unit)? =
            null,
): Modifier {
    // Tint mac dinh = AxSystemFrost (cung PrimaryCard / affordance / notif).
    val resolvedTint = tint ?: Color(AxSystemFrost.tintColor(LocalContext.current))
    return axBlurBackground(
        enabled = true,
        fallbackColor = fallbackColor,
        tintColor = resolvedTint,
        cornerRadius = cornerRadius,
        cornerRadiusY = cornerRadiusY,
        alpha = alpha,
        highlight = AxBlurSurfaceDefaults.highlightBrush(),
        highlightWidth = highlightWidth,
        includeViewBackdrop = includeViewBackdrop,
        onNativeBackdropDrawn = onNativeBackdropDrawn,
        onDestinationSurfaceDrawn = onDestinationSurfaceDrawn,
    )
}

/**
 * true = Dynamic Bar tren lockscreen: tat ripple / indication Compose.
 * Chi provider tu AxDynamicBarKeyguardChip — khong anh huong StatusBar island.
 */
internal val LocalKeyguardSuppressRipple = staticCompositionLocalOf { false }

/** Indication rong cho Surface/LocalIndication tren lockscreen (khong ve pressed). */
internal object KeyguardNoRippleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node() {}

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean = other === this
}

/** Ripple clickable; null khi [LocalKeyguardSuppressRipple]. */
@Composable
internal fun islandClickIndication(color: Color? = null): Indication? {
    if (LocalKeyguardSuppressRipple.current) return null
    return if (color != null) ripple(color = color) else ripple()
}

/**
 * Be mat the expand: clip ShapeCard + blur (tru morph host da co glass) + ripple click tuy chon.
 * Morph + khong onClick: khong clip them (host morph da bo goc).
 * Dung chung Media / Chronometer / PromotedOngoing / PrimaryCard — tranh clip+clickable lap.
 */
@Composable
internal fun Modifier.islandCardSurface(
    morphHostFrame: Boolean,
    onClick: (() -> Unit)? = null,
): Modifier {
    // Yield shared (morph): TOP FG-only. Expanded stack yield=false → moi card
    // giu clip+islandBlur theo key(event.id) (archive stable-tree).
    // OpenWaiting (045): yield=false + local blur α>0 (native|fallback) truoc drop shared.
    val yieldSbGlass = LocalStatusBarYieldGlassToMorph.current
    val reportPaintedTop = LocalStatusBarPaintedCardIsTop.current
    val paintedOwnerKey = LocalStatusBarPaintedCardOwnerKey.current
    val paintedContextToken = LocalStatusBarCardNaturalMeasureContextToken.current
    val awaitOpenDestDraw = LocalStatusBarOpenGlassAwaitDestinationDraw.current
    val openDestSessionGen = LocalStatusBarOpenGlassDestinationSessionGen.current
    val openDestDrawReporter = LocalStatusBarOpenGlassDestinationDrawReporter.current
    val hostOwnsGlass = morphHostFrame || (yieldSbGlass && reportPaintedTop)
    // Surface ACK: native backdrop HOAC fallback ve that — khong content-only / alpha0.
    // Chi top dang await; listener null o surface thuong → khong callback moi frame.
    val reportOpenDestSurface =
        awaitOpenDestDraw &&
            reportPaintedTop &&
            !hostOwnsGlass &&
            openDestSessionGen > 0 &&
            openDestDrawReporter != null
    // Listener moi attach (Waiting sessionGen>0) — invalidate de redraw blur ACK
    // neu surface da ve frame truoc khi reporter ton tai.
    val openGlassHostView = LocalView.current
    val wasAwaitOpenDest = remember { mutableStateOf(false) }
    SideEffect {
        if (reportOpenDestSurface && !wasAwaitOpenDest.value) {
            openGlassHostView.invalidate()
        }
        wasAwaitOpenDest.value = reportOpenDestSurface
    }
    val destinationSurfaceListener:
        ((
            widthPx: Int,
            heightPx: Int,
            surfaceAlpha: Float,
            kind: AxBlurSurfacePaintKind,
        ) -> Unit)? =
        if (reportOpenDestSurface) {
            val reporter = openDestDrawReporter
            val sessionGen = openDestSessionGen
            val ownerKey = paintedOwnerKey
            val ctxToken = paintedContextToken
            { widthPx, heightPx, surfaceAlpha, kind ->
                val paintKind =
                    when (kind) {
                        AxBlurSurfacePaintKind.NativeBackdrop ->
                            StackOpenGlassDestinationPaintKind.NativeBackdrop
                        AxBlurSurfacePaintKind.FallbackSurfaceRendered ->
                            StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered
                    }
                reporter?.invoke(
                    StatusBarOpenGlassDestinationDrawReport(
                        sessionGen = sessionGen,
                        ownerKey = ownerKey,
                        contextToken = ctxToken,
                        layoutWidthPx = widthPx.toFloat(),
                        layoutHeightPx = heightPx.toFloat(),
                        nativeBackdropDrawn =
                            paintKind == StackOpenGlassDestinationPaintKind.NativeBackdrop,
                        blurSurfaceAlpha = surfaceAlpha,
                        paintKind = paintKind,
                        fallbackSurfaceRendered =
                            paintKind ==
                                StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered,
                    ),
                )
            }
        } else {
            null
        }
    val surface =
        when {
            hostOwnsGlass && onClick == null -> Modifier
            hostOwnsGlass -> Modifier.clip(ShapeCard)
            else ->
                Modifier.clip(ShapeCard).islandBlur(
                    cornerRadius = RadiusCard,
                    fallbackColor = CardBg,
                    onDestinationSurfaceDrawn = destinationSurfaceListener,
                )
        }
    // Chi selected top report — under-card khong overwrite LRTB/W/H.
    val paintedBoundsReporter = LocalStatusBarPaintedCardBoundsReporter.current
    @Suppress("DEPRECATION")
    val paintedSizeLegacy = LocalStatusBarPaintedCardSizeReporter.current
    return this
        .then(surface)
        .then(
            if (reportPaintedTop && paintedBoundsReporter != null) {
                Modifier.onGloballyPositioned { coords ->
                    val w = coords.size.width
                    val h = coords.size.height
                    if (w <= 0 || h <= 0) return@onGloballyPositioned
                    val b = coords.boundsInWindow()
                    paintedBoundsReporter(
                        StatusBarPaintedCardReportPx(
                            layoutWPx = w,
                            layoutHPx = h,
                            windowLeftPx = b.left,
                            windowTopPx = b.top,
                            windowRightPx = b.right,
                            windowBottomPx = b.bottom,
                            ownerKey = paintedOwnerKey,
                            contextToken = paintedContextToken,
                        ),
                    )
                }
            } else if (reportPaintedTop && paintedSizeLegacy != null) {
                Modifier.onSizeChanged { sz ->
                    if (sz.width > 0 && sz.height > 0) {
                        paintedSizeLegacy(sz.width, sz.height)
                    }
                }
            } else {
                Modifier
            },
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(),
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
}

/**
 * Emphasis tone for an island event.
 *
 * [eventStyleFor] returns one of these instead of a fixed colour, so every hue the island draws
 * comes from the user's wallpaper-derived scheme:
 *
 * - [ACCENT]    event identity (most events)
 * - [POSITIVE]  success / active / done — charging, recording saved, unlocked, validated VPN, calls
 * - [ATTENTION] needs notice — alarm ringing, vibrate/silent, unvalidated VPN, paused capture
 * - [ALERT]     privacy or danger — mic/screen capture in progress, low battery, hard errors
 *
 * Events sharing a tone are told apart by icon, motion and chip tier.
 */
internal enum class IslandTone { ACCENT, POSITIVE, ATTENTION, ALERT }

/** Saturated role: icon/text tints and translucent washes (`tint.copy(alpha = …)`). */
internal val IslandTone.tint: Color
    @Composable
    get() = when (this) {
        IslandTone.ACCENT -> MaterialTheme.colorScheme.primary
        IslandTone.POSITIVE -> MaterialTheme.colorScheme.secondary
        IslandTone.ATTENTION -> MaterialTheme.colorScheme.tertiary
        IslandTone.ALERT -> MaterialTheme.colorScheme.error
    }

/** Content legible on the saturated [tint] itself, for glyphs drawn inside a filled shape. */
internal val IslandTone.onTint: Color
    @Composable
    get() = when (this) {
        IslandTone.ACCENT -> MaterialTheme.colorScheme.onPrimary
        IslandTone.POSITIVE -> MaterialTheme.colorScheme.onSecondary
        IslandTone.ATTENTION -> MaterialTheme.colorScheme.onTertiary
        IslandTone.ALERT -> MaterialTheme.colorScheme.onError
    }

/** Filled container role: for chips that paint the tone as their background. */
internal val IslandTone.container: Color
    @Composable
    get() = when (this) {
        IslandTone.ACCENT -> MaterialTheme.colorScheme.primaryContainer
        IslandTone.POSITIVE -> MaterialTheme.colorScheme.secondaryContainer
        IslandTone.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
        IslandTone.ALERT -> MaterialTheme.colorScheme.errorContainer
    }

/** Content legible on [container]. Never pair it with [tint] — that is what [onTint] is for. */
internal val IslandTone.onContainer: Color
    @Composable
    get() = when (this) {
        IslandTone.ACCENT -> MaterialTheme.colorScheme.onPrimaryContainer
        IslandTone.POSITIVE -> MaterialTheme.colorScheme.onSecondaryContainer
        IslandTone.ATTENTION -> MaterialTheme.colorScheme.onTertiaryContainer
        IslandTone.ALERT -> MaterialTheme.colorScheme.onErrorContainer
    }

internal val SubtleGray: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val CardBg: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceBright
internal val DarkCard: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
internal val OnCardText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
internal val OnCardSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val ActionBg: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val OnActionText: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary
internal val DestructiveBg: Color
    @Composable get() = MaterialTheme.colorScheme.errorContainer
internal val OnDestructiveText: Color
    @Composable get() = MaterialTheme.colorScheme.onErrorContainer

/** Keyguard battery chip icon roles. Monet-derived — no fixed hues. */
internal val BatteryChargingColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val BatteryPowerSaveColor: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

/**
 * Soft = seed sang cho nen icon / thanh muc tren kinh one-glass.
 * content/secondary bam OnCard* de doc duoc tren glass sang/toi.
 */
internal data class BatteryColors(
    val isDark: Boolean,
    val content: Color,
    val secondary: Color,
    val charging: Color,
    val powerSave: Color,
    val lowBattery: Color,
    val temperatureWarning: Color,
    val temperatureCritical: Color,
    val chargingSoft: Color,
    val powerSaveSoft: Color,
    val lowBatterySoft: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val trackBackground: Color,
) {
    val iconBgAlpha: Float get() = if (isDark) 0.14f else 0.10f
    val iconBorderAlpha: Float get() = if (isDark) 0.24f else 0.18f
    val fillAlphaStart: Float get() = if (isDark) 0.18f else 0.12f
    val fillAlphaEnd: Float get() = if (isDark) 0.36f else 0.24f

    fun accent(isCharging: Boolean, isPowerSave: Boolean, level: Int): Color =
        when {
            isCharging -> charging
            isPowerSave -> powerSave
            level <= 20 -> lowBattery
            else -> charging
        }

    fun softAccent(isCharging: Boolean, isPowerSave: Boolean, level: Int): Color =
        when {
            isCharging -> chargingSoft
            isPowerSave -> powerSaveSoft
            level <= 20 -> lowBatterySoft
            else -> chargingSoft
        }

    fun temperatureColor(tempC: Float?): Color =
        when {
            tempC == null -> content
            tempC >= 45f -> temperatureCritical
            tempC >= 38f -> temperatureWarning
            else -> content
        }
}

@Composable
internal fun rememberKeyguardBatteryColors(): BatteryColors {
    val dark = isSystemInDarkTheme()
    val content = OnCardText
    val secondary = OnCardSecondary
    val chargingSoft = BatteryChargingColor
    val powerSaveSoft = BatteryPowerSaveColor
    val lowSoft = MaterialTheme.colorScheme.error
    return remember(dark, content, secondary, chargingSoft, powerSaveSoft, lowSoft) {
        if (dark) {
            BatteryColors(
                isDark = true,
                content = content,
                secondary = secondary,
                charging = chargingSoft,
                powerSave = Color(0xFFFF9F0A),
                lowBattery = Color(0xFFFF453A),
                temperatureWarning = Color(0xFFFF9F0A),
                temperatureCritical = Color(0xFFFF453A),
                chargingSoft = chargingSoft,
                powerSaveSoft = powerSaveSoft,
                lowBatterySoft = lowSoft,
                // Metric tile tren one-glass — alpha nhe, khong boc blur card thu hai
                cardBackground = content.copy(alpha = 0.07f),
                cardBorder = content.copy(alpha = 0.10f),
                trackBackground = content.copy(alpha = 0.12f),
            )
        } else {
            BatteryColors(
                isDark = false,
                content = content,
                secondary = secondary,
                charging = Color(0xFF1B5E20),
                powerSave = Color(0xFF8A4500),
                lowBattery = Color(0xFF991B1B),
                temperatureWarning = Color(0xFF8A4500),
                temperatureCritical = Color(0xFF991B1B),
                chargingSoft = chargingSoft,
                powerSaveSoft = powerSaveSoft,
                lowBatterySoft = lowSoft,
                cardBackground = Color.Black.copy(alpha = 0.045f),
                cardBorder = Color.Black.copy(alpha = 0.08f),
                trackBackground = Color.Black.copy(alpha = 0.07f),
            )
        }
    }
}

internal val CardBorderBrush: Brush
    @Composable
    get() =
        Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f),
                )
        )


/** The tone an event is drawn in. */
internal fun toneFor(event: IslandEvent): IslandTone = eventStyleFor(event).tone

/** How much room an event's chip takes. */
internal fun tierFor(event: IslandEvent): ChipTier = eventStyleFor(event).tier

/** Saturated accent for [event]: icon/text tints and translucent washes. */
@Composable internal fun accentColorFor(event: IslandEvent): Color = toneFor(event).tint

/** Filled-container role for [event], for chips that paint the accent as their background. */
@Composable internal fun chipAccentColorFor(event: IslandEvent): Color = toneFor(event).container

/** Content role legible on [chipAccentColorFor]. */
@Composable internal fun chipContentColorFor(event: IslandEvent): Color = toneFor(event).onContainer

/**
 * Colour set for one expanded / keyguard surface, resolved from the event's [IslandTone] against
 * the user's scheme.
 */
internal data class IslandColorScheme(
    /** The saturated fill. */
    val accent: Color,
    /** Content legible *on* [accent] (the tone's `onTint`, not `onContainer`). */
    val onAccent: Color,
    /** A faint wash of [accent], for tinting a surface rather than filling it. */
    val tonal: Color,
    /** A slightly stronger wash, kept for callers that tint a whole panel. */
    val surfaceTint: Color,
)

@Composable
internal fun rememberIslandColors(event: IslandEvent): IslandColorScheme {
    val tone = toneFor(event)
    val tint = tone.tint
    return IslandColorScheme(
        accent = tint,
        onAccent = tone.onTint,
        tonal = tint.copy(alpha = AlphaFaint),
        surfaceTint = tint.copy(alpha = 0.12f),
    )
}

/** Media surfaces use the event's own tone; album-art colour extraction is gone. */
@Composable
internal fun rememberMediaColors(event: IslandEvent.Media): IslandColorScheme =
    rememberIslandColors(event)

/*
 * Height carries urgency, corner radius carries kind. Status-bar pill: tat ca tier 20.dp
 * (LiveCorner = nua chieu cao). Keyguard ChipHeight rieng — khong dung token nay.
 */
internal val ChipTierPassiveHeight = 20.dp
internal val ChipTierActiveHeight = 20.dp
internal val ChipTierLiveHeight = 20.dp

/** Nua [ChipTierLiveHeight] — pill tron deu khi moi tier cung 20.dp. */
internal val ChipTierLiveCorner = 10.dp

internal val ChipTier.chipHeight: Dp
    get() = when (this) {
        ChipTier.PASSIVE -> ChipTierPassiveHeight
        ChipTier.ACTIVE -> ChipTierActiveHeight
        ChipTier.LIVE -> ChipTierLiveHeight
    }

/** Fixed radii rather than percentages, so the corner animates smoothly between tiers. */
internal val ChipTier.chipCornerRadius: Dp
    get() = when (this) {
        ChipTier.PASSIVE -> ChipTierPassiveHeight / 2
        ChipTier.ACTIVE -> ChipTierActiveHeight / 2
        ChipTier.LIVE -> ChipTierLiveCorner
    }

/** Micro badge — a count inside a chip. Below the M3 label floor by design. */
internal val TsBadge: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmallEmphasized.copy(
        fontSize = 8.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

internal val PillPrimary: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmallEmphasized.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

internal val PillAccent: TextStyle
    @Composable get() = MaterialTheme.typography.labelLargeEmphasized

/** Title over a caption, as in the keyguard media chip. */
internal val PillTitle: TextStyle
    @Composable get() = MaterialTheme.typography.titleSmallEmphasized

/** Second line under [PillTitle]; 10 sp is below the M3 label floor by design. */
internal val TsCaption: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmallEmphasized.copy(
        fontSize = 10.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** Mono so ticking digits don't make the readout jitter. */
internal val PillMono: TextStyle
    @Composable get() = MaterialTheme.typography.labelSmallEmphasized.copy(
        fontFamily = FontFamily.Monospace,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

internal val TsMono: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)

/** A ticking number inside an expanded row. */
internal val TsCounter: TextStyle
    @Composable get() = MaterialTheme.typography.labelLargeEmphasized.copy(
        fontFamily = FontFamily.Monospace,
    )

/** Headline value of a compact expanded tile (countdown, stopwatch, recording time). */
internal val TsValue: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = FontFamily.Monospace,
    )

@Composable
internal fun StatusChip(text: String, color: Color = SubtleGray) {
    Box(
        modifier =
            Modifier.background(color.copy(alpha = AlphaSubtle), ShapeChip)
                .padding(horizontal = SpaceMd, vertical = SpaceXxs)
    ) {
        Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelSmallEmphasized)
    }
}

@Composable
internal fun CircleButton(
    color: Color = ActionBg,
    size: Dp = 48.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier.size(size)
                .clip(CircleShape)
                .background(color)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Stack panel tu xu ly tap noi dung — tat onContentClick tren ExpandedCardLayout. */
internal val LocalStackOwnsContentClick = compositionLocalOf { false }

/**
 * The expanded tile shell: a compact header row — icon badge, title column, optional trailing
 * control — over an optional action row. The header is a plain row, not a second tinted surface
 * nested inside the frosted card the tile already sits on.
 */
@Composable
internal fun ExpandedCardLayout(
    accentColor: Color,
    icon: @Composable () -> Unit,
    iconSize: Dp = SizeIconBadge,
    iconBackground: Boolean = true,
    title: @Composable ColumnScope.() -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    actions: @Composable (ColumnScope.() -> Unit)? = null,
    /** Tap header/card content (khong gom nut action). Null = khong mo app. */
    onContentClick: (() -> Unit)? = null,
) {
    // Filter LazyColumn: dung onContentClick. Stack: StackPanelCard xu ly.
    val contentClick =
        if (LocalStackOwnsContentClick.current) null else onContentClick
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpaceSm)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .then(
                        if (contentClick != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = contentClick,
                            )
                        } else {
                            Modifier
                        }
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            Box(
                modifier =
                    if (iconBackground) {
                        Modifier.size(iconSize)
                            .background(accentColor.copy(alpha = AlphaIconBg), ShapeCompact)
                    } else {
                        Modifier.size(iconSize)
                    },
                contentAlignment = Alignment.Center,
            ) { icon() }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXxs),
            ) { title() }
            trailing?.invoke()
        }
        actions?.invoke(this)
    }
}

@Composable
internal fun ActionChip(
    label: String,
    icon: ImageVector? = null,
    color: Color = OnActionText,
    bg: Color = ActionBg,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val height = if (compact) SizeActionIcon else SizeActionHeight
    val horizontal = if (compact) SpaceLg else SpacePanel
    Box(
        modifier =
            modifier
                .clip(ShapeChip)
                .background(bg)
                .border(1.2.dp, color.copy(alpha = 0.12f), ShapeChip)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(color),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(height).padding(horizontal = horizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(SizeIconSm))
            }
            Text(
                label,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Circular, icon-only tile action that fits in a tile's header row. */
@Composable
internal fun ActionIconButton(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = SizeActionIcon,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(bg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(tint),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(SizeIconSm))
    }
}

/**
 * Icon for a classified notification action, or null when there is no unambiguous glyph — the
 * caller then keeps the action's own label rather than inventing a meaning.
 *
 * Exhaustive over [NotificationActionType] so a new constant fails the build instead of
 * silently falling back to a label.
 */
internal val NotificationActionType.icon: ImageVector?
    get() =
        when (this) {
            NotificationActionType.PAUSE -> Icons.Filled.Pause
            NotificationActionType.RESUME -> Icons.Filled.PlayArrow
            NotificationActionType.STOP -> Icons.Filled.Stop
            NotificationActionType.RESET -> Icons.Filled.Refresh
            NotificationActionType.LAP -> Icons.Filled.Flag
            NotificationActionType.ADD_MINUTE -> Icons.Filled.MoreTime
            NotificationActionType.SNOOZE -> Icons.Filled.Snooze
            NotificationActionType.DELETE -> Icons.Filled.Delete
            NotificationActionType.DISMISS -> Icons.Filled.Close
            NotificationActionType.OTHER -> null
        }

internal data class ActionVisuals(val bg: Color, val content: Color)

/** Destructive commands take the error roles, resume the positive one, the rest the event tone. */
@Composable
internal fun NotificationActionType.visuals(tone: IslandTone): ActionVisuals =
    when (this) {
        NotificationActionType.STOP,
        NotificationActionType.DELETE -> ActionVisuals(DestructiveBg, OnDestructiveText)
        NotificationActionType.RESUME -> ActionVisuals(IslandTone.POSITIVE.container, IslandTone.POSITIVE.onContainer)
        else -> ActionVisuals(tone.tint.copy(alpha = AlphaIconBg), tone.tint)
    }

/**
 * One action forwarded from an event's notification: an [ActionIconButton] when the classifier
 * recognises it, a compact labelled [ActionChip] when it doesn't.
 */
@Composable
internal fun NotificationActionButton(
    action: Notification.Action,
    label: String,
    tone: IslandTone,
    modifier: Modifier = Modifier,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    // classify() goes through the package manager; don't repeat it every tick of a running timer.
    val kind =
        remember(action) {
            action.classify(context, action.actionIntent?.creatorPackage ?: context.packageName)
        }
    val icon = kind.icon
    if (icon != null) {
        val visuals = kind.visuals(tone)
        ActionIconButton(
            icon = icon,
            tint = visuals.content,
            bg = visuals.bg,
            contentDescription = label,
            modifier = modifier,
            onClick = onSend,
        )
    } else {
        ActionChip(
            label = label,
            color = tone.tint,
            bg = tone.tint.copy(alpha = AlphaIconBg),
            modifier = modifier,
            compact = true,
            onClick = onSend,
        )
    }
}

/** Most actions a tile carries in its header row before they move to a row of their own. */
internal const val MAX_INLINE_ACTIONS = 2

/**
 * Whether a tile's forwarded actions can sit inline in its header row: only when there are few of
 * them and every one resolves to an icon. A single labelled fallback chip in the header squeezes
 * the title to nothing, so any unrecognised action sends the whole set to an action row instead.
 */
@Composable
internal fun rememberActionsFitInline(actions: List<Notification.Action>): Boolean {
    val context = LocalContext.current
    return remember(actions) {
        actions.size <= MAX_INLINE_ACTIONS &&
            actions.all { action ->
                action.classify(context, action.actionIntent?.creatorPackage ?: context.packageName)
                    .icon != null
            }
    }
}

@Composable
internal fun ExpressivePillButton(
    label: String,
    icon: ImageVector? = null,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(backgroundColor)
                .border(1.2.dp, contentColor.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = islandClickIndication(contentColor),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(SizeActionHeight).padding(horizontal = SpacePanel),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceMd),
        ) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(SizeIconSm))
            }
            Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
        }
    }
}

internal data class MediaProgress(val progress: Float, val positionMs: Long)

@Composable
internal fun rememberMediaProgress(event: IslandEvent.Media): MediaProgress {
    return MediaProgress(event.progress, event.position)
}

/**
 * Cho doi toi lan doi chu so ke tiep — [Chronometer.scheduleTick]:
 * count-up: period - (elapsed % period) + 3ms; countdown: (remaining % period) (+ period neu 0) + 3ms.
 * Moc = gia tri elapsed/remaining cua dong ho, khong phai bien giay uptime.
 */
internal fun chronometerTickDelayMs(
    valueMs: Long,
    periodMs: Long = 1000L,
    isCountdown: Boolean = false,
): Long {
    val period = periodMs.coerceAtLeast(1L)
    val delayMillis =
        if (isCountdown) {
            var d = valueMs % period
            if (d <= 0L) d += period
            d
        } else {
            period - (valueMs.coerceAtLeast(0L) % period)
        }
    return delayMillis + 3L
}

/**
 * Chronometer hien thi: SSOT = [compute], khong phu thuoc remember de giu gia tri paused.
 * Paused/ticking=false → moi composition goi compute() truc tiep.
 * Dang chay → tick theo moc elapsed/remaining (Chronometer.scheduleTick), khong elapsedRealtime % 1000.
 */
@Composable
internal fun rememberDisplayedChronometerMs(
    identityKey: Any?,
    ticking: Boolean,
    tickMs: Long = 1000L,
    isCountdown: Boolean = false,
    compute: () -> Long,
): Long {
    if (!ticking) return compute()
    var ms by remember(identityKey) { mutableLongStateOf(compute()) }
    LaunchedEffect(identityKey, tickMs, isCountdown) {
        while (true) {
            ms = compute()
            delay(chronometerTickDelayMs(ms, tickMs, isCountdown))
        }
    }
    return ms
}

internal fun formatElapsedTime(ms: Long): String {
    val secs = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(secs / 60, secs % 60)
}

internal fun formatCountdownSeconds(secs: Long): String {
    val s = secs.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

internal fun formatCountdownLong(ms: Long): String {
    val secs = (ms / 1000).coerceAtLeast(0)
    val mins = secs / 60
    val hrs = mins / 60
    return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins % 60, secs % 60)
    else "%02d:%02d".format(mins, secs % 60)
}

internal fun formatStopwatch(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val hrs = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    val tenths = ((ms % 1000) / 100).coerceIn(0, 9)
    return if (hrs > 0) "%d:%02d:%02d.%d".format(hrs, mins, secs, tenths)
    else "%d:%02d.%d".format(mins, secs, tenths)
}

/**
 * Compact StatusBar chronometer: MM:SS khi <1h, HH:MM:SS khi >=1h.
 * Giong formatCountdownLong; ten rieng de API compact/ARR ro rang.
 */
internal fun formatCountdownCompact(ms: Long): String = formatCountdownLong(ms)

/**
 * Compact StatusBar stopwatch: MM:SS khi <1h, HH:MM:SS khi >=1h; khong tenths (tick 1Hz).
 * Lam tron giay giong [Chronometer] count-up: Math.round((now - base) / 1000f).
 * Khong floor (ms/1000) — 29.7s phai hien 30, khong 29.
 * Canvas width theo format (Mm/Hh) - tick trong cung format khong remeasure.
 */
internal fun formatStopwatchCompact(ms: Long): String {
    val totalSecs =
        kotlin.math.round(ms.coerceAtLeast(0L) / 1000.0).toLong().coerceAtLeast(0L)
    val hrs = (totalSecs / 3600).coerceAtMost(99)
    val mins = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs)
    else "%02d:%02d".format(mins, secs)
}

internal fun formatTimeAgo(timestampMs: Long, res: android.content.res.Resources): String {
    val diff = System.currentTimeMillis() - timestampMs
    if (diff < 0) return ""
    val secs = diff / 1000
    val mins = secs / 60
    val hrs = mins / 60
    val days = hrs / 24
    return when {
        secs < 60 -> res.getString(R.string.ax_dynamic_bar_just_now)
        mins < 60 -> res.getString(R.string.ax_dynamic_bar_mins_ago, mins.toInt())
        hrs < 24 -> res.getString(R.string.ax_dynamic_bar_hours_ago, hrs.toInt())
        days < 7 -> res.getString(R.string.ax_dynamic_bar_days_ago, days.toInt())
        else -> res.getString(R.string.ax_dynamic_bar_weeks_ago, (days / 7).toInt())
    }
}

@Composable
internal fun Drawable.toScaledBitmap(sizeDp: Dp): ImageBitmap {
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }
    return remember(this, px) { toBitmap(px, px).asImageBitmap() }
}

/** Center-crop to a square, then scale. Use for album art, including small icon slots. */
@Composable
internal fun Drawable.toSquareScaledBitmap(sizeDp: Dp): ImageBitmap {
    val px = with(LocalDensity.current) { sizeDp.roundToPx() }
    return remember(this, px) { cropToSquareThenScale(px).asImageBitmap() }
}

private fun Drawable.cropToSquareThenScale(px: Int): Bitmap {
    val w = intrinsicWidth
    val h = intrinsicHeight
    if (w <= 0 || h <= 0) return toBitmap(px, px)
    val raw = (this as? BitmapDrawable)?.bitmap?.takeUnless { it.isRecycled } ?: toBitmap(w, h)
    val src =
        if (raw.config == Bitmap.Config.HARDWARE) {
            raw.copy(Bitmap.Config.ARGB_8888, false) ?: return toBitmap(px, px)
        } else {
            raw
        }
    val side = min(src.width, src.height)
    if (side <= 0) return toBitmap(px, px)
    val square =
        if (src.width == src.height) src
        else Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    return if (square.width == px && square.height == px) square
    else Bitmap.createScaledBitmap(square, px, px, true)
}

internal fun chipProgressFor(event: IslandEvent, includeMediaProgress: Boolean = false): Float? =
    when (event) {
        is IslandEvent.Media -> if (includeMediaProgress) event.progress.coerceIn(0f, 1f) else null
        is IslandEvent.PromotedOngoing ->
            if (event.progress >= 0f) event.progress.coerceIn(0f, 1f) else null
        is IslandEvent.Notification ->
            if (event.progress >= 0 && event.progressMax > 0)
                (event.progress.toFloat() / event.progressMax).coerceIn(0f, 1f)
            else null
        else -> null
    }

/**
 * Cung quy tac [AxDynamicBarChip] useCircleStyle — chipStyle==1 + loai alert/timer/…
 * Morph shell phai derive tu day (khong chi event accent).
 */
internal fun statusBarChipUsesCircleStyle(
    chipStyle: Int,
    event: IslandEvent,
    isAlert: Boolean = false,
): Boolean =
    chipStyle == 1 &&
        !isAlert &&
        event !is IslandEvent.AudioRecording &&
        event !is IslandEvent.Timer &&
        event !is IslandEvent.Stopwatch &&
        !(event is IslandEvent.Sports && event.team2Name.isNotEmpty())

/**
 * Key doi bai chung cho cover + tieu de — khong dung Drawable identity
 * (UMO rebind moi lan play/pause tao Drawable moi → nhay cover).
 */
internal fun mediaTrackSwitchKey(event: IslandEvent.Media): String =
    "${event.mediaKey}|${event.track}|${event.artist}|${event.packageName}"

/** Fade + scale nhe — dung chung cover va tieu de khi chuyen bai. */
internal fun mediaTrackSwitchTransition(motionScheme: MotionScheme): ContentTransform =
    (fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(initialScale = 0.88f, animationSpec = motionScheme.defaultSpatialSpec())) togetherWith
        (fadeOut(motionScheme.fastEffectsSpec()) +
            scaleOut(targetScale = 0.88f, animationSpec = motionScheme.fastSpatialSpec()))

internal fun iconKeyFor(event: IslandEvent): Any =
    when (event) {
        is IslandEvent.Media -> mediaTrackSwitchKey(event)
        is IslandEvent.Notification -> event.appIcon?.hashCode() ?: "notif_default"
        is IslandEvent.AppSwitch -> {
            val app = event.previousApp ?: event.recentApps.firstOrNull()
            app?.appIcon?.hashCode() ?: "app_default"
        }
        else -> event::class.simpleName ?: "default"
    }

internal fun textKeyFor(event: IslandEvent): Any =
    when (event) {
        is IslandEvent.Media -> mediaTrackSwitchKey(event)
        // Timer: key rieng de doi timer khong dung AnimatedContent tick chung
        is IslandEvent.Timer ->
            event.notificationKey.ifEmpty { "timer" } + "|tick"
        is IslandEvent.Stopwatch,
        is IslandEvent.AudioRecording -> "tick_text"
        else -> event.id
    }

internal fun resolveLabelIcon(label: String): ImageVector {
    val lower = label.lowercase()
    return when {
        lower.contains("shuffle") -> Icons.Filled.Shuffle
        lower.contains("repeat") -> Icons.Filled.Repeat
        lower.contains("thumb") && lower.contains("up") -> Icons.Filled.ThumbUp
        lower.contains("thumb") && lower.contains("down") -> Icons.Filled.ThumbDown
        lower.contains("like") || lower.contains("love") || lower.contains("favorite") -> Icons.Filled.Favorite
        else -> Icons.Filled.Shuffle
    }
}

@Composable
internal fun CustomActionIcon(
    ca: IslandEvent.MediaCustomAction,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val appBitmap = ca.icon?.let { drawable ->
        remember(drawable) {
            try { drawable.toBitmap(48, 48).asImageBitmap() } catch (_: Exception) { null }
        }
    }
    if (appBitmap != null) {
        Icon(appBitmap, ca.label, tint = tint, modifier = modifier)
    } else {
        Icon(resolveLabelIcon(ca.label), ca.label, tint = tint, modifier = modifier)
    }
}

internal fun PendingIntent.sendWithBal(context: Context, fillIntent: Intent? = null) {
    val options = ActivityOptions.makeBasic()
    options.setPendingIntentBackgroundActivityStartMode(
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
    )
    send(context, 0, fillIntent, null, null, null, options.toBundle())
}

/**
 * Mo app tu contentIntent notification; fallback launch package (vd. Clock).
 * Pattern giong NotificationExpanded / openMediaApp.
 */
internal fun openEventContent(
    context: Context,
    contentIntent: PendingIntent?,
    packageName: String = "",
) {
    try {
        if (contentIntent != null) {
            contentIntent.sendWithBal(context)
            return
        }
        val pkg = packageName.takeIf { it.isNotEmpty() } ?: return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/** Mo contentIntent (hoac launch package) roi collapse island — pattern the Notification/Call. */
internal fun openEventContentAndCollapse(
    context: Context,
    contentIntent: PendingIntent?,
    interactor: IslandActions,
    packageName: String = "",
) {
    openEventContent(context, contentIntent, packageName)
    interactor.collapseIsland()
}
