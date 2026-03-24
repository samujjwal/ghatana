/**
 * Feature Flag System for Canvas Application
 * 
 * Provides runtime feature toggles for gradual rollouts, A/B testing,
 * and risk mitigation during deployments.
 * 
 * Features:
 * - Boolean, number, and string flag types
 * - User-based targeting (% rollout, user lists)
 * - Environment-based overrides
 * - Real-time flag evaluation
 * - Flag change subscriptions
 * - Analytics event tracking
 * 
 * @module featureFlags
 */

// ============================================================================
// Types
// ============================================================================

/**
 * Feature flag value types
 */
export type FlagValue = boolean | number | string;

/**
 * Feature flag type
 */
export type FlagType = 'boolean' | 'number' | 'string';

/**
 * Targeting rule operator
 */
export type TargetingOperator = 
  | 'in' 
  | 'not_in' 
  | 'contains' 
  | 'starts_with' 
  | 'ends_with'
  | 'gt'
  | 'gte'
  | 'lt'
  | 'lte'
  | 'eq'
  | 'neq';

/**
 * Targeting rule
 */
export interface TargetingRule {
  attribute: string; // e.g., 'userId', 'email', 'plan'
  operator: TargetingOperator;
  values: string[];
}

/**
 * Rollout percentage
 */
export interface RolloutPercentage {
  enabled: boolean;
  percentage: number; // 0-100
  attribute: string; // attribute to hash for consistent bucketing
}

/**
 * Feature flag variant
 */
export interface FlagVariant {
  name: string;
  value: FlagValue;
  weight: number; // 0-100, sum of all variants should be 100
}

/**
 * Feature flag configuration
 */
export interface FeatureFlag {
  key: string;
  type: FlagType;
  defaultValue: FlagValue;
  enabled: boolean;
  description?: string;
  
  // Targeting
  targeting?: {
    rules?: TargetingRule[];
    rollout?: RolloutPercentage;
    userList?: string[]; // Specific user IDs
  };
  
  // Variants (for A/B testing)
  variants?: FlagVariant[];
}

/**
 * Flag evaluation context (user attributes)
 */
export interface EvaluationContext {
  userId?: string;
  email?: string;
  plan?: string;
  environment?: string;
  [key: string]: string | undefined;
}

/**
 * Flag evaluation result
 */
export interface FlagEvaluation {
  key: string;
  value: FlagValue;
  variant?: string;
  reason: string; // 'default' | 'targeting' | 'rollout' | 'variant'
}

/**
 * Flag change event
 */
export interface FlagChangeEvent {
  key: string;
  oldValue: FlagValue;
  newValue: FlagValue;
  timestamp: Date;
}

/**
 * Analytics event
 */
export interface AnalyticsEvent {
  type: 'flag_evaluated' | 'flag_changed';
  flagKey: string;
  value: FlagValue;
  userId?: string;
  timestamp: Date;
  metadata?: Record<string, unknown>;
}

/**
 * Feature flags manager configuration
 */
export interface FeatureFlagsConfig {
  /** Environment name */
  environment: string;
  /** Enable analytics tracking */
  enableAnalytics: boolean;
  /** Default evaluation context */
  defaultContext?: EvaluationContext;
}

/**
 * Feature flags manager state
 */
export interface FeatureFlagsState {
  config: FeatureFlagsConfig;
  flags: Map<string, FeatureFlag>;
  changeListeners: Array<(event: FlagChangeEvent) => void>;
  analyticsListeners: Array<(event: AnalyticsEvent) => void>;
}

// ============================================================================
// Manager
// ============================================================================

/**
 * Create feature flags manager
 */
export function createFeatureFlagsManager(
  config: Partial<FeatureFlagsConfig> = {}
): FeatureFlagsState {
  const defaultConfig: FeatureFlagsConfig = {
    environment: 'production',
    enableAnalytics: true,
    defaultContext: {},
  };

  return {
    config: { ...defaultConfig, ...config },
    flags: new Map(),
    changeListeners: [],
    analyticsListeners: [],
  };
}

// ============================================================================
// Flag Registration
// ============================================================================

