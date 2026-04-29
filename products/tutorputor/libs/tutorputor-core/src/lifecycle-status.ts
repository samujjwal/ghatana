/**
 * Canonical lifecycle status types shared across TutorPutor domain objects.
 *
 * @doc.type module
 * @doc.purpose Shared lifecycle status discriminants
 * @doc.layer platform
 * @doc.pattern ValueObject
 */

/**
 * Standard lifecycle status for content, experiences, and modules.
 */
export type LifecycleStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "PUBLISHED"
  | "ARCHIVED"
  | "REJECTED";

/**
 * Lifecycle status for user-facing enrollments and progress tracking.
 */
export type ProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "PAUSED";

/**
 * Lifecycle status for async background jobs and workers.
 */
export type JobStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

/**
 * Returns true if the given status represents a terminal state.
 */
export function isTerminal(status: LifecycleStatus | JobStatus): boolean {
  const terminals: Array<LifecycleStatus | JobStatus> = [
    "ARCHIVED",
    "REJECTED",
    "PUBLISHED",
    "SUCCEEDED",
    "FAILED",
    "CANCELLED",
  ];
  return terminals.includes(status);
}
