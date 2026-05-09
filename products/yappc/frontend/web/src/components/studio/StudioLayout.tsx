import React, { useState } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '../ui/Button';
import { useI18n } from '../../i18n/I18nProvider';

/**
 * Studio Mode Layout component.
 *
 * 4-panel layout for power users:
 * - File Tree (left)
 * - Code Editor (center)
 * - Live Preview (bottom-left)
 * - Validation (bottom-right)
 *
 * @doc.type component
 * @doc.purpose Studio mode layout for development
 * @doc.layer ui
 */

export interface StudioLayoutProps {
  fileTree?: React.ReactNode;
  codeEditor?: React.ReactNode;
  livePreview?: React.ReactNode;
  validation?: React.ReactNode;
  onClose?: () => void;
  className?: string;
}

export function StudioLayout({
  fileTree,
  codeEditor,
  livePreview,
  validation,
  onClose,
  className,
}: StudioLayoutProps) {
  const { t } = useI18n();
  const { toggleStudioMode } = useStudioMode();
  const [leftPanelWidth, setLeftPanelWidth] = useState(250);
  const [bottomPanelHeight, setBottomPanelHeight] = useState(300);
  const [isResizingLeft, setIsResizingLeft] = useState(false);
  const [isResizingBottom, setIsResizingBottom] = useState(false);

  const handleMouseMoveLeft = React.useCallback(
    (e: MouseEvent) => {
      if (isResizingLeft) {
        const newWidth = Math.max(200, Math.min(500, e.clientX));
        setLeftPanelWidth(newWidth);
      }
    },
    [isResizingLeft]
  );

  const handleMouseMoveBottom = React.useCallback(
    (e: MouseEvent) => {
      if (isResizingBottom) {
        const newHeight = Math.max(
          200,
          Math.min(600, window.innerHeight - e.clientY)
        );
        setBottomPanelHeight(newHeight);
      }
    },
    [isResizingBottom]
  );

  const handleMouseUp = React.useCallback(() => {
    setIsResizingLeft(false);
    setIsResizingBottom(false);
  }, []);

  React.useEffect(() => {
    if (isResizingLeft || isResizingBottom) {
      document.addEventListener('mousemove', handleMouseMoveLeft);
      document.addEventListener('mousemove', handleMouseMoveBottom);
      document.addEventListener('mouseup', handleMouseUp);
      return () => {
        document.removeEventListener('mousemove', handleMouseMoveLeft);
        document.removeEventListener('mousemove', handleMouseMoveBottom);
        document.removeEventListener('mouseup', handleMouseUp);
      };
    }
  }, [
    isResizingLeft,
    isResizingBottom,
    handleMouseMoveLeft,
    handleMouseMoveBottom,
    handleMouseUp,
  ]);

  const [isCompact, setIsCompact] = React.useState(window.innerWidth <= 768);

  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose?.();
      } else if ((e.metaKey || e.ctrlKey) && e.shiftKey && e.key === 's') {
        onClose?.();
      }
    };
    const handleResize = () => setIsCompact(window.innerWidth <= 768);
    document.addEventListener('keydown', handleKeyDown);
    window.addEventListener('resize', handleResize);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('resize', handleResize);
    };
  }, [onClose]);

  return (
    <div
      data-testid="studio-layout"
      aria-label={t('studio.layout')}
      className={cn('fixed inset-0 bg-white dark:bg-surface z-40', isCompact && 'studio-layout--compact', className)}
    >
      {/* Screen reader announcer */}
      <div data-testid="studio-announcer" aria-live="polite" className="sr-only" />
      {/* Header */}
      <div className="h-12 border-b border-border dark:border-border flex items-center justify-between px-4">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-fg dark:text-fg-muted">
            Studio Mode
          </span>
          <span className="text-xs text-fg-muted dark:text-fg-muted">
            ⌘⇧S to toggle
          </span>
        </div>
        <Button
          onClick={() => { toggleStudioMode(); onClose?.(); }}
          aria-label={t('studio.closeMode')}
          tabIndex={0}
          onKeyDown={(e) => { if (e.key === 'Enter') { toggleStudioMode(); onClose?.(); } }}
          className="text-fg-muted hover:text-fg dark:hover:text-fg-muted"
          variant="ghost"
          size="sm"
        >
          ✕
        </Button>
      </div>

      {/* Main Layout */}
      <div className="flex h-[calc(100vh-3rem)]">
        {/* Left Panel - File Tree */}
        <div
          data-testid="studio-file-tree"
          aria-label={t('studio.fileTreePanel')}
          className="border-r border-border dark:border-border overflow-auto"
          style={{ width: `${leftPanelWidth}px` }}
        >
          {fileTree}
        </div>

        {/* Left Resize Handle */}
        <div
          className={cn(
            'w-1 cursor-col-resize hover:bg-info-bg transition-colors',
            isResizingLeft && 'bg-info-bg'
          )}
          data-testid="resize-handle"
          onMouseDown={() => setIsResizingLeft(true)}
        />

        {/* Right Content */}
        <div className="flex-1 flex flex-col">
          {/* Top Panel - Code Editor */}
          <div
            data-testid="studio-code-editor"
            aria-label={t('studio.codeEditorPanel')}
            className="flex-1 overflow-auto"
            style={{ height: `calc(100% - ${bottomPanelHeight}px)` }}
          >
            {codeEditor}
          </div>

          {/* Bottom Resize Handle */}
          <div
            className={cn(
              'h-1 cursor-row-resize hover:bg-info-bg transition-colors',
              isResizingBottom && 'bg-info-bg'
            )}
            data-testid="resize-handle"
            onMouseDown={() => setIsResizingBottom(true)}
          />

          {/* Bottom Panels */}
          <div
            className="flex border-t border-border dark:border-border"
            style={{ height: `${bottomPanelHeight}px` }}
          >
            {/* Live Preview */}
            <div
              data-testid="studio-live-preview"
              aria-label={t('studio.livePreviewPanel')}
              className="flex-1 border-r border-border dark:border-border overflow-auto"
            >
              {livePreview}
            </div>

            {/* Validation */}
            <div
              data-testid="studio-validation"
              aria-label={t('studio.validationPanel')}
              className="flex-1 overflow-auto"
            >{validation}</div>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * Hook for managing Studio Mode state.
 *
 * @doc.type hook
 * @doc.purpose Studio mode state management
 */
export function useStudioMode() {
  const [isStudioMode, setIsStudioMode] = useState(false);

  React.useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.shiftKey && e.key === 's') {
        e.preventDefault();
        setIsStudioMode((prev) => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return {
    isStudioMode,
    setIsStudioMode,
    toggleStudioMode: () => setIsStudioMode((prev) => !prev),
  };
}
