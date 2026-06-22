import { useState, useCallback, useEffect } from 'react'
import type { Note, Todo } from '../../types/note-todo'

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
}

export function useNoteTodoForm() {
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingNote, setEditingNote] = useState<Note | null>(null)
  const [editingTodo, setEditingTodo] = useState<Todo | null>(null)
  const [formState, setFormState] = useState<FormState>(emptyForm)

  const openCreateForm = useCallback(() => {
    setEditingNote(null)
    setEditingTodo(null)
    setIsFormOpen(true)
  }, [])

  const editNote = useCallback((note: Note) => {
    setEditingNote(note)
    setIsFormOpen(true)
  }, [])

  const editTodo = useCallback((todo: Todo) => {
    setEditingTodo(todo)
    setIsFormOpen(true)
  }, [])

  const cancelForm = useCallback(() => {
    setIsFormOpen(false)
    setEditingNote(null)
    setEditingTodo(null)
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
      setFormState(emptyForm)
    }
  }, [editingNote, editingTodo])

  return {
    isFormOpen,
    editingNote,
    editingTodo,
    formState,
    setFormState,
    openCreateForm,
    editNote,
    editTodo,
    cancelForm,
  }
}
