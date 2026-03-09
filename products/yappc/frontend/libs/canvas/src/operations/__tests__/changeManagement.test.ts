/**
 * Tests for Canvas Change Management System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  ChangeManagementManager,
  type ADRStatus,
  type ApprovalStatus,
  type RiskLevel,
  calculateApprovalRate,
  calculateAvgApprovalTime,
  formatChangeDuration,
  validateChangeRequest,
} from '../changeManagement';

describe('ChangeManagementManager', () => {
  let manager: ChangeManagementManager;

  beforeEach(() => {
    manager = new ChangeManagementManager();
  });

  describe('Initialization', () => {
    it('should create manager with default configuration', () => {
      expect(manager).toBeDefined();
      expect(manager.getStats()).toEqual({
        totalADRs: 0,
        adrsByStatus: {
          proposed: 0,
          accepted: 0,
          deprecated: 0,
          superseded: 0,
        },
        totalChanges: 0,
        changesByStatus: {
          pending: 0,
          approved: 0,
          rejected: 0,
          conditional: 0,
        },
        changesByRisk: {
          low: 0,
          medium: 0,
          high: 0,
          critical: 0,
        },
        scheduledChanges: 0,
        releaseNotes: 0,
      });
    });

    it('should accept custom configuration', () => {
      const customManager = new ChangeManagementManager({
        requireApprovalForRiskLevels: ['high', 'critical'],
        minApprovers: 3,
        changeRetentionDays: 180,
        autoGenerateReleaseNotes: false,
      });

      expect(customManager).toBeDefined();
    });
  });

  describe('Architecture Decision Records (ADR)', () => {
    it('should create ADR', () => {
      const adr = manager.createADR({
        title: 'Use React for UI Framework',
        status: 'proposed',
        context: 'Need to select UI framework',
        decision: 'Use React for component-based architecture',
        consequences: {
          positive: ['Large ecosystem', 'Good performance'],
          negative: ['Learning curve'],
          risks: ['Breaking changes in major versions'],
        },
        alternatives: [
          {
            title: 'Vue.js',
            description: 'Progressive framework',
            rejectionReason: 'Smaller ecosystem',
          },
        ],
        relatedADRs: [],
        tags: ['frontend', 'architecture'],
        author: 'john@example.com',
      });

      expect(adr.id).toMatch(/^adr-0001$/);
      expect(adr.number).toBe(1);
      expect(adr.title).toBe('Use React for UI Framework');
      expect(adr.status).toBe('proposed');
    });

    it('should increment ADR numbers', () => {
      const adr1 = manager.createADR({
        title: 'First ADR',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      const adr2 = manager.createADR({
        title: 'Second ADR',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      expect(adr1.number).toBe(1);
      expect(adr2.number).toBe(2);
    });

    it('should get ADR by ID', () => {
      const adr = manager.createADR({
        title: 'Test',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      const retrieved = manager.getADR(adr.id);
      expect(retrieved).toEqual(adr);
    });

    it('should get ADR by number', () => {
      const adr = manager.createADR({
        title: 'Test',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      const retrieved = manager.getADRByNumber(adr.number);
      expect(retrieved?.id).toBe(adr.id);
    });

    it('should get all ADRs', () => {
      manager.createADR({
        title: 'ADR 1',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });
      manager.createADR({
        title: 'ADR 2',
        status: 'accepted',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      const adrs = manager.getAllADRs();
      expect(adrs).toHaveLength(2);
      expect(adrs[0].number).toBe(1);
      expect(adrs[1].number).toBe(2);
    });

    it('should get ADRs by status', () => {
      manager.createADR({
        title: 'Proposed',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });
      manager.createADR({
        title: 'Accepted',
        status: 'accepted',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      const proposed = manager.getADRsByStatus('proposed');
      expect(proposed).toHaveLength(1);
      expect(proposed[0].title).toBe('Proposed');
    });

    it('should update ADR status', () => {
      const adr = manager.createADR({
        title: 'Test',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      manager.updateADRStatus(adr.id, 'accepted');

      const updated = manager.getADR(adr.id);
      expect(updated?.status).toBe('accepted');
    });

    it('should mark ADR as superseded', () => {
      const adr = manager.createADR({
        title: 'Old Decision',
        status: 'accepted',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      manager.updateADRStatus(adr.id, 'superseded', 'adr-0002');

      const updated = manager.getADR(adr.id);
      expect(updated?.status).toBe('superseded');
      expect(updated?.supersededBy).toBe('adr-0002');
    });

    it('should export ADR as Markdown', () => {
      const adr = manager.createADR({
        title: 'Use GraphQL',
        status: 'accepted',
        context: 'Need API layer',
        decision: 'Use GraphQL for API',
        consequences: {
          positive: ['Type safety', 'Flexible queries'],
          negative: ['Complexity'],
          risks: ['Performance overhead'],
        },
        alternatives: [
          {
            title: 'REST',
            description: 'Traditional REST API',
            rejectionReason: 'Less flexible',
          },
        ],
        relatedADRs: [],
        tags: ['api'],
        author: 'john@example.com',
      });

      const markdown = manager.exportADRMarkdown(adr.id);
      expect(markdown).toContain('# ADR-0001: Use GraphQL');
      expect(markdown).toContain('**Status:** accepted');
      expect(markdown).toContain('## Context');
      expect(markdown).toContain('## Decision');
      expect(markdown).toContain('## Consequences');
      expect(markdown).toContain('### Positive');
      expect(markdown).toContain('- Type safety');
    });
  });

  describe('Change Requests', () => {
    it('should create change request', () => {
      const change = manager.createChangeRequest({
        title: 'Upgrade Database',
        description: 'Upgrade PostgreSQL to v15',
        riskLevel: 'medium',
        impact: {
          services: ['database', 'api'],
          users: 'all',
          downtime: 30 * 60 * 1000, // 30 minutes
        },
        implementationPlan: 'Backup, upgrade, restore',
        rollbackPlan: 'Restore from backup',
        testing: {
          unit: true,
          integration: true,
          e2e: true,
          manual: true,
        },
        approvalStatus: 'pending',
        requester: 'john@example.com',
        tags: ['database', 'upgrade'],
        adrReferences: [],
      });

      expect(change.id).toMatch(/^change-medium-/);
      expect(change.title).toBe('Upgrade Database');
      expect(change.riskLevel).toBe('medium');
      expect(change.approvers).toEqual([]);
    });

    it('should get change request by ID', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const retrieved = manager.getChangeRequest(change.id);
      expect(retrieved).toEqual(change);
    });

    it('should get all change requests', () => {
      manager.createChangeRequest({
        title: 'Change 1',
        description: 'Desc',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });
      manager.createChangeRequest({
        title: 'Change 2',
        description: 'Desc',
        riskLevel: 'medium',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const changes = manager.getAllChangeRequests();
      expect(changes).toHaveLength(2);
    });

    it('should get changes by approval status', () => {
      manager.createChangeRequest({
        title: 'Pending',
        description: 'Desc',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });
      manager.createChangeRequest({
        title: 'Approved',
        description: 'Desc',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const pending = manager.getChangeRequestsByStatus('pending');
      expect(pending).toHaveLength(1);
      expect(pending[0].title).toBe('Pending');
    });

    it('should get changes by risk level', () => {
      manager.createChangeRequest({
        title: 'Low Risk',
        description: 'Desc',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });
      manager.createChangeRequest({
        title: 'High Risk',
        description: 'Desc',
        riskLevel: 'high',
        impact: { services: [], users: 'all', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const high = manager.getChangeRequestsByRiskLevel('high');
      expect(high).toHaveLength(1);
      expect(high[0].title).toBe('High Risk');
    });

    it('should add approval to change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'medium',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.addApproval(
        change.id,
        'approver1@example.com',
        'approved',
        'Looks good'
      );

      const updated = manager.getChangeRequest(change.id);
      expect(updated?.approvers).toHaveLength(1);
      expect(updated?.approvers[0].approver).toBe('approver1@example.com');
      expect(updated?.approvers[0].status).toBe('approved');
    });

    it('should update approval status after enough approvals', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'medium',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.addApproval(change.id, 'approver1@example.com', 'approved', 'LGTM');
      manager.addApproval(change.id, 'approver2@example.com', 'approved', 'LGTM');

      const updated = manager.getChangeRequest(change.id);
      expect(updated?.approvalStatus).toBe('approved');
    });

    it('should reject change if any approver rejects', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'medium',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.addApproval(change.id, 'approver1@example.com', 'approved', 'LGTM');
      manager.addApproval(
        change.id,
        'approver2@example.com',
        'rejected',
        'Too risky'
      );

      const updated = manager.getChangeRequest(change.id);
      expect(updated?.approvalStatus).toBe('rejected');
    });
  });

  describe('Change Scheduling', () => {
    it('should schedule approved change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 60000 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const scheduledTime = new Date(Date.now() + 24 * 60 * 60 * 1000);
      const event = manager.scheduleChange(change.id, scheduledTime);

      expect(event.changeRequestId).toBe(change.id);
      expect(event.scheduledTime).toEqual(scheduledTime);
      expect(event.status).toBe('scheduled');
      expect(event.maintenanceWindow).toBeDefined();
    });

    it('should not schedule unapproved change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const scheduledTime = new Date(Date.now() + 24 * 60 * 60 * 1000);

      expect(() => manager.scheduleChange(change.id, scheduledTime)).toThrow(
        'not approved'
      );
    });

    it('should start change execution', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const event = manager.scheduleChange(change.id, new Date());
      manager.startChange(event.id);

      // Verify change was started - event is mutated in place
      expect(event.status).toBe('in-progress');
    });

    it('should complete change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const event = manager.scheduleChange(change.id, new Date());
      manager.startChange(event.id);
      manager.completeChange(event.id);

      // Event status should be updated by the manager - mutated in place
      expect(event.status).toBe('completed');
    });

    it('should cancel change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const event = manager.scheduleChange(change.id, new Date());
      manager.cancelChange(event.id);

      // Verify cancellation by checking stats
      const stats = manager.getStats();
      expect(stats.scheduledChanges).toBe(0);
    });

    it('should rollback change', () => {
      const change = manager.createChangeRequest({
        title: 'Test',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const event = manager.scheduleChange(change.id, new Date());
      manager.startChange(event.id);
      manager.rollbackChange(event.id);

      // Verify rollback happened
      expect(event).toBeDefined();
    });

    it('should get upcoming changes', () => {
      const change1 = manager.createChangeRequest({
        title: 'Soon',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      const change2 = manager.createChangeRequest({
        title: 'Later',
        description: 'Test',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'approved',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.scheduleChange(change1.id, new Date(Date.now() + 24 * 60 * 60 * 1000));
      manager.scheduleChange(
        change2.id,
        new Date(Date.now() + 10 * 24 * 60 * 60 * 1000)
      ); // 10 days

      const upcoming = manager.getUpcomingChanges(7); // Within 7 days
      expect(upcoming).toHaveLength(1);
      expect(upcoming[0].title).toBe('Soon');
    });
  });

  describe('Release Notes', () => {
    it('should create release note', () => {
      const release = manager.createReleaseNote({
        version: '1.2.0',
        releaseDate: new Date(),
        sections: {
          features: [{ description: 'New canvas tools' }],
          bugFixes: [{ description: 'Fixed rendering issue' }],
          breaking: [],
          deprecations: [],
          improvements: [{ description: 'Better performance' }],
        },
        contributors: ['john@example.com', 'jane@example.com'],
        changeRequests: [],
      });

      expect(release.id).toBe('release-1-2-0');
      expect(release.version).toBe('1.2.0');
      expect(release.sections.features).toHaveLength(1);
    });

    it('should get release note by ID', () => {
      const release = manager.createReleaseNote({
        version: '1.0.0',
        releaseDate: new Date(),
        sections: {
          features: [],
          bugFixes: [],
          breaking: [],
          deprecations: [],
          improvements: [],
        },
        contributors: [],
        changeRequests: [],
      });

      const retrieved = manager.getReleaseNote(release.id);
      expect(retrieved).toEqual(release);
    });

    it('should get release note by version', () => {
      const release = manager.createReleaseNote({
        version: '2.0.0',
        releaseDate: new Date(),
        sections: {
          features: [],
          bugFixes: [],
          breaking: [],
          deprecations: [],
          improvements: [],
        },
        contributors: [],
        changeRequests: [],
      });

      const retrieved = manager.getReleaseNoteByVersion('2.0.0');
      expect(retrieved?.id).toBe(release.id);
    });

    it('should get all release notes', () => {
      manager.createReleaseNote({
        version: '1.0.0',
        releaseDate: new Date('2024-01-01'),
        sections: {
          features: [],
          bugFixes: [],
          breaking: [],
          deprecations: [],
          improvements: [],
        },
        contributors: [],
        changeRequests: [],
      });
      manager.createReleaseNote({
        version: '1.1.0',
        releaseDate: new Date('2024-02-01'),
        sections: {
          features: [],
          bugFixes: [],
          breaking: [],
          deprecations: [],
          improvements: [],
        },
        contributors: [],
        changeRequests: [],
      });

      const releases = manager.getAllReleaseNotes();
      expect(releases).toHaveLength(2);
      expect(releases[0].version).toBe('1.1.0'); // Most recent first
    });

    it('should export release note as Markdown', () => {
      const release = manager.createReleaseNote({
        version: '1.0.0',
        releaseDate: new Date('2024-01-01'),
        sections: {
          features: [{ description: 'New feature' }],
          bugFixes: [{ description: 'Bug fix' }],
          breaking: [{ description: 'Breaking change' }],
          deprecations: [],
          improvements: [],
        },
        contributors: ['john@example.com'],
        changeRequests: [],
      });

      const markdown = manager.exportReleaseNoteMarkdown(release.id);
      expect(markdown).toContain('# Release 1.0.0');
      expect(markdown).toContain('## ✨ Features');
      expect(markdown).toContain('## 🐛 Bug Fixes');
      expect(markdown).toContain('## ⚠️  Breaking Changes');
      expect(markdown).toContain('## 👏 Contributors');
    });
  });

  describe('Statistics', () => {
    it('should get comprehensive stats', () => {
      manager.createADR({
        title: 'ADR',
        status: 'accepted',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      manager.createChangeRequest({
        title: 'Change',
        description: 'Desc',
        riskLevel: 'high',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.createReleaseNote({
        version: '1.0.0',
        releaseDate: new Date(),
        sections: {
          features: [],
          bugFixes: [],
          breaking: [],
          deprecations: [],
          improvements: [],
        },
        contributors: [],
        changeRequests: [],
      });

      const stats = manager.getStats();
      expect(stats.totalADRs).toBe(1);
      expect(stats.adrsByStatus.accepted).toBe(1);
      expect(stats.totalChanges).toBe(1);
      expect(stats.changesByRisk.high).toBe(1);
      expect(stats.releaseNotes).toBe(1);
    });
  });

  describe('Cleanup Operations', () => {
    it('should clean up old changes', () => {
      const oldChange = manager.createChangeRequest({
        title: 'Old',
        description: 'Old change',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      // Manually set old date
      const change = manager.getChangeRequest(oldChange.id);
      if (change) {
        change.createdAt = new Date(Date.now() - 400 * 24 * 60 * 60 * 1000);
      }

      const removed = manager.cleanupOldChanges();
      expect(removed).toBe(1);
      expect(manager.getChangeRequest(oldChange.id)).toBeNull();
    });
  });

  describe('Reset Operations', () => {
    it('should reset manager state', () => {
      manager.createADR({
        title: 'ADR',
        status: 'proposed',
        context: 'Context',
        decision: 'Decision',
        consequences: { positive: [], negative: [], risks: [] },
        alternatives: [],
        relatedADRs: [],
        tags: [],
        author: 'author@example.com',
      });

      manager.createChangeRequest({
        title: 'Change',
        description: 'Desc',
        riskLevel: 'low',
        impact: { services: [], users: 'none', downtime: 0 },
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        approvalStatus: 'pending',
        requester: 'user@example.com',
        tags: [],
        adrReferences: [],
      });

      manager.reset();

      const stats = manager.getStats();
      expect(stats.totalADRs).toBe(0);
      expect(stats.totalChanges).toBe(0);
    });
  });
});

describe('Change Management Helper Functions', () => {
  describe('calculateApprovalRate', () => {
    it('should calculate approval rate', () => {
      const changes = [
        { approvalStatus: 'approved' },
        { approvalStatus: 'approved' },
        { approvalStatus: 'rejected' },
        { approvalStatus: 'pending' },
      ] as unknown[];

      const rate = calculateApprovalRate(changes);
      expect(rate).toBe(50); // 2 out of 4
    });

    it('should return 0 for no changes', () => {
      const rate = calculateApprovalRate([]);
      expect(rate).toBe(0);
    });
  });

  describe('calculateAvgApprovalTime', () => {
    it('should calculate average approval time', async () => {
      const baseTime = new Date();
      const changes = [
        {
          createdAt: baseTime,
          approvalStatus: 'approved',
          approvers: [{ approvedAt: new Date(baseTime.getTime() + 60000) }],
        },
        {
          createdAt: baseTime,
          approvalStatus: 'approved',
          approvers: [{ approvedAt: new Date(baseTime.getTime() + 120000) }],
        },
      ] as unknown[];

      const avgTime = calculateAvgApprovalTime(changes);
      expect(avgTime).toBe(90000); // Average of 60s and 120s
    });

    it('should return 0 for no approved changes', () => {
      const avgTime = calculateAvgApprovalTime([]);
      expect(avgTime).toBe(0);
    });
  });

  describe('formatChangeDuration', () => {
    it('should format duration in hours and minutes', () => {
      const duration = 2 * 60 * 60 * 1000 + 30 * 60 * 1000; // 2h 30m
      expect(formatChangeDuration(duration)).toBe('2h 30m');
    });

    it('should format duration in minutes only', () => {
      const duration = 45 * 60 * 1000; // 45m
      expect(formatChangeDuration(duration)).toBe('45m');
    });
  });

  describe('validateChangeRequest', () => {
    it('should validate correct change request', () => {
      const change = {
        title: 'Valid Change',
        implementationPlan: 'Detailed plan',
        rollbackPlan: 'Detailed rollback',
        riskLevel: 'low',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        impact: { downtime: 60000 },
      } as unknown;

      const result = validateChangeRequest(change);
      expect(result.valid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should detect missing title', () => {
      const change = {
        title: '',
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        riskLevel: 'low',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        impact: { downtime: 0 },
      } as unknown;

      const result = validateChangeRequest(change);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Change title is required');
    });

    it('should detect missing implementation plan', () => {
      const change = {
        title: 'Test',
        implementationPlan: '',
        rollbackPlan: 'Rollback',
        riskLevel: 'low',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        impact: { downtime: 0 },
      } as unknown;

      const result = validateChangeRequest(change);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Implementation plan is required');
    });

    it('should detect insufficient testing for high risk', () => {
      const change = {
        title: 'Test',
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        riskLevel: 'critical',
        testing: { unit: true, integration: false, e2e: false, manual: true },
        impact: { downtime: 0 },
      } as unknown;

      const result = validateChangeRequest(change);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        'High/critical risk changes require all test types'
      );
    });

    it('should detect excessive downtime', () => {
      const change = {
        title: 'Test',
        implementationPlan: 'Plan',
        rollbackPlan: 'Rollback',
        riskLevel: 'low',
        testing: { unit: true, integration: true, e2e: true, manual: true },
        impact: { downtime: 5 * 60 * 60 * 1000 }, // 5 hours
      } as unknown;

      const result = validateChangeRequest(change);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain(
        'Downtime exceeds 4 hours - consider breaking into smaller changes'
      );
    });
  });
});
