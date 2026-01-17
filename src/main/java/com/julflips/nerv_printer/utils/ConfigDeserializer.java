package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public final class ConfigDeserializer {

    private static BlockPos jsonToBlockPos(JsonObject obj) {
        return new BlockPos(
            obj.get("x").getAsInt(),
            obj.get("y").getAsInt(),
            obj.get("z").getAsInt()
        );
    }

    private static Vec3d jsonToVec3d(JsonObject obj) {
        return new Vec3d(
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble()
        );
    }

    private static Pair<BlockPos, Vec3d> jsonToBlockPosVecPair(JsonObject obj) {
        BlockPos pos = jsonToBlockPos(obj.getAsJsonObject("blockPos"));
        Vec3d openPos = jsonToVec3d(obj.getAsJsonObject("openPos"));
        return new Pair<>(pos, openPos);
    }

    /**
     * Data container for config values
     */
    public static class ConfigData {
        public String type;
        public Pair<BlockPos, Vec3d> reset;
        public Pair<BlockPos, Vec3d> cartographyTable;
        public Pair<BlockPos, Vec3d> finishedMapChest;
        public ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
        public Pair<Vec3d, Pair<Float, Float>> dumpStation;
        public BlockPos mapCorner;
        public HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict;
    }

    public static ConfigData readFromJson(Path file) throws IOException {
        Gson gson = new Gson();

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            ConfigData data = new ConfigData();

            data.type = root.get("type").getAsString();

            data.reset = jsonToBlockPosVecPair(root.getAsJsonObject("reset"));
            data.cartographyTable = jsonToBlockPosVecPair(root.getAsJsonObject("cartographyTable"));
            data.finishedMapChest = jsonToBlockPosVecPair(root.getAsJsonObject("finishedMapChest"));

            data.mapMaterialChests = new ArrayList<>();
            JsonArray materialChestArray = root.getAsJsonArray("mapMaterialChests");
            for (JsonElement element : materialChestArray) {
                data.mapMaterialChests.add(
                    jsonToBlockPosVecPair(element.getAsJsonObject())
                );
            }

            JsonObject dumpStationObj = root.getAsJsonObject("dumpStation");
            Vec3d dumpPos = jsonToVec3d(dumpStationObj.getAsJsonObject("pos"));
            float yaw = dumpStationObj.get("yaw").getAsFloat();
            float pitch = dumpStationObj.get("pitch").getAsFloat();
            data.dumpStation = new Pair<>(dumpPos, new Pair<>(yaw, pitch));

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));

            data.materialDict = new HashMap<>();
            JsonObject materialDictObj = root.getAsJsonObject("materialDict");

            for (String key : materialDictObj.keySet()) {
                Identifier id = Identifier.of(key);
                Item item = Registries.ITEM.get(id);

                ArrayList<Pair<BlockPos, Vec3d>> chestList = new ArrayList<>();
                JsonArray chestArray = materialDictObj.getAsJsonArray(key);

                for (JsonElement element : chestArray) {
                    chestList.add(
                        jsonToBlockPosVecPair(element.getAsJsonObject())
                    );
                }

                data.materialDict.put(item, chestList);
            }

            return data;
        }
    }
}
