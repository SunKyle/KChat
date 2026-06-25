import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
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
  Undo2,
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
  const { success: toastSuccess, error: toastError } = useToast()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)
  const exitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // 内容优化相关状态
  const [isOptimizing, setIsOptimizing] = useState(false)
  const [canUndoOptimize, setCanUndoOptimize] = useState(false)
  const [originalContent, setOriginalContent] = useState('')
  const [showOptimizationResult, setShowOptimizationResult] = useState(false)
  const [optimizedContent, setOptimizedContent] = useState<string | null>(null)
  const optimizationControllerRef = useRef<AbortController | null>(null)

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

  // 计时器（支持流式响应和优化操作）
  useEffect(() => {
    let intervalId: ReturnType<typeof setInterval> | null = null
    const isRunning = streamingState.isStreaming || isOptimizing

    if (isRunning) {
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
  }, [streamingState.isStreaming, isOptimizing])

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
    setCanUndoOptimize(false)
    setOriginalContent(input)

    // 创建 AbortController 用于取消请求
    const controller = new AbortController()
    optimizationControllerRef.current = controller

    try {
      const requestData: OptimizationRequest = {
        content: input,
        modelId: currentModel,
        modelType: detectModelType(currentModel),
      }

      const response: OptimizationResponse = await optimization.optimize(requestData)

      // 检查请求是否被取消
      if (controller.signal.aborted) {
        console.log('优化已被取消')
        return
      }

      if (response.success) {
        // 直接将优化结果覆盖到输入框
        setInput(response.optimizedContent)
        setCanUndoOptimize(true)
        toastSuccess('优化完成')
      } else {
        if (response.error === 'RATE_LIMIT_EXCEEDED') {
          const retryTime = response.retryAfterSeconds || 60
          toastError(`请求过于频繁，请 ${retryTime} 秒后重试`)
        } else {
          toastError(response.message || '优化失败')
        }
      }
    } catch (error) {
      // 如果是取消错误，不显示提示
      if (controller.signal.aborted) {
        console.log('优化已被取消')
        return
      }
      console.error('Optimization error:', error)
      toastError('优化请求失败，请稍后重试')
    } finally {
      setIsOptimizing(false)
      optimizationControllerRef.current = null
    }
  }

  // 撤回优化
  const handleUndoOptimize = () => {
    setInput(originalContent)
    setCanUndoOptimize(false)
    toastSuccess('已撤回优化')
  }

  // 停止优化
  const handleStopOptimization = () => {
    if (optimizationControllerRef.current) {
      optimizationControllerRef.current.abort()
      optimizationControllerRef.current = null
    }
    setIsOptimizing(false)
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

        {/* 状态条 — 在输入框背后，从顶部滑出 */}
        <div className='relative z-0 mx-4 lg:mx-6 mb-0'>
          {(showStatusBar || isOptimizing) &&
            (() => {
              const isOutputting = streamingState.currentContent.length > 0
              const isThinking = streamingState.isStreaming && !isOutputting
              const isOptimizingNow = isOptimizing

              return (
                <div
                  role='status'
                  aria-live='polite'
                  className={`flex items-center justify-between px-4 lg:px-6 pt-2 pb-5 rounded-t-xl shadow-[0_1px_3px_rgba(0,0,0,0.06),0_4px_12px_rgba(0,0,0,0.04)] ${
                    isExiting ? 'status-bar-exit' : 'status-bar-enter'
                  } status-bar-color-transition ${
                    isOptimizingNow
                      ? 'status-bar-optimizing'
                      : isOutputting
                        ? 'status-bar-outputting'
                        : 'status-bar-thinking'
                  }`}
                >
                  <div className='flex items-center gap-2'>
                    <div
                      className={`w-2 h-2 rounded-full transition-all duration-500 ${
                        isOptimizingNow
                          ? 'bg-emerald-500'
                          : isOutputting
                            ? 'bg-sky-500'
                            : 'bg-amber-500'
                      } ${isOutputting ? 'animate-pulse-slow' : 'animate-pulse'}`}
                    />
                    <span
                      className={`text-xs font-semibold transition-all duration-500 ${
                        isOptimizingNow
                          ? 'text-emerald-800'
                          : isOutputting
                            ? 'text-sky-800'
                            : 'text-amber-800'
                      }`}
                    >
                      {isOptimizingNow
                        ? '正在优化...'
                        : isOutputting
                          ? '正在输出...'
                          : '正在思考...'}
                    </span>
                  </div>
                  <span
                    className={`text-xs font-secondary tabular-nums transition-all duration-500 ${
                      isOptimizingNow
                        ? 'text-emerald-700/80'
                        : isOutputting
                          ? 'text-sky-700/80'
                          : 'text-amber-700/80'
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
          const isOptimizingNow = isOptimizing
          return (
            <div
              className={`relative z-10 -mt-3 flex flex-col card-float-solid bg-transparent mx-4 mb-4 lg:mx-6 lg:mb-6 overflow-hidden transition-all duration-500 ease-out ${
                isThinking
                  ? 'input-glow-thinking'
                  : isOutputting
                    ? 'input-glow-outputting'
                    : isOptimizingNow
                      ? 'input-glow-optimizing'
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
                    // 当用户手动输入时，重置撤回状态
                    setCanUndoOptimize(false)
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
                      onClick={() => {
                        if (isOptimizing) {
                          handleStopOptimization()
                        } else if (canUndoOptimize) {
                          handleUndoOptimize()
                        } else {
                          handleOptimize()
                        }
                      }}
                      disabled={!input.trim() || streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        !input.trim() || streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : isOptimizing
                            ? 'bg-emerald-100 text-emerald-600 hover:bg-emerald-200 cursor-pointer'
                            : canUndoOptimize
                              ? 'bg-amber-100 text-amber-600 hover:bg-amber-200 cursor-pointer'
                              : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-amber-500 text-[var(--text-toolbar)] cursor-pointer'
                      }`}
                      aria-label={
                        isOptimizing ? '停止优化' : canUndoOptimize ? '撤回优化' : '内容优化'
                      }
                    >
                      {isOptimizing ? (
                        <Square className='w-4 h-4 fill-current' />
                      ) : canUndoOptimize ? (
                        <Undo2 className='w-4 h-4' />
                      ) : (
                        <Sparkles className='w-4 h-4' />
                      )}
                    </button>
                    <span className='tooltip-content'>
                      {isOptimizing ? '停止优化' : canUndoOptimize ? '撤回优化' : '内容优化'}
                    </span>
                  </div>

                  {/* 联网搜索按钮 */}
                  <div className='relative'>
                    <button
                      onClick={toggleWebSearch}
                      disabled={streamingState.isStreaming}
                      className={`peer flex items-center gap-1.5 h-8 cursor-pointer rounded-full border px-1.5 py-1 transition-all ${
                        streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed border-transparent'
                          : webSearchEnabled
                            ? 'border-sky-400 bg-sky-500/15 text-sky-500 hover:bg-sky-500/25'
                            : 'border-transparent hover:bg-[var(--bg-toolbar-hover)] text-[var(--text-toolbar)]'
                      }`}
                      aria-label={webSearchEnabled ? '关闭联网搜索' : '开启联网搜索'}
                      aria-pressed={webSearchEnabled}
                    >
                      <div className='flex h-4 w-4 shrink-0 items-center justify-center'>
                        <motion.div
                          animate={{ rotate: webSearchEnabled ? 180 : 0, scale: webSearchEnabled ? 1.1 : 1 }}
                          transition={{ type: 'spring', stiffness: 260, damping: 25 }}
                          whileHover={{ rotate: webSearchEnabled ? 180 : 15, scale: 1.1, transition: { type: 'spring', stiffness: 300, damping: 10 } }}
                        >
                          <Globe className='h-4 w-4' />
                        </motion.div>
                      </div>
                      <AnimatePresence>
                        {webSearchEnabled && (
                          <motion.span
                            initial={{ width: 0, opacity: 0 }}
                            animate={{ width: 'auto', opacity: 1 }}
                            exit={{ width: 0, opacity: 0 }}
                            transition={{ duration: 0.2 }}
                            className='shrink-0 overflow-hidden whitespace-nowrap text-xs font-semibold'
                          >
                            搜索
                          </motion.span>
                        )}
                      </AnimatePresence>
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
                            ? 'text-amber-500 font-weight-semibold'
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

                  {/* 发送/停止按钮 — 四态平滑过渡 */}
                  <div className='relative'>
                    <button
                      onClick={() => {
                        if (streamingState.isStreaming) {
                          stopStreaming()
                        } else {
                          handleSend()
                        }
                      }}
                      disabled={(!hasContent && !streamingState.isStreaming) || isOptimizing}
                      className={`peer group/send relative flex items-center justify-center w-9 h-9 rounded-full transition-[background-color,box-shadow,transform,color] duration-500 ease-out ${
                        isThinking
                          ? 'bg-amber-500 text-white hover:bg-amber-600 hover:scale-105 shadow-md shadow-amber-500/30 cursor-pointer'
                          : isOutputting
                            ? 'bg-sky-500 text-white hover:bg-sky-600 hover:scale-105 shadow-md shadow-sky-500/30 cursor-pointer'
                            : isOptimizing
                              ? 'bg-emerald-500 text-white hover:bg-emerald-600 hover:scale-105 shadow-md shadow-emerald-500/30 cursor-pointer'
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
                      {/* 状态切换时的脉冲光环 */}
                      <span
                        aria-hidden='true'
                        className={`pointer-events-none absolute inset-0 rounded-full transition-opacity duration-500 ${
                          isOutputting
                            ? 'opacity-100 animate-pulse bg-sky-400/40'
                            : isOptimizing
                              ? 'opacity-100 animate-pulse bg-emerald-400/40'
                              : 'opacity-0'
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
                              : isOptimizing
                                ? 'optimizing'
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
                              : isOptimizing
                                ? 'opacity-60 animate-spin bg-[conic-gradient(from_0deg,transparent_0deg,rgba(34,197,94,0.5)_120deg,transparent_240deg)]'
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
