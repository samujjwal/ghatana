import React, { useState, useEffect, useMemo, useRef } from 'react';
import { cn } from '@/lib/utils';
import {
  PreviewHostService,
  createSandboxProfile,
  PRESET_VIEWPORTS,
  resolvePreviewExecutionPolicy,
  type SandboxProfile,
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
  const [currentViewport, setCurrentViewport] = useState<keyof typeof PRESET_VIEWPORTS>(viewportKey);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const previewServiceRef = useRef<PreviewHostService | null>(null);

  const previewPolicy = useMemo(() => {
    const trustedOrigins = (() => {
      if (!previewUrl || previewUrl === 'about:blank') {
        return [] as string[];
      }

      try {
        return [new URL(previewUrl, window.location.origin).origin];
      } catch {
        return [] as string[];
      }
    })();

    const sandboxProfile: SandboxProfile = createSandboxProfile({
      id: 'yappc-preview',
      name: 'YAPPC Preview',
      viewport: PRESET_VIEWPORTS[currentViewport],
      trustedOrigins,
    });

    return resolvePreviewExecutionPolicy(document, sandboxProfile);
  }, [currentViewport, document, previewUrl]);

  // Initialize preview host service
  useEffect(() => {
    if (!iframeRef.current) return;

    const service = new PreviewHostService(iframeRef.current, previewPolicy.profile, {
      onMounted: (msg) => {
        console.log('Preview mounted:', msg);
        setIsLoading(false);
      },
      onError: (msg) => {
        console.error('Preview error:', msg);
        setError(msg.message);
        setIsLoading(false);
      },
    });

    previewServiceRef.current = service;

    return () => {
      service.teardown();
    };
  }, [previewPolicy.profile]);

  // Mount/update document when it changes
  useEffect(() => {
    if (!document || !previewServiceRef.current) return;

    setLastUpdate(new Date());
    setIsLoading(true);
    setError(previewPolicy.fallbackState?.message ?? null);

    if (previewPolicy.fallbackState) {
      setIsLoading(false);
      return;
    }

    previewServiceRef.current.mountDocument(document);
  }, [document, previewPolicy]);

  const handleRefresh = () => {
    if (previewPolicy.fallbackState) {
      setError(previewPolicy.fallbackState.message);
      setIsLoading(false);
      onRefresh?.();
      return;
    }

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
          <span className="rounded-full bg-gray-200 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-700 dark:bg-gray-700 dark:text-gray-100">
            {previewPolicy.trustLevel.replaceAll('_', ' ')}
          </span>
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

      {previewPolicy.diagnostics.length > 0 && (
        <div className="border-b border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-100">
          {previewPolicy.diagnostics.join(' ')}
        </div>
      )}

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
              sandbox={previewPolicy.sandbox.join(' ')}
              csp={previewPolicy.contentSecurityPolicy}
            />
          </div>
        )}
      </div>
    </div>
  );
}
