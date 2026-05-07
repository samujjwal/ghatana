import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Button, Select } from '@ghatana/design-system';
import { cn } from '@/lib/utils';
import {
  PreviewHostService,
  createSandboxProfile,
  PRESET_VIEWPORTS,
  resolvePreviewExecutionPolicy,
  type SandboxProfile,
} from '@ghatana/ui-builder/preview';
import type { BuilderDocument } from '@ghatana/ui-builder';
import { getPreviewSync } from '@/services/canvas/preview/BidirectionalPreviewSync';
import {
  issuePreviewSession,
  type PreviewSessionContext,
} from '@/services/preview/PreviewSessionApi';

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
  previewContext?: PreviewSessionContext;
  onRefresh?: () => void;
  className?: string;
  viewportKey?: keyof typeof PRESET_VIEWPORTS;
  onDocumentChange?: (document: BuilderDocument) => void;
  validation?: {
    readonly valid: boolean;
    readonly errorCount: number;
    readonly warningCount: number;
  };
  /** The currently selected node ID in the builder canvas — sent to the preview to highlight it. */
  selectedNodeId?: string | null;
  /** Fired when the user clicks a node in the preview iframe. */
  onElementClick?: (nodeId: string) => void;
  /** Fired when the user hovers over a node in the preview iframe (null = hover cleared). */
  onElementHover?: (nodeId: string | null) => void;
}

