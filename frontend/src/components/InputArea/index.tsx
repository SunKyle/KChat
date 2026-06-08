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
  const { sendMessage, streamingState, activeConversation, createConversation, stopStreaming } =
    useChat()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)

  const charCount = input.length
  const maxChars = 2000
  const maxImages = 5

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

    if (!activeConversation) {
      await createConversation()
    }

    sendMessage(input, uploadingImages)
    setInput('')
    setUploadingImages([])
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
                className='relative w-16 h-16 rounded-lg overflow-hidden border border-gray-200 hover:border-sky-400/50 transition-all duration-200 shadow-sm hover:shadow-md'
              >
                <img
                  src={imageUrl}
                  alt={`Uploaded ${index + 1}`}
                  className='w-full h-full object-cover'
                />
                <button
                  onClick={() => handleRemoveImage(index)}
                  className='absolute top-1 right-1 w-6 h-6 backdrop-blur-sm bg-white/90 rounded-full flex items-center justify-center hover:bg-red-50 hover:text-red-500 transition-colors'
                >
                  <Trash2 className='w-[14px] h-[14px]' />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* 输入框容器 */}
        {(() => {
          const isOutputting = streamingState.isStreaming && streamingState.currentContent.length > 0
          const isThinking = streamingState.isStreaming && !isOutputting
          return (
          <div className={`flex flex-col card-float-solid mx-4 mb-4 lg:mx-6 lg:mb-6 overflow-hidden transition-all duration-500 ease-out ${
            isThinking
              ? 'shadow-[0_0_20px_rgba(16,185,129,0.10),0_4px_16px_rgba(16,185,129,0.06)] ring-1 ring-emerald-400/15'
              : isOutputting
                ? 'shadow-[0_0_20px_rgba(14,165,233,0.10),0_4px_16px_rgba(14,165,233,0.06)] ring-1 ring-sky-400/15'
                : 'shadow-[0_4px_16px_rgba(0,0,0,0.06),0_6px_24px_rgba(0,0,0,0.03)] focus-within:shadow-[0_0_24px_rgba(14,165,233,0.12),0_4px_16px_rgba(14,165,233,0.06)] focus-within:ring-1 focus-within:ring-sky-400/15'
          }`}>
          {/* AI 思考/输出状态条 */}
          {streamingState.isStreaming && (() => {
            const isOutputting = streamingState.currentContent.length > 0
            return (
              <div className={`thinking-bar-enter flex items-center justify-between px-4 py-2.5 mx-2 mt-2 rounded-xl transition-colors duration-500 ${
                isOutputting ? 'bg-sky-50/80' : 'bg-emerald-50/80'
              }`}>
                <div className='flex items-center gap-2.5'>
                  <div className={`w-5 h-5 rounded-full flex items-center justify-center transition-colors duration-500 ${
                    isOutputting ? 'border-[1.5px] border-sky-500/60' : 'border-[1.5px] border-emerald-500/60'
                  }`}>
                    <div className={`w-2.5 h-2.5 border-[1.5px] border-t-transparent rounded-full thinking-spinner transition-colors duration-500 ${
                      isOutputting ? 'border-sky-500' : 'border-emerald-500'
                    }`} />
                  </div>
                  <span className={`text-[13px] font-medium transition-colors duration-500 ${
                    isOutputting ? 'text-sky-700' : 'text-emerald-700'
                  }`}>{isOutputting ? '正在输出...' : '正在思考...'}</span>
                </div>
                <span className={`text-[12px] font-secondary tabular-nums transition-colors duration-500 ${
                  isOutputting ? 'text-sky-600/80' : 'text-emerald-600/80'
                }`}>{elapsedSeconds}s</span>
              </div>
            )
          })()}

          {/* 上半部分：文本输入区域 */}
          <div className='px-4 py-1.5'>
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder={streamingState.isStreaming ? '添加到队列' : '输入消息...'}
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
                    : 'hover:bg-gray-100 hover:text-sky-600 cursor-pointer'
                }`}
                title='上传文件（包括图片）'
              >
                {uploading ? (
                  <Loader2 className='w-4 h-4 text-gray-400 animate-spin' />
                ) : (
                  <Paperclip className='w-4 h-4 text-gray-500' />
                )}
              </button>

              {/* 代码按钮 */}
              <button
                className='flex items-center justify-center w-9 h-9 rounded-md hover:bg-gray-100 hover:text-amber-600 text-gray-500 transition-all duration-200 cursor-pointer'
                title='插入代码'
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
                    : 'hover:bg-gray-100 hover:text-emerald-600 text-gray-500 cursor-pointer'
                }`}
                title='生成图片'
              >
                <Image className='w-4 h-4' />
              </button>
            </div>

            {/* 右侧：提示文字和发送按钮 */}
            <div className='flex items-center gap-3'>
              {/* 字符计数 */}
              {!streamingState.isStreaming && (
                <span
                  className={`font-helper-text transition-colors ${charCount > maxChars ? 'text-red-500' : 'text-gray-400'}`}
                >
                  {charCount}/{maxChars}
                </span>
              )}

              {/* 键盘提示 */}
              {!streamingState.isStreaming && (
                <span className='font-helper-text text-gray-400/60 hidden sm:inline'>
                  Shift + Enter 换行
                </span>
              )}

              {/* 发送/停止按钮 */}
              <button
                onClick={() => {
                  if (streamingState.isStreaming) {
                    stopStreaming()
                  } else {
                    handleSend()
                  }
                }}
                disabled={!hasContent && !streamingState.isStreaming}
                className={`relative flex items-center justify-center w-9 h-9 rounded-full transition-all duration-200 ease-out ${
                  streamingState.isStreaming
                    ? 'bg-emerald-500 text-white hover:bg-emerald-600 hover:scale-105 shadow-md shadow-emerald-500/25 cursor-pointer'
                    : uploading
                      ? 'bg-sky-500/80 text-white cursor-wait'
                      : hasContent && charCount <= maxChars
                        ? 'bg-gradient-to-br from-sky-500 to-sky-600 text-white shadow-lg shadow-sky-500/30 hover:shadow-xl hover:shadow-sky-500/40 hover:scale-105 cursor-pointer'
                        : 'bg-gray-100 text-gray-400 cursor-not-allowed'
                }`}
                title={
                  streamingState.isStreaming ? '中断回答' : uploading ? '发送中...' : '发送消息'
                }
              >
                {streamingState.isStreaming ? (
                  <Square className='w-3.5 h-3.5' fill='currentColor' />
                ) : uploading ? (
                  <div className='w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin' />
                ) : (
                  <Send className='w-4 h-4' />
                )}
              </button>
            </div>
          </div>
        </div>
          )
        })()}
        {uploading && (
          <div className='mt-3 font-helper-text text-gray-500 flex items-center gap-2'>
            <Loader2 className='w-3 h-3 animate-spin' />
            正在上传图片...
          </div>
        )}
      </div>
    </div>
  )
}
