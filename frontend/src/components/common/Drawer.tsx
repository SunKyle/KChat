import { createPortal } from 'react-dom'
import type { ReactNode } from 'react'
import { useEffect, useCallback, useState, useRef } from 'react'
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
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousActiveElement = useRef<Element | null>(null)

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
        return
      }
      if (e.key === 'Tab' && dialogRef.current) {
        const focusable = dialogRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (!first) return
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault()
          last.focus()
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault()
          first.focus()
        }
      }
    },
    [onClose]
  )

  useEffect(() => {
    if (isOpen) {
      previousActiveElement.current = document.activeElement
      setIsAnimating(true)
      document.addEventListener('keydown', handleKeyDown)
      document.body.style.overflow = 'hidden'
      requestAnimationFrame(() => {
        const first = dialogRef.current?.querySelector<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        first?.focus()
      })

      const timer = setTimeout(() => setIsAnimating(false), 300)
      return () => clearTimeout(timer)
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
      if (previousActiveElement.current instanceof HTMLElement) {
        previousActiveElement.current.focus()
      }
    }
  }, [isOpen, handleKeyDown])

  if (!isOpen) return null

  const titleId = title ? `drawer-title-${title.replace(/\s+/g, '-')}` : undefined

  return createPortal(
    <div className='fixed inset-0 z-[60] flex justify-end' onClick={onClose}>
      <div
        className={`absolute inset-0 bg-black/30 backdrop-blur-sm transition-opacity duration-300 ${
          isAnimating ? 'opacity-0' : 'opacity-100'
        }`}
      />

      <div
        ref={dialogRef}
        role='dialog'
        aria-modal='true'
        aria-labelledby={titleId}
        className={`relative h-full ${sizeClasses[size]} shadow-2xl flex flex-col overflow-hidden rounded-l-2xl transition-transform duration-300 ease-out bg-[var(--bg-card)] ${
          isAnimating ? 'translate-x-full' : 'translate-x-0'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className='flex items-center justify-between px-6 py-4 border-b border-[var(--border-primary)]/30 bg-[var(--bg-card)]/80 backdrop-blur-sm'>
            <h2 id={titleId} className='font-title theme-text-primary'>
              {title}
            </h2>
            <button
              onClick={onClose}
              className='p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='关闭'
            >
              <X className='w-5 h-5' aria-hidden='true' />
            </button>
          </div>
        )}

        <div className={`flex-1 overflow-y-auto ${className}`}>{children}</div>
      </div>
    </div>,
    document.body
  )
}
