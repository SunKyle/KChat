import { FileText, Pin, Edit3, Trash2 } from 'lucide-react'
import type { Note } from '../../types/note-todo'

interface NoteListItemProps {
  note: Note
  onSelect: () => void
  onEdit: () => void
  onDelete: () => void
  onPin: () => void
  formatDateFull: (dateString: string) => string
  getContentPreview: (content: string) => string
}

function NoteListItem({
  note,
  onSelect,
  onEdit,
  onDelete,
  onPin,
  formatDateFull,
  getContentPreview,
}: NoteListItemProps) {
  return (
    <div
      className='group relative rounded-xl border border-[var(--border-divider)] bg-[var(--bg-card)] hover:shadow-md hover:shadow-[var(--shadow-color)]/15 hover:border-[var(--border-primary)] transition-all duration-200 cursor-pointer overflow-hidden'
      onClick={onSelect}
    >
      <div className='p-4'>
        <div className='flex items-start min-w-0'>
          <div className='flex-1 min-w-0'>
            <h3 className='text-base font-semibold text-[var(--text-primary)] leading-tight'>
              {note.title || '无标题'}
            </h3>
            <p className='text-sm text-[var(--text-muted)] line-clamp-2 mt-2 leading-relaxed'>
              {getContentPreview(note.content)}
            </p>
            <div className='flex items-center justify-between mt-3 flex-wrap gap-2'>
              <div className='flex items-center gap-2 flex-wrap'>
                {note.category === '工作' && (
                  <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--brand-info)]/10 text-[var(--brand-info)]'>
                    {note.category}
                  </span>
                )}
                {note.category === '学习' && (
                  <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--brand-success)]/10 text-[var(--brand-success)]'>
                    {note.category}
                  </span>
                )}
                {note.category === '生活' && (
                  <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--accent-rose)]/10 text-[var(--accent-rose)]'>
                    {note.category}
                  </span>
                )}
                {note.category === '默认' && (
                  <span className='inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-[var(--bg-hover)] text-[var(--text-secondary)]'>
                    {note.category}
                  </span>
                )}
                {note.tags.slice(0, 2).map((tag) => (
                  <span
                    key={tag}
                    className='inline-flex items-center px-2.5 py-1 rounded-full text-xs bg-[var(--bg-hover)] text-[var(--text-muted)]'
                  >
                    #{tag}
                  </span>
                ))}
              </div>
              <span className='text-xs text-[var(--text-muted)]/60 flex-shrink-0'>
                {formatDateFull(note.updatedAt)}
              </span>
            </div>
          </div>
        </div>
      </div>
      <div className='absolute top-3 right-3 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onPin()
          }}
          className='p-1.5 rounded-md bg-[var(--bg-glass)] backdrop-blur-sm hover:bg-[var(--bg-glass-hover)] shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--border-primary)] transition-all'
          aria-label={note.pinned ? '取消置顶' : '置顶'}
        >
          <Pin
            className={`w-3.5 h-3.5 transition-all ${note.pinned ? 'text-[var(--accent-amber)] fill-current' : 'text-[var(--text-secondary)]'}`}
          />
        </button>
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

interface NoteListProps {
  notes: Note[]
  onSelect: (note: Note) => void
  onEdit: (note: Note) => void
  onDelete: (id: string) => void
  onPin: (note: Note) => void
  formatDateFull: (dateString: string) => string
  getContentPreview: (content: string) => string
  onOpenCreate: () => void
}

export function NoteList({
  notes,
  onSelect,
  onEdit,
  onDelete,
  onPin,
  formatDateFull,
  getContentPreview,
  onOpenCreate,
}: NoteListProps) {
  const pinnedNotes = notes.filter((n) => n.pinned)
  const unpinnedNotes = notes.filter((n) => !n.pinned)

  if (notes.length === 0) {
    return (
      <div className='flex flex-col items-center justify-center py-16 px-4'>
        <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
          <FileText className='w-6 h-6 text-[var(--text-muted)]/50' />
        </div>
        <p className='text-sm text-[var(--text-muted)]'>暂无笔记</p>
        <button
          onClick={onOpenCreate}
          className='mt-3 px-4 py-1.5 rounded-lg text-xs font-semibold text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'
        >
          新建笔记
        </button>
      </div>
    )
  }

  return (
    <div className='px-4 pt-4 pb-6 space-y-3'>
      {pinnedNotes.length > 0 && (
        <div>
          <div className='flex items-center gap-1.5 px-0.5 pb-3'>
            <Pin className='w-3.5 h-3.5 text-[var(--accent-amber)] fill-current' />
            <span className='text-xs font-semibold text-[var(--accent-amber)]'>
              置顶
            </span>
          </div>
          <div className='space-y-3'>
            {pinnedNotes.map((note) => (
              <NoteListItem
                key={note.id}
                note={note}
                onSelect={() => onSelect(note)}
                onEdit={() => onEdit(note)}
                onDelete={() => onDelete(note.id)}
                onPin={() => onPin(note)}
                formatDateFull={formatDateFull}
                getContentPreview={getContentPreview}
              />
            ))}
          </div>
        </div>
      )}
      {unpinnedNotes.length > 0 && (
        <div>
          {pinnedNotes.length > 0 && (
            <div className='flex items-center gap-2 px-0.5 pt-2 pb-3'>
              <div className='w-1 h-4 rounded-full bg-[var(--text-muted)]/40' />
              <span className='text-xs font-semibold text-[var(--text-muted)]'>
                全部笔记
              </span>
            </div>
          )}
          <div className='space-y-3'>
            {unpinnedNotes.map((note) => (
              <NoteListItem
                key={note.id}
                note={note}
                onSelect={() => onSelect(note)}
                onEdit={() => onEdit(note)}
                onDelete={() => onDelete(note.id)}
                onPin={() => onPin(note)}
                formatDateFull={formatDateFull}
                getContentPreview={getContentPreview}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
