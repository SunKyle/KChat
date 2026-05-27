import {
  Plus,
  MessageSquare,
  Bot,
  PanelLeftClose,
  PanelLeft,
} from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { ConversationItem } from './ConversationItem'
import { useState, useEffect, useRef } from 'react'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
}

export function Sidebar({
  collapsed = false,
  onToggle,
  onDeleteClick,
}: SidebarProps) {
  const [isScrolling, setIsScrolling] = useState(false)
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  let scrollTimeout: ReturnType<typeof setTimeout> | null = null

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

  const {
    conversations,
    activeConversation,
    setActiveConversation,
    createConversation,
    updateConversation,
    getStreamingState,
    getHasNewReply,
    resetNewReply,
  } = useChat()

  const handleDelete = (id: string, title: string) => {
    onDeleteClick?.(id, title)
  }

  // 对对话进行时间分组
  const groupConversations = () => {
    const groups: Record<string, any[]> = {}
    const now = new Date()

    conversations.forEach((conv) => {
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

    const order = ['今天', '昨天', '本周', '最近']
    return order
      .filter((g) => groups[g])
      .map((g) => ({ group: g, items: groups[g] }))
  }

  const grouped = groupConversations()

  return (
    <div className="bg-[#111827] border-r border-white/5 flex flex-col h-full overflow-hidden">
      <div
        className={`p-4 border-b border-white/5 ${collapsed ? 'flex flex-col items-center' : ''}`}
      >
        <div
          className={`mb-4 ${collapsed ? 'flex flex-col items-center' : 'flex items-center justify-between'}`}
        >
          <div
            className={`flex items-center gap-3 ${collapsed ? 'flex flex-col' : ''}`}
          >
            <div className="w-8 h-8 rounded-lg bg-[#0EA5E9] flex items-center justify-center shadow-sm">
              <Bot className="w-5 h-5 text-white" />
            </div>
            {!collapsed && (
              <div>
                <h1 className="text-sm font-semibold text-[#E5E7EB] tracking-tight">
                  KChat
                </h1>
                <p className="text-[10px] text-slate-500 uppercase tracking-wider font-medium">
                  Productivity AI
                </p>
              </div>
            )}
          </div>
          {!collapsed && onToggle && (
            <button
              onClick={onToggle}
              className="p-1.5 rounded-md hover:bg-white/5 transition-colors text-slate-500 hover:text-slate-300"
              title="收起侧边栏"
            >
              <PanelLeftClose className="w-5 h-5" />
            </button>
          )}
        </div>

        <button
          onClick={createConversation}
          className={`flex items-center justify-center gap-2 transition-all duration-200 font-medium text-sm ${
            collapsed
              ? 'w-10 h-10 bg-white/5 text-slate-400 hover:bg-white/10 hover:text-slate-200 rounded-full'
              : 'w-full bg-[#E5E7EB] text-[#111827] hover:bg-white transition-transform active:scale-[0.98] px-3 py-2 rounded-lg'
          }`}
          title={collapsed ? '新对话' : undefined}
        >
          <Plus className={`${collapsed ? 'w-5 h-5' : 'w-4 h-4'}`} />
          {!collapsed && <span>新对话</span>}
        </button>

        {collapsed && onToggle && (
          <button
            onClick={onToggle}
            className="mt-3 w-10 h-10 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 transition-all text-slate-400 hover:text-slate-200"
            title="展开侧边栏"
          >
            <PanelLeft className="w-5 h-5" />
          </button>
        )}
      </div>

      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className={`flex-1 overflow-y-auto py-4 px-3 scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
      >
        {conversations.length === 0 ? (
          <div
            className={`text-center py-12 px-4 ${collapsed ? 'flex flex-col items-center' : ''}`}
          >
            <div className="w-12 h-12 mx-auto mb-3 rounded-full bg-white/5 flex items-center justify-center">
              <MessageSquare className="w-6 h-6 text-slate-600" />
            </div>
            {!collapsed && (
              <>
                <p className="text-slate-400 text-sm mb-1 font-medium">
                  暂无对话
                </p>
                <p className="text-xs text-slate-500">点击上方按钮开始</p>
              </>
            )}
          </div>
        ) : (
          <div className="space-y-6">
            {grouped.map(({ group, items }) => (
              <div key={group} className="space-y-1">
                {!collapsed && (
                  <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-widest mb-2 px-2">
                    {group}
                  </div>
                )}
                {items.map((conversation) => (
                  <ConversationItem
                    key={conversation.id}
                    conversation={conversation}
                    isActive={activeConversation?.id === conversation.id}
                    isStreaming={getStreamingState(conversation.id).isStreaming}
                    hasNewReply={getHasNewReply(conversation.id)}
                    onClick={() => {
                      resetNewReply(conversation.id)
                      setActiveConversation(conversation)
                    }}
                    onDelete={() =>
                      handleDelete(conversation.id, conversation.title)
                    }
                    onUpdate={updateConversation}
                    collapsed={collapsed}
                  />
                ))}
              </div>
            ))}
          </div>
        )}
      </div>

      <div
        className={`p-4 border-t border-white/5 ${collapsed ? 'flex flex-col items-center' : ''}`}
      >
        <div
          className={`flex items-center gap-3 ${collapsed ? 'justify-center' : ''}`}
        >
          <div className="w-8 h-8 rounded-full bg-slate-700 flex-shrink-0" />
          {!collapsed && (
            <div className="flex-1 min-w-0">
              <p className="text-xs font-medium text-slate-300 truncate">
                Sun Xiaokai
              </p>
              <p className="text-[10px] text-slate-500 truncate">
                Premium Plan
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
