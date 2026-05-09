import React, { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { Textarea } from '../ui/Textarea';
import { useI18n } from '../../i18n/I18nProvider';

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
  const { t } = useI18n();
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
        <div className="h-10 bg-surface-muted dark:bg-surface border-t border-border dark:border-border flex items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-fg dark:text-fg-muted">
              {fileName}
            </span>
            <span className="text-xs text-fg-muted dark:text-fg-muted">
              {language}
            </span>
          </div>
          <Button
            variant="ghost"
            size="small"
            onClick={() => setIsMinimized(false)}
            className="min-h-0 px-0 py-0 text-fg-muted hover:text-fg dark:hover:text-fg-muted text-sm"
          >
            ▲ Expand
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div
      data-testid="inline-code-panel"
      aria-label={t('canvas.inlineCodePanel.panel')}
      className={cn(
        'fixed bottom-0 left-0 right-0 z-30 bg-white dark:bg-surface',
        className
      )}
      style={{ height: `${height}px` }}
    >
      {/* Resize Handle */}
      <div
        className={cn(
          'h-1 cursor-row-resize hover:bg-info-bg transition-colors',
          isResizing && 'bg-info-bg'
        )}
        onMouseDown={() => setIsResizing(true)}
      />

      {/* Header */}
      <div className="h-10 border-b border-border dark:border-border flex items-center justify-between px-4">
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-fg dark:text-fg-muted">
            {fileName}
          </span>
          <span className="text-xs text-fg-muted dark:text-fg-muted">
            {language}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {onFormat && (
            <Button
              variant="soft"
              size="small"
              onClick={onFormat}
              tabIndex={0}
              className="min-h-0 px-2 py-1 text-xs bg-surface-muted dark:bg-surface hover:bg-surface-muted dark:hover:bg-surface-muted rounded"
              title={t('canvas.inlineCodePanel.format')}
            >
              Format
            </Button>
          )}
          {onRun && (
            <Button
              variant="solid"
              tone="success"
              size="small"
              onClick={onRun}
              tabIndex={0}
              className="min-h-0 px-2 py-1 text-xs bg-success-bg hover:bg-success-bg text-white rounded"
              title={t('canvas.inlineCodePanel.run')}
            >
              Run
            </Button>
          )}
          {onAIFix && (
            <Button
              variant="solid"
              tone="info"
              size="small"
              onClick={onAIFix}
              className="min-h-0 px-2 py-1 text-xs bg-info-bg hover:bg-info-bg text-white rounded"
              title={t('canvas.inlineCodePanel.guidedFix')}
            >
              ✨ Guided Fix
            </Button>
          )}
          <Button
            variant="ghost"
            size="small"
            onClick={() => setIsMinimized(true)}
            className="min-h-0 px-0 py-0 text-fg-muted hover:text-fg dark:hover:text-fg-muted text-sm"
            title={t('canvas.inlineCodePanel.minimize')}
          >
            ▼
          </Button>
          {onToggle && (
            <Button
              variant="ghost"
              size="small"
              onClick={onToggle}
              tabIndex={0}
              className="min-h-0 px-0 py-0 text-fg-muted hover:text-fg dark:hover:text-fg-muted text-sm"
              title={t('canvas.inlineCodePanel.close')}
            >
              {t('canvas.inlineCodePanel.hide')}
            </Button>
          )}
        </div>
      </div>

      {/* Editor Area */}
      <div
        ref={editorRef}
        className="flex-1 overflow-auto bg-surface-muted dark:bg-surface"
        style={{ height: `${height - 44}px` }}
      >
        {/* Code Editor */}
        <Textarea
          role="textbox"
          value={code || ''}
          onChange={(e) => onCodeChange?.(e.target.value)}
          fullWidth
          resize="none"
          className="w-full h-full p-4 font-mono text-sm bg-transparent text-fg dark:text-fg-muted resize-none outline-none"
          placeholder={t('canvas.inlineCodePanel.placeholder')}
          aria-label={t('canvas.inlineCodePanel.editor')}
        />
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
    console.log('guided fix code');
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
