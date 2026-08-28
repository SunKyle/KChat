import { useTheme } from '../../context/ThemeContext'
import { Button as AnimalButton } from 'animal-island-ui'
import type { ReactNode } from 'react'

interface ButtonProps {
  children: ReactNode
  variant?: 'primary' | 'ghost' | 'secondary'
  onClick?: () => void
  disabled?: boolean
  className?: string
}

export function Button({
  children,
  variant = 'primary',
  onClick,
  disabled,
  className = '',
}: ButtonProps) {
  const { theme } = useTheme()
  const isAnimalIsland = theme === 'animal-island'

  if (isAnimalIsland) {
    const animalIslandType =
      variant === 'ghost' ? 'text' : variant === 'secondary' ? 'default' : 'primary'

    return (
      <AnimalButton
        type={animalIslandType}
        onClick={onClick}
        disabled={disabled}
        className={className}
      >
        {children}
      </AnimalButton>
    )
  }

  const baseStyles =
    'inline-flex items-center justify-center gap-1.5 px-4 py-2 rounded-lg text-sm font-semibold transition-all'

  const variantStyles = {
    primary: 'bg-[var(--brand-primaryButton)] text-white hover:brightness-110 hover:shadow-lg',
    ghost:
      'bg-transparent text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] hover:text-[var(--text-primary)]',
    secondary: 'bg-[var(--bg-hover)] text-[var(--text-secondary)] hover:bg-[var(--border-primary)]',
  }

  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`${baseStyles} ${variantStyles[variant]} ${className} ${disabled ? 'opacity-50 cursor-not-allowed' : ''}`}
    >
      {children}
    </button>
  )
}
