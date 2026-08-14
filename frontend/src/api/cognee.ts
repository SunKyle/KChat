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

export interface GraphNode {
  id: string
  label: string
  type: string
  properties: Record<string, unknown>
  position?: { x: number; y: number }
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  label: string
  type: string
}

export interface GraphResponse {
  nodes: GraphNode[]
  edges: GraphEdge[]
  status: string
  total_nodes: number
  total_edges: number
}

export interface DatasetInfo {
  id: string
  name: string
  data_count: number
  created_at: string
}

export interface DatasetsResponse {
  datasets: DatasetInfo[]
  status: string
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

  /**
   * Get the knowledge graph structure (nodes + edges) for visualization.
   */
  getGraph: async (): Promise<GraphResponse> => {
    try {
      const response = await fetch(`${COGNEE_BASE_URL}/graph`, {
        method: 'GET',
      })
      if (!response.ok) {
        console.warn('[Cognee] Get graph returned status:', response.status)
        return { nodes: [], edges: [], status: `error: HTTP ${response.status}`, total_nodes: 0, total_edges: 0 }
      }
      return await response.json()
    } catch (error) {
      console.warn('[Cognee] Get graph failed:', error)
      return { nodes: [], edges: [], status: `error: ${error}`, total_nodes: 0, total_edges: 0 }
    }
  },

  /**
   * List all datasets with their metadata.
   */
  listDatasets: async (): Promise<DatasetInfo[]> => {
    try {
      const response = await fetch(`${COGNEE_BASE_URL}/datasets`, {
        method: 'GET',
      })
      if (!response.ok) {
        console.warn('[Cognee] List datasets returned status:', response.status)
        return []
      }
      const data: DatasetsResponse = await response.json()
      return data.datasets ?? []
    } catch (error) {
      console.warn('[Cognee] List datasets failed:', error)
      return []
    }
  },

  /**
   * 手动触发 Cognee 图谱自我优化（improve）。
   * 推导跨实体连接、重加权边、剪枝陈旧节点，使 recall 更准确。
   * 通过 Spring 后端代理：/api/cognee/improve → cognee Python /improve
   */
  improve: async (dataset = 'main_dataset'): Promise<{ success: boolean; message: string }> => {
    try {
      const response = await fetch(`/api/cognee/improve?dataset=${encodeURIComponent(dataset)}`, {
        method: 'POST',
      })
      const data = (await response.json()) as { success?: boolean; message?: string }
      return {
        success: data.success ?? response.ok,
        message: data.message ?? (response.ok ? '优化完成' : '优化失败'),
      }
    } catch (error) {
      console.warn('[Cognee] Improve failed:', error)
      return { success: false, message: `优化失败：${String(error)}` }
    }
  },
}

export default cogneeMemory
