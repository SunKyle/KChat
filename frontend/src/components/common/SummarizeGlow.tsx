import './SummarizeGlow.css'

interface SummarizeGlowProps {
  active: boolean
}

export function SummarizeGlow({ active }: SummarizeGlowProps) {
  if (!active) return null
  return <div className='summarize-glow' />
}
