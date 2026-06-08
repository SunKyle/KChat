import { X } from 'lucide-react'

interface ConfirmDialogProps {
  isOpen: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  onConfirm: () => void
  onCancel: () => void
  type?: 'danger' | 'warning' | 'info'
}

export function ConfirmDialog({
  isOpen,
  title,
  message,
  confirmText = '确认',
  cancelText = '取消',
  onConfirm,
  onCancel,
  type = 'danger',
}: ConfirmDialogProps) {
  if (!isOpen) return null

  const buttonColorClass = {
    danger: 'bg-red-500 hover:bg-red-600',
    warning: 'bg-yellow-500 hover:bg-yellow-600',
    info: 'bg-blue-500 hover:bg-blue-600',
  }[type]

  return (
    <div className='fixed inset-0 z-[100] flex items-center justify-center' onClick={onCancel}>
      <div className='absolute inset-0 theme-bg-primary/70 backdrop-blur-sm' />
      <div
        className='relative theme-bg-card rounded-2xl card-inset max-w-sm w-[90%] mx-4 p-6 animate-fade-in border theme-border-primary'
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onCancel}
          className='absolute top-4 right-4 icon-btn'
        >
          <X className='w-4 h-4 theme-text-muted' />
        </button>

        <h3 className='font-title mb-2 pr-6'>{title}</h3>
        <p className='font-secondary mb-6 leading-relaxed'>{message}</p>

        <div className='flex items-center justify-end gap-3'>
          <button
            onClick={onCancel}
            className='btn-ghost px-5 py-2.5 text-sm'
          >
            {cancelText}
          </button>
          <button
            onClick={onConfirm}
            className={`px-5 py-2.5 text-sm text-white rounded-lg font-medium transition-all active:scale-[0.97] ${buttonColorClass}`}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  )
}
