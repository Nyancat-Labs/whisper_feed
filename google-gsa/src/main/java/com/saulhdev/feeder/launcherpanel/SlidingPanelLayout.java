package com.saulhdev.feeder.launcherpanel;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.util.Property;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

public class SlidingPanelLayout extends FrameLayout {

    private static final boolean ENABLE_ALPHA = false;
    private static final boolean USE_HARDWARE_LAYER = false;

    public static final Property<SlidingPanelLayout, Integer> PANEL_X = new SlidingPanelLayoutProperty(Integer.class, "panelX");

    public float downX, downY;
    public float lastMotionX, totalMotionX;
    private float initialPanelOffset, panelOffset;
    public int activePointerId = -1;
    private VelocityTracker velocityTracker;

    public boolean isPanelOpen = false;
    public boolean isPageMoving = false;
    public boolean forceDrag = false;
    public boolean settling = false;
    public float panelPositionRatio;
    private final float density;
    private final int flingThresholdVelocity;
    private final int minFlingVelocity;
    private final int minSnapVelocity;
    public final int touchSlop;
    private final int maxVelocity;
    public final boolean isRtl;

    public View foregroundPanel;
    private View backgroundPanel;
    public int panelOffsetPx;

    private SlidingPanelLayoutInterpolator panelInterpolator;
    private final DecelerateInterpolator alphaInterpolator = new DecelerateInterpolator(3.0f);

    public PanelController panelController;

    public int touchState = 0;

    public SlidingPanelLayout(Context context) {
        this(context, null);
    }

    public SlidingPanelLayout(Context context, AttributeSet attrs) {
        super(context);
        final ViewConfiguration config = ViewConfiguration.get(context);
        touchSlop = config.getScaledPagingTouchSlop();
        maxVelocity = config.getScaledMaximumFlingVelocity();
        density = getResources().getDisplayMetrics().density;
        flingThresholdVelocity = (int) (300 * density);
        minFlingVelocity = (int) (150 * density);
        minSnapVelocity = (int) (1000 * density);
        panelInterpolator = new SlidingPanelLayoutInterpolator(this);
        isRtl = isRtl(getResources());
    }

    public void setForegroundPanel(View view) {
        this.foregroundPanel = view;
        super.addView(view);
    }

    public void setBackgroundPanel(View view) {
        if (this.backgroundPanel != null) {
            super.removeView(this.backgroundPanel);
        }
        this.backgroundPanel = view;
        super.addView(view, 0);
    }

    public static boolean isRtl(Resources resources) {
        return resources.getConfiguration().getLayoutDirection() == 1;
    }

    public void updatePanelOffset(int offsetPx) {
        if (offsetPx <= 1) offsetPx = 0;
        int width = getMeasuredWidth();
        panelOffsetPx = Math.max(0, Math.min(offsetPx, width));
        panelPositionRatio = (float) panelOffsetPx / width;

        if (foregroundPanel != null) {
            foregroundPanel.setTranslationX(isRtl ? -panelOffsetPx : panelOffsetPx);
            if (ENABLE_ALPHA) {
                foregroundPanel.setAlpha(Math.max(0.1f, alphaInterpolator.getInterpolation(panelPositionRatio)));
            }
        }

        if (panelController != null) {
            panelController.setPanelPosition(panelPositionRatio);
        }
    }

    public void startSettlingTo(int target, int duration) {
        startPanelDrag();
        settling = true;
        panelInterpolator.animateTo(target, duration);
    }

    public void closePanel(int duration) {
        isPageMoving = true;
        if (panelController != null) {
            panelController.setPanelEnabled(touchState == 1);
        }
        settling = true;
        panelInterpolator.animateTo(0, Math.min(duration, 300));
    }

    private void cnN() {
        touchState = 1;
        isPageMoving = true;
        settling = false;
        panelInterpolator.cancelAnimation();
        if (USE_HARDWARE_LAYER) setLayerType(LAYER_TYPE_HARDWARE, null);
        if (panelController != null) panelController.onPanelDragged();
    }

