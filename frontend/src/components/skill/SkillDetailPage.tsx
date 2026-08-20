import { useState, useEffect, useCallback } from 'react'
import {
  Sparkles,
  Loader2,
  AlertTriangle,
  Pencil,
  Trash2,
  Wand2,
  Shield,
  FileText,
  Plus,
  Save,
  X,
  Power,
} from 'lucide-react'
import { skills as skillsApi, tools as toolsApi } from '../../api'
import type { Skill, SkillRequest } from '../../api/skill'
import type { ToolInfo } from '../../types'
import { SkillForm, HOOK_TYPE_LABELS } from '../settings/SkillsPanel'
import { Modal } from '../common/Modal'
import { useToast } from '../../hooks/useToast'

interface SkillDetailPageProps {
  skillId: string | null
  onCreateNew?: () => void
  onDeleted?: (id: string) => void
  onSaved?: () => void
}

export function SkillDetailPage({ skillId, onCreateNew, onDeleted, onSaved }: SkillDetailPageProps) {
  const [skill, setSkill] = useState<Skill | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [toolList, setToolList] = useState<ToolInfo[]>([])
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [isCreateMode, setIsCreateMode] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const { success: toastSuccess, error: toastError } = useToast()

  // 加载工具列表
  useEffect(() => {
    toolsApi
      .list()
      .then(setToolList)
      .catch((err) => console.error('Failed to load tools:', err))
  }, [])

  const loadSkill = useCallback(async () => {
    if (!skillId) {
      setSkill(null)
      return
    }
    setLoading(true)
    setError(null)
    try {
      // 从 list 中查找，避免单独请求
      const list = await skillsApi.list()
      const found = list.find((s) => s.id === skillId)
      if (found) {
        setSkill(found)
      } else {
        setError('技能不存在')
      }
    } catch (err) {
      console.error('Failed to load skill:', err)
      setError(err instanceof Error ? err.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [skillId])

  useEffect(() => {
    loadSkill()
  }, [loadSkill])

  const handleDelete = async () => {
    if (!skill) return
    setDeleting(true)
    try {
      await skillsApi.delete(skill.id)
      toastSuccess(`技能「${skill.name}」已删除`)
      onDeleted?.(skill.id)
    } catch (err) {
      console.error('Failed to delete skill:', err)
      toastError(err instanceof Error ? err.message : '删除失败')
    } finally {
      setDeleting(false)
    }
  }

  const handleSubmit = async (data: SkillRequest) => {
    if (!skill) return
    try {
      const updated = await skillsApi.update(skill.id, data)
      setSkill(updated)
      toastSuccess(`技能「${updated.name}」已更新`)
      setIsFormOpen(false)
      onSaved?.()
    } catch (err) {
      console.error('Failed to save skill:', err)
      toastError(err instanceof Error ? err.message : '保存失败')
    }
  }

  const handleCreate = async (data: SkillRequest) => {
    try {
      const created = await skillsApi.create(data)
      toastSuccess(`技能「${created.name}」已创建`)
      setIsCreateMode(false)
      onSaved?.()
    } catch (err) {
      console.error('Failed to create skill:', err)
      toastError(err instanceof Error ? err.message : '创建失败')
    }
  }

  // 空状态
  if (!skillId && !isCreateMode) {
    return (
      <>
        <div className='relative flex-1 min-h-0 flex items-center justify-center'>
          <div className='flex flex-col items-center text-center px-4'>
            <Sparkles className='w-10 h-10 theme-text-muted mb-3' />
            <p className='text-sm theme-text-secondary font-medium mb-1'>选择一个技能查看详情</p>
            <p className='text-xs theme-text-muted mb-4'>或创建新技能</p>
            <button
              onClick={() => setIsCreateMode(true)}
              className='flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium bg-[var(--accent-primary)] text-white hover:opacity-90 transition-opacity'
            >
              <Plus className='w-4 h-4' />
              新建技能
            </button>
          </div>
        </div>
        {/* 创建 Modal */}
        <Modal
          isOpen={isCreateMode}
          onClose={() => setIsCreateMode(false)}
          title='新建技能'
          size='xl'
          autoHeight
        >
          <SkillForm
            initial={null}
            toolList={toolList}
            onCancel={() => setIsCreateMode(false)}
            onSubmit={handleCreate}
          />
        </Modal>
      </>
    )
  }

  // 创建模式
  if (isCreateMode) {
    return (
      <Modal
        isOpen={isCreateMode}
        onClose={() => setIsCreateMode(false)}
        title='新建技能'
        size='xl'
        autoHeight
      >
        <SkillForm
          initial={null}
          toolList={toolList}
          onCancel={() => setIsCreateMode(false)}
          onSubmit={handleCreate}
        />
      </Modal>
    )
  }

  if (loading) {
    return (
      <div className='relative flex-1 min-h-0 flex items-center justify-center'>
        <Loader2 className='w-6 h-6 theme-text-muted animate-spin' />
      </div>
    )
  }

  if (error || !skill) {
    return (
      <div className='relative flex-1 min-h-0 flex items-center justify-center'>
        <div className='flex flex-col items-center text-center px-4'>
          <AlertTriangle className='w-10 h-10 text-amber-500 mx-auto mb-3' />
          <p className='text-sm theme-text-secondary mb-1'>{error ?? '加载失败'}</p>
          <button onClick={loadSkill} className='text-sm text-[var(--accent-primary)] hover:underline'>
            重试
          </button>
        </div>
      </div>
    )
  }

  const hookLabel = HOOK_TYPE_LABELS[skill.completionHookType] ?? skill.completionHookType

  return (
    <>
      <div className='relative flex-1 min-h-0 overflow-y-auto'>
        {/* Header */}
        <header className='sticky top-0 z-10 h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-primary gap-3 bg-[var(--bg-card)]/80 backdrop-blur-sm'>
          <div className='flex items-center gap-2.5 min-w-0'>
            <span className='text-xl flex-shrink-0'>{skill.icon || '⚡'}</span>
            <h1 className='font-conversation-name font-semibold theme-text-primary truncate min-w-0'>
              {skill.name}
            </h1>
            {skill.isPublic && (
              <span className='text-[10px] px-2 py-0.5 rounded-full bg-blue-500/10 text-blue-600 font-semibold flex-shrink-0'>
                公共
              </span>
            )}
            {!skill.isEnabled && (
              <span className='text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-600 font-semibold flex-shrink-0'>
                已禁用
              </span>
            )}
          </div>
          <div className='flex items-center gap-1.5 flex-shrink-0'>
            <button
              onClick={() => setIsFormOpen(true)}
              className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm transition-all duration-200 cursor-pointer'
            >
              <Pencil className='w-3.5 h-3.5 theme-brand-primary' />
              <span className='theme-text-primary hidden sm:inline'>编辑</span>
            </button>
            <button
              onClick={handleDelete}
              disabled={deleting}
              className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-red-500/20 hover:border-red-500/40 hover:bg-red-500/5 transition-all duration-200 cursor-pointer disabled:opacity-50'
            >
              {deleting ? (
                <Loader2 className='w-3.5 h-3.5 text-red-500 animate-spin' />
              ) : (
                <Trash2 className='w-3.5 h-3.5 text-red-500' />
              )}
              <span className='text-red-500 hidden sm:inline'>删除</span>
            </button>
          </div>
        </header>

        {/* 详情内容 */}
        <div className='p-4 sm:p-5 lg:p-6 space-y-4 max-w-4xl mx-auto'>
          {/* 基本信息卡片 */}
          <section className='card-float-solid rounded-2xl p-5 space-y-3'>
            <h2 className='text-xs font-semibold theme-text-muted uppercase tracking-wide'>基本信息</h2>
            {skill.description && (
              <div>
                <label className='block text-xs theme-text-muted mb-1'>描述</label>
                <p className='text-sm theme-text-primary leading-relaxed'>{skill.description}</p>
              </div>
            )}
            <div className='grid grid-cols-2 gap-4'>
              <div>
                <label className='block text-xs theme-text-muted mb-1'>最大迭代</label>
                <p className='text-sm theme-text-primary font-mono'>{skill.maxIterations}</p>
              </div>
              <div>
                <label className='block text-xs theme-text-muted mb-1'>完成钩子</label>
                <p className='text-sm theme-text-primary'>{hookLabel}</p>
              </div>
            </div>
          </section>

          {/* 工具白名单 */}
          <section className='card-float-solid rounded-2xl p-5 space-y-3'>
            <h2 className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted uppercase tracking-wide'>
              <Shield className='w-3.5 h-3.5' />
              工具白名单
            </h2>
            {skill.allowedToolNames && skill.allowedToolNames.length > 0 ? (
              <div className='flex flex-wrap gap-1.5'>
                {skill.allowedToolNames.map((t) => (
                  <span
                    key={t}
                    className='inline-flex items-center gap-1 px-2 py-1 rounded-md theme-bg-hover theme-text-secondary text-xs font-mono'
                  >
                    <Wand2 className='w-3 h-3' />
                    {t}
                  </span>
                ))}
              </div>
            ) : (
              <p className='text-sm theme-text-muted'>无白名单（继承全局可用工具）</p>
            )}
          </section>

          {/* 触发关键词 */}
          {skill.triggerKeywords && skill.triggerKeywords.length > 0 && (
            <section className='card-float-solid rounded-2xl p-5 space-y-3'>
              <h2 className='text-xs font-semibold theme-text-muted uppercase tracking-wide'>触发关键词</h2>
              <div className='flex flex-wrap gap-1.5'>
                {skill.triggerKeywords.map((k) => (
                  <span
                    key={k}
                    className='px-2 py-1 rounded-md bg-amber-500/10 text-amber-600 text-xs font-medium'
                  >
                    {k}
                  </span>
                ))}
              </div>
            </section>
          )}

          {/* System Prompt */}
          {skill.systemPromptTemplate && (
            <section className='card-float-solid rounded-2xl p-5 space-y-3'>
              <h2 className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted uppercase tracking-wide'>
                <Wand2 className='w-3.5 h-3.5' />
                System Prompt 模板
              </h2>
              <pre className='text-xs theme-text-secondary font-mono whitespace-pre-wrap leading-relaxed bg-[var(--bg-base)] p-3 rounded-lg overflow-x-auto max-h-96 overflow-y-auto'>
                {skill.systemPromptTemplate}
              </pre>
            </section>
          )}

          {/* 补充指令 */}
          {skill.systemPromptSupplement && (
            <section className='card-float-solid rounded-2xl p-5 space-y-3'>
              <h2 className='text-xs font-semibold theme-text-muted uppercase tracking-wide'>补充指令</h2>
              <pre className='text-xs theme-text-secondary font-mono whitespace-pre-wrap leading-relaxed bg-[var(--bg-base)] p-3 rounded-lg overflow-x-auto max-h-96 overflow-y-auto'>
                {skill.systemPromptSupplement}
              </pre>
            </section>
          )}
        </div>
      </div>

      {/* 编辑 Modal */}
      <Modal
        isOpen={isFormOpen}
        onClose={() => setIsFormOpen(false)}
        title='编辑技能'
        size='xl'
        autoHeight
      >
        <SkillForm
          initial={skill}
          toolList={toolList}
          onCancel={() => setIsFormOpen(false)}
          onSubmit={handleSubmit}
        />
      </Modal>
    </>
  )
}

export default SkillDetailPage
