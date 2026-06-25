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
  scrollTrigger: number
  setActiveConversation: (conv: Conversation) => void
  createConversation: () => Promise<void>
  deleteConversation: (id: string) => Promise<void>
  updateConversation: (id: string, title: string) => Promise<void>
  pinConversation: (id: string, pinned: boolean) => Promise<void>
  sendMessage: (content: string, imageUrls?: string[], webSearch?: boolean) => Promise<void>
  stopStreaming: (conversationId?: string) => void
  loadMessages: (conversationId: string) => Promise<void>
  clearError: () => void
  setCurrentModel: (model: string) => void
  refreshModels: () => Promise<void>
  getStreamingState: (conversationId: string) => StreamingState
  getHasNewReply: (conversationId: string) => boolean
  resetNewReply: (conversationId: string) => void
  triggerScrollToBottom: () => void
  dispatch: React.Dispatch<ChatAction>
  stateRef: React.MutableRefObject<ChatState>
  getSummarizingState: (conversationId: string) => boolean
  summarizingMessageId: string | null
  getSearchResults: (conversationId: string) => import('../types').WebSearchResultData | null
  startSummarizing: (conversationId: string, messageId: string) => void
  endSummarizing: (conversationId: string) => void
  regenerateMessage: (conversationId: string, messageId: string) => Promise<void>
  getRegeneratingState: (conversationId: string) => { isRegenerating: boolean; messageId: string | null }
}

