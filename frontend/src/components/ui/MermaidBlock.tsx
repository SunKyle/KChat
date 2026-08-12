import { useEffect, useRef, useState } from 'react'
import mermaid from 'mermaid'
import { useTheme } from '../../context/ThemeContext'

interface MermaidBlockProps {
  chart: string
}

let initialized = false
let renderId = 0

/**
 * 渲染 Mermaid 图表（flowchart / sequenceDiagram / erDiagram 等）。
 *
 * 通过 react-markdown 的 code 组件识别 `language-mermaid` 代码块后调用本组件，
 * 使用 mermaid.render 将图源文本转为 SVG 内联展示。
 */
export function MermaidBlock({ chart }: MermaidBlockProps) {
  const { theme } = useTheme()
  const [svg, setSvg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!initialized) {
      mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'loose',
        theme: 'default',
      })
      initialized = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const render = async () => {
      setError(null)
      setSvg(null)
      const id = `kchat-mermaid-${Date.now()}-${renderId++}`
      try {
        const { svg: result } = await mermaid.render(id, chart)
        if (!cancelled) setSvg(result)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e))
      }
    }
    render()
    return () => {
      cancelled = true
    }
  }, [chart, theme])

  // 渲染失败时回退为纯文本代码，避免用户看到空白
  if (error) {
    return (
      <pre className='my-3 rounded-xl overflow-x-auto p-4 text-sm theme-text-secondary whitespace-pre-wrap break-words'>
        {chart}
      </pre>
    )
  }

  return (
    <div
      ref={containerRef}
      className='my-3 rounded-xl overflow-x-auto p-4 flex justify-center'
      dangerouslySetInnerHTML={{ __html: svg || '' }}
    />
  )
}
