/**
 * Page Layout Components
 *
 * Reusable layout primitives for consistent page structure.
 * All pages should use these components for visual coherence.
 *
 * @doc.type component
 * @doc.purpose Unified page layout system
 * @doc.layer frontend
 * @doc.pattern Layout Component
 */

import React from 'react';
import { Sparkles, ChevronRight } from 'lucide-react';
import { cn, bgStyles, borderStyles, textStyles } from '../../lib/theme';

// =============================================================================
// PAGE HEADER
// =============================================================================

export interface PageHeaderProps {
  /** Page title */
  title: string;
  /** Optional subtitle/description */
  subtitle?: string;
  /** Optional icon element */
  icon?: React.ReactNode;
  /** Show AI-powered badge */
  aiPowered?: boolean;
  /** Right-side actions (buttons, etc.) */
  actions?: React.ReactNode;
  /** Breadcrumb items */
  breadcrumbs?: { label: string; href?: string }[];
  /** Additional class names */
  className?: string;
}

export function PageHeader({
  title,
  subtitle,
  icon,
  aiPowered,
  actions,
  breadcrumbs,
  className,
}: PageHeaderProps): React.ReactElement {
  return (
    <div className={cn('border-b', bgStyles.surface, borderStyles.divider, className)}>
      <div className="px-6 py-4">
        {/* Breadcrumbs */}
        {breadcrumbs && breadcrumbs.length > 0 && (
          <nav className="flex items-center gap-1 mb-2 text-sm">
            {breadcrumbs.map((crumb, index) => (
              <React.Fragment key={crumb.label}>
                {index > 0 && (
                  <ChevronRight className="h-4 w-4 text-gray-400" />
                )}
                {crumb.href ? (
                  <a
                    href={crumb.href}
                    className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
                  >
                    {crumb.label}
                  </a>
                ) : (
                  <span className="text-gray-900 dark:text-white font-medium">
                    {crumb.label}
                  </span>
                )}
              </React.Fragment>
            ))}
          </nav>
        )}

        {/* Header content */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {icon && (
              <div className="p-2 bg-primary-100 dark:bg-primary-900/30 rounded-lg">
                {icon}
              </div>
            )}
            <div>
              <div className="flex items-center gap-2">
                <h1 className={textStyles.h1}>{title}</h1>
                {aiPowered && (
                  <span className="ai-badge">
                    <Sparkles className="h-3 w-3" />
                    AI Powered
                  </span>
                )}
              </div>
              {subtitle && (
                <p className={cn(textStyles.muted, 'mt-1')}>{subtitle}</p>
              )}
            </div>
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// PAGE CONTENT
// =============================================================================

export interface PageContentProps {
  /** Main content */
  children: React.ReactNode;
  /** Optional left sidebar */
  sidebar?: React.ReactNode;
  /** Optional right AI sidebar */
  aiSidebar?: React.ReactNode;
  /** Full width (no max-width constraint) */
  fullWidth?: boolean;
  /** No padding */
  noPadding?: boolean;
  /** Additional class names */
  className?: string;
}

export function PageContent({
  children,
  sidebar,
  aiSidebar,
  fullWidth,
  noPadding,
  className,
}: PageContentProps): React.ReactElement {
  return (
    <div className={cn('flex flex-1 min-h-0', className)}>
      {/* Left Sidebar */}
      {sidebar && (
        <aside className={cn(
          'w-64 flex-shrink-0 border-r overflow-y-auto',
          bgStyles.surface,
          borderStyles.divider
        )}>
          {sidebar}
        </aside>
      )}

      {/* Main Content */}
      <main
        className={cn(
          'flex-1 overflow-y-auto',
          !noPadding && 'p-6',
          !fullWidth && 'max-w-7xl mx-auto'
        )}
      >
        {children}
      </main>

      {/* AI Sidebar */}
      {aiSidebar && (
        <aside className={cn(
          'w-80 flex-shrink-0 border-l overflow-y-auto',
          bgStyles.surfaceSecondary,
          borderStyles.divider
        )}>
          {aiSidebar}
        </aside>
      )}
    </div>
  );
}

// =============================================================================
// PAGE SECTION
// =============================================================================

export interface PageSectionProps {
  /** Section title */
  title?: string;
  /** Right side actions */
  actions?: React.ReactNode;
  /** Section content */
  children: React.ReactNode;
  /** Additional class names */
  className?: string;
}

export function PageSection({
  title,
  actions,
  children,
  className,
}: PageSectionProps): React.ReactElement {
  return (
    <section className={cn('mb-8', className)}>
      {(title || actions) && (
        <div className="flex items-center justify-between mb-4">
          {title && (
            <h2 className="text-sm font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
              {title}
            </h2>
          )}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

// =============================================================================
// AI SIDEBAR PANEL
// =============================================================================

export interface AISidebarProps {
  /** Sidebar title */
  title?: string;
  /** Content */
  children: React.ReactNode;
  /** Additional class names */
  className?: string;
}

export function AISidebar({
  title = 'AI Assistant',
  children,
  className,
}: AISidebarProps): React.ReactElement {
  return (
    <div className={cn('p-4', className)}>
      <div className="flex items-center gap-2 mb-4">
        <Sparkles className="h-4 w-4 text-purple-500" />
        <h3 className="text-sm font-semibold text-gray-900 dark:text-white">
          {title}
        </h3>
      </div>
      {children}
    </div>
  );
}

// =============================================================================
// AI SUGGESTION CARD
// =============================================================================

export interface AISuggestionProps {
  /** Suggestion icon */
  icon: React.ReactNode;
  /** Suggestion title */
  title: string;
  /** Suggestion description */
  description: string;
  /** Action button text */
  actionLabel?: string;
  /** Action callback */
  onAction?: () => void;
  /** Confidence score (0-1) */
  confidence?: number;
  /** Additional class names */
  className?: string;
}

export function AISuggestion({
  icon,
  title,
  description,
  actionLabel = 'Apply',
  onAction,
  confidence,
  className,
}: AISuggestionProps): React.ReactElement {
  return (
    <div
      className={cn(
        'p-3 rounded-lg',
        'bg-white/80 dark:bg-gray-800/80',
        'border border-purple-100 dark:border-purple-900/30',
        className
      )}
    >
      <div className="flex items-start gap-3">
        <div className="p-1.5 bg-purple-100 dark:bg-purple-900/50 rounded">
          {icon}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {title}
            </p>
            {confidence !== undefined && (
              <span className="text-xs text-gray-400">
                {Math.round(confidence * 100)}%
              </span>
            )}
          </div>
          <p className="text-xs text-gray-500 mt-0.5">{description}</p>
          {onAction && (
            <button
              onClick={onAction}
              className="mt-2 text-xs text-purple-600 dark:text-purple-400 hover:underline"
            >
              {actionLabel} →
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

// =============================================================================
// EMPTY STATE
// =============================================================================

export interface EmptyStateProps {
  /** Icon to display */
  icon: React.ReactNode;
  /** Title text */
  title: string;
  /** Description text */
  description?: string;
  /** Primary action */
  action?: {
    label: string;
    onClick: () => void;
  };
  /** AI suggestion */
  aiSuggestion?: string;
  /** Additional class names */
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  aiSuggestion,
  className,
}: EmptyStateProps): React.ReactElement {
  return (
    <div className={cn('text-center py-12', className)}>
      <div className="inline-flex items-center justify-center w-16 h-16 mb-4 rounded-full bg-gray-100 dark:bg-gray-800">
        {icon}
      </div>
      <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-1">
        {title}
      </h3>
      {description && (
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4 max-w-sm mx-auto">
          {description}
        </p>
      )}
      {aiSuggestion && (
        <div className="inline-flex items-center gap-2 px-3 py-2 mb-4 rounded-lg bg-purple-50 dark:bg-purple-900/20">
          <Sparkles className="h-4 w-4 text-purple-500" />
          <span className="text-sm text-purple-700 dark:text-purple-300">
            {aiSuggestion}
          </span>
        </div>
      )}
      {action && (
        <button
          onClick={action.onClick}
          className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}

// =============================================================================
// STAT CARD
// =============================================================================

export interface StatCardProps {
  /** Stat label */
  label: string;
  /** Stat value */
  value: string | number;
  /** Optional icon */
  icon?: React.ReactNode;
  /** Trend indicator */
  trend?: {
    value: number;
    direction: 'up' | 'down' | 'neutral';
  };
  /** Color variant */
  color?: 'default' | 'blue' | 'green' | 'red' | 'yellow' | 'purple';
  /** Additional class names */
  className?: string;
}

const colorVariants = {
  default: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  blue: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
  green: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  red: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
  yellow: 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400',
  purple: 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',
};

export function StatCard({
  label,
  value,
  icon,
  trend,
  color = 'default',
  className,
}: StatCardProps): React.ReactElement {
  return (
    <div
      className={cn(
        'p-4 rounded-xl',
        'bg-white dark:bg-gray-800',
        'border border-gray-200 dark:border-gray-700',
        className
      )}
    >
      <div className="flex items-center gap-3">
        {icon && (
          <div className={cn('p-2 rounded-lg', colorVariants[color])}>
            {icon}
          </div>
        )}
        <div className="flex-1">
          <p className="text-xs text-gray-500 dark:text-gray-400">{label}</p>
          <div className="flex items-baseline gap-2">
            <p className="text-xl font-semibold text-gray-900 dark:text-white">
              {value}
            </p>
            {trend && trend.direction !== 'neutral' && (
              <span
                className={cn(
                  'text-xs font-medium',
                  trend.direction === 'up' && 'text-green-600',
                  trend.direction === 'down' && 'text-red-600'
                )}
              >
                {trend.direction === 'up' ? '+' : '-'}{Math.abs(trend.value)}%
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default {
  PageHeader,
  PageContent,
  PageSection,
  AISidebar,
  AISuggestion,
  EmptyState,
  StatCard,
};
