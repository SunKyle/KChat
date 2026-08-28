import { useState, useEffect, useCallback } from 'react'
import { Icon } from '../common/Icon'
import { motion } from 'framer-motion'
import { cogneeMemory, type DatasetInfo } from '../../api/cognee'
import { knowledgeBaseApi, type KnowledgeBase } from '../../api/knowledge'

interface GraphPanelProps {
  onToggle: () => void
  onSelectDataset: (datasetName: string, displayName: string) => void
  /** 当前选中的数据集名（用于二级列表高亮） */
  selectedDatasetName?: string | null
}

interface GraphDatasetEntry {
  datasetName: string
  displayName: string
  count: number
  isKnowledgeBase: boolean
  description?: string
}

export function GraphPanel({
  onToggle,
  onSelectDataset,
  selectedDatasetName,
}: GraphPanelProps) {
  const [datasets, setDatasets] = useState<GraphDatasetEntry[]>([])
  const [loading, setLoading] = useState(true)

  const loadDatasets = useCallback(async () => {
    try {
      setLoading(true)
      const [dsList, kbList] = await Promise.all([
        cogneeMemory.listDatasets().catch(() => [] as DatasetInfo[]),
        knowledgeBaseApi.list('default').catch(() => [] as KnowledgeBase[]),
      ])

      const kbByDataset = new Map<string, KnowledgeBase>()
      for (const kb of kbList) {
        kbByDataset.set(kb.datasetName, kb)
      }

      const entries: GraphDatasetEntry[] = []
      for (const ds of dsList) {
        const kb = kbByDataset.get(ds.name)
        if (kb) {
          entries.push({
            datasetName: ds.name,
            displayName: kb.name,
            count: kb.documentCount,
            isKnowledgeBase: true,
            description: kb.description || undefined,
          })
        } else {
          const isMain = ds.name === 'main_dataset'
          entries.push({
            datasetName: ds.name,
            displayName: isMain ? '对话记忆' : ds.name,
            count: ds.data_count,
            isKnowledgeBase: false,
            description: isMain ? '系统自动生成的对话历史图谱' : undefined,
          })
        }
      }

      entries.sort((a, b) => {
        if (a.datasetName === 'main_dataset') return 1
        if (b.datasetName === 'main_dataset') return -1
        return a.displayName.localeCompare(b.displayName, 'zh')
      })

      setDatasets(entries)
    } catch (e) {
      console.error('Failed to load datasets:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDatasets()
  }, [loadDatasets])

  // 无选中项时，默认选中列表中第一个 dataset
  useEffect(() => {
    if (!selectedDatasetName && datasets.length > 0) {
      onSelectDataset(datasets[0].datasetName, datasets[0].displayName)
    }
  }, [selectedDatasetName, datasets, onSelectDataset])

  return (
    <div className='flex flex-col h-full'>
      {/* 标题栏 */}
      <div className='px-4 h-14 flex items-center justify-between flex-shrink-0'>
        <h2 className='font-group-title theme-text-primary'>知识图谱</h2>
        <div className='flex items-center gap-1'>
          <button
            onClick={loadDatasets}
            aria-label='刷新'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200'
          >
            <Icon name='RefreshCw' size='md' />
          </button>
          <button
            onClick={onToggle}
            aria-label='收起侧边栏'
            className='p-1.5 rounded-lg hover:theme-bg-hover theme-text-muted hover:theme-text-secondary transition-all duration-200 focus-ring flex-shrink-0'
          >
            <Icon name='ChevronRight' size='md' className='rotate-180' aria-hidden='true' />
          </button>
        </div>
      </div>

      {/* 列表 */}
      <div className='flex-1 min-h-0 overflow-y-auto px-3 pb-3'>
        {loading ? (
          <div className='flex items-center justify-center h-full'>
            <Icon name='Loader2' size='lg' className='animate-spin theme-text-muted' />
          </div>
        ) : datasets.length === 0 ? (
          <div className='flex flex-col items-center justify-center h-full text-center px-4'>
            <Icon name='Share2' size='2xl' className='theme-text-muted mb-3' />
            <p className='text-sm theme-text-secondary mb-1 font-semibold'>暂无图谱数据</p>
            <p className='text-xs theme-text-muted'>在知识库中上传文档以生成图谱</p>
          </div>
        ) : (
          <div className='space-y-1.5'>
            {datasets.map((entry) => {
              const isMain = entry.datasetName === 'main_dataset'
              const isActive = selectedDatasetName === entry.datasetName
              return (
                <motion.div
                  key={entry.datasetName}
                  initial={{ opacity: 0, y: 4 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={`group flex items-center gap-2.5 p-2.5 rounded-xl cursor-pointer transition-all duration-200 ${
                    isActive ? 'bg-brand-selected theme-brand-primary' : 'hover:theme-bg-hover'
                  }`}
                  onClick={() => onSelectDataset(entry.datasetName, entry.displayName)}
                >
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
                      isMain ? 'bg-gradient-to-br from-purple-500/10 to-blue-500/10' : 'theme-bg-hover'
                    }`}
                  >
                    {isMain ? (
                      <Icon name='Brain' size='md' className='text-purple-400' />
                    ) : (
                      <Icon name='Database' size='md' className='theme-text-muted' />
                    )}
                  </div>
                  <div className='flex-1 min-w-0'>
                    <p
                      className={`text-sm truncate font-medium ${
                        isActive ? 'theme-brand-primary' : 'theme-text-primary'
                      }`}
                    >
                      {entry.displayName}
                    </p>
                    <p className='text-xs theme-text-muted'>
                      {entry.count} 条数据
                      {entry.description && (
                        <span className='ml-1.5'>· {entry.description}</span>
                      )}
                    </p>
                  </div>
                  <Icon
                    name='Share2'
                    size='sm'
                    className='theme-text-muted opacity-0 group-hover:opacity-60 flex-shrink-0'
                  />
                </motion.div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

export default GraphPanel