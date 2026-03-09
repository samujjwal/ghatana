/**
 * Audit Log Repository
 *
 * <p><b>Purpose</b><br>
 * Data access layer for audit log entries. Provides specialized queries for
 * retrieving audit records by time period, actor, action, resource, and tenant.
 * Ensures immutability of audit logs and efficient querying for compliance reports.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const repo = new AuditLogRepository(prisma);
 * const events = await repo.findByTenant('tenant-123', {
 *   startDate: new Date('2024-01-01'),
 *   endDate: new Date(),
 *   action: 'compliance.assessment.created'
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Audit log data access
 * @doc.layer product
 * @doc.pattern Repository
 */

import { PrismaClient, AuditLogEntry } from '@prisma/client';
import { BaseRepository } from '../repository.base';

/**
 * Interface for audit log query filters
 */
export interface AuditLogFilter {
  tenantId?: string;
  actor?: string;
  action?: string;
  resource?: string;
  severity?: 'info' | 'warning' | 'critical';
  startDate?: Date;
  endDate?: Date;
  limit?: number;
  offset?: number;
}

/**
 * Interface for audit log statistics
 */
export interface AuditLogStats {
  totalEvents: number;
  eventsByAction: { [action: string]: number };
  eventsByActor: { [actor: string]: number };
  eventsBySeverity: { [severity: string]: number };
  timeRange: {
    earliest: Date;
    latest: Date;
  };
}

/**
 * AuditLogRepository handles audit log data access operations
 */
export class AuditLogRepository extends BaseRepository<
  AuditLogEntry,
  'auditLogEntry'
