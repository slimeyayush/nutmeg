package com.example.nutmeg.api.room;

/** A seat in a room. Survives reconnects; the socket does not. */
public final class RoomPlayer {

    private final String id;      // public — everyone sees it
    private final String token;   // secret — only this player's browser has it
    private final String name;
    private final int colour;     // index into the kit palette, stable per room

    private boolean connected = false;
    private long disconnectedAt;
    private int score = 0;

    RoomPlayer(String id, String token, String name, int colour, long now) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.colour = colour;
        this.disconnectedAt = now;
    }

    public String id()          { return id; }
    public String token()       { return token; }
    public String name()        { return name; }
    public int colour()         { return colour; }
    public boolean connected()  { return connected; }
    public long disconnectedAt(){ return disconnectedAt; }
    public int score()          { return score; }

    void setConnected(boolean value, long now) {
        this.connected = value;
        if (!value) {
            this.disconnectedAt = now;
        }
    }

    public void addScore(int delta) { this.score += delta; }
    void resetScore()               { this.score = 0; }
}
