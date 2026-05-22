#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const packageJsonPath = path.join(repoRoot, 'package.json');
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'wave2-product-quality-scorecard.json');
const releaseEvidenceDir = path.join(repoRoot, 'release-evidence');
const releaseMarkdownPath = path.join(releaseEvidenceDir, 'wave2-product-quality-scorecard.md');

const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
const scripts = packageJson.scripts ?? {};
const registry = loadCanonicalRegistry(repoRoot);

const businessProducts = Object.values(registry)
  .filter((product) => product.kind === 'business-product')
  .filter((product) => product.metadata?.status === 'active')
  .map((product) => ({
    id: product.id,
    surfaces: product.surfaces ?? [],
  }))
  .sort((a, b) => a.id.localeCompare(b.id));

function readJson(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

function hasTaggedA11ySpec(productId) {
  const candidatesByProduct = {
    phr: ['products/phr/apps/web/tests/e2e/phr-a11y.spec.ts'],
    'digital-marketing': [
      'products/digital-marketing/ui/e2e/a11y.spec.ts',
      'products/digital-marketing/ui/e2e/accessibility.spec.ts',
    ],
  };

  const candidates = candidatesByProduct[productId] ?? [];
  for (const relativePath of candidates) {
    const absolutePath = path.join(repoRoot, relativePath);
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

const platformGateMap = {
  a11y: typeof scripts['check:data-cloud-ui-a11y'] === 'string',
  productA11yRouteMatrix: typeof scripts['check:product-a11y-route-matrix'] === 'string',
  i18n: typeof scripts['check:i18n-conformance'] === 'string',
  aiGovernance: typeof scripts['check:ai-governance-conformance'] === 'string',
  performanceSlo: typeof scripts['check:audited-performance-workflows'] === 'string',
  runtimeTruth: typeof scripts['check:interaction-runtime-truth'] === 'string',
};

const accessibilityWorkflow = existsSync(path.join(repoRoot, '.github/workflows/accessibility.yml'))
  ? readFileSync(path.join(repoRoot, '.github/workflows/accessibility.yml'), 'utf8')
  : '';

const scoreRows = businessProducts.map((product) => {
  const web = hasWebSurface(product);
  const webSurface = product.surfaces.find((surface) => surface.type === 'web');
  const packagePath = webSurface?.packagePath;
  const packageJson = packagePath ? readJson(packagePath) : null;
  const lifecycleEnabled = (registry[product.id]?.lifecycle?.enabled === true)
    || (registry[product.id]?.lifecycleExecutionAllowed === true);

  const a11yPathToken = webSurface?.path ? `'${webSurface.path}/**'` : null;
  const a11yProductSignal = web
    ? Boolean(
      packageJson?.scripts?.['test:e2e:a11y']
      && hasTaggedA11ySpec(product.id)
      && a11yPathToken
      && accessibilityWorkflow.includes(a11yPathToken),
    )
    : true;

  const i18nProductSignal = web
    ? (product.id === 'digital-marketing'
      ? existsSync(path.join(repoRoot, 'products/digital-marketing/ui/src/lib/i18n/format.ts'))
      : product.id === 'phr'
        ? existsSync(path.join(repoRoot, 'products/phr/apps/web/src/lib/i18n.ts'))
          || existsSync(path.join(repoRoot, 'products/phr/apps/web/src/i18n/config.ts'))
        : true)
    : true;

  const aiProductSignal = existsSync(path.join(repoRoot, '.github/workflows/agent-eval.yml'));
  const perfProductSignal = existsSync(path.join(repoRoot, '.github/workflows/performance-budgets.yml'));
  const runtimeTruthSignal = typeof scripts['check:interaction-runtime-truth'] === 'string';

  const area = {
    a11y: lifecycleEnabled ? (web ? (platformGateMap.a11y && platformGateMap.productA11yRouteMatrix && a11yProductSignal) : true) : true,
    i18n: lifecycleEnabled ? (web ? platformGateMap.i18n && i18nProductSignal : true) : true,
    aiGovernance: platformGateMap.aiGovernance && aiProductSignal,
    performanceSlo: platformGateMap.performanceSlo && perfProductSignal,
    runtimeTruth: platformGateMap.runtimeTruth && runtimeTruthSignal,
  };

  const passedAreas = Object.values(area).filter(Boolean).length;
  const totalAreas = Object.keys(area).length;

  return {
    productId: product.id,
    webSurface: web,
    area,
    score: {
      passedAreas,
      totalAreas,
      ratio: Number((passedAreas / totalAreas).toFixed(2)),
    },
  };
});

const summary = {
  generatedAt: new Date().toISOString(),
  platformGateMap,
  productCount: scoreRows.length,
  scoreRows,
};

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(evidencePath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');

mkdirSync(releaseEvidenceDir, { recursive: true });
const markdownLines = [
  '# Wave 2 Product Quality Scorecard',
  '',
  `- Generated at: ${summary.generatedAt}`,
  `- Products evaluated: ${summary.productCount}`,
  '',
  '| Product | A11y | i18n | AI Governance | Performance/SLO | Runtime Truth | Score |',
  '| --- | --- | --- | --- | --- | --- | --- |',
  ...scoreRows.map((row) => {
    const a11y = row.area.a11y ? 'yes' : 'no';
    const i18n = row.area.i18n ? 'yes' : 'no';
    const ai = row.area.aiGovernance ? 'yes' : 'no';
    const perf = row.area.performanceSlo ? 'yes' : 'no';
    const rt = row.area.runtimeTruth ? 'yes' : 'no';
    return `| ${row.productId} | ${a11y} | ${i18n} | ${ai} | ${perf} | ${rt} | ${row.score.passedAreas}/${row.score.totalAreas} |`;
  }),
  '',
  'Legend: score reflects currently-detected gate signals, not full product certification status.',
  '',
];
writeFileSync(releaseMarkdownPath, `${markdownLines.join('\n')}\n`, 'utf8');

console.log(`Generated Wave 2 scorecard: ${path.relative(repoRoot, evidencePath)}`);
