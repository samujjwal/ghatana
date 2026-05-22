#!/usr/bin/env node

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { loadCanonicalRegistry } from './resolve-affected-products.mjs';

const repoRoot = process.cwd();
const evidenceDir = path.join(repoRoot, '.kernel/evidence');
const evidencePath = path.join(evidenceDir, 'product-a11y-route-matrix.json');

const violations = [];

function readJson(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return JSON.parse(readFileSync(absolutePath, 'utf8'));
}

function readText(relativePath) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    return null;
  }
  return readFileSync(absolutePath, 'utf8');
}

function hasA11ySpec(webRootPath) {
  const candidates = [
    path.join(webRootPath, 'e2e/a11y.spec.ts'),
    path.join(webRootPath, 'e2e/accessibility.spec.ts'),
    path.join(webRootPath, 'tests/e2e/a11y.spec.ts'),
    path.join(webRootPath, 'tests/e2e/accessibility.spec.ts'),
    path.join(webRootPath, 'tests/e2e/phr-a11y.spec.ts'),
  ];

  for (const relativeCandidate of candidates) {
    const absoluteCandidate = path.join(repoRoot, relativeCandidate);
    if (!existsSync(absoluteCandidate)) {
      continue;
    }
    const source = readFileSync(absoluteCandidate, 'utf8');
    if (source.includes('@a11y') || source.includes('accessibility')) {
      return { ok: true, path: relativeCandidate };
    }
  }

  return { ok: false, path: null };
}

const registry = loadCanonicalRegistry(repoRoot);
const accessibilityWorkflow = readText('.github/workflows/accessibility.yml') ?? '';

const scopedProducts = Object.values(registry)
  .filter((product) => product.kind === 'business-product')
  .filter((product) => product.metadata?.status === 'active')
  .filter((product) => product.lifecycle?.enabled === true || product.lifecycleExecutionAllowed === true)
  .filter((product) => (product.surfaces ?? []).some((surface) => surface.type === 'web'))
  .map((product) => {
    const webSurface = (product.surfaces ?? []).find((surface) => surface.type === 'web');
    return {
      productId: product.id,
      packagePath: webSurface?.packagePath,
      webPath: webSurface?.path,
    };
  })
  .sort((a, b) => a.productId.localeCompare(b.productId));

const rows = [];

for (const scopedProduct of scopedProducts) {
  const row = {
    productId: scopedProduct.productId,
    packagePath: scopedProduct.packagePath ?? null,
    webPath: scopedProduct.webPath ?? null,
    checks: {
      packageScript: false,
      routeSpec: false,
      workflowCoverage: false,
    },
    evidence: {
      a11ySpecPath: null,
    },
  };

  if (!scopedProduct.packagePath || !scopedProduct.webPath) {
    violations.push(`${scopedProduct.productId}: missing web surface packagePath/path in canonical product registry`);
    rows.push(row);
    continue;
  }

  const pkg = readJson(scopedProduct.packagePath);
  if (!pkg) {
    violations.push(`${scopedProduct.productId}: missing package file ${scopedProduct.packagePath}`);
  } else if (typeof pkg.scripts?.['test:e2e:a11y'] !== 'string') {
    violations.push(`${scopedProduct.productId}: package must define scripts.test:e2e:a11y in ${scopedProduct.packagePath}`);
  } else {
    row.checks.packageScript = true;
  }

  const routeSpec = hasA11ySpec(scopedProduct.webPath);
  if (!routeSpec.ok) {
    violations.push(`${scopedProduct.productId}: missing tagged accessibility e2e spec under ${scopedProduct.webPath}`);
  } else {
    row.checks.routeSpec = true;
    row.evidence.a11ySpecPath = routeSpec.path;
  }

  const pathToken = `'${scopedProduct.webPath}/**'`;
  const hasPathCoverage = accessibilityWorkflow.includes(pathToken);
  const hasA11yExecution = accessibilityWorkflow.includes('run test:e2e:a11y');
  const hasProductMatrixEntry = accessibilityWorkflow.includes(`product: ${scopedProduct.productId}`)
    || accessibilityWorkflow.includes(`product: ${scopedProduct.productId}-web`)
    || accessibilityWorkflow.includes('product: dmos-ui');
  const hasPackageCoverage = hasA11yExecution && hasProductMatrixEntry;
  if (!hasPathCoverage || !hasPackageCoverage) {
    violations.push(`${scopedProduct.productId}: accessibility workflow is missing matrix coverage for ${scopedProduct.webPath}`);
  } else {
    row.checks.workflowCoverage = true;
  }

  rows.push(row);
}

mkdirSync(evidenceDir, { recursive: true });
writeFileSync(
  evidencePath,
  `${JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      scopedProductCount: scopedProducts.length,
      rows,
      violations,
    },
    null,
    2,
  )}\n`,
  'utf8',
);

if (violations.length > 0) {
  console.error('Product accessibility route matrix check failed:\n');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  console.error(`\nEvidence written to ${path.relative(repoRoot, evidencePath)}`);
  process.exit(1);
}

console.log(`Product accessibility route matrix passed. Evidence: ${path.relative(repoRoot, evidencePath)}`);