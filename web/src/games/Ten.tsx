import { Who, byId, nameOf } from '../components/People'
import { Shirt } from '../components/Shirt'
import type { TenView } from '../lib/types'
import { AnswerBox, Waiting, type GameProps } from './common'

const YELLOW = 'var(--color-ten)'

export function Ten({ room, view, youId, send }: GameProps<TenView>) {
  const players = room.players
  const stage = room.stage
  const found = view.slots.filter((s) => s.foundBy).length

  return (
    <div className="flex flex-col gap-5 pt-4">
      <header>
        <p className="text-[var(--color-muted)]">List {view.round} of {view.rounds}</p>
        <h2 className="display text-[2.2rem] mt-1">{view.question}</h2>
      </header>

      <div className="flex items-center justify-between">
        <span className="font-semibold">{found} of 10 found</span>
        <span className="flex items-center gap-1.5" aria-label={`${view.strikes} of ${view.maxStrikes} strikes`}>
          {Array.from({ length: view.maxStrikes }).map((_, i) => (
            <span
              key={i}
              className="inline-block w-4 h-6 rounded-[3px] border-2"
              style={i < view.strikes
                ? { background: 'var(--color-imposter)', borderColor: 'var(--color-imposter)', transform: 'rotate(8deg)' }
                : { borderColor: 'var(--color-line)' }}
            />
          ))}
        </span>
      </div>

      <ol className="panel divide-y divide-[var(--color-line)] overflow-hidden">
        {view.slots.map((slot) => {
          const who = byId(players, slot.foundBy)
          const missed = view.revealed && !slot.foundBy
          return (
            <li key={slot.points} className={`flex items-center gap-3 px-3 py-2 ${slot.answer && !missed ? 'flip-in' : ''}`}>
              <span
                className="display text-xl w-9 h-9 rounded-[8px] flex items-center justify-center shrink-0"
                style={slot.foundBy ? { background: YELLOW, color: 'var(--color-ink)' } : { boxShadow: 'inset 0 0 0 1.5px var(--color-line)' }}
                aria-label={`${slot.points} points`}
              >
                {slot.points}
              </span>
              <span className={`flex-1 font-semibold ${missed ? 'text-[var(--color-muted)] italic font-normal' : ''}`}>
                {slot.answer ?? <span className="inline-block w-24 h-[2px] bg-[var(--color-line)] align-middle" />}
              </span>
              {who && <Shirt colour={who.colour} size={26} title={who.name} />}
            </li>
          )
        })}
      </ol>

      {stage === 'INTRO' && <Waiting>Get ready. Wrong answers cost everyone a strike.</Waiting>}

      {stage === 'ANSWER' &&
        (view.yourTurn ? (
          <AnswerBox placeholder="Name one" button="Lock in" colour={YELLOW} onSubmit={(text) => send('SUBMIT_ANSWER', { text })} />
        ) : (
          <Waiting>{nameOf(players, view.turnPlayerId, youId)} is answering</Waiting>
        ))}

      {stage === 'RESULT' && (
        <p className="display text-3xl text-center flip-in">
          {view.cleared ? 'All ten. Everyone gets 5.' : view.strikes >= view.maxStrikes ? 'Three strikes. List closed.' : 'List closed.'}
        </p>
      )}

      {view.log.length > 0 && (
        <ul className="flex flex-col gap-1.5 text-[0.95rem]" aria-label="Recent answers">
          {[...view.log].reverse().slice(0, 6).map((e, i) => (
            <li key={i} className="flex items-center justify-between gap-2">
              <Who players={players} id={e.playerId} youId={youId} size={18} />
              <span className={e.kind === 'HIT' ? 'font-semibold' : 'text-[var(--color-muted)]'}>
                {e.kind === 'HIT' && `${e.text} +${e.points}`}
                {e.kind === 'MISS' && <><s>{e.text}</s> strike</>}
                {e.kind === 'TIMEOUT' && 'ran out of time, strike'}
                {e.kind === 'SKIP' && 'dropped out, skipped'}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
