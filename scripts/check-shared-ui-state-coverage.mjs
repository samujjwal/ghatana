#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const productShape = JSON.parse(
  readFileSync(path.join(repoRoot, 'config/product-shape.json'), 'utf8'),
);

const visualSpecCandidates = [
  'e2e/visual-regression.spec.ts',
  'e2e/11-visual-regression.spec.ts',
  'tests/e2e/phr-visual-regression.spec.ts',
];

const a11ySpecCandidates = [
  'e2e/a11y.spec.ts',
  'e2e/09-accessibility.spec.ts',
  'tests/e2e/phr-a11y.spec.ts',
  'e2e/accessibility.spec.ts',
];

const errors = [];

function getCanonicalWebPackage(productConfig) {
  return (productConfig.clientPackages ?? []).find((candidate) =>
    /(apps\/web|client\/web|\/ui)\/package\.json$/.test(candidate),
  );
}

function walk(relativeDir) {
  const absoluteDir = path.join(repoRoot, relativeDir);
  if (!existsSync(absoluteDir)) {
    return [];
  }

  const files = [];

  for (const entry of readdirSync(absoluteDir)) {
    const absoluteEntry = path.join(absoluteDir, entry);
    const relativeEntry = path.relative(repoRoot, absoluteEntry);
    const stats = statSync(absoluteEntry);
    if (stats.isDirectory()) {
      files.push(...walk(relativeEntry));
      continue;
    }
    files.push(relativeEntry);
  }

  return files;
}

function read(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function findExisting(baseDir, candidates) {
  return candidates.find((candidate) => existsSync(path.join(repoRoot, baseDir, candidate)));
}

function hasTokenInFiles(files, matcher) {
  return files.some((file) => matcher.test(read(file)));
}

for (const [productName, productConfig] of Object.entries(productShape.products)) {
  if (!productConfig.ui) {
    continue;
  }

  const packageJsonPath = getCanonicalWebPackage(productConfig);
  if (!packageJsonPath) {
    errors.push(`${productName} must declare a canonical web client package in config/product-shape.json`);
    continue;
  }

  const clientDir = path.dirname(packageJsonPath);
  const allFiles = [
    ...walk(path.join(clientDir, 'src')),
    ...walk(path.join(clientDir, 'tests')),
    ...walk(path.join(clientDir, 'e2e')),
  ].filter((file) => /\.(ts|tsx|js|jsx)$/.test(file));

  const visualSpec = findExisting(clientDir, visualSpecCandidates);
  if (!visualSpec) {
    errors.push(`${productName} must include a shared visual regression spec under ${clientDir}`);
  }

  const a11ySpec = findExisting(clientDir, a11ySpecCandidates);
  if (!a11ySpec) {
    errors.push(`${productName} must include a shared accessibility spec under ${clientDir}`);
  }

  if (visualSpec) {
    const visualSource = read(path.join(clientDir, visualSpec));
    if (!/@visual/.test(visualSource)) {
      errors.push(`${productName} visual regression spec must be tagged with @visual in ${path.join(clientDir, visualSpec)}`);
    }
    if (!/dashboard|app shell|shell/i.test(visualSource)) {
      errors.push(`${productName} visual regression coverage must include a dashboard or shell baseline in ${path.join(clientDir, visualSpec)}`);
    }
  }

  if (a11ySpec) {
    const a11ySource = read(path.join(clientDir, a11ySpec));
    if (!/@a11y/.test(a11ySource)) {
      errors.push(`${productName} accessibility spec must be tagged with @a11y in ${path.join(clientDir, a11ySpec)}`);
    }
  }

  const genericStateChecks = [
    {
      label: 'loading',
      matcher: /\bloading\b|aria-busy|skeleton/i,
    },
    {
      label: 'empty',
      matcher: /\bempty state\b|no .* yet|no data available|start capturing your first moment/i,
    },
    {
      label: 'error',
      matcher: /\berror handling\b|failed to|network error|error:/i,
    },
  ];

  for (const check of genericStateChecks) {
    if (!hasTokenInFiles(allFiles, check.matcher)) {
      errors.push(`${productName} must include ${check.label} state coverage in tests or UI contracts under ${clientDir}`);
    }
  }

  const permissionMatchersByProduct = {
    phr: [/permission denied/i, /minimumRole/i, /not available for the current persona/i],
    'digital-marketing': [/GuardedProductRoute/i, /FeatureUnavailablePage/i, /minimumRole/i],
    flashit: [/onUnauthorized/i, /Navigate to="\/login"/i, /minimumRole/i],
  };

  const permissionMatchers = permissionMatchersByProduct[productName] ?? [/permission denied|unauthorized|forbidden/i];
  const hasPermissionCoverage = permissionMatchers.every((matcher) => hasTokenInFiles(allFiles, matcher));

  if (!hasPermissionCoverage) {
    errors.push(`${productName} must include permission-denied or unauthorized route coverage evidence under ${clientDir}`);
  }
}

if (errors.length > 0) {
  console.error('Shared UI state coverage check failed:\n');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('Shared UI state coverage check passed.');
