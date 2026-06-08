import { request } from './client'
import type { Memory } from '../types'

export type MemoryItem = Memory

export const memory = {
  getAll: async (): Promise<MemoryItem[]> => {
    return request('/memory')
  },

  getByType: async (type: string): Promise<MemoryItem[]> => {
    return request(`/memory/type/${type}`)
  },

  getById: async (id: string): Promise<MemoryItem> => {
    return request(`/memory/${id}`)
  },

  create: async (data: Omit<MemoryItem, 'id' | 'createdAt'>): Promise<MemoryItem> => {
    return request('/memory', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  update: async (id: string, data: Partial<MemoryItem>): Promise<MemoryItem> => {
    return request(`/memory/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  createBatch: async (items: Omit<MemoryItem, 'id' | 'createdAt'>[]): Promise<MemoryItem[]> => {
    return request('/memory/batch', {
      method: 'POST',
      body: JSON.stringify(items),
    })
  },

  recall: async (query: string, limit?: number): Promise<MemoryItem[]> => {
    const params = new URLSearchParams({ query })
    if (limit) params.set('limit', limit.toString())
    return request(`/memory/recall?${params}`)
  },

  delete: async (id: string): Promise<void> => {
    await request(`/memory/${id}`, {
      method: 'DELETE',
    })
  },

  deleteByUserId: async (userId: string): Promise<void> => {
    await request(`/memory/user/${userId}`, {
      method: 'DELETE',
    })
  },

  getTypes: async (): Promise<string[]> => {
    return request('/memory/types')
  },
}
