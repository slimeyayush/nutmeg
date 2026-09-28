import type { Identity } from './types'

export class ApiError extends Error {
  constructor(public code: string, message: string) {
    super(message)
  }
}

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    let code = 'HTTP_' + res.status
    let message = "Couldn't reach the server. Is it running?"
    try {
      const problem = await res.json()
      code = problem.code ?? code
      message = problem.detail ?? message
    } catch {
      /* not JSON — keep the default */
    }
    throw new ApiError(code, message)
  }
  return res.json() as Promise<T>
}

export function createRoom(name: string): Promise<Identity> {
  return post('/api/rooms', { name })
}

export function joinRoom(code: string, name: string): Promise<Identity> {
  return post(`/api/rooms/${encodeURIComponent(code.toUpperCase())}/players`, { name })
}
