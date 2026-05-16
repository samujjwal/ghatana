import { describe, it, expect, vi } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as url from 'node:url';
import {
  ProductLifecyclePlanner,
  type ProductLifecyclePlannerProviderContext,
} from '../planning/ProductLifecyclePlanner.js';
import { SurfaceSelector } from '../planning/SurfaceSelector.js';
import { LifecycleProfileResolver } from '../planning/LifecycleProfileResolver.js';
import { GateResolver } from '../planning/GateResolver.js';
import { ArtifactResolver } from '../planning/ArtifactResolver.js';
import { EnvironmentResolver } from '../planning/EnvironmentResolver.js';
import { ToolchainResolver } from '../planning/ToolchainResolver.js';
import { ProductSurface, ProductLifecyclePhase, ProductSurfaceType } from '../domain/ProductLifecyclePhase.js';
import { createExecutableProductUnit } from '@ghatana/kernel-product-contracts';

const REPO_ROOT = path.join(path.dirname(url.fileURLToPath(import.meta.url)), '../../../../..');

function createPlatformProviderContext(): ProductLifecyclePlannerProviderContext {
  return {
    mode: 'platform',
    artifacts: {
      providerId: 'platform-artifacts',
      version: '1.0.0',
      capabilities: ['artifact-manifests'],
      recordArtifactManifest: async () => ({ success: true, ref: 'artifact-manifest.json' }),
      listArtifactManifests: async () => [],
    },
  };
}

