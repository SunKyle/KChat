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
    <div className='flex flex-col max-h-[calc(100vh-200px)] min-h-[200px]'>
      <div className='flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-4'>
        <div className='flex items-center gap-2'>
          <Database className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>记忆列表</h3>
        </div>

        <div className='flex items-center gap-2.5 w-full sm:w-auto'>
          {/* 搜索框 */}
          <div className='flex-1 sm:flex-initial relative'>
            <Search className='absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none' />
            <input
              type='text'
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              placeholder='搜索记忆...'
              className='w-full sm:w-44 pl-10 pr-3 py-2.5 text-sm bg-gray-50 border border-gray-200 rounded-xl text-gray-700 placeholder-gray-400 focus:outline-none focus:border-[var(--accent-sky)]/60 focus:ring-1.5 focus:ring-[var(--accent-sky)]/30 transition-all duration-200'
            />
          </div>

          {/* 下拉框 */}
          <div className='relative min-w-[100px]'>
            <Filter className='absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none' />
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as MemoryType | 'ALL')}
              className='w-full pl-10 pr-8 py-2.5 text-sm appearance-none cursor-pointer bg-gray-50 border border-gray-200 rounded-xl text-gray-700 focus:outline-none focus:border-[var(--accent-sky)]/60 focus:ring-1.5 focus:ring-[var(--accent-sky)]/30 transition-all duration-200'
            >
              <option value='ALL'>全部</option>
              {MEMORY_TYPES.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label}
                </option>
              ))}
            </select>
            {/* 下拉箭头 */}
            <svg className='absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
              <path strokeLinecap='round' strokeLinejoin='round' strokeWidth={2} d='M19 9l-7 7-7-7' />
            </svg>
          </div>

          {/* 添加按钮 */}
          <button
            onClick={() => {
              setEditingMemory(null)
              setShowForm(true)
            }}
            className='flex items-center justify-center gap-1.5 px-4 py-2.5 text-sm font-medium text-white bg-[var(--accent-sky)] hover:bg-[var(--accent-sky)]/90 rounded-xl shadow-sm hover:shadow-md transition-all duration-200 whitespace-nowrap'
          >
            <Plus className='w-4 h-4' />
            添加
          </button>
        </div>
      </div>

      {loading ? (
        <div className='flex items-center justify-center py-12'>
          <div className='w-8 h-8 border-3 border-[var(--accent-sky)] border-t-transparent rounded-full animate-spin'></div>
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
        <div className='bg-white rounded-xl border border-gray-100 overflow-hidden'>
          {/* 记忆列表 */}
          <div className='divide-y divide-gray-50'>
            {filteredMemories.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <div className="w-12 h-12 rounded-xl bg-gray-50 flex items-center justify-center mb-3">
                  <Database className="w-6 h-6 text-gray-400" />
                </div>
                <h3 className="text-sm font-medium text-gray-600">暂无记忆</h3>
                <p className="text-xs text-gray-400 mt-1">
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
                    className={`group relative px-4 py-3 flex items-center gap-3 transition-colors cursor-pointer ${
                      isSelected ? 'bg-[var(--accent-sky)]/5' : 'hover:bg-gray-50/50'
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
                        <div className='w-4 h-4 rounded-md bg-[var(--accent-sky)] flex items-center justify-center'>
                          <svg className='w-3 h-3 text-white' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
                            <path strokeLinecap='round' strokeLinejoin='round' strokeWidth={3} d='M5 13l4 4L19 7' />
                          </svg>
                        </div>
                      ) : selectedMemories.length > 0 ? (
                        <div className='w-4 h-4 rounded-md border border-gray-300' />
                      ) : null}
                    </div>

                    {/* 类型图标 */}
                    <div className={`w-8 h-8 rounded-lg ${colorClass} flex items-center justify-center flex-shrink-0`}>
                      <TypeIcon className='w-4 h-4 text-white' />
                    </div>

                    {/* 内容区域 */}
                    <div className='min-w-0 flex-1'>
                      <div className='flex items-start justify-between gap-3'>
                        <h4 className='text-sm font-medium text-gray-800 leading-relaxed line-clamp-2'>
                          {memory.content}
                        </h4>
                        
                        {/* 操作按钮 */}
                        <div className='flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0'>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleEdit(memory)
                            }}
                            className='p-1.5 rounded-md hover:bg-gray-100 text-gray-400 hover:text-[var(--accent-sky)]'
                            title='编辑'
                          >
                            <Edit2 className='w-4 h-4' />
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation()
                              handleDelete(memory.id)
                            }}
                            className='p-1.5 rounded-md hover:bg-red-50 text-gray-400 hover:text-red-500'
                            title='删除'
                          >
                            <Trash2 className='w-4 h-4' />
                          </button>
                        </div>
                      </div>

                      <div className='flex items-center gap-2 mt-1.5'>
                        {/* 类型标签 */}
                        <span className={`px-2 py-0.5 rounded-md text-xs text-white ${colorClass}`}>
                          {typeInfo.label}
                        </span>

                        {/* 规则标记 */}
                        {memory.isRule && (
                          <span className='px-2 py-0.5 rounded-md text-xs bg-red-50 text-red-500'>
                            规则
                          </span>
                        )}

                        {/* 重要性 */}
                        <div className='flex items-center gap-0.5 ml-auto'>
                          {Array.from({ length: 5 }).map((_, i) => (
                            <Star
                              key={i}
                              className={`w-3 h-3 ${
                                i < importance ? 'text-amber-400 fill-amber-400' : 'text-gray-200'
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
            <div className='px-4 py-3 bg-gray-50/50 border-t border-gray-100'>
              <div className='flex items-center justify-between'>
                <div className='flex items-center gap-3'>
                  <label className='flex items-center gap-2 cursor-pointer'>
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
                      className='w-4 h-4 rounded border-gray-300 bg-white text-[var(--accent-sky)] focus:ring-[var(--accent-sky)]/50'
                    />
                    <span className='text-sm text-gray-600'>全选</span>
                  </label>
                  <span className='text-xs text-gray-500'>
                    已选择 {selectedMemories.length} 项
                  </span>
                </div>
                <button
                  onClick={handleBatchDelete}
                  className='flex items-center gap-1.5 px-3 py-1.5 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm font-medium transition-colors'
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
            className='w-full max-w-lg bg-white rounded-2xl shadow-xl border border-gray-100 overflow-hidden transform transition-all duration-200 scale-100'
            onClick={(e) => e.stopPropagation()}
          >
            {/* 头部 */}
            <div className='flex items-center justify-between px-5 py-4 border-b border-gray-100 bg-gradient-to-r from-gray-50 to-white'>
              <div className='flex items-center gap-3'>
                <div className={`w-8 h-8 rounded-xl ${editingMemory ? 'bg-violet-500' : 'bg-[var(--accent-sky)]'} flex items-center justify-center`}>
                  {editingMemory ? (
                    <Edit2 className='w-4 h-4 text-white' />
                  ) : (
                    <Plus className='w-4 h-4 text-white' />
                  )}
                </div>
                <div>
                  <h3 className='text-base font-semibold text-gray-800'>
                    {editingMemory ? '编辑记忆' : '添加记忆'}
                  </h3>
                  <p className='text-xs text-gray-500'>
                    {editingMemory ? '修改已有的记忆内容' : '创建一条新的记忆'}
                  </p>
                </div>
              </div>
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className='p-2 rounded-lg hover:bg-gray-100 text-gray-400 hover:text-gray-600 transition-colors'
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
                  <label className='block text-sm font-medium text-gray-700'>
                    记忆内容
                  </label>
                  <span className='text-xs text-gray-400'>
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
                  className='w-full h-28 px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-gray-800 text-sm placeholder-gray-400 focus:outline-none focus:border-[var(--accent-sky)]/60 focus:ring-1.5 focus:ring-[var(--accent-sky)]/30 resize-none transition-all duration-200'
                  maxLength={500}
                />
              </div>

              {/* 类型选择 */}
              <div className='space-y-2.5'>
                <label className='block text-sm font-medium text-gray-700'>
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
                            : 'bg-gray-50 border border-gray-200 text-gray-600 hover:border-gray-300 hover:bg-gray-100'
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
                <label className='block text-sm font-medium text-gray-700'>
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
                            ? 'text-amber-500 bg-amber-50 scale-110'
                            : 'text-gray-300 hover:text-gray-400 hover:bg-gray-50'
                        }`}
                      >
                        <Star className={`w-5 h-5 ${i < currentImportance ? 'fill-current' : ''}`} />
                      </button>
                    )
                  })}
                  <span className='ml-2 text-sm text-gray-500 font-medium'>
                    {(editingMemory?.importance || 3)}/5
                  </span>
                  <span className='text-xs text-gray-400 ml-1'>
                    ({['最低', '较低', '中等', '较高', '最高'][(editingMemory?.importance || 3) - 1]})
                  </span>
                </div>
              </div>

              {/* 规则标记 */}
              <div className='flex items-center gap-3 p-3 bg-red-50/50 rounded-xl border border-red-100'>
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
                  className='w-4 h-4 rounded border-red-300 bg-white text-red-500 focus:ring-red-500/50 focus:ring-offset-0'
                />
                <div>
                  <label htmlFor='isRule' className='text-sm font-medium text-red-700 cursor-pointer'>
                    标记为规则
                  </label>
                  <p className='text-xs text-red-500 mt-0.5'>
                    规则类型的记忆会在匹配时强制应用
                  </p>
                </div>
              </div>
            </div>

            {/* 底部按钮 */}
            <div className='flex items-center justify-between px-5 py-4 border-t border-gray-100 bg-gray-50/50'>
              <div className='text-xs text-gray-500'>
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
                  className='px-5 py-2.5 text-sm font-medium text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-xl transition-all duration-200'
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
                      ? 'bg-[var(--accent-sky)] text-white hover:bg-[var(--accent-sky)]/90 shadow-md hover:shadow-lg'
                      : 'bg-gray-200 text-gray-400 cursor-not-allowed'
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
