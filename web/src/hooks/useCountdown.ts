import { useEffect, useState } from 'react'

/** Milliseconds left until a server timestamp, corrected for clock skew. */
export function useCountdown(endsAt: number, skewMs: number): number {
  const compute = () => (endsAt > 0 ? Math.max(0, endsAt - (Date.now() + skewMs)) : 0)
  const [left, setLeft] = useState(compute)

  useEffect(() => {
    setLeft(compute())
    if (endsAt <= 0) return
    const id = window.setInterval(() => setLeft(compute()), 200)
    return () => window.clearInterval(id)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [endsAt, skewMs])

  return left
}
