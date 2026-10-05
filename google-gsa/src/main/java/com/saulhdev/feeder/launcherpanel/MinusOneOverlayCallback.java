package com.saulhdev.feeder.launcherpanel;

import android.content.res.Configuration;
import android.os.Message;

import androidx.annotation.NonNull;

import java.io.PrintWriter;

public final class MinusOneOverlayCallback extends OverlayControllerCallback {

    private final OverlaysController overlaysController;

    public MinusOneOverlayCallback(OverlaysController overlaysController, OverlayControllerBinder overlayControllerBinder) {
        super(overlayControllerBinder, 3);
        this.overlaysController = overlaysController;
    }

    @Override
    public OverlayController createController(Configuration configuration) {
        return overlaysController.createController(
                configuration,
                binder.getServerVersion(),
                binder.getClientVersion()
        );
    }

    @Override
    public void dump(PrintWriter printWriter, @NonNull String prefix) {
        printWriter.println(prefix + "MinusOneOverlayCallback");
        super.dump(printWriter, prefix);
    }

    @Override
    public boolean handleMessage(Message message) {
        if (super.handleMessage(message)) {
            return true;
        }

        if (overlayController == null) {
            return false;
        }

        final OverlayController controller = overlayController;
        final long timestamp = message.getWhen();

        return switch (message.what) {
            case 3 -> {
                if (!controller.isOpen()) {
                    SlidingPanelLayout panel = controller.slidingPanelLayout;
                    if (panel.panelOffsetPx < panel.touchSlop) {
                        panel.updatePanelOffset(0);
                        controller.acceptExternalMove = true;
                        controller.touchStartX = 0;
                        panel.forceDrag = true;
                        controller.lastTouchTime = timestamp - 30;
                        controller.simulateMotionEvent(0, controller.touchStartX, controller.lastTouchTime);
                        controller.simulateMotionEvent(2, controller.touchStartX, timestamp);
                    }
                }
                yield true;
            }

            case 4 -> {
                if (controller.acceptExternalMove && message.obj instanceof Float floatValue) {
                    controller.touchStartX = (int) (floatValue * controller.slidingPanelLayout.getMeasuredWidth());
                    controller.simulateMotionEvent(2, controller.touchStartX, timestamp);
                }
                yield true;
            }

            case 5 -> {
                if (controller.acceptExternalMove) {
                    controller.simulateMotionEvent(1, controller.touchStartX, timestamp);
                }
                controller.acceptExternalMove = false;
                yield true;
            }

            default -> false;
        };
    }
}
