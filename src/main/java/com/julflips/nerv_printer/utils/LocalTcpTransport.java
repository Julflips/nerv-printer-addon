package com.julflips.nerv_printer.utils;

import com.mojang.datafixers.kinds.IdF;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.include.com.google.common.collect.BiMap;
import org.spongepowered.include.com.google.common.collect.HashBiMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LocalTcpTransport {
    private static final Object SOCKET_LOCK = new Object();

    private static String host;
    private static int port;
    private static Socket masterSocket;
    private static final List<Socket> slaveSockets = new ArrayList<>();
    private static final BiMap<String, Socket> slaveSocketsByUsername = HashBiMap.create();
    private static ServerSocket serverSocket;

    public static boolean initialize(String ip, int tcpPort) {
        // Returns true if there is no master server and the bot becomes a master
        if (ip == null || ip.isBlank() || tcpPort <= 0 || tcpPort > 65535) {
            ChatUtils.warning("Invalid TCP address or port: " + ip + ":" + tcpPort);
            return true;
        }

        host = ip;
        port = tcpPort;
        close();

        if (connectToMaster()) return true;
        startServer();
        return false;
    }

    public static void sendToMaster(String payload) {
        if (payload == null || payload.isBlank()) return;
        Socket socket;
        synchronized (SOCKET_LOCK) {
            socket = masterSocket;
        }
        if (socket != null && !socket.isClosed()) {
            writeLine(socket, payload);
        }
    }

    public static void sendToAllSlaves(String payload) {
        if (payload == null || payload.isBlank()) return;
        List<Socket> sockets;
        synchronized (SOCKET_LOCK) {
            sockets = new ArrayList<>(slaveSockets);
        }
        for (Socket socket : sockets) {
            if (socket != null && !socket.isClosed()) {
                writeLine(socket, payload);
            }
        }
    }

    public static void sendToSlave(String username, String payload) {
        if (username == null || username.isBlank() || payload == null || payload.isBlank()) return;

        Socket socket;
        synchronized (SOCKET_LOCK) {
            socket = slaveSocketsByUsername.get(username);
        }

        if (socket != null && !socket.isClosed()) {
            writeLine(socket, payload);
        }
    }

    private static boolean connectToMaster() {
        try {
            Socket socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), 1500);
            if (!isLoopback(socket)) {
                socket.close();
                return false;
            }
            synchronized (SOCKET_LOCK) {
                if (masterSocket != null && !masterSocket.isClosed()) {
                    socket.close();
                    return true;
                }
                masterSocket = socket;
            }
            writeLine(socket, "register:" + MinecraftClient.getInstance().getSession().getUsername());
            Thread readThread = new Thread(() -> readLoop(socket), "map-printer-tcp-client");
            readThread.setDaemon(true);
            readThread.start();
            ChatUtils.info("§aSuccessfully §bconnected to §aMaster Server");
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void startServer() {
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), port));

            synchronized (SOCKET_LOCK) {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    socket.close();
                    return;
                }
                serverSocket = socket;
            }

            Thread acceptThread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        if (!isLoopback(clientSocket)) {
                            clientSocket.close();
                            continue;
                        }
                        registerSlaveSocket(clientSocket);
                    } catch (IOException e) {
                        ChatUtils.error("TCP server socket failed: " + e.getMessage());
                        break;
                    }
                }
            }, "map-printer-tcp-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
            ChatUtils.info("§aSuccessfully §bstarted §aMaster Server");
        } catch (IOException e) {
            ChatUtils.error("Failed to start master TCP server: " + e.getMessage());
        }
    }

    private static void registerSlaveSocket(Socket socket) {
        synchronized (SOCKET_LOCK) {
            if (!slaveSockets.contains(socket)) {
                slaveSockets.add(socket);
            }
        }

        Thread readThread = new Thread(() -> readLoop(socket), "map-printer-tcp-client-handler");
        readThread.setDaemon(true);
        readThread.start();
    }

    private static void readLoop(Socket socket) {
        String sender = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                line = line.trim();
                // If no sender is specified, the message is from master
                sender = null;

                if (masterSocket == null) {
                    // Handle as master
                    if (line.startsWith("register")) {
                        sender = line.split(":")[1];
                        synchronized (SOCKET_LOCK) {
                            slaveSocketsByUsername.put(sender, socket);
                        }
                    } else {
                        sender = slaveSocketsByUsername.inverse().get(socket);
                        if (sender == null) ChatUtils.warning("Could not determine sender: " + line);
                    }
                }

                SlaveSystem.handleIncomingTcpMessage(sender, line);
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                ChatUtils.error("TCP socket read failed: " + e.getMessage());
            }
        } finally {
            synchronized (SOCKET_LOCK) {
                if (sender != null && slaveSocketsByUsername.get(sender) == socket) {
                    slaveSocketsByUsername.remove(sender);
                }
                if (masterSocket == socket) {
                    masterSocket = null;
                } else {
                    slaveSockets.remove(socket);
                }
            }
            closeQuietly(socket);
        }
    }

    private static void writeLine(Socket socket, String payload) {
        if (socket == null || socket.isClosed()) return;
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.println(payload);
        } catch (IOException ignored) {
            closeQuietly(socket);
        }
    }

    private static boolean isLoopback(Socket socket) {
        return socket != null && socket.getInetAddress() != null && socket.getInetAddress().isLoopbackAddress();
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null || socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static void close() {
        synchronized (SOCKET_LOCK) {
            if (masterSocket != null) {
                closeQuietly(masterSocket);
                masterSocket = null;
            }
            for (Socket socket : slaveSockets) {
                closeQuietly(socket);
            }
            slaveSockets.clear();
            slaveSocketsByUsername.clear();
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
                serverSocket = null;
            }
        }
    }
}
