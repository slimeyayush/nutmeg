import { useState, type FormEvent, type ReactNode } from 'react'
import type { RoomSnapshot } from '../lib/types'

export interface GameProps<V> {
  room: RoomSnapshot
  view: V
  youId: string | null
  send: (type: string, payload?: Record<string, string>) => void
}

/** One text box + one button, clears itself after sending. */
export function AnswerBox({
  placeholder,
  button,
  onSubmit,
  maxLength = 40,
  autoFocus = true,
  colour,
}: {
  placeholder: string
  button: string
  onSubmit: (text: string) => void
  maxLength?: number
  autoFocus?: boolean
  colour?: string
}) {
  const [text, setText] = useState('')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t) return
    onSubmit(t)
    setText('')
  }
  return (
    <form onSubmit={submit} className="flex gap-2">
      <input
        className="field flex-1 min-w-0"
        value={text}
        maxLength={maxLength}
        placeholder={placeholder}
        autoFocus={autoFocus}
        autoComplete="off"
        autoCorrect="off"
        spellCheck={false}
        onChange={(e) => setText(e.target.value)}
      />
      <button className="btn" type="submit" disabled={!text.trim()} style={colour ? { background: colour } : undefined}>
        {button}
      </button>
    </form>
  )
}

export function Waiting({ children }: { children: ReactNode }) {
  return <p className="text-center text-[var(--color-muted)] py-3">{children}</p>
}
