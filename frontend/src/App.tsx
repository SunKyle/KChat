import { ChatProvider, useChat } from './context/ChatContext'
import { ModalProvider } from './context/ModalContext'
import { UserProvider } from './context/UserContext'
import { Sidebar } from './components/sidebar'
import type { MenuId } from './components/sidebar/SidebarRail'
import { ChatArea } from './components/chat/ChatArea'
import { InputArea } from './components/chat/InputArea'
import { Header } from './components/chat/Header'
import { Modal } from './components/common/Modal'
import { ToastContainer } from './components/common/ToastContainer'
import { ReminderNotification } from './components/common/ReminderNotification'
import { UserSettings } from './components/settings/UserSettings'
import { NoteTodoPanel } from './components/note-todo/NoteTodoPanel'
import { KnowledgeGraph } from './components/settings/knowledge-graph'
import { KnowledgeContentView } from './components/knowledge/KnowledgeContentView'
import { SkillDetailPage } from './components/skill/SkillDetailPage'
import { useState, useEffect, useCallback } from 'react'
import { Icon } from './components/common/Icon'
import { useSidebar } from './hooks/useSidebar'
import { useSettings } from './hooks/useSettings'
import { useConversation } from './hooks/useConversation'
import { cogneeMemory } from './api/cognee'
import type { KnowledgeBase } from './api/knowledge'

