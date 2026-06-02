import { useState, useEffect, useRef } from 'react'
import type { KeyboardEvent } from 'react'
import { Send, Square, Image, Trash2, Paperclip, Code } from 'lucide-react'
import { useChat } from '../../context/ChatContext'
import { api } from '../../utils/api'

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
        const result = await api.images.upload(file)
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
      <div className="max-w-[800px] mx-auto relative group">
        {/* 已上传图片预览 */}
        {uploadingImages.length > 0 && (
          <div className="flex flex-wrap gap-2 mb-3">
            {uploadingImages.map((imageUrl, index) => (
              <div
                key={index}
                className="relative w-16 h-16 rounded-lg overflow-hidden border border-white/10"
              >
                <img
                  src={imageUrl}
                  alt={`Uploaded ${index + 1}`}
                  className="w-full h-full object-cover"
                />
                <button
                  onClick={() => handleRemoveImage(index)}
                  className="absolute top-1 right-1 w-5 h-5 flex items-center justify-center bg-black/60 rounded-full hover:bg-black/80 transition-colors"
                >
                  <Trash2 className="w-3 h-3 text-white" />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* 输入框容器 */}
        <div
          className={`flex flex-col bg-white/[0.03] backdrop-blur-2xl rounded-[12px] border border-white/10 micro-transition focus-within:border-sky-500/30 focus-within:bg-white/[0.05] transition-all duration-300`}
        >
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
              className="w-full resize-none bg-transparent px-0 py-1 text-[#E5E7EB] placeholder-slate-500 focus:outline-none min-h-[40px] max-h-[200px] overflow-y-auto text-sm leading-relaxed"
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
                className={`p-1.5 rounded-lg transition-colors ${
                  uploading ||
                  streamingState.isStreaming ||
                  uploadingImages.length >= maxImages
                    ? 'text-slate-600 cursor-not-allowed'
                    : 'text-slate-500 hover:text-slate-300 hover:bg-white/5 cursor-pointer'
                }`}
                title="上传文件（包括图片）"
              >
                <Paperclip className="w-4 h-4" />
              </button>

              {/* 代码按钮 */}
              <button
                className="p-1.5 text-slate-500 hover:text-slate-300 hover:bg-white/5 rounded-lg transition-colors"
                title="插入代码"
              >
                <Code className="w-4 h-4" />
              </button>

              {/* 生成图片按钮 */}
              <button
                onClick={() => setInput('生成图片：')}
                disabled={streamingState.isStreaming}
                className={`p-1.5 rounded-lg transition-colors ${
                  streamingState.isStreaming
                    ? 'text-slate-600 cursor-not-allowed'
                    : 'text-slate-500 hover:text-slate-300 hover:bg-white/5 cursor-pointer'
                }`}
                title="生成图片"
              >
                <Image className="w-4 h-4" />
              </button>
            </div>

            {/* 右侧：提示文字和发送按钮 */}
            <div className="flex items-center gap-2">
              {/* 键盘提示 */}
              <span className="text-xs text-slate-600">
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
                className={`flex items-center justify-center p-1.5 rounded-lg micro-transition ${
                  streamingState.isStreaming
                    ? 'bg-red-500/20 text-red-400 hover:bg-red-500/30 cursor-pointer'
                    : hasContent && charCount <= maxChars
                      ? 'bg-sky-500 text-white shadow-lg shadow-sky-500/30 hover:bg-sky-400 active:scale-95 cursor-pointer'
                      : 'bg-slate-700/50 text-slate-500 cursor-not-allowed'
                }`}
                title={streamingState.isStreaming ? '中断回答' : '发送消息'}
              >
                {streamingState.isStreaming ? (
                  <Square className="w-3.5 h-3.5" fill="currentColor" />
                ) : (
                  <Send className="w-4 h-4" />
                )}
              </button>
            </div>
          </div>
        </div>

        {/* 图片上传提示 */}
        {uploading && (
          <div className="mt-2 text-xs text-slate-500">正在上传图片...</div>
        )}
      </div>
    </div>
  )
}
