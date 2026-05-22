import { useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Send, MessageCircle } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function InputArea() {
  const [input, setInput] = useState('');
  const { sendMessage, streamingState, activeConversation, createConversation } = useChat();

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleSend = async () => {
    if (!input.trim() || streamingState.isStreaming) return;
    
    if (!activeConversation) {
      await createConversation();
    }
    
    sendMessage(input);
    setInput('');
  };

  return (
    <div className="border-t border-slate-700 bg-slate-800 p-4">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-end gap-3 bg-slate-700 rounded-xl border border-slate-600 overflow-hidden">
          <div className="flex-1 relative">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder="输入消息...\nShift + Enter 换行"
              className="w-full resize-none bg-transparent px-4 py-3 text-slate-100 placeholder-slate-500 focus:outline-none min-h-[60px] max-h-[200px] overflow-y-auto"
              rows={1}
            />
          </div>
          <div className="p-2">
            <button
              onClick={handleSend}
              disabled={!input.trim() || streamingState.isStreaming}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-lg font-medium transition-all ${
                input.trim() && !streamingState.isStreaming
                  ? 'bg-primary-500 hover:bg-primary-600 text-white'
                  : 'bg-slate-600 text-slate-400 cursor-not-allowed'
              }`}
            >
              {streamingState.isStreaming ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  发送中
                </>
              ) : (
                <>
                  <Send className="w-4 h-4" />
                  发送
                </>
              )}
            </button>
          </div>
        </div>
        
        <div className="flex items-center justify-between mt-2 text-xs text-slate-500">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1">
              <MessageCircle className="w-3 h-3" />
              Enter 发送
            </span>
            <span>Shift + Enter 换行</span>
          </div>
          <span>
            {streamingState.isStreaming ? 'AI 正在回复...' : '准备就绪'}
          </span>
        </div>
      </div>
    </div>
  );
}
