import {
  Plus,
  MessageSquare,
  PanelLeftClose,
  PanelLeft,
  User,
  ChevronRight,
  Search,
  X,
} from 'lucide-react'
import { useChat } from '../../../context/ChatContext'
import { useUser } from '../../../context/UserContext'
import { ConversationItem } from './ConversationItem'
import { useState, useEffect, useRef } from 'react'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
  onConversationClick?: () => void
}

export function Sidebar({
  collapsed = false,
  onToggle,
  onDeleteClick,
  onConversationClick,
}: SidebarProps) {
  const [isScrolling, setIsScrolling] = useState(false)
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(
    new Set(['今天', '昨天', '本周', '最近']),
  )
  const [searchQuery, setSearchQuery] = useState('')
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  let scrollTimeout: ReturnType<typeof setTimeout> | null = null

  const {
    conversations,
    activeConversation,
    setActiveConversation,
    createConversation,
    updateConversation,
    pinConversation,
    getStreamingState,
    getHasNewReply,
    resetNewReply,
  } = useChat()

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

  const filteredConversations = conversations.filter((conv) => {
    if (!searchQuery.trim()) return true
    const query = searchQuery.toLowerCase()
    return (
      conv.title.toLowerCase().includes(query) ||
      conv.lastMessage?.toLowerCase().includes(query)
    )
  })

  const groupFilteredConversations = () => {
    const groups: Record<string, any[]> = {}
    const now = new Date()

    filteredConversations.forEach((conv) => {
      if (conv.pinned) {
        if (!groups['置顶']) groups['置顶'] = []
        groups['置顶'].push(conv)
        return
      }

      const date = new Date(conv.createdAt || Date.now())
      const diffDays = Math.floor(
        (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24),
      )

      let group = '最近'
      if (diffDays === 0) group = '今天'
      else if (diffDays === 1) group = '昨天'
      else if (diffDays < 7) group = '本周'

      if (!groups[group]) groups[group] = []
      groups[group].push(conv)
    })

    const order = ['置顶', '今天', '昨天', '本周', '最近']
    return order
      .filter((g) => groups[g])
      .map((g) => ({ group: g, items: groups[g] }))
  }

  const filteredGrouped = groupFilteredConversations()

  const handleScroll = () => {
    setIsScrolling(true)
    if (scrollTimeout) {
      clearTimeout(scrollTimeout)
    }
    scrollTimeout = setTimeout(() => {
      setIsScrolling(false)
    }, 2500)
  }

  useEffect(() => {
    return () => {
      if (scrollTimeout) {
        clearTimeout(scrollTimeout)
      }
    }
  }, [])

  const handleDelete = (id: string, title: string) => {
    onDeleteClick?.(id, title)
  }

  const groupConversations = () => {
    const groups: Record<string, any[]> = {}
    const now = new Date()

    conversations.forEach((conv) => {
      if (conv.pinned) {
        if (!groups['置顶']) groups['置顶'] = []
        groups['置顶'].push(conv)
        return
      }

      const date = new Date(conv.createdAt || Date.now())
      const diffDays = Math.floor(
        (now.getTime() - date.getTime()) / (1000 * 60 * 60 * 24),
      )

      let group = '最近'
      if (diffDays === 0) group = '今天'
      else if (diffDays === 1) group = '昨天'
      else if (diffDays < 7) group = '本周'

      if (!groups[group]) groups[group] = []
      groups[group].push(conv)
    })

    const order = ['置顶', '今天', '昨天', '本周', '最近']
    return order
      .filter((g) => groups[g])
      .map((g) => ({ group: g, items: groups[g] }))
  }

  const grouped = groupConversations()

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className={`p-4 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <div
          className={`mb-4 ${collapsed ? 'flex flex-col items-center' : 'flex items-center justify-between'}`}
        >
          <div
            className={`flex items-center gap-3 ${collapsed ? 'flex flex-col' : ''}`}
          >
            <div className="w-9 h-9 rounded-lg theme-brand-primary flex items-center justify-center shadow-sm overflow-hidden">
              <img
                src="/kchat-icon.svg"
                alt="KChat"
                className="w-full h-full object-contain"
              />
            </div>
            {!collapsed && (
              <div>
                <h1 className="text-base font-semibold theme-text-primary tracking-tight">
                  KChat
                </h1>
                <p className="text-[11px] theme-text-muted uppercase tracking-wider font-medium">
                  Productivity AI
                </p>
              </div>
            )}
          </div>
          {!collapsed && onToggle && (
            <button
              onClick={onToggle}
              className="p-1.5 rounded-md hover:theme-bg-hover transition-colors theme-text-muted hover:theme-text-secondary"
              title="收起侧边栏"
            >
              <PanelLeftClose className="w-5 h-5" />
            </button>
          )}
        </div>

        {!collapsed && (
          <div className="relative mt-3">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4.5 h-4.5 theme-text-muted" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="搜索会话..."
              className="w-full pl-10 pr-10 py-2.5 theme-bg-input border theme-border-primary rounded-lg theme-text-primary placeholder-theme-text-placeholder text-sm focus:outline-none focus:border-[var(--accent-sky)]/50"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 p-0.5 rounded hover:theme-bg-hover transition-colors"
              >
                <X className="w-3.5 h-3.5 theme-text-muted" />
              </button>
            )}
          </div>
        )}

        <button
          onClick={createConversation}
          className={`flex items-center justify-center gap-2.5 transition-all duration-200 font-medium ${
            collapsed
              ? 'w-10 h-10 theme-bg-hover/50 theme-text-secondary hover:theme-bg-hover hover:theme-text-primary hover:scale-110 rounded-full mt-4'
              : 'w-full theme-bg-card hover:theme-bg-hover theme-text-secondary hover:theme-text-primary transition-transform active:scale-[0.98] px-4 py-2.5 rounded-lg border theme-border-primary hover:border-primary-500/30 mt-4 text-sm'
          }`}
          title={collapsed ? '新对话' : undefined}
        >
          <Plus className={`${collapsed ? 'w-5 h-5' : 'w-4.5 h-4.5'}`} />
          {!collapsed && <span>新对话</span>}
        </button>

        {collapsed && onToggle && (
          <button
            onClick={onToggle}
            className="mt-3 w-10 h-10 flex items-center justify-center rounded-full theme-bg-hover/50 hover:theme-bg-hover transition-all theme-text-secondary hover:theme-text-primary"
            title="展开侧边栏"
          >
            <PanelLeft className="w-5 h-5" />
          </button>
        )}
      </div>

      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className={`flex-1 overflow-y-auto py-3 px-2 scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
      >
        {conversations.length === 0 ? (
          <div
            className={`text-center py-12 px-4 ${collapsed ? 'flex flex-col items-center' : ''}`}
          >
            <div className="w-12 h-12 mx-auto mb-3 rounded-full theme-bg-hover/50 flex items-center justify-center">
              <MessageSquare className="w-6 h-6 theme-text-muted" />
            </div>
            {!collapsed && (
              <>
                <p className="theme-text-secondary text-sm mb-1 font-medium">
                  暂无对话
                </p>
                <p className="text-xs theme-text-muted">点击上方按钮开始</p>
              </>
            )}
          </div>
        ) : (
          <div className="space-y-6">
            {filteredGrouped.map(({ group, items }) => (
              <div key={group} className="space-y-1">
                {!collapsed && (
                  <button
                    onClick={() => toggleGroup(group)}
                    className="w-full flex items-center justify-between px-2.5 py-2 text-xs font-semibold theme-text-muted uppercase tracking-widest hover:theme-bg-hover rounded-md transition-colors"
                  >
                    <span className="flex items-center gap-2">
                      <ChevronRight
                        className={`w-3.5 h-3.5 transition-transform duration-200 ${
                          expandedGroups.has(group) ? 'rotate-90' : ''
                        }`}
                      />
                      {group}
                    </span>
                    <span className="text-[11px] opacity-60">
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
                      isStreaming={
                        getStreamingState(conversation.id).isStreaming
                      }
                      hasNewReply={getHasNewReply(conversation.id)}
                      onClick={() => {
                        resetNewReply(conversation.id)
                        setActiveConversation(conversation)
                        onConversationClick?.()
                      }}
                      onDelete={() =>
                        handleDelete(conversation.id, conversation.title)
                      }
                      onUpdate={updateConversation}
                      onPin={pinConversation}
                      collapsed={collapsed}
                    />
                  ))}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className={`p-3 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <div className="w-full">
          <div
            className={`w-full flex items-center gap-2 ${collapsed ? 'justify-center' : ''}`}
          >
            <div className="w-7 h-7 rounded-full theme-bg-hover flex-shrink-0 flex items-center justify-center overflow-hidden">
              {profile?.avatar ? (
                <img
                  src={profile.avatar}
                  alt="Avatar"
                  className="w-full h-full object-cover"
                />
              ) : (
                <User className="w-4 h-4 theme-text-secondary" />
              )}
            </div>
            {!collapsed && (
              <div className="flex-1 min-w-0 space-y-1 text-left">
                <p className="text-sm font-medium theme-text-primary truncate leading-tight">
                  {profile?.nickname || '用户'}
                </p>
                <p className="text-[11px] theme-text-muted truncate leading-tight">
                  Premium Plan
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
