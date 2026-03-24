/**
 * Canvas Deployment Management System
 *
 * Comprehensive deployment orchestration with blue/green strategies, feature flags,
 * and rollback capabilities for canvas infrastructure.
 *
 * @module operations/deployment
 */

/**
 * Deployment slot types for blue/green deployments
 */
export type DeploymentSlot = 'blue' | 'green';

/**
 * Deployment environment types
 */
export type DeploymentEnvironment = 'development' | 'staging' | 'production';

/**
 * Deployment status states
 */
export type DeploymentStatus =
  | 'pending'
  | 'in-progress'
  | 'deployed'
  | 'active'
  | 'rollback'
  | 'failed';

/**
 * Feature flag configuration
 */
export interface FeatureFlag {
  /** Unique flag identifier */
  key: string;
  /** Human-readable name */
  name: string;
  /** Flag description */
  description: string;
  /** Whether flag is enabled */
  enabled: boolean;
  /** Target environment */
  environment: DeploymentEnvironment;
  /** Rollout percentage (0-100) */
  rolloutPercentage?: number;
  /** User segment targeting */
  targetSegments?: string[];
  /** Creation timestamp */
  createdAt: Date;
  /** Last update timestamp */
  updatedAt: Date;
}

/**
 * Deployment configuration
 */
export interface DeploymentConfig {
  /** Deployment ID */
  id: string;
  /** Target environment */
  environment: DeploymentEnvironment;
  /** Target slot */
  slot: DeploymentSlot;
  /** Application version */
  version: string;
  /** Build artifact URL */
  artifactUrl: string;
  /** Feature flags to enable */
  featureFlags: string[];
  /** Health check endpoint */
  healthCheckUrl: string;
  /** Health check timeout (ms) */
  healthCheckTimeout: number;
  /** Deployment timestamp */
  timestamp: Date;
}

/**
 * Health check result
 */
export interface HealthCheckResult {
  /** Check success status */
  healthy: boolean;
  /** Response time (ms) */
  responseTime: number;
  /** Status code */
  statusCode?: number;
  /** Error message if unhealthy */
  error?: string;
  /** Timestamp of check */
  timestamp: Date;
}

/**
 * Deployment record with full history
 */
export interface DeploymentRecord {
  /** Deployment configuration */
  config: DeploymentConfig;
  /** Current status */
  status: DeploymentStatus;
  /** Active slot */
  activeSlot?: DeploymentSlot;
  /** Health check results */
  healthChecks: HealthCheckResult[];
  /** Deployment start time */
  startedAt?: Date;
  /** Deployment completion time */
  completedAt?: Date;
  /** Deployment duration (ms) */
  duration?: number;
  /** Error message if failed */
  error?: string;
}

/**
 * Rollback configuration
 */
export interface RollbackConfig {
  /** Target deployment ID to rollback to */
  targetDeploymentId: string;
  /** Reason for rollback */
  reason: string;
  /** Whether to skip health checks */
  skipHealthChecks?: boolean;
  /** Rollback timestamp */
  timestamp: Date;
}

/**
 * Traffic split configuration for gradual rollouts
 */
export interface TrafficSplit {
  /** Blue slot traffic percentage */
  bluePercentage: number;
  /** Green slot traffic percentage */
  greenPercentage: number;
  /** Start time */
  startedAt: Date;
  /** Update time */
  updatedAt: Date;
}

/**
 * Deployment rehearsal configuration
 */
export interface RehearsalConfig {
  /** Rehearsal ID */
  id: string;
  /** Target environment */
  environment: DeploymentEnvironment;
  /** Deployment config to rehearse */
  deploymentConfig: DeploymentConfig;
  /** Dry run mode (no actual deployment) */
  dryRun: boolean;
  /** Validation steps to perform */
  validationSteps: string[];
  /** Created timestamp */
  createdAt: Date;
}

/**
 * Rehearsal result
 */
