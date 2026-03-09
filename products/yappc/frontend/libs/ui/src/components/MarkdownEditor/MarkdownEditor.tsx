/**
 * Markdown Editor Component
 * 
 * Rich text editor with markdown support and live preview.
 * 
 * @module ui/components
 */

import React, { useState, useCallback } from 'react';

export interface MarkdownEditorProps {
  /** Initial value */
  value?: string;
  /** Change handler */
  onChange?: (value: string) => void;
  /** Placeholder text */
  placeholder?: string;
  /** Show preview panel */
  showPreview?: boolean;
  /** Minimum height */
  minHeight?: number;
  /** Read only mode */
  readOnly?: boolean;
  /** Additional CSS classes */
  className?: string;
}

/**
 * Simple markdown to HTML converter
 */
function markdownToHtml(markdown: string): string {
  let html = markdown
    // Headers
    .replace(/^### (.*$)/gm, '<h3 class="text-lg font-semibold mt-4 mb-2">$1</h3>')
    .replace(/^## (.*$)/gm, '<h2 class="text-xl font-semibold mt-4 mb-2">$1</h2>')
    .replace(/^# (.*$)/gm, '<h1 class="text-2xl font-bold mt-4 mb-2">$1</h1>')
    // Bold and italic
    .replace(/\*\*\*(.*?)\*\*\*/g, '<strong><em>$1</em></strong>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // Code blocks
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre class="bg-zinc-800 p-3 rounded my-2 overflow-x-auto"><code>$2</code></pre>')
    // Inline code
    .replace(/`([^`]+)`/g, '<code class="bg-zinc-800 px-1 rounded text-violet-400">$1</code>')
    // Links
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" class="text-violet-400 hover:underline">$1</a>')
    // Lists
    .replace(/^\* (.*$)/gm, '<li class="ml-4">$1</li>')
    .replace(/^- (.*$)/gm, '<li class="ml-4">$1</li>')
    .replace(/^\d+\. (.*$)/gm, '<li class="ml-4 list-decimal">$1</li>')
    // Blockquotes
    .replace(/^> (.*$)/gm, '<blockquote class="border-l-4 border-zinc-600 pl-4 italic text-zinc-400">$1</blockquote>')
    // Horizontal rule
    .replace(/^---$/gm, '<hr class="border-zinc-700 my-4" />')
    // Paragraphs
    .replace(/\n\n/g, '</p><p class="my-2">')
    // Line breaks
    .replace(/\n/g, '<br />');

  return `<p class="my-2">${html}</p>`;
}

/**
 * Markdown Editor Component
 * 
 * @example
 * ```tsx
 * <MarkdownEditor
 *   value={content}
 *   onChange={setContent}
 *   showPreview={true}
 *   placeholder="Write your content..."
 * />
 * ```
 */
export const MarkdownEditor: React.FC<MarkdownEditorProps> = ({
  value = '',
  onChange,
  placeholder = 'Write markdown here...',
  showPreview = true,
  minHeight = 300,
  readOnly = false,
  className = '',
}) => {
  const [activeTab, setActiveTab] = useState<'write' | 'preview'>('write');

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange?.(e.target.value);
    },
    [onChange]
  );

  const insertMarkdown = useCallback(
    (before: string, after: string = '') => {
      const textarea = document.querySelector('textarea');
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const selectedText = value.substring(start, end);
      const newValue =
        value.substring(0, start) +
        before +
        selectedText +
        after +
        value.substring(end);

      onChange?.(newValue);
    },
    [value, onChange]
  );

  const toolbar = [
    { icon: 'B', action: () => insertMarkdown('**', '**'), title: 'Bold' },
    { icon: 'I', action: () => insertMarkdown('*', '*'), title: 'Italic' },
    { icon: 'H', action: () => insertMarkdown('## '), title: 'Heading' },
    { icon: '—', action: () => insertMarkdown('\n---\n'), title: 'Divider' },
    { icon: '•', action: () => insertMarkdown('- '), title: 'List' },
    { icon: '1.', action: () => insertMarkdown('1. '), title: 'Numbered List' },
    { icon: '""', action: () => insertMarkdown('> '), title: 'Quote' },
    { icon: '<>', action: () => insertMarkdown('`', '`'), title: 'Code' },
    { icon: '[]', action: () => insertMarkdown('[', '](url)'), title: 'Link' },
  ];

  return (
    <div className={`rounded-lg border border-zinc-700 overflow-hidden ${className}`}>
      {/* Toolbar */}
      {!readOnly && (
        <div className="flex items-center gap-1 px-2 py-1 bg-zinc-800 border-b border-zinc-700">
          {toolbar.map((item, index) => (
            <button
              key={index}
              onClick={item.action}
              title={item.title}
              className="px-2 py-1 text-sm font-mono text-zinc-400 hover:text-white hover:bg-zinc-700 rounded transition-colors"
            >
              {item.icon}
            </button>
          ))}
          <div className="flex-1" />
          <div className="flex gap-1">
            <button
              onClick={() => setActiveTab('write')}
              className={`px-3 py-1 text-sm rounded transition-colors ${
                activeTab === 'write'
                  ? 'bg-violet-600 text-white'
                  : 'text-zinc-400 hover:text-white'
              }`}
            >
              Write
            </button>
            {showPreview && (
              <button
                onClick={() => setActiveTab('preview')}
                className={`px-3 py-1 text-sm rounded transition-colors ${
                  activeTab === 'preview'
                    ? 'bg-violet-600 text-white'
                    : 'text-zinc-400 hover:text-white'
                }`}
              >
                Preview
              </button>
            )}
          </div>
        </div>
      )}

      {/* Content */}
      <div style={{ minHeight }}>
        {activeTab === 'write' ? (
          <textarea
            value={value}
            onChange={handleChange}
            placeholder={placeholder}
            readOnly={readOnly}
            className="w-full h-full p-4 bg-zinc-900 text-zinc-100 font-mono text-sm resize-none focus:outline-none"
            style={{ minHeight }}
          />
        ) : (
          <div
            className="p-4 prose prose-invert max-w-none text-zinc-100"
            style={{ minHeight }}
            dangerouslySetInnerHTML={{ __html: markdownToHtml(value) }}
          />
        )}
      </div>
    </div>
  );
};

export default MarkdownEditor;
