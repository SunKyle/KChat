import { useState, useEffect, useMemo, useCallback } from 'react'
import { Icon, isIconName, type IconName } from '../common/Icon'
import { skills as skillsApi, tools as toolsApi } from '../../api'
import type { Skill, SkillRequest, CompletionHookType } from '../../api/skill'
import type { ToolInfo } from '../../types'
import { Modal } from '../common/Modal'
import { useToast } from '../../hooks/useToast'

/** 可供技能选择的图标清单（均为 IconMap 中的合法名称） */
const SKILL_ICON_OPTIONS: IconName[] = [
  'Sparkles',
  'Wand2',
  'BookOpen',
  'Brain',
  'Bot',
  'Globe',
  'Database',
  'Search',
  'FileText',
  'Code',
  'Languages',
  'Lightbulb',
  'Network',
  'Wrench',
  'MessageSquare',
  'Image',
  'Star',
  'Cpu',
]

/**
 * 技能中心 —— Skill 管理面板（CRUD）
 *
 * Skill 是能力包：封装 systemPrompt + 工具白名单 + 完成钩子。
 * 创建后用户可在聊天输入框选择激活某个 Skill，整个对话走该 Skill 的 prompt 和工具集。
 */
export function SkillsPanel() {
  const [skillList, setSkillList] = useState<Skill[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [toolList, setToolList] = useState<ToolInfo[]>([])
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [deletingSkill, setDeletingSkill] = useState<Skill | null>(null)
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
    // 加载工具列表供白名单选择
    toolsApi
      .list()
      .then(setToolList)
      .catch((err) => console.error('Failed to load tools for skill panel:', err))
  }, [loadSkills])

  const stats = useMemo(() => {
    const total = skillList.length
    const enabled = skillList.filter((s) => s.isEnabled).length
    const disabled = total - enabled
    const publicCount = skillList.filter((s) => s.isPublic).length
    return { total, enabled, disabled, publicCount }
  }, [skillList])

  const handleCreate = () => {
    setEditingSkill(null)
    setIsFormOpen(true)
  }

  const handleEdit = (skill: Skill) => {
    setEditingSkill(skill)
    setIsFormOpen(true)
  }

  const handleDelete = (skill: Skill) => {
    setDeletingSkill(skill)
  }

  const confirmDelete = async () => {
    if (!deletingSkill) return
    try {
      await skillsApi.delete(deletingSkill.id)
      setSkillList((prev) => prev.filter((s) => s.id !== deletingSkill.id))
      toastSuccess(`技能「${deletingSkill.name}」已删除`)
      setDeletingSkill(null)
    } catch (err) {
      console.error('Failed to delete skill:', err)
      toastError(err instanceof Error ? err.message : '删除失败')
    }
  }

  const toggleEnabled = async (skill: Skill) => {
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

  const handleSubmit = async (data: SkillRequest) => {
    try {
      if (editingSkill) {
        const updated = await skillsApi.update(editingSkill.id, data)
        setSkillList((prev) => prev.map((s) => (s.id === editingSkill.id ? updated : s)))
        toastSuccess(`技能「${updated.name}」已更新`)
      } else {
        const created = await skillsApi.create(data)
        setSkillList((prev) => [created, ...prev])
        toastSuccess(`技能「${created.name}」已创建`)
      }
      setIsFormOpen(false)
      setEditingSkill(null)
    } catch (err) {
      console.error('Failed to save skill:', err)
      toastError(err instanceof Error ? err.message : '保存失败')
    }
  }

  return (
    <div className='space-y-6'>
      {/* 头部：标题 + 统计 + 新建 */}
      <div className='flex items-center justify-between'>
        <div className='flex items-center gap-2'>
          <Icon name='Sparkles' size='lg' className='theme-text-muted' />
          <h3 className='font-semibold theme-text-primary'>技能中心</h3>
          {!loading && !error && (
            <span className='text-xs px-2 py-0.5 rounded-full theme-bg-hover theme-text-secondary'>
              {stats.total} 个技能
              {stats.disabled > 0 && <span className='ml-1 text-amber-500'>· {stats.disabled} 已禁用</span>}
              {stats.publicCount > 0 && <span className='ml-1 text-blue-500'>· {stats.publicCount} 公共</span>}
            </span>
          )}
        </div>
        <div className='flex items-center gap-2'>
          <button onClick={loadSkills} disabled={loading} className='icon-btn disabled:opacity-50' title='刷新'>
            <Icon name='RefreshCw' size='md' className={`theme-text-muted ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={handleCreate}
            className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-[var(--accent-primary)] text-white hover:opacity-90 transition-opacity'
          >
            <Icon name='Plus' size='md' />
            新建技能
          </button>
        </div>
      </div>

      <p className='text-sm theme-text-muted'>
        技能（Skill）是能力包：封装专属 system prompt + 工具白名单 + 完成钩子。
        创建后在聊天输入框选择激活，整段对话走该技能的提示词和工具集。
      </p>

      {loading ? (
        <div className='flex items-center justify-center py-12'>
          <Icon name='Loader2' size='xl' className='theme-text-muted animate-spin' />
        </div>
      ) : error ? (
        <div className='card-float-solid rounded-2xl p-6 text-center'>
          <Icon name='AlertTriangle' size={40} className='text-amber-500 mx-auto mb-3' />
          <p className='font-semibold theme-text-primary mb-1'>加载失败</p>
          <p className='text-sm theme-text-muted mb-4'>{error}</p>
          <button onClick={loadSkills} className='text-sm text-[var(--accent-primary)] hover:underline'>
            重试
          </button>
        </div>
      ) : skillList.length === 0 ? (
        <div className='card-float-solid rounded-2xl p-8 text-center'>
          <Icon name='Sparkles' size={48} className='theme-text-muted mx-auto mb-4' />
          <p className='theme-text-secondary mb-2'>暂无技能</p>
          <p className='text-sm theme-text-muted mb-4'>点击「新建技能」创建你的第一个能力包</p>
          <button
            onClick={handleCreate}
            className='inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium bg-[var(--accent-primary)] text-white hover:opacity-90 transition-opacity'
          >
            <Icon name='Plus' size='md' />
            新建技能
          </button>
        </div>
      ) : (
        <div className='space-y-3'>
          {skillList.map((skill) => (
            <SkillCard
              key={skill.id}
              skill={skill}
              isToggling={togglingIds.has(skill.id)}
              onEdit={() => handleEdit(skill)}
              onDelete={() => handleDelete(skill)}
              onToggleEnabled={() => toggleEnabled(skill)}
            />
          ))}
        </div>
      )}

      {/* 创建/编辑 Modal */}
      <Modal
        isOpen={isFormOpen}
        onClose={() => {
          setIsFormOpen(false)
          setEditingSkill(null)
        }}
        title={editingSkill ? '编辑技能' : '新建技能'}
        size='xl'
        autoHeight
      >
        <SkillForm
          initial={editingSkill}
          toolList={toolList}
          onCancel={() => {
            setIsFormOpen(false)
            setEditingSkill(null)
          }}
          onSubmit={handleSubmit}
        />
      </Modal>

      {/* 删除确认 Modal */}
      <Modal
        isOpen={deletingSkill !== null}
        onClose={() => setDeletingSkill(null)}
        title='删除技能'
        type='danger'
        confirmText='删除'
        onConfirm={confirmDelete}
        message={`确定要删除技能「${deletingSkill?.name ?? ''}」吗？此操作不可恢复。`}
      />
    </div>
  )
}

interface SkillCardProps {
  skill: Skill
  isToggling: boolean
  onEdit: () => void
  onDelete: () => void
  onToggleEnabled: () => void
}

function SkillCard({ skill, isToggling, onEdit, onDelete, onToggleEnabled }: SkillCardProps) {
  const hookLabel = HOOK_TYPE_LABELS[skill.completionHookType] ?? skill.completionHookType
  return (
    <div
      className={`card-float-solid rounded-2xl transition-all overflow-hidden ${
        !skill.isEnabled ? 'opacity-50 hover:opacity-70' : ''
      }`}
    >
      <div className='p-4'>
        <div className='flex items-start gap-3'>
          <div className='w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 theme-bg-hover'>
            <Icon
              name={isIconName(skill.icon) ? skill.icon : 'Sparkles'}
              size='lg'
              className='text-[var(--accent-primary)]'
            />
          </div>
          <div className='flex-1 min-w-0'>
            <div className='flex items-center gap-2 flex-wrap mb-1.5'>
              <span className='font-semibold theme-text-primary text-base'>{skill.name}</span>
              {skill.isPublic && (
                <span className='text-[10px] px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-600 font-semibold'>
                  公共
                </span>
              )}
              {skill.completionHookType !== 'NONE' && (
                <span className='inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full bg-purple-500/10 text-purple-600 font-semibold'>
                  <Icon name='FileText' size='xs' />
                  {hookLabel}
                </span>
              )}
              {skill.maxIterations !== 5 && (
                <span className='text-[10px] px-2 py-0.5 rounded-full theme-bg-hover theme-text-muted font-mono'>
                  maxIter={skill.maxIterations}
                </span>
              )}
            </div>
            {skill.description && (
              <p className='text-sm theme-text-secondary leading-relaxed mb-2 line-clamp-2'>
                {skill.description}
              </p>
            )}
            <div className='flex flex-wrap gap-1.5 text-xs'>
              {skill.allowedToolNames && skill.allowedToolNames.length > 0 ? (
                skill.allowedToolNames.map((t) => (
                  <span
                    key={t}
                    className='inline-flex items-center gap-1 px-2 py-0.5 rounded theme-bg-hover theme-text-secondary font-mono'
                  >
                    <Icon name='Wand2' size='xs' />
                    {t}
                  </span>
                ))
              ) : (
                <span className='theme-text-muted'>无工具白名单（继承全局）</span>
              )}
            </div>
            {skill.triggerKeywords && skill.triggerKeywords.length > 0 && (
              <div className='flex flex-wrap gap-1.5 text-xs mt-1.5'>
                <span className='theme-text-muted'>关键词:</span>
                {skill.triggerKeywords.map((k) => (
                  <span
                    key={k}
                    className='px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-600 font-medium'
                  >
                    {k}
                  </span>
                ))}
              </div>
            )}
          </div>
          <div className='flex-shrink-0 flex items-center gap-1'>
            <button
              onClick={onToggleEnabled}
              disabled={isToggling}
              className={`relative w-11 h-6 rounded-full transition-colors ${
                skill.isEnabled ? 'bg-green-500 hover:bg-green-600' : 'theme-bg-hover hover:bg-red-500/30'
              } ${isToggling ? 'opacity-50 cursor-not-allowed' : ''}`}
              title={skill.isEnabled ? '点击禁用' : '点击启用'}
            >
              <span
                className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-md transition-transform flex items-center justify-center ${
                  skill.isEnabled ? 'translate-x-5' : 'translate-x-0.5'
                }`}
              >
                {isToggling ? (
                  <Icon name='Loader2' size='xs' className='theme-text-muted animate-spin' />
                ) : (
                  <Icon name='Power' size='xs' className={`${skill.isEnabled ? 'text-green-500' : 'theme-text-muted'}`} />
                )}
              </span>
            </button>
            <button onClick={onEdit} className='icon-btn' title='编辑'>
              <Icon name='Pencil' size='md' className='theme-text-muted' />
            </button>
            <button onClick={onDelete} className='icon-btn hover:text-red-500' title='删除'>
              <Icon name='Trash2' size='md' className='theme-text-muted' />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export interface SkillFormProps {
  initial: Skill | null
  toolList: ToolInfo[]
  onCancel: () => void
  onSubmit: (data: SkillRequest) => void
}

export const HOOK_TYPE_LABELS: Record<CompletionHookType, string> = {
  NONE: '无',
  CREATE_NOTE: '写入笔记',
  SCHEDULE_REMINDER: '创建提醒',
  SAVE_TO_KB: '存入知识库',
}

export function SkillForm({ initial, toolList, onCancel, onSubmit }: SkillFormProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [icon, setIcon] = useState<IconName>(isIconName(initial?.icon) ? initial.icon : 'Sparkles')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [systemPromptTemplate, setSystemPromptTemplate] = useState(initial?.systemPromptTemplate ?? '')
  const [systemPromptSupplement, setSystemPromptSupplement] = useState(initial?.systemPromptSupplement ?? '')
  const [allowedToolNames, setAllowedToolNames] = useState<string[]>(initial?.allowedToolNames ?? [])
  const [forbiddenToolNames, setForbiddenToolNames] = useState<string[]>(initial?.forbiddenToolNames ?? [])
  const [triggerKeywords, setTriggerKeywords] = useState<string[]>(initial?.triggerKeywords ?? [])
  const [completionHookType, setCompletionHookType] = useState<CompletionHookType>(
    initial?.completionHookType ?? 'NONE'
  )
  const [maxIterations, setMaxIterations] = useState(initial?.maxIterations ?? 5)
  const [isEnabled, setIsEnabled] = useState(initial?.isEnabled ?? true)
  const [isPublic, setIsPublic] = useState(initial?.isPublic ?? false)
  const [keywordInput, setKeywordInput] = useState('')

  const toggleTool = (toolName: string, list: string[], setList: (l: string[]) => void) => {
    if (list.includes(toolName)) {
      setList(list.filter((t) => t !== toolName))
    } else {
      setList([...list, toolName])
    }
  }

  const addKeyword = () => {
    const k = keywordInput.trim()
    if (k && !triggerKeywords.includes(k)) {
      setTriggerKeywords([...triggerKeywords, k])
    }
    setKeywordInput('')
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!name.trim()) return
    onSubmit({
      name: name.trim(),
      icon,
      description: description.trim() || undefined,
      systemPromptTemplate: systemPromptTemplate.trim() || undefined,
      systemPromptSupplement: systemPromptSupplement.trim() || undefined,
      allowedToolNames: allowedToolNames.length > 0 ? allowedToolNames : undefined,
      forbiddenToolNames: forbiddenToolNames.length > 0 ? forbiddenToolNames : undefined,
      triggerKeywords: triggerKeywords.length > 0 ? triggerKeywords : undefined,
      completionHookType,
      maxIterations,
      isEnabled,
      isPublic,
    })
  }

  return (
    <form onSubmit={handleSubmit} className='space-y-4'>
      {/* 基本信息 */}
      <div className='grid grid-cols-2 gap-3'>
        <div>
          <label className='block text-xs font-semibold theme-text-muted mb-1.5'>
            名称 <span className='text-red-500'>*</span>
          </label>
          <input
            type='text'
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            maxLength={100}
            autoFocus
            className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30'
            placeholder='如：周报生成器'
          />
        </div>
        <div>
          <label className='block text-xs font-semibold theme-text-muted mb-1.5'>最大迭代次数</label>
          <input
            type='number'
            value={maxIterations}
            onChange={(e) => setMaxIterations(Number(e.target.value) || 5)}
            min={1}
            max={20}
            className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30'
          />
        </div>
      </div>

      {/* 图标选择 */}
      <div>
        <label className='block text-xs font-semibold theme-text-muted mb-1.5'>图标</label>
        <div className='flex flex-wrap gap-1.5'>
          {SKILL_ICON_OPTIONS.map((option) => (
            <button
              key={option}
              type='button'
              onClick={() => setIcon(option)}
              aria-label={option}
              className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors ${
                icon === option
                  ? 'bg-[var(--accent-primary)] text-white'
                  : 'theme-bg-hover theme-text-secondary hover:theme-bg-card'
              }`}
            >
              <Icon name={option} size='md' />
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className='block text-xs font-semibold theme-text-muted mb-1.5'>描述</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={1000}
          rows={2}
          className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30 resize-y'
          placeholder='技能的用途说明，会展示在技能列表和 LLM 路由参考'
        />
      </div>

      {/* System Prompt */}
      <div>
        <label className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted mb-1.5'>
          <Icon name='Wand2' size='sm' />
          专属 System Prompt 模板
        </label>
        <textarea
          value={systemPromptTemplate}
          onChange={(e) => setSystemPromptTemplate(e.target.value)}
          maxLength={8000}
          rows={4}
          className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30 resize-y font-mono'
          placeholder='非空时完全覆盖默认 system prompt。支持变量：{user_profile} {memory_cognee_graph} {search_context} {context_policy}'
        />
        <p className='text-xs theme-text-muted mt-1'>
          为空时使用默认 prompt + 下方的补充指令
        </p>
      </div>

      <div>
        <label className='block text-xs font-semibold theme-text-muted mb-1.5'>补充指令（追加到默认 prompt 末尾）</label>
        <textarea
          value={systemPromptSupplement}
          onChange={(e) => setSystemPromptSupplement(e.target.value)}
          maxLength={4000}
          rows={2}
          className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30 resize-y'
          placeholder='当上方模板为空时生效，作为默认 prompt 的补充'
        />
      </div>

      {/* 工具白名单 */}
      <div>
        <label className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted mb-1.5'>
          <Icon name='Shield' size='sm' />
          工具白名单（为空表示继承全局可用工具）
        </label>
        {toolList.length === 0 ? (
          <p className='text-xs theme-text-muted'>暂无可用工具</p>
        ) : (
          <div className='flex flex-wrap gap-1.5 max-h-32 overflow-y-auto p-2 rounded-lg theme-bg-hover'>
            {toolList.map((tool) => {
              const isSelected = allowedToolNames.includes(tool.name)
              const isForbidden = forbiddenToolNames.includes(tool.name)
              return (
                <button
                  key={tool.name}
                  type='button'
                  onClick={() => toggleTool(tool.name, allowedToolNames, setAllowedToolNames)}
                  onContextMenu={(e) => {
                    e.preventDefault()
                    toggleTool(tool.name, forbiddenToolNames, setForbiddenToolNames)
                  }}
                  className={`px-2 py-1 rounded-md text-xs font-mono transition-colors ${
                    isForbidden
                      ? 'bg-red-500/15 text-red-600 line-through'
                      : isSelected
                      ? 'bg-[var(--accent-primary)] text-white'
                      : 'theme-bg-card theme-text-secondary hover:theme-bg-hover'
                  }`}
                  title={isForbidden ? '黑名单（右键移除）' : isSelected ? '白名单已选（右键加入黑名单）' : '点击加入白名单'}
                >
                  {tool.name}
                </button>
              )
            })}
          </div>
        )}
        <p className='text-xs theme-text-muted mt-1'>
          左键点击：加入/移除白名单；右键点击：加入/移除黑名单
        </p>
        {(allowedToolNames.length > 0 || forbiddenToolNames.length > 0) && (
          <div className='flex flex-wrap gap-2 mt-2 text-xs'>
            {allowedToolNames.length > 0 && (
              <span className='theme-text-secondary'>
                白名单 {allowedToolNames.length} 个
              </span>
            )}
            {forbiddenToolNames.length > 0 && (
              <span className='text-red-600'>黑名单 {forbiddenToolNames.length} 个</span>
            )}
          </div>
        )}
      </div>

      {/* 触发关键词 */}
      <div>
        <label className='block text-xs font-semibold theme-text-muted mb-1.5'>触发关键词</label>
        <div className='flex gap-2 mb-2'>
          <input
            type='text'
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                addKeyword()
              }
            }}
            className='flex-1 text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30'
            placeholder='输入关键词后回车添加（用户消息命中后自动激活该技能）'
          />
          <button
            type='button'
            onClick={addKeyword}
            className='px-3 py-2 rounded-lg theme-bg-hover theme-text-secondary text-sm hover:theme-bg-hover'
          >
            <Icon name='Plus' size='md' />
          </button>
        </div>
        {triggerKeywords.length > 0 && (
          <div className='flex flex-wrap gap-1.5'>
            {triggerKeywords.map((k) => (
              <span
                key={k}
                className='inline-flex items-center gap-1 px-2 py-1 rounded-md bg-amber-500/10 text-amber-600 text-xs font-medium'
              >
                {k}
                <button
                  type='button'
                  onClick={() => setTriggerKeywords(triggerKeywords.filter((x) => x !== k))}
                  className='hover:bg-amber-500/20 rounded'
                >
                  <Icon name='X' size='xs' />
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* 完成钩子 */}
      <div>
        <label className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted mb-1.5'>
          <Icon name='FileText' size='sm' />
          完成钩子（Skill 执行完后触发的副作用）
        </label>
        <select
          value={completionHookType}
          onChange={(e) => setCompletionHookType(e.target.value as CompletionHookType)}
          className='w-full text-sm rounded-lg theme-border-primary theme-bg-input px-3 py-2 theme-text-primary focus:outline-none focus:ring-2 focus:ring-[var(--accent-primary)]/30'
        >
          {Object.entries(HOOK_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
              {value === 'CREATE_NOTE' && '（把 LLM 输出写入笔记）'}
              {value === 'SCHEDULE_REMINDER' && '（MVP 暂未实现）'}
              {value === 'SAVE_TO_KB' && '（MVP 暂未实现）'}
            </option>
          ))}
        </select>
      </div>

      {/* 开关 */}
      <div className='flex items-center gap-6 pt-2'>
        <label className='flex items-center gap-2 cursor-pointer'>
          <input
            type='checkbox'
            checked={isEnabled}
            onChange={(e) => setIsEnabled(e.target.checked)}
            className='w-4 h-4 rounded'
          />
          <span className='text-sm theme-text-secondary'>启用</span>
        </label>
        <label className='flex items-center gap-2 cursor-pointer'>
          <input
            type='checkbox'
            checked={isPublic}
            onChange={(e) => setIsPublic(e.target.checked)}
            className='w-4 h-4 rounded'
          />
          <span className='text-sm theme-text-secondary'>公共技能（所有用户可见）</span>
        </label>
      </div>

      {/* 操作按钮 */}
      <div className='flex items-center justify-end gap-3 pt-4 border-t theme-border-primary'>
        <button
          type='button'
          onClick={onCancel}
          className='px-4 py-2 rounded-lg text-sm theme-text-secondary hover:theme-bg-hover transition-colors'
        >
          取消
        </button>
        <button
          type='submit'
          disabled={!name.trim()}
          className='flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium bg-[var(--accent-primary)] text-white hover:opacity-90 transition-opacity disabled:opacity-50'
        >
          <Icon name='Save' size='md' />
          保存
        </button>
      </div>
    </form>
  )
}

/**
 * Skill 实体转 SkillRequest（用于更新接口）
 */
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
