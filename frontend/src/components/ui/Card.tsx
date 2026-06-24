import { useTheme } from '../../context/ThemeContext'
import { Card as AnimalCard } from 'animal-island-ui'
import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
}

export function Card({ children, className = '' }: CardProps) {
  const { theme } = useTheme()
  const isAnimalIsland = theme === 'animal-island'

  if (isAnimalIsland) {
    return (
      <AnimalCard className={className}>
        {children}
      </AnimalCard>
    )
  }

  return (
    <div className={`rounded-xl border border-[var(--border-primary)] bg-[var(--bg-card)] ${className}`}>
      {children}
    </div>
  )
}