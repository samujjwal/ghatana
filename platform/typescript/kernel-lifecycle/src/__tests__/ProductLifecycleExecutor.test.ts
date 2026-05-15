import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mkdtemp, rm, readFile } from 'node:fs/promises';
import * as path from 'node:path';
import * as os from 'node:os';
import type {
  KernelLifecycleEvent,
  LifecycleArtifactProvider,
  LifecycleApprovalProvider,
  LifecycleEventProvider,
  GateProvider,
} from '@ghatana/kernel-product-contracts';
import { ProductLifecycleExecutor } from '../execution/ProductLifecycleExecutor.js';
import {
  ProductLifecycleStepRunner,
  AdapterRegistry,
  Adapter,
  AdapterResult,
  AdapterContext,
} from '../execution/ProductLifecycleStepRunner.js';
import { ConsoleExecutionLogger } from '../execution/ExecutionLogger.js';
import { ExecutionResultCollector } from '../execution/ExecutionResultCollector.js';
import { ProductLifecycleStep, ProductLifecyclePhase, ProductLifecyclePlan } from '../domain/ProductLifecyclePhase.js';
import { KernelLifecycleEventEmitter } from '../events/KernelLifecycleEventEmitter.js';

class FakeAdapter implements Adapter {
  constructor(private shouldSucceed: boolean = true) {}

  async execute(_context: AdapterContext): Promise<AdapterResult> {
    return { status: this.shouldSucceed ? 'succeeded' : 'failed' };
  }
}

class ArtifactAdapter implements Adapter {
  async execute(_context: AdapterContext): Promise<AdapterResult> {
    return {
      status: 'succeeded',
      artifacts: [
        {
          id: 'backend-jar',
          surface: 'backend-api',
          type: 'jvm-service',
          path: 'build/libs/app.jar',
          fingerprint: 'sha256:jar',
          producedBy: 'gradle-java-service',
        },
      ],
      stdout: 'artifact produced',
      warnings: ['artifact warning'],
      manifestRefs: { artifactManifest: 'artifact-manifest.json' },
    };
  }
}

class FakeAdapterRegistry implements AdapterRegistry {
  private adapters = new Map<string, Adapter>();

  register(adapterId: string, adapter: Adapter): void {
    this.adapters.set(adapterId, adapter);
  }

  getAdapter(adapterId: string): Adapter {
    const adapter = this.adapters.get(adapterId);
    if (!adapter) {
      throw new Error(`Adapter ${adapterId} not found`);
    }
    return adapter;
  }
}

function makeStep(
  overrides: Partial<ProductLifecycleStep> & { id: string; adapter: string },
): ProductLifecycleStep {
  return {
    stepKind: 'surface',
    phase: 'build' as ProductLifecyclePhase,
    surface: 'backend-api',
    description: 'Test step',
    dependsOn: [],
    estimatedDurationMs: 1000,
    ...overrides,
  };
}

function makePlan(
  overrides: Partial<ProductLifecyclePlan> & Pick<ProductLifecyclePlan, 'productId' | 'phase' | 'steps'>,
): ProductLifecyclePlan {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-001',
    correlationId: 'corr-run-001',
    providerMode: 'bootstrap',
    phaseMode: 'sequential',
    lifecycleProfile: 'standard-web-api-product',
    surfaces: [],
    gates: [],
    expectedArtifacts: [],
    requiredManifests: [],
    requiredPlugins: [],
    approvalRequirements: [],
    outputDirectory: '/tmp/output',
    estimatedDurationMs: 5000,
    ...overrides,
  };
}

function createLifecycleEventProvider(): LifecycleEventProvider {
  return {
    providerId: 'events-test',
    version: '1.0.0',
    capabilities: ['events'],
    appendEvent: vi.fn().mockResolvedValue({ success: true, ref: 'events/run-001.jsonl' }),
    listEvents: vi.fn().mockResolvedValue([]),
  };
}

function appendedEvents(provider: LifecycleEventProvider): KernelLifecycleEvent[] {
  return vi.mocked(provider.appendEvent).mock.calls.map(([event]) => event);
}

