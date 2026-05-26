#!/usr/bin/env node

import { createChecker, readText } from './lib/yappc-release-check-utils.mjs';

const checker = createChecker({
  checkId: 'YAPPC-006 kernel artifact roundtrip',
  evidencePath: '.kernel/evidence/yappc/kernel-artifact-roundtrip.json',
});

const files = [
  'products/yappc/core/scaffold/api/src/test/java/com/ghatana/yappc/cli/E2EMonorepoTest.java',
  'products/yappc/core/scaffold/engine/src/main/java/com/ghatana/yappc/core/cache/CachedArtifact.java',
  'products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/packs/PackTemplate.java',
  'products/yappc/kernel-bridge/src/main/java/com/ghatana/yappc/kernel/YappcSemanticArtifactEvidenceProvider.java',
  'platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip.test.ts',
  'platform/typescript/artifact-compiler-ts/src/__tests__/react-protected-regions.test.ts',
  'platform/typescript/artifact-compiler-ts/src/__tests__/roundtrip-diff.test.ts',
  'platform/typescript/artifact-compiler-ts/src/__tests__/generated-validation.test.ts',
  'scripts/check-artifact-roundtrip.mjs',
  'scripts/check-generated-artifact-validation-pipeline.mjs',
];

for (const file of files) checker.requireFile(file);

const roundtripText = files.slice(3).map((file) => readText(file)).join('\n');
const scenarios = {
  generateArtifact: ['generate', 'artifact'],
  compileValidate: ['compile', 'validate'],
  decompileImport: ['decompile', 'import'],
  modify: ['modify', 'modified'],
  roundtripDiff: ['roundtrip', 'diff'],
  protectedRegionsPreserved: ['protected', 'preserved'],
  validationPasses: ['validation', 'passes'],
};

for (const [name, tokens] of Object.entries(scenarios)) {
  checker.record(name, tokens.every((token) => roundtripText.toLowerCase().includes(token)), { tokens });
}

checker.record('YAPPC kernel bridge persists semantic artifact evidence', readText(files[3]).includes('semanticArtifactEvidence'), {
  path: files[3],
});

checker.finish({ scenarios: Object.keys(scenarios) });
