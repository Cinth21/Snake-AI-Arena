package com.student.snakearena;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.text.InputType;
import org.json.JSONObject;

import java.util.ArrayList;

public final class MainActivity extends Activity implements LanNetwork.Listener {
    private enum Screen { HOME, LOBBY, GAME, HELP }
    private Screen screen = Screen.HOME;
    private ArenaView view;
    private LanNetwork network;
    private GameEngine game;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private boolean offline = true;
    private int aiDifficulty = 1;
    private int mySlot = 0;
    private String info = "";
    private String pendingRoomCode = "";
    private String pendingPlayerName = "PLAYER";

    private final Runnable gameLoop = new Runnable() {
        @Override public void run() {
            if (screen == Screen.GAME && game != null && (offline || network.isHosting())) {
                if (!game.finished) {
                    game.tick();
                    if (!offline) pushState();
                    view.invalidate();
                }
            }
            timer.postDelayed(this, 250);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.rgb(7, 13, 27));
        network = new LanNetwork(this);
        view = new ArenaView(this);
        setContentView(view);
        timer.postDelayed(gameLoop, 250);
    }

    private void openHome() {
        network.close();
        game = null;
        offline = true;
        mySlot = 0;
        info = "";
        screen = Screen.HOME;
        view.invalidate();
    }

    private void startAi() {
        network.close();
        offline = true;
        mySlot = 0;
        game = GameEngine.aiGame(aiDifficulty);
        screen = Screen.GAME;
        view.invalidate();
    }

    private void hostRoom() {
        game = null;
        offline = false;
        mySlot = 0;
        screen = Screen.LOBBY;
        info = "Creating room on this phone...";
        view.invalidate();
        // Bind sockets off the UI thread to respect Android network restrictions.
        new Thread(() -> {
            boolean created = network.host("HOST");
            runOnUiThread(() -> {
                if (created && screen != Screen.LOBBY) { network.close(); return; }
                if (created && screen == Screen.LOBBY) {
                    info = "Connect all phones to the same hotspot";
                    view.invalidate();
                } else if (!created) {
                    info = "Unable to start LAN host";
                    view.invalidate();
                }
            });
        }, "Snake-host-init").start();
    }

