import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { SidebarRail, type MenuId } from './SidebarRail'
import { ChatPanel } from './ChatPanel'
import { GraphPanel } from './GraphPanel'
import { KnowledgePanel } from './KnowledgePanel'
import { SettingsPanel } from './SettingsPanel'
import type { KnowledgeBase } from '../../api/knowledge'
import type { SettingsTab } from '../../hooks/useSettings'

interface SidebarProps {
  collapsed?: boolean
  onToggle?: () => void
  onDeleteClick?: (id: string, title: string) => void
  onConversationClick?: () => void
  /** 知识库菜单点击：在主区域展示提取信息 */
  onSelectKnowledgeBase?: (kb: KnowledgeBase) => void
  /** 知识图谱菜单点击：在主区域展示图谱 */
  onSelectDataset?: (datasetName: string, displayName: string) => void
  /** 设置模式是否打开 */
  showSettings?: boolean
  /** 当前选中的设置项 */
  settingsTab?: SettingsTab
  /** 切换设置项 */
  onSettingsTabChange?: (tab: SettingsTab) => void
  /** 关闭设置模式（切回对话等场景） */
  onCloseSettings?: () => void
  /** 头像点击：打开或关闭设置 */
  onAvatarClick?: () => void
  /** 一级菜单切换回调（用于 App 侧监听当前菜单） */
  onActiveMenuChange?: (menu: MenuId) => void
  /** 当前选中的知识库 id（用于二级列表高亮） */
  selectedKbId?: string | null
  /** 当前选中的数据集名（用于二级列表高亮） */
  selectedDatasetName?: string | null
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
  onSelectKnowledgeBase,
  onSelectDataset,
  showSettings = false,
  settingsTab = 'profile',
  onSettingsTabChange,
  onCloseSettings,
  onAvatarClick,
  onActiveMenuChange,
  selectedKbId,
  selectedDatasetName,
}: SidebarProps) {
  const [activeMenu, setActiveMenu] = useState<MenuId>(() => {
    const saved = localStorage.getItem(ACTIVE_MENU_STORAGE_KEY)
    return isValidMenu(saved) ? saved : 'chat'
  })

  useEffect(() => {
    localStorage.setItem(ACTIVE_MENU_STORAGE_KEY, activeMenu)
    onActiveMenuChange?.(activeMenu)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeMenu])

  const handleMenuClick = (menu: MenuId) => {
    // 设置模式打开时，点击任意主菜单：关闭设置并切换到该菜单
    if (showSettings) {
      onCloseSettings?.()
      setActiveMenu(menu)
      if (collapsed) {
        onToggle?.()
      }
      return
    }
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
    // 设置模式优先渲染设置面板
    if (showSettings) {
      return (
        <SettingsPanel
          activeTab={settingsTab}
          onTabChange={(tab) => onSettingsTabChange?.(tab)}
          onToggle={() => onToggle?.()}
        />
      )
    }
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
            onSelectKnowledgeBase={(kb) => onSelectKnowledgeBase?.(kb)}
            selectedKbId={selectedKbId}
          />
        )
      case 'graph':
        return (
          <GraphPanel
            onToggle={() => onToggle?.()}
            onSelectDataset={(name, displayName) => onSelectDataset?.(name, displayName)}
            selectedDatasetName={selectedDatasetName}
          />
        )
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
        showSettings={showSettings}
        onAvatarClick={() => onAvatarClick?.()}
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
                key={showSettings ? 'settings' : activeMenu}
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
