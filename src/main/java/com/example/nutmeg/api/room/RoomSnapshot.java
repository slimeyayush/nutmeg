package com.example.nutmeg.api.room;

import java.util.List;

/**
 * The public part of a room. Everything here is safe for every player to see.
 * Times are epoch millis (plain longs) so no JSON library has to guess at date formats.
 */
public record RoomSnapshot(
        String code,
        GameModeId mode,
        Status status,
        String stage,
        long stageEndsAt,
        long stageDurationMs,
        String hostId,
        List<PlayerView> players
) {

    public record PlayerView(
            String id,
            String name,
            int colour,
            boolean host,
            boolean connected,
            int score
    ) {}
}
