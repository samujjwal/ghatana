#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const packageJsonPath = path.join(repoRoot, 'package.json');
const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));

const KERNEL_BACKED_PRODUCTS = new Set(['phr', 'digital-marketing']);
const NON_KERNEL_PRODUCTS = new Set(['finance', 'flashit', 'data-cloud', 'yappc', 'audio-video', 'dcmaar', 'tutorputor', 'security-gateway']);

const errors = [];

for (const [scriptName, scriptCommand] of Object.entries(packageJson.scripts)) {
  if (typeof scriptCommand !== 'string') continue;
  
  // Check for direct calls to kernel-product.mjs in package.json
  if (scriptCommand.includes('scripts/kernel-product.mjs product')) {
    // Check if this is a kernel-backed product
    const productId = KERNEL_BACKED_PRODUCTS.has(scriptName.split(':')[1]) ? scriptName.split(':')[1] : null;
    if (productId && KERNEL_BACKED_PRODUCTS.has(productId)) {
      errors.push(
        `Script "${scriptName}" uses "scripts/kernel-product.mjs product ${productId}" instead of "pnpm kernel ${productId}". ` +
        `Kernel-backed products must use the "pnpm kernel <productId> <phase>" pattern.`
      );
    }
  }
  
  // Check that kernel-backed products use pnpm kernel pattern
  for (const productId of KERNEL_BACKED_PRODUCTS) {
    if (scriptName.includes(productId) && scriptCommand.includes('pnpm product')) {
      errors.push(
        `Script "${scriptName}" uses "pnpm product ${productId}" instead of "pnpm kernel ${productId}". ` +
        `Kernel-backed products must use the "pnpm kernel <productId> <phase>" pattern.`
      );
    }
  }
}

if (errors.length > 0) {
  console.error('Kernel CLI mental model guard failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  console.error('\nSee docs/KERNEL_CLI_MENTAL_MODEL.md for the documented pattern.');
  process.exit(1);
}

console.log('Kernel CLI mental model guard passed.');
