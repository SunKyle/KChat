import { ListTodo, CheckCircle2, Circle, Edit3, Calendar, Clock } from 'lucide-react'
import type { Todo } from '../../types/note-todo'

const priorityMeta = {
  high: { label: '高', bgColor: 'bg-[var(--brand-danger)]/15', textColor: 'text-[var(--brand-danger)]', borderColor: 'border-[var(--brand-danger)]/30' },
  medium: { label: '中', bgColor: 'bg-[var(--accent-amber)]/15', textColor: 'text-[var(--accent-amber)]', borderColor: 'border-[var(--accent-amber)]/30' },
  low: { label: '低', bgColor: 'bg-[var(--brand-success)]/15', textColor: 'text-[var(--brand-success)]', borderColor: 'border-[var(--brand-success)]/30' },
} as const

interface TodoListItemProps {
  todo: Todo
  onSelect: () => void
  onToggle: () => void
  onEdit: () => void
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
}

function TodoListItem({ todo, onSelect, onToggle, onEdit, formatDateFull, formatDate, isOverdue }: TodoListItemProps) {
  return (
    <div className='group relative rounded-lg border border-transparent bg-transparent hover:bg-[var(--bg-hover)] hover:shadow-md hover:shadow-[var(--shadow-color)]/10 transition-all duration-200 cursor-pointer overflow-hidden' onClick={onSelect}>
      <div className='p-3 flex items-start gap-3'>
        <button onClick={(e) => { e.stopPropagation(); onToggle(); }} className='flex-shrink-0 mt-0.5 transition-all hover:scale-110' aria-label={todo.status === 'completed' ? '标记为未完成' : '标记为完成'}>
          {todo.status === 'completed' ? (<CheckCircle2 className='w-[18px] h-[18px] text-[var(--brand-primary)]' />) : (<Circle className='w-[18px] h-[18px] text-[var(--text-muted)]/50 hover:text-[var(--brand-primary)]' />)}
        </button>
        <div className='flex-1 min-w-0'>
          <div className='flex items-center justify-between gap-2'>
            <h3 className={`text-sm font-medium truncate ${todo.status === 'completed' ? 'text-[var(--text-muted)] line-through' : 'text-[var(--text-primary)]'}`}>
              {todo.title}
            </h3>
            <span className='text-xs text-[var(--text-muted)]/50 flex-shrink-0'>
              {formatDateFull(todo.updatedAt)}
            </span>
          </div>
          {todo.description && (<p className='text-xs text-[var(--text-muted)] line-clamp-2 mt-1 leading-relaxed'>
            {todo.description}
          </p>)}
          <div className='flex items-center gap-1.5 mt-1.5 flex-wrap'>
            <span className='inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--bg-secondary)] text-[var(--text-secondary)]'>
              {todo.category}
            </span>
            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${priorityMeta[todo.priority].bgColor} ${priorityMeta[todo.priority].textColor} ${priorityMeta[todo.priority].borderColor}`}>
              {priorityMeta[todo.priority].label}
            </span>
            {todo.dueDate && !isOverdue(todo.dueDate, todo.status) && (<span className='inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs bg-[var(--bg-secondary)] text-[var(--text-muted)]'>
              <Calendar className='w-3 h-3' />
              {formatDate(todo.dueDate)}
            </span>)}
            {isOverdue(todo.dueDate, todo.status) && (<span className='inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--brand-danger)]/10 text-[var(--brand-danger)]'>
              <Clock className='w-3 h-3' />
              已过期
            </span>)}
          </div>
        </div>
      </div>
      <div className='absolute inset-0 bg-gradient-to-r from-transparent via-transparent to-[var(--bg-hover)]/50 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none' />
      <div className='absolute top-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0'>
        <button onClick={(e) => { e.stopPropagation(); onEdit(); }} className='p-1.5 rounded-lg bg-white/80 backdrop-blur-sm hover:bg-white shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--border-primary)] transition-all' aria-label='编辑'>
          <Edit3 className='w-3.5 h-3.5 text-[var(--text-secondary)]' />
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
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
  onOpenCreate: () => void
}

export function TodoList({ todos, activeTab, onSelect, onToggle, onEdit, formatDateFull, formatDate, isOverdue, onOpenCreate }: TodoListProps) {
  const filteredTodos = todos.filter((t) => activeTab === 'all' || t.status === activeTab)
  const pendingTodos = filteredTodos.filter((t) => t.status === 'pending')
  const completedTodos = filteredTodos.filter((t) => t.status === 'completed')

  if (filteredTodos.length === 0) {
    return (<div className='flex flex-col items-center justify-center py-16 px-4'>
      <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
        <ListTodo className='w-6 h-6 text-[var(--text-muted)]/50' />
      </div>
      <p className='text-[14px] text-[var(--text-muted)]'>暂无待办</p>
      <button onClick={onOpenCreate} className='mt-3 px-4 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'>
        新建待办
      </button>
    </div>)
  }

  return (<div className='px-3 pt-3 pb-4 space-y-2'>
    {pendingTodos.length > 0 && (<div className='space-y-2'>
      {pendingTodos.map((todo) => (<TodoListItem key={todo.id} todo={todo} onSelect={() => onSelect(todo)} onToggle={() => onToggle(todo.id)} onEdit={() => onEdit(todo)} formatDateFull={formatDateFull} formatDate={formatDate} isOverdue={isOverdue}/>))}
    </div>)}
    {completedTodos.length > 0 && (<div>
      <div className='flex items-center gap-1.5 px-1 pt-2 pb-2'>
        <div className='w-[3px] h-3.5 rounded-full bg-[var(--text-muted)]/30' />
        <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
          已完成
        </span>
      </div>
      <div className='space-y-2'>
        {completedTodos.map((todo) => (<TodoListItem key={todo.id} todo={todo} onSelect={() => onSelect(todo)} onToggle={() => onToggle(todo.id)} onEdit={() => onEdit(todo)} formatDateFull={formatDateFull} formatDate={formatDate} isOverdue={isOverdue}/>))}
      </div>
    </div>)}
  </div>)
}