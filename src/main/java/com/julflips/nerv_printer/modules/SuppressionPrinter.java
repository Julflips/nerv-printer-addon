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
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
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
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SuppressionPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgRender = settings.createGroup("Render");

    //General

    private final Setting<Integer> linesPerRun = sgGeneral.add(new IntSetting.Builder()
        .name("lines-per-run")
        .description("How many lines to place in parallel per run.")
        .defaultValue(2)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

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

    private final Setting<Double> minMiningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-mining-range")
        .description("The minimum distance to keep to blocks when mining.")
        .defaultValue(3)
        .min(0.5)
        .sliderRange(0.5, 3.5)
        .build()
    );

    private final Setting<List<Block>> startBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-blocks")
        .description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON)
        .build()
    );

    private final Setting<Block> fillerBlock = sgGeneral.add(new BlockSetting.Builder()
        .name("filler-block")
        .description("The block to use as a filler.")
        .defaultValue(Blocks.RESIN_BLOCK)
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

    private final Setting<Boolean> supportBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("support-blocks")
        .description("Place blocks with support. Can be disabled if there is a 2 block heigh ceiling or airplace allowed.")
        .defaultValue(false)
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

    private final Setting<Boolean> useDefaultConfigFile = sgGeneral.add(new BoolSetting.Builder()
        .name("use-default-config-file")
        .description("Load a config file when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    public final Setting<String> configFileName = sgGeneral.add(new StringSetting.Builder()
        .name("config-file-name")
        .description("The config file that is loaded  when the module is enabled.")
        .defaultValue("suppressed-config.json")
        .wide()
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> useDefaultConfigFile.get())
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

    private final Setting<Integer> mapLoadTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("map-load-timeout")
        .description("How many ticks to wait to update the map in hand.")
        .defaultValue(7)
        .min(0)
        .sliderRange(0, 20)
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

    private final Setting<Double> durabilityBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("durability-buffer")
        .description("The additional required durability for restocked mining tools on top of the predicted one (in %).")
        .defaultValue(0.2)
        .min(0)
        .sliderRange(0, 1)
        .build()
    );

    private final Setting<Integer> minDurability = sgAdvanced.add(new IntSetting.Builder()
        .name("min-durability")
        .description("The minimum absolute durability value allowed on a mining tools before exchanging.")
        .defaultValue(3)
        .min(0)
        .sliderRange(0, 50)
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

    private final Setting<Boolean> fixErrors = sgAdvanced.add(new BoolSetting.Builder()
        .name("fix-errors")
        .description("Fix errors by mining the misplaced blocks on the fly.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> snapToCheckpoints = sgAdvanced.add(new BoolSetting.Builder()
        .name("snap-to-checkpoints")
        .description("Snap to checkpoints when getting close.")
        .defaultValue(false)
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

    private final Setting<String> tcpIp = sgMultiUser.add(new StringSetting.Builder()
        .name("TCP-IP")
        .description("The loopback IP used by bot-to-bot TCP communication.")
        .defaultValue("127.0.0.1")
        .onChanged(value -> updateTcpAddress())
        .build()
    );

    private final Setting<Integer> tcpPort = sgMultiUser.add(new IntSetting.Builder()
        .name("TCP-port")
        .description("The loopback TCP port used by bot-to-bot communication.")
        .defaultValue(42069)
        .min(1)
        .max(65535)
        .sliderRange(1, 65535)
        .onChanged(value -> updateTcpAddress())
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

    private final Setting<Boolean> renderNextBlockPos = sgRender.add(new BoolSetting.Builder()
        .name("render-next-block-pos")
        .description("Highlights the block position of the current place/mining action.")
        .defaultValue(true)
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
        .defaultValue(0.13)
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
    int interactTimeout;
    int toBeSwappedSlot;
    int lastPlacedLine;
    int minedLines;
    int suppressedLines;
    long lastTickTime;
    boolean closeNextInvPacket;
    boolean workOnUpper;
    State state;
    State oldState;
    State debugPreviousState;
    Pair<Integer, Integer> workingInterval;                         // Interval the bot should work in 0-127
    Pair<BlockPos, Vec3d> cartographyTable;
    Pair<BlockPos, Vec3d> finishedMapChest;
    ArrayList<Pair<BlockPos, Vec3d>> mapMaterialChests;
    ArrayList<Pair<Vec3d, Pair<Float, Float>>> dumpStations;                    // Pos, Yaw, Pitch
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    BlockPos nextBlockPos;
    Item lastSwappedMaterial;
    InventoryS2CPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;        // Maps palette block id to the Minecraft block and amount
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>> materialDict;   // Maps block to the chest pos and the open position
    Set<ItemStack> toolSet;                                         // Set of all registered tool item stacks
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Integer> lowerMiningCommand;
    ArrayList<Integer> upperMiningCommand;
    ArrayList<Triple<Item, Integer, Integer>> restockList;          // Material, Stacks, Raw Amount
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3d, Pair<String, BlockPos>>> checkpoints;     // (GoalPos, (checkpointAction, targetBlock))
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    File mapFolder;
    File mapFile;
    BlockPos lowerMapCorner;
    BlockPos upperMapCorner;
    Block[][][] lowerMapLayer;
    Block[][][] upperMapLayer;

    public SuppressionPrinter() {
        super(Addon.CATEGORY, "suppression-printer", "Uses inplace suppression to build fullblock staircased maps.");
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
        lowerMiningCommand = new ArrayList<>();
        upperMiningCommand = new ArrayList<>();
        lowerMapCorner = null;
        upperMapCorner = null;
        lastInteractedChest = null;
        nextBlockPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        mapMaterialChests = new ArrayList<>();
        dumpStations = new ArrayList<>();
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        workOnUpper = false;
        timeoutTicks = 0;
        interactTimeout = 0;
        toBeSwappedSlot = -1;
        minedLines = 128;
        suppressedLines = -1;
        oldState = null;
        debugPreviousState = null;
        lowerMapLayer = new Block[128][129][2];
        upperMapLayer = new Block[128][129][2];

        setInterval(new Pair<>(0, 127));
        // Initialize Slave System settings
        SlaveSystem.setupSlaveSystem(this, tcpIp.get(), tcpPort.get());

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

        state = State.SelectingLowerMapArea;
        if (useDefaultConfigFile.get()) {
            File configFolder = new File(mapFolder, "_configs");
            if (!loadConfig(new File(configFolder, configFileName.get()))) {
                info("§aSelect the §bLower Map Building Area (128x128)§a. (Right-click anywhere on the floor)");
            }
        } else {
            info("§aSelect the §bLower Map Building Area (128x128)§a. (Right-click anywhere on the floor)");
        }
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof PlayerActionC2SPacket packet
            && packet.getAction() == PlayerActionC2SPacket.Action.DROP_ITEM) {
            dumpStations.add(new Pair<>(mc.player.getEntityPos(), new Pair<>(mc.player.getYaw(), mc.player.getPitch())));
            info("§aDump Station selected. You can register more or continue with the §bCartography Table.");
            return;
        }
        if (!(event.packet instanceof PlayerInteractBlockC2SPacket packet) || state == null) return;
        switch (state) {
            case SelectingLowerMapArea:
                BlockPos hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ() + 1);
                // Move Z down by one to include the north line as players are likely to use it for registration
                lowerMapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ - 1);
                MapAreaCache.reset(lowerMapCorner, true);
                state = State.SelectingUpperMapArea;
                info("§aLowerMap Area selected. Select the §bUpper Map Area.");
                return;
            case SelectingUpperMapArea:
                hitPos = packet.getBlockHitResult().getBlockPos().offset(packet.getBlockHitResult().getSide());
                adjustedX = Utils.getIntervalStart(hitPos.getX());
                adjustedZ = Utils.getIntervalStart(hitPos.getZ() + 1);
                // Move Z down by one to include the north line as players are likely to use it for registration
                upperMapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ - 1);
                if (upperMapCorner.getX() == lowerMapCorner.getX() &&
                    upperMapCorner.getZ() == lowerMapCorner.getZ() &&
                    upperMapCorner.getY() > lowerMapCorner.getY()) {
                    state = State.SelectingDumpStation;
                    info("§aUpper Map Area selected. Throw an item into each §bDump Station. " +
                        "§aInteract with the §bCartography Table §aafter you selected all.");
                }
                return;
            case SelectingDumpStation:
                BlockPos blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("§aCartography Table selected. Select the §bFinished Map Chest.");
                    state = State.SelectingFinishedMapChest;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getBlockHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof AbstractChestBlock) {
                    finishedMapChest = new Pair<>(blockPos, mc.player.getEntityPos());
                    info("§aFinished Map Chest selected. Select all §bMaterial-, Tool-, and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlocks.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getBlockHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlocks.get().contains(blockState.getBlock())) startBuilding();
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
                        info("§aRegistered §bMap Chest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.getEntityPos());
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
            ArrayList newChestList = Utils.saveAdd(oldList, tempChestPos, mc.player.getEntityPos());
            materialDict.put(foundItem, newChestList);
            state = State.SelectingChests;
            return;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse);
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
                    if (!stack.isEmpty() && (stack.getCount() == 64 || !stack.isStackable())) {
                        //info("Taking Stack of " + restockList.get(0).getLeft().getName().getString());
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
                            checkpoints.add(1, new Pair(getBestDumpStation().getLeft(), new Pair("calculateRefill", null)));
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
                // ToDo: Do mining for <4 slaves
                state = State.AwaitNBTFile;
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        long timeDifference = System.currentTimeMillis() - lastTickTime;
        int allowedPlacements = (int) Math.floor(timeDifference / (long) placeDelay.get());
        lastTickTime += allowedPlacements * placeDelay.get();

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: §a" + state);
        }

        for (int i = SlaveSystem.slaves.size() - 1; i >= SlaveSystem.slaves.size()-2 ; i--) {
            ArrayList<Integer> miningCommand = i % 2 == 1 ? lowerMiningCommand : upperMiningCommand;
            if (!miningCommand.isEmpty()) {
                String slave = SlaveSystem.slaves.get(i);
                if (SlaveSystem.finishedSlavesDict.get(slave)) {
                    int targetLine = miningCommand.removeFirst();
                    SlaveSystem.sendToSlave(slave, "mine:" + targetLine);
                    SlaveSystem.finishedSlavesDict.put(slave, false);
                    SlaveSystem.activeSlavesDict.put(slave, true);
                }
            }
        }

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

        if (timeoutTicks > 0) {
            if (mc.player.isOnGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
            return;
        }

        // Swap into Hotbar
        if (toBeSwappedSlot != -1) {
            swapIntoHotbar(toBeSwappedSlot);
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

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && nextBlockPos != null) {
            // Break block if miningPos is not null. Stop walking if further away than maxMiningRange
            if (MapAreaCache.getCachedBlockState(nextBlockPos).isAir()) {
                nextBlockPos = null;
                state = State.Mining;
            } else {
                mc.player.setPitch((float) Rotations.getPitch(nextBlockPos));
                BlockUtils.breakBlock(nextBlockPos, true);

                if (Math.abs(nextBlockPos.getZ() - mc.player.getZ()) <= minMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }

                if (state.equals(State.AwaitBlockBreak)) {
                    Utils.setForwardPressed(false);
                    return;
                }
            }
        }

        if (state.equals(State.AwaitSuppressionReady)) {
            if (SlaveSystem.mapCompletionState.isLineComplete(true, suppressedLines, false) &&
                SlaveSystem.mapCompletionState.isLineComplete(false, suppressedLines, true) &&
                SlaveSystem.mapCompletionState.isLineComplete(true, suppressedLines+1, true) &&
                SlaveSystem.mapCompletionState.isLineComplete(false, suppressedLines+1, true)) {

                suppressedLines++;
                if (suppressedLines > 0) {
                    // Suppress one line
                    suppressPath();
                } else {
                    // Only continue filling map on first iteration
                    loadFirstLinePath();
                }
            }
        }

        // Dump unnecessary items
        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                state = State.Walking;
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

        if (!(state.equals(State.Walking) || state.equals(State.Mining))) return;

        // Main Loop for Building & Mining
        Utils.setForwardPressed(true);
        Vec3d goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null)
                info("Reached: §a" + checkpointAction.getLeft());
            if (snapToCheckpoints.get()) mc.player.setPosition(goal.x, mc.player.getY(), goal.z);
            checkpoints.remove(0);

            switch (checkpointAction.getLeft()) {
                case "lineEnd":
                    boolean reachedNorthSide = goal.z == lowerMapCorner.north(2).toCenterPos().z;
                    buildPath(reachedNorthSide, false);
                    if (checkpoints.isEmpty() || goal.z == checkpoints.getFirst().getLeft().z) {
                        // Last line was completed without missing blocks
                        boolean placing = state.equals(State.Walking) ? true : false;
                        int newPlacedLine = Math.min(workingInterval.getRight()+1, lastPlacedLine + linesPerRun.get());
                        SlaveSystem.mapCompletionState.setInterval(lastPlacedLine, (newPlacedLine-1), placing, workOnUpper);
                        if (SlaveSystem.isSlave) {
                            SlaveSystem.sendMessageToMaster("placeStatus:" + lastPlacedLine + ":" + (newPlacedLine-1) + ":" + placing + ":" + workOnUpper);
                        }
                        lastPlacedLine = newPlacedLine;
                        // Remove finished line from workingInterval. Flip snake pattern
                        setInterval(new Pair<>(workingInterval.getLeft() + 2, workingInterval.getRight()));
                    }
                    break;
                case "mapMaterialChest":
                    state = State.AwaitMapChestResponse;
                    BlockPos mapMaterialChest = getBestChest(Items.CARTOGRAPHY_TABLE).getLeft();
                    interactWithBlock(mapMaterialChest);
                    return;
                case "fillMap":
                    state = State.AwaitSuppressionReady;
                    Utils.setForwardPressed(false);
                    mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()));
                    return;
                case "awaitSuppression":
                    Utils.setForwardPressed(false);
                    state = State.AwaitSuppressionReady;
                    if (suppressedLines < 0) return;
                    if (suppressedLines > 0) {
                        info("add lower mining command: " + (suppressedLines-1));
                        lowerMiningCommand.add(suppressedLines-1);
                    }
                    if (suppressedLines < lowerMapLayer.length / 2) {
                        info("add upper mining command: " + (suppressedLines));
                        upperMiningCommand.add(suppressedLines);
                    }
                    return;
                case "startSneak":
                    Utils.setSneakPressed(true);
                    break;
                case "stopSneak":
                    Utils.setForwardPressed(false);
                    Utils.setSneakPressed(false);
                    timeoutTicks = mapLoadTimeout.get();
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
                    Pair<Float, Float> throwingAngle = getBestDumpStation().getRight();
                    mc.player.setYaw(throwingAngle.getLeft());
                    mc.player.setPitch(throwingAngle.getRight());
                    return;
                case "calculateMiningRefill":
                    refillMiningInventory();
                    return;
                case "calculateRefill":
                    HashMap<Item, Integer> requiredItems = getRequiredItems();
                    Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(invInformation.getRight());
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getRight());
                    return;
                case "mineEnd":
                    state = State.StandBy;
                    Utils.setForwardPressed(false);
                    info("ToDo: Add check if everything was mined and item collection");
                    SlaveSystem.sendMessageToMaster("finished");
                    SlaveSystem.sendMessageToMaster("placeStatus:" + workingInterval.getLeft() + ":"
                        + workingInterval.getRight() + ":" + false + ":" + workOnUpper);
                    return;
                case "slaveFinished":
                    state = State.StandBy;
                    Utils.setForwardPressed(false);
                    SlaveSystem.sendMessageToMaster("finished");
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (state.equals(State.Walking)) {
                    // Done Building
                    info("Done building");
                    checkpoints.add(new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
                    checkpoints.add(new Pair(getBestDumpStation().getLeft(), new Pair("slaveFinished", null)));
                    if (!SlaveSystem.isSlave) {
                        // Master
                        fillMapPath();
                    }
                }
            }
            goal = checkpoints.get(0).getLeft();
        }

        // Set yaw rotation to goal
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ() + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        mc.player.setYaw((float) Rotations.getYaw(new Vec3d(goal.x, goal.y, lookZ)));

        String nextAction = checkpoints.get(0).getRight().getLeft();

        // Set sprint mode
        final List<String> noSprintActions = Arrays.asList("lineBegin", "lineEnd", "mineBegin", "mineEnd");
        if (noSprintActions.contains(nextAction) && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("lineBegin", "lineEnd", "sprint", "mineBegin", "mineEnd");
        if (!allowPlaceActions.contains(nextAction)) return;

        if (state.equals(State.Walking)) {
            nextBlockPos = getNextPlacementPos(goal.z >= mc.player.getZ());
        } else {
            nextBlockPos = getNextMiningPos();
        }

        if (nextBlockPos == null) return;

        if (state.equals(State.Walking)) {
            if (PlayerUtils.distanceTo(nextBlockPos.toCenterPos()) <= interactionRange.get()) {
                tryPlacingBlock();
            }
        } else {
            mc.player.setPitch((float) Rotations.getPitch(nextBlockPos));
            BlockState blockState = MapAreaCache.getCachedBlockState(nextBlockPos);
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockState);
            for (int slot : availableHotBarSlots) {
                if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
                ItemStack itemStack = mc.player.getInventory().getStack(slot);
                if (itemStack.getItem().equals(bestTool.getItem())) {
                    if (ToolUtils.getRemainingDamage(itemStack) < minDurability.get()) continue;
                    InvUtils.swap(slot, false);
                    BlockUtils.breakBlock(nextBlockPos, true);
                    state = State.Mining;
                    if (Math.abs(nextBlockPos.getZ() - mc.player.getZ()) <= minMiningRange.get()) {
                        state = State.AwaitBlockBreak;
                    }
                    return;
                }
            }
            warning("No tool with enough durability found to mine blocks");
        }
    }

    // Restocking

    private Pair<BlockPos, Vec3d> getBestChest(Item item) {
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
        ArrayList<Pair<BlockPos, Vec3d>> bestList = new ArrayList<>();
        // Get lowest/heighest chest (depending on layer) as 1. criteria
        for (Pair<BlockPos, Vec3d> p : list) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestList.isEmpty() || ((bestList.getFirst().getLeft().getY() <= p.getLeft().getY()) && workOnUpper)
                || ((bestList.getFirst().getLeft().getY() >= p.getLeft().getY()) && !workOnUpper)) {
                // Remove all previous results if new one is truly better
                if (!bestList.isEmpty() && bestList.getFirst().getLeft().getY() != p.getLeft().getY()) {
                    bestList.clear();
                }
                bestList.add(p);
            }
        }
        if (bestList.isEmpty()) {
            checkedChests.clear();
            return getBestChest(item);
        }
        // Get nearest chest as 2. criteria
        Vec3d bestPos = null;
        BlockPos bestChestPos = null;
        for (Pair<BlockPos, Vec3d> p : bestList) {
            //Skip chests that have already been checked
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        return new Pair(bestChestPos, bestPos);
    }

    private Pair<Vec3d, Pair<Float, Float>> getBestDumpStation() {
        Vec3d bestPos = null;
        Pair<Float, Float> throwAngle = null;
        for (int i = 0; i < dumpStations.size(); i++) {
            if (bestPos == null || PlayerUtils.distanceTo(dumpStations.get(i).getLeft()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = dumpStations.get(i).getLeft();
                throwAngle = dumpStations.get(i).getRight();
            }
        }
        return new Pair(bestPos, throwAngle);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        //Fills restockList with required build materials
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems();
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
        // Fills restockList mining tools for mining the complete layer
        restockList.clear();
        // Calculate total uses per tool
        HashMap<ItemStack, Integer> toolUseDict = new HashMap<>();
        forEachMapBlock((x, z, y, blockState, block) -> {
            Block futureBlock = getActiveMapLayer()[x][z][y];
            if (futureBlock == null) return true;
            BlockState futureBlockState = getActiveMapLayer()[x][z][y].getDefaultState();
            ItemStack bestTool = ToolUtils.getBestTool(toolSet, futureBlockState);
            if (bestTool == null) return true;
            if (toolUseDict.containsKey(bestTool)) {
                toolUseDict.put(bestTool, toolUseDict.get(bestTool) + 1);
            } else {
                toolUseDict.put(bestTool, 1);
            }
            return true;
        }, true);

        for (ItemStack itemStack : toolUseDict.keySet()) {
            // Fetch unbreaking level
            int unbreakingLevel = 0;
            for (var enchantment : EnchantmentHelper.getEnchantments(itemStack).getEnchantmentEntries()) {
                if (!enchantment.getKey().getKey().isPresent()) continue;
                if (enchantment.getKey().getKey().get().getValue().equals(Enchantments.UNBREAKING.getValue())) {
                    unbreakingLevel = enchantment.getIntValue();
                }
            }
            int rawUses = toolUseDict.get(itemStack);
            double adjustedUses = (float) rawUses / (float) (unbreakingLevel + 1) * durabilityBuffer.get();
            for (int slot : availableSlots) {
                ItemStack invTool = mc.player.getInventory().getStack(slot);
                if (invTool.getItem() == itemStack.getItem()) {
                    // ToDo: Take enchantments of inventory tools into account
                    adjustedUses = Math.max(0, adjustedUses - (ToolUtils.getRemainingDamage(invTool) - minDurability.get()));
                }
            }

            int itemsNeeded = (int) Math.ceil(adjustedUses / (float) itemStack.getMaxDamage());
            if (itemsNeeded == 0) continue;
            info("Restocking §a" + itemsNeeded + " " + itemStack.getItem().getName().getString() + " (" + rawUses + " uses)");
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), itemsNeeded, itemsNeeded));
        }
        checkpoints.add(new Pair<>(getActiveMapCorner().toCenterPos().add(0,-0.5f, -1), new Pair<>("slaveFinished", null)));
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

    private void tryPlacingBlock() {
        BlockPos relativePos = nextBlockPos.subtract(getActiveMapCorner());
        Item material = getActiveMapLayer()[relativePos.getX()][relativePos.getZ()][relativePos.getY()].asItem();
        //info("Placing " + material.getName().getString() + " at: " + relativePos.toShortString());
        //Check hot-bar slots
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getStack(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(nextBlockPos, Hand.MAIN_HAND, slot, rotatePlace.get(), 50, true, true, false);
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
                mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, 0);
                timeoutTicks = preSwapDelay.get();
                return;
            }
        }
        if (lastSwappedMaterial == material) return;      //Wait for swapped material
        if (debugPrints.get()) info("No " + material.getName().getString() + " found in inventory. Resetting...");
        mc.player.setVelocity(0, 0, 0);
        Vec3d pathCheckpoint = new Vec3d(mc.player.getX(), lowerMapCorner.getY(), lowerMapCorner.north().toCenterPos().getZ());
        checkpoints.add(0, new Pair(mc.player.getEntityPos(), new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));
        checkpoints.add(0, new Pair(getBestDumpStation().getLeft(), new Pair("calculateRefill", null)));
        checkpoints.add(0, new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
        checkpoints.add(0, new Pair(pathCheckpoint, new Pair("walkRestock", null)));
    }

    private BlockPos getNextPlacementPos(boolean movingSouth) {
        // Get next block in working interval to place/mine
        // ToDo: Add mining and suppression steps
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x += linesPerRun.get()) {
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                    for (int y = 0; y < lowerMapLayer[0][0].length; y++) {
                        int adjustedZ = !movingSouth ? lowerMapLayer[0].length-1-z : z;
                        BlockPos blockPos = getActiveMapCorner().add(adjustedX, y, adjustedZ);
                        BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                        // Check if block is not placed already, should not be placed at all, or is too far east
                        // On the last line, ignore the x check as the player is walking on the line
                        if (blockState.isAir() && getActiveMapLayer()[adjustedX][adjustedZ][y] != null
                            && (mc.player.getX() > blockPos.toCenterPos().getX() + 0.5f
                            || (mc.player.getBlockX() - getActiveMapCorner().getX() >= workingInterval.getRight()
                            && !movingSouth &&  mc.player.getZ() + 0.3f < blockPos.toCenterPos().getZ()))) {
                            return blockPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private BlockPos getNextMiningPos() {
        // Get next block in working interval to place/mine
        // ToDo: Add mining and suppression steps
        boolean flippedZ = false;
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x += 2) {
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < 2; lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                    for (int y = lowerMapLayer[0][0].length-1; y >= 0; y--) {
                        int adjustedZ = flippedZ ? lowerMapLayer[0].length-1-z : z;
                        BlockPos blockPos = getActiveMapCorner().add(adjustedX, y, adjustedZ);
                        BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                        if (!blockState.isAir()) {
                            return blockPos;
                        }
                    }
                }
            }
            flippedZ = !flippedZ;
        }
        return null;
    }

    // Path and Building Management

    private void buildPath(boolean startNorthSide, boolean sprintFirst) {
        info("buildpath | northside: " + startNorthSide + " | sprint: " + sprintFirst);
        // Iterate over map and skip completed lines. Player has to be able to see the complete map area
        // Fills checkpoints list
        boolean northToSouth = startNorthSide;
        boolean evenIteration = false;
        boolean isLastLine = false;
        checkpoints.clear();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x += linesPerRun.get()) {
            // Last line hast to be south-to-north to not get stuck
            if (!Utils.isInInterval(workingInterval, x+linesPerRun.get()) && northToSouth) {
                northToSouth = false;
                isLastLine= true;
            } else {
                evenIteration = ! evenIteration;
            }

            boolean lineFinished = true;
            int firstPlacedZ = 128;
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                int adjustedX = x + lineBonus;
                if (!Utils.isInInterval(workingInterval, adjustedX)) break;
                for (int z = 0; z < upperMapLayer[0].length; z++) {
                    int adjustedZ = northToSouth ? lowerMapLayer[0].length - z - 1 : z;
                    for (int y = 0; y < upperMapLayer[0][0].length; y++) {
                        BlockState blockState = MapAreaCache.getCachedBlockState(getActiveMapCorner().add(adjustedX, y, adjustedZ));
                        if (blockState.isAir() && getActiveMapLayer()[adjustedX][adjustedZ][y] != null) {
                            //If there is a replaceable block and not an ignored block type at the position. Mark the line as not done
                            lineFinished = false;
                        }
                        if (!blockState.isAir()) firstPlacedZ = adjustedZ-1;
                    }
                    if (!lineFinished && firstPlacedZ != 128) break;
                }
            }
            if (lineFinished) {
                continue;
            }

            Vec3d cp1 = getActiveMapCorner().toCenterPos().add(x + linesPerRun.get(), -0.5f, -2);
            float cp2Z = isLastLine ? firstPlacedZ + 0.3f : 128 + 0.3f;
            Vec3d cp2 = getActiveMapCorner().toCenterPos().add(x + linesPerRun.get(), -0.5f, cp2Z);

            if (northToSouth) {
                checkpoints.add(new Pair(cp1, new Pair("lineBegin", null)));
                checkpoints.add(new Pair(cp2, new Pair("lineEnd", null)));
            } else {
                checkpoints.add(new Pair(cp2, new Pair("lineBegin", null)));
                checkpoints.add(new Pair(cp1, new Pair("lineEnd", null)));
            }
            northToSouth = !northToSouth;
        }

        if (checkpoints.size() >= 2) {
            // Move last line west by 1 to not walk into blocks placed by others
            Vec3d lastEnd = checkpoints.removeLast().getLeft();
            Vec3d lastBegin = checkpoints.removeLast().getLeft();
            double maxX = lowerMapCorner.toCenterPos().x + workingInterval.getRight();
            checkpoints.add(new Pair(new Vec3d(Math.min(lastBegin.x, maxX), lastBegin.y, lastBegin.z), new Pair("lineBegin", null)));
            checkpoints.add(new Pair(new Vec3d(Math.min(lastEnd.x, maxX), lastEnd.y, lastEnd.z), new Pair("lineEnd", null)));
            if (sprintFirst) {
                // Set the first checkpoint to sprint
                Vec3d firstVec3 = checkpoints.removeFirst().getLeft();
                checkpoints.add(0, new Pair(firstVec3, new Pair("sprint", null)));
            }
        }
    }

    private void minePath() {
        checkpoints.clear();
        Vec3d cp1 = getActiveMapCorner().toCenterPos().add(workingInterval.getRight()-0.5f, -0.5f, -1);
        Vec3d cp2 = getActiveMapCorner().toCenterPos().add(workingInterval.getRight()-0.5f, -0.5f, lowerMapLayer[0].length-1);
        if (mc.player.getZ() < lowerMapCorner.getZ() + lowerMapLayer[0].length / 2) {
            checkpoints.add(new Pair(cp1, new Pair("mineBegin", null)));
            checkpoints.add(new Pair(cp2, new Pair("mineEnd", null)));
        } else {
            checkpoints.add(new Pair(cp2, new Pair("mineBegin", null)));
            checkpoints.add(new Pair(cp1, new Pair("mineEnd", null)));
        }
        for (Pair<Vec3d, Pair<String, BlockPos>> checkpoint : checkpoints) {
            info("Mining checkpoint: " + checkpoint.getLeft());
        }
    }

    private void fillMapPath() {
        state = State.Walking;
        setInterval(new Pair<>(0, -1));
        checkpoints.add(new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
        Pair<BlockPos, Vec3d> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        checkpoints.add(new Pair(bestChest.getRight(), new Pair("mapMaterialChest", bestChest.getLeft())));
        checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(129,-1,-2), new Pair("sprint", null)));
        checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(129,-3.5,1), new Pair("sprint", null)));
        checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(125,-3.5,116), new Pair("fillMap", null)));
    }

    private void loadFirstLinePath() {
        checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(125,-3.5,12), new Pair("sprint", null)));
        checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(128,-3.5,15), new Pair("awaitSuppression", null)));
        state = State.Walking;
    }

    private void suppressPath() {
        info("suppressionPath with " + suppressedLines);
        state = State.Walking;
        int xOffset = 125 + suppressedLines*2;
        List<Integer> suppressionCheckpoints = Arrays.asList(15, 41, 65, 89, 113);

        if (suppressedLines <= 64) {
            // When player is south of the center, walk checkpoints south-to-north, otherwise north-to-south
            if (mc.player.getZ() > lowerMapCorner.getZ() + lowerMapLayer[0].length/2) {
                suppressionCheckpoints = suppressionCheckpoints.reversed();
            }
            // Generate Suppression Path
            for (Integer suppressionCheckpoint : suppressionCheckpoints) {
                Vec3d entry = lowerMapCorner.toCenterPos().add(xOffset + 1.4f,-3.5,suppressionCheckpoint);
                checkpoints.add(new Pair(entry, new Pair("startSneak", null)));
                checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(xOffset - 0.2f,-3.5, suppressionCheckpoint), new Pair("stopSneak", null)));
                checkpoints.add(new Pair(entry, new Pair("sprint", null)));
            }
            // Go to next suppression line
            checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(xOffset + 2,-3.5, suppressionCheckpoints.getLast()), new Pair("awaitSuppression", null)));
        } else {
            // Switch to something that is not the filled map in hotbar to not destroy the map
            FindItemResult mapItem = InvUtils.findInHotbar(Items.FILLED_MAP);
            int targetSlot = mapItem.found() ? (mapItem.slot()+1) % 9 : 8;
            mc.player.getInventory().setSelectedSlot(targetSlot);
            suppressedLines = -1;
            // Finished Suppression. Lock the finished map
            checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(129,-3.5,suppressionCheckpoints.getFirst()), new Pair("sprint", null)));
            checkpoints.add(new Pair(lowerMapCorner.toCenterPos().add(129,-1,-2), new Pair("sprint", null)));
            checkpoints.add(new Pair(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
        }
    }

    private void initialSetup() {
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
        if (fillerBlock.get() == null) {
            warning("Filler-block setting must be set!");
            return;
        }
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(getActiveMapCorner(), true);
    }

    private void startBuilding() {
        initialSetup();
        if (debugPrints.get()) info("§aStart building map");

        if (!SlaveSystem.isSlave) {
            if (SlaveSystem.slaves.isEmpty()) {
                error("Need at least one slave to work on upper platform!");
                toggle();
                return;
            }

            if (SlaveSystem.slaves.size() > 4) {
                warning("Only 2-5 accounts are supported. More will not result in a speed up.");
                // Remove excess Slaves
                ArrayList<String> toBeRemoved = new ArrayList<>();
                for (int i = 5; i < SlaveSystem.slaves.size(); i++) {toBeRemoved.add(SlaveSystem.slaves.get(i));}
                for (String slave : toBeRemoved) {SlaveSystem.removeSlave(slave);}
            }

            // Slave 1: Placing upper layer
            SlaveSystem.sendToSlave(SlaveSystem.slaves.get(0), "interval:" + 0 + ":" + (lowerMapLayer.length-1));
            SlaveSystem.sendToSlave(SlaveSystem.slaves.get(0), "start");

            // Slave 3-4: Optional mining in parallel to placing (3. upper, 4. lower)
            for (int i = 3; i <= 4 ; i++) {
                if (SlaveSystem.slaves.size() >= i) {
                    // Prepare mining slaves
                    String slave = SlaveSystem.slaves.get(i-1);
                    SlaveSystem.sendToSlave(slave, "interval:" + -1 + ":" + -1);
                    SlaveSystem.sendToSlave(slave, "mine:"+ -1);
                }
            }

            // Slave 2 + Master: Placing lower layer
            if (SlaveSystem.slaves.size() >= 2) {
                // 2-3 slaves -> Master will place half the map
                // 4+ slaves -> Master does not place anything
                int masterIntervalSize = SlaveSystem.slaves.size() >= 4 ? 0 : lowerMapLayer.length/2;
                String slave = SlaveSystem.slaves.get(1);
                SlaveSystem.sendToSlave(slave, "interval:" + masterIntervalSize + ":" + (lowerMapLayer.length-1));
                SlaveSystem.sendToSlave(slave, "start");
                if (masterIntervalSize == 0) {
                    fillMapPath();
                    return;
                } else {
                    setInterval(new Pair<>(0, masterIntervalSize - 1));
                }
            } else {
                setInterval(new Pair<>(0, (lowerMapLayer.length-1)));
            }
        }
        buildPath(true, true);
        checkpoints.add(0, new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
        checkpoints.add(1, new Pair(getBestDumpStation().getLeft(), new Pair("calculateRefill", null)));
        state = State.Walking;
    }

    private BlockPos getActiveMapCorner() {
        return workOnUpper ? upperMapCorner : lowerMapCorner;
    }

    private Block[][][] getActiveMapLayer() {
        return workOnUpper ? upperMapLayer : lowerMapLayer;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        if (debugPrints.get()) info("Inventory slots available for building: " + availableSlots);
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
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        for (int slot: invInformation.getLeft()) {
            ItemStack itemStack = mc.player.getInventory().getStack(slot);
            if (ToolUtils.isTool(itemStack)) {
                if (ToolUtils.getRemainingDamage(itemStack) <= minDurability.get()) {
                    return slot;
                }
            } else {
                return slot;
            }
        }
        return -1;
    }

    private HashMap<Item, Integer> getRequiredItems() {
        //Calculate the next items to restock
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        forEachMapBlock((x, z, y, blockState, block) -> {
            if (blockState.isAir() && getActiveMapLayer()[x][z][y] != null) {
                // ChatUtils.info("Add material for: " + mapCorner.add(x, y, z).toShortString());
                Item material = block.asItem();
                if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                requiredItems.put(material, requiredItems.get(material) + 1);
                // Check if the item fits into inventory. If not, undo the last increment and return
                if (Utils.stacksRequired(requiredItems.values()) > availableSlots.size()) {
                    requiredItems.put(material, requiredItems.get(material) - 1);
                    return false;
                }
            }
            return true;
        });
        return requiredItems;
    }

    private void swapIntoHotbar(int slot) {
        Map<Item, Integer> itemSlot = new HashMap<>();
        Map<Item, Integer> itemDistance = new HashMap<>();
        Map<Item, Integer> itemFrequency = new HashMap<>();

        int targetSlot = availableHotBarSlots.get(0);

        // Scan hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemSlot.put(item, hotbarSlot);
                itemDistance.put(item, -1); // -1 = never used
                itemFrequency.put(item, 0);
            } else {
                targetSlot = hotbarSlot;
                break;
            }
        }

        // PRIORITY 1: empty slot → instant choice
        if (mc.player.getInventory().getStack(targetSlot).isEmpty()) {
            Utils.performSwap(slot, targetSlot);
            return;
        }

        // Get blocks until next use of items in hotbar
        AtomicReference<Integer> blockCounter = new AtomicReference<>(0);
        forEachMapBlock((x, z, y, blockState, block) -> {
            if (!Utils.isInInterval(workingInterval, x)) return true;
            blockCounter.set(blockCounter.get() + 1);

            if (blockState.isAir() || block == null) return true;
            Item item = block.asItem();

            if (itemDistance.containsKey(item) &&
                itemDistance.get(item) == -1) {
                itemDistance.put(item, blockCounter.get());
            }
            return true;
        });

        // Count frequency of items in hotbar
        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getStack(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemFrequency.put(item, itemFrequency.get(item) + 1);
            }
        }

        // Choose best candidate
        Item bestItem = null;
        int bestDistance = -2; // lower than -1
        int bestFrequency = -1;

        for (Item item : itemSlot.keySet()) {
            int distance = itemDistance.get(item); // -1 = never used
            int frequency = itemFrequency.get(item);

            boolean better = false;

            // PRIORITY 2: never used (-1)
            if (distance == -1 && bestDistance != -1) {
                better = true;
            }
            // PRIORITY 3: hotbar frequency
            else if (frequency > bestFrequency) {
                better = true;
            }
            // PRIORITY 4: distance to next use
            else if (frequency == bestFrequency && distance > bestDistance && bestDistance != -1) {
                better = true;
            }

            if (better) {
                bestItem = item;
                bestDistance = distance;
                bestFrequency = frequency;
            }
        }

        if (bestItem != null) {
            targetSlot = itemSlot.get(bestItem);
        }

        Utils.performSwap(slot, targetSlot);
    }

    // MapPrinter Interface for Slave Logic

    public void setLayer(boolean isUpper) {
        workOnUpper = isUpper;
    }

    public void setInterval(Pair<Integer, Integer> interval) {
        if (debugPrints.get()) info("set interval to " + interval.getLeft() + " " + interval.getRight());
        lastPlacedLine = interval.getLeft();
        workingInterval = interval;
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
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {}

    public void slaveFinished(String slave) {}

    public void mineLine(int line) {
        if (line < 0) {
            initialSetup();
            if (!prepareNextMapFile()) return;
            state = State.Walking;
            checkpoints.add(0, new Pair(getBestDumpStation().getLeft(), new Pair("dump", null)));
            checkpoints.add(1, new Pair(getBestDumpStation().getLeft(), new Pair("calculateMiningRefill", null)));
            return;
        }
        setInterval(new Pair<>(Math.max(workingInterval.getLeft(), 0), line*2 + 1));
        minePath();
        state = State.Mining;
    }

    private void updateTcpAddress() {
        if (mc == null || mc.world == null || !isActive()) return;
        if (tcpIp != null && tcpPort != null) {
            SlaveSystem.setTcpAddress(tcpIp.get(), tcpPort.get());
        }
    }

    public void addError(BlockPos relativeBlockPos) {}

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
        if (cartographyTable == null || finishedMapChest == null || dumpStations == null || lowerMapCorner == null
            || upperMapCorner == null || materialDict.isEmpty() || toolSet.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(),
                "suppressed",
                cartographyTable,
                finishedMapChest,
                mapMaterialChests,
                dumpStations,
                lowerMapCorner,
                upperMapCorner,
                materialDict,
                toolSet);
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully saved config to: ").formatted(Formatting.GRAY).append(configText));
        } catch (IOException e) {
            error("Failed to create config file.");
        }
    }

    private boolean loadConfig(File configFile) {
        if (configFile == null || !configFile.exists() || state == null) {
            warning("Could not find config file.");
            return false;
        }
        List<State> allowedStates = List.of(
            State.SelectingChests,
            State.SelectingFinishedMapChest,
            State.SelectingDumpStation,
            State.SelectingTable,
            State.SelectingLowerMapArea,
            State.SelectingUpperMapArea,
            State.AwaitRegisterResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return false;
        }

        try {
            ConfigDeserializer.ConfigData data =
                ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("suppressed")) {
                error("Config file is of type " + data.type + " and not 'suppressed'.");
                return false;
            }
            if (data.cartographyTable == null || data.finishedMapChest == null || data.dumpStations == null || data.mapCorner == null
                || data.upperMapCorner == null || data.materialDict.isEmpty() || toolSet == null) {
                error("Config file is missing required data.");
                return false;
            }
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStations = data.dumpStations;
            this.lowerMapCorner = data.mapCorner;
            this.upperMapCorner = data.upperMapCorner;
            MapAreaCache.reset(lowerMapCorner, true);
            this.materialDict = data.materialDict;
            this.toolSet = data.toolSet;
            Text configText = Text.literal(configFile.getName())
                .styled(style -> style
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath().toString()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Open config")))
                    .withUnderline(true));
            info(Text.literal("Successfully loaded config: ").formatted(Formatting.GRAY).append(configText));
            info("§aInteract with the §bStart Block §ato start printing.");
            state = State.SelectingChests;
        } catch (IOException e) {
            error("Failed to read config file.");
        }
        return true;
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());

        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("§aAll nbt files finished");
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
            generateSuppressionLayers(blockList, blockPaletteDict);
            return true;
        } catch (Exception e) {
            warning("Failed to load NBT file: " + e.getMessage());
            e.printStackTrace();
            toggle();
            return false;
        }
    }

    private void generateSuppressionLayers(NbtList blockList, HashMap<Integer, Pair<Block, Integer>> blockPaletteDict) {
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
        }
        // Generate HeightDiffArray
        HeightDiff[][] heightDiffArray = new HeightDiff[128][128];
        for (int x = 0; x < absoluteHeightMap.length; x++) {
            for (int z = absoluteHeightMap[x].length-1; z > 0; z--) {
                int predecessorY = absoluteHeightMap[x][z - 1].getRight();
                int currentY = absoluteHeightMap[x][z].getRight();
                if (predecessorY > currentY) {
                    heightDiffArray[x][z-1] = HeightDiff.Up;
                } else if (predecessorY < currentY) {
                    heightDiffArray[x][z-1] = HeightDiff.Down;
                } else {
                    heightDiffArray[x][z-1] = HeightDiff.Even;
                }
            }
        }

        lowerMapLayer = new Block[128][129][2];
        upperMapLayer = new Block[128][129][2];
        boolean suppressed = false;
        for (int x = 0; x < heightDiffArray.length; x++) {
            int z = heightDiffArray[x].length-1;
            if (suppressed) {
                HeightDiff currentDiff = heightDiffArray[x][z];
                switch (currentDiff) {
                    case Up -> {
                        upperMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getLeft();
                        upperMapLayer[x][z][1] = fillerBlock.get();
                        if (supportBlocks.get()) upperMapLayer[x][z][0] = fillerBlock.get();
                    }
                    case Even -> {
                        lowerMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getLeft();
                    }
                    case Down -> {
                        lowerMapLayer[x][z+1][1] = absoluteHeightMap[x][z+1].getLeft();
                        if (supportBlocks.get()) lowerMapLayer[x][z+1][0] = fillerBlock.get();
                    }
                }
                z = z - 1;
            }
            suppressed = !suppressed;
            while (z >= 0) {
                lowerMapLayer[x][z+1][0] = absoluteHeightMap[x][z+1].getLeft();
                HeightDiff currentDiff = heightDiffArray[x][z];
                // Default values for north most row
                HeightDiff nextDiff = HeightDiff.Down;
                Block nextMaterial = fillerBlock.get();
                // Fetch info about next suppressed block if there is one
                if (z > 0) {
                    nextDiff = heightDiffArray[x][z-1];
                    nextMaterial = absoluteHeightMap[x][z].getLeft();
                }
                switch (currentDiff) {
                    case Up -> {
                        switch (nextDiff) {
                            case Up -> {
                                lowerMapLayer[x][z][1] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) {
                                    lowerMapLayer[x][z][0] = fillerBlock.get();
                                    upperMapLayer[x][z-1][0] = fillerBlock.get();
                                }
                            }
                            case Even -> {
                                lowerMapLayer[x][z][1] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][0] = fillerBlock.get();
                                if (supportBlocks.get()) lowerMapLayer[x][z][0] = fillerBlock.get();
                            }
                            case Down -> {
                                lowerMapLayer[x][z][1] = nextMaterial;
                                if (supportBlocks.get()) lowerMapLayer[x][z][0] = fillerBlock.get();
                            }
                        }
                    }
                    case Even -> {
                        switch (nextDiff) {
                            case Up -> {
                                lowerMapLayer[x][z][0] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Even -> {
                                lowerMapLayer[x][z][0] = nextMaterial;
                            }
                            case Down -> {
                                lowerMapLayer[x][z][0] = fillerBlock.get();
                                upperMapLayer[x][z][0] = nextMaterial;
                            }
                        }
                    }
                    case Down -> {
                        switch (nextDiff) {
                            case Up -> {
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][1] = fillerBlock.get();
                                if (supportBlocks.get()) upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Even -> {
                                upperMapLayer[x][z][0] = nextMaterial;
                                upperMapLayer[x][z-1][0] = fillerBlock.get();
                            }
                            case Down -> upperMapLayer[x][z][0] = nextMaterial;
                        }
                    }
                }
                z-=2;
            }
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
                new File(configFolder, "suppressed-config.json").getAbsolutePath(),
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
                new File(configFolder, "suppressed-config.json").getAbsolutePath(),
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
        if (lowerMapCorner == null || !render.get()) return;
        event.renderer.box(lowerMapCorner.getX(), lowerMapCorner.getY(), lowerMapCorner.getZ(), lowerMapCorner.getX() + upperMapLayer.length, lowerMapCorner.getY(), lowerMapCorner.getZ() + upperMapLayer[0].length, color.get(), color.get(), ShapeMode.Lines, 0);
        if (upperMapCorner == null) return;
        event.renderer.box(upperMapCorner.getX(), upperMapCorner.getY(), upperMapCorner.getZ(), upperMapCorner.getX() + upperMapLayer.length, upperMapCorner.getY(), upperMapCorner.getZ() + upperMapLayer[0].length, color.get(), color.get(), ShapeMode.Lines, 0);

        if (renderNextBlockPos.get() && nextBlockPos != null) {
            event.renderer.box(nextBlockPos, color.get(), color.get(), ShapeMode.Lines, 0);
        }

        if (renderMap.get() && !(state.equals(State.Mining) || state.equals(State.AwaitBlockBreak))) {
            if (upperMapLayer != null && lowerMapLayer != null) {
                for (int x = 0; x < lowerMapLayer.length; x++) {
                    for (int z = 0; z < lowerMapLayer[x].length; z++) {
                        for (int y = 0; y < lowerMapLayer[x][z].length; y++) {
                            Block material = lowerMapLayer[x][z][y];
                            BlockPos renderPos = new BlockPos(lowerMapCorner.getX() + x, lowerMapCorner.getY() + y, lowerMapCorner.getZ() + z);
                            if (material != null && (MapAreaCache.getCachedBlockState(renderPos) == null || MapAreaCache.getCachedBlockState(renderPos).isAir())) {
                                Color renderColor = color.get();
                                if (material.equals(fillerBlock.get())) renderColor = Color.BLUE;
                                event.renderer.box(renderPos, renderColor, renderColor, ShapeMode.Lines, 0);
                            }
                        }
                    }
                }
                for (int x = 0; x < upperMapLayer.length; x++) {
                    for (int z = 0; z < upperMapLayer[x].length; z++) {
                        for (int y = 0; y < upperMapLayer[x][z].length; y++) {
                            Block material = upperMapLayer[x][z][y];
                            BlockPos renderPos = new BlockPos(upperMapCorner.getX() + x, upperMapCorner.getY() + y, upperMapCorner.getZ() + z);
                            if (material != null && (MapAreaCache.getCachedBlockState(renderPos) == null || MapAreaCache.getCachedBlockState(renderPos).isAir())) {
                                Color renderColor = color.get();
                                if (material.equals(fillerBlock.get())) renderColor = Color.BLUE;
                                event.renderer.box(renderPos, renderColor, renderColor, ShapeMode.Lines, 0);
                            }
                        }
                    }
                }
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
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.x + indicatorSize.get(), cp.y + indicatorSize.get(), cp.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x - indicatorSize.get(), cartographyTable.getRight().y - indicatorSize.get(), cartographyTable.getRight().z - indicatorSize.get(), cartographyTable.getRight().x + indicatorSize.get(), cartographyTable.getRight().y + indicatorSize.get(), cartographyTable.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x - indicatorSize.get(), finishedMapChest.getRight().y - indicatorSize.get(), finishedMapChest.getRight().z - indicatorSize.get(), finishedMapChest.getRight().x + indicatorSize.get(), finishedMapChest.getRight().y + indicatorSize.get(), finishedMapChest.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStations != null) {
                for (int i = 0; i < dumpStations.size(); i++) {
                    Vec3d pos = dumpStations.get(i).getLeft();
                    event.renderer.box(pos.x - indicatorSize.get(), pos.y - indicatorSize.get(), pos.z - indicatorSize.get(), pos.x + indicatorSize.get(), pos.y + indicatorSize.get(), pos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
                }
            }
        }
    }

    private void forEachMapBlock(MapBlockAction action) {
        forEachMapBlock(action, false);
    }

    private void forEachMapBlock(MapBlockAction action, boolean completeMap) {
        int leftX = completeMap ? 0 : workingInterval.getLeft();
        int rightX = completeMap ? lowerMapLayer.length-1 : workingInterval.getRight();
        for (int x = leftX; x <= rightX; x += linesPerRun.get()) {
            for (int z = 0; z < lowerMapLayer[0].length; z++) {
                for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                    int adjustedX = x + lineBonus;
                    if (!Utils.isInInterval(new Pair<>(leftX, rightX), adjustedX)) break;

                    for (int y = 0; y < lowerMapLayer[adjustedX][z].length; y++) {
                        Block block = getActiveMapLayer()[adjustedX][z][y];
                        BlockPos pos = getActiveMapCorner().add(adjustedX, y, z);
                        BlockState state = MapAreaCache.getCachedBlockState(pos);

                        if (!action.accept(adjustedX, z, y, state, block)) {
                            // Stop future iterations if false is returned
                            return;
                        }
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface MapBlockAction {
        boolean accept(int x, int z, int y,BlockState state, Block desiredBlock);
    }

    private enum State {
        SelectingLowerMapArea,
        SelectingUpperMapArea,
        SelectingTable,
        SelectingDumpStation,
        SelectingFinishedMapChest,
        SelectingChests,
        AwaitRegisterResponse,
        AwaitRestockResponse,
        AwaitMapChestResponse,
        AwaitFinishedMapChestResponse,
        AwaitCartographyResponse,
        AwaitNBTFile,
        AwaitBlockBreak,
        AwaitSuppressionReady,
        AwaitMasterAllCleared,
        AwaitSlaveContinue,
        StandBy,
        Walking,
        Mining,
        Dumping
    }

    private enum SprintMode {
        Off,
        NotPlacing,
        Always
    }

    private enum HeightDiff {
        Up,
        Even,
        Down
    }
}
