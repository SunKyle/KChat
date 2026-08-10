import React, { createContext, useContext, useReducer, useEffect, useCallback, useRef } from 'react'
import type { Conversation, Message, ChatRequest, StreamingState } from '../types'
import { conversations, chat, models } from '../api'
import { initialState, chatReducer } from './chatReducer'
import type { ChatAction, ChatState } from './chatReducer'

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
  sendMessage: (
    content: string,
    imageUrls?: string[],
    webSearch?: boolean,
    agentMode?: boolean
  ) => Promise<void>
  stopStreaming: (conversationId?: string) => void
  loadMessages: (conversationId: string) => Promise<void>
  clearError: () => void
  setCurrentModel: (model: string) => void
  refreshModels: (category?: string) => Promise<void>
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
  getRegeneratingState: (conversationId: string) => {
    isRegenerating: boolean
    messageId: string | null
    savedContent?: string
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

  const loadModels = useCallback(async (category?: string) => {
    try {
      const allModels = await models.list(category)

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

  const refreshModels = useCallback(
    async (category?: string) => {
      await loadModels(category)
    },
    [loadModels]
  )

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
    // 仅初始化一次，loadModels/loadConversations 通过 ref 守卫避免重复调用
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
      dispatch({
        type: 'SET_MESSAGES',
        payload: { conversationId: newConversation.id, messages: [] },
      })
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

  const loadMessages = useCallback(
    async (conversationId: string) => {
      const streamingState = state.streamingStates[conversationId]
      if (streamingState?.isStreaming) {
        return
      }

      try {
        const data = await conversations.get(conversationId)
        dispatch({
          type: 'SET_MESSAGES',
          payload: { conversationId, messages: data.messages || [] },
        })
      } catch (error) {
        console.error('Failed to load messages:', error)
        dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' })
      }
    },
    [state.streamingStates]
  )

  const clearError = useCallback(() => {
    dispatch({ type: 'CLEAR_ERROR' })
  }, [])

  const sendMessage = useCallback(
    async (content: string, imageUrls: string[] = [], webSearch = false, agentMode = false) => {
      if (!content.trim() && imageUrls.length === 0) return

      // Optimistic conversation creation: create inline if no active conversation
      let conversationId = state.activeConversation?.id
      if (!conversationId) {
        try {
          const newConversation = await conversations.create()
          dispatch({ type: 'ADD_CONVERSATION', payload: newConversation })
          dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: newConversation })
          dispatch({
            type: 'SET_MESSAGES',
            payload: { conversationId: newConversation.id, messages: [] },
          })
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
        agentMode,
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

      // Throttle STREAM_CHUNK dispatches: buffer chunks and flush at 50ms intervals
      // to reduce context re-render frequency during streaming (P0 perf fix)
      let chunkBuffer = ''
      const CHUNK_FLUSH_MS = 50
      const chunkFlushInterval = setInterval(() => {
        if (chunkBuffer) {
          const content = chunkBuffer
          chunkBuffer = ''
          dispatch({
            type: 'STREAM_CHUNK',
            payload: {
              conversationId,
              messageId: tempMessageId,
              content,
              accumulated: streamingContent,
            },
          })
        }
      }, CHUNK_FLUSH_MS)

      try {
        await chat.stream(
          request,
          (chunk) => {
            streamingContent += chunk
            chunkBuffer += chunk
          },
          (backendMessageId, title, artifacts) => {
            // Flush remaining buffered chunks before completing
            clearInterval(chunkFlushInterval)
            if (chunkBuffer) {
              dispatch({
                type: 'STREAM_CHUNK',
                payload: {
                  conversationId,
                  messageId: tempMessageId,
                  content: chunkBuffer,
                  accumulated: streamingContent,
                },
              })
              chunkBuffer = ''
            }

            // 更新消息内容
            dispatch({
              type: 'UPDATE_MESSAGE',
              payload: {
                id: tempMessageId,
                content: streamingContent,
                conversationId,
                images: artifacts
                  ? artifacts
                      .filter((artifact) => artifact.type === 'image')
                      .map((artifact) => artifact.url)
                  : undefined,
              },
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
            if (title && conversationId) {
              dispatch({
                type: 'UPDATE_CONVERSATION_TITLE',
                payload: { id: conversationId, title },
              })
            }
            abortControllersRef.current[conversationId] = null
          },
          (error) => {
            clearInterval(chunkFlushInterval)
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
              payload: {
                conversationId,
                results: results as import('../types').WebSearchResultData,
              },
            })
          },
          undefined,
          (step) => {
            dispatch({
              type: 'ADD_AGENT_THINKING',
              payload: { conversationId, messageId: tempMessageId, step },
            })
          }
        )
      } catch (error) {
        clearInterval(chunkFlushInterval)
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

  const regenerateMessage = useCallback(async (conversationId: string, messageId: string) => {
    const currentState = stateRef.current
    const isAlreadyStreaming = currentState.streamingStates[conversationId]?.isStreaming
    const isAlreadyRegenerating = currentState.regeneratingStates[conversationId]?.isRegenerating

    if (isAlreadyStreaming || isAlreadyRegenerating) return

    const originalContent =
      currentState.messagesByConversation[conversationId]?.find((m) => m.id === messageId)
        ?.content || ''

    dispatch({ type: 'START_REGENERATING', payload: { conversationId, messageId } })

    const abortController = new AbortController()
    abortControllersRef.current[conversationId] = abortController

    try {
      const response = await chat.regenerate(
        conversationId,
        messageId,
        'default',
        stateRef.current.currentModel
      )

      if (abortController.signal.aborted) return

      if (response.success) {
        dispatch({
          type: 'UPDATE_MESSAGE',
          payload: { id: messageId, content: response.content, conversationId },
        })
      } else {
        dispatch({
          type: 'UPDATE_MESSAGE',
          payload: { id: messageId, content: originalContent, conversationId },
        })
        dispatch({ type: 'SET_ERROR', payload: response.message || '重新生成失败，请稍后重试' })
      }
    } catch {
      if (abortController.signal.aborted) return
      dispatch({
        type: 'UPDATE_MESSAGE',
        payload: { id: messageId, content: originalContent, conversationId },
      })
      dispatch({ type: 'SET_ERROR', payload: '重新生成失败，请检查网络或模型状态' })
    } finally {
      abortControllersRef.current[conversationId] = null
      dispatch({ type: 'END_REGENERATING', payload: { conversationId } })
    }
  }, [])

  const getRegeneratingState = useCallback(
    (conversationId: string) => {
      return (
        state.regeneratingStates[conversationId] || {
          isRegenerating: false,
          messageId: null,
          savedContent: undefined,
        }
      )
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
