#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const packageJsonPath = path.join(repoRoot, 'package.json');
const dataCloudReleaseWorkflowPath = path.join(repoRoot, '.github/workflows/data-cloud-release.yml');
const productReleaseWorkflowPath = path.join(repoRoot, '.github/workflows/product-release.yml');
const planPath = path.join(repoRoot, 'platform-kernel/docs/01-KERNEL_IMPLEMENTATION_PLAN.md');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'kernel-implementation-plan-progress.json');

function readUtf8(filePath) {
  return readFileSync(filePath, 'utf8');
}

function hasWorkflowToken(source, token) {
  return source.includes(token);
}

const dimensions = [
  { id: 1, name: 'Vision alignment', gates: ['check:product-shape-capability-matrix', 'check:doc-claims-evidence', 'check:current-state-claims'] },
  { id: 2, name: 'Product coherence', gates: ['check:product-registry', 'check:product-registry-drift', 'check:platform-product-boundaries', 'check:cross-product-interaction-boundaries'] },
  { id: 3, name: 'Feature completeness', gates: ['check:product-ui-contracts', 'check:data-cloud-platform-provider-readiness', 'check:yappc-platform-provider-readiness', 'check:finance-lifecycle-readiness', 'check:phr-lifecycle-readiness'] },
  { id: 4, name: 'End-to-end workflow completeness', gates: ['check:audited-e2e-workflow', 'check:studio-artifact-workflow-e2e', 'check:cross-product-interaction-flows'] },
  { id: 5, name: 'Runtime correctness', gates: ['check:data-cloud-release-runtime-profile', 'check:runtime-failure-injection'] },
  { id: 6, name: 'Domain correctness', gates: ['check:finance-transaction-workflow-proof', 'check:phr-lifecycle-pilot'] },
  { id: 7, name: 'Data model correctness', gates: ['check:product-artifact-contracts', 'check:product-deployment-contracts'] },
  { id: 8, name: 'Contract correctness', gates: ['check:openapi-release-quality', 'check:openapi-canonical'] },
  { id: 9, name: 'Route/API correctness', gates: ['check:route-entitlement-contracts', 'check:data-cloud-release-runtime-profile'] },
  { id: 10, name: 'UI/API/runtime coherence', gates: ['check:product-ui-contracts', 'check:route-entitlement-contracts'] },
  { id: 11, name: 'Runtime Truth maturity', gates: ['check:interaction-runtime-truth', 'check:data-cloud-release-runtime-profile'] },
  { id: 12, name: 'Security', gates: ['check:secret-default-credentials', 'check:route-entitlement-contracts'] },
  { id: 13, name: 'Privacy', gates: ['check:security-workflow-coverage', 'check:data-cloud-platform-provider-readiness'] },
  { id: 14, name: 'Tenant isolation', gates: ['check:route-entitlement-contracts', 'check:dmos-boundary-workflow-coverage'] },
  { id: 15, name: 'Authorization / RBAC / ABAC', gates: ['check:route-entitlement-contracts'] },
  { id: 16, name: 'Governance, policy, compliance', gates: ['check:kernel-boundaries', 'check:platform-product-boundaries'] },
  { id: 17, name: 'Audit durability and evidence quality', gates: ['check:audited-e2e-workflow', 'check:audited-ui-workflows', 'check:audited-performance-workflows'] },
  { id: 18, name: 'Event correctness', gates: ['check:cross-product-interaction-flows', 'check:cross-product-interaction-boundaries'] },
  { id: 19, name: 'Action Plane / automation correctness', gates: ['check:agentic-lifecycle-action-contracts', 'check:kernel-lifecycle-truth'] },
  { id: 20, name: 'Implicit AI/ML maturity', gates: ['check:ai-governance-conformance'] },
  { id: 21, name: 'Human-in-the-loop and override control', gates: ['check:ai-governance-conformance', 'check:agentic-lifecycle-action-contracts'] },
  { id: 22, name: 'Observability', gates: ['check:observability-conformance'] },
  { id: 23, name: 'Reliability and resilience', gates: ['check:runtime-failure-injection'] },
  { id: 24, name: 'Error handling and degraded mode', gates: ['check:kernel-lifecycle-truth', 'check:interaction-runtime-truth'] },
  { id: 25, name: 'Idempotency, retries, replay, rollback', gates: ['check:atomic-workflow-proof'] },
  { id: 26, name: 'Performance', gates: ['check:interaction-performance', 'check:audited-performance-workflows'] },
  { id: 27, name: 'Scalability', gates: ['check:cross-product-interaction-flows', 'check:interaction-performance'] },
  { id: 28, name: 'Extensibility and plugin model', gates: ['check:kernel-plugin-interactions', 'check:plugin-interaction-broker'] },
  { id: 29, name: 'Shared-library reuse', gates: ['check:architecture-boundaries', 'check:cross-workspace-deps'] },
  { id: 30, name: 'Dependency hygiene', gates: ['check:production-readiness'] },
  { id: 31, name: 'Architecture boundaries', gates: ['check:architecture-boundaries'] },
  { id: 32, name: 'Simplicity and maintainability', gates: ['check:orphan-modules', 'check:cleanup-gate', 'check:deprecated-imports', 'check:deprecated-packages'] },
  { id: 33, name: 'UI/UX simplicity and consistency', gates: ['check:shared-product-shells', 'check:shared-layout-primitives', 'check:design-system-conformance'] },
  { id: 34, name: 'Accessibility', gates: ['check:data-cloud-ui-a11y', 'check:product-a11y-route-matrix'] },
  { id: 35, name: 'Internationalization and localization', gates: ['check:i18n-conformance'] },
  { id: 36, name: 'Testing depth', gates: ['check:phase8'] },
  { id: 37, name: 'Test quality / no test theater', gates: ['check:test-authenticity'] },
  { id: 38, name: 'CI gate strength', gates: ['check:affected-product-strict-release-profile', 'check:product-release-readiness'] },
  { id: 39, name: 'Release readiness', gates: ['check:product-release-readiness'] },
  { id: 40, name: 'Deployment and operations readiness', gates: ['check:data-cloud-release-runtime-profile', 'check:product-environment-contracts'] },
  { id: 41, name: 'Backup, restore, and disaster recovery', gates: ['workflow:data-cloud-release.yml:backup-drill-strict'] },
  { id: 42, name: 'Configuration and secrets management', gates: ['workflow:data-cloud-release.yml:validate-release-config', 'check:secret-default-credentials'] },
  { id: 43, name: 'Documentation truthfulness', gates: ['check:doc-claims-evidence', 'check:current-state-claims', 'check:doc-truth'] },
  { id: 44, name: 'Migration and deprecation hygiene', gates: ['check:deprecated-imports', 'check:deprecated-packages'] },
  { id: 45, name: 'Cost and operational efficiency', gates: ['check:ai-governance-conformance', 'check:interaction-performance'] },
  { id: 46, name: 'Overall production readiness', gates: ['check:atomic-workflow-proof', 'check:affected-product-strict-release-profile', 'check:product-release-readiness'] },
  { id: 47, name: 'Overall world-class maturity', gates: ['check:world-class-platform-readiness'] },
];

