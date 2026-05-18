#!/usr/bin/env node
/**
 * check-product-registry-consistency.mjs
 *
 * Governance wrapper: validates canonical-product-registry.json for structural
 * consistency, ensuring Gradle includes, pnpm workspace, lifecycle config paths,
 * and pilot/blocked state all align.
 *
 * Delegates to ../validate-product-registry.mjs and adds additional checks
 * specific to kernel-todo.md §1.4 requirements.
 *
 * Exits non-zero on any violation.
 */

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..', '..');

/** The only product that may have lifecycleExecutionAllowed: true. */
const LIFECYCLE_PILOT_ID = 'digital-marketing';

/** Products that must remain blocked until their gates pass. */
const BLOCKED_PRODUCT_IDS = new Set(['phr', 'finance', 'flashit', 'data-cloud', 'yappc']);

/**
 * Minimum required reason codes per blocked product — §2.3 Kernel contract alignment.
 * These reason codes align to the gate types defined in GateContracts.ts.
 * A blocked product missing ANY of its domain-specific reason codes is a governance violation.
 */
const REQUIRED_BLOCKER_REASON_CODES = {
  'phr': [
    'requires-consent-gate',
    'requires-pii-classification',
    'requires-audit-evidence',
    'requires-fhir-contract-validation',
    'requires-data-sovereignty-gate',
  ],
  'finance': [
    'requires-regulatory-gates',
    'requires-promotion-approval',
    'requires-multi-module-build-validation',
  ],
  'flashit': [
    'requires-mobile-adapters',
    'requires-preview-security-gate',
    'requires-personal-data-classification',
    'requires-mobile-bundle-artifacts',
  ],
  'data-cloud': [
    'platform-provider-mode-required',
    'requires-bootstrap-platform-separation',
    'requires-runtime-truth-provider',
  ],
  'yappc': [
    'platform-provider-mode-required',
    'creator-lifecycle-distinct-from-kernel',
    'artifact-intelligence-evidence-contracts-ready',
  ],
};

/**
 * Products whose kind is "platform-provider" must NEVER be enabled as an ordinary lifecycle product.
 * Detected by the presence of 'platform-provider-mode-required' in their reason codes.
 */
const PLATFORM_PROVIDER_REASON_CODE = 'platform-provider-mode-required';

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function resolvePath(relative) {
  return path.join(repoRoot, relative);
}

/**
 * Validate a product registry object (the `registry` field from canonical-product-registry.json).
 * Exported so tests can exercise the validation logic without touching the real file system.
 *
 * @param {Record<string, unknown>} products - The products map from the registry file.
 * @returns {string[]} Array of issue strings (empty = pass).
 */
export function validateProductRegistryProducts(products) {
  const issues = [];
  let enabledPilots = 0;

  for (const [productId, product] of Object.entries(products)) {
    const isEnabled = product.lifecycleExecutionAllowed === true;
    const isBlocked = BLOCKED_PRODUCT_IDS.has(productId);

    // Digital Marketing is the only allowed pilot
    if (isEnabled) {
      enabledPilots++;
      if (productId !== LIFECYCLE_PILOT_ID) {
        issues.push(
          `[${productId}] lifecycleExecutionAllowed is true but only '${LIFECYCLE_PILOT_ID}' may be the lifecycle pilot. ` +
          `Remediation: Set lifecycleExecutionAllowed to false or provide full proof gates.`
        );
      }
    }

    // Blocked products must remain disabled
    if (isBlocked && isEnabled) {
      issues.push(
        `[${productId}] is in the blocked list but has lifecycleExecutionAllowed: true. ` +
        `Remediation: Keep this product disabled until blocker gates are resolved.`
      );
    }

    // Blocked products must have reason codes
    if (isBlocked) {
      const readiness = product.lifecycleReadiness;
      if (!readiness || !Array.isArray(readiness.reasonCodes) || readiness.reasonCodes.length === 0) {
        issues.push(
          `[${productId}] Blocked product must have lifecycleReadiness.reasonCodes. ` +
          `Remediation: Add reason codes documenting why this product is blocked.`
        );
      }

      // §2.3: domain-specific required blocker codes must all be present
      const requiredCodes = REQUIRED_BLOCKER_REASON_CODES[productId];
      if (requiredCodes) {
        const actualCodes = new Set(readiness?.reasonCodes ?? []);
        for (const code of requiredCodes) {
          if (!actualCodes.has(code)) {
            issues.push(
              `[${productId}] Missing required blocker reason code '${code}'. ` +
              `This code aligns to a required gate in GateContracts.ts. ` +
              `Remediation: Add '${code}' to lifecycleReadiness.reasonCodes.`
            );
          }
        }
      }

      // §2.3: platform-provider products must never be enabled as ordinary lifecycle products
      const isPlatformProvider =
        Array.isArray(readiness?.reasonCodes) &&
        readiness.reasonCodes.includes(PLATFORM_PROVIDER_REASON_CODE);
      if (isPlatformProvider && isEnabled) {
        issues.push(
          `[${productId}] Platform-provider products must not be enabled as ordinary lifecycle products. ` +
          `Remediation: Keep platform-provider-mode-required in reason codes and lifecycleExecutionAllowed: false.`
        );
      }
    }

    // lifecycleConfigPath must exist if set — skipped in unit validation (file system not available)

    // Enabled products must have non-empty surfaces and phase toolchains
    if (isEnabled) {
      const lifecycle = product.lifecycle;
      if (!lifecycle || lifecycle.enabled !== true) {
        issues.push(
          `[${productId}] lifecycleExecutionAllowed is true but lifecycle.enabled is not true. ` +
          `Remediation: Set lifecycle.enabled: true for the pilot.`
        );
      }

      const surfaceCount = Array.isArray(product.surfaces)
        ? product.surfaces.length
        : Object.keys(product.surfaces ?? {}).length;
      if (!product.surfaces || surfaceCount === 0) {
        issues.push(
          `[${productId}] Enabled lifecycle product must declare surfaces. ` +
          `Remediation: Add surface declarations.`
        );
      }

      // §2.3: enabled lifecycle pilot must declare toolchain adapters
      const toolchainAdapters = product.toolchain?.adapters ?? product.lifecycle?.toolchain ?? {};
      const hasAnyToolchain = Object.keys(toolchainAdapters).length > 0;
      if (!hasAnyToolchain) {
        issues.push(
          `[${productId}] Enabled lifecycle product must declare toolchain adapters (toolchain.adapters or lifecycle.toolchain). ` +
          `Remediation: Add toolchain adapter declarations for each surface.`
        );
      }

      // §2.3: enabled lifecycle pilot must declare artifacts for at least one surface
      const artifacts = product.artifacts ?? {};
      const hasAnyArtifact = Object.keys(artifacts).length > 0;
      if (!hasAnyArtifact) {
        issues.push(
          `[${productId}] Enabled lifecycle product must declare artifacts for at least one surface. ` +
          `Remediation: Add artifact declarations aligned to ArtifactReferences.ts ARTIFACT_TYPES.`
        );
      }
    }
  }

  // Exactly one pilot must be enabled
  if (enabledPilots === 0) {
    issues.push(
      `No product has lifecycleExecutionAllowed: true. ` +
      `Remediation: Ensure '${LIFECYCLE_PILOT_ID}' has lifecycleExecutionAllowed: true.`
    );
  } else if (enabledPilots > 1) {
    issues.push(
      `${enabledPilots} products have lifecycleExecutionAllowed: true. Only the designated pilot may be enabled. ` +
      `Remediation: Set lifecycleExecutionAllowed: false for all except '${LIFECYCLE_PILOT_ID}'.`
    );
  }

  return issues;
}

