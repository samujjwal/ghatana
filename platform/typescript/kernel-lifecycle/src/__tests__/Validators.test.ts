import { describe, it, expect } from 'vitest';
import { ProductLifecycleContractValidator } from '../validation/ProductLifecycleContractValidator.js';
import { ProductSurfaceValidator } from '../validation/ProductSurfaceValidator.js';
import { ProductEnvironmentValidator } from '../validation/ProductEnvironmentValidator.js';
import { ProductArtifactValidator } from '../validation/ProductArtifactValidator.js';
import { ProductGateValidator } from '../validation/ProductGateValidator.js';
import { ProductLifecyclePlan, ProductLifecycleResult, ProductSurface, ProductSurfaceType, ProductEnvironment, ProductArtifact, ProductGate, ProductGatePlan, ProductGateResult } from '../domain/ProductLifecyclePhase.js';

describe('ProductLifecycleContractValidator', () => {
  const validator = new ProductLifecycleContractValidator();

  it('should validate correct lifecycle plan', () => {
    const plan: ProductLifecyclePlan = {
      schemaVersion: '1.0.0',
      runId: 'run-1',
      correlationId: 'corr-1',
      providerMode: 'bootstrap',
      productId: 'test-product',
      phase: 'build',
      phaseMode: 'sequential',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: [
        {
          surface: 'backend-api',
          type: 'backend-api',
          adapter: 'gradle-java-service',
          config: {},
        },
      ],
      gates: [],
      steps: [
        {
          id: 'build-backend',
          stepKind: 'surface',
          phase: 'build',
          surface: 'backend-api',
          adapter: 'gradle-java-service',
          description: 'Build backend',
          dependsOn: [],
          estimatedDurationMs: 30000,
        },
      ],
      expectedArtifacts: [],
      requiredManifests: [],
      requiredPlugins: [],
      approvalRequirements: [],
      outputDirectory: '/tmp/output',
      estimatedDurationMs: 30000,
    };

    const errors = validator.validatePlan(plan);
    expect(errors).toHaveLength(0);
  });

  it('should detect missing required fields in plan', () => {
    const plan = {} as ProductLifecyclePlan;
    const errors = validator.validatePlan(plan);

    expect(errors.length).toBeGreaterThan(0);
    expect(errors.some((e) => e.path === 'productId')).toBe(true);
    expect(errors.some((e) => e.path === 'phase')).toBe(true);
  });

  it('should detect invalid lifecycle truth fields in plan', () => {
    const plan = {
      schemaVersion: '1.0.0',
      runId: '',
      correlationId: '',
      providerMode: 'legacy',
      productId: 'test-product',
      phase: 'build',
      phaseMode: 'sequential',
      lifecycleProfile: 'standard-web-api-product',
      surfaces: [{ surface: 'backend-api', type: 'backend-api', adapter: 'gradle-java-service', config: {} }],
      gates: [],
      steps: [
        {
          id: 'build-backend',
          stepKind: 'surface',
          phase: 'build',
          surface: 'backend-api',
          adapter: 'gradle-java-service',
          description: 'Build backend',
          dependsOn: [],
          estimatedDurationMs: 30000,
        },
      ],
      expectedArtifacts: [],
      requiredManifests: 'artifact-manifest',
      requiredPlugins: 'audit',
      approvalRequirements: 'approval',
      outputDirectory: '/tmp/output',
      estimatedDurationMs: -1,
    } as unknown as ProductLifecyclePlan;

    const errors = validator.validatePlan(plan);

    expect(errors.map((error) => error.path)).toEqual(
      expect.arrayContaining([
        'runId',
        'correlationId',
        'providerMode',
        'estimatedDurationMs',
        'requiredManifests',
        'requiredPlugins',
        'approvalRequirements',
      ]),
    );
  });

  it('should validate correct lifecycle result', () => {
    const result: ProductLifecycleResult = {
      schemaVersion: '1.0.0',
      runId: 'run-1',
      productId: 'test-product',
      phase: 'build',
      status: 'succeeded',
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      steps: [
        {
          stepId: 'build-backend',
          status: 'succeeded',
          durationMs: 30000,
        },
      ],
      gates: [],
      artifacts: [],
      outputDirectory: '/tmp/output',
    };

    const errors = validator.validateResult(result);
    expect(errors).toHaveLength(0);
  });

  it('should detect missing failure details for failed result', () => {
    const result: ProductLifecycleResult = {
      schemaVersion: '1.0.0',
      runId: 'run-1',
      productId: 'test-product',
      phase: 'build',
      status: 'failed',
      startedAt: new Date().toISOString(),
      completedAt: new Date().toISOString(),
      steps: [
        {
          stepId: 'build-backend',
          status: 'failed',
          durationMs: 30000,
        },
      ],
      gates: [],
      artifacts: [],
      outputDirectory: '/tmp/output',
    };

    const errors = validator.validateResult(result);
    expect(errors.some((e) => e.path === 'failure')).toBe(true);
  });

  it('should detect invalid lifecycle result fields', () => {
    const result = {
      schemaVersion: '1.0.0',
      runId: 'run-1',
      productId: '',
      status: '',
      startedAt: '',
      completedAt: '',
      steps: [],
      outputDirectory: '',
    } as unknown as ProductLifecycleResult;

    const errors = validator.validateResult(result);

    expect(errors.map((error) => error.path)).toEqual(
      expect.arrayContaining(['productId', 'phase', 'status', 'startedAt', 'completedAt', 'steps', 'outputDirectory']),
    );
  });
});

