# Intelligent Systems Assignment — Snake AI Arena

**Suggested paper title:** Design and Development of Snake AI Arena: An Android Multiplayer Snake Game Using Breadth-First Search and Rule-Based Artificial Intelligence

## Objectives

1. Build an offline Android Snake game playable by a human against an intelligent computer opponent.
2. Implement peer-to-peer hotspot LAN multiplayer with a mobile host, room code, and two to four human players.
3. Use pathfinding and safe-move assessment to demonstrate intelligent decision-making.
4. Measure win rate, average score, survival ticks, and decision performance for each AI difficulty.

## Algorithm overview

- **BFS pathfinding:** For each legal direction, calculate the shortest path distance from the proposed next square to food. BFS traverses unoccupied cells and returns 999 if no path exists.
- **Rule-based hazard avoidance:** Reject reverse moves and immediate moves into occupied squares or arena boundaries.
- **Flood-fill reachable area:** In Hard mode, reward next positions that lead to more open squares, which lowers the risk of trapping itself.
- **Opponent risk heuristic:** In Hard mode, penalize positions near an enemy snake's head.
- **Easy AI:** Randomly selects a safe move with 60% probability, otherwise uses the BFS food heuristic.
- **Medium AI:** Uses BFS food distance with a little random tie-breaking.
- **Hard AI:** Uses BFS food distance + available open space + nearby enemy risk.

**Note:** The AI uses a heuristic based on the currently occupied grid; it does *not* guarantee a collision-free route through moving opponents over time. This is a reasonable limitation to discuss during defense.

## Network design

- The Android phone that taps **Create LAN Room** starts a TCP listener on port `38772` and a UDP room-code discovery listener on port `38771`.
- Host generates a random six-digit room code; other phones on the same hotspot broadcast code-scoped discovery requests. Host replies from its local address.
- If UDP discovery is blocked, a player may enter the host IPv4 address manually.
- Clients establish a TCP connection, submit direction commands, and receive authoritative game-state snapshots from the host.
- Server enforces a maximum of four human player slots including the host.
- No cloud service, mobile-data connection, XAMPP, or MacBook server is needed during gameplay.

## Suggested evaluation

| Metric | How to collect |
|---|---|
| Win rate | Wins / total matches per difficulty |
| Survival | Average number of game ticks alive |
| Food collected | Mean score per match |
| Decision success | Percentage of AI moves that avoid immediate collision |
| LAN consistency | Compare snake and food locations on all devices during match |
| Join reliability | Successful joins / 10 attempts per phone model |
| Offline operation | Run AI and LAN after disabling mobile data / internet |

**Experiment:** Perform at least 20 matches per AI difficulty and 10 LAN room joins. Record actual observations; do not fabricate results.

## Demonstration plan

1. Launch Android app; select Easy/Medium/Hard and start an AI match.
2. Show AI avoiding walls and seeking food.
3. Start a hotspot on Phone A, Create Room, show six-digit code.
4. Phones B/C/D join and host starts match.
5. Demonstrate independent controls and win/restart.
6. Explain BFS, flood-fill scoring, and host-authoritative synchronization.

## Known limits

- Real-world hotspot discovery depends on devices' network behavior and has not yet been tested.
- No online matchmaking, cloud database, accounts, sound assets, or touch joystick animations; the design prioritizes a simple classroom prototype.
- Current build workflow produces a debug-signed APK suitable for testing, not a production Play Store release.
