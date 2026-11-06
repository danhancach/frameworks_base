package com.android.systemui.axdynamicbar.shared

import android.util.Log
import kotlin.math.abs

/** Gated open-glass transfer traces (`adb shell setprop log.tag.AxDbOpenGlass DEBUG`). */
internal const val AxDbOpenGlassTag = "AxDbOpenGlass"

internal fun axDbOpenGlassTraceEnabled(): Boolean =
    Log.isLoggable(AxDbOpenGlassTag, Log.DEBUG)

/** Trace moc open prepare/first-draw/transfer/cancel — khong goi moi animation frame. */
internal fun axDbOpenGlassTrace(event: String, detail: String = "") {
    if (!axDbOpenGlassTraceEnabled()) return
    if (detail.isEmpty()) {
        Log.d(AxDbOpenGlassTag, event)
    } else {
        Log.d(AxDbOpenGlassTag, "$event $detail")
    }
}

/**
 * Phase visual stack expand khi keo doi the (SSOT decision — production + test).
 *
 * HELD: ngon tay con giu (DOWN→move), chua UP that.
 * CANCEL_SETTLE: UP/CANCEL khong du threshold — snap dragY→0.
 * COMMIT_SETTLE: UP commit — pin/leave z / topSettle (chi sau release).
 * IDLE: nghi / xong settle.
 */
internal enum class StackExpandedDragPhase {
    Idle,
    Held,
    CancelSettle,
    CommitSettle,
}

/**
 * Ai so huu glass top / whole-surface trong stack expand.
 *
 * SharedPlate: morph sibling plate (rest / idle).
 * CardLocal: blur nam trong Magnetic/card — cung content transform + dung z.
 */
internal enum class StackExpandedGlassSurfaceOwner {
    SharedPlate,
    CardLocal,
}

/** Epsilon dragY — coi nhu 0 (tranh jitter float). Khong dung de doi glass lifecycle. */
internal const val StackExpandedDragYEpsilonPx = 0.5f

/**
 * Giai phase tu state stack — KHONG dung distance de swap/leave.
 *
 * [isDragging]=true + chua settling → Held (ke ca dragY tam 0 sau DOWN lock axis).
 * settling + leaving → CommitSettle; settling + !leaving → CancelSettle.
 */
internal fun stackExpandedDragPhase(
    isDragging: Boolean,
    isSettling: Boolean,
    leavingId: String?,
): StackExpandedDragPhase =
    when {
        isDragging && !isSettling -> StackExpandedDragPhase.Held
        isSettling && leavingId != null -> StackExpandedDragPhase.CommitSettle
        isSettling -> StackExpandedDragPhase.CancelSettle
        else -> StackExpandedDragPhase.Idle
    }

/**
 * HELD: cam gan leavingId / localPinned / ha z theo threshold.
 * Chi RELEASE (CommitSettle) moi duoc leaving + pin.
 */
internal fun stackExpandedHeldKeepsTopIdentity(
    phase: StackExpandedDragPhase,
    leavingId: String?,
    localPinnedIdAssignedThisGesture: Boolean,
): Boolean {
    if (phase != StackExpandedDragPhase.Held) return true
    return leavingId == null && !localPinnedIdAssignedThisGesture
}

/**
 * Commit chi khi UP that + (distance|velocity) + co under.
 * CANCEL / lost pointer goi voi dragY=0, vel=0 → false.
 */
internal fun stackExpandedShouldCommitOnRelease(
    dragYPx: Float,
    velocityYPx: Float,
    commitThresholdPx: Float,
    commitVelocityPx: Float,
    canCommitStackSwitch: Boolean,
): Boolean {
    if (!canCommitStackSwitch) return false
    return dragYPx >= commitThresholdPx || velocityYPx >= commitVelocityPx
}

/**
 * zIndex layer stack: leaving DUOI top moi; HELD khong bao gio isLeaving.
 * visibleCount=2 → top=2, under=1, leaving=0.5.
 */
internal fun stackExpandedLayerZIndex(
    layer: Int,
    visibleCount: Int,
    isLeaving: Boolean,
): Float =
    if (isLeaving) {
        (visibleCount - 1).toFloat() - 0.5f
    } else {
        (visibleCount - layer).toFloat()
    }

/** Top z phai > under z khi overlap (HELD / idle). */
internal fun stackExpandedTopZAboveUnder(
    topZ: Float,
    underZ: Float,
): Boolean = topZ > underZ

/**
 * Glass owner — tach stack expanded vs pill morph (task-041 / archive stable-tree).
 *
 * Expanded at-rest + Held/Cancel/Commit: luon CardLocal — moi event giu
 * content+islandBlur theo key(event.id) xuyen idle/held/settle/swap.
 * KHONG Idle yield SharedPlate (attach/detach blur → Home flash) va KHONG
 * warm-floor shared (Aggregator: alpha>0 && radius>0 && visible → SF region
 * dung yen = lop nen thu 3 APK040).
 *
 * SharedPlate chi khi !morphExpandedAtRest (pill↔expanded morph shell).
 *
 * Blur backend (axBlurBackground / BlurEngine / BackgroundBlurDrawable):
 * - alpha=0 → hideDrawable (setVisible false + alpha 0) → Aggregator remove.
 * - enabled=false / clearKey → deactivateCrossWindowBlurDrawable (bounds0, radius0).
 * - go Box/islandBlur khoi composition → onDispose(dispose) → DESTROY engine.
 * Canvas alpha0 thoi CHUA du neu van warm 1/255 (con trong mDrawables).
 */
