#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();

const checks = [
  {
    name: 'Shared product-shell entitlement contract',
    file: 'platform/typescript/product-shell/src/types.ts',
    required: [
      'ProductRouteEntitlement',
      'product',
      'principalId',
      'tenantId',
      'role',
      'persona',
      'tier',
      'routes',
      'actions',
      'cards',
      'ProductEntitledAction',
      'ProductEntitledCard',
    ],
  },
  {
    name: 'PHR route manifest',
    file: 'products/phr/apps/web/src/routeManifest.tsx',
    required: [
      'ProductRouteCapability',
      'minimumRole',
      'personas',
      'tiers',
      'actions',
      'cards',
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
    name: 'PHR backend route entitlement API',
    file: 'products/phr/src/main/java/com/ghatana/phr/api/PhrHttpServer.java',
    required: [
      '/route-entitlements',
      'handleRouteEntitlements',
      '"product", "phr"',
      '"principalId"',
      '"tenantId"',
      '"role"',
      '"persona"',
      '"tier"',
      '"routes"',
      '"actions"',
      '"cards"',
      'phrRoutesFor',
    ],
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
      'cards',
      'dmosRouteManifest',
      'capabilityKey',
    ],
  },
  {
    name: 'DMOS route consumption',
    file: 'products/digital-marketing/ui/src/App.tsx',
    required: ['dmosRouteManifest', 'GuardedProductRoute', 'FeatureUnavailablePage'],
    forbidden: ['path="/workspaces/:workspaceId/dashboard"', 'path="/workspaces/:workspaceId/approvals"'],
  },
  {
    name: 'DMOS backend route entitlement API',
    file: 'products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosRouteEntitlementServlet.java',
    required: [
      '/v1/route-entitlements',
      'handleRouteEntitlements',
      '"product", "digital-marketing"',
      '"principalId"',
      '"tenantId"',
      '"role"',
      '"persona"',
      '"tier"',
      '"routes"',
      '"actions"',
      '"cards"',
      'routesFor',
    ],
  },
  {
    name: 'DMOS backend route entitlement registration',
    file: 'products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java',
    required: ['DmosRouteEntitlementServlet', 'new DmosRouteEntitlementServlet(eventloop)'],
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
      'cards',
      'flashitRouteManifest',
      'getFlashitNavigationRoutes',
    ],
  },
  {
    name: 'FlashIt route access helpers',
    file: 'products/flashit/client/web/src/routeAccess.ts',
    required: ['resolveFlashitRole', 'isRouteAllowedForRole', 'FLASHIT_ROLE_ORDER'],
  },
  {
    name: 'FlashIt route consumption',
    file: 'products/flashit/client/web/src/App.tsx',
    required: ['flashitRouteManifest.map', 'isRouteAllowedForRole', 'resolveFlashitRole', 'FlashitAccessDenied'],
    forbidden: ['path="/capture"', 'path="/moments"', 'path="/analytics"'],
  },
  {
    name: 'FlashIt navigation composition',
    file: 'products/flashit/client/web/src/components/Layout.tsx',
    required: ['FlashitProductShell'],
    forbidden: ['const navItems = ['],
  },
  {
    name: 'FlashIt shared shell composition',
    file: 'products/flashit/client/web/src/components/FlashitProductShell.tsx',
    required: ['ProductShell', 'flashitRouteManifest', '@ghatana/product-shell'],
  },
  {
    name: 'FlashIt backend route entitlement API',
    file: 'products/flashit/backend/gateway/src/routes/entitlements.ts',
    required: [
      '/route-entitlements',
      'ProductRouteEntitlement-shaped',
      "product: 'flashit'",
      'principalId',
      'tenantId',
      'role',
      'persona',
      'tier',
      'routes: allowedRoutes',
      'actions: allowedRoutes.flatMap',
      'cards: allowedRoutes.flatMap',
    ],
  },
  {
    name: 'FlashIt backend route entitlement registration',
    file: 'products/flashit/backend/gateway/src/server.ts',
    required: ['entitlementRoutes', 'prefix: "/api/entitlements"'],
  },
];

/**
 * Behavioral test coverage checks.
 * Each entry asserts that a co-located behavioral test file exists AND
 * that it imports real production code (not object-literal theater).
 */
const behavioralCoverageChecks = [
  {
    name: 'FlashIt entitlements behavioral test',
    testFile: 'products/flashit/backend/gateway/src/routes/__tests__/entitlements.test.ts',
    // Must import the real route module, not a hand-rolled stub
    required: [
      "from '../entitlements.js'",
      'route-entitlements',
      'principalId',
      'role',
      'tier',
      'routes',
    ],
  },
  {
    name: 'FlashIt data-access-context behavioral test',
    testFile: 'products/flashit/backend/gateway/src/lib/__tests__/data-access-context.test.ts',
    required: [
      "from '../../lib/data-access-context.js'",
      'buildFlashItDataAccessContext',
      'FlashItDataAccessContextError',
      'requireIdempotencyKey',
    ],
  },
  {
    name: 'FlashIt idempotency behavioral test',
    testFile: 'products/flashit/backend/gateway/src/lib/__tests__/idempotency.test.ts',
    required: [
      "from '../../idempotency.js'",
      'checkIdempotency',
      'found: false',
      'found: true',
    ],
  },
];

import { existsSync } from 'node:fs';

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

for (const check of behavioralCoverageChecks) {
  const absolutePath = path.join(repoRoot, check.testFile);
  if (!existsSync(absolutePath)) {
    errors.push(`${check.name}: behavioral test file is missing — ${check.testFile}`);
    continue;
  }
  const testSource = readFileSync(absolutePath, 'utf8');
  for (const token of check.required) {
    if (!testSource.includes(token)) {
      errors.push(`${check.name}: behavioral test is missing evidence of "${token}" in ${check.testFile}`);
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

console.log('Route entitlement contract check passed (token + behavioral coverage).');

