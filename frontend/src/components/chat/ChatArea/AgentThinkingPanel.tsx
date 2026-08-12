import { useState } from 'react'
import {
  ChevronDown,
  Wrench,
  Sparkles,
  CheckCircle,
  XCircle,
  ArrowRight,
  Brain,
  Settings,
  MessageSquare,
} from 'lucide-react'
import type { AgentThinkingStep } from '../../../types'

interface AgentThinkingPanelProps {
  steps: AgentThinkingStep[]
}

type RenderStep =
  | { kind: 'llm_call'; step: AgentThinkingStep }
  | {
      kind: 'merged'
      iteration: number
      timestamp: number
      detection: AgentThinkingStep
      execution?: AgentThinkingStep
      assembly?: AgentThinkingStep
    }

function buildRenderSteps(steps: AgentThinkingStep[]): RenderStep[] {
  const result: RenderStep[] = []
  const len = steps.length

  // Pre-index: for each iteration, find the tool_assembly step (shared across all tool calls)
  const assemblyByIteration = new Map<number, AgentThinkingStep>()
  for (let k = 0; k < len; k++) {
    if (steps[k].type === 'tool_assembly') {
      assemblyByIteration.set(steps[k].iteration, steps[k])
    }
  }

  let i = 0
  while (i < len) {
    const s = steps[i]
    if (s.type === 'tool_definition') {
      i++
    } else if (s.type === 'llm_call') {
      result.push({ kind: 'llm_call', step: s })
      i++
    } else if (s.type === 'tool_detection') {
      const detection = s
      const toolCallId = detection.data.toolCallId

      // Search forward for matching tool_execution by toolCallId + iteration
      let execution: AgentThinkingStep | undefined
      for (let j = i + 1; j < len; j++) {
        const candidate = steps[j]
        if (candidate.type === 'tool_detection' && j > i + 1) {
          // If we hit another detection (not the immediate next), keep searching
          // but don't match across iterations
          if (candidate.iteration !== detection.iteration) break
          continue
        }
        if (
          candidate.type === 'tool_execution' &&
          candidate.iteration === detection.iteration &&
          candidate.data.toolCallId === toolCallId
        ) {
          execution = candidate
          break
        }
        // Skip llm_call or other types between detection and execution
      }

      // Find assembly for this iteration (shared across all tool calls)
      const assembly = assemblyByIteration.get(detection.iteration)

      result.push({
        kind: 'merged',
        iteration: detection.iteration,
        timestamp: detection.timestamp,
        detection,
        execution,
        assembly,
      })
      i++
    } else {
      i++
    }
  }
  return result
}

/* ─────────────────────────────────────────────── */
/*  Shared UI helpers                              */
/* ─────────────────────────────────────────────── */

function CardShell({
  children,
  borderColor = 'purple',
}: {
  children: React.ReactNode
  borderColor?: 'purple' | 'emerald' | 'rose'
}) {
  const borderMap = {
    purple: 'border-[var(--accent-purple)]/20',
    emerald: 'border-[var(--accent-emerald)]/20',
    rose: 'border-[var(--accent-rose)]/20',
  }
  return (
    <div
      className={`rounded-xl border ${borderMap[borderColor]} bg-[var(--bg-hover)]/40 overflow-hidden`}
    >
      {children}
    </div>
  )
}

function CardHeader({
  icon,
  title,
  iteration,
  rightBadge,
  iconColor = 'purple',
}: {
  icon: React.ReactNode
  title: string
  iteration?: number
  rightBadge?: React.ReactNode
  iconColor?: 'purple' | 'emerald' | 'rose'
}) {
  const iconColorMap = {
    purple: 'text-[var(--accent-purple)]',
    emerald: 'text-[var(--accent-emerald)]',
    rose: 'text-[var(--accent-rose)]',
  }
  return (
    <div className='flex items-center gap-2 px-3 py-2 bg-[var(--accent-purple)]/5 border-b border-[var(--border-primary)]/20'>
      <span className={`flex-shrink-0 ${iconColorMap[iconColor]}`}>{icon}</span>
      <span className='text-xs font-semibold text-[var(--text-primary)]'>{title}</span>
      {iteration !== undefined && iteration > 0 && (
        <span className='text-[10px] px-1.5 py-0.5 rounded bg-[var(--accent-purple)]/10 text-[var(--accent-purple)] font-medium'>
          轮次 {iteration}
        </span>
      )}
      {rightBadge && <div className='ml-auto'>{rightBadge}</div>}
    </div>
  )
}

