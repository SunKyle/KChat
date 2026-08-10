import { X, Pin, Maximize2, Sparkles, Loader2 } from 'lucide-react'
import { useState } from 'react'
import { useModel } from '../../hooks/useModel'
import { useToast } from '../../hooks/useToast'
import { chat as chatApi } from '../../api/chat'
import { BorderBeam } from '../ui/border-beam'

interface FormState {
  title: string
  content: string
  category: string
  tags: string[]
  newTag: string
  pinned: boolean
  description: string
  priority: 'high' | 'medium' | 'low'
  dueDate: string
}

interface NoteFormProps {
  formState: FormState
  setFormState: React.Dispatch<React.SetStateAction<FormState>>
  categories: string[]
  isEditing: boolean
  onCancel: () => void
  onSubmit: () => void
  onOpenFullscreen: () => void
}

export function NoteForm({
  formState,
  setFormState,
  categories,
  isEditing,
  onCancel,
  onSubmit,
  onOpenFullscreen,
}: NoteFormProps) {
  const [aiSummarizing, setAiSummarizing] = useState(false)
  const { getCurrentModel } = useModel()
  const toast = useToast()

  const handleAISummarize = async () => {
    if (aiSummarizing || !formState.content.trim()) {
      if (!formState.content.trim()) toast.warning('请先输入内容')
      return
    }
    setAiSummarizing(true)
    try {
      const model = getCurrentModel()
      const { title, summary } = await chatApi.summarize(formState.content, model)
      setFormState((prev) => ({
        ...prev,
        title: !prev.title.trim() ? title : prev.title,
        content: summary,
      }))
      toast.success('AI 整理完成')
    } catch (err) {
      console.error('AI 总结失败:', err)
      toast.error('AI 整理失败，请重试')
    } finally {
      setAiSummarizing(false)
    }
  }

  const handleAddTag = (e: React.KeyboardEvent) => {
    if (
      e.key === 'Enter' &&
      formState.newTag.trim() &&
      !formState.tags.includes(formState.newTag.trim())
    ) {
      e.preventDefault()
      setFormState((prev) => ({ ...prev, tags: [...prev.tags, prev.newTag.trim()], newTag: '' }))
    }
  }

  const handleRemoveTag = (tag: string) => {
    setFormState((prev) => ({ ...prev, tags: prev.tags.filter((t) => t !== tag) }))
  }

  return (
    <div className='flex-1 flex flex-col overflow-hidden'>
      <div className='flex-1 overflow-y-auto p-4 space-y-5'>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            标题
          </label>
          <input
            type='text'
            value={formState.title}
            onChange={(e) => setFormState((p) => ({ ...p, title: e.target.value }))}
            placeholder='输入笔记标题'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            内容
          </label>
          <div className='relative'>
            {aiSummarizing && <BorderBeam size={100} duration={4} className='z-10' />}
            <textarea
              value={formState.content}
              onChange={(e) => setFormState((p) => ({ ...p, content: e.target.value }))}
              rows={8}
              placeholder='输入笔记内容...'
              className='w-full px-3 py-2.5 pr-10 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all resize-none leading-relaxed'
            />
            <button
              onClick={onOpenFullscreen}
              className='absolute right-2 top-2 p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='全屏编辑'
              title='全屏 Markdown 编辑'
            >
              <Maximize2 className='w-4 h-4 text-[var(--text-muted)]' />
            </button>
            <div className='absolute right-9 top-2'>
              <button
                onClick={handleAISummarize}
                disabled={aiSummarizing}
                className='p-1.5 rounded-lg hover:bg-[var(--bg-hover)] transition-colors peer disabled:opacity-50'
                aria-label='AI 整理'
              >
                {aiSummarizing ? (
                  <Loader2 className='w-4 h-4 text-[var(--brand-primary)] animate-spin' />
                ) : (
                  <Sparkles className='w-4 h-4 text-[var(--text-muted)] hover:text-[var(--brand-primary)] transition-colors' />
                )}
              </button>
              {!aiSummarizing && (
                <span className='absolute bottom-full right-0 mb-1.5 px-2.5 py-1 rounded-md text-xs text-white bg-[var(--text-primary)] whitespace-nowrap opacity-0 scale-95 pointer-events-none peer-hover:opacity-100 peer-hover:scale-100 transition-all duration-200 z-20 shadow-lg'>
                  AI 整理为 Markdown 笔记
                  <span className='absolute top-full right-3 border-4 border-transparent border-t-gray-800' />
                </span>
              )}
            </div>
          </div>
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            分类
          </label>
          <div className='flex items-center gap-2'>
            <div className='flex items-center gap-1.5 flex-wrap flex-1'>
              {categories.map((c) => (
                <button
                  key={c}
                  onClick={() => setFormState((p) => ({ ...p, category: c }))}
                  className={`px-3 py-2 rounded-lg text-xs font-semibold transition-all ${
                    formState.category === c
                      ? 'bg-[var(--brand-primary)]/15 text-[var(--brand-primary)]'
                      : 'bg-[var(--bg-input)] text-[var(--text-muted)] hover:text-[var(--text-secondary)]'
                  }`}
                >
                  {c}
                </button>
              ))}
            </div>
            <button
              onClick={() => setFormState((p) => ({ ...p, pinned: !p.pinned }))}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                formState.pinned
                  ? 'bg-[var(--accent-amber)]/[0.12] text-[var(--accent-amber)] border border-[var(--accent-amber)]/25'
                  : 'bg-[var(--bg-input)] text-[var(--text-muted)] border border-transparent hover:text-[var(--text-secondary)]'
              }`}
            >
              <Pin className={`w-3.5 h-3.5 ${formState.pinned ? 'fill-current' : ''}`} />
              置顶
            </button>
          </div>
        </div>
        <div>
          <label className='block text-xs font-semibold text-[var(--text-secondary)] mb-2'>
            标签
          </label>
          {formState.tags.length > 0 && (
            <div className='flex flex-wrap gap-1.5 mb-2.5'>
              {formState.tags.map((tag) => (
                <span
                  key={tag}
                  className='flex items-center gap-1 px-2.5 py-1 bg-[var(--brand-primary)]/[0.08] text-[var(--brand-primary)] rounded-full text-xs font-semibold'
                >
                  {tag}
                  <button
                    onClick={() => handleRemoveTag(tag)}
                    className='hover:opacity-70 transition-opacity'
                  >
                    <X className='w-3 h-3' />
                  </button>
                </span>
              ))}
            </div>
          )}
          <input
            type='text'
            value={formState.newTag}
            onChange={(e) => setFormState((p) => ({ ...p, newTag: e.target.value }))}
            onKeyDown={handleAddTag}
            placeholder='输入标签，按 Enter 添加'
            className='w-full px-3 py-2.5 bg-[var(--bg-input)] border border-transparent rounded-xl text-sm text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/20 transition-all'
          />
        </div>
      </div>
      <div className='flex-shrink-0 flex items-center justify-end gap-2.5 px-4 py-3 border-t border-[var(--border-divider)]'>
        <button
          onClick={onCancel}
          className='px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-secondary)] rounded-xl text-sm font-semibold hover:bg-[var(--bg-input)] transition-colors'
        >
          取消
        </button>
        <button
          onClick={onSubmit}
          className='px-5 py-2 rounded-xl text-sm font-semibold text-white bg-[var(--brand-primary)] hover:brightness-110 transition-all shadow-sm shadow-[var(--brand-primary)]/20'
        >
          {isEditing ? '保存' : '创建'}
        </button>
      </div>
    </div>
  )
}
