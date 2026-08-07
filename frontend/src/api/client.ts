const BASE_URL = import.meta.env.VITE_API_URL || '/api'

interface RequestOptions extends RequestInit {
  headers?: Record<string, string>
  timeout?: number
  retries?: number
  retryDelay?: number
  skipAuth?: boolean
}

interface ApiError extends Error {
  status?: number
  code?: string
  data?: unknown
}

function createApiError(message: string, status?: number, code?: string, data?: unknown): ApiError {
  const error = new Error(message) as ApiError
  error.status = status
  error.code = code
  error.data = data
  return error
}

function isRetryableError(error: ApiError): boolean {
  if (error.message.includes('请求超时')) return true
  if (error.message.includes('NetworkError')) return true
  if (error.message.includes('Failed to fetch')) return true
  if (error.status === 500 || error.status === 502 || error.status === 503 || error.status === 504) return true
  return false
}

function isNetworkError(error: Error): boolean {
  return error.message.includes('Failed to fetch') || error.message.includes('NetworkError')
}

async function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function generateRequestId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
}

function logRequest(requestId: string, endpoint: string, options: RequestOptions): void {
  if (import.meta.env.NODE_ENV !== 'development') return
  console.debug(`[API] [${requestId}] Request: ${options.method || 'GET'} ${endpoint}`, {
    body: options.body ? 'Present' : 'None',
    timeout: options.timeout,
    retries: options.retries,
  })
}

function logResponse(requestId: string, status: number, duration: number): void {
  if (import.meta.env.NODE_ENV !== 'development') return
  console.debug(`[API] [${requestId}] Response: ${status} (${duration}ms)`, { status })
}

function logError(requestId: string, error: ApiError): void {
  if (import.meta.env.NODE_ENV !== 'development') return
  console.error(`[API] [${requestId}] Error: ${error.message}`, {
    status: error.status,
    code: error.code,
    data: error.data,
  })
}

async function applyRequestInterceptors(options: RequestOptions): Promise<RequestOptions> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...options.headers,
  }

  if (!options.skipAuth) {
    const token = localStorage.getItem('kchat_token')
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
  }

  return {
    ...options,
    headers,
  }
}

async function applyResponseInterceptors<T>(response: Response, requestId: string): Promise<T> {
  const duration = Date.now() - parseInt(requestId.split('-')[0])
  logResponse(requestId, response.status, duration)

  if (!response.ok) {
    let errorData: { message?: string; code?: string; data?: unknown } = {}
    try {
      errorData = await response.json()
    } catch {
      errorData = { message: `HTTP error! status: ${response.status}` }
    }

    const error = createApiError(
      errorData.message || `HTTP error! status: ${response.status}`,
      response.status,
      errorData.code,
      errorData.data
    )
    logError(requestId, error)
    throw error
  }

  const contentType = response.headers.get('content-type')
  if (contentType && contentType.includes('application/json')) {
    return response.json()
  }

  return response.text() as unknown as T
}

export async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const requestId = generateRequestId()
  const { timeout = 30000, retries = 2, retryDelay = 1000, ...rest } = await applyRequestInterceptors(options)

  logRequest(requestId, endpoint, { ...rest, timeout, retries })

  let attempt = 0
  let lastError: ApiError | null = null

  while (attempt <= retries) {
    const abortController = new AbortController()
    const timeoutId = setTimeout(() => abortController.abort(), timeout)

    try {
      const response = await fetch(`${BASE_URL}${endpoint}`, {
        signal: abortController.signal,
        ...rest,
      })

      clearTimeout(timeoutId)
      return applyResponseInterceptors<T>(response, requestId)
    } catch (error) {
      clearTimeout(timeoutId)

      const apiError = error instanceof Error
        ? createApiError(error.message, undefined, isNetworkError(error) ? 'NETWORK_ERROR' : undefined)
        : createApiError(String(error))

      lastError = apiError

      if (attempt < retries && isRetryableError(apiError)) {
        attempt++
        const delayMs = retryDelay * Math.pow(2, attempt - 1)
        if (import.meta.env.NODE_ENV === 'development') {
          console.debug(`[API] [${requestId}] Retrying (${attempt}/${retries}) after ${delayMs}ms`)
        }
        await delay(delayMs)
        continue
      }

      logError(requestId, apiError)
      throw apiError
    }
  }

  throw lastError || createApiError('请求失败')
}

export async function requestStream<T>(
  endpoint: string,
  options: RequestOptions = {},
  onData: (data: T) => void,
  onError?: (error: ApiError) => void
): Promise<void> {
  const requestId = generateRequestId()
  const processedOptions = await applyRequestInterceptors(options)

  logRequest(requestId, endpoint, processedOptions)

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      credentials: 'same-origin',
      ...processedOptions,
    })

    await applyResponseInterceptors(response, requestId)

    const reader = response.body?.getReader()
    if (!reader) {
      const error = createApiError('无法获取响应流')
      logError(requestId, error)
      throw error
    }

    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.trim()) {
          try {
            const parsed = JSON.parse(line)
            onData(parsed)
          } catch {
            // Ignore JSON parse errors for incomplete lines
          }
        }
      }
    }

    if (buffer.trim()) {
      try {
        const parsed = JSON.parse(buffer)
        onData(parsed)
      } catch {
        // Ignore JSON parse errors for incomplete buffer
      }
    }
  } catch (error) {
    const apiError = error instanceof Error
      ? createApiError(error.message, undefined, isNetworkError(error) ? 'NETWORK_ERROR' : undefined)
      : createApiError(String(error))
    onError?.(apiError)
    throw apiError
  }
}

