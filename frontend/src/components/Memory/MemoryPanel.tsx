import { useState, useEffect } from 'react'
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
} from 'lucide-react'
import { memoryApi } from '../../utils/memoryApi'
import { MEMORY_TYPES } from '../../types'
import type { Memory, MemoryType } from '../../types'

const typeIcons: Record<
  string,
  React.ComponentType<{ size?: number; className?: string }>
> = {
  KNOWLEDGE: BookOpen,
  RULE: FileText,
  FACT: CheckCircle,
  PREFERENCE: Heart,
  EXPERIENCE: Lightbulb,
}

const typeColors: Record<string, string> = {
  KNOWLEDGE: 'bg-blue-500',
  RULE: 'bg-red-500',
  FACT: 'bg-green-500',
  PREFERENCE: 'bg-purple-500',
  EXPERIENCE: 'bg-orange-500',
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
  const [debounceRef, setDebounceRef] = useState<ReturnType<
    typeof setTimeout
  > | null>(null)

  const userId = 'default'

  useEffect(() => {
    loadMemories()
    return () => {
      if (debounceRef) clearTimeout(debounceRef)
    }
  }, [])

  const loadMemories = async () => {
    setLoading(true)
    try {
      const data = await memoryApi.getAll(userId)
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
      const result = await memoryApi.recall({ userId, query, topK: 20 })
      setMemories(result.memories)
    } catch (error) {
      console.error('搜索失败:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSearchChange = (value: string) => {
    setSearchQuery(value)
    if (debounceRef) clearTimeout(debounceRef)
    const timer = setTimeout(() => handleSearch(value), 300)
    setDebounceRef(timer)
  }

  const handleDelete = async (id: number) => {
    try {
      await memoryApi.delete(id)
      setMemories(memories.filter((m) => m.id !== id))
      setSelectedMemories(selectedMemories.filter((i) => i !== id))
    } catch (error) {
      console.error('删除失败:', error)
    }
  }

  const handleBatchDelete = async () => {
    for (const id of selectedMemories) {
      await memoryApi.delete(id)
    }
    setMemories(memories.filter((m) => !selectedMemories.includes(m.id)))
    setSelectedMemories([])
  }

  const handleSubmit = async (
    memory: Memory | Omit<Memory, 'id' | 'createdAt'>,
  ) => {
    const data = {
      userId: 'default',
      content: (memory as Memory).content,
      type: (memory as Memory).type,
      importance: (memory as Memory).importance,
    }

    try {
      if ('id' in memory) {
        const updatedMemory = await memoryApi.update(
          (memory as Memory).id,
          data,
        )
        setMemories(
          memories.map((m) =>
            m.id === (memory as Memory).id ? updatedMemory : m,
          ),
        )
      } else {
        const newMemory = await memoryApi.create(data)
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
    selectedType === 'ALL'
      ? memories
      : memories.filter((m) => m.type === selectedType)

  const isSelectAll =
    selectedMemories.length === filteredMemories.length &&
    filteredMemories.length > 0

  const getTypeIcon = (type: string) => typeIcons[type] || BookOpen
  const getTypeColor = (type: string) => typeColors[type] || 'text-slate-400'

  return (
    <div className="flex flex-col h-full space-y-4">
      {/* 搜索和操作栏 */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => handleSearchChange(e.target.value)}
            placeholder="搜索记忆..."
            className="w-full pl-9 pr-4 py-2 bg-slate-800/30 border border-white/5 rounded-lg text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50"
          />
        </div>
        <div className="relative">
          <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <select
            value={selectedType}
            onChange={(e) =>
              setSelectedType(e.target.value as MemoryType | 'ALL')
            }
            className="pl-9 pr-6 py-2 bg-slate-800/30 border border-white/5 rounded-lg text-white text-sm focus:outline-none focus:border-blue-500/50 appearance-none cursor-pointer"
          >
            <option value="ALL">全部</option>
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
          className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium transition-colors"
        >
          <Plus className="w-4 h-4" />
          添加
        </button>
      </div>

      {/* 批量操作栏 */}
      {selectedMemories.length > 0 && (
        <div className="flex items-center gap-3 px-3 py-2 bg-slate-800/20 rounded-lg">
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={isSelectAll}
              onChange={(e) => {
                if (e.target.checked) {
                  setSelectedMemories(filteredMemories.map((m) => m.id))
                } else {
                  setSelectedMemories([])
                }
              }}
              className="w-3.5 h-3.5 rounded border-white/10"
            />
            <span className="text-slate-400 text-xs">
              已选 {selectedMemories.length}
            </span>
          </label>
          <button
            onClick={handleBatchDelete}
            className="flex items-center gap-1 px-3 py-1 bg-red-600/80 hover:bg-red-600 text-white rounded text-xs transition-colors"
          >
            <Trash2 className="w-3 h-3" />
            删除
          </button>
        </div>
      )}

      {/* 记忆列表 */}
      <div className="space-y-0.5 flex-1 overflow-y-auto min-h-0">
        {loading ? (
          <div className="flex items-center justify-center py-6">
            <div className="w-5 h-5 border border-slate-600 border-t-blue-500 rounded-full animate-spin"></div>
          </div>
        ) : filteredMemories.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-slate-500">
            <Search className="w-6 h-6 mb-2 opacity-30" />
            <p className="text-xs">暂无记忆</p>
            <button
              onClick={() => {
                setEditingMemory(null)
                setShowForm(true)
              }}
              className="mt-2 text-blue-400 text-xs"
            >
              添加一条
            </button>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredMemories.map((memory) => {
              const TypeIcon = getTypeIcon(memory.type)
              const colorClass = getTypeColor(memory.type)
              const isSelected = selectedMemories.includes(memory.id)

              return (
                <div
                  key={memory.id}
                  className={`group flex items-center justify-between p-4 bg-white/[0.02] rounded-xl border transition-all duration-200 cursor-pointer ${
                    isSelected
                      ? 'border-blue-500/50 bg-blue-500/10'
                      : 'border-white/5 hover:border-white/10'
                  }`}
                  onClick={() =>
                    setSelectedMemories((prev) =>
                      prev.includes(memory.id)
                        ? prev.filter((i) => i !== memory.id)
                        : [...prev, memory.id],
                    )
                  }
                >
                  <div className="flex items-center gap-3">
                    {/* 选中标记 */}
                    <div className="w-4 h-4 flex-shrink-0">
                      {isSelected ? (
                        <div className="w-4 h-4 rounded-full bg-blue-500 flex items-center justify-center">
                          <svg
                            className="w-3 h-3 text-white"
                            fill="none"
                            stroke="currentColor"
                            viewBox="0 0 24 24"
                          >
                            <path
                              strokeLinecap="round"
                              strokeLinejoin="round"
                              strokeWidth={3}
                              d="M5 13l4 4L19 7"
                            />
                          </svg>
                        </div>
                      ) : selectedMemories.length > 0 ? (
                        <div className="w-4 h-4 rounded border-2 border-white/20" />
                      ) : null}
                    </div>

                    {/* 类型图标 */}
                    <div
                      className={`w-8 h-8 rounded-lg ${colorClass.replace('text-', 'bg-')} flex items-center justify-center`}
                    >
                      <TypeIcon className="w-4 h-4 text-white" />
                    </div>

                    {/* 内容 */}
                    <div className="min-w-0">
                      <h4
                        className={`text-sm font-medium truncate transition-colors ${
                          isSelected ? 'text-white' : 'text-slate-200'
                        }`}
                      >
                        {memory.content}
                      </h4>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-slate-500">
                          {getTypeInfo(memory.type)?.label}
                        </span>
                        {memory.isRule && (
                          <span className="text-xs text-red-400">规则</span>
                        )}
                        {memory.score !== undefined && (
                          <span className="text-xs text-slate-600">
                            {(memory.score * 100).toFixed(0)}%
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* 操作按钮 */}
                  <div
                    className={`flex items-center gap-2 transition-opacity ${
                      isSelected
                        ? 'opacity-100'
                        : 'opacity-0 group-hover:opacity-100'
                    }`}
                  >
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleEdit(memory)
                      }}
                      className="p-1.5 hover:bg-white/10 rounded-lg text-slate-400 hover:text-white transition-colors"
                      title="编辑"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation()
                        handleDelete(memory.id)
                      }}
                      className="p-1.5 hover:bg-red-500/20 rounded-lg text-slate-400 hover:text-red-400 transition-colors"
                      title="删除"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 添加/编辑表单弹窗 */}
      {showForm && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          onClick={() => {
            setShowForm(false)
            setEditingMemory(null)
          }}
        >
          <div
            className="w-full max-w-md bg-dark-700 rounded-xl border border-white/10 overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-4 border-b border-white/5">
              <h3 className="text-sm font-medium text-white">
                {editingMemory ? '编辑记忆' : '添加记忆'}
              </h3>
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className="p-1 text-slate-400 hover:text-white"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
            </div>

            <div className="p-4 space-y-4">
              <div>
                <label className="block text-xs text-slate-400 mb-1.5">
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
                  placeholder="输入记忆内容..."
                  className="w-full h-24 px-3 py-2 bg-slate-800/50 border border-white/5 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:border-blue-500/50 resize-none"
                  maxLength={500}
                />
                <p className="text-xs text-slate-500 mt-1 text-right">
                  {(editingMemory?.content || '').length}/500
                </p>
              </div>

              <div>
                <label className="block text-xs text-slate-400 mb-1.5">
                  类型
                </label>
                <div className="flex flex-wrap gap-1.5">
                  {MEMORY_TYPES.map((t) => {
                    const TypeIcon = getTypeIcon(t.type)
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
                        className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs transition-colors ${
                          !editingMemory || editingMemory.type === t.type
                            ? `${getTypeColor(t.type)} bg-white/5`
                            : 'text-slate-400 hover:bg-white/5'
                        }`}
                      >
                        <TypeIcon className="w-3 h-3" />
                        {t.label}
                      </button>
                    )
                  })}
                </div>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="isRule"
                  checked={editingMemory?.isRule || false}
                  onChange={(e) => {
                    if (editingMemory) {
                      setEditingMemory({
                        ...editingMemory,
                        isRule: e.target.checked,
                      })
                    }
                  }}
                  className="w-3.5 h-3.5 rounded border-white/10"
                />
                <label
                  htmlFor="isRule"
                  className="text-xs text-slate-400 cursor-pointer"
                >
                  标记为规则
                </label>
              </div>
            </div>

            <div className="flex justify-end gap-2 p-4 border-t border-white/5">
              <button
                onClick={() => {
                  setShowForm(false)
                  setEditingMemory(null)
                }}
                className="px-4 py-1.5 text-slate-400 hover:text-white text-sm"
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
                className={`px-4 py-1.5 rounded text-sm font-medium transition-colors ${
                  editingMemory?.content.trim()
                    ? 'bg-blue-600 hover:bg-blue-700 text-white'
                    : 'bg-slate-700 text-slate-500 cursor-not-allowed'
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
