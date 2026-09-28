import { useCountdown } from '../hooks/useCountdown'
import { Led } from './Led'

export function Countdown({ endsAt, skewMs, size = 22 }: { endsAt: number; skewMs: number; size?: number }) {
  const left = useCountdown(endsAt, skewMs)
  if (endsAt <= 0) return null
  const secs = Math.ceil(left / 1000)
  return (
    <Led size={size} hot={secs <= 5} label={`${secs} seconds left`}>
      {String(secs).padStart(2, '0')}
    </Led>
  )
}

/** A thin line that drains with the stage clock. */
export function StageBar({ endsAt, durationMs, skewMs, colour }: { endsAt: number; durationMs: number; skewMs: number; colour: string }) {
  const left = useCountdown(endsAt, skewMs)
  if (endsAt <= 0 || durationMs <= 0) return <div className="h-[3px]" />
  const fraction = Math.max(0, Math.min(1, left / durationMs))
  return (
    <div className="h-[3px] w-full rounded-full bg-[var(--color-line)] overflow-hidden" aria-hidden>
      <div
        className="h-full origin-left"
        style={{ background: colour, transform: `scaleX(${fraction})`, transition: 'transform 200ms linear' }}
      />
    </div>
  )
}
