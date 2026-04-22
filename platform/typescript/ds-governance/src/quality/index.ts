/**
 * @fileoverview Contract quality gates for design system governance.
 *
 * Rules covered:
 * - Invalid or missing token references in props
 * - Missing required metadata fields
 * - Preview restriction consistency (trust level vs. privacy/network requirements)
 * - Accessibility declaration completeness
 * - Privacy and review-sensitive metadata completeness
 *
 * @doc.type module
 * @doc.purpose Contract quality validation rules beyond naming and compatibility
 * @doc.layer platform
 * @doc.pattern Gate
 */

import type { ComponentContract, ComponentProp } from '@ghatana/ds-schema';

// ============================================================================
// Shared result type
// ============================================================================

export interface QualityViolation {
  readonly rule: string;
  readonly path: string;
  readonly severity: 'error' | 'warning';
  readonly message: string;
}

export interface QualityCheckResult {
  readonly passed: boolean;
  readonly violations: readonly QualityViolation[];
}

function violation(
  rule: string,
  path: string,
  severity: 'error' | 'warning',
  message: string,
): QualityViolation {
  return { rule, path, severity, message };
}

// ============================================================================
// Token reference validation
// ============================================================================

/**
 * Validates that every prop of type `token-ref` declares at least one
 * `tokenTypes` entry in its `builderMetadata`, so the builder knows which
 * token categories to offer in the picker.
 *
 * Also checks that `token-ref` props do not carry a static `defaultValue`
 * (the default should be expressed as a token alias, not a raw value).
 */
export function checkTokenReferences(contract: ComponentContract): QualityCheckResult {
  const violations: QualityViolation[] = [];

  for (const prop of contract.props) {
    const path = `props.${prop.name}`;

    if (prop.type === 'token-ref') {
      const tokenTypes = prop.builderMetadata?.tokenTypes;
      if (!tokenTypes || tokenTypes.length === 0) {
        violations.push(
          violation(
            'token-ref-missing-types',
            path,
            'error',
            `Prop "${prop.name}" has type "token-ref" but declares no "tokenTypes" — ` +
              'the builder cannot determine which token categories to show.',
          ),
        );
      }

      if (prop.defaultValue !== undefined && typeof prop.defaultValue !== 'object') {
        violations.push(
          violation(
            'token-ref-raw-default',
            path,
            'warning',
            `Prop "${prop.name}" is a token reference but has a raw defaultValue. ` +
              'Prefer a token alias object { $value: "...", $type: "..." } instead.',
          ),
        );
      }
    }
  }

  return { passed: violations.every((v) => v.severity !== 'error'), violations };
}

// ============================================================================
// Required metadata completeness
// ============================================================================

/**
 * Checks that a contract carries the minimum metadata required for it to
 * be displayed correctly in the builder palette and documentation surfaces.
 *
 * - `description` must be present
 * - `metadata.category` must be non-empty
 * - `builder.palette.group` must be set when a codegen entry is present
 * - `builder.codegen.importPath` and `componentName` must both be present
 *   when a codegen block exists
 */
export function checkRequiredMetadata(contract: ComponentContract): QualityCheckResult {
  const violations: QualityViolation[] = [];

  if (!contract.description || contract.description.trim().length === 0) {
    violations.push(
      violation(
        'missing-description',
        'description',
        'warning',
        `Component "${contract.name}" has no description — add one for the builder palette and docs.`,
      ),
    );
  }

  if (!contract.metadata.category || contract.metadata.category.trim().length === 0) {
    violations.push(
      violation(
        'missing-category',
        'metadata.category',
        'error',
        `Component "${contract.name}" has no category — required for palette grouping.`,
      ),
    );
  }

  const codegen = contract.builder?.codegen;
  if (codegen) {
    if (!codegen.importPath || codegen.importPath.trim().length === 0) {
      violations.push(
        violation(
          'codegen-missing-import-path',
          'builder.codegen.importPath',
          'error',
          `Component "${contract.name}" has a codegen block but no importPath.`,
        ),
      );
    }
    if (!codegen.componentName || codegen.componentName.trim().length === 0) {
      violations.push(
        violation(
          'codegen-missing-component-name',
          'builder.codegen.componentName',
          'error',
          `Component "${contract.name}" has a codegen block but no componentName.`,
        ),
      );
    }

    // Warn if palette group is missing alongside a codegen entry
    if (!contract.builder?.palette?.group) {
      violations.push(
        violation(
          'codegen-missing-palette-group',
          'builder.palette.group',
          'warning',
          `Component "${contract.name}" has codegen metadata but no palette group — ` +
            'it may not appear in the correct palette section.',
        ),
      );
    }
  }

  return { passed: violations.every((v) => v.severity !== 'error'), violations };
}

