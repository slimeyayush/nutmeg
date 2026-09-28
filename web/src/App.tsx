import { useEffect, useState } from 'react'
import { Home } from './screens/Home'
import { Room } from './screens/Room'
import { clearIdentity, loadIdentity, saveIdentity } from './lib/session'
import type { Identity } from './lib/types'

/** /r/ABCD -> "ABCD", anything else -> "" */
function codeFromPath(): string {
  const m = location.pathname.match(/^\/r\/([A-Za-z0-9]{4})\/?$/)
  return m ? m[1].toUpperCase() : ''
}

export function App() {
  const [pathCode, setPathCode] = useState(codeFromPath)
  const [identity, setIdentity] = useState<Identity | null>(() => {
    const code = codeFromPath()
    return code ? loadIdentity(code) : loadIdentity()
  })
  const [notice, setNotice] = useState<string | null>(null)

  // If we restored an identity on "/", put the room in the URL so refresh + share work.
  useEffect(() => {
    if (identity && codeFromPath() !== identity.roomCode) {
      history.replaceState(null, '', `/r/${identity.roomCode}`)
      setPathCode(identity.roomCode)
    }
  }, [identity])

  useEffect(() => {
    const onPop = () => {
      const code = codeFromPath()
      setPathCode(code)
      setIdentity(code ? loadIdentity(code) : null)
    }
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  const joined = (id: Identity) => {
    saveIdentity(id)
    history.pushState(null, '', `/r/${id.roomCode}`)
    setPathCode(id.roomCode)
    setNotice(null)
    setIdentity(id)
  }

  const leave = (message?: string) => {
    clearIdentity()
    history.pushState(null, '', '/')
    setPathCode('')
    setIdentity(null)
    setNotice(message ?? null)
  }

  return (
    <div className="mx-auto w-full max-w-[440px] px-4">
      {identity ? (
        <Room key={`${identity.roomCode}:${identity.playerId}`} identity={identity} onLeave={leave} />
      ) : (
        <Home key={pathCode} initialCode={pathCode} notice={notice} onJoined={joined} />
      )}
    </div>
  )
}
