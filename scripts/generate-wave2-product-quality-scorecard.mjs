#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'wave2-product-quality-scorecard.json');
const releaseEvidenceDir = path.join(repoRoot, 'release-evidence');
const releaseMarkdownPath = path.join(releaseEvidenceDir, 'wave2-product-quality-scorecard.md');

const stableGeneratedAt = 'generated-on-demand';

const artifactAuthoringGateScripts = {
  sourceAcquisition: [
    'check:studio-source-acquisition-worker',
    'check:studio-production-profile:strict',
  ],
  compilerDecompilerFidelity: [
    'check:artifact-roundtrip',
    'check:kernel-authoring-pipeline',
  ],
  studioWorkflow: [
    'check:studio-artifact-workflow-e2e',
    'check:studio-deep-interactions',
  ],
  generatedValidation: [
    'check:generated-artifact-validation-pipeline',
    'check:artifact-roundtrip',
  ],
  evidencePersistence: [
    'check:studio-workflow-persistence-contracts',
  ],
};

function readJson(relativePath, root = repoRoot) {
  const absolutePath = path.join(root, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

function hasTaggedA11ySpec(productId, root = repoRoot) {
  const candidatesByProduct = {
    phr: ['products/phr/apps/web/tests/e2e/phr-a11y.spec.ts'],
    'digital-marketing': [
      'products/digital-marketing/ui/e2e/a11y.spec.ts',
      'products/digital-marketing/ui/e2e/accessibility.spec.ts',
    ],
  };

  const candidates = candidatesByProduct[productId] ?? [];
  for (const relativePath of candidates) {
    const absolutePath = path.join(root, relativePath);
    if (!existsSync(absolutePath)) {
      continue;
    }
    const source = readFileSync(absolutePath, 'utf8');
    if (source.includes('@a11y') || source.includes('accessibility')) {
      return true;
    }
  }

  return false;
}

function hasWebSurface(product) {
  return product.surfaces.some((surface) => surface.type === 'web');
}

function hasScripts(scripts, scriptNames) {
  return scriptNames.every((scriptName) => typeof scripts[scriptName] === 'string');
}

function buildPlatformGateMap(scripts) {
  return {
    a11y: typeof scripts['check:data-cloud-ui-a11y'] === 'string',
    productA11yRouteMatrix: typeof scripts['check:product-a11y-route-matrix'] === 'string',
    i18n: typeof scripts['check:i18n-conformance'] === 'string',
    aiGovernance: typeof scripts['check:ai-governance-conformance'] === 'string',
    performanceSlo: typeof scripts['check:audited-performance-workflows'] === 'string',
    runtimeTruth: typeof scripts['check:interaction-runtime-truth'] === 'string',
    artifactSourceAcquisition: hasScripts(scripts, artifactAuthoringGateScripts.sourceAcquisition),
    artifactCompilerDecompilerFidelity: hasScripts(scripts, artifactAuthoringGateScripts.compilerDecompilerFidelity),
    artifactStudioWorkflow: hasScripts(scripts, artifactAuthoringGateScripts.studioWorkflow),
    artifactGeneratedValidation: hasScripts(scripts, artifactAuthoringGateScripts.generatedValidation),
    artifactEvidencePersistence: hasScripts(scripts, artifactAuthoringGateScripts.evidencePersistence),
  };
}

function buildArtifactAuthoringSignals(platformGateMap) {
  return {
    sourceAcquisition: platformGateMap.artifactSourceAcquisition,
    compilerDecompilerFidelity: platformGateMap.artifactCompilerDecompilerFidelity,
    studioWorkflow: platformGateMap.artifactStudioWorkflow,
    generatedValidation: platformGateMap.artifactGeneratedValidation,
    evidencePersistence: platformGateMap.artifactEvidencePersistence,
  };
}

export function buildWave2ProductQualityScorecard(options = {}) {
  const root = options.repoRoot ?? repoRoot;
  const packageJsonPath = path.join(root, 'package.json');
  const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
  const scripts = packageJson.scripts ?? {};
  const registry = options.registry ?? loadCanonicalRegistry(root);
  const platformGateMap = buildPlatformGateMap(scripts);
  const accessibilityWorkflowPath = path.join(root, '.github/workflows/accessibility.yml');
  const accessibilityWorkflow = existsSync(accessibilityWorkflowPath)
    ? readFileSync(accessibilityWorkflowPath, 'utf8')
    : '';

  const businessProducts = Object.values(registry)
    .filter((product) => product.kind === 'business-product')
    .filter((product) => product.metadata?.status === 'active')
    .map((product) => ({
      id: product.id,
      surfaces: product.surfaces ?? [],
    }))
    .sort((a, b) => a.id.localeCompare(b.id));

  const scoreRows = businessProducts.map((product) => {
    const web = hasWebSurface(product);
    const webSurface = product.surfaces.find((surface) => surface.type === 'web');
    const packagePath = webSurface?.packagePath;
    const surfacePackageJson = packagePath ? readJson(packagePath, root) : null;
    const lifecycleEnabled = (registry[product.id]?.lifecycle?.enabled === true)
      || (registry[product.id]?.lifecycleExecutionAllowed === true);

    const a11yPathToken = webSurface?.path ? `'${webSurface.path}/**'` : null;
    const a11yProductSignal = web
      ? Boolean(
        surfacePackageJson?.scripts?.['test:e2e:a11y']
        && hasTaggedA11ySpec(product.id, root)
        && a11yPathToken
        && accessibilityWorkflow.includes(a11yPathToken),
      )
      : true;

    const i18nProductSignal = web
      ? (product.id === 'digital-marketing'
        ? existsSync(path.join(root, 'products/digital-marketing/ui/src/lib/i18n/format.ts'))
        : product.id === 'phr'
          ? existsSync(path.join(root, 'products/phr/apps/web/src/i18n/phrI18n.ts'))
            || existsSync(path.join(root, 'products/phr/apps/web/src/i18n/config.ts'))
          : true)
      : true;

    const aiProductSignal = existsSync(path.join(root, '.github/workflows/agent-eval.yml'));
    const perfProductSignal = existsSync(path.join(root, '.github/workflows/performance-budgets.yml'));
    const runtimeTruthSignal = typeof scripts['check:interaction-runtime-truth'] === 'string';
    const artifactAuthoring = buildArtifactAuthoringSignals(platformGateMap);

    const area = {
      a11y: lifecycleEnabled ? (web ? (platformGateMap.a11y && platformGateMap.productA11yRouteMatrix && a11yProductSignal) : true) : true,
      i18n: lifecycleEnabled ? (web ? platformGateMap.i18n && i18nProductSignal : true) : true,
      aiGovernance: platformGateMap.aiGovernance && aiProductSignal,
      performanceSlo: platformGateMap.performanceSlo && perfProductSignal,
      runtimeTruth: platformGateMap.runtimeTruth && runtimeTruthSignal,
      artifactSourceAcquisition: artifactAuthoring.sourceAcquisition,
      artifactCompilerDecompilerFidelity: artifactAuthoring.compilerDecompilerFidelity,
      artifactStudioWorkflow: artifactAuthoring.studioWorkflow,
      artifactGeneratedValidation: artifactAuthoring.generatedValidation,
      artifactEvidencePersistence: artifactAuthoring.evidencePersistence,
    };

    const passedAreas = Object.values(area).filter(Boolean).length;
    const totalAreas = Object.keys(area).length;

    return {
      productId: product.id,
      webSurface: web,
      area,
      artifactAuthoring,
      score: {
        passedAreas,
        totalAreas,
        ratio: Number((passedAreas / totalAreas).toFixed(2)),
      },
    };
  });

  return {
    generatedAt: options.generatedAt ?? stableGeneratedAt,
    artifactAuthoringGateScripts,
    platformGateMap,
    productCount: scoreRows.length,
    scoreRows,
  };
}

export function renderWave2ProductQualityScorecardMarkdown(summary) {
  const markdownLines = [
  '# Wave 2 Product Quality Scorecard',
  '',
  `- Generated at: ${summary.generatedAt}`,
  `- Products evaluated: ${summary.productCount}`,
  '',
  '| Product | A11y | i18n | AI Governance | Performance/SLO | Runtime Truth | Source Acquisition | Compiler/Decompiler Fidelity | Studio Workflow | Generated Validation | Evidence Persistence | Score |',
  '| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |',
  ...summary.scoreRows.map((row) => {
    const a11y = row.area.a11y ? 'yes' : 'no';
    const i18n = row.area.i18n ? 'yes' : 'no';
    const ai = row.area.aiGovernance ? 'yes' : 'no';
    const perf = row.area.performanceSlo ? 'yes' : 'no';
    const rt = row.area.runtimeTruth ? 'yes' : 'no';
    const sourceAcquisition = row.area.artifactSourceAcquisition ? 'yes' : 'no';
    const compilerDecompiler = row.area.artifactCompilerDecompilerFidelity ? 'yes' : 'no';
    const studioWorkflow = row.area.artifactStudioWorkflow ? 'yes' : 'no';
    const generatedValidation = row.area.artifactGeneratedValidation ? 'yes' : 'no';
    const evidencePersistence = row.area.artifactEvidencePersistence ? 'yes' : 'no';
    return `| ${row.productId} | ${a11y} | ${i18n} | ${ai} | ${perf} | ${rt} | ${sourceAcquisition} | ${compilerDecompiler} | ${studioWorkflow} | ${generatedValidation} | ${evidencePersistence} | ${row.score.passedAreas}/${row.score.totalAreas} |`;
  }),
  '',
  'Legend: score reflects currently-detected gate signals, not full product certification status.',
  '',
  ];
  return `${markdownLines.join('\n')}\n`;
}

function writeScorecard(summary) {
  mkdirSync(evidenceDir, { recursive: true });
  writeFileSync(evidencePath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');

  mkdirSync(releaseEvidenceDir, { recursive: true });
  writeFileSync(releaseMarkdownPath, renderWave2ProductQualityScorecardMarkdown(summary), 'utf8');
}

function checkScorecard(summary) {
  const expectedJson = `${JSON.stringify(summary, null, 2)}\n`;
  const expectedMarkdown = renderWave2ProductQualityScorecardMarkdown(summary);
  const currentJson = existsSync(evidencePath) ? readFileSync(evidencePath, 'utf8') : '';
  const currentMarkdown = existsSync(releaseMarkdownPath) ? readFileSync(releaseMarkdownPath, 'utf8') : '';
  const failures = [];

  if (currentJson !== expectedJson) {
    failures.push(path.relative(repoRoot, evidencePath));
  }
  if (currentMarkdown !== expectedMarkdown) {
    failures.push(path.relative(repoRoot, releaseMarkdownPath));
  }

  if (failures.length > 0) {
    throw new Error(`Wave 2 product quality scorecard is stale: ${failures.join(', ')}`);
  }
}

function parseArgs(argv) {
  return {
    check: argv.includes('--check'),
  };
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const summary = buildWave2ProductQualityScorecard();

  if (args.check) {
    checkScorecard(summary);
    console.log('Wave 2 scorecard is current.');
    return;
  }

  writeScorecard(summary);
  console.log(`Generated Wave 2 scorecard: ${path.relative(repoRoot, evidencePath)}`);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  try {
    main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exit(1);
  }
}
