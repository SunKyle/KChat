import { useEffect, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import { Icon, isIconName } from '../../../components/common/Icon'
import { skills as skillsApi } from '../../../api'
import type { Skill } from '../../../api/skill'

const DEFAULT_USER_ID = 'default'

interface SkillPickerProps {
  /** 是否已激活 Skill 选择器（输入了 / 之后为 true） */
  open: boolean
  /** / 后面的搜索文本 */
  query: string
  /** 已选中的 skill id（选择器中提供「无技能」替代选项，所以这里仅用于高亮） */
  selectedId?: string | null
  /** 用户选中一个 Skill。选中「无技能」时传 null。 */
  onSelect: (skill: Skill | null) => void
  onClose: () => void
}

/**
 * Skill 选择器 —— 输入 / 唤起，样式完全对齐 KnowledgeBasePicker。
 *
 * 行为：
 * - 在 textarea 输入 `/` 时触发 open=true
 * - 随 `/xxx` 后面的文字实时 filter（query 由父组件提供）
 * - Esc / 点击外部 / 输入里没有 / 时自动关闭
 * - 选择后：清除尾部 /xxx 片段，选中项变成 chip 展示（由父组件处理）
 */
export function SkillPicker({
  open,
  query,
  selectedId,
  onSelect,
  onClose,
}: SkillPickerProps) {
  const [skillList, setSkillList] = useState<Skill[]>([])
  const [loading, setLoading] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  // 组件首次打开时加载一次，后续缓存
  const loadedRef = useRef(false)

  useEffect(() => {
    if (!open) return
    // 已成功加载过一次就跳过（避免重复请求）
    if (loadedRef.current) return
    let cancelled = false
    setLoading(true)
    skillsApi
      .list(DEFAULT_USER_ID)
      .then((list) => {
        if (cancelled) return
        setSkillList(list.filter((s) => s.isEnabled !== false))
        loadedRef.current = true
      })
      .catch(() => {
        if (cancelled) return
        setSkillList([])
      })
      .finally(() => {
        // 无论成功/失败/取消都必须解除 loading。
        // BUG 根因：之前在 cancelled=true 时跳过 setLoading(false)，
        // 用户快速输入 "/" 后立即删掉（弹层关闭触发 cleanup → cancelled=true），
        // 此时 pending 的请求在 finally 中被 cancelled 守卫拦下来，loading 永远 true；
        // 再打开时 loadedRef 已经为 true 无法重新请求，导致弹层永久 loading。
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [open])

  // Esc 关闭
  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: globalThis.KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  // 点击外部关闭
  useEffect(() => {
    if (!open) return
    const onMouseDown = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', onMouseDown)
    return () => document.removeEventListener('mousedown', onMouseDown)
  }, [open, onClose])

  const trimmed = query.trim().toLocaleLowerCase()
  const filtered = trimmed
    ? skillList.filter(
        (s) =>
          s.name.toLocaleLowerCase().includes(trimmed) ||
          (s.description ?? '').toLocaleLowerCase().includes(trimmed) ||
          (s.triggerKeywords ?? []).some((k) => k.toLocaleLowerCase().includes(trimmed))
      )
    : skillList

  return (
    <motion.div
      ref={containerRef}
      initial={{ opacity: 0, y: -8, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.98 }}
      transition={{ duration: 0.15, ease: 'easeOut' }}
      role='listbox'
      aria-label='技能引用选择器'
      className='relative z-30 mx-4 lg:mx-6 mb-6 overflow-hidden rounded-2xl theme-bg-elevated border theme-border-primary shadow-2xl'
    >
      <div className='px-3 pt-2.5 pb-2'>
        <div className='flex items-center gap-1.5 text-xs font-semibold theme-text-muted'>
          <Icon name='Sparkles' size='sm' />
          引用技能
          {trimmed && <span className='ml-1 text-[10px] opacity-70'>搜索「{query}」</span>}
        </div>
      </div>
      <div className='max-h-72 overflow-y-auto px-1.5 pb-1.5'>
        {/* 无技能选项 — 用来清除已选 */}
        <button
          type='button'
          role='option'
          aria-selected={selectedId == null}
          onClick={() => onSelect(null)}
          className={`w-full flex items-center gap-2.5 px-2 py-2 rounded-lg text-left hover:theme-bg-hover transition-colors duration-150 ${
            selectedId == null ? 'bg-[var(--brand-primary)]/10' : ''
          }`}
        >
          <div className='w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 theme-bg-hover'>
            <Icon name='Lock' size='md' className='theme-text-muted' />
          </div>
          <div className='flex-1 min-w-0'>
            <p className='text-sm theme-text-primary truncate'>无技能（默认）</p>
            <p className='text-xs theme-text-muted flex items-center gap-1'>不使用任何技能，使用通用对话能力</p>
          </div>
        </button>

        {skillList.length > 0 && <div className='mx-2 my-1 border-t theme-border-primary' />}

        {loading ? (
          <div className='flex items-center justify-center py-8'>
            <Icon name='Loader2' size='lg' className='animate-spin theme-text-muted' />
          </div>
        ) : skillList.length === 0 ? (
          <div className='px-3 py-8 text-center'>
            <Icon name='Sparkles' size='2xl' className='theme-text-muted mx-auto mb-2' />
            <p className='text-sm theme-text-secondary'>暂无技能，请先在设置 → 技能中心创建</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className='px-3 py-6 text-center text-sm theme-text-muted'>
            未找到匹配的技能
          </div>
        ) : (
          <ul>
            {filtered.map((skill) => {
              const isSelected = selectedId === skill.id
              return (
                <li key={skill.id}>
                  <button
                    type='button'
                    role='option'
                    aria-selected={isSelected}
                    onClick={() => onSelect(skill)}
                    className={`w-full flex items-center gap-2.5 px-2 py-2 rounded-lg text-left hover:theme-bg-hover transition-colors duration-150 ${
                      isSelected ? 'bg-[var(--accent-primary)]/10' : ''
                    }`}
                  >
                    <div className='w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 theme-bg-hover'>
                      <Icon
                        name={isIconName(skill.icon) ? skill.icon : 'Sparkles'}
                        size='md'
                        className='text-[var(--accent-primary)]'
                      />
                    </div>
                    <div className='flex-1 min-w-0'>
                      <p className='text-sm theme-text-primary truncate font-medium'>
                        {skill.name}
                      </p>
                      <p className='text-xs theme-text-muted flex items-center gap-1 line-clamp-1'>
                        {skill.description ?? `可使用 ${skill.allowedToolNames?.length ?? 0} 个工具`}
                      </p>
                    </div>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </motion.div>
  )
}

export default SkillPicker
