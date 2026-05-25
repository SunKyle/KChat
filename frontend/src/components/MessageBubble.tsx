import { User, Bot, Copy, RotateCcw, Check } from 'lucide-react';
import type { Message } from '../../types';
import { MarkdownRenderer } from './MarkdownRenderer';
import { useState, memo } from 'react';

interface MessageBubbleProps {
  message: Message;
  onRegenerate?: () => void;
  isThinking?: boolean;
  onStop?: () => void;
}

export const MessageBubble = memo(function MessageBubble({ message, onRegenerate, isThinking, onStop }: MessageBubbleProps) {
  const isUser = message.role === 'user';
  const [copied, setCopied] = useState(false);

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    
    if (diffMins < 1) return '刚刚';
    if (diffMins < 60) return `${diffMins} 分钟前`;
    if (diffMins < 1440) return `${Math.floor(diffMins / 60)} 小时前`;
    
    return date.toLocaleString('zh-CN', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(message.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  };

  return (
    <div className={`flex gap-4 py-8 group micro-transition ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center transition-all micro-transition ${
          isUser 
            ? 'bg-slate-700 text-slate-300' 
            : 'bg-[#0EA5E9] text-white shadow-sm shadow-sky-500/20'
        }`}
      >
        {isUser ? (
          <User className="w-4 h-4" />
        ) : (
          <Bot className="w-4 h-4" />
        )}
      </div>

      <div className={`flex-1 min-w-0 ${isUser ? 'text-right' : 'text-left'}`}>
        <div
          className={`relative inline-block max-w-[85%] transition-all ${
            isUser
              ? 'text-[#E5E7EB]'
              : 'bg-transparent text-[#E5E7EB]'
          }`}
        >
          {isThinking && !message.content ? (
            <div className="flex items-center gap-2 py-1">
              <span className="text-slate-500 text-sm font-medium">
                AI 正在思考
              </span>
              <div className="flex items-center gap-1">
                <span 
                  className="w-1.5 h-1.5 rounded-full bg-slate-500" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0ms' }} 
                />
                <span 
                  className="w-1.5 h-1.5 rounded-full bg-slate-500" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0.2s' }} 
                />
                <span 
                  className="w-1.5 h-1.5 rounded-full bg-slate-500" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0.4s' }} 
                />
              </div>
            </div>
          ) : (
            <div className="leading-relaxed">
              <MarkdownRenderer content={message.content} />
            </div>
          )}
        </div>
        
        <div className={`flex items-center gap-3 mt-2 ${isUser ? 'justify-end' : 'justify-start'}`}>
          <span className="text-[10px] font-medium text-slate-500 uppercase">
            {formatTimestamp(message.timestamp)}
          </span>
          
          {!isUser && !isThinking && (
            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 micro-transition">
              <button
                onClick={handleCopy}
                className="p-1.5 rounded-md hover:bg-white/5 micro-transition"
                title={copied ? '已复制' : '复制'}
              >
                {copied ? (
                  <Check className="w-3.5 h-3.5 text-green-400" />
                ) : (
                  <Copy className="w-3.5 h-3.5 text-slate-500 hover:text-slate-300" />
                )}
              </button>
              {onRegenerate && (
                <button
                  onClick={onRegenerate}
                  className="p-1.5 rounded-md hover:bg-white/5 micro-transition"
                >
                <RotateCcw className="w-3.5 h-3.5 text-slate-500 hover:text-slate-300" />
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
});
