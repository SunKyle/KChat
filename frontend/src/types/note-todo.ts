export interface Note {
  id: string
  userId: string
  title: string
  content: string
  category: string
  tags: string[]
  pinned: boolean
  createdAt: string
  updatedAt: string
}

export interface Todo {
  id: string
  userId: string
  title: string
  description: string
  status: 'pending' | 'completed'
  priority: 'high' | 'medium' | 'low'
  dueDate: string | null
  category: string
  createdAt: string
  updatedAt: string
  completedAt: string | null
}

export type NoteTodoMode = 'note' | 'todo'

export interface CreateNoteRequest {
  title?: string
  content?: string
  category?: string
  tags?: string[]
  pinned?: boolean
}

export interface UpdateNoteRequest {
  title?: string
  content?: string
  category?: string
  tags?: string[]
  pinned?: boolean
}

export interface CreateTodoRequest {
  title?: string
  description?: string
  priority?: 'high' | 'medium' | 'low'
  dueDate?: string | null
  category?: string
}

export interface UpdateTodoRequest {
  title?: string
  description?: string
  status?: 'pending' | 'completed'
  priority?: 'high' | 'medium' | 'low'
  dueDate?: string | null
  category?: string
}
