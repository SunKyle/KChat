import { request } from './client'
import type { Memory } from '../types'

export interface MemoryRecallRequest {
  userId: string
  query: string
  topK?: number
  types?: string[]
}

export interface MemoryRecallResponse {
  memories: Memory[]
  count: number
}

export const memory = {
  getAll: async (userId: string): Promise<Memory[]> => {
    return request(`/memories?userId=${userId}`)
  },

  getByType: async (userId: string, type: string): Promise<Memory[]> => {
    return request(`/memories/type/${type}?userId=${userId}`)
  },

  getById: async (id: number): Promise<Memory> => {
    return request(`/memories/${id}`)
  },

  create: async (data: Omit<Memory, 'id' | 'createdAt'>): Promise<Memory> => {
    return request('/memories', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  update: async (id: number, data: Partial<Omit<Memory, 'id' | 'createdAt'>>): Promise<Memory> => {
    return request(`/memories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  createBatch: async (items: Omit<Memory, 'id' | 'createdAt'>[]): Promise<Memory[]> => {
    return request('/memories/batch', {
      method: 'POST',
      body: JSON.stringify(items),
    })
  },

  recall: async (requestData: MemoryRecallRequest): Promise<MemoryRecallResponse> => {
    return request('/memories/recall', {
      method: 'POST',
      body: JSON.stringify(requestData),
    })
  },

  delete: async (id: number): Promise<void> => {
    await request(`/memories/${id}`, {
      method: 'DELETE',
    })
  },

  deleteByUserId: async (userId: string): Promise<void> => {
    await request(`/memories/user/${userId}`, {
      method: 'DELETE',
    })
  },

  getTypes: async (): Promise<string[]> => {
    return request('/memories/types')
  },
}
