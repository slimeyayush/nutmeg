package com.example.nutmeg.api.game;

import java.util.List;

import com.example.nutmeg.api.room.GameRoom;

/**
 * Round-robin turn order that quietly steps over anyone who's disconnected.
 * "laps" = how many times round the table before it's done.
 */
public final class Turns {

    private final List<String> order;
    private final int laps;
    private int index = 0;

    public Turns(List<String> order, int laps) {
        this.order = List.copyOf(order);
        this.laps = laps;
    }

    public List<String> order() { return order; }

    public boolean done() {
        return order.isEmpty() || index >= order.size() * laps;
    }

    public String current() {
        return done() ? null : order.get(index % order.size());
    }

    public int lap() {
        return order.isEmpty() ? 0 : index / order.size();
    }

    public boolean isTurnOf(String playerId) {
        return playerId != null && playerId.equals(current());
    }

    /** Move on one turn, then past anyone who isn't there. */
    public void advance(GameRoom room) {
        index++;
        skipAbsent(room);
    }

    public void skipAbsent(GameRoom room) {
        int guard = 0;
        while (!done() && !room.isConnected(current()) && guard++ < order.size() * Math.max(1, laps)) {
            index++;
        }
    }
}
