#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    name: 'PHR route manifest',
    file: 'products/phr/apps/web/src/routeManifest.tsx',
    required: [
      'ProductRouteCapability',
      'minimumRole',
      'personas',
      'tiers',
      'emergencyAction',
      'phrRouteManifest',
    ],
  },
  {
    name: 'PHR route consumption',
    file: 'products/phr/apps/web/src/routes.tsx',
    required: ['phrRouteManifest'],
  },
  {
    name: 'DMOS route manifest',
    file: 'products/digital-marketing/ui/src/routeManifest.tsx',
    required: [
      'ProductRouteCapability',
      'minimumRole',
      'personas',
      'tiers',
      'actions',
      'dmosRouteManifest',
      'featureFlagKey',
    ],
  },
  {
    name: 'DMOS route consumption',
    file: 'products/digital-marketing/ui/src/App.tsx',
    required: ['dmosRouteManifest', 'GuardedProductRoute', 'FeatureUnavailablePage'],
    forbidden: ['path="/workspaces/:workspaceId/dashboard"', 'path="/workspaces/:workspaceId/approvals"'],
  },
  {
    name: 'FlashIt route manifest',
    file: 'products/flashit/client/web/src/routeManifest.tsx',
    required: [
      'ProductRouteCapability',
      'minimumRole',
      'personas',
      'tiers',
      'actions',
      'flashitRouteManifest',
      'getFlashitNavigationRoutes',
    ],
  },
  {
    name: 'FlashIt route consumption',
    file: 'products/flashit/client/web/src/App.tsx',
    required: ['flashitRouteManifest.map'],
    forbidden: ['path="/capture"', 'path="/moments"', 'path="/analytics"'],
  },
  {
    name: 'FlashIt navigation composition',
    file: 'products/flashit/client/web/src/components/Layout.tsx',
    required: ['getFlashitNavigationRoutes', 'iconRegistry'],
    forbidden: ['const navItems = ['],
  },
];

const errors = [];

for (const check of checks) {
  const absolutePath = path.join(repoRoot, check.file);
  const source = readFileSync(absolutePath, 'utf8');

  for (const token of check.required) {
    if (!source.includes(token)) {
      errors.push(`${check.name} is missing required token "${token}" in ${check.file}`);
    }
  }

  for (const token of check.forbidden ?? []) {
    if (source.includes(token)) {
      errors.push(`${check.name} still contains forbidden inline routing token "${token}" in ${check.file}`);
    }
  }
}

if (errors.length > 0) {
  console.error('Route entitlement contract check failed:\n');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log('Route entitlement contract check passed.');
