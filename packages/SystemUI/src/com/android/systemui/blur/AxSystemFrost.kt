/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.blur

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import com.android.axion.blur.AxBlurColors
import com.android.axion.blur.AxBlurConfig
import kotlin.math.roundToInt

/**
 * Cong thuc frost he thong giong PrimaryCard / islandBlur:
 * radius tu [AxBlurConfig] (`system_blur_radius_pct`), tint/fallback tu
 * `ax_blur_surface_bright_expressive(_tint)`.
 */
object AxSystemFrost {
    /** Max radius (truoc khi nhan %) — dung cho BlurConfig / shade window blur. */
    @JvmStatic
    fun maxBlurRadiusPx(context: Context): Float = AxBlurConfig.getMaxBlurRadiusPx(context)

    /** Radius thuc te sau % — dung cho BackgroundBlurDrawable. */
    @JvmStatic
    fun blurRadiusPx(context: Context): Int =
        AxBlurConfig.getSystemBlurRadiusPx(context).roundToInt().coerceAtLeast(0)

    /** Overlay tint khi blur bat (AxBlurSurfaceDefaults.tintColor). */
    @JvmStatic
    fun tintColor(context: Context): Int = AxBlurColors.surfaceBrightTint(context)

    /**
     * Fallback khi blur tat — khop PrimaryCard [CardBg] =
     * MaterialTheme.colorScheme.surfaceBright (materialColorSurfaceBright).
     */
    @JvmStatic
    fun fallbackColor(context: Context): Int =
        context.getColor(com.android.internal.R.color.materialColorSurfaceBright)

    /**
     * Frost thuc su dang ve — BlurEngine yeu cau blurRadiusPx > 0; strength 0% = tat frost.
     */
    @JvmStatic
    fun isFrostActive(context: Context): Boolean = blurRadiusPx(context) > 0

    /**
     * Gate ve kinh: he thong cho phep blur VA strength cho radius > 0.
     * Dung chung cho affordance / notif / shelf — tranh ghost blur khi 0%.
     */
    @JvmStatic
    fun isFrostRenderable(context: Context, blurSupported: Boolean): Boolean =
        blurSupported && isFrostActive(context)

    /**
     * Radius ap dung len BackgroundBlurDrawable: strength/gate off → 0 (khong gui region).
     */
    @JvmStatic
    fun appliedBlurRadiusPx(context: Context, blurSupported: Boolean): Int =
        if (isFrostRenderable(context, blurSupported)) blurRadiusPx(context) else 0

    /**
     * Mau be mat: tint kinh khi window blur bat VA radius > 0; else CardBg fallback.
     */
    @JvmStatic
    fun surfaceColor(context: Context, blurSupported: Boolean): Int =
        if (isFrostRenderable(context, blurSupported)) tintColor(context) else fallbackColor(context)

    /**
     * Theo doi thanh strength (`system_blur_radius_pct`) — goi [onChanged] tren main
     * khi keo slider (PrimaryCard/AxBlur da subscribe rieng).
     */
    @JvmStatic
    fun registerRadiusObserver(context: Context, onChanged: Runnable): ContentObserver {
        val main = Handler(Looper.getMainLooper())
        val observer =
            object : ContentObserver(main) {
                override fun onChange(selfChange: Boolean) {
                    onChanged.run()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(AxBlurConfig.KEY_SYSTEM_BLUR_RADIUS_PCT),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        return observer
    }

    @JvmStatic
    fun unregisterRadiusObserver(context: Context, observer: ContentObserver?) {
        if (observer == null) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
            // ignore
        }
    }

    /**
     * FLAG_BLUR_BEHIND + radius theo strength (PrimaryCard). Strength 0% / blur tat →
     * khong blur, dim day hon.
     *
     * @return true neu dang bat blur behind
     */
    @JvmStatic
    @JvmOverloads
    fun applyWindowBlurBehind(
        window: Window,
        blurSupported: Boolean,
        dimWhenBlur: Float = 0.54f,
        dimWhenSolid: Float = 0.88f,
    ): Boolean {
        val attrs = window.attributes
        val active = blurSupported && isFrostActive(window.context)
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.blurBehindRadius = blurRadiusPx(window.context)
            window.attributes = attrs
            window.setDimAmount(dimWhenBlur)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.blurBehindRadius = 0
            window.attributes = attrs
            window.setDimAmount(dimWhenSolid)
        }
        return active
    }
}
