#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/check-audit-completeness.mjs';
const EVIDENCE_PATH = '.kernel/evidence/audit-completeness.json';
const COMMAND = 'pnpm check:audit-completeness';

const REQUIRED_FILES = [
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/audit/EventLogAuditService.java',
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java',
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java',
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java',
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java',
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/audit/EventLogAuditServiceTest.java',
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/audit/CriticalAuditFailureInjectionTest.java',
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/SensitiveMutationAuditTrailTest.java',
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/EntityEventAuditIdempotencyGoldenTest.java',
  'products/data-cloud/planes/governance/core/src/test/java/com/ghatana/datacloud/governance/audit/GovernanceAuditServiceTest.java',
];

const REQUIRED_TOKENS = [
  {
    file: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java',
    tokens: ['auditService', 'AuditEvent.builder', 'sensitivity', 'success'],
  },
  {
    file: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java',
    tokens: ['AuditService is required', 'AuditEvent.builder', 'auditPayload', 'Entity writes must be atomic with event append and audit emission'],
  },
  {
    file: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java',
    tokens: ['recordCritical', 'RETENTION_PURGE', 'PII_REDACT'],
  },
  {
    file: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandler.java',
    tokens: ['emitConnectorAudit', 'CONNECTOR_CREATED', 'CONNECTOR_UPDATED', 'CONNECTOR_DELETED'],
  },
  {
    file: 'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/audit/EventLogAuditService.java',
    tokens: ['recordCritical', 'failClosed', 'eventLogStore.append'],
  },
];

function currentGitSha(root) {
  try {
    return execFileSync('git', ['rev-parse', 'HEAD'], {
      cwd: root,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return 'unknown';
  }
}

function read(root, relativePath) {
  return readFileSync(path.join(root, relativePath), 'utf8');
}

export function createAuditCompletenessEvidence(root = process.cwd(), now = new Date()) {
  const head = currentGitSha(root);
  const violations = [];
  const coveredFiles = [];

  for (const file of REQUIRED_FILES) {
    if (!existsSync(path.join(root, file))) {
      violations.push(`missing required audit proof file: ${file}`);
    } else {
      coveredFiles.push(file);
    }
  }

  for (const requirement of REQUIRED_TOKENS) {
    const fullPath = path.join(root, requirement.file);
    if (!existsSync(fullPath)) {
      continue;
    }
    const source = read(root, requirement.file);
    for (const token of requirement.tokens) {
      if (!source.includes(token)) {
        violations.push(`${requirement.file}: missing audit completeness token ${JSON.stringify(token)}`);
      }
    }
  }

  return {
    generatedAt: now.toISOString(),
    pass: violations.length === 0,
    evidenceRun: {
      generatedBy: SCRIPT_PATH,
      source: SCRIPT_PATH,
      command: COMMAND,
      commit: head,
    },
    summary: {
      requiredFileCount: REQUIRED_FILES.length,
      coveredFileCount: coveredFiles.length,
      mutationSurfacesChecked: REQUIRED_TOKENS.length,
      violationCount: violations.length,
    },
    mutationSurfaces: REQUIRED_TOKENS.map((requirement) => ({
      file: requirement.file,
      requiredTokens: requirement.tokens,
    })),
    coveredFiles,
    violations,
  };
}

export function writeAuditCompletenessEvidence(root = process.cwd(), evidence = createAuditCompletenessEvidence(root)) {
  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const evidence = createAuditCompletenessEvidence(root);
  writeAuditCompletenessEvidence(root, evidence);

  if (!evidence.pass) {
    console.error('Audit completeness check failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Audit completeness evidence written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
