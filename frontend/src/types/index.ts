export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt?: string;
}

export interface Message {
  id: string;
  conversationId: string;
  content: string;
  role: 'user' | 'assistant';
  timestamp: string;
  images?: string[];
}

export interface ChatRequest {
  conversationId?: string;
  message: string;
  model?: string;
  imageUrls?: string[];
}

export interface ChatResponse {
  messageId: string;
  content: string;
  role: 'assistant';
  conversationId: string;
}

export interface StreamingState {
  isStreaming: boolean;
  currentContent: string;
  messageId: string | null;
}

export interface InputState {
  value: string;
  isFocused: boolean;
}

export interface ModelConfig {
  id: number;
  name: string;
  modelId: string;
  baseUrl: string;
  apiKey: string;
  type: string;
  enabled: boolean;
  createdAt: string;
}
