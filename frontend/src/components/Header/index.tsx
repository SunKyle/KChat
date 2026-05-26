import { Settings, Cpu, Database, BrainCircuit, ChevronDown, Check } from 'lucide-react';
import { useState, useRef, useEffect } from 'react';
import { useChat } from '../../context/ChatContext';

export function Header() {
  const { activeConversation, currentModel, availableModels, setCurrentModel } = useChat();
  const [isModelDropdownOpen, setIsModelDropdownOpen] = useState(false);
  const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
  const buttonRef = useRef<HTMLButtonElement>(null);
  const isOnline = true;

  const handleDropdownToggle = () => {
    if (!isModelDropdownOpen && buttonRef.current) {
      const rect = buttonRef.current.getBoundingClientRect();
      setDropdownPosition({
        top: rect.bottom + 4,
        left: rect.left,
      });
    }
    setIsModelDropdownOpen(!isModelDropdownOpen);
  };

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (isModelDropdownOpen && buttonRef.current && !buttonRef.current.contains(e.target as Node)) {
        setIsModelDropdownOpen(false);
      }
    };
    document.addEventListener('click', handleClickOutside);
    return () => document.removeEventListener('click', handleClickOutside);
  }, [isModelDropdownOpen]);

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
          <div>
            <button
              ref={buttonRef}
              onClick={handleDropdownToggle}
              className="flex items-center gap-1.5 px-2 py-0.5 bg-sky-500/10 text-sky-400 rounded-full border border-sky-500/20 micro-transition hover:bg-sky-500/20 cursor-pointer"
            >
              <Cpu className="w-3 h-3" />
              <span className="text-[10px] font-bold uppercase tracking-tight">{currentModel}</span>
              <ChevronDown className="w-3 h-3" />
            </button>
            
            {isModelDropdownOpen && (
              <div 
                className="fixed w-36 bg-[#1E293B] rounded-lg border border-white/10 shadow-xl z-[100] overflow-hidden"
                style={{ top: dropdownPosition.top, left: dropdownPosition.left }}
              >
                {availableModels.map((model) => (
                  <button
                    key={model}
                    onClick={() => {
                      setCurrentModel(model);
                      setIsModelDropdownOpen(false);
                    }}
                    className={`w-full px-3 py-2 text-left text-sm flex items-center justify-between hover:bg-white/5 transition-colors ${
                      model === currentModel ? 'text-sky-400' : 'text-slate-300'
                    }`}
                  >
                    <span className="capitalize">{model}</span>
                    {model === currentModel && <Check className="w-3 h-3" />}
                  </button>
                ))}
              </div>
            )}
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
