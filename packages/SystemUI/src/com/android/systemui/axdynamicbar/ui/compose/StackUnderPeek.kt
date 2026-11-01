package com.android.systemui.axdynamicbar.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * SSOT peek under (alert + island stack): mep top + 1/2 chieu cao the dang keo,
 * quy ve px local cua under (graphicsLayer under co translation/scale).
 *
 * Vi du: top cao 400, mep tren y=300 → mep duoi vung lo = 500;
 * under clip local = clamp((500 - underY) / underScale, 0, underHeight).
 *
 * Dung boi [StackedExpandedPanels] / [StackedAlertPanels] qua underPeekClip (SSOT peek under).
 */
internal fun underPeekHeightPx(
    topY: Float,
    topHeight: Float,
    underY: Float,
    underHeight: Float,
    underScale: Float,
): Float {
    if (topHeight <= 0f || underHeight <= 0f || underScale <= 0f) return 0f
    val bottomY = topY + topHeight * 0.5f
    return ((bottomY - underY) / underScale).coerceIn(0f, underHeight)
}

/** Clip phan tren; van measure full — toa do pointer on dinh. */
internal fun Modifier.underPeekClip(peekPx: () -> Float): Modifier = drawWithContent {
    val height = peekPx().coerceIn(0f, size.height)
    if (height > 0f) {
        clipRect(bottom = height) { this@drawWithContent.drawContent() }
    }
}
