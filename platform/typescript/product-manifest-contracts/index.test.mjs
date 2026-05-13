import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  expectedSurfacesForShape,
  loadProductManifest,
  validatePolicyResourcesVocabulary,
  validatePolicyVocabulary,
  validateProductIdentityAlignment,
  validateProductManifestShape,
  validateRegistryReferences,
  validateSurfaceAlignment,
} from './index.mjs';

const registry = {
  kernelCapabilities: {
    'audit-trail': {},
    'tenant-context': {},
  },
  plugins: {
    'plugin-audit-trail': {},
  },
  domainPacks: {
    'flashit-boundary-policy': { owner: 'flashit' },
  },
  policyActions: {
    canonical: ['read', 'write', 'delete', 'export', 'download'],
    extensionNamespaces: ['flashit', 'finance'],
  },
  policyResources: {
    canonical: [],
    extensionNamespaces: ['flashit', 'finance'],
  },
};

const validManifest = {
  schemaVersion: '1.0.0',
  id: 'flashit',
  version: '1.0.0',
  product: 'flashit',
  kind: 'domain-pack',
  policies: {
    actions: ['read', 'flashit:reflect'],
    resources: ['flashit:moments'],
  },
  surfaces: {
    ui: ['web'],
    runtime: ['web-api'],
  },
  kernelCapabilitiesConsumed: ['audit-trail', 'tenant-context'],
  policyActions: ['read', 'flashit:reflect'],
  policyResources: ['flashit:moments'],
  pluginsConsumed: ['plugin-audit-trail'],
  bridgesConsumed: [],
  domainPacksProvided: ['flashit-boundary-policy'],
  uiSurfaces: ['web'],
  runtimeServices: ['web-api'],
  dataSensitivity: 'personal-journal',
};

describe('product manifest contracts', () => {
  it('loads canonical JSON and YAML envelopes through one normalized path', () => {
    const tempDir = mkdtempSync(path.join(tmpdir(), 'manifest-contracts-'));
    const jsonPath = path.join(tempDir, 'manifest.json');
    const yamlPath = path.join(tempDir, 'manifest.yaml');

    writeFileSync(jsonPath, JSON.stringify(validManifest), 'utf8');
    writeFileSync(
      yamlPath,
      [
        'schemaVersion: "1.0.0"',
        'id: flashit',
        'version: "1.0.0"',
        'product: flashit',
        'kind: domain-pack',
      ].join('\n'),
      'utf8',
    );

    expect(loadProductManifest(jsonPath, 'json')).toMatchObject({ id: 'flashit', product: 'flashit' });
    expect(loadProductManifest(yamlPath, 'yaml')).toMatchObject({ id: 'flashit', product: 'flashit' });
  });

  it('validates canonical manifest shape and rejects unsafe nested capability declarations', () => {
    expect(validateProductManifestShape(validManifest).success).toBe(true);

    const invalid = validateProductManifestShape({
      ...validManifest,
      capabilities: [{ id: 'flashit.reflect', name: 'Reflect' }],
    });

    expect(invalid.success).toBe(false);
  });

  it('detects identity and product-shape drift', () => {
    expect(validateProductIdentityAlignment({ product: 'flashit', manifest: validManifest })).toEqual([]);
    expect(
      validateProductIdentityAlignment({
        product: 'flashit',
        manifest: { ...validManifest, id: 'flashit-domain-pack' },
      }),
    ).toEqual(["manifest id 'flashit-domain-pack' must match the canonical product registry key 'flashit' for identity alignment"]);

    expect(expectedSurfacesForShape({ ui: true, uiMode: 'web' })).toEqual(['web']);
    expect(
      validateSurfaceAlignment({
        product: 'flashit',
        manifest: { ...validManifest, uiSurfaces: ['web', 'mobile'] },
        shape: { ui: true, uiMode: 'web', clientPackages: ['products/flashit/client/web/package.json'] },
      }),
    ).toContain('product-shape.json uiMode "web" expects uiSurfaces [web] but manifest declares [mobile, web]');
  });

  it('validates registry references and policy namespaces', () => {
    expect(validateRegistryReferences({ product: 'flashit', manifest: validManifest, registry })).toEqual([]);
    expect(validatePolicyVocabulary({ manifest: validManifest, registry })).toEqual([]);
    expect(validatePolicyResourcesVocabulary({ manifest: validManifest, registry })).toEqual([]);

    expect(validatePolicyVocabulary({ manifest: { policyActions: ['settle'] }, registry })).toEqual([
      "policyActions entry 'settle' must be canonical or namespaced as '<product>:<verb>'",
    ]);
    expect(validatePolicyResourcesVocabulary({ manifest: { policyResources: ['moments/**'] }, registry })).toEqual([
      "policyResources entry 'moments/**' must be canonical or namespaced as '<product>:<resource>'",
    ]);
  });
});
