import { ChatProvider, useChat } from './context/ChatContext'
import { ModalProvider } from './context/ModalContext'
import { UserProvider } from './context/UserContext'
import { Sidebar } from './components/sidebar'
import { ChatArea } from './components/chat/ChatArea'
import { InputArea } from './components/chat/InputArea'
import { Header } from './components/chat/Header'
import { Modal } from './components/common/Modal'
import { ToastContainer } from './components/common/ToastContainer'
import { UserSettings } from './components/settings/UserSettings'
import { NoteTodoPanel } from './components/note-todo/NoteTodoPanel'
import { useState, useCallback, useEffect } from 'react'
import { Menu, X } from 'lucide-react'
import { useSidebar } from './hooks/useSidebar'
import { useSettings } from './hooks/useSettings'
import { useConversation } from './hooks/useConversation'

function AppContent() {
  const {
    sidebarOpen,
    sidebarCollapsed,
    sidebarWidth,
    setSidebarOpen,
    setSidebarCollapsed,
    toggleSidebar,
    toggleCollapsed,
  } = useSidebar()
  const { showSettings, settingsTab, closeSettings } = useSettings()
  const { activeConversation } = useChat()
  const { remove } = useConversation()
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; title: string } | null>(null)
  const [noteTodoDrawerOpen, setNoteTodoDrawerOpen] = useState(false)
  const [isLg, setIsLg] = useState(() => typeof window !== 'undefined' && window.innerWidth >= 1024)
  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const handler = (e: MediaQueryListEvent) => setIsLg(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const handleSidebarEnter = useCallback(() => {
    setSidebarCollapsed(false)
  }, [setSidebarCollapsed])

  const handleDeleteClick = (id: string, title: string) => {
    setDeleteConfirm({ id, title })
  }

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      remove(deleteConfirm.id)
      setDeleteConfirm(null)
    }
  }

  return (
    <>
      <div className='flex h-dvh theme-bg-primary overflow-hidden theme-text-primary'>
        <a
          href='#main-content'
          className='sr-only focus:not-sr-only focus:fixed focus:top-4 focus:left-4 focus:z-[9999] focus:px-4 focus:py-2 focus:bg-[var(--brand-primary)] focus:text-white focus:rounded-lg focus:shadow-lg focus:outline-none'
        >
          跳转到主内容
        </a>
        {sidebarOpen && (
          <div
            className='fixed inset-0 theme-bg-overlay z-40 lg:hidden'
            onClick={() => setSidebarOpen(false)}
          />
        )}

        <aside
          className={`
          fixed left-4 top-20 bottom-[max(1rem,env(safe-area-inset-bottom))] z-50 transition-all duration-300 ease-in-out
          lg:left-4 lg:top-4 lg:bottom-4
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
          onMouseEnter={handleSidebarEnter}
        >
          <div
            className={`h-full card-panel-quiet ${sidebarWidth} transition-[width] duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] will-change-[width]`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={toggleCollapsed}
              onDeleteClick={handleDeleteClick}
              onConversationClick={() => closeSettings()}
            />
          </div>
        </aside>

        <button
          onClick={toggleSidebar}
          className='fixed top-[max(1rem,env(safe-area-inset-top))] left-4 z-30 p-2 rounded-lg theme-bg-card lg:hidden shadow-md hover:theme-bg-hover transition-colors'
        >
          {sidebarOpen ? (
            <X className='w-5 h-5 theme-text-primary' />
          ) : (
            <Menu className='w-5 h-5 theme-text-primary' />
          )}
        </button>

        <div
          id='main-content'
          className='flex-1 flex flex-col overflow-hidden relative pt-20 pb-[max(1rem,env(safe-area-inset-bottom))] lg:pt-4 lg:pb-4 lg:pl-[var(--sidebar-pad,20rem)] lg:pr-[var(--note-pad,3.25rem)] transition-[padding] duration-[280ms] ease-[cubic-bezier(0.32,0.72,0,1)] delay-[60ms]'
          style={
            isLg
              ? ({
                  '--sidebar-pad': sidebarCollapsed ? '6rem' : '20rem',
                  '--note-pad': noteTodoDrawerOpen ? '27rem' : '3.25rem',
                } as React.CSSProperties)
              : undefined
          }
        >
          <div className='flex flex-col h-full card-float-solid relative overflow-hidden'>
            <div
              className='absolute inset-0 pointer-events-none'
              style={{
                background: `
                radial-gradient(ellipse 60% 50% at 50% 40%, var(--accent-primary-opacity-8, rgba(30,157,241,0.08)) 0%, transparent 70%),
                radial-gradient(ellipse 50% 40% at 80% 60%, var(--accent-purple-opacity-6, rgba(139,92,246,0.06)) 0%, transparent 70%),
                radial-gradient(ellipse 40% 35% at 20% 30%, var(--accent-amber-opacity-4, rgba(251,191,36,0.04)) 0%, transparent 60%)
              `,
              }}
            />
            <Header />
            <div
              className={`relative flex-1 flex flex-col overflow-hidden ${showSettings ? 'hidden' : ''}`}
            >
              <ChatArea />
              {activeConversation && <InputArea />}
            </div>
            {showSettings && (
              <div className='relative flex-1 overflow-y-auto p-6'>
                <UserSettings onClose={closeSettings} defaultTab={settingsTab} />
              </div>
            )}
          </div>
        </div>
      </div>

      <Modal
        isOpen={deleteConfirm !== null}
        title='删除对话'
        message={deleteConfirm ? `确定要删除"${deleteConfirm.title}"吗？此操作无法撤销。` : ''}
        confirmText='删除'
        cancelText='取消'
        onConfirm={handleConfirmDelete}
        onClose={() => setDeleteConfirm(null)}
        type='danger'
      />

      <NoteTodoPanel
        isOpen={noteTodoDrawerOpen}
        onClose={() => setNoteTodoDrawerOpen(false)}
        onOpen={() => setNoteTodoDrawerOpen(true)}
      />

      <ToastContainer />
    </>
  )
}

export default function App() {
  return (
    <UserProvider>
      <ChatProvider>
        <ModalProvider>
          <AppContent />
        </ModalProvider>
      </ChatProvider>
    </UserProvider>
  )
}
