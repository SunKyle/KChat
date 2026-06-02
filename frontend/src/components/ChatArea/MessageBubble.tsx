import { User, Bot, Copy, RotateCcw, Check } from 'lucide-react'
import type { Message } from '../../types'
import { MarkdownRenderer } from './MarkdownRenderer'
import { useState, memo } from 'react'

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
  const [imageLoaded, setImageLoaded] = useState<Record<string, boolean>>({})

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

  const handleImageLoad = (imageUrl: string) => {
    setImageLoaded((prev) => ({ ...prev, [imageUrl]: true }))
  }

  return (
    <div
      className={`flex gap-4 py-8 group micro-transition ${isUser ? 'flex-row-reverse' : ''}`}
    >
      <div
        className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center transition-all micro-transition ${
          isUser
            ? 'theme-bg-card theme-text-secondary'
            : 'bg-primary-500 text-white shadow-sm shadow-primary-500/20'
        }`}
      >
        {isUser ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
      </div>

      <div className={`flex-1 min-w-0 ${isUser ? 'text-right' : 'text-left'}`}>
        <div className={`relative inline-block max-w-[85%] transition-all ${
            isUser ? 'theme-text-primary' : 'bg-transparent theme-text-primary'
          }`}>
          {isThinking && !message.content ? (
            <div className="flex items-center gap-2 py-1">
              <span className="theme-text-muted text-sm font-medium">
                AI 正在思考
              </span>
              <div className="flex items-center gap-1">
                <span
                  className="w-1.5 h-1.5 rounded-full theme-text-muted"
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0ms',
                  }}
                />
                <span
                  className="w-1.5 h-1.5 rounded-full theme-text-muted"
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0.2s',
                  }}
                />
                <span
                  className="w-1.5 h-1.5 rounded-full theme-text-muted"
                  style={{
                    animation: 'thinking-dot 1.4s ease-in-out infinite',
                    animationDelay: '0.4s',
                  }}
                />
              </div>
            </div>
          ) : (
            <div className="leading-relaxed">
              {message.images && message.images.length > 0 && (
                <div className="flex flex-wrap gap-2 mb-3">
                  {message.images.map((imageUrl, index) => (
                    <div
                      key={index}
                      className="relative rounded-lg overflow-hidden max-w-xs"
                    >
                      {!imageLoaded[imageUrl] && (
                        <div className="absolute inset-0 theme-bg-hover/30 flex items-center justify-center z-10">
                          <div className="w-6 h-6 border-2 theme-border-primary border-t-transparent rounded-full animate-spin" />
                        </div>
                      )}
                      <img
                        src={imageUrl}
                        alt={`Image ${index + 1}`}
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

        <div
          className={`flex items-center gap-3 mt-2 ${isUser ? 'justify-end' : 'justify-start'}`}
        >
          <span className="text-[10px] font-medium theme-text-muted uppercase">
            {formatTimestamp(message.timestamp)}
          </span>

          {!isUser && !isThinking && (
            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 micro-transition">
              <button
                onClick={handleCopy}
                className="p-2 rounded-lg hover:theme-bg-hover/50 hover:scale-110 transition-all duration-200"
                title={copied ? '已复制' : '复制'}
              >
                {copied ? (
                  <Check className="w-4 h-4 text-green-400" />
                ) : (
                  <Copy className="w-4 h-4 theme-text-muted hover:theme-text-secondary" />
                )}
              </button>
              {onRegenerate && (
                <button
                  onClick={onRegenerate}
                  className="p-2 rounded-lg hover:theme-bg-hover/50 hover:scale-110 transition-all duration-200"
                >
                  <RotateCcw className="w-4 h-4 theme-text-muted hover:theme-text-secondary" />
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
})