/**
 * Register feature flag
 */
export function registerFlag(
  state: FeatureFlagsState,
  flag: FeatureFlag
): void {
  state.flags.set(flag.key, flag);
}

/**
 * Register multiple flags
 */
export function registerFlags(
  state: FeatureFlagsState,
  flags: FeatureFlag[]
): void {
  for (const flag of flags) {
    registerFlag(state, flag);
  }
}

/**
 * Unregister flag
 */
export function unregisterFlag(
  state: FeatureFlagsState,
  key: string
): boolean {
  return state.flags.delete(key);
}

/**
 * Get flag configuration
 */
export function getFlag(
  state: FeatureFlagsState,
  key: string
): FeatureFlag | null {
  return state.flags.get(key) || null;
}

/**
 * Get all flags
 */
export function getAllFlags(state: FeatureFlagsState): FeatureFlag[] {
  return Array.from(state.flags.values());
}

// ============================================================================
// Flag Evaluation
// ============================================================================

/**
 * Hash string to number (for consistent bucketing)
 */
function hashString(str: string): number {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash);
}

/**
 * Evaluate targeting rule
 */
function evaluateRule(
  rule: TargetingRule,
  context: EvaluationContext
): boolean {
  const attributeValue = context[rule.attribute];
  if (attributeValue === undefined) {
    return false;
  }

  switch (rule.operator) {
    case 'in':
      return rule.values.includes(attributeValue);
    case 'not_in':
      return !rule.values.includes(attributeValue);
    case 'contains':
      return rule.values.some((v) => attributeValue.includes(v));
    case 'starts_with':
      return rule.values.some((v) => attributeValue.startsWith(v));
    case 'ends_with':
      return rule.values.some((v) => attributeValue.endsWith(v));
    case 'gt':
      return parseFloat(attributeValue) > parseFloat(rule.values[0]);
    case 'gte':
      return parseFloat(attributeValue) >= parseFloat(rule.values[0]);
    case 'lt':
      return parseFloat(attributeValue) < parseFloat(rule.values[0]);
    case 'lte':
      return parseFloat(attributeValue) <= parseFloat(rule.values[0]);
    case 'eq':
      return attributeValue === rule.values[0];
    case 'neq':
      return attributeValue !== rule.values[0];
    default:
      return false;
  }
}

/**
 * Check if user is in rollout percentage
 */
function isInRollout(
  rollout: RolloutPercentage,
  context: EvaluationContext
): boolean {
  if (!rollout.enabled) {
    return false;
  }

  const attributeValue = context[rollout.attribute];
  if (!attributeValue) {
    return false;
  }

  // Hash attribute value to get consistent bucketing
  const hash = hashString(attributeValue);
  const bucket = hash % 100;

  return bucket < rollout.percentage;
}

/**
 * Select variant based on weights
 */
function selectVariant(
  variants: FlagVariant[],
  context: EvaluationContext
): FlagVariant {
  // Use userId for consistent variant selection
  const userId = context.userId || 'anonymous';
  const hash = hashString(userId);
  const bucket = hash % 100;

  let cumulativeWeight = 0;
  for (const variant of variants) {
    cumulativeWeight += variant.weight;
    if (bucket < cumulativeWeight) {
      return variant;
    }
  }

  // Fallback to first variant
  return variants[0];
}

/**
 * Evaluate feature flag
 */
