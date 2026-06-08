import { useState, useEffect } from 'react'
import { MEMORY_TYPES } from '../../types'
import type { Memory, MemoryType } from '../../types'
import { Icon } from '../Icon'

interface MemoryFormProps {
  memory: Memory | null
  onSubmit: (memory: Memory | Omit<Memory, 'id' | 'createdAt'>) => void
  onCancel: () => void
}

export default function MemoryForm({ memory, onSubmit, onCancel }: MemoryFormProps) {
  const [content, setContent] = useState('')
  const [type, setType] = useState<MemoryType>('KNOWLEDGE')
  const [importance, setImportance] = useState(3)
  const [isRule, setIsRule] = useState(false)

  useEffect(() => {
    if (memory) {
      setContent(memory.content)
      setType(memory.type as MemoryType)
      setImportance(memory.importance || 3)
      setIsRule(memory.isRule || false)
    } else {
      setContent('')
      setType('KNOWLEDGE')
      setImportance(3)
      setIsRule(false)
    }
  }, [memory])

  const handleSubmit = () => {
    if (!content.trim()) return

    if (memory) {
      onSubmit({
        ...memory,
        content: content.trim(),
        type,
        importance,
        isRule,
      })
    } else {
      onSubmit({
        userId: 'default',
        content: content.trim(),
        type,
        importance,
        isRule,
      })
    }
  }

  return (
    <div className='fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4'>
      <div className='w-full max-w-lg theme-bg-card rounded-xl border theme-border-primary shadow-2xl'>
        <div className='flex items-center justify-between p-4 border-b theme-border-primary'>
          <h3 className='text-lg font-semibold theme-text-primary'>
            {memory ? '编辑记忆' : '添加记忆'}
          </h3>
          <button
            onClick={onCancel}
            className='p-1 hover:theme-bg-hover rounded-lg theme-text-muted hover:theme-text-primary transition-colors'
          >
            <Icon name='X' size={20} />
          </button>
        </div>

        <div className='p-4 space-y-4'>
          <div>
            <label className='block text-sm font-medium theme-text-secondary mb-2'>
              内容 <span className='text-red-400'>*</span>
            </label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder='输入记忆内容...'
              className='w-full h-32 px-4 py-3 theme-bg-input theme-text-primary border theme-border-primary rounded-lg focus:outline-none focus:border-blue-500 resize-none'
              maxLength={500}
            />
            <p className='text-xs theme-text-muted mt-1 text-right'>{content.length}/500</p>
          </div>

          <div>
            <label className='block text-sm font-medium theme-text-secondary mb-2'>类型</label>
            <div className='flex flex-wrap gap-2'>
              {MEMORY_TYPES.map((t) => (
                <button
                  key={t.type}
                  onClick={() => setType(t.type)}
                  className={`flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm transition-all ${
                    type === t.type
                      ? `${t.color} text-white`
                      : 'theme-bg-input theme-text-muted hover:theme-bg-hover'
                  }`}
                >
                  <Icon name={t.icon} size={14} />
                  {t.label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className='block text-sm font-medium theme-text-secondary mb-2'>重要性</label>
            <div className='flex items-center gap-1'>
              {Array.from({ length: 5 }).map((_, i) => (
                <button
                  key={i}
                  onClick={() => setImportance(i + 1)}
                  className='p-2 hover:theme-bg-hover rounded-lg transition-colors'
                >
                  <Icon
                    name='Star'
                    size={24}
                    className={
                      i < importance ? 'text-yellow-400 fill-yellow-400' : 'theme-text-muted'
                    }
                  />
                </button>
              ))}
              <span className='ml-2 text-sm theme-text-muted'>{importance} 星</span>
            </div>
          </div>

          <div className='flex items-center gap-3'>
            <input
              type='checkbox'
              id='isRule'
              checked={isRule}
              onChange={(e) => setIsRule(e.target.checked)}
              className='w-4 h-4 rounded border-theme-border-secondary theme-bg-input'
            />
            <label
              htmlFor='isRule'
              className='flex items-center gap-2 text-sm theme-text-secondary'
            >
              <Icon name='AlertCircle' size={16} className='text-red-400' />
              标记为规则
              <span className='text-xs theme-text-muted'>(AI 将优先遵循此记忆)</span>
            </label>
          </div>
        </div>

        <div className='flex justify-end gap-3 p-4 border-t theme-border-primary'>
          <button
            onClick={onCancel}
            className='px-4 py-2 theme-text-secondary hover:theme-text-primary hover:theme-bg-hover rounded-lg transition-colors'
          >
            取消
          </button>
          <button
            onClick={handleSubmit}
            disabled={!content.trim()}
            className={`px-4 py-2 rounded-lg transition-colors ${
              content.trim()
                ? 'bg-blue-600 hover:bg-blue-700 text-white'
                : 'theme-bg-hover theme-text-muted cursor-not-allowed'
            }`}
          >
            {memory ? '保存修改' : '创建记忆'}
          </button>
        </div>
      </div>
    </div>
  )
}
