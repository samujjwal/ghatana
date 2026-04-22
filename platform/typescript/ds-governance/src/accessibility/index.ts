/**
 * @fileoverview Accessibility governance helpers for contract validation and auditing.
 *
 * Provides automated checks for:
 * - WCAG compliance declarations
 * - Required accessibility props and attributes
 * - Focus management obligations
 * - Keyboard navigation support
 * - Semantic HTML/ARIA declarations
 * - Motion reduction requirements
 *
 * @doc.type module
 * @doc.purpose Accessibility governance and validation for design-system components
 * @doc.layer platform
 * @doc.pattern Gate
 */

import type { ComponentContract } from '@ghatana/ds-schema';

// ============================================================================
// Accessibility check result types
// ============================================================================

export interface AccessibilityViolation {
  readonly rule: string;
  readonly path: string;
  readonly severity: 'error' | 'warning' | 'info';
  readonly message: string;
  readonly suggestion?: string;
}

export interface AccessibilityCheckResult {
  readonly passed: boolean;
  readonly violations: readonly AccessibilityViolation[];
  readonly wcagLevel: 'A' | 'AA' | 'AAA';
  readonly score: number; // 0–100, where 100 = no violations
}

function violation(
  rule: string,
  path: string,
  severity: 'error' | 'warning' | 'info',
  message: string,
  suggestion?: string,
): AccessibilityViolation {
  return { rule, path, severity, message, suggestion };
}

// ============================================================================
// WCAG level validation
// ============================================================================

/**
 * Validates that the component declares appropriate WCAG conformance level
 * and that metadata is complete for that level.
 *
 * WCAG A: bare minimum accessibility (form labels, basic semantic HTML)
 * WCAG AA: standard compliance (color contrast, heading structure, keyboard nav)
 * WCAG AAA: enhanced (extended color contrast, captions, sign language)
 */
export function checkWCAGConformance(contract: ComponentContract): AccessibilityCheckResult {
  const violations: AccessibilityViolation[] = [];

  const builderA11y = contract.builderA11y;
  const metadata = contract.metadata;
  const wcagLevel = builderA11y?.wcagLevel ?? 'AA';

  // Check that wcagLevel is valid and documented
  if (!builderA11y) {
    violations.push(
      violation(
        'missing-builder-a11y',
        'builderA11y',
        'warning',
        'Component declares no builder accessibility obligations — accessibility guidance may be incomplete.',
        'Add a builderA11y object with wcagLevel, requiredA11yProps, and a11yGuidance.',
      ),
    );
  } else {
    // Check that guidance is provided at AA and higher
    if ((wcagLevel === 'AA' || wcagLevel === 'AAA') && !builderA11y.a11yGuidance) {
      violations.push(
        violation(
          'missing-wcag-guidance',
          'builderA11y.a11yGuidance',
          'warning',
          `Component declares WCAG ${wcagLevel} but provides no accessibility guidance.`,
          'Add a11yGuidance text explaining what authors must do to keep the component accessible.',
        ),
      );
    }

    // Check WCAG AA requirements: keyboard navigation
    if (wcagLevel === 'AA' || wcagLevel === 'AAA') {
      const hasKeyboardSupport = metadata.a11y?.keyboardNavigation ?? false;
      if (!hasKeyboardSupport && contract.events.some((e) => e.name === 'onClick' || e.name === 'onActivate')) {
        violations.push(
          violation(
            'wcag-aa-keyboard-required',
            'metadata.a11y.keyboardNavigation',
            'error',
            `Component emits click/activation events but does not declare keyboard navigation support (required for WCAG AA).`,
            'Set metadata.a11y.keyboardNavigation to true and test with keyboard-only navigation.',
          ),
        );
      }
    }

    // Check WCAG AA requirements: semantic role
    if (wcagLevel === 'AA' || wcagLevel === 'AAA') {
      const hasRole = metadata.a11y?.role ?? false;
      if (!hasRole && ['button', 'link', 'input', 'modal', 'navigation', 'main'].some((t) => contract.name.toLowerCase().includes(t))) {
        violations.push(
          violation(
            'wcag-aa-semantic-role',
            'metadata.a11y.role',
            'warning',
            `Component ${contract.name} appears to be interactive but does not declare a semantic ARIA role.`,
            'Set metadata.a11y.role to the appropriate ARIA role (e.g., "button", "navigation", "heading").',
          ),
        );
      }
    }
  }

  // Compute accessibility score
  const errorCount = violations.filter((v) => v.severity === 'error').length;
  const warningCount = violations.filter((v) => v.severity === 'warning').length;
  const score = Math.max(0, 100 - errorCount * 20 - warningCount * 5);

  return {
    passed: errorCount === 0,
    violations,
    wcagLevel,
    score,
  };
}

