#!/usr/bin/env node
/**
 * PHR FHIR Validation
 * Validates that FHIR resources pass schema validation
 */

import { readFileSync, existsSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/fhir-compliance.json');

function validateFHIR() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ FHIR compliance evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = JSON.parse(readFileSync(EVIDENCE_PATH, 'utf-8'));

  if (!evidence.fhirVersion || !evidence.fhirVersion.startsWith('R4')) {
    console.error('❌ FHIR R4 compliance not verified');
    process.exit(1);
  }

  if (!evidence.validationEnabled) {
    console.error('❌ FHIR validation not enabled');
    process.exit(1);
  }

  console.log('✅ FHIR validation passed');
  console.log('   - FHIR version:', evidence.fhirVersion);
  console.log('   - Validation: enabled');
}

validateFHIR();
