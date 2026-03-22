import { existsSync, readFileSync } from 'fs';
import path from 'path';

import { describe, it, expect } from 'vitest';

describe.skip('routes configuration (smoke)', () => {
  it('routes file exists or docs include routes marker', () => {
    const routesPath = path.resolve(__dirname, '..', 'routes.ts');
    const docPath = path.resolve(
      __dirname,
      '..',
      '..',
      '..',
      'docs',
      'ui-flow-react-router-framework.md'
    );

    const routesExists = existsSync(routesPath);
    const docsExists = existsSync(docPath);

    // At least one source of truth should be present: runtime routes or the docs
    if (!routesExists && !docsExists) {
      throw new Error(
        `Neither ${routesPath} nor ${docPath} exist. Add a routes file or update the docs.`
      );
    }

    if (docsExists) {
      const content = readFileSync(docPath, 'utf8');
      expect(
        content.includes('createBrowserRouter') ||
          content.includes('/app/w/:workspaceId/p/:projectId')
      ).toBe(true);
    }
  });

  it('every route has id and path and lazy entries are functions (if routes module exists)', async () => {
    const routesPath = path.resolve(__dirname, '..', 'routes.ts');
    if (!existsSync(routesPath)) return; // nothing to validate at runtime

    // dynamic import to avoid failing when module is missing
    const mod = await import('../routes').catch((err) => {
      throw new Error(`Failed to import routes module: ${err?.message || err}`);
    });

    const routes = mod?.routes || mod?.default;
    if (!Array.isArray(routes))
      throw new Error('routes export is not an array');

    function assertRoute(route: unknown) {
      expect(route).toBeTruthy();
      expect(route.id).toBeTruthy();
      // Index routes use 'index: true' instead of a path
      if (!route.index) {
        expect(route.path).toBeTruthy();
      }
      if (route.lazy) expect(typeof route.lazy).toBe('function');
      if (route.children) route.children.forEach(assertRoute);
    }

    routes.forEach(assertRoute);
  });
});