export function LivePreviewPanel({
  document,
  componentPath,
  previewUrl,
  previewContext,
  onRefresh,
  className,
  viewportKey = 'desktop',
  onDocumentChange,
  validation,
  selectedNodeId,
  onElementClick,
  onElementHover,
}: LivePreviewPanelProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const [currentViewport, setCurrentViewport] = useState<keyof typeof PRESET_VIEWPORTS>(viewportKey);
  const [currentTheme, setCurrentTheme] = useState('default');
  const [currentLocale, setCurrentLocale] = useState('en-US');
  const [resolvedPreviewUrl, setResolvedPreviewUrl] = useState<string | null>(previewUrl ?? null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const previewServiceRef = useRef<PreviewHostService | null>(null);
  const mountedDocumentIdRef = useRef<string | null>(null);
  const updateTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const previewPolicy = useMemo(() => {
    // Default to the built-in same-origin preview runtime so postMessage can flow.
    const effectivePreviewUrl = resolvedPreviewUrl ?? previewUrl ?? '/preview/builder';

    const trustedOrigins = (() => {
      try {
        return [new URL(effectivePreviewUrl, window.location.origin).origin];
      } catch {
        return [] as string[];
      }
    })();

    const sandboxProfile: SandboxProfile = createSandboxProfile({
      id: 'yappc-preview',
      name: 'YAPPC Preview',
      viewport: PRESET_VIEWPORTS[currentViewport],
      theme: currentTheme,
      locale: currentLocale,
      trustedOrigins,
    });

    return resolvePreviewExecutionPolicy(document, sandboxProfile);
  }, [currentLocale, currentTheme, currentViewport, document, previewUrl, resolvedPreviewUrl]);

  useEffect(() => {
    let cancelled = false;

    if (previewUrl) {
      setResolvedPreviewUrl(previewUrl);
      return () => {
        cancelled = true;
      };
    }

    if (!previewContext) {
      setResolvedPreviewUrl('/preview/builder');
      return () => {
        cancelled = true;
      };
    }

    setIsLoading(true);
    setError(null);

    void issuePreviewSession(previewContext)
      .then(({ sessionToken }) => {
        if (cancelled) {
          return;
        }
        setResolvedPreviewUrl(`/preview/builder?session=${encodeURIComponent(sessionToken)}`);
      })
      .catch((err: unknown) => {
        if (cancelled) {
          return;
        }
        setError(err instanceof Error ? err.message : 'Failed to prepare preview session.');
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [previewContext, previewUrl]);

  // Initialize preview host service
  useEffect(() => {
    if (!iframeRef.current) return;

    const previewSync = getPreviewSync();

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
      onElementClick: (msg) => {
        previewSync.handlePreviewClick(msg.nodeId);
        onElementClick?.(msg.nodeId);
      },
      onElementHover: (msg) => {
        if (msg.nodeId) {
          previewSync.handlePreviewHover(msg.nodeId);
        } else {
          previewSync.clearHover();
        }
        onElementHover?.(msg.nodeId);
      },
    });

    previewServiceRef.current = service;

    return () => {
      service.teardown();
    };
  }, [previewPolicy.profile]);

  useEffect(() => {
    if (!iframeRef.current) {
      return;
    }

    iframeRef.current.setAttribute('csp', previewPolicy.contentSecurityPolicy);
  }, [previewPolicy.contentSecurityPolicy]);

  // Send SELECT_NODE to preview when canvas selection changes
  useEffect(() => {
    if (!previewServiceRef.current || !previewPolicy.profile.trustedOrigins[0]) return;
    const previewSync = getPreviewSync();
    if (selectedNodeId) {
      previewSync.handleCanvasClick(selectedNodeId);
    } else {
      previewSync.clearSelection();
    }
    previewServiceRef.current.send({ type: 'SELECT_NODE', nodeId: selectedNodeId ?? null });
  }, [selectedNodeId, previewPolicy.profile.trustedOrigins]);

  // Mount/update document when it changes
  useEffect(() => {
    if (!document || !previewServiceRef.current) return;

    setLastUpdate(new Date());
    setError(previewPolicy.fallbackState?.message ?? null);

    if (previewPolicy.fallbackState) {
      setIsLoading(false);
      return;
    }

    if (mountedDocumentIdRef.current !== document.id) {
      setIsLoading(true);
      mountedDocumentIdRef.current = document.id;
      void previewServiceRef.current.mountDocument(document);
      return;
    }

    if (updateTimeoutRef.current) {
      clearTimeout(updateTimeoutRef.current);
    }

    updateTimeoutRef.current = setTimeout(() => {
      setIsLoading(true);
      void previewServiceRef.current?.updateDocument(document);
    }, 250);
  }, [document, previewPolicy]);

  useEffect(() => {
    return () => {
      if (updateTimeoutRef.current) {
        clearTimeout(updateTimeoutRef.current);
      }
    };
  }, []);

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
      mountedDocumentIdRef.current = document.id;
      void previewServiceRef.current.mountDocument(document);
    }
    onRefresh?.();
  };

  const handleViewportChange = (key: keyof typeof PRESET_VIEWPORTS) => {
    setCurrentViewport(key);
  };

  const createPreviewCorrelationId = (operation: string): string => {
    return `live-preview-${operation}-${Date.now()}`;
  };

  useEffect(() => {
    if (!previewServiceRef.current || previewPolicy.fallbackState) {
      return;
    }

    previewServiceRef.current.send({
      type: 'SET_VIEWPORT',
      viewport: PRESET_VIEWPORTS[currentViewport],
      correlationId: createPreviewCorrelationId('viewport'),
    });
    previewServiceRef.current.send({
      type: 'SET_THEME',
      theme: currentTheme,
      correlationId: createPreviewCorrelationId('theme'),
    });
    previewServiceRef.current.send({
      type: 'SET_LOCALE',
      locale: currentLocale,
      correlationId: createPreviewCorrelationId('locale'),
    });
  }, [currentLocale, currentTheme, currentViewport, previewPolicy.fallbackState, previewPolicy.profile]);

  const currentViewportSpec = PRESET_VIEWPORTS[currentViewport];

  return (
    <div
      className={cn(
        'flex flex-col h-full bg-surface-muted dark:bg-surface',
        className
      )}
    >
      {/* Header */}
      <div className="h-10 border-b border-border dark:border-border flex items-center justify-between px-3">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-fg dark:text-fg-muted">
            Live Preview
          </h3>
          <span className="rounded-full bg-surface-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-fg dark:bg-surface-muted dark:text-fg-muted">
            {previewPolicy.trustLevel.replaceAll('_', ' ')}
          </span>
          {isLoading && (
            <span className="text-xs text-info-color animate-pulse">●</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {/* Viewport Selector */}
          <Select
            value={currentViewport}
            onChange={(e) => handleViewportChange(e.target.value as keyof typeof PRESET_VIEWPORTS)}
            className="text-xs"
            options={[
              { value: 'mobile', label: 'Mobile (375px)' },
              { value: 'tablet', label: 'Tablet (768px)' },
              { value: 'desktop', label: 'Desktop (1440px)' },
              { value: 'desktop-xl', label: 'Desktop XL (1920px)' },
            ]}
          />
          <Select
            value={currentTheme}
            onChange={(e) => setCurrentTheme(e.target.value)}
            className="text-xs"
            options={[
              { value: 'default', label: 'Default theme' },
              { value: 'contrast', label: 'High contrast' },
            ]}
          />
          <Select
            value={currentLocale}
            onChange={(e) => setCurrentLocale(e.target.value)}
            className="text-xs"
            options={[
              { value: 'en-US', label: 'en-US' },
              { value: 'en-GB', label: 'en-GB' },
            ]}
          />
          <span className="text-xs text-fg-muted">
            {lastUpdate.toLocaleTimeString()}
          </span>
          <Button
            onClick={handleRefresh}
            variant="outline"
            size="small"
            title="Refresh Preview"
          >
            Refresh
          </Button>
        </div>
      </div>

      {previewPolicy.diagnostics.length > 0 && (
        <div className="border-b border-warning-border bg-warning-bg px-3 py-2 text-xs text-warning-color dark:border-warning-border/50 dark:bg-warning-bg/40 dark:text-warning-color">
          {previewPolicy.diagnostics.join(' ')}
        </div>
      )}

      {validation && !validation.valid ? (
        <div className="border-b border-destructive-border bg-destructive-bg px-3 py-2 text-xs text-destructive dark:border-destructive-border/50 dark:bg-destructive-bg/40 dark:text-destructive">
          Preview blocked by validation: {validation.errorCount} error(s), {validation.warningCount} warning(s).
        </div>
      ) : null}

      {/* Preview Content */}
      <div className="flex-1 overflow-auto p-4">
        {error || (validation && !validation.valid) ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <span className="text-4xl mb-2">⚠️</span>
              <p className="text-sm text-destructive dark:text-destructive">
                {error ?? 'Preview is paused until validation errors are resolved.'}
              </p>
              <Button
                onClick={handleRefresh}
                className="mt-4"
                variant="solid"
                size="small"
              >
                Retry
              </Button>
            </div>
          </div>
        ) : !document && !componentPath && !previewUrl && !previewContext ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-fg-muted dark:text-fg-muted">
              <span className="text-4xl mb-2">👁️</span>
              <p className="text-sm">Select a document or component to preview</p>
            </div>
          </div>
        ) : previewContext && !previewUrl && !resolvedPreviewUrl ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center text-fg-muted dark:text-fg-muted">
              <span className="text-4xl mb-2">🔐</span>
              <p className="text-sm">Preparing secure preview session…</p>
            </div>
          </div>
        ) : (
          <div className="bg-white dark:bg-surface rounded-lg shadow-sm border border-border dark:border-border p-4 min-h-[200px] overflow-auto">
            <iframe
              ref={iframeRef}
              src={resolvedPreviewUrl ?? previewUrl ?? '/preview/builder'}
              title="Live Preview"
              className="h-full max-w-full border-0 transition-[width,height] duration-200 ease-out"
              style={{
                width: `${currentViewportSpec.width}px`,
                minHeight: `${currentViewportSpec.height}px`,
              }}
              sandbox={previewPolicy.sandbox.join(' ')}
            />
          </div>
        )}
      </div>
    </div>
  );
}
