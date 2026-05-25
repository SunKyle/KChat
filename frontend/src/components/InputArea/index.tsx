import { useState, useEffect, useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { Send, X } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function InputArea() {
  const [input, setInput] = useState('');
  const { sendMessage, streamingState, activeConversation, createConversation } = useChat();
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
    <div className="p-4 pb-6">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-end gap-2 bg-slate-800/80 backdrop-blur-xl rounded-2xl border border-slate-600/30 shadow-[0_8px_32px_rgba(0,0,0,0.4),0_2px_8px_rgba(0,0,0,0.2)] overflow-hidden transition-shadow duration-300 hover:shadow-[0_12px_40px_rgba(0,0,0,0.5),0_4px_12px_rgba(0,0,0,0.25)]">
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder="输入消息..."
              className="w-full resize-none bg-transparent px-5 py-3.5 text-slate-100 placeholder-slate-500 focus:outline-none min-h-[56px] max-h-[200px] overflow-y-auto text-base"
            />
          </div>
          
          <div className="flex items-center gap-1.5 p-2 pr-3">
            {input && !streamingState.isStreaming && (
              <button
                onClick={handleClear}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-700/50 transition-all"
                title="清空"
              >
                <X className="w-4 h-4" />
              </button>
            )}
            <button
              onClick={handleSend}
              disabled={!input.trim() || streamingState.isStreaming || charCount > maxChars}
              className={`flex items-center justify-center w-10 h-10 rounded-xl transition-all ${
                input.trim() && !streamingState.isStreaming && charCount <= maxChars
                  ? 'bg-primary-500 hover:bg-primary-600 text-white shadow-md hover:shadow-lg active:scale-95'
                  : 'bg-slate-600/50 text-slate-500 cursor-not-allowed'
              }`}
            >
              {streamingState.isStreaming ? (
                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              ) : (
                <Send className="w-4 h-4" />
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