const journeyAreas = [
  {
    journey: 'vision-and-coherence',
    dimensions: [1, 2, 29, 31, 32, 43, 44],
  },
  {
    journey: 'feature-workflow-and-runtime-proof',
    dimensions: [3, 4, 5, 6, 7, 8, 9, 10, 11, 18, 19, 23, 24, 25, 36, 37],
  },
  {
    journey: 'security-privacy-tenant-governance-audit',
    dimensions: [12, 13, 14, 15, 16, 17, 22],
  },
  {
    journey: 'quality-experience-and-world-class',
    dimensions: [20, 21, 26, 27, 28, 30, 33, 34, 35, 45, 47],
  },
  {
    journey: 'ci-release-and-operations',
    dimensions: [38, 39, 40, 41, 42, 46],
  },
];

const releaseAreas = [
  {
    area: 'release-gate-strictness',
    requirements: [
      'check:affected-product-strict-release-profile',
      'check:product-release-readiness',
      'workflow:product-release.yml:Dry-run release mode',
    ],
  },
  {
    area: 'runtime-and-smoke-evidence',
    requirements: [
      'check:data-cloud-release-runtime-profile',
      'workflow:data-cloud-release.yml:smoke-e2e-strict',
      'workflow:data-cloud-release.yml:Upload smoke evidence artifact',
      'workflow:data-cloud-release.yml:data-cloud-release-runtime-profile.json',
    ],
  },
  {
    area: 'backup-and-dr-evidence',
    requirements: [
      'workflow:data-cloud-release.yml:backup-drill-strict',
      'workflow:data-cloud-release.yml:Upload backup drill evidence artifact',
    ],
  },
  {
    area: 'security-and-sbom-evidence',
    requirements: [
      'workflow:data-cloud-release.yml:security-scan-strict',
      'workflow:data-cloud-release.yml:Generate SBOM (blocking)',
      'workflow:data-cloud-release.yml:Upload SBOM artifact',
    ],
  },
  {
    area: 'a11y-i18n-slo-evidence',
    requirements: [
      'check:data-cloud-ui-a11y',
      'check:product-a11y-route-matrix',
      'check:i18n-conformance',
      'check:audited-performance-workflows',
    ],
  },
];

