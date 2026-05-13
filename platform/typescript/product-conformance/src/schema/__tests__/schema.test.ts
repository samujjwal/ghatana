import { describe, expect, it } from 'vitest';

import {
  checkObservabilityFlow,
  checkProductManifest,
  validateObservabilityFlow,
  validateProductManifest,
} from '../index.js';

describe('schema conformance validators', () => {
  it('accepts canonical product manifest envelopes', () => {
    const manifest = validateProductManifest({
      schemaVersion: '1.0.0',
      product: 'flashit',
      kind: 'business-product',
      capabilities: [],
      policies: {},
      surfaces: [],
      runtimeServices: [],
    });

    expect(manifest.product).toBe('flashit');
    expect(checkProductManifest(manifest)).toBe(true);
  });

  it('rejects incomplete product manifests without throwing from guard helpers', () => {
    expect(checkProductManifest({ product: 'flashit' })).toBe(false);
    expect(() => validateProductManifest({ product: 'flashit' })).toThrow();
  });

  it('accepts behavior-only observability flow evidence', () => {
    const flow = validateObservabilityFlow({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace', 'audit'],
      flows: [
        {
          product: 'flashit',
          flow: 'moment-read-write',
          kind: 'api',
          facets: ['trace', 'audit'],
          evidence: [
            {
              type: 'behavior',
              file: 'platform/typescript/product-conformance/src/telemetry/__tests__/telemetry.test.ts',
              requiredFacets: ['trace', 'audit'],
            },
          ],
        },
      ],
    });

    expect(flow.flows[0]?.evidence[0]?.type).toBe('behavior');
    expect(checkObservabilityFlow(flow)).toBe(true);
  });

  it('rejects source-token observability evidence', () => {
    expect(checkObservabilityFlow({
      requiredFacets: ['trace'],
      flows: [
        {
          product: 'flashit',
          flow: 'moment-read-write',
          kind: 'api',
          facets: ['trace'],
          evidence: [
            {
              type: 'source',
              file: 'src/file.ts',
              tokens: ['trace'],
            },
          ],
        },
      ],
    })).toBe(false);
  });
});
