import { Plus, MessageSquare, Bot } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { ConversationItem } from './ConversationItem';

export function Sidebar() {
  const { conversations, activeConversation, setActiveConversation, createConversation, deleteConversation } = useChat();

  return (
    <div className="w-72 bg-slate-800/50 border-r border-slate-700/50 flex flex-col h-full backdrop-blur-sm">
      <div className="p-4 border-b border-slate-700/50">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center shadow-lg">
            <Bot className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white">KChat</h1>
            <p className="text-xs text-slate-400">AI 对话助手</p>
          </div>
        </div>
        
        <button
          onClick={createConversation}
          className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white rounded-lg transition-all font-medium shadow-md hover:shadow-lg active:scale-95"
        >
          <Plus className="w-5 h-5" />
          新对话
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-3 scrollbar-thin scrollbar-thumb-slate-600 scrollbar-track-transparent">
        {conversations.length === 0 ? (
          <div className="text-center py-12 px-4">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-slate-700/50 flex items-center justify-center">
              <MessageSquare className="w-8 h-8 text-slate-500" />
            </div>
            <p className="text-slate-400 font-medium mb-2">暂无对话</p>
            <p className="text-sm text-slate-500">点击上方按钮开始新对话</p>
          </div>
        ) : (
          <div className="space-y-1">
            <div className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2 px-2">
              历史对话
            </div>
            {conversations.map((conversation) => (
              <ConversationItem
                key={conversation.id}
                conversation={conversation}
                isActive={activeConversation?.id === conversation.id}
                onClick={() => setActiveConversation(conversation)}
                onDelete={() => deleteConversation(conversation.id)}
              />
            ))}
          </div>
        )}
      </div>

      <div className="p-4 border-t border-slate-700/50">
        <div className="flex items-center gap-2 px-3 py-2 bg-slate-700/50 rounded-lg">
          <div className="w-2 h-2 rounded-full bg-green-400 animate-pulse" />
          <p className="text-xs text-slate-400">
            已连接到本地 Ollama
          </p>
        </div>
      </div>
    </div>
  );
}
