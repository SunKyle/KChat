import { useState, useRef, useCallback, useEffect } from 'react'
import { tts } from '../api/tts'

export type TtsState = 'idle' | 'loading' | 'playing' | 'error'

export function useTts(userId?: string) {
  const [state, setState] = useState<TtsState>('idle')
  const [error, setError] = useState<string | null>(null)
  const [currentText, setCurrentText] = useState<string | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const objectUrlRef = useRef<string | null>(null)
  const mediaSourceRef = useRef<MediaSource | null>(null)
  const sourceBufferRef = useRef<SourceBuffer | null>(null)
  const streamCancelRef = useRef<(() => void) | null>(null)
  const pendingChunksRef = useRef<{ wav: Uint8Array }[]>([])
  const audioCtxRef = useRef<AudioContext | null>(null)

  useEffect(() => {
    return () => {
      stop()
    }
    // stop 在下方定义且引用 ref，无需作为依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const speak = useCallback(
    async (text: string, spkId?: string) => {
      stop()
      setState('loading')
      setError(null)
      setCurrentText(text)

      try {
        const blob = await tts.speak({ text, spkId }, userId)
        const url = URL.createObjectURL(blob)
        objectUrlRef.current = url

        const audio = new Audio(url)
        audioRef.current = audio

        audio.onplay = () => setState('playing')
        audio.onended = () => {
          setState('idle')
          cleanup()
        }
        audio.onerror = () => {
          setState('error')
          setError('音频播放失败')
          cleanup()
        }

        await audio.play()
      } catch (err) {
        setState('error')
        setError(err instanceof Error ? err.message : '合成失败')
        cleanup()
      }
    },
    // stop/cleanup 引用 ref 且为闭包内调用，避免重渲染
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [userId]
  )

  /**
   * 流式 TTS：边生成边播放
   * 使用 MediaSource API 接收音频分片，实现秒开体验
   */
  const speakStream = useCallback(
    (text: string, spkId?: string) => {
      stop()
      setState('loading')
      setError(null)
      setCurrentText(text)

      const chunks: { wav: Uint8Array }[] = []
      pendingChunksRef.current = chunks

      // 解析 base64 分片，累积成完整 wav
      const base64ToUint8 = (b64: string) => {
        const binary = atob(b64)
        const bytes = new Uint8Array(binary.length)
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
        return bytes
      }

      const onChunk = (b64: string) => {
        try {
          const bytes = base64ToUint8(b64)
          chunks.push({ wav: bytes })

          // 首个 chunk 初始化 MediaSource 并立即播放
          if (!mediaSourceRef.current && chunks.length === 1) {
            initStreamPlayback(chunks)
          } else if (mediaSourceRef.current && sourceBufferRef.current) {
            appendChunkToBuffer(bytes)
          }
        } catch (e) {
          console.error('Stream chunk decode error:', e)
        }
      }

      const onEvent = (e: {
        type: 'start' | 'done' | 'error'
        message?: string
        totalBytes?: number
      }) => {
        if (e.type === 'error') {
          setState('error')
          setError(e.message || '流式合成失败')
          cleanup()
        } else if (e.type === 'done') {
          // 所有 chunk 已接收，结束 SourceBuffer
          if (sourceBufferRef.current) {
            try {
              ;(sourceBufferRef.current as unknown as { endOfStream: () => void }).endOfStream()
            } catch {
              // ignore
            }
          }
        }
      }

      const cancel = tts.speakStream({ text, spkId }, onChunk, onEvent, userId)
      streamCancelRef.current = cancel
    },
    // 内部辅助函数引用 ref，避免重渲染
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [userId]
  )

  /**
   * 初始化流式播放：创建 MediaSource + Audio + SourceBuffer
   */
  const initStreamPlayback = (chunks: { wav: Uint8Array }[]) => {
    const audio = new Audio()
    audioRef.current = audio

    // 准备完整 blob（作为后备）
    const totalLength = chunks.reduce((s, c) => s + c.wav.length, 0)
    const merged = new Uint8Array(totalLength)
    let offset = 0
    for (const c of chunks) {
      merged.set(c.wav, offset)
      offset += c.wav.length
    }

    // MediaSource 方式
    if ('MediaSource' in window) {
      try {
        const mediaSource = new MediaSource()
        mediaSourceRef.current = mediaSource
        audio.src = URL.createObjectURL(mediaSource)
        objectUrlRef.current = audio.src

        mediaSource.addEventListener('sourceopen', () => {
          // CosyVoice 输出 wav (PCM 16bit, 22050Hz)
          // 浏览器不一定支持 wav 的 MediaSource，改用 AudioContext 方案
          try {
            // 先尝试直接 append wav
            const sb = mediaSource.addSourceBuffer('audio/wav')
            sourceBufferRef.current = sb

            // 追加已缓存的 chunks
            for (const c of chunks) {
              appendChunkToBuffer(c.wav)
            }
          } catch {
            // 如果浏览器不支持 wav MSE，降级为 AudioContext PCM 播放
            setupAudioContextPlayback(chunks, mediaSource)
          }
        })
      } catch {
        // fallback
        setupDirectAudioPlayback(merged)
      }
    } else {
      setupDirectAudioPlayback(merged)
    }

    audio.onplay = () => setState('playing')
    audio.onended = () => {
      setState('idle')
      cleanup()
    }
    audio.onerror = () => {
      setState('error')
      setError('音频播放失败')
      cleanup()
    }

    audio.play().catch(() => {
      // 某些浏览器需要用户手势，静默失败
    })
  }

  /**
   * 将新到达的音频分片追加到 SourceBuffer
   */
  const appendChunkToBuffer = (bytes: Uint8Array) => {
    const sb = sourceBufferRef.current
    if (!sb || sb.updating) {
      // 排队等待
      setTimeout(() => appendChunkToBuffer(bytes), 10)
      return
    }
    try {
      sb.appendBuffer(bytes)
    } catch (e) {
      console.warn('appendBuffer failed, falling back:', e)
      // 如果 MSE 方式失败，切换为 AudioContext 方案
    }
  }

  /**
   * AudioContext PCM 播放备选方案
   */
  const setupAudioContextPlayback = (chunks: { wav: Uint8Array }[], mediaSource: MediaSource) => {
    // MediaSource 不可用时清理
    try {
      mediaSource.endOfStream?.()
    } catch {
      // noop
    }

    // 累积所有 wav 数据，解析 PCM
    const totalLen = chunks.reduce((s, c) => s + c.wav.length, 0)
    const merged = new Uint8Array(totalLen)
    let off = 0
    for (const c of chunks) {
      merged.set(c.wav, off)
      off += c.wav.length
    }

    // WAV 头部是 44 字节，跳过得到 PCM 数据
    const pcm = merged.subarray(44)
    const sampleRate = 22050
    const numSamples = pcm.length / 2

    const audioCtx = new (
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    )()
    audioCtxRef.current = audioCtx

    const buffer = audioCtx.createBuffer(1, numSamples, sampleRate)
    const view = new DataView(pcm.buffer, pcm.byteOffset, pcm.byteLength)
    const channelData = buffer.getChannelData(0)
    for (let i = 0; i < numSamples; i++) {
      channelData[i] = view.getInt16(i * 2, true) / 32768
    }

    const source = audioCtx.createBufferSource()
    source.buffer = buffer
    source.connect(audioCtx.destination)
    source.start(0)
    setState('playing')

    source.onended = () => {
      setState('idle')
      audioCtx.close().catch(() => {})
    }
  }

  /**
   * 直接 Blob 播放备选方案
   */
  const setupDirectAudioPlayback = (wavData: Uint8Array) => {
    const blob = new Blob([wavData], { type: 'audio/wav' })
    const url = URL.createObjectURL(blob)
    objectUrlRef.current = url

    const audio = new Audio(url)
    audioRef.current = audio

    audio.onplay = () => setState('playing')
    audio.onended = () => {
      setState('idle')
      cleanup()
    }
    audio.onerror = () => {
      setState('error')
      setError('音频播放失败')
    }
    audio.play().catch(() => {})
  }

  const stop = useCallback(() => {
    streamCancelRef.current?.()
    streamCancelRef.current = null

    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current.currentTime = 0
      audioRef.current = null
    }

    if (sourceBufferRef.current) {
      try {
        sourceBufferRef.current.abort()
      } catch {
        // noop
      }
      sourceBufferRef.current = null
    }

    if (mediaSourceRef.current) {
      try {
        mediaSourceRef.current.endOfStream()
      } catch {
        // noop
      }
      mediaSourceRef.current = null
    }

    if (audioCtxRef.current) {
      try {
        audioCtxRef.current.close()
      } catch {
        // noop
      }
      audioCtxRef.current = null
    }

    cleanup()
    setState('idle')
    // cleanup 在下方定义且仅引用 ref，无需作为依赖
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const cleanup = useCallback(() => {
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = null
    }
    pendingChunksRef.current = []
  }, [])

  const isCurrentlyPlaying = useCallback(
    (text: string) => {
      return state === 'playing' && currentText === text
    },
    [state, currentText]
  )

  return {
    state,
    error,
    speak,
    speakStream,
    stop,
    isCurrentlyPlaying,
  }
}
