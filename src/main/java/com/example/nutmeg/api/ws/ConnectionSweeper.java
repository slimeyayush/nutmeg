package com.example.nutmeg.api.ws;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.nutmeg.api.game.GameRunner;
import com.example.nutmeg.api.room.RoomRegistry;

@Component
public class ConnectionSweeper {

    private static final Logger log = LoggerFactory.getLogger(ConnectionSweeper.class);

    private static final Duration PONG_DEADLINE = Duration.ofSeconds(70);
    private static final long GHOST_GRACE_MS = 90_000;
    private static final long ROOM_IDLE_MS = 2 * 60 * 60 * 1000L;

    private final RoomRegistry rooms;
    private final SessionRegistry sessions;
    private final GameRunner games;

    public ConnectionSweeper(RoomRegistry rooms, SessionRegistry sessions, GameRunner games) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.games = games;
    }

    /**
     * Ping every socket every 25s, and hang up on anything silent too long.
     * Free-tier proxies close idle connections after about a minute and nobody
     * gets told — the ping keeps them warm, the missing pong proves they're dead.
     */
    @Scheduled(fixedDelay = 25_000L, initialDelay = 25_000L)
    public void pingAndReap() {
        Instant cutoff = Instant.now().minus(PONG_DEADLINE);
        List<WebSocketSession> all = new ArrayList<>();
        for (Map<String, WebSocketSession> room : sessions.all().values()) {
            all.addAll(room.values());
        }
        for (WebSocketSession session : all) {
            Instant lastPong = (Instant) session.getAttributes().get(WsAttr.LAST_PONG_AT);
            if (lastPong != null && lastPong.isBefore(cutoff)) {
                closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
                continue;
            }
            try {
                session.sendMessage(new PingMessage());
            } catch (Exception ex) {
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            }
        }
    }

    /** Bin lobby players who left and never came back. */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void evictGhosts() {
        rooms.all().forEach(room -> {
            if (room.evictGhosts(GHOST_GRACE_MS)) {
                games.republish(room);
            }
        });
    }

    /** Rooms nobody has touched in two hours go away. */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
    public void evictIdleRooms() {
        for (String code : rooms.evictIdle(ROOM_IDLE_MS)) {
            sessions.sessionsIn(code).values().forEach(s -> closeQuietly(s, CloseStatus.GOING_AWAY));
            sessions.dropRoom(code);
            log.info("room.evicted code={}", code);
        }
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // already gone
        }
    }
}
