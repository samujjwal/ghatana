/**
 * Integration Tests
 * 
 * Provides utilities for component integration testing including CanvasScene
 * mounting, palette drag-and-drop validation, and update flow testing.
 * 
 * Features:
 * - Component mounting with React Flow provider
 * - DnD metadata validation
 * - Update flow testing with state assertions
 * - Integration test helpers for common scenarios
 * - Mock integration with test utilities
 * 
 * @module testing/integrationTests
 */

import type { Node, Edge, Viewport } from '@xyflow/react';

/**
 * Integration test configuration options
 */
export interface IntegrationTestConfig {
  /**
   * Whether to enable debug logging
   */
  debug?: boolean;

  /**
   * Timeout for async operations (ms)
   */
  timeout?: number;

  /**
   * Whether to strict check prop types
   */
  strictProps?: boolean;

  /**
   * Custom React Flow provider props
   */
  flowProviderProps?: Record<string, unknown>;

  /**
   * Mock implementations for dependencies
   */
  mocks?: {
    localStorage?: Partial<Storage>;
    fetch?: typeof fetch;
    [key: string]: unknown;
  };
}

/**
 * Canvas scene integration test context
 */
export interface CanvasSceneContext {
  /**
   * Root component wrapper
   */
  wrapper: unknown;

  /**
   * React Flow instance
   */
  flowInstance: {
    getNodes: () => Node[];
    getEdges: () => Edge[];
    setNodes: (nodes: Node[]) => void;
    setEdges: (edges: Edge[]) => void;
    getViewport: () => Viewport;
    setViewport: (viewport: Viewport) => void;
  };

  /**
   * Test utilities
   */
  utils: {
    /**
     * Find node by ID
     */
    findNode: (id: string) => Node | undefined;

    /**
     * Find edge by ID
     */
    findEdge: (id: string) => Edge | undefined;

    /**
     * Simulate node update
     */
    updateNode: (id: string, updates: Partial<Node>) => void;

    /**
     * Simulate edge update
     */
    updateEdge: (id: string, updates: Partial<Edge>) => void;

    /**
     * Simulate viewport change
     */
    updateViewport: (updates: Partial<Viewport>) => void;

    /**
     * Wait for state update
     */
    waitForUpdate: () => Promise<void>;

    /**
     * Clean up test context
     */
    cleanup: () => void;
  };

  /**
   * Current state snapshot
   */
  state: {
    nodes: Node[];
    edges: Edge[];
    viewport: Viewport;
  };
}

/**
 * Palette drag test metadata
 */
export interface PaletteDragMetadata {
  /**
   * Component type being dragged
   */
  componentType: string;

  /**
   * Drag start position
   */
  startPosition: { x: number; y: number };

  /**
   * Drop target position
   */
  dropPosition: { x: number; y: number };

  /**
   * Whether drop was successful
   */
  dropSuccessful: boolean;

  /**
   * Created node ID (if successful)
   */
  nodeId?: string;

  /**
   * Error message (if failed)
   */
  error?: string;

  /**
   * Drag duration (ms)
   */
  duration?: number;

  /**
   * Custom data attached to drag
   */
  customData?: Record<string, unknown>;
}

/**
 * Update flow validation result
 */
export interface UpdateFlowValidation {
  /**
   * Whether update flow is valid
   */
  isValid: boolean;

  /**
   * Validation errors
   */
  errors: Array<{
    type: 'node' | 'edge' | 'viewport' | 'state';
    message: string;
    details?: unknown;
  }>;

  /**
   * Validation warnings
   */
  warnings: Array<{
    type: 'performance' | 'consistency' | 'best-practice';
    message: string;
    details?: unknown;
  }>;

  /**
   * Update statistics
   */
  stats: {
    nodesUpdated: number;
    edgesUpdated: number;
    viewportUpdated: boolean;
    duration: number;
  };
}

/**
 * Integration test scenario
 */
export interface IntegrationScenario {
  /**
   * Scenario name
   */
  name: string;

  /**
   * Scenario description
   */
  description: string;

  /**
   * Setup function
   */
  setup: (context: CanvasSceneContext) => Promise<void> | void;

  /**
   * Test steps
   */
  steps: Array<{
    name: string;
    action: (context: CanvasSceneContext) => Promise<void> | void;
    assertions: (context: CanvasSceneContext) => Promise<void> | void;
  }>;

  /**
   * Teardown function
   */
  teardown?: (context: CanvasSceneContext) => Promise<void> | void;

  /**
   * Expected duration (ms)
   */
  expectedDuration?: number;

