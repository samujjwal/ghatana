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

/**
 * Maps database module to ModuleSummary contract type
 */
export function mapModuleSummary(module: Record<string, unknown>): ModuleSummary {
  return {
    id: module.id as ModuleId,
    title: module.title,
    description: module.description || '',
    difficulty: module.difficulty || 'beginner',
    estimatedMinutes: module.estimatedMinutes || 0,
    tags: module.tags || [],
    status: module.status || 'draft',
    createdAt: module.createdAt?.toISOString() || new Date().toISOString(),
    updatedAt: module.updatedAt?.toISOString() || new Date().toISOString(),
  };
}

/**
 * Maps database enrollment to Enrollment contract type
 */
export function mapEnrollment(enrollment: Record<string, unknown>): Enrollment {
  return {
    id: enrollment.id as EnrollmentId,
    moduleId: enrollment.moduleId as ModuleId,
    userId: enrollment.userId as UserId,
    tenantId: enrollment.tenantId as TenantId,
    status: mapEnrollmentStatus(enrollment.status),
    progress: enrollment.progress || 0,
    timeSpentSeconds: enrollment.timeSpentSeconds || 0,
    startedAt: enrollment.startedAt?.toISOString() || new Date().toISOString(),
    completedAt: enrollment.completedAt?.toISOString(),
    lastAccessedAt: enrollment.lastAccessedAt?.toISOString(),
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
    'ABANDONED',
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
    startedAt: attempt.startedAt?.toISOString() || new Date().toISOString(),
    completedAt: attempt.completedAt?.toISOString(),
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
    timestamp: event.timestamp?.toISOString() || new Date().toISOString(),
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
    createdAt: classroom.createdAt?.toISOString() || new Date().toISOString(),
    updatedAt: classroom.updatedAt?.toISOString() || new Date().toISOString(),
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
    createdAt: notification.createdAt?.toISOString() || new Date().toISOString(),
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
  const limit = Math.min(params.limit || 50, 200);
  const cursor = params.cursor;

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
  metadata: any,
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
  return (request.params?.tenantId || request.user?.tenantId || 'default') as TenantId;
}

/**
 * Extracts user ID from request
 */
export function extractUserId(request: Record<string, unknown>): UserId {
  return (request.user?.id || request.params?.userId) as UserId;
}
