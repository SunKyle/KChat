import { request } from './client'
import type { ToolInfo } from '../types'

export const tools = {
  list: async (userId?: string): Promise<ToolInfo[]> => {
    const params = userId ? `?userId=${encodeURIComponent(userId)}` : ''
    return request(`/tools${params}`)
  },
}