function createFullPlatformProviderContext(): ProductLifecyclePlannerProviderContext {
  const productUnit = createExecutableProductUnit({
    id: 'digital-marketing',
    name: 'Digital Marketing',
    kind: 'business-product',
    scope: {
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
    },
    lifecycleProfile: 'standard-web-api-product',
    lifecycleStatus: 'enabled',
    registryProviderRef: { providerId: 'platform-registry' },
    sourceProviderRef: {
      providerId: 'platform-source',
      config: { lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml' },
    },
    surfaces: [
      {
        id: 'web',
        type: 'web',
        implementationStatus: 'implemented',
        sourceRef: 'products/digital-marketing/ui',
      },
    ],
  });
  return {
    ...createPlatformProviderContext(),
    registryProvider: {
      providerId: 'platform-registry',
      version: '1.0.0',
      capabilities: ['product-units'],
      getProductUnit: async () => productUnit,
      listProductUnits: async () => [productUnit],
      listProductUnitsByKind: async () => [productUnit],
      validateProductUnit: async () => ({ valid: true, errors: [] }),
    },
    sourceProvider: {
      providerId: 'platform-source',
      version: '1.0.0',
      capabilities: ['source'],
      getSource: async () => ({ sourceRef: 'ref', path: 'products/digital-marketing/ui' }),
      listSources: async () => [],
    },
    events: {
      providerId: 'platform-events',
      version: '1.0.0',
      capabilities: ['events'],
      appendEvent: async () => ({ success: true, ref: 'events.json' }),
      listEvents: async () => [],
    },
    health: {
      providerId: 'platform-health',
      version: '1.0.0',
      capabilities: ['health'],
      recordHealthSnapshot: async () => ({ success: true, ref: 'health.json' }),
      getLatestHealthSnapshot: async () => null,
    },
    approvals: {
      providerId: 'platform-approvals',
      version: '1.0.0',
      capabilities: ['approvals'],
      requestLifecycleApproval: async () => ({ success: true, ref: 'approval.json' }),
      decideLifecycleApproval: async () => ({ success: true, ref: 'approval.json' }),
    },
    provenance: {
      providerId: 'platform-provenance',
      version: '1.0.0',
      capabilities: ['provenance'],
      recordProvenance: async () => ({ success: true, ref: 'provenance.json' }),
      listProvenance: async () => [],
    },
    runtimeTruth: {
      providerId: 'platform-runtime-truth',
      version: '1.0.0',
      capabilities: ['runtime-truth'],
      recordRuntimeTruth: async () => ({ success: true, ref: 'runtime-truth.json' }),
      getRuntimeTruth: async () => null,
    },
  };
}

describe('ProductLifecyclePlanner', () => {
  it('should load product configuration', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const config = await planner.loadProductConfig('digital-marketing');

    expect(config.productId).toBe('digital-marketing');
    expect(config.lifecycleProfile).toBe('standard-web-api-product');
    expect(config.surfaces).toHaveProperty('backend-api');
    expect(config.surfaces).toHaveProperty('web');
  });

  it('should plan build phase for digital-marketing', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'build');

    expect(plan.productId).toBe('digital-marketing');
    expect(plan.phase).toBe('build');
    expect(plan.providerMode).toBe('bootstrap');
    expect(plan.requiredManifests).toEqual([
      'lifecycle-result',
      'artifact-manifest',
      'lifecycle-health-snapshot',
    ]);
    expect(plan.requiredPlugins).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ pluginId: 'audit', required: true }),
        expect.objectContaining({ pluginId: 'preview-security', required: false }),
      ]),
    );
    expect(plan.expectedArtifacts.map((artifact) => artifact.type)).toEqual([
      'jvm-service',
      'static-web-bundle',
    ]);
    expect(plan.surfaces.length).toBeGreaterThan(0);
    expect(plan.steps.length).toBeGreaterThan(0);
  });

  it('should fail closed when platform mode has no platform provider context', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);

    await expect(
      planner.plan('digital-marketing', 'build', { providerMode: 'platform' }),
    ).rejects.toThrow('Kernel platform provider mode requires a provider context');
  });

  it('should include platform provider refs and semantic artifact refs in platform mode', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT, undefined, createFullPlatformProviderContext());
    const plan = await planner.plan('digital-marketing', 'build', { providerMode: 'platform' });

    expect(plan.providerMode).toBe('platform');
    expect(plan.productUnitRef).toBe('digital-marketing');
    expect(plan.providerRefs).toMatchObject({
      registryProviderId: 'platform-registry',
      sourceProviderId: 'platform-source',
      eventProviderId: 'platform-events',
      artifactProviderId: 'platform-artifacts',
      healthProviderId: 'platform-health',
      approvalProviderId: 'platform-approvals',
      provenanceProviderId: 'platform-provenance',
      runtimeTruthProviderId: 'platform-runtime-truth',
    });
    expect(plan.providerRefs?.artifactProviderId).toBe('platform-artifacts');
    expect(plan.expectedArtifacts[0]).toMatchObject({
      providerId: 'platform-artifacts',
      semanticRef: 'artifact://digital-marketing/build/backend-api/jvm-service',
    });
  });

  it('should fall back to file planning when registry provider lookup fails', async () => {
    const warn = vi.fn();
    const context: ProductLifecyclePlannerProviderContext = {
      ...createFullPlatformProviderContext(),
      registryProvider: {
        providerId: 'failing-registry',
        version: '1.0.0',
        capabilities: ['product-units'],
        getProductUnit: async () => {
          throw new Error('registry unavailable');
        },
        listProductUnits: async () => [],
        listProductUnitsByKind: async () => [],
        validateProductUnit: async () => ({ valid: false, errors: ['registry unavailable'] }),
      },
    };
    const planner = new ProductLifecyclePlanner(REPO_ROOT, undefined, context, { warn });

    await expect(planner.plan('digital-marketing', 'build')).resolves.toMatchObject({
      productId: 'digital-marketing',
      providerMode: 'platform',
    });
    expect(warn).toHaveBeenCalledWith(
      'Provider lookup failed; falling back to file-based ProductUnit validation',
      expect.objectContaining({ reasonCode: 'registry-provider-fallback' }),
    );
  });

  it('should plan package phase through package adapters', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'package');

    expect(plan.phase).toBe('package');
    expect(plan.steps.map((step) => step.adapter)).toEqual(['docker-buildx', 'docker-buildx']);
    expect(plan.steps.every((step) => step.stepKind === 'package')).toBe(true);
    expect(plan.requiredManifests).toEqual(['artifact-manifest', 'lifecycle-health-snapshot']);
  });

  it('should plan deploy phase through deployment adapter routing', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'deploy');

    expect(plan.phase).toBe('deploy');
    expect(plan.environment).toBeUndefined();
    expect(plan.steps).toHaveLength(1);
    expect(plan.steps[0]).toMatchObject({
      stepKind: 'deploy',
      adapter: 'compose-local',
      surface: 'local',
    });
    expect(plan.requiredManifests).toEqual(['deployment-manifest', 'lifecycle-health-snapshot']);
  });

  it('should plan dev phase in parallel mode', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);
    const plan = await planner.plan('digital-marketing', 'dev');

    expect(plan.surfaces.length).toBeGreaterThan(0);
  });

  it('should throw error for unknown product', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT);

    await expect(planner.loadProductConfig('unknown-product')).rejects.toThrow(
      'not found in the canonical registry',
    );
  });

  it('should block planned and partial adapters unless shape-only planning is requested', async () => {
    const repoRoot = await createPlannerFixtureRepo();
    const planner = new ProductLifecyclePlanner(repoRoot);

    await expect(planner.plan('shape-product', 'test')).rejects.toThrow(
      'cannot be used for executable planning',
    );

    await expect(planner.plan('shape-product', 'test', { shapeOnly: true })).resolves.toMatchObject({
      productId: 'shape-product',
      steps: [expect.objectContaining({ adapter: 'vitest' })],
    });
  });

  it('should block non-execution adapter readiness and lifecycle-disabled adapters for executable planning', async () => {
    const nonExecutionReadinessPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        adapters: {
          vitest: {
            supportedSurfaceTypes: ['web'],
            supportedPhases: ['test'],
            status: 'implemented',
            safeForDefault: true,
            readiness: 'planning-only',
            lifecycleEnabled: true,
          },
        },
      }),
    );

    await expect(nonExecutionReadinessPlanner.plan('shape-product', 'test')).rejects.toThrow(
      'has readiness "planning-only"',
    );

    const lifecycleDisabledPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        adapters: {
          vitest: {
            supportedSurfaceTypes: ['web'],
            supportedPhases: ['test'],
            status: 'implemented',
            safeForDefault: true,
            readiness: 'execution-ready',
            lifecycleEnabled: false,
          },
        },
      }),
    );

    await expect(lifecycleDisabledPlanner.plan('shape-product', 'test')).rejects.toThrow(
      'is not lifecycleEnabled',
    );

    await expect(nonExecutionReadinessPlanner.plan('shape-product', 'test', { shapeOnly: true })).resolves.toMatchObject({
      productId: 'shape-product',
      steps: [expect.objectContaining({ adapter: 'vitest' })],
    });
  });

  it('should reject bootstrap context when platform mode is requested', async () => {
    const planner = new ProductLifecyclePlanner(REPO_ROOT, undefined, {
      ...createPlatformProviderContext(),
      mode: 'bootstrap',
    });

    await expect(planner.plan('digital-marketing', 'build', { providerMode: 'platform' })).rejects.toThrow(
      'requires a platform provider context; received bootstrap',
    );
  });

  it('should resolve approval requirements from config and deployment environments', async () => {
    const repoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'allowExperimentalAdapters: false',
        'requiredManifests:',
        '  deploy: [deployment-manifest]',
        'plugins:',
        '  audit:',
        '    required: true',
        '    providerId: audit-provider',
        'environments:',
        '  prod:',
        '    approvalRequired: true',
        'approvals:',
        '  deploy:',
        '    - action: deploy:prod',
        '      riskLevel: critical',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: pnpm-vite-react',
        '    path: products/shape-product/web',
        '    implementationStatus: implemented',
        'phases:',
        '  deploy:',
        '    defaultEnvironment: prod',
        'deployment:',
        '  prod:',
        '    adapter: compose-local',
        '    target: compose-local',
      ],
      adapters: {
        'compose-local': {
          supportedSurfaceTypes: ['backend-api', 'web'],
          supportedPhases: ['deploy'],
          status: 'implemented',
          safeForDefault: true,
        },
      },
    });
    const planner = new ProductLifecyclePlanner(repoRoot);
    const plan = await planner.plan('shape-product', 'deploy', { environment: 'prod' });

    expect(plan.requiredManifests).toEqual(['deployment-manifest']);
    expect(plan.requiredPlugins).toEqual([
      { pluginId: 'audit', required: true, providerId: 'audit-provider' },
    ]);
    expect(plan.approvalRequirements).toEqual([
      {
        approvalId: 'deploy-deploy:prod',
        action: 'deploy:prod',
        riskLevel: 'critical',
        required: true,
        source: 'kernel-product.approvals',
      },
      {
        approvalId: 'deploy-prod-approval',
        action: 'deploy:prod',
        riskLevel: 'high',
        required: true,
        source: 'kernel-product.environments.prod',
      },
    ]);
  });

  it('should fail closed for disabled, excluded, missing config, and missing profile states', async () => {
    const disabledRepoRoot = await createPlannerFixtureRepo({
      registryProduct: {
        lifecycleStatus: 'planned',
        lifecycleConfigPath: 'products/shape-product/kernel-product.yaml',
        lifecycle: { enabled: false },
      },
    });
    await expect(new ProductLifecyclePlanner(disabledRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'does not have lifecycle execution enabled',
    );

    const missingPathRepoRoot = await createPlannerFixtureRepo({
      registryProduct: {
        lifecycleStatus: 'enabled',
        lifecycle: { enabled: true },
      },
    });
    await expect(new ProductLifecyclePlanner(missingPathRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'does not declare lifecycleConfigPath',
    );

    const excludedRepoRoot = await createPlannerFixtureRepo({
      exclusions: { 'shape-product': { reason: 'test' } },
    });
    await expect(new ProductLifecyclePlanner(excludedRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'is excluded from Kernel lifecycle execution',
    );

    const missingProfileRepoRoot = await createPlannerFixtureRepo({ profiles: {} });
    await expect(new ProductLifecyclePlanner(missingProfileRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'Lifecycle profile "shape-only-profile" not found',
    );

    const missingFileRepoRoot = await createPlannerFixtureRepo();
    await fs.rm(path.join(missingFileRepoRoot, 'products', 'shape-product', 'kernel-product.yaml'));
    await expect(new ProductLifecyclePlanner(missingFileRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'kernel-product.yaml not found',
    );

    const malformedYamlRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        'phases: [',
      ],
    });
    await expect(new ProductLifecyclePlanner(malformedYamlRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'Invalid YAML',
    );

    const missingSurfacesRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
        '    mode: sequential',
      ],
    });
    await expect(new ProductLifecyclePlanner(missingSurfacesRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'Expected object property "surfaces"',
    );

    const invalidSurfaceEntryRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web: []',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidSurfaceEntryRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare surfaces.web as an object',
    );

    const invalidSurfaceAdapterRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: ""',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidSurfaceAdapterRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare surfaces.web.adapter as a non-empty string',
    );

    const invalidPhaseModeRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
        '    mode: random',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidPhaseModeRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare phases.test.mode as one of sequential|parallel|dag',
    );

    const invalidPhaseDefaultSurfacesRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: web',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidPhaseDefaultSurfacesRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare phases.test.defaultSurfaces as a non-empty string array',
    );

    const invalidPackageEntryRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  package:',
        '    defaultSurfaces: [web]',
        'package:',
        '  web: []',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidPackageEntryRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare package.web as an object',
    );

    const invalidPackageAdapterRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  package:',
        '    defaultSurfaces: [web]',
        'package:',
        '  web:',
        '    adapter: ""',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidPackageAdapterRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare package.web.adapter as a non-empty string',
    );

    const invalidDeploymentEntryRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  deploy:',
        '    defaultEnvironment: local',
        'deployment:',
        '  local: []',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidDeploymentEntryRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare deployment.local as an object',
    );

    const invalidDeploymentAdapterRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  deploy:',
        '    defaultEnvironment: local',
        'deployment:',
        '  local:',
        '    adapter: ""',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidDeploymentAdapterRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare deployment.local.adapter as a non-empty string',
    );

    const invalidRequiredManifestsRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'requiredManifests:',
        '  test: [not-a-manifest-type]',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidRequiredManifestsRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare requiredManifests.test as an array of valid manifest types',
    );

    const invalidPluginsRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'plugins:',
        '  audit: []',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidPluginsRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare plugins.audit as an object',
    );

    const invalidEnvironmentsRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'environments:',
        '  prod:',
        '    approvalRequired: yes',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidEnvironmentsRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare environments.prod.approvalRequired as a boolean',
    );

    const invalidApprovalsRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'approvals:',
        '  deploy:',
        '    - action: ""',
        '      riskLevel: medium',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  deploy:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidApprovalsRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare approvals.deploy.action as a non-empty string',
    );

    const invalidGatesRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'gates:',
        '  test: gate-security',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  test:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidGatesRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare gates.test as a non-empty string array',
    );

    const invalidArtifactsRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'artifacts:',
        '  build:',
        '    web: []',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  build:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidArtifactsRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare artifacts.build.web as an object',
    );

    const invalidArtifactTypeRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'artifacts:',
        '  build:',
        '    web:',
        '      type: ""',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  build:',
        '    defaultSurfaces: [web]',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidArtifactTypeRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare artifacts.build.web.type as a non-empty string',
    );

    const invalidVerifyEntryRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'verify:',
        '  local: []',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  verify:',
        '    defaultEnvironment: local',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidVerifyEntryRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare verify.local as an object',
    );

    const invalidVerifyHealthChecksRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'verify:',
        '  local:',
        '    adapter: compose-local',
        '    healthChecks: []',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  verify:',
        '    defaultEnvironment: local',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidVerifyHealthChecksRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare verify.local.healthChecks as an object',
    );

    const invalidVerifyHttpHealthCheckRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'verify:',
        '  local:',
        '    adapter: compose-local',
        '    healthChecks:',
        '      api:',
        '        type: http',
        '        retries: 2',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  verify:',
        '    defaultEnvironment: local',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidVerifyHttpHealthCheckRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare verify.local.healthChecks.api.url or verify.local.healthChecks.api.host for http checks',
    );

    const invalidVerifyTcpHealthCheckRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'verify:',
        '  local:',
        '    adapter: compose-local',
        '    healthChecks:',
        '      worker:',
        '        type: tcp',
        '        host: localhost',
        '        port: 70000',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  verify:',
        '    defaultEnvironment: local',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidVerifyTcpHealthCheckRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare verify.local.healthChecks.worker.port as an integer between 1 and 65535',
    );

    const invalidVerifyTimeoutRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'verify:',
        '  local:',
        '    adapter: compose-local',
        '    healthChecks:',
        '      api:',
        '        type: http',
        '        url: http://localhost:8080/health',
        '        timeoutMs: 0',
        'surfaces:',
        '  web:',
        '    type: web',
        '    adapter: vitest',
        '    path: products/shape-product/web',
        'phases:',
        '  verify:',
        '    defaultEnvironment: local',
      ],
    });
    await expect(new ProductLifecyclePlanner(invalidVerifyTimeoutRepoRoot).loadProductConfig('shape-product')).rejects.toThrow(
      'must declare verify.local.healthChecks.api.timeoutMs as a positive integer',
    );

    const invalidProfileShapeRepoRoot = await createPlannerFixtureRepo({
      profiles: {
        'shape-only-profile': {
          defaultSurfaces: {
            test: 'web',
          },
          requiredGates: {},
          defaultAdapters: {},
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidProfileShapeRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare defaultSurfaces.test as a non-empty string array',
    );

    const invalidRequiredGatesProfileRepoRoot = await createPlannerFixtureRepo({
      profiles: {
        'shape-only-profile': {
          defaultSurfaces: {
            test: ['web'],
          },
          requiredGates: {
            test: 'security-scan',
          },
          defaultAdapters: {},
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidRequiredGatesProfileRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare requiredGates.test as a non-empty string array',
    );

    const invalidDefaultAdaptersProfileRepoRoot = await createPlannerFixtureRepo({
      profiles: {
        'shape-only-profile': {
          defaultSurfaces: {
            test: ['web'],
          },
          requiredGates: {},
          defaultAdapters: {
            web: ['pnpm-vite-react'],
          },
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidDefaultAdaptersProfileRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare defaultAdapters.web as a non-empty string',
    );

    const invalidToolchainShapeRepoRoot = await createPlannerFixtureRepo({
      adapters: {
        vitest: {
          supportedSurfaceTypes: ['web'],
          supportedPhases: 'test',
          status: 'implemented',
          safeForDefault: true,
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidToolchainShapeRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare supportedPhases as a non-empty string array',
    );

    const invalidToolchainReadinessRepoRoot = await createPlannerFixtureRepo({
      adapters: {
        vitest: {
          supportedSurfaceTypes: ['web'],
          supportedPhases: ['test'],
          status: 'implemented',
          safeForDefault: true,
          lifecycleEnabled: true,
          readiness: 'invalid-readiness',
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidToolchainReadinessRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare readiness as one of execution-ready|production-ready|planning-only|declared-only',
    );

    const invalidToolchainLifecycleEnabledRepoRoot = await createPlannerFixtureRepo({
      adapters: {
        vitest: {
          supportedSurfaceTypes: ['web'],
          supportedPhases: ['test'],
          status: 'implemented',
          safeForDefault: true,
          lifecycleEnabled: 'yes',
          readiness: 'execution-ready',
        } as unknown as Record<string, unknown>,
      },
    });
    await expect(new ProductLifecyclePlanner(invalidToolchainLifecycleEnabledRepoRoot).plan('shape-product', 'test')).rejects.toThrow(
      'must declare lifecycleEnabled as a boolean',
    );
  });

  it('should fail closed for unsupported phases, unknown adapters, unsafe adapters, and missing selections', async () => {
    const unsupportedPhasePlanner = new ProductLifecyclePlanner(await createPlannerFixtureRepo());
    await expect(unsupportedPhasePlanner.plan('shape-product', 'release')).rejects.toThrow('is not yet supported');

    const unknownAdapterPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: missing-adapter',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  test:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(unknownAdapterPlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'Unknown adapter "missing-adapter"',
    );

    const unsafePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: false',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  test:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
        adapters: {
          vitest: {
            supportedSurfaceTypes: ['web'],
            supportedPhases: ['test'],
            status: 'implemented',
            safeForDefault: false,
          },
        },
      }),
    );
    await expect(unsafePlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'is not marked safeForDefault',
    );

    const missingSurfacePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces: {}',
          'phases:',
          '  test:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(missingSurfacePlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'Surface "web" declared for phase "test" is not defined',
    );

    const missingAdapterPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces:',
          '  web:',
          '    type: web',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  test:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(missingAdapterPlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'does not declare an adapter',
    );

    const surfaceTypeMismatchPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces:',
          '  web:',
          '    type: backend-api',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  test:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(surfaceTypeMismatchPlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'does not support surface type "backend-api"',
    );

    const unsupportedAdapterPhasePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  build:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(unsupportedAdapterPhasePlanner.plan('shape-product', 'build', { shapeOnly: true })).rejects.toThrow(
      'does not support phase "build"',
    );

    const missingProfileSurfacePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases: {}',
        ],
        profiles: {
          'shape-only-profile': {
            defaultSurfaces: {},
            requiredGates: {},
            defaultAdapters: {},
          },
        },
      }),
    );
    await expect(missingProfileSurfacePlanner.plan('shape-product', 'test', { shapeOnly: true })).rejects.toThrow(
      'is not defined in product phases',
    );
  });

  it('should fail closed for missing package and deployment routing config', async () => {
    const missingPackagePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  package:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
        ],
      }),
    );
    await expect(missingPackagePlanner.plan('shape-product', 'package', { shapeOnly: true })).rejects.toThrow(
      'does not declare a "package" section',
    );

    const missingPackageSurfacePlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'allowExperimentalAdapters: true',
          'surfaces:',
          '  web:',
          '    type: web',
          '    adapter: vitest',
          '    path: products/shape-product/web',
          '    implementationStatus: implemented',
          'phases:',
          '  package:',
          '    defaultSurfaces: [web]',
          '    mode: sequential',
          'package:',
          '  backend-api:',
          '    adapter: vitest',
          '    image: test',
        ],
      }),
    );
    await expect(missingPackageSurfacePlanner.plan('shape-product', 'package', { shapeOnly: true })).rejects.toThrow(
      'has no entry under "package"',
    );

    const missingDeploymentPlanner = new ProductLifecyclePlanner(
      await createPlannerFixtureRepo({
        kernelProductYamlLines: [
          'productId: shape-product',
          'lifecycleProfile: shape-only-profile',
          'surfaces:',
          '  backend-api:',
          '    type: backend-api',
          '    adapter: compose-local',
          '    path: products/shape-product/backend-api',
          '    implementationStatus: implemented',
          'phases:',
          '  deploy:',
          '    defaultEnvironment: local',
        ],
        adapters: {
          'compose-local': {
            supportedSurfaceTypes: ['backend-api'],
            supportedPhases: ['deploy'],
            status: 'implemented',
            safeForDefault: true,
          },
        },
      }),
    );
    await expect(missingDeploymentPlanner.plan('shape-product', 'deploy')).rejects.toThrow(
      'does not have a deployment config',
    );
  });

  it('should use profile fallback phases, inferred surface types, output overrides, and default artifacts', async () => {
    const repoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'allowExperimentalAdapters: true',
        'surfaces:',
        '  backend-api:',
        '    runtime: java-service',
        '    adapter: gradle-java-service',
        '    path: products/shape-product/backend',
        '    implementationStatus: implemented',
        '  web:',
        '    runtime: static-web',
        '    adapter: pnpm-vite-react',
        '    path: products/shape-product/web',
        '    implementationStatus: implemented',
        '  queue-worker:',
        '    adapter: gradle-java-service',
        '    path: products/shape-product/worker',
        '    implementationStatus: implemented',
        '  api:',
        '    adapter: gradle-java-service',
        '    path: products/shape-product/api',
        '    implementationStatus: implemented',
        'phases:',
        '  build:',
        '    defaultSurfaces: [backend-api, web, queue-worker, api]',
        '    mode: dag',
      ],
      profiles: {
        'shape-only-profile': {
          defaultSurfaces: { package: ['backend-api'] },
          requiredGates: {},
          defaultAdapters: {},
        },
      },
      adapters: {
        'gradle-java-service': {
          supportedSurfaceTypes: ['backend-api', 'worker'],
          supportedPhases: ['build', 'package'],
          status: 'implemented',
          safeForDefault: true,
        },
        'pnpm-vite-react': {
          supportedSurfaceTypes: ['web'],
          supportedPhases: ['build'],
          status: 'implemented',
          safeForDefault: true,
        },
        'docker-buildx': {
          supportedSurfaceTypes: ['backend-api'],
          supportedPhases: ['package'],
          status: 'implemented',
          safeForDefault: true,
        },
      },
    });
    const planner = new ProductLifecyclePlanner(repoRoot);
    const buildPlan = await planner.plan('shape-product', 'build', {
      outputDir: 'relative-out',
      runId: 'run-fixed',
    });

    expect(buildPlan.phaseMode).toBe('dag');
    expect(buildPlan.outputDirectory).toBe(path.join(repoRoot, 'relative-out'));
    expect(buildPlan.surfaces.map((surface) => surface.type)).toEqual([
      'backend-api',
      'web',
      'worker',
      'backend-api',
    ]);
    expect(buildPlan.expectedArtifacts.map((artifact) => artifact.type)).toEqual([
      'jvm-service',
      'static-web-bundle',
      'jvm-service',
      'jvm-service',
    ]);

    const profileFallbackRepoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'allowExperimentalAdapters: true',
        'surfaces:',
        '  web:',
        '    adapter: pnpm-vite-react',
        '    path: products/shape-product/web',
        '    implementationStatus: implemented',
        'phases: {}',
      ],
      profiles: {
        'shape-only-profile': {
          defaultSurfaces: { build: ['web'] },
          requiredGates: {},
          defaultAdapters: {},
        },
      },
      adapters: {
        'pnpm-vite-react': {
          supportedSurfaceTypes: ['web'],
          supportedPhases: ['build'],
          status: 'implemented',
          safeForDefault: true,
        },
      },
    });
    const profileFallbackPlan = await new ProductLifecyclePlanner(profileFallbackRepoRoot).plan('shape-product', 'build');
    expect(profileFallbackPlan.surfaces[0].type).toBe('web');
    expect(profileFallbackPlan.phaseMode).toBe('sequential');
  });

  it('should use default package artifacts when package config omits explicit artifacts', async () => {
    const repoRoot = await createPlannerFixtureRepo({
      kernelProductYamlLines: [
        'productId: shape-product',
        'lifecycleProfile: shape-only-profile',
        'surfaces:',
        '  backend-api:',
        '    type: backend-api',
        '    adapter: gradle-java-service',
        '    path: products/shape-product/backend',
        '    implementationStatus: implemented',
        'phases:',
        '  package:',
        '    defaultSurfaces: [backend-api]',
        '    mode: sequential',
        'package:',
        '  backend-api:',
        '    adapter: docker-buildx',
        '    image: test',
      ],
      adapters: {
        'docker-buildx': {
          supportedSurfaceTypes: ['backend-api'],
          supportedPhases: ['package'],
          status: 'implemented',
          safeForDefault: true,
        },
      },
    });
    const plan = await new ProductLifecyclePlanner(repoRoot).plan('shape-product', 'package');

    expect(plan.expectedArtifacts).toEqual([
      {
        surface: 'backend-api',
        type: 'container-image',
        required: true,
      },
    ]);
  });
});

