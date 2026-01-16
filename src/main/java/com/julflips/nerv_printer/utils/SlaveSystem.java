package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.modules.CarpetPrinter;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SlaveSystem {

    public static int commandDelay = 0;
    public static String directMessageCommand = "w";
    public static ArrayList<String> slaves = new ArrayList<>();
    public static HashMap<String, Boolean> activeSlavesDict = new HashMap<>();
    public static SlaveTableController tableController = null;

    private static CarpetPrinter printerModule = null;
    private static int timeout = 0;
    private static HashMap<String, Boolean> finishedSlavesDict = new HashMap<>();
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
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
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
        for (String slave : finishedSlavesDict.keySet()) {
            if (!finishedSlavesDict.get(slave)) return false;
        }
        return true;
    }

    public static void setAllSlavesUnfinished() {
        for (String slave : finishedSlavesDict.keySet()) {
            finishedSlavesDict.put(slave, false);
        }
    }

    public static boolean isSlave() {
        return master != null;
    }

    public static void sendToAllSlaves(String message) {
        for (String slave : slaves) {
            SlaveSystem.queueDM(slave, message);
        }
    }

    public static void startAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            if (!activeSlavesDict.get(slave)) {
                queueDM(slave, "start");
                activeSlavesDict.put(slave, true);
            }
        }
        if (!printerModule.isActive()) printerModule.toggle();
    }

    public static void pauseAllSlaves() {
        sendToAllSlaves("pause");
        for (String slave : activeSlavesDict.keySet()) {
            activeSlavesDict.put(slave, false);
        }
        if (printerModule.isActive()) printerModule.toggle();
    }

    public static void generateIntervals() {
        int sectionSize = (int) Math.ceil((float) 128 / (float) (slaves.size()+1));
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int end = 127; end >= 0 ; end -= sectionSize) {
            int start = Math.max(0, end - sectionSize + 1);
            intervals.add(new Pair<>(start, end));
        }
        Collections.reverse(intervals);

        printerModule.setInterval(intervals.remove((intervals.size()-1)/2));

        // Remove all previously queued interval messages
        ArrayList<String> toBeRemoved = new ArrayList<>();
        for (String message : toBeSentMessages) {
            if (message.startsWith("interval")) toBeRemoved.add(message);
        }
        toBeRemoved.forEach((message) -> toBeSentMessages.remove(message));

        for (int i = 0; i < intervals.size(); i++) {
            String slave = slaves.get(i);
            SlaveSystem.queueDM(slave, "interval:" + intervals.get(i).getLeft() + ":" + intervals.get(i).getRight());
        }
    }

    public static void registerSlaves() {
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

    public static void removeSlave(String slave) {
        slaves.remove(slave);
        activeSlavesDict.remove(slave);
        finishedSlavesDict.remove(slave);
        queueDM(slave, "remove");
    }

    public static boolean canSeePlayer(String playerName) {
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
                    case "pause":
                        printerModule.pause();
                        break;
                    case "start":
                        printerModule.start();
                        break;
                    case "remove":
                        master = null;
                        printerModule.toggle();
                        break;
                }
            }
            // Client to master message
            if (slaves.contains(sender) || toBeConfirmedSlaves.contains(sender)) {
                switch (command) {
                    case "accept":
                        slaves.add(sender);
                        finishedSlavesDict.put(sender, false);
                        activeSlavesDict.put(sender, false);
                        toBeConfirmedSlaves.remove(sender);
                        ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                        generateIntervals();
                        if (tableController != null) tableController.rebuild();
                        break;
                    case "finished":
                        finishedSlavesDict.put(sender, true);
                        activeSlavesDict.put(sender, false);
                        if (tableController != null) tableController.rebuild();
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
