import type { PlayerView } from '../lib/types'
import { Shirt } from './Shirt'

export const initial = (name: string) => name.trim().slice(0, 1).toUpperCase()

export function byId(players: PlayerView[], id: string | null | undefined): PlayerView | undefined {
  return id ? players.find((p) => p.id === id) : undefined
}

export function nameOf(players: PlayerView[], id: string | null | undefined, youId?: string | null): string {
  if (id && youId && id === youId) return 'You'
  return byId(players, id)?.name ?? 'Someone'
}

/** Inline "shirt + name" used in feeds and lists. */
export function Who({ players, id, youId, size = 22 }: { players: PlayerView[]; id: string | null; youId?: string | null; size?: number }) {
  const p = byId(players, id)
  if (!p) return <span>Someone</span>
  return (
    <span className="inline-flex items-center gap-1.5 align-middle">
      <Shirt colour={p.colour} size={size} />
      <span className="font-semibold">{youId === p.id ? 'You' : p.name}</span>
    </span>
  )
}

/** The running table along the bottom of every game. */
export function ScoreStrip({ players, youId, highlight }: { players: PlayerView[]; youId: string | null; highlight?: Record<string, number> }) {
  const sorted = [...players].sort((a, b) => b.score - a.score)
  return (
    <div className="flex gap-3 overflow-x-auto pt-3 pb-1 -mx-1 px-2" aria-label="Scores">
      {sorted.map((p) => {
        const gained = highlight?.[p.id]
        return (
          <div key={p.id} className="flex flex-col items-center min-w-[52px]">
            <div className="relative">
              <Shirt colour={p.colour} label={String(p.score)} size={44} dim={!p.connected} captain={p.host} title={p.name} />
              {gained ? (
                <span className="absolute -top-2 -right-3 rounded-full bg-[var(--color-good)] text-[var(--color-ink)] text-xs font-bold px-1.5 py-0.5">
                  +{gained}
                </span>
              ) : null}
            </div>
            <span className={`text-xs mt-0.5 max-w-[64px] truncate ${p.id === youId ? 'font-bold' : 'text-[var(--color-muted)]'}`}>
              {p.id === youId ? 'You' : p.name}
            </span>
          </div>
        )
      })}
    </div>
  )
}
