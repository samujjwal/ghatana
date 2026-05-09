/**
 * Status Alignment
 *
 * Centralized status vocabulary alignment across DB, API, and UI layers.
 * Ensures consistent status values for assessment and grading lifecycles.
 *
 * @doc.type module
 * @doc.purpose Status vocabulary alignment across layers
 * @doc.layer platform
 * @doc.pattern Configuration
 */

// ============================================================================
// Assessment Status
// ============================================================================

export enum AssessmentStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  ARCHIVED = "ARCHIVED",
}

export const AssessmentStatusMapping = {
  // Database values
  db: {
    DRAFT: "DRAFT",
    PUBLISHED: "PUBLISHED",
    ARCHIVED: "ARCHIVED",
  },
  // API contract values
  api: {
    DRAFT: "draft",
    PUBLISHED: "published",
    ARCHIVED: "archived",
  },
  // UI display values
  ui: {
    DRAFT: "Draft",
    PUBLISHED: "Published",
    ARCHIVED: "Archived",
  },
  // Color coding for UI
  uiColor: {
    DRAFT: "gray",
    PUBLISHED: "green",
    ARCHIVED: "red",
  },
} as const;

// ============================================================================
// Assessment Attempt Status
// ============================================================================

export enum AssessmentAttemptStatus {
  IN_PROGRESS = "IN_PROGRESS",
  SUBMITTED = "SUBMITTED",
  GRADED = "GRADED",
  PENDING_HUMAN_REVIEW = "PENDING_HUMAN_REVIEW",
  EXPIRED = "EXPIRED",
}

export const AssessmentAttemptStatusMapping = {
  // Database values
  db: {
    IN_PROGRESS: "IN_PROGRESS",
    SUBMITTED: "SUBMITTED",
    GRADED: "GRADED",
    PENDING_HUMAN_REVIEW: "PENDING_HUMAN_REVIEW",
    EXPIRED: "EXPIRED",
  },
  // API contract values
  api: {
    IN_PROGRESS: "in_progress",
    SUBMITTED: "submitted",
    GRADED: "graded",
    PENDING_HUMAN_REVIEW: "pending_human_review",
    EXPIRED: "expired",
  },
  // UI display values
  ui: {
    IN_PROGRESS: "In Progress",
    SUBMITTED: "Submitted",
    GRADED: "Graded",
    PENDING_HUMAN_REVIEW: "Pending Review",
    EXPIRED: "Expired",
  },
  // Color coding for UI
  uiColor: {
    IN_PROGRESS: "blue",
    SUBMITTED: "yellow",
    GRADED: "green",
    PENDING_HUMAN_REVIEW: "orange",
    EXPIRED: "red",
  },
  // Status transitions (allowed state changes)
  transitions: {
    IN_PROGRESS: ["SUBMITTED", "EXPIRED"],
    SUBMITTED: ["GRADED", "PENDING_HUMAN_REVIEW"],
    GRADED: [],
    PENDING_HUMAN_REVIEW: ["GRADED"],
    EXPIRED: [],
  },
} as const;

// ============================================================================
// Content Asset Status
// ============================================================================

export enum ContentAssetStatus {
  DRAFT = "DRAFT",
  PUBLISHED = "PUBLISHED",
  ARCHIVED = "ARCHIVED",
}

export const ContentAssetStatusMapping = {
  // Database values
  db: {
    DRAFT: "DRAFT",
    PUBLISHED: "PUBLISHED",
    ARCHIVED: "ARCHIVED",
  },
  // API contract values
  api: {
    DRAFT: "draft",
    PUBLISHED: "published",
    ARCHIVED: "archived",
  },
  // UI display values
  ui: {
    DRAFT: "Draft",
    PUBLISHED: "Published",
    ARCHIVED: "Archived",
  },
  // Color coding for UI
  uiColor: {
    DRAFT: "gray",
    PUBLISHED: "green",
    ARCHIVED: "red",
  },
} as const;

// ============================================================================
// Enrollment Status
// ============================================================================

export enum EnrollmentStatus {
  NOT_STARTED = "NOT_STARTED",
  IN_PROGRESS = "IN_PROGRESS",
  COMPLETED = "COMPLETED",
}

export const EnrollmentStatusMapping = {
  // Database values
  db: {
    NOT_STARTED: "NOT_STARTED",
    IN_PROGRESS: "IN_PROGRESS",
    COMPLETED: "COMPLETED",
  },
  // API contract values
  api: {
    NOT_STARTED: "not_started",
    IN_PROGRESS: "in_progress",
    COMPLETED: "completed",
  },
  // UI display values
  ui: {
    NOT_STARTED: "Not Started",
    IN_PROGRESS: "In Progress",
    COMPLETED: "Completed",
  },
  // Color coding for UI
  uiColor: {
    NOT_STARTED: "gray",
    IN_PROGRESS: "blue",
    COMPLETED: "green",
  },
  // Status transitions
  transitions: {
    NOT_STARTED: ["IN_PROGRESS"],
    IN_PROGRESS: ["COMPLETED"],
    COMPLETED: [],
  },
} as const;

