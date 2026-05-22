#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import YAML from 'yaml';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const workflowMatrix = {
  'digital-marketing': [
    {
      id: 'customer-account-management',
      source: ['products/digital-marketing/dm-domain/src/main/java/com/ghatana/digitalmarketing/domain/preference/CustomerPreference.java'],
      tests: ['products/digital-marketing/dm-application/src/test/java/com/ghatana/digitalmarketing/application/lead/LeadServiceImplTest.java'],
    },
    {
      id: 'campaign-lifecycle',
      source: ['products/digital-marketing/dm-domain/src/main/java/com/ghatana/digitalmarketing/domain/campaign/CampaignTransitionService.java'],
      tests: ['products/digital-marketing/dm-integration-tests/src/test/java/com/ghatana/digitalmarketing/integration/CampaignLifecycleIT.java'],
    },
    {
      id: 'lead-capture',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/lead/DmLeadCaptureServiceImpl.java'],
      tests: ['products/digital-marketing/dm-application/src/test/java/com/ghatana/digitalmarketing/application/lead/DmLeadCaptureServiceImplTest.java'],
    },
    {
      id: 'conversion-tracking',
      source: ['products/digital-marketing/dm-domain/src/main/java/com/ghatana/digitalmarketing/domain/performance/DmCampaignPerformanceSnapshot.java'],
      tests: ['products/digital-marketing/dm-domain/src/test/java/com/ghatana/digitalmarketing/domain/performance/DmCampaignPerformanceSnapshotTest.java'],
    },
    {
      id: 'audience-segments',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/audience/AudienceRepository.java'],
      tests: ['products/digital-marketing/ui/src/pages/__tests__/CampaignsPage.test.tsx'],
    },
    {
      id: 'google-ads-connector',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/googleads/DmGoogleAdsCampaignConnectorServiceImpl.java'],
      tests: ['products/digital-marketing/dm-connector-google-ads/src/test/java/com/ghatana/digitalmarketing/connector/googleads/HttpDmGoogleAdsCampaignApiClientAdapterTest.java'],
    },
    {
      id: 'notification-retry-dlq',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/event/DmOutboxServiceImpl.java'],
      tests: ['products/digital-marketing/dm-application/src/test/java/com/ghatana/digitalmarketing/application/event/DmOutboxServiceImplTest.java'],
    },
    {
      id: 'reporting-dashboard',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/report/DmPerformanceReportServiceImpl.java'],
      tests: ['products/digital-marketing/dm-domain/src/test/java/com/ghatana/digitalmarketing/domain/report/DmPerformanceReportTest.java'],
    },
    {
      id: 'consent-gated-phr-activation',
      source: ['products/digital-marketing/dm-application/src/main/java/com/ghatana/digitalmarketing/application/campaign/ConsentInteractionBroker.java'],
      tests: ['products/digital-marketing/dm-application/src/test/java/com/ghatana/digitalmarketing/application/campaign/CampaignServiceImplTest.java'],
    },
  ],
  phr: [
    {
      id: 'patient-profile',
      source: ['products/phr/src/main/java/com/ghatana/phr/api/PatientController.java'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/api/PatientControllerTest.java'],
    },
    {
      id: 'health-summary',
      source: ['products/phr/apps/web/src/pages/DashboardPage.tsx'],
      tests: ['products/phr/apps/web/src/__tests__/phrApp.test.tsx'],
    },
    {
      id: 'clinical-resources',
      source: ['products/phr/src/main/java/com/ghatana/phr/fhir/server/MedicationRequestFhirResourceProvider.java'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/fhir/FhirR4ContractTest.java'],
    },
    {
      id: 'documents',
      source: ['products/phr/domain-pack-manifest.yaml'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/fhir/FhirR4TransformationEngineTest.java'],
      token: 'clinical-documents',
    },
    {
      id: 'consent-management',
      source: ['products/phr/src/main/java/com/ghatana/phr/kernel/service/ConsentManagementService.java'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/kernel/service/ConsentManagementServiceTest.java'],
    },
    {
      id: 'data-sharing-authorization',
      source: ['products/phr/policy-packs/healthcare-boundary-policy.yaml'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/kernel/policy/PhrConsentBoundaryAccessGateTest.java'],
      token: 'data sharing consent',
    },
    {
      id: 'audit-access-history',
      source: ['products/phr/src/main/java/com/ghatana/phr/observability/PHRAuditTrailServiceImpl.java'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/observability/PHRAuditTrailServiceTest.java'],
    },
    {
      id: 'fhir-r4-validation',
      source: ['products/phr/src/main/java/com/ghatana/phr/fhir/FhirValidator.java'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/fhir/server/PhrFhirR4ServerTest.java'],
    },
    {
      id: 'tenant-data-sovereignty',
      source: ['products/phr/lifecycle/gate-packs/tenant-data-sovereignty.yaml'],
      tests: ['products/phr/src/test/java/com/ghatana/phr/kernel/PhrKernelBoundaryContractTest.java'],
    },
    {
      id: 'i18n-a11y',
      source: ['products/phr/apps/web/src/i18n/phrI18n.ts', 'products/phr/apps/web/src/locales/ne/common.json'],
      tests: ['products/phr/apps/web/src/i18n/__tests__/phrI18n.test.ts', 'products/phr/apps/web/tests/e2e/phr-a11y.spec.ts'],
    },
  ],
};

function abs(relativePath) {
  return path.join(repoRoot, relativePath);
}

function fail(message) {
  throw new Error(message);
}

function read(relativePath) {
  return readFileSync(abs(relativePath), 'utf8');
}

function readJson(relativePath) {
  return JSON.parse(read(relativePath));
}

function assertFile(relativePath, label) {
  if (!existsSync(abs(relativePath))) {
    fail(`${label} is missing: ${relativePath}`);
  }
}

function validateWorkflowProduct(productId) {
  const workflows = workflowMatrix[productId] ?? fail(`Unknown workflow product: ${productId}`);
  for (const workflow of workflows) {
    for (const file of [...workflow.source, ...workflow.tests]) {
      assertFile(file, `${productId}/${workflow.id}`);
    }
    if (workflow.token) {
      const joined = workflow.source.map(read).join('\n').toLowerCase();
      if (!joined.includes(workflow.token.toLowerCase())) {
        fail(`${productId}/${workflow.id} does not contain required token: ${workflow.token}`);
      }
    }
  }
}

function validateProductFeatureCompleteness() {
  validateWorkflowProduct('digital-marketing');
  validateWorkflowProduct('phr');

  const requiredScorecardTerms = ['blocker severity', 'owner module', 'ticket suggestion'];
  for (const productId of Object.keys(workflowMatrix)) {
    const scorecard = `.kernel/evidence/product-scorecards/${productId}.md`;
    assertFile(scorecard, `${productId} scorecard`);
    const content = read(scorecard).toLowerCase();
    for (const term of requiredScorecardTerms) {
      if (!content.includes(term)) {
        fail(`${scorecard} must include actionable ${term} entries`);
      }
    }
  }
}

function validateReleaseRollbackDrill() {
  const dmKernel = YAML.parse(read('products/digital-marketing/kernel-product.yaml'));
  if (!dmKernel.phases?.rollback || !dmKernel.requiredManifests?.rollback?.includes('rollback-manifest')) {
    fail('Digital Marketing must declare rollback phase and rollback-manifest requirement');
  }
  const phrKernel = YAML.parse(read('products/phr/kernel-product.yaml'));
  if (!phrKernel.rollbackReadiness?.requiredBeforeEnablement?.includes('previous-artifact-selection-policy')) {
    fail('PHR rollback readiness must preserve previous-artifact-selection-policy blocker');
  }
  for (const file of [
    'platform/typescript/kernel-release/src/ProductRollbackPlan.ts',
    'platform/typescript/kernel-release/src/__tests__/PromotionRollbackGate.test.ts',
    '.kernel/evidence/digital-marketing/digital-marketing-lifecycle-evidence-pack.json',
    '.kernel/evidence/phr/phr-lifecycle-evidence-pack.json',
  ]) {
    assertFile(file, 'rollback drill evidence');
  }
}

function validateStudioLifecycleControlPlane() {
  for (const file of [
    'platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts',
    'platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx',
    'platform/typescript/ghatana-studio/src/routes/__tests__/LifecyclePage.test.tsx',
  ]) {
    assertFile(file, 'Studio lifecycle control plane');
  }
  const api = read('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts');
  for (const token of ['createLifecyclePlan', 'executeLifecyclePhase', 'listLifecycleRuns', 'putStudioWorkflowEvidence']) {
    if (!api.includes(token)) {
      fail(`Kernel lifecycle API is missing ${token}`);
    }
  }
}

function validateDurableEventProvider() {
  const provider = read('platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/interaction/DataCloudProductInteractionEventProvider.java');
  for (const token of ['store', 'isDelivered', 'sendToDlq', 'getDlqEvents', 'getEventsForReplay', 'deleteEventsBefore']) {
    if (!provider.includes(token)) {
      fail(`Durable event provider is missing ${token}`);
    }
  }
  assertFile(
    'platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/interaction/DataCloudProductInteractionEventProviderTest.java',
    'durable event provider test',
  );
}

function validateEvidenceRetentionPolicy() {
  validateDurableEventProvider();
  for (const file of [
    'products/phr/docs/01_governance/phr_retention_and_deletion_policy.md',
    'products/phr/lifecycle/gate-packs/audit-evidence.yaml',
    'platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts',
  ]) {
    assertFile(file, 'evidence retention policy');
  }
  const api = read('platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts');
  if (!api.includes('putStudioWorkflowEvidence') || !api.includes('assertNoSecrets')) {
    fail('Studio workflow evidence must be persisted through validated, secret-checked API handlers');
  }
}

function validateProductionDeploymentProvenance() {
  for (const file of [
    'platform/typescript/kernel-release/src/ProductRelease.ts',
    'platform/typescript/kernel-release/src/ProductReleaseManifest.ts',
    'platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts',
    'config/product-release-profiles.json',
    '.github/workflows/product-release.yml',
  ]) {
    assertFile(file, 'deployment provenance');
  }
  const profiles = readJson('config/product-release-profiles.json').profiles;
  for (const [profileId, profile] of Object.entries(profiles)) {
    if (profile.status !== 'stable') {
      fail(`Release profile ${profileId} must be stable before it can gate production provenance`);
    }
    for (const field of ['requiredEvidence', 'requiredChecks', 'sbomPolicy', 'rollbackPolicy']) {
      if (!profile[field]) {
        fail(`Release profile ${profileId} is missing ${field}`);
      }
    }
    if (profile.sbomPolicy.required !== true || profile.sbomPolicy.formats?.length === 0) {
      fail(`Release profile ${profileId} must require an SBOM format`);
    }
    if (profile.rollbackPolicy.previousArtifactSelectionRequired !== true) {
      fail(`Release profile ${profileId} must require previous artifact selection`);
    }
  }
  const releaseSource = read('platform/typescript/kernel-release/src/ProductRelease.ts');
  for (const token of ['sourceRef', 'artifactManifest', 'deploymentManifest', 'releaseManifest', 'fingerprint']) {
    if (!releaseSource.includes(token)) {
      fail(`ProductRelease provenance schema is missing ${token}`);
    }
  }
  const manifestSource = read('platform/typescript/kernel-release/src/ProductReleaseManifest.ts');
  for (const token of ['releaseProfileId', 'sbomChecks', 'SBOM generation is required']) {
    if (!manifestSource.includes(token)) {
      fail(`Product release manifest validation is missing ${token}`);
    }
  }
}

const tasks = {
  'product-feature-completeness': validateProductFeatureCompleteness,
  'dmos-production-workflows': () => validateWorkflowProduct('digital-marketing'),
  'phr-production-workflows': () => validateWorkflowProduct('phr'),
  'release-rollback-drill': validateReleaseRollbackDrill,
  'studio-lifecycle-control-plane': validateStudioLifecycleControlPlane,
  'interaction-durable-event-provider': validateDurableEventProvider,
  'evidence-retention-policy': validateEvidenceRetentionPolicy,
  'production-deployment-provenance': validateProductionDeploymentProvenance,
};

export function runAuditTask(task) {
  const check = tasks[task];
  if (!check) {
    fail(`Unknown production readiness audit task: ${task}`);
  }
  check();
  return task;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const task = process.argv.find((arg) => arg.startsWith('--task='))?.slice('--task='.length);
  runAuditTask(task ?? 'product-feature-completeness');
  console.log(`Production readiness audit task passed: ${task ?? 'product-feature-completeness'}`);
}
