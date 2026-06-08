import { Monitor, Bell, Mail, Volume2, Smartphone } from 'lucide-react'
import { useUser } from '../../context/UserContext'
import { useTheme } from '../../context/ThemeContext'

interface ToggleProps {
  enabled: boolean
  onChange: (value: boolean) => void
  disabled?: boolean
}

function Toggle({ enabled, onChange, disabled }: ToggleProps) {
  return (
    <button
      onClick={() => !disabled && onChange(!enabled)}
      disabled={disabled}
      className={`relative w-11 h-6 rounded-full transition-colors ${
        enabled ? 'bg-sky-500' : 'theme-bg-hover'
      } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
    >
      <span
        className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${
          enabled ? 'translate-x-5' : 'translate-x-0'
        }`}
      />
    </button>
  )
}

export function Preferences() {
  const { profile, updatePreferences, isLoading } = useUser()
  const { setTheme } = useTheme()

  const handleThemeChange = async (theme: 'dark' | 'light' | 'system') => {
    try {
      await updatePreferences({ theme })
      if (theme === 'system') {
        const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches
          ? 'dark'
          : 'light'
        setTheme(systemTheme)
      } else {
        setTheme(theme)
      }
    } catch (err) {
      console.error('Failed to update theme:', err)
    }
  }

  const handleNotificationChange = async (
    key: keyof NonNullable<typeof profile>['preferences']['notifications'],
    value: boolean
  ) => {
    if (!profile) return
    try {
      await updatePreferences({
        notifications: {
          ...profile.preferences.notifications,
          [key]: value,
        },
      })
    } catch (err) {
      console.error('Failed to update notifications:', err)
    }
  }

  if (!profile) return null

  const themes = [
    { id: 'dark' as const, label: '深色模式', description: '适合夜间使用' },
    { id: 'light' as const, label: '浅色模式', description: '适合白天使用' },
    { id: 'system' as const, label: '跟随系统', description: '根据系统设置自动切换' },
  ]

  return (
    <div className='space-y-6'>
      <div className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-6 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out'>
        <div className='flex items-center gap-2 mb-4'>
          <Monitor className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>外观</h3>
        </div>

        <div>
          <label className='block text-sm font-medium theme-text-secondary mb-3'>主题模式</label>
          <div className='grid grid-cols-3 gap-3'>
            {themes.map((theme) => (
              <button
                key={theme.id}
                onClick={() => handleThemeChange(theme.id)}
                disabled={isLoading}
                className={`relative p-3 rounded-lg border transition-all ${
                  profile.preferences.theme === theme.id
                    ? 'border-sky-500/50 bg-sky-500/10'
                    : 'theme-border-primary hover:theme-border-primary/80'
                } ${isLoading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
              >
                <div className='text-sm font-medium theme-text-primary mb-1'>{theme.label}</div>
                <div className='text-xs theme-text-muted'>{theme.description}</div>
                {profile.preferences.theme === theme.id && (
                  <div className='absolute top-2 right-2 w-2 h-2 bg-sky-500 rounded-full' />
                )}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className='theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl p-6 border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out'>
        <div className='flex items-center gap-2 mb-4'>
          <Bell className='w-5 h-5 theme-text-muted' />
          <h3 className='font-medium theme-text-primary'>通知设置</h3>
        </div>

        <div className='space-y-4'>
          <div className='flex items-center justify-between'>
            <div className='flex items-center gap-3'>
              <div className='w-8 h-8 rounded-lg theme-bg-hover flex items-center justify-center'>
                <Bell className='w-4 h-4 theme-text-muted' />
              </div>
              <div>
                <div className='text-sm font-medium theme-text-primary'>消息通知</div>
                <div className='text-xs theme-text-muted'>接收新消息时发送通知</div>
              </div>
            </div>
            <Toggle
              enabled={profile.preferences.notifications.message}
              onChange={(value) => handleNotificationChange('message', value)}
              disabled={isLoading}
            />
          </div>

          <div className='flex items-center justify-between'>
            <div className='flex items-center gap-3'>
              <div className='w-8 h-8 rounded-lg theme-bg-hover flex items-center justify-center'>
                <Mail className='w-4 h-4 theme-text-muted' />
              </div>
              <div>
                <div className='text-sm font-medium theme-text-primary'>邮件通知</div>
                <div className='text-xs theme-text-muted'>发送重要更新到您的邮箱</div>
              </div>
            </div>
            <Toggle
              enabled={profile.preferences.notifications.email}
              onChange={(value) => handleNotificationChange('email', value)}
              disabled={isLoading}
            />
          </div>

          <div className='flex items-center justify-between'>
            <div className='flex items-center gap-3'>
              <div className='w-8 h-8 rounded-lg theme-bg-hover flex items-center justify-center'>
                <Smartphone className='w-4 h-4 theme-text-muted' />
              </div>
              <div>
                <div className='text-sm font-medium theme-text-primary'>推送通知</div>
                <div className='text-xs theme-text-muted'>浏览器推送通知</div>
              </div>
            </div>
            <Toggle
              enabled={profile.preferences.notifications.push}
              onChange={(value) => handleNotificationChange('push', value)}
              disabled={isLoading}
            />
          </div>

          <div className='flex items-center justify-between'>
            <div className='flex items-center gap-3'>
              <div className='w-8 h-8 rounded-lg theme-bg-hover flex items-center justify-center'>
                <Volume2 className='w-4 h-4 theme-text-muted' />
              </div>
              <div>
                <div className='text-sm font-medium theme-text-primary'>通知声音</div>
                <div className='text-xs theme-text-muted'>接收通知时播放声音</div>
              </div>
            </div>
            <Toggle
              enabled={profile.preferences.notifications.sound}
              onChange={(value) => handleNotificationChange('sound', value)}
              disabled={isLoading}
            />
          </div>
        </div>
      </div>
    </div>
  )
}
