import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import '../node_modules/animal-island-ui/dist/index.css'
import App from './App.tsx'
import { IconProvider } from './components/common/Icon'
import { ThemeProvider } from './context/ThemeContext'
import { ErrorProvider } from './context/ErrorContext'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('Root element not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <ErrorProvider>
      <ThemeProvider>
        <IconProvider>
          <App />
        </IconProvider>
      </ThemeProvider>
    </ErrorProvider>
  </StrictMode>
)
