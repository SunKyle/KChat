export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt?: string
  pinned?: boolean
  isSummaryNote?: boolean
  customRules?: string
}

/**
 * 知识库引用来源（文档层级）。
 *
 * `kbName` 为知识库名称，`docName` 为命中的具体文档名（可空）。
 * 当溯源元数据缺失时 `docName` 为 undefined，前端降级为仅展示知识库层级。
 */
export interface KbReference {
  kbName: string
  docName?: string | null
}

/**
 * 归一化知识库引用来源：兼容历史消息中的纯字符串数组（旧格式 ["知识库A"]）。
 */
export function normalizeKbReferences(value: unknown): KbReference[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item): KbReference | null => {
      if (typeof item === 'string') return { kbName: item }
      if (item && typeof item === 'object') {
        const ref = item as Partial<KbReference>
        if (typeof ref.kbName === 'string' && ref.kbName) {
          return { kbName: ref.kbName, docName: ref.docName ?? undefined }
        }
      }
      return null
    })
    .filter((ref): ref is KbReference => ref !== null)
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
  /** 该回复引用的知识库来源（含知识库名 + 文档名），用于展示"引用来源"标签 */
  kbReferences?: KbReference[]
}

/**
 * Agent 思考过程的单个步骤，对应后端 SSE agent_thinking 事件的 envelope。
 *
 * 后端推送结构（详见 ConversationContext#emitAgentThinking）：
 * ```json
 * {
 *   "type": "tool_definition" | "llm_call" | "tool_detection" | "tool_execution"
 *         | "tool_assembly" | "final_response" | "skill_resolution" | "skill_completion",
 *   "frameId": 0,
 *   "role": "ORCHESTRATOR" | "SPECIALIST",
 *   "skillId": null,
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
    | 'skill_resolution'
    | 'skill_completion'
    | 'skill_enter'
    | 'skill_exit'
  /** 当前栈帧 ID（0=Orchestrator，>0=Skill 层） */
  frameId?: number
  /** 帧角色（ORCHESTRATOR / SPECIALIST） */
  role?: 'ORCHESTRATOR' | 'SPECIALIST'
  /** 当前帧的 Skill ID（Orchestrator 帧为 null） */
  skillId?: string | null
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
  knowledgeBaseIds?: string[]
  /** 手动激活的 Skill ID（前端技能选择器透传，非空时 SkillResolutionStage 直接激活） */
  skillId?: string
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
      example?: string
      enum?: string[]
    }>
    required?: string[]
  }
  /** 该工具所需的模型能力（如 IMAGE_IN / IMAGE_OUT）。为 null/undefined 表示不依赖特定能力的模型。 */
  modelCapability?: string | null
  /** 工具是否启用。true 表示启用，false 表示已关闭（对 LLM 不可见）。 */
  enabled?: boolean
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

export type IconName = import('../components/common/Icon').IconName
