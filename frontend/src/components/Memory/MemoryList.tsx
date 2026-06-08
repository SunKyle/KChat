import { MEMORY_TYPES } from '../../types'
import type { Memory } from '../../types'
import { Icon } from '../Icon'

interface MemoryListProps {
  memories: Memory[]
  selectedMemories: number[]
  onSelect: (id: number) => void
  onEdit: (memory: Memory) => void
  onDelete: (id: number) => void
}

export default function MemoryList({
  memories,
  selectedMemories,
  onSelect,
  onEdit,
  onDelete,
}: MemoryListProps) {
  const getTypeInfo = (type: string) => MEMORY_TYPES.find((t) => t.type === type) || MEMORY_TYPES[0]

  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    })
  }

  return (
    <div className='space-y-3'>
      {memories.map((memory) => {
        const typeInfo = getTypeInfo(memory.type)
        const isSelected = selectedMemories.includes(memory.id)

        return (
          <div
            key={memory.id}
            className={`p-4 card-inset rounded-lg border transition-all cursor-pointer group ${
              isSelected
                ? 'border-[var(--accent-sky)] bg-[var(--accent-sky)]/10'
                : 'theme-border-primary hover:theme-bg-hover'
            }`}
            onClick={() => onSelect(memory.id)}
          >
            <div className='flex items-start gap-3'>
              <input
                type='checkbox'
                checked={isSelected}
                onChange={(e) => {
                  e.stopPropagation()
                  onSelect(memory.id)
                }}
                className='mt-1 rounded theme-border-primary theme-bg-card'
              />

              <div className='flex-1 min-w-0'>
                <div className='flex items-center gap-2 mb-1'>
                  <span
                    className={`badge px-2 py-0.5 text-xs ${typeInfo.color} text-white flex items-center gap-1`}
                  >
                    <Icon name={typeInfo.icon} size={12} />
                    {typeInfo.label}
                  </span>
                  {memory.isRule && (
                    <span className='badge flex items-center gap-1 px-2 py-0.5 text-xs bg-red-500/20 text-red-400'>
                      <Icon name='AlertCircle' size={12} />
                      规则
                    </span>
                  )}
                  {memory.score !== undefined && (
                    <span className='text-xs text-slate-500'>
                      相似度: {(memory.score * 100).toFixed(1)}%
                    </span>
                  )}
                </div>
                <p className='text-white text-sm leading-relaxed line-clamp-3'>{memory.content}</p>
                <div className='flex items-center gap-4 mt-2 text-xs text-slate-500'>
                  <span>{formatDate(memory.createdAt)}</span>
                  <span className='flex items-center gap-1'>
                    {Array.from({ length: 5 }).map((_, i) => (
                      <Icon
                        key={i}
                        name='Star'
                        size={12}
                        className={
                          i < memory.importance
                            ? 'text-yellow-400 fill-yellow-400'
                            : 'text-slate-600'
                        }
                      />
                    ))}
                  </span>
                </div>
              </div>

              <div className='flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity'>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    onEdit(memory)
                  }}
                  className='icon-btn'
                  title='编辑'
                >
                  <Icon name='Edit2' size={16} />
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    onDelete(memory.id)
                  }}
                  className='icon-btn hover:text-red-400'
                  title='删除'
                >
                  <Icon name='Trash2' size={16} />
                </button>
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
