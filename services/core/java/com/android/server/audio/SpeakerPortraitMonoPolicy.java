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

package com.android.server.audio;

/**
 * Preference va goc man hinh. Loa trong duoc loc o AudioPolicyManager.
 */
public final class SpeakerPortraitMonoPolicy {
    public static final int ROTATION_UNKNOWN = Integer.MIN_VALUE;

    private SpeakerPortraitMonoPolicy() {}

    // Surface.ROTATION_*: 0, 1, 2, 3. Gia tri la khong biet -> khong giu goc cu.
    public static int degreesFromSurfaceRotation(int surfaceRotation) {
        switch (surfaceRotation) {
            case 0:
                return 0;
            case 1:
                return 90;
            case 2:
                return 180;
            case 3:
                return 270;
            default:
                return ROTATION_UNKNOWN;
        }
    }

    public static boolean isPortrait(int rotationDegrees) {
        return rotationDegrees == 0 || rotationDegrees == 180;
    }

    public static boolean isRequested(boolean preferenceEnabled, int rotationDegrees) {
        return preferenceEnabled && isPortrait(rotationDegrees);
    }
}
