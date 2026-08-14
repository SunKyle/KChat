import { ChevronRight } from 'lucide-react'
import { KnowledgeGraph } from '../settings/Memory/KnowledgeGraph'

interface GraphPanelProps {
  onToggle: () => void
}

export function GraphPanel({ onToggle }: GraphPanelProps) {
  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>知识图谱</h2>
        <button
          onClick={onToggle}
          aria-label='收起侧边栏'
          className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
        >
          <ChevronRight className='w-4 h-4 rotate-180' aria-hidden='true' />
        </button>
      </div>

      {/* 图谱画布 */}
      <div className='flex-1 min-h-0 px-2 pb-2'>
        <KnowledgeGraph />
      </div>
    </div>
  )
}

export default GraphPanel
