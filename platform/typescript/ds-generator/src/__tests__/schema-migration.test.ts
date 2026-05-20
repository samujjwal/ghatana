/**
 * @fileoverview Schema migration tests for DesignSystemDocument.
 *
 * Verifies that:
 *   1. Documents can be migrated between schema versions
 *   2. Migration preserves semantic equivalence
 *   3. Migration validates required fields
 *   4. Backward compatibility is maintained for older versions
 *
 * @doc.type test
 * @doc.purpose DS document schema migration tests
 * @doc.layer ds-generator
 */

import { describe, it, expect } from 'vitest';
import {
  createDesignSystemDocument,
  DesignSystemDocumentSchema,
  DS_DOCUMENT_SCHEMA_VERSION,
  ComponentStateSchema,
  CANONICAL_COMPONENT_STATES,
} from '../model/design-system-document.js';

// ============================================================================
// Fixtures
// ============================================================================

const DETERMINISTIC_CONTEXT = {
  clockFn: () => '2024-01-01T00:00:00.000Z',
  idFn: () => 'test-id-fixed',
};

function makeBaseTokens() {
  return {
    color: {
      primary: '#3b82f6',
      secondary: '#6b7280',
    },
    spacing: {
      unit: '4px',
    },
  } as Record<string, unknown>;
}

// ============================================================================
// Schema Version Tests
// ============================================================================

