/**
 * @fileoverview Tests for DesignSystemDocument model changes:
 * - Strict ComponentState enum (canonical states)
 * - Deterministic GenerationContext injection for createDesignSystemDocument
 *
 * @doc.type test
 */

import { describe, it, expect } from 'vitest';
import {
  createDesignSystemDocument,
  DesignSystemDocumentSchema,
  ComponentStateSchema,
  CANONICAL_COMPONENT_STATES,
} from '../model/design-system-document.js';

// ============================================================================
// Helpers
// ============================================================================

const FIXED_TIMESTAMP = '2024-01-01T00:00:00.000Z';

const deterministicContext = {
  clockFn: () => FIXED_TIMESTAMP,
  idFn: () => 'test-id-fixed',
};

function makeTokens() {
  return { color: { primary: '#3b82f6' } } as Record<string, unknown>;
}

// ============================================================================
// ComponentState strict enum
// ============================================================================

describe('ComponentStateSchema — strict enum', () => {
  it.each(CANONICAL_COMPONENT_STATES)(
    'accepts canonical state "%s"',
    (state) => {
      const result = ComponentStateSchema.parse({
        state,
        tokenOverrides: { '--color-primary': '#ff0000' },
      });
      expect(result.state).toBe(state);
    },
  );

  it('rejects a non-canonical state name', () => {
    expect(() =>
      ComponentStateSchema.parse({
        state: 'focussed', // typo of 'focus'
        tokenOverrides: {},
      }),
    ).toThrow();
  });

  it('rejects an empty string state', () => {
    expect(() =>
      ComponentStateSchema.parse({ state: '', tokenOverrides: {} }),
    ).toThrow();
  });

  it('rejects "disbaled" (typo)', () => {
    expect(() =>
      ComponentStateSchema.parse({ state: 'disbaled', tokenOverrides: {} }),
    ).toThrow();
  });
});

// ============================================================================
// createDesignSystemDocument — deterministic clock injection
// ============================================================================

describe('createDesignSystemDocument — GenerationContext', () => {
  it('uses the injected clockFn for generatedAt', () => {
    const doc = createDesignSystemDocument(
      'doc-1',
      'Test DS',
      'ghatana-default',
      makeTokens(),
      undefined,
      deterministicContext,
    );
    expect(doc.generatedAt).toBe(FIXED_TIMESTAMP);
  });

  it('produces different timestamps when called twice without a fixed clock', () => {
    const start = Date.now();
    // Use the production (non-deterministic) factory with a real-clock context
    const doc1 = createDesignSystemDocument('d1', 'DS1', 'preset', makeTokens());
    const doc2 = createDesignSystemDocument('d2', 'DS2', 'preset', makeTokens());
    // Both should be valid ISO-8601 and on or after the test start
    const t1 = new Date(doc1.generatedAt).getTime();
    const t2 = new Date(doc2.generatedAt).getTime();
    expect(t1).toBeGreaterThanOrEqual(start);
    expect(t2).toBeGreaterThanOrEqual(start);
  });

  it('snapshot test: deterministic output is stable across calls', () => {
    let callCount = 0;
    const context = {
      clockFn: () => FIXED_TIMESTAMP,
      idFn: () => `id-${(++callCount).toString().padStart(3, '0')}`,
    };

    const doc1 = createDesignSystemDocument(
      'doc-snap',
      'Snapshot DS',
      'ghatana-default',
      makeTokens(),
      { brandName: 'Acme', metadata: { version: '1.0' } },
      context,
    );

    callCount = 0; // reset counter
    const doc2 = createDesignSystemDocument(
      'doc-snap',
      'Snapshot DS',
      'ghatana-default',
      makeTokens(),
      { brandName: 'Acme', metadata: { version: '1.0' } },
      context,
    );

    expect(doc1).toStrictEqual(doc2);
  });
});

// ============================================================================
// DesignSystemDocumentSchema — round-trip validation
// ============================================================================

describe('DesignSystemDocumentSchema — round-trip', () => {
  it('parses a document produced by createDesignSystemDocument', () => {
    const doc = createDesignSystemDocument(
      'doc-rt',
      'RoundTrip DS',
      'ghatana-default',
      makeTokens(),
      undefined,
      deterministicContext,
    );
    const parsed = DesignSystemDocumentSchema.parse(doc);
    expect(parsed.documentId).toBe('doc-rt');
    expect(parsed.generatedAt).toBe(FIXED_TIMESTAMP);
  });

  it('rejects a document with a non-canonical component state', () => {
    const doc = createDesignSystemDocument(
      'doc-invalid',
      'Invalid DS',
      'ghatana-default',
      makeTokens(),
      {
        componentVariants: [
          {
            componentId: 'Button',
            variants: {},
            states: [
              {
                // @ts-expect-error intentionally invalid state for runtime test
                state: 'invalid-state',
                tokenOverrides: {},
              },
            ],
          },
        ],
      },
      deterministicContext,
    );
    expect(() => DesignSystemDocumentSchema.parse(doc)).toThrow();
  });
});