export async function uploadFile(endpoint: string, file: File): Promise<{ url: string }> {
  const requestId = generateRequestId()

  logRequest(requestId, endpoint, { method: 'POST', body: 'FormData' })

  const formData = new FormData()
  formData.append('image', file)

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      body: formData,
    })

    return applyResponseInterceptors<{ url: string }>(response, requestId)
  } catch (error) {
    const apiError = error instanceof Error
      ? createApiError(error.message, undefined, isNetworkError(error) ? 'NETWORK_ERROR' : undefined)
      : createApiError(String(error))
    logError(requestId, apiError)
    throw apiError
  }
}

export async function requestSSE(
  endpoint: string,
  options: RequestOptions = {},
  onMessage: (content: string) => void,
  onComplete: (
    messageId: string,
    title?: string,
    artifacts?: Array<{ type: string; url: string; text?: string }>
  ) => void,
  onError: (error: ApiError) => void,
  controller?: AbortController,
  onSearchResults?: (results: unknown) => void,
  onImageDone?: (url: string) => void
): Promise<void> {
  const requestId = generateRequestId()
  const processedOptions = await applyRequestInterceptors(options)

  logRequest(requestId, endpoint, { ...processedOptions, method: 'POST' })

  try {
    const abortController = controller || new AbortController()
    const timeout = setTimeout(() => {
      abortController.abort()
    }, 60000)

    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
        ...processedOptions.headers,
      },
      body: processedOptions.body,
      credentials: 'same-origin',
      signal: abortController.signal,
    })

    clearTimeout(timeout)

    if (!response.ok) {
      const error = await applyResponseInterceptors(response, requestId).catch((e) => e)
      if (error instanceof Error) {
        throw error
      }
      throw createApiError(`HTTP error! status: ${response.status}`, response.status)
    }

    if (!response.body) {
      const error = createApiError('No response body')
      logError(requestId, error)
      throw error
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const processBuffer = (): boolean => {
      const index = buffer.indexOf('\n\n')
      if (index === -1) return false

      const eventBlock = buffer.substring(0, index)
      buffer = buffer.substring(index + 2)

      const lines = eventBlock.split('\n')
      let eventType = ''
      let data = ''

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        const trimmedLine = line.trim()

        if (trimmedLine.startsWith('event:')) {
          eventType = trimmedLine.substring(6).trim()
        } else if (trimmedLine.startsWith('data:')) {
          data += trimmedLine.substring(5)
        }
      }

      if (eventType && data) {
        try {
          const parsedData = JSON.parse(data)
          if (eventType === 'message' && parsedData.content) {
            onMessage(parsedData.content)
          } else if (eventType === 'done' && parsedData.messageId) {
            onComplete(parsedData.messageId, parsedData.title, parsedData.artifacts)
          } else if (eventType === 'search_results') {
            onSearchResults?.(parsedData)
          } else if (eventType === 'image_done' && parsedData.url) {
            onImageDone?.(parsedData.url)
          }
        } catch (e) {
          console.warn('Failed to parse SSE data:', e)
        }
      }
      return true
    }

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        if (value) {
          buffer += decoder.decode(value)
        }
        while (processBuffer()) {
          /* Process all remaining buffer chunks */
        }
        break
      }

      buffer += decoder.decode(value, { stream: true })
      while (processBuffer()) {
        /* Process all remaining buffer chunks */
      }
    }
  } catch (error) {
    const apiError = error instanceof Error
      ? createApiError(error.message, undefined, isNetworkError(error) ? 'NETWORK_ERROR' : undefined)
      : createApiError(String(error))
    logError(requestId, apiError)
    onError(apiError)
  }
}

export async function requestBlob(
  endpoint: string,
  options: RequestOptions = {}
): Promise<Blob> {
  const requestId = generateRequestId()
  const processedOptions = await applyRequestInterceptors(options)

  logRequest(requestId, endpoint, { ...processedOptions, method: processedOptions.method || 'GET' })

  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      ...processedOptions,
    })

    if (!response.ok) {
      let errorData: { message?: string; code?: string; data?: unknown } = {}
      try {
        errorData = await response.json()
      } catch {
        errorData = { message: `HTTP error! status: ${response.status}` }
      }

      const error = createApiError(
        errorData.message || `HTTP error! status: ${response.status}`,
        response.status,
        errorData.code,
        errorData.data
      )
      logError(requestId, error)
      throw error
    }

    const blob = await response.blob()
    return blob
  } catch (error) {
    const apiError = error instanceof Error
      ? createApiError(error.message, undefined, isNetworkError(error) ? 'NETWORK_ERROR' : undefined)
      : createApiError(String(error))
    logError(requestId, apiError)
    throw apiError
  }
}

export { createApiError, isNetworkError }
export type { ApiError }
