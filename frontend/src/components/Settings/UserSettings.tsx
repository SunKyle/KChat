import { Loader2 } from 'lucide-react'
import { ProfileInfo } from './ProfileInfo'
import { Preferences } from './Preferences'
import { Privacy } from './Privacy'
import { APIKeys } from './APIKeys'
import { ModelSettings } from './ModelSettings'
import { ToolsPanel } from './ToolsPanel'
import { useUser } from '../../context/UserContext'
import type { SettingsTab } from '../../hooks/useSettings'

interface UserSettingsProps {
  activeTab: SettingsTab
}

export function UserSettings({ activeTab }: UserSettingsProps) {
  const { isLoading, error } = useUser()

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
      case 'agent':
        return <ToolsPanel />
      default:
        return <ProfileInfo />
    }
  }

  return <div className='min-h-full'>{renderContent()}</div>
}
