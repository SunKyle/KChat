import * as Icons from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface IconProps {
  name: keyof typeof Icons
  size?: number
  className?: string
}

export function Icon({ name, size = 20, className = '' }: IconProps) {
  const LucideIconComponent = Icons[name] as LucideIcon

  if (!LucideIconComponent) {
    return null
  }

  return <LucideIconComponent size={size} className={className} />
}

export default Icon
