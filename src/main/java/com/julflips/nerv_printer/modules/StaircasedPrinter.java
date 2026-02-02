package com.julflips.nerv_printer.modules;

import com.julflips.nerv_printer.Addon;
import com.julflips.nerv_printer.interfaces.MapPrinter;
import com.julflips.nerv_printer.utils.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StaircasedPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced",  false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range")
        .description("The maximum range you can place blocks around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("How many milliseconds to wait after placing.")
        .defaultValue(50)
        .min(1)
        .sliderRange(10, 300)
        .build()
    );

    private final Setting<Double> maxMiningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-mining-range")
        .description("The maximum range you can place blocks around yourself.")
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 2)
        .build()
    );

    private final Setting<List<Block>> startBlock = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-Block")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.Off)
        .build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Disable if the bot should continue after reconnecting to the server.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotatePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place")
        .description("Rotate when placing a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotateMine = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-place")
        .description("Rotate when mining a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customFolderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-folder-path")
        .description("Allows to set a custom path to the nbt folder.")
        .defaultValue(false)
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("nerv-printer-folder-path")
        .description("The path to your nerv-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\nerv-printer")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> customFolderPath.get())
        .onChanged((value) -> warnPathChanged())
        .build()
    );

    //Advanced

    private final Setting<Integer> preRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-restock-delay")
        .description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> invActionDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("inventory-action-delay")
        .description("How many ticks to wait between each inventory action (moving a stack).")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> postRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-restock-delay")
        .description("How many ticks to wait after restocking.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Integer> preSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-swap-delay")
        .description("How many ticks to wait before swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> postSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-swap-delay")
        .description("How many ticks to wait after swapping an item into the hotbar.")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> retryInteractTimer = sgAdvanced.add(new IntSetting.Builder()
        .name("retry-interact-timer")
        .description("How many ticks to wait for chest response before interacting with it again.")
        .defaultValue(80)
        .min(1)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<Integer> posResetTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("pos-reset-timeout")
        .description("How many ticks to wait after the player position was reset by the server.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Integer> jumpCoolDown = sgAdvanced.add(new IntSetting.Builder()
        .name("jump-cooldown")
        .description("How many ticks to wait after jumping before jumping again.")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> minDurabilityPercentage = sgAdvanced.add(new IntSetting.Builder()
        .name("min-durability")
        .description("How many ticks to wait after the player position was reset by the server.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 90)
        .build()
    );

    private final Setting<Boolean> snapToCheckpoints = sgAdvanced.add(new BoolSetting.Builder()
        .name("snap-to-checkpoints")
        .description("Snap to checkpoints when getting close.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgAdvanced.add(new BoolSetting.Builder()
        .name("move-to-finished-folder")
        .description("Moves finished NBT files into the finished-maps folder in the nerv-printer folder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnFinished = sgAdvanced.add(new BoolSetting.Builder()
        .name("disable-on-finished")
        .description("Disables the printer when all nbt files are finished.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> displayMaxRequirements = sgAdvanced.add(new BoolSetting.Builder()
        .name("print-max-requirements")
        .description("Print the maximum amount of material needed for all maps in the map-folder.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> debugPrints = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug-prints")
        .description("Prints additional information.")
        .defaultValue(false)
        .build()
    );

    //Multi User

    private final Setting<String> directMessageCommand = sgMultiUser.add(new StringSetting.Builder()
        .name("direct-message-command")
        .description("The command used to send direct messages between master and slaves.")
        .defaultValue("w")
        .onChanged((value) -> SlaveSystem.directMessageCommand = value)
        .build()
    );

    private final Setting<String> senderPrefix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-prefix")
        .description("The text that always comes before the name of sender of every direct message.")
        .defaultValue("")
        .onChanged((value) -> SlaveSystem.senderPrefix = value)
        .build()
    );

    private final Setting<String> senderSuffix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-suffix")
        .description("The text that is always between the name of the sender and the actual message.")
        .defaultValue(" whispers: ")
        .onChanged((value) -> SlaveSystem.senderSuffix = value)
        .build()
    );

    private final Setting<Integer> commandDelay = sgMultiUser.add(new IntSetting.Builder()
        .name("chat-message-delay")
        .description("How many ticks to wait between sending chat messages (for multi-user printing).")
        .defaultValue(50)
        .min(1)
        .sliderRange(1, 100)
        .onChanged((value) -> SlaveSystem.commandDelay = value)
        .build()
    );

    private final Setting<Integer> randomSuffix = sgMultiUser.add(new IntSetting.Builder()
        .name("random-suffix-length")
        .description("Generate a randomized suffix to circumvent anti-spam plugins.")
        .defaultValue(0)
        .min(0)
        .max(36)
        .sliderRange(0, 10)
        .onChanged((value) -> SlaveSystem.randomLength = value)
        .build()
    );

    //Error Handling

    private final Setting<Boolean> logErrors = sgError.add(new BoolSetting.Builder()
        .name("log-errors")
        .description("Prints warning when a misplacement is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ErrorAction> errorAction = sgError.add(new EnumSetting.Builder<ErrorAction>()
        .name("error-action")
        .description("What to do when a misplacement is detected.")
        .defaultValue(ErrorAction.Ignore)
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> renderMap = sgRender.add(new BoolSetting.Builder()
        .name("render-map")
        .description("Highlights the position of the map blocks.")
        .defaultValue(false)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderChestPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-chest-positions")
        .description("Highlights the selected chests.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderOpenPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-open-positions")
        .description("Indicate the position the bot will go to in order to interact with the chest.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderCheckpoints = sgRender.add(new BoolSetting.Builder()
        .name("render-checkpoints")
        .description("Indicate the checkpoints the bot will traverse.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Boolean> renderSpecialInteractions = sgRender.add(new BoolSetting.Builder()
        .name("render-special-interactions")
        .description("Indicate the position where the reset button and cartography table will be used.")
        .defaultValue(true)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<Double> indicatorSize = sgRender.add(new DoubleSetting.Builder()
        .name("indicator-size")
        .description("How big the rendered indicator will be.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .visible(() -> render.get())
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The render color.")
        .defaultValue(new SettingColor(22, 230, 206, 155))
        .visible(() -> render.get())
        .build()
    );

    int timeoutTicks;
    int jumpTimeout;
    int interactTimeout;
    int toBeSwappedSlot;
    int minedLines;
    long lastTickTime;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    State debugPreviousState;
    Pair<Integer, Integer> workingInterval;     //Interval the bot should work in 0-127
    Pair<Integer, Integer> trueInterval;        //Stores the actual interval in case the old one is temporarily overwritten while repairing
    Pair<BlockPos, Vec3d> usedToolChest;
    Pair<BlockPos, Vec3d> cartographyTable;
    Pair<BlockPos, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    Pair<Vec3d, Pair<Float, Float>> dumpStation;                    //Pos, Yaw, Pitch
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    BlockPos miningPos;
    Item lastSwappedMaterial;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;      //Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict; //Maps block to the chest pos and the open position
    Set<ItemStack> toolSet;                                       //Set of all registered tool item stacks
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Item, Integer, Integer>> restockList;//Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<BlockPos> knownErrors;
    Pair<Block, Integer>[][] map;
    File mapFolder;
    File mapFile;

    public StaircasedPrinter() {
        super(Addon.CATEGORY, "staircased-printer", "Automatically builds full-block maps with staircasing from nbt files.");
    }

    @Override
    public void onActivate() {
        lastTickTime = System.currentTimeMillis();
        if (!activationReset.get() && checkpoints != null) {
            return;
        }
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        toolSet = new HashSet<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        knownErrors = new ArrayList<>();
        usedToolChest = null;
        mapCorner = null;
        lastInteractedChest = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        jumpTimeout = 0;
        interactTimeout = 0;
        toBeSwappedSlot = -1;
        minedLines = 128;
        oldState = null;
        debugPreviousState = null;

        setInterval(new Pair<>(0, 127));
        // Initialize Slave System settings
        SlaveSystem.setupSlaveSystem(this, commandDelay.get(), directMessageCommand.get(), senderPrefix.get(), senderSuffix.get(), randomSuffix.get());

        if (!customFolderPath.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory(), "nerv-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createFolders(mapFolder)) {
            toggle();
            return;
        }

        if (displayMaxRequirements.get()) {
            HashMap<Block, Integer> materialCountDict = new HashMap<>();
            for (File file : mapFolder.listFiles()) {
                if (!file.isFile()) continue;
                if (!prepareNextMapFile()) return;
                for (Pair<Block, Integer> material : blockPaletteDict.values()) {
                    if (!materialCountDict.containsKey(material.getLeft())) {
                        materialCountDict.put(material.getLeft(), material.getRight());
                    } else {
                        materialCountDict.put(material.getLeft(), Math.max(materialCountDict.get(material.getLeft()), material.getRight()));
                    }
                }
            }
            info("§aMaterial needed for all files:");
            for (Block block : materialCountDict.keySet()) {
                float shulkerAmount = (float) Math.ceil((float) materialCountDict.get(block) / (float) (27 * 64) * 10) / (float) 10;
                if (shulkerAmount == 0) continue;
                info(block.getName().getString() + ": " + shulkerAmount + " shulker");
            }
            startedFiles.clear();
        }

        if (!prepareNextMapFile()) return;
        info("Building: §a" + mapFile.getName());
        info("Requirements: ");
        for (Pair<Block, Integer> p : blockPaletteDict.values()) {
            if (p.getRight() == 0) continue;
            info(p.getLeft().getName().getString() + ": " + p.getRight());
        }
        state = State.SelectingMapArea;
        info("Select the §aMap Building Area (128x128). (Right-click the edge from the inside)");
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM) {
            dumpStation = new Pair<>(mc.player.getPos(), new Pair<>(mc.player.getYaw(), mc.player.getPitch()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the §aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                MapAreaCache.reset(mapCorner);
                state = State.SelectingTable;
                info("Map Area selected. Select the §aCartography Table.");
                break;
            case SelectingTable:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(blockPos, mc.player.getPos());
                    info("Cartography Table selected. Throw an item into the §aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(blockPos, mc.player.getPos());
                    info("Finished Map Chest selected. Select the §aUsed Pickaxe Chest.");
                    state = State.SelectingUsedPickaxeChest;
                }
                break;
            case SelectingUsedPickaxeChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    usedToolChest = new Pair<>(blockPos, mc.player.getPos());
                    info("Used Pickaxe Chest selected. Select all §aMaterial-, Tool-, and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlock.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlock.get().contains(blockState.getBlock())) {
                    //Check if requirements to start building are met
                    if (materialDict.isEmpty()) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (toolSet.isEmpty()) {
                        warning("No Tool Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.isEmpty()) {
                        warning("No Map Chests selected!");
                        return;
                    }

                    startBuilding();
                }
                break;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == null) return;

        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) {
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
            }
        }

        if (!(event.packet instanceof InventoryS2CPacket packet)) return;

        if (state.equals(State.AwaitRegisterResponse)) {
            Item foundItem = null;
            ItemStack foundItemStack = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.contents().size() - 36; i++) {
                ItemStack stack = packet.contents().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    foundItemStack = stack;
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered §aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.getPos());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (ToolUtils.isTool(foundItemStack)) {
                toolSet.add(foundItemStack);
            }
            info("Registered item: §a" + foundItem.getName().getString());
            if (!materialDict.containsKey(foundItem)) materialDict.put(foundItem, new ArrayList<>());
            ArrayList<Pair<BlockPos, Vec3d>> oldList = materialDict.get(foundItem);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getPos());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
            return;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitUsedToolChestResponse);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
        }
    }

    private void handleInventoryPacket(InventoryS2CPacket packet) {
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                boolean foundMaterials = false;
                for (int i = 0; i < packet.contents().size() - 36; i++) {
                    ItemStack stack = packet.contents().get(i);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && (stack.getCount() == 64 || !stack.isStackable())) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(i);
                        Triple<Item, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                //Search for map and glass pane
                for (int slot = 0; slot < packet.contents().size() - 36; slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if (mapSlot == -1 || paneSlot == -1) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet);
                Utils.getOneItem(paneSlot, true, availableSlots, availableHotBarSlots, packet);
                mc.player.getInventory().setSelectedSlot(availableHotBarSlots.get(0));

                BlockPos centerBlockPos = mapCorner.add(map.length/2 - 1, map[map.length/2 - 1][map[0].length/2 - 1].getRight(), map[0].length/2 - 1);
                Vec3d center = centerBlockPos.toCenterPos().add(0, 0.5, 0);
                Vec3d centerEdge = mapCorner.add(map.length/2 - 1, 0, -1).toCenterPos().add(0, 0.5, 0);
                checkpoints.add(new Pair(centerEdge, new Pair("walkRestock", null)));
                checkpoints.add(new Pair(center, new Pair("fillMap", null)));
                checkpoints.add(new Pair(centerEdge, new Pair("walkRestock", null)));
                checkpoints.add(new Pair(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
                state = State.Walking;
                break;
            case AwaitCartographyResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                boolean searchingMap = true;
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.contents().get(slot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    if (slot < 9) {  //Stupid slot correction
                        slot += 30;
                    } else {
                        slot -= 6;
                    }
                    ItemStack stack = packet.contents().get(slot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                mc.interactionManager.clickSlot(packet.syncId(), 2, 0, SlotActionType.QUICK_MOVE, mc.player);
                checkpoints.add(new Pair(finishedMapChest.getRight(), new Pair("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.contents().size() - 36; slot < packet.contents().size(); slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                startMining();
                break;
            case AwaitUsedToolChestResponse:
                interactTimeout = 0;
                for (int slot = packet.contents().size() - 36; slot < packet.contents().size(); slot++) {
                    ItemStack stack = packet.contents().get(slot);
                    if (ToolUtils.isTool(stack)) {
                        mc.interactionManager.clickSlot(packet.syncId(), slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                }
                state = State.AwaitNBTFile;
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: §a" + state);
        }

        if (state.equals(State.AwaitMasterAllBuilt)) {
            if (SlaveSystem.allSlavesFinished()) {
                if (!endBuilding()) return;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllBuiltSkip)) {
            if (SlaveSystem.allSlavesFinished()) {
                startMining();
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitManualRepair)) {
            // Refresh known errors
            knownErrors.clear();
            knownErrors.addAll(getInvalidPlacements());
            if (knownErrors.isEmpty()) {
                checkpoints.add(new Pair(mc.player.getPos(), new Pair("lineEnd", null)));
                state = State.Walking;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllMined)) {
            if (SlaveSystem.allSlavesFinished()) {
                minedLines = -1;
                advanceMinedLines();
                if (minedLines >= map.length) {
                    endMining();
                } else {
                    info("Not all lines mined. Redo mining.");
                    calculateMiningPath();
                    state = State.Walking;
                    for (String slave : SlaveSystem.slaves) {
                        if (minedLines >= map.length) break;
                        SlaveSystem.queueDM(slave, "mine:" + minedLines);
                        advanceMinedLines();
                        SlaveSystem.activeSlavesDict.put(slave, true);
                        SlaveSystem.finishedSlavesDict.put(slave, false);
                    }
                }
            } else {
                return;
            }
        }

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / (long) placeDelay.get());
        lastTickTime += allowedPlacements * placeDelay.get();

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getLeft());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        if (jumpTimeout > 0) {
            jumpTimeout--;
            return;
        }

        if (timeoutTicks > 0) {
            timeoutTicks--;
            return;
        }

        // Swap into Hotbar
        if (toBeSwappedSlot != -1) {
            Utils.swapIntoHotbar(toBeSwappedSlot, availableHotBarSlots);
            toBeSwappedSlot = -1;
            if (postSwapDelay.get() != 0) {
                timeoutTicks = postSwapDelay.get();
                return;
            }
        }

        // Restocking
        if (restockBacklogSlots.size() > 0) {
            int slot = restockBacklogSlots.remove(0);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 1, SlotActionType.QUICK_MOVE, mc.player);
            if (restockBacklogSlots.isEmpty()) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && miningPos != null) {
            // Break block if miningPos is not null. Stop walking if further away than maxMiningRange
            if (MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                miningPos = null;
                state = State.Mining;
            } else {
                mc.player.setPitch((float) Rotations.getPitch(miningPos));
                BlockUtils.breakBlock(miningPos, true);

                if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }

                if (state.equals(State.AwaitBlockBreak)) {
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(false);
                    Utils.setJumpPressed(false);
                    return;
                }
            }
        }

        if (state.equals(State.Mining)) {
            // Check if entire line has been mined
            int relativeX = Math.abs(mc.player.getBlockX() - mapCorner.getX());
            if  (isLineMined(relativeX)) {
                miningPos = null;
                if (SlaveSystem.isSlave()) {
                    Utils.setBackwardPressed(false);
                    state = State.AwaitSlaveMineLine;
                    SlaveSystem.queueMasterDM("finished");
                    return;
                } else {
                    if (minedLines < map.length) {
                        state = State.Walking;
                        Utils.setForwardPressed(true);
                        Utils.setBackwardPressed(false);
                        calculateMiningPath();
                    } else {
                        info("Waiting for slaves to finish mining...");
                        state = State.AwaitMasterAllMined;
                        Utils.setBackwardPressed(false);
                        return;
                    }
                }
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                state = State.Walking;
                if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                    refillMiningInventory();
                } else {
                    HashMap<Item, Integer> requiredItems = getRequiredItems(mapCorner, workingInterval, availableSlots.size(), map);
                    Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(invInformation.getRight());
                }
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getStack(dumpSlot).getName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
            if (!prepareNextMapFile()) {
                return;
            }
            startBuilding();
        }

        // Handle Block Entity interaction response
        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            if (mc.currentScreen != null) {
                mc.player.closeHandledScreen();
            }
            closeNextInvPacket = false;
        }

        // Main Loop for Building & Mining

        if (state.equals(State.Walking)) {
            Utils.setForwardPressed(true);
            Utils.setBackwardPressed(false);
        } else if (state.equals(State.Mining)) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(true);
        } else {
            return;
        }
        Utils.setJumpPressed(false);
        // AutoJump logic
        if ((mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()) && jumpTimeout <= 0) {
            Direction direction = Direction.fromHorizontalDegrees(mc.player.getYaw());
            if (mc.options.backKey.isPressed()) direction = direction.getOpposite();
            BlockPos target = mc.player.getBlockPos().offset(direction);
            if (mc.player.isOnGround() && !mc.world.getBlockState(target).isAir()
                && mc.world.getBlockState(target.up(1)).isAir() && mc.world.getBlockState(target.up(2)).isAir()) {
                jumpTimeout = jumpCoolDown.get();
                Utils.setJumpPressed(true);
            }
        }
        if (checkpoints.isEmpty()) {
            // Creating fallback checkpoint
            checkpoints.add(new Pair(mc.player.getPos(), new Pair<>("lineEnd", null)));
        }
        Vec3d goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null) info("Reached: §a" + checkpointAction.getLeft());
            if (snapToCheckpoints.get()) mc.player.setPosition(goal.x, mc.player.getY(), goal.z);
            checkpoints.remove(0);
            switch (checkpointAction.getLeft()) {
                case "lineEnd":
                    calculateBuildingPath(false);
                    ArrayList<BlockPos> newErrors = getInvalidPlacements();
                    for (BlockPos errorPos : newErrors) {
                        BlockPos relativePos = errorPos.subtract(mapCorner);
                        if (logErrors.get()) {
                            info("Error at: " + errorPos.toShortString() + ". Is: "
                                + MapAreaCache.getCachedBlockState(errorPos).getBlock().getName().getString()
                                + ". Should be: " + map[relativePos.getX()][relativePos.getZ()].getLeft().getName().getString());
                        }
                        if (SlaveSystem.isSlave()) {
                            // Obfuscate error pas as relative pos
                            SlaveSystem.queueMasterDM("error:" + relativePos.getX() + ":" + relativePos.getZ());
                        }
                    }
                    knownErrors.addAll(newErrors);
                    break;
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()));
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setForwardPressed(false);
                    mc.player.setYaw(dumpStation.getRight().getLeft());
                    mc.player.setPitch(dumpStation.getRight().getRight());
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getRight());
                    return;
                case "startMine":
                    state = State.Mining;
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(true);
                    break;
                case "miningLineEnd":
                    Utils.setBackwardPressed(false);
                    checkpoints.add(new Pair(mc.player.getPos(), new Pair<>("miningLineEnd", null)));
                    break;
                case "usedToolChest":
                    state = State.AwaitUsedToolChestResponse;
                    interactWithBlock(usedToolChest.getLeft());
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (state.equals(State.Walking)) {
                    // Done Building
                    if (SlaveSystem.isSlave()) {
                        checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                    } else {
                        if (SlaveSystem.allSlavesFinished()) {
                            if (!endBuilding()) return;
                        } else {
                            info("Waiting for slaves to finish placing...");
                            state = State.AwaitMasterAllBuilt;
                            Utils.setForwardPressed(false);
                            return;
                        }
                    }
                }
            }
            goal = checkpoints.get(0).getLeft();
        }

        //Set yaw rotation
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ() + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        Vec3d lookPos = new Vec3d(goal.x, goal.y, lookZ);
        if (state.equals(State.Walking)) {
            mc.player.setYaw((float) Rotations.getYaw(lookPos));
        } else {
            mc.player.setYaw((float) Rotations.getYaw(lookPos) + 180f);
        }

        // Set print mode
        String nextAction = checkpoints.get(0).getRight().getLeft();
        if ((nextAction == "" || nextAction == "lineEnd") && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("", "lineEnd", "sprint", "miningLineEnd");
        if (!allowPlaceActions.contains(nextAction)) return;

        BlockPos nextBlockPos = getNextBlockPos(state.equals(State.Mining));

        if (miningPos != null || nextBlockPos == null) return;

        if (state.equals(State.Walking)) {
            if (PlayerUtils.distanceTo(nextBlockPos.toCenterPos()) <= interactionRange.get()) {
                tryPlacingBlock(nextBlockPos);
            }
        } else {
            Vec3d centerPos = nextBlockPos.toCenterPos();
            if (Math.abs(centerPos.getZ() - mc.player.getZ()) >= 0.5) {
                miningPos = nextBlockPos;
                mc.player.setPitch((float) Rotations.getPitch(miningPos));
                BlockState blockState = mc.world.getBlockState(miningPos);
                ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockState);
                for (int slot : availableHotBarSlots) {
                    if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
                    Item item = mc.player.getInventory().getStack(slot).getItem();
                    if (item.equals(bestTool.getItem())) {
                        InvUtils.swap(slot, false);
                        BlockUtils.breakBlock(miningPos, true);
                        state = State.Mining;
                        if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                            state = State.AwaitBlockBreak;
                        }
                        break;
                    }
                }
            }
        }
    }

    // Restocking

    private Pair<BlockPos, Vec3d> getBestChest(Item item) {
        Vec3d bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Pair<BlockPos, Vec3d>> list = new ArrayList<>();
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + item.getName().getString());
            toggle();
            return new Pair<>(new BlockPos(0, 0, 0), new Vec3d(0, 0, 0));
        }
        //Get nearest chest
        for (Pair<BlockPos, Vec3d> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            checkedChests.clear();
            return getBestChest(item);
        }
        return new Pair(bestChestPos, bestPos);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required build materials
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems(mapCorner, workingInterval, availableSlots.size(), map);
        for (Item item : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(item);
            requiredItems.put(item, oldAmount - invMaterial.get(item));
        }

        for (Item item : requiredItems.keySet()) {
            if (requiredItems.get(item) <= 0) continue;
            int stacks = (int) Math.ceil((float) requiredItems.get(item) / 64f);
            info("Restocking §a" + stacks + " stacks " + item.getName().getString() + " (" + requiredItems.get(item) + ")");
            restockList.add(0, Triple.of(item, stacks, requiredItems.get(item)));
        }
        addClosestRestockCheckpoint();
    }

    private void refillMiningInventory() {
        //Fills restockList with required mining tools
        restockList.clear();

        Set<ItemStack> requiredTools = new HashSet<>();
        for (ItemStack toolStack : toolSet) {
            boolean foundTool = false;
            for (int slot : availableHotBarSlots) {
                if (mc.player.getInventory().getStack(slot).equals(toolStack)) {
                    foundTool = true;
                    break;
                }
            }
            if (!foundTool) requiredTools.add(toolStack);
        }

        for (ItemStack itemStack : requiredTools) {
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), 1, 1));
            info("Restocking §a" + 1 + " " + itemStack.getItem().getName().getString());
        }

        /*HashMap<ItemStack, Integer> toolUseDict = new HashMap<>();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(x, map[x][z].getRight(), z));
                if (!blockstate.isAir()) {
                    ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockstate);
                    if (bestTool == null) continue;
                    if (toolUseDict.containsKey(bestTool)) {
                        toolUseDict.put(bestTool, toolUseDict.get(bestTool) + 1);
                    } else {
                        toolUseDict.put(bestTool, 1);
                    }
                }
            }
        }

        for (ItemStack itemStack : toolUseDict.keySet()) {
            int rawUses = toolUseDict.get(itemStack);
            int adjustedUses = (int) Math.ceil(rawUses / 4);
            int itemsNeeded = (int) Math.ceil((float) adjustedUses / (float) itemStack.getMaxDamage());
            info("Restocking §a" + itemsNeeded + " " + itemStack.getItem().getName().getString() + " (" + rawUses + " uses)");
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), itemsNeeded, itemsNeeded));
        }*/

        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Pair<BlockPos, Vec3d> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos.getLeft() == null) {
                warning("No chest found for " + entry.getLeft().getName().getString());
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getRight());
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        //Set closest material as first and as checkpoint
        restockList.remove(closestEntry);
        restockList.add(0, closestEntry);
        checkpoints.add(0, new Pair(restockPos.getRight(), new Pair("refill", restockPos.getLeft())));
    }

    private void endRestocking() {
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            //Search for the next best chest
            checkedChests.add(lastInteractedChest);
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(getMaterialFromPos(lastInteractedChest));
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
            if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                // Finish building as slave
                state = State.AwaitSlaveMineLine;
                SlaveSystem.queueMasterDM("finished");
                return;
            }
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    private Item getMaterialFromPos(BlockPos pos) {
        for (Item item : materialDict.keySet()) {
            for (Pair<BlockPos, Vec3d> p : materialDict.get(item)) {
                if (p.getLeft().equals(pos)) return item;
            }
        }
        warning("Could not find material for chest position : " + pos.toShortString());
        toggle();
        return null;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos chestPos) {
        Utils.setForwardPressed(false);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setYaw((float) Rotations.getYaw(chestPos.toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(chestPos.toCenterPos()));

        BlockHitResult hitResult = new BlockHitResult(chestPos.toCenterPos(), Utils.getInteractionSide(chestPos), chestPos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);

        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private void tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Item material = map[relativePos.getX()][relativePos.getZ()].getLeft().asItem();
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotatePlace.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                toBeSwappedSlot = slot;
                Utils.setForwardPressed(false);
                mc.player.setVelocity(0, 0, 0);
                timeoutTicks = preSwapDelay.get();
                return;
            }
        }
        if (lastSwappedMaterial == material) return;      //Wait for swapped material
        info("No " + material.getName().getString() + " found in inventory. Resetting...");
        mc.player.setVelocity(0, 0, 0);
        Vec3d pathCheckpoint = new Vec3d(mc.player.getX(), mapCorner.toCenterPos().getY(), mapCorner.north().toCenterPos().getZ());
        checkpoints.add(0, new Pair(mc.player.getPos(), new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));;
    }

    private BlockPos getNextBlockPos(boolean mining) {
         // For building: Get next block in working interval
        // For mining: Get next block on current mining line
        int relativeX = mc.player.getBlockX() - mapCorner.getX();
        int lowerX = mining ? relativeX : workingInterval.getLeft();
        int upperX = mining ? relativeX : workingInterval.getRight();
        for (int x = lowerX; x <= upperX; x++) {
            for (int z = 0; z < 128; z++) {
                int adjustedZ = mining ? 127 - z : z;
                BlockPos blockPos = mapCorner.add(x, map[x][adjustedZ].getRight(), adjustedZ);
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (blockState.isAir() ^ mining) {
                    return blockPos;
                }
            }
        }
        return null;
    }

    /*private boolean canSee(Vec3d pos) {
        RaycastContext raycastContext = new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE, mc.player);
        HitResult hitResult = mc.world.raycast(raycastContext);
        return hitResult.getPos().equals(pos);
    }*/

    // Path and Building Management

    private void calculateBuildingPath(boolean sprintFirst) {
        //Replace checkpoints with path for building (working interval)
        checkpoints.clear();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            boolean lineFinished = true;
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(x, map[x][z].getRight(), z));
                if (blockstate.isAir()) {
                    lineFinished = false;
                    break;
                }
            }
            if (lineFinished) continue;
            Vec3d cp1 = mapCorner.toCenterPos().add(x, 0.5, -1);
            Vec3d cp2 = mapCorner.toCenterPos().add(x, map[x][map[0].length-2].getRight() + 0.5, map[0].length-2);
            checkpoints.add(new Pair(cp1, new Pair("", null)));
            checkpoints.add(new Pair(cp2, new Pair("", null)));
            checkpoints.add(new Pair(cp1, new Pair("lineEnd", null)));
        }
        if (checkpoints.size() > 0 && sprintFirst) {
            //Make player sprint to the start of the map
            Pair<Vec3d, Pair<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair(firstPoint.getLeft(), new Pair("sprint", firstPoint.getRight().getRight())));
        }
    }

    private void calculateMiningPath() {
        // Replace checkpoints with path for mining (next single line)
        checkpoints.clear();
        Vec3d cp1 = mapCorner.toCenterPos().add(minedLines, 0.5, -2);
        Vec3d cp2 = mapCorner.toCenterPos().add(minedLines, map[minedLines][0].getRight()+0.5, -1);
        for (int i = 0; i < map[minedLines].length-2; i++) {
            BlockPos airPos = mapCorner.add(minedLines, map[minedLines][i+2].getRight(), i+2);
            cp2 = mapCorner.toCenterPos().add(minedLines, map[minedLines][i].getRight()+0.5, i);
            if (mc.world.getBlockState(airPos).isAir()) break;
        }
        checkpoints.add(new Pair(cp1, new Pair("miningLineStart", null)));
        checkpoints.add(new Pair(cp2, new Pair("startMine", null)));
        checkpoints.add(new Pair(cp1, new Pair("miningLineEnd", null)));

        advanceMinedLines();
    }

    private void advanceMinedLines() {
        while (minedLines <= map.length) {
            minedLines++;
            if(!isLineMined(minedLines)) return;
        }
    }

    private boolean isLineMined(int line) {
        if (line >= map.length) return false;

        boolean isMined = true;
        for (int z = 0; z < map[line].length; z++) {
            BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.add(line, map[line][z].getRight(), z));
            if (!blockstate.isAir()) {
                isMined = false;
                break;
            }
        }
        return isMined;
    }

    private void startBuilding() {
        info("Start building map");
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(mapCorner);
        calculateBuildingPath(true);
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        state = State.Walking;
    }

    private boolean endBuilding() {
        // Only executed on Master
        if (!knownErrors.isEmpty()) {
            if (errorAction.get() == ErrorAction.ManualRepair) {
                workingInterval = new Pair<>(0, map.length-1);
                info("Found errors: ");
                for (int i = knownErrors.size()-1; i >= 0; i--) {
                    info("Pos: " + knownErrors.get(i).toShortString());
                }
                state = State.AwaitManualRepair;
                Utils.setForwardPressed(false);
                warning("ErrorAction is ManualRepair. The module resumes when all errors are fixed. All errors are highlighted");
                return false;
            }
        }
        info("Finished building map");
        state = State.Walking;
        workingInterval = trueInterval;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Pair<BlockPos, Vec3d> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        checkpoints.add(new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
        try {
            if (moveToFinishedFolder.get())
                mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
        } catch (Exception e) {
            warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
            e.printStackTrace();
        }
        return true;
    }

    private void startMining() {
        info("Start mining map");
        minedLines = -1;
        advanceMinedLines();
        calculateMiningPath();
        refillMiningInventory();
        state = State.Walking;
        for (String slave : SlaveSystem.slaves) {
            if (minedLines >= map.length) break;
            SlaveSystem.queueDM(slave, "mine:" + minedLines);
            advanceMinedLines();
            SlaveSystem.activeSlavesDict.put(slave, true);
            SlaveSystem.finishedSlavesDict.put(slave, false);
        }
    }

    private void endMining() {
        // Only executed on Master
        info("Finished mining map");
        SlaveSystem.sendToAllSlaves("start");
        for (String slave : SlaveSystem.activeSlavesDict.keySet()) {
            SlaveSystem.activeSlavesDict.put(slave, true);
        }
        SlaveSystem.setAllSlavesUnfinished();
        checkpoints.clear();
        checkpoints.add(0, new Pair(usedToolChest.getRight(), new Pair("usedToolChest", null)));
        state = State.Walking;
    }

    public ArrayList<BlockPos> getInvalidPlacements() {
        ArrayList<BlockPos> invalidPlacements = new ArrayList<>();
        for (int x = workingInterval.getRight(); x >= workingInterval.getLeft(); x--) {
            for (int z = 127; z >= 0; z--) {
                BlockPos relativePos = new BlockPos(x, map[x][z].getRight(), z);
                BlockPos absolutePos = mapCorner.add(relativePos);
                if (knownErrors.contains(absolutePos)) continue;
                BlockState blockState = MapAreaCache.getCachedBlockState(absolutePos);
                Block block = blockState.getBlock();
                if (!blockState.isAir()) {
                    if (map[x][z].getLeft() != block) invalidPlacements.add(absolutePos);
                }
            }
        }
        return invalidPlacements;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        info("Inventory slots available for building: " + availableSlots);
        if (availableHotBarSlots.isEmpty()) {
            warning("No free slots found in hot-bar!");
            availableSlots.clear();
            toggle();
            return false;
        }
        if (availableSlots.size() < 2) {
            warning("You need at least 2 free inventory slots!");
            availableSlots.clear();
            toggle();
            return false;
        }
        return true;
    }

    private int getDumpSlot() {
        HashMap<Item, Integer> requiredItems = getRequiredItems(mapCorner, workingInterval, availableSlots.size(), map);
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        return invInformation.getLeft().get(0);
    }

    public HashMap<Item, Integer> getRequiredItems(BlockPos mapCorner, Pair<Integer, Integer> interval, int availableSlotsSize, Pair<Block, Integer>[][] map) {
        //Calculate the next items to restock
        //Iterate over map. Player has to be able to see the complete map area
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        for (int x = interval.getLeft(); x <= interval.getRight(); x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.add(x, map[x][z].getRight(), z));
                if (blockState.isAir() && map[x][z] != null) {
                    //ChatUtils.info("Add material for: " + mapCorner.add(x + lineBonus, 0, adjustedZ).toShortString());
                    Item material = map[x][z].getLeft().asItem();
                    if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                    requiredItems.put(material, requiredItems.get(material) + 1);
                    //Check if the item fits into inventory. If not, undo the last increment and return
                    if (Utils.stacksRequired(requiredItems.values()) > availableSlotsSize) {
                        requiredItems.put(material, requiredItems.get(material) - 1);
                        return requiredItems;
                    }
                }
            }
        }
        return requiredItems;
    }

    // MapPrinter Interface for Slave Logic

    public void setInterval(Pair<Integer, Integer> interval) {
        info("Set interval to: " + interval.getLeft() + " - " + interval.getRight());
        workingInterval = interval;
        trueInterval = interval;
    }

    public void addError(BlockPos relPos) {
        BlockPos absPos = mapCorner.add(relPos.getX(), map[relPos.getX()][relPos.getZ()].getRight(), relPos.getZ());
        if (!knownErrors.contains(absPos)) knownErrors.add(new BlockPos(absPos));
    }

    public void pause() {
        if (!state.equals(State.AwaitSlaveContinue)) {
            oldState = state;
            state = State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
        }
    }

    public void start() {
        if (availableSlots.isEmpty()) {
            state = State.AwaitNBTFile;
            return;
        }
        if (state.equals(State.AwaitSlaveContinue)) {
            state = oldState;
            return;
        }
        if (state.equals(State.AwaitSlaveMineLine)) {
            checkpoints.clear();
            checkpoints.add(0, new Pair(usedToolChest.getRight(), new Pair("usedToolChest", null)));
            state = State.Walking;
        }
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {
        if (availableSlots.isEmpty()) setupSlots();
        knownErrors.clear();
        checkpoints.clear();
        if (SlaveSystem.isSlave()) {
            checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
            state = State.Walking;
        } else {
            try {
                if (moveToFinishedFolder.get())
                    mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
            } catch (Exception e) {
                warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
                e.printStackTrace();
            }
            state = State.AwaitMasterAllBuiltSkip;
        }
    }

    public void slaveFinished(String slave) {
        if (minedLines < map.length) {
            SlaveSystem.queueDM(slave, "mine:" + minedLines);
            advanceMinedLines();
            SlaveSystem.activeSlavesDict.put(slave, true);
            SlaveSystem.finishedSlavesDict.put(slave, false);
        }
    }

    public void mineLine(int lines) {
        minedLines = lines;
        calculateMiningPath();
        state = State.Walking;
    }

    // Path Change Check

    private void warnPathChanged() {
        if (checkpoints != null && !activationReset.get()) {
            String reString = isActive() ? "re" : "";
            warning("The custom path is only applied if the module is " + reString + "started with Activation Reset enabled!");
        }
    }

    // Config System

    private void saveConfig(File configFile) {
        if (configFile == null) {
            error("No config file name selected.");
            return;
        }
        if (cartographyTable == null || finishedMapChest == null || dumpStation == null || mapCorner == null
            || materialDict.isEmpty() || usedToolChest == null || toolSet.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "staircased",
                cartographyTable,
                finishedMapChest,
                usedToolChest,
                mapMaterialChests,
                dumpStation,
                mapCorner,
                materialDict,
                toolSet);
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully saved config to: ").append(configText));
        } catch (IOException e) {
            error("Failed to create config file.");
        }
    }

    private void loadConfig(File configFile) {
        if (configFile == null || !configFile.exists() || state == null) return;
        List<State> allowedStates = List.of(
            State.SelectingChests,
            State.SelectingFinishedMapChest,
            State.SelectingUsedPickaxeChest,
            State.SelectingDumpStation,
            State.SelectingTable,
            State.SelectingMapArea,
            State.AwaitRegisterResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return;
        }

        try {
            ConfigDeserializer.ConfigData data =
                ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("staircased")) {
                error("Config file is of type "+ data.type +" and not 'staircased'.");
                return;
            }
            if (data.cartographyTable == null || data.finishedMapChest == null || data.dumpStation == null || data.mapCorner == null
                || data.materialDict.isEmpty() || data.usedToolChest == null || toolSet == null) {
                error("Config file is missing required data.");
                return;
            }
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.usedToolChest = data.usedToolChest;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStation = data.dumpStation;
            this.mapCorner = data.mapCorner;
            MapAreaCache.reset(mapCorner);
            this.materialDict = data.materialDict;
            this.toolSet = data.toolSet;
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully loaded config: ").append(configText));
            info("Interact with the Start Block to start printing.");
            state = State.SelectingChests;
        } catch (IOException e) {
            error("Failed to read config file.");
        }
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("All nbt files finished");
                toggle();
            }
            return false;
        }
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }

        return true;
    }

    private boolean loadNBTFile() {
        try {
            info("Building: §a" + mapFile.getName());
            NbtSizeTracker sizeTracker = new NbtSizeTracker(0x20000000L, 100);
            NbtCompound nbt = NbtIo.readCompressed(mapFile.toPath(), sizeTracker);
            //Extracting the palette
            NbtList paletteList = (NbtList) nbt.get("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            NbtList blockList = (NbtList) nbt.get("blocks");
            map = generateMapArray(blockList);

            info("Requirements: ");
            for (Pair<Block, Integer> p : blockPaletteDict.values()) {
                if (p.getRight() == 0) continue;
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            //Check if a full 128x128 map is present
            for (int x = 0; x < map.length; x++) {
                for (int z = 0; z < map[x].length; z++) {
                    if (map[x][z] == null) {
                        warning("No 128x129 (extra line on north side) map present in file: " + mapFile.getName());
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Pair<Block, Integer>[][] generateMapArray(NbtList blockList) {
        // Get the highest block of each column
        Pair<Block, Integer>[][] absoluteHeightMap = new Pair[128][129];
        for (int i = 0; i < blockList.size(); i++) {
            Optional<NbtCompound> blockOpt = blockList.getCompound(i);
            if (blockOpt.isEmpty()) continue;

            NbtCompound block = blockOpt.get();

            Optional<Integer> blockIdOpt = block.getInt("state");
            if (blockIdOpt.isEmpty() || !blockPaletteDict.containsKey(blockIdOpt.get())) continue;

            int blockId = blockIdOpt.get();

            NbtList pos = block.getList("pos").get();

            Optional<Integer> xOpt = pos.getInt(0);
            Optional<Integer> yOpt = pos.getInt(1);
            Optional<Integer> zOpt = pos.getInt(2);
            if (xOpt.isEmpty() || yOpt.isEmpty() || zOpt.isEmpty()) {
                continue;
            }

            int x = xOpt.get();
            int y = yOpt.get();
            int z = zOpt.get();
            if (absoluteHeightMap[x][z] == null || absoluteHeightMap[x][z].getRight() < y) {
                Block material = blockPaletteDict.get(blockId).getLeft();
                absoluteHeightMap[x][z] = new Pair<>(material, y);
            }
            if (z > 0) {
                blockPaletteDict.put(blockId, new Pair(blockPaletteDict.get(blockId).getLeft(), blockPaletteDict.get(blockId).getRight()+1));
            }
        }
        // Smooth the y pos out to max 1 block difference
        Pair<Block, Integer>[][] smoothedHeightMap = new Pair[128][128];
        for (int x = 0; x < absoluteHeightMap.length; x++) {
            int totalYDiff = 0;
            for (int z = 1; z < absoluteHeightMap[0].length; z++) {
                int predecessorY = absoluteHeightMap[x][z-1].getRight();
                int currentY = absoluteHeightMap[x][z].getRight();
                totalYDiff += Math.max(-1, Math.min(currentY - predecessorY, 1));
                smoothedHeightMap[x][z-1] = new Pair<>(absoluteHeightMap[x][z].getLeft(), totalYDiff);
            }
        }
        return smoothedHeightMap;
    }

    // Rendering

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = new WTable();
        list.add(table);

        File configFolder = new File(mapFolder, "_configs");
        if (!configFolder.exists()) return table;

        table.add(theme.label("Configurations: "));
        // ---- Save config button ----
        WButton saveButton = table.add(theme.button("Save Config")).widget();
        saveButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null,
                null
            );
            if (path != null) saveConfig(new File(path));
        };

        // ---- Load config button ----
        WButton loadButton = table.add(theme.button("Load Config")).widget();
        loadButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                "Load Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null,
                null,
                false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);

        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, true);
        slaveController.rebuild();

        SlaveSystem.tableController = slaveController;
        return list;
    }

    @Override
    public String getInfoString() {
        if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mapCorner == null || !render.get()) return;

        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX() + 128, mapCorner.getY(), mapCorner.getZ() + 128, color.get(), color.get(), ShapeMode.Lines, 0);

        if (renderMap.get() && !(state.equals(State.Mining) || state.equals(State.AwaitBlockBreak))) {
            for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
                for (int z = 0; z < map[0].length; z++) {
                    BlockPos renderPos = mapCorner.add(x, map[x][z].getRight(), z);
                    if (!MapAreaCache.getCachedBlockState(renderPos).isAir()) continue;
                    event.renderer.box(renderPos, color.get(), color.get(), ShapeMode.Lines, 0);
                }
            }
        }

        if (knownErrors != null) {
            for (BlockPos pos : knownErrors) {
                event.renderer.box(pos, color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }

        ArrayList<Pair<BlockPos, Vec3d>> renderedPairs = new ArrayList<>();
        for (ArrayList<Pair<BlockPos, Vec3d>> list : materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        for (Pair<BlockPos, Vec3d> pair : renderedPairs) {
            if (renderChestPositions.get())
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3d openPos = pair.getRight();
                event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            for (Pair<Vec3d, Pair<String, BlockPos>> pair : checkpoints) {
                Vec3d cp = pair.getLeft();
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.getX() + indicatorSize.get(), cp.getY() + indicatorSize.get(), cp.getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (usedToolChest != null) {
                event.renderer.box(usedToolChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(usedToolChest.getRight().x - indicatorSize.get(), usedToolChest.getRight().y - indicatorSize.get(), usedToolChest.getRight().z - indicatorSize.get(), usedToolChest.getRight().getX() + indicatorSize.get(), usedToolChest.getRight().getY() + indicatorSize.get(), usedToolChest.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x - indicatorSize.get(), cartographyTable.getRight().y - indicatorSize.get(), cartographyTable.getRight().z - indicatorSize.get(), cartographyTable.getRight().getX() + indicatorSize.get(), cartographyTable.getRight().getY() + indicatorSize.get(), cartographyTable.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStation != null) {
                event.renderer.box(dumpStation.getLeft().x - indicatorSize.get(), dumpStation.getLeft().y - indicatorSize.get(), dumpStation.getLeft().z - indicatorSize.get(), dumpStation.getLeft().getX() + indicatorSize.get(), dumpStation.getLeft().getY() + indicatorSize.get(), dumpStation.getLeft().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x - indicatorSize.get(), finishedMapChest.getRight().y - indicatorSize.get(), finishedMapChest.getRight().z - indicatorSize.get(), finishedMapChest.getRight().getX() + indicatorSize.get(), finishedMapChest.getRight().getY() + indicatorSize.get(), finishedMapChest.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    private enum State {
        SelectingMapArea,
        SelectingTable,
        SelectingUsedPickaxeChest,
        SelectingDumpStation,
        SelectingFinishedMapChest,
        SelectingChests,
        AwaitRegisterResponse,
        AwaitRestockResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitUsedToolChestResponse,
        AwaitCartographyResponse,
        AwaitNBTFile,
        AwaitBlockBreak,
        AwaitMasterAllBuilt,
        AwaitMasterAllBuiltSkip,
        AwaitMasterAllMined,
        AwaitSlaveContinue,
        AwaitSlaveMineLine,
        AwaitManualRepair,
        Walking,
        Mining,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum ErrorAction {
        Ignore,
        ManualRepair
    }
}
