package com.example.nutmeg.api.game;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.example.nutmeg.api.json.Json;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;
import com.example.nutmeg.api.room.RoomPlayer;
import com.example.nutmeg.api.room.RoomRegistry;
import com.example.nutmeg.api.room.Status;

/**
 * The only thing the socket layer talks to. Owns the three pieces every game
 * shares: taking the room lock, publishing personalised state, and the fenced
 * stage timer. Plain Java — no Spring — so a whole match can be simulated in a test.
 */
public final class GameRunner {

    private static final System.Logger LOG = System.getLogger(GameRunner.class.getName());

    /** "Run this at that epoch-millis instant." Spring's TaskScheduler in prod, a fake clock in tests. */
    public interface Scheduler {
        void schedule(Runnable task, long atEpochMillis);
    }

    /** "Send each connected player of this room their own JSON." */
    public interface Outbox {
        void sendEach(String roomCode, Function<String, String> jsonForPlayer);
    }

    private final RoomRegistry rooms;
    private final Scheduler scheduler;
    private final Outbox outbox;
    private final Map<GameModeId, GameMode> modes = new EnumMap<>(GameModeId.class);

    public GameRunner(RoomRegistry rooms, Scheduler scheduler, Outbox outbox, List<GameMode> games) {
        this.rooms = rooms;
        this.scheduler = scheduler;
        this.outbox = outbox;
        for (GameMode game : games) {
            modes.put(game.id(), game);
        }
    }

    public boolean supports(GameModeId id) {
        return modes.containsKey(id);
    }

    // ========================================================= player entry points

    public void chooseMode(GameRoom room, String playerId, GameModeId newMode) {
        if (!supports(newMode)) {
            throw new RoomException("NO_SUCH_GAME", "That game isn't available.");
        }
        room.withLock(() -> {
            requireHost(room, playerId);
            if (room.status() == Status.PLAYING) {
                throw new RoomException("ALREADY_RUNNING", "Finish this game first.");
            }
            room.setMode(newMode);
            room.backToLobby();
            room.touch();
        });
        publish(room);
    }

    public void start(GameRoom room, String playerId) {
        room.withLock(() -> {
            requireHost(room, playerId);
            if (room.status() == Status.PLAYING) {
                throw new RoomException("ALREADY_RUNNING", "Game's already going.");
            }
            GameMode game = modeFor(room);
            int online = room.connectedIds().size();
            if (online < game.minPlayers()) {
                throw new RoomException("NOT_ENOUGH_PLAYERS",
                        "This one needs at least " + game.minPlayers() + " players online.");
            }
            room.startPlaying();
            room.setState(null);
            game.start(room);
            room.touch();
        });
        publishAndArm(room);
    }

    public void backToLobby(GameRoom room, String playerId) {
        room.withLock(() -> {
            requireHost(room, playerId);
            if (room.status() == Status.PLAYING) {
                throw new RoomException("ALREADY_RUNNING", "Finish this game first.");
            }
            room.backToLobby();
            room.touch();
        });
        publish(room);
    }

    public void action(GameRoom room, String playerId, String type, Map<String, String> payload) {
        room.withLock(() -> {
            if (room.status() != Status.PLAYING) {
                throw new RoomException("NOT_PLAYING", "There's no game running right now.");
            }
            modeFor(room).action(room, playerId, type, payload == null ? Map.of() : payload);
            room.touch();
        });
        publishAndArm(room);
    }

    /** Someone's socket closed. */
    public void playerLeft(GameRoom room, String playerId) {
        boolean arm = room.withLock(() -> {
            if (room.status() != Status.PLAYING) {
                return false;
            }
            int before = room.stageSeq();
            modeFor(room).onPlayerLeft(room, playerId);
            return room.stageSeq() != before;
        });
        if (arm) {
            publishAndArm(room);
        } else {
            publish(room);
        }
    }

    /** Someone connected, or ghosts were evicted. Nothing advanced, so no new timer. */
    public void republish(GameRoom room) {
        publish(room);
    }

    // ================================================================ the timer

    /**
     * Fired at stageEndsAt. The seq comparison is the whole point: if the stage
     * already moved on because everyone answered early, this timer is stale.
     */
    private void onTimeout(String roomCode, int expectedSeq) {
        GameRoom room = rooms.find(roomCode).orElse(null);
        if (room == null) {
            return;                                   // room evicted — let the timer die
        }
        boolean acted = room.withLock(() -> {
            if (room.stageSeq() != expectedSeq || room.status() != Status.PLAYING) {
                return false;                         // ← the fence
            }
            if (room.connectedIds().isEmpty()) {
                room.finish();                        // everyone left — stop ticking forever
                return true;
            }
            try {
                modeFor(room).onTimeout(room);
            } catch (RuntimeException ex) {
                // A bug in one game must never leave a room frozen mid-stage.
                LOG.log(System.Logger.Level.ERROR, "timeout failed in room " + roomCode, ex);
                room.finish();
            }
            return true;
        });
        if (acted) {
            publishAndArm(room);
        }
    }

    private void arm(GameRoom room) {
        record Fence(int seq, long endsAt, boolean playing) {}
        Fence fence = room.withLock(() ->
                new Fence(room.stageSeq(), room.stageEndsAt(), room.status() == Status.PLAYING));
        if (!fence.playing() || fence.endsAt() <= 0) {
            return;
        }
        scheduler.schedule(() -> onTimeout(room.code(), fence.seq()), fence.endsAt());
    }

    // =============================================================== publishing

    public void publishAndArm(GameRoom room) {
        publish(room);
        arm(room);
    }

    /** Builds every player's envelope under the lock, sends them outside it. */
    public void publish(GameRoom room) {
        Map<String, String> byPlayer = room.withLock(() -> {
            GameMode game = modes.get(room.mode());
            Object snapshot = room.snapshot();
            long now = room.now();
            Map<String, String> out = new HashMap<>();
            for (RoomPlayer player : room.players()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("room", snapshot);
                payload.put("now", now);
                payload.put("game", game == null || room.state() == null
                        ? null
                        : game.viewFor(room, player.id()));
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("type", "STATE");
                envelope.put("payload", payload);
                out.put(player.id(), Json.write(envelope));
            }
            return out;
        });
        outbox.sendEach(room.code(), byPlayer::get);
    }

    // ================================================================= helpers

    private GameMode modeFor(GameRoom room) {
        GameMode game = modes.get(room.mode());
        if (game == null) {
            throw new RoomException("NO_SUCH_GAME", "That game isn't available.");
        }
        return game;
    }

    private static void requireHost(GameRoom room, String playerId) {
        if (!room.isHost(playerId)) {
            throw new RoomException("NOT_HOST", "Only the host can do that.");
        }
    }
}
