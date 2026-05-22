import { Trash2 } from 'lucide-react';
import type { Conversation } from '../../types';
interface ConversationItemProps {
 conversation: Conversation;
 isActive: boolean;
 onClick: () => void;
 onDelete: () => void;
}
export function ConversationItem({ conversation, isActive, onClick, onDelete, }: ConversationItemProps) {
 const handleContextMenu = (e: React.MouseEvent) => {
 e.preventDefault();
 onDelete();
 };
 return (<div onClick={onClick} onContextMenu={handleContextMenu} className={`flex items-center gap-3 px-3 py-2 rounded-lg cursor-pointer transition-all duration-200 hover:bg-slate-700/50 ${isActive ? 'bg-slate-700 border border-slate-600' : 'hover:bg-slate-700/50'}`}>
 <div className="flex-shrink-0 w-8 h-8 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center">
 <span className="text-white text-xs font-medium">
 {conversation.title.charAt(0)}
 </span>
 </div>
 <div className="flex-1 min-w-0">
 <p className="text-sm text-slate-200 truncate">{conversation.title}</p>
 <p className="text-xs text-slate-500 truncate">{conversation.createdAt}</p>
 </div>
 <button onClick={(e) => {
 e.stopPropagation();
 onDelete();
 }} className="opacity-0 hover:opacity-100 transition-opacity p-1 hover:bg-slate-600 rounded">
 <Trash2 className="w-4 h-4 text-slate-400"/>
 </button>
 </div>);
}
