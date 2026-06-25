import { memo, useEffect, useRef, useState, useCallback } from 'react'
import { MessageCircle, ArrowDown, Code, BookOpen, Lightbulb, Sparkles } from 'lucide-react'
import { Virtuoso } from 'react-virtuoso'
import { motion, AnimatePresence } from 'framer-motion'
import { useChat } from '../../../context/ChatContext'
import { MessageBubble } from './MessageBubble'
import { SearchResultsCard } from './SearchResultsCard'
import { MessageSkeleton } from '../../common/Skeleton'
import { ErrorCard } from '../../common/ErrorCard'
import type { Message } from '../../../types'

const MessageWrapper = memo(function MessageWrapper({
  message,
  isLastAssistant,
  onStop,
  onRegenerate,
}: {
  message: Message
  isLastAssistant: boolean
  onStop?: () => void
  onRegenerate?: () => void
}) {
  return (
    <div className='max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto px-4 sm:px-6 lg:px-8'>
      <MessageBubble
        message={message}
        isThinking={isLastAssistant}
        onStop={isLastAssistant ? onStop : undefined}
        onRegenerate={message.role === 'assistant' ? onRegenerate : undefined}
      />
    </div>
  )
})

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
    regenerateMessage,
    getRegeneratingState,
  } = useChat()
  const virtuosoRef = useRef<any>(null)
  const [showScrollButton, setShowScrollButton] = useState(false)

  // Force scroll to bottom on send message
  useEffect(() => {
    if (scrollTrigger > 0) {
      virtuosoRef.current?.scrollToIndex({ index: messages.length - 1, behavior: 'smooth' })
    }
  }, [scrollTrigger, messages.length])

  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => clearError(), 5000)
      return () => clearTimeout(timer)
    }
  }, [error, clearError])

  const handleAtBottomStateChange = useCallback((atBottom: boolean) => {
    setShowScrollButton(!atBottom)
  }, [])

  const scrollToBottom = useCallback(() => {
    virtuosoRef.current?.scrollToIndex({ index: messages.length - 1, behavior: 'smooth' })
  }, [messages.length])

  const renderItem = useCallback(
    (index: number, message: Message) => {
      const isLast = index === messages.length - 1 && streamingState.isStreaming && message.role === 'assistant'
      const regeneratingState = activeConversation ? getRegeneratingState(activeConversation.id) : { isRegenerating: false, messageId: null }
      const isRegenerating = regeneratingState.isRegenerating && regeneratingState.messageId === message.id && message.role === 'assistant'
      return (
        <MessageWrapper
          message={message}
          isLastAssistant={isLast || isRegenerating}
          onStop={isLast ? stopStreaming : undefined}
          onRegenerate={message.role === 'assistant' && !isRegenerating ? () => regenerateMessage(activeConversation?.id!, message.id) : undefined}
        />
      )
    },
    [messages.length, streamingState.isStreaming, stopStreaming, activeConversation?.id, regenerateMessage, getRegeneratingState]
  )

  if (!activeConversation) {
    return (
      <div className='flex-1 flex items-center justify-center relative'>
        
        <div className='absolute top-1/4 left-1/4 w-96 h-96 bg-gradient-to-br from-[var(--brand-primary)]/8 via-[var(--accent-purple)]/5 to-[var(--accent-purple)]/3 rounded-full blur-3xl' />
        <div className='absolute bottom-1/4 right-1/4 w-80 h-80 bg-gradient-to-tr from-[var(--accent-amber)]/5 via-[var(--accent-rose)]/5 to-[var(--brand-success)]/5 rounded-full blur-3xl' />

        <div className='relative z-10 text-center px-4'>
          <div className='mb-6 inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-[var(--accent-primary)]/15 to-[var(--accent-purple)]/15 border border-[var(--border-primary)] backdrop-blur-sm shadow-lg'>
            <MessageCircle className='w-10 h-10 text-[var(--brand-primary)]' />
          </div>
          
          <h2 className='font-h2 theme-text-primary mb-3 animate-fade-in'>
            选择或创建对话
          </h2>
          <p className='text-lg theme-text-secondary max-w-sm mx-auto animate-fade-in animation-delay-100'>
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

      <AnimatePresence mode='wait'>
        <motion.div
          key={activeConversation?.id || 'empty'}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
          role='log'
          aria-live='polite'
          aria-label='聊天消息'
          className='flex-1 min-h-0'
        >
        {isLoading ? (
          <div className='max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto w-full p-6 space-y-4'>
            <MessageSkeleton />
            <MessageSkeleton />
            <MessageSkeleton />
          </div>
        ) : messages.length === 0 ? (
          <div className='flex flex-col items-center justify-center h-full px-6 py-12 sm:py-20 max-w-2xl mx-auto relative'>
            <div className='absolute top-1/4 -right-1/4 w-96 h-96 bg-gradient-to-br from-[var(--brand-primary)]/15 via-[var(--accent-purple)]/10 to-[var(--accent-purple)]/4 rounded-full blur-3xl animate-float-slow' />
            <div className='absolute bottom-1/4 -left-1/4 w-80 h-80 bg-gradient-to-tr from-[var(--accent-amber)]/10 via-[var(--accent-rose)]/10 to-[var(--brand-success)]/6 rounded-full blur-3xl animate-float-slow-delayed' />
            <div className='relative z-10 text-center flex flex-col items-center w-full'>
              <div className='mb-8 sm:mb-12 relative'>
                <div className='absolute inset-0 w-24 h-24 sm:w-28 sm:h-28 -top-2 -left-2 bg-gradient-to-br from-[var(--brand-primary)]/30 to-[var(--accent-purple)]/20 rounded-full blur-2xl' />
                <img src='/kchat-icon.svg' alt='KChat' className='relative w-20 sm:w-24 h-20 sm:h-24 object-contain drop-shadow-lg' />
              </div>
              <h1 className='text-5xl sm:text-6xl font-logo-system theme-text-primary mb-3 sm:mb-4 text-center animate-fade-in-up'>欢迎使用 KChat</h1>
              <p className='text-lg sm:text-xl theme-text-secondary mb-10 sm:mb-12 max-w-xs sm:max-w-sm text-center animate-fade-in-up animation-delay-100 leading-relaxed'>智能助手随时为您服务，开启高效对话体验</p>
              <div className='w-full max-w-md animate-fade-in-up animation-delay-200'>
                <div className='flex flex-wrap justify-center gap-2 sm:gap-3'>
                  <button onClick={() => sendMessage('请帮我写一段代码', [], false)} aria-label='写代码' className='group flex items-center gap-2.5 px-4 sm:px-5 py-3 text-theme-text-secondary text-sm sm:text-base rounded-xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:bg-white/[0.06] hover:border-[var(--brand-primary)]/30 hover:shadow-lg hover:shadow-[var(--brand-primary)]/8 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'>
                    <div className='w-8 h-8 rounded-lg bg-[var(--brand-primary)]/15 group-hover:bg-[var(--brand-primary)]/25 flex items-center justify-center transition-colors'><Code className='w-4 h-4 text-[var(--brand-primary)]' /></div>
                    <span className='hidden sm:inline font-semibold'>写代码</span>
                  </button>
                  <button onClick={() => sendMessage('请帮我解释一个知识点', [], false)} aria-label='学知识' className='group flex items-center gap-2.5 px-4 sm:px-5 py-3 text-theme-text-secondary text-sm sm:text-base rounded-xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:bg-white/[0.06] hover:border-[var(--brand-success)]/30 hover:shadow-lg hover:shadow-[var(--brand-success)]/8 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'>
                    <div className='w-8 h-8 rounded-lg bg-[var(--brand-success)]/15 group-hover:bg-[var(--brand-success)]/25 flex items-center justify-center transition-colors'><BookOpen className='w-4 h-4 text-[var(--brand-success)]' /></div>
                    <span className='hidden sm:inline font-semibold'>学知识</span>
                  </button>
                  <button onClick={() => sendMessage('请帮我头脑风暴一些创意想法', [], false)} aria-label='想创意' className='group flex items-center gap-2.5 px-4 sm:px-5 py-3 text-theme-text-secondary text-sm sm:text-base rounded-xl border border-white/10 bg-white/[0.03] backdrop-blur-md hover:bg-white/[0.06] hover:border-[var(--accent-amber)]/30 hover:shadow-lg hover:shadow-[var(--accent-amber)]/8 hover:-translate-y-0.5 transition-all duration-300 ease-out cursor-pointer'>
                    <div className='w-8 h-8 rounded-lg bg-[var(--accent-amber)]/15 group-hover:bg-[var(--accent-amber)]/25 flex items-center justify-center transition-colors'><Lightbulb className='w-4 h-4 text-[var(--accent-amber)]' /></div>
                    <span className='hidden sm:inline font-semibold'>想创意</span>
                  </button>
                </div>
              </div>
              <div className='mt-12 sm:mt-16 flex items-center gap-4 text-theme-text-muted text-sm animate-fade-in-up animation-delay-300' role='status'>
                <div className='flex items-center gap-1.5'><div className='w-2 h-2 rounded-full bg-[var(--brand-success)] animate-pulse' /><span>服务正常</span></div>
                <div className='w-px h-3 bg-[var(--border-divider)]' /><span>响应迅速</span>
              </div>
            </div>
          </div>
        ) : (
          <div className='h-full flex flex-col'>
            {activeConversation && (
              <div className='flex-shrink-0 px-4 sm:px-6 lg:px-8 pt-4 max-w-xl sm:max-w-2xl lg:max-w-3xl mx-auto w-full'>
                <SearchResultsCard results={getSearchResults(activeConversation.id)} />
              </div>
            )}
            <Virtuoso
              ref={virtuosoRef}
              data={messages}
              followOutput={'smooth'}
              atBottomStateChange={handleAtBottomStateChange}
              increaseViewportBy={{ top: 600, bottom: 1200 }}
              className='scrollbar-auto-hide'
              computeItemKey={(_: number, msg: Message) => msg.id}
              itemContent={renderItem}
            />
          </div>
        )}
        </motion.div>
      </AnimatePresence>

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
