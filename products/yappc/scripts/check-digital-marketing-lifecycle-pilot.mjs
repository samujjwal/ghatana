#!/usr/bin/env node

/**
 * Digital Marketing Lifecycle Pilot Validator
 *
 * Validates that the Digital Marketing product's kernel-product.yaml is correctly
 * configured for the lifecycle pilot:
 * - File exists at the expected location
 * - schemaVersion is present and valid
 * - All required plugins (including security) are declared
 * - All required manifests are present for each lifecycle phase
 * - allowExperimentalAdapters is set to false (production safety)
 *
 * Exit code 0 = all checks pass
 * Exit code 1 = one or more checks failed
 */

import { readFileSync, existsSync } from 'fs';
import { join } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = join(__filename, '..');

const REPO_ROOT = join(__dirname, '../../..');
const DM_KERNEL_PRODUCT_YAML = join(REPO_ROOT, 'products/digital-marketing/kernel-product.yaml');

let errors = [];
let warnings = [];

function pass(msg) {
  console.log(`  ✓ ${msg}`);
}

function fail(msg) {
  console.error(`  ✗ ${msg}`);
  errors.push(msg);
}

function warn(msg) {
  console.warn(`  ⚠ ${msg}`);
  warnings.push(msg);
}

/**
 * Parse simple YAML values (supports string, boolean, arrays of strings)
 * Only handles the subset of YAML needed for kernel-product.yaml
 */
function parseKernelProductYaml(content) {
  const result = {};
  const lines = content.split('\n');
  let currentKey = null;
  let currentObj = null;
  let inPlugins = false;
  let inRequiredManifests = false;
  let currentSection = null;
  let currentPhase = null;

  const plugins = {};
  const requiredManifests = {};
  let productId = null;
  let schemaVersion = null;
  let lifecycleProfile = null;
  let allowExperimentalAdapters = null;

  for (const line of lines) {
    const trimmed = line.trim();

    if (trimmed === '' || trimmed.startsWith('#')) continue;

    const indent = line.length - line.trimStart().length;

    if (indent === 0) {
      inPlugins = false;
      inRequiredManifests = false;
      currentSection = null;
      currentPhase = null;

      if (trimmed.startsWith('schemaVersion:')) {
        schemaVersion = trimmed.split(':')[1].trim();
      } else if (trimmed.startsWith('productId:')) {
        productId = trimmed.split(':')[1].trim();
      } else if (trimmed.startsWith('lifecycleProfile:')) {
        lifecycleProfile = trimmed.split(':')[1].trim();
      } else if (trimmed.startsWith('allowExperimentalAdapters:')) {
        allowExperimentalAdapters = trimmed.split(':')[1].trim() === 'true';
      } else if (trimmed === 'plugins:') {
        inPlugins = true;
      } else if (trimmed === 'requiredManifests:') {
        inRequiredManifests = true;
      }
    } else if (inPlugins && indent === 2) {
      const key = trimmed.replace(':', '').trim();
      plugins[key] = {};
      currentSection = key;
    } else if (inPlugins && indent === 4 && currentSection) {
      const [k, v] = trimmed.split(':').map(s => s.trim());
      plugins[currentSection][k] = v === 'true' ? true : v === 'false' ? false : v;
    } else if (inRequiredManifests && indent === 2) {
      const phase = trimmed.replace(':', '').trim();
      requiredManifests[phase] = [];
      currentPhase = phase;
    } else if (inRequiredManifests && indent === 4 && currentPhase) {
      if (trimmed.startsWith('- ')) {
        requiredManifests[currentPhase].push(trimmed.substring(2).trim());
      }
    }
  }

  return { schemaVersion, productId, lifecycleProfile, allowExperimentalAdapters, plugins, requiredManifests };
}

// === Checks ===

console.log('\nDigital Marketing Lifecycle Pilot Validator');
console.log('===========================================\n');

// Check 1: File exists
console.log('1. File Existence');
if (!existsSync(DM_KERNEL_PRODUCT_YAML)) {
  fail(`kernel-product.yaml not found at: ${DM_KERNEL_PRODUCT_YAML}`);
  console.error('\nFatal: cannot continue without the file.\n');
  process.exit(1);
} else {
  pass(`kernel-product.yaml found at products/digital-marketing/kernel-product.yaml`);
}

const content = readFileSync(DM_KERNEL_PRODUCT_YAML, 'utf-8');
const config = parseKernelProductYaml(content);

// Check 2: Schema version
console.log('\n2. Schema Version');
if (!config.schemaVersion) {
  fail('schemaVersion is missing');
} else if (!/^\d+\.\d+\.\d+$/.test(config.schemaVersion)) {
  fail(`schemaVersion must be semver, got: ${config.schemaVersion}`);
} else {
  pass(`schemaVersion = ${config.schemaVersion}`);
}

// Check 3: Product ID
console.log('\n3. Product Identity');
if (!config.productId) {
  fail('productId is missing');
} else if (config.productId !== 'digital-marketing') {
  fail(`productId must be "digital-marketing", got: ${config.productId}`);
} else {
  pass(`productId = ${config.productId}`);
}

if (!config.lifecycleProfile) {
  fail('lifecycleProfile is missing');
} else {
  pass(`lifecycleProfile = ${config.lifecycleProfile}`);
}

// Check 4: Safety flag
console.log('\n4. Safety Flags');
if (config.allowExperimentalAdapters === true) {
  fail('allowExperimentalAdapters must be false for production pilot');
} else {
  pass('allowExperimentalAdapters = false');
}

// Check 5: Required plugins
console.log('\n5. Required Plugins');
const REQUIRED_PLUGINS = ['audit', 'observability', 'data-access', 'identity-entitlement', 'security'];

for (const plugin of REQUIRED_PLUGINS) {
  if (!config.plugins[plugin]) {
    fail(`Required plugin missing: ${plugin}`);
  } else if (config.plugins[plugin].required !== true) {
    fail(`Plugin "${plugin}" must be declared as required: true`);
  } else {
    pass(`Plugin "${plugin}" is declared required`);
  }
}

// Optional plugins that may be present
if (config.plugins['preview-security']) {
  pass('Optional plugin "preview-security" is declared');
}

// Check 6: Required manifests per phase
console.log('\n6. Required Manifests');
const EXPECTED_MANIFESTS = {
  build: ['lifecycle-result', 'artifact-manifest', 'lifecycle-health-snapshot'],
  package: ['artifact-manifest', 'lifecycle-health-snapshot'],
  deploy: ['deployment-manifest', 'lifecycle-health-snapshot'],
  verify: ['verify-health-report', 'lifecycle-health-snapshot'],
};

for (const [phase, expectedManifests] of Object.entries(EXPECTED_MANIFESTS)) {
  const actualManifests = config.requiredManifests[phase] ?? [];
  for (const manifest of expectedManifests) {
    if (!actualManifests.includes(manifest)) {
      fail(`Phase "${phase}" is missing required manifest: ${manifest}`);
    } else {
      pass(`Phase "${phase}" has manifest: ${manifest}`);
    }
  }
}

// === Summary ===
console.log('\n===========================================');
if (errors.length === 0) {
  console.log(`✅ All checks passed. Digital Marketing pilot is correctly configured.`);
  if (warnings.length > 0) {
    console.log(`   ${warnings.length} warning(s) — review above.`);
  }
  process.exit(0);
} else {
  console.error(`\n❌ ${errors.length} check(s) failed:`);
  for (const err of errors) {
    console.error(`   - ${err}`);
  }
  process.exit(1);
}
