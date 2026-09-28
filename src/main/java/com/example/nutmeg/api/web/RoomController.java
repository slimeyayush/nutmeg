package com.example.nutmeg.api.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomPlayer;
import com.example.nutmeg.api.room.RoomRegistry;
import com.example.nutmeg.api.room.RoomSnapshot;
import com.example.nutmeg.api.room.Status;

/**
 * The only REST surface: make a room, join a room, peek at a room.
 * Everything after joining happens over the WebSocket.
 * DTOs are plain strings/ints so they serialise the same on Jackson 2 or 3.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRegistry rooms;

    public RoomController(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    public record JoinRequest(String name) {}

    public record JoinResponse(String roomCode, String playerId, String playerToken) {}

    public record RoomPeek(String code, String status, String mode, int players, List<String> names) {}

    @PostMapping
    public ResponseEntity<JoinResponse> create(@RequestBody JoinRequest request) {
        GameRoom room = rooms.create();
        RoomPlayer me = room.join(request == null ? null : request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new JoinResponse(room.code(), me.id(), me.token()));
    }

    @PostMapping("/{code}/players")
    public JoinResponse join(@PathVariable("code") String code, @RequestBody JoinRequest request) {
        GameRoom room = rooms.require(code);
        RoomPlayer me = room.join(request == null ? null : request.name());
        return new JoinResponse(room.code(), me.id(), me.token());
    }

    @GetMapping("/{code}")
    public RoomPeek peek(@PathVariable("code") String code) {
        RoomSnapshot s = rooms.require(code).snapshot();
        return new RoomPeek(
                s.code(),
                s.status().name(),
                s.mode().name(),
                s.players().size(),
                s.players().stream().map(RoomSnapshot.PlayerView::name).toList());
    }

    @GetMapping("/stats")
    public java.util.Map<String, Object> stats() {
        long playing = rooms.all().stream()
                .filter(r -> r.withLock(() -> r.status() == Status.PLAYING)).count();
        return java.util.Map.of("rooms", rooms.size(), "playing", playing);
    }
}
