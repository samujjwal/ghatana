// Core
import { AccessibilityAuditor } from './AccessibilityAuditor';

import type { AccessibilityReport, WCAGLevel, OutputFormat } from './types';

// React Components
export { AccessibilityAuditTool } from './AccessibilityAuditTool';
export { AccessibilityReportViewer } from './AccessibilityReportViewer';

// Hooks
export { useAccessibilityAudit } from './useAccessibilityAudit';

// Types - export all comprehensive types
export type {
  AccessibilityReport,
  AccessibilityScore,
  AccessibilityGrade,
  ComplianceLevel,
  DimensionScore,
  Finding,
  FindingType,
  FindingLocation,
  ViolationSeverity,
  WCAGLevel,
  WCAGClassification,
  RemediationGuidance,
  AuditSummary,
  AuditMetadata,
  OutputFormat,
  AffectedUserGroup,
  Recommendation,
  ComparisonResult,
  SARIFOutput,
} from './types';

// Deprecated types for backward compatibility
export type { AccessibilityViolation } from './AccessibilityAuditor';

// Scoring
export { AccessibilityScorer } from './scoring/AccessibilityScorer';

// Formatters
export {
  OutputFormatterFactory,
  IOutputFormatter,
  JSONFormatter,
  HTMLFormatter,
  CSVFormatter,
  SARIFFormatter,
  XMLFormatter,
  MarkdownFormatter,
} from './formatters';

// Main export
export default AccessibilityAuditor;

// Version and constants
export const VERSION = '0.1.0';

export const WCAG_LEVELS = ['A', 'AA', 'AAA'] as const;

export const SEVERITY_LEVELS = ['critical', 'serious', 'moderate', 'minor'] as const;

export const OUTPUT_FORMATS = ['json', 'html', 'csv', 'sarif', 'xml', 'markdown'] as const;

/**
 * Helper function to run a quick audit
 * 
 * @example
 * ```typescript
 * const report = await runQuickAudit();
 * console.log(`Score: ${report.score.overall}`);
 * ```
 */
export async function runQuickAudit(
  element?: Element | Document | string,
  wcagLevel: WCAGLevel = 'AA'
): Promise<AccessibilityReport> {
  const auditor = new AccessibilityAuditor({ wcagLevel });
  await auditor.initialize();
  return auditor.audit(element);
}

/**
 * Helper function to run audit and export in specific format
 * 
 * @example
 * ```typescript
 * const html = await runAuditWithFormat('html');
 * ```
 */
export async function runAuditWithFormat(
  format: OutputFormat = 'json',
  element?: Element | Document | string,
  wcagLevel: WCAGLevel = 'AA'
): Promise<string> {
  const report = await runQuickAudit(element, wcagLevel);
  const auditor = new AccessibilityAuditor({ wcagLevel });
  return auditor.exportReport(report, format);
}

// API helpers
export { auditTarget, auditTargetToFormat } from './api/runAuditOn';
