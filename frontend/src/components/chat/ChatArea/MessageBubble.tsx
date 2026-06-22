import { User, Bot, Copy, RotateCcw, Check, PenLine, Loader2 } from 'lucide-react'
import type { Message } from '../../../types'
import { MarkdownRenderer } from './MarkdownRenderer'
import { useState, memo } from 'react'
import { useUser } from '../../../context/UserContext'
import { useModel } from '../../../hooks/useModel'
import { useToast } from '../../../hooks/useToast'
import { useChat } from '../../../context/ChatContext'
import { chat as chatApi } from '../../../api/chat'
import { noteApi } from '../../../api/note-todo'

interface MessageBubbleProps {
  message: Message
  onRegenerate?: () => void
  isThinking?: boolean
  onStop?: () => void
}

export const MessageBubble = memo(function MessageBubble({
  message,
  onRegenerate,
  isThinking,
}: MessageBubbleProps) {
  const isUser = message.role === 'user'
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [imageLoaded, setImageLoaded] = useState<Record<string, boolean>>({})
  const { profile } = useUser()
  const { getCurrentModel } = useModel()
  const toast = useToast()
  const { startSummarizing, endSummarizing } = useChat()

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60000)

    if (diffMins < 1) return '刚刚'
    if (diffMins < 60) return `${diffMins} 分钟前`
    if (diffMins < 1440) return `${Math.floor(diffMins / 60)} 小时前`

    return date.toLocaleString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch (err) {
      console.error('复制失败:', err)
    }
  }

  const handleSaveAsNote = async () => {
    if (saving || saved) return
    setSaving(true)
    startSummarizing(message.conversationId)

    // 确保光晕至少显示 2 秒
    const minGlow = new Promise<void>((r) => setTimeout(r, 2000))

    try {
      const model = getCurrentModel()
      const { title, summary } = await chatApi.summarize(message.content, model)
      await noteApi.create({
        title,
        content: summary,
        category: 'AI对话',
        tags: ['ai-reply'],
      })
      setSaved(true)
      toast.success('已保存为笔记')
      window.dispatchEvent(new CustomEvent('note-created'))
      setTimeout(() => setSaved(false), 2000)
    } catch (err) {
      console.error('保存为笔记失败:', err)
      toast.error('保存为笔记失败，请重试')
    } finally {
      setSaving(false)
      await minGlow
      endSummarizing(message.conversationId)
    }
  }

  const handleImageLoad = (imageUrl: string) => {
    setImageLoaded((prev) => ({ ...prev, [imageUrl]: true }))
  }

  const bubbleContent = (
    <div className={`flex gap-4 py-5 group micro-transition ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center transition-all micro-transition overflow-hidden ${
          isUser
            ? 'theme-bg-card theme-text-secondary'
            : 'bg-gradient-to-br from-sky-500 to-sky-600 text-white shadow-sm'
        }`}
      >
        {isUser ? (
          profile?.avatar ? (
            <img src={profile.avatar} alt='User Avatar' className='w-full h-full object-cover' />
          ) : (
            <User className='w-[16px] h-[16px]' />
          )
        ) : (
          <Bot className='w-[16px] h-[16px]' />
        )}
      </div>

      <div className={`flex-1 min-w-0 ${isUser ? 'text-right' : 'text-left'}`}>
        <div
          className={`relative ${isThinking && !message.content ? 'block' : 'inline-block'} max-w-[85%] transition-all ${
            isUser ? 'theme-text-primary' : 'bg-transparent theme-text-primary'
          }`}
        >
          {isThinking && !message.content ? (
            <div className='flex items-center py-2 rounded-2xl bg-[var(--bg-input)]/60 max-w-fit'>
              <span className='text-[13px] text-[var(--text-muted)] font-secondary'>
                AI 正在思考
              </span>
              <div className='flex items-center gap-[6px] ml-3 pr-4'>
                <span
                  className='w-[7px] h-[7px] rounded-full bg-[var(--brand-primary)]'
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0ms',
                  }}
                />
                <span
                  className='w-[7px] h-[7px] rounded-full bg-[var(--brand-primary)]'
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0.2s',
                  }}
                />
                <span
                  className='w-[7px] h-[7px] rounded-full bg-[var(--brand-primary)]'
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0.4s',
                  }}
                />
              </div>
            </div>
          ) : (
            <div className='leading-relaxed'>
              {message.images && message.images.length > 0 && (
                <div className='flex flex-wrap gap-2 mb-3'>
                  {message.images.map((imageUrl, index) => (
                    <div key={index} className='relative rounded-lg overflow-hidden max-w-xs'>
                      {!imageLoaded[imageUrl] && (
                        <div className='absolute inset-0 theme-bg-hover/30 flex items-center justify-center z-10'>
                          <div className='w-6 h-6 border-2 theme-border-primary border-t-transparent rounded-full animate-spin' />
                        </div>
                      )}
                      <img
                        src={imageUrl}
                        alt={`Image ${index + 1}`}
                        loading='lazy'
                        className={`max-h-64 object-contain rounded-lg transition-opacity ${
                          imageLoaded[imageUrl] ? 'opacity-100' : 'opacity-50'
                        }`}
                        onLoad={() => handleImageLoad(imageUrl)}
                      />
                    </div>
                  ))}
                </div>
              )}
              <MarkdownRenderer content={message.content} />
            </div>
          )}
        </div>

        <div className={`flex items-center gap-3 mt-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
          <span className='text-[11px] text-[var(--text-timestamp)] opacity-70' title={new Date(message.timestamp).toLocaleString('zh-CN')}>
            {formatTimestamp(message.timestamp)}
          </span>

          {!isUser && !isThinking && (
            <div
              className={`relative flex items-center gap-1 micro-transition ${saving || saved ? 'opacity-100' : 'opacity-0 group-hover:opacity-100 focus-within:opacity-100'}`}
            >
              <button onClick={handleCopy} className='icon-btn' title={copied ? '已复制' : '复制'}>
                {copied ? (
                  <Check className='w-[14px] h-[14px] text-green-400' />
                ) : (
                  <Copy className='w-[14px] h-[14px]' />
                )}
              </button>
              {onRegenerate && (
                <button onClick={onRegenerate} className='icon-btn' title='重新生成'>
                  <RotateCcw className='w-[14px] h-[14px]' />
                </button>
              )}
              <div className='relative'>
                <button onClick={handleSaveAsNote} className='icon-btn peer' disabled={saving}>
                  {saving ? (
                    <Loader2 className='w-[14px] h-[14px] animate-spin text-[var(--brand-primary)]' />
                  ) : saved ? (
                    <Check className='w-[14px] h-[14px] text-green-400' />
                  ) : (
                    <PenLine className='w-[14px] h-[14px]' />
                  )}
                </button>
                {!saving && !saved && (
                  <span className='tooltip-content'>
                    AI 总结并保存为笔记
                  </span>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )

  return bubbleContent
})
