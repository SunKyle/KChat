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
  ): Promise<{ messageId: string; content: string; role: 'assistant'; conversationId: string }> => {
    return request('/chat', {
      method: 'POST',
      body: JSON.stringify(requestData),
    })
  },

  summarize: async (content: string, model: string): Promise<{ title: string; summary: string }> => {
    return request('/chat/summarize', {
      method: 'POST',
      body: JSON.stringify({ content, model }),
      timeout: 120000,
      retries: 0,
    })
  },

  stream: async (
    requestData: ChatRequest,
    onMessage: (content: string) => void,
    onComplete: (messageId: string) => void,
    onError: (error: Error) => void,
    controller?: AbortController
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
      controller
    )
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
