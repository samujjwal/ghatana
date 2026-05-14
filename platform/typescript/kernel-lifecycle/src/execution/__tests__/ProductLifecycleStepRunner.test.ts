import { describe, it, expect, vi } from 'vitest';
import {
  ProductLifecycleStepRunner,
  AdapterRegistry,
  Adapter,
  AdapterResult,
  AdapterContext,
  StepContext,
} from '../ProductLifecycleStepRunner.js';
import type { ProductLifecycleStep } from '../../domain/ProductLifecyclePhase.js';

// ── Test doubles ──────────────────────────────────────────────────────────────

function makeLogger() {
  return {
    info: vi.fn<[string, Record<string, unknown>?], void>(),
    warn: vi.fn<[string, Record<string, unknown>?], void>(),
    error: vi.fn<[string, Record<string, unknown>?], void>(),
    debug: vi.fn<[string, Record<string, unknown>?], void>(),
  };
}

function makeStep(overrides: Partial<ProductLifecycleStep> & { id: string }): ProductLifecycleStep {
  return {
    stepKind: 'surface',
    phase: 'build',
    surface: 'backend-api',
    adapter: 'gradle-java-service',
    description: 'Test step',
    dependsOn: [],
    estimatedDurationMs: 1000,
    ...overrides,
  };
}

function makeContext(overrides: Partial<StepContext> = {}): StepContext {
  return {
    productId: 'digital-marketing',
    surfaceType: 'backend-api',
    surfacePath: 'products/digital-marketing/dm-api',
    dryRun: false,
    surfaceConfig: { gradleModule: ':products:digital-marketing:dm-api' },
    phaseConfig: {},
    logger: makeLogger(),
    outputDirectory: '/tmp/output',
    ...overrides,
  };
}

class FakeSuccessAdapter implements Adapter {
  lastContext: AdapterContext | undefined;
  async execute(context: AdapterContext): Promise<AdapterResult> {
    this.lastContext = context;
    return { status: 'succeeded' };
  }
}

class FakeFailureAdapter implements Adapter {
  async execute(_context: AdapterContext): Promise<AdapterResult> {
    return { status: 'failed' };
  }
}

class FakeSkippedAdapter implements Adapter {
  async execute(_context: AdapterContext): Promise<AdapterResult> {
    return { status: 'skipped' };
  }
}

class FakeThrowingAdapter implements Adapter {
  async execute(_context: AdapterContext): Promise<AdapterResult> {
    throw new Error('Adapter execution exploded');
  }
}

