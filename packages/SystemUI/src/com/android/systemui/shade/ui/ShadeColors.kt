/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.shade.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.UserHandle
import android.provider.Settings
import com.android.internal.graphics.ColorUtils
import com.android.systemui.blur.AxSystemFrost

object ShadeColors {
    /**
     * Calculate notification shade panel color.
     *
     * @param context Context to resolve colors.
     * @param blurSupported Whether blur is enabled (can be off due to battery saver)
     * @param withScrim Whether to composite a scrim when blur is enabled (used by legacy shade).
     * @return color for the shade panel.
     */
    @JvmStatic
    fun shadePanel(context: Context, blurSupported: Boolean, withScrim: Boolean): Int {
        // Frost chi khi radius > 0; strength 0% = CardBg dac (giong PrimaryCard).
        if (!blurSupported || !AxSystemFrost.isFrostActive(context)) {
            return shadePanelFallback(context)
        }
        val frost = AxSystemFrost.tintColor(context)
        return if (withScrim) {
            ColorUtils.compositeColors(frost, shadePanelScrimBehind(context))
        } else {
            frost
        }
    }

    @JvmStatic
    fun notificationScrim(context: Context, blurSupported: Boolean): Int {
        return AxSystemFrost.surfaceColor(context, blurSupported)
    }

    @JvmStatic
    fun shadePanelScrimBehind(context: Context): Int {
        return context.resources.getColor(
            com.android.internal.R.color.shade_panel_scrim,
            context.theme,
        )
    }

    /**
     * Background color of the rounded cornered rect behind Notifications in Single- or SplitShade.
     */
    @JvmStatic
    fun classicShadeNotificationScrimBg(
        context: Context,
        blurSupported: Boolean,
        composited: Boolean,
    ): Int {
        if (!blurSupported || !AxSystemFrost.isFrostActive(context)) {
            return AxSystemFrost.fallbackColor(context)
        }
        val frostTint = AxSystemFrost.tintColor(context)
        if (!composited) {
            // SplitShade (avoid adding the shade panel with scrim)
            return frostTint
        }
        // SingleShade
        val shadePanelWithScrim = shadePanel(context, blurSupported, withScrim = true)
        return ColorUtils.compositeColors(frostTint, shadePanelWithScrim)
    }

    @JvmStatic
    private fun shadePanelFallback(context: Context): Int {
        val isNightMode = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val useBlackTheme = Settings.Secure.getIntForUser(
            context.contentResolver, "berry_black_theme", 0, UserHandle.USER_CURRENT
        ) == 1
        return if (useBlackTheme && isNightMode) {
            Color.BLACK
        } else {
            // Gan CardBg / surfaceBright (AxBlur fallback).
            AxSystemFrost.fallbackColor(context)
        }
    }
}
