import { Led } from '../components/Led'
import { Who, nameOf } from '../components/People'
import type { HolView } from '../lib/types'
import { Waiting, type GameProps } from './common'

const BLUE = 'var(--color-hol)'

export function HigherLower({ room, view, youId, send }: GameProps<HolView>) {
  const players = room.players
  const stage = room.stage
  const online = players.filter((p) => p.connected).length
  const youWereRight = view.revealed && view.yourPick != null &&
    (view.answer === 'EITHER' || view.answer === view.yourPick)

  return (
    <div className="flex flex-col gap-5 pt-4">
      <header>
        <p className="text-[var(--color-muted)]">
          {view.category}, {view.step} of {view.steps}
          <span className="ml-2 text-sm">(set {view.round} of {view.rounds})</span>
        </p>
        <h2 className="display text-[2.2rem] mt-1">{view.question}</h2>
      </header>

      <div className="flex flex-col gap-3">
        <Card name={view.left.name} value={view.left.value} caption={view.category} />
        <Card
          name={view.right.name}
          value={view.right.value}
          caption={view.revealed ? view.category : 'Hidden'}
          highlight={view.revealed ? BLUE : undefined}
          flip={view.revealed}
        />
      </div>

      {stage === 'INTRO' && <Waiting>New category. Is the second one {view.higherWord.toLowerCase()} or {view.lowerWord.toLowerCase()}?</Waiting>}

      {stage === 'PICK' && (
        view.yourPick ? (
          <Waiting>
            Locked in: {view.yourPick === 'HIGHER' ? view.higherWord : view.lowerWord}. {view.lockedIn.length} of {online} in.
          </Waiting>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            <button className="btn text-2xl" style={{ background: BLUE }} onClick={() => send('PICK', { choice: 'HIGHER' })}>
              ▲ {view.higherWord}
            </button>
            <button className="btn text-2xl" style={{ background: BLUE }} onClick={() => send('PICK', { choice: 'LOWER' })}>
              ▼ {view.lowerWord}
            </button>
          </div>
        )
      )}

      {stage === 'REVEAL' && (
        <div className="flex flex-col gap-3">
          <p className="display text-3xl text-center">
            {view.answer === 'EITHER' ? 'Level. Everyone scores.' : view.yourPick == null ? 'You missed that one.' : youWereRight ? 'Right.' : 'Wrong.'}
          </p>
          {view.fastestId && (
            <p className="text-center text-[var(--color-muted)]">Fastest right answer: {nameOf(players, view.fastestId, youId)}, +1 bonus</p>
          )}
          <ul className="flex flex-wrap gap-x-4 gap-y-1.5 justify-center">
            {Object.entries(view.picks).map(([id, pick]) => (
              <li key={id} className="text-sm">
                <Who players={players} id={id} youId={youId} size={18} />{' '}
                <span className="text-[var(--color-muted)]">{pick === 'HIGHER' ? view.higherWord : view.lowerWord}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function Card({ name, value, caption, highlight, flip }: { name: string; value: number | null; caption: string; highlight?: string; flip?: boolean }) {
  return (
    <div className="panel p-4 flex items-center justify-between gap-4" style={highlight ? { boxShadow: `inset 0 0 0 2px ${highlight}` } : undefined}>
      <div className="min-w-0">
        <p className="display text-[1.9rem] leading-none">{name}</p>
        <p className="text-sm text-[var(--color-muted)] mt-1">{caption}</p>
      </div>
      <span className={flip ? 'flip-in' : ''}>
        <Led size={34} style={{ minWidth: 96 }}>{value == null ? '?' : value}</Led>
      </span>
    </div>
  )
}
