import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { IconProvider } from './components/Icon'
import { ThemeProvider } from './context/ThemeContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <IconProvider>
        <App />
      </IconProvider>
    </ThemeProvider>
  </StrictMode>
)
