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

import { AxeResults, RunOptions, Spec, Result as AxeResult } from 'axe-core';
import type { ReactElement } from 'react';
import type {
  AccessibilityReport,
  Finding,
  ViolationSeverity,
  WCAGLevel,
  WCAGClassification,
  AuditSummary,
  FindingLocation,
  RemediationGuidance,
  AuditConfig,
  ReportMetadata,
  AuditTarget,
  Recommendation,
  ComparisonResult,
  OutputFormat,
  FindingType,
  AffectedUserGroup,
} from './types';
import { AccessibilityScorer } from './scoring/AccessibilityScorer';
import { OutputFormatterFactory } from './formatters';

/**
 * Legacy violation format for backward compatibility
 * @deprecated Use Finding from ./types instead
 */
export interface AccessibilityViolation {
  id: string;
  impact: 'minor' | 'moderate' | 'serious' | 'critical' | null;
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
  timestamp: string;
  url: string;
  pageTitle: string;
  summary: {
    totalViolations: number;
    critical: number;
    serious: number;
    moderate: number;
    minor: number;
  };
}

/**
 * Main accessibility auditor class
 * 
 * Provides comprehensive accessibility testing for:
 * - Individual components
 * - Full pages
 * - Design systems
 * - UI patterns
 * 
 * Features:
 * - WCAG 2.1 compliance checking
 * - 8-dimension scoring system
 * - Multiple output formats (JSON, HTML, CSV, SARIF, XML, Markdown)
 * - Actionable remediation guidance
 * - Historical trend analysis
 */
export class AccessibilityAuditor {
  private axeCore: typeof import('axe-core') | null = null;
  private isInitialized = false;
  private config: AuditConfig;
  private scorer: AccessibilityScorer;

  private axeCore: typeof import('axe-core') | null = null;
  private isInitialized = false;
  private config: AuditConfig;
  private scorer: AccessibilityScorer;

  /**
   * Create a new accessibility auditor
   * @param config Optional configuration for the auditor
   * 
   * @example
   * ```typescript
   * const auditor = new AccessibilityAuditor({
   *   wcagLevel: 'AA',
   *   mode: { static: true, dynamic: false },
   *   thresholds: { minScore: 80 }
   * });
   * ```
   */
  constructor(config?: Partial<AuditConfig>) {
    this.config = this.mergeConfig(config);
    this.scorer = AccessibilityScorer.getInstance();
  }

  /**
   * Merge provided config with defaults
   */
  private mergeConfig(config?: Partial<AuditConfig>): AuditConfig {
    const defaults: AuditConfig = {
      wcagLevel: 'AA',
      mode: {
        static: true,
        dynamic: false,
        runtime: false,
        performance: false,
        designSystem: false,
        reactSpecific: false,
      },
      output: {
        formats: ['json'],
        directory: './accessibility-reports',
        screenshots: false,
        codeSnippets: true,
        verbosity: 'normal',
      },
      thresholds: {
        minScore: 80,
        maxViolations: {
          critical: 0,
          serious: 5,
          moderate: 10,
          minor: 20,
        },
        allowScoreDecrease: 5,
        failOnNewViolations: {
          critical: true,
          serious: true,
          moderate: false,
          minor: false,
        },
      },
    };

    return {
      ...defaults,
      ...config,
      mode: { ...defaults.mode, ...config?.mode },
      output: { ...defaults.output, ...config?.output },
      thresholds: {
        ...defaults.thresholds,
        ...config?.thresholds,
        maxViolations: {
          ...defaults.thresholds.maxViolations,
          ...config?.thresholds?.maxViolations,
        },
        failOnNewViolations: {
          ...defaults.thresholds.failOnNewViolations,
          ...config?.thresholds?.failOnNewViolations,
        },
      },
    };
  }

  /**
   * Get current configuration
   */
  public getConfig(): AuditConfig {
    return { ...this.config };
  }

  /**
   * Update configuration
   * @param config Partial configuration to merge
   */
  public setConfig(config: Partial<AuditConfig>): void {
    this.config = this.mergeConfig(config);
  }

  /**
   * Get list of supported output formats
   */
  public getSupportedFormats(): OutputFormat[] {
    return ['json', 'html', 'csv', 'sarif', 'xml', 'markdown'];
  }

  /**
   * Initialize the accessibility auditor
   * Must be called before running audits
   */
  public async initialize(): Promise<void> {
    if (this.isInitialized) return;
    
    // Dynamically import axe-core to avoid loading it in the main bundle
    this.axeCore = (await import('axe-core')).default;
    
    // Configure axe-core with additional rules and options
    this.axeCore.configure({
      rules: [
        { id: 'landmark-one-main', enabled: true },
        { id: 'page-has-heading-one', enabled: true },
        { id: 'region', enabled: true },
      ],
    } as Spec);
    
    this.isInitialized = true;
  }

