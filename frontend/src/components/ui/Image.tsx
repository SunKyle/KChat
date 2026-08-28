import { useState, useCallback } from 'react'
import { Icon } from '../common/Icon'

interface ImageProps {
  src: string
  alt?: string
  /** Tailwind 高度限制类，例如 'max-h-64'、'max-h-96' */
  maxHeightClass?: string
  /** 附加给外层容器的类名 */
  className?: string
  /** 是否展示悬浮操作按钮（放大/下载），默认 true */
  showActions?: boolean
}

/**
 * 正式图片组件：统一处理加载态、错误态、点击放大与下载。
 * 使用 CSS 变量适配主题，不硬编码颜色/字号。
 */
export function Image({
  src,
  alt = 'Image',
  maxHeightClass = 'max-h-64',
  className = '',
  showActions = true,
}: ImageProps) {
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState(false)
  const [expanded, setExpanded] = useState(false)

  const handleDownload = useCallback(async () => {
    try {
      const response = await fetch(src)
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = src.split('/').pop() || `image-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    } catch (e) {
      console.error('Download failed:', e)
      window.open(src, '_blank')
    }
  }, [src])

  if (error) {
    return (
      <span
        className={`inline-flex items-center justify-center rounded-lg border border-[var(--border-divider)] theme-bg-hover/40 ${maxHeightClass} w-full ${className}`}
      >
        <span className='flex flex-col items-center gap-2 py-6 text-[var(--text-muted)]'>
          <Icon name='ImageOff' size='lg' />
          <span className='text-xs'>图片加载失败</span>
        </span>
      </span>
    )
  }

  return (
    <>
      <span
        className={`relative group inline-block rounded-lg overflow-hidden border border-[var(--border-divider)] ${className}`}
      >
        {!loaded && (
          <span
            className={`absolute inset-0 flex items-center justify-center theme-bg-hover/30 z-10 ${maxHeightClass}`}
            style={{ minWidth: '12rem', minHeight: '9rem' }}
          >
            <Icon name='Loader2' size='lg' className='animate-spin text-[var(--brand-primary)]' />
          </span>
        )}
        <img
          src={src}
          alt={alt}
          loading='lazy'
          onLoad={() => setLoaded(true)}
          onError={() => setError(true)}
          className={`block object-contain rounded-lg transition-opacity duration-200 ${maxHeightClass} ${
            loaded ? 'opacity-100' : 'opacity-0'
          }`}
        />
        {loaded && showActions && (
          <span className='absolute bottom-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity'>
            <button
              onClick={() => setExpanded(true)}
              className='icon-btn-sm theme-bg-card/80 backdrop-blur-sm rounded-lg'
              title='查看大图'
              aria-label='查看大图'
            >
              <Icon name='ZoomIn' size='sm' className='theme-text-primary' />
            </button>
            <button
              onClick={handleDownload}
              className='icon-btn-sm theme-bg-card/80 backdrop-blur-sm rounded-lg'
              title='下载图片'
              aria-label='下载图片'
            >
              <Icon name='Download' size='sm' className='theme-text-primary' />
            </button>
          </span>
        )}
      </span>

      {expanded && (
        <div
          className='fixed inset-0 z-50 theme-bg-card/95 backdrop-blur-md flex items-center justify-center p-4'
          onClick={() => setExpanded(false)}
        >
          <div className='absolute top-4 right-4 flex gap-2'>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleDownload()
              }}
              className='icon-btn'
              title='下载图片'
              aria-label='下载图片'
            >
              <Icon name='Download' size='xl' className='theme-text-primary' />
            </button>
            <button
              className='icon-btn'
              onClick={(e) => {
                e.stopPropagation()
                setExpanded(false)
              }}
              title='关闭'
              aria-label='关闭'
            >
              <Icon name='X' size='xl' className='theme-text-primary' />
            </button>
          </div>
          <img
            src={src}
            alt={alt}
            className='max-w-full max-h-full object-contain rounded-lg shadow-2xl'
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </>
  )
}
