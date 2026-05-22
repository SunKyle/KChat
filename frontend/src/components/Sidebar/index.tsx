import { Plus, MessageSquare } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { ConversationItem } from './ConversationItem';

export function Sidebar() {
  const { conversations, activeConversation, setActiveConversation, createConversation, deleteConversation } = useChat();

  return (
    <div className="w-72 bg-slate-800 border-r border-slate-700 flex flex-col h-full">
      <div className="p-4 border-b border-slate-700">
        <div className="flex items-center gap-2 mb-4">
          <MessageSquare className="w-6 h-6 text-primary-400" />
          <h1 className="text-lg font-semibold text-white">KChat</h1>
        </div>
        
        <button
          onClick={createConversation}
          className="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-primary-500 hover:bg-primary-600 text-white rounded-lg transition-colors font-medium"
        >
          <Plus className="w-5 h-5" />
          新对话
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {conversations.length === 0 ? (
          <div className="text-center py-8 text-slate-500">
            <MessageSquare className="w-12 h-12 mx-auto mb-3 opacity-50" />
            <p>暂无对话</p>
            <p className="text-sm">点击上方按钮创建新对话</p>
          </div>
        ) : (
          <div className="space-y-1">
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

      <div className="p-3 border-t border-slate-700 text-center">
        <p className="text-xs text-slate-500">
          连接到本地 Ollama
        </p>
      </div>
    </div>
  );
}
