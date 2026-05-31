import type { ReactNode } from "react";

/**
 * PageShell - Standardized page layout component
 *
 * Provides consistent page structure with:
 * - Title and summary
 * - Primary action area
 * - Table/card content area
 * - Empty/error/disabled states
 *
 * @doc.type component
 * @doc.purpose Standardized page layout
 * @doc.layer frontend
 * @doc.pattern Layout
 */
interface PageShellProps {
  title: string;
  summary?: string;
  primaryAction?: ReactNode;
  children: ReactNode;
  emptyState?: ReactNode;
  errorState?: ReactNode;
  disabledState?: ReactNode;
  isLoading?: boolean;
  isEmpty?: boolean;
  isError?: boolean;
  isDisabled?: boolean;
}

export function PageShell({
  title,
  summary,
  primaryAction,
  children,
  emptyState,
  errorState,
  disabledState,
  isLoading = false,
  isEmpty = false,
  isError = false,
  isDisabled = false,
}: PageShellProps) {
  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <h1 className="text-3xl font-bold mb-2">{title}</h1>
            {summary && <p className="text-muted-foreground">{summary}</p>}
          </div>
          {primaryAction && <div className="ml-4">{primaryAction}</div>}
        </div>
      </div>

      {isLoading && (
        <div className="rounded-md border p-8 text-center text-muted-foreground">
          Loading...
        </div>
      )}

      {isError && errorState && (
        <div className="rounded-md border border-destructive/50 bg-destructive/10 p-8 text-center text-destructive">
          {errorState}
        </div>
      )}

      {isDisabled && disabledState && (
        <div className="rounded-md border border-muted bg-muted/50 p-8 text-center text-muted-foreground">
          {disabledState}
        </div>
      )}

      {isEmpty && emptyState && !isLoading && !isError && !isDisabled && (
        <div className="rounded-md border p-8 text-center text-muted-foreground">
          {emptyState}
        </div>
      )}

      {!isLoading && !isError && !isDisabled && !isEmpty && (
        <div className="rounded-md border">{children}</div>
      )}
    </div>
  );
}