internal fun stackExpandedGlassSurfaceOwner(
    morphExpandedAtRest: Boolean,
    phase: StackExpandedDragPhase,
): StackExpandedGlassSurfaceOwner {
    // phase giu signature (gesture SSOT); ownership khong doi theo phase @ stack.
    if (morphExpandedAtRest) return StackExpandedGlassSurfaceOwner.CardLocal
    return StackExpandedGlassSurfaceOwner.SharedPlate
}

/** true = top dung islandBlur trong card (yield shared off). */
internal fun stackExpandedUsesCardLocalGlass(
    morphExpandedAtRest: Boolean,
    phase: StackExpandedDragPhase,
): Boolean =
    stackExpandedGlassSurfaceOwner(morphExpandedAtRest, phase) ==
        StackExpandedGlassSurfaceOwner.CardLocal

/**
 * Phase handoff glass (task-041/043) — khong PrepareShared / observe Idle yield.
 *
 * CardLocal: expanded stack (moi phase) — shared alpha0 + khong compose plate.
 * SharedPlate: pill morph dang chay.
 * OpenWaitingDestination: at-rest open transfer — GIU shared den local surface ACK
 * (native backdrop HOAC fallback ve that).
 * Cancelled: shared owner vang.
 */
internal enum class StackExpandedGlassHandoffPhase {
    CardLocal,
    SharedPlate,
    OpenWaitingDestination,
    Cancelled,
}

/**
 * Open pill→expanded glass transfer (task-043/045).
 *
 * Readiness = surface local α>0 da ve that:
 * - [StackOpenGlassDestinationPaintKind.NativeBackdrop] BlurEngine cross-window/source
 * - [StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered] fallbackColor ve that
 *   (blur off / unsupported / draw fail) — KHONG fake native ACK / alpha0 content.
 * NoSurface/AlphaZero → khong ready. LIMIT: KHONG GPU/SF PRESENT; View.post coalesce
 * khong bound 1 frame co dinh.
 */
internal enum class StackOpenGlassTransferPhase {
    Idle,
    WaitingDestination,
    Complete,
    Cancelled,
}

/**
 * Ket qua paint destination dung cho open transfer (map tu AxBlurSurfacePaintKind).
 * NoSurface/AlphaZero khong report qua production callback.
 */
internal enum class StackOpenGlassDestinationPaintKind {
    NativeBackdrop,
    FallbackSurfaceRendered,
    NoSurface,
}

/** Mot transition request: session + selected owner + context + rect (size epoch). */
internal data class StackOpenGlassTransferRequest(
    val sessionGen: Int,
    val ownerKey: String,
    val contextToken: Int,
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
    val rightPx: Float = 0f,
    val bottomPx: Float = 0f,
) {
    val widthPx: Float
        get() = (rightPx - leftPx).coerceAtLeast(0f)
    val heightPx: Float
        get() = (bottomPx - topPx).coerceAtLeast(0f)
}

/** Sai so size local vs request (px) — so W/H, khong so window LRTB transformed. */
internal const val StackOpenGlassRectSizeEpsilonPx = 2f

/** Request rect hop le de begin (W/H > 0, owner+context bind). */
internal fun stackOpenGlassRequestGeometryValid(
    ownerKey: String,
    contextToken: Int,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
): Boolean {
    if (ownerKey.isEmpty() || contextToken == 0) return false
    val w = rightPx - leftPx
    val h = bottomPx - topPx
    return w > StackOpenGlassRectSizeEpsilonPx && h > StackOpenGlassRectSizeEpsilonPx
}

/** So khop size local (draw) vs request — toa do local 0..W/H, khong moving window. */
internal fun stackOpenGlassRectSizeMatchesRequest(
    request: StackOpenGlassTransferRequest,
    layoutWidthPx: Float,
    layoutHeightPx: Float,
    epsilonPx: Float = StackOpenGlassRectSizeEpsilonPx,
): Boolean {
    if (layoutWidthPx <= 0f || layoutHeightPx <= 0f) return false
    return kotlin.math.abs(request.widthPx - layoutWidthPx) <= epsilonPx &&
        kotlin.math.abs(request.heightPx - layoutHeightPx) <= epsilonPx
}

/**
 * Ly do reject destination ACK — DEBUG diagnostics (khong flood: caller log khi doi).
 * null = accept. Khong noi epsilon / khong fake ready.
 */
