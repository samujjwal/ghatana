/**
 * RolePermissionNotice Component
 *
 * Informational notice displayed when the current user's role or permission
 * scope does not allow a particular action. Surfaces the user's current view
 * mode and, optionally, the role or steps needed to request elevated access.
 *
 * This is a soft notice — it does NOT block rendering (use `RBACGuard` for
 * hard blocking). Use it to communicate view-mode restrictions inline, e.g.
 * above a read-only form or disabled control cluster.
 *
 * @doc.type component
 * @doc.purpose View-mode / permission-denied informational notice with access-request path
 * @doc.layer shared
 * @doc.pattern Informational Notice
 *
 * @example
 * ```tsx
 * <RolePermissionNotice
 *   currentRole="viewer"
 *   requiredRole="operator"
 *   featureName="trigger pipeline"
 *   requestPath="/settings/access-requests"
 * />
 * ```
 */

import { Info } from "lucide-react";
import React from "react";
import { cn } from "../../lib/theme";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface RolePermissionNoticeProps {
  /**
   * The role or access level the current user has. Displayed as the active
   * "view mode", e.g. "viewer", "operator". If omitted, the notice omits the
   * current-role sentence.
   */
  currentRole?: string;
  /**
   * The minimum role or permission level required to perform the blocked
   * action. Displayed in the notice body. If omitted, the elevated-access
   * sentence is suppressed.
   */
  requiredRole?: string;
  /**
   * Human-readable name of the feature or action being blocked, e.g.
   * "trigger pipeline", "delete collection". Included in the notice body.
   */
  featureName?: string;
  /**
   * URL or instruction for how the user can request the required access level.
   * When a string URL is provided it renders as a link. When a non-URL string
   * is provided it renders as plain instructional text.
   */
  requestPath?: string;
  /**
   * Label for the request-access link.
   * @default "Request access"
   */
  requestLabel?: string;
  /** Optional className for the notice container. */
  className?: string;
  /** Optional test id applied to the notice. */
  "data-testid"?: string;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function isUrl(value: string): boolean {
  return (
    value.startsWith("/") ||
    value.startsWith("http://") ||
    value.startsWith("https://")
  );
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

/**
 * RolePermissionNotice
 *
 * Soft informational notice. Does not block rendering; use `RBACGuard` for
 * hard-blocking permission enforcement.
 */
export const RolePermissionNotice = React.memo(function RolePermissionNotice({
  currentRole,
  requiredRole,
  featureName,
  requestPath,
  requestLabel = "Request access",
  className,
  "data-testid": testId,
}: RolePermissionNoticeProps): React.ReactElement | null {
  // Suppress if nothing meaningful to communicate
  if (!currentRole && !requiredRole && !featureName && !requestPath) {
    return null;
  }

  const featureDisplay = featureName ? ` to ${featureName}` : "";

  return (
    <div
      role="status"
      aria-live="polite"
      data-testid={testId}
      className={cn(
        "flex items-start gap-3 rounded-lg border border-neutral-200 bg-neutral-50 px-4 py-3 dark:border-neutral-700 dark:bg-neutral-800/50",
        className,
      )}
    >
      <Info
        className="mt-px h-4 w-4 shrink-0 text-neutral-500 dark:text-neutral-400"
        aria-hidden="true"
      />
      <div className="flex-1 text-sm text-neutral-700 dark:text-neutral-300">
        {/* Current view mode */}
        {currentRole && (
          <p>
            You are currently in{" "}
            <strong className="font-medium capitalize">{currentRole}</strong>{" "}
            view mode.
          </p>
        )}

        {/* Required role for action */}
        {requiredRole && (
          <p className={currentRole ? "mt-0.5" : undefined}>
            {featureDisplay ? (
              <>
                <span className="capitalize">{featureName}</span> requires{" "}
                <strong className="font-medium capitalize">
                  {requiredRole}
                </strong>{" "}
                access.
              </>
            ) : (
              <>
                This action requires{" "}
                <strong className="font-medium capitalize">
                  {requiredRole}
                </strong>{" "}
                access.
              </>
            )}
          </p>
        )}

        {/* Feature-only message when no roles provided */}
        {!currentRole && !requiredRole && featureName && (
          <p>You do not have permission{featureDisplay}.</p>
        )}

        {/* Request path */}
        {requestPath && (
          <p className="mt-1">
            {isUrl(requestPath) ? (
              <a
                href={requestPath}
                className="text-blue-600 underline underline-offset-2 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-200"
              >
                {requestLabel}
              </a>
            ) : (
              <span>{requestPath}</span>
            )}
          </p>
        )}
      </div>
    </div>
  );
});
