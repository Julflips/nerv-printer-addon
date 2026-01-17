package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.mixininterfaces.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class Utils {

    private static int nextInteractID = 2;

    public static int getNextInteractID() {
        return nextInteractID;
    }

    public static ArrayList<Pair<BlockPos, Vec3d>> saveAdd(ArrayList<Pair<BlockPos, Vec3d>> list, BlockPos blockPos, Vec3d openPos) {
        for (Pair<BlockPos, Vec3d> pair : list) {
            if (pair.getLeft().equals(blockPos)) {
                list.remove(pair);
                break;
            }
        }
        list.add(new Pair(blockPos, openPos));
        return list;
    }

    public static int stacksRequired(Collection<Integer> amounts) {
        //Calculates how many slots are required for the set of item amounts
        int stacks = 0;
        for (int amount : amounts) {
            if (amount == 0) continue;
            stacks += Math.ceil((float) amount / 64f);
        }
        return stacks;
    }

    public static ArrayList<Integer> getAvailableSlots(HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materials) {
        ArrayList<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) {
                slots.add(slot);
                continue;
            }
            Item item = mc.player.getInventory().getStack(slot).getItem();
            if (materials.containsKey(item)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public static HashMap<Item, Integer> getRequiredItems(BlockPos mapCorner, Pair<Integer, Integer> interval, int linesPerRun, int availableSlotsSize, Block[][] map) {
        //Calculate the next items to restock
        //Iterate over map. Player has to be able to see the complete map area
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        boolean isStartSide = true;
        for (int x = interval.getLeft(); x <= interval.getRight(); x += linesPerRun) {
            for (int z = 0; z < 128; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun; lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (adjustedX > interval.getRight()) break;
                    int adjustedZ = z;
                    if (!isStartSide) adjustedZ = 127 - z;
                    BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.add(adjustedX, 0, adjustedZ));
                    if (blockState.isAir() && map[adjustedX][adjustedZ] != null) {
                        //ChatUtils.info("Add material for: " + mapCorner.add(x + lineBonus, 0, adjustedZ).toShortString());
                        Item material = map[adjustedX][adjustedZ].asItem();
                        if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                        requiredItems.put(material, requiredItems.get(material) + 1);
                        //Check if the item fits into inventory. If not, undo the last increment and return
                        if (stacksRequired(requiredItems.values()) > availableSlotsSize) {
                            requiredItems.put(material, requiredItems.get(material) - 1);
                            return requiredItems;
                        }
                    }
                }
            }
            isStartSide = !isStartSide;
        }
        return requiredItems;
    }

    public static Pair<ArrayList<Integer>, HashMap<Item, Integer>> getInvInformation(HashMap<Item, Integer> requiredItems, ArrayList<Integer> availableSlots) {
        //Return a list of slots to be dumped and a Hashmap of material-amount we can keep in the inventory
        ArrayList<Integer> dumpSlots = new ArrayList<>();
        HashMap<Item, Integer> materialInInv = new HashMap<>();
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Item item = mc.player.getInventory().getStack(slot).getItem();
            if (requiredItems.containsKey(item)) {
                int requiredAmount = requiredItems.get(item);
                int requiredModulusAmount = (requiredAmount - (requiredAmount / 64) * 64);
                if (requiredModulusAmount == 0) requiredModulusAmount = 64;
                int stackAmount = mc.player.getInventory().getStack(slot).getCount();
                // ChatUtils.info(material.getName().getString() + " | Required: " + requiredModulusAmount + " | Inv: " + stackAmount);
                if (requiredAmount > 0 && requiredModulusAmount <= stackAmount) {
                    int oldEntry = requiredItems.remove(item);
                    requiredItems.put(item, Math.max(0, oldEntry - stackAmount));
                    if (materialInInv.containsKey(item)) {
                        oldEntry = materialInInv.remove(item);
                        materialInInv.put(item, oldEntry + stackAmount);
                    } else {
                        materialInInv.put(item, stackAmount);
                    }
                    continue;
                }
            }
            dumpSlots.add(slot);
        }
        return new Pair(dumpSlots, materialInInv);
    }

    public static File getMinecraftDirectory() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    public static boolean createFolders(File mapFolder) {
        File finishedMapFolder = new File(mapFolder.getAbsolutePath() + File.separator + "_finished_maps");
        File configFolder = new File(mapFolder.getAbsolutePath() + File.separator + "_configs");
        if (!mapFolder.exists()) {
            if (mapFolder.mkdir()) {
                ChatUtils.info("Created nerv-printer folder in the Minecraft directory.");
            } else {
                ChatUtils.warning("Failed to create nerv-printer folder in the Minecraft directory. Try to enable customFolderPath and enter a path.");
                return false;
            }
        }
        if (!finishedMapFolder.exists()) {
            if (!finishedMapFolder.mkdir()) {
                ChatUtils.warning("Failed to create finished-map folder in the nerv-printer folder");
                return false;
            }
        }
        if (!configFolder.exists()) {
            if (!configFolder.mkdir()) {
                ChatUtils.warning("Failed to create config folder in the nerv-printer folder");
                return false;
            }
        }
        return true;
    }

    public static int getIntervalStart(int pos) {
        //Get top left corner of the map area for one dimension
        return (int) Math.floor((float) (pos + 64) / 128f) * 128 - 64;
    }

    public static void setForwardPressed(boolean pressed) {
        mc.options.forwardKey.setPressed(pressed);
        Input.setKeyState(mc.options.forwardKey, pressed);
    }

    public static void setBackwardPressed(boolean pressed) {
        mc.options.backKey.setPressed(pressed);
        Input.setKeyState(mc.options.backKey, pressed);
    }

    public static void setJumpPressed(boolean pressed) {
        mc.options.jumpKey.setPressed(pressed);
        Input.setKeyState(mc.options.jumpKey, pressed);
    }

    public static int findHighestFreeSlot(InventoryS2CPacket packet) {
        for (int i = packet.contents().size() - 1; i > packet.contents().size() - 1 - 36; i--) {
            ItemStack stack = packet.contents().get(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void swapIntoHotbar(int slot, ArrayList<Integer> hotBarSlots) {
        HashMap<Item, Integer> itemFrequency = new HashMap<>();
        HashMap<Item, Integer> itemSlot = new HashMap<>();
        int targetSlot = hotBarSlots.get(0);

        //Search the most frequent item in the hotbar
        for (int i : hotBarSlots) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                Item item = mc.player.getInventory().getStack(i).getItem();
                if (!itemFrequency.containsKey(item)) {
                    itemFrequency.put(item, 1);
                    itemSlot.put(item, i);
                } else {
                    itemFrequency.put(item, itemFrequency.get(item) + 1);
                }
            }
        }
        int topFrequency = 0;
        ArrayList<Item> topFrequencyItems = new ArrayList<>();
        for (Item item : itemFrequency.keySet()) {
            if (itemFrequency.get(item) > topFrequency) {
                topFrequency = itemFrequency.get(item);
                topFrequencyItems = new ArrayList<>(Collections.singletonList(item));
            } else if (itemFrequency.get(item) == topFrequency) {
                topFrequencyItems.add(item);
            }
        }
        if (!topFrequencyItems.isEmpty()) {
            Random random = new Random();
            Item item = topFrequencyItems.get(random.nextInt(topFrequencyItems.size()));
            targetSlot = itemSlot.get(item);
        }

        //Prefer emtpy slots
        for (int i : hotBarSlots) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                targetSlot = i;
            }
        }

        //info("Swapping " + slot + " into " + targetSlot);
        mc.player.getInventory().setSelectedSlot(targetSlot);

        IClientPlayerInteractionManager cim = (IClientPlayerInteractionManager) mc.interactionManager;
        cim.clickSlot(mc.player.currentScreenHandler.syncId, slot, targetSlot, SlotActionType.SWAP, mc.player);
        //mc.getNetworkHandler().sendPacket(new ClickSlotC2SPacket(0, slot, targetSlot, 0, SlotActionType.SWAP, new ItemStack(Items.AIR), Int2ObjectMaps.emptyMap()));
    }

    public static void iterateBlocks(BlockPos startingPos, int horizontalRadius, int verticalRadius, BiConsumer<BlockPos, BlockState> function) {
        int px = startingPos.getX();
        int py = startingPos.getY();
        int pz = startingPos.getZ();

        BlockPos.Mutable blockPos = new BlockPos.Mutable();

        int hRadius = Math.max(0, horizontalRadius);
        int vRadius = Math.max(0, verticalRadius);

        for (int x = px - hRadius; x <= px + hRadius; x++) {
            for (int z = pz - hRadius; z <= pz + hRadius; z++) {
                for (int y = py - vRadius; y <= py + vRadius; y++) {
                    blockPos.set(x, y, z);
                    BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                    function.accept(blockPos, blockState);
                }
            }
        }

    }

    public static HashMap<Integer, Pair<Block, Integer>> getBlockPalette(NbtList paletteList) {
        HashMap<Integer, Pair<Block, Integer>> blockPaletteDict = new HashMap<>();
        for (int i = 0; i < paletteList.size(); i++) {
            Optional<NbtCompound> block = paletteList.getCompound(i);
            if (block.isEmpty()) continue;

            Optional<String> blockName = block.get().getString("Name");
            if (blockName.isEmpty()) continue;

            blockPaletteDict.put(i, new Pair(Registries.BLOCK.get(Identifier.of(blockName.get())), 0));
        }
        return blockPaletteDict;
    }

    public static Block[][] generateMapArray(NbtList blockList, HashMap<Integer, Pair<Block, Integer>> blockPalette) {
        //Calculating the map offset
        int maxHeight = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < blockList.size(); i++) {
            Optional<NbtCompound> blockOpt = blockList.getCompound(i);
            if (blockOpt.isEmpty()) continue;

            NbtCompound block = blockOpt.get();

            Optional<Integer> blockIdOpt = block.getInt("state");
            if (blockIdOpt.isEmpty() || !blockPalette.containsKey(blockIdOpt.get())) continue;

            Optional<NbtList> posOpt = block.getList("pos");
            if (posOpt.isEmpty()) continue;

            NbtList pos = posOpt.get();
            if (pos.size() < 3) continue;

            Optional<Integer> xOpt = pos.getInt(0);
            Optional<Integer> yOpt = pos.getInt(1);
            Optional<Integer> zOpt = pos.getInt(2);
            if (xOpt.isEmpty() || yOpt.isEmpty() || zOpt.isEmpty()) {
                continue;
            }

            if (yOpt.get() > maxHeight) maxHeight = yOpt.get();
            if (xOpt.get() < minX) minX = xOpt.get();
            if (zOpt.get() > maxZ) maxZ = zOpt.get();
        }
        maxZ -= 127;

        //Extracting the map block positions
        Block[][] map = new Block[128][128];
        for (int i = 0; i < blockList.size(); i++) {
            Optional<NbtCompound> blockOpt = blockList.getCompound(i);
            if (blockOpt.isEmpty()) continue;
            NbtCompound block = blockOpt.get();

            Optional<Integer> blockIdOpt = block.getInt("state");
            if (blockIdOpt.isEmpty() || !blockPalette.containsKey(blockIdOpt.get())) continue;

            Optional<NbtList> posOpt = block.getList("pos");
            if (posOpt.isEmpty()) continue;

            NbtList pos = posOpt.get();
            if (pos.size() < 3) continue;

            Optional<Integer> xOpt = pos.getInt(0);
            Optional<Integer> yOpt = pos.getInt(1);
            Optional<Integer> zOpt = pos.getInt(2);

            if (xOpt.isEmpty() || yOpt.isEmpty() || zOpt.isEmpty()) {
                continue;
            }

            // Center the nbt
            int x = xOpt.get() - minX;
            int y = yOpt.get();
            int z = zOpt.get() - maxZ;

            // If block is within map area, increase counter for the block ID
            if (y == maxHeight && x < map.length && z < map.length & x >= 0 && z >= 0) {
                int blockId = blockIdOpt.get();
                map[x][z] = blockPalette.get(blockId).getLeft();
                blockPalette.put(blockId, new Pair(blockPalette.get(blockId).getLeft(), blockPalette.get(blockId).getRight() + 1));
            }
        }

        //Remove unused blocks from the blockPalette
        ArrayList<Integer> toBeRemoved = new ArrayList<>();
        for (int key : blockPalette.keySet()) {
            if (blockPalette.get(key).getRight() == 0) toBeRemoved.add(key);
        }
        for (int key : toBeRemoved) blockPalette.remove(key);

        return map;
    }

    public static ArrayList<BlockPos> getInvalidPlacements(BlockPos mapCorner, Pair<Integer, Integer> interval, Block[][] map, ArrayList<BlockPos> knownErrors) {
        ArrayList<BlockPos> invalidPlacements = new ArrayList<>();
        for (int x = interval.getRight(); x >= interval.getLeft(); x--) {
            for (int z = 127; z >= 0; z--) {
                BlockPos relativePos = new BlockPos(x, 0, z);
                BlockPos absolutePos = mapCorner.add(relativePos);
                if (knownErrors.contains(absolutePos)) continue;
                BlockState blockState = MapAreaCache.getCachedBlockState(absolutePos);
                Block block = blockState.getBlock();
                if (!blockState.isAir()) {
                    if (map[x][z] != block) invalidPlacements.add(absolutePos);
                }
            }
        }
        return invalidPlacements;
    }

    public static void getOneItem(int sourceSlot, boolean avoidFirstHotBar, ArrayList<Integer> availableSlots,
                                  ArrayList<Integer> availableHotBarSlots, InventoryS2CPacket packet) {
        int targetSlot = availableHotBarSlots.get(0);
        if (avoidFirstHotBar) {
            targetSlot = availableSlots.get(0);
            if (availableSlots.get(0) == availableHotBarSlots.get(0)) {
                targetSlot = availableSlots.get(1);
            }
        }
        if (targetSlot < 9) {
            targetSlot += 27;
        } else {
            targetSlot -= 9;
        }
        targetSlot = packet.contents().size() - 36 + targetSlot;
        mc.interactionManager.clickSlot(packet.syncId(), sourceSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(packet.syncId(), targetSlot, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(packet.syncId(), sourceSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    public static File getNextMapFile(File mapFolder, ArrayList<File> startedFiles, boolean areMoved) {
        File[] files = mapFolder.listFiles();
        if (files == null) return null;
        Arrays.sort(files, Comparator
            .comparingInt((File f) -> f.getName().length()) // sort by name length
            .thenComparing(File::getName));                // then sort alphabetically

        for (File file : files) {
            // Only check if file was used if not moving to finished folder
            // since they are not in the map folder in that case
            if ((!startedFiles.contains(file) || areMoved) &&
                file.isFile() && file.getName().toLowerCase().endsWith(".nbt")) {
                startedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    public static Direction getInteractionSide(BlockPos blockPos) {
        double minDistance = Double.MAX_VALUE;
        Direction bestSide = Direction.UP;
        for (Direction side : Direction.values()) {
            double neighbourDistance = mc.player.getEyePos().distanceTo(blockPos.offset(side).toCenterPos());
            if (neighbourDistance < minDistance) {
                minDistance = neighbourDistance;
                bestSide = side;
            }
        }
        return bestSide;
    }

    public static boolean isInInterval(Pair<Integer, Integer> interval, int number) {
        return number >= interval.getLeft() && number <= interval.getRight();
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        nextInteractID = 2;
    }

    @EventHandler(priority = EventPriority.HIGHEST - 1)
    private static void onRecievePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerInteractItemC2SPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }

        if (event.packet instanceof PlayerInteractBlockC2SPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }
    }
}
