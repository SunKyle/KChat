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
} from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { images } from '../../api'

export function InputArea() {
  const [input, setInput] = useState('')
  const [uploadingImages, setUploadingImages] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const {
    sendMessage,
    streamingState,
    activeConversation,
    createConversation,
    stopStreaming,
  } = useChat()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const generalFileInputRef = useRef<HTMLInputElement>(null)

  const charCount = input.length
  const maxChars = 2000
  const maxImages = 5

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height =
        Math.min(textareaRef.current.scrollHeight, 200) + 'px'
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
    <div className="p-4 pb-6">
      <div className="max-w-3xl mx-auto relative group">
        {/* 已上传图片预览 */}
        {uploadingImages.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-3">
            {uploadingImages.map((imageUrl, index) => (
              <div
                key={index}
                className="relative w-16 h-16 rounded-lg overflow-hidden border theme-border-primary hover:border-primary-500/50 transition-all duration-200"
              >
                <img
                  src={imageUrl}
                  alt={`Uploaded ${index + 1}`}
                  className="w-full h-full object-cover"
                />
                <button
                  onClick={() => handleRemoveImage(index)}
                  className="absolute top-1 right-1 w-6 h-6 flex items-center justify-center theme-bg-card/80 backdrop-blur-sm rounded-full hover:theme-bg-hover hover:scale-110 transition-all duration-200"
                >
                  <Trash2 className="w-4 h-4 theme-text-primary" />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* 输入框容器 */}
        <div className="flex flex-col theme-bg-sidebar/80 backdrop-blur-xl rounded-2xl border-0 shadow-[0_2px_8px_rgba(0,0,0,0.15),0_4px_16px_rgba(0,0,0,0.1)] focus-within:theme-bg-sidebar focus-within:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] hover:theme-bg-sidebar hover:shadow-[0_4px_12px_rgba(0,0,0,0.18),0_8px_20px_rgba(0,0,0,0.12)] transition-all duration-200 ease-out">
          {/* 上半部分：文本输入区域 */}
          <div className="px-4 py-2">
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={streamingState.isStreaming}
              placeholder={
                streamingState.isStreaming ? 'AI 正在思考中...' : '输入消息...'
              }
              className="w-full resize-none bg-transparent px-0 py-1 theme-text-primary placeholder-theme-text-placeholder focus:outline-none min-h-[40px] max-h-[200px] overflow-y-auto text-sm leading-relaxed"
            />
          </div>

          {/* 下半部分：工具栏 */}
          <div className="flex items-center justify-between px-4 pt-0 pb-1">
            {/* 左侧：功能按钮 */}
            <div className="flex items-center gap-3">
              {/* 上传文件按钮（包括图片） */}
              <input
                ref={generalFileInputRef}
                type="file"
                accept="image/*,.txt,.pdf,.doc,.docx"
                multiple
                onChange={handleFileUpload}
                disabled={
                  uploading ||
                  streamingState.isStreaming ||
                  uploadingImages.length >= maxImages
                }
                className="hidden"
              />
              <button
                onClick={() => generalFileInputRef.current?.click()}
                disabled={
                  uploading ||
                  streamingState.isStreaming ||
                  uploadingImages.length >= maxImages
                }
                className={`p-2 rounded-lg transition-all duration-200 ${
                  uploading ||
                  streamingState.isStreaming ||
                  uploadingImages.length >= maxImages
                    ? 'theme-text-muted/50 cursor-not-allowed'
                    : 'theme-text-muted hover:theme-text-primary hover:theme-bg-hover/50 hover:scale-105 cursor-pointer'
                }`}
                title="上传文件（包括图片）"
              >
                {uploading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Paperclip className="w-4 h-4" />
                )}
              </button>

              {/* 代码按钮 */}
              <button
                className="p-2 rounded-lg theme-text-muted hover:theme-text-primary hover:theme-bg-hover/50 hover:scale-105 transition-all duration-200"
                title="插入代码"
              >
                <Code className="w-4 h-4" />
              </button>

              {/* 生成图片按钮 */}
              <button
                onClick={() => setInput('生成图片：')}
                disabled={streamingState.isStreaming}
                className={`p-2 rounded-lg transition-all duration-200 ${
                  streamingState.isStreaming
                    ? 'theme-text-muted/50 cursor-not-allowed'
                    : 'theme-text-muted hover:theme-text-primary hover:theme-bg-hover/50 hover:scale-105 cursor-pointer'
                }`}
                title="生成图片"
              >
                <Image className="w-4 h-4" />
              </button>
            </div>

            {/* 右侧：提示文字和发送按钮 */}
            <div className="flex items-center gap-2">
              {/* 键盘提示 */}
              <span className="text-xs theme-text-muted/70">
                Shift + Enter 换行, Enter 发送
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
                className={`flex items-center justify-center p-2 rounded-lg micro-transition transition-all duration-200 ${
                  streamingState.isStreaming
                    ? 'bg-red-500/20 text-red-400 hover:bg-red-500/30 hover:scale-105 cursor-pointer'
                    : hasContent && charCount <= maxChars
                      ? 'bg-sky-500 text-white shadow-lg shadow-sky-500/40 hover:bg-sky-400 hover:shadow-xl hover:shadow-sky-500/50 hover:scale-105 active:scale-95 cursor-pointer'
                      : 'theme-bg-hover/50 theme-text-muted/50 cursor-not-allowed'
                }`}
                title={streamingState.isStreaming ? '中断回答' : '发送消息'}
              >
                {streamingState.isStreaming ? (
                  <Square className="w-4 h-4" fill="currentColor" />
                ) : (
                  <Send className="w-4 h-4" />
                )}
              </button>
            </div>
          </div>
        </div>

        {/* 图片上传提示 */}
        {uploading && (
          <div className="mt-2 text-xs theme-text-muted flex items-center gap-1">
            <Loader2 className="w-3 h-3 animate-spin" />
            正在上传图片...
          </div>
        )}
      </div>
    </div>
  )
}
