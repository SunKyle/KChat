import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { IconProvider } from './components/Icon'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <IconProvider>
      <App />
    </IconProvider>
  </StrictMode>,
)
