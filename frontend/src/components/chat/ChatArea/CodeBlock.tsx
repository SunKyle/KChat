import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { useState } from 'react'
import { Copy, Check } from 'lucide-react'
import { useTheme } from '../../../context/ThemeContext'

interface CodeBlockProps {
  code: string
  language?: string
}

export function CodeBlock({ code, language = 'text' }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)
  const { theme } = useTheme()
  const isDark = theme === 'dark'

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
    <div className={`my-3 rounded-xl overflow-hidden border ${isDark ? 'border-white/[0.06]' : 'border-[var(--border-primary)]'}`}>
      <div className={`flex items-center justify-between px-4 py-2.5 border-b ${isDark ? 'bg-white/[0.03] border-white/[0.06]' : 'bg-black/[0.03] border-[var(--border-divider)]'}`}>
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
        style={isDark ? oneDark : oneLight}
        customStyle={{
          margin: 0,
          borderRadius: '0 0 0.75rem 0.75rem',
          fontSize: 'var(--font-code)',
          background: isDark ? '#111418' : 'var(--bg-code)',
        }}
        showLineNumbers={true}
        wrapLines={true}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  )
}