type ChatAction =
  | { type: 'SET_CONVERSATIONS'; payload: Conversation[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; payload: Conversation | null }
  | { type: 'SET_MESSAGES'; payload: { conversationId: string; messages: Message[] } }
  | { type: 'ADD_MESSAGE'; payload: Message }
  | { type: 'UPDATE_MESSAGE'; payload: { id: string; content: string; conversationId: string } }
  | { type: 'UPDATE_MESSAGE_ID'; payload: { oldId: string; newId: string; conversationId: string } }
  | { type: 'START_STREAMING'; payload: { conversationId: string } }
  | {
      type: 'UPDATE_STREAMING_CONTENT'
      payload: { conversationId: string; content: string }
    }
  | {
      type: 'STREAM_CHUNK'
      payload: { conversationId: string; messageId: string; content: string; accumulated: string }
    }
  | {
      type: 'SET_SEARCH_RESULTS'
      payload: { conversationId: string; results: import('../types').WebSearchResultData }
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
  | { type: 'SCROLL_TO_BOTTOM' }
  | { type: 'RESET_SCROLL_TRIGGER' }
  | { type: 'START_SUMMARIZING'; payload: { conversationId: string; messageId: string } }
  | { type: 'END_SUMMARIZING'; payload: string }
  | { type: 'START_REGENERATING'; payload: { conversationId: string; messageId: string } }
  | { type: 'END_REGENERATING'; payload: { conversationId: string } }

interface ChatState {
  conversations: Conversation[]
  activeConversation: Conversation | null
  messagesByConversation: Record<string, Message[]>
  streamingStates: Record<string, StreamingState>
  regeneratingStates: Record<string, { isRegenerating: boolean; messageId: string | null }>
  summarizingStates: Record<string, boolean>
  summarizingMessageId: string | null
  searchResultsByConversation: Record<string, import('../types').WebSearchResultData>
  newReplies: Record<string, boolean>
  error: string | null
  isLoading: boolean
  currentModel: string
  availableModels: string[]
  scrollTrigger: number
}

const initialState: ChatState = {
  conversations: [],
  activeConversation: null,
  messagesByConversation: {},
  streamingStates: {},
  regeneratingStates: {},
  summarizingStates: {},
  summarizingMessageId: null,
  searchResultsByConversation: {},
  newReplies: {},
  error: null,
  isLoading: false,
  currentModel: 'llama3',
  availableModels: ['llama3', 'mistral', 'phi', 'gemma', 'qwen'],
  scrollTrigger: 0,
}

function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'SET_CONVERSATIONS':
      return { ...state, conversations: action.payload }

    case 'SET_ACTIVE_CONVERSATION':
      return { ...state, activeConversation: action.payload }

    case 'SET_MESSAGES': {
      return {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [action.payload.conversationId]: action.payload.messages,
        },
      }
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
      const conversationId = action.payload.conversationId
      const msgs = state.messagesByConversation[conversationId]
      if (msgs) {
        const updatedMessages = msgs.map((msg) =>
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

    case 'UPDATE_MESSAGE_ID': {
      const conversationId = action.payload.conversationId
      const msgs = state.messagesByConversation[conversationId]
      if (msgs) {
        const updatedMessages = msgs.map((msg) =>
          msg.id === action.payload.oldId ? { ...msg, id: action.payload.newId } : msg
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

    case 'STREAM_CHUNK': {
      const convId = action.payload.conversationId
      const currentStreaming = state.streamingStates[convId] || {
        isStreaming: false,
        currentContent: '',
        messageId: null,
      }
      const msgs = state.messagesByConversation[convId]
      const updatedMessages = msgs
        ? msgs.map((msg) =>
            msg.id === action.payload.messageId
              ? { ...msg, content: action.payload.accumulated }
              : msg
          )
        : msgs
      return {
        ...state,
        ...(updatedMessages
          ? {
              messagesByConversation: {
                ...state.messagesByConversation,
                [convId]: updatedMessages,
              },
            }
          : {}),
        streamingStates: {
          ...state.streamingStates,
          [convId]: {
            ...currentStreaming,
            currentContent: currentStreaming.currentContent + action.payload.content,
          },
        },
      }
    }

    case 'SET_SEARCH_RESULTS':
      return {
        ...state,
        searchResultsByConversation: {
          ...state.searchResultsByConversation,
          [action.payload.conversationId]: action.payload.results,
        },
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

    case 'SCROLL_TO_BOTTOM':
      return { ...state, scrollTrigger: state.scrollTrigger + 1 }

    case 'RESET_SCROLL_TRIGGER':
      return { ...state, scrollTrigger: 0 }

    case 'START_SUMMARIZING':
      return {
        ...state,
        summarizingStates: {
          ...state.summarizingStates,
          [action.payload.conversationId]: true,
        },
        summarizingMessageId: action.payload.messageId,
      }

    case 'END_SUMMARIZING': {
      const newSummarizingStates = { ...state.summarizingStates }
      delete newSummarizingStates[action.payload]
      return {
        ...state,
        summarizingStates: newSummarizingStates,
        summarizingMessageId: null,
      }
    }

    case 'START_REGENERATING': {
      const messages = state.messagesByConversation[action.payload.conversationId] || []
      const updatedMessages = messages.map((msg) =>
        msg.id === action.payload.messageId ? { ...msg, content: '' } : msg
      )
      return {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [action.payload.conversationId]: updatedMessages,
        },
        regeneratingStates: {
          ...state.regeneratingStates,
          [action.payload.conversationId]: {
            isRegenerating: true,
            messageId: action.payload.messageId,
          },
        },
      }
    }

    case 'END_REGENERATING': {
      const newRegeneratingStates = { ...state.regeneratingStates }
      delete newRegeneratingStates[action.payload.conversationId]
      return {
        ...state,
        regeneratingStates: newRegeneratingStates,
      }
    }

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
      const cachedMessages = state.messagesByConversation[conv.id]
      const isStreaming = state.streamingStates[conv.id]?.isStreaming
      const needsFetch = !cachedMessages && !isStreaming

      // Atomic dispatch: set conversation + messages + loading in one go
      // Prevents intermediate blank state between dispatches
      dispatch({
        type: 'SET_ACTIVE_CONVERSATION',
        payload: conv,
      })
      dispatch({
        type: 'SET_MESSAGES',
        payload: { conversationId: conv.id, messages: cachedMessages || [] },
      })
      if (needsFetch) {
        dispatch({ type: 'SET_LOADING', payload: true })
      }

      // Only fetch from server when no cache exists (first visit to this conversation)
      if (needsFetch) {
        try {
          const data = await conversations.get(conv.id)
          if (stateRef.current.activeConversation?.id === conv.id) {
            dispatch({
              type: 'SET_MESSAGES',
              payload: { conversationId: conv.id, messages: data.messages || [] },
            })
          }
        } catch (error) {
          console.error('Failed to load messages:', error)
          dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' })
        } finally {
          dispatch({ type: 'SET_LOADING', payload: false })
        }
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
      dispatch({ type: 'SET_MESSAGES', payload: { conversationId: newConversation.id, messages: [] } })
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
      dispatch({ type: 'SET_MESSAGES', payload: { conversationId, messages: data.messages || [] } })
    } catch (error) {
      console.error('Failed to load messages:', error)
      dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' })
    }
  }, [])

  const clearError = useCallback(() => {
    dispatch({ type: 'CLEAR_ERROR' })
  }, [])

  const sendMessage = useCallback(
    async (content: string, imageUrls: string[] = [], webSearch = false) => {
      if (!content.trim() && imageUrls.length === 0) return

      // Optimistic conversation creation: create inline if no active conversation
      let conversationId = state.activeConversation?.id
      if (!conversationId) {
        try {
          const newConversation = await conversations.create()
          dispatch({ type: 'ADD_CONVERSATION', payload: newConversation })
          dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: newConversation })
          dispatch({ type: 'SET_MESSAGES', payload: { conversationId: newConversation.id, messages: [] } })
          conversationId = newConversation.id
          localStorage.setItem(
            'kchat_conversations',
            JSON.stringify(
              [...state.conversations, newConversation].sort(
                (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
              )
            )
          )
        } catch (error) {
          console.error('Failed to create conversation:', error)
          dispatch({ type: 'SET_ERROR', payload: '创建对话失败' })
          return
        }
      }

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
      dispatch({ type: 'SCROLL_TO_BOTTOM' })

      const request: ChatRequest = {
        conversationId,
        message: content.trim() || '分析图片',
        model: state.currentModel,
        imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
        userId: 'default',
        webSearch,
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
              type: 'STREAM_CHUNK',
              payload: {
                conversationId,
                messageId: tempMessageId,
                content: chunk,
                accumulated: streamingContent,
              },
            })
          },
          (backendMessageId, title) => {
            // 更新消息内容
            dispatch({
              type: 'UPDATE_MESSAGE',
              payload: { id: tempMessageId, content: streamingContent, conversationId },
            })
            // 更新消息ID为后端实际的消息ID
            dispatch({
              type: 'UPDATE_MESSAGE_ID',
              payload: { oldId: tempMessageId, newId: backendMessageId, conversationId },
            })
            dispatch({
              type: 'END_STREAMING',
              payload: { conversationId, messageId: backendMessageId },
            })
            if (title) {
              dispatch({ type: 'UPDATE_CONVERSATION_TITLE', payload: { id: conversationId!, title } })
            }
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
          abortController,
          (results) => {
            dispatch({
              type: 'SET_SEARCH_RESULTS',
              payload: { conversationId, results: results as import('../types').WebSearchResultData },
            })
          }
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
    [state.activeConversation, state.currentModel, state.conversations]
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

  const triggerScrollToBottom = useCallback(() => {
    dispatch({ type: 'SCROLL_TO_BOTTOM' })
  }, [])

  const getSummarizingState = useCallback(
    (conversationId: string): boolean => {
      return state.summarizingStates[conversationId] || false
    },
    [state.summarizingStates]
  )

  const getSearchResults = useCallback(
    (conversationId: string) => {
      return state.searchResultsByConversation[conversationId] || null
    },
    [state.searchResultsByConversation]
  )

  const startSummarizing = useCallback((conversationId: string, messageId: string) => {
    dispatch({ type: 'START_SUMMARIZING', payload: { conversationId, messageId } })
  }, [])

  const endSummarizing = useCallback((conversationId: string) => {
    dispatch({ type: 'END_SUMMARIZING', payload: conversationId })
  }, [])

  const regenerateMessage = useCallback(
    async (conversationId: string, messageId: string) => {
      dispatch({ type: 'START_REGENERATING', payload: { conversationId, messageId } })

      try {
        const response = await chat.regenerate(conversationId, messageId, 'default', state.currentModel)
        
        if (response.success) {
          // 更新消息内容
          dispatch({
            type: 'UPDATE_MESSAGE',
            payload: { id: messageId, content: response.content, conversationId },
          })
        } else {
          console.error('Regenerate failed:', response.message)
        }
      } catch (error) {
        console.error('Regenerate error:', error)
      } finally {
        dispatch({ type: 'END_REGENERATING', payload: { conversationId } })
      }
    },
    [state.currentModel]
  )

  const getRegeneratingState = useCallback(
    (conversationId: string) => {
      return state.regeneratingStates[conversationId] || {
        isRegenerating: false,
        messageId: null,
      }
    },
    [state.regeneratingStates]
  )

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
        scrollTrigger: state.scrollTrigger,
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
        triggerScrollToBottom,
        dispatch,
        stateRef,
        getSummarizingState,
        summarizingMessageId: state.summarizingMessageId,
        getSearchResults,
        startSummarizing,
        endSummarizing,
        regenerateMessage,
        getRegeneratingState,
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