    private void releaseTouch() {
        releaseVelocityTracker();
        forceDrag = false;
        touchState = 0;
        activePointerId = -1;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        acquireVelocityTrackerAndAddMovement(ev);

        if (getChildCount() <= 0) {
            return super.onInterceptTouchEvent(ev);
        }
        int action = ev.getAction();
        if (action == 2 && this.touchState == 1) {
            return true;
        }

        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                boolean z;
                downX = ev.getX();
                downY = ev.getY();
                initialPanelOffset = panelOffsetPx;
                lastMotionX = downX;
                totalMotionX = 0;
                activePointerId = ev.getPointerId(0);
                action = Math.abs(panelInterpolator.finalX - panelOffsetPx);
                z = panelInterpolator.isFinished() || action < touchSlop / 3;
                if (!z || forceDrag) {
                    forceDrag = false;
                    cnN();
                    panelOffset = downX;
                    break;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (activePointerId != -1) {
                    determineScrollStart(ev, 1.0f);
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseTouch();
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                break;
        }
        return touchState != 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (foregroundPanel == null) return super.onTouchEvent(ev);

        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getActionMasked();
        switch (action& MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                initialPanelOffset = panelOffsetPx;
                lastMotionX = downX;
                totalMotionX = 0;
                activePointerId = ev.getPointerId(0);
                if (panelInterpolator.isFinished() || Math.abs(panelInterpolator.finalX - panelOffsetPx) < touchSlop / 3) {
                    if (!forceDrag) return true;
                }
                forceDrag = false;
                cnN();
                panelOffset = downX;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (touchState == 1) {
                    int pointerIndex = ev.findPointerIndex(activePointerId);
                    if (pointerIndex == -1) return true;
                    float x = ev.getX(pointerIndex);
                    totalMotionX += Math.abs(x - lastMotionX);
                    float delta = x - panelOffset;
                    lastMotionX = x;
                    updatePanelOffset((int) (initialPanelOffset + (isRtl ? -delta : delta)));
                    return true;
                }
                determineScrollStart(ev, 1.0f);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (touchState == 1) {
                    velocityTracker.computeCurrentVelocity(1000, maxVelocity);
                    int velocityX = (int) velocityTracker.getXVelocity(activePointerId);
                    if (isRtl) velocityX = -velocityX;

                    boolean isFling = totalMotionX > 25 && Math.abs(velocityX) > flingThresholdVelocity;
                    if (isFling) {
                        if (Math.abs(velocityX) < minFlingVelocity) {
                            if (velocityX >= 0) startSettlingTo(getMeasuredWidth(), 400);
                            else closePanel(400);
                        } else {
                            float projected = getMeasuredWidth() / 2f + (float) Math.sin((Math.min(1.0f, ((float) (velocityX < 0 ? panelOffsetPx : getMeasuredWidth() - panelOffsetPx)) / getMeasuredWidth()) - 0.5f) * 0.4712389) * getMeasuredWidth() / 2f;
                            int duration = Math.round(Math.abs(projected / Math.max(minSnapVelocity, Math.abs(velocityX))) * 1000f) * 4;
                            if (velocityX > 0) startSettlingTo(getMeasuredWidth(), duration);
                            else closePanel(duration);
                        }
                    } else {
                        if (panelOffsetPx >= getMeasuredWidth() / 2) startSettlingTo(getMeasuredWidth(), 400);
                        else closePanel(400);
                    }
                }
                releaseTouch();
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                releaseVelocityTracker();
                return true;

            default:
                return true;
        }
    }

    public void determineScrollStart(MotionEvent ev, float scaleFactor) {
        int index = ev.findPointerIndex(activePointerId);
        if (index != -1) {
            float x = ev.getX(index);
            if (Math.abs(x - downX) > Math.round(touchSlop * scaleFactor)) {
                totalMotionX += Math.abs(lastMotionX - x);
                panelOffset = x;
                lastMotionX = x;
                cnN();
            }
        }
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.clear();
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        int index = (ev.getAction() >> 8) & 0xFF;
        if (ev.getPointerId(index) == activePointerId) {
            int newIndex = index == 0 ? 1 : 0;
            float x = ev.getX(newIndex);
            panelOffset += x - lastMotionX;
            downX = x;
            lastMotionX = x;
            activePointerId = ev.getPointerId(newIndex);
            if (velocityTracker != null) velocityTracker.clear();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (backgroundPanel != null)
            backgroundPanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        if (foregroundPanel != null)
            foregroundPanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
        updatePanelOffset((int) (width * panelPositionRatio));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (backgroundPanel != null) {
            backgroundPanel.layout(0, 0, backgroundPanel.getMeasuredWidth(), backgroundPanel.getMeasuredHeight());
        }
        if (foregroundPanel != null) {
            int width = foregroundPanel.getMeasuredWidth();
            int height = foregroundPanel.getMeasuredHeight();
            int left = isRtl ? width : -width;
            int right = isRtl ? width * 2 : 0;
            foregroundPanel.layout(left, 0, right, height);
        }
    }

    public void startPanelDrag() {
        isPageMoving = true;
        if (panelController != null) panelController.startPanelDrag();
    }

    public void onPanelFullyOpened() {
        updateLayer();
        isPanelOpen = true;
        isPageMoving = false;
        if (panelController != null) panelController.openPanel();
    }

    final void updateLayer() {
        if (USE_HARDWARE_LAYER) {
            setLayerType(LAYER_TYPE_NONE, null);
        }
    }
}
