const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

interface RequestOptions extends RequestInit {
  headers?: Record<string, string>
  timeout?: number
  retries?: number
  retryDelay?: number
}

function isRetryableError(error: Error): boolean {
  if (error.message.includes('请求超时')) return true
  if (error.message.includes('NetworkError')) return true
  if (error.message.includes('Failed to fetch')) return true
  return false
}

async function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function request<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
  const { timeout = 30000, headers, retries = 2, retryDelay = 1000, ...rest } = options

  let attempt = 0
  let lastError: Error | null = null

  while (attempt <= retries) {
    const abortController = new AbortController()
    const timeoutId = setTimeout(() => abortController.abort(), timeout)

    try {
      const response = await fetch(`${BASE_URL}${endpoint}`, {
        headers: {
          'Content-Type': 'application/json',
          ...headers,
        },
        signal: abortController.signal,
        ...rest,
      })

      clearTimeout(timeoutId)

      if (!response.ok) {
        const error = await response.json().catch(() => ({
          message: `HTTP error! status: ${response.status}`,
        }))
        throw new Error(error.message || `HTTP error! status: ${response.status}`)
      }

      return response.json()
    } catch (error) {
      clearTimeout(timeoutId)

      if (error instanceof Error) {
        lastError = error

        if (attempt < retries && isRetryableError(error)) {
          attempt++
          await delay(retryDelay * Math.pow(2, attempt - 1))
          continue
        }
      }

      throw error
    }
  }

  throw lastError || new Error('请求失败')
}

export async function requestStream<T>(
  endpoint: string,
  options: RequestOptions = {},
  onData: (data: T) => void,
  onError?: (error: Error) => void
): Promise<void> {
  const { headers, ...rest } = options

  const response = await fetch(`${BASE_URL}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    credentials: 'same-origin',
    ...rest,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({
      message: `HTTP error! status: ${response.status}`,
    }))
    throw new Error(error.message || `HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法获取响应流')
  }

  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
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
    onError?.(error as Error)
    throw error
  }
}

export async function uploadFile(endpoint: string, file: File): Promise<{ url: string }> {
  const formData = new FormData()
  formData.append('image', file)

  const response = await fetch(`${BASE_URL}${endpoint}`, {
    method: 'POST',
    body: formData,
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({
      message: `File upload failed: ${response.status}`,
    }))
    throw new Error(error.message || `File upload failed: ${response.status}`)
  }

  return response.json()
}

export async function requestSSE(
  endpoint: string,
  options: RequestOptions = {},
  onMessage: (content: string) => void,
  onComplete: (messageId: string) => void,
  onError: (error: Error) => void,
  controller?: AbortController
): Promise<void> {
  try {
    const abortController = controller || new AbortController()
    const timeout = setTimeout(() => {
      abortController.abort()
    }, 60000)

    const response = await fetch(`${BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
        ...options.headers,
      },
      body: options.body,
      credentials: 'same-origin',
      signal: abortController.signal,
      ...options,
    })

    clearTimeout(timeout)

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    if (!response.body) {
      throw new Error('No response body')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const processBuffer = () => {
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
            onComplete(parsedData.messageId)
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
    console.error('SSE stream error:', error)
    onError(error as Error)
  }
}
