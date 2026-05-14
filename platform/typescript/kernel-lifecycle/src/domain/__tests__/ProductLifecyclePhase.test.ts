import { describe, it, expect } from 'vitest';
import type {
  ProductLifecyclePhase,
  ProductLifecyclePlan,
  ProductLifecycleStep,
  ProductLifecycleResult,
  ProductLifecycleStepResult,
  ProductArtifact,
  LifecyclePlan,
  LifecyclePlanStep,
  LifecycleStepKind,
  LifecycleStepAdapterContext,
  KernelProductConfiguration,
  PackageSurfaceConfig,
  DeploymentEnvironmentConfig,
  VerifyEnvironmentConfig,
  HealthCheckConfig,
} from '../ProductLifecyclePhase.js';

// ── ProductLifecyclePhase ────────────────────────────────────────────────────

describe('ProductLifecyclePhase', () => {
  it('enumerates all expected phase values', () => {
    const phases: ProductLifecyclePhase[] = [
      'create',
      'bootstrap',
      'dev',
      'validate',
      'test',
      'build',
      'package',
      'release',
      'deploy',
      'verify',
      'promote',
      'rollback',
      'operate',
      'retire',
    ];
    expect(phases).toHaveLength(14);
    expect(phases).toContain('build');
    expect(phases).toContain('deploy');
    expect(phases).toContain('verify');
  });
});

// ── LifecycleStepKind ────────────────────────────────────────────────────────

describe('LifecycleStepKind', () => {
  it('accepts all valid step kinds', () => {
    const kinds: LifecycleStepKind[] = [
      'gate',
      'surface',
      'package',
      'deploy',
      'verify',
      'release',
      'promotion',
      'rollback',
    ];
    expect(kinds).toHaveLength(8);
    expect(kinds).toContain('gate');
    expect(kinds).toContain('surface');
    expect(kinds).toContain('deploy');
  });
});

// ── ProductLifecycleStep ─────────────────────────────────────────────────────

describe('ProductLifecycleStep', () => {
  it('constructs a valid step with required fields', () => {
    const step: ProductLifecycleStep = {
      id: 'build-backend-api',
      stepKind: 'surface',
      phase: 'build',
      surface: 'backend-api',
      adapter: 'gradle-java-service',
      description: 'Build the backend API JAR',
      dependsOn: [],
      estimatedDurationMs: 60_000,
    };

    expect(step.id).toBe('build-backend-api');
    expect(step.stepKind).toBe('surface');
    expect(step.phase).toBe('build');
    expect(step.adapter).toBe('gradle-java-service');
    expect(step.dependsOn).toHaveLength(0);
  });

  it('accepts optional adapterContext and execution fields', () => {
    const adapterCtx: LifecycleStepAdapterContext = {
      surfaceConfig: { gradleModule: ':products:dm-api:build' },
      packageConfig: { image: 'ghatana/dm-api:local' },
      deploymentConfig: { composeFile: 'local.compose.yaml' },
      artifactConfig: { type: 'container-image' },
      environmentConfig: { env: 'local' },
    };

    const step: ProductLifecycleStep = {
      id: 'deploy-step',
      stepKind: 'deploy',
      phase: 'deploy',
      surface: 'backend-api',
      adapter: 'compose-local',
      description: 'Deploy with compose',
      dependsOn: ['build-backend-api'],
      estimatedDurationMs: 30_000,
      adapterContext: adapterCtx,
      execution: {
        command: 'docker',
        args: ['compose', 'up', '-d'],
        workingDirectory: '/repo',
      },
    };

    expect(step.adapterContext?.surfaceConfig?.gradleModule).toBe(':products:dm-api:build');
    expect(step.adapterContext?.packageConfig?.image).toBe('ghatana/dm-api:local');
    expect(step.dependsOn).toContain('build-backend-api');
    expect(step.execution?.command).toBe('docker');
  });
});

// ── ProductLifecyclePlan ─────────────────────────────────────────────────────

