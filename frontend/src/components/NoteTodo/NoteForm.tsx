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
      <div className='flex-1 overflow-y-auto p-4 space-y-4'>
        <div>
          <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
            标题
          </label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
          />
        </div>
        <div>
          <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
            内容
          </label>
          <textarea
            value={formState.content}
            onChange={(e) => setFormState((p) => ({ ...p, content: e.target.value }))}
            rows={10}
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none leading-relaxed'
          />
        </div>
        <div className='grid grid-cols-2 gap-3'>
          <div>
            <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>分类</label>
            <select
              value={formState.category}
              onChange={(e) => setFormState((p) => ({ ...p, category: e.target.value }))}
              className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
            >
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <button
            onClick={() => setFormState((p) => ({ ...p, pinned: !p.pinned }))}
            className={`flex items-center gap-2 px-3 py-2 rounded-xl text-[13px] font-medium transition-all justify-center mt-5 ${
              formState.pinned
                ? 'bg-[var(--accent-amber)]/[0.1] text-[var(--accent-amber)] border border-[var(--accent-amber)]/20'
                : 'bg-[var(--bg-input)] text-[var(--text-secondary)] border border-[var(--border-primary)]'
            }`}
          >
            <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />
            置顶
          </button>
        </div>
        <div>
          <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>标签</label>
          {formState.tags.length > 0 && (
            <div className='flex flex-wrap gap-1.5 mb-2'>
              {formState.tags.map((tag) => (
                <span
                  key={tag}
                  className='flex items-center gap-1 px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[11px] font-medium'
                >
                  {tag}
                  <button onClick={() => handleRemoveTag(tag)} className='hover:opacity-70'>
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
            className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button
          onClick={onCancel}
          className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'
        >
          取消
        </button>
        <button
          onClick={onSubmit}
          className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'
        >
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}