// ============================================================================
// Review Status
// ============================================================================

export enum ReviewStatus {
  PENDING = "PENDING",
  IN_REVIEW = "IN_REVIEW",
  APPROVED = "APPROVED",
  REJECTED = "REJECTED",
  CHANGES_REQUESTED = "CHANGES_REQUESTED",
}

export const ReviewStatusMapping = {
  // Database values
  db: {
    PENDING: "PENDING",
    IN_REVIEW: "IN_REVIEW",
    APPROVED: "APPROVED",
    REJECTED: "REJECTED",
    CHANGES_REQUESTED: "CHANGES_REQUESTED",
  },
  // API contract values
  api: {
    PENDING: "pending",
    IN_REVIEW: "in_review",
    APPROVED: "approved",
    REJECTED: "rejected",
    CHANGES_REQUESTED: "changes_requested",
  },
  // UI display values
  ui: {
    PENDING: "Pending",
    IN_REVIEW: "In Review",
    APPROVED: "Approved",
    REJECTED: "Rejected",
    CHANGES_REQUESTED: "Changes Requested",
  },
  // Color coding for UI
  uiColor: {
    PENDING: "gray",
    IN_REVIEW: "blue",
    APPROVED: "green",
    REJECTED: "red",
    CHANGES_REQUESTED: "orange",
  },
  // Status transitions
  transitions: {
    PENDING: ["IN_REVIEW"],
    IN_REVIEW: ["APPROVED", "REJECTED", "CHANGES_REQUESTED"],
    APPROVED: [],
    REJECTED: ["PENDING"],
    CHANGES_REQUESTED: ["PENDING"],
  },
} as const;

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Convert DB status to API status
 */
export function dbToApiStatus<T extends keyof typeof AssessmentStatusMapping.db>(
  status: T,
  mapping: typeof AssessmentStatusMapping | typeof AssessmentAttemptStatusMapping,
): string {
  const apiMapping = mapping.api as Record<string, string>;
  return apiMapping[status] || status.toLowerCase();
}

/**
 * Convert API status to DB status
 */
export function apiToDbStatus(
  status: string,
  mapping: typeof AssessmentStatusMapping | typeof AssessmentAttemptStatusMapping,
): string {
  const dbMapping = mapping.db as Record<string, string>;
  const upperStatus = status.toUpperCase();
  return dbMapping[upperStatus as keyof typeof dbMapping] || upperStatus;
}

/**
 * Convert DB status to UI display label
 */
export function dbToUiLabel<T extends keyof typeof AssessmentStatusMapping.db>(
  status: T,
  mapping: typeof AssessmentStatusMapping | typeof AssessmentAttemptStatusMapping,
): string {
  const uiMapping = mapping.ui as Record<string, string>;
  return uiMapping[status] || status;
}

/**
 * Get UI color for a status
 */
export function getUiColor<T extends keyof typeof AssessmentStatusMapping.db>(
  status: T,
  mapping: typeof AssessmentStatusMapping | typeof AssessmentAttemptStatusMapping,
): string {
  const colorMapping = mapping.uiColor as Record<string, string>;
  return colorMapping[status] || "gray";
}

/**
 * Check if a status transition is allowed
 */
export function isTransitionAllowed(
  fromStatus: string,
  toStatus: string,
  mapping: typeof AssessmentAttemptStatusMapping | typeof ReviewStatusMapping | typeof EnrollmentStatusMapping,
): boolean {
  const transitions = mapping.transitions as unknown as Record<string, readonly string[]>;
  const allowed = transitions[fromStatus] || [];
  return allowed.includes(toStatus);
}

/**
 * Get all allowed transitions from a status
 */
export function getAllowedTransitions(
  fromStatus: string,
  mapping: typeof AssessmentAttemptStatusMapping | typeof ReviewStatusMapping | typeof EnrollmentStatusMapping,
): string[] {
  const transitions = mapping.transitions as unknown as Record<string, readonly string[]>;
  return [...(transitions[fromStatus] || [])];
}

/**
 * Validate status against allowed values
 */
export function isValidStatus(
  status: string,
  allowedStatuses: readonly string[],
): boolean {
  return allowedStatuses.includes(status);
}

// ============================================================================
// Type Guards
// ============================================================================

export function isAssessmentStatus(status: string): status is AssessmentStatus {
  return Object.values(AssessmentStatus).includes(status as AssessmentStatus);
}

export function isAssessmentAttemptStatus(status: string): status is AssessmentAttemptStatus {
  return Object.values(AssessmentAttemptStatus).includes(status as AssessmentAttemptStatus);
}

export function isEnrollmentStatus(status: string): status is EnrollmentStatus {
  return Object.values(EnrollmentStatus).includes(status as EnrollmentStatus);
}

export function isReviewStatus(status: string): status is ReviewStatus {
  return Object.values(ReviewStatus).includes(status as ReviewStatus);
}
