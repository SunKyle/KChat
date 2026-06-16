import { useState } from 'react'
import { X, Eye, EyeOff, Save, Maximize2 } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'

interface FullscreenMarkdownEditorProps {
  title: string
  content: string
  onClose: () => void
  onSave: (title: string, content: string) => void
}

export function FullscreenMarkdownEditor({
  title,
  content,
  onClose,
  onSave,
}: FullscreenMarkdownEditorProps) {
  const [editorTitle, setEditorTitle] = useState(title)
  const [editorContent, setEditorContent] = useState(content)
  const [showPreview, setShowPreview] = useState(false)

  const handleSave = () => {
    onSave(editorTitle, editorContent)
    onClose()
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 's') {
      e.preventDefault()
      handleSave()
    }
    if (e.key === 'Escape') {
      onClose()
    }
  }

  const toolbarButtons = [
    { icon: '#', label: '标题', action: () => insertText('# ') },
    { icon: '**', label: '粗体', action: () => insertText('**', '**') },
    { icon: '*', label: '斜体', action: () => insertText('*', '*') },
    { icon: '`', label: '代码', action: () => insertText('`', '`') },
    { icon: '```', label: '代码块', action: () => insertText('\n```\n', '\n```\n') },
    { icon: '>', label: '引用', action: () => insertText('> ') },
    { icon: '-', label: '列表', action: () => insertText('- ') },
    { icon: '1.', label: '有序', action: () => insertText('1. ') },
    { icon: '[', label: '链接', action: () => insertText('[', '](url)') },
    { icon: '![]', label: '图片', action: () => insertText('![', '](image-url)') },
  ]

  const insertText = (before: string, after: string = '') => {
    const textarea = document.querySelector('.markdown-textarea') as HTMLTextAreaElement
    if (!textarea) return

    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const selectedText = editorContent.substring(start, end)
    const newText =
      editorContent.substring(0, start) +
      before +
      selectedText +
      after +
      editorContent.substring(end)

    setEditorContent(newText)

    setTimeout(() => {
      const newStart = start + before.length
      const newEnd = newStart + selectedText.length
      textarea.setSelectionRange(newStart, newEnd)
      textarea.focus()
    }, 0)
  }

  return (
    <div className='fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm'>
      <div className='w-[90vw] h-[85vh] bg-[var(--bg-primary)] rounded-2xl shadow-2xl flex flex-col overflow-hidden'>
        {/* 工具栏 */}
        <div className='flex items-center justify-between px-6 py-4 border-b border-[var(--border-divider)] bg-[var(--bg-secondary)]'>
          <div className='flex items-center gap-4'>
            <button
              onClick={onClose}
              className='p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='关闭'
            >
              <X className='w-5 h-5 text-[var(--text-muted)]' />
            </button>
            <h2 className='text-lg font-semibold text-[var(--text-primary)]'>Markdown 编辑器</h2>
          </div>
          <div className='flex items-center gap-2'>
            {/* 格式化按钮 */}
            <div className='flex items-center gap-1 bg-[var(--bg-input)] rounded-lg p-1'>
              {toolbarButtons.map((btn) => (
                <button
                  key={btn.label}
                  onClick={btn.action}
                  className='px-2 py-1.5 rounded-md text-[12px] font-medium text-[var(--text-muted)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-hover)] transition-colors'
                  title={btn.label}
                >
                  {btn.icon}
                </button>
              ))}
            </div>
            {/* 预览切换 */}
            <button
              onClick={() => setShowPreview(!showPreview)}
              className='flex items-center gap-2 px-4 py-2 rounded-lg bg-[var(--bg-input)] text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] transition-colors'
            >
              {showPreview ? (
                <>
                  <EyeOff className='w-4 h-4' />
                  编辑
                </>
              ) : (
                <>
                  <Eye className='w-4 h-4' />
                  预览
                </>
              )}
            </button>
            {/* 保存按钮 */}
            <button
              onClick={handleSave}
              className='flex items-center gap-2 px-5 py-2 rounded-lg bg-[var(--brand-primary)] text-white hover:brightness-110 transition-all'
            >
              <Save className='w-4 h-4' />
              保存
            </button>
          </div>
        </div>

        {/* 编辑区域 */}
        <div className='flex-1 flex overflow-hidden'>
          {!showPreview && (
            <div className='flex-1 flex flex-col overflow-hidden'>
              {/* 标题输入 */}
              <input
                type='text'
                value={editorTitle}
                onChange={(e) => setEditorTitle(e.target.value)}
                placeholder='输入标题...'
                className='px-6 py-4 text-xl font-semibold bg-transparent text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none border-none'
              />
              {/* 内容编辑 */}
              <textarea
                ref={(el) => el?.focus()}
                className='markdown-textarea flex-1 w-full px-6 py-4 bg-transparent text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none resize-none font-mono text-sm leading-relaxed'
                value={editorContent}
                onChange={(e) => setEditorContent(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder='使用 Markdown 语法编写内容...'
                spellCheck={false}
              />
            </div>
          )}
          {/* 预览区域 */}
          <div className='flex-1 overflow-y-auto px-6 py-4 bg-[var(--bg-card)]'>
            <h1 className='text-2xl font-bold text-[var(--text-primary)] mb-4'>
              {editorTitle || '预览'}
            </h1>
            <ReactMarkdown
              components={{
                code({ node, inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '')
                  return !inline && match ? (
                    <SyntaxHighlighter style={oneLight} language={match[1]} PreTag='div' {...props}>
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code
                      className='px-1.5 py-0.5 bg-[var(--bg-hover)] rounded text-sm font-mono'
                      {...props}
                    >
                      {children}
                    </code>
                  )
                },
                h1: ({ children }) => (
                  <h1
                    className='text-2xl font-bold text-[var(--text-primary)] mt-6 mb-3'
                    children={children}
                  />
                ),
                h2: ({ children }) => (
                  <h2
                    className='text-xl font-semibold text-[var(--text-primary)] mt-5 mb-2'
                    children={children}
                  />
                ),
                h3: ({ children }) => (
                  <h3
                    className='text-lg font-semibold text-[var(--text-primary)] mt-4 mb-2'
                    children={children}
                  />
                ),
                h4: ({ children }) => (
                  <h4
                    className='text-base font-semibold text-[var(--text-primary)] mt-3 mb-1'
                    children={children}
                  />
                ),
                p: ({ children }) => (
                  <p
                    className='text-[var(--text-secondary)] mb-3 leading-relaxed'
                    children={children}
                  />
                ),
                ul: ({ children }) => (
                  <ul className='list-disc list-inside mb-3 space-y-1' children={children} />
                ),
                ol: ({ children }) => (
                  <ol className='list-decimal list-inside mb-3 space-y-1' children={children} />
                ),
                li: ({ children }) => (
                  <li className='text-[var(--text-secondary)]' children={children} />
                ),
                blockquote: ({ children }) => (
                  <blockquote
                    className='border-l-4 border-[var(--brand-primary)] pl-4 italic text-[var(--text-muted)] my-3'
                    children={children}
                  />
                ),
                a: ({ href, children }) => (
                  <a
                    href={href}
                    className='text-[var(--brand-primary)] hover:underline'
                    children={children}
                  />
                ),
                strong: ({ children }) => (
                  <strong
                    className='font-semibold text-[var(--text-primary)]'
                    children={children}
                  />
                ),
                em: ({ children }) => <em className='italic' children={children} />,
              }}
            >
              {editorContent}
            </ReactMarkdown>
          </div>
        </div>

        {/* 底部提示 */}
        <div className='px-6 py-3 border-t border-[var(--border-divider)] bg-[var(--bg-secondary)]'>
          <div className='flex items-center justify-between text-xs text-[var(--text-muted)]'>
            <div className='flex items-center gap-4'>
              <span>支持 Markdown 语法</span>
              <span>|</span>
              <span>按 Esc 关闭</span>
            </div>
            <div className='flex items-center gap-4'>
              <span>Ctrl/Cmd + S 保存</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
