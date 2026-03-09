/**
 * E2E Tests for AI Copilot Streaming
 * 
 * Tests real-time AI response streaming via WebSocket
 */

import { test, expect } from '@playwright/test';
import {
    login,
    navigateToPersonaDashboard,
    waitForWebSocket,
    waitForCopilotStream,
    mockWebSocketMessage,
} from '../utils/helpers';

test.describe('AI Copilot Streaming', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
    });

    test('should establish WebSocket connection on copilot panel open', async ({
        page,
    }) => {
        await navigateToPersonaDashboard(page, 'developer');

        // Open copilot panel with Cmd+K
        await page.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');

        // Wait for WebSocket connection
        await waitForWebSocket(page);

        // Check connection status indicator
        const statusIndicator = page.locator('[data-testid="ws-status"]');
        await expect(statusIndicator).toHaveAttribute('data-status', 'connected');
    });

    test('should stream AI response token by token', async ({ page }) => {
        await navigateToPersonaDashboard(page, 'developer');

        // Open copilot panel
        await page.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');
        await waitForWebSocket(page);

        // Type a message
        const input = page.locator('[data-testid="copilot-input"]');
        await input.fill('What are the current blockers?');
        await input.press('Enter');

        // Mock WebSocket streaming messages
        const sessionId = 'test-session-123';
        const chunks = ['There', ' are', ' 3', ' critical', ' blockers', '.'];

        for (const chunk of chunks) {
            await mockWebSocketMessage(page, {
                type: 'copilot_chunk',
                data: { chunk, sessionId },
            });
            await page.waitForTimeout(100); // Simulate streaming delay
        }

        // Send completion message
        await mockWebSocketMessage(page, {
            type: 'copilot_complete',
            data: { sessionId },
        });

        // Verify full text is displayed
        const message = page.locator('[data-testid="copilot-message"]:last-child');
        await expect(message).toContainText('There are 3 critical blockers.');

        // Verify streaming indicator is gone
        const streamingIndicator = page.locator('[data-testid="streaming-cursor"]');
        await expect(streamingIndicator).not.toBeVisible();
    });

    test('should handle streaming errors gracefully', async ({ page }) => {
        await navigateToPersonaDashboard(page, 'developer');

        // Open copilot panel
        await page.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');
        await waitForWebSocket(page);

        // Start streaming
        await page.locator('[data-testid="copilot-input"]').fill('Test message');
        await page.keyboard.press('Enter');

        // Mock streaming error
        const sessionId = 'test-session-123';
        await mockWebSocketMessage(page, {
            type: 'copilot_error',
            data: {
                sessionId,
                error: {
                    code: 'STREAMING_ERROR',
                    message: 'Connection lost',
                },
            },
        });

        // Verify error is displayed
        const errorMessage = page.locator('[data-testid="copilot-error"]');
        await expect(errorMessage).toBeVisible();
        await expect(errorMessage).toContainText('Connection lost');

        // Verify retry button is available
        const retryButton = page.locator('[data-testid="retry-button"]');
        await expect(retryButton).toBeVisible();
    });

    test('should auto-reconnect on WebSocket disconnect', async ({ page }) => {
        await navigateToPersonaDashboard(page, 'developer');

        // Open copilot panel
        await page.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');
        await waitForWebSocket(page);

        // Verify initial connection
        const statusIndicator = page.locator('[data-testid="ws-status"]');
        await expect(statusIndicator).toHaveAttribute('data-status', 'connected');

        // Simulate disconnect
        await page.evaluate(() => {
            (window as unknown).__WS_CONNECTED = false;
        });

        // Check reconnecting status
        await expect(statusIndicator).toHaveAttribute('data-status', 'connecting');

        // Wait for reconnection (with timeout)
        await page.waitForFunction(
            () => (window as unknown).__WS_CONNECTED === true,
            { timeout: 10000 }
        );

        // Verify reconnected
        await expect(statusIndicator).toHaveAttribute('data-status', 'connected');
    });

    test('should display connection statistics', async ({ page }) => {
        await navigateToPersonaDashboard(page, 'developer');

        // Navigate to admin panel
        await page.goto('/admin/websocket-stats');

        // Wait for stats to load
        await page.waitForSelector('[data-testid="ws-stats"]');

        // Verify stats are displayed
        const totalConnections = page.locator('[data-testid="total-connections"]');
        const totalSubscriptions = page.locator('[data-testid="total-subscriptions"]');

        await expect(totalConnections).toBeVisible();
        await expect(totalSubscriptions).toBeVisible();

        // Verify stats have numeric values
        await expect(totalConnections).toHaveText(/^\d+$/);
        await expect(totalSubscriptions).toHaveText(/^\d+$/);
    });

    test('should handle multiple concurrent streams', async ({ page, context }) => {
        // Open two browser tabs
        const page1 = page;
        const page2 = await context.newPage();

        // Login both pages
        await login(page1);
        await login(page2);

        // Open copilot on both
        await navigateToPersonaDashboard(page1, 'developer');
        await page1.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');
        await waitForWebSocket(page1);

        await navigateToPersonaDashboard(page2, 'security');
        await page2.keyboard.press(process.platform === 'darwin' ? 'Meta+K' : 'Control+K');
        await waitForWebSocket(page2);

        // Send messages on both
        await page1.locator('[data-testid="copilot-input"]').fill('Message 1');
        await page1.keyboard.press('Enter');

        await page2.locator('[data-testid="copilot-input"]').fill('Message 2');
        await page2.keyboard.press('Enter');

        // Mock responses for both sessions
        await mockWebSocketMessage(page1, {
            type: 'copilot_chunk',
            data: { chunk: 'Response 1', sessionId: 'session-1' },
        });
        await mockWebSocketMessage(page1, {
            type: 'copilot_complete',
            data: { sessionId: 'session-1' },
        });

        await mockWebSocketMessage(page2, {
            type: 'copilot_chunk',
            data: { chunk: 'Response 2', sessionId: 'session-2' },
        });
        await mockWebSocketMessage(page2, {
            type: 'copilot_complete',
            data: { sessionId: 'session-2' },
        });

        // Verify both streams work independently
        await expect(page1.locator('[data-testid="copilot-message"]:last-child')).toContainText(
            'Response 1'
        );
        await expect(page2.locator('[data-testid="copilot-message"]:last-child')).toContainText(
            'Response 2'
        );

        await page2.close();
    });
});
