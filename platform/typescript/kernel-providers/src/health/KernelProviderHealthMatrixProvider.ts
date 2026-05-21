/**
 * KernelProviderHealthMatrixProvider - aggregates health from all registered Kernel providers.
 *
 * This provider collects health status from all registered Kernel providers
 * and aggregates them into a KernelProviderHealthMatrix for platform-wide health monitoring.
 *
 * @doc.type class
 * @doc.purpose Aggregate health matrix provider for Kernel provider health monitoring
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  KernelProviderHealthMatrix,
  ProviderCapability,
  ProviderCapabilityDeclaration,
  ProviderHealthEntry,
  ProviderHealthStatus,
} from '@ghatana/kernel-product-contracts';

export interface KernelProviderHealthMatrixProviderOptions {
  /**
   * Provider mode (bootstrap or platform).
   */
  readonly providerMode: 'bootstrap' | 'platform';

  /**
   * Timeout for individual provider health checks in milliseconds.
   */
  readonly timeoutMs?: number;
}

/**
 * Health check result for a single provider.
 */
interface ProviderHealthCheckResult {
  readonly providerId: string;
  readonly providerKind: string;
  readonly status: ProviderHealthStatus;
  readonly message: string;
  readonly capabilities: ProviderCapabilityDeclaration[];
  readonly error?: string;
  readonly latencyMs?: number;
  readonly checkedAt?: string;
}

/**
 * Provider health matrix implementation.
 */
export class KernelProviderHealthMatrixProvider {
  readonly providerId = 'kernel-provider-health-matrix';
  readonly version = '1.0.0';

  private readonly providerMode: 'bootstrap' | 'platform';
  private readonly timeoutMs: number;
  private readonly registeredProviders: Map<string, ProviderHealthCheckResult>;

  constructor(options: KernelProviderHealthMatrixProviderOptions) {
    this.providerMode = options.providerMode;
    this.timeoutMs = options.timeoutMs ?? 30000;
    this.registeredProviders = new Map();
  }

  /**
   * Register a provider with its health status and capabilities.
   */
  registerProvider(result: ProviderHealthCheckResult): void {
    this.registeredProviders.set(result.providerId, {
      ...result,
      checkedAt: new Date().toISOString(),
    });
  }

  /**
   * Unregister a provider.
   */
  unregisterProvider(providerId: string): void {
    this.registeredProviders.delete(providerId);
  }

  /**
   * Generate the current health matrix.
   */
  generateHealthMatrix(correlationId?: string): KernelProviderHealthMatrix {
    const providers = Array.from(this.registeredProviders.values());
    const totalProviders = providers.length;
    const healthyProviders = providers.filter((p) => p.status === 'healthy').length;
    const degradedProviders = providers.filter((p) => p.status === 'degraded').length;
    const unhealthyProviders = providers.filter((p) => p.status === 'unhealthy').length;
    const unknownProviders = providers.filter((p) => p.status === 'unknown').length;

    // Determine overall status
    let overallStatus: ProviderHealthStatus = 'healthy';
    if (unhealthyProviders > 0) {
      overallStatus = 'unhealthy';
    } else if (degradedProviders > 0) {
      overallStatus = 'degraded';
    } else if (unknownProviders > 0) {
      overallStatus = 'unknown';
    }

    // Identify missing required capabilities
    const missingCapabilities = this.identifyMissingCapabilities(providers);

    return {
      schemaVersion: '1.0.0',
      generatedAt: new Date().toISOString(),
      overallStatus,
      totalProviders,
      healthyProviders,
      degradedProviders,
      unhealthyProviders,
      unknownProviders,
      providers: providers.map((p) => ({
        providerId: p.providerId,
        providerKind: p.providerKind,
        status: p.status,
        checkedAt: p.checkedAt || new Date().toISOString(),
        message: p.message,
        capabilities: [...p.capabilities],
        error: p.error,
        latencyMs: p.latencyMs,
      })),
      providerMode: this.providerMode,
      missingCapabilities,
      correlationId,
    };
  }

  /**
   * Identify missing or unhealthy required capabilities.
   */
  private identifyMissingCapabilities(
    providers: ProviderHealthCheckResult[]
  ): ProviderCapability[] {
    const missing: ProviderCapability[] = [];

    for (const provider of providers) {
      for (const capability of provider.capabilities) {
        if (capability.required && !capability.available) {
          missing.push(capability.capability);
        } else if (
          capability.required &&
          (provider.status === 'unhealthy' || provider.status === 'unknown')
        ) {
          missing.push(capability.capability);
        }
      }
    }

    // Deduplicate
    return Array.from(new Set(missing));
  }

  /**
   * Get a specific provider's health status.
   */
  getProviderHealth(providerId: string): ProviderHealthEntry | null {
    const result = this.registeredProviders.get(providerId);
    if (!result) {
      return null;
    }

    return {
      ...result,
      checkedAt: new Date().toISOString(),
    };
  }

  /**
   * Get all provider IDs.
   */
  getProviderIds(): readonly string[] {
    return Array.from(this.registeredProviders.keys());
  }

  /**
   * Clear all registered providers.
   */
  clearProviders(): void {
    this.registeredProviders.clear();
  }
}
