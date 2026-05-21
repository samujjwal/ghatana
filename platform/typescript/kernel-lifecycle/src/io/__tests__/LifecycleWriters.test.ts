import { describe, expect, it } from 'vitest';
import { PlanWriter } from '../PlanWriter.js';
import { ResultWriter } from '../ResultWriter.js';
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../../domain/ProductLifecyclePhase.js';

describe('Lifecycle writers', () => {
  it('includes interaction preflights, dependencies, warnings, and blockers in plan summaries', () => {
    const summary = new PlanWriter().generateSummary(makePlan());

    expect(summary).toContain('## Interaction Preflights (1)');
    expect(summary).toContain('kernel://interactions/phr.consent-status.v1: blocked');
    expect(summary).toContain('## Interaction Rollback Impact (1)');
    expect(summary).toContain('kernel://interactions/phr.consent-status.v1: blocking');
    expect(summary).toContain('depends on interaction-preflight-0');
    expect(summary).toContain('## Blocking Reasons');
    expect(summary).toContain('product_interaction.provider_not_enabled');
    expect(summary).toContain('## Warnings');
  });

  it('uses ASCII execution markers and includes recovery guidance for failures', () => {
    const summary = new ResultWriter().generateSummary(makeFailedResult());

    expect(summary).toContain('[FAIL] build-backend-api (failed) - 42ms');
    expect(summary).toContain('Error: Compilation failed');
    expect(summary).toContain('[PASS] policy (passed)');
    expect(summary).toContain('## Recovery Guidance');
    expect(summary).toContain('Use failure reason code adapter-failed');
    expect(summary).toContain('Start with adapter gradle-java-service');
    expect(summary).not.toContain('â');
  });
});

function makePlan(): ProductLifecyclePlan {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-1',
    correlationId: 'corr-1',
    productId: 'digital-marketing',
    phase: 'deploy',
    lifecycleProfile: 'polyglot-service-product',
    providerMode: 'bootstrap',
    surfaces: [
      {
        surface: 'backend-api',
        type: 'backend-api',
        adapter: 'gradle-java-service',
        path: 'products/digital-marketing/backend',
      },
    ],
    gates: [
      {
        gateId: 'policy',
        gateName: 'policy',
        required: true,
        source: 'profile',
      },
    ],
    steps: [
      {
        id: 'interaction-preflight-0',
        stepKind: 'interaction-preflight',
        phase: 'deploy',
        surface: 'interaction:kernel://interactions/phr.consent-status.v1',
        adapter: 'kernel-product-interaction-broker',
        description: 'Preflight interaction contract',
        dependsOn: [],
        estimatedDurationMs: 5000,
      },
      {
        id: 'deploy-backend-api',
        stepKind: 'deploy',
        phase: 'deploy',
        surface: 'backend-api',
        adapter: 'compose-local',
        description: 'Deploy backend',
        dependsOn: ['interaction-preflight-0'],
        estimatedDurationMs: 30_000,
      },
    ],
    adapterIds: ['kernel-product-interaction-broker', 'compose-local'],
    expectedArtifacts: [
      {
        artifactId: 'deployment-manifest-backend-api',
        surface: 'backend-api',
        type: 'deployment-manifest',
        required: true,
      },
    ],
    requiredManifests: ['deployment-manifest'],
    requiredPlugins: [],
    approvalRequirements: [],
    interactionPreflights: [
      {
        contractId: 'kernel://interactions/phr.consent-status.v1',
        providerProductId: 'phr',
        consumerProductId: 'digital-marketing',
        mode: 'request-response',
        required: true,
        status: 'blocked',
        reasonCode: 'product_interaction.provider_not_enabled',
        evidenceRequired: true,
      },
    ],
    interactionRollbackImpact: [
      {
        contractId: 'kernel://interactions/phr.consent-status.v1',
        providerProductId: 'phr',
        consumerProductId: 'digital-marketing',
        affectedProductIds: ['digital-marketing'],
        mode: 'request-response',
        required: true,
        impactLevel: 'blocking',
        status: 'provider-not-enabled',
        reasonCode: 'product_interaction.rollback_provider_not_enabled',
        evidenceRequired: true,
      },
    ],
    healthChecks: [],
    outputDirectory: '.kernel/out/run-1',
    estimatedDurationMs: 35_000,
    warnings: ['Adapter compose-local targets local environment'],
    blockingReasons: ['product_interaction.provider_not_enabled'],
  };
}

function makeFailedResult(): ProductLifecycleResult {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-1',
    correlationId: 'corr-1',
    productId: 'digital-marketing',
    phase: 'build',
    status: 'failed',
    startedAt: '2026-05-21T00:00:00.000Z',
    completedAt: '2026-05-21T00:00:01.000Z',
    steps: [
      {
        stepId: 'build-backend-api',
        phase: 'build',
        surface: 'backend-api',
        adapter: 'gradle-java-service',
        status: 'failed',
        durationMs: 42,
        errors: ['Compilation failed'],
      },
    ],
    gates: [
      {
        gateId: 'policy',
        gateName: 'policy',
        status: 'passed',
        required: true,
        durationMs: 1,
      },
    ],
    artifacts: [],
    manifestRefs: { artifactManifest: '.kernel/out/run-1/artifact-manifest.json' },
    outputDirectory: '.kernel/out/run-1',
    failure: {
      reasonCode: 'adapter-failed',
      stepId: 'build-backend-api',
      message: 'Gradle build failed',
    },
  };
}