internal fun stackOpenGlassDestinationRejectReason(
    request: StackOpenGlassTransferRequest?,
    transferPhase: StackOpenGlassTransferPhase,
    alreadyAcked: Boolean,
    callbackSessionGen: Int,
    callbackOwnerKey: String,
    callbackContextToken: Int,
    callbackLayoutWidthPx: Float,
    callbackLayoutHeightPx: Float,
    paintKind: StackOpenGlassDestinationPaintKind,
    blurSurfaceAlpha: Float,
    liveExpandedIntent: Boolean,
    legacyContentOnly: Boolean = false,
): String? {
    if (alreadyAcked) return "already-acked"
    if (transferPhase != StackOpenGlassTransferPhase.WaitingDestination) {
        return "phase=$transferPhase"
    }
    if (!liveExpandedIntent) return "not-expanded"
    val req = request ?: return "no-request"
    if (callbackSessionGen != req.sessionGen) {
        return "stale-gen cb=$callbackSessionGen req=${req.sessionGen}"
    }
    if (callbackOwnerKey.isEmpty() || callbackOwnerKey != req.ownerKey) {
        return "owner cb=$callbackOwnerKey req=${req.ownerKey}"
    }
    if (req.contextToken == 0 ||
        callbackContextToken == 0 ||
        callbackContextToken != req.contextToken
    ) {
        return "context cb=$callbackContextToken req=${req.contextToken}"
    }
    if (legacyContentOnly) return null
    if (blurSurfaceAlpha <= 0f) return "alpha=$blurSurfaceAlpha"
    when (paintKind) {
        StackOpenGlassDestinationPaintKind.NoSurface -> return "kind=NoSurface"
        StackOpenGlassDestinationPaintKind.NativeBackdrop,
        StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered -> Unit
    }
    if (
        !stackOpenGlassRectSizeMatchesRequest(
            request = req,
            layoutWidthPx = callbackLayoutWidthPx,
            layoutHeightPx = callbackLayoutHeightPx,
        )
    ) {
        return "size req=${req.widthPx.toInt()}x${req.heightPx.toInt()} " +
            "cb=${callbackLayoutWidthPx.toInt()}x${callbackLayoutHeightPx.toInt()}"
    }
    return null
}

/** Owner/context/size doi trong Waiting → can retarget (cancel gen cu + begin moi). */
internal fun stackOpenGlassShouldRetargetWaiting(
    transferPhase: StackOpenGlassTransferPhase,
    request: StackOpenGlassTransferRequest?,
    ownerKey: String,
    contextToken: Int,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
): Boolean {
    if (transferPhase != StackOpenGlassTransferPhase.WaitingDestination) return false
    val req = request ?: return true
    if (req.ownerKey != ownerKey || req.contextToken != contextToken) return true
    return kotlin.math.abs(req.leftPx - leftPx) > StackOpenGlassRectSizeEpsilonPx ||
        kotlin.math.abs(req.topPx - topPx) > StackOpenGlassRectSizeEpsilonPx ||
        kotlin.math.abs(req.rightPx - rightPx) > StackOpenGlassRectSizeEpsilonPx ||
        kotlin.math.abs(req.bottomPx - bottomPx) > StackOpenGlassRectSizeEpsilonPx
}

/**
 * Quyet dinh ownership/hide — production SSOT (test assert).
 *
 * Expanded stack: CardLocal + shared plate KHONG ve (alpha0, keepSubtree=false)
 * → khong parked third surface / blur region.
 * Morph: SharedPlate + yield card FG-only.
 */
internal data class StackExpandedGlassHandoffDecision(
    val handoffPhase: StackExpandedGlassHandoffPhase,
    val useCardLocalGlass: Boolean,
    val sharedPlateAlpha: Float,
    val yieldNativeGlassToShared: Boolean,
    val keepSharedBlurSubtree: Boolean,
)

/**
 * [morphExpandedAtRest]=true → CardLocal moi phase (Idle/Held/settle).
 * Morph (!atRest) → SharedPlate khi owner present.
 *
 * Open barrier (task-043): goi [stackOpenGlassMergeHandoff] sau decision nay —
 * WaitingDestination GIU shared den local first-draw; Complete moi drop source.
 */
internal fun stackExpandedGlassHandoffDecision(
    morphExpandedAtRest: Boolean,
    phase: StackExpandedDragPhase,
    sharedGlassOwnerPresent: Boolean,
    basePaintAlpha: Float,
): StackExpandedGlassHandoffDecision {
    val base = basePaintAlpha.coerceIn(0f, 1f)
    if (stackExpandedUsesCardLocalGlass(morphExpandedAtRest, phase)) {
        return StackExpandedGlassHandoffDecision(
            handoffPhase = StackExpandedGlassHandoffPhase.CardLocal,
            useCardLocalGlass = true,
            // An shared: Aggregator remove (khong warm 1/255).
            sharedPlateAlpha = 0f,
            yieldNativeGlassToShared = false,
            // Khong compose parked plate trong expanded stack.
            keepSharedBlurSubtree = false,
        )
    }
    if (!sharedGlassOwnerPresent) {
        return StackExpandedGlassHandoffDecision(
            handoffPhase = StackExpandedGlassHandoffPhase.Cancelled,
            useCardLocalGlass = false,
            sharedPlateAlpha = base,
            yieldNativeGlassToShared = false,
            keepSharedBlurSubtree = base > 0.01f,
        )
    }
    return StackExpandedGlassHandoffDecision(
        handoffPhase = StackExpandedGlassHandoffPhase.SharedPlate,
        useCardLocalGlass = false,
        sharedPlateAlpha = base,
        yieldNativeGlassToShared = true,
        keepSharedBlurSubtree = true,
    )
}

/**
 * Bat dau WaitingDestination khi vua vao expanded at-rest va chua Complete.
 * Reverse/collapse (!atRest) → khong begin.
 */
