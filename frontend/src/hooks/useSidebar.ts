import { useState } from 'react'

export function useSidebar() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(true)

  const toggleSidebar = () => setSidebarOpen(!sidebarOpen)
  const toggleCollapsed = () => setSidebarCollapsed(!sidebarCollapsed)

  return {
    sidebarOpen,
    setSidebarOpen,
    sidebarCollapsed,
    setSidebarCollapsed,
    toggleSidebar,
    toggleCollapsed,
    sidebarWidth: sidebarCollapsed ? 'w-16' : 'w-80',
  }
}
