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
import { Database, X } from 'lucide-react'
import { cogneeMemory, type GraphNode, type GraphEdge } from '../../../api/cognee'

import '@xyflow/react/dist/style.css'

interface NodeDataShape {
  label: string
  type: string
  properties: Record<string, unknown>
  /** 布局内部字段：target Handle 朝向，由 dagre 布局按方向设置 */
  _handleTarget?: Position
  /** 布局内部字段：source Handle 朝向，由 dagre 布局按方向设置 */
  _handleSource?: Position
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

const nodeWidth = 180
const nodeHeight = 60

type RankDir = 'LR' | 'TB' | 'RL' | 'BT'

function getLayoutedNodes(
  nodes: Node[],
  edges: Edge[],
  options?: { nodesep?: number; ranksep?: number; rankdir?: RankDir }
): Node[] {
  const { nodesep = 80, ranksep = 120, rankdir = 'LR' } = options ?? {}
  // 根据方向决定节点连接点位置，让连线从节点边缘进出而不是从中心
  const targetPos =
    rankdir === 'LR'
      ? Position.Left
      : rankdir === 'RL'
        ? Position.Right
        : rankdir === 'TB'
          ? Position.Top
          : Position.Bottom
  const sourcePos =
    rankdir === 'LR'
      ? Position.Right
      : rankdir === 'RL'
        ? Position.Left
        : rankdir === 'TB'
          ? Position.Bottom
          : Position.Top
  // 每次创建全新的 Graph 实例，避免全局单例残留旧节点/边导致子图布局失效
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir, nodesep, ranksep })
  g.setDefaultEdgeLabel(() => ({}))

  nodes.forEach((node) => {
    g.setNode(node.id, { width: nodeWidth, height: nodeHeight })
  })

  edges.forEach((edge) => {
    g.setEdge(edge.source, edge.target)
  })

  dagre.layout(g)

  return nodes.map((node) => {
    const nodeWithPosition = g.node(node.id)
    return {
      ...node,
      targetPosition: targetPos,
      sourcePosition: sourcePos,
      position: {
        x: nodeWithPosition.x - nodeWidth / 2,
        y: nodeWithPosition.y - nodeHeight / 2,
      },
      // 把 Handle 朝向写入 data，供自定义节点组件 GraphNodeComponent 读取
      data: {
        ...(node.data as object),
        _handleTarget: targetPos,
        _handleSource: sourcePos,
      } as unknown as Node['data'],
    } as Node
  })
}

function GraphNodeComponent({ data, selected }: NodeProps) {
  const d = data as unknown as NodeDataShape
  const color = getNodeColor(d.type)
  const typeLabel = getNodeTypeLabel(d.type)
  // Handle 朝向由 dagre 布局按方向设置，默认左右（兼容未布局的初始状态）
  const targetPos = d._handleTarget ?? Position.Left
  const sourcePos = d._handleSource ?? Position.Right

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
        position={targetPos}
        className='!w-2 !h-2 !min-w-2 !min-h-2 !border-0 !bg-[var(--border-secondary)]'
      />
      <div className={`w-2 h-2 rounded-full ${color} mb-1.5`} />
      <div className='text-xs font-semibold text-[var(--text-primary)] leading-tight line-clamp-2'>
        {d.label}
      </div>
      <div className='text-[10px] text-[var(--text-muted)] mt-0.5'>{typeLabel}</div>
      <Handle
        type='source'
        position={sourcePos}
        className='!w-2 !h-2 !min-w-2 !min-h-2 !border-0 !bg-[var(--border-secondary)]'
      />
    </div>
  )
}

const nodeTypes: NodeTypes = {
  cogneeNode: GraphNodeComponent,
}

interface GraphInnerProps {
  onStatsChange?: (stats: { nodes: number; edges: number }) => void
  dataset?: string
  /** 外部受控搜索关键词 */
  externalSearchQuery?: string
  /** 外部受控布局方向 */
  externalRankdir?: RankDir
}

