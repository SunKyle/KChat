import { ChatProvider, useChat } from './context/ChatContext'
import { ModalProvider, useModal } from './context/ModalContext'
import { UserProvider } from './context/UserContext'
import { Sidebar } from './components/layout/Sidebar'
import { ChatArea } from './components/ChatArea'
import { InputArea } from './components/InputArea'
import { Header } from './components/layout/Header'
import { ConfirmDialog } from './components/common/ConfirmDialog'
import { ModelSettings } from './components/Settings/ModelSettings'
import { MemoryPanel } from './components/Memory/MemoryPanel'
import { UserSettings } from './components/Settings/UserSettings'
import { Modal } from './components/ui/Modal'
import { useState } from 'react'
import { Menu, X } from 'lucide-react'

function AppContent() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [deleteConfirm, setDeleteConfirm] = useState<{
    id: string
    title: string
  } | null>(null)
  const [showSettings, setShowSettings] = useState(false)

  const { deleteConversation, activeConversation } = useChat()
  const {
    showModelSettings,
    closeModelSettings,
    showMemoryPanel,
    closeMemoryPanel,
  } = useModal()

  const sidebarWidth = sidebarCollapsed ? 'w-16' : 'w-72'

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
      <div className="flex h-screen theme-bg-primary overflow-hidden theme-text-primary">
        {sidebarOpen && (
          <div
            className="fixed inset-0 theme-bg-overlay z-40 lg:hidden"
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
          <div
            className={`h-full backdrop-blur-xl bg-[var(--bg-sidebar)]/80 rounded-2xl border-0 overflow-hidden transition-all duration-200 ease-out ${sidebarWidth} shadow-[0_4px_12px_rgba(0,0,0,0.2),0_8px_24px_rgba(0,0,0,0.15),0_16px_40px_rgba(0,0,0,0.1)] hover:shadow-[0_6px_16px_rgba(0,0,0,0.25),0_12px_32px_rgba(0,0,0,0.18),0_20px_48px_rgba(0,0,0,0.12)]`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
              onDeleteClick={handleDeleteClick}
            />
          </div>
        </aside>

        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="fixed top-4 left-4 z-30 p-2 rounded-lg theme-bg-card lg:hidden shadow-lg hover:theme-bg-hover transition-colors"
        >
          {sidebarOpen ? (
            <X className="w-5 h-5 theme-text-primary" />
          ) : (
            <Menu className="w-5 h-5 theme-text-primary" />
          )}
        </button>

        <div
          className={`flex-1 flex flex-col overflow-hidden relative ${sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-80'}`}
        >
          <Header onSettingsClick={() => setShowSettings(true)} />
          {showSettings ? (
            <div className="flex-1 overflow-y-auto p-6">
              <UserSettings />
            </div>
          ) : (
            <>
              <ChatArea />
              {activeConversation && <InputArea />}
            </>
          )}
        </div>
      </div>

      <ConfirmDialog
        isOpen={deleteConfirm !== null}
        title="删除对话"
        message={
          deleteConfirm
            ? `确定要删除"${deleteConfirm.title}"吗？此操作无法撤销。`
            : ''
        }
        confirmText="删除"
        cancelText="取消"
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteConfirm(null)}
        type="danger"
      />

      <Modal
        isOpen={showModelSettings}
        onClose={closeModelSettings}
        title="添加自定义模型"
        size="xl"
        autoHeight
      >
        <ModelSettings />
      </Modal>

      <Modal
        isOpen={showMemoryPanel}
        onClose={closeMemoryPanel}
        title="记忆管理"
        size="lg"
        autoHeight
      >
        <MemoryPanel />
      </Modal>
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
