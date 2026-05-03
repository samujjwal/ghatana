/**
 * UnsupportedSurfaceBoundary — boundary/preview surface wrapper.
 *
 * Renders a clear, accessible notice when a route surface is not yet ready
 * (lifecycle: 'boundary') or partially implemented (lifecycle: 'preview').
 *
 * For `boundary` surfaces: renders the boundary notice inline, blocking content.
 * For `preview` surfaces: renders the preview notice as a dismissible banner
 *   above the content, allowing the content to still be accessed.
 *
 * @doc.type component
 * @doc.purpose Boundary/preview lifecycle surface wrapper
 * @doc.layer platform
 * @doc.pattern Molecule
 */
import React, { useState } from 'react';
import { AlertTriangle, Clock, ExternalLink, X } from 'lucide-react';
import type { UnsupportedSurfaceConfig } from '../types';

interface UnsupportedSurfaceBoundaryProps {
  /**
   * Surface lifecycle. 'boundary' blocks all content, 'preview' shows a dismissible banner.
   */
  lifecycle: 'boundary' | 'preview';
  /**
   * Configuration describing why this surface is unavailable/preview.
   */
  surface: UnsupportedSurfaceConfig;
  /**
   * Children rendered for `preview` lifecycle only (below the banner).
   */
  children?: React.ReactNode;
}

/**
 * Renders a boundary surface notice that blocks content (lifecycle: 'boundary')
 * or a dismissible preview banner above content (lifecycle: 'preview').
 */
export function UnsupportedSurfaceBoundary({
  lifecycle,
  surface,
  children,
}: UnsupportedSurfaceBoundaryProps): React.ReactElement {
  const [dismissed, setDismissed] = useState(false);

  if (lifecycle === 'boundary') {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex min-h-[60vh] flex-col items-center justify-center px-6 py-16 text-center"
      >
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-amber-50 dark:bg-amber-900/30">
          <AlertTriangle className="h-7 w-7 text-amber-600 dark:text-amber-400" aria-hidden="true" />
        </div>

        <h1 className="mb-2 text-xl font-semibold text-gray-900 dark:text-gray-100">
          {surface.title}
        </h1>

        <p className="mb-1 max-w-md text-sm text-gray-600 dark:text-gray-400">
          {surface.reason}
        </p>

        {surface.guidance && (
          <p className="mb-4 max-w-md text-sm text-gray-500 dark:text-gray-500">
            {surface.guidance}
          </p>
        )}

        {surface.estimatedAvailability && (
          <div className="mb-4 flex items-center gap-1.5 text-sm text-gray-500 dark:text-gray-400">
            <Clock className="h-4 w-4 shrink-0" aria-hidden="true" />
            <span>Expected: {surface.estimatedAvailability}</span>
          </div>
        )}

        {surface.docsUrl && (
          <a
            href={surface.docsUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-800"
          >
            View documentation
            <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        )}
      </div>
    );
  }

  // Preview lifecycle — dismissible banner + content
  return (
    <>
      {!dismissed && (
        <div
          role="status"
          aria-live="polite"
          className="mb-4 flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 dark:border-amber-700 dark:bg-amber-900/20"
        >
          <AlertTriangle
            className="mt-0.5 h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400"
            aria-hidden="true"
          />
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
              {surface.title} — Preview
            </p>
            <p className="mt-0.5 text-xs text-amber-700 dark:text-amber-400">
              {surface.reason}
              {surface.guidance ? ` ${surface.guidance}` : ''}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setDismissed(true)}
            aria-label="Dismiss preview notice"
            className="shrink-0 rounded p-0.5 text-amber-600 transition-colors hover:bg-amber-100 dark:text-amber-400 dark:hover:bg-amber-900/40"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      )}
      {children}
    </>
  );
}
