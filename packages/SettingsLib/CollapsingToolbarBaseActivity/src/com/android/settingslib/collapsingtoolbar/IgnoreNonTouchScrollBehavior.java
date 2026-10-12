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

package com.android.settingslib.collapsingtoolbar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.AppBarLayout;

/**
 * AppBar behavior: ignore non-touch nested pre-scroll (keyboard flicker + fling
 * mid-list expand), but still expand when fling reaches the top (dyUnconsumed &lt; 0).
 */
public class IgnoreNonTouchScrollBehavior extends AppBarLayout.Behavior {

    public IgnoreNonTouchScrollBehavior() {
        super();
    }

    public IgnoreNonTouchScrollBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onStartNestedScroll(
            @NonNull CoordinatorLayout parent,
            @NonNull AppBarLayout child,
            @NonNull View directTargetChild,
            @NonNull View target,
            int nestedScrollAxes,
            int type) {
        // Can ca NON_TOUCH de nhan onNestedScroll khi fling cham dinh list
        return super.onStartNestedScroll(
                parent, child, directTargetChild, target, nestedScrollAxes, type);
    }

    @Override
    public void onNestedPreScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull AppBarLayout child,
            @NonNull View target,
            int dx,
            int dy,
            @NonNull int[] consumed,
            int type) {
        // Chi TOUCH: tranh fling giua list expand AppBar som (de len tieu de)
        // va tranh collapse tu scroll ban phim / focus
        if (type != ViewCompat.TYPE_TOUCH) {
            return;
        }
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type);
    }

    @Override
    public void onNestedScroll(
            @NonNull CoordinatorLayout coordinatorLayout,
            @NonNull AppBarLayout child,
            @NonNull View target,
            int dxConsumed,
            int dyConsumed,
            int dxUnconsumed,
            int dyUnconsumed,
            int type,
            @NonNull int[] consumed) {
        // Expand khi con dy chua tieu thu huong len (list da o top), ca fling
        if (type == ViewCompat.TYPE_TOUCH || dyUnconsumed < 0) {
            super.onNestedScroll(
                    coordinatorLayout,
                    child,
                    target,
                    dxConsumed,
                    dyConsumed,
                    dxUnconsumed,
                    dyUnconsumed,
                    type,
                    consumed);
        }
    }
}