// ============================================================================
// Focus management validation
// ============================================================================

/**
 * Validates that components that trap focus (modals, dropdowns, etc.) declare
 * and handle that obligation correctly in the builder.
 */
export function checkFocusManagement(contract: ComponentContract): AccessibilityCheckResult {
  const violations: AccessibilityViolation[] = [];

  const builderA11y = contract.builderA11y;
  const trapsFocus = builderA11y?.trapsFocusRequiresClose ?? false;
  const metadata = contract.metadata;

  // If the component name suggests focus trapping, validate it's declared
  const focusTrappingNames = ['modal', 'dialog', 'dropdown', 'menu', 'popover', 'drawer'];
  const appearsFocusTrapping = focusTrappingNames.some((name) =>
    contract.name.toLowerCase().includes(name),
  );

  if (appearsFocusTrapping && !trapsFocus) {
    violations.push(
      violation(
        'undeclared-focus-trap',
        'builderA11y.trapsFocusRequiresClose',
        'warning',
        `Component "${contract.name}" appears to trap focus but does not declare trapsFocusRequiresClose.`,
        'If this component prevents focus from escaping (modal, popover), set trapsFocusRequiresClose to true and ensure a close affordance is present.',
      ),
    );
  }

  // If trapsFocusRequiresClose is true, validate that there's a way to close
  if (trapsFocus) {
    const hasCloseEvent = contract.events.some((e) =>
      e.name.toLowerCase().includes('close') || e.name.toLowerCase().includes('dismiss'),
    );
    if (!hasCloseEvent && !contract.props.some((p) =>
      p.name.toLowerCase().includes('close') || p.name.toLowerCase().includes('dismiss'),
    )) {
      violations.push(
        violation(
          'focus-trap-no-close',
          'events or props',
          'error',
          `Component traps focus but declares no close affordance (event or prop).`,
          'Add an onClose event or a closeButton/dismissible prop for keyboard and assistive technology users to escape.',
        ),
      );
    }
  }

  const errorCount = violations.filter((v) => v.severity === 'error').length;
  const warningCount = violations.filter((v) => v.severity === 'warning').length;
  const score = Math.max(0, 100 - errorCount * 20 - warningCount * 10);

  return {
    passed: errorCount === 0,
    violations,
    wcagLevel: 'AA',
    score,
  };
}

// ============================================================================
// Motion and animation validation
// ============================================================================

/**
 * Validates that components with motion declare motion reduction support
 * when required by the WCAG guidelines.
 */
