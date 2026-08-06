export interface Speaker {
  spkId: string
  name: string
  promptText?: string
  source: string
  ownerUserId?: string
  createdAt?: string
}

export interface TtsHealth {
  status: string
  modelDir?: string
  modelType?: string
  sampleRate: number
  device: string
  cudaAvailable: boolean
  speakers: number
  queueSize: number
  concurrency: number
}

export interface SpeakRequest {
  text: string
  spkId?: string
  speed?: number
}

export interface PreviewRequest {
  text: string
  promptText?: string
  promptWavBase64?: string
  speed?: number
}
