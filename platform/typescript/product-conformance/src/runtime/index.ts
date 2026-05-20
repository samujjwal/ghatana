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
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly warnings: readonly string[];
}

/**
 * Conformance check configuration.
 */
export interface ConformanceConfig {
  readonly repoRoot: string;
  readonly productManifestPath: string;
  readonly observabilityFlowPath: string;
  readonly requiredProducts: readonly string[];
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
      const manifest = toRecord(JSON.parse(readFileSync(manifestPath, 'utf8')));
      const flow = toRecord(JSON.parse(readFileSync(flowPath, 'utf8')));

      const manifestProduct = typeof manifest?.product === 'string' ? manifest.product : null;
      if (!manifestProduct) {
        errors.push('Product manifest missing canonical product field');
      }

      const coveredProducts = new Set(readFlowProducts(flow));
      for (const product of mergedConfig.requiredProducts) {
        if (!coveredProducts.has(product)) {
          warnings.push(`Observability flow missing coverage for product: ${product}`);
        }
      }

      // Validate policy actions are namespaced
      const policyActions = readStringArray(manifest?.policyActions);
      if (policyActions) {
        const unprefixedActions = policyActions.filter((action) => !action.includes(':'));
        if (unprefixedActions.length > 0) {
          warnings.push(`Policy actions should be namespaced (e.g., "product:action"): ${unprefixedActions.join(', ')}`);
        }
      }

      // Validate policy resources are namespaced
      const policyResources = readStringArray(manifest?.policyResources);
      if (policyResources) {
        const unprefixedResources = policyResources.filter((resource) => !resource.includes(':'));
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
    writeConformanceWarning(`Product conformance warnings:\n${result.warnings.join('\n')}`);
  }
}

function writeConformanceWarning(message: string): void {
  const proc = (globalThis as {
    process?: { stderr?: { write?: (message: string) => void } };
  }).process;

  if (typeof proc?.stderr?.write === 'function') {
    proc.stderr.write(`${message}\n`);
    return;
  }

  if (
    typeof globalThis.dispatchEvent === 'function' &&
    typeof CustomEvent !== 'undefined'
  ) {
    globalThis.dispatchEvent(
      new CustomEvent('product-conformance-diagnostic', {
        detail: { level: 'warn', message },
      }),
    );
  }
}

function toRecord(value: unknown): Readonly<Record<string, unknown>> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  return value as Readonly<Record<string, unknown>>;
}

function readStringArray(value: unknown): readonly string[] | null {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
    return null;
  }
  return value;
}

function readFlowProducts(flow: Readonly<Record<string, unknown>> | null): readonly string[] {
  const flows = flow?.flows;
  if (!Array.isArray(flows)) {
    return [];
  }
  return flows.flatMap((entry: unknown) => {
    const record = toRecord(entry);
    return typeof record?.product === 'string' ? [record.product] : [];
  });
}
