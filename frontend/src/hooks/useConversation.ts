import { useCallback } from 'react'
import { useChat } from '../context/ChatContext'
import { conversations as conversationAPI } from '../api'
import type { Conversation } from '../types'
import { conversationStorage } from '../utils/storage'

export function useConversation() {
  const { dispatch, stateRef, stopStreaming } = useChat()

  const create = useCallback(async () => {
    try {
      const newConversation = await conversationAPI.create()
      conversationStorage.add(newConversation)
      dispatch({ type: 'ADD_CONVERSATION', payload: newConversation })
      dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: newConversation })
      dispatch({
        type: 'SET_MESSAGES',
        payload: { conversationId: newConversation.id, messages: [] },
      })
    } catch (error) {
      console.error('Failed to create conversation:', error)
      dispatch({ type: 'SET_ERROR', payload: '创建对话失败' })
    }
  }, [dispatch])

  const update = useCallback(
    async (id: string, title: string) => {
      try {
        await conversationAPI.update(id, { title })
        conversationStorage.update(id, { title })
        dispatch({ type: 'UPDATE_CONVERSATION_TITLE', payload: { id, title } })
      } catch (error) {
        console.error('Failed to update conversation:', error)
      }
    },
    [dispatch]
  )

  const remove = useCallback(
    async (id: string) => {
      try {
        stopStreaming(id)
        await conversationAPI.delete(id)
        conversationStorage.remove(id)
        dispatch({ type: 'REMOVE_CONVERSATION', payload: id })
      } catch (error) {
        console.error('Failed to delete conversation:', error)
        dispatch({ type: 'SET_ERROR', payload: '删除对话失败' })
      }
    },
    [dispatch, stopStreaming]
  )

  const pin = useCallback(
    async (id: string, pinned: boolean) => {
      try {
        await conversationAPI.update(id, { pinned })
        conversationStorage.update(id, { pinned })
        dispatch({ type: 'PIN_CONVERSATION', payload: { id, pinned } })
      } catch (error) {
        console.error('Failed to pin conversation:', error)
      }
    },
    [dispatch]
  )

  const select = useCallback(
    async (conv: Conversation) => {
      const state = stateRef.current
      const cachedMessages = state.messagesByConversation[conv.id]
      const isStreaming = state.streamingStates[conv.id]?.isStreaming

      dispatch({ type: 'SET_ACTIVE_CONVERSATION', payload: conv })
      dispatch({
        type: 'SET_MESSAGES',
        payload: { conversationId: conv.id, messages: cachedMessages || [] },
      })

      if (!cachedMessages && !isStreaming) {
        dispatch({ type: 'SET_LOADING', payload: true })
        try {
          const data = await conversationAPI.get(conv.id)
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
    [dispatch, stateRef]
  )

  const loadAll = useCallback(async () => {
    try {
      dispatch({ type: 'SET_LOADING', payload: true })

      const cached = conversationStorage.get()
      if (cached.length > 0) {
        dispatch({ type: 'SET_CONVERSATIONS', payload: cached })
      }

      const convs = await conversationAPI.list()
      conversationStorage.set(convs)
      dispatch({ type: 'SET_CONVERSATIONS', payload: convs })
    } catch (error) {
      console.error('Failed to load conversations:', error)
      dispatch({ type: 'SET_ERROR', payload: '加载对话列表失败' })
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false })
    }
  }, [dispatch])

  const loadMessages = useCallback(
    async (conversationId: string) => {
      const state = stateRef.current
      const streamingState = state.streamingStates[conversationId]
      if (streamingState?.isStreaming) {
        return
      }

      try {
        const data = await conversationAPI.get(conversationId)
        dispatch({
          type: 'SET_MESSAGES',
          payload: { conversationId, messages: data.messages || [] },
        })
      } catch (error) {
        console.error('Failed to load messages:', error)
        dispatch({ type: 'SET_ERROR', payload: '加载消息失败，请稍后重试' })
      }
    },
    [dispatch, stateRef]
  )

  return {
    create,
    update,
    remove,
    pin,
    select,
    loadAll,
    loadMessages,
    storage: conversationStorage,
  }
}
