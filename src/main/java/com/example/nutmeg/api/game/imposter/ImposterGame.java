package com.example.nutmeg.api.game.imposter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.game.GameMode;
import com.example.nutmeg.api.game.Picker;
import com.example.nutmeg.api.game.Turns;
import com.example.nutmeg.api.match.AnswerMatcher;
import com.example.nutmeg.api.match.Text;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;

/**
 * IMPOSTER — everyone gets the same footballer except one player, who gets a
 * similar one and doesn't know they're the odd one out. One-word clues, twice
 * round the table, then vote. A caught imposter can steal by naming the real player.
 */
public final class ImposterGame implements GameMode {

    static final long DEAL_MS   = 6_000;
    static final long CLUE_MS   = 30_000;
    static final long VOTE_MS   = 45_000;
    static final long STEAL_MS  = 25_000;
    static final long RESULT_MS = 12_000;

    static final int ROUNDS = 3;
    static final int PASSES = 2;

    static final int POINTS_SURVIVE = 3;
    static final int POINTS_CORRECT_VOTE = 2;
    static final int POINTS_STEAL = 2;

    private final List<List<String>> clusters;
    private final Random random;
    private final AnswerMatcher matcher = new AnswerMatcher();

    public ImposterGame(Content content, Random random) {
        this.clusters = content.imposterClusters();
        this.random = random;
    }

    @Override public GameModeId id()   { return GameModeId.IMPOSTER; }
    @Override public int minPlayers()  { return 3; }

    // ------------------------------------------------------------------ state

    public record Clue(String playerId, String word, int pass) {}

    static final class State {
        int round;
        String secret;
        String decoy;
        String imposterId;
        Turns turns;
        final List<Clue> clues = new ArrayList<>();
        final Map<String, String> votes = new LinkedHashMap<>();
        String accusedId;
        boolean caught;
        String stealGuess;
        boolean stealOk;
        final Map<String, Integer> gained = new LinkedHashMap<>();
    }

    // -------------------------------------------------------------- lifecycle

    @Override
    public void start(GameRoom room) {
        State s = new State();
        room.setState(s);
        beginRound(room, s);
    }

    private void beginRound(GameRoom room, State old) {
        State s = new State();
        s.round = old.round + 1;

        List<String> cluster = Picker.fresh(clusters, c -> "imp:" + String.join("|", c),
                room.usedContent(), random);
        List<String> pair = new ArrayList<>(cluster);
        Collections.shuffle(pair, random);
        s.secret = pair.get(0);
        s.decoy = pair.get(1);

        List<String> order = new ArrayList<>(room.connectedIds());
        Collections.shuffle(order, random);
        s.imposterId = order.get(random.nextInt(order.size()));
        s.turns = new Turns(order, PASSES);

        room.setState(s);
        room.enterStage("DEAL", DEAL_MS);
    }

