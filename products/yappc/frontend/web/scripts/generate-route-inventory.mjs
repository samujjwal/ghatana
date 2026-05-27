#!/usr/bin/env node
/**
 * Route Inventory Generator
 *
 * Parses src/routes.ts and generates a markdown inventory of all mounted routes,
 * expected user actions, and focused coverage files.
 * Can be run as:
 *   node scripts/generate-route-inventory.mjs
 *   node scripts/generate-route-inventory.mjs --check
 *
 * --check validates the generated inventory against the existing inventory file
 * and exits with non-zero if they differ.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const ROUTES_FILE = path.join(ROOT, 'src', 'routes.ts');
const INVENTORY_FILE = path.join(ROOT, 'docs', 'route-inventory.md');

const ROUTE_METADATA = new Map([
  routeMetadata('preview/builder', 'routes/preview-builder.tsx', {
    owner: 'Preview runtime',
    auth: 'Public iframe runtime',
    nav: 'Hidden',
    featureFlag: 'None',
    expectedActions: ['Render generated builder preview'],
    coverage: ['src/routes/preview-builder.tsx'],
  }),
  routeMetadata('/', 'routes/dashboard.tsx', {
    owner: 'Lifecycle dashboard',
    auth: 'Public shell entry',
    nav: 'Primary entry',
    featureFlag: 'None',
    expectedActions: ['Open dashboard', 'Start or resume workspace flow'],
    coverage: ['src/__tests__/routes.spec.ts'],
  }),
  routeMetadata('login', 'routes/login.tsx', {
    owner: 'Auth',
    auth: 'Guest',
    nav: 'Auth entry',
    featureFlag: 'None',
    expectedActions: ['Sign in', 'Recover authenticated session'],
    coverage: ['src/__tests__/routes.spec.ts'],
  }),
  routeMetadata('onboarding', 'routes/onboarding.tsx', {
    owner: 'Onboarding',
    auth: 'Authenticated',
    nav: 'Post-login flow',
    featureFlag: 'None',
    expectedActions: ['Complete onboarding checklist', 'Choose workspace/project starting point'],
    coverage: ['src/components/onboarding/__tests__/EndToEndOnboarding.test.tsx'],
  }),
  routeMetadata('workspaces', 'routes/app/workspaces.tsx', {
    owner: 'Workspace',
    auth: 'Authenticated',
    nav: 'App shell',
    featureFlag: 'None',
    expectedActions: ['Create workspace', 'Select workspace'],
    coverage: ['src/components/workspace/__tests__/CreateWorkspaceDialog.test.tsx'],
  }),
  routeMetadata('projects', 'routes/app/projects.tsx', {
    owner: 'Project',
    auth: 'Authenticated',
    nav: 'App shell',
    featureFlag: 'None',
    expectedActions: ['Create project', 'Open project'],
    coverage: ['src/components/workspace/__tests__/CreateProjectDialog.test.tsx'],
  }),
  routeMetadata('profile', 'routes/profile.tsx', {
    owner: 'User settings',
    auth: 'Authenticated',
    nav: 'User menu',
    featureFlag: 'None',
    expectedActions: ['View profile', 'Update profile settings'],
    coverage: ['src/__tests__/routes.spec.ts'],
  }),
  routeMetadata('settings', 'routes/settings.tsx', {
    owner: 'Workspace settings',
    auth: 'Authenticated',
    nav: 'User menu',
    featureFlag: 'None',
    expectedActions: ['Open workspace settings', 'Update workspace preferences'],
    coverage: ['src/__tests__/routes.spec.ts'],
  }),
  routeMetadata('p/:projectId', 'routes/app/project/_shell.tsx', {
    owner: 'Project shell',
    auth: 'Authenticated project access',
    nav: 'Project shell',
    featureFlag: 'Phase-specific flags',
    expectedActions: ['Navigate phase tabs', 'Open project settings', 'Open intent drawer'],
    coverage: ['src/routes/app/project/__tests__/shell.test.tsx'],
  }),
  routeMetadata('p/:projectId', 'routes/app/project/index.tsx', {
    owner: 'Project shell',
    auth: 'Authenticated project access',
    nav: 'Redirect/default',
    featureFlag: 'None',
    expectedActions: ['Redirect to Intent phase'],
    coverage: ['src/routes/app/project/__tests__/index.test.tsx'],
  }),
  routeMetadata('p/:projectId/intent', 'routes/app/project/intent.tsx', {
    owner: 'Intent phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'intent',
    expectedActions: ['Capture intent notes', 'Open intent workspace', 'Define requirements'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/shape', 'routes/app/project/shape.tsx', {
    owner: 'Shape phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'shape',
    expectedActions: ['Open canvas workspace', 'Review shape contract', 'Start builder review'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/validate', 'routes/app/project/validate.tsx', {
    owner: 'Validate phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'validate',
    expectedActions: ['Review approval gates', 'Approve lifecycle transition'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/generate', 'routes/app/project/generate.tsx', {
    owner: 'Generate phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'generate',
    expectedActions: ['Start generation', 'Review diff', 'Apply/reject/rollback generated changes'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/run', 'routes/app/project/run.tsx', {
    owner: 'Run phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'run',
    expectedActions: ['Start run workflow', 'Retry run', 'Rollback run', 'Promote run'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/observe', 'routes/app/project/observe.tsx', {
    owner: 'Observe phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'observe',
    expectedActions: ['Inspect preview diagnostics', 'Review runtime health', 'Review recommendations'],
    coverage: ['src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx'],
  }),
  routeMetadata('p/:projectId/learn', 'routes/app/project/learn.tsx', {
    owner: 'Learn phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'learn',
    expectedActions: ['Review learning evidence', 'Inspect agent governance state'],
    coverage: ['src/routes/app/project/__tests__/PhaseStatusPanels.test.tsx'],
  }),
  routeMetadata('p/:projectId/evolve', 'routes/app/project/evolve.tsx', {
    owner: 'Evolve phase',
    auth: 'Authenticated project access',
    nav: 'Project phase tab',
    featureFlag: 'evolve',
    expectedActions: ['Review evolution proposal', 'Inspect impact analysis', 'Approve or reject diff'],
    coverage: ['src/routes/app/project/__tests__/PhaseStatusPanels.test.tsx'],
  }),
  routeMetadata('p/:projectId/settings', 'routes/app/project/settings.tsx', {
    owner: 'Project settings',
    auth: 'Authenticated project access',
    nav: 'Project settings action',
    featureFlag: 'None',
    expectedActions: ['Update project metadata', 'Manage project access settings'],
    coverage: ['src/routes/app/project/__tests__/settings.test.tsx'],
  }),
  routeMetadata('p/:projectId/canvas', 'routes/app/project/canvas.tsx', {
    owner: 'Legacy Shape canvas',
    auth: 'Authenticated project access',
    nav: 'Deep link only',
    featureFlag: 'legacy route policy',
    expectedActions: ['Open compatibility canvas surface'],
    coverage: ['src/routes/app/project/__tests__/canvas.integration.test.tsx'],
  }),
  routeMetadata('p/:projectId/preview', 'routes/app/project/preview.tsx', {
    owner: 'Legacy preview',
    auth: 'Authenticated project access',
    nav: 'Deep link only',
    featureFlag: 'legacy route policy',
    expectedActions: ['Open compatibility preview surface'],
    coverage: ['src/routes/app/project/__tests__/preview.test.tsx'],
  }),
  routeMetadata('p/:projectId/deploy', 'routes/app/project/deploy.tsx', {
    owner: 'Legacy deploy',
    auth: 'Authenticated project access',
    nav: 'Deep link only',
    featureFlag: 'legacy route policy',
    expectedActions: ['Open compatibility deploy surface'],
    coverage: ['src/routes/app/project/__tests__/deploy.test.tsx'],
  }),
  routeMetadata('p/:projectId/lifecycle', 'routes/app/project/lifecycle.tsx', {
    owner: 'Legacy lifecycle',
    auth: 'Authenticated project access',
    nav: 'Deep link only',
    featureFlag: 'legacy route policy',
    expectedActions: ['Open compatibility lifecycle explorer'],
    coverage: ['src/routes/app/project/__tests__/lifecycle.test.tsx'],
  }),
  routeMetadata('kernel-health', 'routes/app/kernel-health.tsx', {
    owner: 'Kernel visibility',
    auth: 'OWNER/ADMIN capability',
    nav: 'Capability-gated app route',
    featureFlag: 'kernel visibility',
    expectedActions: ['Review ProductUnit health', 'Open product detail'],
    coverage: ['src/pages/kernel-health/__tests__/KernelHealthDashboardPage.test.tsx'],
  }),
  routeMetadata('kernel-health/products/:productUnitId', 'routes/app/kernel-health-product.tsx', {
    owner: 'Kernel visibility',
    auth: 'OWNER/ADMIN capability',
    nav: 'Kernel health detail',
    featureFlag: 'kernel visibility',
    expectedActions: ['Inspect lifecycle timeline', 'Inspect gates/artifacts/deployment details'],
    coverage: ['src/pages/kernel-health/__tests__/KernelHealthDashboardPage.test.tsx'],
  }),
  routeMetadata('product-family', 'routes/app/product-family.tsx', {
    owner: 'Product-family control plane',
    auth: 'product-family:control-plane capability',
    nav: 'Capability-gated app route',
    featureFlag: 'product family',
    expectedActions: ['Review assets/releases', 'Promote product-family asset'],
    coverage: ['src/routes/app/__tests__/product-family-gate.test.tsx'],
  }),
  routeMetadata('admin/prompt-versions', 'routes/app/admin/prompt-versions.tsx', {
    owner: 'Prompt admin',
    auth: 'OWNER/ADMIN capability',
    nav: 'Admin route',
    featureFlag: 'admin prompts',
    expectedActions: ['View prompt versions', 'Rollback prompt', 'Rebalance weights'],
    coverage: ['src/components/admin/__tests__/PromptVersionsPage.test.tsx'],
  }),
  routeMetadata('admin/ab-testing', 'routes/app/admin/ab-testing.tsx', {
    owner: 'Experiment admin',
    auth: 'OWNER/ADMIN capability',
    nav: 'Admin route',
    featureFlag: 'admin experiments',
    expectedActions: ['Create experiment', 'Promote winner', 'Pause experiment'],
    coverage: ['src/components/admin/__tests__/ABTestingDashboardPage.test.tsx'],
  }),
  routeMetadata('admin/feature-flags', 'routes/app/admin/feature-flags.tsx', {
    owner: 'Feature flag admin',
    auth: 'OWNER/ADMIN capability',
    nav: 'Admin route',
    featureFlag: 'admin feature flags',
    expectedActions: ['List flags', 'Update tenant flag', 'Review flag audit'],
    coverage: ['src/components/admin/__tests__/FeatureFlagsPage.test.tsx'],
  }),
  routeMetadata('admin/observability', 'routes/app/admin/observability.tsx', {
    owner: 'Admin observability',
    auth: 'OWNER/ADMIN capability',
    nav: 'Admin route',
    featureFlag: 'admin observability',
    expectedActions: ['Review SLO/cost/domain/OpenAPI release gates'],
    coverage: ['src/components/admin/__tests__/ObservabilityDashboard.test.tsx'],
  }),
  routeMetadata('/*', 'routes/not-found.tsx', {
    owner: 'Error handling',
    auth: 'Public',
    nav: 'Catch-all',
    featureFlag: 'None',
    expectedActions: ['Show not-found recovery navigation'],
    coverage: ['src/__tests__/routes.spec.ts'],
  }),
]);

function routeMetadata(pathSegment, file, metadata) {
  return [`${pathSegment}||${file}`, metadata];
}

function parseRoutes(content) {
  const lines = content.split('\n');
  const routes = [];
  const stack = [{ prefix: '', indent: 0 }];

  for (const rawLine of lines) {
    const line = rawLine.replace(/\/\/.*/, ''); // strip inline comments
    const indent = rawLine.search(/\S/);
    if (indent === -1) continue;

    // Pop stack to current indent level
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }

    const parent = stack[stack.length - 1];

    // route('path', 'file')
    const routeMatch = line.match(/route\(['"]([^'"]+)['"],\s*['"]([^'"]+)['"]\s*(?:,\s*\[)?/);
    if (routeMatch) {
      const pathSegment = routeMatch[1];
      const file = routeMatch[2];
      const fullPath =
        pathSegment === '*'
          ? `${parent.prefix}/*`
          : pathSegment === ''
            ? parent.prefix
            : `${parent.prefix}/${pathSegment}`.replace(/^\//, '');

      routes.push({ path: fullPath, file, children: line.includes('[') });

      if (line.includes('[')) {
        stack.push({ prefix: fullPath, indent });
      }
      continue;
    }

    // index('file')
    const indexMatch = line.match(/index\(['"]([^'"]+)['"]\)/);
    if (indexMatch) {
      const file = indexMatch[1];
      routes.push({ path: parent.prefix || '/', file, children: false });
      continue;
    }

    // layout('file', [ ... ]) — doesn't add a path prefix
    const layoutMatch = line.match(/layout\(['"]([^'"]+)['"],\s*\[/);
    if (layoutMatch) {
      stack.push({ prefix: parent.prefix, indent });
      continue;
    }
  }

  return routes;
}

function metadataFor(route) {
  return ROUTE_METADATA.get(`${route.path}||${route.file}`);
}

function validateMetadata(routes) {
  const routeKeys = new Set(routes.map((route) => `${route.path}||${route.file}`));
  const errors = [];

  for (const route of routes) {
    const metadata = metadataFor(route);
    if (!metadata) {
      errors.push(`Missing route metadata for ${route.path} (${route.file})`);
      continue;
    }

    if (!metadata.expectedActions.length) {
      errors.push(`Missing expected user actions for ${route.path} (${route.file})`);
    }

    for (const coverageFile of metadata.coverage) {
      if (!fs.existsSync(path.join(ROOT, coverageFile))) {
        errors.push(`Missing coverage file for ${route.path}: ${coverageFile}`);
      }
    }
  }

  for (const key of ROUTE_METADATA.keys()) {
    if (!routeKeys.has(key)) {
      errors.push(`Route metadata does not match a mounted route: ${key.replace('||', ' (')})`);
    }
  }

  return errors;
}

function joinList(values) {
  return values.map((value) => value.replaceAll('|', '\\|')).join('<br>');
}

function generationDate() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Los_Angeles',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function generateMarkdown(routes) {
  const now = generationDate();
  const lines = [
    '# YAPPC Route Inventory',
    '',
    `> Auto-generated from \`src/routes.ts\` on ${now}.`,
    '> Run `node scripts/generate-route-inventory.mjs` to regenerate.',
    '',
    '| # | URL Path | Route File | Owner | Auth | Nav | Feature Flag | Expected User Actions | Coverage |',
    '|---|----------|------------|-------|------|-----|--------------|-----------------------|----------|',
  ];

  routes.forEach((r, i) => {
    const metadata = metadataFor(r);
    if (!metadata) {
      throw new Error(`Missing route metadata for ${r.path} (${r.file})`);
    }

    lines.push(
      [
        `| ${i + 1}`,
        `\`${r.path}\``,
        `\`${r.file}\``,
        metadata.owner,
        metadata.auth,
        metadata.nav,
        metadata.featureFlag,
        joinList(metadata.expectedActions),
        joinList(metadata.coverage.map((coverageFile) => `\`${coverageFile}\``)),
      ].join(' | ') + ' |'
    );
  });

  lines.push('');
  return lines.join('\n');
}

function main() {
  if (!fs.existsSync(ROUTES_FILE)) {
    console.error(`Routes file not found: ${ROUTES_FILE}`);
    process.exit(1);
  }

  const content = fs.readFileSync(ROUTES_FILE, 'utf8');
  const routes = parseRoutes(content);
  const metadataErrors = validateMetadata(routes);
  if (metadataErrors.length > 0) {
    console.error('Route inventory metadata is incomplete:');
    for (const error of metadataErrors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
  }

  const markdown = generateMarkdown(routes);

  const checkMode = process.argv.includes('--check');

  if (checkMode) {
    if (!fs.existsSync(INVENTORY_FILE)) {
      console.error(`Inventory file not found: ${INVENTORY_FILE}`);
      process.exit(1);
    }
    const existing = fs.readFileSync(INVENTORY_FILE, 'utf8');
    // Compare ignoring the generation date line
    const stripDate = (s) => s.replace(/> Auto-generated from `src\/routes\.ts` on \d{4}-\d{2}-\d{2}\./, '');
    if (stripDate(existing) !== stripDate(markdown)) {
      console.error('Route inventory is out of date. Run `node scripts/generate-route-inventory.mjs` to regenerate.');
      process.exit(1);
    }
    console.log('Route inventory is up to date.');
    process.exit(0);
  }

  // Ensure docs directory exists
  const docsDir = path.dirname(INVENTORY_FILE);
  if (!fs.existsSync(docsDir)) {
    fs.mkdirSync(docsDir, { recursive: true });
  }

  fs.writeFileSync(INVENTORY_FILE, markdown);
  console.log(`Route inventory written to ${INVENTORY_FILE} (${routes.length} routes).`);
}

main();
