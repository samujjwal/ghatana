/**
 * Audit Service
 *
 * Provides programmatic access to the AuditLogEntry Prisma model.
 * Used by middleware and business logic to record compliance-relevant events.
 *
 * @doc.type class
 * @doc.purpose Audit log persistence and retrieval
 * @doc.layer product
 * @doc.pattern Service
 */

import { getPrismaClient, type PrismaClient } from '../../database/client';

// ============================================================================
// Types
// ============================================================================

export interface AuditEntryInput {
  action: string;
  actor: string;
  actorRole: string;
  resource?: string;
  severity?: 'debug' | 'info' | 'warn' | 'error' | 'critical';
  details?: string;
  ipAddress?: string;
  userAgent?: string;
  method?: string;
  status?: number;
  responseTime?: number;
  tenantId?: string;
  success?: boolean;
  error?: string;
  metadata?: Record<string, unknown>;
}

export interface AuditQueryFilter {
  actor?: string;
  action?: string;
  resource?: string;
  severity?: string;
  tenantId?: string;
  from?: Date;
  to?: Date;
  limit?: number;
  offset?: number;
}

// ============================================================================
// Service
// ============================================================================

/**
 * Service for writing and querying AuditLogEntry records.
 *
 * @doc.type class
 * @doc.purpose Audit log persistence and structured querying
 * @doc.layer product
 * @doc.pattern Service
 */
export class AuditService {
  private prisma: PrismaClient;

  constructor(prisma?: PrismaClient) {
    this.prisma = prisma ?? getPrismaClient();
  }

  /**
   * Persist a single audit event.
   */
  async log(entry: AuditEntryInput) {
    return this.prisma.auditLogEntry.create({
      data: {
        action: entry.action,
        actor: entry.actor,
        actorRole: entry.actorRole,
        resource: entry.resource,
        severity: entry.severity ?? 'info',
        details: entry.details,
        ipAddress: entry.ipAddress,
        userAgent: entry.userAgent,
        method: entry.method,
        status: entry.status,
        responseTime: entry.responseTime,
        tenantId: entry.tenantId,
        success: entry.success,
        error: entry.error,
        metadata: entry.metadata,
      },
    });
  }

