/**
 * @fileoverview Round-trip tests for action schema
 *
 * Tests that builder actions can be validated correctly.
 *
 * @doc.type test
 * @doc.purpose Action schema validation tests
 * @doc.layer platform
 */

import { describe, it, expect } from 'vitest';
import {
  validateBuilderAction,
  validateActionContext,
  validateActionManagerConfig,
  type AddNodeAction,
  type RemoveNodeAction,
} from '../action.schema.js';

describe('Action Schema', () => {
  describe('action validation', () => {
    it('should validate a valid add-node action', () => {
      const action: AddNodeAction = {
        id: 'action-1',
        type: 'add-node',
        timestamp: new Date().toISOString(),
        description: 'Add a new node',
        nodeId: 'node-1',
        parentId: 'root',
        component: {} as any,
        position: { x: 10, y: 10 },
        size: { width: 100, height: 50 },
      };

      const result = validateBuilderAction(action);
      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate a valid remove-node action', () => {
      const action: RemoveNodeAction = {
        id: 'action-2',
        type: 'remove-node',
        timestamp: new Date().toISOString(),
        description: 'Remove a node',
        nodeId: 'node-1',
        component: {} as any,
      };

      const result = validateBuilderAction(action);
      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject action with missing required fields', () => {
      const action = {
        id: 'action-1',
        type: 'add-node',
        // Missing timestamp, description, nodeId, etc.
      };

      const result = validateBuilderAction(action);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should reject action with invalid timestamp', () => {
      const action = {
        id: 'action-1',
        type: 'add-node',
        timestamp: 'not-a-date',
        description: 'Test',
        nodeId: 'node-1',
        parentId: 'root',
        component: {},
      };

      const result = validateBuilderAction(action);
      expect(result.success).toBe(false);
    });

    it('should reject action with invalid type', () => {
      const action = {
        id: 'action-1',
        type: 'invalid-action-type',
        timestamp: new Date().toISOString(),
        description: 'Test',
      };

      const result = validateBuilderAction(action);
      expect(result.success).toBe(false);
    });
  });

  describe('action context validation', () => {
    it('should validate a valid action context', () => {
      const context = {
        document: {} as any,
        userId: 'user-1',
        sessionId: 'session-1',
        dryRun: false,
        skipValidation: false,
      };

      const result = validateActionContext(context);
      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate context with only required fields', () => {
      const context = {
        document: {} as any,
      };

      const result = validateActionContext(context);
      expect(result.success).toBe(true);
    });

    it('should reject context with invalid dryRun type', () => {
      const context = {
        document: {} as any,
        dryRun: 'true' as any, // Should be boolean
      };

      const result = validateActionContext(context);
      expect(result.success).toBe(false);
    });
  });

  describe('action manager config validation', () => {
    it('should validate a valid config', () => {
      const config = {
        maxHistorySize: 100,
        enableUndoRedo: true,
        autoValidate: true,
        trackChanges: true,
      };

      const result = validateActionManagerConfig(config);
      expect(result.success).toBe(true);
    });

    it('should validate config with only some fields', () => {
      const config = {
        enableUndoRedo: true,
      };

      const result = validateActionManagerConfig(config);
      expect(result.success).toBe(true);
    });

    it('should reject config with invalid maxHistorySize', () => {
      const config = {
        maxHistorySize: -10, // Must be positive
      };

      const result = validateActionManagerConfig(config);
      expect(result.success).toBe(false);
    });

    it('should reject config with maxHistorySize too large', () => {
      const config = {
        maxHistorySize: 2000, // Max is 1000
      };

      const result = validateActionManagerConfig(config);
      expect(result.success).toBe(false);
    });
  });
});
