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

  const codeBgColor = isDark ? '#1e1e1e' : '#f8f9fa'
  const borderColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)'

  return (
    <div
      className='my-3 rounded-xl overflow-hidden'
      style={{
        backgroundColor: codeBgColor,
        border: `1px solid ${borderColor}`,
      }}
    >
      <div
        className='flex items-center justify-between px-4 py-2.5'
        style={{ backgroundColor: codeBgColor }}
      >
        <span className='text-xs font-semibold' style={{ color: isDark ? '#858585' : '#6b7280' }}>
          {language}
        </span>
        <button
          onClick={handleCopy}
          className='flex items-center gap-1.5 text-xs transition-colors'
          style={{ color: isDark ? '#858585' : '#6b7280' }}
          onMouseEnter={(e) => (e.currentTarget.style.color = isDark ? '#e7e9ea' : '#0f1419')}
          onMouseLeave={(e) => (e.currentTarget.style.color = isDark ? '#858585' : '#6b7280')}
        >
          {copied ? (
            <>
              <Check className='w-3.5 h-3.5' style={{ color: '#00b87a' }} />
              <span style={{ color: '#00b87a' }}>已复制</span>
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
          background: codeBgColor,
          padding: '16px',
          border: 'none',
        }}
        showLineNumbers={true}
        wrapLines={true}
      >
        {code}
      </SyntaxHighlighter>
    </div>
  )
}
