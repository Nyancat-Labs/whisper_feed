package com.saulhdev.feeder.launcherpanel;

public interface PanelController {
    void setPanelPosition(float position);

    void onPanelDragged();

    void startPanelDrag();

    void openPanel();

    void closePanel();

    boolean canInterceptTouchEvents();

    void setPanelEnabled(boolean enabled);
}
