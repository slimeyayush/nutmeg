package com.example.nutmeg.api.room;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * One room of mates. All mutable state lives here and is guarded by ONE lock.
 *
 * Rule: GameRunner takes the lock; games run inside it and never take it
 * themselves. The lock is reentrant, so the read helpers below are safe to
 * call from inside withLock too.
 */
public final class GameRoom {

    public static final int MAX_PLAYERS = 10;
    public static final int PALETTE_SIZE = 8;

    private final String code;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, RoomPlayer> players = new LinkedHashMap<>();

    /** Content already used in this room, so a rematch doesn't repeat itself. */
    private final Set<String> usedContent = new HashSet<>();

    private volatile long lastActivityAt;

    private String hostId;
    private int nextColour = 0;
    private GameModeId mode = GameModeId.IMPOSTER;
    private Status status = Status.LOBBY;

    private String stage;
    private int stageSeq = 0;
    private long stageEndsAt = 0;
    private long stageDurationMs = 0;

    private Object state;

    public GameRoom(String code, LongSupplier clock) {
        this.code = code;
        this.clock = clock;
        this.lastActivityAt = clock.getAsLong();
    }

    // ============================================================== locking

    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(Runnable action) {
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    // ============================================================ membership

    public RoomPlayer join(String rawName) {
        return withLock(() -> {
            if (status != Status.LOBBY) {
                throw new RoomException("GAME_IN_PROGRESS", "That game has already kicked off.");
            }
            if (players.size() >= MAX_PLAYERS) {
                throw new RoomException("ROOM_FULL", "Room's full — " + MAX_PLAYERS + " max.");
            }
            String name = cleanName(rawName);
            boolean taken = players.values().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
            if (taken) {
                throw new RoomException("NAME_TAKEN", "Someone in there is already called " + name + ".");
            }
            RoomPlayer player = new RoomPlayer(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    name,
                    nextColour++ % PALETTE_SIZE,
                    now());
            players.put(player.id(), player);
            if (hostId == null) {
                hostId = player.id();
            }
            touch();
            return player;
        });
    }

    public Optional<RoomPlayer> authenticate(String playerId, String token) {
        return withLock(() -> {
            RoomPlayer player = players.get(playerId);
            if (player == null || token == null || !player.token().equals(token)) {
                return Optional.<RoomPlayer>empty();
            }
            return Optional.of(player);
        });
    }

    public void markConnected(String playerId, boolean connected) {
        withLock(() -> {
            RoomPlayer player = players.get(playerId);
            if (player != null) {
                player.setConnected(connected, now());
                touch();
            }
        });
    }

    /**
     * Drops lobby players who left and never came back. Mid-game we hold
     * their seat — losing a player mid-round ruins it for everyone else.
     */
    public boolean evictGhosts(long graceMs) {
        return withLock(() -> {
            if (status == Status.PLAYING) {
                return false;
            }
            long cutoff = now() - graceMs;
            boolean changed = false;
            Iterator<RoomPlayer> it = players.values().iterator();
            while (it.hasNext()) {
                RoomPlayer p = it.next();
                if (!p.connected() && p.disconnectedAt() < cutoff) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed && hostId != null && !players.containsKey(hostId)) {
                hostId = players.isEmpty() ? null : players.keySet().iterator().next();
            }
            return changed;
        });
    }

    /**
     * The host, unless they've dropped — then the longest-standing connected
     * player acts as host, so a room never gets stuck waiting on someone's dead wifi.
     */
    public String effectiveHostId() {
        return withLock(() -> {
            RoomPlayer host = hostId == null ? null : players.get(hostId);
            if (host != null && host.connected()) {
                return hostId;
            }
            return players.values().stream()
                    .filter(RoomPlayer::connected)
                    .map(RoomPlayer::id)
                    .findFirst()
                    .orElse(hostId);
        });
    }

    public boolean isHost(String playerId) {
        return playerId != null && playerId.equals(effectiveHostId());
    }

    // ============================================================= snapshot

    public RoomSnapshot snapshot() {
        return withLock(() -> {
            String host = effectiveHostId();
            List<RoomSnapshot.PlayerView> views = new ArrayList<>(players.size());
            for (RoomPlayer p : players.values()) {
                views.add(new RoomSnapshot.PlayerView(
                        p.id(), p.name(), p.colour(), p.id().equals(host), p.connected(), p.score()));
            }
            return new RoomSnapshot(code, mode, status, stage, stageEndsAt, stageDurationMs, host, views);
        });
    }

    // ================================================== state (inside the lock)

    public String code()             { return code; }
    public long now()                { return clock.getAsLong(); }
    public long lastActivityAt()     { return lastActivityAt; }
    public void touch()              { lastActivityAt = now(); }

    public GameModeId mode()         { return mode; }
    public void setMode(GameModeId m){ this.mode = m; }
    public Status status()           { return status; }
    public String stage()            { return stage; }
    public int stageSeq()            { return stageSeq; }
    public long stageEndsAt()        { return stageEndsAt; }
    public Set<String> usedContent() { return usedContent; }

    @SuppressWarnings("unchecked")
    public <T> T state()             { return (T) state; }
    public void setState(Object s)   { this.state = s; }

    public Collection<RoomPlayer> players() { return players.values(); }
    public RoomPlayer player(String id)     { return players.get(id); }

    public List<String> connectedIds() {
        return players.values().stream().filter(RoomPlayer::connected).map(RoomPlayer::id).toList();
    }

    public boolean isConnected(String playerId) {
        RoomPlayer p = players.get(playerId);
        return p != null && p.connected();
    }

    public int playerCount() {
        return withLock(players::size);
    }

    /**
     * Move to a new stage. Bumps stageSeq — the number a fired timer compares
     * against to discover it's stale.
     *
     * @param durationMs 0 for "no timer"
     */
    public void enterStage(String newStage, long durationMs) {
        this.stage = newStage;
        this.stageSeq++;
        this.stageDurationMs = durationMs;
        this.stageEndsAt = durationMs <= 0 ? 0 : now() + durationMs;
    }

    public void startPlaying() {
        this.status = Status.PLAYING;
        for (RoomPlayer p : players.values()) {
            p.resetScore();
        }
    }

    public void finish() {
        this.status = Status.FINISHED;
        enterStage("FINAL", 0);
    }

    public void backToLobby() {
        this.status = Status.LOBBY;
        this.state = null;
        enterStage(null, 0);
    }

    // ============================================================== helpers

    private static String cleanName(String raw) {
        String name = (raw == null ? "" : raw).strip().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 12) {
            throw new RoomException("BAD_NAME", "Pick a name between 2 and 12 characters.");
        }
        return name;
    }
}