export function checkMotionAndAnimation(contract: ComponentContract): AccessibilityCheckResult {
  const violations: AccessibilityViolation[] = [];

  const builderA11y = contract.builderA11y;
  const motionRequiresSupport = builderA11y?.motionRequiresReductionSupport ?? false;

  // Check if component has motion-related keywords
  const motionKeywords = ['transition', 'animation', 'slide', 'fade', 'bounce', 'scroll', 'parallax'];
  const hasMotionKeywords = motionKeywords.some((keyword) =>
    contract.description?.toLowerCase().includes(keyword) ||
    contract.name.toLowerCase().includes(keyword),
  );

  if (hasMotionKeywords && !motionRequiresSupport) {
    violations.push(
      violation(
        'undeclared-motion',
        'builderA11y.motionRequiresReductionSupport',
        'info',
        `Component "${contract.name}" may have motion/animation but does not declare motion reduction support.`,
        'If this component uses animations or transitions, set motionRequiresReductionSupport to true and ensure it respects prefers-reduced-motion.',
      ),
    );
  }

  // Check if builder.canvas has motion-related props
  const hasMotionProps = contract.props.some((p) =>
    p.name.toLowerCase().includes('animate') || p.name.toLowerCase().includes('transition'),
  );

  if (hasMotionProps && !motionRequiresSupport) {
    violations.push(
      violation(
        'motion-prop-no-reduction',
        'builderA11y.motionRequiresReductionSupport',
        'warning',
        `Component has animation props but does not declare prefers-reduced-motion support.`,
        'Ensure animations respect the prefers-reduced-motion CSS media query and declare this support.',
      ),
    );
  }

  const warningCount = violations.filter((v) => v.severity === 'warning').length;
  const score = Math.max(0, 100 - warningCount * 5);

  return {
    passed: true,
    violations,
    wcagLevel: 'AA',
    score,
  };
}

// ============================================================================
// Required accessibility props validation
// ============================================================================

/**
 * Validates that components declare all required accessibility props
 * in the builderA11y.requiredA11yProps array.
 */
export function checkRequiredA11yProps(contract: ComponentContract): AccessibilityCheckResult {
  const violations: AccessibilityViolation[] = [];

  const builderA11y = contract.builderA11y;
  const requiredProps = builderA11y?.requiredA11yProps ?? [];

  // Common accessibility props by component type
  const componentType = contract.metadata.category;
  const commonA11yProps: Record<string, string[]> = {
    input: ['label', 'aria-label', 'aria-describedby'],
    button: ['aria-label', 'children'],
    navigation: ['aria-label', 'role'],
    feedback: ['role', 'aria-live', 'aria-label'],
  };

  const expectedProps = commonA11yProps[componentType] || [];

  for (const expectedProp of expectedProps) {
    if (!requiredProps.includes(expectedProp)) {
      const propExists = contract.props.some((p) => p.name === expectedProp || p.name === expectedProp.replace('aria-', ''));
      if (propExists) {
        violations.push(
          violation(
            'missing-required-a11y-prop-declaration',
            `builderA11y.requiredA11yProps`,
            'warning',
            `Component has "${expectedProp}" prop but does not declare it as required in requiredA11yProps for ${componentType}s.`,
            `Add "${expectedProp}" to builderA11y.requiredA11yProps so authors are prompted to set it.`,
          ),
        );
      }
    }
  }

  const warningCount = violations.filter((v) => v.severity === 'warning').length;
  const score = Math.max(0, 100 - warningCount * 10);

  return {
    passed: violations.filter((v) => v.severity === 'error').length === 0,
    violations,
    wcagLevel: 'AA',
    score,
  };
}

// ============================================================================
// Composite accessibility audit
// ============================================================================

export interface ComprehensiveAccessibilityResult {
  readonly passed: boolean;
  readonly wcagLevel: 'A' | 'AA' | 'AAA';
  readonly overallScore: number;
  readonly conformance: AccessibilityCheckResult;
  readonly focusManagement: AccessibilityCheckResult;
  readonly motionAndAnimation: AccessibilityCheckResult;
  readonly requiredProps: AccessibilityCheckResult;
}

/**
 * Runs all accessibility checks against a contract and returns a comprehensive report.
 */
export function auditContractAccessibility(contract: ComponentContract): ComprehensiveAccessibilityResult {
  const conformance = checkWCAGConformance(contract);
  const focusManagement = checkFocusManagement(contract);
  const motionAndAnimation = checkMotionAndAnimation(contract);
  const requiredProps = checkRequiredA11yProps(contract);

  const scores = [conformance.score, focusManagement.score, motionAndAnimation.score, requiredProps.score];
  const overallScore = Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);

  return {
    passed: conformance.passed && focusManagement.passed && requiredProps.passed,
    wcagLevel: conformance.wcagLevel,
    overallScore,
    conformance,
    focusManagement,
    motionAndAnimation,
    requiredProps,
  };
}
