import { useState } from 'react'
import {
  ChevronLeft,
  Star,
  Edit3,
  Trash2,
  CheckCircle2,
  Circle,
  Calendar,
  Clock,
  Copy,
  Check,
} from 'lucide-react'
import type { Note, Todo } from '../../types/note-todo'

const priorityMeta = {
  high: { label: '高', text: 'text-[var(--brand-danger)]' },
  medium: { label: '中', text: 'text-[var(--accent-amber)]' },
  low: { label: '低', text: 'text-[var(--brand-success)]' },
} as const

interface NotePreviewProps {
  note: Note
  formatDateFull: (dateString: string) => string
  onBack: () => void
  onEdit: () => void
  onDelete: () => void
}

function NotePreview({ note, formatDateFull, onBack, onEdit, onDelete }: NotePreviewProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    const content = `${note.title}\n\n${note.content}`
    try {
      await navigator.clipboard.writeText(content)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('Failed to copy:', err)
    }
  }

  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-shrink-0 flex items-center justify-between px-4 py-2.5 border-b border-[var(--border-divider)]'>
        <button
          onClick={onBack}
          className='flex items-center gap-1 text-[13px] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
        >
          <ChevronLeft className='w-4 h-4' />
          返回
        </button>
        <div className='flex items-center gap-0.5'>
          <button
            onClick={handleCopy}
            className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
            aria-label={copied ? '已复制' : '复制'}
            title={copied ? '已复制' : '复制'}
          >
            {copied ? (
              <Check className='w-4 h-4 text-[var(--brand-success)]' />
            ) : (
              <Copy className='w-4 h-4 text-[var(--text-muted)]' />
            )}
          </button>
          <button
            onClick={onEdit}
            className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
            aria-label='编辑'
          >
            <Edit3 className='w-4 h-4 text-[var(--text-muted)]' />
          </button>
          <button
            onClick={onDelete}
            className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
            aria-label='删除'
          >
            <Trash2 className='w-4 h-4 text-[var(--text-muted)]' />
          </button>
        </div>
      </div>
      <div className='flex-1 overflow-y-auto p-4'>
        <div className='flex items-center gap-2 mb-2'>
          {note.pinned && (
            <Star className='w-4 h-4 text-[var(--accent-amber)] fill-current flex-shrink-0' />
          )}
          <h2 className='text-[16px] font-semibold text-[var(--text-primary)] leading-snug'>
            {note.title}
          </h2>
        </div>
        <div className='flex items-center gap-2 mb-4 text-[11px] text-[var(--text-muted)] flex-wrap'>
          <span>{formatDateFull(note.updatedAt)}</span>
          <span className='w-1 h-1 rounded-full bg-[var(--text-muted)]/30' />
          <span className='px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[10px] font-medium'>
            {note.category}
          </span>
          {note.tags.map((tag) => (
            <span
              key={tag}
              className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-muted)] rounded-full text-[10px]'
            >
              #{tag}
            </span>
          ))}
        </div>
        <div className='text-[13px] text-[var(--text-secondary)] leading-[1.75] whitespace-pre-wrap break-words'>
          {note.content}
        </div>
      </div>
    </div>
  )
}

interface TodoPreviewProps {
  todo: Todo
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
  onBack: () => void
  onToggle: () => void
  onEdit: () => void
  onDelete: () => void
}

function TodoPreview({
  todo,
  formatDate,
  isOverdue,
  onBack,
  onToggle,
  onEdit,
  onDelete,
}: TodoPreviewProps) {
  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-shrink-0 flex items-center justify-between px-4 py-2.5 border-b border-[var(--border-divider)]'>
        <button
          onClick={onBack}
          className='flex items-center gap-1 text-[13px] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
        >
          <ChevronLeft className='w-4 h-4' />
          返回
        </button>
        <div className='flex items-center gap-0.5'>
          <button
            onClick={onEdit}
            className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
            aria-label='编辑'
          >
            <Edit3 className='w-4 h-4 text-[var(--text-muted)]' />
          </button>
          <button
            onClick={onDelete}
            className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
            aria-label='删除'
          >
            <Trash2 className='w-4 h-4 text-[var(--text-muted)]' />
          </button>
        </div>
      </div>
      <div className='flex-1 overflow-y-auto p-4'>
        <div className='flex items-start gap-3 mb-4'>
          <button onClick={onToggle} className='mt-0.5 flex-shrink-0'>
            {todo.status === 'completed' ? (
              <CheckCircle2 className='w-5 h-5 text-[var(--brand-primary)]' />
            ) : (
              <Circle className='w-5 h-5 text-[var(--text-muted)]' />
            )}
          </button>
          <h2
            className={`text-[16px] font-semibold leading-snug ${todo.status === 'completed' ? 'text-[var(--text-muted)] line-through' : 'text-[var(--text-primary)]'}`}
          >
            {todo.title}
          </h2>
        </div>
        <div className='flex items-center gap-2 flex-wrap mb-4'>
          <span
            className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
              todo.status === 'completed'
                ? 'bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)]'
                : `${priorityMeta[todo.priority].text} bg-[var(--bg-hover)]`
            }`}
          >
            {todo.status === 'completed' ? '已完成' : `${priorityMeta[todo.priority].label}优先级`}
          </span>
          <span className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-full text-[10px] font-medium'>
            {todo.category}
          </span>
          {todo.dueDate && !isOverdue(todo.dueDate, todo.status) && (
            <span className='text-[11px] text-[var(--text-muted)] flex items-center gap-1'>
              <Calendar className='w-3 h-3' />
              {formatDate(todo.dueDate)}
            </span>
          )}
          {isOverdue(todo.dueDate, todo.status) && (
            <span className='text-[11px] text-[var(--brand-danger)] flex items-center gap-1 font-medium'>
              <Clock className='w-3 h-3' />
              已过期
            </span>
          )}
        </div>
        {todo.description && (
          <div className='text-[13px] text-[var(--text-secondary)] leading-[1.75] whitespace-pre-wrap break-words'>
            {todo.description}
          </div>
        )}
      </div>
    </div>
  )
}

interface DetailPreviewProps {
  selectedNote: Note | null
  selectedTodo: Todo | null
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
  onNoteBack: () => void
  onNoteEdit: () => void
  onNoteDelete: () => void
  onTodoBack: () => void
  onTodoToggle: () => void
  onTodoEdit: () => void
  onTodoDelete: () => void
}

export function DetailPreview({
  selectedNote,
  selectedTodo,
  formatDateFull,
  formatDate,
  isOverdue,
  onNoteBack,
  onNoteEdit,
  onNoteDelete,
  onTodoBack,
  onTodoToggle,
  onTodoEdit,
  onTodoDelete,
}: DetailPreviewProps) {
  if (selectedNote) {
    return (
      <NotePreview
        note={selectedNote}
        formatDateFull={formatDateFull}
        onBack={onNoteBack}
        onEdit={onNoteEdit}
        onDelete={onNoteDelete}
      />
    )
  }

  if (selectedTodo) {
    return (
      <TodoPreview
        todo={selectedTodo}
        formatDate={formatDate}
        isOverdue={isOverdue}
        onBack={onTodoBack}
        onToggle={onTodoToggle}
        onEdit={onTodoEdit}
        onDelete={onTodoDelete}
      />
    )
  }

  return null
}
