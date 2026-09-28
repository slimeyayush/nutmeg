import type { Identity } from './types'

const KEY = 'nutmeg.identity'

export function saveIdentity(id: Identity): void {
  localStorage.setItem(KEY, JSON.stringify(id))
}

export function loadIdentity(roomCode?: string): Identity | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const id = JSON.parse(raw) as Identity
    if (roomCode && id.roomCode !== roomCode.toUpperCase()) return null
    return id
  } catch {
    return null
  }
}

export function clearIdentity(): void {
  localStorage.removeItem(KEY)
}
