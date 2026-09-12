package com.julflips.nerv_printer.utils;

import net.minecraft.util.Pair;

import java.util.Arrays;
import java.util.Objects;

public final class MapCompletionState {
    private static boolean[] lowerLayer;
    private static boolean[] upperLayer;

    public static synchronized void initializeLayers(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Layer size must be greater than 0.");
        }
        upperLayer = new boolean[size];
        lowerLayer = new boolean[size];
    }

    public static synchronized void setInterval(int startInclusive, int endInclusive, boolean value) {
        setInterval(startInclusive, endInclusive, value, false);
    }

    public static synchronized void setInterval(int startInclusive, int endInclusive, boolean value, boolean isUpperLayer) {
        boolean[] layer = getLayer(isUpperLayer);
        if (startInclusive > endInclusive) {
            throw new IllegalArgumentException("startInclusive must be <= endInclusive.");
        }
        if (startInclusive < 0 || endInclusive >= layer.length) {
            throw new IndexOutOfBoundsException("Interval [" + startInclusive + ", " + endInclusive + "] is outside layer bounds 0.." + (layer.length - 1) + ".");
        }
        Arrays.fill(layer, startInclusive, endInclusive + 1, value);
    }

    public static synchronized boolean isComplete(boolean targetValue) {
        if (lowerLayer == null && upperLayer == null) {
            return false;
        }
        return isLayerComplete(lowerLayer, targetValue) && isLayerComplete(upperLayer, targetValue);
    }

    private static boolean isLayerComplete(boolean[] layer, boolean targetValue) {
        for (boolean value : layer) {
            if (value != targetValue) {
                return false;
            }
        }
        return true;
    }

    private static boolean[] getLayer(boolean isUpperLayer) {
        boolean[] layer = isUpperLayer ? upperLayer : lowerLayer;
        if (layer == null) {
            throw new IllegalStateException((isUpperLayer ? "Upper" : "Lower") + " layer has not been added yet.");
        }
        return layer;
    }
}
