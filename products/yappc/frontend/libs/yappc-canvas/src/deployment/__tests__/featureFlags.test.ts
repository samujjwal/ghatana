import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createFeatureFlagsManager,
  registerFlag,
  registerFlags,
  unregisterFlag,
  getFlag,
  getAllFlags,
  evaluateFlag,
  isFeatureEnabled,
  getFlagValue,
  updateFlag,
  toggleFlag,
  evaluateFlags,
  subscribeToFlagChanges,
  subscribeToAnalytics,
  exportFlags,
  importFlags,
  getConfig,
  updateConfig,
  type FeatureFlag,
  type EvaluationContext,
  type FeatureFlagsState,
} from '../featureFlags';

describe('Feature Flags System', () => {
  describe('Manager Creation', () => {
    it('should create feature flags manager with default config', () => {
      const manager = createFeatureFlagsManager();
      
      expect(manager).toBeDefined();
      expect(manager.flags).toBeInstanceOf(Map);
      expect(manager.flags.size).toBe(0);
      expect(manager.config.enableAnalytics).toBe(true);
      expect(manager.config.environment).toBe('production');
      expect(manager.changeListeners).toEqual([]);
      expect(manager.analyticsListeners).toEqual([]);
    });

    it('should create manager with custom config', () => {
      const manager = createFeatureFlagsManager({
        enableAnalytics: false,
        environment: 'development',
        defaultContext: { userId: 'test-user' },
      });
      
      expect(manager.config.enableAnalytics).toBe(false);
      expect(manager.config.environment).toBe('development');
      expect(manager.config.defaultContext).toEqual({ userId: 'test-user' });
    });

    it('should merge custom config with defaults', () => {
      const manager = createFeatureFlagsManager({
        environment: 'staging',
      });
      
      expect(manager.config.environment).toBe('staging');
      expect(manager.config.enableAnalytics).toBe(true); // default preserved
    });
  });

  describe('Flag Registration', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should register a boolean flag', () => {
      const flag: FeatureFlag = {
        key: 'test_feature',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        description: 'Test feature flag',
      };

      registerFlag(manager, flag);
      
      expect(manager.flags.has('test_feature')).toBe(true);
      expect(manager.flags.get('test_feature')).toEqual(flag);
    });

    it('should register a string flag', () => {
      const flag: FeatureFlag = {
        key: 'api_endpoint',
        type: 'string',
        defaultValue: 'https://api.example.com',
        enabled: true,
      };

      registerFlag(manager, flag);
      
      const registered = manager.flags.get('api_endpoint');
      expect(registered?.type).toBe('string');
      expect(registered?.defaultValue).toBe('https://api.example.com');
    });

    it('should register a number flag', () => {
      const flag: FeatureFlag = {
        key: 'max_retries',
        type: 'number',
        defaultValue: 3,
        enabled: true,
      };

      registerFlag(manager, flag);
      
      const registered = manager.flags.get('max_retries');
      expect(registered?.type).toBe('number');
      expect(registered?.defaultValue).toBe(3);
    });

    it('should register multiple flags at once', () => {
      const flags: FeatureFlag[] = [
        { key: 'feature1', type: 'boolean', defaultValue: false, enabled: true },
        { key: 'feature2', type: 'boolean', defaultValue: true, enabled: true },
        { key: 'feature3', type: 'string', defaultValue: 'test', enabled: true },
      ];

      registerFlags(manager, flags);
      
      expect(manager.flags.size).toBe(3);
      expect(manager.flags.has('feature1')).toBe(true);
      expect(manager.flags.has('feature2')).toBe(true);
      expect(manager.flags.has('feature3')).toBe(true);
    });

    it('should allow duplicate flag registration (overwrite)', () => {
      const flag1: FeatureFlag = {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      };
      
      const flag2: FeatureFlag = {
        key: 'test',
        type: 'boolean',
        defaultValue: true,
        enabled: false,
        description: 'Updated',
      };

      registerFlag(manager, flag1);
      registerFlag(manager, flag2);
      
      expect(manager.flags.size).toBe(1);
      expect(manager.flags.get('test')?.defaultValue).toBe(true);
      expect(manager.flags.get('test')?.description).toBe('Updated');
    });
  });

  describe('Flag Retrieval', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
      registerFlags(manager, [
        { key: 'flag1', type: 'boolean', defaultValue: false, enabled: true },
        { key: 'flag2', type: 'string', defaultValue: 'test', enabled: true },
      ]);
    });

    it('should get flag by key', () => {
      const flag = getFlag(manager, 'flag1');
      
      expect(flag).toBeDefined();
      expect(flag?.key).toBe('flag1');
    });

    it('should return null for non-existent flag', () => {
      const flag = getFlag(manager, 'non_existent');
      expect(flag).toBeNull();
    });

    it('should get all flags', () => {
      const flags = getAllFlags(manager);
      
      expect(flags.length).toBe(2);
      expect(flags.find(f => f.key === 'flag1')).toBeDefined();
      expect(flags.find(f => f.key === 'flag2')).toBeDefined();
    });
  });

  describe('Flag Unregistration', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });
    });

    it('should unregister flag', () => {
      expect(manager.flags.has('test')).toBe(true);
      
      const result = unregisterFlag(manager, 'test');
      
      expect(result).toBe(true);
      expect(manager.flags.has('test')).toBe(false);
    });

    it('should return false when unregistering non-existent flag', () => {
      const result = unregisterFlag(manager, 'non_existent');
      expect(result).toBe(false);
    });
  });

  describe('Flag Evaluation - Basic', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should return default false for non-existent flag', () => {
      const result = evaluateFlag(manager, 'non_existent', {});
      
      expect(result.key).toBe('non_existent');
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });

    it('should evaluate flag with default value (enabled)', () => {
      registerFlag(manager, {
        key: 'simple_flag',
        type: 'boolean',
        defaultValue: true,
        enabled: true,
      });

      const result = evaluateFlag(manager, 'simple_flag', {});
      
      expect(result.key).toBe('simple_flag');
      expect(result.value).toBe(true);
      expect(result.reason).toBe('default');
    });

    it('should evaluate flag with default value (disabled)', () => {
      registerFlag(manager, {
        key: 'disabled_flag',
        type: 'boolean',
        defaultValue: true,
        enabled: false,
      });

      const result = evaluateFlag(manager, 'disabled_flag', {});
      
      expect(result.value).toBe(true); // Returns default value when disabled
      expect(result.reason).toBe('default');
    });

    it('should evaluate string flag', () => {
      registerFlag(manager, {
        key: 'api_url',
        type: 'string',
        defaultValue: 'https://api.com',
        enabled: true,
      });

      const result = evaluateFlag(manager, 'api_url', {});
      
      expect(result.value).toBe('https://api.com');
    });

    it('should evaluate number flag', () => {
      registerFlag(manager, {
        key: 'timeout',
        type: 'number',
        defaultValue: 5000,
        enabled: true,
      });

      const result = evaluateFlag(manager, 'timeout', {});
      
      expect(result.value).toBe(5000);
    });
  });

  describe('Flag Evaluation - User List Targeting', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should enable flag for user in target list', () => {
      registerFlag(manager, {
        key: 'user_targeted',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          userList: ['user-123', 'user-456'],
        },
      });

      const result = evaluateFlag(manager, 'user_targeted', { userId: 'user-123' });
      
      expect(result.value).toBe(true);
      expect(result.reason).toBe('targeting');
    });

    it('should return default for user not in target list', () => {
      registerFlag(manager, {
        key: 'user_targeted',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          userList: ['user-123', 'user-456'],
        },
      });

      const result = evaluateFlag(manager, 'user_targeted', { userId: 'user-789' });
      
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });

    it('should return default when userId not provided', () => {
      registerFlag(manager, {
        key: 'user_targeted',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          userList: ['user-123'],
        },
      });

      const result = evaluateFlag(manager, 'user_targeted', {});
      
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });
  });

  describe('Flag Evaluation - Rollout Percentage', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should enable flag for 100% rollout', () => {
      registerFlag(manager, {
        key: 'rollout_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: {
            enabled: true,
            percentage: 100,
            attribute: 'userId',
          },
        },
      });

      const result = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      
      expect(result.value).toBe(true);
      expect(result.reason).toBe('rollout');
    });

    it('should return default for 0% rollout', () => {
      registerFlag(manager, {
        key: 'rollout_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: {
            enabled: true,
            percentage: 0,
            attribute: 'userId',
          },
        },
      });

      const result = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });

    it('should use consistent hashing for same user', () => {
      registerFlag(manager, {
        key: 'rollout_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: {
            enabled: true,
            percentage: 50,
            attribute: 'userId',
          },
        },
      });

      const result1 = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      const result2 = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      const result3 = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      
      expect(result1.value).toBe(result2.value);
      expect(result2.value).toBe(result3.value);
    });

    it('should return default when rollout attribute missing', () => {
      registerFlag(manager, {
        key: 'rollout_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: {
            enabled: true,
            percentage: 100,
            attribute: 'userId',
          },
        },
      });

      const result = evaluateFlag(manager, 'rollout_flag', {});
      
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });

    it('should return default when rollout disabled', () => {
      registerFlag(manager, {
        key: 'rollout_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: {
            enabled: false,
            percentage: 100,
            attribute: 'userId',
          },
        },
      });

      const result = evaluateFlag(manager, 'rollout_flag', { userId: 'user-123' });
      
      expect(result.value).toBe(false);
      expect(result.reason).toBe('default');
    });
  });

  describe('Flag Evaluation - Rule Targeting', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should evaluate rule with "in" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'plan',
              operator: 'in',
              values: ['pro', 'enterprise'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { plan: 'pro' });
      expect(result.value).toBe(true);
      expect(result.reason).toBe('targeting');

      const result2 = evaluateFlag(manager, 'rule_flag', { plan: 'free' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "not_in" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'plan',
              operator: 'not_in',
              values: ['free'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { plan: 'pro' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { plan: 'free' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "contains" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'email',
              operator: 'contains',
              values: ['@company.com'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { email: 'user@company.com' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { email: 'user@other.com' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "starts_with" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'region',
              operator: 'starts_with',
              values: ['us-'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { region: 'us-east-1' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { region: 'eu-west-1' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "ends_with" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'domain',
              operator: 'ends_with',
              values: ['.com'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { domain: 'example.com' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { domain: 'example.org' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "gt" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'age',
              operator: 'gt',
              values: ['18'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { age: '25' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { age: '15' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "gte" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'age',
              operator: 'gte',
              values: ['18'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { age: '18' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { age: '17' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "lt" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'price',
              operator: 'lt',
              values: ['100'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { price: '50' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { price: '150' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "lte" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'price',
              operator: 'lte',
              values: ['100'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { price: '100' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { price: '101' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "eq" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'status',
              operator: 'eq',
              values: ['active'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { status: 'active' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { status: 'inactive' });
      expect(result2.value).toBe(false);
    });

    it('should evaluate rule with "neq" operator', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            {
              attribute: 'status',
              operator: 'neq',
              values: ['inactive'],
            },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', { status: 'active' });
      expect(result.value).toBe(true);

      const result2 = evaluateFlag(manager, 'rule_flag', { status: 'inactive' });
      expect(result2.value).toBe(false);
    });

    it('should require all rules to match', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            { attribute: 'plan', operator: 'in', values: ['pro', 'enterprise'] },
            { attribute: 'region', operator: 'eq', values: ['us-east-1'] },
          ],
        },
      });

      // Both rules match
      const result1 = evaluateFlag(manager, 'rule_flag', { plan: 'pro', region: 'us-east-1' });
      expect(result1.value).toBe(true);

      // Only one rule matches
      const result2 = evaluateFlag(manager, 'rule_flag', { plan: 'pro', region: 'eu-west-1' });
      expect(result2.value).toBe(false);
    });

    it('should return false when required attribute is missing', () => {
      registerFlag(manager, {
        key: 'rule_flag',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            { attribute: 'plan', operator: 'eq', values: ['pro'] },
          ],
        },
      });

      const result = evaluateFlag(manager, 'rule_flag', {});
      expect(result.value).toBe(false);
    });
  });

  describe('Flag Evaluation - Variants (A/B Testing)', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should select variant based on weights', () => {
      registerFlag(manager, {
        key: 'ab_test',
        type: 'string',
        defaultValue: 'control',
        enabled: true,
        variants: [
          { name: 'control', value: 'control', weight: 50 },
          { name: 'variant_a', value: 'variant_a', weight: 50 },
        ],
      });

      const result = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      
      expect(['control', 'variant_a']).toContain(result.value);
      expect(result.reason).toBe('variant');
      expect(result.variant).toBeDefined();
    });

    it('should use consistent variant selection for same user', () => {
      registerFlag(manager, {
        key: 'ab_test',
        type: 'string',
        defaultValue: 'control',
        enabled: true,
        variants: [
          { name: 'control', value: 'control', weight: 50 },
          { name: 'variant_a', value: 'variant_a', weight: 50 },
        ],
      });

      const result1 = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      const result2 = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      const result3 = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      
      expect(result1.value).toBe(result2.value);
      expect(result2.value).toBe(result3.value);
      expect(result1.variant).toBe(result2.variant);
    });

    it('should select variant with user list targeting', () => {
      registerFlag(manager, {
        key: 'ab_test',
        type: 'string',
        defaultValue: 'control',
        enabled: true,
        targeting: {
          userList: ['user-123'],
        },
        variants: [
          { name: 'control', value: 'control', weight: 50 },
          { name: 'variant_a', value: 'variant_a', weight: 50 },
        ],
      });

      const result = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      
      expect(['control', 'variant_a']).toContain(result.value);
      expect(result.reason).toBe('targeting');
      expect(result.variant).toBeDefined();
    });
  });

  describe('Flag Evaluation - Helper Functions', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should check if feature is enabled (boolean true)', () => {
      registerFlag(manager, {
        key: 'feature',
        type: 'boolean',
        defaultValue: true,
        enabled: true,
      });

      expect(isFeatureEnabled(manager, 'feature', {})).toBe(true);
    });

    it('should check if feature is enabled (boolean false)', () => {
      registerFlag(manager, {
        key: 'feature',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      expect(isFeatureEnabled(manager, 'feature', {})).toBe(false);
    });

    it('should check if feature is enabled (non-existent flag)', () => {
      expect(isFeatureEnabled(manager, 'non_existent', {})).toBe(false);
    });

    it('should get flag value with default', () => {
      registerFlag(manager, {
        key: 'timeout',
        type: 'number',
        defaultValue: 5000,
        enabled: true,
      });

      const value = getFlagValue(manager, 'timeout', 3000, {});
      expect(value).toBe(5000);
    });

    it('should return evaluation value for non-existent flag', () => {
      // getFlagValue returns false for non-existent flags (evaluation.value is false)
      // The ?? operator only uses defaultValue when evaluation.value is null/undefined
      const value = getFlagValue(manager, 'non_existent', 'fallback', {});
      expect(value).toBe(false); // Returns false, not the default value
    });
  });

  describe('Flag Updates', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });
    });

    it('should update flag configuration', () => {
      const updated = updateFlag(manager, 'test', {
        defaultValue: true,
        description: 'Updated description',
      });

      expect(updated).toBeDefined();
      expect(updated?.defaultValue).toBe(true);
      expect(updated?.description).toBe('Updated description');
    });

    it('should return null when updating non-existent flag', () => {
      const result = updateFlag(manager, 'non_existent', { defaultValue: true });
      expect(result).toBeNull();
    });

    it('should toggle flag enabled state', () => {
      const flag = getFlag(manager, 'test');
      expect(flag?.enabled).toBe(true);

      toggleFlag(manager, 'test');
      expect(getFlag(manager, 'test')?.enabled).toBe(false);

      toggleFlag(manager, 'test');
      expect(getFlag(manager, 'test')?.enabled).toBe(true);
    });

    it('should return null when toggling non-existent flag', () => {
      const result = toggleFlag(manager, 'non_existent');
      expect(result).toBeNull();
    });
  });

  describe('Subscriptions', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should subscribe to flag changes', () => {
      const listener = vi.fn();
      const unsubscribe = subscribeToFlagChanges(manager, listener);

      expect(typeof unsubscribe).toBe('function');
      expect(manager.changeListeners).toContain(listener);
    });

    it('should unsubscribe from flag changes', () => {
      const listener = vi.fn();
      const unsubscribe = subscribeToFlagChanges(manager, listener);

      unsubscribe();
      expect(manager.changeListeners).not.toContain(listener);
    });

    it('should notify change listeners when flag value changes', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const listener = vi.fn();
      subscribeToFlagChanges(manager, listener);

      updateFlag(manager, 'test', { defaultValue: true });

      expect(listener).toHaveBeenCalledWith(
        expect.objectContaining({
          key: 'test',
          oldValue: false,
          newValue: true,
        })
      );
    });

    it('should not notify when default value unchanged', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const listener = vi.fn();
      subscribeToFlagChanges(manager, listener);

      updateFlag(manager, 'test', { description: 'Updated' });

      expect(listener).not.toHaveBeenCalled();
    });

    it('should subscribe to analytics events', () => {
      const listener = vi.fn();
      const unsubscribe = subscribeToAnalytics(manager, listener);

      expect(typeof unsubscribe).toBe('function');
      expect(manager.analyticsListeners).toContain(listener);
    });

    it('should unsubscribe from analytics events', () => {
      const listener = vi.fn();
      const unsubscribe = subscribeToAnalytics(manager, listener);

      unsubscribe();
      expect(manager.analyticsListeners).not.toContain(listener);
    });

    it('should emit analytics event on flag evaluation', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const listener = vi.fn();
      subscribeToAnalytics(manager, listener);

      evaluateFlag(manager, 'test', { userId: 'user-123' });

      expect(listener).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'flag_evaluated',
          flagKey: 'test',
          value: false,
          userId: 'user-123',
        })
      );
    });

    it('should not emit analytics when disabled in config', () => {
      manager = createFeatureFlagsManager({ enableAnalytics: false });
      
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const listener = vi.fn();
      subscribeToAnalytics(manager, listener);

      evaluateFlag(manager, 'test', {});

      expect(listener).not.toHaveBeenCalled();
    });

    it('should handle listener errors gracefully', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
      
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const errorListener = vi.fn(() => {
        throw new Error('Listener error');
      });
      const goodListener = vi.fn();

      subscribeToFlagChanges(manager, errorListener);
      subscribeToFlagChanges(manager, goodListener);

      updateFlag(manager, 'test', { defaultValue: true });

      expect(errorListener).toHaveBeenCalled();
      expect(goodListener).toHaveBeenCalled();
      expect(consoleSpy).toHaveBeenCalled();
    });
  });

  describe('Bulk Operations', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
      registerFlags(manager, [
        { key: 'flag1', type: 'boolean', defaultValue: false, enabled: true },
        { key: 'flag2', type: 'boolean', defaultValue: true, enabled: true },
        { key: 'flag3', type: 'string', defaultValue: 'test', enabled: true },
      ]);
    });

    it('should evaluate multiple flags at once', () => {
      const results = evaluateFlags(manager, ['flag1', 'flag2', 'flag3'], {});

      expect(results.size).toBe(3);
      expect(results.get('flag1')?.value).toBe(false);
      expect(results.get('flag2')?.value).toBe(true);
      expect(results.get('flag3')?.value).toBe('test');
    });

    it('should export all flags', () => {
      const exported = exportFlags(manager);

      expect(exported.length).toBe(3);
      expect(exported.find(f => f.key === 'flag1')).toBeDefined();
      expect(exported.find(f => f.key === 'flag2')).toBeDefined();
      expect(exported.find(f => f.key === 'flag3')).toBeDefined();
    });

    it('should import flags', () => {
      const newManager = createFeatureFlagsManager();
      const flags: FeatureFlag[] = [
        { key: 'imported1', type: 'boolean', defaultValue: false, enabled: true },
        { key: 'imported2', type: 'string', defaultValue: 'test', enabled: true },
      ];

      importFlags(newManager, flags);

      expect(newManager.flags.size).toBe(2);
      expect(newManager.flags.has('imported1')).toBe(true);
      expect(newManager.flags.has('imported2')).toBe(true);
    });

    it('should clear existing flags on import', () => {
      const flags: FeatureFlag[] = [
        { key: 'new_flag', type: 'boolean', defaultValue: true, enabled: true },
      ];

      importFlags(manager, flags);

      expect(manager.flags.size).toBe(1);
      expect(manager.flags.has('new_flag')).toBe(true);
      expect(manager.flags.has('flag1')).toBe(false);
    });
  });

  describe('Configuration', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should get current configuration', () => {
      const config = getConfig(manager);

      expect(config.environment).toBe('production');
      expect(config.enableAnalytics).toBe(true);
    });

    it('should update configuration', () => {
      updateConfig(manager, {
        enableAnalytics: false,
        environment: 'staging',
      });

      const config = getConfig(manager);
      expect(config.enableAnalytics).toBe(false);
      expect(config.environment).toBe('staging');
    });

    it('should merge default context', () => {
      manager = createFeatureFlagsManager({
        defaultContext: { environment: 'production' },
      });

      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [
            { attribute: 'environment', operator: 'eq', values: ['production'] },
          ],
        },
      });

      // Context should be merged with default
      const result = evaluateFlag(manager, 'test', {});
      expect(result.value).toBe(true);
    });
  });

  describe('Edge Cases and Error Handling', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager();
    });

    it('should handle empty context', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      const result = evaluateFlag(manager, 'test', {});
      expect(result.value).toBe(false);
    });

    it('should handle undefined context values', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [{ attribute: 'plan', operator: 'eq', values: ['pro'] }],
        },
      });

      const result = evaluateFlag(manager, 'test', { plan: undefined });
      expect(result.value).toBe(false);
    });

    it('should handle empty rollout attribute value', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rollout: { enabled: true, percentage: 50, attribute: 'userId' },
        },
      });

      const result = evaluateFlag(manager, 'test', { userId: '' });
      expect(result.value).toBe(false);
    });

    it('should handle empty user list', () => {
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          userList: [],
        },
      });

      const result = evaluateFlag(manager, 'test', { userId: 'user-123' });
      expect(result.value).toBe(false);
    });

    it('should handle empty rules array', () => {
      // Empty rules array: Array.every() returns true for empty arrays
      // So "all rules match" is true, enabling the flag
      registerFlag(manager, {
        key: 'test',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          rules: [],
        },
      });

      const result = evaluateFlag(manager, 'test', {});
      expect(result.value).toBe(true); // Empty rules means all rules match
      expect(result.reason).toBe('targeting');
    });

    it('should handle variants with unequal weight sum', () => {
      registerFlag(manager, {
        key: 'ab_test',
        type: 'string',
        defaultValue: 'control',
        enabled: true,
        variants: [
          { name: 'control', value: 'control', weight: 60 },
          { name: 'variant_a', value: 'variant_a', weight: 30 },
          // Total is 90, not 100
        ],
      });

      const result = evaluateFlag(manager, 'ab_test', { userId: 'user-123' });
      expect(['control', 'variant_a']).toContain(result.value);
    });

    it('should fallback to first variant when bucket exceeds weight sum', () => {
      registerFlag(manager, {
        key: 'ab_test',
        type: 'string',
        defaultValue: 'control',
        enabled: true,
        variants: [
          { name: 'control', value: 'control', weight: 10 },
        ],
      });

      const result = evaluateFlag(manager, 'ab_test', { userId: 'user-999' });
      expect(result.value).toBe('control');
    });

    it('should handle multiple subscriptions of same listener', () => {
      const listener = vi.fn();
      
      subscribeToFlagChanges(manager, listener);
      subscribeToFlagChanges(manager, listener);

      expect(manager.changeListeners.filter(l => l === listener).length).toBe(2);
    });
  });

  describe('Integration Scenarios', () => {
    let manager: FeatureFlagsState;

    beforeEach(() => {
      manager = createFeatureFlagsManager({
        environment: 'production',
        enableAnalytics: true,
      });
    });

    it('should handle complex targeting with user list and rollout', () => {
      registerFlag(manager, {
        key: 'premium_feature',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
        targeting: {
          userList: ['vip-user-1', 'vip-user-2'],
          rollout: {
            enabled: true,
            percentage: 10,
            attribute: 'userId',
          },
        },
      });

      // VIP user should be enabled (takes priority)
      const result1 = evaluateFlag(manager, 'premium_feature', { userId: 'vip-user-1' });
      expect(result1.value).toBe(true);
      expect(result1.reason).toBe('targeting');

      // Non-VIP user depends on rollout
      const result2 = evaluateFlag(manager, 'premium_feature', { userId: 'regular-user-123' });
      expect(result2.value).toBeTypeOf('boolean');
    });

    it('should export and re-import flags', () => {
      registerFlags(manager, [
        {
          key: 'feature1',
          type: 'boolean',
          defaultValue: false,
          enabled: true,
          targeting: { userList: ['user-1'] },
        },
        {
          key: 'feature2',
          type: 'string',
          defaultValue: 'default',
          enabled: true,
          variants: [
            { name: 'control', value: 'control', weight: 50 },
            { name: 'test', value: 'test', weight: 50 },
          ],
        },
      ]);

      const exported = exportFlags(manager);
      const newManager = createFeatureFlagsManager();
      importFlags(newManager, exported);

      expect(newManager.flags.size).toBe(2);
      expect(getFlag(newManager, 'feature1')?.targeting?.userList).toEqual(['user-1']);
      expect(getFlag(newManager, 'feature2')?.variants?.length).toBe(2);
    });

    it('should track analytics throughout flag lifecycle', () => {
      const analyticsEvents: unknown[] = [];
      subscribeToAnalytics(manager, (event) => {
        analyticsEvents.push(event);
      });

      registerFlag(manager, {
        key: 'tracked_feature',
        type: 'boolean',
        defaultValue: false,
        enabled: true,
      });

      // Evaluate flag
      evaluateFlag(manager, 'tracked_feature', { userId: 'user-123' });

      // Update flag
      updateFlag(manager, 'tracked_feature', { defaultValue: true });

      // Evaluate again
      evaluateFlag(manager, 'tracked_feature', { userId: 'user-123' });

      expect(analyticsEvents.length).toBeGreaterThan(0);
      expect(analyticsEvents.some(e => e.type === 'flag_evaluated')).toBe(true);
      expect(analyticsEvents.some(e => e.type === 'flag_changed')).toBe(true);
    });
  });
});
