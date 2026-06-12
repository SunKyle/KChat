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
}

const typeColors: Record<string, string> = {
  KNOWLEDGE: 'bg-[var(--brand-info)]',
  RULE: 'bg-[var(--brand-danger)]',
  FACT: 'bg-[var(--brand-success)]',
  PREFERENCE: 'bg-[var(--accent-purple)]',
  EXPERIENCE: 'bg-[var(--accent-orange)]',
}

const getTypeInfo = (type: string) => {
  return MEMORY_TYPES.find((t) => t.type === type)
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
    }

    try {
      if ('id' in memoryData) {
        const updatedMemory = await memory.update((memoryData as Memory).id, data)
        setMemories(memories.map((m) => (m.id === (memoryData as Memory).id ? updatedMemory : m)))
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

        <div className='flex items-center gap-3 w-full sm:w-auto'>
          <div className='flex-1 sm:flex-initial relative'>
            <Search className='absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 theme-text-muted' />
            <input
              type='text'
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              placeholder='搜索记忆...'
              className='input-field w-full sm:w-48 pl-9 pr-4 py-2 text-sm placeholder-theme-text-placeholder'
            />
          </div>

          <div className='relative'>
            <Filter className='absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 theme-text-muted' />
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as MemoryType | 'ALL')}
              className='input-field pl-9 pr-6 py-2 text-sm appearance-none cursor-pointer min-w-[80px]'
            >
              <option value='ALL'>全部</option>
              {MEMORY_TYPES.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={() => {
              setEditingMemory(null)
              setShowForm(true)
            }}
            className='flex items-center gap-2 btn-primary whitespace-nowrap'
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
        <div className='card-float-solid rounded-2xl p-4'>
          {selectedMemories.length > 0 && (
            <div className='flex items-center gap-3 px-4 py-3 theme-bg-input/50 rounded-xl mb-4'>
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
                  className='w-3.5 h-3.5 rounded border-theme-border-primary'
                />
                <span className='theme-text-muted text-xs'>已选 {selectedMemories.length}</span>
              </label>
              <button
                onClick={handleBatchDelete}
                className='flex items-center gap-1 px-3 py-1.5 theme-bg-brand-danger/80 hover:bg-[var(--brand-danger)] text-white rounded-lg text-xs transition-colors'
              >
                <Trash2 className='w-3 h-3' />
                删除
              </button>
            </div>
          )}

          <div className='space-y-3 max-h-[calc(100vh-380px)] overflow-y-auto scrollbar-hidden'>
            {filteredMemories.map((memory) => {
              const TypeIcon = getTypeIcon(memory.type)
              const colorClass = getTypeColor(memory.type)
              const isSelected = selectedMemories.includes(memory.id)

              return (
                <div
                  key={memory.id}
                  className={`group flex items-center justify-between p-4 card-inset rounded-xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'border-[var(--accent-sky)]/50 bg-[var(--accent-sky)]/10'
                      : 'border-theme-border-primary hover:border-theme-border-secondary'
                  }`}
                  onClick={() =>
                    setSelectedMemories((prev) =>
                      prev.includes(memory.id)
                        ? prev.filter((i) => i !== memory.id)
                        : [...prev, memory.id]
                    )
                  }
                >
                  <div className='flex items-center gap-3'>
                    <div className='w-4 h-4 flex-shrink-0'>
                      {isSelected ? (
                        <div className='w-4 h-4 rounded-full bg-[var(--accent-sky)] flex items-center justify-center'>
                          <svg
                            className='w-3 h-3 text-white'
                            fill='none'
                            stroke='currentColor'
                            viewBox='0 0 24 24'
                          >
                            <path
                              strokeLinecap='round'
                              strokeLinejoin='round'
                              strokeWidth={3}
                              d='M5 13l4 4L19 7'
                            />
                          </svg>
                        </div>
                      ) : selectedMemories.length > 0 ? (
                        <div className='w-4 h-4 rounded border-2 border-theme-border-primary' />
                      ) : null}
                    </div>

                    <div
                      className={`w-8 h-8 rounded-lg ${colorClass} flex items-center justify-center`}
                    >
                      <TypeIcon className='w-4 h-4 text-white' />
                    </div>

                    <div className='min-w-0 flex-1'>
                      <h4
                        className={`text-sm font-medium truncate transition-colors ${
                          isSelected ? 'theme-text-primary' : 'theme-text-secondary'
                        }`}
                      >
                        {memory.content}
                      </h4>
                      <div className='flex items-center gap-2 mt-0.5'>
                        <span className='text-xs theme-text-muted'>
                          {getTypeInfo(memory.type)?.label}
                        </span>
                        {memory.isRule && <span className='text-xs theme-brand-danger'>规则</span>}
                        {memory.score !== undefined && (
                          <span className='text-xs theme-text-muted/70'>
                            {(memory.score * 100).toFixed(0)}%
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div
                    className={`flex items-center gap-2 transition-opacity ${
                      isSelected ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                    }`}
                  >
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleEdit(memory)
                      }}
                      className='icon-btn'
                      title='编辑'
                    >
                      <Edit2 className='w-4 h-4' />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDelete(memory.id)
                      }}
                      className='icon-btn hover:text-[var(--brand-danger)]'
                      title='删除'
                    >
                      <Trash2 className='w-4 h-4' />
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {showForm && (
        <div
          className='fixed inset-0 bg-black/60 backdrop-blur-md flex items-center justify-center z-50 p-4'
          onClick={() => {
            setShowForm(false)
            setEditingMemory(null)
          }}
        >
          <div
            className='w-full max-w-md theme-bg-card rounded-2xl border theme-border-primary overflow-hidden'
            onClick={(e) => e.stopPropagation()}
          >
            <div className='flex items-center justify-between p-4 border-b theme-border-primary'>
              <h3 className='text-base font-semibold theme-text-primary'>
                {editingMemory ? '编辑记忆' : '添加记忆'}
              </h3>
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className='icon-btn'
              >
                <svg className='w-5 h-5' fill='none' stroke='currentColor' viewBox='0 0 24 24'>
                  <path
                    strokeLinecap='round'
                    strokeLinejoin='round'
                    strokeWidth={2}
                    d='M6 18L18 6M6 6l12 12'
                  />
                </svg>
              </button>
            </div>

            <div className='p-4 space-y-4'>
              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-1.5'>
                  内容
                </label>
                <textarea
                  value={editingMemory?.content || ''}
                  onChange={(e) => {
                    if (editingMemory) {
                      setEditingMemory({
                        ...editingMemory,
                        content: e.target.value,
                      })
                    }
                  }}
                  placeholder='输入记忆内容...'
                  className='w-full h-24 px-3 py-2 theme-bg-input border theme-border-primary rounded-lg theme-text-primary text-sm placeholder-theme-text-placeholder focus:outline-none focus:border-[var(--accent-sky)]/50 resize-none'
                  maxLength={500}
                />
                <p className='text-xs theme-text-muted mt-1 text-right'>
                  {(editingMemory?.content || '').length}/500
                </p>
              </div>

              <div>
                <label className='block text-sm font-medium theme-text-secondary mb-3'>类型</label>
                <div className='flex flex-wrap gap-2'>
                  {MEMORY_TYPES.map((t) => {
                    const TypeIcon = getTypeIcon(t.type)
                    const isSelected = !editingMemory || editingMemory.type === t.type
                    return (
                      <button
                        key={t.type}
                        onClick={() => {
                          if (editingMemory) {
                            setEditingMemory({
                              ...editingMemory,
                              type: t.type as MemoryType,
                            })
                          }
                        }}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs transition-all ${
                          isSelected
                            ? `${getTypeColor(t.type)} text-white`
                            : 'theme-bg-input border theme-border-primary hover:theme-border-secondary hover:theme-text-secondary'
                        }`}
                      >
                        <TypeIcon className='w-3 h-3' />
                        {t.label}
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className='flex items-center gap-2.5'>
                <input
                  type='checkbox'
                  id='isRule'
                  checked={editingMemory?.isRule || false}
                  onChange={(e) => {
                    if (editingMemory) {
                      setEditingMemory({
                        ...editingMemory,
                        isRule: e.target.checked,
                      })
                    }
                  }}
                  className='w-4 h-4 rounded border-theme-border-secondary theme-bg-input text-[var(--brand-danger)] focus:ring-[var(--brand-danger)]/50'
                />
                <label htmlFor='isRule' className='text-sm theme-text-muted cursor-pointer'>
                  标记为规则
                </label>
              </div>
            </div>

            <div className='flex justify-end gap-2 p-4 border-t theme-border-primary'>
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className='px-4 py-2 theme-bg-hover rounded-lg hover:theme-bg-hover/80 transition-colors text-sm'
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
                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                  editingMemory?.content.trim()
                    ? 'theme-bg-accent-sky text-white hover:bg-[var(--accent-sky)]/80'
                    : 'theme-bg-hover/50 theme-text-muted cursor-not-allowed'
                }`}
              >
                {editingMemory ? '保存' : '添加'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
