import { useState } from 'react'
import { Icon } from '../common/Icon'

interface TruncatableTextProps {
  text: string
  /** 折叠态显示的最大字符数，默认 200 */
  maxChars?: number
  /** 渲染变体：text 用段落样式，code 用等宽 + break-all */
  variant?: 'text' | 'code'
  /** 是否显示总字符数提示，默认 true */
  showCount?: boolean
}

/**
 * 可截断的长文本展示组件。
 * 超过 maxChars 时默认折叠，展示前 N 字符 + 总字符数提示 + 展开/收起按钮。
 */
export function TruncatableText({
  text,
  maxChars = 200,
  variant = 'text',
  showCount = true,
}: TruncatableTextProps) {
  const [expanded, setExpanded] = useState(false)

  if (!text) return null

  const totalChars = text.length
  const needTruncate = totalChars > maxChars
  const displayText = expanded || !needTruncate ? text : text.slice(0, maxChars)

  const isCode = variant === 'code'
  const contentClassName = isCode
    ? 'text-xs text-[var(--text-secondary)] whitespace-pre-wrap break-all font-mono m-0 leading-relaxed'
    : 'text-xs text-[var(--text-secondary)] whitespace-pre-wrap leading-relaxed m-0'

  return (
    <div>
      {isCode ? (
        <pre className={contentClassName}>{displayText}</pre>
      ) : (
        <p className={contentClassName}>{displayText}</p>
      )}
      {needTruncate && (
        <div className='flex items-center gap-2 mt-1.5'>
          {showCount && (
            <span className='text-xs text-[var(--text-muted)]'>
              共 {totalChars} 字符
            </span>
          )}
          <button
            onClick={() => setExpanded(!expanded)}
            className='inline-flex items-center gap-0.5 text-xs font-medium text-[var(--accent-purple)] hover:text-[var(--accent-purple)]/80 transition-colors'
          >
            <Icon
              name='ChevronRight'
              size='xs'
              className={`transition-transform duration-200 ${
                expanded ? 'rotate-90' : ''
              }`}
            />
            {expanded ? '收起' : '展开全部'}
          </button>
        </div>
      )}
    </div>
  )
}
