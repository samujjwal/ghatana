/**
 * Audit Trail Management
 *
 * Comprehensive audit logging for compliance tracking.
 * Records all significant events for compliance verification.
 *
 * Maintains immutable audit trail with:
 * - User identification
 * - Timestamp and sequence
 * - Action details
 * - Results and outcomes
 * - System context
 */

import { EventEmitter } from 'events';

export interface AuditEvent {
  id: string;
  timestamp: Date;
  sequenceNumber: number;
  userId: string;
  userEmail: string;
  action: string;
  resourceType: string;
  resourceId: string;
  changes?: Record<string, { before: string | null; after: string | null }>;
  result: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
  ipAddress: string;
  userAgent: string;
  tenantId: string;
  complianceRelevant: boolean;
  tags?: string[];
}

export interface AuditFilter {
  startDate?: Date;
  endDate?: Date;
  userId?: string;
  action?: string;
  resourceType?: string;
  result?: 'SUCCESS' | 'FAILURE';
  complianceRelevant?: boolean;
  tags?: string[];
}

/**
 * Immutable audit trail for compliance
 */
export class AuditTrail extends EventEmitter {
  private events: Map<string, AuditEvent> = new Map();
  private sequenceCounter = 0;
  private readonly maxRetention = 2555; // days (7 years)

