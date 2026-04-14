import React, { useState, useEffect, useRef } from 'react';
import { cn } from '@/lib/utils';
import {
  PreviewHostService,
  createSandboxProfile,
  PRESET_VIEWPORTS,
  type SandboxProfile,
  type HostToPreviewMessage,
  type PreviewToHostMessage,
} from '@ghatana/ui-builder/preview';
import type { BuilderDocument } from '@ghatana/ui-builder';

/**
 * Live Preview Panel component.
 *
 * Migrated to use platform preview protocol from @ghatana/ui-builder.
 * Provides typed message protocol between host and preview iframe.
 *
 * @doc.type component
 * @doc.purpose Live preview for Studio Mode using platform preview protocol
 * @doc.layer ui
 */

export interface LivePreviewPanelProps {
  document?: BuilderDocument;
  componentPath?: string;
  previewUrl?: string;
  onRefresh?: () => void;
  className?: string;
  viewportKey?: keyof typeof PRESET_VIEWPORTS;
  onDocumentChange?: (document: BuilderDocument) => void;
}

export function LivePreviewPanel({
  document,
  componentPath,
  previewUrl,
  onRefresh,
  className,
  viewportKey = 'desktop',
  onDocumentChange,
}: LivePreviewPanelProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const [currentViewport, setCurrentViewport] = useState(viewportKey);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const previewServiceRef = useRef<PreviewHostService | null>(null);

  // Initialize preview host service
  useEffect(() => {
    if (!iframeRef.current) return;

    const sandbox: SandboxProfile = createSandboxProfile({
      id: 'yappc-preview',
      name: 'YAPPC Preview',
      viewport: PRESET_VIEWPORTS[currentViewport],
    });

    const service = new PreviewHostService(iframeRef.current, sandbox, {
      onMounted: (msg) => {
        console.log('Preview mounted:', msg);
        setIsLoading(false);
      },
      onError: (msg) => {
        console.error('Preview error:', msg);
        setError(msg.error);
        setIsLoading(false);
      },
    });

    previewServiceRef.current = service;

    return () => {
      service.teardown();
    };
  }, [currentViewport]);

  // Mount/update document when it changes
  useEffect(() => {
    if (!document || !previewServiceRef.current) return;

    setLastUpdate(new Date());
    setIsLoading(true);
    setError(null);

    previewServiceRef.current.mountDocument(document);
  }, [document]);

  const handleRefresh = () => {
    setIsLoading(true);
    setError(null);
    if (previewServiceRef.current && document) {
      previewServiceRef.current.mountDocument(document);
    }
    onRefresh?.();
  };

  const handleViewportChange = (key: keyof typeof PRESET_VIEWPORTS) => {
    setCurrentViewport(key);
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
          {/* Viewport Selector */}
          <select
            value={currentViewport}
            onChange={(e) => handleViewportChange(e.target.value as keyof typeof PRESET_VIEWPORTS)}
            className="text-xs border border-gray-300 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
          >
            <option value="mobile">Mobile (375px)</option>
            <option value="tablet">Tablet (768px)</option>
            <option value="desktop">Desktop (1440px)</option>
            <option value="desktop-xl">Desktop XL (1920px)</option>
          </select>
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
        ) : !document && !componentPath && !previewUrl ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-gray-500 dark:text-gray-400">
              <span className="text-4xl mb-2">👁️</span>
              <p className="text-sm">Select a document or component to preview</p>
            </div>
          </div>
        ) : (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-4 min-h-[200px]">
            <iframe
              ref={iframeRef}
              src={previewUrl || 'about:blank'}
              title="Live Preview"
              className="w-full h-full border-0"
              sandbox="allow-scripts allow-same-origin allow-forms allow-modals"
            />
          </div>
        )}
      </div>
    </div>
  );
}
