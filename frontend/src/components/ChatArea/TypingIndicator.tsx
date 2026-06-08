export function TypingIndicator() {
  return (
    <div className='flex items-center gap-3'>
      <div className='flex gap-1.5'>
        <span
          className='w-2.5 h-2.5 bg-slate-400 rounded-full animate-bounce'
          style={{ animationDelay: '0ms', animationDuration: '600ms' }}
        />
        <span
          className='w-2.5 h-2.5 bg-slate-400 rounded-full animate-bounce'
          style={{ animationDelay: '150ms', animationDuration: '600ms' }}
        />
        <span
          className='w-2.5 h-2.5 bg-slate-400 rounded-full animate-bounce'
          style={{ animationDelay: '300ms', animationDuration: '600ms' }}
        />
      </div>
      <span className='text-slate-400 text-sm font-medium'>
        AI 正在思考
        <span className='animate-pulse'>...</span>
      </span>
    </div>
  )
}
