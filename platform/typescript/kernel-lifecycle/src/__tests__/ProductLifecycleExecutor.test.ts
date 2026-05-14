import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtemp, rm, readFile } from 'node:fs/promises';
import * as path from 'node:path';
import * as os from 'node:os';
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

class FakeAdapter implements Adapter {
  constructor(private shouldSucceed: boolean = true) {}

  async execute(_context: AdapterContext): Promise<AdapterResult> {
    return { status: this.shouldSucceed ? 'succeeded' : 'failed' };
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
    phaseMode: 'sequential',
    lifecycleProfile: 'standard-web-api-product',
    surfaces: [],
    gates: [],
    expectedArtifacts: [],
    outputDirectory: '/tmp/output',
    estimatedDurationMs: 5000,
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
    });

    const result = await executor.executePlan(plan, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
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

      const summaryContent = await readFile(path.join(tmpDir, 'test-summary.json'), 'utf-8');
      const summary = JSON.parse(summaryContent) as Record<string, unknown>;
      expect(summary.schemaVersion).toBe('1.0.0');
      expect(summary.productId).toBe('test-product');
      expect(summary.status).toBe('succeeded');
      expect(summary.passedSteps).toBe(1);
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

      const manifestContent = await readFile(path.join(tmpDir, 'deployment-manifest.json'), 'utf-8');
      const manifest = JSON.parse(manifestContent) as Record<string, unknown>;
      expect(manifest.schemaVersion).toBe('1.0.0');
      expect(manifest.productId).toBe('test-product');
      expect(manifest.environment).toBe('local');
      expect(manifest.status).toBe('succeeded');
    });
  });
});
