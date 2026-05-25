import { Plus, MessageSquare, Bot, PanelLeftClose, PanelLeft } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { ConversationItem } from './ConversationItem';
import { useState, useEffect, useRef } from 'react';

interface SidebarProps {
  collapsed?: boolean;
  onToggle?: () => void;
  onDeleteClick?: (id: string, title: string) => void;
}

export function Sidebar({ collapsed = false, onToggle, onDeleteClick }: SidebarProps) {
  const [isScrolling, setIsScrolling] = useState(false);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  let scrollTimeout: ReturnType<typeof setTimeout> | null = null;

  const handleScroll = () => {
    setIsScrolling(true);
    if (scrollTimeout) {
      clearTimeout(scrollTimeout);
    }
    scrollTimeout = setTimeout(() => {
      setIsScrolling(false);
    }, 2500);
  };

  useEffect(() => {
    return () => {
      if (scrollTimeout) {
        clearTimeout(scrollTimeout);
      }
    };
  }, []);
  const { conversations, activeConversation, setActiveConversation, createConversation, updateConversation } = useChat();

  const handleDelete = (id: string, title: string) => {
    onDeleteClick?.(id, title);
  };

  return (
    <div className="bg-slate-800/30 border-r border-slate-700/30 flex flex-col h-full overflow-hidden">
      <div className={`p-3 border-b border-slate-700/30 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <div className={`mb-3 ${collapsed ? 'flex flex-col items-center' : 'flex items-center justify-between'}`}>
          <div className={`flex items-center gap-3 ${collapsed ? 'flex flex-col' : ''}`}>
            <div className="w-9 h-9 rounded-lg bg-primary-500/90 flex items-center justify-center">
              <Bot className="w-5 h-5 text-white" />
            </div>
            {!collapsed && (
              <div>
                <h1 className="text-base font-semibold text-white">KChat</h1>
                <p className="text-xs text-slate-400">AI 对话</p>
              </div>
            )}
          </div>
          {!collapsed && onToggle && (
            <button
              onClick={onToggle}
              className="p-1.5 rounded-lg hover:bg-slate-700/50 transition-colors"
              title="收起侧边栏"
            >
              <PanelLeftClose className="w-4 h-4 text-slate-400 hover:text-white transition-colors" />
            </button>
          )}
        </div>
        
        <button
          onClick={createConversation}
          className={`flex items-center justify-center gap-2 px-3 py-2 bg-primary-500 hover:bg-primary-600 text-white rounded-lg transition-all font-medium ${collapsed ? 'w-full' : 'w-full'}`}
          title={collapsed ? '新对话' : undefined}
        >
          <Plus className="w-4 h-4" />
          {!collapsed && <span>新对话</span>}
        </button>
        
        {collapsed && onToggle && (
          <button
            onClick={onToggle}
            className="mt-2 p-2 rounded-lg hover:bg-slate-700/50 transition-colors"
            title="展开侧边栏"
          >
            <PanelLeft className="w-4 h-4 text-slate-400 hover:text-white transition-colors" />
          </button>
        )}
      </div>

      <div 
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className={`flex-1 overflow-y-auto p-2 scrollbar-auto-hide ${isScrolling ? 'scrolling' : ''}`}
      >
        {conversations.length === 0 ? (
          <div className={`text-center py-8 px-4 ${collapsed ? 'flex flex-col items-center' : ''}`}>
            <div className="w-10 h-10 mx-auto mb-3 rounded-full bg-slate-700/30 flex items-center justify-center">
              <MessageSquare className="w-5 h-5 text-slate-500" />
            </div>
            {!collapsed && (
              <>
                <p className="text-slate-400 text-sm mb-1">暂无对话</p>
                <p className="text-xs text-slate-500">点击上方按钮开始</p>
              </>
            )}
          </div>
        ) : (
          <div className="space-y-0.5">
            {!collapsed && (
              <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2 px-2">
                历史对话
              </div>
            )}
            {conversations.map((conversation) => (
              <ConversationItem
                key={conversation.id}
                conversation={conversation}
                isActive={activeConversation?.id === conversation.id}
                onClick={() => setActiveConversation(conversation)}
                onDelete={() => handleDelete(conversation.id, conversation.title)}
                onUpdate={updateConversation}
                collapsed={collapsed}
              />
            ))}
          </div>
        )}
      </div>

      <div className={`p-3 border-t border-slate-700/30 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <div className={`flex items-center gap-2 ${collapsed ? 'justify-center' : ''}`}>
          <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          {!collapsed && (
            <p className="text-xs text-slate-400">Ollama 已连接</p>
          )}
        </div>
      </div>
    </div>
  );
}
