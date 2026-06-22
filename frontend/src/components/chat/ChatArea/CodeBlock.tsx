import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { useState } from 'react'
import { Copy, Check } from 'lucide-react'

interface CodeBlockProps {
  code: string
  language?: string
}

export function CodeBlock({ code, language = 'text' }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('复制失败:', err)
    }
  }

  return (
    <div className='my-3 rounded-xl overflow-hidden border border-[var(--border-primary)]'>
      <div className='flex items-center justify-between px-4 py-2.5 bg-[#21252b] border-b border-white/[0.08]'>
        <span className='text-xs font-semibold text-[var(--text-muted)]'>{language}</span>
        <button
          onClick={handleCopy}
          className='flex items-center gap-1.5 text-xs text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
        >
          {copied ? (
            <>
              <Check className='w-3.5 h-3.5 text-[var(--accent-emerald)]' />
              <span className='text-[var(--accent-emerald)]'>已复制</span>
            </>
          ) : (
            <>
              <Copy className='w-3.5 h-3.5' />
              <span>复制</span>
            </>
          )}
        </button>
      </div>
      <SyntaxHighlighter
        language={language}
        style={oneDark}
        customStyle={{
          margin: 0,
          borderRadius: 0,
          fontSize: 'var(--font-code)',
          background: '#282c34',
        }}
        showLineNumbers={true}
        wrapLines={true}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  )
}
