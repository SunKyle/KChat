import { useState, useEffect, useRef } from 'react'
import {
  Plus,
  Filter,
  Trash2,
  BookOpen,
  FileText,
  CheckCircle,
  Heart,
  Lightbulb,
  Edit2,
  Search,
  Database,
  Star,
  User,
  Wrench,
  Briefcase,
  Target,
  Users,
  Calendar,
} from 'lucide-react'
import { memory } from '../../../api/memory'
import { MEMORY_TYPES } from '../../../types'
import type { Memory, MemoryType } from '../../../types'

const typeIcons: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  KNOWLEDGE: BookOpen,
  RULE: FileText,
  FACT: CheckCircle,
  PREFERENCE: Heart,
  EXPERIENCE: Lightbulb,
  PROFILE: User,
  SKILL: Wrench,
  PROJECT: Briefcase,
  TASK: Target,
  RELATION: Users,
  EVENT: Calendar,
}

const typeColors: Record<string, string> = {
  KNOWLEDGE: 'bg-blue-500',
  RULE: 'bg-red-500',
  FACT: 'bg-green-500',
  PREFERENCE: 'bg-pink-500',
  EXPERIENCE: 'bg-teal-500',
  PROFILE: 'bg-cyan-500',
  SKILL: 'bg-emerald-500',
  PROJECT: 'bg-violet-500',
  TASK: 'bg-amber-500',
  RELATION: 'bg-indigo-500',
  EVENT: 'bg-orange-500',
}

const getTypeInfo = (type: string) => {
  return MEMORY_TYPES.find((t) => t.type === type) || MEMORY_TYPES[0]
}

const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

