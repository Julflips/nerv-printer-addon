package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigSerializer {

    private static JsonObject blockPosToJson(BlockPos pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.getX());
        obj.addProperty("y", pos.getY());
        obj.addProperty("z", pos.getZ());
        return obj;
    }

    private static JsonObject vec3dToJson(Vec3d vec) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", vec.x);
        obj.addProperty("y", vec.y);
        obj.addProperty("z", vec.z);
        return obj;
    }

    private static JsonObject blockPosVecPairToJson(Pair<BlockPos, Vec3d> pair) {
        JsonObject obj = new JsonObject();
        obj.add("blockPos", blockPosToJson(pair.getLeft()));
        obj.add("openPos", vec3dToJson(pair.getRight()));
        return obj;
    }

    public static void writeToJson(
        Path file,
        String type,
        Pair<BlockPos, Vec3d> reset,
        Pair<BlockPos, Vec3d> cartographyTable,
        Pair<BlockPos, Vec3d> finishedMapChest,
        ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests,
        Pair<Vec3d, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict
    ) throws IOException {
        writeToJson(file, type, reset, cartographyTable, finishedMapChest, null,null,
            mapMaterialChests, dumpStation, mapCorner, materialDict, null);
    }

    public static void writeToJson (
        Path file,
        String type,
        Pair<BlockPos, Vec3d> cartographyTable,
        Pair<BlockPos, Vec3d> finishedMapChest,
        Pair<BlockPos, Vec3d> usedToolChest,
        Pair<BlockPos, Vec3d> bed,
        ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests,
        Pair<Vec3d, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict,
        Set<ItemStack> toolSet
    ) throws IOException {
        writeToJson(file, type, null, cartographyTable, finishedMapChest, usedToolChest, bed,
            mapMaterialChests, dumpStation, mapCorner, materialDict, toolSet);
    }

    public static void writeToJson(
        Path file,
        String type,
        Pair<BlockPos, Vec3d> reset,
        Pair<BlockPos, Vec3d> cartographyTable,
        Pair<BlockPos, Vec3d> finishedMapChest,
        Pair<BlockPos, Vec3d> usedToolChest,
        Pair<BlockPos, Vec3d> bed,
        ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests,
        Pair<Vec3d, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict,
        Set<ItemStack> toolSet
    ) throws IOException {
        JsonObject root = new JsonObject();

        root.addProperty("type", type);
        if (reset != null) root.add("reset", blockPosVecPairToJson(reset));
        if (cartographyTable != null) root.add("cartographyTable", blockPosVecPairToJson(cartographyTable));
        if (finishedMapChest != null) root.add("finishedMapChest", blockPosVecPairToJson(finishedMapChest));
        if (usedToolChest != null) root.add("usedToolChest", blockPosVecPairToJson(usedToolChest));
        if (bed != null) root.add("bed", blockPosVecPairToJson(bed));

        if (mapMaterialChests != null) {
            JsonArray materialChestsArray = new JsonArray();
            for (Pair<BlockPos, Vec3d> pair : mapMaterialChests) {
                materialChestsArray.add(blockPosVecPairToJson(pair));
            }
            root.add("mapMaterialChests", materialChestsArray);
        }

        if (dumpStation != null) {
            JsonObject dumpStationObj = new JsonObject();
            dumpStationObj.add("pos", vec3dToJson(dumpStation.getLeft()));
            dumpStationObj.addProperty("yaw", dumpStation.getRight().getLeft());
            dumpStationObj.addProperty("pitch", dumpStation.getRight().getRight());
            root.add("dumpStation", dumpStationObj);
        }

        if (mapCorner != null) root.add("mapCorner", blockPosToJson(mapCorner));

        if (materialDict != null) {
            JsonObject materialDictObj = new JsonObject();
            for (Map.Entry<Item, ArrayList<Pair<BlockPos, Vec3d>>> entry : materialDict.entrySet()) {
                String blockId = Registries.ITEM.getId(entry.getKey()).toString();

                JsonArray chestArray = new JsonArray();
                for (Pair<BlockPos, Vec3d> pair : entry.getValue()) {
                    chestArray.add(blockPosVecPairToJson(pair));
                }

                materialDictObj.add(blockId, chestArray);
            }
            root.add("materialDict", materialDictObj);
        }

        if (toolSet != null) {
            JsonArray toolSetArray = new JsonArray();
            for (ItemStack stack : toolSet) {
                JsonObject stackObj = new JsonObject();
                stackObj.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
                toolSetArray.add(stackObj);
            }
            root.add("toolSet", toolSetArray);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(root, writer);
        }
    }
}
