import { useState, useCallback, useEffect } from 'react'
import {
  FileText,
  ListTodo,
  Plus,
  Search,
  Pin,
  Calendar,
  CheckCircle2,
  Circle,
  Trash2,
  X,
  Edit3,
  Clock,
  ChevronLeft,
} from 'lucide-react'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../../hooks/useToast'
import { useLocalStorage } from '../../hooks/useLocalStorage'
import { useDebounce } from '../../hooks/useDebounce'
import type { Note, Todo, NoteTodoMode } from '../../types/note-todo'

interface NoteTodoDrawerProps {
  isOpen: boolean
  onClose: () => void
}

const mockNotes: Note[] = [
  {
    id: '1',
    userId: 'default',
    title: '项目会议记录',
    content: '讨论了Q3产品路线图，确定了三个核心功能的开发优先级：\n\n1. 智能推荐系统\n2. 多语言支持\n3. 数据分析面板',
    category: '工作',
    tags: ['会议', '项目'],
    pinned: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: '2',
    userId: 'default',
    title: '学习笔记 - React Hooks',
    content: 'useState: 用于管理组件状态\nuseEffect: 用于处理副作用\nuseContext: 用于跨组件传递数据',
    category: '学习',
    tags: ['React', '前端'],
    pinned: false,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
  },
  {
    id: '3',
    userId: 'default',
    title: '购物清单',
    content: '- 牛奶\n- 面包\n- 鸡蛋\n- 水果',
    category: '生活',
    tags: ['日常'],
    pinned: false,
    createdAt: new Date(Date.now() - 172800000).toISOString(),
    updatedAt: new Date(Date.now() - 172800000).toISOString(),
  },
]

