/**
 * Check script for kernel plugin registry.
 *
 * Validates the kernel-plugin-registry.json file against its schema.
 */

import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const REGISTRY_PATH = join(__dirname, '..', 'config', 'kernel-plugin-registry.json');
const SCHEMA_PATH = join(__dirname, '..', 'config', 'kernel-plugin-registry-schema.json');

function loadJson(path) {
  try {
    const content = readFileSync(path, 'utf-8');
    return JSON.parse(content);
  } catch (error) {
    console.error(`Error reading ${path}:`, error.message);
    process.exit(1);
  }
}

function validatePluginEntry(id, entry, errors) {
  if (!entry.id || entry.id !== id) {
    errors.push(`Plugin entry key "${id}" does not match entry.id "${entry.id}"`);
  }

  if (!entry.name) {
    errors.push(`Plugin "${id}" is missing required field "name"`);
  }

  if (!entry.kind) {
    errors.push(`Plugin "${id}" is missing required field "kind"`);
  }

  const validKinds = [
    'pre-phase',
    'post-phase',
    'pre-gate',
    'post-gate',
    'pre-deployment',
    'post-deployment',
    'platform-plugin',
    'product-plugin',
  ];

  if (entry.kind && !validKinds.includes(entry.kind)) {
    errors.push(`Plugin "${id}" has invalid kind "${entry.kind}"`);
  }

  // Validate new required fields
  if (!entry.status) {
    errors.push(`Plugin "${id}" is missing required field "status"`);
  }

  const validStatuses = ['declared', 'implemented'];
  if (entry.status && !validStatuses.includes(entry.status)) {
    errors.push(`Plugin "${id}" has invalid status "${entry.status}"`);
  }

  if (typeof entry.safeForDefault !== 'boolean') {
    errors.push(`Plugin "${id}" must have a boolean "safeForDefault" field`);
  }

  // Validate invariants from the plan
  if (entry.safeForDefault === true && entry.status !== 'implemented') {
    errors.push(`Plugin "${id}" has safeForDefault=true but status is "${entry.status}" (must be "implemented")`);
  }

  if (entry.status === 'implemented' && !entry.implementationRef) {
    errors.push(`Plugin "${id}" has status="implemented" but missing implementationRef`);
  }

  if (entry.status === 'implemented' && (!entry.tests || !Array.isArray(entry.tests) || entry.tests.length === 0)) {
    errors.push(`Plugin "${id}" has status="implemented" but tests array is empty or missing`);
  }

  if (!Array.isArray(entry.capabilities)) {
    errors.push(`Plugin "${id}" must have an array "capabilities" field`);
  } else if (entry.capabilities.length === 0) {
    errors.push(`Plugin "${id}" must have non-empty capabilities array`);
  }

  if (!Array.isArray(entry.lifecycleHooks)) {
    errors.push(`Plugin "${id}" must have an array "lifecycleHooks" field`);
  }

  // Validate new required fields
  if (!Array.isArray(entry.healthOutput)) {
    errors.push(`Plugin "${id}" must have an array "healthOutput" field`);
  }

  if (!Array.isArray(entry.gateOutput)) {
    errors.push(`Plugin "${id}" must have an array "gateOutput" field`);
  }

  if (entry.implementationRef === undefined) {
    errors.push(`Plugin "${id}" is missing required field "implementationRef"`);
  }

  if (!Array.isArray(entry.tests)) {
    errors.push(`Plugin "${id}" must have an array "tests" field`);
  }
}

function main() {
  if (!existsSync(REGISTRY_PATH)) {
    console.error(`Plugin registry not found at ${REGISTRY_PATH}`);
    process.exit(1);
  }

  if (!existsSync(SCHEMA_PATH)) {
    console.error(`Plugin registry schema not found at ${SCHEMA_PATH}`);
    process.exit(1);
  }

  const registry = loadJson(REGISTRY_PATH);
  const schema = loadJson(SCHEMA_PATH);

  console.log('Validating kernel plugin registry...');

  const errors = [];

  if (!registry.version) {
    errors.push('Registry is missing required field "version"');
  }

  if (!registry.plugins || typeof registry.plugins !== 'object') {
    errors.push('Registry is missing required field "plugins"');
  } else {
    for (const [id, entry] of Object.entries(registry.plugins)) {
      validatePluginEntry(id, entry, errors);
    }
  }

  if (errors.length > 0) {
    console.error('Plugin registry validation failed:');
    for (const error of errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  console.log('Plugin registry validation passed.');
  console.log(`Found ${Object.keys(registry.plugins).length} plugins.`);
}

main();
