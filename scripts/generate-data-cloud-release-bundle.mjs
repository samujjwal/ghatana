#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const SCRIPT_PATH = 'scripts/generate-data-cloud-release-bundle.mjs';
const EVIDENCE_PATH = '.kernel/evidence/data-cloud-release-bundle.json';
const COMMAND = 'pnpm generate:data-cloud-release-bundle';

const BUNDLE_ITEMS = [
  ['activeModuleEvidence', '.kernel/evidence/data-cloud-active-modules.json'],
  ['actionPlaneBoundaryEvidence', '.kernel/evidence/action-plane-boundaries.json'],
  ['actionPlaneInventoryEvidence', '.kernel/evidence/action-plane-module-inventory.json'],
  ['productionReadinessTaskMapEvidence', '.kernel/evidence/production-readiness-task-map.json'],
  ['agentCapabilityDuplicateEvidence', '.kernel/evidence/agent-capability-duplicates.json'],
  ['agentRuntimeTestExcludeEvidence', '.kernel/evidence/agent-runtime-test-excludes.json'],
  ['agentUsageAuditEvidence', '.kernel/evidence/agent-usage-audit.json'],
  ['auditCompletenessProof', '.kernel/evidence/audit-completeness.json'],
  ['operationsReadinessProof', '.kernel/evidence/data-cloud-operations-readiness.json'],
  ['productReleaseReadiness', '.kernel/evidence/product-release-readiness.json'],
  ['aiGovernanceProof', '.kernel/evidence/ai-governance-behavioral-proof/ai-governance-behavioral-proof-latest.json'],
  ['openApiDriftProof', '.kernel/evidence/openapi-breaking-changes.json'],
  ['routeManifestProof', '.kernel/evidence/action-plane-route-lifecycle.json'],
  ['sdkGenerationProof', '.kernel/evidence/data-cloud-release-runtime-profile.json'],
  ['tenantIsolationProof', '.kernel/evidence/data-cloud/tenant-isolation-governance-validation.json'],
  ['securitySbomProof', '.kernel/evidence/data-cloud-security-sbom-proof.json'],
  ['smokeE2eProof', 'release-evidence/smoke/smoke-e2e-report.json'],
  ['backupRestoreProof', 'release-evidence/backup/backup-drill-report.json'],
  ['a11yProof', '.kernel/evidence/a11y-behavioral-proof/a11y-behavioral-proof-latest.json'],
  ['i18nProof', '.kernel/evidence/i18n-behavioral-proof/i18n-behavioral-proof-latest.json'],
  ['sloProof', '.kernel/evidence/product-slo-budgets.json'],
  ['costProof', '.kernel/evidence/product-cost-budgets.json'],
  ['domainInvariantProof', '.kernel/evidence/product-domain-invariants.json'],
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

function readJson(root, relativePath) {
  const fullPath = path.join(root, relativePath);
  if (!existsSync(fullPath)) {
    return { present: false, path: relativePath };
  }
  const stats = statSync(fullPath);
  if (stats.isDirectory()) {
    const jsonFile = firstJsonFile(fullPath);
    if (!jsonFile) {
      return { present: false, path: relativePath };
    }
    return readJson(root, path.relative(root, jsonFile).replaceAll(path.sep, '/'));
  }
  try {
    return {
      present: true,
      path: relativePath,
      payload: JSON.parse(readFileSync(fullPath, 'utf8')),
    };
  } catch (error) {
    return {
      present: true,
      path: relativePath,
      parseError: error.message,
    };
  }
}

function firstJsonFile(directory) {
  for (const entry of readdirSync(directory)) {
    const child = path.join(directory, entry);
    const stats = statSync(child);
    if (stats.isDirectory()) {
      const nested = firstJsonFile(child);
      if (nested) {
        return nested;
      }
    } else if (entry.endsWith('.json')) {
      return child;
    }
  }
  return null;
}

export function createDataCloudReleaseBundle(root = process.cwd(), now = new Date()) {
  const head = currentGitSha(root);
  const targetCommitSha = process.env.TARGET_COMMIT_SHA ?? process.env.AUDIT_TARGET_COMMIT ?? head;
  const targetEnvironment = process.env.RELEASE_ENVIRONMENT ?? 'staging';
  const items = Object.fromEntries(BUNDLE_ITEMS.map(([key, relativePath]) => [key, readJson(root, relativePath)]));
  const violations = [];

  for (const [key, item] of Object.entries(items)) {
    if (!item.present) {
      violations.push(`${key}: missing ${item.path}`);
      continue;
    }
    if (item.parseError) {
      violations.push(`${key}: invalid JSON (${item.parseError})`);
      continue;
    }
    const commit = item.payload?.evidenceRun?.commit;
    if (commit !== undefined && commit !== head) {
      violations.push(`${key}: evidenceRun.commit ${commit} must match HEAD ${head}`);
    }
    const sourceCommitSha = item.payload?.evidenceRun?.sourceCommitSha ?? item.payload?.sourceCommitSha;
    if (sourceCommitSha !== undefined && sourceCommitSha !== head) {
      violations.push(`${key}: sourceCommitSha ${sourceCommitSha} must match HEAD ${head}`);
    }
    const itemTargetCommitSha = item.payload?.evidenceRun?.targetCommitSha ?? item.payload?.targetCommitSha;
    if (itemTargetCommitSha !== undefined && itemTargetCommitSha !== targetCommitSha) {
      violations.push(`${key}: targetCommitSha ${itemTargetCommitSha} must match release target ${targetCommitSha}`);
    }
    if (item.payload?.pass === false) {
      violations.push(`${key}: pass is false`);
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
      sourceCommitSha: head,
      targetCommitSha,
      targetEnvironment,
    },
    sourceCommitSha: head,
    targetCommitSha,
    targetEnvironment,
    validationStatus: violations.length === 0 ? 'validated' : 'failed',
    reviewDueAt: new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString(),
    expiresAt: new Date(now.getTime() + 48 * 60 * 60 * 1000).toISOString(),
    summary: {
      itemCount: BUNDLE_ITEMS.length,
      presentItems: Object.values(items).filter((item) => item.present).length,
      violationCount: violations.length,
    },
    items,
    violations,
  };
}

export function writeDataCloudReleaseBundle(root = process.cwd(), bundle = createDataCloudReleaseBundle(root)) {
  // DC-REL-003: Stop committing generated release evidence unless commit-bound
  if (!bundle.pass) {
    throw new Error(`Evidence validation failed with ${bundle.violations.length} violations. Evidence not written.`);
  }

  // Ensure evidence is commit-bound
  const head = bundle.evidenceRun.commit;
  if (!head || head === 'unknown' || !/^[a-f0-9]{40}$/i.test(head)) {
    throw new Error(`Evidence is not commit-bound: commit=${head}. Evidence not written.`);
  }

  const evidencePath = path.join(root, EVIDENCE_PATH);
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(bundle, null, 2)}\n`);
  return evidencePath;
}

function main() {
  const rootArgIndex = process.argv.indexOf('--root');
  const root = rootArgIndex >= 0 ? path.resolve(process.argv[rootArgIndex + 1]) : process.cwd();
  const bundle = createDataCloudReleaseBundle(root);
  writeDataCloudReleaseBundle(root, bundle);

  if (!bundle.pass) {
    console.error('Data Cloud release bundle generation failed:\n');
    for (const violation of bundle.violations) {
      console.error(`- ${violation}`);
    }
    process.exit(1);
  }

  console.log(`Data Cloud release bundle written to ${EVIDENCE_PATH}.`);
}

if (process.argv[1] && import.meta.url === new URL(`file://${path.resolve(process.argv[1])}`).href) {
  main();
}
