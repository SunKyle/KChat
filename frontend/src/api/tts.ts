import { request, requestBlob } from './client'
import type { Speaker, TtsHealth, SpeakRequest, PreviewRequest } from '../types/tts'

const BASE_URL = import.meta.env.VITE_API_URL || '/api'

export const tts = {
  /**
   * 朗读文本，返回 wav Blob
   */
  speak: async (req: SpeakRequest, userId?: string): Promise<Blob> => {
    const headers: Record<string, string> = {}
    if (userId) {
      headers['X-User-Id'] = userId
    }

    return requestBlob('/tts/speak', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(req),
      timeout: 60000, // TTS 可能较慢
      retries: 1,
    })
  },

  /**
   * 流式朗读（SSE），通过回调接收音频分片
   */
  speakStream: (
    req: SpeakRequest,
    onChunk: (base64Chunk: string) => void,
    onEvent: (event: { type: 'start' | 'done' | 'error'; message?: string; totalBytes?: number }) => void,
    userId?: string
  ): (() => void) => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    }
    if (userId) {
      headers['X-User-Id'] = userId
    }

    const controller = new AbortController()
    const url = `${BASE_URL}/tts/speak/stream`

    ;(async () => {
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers,
          body: JSON.stringify(req),
          signal: controller.signal,
        })

        if (!response.ok) {
          const errText = await response.text()
          onEvent({ type: 'error', message: errText })
          return
        }

        if (!response.body) {
          onEvent({ type: 'error', message: 'No response body' })
          return
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let lastEvent: { type: string; data: string } | null = null

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            const trimmed = line.trim()
            if (!trimmed) {
              // 空行 = 一个事件结束
              if (lastEvent) {
                handleSseEvent(lastEvent, onChunk, onEvent)
                lastEvent = null
              }
              continue
            }

            if (trimmed.startsWith('event:')) {
              lastEvent = { type: trimmed.slice(6).trim(), data: '' }
            } else if (trimmed.startsWith('data:')) {
              const data = trimmed.slice(5).trim()
              if (lastEvent) {
                lastEvent.data = data
              }
            }
          }
        }

        // 处理最后一个事件
        if (lastEvent) {
          handleSseEvent(lastEvent, onChunk, onEvent)
        }

        if (!lastEvent || lastEvent.type !== 'done') {
          onEvent({ type: 'done', totalBytes: 0 })
        }
      } catch (err) {
        if ((err as Error).name === 'AbortError') return
        onEvent({ type: 'error', message: (err as Error).message })
      }
    })()

    return () => controller.abort()
  },

  /**
   * 临时试听（使用临时 prompt 音频）
   */
  preview: async (req: PreviewRequest, userId?: string): Promise<Blob> => {
    const headers: Record<string, string> = {}
    if (userId) {
      headers['X-User-Id'] = userId
    }

    return requestBlob('/tts/preview', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(req),
      timeout: 60000,
      retries: 1,
    })
  },

  /**
   * 列出当前用户的音色
   */
  listSpeakers: async (userId?: string): Promise<Speaker[]> => {
    const headers: Record<string, string> = {}
    if (userId) {
      headers['X-User-Id'] = userId
    }
    return request('/tts/speakers', { headers })
  },

  /**
   * 注册新音色
   */
  registerSpeaker: async (
    name: string,
    promptText: string,
    promptWav: File,
    userId?: string
  ): Promise<Speaker> => {
    const formData = new FormData()
    formData.append('name', name)
    formData.append('prompt_text', promptText)
    formData.append('prompt_wav', promptWav)

    const headers: Record<string, string> = {}
    if (userId) {
      headers['X-User-Id'] = userId
    }

    const response = await fetch(`${BASE_URL}/tts/speakers`, {
      method: 'POST',
      headers,
      body: formData,
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new Error(errorData.message || `注册音色失败: ${response.status}`)
    }

    return response.json()
  },

  /**
   * 删除音色
   */
  deleteSpeaker: async (spkId: string, userId?: string): Promise<void> => {
    const headers: Record<string, string> = {}
    if (userId) {
      headers['X-User-Id'] = userId
    }
    return request(`/tts/speakers/${spkId}`, { method: 'DELETE', headers })
  },

  /**
   * 获取 TTS 服务健康状态
   */
  health: async (): Promise<TtsHealth> => {
    return request('/tts/health')
  },
}

function handleSseEvent(
  event: { type: string; data: string },
  onChunk: (base64: string) => void,
  onEvent: (e: { type: 'start' | 'done' | 'error'; message?: string; totalBytes?: number }) => void
) {
  switch (event.type) {
    case 'start':
      onEvent({ type: 'start' })
      break
    case 'audio':
      onChunk(event.data)
      break
    case 'done':
      onEvent({ type: 'done', totalBytes: Number(event.data) || 0 })
      break
    case 'error':
      onEvent({ type: 'error', message: event.data })
      break
  }
}
