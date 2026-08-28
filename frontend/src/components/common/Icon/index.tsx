import {
  AlertCircle,
  AlertTriangle,
  ArrowDown,
  ArrowLeftRight,
  BarChart3,
  Bell,
  BellOff,
  BellRing,
  Bold,
  BookOpen,
  Bot,
  Brain,
  BrainCircuit,
  Calendar,
  Camera,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Circle,
  Clock,
  Code,
  Columns3,
  Copy,
  Cpu,
  Database,
  Download,
  ExternalLink,
  Eye,
  EyeOff,
  FileCode,
  FileSearch,
  FileText,
  Filter,
  Globe,
  Heading,
  Heart,
  Image,
  ImageOff,
  Info,
  Italic,
  Key,
  Languages,
  Leaf,
  Lightbulb,
  Link,
  List,
  ListOrdered,
  ListTodo,
  Loader2,
  Lock,
  Mail,
  Maximize2,
  Menu,
  MessageCircle,
  MessageSquare,
  MessageSquarePlus,
  Monitor,
  Moon,
  Network,
  PanelLeft,
  PanelLeftClose,
  Paperclip,
  Pencil,
  Pin,
  Plus,
  Power,
  Quote,
  RefreshCw,
  RotateCcw,
  Save,
  ScrollText,
  Search,
  SearchX,
  Send,
  Settings,
  Share2,
  Shield,
  Sliders,
  Smartphone,
  Sparkles,
  Square,
  Star,
  Strikethrough,
  Sun,
  Trash2,
  Undo2,
  Upload,
  User,
  Volume2,
  Wand2,
  WifiOff,
  Wrench,
  X,
  XCircle,
  ZoomIn,
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

/**
 * Central icon registry.
 * Canonical variants: Edit2 / Edit3 / PenLine → Pencil, CheckCircle → CheckCircle2.
 * All icons render with strokeWidth 1.75 (via IconProvider theme) for a consistent look.
 */
const IconMap: Record<
  string,
  React.ComponentType<{
    size?: number
    strokeWidth?: number
    className?: string
    style?: React.CSSProperties
  }>
> = {
  AlertCircle,
  AlertTriangle,
  ArrowDown,
  ArrowLeftRight,
  BarChart3,
  Bell,
  BellOff,
  BellRing,
  Bold,
  BookOpen,
  Bot,
  Brain,
  BrainCircuit,
  Calendar,
  Camera,
  Check,
  CheckCircle2,
  CheckSquare,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Circle,
  Clock,
  Code,
  Columns3,
  Copy,
  Cpu,
  Database,
  Download,
  ExternalLink,
  Eye,
  EyeOff,
  FileCode,
  FileSearch,
  FileText,
  Filter,
  Globe,
  Heading,
  Heart,
  Image,
  ImageOff,
  Info,
  Italic,
  Key,
  Languages,
  Leaf,
  Lightbulb,
  Link,
  List,
  ListOrdered,
  ListTodo,
  Loader2,
  Lock,
  Mail,
  Maximize2,
  Menu,
  MessageCircle,
  MessageSquare,
  MessageSquarePlus,
  Monitor,
  Moon,
  Network,
  PanelLeft,
  PanelLeftClose,
  Paperclip,
  Pencil,
  Pin,
  Plus,
  Power,
  Quote,
  RefreshCw,
  RotateCcw,
  Save,
  ScrollText,
  Search,
  SearchX,
  Send,
  Settings,
  Share2,
  Shield,
  Sliders,
  Smartphone,
  Sparkles,
  Square,
  Star,
  Strikethrough,
  Sun,
  Trash2,
  Undo2,
  Upload,
  User,
  Volume2,
  Wand2,
  WifiOff,
  Wrench,
  X,
  XCircle,
  ZoomIn,
}

export type IconName = keyof typeof IconMap

export type { IconName as LucideIconName }

/** 判断字符串是否为 IconMap 中合法的图标名（用于后端/用户数据的安全回退） */
export function isIconName(name: string | null | undefined): name is IconName {
  return typeof name === 'string' && name in IconMap
}

export interface IconProps {
  name: IconName
  size?: number | IconSize
  strokeWidth?: number
  className?: string
  style?: React.CSSProperties
  [key: string]: unknown
}

export function Icon({ name, size, strokeWidth, className, style, ...rest }: IconProps) {
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
      {...rest}
    />
  )
}
