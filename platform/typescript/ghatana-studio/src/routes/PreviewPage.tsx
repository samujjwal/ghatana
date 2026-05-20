/**
 * @fileoverview Preview runtime page.
 *
 * Renders a sandboxed preview of Builder-generated source code inside an
 * isolated `<iframe>` so that arbitrary component output cannot access the
 * Studio's own DOM or JavaScript context.
 *
 * The source to preview is passed via React Router state:
 * `navigate("/preview", { state: { source, mimeType } })`
 *
 * @doc.type component
 * @doc.purpose Sandboxed preview runtime for generated source code
 * @doc.layer studio
 */

import { useState, useCallback, useRef } from 'react';
import type { ReactElement } from 'react';
import { useLocation, useNavigate } from 'react-router';

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
// Helpers
// ============================================================================

/**
 * Wrap a raw HTML fragment in a minimal document shell so the iframe can
 * render it correctly.
 */
function wrapInDocumentShell(source: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Preview</title>
  <style>body { margin: 0; font-family: system-ui, sans-serif; }</style>
</head>
<body>
${source}
</body>
</html>`;
}

// ============================================================================
// Component
// ============================================================================

export default function PreviewPage(): ReactElement {
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state ?? {}) as PreviewPageState;

  const rawSource = state.source ?? null;
  const mimeType = state.mimeType ?? 'text/html';
  const title = state.title ?? 'Preview';

  const iframeRef = useRef<HTMLIFrameElement>(null);
  const [status, setStatus] = useState<PreviewStatus>(rawSource ? 'loading' : 'idle');

  // Convert source to a `srcdoc` value or a Blob URL
  const srcdoc =
    rawSource !== null
      ? mimeType === 'text/html'
        ? wrapInDocumentShell(rawSource)
        : rawSource
      : null;

  const handleIframeLoad = useCallback(() => {
    setStatus('ready');
  }, []);

  const handleIframeError = useCallback(() => {
    setStatus('error');
  }, []);

  const handleRefresh = useCallback(() => {
    if (iframeRef.current !== null && srcdoc !== null) {
      setStatus('loading');
      // Force reload by briefly clearing srcdoc — iframe will re-render
      iframeRef.current.srcdoc = '';
      requestAnimationFrame(() => {
        if (iframeRef.current !== null) {
          iframeRef.current.srcdoc = srcdoc;
        }
      });
    }
  }, [srcdoc]);

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
          {srcdoc !== null && (
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
        {srcdoc === null ? (
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
        ) : (
          <iframe
            ref={iframeRef}
            srcDoc={srcdoc}
            title={`${title} preview`}
            className="w-full h-full border-0 bg-white"
            sandbox="allow-scripts"
            onLoad={handleIframeLoad}
            onError={handleIframeError}
            aria-label={`Sandboxed preview: ${title}`}
          />
        )}
      </div>
    </section>
  );
}
