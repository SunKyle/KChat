import { Bell } from 'lucide-react'

interface FormState {
  title: string
  description: string
  remindAt: string
}

interface ReminderFormProps {
  formState: FormState
  setFormState: React.Dispatch<React.SetStateAction<FormState>>
  isEditing: boolean
  onCancel: () => void
  onSubmit: () => void
}

export function ReminderForm({
  formState,
  setFormState,
  isEditing,
  onCancel,
  onSubmit,
}: ReminderFormProps) {
  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-1 overflow-y-auto p-4 space-y-5'>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            标题
          </label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            placeholder='输入提醒标题'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            提醒时间
          </label>
          <input
            type='datetime-local'
            value={formState.remindAt}
            onChange={(e) => setFormState((p) => ({ ...p, remindAt: e.target.value }))}
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
          <p className='flex items-center gap-1 text-xs text-[var(--text-muted)] mt-1.5'>
            <Bell className='w-3 h-3' />
            到点后系统会通过通知推送提醒
          </p>
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            备注（可选）
          </label>
          <textarea
            value={formState.description}
            onChange={(e) => setFormState((p) => ({ ...p, description: e.target.value }))}
            rows={4}
            placeholder='补充提醒内容...'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all resize-none leading-relaxed'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2.5 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button
          onClick={onCancel}
          aria-label='取消'
          className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-sm font-semibold hover:bg-[var(--bg-input)] transition-colors'
        >
          取消
        </button>
        <button
          onClick={onSubmit}
          aria-label={isEditing ? '保存' : '创建'}
          className='px-5 py-2 rounded-xl text-sm font-semibold text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all shadow-sm shadow-[var(--brand-primary)]/20'
        >
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}