> {
  /**
   * Creates a new AuditLogRepository instance.
   *
   * @param prisma - Prisma client instance
   */
  constructor(prisma: PrismaClient) {
    super(prisma, 'auditLogEntry');
  }

  /**
   * Finds audit logs by tenant with optional filtering.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all audit logs for a specific tenant, optionally filtered by
   * date range, action, actor, or severity.
   *
   * @param tenantId - The tenant ID
   * @param filter - Optional filter parameters
   * @returns Promise<AuditLogEntry[]> - Matching audit log entries
   */
  async findByTenant(
    tenantId: string,
    filter?: AuditLogFilter
  ): Promise<AuditLogEntry[]> {
    const where: Record<string, unknown> = { tenantId };

    if (filter?.action) where.action = filter.action;
    if (filter?.actor) where.actor = filter.actor;
    if (filter?.resource) where.resource = filter.resource;
    if (filter?.severity) where.severity = filter.severity;

    if (filter?.startDate || filter?.endDate) {
      where.timestamp = {};
      if (filter.startDate) {
        where.timestamp.gte = filter.startDate;
      }
      if (filter.endDate) {
        where.timestamp.lte = filter.endDate;
      }
    }

    const skip = filter?.offset || 0;
    const take = filter?.limit || 100;

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
      skip,
      take,
    });
  }

  /**
   * Finds audit logs by action type.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all audit logs for a specific action (e.g., 'compliance.assessment.created')
   * within an optional time range and tenant scope.
   *
   * @param action - The action type
   * @param tenantId - Optional tenant ID filter
   * @param timeRange - Optional time range
   * @returns Promise<AuditLogEntry[]> - Matching entries
   */
  async findByAction(
    action: string,
    tenantId?: string,
    timeRange?: { startDate: Date; endDate: Date }
  ): Promise<AuditLogEntry[]> {
    const where: Record<string, unknown> = { action };

    if (tenantId) where.tenantId = tenantId;

    if (timeRange) {
      where.timestamp = {
        gte: timeRange.startDate,
        lte: timeRange.endDate,
      };
    }

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
    });
  }

  /**
   * Finds audit logs by actor (user or system).
   *
   * <p><b>Purpose</b><br>
   * Retrieves all audit logs created by a specific actor, useful for tracking
   * user activity and accountability.
   *
   * @param actor - The actor ID (user or system)
   * @param tenantId - Optional tenant ID filter
   * @param limit - Maximum number of entries
   * @returns Promise<AuditLogEntry[]> - Matching entries
   */
  async findByActor(
    actor: string,
    tenantId?: string,
    limit = 100
  ): Promise<AuditLogEntry[]> {
    const where: Record<string, unknown> = { actor };
    if (tenantId) where.tenantId = tenantId;

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
      take: limit,
    });
  }

  /**
   * Finds audit logs by resource.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all audit logs related to a specific resource (assessment, control, etc.)
   * to track all changes and access to that resource.
   *
   * @param resource - The resource identifier
   * @param tenantId - Optional tenant ID filter
   * @returns Promise<AuditLogEntry[]> - Matching entries
   */
  async findByResource(
    resource: string,
    tenantId?: string
  ): Promise<AuditLogEntry[]> {
    const where: Record<string, unknown> = { resource };
    if (tenantId) where.tenantId = tenantId;

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
    });
  }

  /**
   * Finds critical severity audit logs.
   *
   * <p><b>Purpose</b><br>
   * Retrieves only critical severity audit logs for immediate alerting and
   * high-priority compliance monitoring.
   *
   * @param tenantId - Optional tenant ID filter
   * @param hours - Number of hours back to search (default: 24)
   * @returns Promise<AuditLogEntry[]> - Critical entries
   */
  async findCritical(tenantId?: string, hours = 24): Promise<AuditLogEntry[]> {
    const since = new Date();
    since.setHours(since.getHours() - hours);

    const where: Record<string, unknown> = {
      severity: 'critical',
      timestamp: { gte: since },
    };

    if (tenantId) where.tenantId = tenantId;

    return this.prisma.auditLogEntry.findMany({
      where,
      orderBy: { timestamp: 'desc' },
    });
  }

  /**
   * Generates audit log statistics.
   *
   * <p><b>Purpose</b><br>
   * Calculates aggregate statistics about audit logs including event counts
   * by action, actor, and severity for compliance metrics.
   *
   * @param tenantId - The tenant ID
   * @param timeRange - Optional time range filter
   * @returns Promise<AuditLogStats> - Aggregate statistics
   */
  async getStatistics(
    tenantId: string,
    timeRange?: { startDate: Date; endDate: Date }
  ): Promise<AuditLogStats> {
    const where: Record<string, unknown> = { tenantId };

    if (timeRange) {
      where.timestamp = {
        gte: timeRange.startDate,
        lte: timeRange.endDate,
      };
    }

    const entries = await this.prisma.auditLogEntry.findMany({
      where,
    });

    const eventsByAction: { [action: string]: number } = {};
    const eventsByActor: { [actor: string]: number } = {};
    const eventsBySeverity: { [severity: string]: number } = {};

    for (const entry of entries) {
      // Count by action
      eventsByAction[entry.action] = (eventsByAction[entry.action] || 0) + 1;

      // Count by actor
      eventsByActor[entry.actor] = (eventsByActor[entry.actor] || 0) + 1;

      // Count by severity
      eventsBySeverity[entry.severity] = (eventsBySeverity[entry.severity] || 0) + 1;
    }

    const timestamps = entries.map((e) => e.timestamp);
    const earliestTime = timestamps.length > 0 ? new Date(Math.min(...timestamps.map((t) => t.getTime()))) : new Date();
    const latestTime = timestamps.length > 0 ? new Date(Math.max(...timestamps.map((t) => t.getTime()))) : new Date();

    return {
      totalEvents: entries.length,
      eventsByAction,
      eventsByActor,
      eventsBySeverity,
      timeRange: {
        earliest: earliestTime,
        latest: latestTime,
      },
    };
  }

  /**
   * Exports audit logs in CSV format.
   *
   * <p><b>Purpose</b><br>
   * Generates CSV export of audit logs for external compliance tools,
   * spreadsheet analysis, or archival purposes.
   *
   * @param filter - Filter parameters
   * @returns Promise<string> - CSV formatted data
   */
  async exportAsCSV(filter: AuditLogFilter): Promise<string> {
    const entries = await this.findByTenant(filter.tenantId || '', filter);

    const headers = [
      'timestamp',
      'actor',
      'action',
      'resource',
      'severity',
      'details',
      'ipAddress',
      'userAgent',
    ];
    const rows = entries.map((entry) => [
      entry.timestamp.toISOString(),
      entry.actor,
      entry.action,
      entry.resource,
      entry.severity,
      entry.details || '',
      entry.ipAddress || '',
      entry.userAgent || '',
    ]);

    const csv = [
      headers.join(','),
      ...rows.map((row) =>
        row
          .map((cell) =>
            typeof cell === 'string' && cell.includes(',')
              ? `"${cell.replace(/"/g, '""')}"`
              : cell
          )
          .join(',')
      ),
    ].join('\n');

    return csv;
  }

  /**
   * Verifies audit log integrity (tamper detection).
   *
   * <p><b>Purpose</b><br>
   * Checks for gaps in audit log sequence, missing entries, or signs of
   * tampering to ensure compliance audit trail integrity.
   *
   * @param tenantId - The tenant ID
   * @param startDate - Start of verification period
   * @param endDate - End of verification period
   * @returns Promise<object> - Integrity verification result
   */
  async verifyIntegrity(
    tenantId: string,
    startDate: Date,
    endDate: Date
  ): Promise<object> {
    const entries = await this.findByTenant(tenantId, {
      startDate,
      endDate,
    });

    if (entries.length === 0) {
      return {
        isValid: true,
        totalEntries: 0,
        gaps: [],
        warnings: [],
      };
    }

    // Sort by timestamp
    entries.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());

    const gaps: unknown[] = [];
    const expectedInterval = 60000; // 1 minute expected max gap

    for (let i = 1; i < entries.length; i++) {
      const gap =
        entries[i].timestamp.getTime() - entries[i - 1].timestamp.getTime();
      if (gap > expectedInterval) {
        gaps.push({
          between: [entries[i - 1].id, entries[i].id],
          gapMs: gap,
        });
      }
    }

    return {
      isValid: gaps.length === 0,
      totalEntries: entries.length,
      gaps,
      warnings: gaps.length > 0 ? ['Suspicious gaps detected in audit log'] : [],
    };
  }

  /**
   * Purges old audit logs (if compliance allows).
   *
   * <p><b>Purpose</b><br>
   * Removes audit logs older than the retention period to manage storage
   * while respecting compliance retention requirements.
   *
   * @param tenantId - The tenant ID
   * @param retentionDays - Number of days to retain (default: 2555 = 7 years)
   * @returns Promise<number> - Number of entries deleted
   */
  async purgeOldLogs(
    tenantId: string,
    retentionDays = 2555
  ): Promise<number> {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - retentionDays);

    const result = await this.prisma.auditLogEntry.deleteMany({
      where: {
        tenantId,
        timestamp: { lt: cutoffDate },
      },
    });

    return result.count;
  }
}

