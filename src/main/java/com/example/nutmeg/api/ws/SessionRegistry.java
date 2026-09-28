package com.example.nutmeg.api.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.nutmeg.api.game.GameRunner;

/**
 * roomCode -> playerId -> that player's ONE live socket.
 * Keyed by what a socket represents (a player), not by object identity —
 * decorated and raw sessions are different objects, but share an id.
 */
@Component
public class SessionRegistry implements GameRunner.Outbox {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final Map<String, Map<String, WebSocketSession>> byRoom = new ConcurrentHashMap<>();

    /** @return the socket this one replaced, or null. The caller closes it. */
    public WebSocketSession attach(String roomCode, String playerId, WebSocketSession session) {
        return byRoom.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>()).put(playerId, session);
    }

    /**
     * Compare-and-remove. A late close event from a socket that was already
     * replaced (wifi -> 4G) must not evict the new one.
     */
    public boolean detachIfCurrent(String roomCode, String playerId, WebSocketSession session) {
        Map<String, WebSocketSession> room = byRoom.get(roomCode);
        if (room == null) {
            return false;
        }
        WebSocketSession current = room.get(playerId);
        if (current == null || !current.getId().equals(session.getId())) {
            return false;
        }
        room.remove(playerId);
        if (room.isEmpty()) {
            byRoom.remove(roomCode, room);
        }
        return true;
    }

    public Map<String, WebSocketSession> sessionsIn(String roomCode) {
        return byRoom.getOrDefault(roomCode, Map.of());
    }

    public Map<String, Map<String, WebSocketSession>> all() {
        return byRoom;
    }

    public void dropRoom(String roomCode) {
        byRoom.remove(roomCode);
    }

    public void send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException ex) {
            log.debug("send.failed session={} reason={}", session.getId(), ex.toString());
        }
    }

    @Override
    public void sendEach(String roomCode, Function<String, String> jsonForPlayer) {
        sessionsIn(roomCode).forEach((playerId, session) -> {
            String json = jsonForPlayer.apply(playerId);
            if (json != null) {
                send(session, json);
            }
        });
    }
}
