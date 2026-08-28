import { memo, useEffect, useRef, useState, useCallback } from 'react'
import { Icon } from '../../common/Icon'
import { Virtuoso } from 'react-virtuoso'
import type { VirtuosoHandle } from 'react-virtuoso'
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
  const virtuosoRef = useRef<VirtuosoHandle | null>(null)
  const [showScrollButton, setShowScrollButton] = useState(false)

  // Force scroll to bottom on send message
  useEffect(() => {
    if (scrollTrigger > 0) {
      virtuosoRef.current?.scrollTo({ top: Number.MAX_SAFE_INTEGER, behavior: 'smooth' })
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
      const isLast =
        index === messages.length - 1 && streamingState.isStreaming && message.role === 'assistant'
      const regeneratingState = activeConversation
        ? getRegeneratingState(activeConversation.id)
        : { isRegenerating: false, messageId: null }
      const isRegenerating =
        regeneratingState.isRegenerating &&
        regeneratingState.messageId === message.id &&
        message.role === 'assistant'
      return (
        <MessageWrapper
          message={message}
          isLastAssistant={isLast || isRegenerating}
          onStop={isLast ? stopStreaming : undefined}
          onRegenerate={
            message.role === 'assistant' && !isRegenerating
              ? () => {
                  if (activeConversation) {
                    regenerateMessage(activeConversation.id, message.id)
                  }
                }
              : undefined
          }
        />
      )
    },
    [
      messages.length,
      streamingState.isStreaming,
      stopStreaming,
      activeConversation,
      regenerateMessage,
      getRegeneratingState,
    ]
  )

  if (!activeConversation) {
    return (
      <div className='flex-1 flex items-center justify-center'>
        <div className='text-center px-4'>
          <div className='mb-5'>
            <div className='w-12 h-12 rounded-xl bg-[var(--brand-primary)]/10 flex items-center justify-center mx-auto'>
              <Icon name='MessageCircle' size='xl' className='text-[var(--brand-primary)]' />
            </div>
          </div>
          <h2 className='text-lg font-semibold theme-text-primary mb-1.5'>选择或创建对话</h2>
          <p className='text-sm theme-text-secondary'>
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
            <div className='flex flex-col items-center justify-center h-full px-6 max-w-lg mx-auto'>
              <div className='mb-7'>
                <div className='w-14 h-14 rounded-2xl bg-[var(--brand-primary)]/10 flex items-center justify-center'>
                  <Icon name='MessageCircle' size='2xl' className='text-[var(--brand-primary)]' />
                </div>
              </div>
              <h1 className='text-2xl font-semibold theme-text-primary mb-2 text-center'>
                欢迎使用 KChat
              </h1>
              <p className='text-sm theme-text-secondary mb-8 text-center leading-relaxed'>
                智能助手随时为您服务，开始一段对话吧
              </p>
              <button
                onClick={() => sendMessage('请帮我写一段代码', [], false)}
                className='group flex items-center gap-2 px-4 py-2.5 rounded-full border border-[var(--border-primary)] bg-[var(--bg-card)]/60 text-sm theme-text-secondary hover:text-[var(--brand-primary)] hover:border-[var(--brand-primary)]/30 hover:bg-[var(--brand-primary)]/5 transition-all duration-200 cursor-pointer'
              >
                <Icon name='Sparkles' size='sm' className='text-[var(--brand-primary)]/60 group-hover:text-[var(--brand-primary)]' />
                <span>试试问：帮我写一段 Python 爬虫</span>
              </button>
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
          <Icon
            name='ArrowDown'
            size='md'
            className={`transition-colors duration-200 ${
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
