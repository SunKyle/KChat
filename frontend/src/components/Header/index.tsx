import { Bot, Settings, Wifi, WifiOff } from 'lucide-react';

export function Header() {
  const isOnline = true;

  return (
    <header className="h-14 bg-slate-800 border-b border-slate-700 flex items-center justify-between px-4">
      <div className="flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-primary-500 flex items-center justify-center">
          <Bot className="w-5 h-5 text-white" />
        </div>
        <div>
          <h1 className="text-lg font-semibold text-slate-100">KChat</h1>
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
