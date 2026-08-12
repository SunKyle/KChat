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

const ANIMATION_DURATION = 300

export function Drawer({
  isOpen,
  onClose,
  title,
  children,
  size = 'lg',
  className = '',
}: DrawerProps) {
  const [mounted, setMounted] = useState(isOpen)
  const [isClosing, setIsClosing] = useState(false)
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousActiveElement = useRef<Element | null>(null)
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isOpenRef = useRef(isOpen)

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
    isOpenRef.current = isOpen
  }, [isOpen])

  // 打开：挂载 DOM（off-screen），下一帧触发入场动画
  useEffect(() => {
    if (!isOpen) return

    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current)
      closeTimerRef.current = null
    }
    setMounted(true)
    setIsClosing(true)
  }, [isOpen])

  // 挂载 + isOpen 为 true 时执行入场动画和副作用
  useEffect(() => {
    if (!mounted || !isOpen) return

    previousActiveElement.current = document.activeElement
    document.addEventListener('keydown', handleKeyDown)
    document.body.style.overflow = 'hidden'

    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (!isOpenRef.current) return
        setIsClosing(false)
        const first = dialogRef.current?.querySelector<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        )
        first?.focus()
      })
    })

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = ''
      if (previousActiveElement.current instanceof HTMLElement) {
        previousActiveElement.current.focus()
      }
    }
  }, [mounted, isOpen, handleKeyDown])

  // 用户主动关闭：通知父组件更新 isOpen
  const handleClose = useCallback(() => {
    if (closeTimerRef.current) return
    onClose()
  }, [onClose])

  // isOpen 变为 false 时播放出场动画并卸载
  useEffect(() => {
    if (!isOpen && mounted) {
      if (closeTimerRef.current) return
      setIsClosing(true)
      closeTimerRef.current = setTimeout(() => {
        setMounted(false)
        setIsClosing(false)
        closeTimerRef.current = null
      }, ANIMATION_DURATION)
    }
  }, [isOpen, mounted])

  useEffect(() => {
    return () => {
      if (closeTimerRef.current) clearTimeout(closeTimerRef.current)
    }
  }, [])

  if (!mounted) return null

  const titleId = title ? `drawer-title-${title.replace(/\s+/g, '-')}` : undefined
  const isEnteringOrOpen = !isClosing

  return createPortal(
    <div className='fixed inset-0 z-[60] flex justify-end' onClick={handleClose}>
      <div
        className={`absolute inset-0 bg-black/30 backdrop-blur-sm transition-opacity duration-300 ${
          isEnteringOrOpen ? 'opacity-100' : 'opacity-0'
        }`}
      />

      <div
        ref={dialogRef}
        role='dialog'
        aria-modal='true'
        aria-labelledby={titleId}
        className={`relative h-full ${sizeClasses[size]} shadow-2xl flex flex-col overflow-hidden rounded-l-2xl transition-all duration-300 ease-out bg-[var(--bg-card)] ${
          isEnteringOrOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className='flex items-center justify-between px-6 py-4 border-b border-[var(--border-primary)]/30 bg-[var(--bg-card)]/80 backdrop-blur-sm'>
            <h2 id={titleId} className='font-title theme-text-primary'>
              {title}
            </h2>
            <button
              onClick={handleClose}
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
