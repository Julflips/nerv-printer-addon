package com.julflips.nerv_printer.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.util.Arrays;

public final class MapCompletionState {
    private static boolean[][] lowerLayer;
    private static boolean[][] upperLayer;

    public static synchronized void initializeLayers() {
        upperLayer = new boolean[128][128];
        lowerLayer = new boolean[128][128];
    }

    public static synchronized void setInterval(int startX, int endX, boolean value, boolean isUpperLayer) {
        setInterval(startX, endX, 0, lowerLayer[0].length - 1, value, isUpperLayer);
    }

    public static synchronized void setInterval(int startX, int endX, int startZ, int endZ, boolean value, boolean isUpperLayer) {
        ChatUtils.info("Update MCS: " + startX + " : " + endX + " | " + startZ + " : " + endZ + " on "
                        + (isUpperLayer ? "upper" : "lower") + " with value: " + value);

        boolean[][] layer = getLayer(isUpperLayer);
        for (int x = startX; x <= endX; x++) {
            Arrays.fill(layer[x], startZ, endZ + 1, value);
        }
    }

    public static synchronized boolean isComplete(boolean targetValue) {
        if (lowerLayer == null && upperLayer == null) {
            return false;
        }
        return isLayerComplete(lowerLayer, targetValue) && isLayerComplete(upperLayer, targetValue);
    }

    public static boolean isLayerComplete(boolean[][] layer, boolean targetValue) {
        for (boolean[] row : layer) {
            for (boolean value : row) {
                if (value != targetValue) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean[][] getLayer(boolean isUpperLayer) {
        boolean[][] layer = isUpperLayer ? upperLayer : lowerLayer;
        if (layer == null) {
            throw new IllegalStateException((isUpperLayer ? "Upper" : "Lower") + " layer has not been added yet.");
        }
        return layer;
    }

    public static boolean isLineComplete(boolean isUpperLayer, int line, boolean targetValue) {
        return isLineComplete(isUpperLayer, line, 0, lowerLayer[0].length-1, targetValue);
    }

    public static boolean isLineComplete(boolean isUpperLayer, int line, int startZ, int endZ, boolean targetValue) {
        if (line < 0 || line > 63) return true;
        // Only for 2 wide lines
        boolean[][] layer = getLayer(isUpperLayer);
        for (int x = line*2; x <= line*2+1; x++) {
            for (int z = startZ; z <= endZ; z++) {
                if (layer[x][z] != targetValue) return false;
            }
        }
        return true;
    }
}
