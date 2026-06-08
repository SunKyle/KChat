export function MessageSkeleton() {
  return (
    <div className='flex gap-3 p-4 max-w-4xl mx-auto animate-pulse'>
      <div className='flex-shrink-0 w-10 h-10 rounded-full bg-slate-700' />
      <div className='flex-1 space-y-2'>
        <div className='h-4 bg-slate-700 rounded w-20' />
        <div className='space-y-1'>
          <div className='h-4 bg-slate-700 rounded w-full' />
          <div className='h-4 bg-slate-700 rounded w-3/4' />
          <div className='h-4 bg-slate-700 rounded w-1/2' />
        </div>
      </div>
    </div>
  )
}

export function ConversationSkeleton() {
  return (
    <div className='flex items-center gap-3 p-3 animate-pulse'>
      <div className='w-10 h-10 rounded-lg bg-slate-700' />
      <div className='flex-1 space-y-1'>
        <div className='h-4 bg-slate-700 rounded w-3/4' />
        <div className='h-3 bg-slate-700 rounded w-1/2' />
      </div>
    </div>
  )
}
