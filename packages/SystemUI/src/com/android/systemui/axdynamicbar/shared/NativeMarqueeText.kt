package com.android.systemui.axdynamicbar.shared

import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Chay chu + fading edge theo Samsung NowBar / FaceWidget music:
 * - [TextView] `ellipsize=MARQUEE`, `marqueeRepeatLimit=-1`
 * - `setHorizontalFadingEdgeEnabled(true)` + `setFadingEdgeLength` (fade native View)
 * - `isSelected=true` de bat marquee (Samsung `setSelected`)
 *
 * Thay Compose `basicMarquee` + Offscreen/DstIn (de giat khi Media tick progress).
 */
@Composable
internal fun NativeMarqueeText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    fontWeight: Int = 400,
    fadeLength: Dp = SpaceMd,
    gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
) {
    val fadePx = with(LocalDensity.current) { fadeLength.roundToPx().coerceAtLeast(1) }
    val textSizePx = with(LocalDensity.current) { textSize.toPx() }
    val colorArgb = color.toArgb()
    val typefaceStyle = if (fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isHorizontalFadingEdgeEnabled = true
                setFadingEdgeLength(fadePx)
                includeFontPadding = false
                setHorizontallyScrolling(true)
                typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
                // Samsung: selected = dieu kien chay marquee.
                isSelected = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        },
        update = { tv ->
            if (tv.horizontalFadingEdgeLength != fadePx) {
                tv.setFadingEdgeLength(fadePx)
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            tv.setTextColor(colorArgb)
            tv.gravity = gravity
            val desired = Typeface.create(Typeface.DEFAULT, typefaceStyle)
            if (tv.typeface != desired) {
                tv.typeface = desired
            }
            if (tv.text?.toString() != text) {
                tv.text = text
                // Restart sau doi bai (Samsung rebind chip text).
                tv.isSelected = false
                tv.isSelected = true
            } else if (!tv.isSelected) {
                tv.isSelected = true
            }
        },
    )
}
