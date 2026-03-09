// Phase 8: Component Schema Registry - Comprehensive tests
// Tests for schema validation, migration, and integration

import { describe, test, expect, beforeEach } from 'vitest';

import {
  componentSchemaRegistry,
  validateComponent,
  createComponent,
  ComponentSchemaRegistry,
  CanvasComponent,
} from './component-registry';
import {
  validateImportData,
  validateExportData,
  getValidationStats,
  createCanvasAPIValidator,
  PRODUCTION_VALIDATION_CONFIG,
} from './validation-helpers';

import type {
  ProcessNode,
  DecisionNode,
  DatabaseNode,
  UIButton} from './component-registry';

describe.skip('Phase 8: Component Schema Registry', () => {
  beforeEach(() => {
    // Reset registry state if needed
  });

  describe('Component Schema Registry', () => {
    test('should be a singleton', () => {
      const registry1 = ComponentSchemaRegistry.getInstance();
      const registry2 = ComponentSchemaRegistry.getInstance();
      expect(registry1).toBe(registry2);
    });

    test('should have default schemas registered', () => {
      const schemas = componentSchemaRegistry.listSchemas();
      expect(schemas).toContain('process');
      expect(schemas).toContain('decision');
      expect(schemas).toContain('database');
      expect(schemas).toContain('group');
      expect(schemas).toContain('service');
      expect(schemas).toContain('ui-button');
      expect(schemas).toContain('ui-card');
      expect(schemas).toContain('ui-textfield');
      expect(schemas).toContain('edge');
    });

    test('should register custom schemas', () => {
      const customSchema = {
        parse: (data: unknown) => data,
        safeParse: (data: unknown) => ({ success: true, data }),
      };
      
      componentSchemaRegistry.registerSchema('custom-type', customSchema as unknown);
      const retrievedSchema = componentSchemaRegistry.getSchema('custom-type');
      expect(retrievedSchema).toBe(customSchema);
    });
  });

  describe('Component Validation', () => {
    test('should validate process node successfully', () => {
      const processData = {
        id: 'process-1',
        type: 'process',
        version: '1.0.0',
        position: { x: 100, y: 100 },
        size: { width: 200, height: 100 },
        rotation: 0,
        visible: true,
        locked: false,
        metadata: {},
        data: {
          label: 'Test Process',
          description: 'A test process',
          category: 'manual' as const,
          color: '#2196f3',
          tags: ['test'],
        },
      };

      const result = validateComponent('process', processData);
      expect(result.success).toBe(true);
      expect(result.data).toBeDefined();
      expect(result.errors).toHaveLength(0);
    });

    test('should fail validation for invalid process node', () => {
      const invalidData = {
        id: 'process-1',
        type: 'process',
        // Missing required fields
        data: {
          // Missing label
          category: 'invalid-category',
          color: 'invalid-color',
        },
      };

      const result = validateComponent('process', invalidData);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors.join(' ')).toContain('label');
    });

    test('should validate decision node with defaults', () => {
      const decisionData = {
        id: 'decision-1',
        type: 'decision',
        position: { x: 0, y: 0 },
        size: { width: 150, height: 80 },
        data: {
          label: 'Is valid?',
          question: 'Check if data is valid',
        },
      };

      const result = validateComponent('decision', decisionData);
      expect(result.success).toBe(true);
      if (result.success && result.data) {
        const decision = result.data as DecisionNode;
        expect(decision.data.trueLabel).toBe('Yes');
        expect(decision.data.falseLabel).toBe('No');
        expect(decision.data.color).toBe('#ff9800');
      }
    });

    test('should validate database node', () => {
      const dbData = {
        id: 'db-1',
        type: 'database',
        position: { x: 0, y: 0 },
        size: { width: 120, height: 80 },
        data: {
          label: 'User Database',
          dbType: 'sql' as const,
          technology: 'PostgreSQL',
          description: 'Main user data storage',
        },
      };

      const result = validateComponent('database', dbData);
      expect(result.success).toBe(true);
      if (result.success && result.data) {
        const db = result.data as DatabaseNode;
        expect(db.data.dbType).toBe('sql');
        expect(db.data.color).toBe('#4caf50');
      }
    });

    test('should validate UI button component', () => {
      const buttonData = {
        id: 'btn-1',
        type: 'ui-button',
        position: { x: 0, y: 0 },
        size: { width: 100, height: 40 },
        data: {
          text: 'Click Me',
          variant: 'contained' as const,
          color: 'primary' as const,
          size: 'medium' as const,
        },
      };

      const result = validateComponent('ui-button', buttonData);
      expect(result.success).toBe(true);
      if (result.success && result.data) {
        const button = result.data as UIButton;
        expect(button.data.text).toBe('Click Me');
        expect(button.data.disabled).toBe(false);
        expect(button.data.fullWidth).toBe(false);
      }
    });

    test('should reject unknown component type', () => {
      const unknownData = {
        id: 'unknown-1',
        type: 'unknown-type',
        position: { x: 0, y: 0 },
        size: { width: 100, height: 100 },
      };

      const result = validateComponent('unknown-type', unknownData);
      expect(result.success).toBe(false);
      expect(result.errors[0]).toContain('No schema registered for type');
    });
  });

  describe('Component Creation', () => {
    test('should create process component with defaults', () => {
      const component = createComponent('process', {
        position: { x: 50, y: 50 },
      });

      expect(component.type).toBe('process');
      expect(component.position).toEqual({ x: 50, y: 50 });
      expect((component as ProcessNode).data.label).toBe('New Process');
      expect((component as ProcessNode).data.category).toBe('manual');
      expect(component.id).toBeDefined();
      expect(component.version).toBe('1.0.0');
    });

    test('should create decision component with overrides', () => {
      const overrides = {
        data: {
          label: 'Custom Decision',
          question: 'Custom question?',
          trueLabel: 'Proceed',
          falseLabel: 'Stop',
        },
      };
      
      const component = createComponent('decision', overrides as unknown);

      expect(component.type).toBe('decision');
      expect((component as DecisionNode).data.label).toBe('Custom Decision');
      expect((component as DecisionNode).data.question).toBe('Custom question?');
      expect((component as DecisionNode).data.trueLabel).toBe('Proceed');
      expect((component as DecisionNode).data.falseLabel).toBe('Stop');
    });

    test('should fail to create component with invalid type', () => {
      expect(() => {
        createComponent('invalid-type');
      }).toThrow('No default data registered for type');
    });
  });

  describe('Import/Export Validation', () => {
    test('should validate valid import data', () => {
      const importData = {
        nodes: [
          {
            id: 'node-1',
            type: 'process',
            position: { x: 0, y: 0 },
            size: { width: 200, height: 100 },
            data: {
              label: 'Process 1',
              category: 'manual',
              color: '#2196f3',
              tags: [],
            },
          },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            type: 'smoothstep',
            animated: false,
            data: {},
          },
        ],
      };

      const result = validateImportData(importData);
      expect(result.success).toBe(true);
      expect(result.data?.nodes).toHaveLength(1);
      expect(result.data?.edges).toHaveLength(1);
      expect(result.errors).toHaveLength(0);
    });

    test('should fail validation for invalid import data', () => {
      const invalidImportData = {
        nodes: [
          {
            id: 'node-1',
            type: 'process',
            // Missing required fields
            data: {
              // Missing label
              category: 'invalid',
            },
          },
        ],
        edges: [],
      };

      const result = validateImportData(invalidImportData);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    test('should validate export data', () => {
      const nodes = [createComponent('process')];
      const edges: unknown[] = [];

      const result = validateExportData(nodes, edges, {
        validateBeforeExport: true,
      });

      expect(result.success).toBe(true);
      expect(result.data?.nodes).toHaveLength(1);
      expect(result.data?.edges).toHaveLength(0);
    });
  });

  describe('Canvas Validation Statistics', () => {
    test('should calculate validation statistics', () => {
      const canvas = {
        nodes: [
          createComponent('process'),
          createComponent('decision'),
          {
            id: 'invalid-node',
            type: 'process',
            // Missing required fields
          },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            type: 'smoothstep',
            animated: false,
            data: {},
          },
        ],
      };

      const stats = getValidationStats(canvas);
      
      expect(stats.nodes.total).toBe(3);
      expect(stats.nodes.valid).toBe(2); // Two valid nodes
      expect(stats.nodes.invalid).toBe(1); // One invalid node
      expect(stats.edges.total).toBe(1);
      expect(stats.edges.valid).toBe(1);
      expect(stats.overall.healthy).toBe(false); // Due to invalid node
    });
  });

  describe('Canvas API Validator', () => {
    test('should create validator with production config', () => {
      const validator = createCanvasAPIValidator(PRODUCTION_VALIDATION_CONFIG);
      
      expect(validator).toHaveProperty('validateCreate');
      expect(validator).toHaveProperty('validateUpdate');
      expect(validator).toHaveProperty('validateImport');
      expect(validator).toHaveProperty('validateBatch');
    });

    test('should validate create operation', () => {
      const validator = createCanvasAPIValidator(PRODUCTION_VALIDATION_CONFIG);
      
      const validData = {
        id: 'process-1',
        type: 'process',
        position: { x: 0, y: 0 },
        size: { width: 200, height: 100 },
        data: {
          label: 'Test Process',
          category: 'manual',
          color: '#2196f3',
          tags: [],
        },
      };

      const result = validator.validateCreate('process', validData);
      expect(result.success).toBe(true);
    });

    test('should validate batch operations', () => {
      const validator = createCanvasAPIValidator(PRODUCTION_VALIDATION_CONFIG);
      
      const operations = [
        {
          type: 'create' as const,
          nodeType: 'process',
          data: createComponent('process'),
        },
        {
          type: 'update' as const,
          nodeType: 'decision',
          data: createComponent('decision'),
        },
        {
          type: 'create' as const,
          nodeType: 'invalid',
          data: { invalid: 'data' },
        },
      ];

      const result = validator.validateBatch(operations);
      expect(result.success).toBe(false); // Due to invalid operation
      expect(result.results).toHaveLength(3);
      expect(result.results[0].success).toBe(true);
      expect(result.results[1].success).toBe(true);
      expect(result.results[2].success).toBe(false);
    });
  });

  describe('Default Data', () => {
    test('should provide default data for all component types', () => {
      const types = ['process', 'decision', 'database', 'group', 'service', 'ui-button', 'ui-card', 'ui-textfield'];
      
      for (const type of types) {
        const defaultData = componentSchemaRegistry.getDefaultData(type);
        expect(defaultData).toBeDefined();
        expect(defaultData?.type).toBe(type);
      }
    });

    test('should register custom default data', () => {
      const customFactory = () => ({
        type: 'custom' as unknown,
        data: { custom: 'value' },
      });

      (componentSchemaRegistry as unknown).setDefaultData('custom', customFactory);
      const defaultData = (componentSchemaRegistry as unknown).getDefaultData('custom');
      
      expect(defaultData).toBeDefined();
      expect(defaultData?.type).toBe('custom');
      expect((defaultData as unknown)?.data.custom).toBe('value');
    });
  });

  describe('Edge Cases and Error Handling', () => {
    test('should handle malformed data gracefully', () => {
      const malformedData = null;
      const result = validateComponent('process', malformedData);
      
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    test('should handle circular references in validation', () => {
      const circularData: any = {
        id: 'circular',
        type: 'process',
        position: { x: 0, y: 0 },
        size: { width: 100, height: 100 },
        data: {
          label: 'Circular',
          category: 'manual',
          color: '#2196f3',
          tags: [],
        },
      };
      
      // Create circular reference
      circularData.self = circularData;
      
      // Should not crash
      const result = validateComponent('process', circularData);
      // May succeed or fail depending on Zod handling, but shouldn't crash
      expect(typeof result.success).toBe('boolean');
    });

    test('should validate edge with missing nodes warning', () => {
      const importData = {
        nodes: [
          {
            id: 'node-1',
            type: 'process',
            position: { x: 0, y: 0 },
            size: { width: 200, height: 100 },
            data: {
              label: 'Process 1',
              category: 'manual',
              color: '#2196f3',
              tags: [],
            },
          },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'non-existent-node', // References non-existent node
            type: 'smoothstep',
            animated: false,
            data: {},
          },
        ],
      };

      const result = validateImportData(importData);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings.some(w => w.includes('non-existent target node'))).toBe(true);
    });
  });
});