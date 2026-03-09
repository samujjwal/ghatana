/**
 * Testing Utilities - Helpers for testing custom simulation kernels.
 *
 * @doc.type module
 * @doc.purpose Provides testing utilities and mocks for kernel development
 * @doc.layer product
 * @doc.pattern TestUtility
 */

import type {
  SimulationManifest,
  SimEntityBase,
  SimulationStep,
  SimKernelService,
  SimulationId,
  SimEntityId,
  SimStepId,
  SimulationDomain,
  CanvasConfig,
  PlaybackConfig,
} from '@ghatana/tutorputor-contracts/v1/simulation';
import type { UserId, TenantId } from '@ghatana/tutorputor-contracts/v1/types';

/**
 * Create a mock manifest for testing.
 */
export function createMockManifest(
  overrides: Partial<SimulationManifest> = {}
): SimulationManifest {
  return {
    id: ('test-manifest-' + Math.random().toString(36).slice(2)) as SimulationId,
    version: '1.0.0',
    schemaVersion: '1.0.0',
    domain: 'CS_DISCRETE' as SimulationDomain,
    title: 'Test Simulation',
    description: 'A test simulation manifest',
    authorId: 'test-author' as UserId,
    tenantId: 'test-tenant' as TenantId,
    canvas: {
      width: 800,
      height: 600,
      backgroundColor: '#FFFFFF',
    } as CanvasConfig,
    playback: {
      defaultSpeed: 1,
      autoPlay: false,
      loop: false,
    } as PlaybackConfig,
    initialEntities: [
      createMockEntity({ id: ('entity-1' as SimEntityId), label: 'Element 1' }),
      createMockEntity({ id: ('entity-2' as SimEntityId), label: 'Element 2' }),
      createMockEntity({ id: ('entity-3' as SimEntityId), label: 'Element 3' }),
    ],
    steps: [
      createMockStep({ id: ('step-1' as SimStepId), orderIndex: 0, description: 'Step 1' }),
      createMockStep({ id: ('step-2' as SimStepId), orderIndex: 1, description: 'Step 2' }),
    ],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
}

/**
 * Create a mock entity.
 */
export function createMockEntity(overrides: Partial<SimEntityBase> = {}): SimEntityBase {
  return {
    id: ('entity-' + Math.random().toString(36).slice(2)) as SimEntityId,
    type: 'element',
    label: 'Test Entity',
    x: 0,
    y: 0,
    color: '#4A90D9',
    opacity: 1,
    ...overrides,
  };
}

/**
 * Create a mock step.
 */
export function createMockStep(overrides: Partial<SimulationStep> = {}): SimulationStep {
  return {
    id: ('step-' + Math.random().toString(36).slice(2)) as SimStepId,
    orderIndex: 0,
    description: 'Test Step',
    actions: [],
    ...overrides,
  };
}

/**
 * Create mock entities for a sorting simulation.
 */
export function createSortingEntities(values: number[]): SimEntityBase[] {
  return values.map((value, index) => ({
    id: (`element-${index}` as SimEntityId),
    type: 'node',
    label: String(value),
    x: index * 60,
    y: 0,
    color: '#4A90D9',
    opacity: 1,
    metadata: { value },
  }));
}

/**
 * Test harness for kernels.
 */
export class KernelTestHarness {
  private kernel: SimKernelService;
  private manifest: SimulationManifest;
  private stepResults: Array<{ stepIndex: number; entities: SimEntityBase[] }> = [];

  constructor(kernel: SimKernelService, manifest?: SimulationManifest) {
    this.kernel = kernel;
    this.manifest = manifest ?? createMockManifest();
  }

  /**
   * Initialize the kernel with the manifest.
   */
  initialize(): this {
    this.kernel.initialize(this.manifest);
    return this;
  }

  /**
   * Run all steps and collect results.
   */
  runAll(): this {
    for (let i = 0; i < this.manifest.steps.length; i++) {
      this.kernel.step();
      const interpolated = this.kernel.interpolate(1.0);
      this.stepResults.push({
        stepIndex: i,
        entities: interpolated.entities ?? [],
      });
    }
    return this;
  }

  /**
   * Run a specific number of steps.
   */
  runSteps(count: number): this {
    for (let i = 0; i < count; i++) {
      this.kernel.step();
      const interpolated = this.kernel.interpolate(1.0);
      this.stepResults.push({
        stepIndex: this.stepResults.length,
        entities: interpolated.entities ?? [],
      });
    }
    return this;
  }

  /**
   * Get results after running.
   */
  getResults(): Array<{ stepIndex: number; entities: SimEntityBase[] }> {
    return this.stepResults;
  }

  /**
   * Get final state.
   */
  getFinalState(): SimEntityBase[] {
    return this.stepResults[this.stepResults.length - 1]?.entities ?? [];
  }

  /**
   * Get analytics.
   */
  getAnalytics(): Record<string, unknown> {
    return this.kernel.getAnalytics();
  }

  /**
   * Reset the harness.
   */
  reset(): this {
    this.kernel.reset();
    this.stepResults = [];
    return this;
  }

  /**
   * Assert that array is sorted (ascending or descending).
   */
  assertSorted(ascending = true): this {
    const entities = this.getFinalState();
    // Extract values from entities that have a value property or from metadata
    const values = entities.map((e) => {
      if ('value' in e) {
        return (e as any).value as number;
      }
      if (e.metadata && 'value' in e.metadata) {
        return e.metadata.value as number;
      }
      throw new Error(`Entity ${e.id} does not have a value property`);
    });

    for (let i = 1; i < values.length; i++) {
      const comparison = ascending ? values[i - 1] <= values[i] : values[i - 1] >= values[i];
      if (!comparison) {
        throw new Error(
          `Array not sorted at index ${i}: ${values[i - 1]} ${ascending ? '>' : '<'} ${values[i]}`
        );
      }
    }

    return this;
  }

  /**
   * Assert entity count.
   */
  assertEntityCount(expected: number): this {
    const actual = this.getFinalState().length;
    if (actual !== expected) {
      throw new Error(`Expected ${expected} entities, got ${actual}`);
    }
    return this;
  }

  /**
   * Assert analytics metric.
   */
  assertMetric(name: string, expected: unknown): this {
    const analytics = this.getAnalytics();
    const actual = analytics[name];
    if (actual !== expected) {
      throw new Error(`Expected metric ${name} to be ${expected}, got ${actual}`);
    }
    return this;
  }

  /**
   * Assert that a specific entity has a value.
   * Note: Only works with entities that have a value property (e.g., DiscreteNodeEntity)
   */
  assertEntityValue(entityId: string, expected: unknown): this {
    const entity = this.getFinalState().find((e) => e.id === entityId);
    if (!entity) {
      throw new Error(`Entity not found: ${entityId}`);
    }
    // Check if entity has a value property
    if (!('value' in entity)) {
      throw new Error(`Entity ${entityId} does not have a value property`);
    }
    const entityWithValue = entity as SimEntityBase & { value: unknown };
    if (entityWithValue.value !== expected) {
      throw new Error(`Expected entity ${entityId} value to be ${expected}, got ${entityWithValue.value}`);
    }
    return this;
  }
}

/**
 * Create a test harness for a kernel.
 */
export function testKernel(
  kernel: SimKernelService,
  manifest?: SimulationManifest
): KernelTestHarness {
  return new KernelTestHarness(kernel, manifest);
}

/**
 * Performance benchmark for kernels.
 */
export async function benchmarkKernel(
  createKernel: () => SimKernelService,
  manifest: SimulationManifest,
  iterations = 100
): Promise<{
  avgInitTimeMs: number;
  avgStepTimeMs: number;
  avgTotalTimeMs: number;
  minTotalTimeMs: number;
  maxTotalTimeMs: number;
}> {
  const initTimes: number[] = [];
  const stepTimes: number[] = [];
  const totalTimes: number[] = [];

  for (let i = 0; i < iterations; i++) {
    const kernel = createKernel();

    const totalStart = performance.now();

    const initStart = performance.now();
    kernel.initialize(manifest);
    initTimes.push(performance.now() - initStart);

    for (let j = 0; j < manifest.steps.length; j++) {
      const stepStart = performance.now();
      kernel.step();
      stepTimes.push(performance.now() - stepStart);
    }

    totalTimes.push(performance.now() - totalStart);

    kernel.reset();
  }

  const avg = (arr: number[]) => arr.reduce((a, b) => a + b, 0) / arr.length;

  return {
    avgInitTimeMs: avg(initTimes),
    avgStepTimeMs: avg(stepTimes),
    avgTotalTimeMs: avg(totalTimes),
    minTotalTimeMs: Math.min(...totalTimes),
    maxTotalTimeMs: Math.max(...totalTimes),
  };
}
