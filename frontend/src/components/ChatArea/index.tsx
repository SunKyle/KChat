import { useEffect, useRef, useState } from 'react'
import { MessageCircle, ArrowDown, Code, BookOpen, Lightbulb } from 'lucide-react'
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
      <div className='flex-1 flex items-center justify-center'>
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
    <div className='flex-1 flex flex-col min-h-0 relative'>
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
        <div className='w-full min-h-full flex flex-col items-center justify-center px-4 sm:px-6 lg:px-8'>
          {isLoading ? (
            <div className='max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto w-full p-6 space-y-4'>
              <MessageSkeleton />
              <MessageSkeleton />
              <MessageSkeleton />
            </div>
          ) : messages.length === 0 ? (
            <div className='flex flex-col items-center justify-center px-6 py-12 sm:py-20 max-w-2xl mx-auto relative'>
              <div className='absolute top-1/4 -right-1/4 w-80 h-80 bg-gradient-to-br from-sky-400/15 via-indigo-500/10 to-purple-500/5 rounded-full blur-3xl animate-float-slow' />
              <div className='absolute bottom-1/4 -left-1/4 w-64 h-64 bg-gradient-to-tr from-amber-400/10 via-pink-500/10 to-emerald-500/5 rounded-full blur-3xl animate-float-slow-delayed' />

              <div className='relative z-10 text-center flex flex-col items-center w-full'>
                <div className='mb-8 sm:mb-12'>
                  <img
                    src='/kchat-icon.svg'
                    alt='KChat'
                    className='w-16 sm:w-20 h-16 sm:h-20 object-contain'
                  />
                </div>

                <h2 className='font-h2 sm:font-h1 theme-text-primary mb-3 sm:mb-4 text-center animate-fade-in-up'>
                  欢迎使用 KChat
                </h2>
                <p className='font-body-m theme-text-secondary mb-8 sm:mb-10 max-w-sm text-center animate-fade-in-up animation-delay-100'>
                  智能助手随时为您服务，开启高效对话体验
                </p>

                <div className='w-full max-w-md animate-fade-in-up animation-delay-200'>
                  <div className='flex flex-wrap justify-center gap-2 sm:gap-3'>
                    <button
                      onClick={() => {}}
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/90 backdrop-blur-sm hover:bg-white hover:border-sky-400/60 hover:shadow-md hover:shadow-sky-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-sky-100 group-hover:bg-sky-500/20 flex items-center justify-center transition-colors'>
                        <Code className='w-3.5 h-3.5 text-sky-600' />
                      </div>
                      <span className='hidden sm:inline'>写代码</span>
                    </button>
                    <button
                      onClick={() => {}}
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/90 backdrop-blur-sm hover:bg-white hover:border-sky-400/60 hover:shadow-md hover:shadow-sky-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-emerald-100 group-hover:bg-emerald-500/20 flex items-center justify-center transition-colors'>
                        <BookOpen className='w-3.5 h-3.5 text-emerald-600' />
                      </div>
                      <span className='hidden sm:inline'>学知识</span>
                    </button>
                    <button
                      onClick={() => {}}
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-white/90 backdrop-blur-sm hover:bg-white hover:border-sky-400/60 hover:shadow-md hover:shadow-sky-500/10 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-amber-100 group-hover:bg-amber-500/20 flex items-center justify-center transition-colors'>
                        <Lightbulb className='w-3.5 h-3.5 text-amber-600' />
                      </div>
                      <span className='hidden sm:inline'>想创意</span>
                    </button>
                  </div>
                </div>

                <div className='mt-10 sm:mt-14 flex items-center gap-4 text-theme-text-muted font-caption animate-fade-in-up animation-delay-300'>
                  <div className='flex items-center gap-1.5'>
                    <div className='w-2 h-2 rounded-full bg-emerald-500 animate-pulse' />
                    <span>服务正常</span>
                  </div>
                  <div className='w-px h-3 bg-theme-border-primary' />
                  <span>响应迅速</span>
                </div>
              </div>
            </div>
          ) : (
            <div className='py-6 font-ai-message w-full max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto'>
              <div
                className={`w-full px-2 sm:px-4 lg:px-6 transition-all duration-300 ease-in-out ${isTransitioning ? 'opacity-0 scale-95 translate-y-4' : 'opacity-100 scale-100 translate-y-0'}`}
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
          className={`scroll-btn-enter scroll-btn-glass absolute bottom-4 right-4 w-10 h-10 rounded-full flex items-center justify-center transition-all duration-200 hover:scale-110 hover:shadow-md active:scale-95 ${
            streamingState.isStreaming ? 'scroll-btn-streaming' : ''
          }`}
          title='滚动到底部'
        >
          <ArrowDown className={`w-4 h-4 transition-colors duration-200 ${
            streamingState.isStreaming ? 'text-sky-500' : 'theme-text-secondary hover:text-sky-500'
          }`} />
        </button>
      )}
    </div>
  )
}
