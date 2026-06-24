import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import {
  Send,
  Square,
  Image,
  Trash2,
  Paperclip,
  Code,
  Loader2,
  Globe,
  Sparkles,
  X,
  Check,
  RefreshCw,
} from 'lucide-react'
import { useChat } from '../../../context/ChatContext'
import {
  images,
  optimization,
  type OptimizationResponse,
  type OptimizationRequest,
} from '../../../api'
import { useWebSearch } from '../../../hooks/useWebSearch'
import { useToast } from '../../../hooks/useToast'

export function InputArea() {
  const [input, setInput] = useState('')
  const [uploadingImages, setUploadingImages] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [showStatusBar, setShowStatusBar] = useState(false)
  const [isExiting, setIsExiting] = useState(false)
  const { sendMessage, streamingState, stopStreaming, currentModel } = useChat()
  const { webSearchEnabled, toggleWebSearch } = useWebSearch()
  const { showToast } = useToast()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)
  const exitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 内容优化相关状态
  const [isOptimizing, setIsOptimizing] = useState(false)
  const [optimizedContent, setOptimizedContent] = useState<string | null>(null)
  const [showOptimizationResult, setShowOptimizationResult] = useState(false)
  const [optimizationError, setOptimizationError] = useState<string | null>(null)
  const [optimizationDetails, setOptimizationDetails] = useState<
    { type: string; description: string }[]
  >([])

  const charCount = input.length
  const maxChars = 2000
  const maxImages = 5

  // 状态条滑出/收起动画
  useEffect(() => {
    if (streamingState.isStreaming) {
      if (exitTimerRef.current) {
        clearTimeout(exitTimerRef.current)
        exitTimerRef.current = null
      }
      setIsExiting(false)
      requestAnimationFrame(() => {
        setShowStatusBar(true)
      })
    } else if (showStatusBar) {
      setIsExiting(true)
      exitTimerRef.current = setTimeout(() => {
        setShowStatusBar(false)
        setIsExiting(false)
      }, 350)
    }
    return () => {
      if (exitTimerRef.current) clearTimeout(exitTimerRef.current)
    }
  }, [streamingState.isStreaming])

  // 流式计时器
  useEffect(() => {
    let intervalId: ReturnType<typeof setInterval> | null = null
    if (streamingState.isStreaming) {
      const startTime = Date.now()
      setElapsedSeconds(0)
      intervalId = setInterval(() => {
        setElapsedSeconds(parseFloat(((Date.now() - startTime) / 1000).toFixed(1)))
      }, 100)
    } else {
      setElapsedSeconds(0)
    }
    return () => {
      if (intervalId) clearInterval(intervalId)
    }
  }, [streamingState.isStreaming])

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px'
    }
  }, [input])

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (streamingState.isStreaming) {
        stopStreaming()
      } else {
        handleSend()
      }
    }
  }

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files || uploadingImages.length >= maxImages) return

    setUploading(true)
    const newImages: string[] = [...uploadingImages]

    for (const file of Array.from(files)) {
      if (newImages.length >= maxImages) break
      if (!file.type.startsWith('image/')) continue

      try {
        const result = await images.upload(file)
        newImages.push(result.url)
        setUploadingImages([...newImages])
      } catch (error) {
        console.error('Failed to upload image:', error)
      }
    }

    setUploading(false)
    e.target.value = ''
  }

  const handleRemoveImage = (index: number) => {
    const newImages = uploadingImages.filter((_, i) => i !== index)
    setUploadingImages(newImages)
  }

  const handleSend = async () => {
    if (
      (!input.trim() && uploadingImages.length === 0) ||
      streamingState.isStreaming ||
      charCount > maxChars
    )
      return

    const currentInput = input
    const currentImages = uploadingImages
    setInput('')
    setUploadingImages([])
    setShowOptimizationResult(false)
    setOptimizedContent(null)

    sendMessage(currentInput, currentImages, webSearchEnabled)
  }

  // 内容优化处理
  const handleOptimize = async () => {
    if (!input.trim() || isOptimizing || streamingState.isStreaming) return

    setIsOptimizing(true)
    setOptimizationError(null)
    setOptimizedContent(null)

    try {
      const requestData: OptimizationRequest = {
        content: input,
        modelId: currentModel,
        modelType: detectModelType(currentModel),
      }

      const response: OptimizationResponse = await optimization.optimize(requestData)

      if (response.success) {
        setOptimizedContent(response.optimizedContent)
        setOptimizationDetails(response.optimizations || [])
        setShowOptimizationResult(true)
      } else {
        if (response.error === 'RATE_LIMIT_EXCEEDED') {
          const retryTime = response.retryAfterSeconds || 60
          showToast(`请求过于频繁，请 ${retryTime} 秒后重试`, 'error')
        } else {
          showToast(response.message || '优化失败', 'error')
        }
      }
    } catch (error) {
      console.error('Optimization error:', error)
      showToast('优化请求失败，请稍后重试', 'error')
    } finally {
      setIsOptimizing(false)
    }
  }

  // 根据模型ID检测模型类型
  const detectModelType = (modelId: string): string | undefined => {
    if (!modelId) return undefined

    // Ollama 模型通常是简单名称，如 llama3, mistral, qwen
    const ollamaPatterns = ['llama', 'mistral', 'qwen', 'phi', 'gemma', 'codellama']
    for (const pattern of ollamaPatterns) {
      if (modelId.toLowerCase().includes(pattern)) {
        return 'OLLAMA'
      }
    }

    // OpenAI 模型
    if (modelId.startsWith('gpt-')) {
      return 'OPENAI'
    }

    // 默认使用 OPENAI_COMPATIBLE
    return 'OPENAI_COMPATIBLE'
  }

  // 应用优化结果
  const handleApplyOptimization = () => {
    if (optimizedContent) {
      setInput(optimizedContent)
      setShowOptimizationResult(false)
      setOptimizedContent(null)
    }
  }

  // 取消优化结果
  const handleCancelOptimization = () => {
    setShowOptimizationResult(false)
    setOptimizedContent(null)
    setOptimizationDetails([])
  }

  // 重新优化
  const handleReoptimize = () => {
    handleOptimize()
  }

  const hasContent = input.trim() || uploadingImages.length > 0

  return (
    <div className='p-4 pb-[max(1.5rem,env(safe-area-inset-bottom))]'>
      <div className='max-w-3xl mx-auto relative group'>
        {/* 已上传图片预览 */}
        {uploadingImages.length > 0 && (
          <div className='flex flex-wrap gap-2 mb-4 mx-4 lg:mx-6'>
            {uploadingImages.map((imageUrl, index) => (
              <div
                key={index}
                className='relative w-16 h-16 rounded-xl overflow-hidden border border-gray-200 hover:border-[var(--accent-primary)]/50 transition-all duration-200 shadow-sm hover:shadow-md'
              >
                <img
                  src={imageUrl}
                  alt={`Uploaded ${index + 1}`}
                  loading='lazy'
                  className='w-full h-full object-cover'
                />
                <button
                  onClick={() => handleRemoveImage(index)}
                  className='absolute top-1 right-1 w-11 h-11 backdrop-blur-md bg-[var(--bg-glass)] rounded-full flex items-center justify-center hover:bg-red-500 hover:text-white transition-colors shadow-sm'
                  aria-label='移除图片'
                >
                  <Trash2 className='w-[14px] h-[14px]' />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* 优化结果展示区域 */}
        {showOptimizationResult && optimizedContent && (
          <div className='mx-4 mb-4 lg:mx-6 bg-[var(--bg-card)] border border-[var(--border-color)] rounded-xl shadow-sm overflow-hidden'>
            <div className='px-4 py-3 border-b border-[var(--border-color)] flex items-center justify-between'>
              <div className='flex items-center gap-2'>
                <Sparkles className='w-4 h-4 text-amber-500' />
                <span className='text-sm font-medium theme-text-primary'>内容优化结果</span>
              </div>
              <button
                onClick={handleCancelOptimization}
                className='p-1 hover:bg-[var(--bg-hover)] rounded-md transition-colors'
                aria-label='关闭优化结果'
              >
                <X className='w-4 h-4 theme-text-muted' />
              </button>
            </div>

            <div className='p-4'>
              {/* 优化详情标签 */}
              {optimizationDetails.length > 0 && (
                <div className='flex flex-wrap gap-2 mb-3'>
                  {optimizationDetails.map((detail, index) => (
                    <span
                      key={index}
                      className='px-2 py-1 text-xs rounded-full bg-amber-50 text-amber-700'
                    >
                      {detail.description}
                    </span>
                  ))}
                </div>
              )}

              {/* 优化后的内容预览 */}
              <div className='bg-[var(--bg-input)] rounded-lg p-3 mb-4'>
                <p className='text-sm theme-text-primary whitespace-pre-wrap break-words'>
                  {optimizedContent}
                </p>
              </div>

              {/* 操作按钮 */}
              <div className='flex items-center gap-2'>
                <button
                  onClick={handleApplyOptimization}
                  className='flex items-center gap-2 px-4 py-2 bg-[var(--accent-primary)] text-white rounded-lg hover:bg-[var(--accent-primary)]/90 transition-colors text-sm font-medium'
                >
                  <Check className='w-4 h-4' />
                  应用优化
                </button>
                <button
                  onClick={handleReoptimize}
                  disabled={isOptimizing}
                  className='flex items-center gap-2 px-4 py-2 bg-[var(--bg-hover)] text-[var(--text-primary)] rounded-lg hover:bg-[var(--bg-hover)]/80 transition-colors text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed'
                >
                  {isOptimizing ? (
                    <Loader2 className='w-4 h-4 animate-spin' />
                  ) : (
                    <RefreshCw className='w-4 h-4' />
                  )}
                  重新优化
                </button>
                <button
                  onClick={handleCancelOptimization}
                  className='px-4 py-2 text-[var(--text-muted)] hover:text-[var(--text-primary)] rounded-lg hover:bg-[var(--bg-hover)] transition-colors text-sm font-medium'
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 状态条 — 在输入框背后，从顶部滑出 */}
        <div className='relative z-0 mx-4 lg:mx-6 mb-0'>
          {showStatusBar &&
            (() => {
              const isOutputting = streamingState.currentContent.length > 0
              return (
                <div
                  role='status'
                  aria-live='polite'
                  className={`flex items-center justify-between px-4 lg:px-6 pt-2 pb-5 rounded-t-xl shadow-[0_1px_3px_rgba(0,0,0,0.06),0_4px_12px_rgba(0,0,0,0.04)] ${
                    isExiting ? 'status-bar-exit' : 'status-bar-enter'
                  } status-bar-color-transition ${isOutputting ? 'status-bar-outputting' : 'status-bar-thinking'}`}
                >
                  <div className='flex items-center gap-2'>
                    <div
                      className={`w-2 h-2 rounded-full transition-all duration-500 ${
                        isOutputting ? 'bg-sky-500' : 'bg-amber-500'
                      } ${isOutputting ? 'animate-pulse-slow' : 'animate-pulse'}`}
                    />
                    <span
                      className={`text-xs font-semibold transition-all duration-500 ${
                        isOutputting ? 'text-sky-800' : 'text-amber-800'
                      }`}
                    >
                      {isOutputting ? '正在输出...' : '正在思考...'}
                    </span>
                  </div>
                  <span
                    className={`text-xs font-secondary tabular-nums transition-all duration-500 ${
                      isOutputting ? 'text-sky-700/80' : 'text-amber-700/80'
                    }`}
                  >
                    {elapsedSeconds}s
                  </span>
                </div>
              )
            })()}
        </div>

        {/* 输入框容器 */}
        {(() => {
          const isOutputting =
            streamingState.isStreaming && streamingState.currentContent.length > 0
          const isThinking = streamingState.isStreaming && !isOutputting
          return (
            <div
              className={`relative z-10 -mt-3 flex flex-col card-float-solid bg-transparent mx-4 mb-4 lg:mx-6 lg:mb-6 overflow-hidden transition-all duration-500 ease-out ${
                isThinking
                  ? 'input-glow-thinking'
                  : isOutputting
                    ? 'input-glow-outputting'
                    : 'input-glow-focus'
              }`}
            >
              {/* 上半部分：文本输入区域 */}
              <div className='px-4 py-1.5'>
                <textarea
                  ref={textareaRef}
                  value={input}
                  onChange={(e) => {
                    setInput(e.target.value)
                    // 当输入变化时，隐藏优化结果
                    if (showOptimizationResult) {
                      setShowOptimizationResult(false)
                      setOptimizedContent(null)
                    }
                  }}
                  onKeyDown={handleKeyDown}
                  disabled={streamingState.isStreaming}
                  placeholder={streamingState.isStreaming ? '添加到队列' : '输入消息...'}
                  aria-label='输入消息'
                  className={`w-full resize-none bg-transparent px-0 py-1 theme-text-primary placeholder-theme-text-placeholder focus:outline-none min-h-[32px] max-h-[200px] overflow-y-auto font-input-text transition-opacity duration-200 ${
                    streamingState.isStreaming ? 'opacity-50' : ''
                  }`}
                />
              </div>

              {/* 下半部分：工具栏 */}
              <div className='flex items-center justify-between px-4 pt-0.5 pb-2'>
                {/* 左侧：功能按钮 */}
                <div className='flex items-center gap-0.5'>
                  {/* 上传文件按钮（包括图片） */}
                  <input
                    ref={generalFileInputRef}
                    type='file'
                    accept='image/*,.txt,.pdf,.doc,.docx'
                    multiple
                    onChange={handleFileUpload}
                    disabled={
                      uploading || streamingState.isStreaming || uploadingImages.length >= maxImages
                    }
                    className='hidden'
                  />
                  <div className='relative'>
                    <button
                      onClick={() => generalFileInputRef.current?.click()}
                      disabled={
                        uploading ||
                        streamingState.isStreaming ||
                        uploadingImages.length >= maxImages
                      }
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        uploading ||
                        streamingState.isStreaming ||
                        uploadingImages.length >= maxImages
                          ? 'opacity-40 cursor-not-allowed'
                          : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-sky-600 cursor-pointer'
                      }`}
                      aria-label='上传文件'
                    >
                      {uploading ? (
                        <Loader2 className='w-4 h-4 theme-text-muted animate-spin' />
                      ) : (
                        <Paperclip className='w-4 h-4 text-[var(--text-toolbar)]' />
                      )}
                    </button>
                    <span className='tooltip-content'>上传文件</span>
                  </div>

                  {/* 代码按钮 */}
                  <div className='relative'>
                    <button
                      className='peer flex items-center justify-center w-8 h-8 rounded-md hover:bg-[var(--bg-toolbar-hover)] hover:text-amber-600 text-[var(--text-toolbar)] transition-all duration-200 cursor-pointer'
                      aria-label='插入代码'
                    >
                      <Code className='w-4 h-4' />
                    </button>
                    <span className='tooltip-content'>插入代码</span>
                  </div>

                  {/* 生成图片按钮 */}
                  <div className='relative'>
                    <button
                      onClick={() => setInput('生成图片：')}
                      disabled={streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-emerald-600 text-[var(--text-toolbar)] cursor-pointer'
                      }`}
                      aria-label='生成图片'
                    >
                      <Image className='w-4 h-4' />
                    </button>
                    <span className='tooltip-content'>生成图片</span>
                  </div>

                  {/* 内容优化按钮 */}
                  <div className='relative'>
                    <button
                      onClick={handleOptimize}
                      disabled={!input.trim() || isOptimizing || streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        !input.trim() || isOptimizing || streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-amber-500 text-[var(--text-toolbar)] cursor-pointer'
                      }`}
                      aria-label='内容优化'
                    >
                      {isOptimizing ? (
                        <Loader2 className='w-4 h-4 theme-text-muted animate-spin' />
                      ) : (
                        <Sparkles className='w-4 h-4' />
                      )}
                    </button>
                    <span className='tooltip-content'>
                      {isOptimizing ? '优化中...' : '内容优化'}
                    </span>
                  </div>

                  {/* 联网搜索按钮 */}
                  <div className='relative'>
                    <button
                      onClick={toggleWebSearch}
                      disabled={streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 active:scale-90 ${
                        streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : webSearchEnabled
                            ? 'bg-sky-100 text-sky-600 hover:bg-sky-200 cursor-pointer'
                            : 'hover:bg-[var(--bg-toolbar-hover)] text-[var(--text-toolbar)] cursor-pointer'
                      }`}
                      aria-label={webSearchEnabled ? '关闭联网搜索' : '开启联网搜索'}
                      aria-pressed={webSearchEnabled}
                    >
                      <Globe className='w-4 h-4 transition-colors duration-200' />
                    </button>
                    <span className='tooltip-content'>
                      {webSearchEnabled ? '已开启联网搜索' : '开启联网搜索'}
                    </span>
                  </div>
                </div>

                {/* 右侧：提示文字和发送按钮 */}
                <div className='flex items-center gap-3'>
                  {/* 字符计数 — 三级颜色阈值 */}
                  {!streamingState.isStreaming && (
                    <span
                      aria-live='polite'
                      className={`text-xs tabular-nums transition-colors duration-200 ${
                        charCount > maxChars
                          ? 'text-red-500 font-weight-semibold'
                          : charCount >= maxChars * 0.9
                            ? 'text-amber-500 font-weight-medium'
                            : 'theme-text-muted'
                      }`}
                    >
                      {charCount}/{maxChars}
                    </span>
                  )}

                  {/* 键盘提示 */}
                  {!streamingState.isStreaming && (
                    <span className='text-xs theme-text-muted hidden sm:inline'>
                      Shift + Enter 换行
                    </span>
                  )}

                  {/* 发送/停止按钮 — 三态平滑过渡 */}
                  <div className='relative'>
                    <button
                      onClick={() => {
                        if (streamingState.isStreaming) {
                          stopStreaming()
                        } else {
                          handleSend()
                        }
                      }}
                      disabled={!hasContent && !streamingState.isStreaming}
                      className={`peer group/send relative flex items-center justify-center w-9 h-9 rounded-full transition-[background-color,box-shadow,transform,color] duration-500 ease-out ${
                        isThinking
                          ? 'bg-amber-500 text-white hover:bg-amber-600 hover:scale-105 shadow-md shadow-amber-500/30 cursor-pointer'
                          : isOutputting
                            ? 'bg-sky-500 text-white hover:bg-sky-600 hover:scale-105 shadow-md shadow-sky-500/30 cursor-pointer'
                            : uploading
                              ? 'bg-sky-500/80 text-white cursor-wait'
                              : hasContent && charCount <= maxChars
                                ? 'bg-gradient-to-br from-sky-500 to-sky-600 text-white shadow-lg shadow-sky-500/30 hover:shadow-xl hover:shadow-sky-500/40 hover:scale-105 cursor-pointer'
                                : 'bg-[var(--bg-hover)] text-[var(--text-muted)] cursor-not-allowed'
                      }`}
                      aria-label={
                        streamingState.isStreaming
                          ? '中断回答'
                          : uploading
                            ? '发送中...'
                            : '发送消息'
                      }
                    >
                      {/* 状态切换时的脉冲光环 — 仅在输出时显示青色呼吸光环 */}
                      <span
                        aria-hidden='true'
                        className={`pointer-events-none absolute inset-0 rounded-full transition-opacity duration-500 ${
                          isOutputting ? 'opacity-100 animate-pulse bg-sky-400/40' : 'opacity-0'
                        }`}
                        style={{ animationDuration: '1.8s' }}
                      />
                      {/* 状态切换时的旋转光环（仅在状态变化瞬间） */}
                      <span
                        aria-hidden='true'
                        key={
                          isThinking
                            ? 'thinking'
                            : isOutputting
                              ? 'outputting'
                              : uploading
                                ? 'uploading'
                                : hasContent && charCount <= maxChars
                                  ? 'ready'
                                  : 'idle'
                        }
                        className={`pointer-events-none absolute -inset-1 rounded-full transition-opacity duration-500 ${
                          isThinking
                            ? 'opacity-60 animate-spin bg-[conic-gradient(from_0deg,transparent_0deg,rgba(245,158,11,0.5)_120deg,transparent_240deg)]'
                            : isOutputting
                              ? 'opacity-60 animate-spin bg-[conic-gradient(from_0deg,transparent_0deg,rgba(14,165,233,0.5)_120deg,transparent_240deg)]'
                              : 'opacity-0'
                        }`}
                        style={{ animationDuration: '3s' }}
                      />
                      {/* 图标 — 透明度+缩放过渡以柔和切换 */}
                      <span className='relative flex items-center justify-center w-full h-full'>
                        {streamingState.isStreaming ? (
                          <Square
                            className='w-2.5 h-2.5 transition-all duration-300 ease-out'
                            fill='currentColor'
                          />
                        ) : uploading ? (
                          <div className='w-2.5 h-2.5 border-2 border-white/30 border-t-white rounded-full animate-spin transition-opacity duration-300' />
                        ) : (
                          <Send className='w-3 h-3 transition-transform duration-300 group-hover/send:translate-x-0.5 group-hover/send:-translate-y-0.5' />
                        )}
                      </span>
                    </button>
                    <span className='tooltip-content'>
                      {streamingState.isStreaming ? '中断回答' : '发送消息'}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )
        })()}
        {uploading && (
          <div className='mt-3 text-xs theme-text-muted flex items-center gap-2'>
            <Loader2 className='w-3 h-3 animate-spin' />
            正在上传图片...
          </div>
        )}
      </div>
    </div>
  )
}
