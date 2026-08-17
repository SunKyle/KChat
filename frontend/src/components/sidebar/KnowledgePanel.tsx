import { useState, useEffect, useCallback } from 'react'
import {
  ChevronRight,
  Database,
  Plus,
  Loader2,
  Trash2,
  RefreshCw,
  FileText,
} from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { knowledgeBaseApi, type KnowledgeBase } from '../../api/knowledge'

const DEFAULT_USER_ID = 'default'

interface KnowledgePanelProps {
  onToggle: () => void
  /** 点击知识库 → 在主区域展示提取信息 */
  onSelectKnowledgeBase?: (kb: KnowledgeBase) => void
}

export function KnowledgePanel({ onToggle, onSelectKnowledgeBase }: KnowledgePanelProps) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newKbName, setNewKbName] = useState('')
  const [newKbDesc, setNewKbDesc] = useState('')
  const [creating, setCreating] = useState(false)

  const loadAll = useCallback(async () => {
    try {
      setLoading(true)
      const kbList = await knowledgeBaseApi
        .list(DEFAULT_USER_ID)
        .catch(() => [] as KnowledgeBase[])
      setKnowledgeBases(kbList)
    } catch (e) {
      console.error('Failed to load knowledge bases:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const handleCreate = async () => {
    const name = newKbName.trim()
    if (!name || creating) return
    setCreating(true)
    try {
      await knowledgeBaseApi.create(DEFAULT_USER_ID, {
        name,
        description: newKbDesc.trim() || undefined,
      })
      setNewKbName('')
      setNewKbDesc('')
      setShowCreateModal(false)
      loadAll()
    } catch (e) {
      console.error('Failed to create KB:', e)
    } finally {
      setCreating(false)
    }
  }

  const handleDeleteKb = async (kbId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm('确定删除此知识库？所有文档和图谱数据将被清除。')) return
    try {
      await knowledgeBaseApi.delete(DEFAULT_USER_ID, kbId)
      loadAll()
    } catch (e) {
      console.error('Failed to delete KB:', e)
    }
  }

  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>知识库</h2>
        <div className='flex items-center gap-1'>
          <button
            onClick={loadAll}
            aria-label='刷新'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200'
          >
            <RefreshCw className='w-4 h-4' />
          </button>
          <button
            onClick={() => setShowCreateModal(true)}
            aria-label='新建知识库'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200'
          >
            <Plus className='w-4 h-4' />
          </button>
          <button
            onClick={onToggle}
            aria-label='收起侧边栏'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 flex-shrink-0'
          >
            <ChevronRight className='w-4 h-4 rotate-180' />
          </button>
        </div>
      </div>

      {/* 列表内容 */}
      <div className='flex-1 min-h-0 overflow-y-auto px-3 pb-3'>
        {loading ? (
          <div className='flex items-center justify-center h-full'>
            <Loader2 className='w-5 h-5 animate-spin theme-text-muted' />
          </div>
        ) : knowledgeBases.length === 0 ? (
          <div className='flex flex-col items-center justify-center h-full text-center px-4'>
            <Database className='w-10 h-10 theme-text-muted mb-3' />
            <p className='text-sm theme-text-secondary mb-1 font-semibold'>暂无知识库</p>
            <p className='text-xs theme-text-muted mb-4'>创建知识库并上传文档</p>
            <button
              onClick={() => setShowCreateModal(true)}
              className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs bg-[var(--brand-primary)] text-white hover:opacity-90 transition-opacity'
            >
              <Plus className='w-3.5 h-3.5' />
              新建知识库
            </button>
          </div>
        ) : (
          <div className='space-y-1.5'>
            {knowledgeBases.map((kb) => (
              <KnowledgeBaseItem
                key={kb.id}
                kb={kb}
                onSelect={() => onSelectKnowledgeBase?.(kb)}
                onDelete={(e) => handleDeleteKb(kb.id, e)}
              />
            ))}
          </div>
        )}
      </div>

      {/* 创建知识库弹窗 */}
      <AnimatePresence>
        {showCreateModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className='fixed inset-0 z-[60] flex items-center justify-center bg-black/50 backdrop-blur-md p-4'
            onClick={() => !creating && setShowCreateModal(false)}
          >
            <motion.div
              initial={{ scale: 0.94, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.94, opacity: 0 }}
              transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
              className='relative w-[320px] max-w-full rounded-2xl theme-bg-elevated shadow-2xl border theme-border-primary p-5'
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className='text-sm font-semibold theme-text-primary mb-3.5'>新建知识库</h3>
              <input
                type='text'
                placeholder='名称'
                value={newKbName}
                onChange={(e) => setNewKbName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') handleCreate()
                }}
                autoFocus
                maxLength={50}
                className='w-full px-3 py-2 rounded-lg theme-bg-input theme-text-primary text-sm border theme-border-primary focus:outline-none focus:border-[var(--brand-primary)]/50 placeholder:theme-text-muted transition-all mb-2'
              />
              <textarea
                placeholder='描述（可选）'
                value={newKbDesc}
                onChange={(e) => setNewKbDesc(e.target.value.slice(0, 200))}
                rows={2}
                className='w-full px-3 py-2 rounded-lg theme-bg-input theme-text-primary text-sm border theme-border-primary focus:outline-none focus:border-[var(--brand-primary)]/50 placeholder:theme-text-muted resize-none transition-all mb-4'
              />
              <div className='flex gap-2'>
                <button
                  onClick={() => setShowCreateModal(false)}
                  disabled={creating}
                  className='flex-1 py-2 rounded-lg text-sm theme-bg-hover theme-text-secondary hover:opacity-80 transition-opacity disabled:opacity-50'
                >
                  取消
                </button>
                <button
                  onClick={handleCreate}
                  disabled={!newKbName.trim() || creating}
                  className='flex-1 py-2 rounded-lg text-sm font-medium text-white bg-[var(--brand-primary)] hover:opacity-90 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center'
                >
                  {creating ? (
                    <>
                      <Loader2 className='w-3.5 h-3.5 animate-spin mr-1.5' />
                      创建中
                    </>
                  ) : (
                    '创建'
                  )}
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

/** 知识库列表项 */
function KnowledgeBaseItem({
  kb,
  onSelect,
  onDelete,
}: {
  kb: KnowledgeBase
  onSelect: () => void
  onDelete: (e: React.MouseEvent) => void
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      className='group flex items-center gap-2.5 p-2.5 rounded-xl hover:theme-bg-hover cursor-pointer transition-all duration-200'
      onClick={onSelect}
    >
      <div className='w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 theme-bg-hover'>
        <Database className='w-4 h-4 theme-text-muted' />
      </div>
      <div className='flex-1 min-w-0'>
        <p className='text-sm theme-text-primary truncate font-medium'>{kb.name}</p>
        <p className='text-xs theme-text-muted flex items-center gap-1'>
          <FileText className='w-3 h-3' />
          {kb.documentCount} 篇文档
          {kb.description && <span className='ml-1.5 truncate'>· {kb.description}</span>}
        </p>
      </div>
      <button
        onClick={onDelete}
        aria-label='删除知识库'
        className='p-1 rounded-lg opacity-0 group-hover:opacity-100 hover:bg-red-500/10 text-red-500 transition-all duration-200 flex-shrink-0'
      >
        <Trash2 className='w-3.5 h-3.5' />
      </button>
    </motion.div>
  )
}

export default KnowledgePanel
