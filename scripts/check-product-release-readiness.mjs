#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-release-readiness.json');

function runCheck(checkRef) {
  if (checkRef.startsWith('pnpm:')) {
    const scriptName = checkRef.slice('pnpm:'.length);
    return spawnSync('pnpm', [scriptName], {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });
  }

  return spawnSync('node', [checkRef], {
    cwd: repoRoot,
    stdio: 'inherit',
    env: process.env,
  });
}

const journeyMatrix = [
  {
    journey: 'vision-and-coherence',
    areas: [
      {
        area: 'product-truth-and-ownership',
        scripts: [
          './scripts/check-product-shape-capability-matrix.mjs',
          './scripts/check-product-registry-drift.mjs',
          'pnpm:check:product-registry',
          './scripts/check-platform-product-boundaries.mjs',
          './scripts/check-cross-product-interaction-boundaries.mjs',
        ],
      },
    ],
  },
  {
    journey: 'workflow-and-runtime-proof',
    areas: [
      {
        area: 'e2e-runtime-and-failure-proof',
        scripts: [
          './scripts/check-product-ui-contracts.mjs',
          './scripts/check-audited-e2e-workflow.mjs',
          'pnpm:check:cross-product-interaction-flows',
          './scripts/check-interaction-runtime-truth.mjs',
          './scripts/check-data-cloud-release-runtime-profile.mjs',
          './scripts/check-runtime-failure-injection.mjs',
          './scripts/check-atomic-workflow-proof.mjs',
        ],
      },
    ],
  },
  {
    journey: 'security-privacy-governance',
    areas: [
      {
        area: 'secure-routes-observability-and-doc-truth',
        scripts: [
          './scripts/check-secret-default-credentials.mjs',
          './scripts/check-route-entitlement-contracts.mjs',
          './scripts/check-observability-conformance.mjs',
          './scripts/check-doc-claims-evidence.mjs',
          './scripts/check-current-state-claims.mjs',
          './scripts/check-doc-truth.mjs',
        ],
      },
    ],
  },
  {
    journey: 'quality-experience-and-release',
    areas: [
      {
        area: 'a11y-i18n-ai-performance',
        scripts: [
          './scripts/check-data-cloud-ui-a11y.mjs',
          './scripts/check-product-a11y-route-matrix.mjs',
          './scripts/check-i18n-conformance.mjs',
          './scripts/check-ai-governance-conformance.mjs',
          './scripts/check-audited-performance-workflows.mjs',
        ],
      },
      {
        area: 'strict-release-and-coverage',
        scripts: [
          './scripts/check-openapi-release-quality.mjs',
          './scripts/generate-wave2-product-quality-scorecard.mjs',
          './scripts/check-product-ci-matrices.mjs',
          './scripts/check-affected-product-strict-release-profile.mjs',
          './scripts/check-kernel-implementation-plan-coverage.mjs',
        ],
      },
    ],
  },
];

const releaseAreas = [
  {
    area: 'strict-release-gate-evidence',
    scripts: [
      './scripts/check-data-cloud-release-runtime-profile.mjs',
      './scripts/check-affected-product-strict-release-profile.mjs',
      './scripts/check-kernel-implementation-plan-coverage.mjs',
    ],
  },
  {
    area: 'quality-and-experience-gates',
    scripts: [
      './scripts/check-data-cloud-ui-a11y.mjs',
      './scripts/check-product-a11y-route-matrix.mjs',
      './scripts/check-i18n-conformance.mjs',
      './scripts/check-ai-governance-conformance.mjs',
      './scripts/check-audited-performance-workflows.mjs',
      './scripts/check-openapi-release-quality.mjs',
    ],
  },
  {
    area: 'security-and-doc-truth-gates',
    scripts: [
      './scripts/check-secret-default-credentials.mjs',
      './scripts/check-route-entitlement-contracts.mjs',
      './scripts/check-doc-claims-evidence.mjs',
      './scripts/check-current-state-claims.mjs',
      './scripts/check-doc-truth.mjs',
    ],
  },
];

const executionOrder = [];
for (const journey of journeyMatrix) {
  for (const area of journey.areas) {
    for (const scriptPath of area.scripts) {
      if (!executionOrder.includes(scriptPath)) {
        executionOrder.push(scriptPath);
      }
    }
  }
}

for (const releaseArea of releaseAreas) {
  for (const scriptPath of releaseArea.scripts) {
    if (!executionOrder.includes(scriptPath)) {
      executionOrder.push(scriptPath);
    }
  }
}

const runs = [];
const runByScript = new Map();

function failWithEvidence(exitCode, failedScript, reason) {
  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(
    evidencePath,
    `${JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        pass: false,
        reason,
        failedScript,
        runs,
        journeyMatrix,
        releaseAreas,
      },
      null,
      2,
    )}\n`,
    'utf8',
  );
  if (failedScript) {
    console.error(`Product release readiness failed at ${failedScript}`);
  }
  process.exit(exitCode);
}

for (const journey of journeyMatrix) {
  if (!journey.areas || journey.areas.length === 0) {
    failWithEvidence(1, null, `Journey ${journey.journey} must define at least one area`);
  }
  for (const area of journey.areas) {
    if (!area.scripts || area.scripts.length === 0) {
      failWithEvidence(1, null, `Area ${journey.journey}/${area.area} must define at least one script`);
    }
  }
}

for (const scriptPath of executionOrder) {
  const startedAt = Date.now();
  const result = runCheck(scriptPath);

  const status = result.status ?? 1;
  const durationMs = Date.now() - startedAt;
  const runRecord = { script: scriptPath, status, durationMs };
  runByScript.set(scriptPath, runRecord);
  runs.push(runRecord);

  if (status !== 0) {
    failWithEvidence(status, scriptPath, 'script-failure');
  }
}

const journeyResults = journeyMatrix.map((journey) => {
  const areaResults = journey.areas.map((area) => {
    const areaRuns = area.scripts
      .map((scriptPath) => runByScript.get(scriptPath))
      .filter(Boolean);
    return {
      area: area.area,
      scripts: area.scripts,
      pass: areaRuns.every((run) => run.status === 0),
    };
  });

  return {
    journey: journey.journey,
    pass: areaResults.every((area) => area.pass),
    areas: areaResults,
  };
});

const releaseAreaResults = releaseAreas.map((releaseArea) => {
  const releaseRuns = releaseArea.scripts
    .map((scriptPath) => runByScript.get(scriptPath))
    .filter(Boolean);
  return {
    area: releaseArea.area,
    pass: releaseRuns.every((run) => run.status === 0),
    scripts: releaseArea.scripts,
  };
});

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(
  evidencePath,
  `${JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      pass: true,
      journeyResults,
      releaseAreaResults,
      runs,
    },
    null,
    2,
  )}\n`,
  'utf8',
);

console.log(`Product release readiness check passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);
