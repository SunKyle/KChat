import {
  Menu,
  X,
  ChevronDown,
  PanelLeftClose,
  PanelLeft,
  Plus,
  Edit2,
  Trash2,
  Save,
  Copy,
  Download,
  Search,
  Star,
  Filter,
  Database,
  Code,
  Image,
  Paperclip,
  Check,
  AlertCircle,
  Sparkles,
  ZoomIn,
  MessageSquare,
  MessageCircle,
  Send,
  Bot,
  User,
  ArrowDown,
  RotateCcw,
  BookOpen,
  FileText,
  CheckCircle,
  Heart,
  Lightbulb,
  Cpu,
  BrainCircuit,
  Pencil,
  Square,
} from 'lucide-react'
import { createContext, useContext } from 'react'
import type { ReactNode } from 'react'

export interface IconTheme {
  size: number
  strokeWidth: number
  color: string
}

export interface IconProviderProps {
  theme?: Partial<IconTheme>
  children: ReactNode
}

const defaultTheme: IconTheme = {
  size: 20,
  strokeWidth: 2,
  color: 'currentColor',
}

export const IconThemeContext = createContext<IconTheme>(defaultTheme)

export function IconProvider({ theme, children }: IconProviderProps) {
  const mergedTheme = { ...defaultTheme, ...theme }
  return <IconThemeContext.Provider value={mergedTheme}>{children}</IconThemeContext.Provider>
}

const IconMap: Record<
  string,
  React.ComponentType<{
    size?: number
    strokeWidth?: number
    className?: string
    style?: React.CSSProperties
  }>
> = {
  Menu,
  X,
  ChevronDown,
  PanelLeftClose,
  PanelLeft,
  Plus,
  Edit2,
  Trash2,
  Save,
  Copy,
  Download,
  Search,
  Star,
  Filter,
  Database,
  Code,
  Image,
  Paperclip,
  Check,
  AlertCircle,
  Sparkles,
  ZoomIn,
  MessageSquare,
  MessageCircle,
  Send,
  Bot,
  User,
  ArrowDown,
  RotateCcw,
  BookOpen,
  FileText,
  CheckCircle,
  Heart,
  Lightbulb,
  Cpu,
  BrainCircuit,
  Pencil,
  Square,
}

export type IconName = keyof typeof IconMap

export type { IconName as LucideIconName }

export interface IconProps {
  name: IconName
  size?: number
  strokeWidth?: number
  className?: string
  style?: React.CSSProperties
}

export function Icon({ name, size, strokeWidth, className, style }: IconProps) {
  const theme = useContext(IconThemeContext)
  const IconComponent = IconMap[name]

  if (!IconComponent) {
    console.warn(`Icon "${name}" not found`)
    return null
  }

  const computedSize = size ?? theme.size
  const computedStrokeWidth = strokeWidth ?? theme.strokeWidth

  return (
    <IconComponent
      size={computedSize}
      strokeWidth={computedStrokeWidth}
      className={className}
      style={{
        color: theme.color,
        ...style,
      }}
    />
  )
}
