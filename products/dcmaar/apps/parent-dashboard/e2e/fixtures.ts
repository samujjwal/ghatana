import { expect, type Page } from '@playwright/test';

/**
 * Helper to register common mock routes used by dashboard e2e tests.
 * Tests should call registerMockRoutes(page) in their beforeEach to ensure
 * consistent mocked responses without exporting a Playwright `test` instance
 * from this module (which can create instance conflicts across the repo).
 */
export async function registerMockRoutes(page: Page) {
  // Mock authentication endpoints
  await page.route('**/api/auth/login', async (route) => {
    const request = route.request();
    // Some browsers/send contexts may not provide postDataJSON() (or it may be undefined)
    // Try to parse JSON first, otherwise fall back to raw postData parsing (JSON or form-encoded)
    let postData = request.postDataJSON?.();
    if (!postData) {
      const raw = request.postData();
      if (raw) {
        try {
          postData = JSON.parse(raw);
        } catch {
          // Try URLSearchParams (form-encoded)
          try {
            const params = new URLSearchParams(raw);
            postData = Object.fromEntries(params.entries());
          } catch {
            postData = undefined;
          }
        }
      }
    }

    if (postData && postData.email === 'parent@example.com' && postData.password === 'password123') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            accessToken: 'mock-token-12345',
            user: {
              id: 'user-123',
              email: 'parent@example.com',
              name: 'Test Parent',
              role: 'parent',
            },
          },
        }),
      });
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: { message: 'Invalid credentials', code: 'INVALID_CREDENTIALS' } }),
      });
    }
  });

  await page.route('**/api/auth/register', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        data: {
          accessToken: 'mock-token-12345',
          user: { id: 'user-123', email: 'parent@example.com', name: 'Test Parent', role: 'parent' },
        },
      }),
    });
  });

  // Children & child requests (for ChildRequests component)
  await page.route('**/api/children**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    const method = route.request().method();

    // GET /api/children
    if (path.endsWith('/children') && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              id: 'child-1',
              name: 'Alice',
              age: 12,
              is_active: true,
              created_at: new Date().toISOString(),
              updated_at: new Date().toISOString(),
            },
          ],
        }),
      });
      return;
    }

    // GET /api/children/:childId/requests
    if (/\/children\/[^/]+\/requests$/.test(path) && method === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: [
            {
              id: 'req-1',
              child_id: 'child-1',
              type: 'extend_session',
              status: 'pending',
              minutes_requested: 30,
              reason: 'Finished homework, want extra time',
              created_at: new Date().toISOString(),
            },
          ],
        }),
      });
      return;
    }

    // POST /api/children/:childId/requests/:requestId/decision
    if (/\/children\/[^/]+\/requests\/[^/]+\/decision$/.test(path) && method === 'POST') {
      // Accept any decision payload and return success
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true }),
      });
      return;
    }

    // Fallback
    await route.continue();
  });

  await page.route('**/api/auth/me', async (route) => {
    const headers = route.request().headers();
    const authHeader = headers['authorization'];
    if (authHeader && authHeader.includes('mock-token')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { id: 'user-123', email: 'parent@example.com', name: 'Test Parent', role: 'parent' } }),
      });
    } else {
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: { message: 'Unauthorized' } }) });
    }
  });

  // Devices
  await page.route('**/api/devices**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'device-1', name: "Child's iPhone", type: 'mobile', status: 'online', lastHeartbeat: new Date().toISOString(), registeredAt: new Date(Date.now() - 86400000 * 30).toISOString(), policies: ['policy-1', 'policy-2'] },
        { id: 'device-2', name: "Child's iPad", type: 'tablet', status: 'offline', lastHeartbeat: new Date(Date.now() - 3600000).toISOString(), registeredAt: new Date(Date.now() - 86400000 * 60).toISOString(), policies: ['policy-1'] },
      ]),
    });
  });

  // Policies
  await page.route('**/api/policies**', async (route) => {
    const method = route.request().method();
    if (method === 'GET') {
      await route.fulfill({
        status: 200, contentType: 'application/json', body: JSON.stringify([
          { id: 'policy-1', name: 'School Hours', type: 'time-limit', enabled: true, deviceIds: ['device-1', 'device-2'], restrictions: { maxUsageMinutes: 120, allowedHours: { start: '08:00', end: '15:00' } }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
          { id: 'policy-2', name: 'Bedtime', type: 'schedule', enabled: true, deviceIds: ['device-1'], restrictions: { allowedHours: { start: '07:00', end: '21:00' } }, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
        ])
      });
    } else if (method === 'POST') {
      const requestBody = route.request().postDataJSON();
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: `policy-${Date.now()}`, ...requestBody, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }) });
    } else if (method === 'PUT') {
      const requestBody = route.request().postDataJSON();
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...requestBody, updatedAt: new Date().toISOString() }) });
    } else if (method === 'DELETE') {
      await route.fulfill({ status: 204, contentType: 'application/json' });
    }
  });

  // Analytics
  await page.route('**/api/analytics**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: { screenTime: { daily: Array.from({ length: 7 }, (_, i) => ({ date: new Date(Date.now() - i * 86400000).toISOString().split('T')[0], hours: Math.random() * 5 + 2 })) }, appUsage: [{ name: 'Safari', hours: 2.5, category: 'Browsing' }, { name: 'YouTube', hours: 1.8, category: 'Entertainment' }, { name: 'Messages', hours: 1.2, category: 'Communication' }], violations: { count: 3, recent: [{ date: new Date().toISOString(), type: 'time_limit', app: 'TikTok' }] } } }) });
  });

  // WebSocket
  await page.route('**/ws/**', async (route) => {
    await route.abort('failed');
  });
}

export { expect };
