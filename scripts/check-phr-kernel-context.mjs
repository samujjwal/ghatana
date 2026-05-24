#!/usr/bin/env node
/**
 * PHR Kernel Context Validation
 * Validates that Kernel context provides DistributedCachePort for PHR
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/kernel-context-wiring.json');

function validateKernelContext() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Kernel context evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.distributedCachePort) {
    console.error('❌ DistributedCachePort not provided in kernel context');
    process.exit(1);
  }

  if (evidence.distributedCachePort.status !== 'ready') {
    console.error('❌ DistributedCachePort status is not ready:', evidence.distributedCachePort.status);
    process.exit(1);
  }

  console.log('✅ Kernel context validation passed');
  console.log('   - DistributedCachePort: ready');
  console.log('   - Provider:', evidence.distributedCachePort.provider);
}

validateKernelContext();
