import assert from 'node:assert/strict';
import test from 'node:test';

import { validatePhrLifecyclePilot } from '../check-phr-lifecycle-pilot.mjs';

function baseKernelProduct() {
  return {
    productId: 'phr',
    lifecycleProfile: 'standard-web-api-product',
    status: 'enabled',
    executionEnabled: true,
    surfaces: {
      'backend-api': { adapter: 'gradle-java-service' },
      web: { adapter: 'pnpm-vite-react' },
    },
    phases: {
      dev: { defaultSurfaces: ['backend-api', 'web'] },
      build: { defaultSurfaces: ['backend-api', 'web'] },
    },
    gates: {
      validate: ['consent', 'pii-classification', 'audit-evidence', 'fhir-contract-validation', 'tenant-data-sovereignty'],
      build: ['consent', 'pii-classification', 'audit-evidence', 'fhir-contract-validation', 'tenant-data-sovereignty'],
      deploy: ['consent', 'tenant-data-sovereignty'],
    },
    requiredManifests: {
      build: ['lifecycle-result'],
      package: ['artifact-manifest'],
      deploy: ['deployment-manifest'],
      verify: ['verify-health-report'],
    },
    manifestSchemaVersions: {
      'lifecycle-result': '1.0.0',
      'artifact-manifest': '1.0.0',
      'lifecycle-health-snapshot': '1.0.0',
      'deployment-manifest': '1.0.0',
      'verify-health-report': '1.0.0',
    },
    package: {
      'backend-api': {
        adapter: 'docker-buildx',
        image: 'ghatana/phr-api',
        tag: 'local',
        dockerfile: 'products/phr/launcher/Dockerfile',
        context: '.',
      },
      web: {
        adapter: 'docker-buildx',
        image: 'ghatana/phr-web',
        tag: 'local',
        dockerfile: 'products/phr/apps/web/Dockerfile',
        context: '.',
      },
    },
    deployment: {
      local: {
        adapter: 'compose-local',
        composeFile: 'products/phr/deploy/local.compose.yaml',
        envExampleFile: 'products/phr/deploy/local.env.example',
        requireEnvFile: false,
        expectedServices: ['phr-api', 'phr-web'],
        healthChecks: {
          'backend-api': { type: 'http', url: 'http://localhost:8085/health/ready' },
          web: { type: 'http', url: 'http://localhost:5178/' },
        },
      },
    },
    verify: {
      local: {
        healthChecks: {
          'backend-api': { type: 'http', url: 'http://localhost:8085/health/ready' },
          web: { type: 'http', url: 'http://localhost:5178/' },
        },
      },
    },
  };
}

function runWith(kernelProduct) {
  const existingPaths = new Set([
    'products/phr/kernel-product.yaml',
    'products/phr/lifecycle/readiness-evidence.yaml',
    'products/phr/schema-packs/schema-registry.yaml',
    'products/phr/deploy/local.compose.yaml',
    'products/phr/deploy/local.env.example',
    'products/phr/launcher/Dockerfile',
    'products/phr/apps/web/Dockerfile',
    'products/phr/lifecycle/gate-packs/consent.yaml',
    'products/phr/lifecycle/gate-packs/pii-classification.yaml',
    'products/phr/lifecycle/gate-packs/audit-evidence.yaml',
    'products/phr/lifecycle/gate-packs/fhir-contract-validation.yaml',
    'products/phr/lifecycle/gate-packs/tenant-data-sovereignty.yaml',
  ]);

  return validatePhrLifecyclePilot({
    registry: {
      phr: {
        lifecycleStatus: 'enabled',
        lifecycleExecutionAllowed: true,
        lifecycle: { enabled: true },
        lifecycleConfigPath: 'products/phr/kernel-product.yaml',
      },
    },
    kernelProduct,
    pathExists: (relativePath) => existingPaths.has(relativePath),
    readText: () => 'PHR_DATABASE_PASSWORD=',
    listFiles: () => [],
  });
}

test('PHR lifecycle pilot check accepts required healthcare gates and manifests', () => {
  assert.deepEqual(runWith(baseKernelProduct()), []);
});

test('PHR lifecycle pilot check fails if consent gate is missing', () => {
  const config = baseKernelProduct();
  config.gates.validate = config.gates.validate.filter((gate) => gate !== 'consent');

  const errors = runWith(config);
  assert(errors.some((error) => error.includes('PHR validate gates missing required entry: consent')));
});

test('PHR lifecycle pilot check fails if lifecycle execution is disabled in registry', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: {
        lifecycleStatus: 'enabled',
        lifecycleExecutionAllowed: false,
        lifecycle: { enabled: true },
        lifecycleConfigPath: 'products/phr/kernel-product.yaml',
      },
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readText: () => '',
    listFiles: () => [],
  });

  assert(errors.some((error) => error.includes('lifecycleExecutionAllowed must be true')));
});
