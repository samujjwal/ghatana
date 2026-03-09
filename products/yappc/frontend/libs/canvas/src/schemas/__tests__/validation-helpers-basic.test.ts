// Phase 8: Validation Helpers - Basic Tests
// Tests for core validation functionality

import { describe, test, expect } from 'vitest';

import { createComponent } from './component-registry';
import {
  validateImportData,
  validateExportData,
  migrateCanvas,
  getValidationStats,
  createCanvasAPIValidator,
  PRODUCTION_VALIDATION_CONFIG,
  DEVELOPMENT_VALIDATION_CONFIG,
} from './validation-helpers';

describe.skip('Phase 8: Validation Helpers - Basic Tests', () => {
  const mockValidCanvas = {
    nodes: [
      createComponent('process'),
      createComponent('decision'),
    ],
    edges: [
      {
        id: 'edge-1',
        source: 'node-1',
        target: 'node-2',
        type: 'smoothstep' as const,
        animated: false,
        data: { metadata: {} },
      },
    ],
  };

  describe('Import Data Validation', () => {
    test('should validate valid import data', () => {
      const result = validateImportData(mockValidCanvas);
      
      expect(result.success).toBe(true);
      expect(result.data).toBeDefined();
      expect(result.errors).toHaveLength(0);
    });

    test('should handle empty canvas', () => {
      const emptyCanvas = { nodes: [], edges: [] };
      const result = validateImportData(emptyCanvas);
      
      expect(result.success).toBe(true);
      expect(result.data?.nodes).toHaveLength(0);
      expect(result.data?.edges).toHaveLength(0);
    });

    test('should handle malformed input', () => {
      const result = validateImportData(null as unknown);
      
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('Export Data Validation', () => {
    test('should validate export data successfully', () => {
      const validEdges = [
        {
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          type: 'smoothstep' as const,
          animated: false,
          data: { metadata: {} },
        },
      ];

      const result = validateExportData(mockValidCanvas.nodes, validEdges);
      
      expect(result.success).toBe(true);
      expect(result.data?.nodes).toHaveLength(2);
      expect(result.data?.edges).toHaveLength(1);
    });

    test('should handle validation options', () => {
      const validEdges = [
        {
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          type: 'smoothstep' as const,
          animated: false,
          data: { metadata: {} },
        },
      ];

      const result = validateExportData(mockValidCanvas.nodes, validEdges, {
        validateBeforeExport: false,
        includeMetadata: true,
      });
      
      expect(result.success).toBe(true);
    });
  });

  describe('Canvas Migration', () => {
    test('should migrate canvas data', () => {
      const v1Canvas = {
        nodes: [createComponent('process')],
        edges: [],
      };

      const result = migrateCanvas(v1Canvas, { dryRun: false });
      
      expect(result.success).toBe(true);
      expect(result.data).toBeDefined();
    });

    test('should handle migration errors', () => {
      const corruptedCanvas = null;

      const result = migrateCanvas(corruptedCanvas as unknown, { dryRun: false });
      
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('Validation Statistics', () => {
    test('should calculate statistics for healthy canvas', () => {
      const stats = getValidationStats(mockValidCanvas);
      
      expect(stats.nodes.total).toBeGreaterThan(0);
      expect(stats.nodes.valid).toBeGreaterThan(0);
      expect(stats.edges.total).toBeGreaterThan(0);
      expect(stats.overall.healthy).toBe(true);
    });

    test('should handle empty canvas statistics', () => {
      const emptyCanvas = { nodes: [], edges: [] };
      const stats = getValidationStats(emptyCanvas);
      
      expect(stats.nodes.total).toBe(0);
      expect(stats.edges.total).toBe(0);
      expect(stats.overall.healthy).toBe(true);
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
      
      const validData = createComponent('process');
      const result = validator.validateCreate('process', validData);
      
      expect(result.success).toBe(true);
    });

    test('should validate update operation', () => {
      const validator = createCanvasAPIValidator(PRODUCTION_VALIDATION_CONFIG);
      
      const updateData = {
        data: { label: 'Updated Process' },
      };

      const result = validator.validateUpdate('process', updateData);
      expect(result.success).toBe(true);
    });

    test('should validate import operation', () => {
      const validator = createCanvasAPIValidator(PRODUCTION_VALIDATION_CONFIG);
      
      const result = validator.validateImport(mockValidCanvas);
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
          data: { data: { label: 'Updated Decision' } },
        },
      ];

      const result = validator.validateBatch(operations);
      
      expect(result.success).toBe(true);
      expect(result.results).toHaveLength(2);
    });

    test('should use development config', () => {
      const validator = createCanvasAPIValidator(DEVELOPMENT_VALIDATION_CONFIG);
      
      expect(validator).toBeDefined();
      expect(typeof validator.validateCreate).toBe('function');
    });
  });

  describe('Configuration', () => {
    test('should have production config', () => {
      expect(PRODUCTION_VALIDATION_CONFIG).toBeDefined();
      expect(PRODUCTION_VALIDATION_CONFIG.strictMode).toBe(true);
    });

    test('should have development config', () => {
      expect(DEVELOPMENT_VALIDATION_CONFIG).toBeDefined();
      expect(DEVELOPMENT_VALIDATION_CONFIG.strictMode).toBe(false);
    });
  });

  describe('Error Handling', () => {
    test('should handle null input gracefully', () => {
      const result = validateImportData(null as unknown);
      
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    test('should handle undefined input gracefully', () => {
      const result = validateImportData(undefined as unknown);
      
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    test('should provide meaningful error messages', () => {
      const invalidCanvas = {
        nodes: [
          {
            id: 'invalid',
            type: 'process',
            // Missing required fields
          },
        ],
        edges: [],
      };

      const result = validateImportData(invalidCanvas as unknown);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toBeTruthy();
    });
  });
});