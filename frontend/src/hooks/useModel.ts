import { useCallback } from 'react'
import { useChat } from '../context/ChatContext'
import { models as modelsAPI } from '../api'

export function useModel() {
  const { dispatch, stateRef } = useChat()

  const select = useCallback(
    (model: string) => {
      dispatch({ type: 'SET_CURRENT_MODEL', payload: model })
    },
    [dispatch]
  )

  const refresh = useCallback(async (category?: string) => {
    await loadModels(category)
  }, [])

  const loadModels = useCallback(
    async (category?: string) => {
      try {
        const allModels = await modelsAPI.list(category)

        if (allModels.length > 0) {
          dispatch({ type: 'SET_AVAILABLE_MODELS', payload: allModels })
          if (!allModels.includes(stateRef.current.currentModel)) {
            dispatch({ type: 'SET_CURRENT_MODEL', payload: allModels[0] })
          }
        }
      } catch (error) {
        console.error('Failed to load models:', error)
      }
    },
    [dispatch]
  )

  const getDefaultModel = useCallback(() => {
    const state = stateRef.current
    return state.availableModels[0] || 'llama3'
  }, [])

  const isValidModel = useCallback((model: string): boolean => {
    const state = stateRef.current
    return state.availableModels.includes(model)
  }, [])

  const getCurrentModel = useCallback(() => {
    return stateRef.current.currentModel
  }, [])

  const getAvailableModels = useCallback(() => {
    return stateRef.current.availableModels
  }, [])

  return {
    select,
    refresh,
    loadModels,
    getDefaultModel,
    isValidModel,
    getCurrentModel,
    getAvailableModels,
  }
}
