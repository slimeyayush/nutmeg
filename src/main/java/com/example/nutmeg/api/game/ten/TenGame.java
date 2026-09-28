package com.example.nutmeg.api.game.ten;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.content.Content.TenAnswer;
import com.example.nutmeg.api.content.Content.TenList;
import com.example.nutmeg.api.game.GameMode;
import com.example.nutmeg.api.game.Picker;
import com.example.nutmeg.api.game.Turns;
import com.example.nutmeg.api.match.AnswerMatcher;
import com.example.nutmeg.api.match.AnswerMatcher.Outcome;
import com.example.nutmeg.api.match.Text;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;

/**
 * TEN — a hidden list of ten. Take turns naming one. Right answers score their
 * slot's points for YOU; wrong answers cost the whole room a strike. Three
 * strikes ends the list. Find all ten and everyone gets a bonus.
 */
public final class TenGame implements GameMode {

    static final long INTRO_MS  = 5_000;
    static final long ANSWER_MS = 25_000;
    static final long RESULT_MS = 12_000;

    static final int ROUNDS = 3;
    static final int MAX_STRIKES = 3;
    static final int CLEAR_BONUS = 5;

    private final List<TenList> lists;
    private final Random random;
    private final AnswerMatcher matcher = new AnswerMatcher();

    public TenGame(Content content, Random random) {
        this.lists = content.tenLists();
        this.random = random;
    }

    @Override public GameModeId id()   { return GameModeId.TEN; }
    @Override public int minPlayers()  { return 2; }

    // ------------------------------------------------------------------ state

    public record Entry(String playerId, String text, String kind, int points) {}

    static final class State {
        int round;
        TenList list;
        final Map<Integer, String> foundBy = new LinkedHashMap<>();   // points -> playerId
        int strikes;
        Turns turns;
        final List<Entry> log = new ArrayList<>();
        final Map<String, Integer> gained = new LinkedHashMap<>();
        boolean cleared;
    }

    @Override
    public void start(GameRoom room) {
        State blank = new State();
        room.setState(blank);
        beginRound(room, blank);
    }

