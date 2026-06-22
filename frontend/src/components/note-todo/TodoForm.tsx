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

const priorityMeta = {
  high: { label: '高', text: 'text-[var(--brand-danger)]' },
  medium: { label: '中', text: 'text-[var(--accent-amber)]' },
  low: { label: '低', text: 'text-[var(--brand-success)]' },
} as const

interface TodoFormProps {
  formState: FormState
  setFormState: React.Dispatch<React.SetStateAction<FormState>>
  categories: string[]
  isEditing: boolean
  onCancel: () => void
  onSubmit: () => void
}

export function TodoForm({ formState, setFormState, categories, isEditing, onCancel, onSubmit }: TodoFormProps) {
  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-1 overflow-y-auto p-4 space-y-5'>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>标题</label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            placeholder='输入待办标题'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>描述</label>
          <textarea
            value={formState.description}
            onChange={(e) => setFormState((p) => ({ ...p, description: e.target.value }))}
            rows={4}
            placeholder='描述待办内容...'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all resize-none leading-relaxed'
          />
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>分类与优先级</label>
          <div className='flex items-center gap-2'>
            <select
              value={formState.category}
              onChange={(e) => setFormState((p) => ({ ...p, category: e.target.value }))}
              className='flex-1 px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/40 transition-colors'
            >
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <div className='flex gap-1.5'>
              {(['high', 'medium', 'low'] as const).map((pr) => (
                <button
                  key={pr}
                  onClick={() => setFormState((p) => ({ ...p, priority: pr }))}
                  className={`w-10 py-2.5 rounded-xl text-xs font-semibold transition-all border ${
                    formState.priority === pr
                      ? `${priorityMeta[pr].text} border-current/20 bg-[var(--bg-hover)]`
                      : 'text-[var(--text-muted)] border-transparent hover:border-[var(--border-primary)]'
                  }`}
                >
                  {priorityMeta[pr].label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>截止日期</label>
          <input
            type='date'
            value={formState.dueDate}
            onChange={(e) => setFormState((p) => ({ ...p, dueDate: e.target.value }))}
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2.5 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button onClick={onCancel} aria-label='取消' className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-sm font-semibold hover:bg-[var(--bg-input)] transition-colors'>
          取消
        </button>
        <button onClick={onSubmit} aria-label={isEditing ? '保存' : '创建'} className='px-5 py-2 rounded-xl text-sm font-semibold text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all shadow-sm shadow-[var(--brand-primary)]/20'>
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}