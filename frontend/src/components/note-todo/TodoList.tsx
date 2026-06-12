import { ListTodo, CheckCircle2, Circle, Edit3, Calendar, Clock } from 'lucide-react'
import type { Todo } from '../../types/note-todo'

const priorityMeta = {
  high: { label: '高', dot: 'bg-[var(--brand-danger)]', text: 'text-[var(--brand-danger)]' },
  medium: { label: '中', dot: 'bg-[var(--accent-amber)]', text: 'text-[var(--accent-amber)]' },
  low: { label: '低', dot: 'bg-[var(--brand-success)]', text: 'text-[var(--brand-success)]' },
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
    <div className='group relative rounded-xl border border-[var(--border-divider)] bg-[var(--bg-sidebar)] hover:border-[var(--border-primary)] hover:shadow-sm transition-all cursor-pointer' onClick={onSelect}>
      <div className='p-3.5 flex items-start gap-3'>
        <button onClick={(e) => { e.stopPropagation(); onToggle(); }} className='flex-shrink-0 mt-0.5 transition-colors' aria-label={todo.status === 'completed' ? '标记为未完成' : '标记为完成'}>
          {todo.status === 'completed' ? (<CheckCircle2 className='w-[18px] h-[18px] text-[var(--brand-primary)]' />) : (<Circle className='w-[18px] h-[18px] text-[var(--text-muted)]/50' />)}
        </button>
        <div className='flex-1 min-w-0'>
          <div className='flex items-center justify-between gap-2'>
            <h3 className={`text-[13px] font-semibold truncate ${todo.status === 'completed' ? 'text-[var(--text-muted)] line-through' : 'text-[var(--text-primary)]'}`}>
              {todo.title}
            </h3>
            <span className='text-[10px] text-[var(--text-muted)]/60 flex-shrink-0'>
              {formatDateFull(todo.updatedAt)}
            </span>
          </div>
          {todo.description && (<p className='text-[12px] text-[var(--text-muted)] line-clamp-2 mt-0.5 leading-relaxed'>
            {todo.description}
          </p>)}
          <div className='flex items-center gap-1.5 mt-2 flex-wrap'>
            <span className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-full text-[10px] font-medium'>
              {todo.category}
            </span>
            <span className='flex items-center gap-1 text-[10px] font-medium'>
              <span className={`inline-block w-1.5 h-1.5 rounded-full ${priorityMeta[todo.priority].dot}`} />
              <span className={priorityMeta[todo.priority].text}>
                {priorityMeta[todo.priority].label}
              </span>
            </span>
            {todo.dueDate && !isOverdue(todo.dueDate, todo.status) && (<span className='text-[10px] text-[var(--text-muted)] flex items-center gap-0.5'>
              <Calendar className='w-3 h-3' />
              {formatDate(todo.dueDate)}
            </span>)}
            {isOverdue(todo.dueDate, todo.status) && (<span className='text-[10px] text-[var(--brand-danger)] flex items-center gap-0.5 font-medium'>
              <Clock className='w-3 h-3' />
              已过期
            </span>)}
          </div>
        </div>
      </div>
      <div className='absolute top-2.5 right-2.5 opacity-0 group-hover:opacity-100 transition-opacity'>
        <button onClick={(e) => { e.stopPropagation(); onEdit(); }} className='p-1 rounded-md bg-[var(--bg-sidebar)] hover:bg-[var(--bg-hover)] border border-[var(--border-divider)]' aria-label='编辑'>
          <Edit3 className='w-3 h-3 text-[var(--text-muted)]' />
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