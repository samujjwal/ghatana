/**
 * Tests for Canvas Deployment Management System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  DeploymentManager,
  type DeploymentConfig,
  type DeploymentEnvironment,
  type DeploymentSlot,
  type FeatureFlag,
  type RollbackConfig,
  type RehearsalConfig,
  estimateDeploymentTime,
  validateDeploymentConfig,
  formatDeploymentDuration,
  calculateRollbackTime,
} from '../deployment';

describe('DeploymentManager', () => {
  let manager: DeploymentManager;

  beforeEach(() => {
    manager = new DeploymentManager();
  });

  describe('Initialization', () => {
    it('should create manager with default configuration', () => {
      expect(manager).toBeDefined();
      expect(manager.getDeploymentStats()).toEqual({
        total: 0,
        active: 0,
        deployed: 0,
        failed: 0,
        rollback: 0,
        avgDuration: 0,
      });
    });

    it('should accept custom configuration', () => {
      const customManager = new DeploymentManager({
        defaultHealthCheckTimeout: 60000,
        maxConcurrentDeployments: 5,
        historyRetentionDays: 30,
        autoRollbackOnFailure: false,
      });

      expect(customManager).toBeDefined();
    });

    it('should initialize with empty state', () => {
      expect(manager.getActiveSlot('production')).toBeNull();
      expect(manager.getActiveDeployment('production')).toBeNull();
      expect(manager.getTrafficSplit('production')).toBeNull();
      expect(manager.getFeatureFlagsForEnvironment('production')).toEqual([]);
    });
  });

  describe('Deployment Configuration', () => {
    it('should create deployment config', () => {
      const config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '1.2.0',
        artifactUrl: 'https://cdn.example.com/canvas-1.2.0.tar.gz',
        featureFlags: ['collaboration', 'devsecops'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      expect(config.id).toContain('deploy-production-1.2.0');
      expect(config.environment).toBe('production');
      expect(config.slot).toBe('green');
      expect(config.version).toBe('1.2.0');
      expect(config.artifactUrl).toContain('cdn.example.com');
      expect(config.featureFlags).toEqual(['collaboration', 'devsecops']);
      expect(config.healthCheckUrl).toContain('/health');
      expect(config.healthCheckTimeout).toBe(30000);
      expect(config.timestamp).toBeInstanceOf(Date);
    });

    it('should generate unique deployment IDs', () => {
      const config1 = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      const config2 = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      expect(config1.id).not.toBe(config2.id);
    });
  });

  describe('Deployment Execution', () => {
    it('should deploy successfully', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      const record = await manager.deploy(config);

      expect(record.status).toBe('deployed');
      expect(record.config).toEqual(config);
      expect(record.startedAt).toBeInstanceOf(Date);
      expect(record.completedAt).toBeInstanceOf(Date);
      expect(record.duration).toBeGreaterThanOrEqual(0);
      expect(record.healthChecks).toHaveLength(1);
      expect(record.healthChecks[0].healthy).toBe(true);
      expect(record.error).toBeUndefined();
    });

    it('should fail deployment with invalid health check', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/invalid',
      });

      // Disable auto rollback for this test
      const customManager = new DeploymentManager({
        autoRollbackOnFailure: false,
      });

      await expect(customManager.deploy(config)).rejects.toThrow(
        'Health check failed'
      );

      const record = customManager.getDeployment(config.id);
      expect(record?.status).toBe('failed');
      expect(record?.error).toContain('Health check failed');
    });

    it('should respect max concurrent deployments', async () => {
      const limitedManager = new DeploymentManager({
        maxConcurrentDeployments: 1,
        autoRollbackOnFailure: false,
      });

      const config1 = limitedManager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/invalid', // Will fail
      });

      const config2 = limitedManager.createDeploymentConfig({
        environment: 'staging',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://cdn.example.com/v2.tar.gz',
        featureFlags: ['feature2'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      // Start first deployment (will fail but remain in-progress briefly)
      const deploy1 = limitedManager.deploy(config1).catch(() => {
        // Expected to fail
      });

      // Second deployment should fail immediately because first is still in-progress
      await expect(limitedManager.deploy(config2)).rejects.toThrow(
        'Max concurrent deployments'
      );

      // Wait for first to complete
      await deploy1;

      // Now second deployment should succeed
      const record2 = await limitedManager.deploy(config2);
      expect(record2.status).toBe('deployed');
    });

    it('should track deployment duration', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'development',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      const record = await manager.deploy(config);

      expect(record.duration).toBeGreaterThanOrEqual(0);
      expect(record.startedAt).toBeDefined();
      expect(record.completedAt).toBeDefined();
    });

    it('should retrieve deployment by ID', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await manager.deploy(config);

      const retrieved = manager.getDeployment(config.id);
      expect(retrieved).toBeDefined();
      expect(retrieved?.config.id).toBe(config.id);
    });
  });

  describe('Slot Management', () => {
    it('should switch active slot', async () => {
      // Deploy to green slot
      const config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(config);

      // Switch to green
      await manager.switchActiveSlot('production', 'green');

      const activeSlot = manager.getActiveSlot('production');
      expect(activeSlot).toBe('green');

      const activeDeployment = manager.getActiveDeployment('production');
      expect(activeDeployment?.config.id).toBe(config.id);
      expect(activeDeployment?.status).toBe('active');
    });

    it('should reset traffic split when switching slots', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(config);
      await manager.switchActiveSlot('production', 'blue');

      const split = manager.getTrafficSplit('production');
      expect(split).toBeDefined();
      expect(split?.bluePercentage).toBe(100);
      expect(split?.greenPercentage).toBe(0);
    });

    it('should fail to switch to non-existent deployment', async () => {
      await expect(
        manager.switchActiveSlot('production', 'green')
      ).rejects.toThrow('No deployed deployment found');
    });

    it('should deactivate previous slot when switching', async () => {
      // Deploy to blue
      const blueConfig = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(blueConfig);
      await manager.switchActiveSlot('production', 'blue');

      // Deploy to green
      const greenConfig = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://cdn.example.com/v2.tar.gz',
        featureFlags: ['feature2'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(greenConfig);
      await manager.switchActiveSlot('production', 'green');

      const blueDeployment = manager.getDeployment(blueConfig.id);
      expect(blueDeployment?.status).toBe('deployed');
    });
  });

  describe('Rollback', () => {
    it('should rollback to previous deployment', async () => {
      // Deploy v1 to blue
      const v1Config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(v1Config);
      await manager.switchActiveSlot('production', 'blue');

      // Deploy v2 to green
      const v2Config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://cdn.example.com/v2.tar.gz',
        featureFlags: ['feature2'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(v2Config);
      await manager.switchActiveSlot('production', 'green');

      // Rollback
      await manager.rollback('production', 'Critical bug found');

      const activeSlot = manager.getActiveSlot('production');
      expect(activeSlot).toBe('blue');

      const v2Deployment = manager.getDeployment(v2Config.id);
      expect(v2Deployment?.status).toBe('rollback');
      expect(v2Deployment?.error).toBe('Critical bug found');
    });

    it('should fail rollback with no previous deployment', async () => {
      await expect(
        manager.rollback('production', 'Test rollback')
      ).rejects.toThrow('No active deployment');
    });

    it('should auto-rollback on deployment failure', async () => {
      // Deploy v1 to blue first
      const v1Config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      await manager.deploy(v1Config);
      await manager.switchActiveSlot('production', 'blue');

      // Try to deploy v2 with bad health check (will auto-rollback)
      const v2Config = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://cdn.example.com/v2.tar.gz',
        featureFlags: ['feature2'],
        healthCheckUrl: 'https://api.example.com/invalid',
      });

      await expect(manager.deploy(v2Config)).rejects.toThrow();

      // Should still be on blue
      const activeSlot = manager.getActiveSlot('production');
      expect(activeSlot).toBe('blue');
    });
  });

  describe('Traffic Split', () => {
    it('should configure traffic split', () => {
      manager.setTrafficSplit('production', 80, 20);

      const split = manager.getTrafficSplit('production');
      expect(split).toBeDefined();
      expect(split?.bluePercentage).toBe(80);
      expect(split?.greenPercentage).toBe(20);
    });

    it('should validate traffic split percentages', () => {
      expect(() => manager.setTrafficSplit('production', 60, 50)).toThrow(
        'must add up to 100%'
      );

      expect(() => manager.setTrafficSplit('production', -10, 110)).toThrow(
        'must be non-negative'
      );
    });

    it('should update traffic split timestamps', () => {
      manager.setTrafficSplit('production', 50, 50);
      const split1 = manager.getTrafficSplit('production');

      manager.setTrafficSplit('production', 70, 30);
      const split2 = manager.getTrafficSplit('production');

      expect(split2?.updatedAt.getTime()).toBeGreaterThanOrEqual(
        split1!.updatedAt.getTime()
      );
      expect(split2?.startedAt).toEqual(split1?.startedAt);
    });

    it('should support gradual rollout', () => {
      // Start with blue at 100%
      manager.setTrafficSplit('production', 100, 0);

      // Gradually shift to green
      manager.setTrafficSplit('production', 90, 10);
      manager.setTrafficSplit('production', 50, 50);
      manager.setTrafficSplit('production', 10, 90);
      manager.setTrafficSplit('production', 0, 100);

      const finalSplit = manager.getTrafficSplit('production');
      expect(finalSplit?.bluePercentage).toBe(0);
      expect(finalSplit?.greenPercentage).toBe(100);
    });
  });

  describe('Feature Flags', () => {
    it('should create feature flag', () => {
      manager.setFeatureFlag({
        key: 'collaboration',
        name: 'Collaboration Module',
        description: 'Enable real-time collaboration features',
        enabled: true,
        environment: 'production',
      });

      const flag = manager.getFeatureFlag('collaboration');
      expect(flag).toBeDefined();
      expect(flag?.key).toBe('collaboration');
      expect(flag?.enabled).toBe(true);
      expect(flag?.createdAt).toBeInstanceOf(Date);
      expect(flag?.updatedAt).toBeInstanceOf(Date);
    });

    it('should update feature flag', () => {
      manager.setFeatureFlag({
        key: 'test-feature',
        name: 'Test Feature',
        description: 'Test flag',
        enabled: false,
        environment: 'staging',
      });

      const flag1 = manager.getFeatureFlag('test-feature');
      const createdAt = flag1!.createdAt;

      manager.setFeatureFlag({
        key: 'test-feature',
        name: 'Test Feature',
        description: 'Updated description',
        enabled: true,
        environment: 'staging',
      });

      const flag2 = manager.getFeatureFlag('test-feature');
      expect(flag2?.createdAt).toEqual(createdAt);
      expect(flag2?.updatedAt.getTime()).toBeGreaterThanOrEqual(
        flag1!.updatedAt.getTime()
      );
      expect(flag2?.description).toBe('Updated description');
      expect(flag2?.enabled).toBe(true);
    });

    it('should check if feature is enabled', () => {
      manager.setFeatureFlag({
        key: 'devsecops',
        name: 'DevSecOps Module',
        description: 'Enable DevSecOps features',
        enabled: true,
        environment: 'production',
      });

      expect(manager.isFeatureEnabled('devsecops', 'production')).toBe(true);
      expect(manager.isFeatureEnabled('devsecops', 'staging')).toBe(false);
      expect(manager.isFeatureEnabled('nonexistent', 'production')).toBe(false);
    });

    it('should support rollout percentage', () => {
      manager.setFeatureFlag({
        key: 'beta-feature',
        name: 'Beta Feature',
        description: 'Beta test feature',
        enabled: true,
        environment: 'production',
        rolloutPercentage: 50,
      });

      // Test with consistent user ID
      const enabled1 = manager.isFeatureEnabled(
        'beta-feature',
        'production',
        'user-123'
      );
      const enabled2 = manager.isFeatureEnabled(
        'beta-feature',
        'production',
        'user-123'
      );

      // Same user should get consistent result
      expect(enabled1).toBe(enabled2);
    });

    it('should support target segments', () => {
      manager.setFeatureFlag({
        key: 'premium-feature',
        name: 'Premium Feature',
        description: 'Feature for premium users',
        enabled: true,
        environment: 'production',
        targetSegments: ['premium', 'enterprise'],
      });

      const flag = manager.getFeatureFlag('premium-feature');
      expect(flag?.targetSegments).toEqual(['premium', 'enterprise']);
    });

    it('should delete feature flag', () => {
      manager.setFeatureFlag({
        key: 'temp-flag',
        name: 'Temporary Flag',
        description: 'Temp',
        enabled: true,
        environment: 'development',
      });

      expect(manager.getFeatureFlag('temp-flag')).toBeDefined();

      const deleted = manager.deleteFeatureFlag('temp-flag');
      expect(deleted).toBe(true);
      expect(manager.getFeatureFlag('temp-flag')).toBeNull();
    });

    it('should get flags for environment', () => {
      manager.setFeatureFlag({
        key: 'prod-flag-1',
        name: 'Prod Flag 1',
        description: 'Production flag',
        enabled: true,
        environment: 'production',
      });

      manager.setFeatureFlag({
        key: 'prod-flag-2',
        name: 'Prod Flag 2',
        description: 'Production flag',
        enabled: true,
        environment: 'production',
      });

      manager.setFeatureFlag({
        key: 'dev-flag',
        name: 'Dev Flag',
        description: 'Development flag',
        enabled: true,
        environment: 'development',
      });

      const prodFlags = manager.getFeatureFlagsForEnvironment('production');
      expect(prodFlags).toHaveLength(2);
      expect(prodFlags.every((f) => f.environment === 'production')).toBe(true);
    });
  });

  describe('Deployment Queries', () => {
    it('should get deployments for environment', async () => {
      const config1 = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      const config2 = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://example.com/v2.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await manager.deploy(config1);
      await manager.deploy(config2);

      const stagingDeployments =
        manager.getDeploymentsForEnvironment('staging');
      expect(stagingDeployments).toHaveLength(1);
      expect(stagingDeployments[0].config.environment).toBe('staging');
    });

    it('should get deployment statistics', async () => {
      const config1 = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await manager.deploy(config1);
      await manager.switchActiveSlot('production', 'blue');

      const stats = manager.getDeploymentStats('production');
      expect(stats.total).toBe(1);
      expect(stats.active).toBe(1);
      expect(stats.deployed).toBe(0);
      expect(stats.failed).toBe(0);
      expect(stats.avgDuration).toBeGreaterThanOrEqual(0);
    });

    it('should export deployment history', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await manager.deploy(config);

      const history = manager.exportDeploymentHistory('staging');
      expect(history).toBeTruthy();

      const parsed = JSON.parse(history);
      expect(Array.isArray(parsed)).toBe(true);
      expect(parsed.length).toBe(1);
      expect(parsed[0].environment).toBe('staging');
    });
  });

  describe('Deployment Rehearsal', () => {
    it('should create rehearsal config', () => {
      const deployConfig = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      const rehearsal = manager.createRehearsal({
        environment: 'production',
        deploymentConfig: deployConfig,
        dryRun: true,
        validationSteps: [
          'artifact-check',
          'version-check',
          'health-check',
          'feature-flags',
        ],
      });

      expect(rehearsal.id).toContain('rehearsal-production');
      expect(rehearsal.environment).toBe('production');
      expect(rehearsal.dryRun).toBe(true);
      expect(rehearsal.validationSteps).toHaveLength(4);
      expect(rehearsal.createdAt).toBeInstanceOf(Date);
    });

    it('should run rehearsal successfully', async () => {
      const deployConfig = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.2.3',
        artifactUrl: 'https://cdn.example.com/v1.2.3.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      const rehearsal = manager.createRehearsal({
        environment: 'staging',
        deploymentConfig: deployConfig,
        dryRun: true,
        validationSteps: [
          'artifact-check',
          'version-check',
          'health-check',
          'feature-flags',
        ],
      });

      const result = await manager.runRehearsal(rehearsal);

      expect(result.success).toBe(true);
      expect(result.issues).toHaveLength(0);
      expect(result.duration).toBeGreaterThanOrEqual(0);
      expect(result.validationResults['artifact-check']).toBe(true);
      expect(result.validationResults['version-check']).toBe(true);
      expect(result.validationResults['health-check']).toBe(true);
      expect(result.validationResults['feature-flags']).toBe(true);
    });

    it('should detect rehearsal validation failures', async () => {
      const deployConfig = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: 'invalid', // Bad version format
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      const rehearsal = manager.createRehearsal({
        environment: 'staging',
        deploymentConfig: deployConfig,
        dryRun: true,
        validationSteps: ['version-check'],
      });

      const result = await manager.runRehearsal(rehearsal);

      expect(result.success).toBe(false);
      expect(result.issues.length).toBeGreaterThan(0);
      expect(result.validationResults['version-check']).toBe(false);
    });

    it('should store rehearsal results', async () => {
      const deployConfig = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '1.0.0',
        artifactUrl: 'https://cdn.example.com/v1.tar.gz',
        featureFlags: ['feature1'],
        healthCheckUrl: 'https://api.example.com/health',
      });

      const rehearsal = manager.createRehearsal({
        environment: 'production',
        deploymentConfig: deployConfig,
        dryRun: true,
        validationSteps: ['artifact-check'],
      });

      const result = await manager.runRehearsal(rehearsal);

      const retrieved = manager.getRehearsalResult(rehearsal.id);
      expect(retrieved).toEqual(result);
    });

    it('should get all rehearsals', async () => {
      const config1 = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      const config2 = manager.createDeploymentConfig({
        environment: 'production',
        slot: 'green',
        version: '2.0.0',
        artifactUrl: 'https://example.com/v2.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      const rehearsal1 = manager.createRehearsal({
        environment: 'staging',
        deploymentConfig: config1,
        dryRun: true,
        validationSteps: ['artifact-check'],
      });

      const rehearsal2 = manager.createRehearsal({
        environment: 'production',
        deploymentConfig: config2,
        dryRun: true,
        validationSteps: ['version-check'],
      });

      await manager.runRehearsal(rehearsal1);
      await manager.runRehearsal(rehearsal2);

      const allRehearsals = manager.getAllRehearsals();
      expect(allRehearsals).toHaveLength(2);
    });
  });

  describe('Cleanup Operations', () => {
    it('should clean up old deployments', async () => {
      const customManager = new DeploymentManager({
        historyRetentionDays: 0, // Immediate cleanup
      });

      const config = customManager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await customManager.deploy(config);

      // Wait a bit to ensure deployment is in the past
      await new Promise((resolve) => setTimeout(resolve, 10));

      const removed = customManager.cleanupOldDeployments();
      expect(removed).toBe(1);
    });

    it('should not clean up active deployments', async () => {
      const customManager = new DeploymentManager({
        historyRetentionDays: 0,
      });

      const config = customManager.createDeploymentConfig({
        environment: 'production',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await customManager.deploy(config);
      await customManager.switchActiveSlot('production', 'blue');

      const removed = customManager.cleanupOldDeployments();
      expect(removed).toBe(0);
    });

    it('should reset manager state', async () => {
      const config = manager.createDeploymentConfig({
        environment: 'staging',
        slot: 'blue',
        version: '1.0.0',
        artifactUrl: 'https://example.com/v1.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://example.com/health',
      });

      await manager.deploy(config);
      manager.setFeatureFlag({
        key: 'test',
        name: 'Test',
        description: 'Test',
        enabled: true,
        environment: 'staging',
      });

      manager.reset();

      expect(manager.getDeploymentStats().total).toBe(0);
      expect(manager.getFeatureFlagsForEnvironment('staging')).toEqual([]);
      expect(manager.getActiveSlot('staging')).toBeNull();
    });
  });
});

describe('Deployment Helper Functions', () => {
  describe('estimateDeploymentTime', () => {
    it('should estimate deployment time for development', () => {
      const time = estimateDeploymentTime('development', 10 * 1024 * 1024); // 10 MB
      expect(time).toBeGreaterThanOrEqual(60000); // At least 1 minute
    });

    it('should estimate deployment time for staging', () => {
      const time = estimateDeploymentTime('staging', 50 * 1024 * 1024); // 50 MB
      expect(time).toBeGreaterThanOrEqual(120000); // At least 2 minutes
    });

    it('should estimate deployment time for production', () => {
      const time = estimateDeploymentTime('production', 100 * 1024 * 1024); // 100 MB
      expect(time).toBeGreaterThanOrEqual(300000); // At least 5 minutes
    });

    it('should include artifact size in estimate', () => {
      const smallTime = estimateDeploymentTime('development', 1024 * 1024); // 1 MB
      const largeTime = estimateDeploymentTime(
        'development',
        100 * 1024 * 1024
      ); // 100 MB

      expect(largeTime).toBeGreaterThan(smallTime);
    });
  });

  describe('validateDeploymentConfig', () => {
    it('should validate correct config', () => {
      const config: DeploymentConfig = {
        id: 'test-deploy-123',
        environment: 'production',
        slot: 'green',
        version: '1.2.3',
        artifactUrl: 'https://cdn.example.com/app-1.2.3.tar.gz',
        featureFlags: ['collaboration'],
        healthCheckUrl: 'https://api.example.com/health',
        healthCheckTimeout: 30000,
        timestamp: new Date(),
      };

      const result = validateDeploymentConfig(config);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect invalid artifact URL', () => {
      const config: DeploymentConfig = {
        id: 'test-deploy-123',
        environment: 'production',
        slot: 'green',
        version: '1.2.3',
        artifactUrl: 'invalid-url',
        featureFlags: ['collaboration'],
        healthCheckUrl: 'https://api.example.com/health',
        healthCheckTimeout: 30000,
        timestamp: new Date(),
      };

      const result = validateDeploymentConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Artifact URL'))).toBe(true);
    });

    it('should detect invalid version', () => {
      const config: DeploymentConfig = {
        id: 'test-deploy-123',
        environment: 'production',
        slot: 'green',
        version: 'v1.2', // Invalid semver
        artifactUrl: 'https://cdn.example.com/app.tar.gz',
        featureFlags: ['collaboration'],
        healthCheckUrl: 'https://api.example.com/health',
        healthCheckTimeout: 30000,
        timestamp: new Date(),
      };

      const result = validateDeploymentConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Version'))).toBe(true);
    });

    it('should detect missing feature flags', () => {
      const config: DeploymentConfig = {
        id: 'test-deploy-123',
        environment: 'production',
        slot: 'green',
        version: '1.2.3',
        artifactUrl: 'https://cdn.example.com/app.tar.gz',
        featureFlags: [],
        healthCheckUrl: 'https://api.example.com/health',
        healthCheckTimeout: 30000,
        timestamp: new Date(),
      };

      const result = validateDeploymentConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('feature flag'))).toBe(true);
    });

    it('should detect short health check timeout', () => {
      const config: DeploymentConfig = {
        id: 'test-deploy-123',
        environment: 'production',
        slot: 'green',
        version: '1.2.3',
        artifactUrl: 'https://cdn.example.com/app.tar.gz',
        featureFlags: ['test'],
        healthCheckUrl: 'https://api.example.com/health',
        healthCheckTimeout: 500, // Too short
        timestamp: new Date(),
      };

      const result = validateDeploymentConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('timeout'))).toBe(true);
    });
  });

  describe('formatDeploymentDuration', () => {
    it('should format seconds', () => {
      expect(formatDeploymentDuration(5000)).toBe('5s');
      expect(formatDeploymentDuration(45000)).toBe('45s');
    });

    it('should format minutes and seconds', () => {
      expect(formatDeploymentDuration(60000)).toBe('1m 0s');
      expect(formatDeploymentDuration(125000)).toBe('2m 5s');
    });

    it('should format hours and minutes', () => {
      expect(formatDeploymentDuration(3600000)).toBe('1h 0m');
      expect(formatDeploymentDuration(5400000)).toBe('1h 30m');
    });

    it('should handle zero duration', () => {
      expect(formatDeploymentDuration(0)).toBe('0s');
    });
  });

  describe('calculateRollbackTime', () => {
    it('should calculate rollback time for development', () => {
      const time = calculateRollbackTime('development');
      expect(time).toBe(30000); // 30 seconds
    });

    it('should calculate rollback time for staging', () => {
      const time = calculateRollbackTime('staging');
      expect(time).toBe(60000); // 1 minute
    });

    it('should calculate rollback time for production', () => {
      const time = calculateRollbackTime('production');
      expect(time).toBe(120000); // 2 minutes (under 5 minute target)
    });
  });
});
