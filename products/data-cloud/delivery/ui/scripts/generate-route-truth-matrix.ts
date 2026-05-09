import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  canonicalRouteSurfaceRegistry,
  getRouteSurfacesByLifecycle,
  type RouteLifecycle,
} from '../src/lib/routing/RouteSurfaceRegistry';

const lifecycleOrder: RouteLifecycle[] = [
  'active',
  'preview',
  'boundary',
  'deprecated',
  'redirect',
  'removed',
];

const lifecycleBadge: Record<RouteLifecycle, string> = {
  active: '✅ active',
  preview: '🔶 preview',
  boundary: '🚧 boundary',
  deprecated: '⚠️ deprecated',
  redirect: '↪ redirect',
  removed: '❌ removed',
};

const targetPath = resolve(process.cwd(), 'docs/ROUTE_TRUTH_MATRIX.md');
const checkOnly = process.argv.includes('--check');

function generateMarkdown(): string {
  const rows = Object.entries(canonicalRouteSurfaceRegistry)
    .map(([key, route]) => {
      const capabilities = route.capabilities.length > 0 ? route.capabilities.join(', ') : '—';
      return `| \`${key}\` | \`${route.path}\` | ${route.label} | ${route.minimumShellRole} | ${lifecycleBadge[route.lifecycle]} | ${route.discoverable ? 'Yes' : 'No'} | ${capabilities} |`;
    })
    .join('\n');

  const grouped = getRouteSurfacesByLifecycle();
  const lifecycleSummary = lifecycleOrder
    .map((lifecycle) => `- ${lifecycle}: ${grouped[lifecycle].length}`)
    .join('\n');

  return [
    '# Data Cloud Route Truth Matrix',
    '',
    '> **Generated from**: `src/lib/routing/RouteSurfaceRegistry.ts` — `canonicalRouteSurfaceRegistry`',
    '> **Generation command**: `pnpm --filter @data-cloud/ui docs:routes:generate`',
    '',
    '## Route Matrix',
    '',
    '| Key | Path | Label | Min Role | Lifecycle | Discoverable | Capabilities |',
    '|-----|------|-------|----------|-----------|--------------|--------------|',
    rows,
    '',
    '## Lifecycle Counts',
    '',
    lifecycleSummary,
    '',
    '## Notes',
    '',
    '- Boundary routes are intentionally hidden from standard navigation by `getDiscoverableRoutes()` unless `includesBoundary=true` is passed.',
    '- Update the canonical registry first; this document is generated from registry state.',
    '',
  ].join('\n');
}

const nextContent = generateMarkdown();

if (checkOnly) {
  const currentContent = readFileSync(targetPath, 'utf8');
  if (currentContent !== nextContent) {
    console.error('ROUTE_TRUTH_MATRIX.md is out of date. Run: pnpm --filter @data-cloud/ui docs:routes:generate');
    process.exit(1);
  }
  console.log('ROUTE_TRUTH_MATRIX.md is up to date.');
  process.exit(0);
}

writeFileSync(targetPath, nextContent, 'utf8');
console.log(`Generated ${targetPath}`);
