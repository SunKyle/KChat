import { useState, useCallback, useEffect } from 'react'
import {
  FileText,
  ListTodo,
  Plus,
  Search,
  ChevronDown,
  Trash2,
  ChevronRight,
  ChevronLeft,
  Loader2,
} from 'lucide-react'
import { useDebounce } from '../../hooks/useDebounce'
import type {
  Note,
  Todo,
  NoteTodoMode,
  UpdateNoteRequest,
} from '../../types/note-todo'
import { noteApi } from '../../api/note-todo'
import { NoteList } from './NoteList'
import { TodoList } from './TodoList'
import { NoteForm } from './NoteForm'
import { TodoForm } from './TodoForm'
import { DetailPreview } from './DetailPreview'
import { FullscreenMarkdownEditor } from './FullscreenMarkdownEditor'
import { useNoteTodoData, convertNote } from './useNoteTodoData'
import { useNoteTodoForm } from './useNoteTodoForm'

interface NoteTodoPanelProps {
  isOpen: boolean
  onClose: () => void
  onOpen: () => void
}

interface DeleteConfirmState {
  type: 'note' | 'todo'
  id: string
  title: string
}

// ─── Inline sub-components ───────────────────────────────────────

function NoteTodoHeader({
  mode,
  notesCount,
  pendingTodosCount,
  onModeChange,
  onClose,
}: {
  mode: NoteTodoMode
  notesCount: number
  pendingTodosCount: number
  onModeChange: (m: NoteTodoMode) => void
  onClose: () => void
}) {
  return (
    <div className='flex-shrink-0 flex items-center justify-between px-3 h-14 border-b border-[var(--border-divider)] bg-[var(--bg-sidebar)]'>
      <div className='relative flex items-center bg-[var(--bg-input)] rounded-lg p-0.5'>
        <div
          className={`absolute top-0.5 bottom-0.5 w-[calc(50%-2px)] bg-[var(--brand-primary)] rounded-md shadow-sm shadow-[var(--brand-primary)]/20 transition-all duration-300 ease-out ${mode === 'note' ? 'left-0.5' : 'left-1/2'}`}
        />
        <button
          onClick={() => onModeChange('note')}
          className={`relative flex items-center gap-1.5 px-3 py-2 text-xs font-semibold transition-colors duration-200 rounded-md ${mode === 'note' ? 'text-white' : 'text-[var(--text-muted)]'}`}
        >
          <FileText className='w-3.5 h-3.5' />
          笔记
          <span
            className={`ml-1 min-w-[18px] h-[18px] flex items-center justify-center text-xs font-semibold rounded-full transition-all duration-300 ${mode === 'note' ? 'bg-white/20 text-white' : 'bg-[var(--bg-hover)] text-[var(--text-muted)]'}`}
          >
            {notesCount}
          </span>
        </button>
        <button
          onClick={() => onModeChange('todo')}
          className={`relative flex items-center gap-1.5 px-3 py-2 text-xs font-semibold transition-colors duration-200 rounded-md ${mode === 'todo' ? 'text-white' : 'text-[var(--text-muted)]'}`}
        >
          <ListTodo className='w-3.5 h-3.5' />
          待办
          <span
            className={`ml-1 min-w-[18px] h-[18px] flex items-center justify-center text-xs font-semibold rounded-full transition-all duration-300 ${mode === 'todo' ? 'bg-white/20 text-white' : 'bg-[var(--bg-hover)] text-[var(--text-muted)]'}`}
          >
            {pendingTodosCount}
          </span>
        </button>
      </div>
      <button
        onClick={onClose}
        className='p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
        aria-label='收起'
      >
        <ChevronRight className='w-4 h-4 text-[var(--text-muted)]' />
      </button>
    </div>
  )
}

