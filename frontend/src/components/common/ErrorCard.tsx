import { useEffect, useState } from 'react'
import { XCircle, AlertTriangle, Info, CheckCircle, X, RotateCcw } from 'lucide-react'

export type ErrorSeverity = 'error' | 'warning' | 'info' | 'success'

export interface ErrorCardProps {
  isVisible: boolean
  severity?: ErrorSeverity
  title: string
  description?: string
  onClose?: () => void
  onRetry?: () => void
  showCloseButton?: boolean
  showRetryButton?: boolean
  autoDismiss?: boolean
  autoDismissDelay?: number
}

const severityConfig = {
  error: {
    icon: XCircle,
    iconBg: 'bg-red-500/15',
    iconColor: 'text-red-400',
    titleColor: 'text-red-400',
    buttonBg: 'bg-red-500/15',
    buttonHover: 'hover:bg-red-500/25',
    buttonText: 'text-red-300',
    borderColor: 'border-red-500/20',
    shadowColor: 'shadow-red-500/15',
  },
  warning: {
    icon: AlertTriangle,
    iconBg: 'bg-amber-500/15',
    iconColor: 'text-amber-400',
    titleColor: 'text-amber-400',
    buttonBg: 'bg-amber-500/15',
    buttonHover: 'hover:bg-amber-500/25',
    buttonText: 'text-amber-300',
    borderColor: 'border-amber-500/20',
    shadowColor: 'shadow-[var(--accent-amber)]/15',
  },
  info: {
    icon: Info,
    iconBg: 'bg-blue-500/15',
    iconColor: 'text-blue-400',
    titleColor: 'text-blue-400',
    buttonBg: 'bg-blue-500/15',
    buttonHover: 'hover:bg-blue-500/25',
    buttonText: 'text-blue-300',
    borderColor: 'border-blue-500/20',
    shadowColor: 'shadow-blue-500/15',
  },
  success: {
    icon: CheckCircle,
    iconBg: 'bg-green-500/15',
    iconColor: 'text-green-400',
    titleColor: 'text-green-400',
    buttonBg: 'bg-green-500/15',
    buttonHover: 'hover:bg-green-500/25',
    buttonText: 'text-green-300',
    borderColor: 'border-green-500/20',
    shadowColor: 'shadow-green-500/15',
  },
}

export function ErrorCard({
  isVisible,
  severity = 'error',
  title,
  description,
  onClose,
  onRetry,
  showCloseButton = true,
  showRetryButton = false,
  autoDismiss = false,
  autoDismissDelay = 5000,
}: ErrorCardProps) {
  const [isShowing, setIsShowing] = useState(false)
  const [isExiting, setIsExiting] = useState(false)

  const config = severityConfig[severity]
  const IconComponent = config.icon

  useEffect(() => {
    if (isVisible && !isShowing) {
      setIsShowing(true)
    } else if (!isVisible && isShowing && !isExiting) {
      setIsExiting(true)
      setTimeout(() => {
        setIsShowing(false)
        setIsExiting(false)
      }, 300)
    }
  }, [isVisible, isShowing, isExiting])

  useEffect(() => {
    if (isVisible && autoDismiss && onClose) {
      const timer = setTimeout(() => {
        onClose()
      }, autoDismissDelay)
      return () => clearTimeout(timer)
    }
  }, [isVisible, autoDismiss, autoDismissDelay, onClose])

  if (!isShowing) return null

  return (
    <div
      className={`
        fixed top-6 left-1/2 -translate-x-1/2 z-50
        max-w-lg w-[calc(100%-2rem)]
        transition-all duration-300 ease-out
        ${isExiting ? 'opacity-0 translate-y-2 scale-98' : 'opacity-100 translate-y-0 scale-100'}
      `}
    >
      <div
        className={`
        relative rounded-2xl border ${config.borderColor}
        bg-[var(--bg-overlay)] backdrop-blur-sm
        p-5
        shadow-lg ${config.shadowColor}
      `}
      >
        <div className='flex items-start gap-4'>
          <div
            className={`
            flex-shrink-0 w-9 h-9
            rounded-full ${config.iconBg}
            flex items-center justify-center
          `}
          >
            <IconComponent className={`w-5 h-5 ${config.iconColor}`} />
          </div>

          <div className='flex-1 min-w-0'>
            <div className='flex items-start justify-between gap-3'>
              <h3 className={`text-xl font-weight-semibold ${config.titleColor}`}>{title}</h3>

              {showCloseButton && onClose && (
                <button
                  onClick={onClose}
                  className='flex-shrink-0 icon-btn'
                  aria-label='关闭'
                >
                  <X className='w-4 h-4 text-[var(--text-muted)] hover:text-[var(--text-secondary)]' />
                </button>
              )}
            </div>

            {description && (
              <p className='mt-2 font-secondary leading-relaxed'>{description}</p>
            )}

            {(showRetryButton || onRetry) && (
              <button
                onClick={onRetry}
                className={`
                  mt-4 inline-flex items-center gap-2 text-sm font-semibold btn-ghost
                  px-4 py-2 rounded-lg
                  ${config.buttonBg} ${config.buttonHover} ${config.buttonText}
                  transition-all hover:scale-[1.02] active:scale-[0.98]
                `}
              >
                <RotateCcw className='w-4 h-4' />
                重试
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default ErrorCard