  /**
   * Query audit log entries with optional filters and pagination.
   */
  async query(filter: AuditQueryFilter = {}) {
    const where: Record<string, unknown> = {};

    if (filter.actor) where.actor = filter.actor;
    if (filter.action) where.action = filter.action;
    if (filter.resource) where.resource = filter.resource;
    if (filter.severity) where.severity = filter.severity;
    if (filter.tenantId) where.tenantId = filter.tenantId;
    if (filter.from || filter.to) {
      where.timestamp = {
        ...(filter.from ? { gte: filter.from } : {}),
        ...(filter.to ? { lte: filter.to } : {}),
      };
    }

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
      take: filter.limit ?? 50,
      skip: filter.offset ?? 0,
    });
  }

  /**
   * Count audit entries matching the given criteria.
   */
  async count(
    filter: Omit<AuditQueryFilter, 'limit' | 'offset'>
  ): Promise<number> {
    const where: Record<string, unknown> = {};
    if (filter.actor) where.actor = filter.actor;
    if (filter.action) where.action = filter.action;
    if (filter.resource) where.resource = filter.resource;
    if (filter.severity) where.severity = filter.severity;
    if (filter.tenantId) where.tenantId = filter.tenantId;
    if (filter.from || filter.to) {
      where.timestamp = {
        ...(filter.from ? { gte: filter.from } : {}),
        ...(filter.to ? { lte: filter.to } : {}),
      };
    }
    return this.prisma.auditLogEntry.count({ where });
  }

  // =============================================================================
  // Typed Audit Event Builders (P2-4: Centralized Audit Service)
  // =============================================================================

  /**
   * Log resource creation
   */
  async logResourceCreate(params: {
    actor: string;
    actorRole: string;
    resourceType: string;
    resourceId: string;
    tenantId?: string;
    details?: string;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: `${params.resourceType.toUpperCase()}_CREATED`,
      actor: params.actor,
      actorRole: params.actorRole,
      resource: `${params.resourceType}/${params.resourceId}`,
      severity: 'info',
      details: params.details || `${params.resourceType} ${params.resourceId} created`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: true,
    });
  }

  /**
   * Log resource update
   */
  async logResourceUpdate(params: {
    actor: string;
    actorRole: string;
    resourceType: string;
    resourceId: string;
    tenantId?: string;
    changes?: Record<string, unknown>;
    details?: string;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: `${params.resourceType.toUpperCase()}_UPDATED`,
      actor: params.actor,
      actorRole: params.actorRole,
      resource: `${params.resourceType}/${params.resourceId}`,
      severity: 'info',
      details: params.details || `${params.resourceType} ${params.resourceId} updated`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: true,
      metadata: params.changes ? { changes: params.changes } : undefined,
    });
  }

  /**
   * Log resource deletion
   */
  async logResourceDelete(params: {
    actor: string;
    actorRole: string;
    resourceType: string;
    resourceId: string;
    tenantId?: string;
    details?: string;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: `${params.resourceType.toUpperCase()}_DELETED`,
      actor: params.actor,
      actorRole: params.actorRole,
      resource: `${params.resourceType}/${params.resourceId}`,
      severity: 'warn',
      details: params.details || `${params.resourceType} ${params.resourceId} deleted`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: true,
    });
  }

  /**
   * Log access denied
   */
  async logAccessDenied(params: {
    actor: string;
    actorRole: string;
    resourceType: string;
    resourceId: string;
    action: string;
    tenantId?: string;
    reason: string;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: 'ACCESS_DENIED',
      actor: params.actor,
      actorRole: params.actorRole,
      resource: `${params.resourceType}/${params.resourceId}`,
      severity: 'warn',
      details: `Access denied: ${params.action} on ${params.resourceType}/${params.resourceId}. Reason: ${params.reason}`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: false,
      error: params.reason,
    });
  }

  /**
   * Log authentication event
   */
  async logAuthEvent(params: {
    action: 'LOGIN' | 'LOGOUT' | 'REFRESH' | 'FAILED';
    actor: string;
    success: boolean;
    tenantId?: string;
    error?: string;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: `AUTH_${params.action}`,
      actor: params.actor,
      actorRole: 'user',
      severity: params.success ? 'info' : 'warn',
      details: `Authentication ${params.action}: ${params.success ? 'success' : 'failed'}`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: params.success,
      error: params.error,
    });
  }

  /**
   * Log lifecycle phase transition
   */
  async logLifecycleTransition(params: {
    actor: string;
    actorRole: string;
    projectId: string;
    fromPhase: string;
    toPhase: string;
    tenantId?: string;
    autoApproved?: boolean;
    ipAddress?: string;
    userAgent?: string;
  }) {
    return this.log({
      action: 'LIFECYCLE_TRANSITION',
      actor: params.actor,
      actorRole: params.actorRole,
      resource: `project/${params.projectId}`,
      severity: params.autoApproved ? 'warn' : 'info',
      details: `Phase transition: ${params.fromPhase} → ${params.toPhase}`,
      ipAddress: params.ipAddress,
      userAgent: params.userAgent,
      tenantId: params.tenantId,
      success: true,
      metadata: {
        projectId: params.projectId,
        fromPhase: params.fromPhase,
        toPhase: params.toPhase,
        autoApproved: params.autoApproved,
      },
    });
  }

  /**
   * Infer resource from URL path
   */
  static inferResourceFromPath(path: string): { type: string; id?: string } | null {
    const patterns = [
      { regex: /^\/api\/v1\/workspaces\/([^\/]+)/, type: 'workspace' },
      { regex: /^\/api\/v1\/projects\/([^\/]+)/, type: 'project' },
      { regex: /^\/api\/v1\/canvas\/([^\/]+)/, type: 'canvas' },
      { regex: /^\/api\/v1\/users\/([^\/]+)/, type: 'user' },
      { regex: /^\/api\/v1\/lifecycle\/([^\/]+)/, type: 'lifecycle' },
      // Legacy patterns (deprecated)
      { regex: /^\/(?:api|v1)\/workspaces\/([^\/]+)/, type: 'workspace' },
      { regex: /^\/(?:api|v1)\/projects\/([^\/]+)/, type: 'project' },
      { regex: /^\/(?:api|v1)\/canvas\/([^\/]+)/, type: 'canvas' },
    ];

    for (const pattern of patterns) {
      const match = path.match(pattern.regex);
      if (match) {
        return { type: pattern.type, id: match[1] };
      }
    }

    return null;
  }
}

// Lazy singleton
let _instance: AuditService | null = null;

/**
 * Returns the singleton AuditService instance.
 *
 * @doc.type function
 * @doc.purpose Lazy-initialise AuditService singleton
 * @doc.layer product
 * @doc.pattern Factory
 */
export function getAuditService(): AuditService {
  if (!_instance) {
    _instance = new AuditService();
  }
  return _instance;
}
