import assert from 'node:assert/strict';
import test from 'node:test';

import { validateEvidencePackShape, validatePhrLifecyclePilot } from '../check-phr-lifecycle-pilot.mjs';

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
    rollbackReadiness: rollbackReadiness(),
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

function rollbackReadiness(overrides = {}) {
  return {
    status: 'target-partial',
    classification: 'target/partial',
    reasonCode: 'phr-rollback-after-stable-deploy-verify',
    requiredBeforeEnablement: [
      'stable-deployment-manifest-history',
      'previous-artifact-selection-policy',
      'healthcare-post-rollback-verification-gates',
      'rollback-approval-contract',
    ],
    ...overrides,
  };
}

function registryPhr(overrides = {}) {
  return {
    lifecycleStatus: 'enabled',
    lifecycleExecutionAllowed: true,
    lifecycle: { enabled: true },
    metadata: {
      pilot: true,
      lifecycleReadiness: {
        requiredGates: ['consent', 'pii-classification', 'audit-evidence', 'fhir-contract-validation', 'tenant-data-sovereignty'],
      },
    },
    lifecycleMigration: { readinessReasonCode: 'validated-phr-lifecycle-pilot' },
    rollbackReadiness: rollbackReadiness(),
    lifecycleConfigPath: 'products/phr/kernel-product.yaml',
    ...overrides,
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
      phr: registryPhr(),
    },
    kernelProduct,
    pathExists: (relativePath) => existingPaths.has(relativePath),
    readYaml: (relativePath) => {
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return kernelProduct;
    },
    readText: () => 'PHR_DATABASE_PASSWORD=',
    listFiles: () => [],
  });
}

function gatePack(gateId, overrides = {}) {
  const lifecyclePhases = gateId === 'consent' || gateId === 'tenant-data-sovereignty'
    ? ['validate', 'build', 'deploy']
    : ['validate', 'build'];
  return {
    schemaVersion: '1.0.0',
    productId: 'phr',
    gateId,
    title: `${gateId} gate`,
    executionMode: 'evidence-backed',
    owner: 'PHR Team',
    status: 'active',
    description: `${gateId} evidence gate`,
    requiredEvidenceRefs: ['products/phr/lifecycle/readiness-evidence.yaml'],
    blockingReasonCodes: [`requires-${gateId}`, `missing-${gateId}`],
    lifecyclePhases,
    validationCommands: ['./gradlew :products:phr:build'],
    ...overrides,
  };
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
      phr: registryPhr({ lifecycleExecutionAllowed: false }),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readText: () => '',
    listFiles: () => [],
  });

  assert(errors.some((error) => error.includes('lifecycleExecutionAllowed must be true')));
});

test('PHR lifecycle pilot check rejects stale disabled readiness metadata', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: registryPhr({
        metadata: {
          pilot: true,
          lifecycleReadiness: {
            reasonCodes: ['disabled-observed'],
            requiredGates: ['consent', 'pii-classification', 'audit-evidence', 'fhir-contract-validation', 'tenant-data-sovereignty'],
          },
        },
      }),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readYaml: (relativePath) => {
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return baseKernelProduct();
    },
    readText: () => '',
    listFiles: () => [],
  });

  assert(errors.some((error) => error.includes('stale disabled/demo code: disabled-observed')));
});

test('PHR lifecycle pilot check requires healthcare gate readiness metadata', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: registryPhr({
        metadata: {
          pilot: true,
          lifecycleReadiness: {
            requiredGates: ['consent'],
          },
        },
      }),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readYaml: (relativePath) => {
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return baseKernelProduct();
    },
    readText: () => '',
    listFiles: () => [],
  });

  assert(
    errors.some((error) =>
      error.includes('metadata.lifecycleReadiness.requiredGates missing required entry: pii-classification'),
    ),
  );
});

