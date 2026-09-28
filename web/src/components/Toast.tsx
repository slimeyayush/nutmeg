import { useEffect, useState } from 'react'

export function Toast({ message, stamp }: { message: string | null; stamp: number }) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!message) return
    setVisible(true)
    const id = window.setTimeout(() => setVisible(false), 2600)
    return () => window.clearTimeout(id)
  }, [message, stamp])

  if (!visible || !message) return null
  return (
    <div
      role="status"
      aria-live="polite"
      className="toast-in fixed left-1/2 -translate-x-1/2 z-50 max-w-[400px] w-[calc(100%-2rem)] rounded-[10px] bg-[var(--color-chalk)] text-[var(--color-ink)] px-4 py-3 font-semibold shadow-[0_10px_30px_rgb(0_0_0/0.4)]"
      style={{ bottom: 'calc(env(safe-area-inset-bottom) + 20px)' }}
    >
      {message}
    </div>
  )
}
