package com.example.nutmeg.api.room;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Every live room, in memory. A room lives about as long as one evening. */
public final class RoomRegistry {

    /** No B/8, I/1, O/0, S/5, Z/2 — nobody has to ask "is that an O or a zero". */
    private static final String ALPHABET = "ACDEFGHJKLMNPQRTUVWXY349";
    private static final int CODE_LENGTH = 4;

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    private final int maxRooms;

    public RoomRegistry(LongSupplier clock, int maxRooms) {
        this.clock = clock;
        this.maxRooms = maxRooms;
    }

    public GameRoom create() {
        if (rooms.size() >= maxRooms) {
            throw new RoomException("AT_CAPACITY", "Server's full right now. Try again in a minute.");
        }
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = randomCode();
            GameRoom room = new GameRoom(code, clock);
            if (rooms.putIfAbsent(code, room) == null) {
                return room;
            }
        }
        throw new RoomException("CODE_EXHAUSTED", "Couldn't allocate a room code. Try again.");
    }

    public Optional<GameRoom> find(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(rooms.get(code.strip().toUpperCase(Locale.ROOT)));
    }

    public GameRoom require(String code) {
        return find(code).orElseThrow(() ->
                new RoomException("ROOM_NOT_FOUND", "No room with that code. Check it and try again."));
    }

    public Collection<GameRoom> all() {
        return rooms.values();
    }

    public int size() {
        return rooms.size();
    }

    /** @return the codes that were removed */
    public List<String> evictIdle(long idleMs) {
        long cutoff = clock.getAsLong() - idleMs;
        List<String> removed = new ArrayList<>();
        rooms.values().removeIf(room -> {
            boolean stale = room.lastActivityAt() < cutoff;
            if (stale) {
                removed.add(room.code());
            }
            return stale;
        });
        return removed;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
