/**
 * @vitest-environment jsdom
 */
import { describe, it, expect } from 'vitest';

import {
  createThreatModel,
  createThreatModelConfig,
  addElement,
  addFlow,
  addBoundary,
  addThreat,
  analyzeThreatModel,
  updateThreatStatus,
  addMitigation,
  updateMitigationStatus,
  getThreatsBySeverity,
  getThreatsByStatus,
  getThreatsByCategory,
  exportThreatModel,
  getSTRIDEThreatCatalog,
  getLINDDUNThreatCatalog,
  calculateThreatScore,
  type ThreatElement,
  type ThreatFlow,
  type TrustBoundary,
} from '../threatEngine';

describe.skip('Feature 2.20: Threat Modeling Suite - threatEngine', () => {
  describe('Configuration Creation', () => {
    it('should create default threat model config', () => {
      const config = createThreatModelConfig();
      
      expect(config.enableSTRIDE).toBe(true);
      expect(config.enableLINDDUN).toBe(false);
      expect(config.autoFlagBoundaryCrossings).toBe(true);
      expect(config.minSeverity).toBe('low');
      expect(config.customThreats).toEqual([]);
    });
    
    it('should create config with overrides', () => {
      const config = createThreatModelConfig({
        enableLINDDUN: true,
        minSeverity: 'high',
      });
      
      expect(config.enableSTRIDE).toBe(true);
      expect(config.enableLINDDUN).toBe(true);
      expect(config.minSeverity).toBe('high');
    });
  });
  
  describe('Threat Model Creation', () => {
    it('should create empty threat model', () => {
      const model = createThreatModel();
      
      expect(model.elements.size).toBe(0);
      expect(model.flows.size).toBe(0);
      expect(model.boundaries.size).toBe(0);
      expect(model.threats.size).toBe(0);
      expect(model.catalog.length).toBeGreaterThan(0);
    });
    
    it('should create model with custom config', () => {
      const model = createThreatModel({ enableLINDDUN: true });
      
      expect(model.config.enableLINDDUN).toBe(true);
    });
  });
  
  describe('Element Management', () => {
    it('should add element to model', () => {
      const model = createThreatModel();
      const element: ThreatElement = {
        id: 'el-1',
        type: 'process',
        label: 'Web Server',
      };
      
      const updated = addElement(model, element);
      
      expect(updated.elements.size).toBe(1);
      expect(updated.elements.get('el-1')).toEqual(element);
    });
    
    it('should add multiple elements', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'API Server',
      });
      
      model = addElement(model, {
        id: 'el-2',
        type: 'data-store',
        label: 'Database',
      });
      
      expect(model.elements.size).toBe(2);
    });
  });
  
  describe('Flow Management', () => {
    it('should add flow to model', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Client',
      });
      
      model = addElement(model, {
        id: 'el-2',
        type: 'process',
        label: 'Server',
      });
      
      const flow: ThreatFlow = {
        id: 'flow-1',
        source: 'el-1',
        target: 'el-2',
        label: 'HTTPS Request',
      };
      
      model = addFlow(model, flow);
      
      expect(model.flows.size).toBe(1);
      expect(model.flows.get('flow-1')?.source).toBe('el-1');
    });
    
    it('should detect boundary crossing in flow', () => {
      let model = createThreatModel();
      
      // Create elements in different boundaries
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'External Client',
        trustBoundary: 'boundary-1',
      });
      
      model = addElement(model, {
        id: 'el-2',
        type: 'process',
        label: 'Internal Server',
        trustBoundary: 'boundary-2',
      });
      
      const flow: ThreatFlow = {
        id: 'flow-1',
        source: 'el-1',
        target: 'el-2',
        label: 'Request',
      };
      
      model = addFlow(model, flow);
      
      const addedFlow = model.flows.get('flow-1');
      expect(addedFlow?.crossesBoundary).toBe(true);
      expect(addedFlow?.boundariesCrossed).toContain('boundary-1');
      expect(addedFlow?.boundariesCrossed).toContain('boundary-2');
    });
    
    it('should not flag crossing when same boundary', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Service A',
        trustBoundary: 'boundary-1',
      });
      
      model = addElement(model, {
        id: 'el-2',
        type: 'process',
        label: 'Service B',
        trustBoundary: 'boundary-1',
      });
      
      const flow: ThreatFlow = {
        id: 'flow-1',
        source: 'el-1',
        target: 'el-2',
      };
      
      model = addFlow(model, flow);
      
      expect(model.flows.get('flow-1')?.crossesBoundary).toBe(false);
    });
  });
  
  describe('Trust Boundary Management', () => {
    it('should add trust boundary', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Web Server',
      });
      
      const boundary: TrustBoundary = {
        id: 'boundary-1',
        name: 'DMZ',
        zone: 'dmz',
        containedElements: ['el-1'],
      };
      
      model = addBoundary(model, boundary);
      
      expect(model.boundaries.size).toBe(1);
      expect(model.boundaries.get('boundary-1')).toBeDefined();
    });
    
    it('should assign elements to boundary', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Server',
      });
      
      const boundary: TrustBoundary = {
        id: 'boundary-1',
        name: 'Internal',
        zone: 'internal',
        containedElements: ['el-1'],
      };
      
      model = addBoundary(model, boundary);
      
      const element = model.elements.get('el-1');
      expect(element?.trustBoundary).toBe('boundary-1');
    });
    
    it('should support custom zones', () => {
      const model = createThreatModel();
      
      const boundary: TrustBoundary = {
        id: 'boundary-1',
        name: 'Partner Network',
        zone: 'custom',
        customZone: 'partner-zone',
        containedElements: [],
      };
      
      const updated = addBoundary(model, boundary);
      
      expect(updated.boundaries.get('boundary-1')?.customZone).toBe(
        'partner-zone'
      );
    });
  });
  
  describe('Threat Cataloging', () => {
    it('should provide STRIDE catalog', () => {
      const catalog = getSTRIDEThreatCatalog();
      
      expect(catalog.length).toBeGreaterThan(0);
      expect(catalog.some((e) => e.category === 'spoofing')).toBe(true);
      expect(catalog.some((e) => e.category === 'tampering')).toBe(true);
      expect(catalog.some((e) => e.category === 'repudiation')).toBe(true);
      expect(catalog.some((e) => e.category === 'information-disclosure')).toBe(
        true
      );
      expect(catalog.some((e) => e.category === 'denial-of-service')).toBe(
        true
      );
      expect(catalog.some((e) => e.category === 'elevation-of-privilege')).toBe(
        true
      );
    });
    
    it('should provide LINDDUN catalog', () => {
      const catalog = getLINDDUNThreatCatalog();
      
      expect(catalog.length).toBeGreaterThan(0);
      expect(catalog.some((e) => e.category === 'linkability')).toBe(true);
      expect(catalog.some((e) => e.category === 'identifiability')).toBe(true);
      expect(catalog.some((e) => e.category === 'disclosure-of-information')).toBe(
        true
      );
    });
    
    it('should include mitigations in catalog entries', () => {
      const catalog = getSTRIDEThreatCatalog();
      const entry = catalog[0];
      
      expect(entry.mitigations.length).toBeGreaterThan(0);
      expect(entry.mitigations[0]).toHaveProperty('title');
      expect(entry.mitigations[0]).toHaveProperty('effort');
      expect(entry.mitigations[0]).toHaveProperty('effectiveness');
    });
  });
  
  describe('Threat Analysis', () => {
    it('should analyze model and generate threats', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Web Application',
      });
      
      const result = analyzeThreatModel(model);
      
      expect(result.newThreats.length).toBeGreaterThan(0);
      expect(result.summary.totalThreats).toBeGreaterThan(0);
    });
    
    it('should detect boundary crossings', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Client',
        trustBoundary: 'b1',
      });
      
      model = addElement(model, {
        id: 'el-2',
        type: 'process',
        label: 'Server',
        trustBoundary: 'b2',
      });
      
      model = addFlow(model, {
        id: 'flow-1',
        source: 'el-1',
        target: 'el-2',
      });
      
      const result = analyzeThreatModel(model);
      
      expect(result.boundaryCrossings.length).toBe(1);
      expect(result.boundaryCrossings[0].id).toBe('flow-1');
    });
    
    it('should identify unbounded elements', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Orphan Process',
      });
      
      const result = analyzeThreatModel(model);
      
      expect(result.unboundedElements.length).toBe(1);
      expect(result.unboundedElements[0].id).toBe('el-1');
    });
    
    it('should generate summary statistics', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'data-store',
        label: 'Database',
      });
      
      const result = analyzeThreatModel(model);
      
      expect(result.summary).toHaveProperty('totalThreats');
      expect(result.summary).toHaveProperty('byCategory');
      expect(result.summary).toHaveProperty('bySeverity');
      expect(result.summary).toHaveProperty('byStatus');
    });
    
    it('should not generate duplicate threats', () => {
      let model = createThreatModel();
      
      model = addElement(model, {
        id: 'el-1',
        type: 'process',
        label: 'Server',
      });
      
      // Analyze twice
      analyzeThreatModel(model);
      const result2 = analyzeThreatModel(model);
      
      expect(result2.newThreats.length).toBe(0);
    });
  });
  
  describe('Manual Threat Management', () => {
    it('should add manual threat', () => {
      const model = createThreatModel();
      
      const updated = addThreat(model, {
        title: 'Custom Threat',
        description: 'Manual threat description',
        category: 'spoofing',
        severity: 'high',
        status: 'identified',
        affectedElements: ['el-1'],
        mitigations: [],
        autoGenerated: false,
      });
      
      expect(updated.threats.size).toBe(1);
      const threat = Array.from(updated.threats.values())[0];
      expect(threat.title).toBe('Custom Threat');
      expect(threat.autoGenerated).toBe(false);
    });
    
    it('should update threat status', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Test Threat',
        description: 'Test',
        category: 'tampering',
        severity: 'medium',
        status: 'identified',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      const threatId = Array.from(model.threats.keys())[0];
      model = updateThreatStatus(model, threatId, 'mitigating');
      
      const threat = model.threats.get(threatId);
      expect(threat?.status).toBe('mitigating');
    });
  });
  
  describe('Mitigation Management', () => {
    it('should add mitigation to threat', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'SQL Injection',
        description: 'Vulnerable to SQL injection',
        category: 'tampering',
        severity: 'high',
        status: 'identified',
        affectedElements: ['db-1'],
        mitigations: [],
        autoGenerated: false,
      });
      
      const threatId = Array.from(model.threats.keys())[0];
      
      model = addMitigation(model, threatId, {
        title: 'Use Prepared Statements',
        description: 'Implement parameterized queries',
        effort: 'low',
        effectiveness: 'high',
        status: 'proposed',
      });
      
      const threat = model.threats.get(threatId);
      expect(threat?.mitigations.length).toBe(1);
      expect(threat?.mitigations[0].title).toBe('Use Prepared Statements');
    });
    
    it('should update mitigation status', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Test',
        description: 'Test',
        category: 'spoofing',
        severity: 'low',
        status: 'identified',
        affectedElements: [],
        mitigations: [
          {
            id: 'mit-1',
            title: 'Test Mitigation',
            description: 'Test',
            effort: 'low',
            effectiveness: 'medium',
            status: 'proposed',
          },
        ],
        autoGenerated: false,
      });
      
      const threatId = Array.from(model.threats.keys())[0];
      model = updateMitigationStatus(model, threatId, 'mit-1', 'implemented');
      
      const threat = model.threats.get(threatId);
      expect(threat?.mitigations[0].status).toBe('implemented');
    });
  });
  
  describe('Threat Queries', () => {
    it('should filter threats by severity', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Critical Threat',
        description: 'Critical',
        category: 'elevation-of-privilege',
        severity: 'critical',
        status: 'identified',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      model = addThreat(model, {
        title: 'Low Threat',
        description: 'Low',
        category: 'repudiation',
        severity: 'low',
        status: 'identified',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      const criticalThreats = getThreatsBySeverity(model, 'critical');
      expect(criticalThreats.length).toBe(1);
      expect(criticalThreats[0].title).toBe('Critical Threat');
    });
    
    it('should filter threats by status', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Resolved Threat',
        description: 'Resolved',
        category: 'tampering',
        severity: 'medium',
        status: 'resolved',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      const resolvedThreats = getThreatsByStatus(model, 'resolved');
      expect(resolvedThreats.length).toBe(1);
    });
    
    it('should filter threats by category', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Spoofing Attack',
        description: 'Spoofing',
        category: 'spoofing',
        severity: 'high',
        status: 'identified',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      const spoofingThreats = getThreatsByCategory(model, 'spoofing');
      expect(spoofingThreats.length).toBe(1);
      expect(spoofingThreats[0].category).toBe('spoofing');
    });
  });
  
  describe('Threat Scoring', () => {
    it('should calculate threat score based on severity', () => {
      const threat = {
        id: 'threat-1',
        title: 'Critical Threat',
        description: 'Test',
        category: 'elevation-of-privilege' as const,
        severity: 'critical' as const,
        status: 'identified' as const,
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
        discoveredAt: new Date(),
        updatedAt: new Date(),
      };
      
      const score = calculateThreatScore(threat);
      expect(score).toBe(10);
    });
    
    it('should reduce score for implemented mitigations', () => {
      const threat = {
        id: 'threat-1',
        title: 'High Threat',
        description: 'Test',
        category: 'tampering' as const,
        severity: 'high' as const,
        status: 'mitigating' as const,
        affectedElements: [],
        mitigations: [
          {
            id: 'mit-1',
            title: 'Mitigation 1',
            description: 'Test',
            effort: 'low' as const,
            effectiveness: 'high' as const,
            status: 'implemented' as const,
          },
          {
            id: 'mit-2',
            title: 'Mitigation 2',
            description: 'Test',
            effort: 'medium' as const,
            effectiveness: 'medium' as const,
            status: 'proposed' as const,
          },
        ],
        autoGenerated: false,
        discoveredAt: new Date(),
        updatedAt: new Date(),
      };
      
      const score = calculateThreatScore(threat);
      expect(score).toBeLessThan(7.5); // High severity baseline is 7.5
    });
  });
  
  describe('Export Functionality', () => {
    it('should export to JSON', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Test Threat',
        description: 'Test',
        category: 'spoofing',
        severity: 'medium',
        status: 'identified',
        affectedElements: [],
        mitigations: [],
        autoGenerated: false,
      });
      
      const json = exportThreatModel(model, 'json');
      const parsed = JSON.parse(json);
      
      expect(parsed.threats.length).toBe(1);
      expect(parsed).toHaveProperty('summary');
    });
    
    it('should export to YAML', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Test Threat',
        description: 'Test',
        category: 'tampering',
        severity: 'high',
        status: 'identified',
        affectedElements: ['el-1'],
        mitigations: [],
        autoGenerated: false,
      });
      
      const yaml = exportThreatModel(model, 'yaml');
      
      expect(yaml).toContain('threats:');
      expect(yaml).toContain('title: "Test Threat"');
      expect(yaml).toContain('category: tampering');
      expect(yaml).toContain('severity: high');
    });
    
    it('should export to CSV', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Test Threat',
        description: 'Test',
        category: 'repudiation',
        severity: 'low',
        status: 'identified',
        affectedElements: ['el-1'],
        mitigations: [],
        autoGenerated: false,
      });
      
      const csv = exportThreatModel(model, 'csv');
      
      expect(csv).toContain('ID,Title,Category,Severity,Status');
      expect(csv).toContain('"Test Threat"');
      expect(csv).toContain('repudiation');
    });
    
    it('should export to Markdown', () => {
      let model = createThreatModel();
      
      model = addThreat(model, {
        title: 'Critical Security Issue',
        description: 'Serious vulnerability',
        category: 'elevation-of-privilege',
        severity: 'critical',
        status: 'identified',
        affectedElements: [],
        mitigations: [
          {
            id: 'mit-1',
            title: 'Apply Patch',
            description: 'Install security patch',
            effort: 'low',
            effectiveness: 'high',
            status: 'proposed',
          },
        ],
        autoGenerated: false,
      });
      
      const markdown = exportThreatModel(model, 'markdown');
      
      expect(markdown).toContain('# Threat Model Report');
      expect(markdown).toContain('## Summary');
      expect(markdown).toContain('Critical Security Issue');
      expect(markdown).toContain('**Mitigations**');
      expect(markdown).toContain('Apply Patch');
    });
  });
});
