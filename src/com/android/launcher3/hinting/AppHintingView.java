/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.wm.shell.animation.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.internal.policy.ScreenDecorationsUtils;

/**
 * Renders a app hinting area for splitting app.
 */
public class AppHintingView extends FrameLayout {

    private static final float SPLASHSCREEN_ALPHA = 0.00f;
    private static final float HIGHLIGHT_ALPHA = 0.9f;
    private static final int MARGIN_ANIMATION_ENTER_DURATION = 400;
    private static final int MARGIN_ANIMATION_EXIT_DURATION = 250;

    private static final FloatProperty<AppHintingView> INSETS =
            new FloatProperty<AppHintingView>("insets") {
                @Override
                public void setValue(AppHintingView v, float percent) {
                    v.setMarginPercent(percent);
                }

                @Override
                public Float get(AppHintingView v) {
                    return v.getMarginPercent();
                }
            };

    private final Path mPath = new Path();
    private final float[] mContainerMargin = new float[4];
    private float mCornerRadius;
    private float mBottomInset;

    private boolean mShowingHighlight;
    private boolean mShowingSplash;
    private boolean mShowingMargin;

    private int mHighlightColor;

    private ObjectAnimator mBackgroundAnimator;
    private ObjectAnimator mMarginAnimator;
    private float mMarginPercent;

    // Renders a highlight or neutral transparent color
    private ColorDrawable mColorDrawable;

    private HighlightView mHighlightView;

    public AppHintingView(Context context) {
        this(context, null);
    }

    public AppHintingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppHintingView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AppHintingView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setContainerMargin(0, 0, 0, 0); // make sure it's populated

        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(context);
        int c = getResources().getColor(android.R.color.system_accent1_500);
        mHighlightColor =  Color.argb(HIGHLIGHT_ALPHA, Color.red(c), Color.green(c), Color.blue(c));
        mColorDrawable = new ColorDrawable();
        animateBackground(mColorDrawable.getColor(), mHighlightColor);

        mHighlightView = new HighlightView(context);
        addView(mHighlightView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

    }

    public void onThemeChange() {
        mCornerRadius = ScreenDecorationsUtils.getWindowCornerRadius(getContext());
        mHighlightColor = getResources().getColor(android.R.color.system_accent1_500);

        if (mMarginPercent > 0) {
            mHighlightView.invalidate();
        }
    }

    /** Sets the desired margins around the drop zone container when fully showing. */
    public void setContainerMargin(float left, float top, float right, float bottom) {
        mContainerMargin[0] = left;
        mContainerMargin[1] = top;
        mContainerMargin[2] = right;
        mContainerMargin[3] = bottom;
        if (mMarginPercent > 0) {
            mHighlightView.invalidate();
        }
    }

    /** Sets the bottom inset so the drop zones are above bottom navigation. */
    public void setBottomInset(float bottom) {
        mBottomInset = bottom;
        if (mMarginPercent > 0) {
            mHighlightView.invalidate();
        }
    }

    /** @return an active animator for this view if one exists. */
    @Nullable
    public Animator getAnimator() {
        if (mMarginAnimator != null && mMarginAnimator.isRunning()) {
            return mMarginAnimator;
        } else if (mBackgroundAnimator != null && mBackgroundAnimator.isRunning()) {
            return mBackgroundAnimator;
        }
        return null;
    }

    /** Animates the margins around the drop zone to show or hide. */
    public void setShowingMargin(boolean visible) {
        if (mShowingMargin != visible) {
            mShowingMargin = visible;
            animateMarginToState();
        }
        if (!mShowingMargin) {
            mShowingHighlight = false;
            mShowingSplash = false;
            animateBackground(mColorDrawable.getColor(), Color.TRANSPARENT);
            mHighlightView.setVisibility(View.GONE);
        } else {
            mHighlightView.setVisibility(View.VISIBLE);
        }
    }

    private void animateBackground(int startColor, int endColor) {
        if (mBackgroundAnimator != null) {
            mBackgroundAnimator.cancel();
        }
        mBackgroundAnimator = ObjectAnimator.ofArgb(mColorDrawable,
                "color",
                startColor,
                endColor);
        if (!mShowingSplash && !mShowingHighlight) {
            mBackgroundAnimator.setInterpolator(FAST_OUT_SLOW_IN);
        }
        mBackgroundAnimator.start();
    }

    private void animateMarginToState() {
        if (mMarginAnimator != null) {
            mMarginAnimator.cancel();
        }
        mMarginAnimator = ObjectAnimator.ofFloat(this, INSETS,
                mMarginPercent,
                mShowingMargin ? 1f : 0f);
        mMarginAnimator.setInterpolator(FAST_OUT_SLOW_IN);
        mMarginAnimator.setDuration(mShowingMargin
                ? MARGIN_ANIMATION_ENTER_DURATION
                : MARGIN_ANIMATION_EXIT_DURATION);
        mMarginAnimator.start();
    }

    private void setMarginPercent(float percent) {
        if (percent != mMarginPercent) {
            mMarginPercent = percent;
            mHighlightView.invalidate();
        }
    }

    private float getMarginPercent() {
        return mMarginPercent;
    }

    /** Simple view that draws a rounded rect margin around its contents. **/
    private class HighlightView extends View {

        HighlightView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mPath.reset();
            mPath.addRoundRect(mContainerMargin[0] * mMarginPercent,
                    mContainerMargin[1] * mMarginPercent,
                    getWidth() - (mContainerMargin[2] * mMarginPercent),
                    getHeight() - (mContainerMargin[3] * mMarginPercent)
                            - mBottomInset,
                    mCornerRadius * mMarginPercent,
                    mCornerRadius * mMarginPercent,
                    Path.Direction.CW);
            mPath.setFillType(Path.FillType.EVEN_ODD);
            canvas.clipPath(mPath);
            canvas.drawColor(mHighlightColor);
        }
    }
}
