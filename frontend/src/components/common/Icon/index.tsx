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
  Settings,
  Sun,
  Moon,
  Loader2,
} from 'lucide-react'
import { createContext, useContext } from 'react'
import type { ReactNode } from 'react'

/**
 * Icon size scale (aligned with design tokens):
 *  xs: 12  — tiny indicators, inline badges
 *  sm: 14  — compact action buttons, tags
 *  md: 16  — default action buttons
 *  lg: 20  — navigation, section headers
 *  xl: 24  — prominent actions, empty states
 *  2xl: 32 — hero / empty-state illustrations
 */
export const ICON_SIZES = {
  xs: 12,
  sm: 14,
  md: 16,
  lg: 20,
  xl: 24,
  '2xl': 32,
} as const

export type IconSize = keyof typeof ICON_SIZES

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
  size: ICON_SIZES.md,
  strokeWidth: 1.75,
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
  Settings,
  Sun,
  Moon,
  Loader2,
}

export type IconName = keyof typeof IconMap

export type { IconName as LucideIconName }

export interface IconProps {
  name: IconName
  size?: number | IconSize
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

  const computedSize = typeof size === 'string' ? ICON_SIZES[size] : (size ?? theme.size)
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
