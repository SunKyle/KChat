import { useState } from 'react'
import { Sun, Moon } from 'lucide-react'
import { motion } from 'framer-motion'
import { useTheme } from '../../context/ThemeContext'

export function ThemeToggle() {
  const { theme, setTheme } = useTheme()
  const [animating, setAnimating] = useState(false)

  const isDark = theme === 'dark'

  const handleToggle = () => {
    setAnimating(true)
    setTheme(isDark ? 'light' : 'dark')
    setTimeout(() => setAnimating(false), 400)
  }

  return (
    <button
      onClick={handleToggle}
      className='p-2 rounded-lg theme-bg-hover/30 hover:theme-bg-hover hover:scale-110 micro-fast focus-ring theme-text-muted hover:theme-text-primary'
      title={isDark ? '切换到明亮主题' : '切换到深色主题'}
      aria-label={isDark ? '切换到明亮主题' : '切换到深色主题'}
    >
      <motion.div
        animate={{ rotate: animating ? 360 : 0 }}
        transition={{ duration: 0.35, ease: [0.34, 1.56, 0.64, 1] }}
      >
        {isDark ? <Sun className='w-[18px] h-[18px]' /> : <Moon className='w-[18px] h-[18px]' />}
      </motion.div>
    </button>
  )
}

export default ThemeToggle
