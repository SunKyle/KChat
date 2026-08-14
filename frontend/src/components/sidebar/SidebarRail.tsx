import { MessageSquare, Database, Share2, User } from 'lucide-react'
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
  { id: 'graph', label: '知识图谱', icon: Share2 },
]

interface SidebarRailProps {
  activeMenu: MenuId
  onMenuClick: (menu: MenuId) => void
  collapsed: boolean
}

export function SidebarRail({ activeMenu, onMenuClick, collapsed }: SidebarRailProps) {
  const { profile } = useUser()

  const handleAvatarClick = () => {
    window.dispatchEvent(new CustomEvent('open-settings', { detail: { tab: 'profile' } }))
  }

  return (
    <div
      role='navigation'
      aria-label='主导航'
      className={`flex flex-col h-full w-16 flex-shrink-0 items-center py-3 gap-2 ${
        collapsed ? '' : 'border-r theme-border-secondary'
      }`}
    >
      {/* Logo */}
      <div className='w-full flex items-center justify-center mb-1'>
        <div className='relative flex-shrink-0'>
          <div className='absolute inset-0 rounded-xl bg-gradient-to-br from-[var(--brand-primary)]/40 to-[var(--accent-purple)]/40 blur-md opacity-25' />
          <img src='/kchat-icon.svg' alt='KChat' className='relative w-6 h-6 object-contain' />
        </div>
      </div>

      <div className='mx-3 w-[calc(100%-1.5rem)] divider' />

      {/* 一级菜单 */}
      <nav className='flex flex-col gap-1 w-full px-2 flex-1' aria-label='一级菜单'>
        {menus.map((menu) => {
          const Icon = menu.icon
          const isActive = activeMenu === menu.id
          return (
            <button
              key={menu.id}
              onClick={() => onMenuClick(menu.id)}
              aria-label={menu.label}
              aria-current={isActive ? 'page' : undefined}
              title={menu.label}
              className={`group relative w-full flex items-center justify-center h-10 rounded-lg transition-all duration-200 focus-ring ${
                isActive
                  ? 'bg-brand-selected theme-brand-primary'
                  : 'theme-text-muted hover:theme-bg-hover hover:theme-text-primary'
              }`}
            >
              <Icon className='w-5 h-5' aria-hidden='true' />
              {isActive && (
                <motion.div
                  layoutId='rail-active-indicator'
                  className='absolute left-0 top-1.5 bottom-1.5 w-[3px] rounded-full bg-[var(--brand-primary)]'
                  transition={{ type: 'spring', stiffness: 400, damping: 30 }}
                />
              )}
            </button>
          )
        })}
      </nav>

      {/* 用户头像：点击跳转设置 */}
      <div className='w-full px-2'>
        <button
          onClick={handleAvatarClick}
          aria-label='打开设置'
          title='设置'
          className='w-full flex items-center justify-center rounded-[var(--radius-xl)] bg-[var(--bg-card)]/60 p-1.5 transition-all duration-200 hover:border-[var(--brand-primary)]/20 hover:bg-[var(--bg-card)] hover:shadow-sm cursor-pointer focus-ring'
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
