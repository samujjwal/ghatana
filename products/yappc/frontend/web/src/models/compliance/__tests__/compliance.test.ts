/**
 * Compliance Model Tests
 *
 * Unit tests for AuditLogEntry, Evidence, ComplianceControl, and ComplianceReport entities.
 *
 * @doc.type test
 * @doc.purpose Unit tests for compliance domain entities
 * @doc.layer product
 * @doc.pattern Entity Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  AuditLogEntry,
  AuditAction,
  ActionStatus,
} from '../AuditLogEntry.entity';
import { Evidence, EvidenceType, EvidenceStatus } from '../Evidence.entity';
import {
  ComplianceControl,
  ComplianceFramework,
  ControlStatus,
} from '../ComplianceControl.entity';
import { ComplianceReport } from '../ComplianceReport.entity';

// ============================================================================
// AuditLogEntry Tests
// ============================================================================

describe('AuditLogEntry', () => {
  let entry: AuditLogEntry;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2025-01-15T10:00:00.000Z'));
    entry = new AuditLogEntry(
      'entry-1',
      'user-abc',
      'Alice',
      AuditAction.CREATE,
      'Project',
      'proj-123'
    );
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('constructor', () => {
    it('initialises required fields', () => {
      expect(entry.id).toBe('entry-1');
      expect(entry.actorId).toBe('user-abc');
      expect(entry.actorName).toBe('Alice');
      expect(entry.action).toBe(AuditAction.CREATE);
      expect(entry.resource).toBe('Project');
      expect(entry.resourceId).toBe('proj-123');
    });

    it('sets default status to SUCCESS', () => {
      expect(entry.status).toBe(ActionStatus.SUCCESS);
    });

    it('initialises timestamp to current time', () => {
      expect(entry.timestamp).toEqual(new Date('2025-01-15T10:00:00.000Z'));
    });

    it('initialises changes array as empty', () => {
      expect(entry.changes).toEqual([]);
    });

    it('accepts explicit status', () => {
      const failureEntry = new AuditLogEntry(
        'entry-2',
        'user-abc',
        'Alice',
        AuditAction.DELETE,
        'Project',
        'proj-456',
        ActionStatus.FAILURE
      );
      expect(failureEntry.status).toBe(ActionStatus.FAILURE);
    });
  });

  describe('addChange()', () => {
    it('appends a change record', () => {
      entry.addChange('name', 'OldName', 'NewName');
      expect(entry.changes).toHaveLength(1);
      expect(entry.changes[0]).toEqual({
        field: 'name',
        oldValue: 'OldName',
        newValue: 'NewName',
      });
    });

    it('accumulates multiple changes', () => {
      entry.addChange('status', 'DRAFT', 'ACTIVE');
      entry.addChange('description', null, 'A project');
      expect(entry.changes).toHaveLength(2);
    });
  });

  describe('setNetworkDetails()', () => {
    it('records ip address and user agent', () => {
      entry.setNetworkDetails('192.168.1.42', 'Mozilla/5.0');
      expect(entry.ipAddress).toBe('192.168.1.42');
      expect(entry.userAgent).toBe('Mozilla/5.0');
    });
  });

  describe('setFailure()', () => {
    it('sets status to FAILURE and records error details', () => {
      entry.setFailure('Permission denied');
      expect(entry.status).toBe(ActionStatus.FAILURE);
      expect(entry.errorDetails).toBe('Permission denied');
    });
  });

  describe('validate()', () => {
    it('returns true for a valid entry', () => {
      expect(entry.validate()).toBe(true);
    });

    it('throws when id is missing', () => {
      entry.id = '';
      expect(() => entry.validate()).toThrow('required fields missing');
    });

    it('throws when actorId is missing', () => {
      entry.actorId = '';
      expect(() => entry.validate()).toThrow('required fields missing');
    });

    it('throws when timestamp is in the future', () => {
      entry.timestamp = new Date('2025-01-16T10:00:00.000Z');
      expect(() => entry.validate()).toThrow('timestamp in future');
    });
  });

  describe('toJSON()', () => {
    it('serialises all fields to plain object', () => {
      const json = entry.toJSON();
      expect(json['id']).toBe('entry-1');
      expect(json['actorId']).toBe('user-abc');
      expect(json['action']).toBe(AuditAction.CREATE);
      expect(typeof json['timestamp']).toBe('string');
    });
  });

  describe('fromJSON()', () => {
    it('reconstructs entity from serialised data', () => {
      const json = entry.toJSON() as Partial<AuditLogEntry>;
      const restored = AuditLogEntry.fromJSON(json);
      expect(restored.id).toBe(entry.id);
      expect(restored.actorId).toBe(entry.actorId);
      expect(restored.action).toBe(entry.action);
    });

    it('uses defaults for missing fields', () => {
      const minimal = AuditLogEntry.fromJSON({});
      expect(minimal.action).toBe(AuditAction.READ);
      expect(minimal.status).toBe(ActionStatus.SUCCESS);
    });
  });
});

// ============================================================================
// Evidence Tests
// ============================================================================

describe('Evidence', () => {
  let evidence: Evidence;

  beforeEach(() => {
    evidence = new Evidence(
      'ev-1',
      'ctrl-42',
      EvidenceType.DOCUMENT,
      'Security Policy v2',
      'https://storage.example.com/policy.pdf',
      'policy.pdf',
      204800,
      'user-bob'
    );
  });

  describe('constructor', () => {
    it('initialises all required fields', () => {
      expect(evidence.id).toBe('ev-1');
      expect(evidence.controlId).toBe('ctrl-42');
      expect(evidence.type).toBe(EvidenceType.DOCUMENT);
      expect(evidence.title).toBe('Security Policy v2');
      expect(evidence.fileUrl).toBe('https://storage.example.com/policy.pdf');
      expect(evidence.fileName).toBe('policy.pdf');
      expect(evidence.fileSize).toBe(204800);
      expect(evidence.uploadedBy).toBe('user-bob');
    });

    it('sets default status to PENDING_REVIEW', () => {
      expect(evidence.status).toBe(EvidenceStatus.PENDING_REVIEW);
    });

    it('sets uploadedAt to current time', () => {
      expect(evidence.uploadedAt).toBeInstanceOf(Date);
    });
  });

  describe('approve()', () => {
    it('transitions status to APPROVED', () => {
      evidence.approve('reviewer-carol');
      expect(evidence.status).toBe(EvidenceStatus.APPROVED);
    });

    it('records reviewer and review date', () => {
      evidence.approve('reviewer-carol', 'Looks good');
      expect(evidence.reviewedBy).toBe('reviewer-carol');
      expect(evidence.reviewedAt).toBeInstanceOf(Date);
      expect(evidence.reviewComment).toBe('Looks good');
    });
  });

  describe('reject()', () => {
    it('transitions status to REJECTED', () => {
      evidence.reject('reviewer-carol', 'Too old');
      expect(evidence.status).toBe(EvidenceStatus.REJECTED);
      expect(evidence.reviewedBy).toBe('reviewer-carol');
      expect(evidence.reviewComment).toBe('Too old');
    });
  });

  describe('markExpired()', () => {
    it('marks evidence as EXPIRED', () => {
      evidence.markExpired();
      expect(evidence.status).toBe(EvidenceStatus.EXPIRED);
    });
  });
});

// ============================================================================
// ComplianceControl Tests
// ============================================================================

describe('ComplianceControl', () => {
  let control: ComplianceControl;

  beforeEach(() => {
    control = new ComplianceControl(
      'ctrl-1',
      ComplianceFramework.SOC2,
      'CC6.1',
      'Logical Access Controls',
      'Implement controls to restrict access to information assets'
    );
  });

  describe('constructor', () => {
    it('initialises required fields', () => {
      expect(control.id).toBe('ctrl-1');
      expect(control.framework).toBe(ComplianceFramework.SOC2);
      expect(control.controlId).toBe('CC6.1');
      expect(control.title).toBe('Logical Access Controls');
    });

    it('defaults status to NOT_STARTED', () => {
      expect(control.status).toBe(ControlStatus.NOT_STARTED);
    });

    it('initialises empty evidenceIds', () => {
      expect(control.evidenceIds).toEqual([]);
    });

    it('sets nextAssessmentDate 30 days from now', () => {
      const thirtyDays = 30 * 24 * 60 * 60 * 1000;
      const diff = control.nextAssessmentDate.getTime() - Date.now();
      expect(diff).toBeGreaterThan(thirtyDays - 5000);
      expect(diff).toBeLessThanOrEqual(thirtyDays + 5000);
    });
  });

  describe('updateStatus()', () => {
    it('updates status and score', () => {
      control.updateStatus(ControlStatus.COMPLIANT, 95);
      expect(control.status).toBe(ControlStatus.COMPLIANT);
      expect(control.score).toBe(95);
    });

    it('clamps score to 0-100 range', () => {
      control.updateStatus(ControlStatus.PARTIALLY_COMPLIANT, 150);
      expect(control.score).toBe(100);

      control.updateStatus(ControlStatus.PARTIALLY_COMPLIANT, -10);
      expect(control.score).toBe(0);
    });

    it('records lastAssessmentDate', () => {
      control.updateStatus(ControlStatus.COMPLIANT);
      expect(control.lastAssessmentDate).toBeInstanceOf(Date);
    });
  });

  describe('addEvidence()', () => {
    it('adds evidence id', () => {
      control.addEvidence('ev-1');
      expect(control.evidenceIds).toContain('ev-1');
    });

    it('accumulates multiple evidence ids', () => {
      control.addEvidence('ev-1');
      control.addEvidence('ev-2');
      expect(control.evidenceIds).toHaveLength(2);
    });
  });
});

// ============================================================================
// ComplianceReport Tests
// ============================================================================

describe('ComplianceReport', () => {
  const periodStart = new Date('2025-01-01');
  const periodEnd = new Date('2025-03-31');
  let report: ComplianceReport;

  beforeEach(() => {
    report = new ComplianceReport(
      'rpt-1',
      ComplianceFramework.ISO_27001,
      periodStart,
      periodEnd
    );
  });

  describe('constructor', () => {
    it('initialises required fields', () => {
      expect(report.id).toBe('rpt-1');
      expect(report.framework).toBe(ComplianceFramework.ISO_27001);
      expect(report.periodStart).toEqual(periodStart);
      expect(report.periodEnd).toEqual(periodEnd);
    });

    it('initialises control counts and percentage to 0', () => {
      expect(report.controlsTotal).toBe(0);
      expect(report.controlsCompliant).toBe(0);
      expect(report.controlsNonCompliant).toBe(0);
      expect(report.compliancePercentage).toBe(0);
    });

    it('initialises empty findings and recommendations', () => {
      expect(report.findings).toEqual([]);
      expect(report.recommendations).toEqual([]);
    });
  });

  describe('setControlResults()', () => {
    it('calculates compliance percentage correctly', () => {
      report.setControlResults(20, 16);
      expect(report.controlsTotal).toBe(20);
      expect(report.controlsCompliant).toBe(16);
      expect(report.controlsNonCompliant).toBe(4);
      expect(report.compliancePercentage).toBe(80);
    });

    it('handles zero total gracefully', () => {
      report.setControlResults(0, 0);
      expect(report.compliancePercentage).toBe(0);
    });

    it('rounds percentage to nearest integer', () => {
      report.setControlResults(3, 1);
      expect(report.compliancePercentage).toBe(33);
    });
  });

  describe('addFinding()', () => {
    it('appends a finding', () => {
      report.addFinding({
        controlId: 'CC6.1',
        title: 'Missing MFA',
        severity: 'HIGH',
        description: 'MFA not enabled for all admin accounts',
        recommendation: 'Enable MFA for all admin accounts',
      });
      expect(report.findings).toHaveLength(1);
      expect(report.findings[0]?.title).toBe('Missing MFA');
    });
  });

  describe('addRecommendation()', () => {
    it('appends a recommendation string', () => {
      report.addRecommendation('Enable MFA for all privileged accounts');
      expect(report.recommendations).toContain(
        'Enable MFA for all privileged accounts'
      );
    });
  });

  describe('approve()', () => {
    it('marks report as approved', () => {
      report.approve('CISO Dave');
      expect(report.isApproved()).toBe(true);
      expect(report.approvedBy).toBe('CISO Dave');
      expect(report.approvedAt).toBeInstanceOf(Date);
    });
  });

  describe('isApproved()', () => {
    it('returns false for unapproved report', () => {
      expect(report.isApproved()).toBe(false);
    });
  });

  describe('validate()', () => {
    it('returns true for valid report', () => {
      report.setControlResults(5, 4);
      expect(report.validate()).toBe(true);
    });

    it('throws when id is missing', () => {
      report.id = '';
      expect(() => report.validate()).toThrow('required fields missing');
    });

    it('throws when compliant count exceeds total', () => {
      report.controlsTotal = 5;
      report.controlsCompliant = 10;
      expect(() => report.validate()).toThrow('control counts invalid');
    });
  });

  describe('toJSON()', () => {
    it('serialises to plain object with ISO date strings', () => {
      report.setControlResults(10, 8);
      const json = report.toJSON();
      expect(json['id']).toBe('rpt-1');
      expect(json['compliancePercentage']).toBe(80);
      expect(typeof json['generatedAt']).toBe('string');
    });
  });

  describe('fromJSON()', () => {
    it('reconstructs report from JSON', () => {
      report.setControlResults(10, 8);
      const json = report.toJSON() as Partial<ComplianceReport>;
      const restored = ComplianceReport.fromJSON(json);
      expect(restored.id).toBe(report.id);
      expect(restored.controlsTotal).toBe(10);
      expect(restored.compliancePercentage).toBe(80);
    });
  });
});
