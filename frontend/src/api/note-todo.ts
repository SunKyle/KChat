import { request } from './client'
import type {
  Note,
  Todo,
  CreateNoteRequest,
  UpdateNoteRequest,
  CreateTodoRequest,
  UpdateTodoRequest,
} from '../types/note-todo'

const DEFAULT_USER_ID = 'default'

export const noteApi = {
  /**
   * 获取所有笔记
   */
  getAll: async (userId: string = DEFAULT_USER_ID): Promise<Note[]> => {
    return request(`/notes?userId=${userId}`)
  },

  /**
   * 获取单条笔记
   */
  getById: async (noteId: string, userId: string = DEFAULT_USER_ID): Promise<Note> => {
    return request(`/notes/${noteId}?userId=${userId}`)
  },

  /**
   * 创建笔记
   */
  create: async (data: CreateNoteRequest, userId: string = DEFAULT_USER_ID): Promise<Note> => {
    return request(`/notes?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  /**
   * 更新笔记
   */
  update: async (
    noteId: string,
    data: UpdateNoteRequest,
    userId: string = DEFAULT_USER_ID
  ): Promise<Note> => {
    return request(`/notes/${noteId}?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  /**
   * 删除笔记
   */
  delete: async (noteId: string, userId: string = DEFAULT_USER_ID): Promise<void> => {
    return request(`/notes/${noteId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },

  /**
   * 搜索笔记
   */
  search: async (keyword: string, userId: string = DEFAULT_USER_ID): Promise<Note[]> => {
    return request(`/notes?userId=${userId}&keyword=${encodeURIComponent(keyword)}`)
  },

  /**
   * 按分类查询笔记
   */
  getByCategory: async (category: string, userId: string = DEFAULT_USER_ID): Promise<Note[]> => {
    return request(`/notes?userId=${userId}&category=${encodeURIComponent(category)}`)
  },
}

export const todoApi = {
  /**
   * 获取所有待办
   */
  getAll: async (userId: string = DEFAULT_USER_ID): Promise<Todo[]> => {
    return request(`/todos?userId=${userId}`)
  },

  /**
   * 获取单条待办
   */
  getById: async (todoId: string, userId: string = DEFAULT_USER_ID): Promise<Todo> => {
    return request(`/todos/${todoId}?userId=${userId}`)
  },

  /**
   * 创建待办
   */
  create: async (data: CreateTodoRequest, userId: string = DEFAULT_USER_ID): Promise<Todo> => {
    return request(`/todos?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  /**
   * 更新待办
   */
  update: async (
    todoId: string,
    data: UpdateTodoRequest,
    userId: string = DEFAULT_USER_ID
  ): Promise<Todo> => {
    return request(`/todos/${todoId}?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  /**
   * 切换待办状态
   */
  toggle: async (todoId: string, userId: string = DEFAULT_USER_ID): Promise<Todo> => {
    return request(`/todos/${todoId}/toggle?userId=${userId}`, {
      method: 'PATCH',
    })
  },

  /**
   * 删除待办
   */
  delete: async (todoId: string, userId: string = DEFAULT_USER_ID): Promise<void> => {
    return request(`/todos/${todoId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },

  /**
   * 搜索待办
   */
  search: async (keyword: string, userId: string = DEFAULT_USER_ID): Promise<Todo[]> => {
    return request(`/todos?userId=${userId}&keyword=${encodeURIComponent(keyword)}`)
  },

  /**
   * 按状态查询待办
   */
  getByStatus: async (
    status: 'pending' | 'completed',
    userId: string = DEFAULT_USER_ID
  ): Promise<Todo[]> => {
    return request(`/todos?userId=${userId}&status=${status}`)
  },

  /**
   * 按优先级查询待办
   */
  getByPriority: async (
    priority: 'high' | 'medium' | 'low',
    userId: string = DEFAULT_USER_ID
  ): Promise<Todo[]> => {
    return request(`/todos?userId=${userId}&priority=${priority}`)
  },

  /**
   * 获取过期待办
   */
  getOverdue: async (userId: string = DEFAULT_USER_ID): Promise<Todo[]> => {
    return request(`/todos/overdue?userId=${userId}`)
  },
}

export default { noteApi, todoApi }
