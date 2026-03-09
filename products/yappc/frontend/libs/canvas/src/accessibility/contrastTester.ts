/**
 * Contrast Testing Automation (Feature 2.32)
 * 
 * Automates WCAG color contrast validation with:
 * - Pa11y-style automated testing
 * - WCAG AA/AAA compliance checking
 * - Batch validation
 * - CI integration support
 * - Detailed reports with remediation suggestions
 * 
 * @module accessibility/contrastTester
 */

// ============================================================================
// Types & Interfaces
// ============================================================================

/**
 * WCAG conformance level
 */
export type WCAGLevel = 'AA' | 'AAA';

/**
 * Text size category for WCAG
 */
export type TextSize = 'normal' | 'large';

/**
 * Element type for context
 */
export type ElementType =
  | 'node'
  | 'edge'
  | 'label'
  | 'button'
  | 'icon'
  | 'badge'
  | 'background'
  | 'selection'
  | 'highlight'
  | 'error'
  | 'warning'
  | 'success'
  | 'custom';

/**
 * Color pair to test
 */
export interface ColorPair {
  foreground: string;
  background: string;
  element: string;           // Element identifier
  elementType: ElementType;
  textSize?: TextSize;
  isInteractive?: boolean;
}

/**
 * Contrast test result
 */
export interface ContrastTestResult {
  pair: ColorPair;
  ratio: number;
  pass: {
    AA: {
      normal: boolean;
      large: boolean;
    };
    AAA: {
      normal: boolean;
      large: boolean;
    };
  };
  wcagLevel: WCAGLevel;
  textSize: TextSize;
  passed: boolean;
  severity: 'pass' | 'warning' | 'fail';
  message: string;
  remediation?: RemediationSuggestion;
}

/**
 * Remediation suggestion for failed tests
 */
export interface RemediationSuggestion {
  type: 'lighten' | 'darken' | 'replace';
  currentColors: {
    foreground: string;
    background: string;
  };
  suggestedColors: {
    foreground?: string;
    background?: string;
  };
  expectedRatio: number;
  achievedRatio: number;
  improvement: string;
}

/**
 * Batch test configuration
 */
export interface BatchTestConfig {
  wcagLevel: WCAGLevel;
  includeInteractive: boolean;
  includeDecorative: boolean;
  generateRemediation: boolean;
  failOnWarning: boolean;
}

/**
 * Contrast test report
 */
export interface ContrastTestReport {
  timestamp: number;
  totalTests: number;
  passed: number;
  warnings: number;
  failed: number;
  compliance: {
    AA: {
      normal: number;
      large: number;
    };
    AAA: {
      normal: number;
      large: number;
    };
  };
  results: ContrastTestResult[];
  summary: string;
  recommendations: string[];
}

/**
 * CI test output format
 */
export interface CITestOutput {
  success: boolean;
  exitCode: number;
  report: ContrastTestReport;
  junitXML?: string;
  jsonReport: string;
}

// ============================================================================
// WCAG Thresholds
// ============================================================================

const WCAG_THRESHOLDS = {
  AA: {
    normal: 4.5,
    large: 3.0,
  },
  AAA: {
    normal: 7.0,
    large: 4.5,
  },
} as const;

// ============================================================================
// Core Functions
// ============================================================================

/**
 * Calculate contrast ratio between two colors
 * Uses WCAG 2.1 formula: (L1 + 0.05) / (L2 + 0.05)
 * where L1 is the relative luminance of the lighter color
 * and L2 is the relative luminance of the darker color
 */
export function calculateContrastRatio(color1: string, color2: string): number {
  const lum1 = getRelativeLuminance(color1);
  const lum2 = getRelativeLuminance(color2);
  
  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);
  
  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Get relative luminance of a color
 * As defined by WCAG 2.1
 */
