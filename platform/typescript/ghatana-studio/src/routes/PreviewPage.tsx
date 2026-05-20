/**
 * @fileoverview Preview runtime page.
 *
 * Renders a sandboxed preview of Builder-generated source code using the
 * preview runtime protocol. The runtime transpiles TypeScript/TSX source,
 * injects design-system/theme providers, and renders components in a
 * sandboxed environment.
 *
 * @doc.type component
 * @doc.purpose Sandboxed preview runtime for generated source code
 * @doc.layer studio
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import type { ReactElement } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';
import { artifactPreviewSourceAtom } from '../state/artifactWorkflowStore.js';
import {
  defaultPreviewRuntime,
} from '../preview/in-memory-preview-runtime.js';
import type {
  PreviewRequest,
  PreviewResult,
} from '../preview/preview-protocol.js';

// ============================================================================
// Types
// ============================================================================

interface PreviewPageState {
  /** Generated source string to preview (HTML or JSX transpiled to HTML). */
  source?: string;
  /** MIME type of the source. Default: "text/html". */
  mimeType?: string;
  /** Human-readable title for the preview panel. */
  title?: string;
}

type PreviewStatus = 'idle' | 'loading' | 'ready' | 'error';

// ============================================================================
// Component
// ============================================================================

export default function PreviewPage(): ReactElement {
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state ?? {}) as PreviewPageState;

  // Prefer workflow store source (from artifact compile); fall back to router state
  const workflowSource = useAtomValue(artifactPreviewSourceAtom);
  const rawSource = workflowSource ?? state.source ?? null;
  const title = state.title ?? 'Preview';

  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [status, setStatus] = useState<PreviewStatus>(rawSource ? 'loading' : 'idle');
  const [previewResult, setPreviewResult] = useState<PreviewResult | null>(null);
  const [sessionId] = useState(() => crypto.randomUUID());

  // Update iframe srcdoc when preview result changes
  useEffect(() => {
    if (previewResult?.html && iframeRef.current) {
      iframeRef.current.srcdoc = previewResult.html;
    }
  }, [previewResult]);

  // Render preview using the runtime when source changes
  useEffect(() => {
    if (!rawSource) {
      setStatus('idle');
      setPreviewResult(null);
      return;
    }

    setStatus('loading');

    const request: PreviewRequest = {
      sessionId,
      source: rawSource,
      filePath: 'preview.tsx',
      designSystem: {
        packageName: '@ghatana/design-system',
        version: '1.0.0',
        availableComponents: ['Button', 'Card', 'Badge', 'Alert'],
      },
      theme: {
        mode: 'light',
      },
      securityPolicy: {
        allowScripts: true,
        allowSameOrigin: false,
        allowPopups: false,
        allowForms: false,
      },
    };

    defaultPreviewRuntime.render(request)
      .then((result) => {
        setPreviewResult(result);
        setStatus(result.success ? 'ready' : 'error');
      })
      .catch((err) => {
        setPreviewResult({
          success: false,
          error: err instanceof Error ? err.message : String(err),
          logs: [],
          duration: 0,
        });
        setStatus('error');
      });

    return () => {
      defaultPreviewRuntime.cleanup(sessionId);
    };
  }, [rawSource, sessionId]);

  const handleRefresh = useCallback(() => {
    if (rawSource) {
      setStatus('loading');
      const request: PreviewRequest = {
        sessionId: crypto.randomUUID(),
        source: rawSource,
        filePath: 'preview.tsx',
        designSystem: {
          packageName: '@ghatana/design-system',
          version: '1.0.0',
          availableComponents: ['Button', 'Card', 'Badge', 'Alert'],
        },
        theme: {
          mode: 'light',
        },
        securityPolicy: {
          allowScripts: true,
          allowSameOrigin: false,
          allowPopups: false,
          allowForms: false,
        },
      };

      defaultPreviewRuntime.render(request)
        .then((result) => {
          setPreviewResult(result);
          setStatus(result.success ? 'ready' : 'error');
        })
        .catch((err) => {
          setPreviewResult({
            success: false,
            error: err instanceof Error ? err.message : String(err),
            logs: [],
            duration: 0,
          });
          setStatus('error');
        });
    }
  }, [rawSource]);

  return (
    <section className="flex flex-col h-full space-y-0" aria-labelledby="preview-title">
      {/* Header bar */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 bg-white shrink-0">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="text-sm text-gray-600 underline hover:text-gray-900"
            aria-label="Go back"
          >
            ← Back
          </button>
          <h2 id="preview-title" className="text-base font-semibold text-gray-900">
            {title}
          </h2>
        </div>

        <div className="flex items-center gap-2">
          {/* Status badge */}
          {status === 'loading' && (
            <span className="text-xs text-gray-500" role="status" aria-live="polite">
              Loading…
            </span>
          )}
          {status === 'ready' && (
            <span className="text-xs text-green-600 font-medium" role="status">
              Ready
            </span>
          )}
          {status === 'error' && (
            <span className="text-xs text-red-600 font-medium" role="alert">
              Error
            </span>
          )}

          {/* Refresh */}
          {previewResult?.html && (
            <button
              type="button"
              onClick={handleRefresh}
              className="text-sm text-blue-600 hover:text-blue-800 underline"
              aria-label="Refresh preview"
            >
              Refresh
            </button>
          )}
        </div>
      </div>

      {/* Preview area */}
      <div className="flex-1 bg-gray-100 overflow-hidden">
        {!previewResult?.html && !previewResult?.error ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center space-y-2">
              <p className="text-sm font-medium text-gray-600">
                No preview source available
              </p>
              <p className="text-xs text-gray-400">
                Navigate here from a Builder export to see a live preview.
              </p>
            </div>
          </div>
        ) : previewResult?.error ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center space-y-2 max-w-md">
              <p className="text-sm font-medium text-red-600">
                Preview failed to render
              </p>
              <p className="text-xs text-gray-600">
                {previewResult.error}
              </p>
              {previewResult.logs.length > 0 && (
                <div className="mt-4 text-left">
                  <p className="text-xs font-medium text-gray-700 mb-2">Console logs:</p>
                  <div className="bg-gray-800 text-gray-100 text-xs p-3 rounded-md max-h-40 overflow-auto">
                    {previewResult.logs.map((log, i) => (
                      <div key={i} className="mb-1">
                        <span className="text-gray-400">[{log.level}]</span> {log.message}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        ) : (
          <iframe
            ref={iframeRef}
            title={`${title} preview`}
            className="w-full h-full border-0 bg-white"
            sandbox="allow-scripts"
            aria-label={`Sandboxed preview: ${title}`}
          />
        )}
      </div>
    </section>
  );
}
