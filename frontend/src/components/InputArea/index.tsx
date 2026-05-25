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
    <div className="p-6 pb-10">
      <div className="max-w-[800px] mx-auto relative group">
        {/* 
          极简优雅设计：
          1. 移除粗重的阴影，改为细腻的 border 和 subtle-shadow
          2. 增加背景透明度，强化玻璃感
          3. 优化圆角，使其更接近 iOS 的连续曲率 (Continuous Corners)
        */}
        <div className={`flex items-end gap-2 bg-white/[0.03] backdrop-blur-2xl rounded-[24px] border border-white/10 micro-transition focus-within:border-sky-500/30 focus-within:bg-white/[0.05] transition-all duration-300`}>
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder={streamingState.isStreaming ? "AI 正在思考中..." : "输入消息..."}
              className="w-full resize-none bg-transparent px-6 py-4 text-[#E5E7EB] placeholder-slate-500 focus:outline-none min-h-[60px] max-h-[200px] overflow-y-auto text-base leading-relaxed"
            />
          </div>
          
          <div className="flex items-center gap-2 p-3">
            {input && !streamingState.isStreaming && (
              <button
                onClick={handleClear}
                className="p-2 rounded-full text-slate-500 hover:text-slate-300 hover:bg-white/5 micro-transition"
                title="清空"
              >
                <X className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={streamingState.isStreaming ? stopStreaming : handleSend}
              disabled={!input.trim() && !streamingState.isStreaming}
              className={`flex items-center justify-center w-10 h-10 rounded-full micro-transition ${
                streamingState.isStreaming
                  ? 'bg-red-500/20 text-red-400 hover:bg-red-500/30 cursor-pointer'
                  : input.trim() && charCount <= maxChars
                  ? 'bg-sky-500 text-white shadow-lg shadow-sky-500/30 hover:bg-sky-400 active:scale-95 cursor-pointer'
                  : 'bg-slate-700/50 text-slate-500 cursor-not-allowed'
              }`}
              title={streamingState.isStreaming ? "中断回答" : "发送消息"}
            >
              {streamingState.isStreaming ? (
                <Square className="w-3.5 h-3.5" fill="currentColor" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>
        
        {/* 极简字数统计：仅在有内容时以淡色显示，不遮挡视觉 */}
        {charCount > 0 && (
          <div className="absolute -top-6 right-0 text-[10px] font-medium text-slate-600 uppercase tracking-widest micro-transition">
            {charCount} <span className="opacity-50">/</span> {maxChars}
          </div>
        )}
      </div>
    </div>
  );
}
