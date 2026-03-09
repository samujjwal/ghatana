import { test, expect } from '@playwright/test';

// Minimal route coverage tests.
// Assumptions: a workspace with id `ws-1` exists in the test mocks and a project id
// `proj-1` is a reasonable sample. These are the same sample ids used in other
// e2e specs (ws-1 appears throughout). If your mocks use different ids adjust
// the constants below.

const WS = 'ws-1';
const PROJ = 'proj-1';

const routesToCheck: { name: string; path: string }[] = [
  { name: 'root', path: '/' },
  { name: 'login', path: '/login' },
  { name: 'auth-login', path: '/auth/login' },
  { name: 'diagram', path: '/diagram' },
  { name: 'canvas-poc', path: '/canvas-poc' },
  { name: 'demo-build', path: '/app/project/demo/builds' },

  // App shell
  { name: 'workspaces', path: '/app/workspaces' },
  { name: 'workspace-projects', path: `/app/w/${WS}/projects` },
  { name: 'projects-root', path: '/app/projects' },
  { name: 'projects-new', path: '/app/projects/new' },

  // Direct project deep links (use sample ids)
  { name: 'project-overview-direct', path: `/app/w/${WS}/p/${PROJ}/overview` },
  { name: 'project-canvas-new-direct', path: `/app/w/${WS}/p/${PROJ}/canvas-new` },
  { name: 'project-build-direct', path: `/app/w/${WS}/p/${PROJ}/build` },
  { name: 'project-versions-direct', path: `/app/w/${WS}/p/${PROJ}/versions` },
  { name: 'project-settings-direct', path: `/app/w/${WS}/p/${PROJ}/settings` },
  { name: 'project-deploy-direct', path: `/app/w/${WS}/p/${PROJ}/deploy` },
  { name: 'project-monitor-direct', path: `/app/w/${WS}/p/${PROJ}/monitor` },

  // Mobile routes
  { name: 'mobile-projects', path: '/mobile/projects' },
  { name: 'mobile-overview-index', path: '/mobile/overview' },
  { name: 'mobile-overview', path: `/mobile/p/${PROJ}/overview` },
  { name: 'mobile-backlog', path: `/mobile/p/${PROJ}/backlog` },
  { name: 'mobile-notifications', path: '/mobile/notifications' },
];

for (const route of routesToCheck) {
  test.describe(route.name, () => {
    test(`loads ${route.path}`, async ({ page, baseURL }) => {
      // Use networkidle to give the app time to load; some routes lazy-load.
      const response = await page.goto(route.path, { waitUntil: 'networkidle' });

      // If we got an HTTP response ensure it's not a 4xx/5xx. If null, at least
      // ensure the page has a body element rendered.
      if (response) {
        expect(response.status()).toBeLessThan(400);
      } else {
        await expect(page.locator('body')).toBeVisible();
      }
    });
  });
}
