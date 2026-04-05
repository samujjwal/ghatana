import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';

/**
 * Live Preview Panel component.
 *
 * Hot-reloading component preview for Studio Mode.
 * Shows real-time preview of selected component.
 *
 * @doc.type component
 * @doc.purpose Live preview for Studio Mode
 * @doc.layer ui
 */

export interface LivePreviewPanelProps {
  componentPath?: string;
  previewUrl?: string;
  onRefresh?: () => void;
  className?: string;
}

export function LivePreviewPanel({
  componentPath,
  previewUrl,
  onRefresh,
  className,
}: LivePreviewPanelProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());

  useEffect(() => {
    setLastUpdate(new Date());
    setIsLoading(true);
    const timer = setTimeout(() => setIsLoading(false), 500);
    return () => clearTimeout(timer);
  }, [componentPath, previewUrl]);

  const handleRefresh = () => {
    setIsLoading(true);
    setError(null);
    onRefresh?.();
    setTimeout(() => setIsLoading(false), 500);
  };

  return (
    <div
      className={cn(
        'flex flex-col h-full bg-gray-50 dark:bg-gray-900',
        className
      )}
    >
      {/* Header */}
      <div className="h-10 border-b border-gray-200 dark:border-gray-800 flex items-center justify-between px-3">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
            Live Preview
          </h3>
          {isLoading && (
            <span className="text-xs text-blue-500 animate-pulse">●</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500">
            {lastUpdate.toLocaleTimeString()}
          </span>
          <button
            onClick={handleRefresh}
            className="p-1 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 rounded"
            title="Refresh Preview"
          >
            <span className="text-sm">🔄</span>
          </button>
        </div>
      </div>

      {/* Preview Content */}
      <div className="flex-1 overflow-auto p-4">
        {error ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <span className="text-4xl mb-2">⚠️</span>
              <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
              <button
                onClick={handleRefresh}
                className="mt-4 px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
              >
                Retry
              </button>
            </div>
          </div>
        ) : !componentPath && !previewUrl ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-gray-500 dark:text-gray-400">
              <span className="text-4xl mb-2">👁️</span>
              <p className="text-sm">Select a component to preview</p>
            </div>
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-4 min-h-[200px]">
            {previewUrl ? (
              <iframe
                src={previewUrl}
                className="w-full h-full border-0"
                title="Component Preview"
                sandbox="allow-scripts allow-same-origin"
              />
            ) : (
              <div className="flex items-center justify-center h-full">
                <div className="text-center">
                  <div className="animate-pulse mb-4">
                    <div className="h-20 w-20 bg-gray-200 dark:bg-gray-700 rounded-lg mx-auto mb-4" />
                    <div className="h-4 w-32 bg-gray-200 dark:bg-gray-700 rounded mx-auto mb-2" />
                    <div className="h-4 w-24 bg-gray-200 dark:bg-gray-700 rounded mx-auto" />
                  </div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    Rendering preview...
                  </p>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Footer Info */}
      {componentPath && (
        <div className="h-8 border-t border-gray-200 dark:border-gray-800 flex items-center px-3">
          <span className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {componentPath}
          </span>
        </div>
      )}
    </div>
  );
}

/**
 * Hook for managing live preview state.
 *
 * @doc.type hook
 * @doc.purpose Live preview state management
 */
export function useLivePreview() {
  const [componentPath, setComponentPath] = useState<string | undefined>();
  const [previewUrl, setPreviewUrl] = useState<string | undefined>();

  const handleRefresh = () => {
    // Trigger preview refresh
    setPreviewUrl((prev) => (prev ? `${prev}?t=${Date.now()}` : prev));
  };

  return {
    componentPath,
    setComponentPath,
    previewUrl,
    setPreviewUrl,
    handleRefresh,
  };
}
