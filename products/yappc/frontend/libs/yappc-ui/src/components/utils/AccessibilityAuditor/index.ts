/**
 * Comprehensive accessibility audit system.
 *
 * Provides automated detection of accessibility issues across
 * WCAG categories and auto-fix capabilities.
 */

import { AccessibilityAuditors } from './auditors';

import type {
  AccessibilityIssue,
  AccessibilityReport,
  AuditOptions,
  AutoFixResult,
} from './types';

/**
 * Main accessibility auditor class.
 *
 * Orchestrates comprehensive accessibility audits, auto-fixes, and
 * continuous monitoring of DOM changes.
 *
 * @example
 * ```ts
 * const auditor = new AccessibilityAuditor();
 * const report = await auditor.auditPage();
 * console.log(`Accessibility score: ${report.score}`);
 * ```
 */
export class AccessibilityAuditor {
  private observers: MutationObserver[] = [];

  private listeners: Set<(report: AccessibilityReport) => void> = new Set();

  /**
   * Run comprehensive accessibility audit on the page.
   *
   * Performs all category audits (color contrast, keyboard navigation,
   * screen reader, focus management, semantic HTML, ARIA, motion).
   *
   * @param options - Audit configuration options
   * @returns Complete accessibility report with score and recommendations
   *
   * @example
   * ```ts
   * const report = await auditor.auditPage({
   *   includeHidden: false,
   *   colorContrastRatio: 4.5
   * });
   * ```
   */
  async auditPage(options: AuditOptions = {}): Promise<AccessibilityReport> {
    const {
      includeHidden = false,
      skipAnimations = false,
      colorContrastRatio = 4.5,
    } = options;

    const issues: AccessibilityIssue[] = [];
    const rootElement = document.body;

    // Color contrast audit
    issues.push(
      ...(await AccessibilityAuditors.auditColorContrast(
        rootElement,
        colorContrastRatio
      ))
    );

    // Keyboard navigation audit
    issues.push(...AccessibilityAuditors.auditKeyboardNavigation(rootElement));

    // Screen reader audit
    issues.push(
      ...AccessibilityAuditors.auditScreenReader(rootElement, includeHidden)
    );

    // Focus management audit
    issues.push(...AccessibilityAuditors.auditFocusManagement(rootElement));

    // Semantic HTML audit
    issues.push(...AccessibilityAuditors.auditSemanticHTML(rootElement));

    // ARIA audit
    issues.push(...AccessibilityAuditors.auditARIA(rootElement));

    // Motion and animation audit
    if (!skipAnimations) {
      issues.push(...AccessibilityAuditors.auditMotion(rootElement));
    }

    return this.generateReport(issues);
  }

  /**
   * Attempt to auto-fix accessibility issues.
   *
   * Fixes only issues marked as autoFixable. Returns separate lists of
   * successfully fixed and failed issues.
   *
   * @param issues - Array of issues to attempt fixing
   * @returns Result with fixed and failed issue lists
   *
   * @example
   * ```ts
   * const report = await auditor.auditPage();
   * const fixResult = await auditor.autoFix(report.issues);
   * console.log(`Fixed ${fixResult.fixed.length} issues`);
   * ```
   */
  async autoFix(issues: AccessibilityIssue[]): Promise<AutoFixResult> {
    const fixed: AccessibilityIssue[] = [];
    const failed: AccessibilityIssue[] = [];

    for (const issue of issues.filter((i) => i.autoFixable)) {
      try {
        switch (issue.category) {
          case 'focus-management':
            if (issue.message.includes('focus indicator')) {
              this.addFocusStyles(issue.element);
              fixed.push(issue);
            }
            break;

          case 'keyboard-navigation':
            if (issue.message.includes('skip navigation')) {
              this.addSkipLinks();
              fixed.push(issue);
            }
            break;

          case 'semantic-html':
            if (issue.message.includes('main landmark')) {
              this.addMainLandmark();
              fixed.push(issue);
            }
            break;

          case 'motion':
            if (issue.message.includes('prefers-reduced-motion')) {
              this.addReducedMotionStyles(issue.element);
              fixed.push(issue);
            }
            break;

          default:
            failed.push(issue);
        }
      } catch (error) {
        console.error('Auto-fix failed for issue:', issue.id, error);
        failed.push(issue);
      }
    }

    return { fixed, failed };
  }

  /**
   * Start continuous accessibility monitoring.
   *
   * Watches for DOM changes and re-audits the page, calling the callback
   * with updated reports. Returns function to stop monitoring.
   *
   * @param callback - Function called with each audit report
   * @returns Unsubscribe function to stop monitoring
   *
   * @example
   * ```ts
   * const unsubscribe = auditor.startMonitoring((report) => {
   *   console.log('Score:', report.score);
   * });
   *
   * // Later, stop monitoring
   * unsubscribe();
   * ```
   */
  startMonitoring(callback: (report: AccessibilityReport) => void): () => void {
    this.listeners.add(callback);

    // Monitor DOM changes
    const observer = new MutationObserver(async () => {
      const report = await this.auditPage();
      this.listeners.forEach((listener) => listener(report));
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['aria-*', 'role', 'tabindex', 'alt'],
    });

    this.observers.push(observer);

    return () => {
      this.listeners.delete(callback);
      observer.disconnect();
      const index = this.observers.indexOf(observer);
      if (index > -1) {
        this.observers.splice(index, 1);
      }
    };
  }

