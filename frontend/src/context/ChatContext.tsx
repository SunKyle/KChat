import React, { createContext, useContext, useReducer, useEffect, useCallback } from 'react';
import type { Conversation, Message, ChatRequest, StreamingState } from '../types';
import { api } from '../utils/api';

interface ChatContextType {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
  setActiveConversation: (conv: Conversation) => void;
  createConversation: () => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  loadMessages: (conversationId: string) => Promise<void>;
}

type ChatAction =
  | { type: 'SET_CONVERSATIONS'; payload: Conversation[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; payload: Conversation | null }
  | { type: 'SET_MESSAGES'; payload: Message[] }
  | { type: 'ADD_MESSAGE'; payload: Message }
  | { type: 'UPDATE_MESSAGE'; payload: { id: string; content: string } }
  | { type: 'START_STREAMING' }
  | { type: 'UPDATE_STREAMING_CONTENT'; payload: string }
  | { type: 'END_STREAMING'; payload: string }
  | { type: 'ADD_CONVERSATION'; payload: Conversation }
  | { type: 'REMOVE_CONVERSATION'; payload: string };

interface ChatState {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
}

const initialState: ChatState = {
  conversations: [],
  activeConversation: null,
  messages: [],
  streamingState: {
    isStreaming: false,
    currentContent: '',
    messageId: null,
  },
};

function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'SET_CONVERSATIONS':
      return { ...state, conversations: action.payload };
    
    case 'SET_ACTIVE_CONVERSATION':
      return { ...state, activeConversation: action.payload };
    
    case 'SET_MESSAGES':
      return { ...state, messages: action.payload };
    
    case 'ADD_MESSAGE':
      return { ...state, messages: [...state.messages, action.payload] };
    
    case 'UPDATE_MESSAGE':
      return {
        ...state,
        messages: state.messages.map((msg) =>
          msg.id === action.payload.id ? { ...msg, content: action.payload.content } : msg
        ),
      };
    
    case 'START_STREAMING':
      return {
        ...state,
        streamingState: { isStreaming: true, currentContent: '', messageId: null },
      };
    
    case 'UPDATE_STREAMING_CONTENT':
      return {
        ...state,
        streamingState: {
          ...state.streamingState,
          currentContent: state.streamingState.currentContent + action.payload,
        },
      };
    
    case 'END_STREAMING':
      return {
        ...state,
        streamingState: { isStreaming: false, currentContent: '', messageId: action.payload },
      };
    
    case 'ADD_CONVERSATION':
      return {
        ...state,
        conversations: [action.payload, ...state.conversations],
      };
    
    case 'REMOVE_CONVERSATION':
      return {
        ...state,
        conversations: state.conversations.filter((conv) => conv.id !== action.payload),
        activeConversation: state.activeConversation?.id === action.payload ? null : state.activeConversation,
        messages: state.activeConversation?.id === action.payload ? [] : state.messages,
      };
    
    default:
      return state;
  }
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(chatReducer, initialState);

  useEffect(() => {
    loadConversations();
  }, []);

  const loadConversations = async () => {
    try {
      const conversations = await api.conversations.list();
      dispatch({ type: 'SET_CONVERSATIONS', payload: conversations });
      
      if (conversations.length > 0 && !state.activeConversation) {
        dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: conversations[0] });
        await loadMessages(conversations[0].id);
      }
    } catch (error) {
      console.error('Failed to load conversations:', error);
    }
  };

  const setActiveConversation = useCallback(async (conv: Conversation) => {
    dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: conv });
    await loadMessages(conv.id);
  }, []);

  const createConversation = useCallback(async () => {
    const newConversation = await api.conversations.create('新对话');
    dispatch({ type: 'ADD_CONVERSATION', payload: newConversation });
    dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: newConversation });
    dispatch({ type: 'SET_MESSAGES', payload: [] });
  }, []);

  const deleteConversation = useCallback(async (id: string) => {
    await api.conversations.delete(id);
    dispatch({ type: 'REMOVE_CONVERSATION', payload: id });
  }, []);

  const loadMessages = useCallback(async (conversationId: string) => {
    try {
      const data = await api.conversations.get(conversationId);
      dispatch({ type: 'SET_MESSAGES', payload: data.messages || [] });
    } catch (error) {
      console.error('Failed to load messages:', error);
    }
  }, []);

  const sendMessage = useCallback(async (content: string) => {
    if (!content.trim() || !state.activeConversation) return;

    const userMessage: Message = {
      id: crypto.randomUUID(),
      conversationId: state.activeConversation.id,
      content: content.trim(),
      role: 'user',
      timestamp: new Date().toISOString(),
    };

    dispatch({ type: 'ADD_MESSAGE', payload: userMessage });
    dispatch({ type: 'START_STREAMING' });

    const request: ChatRequest = {
      conversationId: state.activeConversation.id,
      message: content.trim(),
    };

    const tempMessageId = crypto.randomUUID();
    const tempMessage: Message = {
      id: tempMessageId,
      conversationId: state.activeConversation.id,
      content: '',
      role: 'assistant',
      timestamp: new Date().toISOString(),
    };

    dispatch({ type: 'ADD_MESSAGE', payload: tempMessage });

    let streamingContent = '';

    try {
      console.log('Starting message stream...');
      await api.chat.stream(
        request,
        (chunk) => {
          streamingContent += chunk;
          console.log('Received chunk, total length:', streamingContent.length);
          dispatch({ type: 'UPDATE_STREAMING_CONTENT', payload: chunk });
          dispatch({ type: 'UPDATE_MESSAGE', payload: { id: tempMessageId, content: streamingContent } });
        },
        (messageId) => {
          console.log('Stream completed, messageId:', messageId);
          dispatch({ type: 'UPDATE_MESSAGE', payload: { id: tempMessageId, content: streamingContent } });
          dispatch({ type: 'END_STREAMING', payload: messageId });
        },
        (error) => {
          console.error('Streaming error:', error);
          dispatch({ type: 'END_STREAMING', payload: tempMessageId });
        }
      );

      console.log('Message send completed');
    } catch (error) {
      console.error('Failed to send message:', error);
      dispatch({ type: 'END_STREAMING', payload: tempMessageId });
    }
  }, [state.activeConversation]);

  return (
    <ChatContext.Provider
      value={{
        conversations: state.conversations,
        activeConversation: state.activeConversation,
        messages: state.messages,
        streamingState: state.streamingState,
        setActiveConversation,
        createConversation,
        deleteConversation,
        sendMessage,
        loadMessages,
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const context = useContext(ChatContext);
  if (context === undefined) {
    throw new Error('useChat must be used within a ChatProvider');
  }
  return context;
}
