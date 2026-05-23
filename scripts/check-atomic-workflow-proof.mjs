#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const registryPath = path.join(
  repoRoot,
  'products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java',
);
const invariantTestPath = path.join(
  repoRoot,
  'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistryInvariantTest.java',
);
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'atomic-workflow-posture.json');
const failureEvidenceDir = path.join(repoRoot, '.kernel/evidence/atomic-workflow-failure-injection');
const failureReportPrefix = 'atomic-workflow-failure-injection-';

const routePattern = /route\(map,\s*"([A-Z]+)",\s*"([^"]+)",\s*EndpointSensitivity\.([A-Z_]+),\s*(true|false),\s*(true|false),\s*(true|false),\s*(true|false),/g;
const mutatingMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const requiredAtomicScenarioEvidence = [
  { key: 'businessWriteEventAppendFailure', patterns: ['business write/event append failure'] },
  { key: 'eventAppendAuditWriteFailure', patterns: ['event append/audit write failure'] },
  { key: 'auditOutboxFailure', patterns: ['audit/outbox failure'] },
  { key: 'idempotencyWriteFailure', patterns: ['idempotency write failure'] },
  { key: 'retryAfterPartialFailure', patterns: ['retry after partial failure'] },
  { key: 'rollbackAfterPartialFailure', patterns: ['rollback after partial failure'] },
  { key: 'replayAfterCrash', patterns: ['replay after crash'] },
];

const violations = [];

function findLatestFailureReport() {
  if (!existsSync(failureEvidenceDir)) {
    return null;
  }

  const candidates = readdirSync(failureEvidenceDir)
    .filter((entry) => entry.startsWith(failureReportPrefix) && entry.endsWith('.json'))
    .map((entry) => ({
      entry,
      absolutePath: path.join(failureEvidenceDir, entry),
      modifiedAt: statSync(path.join(failureEvidenceDir, entry)).mtimeMs,
    }))
    .sort((left, right) => right.modifiedAt - left.modifiedAt);

  return candidates[0] ?? null;
}

function validateFailureInjectionReport() {
  const latest = findLatestFailureReport();
  if (!latest) {
    violations.push('Missing atomic workflow failure-injection evidence report in .kernel/evidence/atomic-workflow-failure-injection');
    return {
      report: null,
      source: null,
    };
  }

  let report;
  try {
    report = JSON.parse(readFileSync(latest.absolutePath, 'utf8'));
  } catch (error) {
    violations.push(`Invalid atomic workflow failure-injection report JSON: ${latest.entry} (${error.message})`);
    return {
      report: null,
      source: latest.entry,
    };
  }

  const ageHours = (Date.now() - latest.modifiedAt) / (1000 * 60 * 60);
  if (ageHours > 168) {
    violations.push(`Atomic workflow failure-injection report is stale (> 168h): ${latest.entry}`);
  }

  if (!Array.isArray(report.violations)) {
    violations.push('Atomic workflow failure-injection report missing violations array');
  } else if (report.violations.length > 0) {
    violations.push(`Atomic workflow failure-injection report has ${report.violations.length} violation(s)`);
  }

  if (!Array.isArray(report.evidence)) {
    violations.push('Atomic workflow failure-injection report missing evidence array');
  } else {
    const evidenceText = report.evidence.join(' | ').toLowerCase();
    for (const scenario of requiredAtomicScenarioEvidence) {
      const satisfied = scenario.patterns.every((pattern) => evidenceText.includes(pattern));
      if (!satisfied) {
        violations.push(`Atomic workflow failure-injection report missing scenario evidence for ${scenario.key}`);
      }
    }
  }

  return {
    report,
    source: latest.entry,
  };
}

function readRequiredFile(filePath) {
  try {
    return readFileSync(filePath, 'utf8');
  } catch (error) {
    violations.push(`Missing required file: ${path.relative(repoRoot, filePath)}`);
    return '';
  }
}

const registrySource = readRequiredFile(registryPath);
const invariantSource = readRequiredFile(invariantTestPath);

const mutatingRoutes = [];
const criticalMutatingRoutes = [];

for (const match of registrySource.matchAll(routePattern)) {
  const method = match[1];
  const routePath = match[2];
  const sensitivity = match[3];
  const requiresPolicy = match[6] === 'true';
  const requiresBlockingAudit = match[7] === 'true';

  if (!mutatingMethods.has(method)) {
    continue;
  }

  const route = {
    method,
    path: routePath,
    sensitivity,
    requiresPolicy,
    requiresBlockingAudit,
  };

  mutatingRoutes.push(route);

  if (sensitivity === 'CRITICAL') {
    criticalMutatingRoutes.push(route);
    if (!requiresPolicy || !requiresBlockingAudit) {
      violations.push(
        `Critical route ${method} ${routePath} must require policy and blocking audit`,
      );
    }
  }
}

if (mutatingRoutes.length === 0) {
  violations.push('No mutating routes were parsed from RouteSecurityRegistry');
}
if (criticalMutatingRoutes.length === 0) {
  violations.push('No critical mutating routes were parsed from RouteSecurityRegistry');
}

for (const requiredToken of [
  'criticalRoutesRequirePolicyAndBlockingAudit',
  'registryOperationSetExactlyMatchesRouterOperationSet',
  'registryChecksumMatchesRouterRoutes',
]) {
  if (!invariantSource.includes(requiredToken)) {
    violations.push(`RouteSecurityRegistry invariant coverage missing token: ${requiredToken}`);
  }
}

const hasRollbackRoute = mutatingRoutes.some((route) => route.path.includes('/rollback'));
const hasRetryRoute = mutatingRoutes.some((route) => route.path.includes('/retry'));
if (!hasRollbackRoute) {
  violations.push('Expected at least one rollback mutation route in RouteSecurityRegistry');
}
if (!hasRetryRoute) {
  violations.push('Expected at least one retry mutation route in RouteSecurityRegistry');
}

const evidence = {
  generatedAt: 'generated-on-demand',
  summary: {
    mutatingRouteCount: mutatingRoutes.length,
    criticalMutatingRouteCount: criticalMutatingRoutes.length,
    hasRollbackRoute,
    hasRetryRoute,
    violationCount: violations.length,
  },
  criticalMutatingRoutes: criticalMutatingRoutes.map((route) => ({
    operation: `${route.method} ${route.path}`,
    requiresPolicy: route.requiresPolicy,
    requiresBlockingAudit: route.requiresBlockingAudit,
  })),
  failureInjectionEvidence: (() => {
    const validated = validateFailureInjectionReport();
    return {
      reportSource: validated.source,
      hasReport: validated.report !== null,
      violationCount: Array.isArray(validated.report?.violations) ? validated.report.violations.length : null,
      warningCount: Array.isArray(validated.report?.warnings) ? validated.report.warnings.length : null,
    };
  })(),
};

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');

if (violations.length > 0) {
  console.error('Atomic workflow proof check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
  process.exit(1);
}

console.log(`Atomic workflow proof check passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