internal fun stackOpenGlassShouldBeginWaitingDestination(
    morphExpandedAtRest: Boolean,
    transferPhase: StackOpenGlassTransferPhase,
): Boolean =
    morphExpandedAtRest &&
        (transferPhase == StackOpenGlassTransferPhase.Idle ||
            transferPhase == StackOpenGlassTransferPhase.Cancelled)

/**
 * Phase dung cho merge/alpha TRUOC SideEffect begin.
 * At-rest + chua Complete/ack → Waiting (tranh 1 frame Idle drop shared).
 */
internal fun stackOpenGlassEffectiveTransferPhase(
    morphExpandedAtRest: Boolean,
    recordedPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
): StackOpenGlassTransferPhase {
    if (!morphExpandedAtRest) return recordedPhase
    if (destinationFirstDrawAcked ||
        recordedPhase == StackOpenGlassTransferPhase.Complete
    ) {
        return recordedPhase
    }
    if (recordedPhase == StackOpenGlassTransferPhase.WaitingDestination) {
        return StackOpenGlassTransferPhase.WaitingDestination
    }
    // Idle/Cancelled @rest: imminent begin — giu shared source ngay frame nay.
    if (recordedPhase == StackOpenGlassTransferPhase.Idle ||
        recordedPhase == StackOpenGlassTransferPhase.Cancelled
    ) {
        return StackOpenGlassTransferPhase.WaitingDestination
    }
    return recordedPhase
}

/** Reverse / collapse / dispose: cancel session — callback sau no-op. */
internal fun stackOpenGlassShouldCancelTransfer(
    morphExpandedAtRest: Boolean,
    isExpandedIntent: Boolean,
    transferPhase: StackOpenGlassTransferPhase,
): Boolean {
    if (transferPhase == StackOpenGlassTransferPhase.Idle ||
        transferPhase == StackOpenGlassTransferPhase.Cancelled
    ) {
        return false
    }
    // Collapse intent: cleanup ngay (Waiting hoac Complete).
    if (!isExpandedIntent) return true
    // Roi rest (close morph / reverse): huy wait + Complete de SharedPlate close dung.
    return !morphExpandedAtRest &&
        (transferPhase == StackOpenGlassTransferPhase.WaitingDestination ||
            transferPhase == StackOpenGlassTransferPhase.Complete)
}

/**
 * Waiting chua ACK + user bat dau stack gesture (Held/settle) → abandon ve CardLocal.
 * Tranh parked shared cover duoi the khi chua co surface (size miss / no paint).
 * KHONG false-mark native ready — chi ket thuc wait (Complete local-only).
 */
internal fun stackOpenGlassShouldAbandonWaitingForStackGesture(
    transferPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
    stackDragPhase: StackExpandedDragPhase,
): Boolean {
    if (destinationFirstDrawAcked) return false
    if (transferPhase != StackOpenGlassTransferPhase.WaitingDestination) return false
    return stackDragPhase == StackExpandedDragPhase.Held ||
        stackDragPhase == StackExpandedDragPhase.CancelSettle ||
        stackDragPhase == StackExpandedDragPhase.CommitSettle
}

/**
 * Destination surface callback — accept 1 lan / session.
 *
 * Production: NativeBackdrop HOAC FallbackSurfaceRendered that + α>0 + session +
 * owner + context + size local khop. NoSurface / alpha0 / content-only → reject.
 * Native flag sai khi fallback → reject (khong goi native ACK gia).
 * [legacyContentOnly]: giu API cu cho caller khac — production open path = false.
 */
internal fun stackOpenGlassAcceptDestinationDraw(
    request: StackOpenGlassTransferRequest?,
    transferPhase: StackOpenGlassTransferPhase,
    alreadyAcked: Boolean,
    callbackSessionGen: Int,
    callbackOwnerKey: String,
    callbackContextToken: Int,
    callbackLayoutWidthPx: Float = 0f,
    callbackLayoutHeightPx: Float = 0f,
    nativeBackdropDrawn: Boolean = false,
    blurSurfaceAlpha: Float = 0f,
    liveExpandedIntent: Boolean = true,
    legacyContentOnly: Boolean = false,
    paintKind: StackOpenGlassDestinationPaintKind =
        if (nativeBackdropDrawn) {
            StackOpenGlassDestinationPaintKind.NativeBackdrop
        } else {
            StackOpenGlassDestinationPaintKind.NoSurface
        },
    fallbackSurfaceRendered: Boolean = false,
): Boolean {
    if (alreadyAcked) return false
    if (transferPhase != StackOpenGlassTransferPhase.WaitingDestination) return false
    if (!liveExpandedIntent) return false
    val req = request ?: return false
    if (callbackSessionGen != req.sessionGen) return false
    if (callbackOwnerKey.isEmpty() || callbackOwnerKey != req.ownerKey) return false
    // Strict: context phai nonzero va khop — token 0 khong bypass.
    if (req.contextToken == 0 ||
        callbackContextToken == 0 ||
        callbackContextToken != req.contextToken
    ) {
        return false
    }
    if (!legacyContentOnly) {
        if (blurSurfaceAlpha <= 0f) return false
        val kindReady =
            when (paintKind) {
                StackOpenGlassDestinationPaintKind.NativeBackdrop ->
                    nativeBackdropDrawn && !fallbackSurfaceRendered
                StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered ->
                    fallbackSurfaceRendered && !nativeBackdropDrawn
                StackOpenGlassDestinationPaintKind.NoSurface -> false
            }
        if (!kindReady) return false
        if (
            !stackOpenGlassRectSizeMatchesRequest(
                request = req,
                layoutWidthPx = callbackLayoutWidthPx,
                layoutHeightPx = callbackLayoutHeightPx,
            )
        ) {
            return false
        }
    }
    return true
}

