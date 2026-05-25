import { Settings, Wifi, WifiOff } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function Header() {
  const { activeConversation } = useChat();
  const isOnline = true;

  return (
    <header className="h-14 bg-slate-800/80 backdrop-blur-md border-b border-slate-700/50 flex items-center justify-between px-4">
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-3">
          <div className="text-sm text-slate-400">
            {activeConversation ? (
              <h1 className="text-lg font-semibold text-slate-100 truncate max-w-md">
                {activeConversation.title}
              </h1>
            ) : (
              <h1 className="text-lg font-semibold text-slate-400">选择或创建对话</h1>
            )}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-700/50 rounded-full">
          <div className="w-2 h-2 rounded-full bg-primary-400" />
          <span className="text-sm text-slate-300">llama3</span>
        </div>

        <div className="flex items-center gap-2">
          {isOnline ? (
            <>
              <Wifi className="w-4 h-4 text-green-400" />
              <span className="text-xs text-green-400">在线</span>
            </>
          ) : (
            <>
              <WifiOff className="w-4 h-4 text-red-400" />
              <span className="text-xs text-red-400">离线</span>
            </>
          )}
        </div>

        <button className="p-2 rounded-lg hover:bg-slate-700 transition-colors">
          <Settings className="w-5 h-5 text-slate-400" />
        </button>
      </div>
    </header>
  );
}
