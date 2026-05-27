#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const productRoot = path.resolve(__dirname, '..');
const docPath = path.join(productRoot, 'docs', 'PRODUCT_FAMILY_FEATURE_CONTRACT.md');

const requiredSections = [
  '## Purpose',
  '## Ownership Boundary',
  '## Canonical Routes',
  '## Data Sources',
  '## Promotion Write Path',
  '## Permissions',
  '## UI Behavior',
  '## Failure Semantics',
  '## Validation Evidence',
  '## Change Rules',
];

const requiredMarkers = [
  'ProductFamilyControlPlaneController',
  'ProductFamilyService',
  'ProductFamilyControlPlanePage',
  'product-family:control-plane',
  'product_family_assets',
  'product_family_asset_history',
  'product_release_readiness',
  'product_family_reuse_recommendations',
  'yappc_truth_checks',
  '/api/v1/yappc/product-family/releases/{productKey}',
  '/api/v1/yappc/product-family/assets/{assetId}/promotions',
  'getProductFamilyReleaseReadiness',
  'listProductFamilyAssets',
  'promoteProductFamilyAsset',
  'listProductFamilyDocTruthWarnings',
  'listProductFamilyReuseRecommendations',
  'getProductFamilyKernelTimeline',
  'ProductFamilyControlPlaneControllerTest',
  'product-family-gate.test.tsx',
  'ProductFamilyControlPlanePage.accessibility.test.tsx',
];

function fail(message) {
  console.error(`[product-family-docs] ${message}`);
  process.exitCode = 1;
}

if (!fs.existsSync(docPath)) {
  fail(`Missing ${path.relative(process.cwd(), docPath)}`);
  process.exit();
}

const markdown = fs.readFileSync(docPath, 'utf8');

for (const section of requiredSections) {
  if (!markdown.includes(section)) {
    fail(`Missing section ${section}`);
  }
}

for (const marker of requiredMarkers) {
  if (!markdown.includes(marker)) {
    fail(`Missing evidence marker ${marker}`);
  }
}

const boundarySection = markdown.split('## Ownership Boundary')[1]?.split('## Canonical Routes')[0] ?? '';
for (const owner of ['YAPPC', 'Data Cloud', 'Kernel', 'Frontend']) {
  if (!boundarySection.includes(owner)) {
    fail(`Ownership boundary must include ${owner}`);
  }
}

if (!markdown.includes('Viewer-only users must not see the product-family page or execute promotion actions')) {
  fail('Permissions section must state the viewer denial behavior');
}

if (process.exitCode) {
  process.exit(process.exitCode);
}

console.log('[product-family-docs] product-family purpose, boundary, routes, data, permissions, and validation evidence are documented.');
