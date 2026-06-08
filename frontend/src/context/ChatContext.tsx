import React, { createContext, useContext, useReducer, useEffect, useCallback, useRef } from 'react'
import type { Conversation, Message, ChatRequest, StreamingState } from '../types'
import { conversations, chat, models } from '../api'

interface ChatContextType {
  conversations: Conversation[]
  activeConversation: Conversation | null
  messages: Message[]
  streamingState: StreamingState
  error: string | null
  isLoading: boolean
  currentModel: string
  availableModels: string[]
  setActiveConversation: (conv: Conversation) => void
  createConversation: () => Promise<void>
  deleteConversation: (id: string) => Promise<void>
  updateConversation: (id: string, title: string) => Promise<void>
  pinConversation: (id: string, pinned: boolean) => Promise<void>
  sendMessage: (content: string, imageUrls?: string[]) => Promise<void>
  stopStreaming: (conversationId?: string) => void
  loadMessages: (conversationId: string) => Promise<void>
  clearError: () => void
  setCurrentModel: (model: string) => void
  refreshModels: () => Promise<void>
  getStreamingState: (conversationId: string) => StreamingState
  getHasNewReply: (conversationId: string) => boolean
  resetNewReply: (conversationId: string) => void
}

type ChatAction =
  | { type: 'SET_CONVERSATIONS'; payload: Conversation[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; payload: Conversation | null }
  | { type: 'SET_MESSAGES'; payload: Message[] }
  | { type: 'ADD_MESSAGE'; payload: Message }
  | { type: 'UPDATE_MESSAGE'; payload: { id: string; content: string } }
  | { type: 'START_STREAMING'; payload: { conversationId: string } }
  | {
      type: 'UPDATE_STREAMING_CONTENT'
      payload: { conversationId: string; content: string }
    }
  | {
      type: 'END_STREAMING'
      payload: { conversationId: string; messageId: string }
    }
  | { type: 'SET_NEW_REPLY'; payload: string }
  | { type: 'RESET_NEW_REPLY'; payload: string }
  | { type: 'ADD_CONVERSATION'; payload: Conversation }
  | { type: 'REMOVE_CONVERSATION'; payload: string }
  | {
      type: 'UPDATE_CONVERSATION_TITLE'
      payload: { id: string; title: string }
    }
  | { type: 'PIN_CONVERSATION'; payload: { id: string; pinned: boolean } }
  | { type: 'SET_ERROR'; payload: string }
  | { type: 'CLEAR_ERROR' }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_CURRENT_MODEL'; payload: string }
  | { type: 'SET_AVAILABLE_MODELS'; payload: string[] }

interface ChatState {
  conversations: Conversation[]
  activeConversation: Conversation | null
  messagesByConversation: Record<string, Message[]>
  streamingStates: Record<string, StreamingState>
  newReplies: Record<string, boolean>
  error: string | null
  isLoading: boolean
  currentModel: string
  availableModels: string[]
}

const initialState: ChatState = {
  conversations: [],
  activeConversation: null,
  messagesByConversation: {},
  streamingStates: {},
  newReplies: {},
  error: null,
  isLoading: false,
  currentModel: 'llama3',
  availableModels: ['llama3', 'mistral', 'phi', 'gemma', 'qwen'],
}

function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'SET_CONVERSATIONS':
      return { ...state, conversations: action.payload }

    case 'SET_ACTIVE_CONVERSATION':
      return { ...state, activeConversation: action.payload }

    case 'SET_MESSAGES': {
      if (state.activeConversation) {
        return {
          ...state,
          messagesByConversation: {
            ...state.messagesByConversation,
            [state.activeConversation.id]: action.payload,
          },
        }
      }
      return state
    }

    case 'ADD_MESSAGE': {
      const currentMessages = state.messagesByConversation[action.payload.conversationId] || []
      const newMessages = [...currentMessages, action.payload]
      return {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [action.payload.conversationId]: newMessages,
        },
      }
    }

    case 'UPDATE_MESSAGE': {
      const conversationId = Object.keys(state.messagesByConversation).find((convId) =>
        state.messagesByConversation[convId].some((msg) => msg.id === action.payload.id)
      )
      if (conversationId) {
        const updatedMessages = state.messagesByConversation[conversationId].map((msg) =>
          msg.id === action.payload.id ? { ...msg, content: action.payload.content } : msg
        )
        return {
          ...state,
          messagesByConversation: {
            ...state.messagesByConversation,
            [conversationId]: updatedMessages,
          },
        }
      }
      return state
    }

    case 'START_STREAMING':
      return {
        ...state,
        streamingStates: {
          ...state.streamingStates,
          [action.payload.conversationId]: {
            isStreaming: true,
            currentContent: '',
            messageId: null,
          },
        },
      }

    case 'UPDATE_STREAMING_CONTENT': {
      const currentStreaming = state.streamingStates[action.payload.conversationId] || {
        isStreaming: false,
        currentContent: '',
        messageId: null,
      }
      return {
        ...state,
        streamingStates: {
          ...state.streamingStates,
          [action.payload.conversationId]: {
            ...currentStreaming,
            currentContent: currentStreaming.currentContent + action.payload.content,
          },
        },
      }
    }

    case 'END_STREAMING':
      return {
        ...state,
        streamingStates: {
          ...state.streamingStates,
          [action.payload.conversationId]: {
            isStreaming: false,
            currentContent: '',
            messageId: action.payload.messageId,
          },
        },
        newReplies: {
          ...state.newReplies,
          [action.payload.conversationId]: true,
        },
      }

    case 'SET_NEW_REPLY':
      return {
        ...state,
        newReplies: {
          ...state.newReplies,
          [action.payload]: true,
        },
      }

    case 'RESET_NEW_REPLY': {
      const newNewReplies = { ...state.newReplies }
      delete newNewReplies[action.payload]
      return {
        ...state,
        newReplies: newNewReplies,
      }
    }

    case 'ADD_CONVERSATION':
      return {
        ...state,
        conversations: [action.payload, ...state.conversations],
      }

    case 'REMOVE_CONVERSATION': {
      const newStreamingStates = { ...state.streamingStates }
      delete newStreamingStates[action.payload]
      const newMessagesByConversation = { ...state.messagesByConversation }
      delete newMessagesByConversation[action.payload]
      return {
        ...state,
        conversations: state.conversations.filter((conv) => conv.id !== action.payload),
        activeConversation:
          state.activeConversation?.id === action.payload ? null : state.activeConversation,
        streamingStates: newStreamingStates,
        messagesByConversation: newMessagesByConversation,
      }
    }

    case 'UPDATE_CONVERSATION_TITLE':
      return {
        ...state,
        conversations: state.conversations.map((conv) =>
          conv.id === action.payload.id ? { ...conv, title: action.payload.title } : conv
        ),
        activeConversation:
          state.activeConversation?.id === action.payload.id
            ? { ...state.activeConversation, title: action.payload.title }
            : state.activeConversation,
      }

    case 'PIN_CONVERSATION':
      return {
        ...state,
        conversations: state.conversations.map((conv) =>
          conv.id === action.payload.id ? { ...conv, pinned: action.payload.pinned } : conv
        ),
        activeConversation:
          state.activeConversation?.id === action.payload.id
            ? { ...state.activeConversation, pinned: action.payload.pinned }
            : state.activeConversation,
      }

    case 'SET_ERROR':
      return { ...state, error: action.payload }

    case 'CLEAR_ERROR':
      return { ...state, error: null }

    case 'SET_LOADING':
      return { ...state, isLoading: action.payload }

    case 'SET_CURRENT_MODEL':
      return { ...state, currentModel: action.payload }

    case 'SET_AVAILABLE_MODELS':
      return { ...state, availableModels: action.payload }

    default:
      return state
  }
}

