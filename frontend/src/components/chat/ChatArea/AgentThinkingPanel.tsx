import { useState, useMemo } from 'react'
import {
  ChevronDown,
  ChevronRight,
  CheckCircle,
  XCircle,
  Bell,
  CheckSquare,
  Wrench,
  Sparkles,
  Clock,
} from 'lucide-react'
import type { AgentThinkingStep } from '../../../types'
import { TruncatableText } from '../../ui/TruncatableText'

interface AgentThinkingPanelProps {
  steps: AgentThinkingStep[]
}

/* ─────────────────────────────────────────────── */
/*  Flat timeline types                            */
/*  顶层节点扁平化：Orchestrator 决策 + Skill 块    */
/*  共享一条主竖线，二级子时间线在 Skill 内部        */
/* ─────────────────────────────────────────────── */

type TopLevelItem = OrchestratorDecisionItem | SkillBlock

/** 顶层节点：Orchestrator 的决策点 */
type OrchestratorDecisionItem = {
  kind: 'orchestrator_decision'
  id: string
  title: string
  subtitle?: string
  thinkingText?: string
  model?: string
  hasToolCalls: boolean
  toolNames: string[]
  timestamp: number
}

/** 顶层节点：Skill 块（一个 Skill 的完整生命周期） */
type SkillBlock = {
  kind: 'skill_block'
  id: string
  skillName: string
  skillIcon: React.ReactNode
  inputArgs?: string
  success?: boolean
  durationMs?: number
  errorMessage?: string
  /** Specialist 内部的 ReAct 流程（二级时间线） */
  children: SpecialistNode[]
  timestamp: number
}

/** Specialist 内部节点 */
type SpecialistNode =
  | {
      kind: 'specialist_decision'
      id: string
      title: string
      subtitle?: string
      thinkingText?: string
      model?: string
      hasToolCalls: boolean
      toolNames: string[]
      timestamp: number
    }
  | {
      kind: 'tool_call'
      id: string
      toolName: string
      args: string
      success?: boolean
      result?: string
      errorMessage?: string
      model?: string
      timestamp: number
    }

/* ─────────────────────────────────────────────── */
/*  Build flat timeline from raw steps             */
/* ─────────────────────────────────────────────── */

const SKILL_ICON_MAP: Record<string, React.ReactNode> = {
  reminder: <Bell className='w-3 h-3' />,
  todo: <CheckSquare className='w-3 h-3' />,
}

function getSkillIcon(skillName?: string | null): React.ReactNode {
  if (!skillName) return <Sparkles className='w-3 h-3' />
  const lower = skillName.toLowerCase()
  for (const [key, icon] of Object.entries(SKILL_ICON_MAP)) {
    if (lower.includes(key)) return icon
  }
  return <Sparkles className='w-3 h-3' />
}

