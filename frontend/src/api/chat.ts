import { request, requestStream, uploadFile, requestSSE } from './client'
import type { Conversation, Message, ChatRequest } from '../types'

export const conversations = {
  list: async (): Promise<Conversation[]> => {
    return request('/conversations')
  },

  get: async (id: string): Promise<{ conversation: Conversation; messages: Message[] }> => {
    return request(`/conversations/${id}`)
  },

  create: async (title?: string): Promise<Conversation> => {
    return request('/conversations', {
      method: 'POST',
      body: JSON.stringify(title ? { title } : {}),
    })
  },

  update: async (id: string, updates: Partial<Conversation>): Promise<Conversation> => {
    return request(`/conversations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(updates),
    })
  },

  delete: async (id: string): Promise<void> => {
    await request(`/conversations/${id}`, {
      method: 'DELETE',
    })
  },
}

export const chat = {
  send: async (
    requestData: ChatRequest
  ): Promise<{
    messageId: string
    content: string
    role: 'assistant'
    conversationId: string
    images?: string[]
    artifacts?: Array<{ type: string; url: string; text?: string }>
  }> => {
    return request('/chat', {
      method: 'POST',
      body: JSON.stringify(requestData),
    })
  },

  summarize: async (
    content: string,
    model: string
  ): Promise<{ title: string; summary: string }> => {
    return request('/chat/summarize', {
      method: 'POST',
      body: JSON.stringify({ content, model, userId: 'default' }),
      timeout: 120000,
      retries: 0,
    })
  },

  stream: async (
    requestData: ChatRequest,
    onMessage: (content: string) => void,
    onComplete: (messageId: string, title?: string) => void,
    onError: (error: Error) => void,
    controller?: AbortController,
    onSearchResults?: (results: unknown) => void,
    onImageDone?: (url: string) => void
  ): Promise<void> => {
    return requestSSE(
      '/chat/stream',
      {
        method: 'POST',
        body: JSON.stringify(requestData),
      },
      onMessage,
      onComplete,
      onError,
      controller,
      onSearchResults,
      onImageDone
    )
  },

  regenerate: async (
    conversationId: string,
    messageId: string,
    userId?: string,
    model?: string
  ): Promise<{
    success: boolean
    messageId: string
    conversationId: string
    content: string
    error?: string
    message?: string
  }> => {
    return request('/chat/regenerate', {
      method: 'POST',
      body: JSON.stringify({ conversationId, messageId, userId, model }),
    })
  },

  sendSimple: async (conversationId: string, content: string): Promise<Message> => {
    return request(`/chat/${conversationId}`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
  },

  streamSimple: async (
    conversationId: string,
    content: string,
    onData: (data: Message) => void,
    onError?: (error: Error) => void
  ): Promise<void> => {
    return requestStream(
      `/chat/stream/${conversationId}`,
      {
        method: 'POST',
        body: JSON.stringify({ content }),
      },
      onData,
      onError
    )
  },
}

export interface OptimizationDetail {
  type: string
  description: string
}

export interface OptimizationResponse {
  success: boolean
  optimizedContent: string
  originalContent: string
  optimizations: OptimizationDetail[]
  processingTimeMs: number
  error?: string
  message?: string
  retryAfterSeconds?: number
}

export interface OptimizationRequest {
  content: string
  userId?: string
  optimizationType?: string
  modelId?: string
  modelType?: string
  baseUrl?: string
  apiKey?: string
}

export const optimization = {
  optimize: async (requestData: OptimizationRequest): Promise<OptimizationResponse> => {
    return request('/chat/optimize', {
      method: 'POST',
      body: JSON.stringify({
        content: requestData.content,
        userId: requestData.userId || 'default',
        optimizationType: requestData.optimizationType,
        modelId: requestData.modelId,
        modelType: requestData.modelType,
        baseUrl: requestData.baseUrl,
        apiKey: requestData.apiKey,
      }),
      timeout: 60000,
    })
  },
}

export const images = {
  upload: async (file: File): Promise<{ url: string }> => {
    return uploadFile('/images/upload', file)
  },

  delete: async (filename: string): Promise<void> => {
    await request(`/images/${filename}`, {
      method: 'DELETE',
    })
  },

  deleteByUrl: async (url: string): Promise<void> => {
    await request('/images/delete', {
      method: 'POST',
      body: JSON.stringify({ url }),
    })
  },
}