interface PlannerFixtureOptions {
  readonly registryProduct?: Record<string, unknown>;
  readonly profiles?: Record<string, unknown>;
  readonly adapters?: Record<string, Record<string, unknown>>;
  readonly exclusions?: Record<string, unknown>;
  readonly kernelProductYamlLines?: string[];
}

async function createPlannerFixtureRepo(options: PlannerFixtureOptions = {}): Promise<string> {
  const repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'ghatana-planner-'));
  const configDir = path.join(repoRoot, 'config');
  const productDir = path.join(repoRoot, 'products', 'shape-product');
  await fs.mkdir(configDir, { recursive: true });
  await fs.mkdir(productDir, { recursive: true });
  await fs.writeFile(
    path.join(configDir, 'canonical-product-registry.json'),
    JSON.stringify(
      {
        registry: {
          'shape-product': options.registryProduct ?? {
            lifecycleStatus: 'enabled',
            lifecycleConfigPath: 'products/shape-product/kernel-product.yaml',
            lifecycle: { enabled: true },
          },
        },
      },
      null,
      2,
    ),
  );
  await fs.writeFile(
    path.join(configDir, 'product-lifecycle-profiles.json'),
    JSON.stringify(
      {
        profiles: {
          ...(options.profiles ?? {
            'shape-only-profile': {
            defaultSurfaces: { test: ['web'] },
            requiredGates: {},
            defaultAdapters: { web: 'vitest' },
            },
          }),
        },
      },
      null,
      2,
    ),
  );
  await fs.writeFile(
    path.join(configDir, 'toolchain-adapter-registry.json'),
    JSON.stringify(
      {
        adapters: {
          ...normalizeFixtureAdapters(
            options.adapters ?? {
              vitest: {
                supportedSurfaceTypes: ['web'],
                supportedPhases: ['test'],
                status: 'partial',
                safeForDefault: false,
                lifecycleEnabled: false,
              },
            },
          ),
        },
      },
      null,
      2,
    ),
  );
  if (options.exclusions !== undefined) {
    await fs.writeFile(
      path.join(configDir, 'kernel-lifecycle-exclusions.json'),
      JSON.stringify({ excludedProducts: options.exclusions }, null, 2),
    );
  }
  await fs.writeFile(
    path.join(productDir, 'kernel-product.yaml'),
    (options.kernelProductYamlLines ?? [
      'productId: shape-product',
      'lifecycleProfile: shape-only-profile',
      'allowExperimentalAdapters: true',
      'surfaces:',
      '  web:',
      '    type: web',
      '    adapter: vitest',
      '    path: products/shape-product/web',
      '    implementationStatus: implemented',
      'phases:',
      '  test:',
      '    defaultSurfaces: [web]',
      '    mode: sequential',
    ]).join('\n'),
  );
  return repoRoot;
}