/**
 * Source shared CHI duoc drop khi destination da first-draw (hoac khong trong wait).
 * Waiting + !acked → false (GIU shared).
 */
internal fun stackOpenGlassMayDropSharedSource(
    morphExpandedAtRest: Boolean,
    transferPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
): Boolean {
    if (!morphExpandedAtRest) {
        // Morph SharedPlate: keep shared — "drop" o day nghia la chuyen CardLocal.
        return false
    }
    if (transferPhase == StackOpenGlassTransferPhase.WaitingDestination &&
        !destinationFirstDrawAcked
    ) {
        return false
    }
    return true
}

/**
 * Local card blur alpha factor trong open transfer (task-044/045).
 *
 * Waiting + Complete: luon 1 — local surface (native HOAC fallback) ve α>0
 * TRUOC khi drop shared. Alpha0 bi cam (hideDrawable ≠ glass ready).
 * [destinationFirstDrawAcked] / [transferPhase] giu signature test/callsite.
 * LIMIT: overlap Waiting shared+local cho den ACK (View.post, khong bound 1 frame).
 */
@Suppress("UNUSED_PARAMETER")
internal fun stackOpenGlassLocalBlurAlphaFactor(
    transferPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
): Float = 1f

/**
 * Merge barrier open vao handoff 041.
 *
 * WaitingDestination: attach CardLocal (yield=false, local blur α=1) + GIU shared
 * source (alpha>0) — complementary: shared cover den dest surface ACK (native
 * hoac fallback ve that), roi drop shared; khong parked warm sau Complete.
 * Complete @rest: CardLocal thuan (keepShared=false) — no third plate.
 */
internal fun stackOpenGlassMergeHandoff(
    base: StackExpandedGlassHandoffDecision,
    morphExpandedAtRest: Boolean,
    transferPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
    basePaintAlpha: Float,
): StackExpandedGlassHandoffDecision {
    if (!morphExpandedAtRest) return base
    val waiting =
        transferPhase == StackOpenGlassTransferPhase.WaitingDestination &&
            !destinationFirstDrawAcked
    if (!waiting) {
        // Complete / Cancelled@rest / Idle@rest → CardLocal 041 (base).
        return base
    }
    val sharedAlpha = basePaintAlpha.coerceIn(0f, 1f).let { a ->
        if (a > 0.01f) a else 1f
    }
    return StackExpandedGlassHandoffDecision(
        handoffPhase = StackExpandedGlassHandoffPhase.OpenWaitingDestination,
        useCardLocalGlass = true,
        sharedPlateAlpha = sharedAlpha,
        yieldNativeGlassToShared = false,
        keepSharedBlurSubtree = true,
    )
}

/** true = top card can report destination first-draw (await transfer). */
internal fun stackOpenGlassAwaitDestinationDraw(
    transferPhase: StackOpenGlassTransferPhase,
    destinationFirstDrawAcked: Boolean,
): Boolean =
    transferPhase == StackOpenGlassTransferPhase.WaitingDestination &&
        !destinationFirstDrawAcked

/**
 * Controller nhe — session/phase/ack; stale callback no-op qua pure guards.
 * State-write sau surface draw: caller view.post coalesce + gen check (024/039).
 * View.post KHONG guarantee 1 frame — chi hop le khi gen/owner/ctx/rect con live.
 */
internal class StackOpenGlassTransferController {
    var sessionGen: Int = 0
        private set
    var phase: StackOpenGlassTransferPhase = StackOpenGlassTransferPhase.Idle
        private set
    var request: StackOpenGlassTransferRequest? = null
        private set
    var destinationFirstDrawAcked: Boolean = false
        private set

    /**
     * Begin Waiting tu rect owner/context hop le.
     * 0-rect / token0 / owner rong → null (continuity pending ≠ ready).
     */
    fun beginWaitingDestination(
        ownerKey: String,
        contextToken: Int,
        leftPx: Float = 0f,
        topPx: Float = 0f,
        rightPx: Float = 0f,
        bottomPx: Float = 0f,
    ): StackOpenGlassTransferRequest? {
        if (!stackOpenGlassShouldBeginWaitingDestination(true, phase)) {
            return null
        }
        if (
            !stackOpenGlassRequestGeometryValid(
                ownerKey = ownerKey,
                contextToken = contextToken,
                leftPx = leftPx,
                topPx = topPx,
                rightPx = rightPx,
                bottomPx = bottomPx,
            )
        ) {
            return null
        }
        return startWaitingLocked(
            ownerKey = ownerKey,
            contextToken = contextToken,
            leftPx = leftPx,
            topPx = topPx,
            rightPx = rightPx,
            bottomPx = bottomPx,
            traceEvent = "prepare",
        )
    }

