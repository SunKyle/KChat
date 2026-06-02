import {
  Cpu,
  Database,
  BrainCircuit,
  ChevronDown,
  Check,
} from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useChat } from '../../context/ChatContext'

export function Header() {
  const { activeConversation, currentModel, availableModels, setCurrentModel } =
    useChat()
  const [isModelDropdownOpen, setIsModelDropdownOpen] = useState(false)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const isOnline = true

  const handleDropdownToggle = () => {
    setIsModelDropdownOpen(!isModelDropdownOpen)
  }

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        isModelDropdownOpen &&
        buttonRef.current &&
        !buttonRef.current.contains(e.target as Node)
      ) {
        setIsModelDropdownOpen(false)
      }
    }
    document.addEventListener('click', handleClickOutside)
    return () => document.removeEventListener('click', handleClickOutside)
  }, [isModelDropdownOpen])

  return (
    <header className="h-16 bg-[#0F172A]/80 backdrop-blur-md border-b border-white/5 flex items-center justify-between px-6 relative z-40">
      <div className="flex items-center gap-6">
        {activeConversation ? (
          <div className="px-4 py-1.5 bg-white/5 rounded-full border border-white/10 micro-transition hover:bg-white/10 cursor-default">
            <h1 className="text-sm font-medium text-[#E5E7EB] truncate max-w-lg transition-all">
              {activeConversation.title}
            </h1>
          </div>
        ) : (
          <div className="px-4 py-1.5 bg-white/5 rounded-full border border-white/10">
            <h1 className="text-sm font-medium text-slate-500">
              选择或创建对话
            </h1>
          </div>
        )}
      </div>

      <div className="flex items-center gap-4">
        <div className="hidden md:block relative">
          <button
            ref={buttonRef}
            onClick={handleDropdownToggle}
            className="flex items-center gap-2 px-4 py-2 bg-sky-500/10 text-sky-400 rounded-lg border border-sky-500/20 micro-transition hover:bg-sky-500/20 cursor-pointer"
          >
            <Cpu className="w-4 h-4" />
            <span className="text-xs font-bold uppercase tracking-tight">
              {currentModel}
            </span>
            <ChevronDown className="w-3.5 h-3.5" />
          </button>

          {isModelDropdownOpen && (
            <div className="absolute top-full left-0 w-44 mt-1 bg-[#1E293B] rounded-lg border border-white/10 shadow-xl overflow-hidden z-50">
              {availableModels.map((model) => (
                <button
                  key={model}
                  onClick={() => {
                    setCurrentModel(model)
                    setIsModelDropdownOpen(false)
                  }}
                  className={`w-full px-4 py-2.5 text-left text-sm flex items-center justify-between hover:bg-white/5 transition-colors ${
                    model === currentModel ? 'text-sky-400' : 'text-slate-300'
                  }`}
                >
                  <span className="capitalize">{model}</span>
                  {model === currentModel && <Check className="w-3.5 h-3.5" />}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="hidden md:flex items-center gap-1.5 px-3 py-2 bg-white/5 text-slate-400 rounded-lg border border-white/10 micro-transition">
          <Database className="w-4 h-4" />
          <span className="text-xs font-bold uppercase tracking-tight">
            8 / 10 <span className="opacity-50 text-[10px]">CTX</span>
          </span>
        </div>
        <div className="hidden md:flex items-center gap-1.5 px-3 py-2 bg-white/5 text-slate-400 rounded-lg border border-white/10 micro-transition">
          <BrainCircuit className="w-4 h-4" />
          <span className="text-xs font-bold uppercase tracking-tight">
            记忆开启
          </span>
        </div>

        <div className="flex items-center gap-4 border-l border-white/10 pl-5">
          <div className="flex items-center gap-2">
            {isOnline ? (
              <>
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                <span className="text-xs font-medium text-slate-400 uppercase tracking-tighter">
                  Connected
                </span>
              </>
            ) : (
              <>
                <div className="w-2 h-2 rounded-full bg-red-500" />
                <span className="text-xs font-medium text-slate-400 uppercase tracking-tighter">
                  Offline
                </span>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  )
}