/**
 * Run product registry governance checks beyond the base schema validation.
 * Returns an array of issue strings (empty = pass).
 */
export function runProductRegistryConsistencyChecks() {
  const issues = [];
  const registryPath = resolvePath('config/canonical-product-registry.json');

  if (!existsSync(registryPath)) {
    issues.push('MISSING: config/canonical-product-registry.json does not exist.');
    return issues;
  }

  let registry;
  try {
    registry = readJson(registryPath);
  } catch (err) {
    issues.push(`PARSE_ERROR: canonical-product-registry.json is not valid JSON: ${String(err)}`);
    return issues;
  }

  const products = registry.registry ?? {};

  // Product-level validation (shared with unit tests via validateProductRegistryProducts)
  issues.push(...validateProductRegistryProducts(products));

  // File-system checks that require a real repo checkout
  for (const [productId, product] of Object.entries(products)) {
    if (product.lifecycleConfigPath) {
      const configPath = resolvePath(product.lifecycleConfigPath);
      if (!existsSync(configPath)) {
        issues.push(
          `[${productId}] lifecycleConfigPath '${product.lifecycleConfigPath}' does not exist. ` +
          `Remediation: Create the config file or correct the path.`
        );
      }
    }
  }

  // Validate generated settings include file references
  const generatedIncludesPath = resolvePath('config/generated/settings-gradle-includes.kts');
  if (existsSync(generatedIncludesPath)) {
    const includesContent = readFileSync(generatedIncludesPath, 'utf8');
    if (!includesContent.includes('DO NOT EDIT MANUALLY') && !includesContent.includes('do not edit manually')) {
      issues.push(
        'config/generated/settings-gradle-includes.kts is missing "DO NOT EDIT MANUALLY" header. ' +
        'Remediation: Regenerate using the product registry generation script.'
      );
    }
  } else {
    issues.push(
      'config/generated/settings-gradle-includes.kts does not exist. ' +
      'Remediation: Run the product registry artifact generation script to create it.'
    );
  }

  return issues;
}

// CLI entrypoint
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const validateModule = path.join(repoRoot, 'scripts', 'validate-product-registry.mjs');
  const { validateProductRegistryFiles } = await import(validateModule);

  let baseIssues = [];
  try {
    baseIssues = validateProductRegistryFiles();
  } catch (err) {
    baseIssues = [`FATAL: ${String(err)}`];
  }

  const governanceIssues = runProductRegistryConsistencyChecks();
  const allIssues = [...baseIssues, ...governanceIssues];

  if (allIssues.length > 0) {
    console.error('FAIL: product registry consistency checks found issues:');
    for (const issue of allIssues) {
      const msg = typeof issue === 'object' ? JSON.stringify(issue) : String(issue);
      console.error(` - ${msg}`);
    }
    process.exit(1);
  } else {
    console.log('OK: product registry consistency checks passed.');
  }
}
