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

export type ProviderType = 'OPENAI' | 'OPENAI_COMPATIBLE' | 'ANTHROPIC' | 'GOOGLE' | 'OLLAMA' | 'AZURE' | 'CUSTOM';

export interface ModelConfig {
  id: number;
  name: string;
  modelId: string;
  baseUrl: string;
  apiKey: string;
  type: ProviderType;
  enabled: boolean;
  createdAt: string;
}

export interface ProviderInfo {
  type: ProviderType;
  displayName: string;
  icon: string;
  color: string;
  defaultBaseUrl?: string;
}

export const PROVIDERS: ProviderInfo[] = [
  { type: 'OPENAI', displayName: 'OpenAI', icon: '🧠', color: 'bg-green-500', defaultBaseUrl: 'https://api.openai.com' },
  { type: 'ANTHROPIC', displayName: 'Anthropic', icon: '🔮', color: 'bg-yellow-500', defaultBaseUrl: 'https://api.anthropic.com' },
  { type: 'GOOGLE', displayName: 'Google', icon: '🌐', color: 'bg-blue-600', defaultBaseUrl: 'https://generativelanguage.googleapis.com' },
  { type: 'OLLAMA', displayName: 'Ollama', icon: '🦙', color: 'bg-purple-600', defaultBaseUrl: 'http://localhost:11434' },
  { type: 'AZURE', displayName: 'Azure OpenAI', icon: '☁️', color: 'bg-blue-500', defaultBaseUrl: 'https://your-resource.openai.azure.com' },
  { type: 'CUSTOM', displayName: '自定义', icon: '🔧', color: 'bg-gray-600' },
];
