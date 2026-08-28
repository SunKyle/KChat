import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Icon } from './Icon'
import type { IconName } from './Icon'
import { useTheme } from '../../context/ThemeContext'

export function ThemeToggle() {
  const { theme, setTheme } = useTheme()
  const [animating, setAnimating] = useState(false)
  const [isOpen, setIsOpen] = useState(false)

  const themeLabels: Record<string, { label: string; icon: IconName }> = {
    light: { label: '明亮主题', icon: 'Sun' },
    dark: { label: '深色主题', icon: 'Moon' },
    'animal-island': { label: '动物岛主题', icon: 'Leaf' },
  }

  const currentTheme = themeLabels[theme] || themeLabels.light

  const handleSelectTheme = (newTheme: string) => {
    setAnimating(true)
    setTheme(newTheme as 'light' | 'dark' | 'animal-island')
    setIsOpen(false)
    setTimeout(() => setAnimating(false), 400)
  }

  return (
    <div className='relative'>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className='p-2 rounded-lg theme-bg-hover/30 hover:theme-bg-hover hover:scale-110 micro-fast focus-ring theme-text-muted hover:theme-text-primary'
        title={currentTheme.label}
        aria-label={currentTheme.label}
      >
        <motion.div
          animate={{ rotate: animating ? 360 : 0 }}
          transition={{ duration: 0.35, ease: [0.34, 1.56, 0.64, 1] }}
        >
          <Icon name={currentTheme.icon} size='lg' />
        </motion.div>
      </button>

      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.95 }}
            transition={{ duration: 0.15 }}
            className='absolute top-full right-0 mt-2 w-40 rounded-lg border border-[var(--border-primary)] bg-[var(--bg-card)] shadow-lg py-1 z-50'
          >
            {(
              Object.entries(themeLabels) as Array<[string, { label: string; icon: IconName }]>
            ).map(([name, { label, icon }]) => (
              <button
                key={name}
                onClick={() => handleSelectTheme(name)}
                className={`w-full flex items-center gap-3 px-4 py-2 text-sm transition-colors ${
                  theme === name
                    ? 'bg-[var(--bg-hover)] text-[var(--text-primary)]'
                    : 'text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'
                }`}
              >
                <Icon name={icon} size='md' />
                {label}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default ThemeToggle
