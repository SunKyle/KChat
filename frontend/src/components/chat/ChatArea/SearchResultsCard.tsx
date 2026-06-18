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
        <div className='rounded-xl border border-amber-200/60 bg-amber-50/50 px-4 py-2.5 flex items-center gap-2'>
          <AlertCircle className='w-3.5 h-3.5 text-amber-500 flex-shrink-0' />
          <span className='text-xs text-amber-700'>
            联网搜索失败{errorMessage ? `：${errorMessage}` : ''}
          </span>
        </div>
      </div>
    )
  }

  if (status === 'no_results' || snippets.length === 0) {
    return (
      <div className='mx-auto max-w-[85%] mb-3'>
        <div className='rounded-xl border border-gray-200/60 bg-gray-50/50 px-4 py-2.5 flex items-center gap-2'>
          <SearchX className='w-3.5 h-3.5 text-gray-400 flex-shrink-0' />
          <span className='text-xs text-gray-500'>未找到相关搜索结果</span>
        </div>
      </div>
    )
  }

  return (
    <div className='mx-auto max-w-[85%] mb-3'>
      <div className='rounded-xl border border-sky-200/60 bg-sky-50/50 overflow-hidden'>
        <button
          onClick={() => setExpanded(!expanded)}
          className='w-full flex items-center gap-2 px-4 py-2.5 hover:bg-sky-100/50 transition-colors'
        >
          <Globe className='w-3.5 h-3.5 text-sky-500' />
          <span className='text-xs font-medium text-sky-700'>
            搜索到 {snippets.length} 个相关网页
          </span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-sky-400 ml-auto transition-transform duration-200 ${
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
                className='block p-2.5 rounded-lg bg-white/70 hover:bg-white border border-transparent hover:border-sky-200/50 transition-all group'
              >
                <div className='flex items-start gap-2'>
                  <div className='flex-1 min-w-0'>
                    <div className='flex items-center gap-1.5'>
                      <span className='text-xs font-medium text-gray-800 truncate'>
                        {item.title || '无标题'}
                      </span>
                      <ExternalLink className='w-3 h-3 text-gray-300 group-hover:text-sky-400 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity' />
                    </div>
                    <p className='text-xs text-gray-500 mt-0.5 line-clamp-2'>
                      {item.snippet}
                    </p>
                    <p className='text-[10px] text-gray-400 mt-1 truncate'>
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