    private void beginRound(GameRoom room, State old) {
        State s = new State();
        s.round = old.round + 1;
        s.list = Picker.fresh(lists, l -> "ten:" + l.question(), room.usedContent(), random);
        List<String> order = new ArrayList<>(room.connectedIds());
        Collections.shuffle(order, random);
        s.turns = new Turns(order, 1_000);   // effectively "until strikes or cleared"
        room.setState(s);
        room.enterStage("INTRO", INTRO_MS);
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public void onTimeout(GameRoom room) {
        State s = room.state();
        switch (room.stage()) {
            case "INTRO" -> {
                s.turns.skipAbsent(room);
                continueOrEnd(room, s);
            }
            case "ANSWER" -> {
                s.log.add(new Entry(s.turns.current(), null, "TIMEOUT", 0));
                strike(room, s);
            }
            case "RESULT" -> {
                if (s.round >= ROUNDS) {
                    room.finish();
                } else {
                    beginRound(room, s);
                }
            }
            default -> { }
        }
    }

    @Override
    public void onPlayerLeft(GameRoom room, String playerId) {
        State s = room.state();
        if (s != null && "ANSWER".equals(room.stage()) && s.turns.isTurnOf(playerId)) {
            s.log.add(new Entry(playerId, null, "SKIP", 0));
            s.turns.advance(room);
            continueOrEnd(room, s);
        }
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void action(GameRoom room, String playerId, String type, Map<String, String> payload) {
        if (!"SUBMIT_ANSWER".equals(type)) {
            throw new RoomException("UNKNOWN_ACTION", "That doesn't work in Ten.");
        }
        State s = room.state();
        if (!"ANSWER".equals(room.stage())) {
            throw new RoomException("WRONG_STAGE", "Not right now.");
        }
        if (!s.turns.isTurnOf(playerId)) {
            throw new RoomException("NOT_YOUR_TURN", "Wait for your turn.");
        }
        String raw = payload.get("text");
        String guess = Text.normalise(raw);
        if (guess.isEmpty()) {
            throw new RoomException("EMPTY", "Type an answer.");
        }
        if (raw.length() > 60) {
            throw new RoomException("TOO_LONG", "That's a bit long for one answer.");
        }

        List<TenAnswer> remaining = new ArrayList<>();
        List<TenAnswer> found = new ArrayList<>();
        for (TenAnswer a : s.list.answers()) {
            (s.foundBy.containsKey(a.points()) ? found : remaining).add(a);
        }

        // Exact first. An exact hit on a found answer is a repeat, not a miss.
        AnswerMatcher.Result<TenAnswer> result = matcher.exact(guess, remaining);
        if (result.outcome() == Outcome.MISS) {
            if (matcher.exact(guess, found).outcome() != Outcome.MISS) {
                throw new RoomException("ALREADY_FOUND", "That one's already on the board.");
            }
            result = matcher.fuzzy(guess, remaining);
            if (result.outcome() == Outcome.MISS
                    && matcher.fuzzy(guess, found).outcome() != Outcome.MISS) {
                throw new RoomException("ALREADY_FOUND", "That one's already on the board.");
            }
        }

        switch (result.outcome()) {
            // Never name the candidates — that would reveal two answers at once.
            case AMBIGUOUS -> throw new RoomException("AMBIGUOUS", "Which one? Use their full name.");
            case HIT -> hit(room, s, playerId, raw.strip(), result.single());
            case MISS -> {
                s.log.add(new Entry(playerId, raw.strip(), "MISS", 0));
                strike(room, s);
            }
        }
    }

    private void hit(GameRoom room, State s, String playerId, String typed, TenAnswer answer) {
        s.foundBy.put(answer.points(), playerId);
        room.player(playerId).addScore(answer.points());
        s.gained.merge(playerId, answer.points(), Integer::sum);
        s.log.add(new Entry(playerId, answer.display(), "HIT", answer.points()));

        if (s.foundBy.size() == s.list.answers().size()) {
            s.cleared = true;
            for (String id : s.turns.order()) {
                if (room.player(id) != null) {
                    room.player(id).addScore(CLEAR_BONUS);
                    s.gained.merge(id, CLEAR_BONUS, Integer::sum);
                }
            }
            room.enterStage("RESULT", RESULT_MS);
            return;
        }
        s.turns.advance(room);
        continueOrEnd(room, s);
    }

    private void strike(GameRoom room, State s) {
        s.strikes++;
        if (s.strikes >= MAX_STRIKES) {
            room.enterStage("RESULT", RESULT_MS);
            return;
        }
        s.turns.advance(room);
        continueOrEnd(room, s);
    }

    private void continueOrEnd(GameRoom room, State s) {
        if (s.turns.done()) {
            room.enterStage("RESULT", RESULT_MS);
        } else {
            room.enterStage("ANSWER", ANSWER_MS);
        }
    }

    // ------------------------------------------------------------------- view

    public record Slot(int points, String answer, String foundBy) {}

    public record View(
            int round,
            int rounds,
            String question,
            List<Slot> slots,
            int strikes,
            int maxStrikes,
            String turnPlayerId,
            boolean yourTurn,
            List<Entry> log,
            boolean revealed,
            boolean cleared,
            Map<String, Integer> gained) {}

    @Override
    public Object viewFor(GameRoom room, String playerId) {
        State s = room.state();
        if (s == null) {
            return null;
        }
        boolean revealed = "RESULT".equals(room.stage()) || "FINAL".equals(room.stage());
        List<Slot> slots = new ArrayList<>();
        for (TenAnswer a : s.list.answers()) {
            String by = s.foundBy.get(a.points());
            slots.add(new Slot(a.points(), by != null || revealed ? a.display() : null, by));
        }
        int from = Math.max(0, s.log.size() - 12);
        return new View(
                s.round,
                ROUNDS,
                s.list.question(),
                slots,
                s.strikes,
                MAX_STRIKES,
                "ANSWER".equals(room.stage()) ? s.turns.current() : null,
                "ANSWER".equals(room.stage()) && s.turns.isTurnOf(playerId),
                List.copyOf(s.log.subList(from, s.log.size())),
                revealed,
                s.cleared,
                Map.copyOf(s.gained));
    }
}
