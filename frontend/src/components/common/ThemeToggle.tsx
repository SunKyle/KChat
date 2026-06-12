import { Sun, Moon } from 'lucide-react'
import { useTheme } from '../../context/ThemeContext'

export function ThemeToggle() {
  const { theme, setTheme } = useTheme()

  const isDark = theme === 'dark'

  return (
    <button
      onClick={() => setTheme(isDark ? 'light' : 'dark')}
      className='p-2 rounded-lg theme-bg-hover/30 hover:theme-bg-hover hover:scale-110 micro-fast focus-ring theme-text-muted hover:theme-text-primary'
      title={isDark ? '切换到明亮主题' : '切换到深色主题'}
    >
      {isDark ? <Sun className='w-[18px] h-[18px]' /> : <Moon className='w-[18px] h-[18px]' />}
    </button>
  )
}

export default ThemeToggle
