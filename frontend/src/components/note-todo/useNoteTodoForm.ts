import { useState, useCallback, useEffect } from 'react'
import type { Note, Todo, Reminder } from '../../types/note-todo'

export interface FormState {
  title: string
  content: string
  category: string
  tags: string[]
  newTag: string
  pinned: boolean
  description: string
  priority: 'high' | 'medium' | 'low'
  dueDate: string
  remindAt: string
}

const emptyForm: FormState = {
  title: '',
  content: '',
  category: '默认',
  tags: [],
  newTag: '',
  pinned: false,
  description: '',
  priority: 'medium',
  dueDate: '',
  remindAt: '',
}

export function useNoteTodoForm() {
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingNote, setEditingNote] = useState<Note | null>(null)
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null)
  const [editingReminder, setEditingReminder] = useState<Reminder | null>(null)
  const [formState, setFormState] = useState<FormState>(emptyForm)

  const openCreateForm = useCallback(() => {
    setEditingNote(null)
    setEditingTodo(null)
    setEditingReminder(null)
    setIsFormOpen(true)
  }, [])

  const editNote = useCallback((note: Note) => {
    setEditingNote(note)
    setEditingTodo(null)
    setEditingReminder(null)
    setIsFormOpen(true)
  }, [])

  const editTodo = useCallback((todo: Todo) => {
    setEditingTodo(todo)
    setEditingNote(null)
    setEditingReminder(null)
    setIsFormOpen(true)
  }, [])

  const editReminder = useCallback((reminder: Reminder) => {
    setEditingReminder(reminder)
    setEditingNote(null)
    setEditingTodo(null)
    setIsFormOpen(true)
  }, [])

  const cancelForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingNote(null)
    setEditingTodo(null)
    setEditingReminder(null)
  }, [])

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
        remindAt: '',
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
        remindAt: '',
      })
    } else if (editingReminder) {
      setFormState({
        title: editingReminder.title,
        content: '',
        category: '默认',
        tags: [],
        newTag: '',
        pinned: false,
        description: editingReminder.description,
        priority: 'medium',
        dueDate: '',
        remindAt: toLocalDatetimeValue(editingReminder.remindAt),
      })
    } else {
      setFormState(emptyForm)
    }
  }, [editingNote, editingTodo, editingReminder])

  return {
    isFormOpen,
    editingNote,
    editingTodo,
    editingReminder,
    formState,
    setFormState,
    openCreateForm,
    editNote,
    editTodo,
    editReminder,
    cancelForm,
  }
}

function toLocalDatetimeValue(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
