#!/usr/bin/env node
/**
 * DMOS Data Cloud RLS Validation
 * Validates that Data Cloud collections have RLS policies enabled
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/data-cloud-collections.json');

function validateDataCloudRLS() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Data Cloud collections evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.collections || !Array.isArray(evidence.collections)) {
    console.error('❌ Collections not found or invalid format');
    process.exit(1);
  }

  for (const collection of evidence.collections) {
    if (!collection.rlsEnabled) {
      console.error('❌ Collection missing RLS:', collection.name);
      process.exit(1);
    }
  }

  console.log('✅ Data Cloud RLS validation passed');
  console.log('   - Collections with RLS:', evidence.collections.length);
}

validateDataCloudRLS();
