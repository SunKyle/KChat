import { useEffect, useRef } from 'react';
import { MessageSquare } from 'lucide-react';
import { useChat } from '../../context/ChatContext';
import { MessageBubble } from './MessageBubble';
import { TypingIndicator } from './TypingIndicator';

export function ChatArea() {
  const { activeConversation, messages, streamingState } = useChat();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingState]);

  if (!activeConversation) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-900">
        <div className="text-center text-slate-500">
          <MessageSquare className="w-16 h-16 mx-auto mb-4 opacity-50" />
          <h2 className="text-xl font-medium mb-2">选择或创建对话</h2>
          <p>从左侧列表选择一个对话，或创建新对话开始聊天</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col bg-slate-900 min-h-0">
      <div className="flex-1 overflow-y-auto scroll-smooth scrollbar-thin scrollbar-thumb-slate-600 scrollbar-track-slate-800">
        <div className="h-full">
          {messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-slate-500">
              <MessageSquare className="w-16 h-16 mb-4 opacity-50" />
              <h2 className="text-xl font-medium mb-2">开始新对话</h2>
              <p>在下方输入框中输入消息开始聊天</p>
            </div>
          ) : (
            <div className="py-4">
              {messages.map((message) => (
                <MessageBubble key={message.id} message={message} />
              ))}
              
              {streamingState.isStreaming && (
                <div className="flex gap-3 p-4 max-w-4xl mx-auto">
                  <div className="flex-shrink-0 w-10 h-10 rounded-full bg-slate-600 flex items-center justify-center">
                    <span className="text-white text-sm">AI</span>
                  </div>
                  <div className="flex-1">
                    <div className="inline-block max-w-[80%] px-4 py-3 bg-slate-700 rounded-2xl rounded-bl-md">
                      <TypingIndicator />
                    </div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
