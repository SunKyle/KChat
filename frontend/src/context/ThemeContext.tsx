import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
  type ReactNode,
} from 'react'
import { type ThemeName, themes, getThemeColors } from '../theme/types'

interface ThemeContextType {
  theme: ThemeName
  setTheme: (theme: ThemeName) => void
  themeConfig: (typeof themes)[ThemeName]
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

const STORAGE_KEY = 'kchat-theme'
const TRANSITION_CLASS = 'theme-transitioning'

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>(() => {
    const stored = localStorage.getItem(STORAGE_KEY) as ThemeName | null
    if (stored && stored in themes) {
      return stored
    }
    return 'light'
  })
  const transitionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const setTheme = useCallback((newTheme: ThemeName) => {
    const root = document.documentElement

    root.classList.add(TRANSITION_CLASS)

    if (transitionTimerRef.current) {
      clearTimeout(transitionTimerRef.current)
    }

    setThemeState(newTheme)
    localStorage.setItem(STORAGE_KEY, newTheme)

    transitionTimerRef.current = setTimeout(() => {
      root.classList.remove(TRANSITION_CLASS)
    }, 350)
  }, [])

  useEffect(() => {
    return () => {
      if (transitionTimerRef.current) {
        clearTimeout(transitionTimerRef.current)
      }
    }
  }, [])

  useEffect(() => {
    const colors = getThemeColors(theme)
    const root = document.documentElement

    // 激活 tokens.css 中的 .light/.dark 样式块（--bg-code、--border-divider、
    // --shadow-color-* 等暗色 token 以及 .dark/.light 组件样式都依赖这些类）
    root.classList.toggle('light', theme === 'light')
    root.classList.toggle('dark', theme === 'dark')

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
