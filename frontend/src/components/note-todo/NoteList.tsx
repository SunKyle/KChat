import { FileText, Star, Edit3, Trash2 } from 'lucide-react'
import type { Note } from '../../types/note-todo'

interface NoteListItemProps {
  note: Note
  onSelect: () => void
  onEdit: () => void
  onDelete: () => void
  formatDateFull: (dateString: string) => string
  getContentPreview: (content: string) => string
}

function NoteListItem({
  note,
  onSelect,
  onEdit,
  onDelete,
  formatDateFull,
  getContentPreview,
}: NoteListItemProps) {
  return (
    <div
      className='group relative rounded-lg border border-transparent bg-transparent hover:bg-[var(--bg-hover)] hover:shadow-md hover:shadow-[var(--shadow-color)]/10 transition-all duration-200 cursor-pointer overflow-hidden'
      onClick={onSelect}
    >
      <div className='p-3'>
        <div className='flex items-center justify-between gap-2 min-w-0'>
          <div className='flex items-center gap-1.5 min-w-0'>
            {note.pinned && (
              <div className='w-5 h-5 rounded-full bg-[var(--accent-amber)]/10 flex items-center justify-center flex-shrink-0'>
                <Star className='w-3 h-3 text-[var(--accent-amber)] fill-current' />
              </div>
            )}
            <h3 className='text-sm font-medium text-[var(--text-primary)] truncate'>
              {note.title || '无标题'}
            </h3>
          </div>
          <span className='text-xs text-[var(--text-muted)]/50 flex-shrink-0'>
            {formatDateFull(note.updatedAt)}
          </span>
        </div>
        <p className='text-xs text-[var(--text-muted)] line-clamp-2 mt-1.5 leading-relaxed'>
          {getContentPreview(note.content)}
        </p>
        <div className='flex items-center gap-1.5 mt-2 flex-wrap'>
          <span className='inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--brand-primary)]/10 text-[var(--brand-primary)] border border-[var(--brand-primary)]/20'>
            {note.category}
          </span>
          {note.tags.slice(0, 2).map((tag) => (
            <span key={tag} className='inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-[var(--bg-secondary)] text-[var(--text-secondary)]'>
              #{tag}
            </span>
          ))}
        </div>
      </div>
      <div className='absolute inset-0 bg-gradient-to-r from-transparent via-transparent to-[var(--bg-hover)]/50 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none' />
      <div className='absolute top-2 right-2 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-all transform translate-x-2 group-hover:translate-x-0'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onEdit()
          }}
          className='p-1.5 rounded-lg bg-white/80 backdrop-blur-sm hover:bg-white shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--border-primary)] transition-all'
          aria-label='编辑'
        >
          <Edit3 className='w-3.5 h-3.5 text-[var(--text-secondary)]' />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          className='p-1.5 rounded-lg bg-white/80 backdrop-blur-sm hover:bg-[var(--brand-danger)]/10 shadow-sm hover:shadow-md border border-[var(--border-divider)] hover:border-[var(--brand-danger)]/30 transition-all'
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
  formatDateFull: (dateString: string) => string
  getContentPreview: (content: string) => string
  onOpenCreate: () => void
}

export function NoteList({ notes, onSelect, onEdit, onDelete, formatDateFull, getContentPreview, onOpenCreate }: NoteListProps) {
  const pinnedNotes = notes.filter((n) => n.pinned)
  const unpinnedNotes = notes.filter((n) => !n.pinned)

  if (notes.length === 0) {
    return (
      <div className='flex flex-col items-center justify-center py-16 px-4'>
        <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
          <FileText className='w-6 h-6 text-[var(--text-muted)]/50' />
        </div>
        <p className='text-[14px] text-[var(--text-muted)]'>暂无笔记</p>
        <button onClick={onOpenCreate} className='mt-3 px-4 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'>
          新建笔记
        </button>
      </div>
    )
  }

  return (
    <div className='px-3 pt-3 pb-4 space-y-2'>
      {pinnedNotes.length > 0 && (
        <div>
          <div className='flex items-center gap-1.5 px-1 pt-1 pb-2'>
            <div className='w-[3px] h-3.5 rounded-full bg-[var(--accent-amber)]' />
            <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
              置顶
            </span>
          </div>
          <div className='space-y-2'>
            {pinnedNotes.map((note) => (
              <NoteListItem
                key={note.id}
                note={note}
                onSelect={() => onSelect(note)}
                onEdit={() => onEdit(note)}
                onDelete={() => onDelete(note.id)}
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
            <div className='flex items-center gap-1.5 px-1 pt-2 pb-2'>
              <div className='w-[3px] h-3.5 rounded-full bg-[var(--text-muted)]/30' />
              <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
                全部笔记
              </span>
            </div>
          )}
          <div className='space-y-2'>
            {unpinnedNotes.map((note) => (
              <NoteListItem
                key={note.id}
                note={note}
                onSelect={() => onSelect(note)}
                onEdit={() => onEdit(note)}
                onDelete={() => onDelete(note.id)}
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