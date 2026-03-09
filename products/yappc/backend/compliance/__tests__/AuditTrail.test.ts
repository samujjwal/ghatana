/**
 * Audit Trail Tests
 *
 * @jest-environment jsdom
 */

import { AuditTrail, AuditEvent, AuditFilter, globalAuditTrail } from '../AuditTrail';

describe('AuditTrail', () => {
  let trail: AuditTrail;

  beforeEach(() => {
    trail = new AuditTrail();
  });

  describe('Event Logging', () => {
    it('should log audit event with sequence number', () => {
      const event = trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'USER_LOGIN',
        resourceType: 'User',
        resourceId: 'user1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla/5.0',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      expect(event).toBeDefined();
      expect(event.id).toBeDefined();
      expect(event.sequenceNumber).toBe(1);
      expect(event.timestamp).toBeDefined();
      expect(event.action).toBe('USER_LOGIN');
    });

    it('should increment sequence number for each event', () => {
      const event1 = trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION1',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      const event2 = trail.log({
        userId: 'user2',
        userEmail: 'user2@example.com',
        action: 'ACTION2',
        resourceType: 'Resource',
        resourceId: 'id2',
        result: 'SUCCESS',
        ipAddress: '192.168.1.2',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      expect(event1.sequenceNumber).toBe(1);
      expect(event2.sequenceNumber).toBe(2);
    });

    it('should freeze events (immutable)', () => {
      const event = trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      expect(Object.isFrozen(event)).toBe(true);
    });
  });

  describe('Event Querying', () => {
    beforeEach(() => {
      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'LOGIN',
        resourceType: 'User',
        resourceId: 'user1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
        tags: ['SOC2', 'AUTH'],
      });

      trail.log({
        userId: 'user2',
        userEmail: 'user2@example.com',
        action: 'DATA_ACCESS',
        resourceType: 'Document',
        resourceId: 'doc1',
        result: 'FAILURE',
        ipAddress: '192.168.1.2',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
        tags: ['SOC2'],
      });
    });

    it('should query events by user', () => {
      const events = trail.query({ userId: 'user1' });

      expect(events).toHaveLength(1);
      expect(events[0].userId).toBe('user1');
    });

    it('should query events by action', () => {
      const events = trail.query({ action: 'LOGIN' });

      expect(events).toHaveLength(1);
      expect(events[0].action).toBe('LOGIN');
    });

    it('should query events by result', () => {
      const events = trail.query({ result: 'FAILURE' });

      expect(events).toHaveLength(1);
      expect(events[0].result).toBe('FAILURE');
    });

    it('should query events by tags', () => {
      const events = trail.query({ tags: ['SOC2'] });

      expect(events.length).toBeGreaterThan(0);
      expect(events.every((e) => e.tags?.includes('SOC2'))).toBe(true);
    });

    it('should return events in chronological order', () => {
      const events = trail.query({});

      for (let i = 1; i < events.length; i++) {
        expect(events[i].sequenceNumber).toBeGreaterThan(events[i - 1].sequenceNumber);
      }
    });
  });

  describe('Integrity Verification', () => {
    it('should verify intact trail', () => {
      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      expect(trail.verifyIntegrity()).toBe(true);
    });

    it('should verify integrity with multiple events', () => {
      for (let i = 0; i < 5; i++) {
        trail.log({
          userId: `user${i}`,
          userEmail: `user${i}@example.com`,
          action: `ACTION${i}`,
          resourceType: 'Resource',
          resourceId: `id${i}`,
          result: 'SUCCESS',
          ipAddress: '192.168.1.1',
          userAgent: 'Mozilla',
          tenantId: 'tenant1',
          complianceRelevant: true,
        });
      }

      expect(trail.verifyIntegrity()).toBe(true);
    });
  });

  describe('Export', () => {
    beforeEach(() => {
      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION1',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });
    });

    it('should export to JSON', () => {
      const json = trail.export({});

      expect(json).toContain('exportDate');
      expect(json).toContain('eventCount');
      expect(json).toContain('integrityVerified');

      const parsed = JSON.parse(json);
      expect(parsed.events).toHaveLength(1);
    });

    it('should export to CSV', () => {
      const csv = trail.export({}, 'csv');

      expect(csv).toContain('ID');
      expect(csv).toContain('Timestamp');
      expect(csv).toContain('user1@example.com');
    });
  });

  describe('Compliance Reports', () => {
    beforeEach(() => {
      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'LOGIN',
        resourceType: 'User',
        resourceId: 'user1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
        tags: ['SOC2'],
      });

      trail.log({
        userId: 'user2',
        userEmail: 'user2@example.com',
        action: 'LOGOUT',
        resourceType: 'User',
        resourceId: 'user2',
        result: 'SUCCESS',
        ipAddress: '192.168.1.2',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
        tags: ['SOC2'],
      });
    });

    it('should generate compliance report', () => {
      const report = trail.generateReport(
        new Date(Date.now() - 24 * 60 * 60 * 1000),
        new Date(),
        'SOC2'
      );

      expect(report.framework).toBe('SOC2');
      expect(report.totalEvents).toBeGreaterThan(0);
      expect(report.successRate).toBeGreaterThan(0);
      expect(report.integrityVerified).toBe(true);
    });

    it('should include action summary in report', () => {
      const report = trail.generateReport(
        new Date(Date.now() - 24 * 60 * 60 * 1000),
        new Date(),
        'SOC2'
      );

      expect(report.actionSummary).toBeDefined();
      expect(typeof report.actionSummary).toBe('object');
    });
  });

  describe('Statistics', () => {
    it('should calculate statistics', () => {
      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });

      const stats = trail.getStatistics();

      expect(stats.totalEvents).toBe(1);
      expect(stats.uniqueUsers).toBe(1);
      expect(stats.failureCount).toBe(0);
    });
  });

  describe('Event Emitter', () => {
    it('should emit events', (done) => {
      trail.on('event', (event: AuditEvent) => {
        expect(event.action).toBe('ACTION');
        done();
      });

      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });
    });

    it('should emit compliance events', (done) => {
      trail.on('compliance-event', (event: AuditEvent) => {
        expect(event.complianceRelevant).toBe(true);
        done();
      });

      trail.log({
        userId: 'user1',
        userEmail: 'user1@example.com',
        action: 'ACTION',
        resourceType: 'Resource',
        resourceId: 'id1',
        result: 'SUCCESS',
        ipAddress: '192.168.1.1',
        userAgent: 'Mozilla',
        tenantId: 'tenant1',
        complianceRelevant: true,
      });
    });
  });
});
