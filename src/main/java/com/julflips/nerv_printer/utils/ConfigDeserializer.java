package com.julflips.nerv_printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
        public Pair<BlockPos, Vec3d> bed;
        public ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
        public Pair<Vec3d, Pair<Float, Float>> dumpStation;
        public ArrayList<Pair<Vec3d, Pair<Float, Float>>> dumpStations;
        public BlockPos mapCorner;
        public BlockPos upperMapCorner;
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
            obj = getObj(root, "bed");
            data.bed = obj != null ? jsonToBlockPosVecPair(obj) : null;

            data.mapMaterialChests = new ArrayList<>();
            if (root.has("mapMaterialChests")) {
                for (JsonElement e : root.getAsJsonArray("mapMaterialChests")) {
                    data.mapMaterialChests.add(
                        jsonToBlockPosVecPair(e.getAsJsonObject())
                    );
                }
            }

            data.dumpStations = new ArrayList<>();
            if (root.has("dumpStations")) {
                for (JsonElement e : root.getAsJsonArray("dumpStations")) {
                    JsonObject dump = e.getAsJsonObject();

                    Vec3d pos = jsonToVec3d(dump.getAsJsonObject("pos"));
                    float yaw = dump.get("yaw").getAsFloat();
                    float pitch = dump.get("pitch").getAsFloat();
                    data.dumpStations.add(new Pair<>(pos, new Pair<>(yaw, pitch)));
                }
            } else {
                data.dumpStation = null;
                data.dumpStations = null;
            }
            if (!data.dumpStations.isEmpty()) {
                data.dumpStation = data.dumpStations.get(0);
            }

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));
            data.upperMapCorner = jsonToBlockPos(root.getAsJsonObject("upperMapCorner"));

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

            if (net.minecraft.client.MinecraftClient.getInstance() == null
                || net.minecraft.client.MinecraftClient.getInstance().world == null ) return data;

            data.toolSet = new HashSet<>();
            RegistryWrapper.WrapperLookup registries =
                net.minecraft.client.MinecraftClient.getInstance().world.getRegistryManager();
            if (root.has("toolSet") && root.get("toolSet").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("toolSet")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("item")) continue;
                    ItemStack stack = jsonToItemStack(o, registries);
                    if (!stack.isEmpty()) {
                        data.toolSet.add(stack);
                    }
                }
            }

            return data;
        }
    }

    private static ItemStack jsonToItemStack(JsonObject obj, RegistryWrapper.WrapperLookup registries) {
        Identifier itemId = Identifier.of(obj.get("item").getAsString());
        Item item = Registries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item);
        // Backward compatibility: older configs may not contain enchantments
        if (!obj.has("enchantments") || !obj.get("enchantments").isJsonArray()) {
            return stack;
        }
        ItemEnchantmentsComponent.Builder builder =
            new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        // Get the dynamic enchantment registry using our passed-in wrapper lookup
        RegistryWrapper.Impl<Enchantment> enchantmentRegistry =
            registries.getOrThrow(RegistryKeys.ENCHANTMENT);
        for (JsonElement enchantmentElement : obj.getAsJsonArray("enchantments")) {
            if (!enchantmentElement.isJsonObject()) continue;
            JsonObject enchantmentObj = enchantmentElement.getAsJsonObject();
            if (!enchantmentObj.has("id") || !enchantmentObj.has("level")) continue;
            Identifier enchantmentId = Identifier.of(enchantmentObj.get("id").getAsString());
            int level = enchantmentObj.get("level").getAsInt();
            if (level <= 0) continue;
            RegistryKey<Enchantment> enchantKey = RegistryKey.of(RegistryKeys.ENCHANTMENT, enchantmentId);
            Optional<RegistryEntry.Reference<Enchantment>> enchantmentEntry =
                enchantmentRegistry.getOptional(enchantKey);
            enchantmentEntry.ifPresent(entry -> builder.add(entry, level));
        }
        stack.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        return stack;
    }
}
