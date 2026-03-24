/**
 * @jest-environment node
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  CoverageAnalyzer,
  type FileCoverage,
  type CoverageMetrics,
  type CoverageAnalyzerConfig,
} from '../coverageAnalysis';

describe('CoverageAnalyzer', () => {
  let analyzer: CoverageAnalyzer;
  let mockCoverageData: Record<string, FileCoverage>;

  beforeEach(() => {
    const config: CoverageAnalyzerConfig = {
      thresholds: {
        global: { statements: 80, branches: 75, functions: 80, lines: 80 },
        critical: { statements: 95, branches: 90, functions: 95, lines: 95 },
      },
      criticalPathPatterns: ['src/core/**', 'src/security/**'],
      excludePatterns: ['**/__tests__/**', '**/*.test.ts'],
    };

    analyzer = new CoverageAnalyzer(config);

    mockCoverageData = {
      'src/index.ts': {
        path: 'src/index.ts',
        metrics: { statements: 90, branches: 85, functions: 90, lines: 90 },
        uncoveredLines: [42, 43],
        uncoveredBranches: [],
        totalStatements: 100,
        coveredStatements: 90,
      },
      'src/core/engine.ts': {
        path: 'src/core/engine.ts',
        metrics: { statements: 96, branches: 92, functions: 96, lines: 96 },
        uncoveredLines: [120],
        uncoveredBranches: [],
        totalStatements: 200,
        coveredStatements: 192,
      },
      'src/utils/helpers.ts': {
        path: 'src/utils/helpers.ts',
        metrics: { statements: 70, branches: 65, functions: 70, lines: 70 },
        uncoveredLines: [10, 11, 12, 15, 20],
        uncoveredBranches: [{ line: 10, branch: 1 }],
        totalStatements: 50,
        coveredStatements: 35,
      },
    };
  });

  describe('Initialization', () => {
    it('should initialize with configuration', () => {
      const config = analyzer.getConfig();
      expect(config.thresholds.global).toBeDefined();
      expect(config.thresholds.critical).toBeDefined();
    });

    it('should set default values for optional config', () => {
      const minimalAnalyzer = new CoverageAnalyzer({
        thresholds: {
          global: { statements: 80, branches: 80, functions: 80, lines: 80 },
        },
      });

      const config = minimalAnalyzer.getConfig();
      expect(config.failOnViolation).toBe(true);
      expect(config.allowDecrease).toBe(false);
    });
  });

  describe('Coverage Analysis', () => {
    it('should analyze coverage data', () => {
      const result = analyzer.analyze(mockCoverageData);

      expect(result).toBeDefined();
      expect(result.totalCoverage).toBeDefined();
      expect(result.fileCoverage).toHaveLength(3);
    });

    it('should calculate total coverage correctly', () => {
      const result = analyzer.analyze(mockCoverageData);

      expect(result.totalCoverage.statements).toBeCloseTo(85.33, 1);
      expect(result.totalCoverage.branches).toBeCloseTo(80.67, 1);
    });

    it('should mark critical paths', () => {
      const result = analyzer.analyze(mockCoverageData);

      const criticalFile = result.fileCoverage.find(
        (f) => f.path === 'src/core/engine.ts'
      );
      expect(criticalFile?.isCritical).toBe(true);
    });

    it('should not mark non-critical paths', () => {
      const result = analyzer.analyze(mockCoverageData);

      const nonCriticalFile = result.fileCoverage.find(
        (f) => f.path === 'src/utils/helpers.ts'
      );
      expect(nonCriticalFile?.isCritical).toBe(false);
    });

    it('should exclude files matching exclude patterns', () => {
      const dataWithTests = {
        ...mockCoverageData,
        'src/__tests__/index.test.ts': {
          path: 'src/__tests__/index.test.ts',
          metrics: { statements: 100, branches: 100, functions: 100, lines: 100 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 50,
          coveredStatements: 50,
        },
      };

      const result = analyzer.analyze(dataWithTests);

      expect(result.fileCoverage.find((f) => f.path.includes('__tests__'))).toBeUndefined();
    });
  });

  describe('Threshold Violations', () => {
    it('should detect files below global threshold', () => {
      const result = analyzer.analyze(mockCoverageData);

      const violations = result.violations.filter(
        (v) => v.path === 'src/utils/helpers.ts'
      );
      expect(violations.length).toBeGreaterThan(0);
    });

    it('should detect critical paths below critical threshold', () => {
      const lowCriticalData = {
        'src/core/low.ts': {
          path: 'src/core/low.ts',
          metrics: { statements: 85, branches: 80, functions: 85, lines: 85 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 85,
        },
      };

      const result = analyzer.analyze(lowCriticalData);

      expect(result.violations.some((v) => v.isCritical)).toBe(true);
    });

    it('should not report violations for files meeting thresholds', () => {
      const goodData = {
        'src/good.ts': {
          path: 'src/good.ts',
          metrics: { statements: 95, branches: 90, functions: 95, lines: 95 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 95,
        },
      };

      const result = analyzer.analyze(goodData);

      expect(result.violations).toHaveLength(0);
      expect(result.passed).toBe(true);
    });

    it('should calculate violation gaps correctly', () => {
      const result = analyzer.analyze(mockCoverageData);

      const violation = result.violations.find(
        (v) => v.path === 'src/utils/helpers.ts' && v.metric === 'statements'
      );

      expect(violation).toBeDefined();
      expect(violation!.actual).toBe(70);
      expect(violation!.required).toBe(80);
      expect(violation!.gap).toBe(10);
    });
  });

  describe('Summary Statistics', () => {
    it('should calculate summary correctly', () => {
      const result = analyzer.analyze(mockCoverageData);

      expect(result.summary.totalFiles).toBe(3);
      expect(result.summary.filesAboveThreshold).toBeGreaterThan(0);
      expect(result.summary.filesBelowThreshold).toBeGreaterThan(0);
    });

    it('should track critical path statistics', () => {
      const result = analyzer.analyze(mockCoverageData);

      expect(result.summary.criticalPathsAboveThreshold).toBeGreaterThanOrEqual(0);
      expect(result.summary.criticalPathsBelowThreshold).toBeGreaterThanOrEqual(0);
    });

    it('should identify critical paths correctly', () => {
      const result = analyzer.analyze(mockCoverageData);

      expect(result.criticalPaths.length).toBeGreaterThan(0);
      expect(result.criticalPaths.every((f) => f.isCritical)).toBe(true);
    });
  });

  describe('Coverage Comparison', () => {
    it('should return null when no previous coverage', () => {
      const result = analyzer.analyze(mockCoverageData);
      const delta = analyzer.compareToPrevious(result);

      expect(delta).toBeNull();
    });

    it('should calculate delta with previous coverage', () => {
      const result1 = analyzer.analyze(mockCoverageData);
      analyzer.storeCoverage(result1);

      const improvedData = {
        ...mockCoverageData,
        'src/index.ts': {
          ...mockCoverageData['src/index.ts'],
          metrics: { statements: 95, branches: 90, functions: 95, lines: 95 },
        },
      };

      const result2 = analyzer.analyze(improvedData);
      const delta = analyzer.compareToPrevious(result2);

      expect(delta).not.toBeNull();
      expect(delta!.statements).toBeGreaterThan(0);
    });

    it('should track improved files', () => {
      const result1 = analyzer.analyze(mockCoverageData);
      analyzer.storeCoverage(result1);

      const improvedData = {
        ...mockCoverageData,
        'src/utils/helpers.ts': {
          ...mockCoverageData['src/utils/helpers.ts'],
          metrics: { statements: 85, branches: 80, functions: 85, lines: 85 },
        },
      };

      const result2 = analyzer.analyze(improvedData);
      const delta = analyzer.compareToPrevious(result2);

      expect(delta!.improved).toContain('src/utils/helpers.ts');
    });

    it('should track degraded files', () => {
      const result1 = analyzer.analyze(mockCoverageData);
      analyzer.storeCoverage(result1);

      const degradedData = {
        ...mockCoverageData,
        'src/index.ts': {
          ...mockCoverageData['src/index.ts'],
          metrics: { statements: 80, branches: 75, functions: 80, lines: 80 },
        },
      };

      const result2 = analyzer.analyze(degradedData);
      const delta = analyzer.compareToPrevious(result2);

      expect(delta!.degraded).toContain('src/index.ts');
    });
  });

  describe('Report Generation', () => {
    let result: ReturnType<typeof analyzer.analyze>;

    beforeEach(() => {
      result = analyzer.analyze(mockCoverageData);
    });

    it('should generate JSON report', () => {
      const report = analyzer.generateReport(result, 'json');

      expect(report).toBeTruthy();
      expect(() => JSON.parse(report)).not.toThrow();
    });

    it('should generate JUnit XML report', () => {
      const report = analyzer.generateReport(result, 'junit');

      expect(report).toContain('<?xml version="1.0"');
      expect(report).toContain('<testsuites>');
      expect(report).toContain('</testsuites>');
    });

    it('should include test cases in JUnit report', () => {
      const report = analyzer.generateReport(result, 'junit');

      expect(report).toContain('<testcase');
      expect(report).toContain('name="src/index.ts"');
    });

    it('should include failures in JUnit report', () => {
      const report = analyzer.generateReport(result, 'junit');

      if (result.violations.length > 0) {
        expect(report).toContain('<failure');
      }
    });

    it('should generate Markdown report', () => {
      const report = analyzer.generateReport(result, 'markdown');

      expect(report).toContain('# Coverage Analysis Report');
      expect(report).toContain('## Summary');
      expect(report).toContain('## Overall Coverage');
    });

    it('should include status in Markdown report', () => {
      const report = analyzer.generateReport(result, 'markdown');

      expect(report).toMatch(/Status.*: (✅ PASSED|❌ FAILED)/);
    });

    it('should include violations table in Markdown report', () => {
      const report = analyzer.generateReport(result, 'markdown');

      if (result.violations.length > 0) {
        expect(report).toContain('## Threshold Violations');
        expect(report).toContain('| File | Metric | Actual | Required | Gap |');
      }
    });

    it('should generate HTML report', () => {
      const report = analyzer.generateReport(result, 'html');

      expect(report).toContain('<!DOCTYPE html>');
      expect(report).toContain('<title>Coverage Analysis Report</title>');
    });

    it('should include styling in HTML report', () => {
      const report = analyzer.generateReport(result, 'html');

      expect(report).toContain('<style>');
      expect(report).toContain('</style>');
    });
  });

  describe('Validation', () => {
    it('should pass when all thresholds met', () => {
      const goodData = {
        'src/good.ts': {
          path: 'src/good.ts',
          metrics: { statements: 95, branches: 90, functions: 95, lines: 95 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 95,
        },
      };

      const result = analyzer.analyze(goodData);
      const validation = analyzer.validate(result);

      expect(validation.passed).toBe(true);
      expect(validation.exitCode).toBe(0);
    });

    it('should fail when critical paths below threshold', () => {
      const criticalLowData = {
        'src/core/critical.ts': {
          path: 'src/core/critical.ts',
          metrics: { statements: 70, branches: 65, functions: 70, lines: 70 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 70,
        },
      };

      const result = analyzer.analyze(criticalLowData);
      const validation = analyzer.validate(result);

      expect(validation.passed).toBe(false);
      expect(validation.exitCode).toBe(1);
      expect(validation.message).toContain('critical');
    });

    it('should fail when failOnViolation is true', () => {
      const result = analyzer.analyze(mockCoverageData);
      const validation = analyzer.validate(result);

      if (result.violations.length > 0) {
        expect(validation.passed).toBe(false);
        expect(validation.exitCode).toBe(1);
      }
    });
  });

  describe('Configuration Management', () => {
    it('should update configuration', () => {
      analyzer.updateConfig({
        failOnViolation: false,
      });

      const config = analyzer.getConfig();
      expect(config.failOnViolation).toBe(false);
    });

    it('should merge threshold updates', () => {
      analyzer.updateConfig({
        thresholds: {
          global: { statements: 85, branches: 80, functions: 85, lines: 85 },
        },
      });

      const config = analyzer.getConfig();
      expect(config.thresholds.global?.statements).toBe(85);
    });
  });

  describe('Pattern Matching', () => {
    it('should match simple patterns', () => {
      const testData = {
        'src/test.ts': {
          path: 'src/test.ts',
          metrics: { statements: 90, branches: 85, functions: 90, lines: 90 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 90,
        },
      };

      const result = analyzer.analyze(testData);
      expect(result.fileCoverage).toHaveLength(1);
    });

    it('should match wildcard patterns', () => {
      const customAnalyzer = new CoverageAnalyzer({
        thresholds: {
          global: { statements: 80, branches: 75, functions: 80, lines: 80 },
        },
        criticalPathPatterns: ['src/core/*'],
      });

      const testData = {
        'src/core/test.ts': {
          path: 'src/core/test.ts',
          metrics: { statements: 90, branches: 85, functions: 90, lines: 90 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 90,
        },
      };

      const result = customAnalyzer.analyze(testData);
      expect(result.fileCoverage[0].isCritical).toBe(true);
    });

    it('should match recursive patterns', () => {
      const result = analyzer.analyze(mockCoverageData);

      const coreFile = result.fileCoverage.find((f) =>
        f.path.startsWith('src/core/')
      );
      expect(coreFile?.isCritical).toBe(true);
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty coverage data', () => {
      const result = analyzer.analyze({});

      expect(result.fileCoverage).toHaveLength(0);
      expect(result.totalCoverage.statements).toBe(0);
    });

    it('should handle perfect coverage', () => {
      const perfectData = {
        'src/perfect.ts': {
          path: 'src/perfect.ts',
          metrics: { statements: 100, branches: 100, functions: 100, lines: 100 },
          uncoveredLines: [],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 100,
        },
      };

      const result = analyzer.analyze(perfectData);

      expect(result.violations).toHaveLength(0);
      expect(result.passed).toBe(true);
    });

    it('should handle zero coverage', () => {
      const zeroData = {
        'src/zero.ts': {
          path: 'src/zero.ts',
          metrics: { statements: 0, branches: 0, functions: 0, lines: 0 },
          uncoveredLines: [1, 2, 3, 4, 5],
          uncoveredBranches: [],
          totalStatements: 100,
          coveredStatements: 0,
        },
      };

      const result = analyzer.analyze(zeroData);

      expect(result.violations.length).toBeGreaterThan(0);
      expect(result.passed).toBe(false);
    });
  });
});
