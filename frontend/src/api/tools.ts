import { request } from './client'
import type { ToolInfo } from '../types'

export const tools = {
  list: async (): Promise<ToolInfo[]> => {
    return request('/tools')
  },
}
