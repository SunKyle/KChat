import {
  MessageSquare,
  MessageSquarePlus,
  User,
  ChevronRight,
  Search,
  X,
  Settings,
  Crown,
  FileText,
  ListTodo,
} from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { useUser } from '../../context/UserContext'
import { useConversation } from '../../hooks/useConversation'
import { ConversationItem } from './ConversationItem'
import { useState, useEffect, useRef, useMemo, useCallback } from 'react'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
  onConversationClick?: () => void
  onNoteTodoClick?: () => void
}

export function Sidebar({
  collapsed = false,
  onToggle: _onToggle,
  onDeleteClick,
  onConversationClick,
  onNoteTodoClick,
}: SidebarProps) {
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

  const [isScrolling, setIsScrolling] = useState(false)
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(getInitialExpandedGroups)
  const [searchQuery, setSearchQuery] = useState('')
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const scrollTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const { conversations, activeConversation, getStreamingState, getHasNewReply, resetNewReply } =
    useChat()

  const { create, update, pin, select } = useConversation()

  const { profile } = useUser()

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

  // 统一的分组逻辑
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

  // 分组后的会话列表（使用useMemo优化）
  const filteredGrouped = useMemo(() => {
    return groupConversationsByList(filteredConversations)
  }, [filteredConversations, groupConversationsByList])

  // 优化滚动监听
  useEffect(() => {
    const container = scrollContainerRef.current
    if (!container) return

    const handleScrollEvent = () => {
      setIsScrolling(true)
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current)
      }
      scrollTimeoutRef.current = setTimeout(() => {
        setIsScrolling(false)
      }, 2500)
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

      // 侧边栏折叠时无搜索框可用
      if (collapsed) return

      e.preventDefault()
      searchInputRef.current?.focus()
      searchInputRef.current?.select()
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [collapsed])

  const handleDelete = (id: string, title: string) => {
    onDeleteClick?.(id, title)
  }

  return (
    <div role='navigation' aria-label='会话导航' className='flex flex-col h-full relative'>
      <div className={`flex flex-col h-full overflow-hidden`}>
        <div className={`px-4 pt-3 pb-2 ${collapsed ? 'flex flex-col items-center pb-3' : ''}`}>
          <div
            className={`group/logo ${collapsed ? 'mb-2' : 'mb-2'} ${collapsed ? 'flex flex-col items-center' : 'flex items-center justify-between w-full'}`}
          >
            <div className='flex items-center gap-2'>
              <div className='relative flex-shrink-0'>
                <div
                  className={`absolute inset-0 rounded-xl bg-gradient-to-br from-sky-300 to-blue-400 blur-md transition-opacity duration-300 ${
                    collapsed ? 'opacity-25' : 'opacity-30 group-hover/logo:opacity-50'
                  }`}
                />
                <img
                  src='/kchat-icon.svg'
                  alt='KChat'
                  className={`relative ${collapsed ? 'w-6 h-6' : 'w-7 h-7'} object-contain transition-transform duration-300 ease-out group-hover/logo:rotate-12`}
                />
              </div>
              {!collapsed && (
                <h1 className='font-logo bg-gradient-to-r from-sky-400 via-blue-500 to-indigo-500 bg-clip-text text-transparent leading-none sidebar-content-enter tracking-tight'>
                  KChat
                </h1>
              )}
            </div>
            {!collapsed && (
              <button
                onClick={_onToggle}
                aria-label='收起侧边栏'
                className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
              >
                <ChevronRight className='w-4 h-4 rotate-180' aria-hidden='true' />
              </button>
            )}
          </div>
        </div>

        {!collapsed && (
          <div className='flex items-center gap-2 mt-2 px-4 sidebar-search-enter'>
            <div className='relative flex-1 group/search'>
              <Search
                className={`absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 transition-colors duration-200 ${
                  searchQuery ? 'theme-brand-primary' : 'theme-text-muted'
                }`}
                aria-hidden='true'
              />
              <input
                ref={searchInputRef}
                type='text'
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
                className='w-full pl-9 pr-16 py-2 bg-[var(--bg-glass)] backdrop-blur-md border border-[var(--bg-glass-border)] rounded-xl font-secondary text-[13px] theme-text-primary placeholder-theme-text-placeholder focus:outline-none focus:bg-[var(--bg-glass-hover)] focus:border-[var(--accent-sky)]/40 focus:ring-2 focus:ring-sky-400/15 focus:shadow-sm focus:shadow-sky-500/10 transition-all duration-200 shadow-[0_1px_4px_var(--shadow-color-secondary),0_0_0_0.5px_var(--border-secondary)]'
              />
              {!searchQuery && (
                <kbd className='absolute right-2.5 top-1/2 -translate-y-1/2 hidden sm:inline-flex items-center gap-0.5 px-1.5 h-5 rounded-md text-[10px] font-mono font-medium leading-none bg-[var(--bg-glass)] theme-brand-primary/70 border border-[var(--bg-glass-border)] backdrop-blur-md shadow-[0_1px_2px_rgba(14,165,233,0.05)] group-hover/search:opacity-0 transition-opacity duration-200'>
                  ⌘K
                </kbd>
              )}
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  aria-label='清除搜索'
                  className='absolute right-2.5 top-1/2 -translate-y-1/2 p-1.5 rounded-lg hover:bg-[var(--bg-hover)] hover:theme-brand-primary transition-all duration-300 focus-ring hover:rotate-90'
                >
                  <X className='w-3.5 h-3.5 theme-text-muted' aria-hidden='true' />
                </button>
              )}
            </div>
            <button
              onClick={create}
              aria-label='创建新对话'
              className='flex items-center justify-center w-9 h-9 rounded-xl bg-[var(--bg-glass)] backdrop-blur-md theme-brand-primary border border-[var(--bg-glass-border)] shadow-[0_1px_4px_var(--shadow-color-secondary),0_0_0_0.5px_var(--border-secondary)] hover:bg-[var(--brand-primary)] hover:text-white hover:border-[var(--brand-primary)] hover:backdrop-blur-none hover:shadow-md hover:shadow-[var(--brand-primary)]/20 active:scale-[0.95] transition-all duration-200 focus-ring flex-shrink-0'
            >
              <MessageSquarePlus className='w-4 h-4' aria-hidden='true' />
            </button>
          </div>
        )}

        {collapsed && <div className='mx-3 divider' />}

        <div
          ref={scrollContainerRef}
          className={`flex-1 overflow-y-auto py-2 px-2 scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
        >
          {conversations.length === 0 ? (
            <div
              className={`text-center py-12 px-4 ${collapsed ? 'flex flex-col items-center' : ''}`}
            >
              <div className='w-12 h-12 mx-auto mb-3 rounded-full theme-bg-hover/50 flex items-center justify-center'>
                <MessageSquare className='w-5 h-5 theme-text-muted' />
              </div>
              {!collapsed && (
                <>
                  <p className='theme-text-secondary text-sm mb-1 font-medium'>暂无对话</p>
                  <p className='text-xs theme-text-muted'>点击上方按钮开始</p>
                </>
              )}
            </div>
          ) : filteredGrouped.length === 0 ? (
            <div className='text-center py-12 px-4'>
              <Search className='w-8 h-8 mx-auto mb-3 theme-text-muted' aria-hidden='true' />
              <p className='text-sm theme-text-muted'>未找到匹配的会话</p>
            </div>
          ) : (
            <div role='list' className='space-y-2'>
              {filteredGrouped.map(({ group, items }) => (
                <div key={group} className='space-y-1'>
                  {!collapsed && (
                    <button
                      onClick={() => toggleGroup(group)}
                      aria-expanded={expandedGroups.has(group)}
                      aria-label={`${expandedGroups.has(group) ? '收起' : '展开'}${group}分组`}
                      className='group/header w-full flex items-center justify-between px-2.5 py-1.5 min-h-[36px] font-group-title theme-text-muted hover:theme-bg-hover rounded-md transition-colors duration-200 focus-ring sidebar-content-enter'
                    >
                      <span className='flex items-center gap-1.5'>
                        <ChevronRight
                          className={`w-3.5 h-3.5 transition-transform duration-200 ${
                            expandedGroups.has(group) ? 'rotate-90' : ''
                          }`}
                          aria-hidden='true'
                        />
                        <span className='group-hover/header:theme-text-secondary transition-colors'>
                          {group}
                        </span>
                      </span>
                      <span className='inline-flex items-center justify-center min-w-[22px] h-5 px-1.5 text-[10px] font-semibold rounded-full bg-[var(--bg-hover)] theme-text-muted group-hover/header:bg-[var(--brand-primary)]/10 group-hover/header:theme-brand-primary transition-all duration-200'>
                        {items.length}
                      </span>
                    </button>
                  )}
                  {(collapsed || expandedGroups.has(group)) &&
                    items.map((conversation) => (
                      <ConversationItem
                        key={conversation.id}
                        conversation={conversation}
                        isActive={activeConversation?.id === conversation.id}
                        isStreaming={getStreamingState(conversation.id).isStreaming}
                        hasNewReply={getHasNewReply(conversation.id)}
                        onClick={() => {
                          resetNewReply(conversation.id)
                          select(conversation)
                          onConversationClick?.()
                        }}
                        onDelete={() => handleDelete(conversation.id, conversation.title)}
                        onUpdate={update}
                        onPin={pin}
                        collapsed={collapsed}
                      />
                    ))}
                </div>
              ))}
            </div>
          )}
        </div>

        {collapsed && <div className='mx-3 divider' />}

        {!collapsed && (
          <div className='px-4 pb-3'>
            <button
              onClick={onNoteTodoClick}
              aria-label='笔记和待办'
              className='w-full flex items-center gap-3 px-3 py-2.5 rounded-xl bg-[var(--bg-glass)] backdrop-blur-md border border-[var(--bg-glass-border)] hover:bg-[var(--bg-hover)] hover:border-[var(--border-primary)] transition-all duration-200 group'
            >
              <div className='flex items-center justify-center w-8 h-8 rounded-lg bg-gradient-to-br from-blue-100 to-green-100'>
                <FileText className='w-4 h-4 text-blue-600' />
              </div>
              <div className='flex-1 text-left'>
                <p className='font-conversation-name theme-text-primary'>笔记与待办</p>
                <p className='text-xs theme-text-muted'>记录和管理您的任务</p>
              </div>
              <ListTodo className='w-4 h-4 theme-text-muted group-hover:theme-brand-primary transition-colors' />
            </button>
          </div>
        )}

        {collapsed && (
          <button
            onClick={onNoteTodoClick}
            aria-label='笔记和待办'
            className='p-3 mx-3 rounded-xl bg-[var(--bg-glass)] backdrop-blur-md border border-[var(--bg-glass-border)] hover:bg-[var(--bg-hover)] transition-all duration-200'
          >
            <FileText className='w-5 h-5 theme-text-secondary' />
          </button>
        )}

        {collapsed && <div className='mx-3 divider' />}

        <div className={`p-3 ${collapsed ? 'flex flex-col items-center' : ''}`}>
          <div className='w-full'>
            <div
              className={`w-full flex items-center gap-2 ${collapsed ? 'justify-center cursor-pointer hover:scale-105 transition-transform duration-200' : ''} ${!collapsed ? 'group rounded-lg px-1 py-1.5 hover:theme-bg-hover cursor-pointer transition-colors' : ''}`}
            >
              <div
                className={`rounded-full flex-shrink-0 flex items-center justify-center overflow-hidden ${collapsed ? 'w-9 h-9 hover:theme-bg-hover transition-colors' : 'w-8 h-8 theme-bg-hover'}`}
              >
                {profile?.avatar ? (
                  <img src={profile.avatar} alt='Avatar' className='w-full h-full object-cover' />
                ) : (
                  <User className='w-[18px] h-[18px] theme-text-secondary' />
                )}
              </div>
              {!collapsed && (
                <>
                  <div className='flex-1 min-w-0 space-y-1 text-left sidebar-content-enter'>
                    <p className='font-conversation-name theme-text-primary truncate leading-tight'>
                      {profile?.nickname || '用户'}
                    </p>
                    <span
                      className='inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full bg-gradient-to-r from-amber-400/15 via-yellow-400/15 to-amber-500/15 border border-amber-400/30 text-amber-900 dark:text-amber-200 text-[10px] font-semibold leading-none backdrop-blur-sm'
                      title='Premium Plan'
                    >
                      <Crown className='w-2.5 h-2.5' aria-hidden='true' />
                      Premium
                    </span>
                  </div>
                  <Settings
                    className='w-4 h-4 theme-text-muted opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0'
                    aria-hidden='true'
                  />
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
