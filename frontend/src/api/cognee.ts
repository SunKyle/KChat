/**
 * Cognee AI Memory Platform — TypeScript API Client
 *
 * Cognee provides persistent long-term memory for AI agents via
 * knowledge graphs + vector search.
 *
 * This client communicates directly with the cognee Python REST API
 * (started via `cognee.serve()` on port 8000 by default).
 *
 * Usage:
 *   import { cogneeMemory } from '../api/cognee'
 *   await cogneeMemory.search('What did we discuss about databases?')
 *   await cogneeMemory.add('User: ...\n\nAssistant: ...')
 */

const COGNEE_BASE_URL = import.meta.env.VITE_COGNEE_URL || 'http://localhost:8000'

export interface CogneeSearchResult {
  id?: string
  text?: string
  text_content?: string
  content?: string
  score: number
  metadata?: Record<string, unknown>
}

export interface CogneeSearchResponse {
  results?: CogneeSearchResult[]
  data?: CogneeSearchResult[]
  status?: string
}

export interface CogneeAddResponse {
  id?: string
  success: boolean
  message?: string
}

/**
 * Cognee memory API client.
 * All methods gracefully handle connection errors — failures return empty results.
 */
export const cogneeMemory = {
  /**
   * Search cognee's knowledge graph for relevant memories.
   * @param query  Natural language query
   * @param topK   Maximum results to return (default: 5)
   */
  search: async (query: string, topK = 5): Promise<CogneeSearchResult[]> => {
    try {
      const response = await fetch(`${COGNEE_BASE_URL}/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query, top_k: topK }),
      })

      if (!response.ok) {
        console.warn('[Cognee] Search returned status:', response.status)
        return []
      }

      const data: CogneeSearchResponse = await response.json()
      const results = data.results ?? data.data ?? []

      return results.map((item) => {
        const text = item.text ?? item.text_content ?? item.content ?? ''
        return { ...item, text }
      })
    } catch (error) {
      console.warn('[Cognee] Search failed:', error)
      return []
    }
  },

  /**
   * Add content to cognee for knowledge graph indexing.
   * @param content  Text content to index
   * @param metadata Optional metadata (conversationId, type, etc.)
   */
  add: async (content: string, metadata?: Record<string, unknown>): Promise<boolean> => {
    try {
      const response = await fetch(`${COGNEE_BASE_URL}/add`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, metadata: metadata ?? {} }),
      })

      if (!response.ok) return false

      const data: CogneeAddResponse = await response.json()
      return data.success
    } catch (error) {
      console.warn('[Cognee] Add failed:', error)
      return false
    }
  },

  /**
   * Check if cognee service is reachable.
   */
  isHealthy: async (): Promise<boolean> => {
    try {
      const response = await fetch(`${COGNEE_BASE_URL}/health`, {
        method: 'GET',
      })
      return response.ok
    } catch {
      return false
    }
  },
}

export default cogneeMemory
