import ReactMarkdown from 'react-markdown';
import { CodeBlock } from './CodeBlock';

interface MarkdownRendererProps {
  content: string;
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  return (
    <div className="markdown-body text-sm leading-relaxed">
      <ReactMarkdown
        components={{
          code({ node, inline, className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || '');
            return !inline && match ? (
              <CodeBlock code={String(children).replace(/\n$/, '')} language={match[1]} />
            ) : (
              <code 
                className="bg-slate-600/50 rounded px-1.5 py-0.5 text-sm font-mono text-primary-300" 
                {...props}
              >
                {children}
              </code>
            );
          },
          h1: ({ children }) => <h1 className="text-2xl font-bold mt-4 mb-2 text-white">{children}</h1>,
          h2: ({ children }) => <h2 className="text-xl font-bold mt-3 mb-2 text-white">{children}</h2>,
          h3: ({ children }) => <h3 className="text-lg font-bold mt-2 mb-1 text-white">{children}</h3>,
          p: ({ children }) => <p className="mb-3 last:mb-0 text-slate-200">{children}</p>,
          ul: ({ children }) => <ul className="list-disc list-inside mb-3 space-y-1 text-slate-200">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal list-inside mb-3 space-y-1 text-slate-200">{children}</ol>,
          li: ({ children }) => <li className="text-slate-200">{children}</li>,
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-primary-500 pl-4 my-3 italic text-slate-400">
              {children}
            </blockquote>
          ),
          a: ({ href, children }) => (
            <a href={href} className="text-primary-400 hover:text-primary-300 underline" target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ),
          strong: ({ children }) => <strong className="font-bold text-white">{children}</strong>,
          em: ({ children }) => <em className="italic text-slate-300">{children}</em>,
          hr: () => <hr className="border-slate-700 my-4" />,
          table: ({ children }) => (
            <div className="overflow-x-auto my-3">
              <table className="min-w-full border border-slate-700">{children}</table>
            </div>
          ),
          th: ({ children }) => <th className="border border-slate-700 px-3 py-2 bg-slate-700 text-white">{children}</th>,
          td: ({ children }) => <td className="border border-slate-700 px-3 py-2 text-slate-200">{children}</td>,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
