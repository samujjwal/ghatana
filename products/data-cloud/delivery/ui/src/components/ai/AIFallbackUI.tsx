/**
 * AI Fallback UI Component
 *
 * Displays when AI services are unavailable or confidence is too low.
 * Provides transparent disclosure of fallback mode and heuristic content.
 *
 * @doc.type component
 * @doc.purpose Transparent AI fallback state with explanation
 * @doc.layer shared
 * @doc.pattern AI Component
 * @example
 * ```tsx
 * <AIFallbackUI
 *   mode="heuristic"
 *   explanation="AI service offline — showing rule-based suggestions"
 * />
 * ```
 */

import React from 'react';
import { WifiOff, BrainCircuit, Info } from 'lucide-react';
import { cn } from '../../lib/theme';

export type AIFallbackMode = 'offline' | 'heuristic' | 'cached' | 'degraded';

interface AIFallbackUIProps {
  mode: AIFallbackMode;
  /** Human-readable explanation of fallback mode */
  explanation: string;
  /** Optional action to retry or connect */
  action?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
}

const fallbackConfig: Record<AIFallbackMode, {
  icon: React.ReactNode;
  title: string;
  badgeClass: string;
}> = {
  offline: {
    icon: <WifiOff className="h-5 w-5" aria-hidden="true" />,
    title: 'AI service offline',
    badgeClass: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  },
  heuristic: {
    icon: <BrainCircuit className="h-5 w-5" aria-hidden="true" />,
    title: 'Heuristic mode',
    badgeClass: 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  cached: {
    icon: <Info className="h-5 w-5" aria-hidden="true" />,
    title: 'Cached results',
    badgeClass: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  },
  degraded: {
    icon: <Info className="h-5 w-5" aria-hidden="true" />,
    title: 'AI service degraded',
    badgeClass: 'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300',
  },
};

export const AIFallbackUI = React.memo(function AIFallbackUI({
  mode,
  explanation,
  action,
  className,
}: AIFallbackUIProps) {
  const config = fallbackConfig[mode];

  return (
    <div
      className={cn(
        'rounded-lg border border-gray-200 dark:border-gray-700 p-4',
        'bg-white dark:bg-gray-800',
        className
      )}
      role="status"
      aria-live="polite"
    >
      <div className="flex items-start gap-3">
        <div className={cn(
          'p-2 rounded-lg shrink-0',
          config.badgeClass
        )}>
          {config.icon}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 dark:text-white">
            {config.title}
          </p>
          <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
            {explanation}
          </p>
          {action && (
            <button
              type="button"
              onClick={action.onClick}
              className={cn(
                'mt-3 inline-flex items-center gap-1.5',
                'text-sm font-medium text-primary-600 dark:text-primary-400',
                'hover:text-primary-700 dark:hover:text-primary-300',
                'transition-colors'
              )}
            >
              {action.label}
            </button>
          )}
        </div>
      </div>
    </div>
  );
});

AIFallbackUI.displayName = 'AIFallbackUI';

export default AIFallbackUI;