export interface RehearsalResult {
  /** Rehearsal config */
  config: RehearsalConfig;
  /** Success status */
  success: boolean;
  /** Validation results */
  validationResults: Record<string, boolean>;
  /** Duration (ms) */
  duration: number;
  /** Issues found */
  issues: string[];
  /** Timestamp */
  timestamp: Date;
}

/**
 * Deployment manager configuration options
 */
export interface DeploymentManagerConfig {
  /** Default health check timeout (ms) */
  defaultHealthCheckTimeout?: number;
  /** Max concurrent deployments */
  maxConcurrentDeployments?: number;
  /** Deployment history retention (days) */
  historyRetentionDays?: number;
  /** Enable automatic rollback on failure */
  autoRollbackOnFailure?: boolean;
}

/**
 * Deployment Manager
 *
 * Manages blue/green deployments with feature flags and rollback capabilities.
 *
 * @example
 * ```typescript
 * const manager = new DeploymentManager();
 *
 * // Create deployment config
 * const config = manager.createDeploymentConfig({
 *   environment: 'production',
 *   slot: 'green',
 *   version: '1.2.0',
 *   artifactUrl: 'https://cdn.example.com/canvas-1.2.0.tar.gz',
 *   featureFlags: ['collaboration', 'devsecops'],
 *   healthCheckUrl: 'https://api.example.com/health'
 * });
 *
 * // Deploy to green slot
 * await manager.deploy(config);
 *
 * // Switch traffic to green
 * await manager.switchActiveSlot('production', 'green');
 *
 * // Rollback if needed
 * await manager.rollback('production', 'Health check failed');
 * ```
 */
export class DeploymentManager {
  private deployments = new Map<string, DeploymentRecord>();
  private featureFlags = new Map<string, FeatureFlag>();
  private activeSlots = new Map<DeploymentEnvironment, DeploymentSlot>();
  private trafficSplits = new Map<DeploymentEnvironment, TrafficSplit>();
  private rehearsals = new Map<string, RehearsalResult>();
  private config: Required<DeploymentManagerConfig>;
  private deploymentCounter = 0;

  /**
   *
   */
  constructor(config: DeploymentManagerConfig = {}) {
    this.config = {
      defaultHealthCheckTimeout: config.defaultHealthCheckTimeout ?? 30000,
      maxConcurrentDeployments: config.maxConcurrentDeployments ?? 3,
      historyRetentionDays: config.historyRetentionDays ?? 90,
      autoRollbackOnFailure: config.autoRollbackOnFailure ?? true,
    };
  }

  /**
   * Create a deployment configuration
   */
  createDeploymentConfig(
    params: Omit<DeploymentConfig, 'id' | 'timestamp' | 'healthCheckTimeout'>
  ): DeploymentConfig {
    return {
      ...params,
      id: this.generateDeploymentId(params.environment, params.version),
      timestamp: new Date(),
      healthCheckTimeout: this.config.defaultHealthCheckTimeout,
    };
  }

  /**
   * Deploy to a specific slot
   */
  async deploy(config: DeploymentConfig): Promise<DeploymentRecord> {
    // Validate deployment capacity
    const activeDeployments = Array.from(this.deployments.values()).filter(
      (d) => d.status === 'in-progress'
    );

    if (activeDeployments.length >= this.config.maxConcurrentDeployments) {
      throw new Error(
        `Max concurrent deployments (${this.config.maxConcurrentDeployments}) exceeded`
      );
    }

    const startedAt = new Date();

    const record: DeploymentRecord = {
      config,
      status: 'in-progress',
      healthChecks: [],
      startedAt,
    };

    this.deployments.set(config.id, record);

    try {
      // Simulate deployment steps
      await this.simulateDeploymentSteps(config);

      // Perform health check
      const healthCheck = await this.performHealthCheck(
        config.healthCheckUrl,
        config.healthCheckTimeout
      );

      record.healthChecks.push(healthCheck);

      if (!healthCheck.healthy) {
        throw new Error(`Health check failed: ${healthCheck.error}`);
      }

      // Mark as deployed
      const completedAt = new Date();
      record.status = 'deployed';
      record.completedAt = completedAt;
      record.duration = completedAt.getTime() - startedAt.getTime();

      return record;
    } catch (error) {
      record.status = 'failed';
      record.error = error instanceof Error ? error.message : String(error);
      record.completedAt = new Date();
      record.duration = record.completedAt.getTime() - startedAt.getTime();

      // Auto rollback if enabled
      if (this.config.autoRollbackOnFailure) {
        await this.rollback(config.environment, record.error);
      }

      throw error;
    }
  }

