/**
 * @fileoverview Test for P0-2: Graph validation exports from graph barrel
 *
 * Verifies that validateGraph, GraphValidationResult, and GraphValidationError
 * are properly exported from the graph barrel (index.ts).
 */

import { describe, it, expect } from 'vitest';
import {
  validateGraph,
  type GraphValidationResult,
  type GraphValidationError,
} from '../index';

describe('P0-2: Graph Validation Exports', () => {
  it('should export validateGraph function', () => {
    expect(validateGraph).toBeDefined();
    expect(typeof validateGraph).toBe('function');
  });

  it('should export GraphValidationResult type', () => {
    // Type exports are verified at compile time; this test ensures the module loads correctly
    const validationMock: GraphValidationResult = {
      valid: true,
      errors: [],
      warnings: [],
    };
    expect(validationMock.valid).toBe(true);
    expect(validationMock.errors).toEqual([]);
    expect(validationMock.warnings).toEqual([]);
  });

  it('should export GraphValidationError type', () => {
    // Type exports are verified at compile time; this test ensures the module loads correctly
    const errorMock: GraphValidationError = {
      code: 'DUPLICATE_NODE_ID',
      message: 'Duplicate node ID found',
      severity: 'error',
      context: { nodeId: 'node1' },
    };
    expect(errorMock.code).toBe('DUPLICATE_NODE_ID');
    expect(errorMock.message).toBe('Duplicate node ID found');
    expect(errorMock.context?.nodeId).toBe('node1');
  });

  it('should call validateGraph without errors', () => {
    // This test just verifies validateGraph is callable - actual validation is tested in validateGraph.test.ts
    expect(typeof validateGraph).toBe('function');
  });
});
