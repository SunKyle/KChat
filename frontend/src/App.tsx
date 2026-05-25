import { ChatProvider } from './context/ChatContext';
import { Sidebar } from './components/Sidebar';
import { ChatArea } from './components/ChatArea';
import { InputArea } from './components/InputArea';
import { Header } from './components/Header';
import { useState } from 'react';
import { Menu, X, ChevronLeft, ChevronRight } from 'lucide-react';

function AppContent() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  return (
    <div className="flex h-screen bg-slate-900 overflow-hidden">
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
        <div className={`transition-all duration-300 ease-in-out ${sidebarCollapsed ? 'w-16' : 'w-72'}`}>
          <Sidebar collapsed={sidebarCollapsed} />
        </div>
      </div>

      {/* 移动端菜单按钮 */}
      <button
        onClick={() => setSidebarOpen(!sidebarOpen)}
        className="fixed top-4 left-4 z-30 p-2 rounded-lg bg-slate-800 lg:hidden shadow-lg hover:bg-slate-700 transition-colors"
      >
        {sidebarOpen ? (
          <X className="w-5 h-5 text-white" />
        ) : (
          <Menu className="w-5 h-5 text-white" />
        )}
      </button>

      {/* 桌面端侧边栏收起/展开按钮 */}
      <button
        onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
        className="fixed left-72 lg:left-auto top-1/2 -translate-y-1/2 z-40 hidden lg:flex items-center justify-center w-6 h-10 bg-slate-800/80 hover:bg-slate-700 rounded-r-lg transition-all duration-300 hover:w-7 group"
        style={{ transform: sidebarCollapsed ? 'translateY(-50%) translateX(0)' : 'translateY(-50%) translateX(-100%)' }}
        title={sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'}
      >
        {sidebarCollapsed ? (
          <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-white transition-colors" />
        ) : (
          <ChevronLeft className="w-4 h-4 text-slate-400 group-hover:text-white transition-colors" />
        )}
      </button>

      <div className="flex-1 flex flex-col overflow-hidden">
        <Header />
        <ChatArea />
        <InputArea />
      </div>
    </div>
  );
}

export default function App() {
  return (
    <ChatProvider>
      <AppContent />
    </ChatProvider>
  );
}
