export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt?: string
  pinned?: boolean
  isSummaryNote?: boolean
}

export interface Message {
  id: string
  conversationId: string
  content: string
  role: 'user' | 'assistant'
  timestamp: string
  images?: string[]
  /** Agent 模式下的思考过程步骤（工具调用、LLM 调用等），仅流式推送累积 */
  agentThinking?: AgentThinkingStep[]
}

/**
 * Agent 思考过程的单个步骤，对应后端 SSE agent_thinking 事件的 envelope。
 *
 * 后端推送结构（详见 ConversationContext#emitAgentThinking）：
 * ```json
 * {
 *   "type": "tool_definition" | "llm_call" | "tool_detection" | "tool_execution" | "tool_assembly" | "final_response",
 *   "iteration": 0,
 *   "timestamp": 1234567890,
 *   "data": { ... }
 * }
 * ```
 *
 * `data` 的形状由顶层 `type` 决定，渲染时按 `step.type` 分支处理。
 */
export type AgentThinkingStep = {
  type:
    | 'tool_definition'
    | 'llm_call'
    | 'tool_detection'
    | 'tool_execution'
    | 'tool_assembly'
    | 'final_response'
  iteration: number
  timestamp: number
  // 后端 data 负载是松散 Map，前端按 type 自行取字段
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  data: Record<string, any>
}

export interface ChatRequest {
  conversationId?: string
  message: string
  model?: string
  imageUrls?: string[]
  userId?: string
  webSearch?: boolean
  agentMode?: boolean
}

export interface ChatResponse {
  messageId: string
  content: string
  role: 'assistant'
  conversationId: string
  images?: string[]
  artifacts?: Array<{ type: string; url: string; text?: string }>
}

export interface SearchSnippet {
  title: string
  url: string
  snippet: string
}

export interface WebSearchResultData {
  query: string
  snippets: SearchSnippet[]
  timestamp: number
  status: 'success' | 'no_results' | 'error' | 'disabled'
  errorMessage?: string
}

/**
 * 后端工具信息（对应 GET /api/tools 返回结构）
 */
export interface ToolInfo {
  name: string
  description?: string
  parameters?: {
    type?: string
    properties?: Record<string, {
      type?: string
      description?: string
    }>
    required?: string[]
  }
  /** 该工具所需的模型能力（如 IMAGE_IN / IMAGE_OUT）。为 null/undefined 表示不依赖特定能力的模型。 */
  modelCapability?: string | null
}

export interface StreamingState {
  isStreaming: boolean
  currentContent: string
  messageId: string | null
}

export interface InputState {
  value: string
  isFocused: boolean
}

export type ProviderType =
  | 'OPENAI'
  | 'OPENAI_COMPATIBLE'
  | 'ANTHROPIC'
  | 'GOOGLE'
  | 'OLLAMA'
  | 'AZURE'
  | 'CUSTOM'

export interface Model {
  id: string
  name: string
  type: string
}

export type ModelCategory = 'TEXT' | 'IMAGE' | 'VIDEO'

export interface ModelConfig {
  id: string | number
  name: string
  modelId: string
  baseUrl: string
  apiKey: string
  type: ProviderType
  category: ModelCategory
  capabilities?: string[] | string
  enabled: boolean
  createdAt?: string
}

export interface CategoryInfo {
  type: ModelCategory
  displayName: string
  icon: string
  color: string
}

export const CATEGORIES: CategoryInfo[] = [
  { type: 'TEXT', displayName: '文本', icon: '💬', color: 'bg-blue-500' },
  { type: 'IMAGE', displayName: '图像', icon: '🖼️', color: 'bg-green-500' },
  { type: 'VIDEO', displayName: '视频', icon: '🎬', color: 'bg-purple-500' },
]

export interface ProviderInfo {
  type: ProviderType
  displayName: string
  icon: string
  color: string
  defaultBaseUrl?: string
}

export const PROVIDERS: ProviderInfo[] = [
  {
    type: 'OPENAI',
    displayName: 'OpenAI',
    icon: '🧠',
    color: 'bg-green-500',
    defaultBaseUrl: 'https://api.openai.com',
  },
  {
    type: 'ANTHROPIC',
    displayName: 'Anthropic',
    icon: '🔮',
    color: 'bg-yellow-500',
    defaultBaseUrl: 'https://api.anthropic.com',
  },
  {
    type: 'GOOGLE',
    displayName: 'Google',
    icon: '🌐',
    color: 'bg-blue-600',
    defaultBaseUrl: 'https://generativelanguage.googleapis.com',
  },
  {
    type: 'OLLAMA',
    displayName: 'Ollama',
    icon: '🦙',
    color: 'bg-purple-600',
    defaultBaseUrl: 'http://localhost:11434',
  },
  {
    type: 'AZURE',
    displayName: 'Azure OpenAI',
    icon: '☁️',
    color: 'bg-blue-500',
    defaultBaseUrl: 'https://your-resource.openai.azure.com',
  },
  { type: 'CUSTOM', displayName: '自定义', icon: '🔧', color: 'bg-gray-600' },
]

export type MemoryType =
  | 'KNOWLEDGE'
  | 'RULE'
  | 'FACT'
  | 'PREFERENCE'
  | 'EXPERIENCE'
  | 'PROFILE'
  | 'SKILL'
  | 'PROJECT'
  | 'TASK'
  | 'RELATION'
  | 'EVENT'

export interface Memory {
  id: number
  userId: string
  content: string
  type: MemoryType
  importance: number
  createdAt: string
  score?: number
  isRule?: boolean
}

export type IconName = import('../components/common/Icon').IconName

export interface MemoryTypeInfo {
  type: MemoryType
  label: string
  color: string
  icon: IconName
}

export const MEMORY_TYPES: MemoryTypeInfo[] = [
  { type: 'KNOWLEDGE', label: '知识', color: 'bg-blue-500', icon: 'BookOpen' },
  { type: 'PROFILE', label: '身份', color: 'bg-cyan-500', icon: 'User' },
  { type: 'SKILL', label: '技能', color: 'bg-emerald-500', icon: 'Wrench' },
  { type: 'PROJECT', label: '项目', color: 'bg-violet-500', icon: 'Briefcase' },
  { type: 'PREFERENCE', label: '偏好', color: 'bg-pink-500', icon: 'Heart' },
  { type: 'TASK', label: '任务', color: 'bg-amber-500', icon: 'Target' },
  { type: 'RELATION', label: '关系', color: 'bg-indigo-500', icon: 'Users' },
  { type: 'EVENT', label: '事件', color: 'bg-orange-500', icon: 'Calendar' },
  { type: 'FACT', label: '事实', color: 'bg-green-500', icon: 'CheckCircle' },
  { type: 'RULE', label: '规则', color: 'bg-red-500', icon: 'FileText' },
  { type: 'EXPERIENCE', label: '经验', color: 'bg-teal-500', icon: 'Lightbulb' },
]
