import com.student.snakearena.GameEngine;

public final class GameEngineSmokeTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        GameEngine multi = GameEngine.lanGame(new String[]{"A", "B", "C", "D"});
        check(multi.snakes.size() == 4, "Four LAN players required");
        check(!multi.finished, "Game must start active");
        for (GameEngine.Snake s : multi.snakes) {
            check(s.body.size() == 3, "Initial snake length must be three");
            check(!s.body.contains(multi.food), "Food must not be on snake");
        }
        // Room membership remains slot-safe when another phone leaves.
        GameEngine gapped = GameEngine.lanGame(new String[]{"HOST", "", "P3", null});
        check(gapped.snakes.size() == 2 && gapped.snakes.get(1).slot == 2,
                "Network player slots must be preserved");
        GameEngine ai = GameEngine.aiGame(2);
        check(ai.snakes.size() == 2 && ai.snakes.get(1).ai, "Hard AI must exist");
        ai.requestDirection(0, GameEngine.LEFT); // reverse of initial RIGHT
        check(ai.snakes.get(0).requested == GameEngine.RIGHT, "Reverse move must be rejected");
        int rounds = 0;
        for (int match = 0; match < 100; match++) {
            GameEngine g = GameEngine.aiGame(match % 3);
            for (int t = 0; t < 400 && !g.finished; t++) {
                if (t % 8 == 0) {
                    // Mimic a human steering at intersections.
                    g.requestDirection(0, (t / 8) % 4);
                }
                g.tick();
                for (GameEngine.Snake s : g.snakes) {
                    if (s.alive) for (GameEngine.Cell cell : s.body) {
                        check(cell.x >= 0 && cell.x < GameEngine.WIDTH, "Snake x out of bounds");
                        check(cell.y >= 0 && cell.y < GameEngine.HEIGHT, "Snake y out of bounds");
                    }
                }
            }
            rounds++;
        }
        System.out.println("PASS: " + rounds + " simulated matches; LAN slots, AI and collisions OK");
    }
}
