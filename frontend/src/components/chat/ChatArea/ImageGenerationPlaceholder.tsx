import { Sparkles } from 'lucide-react'

export function ImageGenerationPlaceholder() {
  return (
    <div className='flex flex-col items-start py-2'>
      <div className='relative w-full max-w-[280px] aspect-square rounded-xl overflow-hidden border border-[var(--border-primary)] bg-[var(--bg-card)] shadow-sm'>
        {/* 暖色扫光 */}
        <div
          className='absolute inset-0 animate-shimmer-sweep'
          style={{
            background: `linear-gradient(115deg, transparent 35%, var(--accent-amber-opacity-6, rgba(210,180,140,0.06)) 42%, var(--accent-amber-opacity-10, rgba(210,180,140,0.10)) 48%, var(--accent-amber-opacity-6, rgba(210,180,140,0.06)) 54%, transparent 60%)`,
          }}
        />

        {/* 中心内容 */}
        <div className='absolute inset-0 flex flex-col items-center justify-center gap-3.5'>
          <div className='relative'>
            <span
              className='absolute inset-0 rounded-full animate-pulse'
              style={{
                background: `radial-gradient(circle, var(--accent-amber-opacity-25, rgba(210,180,140,0.25)) 0%, transparent 70%)`,
                transform: 'scale(2.8)',
              }}
            />
            <Sparkles className='w-6 h-6 text-[var(--brand-primary)]/80 relative z-10' />
          </div>

          <span className='text-sm font-medium tracking-[0.02em] text-[var(--text-secondary)]'>
            正在生成图片...
          </span>
        </div>
      </div>
    </div>
  )
}