    /**
     * Waiting + owner/context/rect doi → cancel gen cu + begin moi.
     * Cung identity → null (giu session). Idle/Cancelled → begin nhu [beginWaitingDestination].
     */
    fun retargetOrBeginWaitingDestination(
        ownerKey: String,
        contextToken: Int,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
    ): StackOpenGlassTransferRequest? {
        if (
            !stackOpenGlassRequestGeometryValid(
                ownerKey = ownerKey,
                contextToken = contextToken,
                leftPx = leftPx,
                topPx = topPx,
                rightPx = rightPx,
                bottomPx = bottomPx,
            )
        ) {
            return null
        }
        if (phase == StackOpenGlassTransferPhase.WaitingDestination) {
            if (
                !stackOpenGlassShouldRetargetWaiting(
                    transferPhase = phase,
                    request = request,
                    ownerKey = ownerKey,
                    contextToken = contextToken,
                    leftPx = leftPx,
                    topPx = topPx,
                    rightPx = rightPx,
                    bottomPx = bottomPx,
                )
            ) {
                return null
            }
            cancel("retarget")
        }
        if (!stackOpenGlassShouldBeginWaitingDestination(true, phase)) {
            return null
        }
        return startWaitingLocked(
            ownerKey = ownerKey,
            contextToken = contextToken,
            leftPx = leftPx,
            topPx = topPx,
            rightPx = rightPx,
            bottomPx = bottomPx,
            traceEvent = "retarget-prepare",
        )
    }

    private fun startWaitingLocked(
        ownerKey: String,
        contextToken: Int,
        leftPx: Float,
        topPx: Float,
        rightPx: Float,
        bottomPx: Float,
        traceEvent: String,
    ): StackOpenGlassTransferRequest {
        sessionGen += 1
        destinationFirstDrawAcked = false
        lastRejectTrace = null
        phase = StackOpenGlassTransferPhase.WaitingDestination
        val req =
            StackOpenGlassTransferRequest(
                sessionGen = sessionGen,
                ownerKey = ownerKey,
                contextToken = contextToken,
                leftPx = leftPx,
                topPx = topPx,
                rightPx = rightPx,
                bottomPx = bottomPx,
            )
        request = req
        axDbOpenGlassTrace(
            traceEvent,
            "gen=$sessionGen owner=$ownerKey ctx=$contextToken " +
                "w=${req.widthPx.toInt()} h=${req.heightPx.toInt()}",
        )
        return req
    }

    /**
     * Legacy content-only report — KHONG dung production open path (alpha0 fake ready).
     * Giu cho caller/test cu; mac dinh reject native-required path.
     */
    fun onDestinationDraw(
        callbackSessionGen: Int,
        callbackOwnerKey: String,
        callbackContextToken: Int,
    ): Boolean =
        onDestinationSurfaceDrawn(
            callbackSessionGen = callbackSessionGen,
            callbackOwnerKey = callbackOwnerKey,
            callbackContextToken = callbackContextToken,
            callbackLayoutWidthPx = 0f,
            callbackLayoutHeightPx = 0f,
            paintKind = StackOpenGlassDestinationPaintKind.NoSurface,
            blurSurfaceAlpha = 0f,
            liveExpandedIntent = true,
            legacyContentOnly = true,
        )

    /**
     * Production path cu (native-only) — map sang [onDestinationSurfaceDrawn].
     * Fallback that phai goi surface API voi FallbackSurfaceRendered (khong set native=true).
     */
    fun onDestinationNativeGlassDrawn(
        callbackSessionGen: Int,
        callbackOwnerKey: String,
        callbackContextToken: Int,
        callbackLayoutWidthPx: Float,
        callbackLayoutHeightPx: Float,
        nativeBackdropDrawn: Boolean,
        blurSurfaceAlpha: Float,
        liveExpandedIntent: Boolean = true,
        legacyContentOnly: Boolean = false,
    ): Boolean =
        onDestinationSurfaceDrawn(
            callbackSessionGen = callbackSessionGen,
            callbackOwnerKey = callbackOwnerKey,
            callbackContextToken = callbackContextToken,
            callbackLayoutWidthPx = callbackLayoutWidthPx,
            callbackLayoutHeightPx = callbackLayoutHeightPx,
            paintKind =
                if (nativeBackdropDrawn) {
                    StackOpenGlassDestinationPaintKind.NativeBackdrop
                } else {
                    StackOpenGlassDestinationPaintKind.NoSurface
                },
            blurSurfaceAlpha = blurSurfaceAlpha,
            liveExpandedIntent = liveExpandedIntent,
            legacyContentOnly = legacyContentOnly,
        )

    /** Ly do reject gan nhat, chi trace khi doi de tranh lap moi frame. */
    private var lastRejectTrace: String? = null

