package com.saulhdev.feeder.launcherpanel;

import android.util.Property;
import com.saulhdev.feeder.launcherpanel.SlidingPanelLayout;
/**
 * A Property implementation for animating SlidingPanelLayout values.
 * This allows animating properties of SlidingPanelLayout using Android's property animation system.
 */
final class SlidingPanelLayoutProperty extends Property<SlidingPanelLayout, Integer> {

    /**
     * Constructs a new property for SlidingPanelLayout.
     *
     * @param type The class of the property (SlidingPanelLayout.class)
     * @param name The name of the property (used for debugging and identification)
     */
    SlidingPanelLayoutProperty(Class<Integer> type, String name) {
        super(type, name);
    }

    /**
     * Gets the current value of the property from the target SlidingPanelLayout.
     *
     * @param panel The SlidingPanelLayout from which to get the property value
     * @return The current value of the property
     */
    @Override
    public Integer get(SlidingPanelLayout panel) {
        return panel.panelOffsetPx;
    }

    /**
     * Sets the property value on the target SlidingPanelLayout.
     *
     * @param panel The SlidingPanelLayout on which to set the property value
     * @param position The new position value to set
     */
    @Override
    public void set(SlidingPanelLayout panel, Integer position) {
        panel.updatePanelOffset(position);
    }
}