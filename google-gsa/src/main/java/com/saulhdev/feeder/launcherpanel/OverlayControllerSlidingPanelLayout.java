package com.saulhdev.feeder.launcherpanel;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;

@SuppressLint("ViewConstructor")
public final class OverlayControllerSlidingPanelLayout extends SlidingPanelLayout {

    private final OverlayController overlayController;

    public OverlayControllerSlidingPanelLayout(OverlayController overlayControllerVar) {
        super(overlayControllerVar);
        this.overlayController = overlayControllerVar;
    }

    @Override
    public void determineScrollStart(MotionEvent motionEvent, float velocity) {
        int pointerIndex = motionEvent.findPointerIndex(this.activePointerId);
        if (pointerIndex == -1) return;

        float deltaX = motionEvent.getX() - this.downX;
        float deltaY = motionEvent.getY() - this.downY;
        float absX = Math.abs(deltaX);
        float absY = Math.abs(deltaY);

        if (Float.compare(absX, 0f) == 0) return;

        float angle = (float) Math.atan(absY / absX);

        boolean isDraggingInCorrectDirection = isRtl ? deltaX < 0f : deltaX > 0f;
        boolean canScroll = !isPanelOpen || isPageMoving;

        if (!canScroll && isDraggingInCorrectDirection) return;

        boolean allowIntercept = !canScroll && panelController.canInterceptTouchEvents();

        // Umbral de ángulos para iniciar scroll
        if (allowIntercept || angle > Math.toRadians(60)) return;

        if (angle > Math.toRadians(30)) {
            float normalizedAngle = (angle - (float) Math.toRadians(30)) / (float) Math.toRadians(30);
            float adjustedVelocity = (float) Math.sqrt(normalizedAngle) * 4f + 1f;
            super.determineScrollStart(motionEvent, adjustedVelocity);
        } else {
            super.determineScrollStart(motionEvent, velocity);
        }
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        return !overlayController.isVisible || super.fitSystemWindows(insets);
    }
}
