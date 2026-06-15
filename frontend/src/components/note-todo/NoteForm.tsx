import { X, Pin } from 'lucide-react'

interface FormState {
  title: string
  content: string
  category: string
  tags: string[]
  newTag: string
  pinned: boolean
  description: string
  priority: 'high' | 'medium' | 'low'
  dueDate: string
}

interface NoteFormProps {
  formState: FormState
  setFormState: React.Dispatch<React.SetStateAction<FormState>>
  categories: string[]
  isEditing: boolean
  onCancel: () => void
  onSubmit: () => void
}

export function NoteForm({
  formState,
  setFormState,
  categories,
  isEditing,
  onCancel,
  onSubmit,
}: NoteFormProps) {
  const handleAddTag = (e: React.KeyboardEvent) => {
    if (
      e.key === 'Enter' &&
      formState.newTag.trim() &&
      !formState.tags.includes(formState.newTag.trim())
    ) {
      e.preventDefault()
      setFormState((prev) => ({ ...prev, tags: [...prev.tags, prev.newTag.trim()], newTag: '' }))
    }
  }

  const handleRemoveTag = (tag: string) => {
    setFormState((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }))
  }

  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-1 overflow-y-auto p-4 space-y-5'>
        <div>
          <label className='block text-[12px] font-medium text-[var(--text-secondary)] mb-2'>
            标题
          </label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            placeholder='输入笔记标题'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
        <div>
          <label className='block text-[12px] font-medium text-[var(--text-secondary)] mb-2'>
            内容
          </label>
          <textarea
            value={formState.content}
            onChange={(e) => setFormState((p) => ({ ...p, content: e.target.value }))}
            rows={8}
            placeholder='输入笔记内容...'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all resize-none leading-relaxed'
          />
        </div>
        <div>
          <label className='block text-[12px] font-medium text-[var(--text-secondary)] mb-2'>
            分类
          </label>
          <div className='flex items-center gap-2'>
            <select
              value={formState.category}
              onChange={(e) => setFormState((p) => ({ ...p, category: e.target.value }))}
              className='flex-1 px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/40 transition-colors'
            >
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <button
              onClick={() => setFormState((p) => ({ ...p, pinned: !p.pinned }))}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-[13px] font-medium transition-all ${
                formState.pinned
                  ? 'bg-[var(--accent-amber)]/[0.12] text-[var(--accent-amber)] border border-[var(--accent-amber)]/25'
                  : 'bg-[var(--bg-input)] text-[var(--text-muted)] border border-transparent hover:text-[var(--text-secondary)]'
              }`}
            >
              <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />
              置顶
            </button>
          </div>
        </div>
        <div>
          <label className='block text-[12px] font-medium text-[var(--text-secondary)] mb-2'>标签</label>
          {formState.tags.length > 0 && (
            <div className='flex flex-wrap gap-1.5 mb-2.5'>
              {formState.tags.map((tag) => (
                <span
                  key={tag}
                  className='flex items-center gap-1 px-2.5 py-1 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[11px] font-medium'
                >
                  {tag}
                  <button onClick={() => handleRemoveTag(tag)} className='hover:opacity-70 transition-opacity'>
                    <X className='w-3 h-3' />
                  </button>
                </span>
              ))}
            </div>
          )}
          <input
            type='text'
            value={formState.newTag}
            onChange={(e) => setFormState((p) => ({ ...p, newTag: e.target.value }))}
            onKeyDown={handleAddTag}
            placeholder='输入标签，按 Enter 添加'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2.5 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button
          onClick={onCancel}
          className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'
        >
          取消
        </button>
        <button
          onClick={onSubmit}
          className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all shadow-sm shadow-[var(--brand-primary)]/20'
        >
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}