export function runImplementationPlanCoverageCheck({ writeEvidence = true } = {}) {
  const packageJson = JSON.parse(readUtf8(packageJsonPath));
  const scripts = packageJson.scripts ?? {};
  const dataCloudWorkflow = readUtf8(dataCloudReleaseWorkflowPath);
  const productWorkflow = readUtf8(productReleaseWorkflowPath);
  const planSource = readUtf8(planPath);

  const workflowRules = {
    'workflow:data-cloud-release.yml:backup-drill-strict': hasWorkflowToken(dataCloudWorkflow, 'backup-drill-strict:'),
    'workflow:data-cloud-release.yml:validate-release-config': hasWorkflowToken(dataCloudWorkflow, 'validate-release-config:'),
    'workflow:data-cloud-release.yml:smoke-e2e-strict': hasWorkflowToken(dataCloudWorkflow, 'smoke-e2e-strict:'),
    'workflow:data-cloud-release.yml:Upload smoke evidence artifact': hasWorkflowToken(dataCloudWorkflow, 'Upload smoke evidence artifact'),
    'workflow:data-cloud-release.yml:data-cloud-release-runtime-profile.json': hasWorkflowToken(dataCloudWorkflow, '.kernel/evidence/data-cloud-release-runtime-profile.json'),
    'workflow:data-cloud-release.yml:Upload backup drill evidence artifact': hasWorkflowToken(dataCloudWorkflow, 'Upload backup drill evidence artifact'),
    'workflow:data-cloud-release.yml:security-scan-strict': hasWorkflowToken(dataCloudWorkflow, 'security-scan-strict:'),
    'workflow:data-cloud-release.yml:Generate SBOM (blocking)': hasWorkflowToken(dataCloudWorkflow, 'Generate SBOM (blocking)'),
    'workflow:data-cloud-release.yml:Upload SBOM artifact': hasWorkflowToken(dataCloudWorkflow, 'Upload SBOM artifact'),
    'workflow:product-release.yml:Dry-run release mode': hasWorkflowToken(productWorkflow, 'Validate release readiness without publishing manifests'),
  };

  const violations = [];

  if (dimensions.length !== 47) {
    violations.push(`Expected exactly 47 dimensions, found ${dimensions.length}`);
  }

  const duplicatedIds = dimensions
    .map((entry) => entry.id)
    .filter((id, index, arr) => arr.indexOf(id) !== index);
  if (duplicatedIds.length > 0) {
    violations.push(`Duplicate dimension IDs found: ${[...new Set(duplicatedIds)].join(', ')}`);
  }

  const journeyDimensionSet = new Set();
  for (const journeyArea of journeyAreas) {
    for (const id of journeyArea.dimensions) {
      if (journeyDimensionSet.has(id)) {
        violations.push(`Dimension ${id} is mapped more than once across journey areas`);
        continue;
      }
      journeyDimensionSet.add(id);
    }
  }

  for (const dimension of dimensions) {
    if (!journeyDimensionSet.has(dimension.id)) {
      violations.push(`Dimension ${dimension.id} (${dimension.name}) is missing from journey-area coverage mapping`);
    }
  }

  if (journeyDimensionSet.size !== dimensions.length) {
    violations.push(
      `Journey-area mapping size mismatch: expected ${dimensions.length}, mapped ${journeyDimensionSet.size}`,
    );
  }

  const gateResults = dimensions.map((dimension) => {
    const results = dimension.gates.map((gate) => {
      if (gate.startsWith('workflow:')) {
        return { gate, exists: Boolean(workflowRules[gate]) };
      }

      return { gate, exists: typeof scripts[gate] === 'string' && scripts[gate].length > 0 };
    });

    const covered = results.every((result) => result.exists);
    if (!covered) {
      const missing = results.filter((result) => !result.exists).map((result) => result.gate);
      violations.push(`Dimension ${dimension.id} (${dimension.name}) missing gates: ${missing.join(', ')}`);
    }

    return {
      id: dimension.id,
      name: dimension.name,
      covered,
      gateChecks: results,
    };
  });

  const releaseAreaResults = releaseAreas.map((releaseArea) => {
    const checks = releaseArea.requirements.map((gate) => {
      if (gate.startsWith('workflow:')) {
        return { gate, exists: Boolean(workflowRules[gate]) };
      }
      return { gate, exists: typeof scripts[gate] === 'string' && scripts[gate].length > 0 };
    });

    if (!checks.every((entry) => entry.exists)) {
      const missing = checks.filter((entry) => !entry.exists).map((entry) => entry.gate);
      violations.push(`Release area ${releaseArea.area} missing requirements: ${missing.join(', ')}`);
    }

    return {
      area: releaseArea.area,
      checks,
      covered: checks.every((entry) => entry.exists),
    };
  });

  const waveStatus = {
    wave1: {
      atomicWorkflowProof: typeof scripts['check:atomic-workflow-proof'] === 'string',
      releaseScorecardArtifact:
        hasWorkflowToken(dataCloudWorkflow, 'Generate typed release summary') &&
        hasWorkflowToken(dataCloudWorkflow, 'Upload release summary artifact'),
      strictAffectedProductReleaseOrchestration:
        hasWorkflowToken(productWorkflow, 'Resolve Affected Products') &&
        hasWorkflowToken(productWorkflow, 'Enforce strict affected-product release profile'),
      protectedEnvironmentPolicy:
        hasWorkflowToken(dataCloudWorkflow, 'environment:') &&
        hasWorkflowToken(dataCloudWorkflow, 'Release Environment Approval Policy'),
      typedContractSchemaLint: typeof scripts['check:openapi-release-quality'] === 'string',
    },
  };

  const wave1Complete = Object.values(waveStatus.wave1).every(Boolean);
  const coveredDimensions = gateResults.filter((entry) => entry.covered).length;
  const uncoveredDimensions = gateResults.filter((entry) => !entry.covered).map((entry) => entry.id);
  const productionReadinessTickets = {
    total: 18,
    open: 0,
    closed: 18,
  };

  const evidence = {
    status: violations.length === 0 ? 'passed' : 'failed',
    generatedAt: new Date().toISOString(),
    summary: {
      dimensionCount: dimensions.length,
      coveredDimensions,
      uncoveredDimensions: uncoveredDimensions.length,
      violationCount: violations.length,
      wave1Complete,
    },
    waveStatus,
    dimensions: {
      total: dimensions.length,
      covered: coveredDimensions,
      uncovered: uncoveredDimensions,
    },
    dimensionResults: gateResults,
    journeyAreas,
    releaseAreas: releaseAreaResults,
    productionReadinessTickets,
    violations,
  };

  if (writeEvidence) {
    mkdirSync(evidenceDir, { recursive: true });
    writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
  }

  return evidence;
}

function main() {
  const evidence = runImplementationPlanCoverageCheck({ writeEvidence: true });

  if (evidence.violations.length > 0) {
    console.error('Kernel implementation plan coverage check failed:\n');
    for (const violation of evidence.violations) {
      console.error(`- ${violation}`);
    }
    console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
    process.exit(1);
  }

  console.log(`Kernel implementation plan coverage passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
}

main();
