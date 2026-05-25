import { User, Bot, Copy, RotateCcw } from 'lucide-react';
import type { Message } from '../../types';
import { MarkdownRenderer } from './MarkdownRenderer';
import { useState, memo } from 'react';

interface MessageBubbleProps {
  message: Message;
  onRegenerate?: () => void;
  isThinking?: boolean;
}

export const MessageBubble = memo(function MessageBubble({ message, onRegenerate, isThinking }: MessageBubbleProps) {
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
    <div className={`flex gap-3 p-4 max-w-4xl mx-auto group ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center shadow-lg ${
          isUser ? 'bg-gradient-to-br from-primary-500 to-primary-600' : 'bg-gradient-to-br from-slate-600 to-slate-700'
        }`}
      >
        {isUser ? (
          <User className="w-5 h-5 text-white" />
        ) : (
          <Bot className="w-5 h-5 text-white" />
        )}
      </div>

      <div className={`flex-1 ${isUser ? 'text-right' : 'text-left'}`}>
        <div
          className={`relative inline-block max-w-[85%] px-5 py-3 rounded-2xl shadow-md transition-shadow hover:shadow-lg ${
            isUser
              ? 'bg-gradient-to-br from-primary-500 to-primary-600 text-white rounded-br-md'
              : 'bg-slate-700/80 text-slate-100 rounded-bl-md backdrop-blur-sm'
          }`}
        >
          {isThinking && !message.content ? (
            <div className="flex items-center gap-4">
              <div className="relative">
                <div className="w-8 h-8 rounded-full bg-primary-500/20 flex items-center justify-center">
                  <div className="relative">
                    <div className="w-2 h-2 rounded-full bg-primary-400" />
                    <div className="absolute inset-0 w-2 h-2 rounded-full bg-primary-400 animate-thinking-ring" />
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-1.5">
                <span 
                  className="w-2 h-2 rounded-full bg-slate-300" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0ms' }} 
                />
                <span 
                  className="w-2 h-2 rounded-full bg-slate-300" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0.2s' }} 
                />
                <span 
                  className="w-2 h-2 rounded-full bg-slate-300" 
                  style={{ animation: 'thinking-dot 1.4s ease-in-out infinite', animationDelay: '0.4s' }} 
                />
              </div>
              <span className="text-slate-400 text-sm font-medium">
                正在思考中
              </span>
            </div>
          ) : (
            <MarkdownRenderer content={message.content} />
          )}
          
          {!isUser && !isThinking && (
            <div className="absolute -left-12 top-1/2 -translate-y-1/2 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                onClick={handleCopy}
                className="p-1.5 rounded-lg bg-slate-700 hover:bg-slate-600 transition-colors"
                title="复制"
              >
                {copied ? (
                  <span className="text-xs text-green-400">已复制</span>
                ) : (
                  <Copy className="w-4 h-4 text-slate-300" />
                )}
              </button>
              {onRegenerate && (
                <button
                  onClick={onRegenerate}
                  className="p-1.5 rounded-lg bg-slate-700 hover:bg-slate-600 transition-colors"
                  title="重新生成"
                >
                  <RotateCcw className="w-4 h-4 text-slate-300" />
                </button>
              )}
            </div>
          )}
        </div>
        <div className={`flex mt-1 ${isUser ? 'justify-end' : 'justify-start'}`}>
          <span className="text-xs text-slate-500 px-2">
            {formatTimestamp(message.timestamp)}
          </span>
        </div>
      </div>
    </div>
  );
});