export function MemoryPanel() {
  const [memories, setMemories] = useState<Memory[]>([])
  const [loading, setLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedType, setSelectedType] = useState<MemoryType | 'ALL'>('ALL')
  const [showForm, setShowForm] = useState(false)
  const [editingMemory, setEditingMemory] = useState<Memory | null>(null)
  const [selectedMemories, setSelectedMemories] = useState<number[]>([])
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const userId = 'default'

  useEffect(() => {
    loadMemories()
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [])

  const loadMemories = async () => {
    setLoading(true)
    try {
      const data = await memory.getAll(userId)
      setMemories(data)
    } catch (error) {
      console.error('加载记忆失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = async (query: string) => {
    if (!query.trim()) {
      loadMemories()
      return
    }
    setLoading(true)
    try {
      const result = await memory.recall({ userId, query, topK: 20 })
      setMemories(result.memories)
    } catch (error) {
      console.error('搜索失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSearchChange = (value: string) => {
    setSearchQuery(value)
    if (debounceRef.current) clearTimeout(debounceRef.current)
    const timer = setTimeout(() => handleSearch(value), 300)
    debounceRef.current = timer
  }

  const handleDelete = async (id: number) => {
    try {
      await memory.delete(id)
      setMemories(memories.filter((m) => m.id !== id))
      setSelectedMemories(selectedMemories.filter((i) => i !== id))
    } catch (error) {
      console.error('删除失败:', error)
    }
  }

  const handleBatchDelete = async () => {
    for (const id of selectedMemories) {
      await memory.delete(id)
    }
    setMemories(memories.filter((m) => !selectedMemories.includes(m.id)))
    setSelectedMemories([])
  }

  const handleSubmit = async (memoryData: Memory | Omit<Memory, 'id' | 'createdAt'>) => {
    const data = {
      userId: 'default',
      content: (memoryData as Memory).content,
      type: (memoryData as Memory).type,
      importance: (memoryData as Memory).importance,
      isRule: (memoryData as Memory).isRule || false,
    }

    try {
      const memoryId = (memoryData as Memory).id
      if (memoryId !== undefined && memoryId !== null) {
        const updatedMemory = await memory.update(memoryId, data)
        setMemories(memories.map((m) => (m.id === memoryId ? updatedMemory : m)))
      } else {
        const newMemory = await memory.create(data)
        setMemories([newMemory, ...memories])
      }
      setShowForm(false)
      setEditingMemory(null)
    } catch (error) {
      console.error('保存记忆失败:', error)
    }
  }

  const handleEdit = (memory: Memory) => {
    setEditingMemory(memory)
    setShowForm(true)
  }

  const filteredMemories =
    selectedType === 'ALL' ? memories : memories.filter((m) => m.type === selectedType)

  const isSelectAll =
    selectedMemories.length === filteredMemories.length && filteredMemories.length > 0

  const getTypeIcon = (type: string) => typeIcons[type] || BookOpen
  const getTypeColor = (type: string) => typeColors[type] || 'theme-text-muted'

  return (
    <div className='flex flex-col w-full max-h-[calc(100vh-200px)] min-h-[200px]'>
      <div className='flex flex-col sm:flex-row md:flex-row lg:flex-row items-start sm:items-center md:items-center lg:items-center justify-between gap-3 mb-4 w-full'>
        <div className='flex items-center gap-2 flex-shrink-0'>
          <Database className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>记忆列表</h3>
        </div>

        <div className='flex flex-wrap items-center gap-2 w-full sm:w-auto sm:flex-nowrap md:flex-nowrap lg:flex-nowrap'>
          {/* 搜索框 */}
          <div className='flex-1 sm:flex-initial md:flex-initial lg:flex-initial relative min-w-[100px] sm:min-w-[120px] md:min-w-[140px] lg:min-w-[160px]'>
            <Search className='absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]pointer-events-none' />
            <input
              type='text'
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              placeholder='搜索记忆...'
              className='w-full sm:w-44 md:w-48 lg:w-56 xl:w-64 pl-9 pr-3 py-2 text-sm bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--accent-primary)]/60 focus:ring-1.5 focus:ring-[var(--accent-primary)]/30 transition-all duration-200'
            />
          </div>

          {/* 下拉框 */}
          <div className='relative min-w-[80px] sm:min-w-[90px] flex-shrink-0'>
            <Filter className='absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]pointer-events-none' />
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as MemoryType | 'ALL')}
              className='w-full pl-9 pr-7 py-2 text-sm appearance-none cursor-pointer bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[var(--text-primary)] focus:outline-none focus:border-[var(--accent-primary)]/60 focus:ring-1.5 focus:ring-[var(--accent-primary)]/30 transition-all duration-200'
            >
              <option value='ALL'>全部</option>
              {MEMORY_TYPES.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label}
                </option>
              ))}
            </select>
            {/* 下拉箭头 */}
            <svg className='absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]pointer-events-none' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
              <path strokeLinecap='round' strokeLinejoin='round' strokeWidth={2} d='M19 9l-7 7-7-7' />
            </svg>
          </div>

          {/* 添加按钮 */}
          <button
            onClick={() => {
              setEditingMemory(null)
              setShowForm(true)
            }}
            className='flex items-center justify-center gap-1 px-3 py-2 text-sm font-medium text-white bg-[var(--accent-primary)] hover:bg-[var(--accent-primary)]/90 rounded-xl shadow-sm hover:shadow-md transition-all duration-200 whitespace-nowrap flex-shrink-0'
          >
            <Plus className='w-4 h-4' />
            <span className='hidden sm:inline'>添加</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className='flex items-center justify-center py-12'>
          <div className='w-8 h-8 border-3 border-[var(--accent-primary)] border-t-transparent rounded-full animate-spin'></div>
        </div>
      ) : filteredMemories.length === 0 ? (
        <div className='card-float-solid rounded-2xl p-8 text-center'>
          <div className='w-14 h-14 rounded-full theme-bg-input flex items-center justify-center mb-4'>
            <Search className='w-7 h-7 theme-text-muted' />
          </div>
          <h3 className='text-base font-medium theme-text-primary mb-1'>暂无记忆</h3>
          <p className='theme-text-muted text-sm mb-5'>添加你的第一条记忆</p>
          <button
            onClick={() => {
              setEditingMemory(null)
              setShowForm(true)
            }}
            className='px-4 py-2 theme-bg-hover/50 hover:theme-bg-hover theme-text-secondary rounded-lg transition-colors text-sm'
          >
            添加记忆
          </button>
        </div>
      ) : (
        <div className='w-full bg-[var(--bg-card)] rounded-xl border border-[var(--border-secondary)] overflow-hidden'>
          {/* 记忆列表 */}
          <div className='w-full divide-y divide-[var(--border-secondary)]'>
            {filteredMemories.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-8 sm:py-12 text-center w-full">
                <div className="w-10 h-10 sm:w-12 sm:h-12 rounded-xl bg-[var(--bg-input)] flex items-center justify-center mb-3">
                  <Database className="w-5 h-5 sm:w-6 sm:h-6 text-[var(--text-muted)]" />
                </div>
                <h3 className="text-sm font-medium text-[var(--text-secondary)]">暂无记忆</h3>
                <p className="text-xs text-[var(--text-muted)] mt-1">
                  {searchQuery ? "没有找到匹配的记忆" : "开始对话后，系统会自动提取记忆"}
                </p>
              </div>
            ) : (
              filteredMemories.map((memory) => {
                const TypeIcon = getTypeIcon(memory.type)
                const colorClass = getTypeColor(memory.type)
                const isSelected = selectedMemories.includes(memory.id)
                const typeInfo = getTypeInfo(memory.type)
                const importance = memory.importance || 0

                return (
                  <div
                    key={memory.id}
                    className={`group relative px-3 sm:px-4 py-2.5 sm:py-3 flex items-center gap-2 sm:gap-3 transition-colors cursor-pointer w-full ${
                      isSelected ? 'bg-[var(--accent-primary)]/5' : 'hover:bg-[var(--bg-input)]/50'
                    }`}
                    onClick={() =>
                      setSelectedMemories((prev) =>
                        prev.includes(memory.id)
                          ? prev.filter((i) => i !== memory.id)
                          : [...prev, memory.id]
                      )
                    }
                  >
                    {/* 选择框 */}
                    <div className='flex-shrink-0'>
                      {isSelected ? (
                        <div className='w-4 h-4 rounded-md bg-[var(--accent-primary)] flex items-center justify-center'>
                          <svg className='w-3 h-3 text-white' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
                            <path strokeLinecap='round' strokeLinejoin='round' strokeWidth={3} d='M5 13l4 4L19 7' />
                          </svg>
                        </div>
                      ) : selectedMemories.length > 0 ? (
                        <div className='w-4 h-4 rounded-md border border-[var(--border-primary)]' />
                      ) : null}
                    </div>

                    {/* 类型图标 */}
                    <div className={`w-7 h-7 sm:w-8 sm:h-8 rounded-lg ${colorClass} flex items-center justify-center flex-shrink-0`}>
                      <TypeIcon className='w-3.5 h-3.5 sm:w-4 sm:h-4 text-white' />
                    </div>

                    {/* 内容区域 */}
                    <div className='min-w-0 flex-1'>
                      <div className='flex items-start justify-between gap-2'>
                        <h4 className='text-sm font-medium text-[var(--text-primary)] leading-relaxed line-clamp-2'>
                          {memory.content}
                        </h4>
                        
                        {/* 操作按钮 */}
                        <div className='flex items-center gap-1 opacity-0 sm:opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0'>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleEdit(memory)
                            }}
                            className='p-1 sm:p-1.5 rounded-md hover:bg-[var(--bg-hover)] text-[var(--text-muted)] hover:text-[var(--accent-primary)]'
                            title='编辑'
                          >
                            <Edit2 className='w-3.5 h-3.5 sm:w-4 sm:h-4' />
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleDelete(memory.id)
                            }}
                            className='p-1 sm:p-1.5 rounded-md hover:bg-[var(--brand-danger)]/10 text-[var(--text-muted)] hover:text-[var(--brand-danger)]'
                            title='删除'
                          >
                            <Trash2 className='w-3.5 h-3.5 sm:w-4 sm:h-4' />
                          </button>
                        </div>
                      </div>

                      <div className='flex items-center gap-1.5 sm:gap-2 mt-1 sm:mt-1.5 flex-wrap'>
                        {/* 类型标签 */}
                        <span className={`px-1.5 sm:px-2 py-0.5 rounded-md text-xs text-white ${colorClass}`}>
                          {typeInfo.label}
                        </span>

                        {/* 规则标记 */}
                        {memory.isRule && (
                          <span className='px-1.5 sm:px-2 py-0.5 rounded-md text-xs bg-[var(--brand-danger)]/10 text-[var(--brand-danger)]'>
                            规则
                          </span>
                        )}

                        {/* 重要性 */}
                        <div className='flex items-center gap-0.5 ml-auto'>
                          {Array.from({ length: 5 }).map((_, i) => (
                            <Star
                              key={i}
                              className={`w-2.5 h-2.5 sm:w-3 sm:h-3 ${
                                i < importance ? 'text-amber-400 fill-amber-400' : 'text-[var(--border-primary)]'
                              }`}
                            />
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                )
              })
            )}
          </div>

          {/* 底部批量操作 */}
          {selectedMemories.length > 0 && (
            <div className='px-3 sm:px-4 py-2.5 sm:py-3 bg-[var(--bg-input)]/50 border-t border-[var(--border-secondary)]'>
              <div className='flex items-center justify-between flex-wrap gap-2'>
                <div className='flex items-center gap-2 sm:gap-3'>
                  <label className='flex items-center gap-1.5 sm:gap-2 cursor-pointer'>
                    <input
                      type='checkbox'
                      checked={isSelectAll}
                      onChange={(e) => {
                        if (e.target.checked) {
                          setSelectedMemories(filteredMemories.map((m) => m.id))
                        } else {
                          setSelectedMemories([])
                        }
                      }}
                      className='w-4 h-4 rounded border-[var(--border-primary)] bg-[var(--bg-card)] text-[var(--accent-primary)] focus:ring-[var(--accent-primary)]/50'
                    />
                    <span className='text-sm text-[var(--text-secondary)]'>全选</span>
                  </label>
                  <span className='text-xs text-[var(--text-secondary)]'>
                    已选择 {selectedMemories.length} 项
                  </span>
                </div>
                <button
                  onClick={handleBatchDelete}
                  className='flex items-center gap-1 sm:gap-1.5 px-2.5 sm:px-3 py-1.5 sm:py-1.5 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm font-medium transition-colors whitespace-nowrap'
                >
                  <Trash2 className='w-3.5 h-3.5' />
                  删除
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {showForm && (
        <div
          className='fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4'
          onClick={() => {
            setShowForm(false)
            setEditingMemory(null)
          }}
        >
          <div
            className='w-full max-w-lg bg-[var(--bg-card)] rounded-2xl shadow-xl border border-[var(--border-secondary)] overflow-hidden transform transition-all duration-200 scale-100'
            onClick={(e) => e.stopPropagation()}
          >
            {/* 头部 */}
            <div className='flex items-center justify-between px-5 py-4 border-b border-[var(--border-secondary)] bg-gradient-to-r from-[var(--bg-input)] to-[var(--bg-card)]'>
              <div className='flex items-center gap-3'>
                <div className={`w-8 h-8 rounded-xl ${editingMemory ? 'bg-violet-500' : 'bg-[var(--accent-primary)]'} flex items-center justify-center`}>
                  {editingMemory ? (
                    <Edit2 className='w-4 h-4 text-white' />
                  ) : (
                    <Plus className='w-4 h-4 text-white' />
                  )}
                </div>
                <div>
                  <h3 className='text-base font-semibold text-[var(--text-primary)]'>
                    {editingMemory ? '编辑记忆' : '添加记忆'}
                  </h3>
                  <p className='text-xs text-[var(--text-secondary)]'>
                    {editingMemory ? '修改已有的记忆内容' : '创建一条新的记忆'}
                  </p>
                </div>
              </div>
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className='p-2 rounded-lg hover:bg-[var(--bg-hover)] text-[var(--text-muted)]hover:text-[var(--text-secondary)] transition-colors'
              >
                <svg className='w-5 h-5' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
                  <path strokeLinecap='round' strokeLinejoin='round' strokeWidth={2} d='M6 18L18 6M6 6l12 12' />
                </svg>
              </button>
            </div>

            {/* 表单内容 */}
            <div className='p-5 space-y-5'>
              {/* 内容字段 */}
              <div className='space-y-2'>
                <div className='flex items-center justify-between'>
                  <label className='block text-sm font-medium text-[var(--text-primary)]'>
                    记忆内容
                  </label>
                  <span className='text-xs text-[var(--text-muted)]'>
                    {(editingMemory?.content || '').length}/500
                  </span>
                </div>
                <textarea
                  value={editingMemory?.content || ''}
                  onChange={(e) => {
                    setEditingMemory({
                      id: editingMemory?.id,
                      userId: editingMemory?.userId,
                      type: editingMemory?.type || 'KNOWLEDGE',
                      content: e.target.value,
                      importance: editingMemory?.importance || 3,
                      isRule: editingMemory?.isRule || false,
                      createdAt: editingMemory?.createdAt || '',
                    } as Memory)
                  }}
                  placeholder='输入记忆内容，例如：用户使用Java开发企业应用...'
                  className='w-full h-28 px-4 py-3 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[var(--text-primary)] text-sm placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--accent-primary)]/60 focus:ring-1.5 focus:ring-[var(--accent-primary)]/30 resize-none transition-all duration-200'
                  maxLength={500}
                />
              </div>

              {/* 类型选择 */}
              <div className='space-y-2.5'>
                <label className='block text-sm font-medium text-[var(--text-primary)]'>
                  记忆类型
                </label>
                <div className='flex flex-wrap gap-2'>
                  {MEMORY_TYPES.map((t) => {
                    const TypeIcon = getTypeIcon(t.type)
                    const isSelected = !editingMemory || editingMemory.type === t.type
                    return (
                      <button
                        key={t.type}
                        onClick={() => {
                          setEditingMemory({
                            id: editingMemory?.id,
                            userId: editingMemory?.userId,
                            type: t.type as MemoryType,
                            content: editingMemory?.content || '',
                            importance: editingMemory?.importance || 3,
                            isRule: editingMemory?.isRule || false,
                            createdAt: editingMemory?.createdAt || '',
                          } as Memory)
                        }}
                        className={`flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-medium transition-all duration-200 ${
                          isSelected
                            ? `${getTypeColor(t.type)} text-white shadow-md scale-[1.02]`
                            : 'bg-[var(--bg-input)] border border-[var(--border-primary)] text-[var(--text-secondary)] hover:border-[var(--border-primary)] hover:bg-[var(--bg-hover)]'
                        }`}
                      >
                        <TypeIcon className='w-3.5 h-3.5' />
                        {t.label}
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* 重要性选择 */}
              <div className='space-y-2.5'>
                <label className='block text-sm font-medium text-[var(--text-primary)]'>
                  重要性
                </label>
                <div className='flex items-center gap-1.5'>
                  {Array.from({ length: 5 }).map((_, i) => {
                    const currentImportance = editingMemory?.importance || 3
                    return (
                      <button
                        key={i}
                        onClick={() => {
                          setEditingMemory({
                            id: editingMemory?.id,
                            userId: editingMemory?.userId,
                            type: editingMemory?.type || 'KNOWLEDGE',
                            content: editingMemory?.content || '',
                            importance: i + 1,
                            isRule: editingMemory?.isRule || false,
                            createdAt: editingMemory?.createdAt || '',
                          } as Memory)
                        }}
                        className={`p-2.5 rounded-xl transition-all duration-200 ${
                          i < currentImportance
                            ? 'text-[var(--accent-amber)] bg-[var(--accent-amber)]/10 scale-110'
                            : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'
                        }`}
                      >
                        <Star className={`w-5 h-5 ${i < currentImportance ? 'fill-current' : ''}`} />
                      </button>
                    )
                  })}
                  <span className='ml-2 text-sm text-[var(--text-secondary)] font-medium'>
                    {(editingMemory?.importance || 3)}/5
                  </span>
                  <span className='text-xs text-[var(--text-muted)]ml-1'>
                    ({['最低', '较低', '中等', '较高', '最高'][(editingMemory?.importance || 3) - 1]})
                  </span>
                </div>
              </div>

              {/* 规则标记 */}
              <div className='flex items-center gap-3 p-3 bg-[var(--brand-danger)]/5 rounded-xl border border-[var(--brand-danger)]/20'>
                <input
                  type='checkbox'
                  id='isRule'
                  checked={editingMemory?.isRule || false}
                  onChange={(e) => {
                    setEditingMemory({
                      id: editingMemory?.id,
                      userId: editingMemory?.userId,
                      type: editingMemory?.type || 'KNOWLEDGE',
                      content: editingMemory?.content || '',
                      importance: editingMemory?.importance || 3,
                      isRule: e.target.checked,
                      createdAt: editingMemory?.createdAt || '',
                    } as Memory)
                  }}
                  className='w-4 h-4 rounded border-[var(--brand-danger)]/30 bg-[var(--bg-card)] text-[var(--brand-danger)] focus:ring-[var(--brand-danger)]/50 focus:ring-offset-0'
                />
                <div>
                  <label htmlFor='isRule' className='text-sm font-medium text-[var(--brand-danger)] cursor-pointer'>
                    标记为规则
                  </label>
                  <p className='text-xs text-[var(--brand-danger)] mt-0.5'>
                    规则类型的记忆会在匹配时强制应用
                  </p>
                </div>
              </div>
            </div>

            {/* 底部按钮 */}
            <div className='flex items-center justify-between px-5 py-4 border-t border-[var(--border-secondary)] bg-[var(--bg-input)]/50'>
              <div className='text-xs text-[var(--text-secondary)]'>
                {editingMemory?.createdAt && (
                  <span>创建于 {formatDate(editingMemory.createdAt)}</span>
                )}
              </div>
              <div className='flex items-center gap-2.5'>
                <button
                  onClick={() => {
                    setShowForm(false)
                    setEditingMemory(null)
                  }}
                  className='px-5 py-2.5 text-sm font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-hover)] rounded-xl transition-all duration-200'
                >
                  取消
                </button>
                <button
                  onClick={() => {
                    if (editingMemory) {
                      handleSubmit(editingMemory)
                    }
                  }}
                  disabled={!editingMemory?.content.trim()}
                  className={`px-5 py-2.5 text-sm font-medium rounded-xl transition-all duration-200 ${
                    editingMemory?.content.trim()
                      ? 'bg-[var(--accent-primary)] text-white hover:bg-[var(--accent-primary)]/90 shadow-md hover:shadow-lg'
                      : 'bg-[var(--bg-hover)] text-[var(--text-muted)] cursor-not-allowed'
                  }`}
                >
                  {editingMemory ? '保存修改' : '创建记忆'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
