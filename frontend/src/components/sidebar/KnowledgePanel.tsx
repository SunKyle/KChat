import { useState, useEffect, useRef, useCallback } from 'react'
import {
  ChevronRight,
  Database,
  Plus,
  ArrowLeft,
  Upload,
  FileText,
  Trash2,
  Loader2,
  CheckCircle2,
  XCircle,
  AlertCircle,
  Download,
  Share2,
  Brain,
  RefreshCw,
} from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { knowledgeBaseApi, type KnowledgeBase, type KnowledgeDocument } from '../../api/knowledge'
import { cogneeMemory, type DatasetInfo } from '../../api/cognee'

const DEFAULT_USER_ID = 'default'

/** 合并后的数据集条目 */
interface DatasetEntry {
  /** Cognee dataset name，用于图谱查询 */
  datasetName: string
  /** 显示名称 */
  displayName: string
  /** 是否为用户创建的知识库 */
  isKnowledgeBase: boolean
  /** 关联的知识库实体（仅 isKnowledgeBase=true 时有值） */
  kb?: KnowledgeBase
  /** 文档数量（KB 从 JPA，非 KB 从 Cognee data_count） */
  count: number
  /** 描述 */
  description?: string
}

interface KnowledgePanelProps {
  onToggle: () => void
  onSelectDataset?: (datasetName: string, displayName: string) => void
}