    /**
     * Chi nhan surface da ve that, dung size, intent, owner va context.
     * Callback cu, NoSurface va alpha0 khong thay doi ownership.
     */
    fun onDestinationSurfaceDrawn(
        callbackSessionGen: Int,
        callbackOwnerKey: String,
        callbackContextToken: Int,
        callbackLayoutWidthPx: Float,
        callbackLayoutHeightPx: Float,
        paintKind: StackOpenGlassDestinationPaintKind,
        blurSurfaceAlpha: Float,
        liveExpandedIntent: Boolean = true,
        legacyContentOnly: Boolean = false,
    ): Boolean {
        val nativeOk = paintKind == StackOpenGlassDestinationPaintKind.NativeBackdrop
        val fallbackOk =
            paintKind == StackOpenGlassDestinationPaintKind.FallbackSurfaceRendered
        val accepted =
            stackOpenGlassAcceptDestinationDraw(
                request = request,
                transferPhase = phase,
                alreadyAcked = destinationFirstDrawAcked,
                callbackSessionGen = callbackSessionGen,
                callbackOwnerKey = callbackOwnerKey,
                callbackContextToken = callbackContextToken,
                callbackLayoutWidthPx = callbackLayoutWidthPx,
                callbackLayoutHeightPx = callbackLayoutHeightPx,
                nativeBackdropDrawn = nativeOk,
                blurSurfaceAlpha = blurSurfaceAlpha,
                liveExpandedIntent = liveExpandedIntent,
                legacyContentOnly = legacyContentOnly,
                paintKind = paintKind,
                fallbackSurfaceRendered = fallbackOk,
            )
        if (!accepted) {
            val reason =
                if (axDbOpenGlassTraceEnabled()) {
                    stackOpenGlassDestinationRejectReason(
                        request = request,
                        transferPhase = phase,
                        alreadyAcked = destinationFirstDrawAcked,
                        callbackSessionGen = callbackSessionGen,
                        callbackOwnerKey = callbackOwnerKey,
                        callbackContextToken = callbackContextToken,
                        callbackLayoutWidthPx = callbackLayoutWidthPx,
                        callbackLayoutHeightPx = callbackLayoutHeightPx,
                        paintKind = paintKind,
                        blurSurfaceAlpha = blurSurfaceAlpha,
                        liveExpandedIntent = liveExpandedIntent,
                        legacyContentOnly = legacyContentOnly,
                    )
                } else {
                    null
                }
            if (reason != null && reason != lastRejectTrace) {
                lastRejectTrace = reason
                axDbOpenGlassTrace(
                    "dest-reject",
                    "gen=$callbackSessionGen kind=$paintKind $reason",
                )
            }
            return false
        }
        lastRejectTrace = null
        destinationFirstDrawAcked = true
        phase = StackOpenGlassTransferPhase.Complete
        axDbOpenGlassTrace(
            if (fallbackOk) "fallback-surface+transfer" else "native-glass+transfer",
            "gen=$callbackSessionGen owner=$callbackOwnerKey " +
                "w=${callbackLayoutWidthPx.toInt()} h=${callbackLayoutHeightPx.toInt()} " +
                "a=$blurSurfaceAlpha kind=$paintKind",
        )
        return true
    }

    /**
     * Waiting + stack gesture ma chua co surface ACK → Complete CardLocal, drop shared.
     * KHONG claim native ready; khong restart opening shared (Complete chan begin).
     */
    fun abandonWaitingToCardLocal(reason: String): Boolean {
        if (phase != StackOpenGlassTransferPhase.WaitingDestination) return false
        if (destinationFirstDrawAcked) return false
        val gen = sessionGen
        destinationFirstDrawAcked = true
        phase = StackOpenGlassTransferPhase.Complete
        axDbOpenGlassTrace("abandon-waiting+transfer", "gen=$gen reason=$reason")
        return true
    }

    fun cancel(reason: String) {
        if (phase == StackOpenGlassTransferPhase.Idle ||
            phase == StackOpenGlassTransferPhase.Cancelled
        ) {
            return
        }
        val gen = sessionGen
        phase = StackOpenGlassTransferPhase.Cancelled
        destinationFirstDrawAcked = false
        request = null
        axDbOpenGlassTrace("cancel", "gen=$gen reason=$reason")
    }

    fun resetIdle() {
        phase = StackOpenGlassTransferPhase.Idle
        destinationFirstDrawAcked = false
        request = null
    }
}

/**
 * Yield shared owner chi khi KHONG dung card-local glass.
 * Under van native (reportPaintedTop=false) — khong anh huong.
 */
internal fun stackExpandedYieldNativeGlassToSharedOwner(
    sharedGlassOwnerPresent: Boolean,
    useCardLocalGlass: Boolean,
): Boolean = sharedGlassOwnerPresent && !useCardLocalGlass

/**
 * Alpha plate shared glass.
 * Card-local (expanded stack): 0 — hideDrawable / Aggregator remove; KHONG warm floor.
 * SharedPlate (morph): base handoff alpha.
 */
internal fun stackExpandedSharedGlassPlateAlpha(
    basePaintAlpha: Float,
    useCardLocalGlass: Boolean,
): Float =
    if (useCardLocalGlass) {
        0f
    } else {
        basePaintAlpha.coerceIn(0f, 1f)
    }

/**
 * Compose shared islandBlur chi khi morph SharedPlate (alpha>0).
 * Expanded CardLocal: false — khong parked third surface.
 */
internal fun stackExpandedKeepSharedBlurSubtree(
    basePaintAlpha: Float,
    useCardLocalGlass: Boolean,
): Boolean = !useCardLocalGlass && basePaintAlpha > 0.01f

/**
 * translationY noi dung Magnetic (AXIS_STACK: vy≈0 + contentOffset).
 * Doc provider LIVE trong graphicsLayer — tranh param Float tre 1 frame vs glass.
 */
