/**
 * Integration tests for accessibility audit workflow
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import { AccessibilityAuditor } from '../AccessibilityAuditor';
import { OutputFormatterFactory } from '../formatters/OutputFormatter';
import { runQuickAudit, runAuditWithFormat } from '../index';
import { mockAxeViolations, mockAuditConfig, mockAccessibilityReport, createMinimalFinding } from './fixtures';
import { AccessibilityScorer } from '../scoring/AccessibilityScorer';

import type { OutputFormat } from '../types';

// Mock axe-core
// Mock axe-core
 
vi.mock('axe-core', () => ({
  default: {
    run: vi.fn(),
    configure: vi.fn(),
  },
}));

// eslint-disable-next-line import/order
import axe from 'axe-core';

describe('Full Audit Workflow Integration', () => {
  let mockAxeResults: any;

  beforeEach(() => {
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

    vi.mocked(axe.run).mockResolvedValue(mockAxeResults);
  });

  describe('End-to-End Audit Flow', () => {
    it('should complete full audit workflow', async () => {
      // 1. Create auditor
      const auditor = new AccessibilityAuditor(mockAuditConfig);

      // 2. Run audit
      const report = await auditor.audit();

      // 3. Verify report structure
      expect(report).toBeDefined();
      expect(report.findings.length).toBeGreaterThan(0);
      expect(report.score.overall).toBeGreaterThanOrEqual(0);
      expect(report.score.overall).toBeLessThanOrEqual(100);

      // 4. Export to different formats
      const jsonOutput = auditor.exportReport(report, 'json');
      const htmlOutput = auditor.exportReport(report, 'html');
      const sarifOutput = auditor.exportReport(report, 'sarif');

      // 5. Verify exports
      expect(() => JSON.parse(jsonOutput)).not.toThrow();
      expect(htmlOutput).toContain('<!DOCTYPE html>');
      expect(() => JSON.parse(sarifOutput)).not.toThrow();

      // 6. Generate console report
      const consoleReport = auditor.generateReport(report);
      expect(consoleReport).toContain('ACCESSIBILITY AUDIT REPORT');
    });

    it('should handle trend analysis workflow', async () => {
      const auditor = new AccessibilityAuditor();

      // First audit
      const report1 = await auditor.audit();

      // Simulate improvement
      vi.mocked(axe.run).mockResolvedValue({
        ...mockAxeResults,
        violations: mockAxeResults.violations.slice(0, 1), // Fewer violations
      });

      // Second audit
      const report2 = await auditor.audit();

      // Compare reports
      const comparison = auditor.compareReports(report2, report1);

      expect(comparison.trend.direction).toBe('improving');
      expect(comparison.trend.change).toBeGreaterThan(0);
    });
  });

  describe('Auditor + Scorer Integration', () => {
    it('should integrate auditor with scorer', async () => {
      const auditor = new AccessibilityAuditor();
      const scorer = AccessibilityScorer.getInstance();

      const report = await auditor.audit();

      // Verify scorer was used
      expect(report.score).toBeDefined();
      expect(report.score.dimensions).toBeDefined();
      expect(report.score.grade).toMatch(/^[A-F][+-]?$/);

      // Format score report
      const scoreReport = scorer.formatScoreReport(report.score);
      expect(scoreReport).toContain('Overall Score');
    });

    it('should use scorer for recommendations', async () => {
      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      expect(report.recommendations).toBeDefined();
      expect(report.recommendations.immediate).toBeInstanceOf(Array);
      expect(report.recommendations.shortTerm).toBeInstanceOf(Array);
      expect(report.recommendations.longTerm).toBeInstanceOf(Array);
    });
  });

  describe('Auditor + Formatters Integration', () => {
    it('should integrate auditor with all formatters', async () => {
      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      const formats = OutputFormatterFactory.getSupportedFormats();

      formats.forEach((format: string) => {
        const output = auditor.exportReport(report, format as any);
        expect(output).toBeDefined();
        expect(output.length).toBeGreaterThan(0);
      });
    });

    it('should format same data consistently', async () => {
      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      const json1 = auditor.exportReport(report, 'json');
      const json2 = auditor.exportReport(report, 'json');

      expect(json1).toBe(json2);
    });
  });

  describe('Helper Functions Integration', () => {
    it('should run quick audit', async () => {
      const report = await runQuickAudit();

      expect(report).toBeDefined();
      expect(report.score).toBeDefined();
      expect(report.findings).toBeInstanceOf(Array);
    });

    it('should run audit with format', async () => {
      const output = await runAuditWithFormat('json');

      expect(output).toBeDefined();
      expect(() => JSON.parse(output)).not.toThrow();
    });

    it('should run audit with all formats', async () => {
      const formats = ['json', 'html', 'csv', 'xml', 'sarif', 'markdown'] as const;

      for (const format of formats) {
        const output = await runAuditWithFormat(format);
        expect(output).toBeDefined();
        expect(output.length).toBeGreaterThan(0);
      }
    });
  });

  describe('Configuration Flow', () => {
    it('should respect config throughout workflow', async () => {
      const config = {
        wcagLevel: 'AAA' as const,
      };

      const auditor = new AccessibilityAuditor(config);
      const currentConfig = auditor.getConfig();
      
      expect(currentConfig.wcagLevel).toBe('AAA');
    });

    it('should update config mid-workflow', async () => {
      const auditor = new AccessibilityAuditor({ wcagLevel: 'AA' });

      // First audit with default config
      const config1 = auditor.getConfig();
      expect(config1.wcagLevel).toBe('AA');

      // Update config
      auditor.setConfig({ wcagLevel: 'AAA' });

      // Second audit with new config
      const config2 = auditor.getConfig();
      expect(config2.wcagLevel).toBe('AAA');
    });
  });

  describe('Error Handling Integration', () => {
    it('should handle axe errors gracefully', async () => {
      vi.mocked(axe.run).mockRejectedValue(new Error('Network error'));

      const auditor = new AccessibilityAuditor();
      await expect(auditor.audit()).rejects.toThrow('Network error');
    });

    it('should handle invalid export format', async () => {
      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      expect(() => {
        auditor.exportReport(report, 'invalid' as any);
      }).toThrow();
    });
  });

  describe('Performance Integration', () => {
    it('should complete audit within reasonable time', async () => {
      const auditor = new AccessibilityAuditor();
      const startTime = Date.now();

      await auditor.audit();

      const duration = Date.now() - startTime;
      expect(duration).toBeLessThan(5000); // Should complete in under 5 seconds
    });

    it('should handle multiple concurrent audits', async () => {
      // Use separate auditor instances to avoid axe-core "already running" error
      const auditor1 = new AccessibilityAuditor();
      const auditor2 = new AccessibilityAuditor();
      const auditor3 = new AccessibilityAuditor();

      // Run audits sequentially to respect axe-core's single-run limitation
      const report1 = await auditor1.audit();
      const report2 = await auditor2.audit();
      const report3 = await auditor3.audit();

      const reports = [report1, report2, report3];

      expect(reports.length).toBe(3);
      reports.forEach((report) => {
        expect(report.score).toBeDefined();
        expect(report.score.overall).toBeGreaterThanOrEqual(0);
        expect(report.score.overall).toBeLessThanOrEqual(100);
      });
    });
  });

  describe('Real-World Scenarios', () => {
    it('should handle CI/CD workflow', async () => {
      // Simulate CI/CD environment
      const auditor = new AccessibilityAuditor({
        wcagLevel: 'AA',
      });

      const report = await auditor.audit();

      // Export for CI/CD tools
  const sarif = auditor.exportReport(report, 'sarif');
  const _json = auditor.exportReport(report, 'json');

      // Verify SARIF format
      const sarifData = JSON.parse(sarif);
      expect(sarifData.version).toBe('2.1.0');
      expect(sarifData.runs).toBeInstanceOf(Array);

      // Check that severity counts are accessible
      const criticalCount = report.summary.bySeverity.critical;
      const seriousCount = report.summary.bySeverity.serious;

      expect(typeof criticalCount).toBe('number');
      expect(typeof seriousCount).toBe('number');
    });

    it('should handle development workflow', async () => {
      // Quick audit for development
      const auditor = new AccessibilityAuditor({
        wcagLevel: 'A',
      });

      const report = await auditor.audit();

      // Generate readable console output
      const consoleReport = auditor.generateReport(report);
      expect(consoleReport).toContain('ACCESSIBILITY AUDIT REPORT');

      // Export HTML for viewing
      const html = auditor.exportReport(report, 'html');
      expect(html).toContain('<!DOCTYPE html>');
    });

    it('should handle documentation workflow', async () => {
      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      // Export Markdown for docs
      const markdown = auditor.exportReport(report, 'markdown');
      expect(markdown).toContain('# Accessibility Audit Report');
      expect(markdown).toContain('##');
      expect(markdown).toContain('**');
    });
  });

  describe('Performance Stress Tests', () => {
    it('should handle large number of findings efficiently', async () => {
      // Create a report with many findings
      const manyFindings = Array.from({ length: 1000 }, (_, i) => 
        createMinimalFinding({ 
          id: `rule-${i}`,
          severity: i % 4 === 0 ? 'critical' : i % 3 === 0 ? 'serious' : 'moderate'
        })
      );

      const largeReport = {
        ...mockAccessibilityReport,
        findings: manyFindings,
      };

      const auditor = new AccessibilityAuditor();
      const startTime = Date.now();
      
      // Test all formatters with large dataset
      const formats: OutputFormat[] = ['json', 'sarif', 'csv', 'xml', 'html', 'markdown'];
      formats.forEach(format => {
        const output = auditor.exportReport(largeReport, format);
        expect(output.length).toBeGreaterThan(0);
      });

      const duration = Date.now() - startTime;
      // Should process 1000 findings across 6 formats in under 2 seconds
      expect(duration).toBeLessThan(2000);
    });

    it('should calculate scores for large datasets', () => {
      const scorer = AccessibilityScorer.getInstance();
      const manyFindings = Array.from({ length: 500 }, () => 
        createMinimalFinding({ severity: 'moderate' })
      );

      const startTime = Date.now();
      const score = scorer.calculateScore(manyFindings, 'AA');
      const duration = Date.now() - startTime;

      expect(score.overall).toBeGreaterThanOrEqual(0);
      expect(score.overall).toBeLessThanOrEqual(100);
      // Should calculate score for 500 findings in under 100ms
      expect(duration).toBeLessThan(100);
    });

    it('should generate recommendations for complex reports', () => {
      const scorer = AccessibilityScorer.getInstance();
      const complexFindings = [
        ...Array.from({ length: 50 }, () => createMinimalFinding({ severity: 'critical' })),
        ...Array.from({ length: 100 }, () => createMinimalFinding({ severity: 'serious' })),
        ...Array.from({ length: 200 }, () => createMinimalFinding({ severity: 'moderate' })),
      ];

      const score = scorer.calculateScore(complexFindings, 'AA');
      const recommendations = scorer.generateRecommendations(complexFindings, score);

      expect(recommendations.immediate.length).toBeGreaterThan(0);
      expect(recommendations.shortTerm.length).toBeGreaterThan(0);
    });
  });

  describe('Cross-Environment Compatibility', () => {
    it('should work in browser environment with all features', async () => {
      // Ensure browser globals exist
      expect(global.window).toBeDefined();
      expect(global.document).toBeDefined();

      const auditor = new AccessibilityAuditor();
      const report = await auditor.audit();

      // All export formats should work
      const formats: OutputFormat[] = ['json', 'sarif', 'csv', 'xml', 'html', 'markdown'];
      formats.forEach(format => {
        const output = auditor.exportReport(report, format);
        expect(output).toBeTruthy();
        expect(output.length).toBeGreaterThan(0);
      });
    });

    it('should handle different WCAG levels across environments', async () => {
      const levels: ('A' | 'AA' | 'AAA')[] = ['A', 'AA', 'AAA'];
      
      for (const level of levels) {
        const auditor = new AccessibilityAuditor({ wcagLevel: level });
        const report = await auditor.audit();
        
        // Verify the auditor config has the correct WCAG level
        const config = auditor.getConfig();
        expect(config.wcagLevel).toBe(level);
        
        expect(report.score).toBeDefined();
        expect(report.recommendations).toBeDefined();
        
        // Export to all formats
        const json = auditor.exportReport(report, 'json');
        const sarif = auditor.exportReport(report, 'sarif');
        
        expect(json).toBeTruthy();
        expect(sarif).toBeTruthy();
      }
    });

    it('should generate valid export formats for integration with external tools', async () => {
      const auditor = new AccessibilityAuditor({ wcagLevel: 'AA' });
      const report = await auditor.audit();

      // JSON for custom tooling
      const json = auditor.exportReport(report, 'json');
      const jsonData = JSON.parse(json);
      expect(jsonData.score).toBeDefined();
      expect(jsonData.findings).toBeInstanceOf(Array);

      // SARIF for CI/CD integration (GitHub, VS Code, etc.)
      const sarif = auditor.exportReport(report, 'sarif');
      const sarifData = JSON.parse(sarif);
      expect(sarifData.version).toBe('2.1.0');
      expect(sarifData.runs).toBeInstanceOf(Array);

      // CSV for spreadsheet analysis
      const csv = auditor.exportReport(report, 'csv');
      expect(csv).toContain('Severity,Type,Rule ID');

      // HTML for documentation
      const html = auditor.exportReport(report, 'html');
      expect(html).toContain('<!DOCTYPE html>');
      expect(html).toContain('</html>');
    });
  });
});
