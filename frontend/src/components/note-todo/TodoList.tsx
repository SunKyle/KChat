import { ListTodo, CheckCircle2, Circle, Edit3, Calendar, Clock, Trash2 } from 'lucide-react'
import type { Todo } from '../../types/note-todo'

const priorityMeta = {
  high: {
    label: '高',
    bgColor: 'bg-[var(--brand-danger)]/10',
    textColor: 'text-[var(--brand-danger)]',
    borderColor: 'border-[var(--brand-danger)]/30',
  },
  medium: {
    label: '中',
    bgColor: 'bg-[var(--accent-amber)]/10',
    textColor: 'text-[var(--accent-amber)]',
    borderColor: 'border-[var(--accent-amber)]/30',
  },
  low: {
    label: '低',
    bgColor: 'bg-[var(--brand-success)]/10',
    textColor: 'text-[var(--brand-success)]',
    borderColor: 'border-[var(--brand-success)]/30',
  },
} as const

interface TodoListItemProps {
  todo: Todo
  onSelect: () => void
  onToggle: () => void
  onEdit: () => void
  onDelete: () => void
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
}

function TodoListItem({
  todo,
  onSelect,
  onToggle,
  onEdit,
  onDelete,
  formatDateFull,
  formatDate,
  isOverdue,
}: TodoListItemProps) {
  return (
    <div
      className='group relative rounded-xl border border-[var(--border-divider)] bg-[var(--bg-card)] hover:shadow-md hover:shadow-[var(--shadow-color)]/15 hover:border-[var(--border-primary)] transition-all duration-200 cursor-pointer overflow-hidden'
      onClick={onSelect}
    >
      <div className='p-4 flex items-start gap-3'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onToggle()
          }}
          className='flex-shrink-0 mt-0.5 transition-all hover:scale-110'
          aria-label={todo.status === 'completed' ? '标记为未完成' : '标记为完成'}
        >
          {todo.status === 'completed' ? (
            <CheckCircle2 className='w-[20px] h-[20px] text-[var(--brand-primary)]' />
          ) : (
            <Circle className='w-[20px] h-[20px] text-[var(--text-muted)]/40 hover:text-[var(--brand-primary)]' />
          )}
        </button>
        <div className='flex-1 min-w-0'>
          <h3
            className={`text-base font-semibold leading-tight ${todo.status === 'completed' ? 'text-[var(--text-muted)] line-through' : 'text-[var(--text-primary)]'}`}
          >
            {todo.title}
          </h3>
          {todo.description && (
            <p className='text-sm text-[var(--text-muted)] line-clamp-2 mt-2 leading-relaxed'>
              {todo.description}
            </p>
          )}
          <div className='flex items-center justify-between mt-3 flex-wrap gap-2'>
            <div className='flex items-center gap-2 flex-wrap'>
              <span
                className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${priorityMeta[todo.priority].bgColor} ${priorityMeta[todo.priority].textColor}`}
              >
                {priorityMeta[todo.priority].label}优先级
              </span>
              {todo.category === '工作' && (
                <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--brand-info)]/10 text-[var(--brand-info)]'>
                  {todo.category}
                </span>
              )}
              {todo.category === '学习' && (
                <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--brand-success)]/10 text-[var(--brand-success)]'>
                  {todo.category}
                </span>
              )}
              {todo.category === '生活' && (
                <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--accent-rose)]/10 text-[var(--accent-rose)]'>
                  {todo.category}
                </span>
              )}
              {todo.category === '默认' && (
                <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--bg-hover)] text-[var(--text-secondary)]'>
                  {todo.category}
                </span>
              )}
              {todo.dueDate && !isOverdue(todo.dueDate, todo.status) && (
                <span className='inline-flex items-center gap-0.5 px-2.5 py-1 rounded-full text-xs bg-[var(--bg-hover)] text-[var(--text-muted)]'>
                  <Calendar className='w-3 h-3' />
                  {formatDate(todo.dueDate)}
                </span>
              )}
              {isOverdue(todo.dueDate, todo.status) && (
                <span className='inline-flex items-center gap-0.5 px-2.5 py-1 rounded-full text-xs font-medium bg-[var(--brand-danger)]/5 text-[var(--brand-danger)]'>
                  <Clock className='w-3 h-3' />
                  已过期
                </span>
              )}
            </div>
            <span className='text-xs text-[var(--text-muted)]/60 flex-shrink-0'>
              {formatDateFull(todo.updatedAt)}
            </span>
          </div>
        </div>
      </div>
      <div className='absolute top-3 right-3 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onEdit()
          }}
          className='p-1.5 rounded-md bg-[var(--bg-glass)] backdrop-blur-sm hover:bg-[var(--bg-glass-hover)] shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--border-primary)] transition-all'
          aria-label='编辑'
        >
          <Edit3 className='w-3.5 h-3.5 text-[var(--text-secondary)]' />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          className='p-1.5 rounded-md bg-[var(--bg-glass)] backdrop-blur-sm hover:bg-[var(--brand-danger)]/10 shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--brand-danger)]/30 transition-all'
          aria-label='删除'
        >
          <Trash2 className='w-3.5 h-3.5 text-[var(--text-secondary)] hover:text-[var(--brand-danger)]' />
        </button>
      </div>
    </div>
  )
}

interface TodoListProps {
  todos: Todo[]
  activeTab: 'all' | 'pending' | 'completed'
  onSelect: (todo: Todo) => void
  onToggle: (id: string) => void
  onEdit: (todo: Todo) => void
  onDelete: (id: string) => void
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
  onOpenCreate: () => void
}

export function TodoList({
  todos,
  activeTab,
  onSelect,
  onToggle,
  onEdit,
  onDelete,
  formatDateFull,
  formatDate,
  isOverdue,
  onOpenCreate,
}: TodoListProps) {
  const filteredTodos = todos.filter((t) => activeTab === 'all' || t.status === activeTab)
  const pendingTodos = filteredTodos.filter((t) => t.status === 'pending')
  const completedTodos = filteredTodos.filter((t) => t.status === 'completed')

  if (filteredTodos.length === 0) {
    return (
      <div className='flex flex-col items-center justify-center py-16 px-4'>
        <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
          <ListTodo className='w-6 h-6 text-[var(--text-muted)]/50' />
        </div>
        <p className='text-sm text-[var(--text-muted)]'>暂无待办</p>
        <button
          onClick={onOpenCreate}
          className='mt-3 px-4 py-1.5 rounded-lg text-xs font-medium text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'
        >
          新建待办
        </button>
      </div>
    )
  }

  return (
    <div className='px-4 pt-4 pb-6 space-y-3'>
      {pendingTodos.length > 0 && (
        <div className='space-y-3'>
          {pendingTodos.map((todo) => (
            <TodoListItem
              key={todo.id}
              todo={todo}
              onSelect={() => onSelect(todo)}
              onToggle={() => onToggle(todo.id)}
              onEdit={() => onEdit(todo)}
              onDelete={() => onDelete(todo.id)}
              formatDateFull={formatDateFull}
              formatDate={formatDate}
              isOverdue={isOverdue}
            />
          ))}
        </div>
      )}
      {completedTodos.length > 0 && (
        <div>
          <div className='flex items-center gap-2 px-0.5 pt-2 pb-3'>
            <div className='w-1 h-4 rounded-full bg-[var(--text-muted)]/40' />
            <span className='text-xs font-semibold text-[var(--text-muted)] tracking-wider uppercase'>
              已完成
            </span>
          </div>
          <div className='space-y-3'>
            {completedTodos.map((todo) => (
              <TodoListItem
                key={todo.id}
                todo={todo}
                onSelect={() => onSelect(todo)}
                onToggle={() => onToggle(todo.id)}
                onEdit={() => onEdit(todo)}
                onDelete={() => onDelete(todo.id)}
                formatDateFull={formatDateFull}
                formatDate={formatDate}
                isOverdue={isOverdue}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
