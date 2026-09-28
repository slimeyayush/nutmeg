import { useCallback, useEffect, useRef, useState } from 'react'
import { clearIdentity } from '../lib/session'
import type { GameView, Identity, RoomSnapshot } from '../lib/types'

export type ConnStatus = 'connecting' | 'open' | 'reconnecting' | 'dead'

export interface GameSocket {
  status: ConnStatus
  room: RoomSnapshot | null
  game: GameView | null
  youId: string | null
  skewMs: number
  error: { code: string; message: string; at: number } | null
  deadReason: string | null
  send: (type: string, payload?: Record<string, string | number>) => void
  retry: () => void
}

// Errors that mean "this identity is finished" — go home, don't retry.
const FATAL = new Set(['ROOM_NOT_FOUND', 'ROOM_GONE', 'BAD_TOKEN', 'BAD_HELLO'])
const MAX_ATTEMPTS = 8

function wsUrl(): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${location.host}/ws`
}

export function useGameSocket(identity: Identity, onFatal: (message: string) => void): GameSocket {
  const [status, setStatus] = useState<ConnStatus>('connecting')
  const [room, setRoom] = useState<RoomSnapshot | null>(null)
  const [game, setGame] = useState<GameView | null>(null)
  const [youId, setYouId] = useState<string | null>(null)
  const [skewMs, setSkewMs] = useState(0)
  const [error, setError] = useState<GameSocket['error']>(null)
  const [deadReason, setDeadReason] = useState<string | null>(null)
  const [generation, setGeneration] = useState(0)

  const wsRef = useRef<WebSocket | null>(null)
  const onFatalRef = useRef(onFatal)
  onFatalRef.current = onFatal

  useEffect(() => {
    // One flag per effect run. StrictMode runs effects twice in dev; the first
    // run's socket must never reconnect or touch state after cleanup.
    let disposed = false
    let dead = false
    let attempts = 0
    let retryTimer: number | undefined

    const connect = () => {
      if (disposed) return
      setStatus(attempts === 0 ? 'connecting' : 'reconnecting')
      const ws = new WebSocket(wsUrl())
      wsRef.current = ws

      ws.onopen = () => {
        if (disposed) return
        ws.send(JSON.stringify({
          type: 'HELLO',
          payload: {
            roomCode: identity.roomCode,
            playerId: identity.playerId,
            playerToken: identity.playerToken,
          },
        }))
      }

      ws.onmessage = (event) => {
        if (disposed) return
        let frame: { type: string; payload: any }
        try {
          frame = JSON.parse(event.data)
        } catch {
          return
        }
        switch (frame.type) {
          case 'WELCOME':
            attempts = 0
            setStatus('open')
            setYouId(frame.payload?.you?.id ?? null)
            break
          case 'STATE':
            setStatus('open')
            setRoom((prev) => frame.payload?.room ?? prev)
            setGame(frame.payload?.game ?? null)
            if (typeof frame.payload?.now === 'number') {
              setSkewMs(frame.payload.now - Date.now())
            }
            break
          case 'ERROR': {
            const code: string = frame.payload?.code ?? 'ERROR'
            const message: string = frame.payload?.message ?? 'Something went wrong.'
            if (FATAL.has(code)) {
              dead = true
              clearIdentity()
              ws.close()
              onFatalRef.current(message)
            } else if (code === 'REPLACED') {
              dead = true
              setStatus('dead')
              setDeadReason(message)
            } else {
              setError({ code, message, at: Date.now() })
            }
            break
          }
          default:
            break
        }
      }

      ws.onclose = (event) => {
        if (disposed || dead) return
        if (event.code === 4000) {
          dead = true
          setStatus('dead')
          setDeadReason('This room is open in another tab.')
          return
        }
        attempts++
        if (attempts > MAX_ATTEMPTS) {
          setStatus('dead')
          setDeadReason('Lost connection to the server.')
          return
        }
        setStatus('reconnecting')
        const backoff = Math.min(8000, 400 * 2 ** attempts)
        retryTimer = window.setTimeout(connect, backoff / 2 + Math.random() * (backoff / 2))
      }
    }

    // Phones kill sockets when the screen locks. Reconnect the moment we're back.
    const onVisible = () => {
      if (document.visibilityState !== 'visible' || disposed || dead) return
      const ws = wsRef.current
      if (!ws || ws.readyState === WebSocket.CLOSED) {
        window.clearTimeout(retryTimer)
        attempts = 0
        connect()
      }
    }

    setDeadReason(null)
    connect()
    document.addEventListener('visibilitychange', onVisible)

    return () => {
      disposed = true
      window.clearTimeout(retryTimer)
      document.removeEventListener('visibilitychange', onVisible)
      const ws = wsRef.current
      if (ws) {
        if (ws.readyState === WebSocket.CONNECTING) {
          // Closing a CONNECTING socket logs a noisy browser error; wait until it opens.
          ws.onopen = () => ws.close()
        } else {
          ws.close()
        }
      }
    }
  }, [identity.roomCode, identity.playerId, identity.playerToken, generation])

  const send = useCallback((type: string, payload: Record<string, string | number> = {}) => {
    const ws = wsRef.current
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type, payload }))
    }
  }, [])

  const retry = useCallback(() => setGeneration((g) => g + 1), [])

  return { status, room, game, youId, skewMs, error, deadReason, send, retry }
}
