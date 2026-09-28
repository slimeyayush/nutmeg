package com.example.nutmeg.api.ws;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.nutmeg.api.game.GameRunner;
import com.example.nutmeg.api.json.Json;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;
import com.example.nutmeg.api.room.RoomPlayer;
import com.example.nutmeg.api.room.RoomRegistry;

/**
 * Authenticate, find the room, forward. That's all.
 * This class does not know a single game rule — adding a fifth game
 * doesn't touch it.
 */
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_BYTES = 128 * 1024;
    private static final int MAX_INBOUND_CHARS = 4_000;

    private final RoomRegistry rooms;
    private final SessionRegistry sessions;
    private final GameRunner games;

    public GameSocketHandler(RoomRegistry rooms, SessionRegistry sessions, GameRunner games) {
        this.rooms = rooms;
        this.sessions = sessions;
        this.games = games;
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(WsAttr.LAST_PONG_AT, Instant.now());
        // Nothing else. You are nobody until you say HELLO.
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        String text = message.getPayload();
        if (text.length() > MAX_INBOUND_CHARS) {
            reject(rawSession, "TOO_BIG", "Message too large.");
            return;
        }

        String type;
        Map<String, String> payload;
        try {
            Map<String, Object> frame = Json.parseObject(text);
            type = frame.get("type") instanceof String t ? t : "";
            payload = flatten(frame.get("payload"));
        } catch (RuntimeException ex) {
            reject(rawSession, "BAD_MESSAGE", "Couldn't read that message.");
            return;
        }

        String roomCode = (String) rawSession.getAttributes().get(WsAttr.ROOM_CODE);
        String playerId = (String) rawSession.getAttributes().get(WsAttr.PLAYER_ID);

        if (playerId == null) {
            if ("HELLO".equals(type)) {
                handleHello(rawSession, payload);
            } else {
                reject(rawSession, "NOT_AUTHENTICATED", "Say HELLO first.");
            }
            return;
        }

        // Always write through the DECORATED session so sends are serialised.
        WebSocketSession session = sessions.sessionsIn(roomCode).get(playerId);
        if (session == null) {
            session = rawSession;
        }

        GameRoom room = rooms.find(roomCode).orElse(null);
        if (room == null) {
            reject(session, "ROOM_GONE", "That room has closed.");
            return;
        }

        try {
            switch (type) {
                case "PING" -> sessions.send(session, Frames.of("PONG", null));
                case "HELLO" -> reject(session, "ALREADY_IN", "You're already in.");
                case "CHOOSE_MODE" -> games.chooseMode(room, playerId, parseMode(payload.get("mode")));
                case "START_GAME" -> games.start(room, playerId);
                case "BACK_TO_LOBBY" -> games.backToLobby(room, playerId);
                default -> games.action(room, playerId, type, payload);   // the game's business
            }
        } catch (RoomException ex) {
            // Only the person who did the illegal thing hears about it.
            sessions.send(session, Frames.error(ex.code(), ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("ws.action.failed room={} type={}", roomCode, type, ex);
            sessions.send(session, Frames.error("SERVER_ERROR", "Something went wrong. Try again."));
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        session.getAttributes().put(WsAttr.LAST_PONG_AT, Instant.now());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = (String) session.getAttributes().get(WsAttr.ROOM_CODE);
        String playerId = (String) session.getAttributes().get(WsAttr.PLAYER_ID);
        if (roomCode == null || playerId == null) {
            return;
        }
        if (!sessions.detachIfCurrent(roomCode, playerId, session)) {
            return;   // already replaced by a newer socket — do nothing at all
        }
        rooms.find(roomCode).ifPresent(room -> {
            room.markConnected(playerId, false);
            games.playerLeft(room, playerId);
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("ws.transport.error session={} error={}", session.getId(), exception.toString());
        // afterConnectionClosed always follows.
    }

    // ----------------------------------------------------------------- hello

    private void handleHello(WebSocketSession rawSession, Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String playerId = payload.get("playerId");
        String token = payload.get("playerToken");

        if (roomCode == null || playerId == null || token == null) {
            reject(rawSession, "BAD_HELLO", "Missing credentials.");
            return;
        }
        Optional<GameRoom> maybeRoom = rooms.find(roomCode);
        if (maybeRoom.isEmpty()) {
            reject(rawSession, "ROOM_NOT_FOUND", "That room has closed.");
            return;
        }
        GameRoom room = maybeRoom.get();
        Optional<RoomPlayer> maybePlayer = room.authenticate(playerId, token);
        if (maybePlayer.isEmpty()) {
            reject(rawSession, "BAD_TOKEN", "Couldn't verify you.");
            return;
        }
        RoomPlayer me = maybePlayer.get();

        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(
                rawSession, SEND_TIME_LIMIT_MS, SEND_BUFFER_BYTES);
        // The decorator shares the raw session's attribute map, so the raw session
        // that afterConnectionClosed later receives sees these too.
        session.getAttributes().put(WsAttr.ROOM_CODE, room.code());
        session.getAttributes().put(WsAttr.PLAYER_ID, playerId);

        WebSocketSession replaced = sessions.attach(room.code(), playerId, session);
        if (replaced != null && !replaced.getId().equals(session.getId())) {
            sessions.send(replaced, Frames.error("REPLACED", "This room is open in another tab."));
            try {
                replaced.close(new CloseStatus(4000, "Replaced"));
            } catch (Exception ignored) {
                // already gone
            }
        }

        room.markConnected(playerId, true);

        Map<String, Object> you = new LinkedHashMap<>();
        you.put("id", me.id());
        you.put("name", me.name());
        sessions.send(session, Frames.of("WELCOME", Map.of("you", you)));

        games.republish(room);   // every STATE frame comes from one place, in one shape
        log.info("ws.hello room={} name={}", room.code(), me.name());
    }

    // --------------------------------------------------------------- helpers

    /**
     * Send the reason and let the CLIENT hang up. Closing on the same thread as
     * the write can truncate it into a TCP reset, and then the client can't tell
     * "retry" from "give up".
     */
    private void reject(WebSocketSession session, String code, String message) {
        sessions.send(session, Frames.error(code, message));
    }

    private static Map<String, String> flatten(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (k != null && v != null && !(v instanceof Map) && !(v instanceof java.util.List)) {
                    String value = v instanceof Double d && d == Math.rint(d)
                            ? String.valueOf(d.longValue())
                            : String.valueOf(v);
                    out.put(String.valueOf(k), value);
                }
            });
        }
        return out;
    }

    private static GameModeId parseMode(String raw) {
        try {
            return GameModeId.valueOf(raw == null ? "" : raw);
        } catch (IllegalArgumentException ex) {
            throw new RoomException("NO_SUCH_GAME", "That game isn't available.");
        }
    }
}
