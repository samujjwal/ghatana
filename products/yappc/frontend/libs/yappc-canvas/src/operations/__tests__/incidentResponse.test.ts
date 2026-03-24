/**
 * Tests for Canvas Incident Response System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  IncidentResponseManager,
  type IncidentSeverity,
  type IncidentStatus,
  calculateMTTR,
  calculateMTTA,
  formatIncidentDuration,
  validateEscalationPolicy,
  calculateSeverity,
} from '../incidentResponse';

describe('IncidentResponseManager', () => {
  let manager: IncidentResponseManager;

  beforeEach(() => {
    manager = new IncidentResponseManager();
  });

  describe('Initialization', () => {
    it('should create manager with default configuration', () => {
      expect(manager).toBeDefined();
      expect(manager.getStats()).toEqual({
        totalIncidents: 0,
        activeIncidents: 0,
        bySeverity: { sev1: 0, sev2: 0, sev3: 0, sev4: 0 },
        byStatus: {
          investigating: 0,
          identified: 0,
          monitoring: 0,
          resolved: 0,
          postmortem: 0,
        },
        avgTimeToIdentify: 0,
        avgTimeToResolve: 0,
        escalationPolicies: 0,
        rotations: 0,
        postmortems: 0,
      });
    });

    it('should accept custom configuration', () => {
      const customManager = new IncidentResponseManager({
        defaultEscalationDelayMinutes: 30,
        postmortemRequiredForSeverities: ['sev1'],
        incidentRetentionDays: 60,
        autoEscalationEnabled: false,
      });

      expect(customManager).toBeDefined();
    });
  });

  describe('Incident Management', () => {
    it('should create incident', () => {
      const incident = manager.createIncident({
        title: 'API Outage',
        description: 'Main API is down',
        severity: 'sev1',
        status: 'investigating',
        affectedServices: ['api', 'web'],
        assignedTo: ['john@example.com'],
        tags: ['api', 'outage'],
      });

      expect(incident.id).toMatch(/^inc-sev1-/);
      expect(incident.title).toBe('API Outage');
      expect(incident.severity).toBe('sev1');
      expect(incident.currentEscalationLevel).toBe(0);
      expect(incident.updates).toEqual([]);
      expect(incident.startTime).toBeInstanceOf(Date);
    });

    it('should get incident by ID', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test incident',
        severity: 'sev3',
        status: 'investigating',
        affectedServices: ['test'],
        assignedTo: [],
        tags: [],
      });

      const retrieved = manager.getIncident(incident.id);
      expect(retrieved).toEqual(incident);
    });

    it('should get all incidents', () => {
      manager.createIncident({
        title: 'Incident 1',
        description: 'Desc 1',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Incident 2',
        description: 'Desc 2',
        severity: 'sev3',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const incidents = manager.getAllIncidents();
      expect(incidents).toHaveLength(2);
    });

    it('should get incidents by status', () => {
      manager.createIncident({
        title: 'Active',
        description: 'Active incident',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Resolved',
        description: 'Resolved incident',
        severity: 'sev3',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const investigating = manager.getIncidentsByStatus('investigating');
      expect(investigating).toHaveLength(1);
      expect(investigating[0].title).toBe('Active');
    });

    it('should get incidents by severity', () => {
      manager.createIncident({
        title: 'Critical',
        description: 'Critical incident',
        severity: 'sev1',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Minor',
        description: 'Minor incident',
        severity: 'sev4',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const sev1 = manager.getIncidentsBySeverity('sev1');
      expect(sev1).toHaveLength(1);
      expect(sev1[0].title).toBe('Critical');
    });

    it('should get active incidents', () => {
      manager.createIncident({
        title: 'Active 1',
        description: 'Active',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Active 2',
        description: 'Active',
        severity: 'sev3',
        status: 'identified',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Resolved',
        description: 'Resolved',
        severity: 'sev3',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const active = manager.getActiveIncidents();
      expect(active).toHaveLength(2);
    });

    it('should update incident status', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.updateIncidentStatus(
        incident.id,
        'identified',
        'jane@example.com',
        'Root cause found'
      );

      const updated = manager.getIncident(incident.id);
      expect(updated?.status).toBe('identified');
      expect(updated?.identifiedTime).toBeInstanceOf(Date);
      expect(updated?.updates).toHaveLength(1);
      expect(updated?.updates[0].author).toBe('jane@example.com');
      expect(updated?.updates[0].message).toBe('Root cause found');
    });

    it('should track resolved time', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.updateIncidentStatus(
        incident.id,
        'resolved',
        'john@example.com',
        'Issue fixed'
      );

      const updated = manager.getIncident(incident.id);
      expect(updated?.resolvedTime).toBeInstanceOf(Date);
    });

    it('should add incident update', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.addIncidentUpdate(
        incident.id,
        'john@example.com',
        'Investigating database',
        ['slack']
      );

      const updated = manager.getIncident(incident.id);
      expect(updated?.updates).toHaveLength(1);
      expect(updated?.updates[0].message).toBe('Investigating database');
      expect(updated?.updates[0].notifyChannels).toEqual(['slack']);
    });

    it('should assign incident to user', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.assignIncident(incident.id, 'john@example.com');

      const updated = manager.getIncident(incident.id);
      expect(updated?.assignedTo).toContain('john@example.com');
    });

    it('should not duplicate assignment', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: ['john@example.com'],
        tags: [],
      });

      manager.assignIncident(incident.id, 'john@example.com');

      const updated = manager.getIncident(incident.id);
      expect(updated?.assignedTo).toEqual(['john@example.com']);
    });
  });

  describe('Escalation Policies', () => {
    it('should create escalation policy', () => {
      const policy = manager.createEscalationPolicy({
        name: 'Standard Escalation',
        description: 'Standard escalation path',
        levels: [
          {
            level: 1,
            escalationDelayMinutes: 15,
            notifyUsers: ['oncall@example.com'],
            notifyChannels: ['pagerduty'],
          },
          {
            level: 2,
            escalationDelayMinutes: 30,
            notifyUsers: ['manager@example.com'],
            notifyChannels: ['slack', 'email'],
          },
        ],
      });

      expect(policy.id).toMatch(/^policy-standard-escalation-/);
      expect(policy.levels).toHaveLength(2);
    });

    it('should get escalation policy by ID', () => {
      const policy = manager.createEscalationPolicy({
        name: 'Test',
        description: 'Test policy',
        levels: [],
      });

      const retrieved = manager.getEscalationPolicy(policy.id);
      expect(retrieved).toEqual(policy);
    });

    it('should get all escalation policies', () => {
      manager.createEscalationPolicy({
        name: 'Policy 1',
        description: 'Desc',
        levels: [],
      });
      manager.createEscalationPolicy({
        name: 'Policy 2',
        description: 'Desc',
        levels: [],
      });

      const policies = manager.getAllEscalationPolicies();
      expect(policies).toHaveLength(2);
    });

    it('should escalate incident to next level', () => {
      const policy = manager.createEscalationPolicy({
        name: 'Test',
        description: 'Test',
        levels: [
          {
            level: 1,
            escalationDelayMinutes: 15,
            notifyUsers: ['level1@example.com'],
            notifyChannels: [],
          },
          {
            level: 2,
            escalationDelayMinutes: 30,
            notifyUsers: ['level2@example.com'],
            notifyChannels: [],
          },
        ],
      });

      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
        escalationPolicyId: policy.id,
      });

      manager.escalateIncident(incident.id);

      const updated = manager.getIncident(incident.id);
      expect(updated?.currentEscalationLevel).toBe(1);
    });
  });

  describe('On-Call Rotation', () => {
    it('should create rotation', () => {
      const rotation = manager.createRotation({
        name: 'Engineering Rotation',
        description: 'Primary on-call rotation',
        teamMembers: ['alice@example.com', 'bob@example.com', 'charlie@example.com'],
        shiftDuration: 7 * 24 * 60 * 60 * 1000, // 1 week
      });

      expect(rotation.id).toMatch(/^rotation-engineering-rotation-/);
      expect(rotation.currentShift.userId).toBe('alice@example.com');
      expect(rotation.currentShift.isActive).toBe(true);
      expect(rotation.futureShifts).toHaveLength(3);
    });

    it('should get rotation by ID', () => {
      const rotation = manager.createRotation({
        name: 'Test',
        description: 'Test',
        teamMembers: ['user1@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      const retrieved = manager.getRotation(rotation.id);
      expect(retrieved).toEqual(rotation);
    });

    it('should get all rotations', () => {
      manager.createRotation({
        name: 'Rotation 1',
        description: 'Desc',
        teamMembers: ['user1@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });
      manager.createRotation({
        name: 'Rotation 2',
        description: 'Desc',
        teamMembers: ['user2@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      const rotations = manager.getAllRotations();
      expect(rotations).toHaveLength(2);
    });

    it('should get current on-call user', () => {
      const rotation = manager.createRotation({
        name: 'Test',
        description: 'Test',
        teamMembers: ['alice@example.com', 'bob@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      const oncall = manager.getCurrentOnCall(rotation.id);
      expect(oncall).toBe('alice@example.com');
    });

    it('should rotate to next shift', () => {
      const rotation = manager.createRotation({
        name: 'Test',
        description: 'Test',
        teamMembers: ['alice@example.com', 'bob@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      manager.rotateShift(rotation.id);

      const updated = manager.getRotation(rotation.id);
      expect(updated?.currentShift.userId).toBe('bob@example.com');
      expect(updated?.currentShift.isActive).toBe(true);
    });

    it('should generate more future shifts after rotation', () => {
      const rotation = manager.createRotation({
        name: 'Test',
        description: 'Test',
        teamMembers: ['alice@example.com', 'bob@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      expect(rotation.futureShifts).toHaveLength(3);

      manager.rotateShift(rotation.id);

      const updated = manager.getRotation(rotation.id);
      expect(updated?.futureShifts.length).toBeGreaterThanOrEqual(3);
    });
  });

  describe('Pager Notifications', () => {
    it('should send pager notification', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev3',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const notification = manager.sendPagerNotification(
        incident.id,
        'john@example.com',
        'sms'
      );

      expect(notification.incidentId).toBe(incident.id);
      expect(notification.userId).toBe('john@example.com');
      expect(notification.method).toBe('sms');
      expect(notification.sentAt).toBeInstanceOf(Date);
    });

    it('should acknowledge notification', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev3',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const notification = manager.sendPagerNotification(
        incident.id,
        'john@example.com',
        'push'
      );

      manager.acknowledgeNotification(notification.id);

      const notifications = manager.getNotificationsByIncident(incident.id);
      expect(notifications[0].acknowledgedAt).toBeInstanceOf(Date);
    });

    it('should get notifications by incident', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev3',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.sendPagerNotification(incident.id, 'user1@example.com', 'sms');
      manager.sendPagerNotification(incident.id, 'user2@example.com', 'email');

      const notifications = manager.getNotificationsByIncident(incident.id);
      expect(notifications).toHaveLength(2);
    });
  });

  describe('Postmortems', () => {
    it('should create postmortem', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const postmortem = manager.createPostmortem({
        incidentId: incident.id,
        title: 'API Outage Postmortem',
        date: new Date(),
        participants: ['john@example.com', 'jane@example.com'],
        summary: 'Database connection pool exhausted',
        timeline: [
          {
            time: new Date(),
            event: 'Incident detected',
            author: 'monitoring',
          },
        ],
        rootCause: 'Connection leak in legacy code',
        impact: {
          usersAffected: 10000,
          duration: 3600000,
          servicesAffected: ['api', 'web'],
        },
        resolution: 'Fixed connection leak, increased pool size',
        actionItems: [],
        lessonsLearned: ['Need better connection monitoring'],
      });

      expect(postmortem.id).toMatch(/^pm-/);
      expect(postmortem.incidentId).toBe(incident.id);

      // Check incident was updated
      const updatedIncident = manager.getIncident(incident.id);
      expect(updatedIncident?.postmortemUrl).toBe(postmortem.id);
      expect(updatedIncident?.status).toBe('postmortem');
    });

    it('should get postmortem by ID', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const postmortem = manager.createPostmortem({
        incidentId: incident.id,
        title: 'Test PM',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });

      const retrieved = manager.getPostmortem(postmortem.id);
      expect(retrieved).toEqual(postmortem);
    });

    it('should get postmortem by incident ID', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const postmortem = manager.createPostmortem({
        incidentId: incident.id,
        title: 'Test PM',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });

      const retrieved = manager.getPostmortemByIncident(incident.id);
      expect(retrieved?.id).toBe(postmortem.id);
    });

    it('should get all postmortems', () => {
      const incident1 = manager.createIncident({
        title: 'Test 1',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      const incident2 = manager.createIncident({
        title: 'Test 2',
        description: 'Test',
        severity: 'sev2',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.createPostmortem({
        incidentId: incident1.id,
        title: 'PM 1',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });
      manager.createPostmortem({
        incidentId: incident2.id,
        title: 'PM 2',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });

      const postmortems = manager.getAllPostmortems();
      expect(postmortems).toHaveLength(2);
    });

    it('should add action item to postmortem', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const postmortem = manager.createPostmortem({
        incidentId: incident.id,
        title: 'Test PM',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });

      manager.addActionItem(postmortem.id, {
        description: 'Fix connection leak',
        assignee: 'john@example.com',
        dueDate: new Date(),
        status: 'open',
        priority: 'high',
      });

      const updated = manager.getPostmortem(postmortem.id);
      expect(updated?.actionItems).toHaveLength(1);
      expect(updated?.actionItems[0].description).toBe('Fix connection leak');
    });

    it('should update action item status', () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev1',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const postmortem = manager.createPostmortem({
        incidentId: incident.id,
        title: 'Test PM',
        date: new Date(),
        participants: [],
        summary: 'Test',
        timeline: [],
        rootCause: 'Test',
        impact: { usersAffected: 0, duration: 0, servicesAffected: [] },
        resolution: 'Test',
        actionItems: [],
        lessonsLearned: [],
      });

      manager.addActionItem(postmortem.id, {
        description: 'Test item',
        assignee: 'john@example.com',
        dueDate: new Date(),
        status: 'open',
        priority: 'medium',
      });

      const pm = manager.getPostmortem(postmortem.id);
      const actionItemId = pm!.actionItems[0].id;

      manager.updateActionItemStatus(postmortem.id, actionItemId, 'completed');

      const updated = manager.getPostmortem(postmortem.id);
      expect(updated?.actionItems[0].status).toBe('completed');
    });
  });

  describe('Incident Runbooks', () => {
    it('should create runbook', () => {
      const runbook = manager.createRunbook({
        title: 'Database Connection Issues',
        description: 'How to diagnose and fix DB issues',
        triggers: ['high latency', 'connection timeout', 'pool exhausted'],
        steps: [
          {
            order: 1,
            description: 'Check connection pool metrics',
            commands: ['kubectl get pods', 'tail -f app.log'],
            checkpoints: ['Pool size verified', 'No connection leaks'],
          },
          {
            order: 2,
            description: 'Restart affected services',
            checkpoints: ['Services restarted', 'Health checks passing'],
          },
        ],
        escalationCriteria: [
          'Issue persists after 30 minutes',
          'Multiple services affected',
        ],
        relatedIncidents: [],
      });

      expect(runbook.id).toMatch(/^runbook-database-connection-issues-/);
      expect(runbook.steps).toHaveLength(2);
    });

    it('should get runbook by ID', () => {
      const runbook = manager.createRunbook({
        title: 'Test',
        description: 'Test',
        triggers: [],
        steps: [],
        escalationCriteria: [],
        relatedIncidents: [],
      });

      const retrieved = manager.getRunbook(runbook.id);
      expect(retrieved).toEqual(runbook);
    });

    it('should get all runbooks', () => {
      manager.createRunbook({
        title: 'Runbook 1',
        description: 'Desc',
        triggers: [],
        steps: [],
        escalationCriteria: [],
        relatedIncidents: [],
      });
      manager.createRunbook({
        title: 'Runbook 2',
        description: 'Desc',
        triggers: [],
        steps: [],
        escalationCriteria: [],
        relatedIncidents: [],
      });

      const runbooks = manager.getAllRunbooks();
      expect(runbooks).toHaveLength(2);
    });

    it('should search runbooks by trigger', () => {
      manager.createRunbook({
        title: 'DB Runbook',
        description: 'Database issues',
        triggers: ['database timeout', 'connection pool'],
        steps: [],
        escalationCriteria: [],
        relatedIncidents: [],
      });
      manager.createRunbook({
        title: 'API Runbook',
        description: 'API issues',
        triggers: ['api error', '500 errors'],
        steps: [],
        escalationCriteria: [],
        relatedIncidents: [],
      });

      const results = manager.searchRunbooks('database');
      expect(results).toHaveLength(1);
      expect(results[0].title).toBe('DB Runbook');
    });
  });

  describe('Cleanup Operations', () => {
    it('should clean up old incidents', async () => {
      const oldIncident = manager.createIncident({
        title: 'Old',
        description: 'Old',
        severity: 'sev3',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      // Manually set old resolved time
      const incident = manager.getIncident(oldIncident.id);
      if (incident) {
        incident.resolvedTime = new Date(Date.now() - 100 * 24 * 60 * 60 * 1000);
      }

      const removed = manager.cleanupOldIncidents();
      expect(removed).toBe(1);
      expect(manager.getIncident(oldIncident.id)).toBeNull();
    });

    it('should not clean up active incidents', () => {
      manager.createIncident({
        title: 'Active',
        description: 'Active',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      const removed = manager.cleanupOldIncidents();
      expect(removed).toBe(0);
    });
  });

  describe('Statistics', () => {
    it('should get comprehensive stats', () => {
      manager.createIncident({
        title: 'Sev1 Incident',
        description: 'Critical',
        severity: 'sev1',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createIncident({
        title: 'Sev2 Incident',
        description: 'High',
        severity: 'sev2',
        status: 'resolved',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      manager.createEscalationPolicy({
        name: 'Policy',
        description: 'Test',
        levels: [],
      });

      manager.createRotation({
        name: 'Rotation',
        description: 'Test',
        teamMembers: ['user@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      const stats = manager.getStats();
      expect(stats.totalIncidents).toBe(2);
      expect(stats.activeIncidents).toBe(1);
      expect(stats.bySeverity.sev1).toBe(1);
      expect(stats.bySeverity.sev2).toBe(1);
      expect(stats.byStatus.investigating).toBe(1);
      expect(stats.byStatus.resolved).toBe(1);
      expect(stats.escalationPolicies).toBe(1);
      expect(stats.rotations).toBe(1);
    });

    it('should calculate average time to identify', async () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      // Add small delay to ensure measurable time difference
      await new Promise(resolve => setTimeout(resolve, 10));

      manager.updateIncidentStatus(
        incident.id,
        'identified',
        'john@example.com',
        'Found issue'
      );

      const stats = manager.getStats();
      expect(stats.avgTimeToIdentify).toBeGreaterThan(0);
    });

    it('should calculate average time to resolve', async () => {
      const incident = manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });

      // Add small delay to ensure measurable time difference
      await new Promise(resolve => setTimeout(resolve, 10));

      manager.updateIncidentStatus(
        incident.id,
        'resolved',
        'john@example.com',
        'Fixed'
      );

      const stats = manager.getStats();
      expect(stats.avgTimeToResolve).toBeGreaterThan(0);
    });
  });

  describe('Reset Operations', () => {
    it('should reset manager state', () => {
      manager.createIncident({
        title: 'Test',
        description: 'Test',
        severity: 'sev2',
        status: 'investigating',
        affectedServices: [],
        assignedTo: [],
        tags: [],
      });
      manager.createEscalationPolicy({
        name: 'Policy',
        description: 'Test',
        levels: [],
      });
      manager.createRotation({
        name: 'Rotation',
        description: 'Test',
        teamMembers: ['user@example.com'],
        shiftDuration: 24 * 60 * 60 * 1000,
      });

      manager.reset();

      const stats = manager.getStats();
      expect(stats.totalIncidents).toBe(0);
      expect(stats.escalationPolicies).toBe(0);
      expect(stats.rotations).toBe(0);
    });
  });
});

describe('Incident Response Helper Functions', () => {
  describe('calculateMTTR', () => {
    it('should calculate mean time to recovery', () => {
      const incidents = [
        {
          startTime: new Date('2024-01-01T10:00:00'),
          resolvedTime: new Date('2024-01-01T11:00:00'),
        },
        {
          startTime: new Date('2024-01-02T10:00:00'),
          resolvedTime: new Date('2024-01-02T12:00:00'),
        },
      ] as unknown[];

      const mttr = calculateMTTR(incidents);
      expect(mttr).toBe(1.5 * 60 * 60 * 1000); // 1.5 hours in ms
    });

    it('should return 0 for no resolved incidents', () => {
      const mttr = calculateMTTR([]);
      expect(mttr).toBe(0);
    });
  });

  describe('calculateMTTA', () => {
    it('should calculate mean time to acknowledge', () => {
      const incidents = [] as unknown[];
      const notifications = [
        {
          sentAt: new Date('2024-01-01T10:00:00'),
          acknowledgedAt: new Date('2024-01-01T10:05:00'),
        },
        {
          sentAt: new Date('2024-01-02T10:00:00'),
          acknowledgedAt: new Date('2024-01-02T10:03:00'),
        },
      ] as unknown[];

      const mtta = calculateMTTA(incidents, notifications);
      expect(mtta).toBe(4 * 60 * 1000); // 4 minutes in ms
    });

    it('should return 0 for no acknowledged notifications', () => {
      const mtta = calculateMTTA([], []);
      expect(mtta).toBe(0);
    });
  });

  describe('formatIncidentDuration', () => {
    it('should format duration in days and hours', () => {
      const duration = 2 * 24 * 60 * 60 * 1000 + 3 * 60 * 60 * 1000; // 2d 3h
      expect(formatIncidentDuration(duration)).toBe('2d 3h');
    });

    it('should format duration in hours and minutes', () => {
      const duration = 3 * 60 * 60 * 1000 + 45 * 60 * 1000; // 3h 45m
      expect(formatIncidentDuration(duration)).toBe('3h 45m');
    });

    it('should format duration in minutes and seconds', () => {
      const duration = 5 * 60 * 1000 + 30 * 1000; // 5m 30s
      expect(formatIncidentDuration(duration)).toBe('5m 30s');
    });

    it('should format duration in seconds', () => {
      const duration = 45 * 1000; // 45s
      expect(formatIncidentDuration(duration)).toBe('45s');
    });
  });

  describe('validateEscalationPolicy', () => {
    it('should validate correct policy', () => {
      const policy = {
        levels: [
          {
            level: 1,
            escalationDelayMinutes: 15,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
          {
            level: 2,
            escalationDelayMinutes: 30,
            notifyUsers: [],
            notifyChannels: ['slack'],
          },
        ],
      } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should detect missing levels', () => {
      const policy = { levels: [] } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Policy must have at least one escalation level');
    });

    it('should detect wrong level ordering', () => {
      const policy = {
        levels: [
          {
            level: 2,
            escalationDelayMinutes: 15,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
          {
            level: 1,
            escalationDelayMinutes: 30,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
        ],
      } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Escalation levels must be in ascending order');
    });

    it('should detect duplicate levels', () => {
      const policy = {
        levels: [
          {
            level: 1,
            escalationDelayMinutes: 15,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
          {
            level: 1,
            escalationDelayMinutes: 30,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
        ],
      } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Duplicate escalation levels found');
    });

    it('should detect level without contacts', () => {
      const policy = {
        levels: [
          {
            level: 1,
            escalationDelayMinutes: 15,
            notifyUsers: [],
            notifyChannels: [],
          },
        ],
      } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Level 1 has no notification contacts');
    });

    it('should detect negative delay', () => {
      const policy = {
        levels: [
          {
            level: 1,
            escalationDelayMinutes: -5,
            notifyUsers: ['user@example.com'],
            notifyChannels: [],
          },
        ],
      } as unknown;

      const result = validateEscalationPolicy(policy);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Level 1 has negative escalation delay');
    });
  });

  describe('calculateSeverity', () => {
    it('should return sev1 for major outage', () => {
      const severity = calculateSeverity({
        usersAffected: 15000,
        servicesDown: 2,
        revenueImpact: 150000,
      });
      expect(severity).toBe('sev1');
    });

    it('should return sev2 for significant impact', () => {
      const severity = calculateSeverity({
        usersAffected: 5000,
        servicesDown: 0,
        revenueImpact: 50000,
      });
      expect(severity).toBe('sev2');
    });

    it('should return sev3 for partial impact', () => {
      const severity = calculateSeverity({
        usersAffected: 500,
        servicesDown: 0,
        revenueImpact: 5000,
      });
      expect(severity).toBe('sev3');
    });

    it('should return sev4 for minor issue', () => {
      const severity = calculateSeverity({
        usersAffected: 50,
        servicesDown: 0,
        revenueImpact: 500,
      });
      expect(severity).toBe('sev4');
    });
  });
});
