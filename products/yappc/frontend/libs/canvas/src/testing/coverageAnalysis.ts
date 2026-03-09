/**
 * @module coverageAnalysis
 * @description Coverage analysis and reporting system with threshold enforcement,
 * critical path identification, and CI integration
 *
 * Core Responsibilities:
 * - Analyze test coverage metrics from Vitest/Istanbul reports
 * - Enforce coverage thresholds per file/directory/project
 * - Identify critical paths requiring high coverage
 * - Generate coverage reports for CI/CD integration
 * - Track coverage trends over time
 *
 * Key Features:
 * - Multi-level threshold enforcement (statements/branches/functions/lines)
 * - Critical path detection based on file patterns
 * - JUnit XML and JSON report generation
 * - Coverage delta calculation between runs
 * - Configurable gates for CI failure
 *
 * Example Usage:
 * ```typescript
 * const analyzer = new CoverageAnalyzer({
 *   thresholds: {
 *     global: { statements: 90, branches: 85, functions: 90, lines: 90 },
 *     critical: { statements: 95, branches: 90, functions: 95, lines: 95 }
 *   }
 * });
 *
 * // Analyze coverage report
 * const result = analyzer.analyze(coverageData);
 *
 * // Check if meets thresholds
 * if (!result.passed) {
 *   console.error('Coverage below thresholds:', result.failures);
 *   process.exit(1);
 * }
 * ```
 */

/**
 * Coverage metrics for a file or aggregate
 */
export interface CoverageMetrics {
  /**
   * Statement coverage percentage
   */
  statements: number;
  /**
   * Branch coverage percentage
   */
  branches: number;
  /**
   * Function coverage percentage
   */
  functions: number;
  /**
   * Line coverage percentage
   */
  lines: number;
}

/**
 * Detailed coverage data for a file
 */
export interface FileCoverage {
  /**
   * File path
   */
  path: string;
  /**
   * Coverage metrics
   */
  metrics: CoverageMetrics;
  /**
   * Uncovered line numbers
   */
  uncoveredLines: number[];
  /**
   * Uncovered branches
   */
  uncoveredBranches: Array<{ line: number; branch: number }>;
  /**
   * Total statements
   */
  totalStatements: number;
  /**
   * Covered statements
   */
  coveredStatements: number;
  /**
   * Is critical path
   */
  isCritical?: boolean;
}

/**
 * Coverage threshold configuration
 */
export interface CoverageThresholds {
  /**
   * Global thresholds for all files
   */
  global?: CoverageMetrics;
  /**
   * Per-directory thresholds
   */
  perDirectory?: Map<string, CoverageMetrics>;
  /**
   * Critical path thresholds (higher requirements)
   */
  critical?: CoverageMetrics;
}

/**
 * Threshold violation
 */
export interface ThresholdViolation {
  /**
   * File or directory path
   */
  path: string;
  /**
   * Metric that failed
   */
  metric: keyof CoverageMetrics;
  /**
   * Actual coverage
   */
  actual: number;
  /**
   * Required threshold
   */
  required: number;
  /**
   * Gap (required - actual)
   */
  gap: number;
  /**
   * Is critical path
   */
  isCritical: boolean;
}

/**
 * Coverage analysis result
 */
export interface CoverageAnalysisResult {
  /**
   * Overall pass/fail
   */
  passed: boolean;
  /**
   * Total coverage metrics
   */
  totalCoverage: CoverageMetrics;
  /**
   * Per-file coverage
   */
  fileCoverage: FileCoverage[];
  /**
   * Threshold violations
   */
  violations: ThresholdViolation[];
  /**
   * Critical path files
   */
  criticalPaths: FileCoverage[];
  /**
   * Summary statistics
   */
  summary: {
    totalFiles: number;
    filesAboveThreshold: number;
    filesBelowThreshold: number;
    criticalPathsAboveThreshold: number;
    criticalPathsBelowThreshold: number;
  };
}

/**
 * Coverage delta (comparison between two runs)
 */
export interface CoverageDelta {
  /**
   * Statements delta
   */
  statements: number;
  /**
   * Branches delta
   */
  branches: number;
  /**
   * Functions delta
   */
  functions: number;
  /**
   * Lines delta
   */
  lines: number;
  /**
   * Improved files
   */
  improved: string[];
  /**
   * Degraded files
   */
  degraded: string[];
}

/**
 * Coverage analyzer configuration
 */
