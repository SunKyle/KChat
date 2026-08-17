import { MessageSquare, Database, Network, User } from 'lucide-react'
import { motion } from 'framer-motion'
import { useUser } from '../../context/UserContext'

export type MenuId = 'chat' | 'knowledge' | 'graph'

interface MenuConfig {
  id: MenuId
  label: string
  icon: typeof MessageSquare
}

const menus: MenuConfig[] = [
  { id: 'chat', label: '对话', icon: MessageSquare },
  { id: 'knowledge', label: '知识库', icon: Database },
  { id: 'graph', label: '知识图谱', icon: Network },
]

interface SidebarRailProps {
  activeMenu: MenuId
  onMenuClick: (menu: MenuId) => void
  collapsed: boolean
  showSettings: boolean
  onAvatarClick: () => void
}

export function SidebarRail({
  activeMenu,
  onMenuClick,
  collapsed,
  showSettings,
  onAvatarClick,
}: SidebarRailProps) {
  const { profile } = useUser()

  return (
    <div
      role='navigation'
      aria-label='主导航'
      className={`flex flex-col h-full w-16 flex-shrink-0 items-center ${
        collapsed ? '' : 'border-r border-[var(--border-divider)]'
      }`}
    >
      {/* Logo 区域：h-14 对齐主对话区 Header，底部 border-b 与 Header 分割线一致 */}
      <div className='h-14 w-full flex items-center justify-center flex-shrink-0 border-b theme-border-primary'>
        <div className='relative flex-shrink-0'>
          <div className='absolute inset-0 rounded-xl bg-gradient-to-br from-[var(--brand-primary)]/40 to-[var(--accent-purple)]/40 blur-md opacity-25' />
          <img src='/kchat-icon.svg' alt='KChat' className='relative w-6 h-6 object-contain' />
        </div>
      </div>

      {/* 一级菜单 */}
      <nav className='flex flex-col gap-1 w-full px-2 py-2 flex-1' aria-label='一级菜单'>
        {menus.map((menu) => {
          const Icon = menu.icon
          // 设置模式打开时，主菜单不高亮
          const isActive = !showSettings && activeMenu === menu.id
          return (
            <button
              key={menu.id}
              onClick={() => onMenuClick(menu.id)}
              aria-label={menu.label}
              aria-current={isActive ? 'page' : undefined}
              title={menu.label}
              className={`group relative w-full flex items-center justify-center h-10 rounded-lg transition-all duration-200 focus-ring ${
                isActive ? 'theme-brand-primary' : 'theme-text-muted hover:theme-text-primary'
              }`}
            >
              <span
                className={`flex items-center justify-center w-8 h-8 rounded-lg transition-all duration-200 ${
                  isActive ? 'bg-brand-selected' : 'group-hover:theme-bg-hover'
                }`}
              >
                <Icon className='w-5 h-5' aria-hidden='true' />
              </span>
              {isActive && (
                <motion.div
                  layoutId='rail-active-indicator'
                  className='absolute left-0 top-2 bottom-2 w-[3px] rounded-full bg-[var(--brand-primary)]'
                  transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                />
              )}
            </button>
          )
        })}
      </nav>

      {/* 用户头像：点击打开/关闭设置 */}
      <div className='w-full px-2 pb-3'>
        <button
          onClick={onAvatarClick}
          aria-label={showSettings ? '关闭设置' : '打开设置'}
          title='设置'
          className={`w-full flex items-center justify-center rounded-[var(--radius-xl)] p-1.5 transition-all duration-200 cursor-pointer focus-ring ${
            showSettings
              ? 'border border-[var(--brand-primary)]/40 bg-[var(--bg-card)] shadow-sm'
              : 'bg-[var(--bg-card)]/60 hover:border-[var(--brand-primary)]/20 hover:bg-[var(--bg-card)] hover:shadow-sm'
          }`}
        >
          <div className='rounded-full overflow-hidden flex-shrink-0 flex items-center justify-center bg-[var(--bg-card)] w-10 h-10'>
            {profile?.avatar ? (
              <img src={profile.avatar} alt='Avatar' className='w-full h-full object-cover' />
            ) : (
              <User className='w-4 h-4 text-[var(--text-muted)]' />
            )}
          </div>
        </button>
      </div>
    </div>
  )
}

export default SidebarRail
