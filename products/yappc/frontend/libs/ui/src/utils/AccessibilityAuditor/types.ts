/**
 * Issue types, categories and severity levels for accessibility audits.
 */

/**
 * Represents a single accessibility issue found during an audit.
 *
 * @property id - Unique identifier for this issue
 * @property type - Issue severity level (error/warning/info)
 * @property category - Category of the accessibility issue
 * @property element - The DOM element associated with the issue
 * @property message - Human-readable description of the issue
 * @property suggestion - Recommended fix for the issue
 * @property impact - Business/user impact level
 * @property wcagCriteria - Array of WCAG criteria IDs (e.g., '1.4.3', '2.1.1')
 * @property autoFixable - Whether the issue can be automatically fixed
 * @property documentation - Optional URL to WCAG documentation
 */
export interface AccessibilityIssue {
  id: string;
  type: 'error' | 'warning' | 'info';
  category:
    | 'color-contrast'
    | 'keyboard-navigation'
    | 'screen-reader'
    | 'focus-management'
    | 'semantic-html'
    | 'aria'
    | 'motion';
  element: Element;
  message: string;
  suggestion: string;
  impact: 'critical' | 'serious' | 'moderate' | 'minor';
  wcagCriteria: string[];
  autoFixable: boolean;
  documentation?: string;
}

/**
 * Complete accessibility audit report with metrics and recommendations.
 *
 * @property score - Overall accessibility score (0-100)
 * @property totalIssues - Total number of issues found
 * @property issuesByCategory - Count of issues grouped by category
 * @property issuesByImpact - Count of issues grouped by impact level
 * @property issues - Array of all individual issues
 * @property recommendations - List of high-level improvement recommendations
 * @property timestamp - When the audit was performed
 */
export interface AccessibilityReport {
  score: number;
  totalIssues: number;
  issuesByCategory: Record<string, number>;
  issuesByImpact: Record<string, number>;
  issues: AccessibilityIssue[];
  recommendations: string[];
  timestamp: Date;
}

/**
 * Options for customizing the audit behavior.
 *
 * @property includeHidden - Include elements with display:none or visibility:hidden
 * @property skipAnimations - Skip motion and animation audit
 * @property colorContrastRatio - Minimum color contrast ratio to require (default: 4.5)
 */
export interface AuditOptions {
  includeHidden?: boolean;
  skipAnimations?: boolean;
  colorContrastRatio?: number;
}

/**
 * Result of auto-fix operation.
 *
 * @property fixed - Array of issues that were successfully fixed
 * @property failed - Array of issues that could not be fixed
 */
export interface AutoFixResult {
  fixed: AccessibilityIssue[];
  failed: AccessibilityIssue[];
}
