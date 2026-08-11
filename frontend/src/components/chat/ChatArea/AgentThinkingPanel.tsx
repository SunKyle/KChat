import { useState } from 'react'
import {
  ChevronDown,
  Wrench,
  Sparkles,
  Search,
  CheckCircle,
  XCircle,
  Layers,
  MessageCircle,
} from 'lucide-react'
import type { AgentThinkingStep } from '../../../types'

interface AgentThinkingPanelProps {
  steps: AgentThinkingStep[]
}

const STEP_LABEL: Record<AgentThinkingStep['type'], string> = {
  tool_definition: '加载工具',
  llm_call: 'LLM 思考',
  tool_detection: '发起工具调用',
  tool_execution: '工具执行结果',
  tool_assembly: '回填工具结果',
  final_response: '生成最终回复',
}

function StepIcon({ type, success }: { type: AgentThinkingStep['type']; success?: boolean }) {
  const cls = 'w-3.5 h-3.5 flex-shrink-0'
  switch (type) {
    case 'tool_definition':
      return <Wrench className={`${cls} text-[var(--accent-purple)]`} />
    case 'llm_call':
      return <Sparkles className={`${cls} text-[var(--accent-purple)]`} />
    case 'tool_detection':
      return <Search className={`${cls} text-[var(--accent-purple)]`} />
    case 'tool_execution':
      return success ? (
        <CheckCircle className={`${cls} text-[var(--accent-emerald)]`} />
      ) : (
        <XCircle className={`${cls} text-[var(--accent-rose)]`} />
      )
    case 'tool_assembly':
      return <Layers className={`${cls} text-[var(--accent-purple)]`} />
    case 'final_response':
      return <MessageCircle className={`${cls} text-[var(--accent-emerald)]`} />
  }
}

function formatStepDetail(step: AgentThinkingStep): string | null {
  const d = step.data
  switch (step.type) {
    case 'tool_definition': {
      const tools = (d.tools as string[]) || []
      return `可用工具 (${d.count ?? tools.length}): ${tools.join(', ')}`
    }
    case 'llm_call': {
      const model = d.model ?? '未知'
      const preview = d.inputPreview ? `输入: ${d.inputPreview}` : null
      const executed = (d.executedToolNames as string[]) || []
      const executedLine =
        executed.length > 0 ? `已执行工具(${executed.length}): ${executed.join(', ')}` : '尚未执行工具'
      const tokenLine = `消息 ${d.messageCount ?? 0} 条 · Token ${d.tokenCount ?? 0} · ${
        d.truncated ? '已截断' : '未截断'
      }`
      return [`模型: ${model}`, preview, executedLine, tokenLine].filter(Boolean).join('\n')
    }
    case 'tool_detection': {
      const args = (d.arguments as string) || '{}'
      const model = d.model ? ` · 模型: ${d.model}` : ''
      return `工具: ${d.toolName ?? '未知'}${model}\n参数: ${args}`
    }
    case 'tool_execution': {
      const result = d.result == null ? '' : String(d.result)
      const model = d.model ? ` · 模型: ${d.model}` : ''
      const head = d.success
        ? `工具: ${d.toolName ?? '未知'} ✓${model}`
        : `工具: ${d.toolName ?? '未知'} ✗ (${d.errorMessage ?? '执行失败'})`
      return `${head}\n结果: ${result}`
    }
    case 'tool_assembly':
      return `回填 ${d.assembledCount ?? 0} 个工具结果，消息总数 ${d.totalMessages ?? 0}`
    case 'final_response':
      return null // 最终回复已在主消息气泡展示，无需重复
    default:
      return null
  }
}

export function AgentThinkingPanel({ steps }: AgentThinkingPanelProps) {
  const [expanded, setExpanded] = useState(false)

  if (!steps || steps.length === 0) return null

  // final_response 步骤不展示详情，只在计数中体现
  const visibleSteps = steps.filter((s) => s.type !== 'final_response')

  return (
    <div className='mb-2'>
      <div className='rounded-xl border border-[var(--accent-purple)]/20 bg-[var(--accent-purple)]/5 overflow-hidden'>
        <button
          onClick={() => setExpanded(!expanded)}
          className='w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--accent-purple)]/10 transition-colors'
          aria-expanded={expanded}
        >
          <Sparkles className='w-3.5 h-3.5 text-[var(--accent-purple)] flex-shrink-0' />
          <span className='text-xs font-semibold text-[var(--accent-purple)]'>
            思考过程 · {visibleSteps.length} 步
          </span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-[var(--accent-purple)]/60 ml-auto transition-transform duration-200 ${
              expanded ? 'rotate-180' : ''
            }`}
          />
        </button>
        {expanded && (
          <div className='px-3 pb-3 space-y-2'>
            {visibleSteps.map((step, idx) => {
              const detail = formatStepDetail(step)
              const success =
                step.type === 'tool_execution' ? Boolean(step.data.success) : undefined
              return (
                <div
                  key={`${step.type}-${idx}`}
                  className='rounded-lg bg-[var(--bg-hover)]/40 border border-[var(--border-primary)]/30 px-2.5 py-2'
                >
                  <div className='flex items-center gap-2 mb-1'>
                    <StepIcon type={step.type} success={success} />
                    <span className='text-xs font-semibold text-[var(--text-primary)]'>
                      {STEP_LABEL[step.type]}
                    </span>
                    {step.iteration > 0 && (
                      <span className='text-xs text-[var(--text-muted)] ml-1'>
                        · 轮次 {step.iteration}
                      </span>
                    )}
                  </div>
                  {detail && (
                    <pre className='text-xs text-[var(--text-secondary)] whitespace-pre-wrap break-all font-mono m-0'>
                      {detail}
                    </pre>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
