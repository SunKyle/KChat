import type { ReactNode } from 'react'
import { useEffect, useCallback } from 'react'
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
  danger: 'bg-red-500 hover:bg-red-600',
  warning: 'bg-yellow-500 hover:bg-yellow-600',
  info: 'bg-blue-500 hover:bg-blue-600',
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
      document.addEventListener('keydown', handleKeyDown)
      document.body.style.overflow = 'hidden'
    }

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
    }
  }, [isOpen, handleKeyDown])

  if (!isOpen) return null

  const hasConfirm = typeof onConfirm === 'function'

  return (
    <div
      className='fixed inset-0 bg-black/50 backdrop-blur-md flex items-center justify-center z-50 p-4'
      onClick={onClose}
    >
      <div
        className={`theme-bg-card rounded-2xl shadow-2xl w-full ${sizeClasses[size]} ${className} ${autoHeight ? 'max-h-[90vh] flex flex-col' : ''} animate-fade-in ${hasConfirm ? 'max-w-sm' : ''}`}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className='flex items-center justify-between px-6 py-4 border-b theme-border-primary'>
            <h3 className='font-title'>{title}</h3>
            {!hasConfirm && (
              <button
                onClick={onClose}
                className='icon-btn'
              >
                <X className='w-5 h-5' />
              </button>
            )}
          </div>
        )}
        <div className={`${hasConfirm ? 'p-6' : `p-6 ${autoHeight ? 'flex-1 overflow-y-auto min-h-0' : ''}`}`}>
          {message && <p className='font-secondary mb-6 leading-relaxed'>{message}</p>}
          {children}
        </div>
        {hasConfirm && (
          <div className='flex items-center justify-end gap-3 px-6 pb-6'>
            <button
              onClick={onClose}
              className='btn-ghost px-5 py-2.5 text-sm'
            >
              {cancelText}
            </button>
            <button
              onClick={onConfirm}
              className={`px-5 py-2.5 text-sm text-white rounded-lg font-medium transition-all active:scale-[0.97] ${buttonColorClass[type]}`}
            >
              {confirmText}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
