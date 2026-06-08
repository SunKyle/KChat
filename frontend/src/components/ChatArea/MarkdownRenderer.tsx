import ReactMarkdown from 'react-markdown'
import { CodeBlock } from './CodeBlock'
import { useState } from 'react'
import { ZoomIn, X, Download } from 'lucide-react'

interface MarkdownRendererProps {
  content: string
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const [imageLoaded, setImageLoaded] = useState<Record<string, boolean>>({})
  const [expandedImage, setExpandedImage] = useState<string | null>(null)

  const handleImageLoad = (src: string) => {
    setImageLoaded((prev) => ({ ...prev, [src]: true }))
  }

  const handleDownload = async (src: string, filename?: string) => {
    try {
      const response = await fetch(src)
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename || `generated-image-${Date.now()}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Download failed:', error)
      window.open(src, '_blank')
    }
  }

  return (
    <>
      {expandedImage && (
        <div
          className='fixed inset-0 z-50 theme-bg-card/95 flex items-center justify-center p-4'
          onClick={() => setExpandedImage(null)}
        >
          <div className='absolute top-4 right-4 flex gap-2'>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleDownload(expandedImage)
              }}
              className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 transition-colors'
              title='下载图片'
            >
              <Download className='w-6 h-6 theme-text-primary' />
            </button>
            <button
              className='p-2 rounded-lg theme-bg-hover hover:theme-bg-hover/80 transition-colors'
              onClick={(e) => {
                e.stopPropagation()
                setExpandedImage(null)
              }}
            >
              <X className='w-6 h-6 theme-text-primary' />
            </button>
          </div>
          <img
            src={expandedImage}
            alt='Expanded'
            className='max-w-full max-h-full object-contain rounded-lg shadow-2xl'
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
      <div className='markdown-body text-sm leading-relaxed'>
        <ReactMarkdown
          components={{
            code({ node: _node, inline, className, children, ...props }) {
              const match = /language-(\w+)/.exec(className || '')
              return !inline && match ? (
                <CodeBlock code={String(children).replace(/\n$/, '')} language={match[1]} />
              ) : (
                <code
                  className='theme-bg-hover/50 rounded px-1.5 py-0.5 text-sm font-mono theme-text-primary'
                  {...props}
                >
                  {children}
                </code>
              )
            },
            img: ({ src, alt }) => {
              if (!src) return null
              const filename = src.split('/').pop() || 'generated-image.png'
              return (
                <div className='relative group my-4 inline-block'>
                  {!imageLoaded[src] && (
                    <div className='absolute inset-0 theme-bg-hover/50 flex items-center justify-center z-10 rounded-lg'>
                      <div className='w-8 h-8 border-2 border-sky-400 border-t-transparent rounded-full animate-spin' />
                    </div>
                  )}
                  <img
                    src={src}
                    alt={alt || 'Generated image'}
                    className={`max-w-full max-h-96 object-contain rounded-lg shadow-lg transition-all cursor-zoom-in ${
                      imageLoaded[src]
                        ? 'opacity-100 hover:shadow-xl hover:shadow-sky-500/10'
                        : 'opacity-0'
                    }`}
                    onLoad={() => handleImageLoad(src)}
                    onClick={() => setExpandedImage(src)}
                  />
                  <div className='absolute bottom-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity'>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDownload(src, filename)
                      }}
                      className='p-1.5 theme-bg-card/80 hover:theme-bg-card rounded-lg backdrop-blur-sm transition-colors'
                      title='下载图片'
                    >
                      <Download className='w-4 h-4 theme-text-primary' />
                    </button>
                    <div className='p-1.5 theme-bg-card/80 rounded-lg backdrop-blur-sm'>
                      <ZoomIn className='w-4 h-4 theme-text-primary' />
                    </div>
                  </div>
                </div>
              )
            },
            h1: ({ children }) => (
              <h1 className='text-2xl font-bold mt-4 mb-2 theme-text-primary'>{children}</h1>
            ),
            h2: ({ children }) => (
              <h2 className='text-xl font-bold mt-3 mb-2 theme-text-primary'>{children}</h2>
            ),
            h3: ({ children }) => (
              <h3 className='text-lg font-bold mt-2 mb-1 theme-text-primary'>{children}</h3>
            ),
            p: ({ children }) => <p className='mb-3 last:mb-0 theme-text-primary'>{children}</p>,
            ul: ({ children }) => (
              <ul className='list-disc list-inside mb-3 space-y-1 theme-text-primary'>
                {children}
              </ul>
            ),
            ol: ({ children }) => (
              <ol className='list-decimal list-inside mb-3 space-y-1 theme-text-primary'>
                {children}
              </ol>
            ),
            li: ({ children }) => <li className='theme-text-primary'>{children}</li>,
            blockquote: ({ children }) => (
              <blockquote className='border-l-4 border-primary-500 pl-4 my-3 italic theme-text-secondary'>
                {children}
              </blockquote>
            ),
            a: ({ href, children }) => (
              <a
                href={href}
                className='text-primary-400 hover:text-primary-300 underline'
                target='_blank'
                rel='noopener noreferrer'
              >
                {children}
              </a>
            ),
            strong: ({ children }) => (
              <strong className='font-bold theme-text-primary'>{children}</strong>
            ),
            em: ({ children }) => <em className='italic theme-text-primary'>{children}</em>,
            hr: () => <hr className='border-theme-border-primary my-4' />,
            table: ({ children }) => (
              <div className='overflow-x-auto my-3'>
                <table className='min-w-full border theme-border-primary'>{children}</table>
              </div>
            ),
            th: ({ children }) => (
              <th className='border theme-border-primary px-3 py-2 theme-bg-hover theme-text-primary'>
                {children}
              </th>
            ),
            td: ({ children }) => (
              <td className='border theme-border-primary px-3 py-2 theme-text-primary'>
                {children}
              </td>
            ),
          }}
        >
          {content}
        </ReactMarkdown>
      </div>
    </>
  )
}
