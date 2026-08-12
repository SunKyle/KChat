import { Cpu, ChevronDown, Check, WifiOff } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useChat } from '../../context/ChatContext'
import { useModel } from '../../hooks/useModel'
import { ThemeToggle } from '../common/ThemeToggle'
import { ConversationSettings } from './ConversationSettings'

export function Header() {
  const { activeConversation, currentModel, availableModels } = useChat()
  const { select } = useModel()
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
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isModelDropdownOpen) {
        setIsModelDropdownOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('click', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('click', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isModelDropdownOpen])

  return (
    <header className='relative z-10 h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-primary'>
      <div className='flex items-center gap-3 min-w-0'>
        {activeConversation ? (
          <h1 className='font-conversation-name font-semibold theme-text-primary truncate min-w-0 flex-shrink'>
            {activeConversation.title}
          </h1>
        ) : (
          <h1 className='font-conversation-name theme-text-muted'>选择或创建对话</h1>
        )}
      </div>

      <div className='flex items-center gap-2 sm:gap-3'>
        <div className='relative'>
          <button
            ref={buttonRef}
            onClick={handleDropdownToggle}
            className='flex items-center gap-1.5 sm:gap-2 px-2 sm:px-3.5 py-1.5 sm:py-2 bg-[var(--bg-card)] rounded-lg border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm hover:shadow-[var(--brand-primary)]/8 transition-all duration-200 cursor-pointer'
            aria-label='选择模型'
            aria-expanded={isModelDropdownOpen}
            aria-haspopup='listbox'
          >
            <Cpu className='w-3.5 h-3.5 sm:w-4 sm:h-4 theme-brand-primary' />
            <span className='text-xs sm:text-sm font-secondary theme-text-primary truncate max-w-[100px] sm:max-w-none'>
              {currentModel}
            </span>
            <ChevronDown
              className={`w-3.5 h-3.5 sm:w-4 sm:h-4 theme-text-muted transition-transform duration-200 ${isModelDropdownOpen ? 'rotate-180' : ''}`}
            />
          </button>

          {isModelDropdownOpen && (
            <div
              role='listbox'
              aria-label='模型列表'
              className='absolute top-full right-0 sm:left-0 w-48 sm:w-56 mt-1.5 bg-[var(--bg-dropdown)] rounded-xl border theme-border-secondary shadow-xl shadow-[var(--shadow-color-primary)] overflow-hidden z-50'
            >
              <div className='p-1'>
                {availableModels.map((model) => (
                  <button
                    key={model}
                    role='option'
                    aria-selected={model === currentModel}
                    onClick={() => {
                      select(model)
                      setIsModelDropdownOpen(false)
                    }}
                    className={`w-full px-3 py-2 text-left text-xs sm:text-sm flex items-center rounded-lg transition-all duration-150 font-secondary ${
                      model === currentModel
                        ? 'bg-[var(--bg-hover)] theme-brand-primary'
                        : 'theme-text-secondary hover:bg-[var(--bg-dropdown-hover)]'
                    }`}
                  >
                    <span>{model}</span>
                    {model === currentModel && (
                      <Check className='w-4 h-4 theme-brand-primary ml-auto' />
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className='flex items-center gap-2 sm:gap-3'>
          {!isOnline && (
            <div
              role='status'
              aria-label='服务离线'
              className='flex items-center gap-1.5 px-2 py-1 bg-red-500/10 rounded-full'
              title='服务离线，请检查网络连接'
            >
              <WifiOff className='w-3 h-3 text-red-500' />
              <span className='font-secondary text-xs text-red-500'>离线</span>
            </div>
          )}
          <ThemeToggle />
          <ConversationSettings />
        </div>
      </div>
    </header>
  )
}
