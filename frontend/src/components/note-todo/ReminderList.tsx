import { Icon } from '../../components/common/Icon'
import type { Reminder } from '../../types/note-todo'

interface ReminderListItemProps {
  reminder: Reminder
  onEdit: () => void
  onDelete: () => void
  formatRemindAt: (iso: string) => string
  formatDateFull: (dateString: string) => string
}

const statusMeta = {
  pending: {
    icon: 'Bell',
    label: '待触发',
    className: 'bg-[var(--brand-primary)]/10 text-[var(--brand-primary)] border-[var(--brand-primary)]/30',
  },
  fired: {
    icon: 'BellRing',
    label: '已触发',
    className: 'bg-[var(--brand-success)]/10 text-[var(--brand-success)] border-[var(--brand-success)]/30',
  },
  cancelled: {
    icon: 'BellOff',
    label: '已取消',
    className: 'bg-[var(--text-muted)]/10 text-[var(--text-muted)] border-[var(--text-muted)]/30',
  },
} as const

function ReminderListItem({
  reminder,
  onEdit,
  onDelete,
  formatRemindAt,
  formatDateFull,
}: ReminderListItemProps) {
  const meta = statusMeta[reminder.status]
  return (
    <div className='group relative rounded-xl border border-[var(--border-primary)] bg-[var(--bg-card)] hover:shadow-md hover:shadow-[var(--shadow-color)]/15 hover:border-[var(--brand-primary)]/30 transition-all duration-200 cursor-pointer overflow-hidden'>
      <div className='p-4 flex items-start gap-3'>
        <div
          className={`flex-shrink-0 mt-0.5 w-8 h-8 rounded-full flex items-center justify-center ${meta.className}`}
        >
          <Icon name={meta.icon} size='md' />
        </div>
        <div className='flex-1 min-w-0'>
          <h3
            className={`text-base font-semibold leading-tight ${
              reminder.status === 'pending'
                ? 'text-[var(--text-primary)]'
                : 'text-[var(--text-muted)]'
            }`}
          >
            {reminder.title}
          </h3>
          {reminder.description && (
            <p className='text-sm text-[var(--text-muted)] line-clamp-2 mt-1 leading-relaxed'>
              {reminder.description}
            </p>
          )}
          <div className='flex items-center justify-between mt-3 flex-wrap gap-2'>
            <div className='flex items-center gap-2 flex-wrap'>
              <span
                className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold border ${meta.className}`}
              >
                <Icon name={meta.icon} size='xs' />
                {meta.label}
              </span>
              <span className='inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-[var(--bg-hover)] text-[var(--text-secondary)]'>
                <Icon name='Clock' size='xs' />
                {formatRemindAt(reminder.remindAt)}
              </span>
            </div>
            <span className='text-xs text-[var(--text-muted)]/60 flex-shrink-0'>
              {formatDateFull(reminder.createdAt)}
            </span>
          </div>
        </div>
      </div>
      <div className='absolute top-3 right-3 flex items-center gap-1 opacity-40 group-hover:opacity-100 transition-opacity'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onEdit()
          }}
          className='p-1.5 rounded-md hover:bg-[var(--bg-hover)] transition-colors'
          aria-label='编辑'
        >
          <Icon name='Pencil' size='sm' className='text-[var(--text-secondary)]' />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          className='p-1.5 rounded-md hover:bg-[var(--bg-hover)] transition-colors'
          aria-label='删除'
        >
          <Icon name='Trash2' size='sm' className='text-[var(--text-secondary)] hover:text-[var(--brand-danger)]' />
        </button>
      </div>
    </div>
  )
}

interface ReminderListProps {
  reminders: Reminder[]
  onEdit: (reminder: Reminder) => void
  onDelete: (id: string) => void
  formatRemindAt: (iso: string) => string
  formatDateFull: (dateString: string) => string
  onOpenCreate: () => void
}

export function ReminderList({
  reminders,
  onEdit,
  onDelete,
  formatRemindAt,
  formatDateFull,
  onOpenCreate,
}: ReminderListProps) {
  const pending = reminders.filter((r) => r.status === 'pending')
  const history = reminders.filter((r) => r.status !== 'pending')

  if (reminders.length === 0) {
    return (
      <div className='flex flex-col items-center justify-center py-16 px-4'>
        <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
          <Icon name='Bell' size='xl' className='text-[var(--text-muted)]/50' />
        </div>
        <p className='text-sm text-[var(--text-muted)]'>暂无提醒</p>
        <button
          onClick={onOpenCreate}
          className='mt-3 px-4 py-1.5 rounded-lg text-xs font-semibold text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'
        >
          新建提醒
        </button>
      </div>
    )
  }

  return (
    <div className='px-4 pt-4 pb-6 space-y-3'>
      {pending.length > 0 && (
        <div className='space-y-3'>
          {pending.map((reminder) => (
            <ReminderListItem
              key={reminder.id}
              reminder={reminder}
              onEdit={() => onEdit(reminder)}
              onDelete={() => onDelete(reminder.id)}
              formatRemindAt={formatRemindAt}
              formatDateFull={formatDateFull}
            />
          ))}
        </div>
      )}
      {history.length > 0 && (
        <div>
          <div className='flex items-center gap-2 px-0.5 pt-2 pb-3'>
            <div className='w-1 h-4 rounded-full bg-[var(--text-muted)]/40' />
            <span className='text-xs font-semibold text-[var(--text-muted)]'>历史</span>
          </div>
          <div className='space-y-3'>
            {history.map((reminder) => (
              <ReminderListItem
                key={reminder.id}
                reminder={reminder}
                onEdit={() => onEdit(reminder)}
                onDelete={() => onDelete(reminder.id)}
                formatRemindAt={formatRemindAt}
                formatDateFull={formatDateFull}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
