/**
 * E2E Tests for Real-Time Anomaly Detection
 * 
 * Tests WebSocket-based anomaly alerts and notifications
 */

import { test, expect } from '@playwright/test';
import {
    login,
    navigateToPersonaDashboard,
    waitForWebSocket,
    waitForAnomalyAlert,
    mockWebSocketMessage,
} from '../utils/helpers';

test.describe('Anomaly Detection & Alerts', () => {
    test.beforeEach(async ({ page }) => {
        await login(page);
        await navigateToPersonaDashboard(page, 'devops');
    });

    test('should receive real-time anomaly alerts via WebSocket', async ({ page }) => {
        // Wait for WebSocket connection
        await waitForWebSocket(page);

        // Mock anomaly alert message
        await mockWebSocketMessage(page, {
            type: 'anomaly_alert',
            data: {
                id: 'anomaly-001',
                type: 'velocity',
                severity: 'high',
                title: 'Velocity Drop Detected',
                description: 'Team velocity dropped by 40% in the last sprint',
                affectedItems: ['item-1', 'item-2', 'item-3'],
            },
        });

        // Wait for alert to appear
        const alert = await waitForAnomalyAlert(page);

        // Verify alert content
        expect(alert.title).toBe('Velocity Drop Detected');
        expect(alert.severity).toBe('high');
        expect(alert.type).toBe('velocity');
    });

    test('should display anomaly count badge', async ({ page }) => {
        await waitForWebSocket(page);

        // Send multiple anomaly alerts
        for (let i = 1; i <= 3; i++) {
            await mockWebSocketMessage(page, {
                type: 'anomaly_alert',
                data: {
                    id: `anomaly-00${i}`,
                    type: 'velocity',
                    severity: 'high',
                    title: `Alert ${i}`,
                    description: `Description ${i}`,
                    affectedItems: [],
                },
            });
            await page.waitForTimeout(200);
        }

        // Verify unread count
        const badge = page.locator('[data-testid="anomaly-count-badge"]');
        await expect(badge).toHaveText('3');
    });

    test('should dismiss individual anomaly alerts', async ({ page }) => {
        await waitForWebSocket(page);

        // Send anomaly alert
        await mockWebSocketMessage(page, {
            type: 'anomaly_alert',
            data: {
                id: 'anomaly-001',
                type: 'velocity',
                severity: 'high',
                title: 'Test Alert',
                description: 'Test Description',
                affectedItems: [],
            },
        });

        // Wait for alert
        await waitForAnomalyAlert(page);

        // Click dismiss button
        const dismissButton = page.locator('[data-testid="dismiss-anomaly-001"]');
        await dismissButton.click();

        // Verify alert is removed
        const alert = page.locator('[data-testid="anomaly-alert"]');
        await expect(alert).not.toBeVisible();
    });

    test('should clear all anomaly alerts', async ({ page }) => {
        await waitForWebSocket(page);

        // Send multiple alerts
        for (let i = 1; i <= 5; i++) {
            await mockWebSocketMessage(page, {
                type: 'anomaly_alert',
                data: {
                    id: `anomaly-00${i}`,
                    type: 'velocity',
                    severity: 'high',
                    title: `Alert ${i}`,
                    description: `Description ${i}`,
                    affectedItems: [],
                },
            });
        }

        // Wait for alerts to appear
        await page.waitForSelector('[data-testid="anomaly-alert"]');

        // Click clear all button
        const clearAllButton = page.locator('[data-testid="clear-all-anomalies"]');
        await clearAllButton.click();

        // Verify all alerts are removed
        const alerts = page.locator('[data-testid="anomaly-alert"]');
        await expect(alerts).toHaveCount(0);

        // Verify badge is cleared
        const badge = page.locator('[data-testid="anomaly-count-badge"]');
        await expect(badge).not.toBeVisible();
    });

    test('should filter anomalies by severity', async ({ page }) => {
        await waitForWebSocket(page);

        // Send alerts with different severities
        const severities = ['critical', 'high', 'medium', 'low'];
        for (const severity of severities) {
            await mockWebSocketMessage(page, {
                type: 'anomaly_alert',
                data: {
                    id: `anomaly-${severity}`,
                    type: 'velocity',
                    severity,
                    title: `${severity} Alert`,
                    description: `${severity} Description`,
                    affectedItems: [],
                },
            });
        }

        // Wait for all alerts
        await page.waitForSelector('[data-testid="anomaly-alert"]');

        // Filter by critical
        const criticalFilter = page.locator('[data-testid="filter-critical"]');
        await criticalFilter.click();

        // Verify only critical alerts are shown
        const visibleAlerts = page.locator('[data-testid="anomaly-alert"]');
        await expect(visibleAlerts).toHaveCount(1);
        await expect(visibleAlerts.first()).toHaveAttribute('data-severity', 'critical');
    });

    test('should show anomaly details on click', async ({ page }) => {
        await waitForWebSocket(page);

        // Send anomaly alert
        await mockWebSocketMessage(page, {
            type: 'anomaly_alert',
            data: {
                id: 'anomaly-001',
                type: 'velocity',
                severity: 'high',
                title: 'Velocity Drop Detected',
                description: 'Team velocity dropped by 40% in the last sprint',
                affectedItems: ['item-1', 'item-2', 'item-3'],
            },
        });

        // Wait for alert
        await waitForAnomalyAlert(page);

        // Click on alert
        const alert = page.locator('[data-testid="anomaly-alert"]');
        await alert.click();

        // Verify detail panel opens
        const detailPanel = page.locator('[data-testid="anomaly-detail-panel"]');
        await expect(detailPanel).toBeVisible();

        // Verify affected items are listed
        const affectedItems = page.locator('[data-testid="affected-item"]');
        await expect(affectedItems).toHaveCount(3);
    });

    test('should trigger toast notification for critical anomalies', async ({ page }) => {
        await waitForWebSocket(page);

        // Send critical anomaly
        await mockWebSocketMessage(page, {
            type: 'anomaly_alert',
            data: {
                id: 'anomaly-critical',
                type: 'security',
                severity: 'critical',
                title: 'Security Breach Detected',
                description: 'Unauthorized access attempt',
                affectedItems: [],
            },
        });

        // Verify toast appears
        const toast = page.locator('[data-testid="toast-notification"]');
        await expect(toast).toBeVisible();
        await expect(toast).toContainText('Security Breach Detected');

        // Verify toast has critical styling
        await expect(toast).toHaveClass(/critical/);
    });
});
