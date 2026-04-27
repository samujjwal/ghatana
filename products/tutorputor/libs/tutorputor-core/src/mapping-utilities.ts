/**
 * Shared Mapping Utilities
 * 
 * Extracts duplicate mapping functions found across modules.
 * Provides consistent data transformation patterns.
 * 
 * @doc.type utility
 * @doc.purpose Data mapping and transformation
 * @doc.layer platform
 */

import type {
  ModuleId,
  EnrollmentId,
  UserId,
  TenantId,
  ModuleSummary,
  Enrollment,
  EnrollmentStatus,
} from '@tutorputor/contracts';

type MaybeDate = Date | string | null | undefined;

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

function asNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' ? value : fallback;
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : [];
}

function toIso(value: MaybeDate, fallback = new Date().toISOString()): string {
  if (!value) {
    return fallback;
  }
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === 'string') {
    return value;
  }
  return fallback;
}

/**
 * Maps database module to ModuleSummary contract type
 */
export function mapModuleSummary(module: Record<string, unknown>): ModuleSummary {
  return {
    id: asString(module.id) as ModuleId,
    slug: asString(module.slug),
    title: asString(module.title),
    domain: (asString(module.domain, 'MATH') as ModuleSummary['domain']),
    difficulty: (asString(module.difficulty, 'beginner') as ModuleSummary['difficulty']),
    estimatedTimeMinutes: asNumber(module.estimatedTimeMinutes ?? module.estimatedMinutes),
    tags: asStringArray(module.tags),
    status: (asString(module.status, 'draft') as ModuleSummary['status']),
    progressPercent:
      typeof module.progressPercent === 'number' ? module.progressPercent : undefined,
    publishedAt:
      typeof module.publishedAt === 'string'
        ? module.publishedAt
        : undefined,
  };
}

/**
 * Maps database enrollment to Enrollment contract type
 */
export function mapEnrollment(enrollment: Record<string, unknown>): Enrollment {
  return {
    id: asString(enrollment.id) as EnrollmentId,
    moduleId: asString(enrollment.moduleId) as ModuleId,
    userId: asString(enrollment.userId) as UserId,
    status: mapEnrollmentStatus(asString(enrollment.status, 'NOT_STARTED')),
    progressPercent: asNumber(enrollment.progressPercent ?? enrollment.progress),
    moduleSlug:
      typeof enrollment.moduleSlug === 'string' ? enrollment.moduleSlug : undefined,
    moduleTitle:
      typeof enrollment.moduleTitle === 'string' ? enrollment.moduleTitle : undefined,
    timeSpentSeconds: asNumber(enrollment.timeSpentSeconds),
    startedAt: enrollment.startedAt ? toIso(enrollment.startedAt as MaybeDate) : undefined,
    completedAt: enrollment.completedAt ? toIso(enrollment.completedAt as MaybeDate) : undefined,
  };
}

/**
 * Maps database enrollment status to contract type
 */
export function mapEnrollmentStatus(status: string): EnrollmentStatus {
  const validStatuses: EnrollmentStatus[] = [
    'NOT_STARTED',
    'IN_PROGRESS',
    'COMPLETED',
  ];
  
  const upperStatus = status.toUpperCase();
  return validStatuses.includes(upperStatus as EnrollmentStatus)
    ? (upperStatus as EnrollmentStatus)
    : 'NOT_STARTED';
}

/**
 * Maps user role from database to contract type
 */
export function mapUserRole(role: string): 'student' | 'teacher' | 'creator' | 'admin' {
  const validRoles = ['student', 'teacher', 'creator', 'admin'];
  return validRoles.includes(role) ? (role as any) : 'student';
}

/**
 * Maps assessment attempt to contract type
 */
export function mapAssessmentAttempt(attempt: Record<string, unknown>): any {
  return {
    id: attempt.id,
    assessmentId: attempt.assessmentId,
    userId: attempt.userId,
    tenantId: attempt.tenantId,
    score: attempt.score || 0,
    maxScore: attempt.maxScore || 100,
    passed: attempt.passed || false,
    startedAt: toIso(attempt.startedAt as MaybeDate),
    completedAt: attempt.completedAt ? toIso(attempt.completedAt as MaybeDate) : undefined,
    timeSpentSeconds: attempt.timeSpentSeconds || 0,
    answers: attempt.answers || [],
  };
}

/**
 * Maps learning event to contract type
 */
export function mapLearningEvent(event: Record<string, unknown>): any {
  return {
    id: event.id,
    userId: event.userId,
    tenantId: event.tenantId,
    moduleId: event.moduleId,
    eventType: event.eventType,
    timestamp: toIso(event.timestamp as MaybeDate),
    metadata: event.metadata || {},
  };
}

