/**
 * @fileoverview Tests for contract quality governance rules.
 */

import { describe, it, expect } from 'vitest';
import {
  checkTokenReferences,
  checkRequiredMetadata,
  checkPreviewRestrictionConsistency,
  checkAccessibilityDeclarations,
  checkPrivacyAndReviewMetadata,
  runContractQualityChecks,
} from '../quality/index';
import type { ComponentContract } from '@ghatana/ds-schema';

// ============================================================================
// Minimal contract factory
// ============================================================================

function makeContract(overrides: Partial<ComponentContract> = {}): ComponentContract {
  return {
    name: 'Button',
    version: '1.0.0',
    description: 'A clickable button',
    status: 'stable',
    metadata: {
      category: 'actions',
      tags: [],
      since: '1.0.0',
    },
    props: [],
    slots: [],
    events: [],
    styles: [],
    dependencies: [],
    examples: [],
    ...overrides,
  } as unknown as ComponentContract;
}

// ============================================================================
// Token reference validation
// ============================================================================

describe('checkTokenReferences', () => {
  it('passes when there are no token-ref props', () => {
    const contract = makeContract({
      props: [
        { name: 'label', type: 'string', required: false } as ComponentContract['props'][number],
      ],
    });
    const result = checkTokenReferences(contract);
    expect(result.passed).toBe(true);
    expect(result.violations).toHaveLength(0);
  });

  it('errors when a token-ref prop has no tokenTypes', () => {
    const contract = makeContract({
      props: [
        { name: 'color', type: 'token-ref', required: false, builderMetadata: {} } as ComponentContract['props'][number],
      ],
    });
    const result = checkTokenReferences(contract);
    expect(result.passed).toBe(false);
    const v = result.violations.find((x) => x.rule === 'token-ref-missing-types');
    expect(v).toBeDefined();
    expect(v?.severity).toBe('error');
  });

  it('passes when a token-ref prop declares tokenTypes', () => {
    const contract = makeContract({
      props: [
        {
          name: 'color',
          type: 'token-ref',
          required: false,
          builderMetadata: { tokenTypes: ['color'] },
        } as ComponentContract['props'][number],
      ],
    });
    const result = checkTokenReferences(contract);
    expect(result.passed).toBe(true);
    expect(result.violations.filter((v) => v.rule === 'token-ref-missing-types')).toHaveLength(0);
  });

  it('warns when a token-ref prop has a raw string defaultValue', () => {
    const contract = makeContract({
      props: [
        {
          name: 'color',
          type: 'token-ref',
          required: false,
          defaultValue: '#ff0000',
          builderMetadata: { tokenTypes: ['color'] },
        } as ComponentContract['props'][number],
      ],
    });
    const result = checkTokenReferences(contract);
    const w = result.violations.find((x) => x.rule === 'token-ref-raw-default');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
    // Warning does not fail the gate
    expect(result.passed).toBe(true);
  });
});

// ============================================================================
// Required metadata validation
// ============================================================================

describe('checkRequiredMetadata', () => {
  it('passes for a fully described contract', () => {
    const contract = makeContract();
    const result = checkRequiredMetadata(contract);
    expect(result.passed).toBe(true);
    expect(result.violations).toHaveLength(0);
  });

  it('warns when description is missing', () => {
    const contract = makeContract({ description: '' });
    const result = checkRequiredMetadata(contract);
    const w = result.violations.find((x) => x.rule === 'missing-description');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });

  it('errors when metadata.category is missing', () => {
    const contract = makeContract({
      metadata: { category: '', tags: [], since: '1.0.0' },
    });
    const result = checkRequiredMetadata(contract);
    expect(result.passed).toBe(false);
    const e = result.violations.find((x) => x.rule === 'missing-category');
    expect(e).toBeDefined();
    expect(e?.severity).toBe('error');
  });

  it('errors when codegen block is present but importPath is missing', () => {
    const contract = makeContract({
      builder: {
        codegen: { importPath: '', componentName: 'Button', namedExport: true },
      },
    });
    const result = checkRequiredMetadata(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.find((x) => x.rule === 'codegen-missing-import-path')).toBeDefined();
  });

  it('warns when codegen is present but palette group is missing', () => {
    const contract = makeContract({
      builder: {
        codegen: { importPath: '@ghatana/design-system', componentName: 'Button', namedExport: true },
        palette: undefined,
      },
    });
    const result = checkRequiredMetadata(contract);
    const w = result.violations.find((x) => x.rule === 'codegen-missing-palette-group');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });
});

// ============================================================================
// Preview restriction consistency
// ============================================================================

describe('checkPreviewRestrictionConsistency', () => {
  it('passes when there are no preview restrictions', () => {
    const contract = makeContract({ preview: undefined });
    const result = checkPreviewRestrictionConsistency(contract);
    expect(result.passed).toBe(true);
  });

  it('passes with consistent network + semi-trusted', () => {
    const contract = makeContract({
      preview: {
        minimumTrustLevel: 'semi-trusted',
        requiresNetwork: true,
        requiresStorage: false,
        requiresConsent: false,
      },
    });
    const result = checkPreviewRestrictionConsistency(contract);
    expect(result.violations.filter((v) => v.severity === 'error')).toHaveLength(0);
  });

  it('errors when requiresNetwork is true but minimumTrustLevel is untrusted', () => {
    const contract = makeContract({
      preview: {
        minimumTrustLevel: 'untrusted',
        requiresNetwork: true,
        requiresStorage: false,
        requiresConsent: false,
      },
    });
    const result = checkPreviewRestrictionConsistency(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.find((x) => x.rule === 'preview-network-untrusted')).toBeDefined();
  });

  it('errors when privacy minimumPreviewTrustLevel is stricter than preview minimumTrustLevel', () => {
    const contract = makeContract({
      preview: {
        minimumTrustLevel: 'untrusted',
        requiresNetwork: false,
        requiresStorage: false,
        requiresConsent: false,
      },
      privacy: {
        minimumPreviewTrustLevel: 'semi-trusted',
        requiresConsentFlow: false,
        mayRenderPii: false,
        regulatoryFrameworks: [],
      },
    });
    const result = checkPreviewRestrictionConsistency(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.find((x) => x.rule === 'preview-privacy-trust-mismatch')).toBeDefined();
  });
});

