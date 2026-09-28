import { useState } from 'react'
import { ApiError, createRoom, joinRoom } from '../lib/api'
import { GAMES } from '../lib/games'
import type { Identity } from '../lib/types'

interface Props {
  initialCode: string
  notice: string | null
  onJoined: (id: Identity) => void
}

const NAME_KEY = 'nutmeg.name'

export function Home({ initialCode, notice, onJoined }: Props) {
  const [name, setName] = useState(() => localStorage.getItem(NAME_KEY) ?? '')
  const [code, setCode] = useState(initialCode.toUpperCase())
  const [busy, setBusy] = useState<'create' | 'join' | null>(null)
  const [error, setError] = useState<string | null>(notice)

  const cleanName = name.trim()
  const nameOk = cleanName.length >= 2 && cleanName.length <= 12
  const codeOk = /^[A-Z0-9]{4}$/.test(code)

  async function go(kind: 'create' | 'join') {
    if (!nameOk) {
      setError('Pick a name between 2 and 12 characters.')
      return
    }
    setBusy(kind)
    setError(null)
    try {
      localStorage.setItem(NAME_KEY, cleanName)
      const id = kind === 'create' ? await createRoom(cleanName) : await joinRoom(code, cleanName)
      onJoined(id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Couldn't reach the server. Is it running?")
    } finally {
      setBusy(null)
    }
  }

  return (
    <main className="min-h-[100dvh] flex flex-col justify-between gap-10 pt-10">
      <header>
        <h1 className="display text-[5.5rem] sm:text-[6.5rem] text-[var(--color-chalk)]">Nutmeg</h1>
        <p className="mt-5 text-lg text-[var(--color-muted)] max-w-[32ch]">
          Football party games for you and your mates. Get everyone on a call, open a room, play.
        </p>
      </header>

      <section className="flex flex-col gap-5" aria-label="Start or join">
        <label className="flex flex-col gap-1.5">
          <span className="font-semibold">Your name</span>
          <input
            className="field"
            value={name}
            maxLength={12}
            autoComplete="nickname"
            placeholder="What your mates call you"
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        {initialCode ? null : (
          <button className="btn w-full" disabled={busy !== null} onClick={() => go('create')}>
            {busy === 'create' ? 'Opening a room…' : 'Start a room'}
          </button>
        )}

        <div className="flex flex-col gap-1.5">
          <span className="font-semibold">{initialCode ? 'Joining room' : 'Or join with a code'}</span>
          <div className="flex gap-3 items-stretch">
            <input
              className="led led-input flex-1 min-w-0"
              style={{ fontSize: 34, padding: '10px 12px' }}
              value={code}
              maxLength={4}
              inputMode="text"
              autoCapitalize="characters"
              autoCorrect="off"
              spellCheck={false}
              placeholder="CODE"
              aria-label="Room code"
              onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
              onKeyDown={(e) => e.key === 'Enter' && codeOk && go('join')}
            />
            <button
              className={initialCode ? 'btn' : 'btn btn-ghost'}
              disabled={busy !== null || !codeOk}
              onClick={() => go('join')}
            >
              {busy === 'join' ? 'Joining…' : 'Join'}
            </button>
          </div>
        </div>

        {error && (
          <p role="alert" className="rounded-[10px] bg-[rgb(230_57_70/0.16)] border border-[rgb(230_57_70/0.5)] px-4 py-3">
            {error}
          </p>
        )}
      </section>

      <footer className="pb-6">
        <p className="text-sm text-[var(--color-muted)] mb-3">In the kit bag</p>
        <ul className="grid grid-cols-2 gap-x-4 gap-y-2">
          {GAMES.map((g) => (
            <li key={g.id} className="flex items-center gap-2">
              <span className="inline-block w-2.5 h-4 rounded-[2px]" style={{ background: g.colour }} aria-hidden />
              <span className="font-semibold">{g.name}</span>
            </li>
          ))}
        </ul>
      </footer>
    </main>
  )
}
