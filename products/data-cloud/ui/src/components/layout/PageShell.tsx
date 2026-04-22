/**
 * PageShell Component
 *
 * Canonical page shell with semantic HTML, landmark regions,
 * and consistent heading hierarchy. Wraps page content with
 * accessibility-first structure.
 *
 * @doc.type component
 * @doc.purpose Standardized page wrapper with accessibility landmarks
 * @doc.layer shared
 * @doc.pattern Layout Component
 * @example
 * ```tsx
 * <PageShell title="Data Explorer" subtitle="Explore collections">
 *   <DataTable />
 * </PageShell>
 * ```
 */

import React from 'react';
import { cn } from '../../lib/theme';

interface PageShellProps {
  /** Page title (h1) */
  title: string;
  /** Optional subtitle for context */
  subtitle?: string;
  /** Optional icon displayed next to title */
  icon?: React.ReactNode;
  /** Primary action buttons or controls */
  actions?: React.ReactNode;
  /** Page content */
  children: React.ReactNode;
  /** Custom className for the outer container */
  className?: string;
  /** Whether to use full-width fluid layout */
  fluid?: boolean;
  /** Optional test id */
  'data-testid'?: string;
}

export const PageShell = React.memo(function PageShell({
  title,
  subtitle,
  icon,
  actions,
  children,
  className,
  fluid = false,
  'data-testid': testId,
}: PageShellProps) {
  return (
    <main
      className={cn(
        'flex flex-col h-full',
        className
      )}
      aria-label={title}
      data-testid={testId}
    >
      {/* Header region */}
      <header className="px-6 py-4 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <div className={cn(
          'flex items-start justify-between gap-4',
          fluid ? '' : 'max-w-7xl mx-auto'
        )}>
          <div className="min-w-0">
            <div className="flex items-center gap-3">
              {icon && (
                <div className="p-2 bg-primary-50 dark:bg-primary-900/30 rounded-lg shrink-0">
                  {icon}
                </div>
              )}
              <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                  {title}
                </h1>
                {subtitle && (
                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
                    {subtitle}
                  </p>
                )}
              </div>
            </div>
          </div>
          {actions && (
            <div className="flex items-center gap-2 shrink-0">
              {actions}
            </div>
          )}
        </div>
      </header>

      {/* Content region */}
      <div className={cn(
        'flex-1 overflow-auto p-6',
        fluid ? '' : 'max-w-7xl mx-auto w-full'
      )}>
        {children}
      </div>
    </main>
  );
});

PageShell.displayName = 'PageShell';

export default PageShell;
