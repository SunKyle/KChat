import { useState, useEffect, useCallback } from 'react'
import {
  Wrench,
  Loader2,
  RefreshCw,
  AlertTriangle,
  Power,
  Sliders,
  ChevronDown,
} from 'lucide-react'
import { tools as toolsApi, settingsApi, modelConfigs } from '../../api'
import type { ToolInfo, ModelConfig } from '../../types'

const DEFAULT_USER_ID = 'default'

/**
 * Agent 工具箱展示面板
 *
 * 从 GET /api/tools 加载 ToolRegistry 中注册的所有工具，
 * 展示工具名、描述、参数 schema，供用户了解 Agent 模式下可调用的能力。
 *
 * 对依赖特定模型能力的工具（如图片生成/识别），额外提供模型下拉框，
 * 用户可为该工具指定默认模型，持久化到 user_setting.tool_models。
 *
 * 每个工具支持启用/关闭切换，关闭的工具对 LLM 不可见。
 */
export function ToolsPanel() {
  const [toolList, setToolList] = useState<ToolInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modelList, setModelList] = useState<ModelConfig[]>([])
  const [toolModels, setToolModels] = useState<Record<string, string>>({})
  const [enabledTools, setEnabledTools] = useState<Record<string, boolean>>({})
  const [togglingTools, setTogglingTools] = useState<Set<string>>(new Set())

  const loadTools = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await toolsApi.list(DEFAULT_USER_ID)
      setToolList(data)
    } catch (err) {
      console.error('Failed to load tools:', err)
      setError(err instanceof Error ? err.message : '加载工具列表失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadTools()
    ;(async () => {
      try {
        const [configs, settings] = await Promise.all([
          modelConfigs.list(),
          settingsApi.get(DEFAULT_USER_ID),
        ])
        setModelList(configs)
        if (settings.toolModels) {
          setToolModels(settings.toolModels)
        }
        if (settings.enabledTools) {
          setEnabledTools(settings.enabledTools)
        }
      } catch (err) {
        console.error('Failed to load models/settings:', err)
      }
    })()
  }, [])

  // 保存某工具配置的默认模型
  const saveToolModel = useCallback(
    async (toolName: string, modelId: string) => {
      setToolModels((prev) => {
        const next = { ...prev }
        if (modelId) {
          next[toolName] = modelId
        } else {
          delete next[toolName]
        }
        // 后台保存
        settingsApi
          .update({ toolModels: next, enabledTools }, DEFAULT_USER_ID)
          .then(() => {
            // 成功
          })
          .catch((err) => console.error('Failed to save tool model:', err))
        return next
      })
    },
    [enabledTools]
  )

  // 切换工具启用/关闭状态
  const toggleToolEnabled = useCallback(
    async (toolName: string, enabled: boolean) => {
      setTogglingTools((prev) => {
        const next = new Set(prev)
        next.add(toolName)
        return next
      })

      // 立即更新本地状态
      setEnabledTools((prev) => {
        const next = { ...prev }
        if (enabled) {
          delete next[toolName] // 启用时从 map 中移除（默认启用）
        } else {
          next[toolName] = false
        }
        // 后台保存
        settingsApi
          .update({ enabledTools: next, toolModels }, DEFAULT_USER_ID)
          .then(() => {
            // 同步更新 toolList 中的 enabled 状态
            setToolList((tools) => tools.map((t) => (t.name === toolName ? { ...t, enabled } : t)))
          })
          .catch((err) => {
            console.error('Failed to save tool enabled state:', err)
            // 回滚本地状态
            setEnabledTools((prev) => {
              const rollback = { ...prev }
              if (enabled) {
                rollback[toolName] = false
              } else {
                delete rollback[toolName]
              }
              return rollback
            })
          })
          .finally(() => {
            setTogglingTools((prev) => {
              const next = new Set(prev)
              next.delete(toolName)
              return next
            })
          })
        return next
      })
    },
    [toolModels]
  )

  return (
    <div className='space-y-6'>
      <div className='flex items-center justify-between'>
        <div className='flex items-center gap-2'>
          <Wrench className='w-5 h-5 theme-text-muted' />
          <h3 className='font-semibold theme-text-primary'>工具箱</h3>
          {!loading && !error && (
            <span className='text-xs px-2 py-0.5 rounded-full theme-bg-hover theme-text-secondary'>
              {toolList.length} 个工具
              {toolList.some((t) => t.enabled === false) && (
                <span className='ml-1 text-amber-500'>
                  · {toolList.filter((t) => t.enabled === false).length} 已关闭
                </span>
              )}
            </span>
          )}
        </div>
        <button
          onClick={loadTools}
          disabled={loading}
          className='icon-btn disabled:opacity-50'
          title='刷新'
        >
          <RefreshCw className={`w-4 h-4 theme-text-muted ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      <p className='text-sm theme-text-muted'>
        LLM 可调用以下工具。关闭的工具对大模型不可见。
        依赖模型能力的工具可为它指定默认模型；不指定时自动选择。
      </p>

      {loading ? (
        <div className='flex items-center justify-center py-12'>
          <Loader2 className='w-6 h-6 theme-text-muted animate-spin' />
        </div>
      ) : error ? (
        <div className='card-float-solid rounded-2xl p-6 text-center'>
          <AlertTriangle className='w-10 h-10 text-amber-500 mx-auto mb-3' />
          <p className='font-semibold theme-text-primary mb-1'>加载失败</p>
          <p className='text-sm theme-text-muted mb-4'>{error}</p>
          <button
            onClick={loadTools}
            className='text-sm text-[var(--accent-primary)] hover:underline'
          >
            重试
          </button>
        </div>
      ) : toolList.length === 0 ? (
        <div className='card-float-solid rounded-2xl p-8 text-center'>
          <Wrench className='w-12 h-12 theme-text-muted mx-auto mb-4' />
          <p className='theme-text-secondary mb-2'>暂无注册工具</p>
          <p className='text-sm theme-text-muted'>
            在后端实现 ToolComponent 接口并添加 @Tool 注解即可在此展示
          </p>
        </div>
      ) : (
        <div className='space-y-3'>
          {toolList.map((tool) => (
            <ToolCard
              key={tool.name}
              tool={tool}
              modelList={modelList}
              currentModel={toolModels[tool.name] ?? ''}
              isToggling={togglingTools.has(tool.name)}
              onModelChange={(modelId) => saveToolModel(tool.name, modelId)}
              onToggleEnabled={(enabled) => toggleToolEnabled(tool.name, enabled)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

interface ToolCardProps {
  tool: ToolInfo
  modelList: ModelConfig[]
  currentModel: string
  isToggling: boolean
  onModelChange: (modelId: string) => void
  onToggleEnabled: (enabled: boolean) => void
}

function ToolCard({
  tool,
  modelList,
  currentModel,
  isToggling,
  onModelChange,
  onToggleEnabled,
}: ToolCardProps) {
  const [paramsExpanded, setParamsExpanded] = useState(false)
  const paramEntries = Object.entries(tool.parameters?.properties ?? {})
  const requiredSet = new Set(tool.parameters?.required ?? [])
  const isEnabled = tool.enabled !== false

  const needCapability = Boolean(tool.modelCapability)
  const options = needCapability
    ? modelList.filter((m) => m.enabled && hasCapability(m, tool.modelCapability!))
    : []

  const typeColor: Record<string, string> = {
    string: 'bg-blue-500/10 text-blue-600',
    integer: 'bg-purple-500/10 text-purple-600',
    number: 'bg-cyan-500/10 text-cyan-600',
    boolean: 'bg-emerald-500/10 text-emerald-600',
    array: 'bg-amber-500/10 text-amber-600',
    object: 'bg-slate-500/10 text-slate-600',
  }

  return (
    <div
      className={`card-float-solid rounded-2xl transition-opacity overflow-hidden ${
        !isEnabled ? 'opacity-50' : ''
      }`}
    >
      {/* 头部 */}
      <div className='p-4'>
        <div className='flex items-start gap-3'>
          <div className='flex-1 min-w-0'>
            <div className='flex items-center gap-2 flex-wrap mb-1.5'>
              <code className='font-mono font-semibold theme-text-primary text-base break-all'>
                {tool.name}
              </code>
              {needCapability && (
                <span className='inline-flex items-center text-[10px] px-2.5 py-1 rounded-full theme-bg-accent-primary/10 theme-accent-primary font-semibold font-mono tracking-wide uppercase'>
                  {tool.modelCapability}
                </span>
              )}
            </div>
            {tool.description && (
              <p className='text-sm theme-text-secondary leading-relaxed'>{tool.description}</p>
            )}
          </div>
          <div className='flex-shrink-0 flex items-center gap-2'>
            <button
              onClick={() => onToggleEnabled(!isEnabled)}
              disabled={isToggling}
              className={`relative w-11 h-6 rounded-full transition-colors ${
                isEnabled ? 'bg-green-500 hover:bg-green-600' : 'theme-bg-hover hover:bg-red-500/30'
              } ${isToggling ? 'opacity-50 cursor-not-allowed' : ''}`}
              title={isEnabled ? '点击关闭（对 LLM 不可见）' : '点击启用'}
            >
              <span
                className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform flex items-center justify-center ${
                  isEnabled ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              >
                {isToggling ? (
                  <Loader2 className='w-3 h-3 theme-text-muted animate-spin' />
                ) : (
                  <Power
                    className={`w-3 h-3 ${isEnabled ? 'text-green-500' : 'theme-text-muted'}`}
                  />
                )}
              </span>
            </button>
          </div>
        </div>
      </div>

      {/* 使用的模型 */}
      {needCapability && (
        <div className='px-4 pb-3'>
          <div className='flex items-center gap-2 p-3 rounded-xl theme-bg-hover'>
            <label className='text-xs font-semibold theme-text-muted whitespace-nowrap'>
              使用的模型
            </label>
            <select
              value={currentModel}
              onChange={(e) => onModelChange(e.target.value)}
              className='flex-1 min-w-0 text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-1.5 theme-text-primary focus:outline-none'
            >
              <option value=''>自动选择</option>
              {options.map((m) => (
                <option key={m.id} value={`${m.name}:${m.modelId}`}>
                  {m.name}:{m.modelId}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {/* 参数 */}
      {paramEntries.length > 0 && (
        <div className='border-t theme-border-primary'>
          <button
            onClick={() => setParamsExpanded(!paramsExpanded)}
            className='w-full px-4 py-3 flex items-center gap-2 hover:theme-bg-hover transition-colors'
          >
            <div className='flex-shrink-0 w-7 h-7 rounded-lg theme-bg-accent-primary/10 flex items-center justify-center'>
              <Sliders className='w-4 h-4 theme-accent-primary' />
            </div>
            <span className='text-sm font-semibold theme-text-primary'>参数</span>
            <span className='text-xs px-2 py-0.5 rounded-full theme-bg-hover theme-text-muted font-medium'>
              {paramEntries.length} 个参数
            </span>
            <div className='flex-1' />
            <ChevronDown
              className={`w-4 h-4 theme-text-muted transition-transform duration-200 ${
                paramsExpanded ? 'rotate-180' : ''
              }`}
            />
          </button>

          {paramsExpanded && (
            <div className='px-4 pb-4 space-y-2.5'>
              {paramEntries.map(([paramName, schema]) => {
                const type = (schema?.type as string) || 'string'
                const colorClass = typeColor[type] || typeColor.object
                return (
                  <div
                    key={paramName}
                    className='p-3 rounded-xl theme-border-primary border theme-bg-card'
                  >
                    <div className='flex items-center gap-2 mb-1.5'>
                      <code className='font-mono font-semibold theme-text-primary text-sm'>
                        {paramName}
                      </code>
                      {schema?.type && (
                        <span
                          className={`text-[10px] px-1.5 py-0.5 rounded font-mono font-medium ${colorClass}`}
                        >
                          {schema.type}
                        </span>
                      )}
                      {requiredSet.has(paramName) && (
                        <span className='text-[10px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 font-semibold'>
                          必填
                        </span>
                      )}
                    </div>
                    {schema?.description && (
                      <p className='text-xs theme-text-muted leading-relaxed mb-1'>
                        {schema.description}
                      </p>
                    )}
                    {schema?.example && (
                      <p className='text-xs theme-text-muted/70 font-mono'>
                        示例: {schema.example}
                      </p>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {paramEntries.length === 0 && (
        <div className='px-4 py-3 border-t theme-border-primary'>
          <span className='text-xs theme-text-muted'>无参数</span>
        </div>
      )}
    </div>
  )
}

/**
 * 镜像后端 ModelConfigService.resolveCapabilities 的能力推断逻辑，
 * 用于前端过滤某工具可选的模型。
 */
function hasCapability(model: ModelConfig, capability: string): boolean {
  const caps = new Set<string>()
  const raw = model.capabilities
  if (Array.isArray(raw)) {
    raw.forEach((c) => caps.add(c))
  } else if (typeof raw === 'string' && raw.trim()) {
    try {
      const arr = JSON.parse(raw)
      if (Array.isArray(arr)) arr.forEach((c) => caps.add(String(c)))
    } catch {
      caps.add(raw)
    }
  }
  switch (model.category) {
    case 'TEXT':
      caps.add('TEXT_IN')
      caps.add('TEXT_OUT')
      break
    case 'IMAGE':
      caps.add('IMAGE_OUT')
      break
    case 'VIDEO':
      caps.add('VIDEO_IN')
      caps.add('VIDEO_OUT')
      break
  }
  return caps.has(capability)
}
