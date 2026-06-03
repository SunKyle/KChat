import { useState, useEffect } from 'react'
import {
  User,
  Monitor,
  Lock,
  Key,
  Loader2,
  X,
  Brain,
  Database,
} from 'lucide-react'
import { ProfileInfo } from './ProfileInfo'
import { Preferences } from './Preferences'
import { Privacy } from './Privacy'
import { APIKeys } from './APIKeys'
import { ModelSettings } from './ModelSettings'
import { MemoryPanel } from '../Memory/MemoryPanel'
import { useUser } from '../../context/UserContext'

type TabType =
  | 'profile'
  | 'preferences'
  | 'privacy'
  | 'api'
  | 'models'
  | 'memory'

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
]

export function UserSettings({
  onClose,
  defaultTab = 'profile',
}: UserSettingsProps) {
  const [activeTab, setActiveTab] = useState<TabType>(defaultTab)
  const { isLoading, error } = useUser()

  // 当 defaultTab 改变时更新 activeTab
  useEffect(() => {
    setActiveTab(defaultTab)
  }, [defaultTab])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-6 h-6 theme-text-muted animate-spin" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="p-6 text-center">
        <p className="theme-text-muted">{error}</p>
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
      default:
        return <ProfileInfo />
    }
  }

  return (
    <div className="min-h-full flex flex-col">
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold theme-text-primary mb-1">设置</h1>
          <p className="text-sm theme-text-muted">管理您的账户和偏好设置</p>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="p-2 rounded-lg theme-bg-card hover:theme-bg-hover transition-all duration-200 theme-text-muted hover:theme-text-primary"
            title="返回对话"
          >
            <X className="w-5 h-5" />
          </button>
        )}
      </div>

      <div className="flex-1 flex flex-col lg:flex-row gap-6 min-h-0">
        <div className="lg:w-56 flex-shrink-0">
          <nav className="sticky top-6">
            <div className="space-y-1 p-3 rounded-2xl theme-bg-sidebar/80 backdrop-blur-xl border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out">
              {tabs.map((tab) => {
                const Icon = tab.icon
                const isActive = activeTab === tab.id
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all ${
                      isActive
                        ? 'bg-sky-500/10 text-sky-400'
                        : 'theme-text-secondary hover:theme-bg-hover hover:theme-text-primary'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
                    <span className="font-medium">{tab.label}</span>
                  </button>
                )
              })}
            </div>
          </nav>
        </div>

        <div className="flex-1 min-w-0">{renderContent()}</div>
      </div>
    </div>
  )
}
