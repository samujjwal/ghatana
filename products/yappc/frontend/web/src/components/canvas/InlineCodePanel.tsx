import React, { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';

/**
 * Inline Code Panel component.
 *
 * Embedded Monaco editor at bottom of canvas.
 * Resizable, collapsible, syncs with canvas selection.
 *
 * @doc.type component
 * @doc.purpose Inline code editing panel for canvas
 * @doc.layer ui
 */

export interface InlineCodePanelProps {
  code?: string;
  language?: string;
  fileName?: string;
  isVisible?: boolean;
  onCodeChange?: (code: string) => void;
  onFormat?: () => void;
  onRun?: () => void;
  onAIFix?: () => void;
  onToggle?: () => void;
  className?: string;
}

export function InlineCodePanel({
  code = '',
  language = 'typescript',
  fileName = 'untitled.ts',
  isVisible = true,
  onCodeChange,
  onFormat,
  onRun,
  onAIFix,
  onToggle,
  className,
}: InlineCodePanelProps) {
  const [height, setHeight] = useState(300);
  const [isResizing, setIsResizing] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const editorRef = useRef<HTMLDivElement>(null);

  const handleMouseMove = React.useCallback(
    (e: MouseEvent) => {
      if (isResizing) {
        const newHeight = Math.max(
          100,
          Math.min(600, window.innerHeight - e.clientY)
        );
        setHeight(newHeight);
      }
    },
    [isResizing]
  );

  const handleMouseUp = React.useCallback(() => {
    setIsResizing(false);
  }, []);

  useEffect(() => {
    if (isResizing) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      return () => {
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [isResizing, handleMouseMove, handleMouseUp]);

  if (!isVisible) {
    return null;
  }

  if (isMinimized) {
    return (
      <div className={cn('fixed bottom-0 left-0 right-0 z-30', className)}>
        <div className="h-10 bg-gray-100 dark:bg-gray-800 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {fileName}
            </span>
            <span className="text-xs text-gray-500 dark:text-gray-500">
              {language}
            </span>
          </div>
          <button
            onClick={() => setIsMinimized(false)}
            className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 text-sm"
          >
            ▲ Expand
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      className={cn(
        'fixed bottom-0 left-0 right-0 z-30 bg-white dark:bg-gray-900',
        className
      )}
      style={{ height: `${height}px` }}
    >
      {/* Resize Handle */}
      <div
        className={cn(
          'h-1 cursor-row-resize hover:bg-blue-500 transition-colors',
          isResizing && 'bg-blue-500'
        )}
        onMouseDown={() => setIsResizing(true)}
      />

      {/* Header */}
      <div className="h-10 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between px-4">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
            {fileName}
          </span>
          <span className="text-xs text-gray-500 dark:text-gray-500">
            {language}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {onFormat && (
            <button
              onClick={onFormat}
              className="px-2 py-1 text-xs bg-gray-100 dark:bg-gray-800 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
              title="Format Code (⌘⇧F)"
            >
              Format
            </button>
          )}
          {onRun && (
            <button
              onClick={onRun}
              className="px-2 py-1 text-xs bg-green-500 hover:bg-green-600 text-white rounded"
              title="Run Code"
            >
              ▶ Run
            </button>
          )}
          {onAIFix && (
            <button
              onClick={onAIFix}
              className="px-2 py-1 text-xs bg-purple-500 hover:bg-purple-600 text-white rounded"
              title="AI Fix (⌘.)"
            >
              ✨ AI Fix
            </button>
          )}
          <button
            onClick={() => setIsMinimized(true)}
            className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 text-sm"
            title="Minimize (⌘J)"
          >
            ▼
          </button>
          {onToggle && (
            <button
              onClick={onToggle}
              className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 text-sm"
              title="Close"
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {/* Editor Area */}
      <div
        ref={editorRef}
        className="flex-1 overflow-auto bg-gray-50 dark:bg-gray-900"
        style={{ height: `${height - 44}px` }}
      >
        {/* Monaco Editor would be mounted here */}
        <div className="p-4 font-mono text-sm">
          <pre className="text-gray-900 dark:text-gray-100 whitespace-pre-wrap">
            {code || '// Select a component on canvas to view its code'}
          </pre>
        </div>
      </div>
    </div>
  );
}

/**
 * Hook for managing inline code panel state.
 *
 * @doc.type hook
 * @doc.purpose Code panel state management with keyboard shortcuts
 */
export function useInlineCodePanel() {
  const [isVisible, setIsVisible] = useState(false);
  const [code, setCode] = useState('');
  const [fileName, setFileName] = useState('untitled.ts');
  const [language, setLanguage] = useState('typescript');

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // ⌘J to toggle panel
      if ((e.metaKey || e.ctrlKey) && e.key === 'j') {
        e.preventDefault();
        setIsVisible((prev) => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleCodeChange = (newCode: string) => {
    setCode(newCode);
  };

  const handleFormat = () => {
    console.log('Format code');
  };

  const handleRun = () => {
    console.log('Run code');
  };

  const handleAIFix = () => {
    console.log('AI fix code');
  };

  const handleToggle = () => {
    setIsVisible((prev) => !prev);
  };

  return {
    isVisible,
    setIsVisible,
    code,
    setCode,
    fileName,
    setFileName,
    language,
    setLanguage,
    handleCodeChange,
    handleFormat,
    handleRun,
    handleAIFix,
    handleToggle,
  };
}