// ============================================================================
// Preview restriction consistency
// ============================================================================

/**
 * Checks that a contract's preview restriction trust level is consistent with
 * its other requirements:
 *
 * - A component that `requiresNetwork` should not declare `minimumTrustLevel:
 *   "untrusted"` (network access requires at least semi-trusted).
 * - A component that `requiresConsent` should not declare `minimumTrustLevel:
 *   "untrusted"` (consent flows require a trusted context).
 * - A component that `requiresStorage` should not declare `minimumTrustLevel:
 *   "untrusted"` (storage access is unavailable in the most restrictive sandbox).
 * - A component with a privacy `minimumPreviewTrustLevel` stricter than the
 *   preview `minimumTrustLevel` should reconcile them.
 */
export function checkPreviewRestrictionConsistency(contract: ComponentContract): QualityCheckResult {
  const violations: QualityViolation[] = [];
  const preview = contract.preview;
  if (!preview) return { passed: true, violations: [] };

  const trustRank: Record<string, number> = {
    'untrusted': 0,
    'semi-trusted': 1,
    'trusted-controlled': 2,
    'trusted-local': 3,
  };

  const rank = trustRank[preview.minimumTrustLevel] ?? 0;

  if (preview.requiresNetwork && rank === 0) {
    violations.push(
      violation(
        'preview-network-untrusted',
        'preview.minimumTrustLevel',
        'error',
        `Component "${contract.name}" requires network access but declares minimumTrustLevel "untrusted". ` +
          'Network is unavailable in untrusted sandboxes — set minimumTrustLevel to at least "semi-trusted".',
      ),
    );
  }

  if (preview.requiresConsent && rank < 1) {
    violations.push(
      violation(
        'preview-consent-untrusted',
        'preview.minimumTrustLevel',
        'error',
        `Component "${contract.name}" requires consent handling but declares minimumTrustLevel "untrusted". ` +
          'Consent flows are only available in trusted preview contexts.',
      ),
    );
  }

  if (preview.requiresStorage && rank === 0) {
    violations.push(
      violation(
        'preview-storage-untrusted',
        'preview.minimumTrustLevel',
        'warning',
        `Component "${contract.name}" requires storage access but minimumTrustLevel is "untrusted". ` +
          'Storage is typically blocked in the most restrictive sandboxes.',
      ),
    );
  }

  // Cross-check with privacy.minimumPreviewTrustLevel
  const privacyMinTrust = contract.privacy?.minimumPreviewTrustLevel;
  if (privacyMinTrust) {
    const privacyRank = trustRank[privacyMinTrust] ?? 0;
    if (privacyRank > rank) {
      violations.push(
        violation(
          'preview-privacy-trust-mismatch',
          'privacy.minimumPreviewTrustLevel',
          'error',
          `Component "${contract.name}" has privacy.minimumPreviewTrustLevel "${privacyMinTrust}" which is stricter ` +
            `than preview.minimumTrustLevel "${preview.minimumTrustLevel}". ` +
            'Align both fields so the effective sandbox policy is not weaker than required by privacy rules.',
        ),
      );
    }
  }

  return { passed: violations.every((v) => v.severity !== 'error'), violations };
}

// ============================================================================
// Accessibility declaration completeness
// ============================================================================

/**
 * Checks that interactive components declare sufficient accessibility metadata.
 *
 * Interactive components (those with event handlers or explicit keyboard nav)
 * must:
 * - Declare a `role` or have a semantic HTML element implied by name
 * - Declare `keyboardNavigation: true` when they handle events
 * - Declare WCAG criteria they satisfy if they have `ariaRequired: true`
 */
export function checkAccessibilityDeclarations(contract: ComponentContract): QualityCheckResult {
  const violations: QualityViolation[] = [];
  const a11y = contract.metadata.a11y;

  const hasInteractiveEvents = contract.events.some((e) =>
    /click|press|change|submit|focus|blur|key/i.test(e.name),
  );

  if (hasInteractiveEvents) {
    if (!a11y) {
      violations.push(
        violation(
          'a11y-missing-for-interactive',
          'metadata.a11y',
          'error',
          `Component "${contract.name}" has interactive events but no a11y declaration. ` +
            'Add metadata.a11y with at least a role and keyboardNavigation flag.',
        ),
      );
    } else {
      if (!a11y.role) {
        violations.push(
          violation(
            'a11y-missing-role',
            'metadata.a11y.role',
            'warning',
            `Interactive component "${contract.name}" has no a11y.role declared. ` +
              'Screen readers may not announce this component correctly without a role.',
          ),
        );
      }

      if (!a11y.keyboardNavigation) {
        violations.push(
          violation(
            'a11y-keyboard-not-declared',
            'metadata.a11y.keyboardNavigation',
            'warning',
            `Interactive component "${contract.name}" does not declare keyboardNavigation: true. ` +
              'Confirm keyboard support is implemented and declare it explicitly.',
          ),
        );
      }
    }
  }

  if (a11y?.ariaRequired && (!a11y.wcagCriteria || a11y.wcagCriteria.length === 0)) {
    violations.push(
      violation(
        'a11y-missing-wcag-criteria',
        'metadata.a11y.wcagCriteria',
        'warning',
        `Component "${contract.name}" declares ariaRequired but no wcagCriteria. ` +
          'Specify the WCAG success criteria this component satisfies (e.g. ["1.3.1", "4.1.2"]).',
      ),
    );
  }

  return { passed: violations.every((v) => v.severity !== 'error'), violations };
}

