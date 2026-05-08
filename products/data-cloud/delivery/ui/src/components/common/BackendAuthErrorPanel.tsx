/**
 * BackendAuthErrorPanel
 *
 * Renders an actionable error panel that distinguishes two distinct
 * backend-denied scenarios:
 *
 * - `AUTH_REQUIRED` (HTTP 401): The user's session has expired or the request
 *   carried no valid credential. Recovery: re-authenticate.
 * - `ACCESS_DENIED` (HTTP 403): The user is authenticated but the backend
 *   authorization layer denied the specific operation. Recovery: contact an
 *   administrator or request elevated access.
 *
 * Both cases display the server-echoed `correlationId` when present so that
 * users can hand it to an operator for log correlation.
 *
 * This panel is intentionally a pure-UI leaf — it holds no async state. Wire
 * it wherever an `ApiError` with code `AUTH_REQUIRED` or `ACCESS_DENIED` is
 * caught:
 *
 * ```tsx
 * if (isApiError(error) && (error.code === 'AUTH_REQUIRED' || error.code === 'ACCESS_DENIED')) {
 *   return <BackendAuthErrorPanel error={error} onRetry={refresh} />;
 * }
 * ```
 *
 * @doc.type component
 * @doc.purpose Actionable 401/403 backend-denial UX with correlation ID display
 * @doc.layer frontend
 * @doc.pattern Error Panel
 */

import React, { useCallback } from "react";
import { ShieldAlert, LogIn, Copy, CheckCircle2 } from "lucide-react";
import type { ApiError } from "../../lib/api/client";
import { cn } from "../../lib/theme";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type AuthDenialCode = "AUTH_REQUIRED" | "ACCESS_DENIED";

export interface BackendAuthErrorPanelProps {
  /** The API error object; must have code AUTH_REQUIRED or ACCESS_DENIED. */
  error: ApiError & { code: AuthDenialCode };
  /**
   * Called when the user triggers the primary recovery action (re-auth or
   * retry). If omitted the primary action button is hidden.
   */
  onRetry?: () => void;
  /**
   * Called when the user explicitly wants to go to the login / SSO page.
   * Only shown for AUTH_REQUIRED. If omitted, defaults to
   * `window.location.reload()`.
   */
  onSignIn?: () => void;
  /** Optional additional className on the root element. */
  className?: string;
}

// ---------------------------------------------------------------------------
// Copy-to-clipboard hook (self-contained; no external dep)
// ---------------------------------------------------------------------------

function useCopyToClipboard(): {
  copied: boolean;
  copy: (text: string) => void;
} {
  const [copied, setCopied] = React.useState(false);

  const copy = useCallback((text: string) => {
    void navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      setTimeout(() => {
        setCopied(false);
      }, 2000);
    });
  }, []);

  return { copied, copy };
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * Renders an actionable panel for 401 AUTH_REQUIRED and 403 ACCESS_DENIED
 * backend responses, surfacing the correlation ID for operator diagnosis.
 */