describe('ProductLifecyclePlan', () => {
  function makeMinimalPlan(): ProductLifecyclePlan {
    return {
      schemaVersion: '1.0.0',
      runId: 'run-abc123',
      productId: 'digital-marketing',
      phase: 'build',
      phaseMode: 'sequential',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: [],
      gates: [],
      steps: [],
      expectedArtifacts: [],
      outputDirectory: '/tmp/out',
      estimatedDurationMs: 0,
    };
  }

  it('constructs a minimal plan without errors', () => {
    const plan = makeMinimalPlan();
    expect(plan.schemaVersion).toBe('1.0.0');
    expect(plan.productId).toBe('digital-marketing');
    expect(plan.phaseMode).toBe('sequential');
  });

  it('accepts phaseMode: parallel', () => {
    const plan: ProductLifecyclePlan = {
      ...makeMinimalPlan(),
      phaseMode: 'parallel',
    };
    expect(plan.phaseMode).toBe('parallel');
  });

  it('accepts phaseMode: dag', () => {
    const plan: ProductLifecyclePlan = {
      ...makeMinimalPlan(),
      phaseMode: 'dag',
    };
    expect(plan.phaseMode).toBe('dag');
  });

  it('accepts optional environment and sourceRef', () => {
    const plan: ProductLifecyclePlan = {
      ...makeMinimalPlan(),
      environment: 'local',
      sourceRef: 'refs/heads/main',
    };
    expect(plan.environment).toBe('local');
    expect(plan.sourceRef).toBe('refs/heads/main');
  });

  it('stores steps and gates', () => {
    const step: ProductLifecycleStep = {
      id: 's1',
      stepKind: 'surface',
      phase: 'build',
      surface: 'backend-api',
      adapter: 'gradle-java-service',
      description: 'Build',
      dependsOn: [],
      estimatedDurationMs: 60_000,
    };
    const plan: ProductLifecyclePlan = {
      ...makeMinimalPlan(),
      steps: [step],
    };
    expect(plan.steps).toHaveLength(1);
    expect(plan.steps[0].id).toBe('s1');
  });
});

// ── LifecyclePlan — deprecated alias ─────────────────────────────────────────

describe('LifecyclePlan (deprecated alias)', () => {
  it('is structurally identical to ProductLifecyclePlan', () => {
    const plan: LifecyclePlan = {
      schemaVersion: '1.0.0',
      runId: 'run-001',
      productId: 'test-product',
      phase: 'test',
      phaseMode: 'sequential',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: [],
      gates: [],
      steps: [],
      expectedArtifacts: [],
      outputDirectory: '/tmp',
      estimatedDurationMs: 0,
    };
    // LifecyclePlan must be assignable to ProductLifecyclePlan and vice versa
    const canonical: ProductLifecyclePlan = plan;
    expect(canonical.productId).toBe('test-product');
  });
});

// ── LifecyclePlanStep — deprecated alias ─────────────────────────────────────

describe('LifecyclePlanStep (deprecated alias)', () => {
  it('is structurally identical to ProductLifecycleStep', () => {
    const step: LifecyclePlanStep = {
      id: 'step-1',
      stepKind: 'gate',
      phase: 'validate',
      surface: 'backend-api',
      adapter: 'gradle-java-service',
      description: 'Gate check',
      dependsOn: [],
      estimatedDurationMs: 1000,
    };
    const canonical: ProductLifecycleStep = step;
    expect(canonical.id).toBe('step-1');
  });
});

// ── ProductLifecycleResult ───────────────────────────────────────────────────

describe('ProductLifecycleResult', () => {
  it('constructs a valid result', () => {
    const stepResult: ProductLifecycleStepResult = {
      stepId: 'build-1',
      status: 'succeeded',
      exitCode: 0,
      durationMs: 5000,
    };

    const result: ProductLifecycleResult = {
      schemaVersion: '1.0.0',
      runId: 'run-abc',
      productId: 'digital-marketing',
      phase: 'build',
      status: 'succeeded',
      startedAt: '2026-01-01T00:00:00.000Z',
      completedAt: '2026-01-01T00:01:00.000Z',
      steps: [stepResult],
      gates: [],
      artifacts: [],
      outputDirectory: '/tmp',
    };

    expect(result.status).toBe('succeeded');
    expect(result.steps).toHaveLength(1);
    expect(result.steps[0].stepId).toBe('build-1');
  });

  it('accepts optional failure field', () => {
    const result: ProductLifecycleResult = {
      schemaVersion: '1.0.0',
      runId: 'run-fail',
      productId: 'digital-marketing',
      phase: 'build',
      status: 'failed',
      startedAt: '2026-01-01T00:00:00.000Z',
      completedAt: '2026-01-01T00:01:00.000Z',
      steps: [],
      gates: [],
      artifacts: [],
      outputDirectory: '/tmp',
      failure: {
        stepId: 'build-1',
        message: 'Gradle build failed',
        cause: 'CompilationException: cannot find symbol',
      },
    };

    expect(result.failure?.stepId).toBe('build-1');
    expect(result.failure?.message).toContain('Gradle');
  });
});