  /**
   * Switch active slot for an environment
   */
  async switchActiveSlot(
    environment: DeploymentEnvironment,
    targetSlot: DeploymentSlot
  ): Promise<void> {
    // Find deployed deployment for target slot
    const deployment = Array.from(this.deployments.values()).find(
      (d) =>
        d.config.environment === environment &&
        d.config.slot === targetSlot &&
        d.status === 'deployed'
    );

    if (!deployment) {
      throw new Error(
        `No deployed deployment found for ${environment}/${targetSlot}`
      );
    }

    // Perform health check before switching
    const healthCheck = await this.performHealthCheck(
      deployment.config.healthCheckUrl,
      deployment.config.healthCheckTimeout
    );

    if (!healthCheck.healthy) {
      throw new Error(
        `Cannot switch to unhealthy slot: ${healthCheck.error}`
      );
    }

    // Update active slot
    this.activeSlots.set(environment, targetSlot);
    deployment.status = 'active';
    deployment.activeSlot = targetSlot;

    // Deactivate other slot
    const otherSlot: DeploymentSlot = targetSlot === 'blue' ? 'green' : 'blue';
    const otherDeployment = Array.from(this.deployments.values()).find(
      (d) =>
        d.config.environment === environment &&
        d.config.slot === otherSlot &&
        d.status === 'active'
    );

    if (otherDeployment) {
      otherDeployment.status = 'deployed';
    }

    // Reset traffic split to 100% active slot
    this.trafficSplits.set(environment, {
      bluePercentage: targetSlot === 'blue' ? 100 : 0,
      greenPercentage: targetSlot === 'green' ? 100 : 0,
      startedAt: new Date(),
      updatedAt: new Date(),
    });
  }

  /**
   * Rollback to previous deployment
   */
  async rollback(
    environment: DeploymentEnvironment,
    reason: string
  ): Promise<void> {
    const currentSlot = this.activeSlots.get(environment);
    if (!currentSlot) {
      throw new Error(`No active deployment for ${environment}`);
    }

    // Find previous slot
    const previousSlot: DeploymentSlot =
      currentSlot === 'blue' ? 'green' : 'blue';

    // Find previous deployment
    const previousDeployment = Array.from(this.deployments.values()).find(
      (d) =>
        d.config.environment === environment &&
        d.config.slot === previousSlot &&
        d.status === 'deployed'
    );

    if (!previousDeployment) {
      throw new Error(`No previous deployment found to rollback to`);
    }

    // Record rollback
    const rollbackConfig: RollbackConfig = {
      targetDeploymentId: previousDeployment.config.id,
      reason,
      timestamp: new Date(),
    };

    // Mark current deployment as rollback BEFORE switching
    const currentDeployment = Array.from(this.deployments.values()).find(
      (d) =>
        d.config.environment === environment &&
        d.config.slot === currentSlot &&
        (d.status === 'active' || d.status === 'failed')
    );

    if (currentDeployment) {
      currentDeployment.status = 'rollback';
      currentDeployment.error = reason;
    }

    // Switch back to previous slot
    await this.switchActiveSlot(environment, previousSlot);
  }

  /**
   * Configure traffic split for gradual rollout
   */
  setTrafficSplit(
    environment: DeploymentEnvironment,
    bluePercentage: number,
    greenPercentage: number
  ): void {
    if (bluePercentage + greenPercentage !== 100) {
      throw new Error('Traffic split must add up to 100%');
    }

    if (bluePercentage < 0 || greenPercentage < 0) {
      throw new Error('Traffic percentages must be non-negative');
    }

    const existing = this.trafficSplits.get(environment);
    const now = new Date();

    this.trafficSplits.set(environment, {
      bluePercentage,
      greenPercentage,
      startedAt: existing?.startedAt ?? now,
      updatedAt: now,
    });
  }

