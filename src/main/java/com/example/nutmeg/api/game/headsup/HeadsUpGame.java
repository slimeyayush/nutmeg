package com.example.nutmeg.api.game.headsup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.content.Content.Card;
import com.example.nutmeg.api.game.GameMode;
import com.example.nutmeg.api.game.Turns;
import com.example.nutmeg.api.match.AnswerMatcher;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;

/**
 * HEADS UP — one player is in the hot seat and can't see the card. Everyone
 * else can, and describes it out loud on the call. The guesser types guesses
 * (checked by the matcher) or a describer taps "Got it". 60 seconds each.
 */
public final class HeadsUpGame implements GameMode {

    static final long READY_MS    = 5_000;
    static final long PLAY_MS     = 60_000;
    static final long TURN_END_MS = 7_000;

    private final List<Card> cards;
    private final Random random;
    private final AnswerMatcher matcher = new AnswerMatcher();

    public HeadsUpGame(Content content, Random random) {
        this.cards = content.headsUpCards();
        this.random = random;
    }

    @Override public GameModeId id()   { return GameModeId.HEADS_UP; }
    @Override public int minPlayers()  { return 2; }

    // ------------------------------------------------------------------ state

    public record Done(String name, String result) {}

    static final class State {
        Turns turns;
        int turnNumber;
        List<Card> deck;
        int deckPos;
        Card card;
        int cardNo;                                   // bumps on every new card — the idempotency key
        final List<Done> history = new ArrayList<>();
    }

    @Override
    public void start(GameRoom room) {
        State s = new State();
        List<String> order = new ArrayList<>(room.connectedIds());
        Collections.shuffle(order, random);
        s.turns = new Turns(order, 1);
        s.deck = freshDeck(room);
        room.setState(s);
        beginTurn(room, s);
    }

    /** Unseen cards first, shuffled; seen ones after, so a long game never runs dry. */
    private List<Card> freshDeck(GameRoom room) {
        List<Card> unseen = new ArrayList<>();
        List<Card> seen = new ArrayList<>();
        for (Card c : cards) {
            (room.usedContent().contains("hu:" + c.name()) ? seen : unseen).add(c);
        }
        Collections.shuffle(unseen, random);
        Collections.shuffle(seen, random);
        unseen.addAll(seen);
        return unseen;
    }

    private void beginTurn(GameRoom room, State s) {
        s.turns.skipAbsent(room);
        if (s.turns.done()) {
            room.finish();
            return;
        }
        s.turnNumber++;
        s.history.clear();
        s.card = null;
        room.enterStage("READY", READY_MS);
    }

    private void nextCard(GameRoom room, State s) {
        if (s.deckPos >= s.deck.size()) {
            s.deck = freshDeck(room);
            s.deckPos = 0;
        }
        s.card = s.deck.get(s.deckPos++);
        s.cardNo++;
        room.usedContent().add("hu:" + s.card.name());
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public void onTimeout(GameRoom room) {
        State s = room.state();
        switch (room.stage()) {
            case "READY" -> {
                nextCard(room, s);
                room.enterStage("PLAY", PLAY_MS);
            }
            case "PLAY" -> {
                s.card = null;
                room.enterStage("TURN_END", TURN_END_MS);
            }
            case "TURN_END" -> {
                s.turns.advance(room);
                beginTurn(room, s);
            }
            default -> { }
        }
    }

    @Override
    public void onPlayerLeft(GameRoom room, String playerId) {
        State s = room.state();
        if (s == null || !s.turns.isTurnOf(playerId)) {
            return;
        }
        if ("READY".equals(room.stage()) || "PLAY".equals(room.stage())) {
            s.card = null;
            room.enterStage("TURN_END", TURN_END_MS);
        }
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void action(GameRoom room, String playerId, String type, Map<String, String> payload) {
        State s = room.state();
        if (!"PLAY".equals(room.stage()) || s.card == null) {
            throw new RoomException("WRONG_STAGE", "Not right now.");
        }
        // Two describers tap "Got it" in the same instant: the second tap names a
        // card that's already gone. Without this check it would score the NEXT card.
        String cardNo = payload.get("cardNo");
        if (cardNo != null && !cardNo.equals(String.valueOf(s.cardNo))) {
            throw new RoomException("STALE_CARD", "Already moved on.");
        }
        boolean guesser = s.turns.isTurnOf(playerId);

        switch (type) {
            case "GUESS" -> {
                if (!guesser) {
                    throw new RoomException("NOT_GUESSER", "Only the person in the hot seat guesses.");
                }
                var result = matcher.match(payload.get("text"), List.of(s.card));
                if (result.outcome() != AnswerMatcher.Outcome.HIT) {
                    throw new RoomException("NOT_IT", "Not it. Keep going.");
                }
                scored(room, s);
            }
            case "GOT_IT" -> {
                if (guesser) {
                    throw new RoomException("NOT_DESCRIBER", "Nice try. Type your guess instead.");
                }
                scored(room, s);
            }
            case "PASS" -> {
                s.history.add(new Done(s.card.name(), "PASS"));
                nextCard(room, s);
            }
            default -> throw new RoomException("UNKNOWN_ACTION", "That doesn't work in Heads Up.");
        }
    }

    private void scored(GameRoom room, State s) {
        String guesserId = s.turns.current();
        if (room.player(guesserId) != null) {
            room.player(guesserId).addScore(1);
        }
        s.history.add(new Done(s.card.name(), "GOT"));
        nextCard(room, s);
    }

    // ------------------------------------------------------------------- view

    public record View(
            int turn,
            int turns,
            String guesserId,
            boolean youAreGuesser,
            String card,
            String cardKind,
            int cardNo,
            List<Done> history,
            int gotThisTurn) {}

    @Override
    public Object viewFor(GameRoom room, String playerId) {
        State s = room.state();
        if (s == null) {
            return null;
        }
        boolean guesser = s.turns.isTurnOf(playerId);
        boolean showCard = s.card != null && !guesser;
        int got = (int) s.history.stream().filter(d -> "GOT".equals(d.result())).count();
        return new View(
                s.turnNumber,
                s.turns.order().size(),
                s.turns.current(),
                guesser,
                showCard ? s.card.name() : null,               // the guesser never receives it
                s.card == null ? null : s.card.kind(),
                s.cardNo,
                List.copyOf(s.history),
                got);
    }
}
