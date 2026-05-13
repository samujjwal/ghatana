import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { validateProductConformance } from '../index.js';

describe('runtime conformance validator', () => {
  it('fails closed when required manifest files are missing', () => {
    const repoRoot = mkdtempSync(join(tmpdir(), 'ghatana-conformance-missing-'));

    const result = validateProductConformance({
      repoRoot,
      productManifestPath: 'product.json',
      observabilityFlowPath: 'observability.json',
      requiredProducts: ['flashit'],
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toEqual([
      expect.stringContaining('Product manifest not found'),
      expect.stringContaining('Observability flow manifest not found'),
    ]);
  });

  it('validates canonical manifest and observability coverage without warnings', () => {
    const repoRoot = mkdtempSync(join(tmpdir(), 'ghatana-conformance-valid-'));
    writeFileSync(join(repoRoot, 'product.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'flashit',
      kind: 'business-product',
      capabilities: [],
      policies: {},
      surfaces: [],
      runtimeServices: [],
      policyActions: ['flashit:create'],
      policyResources: ['flashit:moments'],
    }));
    writeFileSync(join(repoRoot, 'observability.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace'],
      flows: [
        {
          product: 'flashit',
          flow: 'moment-read-write',
          kind: 'api',
          facets: ['trace'],
          evidence: [
            {
              type: 'behavior',
              file: 'telemetry.test.ts',
              requiredFacets: ['trace'],
            },
          ],
        },
      ],
    }));

    const result = validateProductConformance({
      repoRoot,
      productManifestPath: 'product.json',
      observabilityFlowPath: 'observability.json',
      requiredProducts: ['flashit'],
    });

    expect(result).toEqual({
      valid: true,
      errors: [],
      warnings: [],
    });
  });

  it('reports coverage and namespacing warnings as structured output', () => {
    const repoRoot = mkdtempSync(join(tmpdir(), 'ghatana-conformance-warning-'));
    writeFileSync(join(repoRoot, 'product.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      product: 'flashit',
      kind: 'business-product',
      capabilities: [],
      policies: {},
      surfaces: [],
      runtimeServices: [],
      policyActions: ['create'],
      policyResources: ['moments'],
    }));
    writeFileSync(join(repoRoot, 'observability.json'), JSON.stringify({
      schemaVersion: '1.0.0',
      requiredFacets: ['trace'],
      flows: [],
    }));

    const result = validateProductConformance({
      repoRoot,
      productManifestPath: 'product.json',
      observabilityFlowPath: 'observability.json',
      requiredProducts: ['flashit'],
    });

    expect(result.valid).toBe(true);
    expect(result.errors).toEqual([]);
    expect(result.warnings).toEqual([
      'Observability flow missing coverage for product: flashit',
      'Policy actions should be namespaced (e.g., "product:action"): create',
      'Policy resources should be namespaced (e.g., "product:resource"): moments',
    ]);
  });
});
