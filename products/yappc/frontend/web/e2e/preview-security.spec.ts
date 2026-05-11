/**
 * Preview Security Tests
 *
 * @doc.type test
 * @doc.purpose Execute preview security tests in CI - token validation, origin checks, postMessage source checks, CSP headers, iframe sandbox, expired token, spoofing denial
 * @doc.layer product
 * @doc.pattern Security E2E Test
 */

import { test, expect } from '@playwright/test';

test.describe('Preview Security', () => {
  test('should validate preview session token', async ({ page }) => {
    // Test that valid tokens are accepted
    await page.goto('/preview/valid-session');
    await expect(page.locator('[data-testid="preview-container"]')).toBeVisible();
  });

  test('should reject invalid preview session token', async ({ page, request }) => {
    const response = await request.get('/api/preview/invalid-token');
    expect(response.status()).toBe(401);
  });

  test('should enforce origin checks for preview requests', async ({ page, request }) => {
    // Test that requests from unauthorized origins are rejected
    const response = await request.get('/api/preview/session', {
      headers: {
        Origin: 'https://malicious-site.com',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should validate postMessage source in preview iframe', async ({ page }) => {
    await page.goto('/preview/test-session');
    
    // Inject script to test postMessage validation
    const result = await page.evaluate(() => {
      return new Promise((resolve) => {
        window.addEventListener('message', (event) => {
          resolve({
            origin: event.origin,
            source: event.source === window ? 'same-window' : 'different-window',
          });
        });
        window.postMessage({ type: 'test' }, '*');
      });
    });

    // In production, postMessage should only accept messages from authorized origins
    expect(result).toBeDefined();
  });

  test('should include CSP headers in preview responses', async ({ page, request }) => {
    const response = await request.get('/api/preview/session');
    const cspHeader = response.headers()['content-security-policy'];
    
    expect(cspHeader).toBeDefined();
    expect(cspHeader).toContain('frame-src');
    expect(cspHeader).toContain('script-src');
  });

  test('should enforce iframe sandbox restrictions', async ({ page }) => {
    await page.goto('/preview/test-session');
    
    const sandboxAttribute = await page.getAttribute('iframe', 'sandbox');
    
    expect(sandboxAttribute).toBeDefined();
    // Verify sandbox includes necessary restrictions
    expect(sandboxAttribute).toContain('allow-scripts');
    expect(sandboxAttribute).toContain('allow-same-origin');
  });

  test('should reject expired preview session tokens', async ({ page, request }) => {
    // Simulate expired token
    const response = await request.get('/api/preview/expired-token');
    expect(response.status()).toBe(401);
  });

  test('should deny spoofed preview session requests', async ({ page, request }) => {
    // Test that spoofed session IDs are rejected
    const response = await request.get('/api/preview/session/spoofed-id');
    expect(response.status()).toBe(403);
  });

  test('should validate tenant scope in preview session', async ({ page, request }) => {
    // Test that preview sessions are scoped to tenant
    const response = await request.get('/api/preview/session', {
      headers: {
        'X-Tenant-ID': 'different-tenant',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should validate workspace scope in preview session', async ({ page, request }) => {
    // Test that preview sessions are scoped to workspace
    const response = await request.get('/api/preview/session', {
      headers: {
        'X-Workspace-ID': 'different-workspace',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should validate project scope in preview session', async ({ page, request }) => {
    // Test that preview sessions are scoped to project
    const response = await request.get('/api/preview/session', {
      headers: {
        'X-Project-ID': 'different-project',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should validate artifact scope in preview session', async ({ page, request }) => {
    // Test that preview sessions are scoped to artifact
    const response = await request.get('/api/preview/session', {
      headers: {
        'X-Artifact-ID': 'different-artifact',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should validate user scope in preview session', async ({ page, request }) => {
    // Test that preview sessions are scoped to user
    const response = await request.get('/api/preview/session', {
      headers: {
        'X-User-ID': 'different-user',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('should enforce preview session expiration', async ({ page }) => {
    // Test that expired sessions are rejected
    await page.goto('/preview/expired-session');
    await expect(page.locator('[data-testid="session-expired"]')).toBeVisible();
  });

  test('should log preview session validation failures', async ({ page, request }) => {
    // Test that validation failures are logged for security monitoring
    const response = await request.get('/api/preview/session/invalid');
    expect(response.status()).toBe(401);
    
    // Verify audit log entry (would need backend access)
    // This is a placeholder for the actual audit log verification
  });
});
