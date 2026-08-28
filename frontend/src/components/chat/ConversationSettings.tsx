import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Icon } from '../common/Icon'
import { Drawer } from '../common/Drawer'
import { useChat } from '../../context/ChatContext'

export function ConversationSettings() {
  const { activeConversation, updateConversationRules } = useChat()
  const [isOpen, setIsOpen] = useState(false)
  const [customRules, setCustomRules] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  useEffect(() => {
    if (activeConversation) {
      setCustomRules(activeConversation.customRules || '')
    }
  }, [activeConversation?.id, activeConversation?.customRules])

  const handleSave = async () => {
    if (!activeConversation) return
    setIsSaving(true)
    try {
      await updateConversationRules(activeConversation.id, customRules)
      setIsOpen(false)
    } catch (e) {
      console.error('Failed to save rules:', e)
    } finally {
      setIsSaving(false)
    }
  }

  const handleOpen = () => {
    if (!activeConversation) return
    setCustomRules(activeConversation.customRules || '')
    setIsOpen(true)
  }

  const rulesText = activeConversation?.customRules?.trim() ?? ''
  const hasRules = rulesText.length > 0

  const tooltip = hasRules ? `会话规则已设定（点击查看/编辑）` : '会话设定'

  return (
    <>
      <button
        onClick={handleOpen}
        disabled={!activeConversation}
        className='relative p-2 rounded-lg theme-bg-hover/30 hover:theme-bg-hover hover:scale-110 micro-fast focus-ring transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:scale-100'
        title={tooltip}
        aria-label={tooltip}
      >
        <AnimatePresence mode='wait' initial={false}>
          {hasRules ? (
            <motion.span
              key='active'
              initial={{ opacity: 0, rotate: -45, scale: 0.6 }}
              animate={{ opacity: 1, rotate: 0, scale: 1 }}
              exit={{ opacity: 0, rotate: 45, scale: 0.6 }}
              transition={{ duration: 0.2, ease: [0.34, 1.56, 0.64, 1] }}
              className='block'
            >
              <Icon name='ScrollText' size='lg' className='theme-brand-primary' />
            </motion.span>
          ) : (
            <motion.span
              key='idle'
              initial={{ opacity: 0, rotate: 45, scale: 0.6 }}
              animate={{ opacity: 1, rotate: 0, scale: 1 }}
              exit={{ opacity: 0, rotate: -45, scale: 0.6 }}
              transition={{ duration: 0.2, ease: [0.34, 1.56, 0.64, 1] }}
              className='block theme-text-muted hover:theme-text-primary'
            >
              <Icon name='Settings' size='lg' />
            </motion.span>
          )}
        </AnimatePresence>
      </button>

      <Drawer isOpen={isOpen} onClose={() => setIsOpen(false)} title='会话设定' size='md'>
        <div className='p-6 flex flex-col gap-5 h-full'>
          <div className='flex items-start gap-3 p-3 rounded-lg bg-[var(--brand-primary)]/5 border border-[var(--brand-primary)]/20'>
            <Icon name='ScrollText' size='md' className='text-[var(--brand-primary)] mt-0.5 shrink-0' />
            <div className='text-xs theme-text-secondary leading-relaxed'>
              自定义指令仅对当前会话生效。模型会优先遵循这些指令，适用于设定输出格式、角色行为、风格偏好等场景。
            </div>
          </div>

          <div className='flex-1 flex flex-col gap-2 min-h-0'>
            <label className='text-sm font-medium theme-text-primary flex items-center gap-2'>
              自定义指令
              <span className='text-xs font-normal theme-text-muted'>（选填）</span>
            </label>
            <textarea
              value={customRules}
              onChange={(e) => setCustomRules(e.target.value)}
              placeholder={`例如：\n- 用 JSON 格式回复，字段名驼峰\n- 你是一位 Python 专家\n- 回答不超过 100 字`}
              className='flex-1 min-h-[200px] w-full px-4 py-3 rounded-xl border theme-border-primary bg-[var(--bg-input)] theme-text-primary placeholder:theme-text-muted/50 text-sm leading-relaxed resize-none focus:outline-none focus:ring-2 focus:ring-[var(--brand-primary)]/30 focus:border-[var(--brand-primary)]/50 transition-all duration-200 font-mono'
            />
            <div className='flex items-center justify-between text-xs theme-text-muted'>
              <span>{customRules.length} 字</span>
              {customRules.length > 4000 && (
                <span className='flex items-center gap-1 text-amber-500'>
                  <Icon name='AlertCircle' size='xs' />
                  建议不超过 4000 字符
                </span>
              )}
            </div>
          </div>

          <div className='flex items-center justify-end gap-2 pt-2 border-t theme-border-primary/30'>
            <button
              onClick={() => setIsOpen(false)}
              className='px-4 py-2 rounded-lg border theme-border-primary theme-text-secondary hover:bg-[var(--bg-hover)] transition-colors text-sm cursor-pointer'
            >
              取消
            </button>
            <button
              onClick={handleSave}
              disabled={isSaving}
              className='px-4 py-2 rounded-lg bg-[var(--brand-primary)] text-white hover:opacity-90 disabled:opacity-50 transition-all text-sm font-medium cursor-pointer'
            >
              {isSaving ? '保存中...' : '保存'}
            </button>
          </div>
        </div>
      </Drawer>
    </>
  )
}
