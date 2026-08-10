import type { Conversation } from '../types'

const CONVERSATIONS_KEY = 'kchat_conversations'
const THEME_KEY = 'kchat-theme'
const NOTES_KEY = 'kchat_notes'
const TODOS_KEY = 'kchat_todos'

export const conversationStorage = {
  get(): Conversation[] {
    try {
      const cached = localStorage.getItem(CONVERSATIONS_KEY)
      return cached ? JSON.parse(cached) : []
    } catch {
      return []
    }
  },

  set(conversations: Conversation[]): void {
    try {
      localStorage.setItem(CONVERSATIONS_KEY, JSON.stringify(conversations))
    } catch (e) {
      console.warn('Error setting conversations:', e)
    }
  },

  update(id: string, updates: Partial<Conversation>): void {
    const conversations = this.get()
    const updated = conversations.map((c) => (c.id === id ? { ...c, ...updates } : c))
    this.set(updated)
  },

  remove(id: string): void {
    const conversations = this.get()
    const filtered = conversations.filter((c) => c.id !== id)
    this.set(filtered)
  },

  add(conversation: Conversation): void {
    const conversations = this.get()
    const updated = [conversation, ...conversations].sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
    this.set(updated)
  },
}

export const themeStorage = {
  get(): string | null {
    try {
      return localStorage.getItem(THEME_KEY)
    } catch {
      return null
    }
  },

  set(theme: string): void {
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch (e) {
      console.warn('Error setting theme:', e)
    }
  },
}

export const notesStorage = {
  get<T>(): T[] {
    try {
      const cached = localStorage.getItem(NOTES_KEY)
      return cached ? JSON.parse(cached) : []
    } catch {
      return []
    }
  },

  set<T>(notes: T[]): void {
    try {
      localStorage.setItem(NOTES_KEY, JSON.stringify(notes))
    } catch (e) {
      console.warn('Error setting notes:', e)
    }
  },
}

export const todosStorage = {
  get<T>(): T[] {
    try {
      const cached = localStorage.getItem(TODOS_KEY)
      return cached ? JSON.parse(cached) : []
    } catch {
      return []
    }
  },

  set<T>(todos: T[]): void {
    try {
      localStorage.setItem(TODOS_KEY, JSON.stringify(todos))
    } catch (e) {
      console.warn('Error setting todos:', e)
    }
  },
}
