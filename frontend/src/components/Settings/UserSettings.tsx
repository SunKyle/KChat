import { useState } from 'react'
import { User, Monitor, Lock, Key, Loader2 } from 'lucide-react'
import { ProfileInfo } from './ProfileInfo'
import { Preferences } from './Preferences'
import { Privacy } from './Privacy'
import { APIKeys } from './APIKeys'
import { useUser } from '../../context/UserContext'

type TabType = 'profile' | 'preferences' | 'privacy' | 'api'

interface TabConfig {
  id: TabType
  label: string
  icon: typeof User
}

const tabs: TabConfig[] = [
  { id: 'profile', label: '基本信息', icon: User },
  { id: 'preferences', label: '偏好设置', icon: Monitor },
  { id: 'privacy', label: '隐私安全', icon: Lock },
  { id: 'api', label: 'API 密钥', icon: Key },
]

export function UserSettings() {
  const [activeTab, setActiveTab] = useState<TabType>('profile')
  const { isLoading, error } = useUser()

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
      default:
        return <ProfileInfo />
    }
  }

  return (
    <div className="min-h-full">
      <div className="mb-6">
        <h1 className="text-2xl font-bold theme-text-primary mb-1">设置</h1>
        <p className="text-sm theme-text-muted">管理您的账户和偏好设置</p>
      </div>

      <div className="flex flex-col lg:flex-row gap-6">
        <div className="lg:w-56 flex-shrink-0">
          <nav className="lg:sticky lg:top-6">
            <div className="space-y-1 p-1 rounded-xl theme-bg-card border theme-border-primary">
              {tabs.map((tab) => {
                const Icon = tab.icon
                const isActive = activeTab === tab.id
                return (
                  <button
                    key={tab.id}
                    onClick={() => setActiveTab(tab.id)}
                    className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all ${
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

        <div className="flex-1 min-w-0">
          {renderContent()}
        </div>
      </div>
    </div>
  )
}