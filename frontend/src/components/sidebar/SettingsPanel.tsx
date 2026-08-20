import { User, Monitor, Lock, Key, Brain, Wrench, ChevronRight, Sparkles } from 'lucide-react'
import type { SettingsTab } from '../../hooks/useSettings'

interface SettingsPanelProps {
  activeTab: SettingsTab
  onTabChange: (tab: SettingsTab) => void
  onToggle: () => void
}

const tabs: { id: SettingsTab; label: string; icon: typeof User }[] = [
  { id: 'profile', label: '基本信息', icon: User },
  { id: 'preferences', label: '偏好设置', icon: Monitor },
  { id: 'privacy', label: '隐私安全', icon: Lock },
  { id: 'api', label: 'API 密钥', icon: Key },
  { id: 'models', label: '模型管理', icon: Brain },
  { id: 'agent', label: '工具管理', icon: Wrench },
  { id: 'skills', label: '技能中心', icon: Sparkles },
]

export function SettingsPanel({ activeTab, onTabChange, onToggle }: SettingsPanelProps) {
  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>设置</h2>
        <button
          onClick={onToggle}
          aria-label='收起侧边栏'
          className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
        >
          <ChevronRight className='w-4 h-4 rotate-180' aria-hidden='true' />
        </button>
      </div>

      {/* 设置项列表 */}
      <div className='flex-1 overflow-y-auto py-2 px-2 scrollbar-auto-hide'>
        <div className='space-y-0.5'>
          {tabs.map((tab) => {
            const Icon = tab.icon
            const isActive = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => onTabChange(tab.id)}
                aria-current={isActive ? 'page' : undefined}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-200 focus-ring ${
                  isActive
                    ? 'bg-[var(--accent-primary)]/8 text-[var(--accent-primary)]'
                    : 'theme-text-secondary hover:theme-bg-hover hover:theme-text-primary'
                }`}
              >
                <Icon className='w-4 h-4 flex-shrink-0' aria-hidden='true' />
                <span className='text-sm font-medium'>{tab.label}</span>
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export default SettingsPanel