export const BackendAuthErrorPanel: React.FC<BackendAuthErrorPanelProps> =
  React.memo(({ error, onRetry, onSignIn, className }) => {
    const { copied, copy } = useCopyToClipboard();

    const isAuthRequired = error.code === "AUTH_REQUIRED";

    // ── Derived copy ──────────────────────────────────────────────────────
    const title = isAuthRequired
      ? "Session Expired"
      : "Access Denied";

    const description = isAuthRequired
      ? "Your session has expired or the server could not verify your identity. Please sign in again to continue."
      : "You are authenticated, but you do not have permission to perform this action. Contact your workspace administrator to request access.";

    const iconColor = isAuthRequired
      ? "text-amber-500"
      : "text-red-500";

    const borderColor = isAuthRequired
      ? "border-amber-200 dark:border-amber-800"
      : "border-red-200 dark:border-red-800";

    const bgColor = isAuthRequired
      ? "bg-amber-50 dark:bg-amber-950"
      : "bg-red-50 dark:bg-red-950";

    const badgeColor = isAuthRequired
      ? "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300"
      : "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300";

    const badgeLabel = isAuthRequired ? "401 Auth Required" : "403 Access Denied";

    // ── Primary action ────────────────────────────────────────────────────
    const handlePrimaryAction = useCallback(() => {
      if (isAuthRequired) {
        if (onSignIn) {
          onSignIn();
        } else {
          window.location.reload();
        }
      } else if (onRetry) {
        onRetry();
      }
    }, [isAuthRequired, onSignIn, onRetry]);

    const showPrimaryAction = isAuthRequired || Boolean(onRetry);
    const primaryLabel = isAuthRequired ? "Sign In Again" : "Retry";
    const PrimaryIcon = isAuthRequired ? LogIn : CheckCircle2;

    // ── Render ────────────────────────────────────────────────────────────
    return (
      <div
        role="alert"
        aria-live="assertive"
        className={cn(
          "rounded-lg border p-5 flex flex-col gap-4",
          bgColor,
          borderColor,
          className,
        )}
        data-testid="backend-auth-error-panel"
      >
        {/* Header row */}
        <div className="flex items-start gap-3">
          <ShieldAlert
            className={cn("h-5 w-5 mt-0.5 shrink-0", iconColor)}
            aria-hidden="true"
          />
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h2
                className="text-sm font-semibold text-gray-900 dark:text-gray-100"
                data-testid="backend-auth-error-title"
              >
                {title}
              </h2>
              <span
                className={cn(
                  "text-xs font-mono px-1.5 py-0.5 rounded",
                  badgeColor,
                )}
                aria-label={`HTTP status: ${badgeLabel}`}
              >
                {badgeLabel}
              </span>
            </div>
            <p className="mt-1 text-sm text-gray-600 dark:text-gray-400">
              {description}
            </p>
          </div>
        </div>

        {/* Correlation ID block — only when present */}
        {error.correlationId && (
          <div
            className="rounded-md bg-white/60 dark:bg-black/20 border border-gray-200 dark:border-gray-700 px-3 py-2 flex items-center justify-between gap-2"
            data-testid="correlation-id-block"
          >
            <div className="min-w-0">
              <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-0.5">
                Correlation ID
              </p>
              <p
                className="text-xs font-mono text-gray-800 dark:text-gray-200 break-all"
                data-testid="correlation-id-value"
              >
                {error.correlationId}
              </p>
            </div>
            <button
              type="button"
              onClick={() => {
                if (error.correlationId) {
                  copy(error.correlationId);
                }
              }}
              aria-label={
                copied ? "Copied!" : "Copy correlation ID to clipboard"
              }
              title={copied ? "Copied!" : "Copy"}
              className="shrink-0 p-1.5 rounded hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
              data-testid="copy-correlation-id-btn"
            >
              {copied ? (
                <CheckCircle2
                  className="h-4 w-4 text-green-600 dark:text-green-400"
                  aria-hidden="true"
                />
              ) : (
                <Copy
                  className="h-4 w-4 text-gray-500 dark:text-gray-400"
                  aria-hidden="true"
                />
              )}
            </button>
          </div>
        )}

        {/* Error message detail — only when the server returned extra context */}
        {error.message && error.message !== title && (
          <p
            className="text-xs text-gray-500 dark:text-gray-400 italic"
            data-testid="backend-auth-error-message"
          >
            Server detail: {error.message}
          </p>
        )}

        {/* Actions */}
        {showPrimaryAction && (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handlePrimaryAction}
              className={cn(
                "inline-flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors",
                isAuthRequired
                  ? "bg-amber-600 hover:bg-amber-700 text-white"
                  : "bg-red-600 hover:bg-red-700 text-white",
              )}
              data-testid="primary-action-btn"
            >
              <PrimaryIcon className="h-4 w-4" aria-hidden="true" />
              {primaryLabel}
            </button>
          </div>
        )}
      </div>
    );
  });

BackendAuthErrorPanel.displayName = "BackendAuthErrorPanel";
