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
    /** false = END ellipsize, khong selected — freeze frame dummy overlay. */
    marqueeEnabled: Boolean = true,
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
                includeFontPadding = false
                typeface = Typeface.create(Typeface.DEFAULT, typefaceStyle)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
        },
        update = { tv ->
            if (marqueeEnabled) {
                tv.ellipsize = TextUtils.TruncateAt.MARQUEE
                tv.marqueeRepeatLimit = -1
                tv.isHorizontalFadingEdgeEnabled = true
                if (tv.horizontalFadingEdgeLength != fadePx) {
                    tv.setFadingEdgeLength(fadePx)
                }
                tv.setHorizontallyScrolling(true)
            } else {
                tv.ellipsize = TextUtils.TruncateAt.END
                tv.isHorizontalFadingEdgeEnabled = false
                tv.setHorizontallyScrolling(false)
                tv.isSelected = false
            }
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            tv.setTextColor(colorArgb)
            tv.gravity = gravity
            val desired = Typeface.create(Typeface.DEFAULT, typefaceStyle)
            if (tv.typeface != desired) {
                tv.typeface = desired
            }
            val textChanged = tv.text?.toString() != text
            if (textChanged) {
                tv.text = text
            }
            if (marqueeEnabled) {
                // Samsung: selected = dieu kien chay marquee; restart sau doi bai.
                if (textChanged || !tv.isSelected) {
                    tv.isSelected = false
                    tv.isSelected = true
                }
            } else {
                tv.isSelected = false
            }
        },
    )
}
