package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
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

        JsonObject root = new JsonObject();

        root.addProperty("type", type);
        root.add("reset", blockPosVecPairToJson(reset));
        root.add("cartographyTable", blockPosVecPairToJson(cartographyTable));
        root.add("finishedMapChest", blockPosVecPairToJson(finishedMapChest));

        JsonArray materialChestsArray = new JsonArray();
        for (Pair<BlockPos, Vec3d> pair : mapMaterialChests) {
            materialChestsArray.add(blockPosVecPairToJson(pair));
        }
        root.add("mapMaterialChests", materialChestsArray);

        JsonObject dumpStationObj = new JsonObject();
        dumpStationObj.add("pos", vec3dToJson(dumpStation.getLeft()));
        dumpStationObj.addProperty("yaw", dumpStation.getRight().getLeft());
        dumpStationObj.addProperty("pitch", dumpStation.getRight().getRight());
        root.add("dumpStation", dumpStationObj);

        root.add("mapCorner", blockPosToJson(mapCorner));

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

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(root, writer);
        }
    }
}
