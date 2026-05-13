import { describe, expect, it } from 'vitest';

import { validateObservabilityFlowManifest } from '../index.js';

describe('validateObservabilityFlowManifest', () => {
  it('accepts a complete typed observability flow manifest', () => {
    const result = validateObservabilityFlowManifest({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace', 'tenantContext', 'metrics', 'audit', 'safeLogging', 'redaction'],
      flows: [
        {
          product: 'phr',
          flow: 'appointment-create',
          kind: 'api',
          facets: ['trace', 'tenantContext', 'metrics', 'audit', 'safeLogging', 'redaction'],
          evidence: [
            {
              type: 'behavior',
              file: 'platform/typescript/product-conformance/src/telemetry/__tests__/telemetry.test.ts',
              requiredFacets: ['trace', 'metrics', 'audit'],
            },
          ],
        },
      ],
    });

    expect(result).toEqual({
      valid: true,
      errors: [],
      manifest: expect.objectContaining({
        schemaVersion: '1.0.0',
      }),
    });
  });

  it('reports schema and facet alignment failures', () => {
    const result = validateObservabilityFlowManifest({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace', 'audit'],
      flows: [
        {
          product: 'phr',
          flow: 'appointment-create',
          kind: 'api',
          facets: ['trace'],
          evidence: [
            {
              type: 'behavior',
              file: 'fixture.ts',
              requiredFacets: ['audit'],
            },
          ],
        },
      ],
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toEqual([
      'phr:appointment-create is missing required facet audit',
      'phr:appointment-create behavior evidence fixture.ts declares undeclared facet audit',
    ]);
  });

  it('rejects unknown facets before semantic validation', () => {
    const result = validateObservabilityFlowManifest({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace', 'debug'],
      flows: [],
    });

    expect(result.valid).toBe(false);
    expect(result.errors[0]).toContain('requiredFacets[1] Invalid option');
  });
});
