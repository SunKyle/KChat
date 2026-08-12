import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { CodeBlock } from './CodeBlock'
import { MermaidBlock } from '../../ui/MermaidBlock'
import { memo } from 'react'
import { Image as UIImage } from '../../ui/Image'

interface MarkdownRendererProps {
  content: string
}

export const MarkdownRenderer = memo(function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className='markdown-body'>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ node: _node, inline, className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || '')
            if (!inline && match && match[1].toLowerCase() === 'mermaid') {
              return <MermaidBlock chart={String(children).replace(/\n$/, '')} />
            }
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
            if (!src || typeof src !== 'string') return null
            return (
              <UIImage src={src} alt={alt || 'Image'} maxHeightClass='max-h-96' className='my-4' />
            )
          },
          h1: ({ children }) => <h1 className='theme-text-primary'>{children}</h1>,
          h2: ({ children }) => <h2 className='theme-text-primary'>{children}</h2>,
          h3: ({ children }) => <h3 className='theme-text-primary'>{children}</h3>,
          p: ({ children }) => <p className='mb-3 last:mb-0 theme-text-primary'>{children}</p>,
          ul: ({ children }) => (
            <ul className='list-disc list-inside mb-3 space-y-1 theme-text-primary'>{children}</ul>
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
          thead: ({ children }) => <thead className='bg-[var(--bg-hover)]'>{children}</thead>,
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
  )
})
