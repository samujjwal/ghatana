#!/usr/bin/env node

import { createChecker, readText } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-007 kernel bridge contracts',
  evidencePath: '.kernel/evidence/yappc/kernel-bridge-contracts.json',
});

const files = [
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcPluginBridgeExtension.java',
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcProductUnitIntentProvider.java',
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcSemanticArtifactEvidenceProvider.java',
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcArtifactGraphSummaryProvider.java',
  'products/yappc/kernel-bridge/src/test/java/com/ghatana/yappc/kernel/YappcPluginBridgeExtensionTest.java',
  'products/data-cloud/extensions/kernel-bridge/src/test/java/com/ghatana/datacloud/kernel/DataCloudProviderTest.java',
  'platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/registry/ServiceRegistry.java',
  'platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/plugin/KernelPlugin.java',
];

for (const file of files) checker.requireFile(file);

const bridge = readText(files[0]);
for (const token of ['registerService', 'ProductUnitIntent', 'SemanticArtifactEvidence', 'ArtifactGraphSummary', 'RiskHotspot', 'ResidualIsland']) {
  checker.record(`YAPPC bridge registers ${token}`, bridge.includes(token), { path: files[0], token });
}

const tests = readText(files[4]);
for (const token of ['yappc.product-unit-intents', 'yappc.artifact-intelligence', 'YappcArtifactGraphSummaryProvider', 'YappcRiskHotspotReportProvider', 'YappcResidualIslandReportProvider']) {
  checker.record(`kernel bridge contract test covers ${token}`, tests.includes(token), { path: files[4], token });
}

const lifecycleEvidence = [
  'scripts/check-yappc-product-unit-intent-handoff.mjs',
  'scripts/check-yappc-artifact-intelligence-boundary.mjs',
  'scripts/check-artifact-roundtrip.mjs',
  'scripts/check-generated-artifact-validation-pipeline.mjs',
  'scripts/check-lifecycle-explain-recover.mjs',
].map((file) => ({ file, source: readText(file) }));

const lifecycleTokens = {
  plan: ['plan', 'ProductUnitIntent'],
  generate: ['generate'],
  validate: ['validate'],
  test: ['test'],
  package: ['package', 'artifact'],
  explain: ['explain'],
  recover: ['recover'],
  'persist evidence': ['evidenceRef', 'evidence'],
};

for (const [step, tokens] of Object.entries(lifecycleTokens)) {
  const normalized = step.toLowerCase();
  const matched = lifecycleEvidence.some(({ source }) =>
    tokens.some((token) => source.toLowerCase().includes(token.toLowerCase()) || normalized.includes(token.toLowerCase())),
  );
  checker.record(`YAPPC lifecycle has ${step} evidence`, matched, { step, tokens });
}

checker.record('YAPPC bridge uses Kernel service registry rather than late add-on imports', bridge.includes('registerService'), {
  path: files[0],
});

checker.finish({
  lifecycleSteps: ['plan', 'generate', 'validate', 'test', 'package', 'explain', 'recover', 'persist evidence'],
});