/**
 * Maps classroom to contract type
 */
export function mapClassroom(classroom: Record<string, unknown>): any {
  return {
    id: classroom.id,
    tenantId: classroom.tenantId,
    name: classroom.name,
    description: classroom.description || '',
    teacherId: classroom.teacherId,
    studentCount: classroom.studentCount || 0,
    createdAt: toIso(classroom.createdAt as MaybeDate),
    updatedAt: toIso(classroom.updatedAt as MaybeDate),
  };
}

/**
 * Maps notification to contract type
 */
export function mapNotification(notification: Record<string, unknown>): any {
  return {
    id: notification.id,
    userId: notification.userId,
    tenantId: notification.tenantId,
    type: notification.type,
    title: notification.title,
    message: notification.message,
    read: notification.read || false,
    createdAt: toIso(notification.createdAt as MaybeDate),
    metadata: notification.metadata || {},
  };
}

/**
 * Maps pagination parameters from request to database query
 */
export function mapPaginationParams(params: Record<string, unknown>): {
  skip: number;
  take: number;
  cursor?: { id: string };
} {
  const limit = Math.min(asNumber(params.limit, 50), 200);
  const cursor = typeof params.cursor === 'string' ? params.cursor : undefined;

  return {
    skip: cursor ? 1 : 0,
    take: limit + 1, // Take one extra to check if there are more
    cursor: cursor ? { id: cursor } : undefined,
  };
}

/**
 * Extracts pagination metadata from query results
 */
export function extractPaginationMetadata<T extends { id: string }>(
  items: T[],
  limit: number,
): {
  items: T[];
  hasMore: boolean;
  nextCursor?: string;
} {
  const hasMore = items.length > limit;
  const resultItems = hasMore ? items.slice(0, -1) : items;
  const nextCursor = hasMore ? resultItems[resultItems.length - 1]?.id : undefined;

  return {
    items: resultItems,
    hasMore,
    nextCursor,
  };
}

/**
 * Maps sort parameters from request to database orderBy
 */
export function mapSortParams(
  sortBy?: string,
  sortOrder?: 'asc' | 'desc',
): Record<string, 'asc' | 'desc'> {
  if (!sortBy) {
    return { createdAt: 'desc' };
  }

  return { [sortBy]: sortOrder || 'asc' };
}

/**
 * Safely parses JSON metadata fields
 */
export function parseMetadata<T = Record<string, any>>(
  metadata: unknown,
  defaultValue: T = {} as T,
): T {
  if (!metadata) {
    return defaultValue;
  }

  if (typeof metadata === 'string') {
    try {
      return JSON.parse(metadata) as T;
    } catch {
      return defaultValue;
    }
  }

  return metadata as T;
}

/**
 * Formats duration in seconds to human-readable string
 */
export function formatDuration(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m`;
  }

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
}

/**
 * Calculates progress percentage
 */
export function calculateProgress(completed: number, total: number): number {
  if (total === 0) {
    return 0;
  }
  return Math.round((completed / total) * 100);
}

/**
 * Maps database timestamp to ISO string
 */
export function mapTimestamp(date: Date | string | null | undefined): string {
  if (!date) {
    return new Date().toISOString();
  }
  
  if (typeof date === 'string') {
    return date;
  }
  
  return date.toISOString();
}

/**
 * Validates and sanitizes email
 */
export function sanitizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

/**
 * Validates and sanitizes display name
 */
export function sanitizeDisplayName(name: string): string {
  return name.trim().replace(/\s+/g, ' ');
}

/**
 * Maps error to user-friendly message
 */
export function mapErrorMessage(error: Record<string, unknown>): string {
  if (error instanceof Error) {
    return error.message;
  }
  
  if (typeof error === 'string') {
    return error;
  }
  
  return 'An unexpected error occurred';
}

/**
 * Extracts tenant ID from request
 */
export function extractTenantId(request: Record<string, unknown>): TenantId {
  const params = request.params as { tenantId?: unknown } | undefined;
  const user = request.user as { tenantId?: unknown } | undefined;
  return (asString(params?.tenantId) || asString(user?.tenantId) || 'default') as TenantId;
}

/**
 * Extracts user ID from request
 */
export function extractUserId(request: Record<string, unknown>): UserId {
  const user = request.user as { id?: unknown } | undefined;
  const params = request.params as { userId?: unknown } | undefined;
  return (asString(user?.id) || asString(params?.userId)) as UserId;
}
