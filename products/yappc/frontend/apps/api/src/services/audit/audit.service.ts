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
