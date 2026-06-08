import { Cpu, Database, BrainCircuit, ChevronDown, Check, Settings } from 'lucide-react'
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
    <header className='h-14 card-float-solid flex items-center justify-between px-5 lg:px-6 relative z-40 mx-4 mt-6 lg:mx-6 rounded-xl'>
      <div className='flex items-center gap-3'>
        {activeConversation ? (
          <h1 className='font-secondary font-medium theme-text-primary truncate max-w-lg'>
            {activeConversation.title}
          </h1>
        ) : (
          <h1 className='font-secondary theme-text-muted'>选择或创建对话</h1>
        )}
      </div>

      <div className='flex items-center gap-3'>
        <div className='hidden md:block relative'>
          <button
            ref={buttonRef}
            onClick={handleDropdownToggle}
            className='flex items-center gap-2.5 px-3.5 py-2 bg-white/80 backdrop-blur-sm theme-brand-primary rounded-md border border-gray-200 hover:border-sky-300 hover:shadow-md hover:shadow-sky-500/10 transition-all duration-200 cursor-pointer'
          >
            <Cpu className='w-4 h-4' />
            <span className='font-model-name font-medium'>{currentModel}</span>
            <ChevronDown className='w-4 h-4 opacity-60' />
          </button>

          {isModelDropdownOpen && (
            <div className='absolute top-full left-0 w-48 mt-2 bg-white rounded-lg border border-gray-200 shadow-xl shadow-gray-500/5 overflow-hidden z-50'>
              {availableModels.map((model) => (
                <button
                  key={model}
                  onClick={() => {
                    setCurrentModel(model)
                    setIsModelDropdownOpen(false)
                  }}
                  className={`w-full px-4 py-2.5 text-left text-sm flex items-center justify-between hover:bg-gray-50 transition-colors ${
                    model === currentModel ? 'theme-brand-primary' : 'theme-text-secondary'
                  }`}
                >
                  <span className='capitalize'>{model}</span>
                  {model === currentModel && <Check className='w-4 h-4' />}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className='hidden md:flex items-center gap-1.5 px-3 py-1.5 bg-gray-50 rounded-md'>
          <Database className='w-4 h-4 text-gray-500' />
          <span className='font-secondary text-sm text-gray-600'>
            8 / 10 <span className='opacity-40'>CTX</span>
          </span>
        </div>
        <div className='hidden md:flex items-center gap-1.5 px-3 py-1.5 bg-sky-50 rounded-md'>
          <BrainCircuit className='w-4 h-4 text-sky-600' />
          <span className='font-secondary text-sm text-sky-700'>记忆开启</span>
        </div>

        <div className='flex items-center gap-3 border-l border-gray-200 pl-4'>
          {onSettingsClick && (
            <button
              onClick={onSettingsClick}
              className='flex items-center justify-center w-9 h-9 rounded-lg hover:bg-gray-100 hover:text-gray-700 text-gray-500 transition-all duration-200 cursor-pointer'
              title='设置'
            >
              <Settings className='w-4 h-4' />
            </button>
          )}
          <ThemeToggle />
          <div className='flex items-center gap-2'>
            {isOnline ? (
              <div className='flex items-center gap-2 px-3 py-1.5 bg-sky-50 rounded-full'>
                <div className='w-2.5 h-2.5 rounded-full bg-sky-500 shadow-md shadow-sky-500/40 animate-pulse' />
                <span className='font-secondary text-sm text-sky-700'>已连接</span>
              </div>
            ) : (
              <div className='flex items-center gap-2 px-3 py-1.5 bg-gray-100 rounded-full'>
                <div className='w-2.5 h-2.5 rounded-full bg-gray-400' />
                <span className='font-secondary text-sm text-gray-600'>离线</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  )
}
