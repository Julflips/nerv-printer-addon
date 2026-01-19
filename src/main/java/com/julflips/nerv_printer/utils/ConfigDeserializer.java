package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import java.util.HashSet;
import java.util.Set;

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
        public Pair<BlockPos, Vec3d> usedToolChest;
        public ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
        public Pair<Vec3d, Pair<Float, Float>> dumpStation;
        public BlockPos mapCorner;
        public HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict;
        public Set<ItemStack> toolSet;
    }

    private static JsonObject getObj(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject()
            ? root.getAsJsonObject(key)
            : null;
    }

    public static ConfigData readFromJson(Path file) throws IOException {
        Gson gson = new Gson();

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            ConfigData data = new ConfigData();

            data.type = root.get("type").getAsString();

            JsonObject obj;
            obj = getObj(root, "reset");
            data.reset = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "cartographyTable");
            data.cartographyTable = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "finishedMapChest");
            data.finishedMapChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "usedToolChest");
            data.usedToolChest = obj != null ? jsonToBlockPosVecPair(obj) : null;

            data.mapMaterialChests = new ArrayList<>();
            if (root.has("mapMaterialChests")) {
                for (JsonElement e : root.getAsJsonArray("mapMaterialChests")) {
                    data.mapMaterialChests.add(
                        jsonToBlockPosVecPair(e.getAsJsonObject())
                    );
                }
            }

            if (root.has("dumpStation")) {
                JsonObject dump = root.getAsJsonObject("dumpStation");

                Vec3d pos = jsonToVec3d(dump.getAsJsonObject("pos"));
                float yaw = dump.get("yaw").getAsFloat();
                float pitch = dump.get("pitch").getAsFloat();

                data.dumpStation = new Pair<>(pos, new Pair<>(yaw, pitch));
            } else {
                data.dumpStation = null;
            }

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));

            data.materialDict = new HashMap<>();
            if (root.has("materialDict")) {
                JsonObject materialDictObj = root.getAsJsonObject("materialDict");
                for (String key : materialDictObj.keySet()) {
                    Identifier id = Identifier.of(key);
                    Item item = Registries.ITEM.get(id);
                    ArrayList<Pair<BlockPos, Vec3d>> list = new ArrayList<>();
                    for (JsonElement e : materialDictObj.getAsJsonArray(key)) {
                        list.add(jsonToBlockPosVecPair(e.getAsJsonObject()));
                    }
                    data.materialDict.put(item, list);
                }
            }

            data.toolSet = new HashSet<>();
            if (root.has("toolSet")) {
                for (JsonElement e : root.getAsJsonArray("toolSet")) {
                    JsonObject o = e.getAsJsonObject();
                    Identifier id = Identifier.of(o.get("item").getAsString());
                    data.toolSet.add(
                        new ItemStack(Registries.ITEM.get(id))
                    );
                }
            }

            return data;
        }
    }
}
