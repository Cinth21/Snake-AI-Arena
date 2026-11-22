package com.student.snakearena;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hotspot-only TCP game transport with UDP room-code discovery; no internet server. */
public final class LanNetwork {
    public static final int TCP_PORT = 38772;
    private static final int DISCOVERY_PORT = 38771;
    private static final String DISCOVER = "SNAKE_DISCOVER:";
    private static final String FOUND = "SNAKE_FOUND:";

    public interface Listener {
        void onLobbyChanged(String[] players);
        void onWelcome(int assignedSlot);
        void onStart(JSONObject state);
        void onState(JSONObject state);
        void onRemoteDirection(int slot, int direction);
        void onRemoteDisconnected(int slot);
        void onError(String message);
    }

    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Peer> peers = new CopyOnWriteArrayList<>();
    private final ExecutorService outgoing = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private volatile boolean hosting = false;
    private volatile boolean inMatch = false;
    private volatile int mySlot = -1;
    private volatile String roomCode = "";
    private volatile String hostName = "HOST";
    private volatile String[] playerNames = {"HOST"};
    private ServerSocket server;
    private DatagramSocket discovery;
    private Socket clientSocket;
    private BufferedWriter clientWriter;

    private static final class Peer {
        final Socket socket;
        final BufferedWriter writer;
        final int slot;
        final String name;
        Peer(Socket socket, BufferedWriter writer, int slot, String name) {
            this.socket = socket; this.writer = writer; this.slot = slot; this.name = name;
        }
    }

    public LanNetwork(Listener listener) { this.listener = listener; }
    public boolean isHosting() { return hosting && running; }
    public boolean isRunning() { return running; }
    public int getMySlot() { return mySlot; }
    public String getCode() { return roomCode; }
    public String[] getPlayers() { return playerNames.clone(); }

    public synchronized boolean host(String name) {
        close();
        try {
            server = new ServerSocket(TCP_PORT);
            discovery = new DatagramSocket(DISCOVERY_PORT);
            roomCode = String.valueOf(100000 + new SecureRandom().nextInt(900000));
            hostName = name;
            hosting = true; running = true; inMatch = false; mySlot = 0;
            refreshPlayers();
            Thread acceptThread = new Thread(this::acceptLoop, "Snake-LAN-accept"); acceptThread.setDaemon(true); acceptThread.start();
            Thread beacon = new Thread(this::discoveryLoop, "Snake-LAN-discovery"); beacon.setDaemon(true); beacon.start();
            return true;
        } catch (Exception e) {
            close();
            error("Cannot host. Check hotspot/Wi-Fi. " + e.getMessage());
            return false;
        }
    }

