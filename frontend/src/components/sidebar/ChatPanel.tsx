import {
  MessageSquare,
  MessageSquarePlus,
  ChevronRight,
  Search,
  X,
} from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import { useChat } from '../../context/ChatContext'
import { useConversation } from '../../hooks/useConversation'
import { ConversationItem } from './ConversationItem'
import { useState, useEffect, useRef, useMemo, useCallback, useLayoutEffect } from 'react'

interface ChatPanelProps {
  onToggle: () => void
  onDeleteClick: (id: string, title: string) => void
  onConversationClick: () => void
}

export function ChatPanel({ onToggle, onDeleteClick, onConversationClick }: ChatPanelProps) {
  // 从localStorage恢复展开状态
  const getInitialExpandedGroups = (): Set<string> => {
    try {
      const saved = localStorage.getItem('sidebarExpandedGroups')
      if (saved) {
        return new Set(JSON.parse(saved))
      }
    } catch (e) {
      console.error('Failed to restore expanded groups:', e)
    }
    return new Set(['今天', '昨天', '本周', '最近'])
  }

  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(getInitialExpandedGroups)
  const [searchQuery, setSearchQuery] = useState('')
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const {
    conversations,
    activeConversation,
    getStreamingState,
    getHasNewReply,
    resetNewReply,
    getSummarizingState,
  } = useChat()

  const { create, update, pin, select } = useConversation()

  const [highlightRect, setHighlightRect] = useState<{ top: number; height: number } | null>(null)
  const itemRefs = useRef<Map<string, HTMLElement>>(new Map())

  const registerItemRef = useCallback((id: string, el: HTMLElement | null) => {
    if (el) {
      itemRefs.current.set(id, el)
    } else {
      itemRefs.current.delete(id)
    }
  }, [])

  const measureActiveItem = useCallback(() => {
    if (!activeConversation?.id || !scrollContainerRef.current) {
      setHighlightRect(null)
      return
    }
    const el = itemRefs.current.get(activeConversation.id)
    if (!el) {
      setHighlightRect(null)
      return
    }
    const containerRect = scrollContainerRef.current.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    setHighlightRect({
      top: elRect.top - containerRect.top + scrollContainerRef.current.scrollTop,
      height: elRect.height,
    })
  }, [activeConversation?.id])

  const toggleGroup = (group: string) => {
    const newExpanded = new Set(expandedGroups)
    if (newExpanded.has(group)) {
      newExpanded.delete(group)
    } else {
      newExpanded.add(group)
    }
    setExpandedGroups(newExpanded)
  }

  // 持久化展开状态
  useEffect(() => {
    localStorage.setItem('sidebarExpandedGroups', JSON.stringify([...expandedGroups]))
  }, [expandedGroups])

  const groupConversationsByList = useCallback((convs: typeof conversations) => {
    const groups: Record<string, typeof conversations> = {}
    const now = new Date()

    convs.forEach((conv) => {
      let group = '最近'

      if (conv.pinned) {
        group = '置顶'
      } else {
        const date = new Date(conv.createdAt || Date.now())
        const diffDays = Math.floor((now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24))
        if (diffDays === 0) group = '今天'
        else if (diffDays === 1) group = '昨天'
        else if (diffDays < 7) group = '本周'
      }

      if (!groups[group]) groups[group] = []
      groups[group].push(conv)
    })

    const order = ['置顶', '今天', '昨天', '本周', '最近']
    return order.filter((g) => groups[g]).map((g) => ({ group: g, items: groups[g] }))
  }, [])

  // 过滤会话
  const filteredConversations = useMemo(() => {
    if (!searchQuery.trim()) return conversations
    const query = searchQuery.toLowerCase()
    return conversations.filter((conv) => conv.title.toLowerCase().includes(query))
  }, [conversations, searchQuery])

  // 分组后的会话列表
  const filteredGrouped = useMemo(() => {
    return groupConversationsByList(filteredConversations)
  }, [filteredConversations, groupConversationsByList])

  useLayoutEffect(() => {
    measureActiveItem()
  }, [measureActiveItem, filteredGrouped, expandedGroups])

  useEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return
    container.addEventListener('scroll', measureActiveItem, { passive: true })
    window.addEventListener('resize', measureActiveItem)
    return () => {
      container.removeEventListener('scroll', measureActiveItem)
      window.removeEventListener('resize', measureActiveItem)
    }
  }, [measureActiveItem])

  // 新增/切换会话时自动展开对应分组并滚动到激活项
  useEffect(() => {
    if (!activeConversation) return

    const activeGroup = filteredGrouped.find((g) =>
      g.items.some((item) => item.id === activeConversation.id)
    )
    if (activeGroup && !expandedGroups.has(activeGroup.group)) {
      setExpandedGroups((prev) => new Set([...prev, activeGroup.group]))
    }

    const timer = setTimeout(() => {
      const el = document.querySelector(`[data-conversation-id="${activeConversation.id}"]`)
      el?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }, 200)

    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeConversation?.id])

  // 滚动时显示滚动条
  useEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return

    const handleScrollEvent = () => {
      container.classList.add('scrolling')
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current)
      }
      scrollTimeoutRef.current = setTimeout(() => {
        container.classList.remove('scrolling')
      }, 1200)
    }

    container.addEventListener('scroll', handleScrollEvent, { passive: true })
    return () => {
      container.removeEventListener('scroll', handleScrollEvent)
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current)
      }
    }
  }, [])

  // 全局 ⌘K / Ctrl+K 快捷键：聚焦搜索框
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isModK = (e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k'
      if (!isModK) return

      e.preventDefault()
      searchInputRef.current?.focus()
      searchInputRef.current?.select()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  const handleDelete = (id: string, title: string) => {
    onDeleteClick?.(id, title)
  }

  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>对话</h2>
        <button
          onClick={onToggle}
          aria-label='收起侧边栏'
          className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
        >
          <ChevronRight className='w-4 h-4 rotate-180' aria-hidden='true' />
        </button>
      </div>

      <AnimatePresence>
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: 0 }}
          transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
          className='overflow-hidden pb-1'
        >
          <div className='flex items-center gap-2 mt-2 px-4 relative z-10'>
            <div className='relative flex-1'>
              <Search
                className='absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-[var(--text-muted)]'
                aria-hidden='true'
              />
              <input
                ref={searchInputRef}
                type='text'
                role='combobox'
                aria-expanded={searchQuery.length > 0}
                aria-autocomplete='list'
                aria-controls='conversation-list'
                aria-haspopup='listbox'
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape' && searchQuery) {
                    e.preventDefault()
                    setSearchQuery('')
                  } else if (e.key === 'Escape') {
                    searchInputRef.current?.blur()
                  }
                }}
                placeholder='搜索会话...'
                aria-label='搜索会话'
                className='w-full pl-8 pr-16 py-2 bg-[var(--bg-input)] border border-[var(--border-primary)] rounded-lg text-sm font-secondary text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none focus:border-[var(--brand-primary)]/40 focus:ring-1 focus:ring-[var(--brand-primary)]/25 transition-all'
              />
              <div className='absolute right-2 top-1/2 -translate-y-1/2 flex items-center gap-1'>
                {searchQuery ? (
                  <button
                    onClick={() => setSearchQuery('')}
                    aria-label='清除搜索'
                    className='p-1 rounded hover:bg-[var(--bg-hover)] transition-colors'
                  >
                    <X className='w-3 h-3 text-[var(--text-muted)]' aria-hidden='true' />
                  </button>
                ) : (
                  <span className='search-kbd' aria-hidden='true'>
                    ⌘K
                  </span>
                )}
              </div>
            </div>
            <button
              onClick={create}
              aria-label='创建新对话'
              className='group/btn flex items-center justify-center w-9 h-9 rounded-lg bg-[var(--brand-primary)] text-white hover:brightness-110 active:scale-95 transition-all duration-200 flex-shrink-0'
            >
              <MessageSquarePlus
                className='w-3.5 h-3.5 transition-transform duration-200'
                aria-hidden='true'
              />
            </button>
          </div>
        </motion.div>
      </AnimatePresence>

      <div
        ref={scrollContainerRef}
        className='flex-1 overflow-y-auto py-2 px-2 scrollbar-auto-hide relative'
      >
        {highlightRect && (
          <motion.div
            className='absolute left-2 right-2 active-item-highlight rounded-lg pointer-events-none z-0'
            animate={{ top: highlightRect.top, height: highlightRect.height }}
            transition={{ type: 'spring', stiffness: 400, damping: 30 }}
          />
        )}
        <div className='relative z-[1]'>
          {conversations.length === 0 ? (
            <div className='text-center py-12 px-4'>
              <div className='w-12 h-12 mx-auto mb-4 rounded-full theme-bg-hover/50 flex items-center justify-center'>
                <MessageSquare className='w-5 h-5 theme-text-muted' />
              </div>
              <p className='theme-text-secondary text-sm mb-1 font-semibold'>开始你的第一次对话</p>
              <p className='text-xs theme-text-muted mb-5'>选择模型，提出问题，获得答案</p>
              <button onClick={create} className='empty-state-cta'>
                <MessageSquarePlus className='w-4 h-4' aria-hidden='true' />
                新建对话
              </button>
            </div>
          ) : filteredGrouped.length === 0 ? (
            <div className='text-center py-12 px-4'>
              <Search className='w-8 h-8 mx-auto mb-3 theme-text-muted' aria-hidden='true' />
              <p className='text-sm theme-text-muted'>未找到匹配的会话</p>
              {searchQuery && (
                <button
                  onClick={() => {
                    setSearchQuery('')
                    searchInputRef.current?.focus()
                  }}
                  className='mt-3 text-xs theme-brand-primary hover:underline'
                >
                  清除搜索
                </button>
              )}
            </div>
          ) : (
            <div role='listbox' id='conversation-list' className='space-y-3'>
              {filteredGrouped.map(({ group, items }) => (
                <div key={group} className='space-y-0.5'>
                  <button
                    onClick={() => toggleGroup(group)}
                    aria-expanded={expandedGroups.has(group)}
                    aria-label={`${expandedGroups.has(group) ? '收起' : '展开'}${group}分组`}
                    className='group/header w-full flex items-center justify-between px-2.5 py-2 min-h-[36px] font-group-title theme-text-secondary hover:theme-bg-hover rounded-md transition-colors duration-200 focus-ring'
                  >
                    <span className='flex items-center gap-1.5'>
                      <ChevronRight
                        className={`w-3.5 h-3.5 transition-transform duration-200 ${
                          expandedGroups.has(group) ? 'rotate-90' : ''
                        }`}
                        aria-hidden='true'
                      />
                      <span className='group-hover/header:theme-text-primary transition-colors'>
                        {group}
                      </span>
                    </span>
                  </button>
                  {expandedGroups.has(group) && (
                    <motion.div
                      key={`${group}-items-${expandedGroups.has(group)}`}
                      variants={{
                        visible: { transition: { staggerChildren: 0.03 } },
                      }}
                      initial='hidden'
                      animate='visible'
                    >
                      {items.map((conversation, idx) => (
                        <motion.div
                          key={conversation.id}
                          variants={{
                            hidden: { opacity: 0, y: 3 },
                            visible: { opacity: 1, y: 0 },
                          }}
                          transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                        >
                          <ConversationItem
                            conversation={conversation}
                            isActive={activeConversation?.id === conversation.id}
                            isStreaming={getStreamingState(conversation.id).isStreaming}
                            isSummarizing={getSummarizingState(conversation.id)}
                            hasNewReply={getHasNewReply(conversation.id)}
                            onClick={() => {
                              resetNewReply(conversation.id)
                              select(conversation)
                              onConversationClick?.()
                            }}
                            onDelete={() => handleDelete(conversation.id, conversation.title)}
                            onUpdate={update}
                            onPin={pin}
                            collapsed={false}
                            index={idx}
                            total={items.length}
                            registerRef={registerItemRef}
                          />
                        </motion.div>
                      ))}
                    </motion.div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default ChatPanel
