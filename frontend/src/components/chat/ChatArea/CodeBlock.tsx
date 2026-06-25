import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'
import { useState } from 'react'
import { Copy, Check } from 'lucide-react'
import { useTheme } from '../../../context/ThemeContext'

interface CodeBlockProps {
  code: string
  language?: string
}

const LANGUAGE_LABELS: Record<string, string> = {
  typescript: 'TypeScript',
  javascript: 'JavaScript',
  jsx: 'JSX',
  tsx: 'TSX',
  python: 'Python',
  java: 'Java',
  go: 'Go',
  rust: 'Rust',
  cpp: 'C++',
  css: 'CSS',
  html: 'HTML',
  json: 'JSON',
  yaml: 'YAML',
  markdown: 'Markdown',
  sql: 'SQL',
  shell: 'Shell',
  bash: 'Bash',
  text: 'Text',
}

export function CodeBlock({ code, language = 'text' }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)
  const { theme } = useTheme()
  const isDark = theme === 'dark'
  const displayLanguage = LANGUAGE_LABELS[language] || language

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
    <div
      className='my-3 rounded-xl overflow-hidden'
      style={{
        backgroundColor: 'var(--bg-code)',
        border: '1px solid var(--border-primary)',
      }}
    >
      <div
        className='flex items-center justify-between px-4 py-2.5'
        style={{ backgroundColor: 'var(--bg-code)' }}
      >
        <span className='text-xs font-semibold theme-text-muted'>{displayLanguage}</span>
        <button
          onClick={handleCopy}
          className='flex items-center gap-1.5 text-xs theme-text-muted hover:theme-text-primary transition-colors'
        >
          {copied ? (
            <>
              <Check className='w-3.5 h-3.5' style={{ color: 'var(--accent-emerald)' }} />
              <span style={{ color: 'var(--accent-emerald)' }}>已复制</span>
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
          background: 'var(--bg-code)',
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
