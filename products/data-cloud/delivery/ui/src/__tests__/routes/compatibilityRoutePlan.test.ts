import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');
const deprecationPlan = readFileSync(
  path.resolve(__dirname, '../../../docs/COMPATIBILITY_ROUTE_DEPRECATION_PLAN.md'),
  'utf8',
);

describe('compatibility route deprecation plan', () => {
  it('keeps canonical alias inventory aligned with declared compatibility routes', () => {
    const expectedAliases = [
      'dashboard',
      'hub',
      'collections',
      'collections/new',
      'collections/:id',
      'collections/:id/edit',
      'datasets',
      'lineage',
      'quality',
      'workflows',
      'workflows/new',
      'workflows/:id',
      'sql',
      'governance',
      'brain',
      'dashboards',
      'cost',
    ] as const;

    for (const alias of expectedAliases) {
      expect(routesSource).toContain(`path: '${alias}'`);
      expect(deprecationPlan).toContain(`/${alias}`);
    }
  });

  it('documents measurable retirement gates before alias removal', () => {
    expect(deprecationPlan).toContain('Retirement Gates');
    expect(deprecationPlan).toContain('rolling 30-day window');
    expect(deprecationPlan).toContain('near-zero');
    expect(deprecationPlan).toContain('explicit 410 responses');
  });

  it('wraps every non-redirect compatibility alias with RoleProtectedRoute', () => {
    // Aliases that are Navigate redirects — no RoleProtectedRoute needed.
    const navigateAliases = ['lineage', 'quality'];

    // All other compat aliases that render page components must be wrapped.
    const protectedAliases = [
      'dashboard',
      'hub',
      'collections',
      'collections/new',
      'collections/:id',
      'collections/:id/edit',
      'datasets',
      'workflows',
      'workflows/new',
      'workflows/:id',
      'sql',
      'governance',
      'brain',
      'dashboards',
      'cost',
    ] as const;

    for (const alias of navigateAliases) {
      // Navigate aliases should NOT use RoleProtectedRoute — they just redirect.
      const aliasLine = routesSource
        .split('\n')
        .find((line) => line.includes(`path: '${alias}'`));
      expect(aliasLine).toBeDefined();
      expect(aliasLine).not.toContain('RoleProtectedRoute');
    }

    for (const alias of protectedAliases) {
      // Each protected alias must have a RoleProtectedRoute on the same line.
      const aliasLine = routesSource
        .split('\n')
        .find((line) => line.includes(`path: '${alias}'`));
      expect(
        aliasLine,
        `Compatibility alias '${alias}' must be wrapped with RoleProtectedRoute`,
      ).toBeDefined();
      expect(
        aliasLine,
        `Compatibility alias '${alias}' missing RoleProtectedRoute — add routePath matching the canonical route`,
      ).toContain('RoleProtectedRoute');
    }
  });

  it('routes every page alias to a canonical routePath', () => {
    // Verify each class of aliases resolves to the expected canonical path.
    const canonicalMappings: Array<{ alias: string; canonicalPath: string }> = [
      { alias: 'dashboard', canonicalPath: 'routePath="/"' },
      { alias: 'hub', canonicalPath: 'routePath="/"' },
      { alias: 'collections', canonicalPath: 'routePath="/data"' },
      { alias: 'datasets', canonicalPath: 'routePath="/data"' },
      { alias: 'workflows', canonicalPath: 'routePath="/pipelines"' },
      { alias: 'sql', canonicalPath: 'routePath="/query"' },
      { alias: 'governance', canonicalPath: 'routePath="/trust"' },
      { alias: 'brain', canonicalPath: 'routePath="/insights"' },
      { alias: 'cost', canonicalPath: 'routePath="/insights"' },
    ];

    for (const { alias, canonicalPath } of canonicalMappings) {
      const aliasLine = routesSource
        .split('\n')
        .find((line) => line.includes(`path: '${alias}'`));
      expect(
        aliasLine,
        `Alias '${alias}' must declare ${canonicalPath}`,
      ).toContain(canonicalPath);
    }
  });
});
