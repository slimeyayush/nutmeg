# Nutmeg

Football party games for you and your mates, played live on your phones while you're on a call together.
One person opens a room, everyone joins with a 4-letter code, the host picks a game.

| Game | Players | How it plays |
|---|---|---|
| **Imposter** | 3–10 | Everyone gets the same footballer except one player, who gets a similar one and doesn't know it. One-word clues, two laps, then vote. A caught imposter can steal points by naming the real player. |
| **Ten** | 2–10 | A hidden list of ten (e.g. "Ballon d'Or winners 2000–2009"). Take turns naming one. Obscure answers score more. Three wrong answers and the list closes. |
| **Higher or lower** | 2–10 | Goals, birth years, founding years. Everyone picks at once; right answers score, fastest right answer gets a bonus. |
| **Heads up** | 2–10 | One player can't see the card; everyone else describes it on the call. 60 seconds each. |

## Run it (one command)

Needs JDK 17 or newer.

```bash
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

Open **http://localhost:8080**. The React app is already built into `src/main/resources/static`, so Node isn't needed just to play.
To test with several players on one computer, use different browsers or a private window per player (each browser tab stores one identity).

To let phones on your wifi join, open `http://<your-laptop-ip>:8080`.

## Working on the frontend

Needs Node 20.19+.

```bash
./gradlew bootRun                      # terminal 1: backend on 8080
cd web && npm install && npm run dev   # terminal 2: http://localhost:5173 with hot reload
```

Vite proxies `/api` and `/ws` to 8080. When you're done, `npm run build` writes the production bundle back into
`src/main/resources/static`, and `./gradlew bootRun` serves the new version.

## Tests

```bash
./gradlew test
```

`GameSimulationTest` plays every game start to finish against a fake clock (no Spring, no sockets, no sleeping) and
parses every frame sent to every player to check nothing secret leaks: the imposter's identity, unfound Ten answers,
the hidden Higher or Lower value, the Heads Up card on the guesser's screen. It also covers stale timers, double taps,
players dropping mid-turn, everyone leaving, host-only actions and rematches.

## Editing the questions ("the database")

All content lives in XML under `src/main/resources/content/`:

- `imposter.xml`: clusters of similar players. Two names from one cluster are dealt each round.
- `ten.xml`: lists of **exactly ten** answers, `points` 1–10 by obscurity, optional `aliases="a|b"`. `kind="teams"` turns off surname matching.
- `higherlower.xml`: categories of at least 8 numeric items.
- `headsup.xml`: the card deck, with a `kind` hint shown to the guesser.

The loader validates everything at startup and refuses to boot on a bad file, with a message saying which list is wrong.
The parser has DTDs and external entities switched off (XXE-safe).
Stats are frozen history (retired players, closed date ranges, birth and founding years) so they don't go stale,
but double-check anything you add. Your mates will.

## Deploying (free tier)

The Dockerfile builds the frontend, then the jar, then a small JRE image.
On Render: New → Web Service → this repo → Docker. It picks up `PORT` automatically.
Optionally set `NUTMEG_ALLOWED_ORIGINS=https://your-app.onrender.com`.
Free instances sleep when idle, so the first visit takes a moment to wake. Rooms live in memory and vanish on restart, which suits a party game.

## How it's built

```
React (Vite, TS, Tailwind)  ──REST──▶  RoomController      create / join / peek
          │                                        
          └──── WebSocket /ws ───▶  GameSocketHandler   auth (HELLO token), routing only
                                            │
                                       GameRunner       lock → game → publish → arm timer
                                            │
                      ImposterGame · TenGame · HigherLowerGame · HeadsUpGame   (plain Java)
```

- **Plain-Java core, Spring at the edges.** Rooms, games, matching and JSON have no Spring imports; `NutmegConfig` wires them. That's what lets whole games run in a unit test.
- **One lock per room.** Every mutation runs inside `GameRoom.withLock`; sockets are written outside it through `ConcurrentWebSocketSessionDecorator`.
- **Fenced timers.** Each stage bumps a sequence number; a timer that fires after the stage already moved on sees a different number and does nothing.
- **Server-authoritative, personalised state.** Every player gets their own view. Secrets are never sent to people who shouldn't have them, not just hidden in the UI.
- **Idempotent taps.** Heads Up actions carry the card number, so two mates hitting "Got it" at once only score one card.
- **Fuzzy answer matching.** Accent-stripping normalisation, surnames, aliases, and a length-scaled, capped Levenshtein distance with three outcomes: hit, ambiguous (ask again, never revealing candidates), miss.
- **Resilience.** Reconnect with backoff and jitter, heartbeat pings to keep free-tier proxies from dropping idle sockets, turn-skipping for disconnected players, host hand-over if the host drops, idle-room eviction.
- **No Jackson in the socket layer.** A ~300-line JSON codec keeps the WebSocket protocol identical on Spring Boot 3 (Jackson 2) and 4 (Jackson 3).

## Resume bullets

- Built a real-time multiplayer party-game platform (Spring Boot WebSockets, React/TypeScript) with four game modes behind a pluggable `GameMode` interface; the socket layer needs no changes to add a game.
- Designed a server-authoritative state model with per-player views and sequence-fenced stage timers, eliminating stale-timer races and preventing secret leakage, verified by deterministic full-match simulations against a fake clock.
- Implemented fuzzy answer matching (Unicode normalisation, alias keys, capped Levenshtein with length-scaled tolerance) and an XXE-hardened, startup-validated XML content store.
