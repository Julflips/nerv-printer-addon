package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.interfaces.MapPrinter;
import com.julflips.nerv_printer.modules.SuppressionPrinter;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public final class SlaveSystem {
    public static boolean isSlave = false;
    public static ArrayList<String> slaves = new ArrayList<>();
    public static HashMap<String, Boolean> activeSlavesDict = new HashMap<>();
    public static HashMap<String, Boolean> finishedSlavesDict = new HashMap<>();
    public static HashMap<String, Boolean> slavesLayerDict = new HashMap<>();  //True if on upper layer
    public static SlaveTableController tableController = null;
    public static MapCompletionState mapCompletionState = new MapCompletionState();

    private static MapPrinter printerModule = null;
    private static boolean sendToUpper = true;

    public static void setupSlaveSystem(MapPrinter module, String ip, int port) {
        printerModule = module;
        slaves.clear();
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        slavesLayerDict.clear();
        isSlave = LocalTcpTransport.initialize(ip, port);
        sendToUpper = true;
        mapCompletionState.initializeLayers();
    }

    public static void setTcpAddress(String ip, int port) {
        isSlave = LocalTcpTransport.initialize(ip, port);
    }

    public static void sendMessageToMaster(String message) {
        if (isSlave) LocalTcpTransport.sendToMaster(message);
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

    public static void setAllSlavesActive() {
        for (String slave : activeSlavesDict.keySet()) {
            activeSlavesDict.put(slave, true);
        }
    }

    public static void sendToAllSlaves(String message) {
        LocalTcpTransport.sendToAllSlaves(message);
    }

    public static void sendToSlave(String username, String message) {
        // ChatUtils.info(username + ": " + message);
        LocalTcpTransport.sendToSlave(username, message);
    }

    public static void startAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            if (!activeSlavesDict.get(slave)) {
                sendToSlave(slave, "start");
                activeSlavesDict.put(slave, true);
                finishedSlavesDict.put(slave, false);
            }
        }
        if (printerModule != null && !printerModule.isActive() && !printerModule.getActivationReset()) {
            printerModule.toggle();
        }
    }

    public static void pauseAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            sendToSlave(slave, "pause");
            activeSlavesDict.put(slave, false);
        }
        if (printerModule != null && printerModule.isActive() && !printerModule.getActivationReset()) {
            printerModule.toggle();
        }
    }

    public static void skipNextBuilding() {
        sendToAllSlaves("skip");
        if (printerModule != null) printerModule.skipBuilding();
    }

    public static void generateIntervals(ArrayList<String> slaveList) {
        int sectionSize = (int) Math.ceil((float) 128 / (float) (slaveList.size() + 1));
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int end = 127; end >= 0; end -= sectionSize) {
            int start = Math.max(0, end - sectionSize + 1);
            intervals.add(new Pair<>(start, end));
        }
        Collections.reverse(intervals);

        if (printerModule != null && !intervals.isEmpty()) {
            printerModule.setInterval(intervals.remove((intervals.size() - 1) / 2));
        }

        ArrayList<String> sortedSlaves = new ArrayList<>(slaveList);
        Collections.sort(sortedSlaves, String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < intervals.size() && i < sortedSlaves.size(); i++) {
            String slave = sortedSlaves.get(i);
            sendToSlave(slave, "interval:" + intervals.get(i).getLeft() + ":" + intervals.get(i).getRight());
        }
    }

    public static void removeSlave(String slave) {
        slaves.remove(slave);
        activeSlavesDict.remove(slave);
        finishedSlavesDict.remove(slave);
        slavesLayerDict.remove(slave);
        sendToAllSlaves("remove");
        generateIntervals(slaves);
    }

    public static void handleIncomingTcpMessage(String sender, String rawMessage) {
        // If no sender is specified, the message is from master
        if (rawMessage == null || rawMessage.isBlank()) return;
        String[] colonSplit = rawMessage.replace(" ", "").split(":");
        String command = colonSplit[0];

        // Master -> Client
        if (sender == null) {
            if (printerModule == null) return;
            switch (command) {
                case "layer":
                    boolean upper = Boolean.parseBoolean(colonSplit[1]);
                    printerModule.setLayer(upper);
                    ChatUtils.info("§a Move this bot to the §b" + (upper ? "upper": "lower") +" §alayer before starting");
                    break;
                case "interval":
                    if (colonSplit.length >= 3) {
                        Pair<Integer, Integer> interval = new Pair<>(Integer.valueOf(colonSplit[1]), Integer.valueOf(colonSplit[2]));
                        printerModule.setInterval(interval);
                    }
                    break;
                case "pause":
                    printerModule.pause();
                    break;
                case "start":
                    printerModule.start();
                    break;
                case "remove":
                    printerModule.toggle();
                    break;
                case "skip":
                    printerModule.skipBuilding();
                    break;
                case "mine":
                    if (colonSplit.length >= 2) printerModule.mineLine(Integer.parseInt(colonSplit[1]));
                    break;
                default:
                    ChatUtils.warning("Unknown command: '" + command + "'");
                    break;
            }
            return;
        }

        // Client -> Master
        switch (command) {
            case "register":
                if (!slaves.contains(sender)) {
                    slaves.add(sender);
                    finishedSlavesDict.put(sender, true);
                    activeSlavesDict.put(sender, false);
                    if (printerModule instanceof SuppressionPrinter) {
                        slavesLayerDict.put(sender, sendToUpper);
                        sendToSlave(sender, "layer:" + sendToUpper);
                        sendToUpper = !sendToUpper;
                    }
                    ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                    generateIntervals(slaves);
                    if (tableController != null) tableController.rebuild();
                }
                break;
            case "finished":
                finishedSlavesDict.put(sender, true);
                activeSlavesDict.put(sender, false);
                printerModule.slaveFinished(sender);
                if (tableController != null) tableController.rebuild();
                break;
            case "placeStatus":
                boolean longVariant = colonSplit.length == 7;
                int startZ = longVariant ? Integer.parseInt(colonSplit[3]) : 0;
                int endZ = longVariant ? Integer.parseInt(colonSplit[4]) : 127;
                boolean targetValue = longVariant ? Boolean.parseBoolean(colonSplit[5]) : Boolean.parseBoolean(colonSplit[3]);
                boolean isUpper = longVariant ? Boolean.parseBoolean(colonSplit[6]): Boolean.parseBoolean(colonSplit[4]);
                SlaveSystem.mapCompletionState.setInterval(
                    Integer.parseInt(colonSplit[1]),
                    Integer.parseInt(colonSplit[2]),
                    startZ,
                    endZ,
                    targetValue,
                    isUpper);
                break;
            case "error":
                if (colonSplit.length >= 3) {
                    BlockPos relativeErrorPos = new BlockPos(Integer.parseInt(colonSplit[1]), 0, Integer.parseInt(colonSplit[2]));
                    printerModule.addError(relativeErrorPos);
                }
                break;
            default:
                ChatUtils.warning("Unknown command: '" + command + "'");
                break;
        }
    }
}
