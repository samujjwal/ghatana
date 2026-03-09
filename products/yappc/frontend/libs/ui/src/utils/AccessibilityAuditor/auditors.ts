/**
 * Core accessibility audit methods organized by category.
 *
 * Each audit method checks for specific accessibility issues and returns
 * an array of AccessibilityIssue objects.
 */

import { AccessibilityUtils } from './utils';

import type { AccessibilityIssue } from './types';

/**
 * Core auditor class with specialized audit methods for each category.
 *
 * Separated from main AccessibilityAuditor for better code organization.
 * Each method is responsible for one category of accessibility checks.
 */
export class AccessibilityAuditors {
  /**
   * Audit color contrast ratios across all text elements.
   *
   * @param root - Root element to audit
   * @param minRatio - Minimum contrast ratio to require
   * @returns Array of contrast-related issues
   */
  static async auditColorContrast(
    root: Element,
    minRatio: number
  ): Promise<AccessibilityIssue[]> {
    const issues: AccessibilityIssue[] = [];
    const textElements = root.querySelectorAll('*');

    for (const element of Array.from(textElements)) {
      const style = window.getComputedStyle(element);
      const hasText = element.textContent?.trim();

      if (
        !hasText ||
        style.display === 'none' ||
        style.visibility === 'hidden'
      ) {
        continue;
      }

      const color = AccessibilityUtils.parseColor(style.color);
      const backgroundColor =
        AccessibilityUtils.getEffectiveBackgroundColor(element);

      if (color && backgroundColor) {
        const contrast = AccessibilityUtils.calculateContrast(
          color,
          backgroundColor
        );
        const isLargeText = AccessibilityUtils.isLargeText(style);
        const requiredRatio = isLargeText ? 3.0 : minRatio;

        if (contrast < requiredRatio) {
          issues.push({
            id: AccessibilityUtils.generateIssueId('contrast'),
            type: 'error',
            category: 'color-contrast',
            element,
            message: `Insufficient color contrast ratio: ${contrast.toFixed(2)}:1 (required: ${requiredRatio}:1)`,
            suggestion:
              'Increase contrast by darkening text or lightening background. Consider using colors that meet WCAG AA standards.',
            impact: contrast < 3.0 ? 'critical' : 'serious',
            wcagCriteria: ['1.4.3'],
            autoFixable: false,
            documentation:
              'https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html',
          });
        }
      }
    }

    return issues;
  }