function createArtifactProvider(overrides: Partial<LifecycleArtifactProvider> = {}): LifecycleArtifactProvider {
  return {
    providerId: 'artifact-provider',
    version: '1.0.0',
    capabilities: ['artifact-manifests'],
    recordArtifactManifest: vi.fn().mockResolvedValue({ success: true, ref: 'artifact-provider-ref' }),
    listArtifactManifests: vi.fn().mockResolvedValue([]),
    ...overrides,
  };
}

function createGateProvider(overrides: Partial<GateProvider> = {}): GateProvider {
  return {
    providerId: 'policy-gates',
    version: '1.0.0',
    capabilities: ['gates'],
    evaluateGate: vi.fn().mockResolvedValue({
      gateId: 'security',
      passed: true,
      reason: 'policy passed',
      evidence: ['policy:security'],
      evaluatedAt: '2026-05-14T00:00:00.000Z',
      duration: 10,
    }),
    getGateConfig: vi.fn().mockResolvedValue(null),
    listGates: vi.fn().mockResolvedValue(['security']),
    ...overrides,
  };
}

function createApprovalProvider(
  overrides: Partial<LifecycleApprovalProvider> = {},
): LifecycleApprovalProvider {
  return {
    providerId: 'approval-provider',
    version: '1.0.0',
    capabilities: ['approvals'],
    requestLifecycleApproval: vi.fn().mockResolvedValue({ success: true, ref: 'approval-gates/deploy-prod.json' }),
    decideLifecycleApproval: vi.fn().mockResolvedValue({ success: true, ref: 'approval-gates/deploy-prod.json' }),
    ...overrides,
  };
}

