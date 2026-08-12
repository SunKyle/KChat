import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Send,
  Square,
  Image,
  Paperclip,
  Loader2,
  Globe,
  Cpu,
  Sparkles,
  X,
  Undo2,
  FileText,
} from 'lucide-react'
import { useChat } from '../../../context/ChatContext'
import { isImageModel } from '../../../utils/model'
import {
  images,
  files,
  optimization,
  type OptimizationResponse,
  type OptimizationRequest,
  type UploadedFile,
} from '../../../api'
import { useWebSearch } from '../../../hooks/useWebSearch'
import { useToast } from '../../../hooks/useToast'
import { toAccessibleImageUrl } from '../../../utils/imageUrl'

export function InputArea() {
  const [input, setInput] = useState('')
  const [uploadingImages, setUploadingImages] = useState<string[]>([])
  const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([])
  const [uploading, setUploading] = useState(false)
  const [agentModeEnabled, setAgentModeEnabled] = useState(true)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [showStatusBar, setShowStatusBar] = useState(false)
  const [isExiting, setIsExiting] = useState(false)
  const { sendMessage, streamingState, stopStreaming, currentModel } = useChat()
  const { webSearchEnabled, toggleWebSearch } = useWebSearch()
  const { success: toastSuccess, error: toastError } = useToast()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)
  const exitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // 输入法组合状态标记，用于避免 IME 输入过程中 Enter 被误判为发送
  const isComposingRef = useRef(false)

  // 内容优化相关状态
  const [isOptimizing, setIsOptimizing] = useState(false)
  const [canUndoOptimize, setCanUndoOptimize] = useState(false)
  const [originalContent, setOriginalContent] = useState('')
  const optimizationControllerRef = useRef<AbortController | null>(null)

  const charCount = input.length
  const maxChars = 2000
  const maxImages = 5

  const isImg2ImgMode = isImageModel(currentModel) && uploadingImages.length > 0

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
    // showStatusBar 作为条件触发器，加入会导致循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
    // 输入法组合状态（如中文输入法输入英文过程中）下，
    // Enter 键应交由输入法处理（确认候选词），不触发发送。
    // 同时检查 nativeEvent.isComposing 以兼容不同浏览器/输入法。
    if (e.nativeEvent.isComposing || isComposingRef.current) return

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
    const selectedFiles = e.target.files
    if (!selectedFiles) return

    setUploading(true)
    const newImages: string[] = [...uploadingImages]
    const newFiles: UploadedFile[] = [...uploadedFiles]

    for (const file of Array.from(selectedFiles)) {
      if (file.type.startsWith('image/')) {
        if (newImages.length >= maxImages) break
        try {
          const result = await images.upload(file)
          newImages.push(result.url)
          setUploadingImages([...newImages])
        } catch (error) {
          console.error('Failed to upload image:', error)
          toastError('图片上传失败')
        }
      } else {
        try {
          const result = await files.upload(file)
          newFiles.push(result)
          setUploadedFiles([...newFiles])
        } catch (error) {
          console.error('Failed to upload file:', error)
          toastError('文件上传失败')
        }
      }
    }

    setUploading(false)
    e.target.value = ''
  }

  const handleRemoveImage = (index: number) => {
    const newImages = uploadingImages.filter((_, i) => i !== index)
    setUploadingImages(newImages)
  }

  const handleRemoveFile = (index: number) => {
    const newFiles = uploadedFiles.filter((_, i) => i !== index)
    setUploadedFiles(newFiles)
  }

  const handleSend = async () => {
    if (
      (!input.trim() && uploadingImages.length === 0 && uploadedFiles.length === 0) ||
      streamingState.isStreaming ||
      charCount > maxChars
    )
      return

    let currentInput = input
    const currentImages = uploadingImages
    const currentFiles = [...uploadedFiles]

    // 有文档附件时，在消息内容前注入文件标记，供 agent 识别并调用 parseFile
    if (currentFiles.length > 0) {
      const fileMarkers = currentFiles
        .map((f) => `[已上传文件: ${f.fileName} (fileId: ${f.fileId})]`)
        .join('\n')
      currentInput = fileMarkers + '\n' + currentInput
    }

    setInput('')
    setUploadingImages([])
    setUploadedFiles([])

    sendMessage(currentInput, currentImages, webSearchEnabled, agentModeEnabled)
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

  const hasContent = input.trim() || uploadingImages.length > 0 || uploadedFiles.length > 0

  return (
    <div className='p-4 pb-[max(1.5rem,env(safe-area-inset-bottom))]'>
      <div className='max-w-3xl mx-auto relative group'>
        {/* 已上传图片预览 */}
        {uploadingImages.length > 0 && (
          <div className='mb-4 mx-4 lg:mx-6'>
            {isImg2ImgMode && (
              <div className='flex items-center gap-1.5 px-2.5 py-1.5 mb-2 rounded-lg bg-[var(--brand-primary)]/8 border border-[var(--brand-primary)]/15 text-xs font-semibold text-[var(--brand-primary)]'>
                <Image className='w-3.5 h-3.5' />
                图生图模式
                <span className='font-normal opacity-60'>· 仅第一张图作为参考</span>
              </div>
            )}
            <div className='flex flex-wrap gap-2'>
              {uploadingImages.map((imageUrl, index) => (
                <div
                  key={index}
                  className='relative group/preview w-16 h-16 rounded-xl overflow-hidden border border-[var(--border-primary)] transition-colors duration-200'
                >
                  {index === 0 && uploadingImages.length > 1 && (
                    <span className='absolute top-1 left-1 z-10 w-4 h-4 rounded-full bg-[var(--brand-primary)]/80 text-xs font-semibold text-white flex items-center justify-center'>
                      1
                    </span>
                  )}
                  <img
                    src={toAccessibleImageUrl(imageUrl)}
                    alt={`Uploaded ${index + 1}`}
                    loading='lazy'
                    className='w-full h-full object-cover'
                  />
                  <div className='absolute inset-0 bg-black/0 group-hover/preview:bg-black/20 transition-colors duration-200' />
                  <button
                    onClick={() => handleRemoveImage(index)}
                    className='absolute top-1 right-1 w-5 h-5 bg-black/40 hover:bg-red-500 rounded-full flex items-center justify-center opacity-0 group-hover/preview:opacity-100 transition-all duration-200'
                    aria-label='移除图片'
                  >
                    <X className='w-3 h-3 text-white' />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 已上传文档文件预览 */}
        {uploadedFiles.length > 0 && (
          <div className='mb-4 mx-4 lg:mx-6'>
            <div className='flex flex-wrap gap-2'>
              {uploadedFiles.map((f, index) => (
                <div
                  key={index}
                  className='relative flex items-center gap-2 px-3 py-2 rounded-xl border border-[var(--border-primary)] bg-[var(--bg-toolbar-hover)] transition-colors duration-200'
                >
                  <FileText className='w-4 h-4 text-[var(--text-toolbar)] shrink-0' />
                  <span className='text-sm theme-text-primary max-w-[160px] truncate'>
                    {f.fileName}
                  </span>
                  <button
                    onClick={() => handleRemoveFile(index)}
                    className='w-5 h-5 hover:bg-red-500 rounded-full flex items-center justify-center transition-all duration-200 text-[var(--text-muted)] hover:text-white'
                    aria-label='移除文件'
                  >
                    <X className='w-3 h-3' />
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 状态条 — 在输入框背后，从顶部滑出 */}
        <div className='relative mx-4 lg:mx-6 mb-0'>
          {(showStatusBar || isOptimizing) &&
            (() => {
              const isOutputting =
                streamingState.isStreaming && streamingState.currentContent.length > 0
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
                          ? 'bg-[var(--accent-emerald)]'
                          : isOutputting
                            ? 'bg-[var(--accent-primary)]'
                            : 'bg-[var(--accent-amber)]'
                      } ${isOutputting ? 'animate-pulse-slow' : 'animate-pulse'}`}
                    />
                    <span
                      className={`text-xs font-semibold transition-all duration-500 ${
                        isOptimizingNow
                          ? 'text-[var(--accent-emerald)]'
                          : isOutputting
                            ? 'text-[var(--accent-foreground)]'
                            : 'text-[var(--accent-amber)]'
                      }`}
                    >
                      {isOptimizingNow
                        ? '正在优化...'
                        : isOutputting
                          ? '正在输出...'
                          : isThinking && isImageModel(currentModel)
                            ? '正在生成图片...'
                            : '正在思考...'}
                    </span>
                  </div>
                  <span
                    className={`text-xs font-secondary tabular-nums transition-all duration-500 ${
                      isOptimizingNow
                        ? 'text-[var(--accent-emerald)]/80'
                        : isOutputting
                          ? 'text-[var(--accent-foreground)]/80'
                          : 'text-[var(--accent-amber)]/80'
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
              className={`relative z-10 -mt-4 flex flex-col card-float-solid mx-4 mb-4 lg:mx-6 lg:mb-6 overflow-hidden transition-all duration-500 ease-out ${
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
                  onCompositionStart={() => {
                    isComposingRef.current = true
                  }}
                  onCompositionEnd={() => {
                    isComposingRef.current = false
                  }}
                  onKeyDown={handleKeyDown}
                  disabled={streamingState.isStreaming}
                  placeholder={
                    streamingState.isStreaming
                      ? '添加到队列'
                      : isImg2ImgMode
                        ? '描述要对图片进行的修改...'
                        : '输入消息...'
                  }
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
                    accept='image/*,.txt,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.md,.csv,.json,.html'
                    multiple
                    onChange={handleFileUpload}
                    disabled={uploading || streamingState.isStreaming}
                    className='hidden'
                  />
                  <div className='relative'>
                    <button
                      onClick={() => generalFileInputRef.current?.click()}
                      disabled={uploading || streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        uploading || streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : 'hover:bg-[var(--bg-toolbar-hover)] cursor-pointer'
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

                  {/* 生成图片按钮 */}
                  <div className='relative'>
                    <button
                      onClick={() => {
                        if (isImg2ImgMode) {
                          textareaRef.current?.focus()
                        } else {
                          setInput('生成图片：')
                        }
                      }}
                      disabled={streamingState.isStreaming}
                      className={`peer flex items-center justify-center w-8 h-8 rounded-md transition-all duration-200 ${
                        streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed'
                          : isImg2ImgMode
                            ? 'bg-[var(--brand-primary)]/10 text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/20 cursor-pointer'
                            : 'hover:bg-[var(--bg-toolbar-hover)] text-[var(--text-toolbar)] cursor-pointer'
                      }`}
                      aria-label={isImg2ImgMode ? '图生图模式' : '生成图片'}
                    >
                      <Image className='w-4 h-4' />
                    </button>
                    <span className='tooltip-content'>
                      {isImg2ImgMode ? '图生图模式' : '生成图片'}
                    </span>
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
                            ? 'border-[var(--accent-primary)] bg-[var(--accent-primary)]/15 text-[var(--accent-primary)] hover:bg-[var(--accent-primary)]/25'
                            : 'border-transparent hover:bg-[var(--bg-toolbar-hover)] text-[var(--text-toolbar)]'
                      }`}
                      aria-label={webSearchEnabled ? '关闭联网搜索' : '开启联网搜索'}
                      aria-pressed={webSearchEnabled}
                    >
                      <div className='flex h-4 w-4 shrink-0 items-center justify-center'>
                        <motion.div
                          animate={{
                            rotate: webSearchEnabled ? 180 : 0,
                            scale: webSearchEnabled ? 1.1 : 1,
                          }}
                          transition={{ type: 'spring', stiffness: 260, damping: 25 }}
                          whileHover={{
                            rotate: webSearchEnabled ? 180 : 15,
                            scale: 1.1,
                            transition: { type: 'spring', stiffness: 300, damping: 10 },
                          }}
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

                  {/* Agent 模式开关 */}
                  <div className='relative'>
                    <button
                      onClick={() => setAgentModeEnabled((enabled) => !enabled)}
                      disabled={streamingState.isStreaming}
                      className={`peer flex items-center gap-1.5 h-8 cursor-pointer rounded-full border px-1.5 py-1 transition-all ${
                        streamingState.isStreaming
                          ? 'opacity-40 cursor-not-allowed border-transparent'
                          : agentModeEnabled
                            ? 'border-[var(--brand-primary)] bg-[var(--brand-primary)]/15 text-[var(--brand-primary)] hover:bg-[var(--brand-primary)]/25'
                            : 'border-transparent hover:bg-[var(--bg-toolbar-hover)] text-[var(--text-toolbar)]'
                      }`}
                      aria-label={agentModeEnabled ? '关闭 Agent 模式' : '开启 Agent 模式'}
                      aria-pressed={agentModeEnabled}
                    >
                      <div className='flex h-4 w-4 shrink-0 items-center justify-center'>
                        <motion.div
                          animate={{
                            rotate: agentModeEnabled ? 180 : 0,
                            scale: agentModeEnabled ? 1.1 : 1,
                          }}
                          transition={{ type: 'spring', stiffness: 260, damping: 25 }}
                          whileHover={{
                            rotate: agentModeEnabled ? 180 : 15,
                            scale: 1.1,
                            transition: { type: 'spring', stiffness: 300, damping: 10 },
                          }}
                        >
                          <Cpu className='h-4 w-4' />
                        </motion.div>
                      </div>
                      <AnimatePresence>
                        {agentModeEnabled && (
                          <motion.span
                            initial={{ width: 0, opacity: 0 }}
                            animate={{ width: 'auto', opacity: 1 }}
                            exit={{ width: 0, opacity: 0 }}
                            transition={{ duration: 0.2 }}
                            className='shrink-0 overflow-hidden whitespace-nowrap text-xs font-semibold'
                          >
                            Agent
                          </motion.span>
                        )}
                      </AnimatePresence>
                    </button>
                    <span className='tooltip-content'>
                      {agentModeEnabled ? '已开启 Agent 模式' : '开启 Agent 模式'}
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
                          ? 'bg-[var(--accent-amber)] text-white hover:brightness-110 hover:scale-105 shadow-md shadow-[var(--accent-amber)]/30 cursor-pointer'
                          : isOutputting
                            ? 'bg-[var(--accent-primary)] text-white hover:brightness-110 hover:scale-105 shadow-md shadow-[var(--accent-primary)]/30 cursor-pointer'
                            : isOptimizing
                              ? 'bg-[var(--accent-emerald)] text-white hover:brightness-110 hover:scale-105 shadow-md shadow-[var(--accent-emerald)]/30 cursor-pointer'
                              : uploading
                                ? 'bg-[var(--accent-primary)]/80 text-white cursor-wait'
                                : hasContent && charCount <= maxChars
                                  ? 'bg-[var(--accent-primary)] text-white shadow-lg shadow-[var(--accent-primary)]/30 hover:shadow-xl hover:shadow-[var(--accent-primary)]/40 hover:scale-105 cursor-pointer'
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
                            ? 'opacity-100 animate-pulse bg-[var(--accent-primary)]/40'
                            : isOptimizing
                              ? 'opacity-100 animate-pulse bg-[var(--accent-emerald)]/40'
                              : 'opacity-0'
                        }`}
                        style={{ animationDuration: '1.8s' }}
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
            正在上传...
          </div>
        )}
      </div>
    </div>
  )
}
