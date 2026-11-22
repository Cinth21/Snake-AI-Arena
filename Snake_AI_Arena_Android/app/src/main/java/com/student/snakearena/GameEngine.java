package com.student.snakearena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/** Pure Java gameplay logic; the host is the only device that calls tick(). */
public final class GameEngine {
    public static final int WIDTH = 20;
    public static final int HEIGHT = 20;
    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private final Random random = new Random();
    public final ArrayList<Snake> snakes = new ArrayList<>();
    public Cell food = new Cell(10, 10);
    public boolean finished;
    public String result = "";
    public int tickNumber;
    public int aiDifficulty = 1; // 0 easy, 1 medium, 2 hard

    public static final class Cell {
        public final int x, y;
        public Cell(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Cell)) return false;
            Cell c = (Cell) other;
            return x == c.x && y == c.y;
        }
        @Override public int hashCode() { return x * 43 + y; }
    }

    public static final class Snake {
        public final int slot;
        public final String name;
        public final boolean ai;
        public final ArrayDeque<Cell> body = new ArrayDeque<>();
        public int direction;
        public int requested;
        public boolean alive = true;
        public int score = 0;
        Snake(int slot, String name, boolean ai, int x, int y, int direction) {
            this.slot = slot;
            this.name = name;
            this.ai = ai;
            this.direction = direction;
            this.requested = direction;
            for (int i = 0; i < 3; i++) {
                body.addLast(new Cell(x - DX[direction] * i, y - DY[direction] * i));
            }
        }
    }

    public static GameEngine aiGame(int difficulty) {
        GameEngine g = new GameEngine();
        g.aiDifficulty = Math.max(0, Math.min(2, difficulty));
        g.addSnake(0, "YOU", false);
        g.addSnake(1, "AI BOT", true);
        g.placeFood();
        return g;
    }

    public static GameEngine lanGame(String[] namesBySlot) {
        GameEngine g = new GameEngine();
        for (int i = 0; i < namesBySlot.length && i < 4; i++) {
            if (namesBySlot[i] != null && !namesBySlot[i].trim().isEmpty()) {
                g.addSnake(i, namesBySlot[i], false);
            }
        }
        g.placeFood();
        return g;
    }

    public void addSnake(int slot, String name, boolean isAi) {
        int x, y, d;
        switch (slot) {
            case 0: x = 3; y = 3; d = RIGHT; break;
            case 1: x = WIDTH - 4; y = HEIGHT - 4; d = LEFT; break;
            case 2: x = WIDTH - 4; y = 3; d = DOWN; break;
            default: x = 3; y = HEIGHT - 4; d = UP; break;
        }
        snakes.add(new Snake(slot, name, isAi, x, y, d));
    }

    public void requestDirection(int slot, int direction) {
        if (direction < 0 || direction > 3 || finished) return;
        for (Snake s : snakes) {
            if (s.slot == slot && s.alive && direction != (s.direction + 2) % 4) {
                s.requested = direction;
            }
        }
    }

    public void tick() {
        if (finished) return;
        tickNumber++;
        for (Snake s : snakes) {
            if (s.alive && s.ai) s.requested = chooseAi(s);
            if (s.requested != (s.direction + 2) % 4) s.direction = s.requested;
        }
        ArrayList<Cell> proposed = new ArrayList<>();
        ArrayList<Boolean> eating = new ArrayList<>();
        for (Snake s : snakes) {
            Cell head = s.body.peekFirst();
            Cell next = s.alive ? step(head, s.direction) : head;
            proposed.add(next);
            eating.add(s.alive && next.equals(food));
        }
        HashSet<Cell> blocked = new HashSet<>();
        for (int j = 0; j < snakes.size(); j++) {
            Snake s = snakes.get(j);
            if (!s.alive) continue;
            int i = 0;
            for (Cell segment : s.body) {
                // The tail vacates its square this tick unless the snake eats.
                if (i < s.body.size() - 1 || eating.get(j)) blocked.add(segment);
                i++;
            }
        }
        boolean[] dying = new boolean[snakes.size()];
        for (int i = 0; i < snakes.size(); i++) {
            Snake s = snakes.get(i);
            if (!s.alive) continue;
            Cell p = proposed.get(i);
            if (!inBounds(p) || blocked.contains(p)) dying[i] = true;
            for (int j = i + 1; j < snakes.size(); j++) {
                if (snakes.get(j).alive && p.equals(proposed.get(j))) {
                    dying[i] = true;
                    dying[j] = true;
                }
            }
        }
        boolean ate = false;
        for (int i = 0; i < snakes.size(); i++) {
            Snake s = snakes.get(i);
            if (!s.alive) continue;
            if (dying[i]) {
                s.alive = false;
            } else {
                s.body.addFirst(proposed.get(i));
                if (eating.get(i)) { s.score++; ate = true; }
                else s.body.removeLast();
            }
        }
        if (ate) placeFood();
        checkWinner();
    }

    public void eliminate(int slot) {
        for (Snake s : snakes) if (s.slot == slot) s.alive = false;
        checkWinner();
    }

    private void checkWinner() {
        int count = 0;
        Snake winner = null;
        for (Snake s : snakes) if (s.alive) { count++; winner = s; }
        if (count <= 1) {
            finished = true;
            result = count == 0 ? "DRAW - EVERYONE CRASHED" : winner.name + " WINS!";
        }
    }

    private void placeFood() {
        HashSet<Cell> occupied = new HashSet<>();
        for (Snake s : snakes) occupied.addAll(s.body);
        ArrayList<Cell> free = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            Cell c = new Cell(x, y);
            if (!occupied.contains(c)) free.add(c);
        }
        if (free.isEmpty()) { finished = true; result = "ARENA FILLED!"; return; }
        food = free.get(random.nextInt(free.size()));
    }

    private int chooseAi(Snake s) {
        HashSet<Cell> occupied = new HashSet<>();
        for (Snake other : snakes) {
            for (Cell c : other.body) occupied.add(c);
        }
        ArrayList<Integer> safe = new ArrayList<>();
        for (int d = 0; d < 4; d++) {
            if (d == (s.direction + 2) % 4) continue;
            Cell next = step(s.body.peekFirst(), d);
            if (inBounds(next) && !occupied.contains(next)) safe.add(d);
        }
        if (safe.isEmpty()) return s.direction;
        if (aiDifficulty == 0 && random.nextDouble() < 0.60) {
            return safe.get(random.nextInt(safe.size()));
        }
        int bestDirection = safe.get(0);
        double bestScore = Double.POSITIVE_INFINITY;
        for (int d : safe) {
            Cell next = step(s.body.peekFirst(), d);
            int distance = bfsDistance(next, food, occupied);
            int open = accessibleArea(next, occupied);
            double value = (distance >= 999 ? 600 : distance * 10.0);
            if (aiDifficulty == 2) {
                // Hard mode prioritizes not boxing itself into a dead end.
                value -= Math.min(80, open) * 2.2;
                for (Snake opponent : snakes) {
                    if (opponent.slot != s.slot && opponent.alive) {
                        Cell enemy = opponent.body.peekFirst();
                        int proximity = Math.abs(next.x - enemy.x) + Math.abs(next.y - enemy.y);
                        if (proximity <= 2) value += 45;
                    }
                }
            }
            value += random.nextDouble() * (aiDifficulty == 1 ? 1.0 : 0.2);
            if (value < bestScore) { bestScore = value; bestDirection = d; }
        }
        return bestDirection;
    }

    private static int bfsDistance(Cell start, Cell target, HashSet<Cell> occupied) {
        boolean[][] seen = new boolean[WIDTH][HEIGHT];
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        ArrayDeque<Integer> distances = new ArrayDeque<>();
        queue.add(start); distances.add(0); seen[start.x][start.y] = true;
        while (!queue.isEmpty()) {
            Cell cur = queue.removeFirst(); int dist = distances.removeFirst();
            if (cur.equals(target)) return dist;
            for (int d = 0; d < 4; d++) {
                Cell c = step(cur, d);
                if (inBounds(c) && !seen[c.x][c.y] && !occupied.contains(c)) {
                    seen[c.x][c.y] = true;
                    queue.addLast(c); distances.addLast(dist + 1);
                }
            }
        }
        return 999;
    }

    private static int accessibleArea(Cell start, HashSet<Cell> occupied) {
        boolean[][] seen = new boolean[WIDTH][HEIGHT];
        ArrayDeque<Cell> q = new ArrayDeque<>();
        q.add(start); seen[start.x][start.y] = true;
        int count = 0;
        while (!q.isEmpty() && count < 100) {
            Cell p = q.removeFirst(); count++;
            for (int d = 0; d < 4; d++) {
                Cell n = step(p, d);
                if (inBounds(n) && !seen[n.x][n.y] && !occupied.contains(n)) {
                    seen[n.x][n.y] = true; q.add(n);
                }
            }
        }
        return count;
    }

    private static Cell step(Cell c, int d) { return new Cell(c.x + DX[d], c.y + DY[d]); }
    private static boolean inBounds(Cell c) { return c.x >= 0 && c.y >= 0 && c.x < WIDTH && c.y < HEIGHT; }
}
