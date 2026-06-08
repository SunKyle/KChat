import { Cpu, ChevronDown, Check, Settings } from 'lucide-react'
import { useState, useRef, useEffect } from 'react'
import { useChat } from '../../context/ChatContext'
import { ThemeToggle } from '../ui/ThemeToggle'

interface HeaderProps {
  onSettingsClick?: () => void
}

export function Header({ onSettingsClick }: HeaderProps) {
  const { activeConversation, currentModel, availableModels, setCurrentModel } = useChat()
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
    <header className='h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-secondary'>
      <div className='flex items-center gap-3'>
        {activeConversation ? (
          <h1 className='font-secondary font-medium theme-text-primary truncate max-w-lg'>
            {activeConversation.title}
          </h1>
        ) : (
          <h1 className='font-secondary theme-text-muted'>选择或创建对话</h1>
        )}
      </div>

      <div className='flex items-center gap-2 sm:gap-3'>
        <div className='hidden sm:block relative'>
          <button
            ref={buttonRef}
            onClick={handleDropdownToggle}
            className='flex items-center gap-2 px-3 py-1.5 sm:px-3.5 sm:py-2 bg-white rounded-lg border border-gray-200 shadow-sm shadow-black/5 hover:border-sky-300 hover:shadow-md hover:shadow-sky-500/15 transition-all duration-200 cursor-pointer'
          >
            <Cpu className='w-4 h-4 text-sky-600' />
            <span className='font-secondary font-medium text-sm sm:text-base theme-text-primary'>
              {currentModel}
            </span>
            <ChevronDown className='w-4 h-4 text-gray-400' />
          </button>

          {isModelDropdownOpen && (
            <div className='absolute top-full left-0 w-52 sm:w-56 mt-1.5 bg-white rounded-xl border border-gray-100 shadow-xl shadow-black/8 overflow-hidden z-50'>
              <div className='p-1'>
                {availableModels.map((model) => (
                  <button
                    key={model}
                    onClick={() => {
                      setCurrentModel(model)
                      setIsModelDropdownOpen(false)
                    }}
                    className={`w-full px-3 py-2 text-left text-sm sm:text-base flex items-center justify-between rounded-lg transition-all duration-150 ${
                      model === currentModel
                        ? 'bg-sky-50 text-sky-700'
                        : 'text-gray-700 hover:bg-gray-50'
                    }`}
                  >
                    <span className='capitalize'>{model}</span>
                    {model === currentModel && <Check className='w-4 h-4 text-sky-500' />}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className='flex items-center gap-2 sm:gap-3 border-l border-gray-200 pl-3 sm:pl-4'>
          {onSettingsClick && (
            <button
              onClick={onSettingsClick}
              className='flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-lg hover:bg-gray-100 text-gray-500 hover:text-gray-700 transition-all duration-200 cursor-pointer'
              title='设置'
            >
              <Settings className='w-4 h-4' />
            </button>
          )}
          <ThemeToggle />
          <div className='hidden sm:flex items-center gap-2'>
            {isOnline ? (
              <div className='flex items-center gap-1.5 px-2.5 py-1 bg-sky-50 rounded-full'>
                <div className='w-2 h-2 rounded-full bg-sky-500 shadow-sm shadow-sky-500/40 animate-pulse' />
                <span className='font-secondary text-xs sm:text-sm text-sky-700'>已连接</span>
              </div>
            ) : (
              <div className='flex items-center gap-1.5 px-2.5 py-1 bg-gray-100 rounded-full'>
                <div className='w-2 h-2 rounded-full bg-gray-400' />
                <span className='font-secondary text-xs sm:text-sm text-gray-600'>离线</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  )
}
