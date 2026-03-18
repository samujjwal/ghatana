import React, { useState } from 'react';
import { cn } from '@/lib/utils';

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

  return (
    <div
      className={cn('fixed inset-0 bg-white dark:bg-gray-900 z-40', className)}
    >
      {/* Header */}
      <div className="h-12 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between px-4">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
            Studio Mode
          </span>
          <span className="text-xs text-gray-500 dark:text-gray-500">
            ⌘⇧S to toggle
          </span>
        </div>
        <button
          onClick={onClose}
          className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
        >
          ✕
        </button>
      </div>

      {/* Main Layout */}
      <div className="flex h-[calc(100vh-3rem)]">
        {/* Left Panel - File Tree */}
        <div
          className="border-r border-gray-200 dark:border-gray-800 overflow-auto"
          style={{ width: `${leftPanelWidth}px` }}
        >
          {fileTree}
        </div>

        {/* Left Resize Handle */}
        <div
          className={cn(
            'w-1 cursor-col-resize hover:bg-blue-500 transition-colors',
            isResizingLeft && 'bg-blue-500'
          )}
          onMouseDown={() => setIsResizingLeft(true)}
        />

        {/* Right Content */}
        <div className="flex-1 flex flex-col">
          {/* Top Panel - Code Editor */}
          <div
            className="flex-1 overflow-auto"
            style={{ height: `calc(100% - ${bottomPanelHeight}px)` }}
          >
            {codeEditor}
          </div>

          {/* Bottom Resize Handle */}
          <div
            className={cn(
              'h-1 cursor-row-resize hover:bg-blue-500 transition-colors',
              isResizingBottom && 'bg-blue-500'
            )}
            onMouseDown={() => setIsResizingBottom(true)}
          />

          {/* Bottom Panels */}
          <div
            className="flex border-t border-gray-200 dark:border-gray-800"
            style={{ height: `${bottomPanelHeight}px` }}
          >
            {/* Live Preview */}
            <div className="flex-1 border-r border-gray-200 dark:border-gray-800 overflow-auto">
              {livePreview}
            </div>

            {/* Validation */}
            <div className="flex-1 overflow-auto">{validation}</div>
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
