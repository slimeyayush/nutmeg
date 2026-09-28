import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App'
// Self-hosted fonts: no Google request, works offline, only the latin subset + weights we use.
import '@fontsource/barlow/latin-400'
import '@fontsource/barlow/latin-500'
import '@fontsource/barlow/latin-600'
import '@fontsource/barlow/latin-700'
import '@fontsource/big-shoulders-display/latin-800'
import '@fontsource/big-shoulders-display/latin-900'
import '@fontsource/doto/latin-900'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
