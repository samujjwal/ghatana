#!/usr/bin/env node
/**
 * DMOS Persistence Validation
 * Validates persistence enforces tenant isolation and data integrity
 */

import { readFileSync, existsSync, writeFileSync } from 'fs';
import { execFileSync, spawnSync } from 'child_process';
import { resolve } from 'path';

const EVIDENCE_PATH = resolve('.kernel/evidence/digital-marketing/database-module-evidence.json');
const RELEASE_READINESS_PATH = resolve('.kernel/evidence/digital-marketing/dmos-release-readiness.json');
const RUNTIME_PROOF_COMMAND = [
  './gradlew',
  ':products:digital-marketing:dm-persistence:test',
  '--rerun-tasks',
  '--tests',
  'com.ghatana.digitalmarketing.persistence.PostgresCampaignRepositoryIT',
];

function readJson(path) {
  return JSON.parse(readFileSync(path, 'utf-8'));
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
}

function currentTargetCommit(existing) {
  return (
    process.env.TARGET_COMMIT_SHA
    ?? process.env.AUDIT_TARGET_COMMIT
    ?? existing?.targetCommitSha
    ?? existing?.evidenceRun?.targetCommitSha
    ?? execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf-8' }).trim()
  );
}

function runRuntimeProof() {
  console.log('Running DMOS Postgres persistence proof...');
  const result = spawnSync(RUNTIME_PROOF_COMMAND[0], RUNTIME_PROOF_COMMAND.slice(1), {
    cwd: resolve('.'),
    stdio: 'inherit',
    env: process.env,
  });

  if (result.status !== 0) {
    console.error('❌ DMOS Postgres persistence proof failed');
    process.exit(result.status ?? 1);
  }
}

function updateReleaseReadiness(targetCommit, now) {
  if (!existsSync(RELEASE_READINESS_PATH)) {
    return;
  }

  const readiness = readJson(RELEASE_READINESS_PATH);
  const persistence = readiness.evidenceCategories?.persistence;
  if (persistence) {
    persistence.status = 'passed';
    persistence.lastChecked = now;
    persistence.evidenceRefs = [
      ...new Set([
        ...(persistence.evidenceRefs ?? []),
        '.kernel/evidence/digital-marketing/database-module-evidence.json',
        'products/digital-marketing/dm-persistence/src/test/java/com/ghatana/digitalmarketing/persistence/PostgresCampaignRepositoryIT.java',
      ]),
    ];
    persistence.data = {
      ...(persistence.data ?? {}),
      tenantFilters: 'validated',
      fkConstraints: 'validated',
      uniqueConstraints: 'validated',
      idempotencyKeys: 'validated',
      timestamps: 'validated',
      nullBudgetMapping: 'validated',
    };
  }

  if (readiness.gates?.['persistence-proof']) {
    readiness.gates['persistence-proof'].status = 'passed';
  }

  readiness.releaseReadiness = {
    ...(readiness.releaseReadiness ?? {}),
    warnings: (readiness.releaseReadiness?.warnings ?? []).filter(
      (warning) => !/persistence|constraint/i.test(warning),
    ),
  };
  readiness.nextRequiredWork = (readiness.nextRequiredWork ?? []).filter(
    (item) => !/FK|unique|persistence/i.test(item),
  );
  readiness.evidenceRun = {
    ...(readiness.evidenceRun ?? {}),
    commit: targetCommit,
    sourceCommitSha: targetCommit,
    targetCommitSha: targetCommit,
  };
  readiness.sourceCommitSha = targetCommit;
  readiness.targetCommitSha = targetCommit;
  readiness.checkedAt = now;
  readiness.generatedAt = now;
  writeJson(RELEASE_READINESS_PATH, readiness);
}

function validatePersistence() {
  if (!existsSync(EVIDENCE_PATH)) {
    console.error('❌ Database module evidence not found:', EVIDENCE_PATH);
    process.exit(1);
  }

  const evidence = readJson(EVIDENCE_PATH);
  const targetCommit = currentTargetCommit(readJson(RELEASE_READINESS_PATH));
  const now = new Date().toISOString();

  if (!evidence.tenantIsolation) {
    console.error('❌ Tenant isolation not enforced');
    process.exit(1);
  }

  if (!evidence.dataIntegrityConstraints) {
    console.error('❌ Data integrity constraints not enabled');
    process.exit(1);
  }

  if (!evidence.constraints?.foreignKeys || !evidence.constraints?.uniqueConstraints) {
    console.error('❌ FK/unique constraint evidence is incomplete');
    process.exit(1);
  }

  runRuntimeProof();

  evidence.commitSha = targetCommit;
  evidence.generatedAt = now;
  evidence.sourceCommitSha = targetCommit;
  evidence.targetCommitSha = targetCommit;
  evidence.executableProof = {
    validatedAt: now,
    command: RUNTIME_PROOF_COMMAND.join(' '),
    tests: [
      'products/digital-marketing/dm-persistence/src/test/java/com/ghatana/digitalmarketing/persistence/PostgresCampaignRepositoryIT.java',
    ],
    passed: true,
  };
  evidence.evidenceRefs = [
    ...new Set([
      ...(evidence.evidenceRefs ?? []),
      'products/digital-marketing/dm-persistence/src/test/java/com/ghatana/digitalmarketing/persistence/PostgresCampaignRepositoryIT.java',
    ]),
  ];
  writeJson(EVIDENCE_PATH, evidence);
  updateReleaseReadiness(targetCommit, now);

  console.log('✅ Persistence validation passed');
  console.log('   - Tenant isolation: enforced');
  console.log('   - Data integrity: enabled');
  console.log('   - Runtime proof:', RUNTIME_PROOF_COMMAND.join(' '));
}

validatePersistence();
