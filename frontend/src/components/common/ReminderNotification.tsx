import { useEffect, useState } from 'react'
import { Icon } from './Icon'

interface ReminderItem {
  id: string
  message: string
}

/**
 * 全局居中提醒弹窗。
 *
 * 监听 `reminder-fired` 事件（由通知 SSE 的 `reminder` 事件触发），
 * 在屏幕中央弹出提醒卡片，仅支持用户手动关闭。
 */
export function ReminderNotification() {
  const [reminders, setReminders] = useState<ReminderItem[]>([])

  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ message?: string }>).detail
      if (!detail?.message) return
      const message = detail.message
      setReminders((prev) => [
        ...prev,
        { id: crypto.randomUUID(), message },
      ])
    }
    window.addEventListener('reminder-fired', handler)
    return () => window.removeEventListener('reminder-fired', handler)
  }, [])

  const dismiss = (id: string) => {
    setReminders((prev) => prev.filter((r) => r.id !== id))
  }

  if (reminders.length === 0) return null

  return (
    <div className='fixed inset-0 z-[60] flex items-center justify-center p-4 pointer-events-none'>
      <div className='pointer-events-auto w-full max-w-sm space-y-3'>
        {reminders.map((r) => (
          <div
            key={r.id}
            className='theme-bg-card border theme-border-primary rounded-2xl shadow-2xl overflow-hidden animate-fade-in'
          >
            <div className='flex items-center justify-between px-5 py-3 border-b theme-border-primary'>
              <div className='flex items-center gap-2'>
                <Icon name='Bell' size='md' className='text-[var(--text-secondary)]' aria-hidden='true' />
                <h3 className='font-title text-sm text-[var(--text-primary)]'>提醒</h3>
              </div>
              <button onClick={() => dismiss(r.id)} className='icon-btn' aria-label='关闭提醒'>
                <Icon name='X' size='lg' aria-hidden='true' />
              </button>
            </div>
            <div className='px-5 py-4'>
              <p className='font-secondary text-sm leading-relaxed whitespace-pre-line text-[var(--text-primary)]'>
                {r.message}
              </p>
            </div>
            <div className='px-5 pb-4 flex justify-end'>
              <button
                onClick={() => dismiss(r.id)}
                className='px-5 py-2 rounded-lg text-sm font-semibold text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all active:scale-[0.97]'
              >
                知道了
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}