import { useState, useRef, useEffect } from 'react'
import { Icon, type IconName } from '../../components/common/Icon'
import { motion, AnimatePresence } from 'framer-motion'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import oneLight from 'react-syntax-highlighter/dist/esm/styles/prism/one-light'
import { MermaidBlock } from '../../components/ui/MermaidBlock'

interface FullscreenMarkdownEditorProps {
  title: string
  content: string
  onClose: () => void
  onSave?: (title: string, content: string) => void
  initialMode?: 'edit' | 'split' | 'preview'
}

export function FullscreenMarkdownEditor({
  title,
  content,
  onClose,
  onSave,
  initialMode = 'split',
}: FullscreenMarkdownEditorProps) {
  const [editorTitle, setEditorTitle] = useState(title)
  const [editorContent, setEditorContent] = useState(content)
  const [editorMode, setEditorMode] = useState<'edit' | 'split' | 'preview'>(initialMode)
  const [toolbarTooltip, setToolbarTooltip] = useState<string | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (initialMode === 'edit' || initialMode === 'split') textareaRef.current?.focus()
  }, [initialMode])

  const handleSave = () => {
    if (onSave) onSave(editorTitle, editorContent)
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

  const toggleMode = () => {
    switch (editorMode) {
      case 'edit':
        setEditorMode('split')
        break
      case 'split':
        setEditorMode('preview')
        break
      case 'preview':
        setEditorMode('edit')
        break
    }
  }

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

  const toolbarGroups: { label: string; icon: IconName; action: () => void }[][] = [
    [
      { label: '粗体', icon: 'Bold', action: () => insertText('**', '**') },
      { label: '斜体', icon: 'Italic', action: () => insertText('*', '*') },
      { label: '删除线', icon: 'Strikethrough', action: () => insertText('~~', '~~') },
    ],
    [
      { label: '标题', icon: 'Heading', action: () => insertText('## ') },
      { label: '引用', icon: 'Quote', action: () => insertText('> ') },
      { label: '链接', icon: 'Link', action: () => insertText('[', '](url)') },
    ],
    [
      { label: '代码', icon: 'Code', action: () => insertText('`', '`') },
      { label: '代码块', icon: 'FileCode', action: () => insertText('\n```\n', '\n```\n') },
    ],
    [
      { label: '无序列表', icon: 'List', action: () => insertText('- ') },
      { label: '有序列表', icon: 'ListOrdered', action: () => insertText('1. ') },
      { label: '图片', icon: 'Image', action: () => insertText('![', '](image-url)') },
    ],
  ]

  return (
    <div className='fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm'>
      <div className='w-[90vw] h-[85vh] bg-[var(--bg-primary)] rounded-2xl shadow-2xl flex flex-col overflow-hidden'>
        {/* 工具栏 */}
        <div className='flex items-center justify-between px-6 py-4 border-b border-[var(--border-divider)] bg-[var(--bg-card)]'>
          <div className='flex items-center gap-4'>
            <button
              onClick={onClose}
              className='p-2 rounded-lg hover:bg-[var(--bg-hover)] transition-colors'
              aria-label='关闭'
            >
              <Icon name='X' size='lg' className='text-[var(--text-muted)]' />
            </button>
            <h2 className='text-lg font-semibold text-[var(--text-primary)]'>Markdown 编辑器</h2>
          </div>
          <div className='flex items-center gap-2'>
            <div className='flex items-center gap-1 bg-[var(--bg-input)] rounded-lg shadow-sm border border-[var(--border-primary)]/30 px-1 py-1'>
              {toolbarGroups.map((group, gi) => (
                <div key={gi} className='flex items-center'>
                  {gi > 0 && <div className='w-px h-5 bg-[var(--border-divider)] mx-0.5' />}
                  {group.map((btn) => (
                    <div
                      key={btn.label}
                      className='relative'
                      onMouseEnter={() => setToolbarTooltip(btn.label)}
                      onMouseLeave={() => setToolbarTooltip(null)}
                    >
                      <button
                        onClick={btn.action}
                        className='h-8 w-8 flex items-center justify-center rounded-md transition-colors duration-200 hover:bg-[var(--bg-toolbar-hover)] hover:text-[var(--brand-primary)] text-[var(--text-muted)] focus:outline-none'
                        aria-label={btn.label}
                      >
                        <Icon name={btn.icon} size='md' />
                      </button>
                      <AnimatePresence>
                        {toolbarTooltip === btn.label && (
                          <motion.div
                            initial={{ opacity: 0, y: -5 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: 5 }}
                            transition={{ duration: 0.15 }}
                            className='absolute top-full left-1/2 -translate-x-1/2 mt-2 px-2.5 py-1 rounded-md text-xs font-semibold text-white bg-[var(--text-primary)] whitespace-nowrap pointer-events-none z-50 shadow-lg'
                          >
                            {btn.label}
                            <span className='absolute bottom-full left-1/2 -translate-x-1/2 border-4 border-transparent border-b-gray-800' />
                          </motion.div>
                        )}
                      </AnimatePresence>
                    </div>
                  ))}
                </div>
              ))}
            </div>
            <button
              onClick={toggleMode}
              className='flex items-center gap-2 px-4 py-2 rounded-lg bg-[var(--bg-input)] text-[var(--text-secondary)] hover:bg-[var(--bg-hover)] transition-colors'
            >
              {editorMode === 'preview' ? (
                <>
                  <Icon name='Eye' size='md' />
                  预览
                </>
              ) : editorMode === 'edit' ? (
                <>
                  <Icon name='Pencil' size='md' />
                  编辑
                </>
              ) : (
                <>
                  <Icon name='Columns3' size='md' />
                  分屏
                </>
              )}
            </button>
            <button
              onClick={handleSave}
              className='flex items-center gap-2 px-5 py-2 rounded-lg bg-[var(--brand-primary)] text-white hover:brightness-110 transition-all'
            >
              <Icon name='Save' size='md' />
              保存
            </button>
          </div>
        </div>

        {/* 编辑区域 */}
        <div className='flex-1 flex overflow-hidden'>
          {editorMode !== 'preview' && (
            <div className='flex-1 flex flex-col overflow-hidden'>
              {/* 标题输入 */}
              <input
                type='text'
                value={editorTitle}
                onChange={(e) => setEditorTitle(e.target.value)}
                placeholder='输入标题...'
                className='w-full px-6 py-4 text-xl font-semibold bg-transparent text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none border-none'
              />
              {/* 内容编辑 */}
              <textarea
                ref={textareaRef}
                className='markdown-textarea flex-1 w-full px-6 py-4 bg-transparent text-[var(--text-primary)] placeholder:text-[var(--text-muted)] focus:outline-none resize-none font-mono text-sm leading-relaxed'
                value={editorContent}
                onChange={(e) => setEditorContent(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder='使用 Markdown 语法编写内容...'
                spellCheck={false}
              />
            </div>
          )}
          {editorMode !== 'edit' && (
            <div className='flex-1 overflow-y-auto px-6 py-4 bg-[var(--bg-card)]'>
              <h1 className='text-2xl font-semibold text-[var(--text-primary)] mb-4'>
                {editorTitle || '预览'}
              </h1>
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  code({ node: _node, inline, className, children, ...props }) {
                    const match = /language-(\w+)/.exec(className || '')
                    if (!inline && match && match[1].toLowerCase() === 'mermaid') {
                      return <MermaidBlock chart={String(children).replace(/\n$/, '')} />
                    }
                    return !inline && match ? (
                      <SyntaxHighlighter
                        style={oneLight}
                        language={match[1]}
                        PreTag='div'
                        {...props}
                      >
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
                      className='text-2xl font-semibold text-[var(--text-primary)] mt-6 mb-3'
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
                  table: ({ children }) => (
                    <div className='overflow-x-auto my-3 rounded-lg border border-[var(--border-divider)]'>
                      <table className='min-w-full border-collapse text-sm'>{children}</table>
                    </div>
                  ),
                  thead: ({ children }) => (
                    <thead className='bg-[var(--bg-hover)]'>{children}</thead>
                  ),
                  tbody: ({ children }) => <tbody>{children}</tbody>,
                  tr: ({ children }) => (
                    <tr className='border-b border-[var(--border-divider)] last:border-b-0 even:bg-[var(--bg-hover)]/30'>
                      {children}
                    </tr>
                  ),
                  th: ({ children }) => (
                    <th className='border-r border-[var(--border-divider)] last:border-r-0 px-4 py-2.5 text-left font-semibold text-[var(--text-primary)]'>
                      {children}
                    </th>
                  ),
                  td: ({ children }) => (
                    <td className='border-r border-[var(--border-divider)] last:border-r-0 px-4 py-2 text-[var(--text-primary)]'>
                      {children}
                    </td>
                  ),
                }}
              >
                {editorContent}
              </ReactMarkdown>
            </div>
          )}
        </div>

        <div className='px-6 py-3 border-t border-[var(--border-divider)] bg-[var(--bg-card)]'>
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