  /**
   * Audit keyboard navigation support.
   *
   * @param root - Root element to audit
   * @returns Array of keyboard navigation issues
   */
  static auditKeyboardNavigation(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];
    const interactiveElements = root.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"]), [contenteditable], [role="button"], [role="link"], [role="menuitem"]'
    );

    for (const element of Array.from(interactiveElements)) {
      const style = window.getComputedStyle(element);

      // Check if focusable elements are keyboard accessible
      if (
        element.getAttribute('tabindex') === '-1' &&
        !element.hasAttribute('aria-hidden')
      ) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('keyboard'),
          type: 'warning',
          category: 'keyboard-navigation',
          element,
          message: 'Interactive element is not keyboard accessible',
          suggestion: 'Remove tabindex="-1" or add keyboard event handlers',
          impact: 'serious',
          wcagCriteria: ['2.1.1'],
          autoFixable: false,
        });
      }

      // Check for visible focus indicators
      const focusOutline = style.outline;
      const focusOutlineWidth = style.outlineWidth;
      if (focusOutline === 'none' || focusOutlineWidth === '0px') {
        // Check if custom focus styles exist
        const hasCustomFocus = AccessibilityUtils.hasCustomFocusStyles(element);
        if (!hasCustomFocus) {
          issues.push({
            id: AccessibilityUtils.generateIssueId('focus-outline'),
            type: 'error',
            category: 'focus-management',
            element,
            message: 'Interactive element lacks visible focus indicator',
            suggestion:
              'Add outline or custom focus styles with sufficient contrast',
            impact: 'serious',
            wcagCriteria: ['2.4.7'],
            autoFixable: true,
          });
        }
      }
    }

    return issues;
  }

  /**
   * Audit screen reader compatibility.
   *
   * Checks for alt text on images, labels on forms, and heading hierarchy.
   *
   * @param root - Root element to audit
   * @param includeHidden - Include hidden elements in audit
   * @returns Array of screen reader related issues
   */
  static auditScreenReader(
    root: Element,
    includeHidden: boolean
  ): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];

    // Check for images without alt text
    const images = root.querySelectorAll('img');
    for (const img of Array.from(images)) {
      if (!includeHidden && window.getComputedStyle(img).display === 'none')
        continue;

      if (!img.hasAttribute('alt')) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('img-alt'),
          type: 'error',
          category: 'screen-reader',
          element: img,
          message: 'Image missing alt attribute',
          suggestion:
            'Add descriptive alt text or alt="" for decorative images',
          impact: 'serious',
          wcagCriteria: ['1.1.1'],
          autoFixable: false,
        });
      }
    }

    // Check for form labels
    const inputs = root.querySelectorAll(
      'input:not([type="hidden"]), select, textarea'
    );
    for (const input of Array.from(inputs)) {
      if (!includeHidden && window.getComputedStyle(input).display === 'none')
        continue;

      const hasLabel = AccessibilityUtils.hasAssociatedLabel(
        input as HTMLInputElement
      );
      if (!hasLabel) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('input-label'),
          type: 'error',
          category: 'screen-reader',
          element: input,
          message: 'Form input missing label',
          suggestion: 'Add <label> element or aria-label attribute',
          impact: 'serious',
          wcagCriteria: ['1.3.1', '4.1.2'],
          autoFixable: false,
        });
      }
    }

    // Check for headings hierarchy
    issues.push(...this.auditHeadingHierarchy(root));

    return issues;
  }

  /**
   * Audit focus management in modals and interactive regions.
   *
   * @param root - Root element to audit
   * @returns Array of focus management issues
   */
  static auditFocusManagement(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];

    // Check for skip links
    const skipLinks = root.querySelectorAll(
      'a[href^="#"], [role="button"][data-skip]'
    );
    if (skipLinks.length === 0) {
      const firstInteractive = root.querySelector(
        'button, [href], input, select, textarea'
      );
      if (firstInteractive) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('skip-link'),
          type: 'warning',
          category: 'keyboard-navigation',
          element: firstInteractive,
          message: 'Page lacks skip navigation links',
          suggestion: 'Add skip links to main content and navigation landmarks',
          impact: 'moderate',
          wcagCriteria: ['2.4.1'],
          autoFixable: true,
        });
      }
    }

    // Check for focus traps in modals
    const modals = root.querySelectorAll(
      '[role="dialog"], [role="alertdialog"], .modal, [data-modal]'
    );
    for (const modal of Array.from(modals)) {
      const style = window.getComputedStyle(modal);
      if (style.display !== 'none' && style.visibility !== 'hidden') {
        const focusableElements = modal.querySelectorAll(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );

        if (focusableElements.length === 0) {
          issues.push({
            id: AccessibilityUtils.generateIssueId('modal-focus'),
            type: 'error',
            category: 'focus-management',
            element: modal,
            message: 'Modal dialog contains no focusable elements',
            suggestion: 'Ensure modal has at least one focusable element',
            impact: 'serious',
            wcagCriteria: ['2.4.3'],
            autoFixable: false,
          });
        }
      }
    }

    return issues;
  }

  /**
   * Audit semantic HTML usage.
   *
   * Checks for proper landmarks, semantic elements, and ARIA roles.
   *
   * @param root - Root element to audit
   * @returns Array of semantic HTML issues
   */
  static auditSemanticHTML(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];

    // Check for proper landmarks
    const hasMain = root.querySelector('main, [role="main"]');
    const hasNav = root.querySelector('nav, [role="navigation"]');

    if (!hasMain) {
      issues.push({
        id: AccessibilityUtils.generateIssueId('landmark-main'),
        type: 'warning',
        category: 'semantic-html',
        element: root,
        message: 'Page missing main landmark',
        suggestion: 'Add <main> element or role="main" to main content area',
        impact: 'moderate',
        wcagCriteria: ['1.3.1'],
        autoFixable: true,
      });
    }

    if (!hasNav && root.querySelectorAll('a[href]').length > 3) {
      issues.push({
        id: AccessibilityUtils.generateIssueId('landmark-nav'),
        type: 'info',
        category: 'semantic-html',
        element: root,
        message: 'Consider adding navigation landmark',
        suggestion:
          'Wrap navigation links in <nav> element or add role="navigation"',
        impact: 'minor',
        wcagCriteria: ['1.3.1'],
        autoFixable: false,
      });
    }

    // Check for button vs div with click handlers
    const clickableDivs = root.querySelectorAll(
      'div[onclick], div[data-click], span[onclick]'
    );
    for (const div of Array.from(clickableDivs)) {
      if (!div.hasAttribute('role') && !div.hasAttribute('tabindex')) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('clickable-div'),
          type: 'error',
          category: 'semantic-html',
          element: div,
          message: 'Non-semantic clickable element',
          suggestion:
            'Use <button> element or add appropriate ARIA role and tabindex',
          impact: 'serious',
          wcagCriteria: ['4.1.2'],
          autoFixable: false,
        });
      }
    }

    return issues;
  }

  /**
   * Audit ARIA attribute usage and validity.
   *
   * @param root - Root element to audit
   * @returns Array of ARIA-related issues
   */
  static auditARIA(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];

    const elementsWithAria = root.querySelectorAll(
      '[aria-label], [aria-labelledby], [aria-describedby], [role]'
    );

    for (const element of Array.from(elementsWithAria)) {
      // Check for valid ARIA attributes
      const ariaAttributes = Array.from(element.attributes).filter((attr) =>
        attr.name.startsWith('aria-')
      );

      for (const attr of ariaAttributes) {
        if (!AccessibilityUtils.isValidAriaAttribute(attr.name)) {
          issues.push({
            id: AccessibilityUtils.generateIssueId('invalid-aria'),
            type: 'error',
            category: 'aria',
            element,
            message: `Invalid ARIA attribute: ${attr.name}`,
            suggestion: 'Remove invalid ARIA attribute or correct spelling',
            impact: 'moderate',
            wcagCriteria: ['4.1.2'],
            autoFixable: false,
          });
        }
      }

      // Check for referenced elements
      const labelledBy = element.getAttribute('aria-labelledby');
      if (labelledBy) {
        const ids = labelledBy.split(' ');
        for (const id of ids) {
          if (!document.getElementById(id)) {
            issues.push({
              id: AccessibilityUtils.generateIssueId('aria-labelledby-ref'),
              type: 'error',
              category: 'aria',
              element,
              message: `aria-labelledby references non-existent element: ${id}`,
              suggestion:
                'Ensure referenced element exists or remove reference',
              impact: 'serious',
              wcagCriteria: ['4.1.2'],
              autoFixable: false,
            });
          }
        }
      }
    }

    return issues;
  }

  /**
   * Audit motion and animation preferences.
   *
   * @param root - Root element to audit
   * @returns Array of motion-related issues
   */
  static auditMotion(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];

    // Check for respect of prefers-reduced-motion
    const animatedElements = root.querySelectorAll(
      '[style*="animation"], [style*="transition"], .animate, .transition'
    );

    for (const element of Array.from(animatedElements)) {
      const style = window.getComputedStyle(element);
      const hasAnimation =
        style.animationName !== 'none' || style.transitionProperty !== 'none';

      if (hasAnimation && !AccessibilityUtils.respectsReducedMotion()) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('motion'),
          type: 'warning',
          category: 'motion',
          element,
          message: 'Animation does not respect prefers-reduced-motion',
          suggestion:
            'Add @media (prefers-reduced-motion: reduce) to disable animations',
          impact: 'moderate',
          wcagCriteria: ['2.3.3'],
          autoFixable: true,
        });
      }
    }

    return issues;
  }

  /**
   * Audit heading hierarchy for logical structure.
   *
   * @param root - Root element to audit
   * @returns Array of heading hierarchy issues
   */
  private static auditHeadingHierarchy(root: Element): AccessibilityIssue[] {
    const issues: AccessibilityIssue[] = [];
    const headings = Array.from(
      root.querySelectorAll('h1, h2, h3, h4, h5, h6')
    );

    let previousLevel = 0;

    for (const heading of headings) {
      const level = parseInt(heading.tagName.charAt(1), 10);

      if (level - previousLevel > 1) {
        issues.push({
          id: AccessibilityUtils.generateIssueId('heading-hierarchy'),
          type: 'warning',
          category: 'semantic-html',
          element: heading,
          message: `Heading level skips from h${previousLevel} to h${level}`,
          suggestion:
            'Use consecutive heading levels for proper document structure',
          impact: 'moderate',
          wcagCriteria: ['1.3.1'],
          autoFixable: false,
        });
      }

      previousLevel = level;
    }

    return issues;
  }
}
