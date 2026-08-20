import { useState, useEffect, useCallback } from 'react'
import { Sparkles, Plus, RefreshCw, Loader2, AlertTriangle } from 'lucide-react'
import { skills as skillsApi } from '../../api'
import type { Skill, SkillRequest } from '../../api/skill'
import { useToast } from '../../hooks/useToast'

interface SkillsPanelProps {
  onToggle?: () => void
  selectedSkillId?: string | null
  onSelectSkill?: (id: string) => void
  onCreateSkill?: () => void
}

export function SkillsPanel({
  onToggle,
  selectedSkillId,
  onSelectSkill,
  onCreateSkill,
}: SkillsPanelProps) {
  const [skillList, setSkillList] = useState<Skill[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [togglingIds, setTogglingIds] = useState<Set<string>>(new Set())
  const { success: toastSuccess, error: toastError } = useToast()

  const loadSkills = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await skillsApi.list()
      setSkillList(data)
    } catch (err) {
      console.error('Failed to load skills:', err)
      setError(err instanceof Error ? err.message : '加载技能列表失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadSkills()
  }, [loadSkills])

  const toggleEnabled = async (e: React.MouseEvent, skill: Skill) => {
    e.stopPropagation()
    setTogglingIds((prev) => new Set(prev).add(skill.id))
    try {
      const updateData: SkillRequest = toRequest(skill)
      updateData.isEnabled = !skill.isEnabled
      const updated = await skillsApi.update(skill.id, updateData)
      setSkillList((prev) => prev.map((s) => (s.id === skill.id ? updated : s)))
      toastSuccess(`技能「${skill.name}」已${updated.isEnabled ? '启用' : '禁用'}`)
    } catch (err) {
      console.error('Failed to toggle skill:', err)
      toastError(err instanceof Error ? err.message : '操作失败')
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev)
        next.delete(skill.id)
        return next
      })
    }
  }

  return (
    <div className='flex flex-col h-full bg-[var(--bg-sidebar)] breath-divider-r overflow-hidden'>
      {/* Header */}
      <div className='h-14 flex items-center justify-between px-4 flex-shrink-0 breath-divider-b'>
        <div className='flex items-center gap-2'>
          <span className='font-semibold theme-text-primary text-sm'>技能库</span>
          {!loading && !error && (
            <span className='text-[10px] px-1.5 py-0.5 rounded-full theme-bg-hover theme-text-muted font-medium flex-shrink-0'>
              {skillList.length}
            </span>
          )}
        </div>
        <div className='flex items-center gap-1'>
          <button
            onClick={loadSkills}
            disabled={loading}
            className='icon-btn disabled:opacity-50'
            title='刷新'
          >
            <RefreshCw className={`w-3.5 h-3.5 theme-text-muted ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => onCreateSkill?.()}
            className='flex items-center gap-1 px-2 py-1 rounded-lg text-xs font-medium bg-[var(--accent-primary)] text-white hover:opacity-90 transition-opacity'
            title='新建技能'
          >
            <Plus className='w-3 h-3' />
            新建
          </button>
        </div>
      </div>

      {/* 列表 */}
      <div className='flex-1 overflow-y-auto py-1'>
        {loading ? (
          <div className='flex items-center justify-center py-12'>
            <Loader2 className='w-5 h-5 theme-text-muted animate-spin' />
          </div>
        ) : error ? (
          <div className='px-4 py-8 text-center'>
            <AlertTriangle className='w-8 h-8 text-amber-500 mx-auto mb-2' />
            <p className='text-xs theme-text-muted mb-2'>{error}</p>
            <button onClick={loadSkills} className='text-xs text-[var(--accent-primary)] hover:underline'>
              重试
            </button>
          </div>
        ) : skillList.length === 0 ? (
          <div className='px-4 py-8 text-center'>
            <Sparkles className='w-8 h-8 theme-text-muted mx-auto mb-2' />
            <p className='text-xs theme-text-muted'>暂无技能</p>
          </div>
        ) : (
          <div className='px-2 space-y-0.5'>
            {skillList.map((skill) => {
              const isSelected = selectedSkillId === skill.id
              return (
                <button
                  key={skill.id}
                  onClick={() => onSelectSkill?.(skill.id)}
                  className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg transition-colors text-left ${
                    isSelected
                      ? 'bg-brand-selected theme-text-primary'
                      : 'hover:theme-bg-hover theme-text-secondary'
                  } ${!skill.isEnabled ? 'opacity-50' : ''}`}
                >
                  {/* 名称 + 描述 */}
                  <div className='flex-1 min-w-0'>
                    <div className='flex items-center gap-1.5'>
                      <span className='text-sm font-medium truncate'>{skill.name}</span>
                      {skill.isPublic && (
                        <span className='text-[9px] px-1 py-0.5 rounded-full bg-blue-500/10 text-blue-600 font-medium flex-shrink-0'>
                          公共
                        </span>
                      )}
                    </div>
                    {skill.allowedToolNames && skill.allowedToolNames.length > 0 && (
                      <p className='text-[10px] theme-text-muted truncate'>
                        {skill.allowedToolNames.length} 个工具
                      </p>
                    )}
                  </div>
                  {/* 开关 */}
                  <span
                    role='button'
                    tabIndex={0}
                    onClick={(e) => toggleEnabled(e, skill)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault()
                        e.stopPropagation()
                        toggleEnabled(e as unknown as React.MouseEvent, skill)
                      }
                    }}
                    className={`relative w-9 h-5 rounded-full transition-colors flex-shrink-0 ${
                      skill.isEnabled ? 'bg-green-500' : 'theme-bg-hover'
                    } ${togglingIds.has(skill.id) ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                  >
                    <span
                      className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow-sm transition-transform flex items-center justify-center ${
                        skill.isEnabled ? 'translate-x-4' : 'translate-x-0.5'
                      }`}
                    >
                      {togglingIds.has(skill.id) && (
                        <Loader2 className='w-2.5 h-2.5 theme-text-muted animate-spin' />
                      )}
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

/** Skill 实体转 SkillRequest */
function toRequest(skill: Skill): SkillRequest {
  return {
    name: skill.name,
    description: skill.description,
    icon: skill.icon,
    systemPromptTemplate: skill.systemPromptTemplate,
    systemPromptSupplement: skill.systemPromptSupplement,
    allowedToolNames: skill.allowedToolNames,
    forbiddenToolNames: skill.forbiddenToolNames,
    triggerKeywords: skill.triggerKeywords,
    triggerIntentTypes: skill.triggerIntentTypes,
    inputSchemaJson: skill.inputSchemaJson,
    outputSchemaJson: skill.outputSchemaJson,
    completionHookType: skill.completionHookType,
    completionHookParamsJson: skill.completionHookParamsJson,
    maxIterations: skill.maxIterations,
    isEnabled: skill.isEnabled,
    isPublic: skill.isPublic,
  }
}

export default SkillsPanel
