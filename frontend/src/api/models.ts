import { request } from './client'
import type { ModelConfig } from '../types'

export const models = {
  list: async (category?: string): Promise<string[]> => {
    const params = category ? `?category=${category}` : ''
    return request(`/models${params}`)
  },

  capabilities: async (): Promise<Array<{ model: string; capabilities: string[] }>> => {
    return request('/models/capabilities')
  },
}

export const modelConfigs = {
  list: async (): Promise<ModelConfig[]> => {
    return request('/model-configs')
  },

  get: async (id: string): Promise<ModelConfig> => {
    return request(`/model-configs/${id}`)
  },

  listByType: async (type: string): Promise<ModelConfig[]> => {
    return request(`/model-configs/type/${type}`)
  },

  listByCategory: async (category: string): Promise<ModelConfig[]> => {
    return request(`/model-configs/by-category/${category}`)
  },

  getTypes: async (): Promise<string[]> => {
    return request('/model-configs/types')
  },

  getCategories: async (): Promise<string[]> => {
    return request('/model-configs/categories')
  },

  create: async (data: Omit<ModelConfig, 'id'>): Promise<ModelConfig> => {
    return request('/model-configs', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  update: async (id: string | number, data: Partial<ModelConfig>): Promise<ModelConfig> => {
    return request(`/model-configs/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  delete: async (id: string | number): Promise<void> => {
    await request(`/model-configs/${id}`, {
      method: 'DELETE',
    })
  },
}
