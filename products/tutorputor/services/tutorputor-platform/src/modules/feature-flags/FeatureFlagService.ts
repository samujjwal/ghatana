/**
 * Feature Flag Service
 *
 * Manages feature flags for the TutorPutor platform.
 * Supports environment-based, user-based, and percentage-based rollouts.
 *
 * @doc.type class
 * @doc.purpose Feature flag management for controlled rollouts
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'FeatureFlagService' });

export interface FeatureFlag {
  key: string;
  enabled: boolean;
  description: string;
  rolloutPercentage?: number;
  userWhitelist?: string[];
  userBlacklist?: string[];
  environmentWhitelist?: string[];
}

export class FeatureFlagService {
  private flags: Map<string, FeatureFlag> = new Map();
  private environment: string;

  constructor(environment = process.env.NODE_ENV || 'development') {
    this.environment = environment;
    this.initializeDefaultFlags();
  }

  /**
   * Initialize default feature flags
   */
  private initializeDefaultFlags(): void {
    const defaultFlags: FeatureFlag[] = [
      {
        key: 'ai_tutoring',
        enabled: true,
        description: 'Enable AI-powered tutoring',
        environmentWhitelist: ['development', 'staging', 'production'],
      },
      {
        key: 'simulations',
        enabled: true,
        description: 'Enable interactive simulations',
        environmentWhitelist: ['development', 'staging', 'production'],
      },
      {
        key: 'marketplace',
        enabled: false,
        description: 'Enable content marketplace',
        rolloutPercentage: 10,
        environmentWhitelist: ['staging'],
      },
      {
        key: 'analytics_dashboard',
        enabled: true,
        description: 'Enable analytics dashboard',
        environmentWhitelist: ['development', 'staging', 'production'],
      },
      {
        key: 'gamification',
        enabled: true,
        description: 'Enable gamification features',
        rolloutPercentage: 50,
        environmentWhitelist: ['development', 'staging', 'production'],
      },
      {
        key: 'new_ui',
        enabled: false,
        description: 'Enable new UI design',
        rolloutPercentage: 5,
        environmentWhitelist: ['staging'],
      },
      {
        key: 'mobile_learner_flows',
        enabled: false,
        description: 'Enable mobile learner flows (disabled in production until mobile reaches parity)',
        environmentWhitelist: ['development', 'staging'],
      },
      {
        key: 'vr_webxr',
        enabled: false,
        description: 'Enable VR/WebXR features (deferred indefinitely)',
        environmentWhitelist: [],
      },
    ];

    defaultFlags.forEach(flag => {
      this.flags.set(flag.key, flag);
    });

    logger.info({
      message: 'Feature flags initialized',
      count: defaultFlags.length,
      environment: this.environment,
    });
  }

  /**
   * Check if a feature flag is enabled for a specific user
   */
  isEnabled(key: string, userId?: string): boolean {
    const flag = this.flags.get(key);

    if (!flag) {
      logger.warn({
        message: 'Feature flag not found',
        key,
      });
      return false;
    }

    // Check environment whitelist
    if (flag.environmentWhitelist && !flag.environmentWhitelist.includes(this.environment)) {
      return false;
    }

    // Check user blacklist
    if (userId && flag.userBlacklist && flag.userBlacklist.includes(userId)) {
      return false;
    }

    // Check user whitelist
    if (userId && flag.userWhitelist && flag.userWhitelist.includes(userId)) {
      return true;
    }

    // Check rollout percentage
    if (flag.rolloutPercentage && userId) {
      const hash = this.hashUserId(userId);
      return hash % 100 < flag.rolloutPercentage;
    }

    return flag.enabled;
  }

  /**
   * Enable a feature flag
   */
  enable(key: string): void {
    const flag = this.flags.get(key);
    if (flag) {
      flag.enabled = true;
      logger.info({
        message: 'Feature flag enabled',
        key,
      });
    }
  }

  /**
   * Disable a feature flag
   */
  disable(key: string): void {
    const flag = this.flags.get(key);
    if (flag) {
      flag.enabled = false;
      logger.info({
        message: 'Feature flag disabled',
        key,
      });
    }
  }

  /**
   * Set rollout percentage for a feature flag
   */
  setRolloutPercentage(key: string, percentage: number): void {
    const flag = this.flags.get(key);
    if (flag) {
      flag.rolloutPercentage = Math.min(100, Math.max(0, percentage));
      logger.info({
        message: 'Feature flag rollout percentage updated',
        key,
        percentage: flag.rolloutPercentage,
      });
    }
  }

  /**
   * Add user to whitelist
   */
  addToWhitelist(key: string, userId: string): void {
    const flag = this.flags.get(key);
    if (flag) {
      flag.userWhitelist = flag.userWhitelist || [];
      if (!flag.userWhitelist.includes(userId)) {
        flag.userWhitelist.push(userId);
        logger.info({
          message: 'User added to feature flag whitelist',
          key,
          userId,
        });
      }
    }
  }

  /**
   * Remove user from whitelist
   */
  removeFromWhitelist(key: string, userId: string): void {
    const flag = this.flags.get(key);
    if (flag && flag.userWhitelist) {
      flag.userWhitelist = flag.userWhitelist.filter(id => id !== userId);
      logger.info({
        message: 'User removed from feature flag whitelist',
        key,
        userId,
      });
    }
  }

  /**
   * Get all feature flags
   */
  getAllFlags(): FeatureFlag[] {
    return Array.from(this.flags.values());
  }

  /**
   * Get a specific feature flag
   */
  getFlag(key: string): FeatureFlag | undefined {
    return this.flags.get(key);
  }

  /**
   * Hash user ID for consistent rollout
   */
  private hashUserId(userId: string): number {
    let hash = 0;
    for (let i = 0; i < userId.length; i++) {
      const char = userId.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash);
  }
}
