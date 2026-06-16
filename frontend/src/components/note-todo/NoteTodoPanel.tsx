import { useState, useCallback, useEffect, useRef } from 'react'
import {
  FileText,
  ListTodo,
  Plus,
  Search,
  X,
  ChevronDown,
  Trash2,
  ChevronRight,
  ChevronLeft,
  Loader2,
} from 'lucide-react'
import { useToast } from '../../hooks/useToast'
import { useDebounce } from '../../hooks/useDebounce'
import type {
  Note,
  Todo,
  NoteTodoMode,
  CreateNoteRequest,
  UpdateNoteRequest,
  CreateTodoRequest,
  UpdateTodoRequest,
} from '../../types/note-todo'
import { noteApi, todoApi } from '../../api/note-todo'
import { NoteList } from './NoteList'
import { TodoList } from './TodoList'
import { NoteForm } from './NoteForm'
import { TodoForm } from './TodoForm'
import { DetailPreview } from './DetailPreview'
import { FullscreenMarkdownEditor } from './FullscreenMarkdownEditor'

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

interface FormState {
  title: string
  content: string
  category: string
  tags: string[]
  newTag: string
  pinned: boolean
  description: string
  priority: 'high' | 'medium' | 'low'
  dueDate: string
}

// 转换后端日期格式为前端 ISO 格式
function convertDate(date: string | null): string | null {
  if (!date) return null
  // 后端返回格式: 2024-01-01T10:00:00 或数组格式
  if (Array.isArray(date)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = date
    return new Date(year, month - 1, day, hour, minute, second).toISOString()
  }
  return new Date(date).toISOString()
}

// 转换后端 Note 为前端格式
function convertNote(note: any): Note {
  return {
    id: note.id,
    userId: note.userId,
    title: note.title,
    content: note.content || '',
    category: note.category || '默认',
    tags: note.tags || [],
    pinned: note.pinned || false,
    createdAt: convertDate(note.createdAt) || new Date().toISOString(),
    updatedAt: convertDate(note.updatedAt) || new Date().toISOString(),
  }
}

// 转换后端 Todo 为前端格式
function convertTodo(todo: any): Todo {
  return {
    id: todo.id,
    userId: todo.userId,
    title: todo.title,
    description: todo.description || '',
    status: todo.status || 'pending',
    priority: todo.priority || 'medium',
    dueDate: convertDate(todo.dueDate),
    category: todo.category || '默认',
    createdAt: convertDate(todo.createdAt) || new Date().toISOString(),
    updatedAt: convertDate(todo.updatedAt) || new Date().toISOString(),
    completedAt: convertDate(todo.completedAt),
  }
}