function NoteTodoSearchBar({
  mode,
  searchQuery,
  onSearchChange,
  activeTab,
  onTabChange,
  todos,
  onCreateClick,
  allTags,
  filterTags,
  filterExpanded,
  onFilterToggle,
  onFilterTagsChange,
}: {
  mode: NoteTodoMode
  searchQuery: string
  onSearchChange: (v: string) => void
  activeTab: 'all' | 'pending' | 'completed'
  onTabChange: (v: 'all' | 'pending' | 'completed') => void
  todos: Todo[]
  onCreateClick: () => void
  allTags: { name: string; count: number }[]
  filterTags: string[]
  filterExpanded: boolean
  onFilterToggle: () => void
  onFilterTagsChange: (tags: string[]) => void
}) {
  return (
    <div className='flex-shrink-0 p-3 border-b border-[var(--border-divider)]'>
      <div className='flex items-center gap-2'>
        <div className='relative flex-1'>
          <Search className='absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[var(--text-muted)]' />
          <input
            type='text'
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder={mode === 'note' ? '搜索笔记...' : '搜索待办...'}
            className='w-full pl-8 pr-8 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-sm font-secondary text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/25 transition-all'
          />
        </div>
        <button
          onClick={onCreateClick}
          className='flex items-center justify-center w-9 h-9 rounded-lg bg-[var(--brand-primary)] text-white hover:bg-primary-600 active:scale-95 transition-all duration-200'
          aria-label={mode === 'note' ? '新建笔记' : '新建待办'}
        >
          <Plus className='w-4 h-4' />
        </button>
      </div>

      {mode === 'todo' && (
        <div className='flex items-center gap-1 mt-2 bg-[var(--bg-input)] rounded-lg p-0.5 w-fit'>
          {(['all', 'pending', 'completed'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => onTabChange(tab)}
              className={`px-2.5 py-1.5 rounded-md text-xs font-semibold transition-all duration-200 ${
                activeTab === tab
                  ? 'bg-[var(--brand-primary)]/15 text-[var(--brand-primary)]'
                  : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
              }`}
            >
              {tab === 'all' ? `全部 (${todos.length})` : tab === 'pending' ? `进行中 (${todos.filter((t) => t.status === 'pending').length})` : `已完成 (${todos.filter((t) => t.status === 'completed').length})`}
            </button>
          ))}
        </div>
      )}

      {mode === 'note' && allTags.length > 0 && (
        <div className='mt-3'>
          <button
            onClick={onFilterToggle}
            className='flex items-center gap-1.5 text-xs font-semibold text-[var(--text-muted)] mb-2.5 px-0.5 hover:text-[var(--text-secondary)] transition-colors'
          >
            <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${filterExpanded ? '' : '-rotate-90'}`} />
            筛选标签
            {filterTags.length > 0 && (
              <span className='text-[var(--brand-primary)] normal-case tracking-normal font-semibold'>({filterTags.length})</span>
            )}
          </button>
          {filterExpanded && (
            <div className='flex items-center gap-1.5 flex-wrap'>
              {allTags.map(({ name, count }) => (
                <button
                  key={name}
                  onClick={() =>
                    onFilterTagsChange(
                      filterTags.includes(name) ? filterTags.filter((t) => t !== name) : [...filterTags, name]
                    )
                  }
                  className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all duration-200 ${filterTags.includes(name) ? 'bg-[var(--brand-primary)]/15 text-[var(--brand-primary)]' : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'}`}
                >
                  {name}
                  <span className='ml-1 opacity-50 text-xs'>{count}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Utility functions ───────────────────────────────────────────

function formatDate(dateString: string) {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays === -1) return '明天'
  if (diffDays === -2) return '后天'
  if (diffDays < 0) return `${-diffDays}天后`
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatDateFull(dateString: string) {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))
  if (diffDays === 0) {
    const hours = date.getHours().toString().padStart(2, '0')
    const minutes = date.getMinutes().toString().padStart(2, '0')
    return `今天 ${hours}:${minutes}`
  }
  if (diffDays === 1) return '昨天'
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function getContentPreview(content: string) {
  const stripped = content
    .replace(/#{1,6}\s/g, '')
    .replace(/[*_~`]/g, '')
    .replace(/>\s/g, '')
    .replace(/^\s*[-+]\s/gm, '')
    .replace(/\n+/g, ' ')
    .trim()
  return stripped.length > 80 ? stripped.substring(0, 80) + '…' : stripped
}

function isOverdue(dueDate: string | null, status: string) {
  if (!dueDate || status === 'completed') return false
  return new Date(dueDate) < new Date()
}

// ─── Main component ──────────────────────────────────────────────

export function NoteTodoPanel({ isOpen, onClose, onOpen }: NoteTodoPanelProps) {
  const {
    notes, setNotes, todos, isLoading,
    loadData, createNote, updateNote, deleteNote, pinNote,
    createTodo, updateTodo, deleteTodo, toggleTodo,
  } = useNoteTodoData()

  const {
    isFormOpen, editingNote, editingTodo,
    formState, setFormState,
    openCreateForm, editNote, editTodo, cancelForm,
  } = useNoteTodoForm()

  const [mode, setMode] = useState<NoteTodoMode>('note')
  const [selectedNote, setSelectedNote] = useState<Note | null>(null)
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState<'all' | 'pending' | 'completed'>('all')
  const [deleteConfirm, setDeleteConfirm] = useState<DeleteConfirmState | null>(null)
  const [filterTags, setFilterTags] = useState<string[]>([])
  const [filterExpanded, setFilterExpanded] = useState(true)
  const [fullscreenNote, setFullscreenNote] = useState<Note | null>(null)
  const [showFullscreenEditor, setShowFullscreenEditor] = useState(false)

  const debouncedSearchQuery = useDebounce(searchQuery, 300)

  useEffect(() => { if (isOpen) loadData() }, [isOpen, loadData])

  useEffect(() => {
    const handler = () => { if (isOpen) loadData() }
    window.addEventListener('note-created', handler)
    return () => window.removeEventListener('note-created', handler)
  }, [isOpen, loadData])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault()
        openCreateForm()
      }
      if (e.key === 'Escape' && isFormOpen) cancelForm()
    }
    if (isOpen) document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, isFormOpen, openCreateForm, cancelForm])

  const handleModeChange = useCallback((newMode: NoteTodoMode) => {
    setMode(newMode)
    setSelectedNote(null)
    setSelectedTodo(null)
    setSearchQuery('')
    setActiveTab('all')
    setFilterTags([])
    cancelForm()
  }, [cancelForm])

  const handleCreateNote = useCallback(async () => {
    const ok = await createNote(formState)
    if (ok) { cancelForm(); setSelectedNote(null) }
  }, [createNote, formState, cancelForm])

  const handleUpdateNote = useCallback(async () => {
    if (!editingNote) return
    const ok = await updateNote(editingNote.id, formState)
    if (ok) { cancelForm(); setEditingNote(null) }
  }, [editingNote, updateNote, formState, cancelForm])

  const handleDeleteNote = useCallback((id: string) => {
    const note = notes.find((n) => n.id === id)
    if (note) setDeleteConfirm({ type: 'note', id, title: note.title })
  }, [notes])

  const confirmDeleteNote = useCallback(async () => {
    if (!deleteConfirm || deleteConfirm.type !== 'note') return
    const ok = await deleteNote(deleteConfirm.id)
    if (ok) {
      if (selectedNote?.id === deleteConfirm.id) setSelectedNote(null)
      setDeleteConfirm(null)
    }
  }, [deleteConfirm, deleteNote, selectedNote])

  const handlePinNote = useCallback(async (note: Note) => {
    await pinNote(note)
  }, [pinNote])

  const handleEditNoteAction = useCallback((note: Note) => {
    editNote(note)
    setSelectedNote(null)
  }, [editNote])

  const handleCreateTodo = useCallback(async () => {
    const ok = await createTodo(formState)
    if (ok) { cancelForm() }
  }, [createTodo, formState, cancelForm])

  const handleUpdateTodo = useCallback(async () => {
    if (!editingTodo) return
    const ok = await updateTodo(editingTodo.id, formState)
    if (ok) { cancelForm(); setEditingTodo(null) }
  }, [editingTodo, updateTodo, formState, cancelForm])

  const handleDeleteTodo = useCallback((id: string) => {
    const todo = todos.find((t) => t.id === id)
    if (todo) setDeleteConfirm({ type: 'todo', id, title: todo.title })
  }, [todos])

  const confirmDeleteTodo = useCallback(async () => {
    if (!deleteConfirm || deleteConfirm.type !== 'todo') return
    const ok = await deleteTodo(deleteConfirm.id)
    if (ok) {
      if (selectedTodo?.id === deleteConfirm.id) setSelectedTodo(null)
      setDeleteConfirm(null)
    }
  }, [deleteConfirm, deleteTodo, selectedTodo])

  const handleEditTodoAction = useCallback((todo: Todo) => {
    editTodo(todo)
    setSelectedTodo(null)
  }, [editTodo])

  const filteredNotes = notes.filter((n) => {
    const matchesSearch =
      n.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.content.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.tags.some((t) => t.toLowerCase().includes(debouncedSearchQuery.toLowerCase()))
    const matchesTag = filterTags.length === 0 || filterTags.some((ft) => n.tags.includes(ft))
    return matchesSearch && matchesTag
  })

  const allTags = [...new Set(notes.flatMap((n) => n.tags))].map((tag) => ({
    name: tag,
    count: notes.filter((n) => n.tags.includes(tag)).length,
  }))

  const categories = ['默认', '工作', '生活', '学习', '其他']

  const isEditing = editingNote || editingTodo
  const formTitle = isEditing
    ? `编辑${mode === 'note' ? '笔记' : '待办'}`
    : `新建${mode === 'note' ? '笔记' : '待办'}`

  return (
    <>
      <div
        className={`fixed right-4 top-4 bottom-4 w-[340px] lg:w-[400px] z-40 transition-transform duration-300 ease-out ${isOpen ? 'translate-x-0' : 'translate-x-[calc(100%-20px)] cursor-pointer'}`}
        onClick={!isOpen ? onOpen : undefined}
      >
        <div className='h-full card-panel-quiet flex flex-col overflow-hidden'>
          <NoteTodoHeader
            mode={mode}
            notesCount={notes.length}
            pendingTodosCount={todos.filter((t) => t.status === 'pending').length}
            onModeChange={handleModeChange}
            onClose={onClose}
          />
          <div className='flex-1 flex flex-col overflow-hidden'>
            {isLoading ? (
              <div className='flex-1 flex items-center justify-center'>
                <Loader2 className='w-6 h-6 text-[var(--brand-primary)] animate-spin' />
              </div>
            ) : selectedNote || selectedTodo ? (
              <DetailPreview
                selectedNote={selectedNote}
                selectedTodo={selectedTodo}
                formatDateFull={formatDateFull}
                formatDate={formatDate}
                isOverdue={isOverdue}
                onNoteBack={() => { setSelectedNote(null); if (isFormOpen) cancelForm() }}
                onNoteEdit={() => handleEditNoteAction(selectedNote!)}
                onNoteDelete={() => handleDeleteNote(selectedNote!.id)}
                onNoteExpand={() => { if (selectedNote) { setFullscreenNote(selectedNote); setShowFullscreenEditor(true) } }}
                onTodoBack={() => { setSelectedTodo(null); if (isFormOpen) cancelForm() }}
                onTodoToggle={() => handleToggleTodo(selectedTodo!.id)}
                onTodoEdit={() => handleEditTodoAction(selectedTodo!)}
                onTodoDelete={() => handleDeleteTodo(selectedTodo!.id)}
              />
            ) : isFormOpen ? (
              <>
                <div className='flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-[var(--border-divider)]'>
                  <button
                    onClick={cancelForm}
                    className='flex items-center gap-1 text-sm text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
                  >
                    <ChevronLeft className='w-4 h-4' />
                    返回
                  </button>
                  <span className='text-base font-semibold text-[var(--text-primary)]'>{formTitle}</span>
                  <div className='w-12' />
                </div>
                {editingNote || mode === 'note' ? (
                  <NoteForm
                    formState={formState}
                    setFormState={setFormState}
                    categories={categories}
                    isEditing={!!editingNote}
                    onCancel={cancelForm}
                    onSubmit={editingNote ? handleUpdateNote : handleCreateNote}
                    onOpenFullscreen={() => setShowFullscreenEditor(true)}
                  />
                ) : (
                  <TodoForm
                    formState={formState}
                    setFormState={setFormState}
                    categories={categories}
                    isEditing={!!editingTodo}
                    onCancel={cancelForm}
                    onSubmit={editingTodo ? handleUpdateTodo : handleCreateTodo}
                  />
                )}
              </>
            ) : (
              <>
                <NoteTodoSearchBar
                  mode={mode}
                  searchQuery={searchQuery}
                  onSearchChange={setSearchQuery}
                  activeTab={activeTab}
                  onTabChange={setActiveTab}
                  todos={todos}
                  onCreateClick={openCreateForm}
                  allTags={allTags}
                  filterTags={filterTags}
                  filterExpanded={filterExpanded}
                  onFilterToggle={() => setFilterExpanded(!filterExpanded)}
                  onFilterTagsChange={setFilterTags}
                />
                <div className='flex-1 overflow-y-auto scrollbar-hidden'>
                  {mode === 'note' ? (
                    <NoteList
                      notes={filteredNotes}
                      onSelect={(note) => setSelectedNote(note)}
                      onEdit={(note) => handleEditNoteAction(note)}
                      onDelete={(id) => handleDeleteNote(id)}
                      onPin={(note) => handlePinNote(note)}
                      formatDateFull={formatDateFull}
                      getContentPreview={getContentPreview}
                      onOpenCreate={openCreateForm}
                    />
                  ) : (
                    <TodoList
                      todos={todos}
                      activeTab={activeTab}
                      onSelect={(todo) => setSelectedTodo(todo)}
                      onToggle={(id) => toggleTodo(id)}
                      onEdit={(todo) => handleEditTodoAction(todo)}
                      onDelete={(id) => handleDeleteTodo(id)}
                      formatDateFull={formatDateFull}
                      formatDate={formatDate}
                      isOverdue={isOverdue}
                      onOpenCreate={openCreateForm}
                    />
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      {deleteConfirm && (
        <div className='fixed inset-0 z-[60] flex items-center justify-center'>
          <div className='absolute inset-0 bg-[var(--bg-overlay)]' onClick={() => setDeleteConfirm(null)} />
          <div className='relative bg-[var(--bg-sidebar)] rounded-xl shadow-xl p-5 w-full max-w-sm mx-4 animate-fade-in-up border border-[var(--border-divider)]'>
            <div className='flex items-start gap-3 mb-4'>
              <div className='w-9 h-9 bg-[var(--brand-danger)]/[0.08] rounded-full flex items-center justify-center flex-shrink-0'>
                <Trash2 className='w-4 h-4 text-[var(--brand-danger)]' />
              </div>
              <div className='flex-1'>
                <h3 className='text-base font-semibold text-[var(--text-primary)]'>确定要删除「{deleteConfirm.title}」吗？</h3>
                <p className='text-xs text-[var(--text-muted)] mt-1'>此操作无法撤销</p>
              </div>
            </div>
            <div className='flex items-center justify-end gap-2'>
              <button onClick={() => setDeleteConfirm(null)} className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-lg text-sm font-semibold hover:bg-[var(--bg-input)] transition-colors'>
                取消
              </button>
              <button
                onClick={deleteConfirm.type === 'note' ? confirmDeleteNote : confirmDeleteTodo}
                className='px-4 py-2 bg-[var(--brand-danger)] text-white rounded-lg text-sm font-semibold hover:bg-[var(--brand-danger)]/90 transition-colors'
              >
                删除
              </button>
            </div>
          </div>
        </div>
      )}

      {showFullscreenEditor && (
        <FullscreenMarkdownEditor
          title={fullscreenNote ? fullscreenNote.title : formState.title}
          content={fullscreenNote ? fullscreenNote.content : formState.content}
          initialMode={fullscreenNote ? 'preview' : 'split'}
          onClose={() => { setShowFullscreenEditor(false); setFullscreenNote(null) }}
          onSave={async (title, content) => {
            if (fullscreenNote) {
              try {
                const request: UpdateNoteRequest = {
                  title: title || '无标题',
                  content,
                  category: fullscreenNote.category,
                  tags: fullscreenNote.tags,
                  pinned: fullscreenNote.pinned,
                }
                const updatedNote = await noteApi.update(fullscreenNote.id, request)
                const converted = convertNote(updatedNote)
                setNotes((prev) => prev.map((n) => (n.id === fullscreenNote.id ? converted : n)))
                setSelectedNote(converted)
              } catch (err) {
                console.error('Failed to save note:', err)
              }
            } else {
              setFormState((prev) => ({ ...prev, title, content }))
            }
            setShowFullscreenEditor(false)
            setFullscreenNote(null)
          }}
        />
      )}
    </>
  )
}