export interface CoverageAnalyzerConfig {
  /**
   * Coverage thresholds
   */
  thresholds: CoverageThresholds;
  /**
   * Critical path patterns (glob)
   */
  criticalPathPatterns?: string[];
  /**
   * Exclude patterns
   */
  excludePatterns?: string[];
  /**
   * Fail on any violation
   */
  failOnViolation?: boolean;
  /**
   * Allow coverage decrease
   */
  allowDecrease?: boolean;
  /**
   * Max allowed decrease percentage
   */
  maxDecreasePercentage?: number;
}

/**
 * Coverage report format
 */
export type ReportFormat = 'json' | 'junit' | 'markdown' | 'html';

/**
 * Coverage Analyzer
 * Analyzes test coverage and enforces quality gates
 */
export class CoverageAnalyzer {
  private config: CoverageAnalyzerConfig;
  private previousCoverage?: CoverageAnalysisResult;

  /**
   *
   */
  constructor(config: CoverageAnalyzerConfig) {
    this.config = {
      criticalPathPatterns: [],
      excludePatterns: [],
      failOnViolation: true,
      allowDecrease: false,
      maxDecreasePercentage: 0,
      ...config,
    };
  }

  /**
   * Analyze coverage data
   */
  analyze(coverageData: Record<string, FileCoverage>): CoverageAnalysisResult {
    const files = Object.values(coverageData).filter((file) =>
      !this.isExcluded(file.path)
    );

    // Mark critical paths
    files.forEach((file) => {
      file.isCritical = this.isCriticalPath(file.path);
    });

    // Calculate total coverage
    const totalCoverage = this.calculateTotalCoverage(files);

    // Find violations
    const violations = this.findViolations(files);

    // Separate critical paths
    const criticalPaths = files.filter((f) => f.isCritical);

    // Calculate summary
    const summary = this.calculateSummary(files, violations);

    const result: CoverageAnalysisResult = {
      passed: violations.length === 0,
      totalCoverage,
      fileCoverage: files,
      violations,
      criticalPaths,
      summary,
    };

    return result;
  }

  /**
   * Calculate total coverage across all files
   */
  private calculateTotalCoverage(files: FileCoverage[]): CoverageMetrics {
    if (files.length === 0) {
      return { statements: 0, branches: 0, functions: 0, lines: 0 };
    }

    const totals = files.reduce(
      (acc, file) => ({
        statements: acc.statements + file.metrics.statements,
        branches: acc.branches + file.metrics.branches,
        functions: acc.functions + file.metrics.functions,
        lines: acc.lines + file.metrics.lines,
      }),
      { statements: 0, branches: 0, functions: 0, lines: 0 }
    );

    return {
      statements: totals.statements / files.length,
      branches: totals.branches / files.length,
      functions: totals.functions / files.length,
      lines: totals.lines / files.length,
    };
  }

  /**
   * Find threshold violations
   */
  private findViolations(files: FileCoverage[]): ThresholdViolation[] {
    const violations: ThresholdViolation[] = [];

    for (const file of files) {
      const thresholds = this.getThresholdsForFile(file);

      for (const metric of ['statements', 'branches', 'functions', 'lines'] as const) {
        const actual = file.metrics[metric];
        const required = thresholds[metric];

        if (actual < required) {
          violations.push({
            path: file.path,
            metric,
            actual,
            required,
            gap: required - actual,
            isCritical: file.isCritical || false,
          });
        }
      }
    }

    return violations;
  }

  /**
   * Get applicable thresholds for a file
   */
  private getThresholdsForFile(file: FileCoverage): CoverageMetrics {
    // Critical path gets highest requirements
    if (file.isCritical && this.config.thresholds.critical) {
      return this.config.thresholds.critical;
    }

    // Check per-directory thresholds
    if (this.config.thresholds.perDirectory) {
      for (const [dir, thresholds] of this.config.thresholds.perDirectory) {
        if (file.path.startsWith(dir)) {
          return thresholds;
        }
      }
    }

    // Fall back to global
    return this.config.thresholds.global || {
      statements: 0,
      branches: 0,
      functions: 0,
      lines: 0,
    };
  }

  /**
   * Check if path is critical
   */
  private isCriticalPath(path: string): boolean {
    if (!this.config.criticalPathPatterns) return false;

    return this.config.criticalPathPatterns.some((pattern) =>
      this.matchesPattern(path, pattern)
    );
  }

  /**
   * Check if path is excluded
   */
  private isExcluded(path: string): boolean {
    if (!this.config.excludePatterns) return false;

    return this.config.excludePatterns.some((pattern) =>
      this.matchesPattern(path, pattern)
    );
  }

  /**
   * Match path against glob pattern
   */
  private matchesPattern(path: string, pattern: string): boolean {
    // Simple glob matching (supports * and **)
    const regexPattern = pattern
      .replace(/\*\*/g, '§§§') // Placeholder for **
      .replace(/\*/g, '[^/]*') // * matches anything except /
      .replace(/§§§/g, '.*'); // ** matches everything

    return new RegExp(`^${regexPattern}$`).test(path);
  }

