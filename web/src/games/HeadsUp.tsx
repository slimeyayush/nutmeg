import { Shirt } from '../components/Shirt'
import { byId, nameOf } from '../components/People'
import type { HeadsUpView } from '../lib/types'
import { AnswerBox, Waiting, type GameProps } from './common'

const PINK = 'var(--color-headsup)'

export function HeadsUp({ room, view, youId, send }: GameProps<HeadsUpView>) {
  const players = room.players
  const stage = room.stage
  const guesser = byId(players, view.guesserId)
  const guesserName = nameOf(players, view.guesserId, youId)
  const cardNo = String(view.cardNo)

  return (
    <div className="flex flex-col gap-5 pt-4">
      <header className="flex items-center gap-3">
        {guesser && <Shirt colour={guesser.colour} size={44} />}
        <div>
          <p className="text-[var(--color-muted)]">Turn {view.turn} of {view.turns}</p>
          <h2 className="display text-[2rem]">
            {view.youAreGuesser ? "You're in the hot seat" : `${guesserName} is guessing`}
          </h2>
        </div>
      </header>

      {stage === 'READY' && (
        <Waiting>
          {view.youAreGuesser
            ? "Listen to your mates. Type what you think it is, or pass."
            : `Describe each card to ${guesserName} out loud. Don't say the name.`}
        </Waiting>
      )}

      {stage === 'PLAY' && (view.youAreGuesser ? (
        <div className="flex flex-col gap-4">
          <div className="rounded-[18px] border-2 border-dashed flex flex-col items-center justify-center py-12" style={{ borderColor: PINK }}>
            <span className="display text-[6rem] leading-none" style={{ color: PINK }}>?</span>
            <span className="text-[var(--color-muted)] mt-2">It's a {view.cardKind ?? 'card'}</span>
          </div>
          <AnswerBox placeholder="Your guess" button="Guess" colour={PINK} onSubmit={(text) => send('GUESS', { text, cardNo })} />
          <button className="btn btn-ghost" onClick={() => send('PASS', { cardNo })}>Pass</button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div key={cardNo} className="rounded-[18px] p-6 text-[var(--color-ink)] flip-in" style={{ background: PINK }}>
            <p className="font-semibold opacity-70 first-letter:uppercase">{view.cardKind}</p>
            <p className="display text-[3.4rem] mt-1 break-words">{view.card}</p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <button className="btn" onClick={() => send('GOT_IT', { cardNo })}>Got it</button>
            <button className="btn btn-ghost" onClick={() => send('PASS', { cardNo })}>Pass</button>
          </div>
        </div>
      ))}

      {(stage === 'PLAY' || stage === 'TURN_END') && (
        <section>
          <p className="font-semibold mb-2">
            {stage === 'TURN_END' ? `Time. ${guesserName} got ${view.gotThisTurn}.` : `${view.gotThisTurn} so far`}
          </p>
          {view.history.length > 0 && (
            <ul className="flex flex-wrap gap-2">
              {view.history.map((h, i) => (
                <li
                  key={i}
                  className="rounded-full px-3 py-1 text-sm font-semibold"
                  style={h.result === 'GOT'
                    ? { background: 'var(--color-good)', color: 'var(--color-ink)' }
                    : { boxShadow: 'inset 0 0 0 1.5px var(--color-line)', color: 'var(--color-muted)' }}
                >
                  {h.name}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  )
}
