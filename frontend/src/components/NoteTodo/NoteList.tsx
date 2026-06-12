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
      className='group relative rounded-xl border border-[var(--border-divider)] bg-[var(--bg-sidebar)] hover:border-[var(--border-primary)] hover:shadow-sm transition-all cursor-pointer'
      onClick={onSelect}
    >
      <div className='p-3.5'>
        <div className='flex items-start justify-between gap-2 mb-1.5'>
          <div className='flex items-center gap-1.5 min-w-0'>
            {note.pinned && (
              <Star className='w-3.5 h-3.5 text-[var(--accent-amber)] fill-current flex-shrink-0' />
            )}
            <h3 className='text-[13px] font-semibold text-[var(--text-primary)] truncate'>
              {note.title || '无标题'}
            </h3>
          </div>
          <span className='text-[10px] text-[var(--text-muted)]/60 flex-shrink-0 mt-0.5'>
            {formatDateFull(note.updatedAt)}
          </span>
        </div>
        <p className='text-[12px] text-[var(--text-muted)] line-clamp-2 leading-relaxed'>
          {getContentPreview(note.content)}
        </p>
        <div className='flex items-center gap-1.5 mt-2.5 flex-wrap'>
          <span className='px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[10px] font-medium'>
            {note.category}
          </span>
          {note.tags.slice(0, 2).map((tag) => (
            <span key={tag} className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-muted)] rounded-full text-[10px]'>
              #{tag}
            </span>
          ))}
        </div>
      </div>
      <div className='absolute top-2.5 right-2.5 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onEdit()
          }}
          className='p-1 rounded-md bg-[var(--bg-sidebar)] hover:bg-[var(--bg-hover)] border border-[var(--border-divider)]'
          aria-label='编辑'
        >
          <Edit3 className='w-3 h-3 text-[var(--text-muted)]' />
        </button>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onDelete()
          }}
          className='p-1 rounded-md bg-[var(--bg-sidebar)] hover:bg-[var(--bg-hover)] border border-[var(--border-divider)]'
          aria-label='删除'
        >
          <Trash2 className='w-3 h-3 text-[var(--text-muted)]' />
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