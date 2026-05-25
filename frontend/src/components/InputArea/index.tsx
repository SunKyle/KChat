import { useState, useEffect, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { Send, Square, X } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function InputArea() {
  const [input, setInput] = useState('');
  const { sendMessage, streamingState, activeConversation, createConversation, stopStreaming } = useChat();
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const charCount = input.length;
  const maxChars = 2000;

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px';
    }
  }, [input]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (streamingState.isStreaming) {
        stopStreaming();
      } else {
        handleSend();
      }
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
    <div className="p-6">
      <div className="max-w-[800px] mx-auto relative">
        <div className="flex items-end gap-2 bg-[#1E293B] backdrop-blur-xl rounded-2xl border border-white/10 shadow-2xl overflow-hidden transition-all duration-200 focus-within:border-sky-500/50 focus-within:ring-1 focus-within:ring-sky-500/20">
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder={streamingState.isStreaming ? "AI 正在思考中..." : "输入消息，Shift+Enter 换行..."}
              className="w-full resize-none bg-transparent px-5 py-4 text-[#E5E7EB] placeholder-slate-500 focus:outline-none min-h-[64px] max-h-[200px] overflow-y-auto text-base leading-relaxed"
            />
          </div>
          
          <div className="flex items-center gap-2 p-3">
            {input && !streamingState.isStreaming && (
              <button
                onClick={handleClear}
                className="p-2 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-white/5 transition-all"
                title="清空"
              >
                <X className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={streamingState.isStreaming ? stopStreaming : handleSend}
              disabled={!input.trim() && !streamingState.isStreaming}
              className={`flex items-center justify-center w-10 h-10 rounded-xl transition-all duration-200 ${
                streamingState.isStreaming
                  ? 'bg-red-500/20 text-red-400 hover:bg-red-500/30 cursor-pointer'
                  : input.trim() && charCount <= maxChars
                  ? 'bg-[#0EA5E9] text-white shadow-lg shadow-sky-500/20 hover:bg-sky-400 active:scale-95 cursor-pointer'
                  : 'bg-slate-700 text-slate-500 cursor-not-allowed'
              }`}
              title={streamingState.isStreaming ? "中断回答" : "发送消息"}
            >
              {streamingState.isStreaming ? (
                <Square className="w-4 h-4" fill="currentColor" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>
        
        {/* 字数提示 - 极简设计 */}
        {charCount > 0 && (
          <div className="absolute -top-6 right-0 text-[10px] font-medium text-slate-500 uppercase tracking-tighter">
            {charCount} / {maxChars}
          </div>
        )}
      </div>
    </div>
  );
}
