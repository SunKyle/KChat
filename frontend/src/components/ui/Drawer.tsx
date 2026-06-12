import type { ReactNode } from 'react'
import { useEffect, useCallback, useState } from 'react'
import { X } from 'lucide-react'

interface DrawerProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}

const sizeClasses = {
  sm: 'w-80',
  md: 'w-96',
  lg: 'w-[520px]',
  xl: 'w-[720px]',
}

export function Drawer({
  isOpen,
  onClose,
  title,
  children,
  size = 'lg',
  className = '',
}: DrawerProps) {
  const [isAnimating, setIsAnimating] = useState(false)

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    },
    [onClose]
  )

  useEffect(() => {
    if (isOpen) {
      setIsAnimating(true)
      document.addEventListener('keydown', handleKeyDown)
      document.body.style.overflow = 'hidden'
      
      const timer = setTimeout(() => setIsAnimating(false), 300)
      return () => clearTimeout(timer)
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
    }
  }, [isOpen, handleKeyDown])

  if (!isOpen) return null

  return (
    <div
      className='fixed inset-0 z-50 flex justify-end'
      onClick={onClose}
    >
      <div 
        className={`absolute inset-0 bg-black/30 backdrop-blur-sm transition-opacity duration-300 ${
          isAnimating ? 'opacity-0' : 'opacity-100'
        }`}
      />
      
      <div
        className={`relative h-full ${sizeClasses[size]} shadow-2xl flex flex-col overflow-hidden rounded-l-2xl transition-transform duration-300 ease-out ${
          isAnimating ? 'translate-x-full' : 'translate-x-0'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className='flex items-center justify-between px-6 py-4 border-b border-[var(--border-primary)]/30 bg-[var(--bg-card)]/80 backdrop-blur-sm'>
            <h2 className='font-title theme-text-primary'>{title}</h2>
            <button
              onClick={onClose}
              className='p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='关闭'
            >
              <X className='w-5 h-5' />
            </button>
          </div>
        )}
        
        <div className={`flex-1 overflow-y-auto ${className}`}>
          {children}
        </div>
      </div>
    </div>
  )
}
