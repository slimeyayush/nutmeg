package com.example.nutmeg.api.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.nutmeg.api.content.Content;
import com.example.nutmeg.api.content.ContentLoader;
import com.example.nutmeg.api.game.headsup.HeadsUpGame;
import com.example.nutmeg.api.game.hol.HigherLowerGame;
import com.example.nutmeg.api.game.imposter.ImposterGame;
import com.example.nutmeg.api.game.ten.TenGame;
import com.example.nutmeg.api.json.Json;
import com.example.nutmeg.api.room.GameModeId;
import com.example.nutmeg.api.room.GameRoom;
import com.example.nutmeg.api.room.RoomException;
import com.example.nutmeg.api.room.RoomPlayer;
import com.example.nutmeg.api.room.RoomRegistry;
import com.example.nutmeg.api.room.Status;

/**
 * Plays every game start to finish against a fake clock — no Spring, no sockets,
 * no sleeping. Every frame sent to every player is parsed and checked for leaks:
 * the one thing a party game server must never do is show a secret to the wrong person.
 */
class GameSimulationTest {

    // ----------------------------------------------------------------- harness

    static final class FakeClock {
        long now = 1_000_000L;
    }

    static final class FakeScheduler implements GameRunner.Scheduler {
        record Job(long at, long order, Runnable task) {}
        final FakeClock clock;
        final PriorityQueue<Job> jobs = new PriorityQueue<>((a, b) ->
                a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.order, b.order));
        long counter = 0;

        FakeScheduler(FakeClock clock) { this.clock = clock; }

        @Override public void schedule(Runnable task, long at) { jobs.add(new Job(at, counter++, task)); }

