/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.hinting;

import static android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS;
import static android.content.pm.ActivityInfo.CONFIG_UI_MODE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.app.StatusBarManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.WindowInsets;
import android.view.WindowInsets.Type;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.launcher3.R;

/**
 * Coordinates the visible app hints for the current drag.
 */
public class AppHintingLayout extends LinearLayout {

    private static final String TAG = "AppHintingLayout";

    // While dragging the status bar is hidden.
    private static final int HIDE_STATUS_BAR_FLAGS = StatusBarManager.DISABLE_NOTIFICATION_ICONS
            | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
            | StatusBarManager.DISABLE_CLOCK
            | StatusBarManager.DISABLE_SYSTEM_INFO;

    private final Configuration mLastConfiguration = new Configuration();
    private final WindowManager mWindowManager;

    private AppHintingView mDropZoneView1;
    private AppHintingView mDropZoneView2;

    private int mDisplayMargin;
    private Insets mInsets = Insets.NONE;

    private boolean mIsShowing;

    private RectF mLeftScreenBound;
    private RectF mRightScreenBound;

    private Context mContext;

    public enum AppHintResult {
        LEFT,
        RIGHT,
        NONE
    };

    private AppHintResult mAppHintResult = AppHintResult.NONE;

    public AppHintingLayout(Context context) {
        this(context, null);
    }

    public AppHintingLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppHintingLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppHintingLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);

        mLastConfiguration.setTo(context.getResources().getConfiguration());

        mDisplayMargin = context.getResources().getDimensionPixelSize(
                R.dimen.drop_layout_display_margin);

        // Always use LTR because we assume dropZoneView1 is on the left and 2 is on the right when
        // showing the highlight.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        mDropZoneView1 = new AppHintingView(context);
        mDropZoneView2 = new AppHintingView(context);
        addView(mDropZoneView1, new LinearLayout.LayoutParams(MATCH_PARENT,
                MATCH_PARENT));
        addView(mDropZoneView2, new LinearLayout.LayoutParams(MATCH_PARENT,
                MATCH_PARENT));
        ((LayoutParams) mDropZoneView1.getLayoutParams()).weight = 1;
        ((LayoutParams) mDropZoneView2.getLayoutParams()).weight = 1;
        int orientation = getResources().getConfiguration().orientation;
        setOrientation(orientation == Configuration.ORIENTATION_LANDSCAPE
                ? LinearLayout.HORIZONTAL
                : LinearLayout.VERTICAL);
        updateContainerMargins(getResources().getConfiguration().orientation);

        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(dm);

        RectF displayBound = new RectF(0, 0, dm.widthPixels, dm.heightPixels);
        float verticalMargin = displayBound.bottom * 0.1f;
        float horizontalMargin = displayBound.right * 0.4f;
        mLeftScreenBound = new RectF(0, verticalMargin, horizontalMargin, displayBound.bottom - verticalMargin);
        mRightScreenBound = new RectF(displayBound.right - horizontalMargin, verticalMargin, displayBound.right, displayBound.bottom - verticalMargin);

        mDropZoneView1.setShowingMargin(false);
        mDropZoneView2.setShowingMargin(false);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        mInsets = insets.getInsets(Type.tappableElement() | Type.displayCutout());

        final int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mDropZoneView1.setBottomInset(mInsets.bottom);
            mDropZoneView2.setBottomInset(mInsets.bottom);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mDropZoneView1.setBottomInset(0);
            mDropZoneView2.setBottomInset(mInsets.bottom);
        }
        return super.onApplyWindowInsets(insets);
    }

    public void onConfigChanged(Configuration newConfig) {
        Slog.d(TAG, "Configuration changed " + newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                && getOrientation() != HORIZONTAL) {
            Slog.d(TAG, "Config changed, setting horizontal layout");
            logWithToast("AppHintingLayout: config changed, horizontal layout");
            setOrientation(LinearLayout.HORIZONTAL);
            updateContainerMargins(newConfig.orientation);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
                && getOrientation() != VERTICAL) {
            logWithToast("AppHintingLayout: config changed, vertical layout");
            Slog.d(TAG, "Config changed, setting vertical layout");
            setOrientation(LinearLayout.VERTICAL);
            updateContainerMargins(newConfig.orientation);
        }

        final int diff = newConfig.diff(mLastConfiguration);
        final boolean themeChanged = (diff & CONFIG_ASSETS_PATHS) != 0
                || (diff & CONFIG_UI_MODE) != 0;
        if (themeChanged) {
            mDropZoneView1.onThemeChange();
            mDropZoneView2.onThemeChange();
        }
        mLastConfiguration.setTo(newConfig);
    }

    private void logWithToast(String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }

    private void updateContainerMargins(int orientation) {
        final float halfMargin = mDisplayMargin / 2f;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mDropZoneView1.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, halfMargin, mDisplayMargin);
            mDropZoneView2.setContainerMargin(
                    halfMargin, mDisplayMargin, mDisplayMargin, mDisplayMargin);
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            mDropZoneView1.setContainerMargin(
                    mDisplayMargin, mDisplayMargin, mDisplayMargin, halfMargin);
            mDropZoneView2.setContainerMargin(
                    mDisplayMargin, halfMargin, mDisplayMargin, mDisplayMargin);
        }
    }

    public AppHintResult getHintingResult() {
        return mAppHintResult;
    }

    public void show() {
        mIsShowing = true;
    }

    public void hide() {
        mIsShowing = false;

        mDropZoneView1.setShowingMargin(false);
        mDropZoneView2.setShowingMargin(false);
    }

    public void update(RectF taskRect) {
        PointF center = new PointF(taskRect.centerX(), taskRect.centerY());

        if (!mIsShowing) {
            mAppHintResult = AppHintResult.NONE;
            return;
        }

        if (mLeftScreenBound.contains(center.x, center.y)) {
            mDropZoneView1.setShowingMargin(true);
            mDropZoneView2.setShowingMargin(false);
            mAppHintResult = AppHintResult.LEFT;
        } else if (mRightScreenBound.contains(center.x, center.y)) {
            mDropZoneView1.setShowingMargin(false);
            mDropZoneView2.setShowingMargin(true);
            mAppHintResult = AppHintResult.RIGHT;
        } else {
            mDropZoneView1.setShowingMargin(false);
            mDropZoneView2.setShowingMargin(false);
            mAppHintResult = AppHintResult.NONE;
        }
    }
}
