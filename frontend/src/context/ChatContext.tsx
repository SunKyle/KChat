import React, { createContext, useContext, useReducer, useEffect, useCallback } from 'react';
import type { Conversation, Message, ChatRequest, StreamingState } from '../types';
import { api } from '../utils/api';

interface ChatContextType {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
  error: string | null;
  isLoading: boolean;
  setActiveConversation: (conv: Conversation) => void;
  createConversation: () => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  updateConversation: (id: string, title: string) => Promise<void>;
  sendMessage: (content: string) => Promise<void>;
  stopStreaming: () => void;
  loadMessages: (conversationId: string) => Promise<void>;
  clearError: () => void;
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
  | { type: 'REMOVE_CONVERSATION'; payload: string }
  | { type: 'UPDATE_CONVERSATION_TITLE'; payload: { id: string; title: string } }
  | { type: 'SET_ERROR'; payload: string }
  | { type: 'CLEAR_ERROR' }
  | { type: 'SET_LOADING'; payload: boolean };

interface ChatState {
  conversations: Conversation[];
  activeConversation: Conversation | null;
  messages: Message[];
  streamingState: StreamingState;
  error: string | null;
  isLoading: boolean;
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
  error: null,
  isLoading: false,
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
    
    case 'UPDATE_CONVERSATION_TITLE':
      return {
        ...state,
        conversations: state.conversations.map((conv) =>
          conv.id === action.payload.id ? { ...conv, title: action.payload.title } : conv
        ),
        activeConversation: state.activeConversation?.id === action.payload.id
          ? { ...state.activeConversation, title: action.payload.title }
          : state.activeConversation,
      };
    
    case 'SET_ERROR':
      return { ...state, error: action.payload };
    
    case 'CLEAR_ERROR':
      return { ...state, error: null };
    
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    
    default:
      return state;
  }
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(chatReducer, initialState);
  const abortControllerRef = React.useRef<AbortController | null>(null);

  useEffect(() => {
    loadConversations();
  }, []);

  const loadConversations = async () => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true });
      const conversations = await api.conversations.list();
      dispatch({ type: 'SET_CONVERSATIONS', payload: conversations });
      
      if (conversations.length > 0 && !state.activeConversation) {
        dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: conversations[0] });
        await loadMessages(conversations[0].id);
      }
    } catch (error) {
      console.error('Failed to load conversations:', error);
      dispatch({ type: 'SET_ERROR', payload: '加载会话失败，请稍后重试' });
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
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

  const updateConversation = useCallback(async (id: string, title: string) => {
    await api.conversations.update(id, title);
    dispatch({ type: 'UPDATE_CONVERSATION_TITLE', payload: { id, title } });
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
      dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' });
    }
  }, []);

  const clearError = useCallback(() => {
    dispatch({ type: 'CLEAR_ERROR' });
  }, []);

  const stopStreaming = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
      dispatch({ type: 'END_STREAMING', payload: 'stopped' });
      console.log('Streaming stopped');
    }
  }, []);

  const sendMessage = useCallback(async (content: string) => {
    if (!content.trim() || !state.activeConversation) return;

    stopStreaming();

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

    abortControllerRef.current = new AbortController();

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
          abortControllerRef.current = null;
        },
        (error) => {
          console.error('Streaming error:', error);
          dispatch({ type: 'END_STREAMING', payload: tempMessageId });
          abortControllerRef.current = null;
        },
        abortControllerRef.current
      );

      console.log('Message send completed');
    } catch (error) {
      console.error('Failed to send message:', error);
      dispatch({ type: 'END_STREAMING', payload: tempMessageId });
      abortControllerRef.current = null;
    }
  }, [state.activeConversation, stopStreaming]);

  return (
    <ChatContext.Provider
      value={{
        conversations: state.conversations,
        activeConversation: state.activeConversation,
        messages: state.messages,
        streamingState: state.streamingState,
        error: state.error,
        isLoading: state.isLoading,
        setActiveConversation,
        createConversation,
        deleteConversation,
        updateConversation,
        sendMessage,
        stopStreaming,
        loadMessages,
        clearError,
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