  /**
   * Tags for categorization
   */
  tags?: string[];
}

/**
 * Integration test results
 */
export interface IntegrationTestResults {
  /**
   * Scenario name
   */
  scenario: string;

  /**
   * Whether test passed
   */
  passed: boolean;

  /**
   * Test duration (ms)
   */
  duration: number;

  /**
   * Step results
   */
  steps: Array<{
    name: string;
    passed: boolean;
    duration: number;
    error?: Error;
  }>;

  /**
   * Test errors
   */
  errors: Error[];

  /**
   * Test metadata
   */
  metadata: {
    timestamp: number;
    config: IntegrationTestConfig;
    context: {
      nodeCount: number;
      edgeCount: number;
      viewport: Viewport;
    };
  };
}

/**
 * Integration Tests Manager
 * 
 * Manages integration test scenarios and provides utilities for component
 * integration testing including CanvasScene mounting, palette DnD, and
 * update flow validation.
 */
export class IntegrationTestsManager {
  private config: Required<IntegrationTestConfig>;
  private scenarios = new Map<string, IntegrationScenario>();
  private results = new Map<string, IntegrationTestResults>();
  private currentContext: CanvasSceneContext | null = null;

  /**
   *
   */
  constructor(config: IntegrationTestConfig = {}) {
    this.config = {
      debug: config.debug ?? false,
      timeout: config.timeout ?? 5000,
      strictProps: config.strictProps ?? true,
      flowProviderProps: config.flowProviderProps ?? {},
      mocks: config.mocks ?? {},
    };
  }

