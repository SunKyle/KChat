import { ChatProvider, useChat } from './context/ChatContext';
import { Sidebar } from './components/Sidebar';
import { ChatArea } from './components/ChatArea';
import { InputArea } from './components/InputArea';
import { Header } from './components/Header';
import { ConfirmDialog } from './components/common/ConfirmDialog';
import { useState } from 'react';
import { Menu, X } from 'lucide-react';

function AppContent() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; title: string } | null>(null);
  
  const { deleteConversation, activeConversation } = useChat();
  
  const sidebarWidth = sidebarCollapsed ? 'w-16' : 'w-72';

  const handleDeleteClick = (id: string, title: string) => {
    setDeleteConfirm({ id, title });
  };

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      deleteConversation(deleteConfirm.id);
      setDeleteConfirm(null);
    }
  };

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
        <div className={`
          fixed lg:relative z-50 lg:z-auto h-full transition-all duration-300 ease-in-out
          ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        `}>
          <div className={`h-full transition-all duration-300 ease-in-out ${sidebarWidth}`}>
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
        message={deleteConfirm ? `确定要删除"${deleteConfirm.title}"吗？此操作无法撤销。` : ''}
        confirmText="删除"
        cancelText="取消"
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteConfirm(null)}
        type="danger"
      />
    </>
  );
}

export default function App() {
  return (
    <ChatProvider>
      <AppContent />
    </ChatProvider>
  );
}
