/**
 * Feature 2.26: Compliance Mapping - Tests
 *
 * Comprehensive test suite for compliance control tagging, coverage reporting,
 * and audit bundle generation.
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createComplianceStore,
  tagControl,
  updateControlTag,
  removeControlTag,
  getElementTags,
  getControlTags,
  getFrameworkTags,
  getCoverageReport,
  registerControlDefinition,
  getControlDefinition,
  exportAuditBundle,
  searchControlTags,
  getComplianceStatistics,
  type ComplianceStore,
  type ControlDefinition,
  type ControlTag,
  type ControlGap,
} from '../complianceStore';

describe('Feature 2.26: Compliance Mapping', () => {
  let store: ComplianceStore;

  // Sample control definitions
  const soc2Controls: ControlDefinition[] = [
    {
      id: 'CC6.1',
      framework: 'SOC2',
      title: 'Logical and Physical Access Controls',
      description: 'Implements authorization controls for access',
      category: 'Access Control',
      requiredEvidence: ['access-logs', 'policy-doc'],
      relatedControls: ['CC6.2', 'CC6.3'],
    },
    {
      id: 'CC6.2',
      framework: 'SOC2',
      title: 'Authentication',
      description: 'Implements secure authentication mechanisms',
      category: 'Access Control',
      requiredEvidence: ['auth-config', 'mfa-logs'],
      relatedControls: ['CC6.1'],
    },
    {
      id: 'CC7.1',
      framework: 'SOC2',
      title: 'System Monitoring',
      description: 'Monitors system operations',
      category: 'Monitoring',
      requiredEvidence: ['monitoring-dashboard', 'alert-logs'],
      relatedControls: [],
    },
  ];

  beforeEach(() => {
    store = createComplianceStore({
      frameworks: ['SOC2', 'ISO27001'],
      controlDefinitions: soc2Controls,
      requireEvidence: true,
      enableAuditLog: true,
    });
  });

  describe('Store Creation', () => {
    it('should create store with default config', () => {
      const defaultStore = createComplianceStore({
        frameworks: ['SOC2'],
      });

      expect(defaultStore.config.frameworks).toEqual(['SOC2']);
      expect(defaultStore.controlTags).toEqual({});
      expect(defaultStore.auditLog).toEqual([]);
    });

    it('should index provided control definitions', () => {
      expect(Object.keys(store.controlDefinitions)).toHaveLength(3);
      expect(store.controlDefinitions['SOC2:CC6.1']).toBeDefined();
      expect(store.controlDefinitions['SOC2:CC6.1'].title).toBe(
        'Logical and Physical Access Controls'
      );
    });
  });

  describe('Control Tagging', () => {
    it('should tag element with control', () => {
      const updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf', 'test-results.json'],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tags = getElementTags(updated, 'node-1');
      expect(tags).toHaveLength(1);
      expect(tags[0].controlId).toBe('CC6.1');
      expect(tags[0].status).toBe('satisfied');
      expect(tags[0].evidence).toHaveLength(2);
    });

    it('should create audit log entry when tagging', () => {
      const updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      expect(updated.auditLog).toHaveLength(1);
      expect(updated.auditLog[0].action).toBe('control-tagged');
      expect(updated.auditLog[0].actor).toBe('user-1');
      expect(updated.auditLog[0].controlId).toBe('CC6.1');
    });

    it('should throw error if evidence required but missing', () => {
      expect(() =>
        tagControl(store, 'node-1', {
          elementId: 'node-1',
          framework: 'SOC2',
          controlId: 'CC6.1',
          status: 'satisfied',
          evidence: [],
          gaps: [],
          taggedBy: 'user-1',
        })
      ).toThrow('Evidence required');
    });

    it('should allow multiple tags on same element', () => {
      let updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      updated = tagControl(updated, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tags = getElementTags(updated, 'node-1');
      expect(tags).toHaveLength(2);
    });

    it('should index tags by element, control, and framework', () => {
      const updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      expect(updated.tagsByElement['node-1']).toHaveLength(1);
      expect(updated.tagsByControl['CC6.1']).toHaveLength(1);
      expect(updated.tagsByFramework['SOC2']).toHaveLength(1);
    });
  });

  describe('Tag Updates', () => {
    it('should update existing tag', () => {
      let updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tagId = Object.keys(updated.controlTags)[0];

      updated = updateControlTag(updated, tagId, {
        status: 'satisfied',
        evidence: ['new-doc.pdf'],
      }, 'user-2');

      const tag = updated.controlTags[tagId];
      expect(tag.status).toBe('satisfied');
      expect(tag.evidence).toContain('new-doc.pdf');
    });

    it('should create audit log entry for updates', () => {
      let updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tagId = Object.keys(updated.controlTags)[0];

      updated = updateControlTag(updated, tagId, {
        status: 'satisfied',
        evidence: ['doc.pdf'],
      }, 'user-2');

      expect(updated.auditLog).toHaveLength(2);
      expect(updated.auditLog[1].action).toBe('control-updated');
      expect(updated.auditLog[1].previousState?.status).toBe('in-progress');
      expect(updated.auditLog[1].newState?.status).toBe('satisfied');
    });

    it('should throw error for non-existent tag', () => {
      expect(() =>
        updateControlTag(store, 'invalid-id', { status: 'satisfied' }, 'user-1')
      ).toThrow('Control tag not found');
    });
  });

  describe('Tag Removal', () => {
    it('should remove control tag', () => {
      let updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tagId = Object.keys(updated.controlTags)[0];

      updated = removeControlTag(updated, tagId, 'user-1');

      expect(updated.controlTags[tagId]).toBeUndefined();
      expect(updated.tagsByElement['node-1']).toHaveLength(0);
      expect(updated.tagsByControl['CC6.1']).toHaveLength(0);
      expect(updated.tagsByFramework['SOC2']).toHaveLength(0);
    });

    it('should create audit log entry for removal', () => {
      let updated = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      const tagId = Object.keys(updated.controlTags)[0];

      updated = removeControlTag(updated, tagId, 'user-2');

      expect(updated.auditLog).toHaveLength(2);
      expect(updated.auditLog[1].action).toBe('control-removed');
      expect(updated.auditLog[1].actor).toBe('user-2');
    });

    it('should throw error for non-existent tag', () => {
      expect(() => removeControlTag(store, 'invalid-id', 'user-1')).toThrow(
        'Control tag not found'
      );
    });
  });

  describe('Tag Queries', () => {
    beforeEach(() => {
      // Add multiple tags
      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-2', {
        elementId: 'node-2',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        status: 'gap',
        evidence: [],
        gaps: [
          {
            id: 'gap-1',
            description: 'Missing access policy',
            severity: 'high',
            remediation: 'Create access policy document',
            identifiedAt: new Date(),
          },
        ],
        taggedBy: 'user-2',
      });
    });

    it('should get tags for element', () => {
      const tags = getElementTags(store, 'node-1');
      expect(tags).toHaveLength(2);
      expect(tags.map((t) => t.controlId)).toContain('CC6.1');
      expect(tags.map((t) => t.controlId)).toContain('CC6.2');
    });

    it('should get tags for control', () => {
      const tags = getControlTags(store, 'CC6.1');
      expect(tags).toHaveLength(1);
      expect(tags[0].elementId).toBe('node-1');
    });

    it('should get tags for framework', () => {
      const soc2Tags = getFrameworkTags(store, 'SOC2');
      expect(soc2Tags).toHaveLength(2);

      const isoTags = getFrameworkTags(store, 'ISO27001');
      expect(isoTags).toHaveLength(1);
    });

    it('should return empty array for non-existent element', () => {
      const tags = getElementTags(store, 'non-existent');
      expect(tags).toEqual([]);
    });
  });

  describe('Coverage Reports', () => {
    beforeEach(() => {
      // Create diverse tag set
      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-2', {
        elementId: 'node-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        assignedTo: 'user-2',
        dueDate: new Date(Date.now() + 15 * 24 * 60 * 60 * 1000), // 15 days
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-3', {
        elementId: 'node-3',
        framework: 'SOC2',
        controlId: 'CC7.1',
        status: 'gap',
        evidence: [],
        gaps: [
          {
            id: 'gap-1',
            description: 'No monitoring dashboard',
            severity: 'critical',
            remediation: 'Implement monitoring solution',
            identifiedAt: new Date(),
          },
        ],
        taggedBy: 'user-1',
      });
    });

    it('should generate coverage report', () => {
      const report = getCoverageReport(store, 'SOC2');

      expect(report.framework).toBe('SOC2');
      expect(report.totalCount).toBe(3); // Matches control definitions
      expect(report.satisfiedCount).toBe(1);
      expect(report.inProgressCount).toBe(1);
      expect(report.gapCount).toBe(1);
    });

    it('should calculate coverage percentage correctly', () => {
      const report = getCoverageReport(store, 'SOC2');

      // (1 satisfied + 0 partial) / 3 total * 100 = 33.33%
      expect(report.coveragePercentage).toBeCloseTo(33.33, 1);
    });

    it('should identify critical gaps', () => {
      const report = getCoverageReport(store, 'SOC2');

      expect(report.criticalGaps).toHaveLength(1);
      expect(report.criticalGaps[0].description).toBe(
        'No monitoring dashboard'
      );
    });

    it('should track upcoming due dates', () => {
      const report = getCoverageReport(store, 'SOC2');

      expect(report.upcomingDueDates).toHaveLength(1);
      expect(report.upcomingDueDates[0].controlId).toBe('CC6.2');
      expect(report.upcomingDueDates[0].assignedTo).toBe('user-2');
    });

    it('should categorize controls', () => {
      const report = getCoverageReport(store, 'SOC2');

      expect(report.controlsByCategory['Access Control']).toBeDefined();
      expect(report.controlsByCategory['Access Control'].total).toBe(2);
      expect(report.controlsByCategory['Access Control'].satisfied).toBe(1);
    });

    it('should handle partial status in coverage calculation', () => {
      const storeWithPartial = tagControl(store, 'node-4', {
        elementId: 'node-4',
        framework: 'SOC2',
        controlId: 'CC8.1',
        status: 'partial',
        evidence: ['partial-doc.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      const report = getCoverageReport(storeWithPartial, 'SOC2');

      // (1 satisfied + 1 partial * 0.5) / 4 total = 37.5%
      expect(report.coveragePercentage).toBeCloseTo(37.5, 1);
    });
  });

  describe('Control Definitions', () => {
    it('should register new control definition', () => {
      const newDef: ControlDefinition = {
        id: 'A.9.1.1',
        framework: 'ISO27001',
        title: 'Access Control Policy',
        description: 'Establish access control policy',
        category: 'Access Control',
        requiredEvidence: ['policy-doc'],
        relatedControls: [],
      };

      const updated = registerControlDefinition(store, newDef);

      expect(updated.controlDefinitions['ISO27001:A.9.1.1']).toBeDefined();
      expect(updated.controlDefinitions['ISO27001:A.9.1.1'].title).toBe(
        'Access Control Policy'
      );
    });

    it('should retrieve control definition', () => {
      const def = getControlDefinition(store, 'SOC2', 'CC6.1');

      expect(def).toBeDefined();
      expect(def?.title).toBe('Logical and Physical Access Controls');
      expect(def?.requiredEvidence).toContain('access-logs');
    });

    it('should return undefined for non-existent definition', () => {
      const def = getControlDefinition(store, 'SOC2', 'INVALID');
      expect(def).toBeUndefined();
    });
  });

  describe('Audit Bundle Export', () => {
    beforeEach(() => {
      // Add some tags
      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-2', {
        elementId: 'node-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'gap',
        evidence: [],
        gaps: [
          {
            id: 'gap-1',
            description: 'Missing MFA',
            severity: 'high',
            remediation: 'Enable MFA',
            identifiedAt: new Date(),
          },
        ],
        taggedBy: 'user-1',
      });
    });

    it('should export audit bundle', async () => {
      const bundle = await exportAuditBundle(
        store,
        {
          framework: 'SOC2',
          includeEvidence: true,
          includeDiagrams: true,
          includeAuditLogs: true,
          format: 'pdf',
        },
        'auditor-1'
      );

      expect(bundle.framework).toBe('SOC2');
      expect(bundle.generatedBy).toBe('auditor-1');
      expect(bundle.controlTags).toHaveLength(2);
      expect(bundle.coverageReport).toBeDefined();
      expect(bundle.auditLogs).toBeDefined();
    });

    it('should filter by control IDs', async () => {
      const bundle = await exportAuditBundle(
        store,
        {
          framework: 'SOC2',
          includeEvidence: false,
          includeDiagrams: false,
          includeAuditLogs: false,
          format: 'csv',
          controlIds: ['CC6.1'],
        },
        'auditor-1'
      );

      expect(bundle.controlTags).toHaveLength(1);
      expect(bundle.controlTags[0].controlId).toBe('CC6.1');
    });

    it('should filter by date range', async () => {
      const now = new Date();
      const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000);

      const bundle = await exportAuditBundle(
        store,
        {
          framework: 'SOC2',
          includeEvidence: false,
          includeDiagrams: false,
          includeAuditLogs: true,
          format: 'json',
          dateRange: {
            start: yesterday,
            end: tomorrow,
          },
        },
        'auditor-1'
      );

      expect(bundle.controlTags).toHaveLength(2); // Both tagged today
      expect(bundle.auditLogs).toBeDefined();
      expect(bundle.auditLogs!.length).toBeGreaterThan(0);
    });

    it('should include correct metadata', async () => {
      const bundle = await exportAuditBundle(
        store,
        {
          framework: 'SOC2',
          includeEvidence: true,
          includeDiagrams: false,
          includeAuditLogs: false,
          format: 'markdown',
        },
        'auditor-1'
      );

      expect(bundle.metadata.version).toBe('1.0.0');
      expect(bundle.metadata.exportedControls).toBe(2);
      expect(bundle.metadata.includesEvidence).toBe(true);
      expect(bundle.metadata.includesDiagrams).toBe(false);
    });
  });

  describe('Tag Search', () => {
    beforeEach(() => {
      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        assignedTo: 'user-1',
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-2', {
        elementId: 'node-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        assignedTo: 'user-2',
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-3', {
        elementId: 'node-3',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        status: 'gap',
        evidence: [],
        gaps: [
          {
            id: 'gap-1',
            description: 'Missing policy',
            severity: 'critical',
            remediation: 'Create policy',
            identifiedAt: new Date(),
          },
        ],
        taggedBy: 'user-1',
      });
    });

    it('should search by framework', () => {
      const results = searchControlTags(store, { framework: 'SOC2' });
      expect(results).toHaveLength(2);
    });

    it('should search by status', () => {
      const results = searchControlTags(store, { status: 'satisfied' });
      expect(results).toHaveLength(1);
      expect(results[0].controlId).toBe('CC6.1');
    });

    it('should search by assignedTo', () => {
      const results = searchControlTags(store, { assignedTo: 'user-2' });
      expect(results).toHaveLength(1);
      expect(results[0].controlId).toBe('CC6.2');
    });

    it('should search by hasGaps', () => {
      const withGaps = searchControlTags(store, { hasGaps: true });
      expect(withGaps).toHaveLength(1);
      expect(withGaps[0].controlId).toBe('A.9.1.1');

      const withoutGaps = searchControlTags(store, { hasGaps: false });
      expect(withoutGaps).toHaveLength(2);
    });

    it('should search by severity', () => {
      const results = searchControlTags(store, { severity: 'critical' });
      expect(results).toHaveLength(1);
      expect(results[0].gaps[0].severity).toBe('critical');
    });

    it('should combine multiple filters', () => {
      const results = searchControlTags(store, {
        framework: 'SOC2',
        status: 'in-progress',
        assignedTo: 'user-2',
      });
      expect(results).toHaveLength(1);
      expect(results[0].controlId).toBe('CC6.2');
    });
  });

  describe('Compliance Statistics', () => {
    beforeEach(() => {
      // Add tags across frameworks
      store = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['doc1.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-2', {
        elementId: 'node-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        status: 'gap',
        evidence: [],
        gaps: [
          {
            id: 'gap-1',
            description: 'Missing',
            severity: 'critical',
            remediation: 'Fix',
            identifiedAt: new Date(),
          },
        ],
        taggedBy: 'user-1',
      });

      store = tagControl(store, 'node-3', {
        elementId: 'node-3',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        status: 'satisfied',
        evidence: ['iso-doc.pdf'],
        gaps: [],
        taggedBy: 'user-1',
      });
    });

    it('should calculate overall statistics', () => {
      const stats = getComplianceStatistics(store);

      expect(stats.frameworkCount).toBe(2);
      expect(stats.totalTags).toBe(3);
      expect(stats.totalControls).toBe(3);
      expect(stats.criticalGaps).toBe(1);
    });

    it('should calculate per-framework statistics', () => {
      const stats = getComplianceStatistics(store);

      expect(stats.frameworkStats['SOC2']).toBeDefined();
      expect(stats.frameworkStats['SOC2'].tags).toBe(2);
      expect(stats.frameworkStats['SOC2'].gaps).toBe(1);

      expect(stats.frameworkStats['ISO27001']).toBeDefined();
      expect(stats.frameworkStats['ISO27001'].tags).toBe(1);
      expect(stats.frameworkStats['ISO27001'].coverage).toBeGreaterThan(0);
    });

    it('should calculate overall coverage', () => {
      const stats = getComplianceStatistics(store);

      // Average of SOC2 (33.33%) and ISO27001 (100%)
      expect(stats.overallCoverage).toBeGreaterThan(0);
      expect(stats.overallCoverage).toBeLessThanOrEqual(100);
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty store', () => {
      const emptyStore = createComplianceStore({
        frameworks: ['SOC2'],
      });

      const tags = getElementTags(emptyStore, 'node-1');
      expect(tags).toEqual([]);

      const report = getCoverageReport(emptyStore, 'SOC2');
      expect(report.totalCount).toBe(0);
      expect(report.coveragePercentage).toBe(0);
    });

    it('should handle not-applicable status in coverage', () => {
      const storeWithNA = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'not-applicable',
        evidence: [],
        gaps: [],
        implementationNotes: 'Not using this feature',
        taggedBy: 'user-1',
      });

      const report = getCoverageReport(storeWithNA, 'SOC2');

      expect(report.notApplicableCount).toBe(1);
      // Should not count in coverage percentage denominator
      expect(report.coveragePercentage).toBe(0); // 0 satisfied out of (3 - 1 NA) = 0%
    });

    it('should handle duplicate framework in config', () => {
      const dupeStore = createComplianceStore({
        frameworks: ['SOC2', 'SOC2'],
      });

      expect(dupeStore.config.frameworks).toEqual(['SOC2', 'SOC2']);
      // Should still work correctly
    });

    it('should handle tags with future due dates beyond 30 days', () => {
      const storeWithFarDueDate = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'in-progress',
        evidence: [],
        gaps: [],
        dueDate: new Date(Date.now() + 60 * 24 * 60 * 60 * 1000), // 60 days
        taggedBy: 'user-1',
      });

      const report = getCoverageReport(storeWithFarDueDate, 'SOC2');

      expect(report.upcomingDueDates).toHaveLength(0); // Not within 30 days
    });

    it('should handle resolved gaps', () => {
      const resolvedGap: ControlGap = {
        id: 'gap-1',
        description: 'Fixed issue',
        severity: 'critical',
        remediation: 'Applied fix',
        identifiedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000),
        resolvedAt: new Date(),
      };

      const storeWithResolvedGap = tagControl(store, 'node-1', {
        elementId: 'node-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        status: 'satisfied',
        evidence: ['fix-doc.pdf'],
        gaps: [resolvedGap],
        taggedBy: 'user-1',
      });

      const report = getCoverageReport(storeWithResolvedGap, 'SOC2');

      // Should not appear in critical gaps since it's resolved
      expect(report.criticalGaps).toHaveLength(0);
    });
  });
});
