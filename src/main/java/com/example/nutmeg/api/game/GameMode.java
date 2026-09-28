package com.example.nutmeg.api.game;

import java.util.Map;

import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;

/**
 * One implementation per game. The socket layer never sees a concrete game.
 *
 * CONTRACT: every method is called by GameRunner with the room lock ALREADY
 * HELD. Implementations mutate room state freely but must not take the lock,
 * send anything, or schedule anything — GameRunner publishes and arms the
 * timer after they return. Validate BEFORE mutating: a thrown RoomException
 * means "nothing happened", so leave nothing half-done.
 */
public interface GameMode {

    GameModeId id();

    int minPlayers();

    /** Scores are already reset and status is PLAYING. Deal round one. */
    void start(GameRoom room);

    /** A game-specific verb from a player: SUBMIT_CLUE, PICK, GUESS... */
    void action(GameRoom room, String playerId, String type, Map<String, String> payload);

    /** The stage clock ran out. Advance however this game advances. */
    void onTimeout(GameRoom room);

    /** Someone's socket dropped mid-game. Skip their turn, re-check "everyone answered?". */
    default void onPlayerLeft(GameRoom room, String playerId) {}

    /** This player's private slice of the game. Null in the lobby. */
    Object viewFor(GameRoom room, String playerId);
}