  /**
   * Run accessibility audit on the current page or a specific element
   * @param context Optional context to run the audit on (defaults to document)
   * @param options Optional axe-core run options
   */
  public async audit(
    context?: Element | Document | string,
    options: RunOptions = {}
  ): Promise<AccessibilityReport> {
    if (!this.isInitialized) {
      await this.initialize();
    }

    if (!this.axeCore) {
      throw new Error('axe-core failed to load');
    }

    // Default options if none provided
    const defaultOptions: RunOptions = {
      runOnly: {
        type: 'tag',
        values: ['wcag2a', 'wcag2aa', 'best-practice'],
      },
      resultTypes: ['violations', 'incomplete'],
      rules: {
        'color-contrast': { enabled: true },
        'duplicate-id': { enabled: true },
        'empty-heading': { enabled: true },
        'heading-order': { enabled: true },
        'image-alt': { enabled: true },
        'label-title-only': { enabled: true },
        'link-name': { enabled: true },
        'region': { enabled: true },
      },
      ...options,
    };

    try {
      const results = await this.axeCore.run(context || document, defaultOptions);
      return this.formatResults(results);
    } catch (error) {
      console.error('Accessibility audit failed:', error);
      throw error;
    }
  }

  /**
   * Format axe-core results into a more usable format
   */
  private formatResults(results: AxeResults): AccessibilityReport {
    const now = new Date();
    const violations = results.violations.map(violation => ({
      id: violation.id,
      impact: violation.impact as AccessibilityViolation['impact'],
      description: violation.description,
      help: violation.help,
      helpUrl: violation.helpUrl,
      html: violation.html,
      tags: violation.tags,
      nodes: violation.nodes.map(node => ({
        html: node.html,
        target: node.target,
        failureSummary: node.failureSummary || '',
        element: this.getElementFromTarget(node.target),
      })),
    }));

    // Count violations by impact level
    const impactCounts = {
      critical: 0,
      serious: 0,
      moderate: 0,
      minor: 0,
    };

    violations.forEach(violation => {
      if (violation.impact) {
        impactCounts[violation.impact]++;
      }
    });

    return {
      violations,
      timestamp: now.toISOString(),
      url: window.location.href,
      pageTitle: document.title,
      summary: {
        totalViolations: violations.length,
        ...impactCounts,
      },
    };
  }

  /**
   * Get an HTML element from a target selector
   */
  private getElementFromTarget(target: string[]): HTMLElement | null {
    try {
      if (target.length === 0) return null;
      
      // Try to find the element using the first valid selector
      for (const selector of target) {
        try {
          const element = document.querySelector<HTMLElement>(selector);
          if (element) return element;
        } catch (e) {
          console.warn(`Invalid selector: ${selector}`, e);
        }
      }
      return null;
    } catch (error) {
      console.error('Error getting element from target:', error);
      return null;
    }
  }

  /**
   * Get a summary of accessibility issues by category
   */
  public getIssuesByCategory(report: AccessibilityReport): Record<string, AccessibilityViolation[]> {
    const categories: Record<string, AccessibilityViolation[]> = {};
    
    report.violations.forEach(violation => {
      // Use the first tag as the category, or 'other' if no tags
      const category = violation.tags.length > 0 ? violation.tags[0] : 'other';
      
      if (!categories[category]) {
        categories[category] = [];
      }
      
      categories[category].push(violation);
    });
    
    return categories;
  }

  /**
   * Generate a human-readable report
   */
  public generateReport(report: AccessibilityReport): string {
    let output = `Accessibility Audit Report\n`;
    output += `================================\n`;
    output += `URL: ${report.url}\n`;
    output += `Page Title: ${report.pageTitle}\n`;
    output += `Date: ${new Date(report.timestamp).toLocaleString()}\n`;
    output += `\nSummary:\n`;
    output += `- Total Violations: ${report.summary.totalViolations}\n`;
    output += `- Critical: ${report.summary.critical}\n`;
    output += `- Serious: ${report.summary.serious}\n`;
    output += `- Moderate: ${report.summary.moderate}\n`;
    output += `- Minor: ${report.summary.minor}\n\n`;

    if (report.violations.length === 0) {
      output += 'No accessibility violations found! 🎉\n';
      return output;
    }

    // Group by impact
    const byImpact: Record<string, AccessibilityViolation[]> = {
      critical: [],
      serious: [],
      moderate: [],
      minor: [],
    };

    report.violations.forEach(violation => {
      const impact = violation.impact || 'minor';
      byImpact[impact].push(violation);
    });

    // Report by impact level
    for (const [impact, violations] of Object.entries(byImpact)) {
      if (violations.length === 0) continue;
      
      output += `\n${impact.toUpperCase()} ISSUES (${violations.length}):\n`;
      
      violations.forEach((violation, index) => {
        output += `\n${index + 1}. [${violation.id}] ${violation.help}\n`;
        output += `   Description: ${violation.description}\n`;
        output += `   Help: ${violation.helpUrl}\n`;
        
        if (violation.nodes.length > 0) {
          output += `   Found in ${violation.nodes.length} location(s):\n`;
          violation.nodes.slice(0, 3).forEach((node, nodeIndex) => {
            output += `   ${nodeIndex + 1}. ${node.html}\n`;
            if (node.failureSummary) {
              output += `      ${node.failureSummary.replace(/\n/g, '\n      ')}\n`;
            }
          });
          
          if (violation.nodes.length > 3) {
            output += `   ...and ${violation.nodes.length - 3} more locations\n`;
          }
        }
      });
    }

    return output;
  }
}
