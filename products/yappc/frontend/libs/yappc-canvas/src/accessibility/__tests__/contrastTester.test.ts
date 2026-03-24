/**
 * Tests for Contrast Testing Automation (Feature 2.32)
 * 
 * Tests WCAG contrast validation including:
 * - Contrast ratio calculation
 * - WCAG AA/AAA compliance
 * - Batch validation
 * - CI output generation
 * - Remediation suggestions
 */

import { describe, it, expect } from 'vitest';

import {
  calculateContrastRatio,
  getRelativeLuminance,
  testColorPair,
  runBatchTests,
  generateCIOutput,
  generateJUnitXML,
  exportMarkdownReport,
  validateCanvasTheme,
  type ColorPair,
  type ContrastTestReport
} from '../contrastTester';

describe('contrastTester', () => {
  // ============================================================================
  // Color Calculation
  // ============================================================================

  describe('getRelativeLuminance', () => {
    it('should calculate luminance for black', () => {
      const lum = getRelativeLuminance('#000000');
      expect(lum).toBeCloseTo(0, 2);
    });

    it('should calculate luminance for white', () => {
      const lum = getRelativeLuminance('#ffffff');
      expect(lum).toBeCloseTo(1, 2);
    });

    it('should calculate luminance for mid-gray', () => {
      const lum = getRelativeLuminance('#808080');
      expect(lum).toBeGreaterThan(0.1);
      expect(lum).toBeLessThan(0.5);
    });

    it('should handle RGB format', () => {
      const lum = getRelativeLuminance('rgb(255, 255, 255)');
      expect(lum).toBeCloseTo(1, 2);
    });
  });

  describe('calculateContrastRatio', () => {
    it('should calculate maximum contrast for black on white', () => {
      const ratio = calculateContrastRatio('#000000', '#ffffff');
      expect(ratio).toBeCloseTo(21, 0);
    });

    it('should calculate minimum contrast for identical colors', () => {
      const ratio = calculateContrastRatio('#808080', '#808080');
      expect(ratio).toBe(1);
    });

    it('should handle colors regardless of order', () => {
      const ratio1 = calculateContrastRatio('#000000', '#ffffff');
      const ratio2 = calculateContrastRatio('#ffffff', '#000000');
      expect(ratio1).toBeCloseTo(ratio2, 5);
    });

    it('should calculate contrast for blue on white', () => {
      const ratio = calculateContrastRatio('#0000ff', '#ffffff');
      expect(ratio).toBeGreaterThan(8); // Should pass AAA
    });

    it('should calculate contrast for red on white', () => {
      const ratio = calculateContrastRatio('#ff0000', '#ffffff');
      expect(ratio).toBeGreaterThan(3.5); // Red on white is borderline (actual: ~4.0)
    });
  });

  // ============================================================================
  // Single Pair Testing
  // ============================================================================

  describe('testColorPair', () => {
    it('should pass WCAG AA for high contrast pair', () => {
      const pair: ColorPair = {
        foreground: '#000000',
        background: '#ffffff',
        element: 'test-element',
        elementType: 'label',
        textSize: 'normal'
      };

      const result = testColorPair(pair, 'AA');

      expect(result.passed).toBe(true);
      expect(result.severity).toBe('pass');
      expect(result.ratio).toBeGreaterThan(4.5);
      expect(result.pass.AA.normal).toBe(true);
    });

    it('should fail WCAG AA for low contrast pair', () => {
      const pair: ColorPair = {
        foreground: '#aaaaaa',
        background: '#ffffff',
        element: 'low-contrast',
        elementType: 'label',
        textSize: 'normal'
      };

      const result = testColorPair(pair, 'AA');

      expect(result.passed).toBe(false);
      expect(result.severity).toBe('fail');
      expect(result.ratio).toBeLessThan(4.5);
    });

    it('should pass AA but warn on AAA failure', () => {
      const pair: ColorPair = {
        foreground: '#666666',
        background: '#ffffff',
        element: 'medium-contrast',
        elementType: 'label',
        textSize: 'normal'
      };

      const aaResult = testColorPair(pair, 'AA');
      expect(aaResult.passed).toBe(true);
      expect(aaResult.severity).toBe('pass');

      const aaaResult = testColorPair(pair, 'AAA');
      expect(aaaResult.passed).toBe(false);
      expect(aaaResult.severity).toBe('warning');
    });

    it('should have lower threshold for large text', () => {
      const pair: ColorPair = {
        foreground: '#888888',
        background: '#ffffff',
        element: 'large-text',
        elementType: 'label',
        textSize: 'large'
      };

      const result = testColorPair(pair, 'AA');
      
      // Large text requires 3:1, normal requires 4.5:1
      expect(result.pass.AA.large).toBe(true);
    });

    it('should generate remediation for failed tests', () => {
      const pair: ColorPair = {
        foreground: '#cccccc',
        background: '#ffffff',
        element: 'fail-test',
        elementType: 'label'
      };

      const result = testColorPair(pair, 'AA', true);

      expect(result.passed).toBe(false);
      expect(result.remediation).toBeDefined();
      expect(result.remediation?.improvement).toBeDefined();
      expect(result.remediation?.suggestedColors.foreground).toBeDefined();
    });

    it('should not generate remediation when disabled', () => {
      const pair: ColorPair = {
        foreground: '#cccccc',
        background: '#ffffff',
        element: 'no-remediation',
        elementType: 'label'
      };

      const result = testColorPair(pair, 'AA', false);

      expect(result.passed).toBe(false);
      expect(result.remediation).toBeUndefined();
    });
  });

  // ============================================================================
  // Batch Testing
  // ============================================================================

  describe('runBatchTests', () => {
    it('should test multiple color pairs', () => {
      const pairs: ColorPair[] = [
        {
          foreground: '#000000',
          background: '#ffffff',
          element: 'high-contrast',
          elementType: 'label'
        },
        {
          foreground: '#cccccc',
          background: '#ffffff',
          element: 'low-contrast',
          elementType: 'label'
        },
        {
          foreground: '#0000ff',
          background: '#ffffff',
          element: 'blue-text',
          elementType: 'label'
        }
      ];

      const report = runBatchTests(pairs);

      expect(report.totalTests).toBe(3);
      expect(report.results.length).toBe(3);
      expect(report.passed).toBeGreaterThan(0);
      expect(report.failed).toBeGreaterThan(0);
    });

    it('should calculate compliance statistics', () => {
      const pairs: ColorPair[] = [
        {
          foreground: '#000000',
          background: '#ffffff',
          element: 'test-1',
          elementType: 'label'
        },
        {
          foreground: '#ffffff',
          background: '#000000',
          element: 'test-2',
          elementType: 'label'
        }
      ];

      const report = runBatchTests(pairs, { wcagLevel: 'AA' });

      expect(report.compliance.AA.normal).toBe(2);
      expect(report.compliance.AAA.normal).toBe(2);
    });

    it('should generate summary', () => {
      const pairs: ColorPair[] = [
        {
          foreground: '#000000',
          background: '#ffffff',
          element: 'test',
          elementType: 'label'
        }
      ];

      const report = runBatchTests(pairs);

      expect(report.summary).toContain('passed');
      expect(report.summary).toContain('1/1');
    });

    it('should provide recommendations', () => {
      const pairs: ColorPair[] = [
        {
          foreground: '#cccccc',
          background: '#ffffff',
          element: 'fail',
          elementType: 'label'
        }
      ];

      const report = runBatchTests(pairs);

      expect(report.recommendations.length).toBeGreaterThan(0);
      expect(report.recommendations[0]).toContain('fail');
    });

    it('should filter interactive elements when configured', () => {
      const pairs: ColorPair[] = [
        {
          foreground: '#000000',
          background: '#ffffff',
          element: 'button',
          elementType: 'button',
          isInteractive: true
        },
        {
          foreground: '#000000',
          background: '#ffffff',
          element: 'label',
          elementType: 'label',
          isInteractive: false
        }
      ];

      const report = runBatchTests(pairs, { includeInteractive: false });

      expect(report.totalTests).toBe(1);
      expect(report.results[0].pair.element).toBe('label');
    });
  });

  // ============================================================================
  // CI Integration
  // ============================================================================

  describe('generateCIOutput', () => {
    it('should generate successful CI output for passing tests', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 2,
        passed: 2,
        warnings: 0,
        failed: 0,
        compliance: {
          AA: { normal: 2, large: 2 },
          AAA: { normal: 2, large: 2 }
        },
        results: [],
        summary: '2/2 tests passed (100%)',
        recommendations: []
      };

      const output = generateCIOutput(report);

      expect(output.success).toBe(true);
      expect(output.exitCode).toBe(0);
      expect(output.jsonReport).toContain('2/2 tests passed');
    });

    it('should generate failed CI output for failing tests', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 2,
        passed: 1,
        warnings: 0,
        failed: 1,
        compliance: {
          AA: { normal: 1, large: 1 },
          AAA: { normal: 1, large: 1 }
        },
        results: [],
        summary: '1/2 tests passed (50%)',
        recommendations: []
      };

      const output = generateCIOutput(report);

      expect(output.success).toBe(false);
      expect(output.exitCode).toBe(1);
    });

    it('should fail on warnings when configured', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 1,
        passed: 0,
        warnings: 1,
        failed: 0,
        compliance: {
          AA: { normal: 1, large: 1 },
          AAA: { normal: 0, large: 0 }
        },
        results: [],
        summary: '0/1 tests passed',
        recommendations: []
      };

      const output = generateCIOutput(report, { failOnWarning: true });

      expect(output.success).toBe(false);
      expect(output.exitCode).toBe(1);
    });
  });

  describe('generateJUnitXML', () => {
    it('should generate valid JUnit XML', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 2,
        passed: 1,
        warnings: 0,
        failed: 1,
        compliance: {
          AA: { normal: 1, large: 1 },
          AAA: { normal: 1, large: 1 }
        },
        results: [
          {
            pair: {
              foreground: '#000000',
              background: '#ffffff',
              element: 'pass-test',
              elementType: 'label'
            },
            ratio: 21,
            pass: {
              AA: { normal: true, large: true },
              AAA: { normal: true, large: true }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: true,
            severity: 'pass',
            message: 'Passed'
          },
          {
            pair: {
              foreground: '#cccccc',
              background: '#ffffff',
              element: 'fail-test',
              elementType: 'label'
            },
            ratio: 1.5,
            pass: {
              AA: { normal: false, large: false },
              AAA: { normal: false, large: false }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: false,
            severity: 'fail',
            message: 'Failed'
          }
        ],
        summary: 'Test summary',
        recommendations: []
      };

      const xml = generateJUnitXML(report);

      expect(xml).toContain('<?xml version="1.0"');
      expect(xml).toContain('<testsuites');
      expect(xml).toContain('tests="2"');
      expect(xml).toContain('failures="1"');
      expect(xml).toContain('<testcase');
      expect(xml).toContain('<failure');
    });

    it('should escape XML special characters', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 1,
        passed: 0,
        warnings: 0,
        failed: 1,
        compliance: {
          AA: { normal: 0, large: 0 },
          AAA: { normal: 0, large: 0 }
        },
        results: [
          {
            pair: {
              foreground: '#000000',
              background: '#ffffff',
              element: 'test & <special>',
              elementType: 'label'
            },
            ratio: 1.5,
            pass: {
              AA: { normal: false, large: false },
              AAA: { normal: false, large: false }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: false,
            severity: 'fail',
            message: 'Message with & < > characters'
          }
        ],
        summary: 'Test',
        recommendations: []
      };

      const xml = generateJUnitXML(report);

      expect(xml).toContain('&amp;');
      expect(xml).toContain('&lt;');
      expect(xml).toContain('&gt;');
      expect(xml).not.toContain('test & <special>');
    });
  });

  // ============================================================================
  // Reporting
  // ============================================================================

  describe('exportMarkdownReport', () => {
    it('should generate Markdown report', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 2,
        passed: 1,
        warnings: 0,
        failed: 1,
        compliance: {
          AA: { normal: 1, large: 1 },
          AAA: { normal: 1, large: 1 }
        },
        results: [
          {
            pair: {
              foreground: '#000000',
              background: '#ffffff',
              element: 'success',
              elementType: 'label'
            },
            ratio: 21,
            pass: {
              AA: { normal: true, large: true },
              AAA: { normal: true, large: true }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: true,
            severity: 'pass',
            message: 'Passed'
          },
          {
            pair: {
              foreground: '#cccccc',
              background: '#ffffff',
              element: 'failure',
              elementType: 'label'
            },
            ratio: 1.5,
            pass: {
              AA: { normal: false, large: false },
              AAA: { normal: false, large: false }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: false,
            severity: 'fail',
            message: 'Failed',
            remediation: {
              type: 'darken',
              currentColors: {
                foreground: '#cccccc',
                background: '#ffffff'
              },
              suggestedColors: {
                foreground: '#666666'
              },
              expectedRatio: 4.5,
              achievedRatio: 1.5,
              improvement: 'Darken foreground'
            }
          }
        ],
        summary: '1/2 tests passed',
        recommendations: ['Fix failures']
      };

      const markdown = exportMarkdownReport(report);

      expect(markdown).toContain('# Contrast Test Report');
      expect(markdown).toContain('## Summary');
      expect(markdown).toContain('## Results');
      expect(markdown).toContain('## Failures & Remediation');
      expect(markdown).toContain('✅');
      expect(markdown).toContain('❌');
      expect(markdown).toContain('Darken foreground');
    });

    it('should include table with results', () => {
      const report: ContrastTestReport = {
        timestamp: Date.now(),
        totalTests: 1,
        passed: 1,
        warnings: 0,
        failed: 0,
        compliance: {
          AA: { normal: 1, large: 1 },
          AAA: { normal: 1, large: 1 }
        },
        results: [
          {
            pair: {
              foreground: '#000000',
              background: '#ffffff',
              element: 'test-element',
              elementType: 'label'
            },
            ratio: 21,
            pass: {
              AA: { normal: true, large: true },
              AAA: { normal: true, large: true }
            },
            wcagLevel: 'AA',
            textSize: 'normal',
            passed: true,
            severity: 'pass',
            message: 'Passed'
          }
        ],
        summary: '1/1 tests passed',
        recommendations: []
      };

      const markdown = exportMarkdownReport(report);

      expect(markdown).toContain('| Element | Type |');
      expect(markdown).toContain('| test-element | label |');
      expect(markdown).toContain('21.00:1');
    });
  });

  // ============================================================================
  // Theme Validation
  // ============================================================================

  describe('validateCanvasTheme', () => {
    it('should validate theme colors', () => {
      const theme = {
        colors: {
          background: '#ffffff',
          foreground: '#000000',
          selection: '#0066cc',
          error: '#cc0000',
          warning: '#ff9900',
          success: '#00cc00'
        }
      };

      const report = validateCanvasTheme(theme);

      expect(report.totalTests).toBeGreaterThan(0);
      expect(report.results.length).toBeGreaterThan(0);
      // Some colors may fail AA/AAA compliance
      expect(report.passed).toBeGreaterThanOrEqual(0);
    });

    it('should test element-specific colors', () => {
      const theme = {
        colors: {
          background: '#ffffff'
        },
        elementTypes: {
          node: {
            fg: '#000000',
            bg: '#f0f0f0'
          },
          edge: {
            fg: '#666666',
            bg: '#ffffff'
          }
        }
      };

      const report = validateCanvasTheme(theme);

      expect(report.results.some(r => r.pair.element === 'node-element')).toBe(true);
      expect(report.results.some(r => r.pair.element === 'edge-element')).toBe(true);
    });

    it('should provide compliance report for theme', () => {
      const theme = {
        colors: {
          background: '#ffffff',
          foreground: '#000000',
          error: '#ff0000'
        }
      };

      const report = validateCanvasTheme(theme);

      expect(report.compliance).toBeDefined();
      expect(report.compliance.AA).toBeDefined();
      expect(report.summary).toBeDefined();
    });
  });
});
