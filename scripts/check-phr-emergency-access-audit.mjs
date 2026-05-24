#!/usr/bin/env node
/**
 * PHR Emergency Access Audit Validation
 * Validates that emergency access has audit trail and review workflow
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/emergency-access.json');

function validateEmergencyAccessAudit() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Emergency access evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.auditTrailEnabled) {
    console.error('❌ Audit trail not enabled for emergency access');
    process.exit(1);
  }

  if (!evidence.reviewWorkflowEnabled) {
    console.error('❌ Post-hoc review workflow not enabled');
    process.exit(1);
  }

  console.log('✅ Emergency access audit validation passed');
  console.log('   - Audit trail: enabled');
  console.log('   - Review workflow: enabled');
}

validateEmergencyAccessAudit();
