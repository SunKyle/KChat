import { useEffect, useRef, useState } from 'react'
import { MessageCircle, ArrowDown, Code, BookOpen, Lightbulb, Sparkles } from 'lucide-react'
import { useChat } from '../../../context/ChatContext'
import { MessageBubble } from './MessageBubble'
import { SearchResultsCard } from './SearchResultsCard'
import { MessageSkeleton } from '../../common/Skeleton'
import { ErrorCard } from '../../common/ErrorCard'

export function ChatArea() {
  const {
    activeConversation,
    messages,
    streamingState,
    error,
    clearError,
    getSearchResults,
    isLoading,
    stopStreaming,
    scrollTrigger,
    sendMessage,
  } = useChat()
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const scrollRafRef = useRef<number>(0)
  const [showScrollButton, setShowScrollButton] = useState(false)
  const currentConversationId = activeConversation?.id ?? null
  const [isScrolling, setIsScrolling] = useState(false)

  // Track pre-existing message count for stagger animation
  const mountedMsgCountRef = useRef(0)
  const prevConvIdRef = useRef<string | null>(null)
  const prevScrollConvIdRef = useRef<string | null>(null)

  // 1) Conversation switch detection & scroll — smooth scroll on switch, no stagger for cached messages
  useEffect(() => {
    const convId = currentConversationId

    if (convId && convId !== prevScrollConvIdRef.current) {
      // Conversation switched: cached messages are "new to this view" → no stagger animation
      mountedMsgCountRef.current = 0
      prevScrollConvIdRef.current = convId

      // Reset scroll position first, then smoothly scroll to bottom
      if (containerRef.current) {
        containerRef.current.scrollTop = 0
      }
      if (messagesEndRef.current) {
        messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
      }
    } else if (convId === prevScrollConvIdRef.current) {
      // Same conversation update (e.g., streaming): existing messages stay put
      mountedMsgCountRef.current = messages.length
    }

    prevConvIdRef.current = convId
  }, [messages, currentConversationId])

  // 2) Streaming auto-scroll — rAF-throttled to avoid excessive reflows
  useEffect(() => {
    if (streamingState.isStreaming && messagesEndRef.current) {
      cancelAnimationFrame(scrollRafRef.current)
      scrollRafRef.current = requestAnimationFrame(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'instant' })
      })
    }
  }, [messages, streamingState.isStreaming])

  // 3) Force scroll to bottom on send message
  useEffect(() => {
    if (scrollTrigger > 0 && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' })
    }
  }, [scrollTrigger])

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
      cancelAnimationFrame(scrollRafRef.current)
    }
  }, [])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  if (!activeConversation) {
    return (
      <div className='flex-1 flex items-center justify-center relative'>
        
        <div className='absolute top-1/4 left-1/4 w-96 h-96 bg-gradient-to-br from-[var(--brand-primary)]/8 via-[var(--accent-purple)]/5 to-[var(--accent-purple)]/3 rounded-full blur-3xl' />
        <div className='absolute bottom-1/4 right-1/4 w-80 h-80 bg-gradient-to-tr from-[var(--accent-amber)]/5 via-[var(--accent-rose)]/5 to-[var(--brand-success)]/5 rounded-full blur-3xl' />

        <div className='relative z-10 text-center px-4'>
          <div className='mb-6 inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-[var(--accent-sky)]/15 to-[var(--accent-purple)]/15 border border-[var(--border-primary)] backdrop-blur-sm shadow-lg'>
            <MessageCircle className='w-10 h-10 text-[var(--brand-primary)]' />
          </div>
          
          <h2 className='font-h2 theme-text-primary mb-3 animate-fade-in'>
            选择或创建对话
          </h2>
          <p className='font-body-m theme-text-secondary max-w-sm mx-auto animate-fade-in animation-delay-100'>
            从左侧列表选择一个对话，或创建新对话开始聊天
          </p>

          <div className='mt-8 flex items-center justify-center gap-3 animate-fade-in animation-delay-200'>
            <div className='flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-[var(--bg-card)]/80 backdrop-blur-sm border border-[var(--border-primary)] shadow-sm'>
              <Sparkles className='w-4 h-4 text-[var(--accent-amber)]' />
              <span className='text-xs text-[var(--text-secondary)]'>AI 助手已就绪</span>
            </div>
          </div>
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
        role='log'
        aria-live='polite'
        aria-label='聊天消息'
        className={`flex-1 overflow-y-auto scroll-smooth scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
      >
        <div className={`w-full min-h-full flex flex-col items-center ${messages.length > 0 ? 'justify-start' : 'justify-center'} px-4 sm:px-6 lg:px-8`}>
          {isLoading ? (
            <div className='max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto w-full p-6 space-y-4'>
              <MessageSkeleton />
              <MessageSkeleton />
              <MessageSkeleton />
            </div>
          ) : messages.length === 0 ? (
            <div className='flex flex-col items-center justify-center px-6 py-12 sm:py-20 max-w-2xl mx-auto relative'>
              <div className='absolute top-1/4 -right-1/4 w-80 h-80 bg-gradient-to-br from-[var(--brand-primary)]/12 via-[var(--accent-purple)]/8 to-[var(--accent-purple)]/3 rounded-full blur-3xl animate-float-slow' />
              <div className='absolute bottom-1/4 -left-1/4 w-64 h-64 bg-gradient-to-tr from-[var(--accent-amber)]/8 via-[var(--accent-rose)]/8 to-[var(--brand-success)]/5 rounded-full blur-3xl animate-float-slow-delayed' />

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
                      onClick={() => sendMessage('请帮我写一段代码', [], false)}
                      aria-label='写代码'
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-[var(--bg-card)]/90 backdrop-blur-sm hover:bg-[var(--bg-card)] hover:border-[var(--accent-sky)]/40 hover:shadow-md hover:shadow-[var(--shadow-color-primary)] hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-[var(--brand-primary)]/10 group-hover:bg-[var(--brand-primary)]/20 flex items-center justify-center transition-colors'>
                        <Code className='w-3.5 h-3.5 text-[var(--brand-primary)]' />
                      </div>
                      <span className='hidden sm:inline'>写代码</span>
                    </button>
                    <button
                      onClick={() => sendMessage('请帮我解释一个知识点', [], false)}
                      aria-label='学知识'
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-[var(--bg-card)]/90 backdrop-blur-sm hover:bg-[var(--bg-card)] hover:border-[var(--accent-sky)]/40 hover:shadow-md hover:shadow-[var(--shadow-color-primary)] hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-[var(--brand-success)]/10 group-hover:bg-[var(--brand-success)]/20 flex items-center justify-center transition-colors'>
                        <BookOpen className='w-3.5 h-3.5 text-[var(--brand-success)]' />
                      </div>
                      <span className='hidden sm:inline'>学知识</span>
                    </button>
                    <button
                      onClick={() => sendMessage('请帮我头脑风暴一些创意想法', [], false)}
                      aria-label='想创意'
                      className='group flex items-center gap-2 px-3 sm:px-4 py-2.5 text-theme-text-secondary font-secondary rounded-lg border theme-border-primary bg-[var(--bg-card)]/90 backdrop-blur-sm hover:bg-[var(--bg-card)] hover:border-[var(--accent-sky)]/40 hover:shadow-md hover:shadow-[var(--shadow-color-primary)] hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'
                    >
                      <div className='w-7 h-7 rounded-md bg-[var(--accent-amber)]/10 group-hover:bg-[var(--accent-amber)]/20 flex items-center justify-center transition-colors'>
                        <Lightbulb className='w-3.5 h-3.5 text-[var(--accent-amber)]' />
                      </div>
                      <span className='hidden sm:inline'>想创意</span>
                    </button>
                  </div>
                </div>

                <div
                  className='mt-10 sm:mt-14 flex items-center gap-4 text-theme-text-muted font-caption animate-fade-in-up animation-delay-300'
                  role='status'
                >
                  <div className='flex items-center gap-1.5'>
                    <div className='w-2 h-2 rounded-full bg-[var(--brand-success)] animate-pulse' />
                    <span>服务正常</span>
                  </div>
                  <div className='w-px h-3 bg-theme-border-primary' />
                  <span>响应迅速</span>
                </div>
              </div>
            </div>
          ) : (
            <div className='py-6 font-ai-message w-full max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto'>
              <div className='w-full px-2 sm:px-4 lg:px-6'>
                {activeConversation && (
                  <SearchResultsCard results={getSearchResults(activeConversation.id)} />
                )}
                {messages.map((message, index) => {
                  const isLastAssistantMessage =
                    message.role === 'assistant' &&
                    index === messages.length - 1 &&
                    streamingState.isStreaming

                  const isPreExisting = index < mountedMsgCountRef.current
                  // Cap stagger animation to first 15 messages to avoid
                  // all messages being invisible (opacity:0) in large conversations
                  const shouldAnimate = isPreExisting && index < 15

                  return (
                    <div
                      key={message.id}
                      className={shouldAnimate ? 'animate-message-in' : ''}
                      style={shouldAnimate ? { animationDelay: `${index * 50}ms` } : undefined}
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
          aria-label='回到底部'
          className={`scroll-btn-enter scroll-btn-glass absolute bottom-4 right-4 z-10 w-10 h-10 rounded-full flex items-center justify-center transition-all duration-200 hover:scale-110 hover:shadow-md active:scale-95 ${
            streamingState.isStreaming ? 'scroll-btn-streaming' : ''
          }`}
          title='滚动到底部'
        >
          <ArrowDown
            className={`w-4 h-4 transition-colors duration-200 ${
              streamingState.isStreaming
                ? 'theme-brand-primary'
                : 'theme-text-secondary hover:text-[var(--brand-primary)]'
            }`}
          />
        </button>
      )}
    </div>
  )
}