  /**
   * Calculate summary statistics
   */
  private calculateSummary(
    files: FileCoverage[],
    violations: ThresholdViolation[]
  ): CoverageAnalysisResult['summary'] {
    const violatedFiles = new Set(violations.map((v) => v.path));
    const criticalPaths = files.filter((f) => f.isCritical);
    const criticalViolations = violations.filter((v) => v.isCritical);
    const violatedCriticalPaths = new Set(
      criticalViolations.map((v) => v.path)
    );

    return {
      totalFiles: files.length,
      filesAboveThreshold: files.length - violatedFiles.size,
      filesBelowThreshold: violatedFiles.size,
      criticalPathsAboveThreshold:
        criticalPaths.length - violatedCriticalPaths.size,
      criticalPathsBelowThreshold: violatedCriticalPaths.size,
    };
  }

  /**
   * Compare with previous coverage
   */
  compareToPrevious(
    current: CoverageAnalysisResult
  ): CoverageDelta | null {
    if (!this.previousCoverage) return null;

    const prev = this.previousCoverage.totalCoverage;
    const curr = current.totalCoverage;

    const improved: string[] = [];
    const degraded: string[] = [];

    // Compare per-file coverage
    const prevFileMap = new Map(
      this.previousCoverage.fileCoverage.map((f) => [f.path, f])
    );

    for (const currFile of current.fileCoverage) {
      const prevFile = prevFileMap.get(currFile.path);
      if (!prevFile) continue;

      const hasImprovement = Object.keys(currFile.metrics).some(
        (key) =>
          currFile.metrics[key as keyof CoverageMetrics] >
          prevFile.metrics[key as keyof CoverageMetrics]
      );

      const hasDegradation = Object.keys(currFile.metrics).some(
        (key) =>
          currFile.metrics[key as keyof CoverageMetrics] <
          prevFile.metrics[key as keyof CoverageMetrics]
      );

      if (hasImprovement && !hasDegradation) {
        improved.push(currFile.path);
      } else if (hasDegradation) {
        degraded.push(currFile.path);
      }
    }

    return {
      statements: curr.statements - prev.statements,
      branches: curr.branches - prev.branches,
      functions: curr.functions - prev.functions,
      lines: curr.lines - prev.lines,
      improved,
      degraded,
    };
  }

  /**
   * Store current coverage for comparison
   */
  storeCoverage(result: CoverageAnalysisResult): void {
    this.previousCoverage = result;
  }

  /**
   * Generate report in specified format
   */
  generateReport(
    result: CoverageAnalysisResult,
    format: ReportFormat
  ): string {
    switch (format) {
      case 'json':
        return this.generateJsonReport(result);
      case 'junit':
        return this.generateJUnitReport(result);
      case 'markdown':
        return this.generateMarkdownReport(result);
      case 'html':
        return this.generateHtmlReport(result);
      default:
        throw new Error(`Unsupported format: ${format}`);
    }
  }

  /**
   * Generate JSON report
   */
  private generateJsonReport(result: CoverageAnalysisResult): string {
    return JSON.stringify(result, null, 2);
  }

  /**
   * Generate JUnit XML report
   */
  private generateJUnitReport(result: CoverageAnalysisResult): string {
    const lines: string[] = [
      '<?xml version="1.0" encoding="UTF-8"?>',
      '<testsuites>',
      `  <testsuite name="Coverage Analysis" tests="${result.summary.totalFiles}" failures="${result.violations.length}">`,
    ];

    for (const file of result.fileCoverage) {
      const fileViolations = result.violations.filter(
        (v) => v.path === file.path
      );

      if (fileViolations.length === 0) {
        lines.push(
          `    <testcase name="${file.path}" classname="coverage"/>`
        );
      } else {
        lines.push(`    <testcase name="${file.path}" classname="coverage">`);
        for (const violation of fileViolations) {
          lines.push(
            `      <failure message="${violation.metric} coverage ${violation.actual.toFixed(2)}% below threshold ${violation.required}%"/>`
          );
        }
        lines.push('    </testcase>');
      }
    }

    lines.push('  </testsuite>');
    lines.push('</testsuites>');

    return lines.join('\n');
  }