internal fun stackExpandedContentTranslationYPx(
    magneticVyPx: Float,
    contentOffsetYPx: Float,
): Float = magneticVyPx + contentOffsetYPx

/**
 * Ty the top/incoming theo phase (world / card outer layer).
 * CommitSettle: dragY=0 nhung topSettleY>0 — glass local di cung topSettleY.
 * Held / CancelSettle: dragY (Magnetic contentOffset).
 * Idle: 0.
 *
 * Production wiring (tranh double-apply):
 * - Outer top Box: baseYPx + helper(phase, dragYPx=0, topSettleY) — chi settle.
 * - Magnetic contentOffsetY: helper(phase, dragY, topSettleYPx=0) — chi drag.
 * - Khong doi glass phase theo |dragY|.
 */
internal fun stackExpandedIncomingSurfaceTranslationYPx(
    phase: StackExpandedDragPhase,
    dragYPx: Float,
    topSettleYPx: Float,
): Float =
    when (phase) {
        StackExpandedDragPhase.CommitSettle -> topSettleYPx
        StackExpandedDragPhase.Held,
        StackExpandedDragPhase.CancelSettle -> dragYPx
        StackExpandedDragPhase.Idle -> 0f
    }

/**
 * Ty panel outgoing trong CommitSettle (leaveStart → nest).
 * HELD khong co outgoing leave.
 */
internal fun stackExpandedOutgoingLeaveTranslationYPx(
    leaveProgress: Float,
    leaveStartYPx: Float,
    leaveNestYPx: Float,
): Float {
    val t = leaveProgress.coerceIn(0f, 1f)
    return leaveStartYPx + (leaveNestYPx - leaveStartYPx) * t
}

/**
 * Phase publish tai UP/release — TRUOC doi translation (dragY/topSettle/leave).
 * Commit vs Cancel chi sau threshold; khong theo |dragY| mid-move.
 */
internal fun stackExpandedPhaseOnPointerRelease(shouldCommit: Boolean): StackExpandedDragPhase =
    if (shouldCommit) {
        StackExpandedDragPhase.CommitSettle
    } else {
        StackExpandedDragPhase.CancelSettle
    }

/**
 * Settle Job con so huu generation hien tai — stale Job (gen da bump) → false.
 * Dung cho TOAN BO mutation/reporter trong finally (khong chi publish Idle).
 */
internal fun stackExpandedOwnsSettleGeneration(
    settleGenAtStart: Int,
    currentSettleGen: Int,
): Boolean = settleGenAtStart == currentSettleGen

/**
 * Quyet dinh cleanup trong settle Job finally — production SSOT (test assert day).
 *
 * Stale gen → khong clearSettling / khong reporter / khong Idle (tranh xoa settle B
 * khi A.finally den muon sau B.isSettling=true).
 * Gen khop → cleanup normal; Idle chi khi !isDragging (Held moi dang DOWN).
 */
internal data class StackExpandedSettleFinallyActions(
    val clearSettlingAndReporters: Boolean,
    val reportDragYZeroIfZero: Boolean,
    val publishIdle: Boolean,
)

internal fun stackExpandedSettleFinallyActions(
    settleGenAtStart: Int,
    currentSettleGen: Int,
    isDragging: Boolean,
    dragYIsZero: Boolean,
): StackExpandedSettleFinallyActions {
    if (!stackExpandedOwnsSettleGeneration(settleGenAtStart, currentSettleGen)) {
        return StackExpandedSettleFinallyActions(
            clearSettlingAndReporters = false,
            reportDragYZeroIfZero = false,
            publishIdle = false,
        )
    }
    return StackExpandedSettleFinallyActions(
        clearSettlingAndReporters = true,
        reportDragYZeroIfZero = dragYIsZero,
        publishIdle = !isDragging,
    )
}

/**
 * Settle Job finally: chi Idle khi gen con khop VA khong co gesture moi (Held).
 * @see stackExpandedSettleFinallyActions — cleanup state cung guard gen.
 */
internal fun stackExpandedShouldPublishIdleAfterSettle(
    settleGenAtStart: Int,
    currentSettleGen: Int,
    isDragging: Boolean,
): Boolean =
    stackExpandedSettleFinallyActions(
            settleGenAtStart = settleGenAtStart,
            currentSettleGen = currentSettleGen,
            isDragging = isDragging,
            dragYIsZero = false,
        )
        .publishIdle

/**
 * Khi card-local: glass+content cung surface ty (delta≈0).
 * Khi shared plate: glassTy = dragY (morphExpandedAtRest) — ky vong = contentOffset.
 */
internal fun stackExpandedWholeSurfaceYDeltaPx(
    contentTranslationYPx: Float,
    glassTranslationYPx: Float,
): Float = abs(contentTranslationYPx - glassTranslationYPx)

/**
 * CANCEL / lost pointer / dispose: dragY=0, vel=0 → khong commit.
 * Phan biet UP commit vs cancel end.
 */
internal fun stackExpandedReleaseIsCancelEnd(
    dragYPx: Float,
    velocityYPx: Float,
): Boolean = abs(dragYPx) <= StackExpandedDragYEpsilonPx && abs(velocityYPx) <= 1f
