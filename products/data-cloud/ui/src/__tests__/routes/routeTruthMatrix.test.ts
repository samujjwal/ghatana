import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');
const shellSource = readFileSync(path.resolve(__dirname, '../../layouts/DefaultLayout.tsx'), 'utf8');
const globalSearchSource = readFileSync(path.resolve(__dirname, '../../components/common/GlobalSearch.tsx'), 'utf8');
const routeTruthMatrix = readFileSync(
  path.resolve(__dirname, '../../../../ROUTE_TRUTH_MATRIX_2026-04-17.md'),
  'utf8',
);

describe('route truth matrix', () => {
  it('captures canonical primary routes in the committed artifact', () => {
    expect(routesSource).toContain("path: 'data'");
    expect(routesSource).toContain("path: 'new'");
    expect(routesSource).toContain("path: ':id/edit'");
    expect(routesSource).toContain("path: 'pipelines'");
    expect(routesSource).toContain("path: 'query'");
    expect(routesSource).toContain("path: 'trust'");

    expect(routeTruthMatrix).toContain('| `/data` | primary-user | `live` |');
    expect(routeTruthMatrix).toContain('| `/data/new` | primary-user | `live` |');
    expect(routeTruthMatrix).toContain('| `/data/:id/edit` | primary-user | `live` |');
    expect(routeTruthMatrix).toContain('| `/pipelines` | primary-user | `live` |');
    expect(routeTruthMatrix).toContain('| `/alerts` | operator | `live` |');
    expect(routeTruthMatrix).toContain('| `/fabric` | operator | `preview` |');
    expect(routesSource).toContain("path: 'lineage', element: <Navigate to=\"/data?view=lineage\" replace />");
    expect(routesSource).toContain("path: 'quality', element: <Navigate to=\"/data?view=quality\" replace />");
  });

  it('keeps unsupported routes out of default discovery surfaces', () => {
    expect(shellSource).toContain("label: 'Alerts'");
    expect(shellSource).toContain("minimumShellRole: 'operator'");
    expect(shellSource).not.toContain("label: 'Fabric'");
    expect(globalSearchSource).not.toContain("id: 'nav-alerts'");
    expect(globalSearchSource).toContain("id: 'nav-query'");
    expect(globalSearchSource).toContain("id: 'nav-trust'");
  });
});