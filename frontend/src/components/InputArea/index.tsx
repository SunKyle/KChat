import { useState, useEffect } from 'react';
import type { KeyboardEvent } from 'react';
import { Send, MessageCircle, Trash2, Plus } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function InputArea() {
  const [input, setInput] = useState('');
  const { sendMessage, streamingState, activeConversation, createConversation } = useChat();

  const charCount = input.length;
  const maxChars = 2000;

  useEffect(() => {
    const textarea = document.querySelector('textarea');
    if (textarea) {
      textarea.style.height = 'auto';
      textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px';
    }
  }, [input]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleSend = async () => {
    if (!input.trim() || streamingState.isStreaming || charCount > maxChars) return;
    
    if (!activeConversation) {
      await createConversation();
    }
    
    sendMessage(input);
    setInput('');
  };

  const handleClear = () => {
    setInput('');
  };

  return (
    <div className="border-t border-slate-700/50 bg-slate-800/50 backdrop-blur-sm p-4">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-end gap-3 bg-slate-700/50 rounded-2xl border border-slate-600/50 overflow-hidden shadow-lg">
          <div className="flex-1 relative">
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder="输入消息... Shift + Enter 换行"
              className="w-full resize-none bg-transparent px-5 py-4 text-slate-100 placeholder-slate-500 focus:outline-none min-h-[60px] max-h-[200px] overflow-y-auto text-base"
            />
          </div>
          <div className="flex items-center gap-2 p-2">
            {input && !streamingState.isStreaming && (
              <button
                onClick={handleClear}
                className="p-2 rounded-lg hover:bg-slate-600 transition-colors text-slate-400 hover:text-slate-300"
                title="清空"
              >
                <Trash2 className="w-5 h-5" />
              </button>
            )}
            <button
              onClick={handleSend}
              disabled={!input.trim() || streamingState.isStreaming || charCount > maxChars}
              className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-medium transition-all ${
                input.trim() && !streamingState.isStreaming && charCount <= maxChars
                  ? 'bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white shadow-md hover:shadow-lg active:scale-95'
                  : 'bg-slate-600 text-slate-400 cursor-not-allowed'
              }`}
            >
              {streamingState.isStreaming ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>发送中</span>
                </>
              ) : (
                <>
                  <Send className="w-4 h-4" />
                  <span>发送</span>
                </>
              )}
            </button>
          </div>
        </div>
        
        <div className="flex items-center justify-between mt-3 text-xs text-slate-500">
          <div className="flex items-center gap-4">
            <span className="flex items-center gap-1.5">
              <MessageCircle className="w-3.5 h-3.5" />
              Enter 发送
            </span>
            <span>Shift + Enter 换行</span>
          </div>
          <div className="flex items-center gap-4">
            <span className={`${charCount > maxChars ? 'text-red-400' : ''}`}>
              {charCount}/{maxChars}
            </span>
            <span>
              {streamingState.isStreaming ? (
                <span className="text-primary-400 flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-primary-400 animate-pulse" />
                  AI 正在回复...
                </span>
              ) : (
                <span className="text-green-400">准备就绪</span>
              )}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
