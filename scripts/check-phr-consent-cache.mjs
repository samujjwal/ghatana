#!/usr/bin/env node
/**
 * PHR Consent Cache Validation
 * Validates that distributed cache is production-grade (not in-memory)
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/distributed-cache-proof.json');

function validateConsentCache() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Distributed cache evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (evidence.cacheType === 'in-memory') {
    console.error('❌ In-memory cache is not production-grade');
    process.exit(1);
  }

  if (!evidence.multiNodeInvalidation) {
    console.error('❌ Multi-node invalidation not enabled');
    process.exit(1);
  }

  console.log('✅ Consent cache validation passed');
  console.log('   - Cache type:', evidence.cacheType);
  console.log('   - Multi-node invalidation: enabled');
}

validateConsentCache();
