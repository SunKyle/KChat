import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react'
import { type ThemeName, themes, getThemeColors } from '../theme/types'

interface ThemeContextType {
  theme: ThemeName
  setTheme: (theme: ThemeName) => void
  themeConfig: (typeof themes)[ThemeName]
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

const STORAGE_KEY = 'kchat-theme'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>(() => {
    const stored = localStorage.getItem(STORAGE_KEY) as ThemeName | null
    if (stored && stored in themes) {
      return stored
    }
    return 'dark'
  })

  const setTheme = useCallback((newTheme: ThemeName) => {
    setThemeState(newTheme)
    localStorage.setItem(STORAGE_KEY, newTheme)
  }, [])

  useEffect(() => {
    const colors = getThemeColors(theme)
    const root = document.documentElement

    Object.entries(colors).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })
  }, [theme])

  return (
    <ThemeContext.Provider value={{ theme, setTheme, themeConfig: themes[theme] }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }
  return context
}

export default ThemeContext
