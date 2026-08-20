import { request } from './client'

/**
 * Skill 完成钩子类型（与后端 Skill.CompletionHookType 对应）
 */
export type CompletionHookType = 'NONE' | 'CREATE_NOTE' | 'SCHEDULE_REMINDER' | 'SAVE_TO_KB'

/**
 * Skill 实体（前端视图，对应后端 SkillResponse）
 */
export interface Skill {
  id: string
  userId: string
  name: string
  description?: string
  icon?: string

  /** 专属 system prompt 模板（覆盖默认） */
  systemPromptTemplate?: string
  /** 追加到默认 prompt 末尾的补充指令 */
  systemPromptSupplement?: string

  /** 工具白名单（为空表示不限制） */
  allowedToolNames?: string[]
  /** 工具黑名单 */
  forbiddenToolNames?: string[]
  /** 触发关键词 */
  triggerKeywords?: string[]
  /** 触发意图类型 */
  triggerIntentTypes?: string[]

  /** 输入契约（JSON Schema 字符串） */
  inputSchemaJson?: string
  /** 输出契约（JSON Schema 字符串） */
  outputSchemaJson?: string

  completionHookType: CompletionHookType
  completionHookParamsJson?: string

  maxIterations: number
  isEnabled: boolean
  isPublic: boolean

  createdAt?: string
  updatedAt?: string
}

/**
 * 创建/更新 Skill 的请求体（对应后端 SkillRequest）
 */
export interface SkillRequest {
  name: string
  description?: string
  icon?: string
  systemPromptTemplate?: string
  systemPromptSupplement?: string
  allowedToolNames?: string[]
  forbiddenToolNames?: string[]
  triggerKeywords?: string[]
  triggerIntentTypes?: string[]
  inputSchemaJson?: string
  outputSchemaJson?: string
  completionHookType?: CompletionHookType
  completionHookParamsJson?: string
  maxIterations?: number
  isEnabled?: boolean
  isPublic?: boolean
}

const DEFAULT_USER_ID = 'default'

export const skills = {
  /**
   * 列出用户可见的 Skill（自己的 + 公共的）
   */
  list: async (userId: string = DEFAULT_USER_ID): Promise<Skill[]> => {
    return request(`/skills?userId=${encodeURIComponent(userId)}`)
  },

  /**
   * 获取 Skill 详情
   */
  get: async (skillId: string, userId: string = DEFAULT_USER_ID): Promise<Skill> => {
    return request(`/skills/${skillId}?userId=${encodeURIComponent(userId)}`)
  },

  /**
   * 创建 Skill
   */
  create: async (data: SkillRequest, userId: string = DEFAULT_USER_ID): Promise<Skill> => {
    return request(`/skills?userId=${encodeURIComponent(userId)}`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  /**
   * 更新 Skill
   */
  update: async (
    skillId: string,
    data: SkillRequest,
    userId: string = DEFAULT_USER_ID
  ): Promise<Skill> => {
    return request(`/skills/${skillId}?userId=${encodeURIComponent(userId)}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  /**
   * 删除 Skill
   */
  delete: async (skillId: string, userId: string = DEFAULT_USER_ID): Promise<void> => {
    await request(`/skills/${skillId}?userId=${encodeURIComponent(userId)}`, {
      method: 'DELETE',
    })
  },

  /**
   * 匹配激活的 Skill（手动 / 关键词）
   * 返回 null 表示无匹配（后端 204 No Content）
   */
  match: async (
    skillId?: string,
    userMessage?: string,
    userId: string = DEFAULT_USER_ID
  ): Promise<Skill | null> => {
    const params = new URLSearchParams({ userId })
    if (skillId) params.append('skillId', skillId)
    if (userMessage) params.append('userMessage', userMessage)
    // 204 No Content 时 request 返回空字符串，统一转换为 null
    const res = await request<unknown>(`/skills/match?${params.toString()}`, {
      method: 'POST',
    })
    if (!res || (typeof res === 'string' && res === '')) return null
    return res as Skill
  },
}
