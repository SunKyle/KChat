import { request, requestStream, uploadFile } from './client';
import type { Conversation, Message } from '../types';

export const conversations = {
  list: async (): Promise<Conversation[]> => {
    return request('/conversations');
  },

  get: async (id: string): Promise<Conversation> => {
    return request(`/conversations/${id}`);
  },

  create: async (data: { name: string }): Promise<Conversation> => {
    return request('/conversations', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  update: async (id: string, data: { name: string }): Promise<Conversation> => {
    return request(`/conversations/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  delete: async (id: string): Promise<void> => {
    await request(`/conversations/${id}`, {
      method: 'DELETE',
    });
  },
};

export const chat = {
  send: async (conversationId: string, content: string): Promise<Message> => {
    return request(`/chat/${conversationId}`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    });
  },

  stream: async (
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
    );
  },
};

export const images = {
  upload: async (file: File): Promise<{ url: string }> => {
    return uploadFile('/images/upload', file);
  },

  delete: async (url: string): Promise<void> => {
    await request('/images/delete', {
      method: 'POST',
      body: JSON.stringify({ url }),
    });
  },
};