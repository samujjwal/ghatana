import assert from 'node:assert/strict';
import test from 'node:test';

import { checkProductLifecycleContracts } from '../check-product-lifecycle-contracts.mjs';

function registryEntry() {
  return {
    phr: {
      id: 'phr',
      lifecycleProfile: 'standard-web-api-product',
      lifecycleStatus: 'enabled',
      lifecycleConfigPath: 'products/phr/kernel-product.yaml',
      lifecycle: {
        enabled: true,
        toolchain: {
          backend: 'gradle-java-service',
          web: 'pnpm-vite-react',
        },
      },
      artifacts: {
        build: {},
      },
      deployment: {
        targets: ['compose-local'],
      },
    },
  };
}

const lifecycleProfiles = {
  'standard-web-api-product': {
    safeForDefault: true,
  },
};

const toolchains = {
  'gradle-java-service': {
    safeForDefault: true,
  },
  'pnpm-vite-react': {
    safeForDefault: true,
  },
};

function validLifecycleConfig() {
  return {
    lifecycleProfile: 'standard-web-api-product',
    requiredManifests: {
      package: ['artifact-manifest', 'lifecycle-health-snapshot'],
      deploy: ['deployment-manifest', 'lifecycle-health-snapshot'],
      verify: ['verify-health-report', 'lifecycle-health-snapshot'],
    },
    package: {
      web: { adapter: 'docker-buildx' },
    },
    deployment: {
      local: { adapter: 'compose-local' },
    },
    verify: {
      local: { adapter: 'compose-local' },
    },
  };
}

test('enabled lifecycle product with package deploy verify manifests passes', async () => {
  const result = await checkProductLifecycleContracts(
    registryEntry(),
    lifecycleProfiles,
    toolchains,
    {},
    {
      parseLifecycleConfig: async () => validLifecycleConfig(),
      runPlan: () => undefined,
    },
  );

  assert.deepEqual(result.errors, []);
});

test('enabled lifecycle product with package config but missing package manifests fails', async () => {
  const config = validLifecycleConfig();
  delete config.requiredManifests.package;

  const result = await checkProductLifecycleContracts(
    registryEntry(),
    lifecycleProfiles,
    toolchains,
    {},
    {
      parseLifecycleConfig: async () => config,
      runPlan: () => undefined,
    },
  );

  assert(
    result.errors.some((error) =>
      error.includes('enabled lifecycle package phase must declare requiredManifests.package'),
    ),
  );
});

test('enabled lifecycle product with deploy config but missing deployment manifest fails', async () => {
  const config = validLifecycleConfig();
  config.requiredManifests.deploy = ['lifecycle-health-snapshot'];

  const result = await checkProductLifecycleContracts(
    registryEntry(),
    lifecycleProfiles,
    toolchains,
    {},
    {
      parseLifecycleConfig: async () => config,
      runPlan: () => undefined,
    },
  );

  assert(result.errors.some((error) => error.includes('requiredManifests.deploy missing deployment-manifest')));
});

test('enabled lifecycle product with verify config but missing health report manifest fails', async () => {
  const config = validLifecycleConfig();
  config.requiredManifests.verify = ['lifecycle-health-snapshot'];

  const result = await checkProductLifecycleContracts(
    registryEntry(),
    lifecycleProfiles,
    toolchains,
    {},
    {
      parseLifecycleConfig: async () => config,
      runPlan: () => undefined,
    },
  );

  assert(result.errors.some((error) => error.includes('requiredManifests.verify missing verify-health-report')));
});