function AppContent() {
  const {
    sidebarOpen,
    sidebarCollapsed,
    sidebarWidth,
    setSidebarOpen,
    toggleSidebar,
    toggleCollapsed,
  } = useSidebar()
  const { showSettings, settingsTab, setSettingsTab, openSettings, closeSettings } = useSettings()
  const { activeConversation, conversations } = useChat()
  const { remove, select } = useConversation()
  const [deleteConfirm, setDeleteConfirm] = useState<{ id: string; title: string } | null>(null)
  const [noteTodoDrawerOpen, setNoteTodoDrawerOpen] = useState(false)
  // 一级菜单状态（Sidebar 上报），用于决定右侧胶囊是否展示
  const [activeMenu, setActiveMenu] = useState<MenuId>(() => {
    try {
      const saved = localStorage.getItem('sidebarActiveMenu')
      if (saved === 'chat' || saved === 'knowledge' || saved === 'graph' || saved === 'skills')
        return saved as MenuId
    } catch {
      // ignore
    }
    return 'chat'
  })
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null)
  const [skillRefreshKey, setSkillRefreshKey] = useState(0)
  const [graphViewDataset, setGraphViewDataset] = useState<{
    name: string
    displayName: string
  } | null>(null)
  const [knowledgeViewKb, setKnowledgeViewKb] = useState<KnowledgeBase | null>(null)
  const [knowledgeDocCount, setKnowledgeDocCount] = useState(0)
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

  const isChatMenu = activeMenu === 'chat'

  // 一级菜单离开会话时，关闭笔记/待办/提醒抽屉，避免占用右侧空间
  useEffect(() => {
    if (!isChatMenu) setNoteTodoDrawerOpen(false)
  }, [isChatMenu])

  // 会话菜单：无 activeConversation 时，默认选活跃会话列表第一条（置顶优先）
  useEffect(() => {
    if (activeMenu !== 'chat') return
    if (activeConversation) return
    if (!conversations || conversations.length === 0) return
    const firstPinned = conversations.find((c) => c.pinned)
    const first = firstPinned || conversations[0]
    select(first)
  }, [activeMenu, activeConversation, conversations, select])

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

  const handleKnowledgeStatsChange = useCallback((count: number) => {
    setKnowledgeDocCount(count)
  }, [])

  const handleSelectKnowledgeBase = useCallback(
    (kb: KnowledgeBase) => {
      setKnowledgeViewKb(kb)
      setKnowledgeDocCount(0)
      closeSettings()
    },
    [closeSettings]
  )

  const handleToggleGraphRankdir = useCallback(() => {
    setGraphRankdir((prev) => (prev === 'LR' ? 'TB' : 'LR'))
  }, [])

  // 头像点击：打开或关闭设置（打开时若侧边栏收起则展开）
  const handleAvatarClick = useCallback(() => {
    if (showSettings) {
      closeSettings()
    } else {
      openSettings('profile')
      if (sidebarCollapsed) {
        toggleCollapsed()
      }
    }
  }, [showSettings, closeSettings, openSettings, sidebarCollapsed, toggleCollapsed])

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
            className={`h-full card-panel-quiet overflow-hidden ${sidebarWidth} transition-[width] duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] will-change-[width]`}
          >
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggle={toggleCollapsed}
              onDeleteClick={handleDeleteClick}
              onConversationClick={() => {
                closeSettings()
              }}
              onSelectKnowledgeBase={handleSelectKnowledgeBase}
              onSelectDataset={(name, displayName) => {
                setGraphViewDataset({ name, displayName })
                setGraphSearchQuery('')
                setGraphRankdir('LR')
                closeSettings()
              }}
              showSettings={showSettings}
              settingsTab={settingsTab}
              onSettingsTabChange={setSettingsTab}
              onCloseSettings={closeSettings}
              onAvatarClick={handleAvatarClick}
              onActiveMenuChange={setActiveMenu}
              selectedKbId={knowledgeViewKb?.id ?? null}
              selectedDatasetName={graphViewDataset?.name ?? null}
              selectedSkillId={selectedSkillId}
              onSelectSkill={(id) => setSelectedSkillId(id)}
              onCreateSkill={() => {
                setSelectedSkillId(null)
              }}
            />
          </div>
        </aside>

        <button
          onClick={toggleSidebar}
          className='fixed top-[max(1rem,env(safe-area-inset-top))] left-4 z-30 p-2 rounded-lg theme-bg-card lg:hidden shadow-md hover:theme-bg-hover transition-colors'
        >
          {sidebarOpen ? (
            <Icon name='X' size='lg' className='theme-text-primary' />
          ) : (
            <Icon name='Menu' size='lg' className='theme-text-primary' />
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
            {activeMenu === 'chat' && !showSettings && <Header />}
            {/* 图谱视图的 Header */}
            {activeMenu === 'graph' && graphViewDataset && !showSettings && (
              <header className='relative z-10 h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-primary gap-3'>
                <div className='flex items-center gap-2.5 min-w-0 flex-shrink-0'>
                  <h1 className='font-conversation-name font-semibold theme-text-primary truncate min-w-0 max-w-[200px]'>
                    {graphViewDataset.displayName}
                  </h1>
                  <div className='flex items-center gap-1.5 px-2.5 py-0.5 rounded-full theme-bg-card border theme-border-primary text-xs flex-shrink-0'>
                    <Icon name='BarChart3' size='xs' className='theme-brand-primary' />
                    <span className='theme-text-primary font-medium whitespace-nowrap'>
                      {graphStats.nodes} 节点 · {graphStats.edges} 关系
                    </span>
                  </div>
                </div>

                <div className='flex items-center gap-2 flex-1 justify-end'>
                  {/* 搜索框 */}
                  <div className='flex items-center gap-1.5 bg-theme-bg-card rounded-lg border theme-border-primary px-2.5 py-1.5 shadow-sm w-48'>
                    <Icon name='Search' size='sm' className='theme-text-muted flex-shrink-0' />
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
                        <Icon name='X' size='xs' />
                      </button>
                    )}
                  </div>

                  {/* 方向切换 */}
                  <button
                    onClick={handleToggleGraphRankdir}
                    className='flex items-center gap-1.5 px-3 py-1.5 rounded-lg border theme-border-primary hover:border-[var(--brand-primary)]/40 hover:shadow-sm transition-all duration-200 cursor-pointer'
                    title={`布局方向：${graphRankdir === 'LR' ? '从左到右' : '从上到下'}`}
                  >
                    <Icon name='ArrowLeftRight' size='sm' className='theme-brand-primary' />
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
                    <Icon
                      name='Wrench'
                      size='sm'
                      className={`theme-brand-primary ${isImproving ? 'animate-spin' : ''}`}
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
                    <Icon name='RefreshCw' size='sm' className='theme-brand-primary' />
                    <span className='text-xs theme-text-primary hidden sm:inline'>刷新</span>
                  </button>
                </div>
              </header>
            )}

            {/* 知识库视图的 Header */}
            {activeMenu === 'knowledge' && knowledgeViewKb && !showSettings && (
              <header className='relative z-10 h-14 flex items-center justify-between px-4 sm:px-5 lg:px-6 border-b theme-border-primary gap-3'>
                <div className='flex items-center gap-2.5 min-w-0 flex-shrink-0'>
                  <h1 className='font-conversation-name font-semibold theme-text-primary truncate min-w-0 max-w-[240px]'>
                    {knowledgeViewKb.name}
                  </h1>
                  <div className='flex items-center gap-1.5 px-2.5 py-0.5 rounded-full theme-bg-card border theme-border-primary text-xs flex-shrink-0'>
                    <Icon name='FileText' size='xs' className='theme-brand-primary' />
                    <span className='theme-text-primary font-medium whitespace-nowrap'>
                      {knowledgeDocCount} 篇文档
                    </span>
                  </div>
                </div>
                {knowledgeViewKb.description && (
                  <p className='text-xs theme-text-muted truncate flex-1 min-w-0 hidden sm:block'>
                    {knowledgeViewKb.description}
                  </p>
                )}
              </header>
            )}

            {/* 数据集图谱视图 */}
            {activeMenu === 'graph' && !showSettings &&
              (graphViewDataset ? (
                <div className='relative flex-1 min-h-0'>
                  <KnowledgeGraph
                    key={graphRefreshKey}
                    dataset={graphViewDataset.name}
                    onStatsChange={handleGraphStatsChange}
                    externalSearchQuery={graphSearchQuery}
                    externalRankdir={graphRankdir}
                  />
                </div>
              ) : (
                <div className='relative flex-1 min-h-0 flex items-center justify-center'>
                  <div className='flex flex-col items-center text-center px-4'>
                    <Icon name='BarChart3' size={40} className='theme-text-muted mb-3' />
                    <p className='text-sm theme-text-secondary font-medium mb-1'>暂无图谱数据</p>
                    <p className='text-xs theme-text-muted'>在知识库中上传文档以生成图谱</p>
                  </div>
                </div>
              ))}
            {/* 知识库提取信息视图 */}
            {activeMenu === 'knowledge' && !showSettings &&
              (knowledgeViewKb ? (
                <div className='relative flex-1 min-h-0'>
                  <KnowledgeContentView
                    key={knowledgeViewKb.id}
                    kbId={knowledgeViewKb.id}
                    onStatsChange={handleKnowledgeStatsChange}
                  />
                </div>
              ) : (
                <div className='relative flex-1 min-h-0 flex items-center justify-center'>
                  <div className='flex flex-col items-center text-center px-4'>
                    <Icon name='Database' size={40} className='theme-text-muted mb-3' />
                    <p className='text-sm theme-text-secondary font-medium mb-1'>暂无知识库</p>
                    <p className='text-xs theme-text-muted'>创建知识库并上传文档</p>
                  </div>
                </div>
              ))}
            {/* 对话视图 */}
            {activeMenu === 'chat' && !showSettings && (
              <div className='relative flex-1 flex flex-col overflow-hidden'>
                <ChatArea />
                {activeConversation && <InputArea />}
              </div>
            )}
            {/* 技能库视图 */}
            {activeMenu === 'skills' && !showSettings && (
              <SkillDetailPage
                key={`${selectedSkillId}-${skillRefreshKey}`}
                skillId={selectedSkillId}
                onDeleted={() => {
                  setSelectedSkillId(null)
                  setSkillRefreshKey((k) => k + 1)
                }}
                onSaved={() => setSkillRefreshKey((k) => k + 1)}
              />
            )}
            {showSettings && (
              <div className='relative flex-1 overflow-y-auto p-6'>
                <UserSettings activeTab={settingsTab} />
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
        hideCapsule={!isChatMenu || showSettings}
      />

      <ToastContainer />
      <ReminderNotification />
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
