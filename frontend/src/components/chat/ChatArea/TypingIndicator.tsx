export function TypingIndicator() {
  return (
    <div className='flex items-center gap-3'>
      <div className='flex gap-1.5'>
        <span
          className='w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce'
          style={{ animationDelay: '0ms', animationDuration: '600ms' }}
        />
        <span
          className='w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce'
          style={{ animationDelay: '150ms', animationDuration: '600ms' }}
        />
        <span
          className='w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce'
          style={{ animationDelay: '300ms', animationDuration: '600ms' }}
        />
      </div>
      <span className='theme-text-muted font-secondary font-weight-semibold'>
        AI 正在思考
        <span className='animate-pulse'>...</span>
      </span>
    </div>
  )
}
