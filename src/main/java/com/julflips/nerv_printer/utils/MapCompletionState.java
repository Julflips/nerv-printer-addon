package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.Pair;

import java.util.Arrays;
import java.util.Objects;

public final class MapCompletionState {
    private static boolean[] lowerLayer;
    private static boolean[] upperLayer;

    public static synchronized void initializeLayers() {
        upperLayer = new boolean[128];
        lowerLayer = new boolean[128];
    }

    public static synchronized void setInterval(int startInclusive, int endInclusive, boolean value, boolean isUpperLayer) {
        ChatUtils.info("Update MCS: " + startInclusive + " to " + endInclusive + " on " + (isUpperLayer ? "upper" : "lower") + " with value: " + value);
        boolean[] layer = getLayer(isUpperLayer);
        Arrays.fill(layer, startInclusive, endInclusive + 1, value);
    }

    public static synchronized boolean isComplete(boolean targetValue) {
        if (lowerLayer == null && upperLayer == null) {
            return false;
        }
        return isLayerComplete(lowerLayer, targetValue) && isLayerComplete(upperLayer, targetValue);
    }

    public static boolean isLayerComplete(boolean[] layer, boolean targetValue) {
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

    public static boolean isLineComplete(boolean isUpperLayer, int line, boolean targetValue) {
        if (line < 0 || line > 63) return true;
        // Only for 2 wide lines
        boolean[] layer = getLayer(isUpperLayer);
        return layer[line*2] == targetValue && layer[line*2+1] == targetValue;
    }
}
