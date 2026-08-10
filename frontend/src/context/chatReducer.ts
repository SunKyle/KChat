import type {
  AgentThinkingStep,
  Conversation,
  Message,
  StreamingState,
  WebSearchResultData,
} from '../types'

export type ChatAction =
  | { type: 'SET_CONVERSATIONS'; payload: Conversation[] }
  | { type: 'SET_ACTIVE_CONVERSATION'; payload: Conversation | null }
  | { type: 'SET_MESSAGES'; payload: { conversationId: string; messages: Message[] } }
  | { type: 'ADD_MESSAGE'; payload: Message }
  | {
      type: 'UPDATE_MESSAGE'
      payload: { id: string; content: string; conversationId: string; images?: string[] }
    }
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
      payload: { conversationId: string; results: WebSearchResultData }
    }
  | {
      type: 'ADD_AGENT_THINKING'
      payload: { conversationId: string; messageId: string; step: AgentThinkingStep }
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

export interface ChatState {
  conversations: Conversation[]
  activeConversation: Conversation | null
  messagesByConversation: Record<string, Message[]>
  streamingStates: Record<string, StreamingState>
  regeneratingStates: Record<
    string,
    { isRegenerating: boolean; messageId: string | null; savedContent?: string }
  >
  summarizingStates: Record<string, boolean>
  summarizingMessageId: string | null
  searchResultsByConversation: Record<string, WebSearchResultData>
  newReplies: Record<string, boolean>
  error: string | null
  isLoading: boolean
  currentModel: string
  availableModels: string[]
  scrollTrigger: number
}

export const initialState: ChatState = {
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

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'SET_CONVERSATIONS':
      return { ...state, conversations: action.payload }

    case 'SET_ACTIVE_CONVERSATION':
      return { ...state, activeConversation: action.payload }

    case 'SET_MESSAGES': {
      const existingMessages = state.messagesByConversation[action.payload.conversationId] || []
      let serverMessages = action.payload.messages

      // Filter out empty placeholder messages from server
      // These are assistant messages with no content and no images that are placeholders
      serverMessages = serverMessages.filter((m) => {
        if (m.role !== 'assistant') return true
        const hasContent = m.content && m.content.trim() !== ''
        const hasImages = m.images && m.images.length > 0
        // Keep if has content or images
        if (hasContent || hasImages) return true
        // Filter out empty assistant messages (they're placeholders)
        return false
      })

      // Create a signature for deduplication (used for assistant messages only)
      const getMessageSignature = (m: (typeof serverMessages)[0]) => {
        const imgKey = (m.images || []).sort().join(',')
        return `${m.role}:${m.content}:${imgKey}`
      }

      // Build signature set from server messages (assistant messages only)
      const serverIds = new Set(serverMessages.map((m) => m.id))
      const serverAssistantSignatures = new Set(
        serverMessages.filter((m) => m.role === 'assistant').map(getMessageSignature)
      )

      // Filter out local-only messages that are duplicates or empty placeholders
      const localOnlyMessages = existingMessages.filter((m) => {
        if (serverIds.has(m.id)) return false
        // Check if this is an empty placeholder message (assistant with no content/images)
        if (m.role === 'assistant') {
          const hasContent = m.content && m.content.trim() !== ''
          const hasImages = m.images && m.images.length > 0
          if (!hasContent && !hasImages) return false // Filter out empty placeholders
          // Check if this local message's content already exists in server messages
          const sig = getMessageSignature(m)
          if (serverAssistantSignatures.has(sig)) return false
        }
        return true
      })

      const merged = [...serverMessages, ...localOnlyMessages]
      merged.sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())

      return {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [action.payload.conversationId]: merged,
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
          msg.id === action.payload.id
            ? {
                ...msg,
                content: action.payload.content,
                images: action.payload.images !== undefined ? action.payload.images : msg.images,
              }
            : msg
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

    case 'ADD_AGENT_THINKING': {
      const convId = action.payload.conversationId
      const msgs = state.messagesByConversation[convId]
      if (!msgs) return state
      const updatedMessages = msgs.map((msg) => {
        if (msg.id !== action.payload.messageId) return msg
        const existing = msg.agentThinking || []
        // 复制数组避免 muting 原对象
        return { ...msg, agentThinking: [...existing, action.payload.step] }
      })
      return {
        ...state,
        messagesByConversation: {
          ...state.messagesByConversation,
          [convId]: updatedMessages,
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
      const newRegeneratingStates = { ...state.regeneratingStates }
      delete newRegeneratingStates[action.payload]
      const newSummarizingStates = { ...state.summarizingStates }
      delete newSummarizingStates[action.payload]
      const newSearchResults = { ...state.searchResultsByConversation }
      delete newSearchResults[action.payload]
      const newNewReplies = { ...state.newReplies }
      delete newNewReplies[action.payload]
      const newMessagesByConversation = { ...state.messagesByConversation }
      delete newMessagesByConversation[action.payload]
      return {
        ...state,
        conversations: state.conversations.filter((conv) => conv.id !== action.payload),
        activeConversation:
          state.activeConversation?.id === action.payload ? null : state.activeConversation,
        streamingStates: newStreamingStates,
        regeneratingStates: newRegeneratingStates,
        summarizingStates: newSummarizingStates,
        searchResultsByConversation: newSearchResults,
        newReplies: newNewReplies,
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
      let savedContent = ''
      const updatedMessages = messages.map((msg) => {
        if (msg.id === action.payload.messageId) {
          savedContent = msg.content
          return { ...msg, content: '' }
        }
        return msg
      })
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
            savedContent,
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
