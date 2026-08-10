/**
 * 前端应用统一运行时配置
 *
 * 所有可调参数（超时、重试、Storage Key、SSE 超时等）集中到此文件。
 * 支持通过 Vite 环境变量（import.meta.env.VITE_*）覆盖，
 * 未设置时使用合理的默认值，使项目开箱即用。
 */

export interface ApiConfig {
  baseUrl: string
  timeout: number
  retries: number
  retryDelay: number
  sseTimeout: number
}

export interface StorageKeysConfig {
  token: string
}

export interface AppConfig {
  api: ApiConfig
  storage: StorageKeysConfig
  /** localStorage 根前缀，便于多环境部署时避免互相污染 */
  storagePrefix: string
}

const toInt = (val: string | undefined, fallback: number): number => {
  if (val === undefined || val === null || val === '') return fallback
  const parsed = Number.parseInt(val, 10)
  return Number.isFinite(parsed) ? parsed : fallback
}

/**
 * 所有 Vite 环境变量必须以 VITE_ 开头，才会在客户端被注入。
 * 可在 .env / .env.development / .env.production 中覆盖。
 */
const api: ApiConfig = {
  baseUrl: import.meta.env.VITE_API_URL || '/api',
  timeout: toInt(import.meta.env.VITE_API_TIMEOUT, 30000),
  retries: toInt(import.meta.env.VITE_API_RETRIES, 2),
  retryDelay: toInt(import.meta.env.VITE_API_RETRY_DELAY, 1000),
  sseTimeout: toInt(import.meta.env.VITE_SSE_TIMEOUT, 60000),
}

const storagePrefix = import.meta.env.VITE_STORAGE_PREFIX || 'kchat_'

const storage: StorageKeysConfig = {
  token: `${storagePrefix}token`,
}

export const appConfig: AppConfig = {
  api,
  storage,
  storagePrefix,
} as const

export type { AppConfig as default }
