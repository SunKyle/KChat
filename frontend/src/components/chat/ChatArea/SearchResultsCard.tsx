import { useState } from 'react'
import { Globe, ChevronDown, ExternalLink, AlertCircle, SearchX } from 'lucide-react'
import type { WebSearchResultData } from '../../../types'

interface SearchResultsCardProps {
  results: WebSearchResultData | null
}

export function SearchResultsCard({ results }: SearchResultsCardProps) {
  const [expanded, setExpanded] = useState(true)

  if (!results) return null

  const { status, snippets, errorMessage } = results

  if (status === 'disabled') return null

  if (status === 'error') {
    return (
      <div className='mx-auto max-w-[85%] mb-3'>
        <div className='rounded-xl border border-[var(--accent-amber)]/20 bg-[var(--accent-amber)]/5 px-4 py-2.5 flex items-center gap-2'>
          <AlertCircle className='w-3.5 h-3.5 text-[var(--accent-amber)] flex-shrink-0' />
          <span className='text-xs text-[var(--accent-amber)]'>
            联网搜索失败{errorMessage ? `：${errorMessage}` : ''}
          </span>
        </div>
      </div>
    )
  }

  if (status === 'no_results' || snippets.length === 0) {
    return (
      <div className='mx-auto max-w-[85%] mb-3'>
        <div className='rounded-xl border border-[var(--border-primary)] bg-[var(--bg-hover)]/50 px-4 py-2.5 flex items-center gap-2'>
          <SearchX className='w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0' />
          <span className='text-xs text-[var(--text-secondary)]'>未找到相关搜索结果</span>
        </div>
      </div>
    )
  }

  return (
    <div className='mx-auto max-w-[85%] mb-3'>
      <div className='rounded-xl border border-[var(--accent-primary)]/20 bg-[var(--accent-primary)]/5 overflow-hidden'>
        <button
          onClick={() => setExpanded(!expanded)}
          className='w-full flex items-center gap-2 px-4 py-2.5 hover:bg-[var(--accent-primary)]/10 transition-colors'
        >
          <Globe className='w-3.5 h-3.5 text-[var(--accent-primary)]' />
          <span className='text-xs font-medium text-[var(--accent-primary)]'>
            搜索到 {snippets.length} 个相关网页
          </span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-[var(--accent-primary)]/60 ml-auto transition-transform duration-200 ${
              expanded ? 'rotate-180' : ''
            }`}
          />
        </button>
        {expanded && (
          <div className='px-4 pb-3 space-y-2'>
            {snippets.map((item, i) => (
              <a
                key={i}
                href={item.url}
                target='_blank'
                rel='noopener noreferrer'
                className='block p-2.5 rounded-lg bg-[var(--bg-card)]/70 hover:bg-[var(--bg-card)] border border-[var(--border-secondary)] hover:border-[var(--accent-primary)]/30 transition-all group'
              >
                <div className='flex items-start gap-2'>
                  <div className='flex-1 min-w-0'>
                    <div className='flex items-center gap-1.5'>
                      <span className='text-xs font-medium text-[var(--text-primary)] truncate'>
                        {item.title || '无标题'}
                      </span>
                      <ExternalLink className='w-3 h-3 text-[var(--text-muted)] group-hover:text-[var(--accent-primary)] flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity' />
                    </div>
                    <p className='text-xs text-[var(--text-secondary)] mt-0.5 line-clamp-2'>
                      {item.snippet}
                    </p>
                    <p className='text-[10px] text-[var(--text-muted)] mt-1 truncate'>
                      {item.url}
                    </p>
                  </div>
                </div>
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
