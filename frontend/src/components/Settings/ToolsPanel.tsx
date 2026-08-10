import { useState, useEffect } from 'react'
import { Wrench, Loader2, RefreshCw, AlertTriangle, FunctionSquare } from 'lucide-react'
import { tools as toolsApi } from '../../api'
import type { ToolInfo } from '../../types'

/**
 * Agent 工具箱展示面板
 *
 * 从 GET /api/tools 加载 ToolRegistry 中注册的所有工具，
 * 展示工具名、描述、参数 schema，供用户了解 Agent 模式下可调用的能力。
 */
export function ToolsPanel() {
  const [toolList, setToolList] = useState<ToolInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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
        Agent 模式下 LLM 可调用以下工具。模型会根据用户意图自动决定是否调用以及调用哪个工具。
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
            <ToolCard key={tool.name} tool={tool} />
          ))}
        </div>
      )}
    </div>
  )
}

interface ToolCardProps {
  tool: ToolInfo
}

function ToolCard({ tool }: ToolCardProps) {
  const paramEntries = Object.entries(tool.parameters?.properties ?? {})
  const requiredSet = new Set(tool.parameters?.required ?? [])

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
          </div>
          {tool.description && (
            <p className='text-sm theme-text-secondary mt-1 leading-relaxed'>
              {tool.description}
            </p>
          )}
        </div>
      </div>

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
