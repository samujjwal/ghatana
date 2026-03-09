/**
 * Tests for RestoreRunbookManager
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  createRestoreRunbook,
  type SmokeTest,

  RestoreRunbookManager} from '../restoreRunbook';

describe('RestoreRunbookManager', () => {
  let manager: RestoreRunbookManager;

  beforeEach(() => {
    manager = createRestoreRunbook();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();
      expect(config.enableStagingValidation).toBe(true);
      expect(config.enableDryRun).toBe(true);
      expect(config.enableSmokeTests).toBe(true);
      expect(config.smokeTestTimeout).toBe(30000);
      expect(config.allowProductionWithoutStaging).toBe(false);
      expect(config.requireAllSmokeTests).toBe(false);
    });

    it('should initialize with custom configuration', () => {
      const custom = createRestoreRunbook({
        smokeTestTimeout: 60000,
        requireAllSmokeTests: true,
      });

      const config = custom.getConfig();
      expect(config.smokeTestTimeout).toBe(60000);
      expect(config.requireAllSmokeTests).toBe(true);
    });

    it('should start with no operations', () => {
      expect(manager.getOperations()).toEqual([]);
    });

    it('should start with no smoke tests', () => {
      expect(manager.getSmokeTests()).toEqual([]);
    });
  });

  describe('Smoke Test Management', () => {
    it('should register smoke test', () => {
      const test: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Test description',
        test: async () => true,
      };

      manager.registerSmokeTest(test);

      const tests = manager.getSmokeTests();
      expect(tests).toHaveLength(1);
      expect(tests[0]).toEqual(test);
    });

    it('should unregister smoke test', () => {
      const test: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Test description',
        test: async () => true,
      };

      manager.registerSmokeTest(test);
      const result = manager.unregisterSmokeTest('test-1');

      expect(result).toBe(true);
      expect(manager.getSmokeTests()).toEqual([]);
    });

    it('should return false when unregistering non-existent test', () => {
      const result = manager.unregisterSmokeTest('non-existent');
      expect(result).toBe(false);
    });

    it('should clear all smoke tests', () => {
      const test1: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Test 1',
        test: async () => true,
      };

      const test2: SmokeTest = {
        id: 'test-2',
        name: 'Test 2',
        description: 'Test 2',
        test: async () => true,
      };

      manager.registerSmokeTest(test1);
      manager.registerSmokeTest(test2);
      manager.clearSmokeTests();

      expect(manager.getSmokeTests()).toEqual([]);
    });
  });

  describe('Restore Operations', () => {
    it('should start staging restore operation', async () => {
      const operation = await manager.startRestore(
        'snapshot-1',
        'staging',
        'user-1'
      );

      expect(operation.id).toBeDefined();
      expect(operation.snapshotId).toBe('snapshot-1');
      expect(operation.environment).toBe('staging');
      expect(operation.stage).toBe('validation');
      expect(operation.startedBy).toBe('user-1');
      expect(operation.startedAt).toBeDefined();
    });

    it('should start restore with metadata', async () => {
      const metadata = { reason: 'disaster recovery', approver: 'admin-1' };
      const operation = await manager.startRestore(
        'snapshot-1',
        'staging',
        'user-1',
        metadata
      );

      expect(operation.metadata).toEqual(metadata);
    });

    it('should reject production restore without staging validation', async () => {
      await expect(
        manager.startRestore('snapshot-1', 'production', 'user-1')
      ).rejects.toThrow('Production restore requires successful staging validation');
    });

    it('should allow production restore with staging validation', async () => {
      // First run staging validation
      const stagingOp = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      
      // Register passing test
      const test: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Test 1',
        test: async () => true,
      };
      manager.registerSmokeTest(test);

      // Run smoke tests
      await manager.runSmokeTests(stagingOp.id);

      // Now production restore should work
      const prodOp = await manager.startRestore('snapshot-1', 'production', 'user-1');
      expect(prodOp.environment).toBe('production');
    });

    it('should allow production restore when staging validation is disabled', async () => {
      manager.updateConfig({ allowProductionWithoutStaging: true });

      const operation = await manager.startRestore(
        'snapshot-1',
        'production',
        'user-1'
      );

      expect(operation.environment).toBe('production');
    });

    it('should get operation by ID', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const retrieved = manager.getOperation(operation.id);

      expect(retrieved).toEqual(operation);
    });

    it('should return undefined for non-existent operation', () => {
      const retrieved = manager.getOperation('non-existent');
      expect(retrieved).toBeUndefined();
    });
  });

  describe('Dry-Run Validation', () => {
    it('should perform successful dry-run', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const snapshotData = { elements: [{ id: '1' }], metadata: {} };

      const result = await manager.performDryRun(operation.id, snapshotData);

      expect(result.success).toBe(true);
      expect(result.issues).toEqual([]);
      expect(result.estimatedDuration).toBeDefined();
      expect(result.resources).toBeDefined();
      expect(result.affectedEntities).toEqual(['elements', 'metadata']);
    });

    it('should detect empty snapshot data', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const result = await manager.performDryRun(operation.id, {});

      expect(result.success).toBe(false);
      expect(result.issues).toContain('Snapshot data is empty or invalid');
    });

    it('should update operation stage during dry-run', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      await manager.performDryRun(operation.id, { data: 'test' });

      const updated = manager.getOperation(operation.id);
      expect(updated?.stage).toBe('dry-run');
    });

    it('should skip dry-run when disabled', async () => {
      manager.updateConfig({ enableDryRun: false });
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const result = await manager.performDryRun(operation.id, {});

      expect(result.success).toBe(true);
      expect(result.issues).toEqual([]);
    });

    it('should throw error for non-existent operation', async () => {
      await expect(
        manager.performDryRun('non-existent', {})
      ).rejects.toThrow('Operation not found');
    });
  });

  describe('Smoke Tests Execution', () => {
    it('should run passing smoke tests', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Connection Test',
        description: 'Test connection',
        test: async () => true,
      };

      manager.registerSmokeTest(test);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(true);
      expect(result.totalTests).toBe(1);
      expect(result.passedTests).toBe(1);
      expect(result.failedTests).toBe(0);
      expect(result.tests[0].status).toBe('passed');
    });

    it('should run failing smoke tests', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Failing Test',
        description: 'Test that fails',
        test: async () => false,
      };

      manager.registerSmokeTest(test);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(true); // Non-critical failure
      expect(result.totalTests).toBe(1);
      expect(result.passedTests).toBe(0);
      expect(result.failedTests).toBe(1);
      expect(result.tests[0].status).toBe('failed');
    });

    it('should detect critical test failures', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Critical Test',
        description: 'Critical test',
        test: async () => false,
        critical: true,
      };

      manager.registerSmokeTest(test);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(false);
      expect(result.criticalFailures).toBe(1);
    });

    it('should handle test exceptions', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Error Test',
        description: 'Test that throws',
        test: async () => {
          throw new Error('Test error');
        },
      };

      manager.registerSmokeTest(test);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(true); // Non-critical
      expect(result.failedTests).toBe(1);
      expect(result.tests[0].error).toBe('Test error');
    });

    it('should enforce test timeout', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Slow Test',
        description: 'Test that times out',
        test: async () => {
          await new Promise((resolve) => setTimeout(resolve, 100000));
          return true;
        },
        timeout: 10,
      };

      manager.registerSmokeTest(test);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.failedTests).toBe(1);
      expect(result.tests[0].error).toBe('Test timeout');
    });

    it('should require all tests to pass when configured', async () => {
      manager.updateConfig({ requireAllSmokeTests: true });
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test1: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Passing test',
        test: async () => true,
      };

      const test2: SmokeTest = {
        id: 'test-2',
        name: 'Test 2',
        description: 'Failing test',
        test: async () => false,
      };

      manager.registerSmokeTest(test1);
      manager.registerSmokeTest(test2);
      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(false);
    });

    it('should skip smoke tests when disabled', async () => {
      manager.updateConfig({ enableSmokeTests: false });
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const result = await manager.runSmokeTests(operation.id);

      expect(result.success).toBe(true);
      expect(result.totalTests).toBe(0);
    });

    it('should update operation stage during smoke tests', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      await manager.runSmokeTests(operation.id);

      const updated = manager.getOperation(operation.id);
      expect(updated?.stage).toBe('smoke-test');
    });

    it('should store staging validation results', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');

      const test: SmokeTest = {
        id: 'test-1',
        name: 'Test 1',
        description: 'Test',
        test: async () => true,
      };

      manager.registerSmokeTest(test);
      await manager.runSmokeTests(operation.id);

      const validation = manager.getStagingValidation('snapshot-1');
      expect(validation).toBeDefined();
      expect(validation?.success).toBe(true);
    });
  });

  describe('Operation Management', () => {
    it('should complete restore operation', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const result = manager.completeRestore(operation.id);

      expect(result).toBe(true);

      const updated = manager.getOperation(operation.id);
      expect(updated?.stage).toBe('complete');
      expect(updated?.completedAt).toBeDefined();
    });

    it('should return false when completing non-existent operation', () => {
      const result = manager.completeRestore('non-existent');
      expect(result).toBe(false);
    });

    it('should update operation stage', async () => {
      const operation = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const result = manager.updateStage(operation.id, 'restore');

      expect(result).toBe(true);

      const updated = manager.getOperation(operation.id);
      expect(updated?.stage).toBe('restore');
    });

    it('should get operations by environment', async () => {
      manager.updateConfig({ allowProductionWithoutStaging: true });

      await manager.startRestore('snapshot-1', 'staging', 'user-1');
      await manager.startRestore('snapshot-2', 'production', 'user-1');
      await manager.startRestore('snapshot-3', 'staging', 'user-1');

      const staging = manager.getOperationsByEnvironment('staging');
      const production = manager.getOperationsByEnvironment('production');

      expect(staging).toHaveLength(2);
      expect(production).toHaveLength(1);
    });

    it('should get operations by stage', async () => {
      const op1 = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const op2 = await manager.startRestore('snapshot-2', 'staging', 'user-1');
      
      manager.completeRestore(op1.id);

      const validation = manager.getOperationsByStage('validation');
      const complete = manager.getOperationsByStage('complete');

      expect(validation).toHaveLength(1);
      expect(complete).toHaveLength(1);
    });

    it('should clear completed operations', async () => {
      const op1 = await manager.startRestore('snapshot-1', 'staging', 'user-1');
      const op2 = await manager.startRestore('snapshot-2', 'staging', 'user-1');

      manager.completeRestore(op1.id);

      const cleared = manager.clearCompleted();
      expect(cleared).toBe(1);
      expect(manager.getOperations()).toHaveLength(1);
    });

    it('should clear all operations', async () => {
      await manager.startRestore('snapshot-1', 'staging', 'user-1');
      await manager.startRestore('snapshot-2', 'staging', 'user-1');

      manager.clearOperations();
      expect(manager.getOperations()).toEqual([]);
    });
  });

  describe('Configuration', () => {
    it('should get configuration', () => {
      const config = manager.getConfig();
      expect(config).toBeDefined();
      expect(config.enableStagingValidation).toBeDefined();
    });

    it('should update configuration', () => {
      manager.updateConfig({
        smokeTestTimeout: 60000,
        requireAllSmokeTests: true,
      });

      const config = manager.getConfig();
      expect(config.smokeTestTimeout).toBe(60000);
      expect(config.requireAllSmokeTests).toBe(true);
    });

    it('should merge configuration updates', () => {
      const originalTimeout = manager.getConfig().smokeTestTimeout;

      manager.updateConfig({
        requireAllSmokeTests: true,
      });

      const config = manager.getConfig();
      expect(config.smokeTestTimeout).toBe(originalTimeout);
      expect(config.requireAllSmokeTests).toBe(true);
    });
  });
});