export function evaluateFlag(
  state: FeatureFlagsState,
  key: string,
  context: EvaluationContext = {}
): FlagEvaluation {
  const flag = state.flags.get(key);

  // Flag not found - return default false
  if (!flag) {
    return {
      key,
      value: false,
      reason: 'default',
    };
  }

  // Merge with default context
  const fullContext = { ...state.config.defaultContext, ...context };

  // Flag disabled - return default value
  if (!flag.enabled) {
    return {
      key,
      value: flag.defaultValue,
      reason: 'default',
    };
  }

  // Check user list targeting
  if (flag.targeting?.userList && fullContext.userId) {
    if (flag.targeting.userList.includes(fullContext.userId)) {
      // Use variant if available
      if (flag.variants) {
        const variant = selectVariant(flag.variants, fullContext);
        trackAnalytics(state, {
          type: 'flag_evaluated',
          flagKey: key,
          value: variant.value,
          userId: fullContext.userId,
          timestamp: new Date(),
          metadata: { variant: variant.name, reason: 'targeting' },
        });
        return {
          key,
          value: variant.value,
          variant: variant.name,
          reason: 'targeting',
        };
      }

      trackAnalytics(state, {
        type: 'flag_evaluated',
        flagKey: key,
        value: true,
        userId: fullContext.userId,
        timestamp: new Date(),
        metadata: { reason: 'targeting' },
      });

      return {
        key,
        value: true,
        reason: 'targeting',
      };
    }
  }

  // Check targeting rules
  if (flag.targeting?.rules) {
    const allRulesMatch = flag.targeting.rules.every((rule) =>
      evaluateRule(rule, fullContext)
    );

    if (allRulesMatch) {
      // Use variant if available
      if (flag.variants) {
        const variant = selectVariant(flag.variants, fullContext);
        trackAnalytics(state, {
          type: 'flag_evaluated',
          flagKey: key,
          value: variant.value,
          userId: fullContext.userId,
          timestamp: new Date(),
          metadata: { variant: variant.name, reason: 'targeting' },
        });
        return {
          key,
          value: variant.value,
          variant: variant.name,
          reason: 'targeting',
        };
      }

      trackAnalytics(state, {
        type: 'flag_evaluated',
        flagKey: key,
        value: true,
        userId: fullContext.userId,
        timestamp: new Date(),
        metadata: { reason: 'targeting' },
      });

      return {
        key,
        value: true,
        reason: 'targeting',
      };
    }
  }

  // Check rollout percentage
  if (flag.targeting?.rollout) {
    const inRollout = isInRollout(flag.targeting.rollout, fullContext);

    if (inRollout) {
      // Use variant if available
      if (flag.variants) {
        const variant = selectVariant(flag.variants, fullContext);
        trackAnalytics(state, {
          type: 'flag_evaluated',
          flagKey: key,
          value: variant.value,
          userId: fullContext.userId,
          timestamp: new Date(),
          metadata: { variant: variant.name, reason: 'rollout' },
        });
        return {
          key,
          value: variant.value,
          variant: variant.name,
          reason: 'rollout',
        };
      }

      trackAnalytics(state, {
        type: 'flag_evaluated',
        flagKey: key,
        value: true,
        userId: fullContext.userId,
        timestamp: new Date(),
        metadata: { reason: 'rollout' },
      });

      return {
        key,
        value: true,
        reason: 'rollout',
      };
    }
  }

  // Use variants if available and no targeting matched
  if (flag.variants) {
    const variant = selectVariant(flag.variants, fullContext);
    trackAnalytics(state, {
      type: 'flag_evaluated',
      flagKey: key,
      value: variant.value,
      userId: fullContext.userId,
      timestamp: new Date(),
      metadata: { variant: variant.name, reason: 'variant' },
    });
    return {
      key,
      value: variant.value,
      variant: variant.name,
      reason: 'variant',
    };
  }

  // Default value
  trackAnalytics(state, {
    type: 'flag_evaluated',
    flagKey: key,
    value: flag.defaultValue,
    userId: fullContext.userId,
    timestamp: new Date(),
    metadata: { reason: 'default' },
  });

  return {
    key,
    value: flag.defaultValue,
    reason: 'default',
  };
}

/**
 * Check if feature is enabled (boolean shortcut)
 */
export function isFeatureEnabled(
  state: FeatureFlagsState,
  key: string,
  context: EvaluationContext = {}
): boolean {
  const evaluation = evaluateFlag(state, key, context);
  return Boolean(evaluation.value);
}

/**
 * Get feature flag value with type
 */
export function getFlagValue<T extends FlagValue>(
  state: FeatureFlagsState,
  key: string,
  defaultValue: T,
  context: EvaluationContext = {}
): T {
  const evaluation = evaluateFlag(state, key, context);
  return (evaluation.value as T) ?? defaultValue;
}

