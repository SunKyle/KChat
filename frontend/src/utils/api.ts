import type { Conversation, Message, ChatRequest, ChatResponse } from '../types';

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
      console.log('Starting SSE stream request...');
      console.log('Request URL:', `${BASE_URL}/chat/stream`);
      console.log('Request body:', JSON.stringify(request));
      console.log('Model:', request.model);
      try {
        const abortController = controller || new AbortController();
        const timeout = setTimeout(() => {
          console.warn('SSE request timeout, aborting');
          abortController.abort();
        }, 60000);

        console.log('About to fetch...');
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

        console.log('Fetch completed, status:', response.status);
        clearTimeout(timeout);

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        if (!response.body) {
          throw new Error('No response body');
        }

        console.log('SSE connection established');

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        const processBuffer = () => {
          console.log('Processing buffer, length:', buffer.length);
          console.log('Buffer preview:', buffer.substring(0, 200));
          
          const index = buffer.indexOf('\n\n');
          if (index === -1) return false;

          const eventBlock = buffer.substring(0, index);
          buffer = buffer.substring(index + 2);

          console.log('Event block:', eventBlock);
          
          const lines = eventBlock.split('\n');
          console.log('Lines array:', lines);
          let eventType = '';
          let data = '';

          for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            const trimmedLine = line.trim();
            console.log(`Line ${i}: [${line.length} chars], trimmed: "${trimmedLine}"`);
            
            if (trimmedLine.startsWith('event:')) {
              eventType = trimmedLine.substring(6).trim();
              console.log('Found event type:', eventType);
            } else if (trimmedLine.startsWith('data:')) {
              data += trimmedLine.substring(5);
              console.log('Found data:', data.substring(0, 50));
            } else if (trimmedLine) {
              console.log('Unexpected line:', line);
            }
          }

          if (eventType && data) {
            try {
              const parsedData = JSON.parse(data);
              console.log('Parsed data:', parsedData);
              if (eventType === 'message' && parsedData.content) {
                console.log('SSE message received:', parsedData.content.substring(0, 50));
                onMessage(parsedData.content);
              } else if (eventType === 'done' && parsedData.messageId) {
                console.log('SSE done event received:', parsedData.messageId);
                onComplete(parsedData.messageId);
              }
            } catch (e) {
              console.warn('Failed to parse SSE data:', data, e);
            }
          }
          return true;
        };

        while (true) {
          const { done, value } = await reader.read();
          console.log('Reader read:', { done, value: value ? `[${value.length} bytes]` : null });
          
          if (done) {
            if (value) {
              buffer += decoder.decode(value);
              console.log('Final chunk decoded:', buffer.substring(0, 100));
            }
            while (processBuffer()) {}
            if (buffer.trim()) {
              console.warn('Remaining buffer after stream ends:', buffer);
            }
            break;
          }

          buffer += decoder.decode(value, { stream: true });
          console.log('Buffer after decode:', buffer.substring(0, 100));
          while (processBuffer()) {}
        }

        console.log('SSE stream completed');
      } catch (error) {
        console.error('SSE stream error:', error);
        onError(error as Error);
      }
    },
  },
};
