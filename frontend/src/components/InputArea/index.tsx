import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { Send, Square, Image, Trash2, Paperclip, Code, Loader2 } from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { images } from '../../api'

export function InputArea() {
  const [input, setInput] = useState('')
  const [uploadingImages, setUploadingImages] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const [showStatusBar, setShowStatusBar] = useState(false)
  const [isExiting, setIsExiting] = useState(false)
  const { sendMessage, streamingState, stopStreaming } = useChat()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)
  const exitTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

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

    sendMessage(currentInput, currentImages)
  }

  const hasContent = input.trim() || uploadingImages.length > 0

  return (
    <div className='p-4 pb-6'>
      <div className='max-w-3xl mx-auto relative group'>
        {/* 已上传图片预览 */}
        {uploadingImages.length > 0 && (
          <div className='flex flex-wrap gap-2 mb-3'>
            {uploadingImages.map((imageUrl, index) => (
              <div
                key={index}
                className='relative w-16 h-16 rounded-lg overflow-hidden border theme-border-secondary hover:border-[var(--accent-sky)]/50 transition-all duration-200 shadow-sm hover:shadow-md'
              >
                <img
                  src={imageUrl}
                  alt={`Uploaded ${index + 1}`}
                  className='w-full h-full object-cover'
                />
                <button
                  onClick={() => handleRemoveImage(index)}
                  className='absolute top-1 right-1 w-7 h-7 backdrop-blur-sm bg-[var(--bg-card)]/90 rounded-full flex items-center justify-center hover:bg-[var(--brand-danger)]/10 hover:text-[var(--brand-danger)] transition-colors'
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
                      className={`text-[12px] font-medium transition-all duration-500 ${
                        isOutputting ? 'text-sky-700' : 'text-amber-700'
                      }`}
                    >
                      {isOutputting ? '正在输出...' : '正在思考...'}
                    </span>
                  </div>
                  <span
                    className={`text-[11px] font-secondary tabular-nums transition-all duration-500 ${
                      isOutputting ? 'text-sky-600/70' : 'text-amber-600/70'
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
              className={`relative z-10 -mt-3 flex flex-col card-float-solid mx-4 mb-4 lg:mx-6 lg:mb-6 overflow-hidden transition-all duration-500 ease-out ${
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
                  onChange={(e) => setInput(e.target.value)}
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
                <div className='flex items-center gap-1'>
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
                  <button
                    onClick={() => generalFileInputRef.current?.click()}
                    disabled={
                      uploading || streamingState.isStreaming || uploadingImages.length >= maxImages
                    }
                    className={`flex items-center justify-center w-9 h-9 rounded-md transition-all duration-200 ${
                      uploading || streamingState.isStreaming || uploadingImages.length >= maxImages
                        ? 'opacity-40 cursor-not-allowed'
                        : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-sky-600 cursor-pointer'
                    }`}
                    title='上传文件（包括图片）'
                    aria-label='上传文件'
                  >
                    {uploading ? (
                      <Loader2 className='w-4 h-4 theme-text-muted animate-spin' />
                    ) : (
                      <Paperclip className='w-4 h-4 text-[var(--text-toolbar)]' />
                    )}
                  </button>

                  {/* 代码按钮 */}
                  <button
                    className='flex items-center justify-center w-9 h-9 rounded-md hover:bg-[var(--bg-toolbar-hover)] hover:text-amber-600 text-[var(--text-toolbar)] transition-all duration-200 cursor-pointer'
                    title='插入代码'
                    aria-label='插入代码'
                  >
                    <Code className='w-4 h-4' />
                  </button>

                  {/* 生成图片按钮 */}
                  <button
                    onClick={() => setInput('生成图片：')}
                    disabled={streamingState.isStreaming}
                    className={`flex items-center justify-center w-9 h-9 rounded-md transition-all duration-200 ${
                      streamingState.isStreaming
                        ? 'opacity-40 cursor-not-allowed'
                        : 'hover:bg-[var(--bg-toolbar-hover)] hover:text-emerald-600 text-[var(--text-toolbar)] cursor-pointer'
                    }`}
                    title='生成图片'
                    aria-label='生成图片'
                  >
                    <Image className='w-4 h-4' />
                  </button>
                </div>

                {/* 右侧：提示文字和发送按钮 */}
                <div className='flex items-center gap-3'>
                  {/* 字符计数 — 三级颜色阈值 */}
                  {!streamingState.isStreaming && (
                    <span
                      aria-live='polite'
                      className={`font-helper-text tabular-nums transition-colors duration-200 ${
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
                    <span className='font-helper-text theme-text-muted/60 hidden sm:inline'>
                      Shift + Enter 换行
                    </span>
                  )}

                  {/* 发送/停止按钮 — 三态平滑过渡 */}
                  <button
                    onClick={() => {
                      if (streamingState.isStreaming) {
                        stopStreaming()
                      } else {
                        handleSend()
                      }
                    }}
                    disabled={!hasContent && !streamingState.isStreaming}
                    className={`group/send relative flex items-center justify-center w-8 h-8 rounded-full transition-[background-color,box-shadow,transform,color] duration-500 ease-out ${
                      isThinking
                        ? 'bg-amber-500 text-white hover:bg-amber-600 hover:scale-105 shadow-md shadow-amber-500/30 cursor-pointer'
                        : isOutputting
                          ? 'bg-sky-500 text-white hover:bg-sky-600 hover:scale-105 shadow-md shadow-sky-500/30 cursor-pointer'
                          : uploading
                            ? 'bg-sky-500/80 text-white cursor-wait'
                            : hasContent && charCount <= maxChars
                              ? 'bg-gradient-to-br from-sky-500 to-sky-600 text-white shadow-lg shadow-sky-500/30 hover:shadow-xl hover:shadow-sky-500/40 hover:scale-105 cursor-pointer'
                              : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                    }`}
                    title={
                      streamingState.isStreaming ? '中断回答' : uploading ? '发送中...' : '发送消息'
                    }
                    aria-label={
                      streamingState.isStreaming ? '中断回答' : uploading ? '发送中...' : '发送消息'
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
                          className='w-3 h-3 transition-all duration-300 ease-out'
                          fill='currentColor'
                        />
                      ) : uploading ? (
                        <div className='w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin transition-opacity duration-300' />
                      ) : (
                        <Send className='w-3.5 h-3.5 transition-transform duration-300 group-hover/send:translate-x-0.5 group-hover/send:-translate-y-0.5' />
                      )}
                    </span>
                  </button>
                </div>
              </div>
            </div>
          )
        })()}
        {uploading && (
          <div className='mt-3 font-helper-text theme-text-muted flex items-center gap-2'>
            <Loader2 className='w-3 h-3 animate-spin' />
            正在上传图片...
          </div>
        )}
      </div>
    </div>
  )
}