function GraphInner({
  onStatsChange,
  dataset,
  externalSearchQuery = '',
  externalRankdir = 'LR',
}: GraphInnerProps) {
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const { fitView } = useReactFlow()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedNode, setSelectedNode] = useState<Node | null>(null)
  const [filteredNodeIds, setFilteredNodeIds] = useState<Set<string> | null>(null)
  const [highlightedNodeIds, setHighlightedNodeIds] = useState<Set<string> | null>(null)
  const [hiddenTypes, setHiddenTypes] = useState<Set<string>>(
    new Set(['EntityType', 'TextDocument', 'DocumentChunk', 'TextSummary'])
  )
  const transitionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // 用 ref 保存最新过滤状态，供 fetchGraph 闭包访问（避免扩大依赖集导致循环）
  const hiddenTypesRef = useRef<Set<string>>(hiddenTypes)
  const filteredNodeIdsRef = useRef<Set<string> | null>(filteredNodeIds)
  const rankdirRef = useRef<RankDir>(externalRankdir)
  const relayoutSubgraphRef = useRef<typeof relayoutSubgraph | null>(null)

  // 保持 ref 与最新 state 同步
  useEffect(() => {
    hiddenTypesRef.current = hiddenTypes
  }, [hiddenTypes])
  useEffect(() => {
    filteredNodeIdsRef.current = filteredNodeIds
  }, [filteredNodeIds])
  useEffect(() => {
    rankdirRef.current = externalRankdir
  }, [externalRankdir])

  // rankdir 变化时触发重新布局
  const prevRankdirRef = useRef<RankDir>(externalRankdir)
  useEffect(() => {
    if (prevRankdirRef.current === externalRankdir) return
    prevRankdirRef.current = externalRankdir
    if (nodes.length === 0) return
    // 根据当前过滤状态决定全图 or 子图布局
    const curHidden = hiddenTypesRef.current
    const curFiltered = filteredNodeIdsRef.current
    const hasFilter = curHidden.size > 0 || curFiltered !== null
    if (hasFilter) {
      let visible: Set<string> | null = new Set()
      nodes.forEach((n) => {
        const type = (n.data?.type as string) ?? ''
        if (curHidden.has(type)) return
        if (curFiltered !== null && !curFiltered.has(n.id)) return
        visible!.add(n.id)
      })
      if (visible.size === 0) visible = null
      relayoutSubgraphRef.current?.(visible)
    } else {
      const layouted = getLayoutedNodes(nodes, edges, { rankdir: externalRankdir })
      setNodes(layouted)
      setTimeout(() => {
        fitView({ padding: 0.2, duration: 500 })
      }, 50)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalRankdir])

  const fetchGraph = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await cogneeMemory.getGraph(dataset)
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
        type: 'default',
        labelPosition: 'center',
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

      const layouted = getLayoutedNodes(rfNodes, rfEdges, { rankdir: rankdirRef.current })
      setNodes(layouted)
      setEdges(rfEdges)
      onStatsChange?.({ nodes: layouted.length, edges: rfEdges.length })

      // 首屏/刷新后先以全图为基准 fitView；若当前有搜索或类型过滤，再对子图重新自适应布局
      setTimeout(() => {
        fitView({ padding: 0.2, duration: 500 })
        const curHidden = hiddenTypesRef.current
        const curFiltered = filteredNodeIdsRef.current
        const hasFilter = curHidden.size > 0 || curFiltered !== null
        if (hasFilter) {
          // 再延迟一轮，等 fitView 和节点渲染稳定后对子图重布局
          setTimeout(() => {
            // 动态计算可见节点：隐藏类型过滤 + 搜索过滤取交集
            let visible: Set<string> | null = null
            if (curHidden.size === 0 && curFiltered === null) {
              visible = null
            } else {
              visible = new Set<string>()
              const allNodes = data.nodes
              allNodes.forEach((n: GraphNode) => {
                if (curHidden.has(n.type)) return
                if (curFiltered !== null && !curFiltered.has(n.id)) return
                visible!.add(n.id)
              })
            }
            relayoutSubgraphRef.current?.(visible)
          }, 380)
        }
      }, 100)
    } catch (err) {
      setError(String(err))
      setNodes([])
      setEdges([])
      onStatsChange?.({ nodes: 0, edges: 0 })
    } finally {
      setLoading(false)
    }
  }, [fitView, setNodes, setEdges, onStatsChange, dataset])

  useEffect(() => {
    fetchGraph()
  }, [fetchGraph])

  // 组件卸载时清除残留的 transition 清理 timer
  useEffect(() => {
    return () => {
      if (transitionTimerRef.current) {
        clearTimeout(transitionTimerRef.current)
        transitionTimerRef.current = null
      }
    }
  }, [])

  // externalSearchQuery 变化时执行搜索逻辑
  useEffect(() => {
    const value = externalSearchQuery
    if (!value.trim()) {
      setFilteredNodeIds(null)
      relayoutSubgraph(computeVisibleNodeIds(hiddenTypesRef.current, null))
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
        edges.forEach((e) => {
          if (e.source === n.id) matched.add(e.target)
          if (e.target === n.id) matched.add(e.source)
        })
      }
    })
    setFilteredNodeIds(matched)
    relayoutSubgraph(computeVisibleNodeIds(hiddenTypesRef.current, matched))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalSearchQuery])

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

  // 自适应布局：对可见子图重新执行 dagre 布局，平滑过渡到新位置
  const relayoutSubgraph = useCallback(
    (visibleNodeIds: Set<string> | null) => {
      // 清除上一次的 transition 清理 timer，避免竞态
      if (transitionTimerRef.current) {
        clearTimeout(transitionTimerRef.current)
        transitionTimerRef.current = null
      }

      // 确定参与布局的节点与边
      let nodesToLayout: Node[]
      let edgesToLayout: Edge[]
      if (visibleNodeIds !== null) {
        if (visibleNodeIds.size === 0) return // 无匹配节点，不布局
        nodesToLayout = nodes.filter((n) => visibleNodeIds.has(n.id))
        edgesToLayout = edges.filter(
          (e) => visibleNodeIds.has(e.source) && visibleNodeIds.has(e.target)
        )
      } else {
        nodesToLayout = nodes
        edgesToLayout = edges
      }

      if (nodesToLayout.length === 0) return

      // 根据可见节点数量自适应调整间距：节点少时更舒展，避免重叠
      const visibleCount = nodesToLayout.length
      const nodesep = visibleCount <= 5 ? 120 : visibleCount <= 10 ? 100 : 80
      const ranksep = visibleCount <= 5 ? 160 : visibleCount <= 10 ? 140 : 120

      const layouted = getLayoutedNodes(nodesToLayout, edgesToLayout, {
        nodesep,
        ranksep,
        rankdir: rankdirRef.current,
      })
      // 同时记录 position / targetPosition / sourcePosition / data 中的 Handle 朝向
      const layoutedMap = new Map(
        layouted.map((n) => [
          n.id,
          {
            position: n.position,
            targetPosition: n.targetPosition,
            sourcePosition: n.sourcePosition,
            handleTarget: (n.data as unknown as NodeDataShape)?._handleTarget,
            handleSource: (n.data as unknown as NodeDataShape)?._handleSource,
          },
        ])
      )

      // 更新节点位置 + Handle 朝向（含 data 内字段），并添加 transition 实现平滑过渡
      setNodes((prev) =>
        prev.map((n) => {
          const info = layoutedMap.get(n.id)
          if (!info) return n
          return {
            ...n,
            position: info.position,
            targetPosition: info.targetPosition,
            sourcePosition: info.sourcePosition,
            data: {
              ...(n.data as object),
              _handleTarget: info.handleTarget,
              _handleSource: info.handleSource,
            } as unknown as Node['data'],
            style: { ...n.style, transition: 'transform 450ms ease' },
          }
        })
      )

      // 动画结束后移除 transition，避免拖拽时粘滞
      transitionTimerRef.current = setTimeout(() => {
        setNodes((prev) =>
          prev.map((n) => ({
            ...n,
            style: { ...n.style, transition: undefined },
          }))
        )
        transitionTimerRef.current = null
      }, 470)

      // 延迟 fitView，让 setNodes 先完成渲染更新后基于新位置适配视口
      setTimeout(() => {
        fitView({ padding: 0.3, duration: 500 })
      }, 50)
    },
    [nodes, edges, setNodes, fitView]
  )

  // 将最新的 relayoutSubgraph 写入 ref，供 fetchGraph 闭包回调访问
  useEffect(() => {
    relayoutSubgraphRef.current = relayoutSubgraph
  }, [relayoutSubgraph])

  // 根据类型过滤 + 搜索过滤计算当前可见节点集合（null 表示全图可见）
  const computeVisibleNodeIds = useCallback(
    (hidden: Set<string>, filtered: Set<string> | null): Set<string> | null => {
      if (hidden.size === 0 && filtered === null) return null
      const visible = new Set<string>()
      nodes.forEach((n) => {
        const d = n.data as unknown as NodeDataShape
        if (hidden.has(d.type)) return
        if (filtered !== null && !filtered.has(n.id)) return
        visible.add(n.id)
      })
      return visible
    },
    [nodes]
  )

  // 切换类型显示/隐藏后对可见节点重新自适应布局
  const handleTypeToggle = useCallback(
    (type: string) => {
      const next = new Set(hiddenTypes)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      setHiddenTypes(next)
      relayoutSubgraph(computeVisibleNodeIds(next, filteredNodeIds))
    },
    [hiddenTypes, filteredNodeIds, setHiddenTypes, relayoutSubgraph, computeVisibleNodeIds]
  )

  // 全部显示后恢复全图布局（若搜索仍激活则只布局搜索结果）
  const handleShowAllTypes = useCallback(() => {
    setHiddenTypes(new Set())
    relayoutSubgraph(computeVisibleNodeIds(new Set(), filteredNodeIds))
  }, [setHiddenTypes, relayoutSubgraph, computeVisibleNodeIds, filteredNodeIds])

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
      {/* Legend + Type Filter */}
      <Panel position='bottom-left' className='!m-2'>
        <div className='bg-[var(--bg-card)]/95 backdrop-blur rounded-xl border border-[var(--border-secondary)] px-4 py-3 shadow-lg'>
          <div className='flex items-center justify-between mb-2'>
            <span className='text-sm font-semibold text-[var(--text-secondary)]'>类型过滤</span>
            {hiddenTypes.size > 0 && (
              <button
                onClick={handleShowAllTypes}
                className='text-xs text-[var(--accent-primary)] hover:underline'
              >
                全部显示
              </button>
            )}
          </div>
          <div className='flex flex-col gap-1.5'>
            {Object.entries(typeCounts)
              .sort((a, b) => b[1] - a[1])
              .map(([type, count]) => {
                const isHidden = hiddenTypes.has(type)
                return (
                  <button
                    key={type}
                    onClick={() => handleTypeToggle(type)}
                    className={`flex items-center gap-2 text-xs transition-opacity ${
                      isHidden ? 'opacity-30 line-through' : 'hover:opacity-80'
                    } text-[var(--text-muted)] cursor-pointer`}
                  >
                    <span className={`w-2.5 h-2.5 rounded-full ${getNodeColor(type)}`} />
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

export function KnowledgeGraph({
  onStatsChange,
  dataset,
  externalSearchQuery,
  externalRankdir,
}: {
  onStatsChange?: (stats: { nodes: number; edges: number }) => void
  dataset?: string
  externalSearchQuery?: string
  externalRankdir?: RankDir
}) {
  return (
    <ReactFlowProvider>
      <GraphInner
        onStatsChange={onStatsChange}
        dataset={dataset}
        externalSearchQuery={externalSearchQuery}
        externalRankdir={externalRankdir}
      />
    </ReactFlowProvider>
  )
}

export default KnowledgeGraph