test('PHR lifecycle pilot check requires explicit target-partial rollback readiness', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: registryPhr({ rollbackReadiness: rollbackReadiness({ status: 'enabled' }) }),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readYaml: (relativePath) => {
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return baseKernelProduct();
    },
    readText: () => '',
    listFiles: () => [],
  });

  assert(errors.some((error) => error.includes('PHR registry rollbackReadiness.status must be target-partial')));
});

test('PHR lifecycle pilot check rejects rollback phase before readiness promotion', () => {
  const config = baseKernelProduct();
  config.phases.rollback = { defaultSurfaces: ['backend-api', 'web'] };

  const errors = runWith(config);

  assert(errors.some((error) => error.includes('rollback phase/manifests must remain absent')));
});

test('PHR lifecycle pilot check fails if a healthcare gate pack is missing evidence metadata', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: registryPhr(),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readYaml: (relativePath) => {
      if (relativePath.endsWith('consent.yaml')) {
        return gatePack('consent', { requiredEvidenceRefs: [] });
      }
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return baseKernelProduct();
    },
    readText: () => '',
    listFiles: () => [],
  });

  assert(errors.some((error) => error.includes('consent.yaml must declare requiredEvidenceRefs')));
});

test('PHR lifecycle pilot check fails if deploy gate pack omits deploy phase mapping', () => {
  const errors = validatePhrLifecyclePilot({
    registry: {
      phr: registryPhr(),
    },
    kernelProduct: baseKernelProduct(),
    pathExists: () => true,
    readYaml: (relativePath) => {
      if (relativePath.endsWith('tenant-data-sovereignty.yaml')) {
        return gatePack('tenant-data-sovereignty', { lifecyclePhases: ['validate', 'build'] });
      }
      if (relativePath.startsWith('products/phr/lifecycle/gate-packs/')) {
        const gateId = relativePath.split('/').pop().replace('.yaml', '');
        return gatePack(gateId);
      }
      return baseKernelProduct();
    },
    readText: () => '',
    listFiles: () => [],
  });

  assert(
    errors.some((error) =>
      error.includes('tenant-data-sovereignty.yaml lifecyclePhases missing required entry: deploy'),
    ),
  );
});

test('PHR evidence pack shape requires taxonomy and phase output fields', () => {
  const errors = validateEvidencePackShape('phr', {
    schemaVersion: '1.0.0',
    evidenceCategories: [
      'baseline',
      'plan',
      'validate',
      'test',
      'build',
      'package',
      'deploy',
      'verify',
      'rollback',
      'gate-results',
      'health',
      'approvals',
      'provenance',
      'product-domain-correctness',
    ],
    checks: {
      smokePhases: [
        {
          phase: 'validate',
          status: 'ok',
          runId: 'run-1',
          correlationId: 'corr-1',
          productUnitId: 'phr',
          providerMode: 'bootstrap',
          startedAt: '2026-05-19T00:00:00.000Z',
          completedAt: '2026-05-19T00:00:01.000Z',
          durationMs: 1000,
          evidenceRefs: ['.kernel/out/products/phr/validate/run-1/lifecycle-plan.json'],
        },
      ],
    },
  });

  assert.deepEqual(errors, []);
});

test('PHR evidence pack shape rejects missing evidence refs', () => {
  const errors = validateEvidencePackShape('phr', {
    schemaVersion: '1.0.0',
    evidenceCategories: ['baseline'],
    checks: {
      smokePhases: [
        {
          phase: 'validate',
          status: 'ok',
          runId: 'run-1',
          correlationId: 'corr-1',
          productUnitId: 'phr',
          providerMode: 'bootstrap',
          startedAt: '2026-05-19T00:00:00.000Z',
          completedAt: '2026-05-19T00:00:01.000Z',
          durationMs: 1000,
        },
      ],
    },
  });

  assert(errors.some((error) => error.includes('evidence categories missing required entry: plan')));
  assert(errors.some((error) => error.includes('smokePhases[0] missing evidenceRefs')));
});
