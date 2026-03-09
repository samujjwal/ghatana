/**
 * @fileoverview Enhanced Accessibility Auditor with comprehensive reporting
 * @module @ghatana/accessibility-audit
 *
 * Provides accessibility auditing for pages, components, and design systems
 * with actionable reports and multiple output formats.
 *
 * @example Basic usage
 * ```typescript
 * const auditor = new AccessibilityAuditor();
 * await auditor.initialize();
 * const report = await auditor.audit();
 * console.log(`Score: ${report.score.overall} (${report.score.grade})`);
 * ```
 *
 * @example Export report
 * ```typescript
 * const auditor = new AccessibilityAuditor();
 * const report = await auditor.audit();
 * const html = auditor.exportReport(report, 'html');
 * await auditor.saveReport(report, 'accessibility-report', 'html');
 * ```
 */

import { OutputFormatterFactory } from "./formatters";
import { AccessibilityScorer } from "./scoring/AccessibilityScorer";

import type {
  AccessibilityReport,
  Finding,
  ViolationSeverity,
  WCAGLevel,
  WCAGClassification,
  AuditSummary,
  RemediationGuidance,
  AuditMetadata,
  OutputFormat,
  AffectedUserGroup,
  ComparisonResult,
  AccessibilityScore,
} from "./types";
import type {
  AxeResults,
  RunOptions,
  Spec,
  Result as AxeResult,
  NodeResult,
} from "axe-core";
import type axe from "axe-core";

/**
 * Simple configuration for the auditor
 */
export interface SimplifiedAuditConfig {
  /** WCAG level to test against */
  wcagLevel?: WCAGLevel;

  /** Include specific rules */
  includeRules?: string[];

  /** Exclude specific rules */
  excludeRules?: string[];

  /** Minimum score threshold */
  minScore?: number;
}

/**
 * Legacy violation format for backward compatibility
 * @deprecated Use Finding from ./types instead
 */
export interface AccessibilityViolation {
  id: string;
  impact: "minor" | "moderate" | "serious" | "critical" | null;
  description: string;
  help: string;
  helpUrl: string;
  html: string;
  tags: string[];
  nodes: Array<{
    html: string;
    target: string[];
    failureSummary: string;
    element: HTMLElement | null;
  }>;
}

/**
 * Legacy report format for backward compatibility
 * @deprecated Use AccessibilityReport from ./types instead
 */
export interface LegacyAccessibilityReport {
  violations: AccessibilityViolation[];
  passes: AxeResult[];
  incomplete: AxeResult[];
  inapplicable: AxeResult[];
  timestamp: string;
  url: string;
}

/**
 * Main accessibility auditor class
 *
 * This class provides comprehensive WCAG accessibility testing using axe-core,
 * with enhanced reporting, scoring, and multiple export formats.
 *
 * ## Features
 * - WCAG 2.0/2.1 compliance testing (A, AA, AAA)
 * - Intelligent scoring across 8 accessibility dimensions
 * - Multiple export formats (JSON, HTML, SARIF, CSV, XML, Markdown)
 * - Trend analysis for tracking improvements
 * - Actionable recommendations prioritized by severity
 *
 * ## Usage
 *
 * ### Basic Audit
 * ```typescript
 * const auditor = new AccessibilityAuditor();
 * const report = await auditor.audit();
 * console.log(`Score: ${report.score.overall}/100`);
 * console.log(`Grade: ${report.score.grade}`);
 * console.log(`Issues: ${report.summary.totalFindings}`);
 * ```
 *
 * ### Audit Specific Element
 * ```typescript
 * const auditor = new AccessibilityAuditor({ wcagLevel: 'AAA' });
 * const element = document.querySelector('#my-component');
 * const report = await auditor.audit(element);
 * ```
 *
 * ### Configure Rules
 * ```typescript
 * const auditor = new AccessibilityAuditor({
 *   wcagLevel: 'AA',
 *   includeRules: ['color-contrast', 'button-name'],
 *   excludeRules: ['region']
 * });
 * ```
 *
 * ### Export Reports
 * ```typescript
 * const report = await auditor.audit();
 *
 * // HTML report for viewing
 * const html = auditor.exportReport(report, 'html');
 * auditor.saveReport(report, 'accessibility-report', 'html');
 *
 * // SARIF for CI/CD integration
 * const sarif = auditor.exportReport(report, 'sarif');
 *
 * // JSON for programmatic access
 * const json = auditor.exportReport(report, 'json');
 * ```
 *
 * ### Track Progress Over Time
 * ```typescript
 * const previousReport = await auditor.audit();
 * // ... make improvements ...
 * const currentReport = await auditor.audit();
 * const comparison = auditor.compareReports(previousReport, currentReport);
 *
 * console.log(`Trend: ${comparison.trend.direction}`);
 * console.log(`Change: ${comparison.trend.changePercentage}%`);
 * ```
 *
 * @public
 */
export class AccessibilityAuditor {
  // Use `typeof axe` for proper type safety with dynamically imported axe-core module
  private axeCore: typeof axe | null = null;
  private isInitialized = false;
  private config: SimplifiedAuditConfig;
  private scorer: AccessibilityScorer;