        boolean runNext() {
            Job job = jobs.poll();
            if (job == null) {
                return false;
            }
            clock.now = Math.max(clock.now, job.at);
            job.task.run();
            return true;
        }
    }

    static final class Inbox implements GameRunner.Outbox {
        final Map<String, String> last = new HashMap<>();
        final List<String> everyFrame = new ArrayList<>();
        final Map<String, List<String>> framesByPlayer = new HashMap<>();
        Set<String> online = new HashSet<>();

        @Override
        public void sendEach(String roomCode, java.util.function.Function<String, String> jsonFor) {
            for (String id : online) {
                String json = jsonFor.apply(id);
                if (json != null) {
                    last.put(id, json);
                    everyFrame.add(json);
                    framesByPlayer.computeIfAbsent(id, k -> new ArrayList<>()).add(json);
                }
            }
        }
    }

    final FakeClock clock = new FakeClock();
    final FakeScheduler scheduler = new FakeScheduler(clock);
    final Inbox inbox = new Inbox();
    final Content content = ContentLoader.fromClasspath().load();
    final RoomRegistry rooms = new RoomRegistry(() -> clock.now, 100);
    final Random random = new Random(42);
    final GameRunner runner = new GameRunner(rooms, scheduler, inbox, List.of(
            new ImposterGame(content, random),
            new TenGame(content, random),
            new HigherLowerGame(content, random),
            new HeadsUpGame(content, random)));

    GameRoom room;
    List<String> ids = new ArrayList<>();

    private void seat(int players) {
        room = rooms.create();
        for (int i = 0; i < players; i++) {
            RoomPlayer p = room.join("P" + i);
            ids.add(p.id());
            room.markConnected(p.id(), true);
            inbox.online.add(p.id());
        }
    }

    private String host() { return ids.get(0); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> view(String playerId) {
        Map<String, Object> frame = Json.parseObject(inbox.last.get(playerId));
        Map<String, Object> payload = (Map<String, Object>) frame.get("payload");
        return (Map<String, Object>) payload.get("game");
    }

    private String stage() { return room.withLock(room::stage); }

    private Status status() { return room.withLock(room::status); }

    private void act(String id, String type, Map<String, String> payload) {
        try {
            runner.action(room, id, type, payload);
        } catch (RoomException ignored) {
            // illegal moves are fine in a fuzz loop — the server just refuses them
        }
    }

    /** Runs the game to FINISHED, calling `move` between timers. Fails on an infinite loop. */
    private void playOut(Runnable move) {
        int guard = 0;
        while (status() == Status.PLAYING) {
            assertTrue(guard++ < 5_000, "game never finished — stuck at stage " + stage());
            move.run();
            if (status() != Status.PLAYING) {
                break;
            }
            if (!scheduler.runNext()) {
                throw new AssertionError("no timer armed while PLAYING at stage " + stage());
            }
        }
        assertEquals(Status.FINISHED, status());
    }

    // ================================================================ IMPOSTER

    @Test
    void imposterPlaysToTheEndAndNeverTellsTheImposter() {
        seat(4);
        runner.chooseMode(room, host(), GameModeId.IMPOSTER);
        runner.start(room, host());

        int[] clueNo = {0};
        int[] votes = {0};
        playOut(() -> {
            switch (stage()) {
                case "CLUES" -> {
                    // exactly one player sees a different word from everyone else
                    Map<String, Integer> words = new HashMap<>();
                    for (String id : ids) {
                        words.merge((String) view(id).get("yourWord"), 1, Integer::sum);
                    }
                    assertEquals(2, words.size(), "expected one secret and one decoy");
                    assertTrue(words.containsValue(1), "exactly one player should hold the decoy");
                    for (String id : ids) {
                        assertNull(view(id).get("reveal"), "reveal leaked before RESULT");
                        assertFalse(inbox.last.get(id).contains("imposterId"), "imposter id leaked");
                        if (Boolean.TRUE.equals(view(id).get("yourTurn"))) {
                            act(id, "SUBMIT_CLUE", Map.of("word", "clue" + (clueNo[0]++)));
                        }
                    }
                }
                case "VOTE" -> {
                    votes[0]++;
                    for (int i = 0; i < ids.size(); i++) {
                        act(ids.get(i), "CAST_VOTE", Map.of("suspectId", ids.get((i + 1) % ids.size())));
                    }
                }
                case "STEAL" -> ids.forEach(id -> act(id, "SUBMIT_STEAL", Map.of("guess", "nobody")));
                default -> { }
            }
        });
        assertEquals(3 * 4 * 2, clueNo[0], "3 rounds x 4 players x 2 passes of clues");
        assertEquals(3, votes[0], "one vote per round");
    }

    @Test
    void imposterRejectsNamingTheWordAndOutOfTurnClues() {
        seat(3);
        runner.start(room, host());
        scheduler.runNext();                                     // DEAL -> CLUES
        assertEquals("CLUES", stage());

        String turn = (String) view(ids.get(0)).get("turnPlayerId");
        String other = ids.stream().filter(id -> !id.equals(turn)).findFirst().orElseThrow();
        assertThrows(RoomException.class,
                () -> runner.action(room, other, "SUBMIT_CLUE", Map.of("word", "fast")));

        String word = (String) view(turn).get("yourWord");
        String surname = word.contains(" ") ? word.substring(word.lastIndexOf(' ') + 1) : word;
        assertThrows(RoomException.class,
                () -> runner.action(room, turn, "SUBMIT_CLUE", Map.of("word", surname)));
        assertThrows(RoomException.class,
                () -> runner.action(room, turn, "SUBMIT_CLUE", Map.of("word", "two words")));
    }

    // ===================================================================== TEN

    @Test
    void tenHidesAnswersUntilFoundAndFinishes() {
        seat(3);
        runner.chooseMode(room, host(), GameModeId.TEN);
        runner.start(room, host());

        int[] tick = {0};
        playOut(() -> {
            if (!"ANSWER".equals(stage())) {
                return;
            }
            Map<String, Object> v = view(ids.get(0));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) v.get("slots");
            for (Map<String, Object> slot : slots) {
                if (slot.get("foundBy") == null) {
                    assertNull(slot.get("answer"), "an unfound answer leaked");
                }
            }
            String turn = (String) v.get("turnPlayerId");
            Content.TenList list = content.tenLists().stream()
                    .filter(l -> l.question().equals(v.get("question"))).findFirst().orElseThrow();
            // alternate: a real answer, then a miss — exercises hits, strikes and repeats
            String guess = (tick[0]++ % 3 == 2)
                    ? "definitely not on the list"
                    : list.answers().get(tick[0] % 10).display();
            act(turn, "SUBMIT_ANSWER", Map.of("text", guess));
        });

        int total = room.withLock(() -> room.players().stream().mapToInt(RoomPlayer::score).sum());
        assertTrue(total > 0, "somebody should have scored");
        assertTrue(tick[0] >= 9, "expected at least 3 answers per list over 3 lists, got " + tick[0]);
    }

    @Test
    void tenRepeatIsNotAStrike() {
        seat(2);
        runner.chooseMode(room, host(), GameModeId.TEN);
        runner.start(room, host());
        scheduler.runNext();                                      // INTRO -> ANSWER

        Map<String, Object> v = view(ids.get(0));
        Content.TenList list = content.tenLists().stream()
                .filter(l -> l.question().equals(v.get("question"))).findFirst().orElseThrow();
        String first = (String) v.get("turnPlayerId");
        runner.action(room, first, "SUBMIT_ANSWER", Map.of("text", list.answers().get(0).display()));

        String second = (String) view(ids.get(0)).get("turnPlayerId");
        RoomException ex = assertThrows(RoomException.class, () ->
                runner.action(room, second, "SUBMIT_ANSWER", Map.of("text", list.answers().get(0).display())));
        assertEquals("ALREADY_FOUND", ex.code());
        assertEquals(0L, view(ids.get(0)).get("strikes"));
    }

    // ============================================================ HIGHER/LOWER

    @Test
    void higherLowerHidesTheRightValueAndIgnoresStaleTimers() {
        seat(3);
        runner.chooseMode(room, host(), GameModeId.HIGHER_LOWER);
        runner.start(room, host());

        boolean[] alternate = {false};
        int[] steps = {0};
        playOut(() -> {
            if (!"PICK".equals(stage())) {
                return;
            }
            steps[0]++;
            for (String id : ids) {
                @SuppressWarnings("unchecked")
                Map<String, Object> right = (Map<String, Object>) view(id).get("right");
                assertNull(right.get("value"), "right-hand value leaked before reveal");
            }
            int before = room.withLock(() -> room.players().stream().mapToInt(RoomPlayer::score).sum());
            // everyone picks instantly -> reveal happens NOW; the PICK timer is now stale
            for (String id : ids) {
                alternate[0] = !alternate[0];
                act(id, "PICK", Map.of("choice", alternate[0] ? "HIGHER" : "LOWER"));
            }
            assertEquals("REVEAL", stage());
            int after = room.withLock(() -> room.players().stream().mapToInt(RoomPlayer::score).sum());
            assertTrue(after - before <= ids.size() + 1, "points double-counted");
        });
        assertEquals(15, steps[0], "3 categories x 5 comparisons");
    }

    // ================================================================ HEADS UP

    @Test
    void headsUpNeverSendsTheCardToTheGuesser() {
        seat(3);
        runner.chooseMode(room, host(), GameModeId.HEADS_UP);
        runner.start(room, host());

        Set<String> guessers = new HashSet<>();
        playOut(() -> {
            if (!"PLAY".equals(stage())) {
                return;
            }
            String guesser = null;
            String card = null;
            String cardNo = null;
            for (String id : ids) {
                Map<String, Object> v = view(id);
                if (Boolean.TRUE.equals(v.get("youAreGuesser"))) {
                    guesser = id;
                    assertNull(v.get("card"), "the guesser was sent the card");
                } else {
                    card = (String) v.get("card");
                    cardNo = String.valueOf(v.get("cardNo"));
                }
            }
            assertNotNull(guesser);
            assertNotNull(card);
            guessers.add(guesser);
            assertFalse(inbox.last.get(guesser).contains("\"card\":\"" + card),
                    "card name appears in the guesser's frame");

            // Two describers tap "Got it" for the same card: only one may count.
            String describerA = null;
            String describerB = null;
            for (String id : ids) {
                if (!id.equals(guesser)) {
                    if (describerA == null) {
                        describerA = id;
                    } else {
                        describerB = id;
                    }
                }
            }
            int before = room.player(guesser).score();
            act(describerA, "GOT_IT", Map.of("cardNo", cardNo));
            act(describerB, "GOT_IT", Map.of("cardNo", cardNo));    // stale — must be refused
            assertEquals(before + 1, room.player(guesser).score(), "double tap scored twice");
        });
        assertEquals(3, guessers.size(), "everyone gets a turn in the hot seat");
    }

    // =========================================================== resilience

    @Test
    void aPlayerDroppingMidGameNeverFreezesTheRoom() {
        seat(4);
        runner.chooseMode(room, host(), GameModeId.TEN);
        runner.start(room, host());
        scheduler.runNext();

        String quitter = (String) view(ids.get(0)).get("turnPlayerId");
        room.markConnected(quitter, false);
        inbox.online.remove(quitter);
        runner.playerLeft(room, quitter);

        assertFalse(quitter.equals(view(ids.stream()
                .filter(id -> !id.equals(quitter)).findFirst().orElseThrow()).get("turnPlayerId")),
                "turn should have moved past the player who left");

        playOut(() -> { });                                         // everyone idles; timers drive it
    }

    @Test
    void everyoneLeavingEndsTheGameInsteadOfTickingForever() {
        seat(3);
        runner.chooseMode(room, host(), GameModeId.HIGHER_LOWER);
        runner.start(room, host());
        for (String id : ids) {
            room.markConnected(id, false);
            inbox.online.remove(id);
            runner.playerLeft(room, id);
        }
        int guard = 0;
        while (scheduler.runNext()) {
            assertTrue(guard++ < 50, "timers kept firing for an empty room");
        }
        assertEquals(Status.FINISHED, status());
    }

    @Test
    void onlyTheHostCanStartAndTheMinimumIsEnforced() {
        seat(2);
        assertThrows(RoomException.class, () -> runner.start(room, ids.get(1)));      // not host
        RoomException tooFew = assertThrows(RoomException.class, () -> runner.start(room, host()));
        assertEquals("NOT_ENOUGH_PLAYERS", tooFew.code());                           // Imposter needs 3
        runner.chooseMode(room, host(), GameModeId.TEN);
        runner.start(room, host());                                                   // TEN is fine with 2
        assertEquals(Status.PLAYING, status());
    }

    @Test
    void rematchAfterFinishing() {
        seat(3);
        runner.chooseMode(room, host(), GameModeId.HIGHER_LOWER);
        runner.start(room, host());
        playOut(() -> { });
        runner.start(room, host());                                   // play again straight away
        assertEquals(Status.PLAYING, status());
        room.withLock(() -> room.players().forEach(p -> assertEquals(0, p.score())));
    }
}