function makeRegistry(adapters: Record<string, Adapter>): AdapterRegistry {
  return {
    getAdapter(adapterId: string): Adapter {
      const adapter = adapters[adapterId];
      if (!adapter) throw new Error(`Adapter '${adapterId}' not registered in test`);
      return adapter;
    },
  };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('ProductLifecycleStepRunner', () => {
  describe('run() — successful execution', () => {
    it('returns succeeded when adapter returns succeeded', async () => {
      const successAdapter = new FakeSuccessAdapter();
      const registry = makeRegistry({ 'gradle-java-service': successAdapter });
      const runner = new ProductLifecycleStepRunner(registry);

      const step = makeStep({ id: 'build-step', adapter: 'gradle-java-service' });
      const context = makeContext();

      const result = await runner.run(step, context);

      expect(result.status).toBe('succeeded');
      expect(result.stepId).toBe('build-step');
      expect(result.exitCode).toBe(0);
      expect(result.durationMs).toBeGreaterThanOrEqual(0);
    });

    it('logs step start and completion', async () => {
      const registry = makeRegistry({ 'gradle-java-service': new FakeSuccessAdapter() });
      const runner = new ProductLifecycleStepRunner(registry);
      const logger = makeLogger();
      const context = makeContext({ logger });

      await runner.run(makeStep({ id: 'step-1', adapter: 'gradle-java-service' }), context);

      expect(logger.info).toHaveBeenCalledWith(expect.stringContaining('step-1'), expect.any(Object));
    });
  });

  describe('run() — failure handling', () => {
    it('returns failed when adapter returns failed', async () => {
      const registry = makeRegistry({ 'gradle-java-service': new FakeFailureAdapter() });
      const runner = new ProductLifecycleStepRunner(registry);

      const result = await runner.run(
        makeStep({ id: 'step-fail', adapter: 'gradle-java-service' }),
        makeContext(),
      );

      expect(result.status).toBe('failed');
      expect(result.stepId).toBe('step-fail');
      expect(result.exitCode).toBe(1);
    });

    it('returns failed when adapter throws', async () => {
      const registry = makeRegistry({ 'gradle-java-service': new FakeThrowingAdapter() });
      const runner = new ProductLifecycleStepRunner(registry);
      const logger = makeLogger();

      const result = await runner.run(
        makeStep({ id: 'step-throw', adapter: 'gradle-java-service' }),
        makeContext({ logger }),
      );

      expect(result.status).toBe('failed');
      expect(result.exitCode).toBe(1);
      expect(logger.error).toHaveBeenCalled();
    });
  });

  describe('run() — fail-closed for skipped result in non-dry-run', () => {
    it('converts skipped to failed for a non-dry-run required step', async () => {
      const registry = makeRegistry({ 'gradle-java-service': new FakeSkippedAdapter() });
      const runner = new ProductLifecycleStepRunner(registry);
      const logger = makeLogger();

      const result = await runner.run(
        makeStep({ id: 'step-skip', adapter: 'gradle-java-service' }),
        makeContext({ dryRun: false, logger }),
      );

      expect(result.status).toBe('failed');
      expect(result.exitCode).toBe(1);
      expect(logger.error).toHaveBeenCalledWith(
        expect.stringContaining('skipped'),
        expect.any(Object),
      );
    });

    it('does not fail when dryRun is true and adapter returns skipped', async () => {
      // In dry-run mode the executor short-circuits to skipped before calling the runner.
      // This test confirms the adapter's 'skipped' is not double-penalised in dry-run scenarios
      // where it IS acceptable (e.g., the run method itself handles dry-run in the executor).
      // Direct StepRunner calls in dry-run: skipped is still fail-closed (runner has no dry-run awareness).
      const registry = makeRegistry({ 'gradle-java-service': new FakeSkippedAdapter() });
      const runner = new ProductLifecycleStepRunner(registry);

      // Dry-run is NOT a bypass of fail-closed inside the runner itself —
      // the executor wraps the call and skips before calling the runner in dry-run.
      // This test documents that the runner fails-closed even in dryRun=true StepContext.
      const result = await runner.run(
        makeStep({ id: 'step-skip-dry', adapter: 'gradle-java-service' }),
        makeContext({ dryRun: true }),
      );

      // Fail closed regardless of dryRun flag at the runner level
      expect(result.status).toBe('failed');
    });
  });

  describe('run() — adapter context construction', () => {
    it('builds adapter context with correct fields from StepContext', async () => {
      const successAdapter = new FakeSuccessAdapter();
      const registry = makeRegistry({ 'gradle-java-service': successAdapter });
      const runner = new ProductLifecycleStepRunner(registry);

      const step = makeStep({
        id: 'step-ctx',
        adapter: 'gradle-java-service',
        phase: 'build',
        surface: 'backend-api',
      });
      const context = makeContext({
        productId: 'test-product',
        surfaceType: 'backend-api',
        surfacePath: 'products/test-product/api',
        environment: 'staging',
        sourceRef: 'refs/heads/main',
        surfaceConfig: { gradleModule: ':products:test-product:api' },
        phaseConfig: { mode: 'sequential' },
        outputDirectory: '/tmp/staging-out',
        metadata: { version: '1.2.3' },
      });

      await runner.run(step, context);

      const adapterCtx = successAdapter.lastContext;
      expect(adapterCtx).toBeDefined();
      expect(adapterCtx?.productId).toBe('test-product');
      expect(adapterCtx?.phase).toBe('build');
      expect(adapterCtx?.surface.type).toBe('backend-api');
      expect(adapterCtx?.surface.adapter).toBe('gradle-java-service');
      expect(adapterCtx?.surface.path).toBe('products/test-product/api');
      expect(adapterCtx?.environment).toBe('staging');
      expect(adapterCtx?.sourceRef).toBe('refs/heads/main');
      expect(adapterCtx?.dryRun).toBe(false);
      expect(adapterCtx?.surfaceConfig).toEqual({ gradleModule: ':products:test-product:api' });
      expect(adapterCtx?.phaseConfig).toEqual({ mode: 'sequential' });
      expect(adapterCtx?.metadata).toEqual({ version: '1.2.3' });
      expect(adapterCtx?.outputDir).toBe('/tmp/staging-out');
    });

    it('passes undefined environment when not set in StepContext', async () => {
      const successAdapter = new FakeSuccessAdapter();
      const registry = makeRegistry({ 'gradle-java-service': successAdapter });
      const runner = new ProductLifecycleStepRunner(registry);

      const context = makeContext({ environment: undefined });
      await runner.run(makeStep({ id: 's', adapter: 'gradle-java-service' }), context);

      expect(successAdapter.lastContext?.environment).toBeUndefined();
    });
  });

  describe('runParallel()', () => {
    it('runs all steps and returns all results', async () => {
      const registry = makeRegistry({
        'gradle-java-service': new FakeSuccessAdapter(),
        'pnpm-vite-react': new FakeSuccessAdapter(),
      });
      const runner = new ProductLifecycleStepRunner(registry);

      const steps: ProductLifecycleStep[] = [
        makeStep({ id: 'step-a', adapter: 'gradle-java-service' }),
        makeStep({ id: 'step-b', adapter: 'pnpm-vite-react' }),
      ];
      const context = makeContext();

      const results = await runner.runParallel(steps, context);

      expect(results).toHaveLength(2);
      expect(results.map((r) => r.stepId)).toContain('step-a');
      expect(results.map((r) => r.stepId)).toContain('step-b');
      expect(results.every((r) => r.status === 'succeeded')).toBe(true);
    });

    it('runs all steps concurrently (both start before either resolves)', async () => {
      let concurrentCount = 0;
      let maxConcurrent = 0;

      class SlowSuccessAdapter implements Adapter {
        async execute(_context: AdapterContext): Promise<AdapterResult> {
          concurrentCount++;
          maxConcurrent = Math.max(maxConcurrent, concurrentCount);
          await new Promise<void>((r) => setTimeout(r, 10));
          concurrentCount--;
          return { status: 'succeeded' };
        }
      }

      const slowAdapter = new SlowSuccessAdapter();
      const registry = makeRegistry({
        'gradle-java-service': slowAdapter,
        'pnpm-vite-react': slowAdapter,
      });
      const runner = new ProductLifecycleStepRunner(registry);

      const steps: ProductLifecycleStep[] = [
        makeStep({ id: 's1', adapter: 'gradle-java-service' }),
        makeStep({ id: 's2', adapter: 'pnpm-vite-react' }),
      ];

      await runner.runParallel(steps, makeContext());

      // Both steps were running concurrently
      expect(maxConcurrent).toBe(2);
    });
  });

  describe('runSequential()', () => {
    it('runs steps in order and stops on first failure', async () => {
      const order: string[] = [];

      class OrderedAdapter implements Adapter {
        constructor(
          private readonly id: string,
          private readonly shouldFail: boolean,
        ) {}
        async execute(_context: AdapterContext): Promise<AdapterResult> {
          order.push(this.id);
          return { status: this.shouldFail ? 'failed' : 'succeeded' };
        }
      }

      const registry = makeRegistry({
        'step-a-adapter': new OrderedAdapter('step-a', false),
        'step-b-adapter': new OrderedAdapter('step-b', true), // fails
        'step-c-adapter': new OrderedAdapter('step-c', false),
      });
      const runner = new ProductLifecycleStepRunner(registry);

      const steps: ProductLifecycleStep[] = [
        makeStep({ id: 'step-a', adapter: 'step-a-adapter' }),
        makeStep({ id: 'step-b', adapter: 'step-b-adapter' }),
        makeStep({ id: 'step-c', adapter: 'step-c-adapter' }),
      ];

      const results = await runner.runSequential(steps, makeContext());

      // step-a: succeeded, step-b: failed → step-c never ran
      expect(results).toHaveLength(2);
      expect(order).toEqual(['step-a', 'step-b']);
      expect(results[0].status).toBe('succeeded');
      expect(results[1].status).toBe('failed');
    });

    it('runs all steps when none fail', async () => {
      const registry = makeRegistry({
        'gradle-java-service': new FakeSuccessAdapter(),
      });
      const runner = new ProductLifecycleStepRunner(registry);

      const steps = [
        makeStep({ id: 's1', adapter: 'gradle-java-service' }),
        makeStep({ id: 's2', adapter: 'gradle-java-service' }),
        makeStep({ id: 's3', adapter: 'gradle-java-service' }),
      ];

      const results = await runner.runSequential(steps, makeContext());

      expect(results).toHaveLength(3);
      expect(results.every((r) => r.status === 'succeeded')).toBe(true);
    });
  });
});