  /**
   * Create a new accessibility auditor
   * @param config Optional configuration for the auditor
   *
   * @example
   * ```typescript
   * const auditor = new AccessibilityAuditor({
   *   wcagLevel: 'AA',
   *   minScore: 80
   * });
   * ```
   */
  constructor(config?: SimplifiedAuditConfig) {
    this.config = {
      wcagLevel: "AA",
      minScore: 80,
      ...config,
    };
    this.scorer = AccessibilityScorer.getInstance();
  }

  /**
   * Get current configuration
   */
  public getConfig(): SimplifiedAuditConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   * @param config Partial configuration to merge
   */
  public setConfig(config: Partial<SimplifiedAuditConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get list of supported output formats
   */
  public getSupportedFormats(): OutputFormat[] {
    return ["json", "html", "csv", "sarif", "xml", "markdown"];
  }

  /**
   * Initialize the accessibility auditor
   * Must be called before running audits
   */
  public async initialize(): Promise<void> {
    if (this.isInitialized) return;

    // Dynamically import axe-core to avoid loading it in the main bundle
    this.axeCore = (await import("axe-core")).default;

    // Configure axe-core with additional rules and options
    this.axeCore.configure({
      rules: [
        { id: "landmark-one-main", enabled: true },
        { id: "page-has-heading-one", enabled: true },
        { id: "region", enabled: true },
      ],
    } as Spec);

    this.isInitialized = true;
  }

  /**
   * Run accessibility audit and return comprehensive report
   *
   * Analyzes the specified context (or entire page) for WCAG compliance violations,
   * calculates dimensional scores, and generates actionable recommendations.
   *
   * @param context - Element, document, or CSS selector to audit. Defaults to entire document.
   * @param options - axe-core run options for fine-tuning the audit
   *
   * @returns Complete accessibility report containing:
   *   - `score`: Overall and dimensional scores (0-100) with letter grade
   *   - `findings`: Array of accessibility violations with severity and remediation
   *   - `recommendations`: Prioritized action items (immediate, short-term, long-term)
   *   - `summary`: Statistics about violations by severity and type
   *   - `metadata`: Audit timestamp, tool version, and configuration
   *
   * @throws {Error} If axe-core is not initialized (call `initialize()` first)
   * @throws {Error} If axe-core fails during analysis
   *
   * @example Basic audit of entire page
   * ```typescript
   * const auditor = new AccessibilityAuditor();
   * await auditor.initialize();
   * const report = await auditor.audit();
   *
   * console.log(`Score: ${report.score.overall}/100 (${report.score.grade})`);
   * console.log(`Critical issues: ${report.summary.bySeverity.critical}`);
   * console.log(`Total violations: ${report.summary.totalFindings}`);
   * ```
   *
   * @example Audit specific component
   * ```typescript
   * const auditor = new AccessibilityAuditor({ wcagLevel: 'AAA' });
   * await auditor.initialize();
   *
   * // Audit by element reference
   * const nav = document.querySelector('nav');
   * const navReport = await auditor.audit(nav);
   *
   * // Audit by CSS selector
   * const formReport = await auditor.audit('#checkout-form');
   *
   * // Audit with custom axe options
   * const report = await auditor.audit(document, {
   *   rules: { 'color-contrast': { enabled: true } },
   *   reporter: 'v2'
   * });
   * ```
   *
   * @example Access findings and recommendations
   * ```typescript
   * const report = await auditor.audit();
   *
   * // Iterate through violations
   * report.findings.forEach(finding => {
   *   console.log(`${finding.severity}: ${finding.title}`);
   *   console.log(`Fix: ${finding.remediation.how}`);
   *   console.log(`WCAG: ${finding.wcag.level} ${finding.wcag.criterion}`);
   * });
   *
   * // Get prioritized recommendations
   * console.log('Immediate actions:', report.recommendations.immediate);
   * console.log('Short-term fixes:', report.recommendations.shortTerm);
   * console.log('Long-term improvements:', report.recommendations.longTerm);
   * ```
   *
   * @public
   */
  public async audit(
    context?: Element | Document | string,
    options: RunOptions = {}
  ): Promise<AccessibilityReport> {
    if (!this.isInitialized) {
      await this.initialize();
    }

    if (!this.axeCore) {
      throw new Error("axe-core failed to load");
    }

    // Build default options based on config
    const wcagTags = this.getWCAGTags(this.config.wcagLevel || "AA");
    const defaultOptions: RunOptions = {
      runOnly: {
        type: "tag",
        values: [...wcagTags, "best-practice"],
      },
      resultTypes: ["violations", "incomplete"],
      rules: this.buildRulesConfig(),
      ...options,
    };

    try {
      const axeResults = await this.axeCore.run(
        context || document,
        defaultOptions
      );
      return this.buildComprehensiveReport(axeResults);
    } catch (error) {
      console.error("Accessibility audit failed:", error);
      throw error;
    }
  }

  /**
   * Run legacy audit (backward compatible)
   * @deprecated Use audit() instead
   */
  public async legacyAudit(
    context?: Element | Document | string,
    options: RunOptions = {}
  ): Promise<LegacyAccessibilityReport> {
    if (!this.isInitialized) {
      await this.initialize();
    }

    if (!this.axeCore) {
      throw new Error("axe-core failed to load");
    }

    const results = await this.axeCore.run(context || document, options);

    return {
      violations: this.convertToLegacyViolations(results.violations),
      passes: results.passes,
      incomplete: results.incomplete,
      inapplicable: results.inapplicable,
      timestamp: results.timestamp,
      url: results.url,
    };
  }

  /**
   * Export accessibility report to various formats
   *
   * Converts the AccessibilityReport to different output formats suitable for
   * different use cases: viewing (HTML), CI/CD integration (SARIF), data analysis (CSV/JSON),
   * or documentation (Markdown).
   *
   * @param report - The accessibility report to export
   * @param format - Output format:
   *   - `json`: Machine-readable JSON with full report data
   *   - `html`: Interactive HTML report with styling and JavaScript
   *   - `sarif`: Static Analysis Results Interchange Format for CI/CD tools
   *   - `csv`: Comma-separated values for spreadsheet analysis
   *   - `xml`: XML format for legacy systems integration
   *   - `markdown`: Human-readable markdown for documentation
   *
   * @returns Formatted string output ready to save or display
   *
   * @throws {Error} If format is not supported
   *
   * @example Export for different purposes
   * ```typescript
   * const report = await auditor.audit();
   *
   * // HTML for viewing in browser
   * const html = auditor.exportReport(report, 'html');
   * document.body.innerHTML = html;
   *
   * // SARIF for GitHub Code Scanning
   * const sarif = auditor.exportReport(report, 'sarif');
   * fs.writeFileSync('.github/sarif/accessibility.sarif', sarif);
   *
   * // JSON for custom processing
   * const json = auditor.exportReport(report, 'json');
   * const data = JSON.parse(json);
   *
   * // CSV for Excel analysis
   * const csv = auditor.exportReport(report, 'csv');
   *
   * // Markdown for documentation
   * const md = auditor.exportReport(report, 'markdown');
   * fs.writeFileSync('docs/accessibility-report.md', md);
   * ```
   *
   * @public
   */
  public exportReport(
    report: AccessibilityReport,
    format: OutputFormat = "json"
  ): string {
    const formatter = OutputFormatterFactory.create(format);
    return formatter.format(report);
  }

  /**
   * Save report to file
   * In browser: triggers download
   * In Node.js: saves to filesystem
   *
   * @param report The accessibility report to save
   * @param filename Base filename (extension will be added based on format)
   * @param format Output format
   *
   * @example
   * ```typescript
   * await auditor.saveReport(report, 'audit-2024-10-30', 'html');
   * ```
   */
  public async saveReport(
    report: AccessibilityReport,
    filename: string,
    format: OutputFormat = "json"
  ): Promise<void> {
    const content = this.exportReport(report, format);
    const formatter = OutputFormatterFactory.create(format);
    const extension = formatter.getFileExtension();
    const fullFilename = filename.endsWith(`.${extension}`)
      ? filename
      : `${filename}.${extension}`;

    if (typeof window !== "undefined" && typeof document !== "undefined") {
      // Browser: trigger download
      const blob = new Blob([content], {
        type: formatter.getMimeType(),
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = fullFilename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } else {
      // Node.js: save to file
      const fs = await import("fs/promises");
      await fs.writeFile(fullFilename, content, "utf-8");
    }
  }

  /**
   * Compare two reports and analyze trends
   * @param current Current report
   * @param baseline Baseline report to compare against
   * @returns Comparison result with trend analysis
   *
   * @example
   * ```typescript
   * const comparison = auditor.compareReports(currentReport, baselineReport);
   * console.log(`Trend: ${comparison.trend.direction}`);
   * console.log(`Change: ${comparison.trend.change}%`);
   * ```
   */
  public compareReports(
    current: AccessibilityReport,
    baseline: AccessibilityReport
  ): ComparisonResult {
    const scoreChange = current.score.overall - baseline.score.overall;
    const percentChange = (scoreChange / baseline.score.overall) * 100;

    let direction: "improving" | "stable" | "degrading";
    if (Math.abs(scoreChange) < 2) {
      direction = "stable";
    } else if (scoreChange > 0) {
      direction = "improving";
    } else {
      direction = "degrading";
    }

    // Find new findings (in current but not baseline)
    const baselineIds = new Set(
      baseline.findings.map((f) => f.id + f.location.selector)
    );
    const newFindings = current.findings.filter(
      (f) => !baselineIds.has(f.id + f.location.selector)
    );

    // Find resolved findings (in baseline but not current)
    const currentIds = new Set(
      current.findings.map((f) => f.id + f.location.selector)
    );
    const resolvedFindings = baseline.findings.filter(
      (f) => !currentIds.has(f.id + f.location.selector)
    );

    return {
      current: current.score,
      previous: baseline.score,
      trend: {
        direction,
        change: percentChange,
        scoreChange,
        previousScore: baseline.score.overall,
      },
      newIssues: newFindings.length,
      resolvedIssues: resolvedFindings.length,
      details: {
        newFindings,
        resolvedFindings,
      },
    };
  }

  /**
   * Generate human-readable console report
   * @param report The accessibility report
   * @returns Formatted text report
   *
   * @example
   * ```typescript
   * const textReport = auditor.generateReport(report);
   * console.log(textReport);
   * ```
   */
  public generateReport(report: AccessibilityReport): string {
    const { score, summary, findings, target } = report;

    let output = "";

    // Header with box drawing
    output +=
      "╔═══════════════════════════════════════════════════════════════╗\n";
    output +=
      "║           ACCESSIBILITY AUDIT REPORT                          ║\n";
    output +=
      "╚═══════════════════════════════════════════════════════════════╝\n\n";

    // Target information
    output += `📄 Page: ${target.title}\n`;
    output += `🔗 URL: ${target.url}\n`;
    output += `📅 Date: ${new Date(
      report.metadata.timestamp
    ).toLocaleString()}\n\n`;

    // Score section
    output +=
      "┌─────────────────────────────────────────────────────────────┐\n";
    output += `│ Overall Score: ${score.overall}/100 (${
      score.grade
    })${" ".repeat(
      Math.max(0, 30 - score.overall.toString().length - score.grade.length)
    )}│\n`;
    output += `│ Compliance: ${score.complianceLevel}${" ".repeat(
      Math.max(0, 46 - score.complianceLevel.length)
    )}│\n`;
    output +=
      "└─────────────────────────────────────────────────────────────┘\n\n";

    // Dimension breakdown
    output += "📊 Dimension Scores:\n";
    Object.entries(score.dimensions).forEach(([name, dim]) => {
      const progressBar = this.createProgressBar(dim.score, 30);
      output += `  ${name.padEnd(25)} ${progressBar} ${dim.score}/100 (${
        dim.grade
      })\n`;
    });
    output += "\n";

    // Summary statistics
    output += "📈 Summary:\n";
    output += `  Total Findings: ${summary.totalFindings}\n`;
    output += `  Critical: ${summary.bySeverity.critical} | `;
    output += `Serious: ${summary.bySeverity.serious} | `;
    output += `Moderate: ${summary.bySeverity.moderate} | `;
    output += `Minor: ${summary.bySeverity.minor}\n`;
    output += `  Elements Analyzed: ${summary.elementsAnalyzed}\n`;
    output += `  Elements with Issues: ${summary.elementsWithIssues}\n\n`;

    if (findings.length === 0) {
      output += "✅ No accessibility issues found! 🎉\n";
      return output;
    }

    // Top issues by severity
    output += "🔍 Top Issues:\n\n";

    const bySeverity = {
      critical: findings.filter((f) => f.severity === "critical"),
      serious: findings.filter((f) => f.severity === "serious"),
      moderate: findings.filter((f) => f.severity === "moderate"),
      minor: findings.filter((f) => f.severity === "minor"),
    };

    ["critical", "serious", "moderate", "minor"].forEach((severity) => {
      const issues = bySeverity[severity as ViolationSeverity];
      if (issues.length === 0) return;

      const emoji = {
        critical: "🔴",
        serious: "🟠",
        moderate: "🟡",
        minor: "🔵",
      }[severity];

      output += `${emoji} ${severity.toUpperCase()} (${issues.length}):\n`;

      issues.slice(0, 3).forEach((finding, idx) => {
        output += `  ${idx + 1}. ${finding.help}\n`;
        output += `     Location: ${finding.location.selector}\n`;
        output += `     Fix: ${finding.remediation.description}\n\n`;
      });

      if (issues.length > 3) {
        output += `  ... and ${issues.length - 3} more ${severity} issues\n\n`;
      }
    });

    // Recommendations
    output += "💡 Recommendations:\n\n";
    if (report.recommendations.immediate.length > 0) {
      output += "  ⚡ Immediate:\n";
      report.recommendations.immediate.forEach((rec) => {
        output += `    • ${rec}\n`;
      });
    }
    if (report.recommendations.shortTerm.length > 0) {
      output += "\n  📅 Short-term:\n";
      report.recommendations.shortTerm.forEach((rec) => {
        output += `    • ${rec}\n`;
      });
    }

    return output;
  }

  /**
   * Create a visual progress bar
   */
  private createProgressBar(value: number, width: number = 20): string {
    const filled = Math.round((value / 100) * width);
    const empty = width - filled;
    return `[${"█".repeat(filled)}${" ".repeat(empty)}]`;
  }

  /**
   * Build a comprehensive AccessibilityReport from raw axe-core results.
   *
   * This function transforms axe results into the library's canonical
   * AccessibilityReport shape by converting violations into Findings,
   * computing dimensional and overall scores via the scorer, assembling
   * summary statistics, recommendations, and metadata.
   *
   * @param axeResults Raw results returned by axe-core.run()
   * @returns AccessibilityReport ready for export or further analysis
   * @internal
   */
  private buildComprehensiveReport(
    axeResults: AxeResults
  ): AccessibilityReport {
    const findings = this.convertViolationsToFindings(axeResults.violations);
    const elementsAnalyzed = this.countAnalyzedElements(axeResults);

    // Calculate score using AccessibilityScorer
    const score = this.scorer.calculateScore(
      findings,
      this.config.wcagLevel || "AA",
      { elementsAnalyzed }
    );

    // Build summary
    const summary = this.buildSummary(findings, axeResults, elementsAnalyzed);

    // Generate recommendations
    const recommendations = this.generateRecommendations(findings, score);

    // Build metadata
    const metadata = this.buildMetadata();

    return {
      metadata,
      score,
      summary,
      findings,
      target: {
        url: axeResults.url,
        title: typeof document !== "undefined" ? document.title : "Unknown",
      },
      recommendations,
    };
  }

  /**
   * Convert axe-core violations into the library's Finding objects.
   *
   * Each node inside a violation becomes one Finding so that fixes can be
   * tracked at element granularity. The function extracts selector, snippet,
   * WCAG metadata, remediation guidance and affected user groups.
   *
   * @param violations Array of axe-core violation objects
   * @returns Array of normalized Finding objects
   * @internal
   */
  private convertViolationsToFindings(violations: AxeResult[]): Finding[] {
    const findings: Finding[] = [];

    violations.forEach((violation) => {
      violation.nodes.forEach((node, nodeIndex) => {
        const selector = Array.isArray(node.target[0])
          ? node.target[0].join(" ")
          : String(node.target[0]);

        findings.push({
          id: `${violation.id}-${nodeIndex}`,
          type: "violation",
          severity: (violation.impact || "minor") as ViolationSeverity,
          impact: (violation.impact || "minor") as ViolationSeverity,
          description: violation.description,
          help: violation.help,
          helpUrl: violation.helpUrl,
          location: {
            selector,
            xpath: selector,
            snippet: node.html,
            url: typeof window !== "undefined" ? window.location.href : "",
          },
          wcag: this.extractWCAGInfo(violation.tags),
          remediation: this.buildRemediationGuidance(violation, node),
          relatedFindings: [],
          affectedUsers: this.determineAffectedUsers(
            violation.id,
            violation.impact || "minor"
          ),
          tags: violation.tags,
          testResult: {
            ruleId: violation.id,
            toolName: "axe-core",
            toolVersion: "4.9.1",
          },
        });
      });
    });

    return findings;
  }

  /**
   * Convert axe-core violations to the legacy `AccessibilityViolation` shape.
   *
   * This helper is provided for backward compatibility with older consumers
   * that expect the legacy format. Prefer `Finding`/`AccessibilityReport`.
   *
   * @param violations axe-core violation objects
   * @returns Array of legacy AccessibilityViolation objects
   * @deprecated Prefer `convertViolationsToFindings` and the new report model
   */
  private convertToLegacyViolations(
    violations: AxeResult[]
  ): AccessibilityViolation[] {
    return violations.map((v) => ({
      id: v.id,
      impact: v.impact || null,
      description: v.description,
      help: v.help,
      helpUrl: v.helpUrl,
      html: v.nodes[0]?.html || "",
      tags: v.tags,
      nodes: v.nodes.map((node) => ({
        html: node.html,
        target: Array.isArray(node.target[0])
          ? node.target[0]
          : [String(node.target[0])],
        failureSummary: node.failureSummary || "",
        element: null,
      })),
    }));
  }

  /**
   * Parse WCAG-related information out of axe-core rule tags.
   *
   * The function infers the WCAG level (A/AA/AAA), extracts a principal
   * criterion number if present, and maps tags to a human-friendly
   * WCAGClassification.
   *
   * @param tags Array of axe rule tags (e.g., ['wcag21aa', 'cat.color'])
   * @returns WCAGClassification including level, criterion, principle, guideline
   * @internal
   */
  private extractWCAGInfo(tags: string[]): WCAGClassification {
    let level: WCAGLevel = "A";
    const criteria: string[] = [];

    tags.forEach((tag) => {
      if (tag.includes("wcag2aaa") || tag.includes("wcag21aaa")) {
        level = "AAA";
      } else if (tag.includes("wcag2aa") || tag.includes("wcag21aa")) {
        level = level === "AAA" ? "AAA" : "AA";
      }

      // Extract criterion numbers like "143" from "wcag143"
      const match = tag.match(/wcag(\d+)/);
      if (match && match[1].length >= 3) {
        const num = match[1];
        const formatted = `${num[0]}.${num[1]}.${num.substring(2)}`;
        if (!criteria.includes(formatted)) {
          criteria.push(formatted);
        }
      }
    });

    const principle = this.determinePrinciple(tags);

    return {
      level,
      criterion: criteria[0] || "1.1.1",
      principle,
      guideline: criteria[0]?.split(".").slice(0, 2).join(".") || "1.1",
    };
  }

  /**
   * Determine the WCAG principle (perceivable / operable / understandable / robust)
   * from axe-tags present on a rule.
   *
   * Used to categorize findings for guidance and recommendations.
   * @internal
   */
  private determinePrinciple(
    tags: string[]
  ): "perceivable" | "operable" | "understandable" | "robust" {
    if (
      tags.some((t) => t.includes("cat.keyboard") || t.includes("cat.time"))
    ) {
      return "operable";
    }
    if (
      tags.some((t) => t.includes("cat.language") || t.includes("cat.sensory"))
    ) {
      return "understandable";
    }
    if (
      tags.some(
        (t) => t.includes("cat.parsing") || t.includes("cat.name-role-value")
      )
    ) {
      return "robust";
    }
    return "perceivable";
  }

  /**
   * Build human-friendly remediation guidance for a given violation/node.
   *
   * The guidance includes a short description, step-by-step remediation
   * tasks, a small code example when applicable, an estimate of effort,
   * priority, and helpful resources.
   *
   * @param violation axe-core violation object
   * @param node Specific node instance from the violation
   * @returns RemediationGuidance for the finding
   * @internal
   */
  private buildRemediationGuidance(
    violation: AxeResult,
    node: NodeResult
  ): RemediationGuidance {
    const steps = this.generateRemediationSteps(violation.id);
    const codeExample = this.generateCodeExample(violation.id, node.html);

    return {
      description: violation.help,
      steps,
      codeExample,
      automatable: this.isAutomatable(violation.id),
      estimatedEffort: {
        hours: this.estimateHours(violation.id),
        complexity: this.estimateComplexity(violation.id),
      },
      priority: this.mapSeverityToPriority(violation.impact || "minor"),
      resources: [violation.helpUrl],
    };
  }

  /**
   * Generate pragmatic remediation steps for common rule identifiers.
   *
   * This is a best-effort mapping used to populate guidance; teams
   * should customize these steps to match their codebase and standards.
   *
   * @param ruleId axe rule id (e.g., 'color-contrast')
   * @returns Array of step strings in recommended order
   * @internal
   */
  private generateRemediationSteps(ruleId: string): string[] {
    const stepMap: Record<string, string[]> = {
      "color-contrast": [
        "Check the color contrast ratio using a contrast checker tool",
        "Adjust foreground or background colors to meet WCAG 2.1 AA (4.5:1 for normal text)",
        "Test with users who have low vision",
        "Document the color choices in your design system",
      ],
      "button-name": [
        "Add descriptive text inside the button element",
        "Or add an aria-label attribute with descriptive text",
        "Ensure the label describes the button's action",
        "Test with screen readers",
      ],
      "image-alt": [
        "Add an alt attribute to the image",
        "Write descriptive alternative text that conveys the image's purpose",
        'For decorative images, use alt=""',
        "Test with screen readers",
      ],
    };

    return (
      stepMap[ruleId] || [
        "Review the WCAG documentation for this rule",
        "Identify all affected elements",
        "Apply the recommended fix",
        "Test with assistive technologies",
        "Verify the fix resolves the issue",
      ]
    );
  }

  /**
   * Provide a small code example demonstrating a typical fix for a rule.
   *
   * If a known pattern exists for the ruleId, a canonical snippet is returned.
   * Otherwise the original HTML is included as a commented suggestion.
   *
   * @param ruleId axe rule id
   * @param originalHtml The HTML snippet reported by axe
   * @returns A short string containing example code
   * @internal
   */
  private generateCodeExample(ruleId: string, originalHtml: string): string {
    const exampleMap: Record<string, string> = {
      "button-name": '<button aria-label="Submit form">Submit</button>',
      "image-alt": '<img src="logo.png" alt="Company Logo" />',
      "color-contrast":
        "/* Use colors with sufficient contrast ratio (4.5:1 minimum) */\ncolor: #000000;\nbackground-color: #FFFFFF;",
    };

    return exampleMap[ruleId] || `<!-- Fixed version of:\n${originalHtml}\n-->`;
  }

  /**
   * Determine whether a rule can be automatically fixed by scripts or tools.
   *
   * Returns `true` for rules that are straightforward to correct programmatically
   * (for example missing lang attributes or duplicate ids) and `false` otherwise.
   *
   * @param ruleId axe rule id
   * @returns boolean indicating automatable status
   * @internal
   */
  private isAutomatable(ruleId: string): boolean {
    const automatable = ["duplicate-id", "html-has-lang", "valid-lang"];
    return automatable.includes(ruleId);
  }

  /**
   * Provide a rough estimate of developer hours required to fix a violation.
   *
   * These are coarse heuristics intended for planning and triage only.
   *
   * @param ruleId axe rule id
   * @returns Estimated hours (fractional values allowed)
   * @internal
   */
  private estimateHours(ruleId: string): number {
    const highEffort = ["color-contrast"];
    const mediumEffort = ["heading-order", "landmark"];

    if (highEffort.includes(ruleId)) return 2;
    if (mediumEffort.includes(ruleId)) return 1;
    return 0.5;
  }

  /**
   * Map rule to a qualitative complexity label.
   *
   * Used alongside estimated hours to aid prioritization.
   *
   * @param ruleId axe rule id
   * @returns 'low' | 'medium' | 'high'
   * @internal
   */
  private estimateComplexity(ruleId: string): "low" | "medium" | "high" {
    const high = ["color-contrast", "heading-order"];
    const medium = ["button-name", "link-name", "label"];

    if (high.includes(ruleId)) return "high";
    if (medium.includes(ruleId)) return "medium";
    return "low";
  }

  /**
   * Convert a textual severity into a numeric priority used for sorting.
   * Lower numbers indicate higher priority.
   *
   * @param severity textual severity (critical/serious/moderate/minor)
   * @returns numeric priority
   * @internal
   */
  private mapSeverityToPriority(severity: string): number {
    const map: Record<string, number> = {
      critical: 1,
      serious: 2,
      moderate: 3,
      minor: 4,
    };
    return map[severity] || 4;
  }

  /**
   * Determine which user groups are most impacted by a given rule.
   *
   * Returns a best-effort list (e.g., ['blind', 'low-vision']) to help teams
   * prioritize fixes that have greater human impact.
   *
   * @param ruleId axe rule id
   * @param severity severity of finding
   * @returns Array of affected user group identifiers
   * @internal
   */
  private determineAffectedUsers(
    ruleId: string,
    _severity: string
  ): AffectedUserGroup[] {
    const userMap: Record<string, AffectedUserGroup[]> = {
      "color-contrast": ["low-vision", "color-blind"],
      "image-alt": ["blind"],
      "button-name": ["blind"],
      "link-name": ["blind"],
      label: ["blind"],
      "video-caption": ["deaf", "hard-of-hearing"],
      "audio-caption": ["deaf", "hard-of-hearing"],
    };

    return userMap[ruleId] || ["blind"];
  }

  /**
   * Build aggregated summary statistics for the report.
   *
   * Summary includes counts by severity and type, coverage metrics and a
   * simple duration placeholder. The duration can be set by callers if they
   * measure analysis time externally.
   *
   * @param findings normalized Finding objects
   * @param axeResults raw axe results (used for incomplete counts)
   * @param elementsAnalyzed estimated number of elements analyzed
   * @returns AuditSummary object
   * @internal
   */
  private buildSummary(
    findings: Finding[],
    axeResults: AxeResults,
    elementsAnalyzed: number
  ): AuditSummary {
    const elementsWithIssues = new Set(findings.map((f) => f.location.selector))
      .size;

    return {
      totalFindings: findings.length,
      byType: {
        violations: findings.filter((f) => f.type === "violation").length,
        warnings: findings.filter((f) => f.type === "warning").length,
        suggestions: findings.filter((f) => f.type === "suggestion").length,
        incomplete: axeResults.incomplete?.length || 0,
      },
      bySeverity: {
        critical: findings.filter((f) => f.severity === "critical").length,
        serious: findings.filter((f) => f.severity === "serious").length,
        moderate: findings.filter((f) => f.severity === "moderate").length,
        minor: findings.filter((f) => f.severity === "minor").length,
      },
      elementsAnalyzed,
      elementsWithIssues,
      coverage:
        elementsAnalyzed > 0
          ? (elementsWithIssues / elementsAnalyzed) * 100
          : 0,
      duration: 0, // Would be calculated from start time
    };
  }

  /**
   * Generate prioritized recommendations for the report based on findings and score.
   *
   * - Immediate: critical/serious issues that should be fixed right away
   * - Short-term: moderate issues and low-hanging dimension improvements
   * - Long-term: strategic items like moving toward AAA or CI integration
   *
   * @param findings normalized Finding objects
   * @param score computed accessibility score
   * @returns object containing immediate, shortTerm and longTerm recommendation arrays
   * @internal
   */
  private generateRecommendations(
    findings: Finding[],
    score: AccessibilityScore
  ): {
    immediate: string[];
    shortTerm: string[];
    longTerm: string[];
  } {
    const immediate: string[] = [];
    const shortTerm: string[] = [];
    const longTerm: string[] = [];

    // Immediate: Critical and serious issues
    const critical = findings.filter((f) => f.severity === "critical");
    const serious = findings.filter((f) => f.severity === "serious");

    if (critical.length > 0) {
      immediate.push(
        `Fix ${critical.length} critical accessibility blocker(s) immediately`
      );
      critical.slice(0, 3).forEach((f) => {
        immediate.push(`- ${f.help}`);
      });
    }

    if (serious.length > 0) {
      immediate.push(
        `Address ${serious.length} serious accessibility issue(s)`
      );
    }

    // Short-term: Moderate issues and dimension improvements
    const moderate = findings.filter((f) => f.severity === "moderate");
    if (moderate.length > 0) {
      shortTerm.push(
        `Resolve ${moderate.length} moderate accessibility issue(s)`
      );
    }

    Object.entries(score.dimensions).forEach(([name, dim]) => {
      if (dim.score < 80) {
        shortTerm.push(`Improve ${name} (currently ${dim.score}/100)`);
      }
    });

    // Long-term: AAA compliance, best practices
    if (score.complianceLevel !== "WCAG AAA") {
      longTerm.push(
        "Work towards WCAG AAA compliance for enhanced accessibility"
      );
    }
    longTerm.push("Integrate accessibility testing into CI/CD pipeline");
    longTerm.push("Conduct user testing with people with disabilities");
    longTerm.push("Create an accessibility statement for your site");

    return { immediate, shortTerm, longTerm };
  }

  /**
   * Build audit metadata describing the environment and configuration used.
   *
   * This metadata is included in the AccessibilityReport and is helpful
   * when comparing reports across environments or tool versions.
   *
   * @returns AuditMetadata object
   * @internal
   */
  private buildMetadata(): AuditMetadata {
    return {
      id: `audit-${Date.now()}`,
      timestamp: new Date().toISOString(),
      toolVersion: "0.1.0",
      config: {
        targets: [],
        viewports: [],
        auth: { type: "none" },
        analysis: {
          static: true,
          dynamic: false,
          runtime: false,
          performance: false,
          designSystem: false,
          reactSpecific: false,
        },
        output: {
          formats: ["json"],
          directory: "",
          screenshots: false,
          codeSnippets: true,
          verbosity: "normal",
        },
        timeout: { page: 30000, analysis: 60000, total: 300000 },
        concurrency: { maxPages: 1, maxAnalyzers: 4 },
      },
      environment: {
        os: typeof process !== "undefined" ? process.platform : "browser",
        browser:
          typeof navigator !== "undefined" ? navigator.userAgent : "unknown",
        browserVersion: "unknown",
        nodeVersion: typeof process !== "undefined" ? process.version : "N/A",
      },
    };
  }

  /**
   * Estimate the number of analyzed elements from axe results.
   *
   * This function uses a heuristic combining passes, violations and other
   * result arrays. It intentionally errs on the side of simplicity.
   *
   * @param axeResults raw axe-core results
   * @returns approximate number of analyzed elements
   * @internal
   */
  private countAnalyzedElements(axeResults: AxeResults): number {
    // Rough estimate based on passes + violations + incomplete
    return (
      axeResults.passes.length +
      axeResults.violations.length +
      axeResults.incomplete.length +
      axeResults.inapplicable.length
    );
  }

  /**
   * Map a WCAG level ('A'|'AA'|'AAA') to the set of axe tags to run.
   *
   * @param level WCAGLevel to target
   * @returns array of axe tag strings
   * @internal
   */
  private getWCAGTags(level: WCAGLevel): string[] {
    const tags = ["wcag2a", "wcag21a"];
    if (level === "AA" || level === "AAA") {
      tags.push("wcag2aa", "wcag21aa");
    }
    if (level === "AAA") {
      tags.push("wcag2aaa", "wcag21aaa");
    }
    return tags;
  }

  /**
   * Assemble the rules configuration object passed to axe-core.run().
   *
   * This function respects `includeRules` and `excludeRules` from the
   * auditor configuration so callers can easily override per-project
   * rule sets.
   *
   * @returns rules configuration map
   * @internal
   */
  private buildRulesConfig(): Record<string, { enabled: boolean }> {
    const rules: Record<string, { enabled: boolean }> = {
      "color-contrast": { enabled: true },
      "duplicate-id": { enabled: true },
      "empty-heading": { enabled: true },
      "heading-order": { enabled: true },
      "image-alt": { enabled: true },
      "label-title-only": { enabled: true },
      "link-name": { enabled: true },
      region: { enabled: true },
    };

    // Apply includes/excludes from config
    if (this.config.includeRules) {
      this.config.includeRules.forEach((rule) => {
        rules[rule] = { enabled: true };
      });
    }

    if (this.config.excludeRules) {
      this.config.excludeRules.forEach((rule) => {
        rules[rule] = { enabled: false };
      });
    }

    return rules;
  }

  /**
   * Helper to infer WCAG level from a tags array.
   * @private
   */
  private extractWCAGLevel(tags: string[]): WCAGLevel {
    if (tags.some((t) => t.includes("wcag2aaa") || t.includes("wcag21aaa")))
      return "AAA";
    if (tags.some((t) => t.includes("wcag2aa") || t.includes("wcag21aa")))
      return "AA";
    return "A";
  }

  /**
   * Helper to categorize a violation into a high-level type (visual/keyboard/semantic/etc.).
   * @private
   */
  private determineViolationType(tags: string[]): string {
    if (tags.some((t) => t.includes("color"))) return "visual";
    if (tags.some((t) => t.includes("keyboard"))) return "keyboard";
    if (tags.some((t) => t.includes("structure"))) return "semantic";
    if (tags.some((t) => t.includes("forms"))) return "forms";
    if (tags.some((t) => t.includes("aria"))) return "aria";
    return "other";
  }
}
