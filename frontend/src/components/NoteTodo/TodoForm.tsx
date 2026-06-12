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
      <div className='flex-1 overflow-y-auto p-4 space-y-4'>
        <div>
          <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>标题</label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
          />
        </div>
        <div>
          <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>描述</label>
          <textarea
            value={formState.description}
            onChange={(e) => setFormState((p) => ({ ...p, description: e.target.value }))}
            rows={4}
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
          <div>
            <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>优先级</label>
            <div className='flex gap-1.5'>
              {(['high', 'medium', 'low'] as const).map((pr) => (
                <button
                  key={pr}
                  onClick={() => setFormState((p) => ({ ...p, priority: pr }))}
                  className={`flex-1 py-2 rounded-xl text-[12px] font-medium transition-all border ${
                    formState.priority === pr ? `${priorityMeta[pr].text} border-current/20 bg-[var(--bg-hover)]` : 'text-[var(--text-muted)] border-[var(--border-primary)]'
                  }`}
                >
                  {priorityMeta[pr].label}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div>
          <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>截止日期</label>
          <input
            type='date'
            value={formState.dueDate}
            onChange={(e) => setFormState((p) => ({ ...p, dueDate: e.target.value }))}
            className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button onClick={onCancel} className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'>
          取消
        </button>
        <button onClick={onSubmit} className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'>
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}