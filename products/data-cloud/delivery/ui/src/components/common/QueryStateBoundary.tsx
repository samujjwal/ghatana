/**
 * QueryStateBoundary
 *
 * Wraps async query state into a consistent loading → error → content pattern.
 * Eliminates ad-hoc ternary chains across pages.
 *
 * @doc.type component
 * @doc.purpose Standardised async state boundary for TanStack Query results
 * @doc.layer shared
 * @doc.pattern State Boundary
 */

import React from "react";
import { ErrorState, LoadingState } from "./AsyncStates";

interface QueryStateBoundaryProps {
  /** Whether the query is currently fetching. */
  isLoading: boolean;
  /** Whether the query has encountered an error. */
  isError: boolean;
  /** The error object, used to extract a message. */
  error?: Error | null;
  /** Called when the user clicks retry. */
  onRetry?: () => void;
  /** Message shown while loading. */
  loadingMessage?: string;
  /** Title shown when an error occurs. */
  errorTitle?: string;
  /** Fallback message shown when no error message is available. */
  errorFallback?: string;
  /** Content rendered when the query succeeds. */
  children: React.ReactNode;
  /** Optional class applied to loading/error states. */
  className?: string;
}

/**
 * QueryStateBoundary — renders loading/error/content states from query flags.
 *
 * Usage:
 * ```tsx
 * <QueryStateBoundary isLoading={isLoading} isError={isError} error={error} onRetry={() => void refetch()}>
 *   <MyContent data={data} />
 * </QueryStateBoundary>
 * ```
 */
export function QueryStateBoundary({
  isLoading,
  isError,
  error,
  onRetry,
  loadingMessage = "Loading...",
  errorTitle = "Something went wrong",
  errorFallback = "An unexpected error occurred. Please try again.",
  children,
  className,
}: QueryStateBoundaryProps): React.ReactElement {
  if (isLoading) {
    return <LoadingState message={loadingMessage} className={className} />;
  }

  if (isError) {
    const message = error instanceof Error ? error.message : errorFallback;
    return (
      <ErrorState
        title={errorTitle}
        message={message}
        onRetry={onRetry}
        className={className}
      />
    );
  }

  return <>{children}</>;
}
