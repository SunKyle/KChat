import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Database, FileText, Loader2 } from 'lucide-react'
import { knowledgeBaseApi, type KnowledgeBase } from '../../../api/knowledge'

const DEFAULT_USER_ID = 'default'

interface KnowledgeBasePickerProps {
  open: boolean
  query: string
  /** 已引用的知识库 id，选择器中将其过滤掉，避免重复引用 */
  excludeIds?: string[]
  onQueryChange: (query: string) => void
  onSelect: (kb: KnowledgeBase) => void
  onClose: () => void
}

export function KnowledgeBasePicker({
  open,
  query,
  excludeIds = [],
  onSelect,
  onClose,
}: KnowledgeBasePickerProps) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // 打开时加载知识库列表
  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoading(true)
    knowledgeBaseApi
      .list(DEFAULT_USER_ID)
      .then((list) => {
        if (!cancelled) setKnowledgeBases(list)
      })
      .catch(() => {
        if (!cancelled) setKnowledgeBases([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open])

  // Esc 关闭
  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  // 点击外部关闭
  useEffect(() => {
    if (!open) return
    const onMouseDown = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [open, onClose])

  const excludeSet = new Set(excludeIds)
  const trimmed = query.trim().toLocaleLowerCase()
  const filtered = (trimmed
    ? knowledgeBases.filter((kb) => kb.name.toLocaleLowerCase().includes(trimmed))
    : knowledgeBases
  ).filter((kb) => !excludeSet.has(kb.id))

  return (
    <motion.div
      ref={containerRef}
      initial={{ opacity: 0, y: -8, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.98 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
      role='listbox'
      aria-label='知识库引用选择器'
      className='relative z-30 mx-4 lg:mx-6 mb-1 overflow-hidden rounded-2xl theme-bg-elevated border theme-border-primary shadow-2xl'
    >
      <div className='px-3 pt-2.5 pb-2'>
        <div className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted'>
          <Database className='w-3.5 h-3.5' />
          引用知识库
        </div>
      </div>
      <div className='max-h-72 overflow-y-auto px-1.5 pb-1.5'>
        {loading ? (
          <div className='flex items-center justify-center py-8'>
            <Loader2 className='w-5 h-5 animate-spin theme-text-muted' />
          </div>
        ) : knowledgeBases.length === 0 ? (
          <div className='px-3 py-8 text-center'>
            <Database className='w-8 h-8 theme-text-muted mx-auto mb-2' />
            <p className='text-sm theme-text-secondary'>暂无知识库，请先在侧边栏创建</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className='px-3 py-6 text-center text-sm theme-text-muted'>
            未找到匹配的知识库
          </div>
        ) : (
          <ul>
            {filtered.map((kb) => (
              <li key={kb.id}>
                <button
                  type='button'
                  role='option'
                  aria-selected='false'
                  onClick={() => onSelect(kb)}
                  className='w-full flex items-center gap-2.5 px-2 py-2 rounded-lg text-left hover:theme-bg-hover transition-colors duration-150'
                >
                  <div className='w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 theme-bg-hover'>
                    <Database className='w-4 h-4 theme-text-muted' />
                  </div>
                  <div className='flex-1 min-w-0'>
                    <p className='text-sm theme-text-primary truncate'>{kb.name}</p>
                    <p className='text-xs theme-text-muted flex items-center gap-1'>
                      <FileText className='w-3 h-3' />
                      {kb.documentCount} 篇文档
                    </p>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </motion.div>
  )
}

export default KnowledgeBasePicker