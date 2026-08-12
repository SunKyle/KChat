import { useState, useCallback, useRef } from 'react'
import { useToast } from '../../hooks/useToast'
import type {
  Note,
  Todo,
  Reminder,
  CreateNoteRequest,
  UpdateNoteRequest,
  CreateTodoRequest,
  UpdateTodoRequest,
  CreateReminderRequest,
} from '../../types/note-todo'
import { noteApi, todoApi, reminderApi } from '../../api/note-todo'

interface RawNote {
  id: string
  userId: string
  title: string
  content?: string
  category?: string
  tags?: string[]
  pinned?: boolean
  createdAt: string | number[] | null
  updatedAt: string | number[] | null
}

interface RawTodo {
  id: string
  userId: string
  title: string
  description?: string
  status?: 'pending' | 'completed'
  priority?: 'high' | 'medium' | 'low'
  dueDate?: string | number[] | null
  category?: string
  createdAt: string | number[] | null
  updatedAt: string | number[] | null
  completedAt?: string | number[] | null
}

function convertDate(date: string | number[] | null): string | null {
  if (!date) return null
  if (Array.isArray(date)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = date
    return new Date(year, month - 1, day, hour, minute, second).toISOString()
  }
  return new Date(date).toISOString()
}

export function convertNote(note: RawNote): Note {
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

export function convertTodo(todo: RawTodo): Todo {
  return {
    id: todo.id,
    userId: todo.userId,
    title: todo.title,
    description: todo.description || '',
    status: todo.status || 'pending',
    priority: todo.priority || 'medium',
    dueDate: convertDate(todo.dueDate ?? null),
    category: todo.category || '默认',
    createdAt: convertDate(todo.createdAt) || new Date().toISOString(),
    updatedAt: convertDate(todo.updatedAt) || new Date().toISOString(),
    completedAt: convertDate(todo.completedAt ?? null),
  }
}

interface RawReminder {
  id: string
  userId: string
  title: string
  description?: string
  remindAt: string | number[] | null
  status?: 'pending' | 'fired' | 'cancelled'
  createdAt: string | number[] | null
  firedAt?: string | number[] | null
}

export function convertReminder(reminder: RawReminder): Reminder {
  return {
    id: reminder.id,
    userId: reminder.userId,
    title: reminder.title,
    description: reminder.description || '',
    remindAt: convertDate(reminder.remindAt ?? null) || new Date().toISOString(),
    status: reminder.status || 'pending',
    createdAt: convertDate(reminder.createdAt) || new Date().toISOString(),
    firedAt: convertDate(reminder.firedAt ?? null),
  }
}

export function useNoteTodoData() {
  const { success, info, error } = useToast()
  const errorRef = useRef(error)
  const infoRef = useRef(info)
  const successRef = useRef(success)

  const [notes, setNotes] = useState<Note[]>([])
  const [todos, setTodos] = useState<Todo[]>([])
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [isLoading, setIsLoading] = useState(false)

  const loadData = useCallback(async () => {
    setIsLoading(true)
    try {
      const [notesData, todosData, remindersData] = await Promise.all([
        noteApi.getAll(),
        todoApi.getAll(),
        reminderApi.getAll(),
      ])
      setNotes(notesData.map(convertNote))
      setTodos(todosData.map(convertTodo))
      setReminders(remindersData.map(convertReminder))
    } catch (err) {
      console.error('Failed to load data:', err)
      errorRef.current('数据加载失败，请稍后重试')
    } finally {
      setIsLoading(false)
    }
  }, [])

  const createNote = useCallback(
    async (formState: {
      title: string
      content: string
      category: string
      tags: string[]
      pinned: boolean
    }) => {
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
        successRef.current('笔记创建成功')
        return true
      } catch (err) {
        console.error('Failed to create note:', err)
        errorRef.current('创建笔记失败')
        return false
      }
    },
    []
  )

  const updateNote = useCallback(
    async (
      noteId: string,
      formState: {
        title: string
        content: string
        category: string
        tags: string[]
        pinned: boolean
      }
    ) => {
      try {
        const request: UpdateNoteRequest = {
          title: formState.title || '无标题',
          content: formState.content,
          category: formState.category,
          tags: formState.tags,
          pinned: formState.pinned,
        }
        const updatedNote = await noteApi.update(noteId, request)
        setNotes((prev) => prev.map((n) => (n.id === noteId ? convertNote(updatedNote) : n)))
        successRef.current('笔记更新成功')
        return true
      } catch (err) {
        console.error('Failed to update note:', err)
        errorRef.current('更新笔记失败')
        return false
      }
    },
    []
  )

  const deleteNote = useCallback(async (id: string) => {
    try {
      await noteApi.delete(id)
      setNotes((prev) => prev.filter((n) => n.id !== id))
      successRef.current('笔记已删除')
      return true
    } catch (err) {
      console.error('Failed to delete note:', err)
      errorRef.current('删除笔记失败')
      return false
    }
  }, [])

  const pinNote = useCallback(async (note: Note) => {
    try {
      const request: UpdateNoteRequest = { pinned: !note.pinned }
      const updatedNote = await noteApi.update(note.id, request)
      setNotes((prev) => prev.map((n) => (n.id === note.id ? convertNote(updatedNote) : n)))
    } catch (err) {
      console.error('Failed to pin note:', err)
      errorRef.current('置顶操作失败')
    }
  }, [])

  const createTodo = useCallback(
    async (formState: {
      title: string
      description: string
      priority: 'high' | 'medium' | 'low'
      dueDate: string
      category: string
    }) => {
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
        successRef.current('待办创建成功')
        return true
      } catch (err) {
        console.error('Failed to create todo:', err)
        errorRef.current('创建待办失败')
        return false
      }
    },
    []
  )

  const updateTodo = useCallback(
    async (
      todoId: string,
      formState: {
        title: string
        description: string
        priority: 'high' | 'medium' | 'low'
        dueDate: string
        category: string
      }
    ) => {
      try {
        const request: UpdateTodoRequest = {
          title: formState.title || '未命名待办',
          description: formState.description,
          priority: formState.priority,
          dueDate: formState.dueDate ? new Date(formState.dueDate).toISOString() : null,
          category: formState.category,
        }
        const updatedTodo = await todoApi.update(todoId, request)
        setTodos((prev) => prev.map((t) => (t.id === todoId ? convertTodo(updatedTodo) : t)))
        successRef.current('待办更新成功')
        return true
      } catch (err) {
        console.error('Failed to update todo:', err)
        errorRef.current('更新待办失败')
        return false
      }
    },
    []
  )

  const deleteTodo = useCallback(async (id: string) => {
    try {
      await todoApi.delete(id)
      setTodos((prev) => prev.filter((t) => t.id !== id))
      successRef.current('待办已删除')
      return true
    } catch (err) {
      console.error('Failed to delete todo:', err)
      errorRef.current('删除待办失败')
      return false
    }
  }, [])

  const toggleTodo = useCallback(async (id: string) => {
    try {
      const updatedTodo = await todoApi.toggle(id)
      const convertedTodo = convertTodo(updatedTodo)
      const message = convertedTodo.status === 'completed' ? '任务已完成！' : '任务已恢复'
      setTodos((prev) => prev.map((t) => (t.id === id ? convertedTodo : t)))
      setTimeout(() => infoRef.current(message), 50)
    } catch (err) {
      console.error('Failed to toggle todo:', err)
      errorRef.current('切换状态失败')
    }
  }, [])

  const createReminder = useCallback(
    async (formState: { title: string; description: string; remindAt: string }) => {
      try {
        const request: CreateReminderRequest = {
          title: formState.title || '未命名提醒',
          description: formState.description,
          remindAt: toLocalIso(formState.remindAt) || toLocalNow(),
        }
        const newReminder = await reminderApi.create(request)
        setReminders((prev) => [...prev, convertReminder(newReminder)].sort(sortByRemindAt))
        successRef.current('提醒创建成功')
        return true
      } catch (err) {
        console.error('Failed to create reminder:', err)
        errorRef.current('创建提醒失败')
        return false
      }
    },
    []
  )

  const updateReminder = useCallback(
    async (id: string, formState: { title: string; description: string; remindAt: string }) => {
      try {
        const request: CreateReminderRequest = {
          title: formState.title || '未命名提醒',
          description: formState.description,
          remindAt: toLocalIso(formState.remindAt) || toLocalNow(),
        }
        const updatedReminder = await reminderApi.update(id, request)
        setReminders((prev) =>
          prev.map((r) => (r.id === id ? convertReminder(updatedReminder) : r)).sort(sortByRemindAt)
        )
        successRef.current('提醒更新成功')
        return true
      } catch (err) {
        console.error('Failed to update reminder:', err)
        errorRef.current('更新提醒失败')
        return false
      }
    },
    []
  )

  const cancelReminder = useCallback(async (id: string) => {
    try {
      await reminderApi.cancel(id)
      setReminders((prev) => prev.map((r) => (r.id === id ? { ...r, status: 'cancelled' } : r)))
      successRef.current('提醒已取消')
      return true
    } catch (err) {
      console.error('Failed to cancel reminder:', err)
      errorRef.current('取消提醒失败')
      return false
    }
  }, [])

  return {
    notes,
    setNotes,
    todos,
    setTodos,
    reminders,
    setReminders,
    isLoading,
    loadData,
    createNote,
    updateNote,
    deleteNote,
    pinNote,
    createTodo,
    updateTodo,
    deleteTodo,
    toggleTodo,
    createReminder,
    updateReminder,
    cancelReminder,
  }
}

function sortByRemindAt(a: Reminder, b: Reminder) {
  return new Date(a.remindAt).getTime() - new Date(b.remindAt).getTime()
}

/**
 * 将 datetime-local 的本地时间（形如 yyyy-MM-ddTHH:mm）转为后端 LocalDateTime
 * 兼容格式（yyyy-MM-ddTHH:mm:ss）。保持本地墙钟，不做 UTC 偏移。
 */
function toLocalIso(datetimeLocal: string): string {
  if (!datetimeLocal) return ''
  return datetimeLocal.length === 16 ? `${datetimeLocal}:00` : datetimeLocal
}

/**
 * 取当前本地时间，格式 yyyy-MM-ddTHH:mm:ss。
 */
function toLocalNow(): string {
  const d = new Date()
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
