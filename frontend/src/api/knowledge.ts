import { request, uploadFile } from './client'

export interface KnowledgeBase {
  id: string
  userId: string
  name: string
  description: string
  datasetName: string
  documentCount: number
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocument {
  id: string
  kbId: string
  fileName: string
  fileType: string
  fileSize: number
  contentLength: number
  status: 'PENDING' | 'PROCESSING' | 'INDEXED' | 'FAILED'
  errorMessage: string | null
  cogneeDataId: string | null
  storedFilePath: string | null
  /** Tika 提取的文本内容 */
  content: string
  downloadUrl: string
  createdAt: string
  updatedAt: string
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
}

export const knowledgeBaseApi = {
  /** 创建知识库 */
  create: async (userId: string, data: CreateKnowledgeBaseRequest): Promise<KnowledgeBase> => {
    return request(`/knowledge-bases?userId=${userId}`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  /** 获取知识库列表 */
  list: async (userId: string): Promise<KnowledgeBase[]> => {
    return request(`/knowledge-bases?userId=${userId}`)
  },

  /** 获取知识库详情 */
  getById: async (userId: string, kbId: string): Promise<KnowledgeBase> => {
    return request(`/knowledge-bases/${kbId}?userId=${userId}`)
  },

  /** 更新知识库 */
  update: async (userId: string, kbId: string, data: CreateKnowledgeBaseRequest): Promise<KnowledgeBase> => {
    return request(`/knowledge-bases/${kbId}?userId=${userId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  /** 删除知识库 */
  delete: async (userId: string, kbId: string): Promise<void> => {
    await request(`/knowledge-bases/${kbId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },

  /** 上传文档到知识库 */
  uploadDocument: async (userId: string, kbId: string, file: File): Promise<KnowledgeDocument> => {
    return uploadFile<KnowledgeDocument>(
      `/knowledge-bases/${kbId}/documents?userId=${userId}`,
      file,
      'file'
    )
  },

  /** 获取文档列表 */
  listDocuments: async (userId: string, kbId: string): Promise<KnowledgeDocument[]> => {
    return request(`/knowledge-bases/${kbId}/documents?userId=${userId}`)
  },

  /** 删除文档 */
  deleteDocument: async (userId: string, kbId: string, docId: string): Promise<void> => {
    await request(`/knowledge-bases/${kbId}/documents/${docId}?userId=${userId}`, {
      method: 'DELETE',
    })
  },

  /** 获取文档处理状态 */
  getDocumentStatus: async (userId: string, kbId: string, docId: string): Promise<KnowledgeDocument> => {
    return request(`/knowledge-bases/${kbId}/documents/${docId}/status?userId=${userId}`)
  },
}
