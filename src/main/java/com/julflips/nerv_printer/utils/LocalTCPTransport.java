package com.julflips.nerv_printer.utils;

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

public final class LocalTCPTransport {
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

        close();
        host = ip;
        port = tcpPort;

        if (connectToMaster()) return true;
        startServer();
        return false;
    }

     // Returns whether this instance has an active master or slave connection.
    public static boolean isRunning() {
        synchronized (SOCKET_LOCK) {
            if (masterSocket != null
                && masterSocket.isConnected()
                && !masterSocket.isClosed()) {
                return true;
            }

            for (Socket socket : slaveSockets) {
                if (socket != null
                    && socket.isConnected()
                    && !socket.isClosed()) {
                    return true;
                }
            }

            return false;
        }
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
        if (username == null || username.isBlank()
            || payload == null || payload.isBlank()) {
            return;
        }

        Socket socket;
        synchronized (SOCKET_LOCK) {
            socket = slaveSocketsByUsername.get(username);
        }

        if (socket != null && !socket.isClosed()) {
            writeLine(socket, payload);
        }
    }

    private static boolean connectToMaster() {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(
                new InetSocketAddress(InetAddress.getByName(host), port),
                2000
            );

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

            writeLine(
                socket,
                "register:" + MinecraftClient.getInstance()
                    .getSession().getUsername()
            );

            Thread readThread = new Thread(
                () -> readLoop(socket),
                "map-printer-tcp-client"
            );
            readThread.setDaemon(true);
            readThread.start();
            ChatUtils.info("§aSuccessfully §bconnected to §aMaster Server");
            return true;
        } catch (IOException e) {
            closeQuietly(socket);
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
                while (!socket.isClosed()) {
                    try {
                        Socket clientSocket = socket.accept();
                        clientSocket.setTcpNoDelay(true);
                        if (!isLoopback(clientSocket)) {
                            clientSocket.close();
                            continue;
                        }
                        registerSlaveSocket(clientSocket);
                    } catch (IOException e) {
                        if (!socket.isClosed()) {
                            ChatUtils.error(
                                "TCP server socket failed: " + e.getMessage()
                            );
                        }
                        break;
                    }
                }
            }, "map-printer-tcp-server");
            acceptThread.setDaemon(true);
            acceptThread.start();
            ChatUtils.info("§aSuccessfully §bstarted §aMaster Server");
        } catch (IOException e) {
            ChatUtils.error(
                "Failed to start master TCP server: " + e.getMessage()
            );
        }
    }

    private static void registerSlaveSocket(Socket socket) {
        synchronized (SOCKET_LOCK) {
            if (!slaveSockets.contains(socket)) {
                slaveSockets.add(socket);
            }
        }

        Thread readThread = new Thread(
            () -> readLoop(socket),
            "map-printer-tcp-client-handler"
        );
        readThread.setDaemon(true);
        readThread.start();
    }

    private static void readLoop(Socket socket) {
        String sender;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                socket.getInputStream(),
                StandardCharsets.UTF_8
            )
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                line = line.trim();
                sender = null;

                boolean isMasterConnection;
                synchronized (SOCKET_LOCK) {
                    isMasterConnection = masterSocket == socket;
                }

                if (!isMasterConnection) {
                    if (line.startsWith("register:")) {
                        sender = line.substring("register:".length());

                        if (sender.isBlank()) {
                            break;
                        }

                        synchronized (SOCKET_LOCK) {
                            Socket existing = slaveSocketsByUsername.get(sender);

                            // Avoid replacing a different active connection.
                            if (existing != null && existing != socket
                                && !existing.isClosed()) {
                                ChatUtils.warning(
                                    "Duplicate TCP registration for: " + sender
                                );
                                break;
                            }
                            slaveSocketsByUsername.put(sender, socket);
                        }
                    } else {
                        synchronized (SOCKET_LOCK) {
                            sender = slaveSocketsByUsername.inverse().get(socket);
                        }
                        if (sender == null) {
                            ChatUtils.warning(
                                "Could not determine sender: " + line
                            );
                        }
                    }
                }
                if ("remove".equals(line) && isMasterConnection) {
                    SlaveSystem.closeConnection();
                    break;
                }

                SlaveSystem.handleIncomingTcpMessage(sender, line);
            }

        } catch (IOException ignored) {
        } finally {
            boolean disconnectedFromMaster = false;

            synchronized (SOCKET_LOCK) {
                if (masterSocket == socket) {
                    masterSocket = null;
                    disconnectedFromMaster = true;
                }

                String registeredUsername =
                    slaveSocketsByUsername.inverse().get(socket);

                if (registeredUsername != null) {
                    slaveSocketsByUsername.remove(registeredUsername);
                }
                slaveSockets.remove(socket);
            }

            closeQuietly(socket);

            if (disconnectedFromMaster) {
                ChatUtils.warning(
                    "Master disconnected."
                );
            }
        }
    }

    private static void writeLine(Socket socket, String payload) {
        if (socket == null || socket.isClosed()) return;

        try {
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(
                    socket.getOutputStream(),
                    StandardCharsets.UTF_8
                ),
                true
            );

            writer.println(payload);

            if (writer.checkError()) {
                handleWriteFailure(socket);
            }

        } catch (IOException ignored) {
            handleWriteFailure(socket);
        }
    }

    private static void handleWriteFailure(Socket socket) {
        synchronized (SOCKET_LOCK) {
            if (masterSocket == socket) {
                masterSocket = null;
            }

            String username = slaveSocketsByUsername.inverse().get(socket);

            if (username != null) {
                slaveSocketsByUsername.remove(username);
            }

            slaveSockets.remove(socket);
        }

        closeQuietly(socket);
    }

    private static boolean isLoopback(Socket socket) {
        return socket != null
            && socket.getInetAddress() != null
            && socket.getInetAddress().isLoopbackAddress();
    }

    private static void closeQuietly(Socket socket) {
        if (socket == null || socket.isClosed()) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public static void close() {
        close(true);
    }

    private static void close(boolean notifyPeers) {
        ChatUtils.info("Closing all connections.");
        Socket currentMaster;
        List<Socket> currentSlaves;
        ServerSocket currentServer;

        synchronized (SOCKET_LOCK) {
            currentMaster = masterSocket;
            currentSlaves = new ArrayList<>(slaveSockets);
            currentServer = serverSocket;
            masterSocket = null;
            slaveSockets.clear();
            slaveSocketsByUsername.clear();
            serverSocket = null;
        }

        if (notifyPeers) {
            // Notify the master if this instance is a slave.
            if (currentMaster != null && !currentMaster.isClosed()) {
                writeLine(currentMaster, "remove");
                closeQuietly(currentMaster);
            }

            // Notify connected slaves if this instance is the master.
            for (Socket socket : currentSlaves) {
                if (socket != null && !socket.isClosed()) {
                    writeLine(socket, "remove");
                    closeQuietly(socket);
                }
            }
        } else {
            closeQuietly(currentMaster);
            for (Socket socket : currentSlaves) {
                closeQuietly(socket);
            }
        }

        if (currentServer != null) {
            try {
                currentServer.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean isMasterInstance() {
        synchronized (SOCKET_LOCK) {
            return serverSocket != null
                && !serverSocket.isClosed()
                && (masterSocket == null || masterSocket.isClosed());
        }
    }
}
