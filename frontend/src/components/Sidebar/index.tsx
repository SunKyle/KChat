import { Plus, MessageSquare, Bot } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { ConversationItem } from './ConversationItem';

interface SidebarProps {
  collapsed?: boolean;
}

export function Sidebar({ collapsed = false }: SidebarProps) {
  const { conversations, activeConversation, setActiveConversation, createConversation, deleteConversation, updateConversation } = useChat();

  return (
    <div className="bg-slate-800/30 border-r border-slate-700/30 flex flex-col h-full overflow-hidden">
      <div className={`p-3 border-b border-slate-700/30 ${collapsed ? 'flex flex-col items-center' : ''}`}>
        <div className={`mb-3 ${collapsed ? 'flex flex-col items-center' : 'flex items-center gap-3'}`}>
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
        
        <button
          onClick={createConversation}
          className={`flex items-center justify-center gap-2 px-3 py-2 bg-primary-500 hover:bg-primary-600 text-white rounded-lg transition-all font-medium ${collapsed ? 'w-full' : 'w-full'}`}
          title={collapsed ? '新对话' : undefined}
        >
          <Plus className="w-4 h-4" />
          {!collapsed && <span>新对话</span>}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
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
                onDelete={() => deleteConversation(conversation.id)}
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
