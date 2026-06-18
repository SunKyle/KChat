import { useState, useEffect, useRef } from 'react'
import { Pencil, Trash2, Check, X, Pin } from 'lucide-react'
import type { Conversation } from '../../types'

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
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const contextMenuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus()
      inputRef.current.select()
    }
  }, [isEditing])

  useEffect(() => {
    if (!contextMenu) return

    const handleClickOutside = (e: MouseEvent) => {
      if (contextMenuRef.current && !contextMenuRef.current.contains(e.target as Node)) {
        setContextMenu(null)
      }
    }
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setContextMenu(null)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [contextMenu])

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setContextMenu({ x: e.clientX, y: e.clientY })
  }

  const handleStartEdit = () => {
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
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            onClick()
          }
        }}
        tabIndex={0}
        role='button'
        title={conversation.title}
        aria-label={`会话: ${conversation.title}${isActive ? ' (当前选中)' : ''}${conversation.pinned ? ' (已置顶)' : ''}`}
        aria-current={isActive ? 'true' : undefined}
        className={`relative flex items-center justify-center w-10 h-10 mx-auto rounded-full cursor-pointer transition-all duration-200 ease-out focus-ring ${
          hasNewReply
            ? 'ring-1.5 ring-[var(--accent-emerald)]/30'
            : isActive
              ? 'bg-brand-selected'
              : 'hover:theme-bg-hover/60'
        }`}
      >
        {isStreaming && (
          <div className='absolute inset-[3px] rounded-full border-[1.5px] border-[var(--brand-primary)]/40 animate-stream-pulse' />
        )}
        <div
          className={`relative rounded-full flex items-center justify-center micro-transition ${
            isActive
              ? 'w-[26px] h-[26px] bg-brand-subtle theme-brand-primary'
              : 'w-7 h-7 theme-bg-card theme-text-secondary'
          }`}
        >
          {isStreaming ? (
            <div className='w-2 h-2 rounded-full bg-[var(--brand-primary)]' />
          ) : (
            <span
              className={`font-weight-semibold ${isActive ? 'text-[11px]' : 'font-conversation-name'}`}
            >
              {conversation.title.charAt(0)}
            </span>
          )}
        </div>
        {conversation.pinned && (
          <div className='absolute top-0.5 right-0.5 w-3.5 h-3.5 rounded-full bg-amber-400/90 flex items-center justify-center shadow-sm'>
            <Pin className='w-[7px] h-[7px] text-white' fill='currentColor' />
          </div>
        )}
      </div>
    )
  }

  const renderCardContent = () => (
    <div
      onClick={isEditing ? undefined : onClick}
      onContextMenu={handleContextMenu}
      onKeyDown={(e) => {
        if (!isEditing && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onClick()
        }
      }}
      tabIndex={0}
      role='button'
      aria-label={`会话: ${conversation.title}${isActive ? ' (当前选中)' : ''}${conversation.pinned ? ' (已置顶)' : ''}`}
      aria-current={isActive ? 'true' : undefined}
      className={`group relative flex items-center gap-2.5 pl-3.5 pr-2.5 py-2 rounded-lg cursor-pointer transition-all duration-200 ease-out focus-ring border-2 ${
        isActive
          ? 'bg-[#F0F9FF] border-l-[#0EA5E9] border-y-transparent border-r-transparent'
          : 'hover:theme-bg-hover/60 border-transparent'
      } ${isStreaming && !isActive ? 'animate-stream-bg' : ''}`}
    >
      <div
        className='flex-1 min-w-0 pr-20 sidebar-content-enter'
        style={{
          maskImage:
            'linear-gradient(to right, black calc(100% - 28px), transparent calc(100% - 4px))',
          WebkitMaskImage:
            'linear-gradient(to right, black calc(100% - 28px), transparent calc(100% - 4px))',
        }}
      >
        {isEditing ? (
          <input
            ref={inputRef}
            type='text'
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleSaveEdit}
            className='w-full px-2.5 py-1.5 font-secondary theme-bg-input border theme-border-primary rounded-lg theme-text-primary focus:outline-none focus:border-[var(--accent-sky)]/50'
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <p
            className={`font-conversation-name truncate transition-colors duration-150 ${
              isActive
                ? 'theme-brand-primary font-medium'
                : hasNewReply
                  ? 'theme-text-primary font-medium'
                  : 'theme-text-secondary'
            }`}
          >
            {conversation.title}
          </p>
        )}
      </div>
      <div className='absolute right-2.5 top-1/2 -translate-y-1/2 flex items-center gap-1'>
        {isStreaming && (
          <div className='w-[18px] h-[18px] rounded-full flex items-center justify-center'>
            <div className='w-4 h-4 border-2 border-[var(--brand-primary)]/40 border-t-[var(--brand-primary)] rounded-full animate-spin' />
          </div>
        )}
        <div
          className={`flex items-center gap-1 ${isEditing || isStreaming ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'} micro-transition`}
        >
          {isEditing ? (
            <>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  handleSaveEdit()
                }}
                aria-label='保存编辑'
                className='icon-btn focus-ring'
              >
                <Check className='w-[15px] h-[15px] theme-accent-emerald' aria-hidden='true' />
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  handleCancelEdit()
                }}
                aria-label='取消编辑'
                className='icon-btn focus-ring'
              >
                <X className='w-[15px] h-[15px] theme-brand-danger' aria-hidden='true' />
              </button>
            </>
          ) : !isStreaming ? (
            <>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  onPin(conversation.id, !conversation.pinned)
                }}
                aria-label={conversation.pinned ? '取消置顶' : '置顶会话'}
                aria-pressed={conversation.pinned}
                className='icon-btn focus-ring'
              >
                <Pin
                  className={`w-[15px] h-[15px] transition-colors ${
                    conversation.pinned
                      ? 'theme-accent-amber'
                      : 'theme-text-muted hover:theme-text-secondary'
                  }`}
                  fill={conversation.pinned ? 'currentColor' : 'none'}
                  aria-hidden='true'
                />
              </button>
              <button
                onClick={handleStartEdit}
                aria-label='编辑会话标题'
                className='icon-btn focus-ring'
              >
                <Pencil
                  className='w-[15px] h-[15px] theme-text-muted hover:theme-text-secondary'
                  aria-hidden='true'
                />
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation()
                  onDelete()
                }}
                aria-label='删除会话'
                className='icon-btn focus-ring'
              >
                <Trash2
                  className='w-[15px] h-[15px] theme-text-muted hover:theme-brand-danger'
                  aria-hidden='true'
                />
              </button>
            </>
          ) : null}
        </div>
      </div>
    </div>
  )

  const contextMenuContent = contextMenu ? (
    <div
      ref={contextMenuRef}
      className='fixed z-[100] min-w-[140px] py-1 bg-[var(--bg-dropdown)] rounded-lg border theme-border-secondary shadow-lg shadow-[var(--shadow-color-elevated)] overflow-hidden'
      style={{ left: contextMenu.x, top: contextMenu.y }}
    >
      <button
        onClick={() => {
          onPin(conversation.id, !conversation.pinned)
          setContextMenu(null)
        }}
        className='w-full px-3 py-1.5 text-left text-sm flex items-center gap-2 hover:bg-[var(--bg-dropdown-hover)] theme-text-secondary transition-colors'
      >
        <Pin className='w-3.5 h-3.5' fill={conversation.pinned ? 'currentColor' : 'none'} />{' '}
        {conversation.pinned ? '取消置顶' : '置顶'}
      </button>
      <button
        onClick={() => {
          handleStartEdit()
          setContextMenu(null)
        }}
        className='w-full px-3 py-1.5 text-left text-sm flex items-center gap-2 hover:bg-[var(--bg-dropdown-hover)] theme-text-secondary transition-colors'
      >
        <Pencil className='w-3.5 h-3.5' /> 编辑标题
      </button>
      <div className='my-0.5 divider' />
      <button
        onClick={() => {
          onDelete()
          setContextMenu(null)
        }}
        className='w-full px-3 py-1.5 text-left text-sm flex items-center gap-2 hover:bg-[var(--brand-danger)]/10 text-[var(--brand-danger)] transition-colors'
      >
        <Trash2 className='w-3.5 h-3.5' /> 删除
      </button>
    </div>
  ) : null

  return (
    <>
      {renderCardContent()}
      {contextMenuContent}
    </>
  )
}
