#!/usr/bin/env node
/**
 * DMOS Kernel Context Validation
 * Validates that Kernel context provides required configuration
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/kernel-context-wiring.json');

function validateKernelContext() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Kernel context evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.featureFlagResolution) {
    console.error('❌ Feature flag resolution not provided in kernel context');
    process.exit(1);
  }

  if (evidence.featureFlagResolution.status !== 'ready') {
    console.error('❌ Feature flag resolution status is not ready:', evidence.featureFlagResolution.status);
    process.exit(1);
  }

  console.log('✅ Kernel context validation passed');
  console.log('   - Feature flag resolution: ready');
  console.log('   - Provider:', evidence.featureFlagResolution.provider);
}

validateKernelContext();