describe('ProductLifecycleExecutor', () => {
  let executor: ProductLifecycleExecutor;
  let adapterRegistry: FakeAdapterRegistry;
  let logger: ConsoleExecutionLogger;

  beforeEach(() => {
    adapterRegistry = new FakeAdapterRegistry();
    adapterRegistry.register('gradle-java-service', new FakeAdapter(true));
    adapterRegistry.register('pnpm-vite-react', new FakeAdapter(true));
    adapterRegistry.register('docker-buildx', new FakeAdapter(true));

    logger = new ConsoleExecutionLogger();
    const stepRunner = new ProductLifecycleStepRunner(adapterRegistry);
    const resultCollector = new ExecutionResultCollector(logger);
    executor = new ProductLifecycleExecutor(stepRunner, resultCollector);
  });

  it('executes all steps successfully', async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: 'build-backend', adapter: 'gradle-java-service' }),
      makeStep({ id: 'build-web', adapter: 'pnpm-vite-react', surface: 'web' }),
    ];

    const result = await executor.execute('test-product', 'build', steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.steps).toHaveLength(2);
    expect(result.steps.every((s) => s.status === 'succeeded')).toBe(true);
  });

  it('skips all steps in dry-run mode', async () => {
    const steps: ProductLifecycleStep[] = [
      makeStep({ id: 'build-backend', adapter: 'gradle-java-service' }),
    ];

    const result = await executor.execute('test-product', 'build', steps, {
      dryRun: true,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('skipped');
    expect(result.steps[0].status).toBe('skipped');
  });

  it('marks result as failed when a step fails', async () => {
    adapterRegistry.register('failing-adapter', new FakeAdapter(false));

    const steps: ProductLifecycleStep[] = [
      makeStep({ id: 'failing-step', adapter: 'failing-adapter' }),
    ];

    const result = await executor.execute('test-product', 'build', steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('failed');
    expect(result.steps[0].status).toBe('failed');
    expect(result.failure).toBeDefined();
    expect(result.failure?.stepId).toBe('failing-step');
  });

  it('skips dependent steps when their dependency fails', async () => {
    adapterRegistry.register('failing-adapter', new FakeAdapter(false));

    const steps: ProductLifecycleStep[] = [
      makeStep({ id: 'step-a', adapter: 'failing-adapter' }),
      makeStep({ id: 'step-b', adapter: 'docker-buildx', dependsOn: ['step-a'] }),
    ];

    const result = await executor.execute('test-product', 'build', steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('failed');
    expect(result.steps[0].status).toBe('failed');
    expect(result.steps[1].status).toBe('skipped');
  });

  it('executePlan accepts a ProductLifecyclePlan', async () => {
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'step-x', adapter: 'gradle-java-service' })],
      phaseMode: 'sequential',
      productUnitRef: 'product-unit://test',
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.correlationId).toBe('corr-run-001');
    expect(result.providerMode).toBe('bootstrap');
    expect(result.productUnitRef).toBe('product-unit://test');
  });

  it('fails closed with artifact-missing reason code when required artifacts are absent', async () => {
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'step-x', adapter: 'gradle-java-service' })],
      expectedArtifacts: [
        {
          surface: 'backend-api',
          type: 'jvm-service',
          required: true,
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('failed');
    expect(result.failure?.reasonCode).toBe('artifact-missing');
    expect(result.failure?.stepId).toBe('artifact-validation');
  });

  it('collects adapter artifacts and preserves step evidence in lifecycle results', async () => {
    adapterRegistry.register('artifact-adapter', new ArtifactAdapter());
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'artifact-step', adapter: 'artifact-adapter' })],
      expectedArtifacts: [
        {
          surface: 'backend-api',
          type: 'jvm-service',
          required: true,
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.artifacts).toEqual([
      {
        id: 'backend-jar',
        surface: 'backend-api',
        type: 'jvm-service',
        path: 'build/libs/app.jar',
        fingerprint: 'sha256:jar',
        producedBy: 'gradle-java-service',
      },
    ]);
    expect(result.steps[0]).toMatchObject({
      stepId: 'artifact-step',
      stdout: 'artifact produced',
      warnings: ['artifact warning'],
      manifestRefs: { artifactManifest: 'artifact-manifest.json' },
    });
    expect(result.manifestRefs?.artifactManifest).toContain('artifact-manifest.json');
  });

  it('fails closed when a required artifact manifest provider write fails', async () => {
    adapterRegistry.register('artifact-adapter', new ArtifactAdapter());
    const artifactProvider = createArtifactProvider({
      recordArtifactManifest: vi.fn().mockResolvedValue({ success: false, error: 'artifact store unavailable' }),
    });
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'artifact-step', adapter: 'artifact-adapter' })],
      requiredManifests: ['artifact-manifest'],
      expectedArtifacts: [
        {
          surface: 'backend-api',
          type: 'jvm-service',
          required: true,
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
      providerContext: {
        mode: 'bootstrap',
        artifacts: artifactProvider,
      },
    });

    expect(result.status).toBe('failed');
    expect(result.failure).toMatchObject({
      reasonCode: 'manifest-write-failed',
      stepId: 'manifest-writer',
    });
    expect(result.failure?.message).toContain('artifact store unavailable');
  });

  it('emits correlated phase and step lifecycle events through the event provider', async () => {
    adapterRegistry.register('artifact-adapter', new ArtifactAdapter());
    const eventProvider = createLifecycleEventProvider();
    const eventEmitter = new KernelLifecycleEventEmitter({
      lifecycleEventProvider: eventProvider,
      enableConsoleLogging: false,
    });
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'artifact-step', adapter: 'artifact-adapter' })],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
      eventEmitter,
    });

    expect(result.status).toBe('succeeded');
    expect(appendedEvents(eventProvider).map((event) => event.metadata.eventType)).toEqual([
      'lifecycle.phase.started',
      'lifecycle.step.started',
      'lifecycle.step.completed',
      'lifecycle.phase.completed',
    ]);
    expect(appendedEvents(eventProvider).every((event) => event.metadata.correlationId === 'corr-run-001')).toBe(true);
    expect(appendedEvents(eventProvider)[2]).toMatchObject({
      payload: {
        stepId: 'artifact-step',
        status: 'succeeded',
        evidenceRefs: ['artifact:backend-jar', 'manifest:artifact-manifest.json'],
      },
    });
  });

  it('blocks adapter execution when a required gate fails', async () => {
    const gateProvider = createGateProvider({
      evaluateGate: vi.fn().mockResolvedValue({
        gateId: 'security',
        passed: false,
        reason: 'policy denied',
        evidence: ['policy:deny'],
        evaluatedAt: '2026-05-14T00:00:00.000Z',
        duration: 15,
      }),
    });
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'step-x', adapter: 'gradle-java-service' })],
      gates: [
        {
          gateId: 'security',
          gateName: 'Security',
          required: true,
          phase: 'build',
          source: 'kernel-product.yaml',
          status: 'pending',
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
      providerContext: {
        mode: 'bootstrap',
        gates: {
          security: gateProvider,
        },
      },
    });

    expect(result.status).toBe('failed');
    expect(result.failure).toMatchObject({
      reasonCode: 'gate-failed',
      stepId: 'gate:security',
      message: 'policy denied',
    });
    expect(result.gates).toEqual([
      expect.objectContaining({
        gateId: 'security',
        status: 'failed',
        evidenceRefs: ['policy:deny'],
      }),
    ]);
    expect(result.steps).toEqual([]);
  });

  it('continues adapter execution when optional gate providers are missing', async () => {
    const plan = makePlan({
      productId: 'test',
      phase: 'build' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'step-x', adapter: 'gradle-java-service' })],
      gates: [
        {
          gateId: 'observability',
          gateName: 'Observability',
          required: false,
          phase: 'build',
          source: 'kernel-product.yaml',
          status: 'pending',
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
      providerContext: {
        mode: 'bootstrap',
        gates: {},
      },
    });

    expect(result.status).toBe('succeeded');
    expect(result.gates).toEqual([
      expect.objectContaining({
        gateId: 'observability',
        status: 'skipped',
        details: 'Optional gate provider missing: observability',
      }),
    ]);
    expect(result.steps).toHaveLength(1);
  });

  it('requests required approvals and blocks adapter execution until approval is decided', async () => {
    const approvalProvider = createApprovalProvider();
    const plan = makePlan({
      productId: 'test-product',
      phase: 'deploy' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'deploy-step', adapter: 'docker-buildx', phase: 'deploy' })],
      approvalRequirements: [
        {
          approvalId: 'deploy-prod-approval',
          action: 'deploy:prod',
          riskLevel: 'high',
          required: true,
          requiredApprovers: ['alice'],
          source: 'kernel-product.approvals',
        },
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      environment: 'prod',
      logger,
      providerContext: {
        mode: 'bootstrap',
        approvals: approvalProvider,
      },
    });

    expect(result.status).toBe('failed');
    expect(result.failure).toMatchObject({
      reasonCode: 'approval-required',
      stepId: 'approval-required',
    });
    expect(result.approvalRefs).toEqual([
      {
        approvalId: 'deploy-prod-approval',
        status: 'pending',
        ref: 'approval-gates/deploy-prod.json',
      },
    ]);
    expect(result.steps).toEqual([]);
    expect(approvalProvider.requestLifecycleApproval).toHaveBeenCalledWith(
      expect.objectContaining({
        approvalId: 'deploy-prod-approval',
        productUnitId: 'test-product',
        runId: 'run-001',
        correlationId: 'corr-run-001',
        environment: 'prod',
        action: 'deploy:prod',
        riskLevel: 'high',
        requiredApprovers: ['alice'],
      }),
      {
        required: true,
        correlationId: 'corr-run-001',
      },
    );
  });

  it('fails closed when required approvals lack a provider or approvers', async () => {
    const plan = makePlan({
      productId: 'test-product',
      phase: 'deploy' as ProductLifecyclePhase,
      steps: [makeStep({ id: 'deploy-step', adapter: 'docker-buildx', phase: 'deploy' })],
      approvalRequirements: [
        {
          approvalId: 'deploy-prod-approval',
          action: 'deploy:prod',
          riskLevel: 'high',
          required: true,
          requiredApprovers: ['alice'],
          source: 'kernel-product.approvals',
        },
      ],
    });

    await expect(executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    })).resolves.toMatchObject({
      status: 'failed',
      failure: {
        reasonCode: 'approval-required',
        stepId: 'approval-provider',
      },
      steps: [],
    });

    const missingApproversPlan = makePlan({
      ...plan,
      approvalRequirements: [
        {
          approvalId: 'deploy-prod-approval',
          action: 'deploy:prod',
          riskLevel: 'high',
          required: true,
          source: 'kernel-product.approvals',
        },
      ],
    });
    const approvalProvider = createApprovalProvider();

    await expect(executor.executePlan(missingApproversPlan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
      providerContext: {
        mode: 'bootstrap',
        approvals: approvalProvider,
      },
    })).resolves.toMatchObject({
      status: 'failed',
      failure: {
        reasonCode: 'approval-required',
        stepId: 'approval:deploy-prod-approval',
      },
      steps: [],
    });
    expect(approvalProvider.requestLifecycleApproval).not.toHaveBeenCalled();
  });

  it('executes independent steps in parallel when phaseMode is parallel', async () => {
    const completionOrder: string[] = [];

    class OrderedAdapter implements Adapter {
      constructor(private id: string, private delayMs: number) {}
      async execute(_context: AdapterContext): Promise<AdapterResult> {
        await new Promise((resolve) => setTimeout(resolve, this.delayMs));
        completionOrder.push(this.id);
        return { status: 'succeeded' };
      }
    }

    adapterRegistry.register('slow-adapter', new OrderedAdapter('slow', 20));
    adapterRegistry.register('fast-adapter', new OrderedAdapter('fast', 5));

    // Both steps have no dependencies — should run in parallel
    const plan = makePlan({
      productId: 'test-parallel',
      phase: 'build' as ProductLifecyclePhase,
      phaseMode: 'parallel',
      steps: [
        makeStep({ id: 'slow-step', adapter: 'slow-adapter' }),
        makeStep({ id: 'fast-step', adapter: 'fast-adapter' }),
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.steps).toHaveLength(2);
    // Fast step should finish before slow step when running in parallel
    expect(completionOrder).toEqual(['fast', 'slow']);
  });

  it('honours DAG dependencies even in parallel mode', async () => {
    const completionOrder: string[] = [];

    class OrderedAdapter implements Adapter {
      constructor(private label: string) {}
      async execute(_context: AdapterContext): Promise<AdapterResult> {
        completionOrder.push(this.label);
        return { status: 'succeeded' };
      }
    }

    adapterRegistry.register('first-adapter', new OrderedAdapter('first'));
    adapterRegistry.register('second-adapter', new OrderedAdapter('second'));

    const plan = makePlan({
      productId: 'test-dag',
      phase: 'build' as ProductLifecyclePhase,
      phaseMode: 'dag',
      steps: [
        makeStep({ id: 'first', adapter: 'first-adapter' }),
        makeStep({ id: 'second', adapter: 'second-adapter', dependsOn: ['first'] }),
      ],
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(completionOrder).toEqual(['first', 'second']);
  });

  describe('phase output writing', () => {
    let tmpDir: string;

    beforeEach(async () => {
      tmpDir = await mkdtemp(path.join(os.tmpdir(), 'kernel-test-'));
    });

    afterEach(async () => {
      await rm(tmpDir, { recursive: true, force: true });
    });

    it('writes test-summary.json after test phase', async () => {
      const plan = makePlan({
        productId: 'test-product',
        phase: 'test' as ProductLifecyclePhase,
        steps: [makeStep({ id: 'test-step', adapter: 'gradle-java-service', phase: 'test' })],
      });

      const result = await executor.executePlan(plan, {
        dryRun: false,
        outputDirectory: tmpDir,
        logger,
      });

      expect(result.status).toBe('succeeded');

      const resultContent = await readFile(
        path.join(tmpDir, 'test-product', 'run-001', 'test', 'lifecycle-result.json'),
        'utf-8',
      );
      const manifest = JSON.parse(resultContent) as Record<string, unknown>;
      expect(manifest.schemaVersion).toBe('1.0.0');
      expect(manifest.productId).toBe('test-product');
      expect(manifest.status).toBe('succeeded');
    });

    it('writes deployment-manifest.json after deploy phase', async () => {
      adapterRegistry.register('compose-local', new FakeAdapter(true));

      const plan = makePlan({
        productId: 'test-product',
        phase: 'deploy' as ProductLifecyclePhase,
        steps: [makeStep({ id: 'deploy-step', adapter: 'compose-local', phase: 'deploy' })],
      });

      const result = await executor.executePlan(plan, {
        dryRun: false,
        outputDirectory: tmpDir,
        environment: 'local',
        logger,
      });

      expect(result.status).toBe('succeeded');

      const manifestContent = await readFile(
        path.join(tmpDir, 'test-product', 'run-001', 'deploy', 'deployment-manifest.json'),
        'utf-8',
      );
      const manifest = JSON.parse(manifestContent) as Record<string, unknown>;
      expect(manifest.schemaVersion).toBe('1.0.0');
      expect(manifest.productId).toBe('test-product');
      expect(manifest.environment).toBe('local');
      expect(manifest.surfaces).toEqual([
        expect.objectContaining({ status: 'deployed' }),
      ]);
    });
  });
});