describe('ProductSurfaceValidator', () => {
  const validator = new ProductSurfaceValidator();

  it('should validate correct surface', () => {
    const surface: ProductSurface = {
      type: 'backend-api',
      adapter: 'gradle-java-service',
      path: '/products/test/backend',
      implementationStatus: 'implemented',
    };

    const errors = validator.validate(surface);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid surface type', () => {
    const surface: ProductSurface = {
      type: 'invalid-type' as ProductSurfaceType,
      adapter: 'gradle-java-service',
      path: '/products/test/backend',
      implementationStatus: 'implemented',
    };

    const errors = validator.validate(surface);
    expect(errors.some((e) => e.path === 'type')).toBe(true);
  });

  it('should detect missing required fields', () => {
    const surface = {} as ProductSurface;
    const errors = validator.validate(surface);

    expect(errors.length).toBeGreaterThan(0);
    expect(errors.some((e) => e.path === 'type')).toBe(true);
    expect(errors.some((e) => e.path === 'adapter')).toBe(true);
    expect(errors.some((e) => e.path === 'path')).toBe(true);
  });
});

describe('ProductEnvironmentValidator', () => {
  const validator = new ProductEnvironmentValidator();

  it('should validate correct environment', () => {
    const environment: ProductEnvironment = {
      id: 'prod',
      displayName: 'Production',
      deploymentTarget: 'kubernetes',
      secretsProvider: 'external-secret-store-with-rotation',
      configProvider: 'environment-config-service-versioned',
      approvalRequired: true,
      requiredGates: ['security', 'privacy', 'conformance'],
      observabilityProfile: 'prod-standard',
      rollbackPolicy: 'manual-with-plan',
      promotionPolicy: 'approval-required',
    };

    const errors = validator.validate(environment);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid environment ID', () => {
    const environment: ProductEnvironment = {
      id: 'invalid-env',
      displayName: 'Invalid',
      deploymentTarget: 'kubernetes',
      secretsProvider: 'external-secret-store',
      configProvider: 'environment-config-service',
      approvalRequired: false,
      requiredGates: [],
      observabilityProfile: 'dev-standard',
      rollbackPolicy: 'manual',
      promotionPolicy: 'linear',
    };

    const errors = validator.validate(environment);
    expect(errors.some((e) => e.path === 'id')).toBe(true);
  });
});

describe('ProductArtifactValidator', () => {
  const validator = new ProductArtifactValidator();

  it('should validate correct artifact', () => {
    const artifact: ProductArtifact = {
      id: 'backend-jar',
      surface: 'backend-api',
      type: 'jar',
      path: '/build/libs/backend.jar',
      fingerprint: 'a'.repeat(64),
      producedBy: 'gradle-java-service',
      sizeBytes: 1024000,
    };

    const errors = validator.validate(artifact);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid fingerprint', () => {
    const artifact: ProductArtifact = {
      id: 'backend-jar',
      surface: 'backend-api',
      type: 'jar',
      path: '/build/libs/backend.jar',
      fingerprint: 'invalid-fingerprint',
      producedBy: 'gradle-java-service',
    };

    const errors = validator.validate(artifact);
    expect(errors.length).toBeGreaterThan(0);
  });

  it('should validate fingerprint format', () => {
    const validFingerprint = 'a'.repeat(64);
    const errors = validator.validateFingerprint(validFingerprint);
    expect(errors).toHaveLength(0);

    const invalidFingerprint = 'invalid';
    const invalidErrors = validator.validateFingerprint(invalidFingerprint);
    expect(invalidErrors.length).toBeGreaterThan(0);
  });
});

describe('ProductGateValidator', () => {
  const validator = new ProductGateValidator();

  it('should validate correct gate', () => {
    const gate: ProductGate = {
      id: 'security-check',
      name: 'Security Check',
      description: 'Run security scans',
      required: true,
      phase: 'build',
      implementation: 'security-scan-adapter',
    };

    const errors = validator.validate(gate);
    expect(errors).toHaveLength(0);
  });

  it('should validate correct gate plan', () => {
    const plan: ProductGatePlan = {
      gateId: 'security-check',
      gateName: 'Security Check',
      required: true,
      phase: 'build',
      status: 'pending',
    };

    const errors = validator.validateGatePlan(plan);
    expect(errors).toHaveLength(0);
  });

  it('should validate correct gate result', () => {
    const result: ProductGateResult = {
      gateId: 'security-check',
      gateName: 'Security Check',
      status: 'passed',
      checkedAt: new Date().toISOString(),
    };

    const errors = validator.validateGateResult(result);
    expect(errors).toHaveLength(0);
  });

  it('should detect invalid gate status', () => {
    const plan: ProductGatePlan = {
      gateId: 'security-check',
      gateName: 'Security Check',
      required: true,
      phase: 'build',
      status: 'invalid-status' as any,
    };

    const errors = validator.validateGatePlan(plan);
    expect(errors.some((e) => e.path === 'status')).toBe(true);
  });
});