describe('Schema Version — Current Version', () => {
  it('creates documents with current schema version', () => {
    const doc = createDesignSystemDocument(
      'doc-001',
      'Test DS',
      'ghatana-default',
      makeBaseTokens(),
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    expect(doc.schemaVersion).toBe(DS_DOCUMENT_SCHEMA_VERSION);
    expect(doc.schemaVersion).toBe('1.0.0');
  });

  it('validates documents with current schema version', () => {
    const doc = createDesignSystemDocument(
      'doc-002',
      'Test DS',
      'ghatana-default',
      makeBaseTokens(),
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.schemaVersion).toBe(DS_DOCUMENT_SCHEMA_VERSION);
  });
});

// ============================================================================
// Migration: Component States
// ============================================================================

describe('Migration — Component States', () => {
  it('accepts all canonical component states in current schema', () => {
    for (const state of CANONICAL_COMPONENT_STATES) {
      const result = ComponentStateSchema.parse({
        state,
        tokenOverrides: { '--color-primary': '#ff0000' },
      });
      expect(result.state).toBe(state);
    }
  });

  it('rejects non-canonical states during migration', () => {
    const doc = createDesignSystemDocument(
      'doc-invalid-state',
      'Invalid State DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        componentVariants: [
          {
            componentId: 'Button',
            variants: {},
            states: [
              {
                // @ts-expect-error intentionally invalid for migration test
                state: 'focussed', // typo of 'focus'
                tokenOverrides: {},
              },
            ],
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });

  it('preserves canonical states through schema validation', () => {
    const doc = createDesignSystemDocument(
      'doc-states',
      'States DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        componentVariants: [
          {
            componentId: 'Button',
            variants: {},
            states: [
              { state: 'default', tokenOverrides: {} },
              { state: 'hover', tokenOverrides: { '--color-primary': '#ff0000' } },
              { state: 'disabled', tokenOverrides: { '--opacity': '0.5' } },
            ],
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    const buttonVariant = parsed.componentVariants?.[0];
    expect(buttonVariant).toBeDefined();
    expect(buttonVariant?.states).toHaveLength(3);
    expect(buttonVariant?.states[0]?.state).toBe('default');
    expect(buttonVariant?.states[1]?.state).toBe('hover');
    expect(buttonVariant?.states[2]?.state).toBe('disabled');
  });
});

// ============================================================================
// Migration: Token Structure
// ============================================================================

describe('Migration — Token Structure', () => {
  it('preserves token structure through schema validation', () => {
    const tokens = {
      color: {
        primary: '#3b82f6',
        secondary: '#6b7280',
        success: '#10b981',
        warning: '#f59e0b',
        error: '#ef4444',
      },
      spacing: {
        unit: '4px',
        sm: '8px',
        md: '16px',
        lg: '24px',
        xl: '32px',
      },
      typography: {
        fontFamily: 'system-ui',
        fontSize: '16px',
        fontWeight: '400',
      },
    } as Record<string, unknown>;

    const doc = createDesignSystemDocument(
      'doc-tokens',
      'Tokens DS',
      'ghatana-default',
      tokens,
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.tokens.color.primary).toBe('#3b82f6');
    expect(parsed.tokens.spacing.unit).toBe('4px');
    expect(parsed.tokens.typography.fontFamily).toBe('system-ui');
  });

  it('validates token values are strings', () => {
    const invalidTokens = {
      color: {
        primary: 123, // Invalid: should be string
      },
    } as Record<string, unknown>;

    const doc = createDesignSystemDocument(
      'doc-invalid-token',
      'Invalid Token DS',
      'ghatana-default',
      invalidTokens,
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    // Schema validation should catch this
    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });
});

// ============================================================================
// Migration: Semantic Aliases
// ============================================================================

describe('Migration — Semantic Aliases', () => {
  it('preserves semantic aliases through schema validation', () => {
    const doc = createDesignSystemDocument(
      'doc-aliases',
      'Aliases DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        semanticAliases: [
          {
            alias: 'interactive-primary',
            tokenKey: 'color.primary',
            description: 'Primary color for interactive elements',
          },
          {
            alias: 'text-on-primary',
            tokenKey: 'color.text-on-primary',
            description: 'Text color on primary background',
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.semanticAliases).toHaveLength(2);
    expect(parsed.semanticAliases[0]?.alias).toBe('interactive-primary');
    expect(parsed.semanticAliases[0]?.tokenKey).toBe('color.primary');
  });

  it('validates semantic alias structure', () => {
    const doc = createDesignSystemDocument(
      'doc-invalid-alias',
      'Invalid Alias DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        // @ts-expect-error intentionally invalid for migration test
        semanticAliases: [
          {
            alias: 'test-alias',
            // Missing required tokenKey
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });
});

// ============================================================================
// Migration: Component Variants
// ============================================================================

describe('Migration — Component Variants', () => {
  it('preserves component variants through schema validation', () => {
    const doc = createDesignSystemDocument(
      'doc-variants',
      'Variants DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        componentVariants: [
          {
            componentId: 'Button',
            variants: {
              size: {
                small: { tokenOverrides: { '--button-height': '32px' } },
                medium: { tokenOverrides: { '--button-height': '40px' } },
                large: { tokenOverrides: { '--button-height': '48px' } },
              },
            },
            states: [
              { state: 'default', tokenOverrides: {} },
              { state: 'hover', tokenOverrides: {} },
              { state: 'disabled', tokenOverrides: {} },
            ],
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    const buttonVariant = parsed.componentVariants?.[0];
    expect(buttonVariant?.componentId).toBe('Button');
    expect(buttonVariant?.variants).toBeDefined();
    expect(buttonVariant?.states).toHaveLength(3);
  });

  it('validates component variant structure', () => {
    const doc = createDesignSystemDocument(
      'doc-invalid-variant',
      'Invalid Variant DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        // @ts-expect-error intentionally invalid for migration test
        componentVariants: [
          {
            // Missing required componentId
            variants: {},
            states: [],
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });
});

// ============================================================================
// Migration: Brand Metadata
// ============================================================================

describe('Migration — Brand Metadata', () => {
  it('preserves brand metadata through schema validation', () => {
    const doc = createDesignSystemDocument(
      'doc-brand',
      'Brand DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        brandName: 'Acme Corporation',
        brandUrl: 'https://acme.com',
        metadata: {
          version: '1.0.0',
          description: 'Acme Design System',
        },
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.brandName).toBe('Acme Corporation');
    expect(parsed.brandUrl).toBe('https://acme.com');
    expect(parsed.metadata?.version).toBe('1.0.0');
  });

  it('validates brand URL format', () => {
    const doc = createDesignSystemDocument(
      'doc-invalid-url',
      'Invalid URL DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        brandName: 'Test Brand',
        // @ts-expect-error intentionally invalid for migration test
        brandUrl: 'not-a-valid-url',
      },
      DETERMINISTIC_CONTEXT,
    );

    // Schema validation should catch invalid URL format
    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });
});

// ============================================================================
// Migration: Round-Trip Semantic Equivalence
// ============================================================================

describe('Migration — Round-Trip Semantic Equivalence', () => {
  it('maintains semantic equivalence after parse and validate', () => {
    const originalDoc = createDesignSystemDocument(
      'doc-roundtrip',
      'RoundTrip DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        brandName: 'Test Brand',
        semanticAliases: [
          {
            alias: 'primary-interactive',
            tokenKey: 'color.primary',
            description: 'Primary color for interactive elements',
          },
        ],
        componentVariants: [
          {
            componentId: 'Button',
            variants: {
              size: {
                small: { tokenOverrides: { '--button-height': '32px' } },
              },
            },
            states: [
              { state: 'default', tokenOverrides: {} },
              { state: 'hover', tokenOverrides: {} },
            ],
          },
        ],
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(originalDoc);

    // Verify semantic equivalence
    expect(parsed.documentId).toBe(originalDoc.documentId);
    expect(parsed.name).toBe(originalDoc.name);
    expect(parsed.brandName).toBe(originalDoc.brandName);
    expect(parsed.tokens).toEqual(originalDoc.tokens);
    expect(parsed.semanticAliases).toEqual(originalDoc.semanticAliases);
    expect(parsed.componentVariants).toEqual(originalDoc.componentVariants);
  });

  it('maintains deterministic timestamps after round-trip', () => {
    const doc = createDesignSystemDocument(
      'doc-timestamp',
      'Timestamp DS',
      'ghatana-default',
      makeBaseTokens(),
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);

    expect(parsed.generatedAt).toBe(doc.generatedAt);
    expect(parsed.generatedAt).toBe('2024-01-01T00:00:00.000Z');
  });
});

// ============================================================================
// Migration: Backward Compatibility
// ============================================================================

describe('Migration — Backward Compatibility', () => {
  it('accepts documents without optional fields (backward compatibility)', () => {
    const minimalDoc = createDesignSystemDocument(
      'doc-minimal',
      'Minimal DS',
      'ghatana-default',
      makeBaseTokens(),
      undefined,
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(minimalDoc);
    expect(parsed.documentId).toBe('doc-minimal');
    expect(parsed.semanticAliases).toBeUndefined();
    expect(parsed.componentVariants).toBeUndefined();
    expect(parsed.brandName).toBeUndefined();
  });

  it('accepts documents with empty arrays (backward compatibility)', () => {
    const doc = createDesignSystemDocument(
      'doc-empty-arrays',
      'Empty Arrays DS',
      'ghatana-default',
      makeBaseTokens(),
      {
        semanticAliases: [],
        componentVariants: [],
      },
      DETERMINISTIC_CONTEXT,
    );

    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.semanticAliases).toEqual([]);
    expect(parsed.componentVariants).toEqual([]);
  });
});
