import { useState, useEffect } from 'react'

export type SettingsTab = 'profile' | 'preferences' | 'privacy' | 'api' | 'models' | 'memory' | 'multimodal'

export function useSettings() {
  const [showSettings, setShowSettings] = useState(false)
  const [settingsTab, setSettingsTab] = useState<SettingsTab>('profile')

  const openSettings = (tab: SettingsTab = 'profile') => {
    setSettingsTab(tab)
    setShowSettings(true)
  }

  const closeSettings = () => setShowSettings(false)

  // 监听自定义事件
  useEffect(() => {
    const handleOpenSettings = (event: CustomEvent<{ tab: SettingsTab }>) => {
      setSettingsTab(event.detail.tab)
      setShowSettings(true)
    }

    window.addEventListener('open-settings', handleOpenSettings as EventListener)
    return () => {
      window.removeEventListener('open-settings', handleOpenSettings as EventListener)
    }
  }, [])

  return {
    showSettings,
    setShowSettings,
    settingsTab,
    setSettingsTab,
    openSettings,
    closeSettings,
  }
}
