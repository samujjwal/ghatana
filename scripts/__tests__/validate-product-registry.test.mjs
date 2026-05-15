import assert from 'node:assert/strict';
import test from 'node:test';

import { validateProductRegistryDocument } from '../validate-product-registry.mjs';

const schema = {
  type: 'object',
  required: ['version', 'registry'],
  properties: {
    version: { type: 'string' },
    registry: {
      type: 'object',
      additionalProperties: {
        type: 'object',
        required: ['id', 'name', 'description', 'kind', 'type', 'metadata', 'gradleModules', 'surfaces'],
        properties: {
          id: { type: 'string' },
          name: { type: 'string' },
          description: { type: 'string' },
          kind: {
            type: 'string',
            enum: ['platform-provider', 'business-product', 'shared-service', 'domain-pack', 'demo/example'],
          },
          type: { type: 'string' },
          metadata: {
            type: 'object',
            required: ['owner', 'status'],
            properties: {
              owner: { type: 'string' },
              status: { type: 'string' },
            },
          },
          gradleModules: { type: 'array', items: { type: 'string' } },
          surfaces: {
            type: 'array',
            items: {
              type: 'object',
              required: ['type', 'path', 'implementationStatus'],
              properties: {
                type: { type: 'string' },
                path: { type: 'string' },
                implementationStatus: { type: 'string' },
              },
            },
          },
        },
      },
    },
  },
};

function createRegistry() {
  return {
    version: '1.0.0',
    registry: {
      'digital-marketing': {
        id: 'digital-marketing',
        name: 'Digital Marketing',
        description: 'Pilot',
        type: 'product',
        kind: 'business-product',
        metadata: { owner: 'team', status: 'active' },
        gradleModules: [':products:digital-marketing:dm-api'],
        surfaces: [{ type: 'backend-api', path: 'products/digital-marketing/dm-api', implementationStatus: 'implemented' }],
        conformance: {
          bridge: true,
          bridgeAdapters: [{ file: 'bridge.java', tests: [{ file: 'bridge.test.java' }] }],
        },
        lifecycleStatus: 'enabled',
        lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml',
        lifecycle: { enabled: true },
      },
      phr: {
        id: 'phr',
        name: 'PHR',
        description: 'Planned',
        type: 'product',
        kind: 'business-product',
        metadata: { owner: 'team', status: 'active' },
        gradleModules: [':products:phr'],
        surfaces: [{ type: 'backend-api', path: 'products/phr', implementationStatus: 'implemented' }],
        lifecycleStatus: 'planned',
        lifecycleConfigPath: 'products/phr/kernel-product.yaml',
        lifecycle: { enabled: false },
        lifecycleReadiness: { requiredGates: ['consent'] },
      },
      'data-cloud': {
        id: 'data-cloud',
        name: 'Data Cloud',
        description: 'Provider',
        type: 'product',
        kind: 'platform-provider',
        metadata: { owner: 'team', status: 'active' },
        gradleModules: [':products:data-cloud:delivery:api'],
        surfaces: [{ type: 'backend-api', path: 'products/data-cloud/delivery/api', implementationStatus: 'implemented' }],
        lifecycleReadiness: { requiredGates: ['runtime-truth-provider'] },
      },
    },
  };
}

test('valid registry passes', () => {
  const issues = validateProductRegistryDocument(createRegistry(), {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: () => true,
    yamlReader: (relativePath) => ({
      executionEnabled: relativePath.includes('digital-marketing') ? undefined : false,
      readiness: { requiredGates: relativePath.includes('phr') ? ['consent'] : [] },
    }),
    runArtifactCheck: () => 'ok',
  });

  assert.deepEqual(issues, []);
});

test('bridge adapter test file missing fails', () => {
  const issues = validateProductRegistryDocument(createRegistry(), {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: (relativePath) => relativePath !== 'bridge.test.java',
    yamlReader: () => ({ executionEnabled: false, readiness: { requiredGates: ['consent'] } }),
    runArtifactCheck: () => 'ok',
  });

  assert(issues.some((problem) => problem.message.includes('bridge adapter test file')));
});

test('enabled mismatch fails', () => {
  const registry = createRegistry();
  registry.registry['digital-marketing'].lifecycleStatus = 'planned';

  const issues = validateProductRegistryDocument(registry, {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: () => true,
    yamlReader: () => ({ executionEnabled: false, readiness: { requiredGates: ['consent'] } }),
    runArtifactCheck: () => 'ok',
  });

  assert(issues.some((problem) => problem.message.includes('lifecycle.enabled=true requires lifecycleStatus=enabled')));
});

test('platform provider enabled fails', () => {
  const registry = createRegistry();
  registry.registry['data-cloud'].lifecycleStatus = 'enabled';
  registry.registry['data-cloud'].lifecycle = { enabled: true };

  const issues = validateProductRegistryDocument(registry, {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: () => true,
    yamlReader: () => ({ executionEnabled: false, readiness: { requiredGates: ['consent'] } }),
    runArtifactCheck: () => 'ok',
  });

  assert(issues.some((problem) => problem.message.includes('must not be treated as ordinary lifecycle-enabled products')));
});

test('disabled product yaml must remain fail closed', () => {
  const issues = validateProductRegistryDocument(createRegistry(), {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: () => true,
    yamlReader: (relativePath) => ({
      executionEnabled: relativePath.includes('phr') ? true : false,
      readiness: { requiredGates: [] },
    }),
    runArtifactCheck: () => 'ok',
  });

  assert(issues.some((problem) => problem.message.includes('must set executionEnabled: false')));
});

test('schema violation fails on unknown kind', () => {
  const registry = createRegistry();
  registry.registry.phr.kind = 'unknown-kind';

  const issues = validateProductRegistryDocument(registry, {
    schema,
    generatedIncludes: 'include(":products:digital-marketing:dm-api")\ninclude(":products:phr")\ninclude(":products:data-cloud:delivery:api")',
    pnpmWorkspace: '',
    pathExists: () => true,
    yamlReader: () => ({ executionEnabled: false, readiness: { requiredGates: ['consent'] } }),
    runArtifactCheck: () => 'ok',
  });

  assert(issues.some((problem) => problem.message.includes('schema violation')));
});