import { Countdown, StageBar } from '../components/Countdown'
import { Led } from '../components/Led'
import { ScoreStrip } from '../components/People'
import { Toast } from '../components/Toast'
import { HeadsUp } from '../games/HeadsUp'
import { HigherLower } from '../games/HigherLower'
import { Imposter } from '../games/Imposter'
import { Ten } from '../games/Ten'
import { useGameSocket } from '../hooks/useGameSocket'
import { gameInfo } from '../lib/games'
import type { HeadsUpView, HolView, Identity, ImposterView, TenView } from '../lib/types'
import { Final } from './Final'
import { Lobby } from './Lobby'

interface Props {
  identity: Identity
  onLeave: (notice?: string) => void
}

export function Room({ identity, onLeave }: Props) {
  const socket = useGameSocket(identity, (message) => onLeave(message))
  const { room, game, youId, send, skewMs, status, error } = socket

  if (status === 'dead') {
    return (
      <main className="min-h-[100dvh] flex flex-col justify-center items-center gap-5 text-center">
        <Led size={40}>{identity.roomCode}</Led>
        <p className="text-lg max-w-[30ch]">{socket.deadReason}</p>
        <div className="flex gap-3">
          <button className="btn" onClick={socket.retry}>Reconnect</button>
          <button className="btn btn-ghost" onClick={() => onLeave()}>Leave</button>
        </div>
      </main>
    )
  }

  if (!room) {
    return (
      <main className="min-h-[100dvh] flex flex-col justify-center items-center gap-5">
        <Led size={40}>{identity.roomCode}</Led>
        <p className="text-[var(--color-muted)]">{status === 'reconnecting' ? 'Reconnecting…' : 'Walking out of the tunnel…'}</p>
      </main>
    )
  }

  const toast = <Toast message={error?.message ?? null} stamp={error?.at ?? 0} />
  const banner = status === 'reconnecting' && (
    <div className="fixed top-0 inset-x-0 z-40 bg-[var(--color-led)] text-[var(--color-ink)] text-center text-sm font-semibold py-1.5">
      Connection dropped. Reconnecting…
    </div>
  )

  if (room.status === 'LOBBY' || !game) {
    return (
      <>
        {banner}
        {room.status === 'FINISHED'
          ? <Final room={room} youId={youId} send={send} />
          : <Lobby room={room} youId={youId} send={send} onLeave={() => onLeave()} />}
        {toast}
      </>
    )
  }

  if (room.status === 'FINISHED') {
    return (
      <>
        {banner}
        <Final room={room} youId={youId} send={send} />
        {toast}
      </>
    )
  }

  const info = gameInfo(room.mode)
  const gained = (game as { gained?: Record<string, number> }).gained
  const showGained = room.stage === 'RESULT' || room.stage === 'REVEAL'

  return (
    <>
      {banner}
      <main className="flex flex-col min-h-[100dvh]">
        <div className="sticky top-0 z-30 -mx-4 px-4 pt-[calc(env(safe-area-inset-top)+10px)] pb-2 bg-[var(--color-pitch-deep)]/85 backdrop-blur-[2px]">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <span className="inline-block w-2.5 h-5 rounded-[2px]" style={{ background: info.colour }} aria-hidden />
              <span className="display text-2xl truncate">{info.name}</span>
            </div>
            <div className="flex items-center gap-2">
              <Led size={15} label={`Room ${room.code}`}>{room.code}</Led>
              <Countdown endsAt={room.stageEndsAt} skewMs={skewMs} size={24} />
            </div>
          </div>
          <div className="mt-2">
            <StageBar endsAt={room.stageEndsAt} durationMs={room.stageDurationMs} skewMs={skewMs} colour={info.colour} />
          </div>
        </div>

        <div className="flex-1">
          {room.mode === 'IMPOSTER' && <Imposter room={room} view={game as ImposterView} youId={youId} send={send} />}
          {room.mode === 'TEN' && <Ten room={room} view={game as TenView} youId={youId} send={send} />}
          {room.mode === 'HIGHER_LOWER' && <HigherLower room={room} view={game as HolView} youId={youId} send={send} />}
          {room.mode === 'HEADS_UP' && <HeadsUp room={room} view={game as HeadsUpView} youId={youId} send={send} />}
        </div>

        <footer className="mt-8 pt-3 pb-[calc(env(safe-area-inset-bottom)+14px)] border-t border-[var(--color-line)]">
          <ScoreStrip players={room.players} youId={youId} highlight={showGained ? gained : undefined} />
        </footer>
      </main>
      {toast}
    </>
  )
}
