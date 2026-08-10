import { useState, useEffect } from 'react'
import { User, Monitor, Lock, Key, Loader2, X, Brain, Database, Bot } from 'lucide-react'
import { ProfileInfo } from './ProfileInfo'
import { Preferences } from './Preferences'
import { Privacy } from './Privacy'
import { APIKeys } from './APIKeys'
import { ModelSettings } from './ModelSettings'
import { MemoryPanel } from './Memory/MemoryPanel'
import { useUser } from '../../context/UserContext'

type TabType = 'profile' | 'preferences' | 'privacy' | 'api' | 'models' | 'memory' | 'agent'

interface TabConfig {
  id: TabType
  label: string
  icon: typeof User
}

interface UserSettingsProps {
  onClose?: () => void
  defaultTab?: TabType
}

const tabs: TabConfig[] = [
  { id: 'profile', label: '基本信息', icon: User },
  { id: 'preferences', label: '偏好设置', icon: Monitor },
  { id: 'privacy', label: '隐私安全', icon: Lock },
  { id: 'api', label: 'API 密钥', icon: Key },
  { id: 'models', label: '模型管理', icon: Brain },
  { id: 'memory', label: '记忆管理', icon: Database },
  { id: 'agent', label: 'Agent 模式', icon: Bot },
]

export function UserSettings({ onClose, defaultTab = 'profile' }: UserSettingsProps) {
  const [activeTab, setActiveTab] = useState<TabType>(defaultTab)
  const { isLoading, error } = useUser()

  // 当 defaultTab 改变时更新 activeTab
  useEffect(() => {
    setActiveTab(defaultTab)
  }, [defaultTab])

  if (isLoading) {
    return (
      <div className='flex items-center justify-center min-h-[400px]'>
        <Loader2 className='w-6 h-6 theme-text-muted animate-spin' />
      </div>
    )
  }

  if (error) {
    return (
      <div className='p-6 text-center'>
        <p className='theme-text-muted'>{error}</p>
      </div>
    )
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'profile':
        return <ProfileInfo />
      case 'preferences':
        return <Preferences />
      case 'privacy':
        return <Privacy />
      case 'api':
        return <APIKeys />
      case 'models':
        return <ModelSettings />
      case 'memory':
        return <MemoryPanel />
      case 'agent':
        return (
          <div className='p-6 text-center'>
            <Bot className='w-10 h-10 mx-auto mb-3 theme-text-muted' />
            <p className='font-h3 mb-1'>Agent 模式配置开发中</p>
            <p className='font-secondary theme-text-muted'>敬请期待</p>
          </div>
        )
      default:
        return <ProfileInfo />
    }
  }

  return (
    <div className='min-h-full flex flex-col'>
      <div className='mb-6 flex items-start justify-between'>
        <div>
          <h1 className='font-h2 mb-1'>设置</h1>
          <p className='font-secondary theme-text-muted'>管理您的账户和偏好设置</p>
        </div>
        {onClose && (
          <button onClick={onClose} className='icon-btn' title='返回对话'>
            <X className='w-5 h-5' />
          </button>
        )}
      </div>

      <div className='flex-1 flex flex-col lg:flex-row gap-6 min-h-0'>
        <div className='lg:w-56 flex-shrink-0'>
          <nav className='sticky top-6'>
            <div className='space-y-1 p-3 rounded-2xl card-float-solid gap-1'>
              {tabs.map((tab) => {
                const Icon = tab.icon
                const isActive = activeTab === tab.id
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-xl transition-all ${
                      isActive
                        ? 'bg-[var(--accent-primary)]/8 text-[var(--accent-primary)]'
                        : 'theme-text-secondary hover:theme-bg-hover hover:theme-text-primary'
                    }`}
                  >
                    <Icon className='w-5 h-5' />
                    <span className='font-semibold'>{tab.label}</span>
                  </button>
                )
              })}
            </div>
          </nav>
        </div>

        <div className='flex-1 min-w-0'>{renderContent()}</div>
      </div>
    </div>
  )
}