const ChatContext = createContext<ChatContextType | undefined>(undefined)

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(chatReducer, initialState)
  const abortControllersRef = useRef<Record<string, AbortController | null>>({})
  const initializedRef = useRef(false)
  const stateRef = useRef(state)

  useEffect(() => {
    stateRef.current = state
  }, [state])

  const loadModels = useCallback(async () => {
    try {
      const allModels = await models.list()

      if (allModels.length > 0) {
        dispatch({ type: 'SET_AVAILABLE_MODELS', payload: allModels })
        if (!allModels.includes(stateRef.current.currentModel)) {
          dispatch({ type: 'SET_CURRENT_MODEL', payload: allModels[0] })
        }
      }
    } catch (error) {
      console.error('Failed to load models:', error)
    }
  }, [])

  const refreshModels = useCallback(async () => {
    await loadModels()
  }, [loadModels])

  const stopStreaming = useCallback((conversationId?: string) => {
    if (conversationId) {
      const controller = abortControllersRef.current[conversationId]
      if (controller) {
        controller.abort()
        abortControllersRef.current[conversationId] = null
        dispatch({
          type: 'END_STREAMING',
          payload: { conversationId, messageId: 'stopped' },
        })
      }
    } else {
      Object.keys(abortControllersRef.current).forEach((id) => {
        const controller = abortControllersRef.current[id]
        if (controller) {
          controller.abort()
          abortControllersRef.current[id] = null
        }
      })
    }
  }, [])

  useEffect(() => {
    if (!initializedRef.current) {
      initializedRef.current = true
      loadConversations()
      loadModels()
    }
  }, [])

  const loadConversations = async () => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true })

      const cached = localStorage.getItem('kchat_conversations')
      if (cached) {
        dispatch({ type: 'SET_CONVERSATIONS', payload: JSON.parse(cached) })
      }

      const convs = await conversations.list()
      dispatch({ type: 'SET_CONVERSATIONS', payload: convs })
      localStorage.setItem('kchat_conversations', JSON.stringify(convs))
    } catch (error) {
      console.error('Failed to load conversations:', error)
      dispatch({ type: 'SET_ERROR', payload: '加载对话列表失败' })
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false })
    }
  }

  const setActiveConversation = useCallback(
    async (conv: Conversation) => {
      dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: conv })

      const cachedMessages = state.messagesByConversation[conv.id]
      const streamingState = state.streamingStates[conv.id]

      if (streamingState?.isStreaming && cachedMessages) {
        dispatch({ type: 'SET_MESSAGES', payload: cachedMessages })
      } else {
        await loadMessages(conv.id)
      }
    },
    [state.messagesByConversation, state.streamingStates]
  )

  const createConversation = useCallback(async () => {
    try {
      const newConversation = await conversations.create()
      dispatch({ type: 'ADD_CONVERSATION', payload: newConversation })
      localStorage.setItem(
        'kchat_conversations',
        JSON.stringify(
          [...state.conversations, newConversation].sort(
            (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          )
        )
      )
      dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: newConversation })
      dispatch({ type: 'SET_MESSAGES', payload: [] })
    } catch (error) {
      console.error('Failed to create conversation:', error)
      dispatch({ type: 'SET_ERROR', payload: '创建对话失败' })
    }
  }, [state.conversations])

  const updateConversation = useCallback(async (id: string, title: string) => {
    try {
      await conversations.update(id, { title })
      dispatch({ type: 'UPDATE_CONVERSATION_TITLE', payload: { id, title } })

      const cached = localStorage.getItem('kchat_conversations')
      if (cached) {
        const conversations = JSON.parse(cached)
        const updated = conversations.map((c: Conversation) => (c.id === id ? { ...c, title } : c))
        localStorage.setItem('kchat_conversations', JSON.stringify(updated))
      }
    } catch (error) {
      console.error('Failed to update conversation:', error)
    }
  }, [])

  const deleteConversation = useCallback(
    async (id: string) => {
      try {
        stopStreaming(id)
        await conversations.delete(id)
        dispatch({ type: 'REMOVE_CONVERSATION', payload: id })

        const cached = localStorage.getItem('kchat_conversations')
        if (cached) {
          const conversations = JSON.parse(cached)
          const updated = conversations.filter((c: Conversation) => c.id !== id)
          localStorage.setItem('kchat_conversations', JSON.stringify(updated))
        }
      } catch (error) {
        console.error('Failed to delete conversation:', error)
        dispatch({ type: 'SET_ERROR', payload: '删除对话失败' })
      }
    },
    [stopStreaming]
  )

  const pinConversation = useCallback(async (id: string, pinned: boolean) => {
    try {
      await conversations.update(id, { pinned })
      dispatch({ type: 'PIN_CONVERSATION', payload: { id, pinned } })

      const cached = localStorage.getItem('kchat_conversations')
      if (cached) {
        const conversations = JSON.parse(cached)
        const updated = conversations.map((c: Conversation) => (c.id === id ? { ...c, pinned } : c))
        localStorage.setItem('kchat_conversations', JSON.stringify(updated))
      }
    } catch (error) {
      console.error('Failed to pin conversation:', error)
    }
  }, [])

  const loadMessages = useCallback(async (conversationId: string) => {
    const streamingState = state.streamingStates[conversationId]
    if (streamingState?.isStreaming) {
      return
    }

    try {
      const data = await conversations.get(conversationId)
      dispatch({ type: 'SET_MESSAGES', payload: data.messages || [] })
    } catch (error) {
      console.error('Failed to load messages:', error)
      dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' })
    }
  }, [])

  const clearError = useCallback(() => {
    dispatch({ type: 'CLEAR_ERROR' })
  }, [])

  const sendMessage = useCallback(
    async (content: string, imageUrls: string[] = []) => {
      if ((!content.trim() && imageUrls.length === 0) || !state.activeConversation) return

      const conversationId = state.activeConversation.id

      const userMessage: Message = {
        id: crypto.randomUUID(),
        conversationId,
        content: content.trim(),
        role: 'user',
        timestamp: new Date().toISOString(),
        images: imageUrls.length > 0 ? imageUrls : undefined,
      }

      dispatch({ type: 'ADD_MESSAGE', payload: userMessage })
      dispatch({ type: 'START_STREAMING', payload: { conversationId } })

      const request: ChatRequest = {
        conversationId,
        message: content.trim() || '分析图片',
        model: state.currentModel,
        imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
        userId: 'default',
      }

      const tempMessageId = crypto.randomUUID()
      const tempMessage: Message = {
        id: tempMessageId,
        conversationId,
        content: '',
        role: 'assistant',
        timestamp: new Date().toISOString(),
      }

      dispatch({ type: 'ADD_MESSAGE', payload: tempMessage })

      let streamingContent = ''
      const abortController = new AbortController()
      abortControllersRef.current[conversationId] = abortController

      try {
        await chat.stream(
          request,
          (chunk) => {
            streamingContent += chunk
            dispatch({
              type: 'UPDATE_STREAMING_CONTENT',
              payload: { conversationId, content: chunk },
            })
            dispatch({
              type: 'UPDATE_MESSAGE',
              payload: { id: tempMessageId, content: streamingContent },
            })
          },
          (messageId) => {
            dispatch({
              type: 'UPDATE_MESSAGE',
              payload: { id: tempMessageId, content: streamingContent },
            })
            dispatch({
              type: 'END_STREAMING',
              payload: { conversationId, messageId },
            })
            abortControllersRef.current[conversationId] = null
          },
          (error) => {
            console.error('Streaming error:', error)
            dispatch({
              type: 'SET_ERROR',
              payload: error instanceof Error ? error.message : String(error),
            })
            dispatch({
              type: 'END_STREAMING',
              payload: { conversationId, messageId: tempMessageId },
            })
            abortControllersRef.current[conversationId] = null
          },
          abortController
        )
      } catch (error) {
        console.error('Failed to send message:', error)
        const errorMessage =
          (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          '发送消息失败，请检查网络或模型状态'
        dispatch({ type: 'SET_ERROR', payload: errorMessage })
        dispatch({
          type: 'END_STREAMING',
          payload: { conversationId, messageId: tempMessageId },
        })
        abortControllersRef.current[conversationId] = null
      }
    },
    [state.activeConversation, state.currentModel]
  )

  const setCurrentModel = useCallback((model: string) => {
    dispatch({ type: 'SET_CURRENT_MODEL', payload: model })
  }, [])

  const getStreamingState = useCallback(
    (conversationId: string): StreamingState => {
      return (
        state.streamingStates[conversationId] || {
          isStreaming: false,
          currentContent: '',
          messageId: null,
        }
      )
    },
    [state.streamingStates]
  )

  const getHasNewReply = useCallback(
    (conversationId: string): boolean => {
      return state.newReplies[conversationId] || false
    },
    [state.newReplies]
  )

  const resetNewReply = useCallback((conversationId: string) => {
    dispatch({ type: 'RESET_NEW_REPLY', payload: conversationId })
  }, [])

  const streamingState = state.activeConversation
    ? state.streamingStates[state.activeConversation.id] || initialState.streamingStates['']
    : initialState.streamingStates['']

  const messages = state.activeConversation
    ? state.messagesByConversation[state.activeConversation.id] || []
    : []

  return (
    <ChatContext.Provider
      value={{
        conversations: state.conversations,
        activeConversation: state.activeConversation,
        messages,
        streamingState: streamingState || {
          isStreaming: false,
          currentContent: '',
          messageId: null,
        },
        error: state.error,
        isLoading: state.isLoading,
        currentModel: state.currentModel,
        availableModels: state.availableModels,
        setActiveConversation,
        createConversation,
        deleteConversation,
        updateConversation,
        pinConversation,
        sendMessage,
        stopStreaming,
        loadMessages,
        clearError,
        setCurrentModel,
        refreshModels,
        getStreamingState,
        getHasNewReply,
        resetNewReply,
      }}
    >
      {children}
    </ChatContext.Provider>
  )
}

export function useChat() {
  const context = useContext(ChatContext)
  if (context === undefined) {
    throw new Error('useChat must be used within a ChatProvider')
  }
  return context
}
