/**
 * Integration Tests - Test Suite
 * 
 * Comprehensive tests for integration testing utilities.
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createIntegrationTestsManager,
  createCanvasContext,
  validatePaletteDrag,
  validateUpdateFlow,
  type IntegrationTestConfig,
  type IntegrationScenario,
  type PaletteDragMetadata,
  type CanvasSceneContext
} from '../integrationTests';
import type {

  IntegrationTestsManager
} from '../integrationTests';

import type { Node, Edge } from '@xyflow/react';

describe('IntegrationTestsManager', () => {
  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const manager = createIntegrationTestsManager();
      const config = manager.getConfig();

      expect(config.debug).toBe(false);
      expect(config.timeout).toBe(5000);
      expect(config.strictProps).toBe(true);
      expect(config.flowProviderProps).toEqual({});
      expect(config.mocks).toEqual({});
    });

    it('should initialize with custom configuration', () => {
      const customConfig: IntegrationTestConfig = {
        debug: true,
        timeout: 10000,
        strictProps: false,
        flowProviderProps: { fitView: true },
        mocks: {
          localStorage: {} as Storage,
        },
      };

      const manager = createIntegrationTestsManager(customConfig);
      const config = manager.getConfig();

      expect(config.debug).toBe(true);
      expect(config.timeout).toBe(10000);
      expect(config.strictProps).toBe(false);
      expect(config.flowProviderProps).toEqual({ fitView: true });
      expect(config.mocks).toHaveProperty('localStorage');
    });

    it('should update configuration', () => {
      const manager = createIntegrationTestsManager();

      manager.updateConfig({
        debug: true,
        timeout: 8000,
      });

      const config = manager.getConfig();
      expect(config.debug).toBe(true);
      expect(config.timeout).toBe(8000);
      expect(config.strictProps).toBe(true); // unchanged
    });
  });

  describe('Canvas Context', () => {
    it('should create canvas context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      expect(context).toBeDefined();
      expect(context.flowInstance).toBeDefined();
      expect(context.utils).toBeDefined();
      expect(context.state).toBeDefined();

      expect(context.state.nodes).toEqual([]);
      expect(context.state.edges).toEqual([]);
      expect(context.state.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
    });

    it('should store current context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      expect(manager.getCurrentContext()).toBe(context);
    });

    it('should manage nodes in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      const nodes: Node[] = [
        {
          id: '1',
          type: 'default',
          position: { x: 0, y: 0 },
          data: { label: 'Node 1' },
        },
        {
          id: '2',
          type: 'default',
          position: { x: 100, y: 100 },
          data: { label: 'Node 2' },
        },
      ];

      context.flowInstance.setNodes(nodes);

      expect(context.state.nodes).toHaveLength(2);
      expect(context.utils.findNode('1')).toEqual(nodes[0]);
      expect(context.utils.findNode('2')).toEqual(nodes[1]);
    });

    it('should update nodes in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      const nodes: Node[] = [
        {
          id: '1',
          type: 'default',
          position: { x: 0, y: 0 },
          data: { label: 'Node 1' },
        },
      ];

      context.flowInstance.setNodes(nodes);
      context.utils.updateNode('1', { position: { x: 50, y: 50 } });

      const updatedNode = context.utils.findNode('1');
      expect(updatedNode?.position).toEqual({ x: 50, y: 50 });
    });

    it('should manage edges in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      const edges: Edge[] = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
        },
      ];

      context.flowInstance.setEdges(edges);

      expect(context.state.edges).toHaveLength(1);
      expect(context.utils.findEdge('e1-2')).toEqual(edges[0]);
    });

    it('should update edges in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      const edges: Edge[] = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
        },
      ];

      context.flowInstance.setEdges(edges);
      context.utils.updateEdge('e1-2', { animated: true });

      const updatedEdge = context.utils.findEdge('e1-2');
      expect(updatedEdge?.animated).toBe(true);
    });

    it('should manage viewport in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      context.flowInstance.setViewport({ x: 100, y: 200, zoom: 1.5 });

      expect(context.state.viewport).toEqual({ x: 100, y: 200, zoom: 1.5 });
    });

    it('should update viewport in context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      context.utils.updateViewport({ x: 150, y: 250 });

      const viewport = context.flowInstance.getViewport();
      expect(viewport.x).toBe(150);
      expect(viewport.y).toBe(250);
      expect(viewport.zoom).toBe(1); // unchanged
    });

    it('should cleanup context', async () => {
      const manager = createIntegrationTestsManager();
      const context = await manager.createCanvasContext();

      const nodes: Node[] = [
        {
          id: '1',
          type: 'default',
          position: { x: 0, y: 0 },
          data: { label: 'Node 1' },
        },
      ];

      context.flowInstance.setNodes(nodes);
      context.utils.cleanup();

      expect(context.state.nodes).toEqual([]);
      expect(context.state.edges).toEqual([]);
      expect(context.state.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
      expect(manager.getCurrentContext()).toBeNull();
    });
  });

  describe('Palette Drag Validation', () => {
    it('should validate valid palette drag metadata', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
        nodeId: 'node-1',
        duration: 500,
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should detect missing component type', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: '',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
        nodeId: 'node-1',
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Component type is required');
    });

    it('should detect invalid start position', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 'invalid' as unknown, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
        nodeId: 'node-1',
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Start position x must be a number');
    });

    it('should detect invalid drop position', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 'invalid' as unknown },
        dropSuccessful: true,
        nodeId: 'node-1',
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Drop position y must be a number');
    });

    it('should detect missing node ID on successful drop', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Node ID is required when drop is successful');
    });

    it('should detect missing error on failed drop', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: false,
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Error message is required when drop fails');
    });

    it('should detect negative duration', () => {
      const manager = createIntegrationTestsManager();
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
        nodeId: 'node-1',
        duration: -100,
      };

      const result = manager.validatePaletteDrag(metadata);

      expect(result.isValid).toBe(false);
      expect(result.errors).toContain('Duration must be non-negative');
    });
  });

  describe('Update Flow Validation', () => {
    let context: CanvasSceneContext;
    let manager: IntegrationTestsManager;

    beforeEach(async () => {
      manager = createIntegrationTestsManager();
      context = await manager.createCanvasContext();

      const nodes: Node[] = [
        {
          id: '1',
          type: 'default',
          position: { x: 0, y: 0 },
          data: { label: 'Node 1' },
        },
        {
          id: '2',
          type: 'default',
          position: { x: 100, y: 100 },
          data: { label: 'Node 2' },
        },
      ];

      const edges: Edge[] = [
        {
          id: 'e1-2',
          source: '1',
          target: '2',
        },
      ];

      context.flowInstance.setNodes(nodes);
      context.flowInstance.setEdges(edges);
    });

    it('should validate node updates', async () => {
      const result = await manager.validateUpdateFlow(context, {
        nodes: [
          { id: '1', position: { x: 50, y: 50 } },
        ],
      });

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
      expect(result.stats.nodesUpdated).toBe(1);
    });

    it('should detect missing node ID', async () => {
      const result = await manager.validateUpdateFlow(context, {
        nodes: [
          { position: { x: 50, y: 50 } } as unknown,
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('node');
      expect(result.errors[0].message).toBe('Node update missing ID');
    });

    it('should detect non-existent node', async () => {
      const result = await manager.validateUpdateFlow(context, {
        nodes: [
          { id: '999', position: { x: 50, y: 50 } },
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('node');
      expect(result.errors[0].message).toBe('Node not found: 999');
    });

    it('should detect invalid node position', async () => {
      const result = await manager.validateUpdateFlow(context, {
        nodes: [
          { id: '1', position: { x: 'invalid' as unknown, y: 50 } },
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('node');
      expect(result.errors[0].message).toBe('Invalid position for node 1');
    });

    it('should validate edge updates', async () => {
      const result = await manager.validateUpdateFlow(context, {
        edges: [
          { id: 'e1-2', animated: true },
        ],
      });

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
      expect(result.stats.edgesUpdated).toBe(1);
    });

    it('should detect missing edge ID', async () => {
      const result = await manager.validateUpdateFlow(context, {
        edges: [
          { animated: true } as unknown,
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('edge');
      expect(result.errors[0].message).toBe('Edge update missing ID');
    });

    it('should detect non-existent edge', async () => {
      const result = await manager.validateUpdateFlow(context, {
        edges: [
          { id: 'e999', animated: true },
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('edge');
      expect(result.errors[0].message).toBe('Edge not found: e999');
    });

    it('should detect invalid edge source', async () => {
      const result = await manager.validateUpdateFlow(context, {
        edges: [
          { id: 'e1-2', source: '999' },
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('edge');
      expect(result.errors[0].message).toBe('Source node not found: 999');
    });

    it('should detect invalid edge target', async () => {
      const result = await manager.validateUpdateFlow(context, {
        edges: [
          { id: 'e1-2', target: '999' },
        ],
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('edge');
      expect(result.errors[0].message).toBe('Target node not found: 999');
    });

    it('should validate viewport updates', async () => {
      const result = await manager.validateUpdateFlow(context, {
        viewport: { x: 100, y: 200, zoom: 1.5 },
      });

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
      expect(result.stats.viewportUpdated).toBe(true);
    });

    it('should detect invalid viewport x', async () => {
      const result = await manager.validateUpdateFlow(context, {
        viewport: { x: 'invalid' as unknown },
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('viewport');
      expect(result.errors[0].message).toBe('Viewport x must be a number');
    });

    it('should detect invalid viewport zoom', async () => {
      const result = await manager.validateUpdateFlow(context, {
        viewport: { zoom: 0 },
      });

      expect(result.isValid).toBe(false);
      expect(result.errors).toHaveLength(1);
      expect(result.errors[0].type).toBe('viewport');
      expect(result.errors[0].message).toBe('Viewport zoom must be positive');
    });

    it('should warn about excessive zoom', async () => {
      const result = await manager.validateUpdateFlow(context, {
        viewport: { zoom: 15 },
      });

      expect(result.isValid).toBe(true);
      expect(result.warnings).toHaveLength(1);
      expect(result.warnings[0].type).toBe('best-practice');
      expect(result.warnings[0].message).toContain('Viewport zoom > 10');
    });

    it('should warn about large node updates', async () => {
      const nodeUpdates = Array.from({ length: 150 }, (_, i) => ({
        id: `${i + 1}`,
        position: { x: i * 10, y: i * 10 },
      }));

      // Add nodes to context first
      const nodes: Node[] = nodeUpdates.map((update) => ({
        id: update.id,
        type: 'default',
        position: update.position,
        data: {},
      }));
      context.flowInstance.setNodes(nodes);

      const result = await manager.validateUpdateFlow(context, {
        nodes: nodeUpdates,
      });

      expect(result.warnings.some((w) => w.type === 'performance')).toBe(true);
    });
  });

  describe('Scenario Management', () => {
    it('should register scenario', () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Test Scenario',
        description: 'A test scenario',
        setup: () => { },
        steps: [],
      };

      manager.registerScenario(scenario);

      expect(manager.getScenario('Test Scenario')).toBe(scenario);
    });

    it('should get all scenarios', () => {
      const manager = createIntegrationTestsManager();
      const scenarios: IntegrationScenario[] = [
        {
          name: 'Scenario 1',
          description: 'First scenario',
          setup: () => { },
          steps: [],
        },
        {
          name: 'Scenario 2',
          description: 'Second scenario',
          setup: () => { },
          steps: [],
        },
      ];

      scenarios.forEach((s) => manager.registerScenario(s));

      expect(manager.getAllScenarios()).toHaveLength(2);
    });

    it('should get scenarios by tags', () => {
      const manager = createIntegrationTestsManager();
      const scenarios: IntegrationScenario[] = [
        {
          name: 'Scenario 1',
          description: 'First scenario',
          setup: () => { },
          steps: [],
          tags: ['smoke', 'critical'],
        },
        {
          name: 'Scenario 2',
          description: 'Second scenario',
          setup: () => { },
          steps: [],
          tags: ['integration'],
        },
      ];

      scenarios.forEach((s) => manager.registerScenario(s));

      const smokeScenarios = manager.getScenariosByTags(['smoke']);
      expect(smokeScenarios).toHaveLength(1);
      expect(smokeScenarios[0].name).toBe('Scenario 1');
    });

    it('should run scenario successfully', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Success Scenario',
        description: 'A scenario that passes',
        setup: async (context) => {
          const nodes: Node[] = [
            {
              id: '1',
              type: 'default',
              position: { x: 0, y: 0 },
              data: {},
            },
          ];
          context.flowInstance.setNodes(nodes);
        },
        steps: [
          {
            name: 'Step 1',
            action: async (context) => {
              context.utils.updateNode('1', { position: { x: 50, y: 50 } });
            },
            assertions: async (context) => {
              const node = context.utils.findNode('1');
              expect(node?.position).toEqual({ x: 50, y: 50 });
            },
          },
        ],
      };

      manager.registerScenario(scenario);
      const results = await manager.runScenario('Success Scenario');

      expect(results.passed).toBe(true);
      expect(results.steps).toHaveLength(1);
      expect(results.steps[0].passed).toBe(true);
      expect(results.errors).toEqual([]);
    });

    it('should handle scenario failure', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Failure Scenario',
        description: 'A scenario that fails',
        setup: () => { },
        steps: [
          {
            name: 'Failing Step',
            action: async () => {
              throw new Error('Step failed');
            },
            assertions: async () => { },
          },
        ],
      };

      manager.registerScenario(scenario);
      const results = await manager.runScenario('Failure Scenario');

      expect(results.passed).toBe(false);
      expect(results.steps).toHaveLength(1);
      expect(results.steps[0].passed).toBe(false);
      expect(results.errors).toHaveLength(1);
    });

    it('should run all scenarios', async () => {
      const manager = createIntegrationTestsManager();
      const scenarios: IntegrationScenario[] = [
        {
          name: 'Scenario 1',
          description: 'First scenario',
          setup: () => { },
          steps: [
            {
              name: 'Step 1',
              action: async () => { },
              assertions: async () => { },
            },
          ],
        },
        {
          name: 'Scenario 2',
          description: 'Second scenario',
          setup: () => { },
          steps: [
            {
              name: 'Step 1',
              action: async () => { },
              assertions: async () => { },
            },
          ],
        },
      ];

      scenarios.forEach((s) => manager.registerScenario(s));
      const results = await manager.runAllScenarios();

      expect(results.size).toBe(2);
      expect(results.get('Scenario 1')?.passed).toBe(true);
      expect(results.get('Scenario 2')?.passed).toBe(true);
    });
  });

  describe('Results Management', () => {
    it('should export results as JSON', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Test Scenario',
        description: 'A test scenario',
        setup: () => { },
        steps: [],
      };

      manager.registerScenario(scenario);
      await manager.runScenario('Test Scenario');

      const json = manager.exportResultsJSON('Test Scenario');
      const results = JSON.parse(json);

      expect(results.scenario).toBe('Test Scenario');
      expect(results.passed).toBeDefined();
    });

    it('should export all results as JSON', async () => {
      const manager = createIntegrationTestsManager();
      const scenarios: IntegrationScenario[] = [
        {
          name: 'Scenario 1',
          description: 'First scenario',
          setup: () => { },
          steps: [],
        },
        {
          name: 'Scenario 2',
          description: 'Second scenario',
          setup: () => { },
          steps: [],
        },
      ];

      scenarios.forEach((s) => manager.registerScenario(s));
      await manager.runAllScenarios();

      const json = manager.exportResultsJSON();
      const results = JSON.parse(json);

      expect(Object.keys(results)).toHaveLength(2);
      expect(results['Scenario 1']).toBeDefined();
      expect(results['Scenario 2']).toBeDefined();
    });

    it('should export results as Markdown', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Test Scenario',
        description: 'A test scenario',
        setup: () => { },
        steps: [
          {
            name: 'Step 1',
            action: async () => { },
            assertions: async () => { },
          },
        ],
      };

      manager.registerScenario(scenario);
      await manager.runScenario('Test Scenario');

      const markdown = manager.exportResultsMarkdown();

      expect(markdown).toContain('# Integration Test Results');
      expect(markdown).toContain('Test Scenario');
      expect(markdown).toContain('Step 1');
    });

    it('should clear specific results', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Test Scenario',
        description: 'A test scenario',
        setup: () => { },
        steps: [],
      };

      manager.registerScenario(scenario);
      await manager.runScenario('Test Scenario');

      manager.clearResults('Test Scenario');

      expect(() => manager.getResults('Test Scenario')).toThrow();
    });

    it('should clear all results', async () => {
      const manager = createIntegrationTestsManager();
      const scenarios: IntegrationScenario[] = [
        {
          name: 'Scenario 1',
          description: 'First scenario',
          setup: () => { },
          steps: [],
        },
        {
          name: 'Scenario 2',
          description: 'Second scenario',
          setup: () => { },
          steps: [],
        },
      ];

      scenarios.forEach((s) => manager.registerScenario(s));
      await manager.runAllScenarios();

      manager.clearResults();

      const results = manager.getResults() as Map<string, unknown>;
      expect(results.size).toBe(0);
    });
  });

  describe('Helper Functions', () => {
    it('should create canvas context via helper', async () => {
      const context = await createCanvasContext();

      expect(context).toBeDefined();
      expect(context.flowInstance).toBeDefined();
      expect(context.utils).toBeDefined();
      expect(context.state).toBeDefined();
    });

    it('should validate palette drag via helper', () => {
      const metadata: PaletteDragMetadata = {
        componentType: 'CustomNode',
        startPosition: { x: 10, y: 20 },
        dropPosition: { x: 100, y: 200 },
        dropSuccessful: true,
        nodeId: 'node-1',
      };

      const result = validatePaletteDrag(metadata);

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
    });

    it('should validate update flow via helper', async () => {
      const context = await createCanvasContext();
      const nodes: Node[] = [
        {
          id: '1',
          type: 'default',
          position: { x: 0, y: 0 },
          data: {},
        },
      ];
      context.flowInstance.setNodes(nodes);

      const result = await validateUpdateFlow(context, {
        nodes: [
          { id: '1', position: { x: 50, y: 50 } },
        ],
      });

      expect(result.isValid).toBe(true);
      expect(result.errors).toEqual([]);
    });
  });

  describe('Reset and Cleanup', () => {
    it('should reset manager', async () => {
      const manager = createIntegrationTestsManager();
      const scenario: IntegrationScenario = {
        name: 'Test Scenario',
        description: 'A test scenario',
        setup: () => { },
        steps: [],
      };

      manager.registerScenario(scenario);
      await manager.runScenario('Test Scenario');
      await manager.createCanvasContext();

      manager.reset();

      expect(manager.getAllScenarios()).toHaveLength(0);
      expect((manager.getResults() as Map<string, unknown>).size).toBe(0);
      expect(manager.getCurrentContext()).toBeNull();
    });

    it('should preserve config on reset', () => {
      const manager = createIntegrationTestsManager({ debug: true, timeout: 8000 });

      manager.reset();

      const config = manager.getConfig();
      expect(config.debug).toBe(true);
      expect(config.timeout).toBe(8000);
    });
  });
});
