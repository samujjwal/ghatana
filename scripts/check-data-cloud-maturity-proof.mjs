#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '..');
const matrixPath = path.join(repoRoot, 'config/data-cloud-maturity-proof-matrix.json');
const evidencePath = path.join(repoRoot, '.kernel/evidence/data-cloud-maturity-proof.json');

const sourceSets = {
  atomicWorkflowFamilies: [
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/routefamily/RouteFamilyAtomicWorkflowTest.java',
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/resilience/AtomicWorkflowFailureInjectionExecutableTest.java',
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleFailureInjectionTest.java',
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/handlers/DataSourceRegistryHandlerTest.java',
    'products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/ApiResponseTest.java',
    'products/data-cloud/contracts/openapi/data-cloud.yaml',
    'products/data-cloud/contracts/openapi/action-plane.yaml',
  ],
  agentRuntimeProof: [
    'products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/AgentExecutionService.java',
    'products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/engine/registry/AgentExecutionServiceTest.java',
    'products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/di/AepDiModulesTest.java',
    'products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/audit/AgentLifecycleActionTraceRecorderTest.java',
    'products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/audit/TraceEventTypeMasteryTest.java',
    'products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/learning/delta/DataCloudLearningDeltaRepositoryTest.java',
    'products/data-cloud/extensions/agent-registry/src/test/java/com/ghatana/datacloud/agent/mastery/DataCloudMasteryRegistryTest.java',
  ],
  aiGovernanceProof: [
    'products/data-cloud/planes/action/orchestrator/src/main/java/com/ghatana/aep/engine/registry/AgentExecutionService.java',
    'products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/engine/registry/AgentExecutionServiceTest.java',
    'products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/audit/AgentLifecycleActionTraceRecorderTest.java',
  ],
  uiRouteProof: [
    'products/data-cloud/delivery/ui/e2e/a11y.spec.ts',
    'products/data-cloud/delivery/ui/src/i18n/__tests__/config.test.ts',
    'products/data-cloud/delivery/ui/src/i18n/config.ts',
    'products/data-cloud/delivery/ui/src/components/cost/CostExplorer.tsx',
    'products/data-cloud/delivery/ui/src/components/plugins/PluginPerformanceMetrics.tsx',
  ],
  actionPlaneLifecycleProof: [
    'products/data-cloud/contracts/openapi/action-plane.yaml',
    'products/data-cloud/contracts/openapi/aep.yaml',
    'products/data-cloud/contracts/openapi/route-compatibility-registry.yaml',
  ],
};

const productionSourceRoots = [
  'products/data-cloud/planes/action',
  'products/data-cloud/delivery',
  'products/data-cloud/extensions',
];

function readJson(filePath) {
  return JSON.parse(readFileSync(filePath, 'utf8'));
}

function readExistingFiles(relativePaths) {
  const missing = [];
  const chunks = [];
  for (const relativePath of relativePaths) {
    const absolutePath = path.join(repoRoot, relativePath);
    if (!existsSync(absolutePath)) {
      missing.push(relativePath);
      continue;
    }
    chunks.push(readFileSync(absolutePath, 'utf8'));
  }
  return {
    missing,
    text: chunks.join('\n'),
  };
}

function includesToken(haystack, token) {
  return haystack.toLowerCase().includes(String(token).toLowerCase());
}

function validateTokenList(area, tokens, relativePaths, violations, rows) {
  const sources = readExistingFiles(relativePaths);
  for (const missing of sources.missing) {
    violations.push(`${area}: missing proof source ${missing}`);
  }

  const missingTokens = tokens.filter((token) => !includesToken(sources.text, token));
  if (missingTokens.length > 0) {
    violations.push(`${area}: missing proof token(s): ${missingTokens.join(', ')}`);
  }

  rows.push({
    area,
    sourceCount: relativePaths.length - sources.missing.length,
    requiredTokenCount: tokens.length,
    missingTokens,
  });
}

function validateAtomicFamilies(matrix, violations, rows) {
  for (const family of matrix.proofAreas.atomicWorkflowFamilies) {
    validateTokenList(
      `atomicWorkflowFamilies.${family.id}`,
      family.requiredTokens,
      sourceSets.atomicWorkflowFamilies,
      violations,
      rows,
    );
  }
}

function extractOpenApiPaths(source) {
  const paths = [];
  for (const match of source.matchAll(/^  (\/[^:\n]+):\s*$/gm)) {
    paths.push(match[1]);
  }
  return paths;
}

