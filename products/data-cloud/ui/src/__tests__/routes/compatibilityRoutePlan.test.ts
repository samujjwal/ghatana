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
});