  /**
   * Stop all monitoring and clean up observers.
   */
  stopMonitoring(): void {
    this.observers.forEach((observer) => observer.disconnect());
    this.observers = [];
    this.listeners.clear();
  }

  /**
   * Add focus styles to an element.
   *
   * @param element - Element to add focus styles to
   */
  private addFocusStyles(element: Element): void {
    const currentStyle = element.getAttribute('style') || '';
    const newStyle = `${currentStyle}; outline: 2px solid #0066cc; outline-offset: 2px;`;
    element.setAttribute('style', newStyle);
  }

  /**
   * Add skip link to beginning of page.
   */
  private addSkipLinks(): void {
    const skipLink = document.createElement('a');
    skipLink.href = '#main-content';
    skipLink.textContent = 'Skip to main content';
    skipLink.className =
      'sr-only focus:not-sr-only focus:absolute focus:top-0 focus:left-0 bg-blue-600 text-white p-2 z-50';
    document.body.insertBefore(skipLink, document.body.firstChild);
  }

  /**
   * Mark main content area with proper landmark.
   */
  private addMainLandmark(): void {
    const main = document.querySelector('main');
    if (!main) {
      const contentArea = document.querySelector(
        '#root, #app, .app, .container'
      );
      if (contentArea) {
        contentArea.setAttribute('role', 'main');
        contentArea.id = 'main-content';
      }
    }
  }

  /**
   * Add styles respecting prefers-reduced-motion.
   *
   * @param _element - Element (unused, kept for API consistency)
   */
  private addReducedMotionStyles(_element: Element): void {
    const style = document.createElement('style');
    style.textContent = `
      @media (prefers-reduced-motion: reduce) {
        * {
          animation-duration: 0.01ms !important;
          animation-iteration-count: 1 !important;
          transition-duration: 0.01ms !important;
        }
      }
    `;
    document.head.appendChild(style);
  }

  /**
   * Generate comprehensive audit report from issues.
   *
   * Calculates score, categorizes issues, and generates recommendations.
   *
   * @param issues - Array of detected issues
   * @returns Complete audit report
   */
  private generateReport(issues: AccessibilityIssue[]): AccessibilityReport {
    const totalIssues = issues.length;
    const maxPossibleScore = 100;
    const errorWeight = 10;
    const warningWeight = 5;
    const infoWeight = 1;

    const errorCount = issues.filter((i) => i.type === 'error').length;
    const warningCount = issues.filter((i) => i.type === 'warning').length;
    const infoCount = issues.filter((i) => i.type === 'info').length;

    const totalDeduction =
      errorCount * errorWeight +
      warningCount * warningWeight +
      infoCount * infoWeight;
    const score = Math.max(0, maxPossibleScore - totalDeduction);

    const issuesByCategory = issues.reduce(
      (acc, issue) => {
        acc[issue.category] = (acc[issue.category] || 0) + 1;
        return acc;
      },
      {} as Record<string, number>
    );

    const issuesByImpact = issues.reduce(
      (acc, issue) => {
        acc[issue.impact] = (acc[issue.impact] || 0) + 1;
        return acc;
      },
      {} as Record<string, number>
    );

    const recommendations = this.generateRecommendations(issues);

    return {
      score,
      totalIssues,
      issuesByCategory,
      issuesByImpact,
      issues,
      recommendations,
      timestamp: new Date(),
    };
  }

  /**
   * Generate high-level recommendations from issues.
   *
   * @param issues - Array of detected issues
   * @returns List of actionable recommendations
   */
  private generateRecommendations(issues: AccessibilityIssue[]): string[] {
    const recommendations: string[] = [];

    if (issues.some((i) => i.category === 'color-contrast')) {
      recommendations.push(
        'Consider using a color palette that meets WCAG AA contrast standards'
      );
    }

    if (issues.some((i) => i.category === 'keyboard-navigation')) {
      recommendations.push(
        'Implement comprehensive keyboard navigation with visible focus indicators'
      );
    }

    if (issues.some((i) => i.category === 'screen-reader')) {
      recommendations.push(
        'Add descriptive alt text and ARIA labels for screen reader users'
      );
    }

    if (issues.some((i) => i.category === 'semantic-html')) {
      recommendations.push(
        'Use semantic HTML elements and proper landmark structure'
      );
    }

    return recommendations;
  }
}

// Singleton instance for convenience
export const accessibilityAuditor = new AccessibilityAuditor();

export default AccessibilityAuditor;
