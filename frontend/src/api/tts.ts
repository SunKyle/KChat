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
