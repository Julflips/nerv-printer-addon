package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.modules.CarpetPrinter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SlaveSystem {

    public static int commandDelay = 0;
    public static String directMessageCommand = "w";
    public static ArrayList<String> finishedSlaves = new ArrayList<>();

    private static CarpetPrinter printerModule = null;
    private static int timeout = 0;
    private static ArrayList<String> slaves = new ArrayList<>();
    private static ArrayList<String> toBeSentMessages = new ArrayList<>();
    private static ArrayList<String> toBeConfirmedSlaves = new ArrayList<>();
    private static String master = null;

    public static void setupSlaveSystem(CarpetPrinter module, int delay, String dmCommand) {
        printerModule = module;
        commandDelay = delay;
        directMessageCommand = dmCommand;
        slaves.clear();
        toBeSentMessages.clear();
        toBeConfirmedSlaves.clear();
        finishedSlaves.clear();
        master = null;
    }

    public static void queueMasterDM(String message) {
        if (master != null) {
            queueDM(master, message);
        }
    }

    public static void queueDM(String recipient, String message) {
        toBeSentMessages.add(directMessageCommand + " " + recipient + " " + message);
    }

    public static boolean allSlavesFinished() {
        return finishedSlaves.size() == slaves.size();
    }

    public static boolean isSlave() {
        return master != null;
    }

    public static void sendToAllSlaves(String message) {
        for (String slave : slaves) {
            SlaveSystem.queueDM(slave, message);
        }
    }

    public static void generateIntervals(int totalSize) {
        int sectionSize = (int) Math.ceil((float) totalSize / (float) (slaves.size()+1));
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int end = totalSize-1; end >= 0 ; end -= sectionSize) {
            int start = Math.max(0, end - sectionSize + 1);
            intervals.add(new Pair<>(start, end));
        }
        Collections.reverse(intervals);

        printerModule.setInterval(intervals.remove((intervals.size()-1)/2));
        for (int i = 0; i < intervals.size(); i++) {
            String slave = slaves.get(i);
            SlaveSystem.queueDM(slave, "interval:" + intervals.get(i).getLeft() + ":" + intervals.get(i).getRight());
        }
    }

    public static void getSlaveUI(GuiTheme theme, WTable table) {
        table.add(theme.label("Multi-User: "));
        WButton startButton = table.add(theme.button("Register players in range")).widget();
        startButton.action = () -> registerSlaves();

        WButton pauseButton = table.add(theme.button("Pause all")).widget();
        pauseButton.action = () -> sendToAllSlaves("pause");

        WButton continueButton = table.add(theme.button("Continue all")).widget();
        continueButton.action = () -> sendToAllSlaves("continue");
        table.row();
    }

    private static void registerSlaves() {
        if (printerModule == null) {
            ChatUtils.warning("The module needs to be enabled to register new slaves.");
            return;
        }
        ArrayList<String> foundPlayers = new ArrayList<>();
        for(Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && !mc.player.equals(player)) {
                foundPlayers.add(player.getName().getString());
            }
        }
        if (foundPlayers.isEmpty()) {
            ChatUtils.warning("No players found in render distance.");
        }
        toBeConfirmedSlaves = foundPlayers;
        for (String slave : foundPlayers) {
            if (slaves.contains(slave)) continue;
            SlaveSystem.queueDM(slave, "register");
        }
    }

    private static boolean canSeePlayer(String playerName) {
        for(Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && player.getName().getString().equals(playerName)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (printerModule != null && event.packet instanceof GameMessageS2CPacket packet) {
            String content = packet.content().getString();
            String[] spaceSplit = content.split(" ");
            String[] colonSplit = content.replace(" ", "").split(":");
            if (spaceSplit.length < 2 || colonSplit.length < 2) return;
            String sender = spaceSplit[0];
            String command = colonSplit[1];
            if (sender == mc.player.getName().getString()) return;
            // Register
            if (command.equals("register") && master == null && toBeConfirmedSlaves.isEmpty()
                && slaves.isEmpty() && canSeePlayer(sender)) {
                master = sender;
                SlaveSystem.queueMasterDM("accept");
            }
            // Master to client message
            if (sender.equals(master)) {
                switch (command) {
                    case "interval":
                        if (colonSplit.length < 4) break;
                        Pair<Integer, Integer> interval = new Pair<>(Integer.valueOf(colonSplit[2]), Integer.valueOf(colonSplit[3]));
                        printerModule.setInterval(interval);
                        break;
                    case "start":
                        printerModule.startNextMap();
                        break;
                    case "remove":
                        printerModule.toggle();
                        break;
                    case "pause":
                        printerModule.pause();
                        break;
                    case "continue":
                        printerModule.resume();
                        break;
                }
            }
            // Client to master message
            if (slaves.contains(sender) || toBeConfirmedSlaves.contains(sender)) {
                switch (command) {
                    case "accept":
                        slaves.add(sender);
                        toBeConfirmedSlaves.remove(sender);
                        ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                        break;
                    case "finished":
                        finishedSlaves.add(sender);
                        break;
                    case "error":
                        if (colonSplit.length < 4) break;
                        BlockPos relativeErrorPos = new BlockPos(Integer.valueOf(colonSplit[2]), 0, Integer.valueOf(colonSplit[3]));
                        printerModule.addError(relativeErrorPos);
                        break;
                }
            }
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        if (timeout > 0) timeout--;
        if (!toBeSentMessages.isEmpty()) {
            if (timeout <= 0) {
                mc.getNetworkHandler().sendChatCommand(toBeSentMessages.remove(0));
                timeout = commandDelay;
            }
        }
    }
}
