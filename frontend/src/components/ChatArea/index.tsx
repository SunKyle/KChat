import { useEffect, useRef, useState } from 'react'
import { MessageCircle, ArrowDown, Sparkles } from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { MessageBubble } from './MessageBubble'
import { MessageSkeleton } from '../common/Skeleton'
import { ErrorCard } from '../common/ErrorCard'

export function ChatArea() {
  const {
    activeConversation,
    messages,
    streamingState,
    error,
    clearError,
    isLoading,
    stopStreaming,
  } = useChat()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [showScrollButton, setShowScrollButton] = useState(false)
  const [isTransitioning, setIsTransitioning] = useState(false)
  const [prevConversationId, setPrevConversationId] = useState<string | null>(
    null,
  )
  const [isScrolling, setIsScrolling] = useState(false)

  useEffect(() => {
    if (activeConversation && activeConversation.id !== prevConversationId) {
      setIsTransitioning(true)
      const timer = setTimeout(() => {
        setIsTransitioning(false)
        setPrevConversationId(activeConversation.id)
      }, 300)
      return () => clearTimeout(timer)
    }
  }, [activeConversation, prevConversationId])

  useEffect(() => {
    if (!showScrollButton && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, streamingState, showScrollButton])

  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => clearError(), 5000)
      return () => clearTimeout(timer)
    }
  }, [error, clearError])

  const handleScroll = () => {
    if (containerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = containerRef.current
      const isNearBottom = scrollHeight - scrollTop - clientHeight < 100
      setShowScrollButton(!isNearBottom)
    }

    setIsScrolling(true)
    if (scrollTimeoutRef.current) {
      clearTimeout(scrollTimeoutRef.current)
    }
    scrollTimeoutRef.current = setTimeout(() => {
      setIsScrolling(false)
    }, 2500)
  }

  useEffect(() => {
    return () => {
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current)
      }
    }
  }, [])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  if (!activeConversation) {
    return (
      <div className="flex-1 flex items-center justify-center theme-bg-primary">
        <div className="text-center theme-text-muted animate-fade-in px-4">
          <MessageCircle className="w-16 h-16 mx-auto mb-4 opacity-50" />
          <h2 className="text-xl font-medium mb-2 theme-text-primary">
            选择或创建对话
          </h2>
          <p className="theme-text-secondary">
            从左侧列表选择一个对话，或创建新对话开始聊天
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col theme-bg-primary min-h-0 relative">
      <ErrorCard
        isVisible={!!error}
        severity="error"
        title="发生错误"
        description={error ?? undefined}
        showCloseButton
        onClose={clearError}
        showRetryButton
        onRetry={() => {
          clearError()
        }}

      />

      <div
        ref={containerRef}
        onScroll={handleScroll}
        className={`flex-1 overflow-y-auto scroll-smooth scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
      >
        <div className="w-full min-h-full flex flex-col">
          {isLoading ? (
            <div className="max-w-[800px] mx-auto w-full p-6 space-y-4">
              <MessageSkeleton />
              <MessageSkeleton />
              <MessageSkeleton />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full px-8 py-20">
              <div className="w-24 h-24 mb-6 rounded-full bg-gradient-to-br from-sky-500/20 to-slate-700/20 flex items-center justify-center animate-pulse-once">
                <Sparkles className="w-12 h-12 text-sky-400" />
              </div>
              <h2 className="text-2xl font-semibold theme-text-primary mb-3">
                开始新对话
              </h2>
              <p className="theme-text-secondary mb-8 text-center max-w-md">
                你好！我是 AI 助手。有什么我可以帮助你的吗？
              </p>
              <div className="flex flex-wrap gap-3 justify-center">
                <button
                  onClick={() => {}}
                  className="px-4 py-2.5 theme-bg-card hover:theme-bg-hover text-theme-text-secondary rounded-xl text-sm micro-transition border theme-border-primary hover:border-primary-500/50 hover:shadow-lg hover:shadow-primary-500/10"
                >
                  帮我写代码
                </button>
                <button
                  onClick={() => {}}
                  className="px-4 py-2.5 theme-bg-card hover:theme-bg-hover text-theme-text-secondary rounded-xl text-sm micro-transition border theme-border-primary hover:border-primary-500/50 hover:shadow-lg hover:shadow-primary-500/10"
                >
                  解释概念
                </button>
                <button
                  onClick={() => {}}
                  className="px-4 py-2.5 theme-bg-card hover:theme-bg-hover text-theme-text-secondary rounded-xl text-sm micro-transition border theme-border-primary hover:border-primary-500/50 hover:shadow-lg hover:shadow-primary-500/10"
                >
                  回答问题
                </button>
              </div>
            </div>
          ) : (
            <div className="py-6">
              <div
                className={`max-w-[800px] mx-auto w-full px-4 sm:px-6 transition-all duration-300 ease-in-out ${isTransitioning ? 'opacity-0 scale-95 translate-y-4' : 'opacity-100 scale-100 translate-y-0'}`}
              >
                {messages.map((message, index) => {
                  const isLastAssistantMessage =
                    message.role === 'assistant' &&
                    index === messages.length - 1 &&
                    streamingState.isStreaming

                  return (
                    <div
                      key={message.id}
                      className="animate-message-in"
                      style={{ animationDelay: `${index * 50}ms` }}
                    >
                      <MessageBubble
                        message={message}
                        isThinking={isLastAssistantMessage}
                        onStop={
                          isLastAssistantMessage ? stopStreaming : undefined
                        }
                      />
                    </div>
                  )
                })}
              </div>
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>
      </div>

      {showScrollButton && messages.length > 0 && (
        <button
          onClick={scrollToBottom}
          className="absolute bottom-4 right-4 p-3 theme-bg-card hover:theme-bg-hover rounded-full shadow-lg micro-transition hover:scale-110"
          title="滚动到底部"
        >
          <ArrowDown className="w-5 h-5 theme-text-secondary" />
        </button>
      )}
    </div>
  )
}
