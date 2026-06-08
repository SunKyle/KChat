import { useEffect, useRef, useState } from 'react'
import { MessageCircle, ArrowDown, Sparkles, Code, BookOpen } from 'lucide-react'
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
  const [prevConversationId, setPrevConversationId] = useState<string | null>(null)
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
      <div className='flex-1 flex items-center justify-center theme-bg-primary'>
        <div className='text-center theme-text-muted animate-fade-in px-4'>
          <MessageCircle className='w-20 h-20 mx-auto mb-4 opacity-40' />
          <h2 className='font-h3 mb-2 theme-text-primary'>选择或创建对话</h2>
          <p className='font-secondary theme-text-secondary'>
            从左侧列表选择一个对话，或创建新对话开始聊天
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className='flex-1 flex flex-col theme-bg-primary min-h-0 relative'>
      <ErrorCard
        isVisible={!!error}
        severity='error'
        title='发生错误'
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
        <div className='w-full min-h-full flex flex-col items-center justify-center'>
          {isLoading ? (
            <div className='max-w-[800px] mx-auto w-full p-6 space-y-4'>
              <MessageSkeleton />
              <MessageSkeleton />
              <MessageSkeleton />
            </div>
          ) : messages.length === 0 ? (
            <div className='flex flex-col items-center justify-center px-8 py-20 max-w-[800px] mx-auto relative'>
              <div className='absolute -top-20 -right-20 w-72 h-72 bg-gradient-to-br from-sky-400/10 to-indigo-500/5 rounded-full blur-3xl' />
              <div className='absolute -bottom-20 -left-20 w-56 h-56 bg-gradient-to-tr from-purple-400/10 to-pink-500/5 rounded-full blur-3xl' />
              <div className='relative z-10 text-center flex flex-col items-center'>
                <div className='w-28 h-28 mb-8 rounded-xl bg-gradient-to-br from-sky-500/25 via-indigo-500/15 to-purple-500/10 flex items-center justify-center shadow-lg shadow-sky-500/10 hover:shadow-xl hover:shadow-sky-500/15 transition-all duration-300'>
                  <div className='w-20 h-20 rounded-lg bg-gradient-to-br from-sky-400 to-indigo-600 flex items-center justify-center shadow-inner'>
                    <Sparkles className='w-10 h-10 text-white' />
                  </div>
                </div>
                <h2 className='font-h2 theme-text-primary mb-3 text-center'>开始新对话</h2>
                <p className='theme-text-secondary mb-10 max-w-md text-center'>
                  你好！我是 AI 助手。有什么我可以帮助你的吗？
                </p>
                <div className='flex flex-wrap justify-center gap-3'>
                  <button
                    onClick={() => {}}
                    className='group relative px-5 py-3 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/60 backdrop-blur-sm hover:bg-white hover:border-sky-400/50 hover:shadow-lg hover:shadow-sky-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out flex items-center gap-2.5 cursor-pointer'
                  >
                    <div className='w-7 h-7 rounded-md bg-sky-100 group-hover:bg-sky-500/15 flex items-center justify-center transition-colors'>
                      <Code className='w-4 h-4 theme-text-muted group-hover:text-sky-500 transition-colors' />
                    </div>
                    帮我写代码
                  </button>
                  <button
                    onClick={() => {}}
                    className='group relative px-5 py-3 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/60 backdrop-blur-sm hover:bg-white hover:border-amber-400/50 hover:shadow-lg hover:shadow-amber-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out flex items-center gap-2.5 cursor-pointer'
                  >
                    <div className='w-7 h-7 rounded-md bg-amber-100 group-hover:bg-amber-500/15 flex items-center justify-center transition-colors'>
                      <BookOpen className='w-4 h-4 theme-text-muted group-hover:text-amber-600 transition-colors' />
                    </div>
                    解释概念
                  </button>
                  <button
                    onClick={() => {}}
                    className='group relative px-5 py-3 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/60 backdrop-blur-sm hover:bg-white hover:border-emerald-400/50 hover:shadow-lg hover:shadow-emerald-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out flex items-center gap-2.5 cursor-pointer'
                  >
                    <div className='w-7 h-7 rounded-md bg-emerald-100 group-hover:bg-emerald-500/15 flex items-center justify-center transition-colors'>
                      <MessageCircle className='w-4 h-4 theme-text-muted group-hover:text-emerald-500 transition-colors' />
                    </div>
                    回答问题
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <div className='py-6 font-ai-message'>
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
                      className='animate-message-in'
                      style={{ animationDelay: `${index * 50}ms` }}
                    >
                      <MessageBubble
                        message={message}
                        isThinking={isLastAssistantMessage}
                        onStop={isLastAssistantMessage ? stopStreaming : undefined}
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
          className='absolute bottom-4 right-4 p-3 card-inset rounded-full hover-lift micro-transition hover:scale-110'
          title='滚动到底部'
        >
          <ArrowDown className='w-[18px] h-[18px] theme-text-secondary' />
        </button>
      )}
    </div>
  )
}