function buildTree(steps: AgentThinkingStep[]): {
  items: TopLevelItem[]
  skillCount: number
  toolCount: number
  totalMs: number
} {
  const items: TopLevelItem[] = []
  let skillCount = 0
  let toolCount = 0
  let totalMs = 0

  // Build skillId → skillName map (resolve call_skill_<uuid> names)
  const skillNameMap = new Map<string, string>()
  for (const s of steps) {
    if (s.type === 'skill_enter') {
      const sid = (s.data.skillId as string) ?? s.skillId
      const sname = (s.data.skillName as string) ?? '技能'
      if (sid) skillNameMap.set(sid, sname)
    }
  }

  const resolveToolName = (name: string): string => {
    if (name.startsWith('call_skill_')) {
      const skillId = name.slice('call_skill_'.length)
      return skillNameMap.get(skillId) ?? '技能'
    }
    return name
  }

  // Process steps in chronological order to preserve
  // "Orchestrator → Skill → Orchestrator → Skill → ... → Output" sequence
  const sortedSteps = [...steps].sort((a, b) => {
    if (a.timestamp !== b.timestamp) return a.timestamp - b.timestamp
    return 0
  })

  // Track active skill block being built (by frameId)
  const activeSkillBlocks = new Map<number, { block: SkillBlock; children: SpecialistNode[] }>()

  for (let i = 0; i < sortedSteps.length; i++) {
    const s = sortedSteps[i]
    const fid = s.frameId ?? 0

    if (s.type === 'skill_enter') {
      const skillName = (s.data.skillName as string) ?? '技能'
      const inputArgs = s.data.inputArgs
      let argsStr: string | undefined
      if (inputArgs != null) {
        try {
          argsStr = typeof inputArgs === 'string' ? inputArgs : JSON.stringify(inputArgs)
        } catch {
          argsStr = String(inputArgs)
        }
      }

      const children: SpecialistNode[] = []
      const block: SkillBlock = {
        kind: 'skill_block',
        id: `skill_${fid}_${i}`,
        skillName,
        skillIcon: getSkillIcon(skillName),
        inputArgs: argsStr,
        children,
        timestamp: s.timestamp,
      }
      activeSkillBlocks.set(fid, { block, children })
      skillCount++
    } else if (s.type === 'skill_exit') {
      const active = activeSkillBlocks.get(fid)
      if (active) {
        active.block.success = s.data.success != null ? Boolean(s.data.success) : undefined
        active.block.durationMs = s.data.durationMs as number | undefined
        active.block.errorMessage = s.data.errorMessage as string | undefined
        if (active.block.durationMs) totalMs += active.block.durationMs

        // Flatten: push skill block into top-level items list
        items.push(active.block)
        activeSkillBlocks.delete(fid)
      }
    } else if (s.type === 'llm_call') {
      const active = activeSkillBlocks.get(fid)
      const d = s.data
      const hasToolCalls = Boolean(d.hasToolCalls)
      const toolRequests = (d.toolRequests as Array<{ name: string }>) ?? []

      if (active) {
        // SPECIALIST frame llm_call → nested inside skill block
        const toolNames = toolRequests.map((r) => r.name)
        let title = 'AI 推理'
        let subtitle: string | undefined
        if (hasToolCalls) {
          subtitle = `调用 ${toolNames.join(', ')}`
        }
        active.children.push({
          kind: 'specialist_decision',
          id: `spec_${fid}_${i}`,
          title,
          subtitle,
          thinkingText: (d.text as string) ?? '',
          model: (d.model as string) ?? undefined,
          hasToolCalls,
          toolNames,
          timestamp: s.timestamp,
        })
      } else {
        // ORCHESTRATOR frame llm_call → top-level item
        const toolNames = toolRequests.map((r) => resolveToolName(r.name))

        // 有 tool_calls → AI 分析需求（需要展示决策过程）
        // 无 tool_calls 且是第一条 → AI 分析需求（初始分析）
        // 无 tool_calls 且非第一条 → 最终回复生成，不需要展示（回复本身已在消息气泡中）
        if (!hasToolCalls && items.length > 0) {
          continue
        }

        items.push({
          kind: 'orchestrator_decision',
          id: `orch_${fid}_${i}`,
          title: 'AI 分析需求',
          thinkingText: (d.text as string) ?? '',
          model: (d.model as string) ?? undefined,
          hasToolCalls,
          toolNames,
          timestamp: s.timestamp,
        })
      }
    } else if (s.type === 'tool_detection') {
      // Only process inside SPECIALIST frames
      const active = activeSkillBlocks.get(fid)
      if (!active) continue

      const detData = s.data
      const toolName = (detData.toolName as string) ?? '未知'
      const args = (detData.arguments as string) || '{}'
      const toolCallId = detData.toolCallId as string | undefined

      // Find matching tool_execution (same frameId, same toolCallId, later in time)
      let execution: AgentThinkingStep | undefined
      for (let j = i + 1; j < sortedSteps.length; j++) {
        const candidate = sortedSteps[j]
        if ((candidate.frameId ?? 0) !== fid) continue
        if (candidate.type === 'tool_execution' && candidate.data.toolCallId === toolCallId) {
          execution = candidate
          break
        }
        if (candidate.type === 'tool_detection' && candidate.data.toolCallId !== toolCallId) {
          break
        }
      }

      const exeData = execution?.data
      active.children.push({
        kind: 'tool_call',
        id: `tool_${fid}_${i}_${toolCallId ?? ''}`,
        toolName,
        args,
        success: exeData ? Boolean(exeData.success) : undefined,
        result: exeData?.result != null ? String(exeData.result) : undefined,
        errorMessage: exeData?.errorMessage as string | undefined,
        model: (exeData?.model as string) ?? (detData.model as string) ?? undefined,
        timestamp: s.timestamp,
      })
      toolCount++
    }
    // tool_execution events are consumed by tool_detection matching above
  }

  // Handle any remaining active skill blocks (skill_exit missing)
  for (const [, active] of activeSkillBlocks) {
    items.push(active.block)
  }

  if (totalMs === 0 && items.length > 0) {
    const last = items[items.length - 1]
    const first = items[0]
    totalMs = last.timestamp - first.timestamp
  }

  return { items, skillCount, toolCount, totalMs }
}

