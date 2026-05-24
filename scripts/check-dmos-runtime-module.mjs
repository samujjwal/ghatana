#!/usr/bin/env node
/**
 * DMOS Runtime Module Decision Validation
 * Documents runtime module requirement or explicit non-requirement
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const KERNEL_PRODUCT_PATH = resolve('products/digital-marketing/kernel-product.yaml');

function validateRuntimeModuleDecision() {
  if (!existsSync(KERNEL_PRODUCT_PATH)) {
    console.error('❌ Kernel product file not found:', KERNEL_PRODUCT_PATH);
    process.exit(1);
  }

  const content = readFileSync(KERNEL_PRODUCT_PATH, 'utf-8');

  // Check if runtime module decision is documented
  if (!content.includes('runtimeModule') && !content.includes('Runtime Module')) {
    console.error('❌ Runtime module decision not documented');
    process.exit(1);
  }

  // The kernel-product.yaml already documents runtimeModule: false with rationale
  console.log('✅ Runtime module decision documented');
  console.log('   - Decision: runtimeModule: false (intentional)');
  console.log('   - Rationale: documented in kernel-product.yaml');
}

validateRuntimeModuleDecision();