function normalizeFixtureAdapters(
  adapters: Record<string, Record<string, unknown>>,
): Record<string, Record<string, unknown>> {
  return Object.fromEntries(
    Object.entries(adapters).map(([adapterId, adapterConfig]) => [
      adapterId,
      {
        lifecycleEnabled: true,
        ...adapterConfig,
      },
    ]),
  );
}

describe('SurfaceSelector', () => {
  it('should select all surfaces when no filters provided', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'implemented',
      },
    };

    const selected = selector.select(surfaces);
    expect(selected).toHaveLength(2);
  });

  it('should filter by surface type', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'implemented',
      },
    };

    const selected = selector.select(surfaces, {
      surfaceTypes: ['backend-api' as ProductSurfaceType],
    });
    expect(selected).toHaveLength(1);
    expect(selected[0].type).toBe('backend-api');
  });

  it('should filter by implementation status', () => {
    const selector = new SurfaceSelector();
    const surfaces: Record<string, ProductSurface> = {
      'backend-api': {
        type: 'backend-api' as ProductSurfaceType,
        adapter: 'gradle-java-service',
        path: '/backend',
        implementationStatus: 'implemented',
      },
      web: {
        type: 'web' as ProductSurfaceType,
        adapter: 'pnpm-vite-react',
        path: '/web',
        implementationStatus: 'planned',
      },
    };

    const selected = selector.select(surfaces, {
      implementationStatus: ['implemented'],
    });
    expect(selected).toHaveLength(1);
    expect(selected[0].type).toBe('backend-api');
  });

  it('should validate surface configuration', () => {
    const selector = new SurfaceSelector();
    const surface: ProductSurface = {
      type: 'backend-api' as ProductSurfaceType,
      adapter: 'gradle-java-service',
      path: '/backend',
      implementationStatus: 'implemented',
    };

    const errors = selector.validate(surface);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid surface configuration', () => {
    const selector = new SurfaceSelector();
    const surface = {} as ProductSurface;

    const errors = selector.validate(surface);
    expect(errors.length).toBeGreaterThan(0);
  });
});

