/**
 * E2E Tests for AI Rate Limiting
 * 
 * Tests per-user AI quotas and rate limit enforcement
 */

import { test, expect } from '@playwright/test';
import { login, checkRateLimit } from '../utils/helpers';

test.describe('AI Rate Limiting', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('should track AI request quotas', async ({ page }) => {
        // Make AI request
        await page.request.post('/api/ai/copilot', {
            data: {
                message: 'Test message',
                sessionId: 'test-session',
            },
        });

        // Check rate limit headers
        const rateLimit = await checkRateLimit(page, '/api/ai/copilot');

        expect(rateLimit.remaining).toBeLessThan(rateLimit.limit);
        expect(rateLimit.limit).toBeGreaterThan(0);
        expect(rateLimit.resetAt).toBeTruthy();
    });

    test('should enforce per-minute rate limits', async ({ page }) => {
        const endpoint = '/api/ai/copilot';

        // Get initial limit
        const initialLimit = await checkRateLimit(page, endpoint);
        const requestsToMake = initialLimit.limit;

        // Exhaust rate limit
        for (let i = 0; i < requestsToMake; i++) {
            await page.request.post(endpoint, {
                data: {
                    message: `Test ${i}`,
                    sessionId: 'test-session',
                },
            });
        }

        // Next request should be rate limited
        const response = await page.request.post(endpoint, {
            data: {
                message: 'This should fail',
                sessionId: 'test-session',
            },
        });

        expect(response.status()).toBe(429);

        const body = await response.json();
        expect(body.error).toBe('AI Rate Limit Exceeded');
        expect(body.retryAfter).toBeGreaterThan(0);
    });

    test('should show rate limit exceeded UI', async ({ page }) => {
        // Navigate to copilot
        await page.goto('/copilot');

        // Mock rate limit exceeded response
        await page.route('**/api/ai/copilot', (route) => {
            route.fulfill({
                status: 429,
                contentType: 'application/json',
                body: JSON.stringify({
                    error: 'AI Rate Limit Exceeded',
                    message: 'Token quota exceeded: tokens per day',
                    retryAfter: 3600,
                    remaining: {
                        requests: 0,
                        tokens: 0,
                        cost: 5.0,
                    },
                }),
            });
        });

        // Try to send message
        await page.locator('[data-testid="copilot-input"]').fill('Test message');
        await page.keyboard.press('Enter');

        // Verify error message is displayed
        const errorMessage = page.locator('[data-testid="rate-limit-error"]');
        await expect(errorMessage).toBeVisible();
        await expect(errorMessage).toContainText('Token quota exceeded');

        // Verify retry timer is shown
        const retryTimer = page.locator('[data-testid="retry-timer"]');
        await expect(retryTimer).toBeVisible();
    });

    test('should display usage statistics', async ({ page }) => {
        await page.goto('/settings/usage');

        // Wait for usage stats to load
        await page.waitForSelector('[data-testid="usage-stats"]');

        // Verify requests stat
        const requestsUsed = page.locator('[data-testid="requests-used"]');
        const requestsLimit = page.locator('[data-testid="requests-limit"]');
        await expect(requestsUsed).toBeVisible();
        await expect(requestsLimit).toBeVisible();

        // Verify tokens stat
        const tokensUsed = page.locator('[data-testid="tokens-used"]');
        const tokensLimit = page.locator('[data-testid="tokens-limit"]');
        await expect(tokensUsed).toBeVisible();
        await expect(tokensLimit).toBeVisible();

        // Verify cost stat
        const costUsed = page.locator('[data-testid="cost-used"]');
        const costLimit = page.locator('[data-testid="cost-limit"]');
        await expect(costUsed).toBeVisible();
        await expect(costLimit).toBeVisible();
    });

    test('should show upgrade prompt when quota exceeded', async ({ page, context }) => {
        // Mock free tier user with exhausted quota
        await context.addCookies([
            {
                name: 'user_tier',
                value: 'free',
                domain: 'localhost',
                path: '/',
            },
        ]);

        await page.goto('/copilot');

        // Mock rate limit response
        await page.route('**/api/ai/copilot', (route) => {
            route.fulfill({
                status: 429,
                contentType: 'application/json',
                body: JSON.stringify({
                    error: 'AI Rate Limit Exceeded',
                    message: 'Daily quota exceeded',
                    retryAfter: 86400,
                }),
            });
        });

        // Try to use copilot
        await page.locator('[data-testid="copilot-input"]').fill('Test');
        await page.keyboard.press('Enter');

        // Verify upgrade prompt is shown
        const upgradePrompt = page.locator('[data-testid="upgrade-prompt"]');
        await expect(upgradePrompt).toBeVisible();
        await expect(upgradePrompt).toContainText('Upgrade to Pro');

        // Verify upgrade button is clickable
        const upgradeButton = page.locator('[data-testid="upgrade-button"]');
        await expect(upgradeButton).toBeEnabled();
    });
});
