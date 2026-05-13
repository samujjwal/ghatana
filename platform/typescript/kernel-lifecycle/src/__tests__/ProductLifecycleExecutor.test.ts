import { describe, it, expect, beforeEach } from 'vitest';
import { ProductLifecycleExecutor } from '../execution/ProductLifecycleExecutor.js';
import { ProductLifecycleStepRunner, AdapterRegistry, Adapter, AdapterResult, AdapterContext } from '../execution/ProductLifecycleStepRunner.js';
import { ExecutionResultCollector, ConsoleExecutionLogger } from '../execution/ExecutionLogger.js';
import { ProductLifecycleStep, ProductLifecyclePhase } from '../domain/ProductLifecyclePhase.js';

/**
 * Fake adapter for testing
 */
class FakeAdapter implements Adapter {
  constructor(private shouldSucceed: boolean = true) {}

  async execute(_context: AdapterContext): Promise<AdapterResult> {
    if (this.shouldSucceed) {
      return { status: 'succeeded' };
    }
    return { status: 'failed' };
  }
}

/**
 * Fake adapter registry for testing
 */
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

  it('should execute lifecycle plan successfully', async () => {
    const steps: ProductLifecycleStep[] = [
      {
        id: 'build-backend',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'gradle-java-service',
        description: 'Build backend API',
        dependsOn: [],
        estimatedDurationMs: 30000,
      },
      {
        id: 'build-web',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'web',
        adapter: 'pnpm-vite-react',
        description: 'Build web frontend',
        dependsOn: [],
        estimatedDurationMs: 20000,
      },
    ];

    const result = await executor.execute('test-product', 'build' as ProductLifecyclePhase, steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.steps).toHaveLength(2);
    expect(result.steps[0].status).toBe('succeeded');
    expect(result.steps[1].status).toBe('succeeded');
  });

  it('should execute in dry-run mode', async () => {
    const steps: ProductLifecycleStep[] = [
      {
        id: 'build-backend',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'gradle-java-service',
        description: 'Build backend API',
        dependsOn: [],
        estimatedDurationMs: 30000,
      },
    ];

    const result = await executor.execute('test-product', 'build' as ProductLifecyclePhase, steps, {
      dryRun: true,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.steps[0].status).toBe('skipped');
  });

  it('should handle step failure', async () => {
    adapterRegistry.register('failing-adapter', new FakeAdapter(false));

    const steps: ProductLifecycleStep[] = [
      {
        id: 'build-backend',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'failing-adapter',
        description: 'Build backend API',
        dependsOn: [],
        estimatedDurationMs: 30000,
      },
    ];

    const result = await executor.execute('test-product', 'build' as ProductLifecyclePhase, steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('failed');
    expect(result.steps[0].status).toBe('failed');
    expect(result.failure).toBeDefined();
    expect(result.failure?.stepId).toBe('build-backend');
  });

  it('should execute steps with dependencies sequentially', async () => {
    const steps: ProductLifecycleStep[] = [
      {
        id: 'build-backend',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'gradle-java-service',
        description: 'Build backend API',
        dependsOn: [],
        estimatedDurationMs: 30000,
      },
      {
        id: 'package-backend',
        phase: 'package' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'docker-buildx',
        description: 'Package backend API',
        dependsOn: ['build-backend'],
        estimatedDurationMs: 20000,
      },
    ];

    const result = await executor.execute('test-product', 'build' as ProductLifecyclePhase, steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('succeeded');
    expect(result.steps).toHaveLength(2);
    expect(result.steps[0].status).toBe('succeeded');
    expect(result.steps[1].status).toBe('succeeded');
  });

  it('should stop execution on dependency failure', async () => {
    adapterRegistry.register('failing-adapter', new FakeAdapter(false));

    const steps: ProductLifecycleStep[] = [
      {
        id: 'build-backend',
        phase: 'build' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'failing-adapter',
        description: 'Build backend API',
        dependsOn: [],
        estimatedDurationMs: 30000,
      },
      {
        id: 'package-backend',
        phase: 'package' as ProductLifecyclePhase,
        surface: 'backend-api',
        adapter: 'docker-buildx',
        description: 'Package backend API',
        dependsOn: ['build-backend'],
        estimatedDurationMs: 20000,
      },
    ];

    const result = await executor.execute('test-product', 'build' as ProductLifecyclePhase, steps, {
      dryRun: false,
      outputDirectory: '/tmp/output',
      logger,
    });

    expect(result.status).toBe('failed');
    expect(result.steps[0].status).toBe('failed');
    expect(result.steps[1].status).toBe('skipped');
  });
});
