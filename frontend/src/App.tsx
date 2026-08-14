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
import { KnowledgeGraph } from './components/settings/Memory/KnowledgeGraph'
import { useState, useEffect, useCallback } from 'react'
import { Menu, X, BarChart3, RefreshCw, Wrench, Search, ArrowLeftRight } from 'lucide-react'
import { useSidebar } from './hooks/useSidebar'
import { useSettings } from './hooks/useSettings'
import { useConversation } from './hooks/useConversation'
import { cogneeMemory } from './api/cognee'

function AppContent() {
  const {
    sidebarOpen,
    sidebarCollapsed,
    sidebarWidth,
    setSidebarOpen,
    toggleSidebar,
    toggleCollapsed,
  } = useSidebar()
  const { showSettings, settingsTab, closeSettings } = useSettings()
  const { activeConversation } = useChat()
  const { remove } = useConversation()
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; title: string } | null>(null)
  const [noteTodoDrawerOpen, setNoteTodoDrawerOpen] = useState(false)
  const [graphViewDataset, setGraphViewDataset] = useState<{
    name: string
    displayName: string
  } | null>(null)
  const [graphStats, setGraphStats] = useState<{ nodes: number; edges: number }>({
    nodes: 0,
    edges: 0,
  })
  const [graphRefreshKey, setGraphRefreshKey] = useState(0)
  const [isImproving, setIsImproving] = useState(false)
  const [graphSearchQuery, setGraphSearchQuery] = useState('')
  const [graphRankdir, setGraphRankdir] = useState<'LR' | 'TB'>('LR')
  const [isLg, setIsLg] = useState(() => typeof window !== 'undefined' && window.innerWidth >= 1024)
  useEffect(() => {
    const mq = window.matchMedia('(min-width: 1024px)')
    const handler = (e: MediaQueryListEvent) => setIsLg(e.matches)
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [])

  const handleDeleteClick = (id: string, title: string) => {
    setDeleteConfirm({ id, title })
  }

  const handleConfirmDelete = () => {
    if (deleteConfirm) {
      remove(deleteConfirm.id)
      setDeleteConfirm(null)
    }
  }

  const handleRefreshGraph = useCallback(() => {
    setGraphRefreshKey((k) => k + 1)
  }, [])

  const handleImproveGraph = useCallback(async () => {
    if (!graphViewDataset || isImproving) return
    setIsImproving(true)
    try {
      const result = await cogneeMemory.improve(graphViewDataset.name)
      if (result.success) {
        handleRefreshGraph()
      }
    } finally {
      setIsImproving(false)
    }
  }, [graphViewDataset, isImproving, handleRefreshGraph])

  const handleGraphStatsChange = useCallback((stats: { nodes: number; edges: number }) => {
    setGraphStats(stats)
  }, [])

  const handleToggleGraphRankdir = useCallback(() => {
    setGraphRankdir((prev) => (prev === 'LR' ? 'TB' : 'LR'))
  }, [])

  return (
    <div
      style={
        isLg
          ? ({
              '--sidebar-pad': sidebarCollapsed ? '6rem' : '22rem',
              '--note-pad': noteTodoDrawerOpen ? '27rem' : '1rem',
            } as React.CSSProperties)
          : undefined
      }
    >
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
        >
          <div
            className={`h-full card-panel-quiet ${sidebarWidth} transition-[width] duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] will-change-[width]`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={toggleCollapsed}
              onDeleteClick={handleDeleteClick}
              onConversationClick={() => {
                closeSettings()
                setGraphViewDataset(null)
              }}
              onSelectDataset={(name, displayName) => {
                setGraphViewDataset({ name, displayName })
                setGraphSearchQuery('')
                setGraphRankdir('LR')
                closeSettings()
              }}
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
          className='flex-1 flex flex-col overflow-hidden relative pt-20 pb-[max(1rem,env(safe-area-inset-bottom))] lg:pt-4 lg:pb-4 lg:pl-[var(--sidebar-pad,20rem)] lg:pr-[var(--note-pad,4.5rem)] transition-[padding] duration-[280ms] ease-[cubic-bezier(0.32,0.72,0,1)] delay-[60ms]'
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
            {/* 对话视图的 Header */}
            {!graphViewDataset && <Header />}
            {/* 图谱视图的 Header */}
            {graphViewDataset && !showSettings && (
              <header className='relative z-10 h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-primary gap-3'>
                <div className='flex items-center gap-3 min-w-0 flex-shrink-0'>
                  <h1 className='font-conversation-name font-semibold theme-text-primary truncate min-w-0 max-w-[200px]'>
                    {graphViewDataset.displayName}
                  </h1>
                  <span className='text-xs theme-text-muted flex-shrink-0'>知识图谱</span>
                </div>

                <div className='flex items-center gap-2 flex-1 justify-end'>
                  {/* 搜索框 */}
                  <div className='flex items-center gap-1.5 bg-theme-bg-card rounded-lg border theme-border-primary px-2.5 py-1.5 shadow-sm w-48'>
                    <Search className='w-3.5 h-3.5 theme-text-muted flex-shrink-0' />
                    <input
                      type='text'
                      value={graphSearchQuery}
                      onChange={(e) => setGraphSearchQuery(e.target.value)}
                      placeholder='搜索节点...'
                      className='flex-1 bg-transparent text-xs theme-text-primary placeholder:theme-text-muted focus:outline-none min-w-0'
                    />
                    {graphSearchQuery && (
                      <button
                        onClick={() => setGraphSearchQuery('')}
                        className='theme-text-muted hover:theme-text-primary flex-shrink-0'
                      >
                        <X className='w-3 h-3' />
                      </button>
                    )}
                  </div>

                  {/* 统计 */}
                  <div className='flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg theme-bg-card border theme-border-primary shadow-sm'>
                    <BarChart3 className='w-3.5 h-3.5 theme-brand-primary' />
                    <span className='text-xs theme-text-primary font-medium'>
                      {graphStats.nodes} 节点
                    </span>
                    <span className='text-xs theme-text-muted'>·</span>
                    <span className='text-xs theme-text-primary font-medium'>
                      {graphStats.edges} 关系
                    </span>
                  </div>

                  {/* 方向切换 */}
                  <button
                    onClick={handleToggleGraphRankdir}
                    className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm transition-all duration-200 cursor-pointer'
                    title={`布局方向：${graphRankdir === 'LR' ? '从左到右' : '从上到下'}`}
                  >
                    <ArrowLeftRight className='w-3.5 h-3.5 theme-brand-primary' />
                    <span className='text-xs theme-text-primary hidden sm:inline'>
                      {graphRankdir === 'LR' ? '左→右' : '上→下'}
                    </span>
                  </button>

                  {/* 优化图谱 */}
                  <button
                    onClick={handleImproveGraph}
                    disabled={isImproving}
                    className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm transition-all duration-200 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed'
                    title='优化图谱：推导跨实体连接、重加权边'
                  >
                    <Wrench
                      className={`w-3.5 h-3.5 theme-brand-primary ${isImproving ? 'animate-spin' : ''}`}
                    />
                    <span className='text-xs theme-text-primary hidden sm:inline'>
                      {isImproving ? '优化中...' : '优化图谱'}
                    </span>
                  </button>

                  {/* 刷新 */}
                  <button
                    onClick={handleRefreshGraph}
                    className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm transition-all duration-200 cursor-pointer'
                    title='刷新图谱'
                  >
                    <RefreshCw className='w-3.5 h-3.5 theme-brand-primary' />
                    <span className='text-xs theme-text-primary hidden sm:inline'>刷新</span>
                  </button>
                </div>
              </header>
            )}

            {/* 数据集图谱视图 */}
            {graphViewDataset && !showSettings && (
              <div className='relative flex-1 min-h-0'>
                <KnowledgeGraph
                  key={graphRefreshKey}
                  dataset={graphViewDataset.name}
                  onStatsChange={handleGraphStatsChange}
                  externalSearchQuery={graphSearchQuery}
                  externalRankdir={graphRankdir}
                />
              </div>
            )}
            {/* 对话视图 */}
            {!graphViewDataset && (
              <div
                className={`relative flex-1 flex flex-col overflow-hidden ${showSettings ? 'hidden' : ''}`}
              >
                <ChatArea />
                {activeConversation && <InputArea />}
              </div>
            )}
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
    </div>
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