    public void join(String code, String playerName, String optionalIp) {
        close();
        roomCode = code.trim();
        hostName = playerName.trim().isEmpty() ? "PLAYER" : playerName.trim();
        hosting = false; running = true; inMatch = false; mySlot = -1;
        new Thread(() -> {
            try {
                String ip = optionalIp.trim().isEmpty() ? discoverHost(roomCode) : optionalIp.trim();
                if (!running) return;
                if (ip == null) throw new Exception("Room not found. Try entering host IP.");
                Socket socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(ip, TCP_PORT), 4000);
                socket.setTcpNoDelay(true);
                clientSocket = socket;
                clientWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                JSONObject hello = new JSONObject().put("type", "join").put("code", roomCode).put("name", hostName);
                sendClient(hello.toString());
                BufferedReader read = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (running && (line = read.readLine()) != null) {
                    if (line.length() > 16000) continue;
                    JSONObject msg = new JSONObject(line);
                    String type = msg.optString("type");
                    if ("welcome".equals(type)) {
                        mySlot = msg.getInt("slot");
                        ui.post(() -> listener.onWelcome(mySlot));
                    } else if ("lobby".equals(type)) {
                        playerNames = parsePlayers(msg.getJSONArray("players"));
                        ui.post(() -> listener.onLobbyChanged(getPlayers()));
                    } else if ("start".equals(type)) {
                        inMatch = true;
                        JSONObject state = msg.getJSONObject("state");
                        ui.post(() -> listener.onStart(state));
                    } else if ("state".equals(type)) {
                        JSONObject state = msg.getJSONObject("state");
                        ui.post(() -> listener.onState(state));
                    } else if ("error".equals(type)) {
                        throw new Exception(msg.optString("message", "Cannot join room"));
                    }
                }
                if (running) throw new Exception("Host disconnected.");
            } catch (Exception e) {
                if (running) { error(e.getMessage()); close(); }
            }
        }, "Snake-LAN-client").start();
    }

    private static String[] parsePlayers(JSONArray array) throws Exception {
        String[] out = new String[array.length()];
        for (int i = 0; i < out.length; i++) out[i] = array.getString(i);
        return out;
    }

    private void acceptLoop() {
        while (running && hosting) {
            try {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                new Thread(() -> handlePeer(socket), "Snake-LAN-peer").start();
            } catch (Exception e) { if (!running) break; }
        }
    }

    private void handlePeer(Socket socket) {
        Peer peer = null;
        try {
            socket.setSoTimeout(6000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String first = reader.readLine();
            if (first == null || first.length() > 512) throw new Exception("Bad join request");
            JSONObject req = new JSONObject(first);
            int assigned = -1;
            synchronized (peers) {
                if ("join".equals(req.optString("type")) && roomCode.equals(req.optString("code")) && !inMatch) {
                    for (int slot = 1; slot < 4; slot++) {
                        boolean taken = false;
                        for (Peer p : peers) if (p.slot == slot) taken = true;
                        if (!taken) { assigned = slot; break; }
                    }
                    if (assigned >= 0) {
                        String name = req.optString("name", "PLAYER").replaceAll("[^a-zA-Z0-9 _-]", "");
                        if (name.isEmpty()) name = "PLAYER " + assigned;
                        name = name.substring(0, Math.min(name.length(), 14));
                        peer = new Peer(socket, writer, assigned, name);
                        peers.add(peer);
                    }
                }
            }
            if (peer == null) {
                writer.write(new JSONObject().put("type", "error").put("message", "Wrong room code, full room, or match already started").toString() + "\n"); writer.flush();
                return;
            }
            socket.setSoTimeout(0);
            synchronized (writer) {
                writer.write(new JSONObject().put("type", "welcome").put("slot", assigned).toString() + "\n"); writer.flush();
            }
            refreshPlayers(); broadcastLobby();
            String msg;
            while (running && (msg = reader.readLine()) != null) {
                if (msg.length() > 512) continue;
                JSONObject obj = new JSONObject(msg);
                if ("dir".equals(obj.optString("type"))) {
                    int dir = obj.optInt("value", -1);
                    if (dir >= 0 && dir <= 3 && inMatch) {
                        final int pslot = assigned;
                        ui.post(() -> listener.onRemoteDirection(pslot, dir));
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (peer != null) {
                peers.remove(peer);
                refreshPlayers(); broadcastLobby();
                int s = peer.slot;
                ui.post(() -> listener.onRemoteDisconnected(s));
            }
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    private void refreshPlayers() {
        if (!hosting) return;
        String[] names = new String[4]; names[0] = hostName;
        for (Peer peer : peers) names[peer.slot] = peer.name;
        playerNames = names;
        ui.post(() -> listener.onLobbyChanged(getPlayers()));
    }

    private void broadcastLobby() {
        if (!running || !hosting) return;
        try {
            JSONArray array = new JSONArray();
            for (String name : playerNames) array.put(name == null ? "" : name);
            JSONObject obj = new JSONObject().put("type", "lobby").put("players", array);
            broadcast(obj.toString());
        } catch (Exception ignored) { }
    }

    public void hostStart(JSONObject state) {
        inMatch = true;
        try { broadcast(new JSONObject().put("type", "start").put("state", state).toString()); }
        catch (Exception e) { error("Could not start match"); }
    }

    public void hostUpdate(JSONObject state) {
        if (!hosting || !running || !inMatch) return;
        try { broadcast(new JSONObject().put("type", "state").put("state", state).toString()); }
        catch (Exception ignored) { }
    }

    public void submitDirection(int direction) {
        if (hosting || !running) return;
        // Avoid Android NetworkOnMainThreadException during touchscreen input.
        outgoing.execute(() -> {
            try { sendClient(new JSONObject().put("type", "dir").put("value", direction).toString()); }
            catch (Exception ignored) { }
        });
    }

    private void sendClient(String line) throws Exception {
        BufferedWriter w = clientWriter;
        if (w != null) synchronized (w) { w.write(line); w.write('\n'); w.flush(); }
    }

    private void broadcast(String line) {
        if (!hosting || !running) return;
        // Avoid blocking the UI frame while slow Wi-Fi clients consume data.
        outgoing.execute(() -> {
            for (Peer p : peers) {
                try { synchronized (p.writer) { p.writer.write(line); p.writer.write('\n'); p.writer.flush(); } }
                catch (Exception ignored) { try { p.socket.close(); } catch (Exception ignored2) { } }
            }
        });
    }

    private void discoveryLoop() {
        byte[] buffer = new byte[200];
        while (running && hosting) {
            try {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                discovery.receive(request);
                String query = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                if (query.equals(DISCOVER + roomCode)) {
                    byte[] reply = (FOUND + roomCode).getBytes(StandardCharsets.UTF_8);
                    discovery.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
                }
            } catch (Exception e) { if (!running) return; }
        }
    }

    private String discoverHost(String code) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        try {
            socket.setBroadcast(true);
            socket.setSoTimeout(900);
            byte[] query = (DISCOVER + code).getBytes(StandardCharsets.UTF_8);
            ArrayList<InetAddress> broadcasts = new ArrayList<>();
            broadcasts.add(InetAddress.getByName("255.255.255.255"));
            for (Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces(); list.hasMoreElements();) {
                NetworkInterface network = list.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast != null && !broadcasts.contains(broadcast)) broadcasts.add(broadcast);
                }
            }
            for (int attempt = 0; attempt < 5 && running; attempt++) {
                for (InetAddress broadcast : broadcasts) {
                    try { socket.send(new DatagramPacket(query, query.length, broadcast, DISCOVERY_PORT)); }
                    catch (Exception ignored) { }
                }
                try {
                    byte[] bytes = new byte[200];
                    DatagramPacket response = new DatagramPacket(bytes, bytes.length);
                    socket.receive(response);
                    String message = new String(bytes, 0, response.getLength(), StandardCharsets.UTF_8);
                    if (message.equals(FOUND + code)) return response.getAddress().getHostAddress();
                } catch (SocketTimeoutException ignored) { }
            }
            return null;
        } finally { socket.close(); }
    }

    public static String localIp() {
        ArrayList<String> candidates = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> list = NetworkInterface.getNetworkInterfaces(); list.hasMoreElements();) {
                NetworkInterface network = list.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                for (Enumeration<InetAddress> addresses = network.getInetAddresses(); addresses.hasMoreElements();) {
                    InetAddress ip = addresses.nextElement();
                    if (ip instanceof Inet4Address && ip.isSiteLocalAddress()) {
                        String value = ip.getHostAddress();
                        if (network.getName().startsWith("wlan") || network.getName().startsWith("ap")) candidates.add(0, value);
                        else candidates.add(value);
                    }
                }
            }
        } catch (Exception ignored) { }
        return candidates.isEmpty() ? "Unavailable" : candidates.get(0);
    }

    private void error(String message) { ui.post(() -> listener.onError(message == null ? "Network error" : message)); }

    public synchronized void close() {
        running = false;
        hosting = false;
        inMatch = false;
        mySlot = -1;
        try { if (discovery != null) discovery.close(); } catch (Exception ignored) { }
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) { }
        for (Peer peer : peers) { try { peer.socket.close(); } catch (Exception ignored) { } }
        peers.clear();
        discovery = null; server = null; clientSocket = null; clientWriter = null;
    }
}
