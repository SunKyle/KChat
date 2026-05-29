import { ChatProvider, useChat } from './context/ChatContext'
import { Sidebar } from './components/Sidebar'
import { ChatArea } from './components/ChatArea'
import { InputArea } from './components/InputArea'
import { Header } from './components/Header'
import { ConfirmDialog } from './components/common/ConfirmDialog'
import { ModelSettings } from './components/Settings/ModelSettings'
import { MemoryPanel } from './components/Memory/MemoryPanel'
import { useState, useEffect } from 'react'
import { Menu, X, X as XIcon } from 'lucide-react'

function AppContent() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [deleteConfirm, setDeleteConfirm] = useState<{
    id: string
    title: string
  } | null>(null)
  const [showModelSettings, setShowModelSettings] = useState(false)
  const [showMemoryPanel, setShowMemoryPanel] = useState(false)

  const { deleteConversation, activeConversation } = useChat()

  useEffect(() => {
    const handleOpenModelSettings = () => {
      setShowModelSettings(true)
    }
    const handleOpenMemory = () => {
      setShowMemoryPanel(true)
    }
    window.addEventListener('open-model-settings', handleOpenModelSettings)
    window.addEventListener('open-memory-panel', handleOpenMemory)
    return () => {
      window.removeEventListener('open-model-settings', handleOpenModelSettings)
      window.removeEventListener('open-memory-panel', handleOpenMemory)
    }
  }, [])

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
      <div className="flex h-screen bg-[#0F172A] overflow-hidden text-[#E5E7EB]">
        {/* 移动端侧边栏遮罩 */}
        {sidebarOpen && (
          <div
            className="fixed inset-0 bg-black/50 z-40 lg:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* 侧边栏 */}
        <div
          className={`
          fixed lg:relative z-50 lg:z-auto h-full transition-all duration-300 ease-in-out
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}
        >
          <div
            className={`h-full transition-all duration-300 ease-in-out ${sidebarWidth}`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
              onDeleteClick={handleDeleteClick}
            />
          </div>
        </div>

        {/* 移动端菜单按钮 */}
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="fixed top-4 left-4 z-30 p-2 rounded-lg bg-[#111827] lg:hidden shadow-lg hover:bg-slate-700 transition-colors"
        >
          {sidebarOpen ? (
            <X className="w-5 h-5 text-white" />
          ) : (
            <Menu className="w-5 h-5 text-white" />
          )}
        </button>

        <div className="flex-1 flex flex-col overflow-hidden relative">
          <Header />
          <ChatArea />
          {activeConversation && <InputArea />}
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

      {/* 模型设置页面 */}
      {showModelSettings && (
        <div className="fixed inset-0 bg-[#0F172A] z-50 overflow-y-auto">
          <div className="flex items-center justify-between p-4 border-b border-white/10">
            <h1 className="text-xl font-semibold text-[#E5E7EB]">模型设置</h1>
            <button
              onClick={() => setShowModelSettings(false)}
              className="p-2 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
            >
              <XIcon className="w-5 h-5" />
            </button>
          </div>
          <ModelSettings />
        </div>
      )}

      {/* 记忆管理弹窗 */}
      {showMemoryPanel && (
        <div
          className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          onClick={() => setShowMemoryPanel(false)}
        >
          <div
            className="w-full max-w-3xl max-h-[85vh] bg-[#1E293B] rounded-2xl shadow-2xl border border-white/10 overflow-hidden flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between p-6 border-b border-white/10">
              <h2 className="text-xl font-semibold text-white flex items-center gap-3">
                <span className="w-1 h-6 bg-blue-500 rounded-full" />
                记忆管理
              </h2>
              <button
                onClick={() => setShowMemoryPanel(false)}
                className="p-2 text-slate-400 hover:text-white hover:bg-white/5 rounded-lg transition-colors"
              >
                <XIcon className="w-5 h-5" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-6">
              <MemoryPanel onClose={() => setShowMemoryPanel(false)} />
            </div>
          </div>
        </div>
      )}
    </>
  )
}

export default function App() {
  return (
    <ChatProvider>
      <AppContent />
    </ChatProvider>
  )
}