describe('GateResolver', () => {
  it('should resolve gates for phase', () => {
    const resolver = new GateResolver();
    const requiredGates = ['security', 'privacy'];
    const optionalGates = ['performance'];

    const gates = resolver.resolve('build' as ProductLifecyclePhase, requiredGates, optionalGates);

    expect(gates).toHaveLength(3);
    expect(gates.filter((g) => g.required)).toHaveLength(2);
    expect(gates.filter((g) => !g.required)).toHaveLength(1);
  });

  it('should validate gate configuration', () => {
    const resolver = new GateResolver();
    const errors = resolver.validate('security-check');
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid gate ID', () => {
    const resolver = new GateResolver();
    const errors = resolver.validate('');
    expect(errors.length).toBeGreaterThan(0);
  });

  it('should check gate requirement for production environment', () => {
    const resolver = new GateResolver();
    const isRequired = resolver.isGateRequiredForEnvironment('security', 'prod');
    expect(isRequired).toBe(true);
  });

  it('should not require production gates for dev environment', () => {
    const resolver = new GateResolver();
    const isRequired = resolver.isGateRequiredForEnvironment('security', 'dev');
    expect(isRequired).toBe(false);
  });
});

describe('ArtifactResolver', () => {
  it('should resolve build artifacts for backend-api surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('build' as ProductLifecyclePhase, 'backend-api' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'jvm-classes')).toBe(true);
  });

  it('should resolve build artifacts for web surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('build' as ProductLifecyclePhase, 'web' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'static-web-bundle')).toBe(true);
  });

  it('should resolve package artifacts for backend-api surface', () => {
    const resolver = new ArtifactResolver();
    const artifacts = resolver.resolve('package' as ProductLifecyclePhase, 'backend-api' as ProductSurfaceType);

    expect(artifacts.length).toBeGreaterThan(0);
    expect(artifacts.some((a) => a.type === 'container-image')).toBe(true);
  });

  it('should validate artifact configuration', () => {
    const resolver = new ArtifactResolver();
    const artifact = { type: 'jar', required: true };
    const errors = resolver.validate(artifact);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid artifact type', () => {
    const resolver = new ArtifactResolver();
    const artifact = { type: '', required: true };
    const errors = resolver.validate(artifact);
    expect(errors.length).toBeGreaterThan(0);
  });
});
