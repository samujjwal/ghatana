#!/usr/bin/env node
/**
 * PHR HIPAA Compliance Validation
 * Validates HIPAA compliance requirements
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/hipaa-compliance.json');

function validateHIPAACompliance() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ HIPAA compliance evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.encryptionAtRest) {
    console.error('❌ Encryption at rest not enabled');
    process.exit(1);
  }

  if (!evidence.tlsInTransit) {
    console.error('❌ TLS in transit not enabled');
    process.exit(1);
  }

  if (!evidence.auditLogging) {
    console.error('❌ Audit logging not enabled');
    process.exit(1);
  }

  console.log('✅ HIPAA compliance validation passed');
  console.log('   - Encryption at rest: enabled');
  console.log('   - TLS in transit: enabled');
  console.log('   - Audit logging: enabled');
}

validateHIPAACompliance();