  /**
   * Get current configuration
   */
  getConfig(): Readonly<Required<IntegrationTestConfig>> {
    return { ...this.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<IntegrationTestConfig>): void {
    this.config = {
      ...this.config,
      ...updates,
      flowProviderProps: {
        ...this.config.flowProviderProps,
        ...(updates.flowProviderProps ?? {}),
      },
      mocks: {
        ...this.config.mocks,
        ...(updates.mocks ?? {}),
      },
    };
  }

  /**
   * Register integration test scenario
   */
  registerScenario(scenario: IntegrationScenario): void {
    this.scenarios.set(scenario.name, scenario);
  }

  /**
   * Get registered scenario
   */
  getScenario(name: string): IntegrationScenario | undefined {
    return this.scenarios.get(name);
  }

  /**
   * Get all registered scenarios
   */
  getAllScenarios(): IntegrationScenario[] {
    return Array.from(this.scenarios.values());
  }

  /**
   * Get scenarios by tags
   */
  getScenariosByTags(tags: string[]): IntegrationScenario[] {
    return Array.from(this.scenarios.values()).filter((scenario) =>
      scenario.tags?.some((tag) => tags.includes(tag))
    );
  }

  /**
   * Create canvas scene integration test context
   */
  async createCanvasContext(): Promise<CanvasSceneContext> {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    let viewport: Viewport = { x: 0, y: 0, zoom: 1 };

    const flowInstance = {
      getNodes: () => [...nodes],
      getEdges: () => [...edges],
      setNodes: (newNodes: Node[]) => {
        nodes.length = 0;
        nodes.push(...newNodes);
      },
      setEdges: (newEdges: Edge[]) => {
        edges.length = 0;
        edges.push(...newEdges);
      },
      getViewport: () => ({ ...viewport }),
      setViewport: (newViewport: Viewport) => {
        viewport = { ...newViewport };
      },
    };

    const context: CanvasSceneContext = {
      wrapper: {},
      flowInstance,
      utils: {
        findNode: (id: string) => nodes.find((n) => n.id === id),
        findEdge: (id: string) => edges.find((e) => e.id === id),
        updateNode: (id: string, updates: Partial<Node>) => {
          const index = nodes.findIndex((n) => n.id === id);
          if (index !== -1) {
            nodes[index] = { ...nodes[index], ...updates };
          }
        },
        updateEdge: (id: string, updates: Partial<Edge>) => {
          const index = edges.findIndex((e) => e.id === id);
          if (index !== -1) {
            edges[index] = { ...edges[index], ...updates };
          }
        },
        updateViewport: (updates: Partial<Viewport>) => {
          viewport = { ...viewport, ...updates };
        },
        waitForUpdate: async () => {
          await new Promise((resolve) => setTimeout(resolve, 0));
        },
        cleanup: () => {
          nodes.length = 0;
          edges.length = 0;
          viewport = { x: 0, y: 0, zoom: 1 };
          this.currentContext = null;
        },
      },
      state: {
        get nodes() {
          return [...nodes];
        },
        get edges() {
          return [...edges];
        },
        get viewport() {
          return { ...viewport };
        },
      },
    };

    this.currentContext = context;
    return context;
  }

  /**
   * Get current canvas context
   */
  getCurrentContext(): CanvasSceneContext | null {
    return this.currentContext;
  }

  /**
   * Validate palette drag metadata
   */
  validatePaletteDrag(metadata: PaletteDragMetadata): {
    isValid: boolean;
    errors: string[];
  } {
    const errors: string[] = [];

    if (!metadata.componentType) {
      errors.push('Component type is required');
    }

    if (!metadata.startPosition) {
      errors.push('Start position is required');
    } else {
      if (typeof metadata.startPosition.x !== 'number') {
        errors.push('Start position x must be a number');
      }
      if (typeof metadata.startPosition.y !== 'number') {
        errors.push('Start position y must be a number');
      }
    }

    if (!metadata.dropPosition) {
      errors.push('Drop position is required');
    } else {
      if (typeof metadata.dropPosition.x !== 'number') {
        errors.push('Drop position x must be a number');
      }
      if (typeof metadata.dropPosition.y !== 'number') {
        errors.push('Drop position y must be a number');
      }
    }

    if (typeof metadata.dropSuccessful !== 'boolean') {
      errors.push('Drop successful flag is required');
    }

    if (metadata.dropSuccessful && !metadata.nodeId) {
      errors.push('Node ID is required when drop is successful');
    }

    if (!metadata.dropSuccessful && !metadata.error) {
      errors.push('Error message is required when drop fails');
    }

    if (metadata.duration !== undefined && metadata.duration < 0) {
      errors.push('Duration must be non-negative');
    }

    return {
      isValid: errors.length === 0,
      errors,
    };
  }

  /**
   * Validate update flow
   */
  async validateUpdateFlow(
    context: CanvasSceneContext,
    updates: {
      nodes?: Partial<Node>[];
      edges?: Partial<Edge>[];
      viewport?: Partial<Viewport>;
    }
  ): Promise<UpdateFlowValidation> {
    const errors: UpdateFlowValidation['errors'] = [];
    const warnings: UpdateFlowValidation['warnings'] = [];
    const startTime = Date.now();

    let nodesUpdated = 0;
    let edgesUpdated = 0;
    let viewportUpdated = false;

    // Validate node updates
    if (updates.nodes) {
      for (const nodeUpdate of updates.nodes) {
        if (!nodeUpdate.id) {
          errors.push({
            type: 'node',
            message: 'Node update missing ID',
            details: nodeUpdate,
          });
          continue;
        }

        const existingNode = context.utils.findNode(nodeUpdate.id as string);
        if (!existingNode) {
          errors.push({
            type: 'node',
            message: `Node not found: ${nodeUpdate.id}`,
            details: nodeUpdate,
          });
          continue;
        }

        // Validate position updates
        if (nodeUpdate.position) {
          if (
            typeof nodeUpdate.position.x !== 'number' ||
            typeof nodeUpdate.position.y !== 'number'
          ) {
            errors.push({
              type: 'node',
              message: `Invalid position for node ${nodeUpdate.id}`,
              details: nodeUpdate.position,
            });
          }
        }

        nodesUpdated++;
      }
    }

    // Validate edge updates
    if (updates.edges) {
      for (const edgeUpdate of updates.edges) {
        if (!edgeUpdate.id) {
          errors.push({
            type: 'edge',
            message: 'Edge update missing ID',
            details: edgeUpdate,
          });
          continue;
        }

        const existingEdge = context.utils.findEdge(edgeUpdate.id as string);
        if (!existingEdge) {
          errors.push({
            type: 'edge',
            message: `Edge not found: ${edgeUpdate.id}`,
            details: edgeUpdate,
          });
          continue;
        }

        // Validate source/target updates
        if (edgeUpdate.source) {
          const sourceNode = context.utils.findNode(edgeUpdate.source as string);
          if (!sourceNode) {
            errors.push({
              type: 'edge',
              message: `Source node not found: ${edgeUpdate.source}`,
              details: edgeUpdate,
            });
          }
        }

        if (edgeUpdate.target) {
          const targetNode = context.utils.findNode(edgeUpdate.target as string);
          if (!targetNode) {
            errors.push({
              type: 'edge',
              message: `Target node not found: ${edgeUpdate.target}`,
              details: edgeUpdate,
            });
          }
        }

        edgesUpdated++;
      }
    }

    // Validate viewport updates
    if (updates.viewport) {
      if (updates.viewport.x !== undefined && typeof updates.viewport.x !== 'number') {
        errors.push({
          type: 'viewport',
          message: 'Viewport x must be a number',
          details: updates.viewport,
        });
      }

      if (updates.viewport.y !== undefined && typeof updates.viewport.y !== 'number') {
        errors.push({
          type: 'viewport',
          message: 'Viewport y must be a number',
          details: updates.viewport,
        });
      }

      if (updates.viewport.zoom !== undefined) {
        if (typeof updates.viewport.zoom !== 'number') {
          errors.push({
            type: 'viewport',
            message: 'Viewport zoom must be a number',
            details: updates.viewport,
          });
        } else if (updates.viewport.zoom <= 0) {
          errors.push({
            type: 'viewport',
            message: 'Viewport zoom must be positive',
            details: updates.viewport,
          });
        } else if (updates.viewport.zoom > 10) {
          warnings.push({
            type: 'best-practice',
            message: 'Viewport zoom > 10 may cause rendering issues',
            details: updates.viewport,
          });
        }
      }

      viewportUpdated = true;
    }

    // Performance warnings
    const duration = Date.now() - startTime;
    if (nodesUpdated > 100) {
      warnings.push({
        type: 'performance',
        message: `Large number of node updates: ${nodesUpdated}`,
        details: { nodesUpdated },
      });
    }

    if (edgesUpdated > 100) {
      warnings.push({
        type: 'performance',
        message: `Large number of edge updates: ${edgesUpdated}`,
        details: { edgesUpdated },
      });
    }

    return {
      isValid: errors.length === 0,
      errors,
      warnings,
      stats: {
        nodesUpdated,
        edgesUpdated,
        viewportUpdated,
        duration,
      },
    };
  }

  /**
   * Run integration test scenario
   */
  async runScenario(scenarioName: string): Promise<IntegrationTestResults> {
    const scenario = this.scenarios.get(scenarioName);
    if (!scenario) {
      throw new Error(`Scenario not found: ${scenarioName}`);
    }

    const startTime = Date.now();
    const context = await this.createCanvasContext();
    const stepResults: IntegrationTestResults['steps'] = [];
    const errors: Error[] = [];
    let passed = true;

    try {
      // Run setup
      if (scenario.setup) {
        await this.runWithTimeout(
          () => scenario.setup(context),
          this.config.timeout,
          'Setup timeout'
        );
      }

      // Run steps
      for (const step of scenario.steps) {
        const stepStartTime = Date.now();
        let stepPassed = true;
        let stepError: Error | undefined;

        try {
          await this.runWithTimeout(
            () => step.action(context),
            this.config.timeout,
            `Action timeout: ${step.name}`
          );

          await this.runWithTimeout(
            () => step.assertions(context),
            this.config.timeout,
            `Assertions timeout: ${step.name}`
          );
        } catch (error) {
          stepPassed = false;
          stepError = error as Error;
          errors.push(stepError);
          passed = false;
        }

        stepResults.push({
          name: step.name,
          passed: stepPassed,
          duration: Date.now() - stepStartTime,
          error: stepError,
        });

        if (!stepPassed) {
          break;
        }
      }

      // Run teardown
      if (scenario.teardown) {
        try {
          await this.runWithTimeout(
            () => scenario.teardown!(context),
            this.config.timeout,
            'Teardown timeout'
          );
        } catch (error) {
          errors.push(error as Error);
        }
      }
    } catch (error) {
      errors.push(error as Error);
      passed = false;
    } finally {
      context.utils.cleanup();
    }

    const duration = Date.now() - startTime;

    const results: IntegrationTestResults = {
      scenario: scenarioName,
      passed,
      duration,
      steps: stepResults,
      errors,
      metadata: {
        timestamp: startTime,
        config: this.config,
        context: {
          nodeCount: context.state.nodes.length,
          edgeCount: context.state.edges.length,
          viewport: context.state.viewport,
        },
      },
    };

    this.results.set(scenarioName, results);
    return results;
  }

  /**
   * Run all scenarios
   */
  async runAllScenarios(): Promise<Map<string, IntegrationTestResults>> {
    const results = new Map<string, IntegrationTestResults>();

    for (const scenarioName of this.scenarios.keys()) {
      const result = await this.runScenario(scenarioName);
      results.set(scenarioName, result);
    }

    return results;
  }

  /**
   * Run scenarios by tags
   */
  async runScenariosByTags(tags: string[]): Promise<Map<string, IntegrationTestResults>> {
    const results = new Map<string, IntegrationTestResults>();
    const scenarios = this.getScenariosByTags(tags);

    for (const scenario of scenarios) {
      const result = await this.runScenario(scenario.name);
      results.set(scenario.name, result);
    }

    return results;
  }

  /**
   * Get test results
   */
  getResults(scenarioName?: string): IntegrationTestResults | Map<string, IntegrationTestResults> {
    if (scenarioName) {
      const result = this.results.get(scenarioName);
      if (!result) {
        throw new Error(`Results not found for scenario: ${scenarioName}`);
      }
      return result;
    }

    return new Map(this.results);
  }

  /**
   * Export results as JSON
   */
  exportResultsJSON(scenarioName?: string): string {
    if (scenarioName) {
      const result = this.results.get(scenarioName);
      if (!result) {
        throw new Error(`Results not found for scenario: ${scenarioName}`);
      }
      return JSON.stringify(result, null, 2);
    }

    return JSON.stringify(Object.fromEntries(this.results), null, 2);
  }

  /**
   * Export results as Markdown
   */
  exportResultsMarkdown(): string {
    const lines: string[] = [];

    lines.push('# Integration Test Results\n');
    lines.push(`Generated: ${new Date().toISOString()}\n`);

    const allResults = Array.from(this.results.values());
    const passed = allResults.filter((r) => r.passed).length;
    const failed = allResults.length - passed;

    lines.push('## Summary\n');
    lines.push(`- Total: ${allResults.length}`);
    lines.push(`- Passed: ${passed}`);
    lines.push(`- Failed: ${failed}\n`);

    lines.push('## Scenarios\n');

    for (const [name, result] of this.results) {
      const status = result.passed ? '✅' : '❌';
      lines.push(`### ${status} ${name}\n`);
      lines.push(`- Duration: ${result.duration}ms`);
      lines.push(`- Steps: ${result.steps.length}\n`);

      if (result.steps.length > 0) {
        lines.push('#### Steps\n');
        for (const step of result.steps) {
          const stepStatus = step.passed ? '✅' : '❌';
          lines.push(`- ${stepStatus} ${step.name} (${step.duration}ms)`);
          if (step.error) {
            lines.push(`  - Error: ${step.error.message}`);
          }
        }
        lines.push('');
      }

      if (result.errors.length > 0) {
        lines.push('#### Errors\n');
        for (const error of result.errors) {
          lines.push(`- ${error.message}`);
        }
        lines.push('');
      }
    }

    return lines.join('\n');
  }

  /**
   * Clear results
   */
  clearResults(scenarioName?: string): void {
    if (scenarioName) {
      this.results.delete(scenarioName);
    } else {
      this.results.clear();
    }
  }

  /**
   * Clear scenarios
   */
  clearScenarios(scenarioName?: string): void {
    if (scenarioName) {
      this.scenarios.delete(scenarioName);
    } else {
      this.scenarios.clear();
    }
  }

  /**
   * Reset manager (preserves config)
   */
  reset(): void {
    this.scenarios.clear();
    this.results.clear();
    this.currentContext = null;
  }

  /**
   * Run function with timeout
   */
  private async runWithTimeout<T>(
    fn: () => Promise<T> | T,
    timeout: number,
    message: string
  ): Promise<T> {
    return Promise.race([
      Promise.resolve(fn()),
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error(message)), timeout)
      ),
    ]);
  }
}

/**
 * Create integration tests manager
 */
export function createIntegrationTestsManager(
  config?: IntegrationTestConfig
): IntegrationTestsManager {
  return new IntegrationTestsManager(config);
}

/**
 * Create canvas scene context
 */
export async function createCanvasContext(): Promise<CanvasSceneContext> {
  const manager = createIntegrationTestsManager();
  return manager.createCanvasContext();
}

/**
 * Validate palette drag metadata
 */
export function validatePaletteDrag(metadata: PaletteDragMetadata): {
  isValid: boolean;
  errors: string[];
} {
  const manager = createIntegrationTestsManager();
  return manager.validatePaletteDrag(metadata);
}

/**
 * Validate update flow
 */
export async function validateUpdateFlow(
  context: CanvasSceneContext,
  updates: {
    nodes?: Partial<Node>[];
    edges?: Partial<Edge>[];
    viewport?: Partial<Viewport>;
  }
): Promise<UpdateFlowValidation> {
  const manager = createIntegrationTestsManager();
  return manager.validateUpdateFlow(context, updates);
}
