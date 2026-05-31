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

import { ConfidenceBadge } from "@ghatana/design-system";
import {
  ContextPanel as SharedContextPanel,
  PageContent as SharedPageContent,
  PageHeader as SharedPageHeader,
  PageSection as SharedPageSection,
  SuggestionCard as SharedSuggestionCard,
} from "@ghatana/product-shell";
import { ChevronRight, Sparkles } from "lucide-react";
import React from "react";
import { bgStyles, borderStyles, cn } from "../../lib/theme";

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
  icon: _icon,
  actions,
  breadcrumbs,
  className,
}: PageHeaderProps): React.ReactElement {
  const eyebrow =
    breadcrumbs && breadcrumbs.length > 0 ? (
      <nav className="mb-2 flex items-center gap-1 text-sm">
        {breadcrumbs.map((crumb, index) => (
          <React.Fragment key={crumb.label}>
            {index > 0 && <ChevronRight className="h-4 w-4 text-gray-400" />}
            {crumb.href ? (
              <a
                href={crumb.href}
                className="text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
              >
                {crumb.label}
              </a>
            ) : (
              <span className="font-medium text-gray-900 dark:text-white">
                {crumb.label}
              </span>
            )}
          </React.Fragment>
        ))}
      </nav>
    ) : undefined;

  return (
    <div
      className={cn(
        "border-b px-6 py-4",
        bgStyles.surface,
        borderStyles.divider,
        className,
      )}
    >
      <SharedPageHeader
        title={title}
        description={subtitle}
        eyebrow={eyebrow}
        actions={actions}
        className="mb-0"
      />
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
  /** Optional right context sidebar */
  contextSidebar?: React.ReactNode;
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
  contextSidebar,
  fullWidth,
  noPadding,
  className,
}: PageContentProps): React.ReactElement {
  return (
    <SharedPageContent
      sidebar={sidebar}
      contextSidebar={contextSidebar}
      fullWidth={fullWidth}
      noPadding={noPadding}
      className={className}
    >
      {children}
    </SharedPageContent>
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
    <SharedPageSection title={title} actions={actions} className={className}>
      {children}
    </SharedPageSection>
  );
}

// =============================================================================
// CONTEXT SIDEBAR PANEL
// =============================================================================

export interface ContextPanelProps {
  /** Panel title */
  title?: string;
  /** Content */
  children: React.ReactNode;
  /** Additional class names */
  className?: string;
}

export function ContextPanel({
  title = "Assistance Panel",
  children,
  className,
}: ContextPanelProps): React.ReactElement {
  return (
    <SharedContextPanel title={title} className={className}>
      <div className="flex items-center gap-2 mb-4">
        <Sparkles className="h-4 w-4 text-purple-500" />
      </div>
      {children}
    </SharedContextPanel>
  );
}

// =============================================================================
// SUGGESTION CARD
// =============================================================================

export interface SuggestionCardProps {
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

export function SuggestionCard({
  icon,
  title,
  description,
  actionLabel = "Apply",
  onAction,
  confidence,
  className,
}: SuggestionCardProps): React.ReactElement {
  return (
    <SharedSuggestionCard
      icon={icon}
      title={title}
      description={description}
      actionLabel={actionLabel}
      onAction={onAction}
      confidence={
        confidence !== undefined ? (
          <ConfidenceBadge confidence={confidence} size="sm" showPercentage />
        ) : undefined
      }
      className={cn("border-purple-100 dark:border-purple-900/30", className)}
    />
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
  /** Smart suggestion */
  smartSuggestion?: string;
  /** Additional class names */
  className?: string;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
  smartSuggestion,
  className,
}: EmptyStateProps): React.ReactElement {
  return (
    <div className={cn("text-center py-12", className)}>
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
      {smartSuggestion && (
        <div className="inline-flex items-center gap-2 px-3 py-2 mb-4 rounded-lg bg-purple-50 dark:bg-purple-900/20">
          <Sparkles className="h-4 w-4 text-purple-500" />
          <span className="text-sm text-purple-700 dark:text-purple-300">
            {smartSuggestion}
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
    direction: "up" | "down" | "neutral";
  };
  /** Color variant */
  color?: "default" | "blue" | "green" | "red" | "yellow" | "purple";
  /** Additional class names */
  className?: string;
}

const colorVariants = {
  default: "bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400",
  blue: "bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400",
  green: "bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400",
  red: "bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400",
  yellow:
    "bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400",
  purple:
    "bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400",
};

export function StatCard({
  label,
  value,
  icon,
  trend,
  color = "default",
  className,
}: StatCardProps): React.ReactElement {
  return (
    <div
      className={cn(
        "p-4 rounded-xl",
        "bg-white dark:bg-gray-800",
        "border border-gray-200 dark:border-gray-700",
        className,
      )}
    >
      <div className="flex items-center gap-3">
        {icon && (
          <div className={cn("p-2 rounded-lg", colorVariants[color])}>
            {icon}
          </div>
        )}
        <div className="flex-1">
          <p className="text-xs text-gray-500 dark:text-gray-400">{label}</p>
          <div className="flex items-baseline gap-2">
            <p className="text-xl font-semibold text-gray-900 dark:text-white">
              {value}
            </p>
            {trend && trend.direction !== "neutral" && (
              <span
                className={cn(
                  "text-xs font-medium",
                  trend.direction === "up" && "text-green-600",
                  trend.direction === "down" && "text-red-600",
                )}
              >
                {trend.direction === "up" ? "+" : "-"}
                {Math.abs(trend.value)}%
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
  ContextPanel,
  SuggestionCard,
  EmptyState,
  StatCard,
};
