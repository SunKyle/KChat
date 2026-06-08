export function MessageSkeleton() {
  return (
    <div className='flex gap-3 p-4 max-w-4xl mx-auto animate-pulse'>
      <div className='flex-shrink-0 w-10 h-10 rounded-lg bg-[var(--bg-hover)] opacity-70' />
      <div className='flex-1 space-y-2'>
        <div className='h-4 bg-[var(--bg-hover)] rounded-md w-20 opacity-80' />
        <div className='space-y-1'>
          <div className='h-4 bg-[var(--bg-hover)] rounded-md w-full opacity-60' />
          <div className='h-4 bg-[var(--bg-hover)] rounded-md w-3/4 opacity-50' />
          <div className='h-4 bg-[var(--bg-hover)] rounded-md w-1/2 opacity-40' />
        </div>
      </div>
    </div>
  )
}

export function ConversationSkeleton() {
  return (
    <div className='flex items-center gap-3 p-3 animate-pulse'>
      <div className='w-10 h-10 rounded-lg bg-[var(--bg-hover)] opacity-70' />
      <div className='flex-1 space-y-1'>
        <div className='h-4 bg-[var(--bg-hover)] rounded-md w-3/4 opacity-80' />
        <div className='h-3 bg-[var(--bg-hover)] rounded-md w-1/2 opacity-60' />
      </div>
    </div>
  )
}
