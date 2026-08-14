import { useState, useRef, useEffect } from 'react'
import { MessageSquare, Database, Share2, User, Settings } from 'lucide-react'
import { motion, AnimatePresence } from 'framer-motion'
import ProfileCard from '../common/ProfileCard'
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
}

export function SidebarRail({ activeMenu, onMenuClick }: SidebarRailProps) {
  const { profile } = useUser()
  const [showProfileCard, setShowProfileCard] = useState(false)
  const [profileCardPos, setProfileCardPos] = useState({ top: 0, left: 0 })
  const userAreaRef = useRef<HTMLDivElement>(null)

  const handleUserAreaClick = () => {
    if (userAreaRef.current) {
      const rect = userAreaRef.current.getBoundingClientRect()
      setProfileCardPos({ top: rect.top, left: rect.right })
    }
    setShowProfileCard((prev) => !prev)
  }

  const handleEditProfile = () => {
    setShowProfileCard(false)
    window.dispatchEvent(new CustomEvent('open-settings', { detail: { tab: 'profile' } }))
  }

  useEffect(() => {
    if (!showProfileCard) return
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setShowProfileCard(false)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [showProfileCard])

  return (
    <div
      role='navigation'
      aria-label='主导航'
      className='flex flex-col h-full w-16 flex-shrink-0 items-center py-3 gap-2 border-r theme-border-secondary'
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

      {/* 用户区 */}
      <div ref={userAreaRef} className='w-full px-2'>
        <div
          onClick={handleUserAreaClick}
          className='w-full flex items-center justify-center rounded-[var(--radius-xl)] bg-[var(--bg-card)]/60 p-1.5 transition-all duration-200 hover:border-[var(--brand-primary)]/20 hover:bg-[var(--bg-card)] hover:shadow-sm cursor-pointer'
        >
          <div className='rounded-full overflow-hidden flex-shrink-0 flex items-center justify-center bg-[var(--bg-card)] w-10 h-10'>
            {profile?.avatar ? (
              <img src={profile.avatar} alt='Avatar' className='w-full h-full object-cover' />
            ) : (
              <User className='w-4 h-4 text-[var(--text-muted)]' />
            )}
          </div>
        </div>
      </div>

      {/* 设置按钮 */}
      <button
        onClick={(e) => {
          e.stopPropagation()
          window.dispatchEvent(new CustomEvent('open-settings', { detail: { tab: 'profile' } }))
        }}
        className='flex-shrink-0 p-2 rounded-md hover:bg-[var(--bg-hover)] transition-colors theme-text-muted hover:theme-text-secondary'
        aria-label='设置'
        title='设置'
      >
        <Settings className='w-4 h-4' />
      </button>

      <AnimatePresence>
        {showProfileCard && (
          <>
            <div className='fixed inset-0 z-[999]' onClick={() => setShowProfileCard(false)} />
            <motion.div
              initial={{ opacity: 0, scale: 0.92, x: -12 }}
              animate={{ opacity: 1, scale: 1, x: 0 }}
              exit={{ opacity: 0, scale: 0.92, x: -12 }}
              transition={{ type: 'spring', stiffness: 380, damping: 28 }}
              className='fixed z-[1000] profile-card-popup'
              style={{
                bottom: window.innerHeight - profileCardPos.top + 12,
                left: profileCardPos.left + 12,
              }}
            >
              <ProfileCard
                avatarUrl={profile?.avatar}
                name={profile?.nickname || '用户'}
                title={profile?.bio || 'KChat 用户'}
                handle={profile?.email?.split('@')[0] || 'user'}
                status={profile?.privacy?.onlineStatus ? '在线' : '离线'}
                contactText='编辑资料'
                onContactClick={handleEditProfile}
                enableTilt
              />
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  )
}

export default SidebarRail
