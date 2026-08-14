import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { SidebarRail, type MenuId } from './SidebarRail'
import { ChatPanel } from './ChatPanel'
import { GraphPanel } from './GraphPanel'
import { KnowledgePanel } from './KnowledgePanel'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
  onConversationClick?: () => void
  onSelectDataset?: (datasetName: string, displayName: string) => void
}

const ACTIVE_MENU_STORAGE_KEY = 'sidebarActiveMenu'

const isValidMenu = (value: string | null): value is MenuId => {
  return value === 'chat' || value === 'knowledge' || value === 'graph'
}

export function Sidebar({
  collapsed = false,
  onToggle,
  onDeleteClick,
  onConversationClick,
  onSelectDataset,
}: SidebarProps) {
  const [activeMenu, setActiveMenu] = useState<MenuId>(() => {
    const saved = localStorage.getItem(ACTIVE_MENU_STORAGE_KEY)
    return isValidMenu(saved) ? saved : 'chat'
  })

  useEffect(() => {
    localStorage.setItem(ACTIVE_MENU_STORAGE_KEY, activeMenu)
  }, [activeMenu])

  const handleMenuClick = (menu: MenuId) => {
    if (menu === activeMenu) {
      // 当前菜单已激活 → 切换收起/展开
      onToggle?.()
    } else {
      // 切换菜单；若当前收起则触发展开
      setActiveMenu(menu)
      if (collapsed) {
        onToggle?.()
      }
    }
  }

  const renderPanel = () => {
    switch (activeMenu) {
      case 'chat':
        return (
          <ChatPanel
            onToggle={() => onToggle?.()}
            onDeleteClick={(id, title) => onDeleteClick?.(id, title)}
            onConversationClick={() => onConversationClick?.()}
          />
        )
      case 'knowledge':
        return (
          <KnowledgePanel
            onToggle={() => onToggle?.()}
            onSelectDataset={(name, displayName) => onSelectDataset?.(name, displayName)}
          />
        )
      case 'graph':
        return (
          <GraphPanel
            onToggle={() => onToggle?.()}
            onSelectDataset={(name, displayName) => onSelectDataset?.(name, displayName)}
          />
        )
      default:
        return null
    }
  }

  return (
    <div className='flex h-full'>
      <SidebarRail activeMenu={activeMenu} onMenuClick={handleMenuClick} collapsed={collapsed} />

      <AnimatePresence initial={false}>
        {!collapsed && (
          <motion.div
            key='content-area'
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15, ease: [0.16, 1, 0.3, 1] }}
            className='flex-1 min-w-0 h-full overflow-hidden'
          >
            <AnimatePresence mode='wait'>
              <motion.div
                key={activeMenu}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 8 }}
                transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
                className='h-full'
              >
                {renderPanel()}
              </motion.div>
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default Sidebar
