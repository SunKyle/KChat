import { Trash2, Pencil, Check, X } from 'lucide-react';
import type { Conversation } from '../../types';
import { useState, useRef, useEffect } from 'react';

interface ConversationItemProps {
  conversation: Conversation;
  isActive: boolean;
  onClick: () => void;
  onDelete: () => void;
  onUpdate: (id: string, title: string) => void;
}

export function ConversationItem({ conversation, isActive, onClick, onDelete, onUpdate }: ConversationItemProps) {
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

  return (
    <div 
      onClick={isEditing ? undefined : onClick} 
      onContextMenu={handleContextMenu} 
      className={`relative flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition-all duration-300 ease-out ${
        isActive 
          ? 'bg-primary-500/20 border border-primary-500/50 shadow-lg shadow-primary-500/10' 
          : 'bg-transparent hover:bg-slate-700/40 border border-transparent'
      }`}
    >
      <div 
        className={`flex-shrink-0 w-9 h-9 rounded-full flex items-center justify-center transition-all duration-300 ${
          isActive 
            ? 'bg-gradient-to-br from-primary-400 to-primary-600 scale-105 shadow-md shadow-primary-500/30' 
            : 'bg-gradient-to-br from-primary-400/60 to-primary-600/60'
        }`}
      >
        <span className="text-white text-sm font-medium">
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
            className="w-full px-2 py-1 text-sm bg-slate-700/50 border border-primary-500/50 rounded text-white focus:outline-none focus:ring-2 focus:ring-primary-500/50"
            onClick={(e) => e.stopPropagation()}
          />
        ) : (
          <p className={`text-sm truncate transition-colors duration-200 ${
            isActive ? 'text-white font-medium' : 'text-slate-300'
          }`}>
            {conversation.title}
          </p>
        )}
        <p className="text-xs text-slate-500 truncate">{conversation.createdAt}</p>
      </div>
      
      {isEditing ? (
        <div className="flex gap-1">
          <button 
            onClick={(e) => {
              e.stopPropagation();
              handleSaveEdit();
            }} 
            className="p-1.5 hover:bg-green-500/20 rounded-lg transition-colors"
          >
            <Check className="w-4 h-4 text-green-400" />
          </button>
          <button 
            onClick={(e) => {
              e.stopPropagation();
              handleCancelEdit();
            }} 
            className="p-1.5 hover:bg-red-500/20 rounded-lg transition-colors"
          >
            <X className="w-4 h-4 text-red-400" />
          </button>
        </div>
      ) : (
        <button 
          onClick={handleStartEdit} 
          className="opacity-0 hover:opacity-100 transition-opacity p-1.5 hover:bg-slate-600/80 rounded-lg transform hover:scale-110"
        >
          <Pencil className="w-4 h-4 text-slate-400" />
        </button>
      )}
      
      <button 
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }} 
        className="opacity-0 hover:opacity-100 transition-opacity p-1.5 hover:bg-slate-600/80 rounded-lg transform hover:scale-110"
      >
        <Trash2 className="w-4 h-4 text-slate-400" />
      </button>
      
      {isActive && (
        <div className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 bg-primary-500 rounded-r-full" />
      )}
    </div>
  );
}