const mockTodos: Todo[] = [
  {
    id: '1',
    userId: 'default',
    title: '完成用户认证模块',
    description: '实现登录、注册和密码重置功能',
    status: 'pending',
    priority: 'high',
    dueDate: new Date(Date.now() + 86400000).toISOString(),
    category: '工作',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    completedAt: null,
  },
  {
    id: '2',
    userId: 'default',
    title: '代码审查',
    description: '审查团队成员提交的PR',
    status: 'pending',
    priority: 'medium',
    dueDate: new Date(Date.now() + 172800000).toISOString(),
    category: '工作',
    createdAt: new Date(Date.now() - 86400000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString(),
    completedAt: null,
  },
  {
    id: '3',
    userId: 'default',
    title: '健身打卡',
    description: '完成30分钟有氧运动',
    status: 'completed',
    priority: 'low',
    dueDate: null,
    category: '生活',
    createdAt: new Date(Date.now() - 259200000).toISOString(),
    updatedAt: new Date(Date.now() - 259200000).toISOString(),
    completedAt: new Date(Date.now() - 259200000).toISOString(),
  },
]

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

/* ─── Priority helpers (theme-aware, minimal) ─── */
const priorityMeta = {
  high: { label: '高', dot: 'bg-[var(--brand-danger)]', text: 'text-[var(--brand-danger)]' },
  medium: { label: '中', dot: 'bg-[var(--accent-amber)]', text: 'text-[var(--accent-amber)]' },
  low: { label: '低', dot: 'bg-[var(--brand-success)]', text: 'text-[var(--brand-success)]' },
} as const

export function NoteTodoDrawer({ isOpen, onClose }: NoteTodoDrawerProps) {
  const { success, info } = useToast()
  const [mode, setMode] = useState<NoteTodoMode>('note')
  const [notes, setNotes] = useLocalStorage<Note[]>('kchat_notes', mockNotes)
  const [todos, setTodos] = useLocalStorage<Todo[]>('kchat_todos', mockTodos)
  const [selectedNote, setSelectedNote] = useState<Note | null>(null)
  const [selectedTodo, setSelectedTodo] = useState<Todo | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingNote, setEditingNote] = useState<Note | null>(null)
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState<'all' | 'pending' | 'completed'>('all')
  const [deleteConfirm, setDeleteConfirm] = useState<DeleteConfirmState | null>(null)

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

  /* ─── Keyboard shortcuts ─── */
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault()
        handleOpenCreateForm()
      }
      if (e.key === 'Escape') {
        if (isFormOpen) {
          handleCancelForm()
        } else if (selectedNote || selectedTodo) {
          setSelectedNote(null)
          setSelectedTodo(null)
        } else {
          onClose()
        }
      }
    }
    if (isOpen) document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose, isFormOpen, selectedNote, selectedTodo])

  /* ─── Sync form state with editing item ─── */
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
        dueDate: editingTodo.dueDate ? new Date(editingTodo.dueDate).toISOString().split('T')[0] : '',
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
  }, [editingNote, editingTodo, mode])

  /* ─── Filtering ─── */
  const filteredNotes = notes.filter(
    (n) =>
      n.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.content.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.tags.some((t) => t.toLowerCase().includes(debouncedSearchQuery.toLowerCase()))
  )

  const filteredTodos = todos.filter((t) => {
    const matchesSearch =
      t.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      t.description.toLowerCase().includes(debouncedSearchQuery.toLowerCase())
    const matchesStatus = activeTab === 'all' || t.status === activeTab
    return matchesSearch && matchesStatus
  })

  /* ─── CRUD handlers ─── */
  const handleCreateNote = useCallback(() => {
    const newNote: Note = {
      id: Date.now().toString(),
      userId: 'default',
      title: formState.title || '无标题',
      content: formState.content,
      category: formState.category,
      tags: formState.tags,
      pinned: formState.pinned,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    setNotes((prev) => [newNote, ...prev])
    setIsFormOpen(false)
    setSelectedNote(newNote)
    success('笔记创建成功')
  }, [formState, setNotes, success])

  const handleUpdateNote = useCallback(() => {
    if (!editingNote) return
    setNotes((prev) =>
      prev.map((n) =>
        n.id === editingNote.id
          ? ({
              ...n,
              title: formState.title || '无标题',
              content: formState.content,
              category: formState.category,
              tags: formState.tags,
              pinned: formState.pinned,
              updatedAt: new Date().toISOString(),
            } as Note)
          : n
      )
    )
    setIsFormOpen(false)
    setEditingNote(null)
    setSelectedNote(null)
    success('笔记更新成功')
  }, [editingNote, formState, setNotes, success])

  const handleDeleteNote = useCallback(
    (id: string) => {
      const note = notes.find((n) => n.id === id)
      if (note) setDeleteConfirm({ type: 'note', id, title: note.title })
    },
    [notes]
  )

  const confirmDeleteNote = useCallback(() => {
    if (!deleteConfirm || deleteConfirm.type !== 'note') return
    setNotes((prev) => prev.filter((n) => n.id !== deleteConfirm.id))
    if (selectedNote?.id === deleteConfirm.id) setSelectedNote(null)
    setDeleteConfirm(null)
    success('笔记已删除')
  }, [deleteConfirm, setNotes, selectedNote, success])

  const handleCreateTodo = useCallback(() => {
    const newTodo: Todo = {
      id: Date.now().toString(),
      userId: 'default',
      title: formState.title || '未命名待办',
      description: formState.description,
      status: 'pending',
      priority: formState.priority,
      dueDate: formState.dueDate || null,
      category: formState.category,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedAt: null,
    }
    setTodos((prev) => [newTodo, ...prev])
    setIsFormOpen(false)
    success('待办创建成功')
  }, [formState, setTodos, success])

  const handleUpdateTodo = useCallback(() => {
    if (!editingTodo) return
    setTodos((prev) =>
      prev.map((t) =>
        t.id === editingTodo.id
          ? ({
              ...t,
              title: formState.title || '未命名待办',
              description: formState.description,
              priority: formState.priority,
              dueDate: formState.dueDate || null,
              category: formState.category,
              updatedAt: new Date().toISOString(),
            } as Todo)
          : t
      )
    )
    setIsFormOpen(false)
    setEditingTodo(null)
    setSelectedTodo(null)
    success('待办更新成功')
  }, [editingTodo, formState, setTodos, success])

  const handleDeleteTodo = useCallback(
    (id: string) => {
      const todo = todos.find((t) => t.id === id)
      if (todo) setDeleteConfirm({ type: 'todo', id, title: todo.title })
    },
    [todos]
  )

  const confirmDeleteTodo = useCallback(() => {
    if (!deleteConfirm || deleteConfirm.type !== 'todo') return
    setTodos((prev) => prev.filter((t) => t.id !== deleteConfirm.id))
    if (selectedTodo?.id === deleteConfirm.id) setSelectedTodo(null)
    setDeleteConfirm(null)
    success('待办已删除')
  }, [deleteConfirm, setTodos, selectedTodo, success])

  const handleToggleTodo = useCallback(
    (id: string) => {
      const todo = todos.find((t) => t.id === id)
      if (!todo) return
      setTodos((prev) =>
        prev.map((t) => {
          if (t.id === id) {
            const newStatus = t.status === 'pending' ? 'completed' : 'pending'
            const message = newStatus === 'completed' ? '任务已完成！' : '任务已恢复'
            setTimeout(() => info(message), 50)
            return {
              ...t,
              status: newStatus,
              completedAt: newStatus === 'completed' ? new Date().toISOString() : null,
              updatedAt: new Date().toISOString(),
            }
          }
          return t
        })
      )
    },
    [todos, info]
  )

  /* ─── UI handlers ─── */
  const handleOpenCreateForm = useCallback(() => {
    setEditingNote(null)
    setEditingTodo(null)
    setIsFormOpen(true)
  }, [])

  const handleEditNote = useCallback((note: Note) => {
    setEditingNote(note)
    setIsFormOpen(true)
  }, [])

  const handleEditTodo = useCallback((todo: Todo) => {
    setEditingTodo(todo)
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
    setIsFormOpen(false)
  }, [])

  const handleBack = useCallback(() => {
    setSelectedNote(null)
    setSelectedTodo(null)
  }, [])

  const handleAddTag = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && formState.newTag.trim() && !formState.tags.includes(formState.newTag.trim())) {
      e.preventDefault()
      setFormState((prev) => ({
        ...prev,
        tags: [...prev.tags, prev.newTag.trim()],
        newTag: '',
      }))
    }
  }

  const handleRemoveTag = (tag: string) => {
    setFormState((prev) => ({
      ...prev,
      tags: prev.tags.filter((t) => t !== tag),
    }))
  }

  /* ─── Derived data ─── */
  const currentDate = new Date().toLocaleDateString('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  })

  const categories = ['默认', '工作', '生活', '学习', '其他']

  const formatDateFull = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('zh-CN')
  }

  const getContentPreview = (content: string) => {
    const trimmed = content.trim().replace(/\n/g, ' ')
    return trimmed.length > 80 ? trimmed.substring(0, 80) + '...' : trimmed
  }

  const isOverdue = (dueDate: string | null, status: string) => {
    if (!dueDate || status === 'completed') return false
    return new Date(dueDate) < new Date()
  }

  const isEditing = editingNote !== null || editingTodo !== null

  /* Determine if we should show detail view */
  const showNoteDetail = mode === 'note' && selectedNote !== null
  const showTodoDetail = mode === 'todo' && selectedTodo !== null
  const showDetail = showNoteDetail || showTodoDetail

  /* ─────────────────────────────────────────────
     RENDER
     ───────────────────────────────────────────── */
  return (
    <>
      <Drawer isOpen={isOpen} onClose={onClose} size='xl'>
        <div className='h-full flex flex-col'>
          {/* ─── Header ─── */}
          <div className='flex-shrink-0 flex items-center justify-between px-4 h-13 border-b border-[var(--border-divider)] bg-[var(--bg-sidebar)]'>
            {showDetail ? (
              <div className='flex items-center gap-2'>
                <button
                  onClick={handleBack}
                  className='p-1.5 -ml-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
                  aria-label='返回列表'
                >
                  <ChevronLeft className='w-4.5 h-4.5 text-[var(--text-secondary)]' />
                </button>
                <h2 className='text-[15px] font-semibold text-[var(--text-primary)]'>
                  {showNoteDetail ? '笔记详情' : '待办详情'}
                </h2>
              </div>
            ) : (
              <h2 className='text-[15px] font-semibold text-[var(--text-primary)]'>
                {mode === 'note' ? '笔记' : '待办'}
              </h2>
            )}
            <div className='flex items-center gap-2'>
              <span className='text-[12px] text-[var(--text-muted)] font-secondary'>{currentDate}</span>
              <button
                onClick={onClose}
                className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
                aria-label='关闭'
              >
                <X className='w-4 h-4 text-[var(--text-muted)]' />
              </button>
            </div>
          </div>

          {/* ─── Content area ─── */}
          <div className='flex-1 flex overflow-hidden relative'>
            {showDetail ? (
              /* ═══════════════════════════════════
                 DETAIL VIEW (full panel)
                 ═══════════════════════════════════ */
              <div className='flex-1 flex flex-col overflow-hidden bg-[var(--bg-primary)]'>
                {showNoteDetail && selectedNote ? (
                  <div className='flex-1 overflow-y-auto'>
                    <div className='max-w-lg mx-auto p-6'>
                      {/* Title + meta */}
                      <div className='mb-6'>
                        <h1 className='text-[20px] font-semibold text-[var(--text-primary)] leading-tight mb-3'>
                          {selectedNote.title}
                        </h1>
                        <div className='flex items-center gap-2 flex-wrap'>
                          <span className='px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[11px] font-medium'>
                            {selectedNote.category}
                          </span>
                          {selectedNote.tags.map((tag) => (
                            <span
                              key={tag}
                              className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-muted)] rounded-full text-[11px]'
                            >
                              #{tag}
                            </span>
                          ))}
                        </div>
                      </div>

                      {/* Content */}
                      <div className='mb-8 pt-4 border-t border-[var(--border-divider)]/50'>
                        <p className='text-[14px] text-[var(--text-secondary)] whitespace-pre-wrap leading-[1.8]'>
                          {selectedNote.content}
                        </p>
                      </div>

                      {/* Footer */}
                      <div className='flex items-center justify-between text-[12px] text-[var(--text-muted)] pt-5 mt-2 border-t border-[var(--border-divider)]'>
                        <span>创建于 {formatDateFull(selectedNote.createdAt)}</span>
                        <div className='flex items-center gap-1'>
                          <span className='mr-2'>更新于 {formatDateFull(selectedNote.updatedAt)}</span>
                          <button
                            onClick={() => handleEditNote(selectedNote)}
                            className='flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[12px] font-medium text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] transition-colors'
                          >
                            <Edit3 className='w-3.5 h-3.5' />
                            编辑
                          </button>
                          <button
                            onClick={() => handleDeleteNote(selectedNote.id)}
                            className='flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-danger)] hover:bg-[var(--brand-danger)]/[0.06] transition-colors'
                          >
                            <Trash2 className='w-3.5 h-3.5' />
                            删除
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : showTodoDetail && selectedTodo ? (
                  <div className='flex-1 overflow-y-auto'>
                    <div className='max-w-lg mx-auto p-6'>
                      {/* Title + status */}
                      <div className='flex items-start gap-3 mb-6'>
                        <button
                          onClick={() => handleToggleTodo(selectedTodo.id)}
                          className='mt-0.5 flex-shrink-0'
                          aria-label={selectedTodo.status === 'completed' ? '标记为未完成' : '标记为完成'}
                        >
                          {selectedTodo.status === 'completed' ? (
                            <CheckCircle2 className='w-5 h-5 text-[var(--brand-primary)]' />
                          ) : (
                            <Circle className='w-5 h-5 text-[var(--text-muted)]' />
                          )}
                        </button>
                        <div className='flex-1'>
                          <h1
                            className={`text-[20px] font-semibold leading-tight ${
                              selectedTodo.status === 'completed'
                                ? 'text-[var(--text-muted)] line-through'
                                : 'text-[var(--text-primary)]'
                            }`}
                          >
                            {selectedTodo.title}
                          </h1>
                          <div className='flex items-center gap-2 mt-2'>
                            <span
                              className={`px-2 py-0.5 rounded-full text-[11px] font-medium ${
                                selectedTodo.status === 'completed'
                                  ? 'bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)]'
                                  : 'bg-[var(--accent-amber)]/[0.1] text-[var(--accent-amber)]'
                              }`}
                            >
                              {selectedTodo.status === 'completed' ? '已完成' : '进行中'}
                            </span>
                            <span className={`text-[11px] font-medium ${priorityMeta[selectedTodo.priority].text}`}>
                              {priorityMeta[selectedTodo.priority].label}优先级
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Description */}
                      {selectedTodo.description && (
                        <div className='mb-6'>
                          <p className='text-[15px] text-[var(--text-secondary)] whitespace-pre-wrap leading-relaxed'>
                            {selectedTodo.description}
                          </p>
                        </div>
                      )}

                      {/* Meta grid */}
                      <div className='grid grid-cols-2 gap-x-8 gap-y-3 py-4 border-t border-[var(--border-divider)]'>
                        <div className='flex items-center gap-2'>
                          <FileText className='w-3.5 h-3.5 text-[var(--text-muted)]/60' />
                          <span className='text-[12px] text-[var(--text-muted)]'>分类</span>
                          <span className='text-[13px] font-medium text-[var(--text-secondary)]'>{selectedTodo.category}</span>
                        </div>
                        {selectedTodo.dueDate && (
                          <div className='flex items-center gap-2'>
                            <Calendar className='w-3.5 h-3.5 text-[var(--text-muted)]/60' />
                            <span className='text-[12px] text-[var(--text-muted)]'>截止</span>
                            <span
                              className={`text-[13px] font-medium ${
                                isOverdue(selectedTodo.dueDate, selectedTodo.status)
                                  ? 'text-[var(--brand-danger)]'
                                  : 'text-[var(--text-secondary)]'
                              }`}
                            >
                              {formatDateFull(selectedTodo.dueDate)}
                            </span>
                          </div>
                        )}
                        <div className='flex items-center gap-2'>
                          <Clock className='w-3.5 h-3.5 text-[var(--text-muted)]/60' />
                          <span className='text-[12px] text-[var(--text-muted)]'>创建</span>
                          <span className='text-[12px] text-[var(--text-secondary)]'>
                            {formatDateFull(selectedTodo.createdAt)}
                          </span>
                        </div>
                        {selectedTodo.completedAt && (
                          <div className='flex items-center gap-2'>
                            <CheckCircle2 className='w-3.5 h-3.5 text-[var(--brand-primary)]/60' />
                            <span className='text-[12px] text-[var(--text-muted)]'>完成</span>
                            <span className='text-[12px] text-[var(--text-secondary)]'>
                              {formatDateFull(selectedTodo.completedAt)}
                            </span>
                          </div>
                        )}
                      </div>

                      {/* Actions */}
                      <div className='flex items-center gap-2 pt-4 border-t border-[var(--border-divider)] mt-2'>
                        <button
                          onClick={() => handleEditTodo(selectedTodo)}
                          className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-medium text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] transition-colors'
                        >
                          <Edit3 className='w-3.5 h-3.5' />
                          编辑
                        </button>
                        <button
                          onClick={() => handleDeleteTodo(selectedTodo.id)}
                          className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-danger)] hover:bg-[var(--brand-danger)]/[0.06] transition-colors'
                        >
                          <Trash2 className='w-3.5 h-3.5' />
                          删除
                        </button>
                      </div>
                    </div>
                  </div>
                ) : null}
              </div>
            ) : (
              /* ═══════════════════════════════════
                 LIST VIEW (full panel)
                 ═══════════════════════════════════ */
              <div className='flex-1 flex flex-col overflow-hidden bg-[var(--bg-sidebar)]'>
                {/* Search bar */}
                <div className='px-4 pt-4 pb-2'>
                  <div className='relative'>
                    <Search className='absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]/60' />
                    <input
                      type='text'
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      placeholder={mode === 'note' ? '搜索笔记...' : '搜索待办...'}
                      className='w-full pl-10 pr-12 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-[13px] font-secondary text-[var(--text-primary)] placeholder:text-[var(--text-muted)]/60 focus:outline-none focus:border-[var(--brand-primary)]/30 transition-colors'
                    />
                    <button
                      onClick={handleOpenCreateForm}
                      className='absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-lg bg-[var(--brand-primary)] text-white hover:brightness-110 transition-all'
                      aria-label={mode === 'note' ? '新建笔记' : '新建待办'}
                      title={mode === 'note' ? '新建笔记 (Ctrl+N)' : '新建待办 (Ctrl+N)'}
                    >
                      <Plus className='w-3.5 h-3.5' />
                    </button>
                  </div>
                </div>

                {/* Mode switcher + Todo filters */}
                <div className='px-4 pb-3 flex items-center gap-2 flex-wrap'>
                  {/* Mode pills */}
                  <div className='flex items-center gap-1 bg-[var(--bg-input)] rounded-lg p-0.5'>
                    {([
                      { key: 'note' as const, label: '笔记', count: notes.length },
                      { key: 'todo' as const, label: '待办', count: todos.length },
                    ]).map(({ key, label, count }) => (
                      <button
                        key={key}
                        onClick={() => handleModeChange(key)}
                        className={`px-3 py-1.5 rounded-md text-[12px] font-medium transition-all duration-200 ${
                          mode === key
                            ? 'bg-[var(--bg-sidebar)] text-[var(--text-primary)] shadow-sm'
                            : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                        }`}
                      >
                        {label}
                        <span className={`ml-1 text-[10px] ${mode === key ? 'text-[var(--text-muted)]' : 'text-[var(--text-muted)]/50'}`}>
                          {count}
                        </span>
                      </button>
                    ))}
                  </div>

                  {/* Todo filter pills */}
                  {mode === 'todo' && (
                    <div className='flex items-center gap-0.5 ml-2'>
                      {([
                        { key: 'all' as const, label: '全部' },
                        { key: 'pending' as const, label: '进行中' },
                        { key: 'completed' as const, label: '已完成' },
                      ]).map((tab) => (
                        <button
                          key={tab.key}
                          onClick={() => setActiveTab(tab.key)}
                          className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-colors ${
                            activeTab === tab.key
                              ? 'bg-[var(--brand-primary)]/[0.12] text-[var(--brand-primary)]'
                              : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                          }`}
                        >
                          {tab.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>

                {/* List */}
                <div className='flex-1 overflow-y-auto scrollbar-auto-hide'>
                  {mode === 'note' ? (
                    /* ─── Note list ─── */
                    filteredNotes.length === 0 ? (
                      <div className='flex flex-col items-center justify-center py-20 px-4'>
                        <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/50 flex items-center justify-center mb-4'>
                          <FileText className='w-6 h-6 text-[var(--text-muted)]/40' />
                        </div>
                        <p className='text-[14px] font-medium text-[var(--text-primary)] mb-1'>暂无笔记</p>
                        <p className='text-[12px] text-[var(--text-muted)] mb-4'>创建第一条笔记开始记录</p>
                        <button
                          onClick={handleOpenCreateForm}
                          className='inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'
                        >
                          <Plus className='w-3.5 h-3.5' />
                          新建笔记
                        </button>
                        <p className='text-[11px] text-[var(--text-muted)]/40 mt-2'>快捷键 Ctrl+N</p>
                      </div>
                    ) : (
                      <div className='px-3'>
                        {/* Section header */}
                        <div className='flex items-center justify-between px-2 pt-2 pb-2'>
                          <span className='text-[12px] font-semibold text-[var(--text-muted)] tracking-wide'>
                            {searchQuery ? '搜索结果' : '全部笔记'}
                          </span>
                          <span className='text-[11px] font-medium text-[var(--text-muted)]/60'>
                            {filteredNotes.length}条
                          </span>
                        </div>
                        {/* Note items */}
                        {filteredNotes.map((note) => (
                          <NoteListItem
                            key={note.id}
                            note={note}
                            onSelect={() => setSelectedNote(note)}
                            onEdit={() => handleEditNote(note)}
                            formatDateFull={formatDateFull}
                            getContentPreview={getContentPreview}
                          />
                        ))}
                      </div>
                    )
                  ) : /* ─── Todo list ─── */
                  filteredTodos.length === 0 ? (
                    <div className='flex flex-col items-center justify-center py-20 px-4'>
                      <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/50 flex items-center justify-center mb-4'>
                        <ListTodo className='w-6 h-6 text-[var(--text-muted)]/40' />
                      </div>
                      <p className='text-[14px] font-medium text-[var(--text-primary)] mb-1'>暂无待办</p>
                      <p className='text-[12px] text-[var(--text-muted)] mb-4'>创建第一条待办开始管理</p>
                      <button
                        onClick={handleOpenCreateForm}
                        className='inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'
                      >
                        <Plus className='w-3.5 h-3.5' />
                        新建待办
                      </button>
                      <p className='text-[11px] text-[var(--text-muted)]/40 mt-2'>快捷键 Ctrl+N</p>
                    </div>
                  ) : (
                    <div className='px-3'>
                      {/* Pending section */}
                      {filteredTodos.filter((t) => t.status === 'pending').length > 0 && (
                        <div className='pt-2'>
                          <div className='flex items-center justify-between px-2 pb-2'>
                            <span className='text-[12px] font-semibold text-[var(--text-muted)] tracking-wide'>
                              进行中
                            </span>
                            <span className='text-[11px] font-medium text-[var(--text-muted)]/60'>
                              {filteredTodos.filter((t) => t.status === 'pending').length}条
                            </span>
                          </div>
                          {filteredTodos
                            .filter((t) => t.status === 'pending')
                            .map((todo) => (
                              <TodoListItem
                                key={todo.id}
                                todo={todo}
                                onSelect={() => setSelectedTodo(todo)}
                                onToggle={() => handleToggleTodo(todo.id)}
                                onEdit={() => handleEditTodo(todo)}
                                formatDateFull={formatDateFull}
                              />
                            ))}
                        </div>
                      )}
                      {/* Completed section */}
                      {filteredTodos.filter((t) => t.status === 'completed').length > 0 && (
                        <div className='pt-2'>
                          {filteredTodos.filter((t) => t.status === 'pending').length > 0 && (
                            <div className='mx-2 my-2 border-t border-[var(--border-divider)]/60' />
                          )}
                          <div className='flex items-center justify-between px-2 pb-2'>
                            <span className='text-[12px] font-semibold text-[var(--text-muted)] tracking-wide'>
                              已完成
                            </span>
                            <span className='text-[11px] font-medium text-[var(--text-muted)]/60'>
                              {filteredTodos.filter((t) => t.status === 'completed').length}条
                            </span>
                          </div>
                          {filteredTodos
                            .filter((t) => t.status === 'completed')
                            .map((todo) => (
                              <TodoListItem
                                key={todo.id}
                                todo={todo}
                                onSelect={() => setSelectedTodo(todo)}
                                onToggle={() => handleToggleTodo(todo.id)}
                                onEdit={() => handleEditTodo(todo)}
                                formatDateFull={formatDateFull}
                              />
                            ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* ─── Form overlay ─── */}
            {isFormOpen && (
              <div className='absolute inset-0 z-10 flex'>
                {/* Backdrop */}
                <div className='absolute inset-0 bg-[var(--bg-overlay)]/50 backdrop-blur-[2px]' onClick={handleCancelForm} />

                {/* Form panel — slides in from right */}
                <div className='relative ml-auto w-full max-w-md h-full bg-[var(--bg-sidebar)] border-l border-[var(--border-divider)] shadow-xl overflow-y-auto animate-slide-in-right'>
                  {/* Form header */}
                  <div className='sticky top-0 z-10 flex items-center justify-between px-5 py-3.5 bg-[var(--bg-sidebar)]/95 backdrop-blur-sm border-b border-[var(--border-divider)]'>
                    <h3 className='text-[15px] font-semibold text-[var(--text-primary)]'>
                      {isEditing ? '编辑' : '新建'}
                      {mode === 'note' ? '笔记' : '待办'}
                    </h3>
                    <button
                      onClick={handleCancelForm}
                      className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
                      aria-label='关闭'
                    >
                      <X className='w-4 h-4 text-[var(--text-muted)]' />
                    </button>
                  </div>

                  {/* Form body */}
                  <div className='p-5 space-y-4'>
                    {/* Title */}
                    <div>
                      <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                        标题
                      </label>
                      <input
                        type='text'
                        value={formState.title}
                        onChange={(e) => setFormState((prev) => ({ ...prev, title: e.target.value }))}
                        placeholder={mode === 'note' ? '输入笔记标题...' : '输入待办标题...'}
                        className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                        autoFocus
                      />
                    </div>

                    {/* Note content */}
                    {mode === 'note' && (
                      <div>
                        <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                          内容
                        </label>
                        <textarea
                          value={formState.content}
                          onChange={(e) => setFormState((prev) => ({ ...prev, content: e.target.value }))}
                          placeholder='开始记录...'
                          rows={5}
                          className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none'
                        />
                      </div>
                    )}

                    {/* Todo description */}
                    {mode === 'todo' && (
                      <div>
                        <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                          描述
                        </label>
                        <textarea
                          value={formState.description}
                          onChange={(e) => setFormState((prev) => ({ ...prev, description: e.target.value }))}
                          placeholder='添加描述（可选）'
                          rows={3}
                          className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none'
                        />
                      </div>
                    )}

                    {/* Category + Pin/Priority row */}
                    <div className='grid grid-cols-2 gap-3'>
                      <div>
                        <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                          分类
                        </label>
                        <select
                          value={formState.category}
                          onChange={(e) => setFormState((prev) => ({ ...prev, category: e.target.value }))}
                          className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                        >
                          {categories.map((cat) => (
                            <option key={cat} value={cat}>
                              {cat}
                            </option>
                          ))}
                        </select>
                      </div>

                      {mode === 'note' && (
                        <div className='flex items-end'>
                          <button
                            onClick={() => setFormState((prev) => ({ ...prev, pinned: !prev.pinned }))}
                            className={`flex items-center gap-2 px-3 py-2 rounded-lg text-[13px] font-medium transition-all w-full justify-center ${
                              formState.pinned
                                ? 'bg-[var(--accent-amber)]/[0.1] text-[var(--accent-amber)] border border-[var(--accent-amber)]/20'
                                : 'bg-[var(--bg-input)] text-[var(--text-secondary)] border border-[var(--border-primary)] hover:bg-[var(--bg-hover)]'
                            }`}
                          >
                            <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />
                            置顶
                          </button>
                        </div>
                      )}

                      {mode === 'todo' && (
                        <div>
                          <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                            优先级
                          </label>
                          <div className='flex gap-1'>
                            {(['high', 'medium', 'low'] as const).map((p) => (
                              <button
                                key={p}
                                onClick={() => setFormState((prev) => ({ ...prev, priority: p }))}
                                className={`flex-1 py-2 rounded-lg text-[12px] font-medium transition-all ${
                                  formState.priority === p
                                    ? `${priorityMeta[p].text} bg-[var(--bg-hover)]`
                                    : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                                }`}
                              >
                                {priorityMeta[p].label}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Note tags */}
                    {mode === 'note' && (
                      <div>
                        <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                          标签
                        </label>
                        {formState.tags.length > 0 && (
                          <div className='flex flex-wrap gap-1.5 mb-2'>
                            {formState.tags.map((tag) => (
                              <span
                                key={tag}
                                className='flex items-center gap-1 px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[11px] font-medium'
                              >
                                {tag}
                                <button onClick={() => handleRemoveTag(tag)} className='hover:opacity-70'>
                                  <X className='w-3 h-3' />
                                </button>
                              </span>
                            ))}
                          </div>
                        )}
                        <input
                          type='text'
                          value={formState.newTag}
                          onChange={(e) => setFormState((prev) => ({ ...prev, newTag: e.target.value }))}
                          onKeyDown={handleAddTag}
                          placeholder='输入标签，按 Enter 添加'
                          className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                        />
                      </div>
                    )}

                    {/* Todo due date */}
                    {mode === 'todo' && (
                      <div>
                        <label className='block text-[12px] font-medium text-[var(--text-muted)] mb-1.5 uppercase tracking-wider'>
                          截止日期
                        </label>
                        <input
                          type='date'
                          value={formState.dueDate}
                          onChange={(e) => setFormState((prev) => ({ ...prev, dueDate: e.target.value }))}
                          className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                        />
                      </div>
                    )}
                  </div>

                  {/* Form footer */}
                  <div className='sticky bottom-0 flex items-center justify-end gap-2 px-5 py-3.5 bg-[var(--bg-sidebar)]/95 backdrop-blur-sm border-t border-[var(--border-divider)]'>
                    <button
                      onClick={handleCancelForm}
                      className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-lg text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'
                    >
                      取消
                    </button>
                    <button
                      onClick={
                        mode === 'note'
                          ? editingNote
                            ? handleUpdateNote
                            : handleCreateNote
                          : editingTodo
                          ? handleUpdateTodo
                          : handleCreateTodo
                      }
                      className='px-4 py-2 rounded-lg text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'
                    >
                      保存
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </Drawer>

      {/* ─── Delete confirmation dialog ─── */}
      {deleteConfirm && (
        <div className='fixed inset-0 z-[60] flex items-center justify-center'>
          <div className='absolute inset-0 bg-[var(--bg-overlay)]' onClick={() => setDeleteConfirm(null)} />
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
    </>
  )
}

/* ═══════════════════════════════════════════════════
   NoteListItem — card-style list row for notes
   ═══════════════════════════════════════════════════ */
interface NoteListItemProps {
  note: Note
  onSelect: () => void
  onEdit: () => void
  formatDateFull: (dateString: string) => string
  getContentPreview: (content: string) => string
}

function NoteListItem({ note, onSelect, onEdit, formatDateFull, getContentPreview }: NoteListItemProps) {
  return (
    <div
      className='group flex items-center gap-3 px-3 py-3 cursor-pointer hover:bg-[var(--bg-hover)]/60 transition-colors border-b border-[var(--border-divider)]/40'
      onClick={onSelect}
    >
      {/* Left icon — pin or file */}
      <div className='flex-shrink-0'>
        {note.pinned ? (
          <Pin className='w-4 h-4 text-[var(--accent-amber)] fill-current' />
        ) : (
          <FileText className='w-4 h-4 text-[var(--text-muted)]/50' />
        )}
      </div>

      {/* Content */}
      <div className='flex-1 min-w-0'>
        <div className='flex items-center justify-between gap-2'>
          <h3 className='text-[13px] font-medium text-[var(--text-primary)] truncate'>
            {note.title || '无标题'}
          </h3>
          <span className='text-[11px] text-[var(--text-muted)]/60 flex-shrink-0'>
            {formatDateFull(new Date(note.updatedAt).toISOString())}
          </span>
        </div>
        <p className='text-[12px] text-[var(--text-muted)] truncate mt-0.5'>
          {getContentPreview(note.content)}
        </p>
      </div>

      {/* Edit icon — hover reveal */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          onEdit()
        }}
        className='flex-shrink-0 p-1 rounded-md opacity-0 group-hover:opacity-100 hover:bg-[var(--bg-hover)] transition-all'
        aria-label='编辑'
      >
        <Edit3 className='w-3.5 h-3.5 text-[var(--text-muted)]' />
      </button>
    </div>
  )
}

/* ═══════════════════════════════════════════════════
   TodoListItem — card-style list row for todos
   ═══════════════════════════════════════════════════ */
interface TodoListItemProps {
  todo: Todo
  onSelect: () => void
  onToggle: () => void
  onEdit: () => void
  formatDateFull: (dateString: string) => string
}

function TodoListItem({ todo, onSelect, onToggle, onEdit, formatDateFull }: TodoListItemProps) {
  return (
    <div
      className='group flex items-center gap-3 px-3 py-3 cursor-pointer hover:bg-[var(--bg-hover)]/60 transition-colors border-b border-[var(--border-divider)]/40'
      onClick={onSelect}
    >
      {/* Checkbox */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          onToggle()
        }}
        className='flex-shrink-0'
        aria-label={todo.status === 'completed' ? '标记为未完成' : '标记为完成'}
      >
        {todo.status === 'completed' ? (
          <CheckCircle2 className='w-4 h-4 text-[var(--brand-primary)]' />
        ) : (
          <Circle className='w-4 h-4 text-[var(--text-muted)]/50' />
        )}
      </button>

      {/* Content */}
      <div className='flex-1 min-w-0'>
        <div className='flex items-center justify-between gap-2'>
          <h3
            className={`text-[13px] font-medium truncate ${
              todo.status === 'completed'
                ? 'text-[var(--text-muted)] line-through'
                : 'text-[var(--text-primary)]'
            }`}
          >
            {todo.title}
          </h3>
          <span className='text-[11px] text-[var(--text-muted)]/60 flex-shrink-0'>
            {formatDateFull(new Date(todo.createdAt).toISOString())}
          </span>
        </div>
        {todo.description && (
          <p className='text-[12px] text-[var(--text-muted)] truncate mt-0.5'>
            {todo.description}
          </p>
        )}
      </div>

      {/* Edit icon — hover reveal */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          onEdit()
        }}
        className='flex-shrink-0 p-1 rounded-md opacity-0 group-hover:opacity-100 hover:bg-[var(--bg-hover)] transition-all'
        aria-label='编辑'
      >
        <Edit3 className='w-3.5 h-3.5 text-[var(--text-muted)]' />
      </button>
    </div>
  )
}
