import { useState, useEffect, useRef } from 'react'
import { Pencil, Trash2, Check, X } from 'lucide-react'
import type { Conversation } from '../../types'

interface ConversationItemProps {
  conversation: Conversation
  isActive: boolean
  isStreaming: boolean
  onClick: () => void
  onDelete: () => void
  onUpdate: (id: string, title: string) => void
  collapsed?: boolean
}

export function ConversationItem({
  conversation,
  isActive,
  isStreaming,
  onClick,
  onDelete,
  onUpdate,
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
        className={`relative flex items-center justify-center py-2 px-1 rounded-lg cursor-pointer micro-transition ${
          isActive ? 'bg-white/10' : 'hover:bg-white/5'
        }`}
        title={conversation.title}
      >
        <div
          className={`w-7 h-7 rounded-full flex items-center justify-center micro-transition ${
            isActive
              ? 'bg-[#0EA5E9] text-white'
              : 'bg-slate-600/60 text-slate-400'
          }`}
        >
          {isStreaming ? (
            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          ) : (
            <span className="text-xs font-medium">
              {conversation.title.charAt(0)}
            </span>
          )}
        </div>
        {isStreaming && (
          <div className="absolute -top-0.5 -right-0.5 w-3 h-3 bg-green-400 rounded-full animate-pulse shadow-lg shadow-green-400/50" />
        )}
      </div>
    )
  }

  return (
    <div
      onClick={isEditing ? undefined : onClick}
      onContextMenu={handleContextMenu}
      className={`group relative flex items-center gap-2 px-2 py-2 rounded-lg cursor-pointer micro-transition ${
        isActive ? 'bg-white/10 shadow-sm' : 'hover:bg-white/5'
      }`}
    >
      <div className="relative">
        <div
          className={`flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center micro-transition ${
            isActive ? 'bg-[#0EA5E9] text-white' : 'bg-slate-700 text-slate-400'
          }`}
        >
          <span className="text-[10px] font-bold uppercase">
            {conversation.title.charAt(0)}
          </span>
        </div>
        {isStreaming && (
          <div className="absolute -bottom-0.5 -right-0.5 w-3 h-3 bg-green-400 rounded-full animate-pulse shadow-lg shadow-green-400/50" />
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
            className="w-full px-1 py-0.5 text-sm bg-white/5 border border-white/10 rounded text-white focus:outline-none"
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <div className="flex items-center gap-2">
            <p
              className={`text-sm truncate transition-colors duration-150 ${
                isActive ? 'text-white font-medium' : 'text-slate-400'
              }`}
            >
              {conversation.title}
            </p>
            {isStreaming && (
              <div className="flex-shrink-0 flex items-center gap-1">
                <div className="flex gap-0.5">
                  <span
                    className="w-1 h-1 bg-green-400 rounded-full animate-bounce"
                    style={{ animationDelay: '0ms' }}
                  />
                  <span
                    className="w-1 h-1 bg-green-400 rounded-full animate-bounce"
                    style={{ animationDelay: '150ms' }}
                  />
                  <span
                    className="w-1 h-1 bg-green-400 rounded-full animate-bounce"
                    style={{ animationDelay: '300ms' }}
                  />
                </div>
                <span className="text-[10px] text-green-400 font-medium">
                  AI 回复中
                </span>
              </div>
            )}
          </div>
        )}
      </div>

      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 micro-transition">
        {isEditing ? (
          <>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleSaveEdit()
              }}
              className="p-1 rounded hover:bg-white/10 micro-transition"
            >
              <Check className="w-3.5 h-3.5 text-green-400" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleCancelEdit()
              }}
              className="p-1.5 rounded hover:bg-white/10 micro-transition"
            >
              <X className="w-3.5 h-3.5 text-red-400" />
            </button>
          </>
        ) : (
          <>
            <button
              onClick={handleStartEdit}
              className="p-1.5 rounded hover:bg-white/10 micro-transition"
            >
              <Pencil className="w-3.5 h-3.5 text-slate-500" />
            </button>
            <button
              onClick={(e) => {
                e.stopPropagation()
                onDelete()
              }}
              className="p-1.5 rounded hover:bg-white/10 micro-transition"
            >
              <Trash2 className="w-3.5 h-3.5 text-slate-500" />
            </button>
          </>
        )}
      </div>
    </div>
  )
}
