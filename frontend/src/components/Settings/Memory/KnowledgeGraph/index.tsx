import { useCallback, useMemo, useRef, useState, useEffect } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  addEdge,
  useNodesState,
  useEdgesState,
  useReactFlow,
  ReactFlowProvider,
  Panel,
  Handle,
  Position,
  MarkerType,
  type Node,
  type Edge,
  type Connection,
  type NodeProps,
  type NodeTypes,
} from '@xyflow/react'
import dagre from 'dagre'
import { Database, Search, RefreshCw, X } from 'lucide-react'
import { cogneeMemory, type GraphNode, type GraphEdge } from '../../../../api/cognee'

import '@xyflow/react/dist/style.css'

interface NodeDataShape {
  label: string
  type: string
  properties: Record<string, unknown>
}

const nodeTypeColors: Record<string, string> = {
  Entity: 'bg-blue-500',
  EntityType: 'bg-cyan-500',
  TextDocument: 'bg-indigo-500',
  DocumentChunk: 'bg-violet-500',
  TextSummary: 'bg-teal-500',
  entity: 'bg-blue-500',
  person: 'bg-green-500',
  organization: 'bg-purple-500',
  location: 'bg-amber-500',
  concept: 'bg-cyan-500',
  event: 'bg-rose-500',
  document: 'bg-indigo-500',
  dataset: 'bg-violet-500',
  memory: 'bg-teal-500',
  default: 'bg-gray-500',
}

function getNodeColor(type: string): string {
  return nodeTypeColors[type] || nodeTypeColors.default
}

const nodeTypeLabels: Record<string, string> = {
  Entity: '实体',
  EntityType: '实体类型',
  TextDocument: '文本文档',
  DocumentChunk: '文档分块',
  TextSummary: '文本摘要',
  entity: '实体',
  person: '人物',
  organization: '组织',
  location: '地点',
  concept: '概念',
  event: '事件',
  document: '文档',
  dataset: '数据集',
  memory: '记忆',
  relationship: '关系',
}

function getNodeTypeLabel(type: string): string {
  return nodeTypeLabels[type] || type
}

const dagreGraph = new dagre.graphlib.Graph()
dagreGraph.setDefaultEdgeLabel(() => ({}))

const nodeWidth = 180
const nodeHeight = 60

function getLayoutedNodes(nodes: Node[], edges: Edge[]): Node[] {
  dagreGraph.setGraph({ rankdir: 'LR', nodesep: 80, ranksep: 120 })

  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, { width: nodeWidth, height: nodeHeight })
  })

  edges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target)
  })

  dagre.layout(dagreGraph)

  return nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id)
    return {
      ...node,
      targetPosition: 'left' as const,
      sourcePosition: 'right' as const,
      position: {
        x: nodeWithPosition.x - nodeWidth / 2,
        y: nodeWithPosition.y - nodeHeight / 2,
      },
    } as Node
  })
}

function GraphNodeComponent({ data, selected }: NodeProps) {
  const d = data as unknown as NodeDataShape
  const color = getNodeColor(d.type)
  const typeLabel = getNodeTypeLabel(d.type)

  return (
    <div
      className={`px-3 py-2 rounded-xl border-2 shadow-lg min-w-[140px] max-w-[200px] transition-all duration-200 ${
        selected
          ? 'border-[var(--accent-primary)] shadow-xl scale-105'
          : 'border-[var(--border-secondary)]'
      } bg-[var(--bg-card)]`}
    >
      <Handle
        type='target'
        position={Position.Left}
        className='!w-2 !h-2 !min-w-2 !min-h-2 !border-0 !bg-[var(--border-secondary)]'
      />
      <div className={`w-2 h-2 rounded-full ${color} mb-1.5`} />
      <div className='text-xs font-semibold text-[var(--text-primary)] leading-tight line-clamp-2'>
        {d.label}
      </div>
      <div className='text-[10px] text-[var(--text-muted)] mt-0.5'>{typeLabel}</div>
      <Handle
        type='source'
        position={Position.Right}
        className='!w-2 !h-2 !min-w-2 !min-h-2 !border-0 !bg-[var(--border-secondary)]'
      />
    </div>
  )
}