  /**
   * Log audit event
   *
   * GIVEN: Audit event details
   * WHEN: log called
   * THEN: Event added to immutable trail with sequence number
   */
  log(event: Omit<AuditEvent, 'id' | 'timestamp' | 'sequenceNumber'>): AuditEvent {
    const auditEvent: AuditEvent = {
      ...event,
      id: `audit-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date(),
      sequenceNumber: ++this.sequenceCounter,
    };

    this.events.set(auditEvent.id, Object.freeze(auditEvent));
    this.emit('event', auditEvent);

    // Emit compliance-tagged events
    if (auditEvent.complianceRelevant) {
      this.emit('compliance-event', auditEvent);
    }

    return auditEvent;
  }

  /**
   * Query audit trail with filtering
   *
   * GIVEN: Filter criteria
   * WHEN: query called
   * THEN: Matching events returned in chronological order
   */
  query(filter: AuditFilter): AuditEvent[] {
    return Array.from(this.events.values())
      .filter((event) => {
        if (filter.startDate && event.timestamp < filter.startDate) return false;
        if (filter.endDate && event.timestamp > filter.endDate) return false;
        if (filter.userId && event.userId !== filter.userId) return false;
        if (filter.action && event.action !== filter.action) return false;
        if (filter.resourceType && event.resourceType !== filter.resourceType) return false;
        if (filter.result && event.result !== filter.result) return false;
        if (filter.complianceRelevant !== undefined && event.complianceRelevant !== filter.complianceRelevant) {
          return false;
        }
        if (filter.tags && filter.tags.length > 0) {
          return filter.tags.some((tag) => event.tags?.includes(tag));
        }
        return true;
      })
      .sort((a, b) => a.sequenceNumber - b.sequenceNumber);
  }

  /**
   * Get audit events for compliance report
   *
   * GIVEN: Framework name
   * WHEN: getComplianceEvents called
   * THEN: Events tagged with framework returned
   */
  getComplianceEvents(framework: string): AuditEvent[] {
    return this.query({
      complianceRelevant: true,
      tags: [framework],
    });
  }

  /**
   * Verify audit trail integrity
   *
   * GIVEN: Audit trail with events
   * WHEN: verifyIntegrity called
   * THEN: Boolean indicating integrity status returned
   *
   * Checks:
   * - Sequence numbers are sequential
   * - Timestamps are monotonic
   * - No gaps in sequence
   */
  verifyIntegrity(): boolean {
    const sortedEvents = Array.from(this.events.values())
      .sort((a, b) => a.sequenceNumber - b.sequenceNumber);

    let lastTimestamp = new Date(0);

    for (let i = 0; i < sortedEvents.length; i++) {
      const event = sortedEvents[i];

      // Check sequence continuity
      if (event.sequenceNumber !== i + 1) {
        return false;
      }

      // Check timestamp ordering (allowing same timestamp)
      if (event.timestamp < lastTimestamp) {
        return false;
      }

      lastTimestamp = event.timestamp;
    }

    return true;
  }

  /**
   * Export audit trail in compliance format
   *
   * GIVEN: Filter criteria and export format
   * WHEN: export called
   * THEN: Events exported with metadata
   */
  export(filter: AuditFilter, format: 'json' | 'csv' = 'json'): string {
    const events = this.query(filter);

    if (format === 'csv') {
      // CSV header
      const headers = [
        'ID',
        'Timestamp',
        'Sequence',
        'User ID',
        'User Email',
        'Action',
        'Resource Type',
        'Resource ID',
        'Result',
        'IP Address',
        'Tenant ID',
      ];

      const rows = events.map((e) => [
        e.id,
        e.timestamp.toISOString(),
        e.sequenceNumber,
        e.userId,
        e.userEmail,
        e.action,
        e.resourceType,
        e.resourceId,
        e.result,
        e.ipAddress,
        e.tenantId,
      ]);

      return [headers, ...rows].map((row) => row.map((cell) => `"${cell}"`).join(',')).join('\n');
    }

    // JSON format with metadata
    return JSON.stringify(
      {
        exportDate: new Date().toISOString(),
        eventCount: events.length,
        integrityVerified: this.verifyIntegrity(),
        events,
      },
      null,
      2
    );
  }

  /**
   * Generate compliance report
   *
   * GIVEN: Period and framework
   * WHEN: generateReport called
   * THEN: Structured compliance report generated
   */
  generateReport(startDate: Date, endDate: Date, framework: string) {
    const events = this.getComplianceEvents(framework).filter(
      (e) => e.timestamp >= startDate && e.timestamp <= endDate
    );

    const actionCounts = new Map<string, number>();
    const resultCounts = { SUCCESS: 0, FAILURE: 0 };

    for (const event of events) {
      actionCounts.set(event.action, (actionCounts.get(event.action) || 0) + 1);
      resultCounts[event.result]++;
    }

    return {
      framework,
      period: { startDate, endDate },
      totalEvents: events.length,
      successCount: resultCounts.SUCCESS,
      failureCount: resultCounts.FAILURE,
      successRate: events.length > 0 ? Math.round((resultCounts.SUCCESS / events.length) * 100) : 0,
      actionSummary: Object.fromEntries(actionCounts),
      integrityVerified: this.verifyIntegrity(),
    };
  }

  /**
   * Cleanup expired events
   *
   * GIVEN: Retention period
   * WHEN: cleanup called
   * THEN: Events older than retention period removed
   */
  cleanup(): number {
    const cutoffDate = new Date(Date.now() - this.maxRetention * 24 * 60 * 60 * 1000);
    let deletedCount = 0;

    for (const [id, event] of this.events) {
      if (event.timestamp < cutoffDate) {
        this.events.delete(id);
        deletedCount++;
      }
    }

    return deletedCount;
  }

  /**
   * Get audit trail statistics
   */
  getStatistics() {
    const events = Array.from(this.events.values());

    return {
      totalEvents: events.length,
      dateRange: {
        earliest: events.length > 0 ? Math.min(...events.map((e) => e.timestamp.getTime())) : null,
        latest: events.length > 0 ? Math.max(...events.map((e) => e.timestamp.getTime())) : null,
      },
      complianceEventCount: events.filter((e) => e.complianceRelevant).length,
      failureCount: events.filter((e) => e.result === 'FAILURE').length,
      uniqueUsers: new Set(events.map((e) => e.userId)).size,
    };
  }
}

/**
 * Global audit trail instance
 */
export const globalAuditTrail = new AuditTrail();

/**
 * Decorator for audit logging
 *
 * Usage:
 * @auditLog('USER_LOGIN', 'User', 'login')
 * async loginUser(userId: string) { ... }
 */
export function auditLog(action: string, resourceType: string, tags: string[] = []) {
  return function (target: any, propertyKey: string, descriptor: PropertyDescriptor) {
    const originalMethod = descriptor.value;

    descriptor.value = async function (...args: any[]) {
      try {
        const result = await originalMethod.apply(this, args);

        globalAuditTrail.log({
          userId: (this as any).currentUser?.id || 'system',
          userEmail: (this as any).currentUser?.email || 'system@ghatana.dev',
          action,
          resourceType,
          resourceId: args[0]?.id || args[0] || 'unknown',
          result: 'SUCCESS',
          ipAddress: (this as any).ipAddress || '0.0.0.0',
          userAgent: (this as any).userAgent || 'unknown',
          tenantId: (this as any).tenantId || 'default',
          complianceRelevant: true,
          tags,
        });

        return result;
      } catch (error) {
        globalAuditTrail.log({
          userId: (this as any).currentUser?.id || 'system',
          userEmail: (this as any).currentUser?.email || 'system@ghatana.dev',
          action,
          resourceType,
          resourceId: args[0]?.id || args[0] || 'unknown',
          result: 'FAILURE',
          errorMessage: (error as Error).message,
          ipAddress: (this as any).ipAddress || '0.0.0.0',
          userAgent: (this as any).userAgent || 'unknown',
          tenantId: (this as any).tenantId || 'default',
          complianceRelevant: true,
          tags,
        });

        throw error;
      }
    };

    return descriptor;
  };
}
