#!/usr/bin/env node

/**
 * check-gate-result-manifest-completeness.mjs
 *
 * Validates completeness of gate provider declarations and gate result manifest
 * contracts across the kernel platform lifecycle. Ensures:
 *
 *  1. All required gate IDs are declared as gate-pack YAML files for their owning product.
 *  2. Each gate-pack YAML file has the required schema fields.
 *  3. GateResultManifest contract is exported from @ghatana/kernel-product-contracts.
 *  4. GateProvider interface is exported from @ghatana/kernel-product-contracts.
 *  5. GateContracts source file declares parseGateResultManifest().
 *
 * This script is required by Task 2.5 of the kernel platform implementation plan.
 */

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

// ─── Required gate declarations per product ────────────────────────────────

/**
 * Gates that must be declared as gate-pack YAMLs in PHR.
 */
const REQUIRED_PHR_GATES = [
  'consent',
  'pii-classification',
  'audit-evidence',
  'fhir-contract-validation',
  'tenant-data-sovereignty',
];

/**
 * Gates that must be declared as gate-pack YAMLs in Digital Marketing.
 */
const REQUIRED_DIGITAL_MARKETING_GATES = [
  'registry-validation',
  'manifest-validation',
  'lifecycle-contract-validation',
  'bridge-compliance',
  'marketing-consent-boundary',
  'non-regulated-customer-data-minimization',
  'unit-test-coverage',
  'integration-test-coverage',
  'contract-test-coverage',
];

/**
 * Required fields in every gate-pack YAML file.
 */
const REQUIRED_GATE_PACK_FIELDS = [
  'schemaVersion',
  'productId',
  'gateId',
  'title',
  'executionMode',
  'owner',
  'status',
  'description',
  'blockingReasonCodes',
];

/**
 * Paths that must export the generic gate contracts (relative to repo root).
 */
const GATE_CONTRACTS_SOURCE = 'platform/typescript/kernel-product-contracts/src/gate/GateContracts.ts';
const GATE_PROVIDER_SOURCE = 'platform/typescript/kernel-product-contracts/src/provider/GateProvider.ts';

// ─── Helpers ───────────────────────────────────────────────────────────────