export function NoteTodoPanel({ isOpen, onClose, onOpen }: NoteTodoPanelProps) {
  const { success, info, error } = useToast()
  const errorRef = useRef(error)
  const infoRef = useRef(info)
  const successRef = useRef(success)

  useEffect(() => {
    errorRef.current = error
    infoRef.current = info
    successRef.current = success
  }, [error, info, success])

  const [mode, setMode] = useState<NoteTodoMode>('note')
  const [notes, setNotes] = useState<Note[]>([])
  const [todos, setTodos] = useState<Todo[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [selectedNote, setSelectedNote] = useState<Note | null>(null)
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingNote, setEditingNote] = useState<Note | null>(null)
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState<'all' | 'pending' | 'completed'>('all')
  const [deleteConfirm, setDeleteConfirm] = useState<DeleteConfirmState | null>(null)
  const [filterTags, setFilterTags] = useState<string[]>([])
  const [filterExpanded, setFilterExpanded] = useState(true)
  const [fullscreenNote, setFullscreenNote] = useState<Note | null>(null)
  const [showFullscreenEditor, setShowFullscreenEditor] = useState(false)

  const [formState, setFormState] = useState<FormState>({
    title: '',
    content: '',
    category: '默认',
    tags: [],
    newTag: '',
    pinned: false,
    description: '',
    priority: 'medium',
    dueDate: '',
  })

  const debouncedSearchQuery = useDebounce(searchQuery, 300)

  // 从后端加载数据
  const loadData = useCallback(async () => {
    setIsLoading(true)
    try {
      const [notesData, todosData] = await Promise.all([noteApi.getAll(), todoApi.getAll()])
      setNotes(notesData.map(convertNote))
      setTodos(todosData.map(convertTodo))
    } catch (err) {
      console.error('Failed to load data:', err)
      errorRef.current('数据加载失败，请稍后重试')
    } finally {
      setIsLoading(false)
    }
  }, [])

  // 初始化加载
  useEffect(() => {
    if (isOpen) {
      loadData()
    }
  }, [isOpen, loadData])

  // 监听外部创建笔记事件（如 AI 回复保存为笔记）
  useEffect(() => {
    const handleNoteCreated = () => {
      if (isOpen) loadData()
    }
    window.addEventListener('note-created', handleNoteCreated)
    return () => window.removeEventListener('note-created', handleNoteCreated)
  }, [isOpen, loadData])

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault()
        handleOpenCreateForm()
      }
      if (e.key === 'Escape') {
        if (isFormOpen) handleCancelForm()
      }
    }
    if (isOpen) document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, isFormOpen])

  useEffect(() => {
    if (editingNote) {
      setFormState({
        title: editingNote.title,
        content: editingNote.content,
        category: editingNote.category,
        tags: editingNote.tags,
        newTag: '',
        pinned: editingNote.pinned,
        description: '',
        priority: 'medium',
        dueDate: '',
      })
    } else if (editingTodo) {
      setFormState({
        title: editingTodo.title,
        content: '',
        category: editingTodo.category,
        tags: [],
        newTag: '',
        pinned: false,
        description: editingTodo.description,
        priority: editingTodo.priority,
        dueDate: editingTodo.dueDate
          ? new Date(editingTodo.dueDate).toISOString().split('T')[0]
          : '',
      })
    } else {
      setFormState({
        title: '',
        content: '',
        category: '默认',
        tags: [],
        newTag: '',
        pinned: false,
        description: '',
        priority: 'medium',
        dueDate: '',
      })
    }
  }, [editingNote, editingTodo])

  const filteredNotes = notes.filter((n) => {
    const matchesSearch =
      n.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.content.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.tags.some((t) => t.toLowerCase().includes(debouncedSearchQuery.toLowerCase()))
    const matchesTag = filterTags.length === 0 || filterTags.some((ft) => n.tags.includes(ft))
    return matchesSearch && matchesTag
  })

  const handleCreateNote = useCallback(async () => {
    try {
      const request: CreateNoteRequest = {
        title: formState.title || '无标题',
        content: formState.content,
        category: formState.category,
        tags: formState.tags,
        pinned: formState.pinned,
      }
      const newNote = await noteApi.create(request)
      setNotes((prev) => [convertNote(newNote), ...prev])
      setIsFormOpen(false)
      successRef.current('笔记创建成功')
    } catch (err) {
      console.error('Failed to create note:', err)
      errorRef.current('创建笔记失败')
    }
  }, [formState])

  const handleUpdateNote = useCallback(async () => {
    if (!editingNote) return
    try {
      const request: UpdateNoteRequest = {
        title: formState.title || '无标题',
        content: formState.content,
        category: formState.category,
        tags: formState.tags,
        pinned: formState.pinned,
      }
      const updatedNote = await noteApi.update(editingNote.id, request)
      setNotes((prev) => prev.map((n) => (n.id === editingNote.id ? convertNote(updatedNote) : n)))
      setIsFormOpen(false)
      setEditingNote(null)
      successRef.current('笔记更新成功')
    } catch (err) {
      console.error('Failed to update note:', err)
      errorRef.current('更新笔记失败')
    }
  }, [editingNote, formState])

  const handleDeleteNote = useCallback(
    (id: string) => {
      const note = notes.find((n) => n.id === id)
      if (note) setDeleteConfirm({ type: 'note', id, title: note.title })
    },
    [notes]
  )

  const confirmDeleteNote = useCallback(async () => {
    if (!deleteConfirm || deleteConfirm.type !== 'note') return
    try {
      await noteApi.delete(deleteConfirm.id)
      setNotes((prev) => prev.filter((n) => n.id !== deleteConfirm.id))
      if (selectedNote?.id === deleteConfirm.id) setSelectedNote(null)
      setDeleteConfirm(null)
      successRef.current('笔记已删除')
    } catch (err) {
      console.error('Failed to delete note:', err)
      errorRef.current('删除笔记失败')
    }
  }, [deleteConfirm, selectedNote])

  const handleCreateTodo = useCallback(async () => {
    try {
      const request: CreateTodoRequest = {
        title: formState.title || '未命名待办',
        description: formState.description,
        priority: formState.priority,
        dueDate: formState.dueDate ? new Date(formState.dueDate).toISOString() : null,
        category: formState.category,
      }
      const newTodo = await todoApi.create(request)
      setTodos((prev) => [convertTodo(newTodo), ...prev])
      setIsFormOpen(false)
      successRef.current('待办创建成功')
    } catch (err) {
      console.error('Failed to create todo:', err)
      errorRef.current('创建待办失败')
    }
  }, [formState])

  const handleUpdateTodo = useCallback(async () => {
    if (!editingTodo) return
    try {
      const request: UpdateTodoRequest = {
        title: formState.title || '未命名待办',
        description: formState.description,
        priority: formState.priority,
        dueDate: formState.dueDate ? new Date(formState.dueDate).toISOString() : null,
        category: formState.category,
      }
      const updatedTodo = await todoApi.update(editingTodo.id, request)
      setTodos((prev) => prev.map((t) => (t.id === editingTodo.id ? convertTodo(updatedTodo) : t)))
      setIsFormOpen(false)
      setEditingTodo(null)
      successRef.current('待办更新成功')
    } catch (err) {
      console.error('Failed to update todo:', err)
      errorRef.current('更新待办失败')
    }
  }, [editingTodo, formState])

  const handleDeleteTodo = useCallback(
    (id: string) => {
      const todo = todos.find((t) => t.id === id)
      if (todo) setDeleteConfirm({ type: 'todo', id, title: todo.title })
    },
    [todos]
  )

  const confirmDeleteTodo = useCallback(async () => {
    if (!deleteConfirm || deleteConfirm.type !== 'todo') return
    try {
      await todoApi.delete(deleteConfirm.id)
      setTodos((prev) => prev.filter((t) => t.id !== deleteConfirm.id))
      if (selectedTodo?.id === deleteConfirm.id) setSelectedTodo(null)
      setDeleteConfirm(null)
      successRef.current('待办已删除')
    } catch (err) {
      console.error('Failed to delete todo:', err)
      errorRef.current('删除待办失败')
    }
  }, [deleteConfirm, selectedTodo])

  const handleToggleTodo = useCallback(async (id: string) => {
    try {
      const updatedTodo = await todoApi.toggle(id)
      const convertedTodo = convertTodo(updatedTodo)
      const message = convertedTodo.status === 'completed' ? '任务已完成！' : '任务已恢复'
      setTodos((prev) =>
        prev.map((t) => {
          if (t.id === id) {
            return convertedTodo
          }
          return t
        })
      )
      setTimeout(() => infoRef.current(message), 50)
    } catch (err) {
      console.error('Failed to toggle todo:', err)
      errorRef.current('切换状态失败')
    }
  }, [])

  const handleOpenCreateForm = useCallback(() => {
    setEditingNote(null)
    setEditingTodo(null)
    setIsFormOpen(true)
  }, [])

  const handleEditNote = useCallback((note: Note) => {
    setEditingNote(note)
    setSelectedNote(null)
    setIsFormOpen(true)
  }, [])

  const handlePinNote = useCallback(async (note: Note) => {
    try {
      const request: UpdateNoteRequest = {
        pinned: !note.pinned,
      }
      const updatedNote = await noteApi.update(note.id, request)
      setNotes((prev) => prev.map((n) => (n.id === note.id ? convertNote(updatedNote) : n)))
    } catch (err) {
      console.error('Failed to pin note:', err)
      errorRef.current('置顶操作失败')
    }
  }, [])

  const handleEditTodo = useCallback((todo: Todo) => {
    setEditingTodo(todo)
    setSelectedTodo(null)
    setIsFormOpen(true)
  }, [])

  const handleCancelForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingNote(null)
    setEditingTodo(null)
  }, [])

  const handleModeChange = useCallback((newMode: NoteTodoMode) => {
    setMode(newMode)
    setSelectedNote(null)
    setSelectedTodo(null)
    setSearchQuery('')
    setActiveTab('all')
    setFilterTags([])
    setIsFormOpen(false)
  }, [])

  const categories = ['默认', '工作', '生活', '学习', '其他']

  const formatDate = (dateString: string) => {
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

  const formatDateFull = (dateString: string) => {
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

  const getContentPreview = (content: string) => {
    const stripped = content
      .replace(/#{1,6}\s/g, '')
      .replace(/[*_~`]/g, '')
      .replace(/>\s/g, '')
      .replace(/^\s*[-+]\s/gm, '')
      .replace(/\n+/g, ' ')
      .trim()
    return stripped.length > 80 ? stripped.substring(0, 80) + '…' : stripped
  }

  const isOverdue = (dueDate: string | null, status: string) => {
    if (!dueDate || status === 'completed') return false
    return new Date(dueDate) < new Date()
  }

  const allTags = [...new Set(notes.flatMap((n) => n.tags))].map((tag) => ({
    name: tag,
    count: notes.filter((n) => n.tags.includes(tag)).length,
  }))

  const renderHeader = () => (
    <div className='flex-shrink-0 flex items-center justify-between px-3 h-14 border-b border-[var(--border-divider)] bg-[var(--bg-sidebar)]'>
      <div className='relative flex items-center bg-[var(--bg-input)] rounded-lg p-0.5'>
        <div
          className={`absolute top-0.5 bottom-0.5 w-[calc(50%-2px)] bg-[var(--brand-primary)] rounded-md shadow-md shadow-[var(--brand-primary)]/25 transition-all duration-300 ease-out ${mode === 'note' ? 'left-0.5' : 'left-1/2'}`}
        />
        <button
          onClick={() => handleModeChange('note')}
          className='relative flex items-center gap-1 px-3 py-1 text-[12px] font-semibold transition-colors duration-200 rounded-md'
          style={{ color: mode === 'note' ? 'white' : 'var(--text-muted)' }}
        >
          <FileText className='w-3 h-3' />
          笔记
          <span
            className={`ml-1 w-3.5 h-3.5 flex items-center justify-center text-[9px] font-semibold rounded-full transition-all duration-300 ${mode === 'note' ? 'bg-white/20' : 'bg-[var(--bg-hover)]'}`}
            style={{ color: mode === 'note' ? 'white' : 'var(--text-muted)' }}
          >
            {notes.length}
          </span>
        </button>
        <button
          onClick={() => handleModeChange('todo')}
          className='relative flex items-center gap-1 px-3 py-1 text-[12px] font-semibold transition-colors duration-200 rounded-md'
          style={{ color: mode === 'todo' ? 'white' : 'var(--text-muted)' }}
        >
          <ListTodo className='w-3 h-3' />
          待办
          <span
            className={`ml-1 w-3.5 h-3.5 flex items-center justify-center text-[9px] font-semibold rounded-full transition-all duration-300 ${mode === 'todo' ? 'bg-white/20' : 'bg-[var(--bg-hover)]'}`}
            style={{ color: mode === 'todo' ? 'white' : 'var(--text-muted)' }}
          >
            {todos.filter((t) => t.status === 'pending').length}
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

  const renderSearchBar = () => (
    <div className='flex-shrink-0 p-3 border-b border-[var(--border-divider)]'>
      <div className='flex items-center gap-2'>
        <div className='relative flex-1'>
          <Search className='absolute left-2 top-1/2 -translate-y-1/2 w-3 h-3 text-[var(--text-muted)]/60' />
          <input
            type='text'
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={mode === 'note' ? '搜索笔记...' : '搜索待办...'}
            className='w-full pl-7 pr-3 py-2 bg-[var(--bg-input)] border border-transparent rounded-lg text-[13px] font-secondary text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/30 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
        {mode === 'todo' && (
          <select
            value={activeTab}
            onChange={(e) => setActiveTab(e.target.value as 'all' | 'pending' | 'completed')}
            className='py-2 px-2 pr-5 bg-[var(--bg-input)] border border-transparent rounded-lg text-[13px] font-secondary text-[var(--text-primary)] cursor-pointer appearance-none focus:outline-none focus:border-[var(--brand-primary)]/30 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
            style={{
              backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23888' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E")`,
              backgroundRepeat: 'no-repeat',
              backgroundPosition: 'right 4px center',
            }}
          >
            <option value='all'>全部 ({todos.length})</option>
            <option value='pending'>
              进行中 ({todos.filter((t) => t.status === 'pending').length})
            </option>
            <option value='completed'>
              已完成 ({todos.filter((t) => t.status === 'completed').length})
            </option>
          </select>
        )}
        <button
          onClick={handleOpenCreateForm}
          className='flex items-center justify-center w-9 h-9 rounded-lg bg-[#0EA5E9] text-white hover:bg-[#0284C7] transition-all'
          aria-label={mode === 'note' ? '新建笔记' : '新建待办'}
        >
          <Plus className='w-4 h-4' />
        </button>
      </div>

      {mode === 'note' && allTags.length > 0 && (
        <div className='mt-3'>
          <button
            onClick={() => setFilterExpanded(!filterExpanded)}
            className='flex items-center gap-1.5 text-[12px] font-semibold text-[var(--text-muted)]/60 uppercase tracking-wider mb-2.5 px-0.5 hover:text-[var(--text-secondary)] transition-colors'
          >
            <ChevronDown
              className={`w-3.5 h-3.5 transition-transform duration-200 ${filterExpanded ? '' : '-rotate-90'}`}
            />
            筛选标签
            {filterTags.length > 0 && (
              <span className='text-[var(--brand-primary)] normal-case tracking-normal font-medium'>
                ({filterTags.length})
              </span>
            )}
          </button>
          {filterExpanded && (
            <div className='flex items-center gap-1.5 flex-wrap'>
              <button
                onClick={() => setFilterTags([])}
                className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition-all duration-200 ${filterTags.length === 0 ? 'bg-[var(--brand-primary)]/15 text-[var(--brand-primary)]' : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'}`}
              >
                全部
              </button>
              {allTags.map(({ name, count }) => (
                <button
                  key={name}
                  onClick={() =>
                    setFilterTags((prev) =>
                      prev.includes(name) ? prev.filter((t) => t !== name) : [...prev, name]
                    )
                  }
                  className={`px-2.5 py-1 rounded-md text-[11px] font-medium transition-all duration-200 ${filterTags.includes(name) ? 'bg-[var(--brand-primary)]/15 text-[var(--brand-primary)]' : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]'}`}
                >
                  {name}
                  <span className='ml-1 opacity-50 text-[10px]'>{count}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )

  const renderNewFormHeader = () => {
    const isEditing = editingNote || editingTodo
    const title = isEditing
      ? `编辑${mode === 'note' ? '笔记' : '待办'}`
      : `新建${mode === 'note' ? '笔记' : '待办'}`

    return (
      <div className='flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-[var(--border-divider)]'>
        <button
          onClick={handleCancelForm}
          className='flex items-center gap-1 text-[13px] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
        >
          <ChevronLeft className='w-4 h-4' />
          返回
        </button>
        <span className='text-[15px] font-semibold text-[var(--text-primary)]'>{title}</span>
        <div className='w-12' />
      </div>
    )
  }

  const renderLoading = () => (
    <div className='flex-1 flex items-center justify-center'>
      <Loader2 className='w-6 h-6 text-[var(--brand-primary)] animate-spin' />
    </div>
  )

  return (
    <>
      <div
        className={`fixed right-4 top-4 bottom-4 w-[400px] z-40 transition-transform duration-300 ease-out ${isOpen ? 'translate-x-0' : 'translate-x-[calc(100%-20px)] cursor-pointer'}`}
        onClick={!isOpen ? onOpen : undefined}
      >
        <div className='h-full card-float-solid flex flex-col overflow-hidden'>
          {renderHeader()}
          <div className='flex-1 flex flex-col overflow-hidden'>
            {isLoading ? (
              renderLoading()
            ) : selectedNote || selectedTodo ? (
              <DetailPreview
                selectedNote={selectedNote}
                selectedTodo={selectedTodo}
                formatDateFull={formatDateFull}
                formatDate={formatDate}
                isOverdue={isOverdue}
                onNoteBack={() => {
                  setSelectedNote(null)
                  if (isFormOpen) handleCancelForm()
                }}
                onNoteEdit={() => handleEditNote(selectedNote!)}
                onNoteDelete={() => handleDeleteNote(selectedNote!.id)}
                onNoteExpand={() => {
                  if (selectedNote) {
                    setFullscreenNote(selectedNote)
                    setShowFullscreenEditor(true)
                  }
                }}
                onTodoBack={() => {
                  setSelectedTodo(null)
                  if (isFormOpen) handleCancelForm()
                }}
                onTodoToggle={() => handleToggleTodo(selectedTodo!.id)}
                onTodoEdit={() => handleEditTodo(selectedTodo!)}
                onTodoDelete={() => handleDeleteTodo(selectedTodo!.id)}
              />
            ) : isFormOpen ? (
              <>
                {renderNewFormHeader()}
                {editingNote || mode === 'note' ? (
                  <NoteForm
                    formState={formState}
                    setFormState={setFormState}
                    categories={categories}
                    isEditing={!!editingNote}
                    onCancel={handleCancelForm}
                    onSubmit={editingNote ? handleUpdateNote : handleCreateNote}
                    onOpenFullscreen={() => setShowFullscreenEditor(true)}
                  />
                ) : (
                  <TodoForm
                    formState={formState}
                    setFormState={setFormState}
                    categories={categories}
                    isEditing={!!editingTodo}
                    onCancel={handleCancelForm}
                    onSubmit={editingTodo ? handleUpdateTodo : handleCreateTodo}
                  />
                )}
              </>
            ) : (
              <>
                {renderSearchBar()}
                <div className='flex-1 overflow-y-auto scrollbar-hidden'>
                  {mode === 'note' ? (
                    <NoteList
                      notes={filteredNotes}
                      onSelect={(note) => setSelectedNote(note)}
                      onEdit={(note) => handleEditNote(note)}
                      onDelete={(id) => handleDeleteNote(id)}
                      onPin={(note) => handlePinNote(note)}
                      formatDateFull={formatDateFull}
                      getContentPreview={getContentPreview}
                      onOpenCreate={handleOpenCreateForm}
                    />
                  ) : (
                    <TodoList
                      todos={todos}
                      activeTab={activeTab}
                      onSelect={(todo) => setSelectedTodo(todo)}
                      onToggle={(id) => handleToggleTodo(id)}
                      onEdit={(todo) => handleEditTodo(todo)}
                      onDelete={(id) => handleDeleteTodo(id)}
                      formatDateFull={formatDateFull}
                      formatDate={formatDate}
                      isOverdue={isOverdue}
                      onOpenCreate={handleOpenCreateForm}
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
          <div
            className='absolute inset-0 bg-[var(--bg-overlay)]'
            onClick={() => setDeleteConfirm(null)}
          />
          <div className='relative bg-[var(--bg-sidebar)] rounded-xl shadow-xl p-5 w-full max-w-sm mx-4 animate-fade-in-up border border-[var(--border-divider)]'>
            <div className='flex items-start gap-3 mb-4'>
              <div className='w-9 h-9 bg-[var(--brand-danger)]/[0.08] rounded-full flex items-center justify-center flex-shrink-0'>
                <Trash2 className='w-4 h-4 text-[var(--brand-danger)]' />
              </div>
              <div className='flex-1'>
                <h3 className='text-[15px] font-semibold text-[var(--text-primary)]'>确认删除</h3>
                <p className='text-[12px] text-[var(--text-muted)] mt-0.5'>此操作无法撤销</p>
              </div>
              <button
                onClick={() => setDeleteConfirm(null)}
                className='p-1 rounded-md hover:bg-[var(--bg-hover)] transition-colors'
              >
                <X className='w-3.5 h-3.5 text-[var(--text-muted)]' />
              </button>
            </div>
            <p className='text-[13px] text-[var(--text-secondary)] mb-5 pl-12'>
              确定要删除「{deleteConfirm.title}」吗？
            </p>
            <div className='flex items-center justify-end gap-2'>
              <button
                onClick={() => setDeleteConfirm(null)}
                className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-lg text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'
              >
                取消
              </button>
              <button
                onClick={deleteConfirm.type === 'note' ? confirmDeleteNote : confirmDeleteTodo}
                className='px-4 py-2 bg-[var(--brand-danger)] text-white rounded-lg text-[13px] font-medium hover:bg-[var(--brand-danger)]/90 transition-colors'
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
          onClose={() => {
            setShowFullscreenEditor(false)
            setFullscreenNote(null)
          }}
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
                successRef.current('笔记已保存')
              } catch (err) {
                console.error('Failed to save note:', err)
                errorRef.current('保存笔记失败')
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