// ============================================================================
// Privacy and review metadata completeness
// ============================================================================

/**
 * Checks that props with sensitive data classifications have matching
 * review and privacy metadata at the prop and contract level.
 *
 * Rules:
 * - A prop with `dataClassification` of 'pii', 'sensitive', 'restricted', or
 *   'confidential' must have `reviewRequired: true`.
 * - A prop with `secretBearing: true` must have `reviewRequired: true`.
 * - If any prop is classified as 'pii' or 'sensitive', the contract's
 *   `privacy.mayRenderPii` should be `true`.
 * - If any prop has `reviewRequired: true`, the contract's
 *   `aiPolicy.reviewRequiredProps` should list that prop name.
 */
export function checkPrivacyAndReviewMetadata(contract: ComponentContract): QualityCheckResult {
  const violations: QualityViolation[] = [];

  const sensitiveClassifications = new Set(['pii', 'sensitive', 'restricted', 'confidential']);
  const piiClassifications = new Set(['pii', 'sensitive']);

  let hasPiiProp = false;
  const reviewRequiredPropNames: string[] = [];

  for (const prop of contract.props) {
    const path = `props.${prop.name}`;
    const classif = prop.dataClassification;
    const isSensitive = classif !== undefined && sensitiveClassifications.has(classif);

    if (isSensitive || prop.secretBearing) {
      if (!prop.reviewRequired) {
        violations.push(
          violation(
            'sensitive-prop-missing-review-required',
            path,
            'error',
            `Prop "${prop.name}" has a sensitive data classification ("${classif ?? 'secret-bearing'}") ` +
              'but does not declare reviewRequired: true.',
          ),
        );
      } else {
        reviewRequiredPropNames.push(prop.name);
      }
    }

    if (classif !== undefined && piiClassifications.has(classif)) {
      hasPiiProp = true;
    }
  }

  // Contract-level privacy consistency
  if (hasPiiProp && !contract.privacy?.mayRenderPii) {
    violations.push(
      violation(
        'pii-prop-missing-contract-privacy',
        'privacy.mayRenderPii',
        'warning',
        `Component "${contract.name}" has PII-classified props but privacy.mayRenderPii is not true. ` +
          'Declare privacy.mayRenderPii: true so preview and codegen can apply the correct restrictions.',
      ),
    );
  }

  // AI policy consistency
  if (reviewRequiredPropNames.length > 0 && contract.aiPolicy) {
    const declaredReviewProps = new Set(contract.aiPolicy.reviewRequiredProps);
    const undeclared = reviewRequiredPropNames.filter((n) => !declaredReviewProps.has(n));
    if (undeclared.length > 0) {
      violations.push(
        violation(
          'ai-policy-missing-review-props',
          'aiPolicy.reviewRequiredProps',
          'warning',
          `Component "${contract.name}" has review-required props [${undeclared.join(', ')}] ` +
            'that are not listed in aiPolicy.reviewRequiredProps. ' +
            'AI assistance may autonomously modify these props without human review.',
        ),
      );
    }
  }

  return { passed: violations.every((v) => v.severity !== 'error'), violations };
}

// ============================================================================
// Combined quality check
// ============================================================================

/**
 * Runs all quality checks for a component contract and aggregates the results.
 * Returns a combined result indicating whether the contract passes all gates.
 */
export function runContractQualityChecks(contract: ComponentContract): QualityCheckResult {
  const results: QualityCheckResult[] = [
    checkTokenReferences(contract),
    checkRequiredMetadata(contract),
    checkPreviewRestrictionConsistency(contract),
    checkAccessibilityDeclarations(contract),
    checkPrivacyAndReviewMetadata(contract),
  ];

  const allViolations = results.flatMap((r) => [...r.violations]);
  const passed = results.every((r) => r.passed);

  return { passed, violations: allViolations };
}

export type {
  ComponentContract,
  ComponentProp,
} from '@ghatana/ds-schema';
