import { useState, useEffect, useRef, useCallback } from 'react'
import { Icon } from '../../common/Icon'
import { motion } from 'framer-motion'
import { knowledgeBaseApi, type KnowledgeDocument } from '../../../api/knowledge'

const DEFAULT_USER_ID = 'default'

interface KnowledgeContentViewProps {
  kbId: string
  /** 文档数量变化回调（供 Header 胶囊展示） */
  onStatsChange?: (count: number) => void
}

export function KnowledgeContentView({ kbId, onStatsChange }: KnowledgeContentViewProps) {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [reindexing, setReindexing] = useState(false)
  const [error, setError] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  const loadDocuments = useCallback(async () => {
    try {
      const docs = await knowledgeBaseApi.listDocuments(DEFAULT_USER_ID, kbId)
      setDocuments(docs)
      onStatsChange?.(docs.length)
    } catch (e) {
      console.error('Failed to load documents:', e)
      setError('加载文档列表失败')
    } finally {
      setLoading(false)
    }
  }, [kbId, onStatsChange])

  useEffect(() => {
    setLoading(true)
    setSelectedId(null)
    setError('')
    loadDocuments()
  }, [loadDocuments])

  /** 轮询处理中的文档状态 */
  useEffect(() => {
    const hasProcessing = documents.some(
      (d) => d.status === 'PROCESSING' || d.status === 'PENDING'
    )
    if (!hasProcessing) return
    const interval = setInterval(() => loadDocuments(), 3000)
    return () => clearInterval(interval)
  }, [documents, loadDocuments])

  /** 默认选中第一个已入库的文档 */
  useEffect(() => {
    if (selectedId || documents.length === 0) return
    const firstIndexed = documents.find((d) => d.status === 'INDEXED') ?? documents[0]
    setSelectedId(firstIndexed.id)
  }, [documents, selectedId])

  const handleUpload = async (files: FileList) => {
    if (files.length === 0) return
    setUploading(true)
    setError('')
    try {
      for (const file of Array.from(files)) {
        await knowledgeBaseApi.uploadDocument(DEFAULT_USER_ID, kbId, file)
      }
      await loadDocuments()
    } catch (e) {
      console.error('Upload failed:', e)
      setError('文档上传失败')
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleDeleteDoc = async (docId: string) => {
    if (!confirm('确定删除此文档？')) return
    try {
      await knowledgeBaseApi.deleteDocument(DEFAULT_USER_ID, kbId, docId)
      if (selectedId === docId) setSelectedId(null)
      await loadDocuments()
    } catch (e) {
      console.error('Failed to delete document:', e)
    }
  }

  const handleDownload = (doc: KnowledgeDocument) => {
    if (!doc.downloadUrl) return
    const fullUrl =
      doc.downloadUrl + (doc.downloadUrl.includes('?') ? '&' : '?') + 'userId=' + DEFAULT_USER_ID
    window.open(fullUrl, '_blank', 'noopener,noreferrer')
  }

  /** 重新索引当前知识库（清空图谱并重建） */
  const handleReindex = async () => {
    if (!confirm('重新索引会清空该知识库的图谱并重新建立索引，文档较多时可能较慢。确定继续？')) return
    setReindexing(true)
    setError('')
    try {
      await knowledgeBaseApi.reindex(DEFAULT_USER_ID, kbId)
      await loadDocuments()
    } catch (e) {
      console.error('Reindex failed:', e)
      setError('重新索引失败')
    } finally {
      setReindexing(false)
    }
  }

  const selectedDoc = documents.find((d) => d.id === selectedId) ?? null

  return (
    <div className='relative flex h-full'>
      <input
        ref={fileInputRef}
        type='file'
        multiple
        className='hidden'
        onChange={(e) => e.target.files && handleUpload(e.target.files)}
        accept='.pdf,.doc,.docx,.txt,.md,.html,.htm,.xls,.xlsx,.ppt,.pptx'
      />

      {/* 左侧：文档列表 */}
      <div className='flex flex-col w-56 sm:w-64 flex-shrink-0 border-r theme-border-primary h-full'>
        <div className='p-3 flex-shrink-0 space-y-2'>
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className='w-full flex items-center justify-center gap-2 py-2 rounded-lg border border-dashed theme-border hover:theme-bg-hover transition-all duration-200 text-xs theme-text-secondary disabled:opacity-50'
          >
            {uploading ? (
              <>
                <Icon name='Loader2' size='sm' className='animate-spin' />
                上传中...
              </>
            ) : (
              <>
                <Icon name='Upload' size='sm' />
                上传文档
              </>
            )}
          </button>

          <button
            onClick={handleReindex}
            disabled={reindexing}
            className='w-full flex items-center justify-center gap-2 py-2 rounded-lg border theme-border hover:theme-bg-hover transition-all duration-200 text-xs theme-text-secondary disabled:opacity-50'
            title='清空该知识库图谱并重新建立索引'
          >
            {reindexing ? (
              <>
                <Icon name='Loader2' size='sm' className='animate-spin' />
                索引中...
              </>
            ) : (
              <>
                <Icon name='RefreshCw' size='sm' />
                重新索引
              </>
            )}
          </button>
        </div>

        {error && (
          <div className='px-3 pb-2'>
            <p className='text-xs text-red-500 flex items-center gap-1'>
              <Icon name='AlertCircle' size='xs' />
              {error}
            </p>
          </div>
        )}

        <div className='flex-1 min-h-0 overflow-y-auto px-2 pb-3'>
          {loading ? (
            <div className='flex items-center justify-center h-full'>
              <Icon name='Loader2' size='lg' className='animate-spin theme-text-muted' />
            </div>
          ) : documents.length === 0 ? (
            <div className='flex flex-col items-center justify-center h-full text-center px-3'>
              <Icon name='FileText' size='2xl' className='theme-text-muted mb-2' />
              <p className='text-xs theme-text-muted'>暂无文档</p>
              <p className='text-xs theme-text-muted mt-1'>上传后即可查看提取内容</p>
            </div>
          ) : (
            <div className='space-y-1'>
              {documents.map((doc) => (
                <DocListItem
                  key={doc.id}
                  doc={doc}
                  active={doc.id === selectedId}
                  onSelect={() => setSelectedId(doc.id)}
                  onDelete={() => handleDeleteDoc(doc.id)}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 右侧：提取内容 */}
      <div className='flex-1 min-w-0 h-full overflow-y-auto'>
        {selectedDoc ? (
          <ExtractedContent doc={selectedDoc} onDownload={() => handleDownload(selectedDoc)} />
        ) : (
          <div className='flex flex-col items-center justify-center h-full text-center px-6'>
            <Icon name='FileSearch' size={48} className='theme-text-muted mb-3 opacity-50' />
            <p className='text-sm theme-text-secondary font-medium mb-1'>
              {documents.length === 0 ? '上传文档以查看提取信息' : '选择左侧文档查看提取内容'}
            </p>
            <p className='text-xs theme-text-muted'>
              系统通过 Apache Tika 自动提取文档文本
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

/** 文档列表项 */
function DocListItem({
  doc,
  active,
  onSelect,
  onDelete,
}: {
  doc: KnowledgeDocument
  active: boolean
  onSelect: () => void
  onDelete: () => void
}) {
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  const statusIcon = () => {
    switch (doc.status) {
      case 'INDEXED':
        return <Icon name='CheckCircle2' size='xs' className='text-green-500' />
      case 'PROCESSING':
      case 'PENDING':
        return <Icon name='Loader2' size='xs' className='animate-spin text-yellow-500' />
      case 'FAILED':
        return <Icon name='XCircle' size='xs' className='text-red-500' />
    }
  }

  return (
    <motion.div
      layout
      onClick={onSelect}
      className={`group flex items-center gap-2 p-2 rounded-lg cursor-pointer transition-all duration-200 ${
        active ? 'bg-brand-selected theme-brand-primary' : 'hover:theme-bg-hover'
      }`}
    >
      <Icon name='FileText' size='sm' className='flex-shrink-0 theme-text-muted' />
      <div className='flex-1 min-w-0'>
        <p className='text-xs truncate font-medium'>{doc.fileName}</p>
        <div className='flex items-center gap-1.5'>
          {statusIcon()}
          <span className='text-xs theme-text-muted'>
            {formatSize(doc.fileSize)}
            {doc.contentLength ? ` · ${doc.contentLength.toLocaleString()}字` : ''}
          </span>
        </div>
      </div>
      <button
        onClick={(e) => {
          e.stopPropagation()
          onDelete()
        }}
        aria-label='删除文档'
        className='p-1 rounded opacity-0 group-hover:opacity-100 hover:bg-red-500/10 text-red-500 transition-all duration-200 flex-shrink-0'
      >
        <Icon name='Trash2' size='xs' />
      </button>
    </motion.div>
  )
}

/** 提取内容展示 */
function ExtractedContent({
  doc,
  onDownload,
}: {
  doc: KnowledgeDocument
  onDownload: () => void
}) {
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B'
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  }

  return (
    <div className='h-full flex flex-col'>
      {/* 文档元信息 */}
      <div className='flex-shrink-0 px-5 py-3 border-b theme-border-primary flex items-center justify-between gap-3'>
        <div className='min-w-0 flex-1'>
          <p className='text-sm font-semibold theme-text-primary truncate'>{doc.fileName}</p>
          <p className='text-xs theme-text-muted mt-0.5'>
            {formatSize(doc.fileSize)} · {doc.fileType}
            {doc.contentLength ? ` · 提取 ${doc.contentLength.toLocaleString()} 字` : ''}
            {doc.errorMessage ? ` · 错误: ${doc.errorMessage}` : ''}
          </p>
        </div>
        {doc.status === 'INDEXED' && doc.downloadUrl && (
          <button
            onClick={onDownload}
            className='flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg border theme-border-primary hover:theme-bg-hover transition-all duration-200 text-xs theme-text-secondary flex-shrink-0'
            title='下载原始文件'
          >
            <Icon name='Download' size='sm' />
            <span className='hidden sm:inline'>原始文件</span>
          </button>
        )}
      </div>

      {/* 提取的文本内容 */}
      <div className='flex-1 min-h-0 overflow-y-auto px-5 py-4'>
        {doc.content && doc.content.trim() ? (
          <pre className='text-sm theme-text-primary whitespace-pre-wrap break-words font-sans leading-relaxed'>
            {doc.content}
          </pre>
        ) : doc.status === 'PROCESSING' || doc.status === 'PENDING' ? (
          <div className='flex items-center justify-center h-full'>
            <div className='flex items-center gap-2 text-xs theme-text-muted'>
              <Icon name='Loader2' size='md' className='animate-spin' />
              正在提取文本...
            </div>
          </div>
        ) : doc.status === 'FAILED' ? (
          <div className='flex items-center justify-center h-full'>
            <p className='text-xs text-red-500'>文本提取失败</p>
          </div>
        ) : (
          <div className='flex items-center justify-center h-full'>
            <p className='text-xs theme-text-muted'>未提取到文本内容（可能是扫描件或二进制文件）</p>
          </div>
        )}
      </div>
    </div>
  )
}

export default KnowledgeContentView
