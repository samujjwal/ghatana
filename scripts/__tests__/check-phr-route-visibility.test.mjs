/**
 * Tests for PHR route visibility check script
 */

import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';
import { mkdtempSync, rmSync, writeFileSync } from 'fs';
import { tmpdir } from 'os';
import { join, resolve } from 'path';
import { execFileSync } from 'child_process';

const SCRIPT_PATH = resolve(process.cwd(), 'scripts/check-phr-route-visibility.mjs');

describe('check-phr-route-visibility', () => {
  let tempDir;

  afterEach(() => {
    if (tempDir) {
      rmSync(tempDir, { recursive: true, force: true });
      tempDir = undefined;
    }
  });

  function writeFixture({ contract, routes, elements }) {
    tempDir = mkdtempSync(join(tmpdir(), 'phr-route-visibility-'));
    const contractPath = join(tempDir, 'phr-route-contract.json');
    const routeMapPath = join(tempDir, 'routes.tsx');
    const routeElementsPath = join(tempDir, 'phrRouteElements.tsx');
    writeFileSync(contractPath, JSON.stringify(contract, null, 2));
    writeFileSync(routeMapPath, routes);
    writeFileSync(routeElementsPath, elements);

    return {
      PHR_ROUTE_CONTRACT_PATH: contractPath,
      PHR_ROUTE_MAP_PATH: routeMapPath,
      PHR_ROUTE_ELEMENTS_PATH: routeElementsPath,
    };
  }

  function runCheck(env) {
    return execFileSync(process.execPath, [SCRIPT_PATH], {
      cwd: process.cwd(),
      env: { ...process.env, ...env },
      encoding: 'utf-8',
      stdio: ['ignore', 'pipe', 'pipe'],
    });
  }

  function routeGuardFixture(extraBody = 'return route.element;') {
    return `
      const phrBrowserRouteManifest = phrRoutePlugin
        .getBrowserRoutes()
        .map(attachPhrRouteElement);
      const protectedRoutes = [
        ...phrBrowserRouteManifest.map(protectedRoute)
      ];
      function ProtectedPhrRoute({ route, role }) {
        if (route.stability === 'hidden' || route.stability === 'deferred' || route.stability === 'removed') {
          return <Navigate to="/not-found" replace />;
        }
        if (route.stability === 'blocked' || route.stability === 'preview') {
          return <Navigate to="/forbidden" replace />;
        }
        if (!phrRoutePlugin.isAllowedForRole(route, role)) {
          return <Navigate to="/forbidden" replace />;
        }
        ${extraBody}
      }
    `;
  }

  it('passes when canonical route projection, hidden exclusion, and stable page mappings are present', () => {
    const env = writeFixture({
      contract: {
        schemaVersion: '1.0.0',
        product: 'phr',
        routes: [
          { path: '/dashboard', stability: 'stable', surface: ['web'] },
          { path: '/forbidden', stability: 'stable', surface: ['web'] },
          { path: '/not-found', stability: 'stable', surface: ['web'] },
          { path: '/hidden-route', stability: 'hidden', surface: ['web'] },
        ],
      },
      routes: routeGuardFixture(),
      elements: `
        export const routeElements = {
          '/dashboard': <DashboardPage />,
          '/forbidden': <ForbiddenPage />,
          '/not-found': <NotFoundPage />,
        };
      `,
    });

    const output = runCheck(env);
    assert.match(output, /Route visibility check passed/);
  });

  it('fails when a hidden route is exposed as a static navigation target', () => {
    const env = writeFixture({
      contract: {
        schemaVersion: '1.0.0',
        product: 'phr',
        routes: [
          { path: '/dashboard', stability: 'stable', surface: ['web'] },
          { path: '/hidden-route', stability: 'hidden', surface: ['web'] },
        ],
      },
      routes: routeGuardFixture('return <a href="/hidden-route">Hidden</a>;'),
      elements: `
        export const routeElements = {
          '/dashboard': <DashboardPage />,
        };
      `,
    });

    assert.throws(() => runCheck(env), /Hidden routes found as static navigation targets/);
  });

  it('fails when a stable product route maps to NotFoundPage instead of a page element', () => {
    const env = writeFixture({
      contract: {
      schemaVersion: '1.0.0',
      product: 'phr',
      routes: [
        { path: '/dashboard', stability: 'stable', surface: ['web'] },
      ],
      },
      routes: routeGuardFixture(),
      elements: `
        export const routeElements = {
          '/dashboard': <NotFoundPage />,
        };
      `,
    });

    assert.throws(() => runCheck(env), /Stable routes missing concrete page element mappings/);
  });
});