/* ─────────────────────────────────────────────── */
/*  Specialist internal node renderers (level 2)   */
/* ─────────────────────────────────────────────── */

function SpecialistDecisionNode({
  node,
}: {
  node: Extract<SpecialistNode, { kind: 'specialist_decision' }>
}) {
  const hasThinking = Boolean(node.thinkingText)

  return (
    <div className='flex gap-2'>
      <div className='flex flex-col items-center flex-shrink-0 pt-1'>
        <div className='w-1.5 h-1.5 rounded-full bg-[var(--accent-purple)]/70' />
        <div className='w-px flex-1 bg-[var(--border-primary)]/30 min-h-[4px]' />
      </div>
      <div className='flex-1 pb-2.5'>
        <div className='flex items-center gap-1.5'>
          <span className='text-xs text-[var(--text-secondary)]'>{node.title}</span>
          {node.model && (
            <span className='text-[11px] text-[var(--text-muted)]'>{node.model}</span>
          )}
        </div>
        {hasThinking && (
          <div className='mt-1'>
            <TruncatableText text={node.thinkingText} maxChars={200} />
          </div>
        )}
        {node.hasToolCalls && node.toolNames.length > 0 && (
          <div className='mt-1 flex flex-wrap gap-x-2 gap-y-0.5'>
            {node.toolNames.map((name, idx) => (
              <span
                key={`${name}-${idx}`}
                className='text-[11px] text-[var(--text-muted)]'
              >
                {name}
              </span>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function ToolCallNode({ node }: { node: Extract<SpecialistNode, { kind: 'tool_call' }> }) {
  const [expanded, setExpanded] = useState(false)

  const success = node.success
  const result = node.result ?? ''

  let resultPreview = ''
  if (result) {
    try {
      const parsed = JSON.parse(result)
      if (parsed.success === true && parsed.id) {
        resultPreview = `ID: ${parsed.id}`
      } else if (typeof parsed === 'string') {
        resultPreview = parsed
      } else if (parsed.message) {
        resultPreview = String(parsed.message)
      } else {
        resultPreview = result.slice(0, 60)
      }
    } catch {
      resultPreview = result.slice(0, 60)
    }
  }

  return (
    <div className='flex gap-2'>
      <div className='flex flex-col items-center flex-shrink-0 pt-1.5'>
        <div
          className={`w-1.5 h-1.5 rounded-full ${
            success === false
              ? 'bg-[var(--accent-rose)]'
              : 'bg-[var(--text-muted)]/50'
          }`}
        />
        <div className='w-px flex-1 bg-[var(--border-primary)]/30 min-h-[4px]' />
      </div>
      <div className='flex-1 pb-2'>
        {/* 工具名行：点击展开参数/完整结果 */}
        <div
          className='flex items-center gap-1.5 cursor-pointer'
          onClick={() => setExpanded(!expanded)}
        >
          <Wrench className='w-3 h-3 text-[var(--text-muted)] flex-shrink-0' />
          <span className='text-xs text-[var(--text-secondary)]'>{node.toolName}</span>
          {success === true && (
            <CheckCircle className='w-3 h-3 text-[var(--text-muted)] flex-shrink-0' />
          )}
          {success === false && (
            <XCircle className='w-3 h-3 text-[var(--accent-rose)] flex-shrink-0' />
          )}
          <ChevronRight
            className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${
              expanded ? 'rotate-90' : ''
            }`}
          />
        </div>
        {/* 结果摘要：默认展示 */}
        {!expanded && resultPreview && (
          <p className='text-[11px] text-[var(--text-muted)] mt-0.5 ml-4.5'>
            → {resultPreview}
          </p>
        )}
        {!expanded && success === false && node.errorMessage && (
          <p className='text-[11px] text-[var(--accent-rose)] mt-0.5 ml-4.5'>
            → {node.errorMessage}
          </p>
        )}
        {/* 展开后：参数 + 完整结果 */}
        {expanded && (
          <div className='mt-1.5 space-y-1.5'>
            <div className='rounded-md bg-[var(--bg-hover)] px-2.5 py-1.5'>
              <span className='text-[11px] text-[var(--text-muted)]'>参数</span>
              <TruncatableText text={node.args} maxChars={150} variant='code' />
            </div>
            {result && (
              <div className='rounded-md bg-[var(--bg-hover)] px-2.5 py-1.5'>
                <span className='text-[11px] text-[var(--text-muted)]'>
                  {success === false ? '失败' : '结果'}
                </span>
                <TruncatableText text={result} maxChars={250} variant='code' />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────── */
/*  Top-level rows (level 1: shared main timeline) */
/* ─────────────────────────────────────────────── */

/** Orchestrator 决策行：紫色小圆点 + 内容，竖线连接到下一个节点 */
function OrchestratorRow({
  node,
  isLast,
}: {
  node: OrchestratorDecisionItem
  isLast: boolean
}) {
  const hasThinking = Boolean(node.thinkingText)

  return (
    <div className='relative pb-4'>
      {/* 紫色小圆点（位于主竖线上） */}
      <div className='absolute left-0 top-1.5 w-2 h-2 rounded-full bg-[var(--accent-purple)] z-10' />
      {/* 主竖线段：从圆点底部延伸到本行底部 */}
      {!isLast && (
        <div className='absolute left-[3px] top-4 bottom-0 w-px bg-[var(--border-primary)]/40' />
      )}
      {/* 内容 */}
      <div className='pl-5'>
        <div className='flex items-center gap-2'>
          <span className='text-sm font-medium text-[var(--text-primary)]'>{node.title}</span>
          {node.model && (
            <span className='text-[11px] text-[var(--text-muted)]'>{node.model}</span>
          )}
        </div>
        {hasThinking && (
          <div className='mt-1.5'>
            <TruncatableText text={node.thinkingText!} maxChars={300} />
          </div>
        )}
      </div>
    </div>
  )
}

/** Skill 调用行：中性小圆点 + 内容，竖线连接到下一个节点，内部含二级子时间线 */
function SkillRow({ block, isLast }: { block: SkillBlock; isLast: boolean }) {
  const [open, setOpen] = useState(true)

  return (
    <div className='relative pb-4'>
      {/* 中性小圆点（位于主竖线上） */}
      <div className='absolute left-0 top-1.5 w-2 h-2 rounded-full bg-[var(--text-muted)]/50 z-10' />
      {/* 主竖线段 */}
      {!isLast && (
        <div className='absolute left-[3px] top-4 bottom-0 w-px bg-[var(--border-primary)]/40' />
      )}
      {/* 内容 */}
      <div className='pl-5'>
        {/* Skill header */}
        <button
          onClick={() => setOpen(!open)}
          className='w-full flex items-center gap-2 cursor-pointer'
        >
          <span className='text-[var(--text-muted)] flex-shrink-0'>{block.skillIcon}</span>
          <span className='text-sm font-medium text-[var(--text-primary)]'>{block.skillName}</span>
          {block.durationMs !== undefined && (
            <span className='text-[11px] text-[var(--text-muted)] flex items-center gap-0.5'>
              <Clock className='w-3 h-3' />
              {(block.durationMs / 1000).toFixed(1)}s
            </span>
          )}
          {block.success === true && (
            <CheckCircle className='w-3 h-3 text-[var(--text-muted)]' />
          )}
          {block.success === false && (
            <XCircle className='w-3 h-3 text-[var(--accent-rose)]' />
          )}
          <ChevronDown
            className={`w-3 h-3 text-[var(--text-muted)] ml-auto transition-transform ${
              open ? 'rotate-180' : ''
            }`}
          />
        </button>

        {/* Input args */}
        {block.inputArgs && (
          <div className='mt-1.5 rounded-md bg-[var(--bg-hover)] px-2.5 py-1.5'>
            <TruncatableText text={block.inputArgs} maxChars={120} variant='code' />
          </div>
        )}

        {/* Error */}
        {block.errorMessage && (
          <p className='text-xs text-[var(--accent-rose)] mt-1'>{block.errorMessage}</p>
        )}

        {/* 二级子时间线：细左边框串联 Skill 内部 ReAct 流程 */}
        {open && block.children.length > 0 && (
          <div className='mt-2 ml-0.5 pl-3 border-l border-[var(--border-primary)]/30'>
            {block.children.map((child) => {
              if (child.kind === 'specialist_decision') {
                return <SpecialistDecisionNode key={child.id} node={child} />
              }
              return <ToolCallNode key={child.id} node={child} />
            })}
          </div>
        )}
      </div>
    </div>
  )
}

/* ─────────────────────────────────────────────── */
/*  Main panel                                     */
/* ─────────────────────────────────────────────── */

export function AgentThinkingPanel({ steps }: AgentThinkingPanelProps) {
  const [expanded, setExpanded] = useState(false)

  const { items, summary } = useMemo(() => {
    const { items, skillCount, toolCount, totalMs } = buildTree(steps)
    return { items, summary: { skillCount, toolCount, totalMs } }
  }, [steps])

  // Smart visibility: don't render if no skill calls AND no tool calls
  if (items.length === 0 || (summary.skillCount === 0 && summary.toolCount === 0)) {
    return null
  }

  const parts: string[] = []
  if (summary.skillCount > 0) parts.push(`${summary.skillCount} 个技能`)
  if (summary.toolCount > 0) parts.push(`${summary.toolCount} 次调用`)
  if (summary.totalMs > 0) {
    parts.push(`${(summary.totalMs / 1000).toFixed(1)}s`)
  }
  const subtitle = parts.join(' · ')

  return (
    <div className='mb-2'>
      <div className='rounded-lg border border-[var(--border-primary)]/40 overflow-hidden'>
        <button
          onClick={() => setExpanded(!expanded)}
          className='w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--bg-hover)] transition-colors'
          aria-expanded={expanded}
        >
          <Sparkles className='w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0' />
          <span className='text-xs font-medium text-[var(--text-secondary)]'>
            AI 工作过程
          </span>
          <span className='text-[11px] text-[var(--text-muted)]'>{subtitle}</span>
          <ChevronDown
            className={`w-3.5 h-3.5 text-[var(--text-muted)] ml-auto transition-transform duration-200 ${
              expanded ? 'rotate-180' : ''
            }`}
          />
        </button>
        {expanded && (
          <div className='px-3 pb-3 pt-1'>
            {items.map((item, idx) => {
              const isLast = idx === items.length - 1
              if (item.kind === 'orchestrator_decision') {
                return <OrchestratorRow key={item.id} node={item} isLast={isLast} />
              }
              return <SkillRow key={item.id} block={item} isLast={isLast} />
            })}
          </div>
        )}
      </div>
    </div>
  )
}
