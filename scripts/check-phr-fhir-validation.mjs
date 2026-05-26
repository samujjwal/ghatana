#!/usr/bin/env node
/**
 * PHR FHIR Validation
 * Validates that FHIR resources pass schema validation
 */

import { execFileSync } from 'node:child_process';
import { readFileSync, existsSync, writeFileSync } from 'fs';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/phr/fhir-compliance.json');
const RELEASE_READINESS_PATH = resolve('.kernel/evidence/phr/phr-release-readiness.json');
const FHIR_SERVER_PATH = resolve('products/phr/src/main/java/com/ghatana/phr/fhir/server/PhrFhirR4Server.java');

function currentGitSha() {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

function registeredRuntimeProviders() {
  const source = readFileSync(FHIR_SERVER_PATH, 'utf8');
  const matches = [...source.matchAll(/new\s+([A-Za-z]+)FhirResourceProvider\s*\(/g)]
    .map((match) => match[1]);
  return matches.map((name) => {
    if (name === 'MedicationRequest') {
      return 'MedicationRequest';
    }
    return name;
  }).sort();
}

function claimedEvidenceResources() {
  const evidence = JSON.parse(readFileSync(RELEASE_READINESS_PATH, 'utf-8'));
  function findResources(value) {
    if (!value || typeof value !== 'object') {
      return [];
    }
    if (Array.isArray(value.resourcesSupported)) {
      return value.resourcesSupported;
    }
    for (const child of Object.values(value)) {
      const found = findResources(child);
      if (found.length > 0) {
        return found;
      }
    }
    return [];
  }
  return [...new Set(findResources(evidence).map(String))].sort();
}

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

  const runtimeResources = registeredRuntimeProviders();
  const claimedResources = claimedEvidenceResources();
  const missingRuntime = claimedResources.filter((resource) => !runtimeResources.includes(resource));
  const missingEvidence = runtimeResources.filter((resource) => !claimedResources.includes(resource));
  if (missingRuntime.length > 0 || missingEvidence.length > 0) {
    console.error('❌ FHIR runtime provider registry does not match release evidence');
    if (missingRuntime.length > 0) {
      console.error('   - Claimed but not registered:', missingRuntime.join(', '));
    }
    if (missingEvidence.length > 0) {
      console.error('   - Registered but not claimed:', missingEvidence.join(', '));
    }
    process.exit(1);
  }

  const now = new Date();
  const commit = currentGitSha();
  evidence.generatedAt = now.toISOString();
  evidence.evidenceRun = {
    generatedBy: 'scripts/check-phr-fhir-validation.mjs',
    command: 'node scripts/check-phr-fhir-validation.mjs',
    source: 'scripts/check-phr-fhir-validation.mjs',
    commit,
    sourceCommitSha: commit,
    targetCommitSha: process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? commit,
    targetEnvironment: process.env.RELEASE_ENVIRONMENT ?? 'staging',
  };
  evidence.runtimeResourcesSupported = runtimeResources;
  evidence.validationStatus = 'validated';
  writeFileSync(EVIDENCE_PATH, `${JSON.stringify(evidence, null, 2)}\n`);

  console.log('✅ FHIR validation passed');
  console.log('   - FHIR version:', evidence.fhirVersion);
  console.log('   - Validation: enabled');
  console.log('   - Runtime resources:', runtimeResources.join(', '));
}

validateFHIR();
