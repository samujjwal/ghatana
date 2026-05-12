/**
 * Runtime conformance validation.
 * Provides runtime checks for product conformance during execution.
 */

import { readFileSync, existsSync } from 'fs';
import { join } from 'path';

/**
 * Runtime conformance check result.
 */
export interface RuntimeCheckResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Conformance check configuration.
 */
export interface ConformanceConfig {
  repoRoot: string;
  productManifestPath: string;
  observabilityFlowPath: string;
  requiredProducts: string[];
}

/**
 * Default conformance configuration.
 */
export const defaultConformanceConfig: ConformanceConfig = {
  repoRoot: process.cwd(),
  productManifestPath: 'domain-pack-manifest.json',
  observabilityFlowPath: 'config/observability/product-observability-flows.json',
  requiredProducts: ['phr', 'finance', 'digital-marketing', 'flashit'],
};

/**
 * Validates product conformance at runtime.
 */
export function validateProductConformance(config: Partial<ConformanceConfig> = {}): RuntimeCheckResult {
  const mergedConfig: ConformanceConfig = { ...defaultConformanceConfig, ...config };
  const errors: string[] = [];
  const warnings: string[] = [];

  // Validate product manifest exists
  const manifestPath = join(mergedConfig.repoRoot, mergedConfig.productManifestPath);
  if (!existsSync(manifestPath)) {
    errors.push(`Product manifest not found: ${manifestPath}`);
  }

  // Validate observability flow manifest exists
  const flowPath = join(mergedConfig.repoRoot, mergedConfig.observabilityFlowPath);
  if (!existsSync(flowPath)) {
    errors.push(`Observability flow manifest not found: ${flowPath}`);
  }

  // Validate required products are covered if manifests exist
  if (existsSync(manifestPath) && existsSync(flowPath)) {
    try {
      const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
      const flow = JSON.parse(readFileSync(flowPath, 'utf8'));

      const manifestId = manifest.id || manifest.productName?.toLowerCase();
      if (!manifestId) {
        errors.push('Product manifest missing id or productName field');
      }

      const coveredProducts = new Set(flow.flows?.map((f: any) => f.product) || []);
      for (const product of mergedConfig.requiredProducts) {
        if (!coveredProducts.has(product)) {
          warnings.push(`Observability flow missing coverage for product: ${product}`);
        }
      }

      // Validate policy actions are namespaced
      if (manifest.policyActions && Array.isArray(manifest.policyActions)) {
        const unprefixedActions = manifest.policyActions.filter((action: string) => 
          !action.includes(':')
        );
        if (unprefixedActions.length > 0) {
          warnings.push(`Policy actions should be namespaced (e.g., "product:action"): ${unprefixedActions.join(', ')}`);
        }
      }

      // Validate policy resources are namespaced
      if (manifest.policyResources && Array.isArray(manifest.policyResources)) {
        const unprefixedResources = manifest.policyResources.filter((resource: string) => 
          !resource.includes(':')
        );
        if (unprefixedResources.length > 0) {
          warnings.push(`Policy resources should be namespaced (e.g., "product:resource"): ${unprefixedResources.join(', ')}`);
        }
      }
    } catch (error) {
      errors.push(`Failed to parse manifests: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

/**
 * Throws an error if conformance validation fails.
 */
export function assertProductConformance(config?: Partial<ConformanceConfig>): void {
  const result = validateProductConformance(config);
  if (!result.valid) {
    throw new Error(`Product conformance validation failed:\n${result.errors.join('\n')}`);
  }
  if (result.warnings.length > 0) {
    console.warn(`Product conformance warnings:\n${result.warnings.join('\n')}`);
  }
}