  /**
   * Get traffic split for environment
   */
  getTrafficSplit(environment: DeploymentEnvironment): TrafficSplit | null {
    return this.trafficSplits.get(environment) ?? null;
  }

  /**
   * Create or update a feature flag
   */
  setFeatureFlag(flag: Omit<FeatureFlag, 'createdAt' | 'updatedAt'>): void {
    const existing = this.featureFlags.get(flag.key);
    const now = new Date();

    this.featureFlags.set(flag.key, {
      ...flag,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    });
  }

  /**
   * Get feature flag value
   */
  getFeatureFlag(key: string): FeatureFlag | null {
    return this.featureFlags.get(key) ?? null;
  }

  /**
   * Check if feature is enabled
   */
  isFeatureEnabled(
    key: string,
    environment: DeploymentEnvironment,
    userId?: string
  ): boolean {
    const flag = this.featureFlags.get(key);

    if (!flag || !flag.enabled || flag.environment !== environment) {
      return false;
    }

    // Check rollout percentage
    if (flag.rolloutPercentage !== undefined) {
      if (userId) {
        // Consistent hashing for user ID
        const hash = this.hashUserId(userId);
        return hash < flag.rolloutPercentage;
      }
      // Random sampling if no user ID
      return Math.random() * 100 < flag.rolloutPercentage;
    }

    return true;
  }

  /**
   * Get all feature flags for environment
   */
  getFeatureFlagsForEnvironment(
    environment: DeploymentEnvironment
  ): FeatureFlag[] {
    return Array.from(this.featureFlags.values()).filter(
      (f) => f.environment === environment
    );
  }

  /**
   * Delete a feature flag
   */
  deleteFeatureFlag(key: string): boolean {
    return this.featureFlags.delete(key);
  }

  /**
   * Get deployment by ID
   */
  getDeployment(id: string): DeploymentRecord | null {
    return this.deployments.get(id) ?? null;
  }

  /**
   * Get all deployments for environment
   */
  getDeploymentsForEnvironment(
    environment: DeploymentEnvironment
  ): DeploymentRecord[] {
    return Array.from(this.deployments.values()).filter(
      (d) => d.config.environment === environment
    );
  }

  /**
   * Get active deployment for environment
   */
  getActiveDeployment(
    environment: DeploymentEnvironment
  ): DeploymentRecord | null {
    return (
      Array.from(this.deployments.values()).find(
        (d) => d.config.environment === environment && d.status === 'active'
      ) ?? null
    );
  }

  /**
   * Get active slot for environment
   */
  getActiveSlot(environment: DeploymentEnvironment): DeploymentSlot | null {
    return this.activeSlots.get(environment) ?? null;
  }

  /**
   * Create deployment rehearsal
   */
  createRehearsal(
    params: Omit<RehearsalConfig, 'id' | 'createdAt'>
  ): RehearsalConfig {
    const config: RehearsalConfig = {
      ...params,
      id: this.generateRehearsalId(params.environment),
      createdAt: new Date(),
    };

    return config;
  }