function exists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function readText(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function readYaml(relativePath) {
  return YAML.parse(readText(relativePath));
}

function listDir(relativePath) {
  const absolute = path.join(repoRoot, relativePath);
  if (!existsSync(absolute)) return [];
  return readdirSync(absolute);
}

// ─── Checks ────────────────────────────────────────────────────────────────

function checkGatePacksForProduct(productId, gatePackDir, requiredGates, errors) {
  const presentFiles = listDir(gatePackDir);
  const presentGateIds = new Set(
    presentFiles
      .filter((f) => f.endsWith('.yaml'))
      .map((f) => f.replace(/\.yaml$/, '')),
  );

  for (const gateId of requiredGates) {
    const filePath = `${gatePackDir}/${gateId}.yaml`;
    if (!presentGateIds.has(gateId)) {
      errors.push(`[${productId}] Missing required gate-pack: ${filePath}`);
      continue;
    }

    // Validate YAML structure
    let gatePack;
    try {
      gatePack = readYaml(filePath);
    } catch (err) {
      errors.push(`[${productId}] Failed to parse gate-pack YAML at ${filePath}: ${String(err)}`);
      continue;
    }

    for (const field of REQUIRED_GATE_PACK_FIELDS) {
      if (gatePack[field] === undefined || gatePack[field] === null || gatePack[field] === '') {
        errors.push(`[${productId}] Gate-pack ${gateId}.yaml missing required field: ${field}`);
      }
    }

    if (gatePack.productId !== productId) {
      errors.push(
        `[${productId}] Gate-pack ${gateId}.yaml has incorrect productId: ` +
          `expected "${productId}", got "${String(gatePack.productId)}"`,
      );
    }

    if (gatePack.gateId !== gateId) {
      errors.push(
        `[${productId}] Gate-pack ${gateId}.yaml has incorrect gateId: ` +
          `expected "${gateId}", got "${String(gatePack.gateId)}"`,
      );
    }

    const validStatuses = ['planned', 'active', 'disabled'];
    if (!validStatuses.includes(gatePack.status)) {
      errors.push(
        `[${productId}] Gate-pack ${gateId}.yaml has invalid status: "${String(gatePack.status)}"` +
          ` — must be one of: ${validStatuses.join(', ')}`,
      );
    }

    if (!Array.isArray(gatePack.blockingReasonCodes) || gatePack.blockingReasonCodes.length === 0) {
      errors.push(`[${productId}] Gate-pack ${gateId}.yaml must declare at least one blockingReasonCode`);
    }
  }
}

function checkGateContractsSource(errors) {
  if (!exists(GATE_CONTRACTS_SOURCE)) {
    errors.push(`Missing GateContracts source: ${GATE_CONTRACTS_SOURCE}`);
    return;
  }

  const source = readText(GATE_CONTRACTS_SOURCE);

  const requiredExports = [
    'GateResultManifest',
    'GateResultManifestSchema',
    'parseGateResultManifest',
    'GateResultEntry',
    'GateDefinitionSchema',
    'GATE_KINDS',
  ];

  for (const name of requiredExports) {
    if (!source.includes(name)) {
      errors.push(`GateContracts.ts does not declare required export: ${name}`);
    }
  }
}

function checkGateProviderSource(errors) {
  if (!exists(GATE_PROVIDER_SOURCE)) {
    errors.push(`Missing GateProvider source: ${GATE_PROVIDER_SOURCE}`);
    return;
  }

  const source = readText(GATE_PROVIDER_SOURCE);

  const requiredMembers = [
    'GateProvider',
    'GateEvaluationRequest',
    'GateEvaluationResult',
    'evaluateGate',
    'listGates',
  ];

  for (const name of requiredMembers) {
    if (!source.includes(name)) {
      errors.push(`GateProvider.ts does not declare required member: ${name}`);
    }
  }
}

function checkContractsIndexExports(errors) {
  const indexPath = 'platform/typescript/kernel-product-contracts/src/index.ts';
  if (!exists(indexPath)) {
    errors.push(`Missing kernel-product-contracts index: ${indexPath}`);
    return;
  }

  const source = readText(indexPath);

  const requiredExports = [
    'GateResultManifest',
    'GateResultManifestSchema',
    'parseGateResultManifest',
    'GateProvider',
    'GateEvaluationRequest',
    'GateEvaluationResult',
  ];

  for (const name of requiredExports) {
    if (!source.includes(name)) {
      errors.push(
        `kernel-product-contracts/src/index.ts does not re-export required gate contract: ${name}`,
      );
    }
  }
}

// ─── Main ──────────────────────────────────────────────────────────────────

function main() {
  const errors = [];

  console.log('Checking gate result manifest completeness...\n');

  // 1. PHR gate packs
  console.log('  Checking PHR gate packs...');
  checkGatePacksForProduct('phr', 'products/phr/lifecycle/gate-packs', REQUIRED_PHR_GATES, errors);

  // 2. Digital-marketing gate packs
  console.log('  Checking digital-marketing gate packs...');
  checkGatePacksForProduct(
    'digital-marketing',
    'products/digital-marketing/lifecycle/gate-packs',
    REQUIRED_DIGITAL_MARKETING_GATES,
    errors,
  );

  // 3. GateContracts source
  console.log('  Checking GateContracts.ts exports...');
  checkGateContractsSource(errors);

  // 4. GateProvider source
  console.log('  Checking GateProvider.ts declarations...');
  checkGateProviderSource(errors);

  // 5. Index re-exports
  console.log('  Checking kernel-product-contracts index re-exports...');
  checkContractsIndexExports(errors);

  console.log('');

  if (errors.length > 0) {
    console.error(`Gate result manifest completeness check FAILED (${errors.length} error(s)):\n`);
    for (const err of errors) {
      console.error(`  ✗ ${err}`);
    }
    process.exit(1);
  }

  console.log(`Gate result manifest completeness check PASSED.`);
  console.log(`  PHR gate packs (${REQUIRED_PHR_GATES.length}): all present and valid`);
  console.log(`  Digital-marketing gate packs (${REQUIRED_DIGITAL_MARKETING_GATES.length}): all present and valid`);
  console.log('  GateContracts.ts: all required exports declared');
  console.log('  GateProvider.ts: all required members declared');
  console.log('  kernel-product-contracts index: all required re-exports present');
}

main();
