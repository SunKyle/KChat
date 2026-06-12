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
  const { showSettings, settingsTab, openSettings, closeSettings } = useSettings()
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

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      const edgeThreshold = 20
      const isNearRightEdge = e.clientX >= window.innerWidth - edgeThreshold
      
      if (isNearRightEdge && !noteTodoDrawerOpen) {
        setNoteTodoDrawerOpen(true)
      }
    }

    window.addEventListener('mousemove', handleMouseMove)
    return () => window.removeEventListener('mousemove', handleMouseMove)
  }, [noteTodoDrawerOpen])

  const handleSidebarEnter = useCallback(() => {
    setSidebarCollapsed(false)
  }, [setSidebarCollapsed])

  const handleDeleteClick = (id: string, title: string) => {
    setDeleteConfirm({ id, title })
  }

  const handleNoteTodoClick = () => {
    setNoteTodoDrawerOpen(true)
  }

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      remove(deleteConfirm.id)
      setDeleteConfirm(null)
    }
  }

  return (
    <>
      <div className='flex h-screen theme-bg-primary overflow-hidden theme-text-primary'>
        {sidebarOpen && (
          <div
            className='fixed inset-0 theme-bg-overlay z-40 lg:hidden'
            onClick={() => setSidebarOpen(false)}
          />
        )}

        <aside
          className={`
          fixed left-4 top-20 bottom-[max(1rem,env(safe-area-inset-bottom))] z-50 transition-all duration-300 ease-in-out
          lg:left-6 lg:top-6 lg:bottom-6
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
          onMouseEnter={handleSidebarEnter}
        >
          <div
            className={`h-full card-float-solid ${sidebarWidth} transition-[width] duration-[280ms] ease-[cubic-bezier(0.32,0.72,0,1)] will-change-[width]`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={toggleCollapsed}
              onDeleteClick={handleDeleteClick}
              onConversationClick={() => closeSettings()}
              onNoteTodoClick={handleNoteTodoClick}
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
          className='flex-1 flex flex-col overflow-hidden relative pt-20 pb-[max(1rem,env(safe-area-inset-bottom))] lg:pt-6 lg:pb-6 transition-[padding] duration-[280ms] ease-[cubic-bezier(0.32,0.72,0,1)] delay-[60ms]'
          style={
            isLg
              ? {
                  paddingLeft: sidebarCollapsed ? 112 : 336,
                  paddingRight: noteTodoDrawerOpen ? 448 : 48,
                }
              : undefined
          }
        >
          <div className='flex flex-col h-full card-float-solid'>
            <Header onSettingsClick={() => openSettings('profile')} />
            <div className={`flex-1 flex flex-col overflow-hidden ${showSettings ? 'hidden' : ''}`}>
              <ChatArea />
              {activeConversation && <InputArea />}
            </div>
            {showSettings && (
              <div className='flex-1 overflow-y-auto p-6'>
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

      <NoteTodoPanel isOpen={noteTodoDrawerOpen} onClose={() => setNoteTodoDrawerOpen(false)} />

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
