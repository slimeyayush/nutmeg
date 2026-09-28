import { Who, nameOf } from '../components/People'
import { Shirt } from '../components/Shirt'
import type { ImposterView } from '../lib/types'
import { AnswerBox, Waiting, type GameProps } from './common'

const RED = 'var(--color-imposter)'

export function Imposter({ room, view, youId, send }: GameProps<ImposterView>) {
  const stage = room.stage
  const players = room.players

  if (stage === 'DEAL') {
    return (
      <div className="flex flex-col gap-6 pt-6">
        <p className="text-[var(--color-muted)]">Round {view.round} of {view.rounds}. Your player is</p>
        <WordCard word={view.yourWord} big />
        <p className="text-[var(--color-muted)]">
          One of you has a different player and doesn't know it. It might be you. Give clues that prove you're
          in on it without handing the imposter the answer.
        </p>
      </div>
    )
  }

  if (stage === 'RESULT' && view.reveal) {
    const r = view.reveal
    const imposter = players.find((p) => p.id === r.imposterId)
    return (
      <div className="flex flex-col gap-5 pt-4">
        <div className="panel p-5 flip-in" style={{ boxShadow: `inset 0 0 0 2px ${RED}` }}>
          <p className="text-[var(--color-muted)]">The imposter was</p>
          <div className="flex items-center gap-3 mt-2">
            {imposter && <Shirt colour={imposter.colour} size={56} />}
            <span className="display text-5xl">{nameOf(players, r.imposterId, youId)}</span>
          </div>
          <p className="mt-4 text-lg">
            {r.caught ? 'Caught.' : r.accusedId ? `Got away with it. The room voted out ${nameOf(players, r.accusedId, youId)}.` : 'Got away with it. The vote was tied.'}
            {r.caught && r.stealGuess != null && (r.stealOk ? ' And stole it back by naming the real player.' : ` Guessed "${r.stealGuess}" and missed.`)}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="panel p-4">
            <p className="text-sm text-[var(--color-muted)]">Everyone had</p>
            <p className="display text-2xl mt-1">{r.secret}</p>
          </div>
          <div className="panel p-4" style={{ boxShadow: `inset 0 0 0 1.5px ${RED}` }}>
            <p className="text-sm text-[var(--color-muted)]">Imposter had</p>
            <p className="display text-2xl mt-1">{r.decoy}</p>
          </div>
        </div>
        <ClueList view={view} players={players} youId={youId} />
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5 pt-4">
      <WordCard word={view.yourWord} />

      {stage === 'CLUES' && (
        <section className="flex flex-col gap-3">
          <p className="font-semibold">
            Clues, lap {view.pass} of {view.passes}
          </p>
          {view.yourTurn ? (
            <AnswerBox placeholder="One word" button="Send clue" maxLength={20} colour={RED}
              onSubmit={(word) => send('SUBMIT_CLUE', { word })} />
          ) : (
            <Waiting>{nameOf(players, view.turnPlayerId, youId)} is thinking of a clue</Waiting>
          )}
        </section>
      )}

      {stage === 'VOTE' && (
        <section className="flex flex-col gap-3">
          <p className="display text-3xl">Who's the imposter?</p>
          <p className="text-sm text-[var(--color-muted)]">
            {view.voted.length} of {players.filter((p) => p.connected).length} voted. You can change your mind until the clock runs out.
          </p>
          <div className="grid grid-cols-3 gap-2">
            {players.filter((p) => p.id !== youId).map((p) => {
              const mine = view.yourVote === p.id
              return (
                <button
                  key={p.id}
                  className="tap panel flex flex-col items-center gap-1 py-3"
                  style={mine ? { background: RED, color: 'var(--color-ink)', borderColor: 'transparent' } : undefined}
                  aria-pressed={mine}
                  onClick={() => send('CAST_VOTE', { suspectId: p.id })}
                >
                  <Shirt colour={p.colour} size={40} dim={!p.connected} />
                  <span className="font-semibold text-sm truncate max-w-full px-1">{p.name}</span>
                  {view.voted.includes(p.id) && <span className="text-xs opacity-70">voted</span>}
                </button>
              )
            })}
          </div>
        </section>
      )}

      {stage === 'STEAL' && (
        view.youMustSteal ? (
          <section className="panel p-4 flex flex-col gap-3" style={{ boxShadow: `inset 0 0 0 2px ${RED}` }}>
            <p className="display text-3xl">They caught you</p>
            <p>Name the player everyone else had and you steal 2 points back.</p>
            <AnswerBox placeholder="Their player" button="Steal" colour={RED} onSubmit={(guess) => send('SUBMIT_STEAL', { guess })} />
          </section>
        ) : (
          <Waiting>They caught someone. The imposter gets one shot at naming your player.</Waiting>
        )
      )}

      <ClueList view={view} players={players} youId={youId} />
    </div>
  )
}

function WordCard({ word, big = false }: { word: string | null; big?: boolean }) {
  return (
    <div className={`rounded-[14px] bg-[var(--color-chalk)] text-[var(--color-ink)] ${big ? 'p-6 flip-in' : 'px-4 py-3'}`}>
      {!big && <p className="text-sm font-semibold opacity-70">Your player</p>}
      <p className={`display ${big ? 'text-[3.4rem]' : 'text-3xl'}`}>{word ?? 'Sitting this one out'}</p>
    </div>
  )
}

function ClueList({ view, players, youId }: { view: ImposterView; players: GameProps<ImposterView>['room']['players']; youId: string | null }) {
  if (view.clues.length === 0) return null
  return (
    <ol className="flex flex-col divide-y divide-[var(--color-line)] panel">
      {view.clues.map((c, i) => (
        <li key={i} className="flex items-center justify-between gap-3 px-4 py-2.5">
          <Who players={players} id={c.playerId} youId={youId} />
          {c.word == null
            ? <span className="text-[var(--color-muted)] italic">no clue</span>
            : <span className="display text-2xl">{c.word}</span>}
        </li>
      ))}
    </ol>
  )
}
