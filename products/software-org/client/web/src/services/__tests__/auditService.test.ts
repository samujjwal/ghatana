/**
 * Tests for Audit Service
 * 
 * Validates audit logging, querying, and analytics functionality
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { auditService } from '@/services/auditService';
import {
    AuditAction,
    AuditResourceType,
    AuditSeverity,
} from '@/types/audit';

describe('AuditService', () => {
    beforeEach(() => {
        // Clear all events before each test
        auditService.clearAll();
    });

    describe('logEvent', () => {
        it('should log a simple audit event', async () => {
            const event = await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'admin',
                resourceName: 'Admin Role',
                metadata: {
                    userId: 'user-123',
                    userName: 'John Doe',
                },
            });

            expect(event.eventId).toBeDefined();
            expect(event.timestamp).toBeDefined();
            expect(event.action).toBe(AuditAction.ROLE_CREATED);
            expect(event.resourceType).toBe(AuditResourceType.ROLE);
            expect(event.resourceId).toBe('admin');
            expect(event.success).toBe(true);
            expect(event.metadata.userId).toBe('user-123');
        });

        it('should log event with changes', async () => {
            const event = await auditService.logEvent({
                action: AuditAction.ROLE_UPDATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'admin',
                changes: [
                    {
                        field: 'permissions',
                        oldValue: ['read'],
                        newValue: ['read', 'write'],
                    },
                ],
                metadata: {
                    userId: 'user-123',
                },
            });

            expect(event.changes).toHaveLength(1);
            expect(event.changes[0].field).toBe('permissions');
            expect(event.changes[0].oldValue).toEqual(['read']);
            expect(event.changes[0].newValue).toEqual(['read', 'write']);
        });

        it('should log failed event', async () => {
            const event = await auditService.logEvent({
                action: AuditAction.ROLE_DELETED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'admin',
                success: false,
                errorMessage: 'Cannot delete admin role',
                severity: AuditSeverity.ERROR,
                metadata: {
                    userId: 'user-123',
                },
            });

            expect(event.success).toBe(false);
            expect(event.errorMessage).toBe('Cannot delete admin role');
            expect(event.severity).toBe(AuditSeverity.ERROR);
        });

        it('should default userId to system if not provided', async () => {
            const event = await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'test',
            });

            expect(event.metadata.userId).toBe('system');
        });

        it('should default severity to INFO if not provided', async () => {
            const event = await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'test',
                metadata: { userId: 'user-123' },
            });

            expect(event.severity).toBe(AuditSeverity.INFO);
        });
    });

    describe('queryEvents', () => {
        beforeEach(async () => {
            // Create test events
            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'admin',
                metadata: { userId: 'user-1', userName: 'Alice' },
            });

            await auditService.logEvent({
                action: AuditAction.ROLE_UPDATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'admin',
                metadata: { userId: 'user-2', userName: 'Bob' },
            });

            await auditService.logEvent({
                action: AuditAction.PERMISSION_ADDED,
                resourceType: AuditResourceType.PERMISSION,
                resourceId: 'write',
                metadata: { userId: 'user-1', userName: 'Alice' },
            });
        });

        it('should return all events without filter', async () => {
            const result = await auditService.queryEvents();

            expect(result.events).toHaveLength(3);
            expect(result.total).toBe(3);
            expect(result.hasMore).toBe(false);
        });

        it('should filter by action', async () => {
            const result = await auditService.queryEvents({
                actions: [AuditAction.ROLE_CREATED],
            });

            expect(result.events).toHaveLength(1);
            expect(result.events[0].action).toBe(AuditAction.ROLE_CREATED);
        });

        it('should filter by resource type', async () => {
            const result = await auditService.queryEvents({
                resourceTypes: [AuditResourceType.PERMISSION],
            });

            expect(result.events).toHaveLength(1);
            expect(result.events[0].resourceType).toBe(AuditResourceType.PERMISSION);
        });

        it('should filter by user', async () => {
            const result = await auditService.queryEvents({
                userIds: ['user-1'],
            });

            expect(result.events).toHaveLength(2);
            expect(result.events.every(e => e.metadata.userId === 'user-1')).toBe(true);
        });

        it('should search by query', async () => {
            const result = await auditService.queryEvents({
                searchQuery: 'alice',
            });

            expect(result.events).toHaveLength(2);
            expect(result.events.every(e => e.metadata.userName === 'Alice')).toBe(true);
        });

        it('should support pagination', async () => {
            const page1 = await auditService.queryEvents({
                limit: 2,
                offset: 0,
            });

            expect(page1.events).toHaveLength(2);
            expect(page1.hasMore).toBe(true);

            const page2 = await auditService.queryEvents({
                limit: 2,
                offset: 2,
            });

            expect(page2.events).toHaveLength(1);
            expect(page2.hasMore).toBe(false);
        });

        it('should filter by time range', async () => {
            const now = new Date();
            const past = new Date(now.getTime() - 3600000); // 1 hour ago
            const future = new Date(now.getTime() + 3600000); // 1 hour from now

            const result = await auditService.queryEvents({
                startTime: past.toISOString(),
                endTime: future.toISOString(),
            });

            expect(result.events).toHaveLength(3);
        });
    });

    describe('getStats', () => {
        beforeEach(async () => {
            // Create diverse events
            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'role-1',
                severity: AuditSeverity.INFO,
                metadata: { userId: 'user-1', userName: 'Alice' },
            });

            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'role-2',
                severity: AuditSeverity.INFO,
                metadata: { userId: 'user-1', userName: 'Alice' },
            });

            await auditService.logEvent({
                action: AuditAction.PERMISSION_ADDED,
                resourceType: AuditResourceType.PERMISSION,
                resourceId: 'perm-1',
                severity: AuditSeverity.WARNING,
                metadata: { userId: 'user-2', userName: 'Bob' },
            });
        });

        it('should return correct total count', async () => {
            const stats = await auditService.getStats();

            expect(stats.totalEvents).toBe(3);
        });

        it('should count events by action', async () => {
            const stats = await auditService.getStats();

            expect(stats.eventsByAction[AuditAction.ROLE_CREATED]).toBe(2);
            expect(stats.eventsByAction[AuditAction.PERMISSION_ADDED]).toBe(1);
        });

        it('should count events by resource type', async () => {
            const stats = await auditService.getStats();

            expect(stats.eventsByResourceType[AuditResourceType.ROLE]).toBe(2);
            expect(stats.eventsByResourceType[AuditResourceType.PERMISSION]).toBe(1);
        });

        it('should count events by severity', async () => {
            const stats = await auditService.getStats();

            expect(stats.eventsBySeverity[AuditSeverity.INFO]).toBe(2);
            expect(stats.eventsBySeverity[AuditSeverity.WARNING]).toBe(1);
        });

        it('should return top users', async () => {
            const stats = await auditService.getStats();

            expect(stats.topUsers).toHaveLength(2);
            expect(stats.topUsers[0].userId).toBe('user-1');
            expect(stats.topUsers[0].count).toBe(2);
            expect(stats.topUsers[1].userId).toBe('user-2');
            expect(stats.topUsers[1].count).toBe(1);
        });

        it('should return recent activity', async () => {
            const stats = await auditService.getStats();

            expect(stats.recentActivity).toHaveLength(3);
            // Most recent first
            expect(stats.recentActivity[0].resourceId).toBe('perm-1');
        });
    });

    describe('getAnalytics', () => {
        it('should generate time series data', async () => {
            const startTime = new Date('2024-01-01T00:00:00Z');
            const endTime = new Date('2024-01-03T00:00:00Z');

            // Create events across multiple days
            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'role-1',
                metadata: { userId: 'user-1' },
            });

            const analytics = await auditService.getAnalytics({
                startTime: startTime.toISOString(),
                endTime: endTime.toISOString(),
                interval: 'day',
            });

            expect(analytics.timeSeries).toBeDefined();
            expect(analytics.timeSeries.length).toBeGreaterThan(0);
            expect(analytics.stats).toBeDefined();
            expect(analytics.period.start).toBe(startTime.toISOString());
            expect(analytics.period.end).toBe(endTime.toISOString());
        });

        it('should group time series by action', async () => {
            const startTime = new Date();
            const endTime = new Date(startTime.getTime() + 86400000); // +1 day

            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'role-1',
                metadata: { userId: 'user-1' },
            });

            const analytics = await auditService.getAnalytics({
                startTime: startTime.toISOString(),
                endTime: endTime.toISOString(),
                interval: 'day',
                groupBy: 'action',
            });

            // Check that breakdown exists
            const pointsWithBreakdown = analytics.timeSeries.filter(p => p.breakdown);
            expect(pointsWithBreakdown.length).toBeGreaterThan(0);
        });
    });

    describe('clearAll', () => {
        it('should clear all events', async () => {
            await auditService.logEvent({
                action: AuditAction.ROLE_CREATED,
                resourceType: AuditResourceType.ROLE,
                resourceId: 'test',
                metadata: { userId: 'user-1' },
            });

            expect(auditService.getEventCount()).toBe(1);

            auditService.clearAll();

            expect(auditService.getEventCount()).toBe(0);
        });
    });
});
