/**
 * Tests for ComplianceStore
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createComplianceStore,
  type ComplianceControl,
  type Evidence,

  ComplianceStore} from '../complianceMapping';

describe.skip('ComplianceStore', () => {
  let store: ComplianceStore;

  beforeEach(() => {
    store = createComplianceStore();
  });

  describe('Control Management', () => {
    it('should add control', () => {
      const control = store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Logical Access Controls',
        description: 'Access controls are implemented',
        status: 'planned',
        severity: 'high',
      });

      expect(control.id).toBe('ctrl-1');
      expect(control.evidence).toEqual([]);
      expect(control.nodeIds).toEqual([]);
    });

    it('should get control', () => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Test Control',
        description: 'Test',
        status: 'planned',
        severity: 'medium',
      });

      const control = store.getControl('ctrl-1');
      expect(control).toBeDefined();
      expect(control!.title).toBe('Test Control');
    });

    it('should update control', () => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Test Control',
        description: 'Test',
        status: 'planned',
        severity: 'medium',
      });

      const updated = store.updateControl('ctrl-1', { status: 'implemented' });
      expect(updated).toBe(true);

      const control = store.getControl('ctrl-1');
      expect(control!.status).toBe('implemented');
    });

    it('should return false when updating non-existent control', () => {
      const updated = store.updateControl('non-existent', { status: 'implemented' });
      expect(updated).toBe(false);
    });

    it('should delete control', () => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Test',
        description: 'Test',
        status: 'planned',
        severity: 'medium',
      });

      const deleted = store.deleteControl('ctrl-1');
      expect(deleted).toBe(true);
      expect(store.getControl('ctrl-1')).toBeUndefined();
    });

    it('should return false when deleting non-existent control', () => {
      const deleted = store.deleteControl('non-existent');
      expect(deleted).toBe(false);
    });
  });

  describe('Control Listing and Filtering', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'validated',
        severity: 'high',
        owner: 'alice',
      });

      store.addControl({
        id: 'ctrl-2',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        title: 'Control 2',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
        owner: 'bob',
      });

      store.addControl({
        id: 'ctrl-3',
        framework: 'SOC2',
        controlId: 'CC6.2',
        title: 'Control 3',
        description: 'Test',
        status: 'planned',
        severity: 'high',
        owner: 'alice',
      });
    });

    it('should list all controls', () => {
      const controls = store.listControls();
      expect(controls.length).toBe(3);
    });

    it('should filter by framework', () => {
      const soc2Controls = store.listControls({ framework: 'SOC2' });
      expect(soc2Controls.length).toBe(2);
      expect(soc2Controls.every(c => c.framework === 'SOC2')).toBe(true);
    });

    it('should filter by status', () => {
      const validatedControls = store.listControls({ status: 'validated' });
      expect(validatedControls.length).toBe(1);
      expect(validatedControls[0].id).toBe('ctrl-1');
    });

    it('should filter by severity', () => {
      const highControls = store.listControls({ severity: 'high' });
      expect(highControls.length).toBe(2);
      expect(highControls.every(c => c.severity === 'high')).toBe(true);
    });

    it('should filter by owner', () => {
      const aliceControls = store.listControls({ owner: 'alice' });
      expect(aliceControls.length).toBe(2);
      expect(aliceControls.every(c => c.owner === 'alice')).toBe(true);
    });

    it('should combine filters', () => {
      const filtered = store.listControls({
        framework: 'SOC2',
        severity: 'high',
        owner: 'alice',
      });

      expect(filtered.length).toBe(2);
      expect(filtered.every(c => 
        c.framework === 'SOC2' && 
        c.severity === 'high' && 
        c.owner === 'alice'
      )).toBe(true);
    });
  });

  describe('Node-Control Mapping', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'validated',
        severity: 'high',
      });

      store.addControl({
        id: 'ctrl-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        title: 'Control 2',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
      });
    });

    it('should attach control to node', () => {
      const attached = store.attachControlToNode('ctrl-1', 'node-1');
      expect(attached).toBe(true);

      const control = store.getControl('ctrl-1');
      expect(control!.nodeIds).toContain('node-1');
    });

    it('should return false when attaching non-existent control', () => {
      const attached = store.attachControlToNode('non-existent', 'node-1');
      expect(attached).toBe(false);
    });

    it('should not duplicate node attachments', () => {
      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-1', 'node-1');

      const control = store.getControl('ctrl-1');
      expect(control!.nodeIds.length).toBe(1);
    });

    it('should detach control from node', () => {
      store.attachControlToNode('ctrl-1', 'node-1');
      
      const detached = store.detachControlFromNode('ctrl-1', 'node-1');
      expect(detached).toBe(true);

      const control = store.getControl('ctrl-1');
      expect(control!.nodeIds).not.toContain('node-1');
    });

    it('should return false when detaching non-existent control', () => {
      const detached = store.detachControlFromNode('non-existent', 'node-1');
      expect(detached).toBe(false);
    });

    it('should get controls for node', () => {
      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-2', 'node-1');

      const controls = store.getNodeControls('node-1');
      expect(controls.length).toBe(2);
      expect(controls.map(c => c.id)).toContain('ctrl-1');
      expect(controls.map(c => c.id)).toContain('ctrl-2');
    });

    it('should return empty array for node with no controls', () => {
      const controls = store.getNodeControls('node-without-controls');
      expect(controls).toEqual([]);
    });

    it('should filter controls by node', () => {
      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-2', 'node-2');

      const node1Controls = store.listControls({ nodeId: 'node-1' });
      expect(node1Controls.length).toBe(1);
      expect(node1Controls[0].id).toBe('ctrl-1');
    });

    it('should cleanup node mappings when deleting control', () => {
      store.attachControlToNode('ctrl-1', 'node-1');
      store.deleteControl('ctrl-1');

      const controls = store.getNodeControls('node-1');
      expect(controls).toEqual([]);
    });
  });

  describe('Evidence Management', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'implemented',
        severity: 'high',
      });
    });

    it('should add evidence to control', () => {
      const evidence = store.addEvidence('ctrl-1', {
        type: 'document',
        title: 'Policy Document',
        description: 'Access control policy',
        url: 'https://docs.example.com/policy.pdf',
        uploadedBy: 'alice',
      });

      expect(evidence).toBeDefined();
      expect(evidence!.id).toBeDefined();
      expect(evidence!.timestamp).toBeGreaterThan(0);

      const control = store.getControl('ctrl-1');
      expect(control!.evidence.length).toBe(1);
      expect(control!.evidence[0].title).toBe('Policy Document');
    });

    it('should return null when adding evidence to non-existent control', () => {
      const evidence = store.addEvidence('non-existent', {
        type: 'document',
        title: 'Test',
      });

      expect(evidence).toBeNull();
    });

    it('should support multiple evidence attachments', () => {
      store.addEvidence('ctrl-1', {
        type: 'document',
        title: 'Evidence 1',
      });

      store.addEvidence('ctrl-1', {
        type: 'screenshot',
        title: 'Evidence 2',
      });

      const control = store.getControl('ctrl-1');
      expect(control!.evidence.length).toBe(2);
    });
  });

  describe('Coverage Reports', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'validated',
        severity: 'high',
      });

      store.addControl({
        id: 'ctrl-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        title: 'Control 2',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
      });

      store.addControl({
        id: 'ctrl-3',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        title: 'Control 3',
        description: 'Test',
        status: 'planned',
        severity: 'high',
      });

      store.addControl({
        id: 'ctrl-4',
        framework: 'SOC2',
        controlId: 'CC6.3',
        title: 'Control 4',
        description: 'Test',
        status: 'failed',
        severity: 'critical',
      });

      // Attach some controls to nodes
      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-2', 'node-1');
      store.attachControlToNode('ctrl-3', 'node-2');
    });

    it('should generate overall coverage report', () => {
      const report = store.generateCoverageReport();

      expect(report.totalControls).toBe(4);
      expect(report.overallCoverage).toBeGreaterThan(0);
      expect(report.satisfiedControls.length).toBe(1); // validated
      expect(report.unsatisfiedControls.length).toBe(3);
      expect(report.gaps.length).toBe(1); // planned
    });

    it('should filter coverage report by framework', () => {
      const report = store.generateCoverageReport({ framework: 'SOC2' });

      expect(report.totalControls).toBe(3);
      expect(report.framework).toBe('SOC2');
    });

    it('should count controls by status', () => {
      const report = store.generateCoverageReport();

      expect(report.byStatus.validated).toBe(1);
      expect(report.byStatus.implemented).toBe(1);
      expect(report.byStatus.planned).toBe(1);
      expect(report.byStatus.failed).toBe(1);
    });

    it('should count controls by severity', () => {
      const report = store.generateCoverageReport();

      expect(report.bySeverity.high).toBe(2);
      expect(report.bySeverity.medium).toBe(1);
      expect(report.bySeverity.critical).toBe(1);
    });

    it('should include node coverage', () => {
      const report = store.generateCoverageReport({ includeNodeCoverage: true });

      expect(report.nodeCoverage.length).toBe(2);
      
      const node1Coverage = report.nodeCoverage.find(n => n.nodeId === 'node-1');
      expect(node1Coverage).toBeDefined();
      expect(node1Coverage!.controlIds.length).toBe(2);
    });

    it('should exclude node coverage when requested', () => {
      const report = store.generateCoverageReport({ includeNodeCoverage: false });
      expect(report.nodeCoverage).toEqual([]);
    });

    it('should calculate node coverage percentage', () => {
      const report = store.generateCoverageReport();
      
      const node1Coverage = report.nodeCoverage.find(n => n.nodeId === 'node-1');
      expect(node1Coverage!.coverage).toBe(50); // 1 validated out of 2
    });

    it('should identify nodes with failures', () => {
      store.attachControlToNode('ctrl-4', 'node-3');
      
      const report = store.generateCoverageReport();
      
      const node3Coverage = report.nodeCoverage.find(n => n.nodeId === 'node-3');
      expect(node3Coverage!.hasFailures).toBe(true);
    });
  });

  describe('Audit Bundles', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'validated',
        severity: 'high',
      });

      store.addControl({
        id: 'ctrl-2',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        title: 'Control 2',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
      });
    });

    it('should create audit bundle', () => {
      const bundle = store.createAuditBundle({
        createdBy: 'auditor@example.com',
      });

      expect(bundle.id).toBeDefined();
      expect(bundle.createdAt).toBeGreaterThan(0);
      expect(bundle.createdBy).toBe('auditor@example.com');
      expect(bundle.controls.length).toBe(2);
      expect(bundle.coverageReport).toBeDefined();
    });

    it('should filter bundle by framework', () => {
      const bundle = store.createAuditBundle({ framework: 'SOC2' });

      expect(bundle.framework).toBe('SOC2');
      expect(bundle.controls.length).toBe(1);
      expect(bundle.controls[0].framework).toBe('SOC2');
    });

    it('should include diagram data', () => {
      const diagram = {
        nodes: [{ id: 'node-1', label: 'Test Node', type: 'component' }],
        edges: [{ source: 'node-1', target: 'node-2' }],
      };

      const bundle = store.createAuditBundle({ diagram });

      expect(bundle.diagram).toEqual(diagram);
    });

    it('should include audit log references', () => {
      const bundle = store.createAuditBundle({
        auditLogReferences: ['log-1', 'log-2', 'log-3'],
      });

      expect(bundle.auditLogReferences).toEqual(['log-1', 'log-2', 'log-3']);
    });

    it('should include additional documents', () => {
      const documents: Evidence[] = [
        {
          id: 'doc-1',
          type: 'audit_report',
          title: 'Annual Audit',
          timestamp: Date.now(),
        },
      ];

      const bundle = store.createAuditBundle({ documents });

      expect(bundle.documents).toEqual(documents);
    });
  });

  describe('Export Formats', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Access Control',
        description: 'Test',
        status: 'validated',
        severity: 'high',
        owner: 'alice',
      });

      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-1', 'node-2');
    });

    it('should export to JSON', () => {
      const report = store.generateCoverageReport();
      const exported = store.exportCoverageReport(report, 'json');

      expect(exported).toContain('"totalControls"');
      expect(exported).toContain('"overallCoverage"');
      
      const parsed = JSON.parse(exported);
      expect(parsed.totalControls).toBe(1);
    });

    it('should export to CSV', () => {
      const report = store.generateCoverageReport();
      const exported = store.exportCoverageReport(report, 'csv');

      expect(exported).toContain('Control ID,Framework,Title,Status,Severity,Owner,Node Count');
      expect(exported).toContain('CC6.1');
      expect(exported).toContain('SOC2');
      expect(exported).toContain('validated');
    });

    it('should export to Markdown', () => {
      const report = store.generateCoverageReport();
      const exported = store.exportCoverageReport(report, 'markdown');

      expect(exported).toContain('# Compliance Coverage Report');
      expect(exported).toContain('## Summary');
      expect(exported).toContain('- Total Controls: 1');
      expect(exported).toContain('**Overall Coverage**:');
    });

    it('should escape CSV fields', () => {
      store.addControl({
        id: 'ctrl-2',
        framework: 'SOC2',
        controlId: 'CC6.2',
        title: 'Control with, comma',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
      });

      const report = store.generateCoverageReport();
      const exported = store.exportCoverageReport(report, 'csv');

      expect(exported).toContain('"Control with, comma"');
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'SOC2',
        controlId: 'CC6.1',
        title: 'Control 1',
        description: 'Test',
        status: 'validated',
        severity: 'high',
      });

      store.addControl({
        id: 'ctrl-2',
        framework: 'ISO27001',
        controlId: 'A.9.1.1',
        title: 'Control 2',
        description: 'Test',
        status: 'implemented',
        severity: 'medium',
      });

      store.addControl({
        id: 'ctrl-3',
        framework: 'SOC2',
        controlId: 'CC6.2',
        title: 'Control 3',
        description: 'Test',
        status: 'planned',
        severity: 'low',
      });

      store.attachControlToNode('ctrl-1', 'node-1');
      store.attachControlToNode('ctrl-2', 'node-1');
      store.attachControlToNode('ctrl-3', 'node-2');
    });

    it('should count total controls', () => {
      const stats = store.getStatistics();
      expect(stats.totalControls).toBe(3);
    });

    it('should count controls by framework', () => {
      const stats = store.getStatistics();
      expect(stats.controlsByFramework.SOC2).toBe(2);
      expect(stats.controlsByFramework.ISO27001).toBe(1);
    });

    it('should count controls by status', () => {
      const stats = store.getStatistics();
      expect(stats.controlsByStatus.validated).toBe(1);
      expect(stats.controlsByStatus.implemented).toBe(1);
      expect(stats.controlsByStatus.planned).toBe(1);
    });

    it('should count nodes with controls', () => {
      const stats = store.getStatistics();
      expect(stats.nodesWithControls).toBe(2);
    });

    it('should calculate average controls per node', () => {
      const stats = store.getStatistics();
      expect(stats.averageControlsPerNode).toBe(1.5); // 3 mappings / 2 nodes
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty store', () => {
      const controls = store.listControls();
      expect(controls).toEqual([]);

      const report = store.generateCoverageReport();
      expect(report.totalControls).toBe(0);
      expect(report.overallCoverage).toBe(0);
    });

    it('should handle control without owner', () => {
      store.addControl({
        id: 'ctrl-1',
        framework: 'custom',
        controlId: 'CTRL-1',
        title: 'Test',
        description: 'Test',
        status: 'planned',
        severity: 'low',
      });

      const controls = store.listControls();
      expect(controls[0].owner).toBeUndefined();
    });

    it('should handle node with no controls', () => {
      const controls = store.getNodeControls('empty-node');
      expect(controls).toEqual([]);
    });

    it('should calculate coverage with no controls', () => {
      const report = store.generateCoverageReport();
      expect(report.overallCoverage).toBe(0);
    });

    it('should handle audit bundle with no controls', () => {
      const bundle = store.createAuditBundle();
      expect(bundle.controls).toEqual([]);
      expect(bundle.coverageReport.totalControls).toBe(0);
    });
  });
});
