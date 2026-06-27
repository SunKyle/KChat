import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { CodeBlock } from './CodeBlock'
import { useState, memo } from 'react'
import { ZoomIn, X, Download } from 'lucide-react'

interface MarkdownRendererProps {
  content: string
}

export const MarkdownRenderer = memo(function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const [expandedImage, setExpandedImage] = useState<string | null>(null)

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
          className='fixed inset-0 z-50 theme-bg-card/95 backdrop-blur-md flex items-center justify-center p-4'
          onClick={() => setExpandedImage(null)}
        >
          <div className='absolute top-4 right-4 flex gap-2'>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleDownload(expandedImage)
              }}
              className='icon-btn'
              title='下载图片'
            >
              <Download className='w-6 h-6 theme-text-primary' />
            </button>
            <button
              className='icon-btn'
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
      <div className='markdown-body'>
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          components={{
            code({ node: _node, inline, className, children, ...props }) {
              const match = /language-(\w+)/.exec(className || '')
              return !inline && match ? (
                <CodeBlock code={String(children).replace(/\n$/, '')} language={match[1]} />
              ) : (
                <code
                  className='bg-[var(--bg-hover)] rounded-[var(--radius-sm)] px-1.5 py-0.5 text-sm font-mono theme-text-primary'
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
                  <img
                    src={src}
                    alt={alt || 'Generated image'}
                    className='max-w-full max-h-96 object-contain rounded-lg shadow-lg cursor-zoom-in opacity-100 hover:shadow-xl hover:shadow-sky-500/10 transition-shadow'
                    onClick={() => setExpandedImage(src)}
                  />
                  <div className='absolute bottom-2 right-2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity'>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        setExpandedImage(src)
                      }}
                      className='icon-btn-sm theme-bg-card/80 backdrop-blur-sm rounded-lg'
                      title='查看大图'
                    >
                      <ZoomIn className='w-3.5 h-3.5 theme-text-primary' />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDownload(src, filename)
                      }}
                      className='icon-btn-sm theme-bg-card/80 backdrop-blur-sm rounded-lg'
                      title='下载图片'
                    >
                      <Download className='w-3.5 h-3.5 theme-text-primary' />
                    </button>
                  </div>
                </div>
              )
            },
            h1: ({ children }) => (
              <h1 className='theme-text-primary'>{children}</h1>
            ),
            h2: ({ children }) => (
              <h2 className='theme-text-primary'>{children}</h2>
            ),
            h3: ({ children }) => (
              <h3 className='theme-text-primary'>{children}</h3>
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
              <blockquote className='border-l-4 border-primary-500 pl-4 my-3 italic theme-text-primary'>
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
              <strong className='font-semibold theme-text-primary'>{children}</strong>
            ),
            em: ({ children }) => <em className='italic theme-text-primary'>{children}</em>,
            hr: () => <hr className='border-theme-border-primary my-4' />,
            table: ({ children }) => (
              <div className='overflow-x-auto my-3 rounded-lg border border-[var(--border-divider)]'>
                <table className='min-w-full border-collapse text-sm'>{children}</table>
              </div>
            ),
            thead: ({ children }) => (
              <thead className='bg-[var(--bg-hover)]'>{children}</thead>
            ),
            tbody: ({ children }) => <tbody>{children}</tbody>,
            tr: ({ children }) => (
              <tr className='border-b border-[var(--border-divider)] last:border-b-0 even:bg-[var(--bg-hover)]/30'>
                {children}
              </tr>
            ),
            th: ({ children }) => (
              <th className='border-r border-[var(--border-divider)] last:border-r-0 px-4 py-2.5 text-left font-semibold theme-text-primary'>
                {children}
              </th>
            ),
            td: ({ children }) => (
              <td className='border-r border-[var(--border-divider)] last:border-r-0 px-4 py-2 theme-text-primary'>
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
})
