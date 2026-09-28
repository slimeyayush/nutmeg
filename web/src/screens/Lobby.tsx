import { useState } from 'react'
import { Led } from '../components/Led'
import { initial } from '../components/People'
import { Shirt } from '../components/Shirt'
import { GAMES, gameInfo } from '../lib/games'
import type { GameModeId, RoomSnapshot } from '../lib/types'

interface Props {
  room: RoomSnapshot
  youId: string | null
  send: (type: string, payload?: Record<string, string>) => void
  onLeave: () => void
}

export function Lobby({ room, youId, send, onLeave }: Props) {
  const [copied, setCopied] = useState(false)
  const isHost = room.hostId === youId
  const host = room.players.find((p) => p.id === room.hostId)
  const chosen = gameInfo(room.mode)
  const online = room.players.filter((p) => p.connected).length
  const enough = online >= chosen.min

  async function share() {
    const url = `${location.origin}/r/${room.code}`
    try {
      if (navigator.share && matchMedia('(pointer: coarse)').matches) {
        await navigator.share({ title: 'Nutmeg', text: `Join my Nutmeg room: ${room.code}`, url })
        return
      }
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      /* user cancelled the share sheet */
    }
  }

  const choose = (id: GameModeId) => {
    if (isHost && id !== room.mode) send('CHOOSE_MODE', { mode: id })
  }

  return (
    <main className="flex flex-col gap-8 pt-6 pb-8">
      <div className="flex items-center justify-between">
        <button className="btn btn-ghost btn-small" onClick={onLeave}>Leave</button>
        <span className="text-sm text-[var(--color-muted)]">{online} online</span>
      </div>

      <section className="flex flex-col items-center gap-3 text-center" aria-label="Room code">
        <p className="text-[var(--color-muted)]">Room code</p>
        <Led size={64} label={`Room code ${room.code.split('').join(' ')}`}>{room.code}</Led>
        <button className="btn btn-ghost btn-small mt-1" onClick={share}>
          {copied ? 'Link copied' : 'Share invite link'}
        </button>
      </section>

      <section aria-label="Players">
        <h2 className="display text-3xl mb-3">Squad</h2>
        <ul className="grid grid-cols-5 gap-y-4 gap-x-2">
          {room.players.map((p) => (
            <li key={p.id} className="flex flex-col items-center text-center">
              <Shirt
                colour={p.colour}
                label={initial(p.name)}
                size={52}
                captain={p.host}
                dim={!p.connected}
                title={`${p.name}${p.host ? ', host' : ''}${p.connected ? '' : ', offline'}`}
              />
              <span className={`text-sm mt-1 max-w-full truncate ${p.id === youId ? 'font-bold' : ''}`}>
                {p.id === youId ? 'You' : p.name}
              </span>
              {p.host && <span className="text-xs text-[var(--color-muted)]">host</span>}
            </li>
          ))}
          {room.players.length < 3 &&
            Array.from({ length: 3 - room.players.length }).map((_, i) => (
              <li key={`empty-${i}`} className="flex flex-col items-center opacity-40">
                <div className="w-[52px] h-[52px] rounded-full border-2 border-dashed border-[var(--color-line)]" />
                <span className="text-sm mt-1">Empty</span>
              </li>
            ))}
        </ul>
      </section>

      <section aria-label="Choose a game">
        <h2 className="display text-3xl mb-1">Game</h2>
        <p className="text-sm text-[var(--color-muted)] mb-3">
          {isHost ? 'Tap a game to pick it.' : `${host?.name ?? 'The host'} picks the game.`}
        </p>
        <div className="flex flex-col gap-2" role="radiogroup">
          {GAMES.map((g) => {
            const selected = g.id === room.mode
            return (
              <button
                key={g.id}
                role="radio"
                aria-checked={selected}
                disabled={!isHost && !selected}
                onClick={() => choose(g.id)}
                className={`tap text-left rounded-[12px] px-4 py-3 transition-colors ${
                  selected ? 'text-[var(--color-ink)]' : 'panel'
                } ${!isHost && !selected ? 'opacity-45' : ''}`}
                style={selected ? { background: g.colour } : { boxShadow: `inset 4px 0 0 ${g.colour}` }}
              >
                <span className="flex items-baseline justify-between gap-3">
                  <span className="display text-[1.9rem]">{g.name}</span>
                  <span className={`text-sm ${selected ? 'font-semibold' : 'text-[var(--color-muted)]'}`}>{g.players}</span>
                </span>
                <span className={`block text-[0.95rem] mt-1 ${selected ? '' : 'text-[var(--color-muted)]'}`}>{g.pitch}</span>
              </button>
            )
          })}
        </div>
      </section>

      <section className="sticky bottom-0 pt-3 pb-[calc(env(safe-area-inset-bottom)+12px)] bg-gradient-to-t from-[var(--color-pitch-deep)] via-[var(--color-pitch-deep)]/90 to-transparent -mx-4 px-4">
        {isHost ? (
          <>
            <button className="btn w-full" disabled={!enough} onClick={() => send('START_GAME')}>
              Kick off {chosen.name}
            </button>
            {!enough && (
              <p className="text-sm text-center mt-2 text-[var(--color-muted)]">
                {chosen.name} needs {chosen.min} players online. Share the code.
              </p>
            )}
          </>
        ) : (
          <p className="text-center text-[var(--color-muted)] py-3">
            Waiting for {host?.name ?? 'the host'} to kick off {chosen.name}.
          </p>
        )}
      </section>
    </main>
  )
}