// ── ProductArtifact ───────────────────────────────────────────────────────────

describe('ProductArtifact', () => {
  it('constructs a file-based artifact', () => {
    const artifact: ProductArtifact = {
      id: 'dm-api-jar',
      surface: 'backend-api',
      type: 'jvm-service',
      path: 'products/digital-marketing/dm-api/build/libs/dm-api.jar',
      fingerprint: 'sha256:abc123',
      producedBy: 'gradle-java-service',
      sizeBytes: 42_000_000,
    };

    expect(artifact.type).toBe('jvm-service');
    expect(artifact.fingerprint).toBe('sha256:abc123');
    expect(artifact.image).toBeUndefined();
  });

  it('constructs a container image artifact', () => {
    const artifact: ProductArtifact = {
      id: 'dm-api-image',
      surface: 'backend-api',
      type: 'container-image',
      path: 'ghatana/digital-marketing-api:local',
      fingerprint: 'sha256:deadbeef',
      producedBy: 'docker-buildx',
      image: 'ghatana/digital-marketing-api',
      tag: 'local',
      digest: 'sha256:deadbeef',
      localImageId: 'abc1234',
    };

    expect(artifact.type).toBe('container-image');
    expect(artifact.image).toBe('ghatana/digital-marketing-api');
    expect(artifact.tag).toBe('local');
    expect(artifact.digest).toBe('sha256:deadbeef');
    expect(artifact.localImageId).toBe('abc1234');
  });
});

// ── KernelProductConfiguration ───────────────────────────────────────────────

describe('KernelProductConfiguration', () => {
  it('constructs a minimal config', () => {
    const config: KernelProductConfiguration = {
      productId: 'digital-marketing',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: {
        'backend-api': {
          type: 'backend-api',
          adapter: 'gradle-java-service',
          path: 'products/digital-marketing/dm-api',
          implementationStatus: 'implemented',
        },
      },
      phases: {
        build: {
          defaultSurfaces: ['backend-api'],
          mode: 'sequential',
        },
      },
    };

    expect(config.productId).toBe('digital-marketing');
    expect(config.surfaces['backend-api'].adapter).toBe('gradle-java-service');
  });

  it('accepts optional package and deployment sections', () => {
    const packageConfig: PackageSurfaceConfig = {
      adapter: 'docker-buildx',
      image: 'ghatana/dm-api',
      tag: 'local',
      dockerfile: 'dm-api/Dockerfile',
      context: 'dm-api',
    };

    const deploymentConfig: DeploymentEnvironmentConfig = {
      adapter: 'compose-local',
      target: 'local',
      composeFile: 'deploy/local.compose.yaml',
      envFile: 'deploy/local.env',
      requireEnvFile: false,
    };

    const verifyConfig: VerifyEnvironmentConfig = {
      adapter: 'compose-local',
      healthChecks: {
        'backend-api': {
          type: 'http',
          url: 'http://localhost:8080/health/ready',
          retries: 10,
          intervalMs: 3000,
          timeoutMs: 30_000,
        },
      },
    };

    const config: KernelProductConfiguration = {
      productId: 'digital-marketing',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: {},
      phases: {},
      package: { 'backend-api': packageConfig },
      deployment: { local: deploymentConfig },
      verify: { local: verifyConfig },
    };

    expect(config.package?.['backend-api'].image).toBe('ghatana/dm-api');
    expect(config.deployment?.local.adapter).toBe('compose-local');
    expect(config.verify?.local.healthChecks?.['backend-api'].url).toContain('/health/ready');
  });
});

// ── HealthCheckConfig ─────────────────────────────────────────────────────────

describe('HealthCheckConfig', () => {
  it('accepts http type with url', () => {
    const check: HealthCheckConfig = {
      type: 'http',
      url: 'http://localhost:8080/health/ready',
      retries: 5,
      intervalMs: 2000,
      timeoutMs: 10_000,
    };

    expect(check.type).toBe('http');
    expect(check.url).toContain('/health/ready');
  });

  it('accepts tcp type with host and port', () => {
    const check: HealthCheckConfig = {
      type: 'tcp',
      host: 'localhost',
      port: 5432,
    };

    expect(check.type).toBe('tcp');
    expect(check.port).toBe(5432);
  });
});