function SectionLabel({
  icon,
  label,
  count,
}: {
  icon: React.ReactNode
  label: string
  count?: number
}) {
  return (
    <div className='flex items-center gap-1.5 mb-1.5'>
      <span className='text-[var(--accent-purple)]/70 flex-shrink-0'>{icon}</span>
      <span className='text-[10px] font-semibold text-[var(--accent-purple)] uppercase tracking-wide'>
        {label}
      </span>
      {count !== undefined && (
        <span className='text-[10px] text-[var(--text-muted)] ml-0.5'>{count}</span>
      )}
    </div>
  )
}

function MetaItem({
  label,
  value,
  highlight,
}: {
  label: string
  value: React.ReactNode
  highlight?: 'amber'
}) {
  return (
    <span className='flex items-center gap-1'>
      <span className='text-[var(--text-muted)]/70'>{label}</span>
      <span
        className={`font-medium ${
          highlight === 'amber' ? 'text-[var(--accent-amber)]' : 'text-[var(--text-secondary)]'
        }`}
      >
        {value}
      </span>
    </span>
  )
}

/* ─────────────────────────────────────────────── */
/*  LlmCallCard                                   */
/* ─────────────────────────────────────────────── */

function LlmCallCard({ step }: { step: AgentThinkingStep }) {
  const d = step.data
  const model = (d.model as string) ?? '未知'
  const thinkingText = (d.text as string) ?? ''
  const toolRequests =
    (d.toolRequests as Array<{ name: string; arguments: string; id: string }>) ?? []
  const hasToolCalls = Boolean(d.hasToolCalls)
  const executedTools = (d.executedToolNames as string[]) || []
  const inputPreview = d.inputPreview as string | undefined
  const tokenCount = d.tokenCount as number | undefined
  const truncated = Boolean(d.truncated)

  return (
    <CardShell borderColor='purple'>
      <CardHeader
        icon={<Brain className='w-3.5 h-3.5' />}
        title='LLM 思考'
        iteration={step.iteration}
        rightBadge={
          <span className='text-[10px] px-1.5 py-0.5 rounded bg-[var(--bg-hover)] text-[var(--text-muted)] font-medium'>
            {model}
          </span>
        }
      />
      <div className='px-3 py-2 space-y-2.5'>
        {/* Zone: Input context */}
        <div>
          <SectionLabel icon={<MessageSquare className='w-3 h-3' />} label='输入上下文' />
          <div className='rounded-lg bg-[var(--bg-hover)] border border-[var(--border-primary)]/20 px-2.5 py-2'>
            {inputPreview && (
              <p className='text-xs text-[var(--text-secondary)] whitespace-pre-wrap leading-relaxed m-0'>
                {inputPreview}
              </p>
            )}
          </div>
          <div className='flex flex-wrap gap-1.5 mt-1.5'>
            <span
              className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-[10px] ${
                truncated
                  ? 'bg-[var(--accent-amber)]/10 border-[var(--accent-amber)]/30 text-[var(--accent-amber)]'
                  : 'bg-[var(--bg-hover)] border-[var(--border-primary)]/20 text-[var(--text-muted)]'
              }`}
            >
              Token {tokenCount ?? 0} · {truncated ? '已截断' : '未截断'}
            </span>
            {executedTools.length > 0 && (
              <span className='inline-flex items-center rounded-md bg-[var(--bg-hover)] border border-[var(--accent-purple)]/20 px-2 py-0.5 text-[10px] text-[var(--accent-purple)]'>
                已执行 {executedTools.join(', ')}
              </span>
            )}
          </div>
        </div>

        {/* Zone: Thinking text */}
        {thinkingText && (
          <div>
            <SectionLabel icon={<Sparkles className='w-3 h-3' />} label='思考过程' />
            <div className='rounded-lg bg-[var(--bg-hover)] border border-[var(--accent-purple)]/20 px-2.5 py-2'>
              <p className='text-xs text-[var(--text-secondary)] whitespace-pre-wrap leading-relaxed m-0'>
                {thinkingText}
              </p>
            </div>
          </div>
        )}

        {/* Zone: Tool plan */}
        {hasToolCalls && toolRequests.length > 0 && (
          <div>
            <SectionLabel
              icon={<Wrench className='w-3 h-3' />}
              label='计划调用'
              count={toolRequests.length}
            />
            <div className='flex flex-wrap gap-1.5'>
              {toolRequests.map((req, idx) => (
                <span
                  key={`${req.id}-${idx}`}
                  className='inline-flex items-center gap-1 rounded-md bg-[var(--bg-hover)] border border-[var(--accent-purple)]/20 px-2 py-1'
                >
                  <span className='text-[11px] font-medium text-[var(--text-secondary)]'>
                    {req.name}
                  </span>
                </span>
              ))}
            </div>
          </div>
        )}

        {/* Status zone (no tool calls → final) */}
        {!hasToolCalls && (
          <div className='flex items-center gap-2 rounded-lg bg-[var(--bg-hover)] border border-[var(--accent-emerald)]/30 px-2.5 py-2'>
            <CheckCircle className='w-3.5 h-3.5 text-[var(--accent-emerald)] flex-shrink-0' />
            <span className='text-xs font-medium text-[var(--accent-emerald)]'>
              无工具调用 · 本轮即为最终回复
            </span>
          </div>
        )}
      </div>
    </CardShell>
  )
}

/* ─────────────────────────────────────────────── */
/*  MergedToolCard                                */
/* ─────────────────────────────────────────────── */

function MergedToolCard({ rs }: { rs: Extract<RenderStep, { kind: 'merged' }> }) {
  const { detection, execution } = rs
  const detData = detection.data
  const exeData = execution?.data

  const toolName = (detData.toolName as string) ?? '未知'
  const requestedModel = detData.model as string | undefined
  const actualModel = exeData?.model as string | undefined
  const args = (detData.arguments as string) || '{}'
  const success = exeData ? Boolean(exeData.success) : undefined
  const errorMessage = exeData?.errorMessage as string | undefined
  const result = exeData?.result == null ? '' : String(exeData.result)

  const headerIcon =
    success === true ? (
      <CheckCircle className='w-3.5 h-3.5' />
    ) : success === false ? (
      <XCircle className='w-3.5 h-3.5' />
    ) : (
      <div className='w-3.5 h-3.5 rounded-full border-2 border-[var(--accent-purple)]/50 border-t-[var(--accent-purple)] animate-spin' />
    )

  const headerColor: 'emerald' | 'rose' | 'purple' =
    success === true ? 'emerald' : success === false ? 'rose' : 'purple'

  const modelBadge = actualModel ?? requestedModel
  const hasResult = execution !== undefined

  return (
    <CardShell
      borderColor={
        headerColor === 'emerald' ? 'emerald' : headerColor === 'rose' ? 'rose' : 'purple'
      }
    >
      <CardHeader
        icon={headerIcon}
        title={`工具调用 · ${toolName}`}
        iteration={rs.iteration}
        iconColor={headerColor}
        rightBadge={
          modelBadge ? (
            <span className='text-[10px] px-1.5 py-0.5 rounded bg-[var(--bg-hover)] text-[var(--text-muted)] font-medium'>
              {modelBadge}
            </span>
          ) : undefined
        }
      />
      <div className='px-3 py-2 space-y-2.5'>
        {/* Zone: Parameters */}
        <div>
          <SectionLabel icon={<Settings className='w-3 h-3' />} label='调用参数' />
          <div className='rounded-lg bg-[var(--bg-hover)] border border-[var(--border-primary)]/20 px-2.5 py-2'>
            <pre className='text-xs text-[var(--text-secondary)] whitespace-pre-wrap break-all font-mono m-0 leading-relaxed'>
              {args}
            </pre>
          </div>
        </div>

        {/* Zone: Execution result */}
        {hasResult && (
          <div>
            <SectionLabel
              icon={
                success ? (
                  <CheckCircle className='w-3 h-3 text-[var(--accent-emerald)]' />
                ) : (
                  <XCircle className='w-3 h-3 text-[var(--accent-rose)]' />
                )
              }
              label={success ? '执行结果' : `执行失败 · ${errorMessage ?? '未知错误'}`}
            />
            <div
              className={`rounded-lg bg-[var(--bg-hover)] border px-2.5 py-2 ${
                success ? 'border-[var(--accent-emerald)]/30' : 'border-[var(--accent-rose)]/30'
              }`}
            >
              <pre className='text-xs text-[var(--text-secondary)] whitespace-pre-wrap break-all font-mono m-0 leading-relaxed'>
                {result || '—'}
              </pre>
            </div>
          </div>
        )}
      </div>
    </CardShell>
  )
}

/* ─────────────────────────────────────────────── */
/*  AgentThinkingPanel (root)                      */
/* ─────────────────────────────────────────────── */

export function AgentThinkingPanel({ steps }: AgentThinkingPanelProps) {
  const [expanded, setExpanded] = useState(false)

  if (!steps || steps.length === 0) return null

  const renderSteps = buildRenderSteps(steps.filter((s) => s.type !== 'final_response'))

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
            思考过程 · {renderSteps.length} 步
          </span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-[var(--accent-purple)]/60 ml-auto transition-transform duration-200 ${
              expanded ? 'rotate-180' : ''
            }`}
          />
        </button>
        {expanded && (
          <div className='px-3 pb-3 pt-2 space-y-2'>
            {renderSteps.map((rs, idx) => {
              if (rs.kind === 'llm_call') {
                return <LlmCallCard key={`llm-${idx}`} step={rs.step} />
              }
              return <MergedToolCard key={`merged-${idx}`} rs={rs} />
            })}
          </div>
        )}
      </div>
    </div>
  )
}