  /**
   * Generate Markdown report
   */
  private generateMarkdownReport(result: CoverageAnalysisResult): string {
    const lines: string[] = [
      '# Coverage Analysis Report',
      '',
      `**Status**: ${result.passed ? '✅ PASSED' : '❌ FAILED'}`,
      '',
      '## Summary',
      '',
      `- Total Files: ${result.summary.totalFiles}`,
      `- Files Above Threshold: ${result.summary.filesAboveThreshold}`,
      `- Files Below Threshold: ${result.summary.filesBelowThreshold}`,
      `- Critical Paths: ${result.criticalPaths.length}`,
      `- Critical Paths Above Threshold: ${result.summary.criticalPathsAboveThreshold}`,
      '',
      '## Overall Coverage',
      '',
      `- Statements: ${result.totalCoverage.statements.toFixed(2)}%`,
      `- Branches: ${result.totalCoverage.branches.toFixed(2)}%`,
      `- Functions: ${result.totalCoverage.functions.toFixed(2)}%`,
      `- Lines: ${result.totalCoverage.lines.toFixed(2)}%`,
    ];

    if (result.violations.length > 0) {
      lines.push('', '## Threshold Violations', '');
      lines.push('| File | Metric | Actual | Required | Gap |');
      lines.push('|------|--------|--------|----------|-----|');

      for (const violation of result.violations) {
        const critical = violation.isCritical ? ' 🔴' : '';
        lines.push(
          `| ${violation.path}${critical} | ${violation.metric} | ${violation.actual.toFixed(2)}% | ${violation.required}% | ${violation.gap.toFixed(2)}% |`
        );
      }
    }

    return lines.join('\n');
  }

  /**
   * Generate HTML report
   */
  private generateHtmlReport(result: CoverageAnalysisResult): string {
    const status = result.passed ? 'PASSED' : 'FAILED';
    const statusColor = result.passed ? 'green' : 'red';

    return `
<!DOCTYPE html>
<html>
<head>
  <title>Coverage Analysis Report</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    .status { font-size: 24px; color: ${statusColor}; font-weight: bold; }
    table { border-collapse: collapse; width: 100%; margin-top: 20px; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #f2f2f2; }
    .critical { color: red; font-weight: bold; }
  </style>
</head>
<body>
  <h1>Coverage Analysis Report</h1>
  <p class="status">Status: ${status}</p>
  
  <h2>Summary</h2>
  <ul>
    <li>Total Files: ${result.summary.totalFiles}</li>
    <li>Files Above Threshold: ${result.summary.filesAboveThreshold}</li>
    <li>Files Below Threshold: ${result.summary.filesBelowThreshold}</li>
  </ul>
  
  <h2>Overall Coverage</h2>
  <ul>
    <li>Statements: ${result.totalCoverage.statements.toFixed(2)}%</li>
    <li>Branches: ${result.totalCoverage.branches.toFixed(2)}%</li>
    <li>Functions: ${result.totalCoverage.functions.toFixed(2)}%</li>
    <li>Lines: ${result.totalCoverage.lines.toFixed(2)}%</li>
  </ul>
  
  ${
    result.violations.length > 0
      ? `
  <h2>Threshold Violations</h2>
  <table>
    <tr>
      <th>File</th>
      <th>Metric</th>
      <th>Actual</th>
      <th>Required</th>
      <th>Gap</th>
    </tr>
    ${result.violations
      .map(
        (v) => `
    <tr class="${v.isCritical ? 'critical' : ''}">
      <td>${v.path}</td>
      <td>${v.metric}</td>
      <td>${v.actual.toFixed(2)}%</td>
      <td>${v.required}%</td>
      <td>${v.gap.toFixed(2)}%</td>
    </tr>
    `
      )
      .join('')}
  </table>
  `
      : ''
  }
</body>
</html>
    `.trim();
  }

  /**
   * Check if coverage meets requirements
   */
  validate(result: CoverageAnalysisResult): {
    passed: boolean;
    exitCode: number;
    message: string;
  } {
    if (result.passed) {
      return {
        passed: true,
        exitCode: 0,
        message: 'All coverage thresholds met',
      };
    }

    const criticalViolations = result.violations.filter((v) => v.isCritical);

    if (criticalViolations.length > 0) {
      return {
        passed: false,
        exitCode: 1,
        message: `${criticalViolations.length} critical path(s) below threshold`,
      };
    }

    if (this.config.failOnViolation) {
      return {
        passed: false,
        exitCode: 1,
        message: `${result.violations.length} file(s) below threshold`,
      };
    }

    return {
      passed: true,
      exitCode: 0,
      message: 'Coverage acceptable with warnings',
    };
  }

  /**
   * Get configuration
   */
  getConfig(): CoverageAnalyzerConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<CoverageAnalyzerConfig>): void {
    this.config = {
      ...this.config,
      ...config,
      thresholds: {
        ...this.config.thresholds,
        ...config.thresholds,
      },
    };
  }
}
