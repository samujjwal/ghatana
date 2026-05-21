/**
 * ProviderModeEnforcer - enforces fail-closed platform mode based on provider health.
 *
 * This module provides contracts and implementation for enforcing that platform mode
 * is only enabled when providers are healthy, following a fail-closed strategy.
 *
 * @doc.type module
 * @doc.purpose Provider mode enforcement based on health matrix
 * @doc.layer kernel-providers
 * @doc.pattern Policy
 */

import type {
  KernelProviderHealthMatrix,
  ProviderHealthStatus,
} from '@ghatana/kernel-product-contracts';

/**
 * Provider mode enforcement result.
 */
export interface ProviderModeEnforcementResult {
  /**
   * Whether the current provider mode is allowed.
   */
  readonly allowed: boolean;

  /**
   * Current provider mode.
   */
  readonly currentMode: 'bootstrap' | 'platform';

  /**
   * Recommended provider mode.
   */
  readonly recommendedMode: 'bootstrap' | 'platform';

  /**
   * Reason for the enforcement decision.
   */
  readonly reason: string;

  /**
   * Blocking issues that prevent platform mode.
   */
  readonly blockingIssues: readonly string[];

  /**
   * Overall health status.
   */
  readonly overallStatus: ProviderHealthStatus;
}

/**
 * Provider mode enforcement configuration.
 */
export interface ProviderModeEnforcementConfig {
  /**
   * Minimum health threshold for platform mode.
   */
  readonly minimumHealthThreshold: number;

  /**
   * Whether to allow degraded providers in platform mode.
   */
  readonly allowDegradedInPlatform: boolean;

  /**
   * Required capabilities for platform mode.
   */
  readonly requiredCapabilitiesForPlatform: readonly string[];

  /**
   * Whether to fail-closed on missing capabilities.
   */
  readonly failClosedOnMissingCapabilities: boolean;
}

/**
 * Default provider mode enforcement configuration.
 */
export const DEFAULT_PROVIDER_MODE_ENFORCEMENT_CONFIG: ProviderModeEnforcementConfig = {
  minimumHealthThreshold: 0.9,
  allowDegradedInPlatform: false,
  requiredCapabilitiesForPlatform: [
    'registry-read',
    'registry-write',
    'source-acquisition',
    'lifecycle-events',
    'artifact-storage',
  ],
  failClosedOnMissingCapabilities: true,
} as const satisfies ProviderModeEnforcementConfig;

/**
 * Provider mode enforcer.
 */
export class ProviderModeEnforcer {
  private readonly config: ProviderModeEnforcementConfig;

  constructor(config: Partial<ProviderModeEnforcementConfig> = {}) {
    this.config = { ...DEFAULT_PROVIDER_MODE_ENFORCEMENT_CONFIG, ...config };
  }

  /**
   * Enforce provider mode based on health matrix.
   */
  enforceProviderMode(
    healthMatrix: KernelProviderHealthMatrix,
    desiredMode: 'bootstrap' | 'platform'
  ): ProviderModeEnforcementResult {
    const blockingIssues: string[] = [];

    // Bootstrap mode is always allowed
    if (desiredMode === 'bootstrap') {
      return {
        allowed: true,
        currentMode: healthMatrix.providerMode,
        recommendedMode: 'bootstrap',
        reason: 'Bootstrap mode is always allowed',
        blockingIssues: [],
        overallStatus: healthMatrix.overallStatus,
      };
    }

    // Platform mode requires healthy providers
    const healthRatio = healthMatrix.totalProviders > 0
      ? healthMatrix.healthyProviders / healthMatrix.totalProviders
      : 0;

    if (healthRatio < this.config.minimumHealthThreshold) {
      blockingIssues.push(
        `Health ratio ${healthRatio.toFixed(2)} is below threshold ${this.config.minimumHealthThreshold}`
      );
    }

    if (!this.config.allowDegradedInPlatform && healthMatrix.degradedProviders > 0) {
      blockingIssues.push(
        `${healthMatrix.degradedProviders} degraded providers not allowed in platform mode`
      );
    }

    if (healthMatrix.unhealthyProviders > 0) {
      blockingIssues.push(
        `${healthMatrix.unhealthyProviders} unhealthy providers prevent platform mode`
      );
    }

    // Check for missing required capabilities
    const missingRequiredCapabilities = healthMatrix.missingCapabilities.filter((cap) =>
      this.config.requiredCapabilitiesForPlatform.includes(cap)
    );

    if (missingRequiredCapabilities.length > 0) {
      blockingIssues.push(
        `Missing required capabilities: ${missingRequiredCapabilities.join(', ')}`
      );
    }

    // Determine if platform mode is allowed
    const allowed = blockingIssues.length === 0;

    return {
      allowed,
      currentMode: healthMatrix.providerMode,
      recommendedMode: allowed ? 'platform' : 'bootstrap',
      reason: allowed
        ? 'All providers healthy and required capabilities available'
        : `Platform mode blocked: ${blockingIssues.join('; ')}`,
      blockingIssues,
      overallStatus: healthMatrix.overallStatus,
    };
  }

  /**
   * Validate provider conformance for platform mode.
   */
  validateProviderConformance(
    healthMatrix: KernelProviderHealthMatrix
  ): {
    readonly valid: boolean;
    readonly errors: readonly string[];
    readonly warnings: readonly string[];
  } {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Check overall health
    if (healthMatrix.overallStatus === 'unhealthy') {
      errors.push('Overall provider health is unhealthy');
    }

    if (healthMatrix.overallStatus === 'unknown') {
      warnings.push('Overall provider health status is unknown');
    }

    // Check for unhealthy providers
    const unhealthyProviderIds = healthMatrix.providers
      .filter((p) => p.status === 'unhealthy')
      .map((p) => p.providerId);

    if (unhealthyProviderIds.length > 0) {
      errors.push(`Unhealthy providers: ${unhealthyProviderIds.join(', ')}`);
    }

    // Check for missing capabilities
    if (healthMatrix.missingCapabilities.length > 0) {
      if (this.config.failClosedOnMissingCapabilities) {
        errors.push(`Missing required capabilities: ${healthMatrix.missingCapabilities.join(', ')}`);
      } else {
        warnings.push(`Missing optional capabilities: ${healthMatrix.missingCapabilities.join(', ')}`);
      }
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
    };
  }

  /**
   * Get recommended provider mode based on health matrix.
   */
  getRecommendedMode(healthMatrix: KernelProviderHealthMatrix): 'bootstrap' | 'platform' {
    const enforcement = this.enforceProviderMode(healthMatrix, 'platform');
    return enforcement.recommendedMode;
  }
}
