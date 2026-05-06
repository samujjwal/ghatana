/**
 * PageLayout primitives — shared structural components for product pages.
 *
 * Products can compose these with product-specific content and product-owned
 * empty states or stat cards without re-implementing the surrounding layout.
 *
 * @doc.type component
 * @doc.purpose Shared page-level structural layout primitives for product UIs
 * @doc.layer platform
 * @doc.pattern Compound Component / Layout Primitive
 */
import React from 'react';

function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ');
}

export interface PageContentProps {
  readonly children: React.ReactNode;
  readonly sidebar?: React.ReactNode;
  readonly contextSidebar?: React.ReactNode;
  readonly fullWidth?: boolean;
  readonly noPadding?: boolean;
  readonly className?: string;
}

export function PageContent({
  children,
  sidebar,
  contextSidebar,
  fullWidth,
  noPadding,
  className,
}: PageContentProps): React.ReactElement {
  return (
    <div className={cn('flex min-h-0 flex-1', className)}>
      {sidebar ? (
        <aside className="w-64 flex-shrink-0 overflow-y-auto border-r border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900">
          {sidebar}
        </aside>
      ) : null}

      <main
        className={cn(
          'flex-1 overflow-y-auto',
          !noPadding && 'p-6',
          !fullWidth && 'mx-auto max-w-7xl',
        )}
      >
        {children}
      </main>

      {contextSidebar ? (
        <aside className="w-80 flex-shrink-0 overflow-y-auto border-l border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900/70">
          {contextSidebar}
        </aside>
      ) : null}
    </div>
  );
}

export interface PageSectionProps {
  readonly title?: string;
  readonly actions?: React.ReactNode;
  readonly children: React.ReactNode;
  readonly className?: string;
}

export function PageSection({
  title,
  actions,
  children,
  className,
}: PageSectionProps): React.ReactElement {
  return (
    <section className={cn('mb-8', className)}>
      {title || actions ? (
        <div className="mb-4 flex items-center justify-between">
          {title ? (
            <h2 className="text-sm font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {title}
            </h2>
          ) : null}
          {actions}
        </div>
      ) : null}
      {children}
    </section>
  );
}

export interface ContextPanelProps {
  readonly title?: string;
  readonly children: React.ReactNode;
  readonly className?: string;
}

export function ContextPanel({
  title = 'Assistance Panel',
  children,
  className,
}: ContextPanelProps): React.ReactElement {
  return (
    <div className={cn('p-4', className)}>
      <h3 className="mb-4 text-sm font-semibold text-gray-900 dark:text-white">
        {title}
      </h3>
      {children}
    </div>
  );
}

export interface SuggestionCardProps {
  readonly icon: React.ReactNode;
  readonly title: string;
  readonly description: string;
  readonly actionLabel?: string;
  readonly onAction?: () => void;
  readonly confidence?: React.ReactNode;
  readonly className?: string;
}

export function SuggestionCard({
  icon,
  title,
  description,
  actionLabel = 'Apply',
  onAction,
  confidence,
  className,
}: SuggestionCardProps): React.ReactElement {
  return (
    <div
      className={cn(
        'rounded-lg border border-gray-200 bg-white/90 p-3 dark:border-gray-700 dark:bg-gray-900/70',
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <div className="rounded bg-gray-100 p-1.5 text-gray-700 dark:bg-gray-800 dark:text-gray-200">
          {icon}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {title}
            </p>
            {confidence}
          </div>
          <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
            {description}
          </p>
          {onAction ? (
            <button
              type="button"
              onClick={onAction}
              className="mt-2 text-xs text-blue-600 hover:underline dark:text-blue-400"
            >
              {actionLabel} →
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
