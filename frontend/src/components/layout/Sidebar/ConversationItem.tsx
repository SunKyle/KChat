import { useState, useEffect, useRef } from 'react'
import { Pencil, Trash2, Check, X, Pin } from 'lucide-react'
import type { Conversation } from '../../../types'

interface ConversationItemProps {
  conversation: Conversation
  isActive: boolean
  isStreaming: boolean
  hasNewReply: boolean
  onClick: () => void
  onDelete: () => void
  onUpdate: (id: string, title: string) => void
  onPin: (id: string, pinned: boolean) => void
  collapsed?: boolean
}

export function ConversationItem({
  conversation,
  isActive,
  isStreaming,
  hasNewReply,
  onClick,
  onDelete,
  onUpdate,
  onPin,
  collapsed = false,
}: ConversationItemProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState(conversation.title)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus()
      inputRef.current.select()
    }
  }, [isEditing])

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault()
    onDelete()
  }

  const handleStartEdit = (e: React.MouseEvent) => {
    e.stopPropagation()
    setEditValue(conversation.title)
    setIsEditing(true)
  }

  const handleSaveEdit = () => {
    const trimmedValue = editValue.trim()
    if (trimmedValue && trimmedValue !== conversation.title) {
      onUpdate(conversation.id, trimmedValue)
    }
    setIsEditing(false)
  }

  const handleCancelEdit = () => {
    setEditValue(conversation.title)
    setIsEditing(false)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSaveEdit()
    } else if (e.key === 'Escape') {
      handleCancelEdit()
    }
  }

  if (collapsed) {
    return (
      <div
        onClick={onClick}
        className={`relative flex items-center justify-center py-2 px-1 rounded-lg cursor-pointer transition-all duration-200 ease-out ${
          isActive ? 'theme-bg-hover' : 'hover:theme-bg-hover/60'
        }`}
        title={conversation.title}
      >
        {' '}
        <div
          className={`relative w-7 h-7 rounded-full flex items-center justify-center micro-transition ${
            isActive
              ? 'theme-brand-primary text-white'
              : 'theme-bg-card theme-text-secondary'
          }`}
        >
          {isStreaming ? (
            <div className="w-3.5 h-3.5 border-2 border-[var(--text-muted)]/50 border-t-[var(--text-primary)] rounded-full animate-spin" />
          ) : (
            <span className="text-xs font-medium">
              {conversation.title.charAt(0)}
            </span>
          )}
        </div>
        {hasNewReply && (
          <div className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 theme-bg-accent-emerald rounded-full shadow-sm shadow-[var(--accent-emerald)]/40" />
        )}
      </div>
    )
  }

  return (
    <div
      onClick={isEditing ? undefined : onClick}
      onContextMenu={handleContextMenu}
      className={`group relative flex items-center gap-2.5 px-2.5 py-2 rounded-lg cursor-pointer transition-all duration-200 ease-out ${
        isActive ? 'theme-bg-hover' : 'hover:theme-bg-hover/60'
      }`}
    >
      {' '}
      <div className="relative">
        <div
          className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center micro-transition ${
            isActive
              ? 'theme-brand-primary text-white'
              : 'theme-bg-card theme-text-secondary'
          }`}
        >
          {isStreaming ? (
            <div className="w-4 h-4 border-2 border-[var(--text-muted)]/50 border-t-[var(--text-primary)] rounded-full animate-spin" />
          ) : (
            <span className="text-xs font-semibold">
              {conversation.title.charAt(0)}
            </span>
          )}
        </div>
        {hasNewReply && (
          <div className="absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 theme-bg-accent-emerald rounded-full shadow-sm shadow-[var(--accent-emerald)]/40" />
        )}
      </div>
      <div className="flex-1 min-w-0">
        {isEditing ? (
          <input
            ref={inputRef}
            type="text"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleSaveEdit}
            className="w-full px-2 py-1 text-sm theme-bg-input border theme-border-primary rounded theme-text-primary focus:outline-none focus:border-[var(--accent-sky)]/50"
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <p
            className={`text-sm font-medium truncate transition-colors duration-150 ${
              isActive ? 'theme-text-primary' : 'theme-text-secondary'
            }`}
          >
            {conversation.title}
          </p>
        )}
      </div>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 micro-transition">
        {isEditing ? (
          <>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleSaveEdit()
              }}
              className="p-2 rounded-lg hover:theme-bg-hover hover:scale-110 transition-all duration-200"
              title="保存"
            >
              <Check className="w-4 h-4 theme-accent-emerald" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleCancelEdit()
              }}
              className="p-2 rounded-lg hover:theme-bg-hover hover:scale-110 transition-all duration-200"
              title="取消"
            >
              <X className="w-4 h-4 theme-brand-danger" />
            </button>
          </>
        ) : (
          <>
            <button
              onClick={(e) => {
                e.stopPropagation()
                onPin(conversation.id, !conversation.pinned)
              }}
              className="p-2 rounded-lg hover:theme-bg-hover hover:scale-110 transition-all duration-200"
              title={conversation.pinned ? '取消置顶' : '置顶'}
            >
              <Pin
                className={`w-4 h-4 transition-colors ${
                  conversation.pinned
                    ? 'theme-accent-amber'
                    : 'theme-text-muted hover:theme-text-secondary'
                }`}
              />
            </button>
            <button
              onClick={handleStartEdit}
              className="p-2 rounded-lg hover:theme-bg-hover hover:scale-110 transition-all duration-200"
              title="编辑"
            >
              <Pencil className="w-4 h-4 theme-text-muted hover:theme-text-secondary" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation()
                onDelete()
              }}
              className="p-2 rounded-lg hover:theme-bg-hover hover:scale-110 transition-all duration-200"
              title="删除"
            >
              <Trash2 className="w-4 h-4 theme-text-muted hover:theme-brand-danger" />
            </button>
          </>
        )}
      </div>
    </div>
  )
}
