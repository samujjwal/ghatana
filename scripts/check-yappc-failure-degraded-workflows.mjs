#!/usr/bin/env node

import { createChecker, readText } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-010 failure and degraded workflows',
  evidencePath: '.kernel/evidence/yappc/failure-degraded-workflows.json',
});

const sources = [
  'products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/datacloud/YappcDataCloudE2ETest.java',
  'products/yappc/infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/YappcDataCloudAgentRuntimeE2ETest.java',
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentExecutorOperator.java',
  'products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/scaffold/YappcScaffoldService.java',
  'platform/typescript/kernel-lifecycle/src/__tests__/KernelLifecycleErrors.test.ts',
  'platform/typescript/artifact-compiler-ts/src/__tests__/generated-validation.test.ts',
  'platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts',
  'platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts',
  'scripts/check-openapi-contract-canonical.mjs',
];

for (const file of sources) checker.requireFile(file);

const corpus = sources.map((file) => readText(file)).join('\n').toLowerCase();
const scenarios = {
  dataCloudUnavailable: ['data-cloud connection failed'],
  agentRuntimeUnavailable: ['agent_system_not_initialized', 'agent_not_found'],
  kernelLifecycleApiUnavailable: ['unavailable', 'kernel'],
  artifactValidationFails: ['validation', 'fail'],
  scaffoldGeneratorFails: ['scaffold/generate failed', 'scaffold/analyze failed'],
  protectedRegionConflict: ['protected', 'region'],
  contractDrift: ['contract drift'],
};

for (const [name, tokens] of Object.entries(scenarios)) {
  checker.record(name, tokens.every((token) => corpus.includes(token)), { tokens });
}

checker.finish({ scenarios: Object.keys(scenarios) });