function validateRouteClassification(matrix, violations, rows) {
  const actionPlanePath = path.join(repoRoot, matrix.routeClassification.canonicalSpecs[0]);
  const aepPath = path.join(repoRoot, matrix.routeClassification.compatibilitySpecs[0]);
  const registryPath = path.join(repoRoot, matrix.routeClassification.compatibilityRegistry);

  const actionPlane = existsSync(actionPlanePath) ? readFileSync(actionPlanePath, 'utf8') : '';
  const aep = existsSync(aepPath) ? readFileSync(aepPath, 'utf8') : '';
  const registry = existsSync(registryPath) ? readFileSync(registryPath, 'utf8') : '';

  if (!actionPlane) violations.push(`routeClassification: missing canonical spec ${matrix.routeClassification.canonicalSpecs[0]}`);
  if (!aep) violations.push(`routeClassification: missing compatibility spec ${matrix.routeClassification.compatibilitySpecs[0]}`);
  if (!registry) violations.push(`routeClassification: missing compatibility registry ${matrix.routeClassification.compatibilityRegistry}`);

  const canonicalPaths = extractOpenApiPaths(actionPlane);
  const leakedLegacyPaths = canonicalPaths.filter((routePath) => (
    routePath.startsWith('/api/v1/')
    && !routePath.startsWith(matrix.routeClassification.canonicalPrefix)
  ));
  if (leakedLegacyPaths.length > 0) {
    violations.push(`routeClassification: canonical Action Plane spec leaks legacy/root paths: ${leakedLegacyPaths.join(', ')}`);
  }

  if (!actionPlane.includes('Legacy AEP/root paths are compatibility-only')) {
    violations.push('routeClassification: canonical Action Plane spec must state that AEP/root paths are compatibility-only');
  }
  if (!aep.includes('AEP Compatibility Note') || !aep.includes('not a standalone product')) {
    violations.push('routeClassification: AEP spec must define AEP as compatibility/internal runtime naming');
  }
  if (!registry.includes('deprecated_since') || !registry.includes('retirement_target') || !registry.includes('DataCloudFeature.LEGACY_ACTION_ROUTES')) {
    violations.push('routeClassification: compatibility registry must include deprecated_since, retirement_target, and feature-flag lifecycle metadata');
  }

  const aepPaths = extractOpenApiPaths(aep);
  const rootApiPaths = aepPaths.filter((routePath) => routePath.startsWith('/api/v1/'));
  rows.push({
    area: 'routeClassification',
    canonicalPathCount: canonicalPaths.length,
    aepCompatibilityPathCount: rootApiPaths.length,
    canonicalLegacyLeakCount: leakedLegacyPaths.length,
  });
}

function scanForMovedPlaceholders(violations, rows) {
  const matches = [];

  function walk(dir) {
    if (!existsSync(dir)) return;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const absolutePath = path.join(dir, entry.name);
      const normalized = absolutePath.replace(/\\/g, '/');
      if (entry.isDirectory()) {
        if (/(^|\/)(build|node_modules|dist|target|\.gradle)(\/|$)/.test(normalized)) {
          continue;
        }
        walk(absolutePath);
      } else if (entry.isFile() && entry.name !== 'package-info.java' && /\.(java|ts|tsx|js|mjs|kt)$/.test(entry.name)) {
        const source = readFileSync(absolutePath, 'utf8');
        const sourceWithoutComments = source
          .replace(/\/\*[\s\S]*?\*\//g, '')
          .replace(/^\s*\/\/.*$/gm, '')
          .trim();
        const onlyPackageDeclaration = /^package\s+[\w.]+;\s*$/.test(sourceWithoutComments);
        if (/MOVED:\s*Implementation lives/i.test(source) || onlyPackageDeclaration) {
          matches.push(path.relative(repoRoot, absolutePath).replace(/\\/g, '/'));
        }
      }
    }
  }

  for (const root of productionSourceRoots) {
    walk(path.join(repoRoot, root));
  }

  if (matches.length > 0) {
    violations.push(`movedPlaceholderSources: production modules contain moved/empty source markers: ${matches.join(', ')}`);
  }
  rows.push({
    area: 'movedPlaceholderSources',
    violationCount: matches.length,
    matches,
  });
}

export function validateDataCloudMaturityProof({ matrix = readJson(matrixPath) } = {}) {
  const violations = [];
  const rows = [];

  validateAtomicFamilies(matrix, violations, rows);
  validateTokenList('agentRuntimeProof', matrix.proofAreas.agentRuntimeProof, sourceSets.agentRuntimeProof, violations, rows);
  validateTokenList('aiGovernanceProof', matrix.proofAreas.aiGovernanceProof, sourceSets.aiGovernanceProof, violations, rows);
  validateTokenList('uiRouteProof', matrix.proofAreas.uiRouteProof, sourceSets.uiRouteProof, violations, rows);
  validateTokenList('actionPlaneLifecycleProof', matrix.proofAreas.actionPlaneLifecycleProof, sourceSets.actionPlaneLifecycleProof, violations, rows);
  validateRouteClassification(matrix, violations, rows);
  scanForMovedPlaceholders(violations, rows);

  return {
    generatedAt: process.env.GITHUB_SHA ? `commit:${process.env.GITHUB_SHA}` : new Date().toISOString(),
    productId: matrix.productId,
    targetScore: matrix.targetScore,
    sourceCommitRequired: matrix.sourceCommitRequired,
    violations,
    rows,
    summary: {
      passed: violations.length === 0,
      violationCount: violations.length,
      proofAreaCount: rows.length,
    },
  };
}

function main() {
  if (!existsSync(matrixPath)) {
    console.error(`Missing maturity proof matrix: ${path.relative(repoRoot, matrixPath)}`);
    process.exit(1);
  }

  const report = validateDataCloudMaturityProof();
  mkdirSync(path.dirname(evidencePath), { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  if (report.violations.length > 0) {
    console.error('Data Cloud maturity proof failed:\n');
    for (const violation of report.violations) {
      console.error(`- ${violation}`);
    }
    console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
    process.exit(1);
  }

  console.log(`Data Cloud maturity proof passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main();
}
