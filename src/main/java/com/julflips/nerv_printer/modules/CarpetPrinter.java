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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
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
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CarpetPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced",  false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render", false);

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("The maximum range you can place carpets around yourself.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> minPlaceDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-place-distance")
        .description("The minimal distance a placement has to have to the player. Avoids placements colliding with the player.")
        .defaultValue(0.8)
        .min(0)
        .sliderRange(0, 2)
        .build()
    );

    private final Setting<List<Block>> ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignored-Blocks")
        .description("Blocks types that will not be placed. Useful to print semi-transparent maps.")
        .defaultValue()
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

    private final Setting<List<Block>> startBlock = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-Block")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<Integer> mapFillSquareSize = sgGeneral.add(new IntSetting.Builder()
        .name("map-fill-square-size")
        .description("The radius of the square the bot fill walk to explore the map.")
        .defaultValue(1)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode")
        .description("How to sprint.")
        .defaultValue(SprintMode.NotPlacing)
        .build()
    );

    public final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset")
        .description("Resets all values when module is activated or the client relogs. Disable to be able to pause.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate when placing a block.")
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

    private final Setting<Integer> resetChestCloseDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("reset-chest-close-delay")
        .description("How many ticks to wait before closing the reset trap chest again.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 40)
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

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer")
        .description("The buffer area of the checkpoints. Larger means less precise walking, but might be desired at higher speeds.")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Boolean> breakCarpetAboveReset = sgAdvanced.add(new BoolSetting.Builder()
        .name("break-carpet-above-reset")
        .description("Break the carpet above the reset chest before activating. Useful when interactions trough blocks are not allowed.")
        .defaultValue(true)
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
        .defaultValue(ErrorAction.Repair)
        .build()
    );

    //Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Highlights the selected areas.")
        .defaultValue(true)
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
        .defaultValue(0.15)
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
    int closeResetChestTicks;
    int interactTimeout;
    int toBeSwappedSlot;
    long lastTickTime;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    State debugPreviousState;
    Pair<Integer, Integer> workingInterval;     //Interval the bot should work in 0-127
    Pair<Integer, Integer> trueInterval;        //Stores the actual interval in case the old one is temporarily overwritten while repairing
    Pair<BlockPos, Vec3d> reset;
    Pair<BlockPos, Vec3d> cartographyTable;
    Pair<BlockPos, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    Pair<Vec3d, Pair<Float, Float>> dumpStation;                    //Pos, Yaw, Pitch
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedBlockPos;
    BlockPos miningPos;
    Item lastSwappedMaterial;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;       //Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict;  //Maps item to the chest pos and the open position
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Item, Integer, Integer>> restockList;        //Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;    //(GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<BlockPos> knownErrors;
    Block[][] map;
    File mapFolder;
    File mapFile;

    public CarpetPrinter() {
        super(Addon.CATEGORY, "carpet-printer", "Automatically builds 2D carpet maps from nbt files.");
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
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        knownErrors = new ArrayList<>();
        reset = null;
        mapCorner = null;
        lastInteractedBlockPos = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        closeResetChestTicks = 0;
        toBeSwappedSlot = -1;
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
        if (!prepareNextMapFile()) return;
        state = State.SelectingMapArea;
        info("Select the §aMap Building Area (128x128)");
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof PlayerActionC2SPacket packet
            && (packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM || packet.getAction() == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS)) {
            dumpStation = new Pair<>(mc.player.getEntityPos(), new Pair<>(mc.player.getYaw(), mc.player.getPitch()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the §aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().up();
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                MapAreaCache.reset(mapCorner);
                state = State.SelectingReset;
                info("Map Area selected. Press the §aReset Trapped Chest §7used to remove the carpets");
                break;
            case SelectingReset:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof TrappedChestBlock) {
                    reset = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Reset Trapped Chest selected. Select the §aCartography Table.");
                    state = State.SelectingTable;
                }
                break;
            case SelectingTable:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Cartography Table selected. Please throw an item into the §aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("Finished Map Chest selected. Select all §aMap- and Material-Chests. Interact with the Start Block to start printing.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlock.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (blockState.getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlock.get().contains(blockState.getBlock())) {
                    //Check if requirements to start building are met
                    if (materialDict.isEmpty()) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.isEmpty()) {
                        warning("No Map Chests selected!");
                        return;
                    }
                    if (!setupSlots()) {
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
            if (timeoutTicks > 0) Utils.setForwardPressed(false);
        }

        if (!(event.packet instanceof InventoryS2CPacket packet)) return;

        if (state.equals(State.AwaitRegisterResponse)) {
            //info("Chest content received.");
            Item foundItem = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.contents().size() - 36; i++) {
                ItemStack stack = packet.contents().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered §aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.getEntityPos());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            info("Registered §a" + foundItem.getName().getString());
            if (!materialDict.containsKey(foundItem)) materialDict.put(foundItem, new ArrayList<>());
            ArrayList<Pair<BlockPos, Vec3d>> oldList = materialDict.get(foundItem);
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getEntityPos());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitResetResponse);
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
                List<Integer> slots = IntStream.rangeClosed(0, packet.contents().size() - 37)
                    .boxed()
                    .collect(Collectors.toList());
                Collections.shuffle(slots);

                for (int slot : slots) {
                    ItemStack stack = packet.contents().get(slot);

                    if (restockList.get(0).getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && stack.getCount() == 64) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(slot);
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

                Vec3d center = mapCorner.add(map.length / 2 - 1, 0, map[0].length / 2 - 1).toCenterPos();
                checkpoints.add(new Pair(center, new Pair("fillMap", null)));
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
                if (breakCarpetAboveReset.get()) {
                    BlockPos abovePos = reset.getLeft().up();
                    if (MapAreaCache.getCachedBlockState(abovePos).getBlock() instanceof CarpetBlock) {
                        checkpoints.add(new Pair(reset.getRight(), new Pair("break", abovePos)));
                    }
                }
                checkpoints.add(new Pair(reset.getRight(), new Pair("reset", null)));
                state = State.Walking;
                break;
            case AwaitResetResponse:
                interactTimeout = 0;
                closeNextInvPacket = false;
                closeResetChestTicks = resetChestCloseDelay.get();
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

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / placeDelay.get());
        lastTickTime += (long) allowedPlacements * placeDelay.get();

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                interactWithBlock(lastInteractedBlockPos);
            }
        }

        if (closeResetChestTicks > 0) {
            closeResetChestTicks--;
            if (closeResetChestTicks == 0) {
                mc.player.closeHandledScreen();
                Vec3d center = mapCorner.add(map.length / 2, 0, map[0].length / 2).toCenterPos();
                checkpoints.add(0, new Pair(center, new Pair("awaitClear", null)));
                state = State.Walking;
                info("close reset chest");
            }
        }

        if (timeoutTicks > 0) {
            if (mc.player.isOnGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
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

        // Break blocks for repair
        if (state == State.AwaitBlockBreak) {
            if (MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                miningPos = null;
                state = State.Walking;
                if (checkpoints.isEmpty()) calculateBuildingPath(false, true);
            } else {
                Rotations.rotate(Rotations.getYaw(miningPos), Rotations.getPitch(miningPos), 50);
                BlockUtils.breakBlock(miningPos, true);
                return;
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                HashMap<Item, Integer> requiredItems = Utils.getRequiredItems(mapCorner, workingInterval, linesPerRun.get(), availableSlots.size(), map);
                Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                refillInventory(invInformation.getRight());
                state = State.Walking;
            } else {
                if (debugPrints.get())
                    info("Dumping §a" + mc.player.getInventory().getStack(dumpSlot).getName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        // Await map reset
        if (state == State.AwaitAreaClear && MapAreaCache.isMapAreaClear()) {
            state = State.AwaitNBTFile;
            return;
        }

        // Load next nbt file
        if (state == State.AwaitNBTFile) {
            if (!prepareNextMapFile()) return;
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

        // Main Loop for building
        if (!state.equals(State.Walking)) return;
        Utils.setForwardPressed(true);
        if (checkpoints.isEmpty()) {
            // Creating fallback checkpoint
            checkpoints.add(new Pair(mc.player.getEntityPos(), new Pair<>("lineEnd", null)));
        }
        Vec3d goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null) info("Reached: §a" + checkpointAction.getLeft());
            checkpoints.remove(0);
            switch (checkpointAction.getLeft()) {
                case "lineEnd":
                    boolean atCornerSide = goal.z == mapCorner.toCenterPos().z;
                    calculateBuildingPath(atCornerSide, false);
                    ArrayList<BlockPos> newErrors = Utils.getInvalidPlacements(mapCorner, workingInterval, map, knownErrors);
                    for (BlockPos errorPos : newErrors) {
                        BlockPos relativePos = errorPos.subtract(mapCorner);
                        if (logErrors.get()) {
                            Block missingBlock = map[relativePos.getX()][relativePos.getZ()];
                            String missingBlockString = missingBlock == null ? "empty" : missingBlock.getName().getString();
                            info("Error at: " + errorPos.toShortString() + ". Is: "
                                + MapAreaCache.getCachedBlockState(errorPos).getBlock().getName().getString()
                                + ". Should be: " + missingBlockString);
                        }
                        if (SlaveSystem.isSlave()) {
                            // Obfuscate error pas as relative pos
                            SlaveSystem.queueMasterDM("error:" + relativePos.getX() + ":" + relativePos.getZ());
                        }
                    }
                    knownErrors.addAll(newErrors);
                    if (!knownErrors.isEmpty() && errorAction.get() == ErrorAction.Reset) {
                        warning("ErrorAction is Reset: Resetting map because of an error...");
                        checkpoints.clear();
                        if (breakCarpetAboveReset.get()) {
                            BlockPos abovePos = reset.getLeft().up();
                            if (MapAreaCache.getCachedBlockState(abovePos).getBlock() instanceof CarpetBlock) {
                                checkpoints.add(new Pair(reset.getRight(), new Pair("break", abovePos)));
                            }
                        }
                        checkpoints.add(new Pair(reset.getRight(), new Pair("reset", null)));
                        startedFiles.remove(mapFile);
                    }
                    break;
                case "mapMaterialChest":
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()));
                    if (mapFillSquareSize.get() == 0) {
                        checkpoints.add(0, new Pair(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
                    } else {
                        checkpoints.add(new Pair(goal.add(-mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(mapFillSquareSize.get(), 0, mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(goal.add(-mapFillSquareSize.get(), 0, -mapFillSquareSize.get()), new Pair("sprint", null)));
                        checkpoints.add(new Pair(cartographyTable.getRight(), new Pair("cartographyTable", null)));
                    }
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "reset":
                    info("Resetting...");
                    state = State.AwaitResetResponse;
                    interactWithBlock(reset.getLeft());
                    lastInteractedBlockPos = reset.getLeft();
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
                case "awaitClear":
                    state = State.AwaitAreaClear;
                    Utils.setForwardPressed(false);
                    return;
                case "break":
                    state = State.AwaitBlockBreak;
                    miningPos = checkpointAction.getRight();
                    Utils.setForwardPressed(false);
                    Rotations.rotate(Rotations.getYaw(miningPos), Rotations.getPitch(miningPos), 50);
                    BlockUtils.breakBlock(miningPos, true);
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (SlaveSystem.isSlave()) {
                    SlaveSystem.queueMasterDM("finished");
                    state = State.AwaitSlaveNextMap;
                    Utils.setForwardPressed(false);
                    return;
                }
                if (SlaveSystem.allSlavesFinished()) {
                    if (!endBuilding()) return;
                } else {
                    info("Waiting for slaves to finish...");
                    state = State.AwaitMasterAllBuilt;
                    Utils.setForwardPressed(false);
                    return;
                }
            }
            goal = checkpoints.get(0).getLeft();
        }
        mc.player.setYaw((float) Rotations.getYaw(goal));
        String nextAction = checkpoints.get(0).getRight().getLeft();

        if ((nextAction == "" || nextAction == "lineEnd") && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("", "lineEnd", "sprint");
        if (!allowPlaceActions.contains(nextAction)) return;

        ArrayList<BlockPos> placements = new ArrayList<>();
        for (int i = 0; i < allowedPlacements; i++) {
            AtomicReference<BlockPos> closestPos = new AtomicReference<>();
            final Vec3d currentGoal = goal;
            BlockPos groundedPlayerPos = new BlockPos(mc.player.getBlockPos().getX(), mapCorner.getY(), mc.player.getBlockPos().getZ());
            Utils.iterateBlocks(groundedPlayerPos, (int) Math.ceil(placeRange.get()) + 1, 0, ((blockPos, blockState) -> {
                Double posDistance = PlayerUtils.distanceTo(blockPos.toCenterPos());
                BlockPos relativePos = blockPos.subtract(mapCorner);
                if (blockState.isAir() && posDistance <= placeRange.get() && posDistance > minPlaceDistance.get()
                    && MapAreaCache.isWithingMap(blockPos) && map[relativePos.getX()][relativePos.getZ()] != null
                    && blockPos.getX() <= currentGoal.getX() + linesPerRun.get() - 1 && !placements.contains(blockPos)
                    && blockPos.getX() >= currentGoal.getX()-1) {
                    if (closestPos.get() == null || posDistance < PlayerUtils.distanceTo(closestPos.get())) {
                        closestPos.set(new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
                    }
                }
            }));

            if (closestPos.get() != null) {
                //Stop placing if restocking
                placements.add(closestPos.get());
                if (!tryPlacingBlock(closestPos.get())) {
                    return;
                }
            }
        }
    }

    // Restocking

    private Pair<BlockPos, Vec3d> getBestChest(Item item) {
        Vec3d bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Pair<BlockPos, Vec3d>> list;
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + item.getName().getString());
            toggle();
            return null;
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

    private void refillInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required items
        restockList.clear();
        HashMap<Item, Integer> requiredItems = Utils.getRequiredItems(mapCorner, workingInterval, linesPerRun.get(), availableSlots.size(), map);
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

    private void addClosestRestockCheckpoint() {
        //Determine closest restock chest for material in restock list
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Pair<BlockPos, Vec3d> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos == null) return;
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
            checkedChests.add(lastInteractedBlockPos);

            Item foundItem = null;
            for (Item item : materialDict.keySet()) {
                for (Pair<BlockPos, Vec3d> p : materialDict.get(item)) {
                    if (p.getLeft().equals(lastInteractedBlockPos)) {
                        foundItem = item;
                        break;
                    }
                }
            }
            if (foundItem == null) {
                warning("Could not find material for chest position : " + lastInteractedBlockPos.toShortString());
                toggle();
                return;
            }
            Pair<BlockPos, Vec3d> bestRestockPos = getBestChest(foundItem);
            if (bestRestockPos == null) return;
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos blockPos) {
        Utils.setForwardPressed(false);
        mc.player.setVelocity(0, 0, 0);
        mc.player.setYaw((float) Rotations.getYaw(blockPos.toCenterPos()));
        mc.player.setPitch((float) Rotations.getPitch(blockPos.toCenterPos()));

        BlockHitResult hitResult = new BlockHitResult(blockPos.toCenterPos(), Utils.getInteractionSide(blockPos), blockPos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
        //Set timeout for chest interaction
        interactTimeout = retryInteractTimer.get();
        lastInteractedBlockPos = blockPos;
    }

    private boolean tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.subtract(mapCorner);
        Item material = map[relativePos.getX()][relativePos.getZ()].asItem();
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotate.get(), 50, true, true, false);
                if (material == lastSwappedMaterial) lastSwappedMaterial = null;
                return true;
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
                return false;
            }
        }
        if (lastSwappedMaterial == material) return false;      //Wait for swapped material
        info("No " + material.getName().getString() + " found in inventory. Resetting...");
        checkpoints.add(0, new Pair(mc.player.getEntityPos(), new Pair("sprint", null)));
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        return false;
    }

    // Path and Building Management

    private void calculateBuildingPath(boolean cornerSide, boolean sprintFirst) {
        //Iterate over map and skip completed lines. Player has to be able to see the complete map area
        //Fills checkpoints list
        boolean isStartSide = cornerSide;
        checkpoints.clear();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x += linesPerRun.get()) {
            if (!Utils.isInInterval(workingInterval, x)) continue;
            boolean lineFinished = true;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                int adjustedX = x + lineBonus;
                if (adjustedX > workingInterval.getRight()) break;
                for (int z = 0; z < 128; z++) {
                    BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.add(adjustedX, 0, z));
                    if (blockState.isAir() && map[adjustedX][z] != null) {
                        //If there is a replaceable block and not an ignored block type at the position. Mark the line as not done
                        lineFinished = false;
                        break;
                    }
                }
            }
            if (lineFinished) continue;
            Vec3d cp1 = mapCorner.toCenterPos().add(x, 0, 0);
            Vec3d cp2 = mapCorner.toCenterPos().add(x, 0, 127);
            if (isStartSide) {
                checkpoints.add(new Pair(cp1, new Pair("", null)));
                checkpoints.add(new Pair(cp2, new Pair("lineEnd", null)));
            } else {
                checkpoints.add(new Pair(cp2, new Pair("", null)));
                checkpoints.add(new Pair(cp1, new Pair("lineEnd", null)));
            }
            isStartSide = !isStartSide;
        }
        if (checkpoints.size() > 0 && sprintFirst) {
            //Make player sprint to the start of the map
            Pair<Vec3d, Pair<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair(firstPoint.getLeft(), new Pair("sprint", firstPoint.getRight().getRight())));
        }
    }

    private void startBuilding() {
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(mapCorner);
        calculateBuildingPath(true, true);
        checkpoints.add(0, new Pair(dumpStation.getLeft(), new Pair("dump", null)));
        state = State.Walking;
    }

    private boolean endBuilding() {
        if (!knownErrors.isEmpty()) {
            if (errorAction.get() == ErrorAction.ToggleOff) {
                workingInterval = new Pair<>(0, map.length-1);
                info("Found errors: ");
                for (int i = knownErrors.size()-1; i >= 0; i--) {
                    info("Pos: " + knownErrors.get(i).toShortString());
                }
                knownErrors.clear();
                checkpoints.add(new Pair(mc.player.getEntityPos(), new Pair("lineEnd", null)));
                state = State.Walking;
                warning("ErrorAction is ToggleOff: Stopping because of an error...");
                toggle();
                return false;
            }
            if (errorAction.get() == ErrorAction.Repair) {
                workingInterval = new Pair<>(0, map.length-1);
                info("Fixing errors: ");
                for (int i = knownErrors.size()-1; i >= 0; i--) {
                    BlockPos errorPos = knownErrors.get(i);
                    info("Pos: " + errorPos.toShortString());
                    checkpoints.add(new Pair(errorPos.toCenterPos(), new Pair("break", errorPos)));
                }
                checkpoints.add(new Pair(dumpStation.getLeft(), new Pair("dump", null)));
                for (int i = 0; i < knownErrors.size(); i++) {
                    String action = (i == knownErrors.size() - 1) ? "lineEnd" : "sprint";
                    BlockPos errorPos = knownErrors.get(i);
                    checkpoints.add(new Pair(errorPos.toCenterPos(), new Pair(action, null)));
                }
                knownErrors.clear();
                state = State.Walking;
                return true;
            }
        }
        info("Finished building map");
        state = State.Walking;
        workingInterval = trueInterval;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Pair<BlockPos, Vec3d> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        if (bestChest == null) return false;
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
        HashMap<Item, Integer> requiredItems = Utils.getRequiredItems(mapCorner, workingInterval, linesPerRun.get(), availableSlots.size(), map);
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        return invInformation.getLeft().get(0);
    }

    // MapPrinter Interface for Slave Logic

    public void setInterval(Pair<Integer, Integer> interval) {
        info("Set interval to: " + interval.getLeft() + " - " + interval.getRight());
        workingInterval = interval;
        trueInterval = interval;
    }

    public void addError(BlockPos relativeBlockPos) {
        BlockPos absoluteErrorPos = mapCorner.add(relativeBlockPos);
        if (!knownErrors.contains(absoluteErrorPos)) knownErrors.add(absoluteErrorPos);
    }

    public void pause() {
        if (!state.equals(CarpetPrinter.State.AwaitSlaveContinue)) {
            oldState = state;
            state = CarpetPrinter.State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
        }
    }

    public void start() {
        if (availableSlots.isEmpty() || state.equals(State.AwaitSlaveNextMap)) {
            state = State.AwaitNBTFile;
            return;
        }
        if (state.equals(State.AwaitSlaveContinue)) {
            state = oldState;
        }
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {}

    public void mineLine(int lines) {}

    public void slaveFinished(String slave) {}

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
        if (reset == null || cartographyTable == null || finishedMapChest == null || dumpStation == null || mapCorner == null || materialDict.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "carpet",
                reset,
                cartographyTable,
                finishedMapChest,
                mapMaterialChests,
                dumpStation,
                mapCorner,
                materialDict);
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
            State.SelectingReset,
            State.SelectingChests,
            State.SelectingFinishedMapChest,
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

            if (!data.type.equals("carpet")) {
                error("Config file is of type "+ data.type +" and not 'carpet'.");
                return;
            }
            if (data.reset == null || data.cartographyTable == null || data.finishedMapChest == null || data.dumpStation == null || data.mapCorner == null || data.materialDict.isEmpty()) {
                error("Config file is missing required data.");
                return;
            }
            this.reset = data.reset;
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStation = data.dumpStation;
            this.mapCorner = data.mapCorner;
            MapAreaCache.reset(mapCorner);
            this.materialDict = data.materialDict;
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

            //Remove any blocks that should be ignored
            List<Integer> toBeRemoved = new ArrayList<>();
            for (int key : blockPaletteDict.keySet()) {
                if (ignoredBlocks.get().contains(blockPaletteDict.get(key).getLeft())) toBeRemoved.add(key);
            }
            for (int key : toBeRemoved) blockPaletteDict.remove(key);

            NbtList blockList = (NbtList) nbt.get("blocks");
            map = Utils.generateMapArray(blockList, blockPaletteDict);

            info("Requirements: ");
            for (Pair<Block, Integer> p : blockPaletteDict.values()) {
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
                new File(configFolder, "carpet-printer-config.json").getAbsolutePath(),
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
                new File(configFolder, "carpet-printer-config.json").getAbsolutePath(),
                null,
                null,
                false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);

        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, false);
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
        event.renderer.box(mapCorner, color.get(), color.get(), ShapeMode.Lines, 0);
        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX() + 128, mapCorner.getY(), mapCorner.getZ() + 128, color.get(), color.get(), ShapeMode.Lines, 0);

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
            if (reset != null) {
                event.renderer.box(reset.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(reset.getRight().x - indicatorSize.get(), reset.getRight().y - indicatorSize.get(), reset.getRight().z - indicatorSize.get(), reset.getRight().getX() + indicatorSize.get(), reset.getRight().getY() + indicatorSize.get(), reset.getRight().getZ() + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
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

    // Enums

    private enum State {
        SelectingReset,
        SelectingChests,
        SelectingFinishedMapChest,
        SelectingDumpStation,
        SelectingTable,
        SelectingMapArea,
        AwaitRegisterResponse,
        AwaitRestockResponse,
        AwaitResetResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitCartographyResponse,
        AwaitBlockBreak,
        AwaitAreaClear,
        AwaitNBTFile,
        AwaitMasterAllBuilt,
        AwaitSlaveContinue,
        AwaitSlaveNextMap,
        Walking,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum ErrorAction {
        Ignore,
        ToggleOff,
        Reset,
        Repair
    }
}
