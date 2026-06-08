import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { Send, Square, Image, Trash2, Paperclip, Code, Loader2 } from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { images } from '../../api'

export function InputArea() {
  const [input, setInput] = useState('')
  const [uploadingImages, setUploadingImages] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const { sendMessage, streamingState, activeConversation, createConversation, stopStreaming } =
    useChat()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)

  const charCount = input.length
  const maxChars = 2000
  const maxImages = 5

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
        <div className='flex flex-col card-float-solid mx-4 mb-4 lg:mx-6 lg:mb-6 shadow-[0_4px_20px_rgba(0,0,0,0.08),0_8px_30px_rgba(0,0,0,0.04)] transition-all duration-300 ease-out focus-within:shadow-[0_6px_24px_rgba(147,197,253,0.18),0_10px_40px_rgba(147,197,253,0.12)]'>
          {/* 上半部分：文本输入区域 */}
          <div className='px-5 py-3'>
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder={streamingState.isStreaming ? 'AI 正在思考中...' : '输入消息...'}
              className='w-full resize-none bg-transparent px-0 py-2 theme-text-primary placeholder-theme-text-placeholder focus:outline-none min-h-[48px] max-h-[200px] overflow-y-auto font-input-text'
            />
          </div>

          {/* 下半部分：工具栏 */}
          <div className='flex items-center justify-between px-5 pt-1 pb-3'>
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
              <span
                className={`font-helper-text transition-colors ${charCount > maxChars ? 'text-red-500' : 'text-gray-400'}`}
              >
                {charCount}/{maxChars}
              </span>

              {/* 键盘提示 */}
              <span className='font-helper-text text-gray-400/60 hidden sm:inline'>
                Shift + Enter 换行
              </span>

              {/* 发送按钮 */}
              <button
                onClick={() => {
                  if (streamingState.isStreaming) {
                    stopStreaming()
                  } else {
                    handleSend()
                  }
                }}
                disabled={!hasContent && !streamingState.isStreaming}
                className={`relative flex items-center justify-center w-11 h-11 rounded-lg transition-all duration-200 ease-out ${
                  streamingState.isStreaming
                    ? 'bg-red-500/15 text-red-500 hover:bg-red-500/25 hover:scale-105 cursor-pointer'
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
                  <Square className='w-5 h-5' fill='currentColor' />
                ) : uploading ? (
                  <div className='w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin' />
                ) : (
                  <Send className='w-5 h-5' />
                )}
              </button>
            </div>
          </div>
        </div>

        {/* 图片上传提示 */}
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
