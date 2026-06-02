import {
  Cpu,
  Database,
  BrainCircuit,
  ChevronDown,
  Check,
  Settings,
} from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useChat } from '../../context/ChatContext'
import { ThemeToggle } from '../ui/ThemeToggle'

interface HeaderProps {
  onSettingsClick?: () => void
}

export function Header({ onSettingsClick }: HeaderProps) {
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
    <header className="h-16 theme-bg-primary/80 backdrop-blur-md border-b theme-border-secondary flex items-center justify-between px-6 relative z-40">
      <div className="flex items-center gap-6">
        {activeConversation ? (
          <div className="px-4 py-1.5 theme-bg-hover/50 rounded-full border theme-border-primary micro-transition hover:theme-bg-hover cursor-default">
            <h1 className="text-sm font-medium theme-text-primary truncate max-w-lg transition-all">
              {activeConversation.title}
            </h1>
          </div>
        ) : (
          <div className="px-4 py-1.5 theme-bg-hover/50 rounded-full border theme-border-primary">
            <h1 className="text-sm font-medium theme-text-muted">
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
            className="flex items-center gap-2 px-4 py-2 theme-bg-hover/50 theme-brand-primary rounded-lg border theme-border-primary micro-transition hover:theme-bg-hover cursor-pointer"
          >
            <Cpu className="w-4 h-4" />
            <span className="text-xs font-bold uppercase tracking-tight">
              {currentModel}
            </span>
            <ChevronDown className="w-3.5 h-3.5" />
          </button>

          {isModelDropdownOpen && (
            <div className="absolute top-full left-0 w-44 mt-1 theme-bg-card rounded-lg border theme-border-primary shadow-xl overflow-hidden z-50">
              {availableModels.map((model) => (
                <button
                  key={model}
                  onClick={() => {
                    setCurrentModel(model)
                    setIsModelDropdownOpen(false)
                  }}
                  className={`w-full px-4 py-2.5 text-left text-sm flex items-center justify-between hover:theme-bg-hover transition-colors ${
                    model === currentModel ? 'theme-brand-primary' : 'theme-text-secondary'
                  }`}
                >
                  <span className="capitalize">{model}</span>
                  {model === currentModel && <Check className="w-3.5 h-3.5" />}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="hidden md:flex items-center gap-1.5 px-3 py-2 theme-bg-hover/50 theme-text-secondary rounded-lg border theme-border-primary micro-transition">
          <Database className="w-4 h-4" />
          <span className="text-xs font-bold uppercase tracking-tight">
            8 / 10 <span className="opacity-50 text-[10px]">CTX</span>
          </span>
        </div>
        <div className="hidden md:flex items-center gap-1.5 px-3 py-2 theme-bg-hover/50 theme-text-secondary rounded-lg border theme-border-primary micro-transition">
          <BrainCircuit className="w-4 h-4" />
          <span className="text-xs font-bold uppercase tracking-tight">
            记忆开启
          </span>
        </div>

        <div className="flex items-center gap-4 border-l theme-border-primary pl-5">
          {onSettingsClick && (
            <button
              onClick={onSettingsClick}
              className="p-2 rounded-lg theme-bg-hover/30 hover:theme-bg-hover hover:scale-105 transition-all duration-200 theme-text-muted hover:theme-text-primary"
              title="设置"
            >
              <Settings className="w-5 h-5" />
            </button>
          )}
          <ThemeToggle />
          <div className="flex items-center gap-2">
            {isOnline ? (
              <>
                <div className="w-2 h-2 rounded-full theme-bg-accent-emerald animate-pulse" />
                <span className="text-xs font-medium theme-text-secondary uppercase tracking-tighter">
                  Connected
                </span>
              </>
            ) : (
              <>
                <div className="w-2 h-2 rounded-full theme-bg-brand-danger" />
                <span className="text-xs font-medium theme-text-secondary uppercase tracking-tighter">
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