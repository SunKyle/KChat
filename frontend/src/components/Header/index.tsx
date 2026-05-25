import { Settings, Cpu, Database, BrainCircuit } from 'lucide-react';
import { useChat } from '../../context/ChatContext';

export function Header() {
  const { activeConversation } = useChat();
  const isOnline = true;

  return (
    <header className="h-14 bg-[#0F172A]/80 backdrop-blur-md border-b border-white/5 flex items-center justify-between px-6">
      <div className="flex items-center gap-4">
        {activeConversation ? (
          <div className="px-3 py-1 bg-white/5 rounded-full border border-white/10 micro-transition hover:bg-white/10 cursor-default">
            <h1 className="text-sm font-medium text-[#E5E7EB] truncate max-w-md transition-all">
              {activeConversation.title}
            </h1>
          </div>
        ) : (
          <div className="px-3 py-1 bg-white/5 rounded-full border border-white/10">
            <h1 className="text-sm font-medium text-slate-500">选择或创建对话</h1>
          </div>
        )}
      </div>

      <div className="flex items-center gap-4">
        <div className="hidden md:flex items-center gap-2 px-2 py-1 bg-white/5 rounded-full border border-white/10">
          <div className="flex items-center gap-1.5 px-2 py-0.5 bg-sky-500/10 text-sky-400 rounded-full border border-sky-500/20 micro-transition">
            <Cpu className="w-3 h-3" />
            <span className="text-[10px] font-bold uppercase tracking-tight">llama3</span>
          </div>
          <div className="flex items-center gap-1.5 px-2 py-0.5 bg-slate-500/10 text-slate-400 rounded-full border border-slate-500/20 micro-transition">
            <Database className="w-3 h-3" />
            <span className="text-[10px] font-bold uppercase tracking-tight">8 / 10 <span className="opacity-50 text-[8px]">CTX</span></span>
          </div>
          <div className="flex items-center gap-1.5 px-2 py-0.5 bg-slate-500/10 text-slate-400 rounded-full border border-slate-500/20 micro-transition">
            <BrainCircuit className="w-3 h-3" />
            <span className="text-[10px] font-bold uppercase tracking-tight">记忆开启</span>
          </div>
        </div>

        <div className="flex items-center gap-3 border-l border-white/10 pl-4">
          <div className="flex items-center gap-1.5">
            {isOnline ? (
              <>
                <div className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" />
                <span className="text-[11px] font-medium text-slate-400 uppercase tracking-tighter">Connected</span>
              </>
            ) : (
              <>
                <div className="w-1.5 h-1.5 rounded-full bg-red-500" />
                <span className="text-[11px] font-medium text-slate-400 uppercase tracking-tighter">Offline</span>
              </>
            )}
          </div>

          <button className="p-1.5 rounded-md hover:bg-white/5 micro-transition text-slate-500 hover:text-slate-300">
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
}
