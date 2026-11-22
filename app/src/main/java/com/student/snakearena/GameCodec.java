package com.student.snakearena;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Compact, versioned full-state snapshots to keep clients in sync. */
public final class GameCodec {
    private GameCodec() { }

    public static JSONObject encode(GameEngine g) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("v", 1);
        out.put("tick", g.tickNumber);
        out.put("foodX", g.food.x);
        out.put("foodY", g.food.y);
        out.put("finished", g.finished);
        out.put("result", g.result);
        JSONArray players = new JSONArray();
        for (GameEngine.Snake s : g.snakes) {
            JSONObject snake = new JSONObject();
            snake.put("slot", s.slot);
            snake.put("name", s.name);
            snake.put("ai", s.ai);
            snake.put("dir", s.direction);
            snake.put("alive", s.alive);
            snake.put("score", s.score);
            JSONArray body = new JSONArray();
            for (GameEngine.Cell c : s.body) {
                JSONArray xy = new JSONArray(); xy.put(c.x); xy.put(c.y); body.put(xy);
            }
            snake.put("body", body);
            players.put(snake);
        }
        out.put("players", players);
        return out;
    }

    public static GameEngine decode(JSONObject data) throws JSONException {
        if (data.getInt("v") != 1) throw new JSONException("Unsupported game state version");
        GameEngine result = new GameEngine();
        result.tickNumber = data.getInt("tick");
        result.food = new GameEngine.Cell(data.getInt("foodX"), data.getInt("foodY"));
        result.finished = data.getBoolean("finished");
        result.result = data.getString("result");
        JSONArray snakes = data.getJSONArray("players");
        for (int i = 0; i < snakes.length(); i++) {
            JSONObject json = snakes.getJSONObject(i);
            int slot = json.getInt("slot");
            GameEngine.Snake s = new GameEngine.Snake(slot, json.getString("name"), json.getBoolean("ai"), 3, 3, 1);
            s.body.clear();
            s.direction = json.getInt("dir");
            s.requested = s.direction;
            s.alive = json.getBoolean("alive");
            s.score = json.getInt("score");
            JSONArray body = json.getJSONArray("body");
            for (int j = 0; j < body.length(); j++) {
                JSONArray xy = body.getJSONArray(j);
                s.body.addLast(new GameEngine.Cell(xy.getInt(0), xy.getInt(1)));
            }
            result.snakes.add(s);
        }
        return result;
    }
}