// ============================================================================
// Flag Updates
// ============================================================================

/**
 * Update flag configuration
 */
export function updateFlag(
  state: FeatureFlagsState,
  key: string,
  updates: Partial<FeatureFlag>
): FeatureFlag | null {
  const flag = state.flags.get(key);
  if (!flag) {
    return null;
  }

  const oldValue = flag.defaultValue;
  const updatedFlag = { ...flag, ...updates };
  state.flags.set(key, updatedFlag);

  // Notify change listeners if default value changed
  if (updates.defaultValue !== undefined && updates.defaultValue !== oldValue) {
    notifyFlagChange(state, {
      key,
      oldValue,
      newValue: updates.defaultValue,
      timestamp: new Date(),
    });
  }

  return updatedFlag;
}

/**
 * Toggle flag enabled state
 */
export function toggleFlag(
  state: FeatureFlagsState,
  key: string
): FeatureFlag | null {
  const flag = state.flags.get(key);
  if (!flag) {
    return null;
  }

  return updateFlag(state, key, { enabled: !flag.enabled });
}

// ============================================================================
// Subscriptions
// ============================================================================

/**
 * Subscribe to flag changes
 */
export function subscribeToFlagChanges(
  state: FeatureFlagsState,
  listener: (event: FlagChangeEvent) => void
): () => void {
  state.changeListeners.push(listener);

  return () => {
    const index = state.changeListeners.indexOf(listener);
    if (index !== -1) {
      state.changeListeners.splice(index, 1);
    }
  };
}

/**
 * Subscribe to analytics events
 */
export function subscribeToAnalytics(
  state: FeatureFlagsState,
  listener: (event: AnalyticsEvent) => void
): () => void {
  state.analyticsListeners.push(listener);

  return () => {
    const index = state.analyticsListeners.indexOf(listener);
    if (index !== -1) {
      state.analyticsListeners.splice(index, 1);
    }
  };
}

/**
 * Notify flag change listeners
 */
function notifyFlagChange(
  state: FeatureFlagsState,
  event: FlagChangeEvent
): void {
  for (const listener of state.changeListeners) {
    try {
      listener(event);
    } catch (error) {
      console.error('Flag change listener error:', error);
    }
  }

  // Track as analytics event
  trackAnalytics(state, {
    type: 'flag_changed',
    flagKey: event.key,
    value: event.newValue,
    timestamp: event.timestamp,
    metadata: { oldValue: event.oldValue },
  });
}

/**
 * Track analytics event
 */
function trackAnalytics(
  state: FeatureFlagsState,
  event: AnalyticsEvent
): void {
  if (!state.config.enableAnalytics) {
    return;
  }

  for (const listener of state.analyticsListeners) {
    try {
      listener(event);
    } catch (error) {
      console.error('Analytics listener error:', error);
    }
  }
}

// ============================================================================
// Bulk Operations
// ============================================================================

/**
 * Evaluate multiple flags at once
 */
export function evaluateFlags(
  state: FeatureFlagsState,
  keys: string[],
  context: EvaluationContext = {}
): Map<string, FlagEvaluation> {
  const results = new Map<string, FlagEvaluation>();

  for (const key of keys) {
    results.set(key, evaluateFlag(state, key, context));
  }

  return results;
}

/**
 * Export flags configuration (for backup/restore)
 */
export function exportFlags(state: FeatureFlagsState): FeatureFlag[] {
  return Array.from(state.flags.values());
}

/**
 * Import flags configuration
 */
export function importFlags(
  state: FeatureFlagsState,
  flags: FeatureFlag[]
): void {
  state.flags.clear();
  registerFlags(state, flags);
}

// ============================================================================
// Configuration
// ============================================================================

/**
 * Get configuration
 */
export function getConfig(state: FeatureFlagsState): FeatureFlagsConfig {
  return { ...state.config };
}

/**
 * Update configuration
 */
export function updateConfig(
  state: FeatureFlagsState,
  updates: Partial<FeatureFlagsConfig>
): FeatureFlagsConfig {
  state.config = { ...state.config, ...updates };
  return { ...state.config };
}
