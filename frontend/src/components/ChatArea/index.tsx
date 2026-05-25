import { useEffect, useRef, useState, useTransition } from 'react';
import { MessageSquare, AlertCircle, ArrowDown } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { MessageBubble } from './MessageBubble';
import { MessageSkeleton } from '../common/Skeleton';

export function ChatArea() {
  const { activeConversation, messages, streamingState, error, clearError, isLoading } = useChat();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [prevConversationId, setPrevConversationId] = useState<string | null>(null);

  useEffect(() => {
    if (activeConversation && activeConversation.id !== prevConversationId) {
      setIsTransitioning(true);
      const timer = setTimeout(() => {
        setIsTransitioning(false);
        setPrevConversationId(activeConversation.id);
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [activeConversation, prevConversationId]);

  useEffect(() => {
    if (!showScrollButton && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, streamingState, showScrollButton]);

  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => clearError(), 5000);
      return () => clearTimeout(timer);
    }
  }, [error, clearError]);

  const handleScroll = () => {
    if (containerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = containerRef.current;
      const isNearBottom = scrollHeight - scrollTop - clientHeight < 100;
      setShowScrollButton(!isNearBottom);
    }
  };

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  if (!activeConversation) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-900">
        <div className="text-center text-slate-500 animate-fade-in">
          <MessageSquare className="w-16 h-16 mx-auto mb-4 opacity-50" />
          <h2 className="text-xl font-medium mb-2">选择或创建对话</h2>
          <p>从左侧列表选择一个对话，或创建新对话开始聊天</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col bg-slate-900 min-h-0 relative">
      {error && (
        <div className="bg-red-500/10 border-b border-red-500/20 px-4 py-3 animate-slide-down">
          <div className="max-w-4xl mx-auto flex items-center justify-between">
            <div className="flex items-center gap-2 text-red-400">
              <AlertCircle className="w-5 h-5" />
              <span>{error}</span>
            </div>
            <button
              onClick={clearError}
              className="p-1 rounded hover:bg-red-500/20 transition-colors"
            >
              <span className="sr-only">关闭</span>
              <span className="text-red-400">&times;</span>
            </button>
          </div>
        </div>
      )}

      <div 
        ref={containerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto scroll-smooth scrollbar-thin scrollbar-thumb-slate-600 scrollbar-track-slate-800"
      >
        <div className="h-full">
          {isLoading ? (
            <div className="space-y-4 p-6">
              <MessageSkeleton />
              <MessageSkeleton />
              <MessageSkeleton />
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full px-8">
              <div className="w-24 h-24 mb-6 rounded-full bg-gradient-to-br from-primary-500/20 to-slate-700/20 flex items-center justify-center animate-pulse-once">
                <MessageSquare className="w-12 h-12 text-primary-400" />
              </div>
              <h2 className="text-2xl font-semibold text-slate-200 mb-3">
                开始新对话
              </h2>
              <p className="text-slate-400 mb-8 text-center max-w-md">
                你好！我是 AI 助手。有什么我可以帮助你的吗？
              </p>
              <div className="flex flex-wrap gap-2 justify-center">
                <button 
                  onClick={() => {/* 发送示例问题 */}}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm transition-all duration-200 border border-slate-700 hover:border-primary-500/50 hover:shadow-md hover:shadow-primary-500/10"
                >
                  帮我写代码
                </button>
                <button 
                  onClick={() => {/* 发送示例问题 */}}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm transition-all duration-200 border border-slate-700 hover:border-primary-500/50 hover:shadow-md hover:shadow-primary-500/10"
                >
                  解释概念
                </button>
                <button 
                  onClick={() => {/* 发送示例问题 */}}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-sm transition-all duration-200 border border-slate-700 hover:border-primary-500/50 hover:shadow-md hover:shadow-primary-500/10"
                >
                  回答问题
                </button>
              </div>
            </div>
          ) : (
            <div className="py-4">
              <div className={`transition-all duration-300 ease-in-out ${isTransitioning ? 'opacity-0 scale-95 translate-y-4' : 'opacity-100 scale-100 translate-y-0'}`}>
                {messages.map((message, index) => {
                  const isLastAssistantMessage = message.role === 'assistant' && 
                    index === messages.length - 1 && 
                    streamingState.isStreaming;
                  
                  return (
                    <div 
                      key={message.id}
                      className="animate-message-in"
                      style={{ animationDelay: `${index * 50}ms` }}
                    >
                      <MessageBubble 
                        message={message} 
                        isThinking={isLastAssistantMessage}
                      />
                    </div>
                  );
                })}
              </div>
              
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>
      </div>

      {showScrollButton && messages.length > 0 && (
        <button
          onClick={scrollToBottom}
          className="absolute bottom-4 right-4 p-3 bg-slate-700 hover:bg-slate-600 rounded-full shadow-lg transition-all duration-200 hover:scale-110 hover:shadow-xl"
          title="滚动到底部"
        >
          <ArrowDown className="w-5 h-5 text-slate-300" />
        </button>
      )}
    </div>
  );
}
