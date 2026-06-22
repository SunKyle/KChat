import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'
import type { ApiError } from '../api/client'

interface ErrorToast {
  id: string
  message: string
  type: 'error' | 'warning' | 'success' | 'info'
  duration?: number
}

interface ErrorContextType {
  errors: ErrorToast[]
  showError: (message: string, duration?: number) => void
  showWarning: (message: string, duration?: number) => void
  showSuccess: (message: string, duration?: number) => void
  showInfo: (message: string, duration?: number) => void
  removeError: (id: string) => void
  clearAllErrors: () => void
  handleApiError: (error: unknown) => void
}

const ErrorContext = createContext<ErrorContextType | undefined>(undefined)

export function ErrorProvider({ children }: { children: ReactNode }) {
  const [errors, setErrors] = useState<ErrorToast[]>([])

  const addToast = useCallback(
    (message: string, type: ErrorToast['type'], duration = 5000) => {
      const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      const toast: ErrorToast = { id, message, type, duration }
      setErrors((prev) => [...prev, toast])

      if (duration > 0) {
        setTimeout(() => {
          setErrors((prev) => prev.filter((e) => e.id !== id))
        }, duration)
      }
    },
    []
  )

  const showError = useCallback(
    (message: string, duration?: number) => {
      addToast(message, 'error', duration)
    },
    [addToast]
  )

  const showWarning = useCallback(
    (message: string, duration?: number) => {
      addToast(message, 'warning', duration)
    },
    [addToast]
  )

  const showSuccess = useCallback(
    (message: string, duration?: number) => {
      addToast(message, 'success', duration)
    },
    [addToast]
  )

  const showInfo = useCallback(
    (message: string, duration?: number) => {
      addToast(message, 'info', duration)
    },
    [addToast]
  )

  const removeError = useCallback((id: string) => {
    setErrors((prev) => prev.filter((e) => e.id !== id))
  }, [])

  const clearAllErrors = useCallback(() => {
    setErrors([])
  }, [])

  const handleApiError = useCallback((error: unknown) => {
    let message = '发生未知错误'

    if (error instanceof Error) {
      const apiError = error as ApiError

      if (apiError.status === 401) {
        message = '登录已过期，请重新登录'
      } else if (apiError.status === 403) {
        message = '权限不足，无法执行此操作'
      } else if (apiError.status === 404) {
        message = '请求的资源不存在'
      } else if (apiError.status === 429) {
        message = '请求过于频繁，请稍后重试'
      } else if (apiError.status === 500) {
        message = '服务器内部错误，请稍后重试'
      } else if (apiError.code === 'NETWORK_ERROR') {
        message = '网络连接失败，请检查网络设置'
      } else {
        message = apiError.message || '请求失败'
      }
    }

    showError(message)
    console.error('API Error:', error)
  }, [showError])

  return (
    <ErrorContext.Provider
      value={{
        errors,
        showError,
        showWarning,
        showSuccess,
        showInfo,
        removeError,
        clearAllErrors,
        handleApiError,
      }}
    >
      {children}
      <ErrorToastContainer />
    </ErrorContext.Provider>
  )
}

function ErrorToastContainer() {
  const { errors, removeError } = useError()

  return (
    <div className="fixed top-4 right-4 z-50 space-y-2">
      {errors.map((toast) => (
        <div
          key={toast.id}
          className={`flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg backdrop-blur-sm min-w-[280px] max-w-[400px] animate-slide-in-right ${
            toast.type === 'error'
              ? 'bg-red-500/90 text-white'
              : toast.type === 'warning'
              ? 'bg-yellow-500/90 text-white'
              : toast.type === 'success'
              ? 'bg-green-500/90 text-white'
              : 'bg-blue-500/90 text-white'
          }`}
        >
          <span className="text-sm font-semibold flex-1">{toast.message}</span>
          <button
            onClick={() => removeError(toast.id)}
            className="p-1 hover:bg-white/20 rounded transition-colors"
          >
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      ))}
    </div>
  )
}

export function useError() {
  const context = useContext(ErrorContext)
  if (context === undefined) {
    throw new Error('useError must be used within an ErrorProvider')
  }
  return context
}
