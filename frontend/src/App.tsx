import { ChatProvider, useChat } from './context/ChatContext'
import { ModalProvider } from './context/ModalContext'
import { UserProvider } from './context/UserContext'
import { Sidebar } from './components/layout/Sidebar'
import { ChatArea } from './components/ChatArea'
import { InputArea } from './components/InputArea'
import { Header } from './components/layout/Header'
import { ConfirmDialog } from './components/common/ConfirmDialog'
import { UserSettings } from './components/Settings/UserSettings'
import { useState } from 'react'
import { Menu, X, ChevronLeft, ChevronRight } from 'lucide-react'
import { useSidebar } from './hooks/useSidebar'
import { useSettings } from './hooks/useSettings'

function AppContent() {
  const {
    sidebarOpen,
    sidebarCollapsed,
    sidebarWidth,
    setSidebarOpen,
    toggleCollapsed,
    toggleSidebar,
  } = useSidebar()
  const { showSettings, settingsTab, openSettings, closeSettings } = useSettings()
  const { deleteConversation, activeConversation } = useChat()
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; title: string } | null>(null)

  const handleDeleteClick = (id: string, title: string) => {
    setDeleteConfirm({ id, title })
  }

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      deleteConversation(deleteConfirm.id)
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
          group/sidebar fixed left-4 top-20 bottom-4 z-50 transition-all duration-300 ease-in-out
          lg:left-6 lg:top-6 lg:bottom-6
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
        >
          {/* 收起/展开条 — 在侧边栏下层，从右边缘露出 */}
          <button
            onClick={toggleCollapsed}
            aria-label={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
            className='absolute inset-y-0 -right-5 z-0 w-7 flex items-center justify-center bg-sky-200/40 group-hover/sidebar:bg-sky-200/70 hover:!bg-sky-300/80 rounded-r-xl cursor-pointer scale-x-0 group-hover/sidebar:scale-x-100 transition-all duration-200 ease-out shadow-[0_1px_3px_rgba(0,0,0,0.06),0_4px_12px_rgba(0,0,0,0.04)] hover:shadow-[0_2px_8px_rgba(0,0,0,0.08),0_8px_24px_rgba(0,0,0,0.06)] focus-ring'
            style={{ transformOrigin: 'left center' }}
          >
            {sidebarCollapsed ? (
              <ChevronRight className='w-4 h-4 text-sky-700' />
            ) : (
              <ChevronLeft className='w-4 h-4 text-sky-700' />
            )}
          </button>

          <div className={`relative z-10 h-full card-float-solid ${sidebarWidth}`}>
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
          className='fixed top-4 left-4 z-30 p-2 rounded-lg theme-bg-card lg:hidden shadow-md hover:theme-bg-hover transition-colors'
        >
          {sidebarOpen ? (
            <X className='w-5 h-5 theme-text-primary' />
          ) : (
            <Menu className='w-5 h-5 theme-text-primary' />
          )}
        </button>

        <div
          className={`flex-1 flex flex-col overflow-hidden relative pt-20 pb-4 lg:pt-6 lg:pb-6 ${sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-80'}`}
        >
          <div className='flex flex-col h-full card-float-solid mx-4 lg:mx-6'>
            <Header onSettingsClick={() => openSettings('profile')} />
            {showSettings ? (
              <div className='flex-1 overflow-y-auto p-6'>
                <UserSettings onClose={closeSettings} defaultTab={settingsTab} />
              </div>
            ) : (
              <div className='flex-1 flex flex-col overflow-hidden'>
                <ChatArea />
                {activeConversation && <InputArea />}
              </div>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        isOpen={deleteConfirm !== null}
        title='删除对话'
        message={deleteConfirm ? `确定要删除"${deleteConfirm.title}"吗？此操作无法撤销。` : ''}
        confirmText='删除'
        cancelText='取消'
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteConfirm(null)}
        type='danger'
      />
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
