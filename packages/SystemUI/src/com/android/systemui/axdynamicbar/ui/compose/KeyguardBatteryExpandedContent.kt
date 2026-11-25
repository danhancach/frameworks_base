@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.axdynamicbar.ui.compose

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Gravity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.axdynamicbar.shared.BatteryColors
import com.android.systemui.axdynamicbar.shared.NativeMarqueeText
import com.android.systemui.axdynamicbar.shared.PillMono
import com.android.systemui.axdynamicbar.shared.PillPrimary
import com.android.systemui.axdynamicbar.shared.SpaceMd
import com.android.systemui.axdynamicbar.shared.rememberKeyguardBatteryColors
import com.android.systemui.axdynamicbar.ui.KeyguardBatteryInfo
import com.android.systemui.res.R
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun rememberChargingParts(batteryString: String): List<String> {
    return remember(batteryString) {
        batteryString
            .split('\n', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

@Composable
private fun AnimatedChargingBoltIcon(color: Color, iconSize: Dp = 20.dp) {
    val boltPath = remember { Path() }
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        boltPath.rewind()
        boltPath.moveTo(w * 0.55f, h * 0.05f)
        boltPath.lineTo(w * 0.25f, h * 0.50f)
        boltPath.lineTo(w * 0.45f, h * 0.50f)
        boltPath.lineTo(w * 0.40f, h * 0.95f)
        boltPath.lineTo(w * 0.75f, h * 0.42f)
        boltPath.lineTo(w * 0.55f, h * 0.42f)
        boltPath.close()
        drawPath(boltPath, color)
    }
}

/**
 * Doc dong dien pin (mA) thuc te.
 * Uu tien sysfs current_now -> BatteryManager CURRENT_NOW -> CURRENT_AVERAGE ->
 * sysfs power_now / voltage_now. Khong co du lieu → 0 (UI hien '--').
 * KHONG synthetic brightness/baseDrain/jitter.
 */
internal fun readBatteryCurrentMa(context: Context, isCharging: Boolean): Int {
    try {
        val pathCurrent = "/sys/class/power_supply/battery/current_now"
        val file = java.io.File(pathCurrent)
        if (file.exists() && file.canRead()) {
            val text = file.readText().trim()
            val raw = text.toLongOrNull()
            if (raw != null && raw != 0L) {
                val absMa =
                    if (abs(raw) > 10_000L) {
                        (abs(raw) / 1000L).toInt()
                    } else {
                        abs(raw).toInt()
                    }
                if (absMa in 10..15_000) {
                    return if (isCharging) absMa else -absMa
                }
            }
        }
    } catch (_: Exception) {}

    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    if (bm != null) {
        try {
            val propNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (propNow != Long.MIN_VALUE && propNow != Long.MAX_VALUE && propNow != 0L) {
                val absMa =
                    if (abs(propNow) > 10_000L) {
                        (abs(propNow) / 1000L).toInt()
                    } else {
                        abs(propNow).toInt()
                    }
                if (absMa in 10..15_000) {
                    return if (isCharging) absMa else -absMa
                }
            }
        } catch (_: Exception) {}

        try {
            val propAvg = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (propAvg != Long.MIN_VALUE && propAvg != Long.MAX_VALUE && propAvg != 0L) {
                val absMa =
                    if (abs(propAvg) > 10_000L) {
                        (abs(propAvg) / 1000L).toInt()
                    } else {
                        abs(propAvg).toInt()
                    }
                if (absMa in 10..15_000) {
                    return if (isCharging) absMa else -absMa
                }
            }
        } catch (_: Exception) {}
    }

    try {
        val filePower = java.io.File("/sys/class/power_supply/battery/power_now")
        val fileVolt = java.io.File("/sys/class/power_supply/battery/voltage_now")
        if (filePower.exists() && fileVolt.exists() && filePower.canRead() && fileVolt.canRead()) {
            val powerRaw = filePower.readText().trim().toLongOrNull() ?: 0L
            val voltRaw = fileVolt.readText().trim().toLongOrNull() ?: 0L
            if (powerRaw > 0L && voltRaw > 0L) {
                val ratio = powerRaw.toDouble() / voltRaw.toDouble()
                val ma =
                    when {
                        ratio > 1000.0 -> (ratio / 10.0).toInt()
                        ratio in 1.0..100.0 -> (ratio * 100.0).toInt()
                        else -> (ratio * 1000.0).toInt()
                    }
                if (ma in 50..6000) {
                    return if (isCharging) ma else -ma
                }
            }
        }
    } catch (_: Exception) {}

    return 0
}

/**
 * The mo rong thong tin pin tren man hinh khoa.
 * Chi compose khi expanded/visible — LaunchedEffect huy poll khi collapse.
 */
@Composable
internal fun KeyguardBatteryExpandedCard(
    info: KeyguardBatteryInfo,
    batteryString: String,
    modifier: Modifier = Modifier,
    onContentSizeChanged: ((Int, Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.ax_dynamic_bar_unavailable)

    val batteryStatusIntent =
        remember(context, info.level, info.isCharging) {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }

    val colors = rememberKeyguardBatteryColors()
    val accent = colors.accent(info.isCharging, info.isPowerSave, info.level)
    val soft = colors.softAccent(info.isCharging, info.isPowerSave, info.level)

    val tempRaw = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
    val tempFloat = if (tempRaw > 0) tempRaw / 10f else null
    val tempStr =
        if (tempFloat != null) {
            String.format(java.util.Locale.US, "%.1f°C", tempFloat)
        } else {
            unavailable
        }
    val tempColor = colors.temperatureColor(tempFloat)

    val voltRaw = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
    val voltStr =
        if (voltRaw > 0) {
            String.format(java.util.Locale.US, "%.2f V", voltRaw / 1000f)
        } else {
            unavailable
        }

    // Poll dong dien chi khi card con trong composition (expanded); IO off main; huy khi collapse.
    var currentMa by remember { mutableIntStateOf(0) }
    LaunchedEffect(info.isCharging, info.level) {
        while (true) {
            currentMa =
                withContext(Dispatchers.IO) { readBatteryCurrentMa(context, info.isCharging) }
            delay(1500L)
        }
    }
    val currentStr =
        if (currentMa != 0) {
            val absMa = abs(currentMa)
            if (info.isCharging) "+$absMa mA" else "-$absMa mA"
        } else {
            unavailable
        }
    val currentColor = if (info.isCharging) colors.charging else colors.content

    val plugged = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val sourceAc = stringResource(R.string.ax_dynamic_bar_source_ac)
    val sourceUsb = stringResource(R.string.ax_dynamic_bar_source_usb)
    val sourceWireless = stringResource(R.string.ax_dynamic_bar_source_wireless)
    val sourceDock = stringResource(R.string.ax_dynamic_bar_source_dock)
    val chargingStr = stringResource(R.string.ax_dynamic_bar_charging)
    val onBatteryStr = stringResource(R.string.ax_dynamic_bar_on_battery)
    val sourceStr =
        when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> sourceAc
            BatteryManager.BATTERY_PLUGGED_USB -> sourceUsb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> sourceWireless
            BatteryManager.BATTERY_PLUGGED_DOCK -> sourceDock
            else -> if (info.isCharging) chargingStr else onBatteryStr
        }

    val statusRaw =
        batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val statusStr =
        when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING ->
                stringResource(R.string.ax_dynamic_bar_status_charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING ->
                stringResource(R.string.ax_dynamic_bar_status_discharging)
            BatteryManager.BATTERY_STATUS_FULL ->
                stringResource(R.string.ax_dynamic_bar_status_full)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING ->
                stringResource(R.string.ax_dynamic_bar_status_not_charging)
            else -> unavailable
        }

    val wirelessChargingStr = stringResource(R.string.ax_dynamic_bar_wireless_charging)
    val wirelessStr = stringResource(R.string.ax_dynamic_bar_wireless)
    val batterySaverStr = stringResource(R.string.ax_dynamic_bar_battery_saver)
    val lowBatteryStr = stringResource(R.string.ax_dynamic_bar_low_battery)
    val normalStr = stringResource(R.string.ax_dynamic_bar_battery_normal)
    val parts = rememberChargingParts(batteryString)

    val chargingTitle =
        remember(
            parts,
            info.isCharging,
            info.isWireless,
            info.isPowerSave,
            info.level,
            wirelessChargingStr,
            chargingStr,
            batterySaverStr,
            lowBatteryStr,
            onBatteryStr,
        ) {
            if (info.isCharging) {
                if (parts.isNotEmpty()) {
                    val firstPart = parts[0]
                    val cleaned =
                        if (firstPart.contains("•")) {
                            firstPart.substringAfter("•").trim()
                        } else if (firstPart.contains("·")) {
                            firstPart.substringAfter("·").trim()
                        } else {
                            firstPart.replace(Regex("""^\d+%\s*"""), "").trim()
                        }
                    if (cleaned.isNotEmpty() && cleaned.length > 1) cleaned else firstPart
                } else if (info.isWireless) {
                    wirelessChargingStr
                } else {
                    chargingStr
                }
            } else if (info.isPowerSave) {
                batterySaverStr
            } else if (info.level <= 20) {
                lowBatteryStr
            } else {
                onBatteryStr
            }
        }

    val subtitleText =
        remember(
            parts,
            info.timeRemaining,
            info.isCharging,
            info.isWireless,
            wirelessStr,
            sourceStr,
            statusStr,
            normalStr,
        ) {
            when {
                parts.size >= 2 && parts[1].isNotEmpty() -> parts[1]
                !info.timeRemaining.isNullOrEmpty() -> info.timeRemaining
                info.isCharging && info.isWireless -> wirelessStr
                info.isCharging -> sourceStr
                else -> statusStr.takeIf { it != unavailable } ?: normalStr
            }
        }

    val labelSource = stringResource(R.string.ax_dynamic_bar_battery_source)
    val labelTemp = stringResource(R.string.ax_dynamic_bar_battery_temperature)
    val labelVolt = stringResource(R.string.ax_dynamic_bar_battery_voltage)
    val labelCurrent = stringResource(R.string.ax_dynamic_bar_battery_current)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .onSizeChanged { onContentSizeChanged?.invoke(it.width, it.height) }
                .padding(top = 18.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(soft.copy(alpha = colors.iconBgAlpha))
                        .border(
                            BorderStroke(0.5.dp, soft.copy(alpha = colors.iconBorderAlpha)),
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (info.isCharging) {
                    AnimatedChargingBoltIcon(color = accent, iconSize = 24.dp)
                } else {
                    BatteryIndicatorIcon(color = accent, level = info.level)
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // Cung native fading-edge marquee voi Media pill: chi mo hai mep chu.
                NativeMarqueeText(
                    text = chargingTitle,
                    color = colors.content,
                    textSize = 16.sp,
                    fontWeight = FontWeight.SemiBold.weight,
                    fadeLength = SpaceMd,
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (subtitleText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    NativeMarqueeText(
                        text = subtitleText,
                        color = colors.secondary,
                        textSize = 12.sp,
                        fontWeight = FontWeight.Normal.weight,
                        fadeLength = SpaceMd,
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Text(
                text = "${info.level}%",
                style =
                    PillMono.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        platformStyle =
                            androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    ),
                color = colors.content,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Energy bar (% level)
        val fillProgress = (info.level / 100f).coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.trackBackground),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(fraction = fillProgress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(soft.copy(alpha = 0.70f), soft)),
                        ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BatteryMetricItem(
                label = labelSource,
                value = sourceStr,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            BatteryMetricItem(
                label = labelTemp,
                value = tempStr,
                colors = colors,
                valueColor = tempColor,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BatteryMetricItem(
                label = labelVolt,
                value = voltStr,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            BatteryMetricItem(
                label = labelCurrent,
                value = currentStr,
                colors = colors,
                valueColor = currentColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BatteryMetricItem(
    label: String,
    value: String,
    colors: BatteryColors,
    modifier: Modifier = Modifier,
    valueColor: Color = colors.content,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBackground)
                .border(BorderStroke(0.5.dp, colors.cardBorder), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = PillPrimary.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                color = colors.secondary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style =
                    PillMono.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        platformStyle =
                            androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    ),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BatteryIndicatorIcon(color: Color, level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 12.dp)) {
        val w = size.width
        val h = size.height
        val strokeW = 1.5.dp.toPx()
        val corner = 2.5.dp.toPx()

        val bodyW = w - 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(bodyW, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            style = Stroke(width = strokeW),
        )

        val nubW = 2.dp.toPx()
        val nubH = h * 0.45f
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyW + 1.dp.toPx(), (h - nubH) / 2f),
            size = Size(nubW, nubH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )

        val pad = strokeW + 1.5.dp.toPx()
        val fillMaxW = (bodyW - pad * 2f).coerceAtLeast(0f)
        val fillH = (h - pad * 2f).coerceAtLeast(0f)
        val fillW = fillMaxW * (level / 100f).coerceIn(0f, 1f)
        if (fillW > 0f && fillH > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(pad, pad),
                size = Size(fillW, fillH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }
    }
}

/**
 * Noi dung pill pin — nen do sharedGlass/morph host cung cap (khong blur long).
 * Envelope cao = MusicChipHeight (58dp); widthIn truoc fillMaxWidth.
 */
@Composable
internal fun KeyguardBatteryPillContent(
    info: KeyguardBatteryInfo,
    keyguardBatteryChipMode: Int,
    batteryString: String,
    contentAlpha: Float = 1f,
) {
    if (keyguardBatteryChipMode <= 0) return
    if (keyguardBatteryChipMode == 1 && !info.isCharging) return

    val colors = rememberKeyguardBatteryColors()
    val accent = colors.accent(info.isCharging, info.isPowerSave, info.level)
    val soft = colors.softAccent(info.isCharging, info.isPowerSave, info.level)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(58.dp).widthIn(min = 130.dp, max = 180.dp),
    ) {
        if (info.isCharging) {
            val wirelessChargingStr = stringResource(R.string.ax_dynamic_bar_wireless_charging)
            val chargingStr = stringResource(R.string.ax_dynamic_bar_charging)
            val wirelessStr = stringResource(R.string.ax_dynamic_bar_wireless)
            val parts = rememberChargingParts(batteryString)
            val chargingTitle =
                remember(parts, info.isWireless, wirelessChargingStr, chargingStr) {
                    if (parts.isNotEmpty()) {
                        val firstPart = parts[0]
                        val cleaned =
                            if (firstPart.contains("•")) {
                                firstPart.substringAfter("•").trim()
                            } else if (firstPart.contains("·")) {
                                firstPart.substringAfter("·").trim()
                            } else {
                                firstPart.replace(Regex("""^\d+%\s*"""), "").trim()
                            }
                        if (cleaned.isNotEmpty() && cleaned.length > 1) cleaned else firstPart
                    } else if (info.isWireless) {
                        wirelessChargingStr
                    } else {
                        chargingStr
                    }
                }
            val subtitleText =
                remember(parts, info.timeRemaining, info.isWireless, wirelessStr) {
                    when {
                        parts.size >= 2 && parts[1].isNotEmpty() -> parts[1]
                        !info.timeRemaining.isNullOrEmpty() -> info.timeRemaining
                        info.isWireless -> wirelessStr
                        else -> null
                    }
                }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(28.dp)
                            .clip(CircleShape)
                            .background(soft.copy(alpha = colors.iconBgAlpha))
                            .border(
                                BorderStroke(0.5.dp, soft.copy(alpha = colors.iconBorderAlpha)),
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedChargingBoltIcon(color = accent, iconSize = 16.dp)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Native fade o hai dau chi tren vung text; khong lam mo icon/%.
                    NativeMarqueeText(
                        text = chargingTitle,
                        color = colors.content.copy(alpha = contentAlpha),
                        textSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold.weight,
                        fadeLength = SpaceMd,
                        gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!subtitleText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        NativeMarqueeText(
                            text = subtitleText,
                            color = colors.secondary.copy(alpha = contentAlpha),
                            textSize = 11.sp,
                            fontWeight = FontWeight.Normal.weight,
                            fadeLength = SpaceMd,
                            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Text(
                    text = "${info.level}%",
                    style =
                        PillMono.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle =
                                androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false,
                                ),
                        ),
                    color = colors.content.copy(alpha = contentAlpha),
                    maxLines = 1,
                )
            }
        } else {
            val fillFraction by
                animateFloatAsState(
                    targetValue = (info.level / 100f).coerceIn(0f, 1f),
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "kg_pill_battery_fill",
                )
            val detailText =
                when {
                    !info.timeRemaining.isNullOrEmpty() -> info.timeRemaining
                    info.isPowerSave -> stringResource(R.string.ax_dynamic_bar_battery_saver)
                    info.level <= 20 -> stringResource(R.string.ax_dynamic_bar_low_battery)
                    else -> null
                }
            // Clip theo toan bo pill truoc, roi moi gioi han fill theo % —
            // tranh hinh chu nhat mau vuong troi goc trai capsule.
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(percent = 50)),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(fraction = fillFraction)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            soft.copy(alpha = colors.fillAlphaStart),
                                            soft.copy(alpha = colors.fillAlphaEnd),
                                        ),
                                ),
                            ),
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${info.level}%",
                    style =
                        PillMono.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            platformStyle =
                                androidx.compose.ui.text.PlatformTextStyle(
                                    includeFontPadding = false,
                                ),
                        ),
                    color = colors.content.copy(alpha = contentAlpha),
                    maxLines = 1,
                )
                if (!detailText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = detailText,
                        style =
                            PillPrimary.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Medium),
                        color = colors.secondary.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
