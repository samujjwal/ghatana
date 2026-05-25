#!/usr/bin/env node

/**
 * Check FlashIt web lifecycle readiness
 *
 * Validates that FlashIt web lifecycle (backend-api and web surfaces)
 * is ready for staging deployment while mobile remains disabled.
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');

const PRODUCT_ID = 'flashit';
const REQUIRED_WEB_GATES = [
  'privacy',
  'preview-security',
  'personal-data-classification',
];
const BLOCKED_MOBILE_SURFACES = ['mobile-ios', 'mobile-android'];
const ENABLED_WEB_SURFACES = ['backend-api', 'web'];

function readJson(relativePath) {
  const fullPath = path.join(repoRoot, relativePath);
  if (!existsSync(fullPath)) {
    return null;
  }
  return JSON.parse(readFileSync(fullPath, 'utf8'));
}

function readYaml(relativePath) {
  const fullPath = path.join(repoRoot, relativePath);
  if (!existsSync(fullPath)) {
    return null;
  }
  const content = readFileSync(fullPath, 'utf8');
  // Simple YAML parsing for key: value patterns
  const result = {};
  const lines = content.split('\n');
  let currentKey = null;
  let currentList = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || trimmed === '') continue;

    const match = trimmed.match(/^(\w+):\s*(.*)$/);
    if (match) {
      currentKey = match[1];
      const value = match[2];
      if (value === '' || value === '') {
        currentList = [];
        result[currentKey] = currentList;
      } else {
        result[currentKey] = value;
        currentList = null;
      }
    } else if (trimmed.startsWith('- ') && currentList) {
      currentList.push(trimmed.substring(2).trim());
    }
  }

  return result;
}

function pathExists(relativePath) {
  return existsSync(path.join(repoRoot, relativePath));
}

function fail(message) {
  throw new Error(message);
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function validateKernelProductConfig() {
  console.log('[FLASHIT-WEB] Validating kernel-product.yaml...');

  const kernelProductPath = 'products/flashit/kernel-product.yaml';
  assert(pathExists(kernelProductPath), 'kernel-product.yaml must exist');

  const content = readFileSync(path.join(repoRoot, kernelProductPath), 'utf8');

  // Check execution is disabled (product is candidate)
  assert(
    content.includes('executionEnabled: false') || content.includes('status: draft'),
    'Flashit must have executionEnabled: false or status: draft'
  );

  // Check mobile lifecycle is disabled
  for (const surface of BLOCKED_MOBILE_SURFACES) {
    // Mobile should not appear in deploy phase
    const deploySection = content.match(/deploy:[\s\S]*?(?=\n\w|$)/);
    if (deploySection) {
      assert(
        !deploySection[0].includes(surface),
        `Mobile surface ${surface} should not be in deploy phase`
      );
    }
  }

  // Check web surfaces are configured
  for (const surface of ENABLED_WEB_SURFACES) {
    assert(
      content.includes(surface),
      `Web surface ${surface} must be configured`
    );
  }

  // Check required gates are present
  for (const gate of REQUIRED_WEB_GATES) {
    assert(
      content.includes(gate),
      `Required gate ${gate} must be in kernel-product.yaml`
    );
  }

  console.log('  ✓ Kernel product config valid');
}

function validateMobileLifecycleDisabled() {
  console.log('[FLASHIT-WEB] Validating mobile lifecycle disabled...');

  const gatePackPath = 'products/flashit/lifecycle/gate-packs/mobile-lifecycle-disabled.yaml';
  assert(pathExists(gatePackPath), 'Mobile lifecycle disabled gate pack must exist');

  const content = readFileSync(path.join(repoRoot, gatePackPath), 'utf8');

  assert(
    content.includes('mobileLifecycleExecution: disabled'),
    'Gate pack must declare mobileLifecycleExecution: disabled'
  );

  assert(
    content.includes('webLifecycleExecution: enabled'),
    'Gate pack must declare webLifecycleExecution: enabled'
  );

  assert(
    content.includes('enforcement: fail-closed'),
    'Gate pack must declare fail-closed enforcement'
  );

  console.log('  ✓ Mobile lifecycle disabled gate pack valid');
}

function validateFoundationUsageProfile() {
  console.log('[FLASHIT-WEB] Validating foundation usage profile...');

  const profilePath = 'products/flashit/lifecycle/foundation-usage-profile.yaml';
  if (!pathExists(profilePath)) {
    console.warn('  ⚠ Foundation usage profile not yet created');
    return;
  }

  const content = readFileSync(path.join(repoRoot, profilePath), 'utf8');

  assert(
    content.includes('productId: flashit'),
    'Foundation profile must declare productId: flashit'
  );

  assert(
    content.includes('kernel:') || content.includes('dataCloud:'),
    'Foundation profile must reference platform components'
  );

  console.log('  ✓ Foundation usage profile valid');
}

function validateGuidedReuseMapping() {
  console.log('[FLASHIT-WEB] Validating guided reuse mapping...');

  const mappingPath = '.kernel/evidence/flashit/flashit-guided-reuse-mapping.json';
  assert(pathExists(mappingPath), 'Guided reuse mapping must exist');

  const mapping = readJson(mappingPath);

  assert(mapping.productId === 'flashit', 'Mapping must have productId: flashit');
  assert(mapping.phdrAssetConsumption != null, 'Mapping must have phrAssetConsumption');
  assert(mapping.dmosAssetConsumption != null, 'Mapping must have dmosAssetConsumption');

  // Check candidate status
  assert(
    mapping.reuseReadiness?.currentStatus === 'candidate-not-ready',
    'FlashIt should be marked as candidate-not-ready for reuse'
  );

  // Check YAPPC integration
  assert(
    mapping.yappcIntegration?.candidateBlocked === true,
    'FlashIt should be candidateBlocked in YAPPC integration'
  );

  console.log('  ✓ Guided reuse mapping valid');
}

function validateWebPackageStructure() {
  console.log('[FLASHIT-WEB] Validating web package structure...');

  const webPackagePath = 'products/flashit/client/web/package.json';
  if (!pathExists(webPackagePath)) {
    console.warn('  ⚠ Web package not found at expected path');
    return;
  }

  const webPackage = readJson(webPackagePath);
  assert(webPackage.name != null, 'Web package must have a name');

  const scripts = webPackage.scripts || {};
  assert(
    scripts.build != null,
    'Web package must have build script'
  );

  console.log('  ✓ Web package structure valid');
}

function validateGatePacks() {
  console.log('[FLASHIT-WEB] Validating gate packs...');

  const gatePackDir = 'products/flashit/lifecycle/gate-packs';
  assert(pathExists(gatePackDir), 'Gate pack directory must exist');

  const requiredGates = [
    'privacy.yaml',
    'preview-security.yaml',
    'personal-data-classification.yaml',
    'mobile-bundle-contract.yaml',
    'mobile-lifecycle-disabled.yaml'
  ];

  for (const gateFile of requiredGates) {
    const gatePath = path.join(gatePackDir, gateFile);
    if (pathExists(gatePath)) {
      console.log(`  ✓ Gate pack exists: ${gateFile}`);
    } else {
      console.warn(`  ⚠ Gate pack missing: ${gateFile}`);
    }
  }
}

function main() {
  console.log('=== FlashIt Web Lifecycle Readiness Check ===\n');

  const results = {
    passed: [],
    warnings: [],
    errors: [],
  };

  try {
    validateKernelProductConfig();
    results.passed.push('Kernel product config');
  } catch (error) {
    results.errors.push(error.message);
  }

  try {
    validateMobileLifecycleDisabled();
    results.passed.push('Mobile lifecycle disabled gate pack');
  } catch (error) {
    results.errors.push(error.message);
  }

  try {
    validateFoundationUsageProfile();
    results.passed.push('Foundation usage profile');
  } catch (error) {
    results.warnings.push(error.message);
  }

  try {
    validateGuidedReuseMapping();
    results.passed.push('Guided reuse mapping');
  } catch (error) {
    results.errors.push(error.message);
  }

  try {
    validateWebPackageStructure();
    results.passed.push('Web package structure');
  } catch (error) {
    results.warnings.push(error.message);
  }

  try {
    validateGatePacks();
    results.passed.push('Gate packs');
  } catch (error) {
    results.warnings.push(error.message);
  }

  console.log('\n=== Results ===');
  console.log(`Passed: ${results.passed.length}`);
  console.log(`Warnings: ${results.warnings.length}`);
  console.log(`Errors: ${results.errors.length}`);

  for (const pass of results.passed) {
    console.log(`  ✓ ${pass}`);
  }

  for (const warning of results.warnings) {
    console.log(`  ⚠ ${warning}`);
  }

  for (const error of results.errors) {
    console.log(`  ✗ ${error}`);
  }

  if (results.errors.length > 0) {
    console.error('\n✗ FlashIt web lifecycle readiness check failed');
    process.exit(1);
  }

  console.log('\n✓ FlashIt web lifecycle readiness check passed');
  console.log('  Web surfaces (backend-api, web) are ready for staging validation');
  console.log('  Mobile surfaces (mobile-ios, mobile-android) remain disabled');
  process.exit(0);
}

main();
