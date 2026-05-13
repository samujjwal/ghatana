#!/usr/bin/env node

import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SchemaValidator } from '../platform/typescript/kernel-lifecycle/dist/index.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..');

async function main() {
  const validator = new SchemaValidator(repoRoot);

  try {
    const result = await validator.validateProductLifecycleProfilesWithArtifacts();

    if (result.errors.length > 0) {
      console.error('Product Lifecycle Profiles Validation Errors:');
      for (const error of result.errors) {
        console.error(`  ${error.path}: ${error.message}`);
      }
    }

    if (result.warnings.length > 0) {
      console.warn('Product Lifecycle Profiles Validation Warnings:');
      for (const warning of result.warnings) {
        console.warn(`  ${warning.path}: ${warning.message}`);
      }
    }

    if (!result.valid) {
      process.exit(1);
    }

    console.log('Product lifecycle profiles schema is valid');
  } catch (error) {
    console.error(
      `Schema validation failed: ${error instanceof Error ? error.message : String(error)}`
    );
    process.exit(1);
  }
}

try {
  await main();
} catch (error) {
  console.error(
    `Fatal error: ${error instanceof Error ? error.message : String(error)}`
  );
  process.exit(1);
}