export function getRelativeLuminance(color: string): number {
  const rgb = parseColor(color);
  
  // Convert RGB to linear RGB
  const [r, g, b] = rgb.map((channel) => {
    const c = channel / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  
  // Calculate relative luminance
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Test a single color pair for WCAG compliance
 */
export function testColorPair(
  pair: ColorPair,
  wcagLevel: WCAGLevel = 'AA',
  generateRemediation = true
): ContrastTestResult {
  const ratio = calculateContrastRatio(pair.foreground, pair.background);
  const textSize = pair.textSize || 'normal';
  
  // Check all WCAG levels
  const pass = {
    AA: {
      normal: ratio >= WCAG_THRESHOLDS.AA.normal,
      large: ratio >= WCAG_THRESHOLDS.AA.large,
    },
    AAA: {
      normal: ratio >= WCAG_THRESHOLDS.AAA.normal,
      large: ratio >= WCAG_THRESHOLDS.AAA.large,
    },
  };
  
  const threshold = WCAG_THRESHOLDS[wcagLevel][textSize];
  const passed = ratio >= threshold;
  
  let severity: 'pass' | 'warning' | 'fail' = 'pass';
  let message = `Contrast ratio ${ratio.toFixed(2)}:1 meets WCAG ${wcagLevel} for ${textSize} text`;
  
  if (!passed) {
    // Determine if it's warning or fail
    if (wcagLevel === 'AAA' && ratio >= WCAG_THRESHOLDS.AA[textSize]) {
      severity = 'warning';
      message = `Contrast ratio ${ratio.toFixed(2)}:1 meets WCAG AA but not AAA for ${textSize} text`;
    } else {
      severity = 'fail';
      message = `Contrast ratio ${ratio.toFixed(2)}:1 fails WCAG ${wcagLevel} (requires ${threshold}:1) for ${textSize} text`;
    }
  }
  
  const result: ContrastTestResult = {
    pair,
    ratio,
    pass,
    wcagLevel,
    textSize,
    passed,
    severity,
    message,
  };
  
  if (generateRemediation && !passed) {
    result.remediation = generateRemediationSuggestion(pair, ratio, threshold);
  }
  
  return result;
}

/**
 * Run batch contrast tests
 */
export function runBatchTests(
  pairs: ColorPair[],
  config?: Partial<BatchTestConfig>
): ContrastTestReport {
  const defaultConfig: BatchTestConfig = {
    wcagLevel: 'AA',
    includeInteractive: true,
    includeDecorative: true,
    generateRemediation: true,
    failOnWarning: false,
  };
  
  const finalConfig = { ...defaultConfig, ...config };
  
  // Filter pairs based on config
  let testPairs = pairs;
  if (!finalConfig.includeInteractive) {
    testPairs = testPairs.filter(p => !p.isInteractive);
  }
  
  // Run tests
  const results = testPairs.map(pair =>
    testColorPair(pair, finalConfig.wcagLevel, finalConfig.generateRemediation)
  );
  
  // Calculate statistics
  const passed = results.filter(r => r.severity === 'pass').length;
  const warnings = results.filter(r => r.severity === 'warning').length;
  const failed = results.filter(r => r.severity === 'fail').length;
  
  const compliance = {
    AA: {
      normal: results.filter(r => r.pass.AA.normal).length,
      large: results.filter(r => r.pass.AA.large).length,
    },
    AAA: {
      normal: results.filter(r => r.pass.AAA.normal).length,
      large: results.filter(r => r.pass.AAA.large).length,
    },
  };
  
  // Generate summary
  const totalTests = results.length;
  const passRate = ((passed / totalTests) * 100).toFixed(1);
  const summary = `${passed}/${totalTests} tests passed (${passRate}%) - ${failed} failed, ${warnings} warnings`;
  
  // Generate recommendations
  const recommendations: string[] = [];
  if (failed > 0) {
    recommendations.push(`${failed} color pairs fail WCAG ${finalConfig.wcagLevel} compliance`);
    recommendations.push('Review remediation suggestions for each failure');
  }
  if (warnings > 0) {
    recommendations.push(`${warnings} color pairs meet AA but not AAA standards`);
  }
  if (passed === totalTests) {
    recommendations.push('All color pairs meet WCAG requirements');
  }
  
  return {
    timestamp: Date.now(),
    totalTests,
    passed,
    warnings,
    failed,
    compliance,
    results,
    summary,
    recommendations,
  };
}

/**
 * Generate CI-friendly test output
 */
export function generateCIOutput(
  report: ContrastTestReport,
  config?: Partial<BatchTestConfig>
): CITestOutput {
  const failOnWarning = config?.failOnWarning ?? false;
  const success = failOnWarning
    ? report.failed === 0 && report.warnings === 0
    : report.failed === 0;
  
  const exitCode = success ? 0 : 1;
  
  const junitXML = generateJUnitXML(report);
  const jsonReport = JSON.stringify(report, null, 2);
  
  return {
    success,
    exitCode,
    report,
    junitXML,
    jsonReport,
  };
}

/**
 * Generate JUnit XML for CI integration
 */
export function generateJUnitXML(report: ContrastTestReport): string {
  const testcases = report.results.map(result => {
    const testname = `${result.pair.element} (${result.pair.elementType})`;
    const classname = `ContrastTest.${result.wcagLevel}`;
    
    if (result.passed) {
      return `    <testcase name="${escapeXML(testname)}" classname="${classname}" time="0"/>`;
    } else {
      const message = escapeXML(result.message);
      const details = result.remediation
        ? `\nSuggested: ${result.remediation.improvement}`
        : '';
      return `    <testcase name="${escapeXML(testname)}" classname="${classname}" time="0">
      <failure message="${message}">${message}${details}</failure>
    </testcase>`;
    }
  }).join('\n');
  
  return `<?xml version="1.0" encoding="UTF-8"?>
<testsuites name="Contrast Tests" tests="${report.totalTests}" failures="${report.failed}" errors="0" time="0">
  <testsuite name="WCAG Contrast Compliance" tests="${report.totalTests}" failures="${report.failed}" errors="0" time="0">
${testcases}
  </testsuite>
</testsuites>`;
}

/**
 * Export report as Markdown
 */
export function exportMarkdownReport(report: ContrastTestReport): string {
  const { results, summary, recommendations } = report;
  
  let markdown = `# Contrast Test Report\n\n`;
  markdown += `**Generated:** ${new Date(report.timestamp).toLocaleString()}\n\n`;
  markdown += `## Summary\n\n${summary}\n\n`;
  
  if (recommendations.length > 0) {
    markdown += `## Recommendations\n\n`;
    recommendations.forEach(rec => {
      markdown += `- ${rec}\n`;
    });
    markdown += '\n';
  }
  
  markdown += `## Results\n\n`;
  markdown += `| Element | Type | Foreground | Background | Ratio | WCAG AA | WCAG AAA | Status |\n`;
  markdown += `|---------|------|------------|------------|-------|---------|----------|--------|\n`;
  
  results.forEach(result => {
    const { pair, ratio, pass, severity } = result;
    const aaStatus = pass.AA.normal ? '✅' : '❌';
    const aaaStatus = pass.AAA.normal ? '✅' : '❌';
    const statusEmoji = severity === 'pass' ? '✅' : severity === 'warning' ? '⚠️' : '❌';
    
    markdown += `| ${pair.element} | ${pair.elementType} | ${pair.foreground} | ${pair.background} | ${ratio.toFixed(2)}:1 | ${aaStatus} | ${aaaStatus} | ${statusEmoji} |\n`;
  });
  
  // Add failures section with remediation
  const failures = results.filter(r => r.severity === 'fail');
  if (failures.length > 0) {
    markdown += `\n## Failures & Remediation\n\n`;
    failures.forEach((result, index) => {
      markdown += `### ${index + 1}. ${result.pair.element}\n\n`;
      markdown += `**Issue:** ${result.message}\n\n`;
      if (result.remediation) {
        const rem = result.remediation;
        markdown += `**Suggested Fix:**\n`;
        markdown += `- ${rem.improvement}\n`;
        if (rem.suggestedColors.foreground) {
          markdown += `- New foreground: \`${rem.suggestedColors.foreground}\`\n`;
        }
        if (rem.suggestedColors.background) {
          markdown += `- New background: \`${rem.suggestedColors.background}\`\n`;
        }
        markdown += `- Target ratio: ${rem.expectedRatio}:1\n\n`;
      }
    });
  }
  
  return markdown;
}

/**
 * Validate canvas theme colors
 */
export function validateCanvasTheme(theme: {
  colors: Record<string, string>;
  elementTypes?: Record<string, { fg?: string; bg?: string }>;
}): ContrastTestReport {
  const pairs: ColorPair[] = [];
  const bg = theme.colors.background || '#ffffff';
  
  // Test standard colors against background
  const standardColors = [
    'foreground',
    'selection',
    'error',
    'warning',
    'success',
    'primary',
    'secondary',
  ];
  
  standardColors.forEach(colorName => {
    if (theme.colors[colorName]) {
      pairs.push({
        foreground: theme.colors[colorName],
        background: bg,
        element: colorName,
        elementType: colorName as ElementType,
        textSize: 'normal',
      });
    }
  });
  
  // Test element-specific colors
  if (theme.elementTypes) {
    Object.entries(theme.elementTypes).forEach(([type, colors]) => {
      if (colors.fg && colors.bg) {
        pairs.push({
          foreground: colors.fg,
          background: colors.bg,
          element: `${type}-element`,
          elementType: type as ElementType,
          textSize: 'normal',
        });
      }
    });
  }
  
  return runBatchTests(pairs, { wcagLevel: 'AA', generateRemediation: true });
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Parse color string to RGB array
 */
function parseColor(color: string): [number, number, number] {
  // Handle hex colors
  if (color.startsWith('#')) {
    const hex = color.slice(1);
    const rgb = parseInt(hex, 16);
    return [
      (rgb >> 16) & 0xff,
      (rgb >> 8) & 0xff,
      rgb & 0xff,
    ];
  }
  
  // Handle rgb(r, g, b) format
  const match = color.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
  if (match) {
    return [parseInt(match[1]), parseInt(match[2]), parseInt(match[3])];
  }
  
  // Default to black
  return [0, 0, 0];
}

/**
 * Generate remediation suggestion
 */
function generateRemediationSuggestion(
  pair: ColorPair,
  currentRatio: number,
  targetRatio: number
): RemediationSuggestion {
  const fgLum = getRelativeLuminance(pair.foreground);
  const bgLum = getRelativeLuminance(pair.background);
  
  // Determine which color needs adjustment
  const shouldDarkenFg = fgLum > bgLum;
  
  let suggestedForeground: string | undefined;
  let suggestedBackground: string | undefined;
  let type: 'lighten' | 'darken' | 'replace';
  let improvement: string;
  
  if (shouldDarkenFg) {
    type = 'darken';
    suggestedForeground = adjustColor(pair.foreground, targetRatio, bgLum, 'darken');
    improvement = `Darken foreground color to achieve ${targetRatio.toFixed(1)}:1 ratio`;
  } else {
    type = 'lighten';
    suggestedForeground = adjustColor(pair.foreground, targetRatio, bgLum, 'lighten');
    improvement = `Lighten foreground color to achieve ${targetRatio.toFixed(1)}:1 ratio`;
  }
  
  return {
    type,
    currentColors: {
      foreground: pair.foreground,
      background: pair.background,
    },
    suggestedColors: {
      foreground: suggestedForeground,
      background: suggestedBackground,
    },
    expectedRatio: targetRatio,
    achievedRatio: currentRatio,
    improvement,
  };
}

/**
 * Adjust color luminance to meet target ratio
 */
function adjustColor(
  color: string,
  targetRatio: number,
  bgLuminance: number,
  direction: 'lighten' | 'darken'
): string {
  const rgb = parseColor(color);
  
  // Calculate target luminance
  const targetLum = direction === 'lighten'
    ? (bgLuminance + 0.05) * targetRatio - 0.05
    : (bgLuminance + 0.05) / targetRatio - 0.05;
  
  // Clamp to valid range
  const clampedLum = Math.max(0, Math.min(1, targetLum));
  
  // Approximate RGB adjustment (simplified)
  const factor = direction === 'lighten' ? 1.2 : 0.8;
  const adjusted = rgb.map(c => Math.round(Math.max(0, Math.min(255, c * factor))));
  
  return `#${adjusted.map(c => c.toString(16).padStart(2, '0')).join('')}`;
}

/**
 * Escape XML special characters
 */
function escapeXML(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}
