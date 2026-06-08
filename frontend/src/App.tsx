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
import { Menu, X } from 'lucide-react'
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
          fixed left-4 top-20 bottom-4 z-50 transition-all duration-300 ease-in-out
          lg:left-6 lg:top-6 lg:bottom-6
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
        >
          <div className={`h-full card-float-solid overflow-hidden ${sidebarWidth}`}>
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