    private void promptJoin(boolean retry) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        layout.setPadding(pad, dp(10), pad, 0);
        EditText codeInput = new EditText(this);
        codeInput.setHint("6-digit room code");
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setSingleLine(true);
        if (retry) codeInput.setText(pendingRoomCode);
        layout.addView(codeInput);
        EditText nameInput = new EditText(this);
        nameInput.setHint("Your nickname");
        nameInput.setSingleLine(true);
        nameInput.setText(retry ? pendingPlayerName : "PLAYER");
        layout.addView(nameInput);
        EditText ipInput = new EditText(this);
        ipInput.setHint("Host IP (optional if discovery works)");
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(ipInput);
        new AlertDialog.Builder(this)
                .setTitle(retry ? "Retry joining LAN room" : "Join LAN multiplayer")
                .setMessage("Join the host's Wi-Fi/hotspot first. The code finds the host automatically; enter host IP only if needed.")
                .setView(layout)
                .setPositiveButton("Join", (d, which) -> {
                    String code = codeInput.getText().toString().trim();
                    if (!code.matches("[0-9]{6}")) {
                        Toast.makeText(this, "Please enter a six-digit code", Toast.LENGTH_LONG).show();
                        return;
                    }
                    pendingRoomCode = code;
                    pendingPlayerName = nameInput.getText().toString().trim();
                    if (pendingPlayerName.isEmpty()) pendingPlayerName = "PLAYER";
                    pendingPlayerName = pendingPlayerName.substring(0, Math.min(14, pendingPlayerName.length()));
                    mySlot = -1;
                    offline = false;
                    game = null;
                    info = "Searching for host...";
                    screen = Screen.LOBBY;
                    view.invalidate();
                    network.join(code, pendingPlayerName, ipInput.getText().toString());
                })
                .setNegativeButton("Cancel", null).show();
    }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }

    private void startLanMatch() {
        if (!network.isHosting()) return;
        String[] players = network.getPlayers();
        int count = 0;
        for (String name : players) if (name != null && !name.isEmpty()) count++;
        if (count < 2) {
            Toast.makeText(this, "Wait for at least one other player", Toast.LENGTH_SHORT).show();
            return;
        }
        game = GameEngine.lanGame(players);
        try {
            network.hostStart(GameCodec.encode(game));
            screen = Screen.GAME;
            view.invalidate();
        } catch (Exception e) { Toast.makeText(this, "Cannot start match", Toast.LENGTH_SHORT).show(); }
    }

    private void pushState() {
        if (!network.isHosting() || game == null) return;
        try { network.hostUpdate(GameCodec.encode(game)); }
        catch (Exception ignored) { }
    }

    private void direction(int direction) {
        if (game == null || game.finished || screen != Screen.GAME) return;
        if (offline || network.isHosting()) game.requestDirection(mySlot, direction);
        else network.submitDirection(direction);
        view.invalidate();
    }

    private void restart() {
        if (offline) { startAi(); return; }
        if (network.isHosting()) {
            // Starting a new round with the same connected players and slots.
            game = GameEngine.lanGame(network.getPlayers());
            try { network.hostStart(GameCodec.encode(game)); }
            catch (Exception ignored) { }
            view.invalidate();
        }
    }

    @Override public void onLobbyChanged(String[] players) {
        if (screen == Screen.LOBBY) { info = "Players joined: " + countPlayers(players) + "/4"; view.invalidate(); }
    }

    private int countPlayers(String[] players) {
        int n = 0; for (String p : players) if (p != null && !p.isEmpty()) n++; return n;
    }

    @Override public void onWelcome(int assignedSlot) {
        mySlot = assignedSlot;
        info = "Connected! You are player " + (mySlot + 1);
        view.invalidate();
    }

    @Override public void onStart(JSONObject state) {
        try { game = GameCodec.decode(state); screen = Screen.GAME; view.invalidate(); }
        catch (Exception e) { onError("Invalid game state from host"); }
    }

    @Override public void onState(JSONObject state) {
        if (screen != Screen.GAME) return;
        try {
            GameEngine fresh = GameCodec.decode(state);
            if (game == null || fresh.tickNumber >= game.tickNumber) { game = fresh; view.invalidate(); }
        } catch (Exception ignored) { }
    }

    @Override public void onRemoteDirection(int slot, int dir) {
        if (screen == Screen.GAME && network.isHosting() && game != null) game.requestDirection(slot, dir);
    }

    @Override public void onRemoteDisconnected(int slot) {
        if (screen == Screen.GAME && network.isHosting() && game != null) {
            game.eliminate(slot);
            pushState(); view.invalidate();
        }
    }

    @Override public void onError(String message) {
        if (screen == Screen.LOBBY && !network.isHosting() && mySlot < 0) {
            new AlertDialog.Builder(this).setTitle("Cannot join room")
                    .setMessage(message + "\n\nCheck that both phones are connected to the same hotspot. Ask the host for its IP address if discovery fails.")
                    .setPositiveButton("Retry / Enter IP", (dialog, which) -> promptJoin(true))
                    .setNegativeButton("Main Menu", (dialog, which) -> openHome())
                    .setCancelable(false).show();
        } else {
            new AlertDialog.Builder(this).setTitle("LAN connection")
                    .setMessage(message).setPositiveButton("OK", (dialog, which) -> openHome()).show();
        }
    }

    @Override public void onBackPressed() {
        if (screen == Screen.HOME) super.onBackPressed();
        else new AlertDialog.Builder(this).setTitle("Return to menu?")
                .setMessage("This will leave your current match or room.")
                .setPositiveButton("Leave", (dialog, which) -> openHome())
                .setNegativeButton("Stay", null).show();
    }

    @Override protected void onDestroy() {
        timer.removeCallbacks(gameLoop);
        network.close();
        super.onDestroy();
    }

    private final class ArenaView extends View {
        private static final float WORLD_W = 420f, WORLD_H = 840f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArrayList<Tap> taps = new ArrayList<>();
        private float scale = 1, offsetX, offsetY, startX, startY;
        private final int bg = Color.rgb(7, 13, 27);
        private final int panel = Color.rgb(18, 30, 51);
        private final int accent = Color.rgb(41, 223, 159);
        private final int muted = Color.rgb(148, 163, 184);
        private final int white = Color.rgb(240, 248, 255);
        private final int[] snakeColors = {
                Color.rgb(42, 226, 159), Color.rgb(91, 174, 255),
                Color.rgb(255, 175, 70), Color.rgb(219, 128, 255)
        };

        private final class Tap {
            final RectF rect;
            final Runnable action;
            Tap(float x, float y, float w, float h, Runnable r) { rect = new RectF(x, y, x + w, y + h); action = r; }
        }

        ArenaView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        @Override protected void onDraw(Canvas raw) {
            super.onDraw(raw);
            raw.drawColor(bg);
            scale = Math.min(getWidth() / WORLD_W, getHeight() / WORLD_H);
            offsetX = (getWidth() - WORLD_W * scale) / 2f;
            offsetY = (getHeight() - WORLD_H * scale) / 2f;
            raw.save(); raw.translate(offsetX, offsetY); raw.scale(scale, scale);
            Canvas c = raw;
            taps.clear();
            drawBackdrop(c);
            switch (screen) {
                case HOME: drawHome(c); break;
                case LOBBY: drawLobby(c); break;
                case GAME: drawGame(c); break;
                case HELP: drawHelp(c); break;
            }
            raw.restore();
            setKeepScreenOn(screen == Screen.GAME || screen == Screen.LOBBY);
        }

        private void drawBackdrop(Canvas c) {
            paint.setColor(bg);
            paint.setShader(new LinearGradient(0, 0, 420, 840, Color.rgb(8, 20, 40), bg, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 420, 840, paint); paint.setShader(null);
            paint.setColor(Color.argb(50, 74, 100, 140));
            for (int i = 0; i < 40; i++) {
                int x = (i * 97 + 17) % 420;
                int y = (i * 153 + 43) % 840;
                c.drawCircle(x, y, 1.6f + i % 2, paint);
            }
        }

        private void rect(Canvas c, float x, float y, float w, float h, int color, float rad) {
            paint.setColor(color);
            c.drawRoundRect(new RectF(x, y, x + w, y + h), rad, rad, paint);
        }

        private void text(Canvas c, String txt, float x, float baseline, float size, int color, boolean bold) {
            paint.setShader(null); paint.setColor(color); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(bold ? android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    : android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            paint.setTextSize(size); paint.setStyle(Paint.Style.FILL);
            c.drawText(txt, x, baseline, paint);
        }

        private void button(Canvas c, float x, float y, float w, float h, String title, int fill, int ink, Runnable action) {
            rect(c, x, y + 4, w, h, Color.argb(95, 0, 0, 0), 16);
            rect(c, x, y, w, h, fill, 16);
            text(c, title, x + w / 2f, y + h / 2f + 6, 16, ink, true);
            if (action != null) taps.add(new Tap(x, y, w, h, action));
        }

        private void drawSnakeLogo(Canvas c, float cx, float cy) {
            int[] xs = {-55, -25, 5, 35, 65};
            int[] ys = {16, 16, -12, -12, -12};
            for (int i = 0; i < xs.length; i++) {
                rect(c, cx + xs[i] - 12, cy + ys[i] - 12, 28, 28,
                        i == 4 ? accent : Color.rgb(23, 172 + i * 9, 127 + i * 12), 9);
            }
            paint.setColor(bg); c.drawCircle(cx + 75, cy - 5, 2.5f, paint);
            c.drawCircle(cx + 74, cy - 14, 2.5f, paint);
        }

        private void drawHome(Canvas c) {
            drawSnakeLogo(c, 210, 115);
            text(c, "SNAKE", 210, 218, 57, white, true);
            text(c, "AI ARENA", 210, 260, 38, accent, true);
            text(c, "OFFLINE INTELLIGENCE + HOTSPOT BATTLES", 210, 299, 11, muted, true);
            rect(c, 30, 334, 360, 65, panel, 16);
            text(c, "AI DIFFICULTY", 210, 353, 12, muted, true);
            String[] difficulties = {"EASY", "MEDIUM", "HARD"};
            for (int i = 0; i < 3; i++) {
                int selected = aiDifficulty == i ? accent : Color.rgb(36, 53, 77);
                final int level = i;
                button(c, 42 + 115 * i, 361, 105, 31, difficulties[i], selected, aiDifficulty == i ? bg : white,
                        () -> { aiDifficulty = level; invalidate(); });
            }
            button(c, 30, 423, 360, 67, "PLAY VS AI", accent, bg, MainActivity.this::startAi);
            button(c, 30, 514, 360, 65, "CREATE LAN ROOM", Color.rgb(62, 122, 244), white, MainActivity.this::hostRoom);
            button(c, 30, 601, 360, 65, "JOIN LAN ROOM", Color.rgb(45, 65, 94), white, () -> promptJoin(false));
            button(c, 30, 688, 360, 57, "HOW TO PLAY", panel, white, () -> { screen = Screen.HELP; invalidate(); });
            text(c, "2-4 PLAYERS  /  NO INTERNET NEEDED FOR LAN", 210, 793, 11, muted, false);
        }

        private void drawLobby(Canvas c) {
            text(c, network.isHosting() ? "YOUR LAN ROOM" : "JOINING ROOM", 210, 87, 28, white, true);
            text(c, "ALL PHONES ON THE SAME WI-FI / HOTSPOT", 210, 119, 12, muted, true);
            rect(c, 30, 156, 360, 176, panel, 20);
            text(c, "ROOM CODE", 210, 187, 13, muted, true);
            text(c, network.getCode(), 210, 258, 56, accent, true);
            text(c, "Share this code with other players", 210, 292, 12, muted, false);
            if (network.isHosting()) {
                text(c, "HOST IP: " + LanNetwork.localIp(), 210, 353, 14, white, true);
                text(c, "Use IP only if code discovery cannot connect", 210, 378, 11, muted, false);
            } else {
                text(c, "Searching on hotspot...", 210, 355, 14, white, true);
            }
            text(c, "PLAYERS", 210, 419, 18, white, true);
            String[] names = network.getPlayers();
            for (int i = 0; i < 4; i++) {
                int y = 439 + 54 * i;
                rect(c, 35, y, 350, 44, panel, 12);
                paint.setColor(snakeColors[i]); c.drawCircle(59, y + 22, 9, paint);
                String player = i < names.length && names[i] != null && !names[i].isEmpty() ? names[i] : "Waiting...";
                text(c, "P" + (i + 1) + "    " + player, 201, y + 28, 14, white, true);
            }
            text(c, info, 210, 680, 13, muted, false);
            if (network.isHosting()) button(c, 30, 707, 360, 58, "START MATCH", accent, bg, MainActivity.this::startLanMatch);
            else text(c, "Host will start the match", 210, 748, 17, accent, true);
            button(c, 30, 781, 360, 41, "LEAVE ROOM", Color.rgb(51, 62, 82), white, MainActivity.this::openHome);
        }

        private void drawGame(Canvas c) {
            if (game == null) { text(c, "Loading arena...", 210, 380, 24, white, true); return; }
            text(c, offline ? "VS AI - " + new String[]{"EASY", "MEDIUM", "HARD"}[aiDifficulty]
                            : (network.isHosting() ? "HOTSPOT HOST" : "LAN PLAYER " + (mySlot + 1)),
                    210, 54, 19, accent, true);
            text(c, "Swipe in the arena or tap the direction buttons", 210, 80, 12, muted, false);
            int n = game.snakes.size();
            for (int i = 0; i < n; i++) {
                GameEngine.Snake s = game.snakes.get(i);
                float x = n <= 2 ? 100 + i * 220 : 92 + (i % 2) * 225;
                float y = n <= 2 ? 121 : (i < 2 ? 112 : 144);
                paint.setColor(snakeColors[s.slot]); c.drawCircle(x - 58, y - 5, 6, paint);
                String name = s.name.length() > 8 ? s.name.substring(0, 8) : s.name;
                text(c, name + " " + s.score + (s.alive ? "" : " OUT"), x + 5, y, 13, s.alive ? white : muted, true);
            }
            final float top = 183, left = 20, tile = 19;
            rect(c, 14, top - 6, 392, 392, Color.rgb(36, 70, 77), 11);
            for (int y = 0; y < GameEngine.HEIGHT; y++) {
                for (int x = 0; x < GameEngine.WIDTH; x++) {
                    rect(c, left + x * tile, top + y * tile, tile - 1, tile - 1,
                            (x + y) % 2 == 0 ? Color.rgb(17, 45, 52) : Color.rgb(20, 53, 59), 2);
                }
            }
            float fx = left + (game.food.x + 0.5f) * tile;
            float fy = top + (game.food.y + 0.5f) * tile;
            paint.setColor(Color.rgb(255, 98, 114)); c.drawCircle(fx, fy, 8, paint);
            paint.setColor(Color.rgb(255, 204, 204)); c.drawCircle(fx - 2, fy - 2, 2, paint);
            for (GameEngine.Snake s : game.snakes) {
                int index = 0;
                for (GameEngine.Cell cell : s.body) {
                    int color = s.alive ? snakeColors[s.slot] : Color.rgb(68, 78, 92);
                    float px = left + cell.x * tile + 1;
                    float py = top + cell.y * tile + 1;
                    rect(c, px, py, tile - 3, tile - 3, color, index == 0 ? 6 : 4);
                    if (index == 0 && s.alive) {
                        paint.setColor(bg);
                        c.drawCircle(px + 6, py + 5, 2, paint);
                        c.drawCircle(px + 12, py + 5, 2, paint);
                    }
                    index++;
                }
            }
            button(c, 161, 591, 98, 55, "UP", panel, white, () -> direction(GameEngine.UP));
            button(c, 42, 653, 104, 55, "LEFT", panel, white, () -> direction(GameEngine.LEFT));
            button(c, 161, 653, 98, 55, "DOWN", panel, white, () -> direction(GameEngine.DOWN));
            button(c, 274, 653, 104, 55, "RIGHT", panel, white, () -> direction(GameEngine.RIGHT));
            if (offline || network.isHosting()) {
                button(c, 25, 741, 172, 55, "RESTART", accent, bg, MainActivity.this::restart);
                button(c, 214, 741, 181, 55, "MENU", panel, white, MainActivity.this::confirmLeave);
            } else {
                button(c, 30, 741, 360, 55, "LEAVE MATCH", panel, white, MainActivity.this::confirmLeave);
            }
            if (game.finished) {
                rect(c, 46, 303, 328, 120, Color.rgb(13, 21, 38), 16);
                text(c, "GAME OVER", 210, 349, 22, accent, true);
                text(c, game.result.length() > 27 ? game.result.substring(0, 27) : game.result,
                        210, 383, 17, white, true);
            }
        }

        private void drawHelp(Canvas c) {
            text(c, "HOW TO PLAY", 210, 110, 36, accent, true);
            String[] lines = {
                    "EAT THE RED FRUIT TO GROW", "DON'T HIT WALLS OR SNAKE BODIES",
                    "LAST SNAKE ALIVE WINS", "", "AI MODE:",
                    "CHOOSE EASY, MEDIUM OR HARD", "PLAY COMPLETELY OFFLINE", "",
                    "LAN MULTIPLAYER:", "1. HOST TURNS ON PHONE HOTSPOT",
                    "2. OTHERS CONNECT TO HOTSPOT", "3. HOST TAPS CREATE LAN ROOM",
                    "4. OTHERS ENTER THE ROOM CODE", "5. HOST TAPS START MATCH",
                    "", "IF AUTO JOIN FAILS, ENTER HOST IP"
            };
            for (int i = 0; i < lines.length; i++) {
                text(c, lines[i], 210, 174 + i * 32, 13, (i == 4 || i == 8) ? accent : white,
                        i == 4 || i == 8);
            }
            button(c, 30, 736, 360, 58, "BACK TO MENU", panel, white, () -> { screen = Screen.HOME; invalidate(); });
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = (event.getX() - offsetX) / scale;
            float y = (event.getY() - offsetY) / scale;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startX = x; startY = y; return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (screen == Screen.GAME && startY > 183 && startY < 563 && startX > 20 && startX < 400) {
                    float dx = x - startX, dy = y - startY;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) > 24) {
                        direction(Math.abs(dx) > Math.abs(dy)
                                ? (dx > 0 ? GameEngine.RIGHT : GameEngine.LEFT)
                                : (dy > 0 ? GameEngine.DOWN : GameEngine.UP));
                        return true;
                    }
                }
                for (Tap tap : new ArrayList<>(taps)) {
                    if (tap.rect.contains(x, y)) { tap.action.run(); invalidate(); return true; }
                }
                return true;
            }
            return true;
        }
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this).setTitle("Leave this match?")
                .setMessage("Your current game will end.")
                .setPositiveButton("Leave", (d, which) -> openHome())
                .setNegativeButton("Stay", null).show();
    }
}
