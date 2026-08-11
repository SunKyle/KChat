import { useState, useEffect, useCallback } from 'react'
import {
  Wrench,
  Loader2,
  RefreshCw,
  AlertTriangle,
  FunctionSquare,
  Check,
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
 */
export function ToolsPanel() {
  const [toolList, setToolList] = useState<ToolInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [modelList, setModelList] = useState<ModelConfig[]>([])
  const [toolModels, setToolModels] = useState<Record<string, string>>({})

  const loadTools = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await toolsApi.list()
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
      } catch (err) {
        console.error('Failed to load models/settings:', err)
      }
    })()
  }, [])

  // 保存某工具配置的默认模型
  const saveToolModel = useCallback(async (toolName: string, modelId: string) => {
    setToolModels((prev) => {
      const next = { ...prev }
      if (modelId) {
        next[toolName] = modelId
      } else {
        delete next[toolName]
      }
      // 后台保存
      settingsApi
        .update({ toolModels: next }, DEFAULT_USER_ID)
        .then(() => {
          // 成功
        })
        .catch((err) => console.error('Failed to save tool model:', err))
      return next
    })
  }, [])

  return (
    <div className='space-y-6'>
      <div className='flex items-center justify-between'>
        <div className='flex items-center gap-2'>
          <Wrench className='w-5 h-5 theme-text-muted' />
          <h3 className='font-semibold theme-text-primary'>工具箱</h3>
          {!loading && !error && (
            <span className='text-xs px-2 py-0.5 rounded-full theme-bg-hover theme-text-secondary'>
              {toolList.length} 个工具
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
        Agent 模式下 LLM 可调用以下工具。依赖模型能力的工具可为它指定默认模型；不指定时自动选择。
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
              onModelChange={(modelId) => saveToolModel(tool.name, modelId)}
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
  onModelChange: (modelId: string) => void
}

function ToolCard({ tool, modelList, currentModel, onModelChange }: ToolCardProps) {
  const paramEntries = Object.entries(tool.parameters?.properties ?? {})
  const requiredSet = new Set(tool.parameters?.required ?? [])

  // 该工具是否需要模型能力，以及具备该能力的可选模型
  const needCapability = Boolean(tool.modelCapability)
  const options = needCapability
    ? modelList.filter((m) => m.enabled && hasCapability(m, tool.modelCapability!))
    : []

  return (
    <div className='card-float-solid rounded-2xl p-4'>
      <div className='flex items-start gap-3 mb-2'>
        <div className='flex-shrink-0 w-9 h-9 rounded-lg bg-[var(--accent-primary)]/10 flex items-center justify-center'>
          <FunctionSquare className='w-5 h-5 text-[var(--accent-primary)]' />
        </div>
        <div className='flex-1 min-w-0'>
          <div className='flex items-center gap-2 flex-wrap'>
            <code className='font-mono font-semibold theme-text-primary break-all'>
              {tool.name}
            </code>
            {needCapability && (
              <span className='text-xs px-1.5 py-0.5 rounded bg-[var(--accent-primary)]/10 text-[var(--accent-primary)] font-medium font-mono'>
                {tool.modelCapability}
              </span>
            )}
          </div>
          {tool.description && (
            <p className='text-sm theme-text-secondary mt-1 leading-relaxed'>
              {tool.description}
            </p>
          )}
        </div>
      </div>

      {needCapability && (
        <div className='mt-3 flex items-center gap-2'>
          <label className='text-xs font-semibold theme-text-muted whitespace-nowrap'>
            使用的模型
          </label>
          <select
            value={currentModel}
            onChange={(e) => onModelChange(e.target.value)}
            className='flex-1 min-w-0 text-sm rounded-lg border theme-border-primary theme-bg-input px-2.5 py-1.5 theme-text-primary focus:outline-none'
          >
            <option value=''>自动选择</option>
            {options.map((m) => (
              <option key={m.id} value={`${m.name}:${m.modelId}`}>
                {m.name}:{m.modelId}
              </option>
            ))}
          </select>
          {currentModel && <Check className='w-4 h-4 text-green-500 flex-shrink-0' />}
        </div>
      )}

      {paramEntries.length > 0 && (
        <div className='mt-3 pt-3 border-t theme-border-primary space-y-2'>
          <div className='text-xs font-semibold theme-text-muted uppercase tracking-wide'>
            参数
          </div>
          {paramEntries.map(([paramName, schema]) => (
            <div key={paramName} className='flex items-start gap-2 text-sm'>
              <code className='font-mono theme-text-primary flex-shrink-0'>{paramName}</code>
              {schema?.type && (
                <span className='text-xs px-1.5 py-0.5 rounded theme-bg-hover theme-text-muted font-mono'>
                  {schema.type}
                </span>
              )}
              {requiredSet.has(paramName) && (
                <span className='text-xs px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-600 font-medium'>
                  必填
                </span>
              )}
              {schema?.description && (
                <span className='text-xs theme-text-muted flex-1'>
                  {schema.description}
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {paramEntries.length === 0 && (
        <div className='mt-3 pt-3 border-t theme-border-primary'>
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
