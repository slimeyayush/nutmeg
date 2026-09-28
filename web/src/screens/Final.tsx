import { Shirt } from '../components/Shirt'
import { gameInfo } from '../lib/games'
import type { RoomSnapshot } from '../lib/types'

interface Props {
  room: RoomSnapshot
  youId: string | null
  send: (type: string, payload?: Record<string, string>) => void
}

export function Final({ room, youId, send }: Props) {
  const game = gameInfo(room.mode)
  const table = [...room.players].sort((a, b) => b.score - a.score)
  const top = table[0]?.score ?? 0
  const winners = table.filter((p) => p.score === top && top > 0)
  const isHost = room.hostId === youId
  const host = room.players.find((p) => p.id === room.hostId)

  // Podium order: 2nd, 1st, 3rd
  const podium = [table[1], table[0], table[2]].filter(Boolean)
  const heights = table.length >= 2 ? [88, 124, 64] : [124]

  const headline =
    winners.length === 0
      ? 'Nobody scored'
      : winners.length > 1
        ? 'Shared the spoils'
        : winners[0].id === youId
          ? 'You won'
          : `${winners[0].name} wins`

  return (
    <main className="flex flex-col gap-8 pt-8 pb-10">
      <header>
        <p className="font-semibold" style={{ color: game.colour }}>Full time, {game.name}</p>
        <h1 className="display text-[3.8rem] mt-1 flip-in">{headline}</h1>
      </header>

      <section aria-label="Podium" className="flex items-end justify-center gap-3">
        {podium.map((p, i) => {
          const place = table.indexOf(p) + 1
          return (
            <div key={p.id} className="flex flex-col items-center w-[31%]">
              <Shirt colour={p.colour} label={String(p.score)} size={place === 1 ? 76 : 58} captain={p.host} title={p.name} />
              <span className={`mt-1 text-sm truncate max-w-full ${p.id === youId ? 'font-bold' : ''}`}>
                {p.id === youId ? 'You' : p.name}
              </span>
              <div
                className="w-full mt-2 rounded-t-[8px] flex items-start justify-center pt-2 display text-3xl text-[var(--color-ink)]"
                style={{ height: heights[table.length >= 2 ? i : 0], background: place === 1 ? 'var(--color-chalk)' : 'rgb(243 246 238 / 0.55)' }}
              >
                {place}
              </div>
            </div>
          )
        })}
      </section>

      <section aria-label="Final table" className="panel overflow-hidden">
        <table className="w-full text-left">
          <tbody>
            {table.map((p, i) => (
              <tr key={p.id} className="border-t border-[var(--color-line)] first:border-t-0">
                <td className="py-2.5 pl-4 w-10 display text-2xl text-[var(--color-muted)]">{i + 1}</td>
                <td className="py-2.5">
                  <span className="inline-flex items-center gap-2">
                    <Shirt colour={p.colour} size={26} />
                    <span className={p.id === youId ? 'font-bold' : ''}>{p.id === youId ? 'You' : p.name}</span>
                  </span>
                </td>
                <td className="py-2.5 pr-4 text-right display text-2xl">{p.score}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {isHost ? (
        <div className="flex flex-col gap-3">
          <button className="btn w-full" onClick={() => send('START_GAME')}>Play {game.name} again</button>
          <button className="btn btn-ghost w-full" onClick={() => send('BACK_TO_LOBBY')}>Pick another game</button>
        </div>
      ) : (
        <p className="text-center text-[var(--color-muted)]">Waiting for {host?.name ?? 'the host'} to pick what's next.</p>
      )}
    </main>
  )
}
