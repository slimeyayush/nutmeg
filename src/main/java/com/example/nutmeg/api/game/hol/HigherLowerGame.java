package com.example.nutmeg.api.game.hol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.content.Content.HolCategory;
import com.example.nutmeg.api.content.Content.HolItem;
import com.example.nutmeg.api.game.GameMode;
import com.example.nutmeg.api.game.Picker;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;

/**
 * HIGHER OR LOWER — everyone sees the left card's number and guesses whether
 * the right card is higher or lower, all at once. Correct = 1 point, fastest
 * correct = 1 more. Five comparisons per category, three categories.
 */
public final class HigherLowerGame implements GameMode {

    static final long INTRO_MS  = 4_000;
    static final long PICK_MS   = 12_000;
    static final long REVEAL_MS = 5_000;

    static final int ROUNDS = 3;
    static final int STEPS = 5;

    private final List<HolCategory> categories;
    private final Random random;

    public HigherLowerGame(Content content, Random random) {
        this.categories = content.holCategories();
        this.random = random;
    }

    @Override public GameModeId id()   { return GameModeId.HIGHER_LOWER; }
    @Override public int minPlayers()  { return 2; }

    // ------------------------------------------------------------------ state

    static final class State {
        int round;
        HolCategory category;
        List<HolItem> deck;
        int step;                                            // compares deck[step] vs deck[step+1]
        final Map<String, String> picks = new LinkedHashMap<>();
        final Map<String, Long> pickedAt = new LinkedHashMap<>();
        String answer;                                        // HIGHER, LOWER or EITHER once revealed
        String fastestId;
        final Map<String, Integer> gained = new LinkedHashMap<>();
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
        s.category = Picker.fresh(categories, c -> "hol:" + c.label(), room.usedContent(), random);
        s.deck = buildDeck(s.category.items());
        room.setState(s);
        room.enterStage("INTRO", INTRO_MS);
    }

    /** STEPS + 1 cards, trying hard to avoid two equal values side by side. */
    private List<HolItem> buildDeck(List<HolItem> items) {
        List<HolItem> best = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            List<HolItem> shuffled = new ArrayList<>(items);
            Collections.shuffle(shuffled, random);
            List<HolItem> deck = shuffled.subList(0, Math.min(STEPS + 1, shuffled.size()));
            best = deck;
            boolean clean = true;
            for (int i = 0; i + 1 < deck.size(); i++) {
                if (deck.get(i).value() == deck.get(i + 1).value()) {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                return List.copyOf(deck);
            }
        }
        return List.copyOf(best);
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public void onTimeout(GameRoom room) {
        State s = room.state();
        switch (room.stage()) {
            case "INTRO" -> room.enterStage("PICK", PICK_MS);
            case "PICK" -> reveal(room, s);
            case "REVEAL" -> {
                if (s.step + 1 < STEPS && s.step + 2 < s.deck.size()) {
                    s.step++;
                    s.picks.clear();
                    s.pickedAt.clear();
                    s.answer = null;
                    s.fastestId = null;
                    s.gained.clear();
                    room.enterStage("PICK", PICK_MS);
                } else if (s.round >= ROUNDS) {
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
        if (s != null && "PICK".equals(room.stage()) && everyonePicked(room, s)) {
            reveal(room, s);
        }
    }

    @Override
    public void action(GameRoom room, String playerId, String type, Map<String, String> payload) {
        if (!"PICK".equals(type)) {
            throw new RoomException("UNKNOWN_ACTION", "That doesn't work in Higher or Lower.");
        }
        State s = room.state();
        if (!"PICK".equals(room.stage())) {
            throw new RoomException("WRONG_STAGE", "Not right now.");
        }
        String choice = payload.get("choice");
        if (!"HIGHER".equals(choice) && !"LOWER".equals(choice)) {
            throw new RoomException("BAD_CHOICE", "Pick higher or lower.");
        }
        if (s.picks.containsKey(playerId)) {
            throw new RoomException("LOCKED_IN", "You're locked in.");
        }
        s.picks.put(playerId, choice);
        s.pickedAt.put(playerId, room.now());
        if (everyonePicked(room, s)) {
            reveal(room, s);
        }
    }

    private static boolean everyonePicked(GameRoom room, State s) {
        return room.connectedIds().stream().allMatch(s.picks::containsKey);
    }

    private void reveal(GameRoom room, State s) {
        long left = s.deck.get(s.step).value();
        long right = s.deck.get(s.step + 1).value();
        s.answer = right > left ? "HIGHER" : right < left ? "LOWER" : "EITHER";

        String fastest = null;
        long fastestAt = Long.MAX_VALUE;
        for (Map.Entry<String, String> pick : s.picks.entrySet()) {
            boolean correct = "EITHER".equals(s.answer) || pick.getValue().equals(s.answer);
            if (!correct || room.player(pick.getKey()) == null) {
                continue;
            }
            award(room, s, pick.getKey(), 1);
            long at = s.pickedAt.get(pick.getKey());
            if (at < fastestAt) {
                fastestAt = at;
                fastest = pick.getKey();
            }
        }
        if (fastest != null && s.picks.size() > 1) {
            award(room, s, fastest, 1);                     // speed bonus
            s.fastestId = fastest;
        }
        room.enterStage("REVEAL", REVEAL_MS);
    }

    private static void award(GameRoom room, State s, String playerId, int points) {
        room.player(playerId).addScore(points);
        s.gained.merge(playerId, points, Integer::sum);
    }

    // ------------------------------------------------------------------- view

    public record Side(String name, Long value) {}

    public record View(
            int round,
            int rounds,
            int step,
            int steps,
            String category,
            String question,
            String higherWord,
            String lowerWord,
            Side left,
            Side right,
            String yourPick,
            List<String> lockedIn,
            boolean revealed,
            String answer,
            Map<String, String> picks,
            String fastestId,
            Map<String, Integer> gained) {}

    @Override
    public Object viewFor(GameRoom room, String playerId) {
        State s = room.state();
        if (s == null) {
            return null;
        }
        boolean revealed = s.answer != null
                && ("REVEAL".equals(room.stage()) || "FINAL".equals(room.stage()));
        HolItem left = s.deck.get(s.step);
        HolItem right = s.deck.get(s.step + 1);
        return new View(
                s.round,
                ROUNDS,
                s.step + 1,
                Math.min(STEPS, s.deck.size() - 1),
                s.category.label(),
                s.category.question(),
                s.category.higherWord(),
                s.category.lowerWord(),
                new Side(left.name(), left.value()),
                new Side(right.name(), revealed ? right.value() : null),   // hidden until reveal
                s.picks.get(playerId),
                List.copyOf(s.picks.keySet()),                             // who, not what
                revealed,
                revealed ? s.answer : null,
                revealed ? Map.copyOf(s.picks) : Map.of(),
                revealed ? s.fastestId : null,
                revealed ? Map.copyOf(s.gained) : Map.of());
    }
}
