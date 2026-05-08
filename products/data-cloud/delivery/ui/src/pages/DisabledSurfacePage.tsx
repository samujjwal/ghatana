/**
 * Disabled Surface Page
 *
 * Shown when a capability-gated route surface is disabled or not available
 * in the current Data Cloud configuration. Provides a clear explanation
 * and next action instead of a generic 404.
 *
 * @doc.type component
 * @doc.purpose User-facing unavailable surface page for capability-gated routes
 * @doc.layer frontend
 * @doc.pattern Page
 */

import React from 'react';
import { useNavigate } from 'react-router';
import { Lock } from 'lucide-react';
import { cn } from '../lib/theme';

export interface DisabledSurfacePageProps {
  /** Primary surface name shown to the user (e.g. "Alerts", "Memory Plane") */
  surfaceName?: string;
  /** Optional description of what this surface provides */
  surfaceDescription?: string;
  /** Optional action hint shown below the message */
  actionHint?: string;
  className?: string;
  'data-testid'?: string;
}

/**
 * Renders a meaningful "surface unavailable" state for capability-gated routes
 * that are disabled or not provisioned in the current deployment profile.
 *
 * This component is the canonical fallback for RuntimeCapabilityRouteGate
 * to avoid silent 404s for legitimately unavailable features.
 */
export const DisabledSurfacePage = React.memo(function DisabledSurfacePage({
  surfaceName = 'This surface',
  surfaceDescription,
  actionHint = 'Contact your administrator to enable this capability.',
  className,
  'data-testid': testId,
}: DisabledSurfacePageProps): React.ReactElement {
  const navigate = useNavigate();

  return (
    <div
      className={cn(
        'flex min-h-[60vh] flex-col items-center justify-center px-4 py-12 text-center',
        className
      )}
      role="status"
      aria-live="polite"
      data-testid={testId ?? 'disabled-surface-page'}
    >
      <div className="mx-auto max-w-md">
        <div className="flex justify-center">
          <span
            className={cn(
              'inline-flex h-16 w-16 items-center justify-center rounded-full',
              'bg-amber-100 dark:bg-amber-900/30'
            )}
            aria-hidden="true"
          >
            <Lock className="h-8 w-8 text-amber-600 dark:text-amber-400" />
          </span>
        </div>

        <h1 className="mt-4 text-2xl font-semibold text-gray-900 dark:text-white">
          {surfaceName} is not available
        </h1>

        {surfaceDescription && (
          <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
            {surfaceDescription}
          </p>
        )}

        <p className="mt-3 text-sm text-gray-500 dark:text-gray-500">
          This capability is not enabled in your current Data Cloud configuration.{' '}
          {actionHint}
        </p>

        <div className="mt-6 flex flex-col items-center gap-3 sm:flex-row sm:justify-center">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2',
              'text-sm font-medium text-gray-700 dark:text-gray-300',
              'bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600',
              'hover:bg-gray-50 dark:hover:bg-gray-700',
              'transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500'
            )}
          >
            Go back
          </button>
          <button
            type="button"
            onClick={() => navigate('/')}
            className={cn(
              'inline-flex items-center gap-2 rounded-lg px-4 py-2',
              'text-sm font-medium text-white',
              'bg-primary-600 hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-400',
              'transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500'
            )}
          >
            Go to Home
          </button>
        </div>
      </div>
    </div>
  );
});

DisabledSurfacePage.displayName = 'DisabledSurfacePage';
