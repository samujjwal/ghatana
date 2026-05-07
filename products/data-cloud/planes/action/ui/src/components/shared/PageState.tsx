/**
 * PageState — unified empty/loading/error/unavailable/degraded state component.
 *
 * Provides a single, accessible surface for every data-fetching boundary
 * in AEP. Replaces ad-hoc loading spinners and generic error strings.
 *
 * @doc.type component
 * @doc.purpose Standardized UI states for data boundaries
 * @doc.layer frontend
 * @doc.pattern State Machine
 */
import React from 'react';
import { AlertTriangle, Loader2, Info, Ban } from 'lucide-react';
import { Button } from '@ghatana/design-system';

export type PageStateMode = 'loading' | 'empty' | 'unavailable' | 'degraded' | 'zero';

interface PageStateProps {
  mode: PageStateMode;
  title?: string;
  description?: string;
  onRetry?: () => void;
  className?: string;
}

const MODE_CONFIG: Record<
  PageStateMode,
  {
    icon: React.ReactNode;
    defaultTitle: string;
    defaultDescription: string;
  }
> = {
  loading: {
    icon: <Loader2 className="h-8 w-8 text-indigo-500 animate-spin" />,
    defaultTitle: 'Loading…',
    defaultDescription: 'Fetching the latest data.',
  },
  empty: {
    icon: <Info className="h-8 w-8 text-gray-400" />,
    defaultTitle: 'No data found',
    defaultDescription: 'There are no records to display at the moment.',
  },
  unavailable: {
    icon: <Ban className="h-8 w-8 text-red-400" />,
    defaultTitle: 'Service unavailable',
    defaultDescription: 'The data source is not reachable. Please try again later.',
  },
  degraded: {
    icon: <AlertTriangle className="h-8 w-8 text-amber-500" />,
    defaultTitle: 'Partial data',
    defaultDescription: 'Some information could not be loaded. Results may be incomplete.',
  },
  zero: {
    icon: <Info className="h-8 w-8 text-gray-400" />,
    defaultTitle: 'No results',
    defaultDescription: 'The current filters returned zero matching records.',
  },
};

export function PageState({
  mode,
  title,
  description,
  onRetry,
  className = '',
}: PageStateProps): React.ReactElement {
  const config = MODE_CONFIG[mode];

  return (
    <div
      className={[
        'flex flex-col items-center justify-center text-center px-6 py-12',
        className,
      ].join(' ')}
      role={mode === 'loading' ? 'status' : 'alert'}
      aria-live={mode === 'loading' ? 'polite' : 'assertive'}
    >
      <div className="mb-3">{config.icon}</div>
      <h3 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
        {title ?? config.defaultTitle}
      </h3>
      <p className="text-xs text-gray-500 dark:text-gray-400 max-w-sm">
        {description ?? config.defaultDescription}
      </p>
      {onRetry && mode !== 'loading' && (
        <div className="mt-4">
          <Button onClick={onRetry} variant="secondary" type="button">
            Retry
          </Button>
        </div>
      )}
    </div>
  );
}
