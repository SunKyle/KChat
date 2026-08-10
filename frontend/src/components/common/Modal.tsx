import type { ReactNode } from 'react'
import { useEffect, useCallback, useRef } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title?: string
  children?: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
  autoHeight?: boolean
  type?: 'danger' | 'warning' | 'info'
  confirmText?: string
  cancelText?: string
  onConfirm?: () => void
  message?: string
}

const sizeClasses = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
}

const buttonColorClass = {
  danger: 'bg-[var(--brand-danger)] hover:bg-[var(--brand-danger)]/90',
  warning: 'bg-[var(--brand-warning)] hover:bg-[var(--brand-warning)]/90',
  info: 'bg-[var(--brand-info)] hover:bg-[var(--brand-info)]/90',
}

export function Modal({
  isOpen,
  onClose,
  title,
  children,
  size = 'md',
  className = '',
  autoHeight = false,
  type = 'danger',
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  message,
}: ModalProps) {
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
      document.addEventListener('keydown', handleKeyDown)
      document.body.style.overflow = 'hidden'
      requestAnimationFrame(() => {
        const first = dialogRef.current?.querySelector<HTMLElement>(
          '[autofocus], button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        first?.focus()
      })
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

  const hasConfirm = typeof onConfirm === 'function'
  const titleId = title ? `modal-title-${title.replace(/\s+/g, '-')}` : undefined

  return (
    <div
      className='fixed inset-0 bg-black/50 backdrop-blur-md flex items-center justify-center z-50 p-4'
      onClick={onClose}
    >
      <div
        ref={dialogRef}
        role='dialog'
        aria-modal='true'
        aria-labelledby={titleId}
        className={`theme-bg-card rounded-2xl shadow-2xl w-full ${sizeClasses[size]} ${className} ${autoHeight ? 'max-h-[90vh] flex flex-col' : ''} animate-fade-in ${hasConfirm ? 'max-w-sm' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className='flex items-center justify-between px-6 py-4 border-b theme-border-primary'>
            <h3 id={titleId} className='font-title'>
              {title}
            </h3>
            {!hasConfirm && (
              <button onClick={onClose} className='icon-btn' aria-label='关闭对话框'>
                <X className='w-5 h-5' aria-hidden='true' />
              </button>
            )}
          </div>
        )}
        <div
          className={`${hasConfirm ? 'p-6' : `p-6 ${autoHeight ? 'flex-1 overflow-y-auto min-h-0' : ''}`}`}
        >
          {message && <p className='font-secondary mb-6 leading-relaxed'>{message}</p>}
          {children}
        </div>
        {hasConfirm && (
          <div className='flex items-center justify-end gap-3 px-6 pb-6'>
            <button onClick={onClose} className='btn-ghost px-5 py-2.5 text-sm'>
              {cancelText}
            </button>
            <button
              onClick={onConfirm}
              className={`px-5 py-2.5 text-sm text-white rounded-lg font-semibold transition-all active:scale-[0.97] ${buttonColorClass[type]}`}
            >
              {confirmText}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
