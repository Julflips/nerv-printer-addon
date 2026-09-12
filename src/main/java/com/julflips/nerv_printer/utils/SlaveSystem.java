package com.julflips.nerv_printer.utils;

import com.julflips.nerv_printer.interfaces.MapPrinter;
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
    public static SlaveTableController tableController = null;

    private static MapPrinter printerModule = null;

    public static void setupSlaveSystem(MapPrinter module, String ip, int port) {
        printerModule = module;
        slaves.clear();
        activeSlavesDict.clear();
        finishedSlavesDict.clear();
        isSlave = LocalTcpTransport.initialize(ip, port);
    }

    public static void setTcpAddress(String ip, int port) {
        isSlave = LocalTcpTransport.initialize(ip, port);
    }

    public static void sendMessage(String message) {
        LocalTcpTransport.sendToMaster(message);
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

    public static void sendToAllSlaves(String message) {
        LocalTcpTransport.sendToAllSlaves(message);
    }

    public static void sendToSlave(String username, String message) {
        LocalTcpTransport.sendToSlave(username, message);
    }

    public static void startAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            if (!activeSlavesDict.get(slave)) {
                LocalTcpTransport.sendToSlave(slave, "start");
                activeSlavesDict.put(slave, true);
            }
        }
        if (printerModule != null && !printerModule.isActive() && !printerModule.getActivationReset()) {
            printerModule.toggle();
        }
    }

    public static void pauseAllSlaves() {
        for (String slave : activeSlavesDict.keySet()) {
            LocalTcpTransport.sendToSlave(slave, "pause");
            activeSlavesDict.put(slave, false);
        }
        if (printerModule != null && printerModule.isActive() && !printerModule.getActivationReset()) {
            printerModule.toggle();
        }
    }

    public static void skipNextBuilding() {
        LocalTcpTransport.sendToAllSlaves("skip");
        if (printerModule != null) printerModule.skipBuilding();
    }

    public static void generateIntervals() {
        int sectionSize = (int) Math.ceil((float) 128 / (float) (slaves.size() + 1));
        ArrayList<Pair<Integer, Integer>> intervals = new ArrayList<>();
        for (int end = 127; end >= 0; end -= sectionSize) {
            int start = Math.max(0, end - sectionSize + 1);
            intervals.add(new Pair<>(start, end));
        }
        Collections.reverse(intervals);

        if (printerModule != null && !intervals.isEmpty()) {
            printerModule.setInterval(intervals.remove((intervals.size() - 1) / 2));
        }

        ArrayList<String> sortedSlaves = new ArrayList<>(slaves);
        Collections.sort(sortedSlaves, String.CASE_INSENSITIVE_ORDER);

        for (int i = 0; i < intervals.size() && i < sortedSlaves.size(); i++) {
            String slave = sortedSlaves.get(i);
            LocalTcpTransport.sendToSlave(slave, "interval:" + intervals.get(i).getLeft() + ":" + intervals.get(i).getRight());
        }
    }

    public static void removeSlave(String slave) {
        slaves.remove(slave);
        activeSlavesDict.remove(slave);
        finishedSlavesDict.remove(slave);
        LocalTcpTransport.sendToAllSlaves("remove");
        generateIntervals();
    }

    public static void handleIncomingTcpMessage(String sender, String rawMessage) {
        // If no sender is specified, the message is from master
        if (rawMessage == null || rawMessage.isBlank()) return;
        ChatUtils.info(rawMessage);
        String[] colonSplit = rawMessage.replace(" ", "").split(":");
        String command = colonSplit[0];

        // Master -> Client
        if (sender == null) {
            if (printerModule == null) return;
            switch (command) {
                case "layer":
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
                    finishedSlavesDict.put(sender, false);
                    activeSlavesDict.put(sender, false);
                    ChatUtils.info("Registered slave: " + sender + " Total slaves: " + slaves.size());
                    generateIntervals();
                    if (tableController != null) tableController.rebuild();
                }
                break;
            case "finished":
                finishedSlavesDict.put(sender, true);
                activeSlavesDict.put(sender, false);
                printerModule.slaveFinished(sender);
                if (tableController != null) tableController.rebuild();
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