export function KnowledgePanel({ onToggle, onSelectDataset }: KnowledgePanelProps) {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])
  const [cogneeDatasets, setCogneeDatasets] = useState<DatasetInfo[]>([])
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null)
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newKbName, setNewKbName] = useState('')
  const [newKbDesc, setNewKbDesc] = useState('')
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')

  /** 加载所有数据：知识库列表 + Cognee dataset 列表 */
  const loadAll = useCallback(async () => {
    try {
      setLoading(true)
      const [kbList, dsList] = await Promise.all([
        knowledgeBaseApi.list(DEFAULT_USER_ID).catch(() => [] as KnowledgeBase[]),
        cogneeMemory.listDatasets().catch(() => [] as DatasetInfo[]),
      ])
      setKnowledgeBases(kbList)
      setCogneeDatasets(dsList)
    } catch (e) {
      console.error('Failed to load datasets:', e)
      setError('加载数据集列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  /** 合并知识库 + Cognee dataset 为统一列表 */
  const mergedDatasets: DatasetEntry[] = (() => {
    const entries: DatasetEntry[] = []
    // 建索引：datasetName → KnowledgeBase
    const kbByDataset = new Map<string, KnowledgeBase>()
    for (const kb of knowledgeBases) {
      kbByDataset.set(kb.datasetName, kb)
    }

    // 先放 Cognee datasets
    for (const ds of cogneeDatasets) {
      const kb = kbByDataset.get(ds.name)
      if (kb) {
        entries.push({
          datasetName: ds.name,
          displayName: kb.name,
          isKnowledgeBase: true,
          kb,
          count: kb.documentCount,
          description: kb.description || undefined,
        })
      } else {
        // 非知识库的 dataset（如 main_dataset）
        const isMain = ds.name === 'main_dataset'
        entries.push({
          datasetName: ds.name,
          displayName: isMain ? '对话记忆' : ds.name,
          isKnowledgeBase: false,
          count: ds.data_count,
          description: isMain ? '系统自动生成的对话历史图谱' : undefined,
        })
      }
    }

    // 还有没有对应 Cognee dataset 的 KB（刚创建还没上传文档）
    const dsNames = new Set(cogneeDatasets.map((d) => d.name))
    for (const kb of knowledgeBases) {
      if (!dsNames.has(kb.datasetName)) {
        entries.push({
          datasetName: kb.datasetName,
          displayName: kb.name,
          isKnowledgeBase: true,
          kb,
          count: kb.documentCount,
          description: kb.description || undefined,
        })
      }
    }

    // 排序：main_dataset 放最后，知识库按名称排序
    entries.sort((a, b) => {
      if (a.datasetName === 'main_dataset') return 1
      if (b.datasetName === 'main_dataset') return -1
      return a.displayName.localeCompare(b.displayName, 'zh')
    })

    return entries
  })()

  /** 加载文档列表 */
  const loadDocuments = useCallback(async (kbId: string) => {
    try {
      const docs = await knowledgeBaseApi.listDocuments(DEFAULT_USER_ID, kbId)
      setDocuments(docs)
    } catch (e) {
      console.error('Failed to load documents:', e)
    }
  }, [])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  /** 轮询处理中的文档状态 */
  useEffect(() => {
    const hasProcessing = documents.some(
      (d) => d.status === 'PROCESSING' || d.status === 'PENDING'
    )
    if (!hasProcessing || !selectedKb) return

    const interval = setInterval(async () => {
      await loadDocuments(selectedKb.id)
    }, 3000)

    return () => clearInterval(interval)
  }, [documents, selectedKb, loadDocuments])

  /** 点击数据集 → 在主区域展示图谱 */
  const handleSelectDataset = (entry: DatasetEntry) => {
    onSelectDataset?.(entry.datasetName, entry.displayName)
  }

  /** 进入文档管理视图（仅知识库） */
  const handleManageDocs = async (kb: KnowledgeBase, e: React.MouseEvent) => {
    e.stopPropagation()
    setSelectedKb(kb)
    await loadDocuments(kb.id)
  }

  /** 返回列表 */
  const handleBack = () => {
    setSelectedKb(null)
    setDocuments([])
    loadAll()
  }

  /** 创建知识库 */
  const handleCreate = async () => {
    if (!newKbName.trim()) return
    try {
      await knowledgeBaseApi.create(DEFAULT_USER_ID, {
        name: newKbName.trim(),
        description: newKbDesc.trim() || undefined,
      })
      setNewKbName('')
      setNewKbDesc('')
      setShowCreateModal(false)
      loadAll()
    } catch (e) {
      console.error('Failed to create KB:', e)
      setError('创建知识库失败')
    }
  }

  /** 删除知识库 */
  const handleDeleteKb = async (kbId: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm('确定删除此知识库？所有文档和图谱数据将被清除。')) return
    try {
      await knowledgeBaseApi.delete(DEFAULT_USER_ID, kbId)
      if (selectedKb?.id === kbId) {
        setSelectedKb(null)
        setDocuments([])
      }
      loadAll()
    } catch (e) {
      console.error('Failed to delete KB:', e)
    }
  }

  /** 刷新所有数据集 */
  const handleRefresh = () => {
    loadAll()
  }

  /** 上传文档 */
  const handleUpload = async (files: FileList) => {
    if (!selectedKb || files.length === 0) return
    setUploading(true)
    setError('')
    try {
      for (const file of Array.from(files)) {
        await knowledgeBaseApi.uploadDocument(DEFAULT_USER_ID, selectedKb.id, file)
      }
      await loadDocuments(selectedKb.id)
      loadAll()
    } catch (e) {
      console.error('Upload failed:', e)
      setError('文档上传失败')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  /** 删除文档 */
  const handleDeleteDoc = async (docId: string) => {
    if (!selectedKb) return
    if (!confirm('确定删除此文档？')) return
    try {
      await knowledgeBaseApi.deleteDocument(DEFAULT_USER_ID, selectedKb.id, docId)
      loadDocuments(selectedKb.id)
      loadAll()
    } catch (e) {
      console.error('Failed to delete document:', e)
    }
  }

  // ── 知识库详情视图 ──────────────────────────────────
  if (selectedKb) {
    return (
      <div className='flex flex-col h-full'>
        {/* 标题栏 */}
        <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
          <div className='flex items-center gap-2 min-w-0'>
            <button
              onClick={handleBack}
              aria-label='返回列表'
              className='p-1 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 flex-shrink-0'
            >
              <ArrowLeft className='w-4 h-4' />
            </button>
            <h2 className='font-group-title theme-text-primary truncate'>{selectedKb.name}</h2>
          </div>
          <button
            onClick={onToggle}
            aria-label='收起侧边栏'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 flex-shrink-0'
          >
            <ChevronRight className='w-4 h-4 rotate-180' />
          </button>
        </div>

        {/* 描述 */}
        {selectedKb.description && (
          <div className='px-4 pb-2'>
            <p className='text-xs theme-text-muted line-clamp-2'>{selectedKb.description}</p>
          </div>
        )}

        {/* 上传区域 */}
        <div className='px-3 pb-3 flex-shrink-0'>
          <input
            ref={fileInputRef}
            type='file'
            multiple
            className='hidden'
            onChange={(e) => e.target.files && handleUpload(e.target.files)}
            accept='.pdf,.doc,.docx,.txt,.md,.html,.htm,.xls,.xlsx,.ppt,.pptx'
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className='w-full flex items-center justify-center gap-2 py-2.5 rounded-xl border border-dashed theme-border hover:theme-bg-hover transition-all duration-200 text-sm theme-text-secondary disabled:opacity-50'
          >
            {uploading ? (
              <>
                <Loader2 className='w-4 h-4 animate-spin' />
                上传中...
              </>
            ) : (
              <>
                <Upload className='w-4 h-4' />
                上传文档
              </>
            )}
          </button>
        </div>

        {/* 错误提示 */}
        {error && (
          <div className='px-4 pb-2'>
            <p className='text-xs text-red-500 flex items-center gap-1'>
              <AlertCircle className='w-3 h-3' />
              {error}
            </p>
          </div>
        )}

        {/* 文档列表 */}
        <div className='flex-1 min-h-0 overflow-y-auto px-3 pb-3'>
          {documents.length === 0 ? (
            <div className='flex flex-col items-center justify-center h-full text-center px-4'>
              <FileText className='w-8 h-8 theme-text-muted mb-2' />
              <p className='text-sm theme-text-muted'>暂无文档</p>
              <p className='text-xs theme-text-muted mt-1'>上传文档后即可检索</p>
            </div>
          ) : (
            <div className='space-y-1.5'>
              {documents.map((doc) => (
                <DocumentItem
                  key={doc.id}
                  doc={doc}
                  onDelete={() => handleDeleteDoc(doc.id)}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    )
  }

  // ── 知识库列表视图 ──────────────────────────────────
  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>知识库</h2>
        <div className='flex items-center gap-1'>
          <button
            onClick={handleRefresh}
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
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200'
          >
            <ChevronRight className='w-4 h-4 rotate-180' />
          </button>
        </div>
      </div>

      {/* 列表内容 */}
      <div className='flex-1 min-h-0 overflow-y-auto px-3 pb-3'>
        {!loading && mergedDatasets.length > 0 && (
          <p className='text-xs theme-text-muted px-1 pb-2'>
            点击数据集查看图谱
          </p>
        )}
        {loading ? (
          <div className='flex items-center justify-center h-full'>
            <Loader2 className='w-5 h-5 animate-spin theme-text-muted' />
          </div>
        ) : mergedDatasets.length === 0 ? (
          <div className='flex flex-col items-center justify-center h-full text-center px-4'>
            <Database className='w-10 h-10 theme-text-muted mb-3' />
            <p className='text-sm theme-text-secondary mb-1 font-semibold'>暂无数据集</p>
            <p className='text-xs theme-text-muted mb-4'>创建知识库并上传文档</p>
            <button
              onClick={() => setShowCreateModal(true)}
              className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs theme-bg-primary text-white hover:opacity-90 transition-opacity'
            >
              <Plus className='w-3.5 h-3.5' />
              新建知识库
            </button>
          </div>
        ) : (
          <div className='space-y-1.5'>
            {mergedDatasets.map((entry) => {
              const kb = entry.kb
              return (
                <DatasetItem
                  key={entry.datasetName}
                  entry={entry}
                  onSelect={() => handleSelectDataset(entry)}
                  onManageDocs={kb ? (e) => handleManageDocs(kb, e) : undefined}
                  onDelete={kb ? (e) => handleDeleteKb(kb.id, e) : undefined}
                />
              )
            })}
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
            className='absolute inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm'
            onClick={() => setShowCreateModal(false)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className='w-[280px] rounded-2xl theme-bg-elevated p-4 shadow-xl'
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className='text-sm font-semibold theme-text-primary mb-3'>新建知识库</h3>
              <input
                type='text'
                placeholder='知识库名称'
                value={newKbName}
                onChange={(e) => setNewKbName(e.target.value)}
                autoFocus
                className='w-full px-3 py-2 rounded-lg theme-bg-input theme-text-primary text-sm border theme-border focus:outline-none mb-2'
              />
              <textarea
                placeholder='描述（可选）'
                value={newKbDesc}
                onChange={(e) => setNewKbDesc(e.target.value)}
                rows={2}
                className='w-full px-3 py-2 rounded-lg theme-bg-input theme-text-primary text-sm border theme-border focus:outline-none mb-3 resize-none'
              />
              <div className='flex gap-2'>
                <button
                  onClick={() => setShowCreateModal(false)}
                  className='flex-1 py-2 rounded-lg text-sm theme-bg-hover theme-text-secondary hover:opacity-80 transition-opacity'
                >
                  取消
                </button>
                <button
                  onClick={handleCreate}
                  disabled={!newKbName.trim()}
                  className='flex-1 py-2 rounded-lg text-sm theme-bg-primary text-white hover:opacity-90 transition-opacity disabled:opacity-50'
                >
                  创建
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

/** 数据集列表项 */
function DatasetItem({
  entry,
  onSelect,
  onManageDocs,
  onDelete,
}: {
  entry: DatasetEntry
  onSelect: () => void
  onManageDocs?: (e: React.MouseEvent) => void
  onDelete?: (e: React.MouseEvent) => void
}) {
  const isMain = entry.datasetName === 'main_dataset'

  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      className='group flex items-center gap-2.5 p-2.5 rounded-xl hover:theme-bg-hover cursor-pointer transition-all duration-200'
      onClick={onSelect}
    >
      <div
        className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
          isMain
            ? 'bg-gradient-to-br from-purple-500/10 to-blue-500/10'
            : 'theme-bg-hover'
        }`}
      >
        {isMain ? (
          <Brain className='w-4 h-4 text-purple-400' />
        ) : entry.isKnowledgeBase ? (
          <Database className='w-4 h-4 theme-text-muted' />
        ) : (
          <Database className='w-4 h-4 theme-text-muted' />
        )}
      </div>
      <div className='flex-1 min-w-0'>
        <p className='text-sm theme-text-primary truncate font-medium'>
          {entry.displayName}
        </p>
        <p className='text-xs theme-text-muted'>
          {entry.count}{' '}
          {entry.isKnowledgeBase ? '篇文档' : '条数据'}
          {entry.description && <span className='ml-1.5'>· {entry.description}</span>}
        </p>
      </div>
      {onManageDocs && (
        <button
          onClick={(e) => onManageDocs(e)}
          aria-label='管理文档'
          title='管理文档'
          className='p-1 rounded-lg opacity-0 group-hover:opacity-100 hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 flex-shrink-0'
        >
          <FileText className='w-3.5 h-3.5' />
        </button>
      )}
      {onDelete && (
        <button
          onClick={(e) => onDelete(e)}
          aria-label='删除知识库'
          className='p-1 rounded-lg opacity-0 group-hover:opacity-100 hover:bg-red-500/10 text-red-500 transition-all duration-200 flex-shrink-0'
        >
          <Trash2 className='w-3.5 h-3.5' />
        </button>
      )}
      <Share2 className='w-3 h-3 theme-text-muted opacity-0 group-hover:opacity-40 flex-shrink-0' />
    </motion.div>
  )
}

/** 文档列表项 */
function DocumentItem({ doc, onDelete }: { doc: KnowledgeDocument; onDelete: () => void }) {
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const statusIcon = () => {
    switch (doc.status) {
      case 'INDEXED':
        return <CheckCircle2 className='w-3.5 h-3.5 text-green-500' />
      case 'PROCESSING':
      case 'PENDING':
        return <Loader2 className='w-3.5 h-3.5 animate-spin text-yellow-500' />
      case 'FAILED':
        return <XCircle className='w-3.5 h-3.5 text-red-500' />
    }
  }

  const handleDownload = (e: React.MouseEvent) => {
    e.stopPropagation()
    if (!doc.downloadUrl) return
    // 拼接成绝对路径：Vite 会把 /api 代理到 8080
    const fullUrl = doc.downloadUrl +
      (doc.downloadUrl.includes('?') ? '&' : '?') + 'userId=' + DEFAULT_USER_ID
    window.open(fullUrl, '_blank', 'noopener,noreferrer')
  }

  return (
    <div className='group flex items-center gap-2.5 p-2 rounded-lg hover:theme-bg-hover transition-all duration-200'>
      <FileText className='w-4 h-4 theme-text-muted flex-shrink-0' />
      <div className='flex-1 min-w-0'>
        <p className='text-sm theme-text-primary truncate'>{doc.fileName}</p>
        <div className='flex items-center gap-1.5'>
          {statusIcon()}
          <span className='text-xs theme-text-muted'>
            {formatSize(doc.fileSize)}
            {doc.contentLength ? ` · ${doc.contentLength.toLocaleString()} 字` : ''}
          </span>
        </div>
      </div>
      {doc.status === 'INDEXED' && doc.downloadUrl && (
        <button
          onClick={handleDownload}
          aria-label='下载原始文件'
          className='p-1 rounded-lg opacity-0 group-hover:opacity-100 hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 flex-shrink-0'
        >
          <Download className='w-3.5 h-3.5' />
        </button>
      )}
      <button
        onClick={onDelete}
        aria-label='删除文档'
        className='p-1 rounded-lg opacity-0 group-hover:opacity-100 hover:bg-red-500/10 text-red-500 transition-all duration-200 flex-shrink-0'
      >
        <Trash2 className='w-3.5 h-3.5' />
      </button>
    </div>
  )
}

export default KnowledgePanel
