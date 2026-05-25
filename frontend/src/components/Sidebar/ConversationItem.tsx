import { useState, useEffect, useRef } from 'react';
import { Check, X, Pencil, Trash2 } from 'lucide-react';
import type { Conversation } from '../../types';

interface ConversationItemProps {
  conversation: Conversation;
  isActive: boolean;
  onClick: () => void;
  onDelete: () => void;
  onUpdate: (id: string, title: string) => void;
  collapsed?: boolean;
}

export function ConversationItem({ conversation, isActive, onClick, onDelete, onUpdate, collapsed = false }: ConversationItemProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editValue, setEditValue] = useState(conversation.title);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isEditing && inputRef.current) {
      inputRef.current.focus();
      inputRef.current.select();
    }
  }, [isEditing]);

  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    onDelete();
  };

  const handleStartEdit = (e: React.MouseEvent) => {
    e.stopPropagation();
    setEditValue(conversation.title);
    setIsEditing(true);
  };

  const handleSaveEdit = () => {
    const trimmedValue = editValue.trim();
    if (trimmedValue && trimmedValue !== conversation.title) {
      onUpdate(conversation.id, trimmedValue);
    }
    setIsEditing(false);
  };

  const handleCancelEdit = () => {
    setEditValue(conversation.title);
    setIsEditing(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSaveEdit();
    } else if (e.key === 'Escape') {
      handleCancelEdit();
    }
  };

  if (collapsed) {
    return (
      <div 
        onClick={onClick}
        className={`flex items-center justify-center py-2 px-1 rounded-lg cursor-pointer transition-all duration-200 ${
          isActive 
            ? 'bg-white/10' 
            : 'hover:bg-white/5'
        }`}
        title={conversation.title}
      >
        <div 
          className={`w-7 h-7 rounded-full flex items-center justify-center transition-all duration-200 ${
            isActive 
              ? 'bg-[#0EA5E9] text-white' 
              : 'bg-slate-600/60 text-slate-400'
          }`}
        >
          <span className="text-xs font-medium">
            {conversation.title.charAt(0)}
          </span>
        </div>
      </div>
    );
  }

  return (
    <div 
      onClick={isEditing ? undefined : onClick} 
      onContextMenu={handleContextMenu} 
      className={`group relative flex items-center gap-2 px-2 py-2 rounded-lg cursor-pointer transition-all duration-200 ${
        isActive 
          ? 'bg-white/10' 
          : 'hover:bg-white/5'
      }`}
    >
      <div 
        className={`flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center transition-all duration-200 ${
          isActive 
            ? 'bg-[#0EA5E9] text-white' 
            : 'bg-slate-700 text-slate-400'
        }`}
      >
        <span className="text-[10px] font-bold uppercase">
          {conversation.title.charAt(0)}
        </span>
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
          <p className={`text-sm truncate transition-colors duration-150 ${
            isActive ? 'text-white font-medium' : 'text-slate-400'
          }`}>
            {conversation.title}
          </p>
        )}
      </div>
      
      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
        {isEditing ? (
          <>
            <button 
              onClick={(e) => {
                e.stopPropagation();
                handleSaveEdit();
              }} 
              className="p-1 rounded hover:bg-white/10 transition-colors"
            >
              <Check className="w-3.5 h-3.5 text-green-400" />
            </button>
            <button 
              onClick={(e) => {
                e.stopPropagation();
                handleCancelEdit();
              }} 
              className="p-1.5 rounded hover:bg-white/10 transition-colors"
            >
              <X className="w-3.5 h-3.5 text-red-400" />
            </button>
          </>
        ) : (
          <>
            <button 
              onClick={handleStartEdit} 
              className="p-1.5 rounded hover:bg-white/10 transition-colors"
            >
              <Pencil className="w-3.5 h-3.5 text-slate-500" />
            </button>
            <button 
              onClick={(e) => {
                e.stopPropagation();
                onDelete();
              }} 
              className="p-1.5 rounded hover:bg-white/10 transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5 text-slate-500" />
            </button>
          </>
        )}
      </div>
    </div>
  );
}
