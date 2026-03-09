/**
 * E2E Test Utilities for AI Features
 * 
 * Provides helpers for testing AI streaming, WebSocket connections,
 * and other AI-specific functionality.
 */

import { Page, expect } from '@playwright/test';

/**
 * Wait for WebSocket connection to be established
 */
export async function waitForWebSocket(page: Page, timeout = 5000): Promise<void> {
    await page.waitForFunction(
        () => {
            return (window as unknown).__WS_CONNECTED === true;
        },
        { timeout }
    );
}

/**
 * Mock AI API responses
 */
export async function mockAIResponse(
    page: Page,
    endpoint: string,
    response: unknown
): Promise<void> {
    await page.route(`**/api/ai/${endpoint}`, (route) => {
        route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(response),
        });
    });
}

/**
 * Mock WebSocket messages
 */
export async function mockWebSocketMessage(
    page: Page,
    message: {
        type: string;
        data?: unknown;
    }
): Promise<void> {
    await page.evaluate((msg) => {
        const event = new MessageEvent('message', {
            data: JSON.stringify({
                ...msg,
                timestamp: Date.now(),
            }),
        });
        (window as unknown).__WS_MOCK_MESSAGE?.(event);
    }, message);
}

/**
 * Wait for copilot streaming to complete
 */
export async function waitForCopilotStream(
    page: Page,
    sessionId: string,
    timeout = 30000
): Promise<string> {
    return await page.waitForFunction(
        (sid) => {
            const state = (window as unknown).__COPILOT_STATE?.[sid];
            return state?.isStreaming === false && state?.fullText;
        },
        sessionId,
        { timeout }
    ).then(() => {
        return page.evaluate((sid) => {
            return (window as unknown).__COPILOT_STATE?.[sid]?.fullText;
        }, sessionId);
    });
}

/**
 * Wait for anomaly alert to appear
 */
export async function waitForAnomalyAlert(
    page: Page,
    timeout = 10000
): Promise<unknown> {
    await page.waitForSelector('[data-testid="anomaly-alert"]', { timeout });
    return await page.evaluate(() => {
        const alert = document.querySelector('[data-testid="anomaly-alert"]');
        return {
            title: alert?.querySelector('[data-testid="anomaly-title"]')?.textContent,
            severity: alert?.getAttribute('data-severity'),
            type: alert?.getAttribute('data-type'),
        };
    });
}

/**
 * Login helper
 */
export async function login(
    page: Page,
    email = 'test@example.com',
    password = 'password'
): Promise<void> {
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', email);
    await page.fill('[data-testid="password-input"]', password);
    await page.click('[data-testid="login-button"]');
    await page.waitForURL('/dashboard');
}

/**
 * Navigate to specific persona dashboard
 */
export async function navigateToPersonaDashboard(
    page: Page,
    persona: 'developer' | 'architect' | 'security' | 'devops' | 'operator'
): Promise<void> {
    await page.goto(`/devsecops/${persona}`);
    await expect(page.locator('h1')).toContainText(
        persona.charAt(0).toUpperCase() + persona.slice(1)
    );
}

/**
 * Wait for AI insight to appear
 */
export async function waitForAIInsight(
    page: Page,
    type: string,
    timeout = 10000
): Promise<unknown> {
    await page.waitForSelector(`[data-testid="ai-insight"][data-type="${type}"]`, {
        timeout,
    });
    return await page.evaluate((insightType) => {
        const insight = document.querySelector(
            `[data-testid="ai-insight"][data-type="${insightType}"]`
        );
        return {
            title: insight?.querySelector('[data-testid="insight-title"]')?.textContent,
            description: insight?.querySelector('[data-testid="insight-description"]')
                ?.textContent,
            confidence: insight?.getAttribute('data-confidence'),
        };
    }, type);
}

/**
 * Wait for prediction to update
 */
export async function waitForPredictionUpdate(
    page: Page,
    targetId: string,
    timeout = 10000
): Promise<unknown> {
    return await page.waitForFunction(
        (id) => {
            const state = (window as unknown).__PREDICTION_STATE?.[id];
            return state?.prediction;
        },
        targetId,
        { timeout }
    ).then(() => {
        return page.evaluate((id) => {
            return (window as unknown).__PREDICTION_STATE?.[id]?.prediction;
        }, targetId);
    });
}

/**
 * Check rate limit headers
 */
export async function checkRateLimit(page: Page, endpoint: string): Promise<{
    remaining: number;
    limit: number;
    resetAt: string;
}> {
    const response = await page.request.get(endpoint);
    return {
        remaining: parseInt(response.headers()['x-ratelimit-remaining'] || '0'),
        limit: parseInt(response.headers()['x-ratelimit-limit'] || '0'),
        resetAt: response.headers()['x-ratelimit-reset'] || '',
    };
}

/**
 * Clear all mocks
 */
export async function clearAllMocks(page: Page): Promise<void> {
    await page.unroute('**/*');
}

/**
 * Take a screenshot with timestamp
 */
export async function takeTimestampedScreenshot(
    page: Page,
    name: string
): Promise<void> {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    await page.screenshot({
        path: `test-results/screenshots/${name}-${timestamp}.png`,
        fullPage: true,
    });
}