  /**
   * Run deployment rehearsal
   */
  async runRehearsal(config: RehearsalConfig): Promise<RehearsalResult> {
    const startTime = Date.now();
    const validationResults: Record<string, boolean> = {};
    const issues: string[] = [];

    // Validate each step
    for (const step of config.validationSteps) {
      try {
        const result = await this.validateRehearsalStep(step, config);
        validationResults[step] = result;

        if (!result) {
          issues.push(`Validation failed for step: ${step}`);
        }
      } catch (error) {
        validationResults[step] = false;
        issues.push(
          `Error in step ${step}: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }

    // Perform dry-run deployment if enabled
    if (config.dryRun) {
      try {
        await this.simulateDeploymentSteps(config.deploymentConfig);
      } catch (error) {
        issues.push(
          `Dry-run deployment failed: ${error instanceof Error ? error.message : String(error)}`
        );
      }
    }

    const duration = Date.now() - startTime;
    const success = issues.length === 0;

    const result: RehearsalResult = {
      config,
      success,
      validationResults,
      duration,
      issues,
      timestamp: new Date(),
    };

    this.rehearsals.set(config.id, result);

    return result;
  }

  /**
   * Get rehearsal result
   */
  getRehearsalResult(id: string): RehearsalResult | null {
    return this.rehearsals.get(id) ?? null;
  }

  /**
   * Get all rehearsal results
   */
  getAllRehearsals(): RehearsalResult[] {
    return Array.from(this.rehearsals.values());
  }

  /**
   * Clean up old deployment records
   */
  cleanupOldDeployments(): number {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - this.config.historyRetentionDays);

    let removed = 0;

    for (const [id, record] of this.deployments.entries()) {
      if (
        record.status !== 'active' &&
        record.completedAt &&
        record.completedAt < cutoffDate
      ) {
        this.deployments.delete(id);
        removed++;
      }
    }

    return removed;
  }

  /**
   * Get deployment statistics
   */
  getDeploymentStats(environment?: DeploymentEnvironment): {
    total: number;
    active: number;
    deployed: number;
    failed: number;
    rollback: number;
    avgDuration: number;
  } {
    let deployments = Array.from(this.deployments.values());

    if (environment) {
      deployments = deployments.filter(
        (d) => d.config.environment === environment
      );
    }

    const total = deployments.length;
    const active = deployments.filter((d) => d.status === 'active').length;
    const deployed = deployments.filter((d) => d.status === 'deployed').length;
    const failed = deployments.filter((d) => d.status === 'failed').length;
    const rollback = deployments.filter((d) => d.status === 'rollback').length;

    const durations = deployments
      .filter((d) => d.duration !== undefined)
      .map((d) => d.duration!);

    const avgDuration =
      durations.length > 0
        ? durations.reduce((a, b) => a + b, 0) / durations.length
        : 0;

    return {
      total,
      active,
      deployed,
      failed,
      rollback,
      avgDuration,
    };
  }

  /**
   * Export deployment history
   */
  exportDeploymentHistory(environment?: DeploymentEnvironment): string {
    let deployments = Array.from(this.deployments.values());

    if (environment) {
      deployments = deployments.filter(
        (d) => d.config.environment === environment
      );
    }

    const history = deployments.map((d) => ({
      id: d.config.id,
      environment: d.config.environment,
      slot: d.config.slot,
      version: d.config.version,
      status: d.status,
      startedAt: d.startedAt?.toISOString(),
      completedAt: d.completedAt?.toISOString(),
      duration: d.duration,
      error: d.error,
    }));

    return JSON.stringify(history, null, 2);
  }

  /**
   * Reset manager state (preserves configuration)
   */
  reset(): void {
    this.deployments.clear();
    this.featureFlags.clear();
    this.activeSlots.clear();
    this.trafficSplits.clear();
    this.rehearsals.clear();
  }

  // Private helper methods

  /**
   *
   */
  private generateDeploymentId(
    environment: DeploymentEnvironment,
    version: string
  ): string {
    const timestamp = Date.now();
    const counter = this.deploymentCounter++;
    return `deploy-${environment}-${version}-${timestamp}-${counter}`;
  }

  /**
   *
   */
  private generateRehearsalId(environment: DeploymentEnvironment): string {
    const timestamp = Date.now();
    return `rehearsal-${environment}-${timestamp}`;
  }

  /**
   *
   */
  private async simulateDeploymentSteps(
    config: DeploymentConfig
  ): Promise<void> {
    // Simulate deployment steps (in real implementation, this would execute actual deployment)
    await new Promise((resolve) => setTimeout(resolve, 10));

    // Validate artifact URL
    if (!config.artifactUrl.startsWith('http')) {
      throw new Error('Invalid artifact URL');
    }

    // Validate version
    if (!config.version.match(/^\d+\.\d+\.\d+$/)) {
      throw new Error('Invalid version format');
    }
  }

  /**
   *
   */
  private async performHealthCheck(
    url: string,
    timeout: number
  ): Promise<HealthCheckResult> {
    const startTime = Date.now();

    try {
      // Simulate health check (in real implementation, this would make HTTP request)
      await new Promise((resolve) => setTimeout(resolve, 10));

      const responseTime = Date.now() - startTime;

      // Simulate success for valid URLs
      if (url.includes('/health') || url.includes('/status')) {
        return {
          healthy: true,
          responseTime,
          statusCode: 200,
          timestamp: new Date(),
        };
      }

      return {
        healthy: false,
        responseTime,
        statusCode: 404,
        error: 'Health check endpoint not found',
        timestamp: new Date(),
      };
    } catch (error) {
      return {
        healthy: false,
        responseTime: Date.now() - startTime,
        error: error instanceof Error ? error.message : String(error),
        timestamp: new Date(),
      };
    }
  }

  /**
   *
   */
  private async validateRehearsalStep(
    step: string,
    config: RehearsalConfig
  ): Promise<boolean> {
    // Simulate validation (in real implementation, this would perform actual checks)
    await new Promise((resolve) => setTimeout(resolve, 5));

    switch (step) {
      case 'artifact-check':
        return config.deploymentConfig.artifactUrl.startsWith('http');
      case 'version-check':
        return !!config.deploymentConfig.version.match(/^\d+\.\d+\.\d+$/);
      case 'health-check':
        return config.deploymentConfig.healthCheckUrl.includes('/health');
      case 'feature-flags':
        return config.deploymentConfig.featureFlags.length > 0;
      default:
        return true;
    }
  }

  /**
   *
   */
  private hashUserId(userId: string): number {
    // Simple hash function for consistent user ID -> percentage mapping
    let hash = 0;
    for (let i = 0; i < userId.length; i++) {
      hash = (hash << 5) - hash + userId.charCodeAt(i);
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash % 100);
  }
}

/**
 * Deployment helper functions
 */

/**
 * Calculate deployment time estimate
 */
export function estimateDeploymentTime(
  environment: DeploymentEnvironment,
  artifactSize: number
): number {
  // Base deployment time by environment (ms)
  const baseTime: Record<DeploymentEnvironment, number> = {
    development: 60000, // 1 minute
    staging: 120000, // 2 minutes
    production: 300000, // 5 minutes
  };

  // Add time based on artifact size (10 seconds per MB)
  const sizeTime = (artifactSize / 1024 / 1024) * 10000;

  return baseTime[environment] + sizeTime;
}

/**
 * Validate deployment configuration
 */
export function validateDeploymentConfig(
  config: DeploymentConfig
): { valid: boolean; errors: string[] } {
  const errors: string[] = [];

  if (!config.artifactUrl.startsWith('http')) {
    errors.push('Artifact URL must start with http:// or https://');
  }

  if (!config.version.match(/^\d+\.\d+\.\d+$/)) {
    errors.push('Version must be in semver format (e.g., 1.2.3)');
  }

  if (!config.healthCheckUrl.startsWith('http')) {
    errors.push('Health check URL must start with http:// or https://');
  }

  if (config.healthCheckTimeout < 1000) {
    errors.push('Health check timeout must be at least 1000ms');
  }

  if (config.featureFlags.length === 0) {
    errors.push('At least one feature flag must be specified');
  }

  return {
    valid: errors.length === 0,
    errors,
  };
}

/**
 * Format deployment duration for display
 */
export function formatDeploymentDuration(durationMs: number): string {
  const seconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  }

  if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  }

  return `${seconds}s`;
}

/**
 * Calculate rollback time based on environment
 */
export function calculateRollbackTime(
  environment: DeploymentEnvironment
): number {
  // Rollback time in milliseconds
  const rollbackTime: Record<DeploymentEnvironment, number> = {
    development: 30000, // 30 seconds
    staging: 60000, // 1 minute
    production: 120000, // 2 minutes (target: <5 minutes)
  };

  return rollbackTime[environment];
}
