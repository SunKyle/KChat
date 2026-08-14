import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Database, ChevronRight } from 'lucide-react'
import { SidebarRail, type MenuId } from './SidebarRail'
import { ChatPanel } from './ChatPanel'
import { GraphPanel } from './GraphPanel'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
  onConversationClick?: () => void
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
        return <KnowledgePanelPlaceholder onToggle={() => onToggle?.()} />
      case 'graph':
        return <GraphPanel onToggle={() => onToggle?.()} />
      default:
        return null
    }
  }

  return (
    <div className='flex h-full'>
      <SidebarRail
        activeMenu={activeMenu}
        onMenuClick={handleMenuClick}
        collapsed={collapsed}
      />

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

/** 知识库占位面板（功能开发中） */
function KnowledgePanelPlaceholder({ onToggle }: { onToggle: () => void }) {
  return (
    <div className='flex flex-col h-full'>
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>知识库</h2>
        <button
          onClick={onToggle}
          aria-label='收起侧边栏'
          className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
        >
          <ChevronRight className='w-4 h-4 rotate-180' aria-hidden='true' />
        </button>
      </div>

      <div className='flex-1 flex items-center justify-center px-6'>
        <div className='text-center'>
          <div className='w-14 h-14 mx-auto mb-4 rounded-2xl theme-bg-hover/50 flex items-center justify-center'>
            <Database className='w-7 h-7 theme-text-muted' aria-hidden='true' />
          </div>
          <p className='theme-text-secondary text-sm mb-1 font-semibold'>知识库功能开发中</p>
          <p className='text-xs theme-text-muted'>即将支持文档上传、检索与引用</p>
        </div>
      </div>
    </div>
  )
}

export default Sidebar
