/**
 * Unit tests for AccessibilityAuditor
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import { AccessibilityAuditor } from './AccessibilityAuditor';
import { mockAxeViolations, mockAuditConfig as _mockAuditConfig, createMinimalReport } from './test/fixtures';

// Mock axe-core
 
vi.mock('axe-core', () => ({
  default: {
    run: vi.fn(),
    configure: vi.fn(),
  },
}));

// Import mocked axe (value import only to avoid duplicate import diagnostics)
// eslint-disable-next-line import/order
import axe from 'axe-core';

describe('AccessibilityAuditor', () => {
  let auditor: AccessibilityAuditor;
  let mockAxeResults: any;

  beforeEach(() => {
    auditor = new AccessibilityAuditor();
    
    // Setup mock axe results
    mockAxeResults = {
      violations: mockAxeViolations,
      passes: [],
      incomplete: [],
      inapplicable: [],
      timestamp: new Date().toISOString(),
      url: 'https://example.com',
      testEngine: { name: 'axe-core', version: '4.9.1' },
      testEnvironment: { userAgent: 'Test', windowWidth: 1920, windowHeight: 1080 },
      testRunner: { name: 'AccessibilityAuditor' },
      toolOptions: {},
    };

    // Mock axe.run to return mock results
    vi.mocked(axe.run).mockResolvedValue(mockAxeResults);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('constructor', () => {
    it('should create auditor without config', () => {
      const auditor = new AccessibilityAuditor();
      expect(auditor).toBeInstanceOf(AccessibilityAuditor);
    });

    it('should create auditor with config', () => {
      const config = { wcagLevel: 'AAA' as const, minScore: 90 };
      const auditor = new AccessibilityAuditor(config);
      expect(auditor).toBeInstanceOf(AccessibilityAuditor);
      const retrievedConfig = auditor.getConfig();
      expect(retrievedConfig.wcagLevel).toBe('AAA');
      expect(retrievedConfig.minScore).toBe(90);
    });
  });

  describe('audit', () => {
    it('should run audit and return report', async () => {
      const report = await auditor.audit();

      expect(report).toHaveProperty('metadata');
      expect(report).toHaveProperty('target');
      expect(report).toHaveProperty('score');
      expect(report).toHaveProperty('findings');
      expect(report).toHaveProperty('summary');
      expect(report).toHaveProperty('recommendations');
    });

    it('should call axe.run', async () => {
      await auditor.audit();
      expect(axe.run).toHaveBeenCalled();
    });

    it('should accept element parameter', async () => {
      const element = document.createElement('div');
      await auditor.audit(element);
      
      expect(axe.run).toHaveBeenCalledWith(element, expect.any(Object));
    });

    it('should accept options parameter', async () => {
      const options = { rules: { 'color-contrast': { enabled: false } } };
      await auditor.audit(undefined, options);
      
      // Should be called with merged options including the user's rules
      expect(axe.run).toHaveBeenCalled();
      const callArgs = vi.mocked(axe.run).mock.calls[0];
      expect(callArgs[1]?.rules).toHaveProperty('color-contrast');
      expect(callArgs[1]?.rules?.['color-contrast']).toEqual({ enabled: false });
    });

    it('should include score in report', async () => {
      const report = await auditor.audit();
      
      expect(report.score).toHaveProperty('overall');
      expect(report.score).toHaveProperty('grade');
      expect(report.score).toHaveProperty('dimensions');
      expect(typeof report.score.overall).toBe('number');
    });

    it('should include metadata in report', async () => {
      const report = await auditor.audit();
      
      expect(report.metadata).toHaveProperty('timestamp');
      expect(report.metadata).toHaveProperty('id');
      expect(report.metadata).toHaveProperty('toolVersion');
      expect(report.metadata).toHaveProperty('environment');
    });

    it('should include findings from violations', async () => {
      const report = await auditor.audit();
      
      expect(report.findings.length).toBeGreaterThan(0);
      expect(report.findings[0]).toHaveProperty('id');
      expect(report.findings[0]).toHaveProperty('severity');
      expect(report.findings[0]).toHaveProperty('remediation');
    });

    it('should include summary statistics', async () => {
      const report = await auditor.audit();
      
      expect(report.summary).toHaveProperty('totalFindings');
      expect(report.summary).toHaveProperty('bySeverity');
      expect(report.summary).toHaveProperty('elementsAnalyzed');
      expect(report.summary).toHaveProperty('duration');
    });

    it('should include recommendations', async () => {
      const report = await auditor.audit();
      
      expect(report.recommendations).toHaveProperty('immediate');
      expect(report.recommendations).toHaveProperty('shortTerm');
      expect(report.recommendations).toHaveProperty('longTerm');
    });

    it('should handle empty violations', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [],
      });

      const report = await auditor.audit();
      
      expect(report.findings.length).toBe(0);
      expect(report.score.overall).toBe(100);
      expect(report.score.grade).toBe('A+');
    });

    it('should handle axe errors gracefully', async () => {
      vi.mocked(axe.run).mockRejectedValue(new Error('Axe error'));

      await expect(auditor.audit()).rejects.toThrow('Axe error');
    });
  });

  describe('legacyAudit', () => {
    it('should return legacy report format', async () => {
      const report = await auditor.legacyAudit();

      expect(report).toHaveProperty('violations');
      expect(report).toHaveProperty('passes');
      expect(report).toHaveProperty('incomplete');
      expect(report).toHaveProperty('inapplicable');
    });

    it('should match axe results structure', async () => {
      const report = await auditor.legacyAudit();
      
      expect(report.violations.length).toBe(mockAxeResults.violations.length);
      expect(report.timestamp).toBe(mockAxeResults.timestamp);
    });
  });

  describe('exportReport', () => {
    let report: any;

    beforeEach(async () => {
      report = await auditor.audit();
    });

    it('should export as JSON', () => {
      const output = auditor.exportReport(report, 'json');
      
      expect(() => JSON.parse(output)).not.toThrow();
    });

    it('should export as HTML', () => {
      const output = auditor.exportReport(report, 'html');
      
      expect(output).toContain('<!DOCTYPE html>');
    });

    it('should export as CSV', () => {
      const output = auditor.exportReport(report, 'csv');
      
      expect(output).toContain(',');
      expect(output.split('\n').length).toBeGreaterThan(1);
    });

    it('should export as SARIF', () => {
      const output = auditor.exportReport(report, 'sarif');
      
      const parsed = JSON.parse(output);
      expect(parsed.version).toBe('2.1.0');
    });

    it('should export as XML', () => {
      const output = auditor.exportReport(report, 'xml');
      
      expect(output).toContain('<?xml');
    });

    it('should export as Markdown', () => {
      const output = auditor.exportReport(report, 'markdown');
      
      expect(output).toContain('#');
    });

    it('should throw for invalid format', () => {
      expect(() => {
        auditor.exportReport(report, 'invalid' as any);
      }).toThrow();
    });
  });

  describe('saveReport', () => {
    let report: any;

    beforeEach(async () => {
      report = await auditor.audit();
    });

    it('should create download in browser', () => {
      // Mock browser environment
      const mockAnchor = {
        click: vi.fn(),
        href: '',
        download: '',
        remove: vi.fn(),
      };
      
      const createElementSpy = vi.spyOn(document, 'createElement');
      createElementSpy.mockReturnValue(mockAnchor as any);
      
      const appendChildSpy = vi.spyOn(document.body, 'appendChild');
      appendChildSpy.mockImplementation(() => mockAnchor as any);
      
      const removeChildSpy = vi.spyOn(document.body, 'removeChild');
      removeChildSpy.mockImplementation(() => mockAnchor as any);
      
      // Mock URL.createObjectURL and revokeObjectURL
      global.URL.createObjectURL = vi.fn(() => 'blob:mock-url');
      global.URL.revokeObjectURL = vi.fn();

      auditor.saveReport(report, 'test-report', 'json');
      
      expect(createElementSpy).toHaveBeenCalledWith('a');
      expect(mockAnchor.click).toHaveBeenCalled();
      expect(mockAnchor.download).toBe('test-report.json');
      expect(global.URL.createObjectURL).toHaveBeenCalled();
      expect(global.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
      
      // Cleanup
      createElementSpy.mockRestore();
      appendChildSpy.mockRestore();
      removeChildSpy.mockRestore();
    });
  });

  describe('compareReports', () => {
    it('should compare two reports', async () => {
      const currentReport = await auditor.audit();
      const previousReport = createMinimalReport({
        score: { ...currentReport.score, overall: 70 },
      });

      const comparison = auditor.compareReports(currentReport, previousReport);
      
      expect(comparison).toHaveProperty('current');
      expect(comparison).toHaveProperty('previous');
      expect(comparison).toHaveProperty('trend');
    });

    it('should include trend analysis', async () => {
      const currentReport = await auditor.audit();
      const previousReport = createMinimalReport({
        score: { ...currentReport.score, overall: 70 },
      });

      const comparison = auditor.compareReports(currentReport, previousReport);
      
      expect(comparison.trend).toHaveProperty('direction');
      expect(comparison.trend).toHaveProperty('change');
      expect(comparison.trend).toHaveProperty('previousScore');
    });

    it('should detect stable trend for small changes', async () => {
      const currentReport = await auditor.audit();
      const previousReport = createMinimalReport({
        score: { ...currentReport.score, overall: currentReport.score.overall - 1 },
      });

      const comparison = auditor.compareReports(currentReport, previousReport);
      
      expect(comparison.trend.direction).toBe('stable');
    });

    it('should detect degrading trend when score decreases', async () => {
      const currentReport = await auditor.audit();
      const previousReport = createMinimalReport({
        score: { ...currentReport.score, overall: currentReport.score.overall + 10 },
      });

      const comparison = auditor.compareReports(currentReport, previousReport);
      
      expect(comparison.trend.direction).toBe('degrading');
    });
  });

  describe('generateReport', () => {
    it('should generate console report', async () => {
      const report = await auditor.audit();
      const consoleReport = auditor.generateReport(report);
      
      expect(consoleReport).toContain('ACCESSIBILITY AUDIT REPORT');
      expect(consoleReport).toContain('Score');
      expect(consoleReport).toContain('Findings');
    });

    it('should include visual formatting', async () => {
      const report = await auditor.audit();
      const consoleReport = auditor.generateReport(report);
      
      expect(consoleReport).toMatch(/[╔═╗║╚╝]/); // Box drawing characters
    });

    it('should show score prominently', async () => {
      const report = await auditor.audit();
      const consoleReport = auditor.generateReport(report);
      
      expect(consoleReport).toContain(report.score.overall.toString());
      expect(consoleReport).toContain(report.score.grade);
    });
  });

  describe('configuration', () => {
    it('should get current config', () => {
      const config = { wcagLevel: 'AAA' as const, minScore: 85 };
      const auditor = new AccessibilityAuditor(config);
      const retrievedConfig = auditor.getConfig();
      
      expect(retrievedConfig.wcagLevel).toBe('AAA');
      expect(retrievedConfig.minScore).toBe(85);
    });

    it('should update config', () => {
      auditor.setConfig({ wcagLevel: 'AAA' });
      
      const config = auditor.getConfig();
      expect(config.wcagLevel).toBe('AAA');
    });

    it('should merge partial config', () => {
      const config = { wcagLevel: 'AA' as const, minScore: 80 };
      const auditor = new AccessibilityAuditor(config);
      auditor.setConfig({ wcagLevel: 'AAA' });
      
      const retrievedConfig = auditor.getConfig();
      expect(retrievedConfig.wcagLevel).toBe('AAA');
      expect(retrievedConfig.minScore).toBe(80); // Original value preserved
    });

    it('should list supported formats', () => {
      const formats = auditor.getSupportedFormats();
      
      expect(formats).toContain('json');
      expect(formats).toContain('html');
      expect(formats).toContain('sarif');
      expect(formats.length).toBe(6);
    });

    it('should handle includeRules config', async () => {
      const config = { includeRules: ['color-contrast', 'label'] };
      const auditor = new AccessibilityAuditor(config);
      await auditor.audit();
      
      // Verify axe.run was called with rules config
      expect(axe.run).toHaveBeenCalled();
      const callArgs = vi.mocked(axe.run).mock.calls[0];
      expect(callArgs[1]).toHaveProperty('rules');
    });

    it('should handle excludeRules config', async () => {
      const config = { excludeRules: ['bypass', 'region'] };
      const auditor = new AccessibilityAuditor(config);
      await auditor.audit();
      
      // Verify axe.run was called with rules config
      expect(axe.run).toHaveBeenCalled();
      const callArgs = vi.mocked(axe.run).mock.calls[0];
      expect(callArgs[1]).toHaveProperty('rules');
    });

    it('should handle both includeRules and excludeRules', async () => {
      const config = { 
        includeRules: ['color-contrast', 'label'],
        excludeRules: ['bypass'] 
      };
      const auditor = new AccessibilityAuditor(config);
      await auditor.audit();
      
      expect(axe.run).toHaveBeenCalled();
      const callArgs = vi.mocked(axe.run).mock.calls[0];
      expect(callArgs[1]).toHaveProperty('rules');
    });
  });

  describe('helper methods', () => {
    it('should determine WCAG level from tags', () => {
      const level = auditor['extractWCAGLevel'](['wcag2aa', 'wcag143']);
      expect(level).toBe('AA');
    });

    it('should extract WCAG AAA level from tags', () => {
      expect(auditor['extractWCAGLevel'](['wcag2aaa', 'wcag143'])).toBe('AAA');
      expect(auditor['extractWCAGLevel'](['wcag21aaa', 'section508'])).toBe('AAA');
    });

    it('should extract WCAG AA level from tags', () => {
      expect(auditor['extractWCAGLevel'](['wcag2aa', 'wcag143'])).toBe('AA');
      expect(auditor['extractWCAGLevel'](['wcag21aa', 'best-practice'])).toBe('AA');
    });

    it('should default to WCAG A level when no specific level tags', () => {
      expect(auditor['extractWCAGLevel'](['best-practice', 'section508'])).toBe('A');
      expect(auditor['extractWCAGLevel']([])).toBe('A');
    });

    it('should determine violation type from tags', () => {
      const type = auditor['determineViolationType'](['color-contrast', 'wcag143']);
      expect(type).toBe('visual');
    });

    it('should determine visual violation type from color tags', () => {
      expect(auditor['determineViolationType'](['color', 'wcag143'])).toBe('visual');
      expect(auditor['determineViolationType'](['color-contrast'])).toBe('visual');
    });

    it('should determine keyboard violation type from keyboard tags', () => {
      expect(auditor['determineViolationType'](['keyboard', 'wcag211'])).toBe('keyboard');
      expect(auditor['determineViolationType'](['keyboard-trap'])).toBe('keyboard');
    });

    it('should determine semantic violation type from structure tags', () => {
      expect(auditor['determineViolationType'](['structure', 'landmark'])).toBe('semantic');
    });

    it('should determine screen reader violation type from aria tags', () => {
      expect(auditor['determineViolationType'](['aria', 'wcag412'])).toBe('aria');
      expect(auditor['determineViolationType'](['aria-label'])).toBe('aria');
    });

    it('should determine forms violation type from forms tags', () => {
      expect(auditor['determineViolationType'](['forms', 'label'])).toBe('forms');
    });

    it('should default to other violation type for unknown tags', () => {
      expect(auditor['determineViolationType'](['unknown', 'custom'])).toBe('other');
      expect(auditor['determineViolationType']([])).toBe('other');
    });

    it('should determine affected users', () => {
      const users = auditor['determineAffectedUsers']('color-contrast', 'serious');
      expect(Array.isArray(users)).toBe(true);
      expect(users).toContain('low-vision');
    });

    it('should map severity to priority', () => {
      expect(auditor['mapSeverityToPriority']('critical')).toBe(1);
      expect(auditor['mapSeverityToPriority']('serious')).toBe(2);
      expect(auditor['mapSeverityToPriority']('moderate')).toBe(3);
      expect(auditor['mapSeverityToPriority']('minor')).toBe(4);
    });
  });

  describe('edge cases', () => {
    it('should handle null element', async () => {
      await auditor.audit(null as any);
      expect(axe.run).toHaveBeenCalled();
    });

    it('should handle undefined options', async () => {
      await auditor.audit(undefined, undefined);
      expect(axe.run).toHaveBeenCalled();
    });

    it('should handle report with no findings', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [],
      });

      const report = await auditor.audit();
      const consoleReport = auditor.generateReport(report);
      
      expect(consoleReport).toContain('No accessibility issues found');
    });

    it('should handle very long violation descriptions', async () => {
      const longDescription = 'A'.repeat(1000);
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          description: longDescription,
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].description).toBe(longDescription);
    });

    it('should handle node target as nested array', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          nodes: [{
            ...mockAxeViolations[0].nodes[0],
            target: [['#app', '.container', 'button']] // Nested array format
          }]
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].location.selector).toContain('#app');
    });

    it('should detect WCAG principle understandable from tags', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          tags: ['cat.language', 'wcag2aa']
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].wcag.principle).toBe('understandable');
    });

    it('should detect WCAG principle robust from tags', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          tags: ['cat.parsing', 'wcag2aa']
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].wcag.principle).toBe('robust');
    });

    it('should generate recommendations for low dimension scores', async () => {
      const report = await auditor.audit();
      
      // Create report with low dimension scores
      const modifiedReport = {
        ...report,
        score: {
          ...report.score,
          dimensions: {
            ...report.score.dimensions,
            keyboardAccessibility: { ...report.score.dimensions.keyboardAccessibility, score: 65 },
            visualAccessibility: { ...report.score.dimensions.visualAccessibility, score: 70 }
          }
        },
        findings: [
          ...report.findings,
          { ...report.findings[0], severity: 'moderate' as const }
        ]
      };

      const recommendations = auditor['generateRecommendations'](modifiedReport.findings, modifiedReport.score);
      
      expect(recommendations.shortTerm.length).toBeGreaterThan(0);
    });

    it('should use getWCAGTags for AA level', () => {
      const tags = auditor['getWCAGTags']('AA');
      expect(tags).toContain('wcag2a');
      expect(tags).toContain('wcag21a');
      expect(tags).toContain('wcag2aa');
      expect(tags).toContain('wcag21aa');
      expect(tags).not.toContain('wcag2aaa');
    });

    it('should use getWCAGTags for AAA level', () => {
      const tags = auditor['getWCAGTags']('AAA');
      expect(tags).toContain('wcag2a');
      expect(tags).toContain('wcag2aa');
      expect(tags).toContain('wcag2aaa');
      expect(tags).toContain('wcag21aaa');
    });

    it('should use getWCAGTags for A level', () => {
      const tags = auditor['getWCAGTags']('A');
      expect(tags).toContain('wcag2a');
      expect(tags).toContain('wcag21a');
      expect(tags).not.toContain('wcag2aa');
    });

    it('should detect operable principle from cat.time tag', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          tags: ['cat.time', 'wcag2aa']
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].wcag.principle).toBe('operable');
    });

    it('should preserve AAA level in extractWCAGLevel when AA tag found', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: [{
          ...mockAxeViolations[0],
          tags: ['wcag2aaa', 'wcag2aa', 'wcag143'] // Both AAA and AA tags
        }],
      });

      const report = await auditor.audit();
      expect(report.findings[0].wcag.level).toBe('AAA');
    });

    it('should show ellipsis when more than 3 issues of same severity', async () => {
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: Array.from({ length: 5 }, (_, i) => ({
          ...mockAxeViolations[0],
          id: `violation-${i}`,
          impact: 'critical'
        })),
      });

      const report = await auditor.audit();
      const consoleReport = auditor.generateReport(report);
      
      expect(consoleReport).toContain('... and');
      expect(consoleReport).toContain('more critical issues');
    });
  });

  describe('error handling for axe-core failures', () => {
    it('should throw error if axe-core fails to load in audit', async () => {
      const auditor = new AccessibilityAuditor();
      
      // Mock initialize to complete but leave axeCore null
      const initializeSpy = vi.spyOn(auditor as any, 'initialize').mockResolvedValue(undefined);
      (auditor as any).axeCore = null;
      (auditor as any).isInitialized = false;
      
      await expect(auditor.audit()).rejects.toThrow('axe-core failed to load');
      
      initializeSpy.mockRestore();
    });

    it('should throw error if axeCore fails to load in legacyAudit', async () => {
      const auditor = new AccessibilityAuditor();
      
      // Mock initialize to complete but leave axeCore null
      const initializeSpy = vi.spyOn(auditor as any, 'initialize').mockResolvedValue(undefined);
      (auditor as any).axeCore = null;
      (auditor as any).isInitialized = false;
      
      await expect(auditor.legacyAudit()).rejects.toThrow('axe-core failed to load');
      
      initializeSpy.mockRestore();
    });
  });
});