// ============================================================================
// Accessibility declaration completeness
// ============================================================================

describe('checkAccessibilityDeclarations', () => {
  it('passes for a non-interactive component with no a11y block', () => {
    const contract = makeContract({
      events: [],
      metadata: { category: 'display', tags: [], since: '1.0.0' },
    });
    const result = checkAccessibilityDeclarations(contract);
    expect(result.passed).toBe(true);
  });

  it('errors when interactive component has no a11y declaration', () => {
    const contract = makeContract({
      events: [{ name: 'onClick', description: 'Fires on click', payload: 'void' } as ComponentContract['events'][number]],
      metadata: { category: 'actions', tags: [], since: '1.0.0' },
    });
    const result = checkAccessibilityDeclarations(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.find((x) => x.rule === 'a11y-missing-for-interactive')).toBeDefined();
  });

  it('warns when interactive component has no role declared', () => {
    const contract = makeContract({
      events: [{ name: 'onClick', description: '', payload: 'void' } as ComponentContract['events'][number]],
      metadata: {
        category: 'actions',
        tags: [],
        since: '1.0.0',
        a11y: {
          keyboardNavigation: true,
          screenReader: true,
          trapsFocus: false,
          ariaRequired: false,
          ariaSupported: [],
          wcagCriteria: [],
        },
      },
    });
    const result = checkAccessibilityDeclarations(contract);
    const w = result.violations.find((x) => x.rule === 'a11y-missing-role');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });

  it('warns when ariaRequired is true but wcagCriteria is empty', () => {
    const contract = makeContract({
      metadata: {
        category: 'forms',
        tags: [],
        since: '1.0.0',
        a11y: {
          role: 'textbox',
          keyboardNavigation: true,
          screenReader: true,
          trapsFocus: false,
          ariaRequired: true,
          ariaSupported: ['aria-label'],
          wcagCriteria: [],
        },
      },
    });
    const result = checkAccessibilityDeclarations(contract);
    const w = result.violations.find((x) => x.rule === 'a11y-missing-wcag-criteria');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });
});

// ============================================================================
// Privacy and review metadata
// ============================================================================

describe('checkPrivacyAndReviewMetadata', () => {
  it('passes for a contract with no sensitive props', () => {
    const contract = makeContract({
      props: [{ name: 'label', type: 'string', required: false } as ComponentContract['props'][number]],
    });
    const result = checkPrivacyAndReviewMetadata(contract);
    expect(result.passed).toBe(true);
  });

  it('errors when a pii prop does not declare reviewRequired', () => {
    const contract = makeContract({
      props: [
        {
          name: 'email',
          type: 'string',
          required: false,
          dataClassification: 'pii',
          reviewRequired: false,
        } as ComponentContract['props'][number],
      ],
    });
    const result = checkPrivacyAndReviewMetadata(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.find((x) => x.rule === 'sensitive-prop-missing-review-required')).toBeDefined();
  });

  it('warns when pii prop exists but privacy.mayRenderPii is not set', () => {
    const contract = makeContract({
      props: [
        {
          name: 'email',
          type: 'string',
          required: false,
          dataClassification: 'pii',
          reviewRequired: true,
        } as ComponentContract['props'][number],
      ],
      privacy: {
        mayRenderPii: false,
        requiresConsentFlow: false,
        regulatoryFrameworks: [],
      },
    });
    const result = checkPrivacyAndReviewMetadata(contract);
    const w = result.violations.find((x) => x.rule === 'pii-prop-missing-contract-privacy');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });

  it('warns when aiPolicy does not list review-required props', () => {
    const contract = makeContract({
      props: [
        {
          name: 'ssn',
          type: 'string',
          required: false,
          dataClassification: 'restricted',
          reviewRequired: true,
        } as ComponentContract['props'][number],
      ],
      aiPolicy: {
        allowAutonomousConfiguration: false,
        reviewRequiredProps: [],
        usageGuidance: '',
        autoApplyConfidenceThreshold: 0,
      },
    });
    const result = checkPrivacyAndReviewMetadata(contract);
    const w = result.violations.find((x) => x.rule === 'ai-policy-missing-review-props');
    expect(w).toBeDefined();
    expect(w?.severity).toBe('warning');
  });
});

// ============================================================================
// Combined check
// ============================================================================

describe('runContractQualityChecks', () => {
  it('returns passed: true for a clean minimal contract', () => {
    const contract = makeContract();
    const result = runContractQualityChecks(contract);
    // Minimal contract may have no-error violations (only warnings)
    expect(result.violations.filter((v) => v.severity === 'error')).toHaveLength(0);
  });

  it('aggregates violations from all checks', () => {
    const contract = makeContract({
      description: '',                                                 // warning
      metadata: { category: '', tags: [], since: '1.0.0' },           // error
      preview: {
        minimumTrustLevel: 'untrusted',
        requiresNetwork: true,
        requiresStorage: false,
        requiresConsent: false,
      },                                                               // error
    });
    const result = runContractQualityChecks(contract);
    expect(result.passed).toBe(false);
    expect(result.violations.length).toBeGreaterThan(1);
  });
});
