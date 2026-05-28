import type { Conversation, Message, ChatRequest, ChatResponse, ModelConfig } from '../types';

const BASE_URL = 'http://localhost:8080/api';

export const api = {
  models: {
    list: async (): Promise<string[]> => {
      const response = await fetch(`${BASE_URL}/models`);
      if (!response.ok) {
        throw new Error('Failed to fetch models');
      }
      return response.json();
    },
  },

  modelConfigs: {
    list: async (): Promise<ModelConfig[]> => {
      const response = await fetch(`${BASE_URL}/model-configs`);
      if (!response.ok) {
        throw new Error('Failed to fetch model configs');
      }
      return response.json();
    },

    get: async (id: number): Promise<ModelConfig> => {
      const response = await fetch(`${BASE_URL}/model-configs/${id}`);
      if (!response.ok) {
        throw new Error('Failed to fetch model config');
      }
      return response.json();
    },

    listByType: async (type: string): Promise<ModelConfig[]> => {
      const response = await fetch(`${BASE_URL}/model-configs/by-type/${type}`);
      if (!response.ok) {
        throw new Error('Failed to fetch model configs by type');
      }
      return response.json();
    },

    getTypes: async (): Promise<string[]> => {
      const response = await fetch(`${BASE_URL}/model-configs/types`);
      if (!response.ok) {
        throw new Error('Failed to fetch provider types');
      }
      return response.json();
    },

    create: async (config: Omit<ModelConfig, 'id'>): Promise<ModelConfig> => {
      const response = await fetch(`${BASE_URL}/model-configs`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      });
      if (!response.ok) {
        throw new Error('Failed to create model config');
      }
      return response.json();
    },

    update: async (id: number, config: Partial<ModelConfig>): Promise<ModelConfig> => {
      const response = await fetch(`${BASE_URL}/model-configs/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
      });
      if (!response.ok) {
        throw new Error('Failed to update model config');
      }
      return response.json();
    },

    delete: async (id: number): Promise<void> => {
      const response = await fetch(`${BASE_URL}/model-configs/${id}`, {
        method: 'DELETE',
      });
      if (!response.ok) {
        throw new Error('Failed to delete model config');
      }
    },
  },

  images: {
    upload: async (file: File): Promise<{ url: string }> => {
      const formData = new FormData();
      formData.append('image', file);
      const response = await fetch(`${BASE_URL}/images/upload`, {
        method: 'POST',
        body: formData,
      });
      if (!response.ok) {
        throw new Error('Failed to upload image');
      }
      return response.json();
    },

    delete: async (filename: string): Promise<void> => {
      const response = await fetch(`${BASE_URL}/images/${filename}`, {
        method: 'DELETE',
      });
      if (!response.ok) {
        throw new Error('Failed to delete image');
      }
    },
  },

  conversations: {
    list: async (): Promise<Conversation[]> => {
      const response = await fetch(`${BASE_URL}/conversations`);
      return response.json();
    },

    get: async (id: string): Promise<{ conversation: Conversation; messages: Message[] }> => {
      const response = await fetch(`${BASE_URL}/conversations/${id}`);
      return response.json();
    },

    create: async (title?: string): Promise<Conversation> => {
      const response = await fetch(`${BASE_URL}/conversations`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(title ? { title } : {}),
      });
      return response.json();
    },

    delete: async (id: string): Promise<void> => {
      await fetch(`${BASE_URL}/conversations/${id}`, {
        method: 'DELETE',
      });
    },

    update: async (id: string, title: string): Promise<Conversation> => {
      const response = await fetch(`${BASE_URL}/conversations/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title }),
      });
      return response.json();
    },
  },

  chat: {
    send: async (request: ChatRequest): Promise<ChatResponse> => {
      const response = await fetch(`${BASE_URL}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      });
      return response.json();
    },

    stream: async (
      request: ChatRequest,
      onMessage: (content: string) => void,
      onComplete: (messageId: string) => void,
      onError: (error: Error) => void,
      controller?: AbortController
    ): Promise<void> => {
      try {
        const abortController = controller || new AbortController();
        const timeout = setTimeout(() => {
          abortController.abort();
        }, 60000);

        const response = await fetch(`${BASE_URL}/chat/stream`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Accept: 'text/event-stream',
            'Cache-Control': 'no-cache',
            'Connection': 'keep-alive',
          },
          body: JSON.stringify(request),
          credentials: 'same-origin',
          signal: abortController.signal,
        });

        clearTimeout(timeout);

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        if (!response.body) {
          throw new Error('No response body');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        const processBuffer = () => {
          const index = buffer.indexOf('\n\n');
          if (index === -1) return false;

          const eventBlock = buffer.substring(0, index);
          buffer = buffer.substring(index + 2);

          const lines = eventBlock.split('\n');
          let eventType = '';
          let data = '';

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const trimmedLine = line.trim();

            if (trimmedLine.startsWith('event:')) {
              eventType = trimmedLine.substring(6).trim();
            } else if (trimmedLine.startsWith('data:')) {
              data += trimmedLine.substring(5);
            }
          }

          if (eventType && data) {
            try {
              const parsedData = JSON.parse(data);
              if (eventType === 'message' && parsedData.content) {
                onMessage(parsedData.content);
              } else if (eventType === 'done' && parsedData.messageId) {
                onComplete(parsedData.messageId);
              }
            } catch (e) {
              console.warn('Failed to parse SSE data:', e);
            }
          }
          return true;
        };

        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            if (value) {
              buffer += decoder.decode(value);
            }
            while (processBuffer()) {}
            break;
          }

          buffer += decoder.decode(value, { stream: true });
          while (processBuffer()) {}
        }
      } catch (error) {
        console.error('SSE stream error:', error);
        onError(error as Error);
      }
    },
  },
};
