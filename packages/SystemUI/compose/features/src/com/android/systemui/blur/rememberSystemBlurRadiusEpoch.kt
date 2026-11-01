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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Epoch tang khi keo system_blur_radius_pct — dung lam key recompose mau frost. */
@Composable
fun rememberSystemBlurRadiusEpoch(): Int {
    val context = LocalContext.current
    var epoch by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observer = AxSystemFrost.registerRadiusObserver(context) { epoch++ }
        onDispose { AxSystemFrost.unregisterRadiusObserver(context, observer) }
    }
    return epoch
}
