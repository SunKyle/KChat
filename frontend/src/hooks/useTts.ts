import { useState, useRef, useCallback, useEffect } from 'react'
import { tts } from '../api/tts'

export type TtsState = 'idle' | 'loading' | 'playing' | 'error'

export function useTts(userId?: string) {
  const [state, setState] = useState<TtsState>('idle')
  const [error, setError] = useState<string | null>(null)
  const [currentText, setCurrentText] = useState<string | null>(null)
  const audioRef = useRef<HTMLAudioElement | null>(null)
  const objectUrlRef = useRef<string | null>(null)

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stop()
    }
  }, [])

  const speak = useCallback(async (text: string, spkId?: string) => {
    // Stop current playback
    stop()

    setState('loading')
    setError(null)
    setCurrentText(text)

    try {
      const blob = await tts.speak({ text, spkId }, userId)
      
      // Create object URL for the blob
      const url = URL.createObjectURL(blob)
      objectUrlRef.current = url

      // Create and play audio
      const audio = new Audio(url)
      audioRef.current = audio

      audio.onplay = () => {
        setState('playing')
      }

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
      const message = err instanceof Error ? err.message : '合成失败'
      setError(message)
      cleanup()
    }
  }, [userId])

  const stop = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.pause()
      audioRef.current.currentTime = 0
      audioRef.current = null
    }
    cleanup()
    setState('idle')
  }, [])

  const cleanup = useCallback(() => {
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current)
      objectUrlRef.current = null
    }
  }, [])

  const isCurrentlyPlaying = useCallback((text: string) => {
    return state === 'playing' && currentText === text
  }, [state, currentText])

  return {
    state,
    error,
    speak,
    stop,
    isCurrentlyPlaying,
  }
}