const nodeTypes: NodeTypes = {
  cogneeNode: GraphNodeComponent,
}

function GraphInner() {
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const { fitView } = useReactFlow()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [filteredNodeIds, setFilteredNodeIds] = useState<Set<string> | null>(null)
  const [highlightedNodeIds, setHighlightedNodeIds] = useState<Set<string> | null>(null)
  const [hiddenTypes, setHiddenTypes] = useState<Set<string>>(new Set())

  const fetchGraph = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await cogneeMemory.getGraph()
      if (data.status && data.status.startsWith('error')) {
        setError(data.status)
        setNodes([])
        setEdges([])
        return
      }

      const rfNodes: Node[] = data.nodes.map((n: GraphNode) => ({
        id: n.id,
        type: 'cogneeNode',
        position: n.position || { x: 0, y: 0 },
        data: {
          label: n.label,
          type: n.type,
          properties: n.properties,
        } as unknown as Node['data'],
      }))

      const rfEdges: Edge[] = data.edges.map((e: GraphEdge) => ({
        id: e.id || `edge-${e.source}-${e.target}`,
        source: e.source,
        target: e.target,
        label: e.label,
        type: 'smoothstep',
        animated: false,
        style: { strokeWidth: 2, stroke: 'var(--accent-primary, #1e9df1)', opacity: 0.6 },
        labelStyle: { fill: 'var(--text-secondary)', fontSize: 10, fontWeight: 600 },
        labelBgStyle: { fill: 'var(--bg-card)' },
        labelBgBorderRadius: 4,
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 12,
          height: 12,
          color: 'var(--accent-primary, #1e9df1)',
        },
      }))

      const layouted = getLayoutedNodes(rfNodes, rfEdges)
      setNodes(layouted)
      setEdges(rfEdges)

      setTimeout(() => {
        fitView({ padding: 0.2, duration: 500 })
      }, 100)
    } catch (err) {
      setError(String(err))
      setNodes([])
      setEdges([])
    } finally {
      setLoading(false)
    }
  }, [fitView, setNodes, setEdges])

  useEffect(() => {
    fetchGraph()
  }, [fetchGraph])

  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ ...params, type: 'smoothstep' }, eds)),
    [setEdges]
  )

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      setSelectedNode(node)
      // 计算关联节点：当前节点 + 所有直接相连的节点
      const connected = new Set<string>([node.id])
      edges.forEach((e) => {
        if (e.source === node.id) connected.add(e.target)
        if (e.target === node.id) connected.add(e.source)
      })
      setHighlightedNodeIds(connected)
    },
    [edges]
  )

  const handleSearch = useCallback(
    (value: string) => {
      setSearchQuery(value)
      if (!value.trim()) {
        setFilteredNodeIds(null)
        return
      }
      const lower = value.toLowerCase()
      const matched = new Set<string>()
      nodes.forEach((n) => {
        const d = n.data as unknown as NodeDataShape
        const label = d.label.toLowerCase()
        const type = d.type.toLowerCase()
        if (label.includes(lower) || type.includes(lower)) {
          matched.add(n.id)
        }
      })
      setFilteredNodeIds(matched)
    },
    [nodes]
  )

  const filteredNodes = useMemo(() => {
    let result = nodes
    // 类型过滤
    if (hiddenTypes.size > 0) {
      result = result.map((n) => {
        const d = n.data as unknown as NodeDataShape
        return {
          ...n,
          hidden: n.hidden || hiddenTypes.has(d.type),
        }
      })
    }
    // 搜索过滤
    if (filteredNodeIds) {
      result = result.map((n) => ({
        ...n,
        hidden: n.hidden || !filteredNodeIds.has(n.id),
      }))
    }
    // 高亮逻辑
    if (highlightedNodeIds) {
      result = result.map((n) => {
        if (n.hidden) return n
        const isHighlighted = highlightedNodeIds.has(n.id)
        return {
          ...n,
          style: {
            ...n.style,
            opacity: isHighlighted ? 1 : 0.2,
          },
        }
      })
    }
    return result
  }, [nodes, filteredNodeIds, highlightedNodeIds, hiddenTypes])

  const filteredEdges = useMemo(() => {
    let result = edges
    // 类型过滤：隐藏连接到被过滤节点的边
    if (hiddenTypes.size > 0) {
      const hiddenNodeIds = new Set<string>()
      nodes.forEach((n) => {
        const d = n.data as unknown as NodeDataShape
        if (hiddenTypes.has(d.type)) hiddenNodeIds.add(n.id)
      })
      result = result.map((e) => ({
        ...e,
        hidden: e.hidden || hiddenNodeIds.has(e.source) || hiddenNodeIds.has(e.target),
      }))
    }
    // 搜索过滤
    if (filteredNodeIds) {
      result = result.map((e) => ({
        ...e,
        hidden: e.hidden || !filteredNodeIds.has(e.source) || !filteredNodeIds.has(e.target),
      }))
    }
    // 高亮逻辑
    if (highlightedNodeIds) {
      result = result.map((e) => {
        if (e.hidden) return e
        const isHighlighted = highlightedNodeIds.has(e.source) && highlightedNodeIds.has(e.target)
        if (isHighlighted) {
          return {
            ...e,
            animated: true,
            style: { strokeWidth: 3, stroke: '#f59e0b', opacity: 1 },
            markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14, color: '#f59e0b' },
            labelStyle: { fill: '#f59e0b', fontSize: 10, fontWeight: 700 },
            labelBgStyle: { fill: 'var(--bg-card)' },
            labelBgBorderRadius: 4,
          }
        }
        return {
          ...e,
          style: { ...e.style, opacity: 0.1 },
        }
      })
    }
    return result
  }, [edges, nodes, filteredNodeIds, highlightedNodeIds, hiddenTypes])

  const typeCounts = useMemo(() => {
    const counts: Record<string, number> = {}
    nodes.forEach((n) => {
      const d = n.data as unknown as NodeDataShape
      const t = d.type
      counts[t] = (counts[t] || 0) + 1
    })
    return counts
  }, [nodes])

  const selectedData = selectedNode?.data as unknown as NodeDataShape | undefined

  return (
    <div className='relative w-full h-full rounded-xl border border-[var(--border-secondary)] overflow-hidden bg-[var(--bg-card)]'>
      {/* Toolbar */}
      <Panel position='top-left' className='!m-2'>
        <div className='flex flex-col gap-2'>
          <div className='flex items-center gap-2 bg-[var(--bg-card)]/95 backdrop-blur rounded-xl border border-[var(--border-secondary)] px-3 py-2 shadow-lg'>
            <Database className='w-4 h-4 text-[var(--accent-primary)]' />
            <span className='text-sm font-semibold text-[var(--text-primary)]'>知识图谱</span>
            <span className='text-xs text-[var(--text-muted)]'>
              {nodes.length} 节点 · {edges.length} 关系
            </span>
          </div>

          <div className='flex items-center gap-2 bg-[var(--bg-card)]/95 backdrop-blur rounded-xl border border-[var(--border-secondary)] px-3 py-2 shadow-lg'>
            <Search className='w-3.5 h-3.5 text-[var(--text-muted)]' />
            <input
              type='text'
              value={searchQuery}
              onChange={(e) => handleSearch(e.target.value)}
              placeholder='搜索节点...'
              className='w-32 bg-transparent text-xs text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none'
            />
            {searchQuery && (
              <button
                onClick={() => handleSearch('')}
                className='text-[var(--text-muted)] hover:text-[var(--text-primary)]'
              >
                <X className='w-3 h-3' />
              </button>
            )}
          </div>

          <button
            onClick={fetchGraph}
            disabled={loading}
            className='flex items-center justify-center gap-1.5 bg-[var(--bg-card)]/95 backdrop-blur rounded-xl border border-[var(--border-secondary)] px-3 py-2 shadow-lg hover:bg-[var(--bg-hover)] text-xs font-medium text-[var(--text-primary)] transition-colors disabled:opacity-50'
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            {loading ? '加载中...' : '刷新图谱'}
          </button>
        </div>
      </Panel>

      {/* Legend + Type Filter */}
      <Panel position='bottom-left' className='!m-2'>
        <div className='bg-[var(--bg-card)]/95 backdrop-blur rounded-xl border border-[var(--border-secondary)] px-3 py-2 shadow-lg'>
          <div className='flex items-center justify-between mb-1.5'>
            <span className='text-xs font-semibold text-[var(--text-secondary)]'>类型过滤</span>
            {hiddenTypes.size > 0 && (
              <button
                onClick={() => setHiddenTypes(new Set())}
                className='text-[10px] text-[var(--accent-primary)] hover:underline'
              >
                全部显示
              </button>
            )}
          </div>
          <div className='flex flex-col gap-1'>
            {Object.entries(typeCounts)
              .sort((a, b) => b[1] - a[1])
              .map(([type, count]) => {
                const isHidden = hiddenTypes.has(type)
                return (
                  <button
                    key={type}
                    onClick={() => {
                      setHiddenTypes((prev) => {
                        const next = new Set(prev)
                        if (next.has(type)) next.delete(type)
                        else next.add(type)
                        return next
                      })
                    }}
                    className={`flex items-center gap-1.5 text-[10px] transition-opacity ${
                      isHidden ? 'opacity-30 line-through' : 'hover:opacity-80'
                    } text-[var(--text-muted)] cursor-pointer`}
                  >
                    <span className={`w-2 h-2 rounded-full ${getNodeColor(type)}`} />
                    <span>{getNodeTypeLabel(type)}</span>
                    <span>({count})</span>
                  </button>
                )
              })}
          </div>
        </div>
      </Panel>

      {/* Error overlay */}
      {error && (
        <div className='absolute inset-0 flex items-center justify-center bg-[var(--bg-card)]/80 backdrop-blur-sm z-10'>
          <div className='text-center p-6 bg-[var(--bg-card)] rounded-2xl border border-red-500/30 shadow-xl max-w-md'>
            <div className='w-12 h-12 rounded-full bg-red-500/10 flex items-center justify-center mx-auto mb-3'>
              <Database className='w-6 h-6 text-red-500' />
            </div>
            <h3 className='text-base font-semibold text-[var(--text-primary)] mb-1'>
              图谱加载失败
            </h3>
            <p className='text-sm text-[var(--text-muted)] mb-3'>{error}</p>
            <p className='text-xs text-[var(--text-muted)]'>
              请确保 Cognee 服务正在运行，并已添加数据。
            </p>
          </div>
        </div>
      )}

      {/* Empty state */}
      {!loading && !error && nodes.length === 0 && (
        <div className='absolute inset-0 flex items-center justify-center z-10'>
          <div className='text-center p-8 bg-[var(--bg-card)] rounded-2xl border border-[var(--border-secondary)] shadow-xl'>
            <div className='w-14 h-14 rounded-full theme-bg-input flex items-center justify-center mx-auto mb-4'>
              <Database className='w-7 h-7 theme-text-muted' />
            </div>
            <h3 className='text-base font-semibold theme-text-primary mb-1'>暂无图谱数据</h3>
            <p className='theme-text-muted text-sm mb-4'>开始对话后，系统会自动构建知识图谱</p>
          </div>
        </div>
      )}

      {/* React Flow Canvas */}
      <ReactFlow
        ref={reactFlowWrapper}
        nodes={filteredNodes}
        edges={filteredEdges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onPaneClick={() => {
          setSelectedNode(null)
          setHighlightedNodeIds(null)
        }}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        proOptions={{ hideAttribution: true }}
        minZoom={0.1}
        maxZoom={2}
      >
        <Background color='var(--border-primary)' gap={20} size={1} />
        <Controls className='!bg-[var(--bg-card)] !border-[var(--border-secondary)] [&>button]:!bg-[var(--bg-card)] [&>button]:!border-[var(--border-secondary)] [&>button]:!text-[var(--text-secondary)] hover:[&>button]:!bg-[var(--bg-hover)]' />
      </ReactFlow>

      {/* Node detail panel */}
      {selectedNode && selectedData && (
        <div className='absolute top-2 right-2 w-72 max-h-[calc(100%-1rem)] bg-[var(--bg-card)] rounded-xl border border-[var(--border-secondary)] shadow-xl z-20 overflow-hidden flex flex-col'>
          <div className='flex items-center justify-between px-4 py-3 border-b border-[var(--border-secondary)] bg-gradient-to-r from-[var(--bg-input)] to-[var(--bg-card)]'>
            <div className='flex items-center gap-2'>
              <span className={`w-2.5 h-2.5 rounded-full ${getNodeColor(selectedData.type)}`} />
              <h3 className='text-sm font-semibold text-[var(--text-primary)] truncate max-w-[180px]'>
                {selectedData.label || '节点详情'}
              </h3>
            </div>
            <button
              onClick={() => {
                setSelectedNode(null)
                setHighlightedNodeIds(null)
              }}
              className='p-1 rounded-md hover:bg-[var(--bg-hover)] text-[var(--text-muted)]'
            >
              <X className='w-4 h-4' />
            </button>
          </div>
          <div className='flex-1 overflow-y-auto p-4 space-y-3'>
            <div>
              <span className='text-xs font-semibold text-[var(--text-muted)]'>类型</span>
              <div className='text-sm text-[var(--text-primary)] mt-0.5'>
                {getNodeTypeLabel(selectedData.type)}
              </div>
            </div>
            <div>
              <span className='text-xs font-semibold text-[var(--text-muted)]'>ID</span>
              <div className='text-xs text-[var(--text-secondary)] mt-0.5 font-mono break-all'>
                {selectedNode.id}
              </div>
            </div>
            {selectedData.properties && Object.keys(selectedData.properties).length > 0 && (
              <div>
                <span className='text-xs font-semibold text-[var(--text-muted)]'>属性</span>
                <div className='mt-1.5 space-y-1'>
                  {Object.entries(selectedData.properties).map(([key, value]) => (
                    <div key={key} className='flex items-start gap-2 text-xs'>
                      <span className='text-[var(--text-muted)] font-medium flex-shrink-0 min-w-[60px]'>
                        {key}:
                      </span>
                      <span className='text-[var(--text-secondary)] break-all'>
                        {String(value)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
            <div>
              <span className='text-xs font-semibold text-[var(--text-muted)]'>连接关系</span>
              <div className='mt-1.5 space-y-1'>
                {edges
                  .filter((e) => e.source === selectedNode.id || e.target === selectedNode.id)
                  .map((e) => {
                    const isSource = e.source === selectedNode.id
                    const otherId = isSource ? e.target : e.source
                    const otherNode = nodes.find((n) => n.id === otherId)
                    const otherData = otherNode?.data as unknown as NodeDataShape | undefined
                    return (
                      <div
                        key={e.id}
                        className='flex items-center gap-1 text-xs text-[var(--text-secondary)]'
                      >
                        <span className='text-[var(--text-muted)]'>{isSource ? '→' : '←'}</span>
                        <span className='truncate max-w-[120px]'>
                          {otherData?.label || otherId}
                        </span>
                        {e.label && (
                          <span className='text-[var(--text-muted)] text-[10px]'>({e.label})</span>
                        )}
                      </div>
                    )
                  })}
                {edges.filter((e) => e.source === selectedNode.id || e.target === selectedNode.id)
                  .length === 0 && <div className='text-xs text-[var(--text-muted)]'>无连接</div>}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export function KnowledgeGraph() {
  return (
    <ReactFlowProvider>
      <GraphInner />
    </ReactFlowProvider>
  )
}

export default KnowledgeGraph
