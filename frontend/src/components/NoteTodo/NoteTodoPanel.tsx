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
  Star,
  ChevronLeft,
  ChevronDown,
} from 'lucide-react'
import { useToast } from '../../hooks/useToast'
import { useLocalStorage } from '../../hooks/useLocalStorage'
import { useDebounce } from '../../hooks/useDebounce'
import type { Note, Todo, NoteTodoMode } from '../../types/note-todo'

interface NoteTodoPanelProps {
  isOpen: boolean
  onClose: () => void
}

const mockNotes: Note[] = [
  {
    id: '1',
    userId: 'default',
    title: '项目会议记录',
    content:
      '讨论了Q3产品路线图，确定了三个核心功能的开发优先级：\n\n## 一、项目定位\nKChat是一个基于大模型的智能对话平台，致力于为用户提供高效、智能、个性化的AI对话体验。\n\n## 二、核心功能\n- 多模态对话\n- 智能推荐引擎\n- 记忆体（Agent）\n- 知识图谱\n- RAG检索增强生成\n- 笔记与待办管理',
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
    content:
      'useState: 用于管理组件状态\nuseEffect: 用于处理副作用\nuseContext: 用于跨组件传递数据',
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

const priorityMeta = {
  high: { label: '高', dot: 'bg-[var(--brand-danger)]', text: 'text-[var(--brand-danger)]' },
  medium: { label: '中', dot: 'bg-[var(--accent-amber)]', text: 'text-[var(--accent-amber)]' },
  low: { label: '低', dot: 'bg-[var(--brand-success)]', text: 'text-[var(--brand-success)]' },
} as const

export function NoteTodoPanel({ isOpen, onClose }: NoteTodoPanelProps) {
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
  const [filterTags, setFilterTags] = useState<string[]>([])
  const [filterExpanded, setFilterExpanded] = useState(true)

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

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
        e.preventDefault()
        handleOpenCreateForm()
      }
      if (e.key === 'Escape') {
        if (isFormOpen) {
          handleCancelForm()
        }
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
  }, [editingNote, editingTodo, mode])

  const filteredNotes = notes.filter((n) => {
    const matchesSearch =
      n.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.content.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      n.tags.some((t) => t.toLowerCase().includes(debouncedSearchQuery.toLowerCase()))
    const matchesTag = filterTags.length === 0 || filterTags.some((ft) => n.tags.includes(ft))
    return matchesSearch && matchesTag
  })

  const filteredTodos = todos.filter((t) => {
    const matchesSearch =
      t.title.toLowerCase().includes(debouncedSearchQuery.toLowerCase()) ||
      t.description.toLowerCase().includes(debouncedSearchQuery.toLowerCase())
    const matchesStatus = activeTab === 'all' || t.status === activeTab
    return matchesSearch && matchesStatus
  })

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
    setFilterTags([])
    setIsFormOpen(false)
  }, [])

  const handleAddTag = (e: React.KeyboardEvent) => {
    if (
      e.key === 'Enter' &&
      formState.newTag.trim() &&
      !formState.tags.includes(formState.newTag.trim())
    ) {
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

  const isEditing = editingNote !== null || editingTodo !== null

  const allTags = [...new Set(notes.flatMap((n) => n.tags))].map((tag) => ({
    name: tag,
    count: notes.filter((n) => n.tags.includes(tag)).length,
  }))

  return (
    <>
      <div
        className={`fixed right-6 top-6 bottom-6 w-[400px] z-40 transition-transform duration-300 ease-out ${
          isOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <div className='h-full card-float-solid flex flex-col overflow-hidden'>
          <div className='flex-shrink-0 flex items-center justify-between px-4 h-12 border-b border-[var(--border-divider)] bg-[var(--bg-sidebar)]'>
            <div className='flex items-center gap-1'>
              <button
                onClick={() => handleModeChange('note')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[13px] font-medium transition-all duration-200 ${
                  mode === 'note'
                    ? 'bg-[var(--brand-primary)]/[0.1] text-[var(--brand-primary)]'
                    : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                }`}
              >
                <FileText className='w-4 h-4' />
                笔记
                <span className={`text-[12px] font-normal ${mode === 'note' ? 'opacity-70' : 'opacity-50'}`}>
                  {notes.length}
                </span>
              </button>
              <button
                onClick={() => handleModeChange('todo')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[13px] font-medium transition-all duration-200 ${
                  mode === 'todo'
                    ? 'bg-[var(--brand-primary)]/[0.1] text-[var(--brand-primary)]'
                    : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                }`}
              >
                <ListTodo className='w-4 h-4' />
                待办
                <span className={`text-[12px] font-normal ${mode === 'todo' ? 'opacity-70' : 'opacity-50'}`}>
                  {todos.length}
                </span>
              </button>
            </div>
            <button
              onClick={onClose}
              className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='关闭'
            >
              <X className='w-4 h-4 text-[var(--text-muted)]' />
            </button>
          </div>

          <div className='flex-1 flex flex-col overflow-hidden'>
            <div className='flex-shrink-0 p-3 border-b border-[var(--border-divider)]'>
              <div className='flex items-center gap-2'>
                <div className='relative flex-1'>
                  <Search className='absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]' />
                  <input
                    type='text'
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder={mode === 'note' ? '搜索笔记...' : '搜索待办...'}
                    className='w-full pl-9 pr-3 py-2 bg-[var(--bg-input)] border border-transparent rounded-lg text-[13px] font-secondary text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 transition-colors'
                  />
                </div>
                <button
                  onClick={handleOpenCreateForm}
                  className='flex items-center gap-1.5 px-3 py-2 rounded-lg bg-[var(--brand-primary)] text-white text-[12px] font-medium hover:brightness-110 transition-all'
                  aria-label={mode === 'note' ? '新建笔记' : '新建待办'}
                >
                  <Plus className='w-3.5 h-3.5' />
                  新建
                </button>
              </div>

              {mode === 'note' && allTags.length > 0 && (
                <div className='mt-3'>
                  <button
                    onClick={() => setFilterExpanded(!filterExpanded)}
                    className='flex items-center gap-1 text-[10px] font-medium text-[var(--text-muted)]/60 uppercase tracking-widest mb-1.5 px-0.5 hover:text-[var(--text-muted)] transition-colors'
                  >
                    <ChevronDown
                      className={`w-3 h-3 transition-transform duration-200 ${filterExpanded ? '' : '-rotate-90'}`}
                    />
                    筛选标签
                    {filterTags.length > 0 && (
                      <span className='text-[var(--brand-primary)] normal-case tracking-normal'>
                        ({filterTags.length})
                      </span>
                    )}
                  </button>
                  {filterExpanded && (
                    <div className='flex items-center gap-1.5 flex-wrap'>
                      <button
                        onClick={() => setFilterTags([])}
                        className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-all ${
                          filterTags.length === 0
                            ? 'bg-[var(--brand-primary)]/[0.12] text-[var(--brand-primary)]'
                            : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]/50'
                        }`}
                      >
                        全部
                      </button>
                      {allTags.map(({ name, count }) => (
                        <button
                          key={name}
                          onClick={() =>
                            setFilterTags((prev) =>
                              prev.includes(name)
                                ? prev.filter((t) => t !== name)
                                : [...prev, name],
                            )
                          }
                          className={`px-2.5 py-1 rounded-full text-[11px] font-medium transition-all ${
                            filterTags.includes(name)
                              ? 'bg-[var(--brand-primary)]/[0.12] text-[var(--brand-primary)]'
                              : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)] hover:bg-[var(--bg-hover)]/50'
                          }`}
                        >
                          {name}
                          <span className='ml-1 opacity-50'>{count}</span>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {mode === 'todo' && (
                <div className='flex items-center gap-0.5 mt-3 bg-[var(--bg-hover)]/30 rounded-lg p-0.5'>
                  {[
                    { key: 'all' as const, label: '全部', count: todos.length },
                    {
                      key: 'pending' as const,
                      label: '进行中',
                      count: todos.filter((t) => t.status === 'pending').length,
                    },
                    {
                      key: 'completed' as const,
                      label: '已完成',
                      count: todos.filter((t) => t.status === 'completed').length,
                    },
                  ].map((tab) => (
                    <button
                      key={tab.key}
                      onClick={() => setActiveTab(tab.key)}
                      className={`flex-1 px-2 py-1.5 rounded-md text-[11px] font-medium transition-all ${
                        activeTab === tab.key
                          ? 'bg-[var(--bg-sidebar)] text-[var(--text-primary)] shadow-sm'
                          : 'text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                      }`}
                    >
                      {tab.label}
                      <span className='ml-1 opacity-50'>{tab.count}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            {selectedNote ? (
              <div className='flex-1 flex flex-col overflow-hidden'>
                <div className='flex-shrink-0 flex items-center justify-between px-4 py-2.5 border-b border-[var(--border-divider)]'>
                  <button
                    onClick={() => { setSelectedNote(null); if (isFormOpen) handleCancelForm(); }}
                    className='flex items-center gap-1 text-[13px] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
                  >
                    <ChevronLeft className='w-4 h-4' />
                    返回
                  </button>
                  {!isFormOpen && (
                    <div className='flex items-center gap-0.5'>
                      <button onClick={() => handleEditNote(selectedNote)} className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors' aria-label='编辑'>
                        <Edit3 className='w-4 h-4 text-[var(--text-muted)]' />
                      </button>
                      <button onClick={() => handleDeleteNote(selectedNote.id)} className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors' aria-label='删除'>
                        <Trash2 className='w-4 h-4 text-[var(--text-muted)]' />
                      </button>
                    </div>
                  )}
                </div>
                {!isFormOpen ? (
                  <div className='flex-1 overflow-y-auto p-4'>
                    <div className='flex items-center gap-2 mb-2'>
                      {selectedNote.pinned && <Star className='w-4 h-4 text-[var(--accent-amber)] fill-current flex-shrink-0' />}
                      <h2 className='text-[16px] font-semibold text-[var(--text-primary)] leading-snug'>{selectedNote.title}</h2>
                    </div>
                    <div className='flex items-center gap-2 mb-4 text-[11px] text-[var(--text-muted)] flex-wrap'>
                      <span>{formatDateFull(selectedNote.updatedAt)}</span>
                      <span className='w-1 h-1 rounded-full bg-[var(--text-muted)]/30' />
                      <span className='px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[10px] font-medium'>
                        {selectedNote.category}
                      </span>
                      {selectedNote.tags.map((tag) => (
                        <span key={tag} className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-muted)] rounded-full text-[10px]'>#{tag}</span>
                      ))}
                    </div>
                    <div className='text-[13px] text-[var(--text-secondary)] leading-[1.75] whitespace-pre-wrap break-words'>
                      {selectedNote.content}
                    </div>
                  </div>
                ) : (
                  <div className='flex-1 flex flex-col overflow-hidden'>
                    <div className='flex-1 overflow-y-auto p-4 space-y-4'>
                      <div>
                        <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>标题</label>
                        <input type='text' value={formState.title} onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))} className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors' />
                      </div>
                      <div>
                        <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>内容</label>
                        <textarea value={formState.content} onChange={(e) => setFormState((p) => ({ ...p, content: e.target.value }))} rows={10} className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none leading-relaxed' />
                      </div>
                      <div className='grid grid-cols-2 gap-3'>
                        <div>
                          <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>分类</label>
                          <select value={formState.category} onChange={(e) => setFormState((p) => ({ ...p, category: e.target.value }))} className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'>
                            {categories.map((c) => <option key={c} value={c}>{c}</option>)}
                          </select>
                        </div>
                        <button onClick={() => setFormState((p) => ({ ...p, pinned: !p.pinned }))} className={`flex items-center gap-2 px-3 py-2 rounded-xl text-[13px] font-medium transition-all justify-center mt-5 ${formState.pinned ? 'bg-[var(--accent-amber)]/[0.1] text-[var(--accent-amber)] border border-[var(--accent-amber)]/20' : 'bg-[var(--bg-input)] text-[var(--text-secondary)] border border-[var(--border-primary)]'}`}>
                          <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />置顶
                        </button>
                      </div>
                      <div>
                        <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>标签</label>
                        {formState.tags.length > 0 && (
                          <div className='flex flex-wrap gap-1.5 mb-2'>
                            {formState.tags.map((tag) => (
                              <span key={tag} className='flex items-center gap-1 px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-[11px] font-medium'>
                                {tag}<button onClick={() => handleRemoveTag(tag)} className='hover:opacity-70'><X className='w-3 h-3' /></button>
                              </span>
                            ))}
                          </div>
                        )}
                        <input type='text' value={formState.newTag} onChange={(e) => setFormState((p) => ({ ...p, newTag: e.target.value }))} onKeyDown={handleAddTag} placeholder='输入标签，按 Enter 添加' className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors' />
                      </div>
                    </div>
                    <div className='flex-shrink-0 flex items-center justify-end gap-2 px-4 py-3 border-t border-[var(--border-divider)]'>
                      <button onClick={handleCancelForm} className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'>取消</button>
                      <button onClick={handleUpdateNote} className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'>保存</button>
                    </div>
                  </div>
                )}
              </div>
            ) : selectedTodo ? (
              <div className='flex-1 flex flex-col overflow-hidden'>
                <div className='flex-shrink-0 flex items-center justify-between px-4 py-2.5 border-b border-[var(--border-divider)]'>
                  <button
                    onClick={() => { setSelectedTodo(null); if (isFormOpen) handleCancelForm(); }}
                    className='flex items-center gap-1 text-[13px] text-[var(--text-muted)] hover:text-[var(--text-primary)] transition-colors'
                  >
                    <ChevronLeft className='w-4 h-4' />
                    返回
                  </button>
                  {!isFormOpen && (
                    <div className='flex items-center gap-0.5'>
                      <button onClick={() => handleEditTodo(selectedTodo)} className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors' aria-label='编辑'>
                        <Edit3 className='w-4 h-4 text-[var(--text-muted)]' />
                      </button>
                      <button onClick={() => handleDeleteTodo(selectedTodo.id)} className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors' aria-label='删除'>
                        <Trash2 className='w-4 h-4 text-[var(--text-muted)]' />
                      </button>
                    </div>
                  )}
                </div>
                {!isFormOpen ? (
                  <div className='flex-1 overflow-y-auto p-4'>
                    <div className='flex items-start gap-3 mb-4'>
                      <button onClick={() => handleToggleTodo(selectedTodo.id)} className='mt-0.5 flex-shrink-0'>
                        {selectedTodo.status === 'completed' ? <CheckCircle2 className='w-5 h-5 text-[var(--brand-primary)]' /> : <Circle className='w-5 h-5 text-[var(--text-muted)]' />}
                      </button>
                      <h2 className={`text-[16px] font-semibold leading-snug ${selectedTodo.status === 'completed' ? 'text-[var(--text-muted)] line-through' : 'text-[var(--text-primary)]'}`}>{selectedTodo.title}</h2>
                    </div>
                    <div className='flex items-center gap-2 flex-wrap mb-4'>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${selectedTodo.status === 'completed' ? 'bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)]' : `${priorityMeta[selectedTodo.priority].text} bg-[var(--bg-hover)]`}`}>
                        {selectedTodo.status === 'completed' ? '已完成' : `${priorityMeta[selectedTodo.priority].label}优先级`}
                      </span>
                      <span className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-full text-[10px] font-medium'>{selectedTodo.category}</span>
                      {selectedTodo.dueDate && !isOverdue(selectedTodo.dueDate, selectedTodo.status) && (
                        <span className='text-[11px] text-[var(--text-muted)] flex items-center gap-1'><Calendar className='w-3 h-3' />{formatDate(selectedTodo.dueDate)}</span>
                      )}
                      {isOverdue(selectedTodo.dueDate, selectedTodo.status) && (
                        <span className='text-[11px] text-[var(--brand-danger)] flex items-center gap-1 font-medium'><Clock className='w-3 h-3' />已过期</span>
                      )}
                    </div>
                    {selectedTodo.description && (
                      <div className='text-[13px] text-[var(--text-secondary)] leading-[1.75] whitespace-pre-wrap break-words'>{selectedTodo.description}</div>
                    )}
                  </div>
                ) : (
                  <div className='flex-1 flex flex-col overflow-hidden'>
                    <div className='flex-1 overflow-y-auto p-4 space-y-4'>
                      <div>
                        <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>标题</label>
                        <input type='text' value={formState.title} onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))} className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors' />
                      </div>
                      <div>
                        <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>描述</label>
                        <textarea value={formState.description} onChange={(e) => setFormState((p) => ({ ...p, description: e.target.value }))} rows={4} className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none leading-relaxed' />
                      </div>
                      <div className='grid grid-cols-2 gap-3'>
                        <div>
                          <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>分类</label>
                          <select value={formState.category} onChange={(e) => setFormState((p) => ({ ...p, category: e.target.value }))} className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'>
                            {categories.map((c) => <option key={c} value={c}>{c}</option>)}
                          </select>
                        </div>
                        <div>
                          <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>优先级</label>
                          <div className='flex gap-1.5'>
                            {(['high', 'medium', 'low'] as const).map((pr) => (
                              <button key={pr} onClick={() => setFormState((p) => ({ ...p, priority: pr }))} className={`flex-1 py-2 rounded-xl text-[12px] font-medium transition-all border ${formState.priority === pr ? `${priorityMeta[pr].text} border-current/20 bg-[var(--bg-hover)]` : 'text-[var(--text-muted)] border-[var(--border-primary)]'}`}>{priorityMeta[pr].label}</button>
                            ))}
                          </div>
                        </div>
                      </div>
                      <div>
                        <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>截止日期</label>
                        <input type='date' value={formState.dueDate} onChange={(e) => setFormState((p) => ({ ...p, dueDate: e.target.value }))} className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors' />
                      </div>
                    </div>
                    <div className='flex-shrink-0 flex items-center justify-end gap-2 px-4 py-3 border-t border-[var(--border-divider)]'>
                      <button onClick={handleCancelForm} className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'>取消</button>
                      <button onClick={handleUpdateTodo} className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'>保存</button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className='flex-1 overflow-y-auto'>
                {mode === 'note' ? (
                  filteredNotes.length === 0 ? (
                    <div className='flex flex-col items-center justify-center py-16 px-4'>
                      <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
                        <FileText className='w-6 h-6 text-[var(--text-muted)]/50' />
                      </div>
                      <p className='text-[14px] text-[var(--text-muted)]'>暂无笔记</p>
                      <button
                        onClick={handleOpenCreateForm}
                        className='mt-3 px-4 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'
                      >
                        新建笔记
                      </button>
                    </div>
                  ) : (
                    <div className='px-3 pt-3 pb-4 space-y-2'>
                      {filteredNotes.filter((n) => n.pinned).length > 0 && (
                        <div>
                          <div className='flex items-center gap-1.5 px-1 pt-1 pb-2'>
                            <div className='w-[3px] h-3.5 rounded-full bg-[var(--accent-amber)]' />
                            <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
                              置顶
                            </span>
                          </div>
                          <div className='space-y-2'>
                            {filteredNotes
                              .filter((n) => n.pinned)
                              .map((note) => (
                                <NoteListItem
                                  key={note.id}
                                  note={note}
                                  onSelect={() => setSelectedNote(note)}
                                  onEdit={() => handleEditNote(note)}
                                  onDelete={() => handleDeleteNote(note.id)}
                                  formatDateFull={formatDateFull}
                                  getContentPreview={getContentPreview}
                                />
                              ))}
                          </div>
                        </div>
                      )}
                      {filteredNotes.filter((n) => !n.pinned).length > 0 && (
                        <div>
                          {filteredNotes.filter((n) => n.pinned).length > 0 && (
                            <div className='flex items-center gap-1.5 px-1 pt-2 pb-2'>
                              <div className='w-[3px] h-3.5 rounded-full bg-[var(--text-muted)]/30' />
                              <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
                                全部笔记
                              </span>
                            </div>
                          )}
                          <div className='space-y-2'>
                            {filteredNotes
                              .filter((n) => !n.pinned)
                              .map((note) => (
                                <NoteListItem
                                  key={note.id}
                                  note={note}
                                  onSelect={() => setSelectedNote(note)}
                                  onEdit={() => handleEditNote(note)}
                                  onDelete={() => handleDeleteNote(note.id)}
                                  formatDateFull={formatDateFull}
                                  getContentPreview={getContentPreview}
                                />
                              ))}
                          </div>
                        </div>
                      )}
                    </div>
                  )
                ) : filteredTodos.length === 0 ? (
                  <div className='flex flex-col items-center justify-center py-16 px-4'>
                    <div className='w-14 h-14 rounded-full bg-[var(--bg-hover)]/60 flex items-center justify-center mb-4'>
                      <ListTodo className='w-6 h-6 text-[var(--text-muted)]/50' />
                    </div>
                    <p className='text-[14px] text-[var(--text-muted)]'>暂无待办</p>
                    <button
                      onClick={handleOpenCreateForm}
                      className='mt-3 px-4 py-1.5 rounded-lg text-[12px] font-medium text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/[0.06] transition-colors'
                    >
                      新建待办
                    </button>
                  </div>
                ) : (
                  <div className='px-3 pt-3 pb-4 space-y-2'>
                    <div className='space-y-2'>
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
                            formatDate={formatDate}
                            isOverdue={isOverdue}
                          />
                        ))}
                    </div>
                    {filteredTodos.filter((t) => t.status === 'completed').length > 0 && (
                      <div>
                        <div className='flex items-center gap-1.5 px-1 pt-2 pb-2'>
                          <div className='w-[3px] h-3.5 rounded-full bg-[var(--text-muted)]/30' />
                          <span className='text-[11px] font-semibold text-[var(--text-muted)] tracking-widest uppercase'>
                            已完成
                          </span>
                        </div>
                        <div className='space-y-2'>
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
                                formatDate={formatDate}
                                isOverdue={isOverdue}
                              />
                            ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {isFormOpen && (
          <div className='absolute inset-0 z-10 flex'>
            <div
              className='absolute inset-0 bg-[var(--bg-overlay)]/40 backdrop-blur-[2px]'
              onClick={handleCancelForm}
            />
            <div className='relative ml-auto w-full h-full bg-[var(--bg-sidebar)] overflow-y-auto animate-slide-in-right flex flex-col'>
              <div className='flex-shrink-0 flex items-center justify-between px-5 py-3 border-b border-[var(--border-divider)]'>
                <h3 className='text-[15px] font-semibold text-[var(--text-primary)]'>
                  {isEditing ? '编辑' : '新建'}{mode === 'note' ? '笔记' : '待办'}
                </h3>
                <button
                  onClick={handleCancelForm}
                  className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
                  aria-label='关闭'
                >
                  <X className='w-4 h-4 text-[var(--text-muted)]' />
                </button>
              </div>

              <div className='flex-1 p-5 space-y-5'>
                <div>
                  <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
                    标题
                  </label>
                  <input
                    type='text'
                    value={formState.title}
                    onChange={(e) => setFormState((prev) => ({ ...prev, title: e.target.value }))}
                    placeholder={mode === 'note' ? '输入笔记标题...' : '输入待办标题...'}
                    className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                    autoFocus
                  />
                </div>

                {mode === 'note' && (
                  <div>
                    <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
                      内容
                    </label>
                    <textarea
                      value={formState.content}
                      onChange={(e) =>
                        setFormState((prev) => ({ ...prev, content: e.target.value }))
                      }
                      placeholder='开始记录...'
                      rows={8}
                      className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none leading-relaxed'
                    />
                  </div>
                )}

                {mode === 'todo' && (
                  <div>
                    <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
                      描述
                    </label>
                    <textarea
                      value={formState.description}
                      onChange={(e) =>
                        setFormState((prev) => ({ ...prev, description: e.target.value }))
                      }
                      placeholder='添加描述（可选）'
                      rows={4}
                      className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[14px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors resize-none leading-relaxed'
                    />
                  </div>
                )}

                <div className='border-t border-[var(--border-divider)] pt-4'>
                  <div className='text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-3'>
                    属性设置
                  </div>
                  <div className='space-y-3'>
                    <div>
                      <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>分类</label>
                      <select
                        value={formState.category}
                        onChange={(e) =>
                          setFormState((prev) => ({ ...prev, category: e.target.value }))
                        }
                        className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                      >
                        {categories.map((cat) => (
                          <option key={cat} value={cat}>
                            {cat}
                          </option>
                        ))}
                      </select>
                    </div>

                    {mode === 'note' && (
                      <button
                        onClick={() => setFormState((prev) => ({ ...prev, pinned: !prev.pinned }))}
                        className={`flex items-center gap-2 px-3 py-2 rounded-xl text-[13px] font-medium transition-all w-full ${
                          formState.pinned
                            ? 'bg-[var(--accent-amber)]/[0.1] text-[var(--accent-amber)] border border-[var(--accent-amber)]/20'
                            : 'bg-[var(--bg-input)] text-[var(--text-secondary)] border border-[var(--border-primary)] hover:bg-[var(--bg-hover)]'
                        }`}
                      >
                        <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />
                        置顶
                      </button>
                    )}

                    {mode === 'todo' && (
                      <div>
                        <label className='block text-[11px] text-[var(--text-muted)] mb-1.5'>优先级</label>
                        <div className='flex gap-1.5'>
                          {(['high', 'medium', 'low'] as const).map((p) => (
                            <button
                              key={p}
                              onClick={() => setFormState((prev) => ({ ...prev, priority: p }))}
                              className={`flex-1 py-2 rounded-xl text-[12px] font-medium transition-all border ${
                                formState.priority === p
                                  ? `${priorityMeta[p].text} border-current/20 bg-[var(--bg-hover)]`
                                  : 'text-[var(--text-muted)] border-[var(--border-primary)] hover:text-[var(--text-secondary)]'
                              }`}
                            >
                              {priorityMeta[p].label}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                {mode === 'note' && (
                  <div className='border-t border-[var(--border-divider)] pt-4'>
                    <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-2'>
                      标签
                    </label>
                    {formState.tags.length > 0 && (
                      <div className='flex flex-wrap gap-1.5 mb-2'>
                        {formState.tags.map((tag) => (
                          <span
                            key={tag}
                            className='flex items-center gap-1 px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-lg text-[11px] font-medium'
                          >
                            {tag}
                            <button
                              onClick={() => handleRemoveTag(tag)}
                              className='hover:opacity-70'
                            >
                              <X className='w-3 h-3' />
                            </button>
                          </span>
                        ))}
                      </div>
                    )}
                    <input
                      type='text'
                      value={formState.newTag}
                      onChange={(e) =>
                        setFormState((prev) => ({ ...prev, newTag: e.target.value }))
                      }
                      onKeyDown={handleAddTag}
                      placeholder='输入标签，按 Enter 添加'
                      className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] placeholder:text-[var(--text-placeholder)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                    />
                  </div>
                )}

                {mode === 'todo' && (
                  <div className='border-t border-[var(--border-divider)] pt-4'>
                    <label className='block text-[11px] font-semibold text-[var(--text-muted)] uppercase tracking-wider mb-1.5'>
                      截止日期
                    </label>
                    <input
                      type='date'
                      value={formState.dueDate}
                      onChange={(e) =>
                        setFormState((prev) => ({ ...prev, dueDate: e.target.value }))
                      }
                      className='w-full px-3 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-xl text-[13px] text-[var(--text-primary)] focus:outline-none focus:border-[var(--brand-primary)]/50 transition-colors'
                    />
                  </div>
                )}
              </div>

              <div className='flex-shrink-0 flex items-center justify-end gap-2 px-5 py-3 border-t border-[var(--border-divider)]'>
                <button
                  onClick={handleCancelForm}
                  className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-[13px] font-medium hover:bg-[var(--bg-input)] transition-colors'
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
                  className='px-5 py-2 rounded-xl text-[13px] font-medium text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all'
                >
                  保存
                </button>
              </div>
            </div>
          </div>
        )}
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
    </>
  )
}

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
        <div className='flex items-center gap-2 mt-2.5 flex-wrap'>
          <span className='px-2 py-0.5 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-md text-[10px] font-medium'>
            {note.category}
          </span>
          {note.tags.slice(0, 2).map((tag) => (
            <span key={tag} className='text-[10px] text-[var(--text-muted)]'>
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

interface TodoListItemProps {
  todo: Todo
  onSelect: () => void
  onToggle: () => void
  onEdit: () => void
  formatDateFull: (dateString: string) => string
  formatDate: (dateString: string) => string
  isOverdue: (dueDate: string | null, status: string) => boolean
}

function TodoListItem({
  todo,
  onSelect,
  onToggle,
  onEdit,
  formatDateFull,
  formatDate,
  isOverdue,
}: TodoListItemProps) {
  return (
    <div
      className='group relative rounded-xl border border-[var(--border-divider)] bg-[var(--bg-sidebar)] hover:border-[var(--border-primary)] hover:shadow-sm transition-all cursor-pointer'
      onClick={onSelect}
    >
      <div className='p-3.5 flex items-start gap-3'>
        <button
          onClick={(e) => {
            e.stopPropagation()
            onToggle()
          }}
          className='flex-shrink-0 mt-0.5 transition-colors'
          aria-label={todo.status === 'completed' ? '标记为未完成' : '标记为完成'}
        >
          {todo.status === 'completed' ? (
            <CheckCircle2 className='w-[18px] h-[18px] text-[var(--brand-primary)]' />
          ) : (
            <Circle className='w-[18px] h-[18px] text-[var(--text-muted)]/50' />
          )}
        </button>
        <div className='flex-1 min-w-0'>
          <div className='flex items-center justify-between gap-2'>
            <h3
              className={`text-[13px] font-semibold truncate ${
                todo.status === 'completed'
                  ? 'text-[var(--text-muted)] line-through'
                  : 'text-[var(--text-primary)]'
              }`}
            >
              {todo.title}
            </h3>
            <span className='text-[10px] text-[var(--text-muted)]/60 flex-shrink-0'>
              {formatDateFull(todo.updatedAt)}
            </span>
          </div>
          {todo.description && (
            <p className='text-[12px] text-[var(--text-muted)] line-clamp-2 mt-0.5 leading-relaxed'>
              {todo.description}
            </p>
          )}
          <div className='flex items-center gap-2 mt-2 flex-wrap'>
            <span className='px-2 py-0.5 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-md text-[10px] font-medium'>
              {todo.category}
            </span>
            <span className='flex items-center gap-1 text-[10px] font-medium'>
              <span className={`inline-block w-1.5 h-1.5 rounded-full ${priorityMeta[todo.priority].dot}`} />
              <span className={priorityMeta[todo.priority].text}>
                {priorityMeta[todo.priority].label}
              </span>
            </span>
            {todo.dueDate && !isOverdue(todo.dueDate, todo.status) && (
              <span className='text-[10px] text-[var(--text-muted)] flex items-center gap-0.5'>
                <Calendar className='w-3 h-3' />
                {formatDate(todo.dueDate)}
              </span>
            )}
            {isOverdue(todo.dueDate, todo.status) && (
              <span className='text-[10px] text-[var(--brand-danger)] flex items-center gap-0.5 font-medium'>
                <Clock className='w-3 h-3' />
                已过期
              </span>
            )}
          </div>
        </div>
      </div>
      <div className='absolute top-2.5 right-2.5 opacity-0 group-hover:opacity-100 transition-opacity'>
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
      </div>
    </div>
  )
}