    @Override
    public void onTimeout(GameRoom room) {
        State s = room.state();
        switch (room.stage()) {
            case "DEAL" -> {
                s.turns.skipAbsent(room);
                goToCluesOrVote(room, s);
            }
            case "CLUES" -> {
                s.clues.add(new Clue(s.turns.current(), null, s.turns.lap()));   // ran out of time
                nextTurn(room, s);
            }
            case "VOTE" -> tally(room, s);
            case "STEAL" -> room.enterStage("RESULT", RESULT_MS);
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
        if (s == null) {
            return;
        }
        switch (room.stage()) {
            case "CLUES" -> {
                if (s.turns.isTurnOf(playerId)) {
                    nextTurn(room, s);                // skip them, no penalty
                }
            }
            case "VOTE" -> {
                if (everyoneVoted(room, s)) {
                    tally(room, s);
                }
            }
            case "STEAL" -> {
                if (playerId.equals(s.imposterId)) {
                    room.enterStage("RESULT", RESULT_MS);
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void action(GameRoom room, String playerId, String type, Map<String, String> payload) {
        State s = room.state();
        switch (type) {
            case "SUBMIT_CLUE" -> submitClue(room, s, playerId, payload.get("word"));
            case "CAST_VOTE" -> castVote(room, s, playerId, payload.get("suspectId"));
            case "SUBMIT_STEAL" -> submitSteal(room, s, playerId, payload.get("guess"));
            default -> throw new RoomException("UNKNOWN_ACTION", "That doesn't work in Imposter.");
        }
    }

    private void submitClue(GameRoom room, State s, String playerId, String raw) {
        requireStage(room, "CLUES");
        if (!s.turns.isTurnOf(playerId)) {
            throw new RoomException("NOT_YOUR_TURN", "Wait for your turn.");
        }
        String word = cleanClue(raw);
        if (givesItAway(word, wordFor(s, playerId))) {
            throw new RoomException("TOO_OBVIOUS", "You can't use the player's name.");
        }
        s.clues.add(new Clue(playerId, word, s.turns.lap()));
        nextTurn(room, s);
    }

    private void castVote(GameRoom room, State s, String voterId, String suspectId) {
        requireStage(room, "VOTE");
        if (suspectId == null || room.player(suspectId) == null) {
            throw new RoomException("NO_SUCH_PLAYER", "Pick someone who's in the room.");
        }
        if (suspectId.equals(voterId)) {
            throw new RoomException("NO_SELF_VOTE", "You can't vote for yourself.");
        }
        if (!s.turns.order().contains(voterId)) {
            throw new RoomException("NOT_IN_ROUND", "You're sitting this round out.");
        }
        s.votes.put(voterId, suspectId);                 // changing your mind is allowed
        if (everyoneVoted(room, s)) {
            tally(room, s);
        }
    }

    private void submitSteal(GameRoom room, State s, String playerId, String guess) {
        requireStage(room, "STEAL");
        if (!playerId.equals(s.imposterId)) {
            throw new RoomException("NOT_THE_IMPOSTER", "Only the imposter gets this shot.");
        }
        if (guess == null || guess.isBlank()) {
            throw new RoomException("EMPTY", "Type a name.");
        }
        Content.Card secret = new Content.Card(s.secret, "player", Text.keysFor(s.secret, List.of(), true));
        boolean correct = matcher.match(guess, List.of(secret)).outcome() == AnswerMatcher.Outcome.HIT;
        s.stealGuess = guess.strip();
        s.stealOk = correct;
        if (correct) {
            award(room, s, playerId, POINTS_STEAL);
        }
        room.enterStage("RESULT", RESULT_MS);
    }

    // ------------------------------------------------------------- transitions

    private void nextTurn(GameRoom room, State s) {
        s.turns.advance(room);
        goToCluesOrVote(room, s);
    }

    private void goToCluesOrVote(GameRoom room, State s) {
        if (s.turns.done()) {
            room.enterStage("VOTE", VOTE_MS);
        } else {
            room.enterStage("CLUES", CLUE_MS);
        }
    }

    private boolean everyoneVoted(GameRoom room, State s) {
        return s.turns.order().stream()
                .filter(room::isConnected)
                .allMatch(s.votes::containsKey);
    }

    private void tally(GameRoom room, State s) {
        Map<String, Integer> counts = new HashMap<>();
        s.votes.values().forEach(suspect -> counts.merge(suspect, 1, Integer::sum));

        String accused = null;
        if (!counts.isEmpty()) {
            int top = Collections.max(counts.values());
            List<String> leaders = counts.entrySet().stream()
                    .filter(e -> e.getValue() == top).map(Map.Entry::getKey).toList();
            accused = leaders.size() == 1 ? leaders.get(0) : null;   // a tie accuses nobody
        }
        s.accusedId = accused;
        s.caught = s.imposterId.equals(accused);

        if (s.caught) {
            s.votes.forEach((voter, suspect) -> {
                if (suspect.equals(s.imposterId)) {
                    award(room, s, voter, POINTS_CORRECT_VOTE);
                }
            });
            if (room.isConnected(s.imposterId)) {
                room.enterStage("STEAL", STEAL_MS);
            } else {
                room.enterStage("RESULT", RESULT_MS);
            }
        } else {
            award(room, s, s.imposterId, POINTS_SURVIVE);
            room.enterStage("RESULT", RESULT_MS);
        }
    }

    private static void award(GameRoom room, State s, String playerId, int points) {
        if (room.player(playerId) != null) {
            room.player(playerId).addScore(points);
            s.gained.merge(playerId, points, Integer::sum);
        }
    }

    // ------------------------------------------------------------------ view

    public record Reveal(
            String imposterId,
            String secret,
            String decoy,
            String accusedId,
            boolean caught,
            String stealGuess,
            boolean stealOk,
            Map<String, String> votes,
            Map<String, Integer> gained) {}

    public record View(
            int round,
            int rounds,
            String yourWord,
            String turnPlayerId,
            boolean yourTurn,
            int pass,
            int passes,
            List<Clue> clues,
            List<String> voted,
            String yourVote,
            boolean youMustSteal,
            Reveal reveal) {}

    @Override
    public Object viewFor(GameRoom room, String playerId) {
        State s = room.state();
        if (s == null) {
            return null;
        }
        boolean revealing = "RESULT".equals(room.stage()) || "FINAL".equals(room.stage());
        Reveal reveal = revealing
                ? new Reveal(s.imposterId, s.secret, s.decoy, s.accusedId, s.caught,
                        s.stealGuess, s.stealOk, Map.copyOf(s.votes), Map.copyOf(s.gained))
                : null;

        boolean inRound = s.turns.order().contains(playerId);
        return new View(
                s.round,
                ROUNDS,
                inRound ? wordFor(s, playerId) : null,
                s.turns.current(),
                "CLUES".equals(room.stage()) && s.turns.isTurnOf(playerId),
                Math.min(s.turns.lap() + 1, PASSES),
                PASSES,
                List.copyOf(s.clues),
                List.copyOf(s.votes.keySet()),          // WHO voted, never for whom
                s.votes.get(playerId),
                "STEAL".equals(room.stage()) && playerId.equals(s.imposterId),
                reveal);
    }

    // ---------------------------------------------------------------- helpers

    /** The imposter is never told. There is no "youAreTheImposter" field anywhere. */
    private static String wordFor(State s, String playerId) {
        return playerId.equals(s.imposterId) ? s.decoy : s.secret;
    }

    private static void requireStage(GameRoom room, String stage) {
        if (!stage.equals(room.stage())) {
            throw new RoomException("WRONG_STAGE", "Not right now.");
        }
    }

    private static String cleanClue(String raw) {
        String word = raw == null ? "" : raw.strip();
        if (word.isEmpty()) {
            throw new RoomException("BAD_CLUE", "Type a word first.");
        }
        if (word.contains(" ")) {
            throw new RoomException("BAD_CLUE", "One word only.");
        }
        if (word.length() > 20) {
            throw new RoomException("BAD_CLUE", "Keep it under 20 letters.");
        }
        return word;
    }

    private static boolean givesItAway(String clue, String answer) {
        String c = Text.normalise(clue).replace(" ", "");
        if (c.length() < 3) {
            return false;
        }
        for (String part : Text.normalise(answer).split(" ")) {
            if (part.length() >= 3 && (c.contains(part) || part.contains(c))) {
                return true;
            }
        }
        return false;
    }
}
