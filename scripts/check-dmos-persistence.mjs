#!/usr/bin/env node
/**
 * DMOS Persistence Validation
 * Validates persistence enforces tenant isolation and data integrity
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/database-module-evidence.json');

function validatePersistence() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Database module evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.tenantIsolation) {
    console.error('❌ Tenant isolation not enforced');
    process.exit(1);
  }

  if (!evidence.dataIntegrityConstraints) {
    console.error('❌ Data integrity constraints not enabled');
    process.exit(1);
  }

  console.log('✅ Persistence validation passed');
  console.log('   - Tenant isolation: enforced');
  console.log('   - Data integrity: enabled');
}

validatePersistence();
